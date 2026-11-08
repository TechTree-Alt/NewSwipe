package com.alternative_studios.newswipe.keyboard;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

public class FunctionSwipesTest {
    private static FunctionSwipes with(Map<String, String> m) {
        return FunctionSwipes.from((slot, dir) -> m.get(slot + "_" + dir));
    }

    @Test
    public void slotsCoverTheFiveKeys() {
        assertEquals(FunctionSwipes.MODE, FunctionSwipes.slotOf(Key.TO_SYMBOLS));
        assertEquals(FunctionSwipes.MODE, FunctionSwipes.slotOf(Key.TO_LETTERS));
        assertEquals(FunctionSwipes.GLOBE, FunctionSwipes.slotOf(Key.LANGUAGE));
        assertEquals(FunctionSwipes.GLOBE, FunctionSwipes.slotOf(Key.EMOJI));
        assertEquals(FunctionSwipes.SPACE, FunctionSwipes.slotOf(Key.SPACE));
        assertEquals(FunctionSwipes.ENTER, FunctionSwipes.slotOf(Key.ENTER));
        assertEquals(FunctionSwipes.DELETE, FunctionSwipes.slotOf(Key.DELETE));
        assertEquals(-1, FunctionSwipes.slotOf(Key.CHAR));
        assertEquals(-1, FunctionSwipes.slotOf(Key.SHIFT));
        assertEquals(-1, FunctionSwipes.slotOf(Key.SYMBOL_PAGE));
    }

    @Test
    public void defaultsMatchStandardBehavior() {
        FunctionSwipes f = with(new HashMap<>());
        assertEquals(SwipeAction.EMOJI_OPEN, f.action(FunctionSwipes.MODE, Key.SWIPE_UP));
        assertEquals(SwipeAction.NONE, f.action(FunctionSwipes.MODE, Key.SWIPE_DOWN));
        assertEquals(SwipeAction.DELETE_WORD, f.action(FunctionSwipes.DELETE, Key.SWIPE_LEFT));
        assertEquals(SwipeAction.DELETE_LINE_START, f.action(FunctionSwipes.DELETE, Key.SWIPE_UP));
        assertEquals(SwipeAction.NONE, f.action(FunctionSwipes.DELETE, Key.SWIPE_DOWN));
        assertEquals(SwipeAction.NONE, f.action(FunctionSwipes.DELETE, Key.SWIPE_RIGHT));
        assertEquals(SwipeAction.NONE, f.action(FunctionSwipes.GLOBE, Key.SWIPE_UP));
        assertEquals(SwipeAction.NONE, f.action(FunctionSwipes.ENTER, Key.SWIPE_LEFT));
        for (int dir = 0; dir < 4; dir++) {
            assertEquals(SwipeAction.CURSOR_MOVE, f.action(FunctionSwipes.SPACE, dir));
        }
        assertTrue(f.cursorH());
        assertTrue(f.cursorV());
    }

    @Test
    public void overridesReplaceDefaults() {
        Map<String, String> m = new HashMap<>();
        m.put("globe_up", SwipeAction.CLIPBOARD_OPEN);
        m.put("delete_left", SwipeAction.NONE);          // 기본값(단어 지우기)을 없음으로
        m.put("space_up", SwipeAction.LINE_START);        // 세로 커서 이동 대신
        m.put("space_down", SwipeAction.LINE_END);
        FunctionSwipes f = with(m);
        assertEquals(SwipeAction.CLIPBOARD_OPEN, f.action(FunctionSwipes.GLOBE, Key.SWIPE_UP));
        assertEquals(SwipeAction.NONE, f.action(FunctionSwipes.DELETE, Key.SWIPE_LEFT));
        assertEquals(SwipeAction.LINE_START, f.action(FunctionSwipes.SPACE, Key.SWIPE_UP));
        assertTrue(f.cursorH());    // 좌우는 그대로 커서 이동
        assertFalse(f.cursorV());   // 위아래는 다른 기능이라 세로 이동은 쓰지 않는다
    }

    @Test
    public void cursorMoveOnEveryKey() {
        Map<String, String> m = new HashMap<>();
        m.put("enter_up", SwipeAction.CURSOR_MOVE);
        assertEquals(SwipeAction.CURSOR_MOVE, with(m).action(FunctionSwipes.ENTER, Key.SWIPE_UP));
        String[] enter = SwipeAction.idsFor(FunctionSwipes.ENTER);
        String[] space = SwipeAction.idsFor(FunctionSwipes.SPACE);
        assertEquals(space.length, enter.length);
        assertTrue(java.util.Arrays.asList(space).contains(SwipeAction.CURSOR_MOVE));
        assertTrue(java.util.Arrays.asList(enter).contains(SwipeAction.CURSOR_MOVE));
    }

    @Test
    public void unknownStoredValueFallsBackToDefault() {
        Map<String, String> m = new HashMap<>();
        m.put("mode_up", "no_such_action");
        assertEquals(SwipeAction.EMOJI_OPEN, with(m).action(FunctionSwipes.MODE, Key.SWIPE_UP));
    }

    @Test
    public void everyActionHasUniqueIdAndLabels() {
        java.util.Set<String> seen = new java.util.HashSet<>();
        for (String id : SwipeAction.idsFor(FunctionSwipes.SPACE)) {
            assertTrue(id, seen.add(id));
            assertTrue(id, SwipeAction.isKnown(id));
            assertFalse(id, SwipeAction.label(id).isEmpty());
            assertFalse(id, SwipeAction.shortLabel(id).isEmpty());
        }
        assertEquals("없음", SwipeAction.label(null));
    }

    /** 기능을 고르는 창의 갈래: '없음'을 포함한 모든 기능이 꼭 한 갈래에만 들어 있다. */
    @Test
    public void everyActionBelongsToExactlyOneCategory() {
        java.util.Set<String> all = new java.util.LinkedHashSet<>();
        all.addAll(java.util.Arrays.asList(SwipeAction.idsForKeyboard(true)));
        all.addAll(java.util.Arrays.asList(SwipeAction.idsFor(FunctionSwipes.GLOBE)));
        all.addAll(java.util.Arrays.asList(SwipeAction.idsForSpaceLongPress()));
        java.util.Set<String> seen = new java.util.HashSet<>();
        for (int c = 0; c < SwipeAction.categoryCount(); c++) {
            assertFalse(SwipeAction.categoryName(c).isEmpty());
            for (String id : SwipeAction.categoryIds(c)) {
                assertTrue(id, SwipeAction.isKnown(id));
                assertTrue(id, seen.add(id));
                assertEquals(id, c, SwipeAction.category(id));
            }
        }
        for (String id : all) {
            assertTrue(id, SwipeAction.category(id) >= 0);
        }
    }
}
