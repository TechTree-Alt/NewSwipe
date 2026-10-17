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
 *   <li>내 기록({@link UsageHistory}, {@link UserDictionary}): 내가 그 단어를 쓴 비율(기록이 쌓일수록 더 믿는다, 최대
 *       USER_MAX)과 최근에 쓴 단어 위에, 내가 앞 단어의 끝 글자 다음에 쓴 비율과 그 앞 단어 다음에 쓴 비율을 차례로 보간한다
 *       (문맥마다 쓴 횟수만큼 믿는다, {@link Context#score}).</li>
 *   <li>내 어미 버릇: 같은 어간의 꼴 중 내가 다른 말에서 자주 쓴 어미를 앞세운다 ({@link #E_MAX}).</li>
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

    /**
     * 내 기록의 문맥 (앞 단어의 끝 글자, 앞 단어)을 믿는 정도: 그 문맥 다음에 쓴 횟수 n과 다른 단어 수 t로
     * λ = n / (n + K·t) (Witten-Bell). 한 번 이어 쓴 앞 단어도 바로 절반쯤 믿어 빨리 배운다. 끝 글자 문맥('-고' 다음에
     * 내가 쓰는 말)은 여러 앞 단어에 걸친 버릇이라 A_END보다 더 믿지 않는다. K가 0이면 그 문맥을 쓰지 않는다.
     */
    static double K_END = 2.0, A_END = 0.15, K_BI = 1.0;

    /**
     * 입력 상태(0 = 다음 단어 예측, 1 = 한 글자, 2 = 두 글자, 3 = 세 글자 이상 쳤을 때)마다 내 단어 비율(mu)·최근 단어(CACHE_W)·
     * 내 기록 문맥(앞 단어와 그 끝 글자)을 믿는 정도에 곱하는 값 (dev로 맞춘 값). 한 글자일 때는 최근 단어가 가장 쓸모 있고(끄면
     * -0.45%p), 두 글자부터는 내가 쓴 말을 더 믿고 최근 단어는 덜 믿는 편이, 예측은 내 문맥을 더 믿고 내 단어 비율은 덜 믿는
     * 편이 낫다. 곱한 값이 1을 넘지 않게 자른다 (내 단어 비율을 너무 믿으면 사전에 있는 말이 사라진다: 두 글자 ×3은 -1.6%p).
     */
    static final double[] USER_BY_STATE = {0.5, 1, 1.5, 0.5}, CACHE_BY_STATE = {1, 1, 0, 1}, CTX_BY_STATE = {1.5, 0.5, 1, 1};

    /** 입력 중인 word의 상태 (한글 글자·자모 수, 0이면 예측). */
    static int stateOf(String word) {
        if (word == null || word.isEmpty()) return 0;
        int n = 0;
        for (int i = 0; i < word.length(); i++) {
            char c = word.charAt(i);
            if (c >= 0xAC00 && c <= 0xD7A3 || c >= 0x3131 && c <= 0x3163) n++;
        }
        return Math.max(1, Math.min(3, n));
    }

    /** 최근에 쓴 단어(UsageHistory.RECENT개)를 믿는 비율: 대화 안에서 되풀이되는 말(이름, 주제어)을 앞세운다. */
    static double CACHE_W = 0.03;

    /**
     * 어미 버릇 (내가 쓴 어미를 다른 어간에도): 후보를 {종류, 앞 꼴, 어미}로 나눠({@link KoreanForms#split}), 같은 앞 꼴 후보들의
     * 점수 합 × 내가 그 종류에서 그 어미를 쓴 비율 × ε를 더한다 (없어·싶어처럼 '-어'를 많이 쓰면 있다보다 있어를 앞세운다).
     * ε = E_MAX × N / (N + E_HALF) (N = 그 종류의 어미를 쓴 횟수). 후보 사이의 순서만 바꾸므로 어색한 꼴이 새로 생기지 않는다.
     */
    static double E_MAX = 0.5, E_HALF = 50;

    /**
     * 다음 단어 예측을 보여 줄 가장 높은 후보의 최소 점수 P(w|앞 단어) (사람이 쓴 글로 맞춘 값). 내 기록을 쌓으며 쳤을 때
     * 예전 기준(근거 조각 중 큰 것 ≥ 0.25)보다 예측이 두세 배 자주 보이고 맞힌 예측도 두 배쯤 많으며, 보인 예측의 세 칸 적중률은
     * 15~30%다 (기록이 없으면 10~25%). 더 내리면 틀린 예측이 도구 막대를 가리는 일이 늘어난다.
     */
    static double PREDICT_MIN = 0.08;

    /**
     * 예측을 보였지만 고르지 않고 다른 말을 친 횟수 g마다 그 예측 점수에 곱하는 값 (IGNORED_FACTOR^g). 앞 단어의 끝 글자
     * 문맥에서 고르지 않은 횟수는 IGNORED_END만큼만 센다. 그 말을 그 앞 단어 다음에 실제로 쓰면 기록을 지운다.
     */
    static double IGNORED_FACTOR = 0.5, IGNORED_END = 0;

    /**
     * 이만큼은 고르지 않아도 깎지 않는다: 한두 번 지나친 예측도 다음에는 맞기도 하므로 세 번째부터 깎는다 (dev에서 늘 깎는 것보다
     * 맞힌 예측을 덜 잃었다).
     */
    static double IGNORED_GRACE = 2;

    /** 내가 이어 쓴 기록에서 예측 후보로 쓸 최소 횟수 (한 번 이어 쓴 것만으로 예측하면 틀린 예측이 크게 는다). */
    static int PRED_USER_MIN = 2;

    /** 앞 단어 끝 글자 문맥의 키 (낱말에는 ~가 없다). UsageHistory의 단어 쌍에 앞 단어 대신 넣는다. */
    static String endKey(String prev) {
        return prev == null || prev.isEmpty() ? null : "~" + prev.charAt(prev.length() - 1);
    }

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
        /** 예측일 때 맨 위 후보의 점수 (PREDICT_MIN과 견준다). */
        double confidence;

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

    /** 용언 활용형으로 나뉘는지 ({@link KoreanForms#split}, 평가용). */
    boolean inflected(String w) {
        return forms.split(w) != null;
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
        final double mu;            // 내 단어 비율을 믿는 비율
        final long total;
        final int field;
        final int recentN;          // 최근 단어 수
        // 내 기록 문맥 (일반 → 구체 순: 앞 단어 끝 글자, 앞 단어)의 키, 쓴 횟수 합, 믿는 비율
        final String[] uKey = new String[2];
        final int[] uN = new int[2];
        int state = -1;   // 입력 상태 (stateOf, -1 = 따지지 않음: 자동 수정 등)
        final double[] uA = new double[2];
        int uK;
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
            recentN = usage == null || CACHE_W <= 0 ? 0 : usage.recentSize();
            if (total > 0 && prev != null) {
                addContext(endKey(prev), K_END, A_END);
                addContext(prev, K_BI, 1);
            }
        }

        private void addContext(String key, double k, double cap) {
            if (key == null || k <= 0) return;
            int[] st = usage.contextStatsIfLoaded(key);
            if (st[0] == 0) return;
            uKey[uK] = key;
            uN[uK] = st[0];
            uA[uK] = Math.min(cap, st[0] / (st[0] + k * st[1]));
            uK++;
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

        /** 내가 w를 쓴 비율. */
        double mine(String w) {
            int c = usage.countIfLoaded(w);
            if (user != null) c = Math.max(c, user.knownCountIfLoaded(w));
            return c / (total + 30.0);
        }

        /** 입력 상태에 따른 배율 (상태를 따지지 않으면 1). */
        private double byState(double[] f) {
            return state < 0 ? 1 : f[state];
        }

        /**
         * P(w|앞 단어): 기본 사전 모형과 내 단어 비율, 최근 단어를 섞은 위에 내 기록 문맥을 일반 → 구체 순으로 보간한다
         * (p ← λ·c(문맥, w)/c(문맥) + (1-λ)·p).
         */
        double score(String w, int id) {
            double m = Math.min(1, mu * byState(USER_BY_STATE));
            double p = m == 0 ? model(id) : (1 - m) * model(id) + m * mine(w);
            double cw = Math.min(1, CACHE_W * byState(CACHE_BY_STATE));
            if (recentN > 0) p = (1 - cw) * p + cw * usage.recentCount(w) / recentN;
            double xs = byState(CTX_BY_STATE);
            for (int i = 0; i < uK; i++) {
                double a = Math.min(1, uA[i] * xs);
                p = a * usage.pairCountIfLoaded(uKey[i], w) / uN[i] + (1 - a) * p;
            }
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
        cx.state = stateOf(word);
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
        List<String> ranked = rank(pool, cx);

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
        cx.state = 0;
        Map<String, Double> pool = new HashMap<>();
        if (cx.group >= 0) addGroup(pool, cx, next, cx.group);
        if (cx.endGroup >= 0) addGroup(pool, cx, ending, cx.endGroup);
        for (int i = 0; i < cx.uK; i++) {   // 내가 앞 단어(와 그 끝 글자) 다음에 이어 쓴 말
            for (Map.Entry<String, Integer> e : usage.nextIfLoaded(cx.uKey[i], PRED_USER_MIN)) {
                add(pool, cx, e.getKey(), dict.indexOf(e.getKey()), 1);
            }
        }
        if (pool.isEmpty()) return Result.EMPTY;
        if (usage != null && IGNORED_FACTOR < 1) {
            String ek = endKey(prev);
            for (Map.Entry<String, Double> e : pool.entrySet()) {
                double g = usage.ignoredIfLoaded(prev, e.getKey());
                if (IGNORED_END > 0) g += IGNORED_END * usage.ignoredIfLoaded(ek, e.getKey());
                g -= IGNORED_GRACE;
                if (g > 0) e.setValue(e.getValue() * Math.pow(IGNORED_FACTOR, g));
            }
        }
        List<String> ranked = rank(pool, cx);
        double top = pool.get(ranked.get(0));
        if (top < PREDICT_MIN) return Result.EMPTY;
        int n = Math.min(max, ranked.size());
        Result r = new Result(ranked.subList(0, n).toArray(new String[0]), -1, true);
        r.confidence = top;
        return r;
    }

    private static void add(Map<String, Double> pool, Context cx, String w, int id, double factor) {
        double s = cx.score(w, id) * factor;
        if (s <= 0) return;
        Double old = pool.get(w);
        if (old == null || old < s) pool.put(w, s);
    }

    private List<String> rank(Map<String, Double> pool, Context cx) {
        if (E_MAX > 0 && cx.usage != null) learnedEndings(pool, cx);
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

    /** 어미 버릇을 볼 후보 수 (점수가 높은 것부터. 순위를 바꿀 수 있는 것만 나눠 본다). */
    private static final int ENDING_TOP = 16;

    /** 어미 버릇: 같은 앞 꼴 후보들 사이에서 내가 다른 말에서도 자주 쓴 어미를 앞세운다 ({@link #E_MAX}). */
    private void learnedEndings(Map<String, Double> pool, Context cx) {
        Map<String, Integer> tot = cx.usage.endingTotalsIfLoaded();
        if (tot == null || tot.isEmpty()) return;
        List<Map.Entry<String, Double>> top = new ArrayList<>(pool.entrySet());
        if (top.size() > ENDING_TOP) {
            top.sort((a, b) -> Double.compare(b.getValue(), a.getValue()));
            top = top.subList(0, ENDING_TOP);
        }
        Map<String, String[]> parts = new HashMap<>();
        Map<String, Double> fam = new HashMap<>();   // "아/어 꼴인지:앞 꼴" → 그 앞 꼴 후보들의 점수 합
        for (Map.Entry<String, Double> e : top) {
            String[] sp = forms.split(e.getKey());
            if (sp == null) continue;
            parts.put(e.getKey(), sp);
            fam.merge(isAo(sp) + ":" + sp[1], e.getValue(), Double::sum);
        }
        for (Map.Entry<String, String[]> e : parts.entrySet()) {
            String[] sp = e.getValue();
            // 어간 뒤(있+어)와 과거·추측 글자 뒤(했+어)는 붙는 어미가 같아 버릇을 함께 센다 (있·했처럼 둘로 다 보이는 앞 꼴도 있다)
            int nk, c;
            if (isAo(sp)) {
                nk = count(tot, KoreanForms.END_AO + "");
                c = count(tot, KoreanForms.END_AO + ":" + sp[2]);
            } else {
                nk = count(tot, KoreanForms.END_STEM + "") + count(tot, KoreanForms.END_PAST + "");
                c = count(tot, KoreanForms.END_STEM + ":" + sp[2]) + count(tot, KoreanForms.END_PAST + ":" + sp[2]);
            }
            if (nk == 0 || c == 0) continue;
            double eps = E_MAX * nk / (nk + E_HALF);
            pool.put(e.getKey(), pool.get(e.getKey()) + eps * fam.get(isAo(sp) + ":" + sp[1]) * c / nk);
        }
    }

    private static boolean isAo(String[] split) {
        return split[0].equals(KoreanForms.END_AO + "");
    }

    private static int count(Map<String, Integer> m, String k) {
        Integer v = m.get(k);
        return v == null ? 0 : v;
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
