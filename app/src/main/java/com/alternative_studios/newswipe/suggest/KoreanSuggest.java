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
    static double CORRECT_MIN_FREQ = 260, CORRECT_MIN_FREQ_CONTEXT = 145;
    static double CORRECT_MARGIN = 0.7;
    /** 자동 수정에서 허용하는 오타 비용 (세 글자 이하, 네 글자 이상). */
    static float CORRECT_MAX_SHORT = 1.0f, CORRECT_MAX_LONG = 1.6f;
    /** 자동 수정에서 이 글자 수 이하의 말은 자모 한 번의 편집(바꾸기·빠뜨리기·더하기·맞바꾸기)으로 되는 단어로만 고친다. */
    static int CORRECT_ONE_EDIT_SYLL = 3;
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

    /**
     * 앞 단어의 끝 글자(조사·어미)로 본 다음 단어 분포를 믿는 최대 비율과, 그 절반에 이르는 앞 단어 수
     * ({@link EndingContext}). 앞 단어의 단어 쌍이 없을 때(사전에 없거나 드문 꼴)도 '을' 뒤에는 용언이, '는' 뒤에는 의존 명사가
     * 오기 쉽다는 것을 쓴다. 0이면 쓰지 않는다.
     */
    static double ENDING_MAX = 0.3, ENDING_HALF = 20;

    private final WordDictionary dict;
    private final NextWords next;
    private final EndingContext ending;
    private final TypoModel typo;
    private final double[] uni = new double[256];

    KoreanSuggest(WordDictionary dict, NextWords next, TypoModel typo) {
        this.dict = dict;
        this.next = next;
        this.typo = typo;
        if (next == null) NextWords.fillUnigram(uni, FALLBACK_LN_TOP, FALLBACK_LN_BOTTOM);
        else for (int s = 0; s < 256; s++) uni[s] = next.unigram(s);
        ending = EndingContext.build(dict, next);
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
        final int endGroup;         // 앞 단어 끝 글자의 묶음 ({@link EndingContext}, -1 = 없음)
        final double nu;            // 끝 글자 분포를 믿는 비율

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
            addEndingInRanges(pool, cx, word);
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
            addBound(pool, cx, word);
            // 만든 활용형은 사전 후보가 모자랄 때만 빈칸을 채운다 (사전에 있는 말을 밀어내면 손해가 크다)
            if (pool.size() < max) addBelow(pool, cx, word);
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

    /** 앞 단어의 끝 글자 다음에 자주 오는 단어 중 word로 시작할 수 있는 것을 후보에 넣는다. */
    private void addEndingInRanges(Map<String, Double> pool, Context cx, String word) {
        if (cx.endGroup < 0) return;
        int[] rg = new int[4];
        int nr = dict.ranges(word, rg);
        for (int r = 0; r < nr; r++) {
            for (int k = ending.lowerBound(cx.endGroup, rg[2 * r]); k < ending.to(cx.endGroup); k++) {
                int id = ending.word(k);
                if (id >= rg[2 * r + 1]) break;
                if (!pool.containsKey(dict.word(id)) && !dict.word(id).equals(word)) add(pool, cx, dict.word(id), id, 1);
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
        if (cx.endGroup >= 0) {
            for (int k = ending.from(cx.endGroup); k < ending.to(cx.endGroup); k++) {
                int id = ending.word(k);
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
            {"밖에", null, "0.003"},
            {"뿐", null, "0.001"}, {"이든", "든", "0.002"}, {"보다는", null, "0.001"}, {"으로서", "로서", "0.001"},
            {"이란", "란", "0.002"}, {"에선", null, "0.002"}, {"에서도", null, "0.003"}, {"한테는", null, "0.002"},
            {"한테도", null, "0.002"}, {"까지도", null, "0.001"}, {"만큼", null, "0.002"},
            {"은요", "는요", "0.002"}, {"이요", "요", "0.002"}, {"엔", null, "0.002"},
            {"이며", "며", "0.001"}, {"이든지", "든지", "0.0005"}, {"이라든지", "라든지", "0.0003"},
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
            {"까지는", null, "0.002"}, {"까지만", null, "0.001"}, {"까지의", null, "0.001"},
            {"에만", null, "0.001"},
            {"에서만", null, "0.001"}, {"에서의", null, "0.001"}, {"에서부터", null, "0.0005"},
            {"에게는", null, "0.001"}, {"에게도", null, "0.001"}, {"에게만", null, "0.0003"}, {"한테만", null, "0.0005"},
            {"부터는", null, "0.0005"}, {"부터도", null, "0.0003"}, {"보다도", null, "0.0005"},
            {"으로만", "로만", "0.001"}, {"으로도", "로도", "0.002"}, {"으로부터", "로부터", "0.001"}, {"으로써", "로써", "0.0005"},
            {"과는", "와는", "0.001"}, {"과도", "와도", "0.001"}, {"과의", "와의", "0.001"}, {"이랑도", "랑도", "0.0005"},
    };
    private static final double[] PARTICLE_PRIOR = new double[PARTICLES.length];
    /**
     * 사람 뒤에만 오는 조사 (수한테, 사업한테, 수께서 같은 말이 나오지 않게): 사전에 그 말 + 한테·에게·께가 있거나
     * 내가 쓴 말일 때만 붙인다. 근거 없는 사람 이름(오빠한테)은 한 번 쓰면 입력 기록으로 배운다.
     */
    private static final boolean[] PARTICLE_ANIMATE = new boolean[PARTICLES.length];

    static {
        for (int i = 0; i < PARTICLES.length; i++) {
            PARTICLE_PRIOR[i] = Double.parseDouble(PARTICLES[i][2]);
            String p = PARTICLES[i][0];
            PARTICLE_ANIMATE[i] = p.startsWith("께") || p.startsWith("한테") || p.startsWith("에게");
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
                Double old = pool.get(cand);
                if (old == null || old < s) pool.put(cand, s);
            }
        }
    }

    // ---------------------------------------------------------------- 의존 명사

    /**
     * 관형형 뒤에 띄어 쓰지 않고 붙여 치는 의존 명사 (좋아하는게, 힘든거, 할수, 갈때). 메신저에서는 띄어 쓰지 않는 일이 많다.
     * ㄴ 관형형(-는, -은, -ㄴ, -던) 뒤와 ㄹ 관형형(-ㄹ, -을) 뒤가 다르다. 짝은 {말, 대략적인 비율}.
     */
    private static final String[][] BOUND_N = {
            {"게", "0.25"}, {"거", "0.2"}, {"건", "0.1"}, {"걸", "0.06"}, {"거야", "0.05"}, {"거지", "0.03"},
            {"거같아", "0.03"}, {"건데", "0.02"}, {"걸까", "0.02"}, {"지", "0.02"}, {"듯", "0.02"}, {"줄", "0.02"},
            {"거니", "0.01"}, {"거면", "0.01"}, {"거예요", "0.01"}, {"건지", "0.01"}, {"건가", "0.01"}, {"거라", "0.01"},
    };
    private static final String[][] BOUND_L = {
            {"수", "0.25"}, {"때", "0.2"}, {"거야", "0.1"}, {"거", "0.05"}, {"줄", "0.05"}, {"거같아", "0.03"}, {"걸", "0.03"},
            {"듯", "0.03"}, {"수도", "0.03"}, {"때마다", "0.02"}, {"때는", "0.02"}, {"지도", "0.02"}, {"수있어", "0.02"},
            {"수있을까", "0.01"}, {"거면", "0.01"}, {"거니까", "0.01"}, {"만큼", "0.01"},
    };
    static double BOUND_FACTOR = 0.2;

    private void addBound(Map<String, Double> pool, Context cx, String word) {
        int n = word.length();
        for (int k = n; k >= Math.max(1, n - 3); k--) {
            String x = word.substring(0, k);
            char last = x.charAt(k - 1);
            if (last < 0xAC00 || last > 0xD7A3) continue;
            int t = (last - 0xAC00) % 28;
            String[][] list;
            // 관형형인지: ㄴ 관형형은 사전에 '-데'(좋아하는데, 힘든데), ㄹ 관형형은 '-까·-게·-래'(할까, 할게, 갈래)가 있어야 한다
            if (t == 4 && (dict.contains(x + "데") || adnominalStem(x))) list = BOUND_N;
            else if (t == 8 && (dict.contains(x + "까") || dict.contains(x + "게") || dict.contains(x + "래"))) list = BOUND_L;
            else continue;
            int id = dict.indexOf(x);
            double px = cx.score(x, id);
            if (px <= 0) continue;
            for (String[] b : list) {
                String cand = x + b[0];
                if (cand.equals(word) || !HangulRange.startsWith(cand, word) || dict.indexOf(cand) >= 0) continue;
                double sc = px * Double.parseDouble(b[1]) * BOUND_FACTOR;
                Double old = pool.get(cand);
                if (old == null || old < sc) pool.put(cand, sc);
            }
        }
    }

    /** ㄴ 받침 말이 용언의 관형형인지: 어간 + 고가 사전에 있으면 (해주는 → 해주고, 생긴 → 생기고, 힘든 → 힘들고). */
    private boolean adnominalStem(String x) {
        String head = x.substring(0, x.length() - 1);
        char last = x.charAt(x.length() - 1);
        if (last == '는' || last == '던') return !head.isEmpty() && dict.contains(head + "고");
        char open = (char) (last - 4);   // 받침 ㄴ을 뗀 글자
        return dict.contains(head + open + "고") || dict.contains(head + (char) (open + 8) + "고");
    }

    // ---------------------------------------------------------------- 어미

    /**
     * '○○하다'의 어미 (하다 활용은 규칙적이라 모두 붙인다). 사전에 '○○하고', '○○했다' 같은 활용형이 있는 말에만
     * 붙인다. 어미 목록(아래 일반 어간·과거형도)은 사람이 쓴 대화 문장(평가에 쓰지 않는 절반)에서 흔한 순서이고, 같은 어간이면
     * 앞의 것을 먼저 낸다.
     * 짝은 {어미, 대략적인 비율}.
     */
    private static final String[] HA_ENDINGS = {
            "해", "하고", "할까", "하면", "하네", "해도", "했어", "하는데", "하지", "했는데", "하게", "해야지", "해서", "해줘",
            "하겠어", "한데", "하려고", "하다",
            "하겠지", "할거야", "해볼까", "해요", "했습니다", "할까요", "합니다", "하니", "했으면", "했네", "하더라", "하니까", "했더니",
            "할걸", "하나봐", "했다", "했잖아", "하지만", "하네요", "했어요", "하냐", "하세요", "하자", "하던데", "했지", "하겠다", "할게요",
            "하잖아", "할게", "하겠네", "해봐", "할래", "해줘요", "했나봐", "했거든", "하기로", "하거든", "할래요", "해야돼",
    };
    /** '○○하다'인지 알아보는 활용형 (하나라도 사전에 있으면). 형용사는 사전에 '불쌍한'만 있는 일이 많아 '한'도 본다. */
    private static final String[] HA_EVIDENCE = {"하고", "하는", "했다", "했어", "해서", "하면", "한다", "하기", "해요", "했던",
            "한", "하게", "하다"};
    /** 동사에만 붙는 하다 어미 (피곤해줘, 가능할래 같은 말이 나오지 않게). */
    private static final String[] HA_VERB_ONLY = {"해줘", "해줘요", "해봐", "해볼까", "하자", "하려고", "할게", "할게요", "할래",
            "할래요", "하기로", "하는데"};
    /** 형용사에만 붙는 하다 어미 (불쌍한데 O, 추천한데 X). */
    private static final String[] HA_ADJ_ONLY = {"한데"};
    /** 동사인지 알아보는 활용형 (형용사는 '피곤한다', '가능하자'가 없다). */
    private static final String[] HA_VERB_EVIDENCE = {"한다", "하는", "하자", "할게"};
    private static final String[] HA_VERB_ENDINGS, HA_ADJ_ENDINGS;

    static {
        List<String> adj = new ArrayList<>(Arrays.asList(HA_ENDINGS));
        adj.removeAll(Arrays.asList(HA_VERB_ONLY));
        HA_ADJ_ENDINGS = adj.toArray(new String[0]);
        List<String> verb = new ArrayList<>(Arrays.asList(HA_ENDINGS));
        verb.removeAll(Arrays.asList(HA_ADJ_ONLY));
        HA_VERB_ENDINGS = verb.toArray(new String[0]);
    }
    /** 일반 어간에 붙이는 어미: 자음으로 시작해 불규칙 활용과 상관없고 동사·형용사 모두에 붙는 것만. */
    private static final String[] STEM_ENDINGS = {
            "고", "지", "게", "네", "겠어", "니", "겠지", "겠다", "죠", "지만", "기로", "냐", "더라", "네요", "겠네", "잖아",
            "겠어요", "던데", "잖아요", "거든", "거든요", "더라고",
    };
    private static final String[] STEM_EVIDENCE = {"고", "지", "는", "네", "게", "기", "다", "던", "지만", "겠다"};
    /** 과거형(-았/었/였-, 받침 ㅆ) 뒤에 붙이는 어미. */
    private static final String[] PAST_ENDINGS = {
            "어", "고", "지", "네", "다", "는데", "을까", "나", "으면", "니", "겠지", "대", "습니다", "겠다", "나봐", "죠",
            "다고", "더니", "지만", "어요", "나요", "냐", "더라", "네요", "구나", "겠네", "잖아", "대요", "던데", "잖아요", "거든",
            "거든요", "을걸", "더라고",
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
            if (t == 20) addPast(pool, cx, word, base);   // 받침 ㅆ
            // '있다', '했다'처럼 다로 끝난 말은 이미 끝난 활용형이다 (있다잖아, 했다겠네가 나오지 않게)
            if (last != '다' && !quoted(base)) generate(pool, cx, word, base, STEM_EVIDENCE, 2, "고", STEM_ENDINGS, t == 8);
        }
        for (int k = n; k >= Math.max(1, n - 3); k--) addEo(pool, cx, word, word.substring(0, k));
        // '불쌍'처럼 하다 앞말까지 친 말 → 불쌍해, 불쌍하다
        if (n >= 2 && hangulSyllables(word) == n) addHa(pool, cx, word, word);
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
        generate(pool, cx, word, stem, HA_EVIDENCE, 1, null, verb ? HA_VERB_ENDINGS : HA_ADJ_ENDINGS, false);
    }

    /** 만든 활용형을 이미 있는 후보 아래에 넣는다 (사전에 있는 '말자'가 만든 '말죠'에 밀리지 않게). 순서는 그대로 둔다. */
    private void addBelow(Map<String, Double> pool, Context cx, String word) {
        Map<String, Double> made = new HashMap<>();
        addEndings(made, cx, word);
        if (made.isEmpty()) return;
        double floor = Double.MAX_VALUE, top = 0;
        for (double v : pool.values()) floor = Math.min(floor, v);
        for (double v : made.values()) top = Math.max(top, v);
        double scale = pool.isEmpty() || top < floor ? 1 : floor * 0.9 / top;
        for (Map.Entry<String, Double> e : made.entrySet()) {
            if (!pool.containsKey(e.getKey())) pool.put(e.getKey(), e.getValue() * scale);
        }
    }

    /**
     * stem + 어미 중 입력 word로 시작할 수 있는 것을 후보에 넣는다. stem + evidence 활용형이 사전에 minEvidence개 이상
     * 있어야 하고, must가 있으면 stem + must도 사전에 있어야 한다 (일반 어간은 '먹고'처럼 -고 꼴이 있어야 어간으로 본다:
     * '할', '있을'은 할지·할게가 있어도 어간이 아니다). dropRieul: ㄹ 받침 어간이면 네·니 앞에서 ㄹ을 뗀다 (놀 → 노네, 사니).
     */
    private void generate(Map<String, Double> pool, Context cx, String word, String stem, String[] evidence,
                          int minEvidence, String must, String[] endings, boolean dropRieul) {
        emit(pool, word, stem, endings, dropRieul, family(cx, stem, evidence, minEvidence, must));
    }

    /** stem + evidence 활용형의 확률 합 (활용형이 minEvidence개보다 적거나 stem + must가 없으면 0). */
    private double family(Context cx, String stem, String[] evidence, int minEvidence, String must) {
        if (stem.isEmpty() || hangulSyllables(stem) != stem.length()) return 0;
        if (must != null && !dict.contains(stem + must)) return 0;
        double family = 0;
        int found = 0;
        for (String e : evidence) {
            int id = dict.indexOf(stem + e);
            if (id < 0) continue;
            found++;
            family += cx.score(stem + e, id);
        }
        return found < minEvidence ? 0 : family;
    }

    private void emit(Map<String, Double> pool, String word, String stem, String[] endings, boolean dropRieul,
                      double family) {
        if (family <= 0) return;
        for (int i = 0; i < endings.length; i++) {
            String e = endings[i];
            String s = stem;
            if (dropRieul && (e.startsWith("네") || e.startsWith("니"))) {
                char l = stem.charAt(stem.length() - 1);
                s = stem.substring(0, stem.length() - 1) + (char) (l - 8);
            }
            String cand = s + e;
            if (cand.equals(word) || !HangulRange.startsWith(cand, word) || dict.indexOf(cand) >= 0) continue;
            double sc = family * ENDING_FACTOR * (1 - 0.002 * i);   // 같은 어간이면 흔한 어미부터
            Double old = pool.get(cand);
            if (old == null || old < sc) pool.put(cand, sc);
        }
    }

    /** 아/어 꼴(먹어, 해, 바꿔) 뒤에 붙이는 말. 사람이 쓴 대화 문장(평가에 쓰지 않는 절반)에서 흔한 순서. */
    private static final String[] EO_ENDINGS = {"도", "서", "야지", "요", "줘", "볼까", "봐", "야할까", "야겠다", "야겠지", "있어",
            "야해", "야돼", "줬어", "야되는데", "야겠어", "놓고", "줘요", "줄래", "줄게", "주세요", "봐야지", "버렸어", "봤어", "보니까"};
    private static final String[] EO_EVIDENCE = {"도", "요", "야", "줘", "봐", "야지"};

    /**
     * 아/어 꼴 뒤의 말 (먹어 → 먹어야지, 먹어봐 / 해야 → 해야할까). 불규칙 활용(들어, 도와)은 사용자가 이미 친 꼴이라 따질
     * 필요가 없다. 사전에 그 꼴 + 서(먹어서)가 있고 다른 활용(먹어도, 먹어요 ...)도 하나 이상 있어야 용언으로 본다.
     */
    private void addEo(Map<String, Double> pool, Context cx, String word, String x) {
        char last = x.charAt(x.length() - 1);
        if (last < 0xAC00 || last > 0xD7A3 || (last - 0xAC00) % 28 != 0) return;
        int jung = (last - 0xAC00) % 588 / 28;
        if (jung != 0 && jung != 4 && jung != 6 && jung != 9 && jung != 14 && jung != 1 && jung != 10) return;   // ㅏㅓㅕㅘㅝㅐㅙ
        int id = dict.indexOf(x + "서");
        if (id < 0) return;
        double fam = cx.score(x + "서", id);
        int found = 0;
        for (String e : EO_EVIDENCE) {
            int j = dict.indexOf(x + e);
            if (j < 0) continue;
            found++;
            fam += cx.score(x + e, j);
        }
        if (found == 0) return;
        emit(pool, word, x, EO_ENDINGS, false, fam);
    }

    /** -겠- 뒤에 붙이는 어미 (과거형 어미 중 겠 뒤에 자연스러운 것만). */
    private static final String[] WILL_ENDINGS = {"어", "지", "네", "다", "는데", "고", "니", "냐", "죠", "어요", "습니다",
            "네요", "지만", "다고"};

    /**
     * 받침 ㅆ으로 끝난 base(떨어졌, 먹었, 못하겠) 뒤의 어미. 사전에 base의 활용형이 없어도, 줄기 전의 어간(떨어지, 먹, 못하)이
     * 사전에 있으면 만든다. 사용자가 과거형 글자를 이미 쳤으니 불규칙 활용을 따질 필요가 없다.
     */
    private void addPast(Map<String, Double> pool, Context cx, String word, String base) {
        double fam = family(cx, base, PAST_EVIDENCE, 2, null);
        char last = base.charAt(base.length() - 1);
        String prefix = base.substring(0, base.length() - 1);
        boolean will = last == '겠';
        if (fam <= 0) {
            for (String stem : presentStems(prefix, last)) {
                fam = family(cx, stem, STEM_EVIDENCE, 2, "고");
                if (fam > 0) break;
            }
        }
        emit(pool, word, base, will ? WILL_ENDINGS : PAST_ENDINGS, false, fam);
    }

    /** 과거·추측 글자(했, 졌, 봤, 먹었의 었, 겠) 앞의 어간 후보. */
    private static List<String> presentStems(String prefix, char last) {
        List<String> out = new ArrayList<>(2);
        int l = last - 0xAC00, cho = l / 588, jung = l % 588 / 28;
        if (last == '겠' || cho == 11 && (jung == 0 || jung == 4)) {   // 겠, 았·었: 앞이 어간 (먹었, 좋았, 못하겠)
            if (!prefix.isEmpty()) out.add(prefix);
            return out;
        }
        if (last == '했') {
            out.add(prefix + '하');
            return out;
        }
        int[][] map = {{6, 20}, {9, 8}, {14, 13}, {10, 11}, {1, 1}, {0, 0}, {4, 4}, {4, 18}, {5, 5}};   // 졌→지, 봤→보 ...
        for (int[] m : map) {
            if (m[0] == jung) out.add(prefix + (char) (0xAC00 + cho * 588 + m[1] * 28));
        }
        return out;
    }

    /** 이 조사로 끝난 말은 명사로 보지 않는다. */
    private static final String[] JOINED = {"에서", "에게", "한테", "까지", "부터", "으로"};

    /**
     * 사전에 격조사를 붙인 말이 있으면 명사로 본다. 은·는·도는 부사(그냥은, 또는)와 용언 활용(가는, 먹은)에도 붙어 보지 않는다.
     * '있다가'처럼 다로 끝난 용언 활용형도 뺀다.
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
        String[] cases = {"의", "에", "에서", fin ? "과" : "와", "에게", "한테"};
        int strong = 0;
        for (String c : cases) if (dict.contains(w + c)) strong++;
        if (!fin) {   // 받침 없는 말 뒤 를·가는 용언 활용과 헷갈리지 않는다
            if (dict.contains(w + "를")) strong++;
            if (dict.contains(w + "가")) strong++;
        }
        if (verb) return strong >= 2;
        if (strong >= 1) return true;
        // 받침 뒤 을은 용언 어간이 아닐 때만 (먹을 X, 월급을 O). 이·(으)로는 부사(일찍이, 절대로)에도 붙는다.
        boolean notVerb = !dict.contains(w + "고") && !dict.contains(w + "는") && !dict.contains(w + "지");
        if (fin && dict.contains(w + "을")) return notVerb;
        if (!notVerb) return false;
        // 하나뿐이면 조사 붙은 꼴이 맨말보다 아주 드물지 않아야 한다: 상관이·한쪽으로는 흔하고, 일찍이·정말로·절대로는 드물다
        double min = Math.max(freq(w) * WEAK_RATIO, Double.MIN_VALUE);
        return fin && freq(w + "이") >= min || freq(w + (fin && t != 8 ? "으로" : "로")) >= min;
    }

    static double WEAK_RATIO = 0.2;

    /** 사전에 있는 말의 빈도 (없으면 0). */
    private double freq(String w) {
        int id = dict.indexOf(w);
        return id < 0 ? 0 : uni[dict.score(id)];
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
        if (KEEP_PLAUSIBLE && plausible(word)) return null;
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
            // 짧은 말은 싼 편집 두 번(현타 → 한다, 파도 → 봐도)으로도 다른 흔한 말이 되기 쉬워 한 번의 편집만 고친다
            if (syll <= CORRECT_ONE_EDIT_SYLL && !oneEdit(p, p.length, t, tn)) continue;
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

    // ---------------------------------------------------------------- 그럴듯한 말 (고치지 않는다)

    /**
     * 자동 수정 전에 사전에 없는 말이 한국어 낱말로 그럴듯한지 본다. 사람이 쓴 대화에서 잘못 고친 말은 대부분 오타가 아니라
     * 사전에 없는 활용형·구어체(같애, 간대, 있대, 망했다, 고민이야)나 사전 단어의 앞부분인 명사(화장 → 화장실·화장품)였다.
     * 끄면(평가용) 예전처럼 사전에 없는 말을 모두 고칠 후보로 본다.
     */
    static boolean KEEP_PLAUSIBLE = true;
    /** 사전 단어의 앞부분으로 볼 최소 빈도 (그 말로 시작하는 더 긴 사전 단어들의 100만 어절당 빈도 합). */
    static double PLAUSIBLE_PREFIX_FREQ = 3;

    /** 용언 어간 바로 뒤에 붙는 어미 (구어체 포함). 받침에 섞여 들어가는 어미(간대의 ㄴ대)는 MERGED_ENDINGS. */
    private static final String[] FREE_ENDINGS = {
            "고", "지", "게", "네", "니", "냐", "다", "대", "래", "자", "죠", "지만", "기", "던", "더라", "든", "든지",
            "음", "어", "아", "애", "여", "어요", "아요", "어서", "아서", "어도", "아도", "어야", "아야", "러", "려고", "려",
            "면", "며", "으면", "으러", "으려고", "은", "을", "는", "는데", "은데", "을까", "을게", "을래", "잖아", "거든",
            "구나", "군", "겠다", "겠어", "겠지", "겠네", "네요", "지요", "대요", "냐고", "자고", "다고", "라고", "더니",
            "징", "당", "넹", "쥐", "셈", "듯", "음요",
    };
    /** 어간의 마지막 글자에 받침으로 들어가는 어미 첫소리와 그 뒤 (가+ㄴ대 = 간대, 가+ㄹ게 = 갈게, 되+ㅁ = 됨, 가+ㅂ니다). */
    private static final String[][] MERGED_ENDINGS = {
            {"ㄴ", "", "대", "데", "다", "지", "가", "거", "건", "걸", "게", "듯", "줄", "다고", "대요", "데요", "가요", "지도"},
            {"ㄹ", "", "게", "래", "까", "걸", "거", "건", "듯", "수", "때", "지", "께", "껄", "까요", "게요", "래요", "거야"},
            {"ㅁ", ""},
            {"ㅂ", "니다", "니까", "시다"},
    };
    /** 명사 뒤의 서술격·조사 중 PARTICLES에 없는 구어체 (고민임, 사람인듯, 친구냐). */
    private static final String[] COPULA_EXTRA = {"임", "인듯", "인거", "인게", "이냐", "냐", "이니", "이었음", "였음", "인지"};

    /** 사전에 없는 word가 한국어 낱말로 그럴듯한지 (사전 단어의 앞부분, 용언 어간 + 어미, 명사 + 조사·서술격). */
    boolean plausible(String word) {
        int n = word.length();
        if (prefixFreq(word) * 1e6 >= PLAUSIBLE_PREFIX_FREQ) return true;
        for (int k = n - 1; k >= 1; k--) {
            String stem = word.substring(0, k), tail = word.substring(k);
            if (nounWithParticle(stem, tail)) return true;
            if (contains(FREE_ENDINGS, tail) && verbStem(stem)) return true;
        }
        if (eoForm(word)) return true;
        // 받침에 어미가 섞인 말: 마지막 몇 글자 중 한 글자의 받침을 떼어 어간으로 본다 (간대 → 가 + ㄴ대)
        for (int k = n; k >= Math.max(1, n - 2); k--) {
            char c = word.charAt(k - 1);
            int t = (c - 0xAC00) % 28;
            String rest = word.substring(k);
            for (String[] m : MERGED_ENDINGS) {
                if (t != finalIndex(m[0].charAt(0))) continue;
                String stem = word.substring(0, k - 1) + (char) (c - t);
                for (int i = 1; i < m.length; i++) {
                    if (m[i].equals(rest) && (verbStem(stem) || copulaNoun(stem))) return true;
                }
            }
            // 과거형(받침 ㅆ) 뒤의 어미: 망했다 → 망하, 떨어졌대 → 떨어지
            if (t == 20 && (rest.isEmpty() || contains(FREE_ENDINGS, rest) || rest.equals("어") || rest.equals("음"))) {
                for (String stem : presentStems(word.substring(0, k - 1), c)) {
                    if (verbStem(stem) || haStem(stem)) return true;
                }
            }
        }
        return false;
    }

    /** 아/어 꼴 뒤에 오는 말 (EO_ENDINGS와 함께 본다): 힘들어져, 보내줘야, 행복해라, 먹어봐. */
    private static final String[] EO_TAIL_EXTRA = {"", "라", "져", "졌어", "지", "야겠네", "야지", "버려", "버렸네", "보고", "봐도", "봤는데"};

    /**
     * 용언의 아/어 꼴(먹어, 줄인 꼴 져·줘·봐·해·돼 ...) 뒤에 흔한 말이 붙은 말인지. 마지막 세 글자 안에서 아/어 꼴인 글자를 찾아
     * 어간을 되살리고(져 → 지, 줘 → 주, 봐 → 보, 해 → 하, 돼 → 되) 그 어간이 사전의 용언이어야 한다.
     */
    private boolean eoForm(String word) {
        int n = word.length();
        for (int k = n; k >= Math.max(2, n - 2); k--) {
            String rest = word.substring(k);
            if (!contains(EO_ENDINGS, rest) && !contains(EO_TAIL_EXTRA, rest)) continue;
            char c = word.charAt(k - 1);
            if (c < 0xAC00 || c > 0xD7A3 || (c - 0xAC00) % 28 != 0) continue;
            int cho = (c - 0xAC00) / 588, jung = (c - 0xAC00) % 588 / 28;
            String head = word.substring(0, k - 1);
            // 줄이지 않은 꼴: 먹 + 어, 잡 + 아, 하 + 여
            if (cho == 11 && (jung == 0 || jung == 4 || jung == 6) && verbStem(head)) return true;
            // 줄인 꼴: ㅕ ← ㅣ, ㅝ ← ㅜ, ㅘ ← ㅗ, ㅙ ← ㅚ, 해 ← 하
            int stemJung = jung == 6 ? 20 : jung == 14 ? 13 : jung == 9 ? 8 : jung == 10 ? 11 : -1;
            if (c == '해') {
                if (haStem(head + '하')) return true;
                continue;
            }
            if (stemJung < 0) continue;
            String stem = head + (char) (0xAC00 + cho * 588 + stemJung * 28);
            if (verbStem(stem)) return true;
        }
        return false;
    }

    /** word로 시작하는 더 긴 사전 단어들의 빈도 합 (단어 확률). 많아야 앞의 200개만 센다. */
    private double prefixFreq(String word) {
        int[] r = dict.prefixRange(word);
        double sum = 0;
        for (int i = r[0], seen = 0; i < r[1] && seen < 200; i++, seen++) {
            if (dict.length(i) > word.length()) sum += uni[dict.score(i)];
        }
        return sum;
    }

    /** 용언 어간인지: 사전에 어간 + 고와, 어간 + 는·지·다·게 중 하나가 있다. */
    private boolean verbStem(String stem) {
        if (stem.isEmpty() || !dict.contains(stem + "고")) return false;
        return dict.contains(stem + "는") || dict.contains(stem + "지") || dict.contains(stem + "다") || dict.contains(stem + "게");
    }

    /** '○○하'의 ○○가 하다 용언인지 (사전에 하다 활용형이 있다). */
    private boolean haStem(String stem) {
        if (stem.length() < 2 || stem.charAt(stem.length() - 1) != '하') return false;
        String head = stem.substring(0, stem.length() - 1);
        for (String e : HA_EVIDENCE) if (dict.contains(head + e)) return true;
        return false;
    }

    /** 명사 + 이다의 어간 '○○이' (받침 없는 명사는 ○○): 고민이 → 고민, 친구 → 친구. */
    private boolean copulaNoun(String stem) {
        String noun = stem.endsWith("이") && stem.length() >= 2 ? stem.substring(0, stem.length() - 1) : stem;
        int id = dict.indexOf(noun);
        return id >= 0 && noun.length() >= 2 && nounLike(noun);
    }

    /** stem이 사전의 명사이고 tail이 그 받침에 맞는 조사·서술격인지 (고민이야, 사람인듯, 친구예요). */
    private boolean nounWithParticle(String stem, String tail) {
        if (stem.length() < 2 && !dict.contains(stem)) return false;
        if (!dict.contains(stem) || !nounLike(stem)) return false;
        char last = stem.charAt(stem.length() - 1);
        if (last < 0xAC00 || last > 0xD7A3) return false;
        boolean fin = (last - 0xAC00) % 28 != 0, rieul = (last - 0xAC00) % 28 == 8;
        for (String[] p : PARTICLES) {
            String particle = p[1] == null ? p[0] : fin && !(rieul && p[1].startsWith("로")) ? p[0] : p[1];
            if (particle.equals(tail)) return true;
        }
        for (String c : COPULA_EXTRA) {
            if (c.equals(tail) && (fin || !c.startsWith("이"))) return true;
        }
        return false;
    }

    /** 자모열 a와 b가 정확히 한 번의 편집(바꾸기·빠뜨리기·더하기·맞바꾸기)으로 같아지는지. */
    static boolean oneEdit(char[] a, int an, char[] b, int bn) {
        if (Math.abs(an - bn) > 1) return false;
        int p = 0, min = Math.min(an, bn);
        while (p < min && a[p] == b[p]) p++;
        if (p == an && p == bn) return false;
        if (an == bn) {
            if (sameTail(a, p + 1, an, b, p + 1, bn)) return true;
            return p + 1 < an && a[p] == b[p + 1] && a[p + 1] == b[p] && sameTail(a, p + 2, an, b, p + 2, bn);
        }
        return an > bn ? sameTail(a, p + 1, an, b, p, bn) : sameTail(a, p, an, b, p + 1, bn);
    }

    private static boolean sameTail(char[] a, int ai, int an, char[] b, int bi, int bn) {
        if (an - ai != bn - bi) return false;
        for (; ai < an; ai++, bi++) if (a[ai] != b[bi]) return false;
        return true;
    }

    private static int finalIndex(char jamo) {
        return "\0ㄱㄲㄳㄴㄵㄶㄷㄹㄺㄻㄼㄽㄾㄿㅀㅁㅂㅄㅅㅆㅇㅈㅊㅋㅌㅍㅎ".indexOf(jamo);
    }

    private static boolean contains(String[] a, String s) {
        for (String x : a) if (x.equals(s)) return true;
        return false;
    }
}
