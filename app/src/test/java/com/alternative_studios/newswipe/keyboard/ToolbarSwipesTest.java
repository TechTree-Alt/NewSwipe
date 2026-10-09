package com.alternative_studios.newswipe.keyboard;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

public class ToolbarSwipesTest {
    private static ToolbarSwipes with(Map<String, String> m) {
        return ToolbarSwipes.from((slot, dir) -> m.get(slot + "_" + dir));
    }

    @Test
    public void defaultsMatchStandardBehavior() {
        ToolbarSwipes t = with(new HashMap<>());
        assertEquals(SwipeAction.LINE_START, t.action(ToolbarSwipes.BAR, Key.SWIPE_LEFT));
        assertEquals(SwipeAction.LINE_END, t.action(ToolbarSwipes.BAR, Key.SWIPE_RIGHT));
        assertEquals(SwipeAction.CURSOR_UP, t.action(ToolbarSwipes.BAR, Key.SWIPE_UP));
        assertEquals(SwipeAction.CURSOR_DOWN, t.action(ToolbarSwipes.BAR, Key.SWIPE_DOWN));
        assertEquals(SwipeAction.PASTE, t.action(ToolbarSwipes.CLIPBOARD, Key.SWIPE_DOWN));
        assertEquals(SwipeAction.EMOJI_LATEST, t.action(ToolbarSwipes.EMOJI, Key.SWIPE_DOWN));
        assertEquals(SwipeAction.REDO, t.action(ToolbarSwipes.UNDO, Key.SWIPE_DOWN));
        assertEquals(SwipeAction.NONE, t.action(ToolbarSwipes.VOICE, Key.SWIPE_DOWN));
        assertEquals(SwipeAction.NONE, t.action(ToolbarSwipes.SETTINGS, Key.SWIPE_DOWN));
        assertEquals(SwipeAction.NONE, t.action(ToolbarSwipes.HIDE, Key.SWIPE_DOWN));
        assertEquals(SwipeAction.NONE, t.action(ToolbarSwipes.ONE_HAND, Key.SWIPE_UP));
        assertEquals(SwipeAction.NONE, t.action(ToolbarSwipes.ONE_HAND, Key.SWIPE_DOWN));
    }

    @Test
    public void oneHandButtonUpDownCanBeCustomized() {
        Map<String, String> m = new HashMap<>();
        m.put("onehand_down", SwipeAction.HIDE_KEYBOARD);
        m.put("onehand_left", SwipeAction.UNDO);   // 좌우는 한 손 모드 방향이라 정할 수 없다
        ToolbarSwipes t = with(m);
        assertEquals(SwipeAction.HIDE_KEYBOARD, t.action(ToolbarSwipes.ONE_HAND, Key.SWIPE_DOWN));
        assertEquals(SwipeAction.NONE, t.action(ToolbarSwipes.ONE_HAND, Key.SWIPE_LEFT));
    }

    @Test
    public void oneHandActionsAreOfferedEverywhere() {
        for (String[] ids : new String[][]{SwipeAction.idsForToolbar(), SwipeAction.idsFor(FunctionSwipes.SPACE),
                SwipeAction.idsForKeyboard(true), SwipeAction.idsForKeyboard(false)}) {
            assertTrue(Arrays.asList(ids).contains(SwipeAction.ONE_HAND_LEFT));
            assertTrue(Arrays.asList(ids).contains(SwipeAction.ONE_HAND_RIGHT));
        }
        assertEquals("한 손 모드 (왼쪽)", SwipeAction.label(SwipeAction.ONE_HAND_LEFT));
        assertEquals("한 손 모드 (오른쪽)", SwipeAction.label(SwipeAction.ONE_HAND_RIGHT));
    }

    @Test
    public void overridesReplaceDefaults() {
        Map<String, String> m = new HashMap<>();
        m.put("bar_up", SwipeAction.HIDE_KEYBOARD);
        m.put("bar_left", SwipeAction.NONE);
        m.put("bar_down", SwipeAction.NONE);
        m.put("hide_down", SwipeAction.IME_PICKER);
        m.put("clipboard_down", SwipeAction.CLIPBOARD_PINNED);
        ToolbarSwipes t = with(m);
        assertEquals(SwipeAction.HIDE_KEYBOARD, t.action(ToolbarSwipes.BAR, Key.SWIPE_UP));
        assertEquals(SwipeAction.NONE, t.action(ToolbarSwipes.BAR, Key.SWIPE_LEFT));
        assertEquals(SwipeAction.IME_PICKER, t.action(ToolbarSwipes.HIDE, Key.SWIPE_DOWN));
        assertEquals(SwipeAction.CLIPBOARD_PINNED, t.action(ToolbarSwipes.CLIPBOARD, Key.SWIPE_DOWN));
    }

