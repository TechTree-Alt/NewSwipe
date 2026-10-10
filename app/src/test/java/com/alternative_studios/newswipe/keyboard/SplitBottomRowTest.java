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
            if (k.type != Key.GAP && !k.hidden) {
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
                    if (k.type != Key.GAP) {
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
}
