package com.alternative_studios.newswipe.suggest;

import java.util.ArrayList;
import java.util.List;

/**
 * 사전으로 한국어 낱말의 꼴을 알아본다: 명사인지(격조사를 붙인 꼴이 사전에 있는지), 용언 어간인지(어간 + 고 ...), '○○하다'인지,
 * 과거형 글자 앞의 어간. 추천({@link KoreanForms})과 자동 수정({@link KoreanCorrect})이 같이 쓴다.
 */
final class KoreanMorph {
    private final WordDictionary dict;
    private final double[] uni;

    /** uni = 사전 점수(0~255) → 단어 확률. */
    KoreanMorph(WordDictionary dict, double[] uni) {
        this.dict = dict;
        this.uni = uni;
    }

    /** 사전 점수의 단어 확률. */
    double prob(int score) {
        return uni[score];
    }

    static int hangulSyllables(String w) {
        int n = 0;
        for (int i = 0; i < w.length(); i++) {
            char c = w.charAt(i);
            if (c >= 0xAC00 && c <= 0xD7A3) n++;
        }
        return n;
    }

    /** '○○하다'인지 알아보는 활용형 (하나라도 사전에 있으면). 형용사는 사전에 '불쌍한'만 있는 일이 많아 '한'도 본다. */
    static final String[] HA_EVIDENCE = {"하고", "하는", "했다", "했어", "해서", "하면", "한다", "하기", "해요", "했던",
            "한", "하게", "하다"};

    /** 과거·추측 글자(했, 졌, 봤, 먹었의 었, 겠) 앞의 어간 후보. */
    static List<String> presentStems(String prefix, char last) {
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
    boolean animate(String w) {
        return dict.contains(w + "에게") || dict.contains(w + "한테") || dict.contains(w + "께서") || dict.contains(w + "께");
    }

    /** 용언 어간인지: 사전에 어간 + 고와, 어간 + 는·지·다·게 중 하나가 있다. */
    boolean verbStem(String stem) {
        if (stem.isEmpty() || !dict.contains(stem + "고")) return false;
        return dict.contains(stem + "는") || dict.contains(stem + "지") || dict.contains(stem + "다") || dict.contains(stem + "게");
    }

    /** '○○하'의 ○○가 하다 용언인지 (사전에 하다 활용형이 있다). */
    boolean haStem(String stem) {
        if (stem.length() < 2 || stem.charAt(stem.length() - 1) != '하') return false;
        String head = stem.substring(0, stem.length() - 1);
        for (String e : HA_EVIDENCE) if (dict.contains(head + e)) return true;
        return false;
    }
}
