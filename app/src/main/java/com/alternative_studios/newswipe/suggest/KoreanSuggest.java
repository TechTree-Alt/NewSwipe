package com.alternative_studios.newswipe.suggest;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 한국어 추천·다음 단어 예측의 순위를 한 확률 모형으로 매기고 후보를 모은다. 자동 수정은 {@link KoreanCorrect}, 사전에 없는
 * 꼴을 만드는 일(조사·의존 명사·어미 붙이기, 내가 쓴 어미)은 {@link KoreanForms}, 사전으로 낱말의 꼴을 알아보는 일(명사인지,
 * 용언 어간인지)은 {@link KoreanMorph}가 맡는다.
 *
 * <p>후보 단어 w의 점수 = P(w | 앞 단어)이며, 다음을 섞는다.
 * <ul>
 *   <li>기본 사전: λ·P(w|앞 단어) + (1-λ)·P(w). 앞 단어와의 관계는 다음 단어 사전({@link NextWords})에서 온다.</li>
 *   <li>내 기록({@link UsageHistory}, {@link UserDictionary}): 내가 그 단어를 쓴 비율과, 그 앞 단어 다음에 쓴 비율.
 *       기록이 쌓일수록 더 믿는다 (최대 USER_MAX).</li>
 *   <li>입력란 종류: 검색 칸에서는 조사가 붙은 말을 뒤로 미룬다 (검색어에는 조사를 거의 붙이지 않는다).</li>
 * </ul>
 * 후보는 접두사가 맞는 사전 단어, 앞 단어 다음에 자주 오는 단어, 내가 쓴 단어, 명사에 조사를 붙인 말, 초성 검색,
 * 오타가 섞인 접두사에서 모은다. 화면 스레드에서 부른다 (사전은 이미 읽혀 있어야 한다).
 */
final class KoreanSuggest {
    /** 입력란 종류. */
    static final int FIELD_NORMAL = 0, FIELD_SEARCH = 1;

    // 순위 모형의 계수 (dict/eval/SuggestEval.java로 맞춘 값. 평가 도구가 다른 값을 넣어 볼 수 있게 final로 두지 않는다).
    /** 내 기록을 믿는 최대 비율과, 그 절반에 이르는 기록 수. */
    static double USER_MAX = 0.5, USER_HALF = 300;

    /** 내 기록 안에서 '단어를 쓴 비율'과 '앞 단어 다음에 쓴 비율'의 몫 (나머지가 단어 쌍). */
    static double USER_UNI_SHARE = 0.5;

    /** 다음 단어 예측을 보여 줄 가장 높은 후보의 최소 확률 (사람이 쓴 글로 맞춘 값: 낮으면 자주 보이지만 대부분 틀린다). */
    static double PREDICT_MIN = 0.25;

    /** 오타 비용 1당 확률에 곱하는 값의 로그 (추천에서 오타가 섞인 접두사 후보, 자동 수정). */
    static double TYPO_PENALTY = 3.0;

    /** 오타가 섞인 접두사를 찾을 때의 최대 비용과, 후보로 볼 최소 점수. */
    static float FUZZY_MAX = 1.0f;

    static double FUZZY_MIN_FREQ = 85;

    /** 검색 칸에서 사전 단어에 조사가 붙은 말에 곱하는 값. */
    static double SEARCH_PARTICLE = 0.45;

    /** 띄어 쓰지 않고 이어 친 말의 후보에 곱하는 값과, 접두사가 맞는 후보가 충분해도 늘 찾을지. */
    static double SPLIT_FACTOR = 1.0;

    /** 이어 붙일 근거로 볼 단어 쌍의 최소 P(뒷말|앞말). */
    static double SPLIT_MIN_P = 0.03;

    static boolean SPLIT_ALWAYS = false;

    /** 다음 단어 사전이 없을 때 단어 사전 점수를 확률로 되돌리는 기준 (build_ko_model.py로 만든 사전과 비슷하게). */
    private static final double FALLBACK_LN_TOP = 8.8, FALLBACK_LN_BOTTOM = 1.6;

    static final class Result {
        final String[] words;
        /** 스페이스를 누르면 이 칸의 단어로 고친다 (자동 수정 미리 보기). 없으면 -1. */
        final int correction;
        /** 입력 중인 단어 없이 앞 단어만 보고 예측한 다음 단어인지. */
        final boolean prediction;

