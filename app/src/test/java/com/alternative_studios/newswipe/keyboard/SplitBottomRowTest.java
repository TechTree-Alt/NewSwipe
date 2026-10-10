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

    /** 줄 중 가장 바깥 키의 {왼쪽, 오른쪽} (맨 아래 줄과 숫자 줄은 빼고 글자·기호 줄만). */
    private static float[] outer(KeyboardLayout l, int firstRow) {
        float left = Float.MAX_VALUE, right = 0f;
        for (int r = firstRow; r < l.rows.length - 1; r++) {
            float[] e = extent(l.rows[r]);
            left = Math.min(left, e[0]);
            right = Math.max(right, e[1]);
        }
        return new float[]{left, right};
    }

    private static final int[][] SETTINGS = {{100, 0, 60, 60}, {50, 50, 70, 80}, {100, 100, 50, 40}, {0, 100, 100, 100},
            {50, 50, 100, 100}};

    private static Prefs prefsFor(int[] s, boolean numberRow) throws Exception {
        FakeSp sp = new FakeSp();
        Prefs p = landscapeEditView(sp);
        sp.m.put(Prefs.NUMBER_ROW, numberRow);
        sp.m.put(p.balancedConsonantPosKey(), s[0]);
        sp.m.put(p.balancedVowelPosKey(), s[1]);
        sp.m.put(p.balancedConsonantWidthKey(), s[2]);
        sp.m.put(p.balancedVowelWidthKey(), s[3]);
        return p;
    }

    @Test
    public void bottomRowAndNumberRowShareTheSameEdgesOnEveryPage() throws Exception {
        for (int[] s : SETTINGS) {
            Prefs p = prefsFor(s, true);
            KeyboardLayout korean = KeyboardLayout.korean(p), english = KeyboardLayout.english(p);
            KeyboardLayout sym1 = KeyboardLayout.symbols(true, p), sym2 = KeyboardLayout.symbols2(false, p);
            float[] want = extent(korean.rows[korean.rows.length - 1]);
            for (KeyboardLayout l : new KeyboardLayout[]{english, sym1, sym2}) {
                float[] bottom = extent(l.rows[l.rows.length - 1]);
                assertEquals("맨 아래 줄 왼쪽 끝", want[0], bottom[0], 0.001f);
                assertEquals("맨 아래 줄 오른쪽 끝", want[1], bottom[1], 0.001f);
            }
            for (KeyboardLayout l : new KeyboardLayout[]{korean, english}) {
                float[] number = extent(l.rows[0]);   // 숫자 줄
                assertEquals(0.78f, l.rows[0].height, 0.001f);
                assertEquals("숫자 줄 왼쪽 끝", want[0], number[0], 0.001f);
                assertEquals("숫자 줄 오른쪽 끝", want[1], number[1], 0.001f);
            }
            // 어느 자판의 글자 줄도 이 양 끝 밖으로 나가지 않는다.
            for (KeyboardLayout l : new KeyboardLayout[]{korean, english}) {
                float[] letters = outer(l, 1);
                assertTrue(letters[0] >= want[0] - 0.001f);
                assertTrue(letters[1] <= want[1] + 0.001f);
            }
        }
    }

    /** 줄에서 x칸(가운데 5칸 등)보다 왼쪽에 있는 키와 오른쪽에 있는 키의 {왼쪽 끝, 오른쪽 끝, 시작, 끝}. 가로지르면 실패. */
    private static float[] halves(KeyboardLayout.Row row) {
        float x = 0f, ll = -1f, lr = 0f, rl = -1f, rr = 0f;
        for (Key k : row.keys) {
            if (k.type != Key.GAP && !middleGap(k, x) && !k.hidden) {
                if (x + k.weight <= 5.001f) {
                    if (ll < 0f) ll = x;
                    lr = x + k.weight;
                } else if (x >= 4.999f) {
                    if (rl < 0f) rl = x;
                    rr = x + k.weight;
                } else {
                    throw new AssertionError("키가 가운데를 가로지릅니다");
                }
            }
            x += k.weight;
        }
        return new float[]{ll, lr, rl, rr};
    }

    /** 두 덩어리 사이의 빈 자리(가운데 5칸에 닿거나 걸치는 SPACER)인지. Shift 자리 같은 다른 빈 칸은 키로 센다. */
    private static boolean middleGap(Key k, float x) {
        return k.type == Key.SPACER && x <= 5.001f && x + k.weight >= 4.999f;
    }

    @Test
    public void numberKeysFillEachBlockEvenly() throws Exception {
        for (int[] s : SETTINGS) {
            Prefs p = prefsFor(s, true);
            KeyboardLayout korean = KeyboardLayout.korean(p);
            float[] letters = halves(korean.rows[1]);   // 첫 글자 줄: 자음 덩어리와 모음 덩어리
            for (KeyboardLayout l : new KeyboardLayout[]{korean, KeyboardLayout.english(p)}) {
                float[] h = halves(l.rows[0]);
                for (int i = 0; i < 4; i++) assertEquals("숫자 줄은 덩어리와 같은 자리", letters[i], h[i], 0.001f);
                float x = 0f;
                for (Key k : l.rows[0].keys) {
                    if (k.type != Key.GAP && !middleGap(k, x)) {
                        boolean left = x < 5f;
                        float want = left ? (h[1] - h[0]) / 5f : (h[3] - h[2]) / 5f;
                        assertEquals("덩어리 안의 숫자 키 폭은 같습니다", want, k.weight, 0.001f);
                    }
                    x += k.weight;
                }
            }
        }
    }

    @Test
    public void everyRowMovesWithItsBlock() throws Exception {
        for (int[] s : SETTINGS) {
            Prefs p = prefsFor(s, true);
            KeyboardLayout korean = KeyboardLayout.korean(p);
            float[] want = halves(korean.rows[1]);
            // 자음·모음 줄, 숫자 줄, 맨 아래 줄이 모두 같은 두 덩어리를 쓴다.
            for (KeyboardLayout.Row row : korean.rows) {
                float[] h = halves(row);
                for (int i = 0; i < 4; i++) assertEquals(want[i], h[i], 0.001f);
            }
            assertEquals("왼쪽 덩어리 폭 = 절반의 자음 키 폭%", 5f * s[2] / 100f, want[1] - want[0], 0.001f);
            assertEquals("오른쪽 덩어리 폭 = 절반의 모음 키 폭%", 5f * s[3] / 100f, want[3] - want[2], 0.001f);
        }
    }

    @Test
    public void vowelsFillTheRightHalfByDefault() throws Exception {
        Prefs p = landscapeEditView(new FakeSp());
        KeyboardLayout korean = KeyboardLayout.korean(p);
        for (int r = 0; r < 3; r++) {
            float[] h = halves(korean.rows[r]);
            assertEquals(0f, h[0], 0.001f);
            assertEquals(5f, h[2], 0.001f);
            assertEquals(10f, h[3], 0.001f);
        }
    }

    @Test
    public void spaceBarIsSplitIntoBothBlocks() throws Exception {
        for (int[] s : SETTINGS) {
            Prefs p = prefsFor(s, false);
            for (KeyboardLayout l : new KeyboardLayout[]{KeyboardLayout.korean(p), KeyboardLayout.english(p),
                    KeyboardLayout.symbols(true, p)}) {
                KeyboardLayout.Row bottom = l.rows[l.rows.length - 1];
                halves(bottom);   // 가운데를 가로지르는 키(스페이스바)가 없다
                float x = 0f;
                int leftSpaces = 0, rightSpaces = 0;
                for (Key k : bottom.keys) {
                    if (k.type == Key.SPACE) {
                        if (x < 5f) leftSpaces++;
                        else rightSpaces++;
                    }
                    x += k.weight;
                }
                assertEquals(1, leftSpaces);
                assertEquals(1, rightSpaces);
            }
        }
    }

    @Test
    public void englishHomeRowIsIndentedLikeThePortraitKeyboard() throws Exception {
        for (int[] s : SETTINGS) {
            KeyboardLayout l = KeyboardLayout.english(prefsFor(s, false));
            float[] top = extent(l.rows[0]), home = extent(l.rows[1]);
            assertTrue("a의 왼쪽에 여백이 있어야 합니다", home[0] > top[0] + 0.01f);
            assertTrue("l의 오른쪽에 여백이 있어야 합니다", home[1] < top[1] - 0.01f);
            float total = 0f;
            for (Key k : l.rows[1].keys) total += k.weight;
            assertEquals(10f, total, 0.001f);
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
                        if (k.type != Key.GAP && !middleGap(k, x)) {
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

    /** 줄에서 label 글자 키(그리는 키가 아니면 hidden)의 위치 {시작 x, 폭, 키 번호}. 없으면 null. */
    private static float[] find(KeyboardLayout.Row row, char label, boolean hidden) {
        float x = 0f;
        for (int i = 0; i < row.keys.length; i++) {
            Key k = row.keys[i];
            if (k.type == Key.CHAR && k.label.charAt(0) == label && k.hidden == hidden) return new float[]{x, k.weight, i};
            x += k.weight;
        }
        return null;
    }

    @Test
    public void emptySpaceLeftOfHAndBTypesGAndVWithoutMovingAnyKey() throws Exception {
        boolean seen = false;
        for (int[] s : SETTINGS) {
            KeyboardLayout l = KeyboardLayout.english(prefsFor(s, false));
            for (Object[] spec : new Object[][]{{1, 'g', 'h'}, {2, 'v', 'b'}}) {
                KeyboardLayout.Row row = l.rows[(Integer) spec[0]];
                char hiddenLabel = (Character) spec[1], hostLabel = (Character) spec[2];
                float[] hidden = find(row, hiddenLabel, true), host = find(row, hostLabel, false);
                assertTrue(host != null);
                float total = 0f;
                for (Key k : row.keys) total += k.weight;
                assertEquals(10f, total, 0.001f);
                if (hidden == null) continue;   // 빈 자리가 없으면 추가하지 않는다
                seen = true;
                assertTrue("빈 자리 폭은 키 하나를 넘지 않습니다", hidden[1] <= host[1] + 0.001f);
                assertEquals("host 키 바로 왼쪽", host[2] - 1f, hidden[2], 0.001f);
                assertEquals("host 키에 붙어 있다", host[0], hidden[0] + hidden[1], 0.001f);
                Key k = row.keys[(int) hidden[2]];
                assertEquals(String.valueOf(hiddenLabel), k.output);
            }
        }
        assertTrue("설정 중에는 빈 자리가 생기는 경우가 있어야 합니다", seen);
    }

    @Test
    public void noHiddenKeysInTheNormalKeyboard() {
        for (KeyboardLayout.Row row : KeyboardLayout.english(null).rows) {
            for (Key k : row.keys) assertTrue(!k.hidden);
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

    @Test
    public void portraitBalancedLayoutIsUnchanged() {
        // 세로 모드(분리 키보드 아님)의 맨 아래 줄은 스페이스바 하나가 가운데를 지난다.
        int spaces = 0;
        for (Key k : KeyboardLayout.english(null).rows[3].keys) if (k.type == Key.SPACE) spaces++;
        assertEquals(1, spaces);
    }

    @Test
    public void middleGapDoesNotPressTheNearestKey() throws Exception {
        boolean seen = false;
        for (int[] s : SETTINGS) {
            Prefs p = prefsFor(s, true);
            for (KeyboardLayout l : new KeyboardLayout[]{KeyboardLayout.korean(p), KeyboardLayout.english(p),
                    KeyboardLayout.symbols(true, p)}) {
                for (KeyboardLayout.Row row : l.rows) {
                    // 왼쪽 덩어리의 마지막 키와 오른쪽 덩어리의 첫 키 사이에는 '가까운 키'로 가는 빈틈(GAP)이 없다.
                    float x = 0f;
                    for (Key k : row.keys) {
                        if (k.type == Key.GAP) {
                            assertTrue("가운데에 가까운 키로 가는 빈틈", x + k.weight < 4.999f || x > 5.001f);
                        }
                        if (k.type == Key.SPACER && x < 5f && x + k.weight > 5f) seen = true;
                        x += k.weight;
                    }
                }
            }
        }
        assertTrue("폭을 줄이면 가운데에 반응하지 않는 빈 자리가 생긴다", seen);
    }

    @Test
    public void middleGapCanBeDraggedToMoveTheCursor() throws Exception {
        boolean seen = false;
        for (Key k : concat(KeyboardLayout.korean(prefsFor(SETTINGS[1], true)))) seen |= k.dragCursor;
        assertTrue("폭을 줄이면 끌 수 있는 가운데 빈 자리가 생긴다", seen);
        for (Key k : concat(KeyboardLayout.korean(prefsFor(SETTINGS[1], true)))) {
            if (k.dragCursor) assertEquals(Key.SPACER, k.type);
        }
        for (Key k : concat(KeyboardLayout.english(null))) assertTrue(!k.dragCursor);
        // g·v를 입력하는 키 왼쪽에 남은 빈 자리도 가운데 빈 자리의 일부라 끌 수 있다.
        boolean rest = false;
        for (int[] s : SETTINGS) {
            KeyboardLayout l = KeyboardLayout.english(prefsFor(s, false));
            for (KeyboardLayout.Row row : l.rows) {
                for (Key k : row.keys) rest |= k.type == Key.SPACER && k.dragCursor;
            }
        }
        assertTrue(rest);
    }

    private static java.util.List<Key> concat(KeyboardLayout l) {
        java.util.List<Key> all = new java.util.ArrayList<>();
        for (KeyboardLayout.Row row : l.rows) java.util.Collections.addAll(all, row.keys);
        return all;
    }

    @Test
    public void englishBottomLetterRowHasWideShiftAndDeleteAtTheEdges() throws Exception {
        for (int[] s : SETTINGS) {
            Prefs p = prefsFor(s, false);
            KeyboardLayout l = KeyboardLayout.english(p);
            KeyboardLayout.Row row = l.rows[2];
            float x = 0f, shiftAt = -1f, shiftW = 0f, delEnd = -1f, delW = 0f, letterW = 0f, rightLetterW = 0f, firstLetterAt = -1f;
            for (Key k : row.keys) {
                if (k.type == Key.SHIFT) { shiftAt = x; shiftW = k.weight; }
                if (k.type == Key.DELETE) { delEnd = x + k.weight; delW = k.weight; }
                if (k.type == Key.CHAR && !k.hidden && firstLetterAt < 0f) { firstLetterAt = x; letterW = k.weight; }
                if (k.type == Key.CHAR && !k.hidden) rightLetterW = k.weight;   // 마지막 글자(m): 오른쪽 덩어리의 칸 폭
                x += k.weight;
            }
            float[] top = extent(l.rows[0]);   // q ~ p (첫 글자 줄): 덩어리의 양 끝
            assertEquals("Shift는 왼쪽 덩어리의 왼쪽 끝에", top[0], shiftAt, 0.001f);
            assertEquals("⌫는 오른쪽 덩어리의 오른쪽 끝에", top[1], delEnd, 0.001f);
            assertEquals("Shift는 글자 키의 1.5배", 1.5f * letterW, shiftW, 0.001f);
            assertEquals("⌫도 (오른쪽 덩어리의) 글자 키의 1.5배", 1.5f * rightLetterW, delW, 0.001f);
            assertEquals("z는 Shift 바로 옆", shiftAt + shiftW, firstLetterAt, 0.001f);
            assertEquals(10f, x, 0.001f);
        }
    }

    private static float widthOf(KeyboardLayout.Row row, int type, boolean leftHalf) {
        float x = 0f;
        for (Key k : row.keys) {
            if (k.type == type && (x < 5f) == leftHalf) return k.weight;
            x += k.weight;
        }
        return -1f;
    }

    @Test
    public void gridLayoutMatchesShiftAndDeleteToTheBottomFunctionKeys() throws Exception {
        for (boolean d7 : new boolean[]{false, true}) {
            for (int[] s : SETTINGS) {
                FakeSp sp = new FakeSp();
                Prefs p = landscapeEditView(sp);
                sp.m.put(Prefs.GRID_LAYOUT, true);
                if (d7) sp.m.put(Prefs.KOREAN_LAYOUT, "danmoeum7");
                sp.m.put(p.balancedConsonantPosKey(), s[0]);
                sp.m.put(p.balancedVowelPosKey(), s[1]);
                sp.m.put(p.balancedConsonantWidthKey(), s[2]);
                sp.m.put(p.balancedVowelWidthKey(), s[3]);
                KeyboardLayout en = KeyboardLayout.english(p), ko = KeyboardLayout.korean(p);
                KeyboardLayout.Row enBottom = en.rows[en.rows.length - 1], koBottom = ko.rows[ko.rows.length - 1];
                float mode = widthOf(enBottom, Key.TO_SYMBOLS, true), enter = widthOf(enBottom, Key.ENTER, false);
                KeyboardLayout.Row letters = en.rows[2];
                assertEquals("Shift = ?123 폭", mode, widthOf(letters, Key.SHIFT, true), 0.001f);
                assertEquals("⌫ = 엔터 폭", enter, widthOf(letters, Key.DELETE, false), 0.001f);
                // 한글 자판의 ⌫는 엔터 폭이 아니라 다른 모음 키와 같은 폭이다.
                assertEquals("한글 ⌫ = 모음 폭", widthOf(ko.rows[1], Key.CHAR, false), widthOf(ko.rows[2], Key.DELETE, false), 0.001f);
                for (KeyboardLayout sym : new KeyboardLayout[]{KeyboardLayout.symbols(true, p), KeyboardLayout.symbols2(true, p)}) {
                    KeyboardLayout.Row third = sym.rows[sym.rows.length - 2];
                    KeyboardLayout.Row symBottom = sym.rows[sym.rows.length - 1];
                    assertEquals("기호 쪽 넘김 키 = 맨 아래 왼쪽 기능키 폭", widthOf(symBottom, Key.TO_LETTERS, true),
                            widthOf(third, Key.SYMBOL_PAGE, true), 0.001f);
                    assertEquals("기호 자판 ⌫ = 엔터 폭", widthOf(symBottom, Key.ENTER, false),
                            widthOf(third, Key.DELETE, false), 0.001f);
                }
                float total = 0f;
                for (Key k : letters.keys) total += k.weight;
                assertEquals(10f, total, 0.001f);
            }
        }
    }

    private static Prefs portraitView(FakeSp sp) throws Exception {
        Constructor<Prefs> c = Prefs.class.getDeclaredConstructor(SharedPreferences.class, boolean.class, String.class,
                boolean.class);
        c.setAccessible(true);
        return c.newInstance(sp, false, "", false);
    }

    /** 줄의 실제 키(빈 자리·숨은 키 제외)를 "종류 글자 시작 폭"으로. 합이 10보다 짧은 줄은 일반 자판처럼 가운데에 놓는다. */
    private static java.util.List<String> geometry(KeyboardLayout.Row row) {
        float total = 0f;
        for (Key k : row.keys) total += k.weight;
        float x = Math.max(0f, (10f - total) / 2f);
        java.util.List<String> out = new java.util.ArrayList<>();
        for (Key k : row.keys) {
            if (k.type != Key.GAP && k.type != Key.SPACER && !k.hidden) {
                out.add(k.type + " " + k.label + String.format(" %.3f %.3f", x, k.weight));
            }
            x += k.weight;
        }
        return out;
    }

    @Test
    public void englishAndSymbolRowsAreTheNormalRowsCutInTheMiddle() throws Exception {
        for (boolean grid : new boolean[]{false, true}) {
            FakeSp splitSp = new FakeSp(), normalSp = new FakeSp();
            Prefs split = landscapeEditView(splitSp), normal = portraitView(normalSp);
            splitSp.m.put(Prefs.GRID_LAYOUT, grid);
            normalSp.m.put(Prefs.GRID_LAYOUT, grid);
            // 격자 키 한 칸(한글 자음 키 폭)이 같도록 세로 모드도 자·모음 균형 레이아웃으로 둔다.
            normalSp.m.put(normal.balancedLayoutKey(), true);
            KeyboardLayout[][] pairs = {
                    {KeyboardLayout.english(split), KeyboardLayout.english(normal)},
                    {KeyboardLayout.symbols(true, split), KeyboardLayout.symbols(true, normal)},
                    {KeyboardLayout.symbols2(true, split), KeyboardLayout.symbols2(true, normal)},
            };
            for (KeyboardLayout[] pair : pairs) {
                assertEquals(pair[1].rows.length, pair[0].rows.length);
                for (int r = 0; r < pair[0].rows.length - 1; r++) {   // 맨 아래 줄(스페이스바 둘)만 다르다
                    assertEquals("격자 " + grid + " 줄 " + r, geometry(pair[1].rows[r]), geometry(pair[0].rows[r]));
                }
            }
        }
    }

    @Test
    public void splitRowsNeverOverlapAndFillTheRow() throws Exception {
        for (boolean grid : new boolean[]{false, true}) {
            for (int[] s : SETTINGS) {
                for (int[] vw : new int[][]{{s[2], s[3]}, {100, 40}, {40, 100}}) {
                    FakeSp sp = new FakeSp();
                    Prefs p = landscapeEditView(sp);
                    sp.m.put(Prefs.GRID_LAYOUT, grid);
                    sp.m.put(p.balancedConsonantPosKey(), s[0]);
                    sp.m.put(p.balancedVowelPosKey(), s[1]);
                    sp.m.put(p.balancedConsonantWidthKey(), vw[0]);
                    sp.m.put(p.balancedVowelWidthKey(), vw[1]);
                    for (KeyboardLayout l : new KeyboardLayout[]{KeyboardLayout.english(p), KeyboardLayout.symbols(true, p),
                            KeyboardLayout.symbols2(false, p)}) {
                        for (KeyboardLayout.Row row : l.rows) {
                            float total = 0f;
                            for (Key k : row.keys) {
                                assertTrue("폭이 음수인 키", k.weight >= -0.0001f);
                                total += k.weight;
                            }
                            assertEquals(10f, total, 0.001f);
                        }
                    }
                }
            }
        }
    }

    @Test
    public void portraitBalancedVowelsFillTheRightHalfAtFullWidth() throws Exception {
        FakeSp sp = new FakeSp();
        Prefs p = portraitView(sp);
        sp.m.put(p.balancedLayoutKey(), true);
        for (int r = 0; r < 3; r++) {
            float x = 0f, from = -1f, to = 0f;
            for (Key k : KeyboardLayout.korean(p).rows[r].keys) {
                if (k.type != Key.GAP && k.type != Key.SPACER && x >= 4.999f) {
                    if (from < 0f) from = x;
                    to = x + k.weight;
                }
                x += k.weight;
            }
            assertEquals("모음 첫 키는 오른쪽 절반의 왼쪽 끝", 5f, from, 0.001f);
            assertEquals("모음 끝 키는 오른쪽 끝", 10f, to, 0.001f);
        }
        sp.m.put(p.balancedVowelWidthKey(), 60);   // 60%면 오른쪽 절반의 60%만 쓴다
        float x = 0f, from = -1f, to = 0f;
        for (Key k : KeyboardLayout.korean(p).rows[0].keys) {
            if (k.type != Key.GAP && x >= 4.999f) {
                if (from < 0f) from = x;
                to = x + k.weight;
            }
            x += k.weight;
        }
        assertEquals(3f, to - from, 0.001f);
        assertEquals(100, KeyboardLayout.balancedWidthMax(p));
    }

    @Test
    public void joinedSpaceBarCrossesTheMiddleAndFunctionKeyWidthApplies() throws Exception {
        for (int[] s : SETTINGS) {
            FakeSp sp = new FakeSp();
            Prefs p = landscapeEditView(sp);
            sp.m.put(p.balancedConsonantPosKey(), s[0]);
            sp.m.put(p.balancedVowelPosKey(), s[1]);
            sp.m.put(p.balancedConsonantWidthKey(), s[2]);
            sp.m.put(p.balancedVowelWidthKey(), s[3]);
            sp.m.put(p.splitFnWidthKey(), 50);
            // 스페이스바를 잇지 않으면 기능키 폭 설정은 쓰지 않는다.
            KeyboardLayout.Row apart = KeyboardLayout.korean(p).rows[3];
            float modeApart = widthOf(apart, Key.TO_SYMBOLS, true);
            int spaces = 0;
            for (Key k : apart.keys) if (k.type == Key.SPACE) spaces++;
            assertEquals(2, spaces);
            sp.m.put(p.splitSpaceJoinKey(), true);
            for (KeyboardLayout l : new KeyboardLayout[]{KeyboardLayout.korean(p), KeyboardLayout.english(p),
                    KeyboardLayout.symbols(true, p)}) {
                KeyboardLayout.Row row = l.rows[l.rows.length - 1];
                float x = 0f, total = 0f, spaceFrom = -1f, spaceTo = 0f;
                int count = 0;
                for (Key k : row.keys) {
                    if (k.type == Key.SPACE) { count++; spaceFrom = x; spaceTo = x + k.weight; }
                    x += k.weight;
                    total += k.weight;
                }
                assertEquals(1, count);
                assertEquals(10f, total, 0.001f);
                assertTrue("스페이스바가 가운데를 가로지른다", spaceFrom < 5f - 0.01f && spaceTo > 5f + 0.01f);
            }
            float modeJoined = widthOf(KeyboardLayout.korean(p).rows[3], Key.TO_SYMBOLS, true);
            assertEquals("하단 기능키 폭 50%", modeApart / 2f, modeJoined, 0.001f);
            // 폭 설정은 스페이스바를 이었을 때만 쓴다.
            sp.m.put(p.splitSpaceJoinKey(), false);
            assertEquals(modeApart, widthOf(KeyboardLayout.korean(p).rows[3], Key.TO_SYMBOLS, true), 0.001f);
        }
    }

    @Test
    public void numberKeysCanHaveLongPressCharacters() throws Exception {
        assertEquals("num", KeyboardLayout.groupOf(true, "5"));
        assertEquals("num", KeyboardLayout.groupOf(false, "0"));
        assertEquals("ko", KeyboardLayout.groupOf(true, "ㅂ"));
        for (boolean split : new boolean[]{false, true}) {
            FakeSp sp = new FakeSp();
            Prefs p = split ? landscapeEditView(sp) : portraitView(sp);
            sp.m.put(Prefs.NUMBER_ROW, true);
            for (KeyboardLayout l : new KeyboardLayout[]{KeyboardLayout.korean(p), KeyboardLayout.english(p)}) {
                assertTrue("기본은 길게 누르기 문자가 없다", l.rows[0].keys[l.rows[0].keys.length > 10 ? 1 : 0].popup == null);
            }
            sp.m.put("popup_num_1", "@\n#\n긴 문장 입니다");
            for (KeyboardLayout l : new KeyboardLayout[]{KeyboardLayout.korean(p), KeyboardLayout.english(p)}) {
                Key one = null;
                for (Key k : l.rows[0].keys) if (k.type == Key.CHAR && "1".equals(k.label)) one = k;
                assertTrue(one != null);
                assertEquals(3, one.popup.length);
                assertEquals("긴 문장 입니다", one.popup[2]);
                assertEquals("@", one.hint());
                for (Key k : l.rows[0].keys) if (k.type == Key.CHAR && "2".equals(k.label)) assertTrue(k.popup == null);
            }
        }
        assertTrue(KeyboardLayout.isDigit("7") && !KeyboardLayout.isDigit("a") && !KeyboardLayout.isDigit("12"));
    }
}
