package com.alternative_studios.newswipe;

import android.content.SharedPreferences;

import org.junit.Test;

import java.lang.reflect.Constructor;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * 폴더블 내부 화면 세로·가로를 나누고, 한 손 모드의 폴더블 내부 화면 가로가 가로 모드 값을 따르지 않게 바꾼 뒤에도
 * 업데이트 전과 같은 모습인지 (Prefs.migrateProfileSides).
 */
public class ProfileSidesMigrationTest {

    private static Prefs view(LegacySwipeSettingsTest.MapSp sp, String profile) throws Exception {
        Constructor<Prefs> c = Prefs.class.getDeclaredConstructor(SharedPreferences.class, boolean.class, String.class,
                boolean.class);
        c.setAccessible(true);
        return c.newInstance(sp, false, profile, false);
    }

    @Test
    public void sharedInnerScreenLayoutIsCopiedToTheLandscapeSide() throws Exception {
        LegacySwipeSettingsTest.MapSp sp = new LegacySwipeSettingsTest.MapSp();
        sp.m.put("large_split_port", true);
        sp.m.put("large_split_space_join_port", true);
        sp.m.put("large_split_fn_width_port", 150);
        Prefs port = view(sp, Prefs.PROFILE_PORTRAIT);
        port.migrateProfileSides();
        Prefs lp = view(sp, Prefs.PROFILE_LARGE_PORTRAIT), ll = view(sp, Prefs.PROFILE_LARGE_LANDSCAPE);
        // 예전처럼 폴더블 내부 화면 세로·가로 모두 켜져 있다.
        assertTrue(lp.profileSplit() && ll.profileSplit());
        assertTrue(lp.splitSpaceJoin() && ll.splitSpaceJoin());
        assertEquals(150, ll.splitFnWidthSetting());
        // 이제는 따로: 세로를 끄면 가로는 그대로.
        sp.m.put(lp.splitKey(), false);
        assertFalse(lp.profileSplit());
        assertTrue(ll.profileSplit());
        assertTrue(sp.getBoolean(Prefs.PROFILE_SIDES_DONE, false));
    }

    @Test
    public void legacyPerLayoutSplitIsCopiedToo() throws Exception {
        LegacySwipeSettingsTest.MapSp sp = new LegacySwipeSettingsTest.MapSp();
        sp.m.put("large_split_port_danmoeum8", true);   // 배열마다 저장하던 때의 켜기
        sp.m.put(Prefs.KOREAN_LAYOUT, Prefs.KOREAN_LAYOUT_8);
        view(sp, Prefs.PROFILE_PORTRAIT).migrateProfileSides();
        assertTrue(view(sp, Prefs.PROFILE_LARGE_PORTRAIT).profileSplit());
        assertTrue(view(sp, Prefs.PROFILE_LARGE_LANDSCAPE).profileSplit());
    }

    @Test
    public void sizesStillFollowPortraitAndAreNotPinned() throws Exception {
        LegacySwipeSettingsTest.MapSp sp = new LegacySwipeSettingsTest.MapSp();
        sp.m.put(Prefs.KEYBOARD_HEIGHT, 110);
        sp.m.put(Prefs.KEYBOARD_HEIGHT_LAND, 60);   // 가로 모드에서만 정했다
        sp.m.put("land_pad_b", 30);
        view(sp, Prefs.PROFILE_PORTRAIT).migrateProfileSides();
        Prefs ll = view(sp, Prefs.PROFILE_LARGE_LANDSCAPE);
        // 폴더블 내부 화면 가로는 예전처럼 세로 모드 값을 따르고, 값을 적어 두지 않아 세로 모드를 바꾸면 따라간다.
        assertEquals(110, ll.keyboardHeight());
        assertFalse(sp.contains(ll.heightKey()));
        assertFalse(sp.contains(ll.padKey(Prefs.SIDE_BOTTOM)));
        sp.m.put(Prefs.KEYBOARD_HEIGHT, 120);
        assertEquals(120, ll.keyboardHeight());
    }

    @Test
    public void landscapeOneHandValuesAreCopiedToTheInnerLandscape() throws Exception {
        LegacySwipeSettingsTest.MapSp sp = new LegacySwipeSettingsTest.MapSp();
        sp.m.put(Prefs.ONE_HAND_LIFT, 40);
        sp.m.put(Prefs.ONE_HAND_LIFT + "_land", 12);     // 가로 모드에서 정했다 (예전에는 폴더블 가로도 이 값을 따랐다)
        sp.m.put(Prefs.ONE_HAND_WIDTH + "_land", 45);
        sp.m.put("large_one_hand_height_land", 75);      // 폴더블 가로에서 이미 정한 값은 그대로
        sp.m.put(Prefs.ONE_HAND_HEIGHT + "_land", 85);
        view(sp, Prefs.PROFILE_PORTRAIT).migrateProfileSides();
        Prefs ll = view(sp, Prefs.PROFILE_LARGE_LANDSCAPE), lp = view(sp, Prefs.PROFILE_LARGE_PORTRAIT);
        assertEquals(12, ll.oneHandBottomForProfile());
        assertEquals(45, ll.oneHandWidthForProfile());
        assertEquals(75, ll.oneHandHeightForProfile());
        // 폴더블 내부 화면 세로의 키 높이·아래쪽 여백은 손대지 않는다 (예전처럼 세로 모드 값).
        assertEquals(40, lp.oneHandBottomForProfile());
        assertFalse(sp.contains(lp.oneHandBottomKey()));
        // 세로 모드 자판 폭을 정하지 않았으면 폴더블 내부 화면 세로는 새 기본값 40%.
        assertEquals(Prefs.ONE_HAND_WIDTH_LARGE_PORT_DEFAULT, lp.oneHandWidthForProfile());
    }

    @Test
    public void portraitOneHandWidthIsCopiedToTheInnerPortrait() throws Exception {
        LegacySwipeSettingsTest.MapSp sp = new LegacySwipeSettingsTest.MapSp();
        sp.m.put(Prefs.ONE_HAND_WIDTH, 70);   // 세로 모드에서 정했다 (예전에는 폴더블 세로도 이 값을 따랐다)
        view(sp, Prefs.PROFILE_PORTRAIT).migrateProfileSides();
        Prefs lp = view(sp, Prefs.PROFILE_LARGE_PORTRAIT);
        assertEquals(70, lp.oneHandWidthForProfile());
        // 이제는 따로: 세로 모드를 바꿔도 폴더블 세로는 그대로.
        sp.m.put(Prefs.ONE_HAND_WIDTH, 85);
        assertEquals(70, lp.oneHandWidthForProfile());
    }

    @Test
    public void freshInstallWritesOnlyTheMark() throws Exception {
        LegacySwipeSettingsTest.MapSp sp = new LegacySwipeSettingsTest.MapSp();
        view(sp, Prefs.PROFILE_PORTRAIT).migrateProfileSides();
        assertEquals(1, sp.m.size());
        assertTrue(sp.getBoolean(Prefs.PROFILE_SIDES_DONE, false));
        // 한 번만 돈다: 표시가 있으면 나중에 생긴 예전 키도 옮기지 않는다.
        sp.m.put("large_split_port", true);
        view(sp, Prefs.PROFILE_PORTRAIT).migrateProfileSides();
        assertFalse(sp.contains("large_split_land"));
    }
}
