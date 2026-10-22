package com.alternative_studios.newswipe.suggest;

import android.content.Context;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 입력 중인 단어에 맞는 추천 단어를 찾는다.
 * 사전은 백그라운드 스레드에서 warmUp()으로 읽고, 화면 스레드에서는 이미 읽은 사전만 쓴다 (기다리지 않는다).
 * release()로 메모리를 돌려준다.
 *
 * <p>한국어는 앞 단어와의 관계(다음 단어 사전), 내가 쓴 기록, 입력란 종류까지 보고 순위를 매긴다 ({@link KoreanSuggest}).
 * 영어는 접두사가 맞는 단어를 빈도순으로 보인다.
 */
public final class WordSuggester {
    public static final int MAX = 3;
    /** 입력란 종류 (한국어 추천 순위에 쓴다): 보통, 검색. */
    public static final int FIELD_NORMAL = KoreanSuggest.FIELD_NORMAL, FIELD_SEARCH = KoreanSuggest.FIELD_SEARCH;

    /** 추천 결과. */
    public static final class Suggestions {
        public static final Suggestions EMPTY = new Suggestions(new String[0], -1, false);
        public final String[] words;
        /** 스페이스를 누르면 이 칸의 단어로 자동 수정된다 (추천란에 굵게 보인다). 없으면 -1. */
        public final int correction;
        /** 입력 중인 단어가 없을 때 앞 단어를 보고 예측한 다음 단어인지 (누르면 지우지 않고 넣는다). */
        public final boolean prediction;

        Suggestions(String[] words, int correction, boolean prediction) {
            this.words = words;
            this.correction = correction;
            this.prediction = prediction;
        }
    }

    private final Context context;
    private final UserDictionary user;
    /** 한국어 단어와 단어 쌍을 쓴 기록. null이면 기록하지 않는다. */
    private final UsageHistory usage;
    private volatile WordDictionary ko, en;
    /** 한국어 순위 모형 (단어 사전과 다음 단어 사전을 함께 읽은 뒤 만든다). */
    private volatile KoreanSuggest koModel;
    private final TypoModel typo = new TypoModel();
    /** 사전 파일을 읽지 못했다. 다시 시도하지 않는다 (release() 뒤에 한 번 더 시도한다). */
    private volatile boolean koFailed, enFailed;
    /** release()할 때마다 올린다. 읽는 도중에 놓아 달라는 요청이 오면 다 읽은 사전을 버리기 위해 쓴다. */
    private volatile int generation;
    /** 학습한 단어를 추천·자동 수정에 쓸지. 끄면 사용자 사전을 읽지도 않는다. */
    private volatile boolean userEnabled = true;

    public WordSuggester(Context context, UserDictionary user, UsageHistory usage) {
        this.context = context.getApplicationContext();
        this.user = user;
        this.usage = usage;
    }

    /** 모음 연속 탭(ㅓ+ㅓ → ㅕ)을 쓰는지 알려 준다: 쓰면 입력 중인 '안너'도 '안녀…'로 시작하는 말을 찾는다. */
    public static void setDoubleTapVowel(boolean enabled) {
        HangulRange.doubleTapVowel = enabled;
    }

    /** 자동 수정에서 이웃한 키를 알 수 있게 지금 한글 자판의 글자 줄을 알려 준다. */
    public void setKeyRows(String[][] rows) {
        typo.setRows(rows);
    }

    public UserDictionary user() {
        return user;
    }

    public void setUserEnabled(boolean enabled) {
        userEnabled = enabled;
    }

    private boolean useUser() {
        return user != null && userEnabled;
    }

    /** 사용자가 입력한 단어를 기억한다 (파일을 읽을 수 있으니 백그라운드 스레드에서 부른다). */
    public void learn(String word, boolean korean, boolean strong) {
        learn(word, null, korean, strong);
    }

