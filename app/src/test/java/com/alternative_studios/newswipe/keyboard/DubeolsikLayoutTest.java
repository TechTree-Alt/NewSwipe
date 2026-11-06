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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** 실험실 '두벌식 자판': 고른 배열·균형 레이아웃·분리 키보드·한 손 모드와 상관없이 표준 두벌식으로 보이는지. */
public class DubeolsikLayoutTest {
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

    private static final String[][] STANDARD = {
            {"ㅂ", "ㅈ", "ㄷ", "ㄱ", "ㅅ", "ㅛ", "ㅕ", "ㅑ", "ㅐ", "ㅔ"},
            {"ㅁ", "ㄴ", "ㅇ", "ㄹ", "ㅎ", "ㅗ", "ㅓ", "ㅏ", "ㅣ"},
            {"ㅋ", "ㅌ", "ㅊ", "ㅍ", "ㅠ", "ㅜ", "ㅡ"},
    };

    /** 두벌식을 켠 설정. 고른 배열(layout)과 그 배열의 균형 레이아웃(balanced)을 함께 정한다. */
    private static FakeSp dubeolsik(String layout, boolean balanced) {
        FakeSp sp = new FakeSp();
        sp.m.put(Prefs.LAB_DUBEOLSIK, true);
        sp.m.put(Prefs.KOREAN_LAYOUT, layout);
        sp.m.put(Prefs.BALANCED_LAYOUT + "_" + layout, balanced);
        return sp;
    }

    /** 줄의 글자 키 (밀기용으로 숨긴 키 제외). */
    private static List<Key> letters(KeyboardLayout.Row row) {
        List<Key> out = new ArrayList<>();
        for (Key k : row.keys) if (k.type == Key.CHAR && !k.hidden && !k.grayStyle) out.add(k);
        return out;
    }

    private static String[] labels(KeyboardLayout.Row row) {
        List<Key> ks = letters(row);
        String[] out = new String[ks.size()];
        for (int i = 0; i < out.length; i++) out[i] = ks.get(i).label;
        return out;
    }

    private static Key key(KeyboardLayout l, String label) {
        for (KeyboardLayout.Row row : l.rows) for (Key k : row.keys) if (label.equals(k.label) && !k.hidden) return k;
        return null;
    }

    private static int count(KeyboardLayout l, int type) {
        int n = 0;
        for (KeyboardLayout.Row row : l.rows) for (Key k : row.keys) if (k.type == type) n++;
        return n;
    }

    @Test
    public void standardRowsWhateverLayoutIsChosen() throws Exception {
        for (String layout : new String[]{Prefs.KOREAN_LAYOUT_8, Prefs.KOREAN_LAYOUT_D7}) {
            for (boolean balanced : new boolean[]{false, true}) {
                for (boolean numberRow : new boolean[]{false, true}) {
                    for (boolean grid : new boolean[]{false, true}) {
                        FakeSp sp = dubeolsik(layout, balanced);
                        sp.m.put(Prefs.NUMBER_ROW, numberRow);
                        sp.m.put(Prefs.GRID_LAYOUT, grid);
                        Prefs p = view(sp, Prefs.PROFILE_PORTRAIT);
                        KeyboardLayout l = KeyboardLayout.korean(p);
                        String what = layout + " balanced=" + balanced + " numberRow=" + numberRow + " grid=" + grid;
                        assertEquals(what, KeyboardLayout.KOREAN, l.kind);
                        assertEquals(what, 10, l.columns);
                        int first = numberRow ? 1 : 0;
                        assertEquals(what, first + 4, l.rows.length);
                        for (int r = 0; r < 3; r++) assertArrayEquals(what, STANDARD[r], labels(l.rows[first + r]));
                        // 셋째 줄은 Shift + 글자 + ⌫ (Shift는 '한글 Shift 키 없애기'와 상관없이 늘 있다)
                        KeyboardLayout.Row third = l.rows[first + 2];
                        assertEquals(what, Key.SHIFT, third.keys[0].type);
                        assertEquals(what, Key.DELETE, third.keys[third.keys.length - 1].type);
                        assertEquals(what, 1, count(l, Key.SHIFT));
                        assertEquals(what, 0, count(l, Key.FUNCTION));
                        assertFalse(what, p.koreanNewSwipe());
                        assertFalse(what, p.balancedLayout());
                        assertEquals(what, 10, KeyboardLayout.koreanColumns(p));
                    }
                }
            }
        }
    }

