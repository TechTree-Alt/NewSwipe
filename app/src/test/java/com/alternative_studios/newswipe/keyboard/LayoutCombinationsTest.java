package com.alternative_studios.newswipe.keyboard;

import static org.junit.Assert.assertEquals;

import android.content.SharedPreferences;

import com.alternative_studios.newswipe.Prefs;

import org.junit.Test;

import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 네 키보드(세로·가로·폴더블 내부 화면 세로·가로) × 한글 배열 × 한글·영어·기호 자판의 상세 배열 × 숫자 줄·격자 정렬·한 손 모드 ×
 * 묶음 폭·위치의 양 끝 값을 모두 섞어 자판을 만들어 본다: 예외가 없고, 키 폭이 음수·NaN이거나 누를 수 없을 만큼 좁지 않고,
 * 줄이 넘치지 않으며, 일반 자판의 키가 하나도 빠지거나 겹쳐 생기지 않아야 한다.
 */
public class LayoutCombinationsTest {
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
        Constructor<Prefs> c = Prefs.class.getDeclaredConstructor(SharedPreferences.class, boolean.class, String.class, boolean.class);
        c.setAccessible(true);
        return c.newInstance(sp, false, profile, false);
    }

    private static java.util.TreeMap<String, Integer> chars(KeyboardLayout l) {
        java.util.TreeMap<String, Integer> m = new java.util.TreeMap<>();
        for (KeyboardLayout.Row r : l.rows) for (Key k : r.keys) {
            if (k.isHidden() || k.type == Key.GAP || k.type == Key.SPACER || k.type == Key.SPACE) continue;
            String id = k.type + ":" + k.label;
            m.merge(id, 1, Integer::sum);
        }
        return m;
    }

    private static String check(KeyboardLayout l, String what) {
        for (int r = 0; r < l.rows.length; r++) {
            float sum = 0;
            for (Key k : l.rows[r].keys) {
                if (Float.isNaN(k.weight) || k.weight < -1e-4f) return what + " row " + r + " bad weight " + k.weight + " " + k.label;
                if (!k.isHidden() && k.type != Key.GAP && k.type != Key.SPACER && k.weight < 0.05f) return what + " row " + r + " tiny key " + k.label + " " + k.weight;
                sum += k.weight;
            }
            if (sum > Math.max(l.columns, 10) + 1e-3f) return what + " row " + r + " too wide " + sum;
            if (l.rows[r].height <= 0) return what + " row " + r + " height";
        }
        return null;
    }

    /** 맨 아래 기능키 줄을 뺀 줄들의 글자 키(숨은 키 제외) 가운데 자리: "줄:글자" → x (칸). */
    private static Map<String, Float> letterCenters(KeyboardLayout l) {
        Map<String, Float> m = new HashMap<>();
        for (int r = 0; r < l.rows.length - 1; r++) {
            float total = 0f;
            for (Key k : l.rows[r].keys) total += k.weight;
            float x = Math.max(0f, (Math.max(l.columns, 10) - total) / 2f);   // 짧은 줄은 가운데에 놓인다
            for (Key k : l.rows[r].keys) {
                if (k.type == Key.CHAR && !k.isHidden()) m.put(r + ":" + k.label, x + k.weight / 2f);
                x += k.weight;
            }
        }
        return m;
    }

    /**
     * 격자 정렬은 쿼티 모양 자판(두벌식·영어·기호)의 글자 키 자리를 바꾸지 않는다: Shift·⌫의 폭이 바뀌어도
     * 맨 아래 글자 줄이 좌우로 밀리지 않는다 (상세 배열 폭에 맞추기을 켜도 꺼도, 묶음 폭이 달라도).
     */
    @Test
    public void gridNeverMovesQwertyLetters() throws Exception {
        String[] profiles = {Prefs.PROFILE_PORTRAIT, Prefs.PROFILE_LANDSCAPE, Prefs.PROFILE_LARGE_PORTRAIT};
        int[] modes = {Prefs.DETAIL_NONE, Prefs.DETAIL_BALANCED, Prefs.DETAIL_SPLIT};
        for (String profile : profiles) for (String layout : new String[]{Prefs.KOREAN_LAYOUT_DUBEOLSIK, Prefs.KOREAN_LAYOUT_8, Prefs.KOREAN_LAYOUT_D7})
        for (int mode : modes) for (int width : new int[]{Prefs.BALANCED_WIDTH_MIN, 60, 100, 130}) for (boolean nr : new boolean[]{false, true}) {
            KeyboardLayout[][] got = new KeyboardLayout[3][];
            for (int g = 0; g < 3; g++) {   // 격자 정렬 끔 / 켬 / 켬 + 상세 배열 폭에 맞추기
                FakeSp sp = new FakeSp();
                sp.m.put(Prefs.KOREAN_LAYOUT, layout);
                sp.m.put(Prefs.NUMBER_ROW, nr);
                sp.m.put(Prefs.LARGE_LAYOUT, true);
                sp.m.put(Prefs.GRID_LAYOUT, g > 0);
                sp.m.put(Prefs.GRID_DETAIL, g == 2);
                Prefs p = view(sp, profile);
                for (int b = 0; b < 3; b++) p.setDetailMode(b, mode);
                for (int b = 0; b < 3; b++) {
                    Prefs bv = p.boardView(b);
                    sp.m.put(bv.balancedConsonantWidthKey(), width);
                    sp.m.put(bv.balancedVowelWidthKey(), Math.max(Prefs.BALANCED_WIDTH_MIN, 160 - width));
                }
                Prefs v = view(sp, profile);
                got[g] = new KeyboardLayout[]{KeyboardLayout.english(v), KeyboardLayout.symbols(true, v), KeyboardLayout.symbols2(true, v),
                        Prefs.KOREAN_LAYOUT_DUBEOLSIK.equals(layout) ? KeyboardLayout.korean(v) : null};
            }
            String[] names = {"en", "sym", "sym2", "ko"};
            for (int i = 0; i < 4; i++) {
                if (got[0][i] == null) continue;
                Map<String, Float> want = letterCenters(got[0][i]);
                for (int g = 1; g < 3; g++) {
                    Map<String, Float> have = letterCenters(got[g][i]);
                    assertEquals(want.keySet(), have.keySet());
                    for (String k : want.keySet()) {
                        assertEquals(profile + "/" + layout + " mode=" + mode + " w=" + width + " nr=" + nr + " grid=" + g + " "
                                + names[i] + " " + k, want.get(k), have.get(k), 0.001f);
                    }
                }
            }
            // 일반 영어 자판: z~m은 s~k 바로 아래다.
            if (mode == Prefs.DETAIL_NONE && Prefs.PROFILE_PORTRAIT.equals(profile)) {
                for (int g = 0; g < 3; g++) {
                    Map<String, Float> c = letterCenters(got[g][0]);
                    int top = nr ? 1 : 0;
                    String[] upper = {"s", "d", "f", "g", "h", "j", "k"}, lower = {"z", "x", "c", "v", "b", "n", "m"};
                    for (int j = 0; j < 7; j++) {
                        assertEquals(lower[j] + "는 " + upper[j] + " 아래", c.get((top + 1) + ":" + upper[j]),
                                c.get((top + 2) + ":" + lower[j]), 0.001f);
                    }
                }
            }
        }
    }

    @Test
    public void everyCombinationBuildsAWholeKeyboard() throws Exception {
        String[] profiles = {Prefs.PROFILE_PORTRAIT, Prefs.PROFILE_LANDSCAPE, Prefs.PROFILE_LARGE_PORTRAIT, Prefs.PROFILE_LARGE_LANDSCAPE};
        String[] layouts = {Prefs.KOREAN_LAYOUT_DUBEOLSIK, Prefs.KOREAN_LAYOUT_8, Prefs.KOREAN_LAYOUT_D7};
        int[] koModes = {Prefs.DETAIL_NONE, Prefs.DETAIL_BALANCED, Prefs.DETAIL_SPLIT};
        int[] otherModes = {Prefs.DETAIL_FOLLOW, Prefs.DETAIL_NONE, Prefs.DETAIL_BALANCED, Prefs.DETAIL_SPLIT};
        int n = 0, fails = 0;
        java.util.Set<String> seen = new java.util.LinkedHashSet<>();
        java.util.Map<String, java.util.List<java.util.TreeMap<String, Integer>>> refs = new java.util.HashMap<>();
        for (String profile : profiles) for (String layout : layouts) for (int ko : koModes) for (int en : otherModes) for (int sy : otherModes)
        for (int flags = 0; flags < 8; flags++) for (int w : new int[]{0, 3}) {
            boolean nr = (flags & 1) != 0, grid = (flags & 2) != 0, oneHand = (flags & 4) != 0;
            FakeSp sp = new FakeSp();
            sp.m.put(Prefs.KOREAN_LAYOUT, layout);
            sp.m.put(Prefs.NUMBER_ROW, nr);
            sp.m.put(Prefs.GRID_LAYOUT, grid);
            sp.m.put(Prefs.LARGE_LAYOUT, true);
            Prefs p = view(sp, profile);
            p.setDetailMode(Prefs.BOARD_KOREAN, ko);
            p.setDetailMode(Prefs.BOARD_ENGLISH, en);
            p.setDetailMode(Prefs.BOARD_SYMBOLS, sy);
            int width = w % 2 == 0 ? Prefs.BALANCED_WIDTH_MIN : 100;
            int pos = w < 2 ? 0 : (w == 2 ? 100 : -100);
            for (int b = 0; b < 3; b++) {
                Prefs bv = p.boardView(b);
                sp.m.put(bv.balancedConsonantWidthKey(), width);
                sp.m.put(bv.balancedVowelWidthKey(), width);
                sp.m.put(bv.balancedConsonantPosKey(), w == 3 ? 100 : -pos);
                sp.m.put(bv.balancedVowelPosKey(), pos);
            }
            Prefs v = view(sp, profile);
            if (oneHand) v = v.oneHandView();
            String what = profile + "/" + layout + " ko=" + ko + " en=" + en + " sy=" + sy + " nr=" + nr + " grid=" + grid + " oh=" + oneHand + " w=" + w;
            KeyboardLayout[] got;
            try {
                got = new KeyboardLayout[]{KeyboardLayout.korean(v), KeyboardLayout.english(v), KeyboardLayout.symbols(true, v), KeyboardLayout.symbols2(true, v)};
            } catch (Throwable e) {
                if (seen.add("EX " + e)) { System.out.println("EXCEPTION " + what + " " + e); e.printStackTrace(System.out); }
                fails++;
                continue;
            }
            // 비교할 일반 자판 (한글 배열·숫자 줄·격자 정렬마다 한 번 만든다)
            String refKey = layout + nr + grid;
            java.util.List<java.util.TreeMap<String, Integer>> want = refs.get(refKey);
            if (want == null) {
                FakeSp ref = new FakeSp();
                ref.m.put(Prefs.KOREAN_LAYOUT, layout);
                ref.m.put(Prefs.NUMBER_ROW, nr);
                ref.m.put(Prefs.GRID_LAYOUT, grid);
                Prefs rp = view(ref, Prefs.PROFILE_PORTRAIT);
                want = new java.util.ArrayList<>();
                for (KeyboardLayout l : new KeyboardLayout[]{KeyboardLayout.korean(rp), KeyboardLayout.english(rp),
                        KeyboardLayout.symbols(true, rp), KeyboardLayout.symbols2(true, rp)}) want.add(chars(l));
                refs.put(refKey, want);
            }
            String[] names = {"ko", "en", "sym", "sym2"};
            for (int i = 0; i < 4; i++) {
                n++;
                String err = check(got[i], what + " " + names[i]);
                if (err == null && !chars(got[i]).equals(want.get(i))) {
                    // 분리 키보드 8열 단모음의 좌·우 균형 배열은 키를 하나 더 놓는다 (숨긴 복사본이 아닌 실제 키)
                    java.util.TreeMap<String, Integer> a = chars(got[i]), bb = want.get(i);
                    java.util.Set<String> diff = new java.util.TreeSet<>();
                    for (String k : a.keySet()) if (!a.get(k).equals(bb.get(k))) diff.add(k + "=" + a.get(k) + "/" + bb.get(k));
                    for (String k : bb.keySet()) if (!bb.get(k).equals(a.get(k))) diff.add(k + "=" + a.get(k) + "/" + bb.get(k));
                    err = what + " " + names[i] + " keys differ " + diff;
                }
                if (err != null) {
                    fails++;
                    String sig = err.replaceAll(" w=\\d", "").replaceAll("nr=\\w+ grid=\\w+ ", "");
                    if (seen.size() < 60 && seen.add(sig)) System.out.println("FAIL " + err);
                }
            }
        }
        assertEquals("자판 " + n + "개 중 문제 " + fails + "개 (처음 몇 개는 위에 출력)", 0, fails);
    }
}
