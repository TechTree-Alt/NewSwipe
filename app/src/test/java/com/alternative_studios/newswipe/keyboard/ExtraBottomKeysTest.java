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
        // 기호 자판의 언어 전환 키 자리만 이모지 키다 ('기호 자판에서는'의 기본값)
        KeyboardLayout[] boards = all(p);   // 한글·영어·기호 1쪽·기호 2쪽
        for (int b = 0; b < boards.length; b++) for (int t : TYPES) {
            assertEquals(t == Key.EMOJI_KEY && b >= 2 ? 1 : 0, count(boards[b], t));
        }
        for (int t : TYPES) assertEquals(t == Key.EMOJI_KEY ? 1 : 0, count(KeyboardLayout.symbols(true, null), t));   // 설정 없이 만든 자판도
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
                sp.m.put(Prefs.SYMBOL_GLOBE_KEY, BottomKeys.GLOBE);   // 기호 자판에서도 언어 전환 키 그대로
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
    public void joystickTakesNoRoomAndSitsBetweenItsNeighbors() throws Exception {
        for (String mode : new String[]{BottomKeys.CURSOR_MODE_JOYSTICK, BottomKeys.CURSOR_MODE_SMALL})
        for (boolean split : new boolean[]{false, true}) {
            for (boolean grid : new boolean[]{false, true}) {
                FakeSp sp = new FakeSp();
                sp.m.put(Prefs.BOTTOM_CURSOR_KEY, true);
                sp.m.put(Prefs.CURSOR_KEY_MODE, mode);
                sp.m.put(Prefs.LANDSCAPE_SPLIT, split);
                sp.m.put(Prefs.GRID_LAYOUT, grid);
                // 커서 이동 키를 기호 키와 지구본 키 사이에 둔다.
                sp.m.put(Prefs.BOTTOM_KEY_ORDER, "mode,cursor,globe,space,period,enter");
                Prefs p = view(sp, split ? Prefs.PROFILE_LANDSCAPE : Prefs.PROFILE_PORTRAIT);
                for (KeyboardLayout l : all(p)) {
                    KeyboardLayout.Row bottom = l.rows[l.rows.length - 1];
                    float width = 0f;
                    int at = -1;
                    for (int i = 0; i < bottom.keys.length; i++) {
                        Key k = bottom.keys[i];
                        width += k.weight;
                        if (k.type == Key.CURSOR_PAD) {
                            at = i;
                            assertTrue(k.isJoystick());
                            assertEquals(0f, k.weight, 0f);
                            assertEquals(BottomKeys.CURSOR_MODE_SMALL.equals(mode) ? 0.2f : 0.25f, k.joystickRadius(), 0f);
                        }
                    }
                    assertTrue(at > 0);
                    int before = bottom.keys[at - 1].type, after = bottom.keys[at + 1].type;
                    assertTrue(before == Key.TO_SYMBOLS || before == Key.TO_LETTERS);
                    assertTrue(after == Key.LANGUAGE || after == Key.EMOJI_KEY);
                    assertEquals(10f, width, 1e-3f);
                }
            }
        }
        // 끄면 보통 키 (한 칸)
        FakeSp sp = new FakeSp();
        sp.m.put(Prefs.BOTTOM_CURSOR_KEY, true);
        for (Key k : KeyboardLayout.english(view(sp, Prefs.PROFILE_PORTRAIT)).rows[3].keys) {
            if (k.type == Key.CURSOR_PAD) {
                assertFalse(k.isJoystick());
                assertTrue(k.weight > 0f);
            }
        }
    }

    @Test
    public void extraKeysHaveTheirOwnFunctionSwipeSlots() {
        int[] slots = {FunctionSwipes.EMOJI_KEY, FunctionSwipes.CLIPBOARD, FunctionSwipes.CURSOR, FunctionSwipes.VOICE};
        int[] lp = {LongPressActions.EMOJI_KEY, LongPressActions.CLIPBOARD, LongPressActions.CURSOR, LongPressActions.VOICE};
        for (int i = 0; i < TYPES.length; i++) {
            assertEquals(slots[i], FunctionSwipes.slotOf(TYPES[i]));
            assertEquals(lp[i], LongPressActions.slotOf(TYPES[i]));
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
    public void cursorMoveIsOfferedOnEveryKey() {
        Map<String, String> m = new HashMap<>();
        m.put("cursor/left", SwipeAction.NONE);
        m.put("cursor/up", SwipeAction.CLIPBOARD_OPEN);
        m.put("voice/up", SwipeAction.CURSOR_MOVE);   // 다른 키에서도 쓸 수 있다
        FunctionSwipes f = FunctionSwipes.from((slot, dir) -> m.get(slot + "/" + dir));
        assertEquals(SwipeAction.NONE, f.action(FunctionSwipes.CURSOR, Key.SWIPE_LEFT));
        assertEquals(SwipeAction.CLIPBOARD_OPEN, f.action(FunctionSwipes.CURSOR, Key.SWIPE_UP));
        assertEquals(SwipeAction.CURSOR_MOVE, f.action(FunctionSwipes.VOICE, Key.SWIPE_UP));
        assertTrue(f.cursorH(FunctionSwipes.CURSOR));    // 오른쪽은 아직 커서 이동
        assertTrue(f.cursorV(FunctionSwipes.CURSOR));    // 아래도
        assertTrue(java.util.Arrays.asList(SwipeAction.idsFor(FunctionSwipes.CURSOR)).contains(SwipeAction.CURSOR_MOVE));
        assertTrue(java.util.Arrays.asList(SwipeAction.idsFor(FunctionSwipes.VOICE)).contains(SwipeAction.CURSOR_MOVE));
    }

    @Test
    public void numberPadAlwaysHasTheFunctionKeys() throws Exception {
        // 기호 키·스페이스바·엔터·⌫는 맨 아래 줄에서 꺼도 숫자 패드에 늘 있다 (그래서 그 키의 설정은 늘 쓰인다).
        KeyboardLayout pad = KeyboardLayout.numberPad(KeyboardLayout.NUMBER_PAD_SYMBOLS_LABEL, "가");
        for (int type : new int[]{Key.TO_LETTERS, Key.TO_SYMBOLS, Key.SPACE, Key.ENTER, Key.DELETE}) {
            assertEquals(1, count(pad, type));
        }
    }

    @Test
    public void atMostEightBottomKeys() throws Exception {
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
    public void englishKeepsShiftWhenKoreanShiftIsOff() throws Exception {
        FakeSp sp = new FakeSp();
        sp.m.put(Prefs.KOREAN_SHIFT_HIDDEN, true);
        assertEquals(1, count(KeyboardLayout.english(view(sp, Prefs.PROFILE_PORTRAIT)), Key.SHIFT));
    }

    @Test
    public void toolbarSlotNamesAreButtonIds() {
        SwipeSpec spec = ToolbarSwipes.SPEC;
        // 칸 이름이 도구 막대 버튼 ID와 같다 (막대 자체 제외)
        for (int slot = 0; slot < spec.slotCount(); slot++) {
            if (slot != ToolbarSwipes.BAR) {
                assertTrue(java.util.Arrays.asList(ToolbarButtons.ALL).contains(spec.slotName(slot)));
            }
        }
    }

    @Test
    public void longPressOnExtraKeys() {
        // 정하지 않으면 없음 (길게 눌러도 아무것도 하지 않고, 떼면 그냥 누른 것)
        String[] none = LongPressActions.actions(null);
        assertEquals(LongPressActions.SPEC.slotCount(), none.length);
        for (int slot = LongPressActions.EMOJI_KEY; slot <= LongPressActions.VOICE; slot++) {
            assertEquals(SwipeAction.NONE, none[slot]);
        }
        // 정한 기능은 그 키의 칸에 들어간다. 밀어야 하는 커서 이동은 길게 누르기에서 고를 수 없다.
        Map<String, String> m = new HashMap<>();
        m.put("clipboard/" + SwipeCustom.dirName(LongPressActions.DIR), SwipeAction.PASTE);
        m.put("cursor/" + SwipeCustom.dirName(LongPressActions.DIR), SwipeAction.CURSOR_MOVE);
        String[][] t = LongPressActions.SPEC.table((slot, dir) -> m.get(slot + "/" + dir));
        assertEquals(SwipeAction.PASTE, t[LongPressActions.CLIPBOARD][LongPressActions.DIR]);
        assertEquals(SwipeAction.NONE, t[LongPressActions.CURSOR][LongPressActions.DIR]);
        assertEquals(SwipeAction.IME_PICKER, t[LongPressActions.GLOBE][LongPressActions.DIR]);   // 기존 기본값 그대로
    }

    @Test
    public void symbolKeyboardGlobeSlotFollowsChoice() throws Exception {
        FakeSp sp = new FakeSp();
        Prefs p = view(sp, Prefs.PROFILE_PORTRAIT);
        // 기본: 기호 자판에서는 이모지 키, 글자 자판은 언어 전환 키
        assertEquals(BottomKeys.EMOJI, p.symbolGlobeKey());
        // (바뀐 키는 맨 아래 줄에 더하는 이모지 키와 같은 키라 그 동작·길게 누르기·밀기 설정을 따른다)
        assertEquals(1, count(KeyboardLayout.symbols(true, p), Key.EMOJI_KEY));
        assertEquals(0, count(KeyboardLayout.symbols(true, p), Key.EMOJI));
        assertEquals(0, count(KeyboardLayout.symbols(true, p), Key.LANGUAGE));
        assertEquals(1, count(KeyboardLayout.korean(p), Key.LANGUAGE));
        assertEquals(0, count(KeyboardLayout.korean(p), Key.EMOJI_KEY));
        // 다른 키로: 기호 자판(1·2쪽)에서만 그 키로 보인다
        sp.m.put(Prefs.SYMBOL_GLOBE_KEY, BottomKeys.CLIPBOARD);
        for (KeyboardLayout l : new KeyboardLayout[]{KeyboardLayout.symbols(true, p), KeyboardLayout.symbols2(true, p)}) {
            assertEquals(1, count(l, Key.CLIPBOARD));
            assertEquals(0, count(l, Key.EMOJI_KEY));
            assertEquals(0, count(l, Key.LANGUAGE));
        }
        assertEquals(1, count(KeyboardLayout.korean(p), Key.LANGUAGE));
        assertEquals(0, count(KeyboardLayout.korean(p), Key.CLIPBOARD));
        // 기호 자판에서도 그대로
        sp.m.put(Prefs.SYMBOL_GLOBE_KEY, BottomKeys.GLOBE);
        assertEquals(1, count(KeyboardLayout.symbols(true, p), Key.LANGUAGE));
        assertEquals(0, count(KeyboardLayout.symbols(true, p), Key.EMOJI_KEY));
        // 언어 전환 키를 끄면 기호 자판에서도 없어진다
        sp.m.put(Prefs.SYMBOL_GLOBE_KEY, BottomKeys.VOICE);
        sp.m.put(Prefs.LANGUAGE_KEY_HIDDEN, true);
        assertEquals(0, count(KeyboardLayout.symbols(true, p), Key.VOICE));
        // 모르는 값은 기본값
        sp.m.put(Prefs.SYMBOL_GLOBE_KEY, "nope");
        assertEquals(BottomKeys.EMOJI, p.symbolGlobeKey());
    }
}
