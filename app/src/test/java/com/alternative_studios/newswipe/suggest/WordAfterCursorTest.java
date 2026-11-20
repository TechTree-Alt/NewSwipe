package com.alternative_studios.newswipe.suggest;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class WordAfterCursorTest {
    @Test
    public void koreanTakesRestOfWord() {
        // '신나게 우|치원에서 뛰놀았습니다'
        assertEquals("치원에서", WordSuggester.wordAfterCursor("치원에서 뛰놀았습니다", true));
        assertEquals("", WordSuggester.wordAfterCursor(" 뛰놀았습니다", true));
        assertEquals("치원", WordSuggester.wordAfterCursor("치원.", true));
        assertEquals("", WordSuggester.wordAfterCursor(null, true));
    }

    @Test
    public void englishKeepsInnerApostropheOnly() {
        assertEquals("llo", WordSuggester.wordAfterCursor("llo world", false));
        assertEquals("'t", WordSuggester.wordAfterCursor("'t go", false));
        assertEquals("lo", WordSuggester.wordAfterCursor("lo' said", false));
    }
}
