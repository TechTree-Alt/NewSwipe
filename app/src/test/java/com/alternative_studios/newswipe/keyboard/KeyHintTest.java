package com.alternative_studios.newswipe.keyboard;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

public class KeyHintTest {
    private static Key key(String... popup) {
        return new Key(Key.CHAR, "a", "a", "A", null, popup.length == 0 ? null : popup, 1f);
    }

    @Test
    public void hintShowsOnlyTheFirstCharacterOfALongFirstItem() {
        assertEquals("안", key("안녕하세요", "b").hint());
        assertEquals("h", key("hello").hint());
        assertEquals("1", key("1").hint());
    }

    @Test
    public void hintKeepsAnEmojiWhole() {
        assertEquals("😀", key("😀웃음").hint());
    }

    @Test
    public void hintUpperUsesTheFirstCharacterToo() {
        assertEquals("H", key("hello").hintUpper());
    }

    @Test
    public void noHintWithoutPopupOrWithEmptyFirstItem() {
        assertNull(key().hint());
        assertNull(key("", "x").hint());
    }

    @Test
    public void hintFollowsAChangedPopup() {
        Key k = key("abc");
        assertEquals("a", k.hint());
        k.popup = new String[]{"xyz"};
        assertEquals("x", k.hint());
        assertEquals("X", k.hintUpper());
    }
}
