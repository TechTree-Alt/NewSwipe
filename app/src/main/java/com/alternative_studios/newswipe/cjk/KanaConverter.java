package com.alternative_studios.newswipe.cjk;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 히라가나 읽기 → 일본어 후보. 사전은 읽기(히라가나)마다 한자·가타카나 표기를 담는다 (가나 그대로는 여기서 만든다).
 * 품사 연결 비용 없이 단어 비용만으로 문장을 나누므로, 긴 읽기는 앞에서부터 단어 단위로 골라 가며 바꾸는 것이 기본이다.
 */
public final class KanaConverter implements Converter {
    static final int WORD_PENALTY = 100;
    /** 사전에 없는 글자를 그대로 쓸 때의 글자당 비용 (조사 は·を 등). */
    static final int KANA_COST = 90;
    /** 문장을 나눌 때 읽기 한 글자짜리 한자 단어(葉·点 등)에 더하는 비용. 조사·어미를 한자로 잘못 바꾸지 않게 한다. */
    static final int SHORT_KANJI_PENALTY = 60;
    /** 문장 후보에서 가나 표기 대신 한자 표기를 쓸 비용 차이. */
    static final int KANJI_PREFERENCE = 40;
    private static final int MAX_WORD = 16, PREDICT_SCAN = 3000, PREDICT = 6;

    private final ConversionDictionary dict;

    public KanaConverter(ConversionDictionary dict) {
        this.dict = dict;
    }

    @Override
    public List<Candidate> convert(String in, int max) {
        List<Candidate> out = new ArrayList<>();
        int n = in.length();
        if (n == 0) return out;
        Lattice lat = new Lattice(n);
        for (int i = 0; i < n; i++) {
            for (int j = i + 1; j <= n && j - i <= MAX_WORD; j++) {
                byte[] k = ConversionDictionary.utf8(in.substring(i, j));
                int lb = dict.lowerBound(k);
                if (!dict.startsWith(lb, k)) break;   // 이 읽기로 시작하는 단어가 더 없다
                if (dict.isKey(lb, k)) {
                    boolean shortKanji = j - i == 1 && !isKana(dict.firstText(lb));
                    lat.add(new Lattice.Edge(i, j, lb, dict.bestCost(lb) + (shortKanji ? SHORT_KANJI_PENALTY : 0), null));
                }
            }
            lat.add(new Lattice.Edge(i, i + 1, -1, KANA_COST, in.substring(i, i + 1)));
        }
        Set<String> used = new HashSet<>();
        List<ConversionDictionary.Entry> es = new ArrayList<>();
        int exact = dict.find(ConversionDictionary.utf8(in));
        List<Lattice.Edge> path = lat.bestPath(WORD_PENALTY);
        String sentence = null;
        if (path != null && path.size() >= 2) {
            boolean anyWord = false;
            for (Lattice.Edge e : path) anyWord |= e.dictIndex >= 0;
            if (anyWord) sentence = sentenceText(path);
        }
        if (exact < 0 && sentence != null) add(out, used, sentence, n);
        if (exact >= 0) {
            dict.entries(exact, es, max);
            for (ConversionDictionary.Entry e : es) add(out, used, e.text, n);
            if (sentence != null) add(out, used, sentence, n);
        }
        if (n >= 2) predict(in, out, used, max);
        add(out, used, in, n);
        add(out, used, Kana.toKatakana(in), n);
        // 앞에서 시작하는 더 짧은 단어 (고르면 나머지 읽기가 남는다): 긴 것부터, 같으면 자주 쓰는 것부터.
        List<Lattice.Edge> starts = new ArrayList<>(lat.edgesFrom(0));
        starts.removeIf(e -> e.dictIndex < 0 || e.to == n);
        starts.sort((a, b) -> a.to != b.to ? b.to - a.to : a.cost - b.cost);
        for (Lattice.Edge e : starts) {
            if (out.size() >= max) break;
            es.clear();
            dict.entries(e.dictIndex, es, max);
            for (ConversionDictionary.Entry x : es) {
                if (out.size() >= max) break;
                add(out, used, x.text, e.to);
            }
        }
        if (out.size() > max) out.subList(max, out.size()).clear();
        return out;
    }

    /** 문장 후보의 글자: 단어마다 가장 자주 쓰는 표기를 쓰되, 가나 표기와 비용이 비슷한 한자 표기가 있으면 한자를 쓴다. */
    private String sentenceText(List<Lattice.Edge> path) {
        StringBuilder b = new StringBuilder();
        List<ConversionDictionary.Entry> es = new ArrayList<>();
        for (Lattice.Edge e : path) {
            if (e.dictIndex < 0) {
                b.append(e.literal);
                continue;
            }
            es.clear();
            dict.entries(e.dictIndex, es, 8);
            String text = es.get(0).text;
            if (isKana(text) && dict.key(e.dictIndex).length() > 1) {
                for (ConversionDictionary.Entry x : es) {
                    if (!isKana(x.text) && x.cost <= es.get(0).cost + KANJI_PREFERENCE) {
                        text = x.text;
                        break;
                    }
                }
            }
            b.append(text);
        }
        return b.toString();
    }

    /** 읽기로 시작하는 더 긴 단어 (예측): 자주 쓰는 것 몇 개. */
    private void predict(String in, List<Candidate> out, Set<String> used, int max) {
        byte[] k = ConversionDictionary.utf8(in);
        int lb = dict.lowerBound(k);
        List<int[]> found = new ArrayList<>();
        for (int i = lb, seen = 0; i < dict.size() && seen < PREDICT_SCAN && dict.startsWith(i, k); i++, seen++) {
            if (dict.key(i).length() > in.length()) found.add(new int[]{i, dict.bestCost(i)});
        }
        found.sort((a, b) -> a[1] - b[1]);
        for (int i = 0; i < found.size() && i < PREDICT && out.size() < max; i++) {
            add(out, used, dict.firstText(found.get(i)[0]), in.length());
        }
    }

    private static boolean isKana(String s) {
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (!Kana.isHiragana(c) && !(c >= 'ァ' && c <= 'ヶ') && c != 'ー') return false;
        }
        return true;
    }

    private static void add(List<Candidate> out, Set<String> used, String text, int consumed) {
        if (text.isEmpty() || !used.add(text)) return;
        out.add(new Candidate(text, consumed));
    }
}
