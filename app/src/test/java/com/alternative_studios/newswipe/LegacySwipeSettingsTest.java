package com.alternative_studios.newswipe;

import android.content.SharedPreferences;

import com.alternative_studios.newswipe.keyboard.FunctionSwipes;
import com.alternative_studios.newswipe.keyboard.Key;
import com.alternative_studios.newswipe.keyboard.KeyboardLayout;
import com.alternative_studios.newswipe.keyboard.KeyboardSwipes;
import com.alternative_studios.newswipe.keyboard.LongPressActions;
import com.alternative_studios.newswipe.keyboard.SwipeAction;
import com.alternative_studios.newswipe.keyboard.SwipeCustom;
import com.alternative_studios.newswipe.keyboard.ToolbarSwipes;

import org.junit.Test;

import java.lang.reflect.Constructor;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** 예전 밀기 규칙 스위치를 키마다 정한 값으로 옮긴 뒤에도 키보드가 옮기기 전과 똑같이 동작하는지. */
public class LegacySwipeSettingsTest {

    /** 쓰기도 되는 메모리 설정. */
    static final class MapSp implements SharedPreferences {
        final Map<String, Object> m = new HashMap<>();

        @Override public Map<String, ?> getAll() { return new HashMap<>(m); }
        @Override public String getString(String k, String d) { Object o = m.get(k); return o == null ? d : (String) o; }
        @Override public Set<String> getStringSet(String k, Set<String> d) { return d; }
        @Override public int getInt(String k, int d) { Object o = m.get(k); return o == null ? d : (Integer) o; }
        @Override public long getLong(String k, long d) { return d; }
        @Override public float getFloat(String k, float d) { return d; }
        @Override public boolean getBoolean(String k, boolean d) { Object o = m.get(k); return o == null ? d : (Boolean) o; }
        @Override public boolean contains(String k) { return m.containsKey(k); }
        @Override public void registerOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener l) { }
        @Override public void unregisterOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener l) { }

        @Override public Editor edit() {
            return new Editor() {
                final Map<String, Object> put = new HashMap<>();
                final java.util.List<String> drop = new java.util.ArrayList<>();
                @Override public Editor putString(String k, String v) { put.put(k, v); return this; }
                @Override public Editor putStringSet(String k, Set<String> v) { return this; }
                @Override public Editor putInt(String k, int v) { put.put(k, v); return this; }
                @Override public Editor putLong(String k, long v) { return this; }
                @Override public Editor putFloat(String k, float v) { return this; }
                @Override public Editor putBoolean(String k, boolean v) { put.put(k, v); return this; }
                @Override public Editor remove(String k) { drop.add(k); return this; }
                @Override public Editor clear() { return this; }
                @Override public boolean commit() { apply(); return true; }
                @Override public void apply() {
                    for (String k : drop) m.remove(k);
                    m.putAll(put);
                }
            };
        }
    }

    private static Prefs prefs(MapSp sp) throws Exception {
        Constructor<Prefs> c = Prefs.class.getDeclaredConstructor(SharedPreferences.class, boolean.class, String.class,
                boolean.class);
        c.setAccessible(true);
        return c.newInstance(sp, false, Prefs.PROFILE_PORTRAIT, false);
    }

    private static Key key(KeyboardLayout l, String label) {
        for (KeyboardLayout.Row r : l.rows) for (Key k : r.keys) if (k.type == Key.CHAR && label.equals(k.slot())) return k;
        throw new AssertionError(label);
    }

    /** 옮기기 전 예전 규칙이 만들던 글자와, 옮긴 뒤 자판이 입력하는 글자가 모든 한글 키·방향에서 같은지. */
    private static void assertKoreanSame(MapSp sp) throws Exception {
        Prefs p = prefs(sp);
        Map<String, String> before = new HashMap<>();
        KeyboardLayout layout = KeyboardLayout.korean(p);
        for (KeyboardLayout.Row r : layout.rows) {
            for (Key k : r.keys) {
                if (k.type != Key.CHAR || k.slot().length() != 1 || k.slot().charAt(0) < 'ㄱ' || k.slot().charAt(0) > 'ㅣ') continue;
                for (int dir : SwipeCustom.DIRS) {
                    before.put(k.slot() + dir, LegacySwipeSettings.legacyKorean(p, sp, k.slot().charAt(0), dir));
                }
            }
        }
        LegacySwipeSettings.migrateIfNeeded(p);
        KeyboardLayout after = KeyboardLayout.korean(p);
        assertFalse(before.isEmpty());
        for (Map.Entry<String, String> e : before.entrySet()) {
            String label = e.getKey().substring(0, 1);
            int dir = Integer.parseInt(e.getKey().substring(1));
            assertEquals(e.getKey(), e.getValue(), key(after, label).swipeText(dir));
        }
    }

    @Test
    public void oldDefaultsStayTheSame() throws Exception {
        MapSp sp = new MapSp();
        sp.m.put(Prefs.ONBOARDING_DONE, true);   // 설정을 쓰던 사람 (아무 스위치도 바꾸지 않았다)
        assertKoreanSame(sp);
        Prefs p = prefs(sp);
        KeyboardLayout l = KeyboardLayout.korean(p);
        assertEquals("ㄲ", key(l, "ㄱ").swipeText(Key.SWIPE_DOWN));
        assertEquals("ㄳ", key(l, "ㄱ").swipeText(Key.SWIPE_RIGHT));
        assertEquals(",", key(l, ".").swipeText(Key.SWIPE_UP));
        // 예전에는 두 손가락 실행 취소가 꺼져 있었다.
        assertEquals(SwipeAction.NONE, KeyboardSwipes.SPEC.get(p, KeyboardSwipes.TWO, Key.SWIPE_LEFT));
        assertEquals(SwipeAction.CURSOR_MOVE, FunctionSwipes.SPEC.get(p, FunctionSwipes.SPACE, Key.SWIPE_LEFT));
        assertEquals(SwipeAction.NONE, FunctionSwipes.SPEC.get(p, FunctionSwipes.GLOBE, Key.SWIPE_UP));
        assertEquals(SwipeAction.DELETE_REPEAT, LongPressActions.SPEC.get(p, LongPressActions.DELETE, LongPressActions.DIR));
        assertEquals(SwipeAction.COPY, ToolbarSwipes.SPEC.get(p, ToolbarSwipes.CLIPBOARD, Key.SWIPE_UP));
        assertTrue(sp.getBoolean(LegacySwipeSettings.DONE, false));
    }

    @Test
    public void changedSwitchesAreKept() throws Exception {
        MapSp sp = new MapSp();
        sp.m.put("swipe_double", false);
        sp.m.put("swipe_compound_vowel", false);
        sp.m.put("swipe_final_ㄹ_up", "ㄼ");
        sp.m.put("period_swipe_comma", false);
        sp.m.put("space_lang_swipe", true);
        sp.m.put("space_cursor_v", false);
        sp.m.put("globe_swipe_lang", true);
        sp.m.put("delete_word_swipe", false);
        sp.m.put("long_press_delete", false);
        sp.m.put("mode_key_settings", false);
        sp.m.put("toolbar_swipe", false);
        sp.m.put("char_cursor", true);
        sp.m.put("char_cursor_v", false);
        sp.m.put("two_finger_undo", true);
        assertKoreanSame(sp);
        Prefs p = prefs(sp);
        KeyboardLayout l = KeyboardLayout.korean(p);
        assertEquals(null, key(l, "ㄱ").swipeText(Key.SWIPE_DOWN));
        assertEquals("ㄼ", key(l, "ㄹ").swipeText(Key.SWIPE_UP));
        assertEquals(null, key(l, "ㅗ").swipeText(Key.SWIPE_LEFT));
        assertEquals("ㅛ", key(l, "ㅗ").swipeText(Key.SWIPE_DOWN));
        assertEquals(null, key(l, ".").swipeText(Key.SWIPE_UP));
        assertEquals(SwipeAction.LANGUAGE, FunctionSwipes.SPEC.get(p, FunctionSwipes.SPACE, Key.SWIPE_RIGHT));
        assertEquals(SwipeAction.NONE, FunctionSwipes.SPEC.get(p, FunctionSwipes.SPACE, Key.SWIPE_UP));
        assertEquals(SwipeAction.LANGUAGE_PICK, FunctionSwipes.SPEC.get(p, FunctionSwipes.GLOBE, Key.SWIPE_UP));
        assertEquals(SwipeAction.NONE, FunctionSwipes.SPEC.get(p, FunctionSwipes.DELETE, Key.SWIPE_LEFT));
        assertEquals(SwipeAction.NONE, LongPressActions.SPEC.get(p, LongPressActions.DELETE, LongPressActions.DIR));
        assertEquals(SwipeAction.NONE, LongPressActions.SPEC.get(p, LongPressActions.MODE, LongPressActions.DIR));
        assertEquals(SwipeAction.IME_PICKER, LongPressActions.SPEC.get(p, LongPressActions.GLOBE, LongPressActions.DIR));
        assertEquals(SwipeAction.NONE, ToolbarSwipes.SPEC.get(p, ToolbarSwipes.BAR, Key.SWIPE_LEFT));
        assertEquals(SwipeAction.FREE_CURSOR, KeyboardSwipes.SPEC.get(p, KeyboardSwipes.ONE, Key.SWIPE_LEFT));
        assertEquals(SwipeAction.NONE, KeyboardSwipes.SPEC.get(p, KeyboardSwipes.ONE, Key.SWIPE_UP));
        assertEquals(SwipeAction.UNDO, KeyboardSwipes.SPEC.get(p, KeyboardSwipes.TWO, Key.SWIPE_LEFT));
        // 예전 스위치와 겹받침 편집 값은 지운다.
        for (String k : LegacySwipeSettings.LEGACY_KEYS) assertFalse(k, sp.contains(k));
        assertFalse(sp.contains("swipe_final_ㄹ_up"));
    }

    @Test
    public void newSwipeLayoutKeepsAspirates() throws Exception {
        MapSp sp = new MapSp();
        sp.m.put(Prefs.KOREAN_LAYOUT, Prefs.KOREAN_LAYOUT_D7);
        sp.m.put("d7_ss_up", true);
        assertKoreanSame(sp);
        Prefs p = prefs(sp);
        KeyboardLayout l = KeyboardLayout.korean(p);
        assertEquals("ㅋ", key(l, "ㄱ").swipeText(Key.SWIPE_RIGHT));
        assertEquals("ㅆ", key(l, "ㅅ").swipeText(Key.SWIPE_UP));
        assertTrue(KeyboardLayout.koreanAspirateSwipe(p));
    }

    @Test
    public void customGroupsAreLeftAlone() throws Exception {
        MapSp sp = new MapSp();
        sp.m.put("swipe_custom", true);
        sp.m.put("swipe_double", false);   // 완전 사용자화를 켜 둔 동안에는 쓰이지 않던 스위치
        sp.m.put("swipe_custom_ko_ㄱ_up", "x");
        Prefs p = prefs(sp);
        LegacySwipeSettings.migrateIfNeeded(p);
        KeyboardLayout l = KeyboardLayout.korean(p);
        assertEquals("x", key(l, "ㄱ").swipeText(Key.SWIPE_UP));
        assertEquals("ㄲ", key(l, "ㄱ").swipeText(Key.SWIPE_DOWN));
    }

    @Test
    public void freshInstallUsesNewDefaults() throws Exception {
        MapSp sp = new MapSp();
        Prefs p = prefs(sp);
        LegacySwipeSettings.migrateIfNeeded(p);
        assertEquals(2, sp.getAll().size());   // 옮김 표시 두 개뿐
        assertEquals(SwipeAction.UNDO, KeyboardSwipes.SPEC.get(p, KeyboardSwipes.TWO, Key.SWIPE_LEFT));
    }

    @Test
    public void runsOnlyOnce() throws Exception {
        MapSp sp = new MapSp();
        sp.m.put(Prefs.ONBOARDING_DONE, true);
        Prefs p = prefs(sp);
        LegacySwipeSettings.migrateIfNeeded(p);
        KeyboardSwipes.SPEC.set(p, KeyboardSwipes.TWO, Key.SWIPE_LEFT, SwipeAction.UNDO);   // 옮긴 뒤 사용자가 바꿨다
        LegacySwipeSettings.migrateIfNeeded(p);
        assertEquals(SwipeAction.UNDO, KeyboardSwipes.SPEC.get(p, KeyboardSwipes.TWO, Key.SWIPE_LEFT));
    }

    @Test
    public void longPressSwitchOffKeepsKeysQuiet() throws Exception {
        MapSp sp = new MapSp();
        sp.m.put(LegacySwipeSettings.DONE, true);   // 밀기 설정은 이미 옮겼다
        sp.m.put("long_press_chars", false);
        sp.m.put("popup_ko_ㄱ", "x\ny");            // 꺼 둔 동안 쓰이지 않던 문자
        sp.m.put("repeat_ko_ㄴ", true);              // '연속 입력'이 꺼져 있어 쓰이지 않던 값
        Prefs p = prefs(sp);
        LegacySwipeSettings.migrateIfNeeded(p);
        // 모든 키의 길게 누르기가 '없음' (예전처럼 아무것도 나오지 않는다)
        KeyboardLayout l = KeyboardLayout.korean(p);
        for (KeyboardLayout.Row r : l.rows) for (Key k : r.keys) if (k.type == Key.CHAR) assertEquals(k.label, null, k.popup);
        assertEquals(0, KeyboardLayout.english(p).rows[0].keys[0].popup == null ? 0 : 1);
        assertFalse(p.repeatChar("ko", "ㄴ"));
        // '문자 입력'으로 되살리면 정해 두었던 문자가 돌아온다
        p.turnOnPopup("ko", "ㄱ");
        org.junit.Assert.assertArrayEquals(new String[]{"x", "y"}, p.popupOverride("ko", "ㄱ"));
        p.turnOnPopup("ko", "ㄴ");
        assertEquals(null, p.popupOverride("ko", "ㄴ"));   // 기본 문자로
        assertFalse(sp.contains("long_press_chars"));
    }

    @Test
    public void longPressRepeatOnKeepsRepeatKeys() throws Exception {
        MapSp sp = new MapSp();
        sp.m.put(LegacySwipeSettings.DONE, true);
        sp.m.put("long_press_repeat", true);
        sp.m.put("repeat_ko_ㄴ", true);
        Prefs p = prefs(sp);
        LegacySwipeSettings.migrateIfNeeded(p);
        assertTrue(p.repeatChar("ko", "ㄴ"));
        assertTrue(p.repeatChars().contains("ko_ㄴ"));
        assertEquals(null, p.popupOverride("ko", "ㄱ"));   // 문자 입력은 그대로
        assertFalse(sp.contains("long_press_repeat"));
    }
}
