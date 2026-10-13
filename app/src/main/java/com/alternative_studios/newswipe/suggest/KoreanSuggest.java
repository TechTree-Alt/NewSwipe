package com.alternative_studios.newswipe.suggest;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 한국어 추천·다음 단어 예측·자동 수정의 순위를 한 확률 모형으로 매긴다.
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
    /**
     * 자동 수정: 고칠 단어의 최소 빈도(100만 어절당; 앞 단어와 이어지는 단어 쌍이 있을 때는 MIN_FREQ_CONTEXT), 1·2위 점수 차.
     * 사전 점수는 사전 크기에 따라 기준이 달라지므로 빈도로 정한다 (드문 단어로 고치면 맞는 말을 잘못 고치기 쉽다).
     */
    static double CORRECT_MIN_FREQ = 320, CORRECT_MIN_FREQ_CONTEXT = 145;
    static double CORRECT_MARGIN = 0.7;
    /** 자동 수정에서 허용하는 오타 비용 (세 글자 이하, 네 글자 이상). */
    static float CORRECT_MAX_SHORT = 1.0f, CORRECT_MAX_LONG = 1.6f;
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

    /** 조사를 붙여 만든 말의 확률에 곱하는 값 (사전에 그 말이 없을 때만 만든다). */
    static double PARTICLE_FACTOR = 0.6;

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

    private final WordDictionary dict;
    private final NextWords next;
    private final TypoModel typo;
    private final double[] uni = new double[256];

    KoreanSuggest(WordDictionary dict, NextWords next, TypoModel typo) {
        this.dict = dict;
        this.next = next;
        this.typo = typo;
        if (next == null) NextWords.fillUnigram(uni, FALLBACK_LN_TOP, FALLBACK_LN_BOTTOM);
        else for (int s = 0; s < 256; s++) uni[s] = next.unigram(s);
    }

    WordDictionary dict() {
        return dict;
    }

    /** 단어 쌍이 하나라도 있는 다음 단어 사전을 쓰는지 (기본 사전의 것은 빈도 기준만 담고 단어 쌍이 없다). */
    boolean hasNextWords() {
        return next != null && next.pairs() > 0;
    }

    /** 빈도(100만 어절당)가 perMillion 이상인 가장 낮은 사전 점수. */
    int scoreFor(double perMillion) {
        for (int s = 1; s < 256; s++) if (uni[s] * 1e6 >= perMillion) return s;
        return 255;
    }

    /** 순위를 매기는 동안 쓰는 앞 단어 정보. */
    private final class Context {
        final String prev;
        final int group;            // 다음 단어 사전의 묶음 (-1 = 없음)
        final double lambda;
        final UserDictionary user;
        final UsageHistory usage;
        final double mu;            // 내 기록을 믿는 비율
        final long total;
        final int ctxCount;
        final int field;

        Context(String prev, int field, UserDictionary user, UsageHistory usage) {
            this.prev = prev;
            this.field = field;
            int pid = prev == null ? -1 : dict.indexOf(prev);
            group = pid >= 0 && next != null ? next.group(pid) : -1;
            lambda = group >= 0 ? next.lambda(group) : 0;
            this.user = user;
            this.usage = usage;
            total = usage == null ? 0 : usage.totalIfLoaded();
            mu = total == 0 ? 0 : USER_MAX * total / (total + USER_HALF);
            ctxCount = usage == null || prev == null ? 0 : usage.contextCountIfLoaded(prev);
        }

        /** 기본 사전 모형의 P(w|앞 단어). id = 사전 번호 (-1이면 사전에 없는 말). */
        double model(int id) {
            if (id < 0) return 0;
            double pu = uni[dict.score(id)];
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
        return 1;
    }

    // ---------------------------------------------------------------- 추천

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
            addNextInRanges(pool, cx, word);
            if (usage != null) {
                for (String w : usage.prefixIfLoaded(word, 1)) {
                    int id = dict.indexOf(w);
                    // 사전에 없는 말은 두 번 이상 썼을 때만 (한 번 친 오타가 추천에 끼어들지 않게)
                    if (id >= 0 || usage.countIfLoaded(w) >= 2) add(pool, cx, w, id, 1);
                }
            }
            if (user != null) {
                for (String w : user.lookupIfLoaded(word, true, 6)) add(pool, cx, w, dict.indexOf(w), 1);
            }
            addParticles(pool, cx, word);
            if ((SPLIT_ALWAYS || pool.size() < max) && hangulSyllables(word) >= 2) addSplits(pool, cx, word);
            if (pool.size() < max && hangulSyllables(word) >= 2) {
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

    /** 앞 단어 cx 다음에 자주 오는 단어 중 word로 시작할 수 있는 것을 후보에 넣는다. */
    private void addNextInRanges(Map<String, Double> pool, Context cx, String word) {
        if (cx.group < 0) return;
        int[] rg = new int[4];
        int nr = dict.ranges(word, rg);
        for (int r = 0; r < nr; r++) {
            for (int k = next.lowerBound(cx.group, rg[2 * r]); k < next.to(cx.group); k++) {
                int id = next.word(k);
                if (id >= rg[2 * r + 1]) break;
                if (!dict.word(id).equals(word)) add(pool, cx, dict.word(id), id, 1);
            }
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
            if (hangulSyllables(head) != k) continue;
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
                for (Object[] e : cx.usage.nextIfLoaded(head)) {
                    String w = (String) e[0];
                    if ((Integer) e[1] < 2 || w.equals(tail) || !HangulRange.startsWith(w, tail)) continue;
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
        if (cx.group >= 0) {
            for (int k = next.from(cx.group); k < next.to(cx.group); k++) {
                int id = next.word(k);
                add(pool, cx, dict.word(id), id, 1);
            }
        }
        if (usage != null) {
            for (Object[] e : usage.nextIfLoaded(prev)) {
                if ((Integer) e[1] < 2) continue;   // 한 번 이어 쓴 것만으로는 예측하지 않는다
                String w = (String) e[0];
                add(pool, cx, w, dict.indexOf(w), 1);
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

    private static int hangulSyllables(String w) {
        int n = 0;
        for (int i = 0; i < w.length(); i++) {
            char c = w.charAt(i);
            if (c >= 0xAC00 && c <= 0xD7A3) n++;
        }
        return n;
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

    // ---------------------------------------------------------------- 조사

    /** 조사와, 명사 뒤에서의 대략적인 비율. 받침 뒤(앞), 받침 없는 말 뒤(뒤) 짝. 짝이 없으면 한 칸만. */
    private static final String[][] PARTICLES = {
            {"이", "가", "0.15"}, {"을", "를", "0.15"}, {"은", "는", "0.13"}, {"에", null, "0.07"}, {"의", null, "0.05"},
            {"도", null, "0.05"}, {"으로", "로", "0.05"}, {"에서", null, "0.03"}, {"과", "와", "0.03"}, {"만", null, "0.02"},
            {"이랑", "랑", "0.015"}, {"한테", null, "0.012"}, {"까지", null, "0.01"}, {"에는", null, "0.01"},
            {"부터", null, "0.008"}, {"에게", null, "0.008"}, {"이나", "나", "0.006"}, {"처럼", null, "0.005"},
            {"보다", null, "0.005"}, {"하고", null, "0.005"}, {"에도", null, "0.005"}, {"으로는", "로는", "0.004"},
            {"에서는", null, "0.004"}, {"이라도", "라도", "0.003"},
    };
    private static final double[] PARTICLE_PRIOR = new double[PARTICLES.length];

    static {
        for (int i = 0; i < PARTICLES.length; i++) PARTICLE_PRIOR[i] = Double.parseDouble(PARTICLES[i][2]);
    }

    /** 명사에 조사를 붙인 말을 후보에 넣는다 ('민수ㄱ' → 민수가, '친구ㄹ' → 친구를). 사전에 이미 있는 말은 사전 점수를 쓴다. */
    private void addParticles(Map<String, Double> pool, Context cx, String word) {
        int n = word.length();
        for (int k = Math.max(1, n - 3); k <= n; k++) {
            String stem = word.substring(0, k);
            char last = stem.charAt(k - 1);
            if (last < 0xAC00 || last > 0xD7A3) continue;
            int sid = dict.indexOf(stem);
            boolean mine = cx.user != null && cx.user.knownCountIfLoaded(stem) > 0;
            if (!mine && (sid < 0 || !nounLike(stem))) continue;
            double ps = cx.score(stem, sid);
            if (ps <= 0) continue;
            boolean fin = (last - 0xAC00) % 28 != 0;
            boolean rieul = (last - 0xAC00) % 28 == 8;
            for (int i = 0; i < PARTICLES.length; i++) {
                String[] p = PARTICLES[i];
                String particle;
                if (p[1] == null) particle = p[0];
                else if (fin && !(rieul && p[1].startsWith("로"))) particle = p[0];   // ㄹ 받침 뒤에는 '로'
                else particle = p[1];
                String cand = stem + particle;
                if (cand.equals(word) || !HangulRange.startsWith(cand, word)) continue;
                int cid = dict.indexOf(cand);
                if (cid >= 0) continue;   // 사전에 있으면 사전 후보로 이미 들어간다
                double s = ps * PARTICLE_PRIOR[i] * PARTICLE_FACTOR;
                Double old = pool.get(cand);
                if (old == null || old < s) pool.put(cand, s);
            }
        }
    }

    /** 사전 단어에 흔한 조사를 붙인 말도 사전에 있으면 명사로 본다. */
    boolean nounLike(String w) {
        char last = w.charAt(w.length() - 1);
        if (last < 0xAC00 || last > 0xD7A3) return false;
        boolean fin = (last - 0xAC00) % 28 != 0;
        return dict.contains(w + (fin ? "을" : "를")) || dict.contains(w + (fin ? "이" : "가"))
                || dict.contains(w + (fin ? "은" : "는")) || dict.contains(w + "에서");
    }

    // ---------------------------------------------------------------- 자동 수정

    /**
     * 사전에 없는 word를 고칠 단어. 오타 비용(자판에서 가까운 키, 같은 키의 다른 동작 등)과 앞 단어와의 관계를 함께 본다.
     * 비슷하게 그럴듯한 후보가 둘 이상이면 고치지 않는다. 사전 단어(조사가 붙은 것 포함)인지는 부르는 쪽이 이미 보았다.
     */
    String correct(String word, String prev, int field, UserDictionary user, UsageHistory usage) {
        int syll = hangulSyllables(word);
        if (syll < 2 || syll != word.length()) return null;
        if (dict.contains(word) || WordSuggester.isDictWordWithParticle(dict, word)) return null;
        Context cx = new Context(prev, field, user, usage);
        char[] p = TypoModel.letters(word);
        float maxCost = syll <= 3 ? CORRECT_MAX_SHORT : CORRECT_MAX_LONG;
        char[] t = new char[72];
        double best = Double.NEGATIVE_INFINITY, second = Double.NEGATIVE_INFINITY;
        int bestId = -1;
        int minScore = scoreFor(CORRECT_MIN_FREQ);
        for (int id : dict.strongWords(scoreFor(CORRECT_MIN_FREQ_CONTEXT))) {
            int len = dict.length(id);
            if (len < syll - 1 || len > syll + 1 || len > 24) continue;
            String w = dict.word(id);
            int tn = TypoModel.letters(w.toCharArray(), 0, len, t);
            if (Math.abs(tn - p.length) > 2) continue;
            float c = typo.distance(p, p.length, t, tn, maxCost);
            if (c > maxCost || c <= 0f) continue;
            boolean context = cx.group >= 0 && next.find(cx.group, id) >= 0;
            if (dict.score(id) < minScore && !context) continue;
            double s = Math.log(cx.score(w, id)) - TYPO_PENALTY * c;
            if (s > best) {
                second = best;
                best = s;
                bestId = id;
            } else if (s > second) {
                second = s;
            }
        }
        if (bestId < 0 || best - second < CORRECT_MARGIN) return null;
        return dict.word(bestId);
    }
}
