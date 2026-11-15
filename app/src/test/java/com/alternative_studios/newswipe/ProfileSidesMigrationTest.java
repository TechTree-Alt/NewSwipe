package com.alternative_studios.newswipe;

import android.content.SharedPreferences;

import org.junit.Test;

import java.lang.reflect.Constructor;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * 폴더블 내부 화면 세로·가로를 나누고, 따로 정하는 크기·여백이 정하기 전에 같은 방향의 일반 키보드 값을 따르게 바꾼 뒤에도
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
    public void landscapeOnlyValuesDoNotChangeTheInnerLandscapeKeyboard() throws Exception {
        LegacySwipeSettingsTest.MapSp sp = new LegacySwipeSettingsTest.MapSp();
        sp.m.put(Prefs.KEYBOARD_HEIGHT, 110);
        sp.m.put(Prefs.KEYBOARD_HEIGHT_LAND, 60);   // 가로 모드에서만 정했다
        sp.m.put("land_pad_b", 30);
        sp.m.put("land_pad_a0t", 5);                 // 가로 모드 도구 막대 위쪽 여백
        sp.m.put("large_toolbar_height_land", 90);   // 폴더블 가로에서 이미 정한 값은 그대로
        sp.m.put(Prefs.TOOLBAR_HEIGHT + "_land", 130);
        Prefs port = view(sp, Prefs.PROFILE_PORTRAIT);
        int padBottom = port.padBottomDp();
        port.migrateProfileSides();
        Prefs ll = view(sp, Prefs.PROFILE_LARGE_LANDSCAPE), land = view(sp, Prefs.PROFILE_LANDSCAPE);
        // 업데이트 전처럼 세로 모드 값이다 (가로 모드 값을 따라가지 않는다).
        assertEquals(110, ll.keyboardHeight());
        assertEquals(padBottom, ll.padBottomDp());
        assertEquals(0, ll.areaPadDp(0, Prefs.SIDE_TOP));
        assertEquals(90, ll.toolbarHeight());
        // 가로 모드는 그대로, 가로 모드에서 정하지 않은 값은 적어 두지 않는다 (정하기 전 규칙대로 따른다).
        assertEquals(60, land.keyboardHeight());
        assertFalse(sp.contains(ll.textSizeKey()));
        assertFalse(sp.contains(ll.padKey(Prefs.SIDE_LEFT)));
        // 폴더블 내부 화면 세로는 손대지 않는다.
        assertFalse(sp.contains(view(sp, Prefs.PROFILE_LARGE_PORTRAIT).heightKey()));
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