        Result(String[] words, int correction, boolean prediction) {
            this.words = words;
            this.correction = correction;
            this.prediction = prediction;
        }

        static final Result EMPTY = new Result(new String[0], -1, false);
    }

    /**
     * 앞 단어의 끝 글자(조사·어미)로 본 다음 단어 분포를 믿는 최대 비율과, 그 절반에 이르는 앞 단어 수
     * ({@link EndingContext}). 앞 단어의 단어 쌍이 없을 때(사전에 없거나 드문 꼴)도 '을' 뒤에는 용언이, '는' 뒤에는 의존 명사가
     * 오기 쉽다는 것을 쓴다. 0이면 쓰지 않는다.
     */
    static double ENDING_MAX = 0.3, ENDING_HALF = 20;

    /**
     * 사전에 없는 말을 내 기록에서 추천하려면 이만큼 써야 한다. 한 번만 쓴 말은 흔한 단어의 오타로 보이지 않을 때만
     * (자동 수정이 고치지 않을 말일 때만) 추천한다: 신조어·이름은 두 번째부터 추천하고, 한 번 친 오타는 끼어들지 않게.
     */
    static int LEARNED_MIN = 1;

    /**
     * 한 번만 쓴 말이 오타로 보이는지 (자동 수정 후보가 있는지). 학습할 때 백그라운드에서 재 두고({@link #noteLearned}),
     * 재 두지 않은 말(앱을 다시 켠 뒤 등)은 추천 한 번에 TYPO_CHECKS개까지만 화면 스레드에서 잰다.
     */
    private final Map<String, Boolean> typoLike = new java.util.concurrent.ConcurrentHashMap<>();

    private static final int TYPO_CHECKS = 4;

    private int typoBudget;

    private final WordDictionary dict;

    private final NextWords next;

    private final EndingContext ending;

    private final TypoModel typo;

    private final double[] uni = new double[256];
    private final KoreanMorph morph;
    private final KoreanForms forms;
    private final KoreanCorrect corrector;

    KoreanSuggest(WordDictionary dict, NextWords next, TypoModel typo) {
        this.dict = dict;
        this.next = next;
        this.typo = typo;
        if (next == null) NextWords.fillUnigram(uni, FALLBACK_LN_TOP, FALLBACK_LN_BOTTOM);
        else for (int s = 0; s < 256; s++) uni[s] = next.unigram(s);
        ending = EndingContext.build(dict, next);
        morph = new KoreanMorph(dict, uni);
        forms = new KoreanForms(dict, morph);
        corrector = new KoreanCorrect(this, dict, morph, typo);
    }

    /** 앞 단어 정보 (자동 수정이 쓴다). */
    Context context(String prev, int field, UserDictionary user, UsageHistory usage) {
        return new Context(prev, field, user, usage);
    }

    /** 사전에 없는 word를 고칠 단어 ({@link KoreanCorrect#correct}). */
    String correct(String word, String prev, int field, UserDictionary user, UsageHistory usage) {
        return corrector.correct(word, prev, field, user, usage);
    }

    /** 사전에 없는 word가 한국어 낱말로 그럴듯한지 ({@link KoreanCorrect#plausible}). */
    boolean plausible(String word) {
        return corrector.plausible(word);
    }

    /** 학습할 때 (백그라운드): 내가 쓴 어미를 기록한다 ({@link KoreanForms#learnEndings}). */
    void learnEndings(String w, UsageHistory usage, long now) {
        forms.learnEndings(w, usage, now);
    }

    /** 사전에 격조사를 붙인 말이 있는 명사인지 ({@link KoreanMorph#nounLike}). */
    boolean nounLike(String w) {
        return morph.nounLike(w);
    }

    WordDictionary dict() {
        return dict;
    }

    /** 단어 쌍이 하나라도 있는 다음 단어 사전을 쓰는지. */
    boolean hasNextWords() {
        return next != null && next.pairs() > 0;
    }

    /** 빈도(100만 어절당)가 perMillion 이상인 가장 낮은 사전 점수. */
    int scoreFor(double perMillion) {
        for (int s = 1; s < 256; s++) if (uni[s] * 1e6 >= perMillion) return s;
        return 255;
    }

