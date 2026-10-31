package com.alternative_studios.newswipe.keyboard;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class CjkLayoutTest {

    private static Key find(KeyboardLayout l, String label) {
        for (KeyboardLayout.Row r : l.rows) for (Key k : r.keys) if (label.equals(k.label)) return k;
        return null;
    }

    @Test
    public void flickKeysCarryFourDirections() {
        KeyboardLayout l = KeyboardLayout.japanese(null, false);
        assertEquals(KeyboardLayout.JAPANESE, l.kind);
        assertTrue(l.isLetters());
        Key ka = find(l, "か");
        assertNotNull(ka);
        assertEquals("き", ka.swipeText(Key.SWIPE_LEFT));
        assertEquals("く", ka.swipeText(Key.SWIPE_UP));
        assertEquals("け", ka.swipeText(Key.SWIPE_RIGHT));
        assertEquals("こ", ka.swipeText(Key.SWIPE_DOWN));
        assertTrue(ka.flick);
        assertNotNull(find(l, "゛"));
        // 맨 아래 줄은 다른 자판과 같고, 쉼표·온점은 전각이 된다 (설정은 원래 자리 이름을 따른다)
        Key period = find(l, "。");
        assertNotNull(period);
        assertEquals(".", period.slot());
        assertNull(find(l, "."));
        int deletes = 0;
        for (KeyboardLayout.Row r : l.rows) for (Key k : r.keys) if (k.type == Key.DELETE) deletes++;
        assertEquals(1, deletes);
    }

    @Test
    public void qwertyVariantsUseFullWidthPunctuation() {
        KeyboardLayout zh = KeyboardLayout.chinese(null);
        assertEquals(KeyboardLayout.CHINESE, zh.kind);
        assertNotNull(find(zh, "q"));
        assertNotNull(find(zh, "。"));
        KeyboardLayout ja = KeyboardLayout.japanese(null, true);
        assertEquals(KeyboardLayout.JAPANESE, ja.kind);
        assertNotNull(find(ja, "q"));
        assertNotNull(find(ja, "。"));
        // 영어 자판은 그대로다
        assertNotNull(find(KeyboardLayout.english(null), "."));
    }

    @Test
    public void symbolsBackLabel() {
        Key back = null;
        for (KeyboardLayout.Row r : KeyboardLayout.symbols("あ", null).rows) {
            for (Key k : r.keys) if (k.type == Key.TO_LETTERS) back = k;
        }
        assertNotNull(back);
        assertEquals("あ", back.label);
    }
}