    /**
     * 사용자가 입력한 단어를 기억한다. prev = 바로 앞 단어 (없으면 null). 한국어는 사전 단어도 쓴 기록에 남겨
     * 추천 순서를 내 말버릇에 맞춘다. 백그라운드 스레드에서 부른다.
     */
    public void learn(String word, String prev, boolean korean, boolean strong) {
        if (!useUser()) return;
        learnInto(korean ? ko : en, user, korean ? usage : null, word, prev, korean, strong, System.currentTimeMillis());
        KoreanSuggest m = koModel;
        if (korean && m != null && learnable(word, true)) {
            m.noteLearned(word);   // 한 번 쓴 말도 추천하되 오타로 보이는 말은 빼려고, 오타인지를 여기(백그라운드)서 재 둔다
            m.learnEndings(word, usage, System.currentTimeMillis());   // 내가 쓴 어미
        }
    }

    /**
     * 학습 한 번 (앱과 평가 도구 dict/eval/SuggestEval이 같이 쓴다). d = 지금 언어의 기본 사전 (아직 읽지 않았으면 null),
     * usage = 한국어 쓴 기록 (영어나 기록하지 않으면 null).
     */
    static void learnInto(WordDictionary d, UserDictionary user, UsageHistory usage, String word, String prev,
                          boolean korean, boolean strong, long now) {
        if (!learnable(word, korean) || user.isBlocked(word, korean)) return;   // 사용자가 지워 학습하지 않는 단어
        if (korean && usage != null) {
            String p = prev != null && learnable(prev, true) ? prev : null;
            usage.record(p, word, now);
            // 앞 단어의 끝 글자(조사·어미) 다음에 쓴 말도 센다: '-고' 다음에 내가 쓰는 말을 다른 앞 단어 뒤에서도 예측한다
            if (p != null && KoreanSuggest.K_END > 0) usage.recordContext(KoreanSuggest.endKey(p), word, now);
            usage.noteRecent(word);
        }
        if (d == null || d.contains(korean ? word : word.toLowerCase(Locale.ROOT))) return;
        if (korean) {
            // '짝남+한테', '민수+가', '고민+이야'처럼 조사(서술격 포함)가 붙은 말은 조사를 뗀 말로 기억한다
            // (조사마다 따로 쌓이지 않고, 기억한 말에 어느 조사든 붙여 추천한다).
            String[] sp = splitParticle(word);
            if (sp != null) {
                String stem = sp[0];
                if (d.contains(stem)) return;   // 사전 단어에 조사만 붙었으면 기억할 필요가 없다
                // 이·가·도·만처럼 낱말의 끝 글자이기도 한 조사는, 앞부분을 맨말이나 다른 조사와 함께 쓴 적이 있을 때만 뗀다
                // (길냥이 → 길냥, 냥이 → 냥으로 잘못 떼지 않게). 앞부분을 이미 다른 꼴로 썼으면 두 번째이니 바로 아는 말이 된다.
                boolean seen = stemSeen(user, usage, stem, word);
                if (learnable(stem, true) && (seen || !AMBIGUOUS_PARTICLES.contains(sp[1]))) {
                    user.add(stem, true, strong || seen, now);
                    return;
                }
                // 조사인지 알 수 없으면 입력한 그대로 기억한다
            }
        }
        user.add(word, korean, strong, now);
    }

    /**
     * 앞 단어 prev 다음에 shown을 예측해 보였는데 사용자가 고르지 않고 typed를 쳤다 (백그라운드 스레드에서 부른다).
     * typed가 shown에 있으면 (직접 쳤어도 맞힌 예측이니) 기록하지 않는다.
     */
    public void noteIgnoredPrediction(String prev, String[] shown, String typed) {
        if (!useUser()) return;
        noteIgnoredInto(usage, prev, shown, typed, System.currentTimeMillis());
    }

    /** 고르지 않은 예측 기록 (앱과 평가 도구가 같이 쓴다). */
    static void noteIgnoredInto(UsageHistory usage, String prev, String[] shown, String typed, long now) {
        if (usage == null || shown == null || shown.length == 0 || prev == null || !learnable(prev, true)
                || typed == null || !learnable(typed, true)) {
            return;
        }
        for (String w : shown) if (w.equals(typed)) return;
        String ek = KoreanSuggest.endKey(prev);
        for (String w : shown) {
            usage.recordIgnored(prev, w, now);
            if (KoreanSuggest.IGNORED_END > 0) usage.recordIgnored(ek, w, now);
        }
    }