    /** 순위를 매기는 동안 쓰는 앞 단어 정보. */
    final class Context {
        final String prev;
        final int group;            // 다음 단어 사전의 묶음 (-1 = 없음)
        final double lambda;
        final UserDictionary user;
        final UsageHistory usage;
        final double mu;            // 내 기록을 믿는 비율
        final long total;
        final int ctxCount;
        final int field;
        final int endGroup;         // 앞 단어 끝 글자의 묶음 ({@link EndingContext}, -1 = 없음)
        final double nu;            // 끝 글자 분포를 믿는 비율
        final String[][] learnedEndings = new String[3][];   // 내가 쓴 어미를 더한 어미 목록 (종류마다, 처음 쓸 때 만든다)

        Context(String prev, int field, UserDictionary user, UsageHistory usage) {
            this.prev = prev;
            this.field = field;
            int pid = prev == null ? -1 : dict.indexOf(prev);
            group = pid >= 0 && next != null ? next.group(pid) : -1;
            lambda = group >= 0 ? next.lambda(group) : 0;
            endGroup = ending == null || ENDING_MAX <= 0 ? -1 : ending.group(prev);
            if (endGroup >= 0) {
                int h = ending.heads(endGroup);
                nu = ENDING_MAX * h / (h + ENDING_HALF);
            } else {
                nu = 0;
            }
            this.user = user;
            this.usage = usage;
            total = usage == null ? 0 : usage.totalIfLoaded();
            mu = total == 0 ? 0 : USER_MAX * total / (total + USER_HALF);
            ctxCount = usage == null || prev == null ? 0 : usage.contextCountIfLoaded(prev);
        }

        /** 앞 단어와 id의 단어 쌍이 다음 단어 사전에 있는지. */
        boolean hasPair(int id) {
            return group >= 0 && next.find(group, id) >= 0;
        }

        /** 기본 사전 모형의 P(w|앞 단어). id = 사전 번호 (-1이면 사전에 없는 말). */
        double model(int id) {
            if (id < 0) return 0;
            double pu = uni[dict.score(id)];
            if (endGroup >= 0) {   // 단어 쌍이 없을 때 물러설 곳: 끝 글자 분포와 단어 빈도를 섞는다
                int e = ending.find(endGroup, id);
                pu = (1 - nu) * pu + (e >= 0 ? nu * ending.prob(e) : 0);
            }
            if (group < 0) return pu;
            int k = next.find(group, id);
            return k >= 0 ? lambda * next.prob(k) + (1 - lambda) * pu : (1 - lambda) * pu;
        }

        /** 내 기록의 P(w|앞 단어). */
        double mine(String w) {
            if (mu == 0) return 0;
            int c = usage.countIfLoaded(w);
            if (user != null) c = Math.max(c, user.knownCountIfLoaded(w));
            double pu = c / (total + 30.0);
            double pb = ctxCount == 0 ? 0 : usage.pairCountIfLoaded(prev, w) / (ctxCount + 2.0);
            return USER_UNI_SHARE * pu + (1 - USER_UNI_SHARE) * pb;
        }

        double score(String w, int id) {
            double p = (1 - mu) * model(id) + mu * mine(w);
            if (p <= 0 && user != null && user.knownCountIfLoaded(w) > 0) p = 1e-7;   // 기록을 아직 읽지 않았을 때
            return p * fieldFactor(w, field);
        }
    }

    /**
     * 입력란 종류에 따른 배율. 메시지 칸에서 대화체 어미를 앞세우는 것도 재 보았지만(dict/eval), 구어체 보완 목록이 이미
     * 대화체를 올려 두어서 오히려 키 절약률이 떨어졌다. 그래서 검색 칸만 따로 다룬다.
     */
    private double fieldFactor(String w, int field) {
        if (field == FIELD_SEARCH && WordSuggester.isDictWordWithParticle(dict, w)) return SEARCH_PARTICLE;
        return formal(w) ? FORMAL_FACTOR : written(w) ? WRITTEN_FACTOR : 1;
    }

    static double WRITTEN_FACTOR = 0.5;

    /** 기사체 서술(했다, 있었다, 한다, 먹는다). */
    static boolean written(String w) {
        int n = w.length();
        if (n < 2 || w.charAt(n - 1) != '다') return false;
        char c = w.charAt(n - 2);
        if (c < 0xAC00 || c > 0xD7A3) return false;
        int t = (c - 0xAC00) % 28;
        return t == 20 && c != '겠' || t == 4 && (c == '는' || (c - 0xAC00) % 588 / 28 == 0 && c != '안');   // 했다, 는다, 한다
    }

