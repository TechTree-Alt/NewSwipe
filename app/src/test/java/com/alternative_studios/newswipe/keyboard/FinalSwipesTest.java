package com.alternative_studios.newswipe.keyboard;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

public class FinalSwipesTest {
    @Test
    public void defaultLayout() {
        assertEquals("ㄻ", FinalSwipes.get(null, 'ㄹ', FinalSwipes.LEFT));
        assertEquals("ㄺ", FinalSwipes.get(null, 'ㄹ', FinalSwipes.UP));
        assertEquals("ㅀ", FinalSwipes.get(null, 'ㄹ', FinalSwipes.RIGHT));
        assertEquals("ㅄ", FinalSwipes.get(null, 'ㅂ', FinalSwipes.RIGHT));
        assertEquals("ㄳ", FinalSwipes.get(null, 'ㄱ', FinalSwipes.RIGHT));
        assertEquals("ㄵ", FinalSwipes.get(null, 'ㄴ', FinalSwipes.UP));
        assertEquals("ㄶ", FinalSwipes.get(null, 'ㄴ', FinalSwipes.RIGHT));
        assertNull(FinalSwipes.get(null, 'ㄴ', FinalSwipes.LEFT));
        assertNull(FinalSwipes.get(null, 'ㅁ', FinalSwipes.UP));
    }

    @Test
    public void everySourceHasOptions() {
        for (char c : FinalSwipes.SOURCES.toCharArray()) {
            for (int dir = 0; dir < FinalSwipes.DIR_NAMES.length; dir++) {
                String d = FinalSwipes.defaultFor(c, dir);
                if (d != null) assertEquals(true, FinalSwipes.options(c).contains(d));
            }
        }
    }
}
