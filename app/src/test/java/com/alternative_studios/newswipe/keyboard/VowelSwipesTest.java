package com.alternative_studios.newswipe.keyboard;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class VowelSwipesTest {
    @Test
    public void downIsIotized() {
        assertEquals("ㅑ", VowelSwipes.iotizedFor('ㅏ'));
        assertEquals("ㅕ", VowelSwipes.iotizedFor('ㅓ'));
        assertEquals("ㅛ", VowelSwipes.iotizedFor('ㅗ'));
        assertEquals("ㅠ", VowelSwipes.iotizedFor('ㅜ'));
        assertEquals("ㅒ", VowelSwipes.iotizedFor('ㅐ'));
        assertEquals("ㅖ", VowelSwipes.iotizedFor('ㅔ'));
        assertNull(VowelSwipes.iotizedFor('ㅣ'));
        assertNull(VowelSwipes.iotizedFor('ㄱ'));
    }

    @Test
    public void compoundDefaults() {
        assertEquals("ㅝ", VowelSwipes.compoundDefault('ㅜ', Key.SWIPE_LEFT));
        assertEquals("ㅟ", VowelSwipes.compoundDefault('ㅜ', Key.SWIPE_UP));
        assertEquals("ㅞ", VowelSwipes.compoundDefault('ㅜ', Key.SWIPE_RIGHT));
        assertEquals("ㅘ", VowelSwipes.compoundDefault('ㅗ', Key.SWIPE_LEFT));
        assertEquals("ㅚ", VowelSwipes.compoundDefault('ㅗ', Key.SWIPE_UP));
        assertEquals("ㅙ", VowelSwipes.compoundDefault('ㅗ', Key.SWIPE_RIGHT));
        assertEquals("ㅢ", VowelSwipes.compoundDefault('ㅡ', Key.SWIPE_UP));
        assertNull(VowelSwipes.compoundDefault('ㅜ', Key.SWIPE_DOWN));   // 아래는 ㅠ 자리
        assertNull(VowelSwipes.compoundDefault('ㅏ', Key.SWIPE_LEFT));
        assertEquals("ㅝ", VowelSwipes.compoundFor(null, 'ㅜ', Key.SWIPE_LEFT));
        assertNull(VowelSwipes.compoundFor(null, 'ㅜ', Key.SWIPE_DOWN));
    }

    @Test
    public void optionsContainTheVowel() {
        assertEquals("ㅝㅞㅟ", VowelSwipes.optionsFor('ㅜ'));
        assertEquals("ㅘㅙㅚ", VowelSwipes.optionsFor('ㅗ'));
        assertEquals("ㅚㅟㅢ", VowelSwipes.optionsFor('ㅣ'));
        assertEquals("", VowelSwipes.optionsFor('ㄱ'));
        for (char v : VowelSwipes.SOURCES.toCharArray()) {
            for (int dir : new int[]{Key.SWIPE_UP, Key.SWIPE_LEFT, Key.SWIPE_RIGHT}) {
                String d = VowelSwipes.compoundDefault(v, dir);
                if (d != null) assertTrue(v + " " + d, VowelSwipes.optionsFor(v).contains(d));
            }
        }
    }

    @Test
    public void defaultsAreSharedByAllLayouts() {
        // 모든 한글 배열: 아래는 ㅣ계, 위·왼쪽·오른쪽은 조합형.
        assertEquals("ㅛ", VowelSwipes.defaultFor('ㅗ', Key.SWIPE_DOWN));
        assertEquals("ㅠ", VowelSwipes.defaultFor('ㅜ', Key.SWIPE_DOWN));
        assertEquals("ㅘ", VowelSwipes.defaultFor('ㅗ', Key.SWIPE_LEFT));
        assertEquals("ㅢ", VowelSwipes.defaultFor('ㅡ', Key.SWIPE_UP));
        assertNull(VowelSwipes.defaultFor('ㅏ', Key.SWIPE_LEFT));
    }

    @Test
    public void iotizedCheck() {
        assertTrue(VowelSwipes.isIotized("ㅑ"));
        assertFalse(VowelSwipes.isIotized("ㅘ"));
    }
}