    /** 합쇼체 어미(-습니다, -ㅂ니다, -습니까): 사전 빈도는 뉴스·웹 글에서 와서 휴대폰으로 쓰는 글보다 높게 잡혀 있다. */
    static double FORMAL_FACTOR = 0.5;

    static boolean formal(String w) {
        int n = w.length();
        if (n < 3 || w.charAt(n - 2) != '니' || (w.charAt(n - 1) != '다' && w.charAt(n - 1) != '까')) return false;
        char c = w.charAt(n - 3);
        return c == '습' || c >= 0xAC00 && c <= 0xD7A3 && (c - 0xAC00) % 28 == 17;   // 받침 ㅂ (합니다, 갑니다)
    }

    /**
     * 입력 중인 word에 맞는 추천. fix(스페이스에서 고칠 단어, 없으면 null)가 있으면 맨 앞에 넣고 표시한다.
     */
    Result suggest(String word, String prev, int field, UserDictionary user, UsageHistory usage, String fix, int max) {
        if (word.isEmpty()) return predict(prev, field, user, usage, max);
        Context cx = new Context(prev, field, user, usage);
        Map<String, Double> pool = new HashMap<>();

        boolean initials = isInitials(word);
        if (initials) {
            for (int id : dict.chosungIds(word, 24)) add(pool, cx, dict.word(id), id, 1);
            if (usage != null) {
                // 내가 쓴 단어 중 초성이 맞는 것
                for (String w : chosungMatches(usage, word)) add(pool, cx, w, dict.indexOf(w), 1);
            }
        } else {
            for (int id : dict.lookupIds(word, 12)) add(pool, cx, dict.word(id), id, 1);
            if (cx.group >= 0) addInRanges(pool, cx, word, next, cx.group);            // 앞 단어 다음에 자주 오는 말
            if (cx.endGroup >= 0) addInRanges(pool, cx, word, ending, cx.endGroup);   // 앞 단어 끝 글자 다음에 자주 오는 말
            if (usage != null) {
                typoBudget = TYPO_CHECKS;
                for (String w : usage.prefixIfLoaded(word, 1)) {
                    int id = dict.indexOf(w);
                    // 사전에 없는 말은 두 번 이상 썼을 때만 (한 번 친 오타가 추천에 끼어들지 않게)
                    if (id >= 0 || learnedEnough(w, usage.countIfLoaded(w))) add(pool, cx, w, id, 1);
                }
            }
            if (user != null) {
                for (String w : user.lookupIfLoaded(word, true, 6)) add(pool, cx, w, dict.indexOf(w), 1);
            }
            forms.addParticles(pool, cx, word);
            forms.addBound(pool, cx, word);
            // 만든 활용형은 사전 후보가 모자랄 때만 빈칸을 채운다 (사전에 있는 말을 밀어내면 손해가 크다)
            if (pool.size() < max) forms.addBelow(pool, cx, word);
            if ((SPLIT_ALWAYS || pool.size() < max) && KoreanMorph.hangulSyllables(word) >= 2) addSplits(pool, cx, word);
            if (pool.size() < max && KoreanMorph.hangulSyllables(word) >= 2) {
                int[] ids = new int[8];
                float[] cost = new float[8];
                int n = dict.fuzzyPrefix(word, typo, FUZZY_MAX, scoreFor(FUZZY_MIN_FREQ), 8, ids, cost);
                for (int i = 0; i < n; i++) {
                    add(pool, cx, dict.word(ids[i]), ids[i], Math.exp(-TYPO_PENALTY * cost[i]));
                }
            }
        }
        pool.remove(word);
        List<String> ranked = rank(pool);

        List<String> out = new ArrayList<>(max);
        if (fix != null) out.add(fix);
        for (String w : ranked) {
            if (out.size() >= max) break;
            if (!w.equals(fix)) out.add(w);
        }
        return new Result(out.toArray(new String[0]), fix != null ? 0 : -1, false);
    }

