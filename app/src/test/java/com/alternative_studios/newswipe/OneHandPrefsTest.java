package com.alternative_studios.newswipe;

import android.content.SharedPreferences;

import org.junit.Test;

import java.lang.reflect.Constructor;

import static org.junit.Assert.assertEquals;

/** 한 손 모드 값이 화면 방향(프로필)마다 따로이고, 한 손 모드에서는 키보드 전체 여백을 쓰지 않는지. */
public class OneHandPrefsTest {

    private static Prefs view(LegacySwipeSettingsTest.MapSp sp, String profile) throws Exception {
        Constructor<Prefs> c = Prefs.class.getDeclaredConstructor(SharedPreferences.class, boolean.class, String.class,
                boolean.class);
        c.setAccessible(true);
        return c.newInstance(sp, false, profile, false);
    }

    @Test
    public void valuesArePerProfileWithFallbacks() throws Exception {
        LegacySwipeSettingsTest.MapSp sp = new LegacySwipeSettingsTest.MapSp();
        sp.m.put(Prefs.ONE_HAND_HEIGHT, 70);
        sp.m.put(Prefs.ONE_HAND_LIFT, 40);
        Prefs port = view(sp, Prefs.PROFILE_PORTRAIT), land = view(sp, Prefs.PROFILE_LANDSCAPE);
        Prefs largePort = view(sp, Prefs.PROFILE_LARGE_PORTRAIT), largeLand = view(sp, Prefs.PROFILE_LARGE_LANDSCAPE);
        // 가로 모드·폴더블 내부 화면은 정하기 전에는 세로 모드 값을 따른다
        // (자판 폭만 가로 모드 기본값 50%, 폴더블 내부 화면 세로·가로 기본값 40%·35%).
        assertEquals(70, land.oneHandHeightForProfile());
        assertEquals(40, largeLand.oneHandBottomForProfile());
        assertEquals(70, largeLand.oneHandHeightForProfile());
        assertEquals(Prefs.ONE_HAND_WIDTH_DEFAULT, port.oneHandWidthForProfile());
        assertEquals(Prefs.ONE_HAND_WIDTH_LAND_DEFAULT, land.oneHandWidthForProfile());
        assertEquals(Prefs.ONE_HAND_WIDTH_LARGE_LAND_DEFAULT, largeLand.oneHandWidthForProfile());
        assertEquals(Prefs.ONE_HAND_WIDTH_LARGE_PORT_DEFAULT, largePort.oneHandWidthForProfile());
        assertEquals(40, Prefs.ONE_HAND_WIDTH_LARGE_PORT_DEFAULT);
        // 세로 모드에서 자판 폭을 정해도 폴더블 내부 화면 세로는 자기 기본값.
        sp.m.put(port.oneHandWidthKey(), 70);
        assertEquals(Prefs.ONE_HAND_WIDTH_LARGE_PORT_DEFAULT, largePort.oneHandWidthForProfile());
        sp.m.remove(port.oneHandWidthKey());
        // 가로 모드에서 정하면 가로 모드만 바뀐다: 높이가 넉넉한 폴더블 내부 화면 가로는 가로 모드 값을 따르지 않는다.
        sp.m.put(land.oneHandBottomKey(), 12);
        sp.m.put(land.oneHandHeightKey(), 80);
        sp.m.put(land.oneHandWidthKey(), 40);
        assertEquals(12, land.oneHandBottomForProfile());
        assertEquals(40, largeLand.oneHandBottomForProfile());
        assertEquals(70, largeLand.oneHandHeightForProfile());
        assertEquals(Prefs.ONE_HAND_WIDTH_LARGE_LAND_DEFAULT, largeLand.oneHandWidthForProfile());
        assertEquals(40, port.oneHandBottomForProfile());
        assertEquals(40, largePort.oneHandBottomForProfile());
        assertEquals(80, land.oneHandHeightForProfile());
        assertEquals(70, port.oneHandHeightForProfile());
        // 폴더블 내부 화면은 따로 정할 수 있다 (예전 폴더블 자판 폭 키를 그대로 쓴다).
        assertEquals("large_one_hand_width_port", largePort.oneHandWidthKey());
        sp.m.put(largePort.oneHandBottomKey(), 8);
        assertEquals(8, largePort.oneHandBottomForProfile());
        assertEquals(40, port.oneHandBottomForProfile());
        assertEquals(10, land.oneHandWidthMin());   // 모든 키보드가 10%까지
        assertEquals(10, port.oneHandWidthMin());
        sp.m.put(port.oneHandWidthKey(), 10);
        assertEquals(10, port.oneHandWidthForProfile());
    }

    @Test
    public void oneHandViewHasNoKeyboardPadding() throws Exception {
        LegacySwipeSettingsTest.MapSp sp = new LegacySwipeSettingsTest.MapSp();
        sp.m.put(Prefs.PAD_LEFT, 10);
        sp.m.put(Prefs.PAD_BOTTOM, 20);
        for (String profile : new String[]{Prefs.PROFILE_PORTRAIT, Prefs.PROFILE_LANDSCAPE}) {
            Prefs p = view(sp, profile);
            assertEquals(10, p.padLeftDp());
            assertEquals(20, p.padBottomDp());
            Prefs one = p.oneHandView();
            assertEquals(0, one.padLeftDp());
            assertEquals(0, one.padRightDp());
            assertEquals(0, one.padTopDp());
            assertEquals(0, one.padBottomDp());
        }
    }

    @Test
    public void resetClearsEveryProfile() throws Exception {
        LegacySwipeSettingsTest.MapSp sp = new LegacySwipeSettingsTest.MapSp();
        sp.m.put(Prefs.ONE_HAND_LIFT, 40);
        sp.m.put("one_hand_lift_land", 12);
        sp.m.put("large_one_hand_height_port", 70);
        view(sp, Prefs.PROFILE_PORTRAIT).resetSection("onehand");
        assertEquals(0, sp.m.size());
    }
}
