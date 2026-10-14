package com.alternative_studios.newswipe.suggest;

import java.util.ArrayList;
import java.util.Arrays;
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
            // 만든 활용형은 사전 후보가 모자랄 때만 빈칸을 채운다 (사전에 있는 말을 밀어내면 손해가 크다)
            if (pool.size() < max) addEndings(pool, cx, word);
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
            // 이다 (서술격): 스타일이야, 친구예요, 사람인데, 효과적일까
            {"이야", "야", "0.02"}, {"이에요", "예요", "0.01"}, {"인데", null, "0.008"}, {"이지", "지", "0.004"},
            {"이라서", "라서", "0.004"}, {"이니까", "니까", "0.004"}, {"이고", "고", "0.004"}, {"이면", "면", "0.004"},
            {"이라고", "라고", "0.004"}, {"이래", "래", "0.002"}, {"이었어", "였어", "0.004"}, {"이었는데", "였는데", "0.002"},
            {"일까", null, "0.003"}, {"인가", null, "0.003"}, {"인가봐", null, "0.002"}, {"이잖아", "잖아", "0.002"},
            {"이었다", "였다", "0.002"}, {"이라는", "라는", "0.003"}, {"이랑은", "랑은", "0.001"},
            // 그 밖의 조사
            {"한테서", null, "0.002"}, {"에게서", null, "0.001"}, {"께서", null, "0.001"}, {"께", null, "0.001"},
            {"마다", null, "0.003"}, {"밖에", null, "0.003"}, {"조차", null, "0.001"}, {"마저", null, "0.001"},
            {"뿐", null, "0.001"}, {"이든", "든", "0.002"}, {"보다는", null, "0.001"}, {"으로서", "로서", "0.001"},
            {"이란", "란", "0.002"}, {"에선", null, "0.002"}, {"에서도", null, "0.003"}, {"한테는", null, "0.002"},
            {"한테도", null, "0.002"}, {"까지도", null, "0.001"}, {"만큼", null, "0.002"}, {"대로", null, "0.002"},
            {"은요", "는요", "0.002"}, {"이요", "요", "0.002"}, {"엔", null, "0.002"},
            {"이며", "며", "0.001"}, {"이든지", "든지", "0.0005"}, {"이라든지", "라든지", "0.0003"}, {"이나마", "나마", "0.0002"},
            {"이야말로", "야말로", "0.0002"},
            // 서술격 더
            {"입니다", null, "0.004"}, {"이었습니다", "였습니다", "0.001"}, {"인가요", null, "0.002"}, {"인지", null, "0.003"},
            {"이네", "네", "0.002"}, {"이네요", "네요", "0.001"}, {"이죠", "죠", "0.002"}, {"이라면", "라면", "0.003"},
            {"이었으면", "였으면", "0.001"}, {"이거든", "거든", "0.001"}, {"이거든요", "거든요", "0.001"}, {"인데요", null, "0.002"},
            {"이었지", "였지", "0.001"}, {"이겠지", "겠지", "0.001"}, {"일걸", null, "0.001"}, {"이래요", "래요", "0.001"},
            {"이라니", "라니", "0.001"}, {"이었네", "였네", "0.001"},
            // 겹조사: 뿐만, 밖에는, 까지만, 에서부터 ...
            {"뿐만", null, "0.001"}, {"뿐이야", null, "0.0006"}, {"뿐이에요", null, "0.0004"}, {"뿐인데", null, "0.0004"},
            {"뿐이다", null, "0.0004"}, {"뿐이지", null, "0.0003"}, {"뿐이고", null, "0.0002"}, {"뿐이었다", null, "0.0002"},
            {"뿐이었어", null, "0.0002"}, {"뿐이라고", null, "0.0002"}, {"뿐이니까", null, "0.0002"},
            {"밖에는", null, "0.0005"}, {"밖엔", null, "0.0003"}, {"밖에도", null, "0.0003"},
            {"만이", null, "0.002"}, {"만을", null, "0.002"}, {"만은", null, "0.001"}, {"만으로", null, "0.001"},
            {"만의", null, "0.001"}, {"만이라도", null, "0.0005"}, {"만도", null, "0.0003"}, {"만큼은", null, "0.0005"},
            {"만큼의", null, "0.0003"},
            {"까지는", null, "0.002"}, {"까지만", null, "0.001"}, {"까지의", null, "0.001"},
            {"에만", null, "0.001"},
            {"에서만", null, "0.001"}, {"에서의", null, "0.001"}, {"에서부터", null, "0.0005"}, {"에서처럼", null, "0.0002"},
            {"에게는", null, "0.001"}, {"에게도", null, "0.001"}, {"에게만", null, "0.0003"}, {"한테만", null, "0.0005"},
            {"부터는", null, "0.0005"}, {"부터도", null, "0.0003"}, {"보다도", null, "0.0005"}, {"조차도", null, "0.0003"},
            {"마저도", null, "0.0002"},
            {"으로만", "로만", "0.001"}, {"으로도", "로도", "0.002"}, {"으로부터", "로부터", "0.001"}, {"으로써", "로써", "0.0005"},
            {"과는", "와는", "0.001"}, {"과도", "와도", "0.001"}, {"과의", "와의", "0.001"}, {"이랑도", "랑도", "0.0005"},
    };
    private static final double[] PARTICLE_PRIOR = new double[PARTICLES.length];
    /**
     * 사람 뒤에만 오는 조사 (수께서, 평생한테는 같은 말이 나오지 않게): 사전에 그 말 + 한테·에게·께가 있어야 붙인다.
     * 그냥 한테·에게는 사전에 근거가 없어도 붙이되(오빠한테) 비율을 낮춘다.
     */
    private static final boolean[] PARTICLE_ANIMATE = new boolean[PARTICLES.length];

    static {
        for (int i = 0; i < PARTICLES.length; i++) {
            PARTICLE_PRIOR[i] = Double.parseDouble(PARTICLES[i][2]);
            String p = PARTICLES[i][0];
            PARTICLE_ANIMATE[i] = p.startsWith("께") || p.length() > 2 && (p.startsWith("한테") || p.startsWith("에게"));
        }
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
            boolean animate = mine || animate(stem);
            for (int i = 0; i < PARTICLES.length; i++) {
                if (PARTICLE_ANIMATE[i] && !animate) continue;
                String[] p = PARTICLES[i];
                String particle;
                if (p[1] == null) particle = p[0];
                else if (fin && !(rieul && p[1].startsWith("로"))) particle = p[0];   // ㄹ 받침 뒤에는 '로'
                else particle = p[1];
                // 한 글자 말 + 은요·는요·요는 '자는', '우는' 같은 용언 활용 뒤에 요를 붙인 꼴이 되기 쉽다
                if (k == 1 && particle.endsWith("요") && particle.length() <= 2) continue;
                String cand = stem + particle;
                if (cand.equals(word) || !HangulRange.startsWith(cand, word)) continue;
                int cid = dict.indexOf(cand);
                if (cid >= 0) continue;   // 사전에 있으면 사전 후보로 이미 들어간다
                double s = ps * PARTICLE_PRIOR[i] * PARTICLE_FACTOR;
                if (!animate && (p[0].startsWith("한테") || p[0].startsWith("에게"))) s *= 0.3;
                Double old = pool.get(cand);
                if (old == null || old < s) pool.put(cand, s);
            }
        }
    }

    // ---------------------------------------------------------------- 어미

    /**
     * '○○하다'의 어미 (하다 활용은 규칙적이라 모두 붙인다). 사전에 '○○하고', '○○했다' 같은 활용형이 있는 말에만 붙인다.
     * 짝은 {어미, 대략적인 비율}.
     */
    private static final String[] HA_ENDINGS = {
            "해", "해요", "해서", "해도", "해야지", "해야돼", "해줘", "해줘요", "해봐", "해볼까", "했어", "했어요", "했다",
            "했네", "했지", "했잖아", "했는데", "했으면", "했나봐", "했거든", "했더니", "했습니다", "하고", "하지", "하지만",
            "하네", "하네요", "하니", "하냐", "하자", "하면", "하니까", "하려고", "하잖아", "하거든", "하더라", "하던데",
            "하겠어", "하겠다", "하겠지", "하겠네", "할게", "할게요", "할래", "할래요", "할까", "할까요", "할거야", "할걸",
            "합니다", "하세요", "하나봐", "하기로",
    };
    /** '○○하다'인지 알아보는 활용형 (하나라도 사전에 있으면). */
    private static final String[] HA_EVIDENCE = {"하고", "하는", "했다", "했어", "해서", "하면", "한다", "하기", "해요", "했던"};
    /** 동사에만 붙는 하다 어미 (피곤해줘, 가능할래 같은 말이 나오지 않게). */
    private static final String[] HA_VERB_ONLY = {"해줘", "해줘요", "해봐", "해볼까", "하자", "하려고", "할게", "할게요", "할래",
            "할래요", "하기로"};
    /** 동사인지 알아보는 활용형 (형용사는 '피곤한다', '가능하자'가 없다). */
    private static final String[] HA_VERB_EVIDENCE = {"한다", "하는", "하자", "할게"};
    private static final String[] HA_ADJ_ENDINGS;

    static {
        List<String> adj = new ArrayList<>(Arrays.asList(HA_ENDINGS));
        adj.removeAll(Arrays.asList(HA_VERB_ONLY));
        HA_ADJ_ENDINGS = adj.toArray(new String[0]);
    }
    /** 일반 어간에 붙이는 어미: 자음으로 시작해 불규칙 활용과 상관없고 동사·형용사 모두에 붙는 것만. */
    private static final String[] STEM_ENDINGS = {
            "고", "지", "지만", "네", "네요", "니", "냐", "거든", "거든요", "더라", "더라고", "던데", "겠어", "겠어요", "겠다",
            "겠네", "겠지", "잖아", "잖아요", "죠", "기로", "게",
    };
    private static final String[] STEM_EVIDENCE = {"고", "지", "는", "네", "게", "기", "다", "던", "지만", "겠다"};
    /** 과거형(-았/었/였-, 받침 ㅆ) 뒤에 붙이는 어미. */
    private static final String[] PAST_ENDINGS = {
            "어", "어요", "다", "네", "네요", "지", "지만", "잖아", "잖아요", "거든", "거든요", "더라", "더라고", "던데",
            "는데", "고", "니", "냐", "구나", "대", "대요", "다고", "죠", "으면", "을까", "을걸", "겠다", "겠네", "겠지",
            "나봐", "나", "나요", "더니", "습니다",
    };
    private static final String[] PAST_EVIDENCE = {"어", "다", "지", "네", "는데", "고", "어요", "던", "으면"};
    /** 어미를 붙여 만든 말의 확률에 곱하는 값 (같은 어간의 사전 활용형 확률 합에 곱한다). */
    static double ENDING_FACTOR = 1.0;

    /**
     * 사전에 없는 활용형을 만들어 넣는다 ('추천해' → 추천해줘, '좋겠' → 좋겠지, '됐나' → 됐나봐). 사전에 같은 어간의 활용형이
     * 있어야 하며(하다는 하나, 일반 어간·과거형은 둘 이상), 규칙이 확실한 어미만 붙여 틀린 활용이 나오지 않게 한다.
     */
    private void addEndings(Map<String, Double> pool, Context cx, String word) {
        int n = word.length();
        for (int k = n; k >= Math.max(1, n - 4); k--) {
            String base = word.substring(0, k);
            char last = base.charAt(k - 1);
            if (last < 0xAC00 || last > 0xD7A3) continue;
            // 하다: base가 '○○하'로 끝나거나, 입력이 '○○'까지이고 뒤에 하·해·했·할이 오는 중
            if (last == '하' && k >= 2) addHa(pool, cx, word, base.substring(0, k - 1));
            int t = (last - 0xAC00) % 28;
            if (t == 20) generate(pool, cx, word, base, PAST_EVIDENCE, 2, null, PAST_ENDINGS, false);   // 받침 ㅆ
            // '있다', '했다'처럼 다로 끝난 말은 이미 끝난 활용형이다 (있다잖아, 했다겠네가 나오지 않게)
            if (last != '다' && !quoted(base)) generate(pool, cx, word, base, STEM_EVIDENCE, 2, "고", STEM_ENDINGS, t == 8);
        }
        // '추천해', '부업할'처럼 하다 활용 중간까지 친 말: 입력이 명사 + 해/했/할/한/합 ...
        for (int k = n - 1; k >= Math.max(1, n - 4); k--) {
            char c = word.charAt(k);
            if (c >= 0xAC00 && c <= 0xD7A3 && (c - 0xAC00) / 588 == 18 || c == 'ㅎ') {   // 초성 ㅎ (하·해·했·할 ...)
                addHa(pool, cx, word, word.substring(0, k));
            }
        }
    }

    /** '보자', '아니라'처럼 어간 + 자·라로 끝난 말 (보자던데, 아니라겠네가 나오지 않게). */
    private boolean quoted(String w) {
        char last = w.charAt(w.length() - 1);
        return w.length() >= 2 && (last == '자' || last == '라') && dict.contains(w.substring(0, w.length() - 1) + "고");
    }

    /**
     * '○○하다' 활용형. 형용사(피곤하다)에는 동사 어미를 붙이지 않는다. 한 글자 말은 뜻이 굳은 말(위해, 통해, 대해)이 많아,
     * 띄어 쓰지 않고 붙여 치는 안·못·잘·다와 사전에 '말해줘', '일할게' 같은 동사 활용이 있는 말에만 붙인다.
     */
    private void addHa(Map<String, Double> pool, Context cx, String word, String stem) {
        char last = stem.charAt(stem.length() - 1);
        if (last == '야' || last == '해') return;   // 해야하는 → '해야'는 하다 앞말이 아니다
        boolean verb = false;
        for (String e : HA_VERB_EVIDENCE) verb |= dict.contains(stem + e);
        if (stem.length() == 1) {
            boolean ok = "안못잘다".indexOf(last) >= 0;
            for (String e : HA_VERB_ONLY) ok |= dict.contains(stem + e);
            if (!ok) return;
            verb = true;
        }
        generate(pool, cx, word, stem, HA_EVIDENCE, 1, null, verb ? HA_ENDINGS : HA_ADJ_ENDINGS, false);
    }

    /**
     * stem + 어미 중 입력 word로 시작할 수 있는 것을 후보에 넣는다. stem + evidence 활용형이 사전에 minEvidence개 이상
     * 있어야 하고, must가 있으면 stem + must도 사전에 있어야 한다 (일반 어간은 '먹고'처럼 -고 꼴이 있어야 어간으로 본다:
     * '할', '있을'은 할지·할게가 있어도 어간이 아니다). dropRieul: ㄹ 받침 어간이면 네·니 앞에서 ㄹ을 뗀다 (놀 → 노네, 사니).
     */
    private void generate(Map<String, Double> pool, Context cx, String word, String stem, String[] evidence,
                          int minEvidence, String must, String[] endings, boolean dropRieul) {
        if (stem.isEmpty() || hangulSyllables(stem) != stem.length()) return;
        if (must != null && !dict.contains(stem + must)) return;
        double family = 0;
        int found = 0;
        for (String e : evidence) {
            int id = dict.indexOf(stem + e);
            if (id < 0) continue;
            found++;
            family += cx.score(stem + e, id);
        }
        if (found < minEvidence || family <= 0) return;
        for (String e : endings) {
            String s = stem;
            if (dropRieul && (e.startsWith("네") || e.startsWith("니"))) {
                char l = stem.charAt(stem.length() - 1);
                s = stem.substring(0, stem.length() - 1) + (char) (l - 8);
            }
            String cand = s + e;
            if (cand.equals(word) || !HangulRange.startsWith(cand, word) || dict.indexOf(cand) >= 0) continue;
            double sc = family * ENDING_FACTOR;
            Double old = pool.get(cand);
            if (old == null || old < sc) pool.put(cand, sc);
        }
    }

    /** 이 조사로 끝난 말은 명사로 보지 않는다. */
    private static final String[] JOINED = {"에서", "에게", "한테", "까지", "부터", "으로"};

    /**
     * 사전에 격조사를 붙인 말이 있으면 명사로 본다. 은·는·도는 부사(그냥은, 또는)와 용언 활용(가는, 먹은)에도 붙어 보지 않는다.
     * 받침 뒤 을·이는 용언 활용(먹을, 먹이)과 헷갈려, 하나만 있으면 그 말에 -고·-는·-지 꼴이 없을 때만 믿는다. '있다가'처럼
     * 다로 끝난 용언 활용형도 뺀다.
     */
    boolean nounLike(String w) {
        int n = w.length();
        char last = w.charAt(n - 1);
        if (last < 0xAC00 || last > 0xD7A3) return false;
        if (last == '다' && n >= 2 && dict.contains(w.substring(0, n - 1) + "고")) return false;
        // 이미 조사가 붙은 말 (시장에서, 지금까지, 앞으로) 뒤에 또 조사를 붙이지 않는다
        for (String p : JOINED) {
            if (w.endsWith(p) && (w.length() == p.length() || dict.contains(w.substring(0, n - p.length())))) return false;
        }
        int t = (last - 0xAC00) % 28;
        boolean fin = t != 0;
        // 가고·가는이 있는 '가'처럼 용언 어간이기도 한 말은 격조사 꼴이 둘 이상 있어야 한다
        boolean verb = dict.contains(w + "고") && (dict.contains(w + "는") || dict.contains(w + "지"))
                || "아어여워와해".indexOf(last) >= 0;   // 걸어(가), 들어(가): 용언 + 어 꼴
        String[] cases = {"의", "에", "에서", fin && t != 8 ? "으로" : "로", fin ? "과" : "와", "에게", "한테",
                fin ? "을" : "를", fin ? "이" : "가"};
        int strong = 0, weak = 0;
        for (int i = 0; i < cases.length; i++) {
            if (!dict.contains(w + cases[i])) continue;
            if (fin && i >= cases.length - 2) weak++;
            else strong++;
        }
        if (verb) return strong >= 2;
        return strong >= 1 || weak == 2 || weak == 1 && !dict.contains(w + "고") && !dict.contains(w + "는")
                && !dict.contains(w + "지");
    }

    /** 사람·동물 뒤에 오는 조사(한테, 에게, 께)를 붙일 만한 말인지: 사전에 그런 조사를 붙인 말이 있어야 한다. */
    private boolean animate(String w) {
        return dict.contains(w + "에게") || dict.contains(w + "한테") || dict.contains(w + "께서") || dict.contains(w + "께");
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