    /** 사전에 없는 말 w를 c번 썼을 때 추천해도 되는지. */
    private boolean learnedEnough(String w, int c) {
        if (c >= 2) return true;
        if (c < LEARNED_MIN) return false;
        Boolean t = typoLike.get(w);
        if (t == null) {
            if (typoBudget <= 0) return false;
            typoBudget--;
            t = noteLearned(w);
        }
        return !t;
    }

    /** 사전에 없는 말을 학습했을 때 (백그라운드): 오타로 보이는지 재 둔다. 오타로 보이면 true. */
    boolean noteLearned(String w) {
        if (typoLike.size() > 4000) typoLike.clear();
        boolean t = !dict.contains(w) && corrector.correct(w, null, FIELD_NORMAL, null, null) != null;
        typoLike.put(w, t);
        return t;
    }

    /** 묶음 g(앞 단어나 그 끝 글자 다음에 자주 오는 말)의 단어 중 word로 시작할 수 있는 것을 후보에 넣는다. */
    private void addInRanges(Map<String, Double> pool, Context cx, String word, WordGroups src, int g) {
        int[] rg = new int[4];
        int nr = dict.ranges(word, rg);
        for (int r = 0; r < nr; r++) {
            for (int k = src.lowerBound(g, rg[2 * r]); k < src.to(g); k++) {
                int id = src.word(k);
                if (id >= rg[2 * r + 1]) break;
                String w = dict.word(id);
                if (!w.equals(word)) add(pool, cx, w, id, 1);
            }
        }
    }

    /** 묶음 g의 단어를 모두 후보에 넣는다 (다음 단어 예측). */
    private void addGroup(Map<String, Double> pool, Context cx, WordGroups src, int g) {
        for (int k = src.from(g); k < src.to(g); k++) {
            int id = src.word(k);
            add(pool, cx, dict.word(id), id, 1);
        }
    }

    /**
     * 띄어쓰기를 생략하고 치는 말 ('밥먹' → 밥먹었어): 앞부분이 단어이고, 그 단어 다음에 뒷부분으로 시작하는 말이 온다는 근거가
     * 있을 때만 이어 붙인 후보를 만든다. 근거는 다음 단어 사전의 단어 쌍(P(뒷말|앞말) ≥ SPLIT_MIN_P)이나 내가 두 번 이상
     * 이어 쓴 기록이다. 근거 없이 이어 붙이면 '그런가장', '사솔직히'처럼 말이 안 되는 후보가 빈칸을 채운다.
     * 확률은 P(앞말) × P(뒷말|앞말) × SPLIT_FACTOR. 긴 앞부분부터 두 가지만 본다.
     */
    private void addSplits(Map<String, Double> pool, Context cx, String word) {
        if (SPLIT_FACTOR <= 0) return;
        int n = word.length(), tried = 0;
        for (int k = n - 1; k >= 1 && tried < 2; k--) {
            String head = word.substring(0, k);
            if (KoreanMorph.hangulSyllables(head) != k) continue;
            int hid = dict.indexOf(head);
            if (hid < 0 && (cx.user == null || cx.user.knownCountIfLoaded(head) == 0)) continue;
            tried++;
            double ph = cx.score(head, hid);
            if (ph <= 0) continue;
            String tail = word.substring(k);
            Context ch = new Context(head, cx.field, cx.user, cx.usage);
            if (ch.group >= 0) {
                int[] rg = new int[4];
                int nr = dict.ranges(tail, rg);
                for (int r = 0; r < nr; r++) {
                    for (int i = next.lowerBound(ch.group, rg[2 * r]); i < next.to(ch.group); i++) {
                        int id = next.word(i);
                        if (id >= rg[2 * r + 1]) break;
                        if (next.prob(i) < SPLIT_MIN_P) continue;
                        putSplit(pool, head + dict.word(id), ph * ch.score(dict.word(id), id) * SPLIT_FACTOR);
                    }
                }
            }
            if (cx.usage != null) {
                for (Map.Entry<String, Integer> e : cx.usage.nextIfLoaded(head)) {
                    String w = e.getKey();
                    if (e.getValue() < 2 || w.equals(tail) || !HangulRange.startsWith(w, tail)) continue;
                    putSplit(pool, head + w, ph * ch.score(w, dict.indexOf(w)) * SPLIT_FACTOR);
                }
            }
        }
    }

    private static void putSplit(Map<String, Double> pool, String cand, double sc) {
        if (sc <= 0) return;
        Double old = pool.get(cand);
        if (old == null || old < sc) pool.put(cand, sc);
    }

