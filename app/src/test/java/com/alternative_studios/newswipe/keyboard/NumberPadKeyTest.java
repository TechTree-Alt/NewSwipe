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

/** 3×4 숫자 패드 (숫자 입력란과 '기호 자판 대신 숫자 패드 사용'이 같이 쓴다): 기호 자판·글자 자판으로 가는 키가 있다. */
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
        assertEquals(5, pad.columns);
        assertEquals(4, pad.rows.length);
        for (int r = 0; r < 3; r++) assertEquals(5, pad.rows[r].keys.length);
        assertEquals(6, pad.rows[3].keys.length);   // 맨 아래 왼쪽 칸은 둘로 나뉜다
        assertEquals("+", pad.rows[0].keys[0].output);
        assertEquals(",", pad.rows[1].keys[0].output);
        assertEquals(".", pad.rows[2].keys[0].output);
        Key letters = pad.rows[3].keys[0], symbols = pad.rows[3].keys[1];
        assertEquals(Key.TO_SYMBOLS, symbols.type);
        assertEquals("?!@", symbols.label);
        assertEquals(Key.TO_LETTERS, letters.type);
        assertEquals("가", letters.label);
        assertEquals(1f, symbols.weight + letters.weight, 0.001f);
        // 3×4 숫자 부분: 1~9·*·0·# 과 오른쪽 줄 (-·스페이스바·⌫·엔터)
        String[][] digits = {{"1", "2", "3"}, {"4", "5", "6"}, {"7", "8", "9"}, {"*", "0", "#"}};
        int[] right = {Key.CHAR, Key.SPACE, Key.DELETE, Key.ENTER};
        for (int r = 0; r < 4; r++) {
            int off = r == 3 ? 2 : 1;
            for (int i = 0; i < 3; i++) assertEquals(digits[r][i], pad.rows[r].keys[i + off].output);
            assertEquals(right[r], pad.rows[r].keys[off + 3].type);
        }
        assertEquals("-", pad.rows[0].keys[4].output);
    }
}