    @Test
    public void buttonsUseUpAndDownOnly() {
        Map<String, String> m = new HashMap<>();
        m.put("emoji_up", SwipeAction.COPY);
        m.put("undo_left", SwipeAction.PASTE);
        ToolbarSwipes t = with(m);
        assertEquals(SwipeAction.COPY, t.action(ToolbarSwipes.EMOJI, Key.SWIPE_UP));
        assertEquals(SwipeAction.EMOJI_LATEST, t.action(ToolbarSwipes.EMOJI, Key.SWIPE_DOWN));
        assertEquals(SwipeAction.COPY, t.action(ToolbarSwipes.CLIPBOARD, Key.SWIPE_UP));   // 클립보드 위쪽 기본값은 복사
        assertEquals(SwipeAction.NONE, t.action(ToolbarSwipes.UNDO, Key.SWIPE_UP));        // 다른 버튼의 위쪽 기본값은 없음
        assertEquals(SwipeAction.NONE, t.action(ToolbarSwipes.UNDO, Key.SWIPE_LEFT));
        assertFalse(ToolbarSwipes.usesDir(ToolbarSwipes.VOICE, Key.SWIPE_RIGHT));
        assertTrue(ToolbarSwipes.usesDir(ToolbarSwipes.VOICE, Key.SWIPE_UP));
        assertTrue(ToolbarSwipes.usesDir(ToolbarSwipes.BAR, Key.SWIPE_UP));
    }

    @Test
    public void unknownOrCursorMoveFallsBackToDefault() {
        Map<String, String> m = new HashMap<>();
        m.put("bar_right", "no_such_action");
        m.put("bar_up", SwipeAction.CURSOR_MOVE);   // 스페이스바 전용
        ToolbarSwipes t = with(m);
        assertEquals(SwipeAction.LINE_END, t.action(ToolbarSwipes.BAR, Key.SWIPE_RIGHT));
        assertEquals(SwipeAction.CURSOR_UP, t.action(ToolbarSwipes.BAR, Key.SWIPE_UP));
        assertFalse(Arrays.asList(SwipeAction.idsForToolbar()).contains(SwipeAction.CURSOR_MOVE));
    }

    @Test
    public void standardFollowsTheThreeToggles() {
        ToolbarSwipes all = ToolbarSwipes.standard(true, true, true, true);
        assertEquals(SwipeAction.LINE_START, all.action(ToolbarSwipes.BAR, Key.SWIPE_LEFT));
        assertEquals(SwipeAction.PASTE, all.action(ToolbarSwipes.CLIPBOARD, Key.SWIPE_DOWN));
        assertEquals(SwipeAction.COPY, all.action(ToolbarSwipes.CLIPBOARD, Key.SWIPE_UP));
        assertEquals(SwipeAction.EMOJI_LATEST, all.action(ToolbarSwipes.EMOJI, Key.SWIPE_DOWN));
        assertEquals(SwipeAction.REDO, all.action(ToolbarSwipes.UNDO, Key.SWIPE_DOWN));
        ToolbarSwipes none = ToolbarSwipes.standard(false, false, false, false);
        for (int slot = 0; slot < ToolbarSwipes.SPEC.slotCount(); slot++) {
            for (int dir = 0; dir < 4; dir++) assertEquals(SwipeAction.NONE, none.action(slot, dir));
        }
        ToolbarSwipes clipOnly = ToolbarSwipes.standard(false, true, false, false);
        assertEquals(SwipeAction.COPY, clipOnly.action(ToolbarSwipes.CLIPBOARD, Key.SWIPE_UP));
        assertEquals(SwipeAction.NONE, clipOnly.action(ToolbarSwipes.EMOJI, Key.SWIPE_DOWN));
        ToolbarSwipes undoOnly = ToolbarSwipes.standard(false, false, false, true);
        assertEquals(SwipeAction.REDO, undoOnly.action(ToolbarSwipes.UNDO, Key.SWIPE_DOWN));
        assertEquals(SwipeAction.NONE, undoOnly.action(ToolbarSwipes.EMOJI, Key.SWIPE_DOWN));
    }
}
