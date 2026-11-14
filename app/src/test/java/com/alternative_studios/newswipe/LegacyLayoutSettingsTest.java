package com.alternative_studios.newswipe;

import android.content.SharedPreferences;

import com.alternative_studios.newswipe.keyboard.Key;
import com.alternative_studios.newswipe.keyboard.KeyboardLayout;

import org.junit.Test;

import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** 1.9.1에서 올라올 때 자판 모양(두 묶음, 좌·우 균형 배열, 세로 위치)과 완전 사용자화한 밀기가 그대로인지. */
public class LegacyLayoutSettingsTest {

    private static Prefs view(LegacySwipeSettingsTest.MapSp sp, String profile) throws Exception {
        Constructor<Prefs> c = Prefs.class.getDeclaredConstructor(SharedPreferences.class, boolean.class, String.class,
                boolean.class);
        c.setAccessible(true);
        return c.newInstance(sp, false, profile, false);
    }

    /** 1.9.1을 쓰던 설정 (keyValues: 키, 값, 키, 값 …)을 옮긴다. */
    private static LegacySwipeSettingsTest.MapSp upgraded(Object... keyValues) throws Exception {
        LegacySwipeSettingsTest.MapSp sp = new LegacySwipeSettingsTest.MapSp();
        sp.m.put(Prefs.SETUP_DONE, true);
        for (int i = 0; i < keyValues.length; i += 2) sp.m.put((String) keyValues[i], keyValues[i + 1]);
        LegacySwipeSettings.migrateIfNeeded(view(sp, Prefs.PROFILE_PORTRAIT));
        return sp;
    }

    /** 첫 줄의 키 폭 (빈틈 GAP·SPACER 포함, 그리지 않는 키 제외). */
    private static List<Float> firstRow(KeyboardLayout l) {
        List<Float> out = new ArrayList<>();
        for (Key k : l.rows[0].keys) if (!k.isHidden()) out.add(Math.round(k.weight * 1000f) / 1000f);
        return out;
    }

    private static List<String> labels(KeyboardLayout.Row row) {
        List<String> out = new ArrayList<>();
        for (Key k : row.keys) if (!k.isHidden() && k.type == Key.CHAR) out.add(k.label);
        return out;
    }

    @Test
    public void balancedDefaultsKeepTheOldLook() throws Exception {
        // 1.9.1: 균형 배열을 켜고 폭·위치는 기본값(100%) → 자음 1칸, 모음 1.667칸, 가운데 빈틈 없음.
        LegacySwipeSettingsTest.MapSp sp = upgraded("balanced_layout_danmoeum8", true);
        List<Float> row = firstRow(KeyboardLayout.korean(view(sp, Prefs.PROFILE_PORTRAIT)));
        assertEquals(8, row.size());
        for (int i = 0; i < 5; i++) assertEquals(1f, row.get(i), 0.001f);
        for (int i = 5; i < 8; i++) assertEquals(1.667f, row.get(i), 0.001f);
    }

    @Test
    public void oldBoundaryBecomesBlockSizes() throws Exception {
        // 1.9.1: 자·모음 경계 40% → 자음 5개가 4칸, 모음 3개가 6칸.
        LegacySwipeSettingsTest.MapSp sp = upgraded("balanced_layout_danmoeum8", true, "balanced_boundary_danmoeum8", 40);
        List<Float> row = firstRow(KeyboardLayout.korean(view(sp, Prefs.PROFILE_PORTRAIT)));
        assertEquals(8, row.size());
        for (int i = 0; i < 5; i++) assertEquals(0.8f, row.get(i), 0.001f);
        for (int i = 5; i < 8; i++) assertEquals(2f, row.get(i), 0.001f);
        assertFalse(sp.contains("balanced_boundary_danmoeum8"));
    }