    /** 낱말의 끝 글자이기도 한 짧은 조사 (길냥이, 사이다, 오징어볶음밥도처럼 낱말에 들어 있을 수 있다). */
    private static final java.util.Set<String> AMBIGUOUS_PARTICLES = new java.util.HashSet<>(java.util.Arrays.asList(
            "이", "가", "도", "의", "만", "와", "과", "로", "나", "야", "요", "고", "면", "지", "네", "래", "며", "든", "엔", "란",
            "뿐", "께", "죠", "임", "냐", "니"));

    /**
     * 떼어 알아보는 조사: 추천에서 붙이는 조사(서술격 포함, {@link KoreanSuggest#PARTICLES})와 서술격의 구어체. 긴 것부터.
     * 학습(조사를 뗀 말로 기억), 자동 수정(사전 단어·학습한 말 + 조사는 고치지 않는다), 검색 칸 순위가 모두 이것을 쓴다.
     * (아래쪽에 선언한 목록을 쓰므로 처음 쓸 때 만든다.)
     */
    private static final class LearnParticles {
        static final String[][] ALL = build();
    }

    private static String[][] build() {
        // {조사, 붙는 자리} 붙는 자리: "" 아무 데나, "F" 받침 뒤, "V" 받침 없는 말 뒤 (ㄹ 받침 뒤의 로도 V로 본다)
        java.util.Map<String, String> m = new java.util.LinkedHashMap<>();
        for (String[] p : KoreanForms.PARTICLES) {
            if (p[1] == null) {
                m.put(p[0] + "|", "");
            } else {
                m.put(p[0] + "|F", "F");
                m.put(p[1] + "|V", "V");
            }
        }
        m.put("마다|", "");
        // 서술격의 구어체 (고민임, 사람인듯, 친구냐, 짝남이었음): 추천에서 붙이지는 않지만 떼어 알아본다
        for (String p : new String[]{"임", "인듯", "인거", "인게", "이냐", "이니", "이었음", "인지"}) m.put(p + "|F", "F");
        for (String p : new String[]{"임", "인듯", "인거", "인게", "냐", "니", "였음", "인지"}) m.put(p + "|V", "V");
        List<String[]> all = new ArrayList<>();
        for (String k : m.keySet()) {
            int bar = k.indexOf('|');
            all.add(new String[]{k.substring(0, bar), k.substring(bar + 1)});
        }
        all.sort((a, b) -> b[0].length() - a[0].length());
        return all.toArray(new String[0][]);
    }

    /** s가 조사(서술격 포함) 하나인지 (이, 가, 를, 한테, 이야 ...). */
    static boolean isParticle(String s) {
        for (String[] p : LearnParticles.ALL) if (p[0].equals(s)) return true;
        return false;
    }

        /** word = 앞부분 + 조사이면 {앞부분, 조사}, 아니면 null. 받침에 맞는 조사만, 긴 것부터. 앞부분은 한글 한 글자 이상. */
    static String[] splitParticle(String word) {
        for (String[] p : LearnParticles.ALL) {
            String s = p[0];
            if (word.length() <= s.length() || !word.endsWith(s)) continue;
            String stem = word.substring(0, word.length() - s.length());
            char last = stem.charAt(stem.length() - 1);
            if (last < '\uAC00' || last > '\uD7A3') continue;
            int fin = (last - 0xAC00) % 28;
            if (p[1].equals("F") && (fin == 0 || fin == 8 && s.startsWith("으로"))) continue;
            if (p[1].equals("V") && fin != 0 && !(fin == 8 && s.startsWith("로"))) continue;
            return new String[]{stem, s};
        }
        return null;
    }

