package com.alternative_studios.newswipe.keyboard;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;

public class LongPressActionsTest {
    @Test
    public void defaultsMatchStandardBehavior() {
        String[] a = LongPressActions.actions(null);
        assertEquals(SwipeAction.NONE, a[LongPressActions.SHIFT]);
        assertEquals(SwipeAction.EMOJI_OPEN, a[LongPressActions.MODE]);
        assertEquals(SwipeAction.IME_PICKER, a[LongPressActions.GLOBE]);
        assertEquals(SwipeAction.NONE, a[LongPressActions.ENTER]);
        assertEquals(SwipeAction.DELETE_REPEAT, a[LongPressActions.DELETE]);
    }

    @Test
    public void resolveFiltersUnknownAndCursorActions() {
        assertEquals(SwipeAction.EMOJI_OPEN, LongPressActions.SPEC.resolve("bogus", LongPressActions.MODE, LongPressActions.DIR));
        assertEquals(SwipeAction.NONE, LongPressActions.SPEC.resolve(SwipeAction.CURSOR_MOVE, LongPressActions.MODE, LongPressActions.DIR));
        assertEquals(SwipeAction.CAPS_LOCK, LongPressActions.SPEC.resolve(SwipeAction.CAPS_LOCK, LongPressActions.SHIFT, LongPressActions.DIR));
    }

    @Test
    public void slotsCoverFunctionKeys() {
        assertEquals(LongPressActions.SHIFT, LongPressActions.slotOf(Key.SHIFT));
        assertEquals(LongPressActions.MODE, LongPressActions.slotOf(Key.TO_LETTERS));
        assertEquals(LongPressActions.GLOBE, LongPressActions.slotOf(Key.EMOJI));
        assertEquals(-1, LongPressActions.slotOf(Key.CHAR));
    }

    @Test
    public void longPressOnlyActionsStayOutOfSwipeLists() {
        assertTrue(Arrays.asList(SwipeAction.idsForLongPress()).contains(SwipeAction.DELETE_REPEAT));
        assertFalse(Arrays.asList(SwipeAction.idsForLongPress()).contains(SwipeAction.CURSOR_MOVE));
        assertFalse(Arrays.asList(SwipeAction.idsFor(FunctionSwipes.SPACE)).contains(SwipeAction.DELETE_REPEAT));
        assertFalse(Arrays.asList(SwipeAction.idsForKeyboard(true)).contains(SwipeAction.CAPS_LOCK));
    }
}
