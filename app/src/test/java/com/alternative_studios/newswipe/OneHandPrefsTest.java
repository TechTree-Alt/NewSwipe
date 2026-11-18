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

    @Test
    public void floatingKeepsPositionPerProfile() throws Exception {
        LegacySwipeSettingsTest.MapSp sp = new LegacySwipeSettingsTest.MapSp();
        Prefs port = view(sp, Prefs.PROFILE_PORTRAIT), land = view(sp, Prefs.PROFILE_LANDSCAPE);
        Prefs largeLand = view(sp, Prefs.PROFILE_LARGE_LANDSCAPE);
        port.setOneHand(Prefs.ONE_HAND_FLOAT);
        assertEquals(Prefs.ONE_HAND_FLOAT, port.oneHand());
        assertEquals(Prefs.ONE_HAND_FLOAT, port.oneHand("land"));   // 방향마다 기억하지 않으면 모든 화면이 같다
        // 떠 있는 키보드는 버튼으로 켤 한 손 모드 쪽으로 기억하지 않는다.
        port.setOneHand(Prefs.ONE_HAND_LEFT);
        port.setOneHand(Prefs.ONE_HAND_FLOAT);
        assertEquals(Prefs.ONE_HAND_LEFT, port.oneHandLast());
        // 방향마다 기억하지 않으면 자리는 모든 화면이 함께 쓰고, 정하기 전에는 기본값이다.
        assertEquals(Prefs.FLOAT_POS_X_DEFAULT, land.floatX("land"));
        assertEquals(Prefs.FLOAT_POS_LIFT_DEFAULT, land.floatLiftDp("land"));
        port.setFloatPos("port", 1200, -5);
        assertEquals(1000, port.floatX("port"));
        assertEquals(0, port.floatLiftDp("port"));
        assertEquals(1000, land.floatX("land"));
        // 방향마다 기억하면 화면마다 따로이고, 그 화면에서 정하기 전에는 함께 쓰던 값을 따른다.
        sp.m.put(Prefs.FLOAT_PER_SCREEN, true);
        land.setFloatPos("land", 250, 300);
        assertEquals(250, land.floatX("land"));
        assertEquals(300, land.floatLiftDp("land"));
        assertEquals(1000, port.floatX("port"));
        assertEquals(1000, largeLand.floatX("large_land"));
        largeLand.setFloatPos("large_land", 10, 10);
        assertEquals(10, largeLand.floatX("large_land"));
        assertEquals(250, land.floatX("land"));
        // 실험실 되돌리기는 떠 있는 키보드 설정과 자리를 모두 지운다.
        port.resetSection("lab");
        assertEquals(false, port.floatPerScreen());
        assertEquals(Prefs.FLOAT_POS_X_DEFAULT, port.floatX("port"));
        sp.m.put(Prefs.FLOAT_PER_SCREEN, true);
        assertEquals(Prefs.FLOAT_POS_X_DEFAULT, land.floatX("land"));
        assertEquals(Prefs.FLOAT_POS_X_DEFAULT, largeLand.floatX("large_land"));
    }

    @Test
    public void floatingSizeIsSeparateFromOneHandedModeAndPerScreen() throws Exception {
        LegacySwipeSettingsTest.MapSp sp = new LegacySwipeSettingsTest.MapSp();
        Prefs port = view(sp, Prefs.PROFILE_PORTRAIT), land = view(sp, Prefs.PROFILE_LANDSCAPE);
        // 정하기 전에는 그 프로필의 한 손 모드 값을 따른다.
        sp.m.put(port.oneHandWidthKey(), 70);
        sp.m.put(port.oneHandHeightKey(), 80);
        assertEquals(70, port.floatWidth("port"));
        assertEquals(80, port.floatHeight("port"));
        assertEquals(Prefs.ONE_HAND_WIDTH_LAND_DEFAULT, land.floatWidth("land"));
        // 떠 있는 키보드에서 정하면 떠 있는 키보드만 바뀌고 한 손 모드 값은 그대로다. 자리도 함께 저장한다.
        port.setFloatGeometry("port", 55, 120, 300, 100);
        assertEquals(55, port.floatWidth("port"));
        assertEquals(120, port.floatHeight("port"));
        assertEquals(300, port.floatX("port"));
        assertEquals(100, port.floatLiftDp("port"));
        assertEquals(70, port.oneHandWidthForProfile());
        assertEquals(80, port.oneHandHeightForProfile());
        // 방향마다 기억하지 않으면 모든 화면이 같은 크기다.
        assertEquals(55, land.floatWidth("land"));
        // 방향마다 기억하면 화면마다 따로이고, 범위를 넘는 값은 줄인다.
        sp.m.put(Prefs.FLOAT_PER_SCREEN, true);
        land.setFloatGeometry("land", 5, 500, 0, 0);
        assertEquals(Prefs.FLOAT_WIDTH_MIN, land.floatWidth("land"));
        assertEquals(Prefs.FLOAT_HEIGHT_MAX, land.floatHeight("land"));
        assertEquals(55, port.floatWidth("port"));
        assertEquals(120, port.floatHeight("large_port"));   // 정하지 않은 화면은 함께 쓰던 값
    }

    @Test
    public void floatingStateCanBeRememberedPerScreen() throws Exception {
        LegacySwipeSettingsTest.MapSp sp = new LegacySwipeSettingsTest.MapSp();
        Prefs p = view(sp, Prefs.PROFILE_PORTRAIT);
        // 켜 둔 채로 '방향마다 기억'을 켜면 지금 화면에서만 떠 있다.
        p.setOneHand("land", Prefs.ONE_HAND_FLOAT);
        p.setFloatPerScreen("land", true);
        assertEquals(Prefs.ONE_HAND_FLOAT, p.oneHand("land"));
        assertEquals(Prefs.ONE_HAND_OFF, p.oneHand("port"));
        // 폴더블 내부 화면에서 켜도 외부 화면은 그대로다.
        p.setOneHand("large_port", Prefs.ONE_HAND_FLOAT);
        assertEquals(Prefs.ONE_HAND_FLOAT, p.oneHand("large_port"));
        assertEquals(Prefs.ONE_HAND_OFF, p.oneHand("port"));
        // 외부 화면에서 한 손 모드를 켜면, 떠 있지 않은 화면은 한 손 모드를 쓰고 떠 있는 화면은 그대로 떠 있다.
        p.setOneHand("port", Prefs.ONE_HAND_LEFT);
        assertEquals(Prefs.ONE_HAND_LEFT, p.oneHand("port"));
        assertEquals(Prefs.ONE_HAND_FLOAT, p.oneHand("land"));
        // 떠 있는 화면에서 끄면 그 화면만 꺼진다.
        p.setOneHand("land", Prefs.ONE_HAND_OFF);
        assertEquals(Prefs.ONE_HAND_OFF, p.oneHand("land"));
        assertEquals(Prefs.ONE_HAND_FLOAT, p.oneHand("large_port"));
        // '방향마다 기억'을 끄면 지금 화면의 상태를 모든 화면이 쓴다.
        p.setFloatPerScreen("large_port", false);
        assertEquals(Prefs.ONE_HAND_FLOAT, p.oneHand("port"));
        assertEquals(Prefs.ONE_HAND_FLOAT, p.oneHand("land"));
    }
}