    /** stem을 맨말로, 또는 word가 아닌 다른 조사와 함께 쓴 적이 있는지 (쓴 기록과 학습한 단어에서). */
    private static boolean stemSeen(UserDictionary user, UsageHistory usage, String stem, String word) {
        if (user.knownCountIfLoaded(stem) > 0) return true;
        if (usage == null) return false;
        if (usage.countIfLoaded(stem) > 0) return true;
        for (String[] p : LearnParticles.ALL) {
            String w = stem + p[0];
            if (w.equals(word) || usage.countIfLoaded(w) == 0) continue;
            String[] sp = splitParticle(w);
            if (sp != null && sp[0].equals(stem)) return true;   // 받침에 맞는 조사로 떼어지는 꼴만
        }
        return false;
    }

    /** 학습한 말이거나 학습한 말에 조사가 붙은 말인지 (자동 수정하지 않는다). */
    static boolean userKnows(UserDictionary user, String word) {
        if (user.knownCountIfLoaded(word) > 0) return true;
        String[] sp = splitParticle(word);
        return sp != null && user.knownCountIfLoaded(sp[0]) > 0;
    }

    /** 끝에 조사(서술격 포함)가 붙어 있으면 조사를 뗀 앞부분을, 아니면 null ({@link #splitParticle}). */
    static String particleStem(String word) {
        String[] sp = splitParticle(word);
        return sp == null ? null : sp[0];
    }

    /** 기본 사전에 있는 단어 뒤에 흔한 조사만 붙은 말인지 (예: 바람+에도, 친구+를). */
    static boolean isDictWordWithParticle(WordDictionary d, String word) {
        String stem = particleStem(word);
        return stem != null && d.contains(stem);
    }

    static boolean learnable(String word, boolean korean) {
        int n = word.length();
        if (n > 24) return false;
        if (korean) {
            if (n < 2) return false;
            for (int i = 0; i < n; i++) {
                char c = word.charAt(i);
                if (c < '\uAC00' || c > '\uD7A3') return false;
            }
            return true;
        }
        if (n < 3) return false;
        for (int i = 0; i < n; i++) {
            char c = word.charAt(i);
            if (!((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c == '\'' && i > 0 && i < n - 1))) return false;
        }
        return true;
    }

    /** 사전(과 사용자 사전)을 읽어 둔다. 백그라운드 스레드에서 부른다. */
    public void warmUp(boolean korean) {
        warmUp(korean, generation);
    }

    /** gen: 작업을 맡길 때의 generation(). 그사이 release()가 있었으면 읽지 않는다 (놓아 준 뒤 다시 올리지 않도록). */
    public void warmUp(boolean korean, int gen) {
        if (gen != generation) return;
        WordDictionary d = dictionary(korean);
        if (d != null && korean && gen == generation) d.warmUpChosung();
        if (useUser() && gen == generation) {
            user.load();
            if (korean && usage != null) usage.load();
        }
    }

    /** 쓴 기록이 다 읽혔는지 (기록을 쓰지 않으면 true). */
    public boolean historyLoaded() {
        return usage == null || !useUser() || usage.isLoaded();
    }

    /** 바뀐 학습 내용이 쌓였으면 파일에 쓴다. 백그라운드 스레드에서 부른다. */
    public void saveIfNeeded() {
        if (user != null && user.needsSave()) user.save();
        if (usage != null && usage.needsSave()) usage.save();
    }

    public int generation() {
        return generation;
    }

    public boolean isLoaded(boolean korean) {
        return (korean ? ko : en) != null;
    }

    /** 읽었거나, 읽으려 했지만 실패해서 더 기다릴 필요가 없는지. */
    public boolean isSettled(boolean korean) {
        return korean ? ko != null || koFailed : en != null || enFailed;
    }

    /** 한국어 사전을 읽을 때의 {@link DictionaryPack#changes()}. */
    private volatile int koPackChanges = -1;

    /** 한국어 사전을 읽은 뒤 사전 팩을 가져오거나 지웠는지 (그러면 release()하고 다시 읽는다). */
    public boolean packChanged() {
        return (ko != null || koFailed) && koPackChanges != DictionaryPack.changes();
    }