    @Test
    public void shiftFollowsTheStandard() throws Exception {
        KeyboardLayout l = KeyboardLayout.korean(view(dubeolsik(Prefs.KOREAN_LAYOUT_8, false), Prefs.PROFILE_PORTRAIT));
        String[][] expect = {{"ㅂ", "ㅃ"}, {"ㅈ", "ㅉ"}, {"ㄷ", "ㄸ"}, {"ㄱ", "ㄲ"}, {"ㅅ", "ㅆ"}, {"ㅐ", "ㅒ"}, {"ㅔ", "ㅖ"},
                // 단모음 자판과 달리 ㅗ·ㅏ 등은 Shift를 눌러도 같다 (ㅛ·ㅑ 키가 따로 있다)
                {"ㅗ", "ㅗ"}, {"ㅏ", "ㅏ"}, {"ㅓ", "ㅓ"}, {"ㅜ", "ㅜ"}, {"ㅛ", "ㅛ"}, {"ㅁ", "ㅁ"}, {"ㅋ", "ㅋ"}};
        for (String[] e : expect) assertEquals(e[0], e[1], key(l, e[0]).shifted);
    }

    @Test
    public void swipesKeepWorking() throws Exception {
        // 기본 설정: 밀어서 쌍자음·ㅣ계 이중모음·겹받침. NewSwipe 단모음을 골라 두었어도 ㄱ 오른쪽은 ㅋ이 아니다 (ㅋ 키가 따로 있다).
        KeyboardLayout l = KeyboardLayout.korean(view(dubeolsik(Prefs.KOREAN_LAYOUT_D7, false), Prefs.PROFILE_PORTRAIT));
        assertEquals("ㄲ", key(l, "ㄱ").swipeDown);
        assertEquals("ㅑ", key(l, "ㅏ").swipeDown);
        assertNull(key(l, "ㅑ").swipeDown);   // ㅣ계 이중모음 키는 더 밀 것이 없다
        assertEquals(FinalSwipes.get(null, 'ㄹ', FinalSwipes.UP), key(l, "ㄹ").swipeUp);
        assertEquals("ㄳ", key(l, "ㄱ").swipeRight);   // 겹받침 (NewSwipe 단모음이면 ㅋ)
        assertTrue(!"ㅊ".equals(key(l, "ㅈ").swipeRight));
    }

    @Test
    public void popupsHaveDefaultsForEveryKey() throws Exception {
        Prefs p = view(dubeolsik(Prefs.KOREAN_LAYOUT_8, false), Prefs.PROFILE_PORTRAIT);
        KeyboardLayout l = KeyboardLayout.korean(p);
        for (String[] row : STANDARD) {
            for (String label : row) {
                assertNotNull(label, key(l, label).popup);
                assertArrayEquals(label, KeyboardLayout.defaultPopup(true, p, label, true), key(l, label).popup);
            }
        }
        assertArrayEquals(new String[]{"6"}, key(l, "ㅛ").popup);   // 첫 줄은 숫자
        // 편집 화면은 두벌식의 키를 보인다
        String[][] editable = KeyboardLayout.editableRows(true, p);
        for (int r = 0; r < 3; r++) assertArrayEquals(STANDARD[r], editable[r]);
    }

    @Test
    public void splitKeyboardPutsConsonantsLeftAndVowelsRight() throws Exception {
        for (String layout : new String[]{Prefs.KOREAN_LAYOUT_8, Prefs.KOREAN_LAYOUT_D7}) {
            for (boolean reach : new boolean[]{false, true}) {
                FakeSp sp = dubeolsik(layout, false);
                sp.m.put(Prefs.LANDSCAPE_SPLIT, true);
                Prefs p = view(sp, Prefs.PROFILE_LANDSCAPE);
                sp.m.put(p.splitReachColumnKey(), reach);   // 좌·우 균형 레이아웃은 두벌식에 쓰이지 않는다
                assertTrue(p.splitView());
                KeyboardLayout l = KeyboardLayout.korean(p);
                assertEquals(KeyboardLayout.KOREAN, l.kind);
                for (int r = 0; r < 3; r++) {
                    assertArrayEquals(layout, STANDARD[r], labels(l.rows[r]));
                    // 가운데(5칸)보다 왼쪽은 자음, 오른쪽은 모음
                    float x = 0f;
                    for (Key k : l.rows[r].keys) {
                        if (k.type == Key.CHAR && !k.hidden) {
                            boolean vowel = com.alternative_studios.newswipe.hangul.HangulComposer.isVowel(k.label.charAt(0));
                            assertEquals(layout + " " + k.label, vowel, x + k.weight / 2f > 5f);
                        }
                        x += k.weight;
                    }
                    assertEquals(10f, x, 1e-3f);
                }
                assertEquals(1, count(l, Key.SHIFT));
            }
        }
    }

