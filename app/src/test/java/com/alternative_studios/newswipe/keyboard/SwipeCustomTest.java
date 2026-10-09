package com.alternative_studios.newswipe.keyboard;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

public class SwipeCustomTest {
    @Test
    public void defaultsMatchStandardSwipes() {
        assertEquals("ㄲ", SwipeCustom.get(null, true, "ㄱ", Key.SWIPE_DOWN));
        assertEquals("ㅛ", SwipeCustom.get(null, true, "ㅗ", Key.SWIPE_DOWN));
        assertEquals("ㄺ", SwipeCustom.get(null, true, "ㄹ", Key.SWIPE_UP));
        assertEquals("ㄻ", SwipeCustom.get(null, true, "ㄹ", Key.SWIPE_LEFT));
        assertEquals("ㅄ", SwipeCustom.get(null, true, "ㅂ", Key.SWIPE_RIGHT));
        assertNull(SwipeCustom.get(null, true, "ㅁ", Key.SWIPE_UP));
    }

    @Test
    public void englishAndPunctuationHaveNoDefaults() {
        assertNull(SwipeCustom.get(null, false, "q", Key.SWIPE_DOWN));
        assertNull(SwipeCustom.get(null, true, ".", Key.SWIPE_UP));
    }

    @Test
    public void vowelDefaultsAreSharedByAllLayouts() {
        assertEquals("ㅑ", SwipeCustom.get(null, true, "ㅏ", Key.SWIPE_DOWN));
        assertEquals("ㅛ", SwipeCustom.get(null, true, "ㅗ", Key.SWIPE_DOWN));
        assertNull(SwipeCustom.get(null, true, "ㅣ", Key.SWIPE_DOWN));
        assertNull(SwipeCustom.get(null, true, "ㅏ", Key.SWIPE_LEFT));
        assertEquals("ㅙ", SwipeCustom.get(null, true, "ㅗ", Key.SWIPE_RIGHT));   // 조합형 밀기는 모든 배열 공통
        assertEquals("ㅙ", SwipeCustom.defaultFor(true, false, "ㅗ", Key.SWIPE_RIGHT));
        assertEquals("ㅞ", SwipeCustom.defaultFor(true, false, "ㅜ", Key.SWIPE_RIGHT));
        assertEquals("ㅚ", SwipeCustom.defaultFor(true, false, "ㅗ", Key.SWIPE_UP));
        assertEquals("ㅟ", SwipeCustom.defaultFor(true, false, "ㅜ", Key.SWIPE_UP));
    }

    @Test
    public void newSwipeDefaultsPutConsonantsOnSwipeRight() {
        assertEquals("ㅍ", SwipeCustom.defaultFor(true, true, "ㅂ", Key.SWIPE_RIGHT));
        assertEquals("ㅊ", SwipeCustom.defaultFor(true, true, "ㅈ", Key.SWIPE_RIGHT));
        assertEquals("ㅌ", SwipeCustom.defaultFor(true, true, "ㄷ", Key.SWIPE_RIGHT));
        assertEquals("ㅋ", SwipeCustom.defaultFor(true, true, "ㄱ", Key.SWIPE_RIGHT));
        assertNull(SwipeCustom.defaultFor(true, true, "ㅂ", Key.SWIPE_UP));   // 위쪽은 비어 있다
        assertEquals("ㄲ", SwipeCustom.defaultFor(true, true, "ㄱ", Key.SWIPE_DOWN));   // 쌍자음은 그대로
        assertEquals("ㄺ", SwipeCustom.defaultFor(true, true, "ㄹ", Key.SWIPE_UP));      // 다른 겹받침도 그대로
        assertEquals("ㅀ", SwipeCustom.defaultFor(true, true, "ㄹ", Key.SWIPE_RIGHT));
        assertEquals("ㅛ", SwipeCustom.defaultFor(true, true, "ㅗ", Key.SWIPE_DOWN));
        assertEquals("ㅘ", SwipeCustom.defaultFor(true, true, "ㅗ", Key.SWIPE_LEFT));   // 조합형 이중모음도 모든 배열 공통
        assertNull(SwipeCustom.defaultFor(true, false, "ㅈ", Key.SWIPE_RIGHT));   // 8열 단모음은 그대로
        assertEquals("ㄳ", SwipeCustom.defaultFor(true, false, "ㄱ", Key.SWIPE_RIGHT));
        assertNull(KeyboardLayout.nsRight("ㅅ"));
    }
}