    @Test
    public void unusedBalancedLayoutGetsNewDefaults() throws Exception {
        LegacySwipeSettingsTest.MapSp sp = upgraded();
        sp.m.put("balanced_layout", true);   // 2.0.0에서 처음 켠다
        List<Float> row = firstRow(KeyboardLayout.korean(view(sp, Prefs.PROFILE_PORTRAIT)));
        assertEquals(0.9f, row.get(0), 0.001f);   // 새 기본값: 폭 90%와 가운데 빈틈
    }

    @Test
    public void landscapeSplitKeepsOldBlocksAndReach() throws Exception {
        // 1.9.1: 가로 모드 분리 키보드, 단모음(8열), 좌·우 균형 배열은 기본(꺼짐) → 자음 5개가 왼쪽 절반을 꽉 채운다.
        LegacySwipeSettingsTest.MapSp sp = upgraded(Prefs.LANDSCAPE_SPLIT, true);
        KeyboardLayout l = KeyboardLayout.korean(view(sp, Prefs.PROFILE_LANDSCAPE));
        assertEquals(java.util.Arrays.asList("ㅂ", "ㅈ", "ㄷ", "ㄱ", "ㅅ", "ㅗ", "ㅐ", "ㅔ"), labels(l.rows[0]));
        List<Float> row = firstRow(l);
        for (int i = 0; i < 5; i++) assertEquals(1f, row.get(i), 0.001f);
        for (int i = 5; i < 8; i++) assertEquals(1.667f, row.get(i), 0.001f);
    }

    @Test
    public void landscapeReachIsKept() throws Exception {
        // 1.9.1: NewSwipe 단모음에서 가로 모드 좌·우 균형 배열을 켰다 → ㄱ이 오른쪽 묶음에도 있다.
        LegacySwipeSettingsTest.MapSp sp = upgraded(Prefs.LANDSCAPE_SPLIT, true, Prefs.KOREAN_LAYOUT, Prefs.KOREAN_LAYOUT_D7,
                "split_reach_column", true);
        Prefs land = view(sp, Prefs.PROFILE_LANDSCAPE);
        assertTrue(land.splitReachColumn());
        List<String> top = labels(KeyboardLayout.korean(land).rows[0]);
        assertEquals(2, java.util.Collections.frequency(top, "ㄱ"));
    }

    @Test
    public void liftBecomesBottomPadding() throws Exception {
        LegacySwipeSettingsTest.MapSp sp = upgraded(Prefs.PAD_BOTTOM, 10, "lift_land", 160, "large_lift_port", 20);
        assertEquals(170, view(sp, Prefs.PROFILE_LANDSCAPE).padBottomDp());   // 48을 넘어도 잘리지 않는다
        assertEquals(30, view(sp, Prefs.PROFILE_LARGE_PORTRAIT).padBottomDp());
        assertEquals(10, view(sp, Prefs.PROFILE_LARGE_LANDSCAPE).padBottomDp());   // 세로 위치가 없던 프로필은 그대로
        assertEquals(10, view(sp, Prefs.PROFILE_PORTRAIT).padBottomDp());
        assertFalse(sp.contains("lift_land"));
        assertEquals(Prefs.PAD_BOTTOM_MAX, view(sp, Prefs.PROFILE_PORTRAIT).padBottomMax());
        assertTrue(view(sp, Prefs.PROFILE_LANDSCAPE).padBottomMax() >= Prefs.PAD_BOTTOM_MAX + 160);
    }

