package com.alternative_studios.newswipe.keyboard;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.content.SharedPreferences;

import com.alternative_studios.newswipe.Prefs;

import org.junit.Test;

import java.lang.reflect.Constructor;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/** 기호 자판의 기호 키: 누르면 입력할 글자와 네 방향 밀기를 키마다 정한다 (설정은 원래 기호 자리에 저장된다). */
public class SymbolKeyCustomTest {
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

    private static Prefs prefs(FakeSp sp) throws Exception {
        Constructor<Prefs> c = Prefs.class.getDeclaredConstructor(SharedPreferences.class, boolean.class, String.class,
                boolean.class);
        c.setAccessible(true);
        return c.newInstance(sp, false, Prefs.PROFILE_PORTRAIT, false);
    }

    private static Key find(KeyboardLayout l, String slot) {
        for (KeyboardLayout.Row row : l.rows) {
            for (Key k : row.keys) if (k.type == Key.CHAR && slot.equals(k.slot())) return k;
        }
        return null;
    }

    @Test
    public void tapCharAndSwipesApplyToSymbolKey() throws Exception {
        FakeSp sp = new FakeSp();
        sp.m.put(Prefs.SYMBOL_CHAR_PREFIX + "@", "♥");
        sp.m.put("swipe_custom_sym_@_up", "©");
        sp.m.put("swipe_custom_sym_@_left", "");   // 없음
        sp.m.put("swipe_custom_sym_*_down", "※");
        sp.m.put("popup_sym_@", "a\nb");
        Prefs p = prefs(sp);
        Key at = find(KeyboardLayout.symbols(true, p), "@");
        assertNotNull(at);
        assertEquals("♥", at.label);
        assertEquals("♥", at.output);
        assertEquals("@", at.slot());
        assertEquals("©", at.swipeText(Key.SWIPE_UP));
        assertNull(at.swipeText(Key.SWIPE_LEFT));
        assertNull(at.swipeText(Key.SWIPE_DOWN));
        assertArrayEquals(new String[]{"a", "b"}, at.popup);
        Key star = find(KeyboardLayout.symbols(true, p), "*");
        assertEquals("*", star.label);
        assertEquals("※", star.swipeText(Key.SWIPE_DOWN));
    }

    @Test
    public void sameSymbolSharesSettingsOnBothPages() throws Exception {
        FakeSp sp = new FakeSp();
        sp.m.put(Prefs.NUMBER_ROW, true);   // 숫자 줄을 켜면 '^'·'='가 1쪽, 끄면 2쪽에 있다
        sp.m.put(Prefs.SYMBOL_CHAR_PREFIX + "^", "ˇ");
        Prefs p = prefs(sp);
        assertEquals("ˇ", find(KeyboardLayout.symbols(true, p), "^").label);
        sp.m.put(Prefs.NUMBER_ROW, false);
        assertEquals("ˇ", find(KeyboardLayout.symbols2(true, p), "^").label);
    }

    @Test
    public void keyCharFallsBackForUnchangedAndNonSymbolKeys() throws Exception {
        Prefs p = prefs(new FakeSp());
        assertEquals("@", p.keyChar("@"));
        assertEquals("a", p.keyChar("a"));
        assertEquals("1", p.keyChar("1"));
        assertEquals(",", p.keyChar(","));
    }

    @Test
    public void singleCharChecks() {
        assertTrue(Prefs.isSingleChar("♥"));
        assertTrue(Prefs.isSingleChar("❤️"));   // 이모지 + 변형 선택자
        assertFalse(Prefs.isSingleChar("ab"));
        assertFalse(Prefs.isSingleChar(" "));
        assertFalse(Prefs.isSingleChar(""));
        assertTrue(Prefs.isSingleCodePoint("…"));
        assertFalse(Prefs.isSingleCodePoint("❤️"));
    }
}