    /** 지금 쓰는 한국어 사전에 다음 단어 정보가 있는지 (사전 팩을 가져왔을 때). */
    public boolean hasNextWords() {
        KoreanSuggest m = koModel;
        return m != null && m.hasNextWords();
    }

    /** 기본 사전을 놓아 준다. 화면 스레드에서 불러도 기다리지 않는다. */
    public void release() {
        generation++;
        ko = null;
        en = null;
        koModel = null;
        koFailed = false;
        enFailed = false;
    }

    private WordDictionary dictionary(boolean korean) {
        WordDictionary d = korean ? ko : en;
        if (d != null || (korean ? koFailed : enFailed)) return d;
        synchronized (this) {
            d = korean ? ko : en;
            if (d != null || (korean ? koFailed : enFailed)) return d;
            int gen = generation;
            NextWords next = null;
            DictionaryPack.Loaded pack = null;
            if (korean) {
                // 사전 팩을 가져왔으면 그것을 쓴다. 없거나 읽지 못하면 앱에 든 기본 사전을 쓴다.
                koPackChanges = DictionaryPack.changes();
                pack = DictionaryPack.load(context.getFilesDir());
            }
            if (pack != null) {
                d = pack.dict;
                next = pack.next;
            } else try (InputStream in = context.getAssets().open(korean ? "dict_ko.bin" : "dict_en.bin")) {
                d = WordDictionary.load(in);
            } catch (IOException | RuntimeException e) {
                if (korean) koFailed = true;
                else enFailed = true;
                if (gen != generation) {   // 그사이 release()가 있었다: 실패 표시도 지운다
                    if (korean) koFailed = false;
                    else enFailed = false;
                }
                return null;
            }
            if (korean && pack == null) {
                // 기본 사전의 다음 단어 사전 (자주 쓰는 단어 쌍 5만 개). 없거나 깨져 있어도 단어 추천은 쓴다.
                try (InputStream in = context.getAssets().open("dict_ko_next.bin")) {
                    next = NextWords.load(in, d.size());
                } catch (IOException | RuntimeException e) {
                    next = null;
                }
            }
            if (korean) {
                KnownWords known = pack != null ? pack.known : null;
                if (pack == null) {
                    // 기본 사전의 맞는 말 목록. 없거나 깨져 있으면 쓰지 않는다 (자동 수정이 예전 기준으로 고친다).
                    try (InputStream in = new java.io.BufferedInputStream(context.getAssets().open("dict_ko_known.bin"))) {
                        known = KnownWords.load(in);
                    } catch (IOException | RuntimeException e) {
                        known = null;
                    }
                }
                KoreanSuggest m = new KoreanSuggest(d, next, typo);
                m.setKnownWords(known);   // 다 정한 뒤에 내놓는다
                koModel = m;
                ko = d;
            } else {
                en = d;
            }
            // 읽는 동안(또는 방금) 놓아 달라는 요청이 왔으면 버린다. 넣은 뒤에 다시 확인해야 틈이 없다.
            if (gen != generation) {
                if (korean) {
                    ko = null;
                    koModel = null;
                } else {
                    en = null;
                }
                return null;
            }
            return d;
        }
    }

    /**
     * 입력 중인 word의 추천 (화면 스레드용, 사전을 읽지 않았으면 빈 결과).
     * prev = 바로 앞 단어 (한국어 순위에 쓴다, 없으면 null). correction이면 스페이스에서 자동 수정될 단어를 맨 앞에 넣는다.
     */
    public Suggestions suggestIfLoaded(String word, String prev, boolean korean, int field, boolean correction) {
        if (word.isEmpty()) return Suggestions.EMPTY;
        if (!korean) {
            String[] r = merge(word, false, en);
            String fix = correction ? autoCorrect(word, false) : null;
            if (fix == null) return new Suggestions(r, -1, false);
            java.util.LinkedHashSet<String> out = new java.util.LinkedHashSet<>();
            out.add(fix);
            for (String w : r) out.add(w);
            String[] a = out.toArray(new String[0]);
            return new Suggestions(a.length <= MAX ? a : java.util.Arrays.copyOf(a, MAX), 0, false);
        }
        KoreanSuggest m = koModel;
        if (m == null) return Suggestions.EMPTY;
        String fix = correction ? autoCorrect(word, prev, true, field) : null;
        KoreanSuggest.Result r = m.suggest(word, prev, field, useUser() ? user : null, useUser() ? usage : null, fix, MAX);
        return new Suggestions(r.words, r.correction, false);
    }

