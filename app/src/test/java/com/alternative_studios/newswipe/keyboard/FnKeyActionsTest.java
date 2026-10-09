package com.alternative_studios.newswipe.keyboard;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

public class FnKeyActionsTest {
    @Test
    public void defaultsOpenClipboardOnTapOnly() {
        String[] a = FnKeyActions.actions(null);
        assertEquals(6, a.length);
        assertEquals(SwipeAction.CLIPBOARD_OPEN, a[FnKeyActions.I_TAP]);
        assertEquals(SwipeAction.NONE, a[FnKeyActions.I_LONG]);
        for (int dir = 0; dir < 4; dir++) assertEquals(SwipeAction.NONE, a[FnKeyActions.I_SWIPE + dir]);
    }

    @Test
    public void storedValuesUseTapLongAndDirectionNames() {
        Map<String, String> m = new HashMap<>();
        m.put("press/tap", SwipeAction.UNDO);
        m.put("press/long", SwipeAction.DELETE_REPEAT);
        m.put("swipe/up", SwipeAction.EMOJI_OPEN);
        String[][] t = FnKeyActions.SPEC.table((slot, dir) -> m.get(slot + "/" + dir));
        assertEquals(SwipeAction.UNDO, t[FnKeyActions.PRESS][FnKeyActions.TAP]);
        assertEquals(SwipeAction.DELETE_REPEAT, t[FnKeyActions.PRESS][FnKeyActions.LONG]);
        assertEquals(SwipeAction.EMOJI_OPEN, t[FnKeyActions.SWIPE][Key.SWIPE_UP]);
        assertEquals(SwipeAction.NONE, t[FnKeyActions.SWIPE][Key.SWIPE_DOWN]);
    }

    @Test
    public void resolveDropsActionsNotOfferedThere() {
        SwipeSpec s = FnKeyActions.SPEC;
        // 계속 지우기는 길게 누르기에서만, Caps Lock·스페이스바 커서 이동은 어디서도 쓰지 않는다.
        assertEquals(SwipeAction.NONE, s.resolve(SwipeAction.DELETE_REPEAT, FnKeyActions.PRESS, FnKeyActions.TAP));
        assertEquals(SwipeAction.NONE, s.resolve(SwipeAction.DELETE_REPEAT, FnKeyActions.SWIPE, Key.SWIPE_LEFT));
        assertEquals(SwipeAction.NONE, s.resolve(SwipeAction.CAPS_LOCK, FnKeyActions.PRESS, FnKeyActions.LONG));
        assertEquals(SwipeAction.NONE, s.resolve(SwipeAction.CURSOR_MOVE, FnKeyActions.SWIPE, Key.SWIPE_UP));
        assertEquals(SwipeAction.CLIPBOARD_OPEN, s.resolve("bogus", FnKeyActions.PRESS, FnKeyActions.TAP));
        assertTrue(Arrays.asList(s.choices(FnKeyActions.PRESS, FnKeyActions.LONG)).contains(SwipeAction.DELETE_REPEAT));
        assertFalse(Arrays.asList(s.choices(FnKeyActions.PRESS, FnKeyActions.TAP)).contains(SwipeAction.DELETE_REPEAT));
    }

    @Test
    public void editorLabels() {
        SwipeSpec s = FnKeyActions.SPEC;
        assertEquals("탭", s.dirLabel(FnKeyActions.PRESS, FnKeyActions.TAP));
        assertEquals("길게 누르기", s.dirLabel(FnKeyActions.PRESS, FnKeyActions.LONG));
        assertEquals(SwipeCustom.label(Key.SWIPE_UP), s.dirLabel(FnKeyActions.SWIPE, Key.SWIPE_UP));
    }
}
