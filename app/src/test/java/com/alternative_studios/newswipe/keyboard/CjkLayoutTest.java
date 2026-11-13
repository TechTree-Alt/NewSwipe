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
        // Gboard처럼 5열: 가운데 3열이 가나, 양옆이 기능키이고 스페이스바 줄은 없다
        assertEquals(5, l.columns);
        assertEquals(4, l.rows.length);
        for (KeyboardLayout.Row r : l.rows) assertEquals(5, r.keys.length);
        assertEquals("゛", l.rows[0].keys[0].label);
        assertEquals("あ", l.rows[0].keys[1].label);
        assertEquals(Key.DELETE, l.rows[0].keys[4].type);
        assertEquals(Key.CURSOR_LEFT, l.rows[1].keys[0].type);
        assertEquals(Key.CURSOR_RIGHT, l.rows[1].keys[4].type);
        assertEquals(Key.SPACE, l.rows[2].keys[4].type);
        assertEquals(Key.TO_SYMBOLS, l.rows[3].keys[0].type);
        assertEquals(Key.LANGUAGE, l.rows[3].keys[1].type);
        assertEquals("わ", l.rows[3].keys[2].label);
        assertEquals("、", l.rows[3].keys[3].label);
        assertEquals("。", l.rows[3].keys[3].swipeText(Key.SWIPE_LEFT));
        assertEquals(Key.ENTER, l.rows[3].keys[4].type);
        int deletes = 0, spaces = 0;
        for (KeyboardLayout.Row r : l.rows) {
            for (Key k : r.keys) {
                if (k.type == Key.DELETE) deletes++;
                if (k.type == Key.SPACE) spaces++;
            }
        }
        assertEquals(1, deletes);
        assertEquals(1, spaces);
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
