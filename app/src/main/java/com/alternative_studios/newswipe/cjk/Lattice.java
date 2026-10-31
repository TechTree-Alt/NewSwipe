package com.alternative_studios.newswipe.cjk;

import java.util.ArrayList;
import java.util.List;

/**
 * 읽기의 칸(글자 위치) 사이를 잇는 단어들. 비용이 가장 작은 길을 찾아 문장 후보를 만든다 (Viterbi).
 * 중국어·일본어 변환이 함께 쓴다.
 */
final class Lattice {
    /** 사전에 든 단어 하나: from~to 칸을 덮는다. dictIndex가 -1이면 사전 밖의 글자(literal)를 그대로 쓴다. */
    static final class Edge {
        final int from, to, dictIndex, cost;
        final String literal;

        Edge(int from, int to, int dictIndex, int cost, String literal) {
            this.from = from;
            this.to = to;
            this.dictIndex = dictIndex;
            this.cost = cost;
            this.literal = literal;
        }
    }

    final int length;
    private final List<List<Edge>> from;

    Lattice(int length) {
        this.length = length;
        from = new ArrayList<>(length + 1);
        for (int i = 0; i <= length; i++) from.add(null);
    }

    void add(Edge e) {
        List<Edge> l = from.get(e.from);
        if (l == null) from.set(e.from, l = new ArrayList<>(4));
        l.add(e);
    }

    List<Edge> edgesFrom(int i) {
        List<Edge> l = from.get(i);
        return l != null ? l : java.util.Collections.emptyList();
    }

    /**
     * 처음부터 끝까지 비용이 가장 작은 길. 단어 하나를 지날 때마다 penalty를 더해 긴 단어를 앞세운다.
     * 끝까지 이어지지 않으면 null.
     */
    List<Edge> bestPath(int penalty) {
        long[] best = new long[length + 1];
        Edge[] back = new Edge[length + 1];
        java.util.Arrays.fill(best, Long.MAX_VALUE);
        best[0] = 0;
        for (int i = 0; i < length; i++) {
            if (best[i] == Long.MAX_VALUE) continue;
            for (Edge e : edgesFrom(i)) {
                long c = best[i] + e.cost + penalty;
                if (c < best[e.to]) {
                    best[e.to] = c;
                    back[e.to] = e;
                }
            }
        }
        if (best[length] == Long.MAX_VALUE) return null;
        List<Edge> path = new ArrayList<>();
        for (int at = length; at > 0; ) {
            Edge e = back[at];
            path.add(0, e);
            at = e.from;
        }
        return path;
    }

    static String textOf(List<Edge> path, ConversionDictionary dict) {
        StringBuilder b = new StringBuilder();
        for (Edge e : path) b.append(e.dictIndex >= 0 ? dict.firstText(e.dictIndex) : e.literal);
        return b.toString();
    }
}