    @Test
    public void oneHandKeepsItsHeightAboveTheBottom() throws Exception {
        // 1.9.1: 한 손 모드 자판은 키보드 아래쪽 여백(10) 위에 세로 위치(40)만큼 더 떠 있었다 → 한 손 모드 아래쪽 여백 50.
        LegacySwipeSettingsTest.MapSp sp = upgraded(Prefs.PAD_BOTTOM, 10, Prefs.ONE_HAND_LIFT, 40);
        assertEquals(50, view(sp, Prefs.PROFILE_PORTRAIT).oneHandBottomForProfile());
        assertEquals(50, view(sp, Prefs.PROFILE_LANDSCAPE).oneHandBottomForProfile());
        assertEquals(10, view(sp, Prefs.PROFILE_PORTRAIT).padBottomDp());   // 일반 모드의 여백은 그대로
        // 기본값(여백 4, 세로 위치 0)만 쓰던 사람도 4dp 위에 그대로 있다.
        assertEquals(4, view(upgraded(), Prefs.PROFILE_PORTRAIT).oneHandBottomForProfile());
        // 둘 다 최대여도 잘리지 않는다.
        LegacySwipeSettingsTest.MapSp max = upgraded(Prefs.PAD_BOTTOM, 48, Prefs.ONE_HAND_LIFT, 160);
        assertEquals(208, view(max, Prefs.PROFILE_PORTRAIT).oneHandBottomForProfile());
        // 처음 설치하면 기본값 0.
        LegacySwipeSettingsTest.MapSp fresh = new LegacySwipeSettingsTest.MapSp();
        LegacySwipeSettings.migrateIfNeeded(view(fresh, Prefs.PROFILE_PORTRAIT));
        assertEquals(0, view(fresh, Prefs.PROFILE_PORTRAIT).oneHandBottomForProfile());
    }

    @Test
    public void customDownSwipesAreKept() throws Exception {
        LegacySwipeSettingsTest.MapSp sp = upgraded("swipe_custom", true, "swipe_custom_ko_ㄱ_down", "ㅋ",
                "swipe_custom_ko_ㅏ_down", "");
        KeyboardLayout l = KeyboardLayout.korean(view(sp, Prefs.PROFILE_PORTRAIT));
        assertEquals("ㅋ", key(l, "ㄱ").swipeText(Key.SWIPE_DOWN));
        assertEquals(null, key(l, "ㅏ").swipeText(Key.SWIPE_DOWN));
        // 바꾸지 않은 키는 예전처럼 쌍자음·ㅣ계 이중모음
        assertEquals("ㄸ", key(l, "ㄷ").swipeText(Key.SWIPE_DOWN));
        assertEquals("ㅕ", key(l, "ㅓ").swipeText(Key.SWIPE_DOWN));
        // 예전 완전 사용자화에서 온점 위로 밀기는 기본이 없음이었다
        assertEquals(null, key(l, ".").swipeText(Key.SWIPE_UP));
    }

    @Test
    public void untouchedCustomDownKeepsLock() throws Exception {
        LegacySwipeSettingsTest.MapSp sp = upgraded("swipe_custom", true, "swipe_custom_ko_ㄱ_up", "x");
        assertFalse(sp.contains(Prefs.SWIPE_DOWN_CONSONANT));
        assertFalse(sp.contains(Prefs.SWIPE_DOWN_VOWEL));
    }

    @Test
    public void freshAndCurrentUsersAreNotTouched() throws Exception {
        LegacySwipeSettingsTest.MapSp fresh = new LegacySwipeSettingsTest.MapSp();
        LegacySwipeSettings.migrateIfNeeded(view(fresh, Prefs.PROFILE_PORTRAIT));
        assertFalse(fresh.contains("portrait_split_reach_column"));
        // 이미 2.0.0을 쓰던 설정 (밀기 규칙은 옮겼다): 다시 들어와도 자판 모양 값을 적지 않는다.
        LegacySwipeSettingsTest.MapSp current = new LegacySwipeSettingsTest.MapSp();
        current.m.put(LegacySwipeSettings.DONE, true);
        current.m.put(Prefs.LANDSCAPE_SPLIT, true);
        LegacySwipeSettings.migrateIfNeeded(view(current, Prefs.PROFILE_PORTRAIT));
        assertFalse(current.contains("portrait_split_reach_column"));
        assertTrue(current.getBoolean(LegacyLayoutSettings.DONE, false));
    }

    private static Key key(KeyboardLayout l, String label) {
        for (KeyboardLayout.Row r : l.rows) for (Key k : r.keys) if (k.type == Key.CHAR && label.equals(k.slot())) return k;
        throw new AssertionError(label);
    }
}
