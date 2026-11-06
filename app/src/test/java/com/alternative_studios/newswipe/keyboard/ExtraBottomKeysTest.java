package com.alternative_studios.newswipe.keyboard;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.SharedPreferences;

import com.alternative_studios.newswipe.Prefs;

import org.junit.Test;

import java.lang.reflect.Constructor;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/** 기능키 순서·유무 사용자화에서 더할 수 있는 키 (이모지·클립보드·커서 이동·음성 입력): 처음에는 꺼져 있다. */
public class ExtraBottomKeysTest {
    private static final class FakeSp implements SharedPreferences {
        final Map<String, Object> m = new HashMap<>();
        @Override public Map<String, ?> getAll() { return m; }
        @Override public String getString(String k, String d) { Object o = m.get(k); return o == null ? d : (String) o; }
        @Override public Set<String> getStringSet(String k, Set<String> d) { return d; }
        @Override public int getInt(String k, int d) { Object o = m.get(k); return o == null ? d : (Integer) o; }
        @Override public long getLong(String k, long d) { return d; }
        @Override public float getFloat(String k, float d) { return d; }
        @Override public boolean getBoolean(String k, boolean d) { Object o = m.get(k); return o == null ? d : (Boolean) o; }
        @Override public boolean contains(String k) { return m.containsKey(k); }
        @Override public Editor edit() { throw new UnsupportedOperationException(); }
        @Override public void registerOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener l) { }
        @Override public void unregisterOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener l) { }
    }

    private static Prefs view(FakeSp sp, String profile) throws Exception {
        Constructor<Prefs> c = Prefs.class.getDeclaredConstructor(SharedPreferences.class, boolean.class, String.class,
                boolean.class);
        c.setAccessible(true);
        return c.newInstance(sp, false, profile, false);
    }

    private static final String[] IDS = {BottomKeys.EMOJI, BottomKeys.CLIPBOARD, BottomKeys.CURSOR, BottomKeys.VOICE};
    private static final String[] PREF = {Prefs.BOTTOM_EMOJI_KEY, Prefs.BOTTOM_CLIPBOARD_KEY, Prefs.BOTTOM_CURSOR_KEY,
            Prefs.BOTTOM_VOICE_KEY};
    private static final int[] TYPES = {Key.EMOJI_KEY, Key.CLIPBOARD, Key.CURSOR_PAD, Key.VOICE};

    private static int count(KeyboardLayout l, int type) {
        int n = 0;
        for (KeyboardLayout.Row row : l.rows) for (Key k : row.keys) if (k.type == type) n++;
        return n;
    }

    private static KeyboardLayout[] all(Prefs p) {
        return new KeyboardLayout[]{KeyboardLayout.korean(p), KeyboardLayout.english(p), KeyboardLayout.symbols(true, p),
                KeyboardLayout.symbols2(true, p)};
    }

    @Test
    public void offByDefault() throws Exception {
        Prefs p = view(new FakeSp(), Prefs.PROFILE_PORTRAIT);
        for (String id : IDS) {
            assertFalse(id, p.bottomKeyShown(id));
            assertFalse(id, BottomKeys.shownByDefault(id));
        }
        for (KeyboardLayout l : all(p)) for (int t : TYPES) assertEquals(0, count(l, t));
        for (int t : TYPES) assertEquals(0, count(KeyboardLayout.symbols(true, null), t));   // 설정 없이 만든 자판도
        // 원래 키는 그대로 보인다
        assertTrue(p.bottomKeyShown(BottomKeys.SPACE));
        assertTrue(p.bottomKeyShown(BottomKeys.GLOBE));
    }

    @Test
    public void eachKeyAppearsInTheBottomRowWhenTurnedOn() throws Exception {
        for (String profile : new String[]{Prefs.PROFILE_PORTRAIT, Prefs.PROFILE_LANDSCAPE}) {
            for (int i = 0; i < IDS.length; i++) {
                FakeSp sp = new FakeSp();
                sp.m.put(PREF[i], true);
                sp.m.put(Prefs.LANDSCAPE_SPLIT, true);
                Prefs p = view(sp, profile);
                assertTrue(p.bottomKeyShown(IDS[i]));
                for (KeyboardLayout l : all(p)) {
                    assertEquals(profile + " " + IDS[i], 1, count(l, TYPES[i]));
                    KeyboardLayout.Row bottom = l.rows[l.rows.length - 1];
                    int inBottom = 0;
                    float width = 0f;
                    for (Key k : bottom.keys) {
                        if (k.type == TYPES[i]) inBottom++;
                        width += k.weight;
                    }
                    assertEquals(1, inBottom);
                    assertEquals(10f, width, 1e-3f);   // 줄 폭은 그대로 (스페이스바가 줄어든다)
                }
            }
        }
    }

    @Test
    public void followsTheStoredOrder() throws Exception {
        FakeSp sp = new FakeSp();
        for (String k : PREF) sp.m.put(k, true);
        sp.m.put(Prefs.BOTTOM_KEY_ORDER, "voice,mode,space,cursor,clipboard,emoji,globe,period,enter,comma");
        KeyboardLayout.Row bottom = KeyboardLayout.english(view(sp, Prefs.PROFILE_PORTRAIT)).rows[3];
        int[] expect = {Key.VOICE, Key.TO_SYMBOLS, Key.SPACE, Key.CURSOR_PAD, Key.CLIPBOARD, Key.EMOJI_KEY, Key.LANGUAGE};
        for (int i = 0; i < expect.length; i++) assertEquals(i + "", expect[i], bottom.keys[i].type);
    }

    @Test
    public void extraKeysHaveTheirOwnFunctionSwipeSlots() {
        int[] slots = {FunctionSwipes.EMOJI_KEY, FunctionSwipes.CLIPBOARD, FunctionSwipes.CURSOR, FunctionSwipes.VOICE};
        for (int i = 0; i < TYPES.length; i++) {
            assertEquals(slots[i], FunctionSwipes.slotOf(TYPES[i]));
            assertEquals(-1, LongPressActions.slotOf(TYPES[i]));   // 길게 누르기 사용자화의 대상은 아니다
        }
    }

    @Test
    public void functionSwipeDefaultsForExtraKeys() {
        FunctionSwipes f = FunctionSwipes.from(null);
        for (int dir = 0; dir < 4; dir++) {
            // 커서 이동 키는 네 방향 모두 커서 이동, 나머지 새 키는 없음
            assertEquals(SwipeAction.CURSOR_MOVE, f.action(FunctionSwipes.CURSOR, dir));
            assertEquals(SwipeAction.NONE, f.action(FunctionSwipes.EMOJI_KEY, dir));
            assertEquals(SwipeAction.NONE, f.action(FunctionSwipes.CLIPBOARD, dir));
            assertEquals(SwipeAction.NONE, f.action(FunctionSwipes.VOICE, dir));
        }
        assertTrue(f.cursorH(FunctionSwipes.CURSOR));
        assertTrue(f.cursorV(FunctionSwipes.CURSOR));
        // 기존 키의 기본값은 그대로
        assertEquals(SwipeAction.EMOJI_OPEN, f.action(FunctionSwipes.MODE, Key.SWIPE_UP));
        assertEquals(SwipeAction.CURSOR_MOVE, f.action(FunctionSwipes.SPACE, Key.SWIPE_LEFT));
    }

    @Test
    public void cursorMoveIsOnlyForSpaceAndCursorKey() {
        Map<String, String> m = new HashMap<>();
        m.put("cursor/left", SwipeAction.NONE);
        m.put("cursor/up", SwipeAction.CLIPBOARD_OPEN);
        m.put("voice/up", SwipeAction.CURSOR_MOVE);   // 커서 이동 키가 아닌 키에서는 없음
        FunctionSwipes f = FunctionSwipes.from((slot, dir) -> m.get(slot + "/" + dir));
        assertEquals(SwipeAction.NONE, f.action(FunctionSwipes.CURSOR, Key.SWIPE_LEFT));
        assertEquals(SwipeAction.CLIPBOARD_OPEN, f.action(FunctionSwipes.CURSOR, Key.SWIPE_UP));
        assertEquals(SwipeAction.NONE, f.action(FunctionSwipes.VOICE, Key.SWIPE_UP));
        assertTrue(f.cursorH(FunctionSwipes.CURSOR));    // 오른쪽은 아직 커서 이동
        assertTrue(f.cursorV(FunctionSwipes.CURSOR));    // 아래도
        assertTrue(java.util.Arrays.asList(SwipeAction.idsFor(FunctionSwipes.CURSOR)).contains(SwipeAction.CURSOR_MOVE));
        assertFalse(java.util.Arrays.asList(SwipeAction.idsFor(FunctionSwipes.VOICE)).contains(SwipeAction.CURSOR_MOVE));
    }

    @Test
    public void editorShowsOnlyKeysThatAreOn() throws Exception {
        FakeSp sp = new FakeSp();
        Prefs p = view(sp, Prefs.PROFILE_PORTRAIT);
        SwipeSpec spec = FunctionSwipes.SPEC;
        // 처음: 원래 다섯 키만
        for (int slot = 0; slot < spec.slotCount(); slot++) assertEquals(slot + "", slot <= FunctionSwipes.DELETE, spec.shown(p, slot));
        sp.m.put(Prefs.BOTTOM_CURSOR_KEY, true);
        sp.m.put(Prefs.BOTTOM_VOICE_KEY, true);
        sp.m.put(Prefs.LANGUAGE_KEY_HIDDEN, true);
        sp.m.put(Prefs.DELETE_KEY_HIDDEN, true);
        assertTrue(spec.shown(p, FunctionSwipes.CURSOR));
        assertTrue(spec.shown(p, FunctionSwipes.VOICE));
        assertFalse(spec.shown(p, FunctionSwipes.EMOJI_KEY));
        assertFalse(spec.shown(p, FunctionSwipes.CLIPBOARD));
        assertFalse(spec.shown(p, FunctionSwipes.GLOBE));    // 끈 언어 전환 키도 감춘다
        // 기호 키·스페이스바·엔터·⌫는 꺼도 숫자 패드에 늘 있어 보인다
        sp.m.put(Prefs.MODE_KEY_HIDDEN, true);
        sp.m.put(Prefs.SPACE_KEY_HIDDEN, true);
        sp.m.put(Prefs.ENTER_KEY_HIDDEN, true);
        for (int slot : new int[]{FunctionSwipes.MODE, FunctionSwipes.SPACE, FunctionSwipes.ENTER, FunctionSwipes.DELETE}) {
            assertTrue(slot + "", spec.shown(p, slot));
        }
        KeyboardLayout pad = KeyboardLayout.numberPad(KeyboardLayout.NUMBER_PAD_SYMBOLS_LABEL, "가");
        for (int type : new int[]{Key.TO_LETTERS, Key.TO_SYMBOLS, Key.SPACE, Key.ENTER, Key.DELETE}) {
            assertEquals(1, count(pad, type));
        }
    }

    @Test
    public void atMostEightBottomKeys() throws Exception {
        assertEquals(8, BottomKeys.MAX_SHOWN);
        // 모든 키를 켠 설정(가져온 설정 등)이어도 순서상 앞의 8개만 놓는다. Shift·⌫는 세지 않는다.
        FakeSp sp = new FakeSp();
        for (String k : PREF) sp.m.put(k, true);
        sp.m.put(Prefs.PERIOD_COMMA, false);   // 쉼표 키도 보이기 → 모두 10개
        Prefs p = view(sp, Prefs.PROFILE_PORTRAIT);
        for (KeyboardLayout l : all(p)) {
            KeyboardLayout.Row bottom = l.rows[l.rows.length - 1];
            int keys = 0;
            for (Key k : bottom.keys) if (k.type != Key.GAP && k.type != Key.SPACER) keys++;
            assertEquals(8, keys);
            assertEquals(0, count(l, Key.CURSOR_PAD));   // 기본 순서의 9·10번째 (커서 이동·음성 입력)
            assertEquals(0, count(l, Key.VOICE));
            assertEquals(1, count(l, Key.CLIPBOARD));
        }
        // 기본 설정은 다섯 키 (쉼표 꺼짐)
        KeyboardLayout.Row bottom = KeyboardLayout.english(view(new FakeSp(), Prefs.PROFILE_PORTRAIT)).rows[3];
        assertEquals(5, bottom.keys.length);
    }

    @Test
    public void longPressEditorHidesOnlyTheGlobeKeyWhenOff() throws Exception {
        FakeSp sp = new FakeSp();
        Prefs p = view(sp, Prefs.PROFILE_PORTRAIT);
        SwipeSpec spec = LongPressActions.SPEC;
        for (int slot = 0; slot < spec.slotCount(); slot++) assertTrue(spec.shown(p, slot));
        sp.m.put(Prefs.LANGUAGE_KEY_HIDDEN, true);
        sp.m.put(Prefs.MODE_KEY_HIDDEN, true);
        sp.m.put(Prefs.ENTER_KEY_HIDDEN, true);
        sp.m.put(Prefs.DELETE_KEY_HIDDEN, true);
        sp.m.put(Prefs.KOREAN_SHIFT_HIDDEN, true);
        assertFalse(spec.shown(p, LongPressActions.GLOBE));
        // Shift는 영어 자판에, 기호 키·엔터·⌫는 숫자 패드에 늘 있다
        for (int slot : new int[]{LongPressActions.SHIFT, LongPressActions.MODE, LongPressActions.ENTER,
                LongPressActions.DELETE}) {
            assertTrue(slot + "", spec.shown(p, slot));
        }
        assertEquals(1, count(KeyboardLayout.english(p), Key.SHIFT));
    }

    @Test
    public void toolbarEditorShowsOnlyButtonsThatAreOn() throws Exception {
        FakeSp sp = new FakeSp();
        Prefs p = view(sp, Prefs.PROFILE_PORTRAIT);
        SwipeSpec spec = ToolbarSwipes.SPEC;
        for (int slot = 0; slot < spec.slotCount(); slot++) {
            assertTrue(slot + "", spec.shown(p, slot));
            // 칸 이름이 도구 막대 버튼 ID와 같다 (막대 자체 제외)
            if (slot != ToolbarSwipes.BAR) {
                assertTrue(java.util.Arrays.asList(ToolbarButtons.ALL).contains(spec.slotName(slot)));
            }
        }
        sp.m.put(Prefs.TOOL_CLIPBOARD, false);
        sp.m.put(Prefs.VOICE_KEY, false);
        assertFalse(spec.shown(p, ToolbarSwipes.CLIPBOARD));
        assertFalse(spec.shown(p, ToolbarSwipes.VOICE));
        assertTrue(spec.shown(p, ToolbarSwipes.EMOJI));
        assertTrue(spec.shown(p, ToolbarSwipes.BAR));
        // 도구 막대를 끄면 버튼은 모두 감추고 막대 자체만 남긴다 (외장 키보드의 '도구 막대 보이기'를 켰으면 버튼도 보인다)
        sp.m.put(Prefs.TOOLBAR, false);
        for (int slot = 1; slot < spec.slotCount(); slot++) assertFalse(slot + "", spec.shown(p, slot));
        assertTrue(spec.shown(p, ToolbarSwipes.BAR));
        sp.m.put(Prefs.HW_TOOLBAR, true);
        assertTrue(spec.shown(p, ToolbarSwipes.EMOJI));
        assertFalse(spec.shown(p, ToolbarSwipes.CLIPBOARD));
    }
}
