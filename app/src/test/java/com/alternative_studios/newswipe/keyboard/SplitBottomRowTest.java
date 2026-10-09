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
        for (int r = 0; r < l.rows.length - 1; r++) {
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
            assertBottomMatchesLetters(KeyboardLayout.symbols(true, p));
            assertBottomMatchesLetters(KeyboardLayout.symbols2(false, p));
        }
    }

    @Test
    public void symbolPagesAreSplitInHalvesIncludingTheNumberRow() throws Exception {
        for (boolean numberRow : new boolean[]{false, true}) {
            FakeSp sp = new FakeSp();
            Prefs p = landscapeEditView(sp);
            sp.m.put(p.balancedConsonantWidthKey(), 60);   // 폭을 줄이면 가운데 빈틈이 생긴다
            sp.m.put(p.balancedVowelWidthKey(), 60);
            sp.m.put(Prefs.NUMBER_ROW, numberRow);
            for (KeyboardLayout l : new KeyboardLayout[]{KeyboardLayout.symbols(true, p), KeyboardLayout.symbols2(true, p)}) {
                assertEquals(numberRow ? 5 : 4, l.rows.length);
                for (int r = 0; r < l.rows.length - 1; r++) {
                    // 줄 가운데(5칸)를 지나는 키가 없고, 양 절반에 키가 있다.
                    float x = 0f;
                    int leftKeys = 0, rightKeys = 0;
                    for (Key k : l.rows[r].keys) {
                        if (k.type != Key.GAP) {
                            if (x + k.weight <= 5.001f) leftKeys++;
                            else if (x >= 4.999f) rightKeys++;
                            else throw new AssertionError("키가 가운데를 가로지릅니다: 줄 " + r);
                        }
                        x += k.weight;
                    }
                    assertTrue(leftKeys >= 4 && rightKeys >= 4);
                }
            }
        }
    }

    @Test
    public void symbolPagesAreNotSplitOutsideSplitView() {
        // 세로 모드(설정 보기 없음): 맨 아래 줄 위의 줄들에는 빈틈이 없다.
        KeyboardLayout l = KeyboardLayout.symbols(true, null);
        for (int r = 0; r < l.rows.length - 1; r++) {
            for (Key k : l.rows[r].keys) assertTrue(k.type != Key.GAP);
        }
    }

    @Test
    public void numberRowRightEdgeFollowsTheVowelKeys() throws Exception {
        int[][] settings = {{50, 50, 70, 80}, {100, 0, 60, 60}, {0, 100, 100, 100}, {100, 100, 50, 40}};
        for (int[] s : settings) {
            FakeSp sp = new FakeSp();
            Prefs p = landscapeEditView(sp);
            sp.m.put(Prefs.NUMBER_ROW, true);
            sp.m.put(p.balancedConsonantPosKey(), s[0]);
            sp.m.put(p.balancedVowelPosKey(), s[1]);
            sp.m.put(p.balancedConsonantWidthKey(), s[2]);
            sp.m.put(p.balancedVowelWidthKey(), s[3]);
            for (KeyboardLayout l : new KeyboardLayout[]{KeyboardLayout.korean(p), KeyboardLayout.english(p)}) {
                assertEquals(0.78f, l.rows[0].height, 0.001f);   // 숫자 줄
                float letterRight = 0f;
                for (int r = 1; r < l.rows.length - 1; r++) letterRight = Math.max(letterRight, extent(l.rows[r])[1]);
                float[] number = extent(l.rows[0]);
                assertTrue("숫자 줄이 글자 줄보다 오른쪽으로 나갑니다", number[1] <= letterRight + 0.001f);
                assertEquals(letterRight, number[1], 0.001f);
                assertEquals(extent(l.rows[1])[0], number[0], 0.001f);   // 왼쪽 끝도 그대로 맞는다
            }
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
