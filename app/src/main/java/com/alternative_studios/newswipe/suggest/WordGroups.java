package com.alternative_studios.newswipe.suggest;

/**
 * 묶음마다 단어 번호를 오름차순으로 담은 목록: 앞 단어마다의 다음 단어({@link NextWords}), 앞 단어 끝 글자마다의 다음 단어
 * ({@link EndingContext}). 묶음 g의 단어는 [from(g), to(g)) 자리에 있다.
 */
interface WordGroups {
    int from(int g);

    int to(int g);

    /** 자리 i의 단어 번호. */
    int word(int i);

    /** 묶음 g 안에서 단어 번호가 w 이상인 첫 자리. */
    int lowerBound(int g, int w);
}
