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

    /** 예전에는 분리 키보드를 켜면 영어·기호 자판도 함께 나뉘었다: 기본값(한글 자판 따라가기)이 같으므로 아무것도 적지 않는다. */
    @Test
    public void splitKeyboardKeepsFollowing() throws Exception {
        LegacySwipeSettingsTest.MapSp sp = new LegacySwipeSettingsTest.MapSp();
        sp.m.put(Prefs.KOREAN_LAYOUT, Prefs.KOREAN_LAYOUT_D7);
        Prefs land = view(sp, Prefs.PROFILE_LANDSCAPE);
        sp.m.put(land.splitKey(), true);
        sp.m.put(Prefs.balancedKeyFor(Prefs.BALANCED_VOWEL_WIDTH, Prefs.PROFILE_LANDSCAPE, true, Prefs.KOREAN_LAYOUT_D7), 70);
        view(sp, Prefs.PROFILE_PORTRAIT).migrateProfileSides();
        Prefs l = view(sp, Prefs.PROFILE_LANDSCAPE);
        assertFalse(sp.contains(l.detailKeyOf(Prefs.BOARD_ENGLISH)));
        assertTrue(l.englishView().profileSplit());
        assertEquals(70, l.englishView().balancedVowelWidth());
        assertTrue(l.symbolsView().profileSplit());
    }

    /**
     * 예전 '영어 자판도 균형 배열'은 두벌식에서 한글 자판이 균형 배열일 때만 영어 자판을 나눴고, 기호 자판은 나뉘지 않았다:
     * 따라가기로는 달라지는 자판에만 '사용 안 함'을 적어 둔다.
     */
    @Test
    public void oldBalancedLayoutKeepsItsLook() throws Exception {
        LegacySwipeSettingsTest.MapSp shown = new LegacySwipeSettingsTest.MapSp();   // 두벌식 + 영어 자판도 균형 배열
        shown.m.put(Prefs.KOREAN_LAYOUT, Prefs.KOREAN_LAYOUT_DUBEOLSIK);
        shown.m.put(Prefs.BALANCED_LAYOUT, true);
        shown.m.put(Prefs.ENGLISH_BALANCED, true);
        view(shown, Prefs.PROFILE_PORTRAIT).migrateProfileSides();
        Prefs p = view(shown, Prefs.PROFILE_PORTRAIT);
        assertEquals(Prefs.DETAIL_FOLLOW, p.detailModeOf(Prefs.BOARD_ENGLISH));
        assertTrue(p.englishBalanced());
        assertEquals(Prefs.DETAIL_NONE, p.detailModeOf(Prefs.BOARD_SYMBOLS));
        assertEquals(Prefs.DETAIL_NONE, view(shown, Prefs.PROFILE_LANDSCAPE).detailModeOf(Prefs.BOARD_SYMBOLS));   // 키보드마다

        LegacySwipeSettingsTest.MapSp hidden = new LegacySwipeSettingsTest.MapSp();   // 단모음 균형 배열: 영어 자판은 나뉘지 않았다
        hidden.m.put(Prefs.BALANCED_LAYOUT, true);
        hidden.m.put(Prefs.ENGLISH_BALANCED, true);
        view(hidden, Prefs.PROFILE_PORTRAIT).migrateProfileSides();
        assertFalse(view(hidden, Prefs.PROFILE_PORTRAIT).englishBalanced());
        assertFalse(view(hidden, Prefs.PROFILE_LANDSCAPE).englishBalanced());
        assertEquals(Prefs.DETAIL_NONE, view(hidden, Prefs.PROFILE_PORTRAIT).detailModeOf(Prefs.BOARD_SYMBOLS));

        LegacySwipeSettingsTest.MapSp plain = new LegacySwipeSettingsTest.MapSp();   // 균형 배열을 쓰지 않았다: 따라가기 그대로
        view(plain, Prefs.PROFILE_PORTRAIT).migrateProfileSides();
        assertEquals(Prefs.DETAIL_FOLLOW, view(plain, Prefs.PROFILE_PORTRAIT).detailModeOf(Prefs.BOARD_ENGLISH));
        assertEquals(Prefs.DETAIL_FOLLOW, view(plain, Prefs.PROFILE_PORTRAIT).detailModeOf(Prefs.BOARD_SYMBOLS));
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
