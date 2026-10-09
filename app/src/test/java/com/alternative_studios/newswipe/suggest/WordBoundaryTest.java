package com.alternative_studios.newswipe.suggest;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class WordBoundaryTest {
    @Test
    public void leftGoesToStartOfPreviousWord() {
        assertEquals(3, WordBoundary.distance("안녕 하세요", false));      // '하세요'
        assertEquals(3, WordBoundary.distance("안녕 ", false));            // 공백 + '안녕'
        assertEquals(1, WordBoundary.distance("hello,", false));          // 문장 부호 하나
        assertEquals(0, WordBoundary.distance("", false));
        assertEquals(2, WordBoundary.distance("  ", false));              // 공백뿐이면 글 처음까지
    }

    @Test
    public void rightGoesToEndOfNextWord() {
        assertEquals(5, WordBoundary.distance("hello world", true));
        assertEquals(6, WordBoundary.distance(" world", true));
        assertEquals(1, WordBoundary.distance(".next", true));
        assertEquals(4, WordBoundary.distance(" 123!", true));
    }

    @Test
    public void surrogatePairsAreNotSplit() {
        assertEquals(2, WordBoundary.distance("a😀", false));            // 이모지 하나 (UTF-16 두 칸)
        assertEquals(2, WordBoundary.distance("😀a", true));
    }
}
