package com.alternative_studios.newswipe.keyboard;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.content.SharedPreferences;

import com.alternative_studios.newswipe.Prefs;
import com.alternative_studios.newswipe.hangul.HangulComposer;

import org.junit.Test;

import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 한글 배열 '두벌식': 표준 두벌식 자판, 자·모음 균형 배열, 분리 키보드·한 손 모드·격자 정렬과 함께 쓸 때의 모양,
 * 예전 실험실 '두벌식 자판' 스위치를 옮기는 것.
 */
public class DubeolsikLayoutTest {
    private static final class FakeSp implements SharedPreferences {
        final Map<String, Object> m = new HashMap<>();
        @Override public Map<String, ?> getAll() { return new HashMap<>(m); }
        @Override public String getString(String k, String d) { Object o = m.get(k); return o == null ? d : (String) o; }
        @Override public Set<String> getStringSet(String k, Set<String> d) { return d; }
        @Override public int getInt(String k, int d) { Object o = m.get(k); return o == null ? d : (Integer) o; }
        @Override public long getLong(String k, long d) { return d; }
        @Override public float getFloat(String k, float d) { return d; }
        @Override public boolean getBoolean(String k, boolean d) { Object o = m.get(k); return o == null ? d : (Boolean) o; }
        @Override public boolean contains(String k) { return m.containsKey(k); }
        @Override public void registerOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener l) { }
        @Override public void unregisterOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener l) { }

        @Override public Editor edit() {
            return new Editor() {
                final Map<String, Object> put = new HashMap<>();
                final List<String> drop = new ArrayList<>();
                @Override public Editor putString(String k, String v) { put.put(k, v); return this; }
                @Override public Editor putStringSet(String k, Set<String> v) { return this; }
                @Override public Editor putInt(String k, int v) { put.put(k, v); return this; }
                @Override public Editor putLong(String k, long v) { return this; }
                @Override public Editor putFloat(String k, float v) { return this; }
                @Override public Editor putBoolean(String k, boolean v) { put.put(k, v); return this; }
                @Override public Editor remove(String k) { drop.add(k); return this; }
                @Override public Editor clear() { return this; }
                @Override public boolean commit() { apply(); return true; }
                @Override public void apply() {
                    for (String k : drop) m.remove(k);
                    m.putAll(put);
                }
            };
        }
    }

    private static Prefs view(FakeSp sp, String profile) throws Exception {
        Constructor<Prefs> c = Prefs.class.getDeclaredConstructor(SharedPreferences.class, boolean.class, String.class,
                boolean.class);
        c.setAccessible(true);
        return c.newInstance(sp, false, profile, false);
    }

    private static Prefs portrait(FakeSp sp) throws Exception {
        return view(sp, Prefs.PROFILE_PORTRAIT);
    }

    private static final String[][] STANDARD = {
            {"ㅂ", "ㅈ", "ㄷ", "ㄱ", "ㅅ", "ㅛ", "ㅕ", "ㅑ", "ㅐ", "ㅔ"},
            {"ㅁ", "ㄴ", "ㅇ", "ㄹ", "ㅎ", "ㅗ", "ㅓ", "ㅏ", "ㅣ"},
            {"ㅋ", "ㅌ", "ㅊ", "ㅍ", "ㅠ", "ㅜ", "ㅡ"},
    };

    /** 한글 배열을 두벌식으로 고른 설정. balanced는 두벌식의 자·모음 균형 배열. */
    private static FakeSp dubeolsik(boolean balanced) {
        FakeSp sp = new FakeSp();
        sp.m.put(Prefs.KOREAN_LAYOUT, Prefs.KOREAN_LAYOUT_DUBEOLSIK);
        sp.m.put(Prefs.BALANCED_LAYOUT, balanced);   // 균형 배열 값은 모든 한글 배열이 함께 쓴다
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

    /** 줄에서 key의 왼쪽 끝 위치 (칸 단위). 줄이 10칸보다 짧으면 KeyboardView처럼 가운데에 놓는다. */
    private static float leftOf(KeyboardLayout.Row row, Key key, int columns) {
        float sum = 0f;
        for (Key k : row.keys) sum += k.weight;
        float x = (columns - sum) / 2f;
        for (Key k : row.keys) {
            if (k == key) return x;
            x += k.weight;
        }
        throw new AssertionError("no key");
    }

    private static float total(KeyboardLayout.Row row) {
        float sum = 0f;
        for (Key k : row.keys) sum += k.weight;
        return sum;
    }

    @Test
    public void standardRows() throws Exception {
        for (boolean numberRow : new boolean[]{false, true}) {
            for (boolean grid : new boolean[]{false, true}) {
                for (boolean shiftHidden : new boolean[]{false, true}) {
                    FakeSp sp = dubeolsik(false);
                    sp.m.put(Prefs.NUMBER_ROW, numberRow);
                    sp.m.put(Prefs.GRID_LAYOUT, grid);
                    sp.m.put(Prefs.KOREAN_SHIFT_HIDDEN, shiftHidden);
                    sp.m.put(Prefs.KOREAN_SHIFT_FN, true);
                    Prefs p = portrait(sp);
                    KeyboardLayout l = KeyboardLayout.korean(p);
                    String what = "numberRow=" + numberRow + " grid=" + grid + " shiftHidden=" + shiftHidden;
                    assertEquals(what, KeyboardLayout.KOREAN, l.kind);
                    assertEquals(what, 10, l.columns);
                    int first = numberRow ? 1 : 0;
                    assertEquals(what, first + 4, l.rows.length);
                    for (int r = 0; r < 3; r++) assertArrayEquals(what, STANDARD[r], labels(l.rows[first + r]));
                    // 셋째 줄은 Shift + 글자 + ⌫ (다른 배열의 '한글 Shift 키 없애기'와는 따로다: 두벌식은 기본으로 보인다)
                    KeyboardLayout.Row third = l.rows[first + 2];
                    assertEquals(what, Key.SHIFT, third.keys[0].type);
                    assertEquals(what, Key.DELETE, third.keys[third.keys.length - 1].type);
                    assertEquals(what, 1, count(l, Key.SHIFT));
                    assertEquals(what, 0, count(l, Key.FUNCTION));
                    assertTrue(what, p.koreanDubeolsik());
                    assertFalse(what, p.koreanNewSwipe());
                    assertFalse(what, p.koreanD7());
                    assertFalse(what, p.balancedLayout());
                    assertEquals(what, 10, KeyboardLayout.koreanColumns(p));
                    assertEquals(what, Prefs.KOREAN_LAYOUT_DUBEOLSIK, p.koreanLayoutId());
                }
            }
        }
    }

    @Test
    public void shiftFollowsTheStandard() throws Exception {
        for (boolean balanced : new boolean[]{false, true}) {
            KeyboardLayout l = KeyboardLayout.korean(portrait(dubeolsik(balanced)));
            String[][] expect = {{"ㅂ", "ㅃ"}, {"ㅈ", "ㅉ"}, {"ㄷ", "ㄸ"}, {"ㄱ", "ㄲ"}, {"ㅅ", "ㅆ"}, {"ㅐ", "ㅒ"}, {"ㅔ", "ㅖ"},
                    // 단모음 자판과 달리 ㅗ·ㅏ 등은 Shift를 눌러도 같다 (ㅛ·ㅑ 키가 따로 있다)
                    {"ㅗ", "ㅗ"}, {"ㅏ", "ㅏ"}, {"ㅓ", "ㅓ"}, {"ㅜ", "ㅜ"}, {"ㅛ", "ㅛ"}, {"ㅁ", "ㅁ"}, {"ㅋ", "ㅋ"}};
            for (String[] e : expect) assertEquals(balanced + " " + e[0], e[1], key(l, e[0]).shifted);
        }
    }

    @Test
    public void swipesKeepWorking() throws Exception {
        // 기본 설정: 밀어서 쌍자음·ㅣ계 이중모음·겹받침. ㄱ 오른쪽은 ㅋ이 아니라 겹받침이다 (ㅋ 키가 따로 있다).
        for (boolean balanced : new boolean[]{false, true}) {
            KeyboardLayout l = KeyboardLayout.korean(portrait(dubeolsik(balanced)));
            assertEquals("ㄲ", key(l, "ㄱ").swipeDown);
            assertEquals("ㅑ", key(l, "ㅏ").swipeDown);
            assertNull(key(l, "ㅑ").swipeDown);   // ㅣ계 이중모음 키는 더 밀 것이 없다
            assertEquals(FinalSwipes.get(null, 'ㄹ', FinalSwipes.UP), key(l, "ㄹ").swipeUp);
            assertEquals("ㄳ", key(l, "ㄱ").swipeRight);
            assertTrue(!"ㅊ".equals(key(l, "ㅈ").swipeRight));
        }
    }

    @Test
    public void popupsHaveDefaultsForEveryKey() throws Exception {
        for (boolean balanced : new boolean[]{false, true}) {
            Prefs p = portrait(dubeolsik(balanced));
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
    }

    private static Key firstKey(KeyboardLayout.Row row) {
        for (Key k : row.keys) if (k.type != Key.GAP && k.type != Key.SPACER) return k;
        throw new AssertionError("no key");
    }

    /**
     * 자·모음 균형 배열: 일반 두벌식 자판을 자음과 모음 사이에서 나누기만 한다. 각 쪽 안에서 키의 상대적인 자리와 폭
     * (ㅁ·ㅗ 줄의 반 칸 들여쓰기, 1.5칸 Shift·⌫)은 일반 자판과 같다.
     */
    @Test
    public void balancedLayoutOnlySplitsTheStandardRows() throws Exception {
        for (boolean numberRow : new boolean[]{false, true}) {
            for (boolean grid : new boolean[]{false, true}) {
                for (int width : new int[]{90, 100}) {
                    FakeSp sp = dubeolsik(true);
                    sp.m.put(Prefs.NUMBER_ROW, numberRow);
                    sp.m.put(Prefs.GRID_LAYOUT, grid);
                    sp.m.put(Prefs.KOREAN_SHIFT_HIDDEN, true);   // 다른 배열의 Shift 설정은 두벌식에 영향이 없다
                    Prefs p0 = portrait(sp);
                    if (width != 90) {
                        sp.m.put(p0.balancedConsonantWidthKey(), width);
                        sp.m.put(p0.balancedVowelWidthKey(), width);
                    }
                    Prefs p = portrait(sp);
                    assertTrue(p.balancedLayout());
                    KeyboardLayout l = KeyboardLayout.korean(p);
                    String what = "numberRow=" + numberRow + " grid=" + grid + " width=" + width;
                    assertEquals(what, 10, l.columns);
                    int first = numberRow ? 1 : 0;
                    assertEquals(what, first + 4, l.rows.length);
                    // 자음 쪽은 5.5칸, 모음 쪽은 5칸을 각 절반의 width%에 맞춘 단위
                    float cu = 5f / 5.5f * width / 100f, vu = 5f / 5f * width / 100f;
                    for (int r = 0; r < 3; r++) {
                        KeyboardLayout.Row row = l.rows[first + r];
                        assertArrayEquals(what, STANDARD[r], labels(row));
                        assertEquals(what, 10f, total(row), 1e-3f);
                        for (Key k : letters(row)) {
                            boolean vowel = HangulComposer.isVowel(k.label.charAt(0));
                            float x = leftOf(row, k, 10);
                            if (vowel) assertTrue(what + " " + k.label, x >= 5f - 1e-3f);
                            else assertTrue(what + " " + k.label, x + k.weight <= 5f + 1e-3f);
                            assertEquals(what + " " + k.label, vowel ? vu : cu, k.weight, 1e-3f);
                        }
                    }
                    KeyboardLayout.Row r0 = l.rows[first], r1 = l.rows[first + 1], r2 = l.rows[first + 2];
                    // 자음 쪽: ㅁ은 ㅂ보다 반 칸, ㅋ은 1.5칸 Shift 다음
                    assertEquals(what, 0.5f * cu, leftOf(r1, key(l, "ㅁ"), 10) - leftOf(r0, key(l, "ㅂ"), 10), 1e-3f);
                    Key shift = firstKey(r2);
                    assertEquals(what, Key.SHIFT, shift.type);
                    assertEquals(what, 1.5f * cu, shift.weight, 1e-3f);
                    assertEquals(what, leftOf(r0, key(l, "ㅂ"), 10), leftOf(r2, shift, 10), 1e-3f);
                    assertEquals(what, 1.5f * cu, leftOf(r2, key(l, "ㅋ"), 10) - leftOf(r2, shift, 10), 1e-3f);
                    // 모음 쪽: ㅗ·ㅠ는 ㅛ보다 반 칸, ⌫는 1.5칸 (일반 자판과 같다)
                    float yo = leftOf(r0, key(l, "ㅛ"), 10);
                    assertEquals(what, 0.5f * vu, leftOf(r1, key(l, "ㅗ"), 10) - yo, 1e-3f);
                    assertEquals(what, 0.5f * vu, leftOf(r2, key(l, "ㅠ"), 10) - yo, 1e-3f);
                    Key delete = r2.keys[r2.keys.length - 1].type == Key.DELETE ? r2.keys[r2.keys.length - 1]
                            : r2.keys[r2.keys.length - 2];
                    assertEquals(what, Key.DELETE, delete.type);
                    assertEquals(what, 1.5f * vu, delete.weight, 1e-3f);
                    // ⌫의 오른쪽 끝은 ㅔ의 오른쪽 끝과 같다
                    Key e = key(l, "ㅔ");
                    assertEquals(what, leftOf(r0, e, 10) + e.weight, leftOf(r2, delete, 10) + delete.weight, 1e-3f);
                    assertEquals(what, 1, count(l, Key.SHIFT));
                }
            }
        }
    }

    /** 균형 배열의 키 폭·위치 설정을 두벌식도 따르고, 키 폭은 줄마다 같다 (늘 가운데에서 나눈다). */
    @Test
    public void balancedWidthsApplyToEveryRow() throws Exception {
        FakeSp sp = dubeolsik(true);
        Prefs p0 = portrait(sp);
        sp.m.put(p0.balancedVowelWidthKey(), 80);
        sp.m.put(p0.balancedVowelPosKey(), 0);
        sp.m.put(p0.balancedConsonantWidthKey(), 90);
        sp.m.put(p0.balancedBoundaryKey(), 60);   // 예전 '자·모음 경계 위치' 값은 쓰지 않는다
        assertTrue(p0.balancedVowelWidthKey().endsWith("_" + Prefs.KOREAN_LAYOUT_DUBEOLSIK));
        KeyboardLayout l = KeyboardLayout.korean(portrait(sp));
        float consonant = 5f / 5.5f * 0.9f, vowel = 5f / 5f * 0.8f;
        for (int r = 0; r < 3; r++) {
            KeyboardLayout.Row row = l.rows[r];
            assertEquals(10f, total(row), 1e-3f);
            for (Key k : letters(row)) {
                boolean v = HangulComposer.isVowel(k.label.charAt(0));
                assertEquals(k.label, v ? vowel : consonant, k.weight, 1e-3f);
                if (v) assertTrue(k.label, leftOf(row, k, 10) >= 5f - 1e-3f);   // 가운데 오른쪽
                else assertTrue(k.label, leftOf(row, k, 10) + k.weight <= 5f + 1e-3f);
            }
        }
        // 모음 위치 0: 모음 묶음이 가운데에 붙는다 (ㅛ가 가운데에서 시작)
        assertEquals(5f, leftOf(l.rows[0], key(l, "ㅛ"), 10), 1e-3f);
    }

    /**
     * 세로 모드 균형 배열의 기본값: 두벌식은 키 폭 90%, 자음은 왼쪽 끝·모음은 오른쪽 끝.
     * 단모음 배열은 그대로 키 폭 100%·가운데.
     */
    @Test
    public void balancedDefaults() throws Exception {
        Prefs d = portrait(dubeolsik(true));
        assertEquals(90, d.balancedConsonantWidth());
        assertEquals(0, d.balancedConsonantPos());
        assertEquals(90, d.balancedVowelWidth());
        assertEquals(100, d.balancedVowelPos());
        // 자음은 왼쪽 끝에서, 모음은 오른쪽 끝까지
        KeyboardLayout l = KeyboardLayout.korean(d);
        assertEquals(0f, leftOf(l.rows[0], key(l, "ㅂ"), 10), 1e-3f);
        Key e = key(l, "ㅔ");
        assertEquals(10f, leftOf(l.rows[0], e, 10) + e.weight, 1e-3f);
        for (String layout : new String[]{Prefs.KOREAN_LAYOUT_8, Prefs.KOREAN_LAYOUT_D7}) {
            FakeSp sp = new FakeSp();
            sp.m.put(Prefs.KOREAN_LAYOUT, layout);
            Prefs p = portrait(sp);
            assertEquals(layout, 100, p.balancedConsonantWidth());
            assertEquals(layout, 50, p.balancedConsonantPos());
            assertEquals(layout, 100, p.balancedVowelWidth());
            assertEquals(layout, 50, p.balancedVowelPos());
        }
        // 분리 키보드(가로 모드)의 기본값은 배열과 상관없이 그대로
        FakeSp land = dubeolsik(true);
        Prefs lp = view(land, Prefs.PROFILE_LANDSCAPE).profileEditView(Prefs.PROFILE_LANDSCAPE);
        assertEquals(Prefs.SPLIT_WIDTH_DEFAULT, lp.balancedConsonantWidth());
        assertEquals(Prefs.SPLIT_LEFT_POS_DEFAULT, lp.balancedConsonantPos());
        assertEquals(Prefs.SPLIT_RIGHT_POS_DEFAULT, lp.balancedVowelPos());
    }

    /** 격자 정렬: 균형 배열에서도 맨 아래 줄 기능키가 반듯한 칸 폭이다. */
    @Test
    public void gridKeepsTheBottomRowOnTheGrid() throws Exception {
        FakeSp sp = dubeolsik(true);
        sp.m.put(Prefs.GRID_LAYOUT, true);
        KeyboardLayout l = KeyboardLayout.korean(portrait(sp));
        KeyboardLayout.Row bottom = l.rows[l.rows.length - 1];
        assertEquals(10f, total(bottom), 1e-3f);
        // 기능키 한 칸 = 자음 쪽을 꽉 채웠을 때의 자음 키 한 칸 (자음 쪽은 5.5칸)
        assertEquals(5f / 5.5f, bottom.keys[0].weight, 1e-3f);
        sp.m.put(portrait(sp).balancedConsonantWidthKey(), 100);
        KeyboardLayout full = KeyboardLayout.korean(portrait(sp));
        assertEquals(key(full, "ㅂ").weight, full.rows[full.rows.length - 1].keys[0].weight, 1e-3f);
    }

    @Test
    public void balancedIsSharedByAllLayouts() throws Exception {
        FakeSp sp = dubeolsik(true);
        Prefs p = portrait(sp);
        assertTrue(p.balancedLayout());
        p.setKoreanLayout(Prefs.KOREAN_LAYOUT_8);
        assertTrue(portrait(sp).balancedLayout());   // 모든 한글 배열이 같은 값을 쓴다
        p.setKoreanLayout(Prefs.KOREAN_LAYOUT_DUBEOLSIK);
        assertTrue(portrait(sp).balancedLayout());
        // 함께 쓰기 전에 배열마다 저장하던 값은 새 값을 정하기 전까지 그 배열이 이어 쓴다
        FakeSp legacy = new FakeSp();
        legacy.m.put(Prefs.KOREAN_LAYOUT, Prefs.KOREAN_LAYOUT_8);
        legacy.m.put(Prefs.BALANCED_LAYOUT + "_" + Prefs.KOREAN_LAYOUT_8, true);
        assertTrue(portrait(legacy).balancedLayout());
        legacy.m.put(Prefs.BALANCED_LAYOUT, false);
        assertFalse(portrait(legacy).balancedLayout());
    }

    @Test
    public void splitKeyboardPutsConsonantsLeftAndVowelsRight() throws Exception {
        for (String profile : new String[]{Prefs.PROFILE_LANDSCAPE, Prefs.PROFILE_LARGE_PORTRAIT, Prefs.PROFILE_LARGE_LANDSCAPE}) {
            for (boolean balanced : new boolean[]{false, true}) {
                for (boolean reach : new boolean[]{false, true}) {
                    for (boolean grid : new boolean[]{false, true}) {
                        FakeSp sp = dubeolsik(balanced);
                        Prefs p = view(sp, profile);
                        sp.m.put(p.splitKey(), true);
                        sp.m.put(p.splitReachColumnKey(), reach);   // 좌·우 균형 배열은 두벌식에 쓰이지 않는다
                        sp.m.put(Prefs.GRID_LAYOUT, grid);
                        p = view(sp, profile);
                        String what = profile + " balanced=" + balanced + " reach=" + reach + " grid=" + grid;
                        assertTrue(what, p.splitView());
                        assertFalse(what, p.balancedLayout());   // 분리 키보드는 영어 자판처럼 가운데에서 나눈다
                        KeyboardLayout l = KeyboardLayout.korean(p);
                        assertEquals(what, KeyboardLayout.KOREAN, l.kind);
                        for (int r = 0; r < 3; r++) {
                            assertArrayEquals(what, STANDARD[r], labels(l.rows[r]));
                            // 가운데(5칸)보다 왼쪽은 자음, 오른쪽은 모음
                            float x = 0f;
                            for (Key k : l.rows[r].keys) {
                                if (k.type == Key.CHAR && !k.hidden) {
                                    boolean vowel = HangulComposer.isVowel(k.label.charAt(0));
                                    assertEquals(what + " " + k.label, vowel, x + k.weight / 2f > 5f);
                                }
                                x += k.weight;
                            }
                            assertEquals(what, 10f, x, 1e-3f);
                        }
                        assertEquals(what, 1, count(l, Key.SHIFT));
                        assertEquals(what, 1, count(l, Key.DELETE));
                    }
                }
            }
        }
    }

    @Test
    public void oneHandModeKeepsDubeolsik() throws Exception {
        for (boolean balanced : new boolean[]{false, true}) {
            FakeSp sp = dubeolsik(balanced);
            Prefs p = portrait(sp).oneHandView();
            assertFalse(p.balancedLayout());   // 한 손 모드는 균형 배열 없이 한쪽으로 모은다
            KeyboardLayout l = KeyboardLayout.korean(p);
            assertEquals(10, l.columns);
            for (int r = 0; r < 3; r++) assertArrayEquals(STANDARD[r], labels(l.rows[r]));
            assertEquals(1, count(l, Key.SHIFT));
            for (KeyboardLayout.Row row : l.rows) for (Key k : row.keys) assertNotEquals(Key.GAP, k.type);
        }
    }

    private static void assertNotEquals(int unexpected, int actual) {
        assertTrue(unexpected != actual);
    }

    @Test
    public void bottomRowPreviewShowsTheShiftRow() throws Exception {
        for (boolean balanced : new boolean[]{false, true}) {
            KeyboardLayout l = KeyboardLayout.bottomRowPreview(portrait(dubeolsik(balanced)));
            assertEquals(2, l.rows.length);
            assertArrayEquals(STANDARD[2], labels(l.rows[0]));
            assertEquals(Key.SHIFT, firstKey(l.rows[0]).type);
        }
    }

    /** 두벌식도 Shift 키를 없앨 수 있다 (자기 설정, 기본은 보임). 없앤 자리는 빈 칸이거나 Fn 키이고 배치는 그대로다. */
    @Test
    public void shiftCanBeRemoved() throws Exception {
        for (boolean balanced : new boolean[]{false, true}) {
            for (boolean grid : new boolean[]{false, true}) {
                for (boolean fnKey : new boolean[]{false, true}) {
                    FakeSp sp = dubeolsik(balanced);
                    sp.m.put(Prefs.GRID_LAYOUT, grid);
                    Prefs shown = portrait(sp);
                    assertFalse(shown.koreanShiftHidden());
                    KeyboardLayout with = KeyboardLayout.korean(shown);
                    sp.m.put(Prefs.DUBEOLSIK_SHIFT_HIDDEN, true);
                    sp.m.put(Prefs.KOREAN_SHIFT_FN, fnKey);
                    Prefs p = portrait(sp);
                    assertTrue(p.koreanShiftHidden());
                    KeyboardLayout l = KeyboardLayout.korean(p);
                    String what = "balanced=" + balanced + " grid=" + grid + " fn=" + fnKey;
                    assertEquals(what, 0, count(l, Key.SHIFT));
                    assertEquals(what, fnKey ? 1 : 0, count(l, Key.FUNCTION));
                    KeyboardLayout.Row row = l.rows[2], rowWith = with.rows[2];
                    assertArrayEquals(what, STANDARD[2], labels(row));
                    // 그 자리는 그대로 남아 글자 키의 자리가 바뀌지 않는다
                    for (Key k : letters(row)) {
                        assertEquals(what + " " + k.label, leftOf(rowWith, key(with, k.label), 10), leftOf(row, k, 10), 1e-3f);
                    }
                    // Shift 자리의 키는 Fn 키이거나, 없으면 빈 칸이라 그 줄의 첫 키가 글자다
                    assertEquals(what, fnKey ? Key.FUNCTION : Key.CHAR, firstKey(row).type);
                    assertEquals(what, Key.DELETE, row.keys[row.keys.length - 1].type == Key.GAP
                            ? row.keys[row.keys.length - 2].type : row.keys[row.keys.length - 1].type);
                }
            }
        }
        // 분리 키보드도 같다
        FakeSp sp = dubeolsik(false);
        Prefs lp = view(sp, Prefs.PROFILE_LANDSCAPE);
        sp.m.put(lp.splitKey(), true);
        sp.m.put(Prefs.DUBEOLSIK_SHIFT_HIDDEN, true);
        KeyboardLayout split = KeyboardLayout.korean(view(sp, Prefs.PROFILE_LANDSCAPE));
        assertEquals(0, count(split, Key.SHIFT));
        assertEquals(1, count(split, Key.DELETE));
        // 다른 배열의 '한글 Shift 키 없애기' 설정은 두벌식에 영향이 없다
        FakeSp other = dubeolsik(false);
        other.m.put(Prefs.KOREAN_SHIFT_HIDDEN, true);
        assertEquals(1, count(KeyboardLayout.korean(portrait(other)), Key.SHIFT));
        // 두벌식의 설정도 다른 배열에 영향이 없다
        FakeSp d8 = new FakeSp();
        d8.m.put(Prefs.DUBEOLSIK_SHIFT_HIDDEN, false);
        assertTrue(portrait(d8).koreanShiftHidden());   // 단모음의 기본은 없앰
    }

    /** 영어 자판: 글자 키를 아래로 밀면 대문자 (켜면 키마다 정한 값은 쓰지 않는다). */
    @Test
    public void englishSwipeDownTypesUpperCase() throws Exception {
        FakeSp sp = new FakeSp();
        KeyboardLayout off = KeyboardLayout.english(portrait(sp));
        assertNull(key(off, "q").swipeDown);
        sp.m.put(Prefs.ENGLISH_SWIPE_UPPER, true);
        sp.m.put("swipe_custom_en_q_down", "x");   // 키마다 정한 값이 있어도 대문자로 고정
        Prefs p = portrait(sp);
        KeyboardLayout on = KeyboardLayout.english(p);
        for (String row : new String[]{"qwertyuiop", "asdfghjkl", "zxcvbnm"}) {
            for (char c : row.toCharArray()) {
                assertEquals(String.valueOf(c), String.valueOf(Character.toUpperCase(c)), key(on, String.valueOf(c)).swipeDown);
            }
        }
        // 한글 자판과 기호 키는 영향이 없다
        KeyboardLayout ko = KeyboardLayout.korean(p);
        assertEquals("ㄲ", key(ko, "ㄱ").swipeDown);
        assertNull(key(on, ".") == null ? null : key(on, ".").swipeDown);
        assertTrue(SwipeCustom.englishUpperSwipe(p, false, "q", Key.SWIPE_DOWN));
        assertFalse(SwipeCustom.englishUpperSwipe(p, false, "q", Key.SWIPE_UP));
        assertFalse(SwipeCustom.englishUpperSwipe(p, true, "ㄱ", Key.SWIPE_DOWN));
        assertFalse(SwipeCustom.englishUpperSwipe(p, false, "1", Key.SWIPE_DOWN));
        assertFalse(SwipeCustom.englishUpperSwipe(portrait(new FakeSp()), false, "q", Key.SWIPE_DOWN));
    }

    private static String[] qwertyLabels(KeyboardLayout.Row row) {
        return labels(row);
    }

    /** 두벌식 균형 배열을 켜고 '영어 자판도 균형 배열'을 켜면 영어 자판도 같은 모양으로 나뉜다. */
    @Test
    public void englishFollowsTheBalancedLayout() throws Exception {
        String[][] english = {{"q", "w", "e", "r", "t", "y", "u", "i", "o", "p"}, {"a", "s", "d", "f", "g", "h", "j", "k", "l"},
                {"z", "x", "c", "v", "b", "n", "m"}};
        for (boolean numberRow : new boolean[]{false, true}) {
            FakeSp sp = dubeolsik(true);
            sp.m.put(Prefs.NUMBER_ROW, numberRow);
            // 옵션을 켜지 않으면 일반 영어 자판
            assertEquals(10, KeyboardLayout.english(portrait(sp)).columns);
            assertEquals(1f, key(KeyboardLayout.english(portrait(sp)), "a").weight, 1e-3f);
            sp.m.put(Prefs.ENGLISH_BALANCED, true);
            Prefs p = portrait(sp);
            assertTrue(p.englishBalanced());
            KeyboardLayout en = KeyboardLayout.english(p), ko = KeyboardLayout.korean(p);
            int first = numberRow ? 1 : 0;
            assertEquals(ko.rows.length, en.rows.length);
            for (int r = 0; r < 3; r++) {
                KeyboardLayout.Row row = en.rows[first + r], kr = ko.rows[first + r];
                assertArrayEquals(english[r], qwertyLabels(row));
                assertEquals(10f, total(row), 1e-3f);
                // 한글 두벌식 줄과 키 하나하나의 자리·폭이 같다
                java.util.List<Key> e = letters(row), k = letters(kr);
                assertEquals(e.size(), k.size());
                for (int i = 0; i < e.size(); i++) {
                    assertEquals(r + ":" + i, leftOf(kr, k.get(i), 10), leftOf(row, e.get(i), 10), 1e-3f);
                    assertEquals(r + ":" + i, k.get(i).weight, e.get(i).weight, 1e-3f);
                }
            }
            KeyboardLayout.Row third = en.rows[first + 2];
            assertEquals(Key.SHIFT, firstKey(third).type);   // 영어는 늘 Shift (한글 두벌식에서 Shift를 없애도)
            // 두벌식의 Shift를 없애도 영어 Shift는 그대로
            sp.m.put(Prefs.DUBEOLSIK_SHIFT_HIDDEN, true);
            assertEquals(1, count(KeyboardLayout.english(portrait(sp)), Key.SHIFT));
            sp.m.remove(Prefs.DUBEOLSIK_SHIFT_HIDDEN);
            // 키 폭 설정도 함께 따른다
            sp.m.put(p.balancedConsonantWidthKey(), 80);
            KeyboardLayout moved = KeyboardLayout.english(portrait(sp));
            assertEquals(KeyboardLayout.korean(portrait(sp)).rows[first].keys.length, moved.rows[first].keys.length);
        }
        // 켜지지 않는 경우: 균형 배열이 꺼짐, 두벌식이 아님, 한 손 모드, 가로 모드 분리 키보드
        FakeSp off = dubeolsik(false);
        off.m.put(Prefs.ENGLISH_BALANCED, true);
        assertFalse(portrait(off).englishBalanced());
        assertEquals(1f, key(KeyboardLayout.english(portrait(off)), "a").weight, 1e-3f);
        FakeSp d8 = new FakeSp();
        d8.m.put(Prefs.ENGLISH_BALANCED, true);
        d8.m.put(Prefs.BALANCED_LAYOUT + "_" + Prefs.KOREAN_LAYOUT_8, true);
        assertFalse(portrait(d8).englishBalanced());
        FakeSp on = dubeolsik(true);
        on.m.put(Prefs.ENGLISH_BALANCED, true);
        assertFalse(portrait(on).oneHandView().englishBalanced());
        // 가로 모드 분리 키보드는 영어 자판도 늘 영어 자판처럼 가운데에서 나눈다
        Prefs land = view(on, Prefs.PROFILE_LANDSCAPE);
        on.m.put(land.splitKey(), true);
        assertFalse(view(on, Prefs.PROFILE_LANDSCAPE).englishBalanced());
        assertEquals(1, count(KeyboardLayout.english(view(on, Prefs.PROFILE_LANDSCAPE)), Key.SHIFT));
    }

    /** 쌍자음·이중모음 입력 방식: 아래로 밀기·연속 탭·둘 다. 연속 탭만이면 기본 배치의 아래로 밀기는 쓰지 않는다. */
    @Test
    public void doubleInputModes() throws Exception {
        for (String layout : new String[]{Prefs.KOREAN_LAYOUT_DUBEOLSIK, Prefs.KOREAN_LAYOUT_8, Prefs.KOREAN_LAYOUT_D7}) {
            FakeSp sp = new FakeSp();
            sp.m.put(Prefs.KOREAN_LAYOUT, layout);
            Prefs p = portrait(sp);
            // 기본: 쌍자음은 아래로 밀기, 이중모음은 둘 다
            assertEquals(Prefs.INPUT_SWIPE, p.consonantInputMode());
            assertEquals(Prefs.INPUT_BOTH, p.vowelInputMode());
            assertEquals("ㄲ", key(KeyboardLayout.korean(p), "ㄱ").swipeDown);
            assertEquals("ㅑ", key(KeyboardLayout.korean(p), "ㅏ").swipeDown);
            // 아래로 밀기를 쓰는 방식이면 기본 글자가 있는 키의 아래로 밀기는 고정이고, 키마다 정한 값이 있어도 쓰지 않는다
            assertTrue(SwipeCustom.hangulDownLocked(p, true, "ㄱ", Key.SWIPE_DOWN));
            assertTrue(SwipeCustom.hangulDownLocked(p, true, "ㅏ", Key.SWIPE_DOWN));
            assertFalse(SwipeCustom.hangulDownLocked(p, true, "ㅁ", Key.SWIPE_DOWN));   // 기본 글자가 없는 키는 바꿀 수 있다
            assertFalse(SwipeCustom.hangulDownLocked(p, true, "ㄱ", Key.SWIPE_UP));
            assertFalse(SwipeCustom.hangulDownLocked(p, false, "q", Key.SWIPE_DOWN));
            sp.m.put("swipe_custom_ko_ㄱ_down", "ㅋ");
            sp.m.put("swipe_custom_ko_ㅁ_down", "ㅂ");
            assertEquals("ㄲ", key(KeyboardLayout.korean(portrait(sp)), "ㄱ").swipeDown);
            assertEquals("ㅂ", key(KeyboardLayout.korean(portrait(sp)), "ㅁ").swipeDown);
            sp.m.remove("swipe_custom_ko_ㄱ_down");
            sp.m.remove("swipe_custom_ko_ㅁ_down");
            p.setConsonantInputMode(Prefs.INPUT_TAP);
            p.setVowelInputMode(Prefs.INPUT_TAP);
            assertFalse(SwipeCustom.hangulDownLocked(p, true, "ㄱ", Key.SWIPE_DOWN));   // 연속 탭만이면 풀린다
            assertTrue(p.doubleTapConsonant() && p.doubleTapVowel());
            KeyboardLayout l = KeyboardLayout.korean(portrait(sp));
            assertNull(layout, key(l, "ㄱ").swipeDown);
            assertNull(layout, key(l, "ㅏ").swipeDown);
            assertEquals(layout, FinalSwipes.get(null, 'ㄹ', FinalSwipes.UP), key(l, "ㄹ").swipeUp);   // 다른 방향은 그대로
            // 키마다 정한 아래로 밀기 글자는 그대로 쓴다
            sp.m.put("swipe_custom_ko_ㄱ_down", "ㅋ");
            assertEquals("ㅋ", key(KeyboardLayout.korean(portrait(sp)), "ㄱ").swipeDown);
            p.setConsonantInputMode(Prefs.INPUT_BOTH);
            p.setVowelInputMode(Prefs.INPUT_SWIPE);
            assertEquals(Prefs.INPUT_BOTH, p.consonantInputMode());
            assertEquals(Prefs.INPUT_SWIPE, p.vowelInputMode());
            assertTrue(p.doubleTapConsonant() && p.swipeDownConsonant());
            assertFalse(p.doubleTapVowel());
            assertEquals("ㅑ", key(KeyboardLayout.korean(portrait(sp)), "ㅏ").swipeDown);
        }
    }

    @Test
    public void otherLayoutsAreNotDubeolsik() throws Exception {
        FakeSp sp = new FakeSp();
        sp.m.put(Prefs.KOREAN_LAYOUT, Prefs.KOREAN_LAYOUT_D7);
        Prefs p = portrait(sp);
        assertFalse(p.koreanDubeolsik());
        assertTrue(p.koreanNewSwipe());
        assertEquals(7, KeyboardLayout.korean(p).columns);
        assertEquals(Prefs.KOREAN_LAYOUT_D7, p.koreanLayoutId());
        assertNull(KeyboardLayout.koreanRowOffsets(p));
        assertFalse(KeyboardLayout.koreanIotizedKeys(p));
        assertFalse(portrait(new FakeSp()).koreanDubeolsik());   // 기본은 8열 단모음
    }

    /** 예전 실험실 '두벌식 자판' 스위치: 켜 두었으면 두벌식으로 옮기고, 스위치는 지운다. */
    @Test
    public void labSwitchMovesToTheLayout() throws Exception {
        FakeSp on = new FakeSp();
        on.m.put(Prefs.KOREAN_LAYOUT, Prefs.KOREAN_LAYOUT_D7);
        on.m.put(Prefs.LAB_DUBEOLSIK, true);
        Prefs p = portrait(on);
        assertTrue(p.koreanDubeolsik());   // 옮기기 전(예전 백업을 막 가져왔을 때)에도 두벌식
        assertFalse(p.koreanNewSwipe());
        p.migrateLabDubeolsik();
        assertEquals(Prefs.KOREAN_LAYOUT_DUBEOLSIK, on.m.get(Prefs.KOREAN_LAYOUT));
        assertFalse(on.m.containsKey(Prefs.LAB_DUBEOLSIK));
        assertTrue(portrait(on).koreanDubeolsik());

        FakeSp off = new FakeSp();
        off.m.put(Prefs.KOREAN_LAYOUT, Prefs.KOREAN_LAYOUT_D7);
        off.m.put(Prefs.LAB_DUBEOLSIK, false);
        portrait(off).migrateLabDubeolsik();
        assertEquals(Prefs.KOREAN_LAYOUT_D7, off.m.get(Prefs.KOREAN_LAYOUT));
        assertFalse(off.m.containsKey(Prefs.LAB_DUBEOLSIK));

        // 옮기기 전에 다른 배열을 고르면 남은 스위치도 지워 고른 배열이 쓰인다
        FakeSp pick = new FakeSp();
        pick.m.put(Prefs.LAB_DUBEOLSIK, true);
        Prefs q = portrait(pick);
        q.setKoreanLayout(Prefs.KOREAN_LAYOUT_8);
        assertFalse(q.koreanDubeolsik());
        assertFalse(pick.m.containsKey(Prefs.LAB_DUBEOLSIK));
    }

    @Test
    public void autocorrectGetsTheDubeolsikGrid() throws Exception {
        Prefs p = portrait(dubeolsik(false));
        assertArrayEquals(STANDARD, KeyboardLayout.koreanLetterRows(p));
        assertArrayEquals(new float[]{0f, 0.5f, 1.5f}, KeyboardLayout.koreanRowOffsets(p), 0f);
        assertTrue(KeyboardLayout.koreanIotizedKeys(p));
        assertFalse(KeyboardLayout.koreanAspirateSwipe(p));   // ㅋ·ㅌ·ㅊ·ㅍ 키가 따로 있다
        // 균형 배열도 양쪽 안에서 두벌식의 엇갈림이 그대로다
        Prefs b = portrait(dubeolsik(true));
        assertArrayEquals(STANDARD, KeyboardLayout.koreanLetterRows(b));
        assertArrayEquals(new float[]{0f, 0.5f, 1.5f}, KeyboardLayout.koreanRowOffsets(b), 0f);
        assertTrue(KeyboardLayout.koreanIotizedKeys(b));
    }

    /** 실제 자판에서 글자 키들의 가운데 위치가 koreanRowOffsets와 맞는지 (자동 수정의 이웃 키가 화면과 같은지). */
    @Test
    public void rowOffsetsMatchTheDrawnKeys() throws Exception {
        for (boolean grid : new boolean[]{false, true}) {
            FakeSp sp = dubeolsik(false);
            sp.m.put(Prefs.GRID_LAYOUT, grid);
            Prefs p = portrait(sp);
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

    /** 두벌식에서도 자음 연속 탭(ㄱㄱ→ㄲ)과 모음 연속 탭(ㅗㅗ→ㅛ, ㅐㅐ→ㅒ)이 조합기에서 그대로 동작한다. */
    @Test
    public void doubleTapsWorkWithDubeolsikKeys() {
        HangulComposer c = new HangulComposer();
        c.setDoubleTapVowel(true);
        c.setDoubleTapConsonant(true, 300);
        c.input('ㄱ', true, 0);
        c.input('ㄱ', true, 100);
        c.input('ㅗ', true, 200);
        c.input('ㅗ', true, 300);
        assertEquals("꾜", c.getComposing());
        HangulComposer d = new HangulComposer();
        d.setDoubleTapVowel(true);
        d.input('ㅇ', true, 0);
        d.input('ㅐ', true, 100);
        d.input('ㅐ', true, 200);
        assertEquals("얘", d.getComposing());
        // ㅛ 키를 바로 눌러도 된다 (두벌식의 ㅣ계 이중모음 키)
        HangulComposer e = new HangulComposer();
        e.setDoubleTapVowel(true);
        e.input('ㄱ', true, 0);
        e.input('ㅛ', true, 100);
        assertEquals("교", e.getComposing());
    }
}
