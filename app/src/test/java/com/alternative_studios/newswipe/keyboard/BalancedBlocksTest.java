package com.alternative_studios.newswipe.keyboard;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.content.SharedPreferences;

import com.alternative_studios.newswipe.Prefs;

import org.junit.Test;

import java.lang.reflect.Constructor;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * 세로 모드 자·모음 균형 배열과 분리 키보드의 두 묶음: 폭·가로 위치를 따르고, 자리가 있으면 폭 100% 넘게·다른 쪽 절반으로도
 * 넘어가며, 어떤 값이어도 두 묶음이 겹치거나 줄 밖으로 나가지 않는지. (예전 '자·모음 경계 위치'는 없앴고 늘 가운데에서 나눈다.)
 */
public class BalancedBlocksTest {
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

    /** 세로 모드 보기: layout 배열에서 균형 배열을 켜고 자음 폭·자음 위치·모음 폭·모음 위치를 정한다. */
    private static Prefs portrait(String layout, int cw, int cp, int vw, int vp, boolean grid) throws Exception {
        FakeSp sp = new FakeSp();
        sp.m.put(Prefs.KOREAN_LAYOUT, layout);
        sp.m.put(Prefs.GRID_LAYOUT, grid);
        Prefs p = view(sp, Prefs.PROFILE_PORTRAIT, false);
        sp.m.put(p.balancedLayoutKey(), true);
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

    /** 자기 절반 안의 값(폭 100% 이하, 위치 0~100)은 예전과 똑같이 놓인다. */
    @Test
    public void keysStayInsideTheirSideAndFollowWidthAndPosition() throws Exception {
        int[] widths = {60, 80, 100}, positions = {0, 25, 50, 100};
        for (String layout : LAYOUTS) {
            int consonants = Prefs.KOREAN_LAYOUT_D7.equals(layout) ? 4 : 5;
            for (int cw : widths) for (int cp : positions) for (int vw : widths) for (int vp : positions) {
                Prefs p = portrait(layout, cw, cp, vw, vp, false);
                KeyboardLayout l = KeyboardLayout.korean(p);
                String at = layout + " 자음 " + cw + "/" + cp + " 모음 " + vw + "/" + vp;
                for (int r = 0; r < 3; r++) {
                    KeyboardLayout.Row row = l.rows[r];
                    assertEquals(at + " 줄 폭", 10f, total(row), 0.001f);
                    float[][] s = spans(row);
                    int vowels = s.length - consonants;
                    assertEquals(at + " 모음 쪽은 3열", 3, vowels);
                    float ck = 5f / consonants * cw / 100f, vk = 5f / vowels * vw / 100f;
                    float cStart = (5f - consonants * ck) * cp / 100f;
                    float vStart = 5f + (5f - vowels * vk) * vp / 100f;
                    for (int i = 0; i < s.length; i++) {
                        boolean c = i < consonants;
                        float w = c ? ck : vk;
                        float start = c ? cStart + i * ck : vStart + (i - consonants) * vk;
                        assertEquals(at + " 줄 " + r + " 키 " + i + " 폭", w, s[i][1] - s[i][0], 0.001f);
                        assertEquals(at + " 줄 " + r + " 키 " + i + " 시작", start, s[i][0], 0.001f);
                    }
                }
                assertEquals(at, cw, p.balancedConsonantWidth());
                assertEquals(at, cp, p.balancedConsonantPos());
                assertEquals(at, vw, p.balancedVowelWidth());
                assertEquals(at, vp, p.balancedVowelPos());
            }
        }
    }

    @Test
    public void defaultKeepsTheOldHalves() throws Exception {
        FakeSp sp = new FakeSp();
        Prefs p = view(sp, Prefs.PROFILE_PORTRAIT, false);
        sp.m.put(p.balancedLayoutKey(), true);
        sp.m.put(Prefs.BALANCED_BOUNDARY + "_" + Prefs.KOREAN_LAYOUT_8, 70);   // 없앤 경계 값은 쓰지 않는다
        float[][] s = spans(KeyboardLayout.korean(p).rows[0]);
        for (int i = 0; i < 5; i++) assertEquals("자음 1칸", 1f, s[i][1] - s[i][0], 0.001f);
        for (int i = 5; i < 8; i++) assertEquals("모음 5/3칸", 5f / 3f, s[i][1] - s[i][0], 0.001f);
    }

    /** 다른 쪽 묶음이 비켜 준 자리가 있으면 폭을 100% 넘게 넓힐 수 있다. */
    @Test
    public void widthGrowsPastHalfWhenTheOtherLeavesRoom() throws Exception {
        // 모음 쪽: 폭 60%(3칸)를 오른쪽 끝에 → 7칸부터. 자음 쪽은 130%(6.5칸)까지 넓어진다.
        Prefs p = portrait(Prefs.KOREAN_LAYOUT_8, 130, 0, 60, 100, false);
        assertEquals(130, p.balancedConsonantWidth());
        float[][] s = spans(KeyboardLayout.korean(p).rows[0]);
        assertEquals(0f, s[0][0], 0.001f);
        assertEquals(6.5f, s[4][1], 0.001f);
        assertEquals(7f, s[5][0], 0.001f);
        // 자리가 모자라면 다른 묶음 앞까지만 (모음 쪽이 절반을 꽉 채우면 자음 쪽은 100%)
        Prefs full = portrait(Prefs.KOREAN_LAYOUT_8, 140, 0, 100, 50, false);
        assertEquals(100, full.balancedConsonantWidth());
        float[][] f = spans(KeyboardLayout.korean(full).rows[0]);
        assertTrue(f[4][1] <= f[5][0] + 0.001f);
        // 모음 쪽도 같다: 자음 쪽을 60%로 왼쪽 끝에 두면 모음 쪽은 140%까지
        Prefs v = portrait(Prefs.KOREAN_LAYOUT_8, 60, 0, 140, 100, false);
        assertEquals(140, v.balancedVowelWidth());
        float[][] vs = spans(KeyboardLayout.korean(v).rows[0]);
        assertEquals(3f, vs[5][0], 0.001f);
        assertEquals(10f, vs[7][1], 0.001f);
    }

    /** 자리가 있으면 왼쪽 묶음은 가로 위치 100 넘게, 오른쪽 묶음은 0 아래로 옮겨 다른 쪽 절반으로 넘어간다. */
    @Test
    public void positionCrossesTheMiddleWhenThereIsRoom() throws Exception {
        // 자음 60%(3칸), 위치 120 → 절반의 남는 폭(2칸) 끝에서 절반 폭의 20%(1칸) 더: 3칸부터 시작
        Prefs p = portrait(Prefs.KOREAN_LAYOUT_8, 60, 120, 60, 100, false);
        assertEquals(120, p.balancedConsonantPos());
        assertEquals(3f, spans(KeyboardLayout.korean(p).rows[0])[0][0], 0.001f);
        // 더 옮기면 모음 묶음(7칸부터) 앞에서 멈춘다: 4칸부터 → 위치 140
        Prefs stop = portrait(Prefs.KOREAN_LAYOUT_8, 60, 200, 60, 100, false);
        assertEquals(140, stop.balancedConsonantPos());
        float[][] s = spans(KeyboardLayout.korean(stop).rows[0]);
        assertEquals(4f, s[0][0], 0.001f);
        assertEquals(7f, s[4][1], 0.001f);
        // 모음 60%, 위치 -40 → 가운데에서 절반 폭의 40%(2칸) 왼쪽: 3칸부터 (자음 60%가 왼쪽 끝이면 자리가 있다)
        Prefs v = portrait(Prefs.KOREAN_LAYOUT_8, 60, 0, 60, -40, false);
        assertEquals(-40, v.balancedVowelPos());
        assertEquals(3f, spans(KeyboardLayout.korean(v).rows[0])[5][0], 0.001f);
        // 자음 묶음에 막히면 그 뒤에서 멈춘다
        Prefs vs = portrait(Prefs.KOREAN_LAYOUT_8, 100, 0, 60, -100, false);
        assertEquals(0, vs.balancedVowelPos());
        assertEquals(5f, spans(KeyboardLayout.korean(vs).rows[0])[5][0], 0.001f);
    }

    /**
     * 한쪽이 100% 넘게 넓거나 다른 쪽 절반으로 넘어가 있을 때 다른 쪽을 조절하면, 그쪽은 둘 사이의 빈 자리 안에서만 움직이고
     * 넘어가 있던 쪽은 밀려나지 않는다 (fitBlockValue).
     */
    @Test
    public void adjustingOneBlockOnlyUsesTheFreeSpace() throws Exception {
        // 자음 130%(0~6.5칸), 모음 60%를 오른쪽 끝에(7~10칸)
        Prefs p = portrait(Prefs.KOREAN_LAYOUT_8, 130, 0, 60, 100, false);
        FakeSp sp = (FakeSp) p.raw();
        // 모음을 왼쪽으로 옮기면 6.5칸(위치 75)에서 멈추고, 자음 쪽은 그대로
        int pos = p.fitBlockValue(p.balancedVowelPosKey(), 100, 0);
        assertEquals(75, pos);
        sp.m.put(p.balancedVowelPosKey(), pos);
        float[] e = p.balancedExtents();
        assertEquals(0f, e[0], 0.001f);
        assertEquals(6.5f, e[1], 0.001f);
        assertEquals(6.5f, e[2], 0.001f);
        assertEquals(130, p.balancedConsonantWidth());
        // 오른쪽 끝에 붙은 모음을 넓히면 빈 자리만큼만 (6.5칸부터 오른쪽 끝까지 3.5칸 → 70%)
        sp.m.put(p.balancedVowelPosKey(), 100);
        assertEquals(70, p.fitBlockValue(p.balancedVowelWidthKey(), 60, 140));
        // 모음이 왼쪽 절반으로 넘어가 있을 때(3~6칸) 자음을 넓히거나 옮겨도 0~3칸 안에서만
        Prefs q = portrait(Prefs.KOREAN_LAYOUT_8, 60, 0, 60, -40, false);
        assertEquals(60, q.fitBlockValue(q.balancedConsonantWidthKey(), 60, 140));
        assertEquals(0, q.fitBlockValue(q.balancedConsonantPosKey(), 0, 100));
        // 모음은 자음 쪽(0~3칸)을 밀지 않고 오른쪽으로 140%까지 넓어진다
        assertEquals(140, q.fitBlockValue(q.balancedVowelWidthKey(), 60, 140));
        // 자리가 있으면 바라는 값을 그대로 쓴다
        assertEquals(80, q.fitBlockValue(q.balancedVowelWidthKey(), 60, 80));
    }

    /** 어떤 값이어도 두 묶음은 겹치지 않고 줄 안에 있으며, 보이는 값(막힌 값)을 다시 저장해도 모양이 같다. */
    @Test
    public void blocksNeverOverlapForAnyValue() throws Exception {
        Random rnd = new Random(7);
        for (String profile : new String[]{Prefs.PROFILE_PORTRAIT, Prefs.PROFILE_LANDSCAPE}) {
            for (int n = 0; n < 400; n++) {
                FakeSp sp = new FakeSp();
                Prefs p = view(sp, profile, !profile.isEmpty());
                sp.m.put(p.balancedLayoutKey(), true);
                if (!profile.isEmpty()) sp.m.put(p.splitKey(), true);
                int min = p.balancedWidthMin(), max = p.balancedWidthMax();
                sp.m.put(p.balancedConsonantWidthKey(), min + rnd.nextInt(max - min + 1));
                sp.m.put(p.balancedVowelWidthKey(), min + rnd.nextInt(max - min + 1));
                sp.m.put(p.balancedConsonantPosKey(), rnd.nextInt(Prefs.BALANCED_LEFT_POS_MAX + 1));
                sp.m.put(p.balancedVowelPosKey(), Prefs.BALANCED_RIGHT_POS_MIN + rnd.nextInt(101 - Prefs.BALANCED_RIGHT_POS_MIN));
                float[] e = p.balancedExtents();
                String at = profile + " " + sp.m;
                assertTrue(at, e[0] >= -1e-4f);
                assertTrue(at, e[0] + e[1] <= e[2] + 1e-4f);
                assertTrue(at, e[2] + e[3] <= 10f + 1e-4f);
                assertTrue(at, e[1] > 0f && e[3] > 0f);
                // 보이는 값을 그대로 저장하면 같은 자리 (슬라이더가 막힌 값으로 돌아가도 모양이 튀지 않는다)
                sp.m.put(p.balancedConsonantWidthKey(), p.balancedConsonantWidth());
                sp.m.put(p.balancedConsonantPosKey(), p.balancedConsonantPos());
                sp.m.put(p.balancedVowelWidthKey(), p.balancedVowelWidth());
                sp.m.put(p.balancedVowelPosKey(), p.balancedVowelPos());
                float[] again = p.balancedExtents();
                for (int i = 0; i < 4; i++) assertEquals(at + " " + i, e[i], again[i], 0.11f);
                // 줄의 키도 겹치지 않는다
                if (profile.isEmpty()) {
                    for (KeyboardLayout.Row row : KeyboardLayout.korean(p).rows) {
                        assertEquals(at, 10f, total(row), 0.001f);
                        float x = 0f;
                        for (Key k : row.keys) {
                            assertTrue(at, k.weight >= -1e-4f);
                            x += k.weight;
                        }
                    }
                }
            }
        }
    }

    /** 폭·위치를 바꿔도 맨 아래 줄(기능키·스페이스바)은 그대로다 (격자 정렬일 때도). */
    @Test
    public void bottomRowDoesNotChangeWithTheBlocks() throws Exception {
        for (String layout : LAYOUTS) {
            for (boolean grid : new boolean[]{false, true}) {
                KeyboardLayout base = KeyboardLayout.korean(portrait(layout, 100, 50, 100, 50, grid));
                KeyboardLayout.Row want = base.rows[base.rows.length - 1];
                for (int[] v : new int[][]{{60, 0, 60, 100}, {130, 0, 60, 100}, {60, 150, 60, 100}, {60, 0, 60, -50}}) {
                    KeyboardLayout l = KeyboardLayout.korean(portrait(layout, v[0], v[1], v[2], v[3], grid));
                    KeyboardLayout.Row row = l.rows[l.rows.length - 1];
                    assertEquals(want.keys.length, row.keys.length);
                    for (int i = 0; i < row.keys.length; i++) {
                        assertEquals(want.keys[i].type, row.keys[i].type);
                        assertEquals(layout + " 아래 줄 키 " + i, want.keys[i].weight, row.keys[i].weight, 0.001f);
                    }
                }
            }
        }
    }
}
