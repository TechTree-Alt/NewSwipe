package com.alternative_studios.newswipe.keyboard;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

public class KeyboardSwipesTest {
    private static KeyboardSwipes with(Map<String, String> m) {
        return KeyboardSwipes.from((slot, dir) -> m.get(slot + "_" + dir));
    }

    @Test
    public void defaultsAreTwoFingerUndoRedo() {
        KeyboardSwipes k = KeyboardSwipes.twoFingerUndo();
        assertEquals(SwipeAction.UNDO, k.action(KeyboardSwipes.TWO, Key.SWIPE_LEFT));
        assertEquals(SwipeAction.REDO, k.action(KeyboardSwipes.TWO, Key.SWIPE_RIGHT));
        assertEquals(SwipeAction.NONE, k.action(KeyboardSwipes.TWO, Key.SWIPE_UP));
        for (int dir = 0; dir < 4; dir++) assertEquals(SwipeAction.NONE, k.action(KeyboardSwipes.ONE, dir));
        assertTrue(k.uses(KeyboardSwipes.TWO));
        assertFalse(k.uses(KeyboardSwipes.ONE));
    }

    @Test
    public void overridesReplaceDefaults() {
        Map<String, String> m = new HashMap<>();
        m.put("one_up", SwipeAction.HIDE_KEYBOARD);
        m.put("two_left", SwipeAction.NONE);
        m.put("two_right", SwipeAction.NONE);
        KeyboardSwipes k = with(m);
        assertEquals(SwipeAction.HIDE_KEYBOARD, k.action(KeyboardSwipes.ONE, Key.SWIPE_UP));
        assertTrue(k.uses(KeyboardSwipes.ONE));
        assertFalse(k.uses(KeyboardSwipes.TWO));
    }

    @Test
    public void unknownOrCursorMoveFallsBackToDefault() {
        Map<String, String> m = new HashMap<>();
        m.put("two_left", "no_such_action");
        m.put("one_down", SwipeAction.CURSOR_MOVE);
        KeyboardSwipes k = with(m);
        assertEquals(SwipeAction.UNDO, k.action(KeyboardSwipes.TWO, Key.SWIPE_LEFT));
        assertEquals(SwipeAction.NONE, k.action(KeyboardSwipes.ONE, Key.SWIPE_DOWN));
    }

    @Test
    public void freeCursorOnlyForOneFinger() {
        Map<String, String> m = new HashMap<>();
        m.put("one_left", SwipeAction.FREE_CURSOR);
        m.put("one_right", SwipeAction.FREE_CURSOR);
        m.put("two_up", SwipeAction.FREE_CURSOR);   // 두 손가락에서는 쓸 수 없다
        KeyboardSwipes k = with(m);
        assertEquals(SwipeAction.FREE_CURSOR, k.action(KeyboardSwipes.ONE, Key.SWIPE_LEFT));
        assertEquals(SwipeAction.NONE, k.action(KeyboardSwipes.TWO, Key.SWIPE_UP));
        assertTrue(k.freeCursor(true));
        assertFalse(k.freeCursor(false));
        assertTrue(java.util.Arrays.asList(SwipeAction.idsForKeyboard(true)).contains(SwipeAction.FREE_CURSOR));
        assertFalse(java.util.Arrays.asList(SwipeAction.idsForKeyboard(false)).contains(SwipeAction.FREE_CURSOR));
        assertFalse(java.util.Arrays.asList(SwipeAction.idsForToolbar()).contains(SwipeAction.FREE_CURSOR));
    }
}
