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
    public void extraKeysHaveNoFunctionKeySlots() {
        // 밀기·길게 누르기 사용자화의 대상이 아니다 (언어 전환 키 등의 설정을 따라가지 않는다)
        for (int t : TYPES) {
            assertEquals(-1, FunctionSwipes.slotOf(t));
            assertEquals(-1, LongPressActions.slotOf(t));
        }
    }
}