    @Test
    public void oneHandModeKeepsDubeolsik() throws Exception {
        FakeSp sp = dubeolsik(Prefs.KOREAN_LAYOUT_8, true);
        Prefs p = view(sp, Prefs.PROFILE_PORTRAIT).oneHandView();
        KeyboardLayout l = KeyboardLayout.korean(p);
        for (int r = 0; r < 3; r++) assertArrayEquals(STANDARD[r], labels(l.rows[r]));
    }

    @Test
    public void bottomRowPreviewShowsTheShiftRow() throws Exception {
        KeyboardLayout l = KeyboardLayout.bottomRowPreview(view(dubeolsik(Prefs.KOREAN_LAYOUT_D7, false),
                Prefs.PROFILE_PORTRAIT));
        assertEquals(2, l.rows.length);
        assertArrayEquals(STANDARD[2], labels(l.rows[0]));
        assertEquals(Key.SHIFT, l.rows[0].keys[0].type);
    }

    @Test
    public void offTurnsBackToTheChosenLayout() throws Exception {
        FakeSp sp = dubeolsik(Prefs.KOREAN_LAYOUT_D7, false);
        sp.m.put(Prefs.LAB_DUBEOLSIK, false);
        Prefs p = view(sp, Prefs.PROFILE_PORTRAIT);
        assertTrue(p.koreanNewSwipe());
        assertEquals(7, KeyboardLayout.korean(p).columns);
        assertEquals(Prefs.KOREAN_LAYOUT_D7, p.koreanLayoutId());
        assertNull(KeyboardLayout.koreanRowOffsets(p));
        assertFalse(KeyboardLayout.koreanIotizedKeys(p));
    }

    @Test
    public void autocorrectGetsTheDubeolsikGrid() throws Exception {
        Prefs p = view(dubeolsik(Prefs.KOREAN_LAYOUT_D7, false), Prefs.PROFILE_PORTRAIT);
        assertArrayEquals(STANDARD, KeyboardLayout.koreanLetterRows(p));
        assertArrayEquals(new float[]{0f, 0.5f, 1.5f}, KeyboardLayout.koreanRowOffsets(p), 0f);
        assertTrue(KeyboardLayout.koreanIotizedKeys(p));
        assertFalse(KeyboardLayout.koreanAspirateSwipe(p));   // NewSwipe 단모음을 골라 두었어도 ㅋ·ㅌ·ㅊ·ㅍ 키가 따로 있다
        assertEquals(Prefs.KOREAN_LAYOUT_DUBEOLSIK, p.koreanLayoutId());
        assertFalse(p.doubleTapVowelActive());
    }

    /** 실제 자판에서 글자 키들의 가운데 위치가 koreanRowOffsets와 맞는지 (자동 수정의 이웃 키가 화면과 같은지). */
    @Test
    public void rowOffsetsMatchTheDrawnKeys() throws Exception {
        for (boolean grid : new boolean[]{false, true}) {
            FakeSp sp = dubeolsik(Prefs.KOREAN_LAYOUT_8, false);
            sp.m.put(Prefs.GRID_LAYOUT, grid);
            Prefs p = view(sp, Prefs.PROFILE_PORTRAIT);
            KeyboardLayout l = KeyboardLayout.korean(p);
            float[] offsets = KeyboardLayout.koreanRowOffsets(p);
            for (int r = 0; r < 3; r++) {
                KeyboardLayout.Row row = l.rows[r];
                float sum = 0f;
                for (Key k : row.keys) sum += k.weight;
                float x = (l.columns - sum) / 2f;   // KeyboardView처럼 짧은 줄은 가운데에 놓는다
                int c = 0;
                for (Key k : row.keys) {
                    if (k.type == Key.CHAR) {
                        assertEquals("grid=" + grid + " " + k.label, offsets[r] + c + 0.5f, x + k.weight / 2f, 1e-3f);
                        c++;
                    }
                    x += k.weight;
                }
            }
        }
    }
}
