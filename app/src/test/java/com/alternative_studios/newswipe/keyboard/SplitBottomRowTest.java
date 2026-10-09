package com.alternative_studios.newswipe.keyboard;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.content.SharedPreferences;

import com.alternative_studios.newswipe.Prefs;

import org.junit.Test;

import java.lang.reflect.Constructor;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/** 분리 키보드(가로 모드·대화면)에서 맨 아래 줄의 양 끝이 글자 줄의 가장 바깥 키와 열이 맞는지. */
public class SplitBottomRowTest {
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

    private static Prefs landscapeEditView(FakeSp sp) throws Exception {
        Constructor<Prefs> c = Prefs.class.getDeclaredConstructor(SharedPreferences.class, boolean.class, String.class,
                boolean.class);
        c.setAccessible(true);
        return c.newInstance(sp, false, Prefs.PROFILE_LANDSCAPE, true);
    }

    /** 줄에서 빈틈이 아닌 키의 가장 왼쪽 시작과 가장 오른쪽 끝. */
    private static float[] extent(KeyboardLayout.Row row) {
        float x = 0f, left = -1f, right = 0f;
        for (Key k : row.keys) {
            if (k.type != Key.GAP) {
                if (left < 0f) left = x;
                right = x + k.weight;
            }
            x += k.weight;
        }
        return new float[]{left, right};
    }

    private static void assertBottomMatchesLetters(KeyboardLayout l) {
        float left = Float.MAX_VALUE, right = 0f;
        for (int r = 0; r < 3; r++) {
            float[] e = extent(l.rows[r]);
            left = Math.min(left, e[0]);
            right = Math.max(right, e[1]);
        }
        float[] bottom = extent(l.rows[l.rows.length - 1]);
        assertEquals(left, bottom[0], 0.001f);
        assertEquals(right, bottom[1], 0.001f);
    }

    @Test
    public void bottomRowEdgesFollowTheOutermostLetterKeys() throws Exception {
        int[][] settings = {{100, 0, 60, 60}, {50, 50, 70, 80}, {100, 100, 50, 40}, {0, 100, 100, 100}};
        for (int[] s : settings) {
            FakeSp sp = new FakeSp();
            Prefs p = landscapeEditView(sp);
            sp.m.put(p.balancedConsonantPosKey(), s[0]);
            sp.m.put(p.balancedVowelPosKey(), s[1]);
            sp.m.put(p.balancedConsonantWidthKey(), s[2]);
            sp.m.put(p.balancedVowelWidthKey(), s[3]);
            assertBottomMatchesLetters(KeyboardLayout.korean(p));
            assertBottomMatchesLetters(KeyboardLayout.english(p));
        }
    }

    @Test
    public void bottomRowStillFillsItsSpanWithTheSpaceBar() throws Exception {
        FakeSp sp = new FakeSp();
        Prefs p = landscapeEditView(sp);
        sp.m.put(p.balancedConsonantWidthKey(), 60);
        sp.m.put(p.balancedConsonantPosKey(), 100);
        KeyboardLayout l = KeyboardLayout.korean(p);
        float total = 0f;
        boolean hasSpace = false;
        for (Key k : l.rows[l.rows.length - 1].keys) {
            total += k.weight;
            hasSpace |= k.type == Key.SPACE && k.weight > 0f;
        }
        assertEquals(10f, total, 0.001f);   // 양옆 빈틈까지 합쳐 줄 전체
        assertTrue(hasSpace);
    }
}