    /** 입력 중인 단어 없이 앞 단어 prev 다음에 올 한국어 단어 (화면 스레드용). 자신이 없거나 사전을 읽지 않았으면 빈 결과. */
    public Suggestions predictIfLoaded(String prev, int field) {
        KoreanSuggest m = koModel;
        if (m == null || prev == null || prev.isEmpty()) return Suggestions.EMPTY;
        KoreanSuggest.Result r = m.predict(prev, field, useUser() ? user : null, useUser() ? usage : null, MAX);
        return r.words.length == 0 ? Suggestions.EMPTY : new Suggestions(r.words, -1, true);
    }

    /** 사용자가 자주 쓴 단어를 먼저, 나머지는 기본 사전에서 채운다. */
    private String[] merge(String word, boolean korean, WordDictionary d) {
        String[] base = d == null ? new String[0] : suggest(d, word, korean);
        if (!useUser()) return base;
        String[] mine = user.lookupIfLoaded(word, korean, MAX);
        if (mine.length == 0) return base;
        boolean cap = !korean && Character.isUpperCase(word.charAt(0));
        boolean allCaps = cap && word.length() > 1 && word.equals(word.toUpperCase(Locale.ROOT));
        java.util.LinkedHashSet<String> out = new java.util.LinkedHashSet<>();
        for (String w : mine) {
            if (allCaps) w = w.toUpperCase(Locale.ROOT);
            else if (cap) w = Character.toUpperCase(w.charAt(0)) + w.substring(1);
            out.add(w);
        }
        for (String w : base) out.add(w);
        String[] r = out.toArray(new String[0]);
        return r.length <= MAX ? r : java.util.Arrays.copyOf(r, MAX);
    }

    static String[] suggest(WordDictionary d, String word, boolean korean) {
        if (word.isEmpty()) return new String[0];
        if (korean) return d.lookup(word, MAX);
        // 영어: 소문자로 찾고 입력한 대소문자 모양에 맞춘다.
        String lower = word.toLowerCase(Locale.ROOT);
        String[] r = d.lookup(lower, MAX);
        boolean cap = Character.isUpperCase(word.charAt(0));
        boolean allCaps = cap && word.length() > 1 && word.equals(word.toUpperCase(Locale.ROOT));
        for (int i = 0; i < r.length; i++) {
            if (allCaps) r[i] = r[i].toUpperCase(Locale.ROOT);
            else if (cap) r[i] = Character.toUpperCase(r[i].charAt(0)) + r[i].substring(1);
        }
        return r;
    }

    /**
     * 스페이스를 눌렀을 때 단어를 자동으로 고칠 수 있으면 고친 단어를, 아니면 null을 돌려준다.
     * 사전에 있는 단어, 너무 짧은 단어, 대문자가 섞인 단어는 건드리지 않는다. 사전을 읽지 않았으면 null.
     */
    public String autoCorrect(String word, boolean korean) {
        return autoCorrect(word, null, korean, FIELD_NORMAL);
    }

    /** 앞 단어 prev와 입력란 종류도 보고 고친다 (한국어). */
    public String autoCorrect(String word, String prev, boolean korean, int field) {
        WordDictionary d = korean ? ko : en;
        if (d == null) return null;
        if (useUser()) {
            // 내가 쓰는 단어는 고치지 않는다. 학습한 단어를 아직 읽지 않았으면 모르니 고치지 않는다.
            if (!user.isLoaded() || user.isKnownIfLoaded(word, korean)) return null;
            if (korean && userKnows(user, word)) return null;   // '민수가', '짝남이야'처럼 학습한 말에 조사가 붙은 말
        }
        if (korean) {
            KoreanSuggest m = koModel;
            if (m == null) return null;
            return m.correct(word, prev, field, useUser() ? user : null, useUser() ? usage : null);   // 덜 만든 글자·ㅋㅋ·사전 단어는 그대로 둔다
        }
        return autoCorrect(d, word, false);
    }