    /** 입력 중인 단어 없이, 앞 단어 다음에 올 단어를 예측한다. 자신이 없으면 빈 결과. */
    Result predict(String prev, int field, UserDictionary user, UsageHistory usage, int max) {
        if (prev == null || prev.isEmpty()) return Result.EMPTY;
        Context cx = new Context(prev, field, user, usage);
        Map<String, Double> pool = new HashMap<>();
        if (cx.group >= 0) addGroup(pool, cx, next, cx.group);
        if (cx.endGroup >= 0) addGroup(pool, cx, ending, cx.endGroup);
        if (usage != null) {
            for (Map.Entry<String, Integer> e : usage.nextIfLoaded(prev)) {
                if (e.getValue() < 2) continue;   // 한 번 이어 쓴 것만으로는 예측하지 않는다
                add(pool, cx, e.getKey(), dict.indexOf(e.getKey()), 1);
            }
        }
        if (pool.isEmpty()) return Result.EMPTY;
        List<String> ranked = rank(pool);
        if (confidence(cx, ranked.get(0)) < PREDICT_MIN) return Result.EMPTY;
        int n = Math.min(max, ranked.size());
        return new Result(ranked.subList(0, n).toArray(new String[0]), -1, true);
    }

    /**
     * 예측을 보여 줄 만큼 믿을 만한지: 다음 단어 사전의 λ·P(w|앞)와, 내가 그 앞 단어 다음에 w를 쓴 비율 중 큰 것.
     * (순위 점수는 내 기록의 비중이 아직 작을 때 내 기록만으로 아는 말을 낮게 보므로 따로 잰다.)
     */
    private double confidence(Context cx, String w) {
        double c = 0;
        int id = dict.indexOf(w);
        if (cx.group >= 0 && id >= 0) {
            int k = next.find(cx.group, id);
            if (k >= 0) c = cx.lambda * next.prob(k);
        }
        if (cx.endGroup >= 0 && id >= 0) {
            int e = ending.find(cx.endGroup, id);
            if (e >= 0) c = Math.max(c, (1 - cx.lambda) * cx.nu * ending.prob(e));
        }
        if (cx.usage != null && cx.ctxCount > 0) {
            c = Math.max(c, cx.usage.pairCountIfLoaded(cx.prev, w) / (cx.ctxCount + 2.0));
        }
        return c;
    }

    private static void add(Map<String, Double> pool, Context cx, String w, int id, double factor) {
        double s = cx.score(w, id) * factor;
        if (s <= 0) return;
        Double old = pool.get(w);
        if (old == null || old < s) pool.put(w, s);
    }

    private static List<String> rank(Map<String, Double> pool) {
        List<Map.Entry<String, Double>> all = new ArrayList<>(pool.entrySet());
        all.sort((a, b) -> {
            int c = Double.compare(b.getValue(), a.getValue());
            if (c != 0) return c;
            c = Integer.compare(a.getKey().length(), b.getKey().length());
            return c != 0 ? c : a.getKey().compareTo(b.getKey());
        });
        List<String> out = new ArrayList<>(all.size());
        for (Map.Entry<String, Double> e : all) out.add(e.getKey());
        return out;
    }

    static boolean isInitials(String word) {
        int n = word.length();
        if (n < 2) return false;
        boolean same = true;
        for (int i = 0; i < n; i++) {
            char c = word.charAt(i);
            int[] r = HangulRange.of(c);
            if (c < 0x3131 || c >= 0x3164 || r == null) return false;
            if (c != word.charAt(0)) same = false;
        }
        return !same;   // ㅋㅋ·ㅎㅎㅎ처럼 한 자음만 되풀이한 말은 초성 검색으로 보지 않는다
    }

    private static List<String> chosungMatches(UsageHistory usage, String initials) {
        List<String> out = new ArrayList<>();
        for (String w : usage.prefixIfLoaded(String.valueOf(initials.charAt(0)), 2)) {
            if (w.length() < initials.length()) continue;
            boolean ok = true;
            for (int i = 0; i < initials.length() && ok; i++) {
                char c = w.charAt(i);
                int[] r = HangulRange.of(initials.charAt(i));
                ok = r != null && c >= r[0] && c <= r[1];
            }
            if (ok) out.add(w);
        }
        return out;
    }
}
