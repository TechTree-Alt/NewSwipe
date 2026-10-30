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

/** 세로 모드 자·모음 균형 레이아웃의 '자·모음 경계 위치': 자음·모음 폭·위치 슬라이더가 바뀐 경계 안에서 맞게 움직이는지. */
public class BalancedBoundaryTest {
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

    private static final String[] LAYOUTS = {Prefs.KOREAN_LAYOUT_8, Prefs.KOREAN_LAYOUT_D7};

    /** 세로 모드 보기: layout 배열에서 균형 레이아웃을 켜고 경계·자음 폭·자음 위치·모음 폭·모음 위치를 정한다. */
    private static Prefs portrait(String layout, int boundary, int cw, int cp, int vw, int vp, boolean grid)
            throws Exception {
        FakeSp sp = new FakeSp();
        sp.m.put(Prefs.KOREAN_LAYOUT, layout);
        sp.m.put(Prefs.GRID_LAYOUT, grid);
        Prefs p = view(sp, Prefs.PROFILE_PORTRAIT, false);
        sp.m.put(p.balancedLayoutKey(), true);
        sp.m.put(p.balancedBoundaryKey(), boundary);
        sp.m.put(p.balancedConsonantWidthKey(), cw);
        sp.m.put(p.balancedConsonantPosKey(), cp);
        sp.m.put(p.balancedVowelWidthKey(), vw);
        sp.m.put(p.balancedVowelPosKey(), vp);
        return p;
    }

    /** 줄에서 빈틈이 아닌 키들의 {시작, 끝} (칸 단위). */
    private static float[][] spans(KeyboardLayout.Row row) {
        java.util.List<float[]> out = new java.util.ArrayList<>();
        float x = 0f;
        for (Key k : row.keys) {
            if (k.type != Key.GAP) out.add(new float[]{x, x + k.weight});
            x += k.weight;
        }
        return out.toArray(new float[0][]);
    }

    private static float total(KeyboardLayout.Row row) {
        float x = 0f;
        for (Key k : row.keys) x += k.weight;
        return x;
    }

    @Test
    public void keysStayInsideTheirSideAndFollowWidthAndPosition() throws Exception {
        int[] boundaries = {30, 40, 50, 60, 70}, widths = {60, 80, 100}, positions = {0, 25, 50, 100};
        for (String layout : LAYOUTS) {
            int consonants = Prefs.KOREAN_LAYOUT_D7.equals(layout) ? 4 : 5;
            for (int b : boundaries) for (int cw : widths) for (int cp : positions)
                for (int vw : widths) for (int vp : positions) {
                    Prefs p = portrait(layout, b, cw, cp, vw, vp, false);
                    KeyboardLayout l = KeyboardLayout.korean(p);
                    float left = 10f * b / 100f, right = 10f - left;
                    String at = layout + " 경계 " + b + " 자음 " + cw + "/" + cp + " 모음 " + vw + "/" + vp;
                    for (int r = 0; r < 3; r++) {
                        KeyboardLayout.Row row = l.rows[r];
                        assertEquals(at + " 줄 폭", 10f, total(row), 0.001f);
                        float[][] s = spans(row);
                        int vowels = s.length - consonants;
                        assertEquals(at + " 모음 쪽은 3열", 3, vowels);
                        float ck = left / consonants * cw / 100f, vk = right / vowels * vw / 100f;
                        float cStart = (left - consonants * ck) * cp / 100f;
                        float vStart = left + (right - vowels * vk) * vp / 100f;
                        for (int i = 0; i < s.length; i++) {
                            boolean c = i < consonants;
                            float w = c ? ck : vk;
                            float start = c ? cStart + i * ck : vStart + (i - consonants) * vk;
                            assertEquals(at + " 줄 " + r + " 키 " + i + " 폭", w, s[i][1] - s[i][0], 0.001f);
                            assertEquals(at + " 줄 " + r + " 키 " + i + " 시작", start, s[i][0], 0.001f);
                            if (c) assertTrue(at + " 자음이 경계를 넘음", s[i][1] <= left + 0.001f);
                            else assertTrue(at + " 모음이 경계를 넘음", s[i][0] >= left - 0.001f);
                        }
                    }
                }
        }
    }

    @Test
    public void defaultBoundaryKeepsTheOldHalves() throws Exception {
        FakeSp sp = new FakeSp();
        Prefs p = view(sp, Prefs.PROFILE_PORTRAIT, false);
        sp.m.put(p.balancedLayoutKey(), true);
        assertEquals(50, p.balancedBoundary());
        float[][] s = spans(KeyboardLayout.korean(p).rows[0]);
        for (int i = 0; i < 5; i++) assertEquals("자음 1칸", 1f, s[i][1] - s[i][0], 0.001f);
        for (int i = 5; i < 8; i++) assertEquals("모음 5/3칸", 5f / 3f, s[i][1] - s[i][0], 0.001f);
    }

    @Test
    public void boundaryIsClampedToItsRange() throws Exception {
        FakeSp sp = new FakeSp();
        Prefs p = view(sp, Prefs.PROFILE_PORTRAIT, false);
        sp.m.put(p.balancedBoundaryKey(), 10);
        assertEquals(Prefs.BALANCED_BOUNDARY_MIN, p.balancedBoundary());
        sp.m.put(p.balancedBoundaryKey(), 90);
        assertEquals(Prefs.BALANCED_BOUNDARY_MAX, p.balancedBoundary());
    }

    /** 경계를 옮겨도 맨 아래 줄(기능키·스페이스바)은 그대로다 (격자 정렬일 때도). */
    @Test
    public void bottomRowDoesNotChangeWithTheBoundary() throws Exception {
        for (String layout : LAYOUTS) {
            for (boolean grid : new boolean[]{false, true}) {
                KeyboardLayout base = KeyboardLayout.korean(portrait(layout, 50, 100, 50, 100, 50, grid));
                KeyboardLayout.Row want = base.rows[base.rows.length - 1];
                for (int b : new int[]{30, 45, 70}) {
                    for (int w : new int[]{60, 100}) {
                        KeyboardLayout l = KeyboardLayout.korean(portrait(layout, b, w, 0, w, 100, grid));
                        KeyboardLayout.Row row = l.rows[l.rows.length - 1];
                        assertEquals(want.keys.length, row.keys.length);
                        for (int i = 0; i < row.keys.length; i++) {
                            assertEquals(want.keys[i].type, row.keys[i].type);
                            assertEquals(layout + " 경계 " + b + " 아래 줄 키 " + i, want.keys[i].weight,
                                    row.keys[i].weight, 0.001f);
                        }
                    }
                }
            }
        }
    }

    /** 분리 키보드(가로 모드)는 세로 모드의 경계 값을 쓰지 않고 늘 가운데에서 나눈다. */
    @Test
    public void splitViewIgnoresTheBoundary() throws Exception {
        FakeSp sp = new FakeSp();
        Prefs p = view(sp, Prefs.PROFILE_LANDSCAPE, true);
        sp.m.put(Prefs.BALANCED_BOUNDARY + "_" + Prefs.KOREAN_LAYOUT_8, 70);
        sp.m.put(p.balancedBoundaryKey(), 70);
        assertEquals(Prefs.BALANCED_BOUNDARY_DEFAULT, p.balancedBoundary());
    }
}