    static String autoCorrect(WordDictionary d, String word, boolean korean) {
        if (korean) return new KoreanSuggest(d, null, new TypoModel()).correct(word, null, FIELD_NORMAL, null, null);
        if (word.length() < 3) return null;
        for (int i = 1; i < word.length(); i++) if (Character.isUpperCase(word.charAt(i))) return null;
        String lower = word.toLowerCase(Locale.ROOT);
        if (d.contains(lower)) return null;
        String r = d.correct(lower, 100);
        if (r == null) return null;
        return Character.isUpperCase(word.charAt(0)) ? Character.toUpperCase(r.charAt(0)) + r.substring(1) : r;
    }

    /** 단어 뒤에 붙일 목적격 조사. 한글은 받침에 따라 '을/를', 그 밖에는 '을(를)'. */
    public static String objectParticle(String word) {
        char last = word.isEmpty() ? 0 : word.charAt(word.length() - 1);
        if (last >= 0xAC00 && last <= 0xD7A3) return (last - 0xAC00) % 28 == 0 ? "를" : "을";
        return "을(를)";
    }

    /**
     * 커서 앞 글자에서 입력 중인 단어 바로 앞의 한글 단어 (공백으로만 떨어져 있을 때). 문장 부호·줄바꿈으로 끊겼거나
     * 숫자·영문이 섞인 어절이면 null. 다음 단어 예측과 순위에 쓴다.
     */
    public static String previousWord(CharSequence before) {
        if (before == null) return null;
        int i = before.length() - currentWord(before, true).length();
        int j = i;
        while (j > 0 && before.charAt(j - 1) == ' ') j--;
        if (j == i || j == 0) return null;
        // '알겠어ㅋㅋ'처럼 단어 뒤에 붙인 자음·모음(ㅋㅋ, ㅎㅎ, ㅠㅠ)은 건너뛰고 그 앞 단어를 본다.
        int jamo = j;
        while (jamo > 0 && isCompatJamo(before.charAt(jamo - 1))) jamo--;
        if (jamo < j && jamo > 0 && isSyllable(before.charAt(jamo - 1))) j = jamo;
        int k = j;
        while (k > 0) {
            char c = before.charAt(k - 1);
            if (c < '\uAC00' || c > '\uD7A3') break;
            k--;
        }
        if (k == j || (k > 0 && !Character.isWhitespace(before.charAt(k - 1)))) return null;
        return before.subSequence(k, j).toString();
    }

    /**
     * 학습할 때의 단어: 끝에 붙인 자음·모음(ㅋㅋ, ㅎㅎ, ㅠㅠ)을 뗀 한글 부분 ('알겠어ㅋㅋ' → 알겠어).
     * 자음·모음만 있는 말이면 원래 말을 그대로 돌려준다 (학습하지 않게 된다).
     */
    public static String learnablePart(String word) {
        int n = word.length();
        while (n > 0 && isCompatJamo(word.charAt(n - 1))) n--;
        return n == 0 ? word : word.substring(0, n);
    }

    private static boolean isCompatJamo(char c) {
        return c >= 'ㄱ' && c <= 'ㅣ';
    }

    private static boolean isSyllable(char c) {
        return c >= '\uAC00' && c <= '\uD7A3';
    }

    /** 커서 앞 글자에서 추천에 쓸 '입력 중인 단어'를 뽑는다. 없으면 빈 문자열. */
    public static String currentWord(CharSequence before, boolean korean) {
        int i = before.length();
        while (i > 0) {
            char c = before.charAt(i - 1);
            boolean ok = korean ? (c >= '가' && c <= '힣') || (c >= 'ㄱ' && c <= 'ㅣ')
                    : (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c == '\'' && i < before.length() && i > 1);
            if (!ok) break;
            i--;
        }
        return before.subSequence(i, before.length()).toString();
    }
}
