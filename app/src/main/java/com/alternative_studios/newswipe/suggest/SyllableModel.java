package com.alternative_studios.newswipe.suggest;

import java.util.Arrays;

/**
 * 사전 단어로 배운 음절 두 개짜리(바이그램) 언어 모형. 말이 한국어 낱말처럼 생겼는지를 로그 확률로 잰다 (자동 수정용).
 * 오타는 낯선 음절 이음을 만들기 쉽고(자댕, 사옹하세요), 사전에 없지만 맞는 말은 흔한 이음으로 되어 있다(혼수, 통학, 미역).
 * 단어마다 한 번씩 센다 (빈도로 무게를 주지 않는다: 사전에 없는 말은 드문 말이라 드문 단어의 모양을 닮는다).
 * 만든 뒤에는 읽기만 하므로 여러 스레드에서 써도 된다.
 */
final class SyllableModel {
    /** 기호: 음절 0~11171, 단어 시작, 단어 끝. */
    private static final int SYLL = 11172, START = SYLL, END = SYLL + 1, SYMBOLS = SYLL + 2;
    /** 앞 기호로 나온 횟수 (시작 기호 포함, 끝 기호 제외). */
    private final int[] left = new int[SYMBOLS];
    /** 바이그램 (앞 * SYMBOLS + 뒤) 을 정렬한 것과 그 횟수. */
    private final int[] keys, counts;
    private final double total, symbols;

    SyllableModel(WordDictionary dict) {
        int[] buf = new int[1 << 16];
        int n = 0;
        boolean[] seen = new boolean[SYMBOLS];
        for (int id = 0; id < dict.size(); id++) {
            String w = dict.word(id);
            int prev = START;
            for (int i = 0; i <= w.length(); i++) {
                int cur = i == w.length() ? END : sym(w.charAt(i));
                if (cur < 0) break;   // 한글 음절이 아닌 말은 세지 않는다 (사전은 한글뿐이다)
                if (n == buf.length) buf = Arrays.copyOf(buf, n * 2);
                buf[n++] = prev * SYMBOLS + cur;
                left[prev]++;
                seen[prev] = seen[cur] = true;
                prev = cur;
            }
        }
        Arrays.sort(buf, 0, n);
        int distinct = 0;
        for (int i = 0; i < n; i++) if (i == 0 || buf[i] != buf[i - 1]) distinct++;
        keys = new int[distinct];
        counts = new int[distinct];
        for (int i = 0, k = -1; i < n; i++) {
            if (i == 0 || buf[i] != buf[i - 1]) keys[++k] = buf[i];
            counts[k]++;
        }
        long sum = 0;
        for (int c : left) sum += c;
        total = sum;
        int v = 0;
        for (boolean b : seen) if (b) v++;
        symbols = v;
    }

    private static int sym(char c) {
        return c >= 0xAC00 && c <= 0xD7A3 ? c - 0xAC00 : -1;
    }

    /**
     * 단어의 로그 확률 (시작·끝 포함). P(뒤|앞) = (c(앞,뒤) + 2·P(뒤)) / (c(앞) + 2), P(뒤) = (c(뒤) + 0.5) / (전체 + 0.5·기호 수).
     * 한글 음절이 아닌 글자가 있으면 아주 작은 값.
     */
    double logProb(String w) {
        double lp = 0;
        int prev = START;
        for (int i = 0; i <= w.length(); i++) {
            int cur = i == w.length() ? END : sym(w.charAt(i));
            if (cur < 0) return -1e3;
            double pu = (left[cur] + 0.5) / (total + 0.5 * symbols);
            int at = Arrays.binarySearch(keys, prev * SYMBOLS + cur);
            lp += Math.log(((at >= 0 ? counts[at] : 0) + 2 * pu) / (left[prev] + 2));
            prev = cur;
        }
        return lp;
    }
}
