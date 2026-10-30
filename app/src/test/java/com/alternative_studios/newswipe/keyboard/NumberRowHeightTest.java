package com.alternative_studios.newswipe.keyboard;

import static org.junit.Assert.assertEquals;

import android.content.SharedPreferences;

import com.alternative_studios.newswipe.Prefs;

import org.junit.Test;

import java.lang.reflect.Constructor;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/** '숫자 줄 높이': 숫자 줄을 켠 모든 자판의 첫 줄이 이 높이를 쓰고, 다른 줄은 그대로인지. */
public class NumberRowHeightTest {
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

    private static Prefs view(FakeSp sp, String profile, boolean profileEdit) throws Exception {
        Constructor<Prefs> c = Prefs.class.getDeclaredConstructor(SharedPreferences.class, boolean.class, String.class,
                boolean.class);
        c.setAccessible(true);
        return c.newInstance(sp, false, profile, profileEdit);
    }

    /** 숫자 줄을 켠 자판들: 한글(두 배열, 균형 레이아웃 켬·끔)·영어·기호 1·2쪽. */
    private static KeyboardLayout[] layouts(FakeSp sp, Prefs p) {
        java.util.List<KeyboardLayout> out = new java.util.ArrayList<>();
        for (String layout : new String[]{Prefs.KOREAN_LAYOUT_8, Prefs.KOREAN_LAYOUT_D7}) {
            sp.m.put(Prefs.KOREAN_LAYOUT, layout);
            for (boolean balanced : new boolean[]{false, true}) {
                sp.m.put(p.balancedLayoutKey(), balanced);
                out.add(KeyboardLayout.korean(p));
            }
        }
        out.add(KeyboardLayout.english(p));
        out.add(KeyboardLayout.symbols(true, p));
        out.add(KeyboardLayout.symbols2(true, p));
        return out.toArray(new KeyboardLayout[0]);
    }

    @Test
    public void defaultIsEightyPercent() throws Exception {
        FakeSp sp = new FakeSp();
        Prefs p = view(sp, Prefs.PROFILE_PORTRAIT, false);
        sp.m.put(Prefs.NUMBER_ROW, true);
        assertEquals(80, p.numberRowHeight());
        for (KeyboardLayout l : layouts(sp, p)) assertEquals(0.8f, l.rows[0].height, 0.001f);
        assertEquals(0.8f, KeyboardLayout.numberRowHeight(null), 0.001f);
    }

    @Test
    public void everyNumberRowFollowsTheSettingAndOtherRowsStayFull() throws Exception {
        for (int h = Prefs.NUMBER_ROW_HEIGHT_MIN; h <= Prefs.NUMBER_ROW_HEIGHT_MAX; h += 5) {
            FakeSp sp = new FakeSp();
            sp.m.put(Prefs.NUMBER_ROW, true);
            sp.m.put(Prefs.NUMBER_ROW_HEIGHT, h);
            for (Prefs p : new Prefs[]{view(sp, Prefs.PROFILE_PORTRAIT, false), view(sp, Prefs.PROFILE_LANDSCAPE, true)}) {
                for (KeyboardLayout l : layouts(sp, p)) {
                    assertEquals("숫자 줄 " + h, h / 100f, l.rows[0].height, 0.001f);
                    for (int r = 1; r < l.rows.length; r++) assertEquals(1f, l.rows[r].height, 0.001f);
                    assertEquals(l.rows.length - 1 + h / 100f, l.totalHeight(), 0.001f);
                }
            }
        }
    }

    @Test
    public void heightIsClampedToItsRange() throws Exception {
        FakeSp sp = new FakeSp();
        Prefs p = view(sp, Prefs.PROFILE_PORTRAIT, false);
        sp.m.put(Prefs.NUMBER_ROW_HEIGHT, 10);
        assertEquals(Prefs.NUMBER_ROW_HEIGHT_MIN, p.numberRowHeight());
        sp.m.put(Prefs.NUMBER_ROW_HEIGHT, 150);
        assertEquals(Prefs.NUMBER_ROW_HEIGHT_MAX, p.numberRowHeight());
    }

    /** 숫자 줄을 끄면 기호 자판의 첫 줄도 일반 높이다. */
    @Test
    public void symbolsWithoutNumberRowKeepFullRows() throws Exception {
        FakeSp sp = new FakeSp();
        sp.m.put(Prefs.NUMBER_ROW_HEIGHT, 60);
        Prefs p = view(sp, Prefs.PROFILE_PORTRAIT, false);
        for (KeyboardLayout l : new KeyboardLayout[]{KeyboardLayout.symbols(true, p), KeyboardLayout.korean(p)}) {
            for (KeyboardLayout.Row row : l.rows) assertEquals(1f, row.height, 0.001f);
        }
    }
}
