package com.alternative_studios.newswipe.cjk;

/** 변환 후보 하나: 넣을 글자와, 고르면 읽기에서 앞에서부터 몇 글자를 써 버리는지. */
public final class Candidate {
    public final String text;
    /** 읽기 문자열의 앞에서부터 쓰는 글자 수 (중국어는 병음 글자와 '를 센다). 읽기 전체면 그 길이. */
    public final int consumed;

    public Candidate(String text, int consumed) {
        this.text = text;
        this.consumed = consumed;
    }

    @Override
    public String toString() {
        return text + "/" + consumed;
    }
}
