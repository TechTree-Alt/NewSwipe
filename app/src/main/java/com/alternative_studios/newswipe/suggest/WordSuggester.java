package com.alternative_studios.newswipe.suggest;

import android.content.Context;

import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;

/**
 * 입력 중인 단어에 맞는 추천 단어를 찾는다.
 * 사전은 백그라운드 스레드에서 warmUp()으로 읽고, 화면 스레드에서는 이미 읽은 사전만 쓴다 (기다리지 않는다).
 * release()로 메모리를 돌려준다.
 */
public final class WordSuggester {
    public static final int MAX = 3;

    private final Context context;
    private final UserDictionary user;
    private volatile WordDictionary ko, en;
    /** 사전 파일을 읽지 못했다. 다시 시도하지 않는다 (release() 뒤에 한 번 더 시도한다). */
    private volatile boolean koFailed, enFailed;
    /** release()할 때마다 올린다. 읽는 도중에 놓아 달라는 요청이 오면 다 읽은 사전을 버리기 위해 쓴다. */
    private volatile int generation;
    /** 학습한 단어를 추천·자동 수정에 쓸지. 끄면 사용자 사전을 읽지도 않는다. */
    private volatile boolean userEnabled = true;

    public WordSuggester(Context context, UserDictionary user) {
        this.context = context.getApplicationContext();
        this.user = user;
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
        if (!useUser() || !learnable(word, korean)) return;
        WordDictionary d = korean ? ko : en;
        if (d == null || d.contains(korean ? word : word.toLowerCase(Locale.ROOT))) return;
        if (korean) {
            // '글자+를', '민수+가'처럼 조사가 붙은 말은 조사를 뗀 단어로 기억한다 (조사마다 따로 쌓이지 않게).
            String stem = particleStem(word);
            if (stem != null) {
                if (d.contains(stem) || !learnable(stem, true)) return;   // 사전 단어에 조사만 붙었으면 기억할 필요가 없다
                word = stem;
            }
        }
        user.add(word, korean, strong, System.currentTimeMillis());
    }

    /** 받침이 있어야 붙는 조사, 받침이 없어야(또는 ㄹ 받침) 붙는 조사, 받침과 상관없는 조사. 긴 것부터 맞춘다. */
    private static final String[] PARTICLE_AFTER_FINAL = {"으로부터", "으로는", "으로도", "이라도", "이랑", "으로", "이나", "을", "은", "이", "과"};
    private static final String[] PARTICLE_AFTER_VOWEL = {"로부터", "로는", "로도", "라도", "랑", "로", "나", "를", "는", "가", "와"};
    private static final String[] PARTICLE_ANY = {"에서부터", "에게서", "한테서", "에서는", "에서도", "에게는", "에게도", "한테는", "까지는",
            "까지도", "부터는", "만큼은", "에서", "에게", "한테", "까지", "부터", "처럼", "보다", "마다", "만큼", "하고", "에는", "에도",
            "의", "에", "도", "만", "께"};

    /**
     * 끝에 흔한 조사가 붙어 있으면 조사를 뗀 앞부분을, 아니면 null. 받침에 맞는 조사만 인정한다
     * (받침 뒤 을·은·이·과, 받침 없는 말 뒤 를·는·가·와, '로'는 ㄹ 받침 뒤에도). 앞부분은 한글 한 글자 이상.
     */
    static String particleStem(String word) {
        if (word.length() < 2) return null;
        for (String[] group : new String[][]{PARTICLE_ANY, PARTICLE_AFTER_FINAL, PARTICLE_AFTER_VOWEL}) {
            for (String p : group) {
                if (word.length() <= p.length() || !word.endsWith(p)) continue;
                String stem = word.substring(0, word.length() - p.length());
                char last = stem.charAt(stem.length() - 1);
                if (last < '\uAC00' || last > '\uD7A3') continue;
                int fin = (last - 0xAC00) % 28;
                if (group == PARTICLE_AFTER_FINAL && fin == 0) continue;
                if (group == PARTICLE_AFTER_VOWEL && fin != 0 && !(fin == 8 && p.startsWith("로"))) continue;
                return stem;
            }
        }
        return null;
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
        dictionary(korean);
        if (useUser() && gen == generation) user.load();
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

    /** 기본 사전을 놓아 준다. 화면 스레드에서 불러도 기다리지 않는다. */
    public void release() {
        generation++;
        ko = null;
        en = null;
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
            try (InputStream in = context.getAssets().open(korean ? "dict_ko.bin" : "dict_en.bin")) {
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
            if (korean) ko = d;
            else en = d;
            // 읽는 동안(또는 방금) 놓아 달라는 요청이 왔으면 버린다. 넣은 뒤에 다시 확인해야 틈이 없다.
            if (gen != generation) {
                if (korean) ko = null;
                else en = null;
                return null;
            }
            return d;
        }
    }

    /** 사전을 아직 읽지 않았으면 읽지 않고 빈 결과를 돌려준다 (화면 스레드에서 쓰기 위해). */
    public String[] suggestIfLoaded(String word, boolean korean) {
        return merge(word, korean, korean ? ko : en);
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
        WordDictionary d = korean ? ko : en;
        if (d == null) return null;
        if (useUser()) {
            // 내가 쓰는 단어는 고치지 않는다. 학습한 단어를 아직 읽지 않았으면 모르니 고치지 않는다.
            if (!user.isLoaded() || user.isKnownIfLoaded(word, korean)) return null;
            if (korean) {
                String stem = particleStem(word);   // '민수가'처럼 학습한 단어에 조사가 붙은 말
                if (stem != null && user.isKnownIfLoaded(stem, true)) return null;
            }
        }
        return autoCorrect(d, word, korean);
    }

    static String autoCorrect(WordDictionary d, String word, boolean korean) {
        if (korean) {
            if (word.length() < 2) return null;
            for (int i = 0; i < word.length(); i++) {
                char c = word.charAt(i);
                if (c < '\uAC00' || c > '\uD7A3') return null;   // 덜 만든 글자나 자음만 있는 말(ㅋㅋ)은 그대로 둔다
            }
            if (d.contains(word) || isDictWordWithParticle(d, word)) return null;
            return d.correct(word, 140);
        }
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
