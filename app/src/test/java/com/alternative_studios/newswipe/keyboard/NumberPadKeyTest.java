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

/** '기호 자판 대신 숫자 패드 사용'의 3×4 숫자 패드: 기호 자판·글자 자판으로 가는 키가 있고, 기호 자판은 그대로다. */
public class NumberPadKeyTest {
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

    private static int count(KeyboardLayout l, int type) {
        int n = 0;
        for (KeyboardLayout.Row row : l.rows) for (Key k : row.keys) if (k.type == type) n++;
        return n;
    }

    private static KeyboardLayout[] symbolPages(Prefs p) {
        return new KeyboardLayout[]{KeyboardLayout.symbols(true, p), KeyboardLayout.symbols2(true, p)};
    }

    @Test
    public void offByDefaultAndSymbolsKeepTheEmojiKey() throws Exception {
        FakeSp sp = new FakeSp();
        assertFalse(view(sp, Prefs.PROFILE_PORTRAIT).numberPadForSymbols());
        sp.m.put(Prefs.NUMBER_PAD_SYMBOLS, true);
        Prefs p = view(sp, Prefs.PROFILE_PORTRAIT);
        assertTrue(p.numberPadForSymbols());
        // 숫자 패드를 써도 기호 자판은 그대로다 (지구본 자리는 이모지 키)
        for (KeyboardLayout l : symbolPages(p)) assertEquals(1, count(l, Key.EMOJI));
    }

    @Test
    public void numberPadHasWayBack() {
        KeyboardLayout pad = KeyboardLayout.numberPad(KeyboardLayout.NUMBER_PAD_SYMBOLS_LABEL, "가");
        assertEquals(KeyboardLayout.NUMBER, pad.kind);
        assertTrue(pad.numberPad);   // 색 정렬에서 숫자 입력란의 자판과 따로 칠한다
        assertFalse(KeyboardLayout.number().numberPad);
        assertEquals(5, pad.columns);
        assertEquals(4, pad.rows.length);
        for (KeyboardLayout.Row row : pad.rows) assertEquals(5, row.keys.length);
        Key letters = pad.rows[0].keys[0], symbols = pad.rows[1].keys[0];
        assertEquals(Key.TO_LETTERS, letters.type);
        assertEquals("가", letters.label);
        assertEquals(Key.TO_SYMBOLS, symbols.type);
        assertEquals("?!@", symbols.label);
        assertEquals(",", pad.rows[2].keys[0].output);
        assertEquals(".", pad.rows[3].keys[0].output);
        // 나머지는 숫자 입력란의 자판과 같다
        KeyboardLayout field = KeyboardLayout.number();
        for (int r = 0; r < 4; r++) {
            for (int i = 0; i < field.rows[r].keys.length; i++) {
                Key a = field.rows[r].keys[i], b = pad.rows[r].keys[i + 1];
                assertEquals(a.type, b.type);
                assertEquals(a.label, b.label);
            }
        }
        // 숫자 입력란의 자판에는 돌아가는 키가 없다
        assertEquals(0, count(field, Key.TO_SYMBOLS));
        assertEquals(0, count(field, Key.TO_LETTERS));
    }
}
