package com.alternative_studios.newswipe;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class AccentContrastTest {
    private static void assertReadable(int c, boolean dark) {
        int bg = dark ? 0xFF121316 : 0xFFF4F5F8;
        int card = dark ? 0xFF1E1F23 : 0xFFFFFFFF;
        assertTrue(AppTheme.contrast(c, bg) >= AppTheme.MIN_CONTRAST);
        assertTrue(AppTheme.contrast(c, card) >= AppTheme.MIN_CONTRAST);
    }

    @Test
    public void blackIsLightenedInDarkMode() {
        int c = AppTheme.readable(0xFF000000, true);
        assertReadable(c, true);
        assertTrue(AppTheme.luminance(c) > AppTheme.luminance(0xFF000000));
    }

    @Test
    public void whiteIsDarkenedInLightMode() {
        int c = AppTheme.readable(0xFFFFFFFF, false);
        assertReadable(c, false);
        assertTrue(AppTheme.luminance(c) < AppTheme.luminance(0xFFFFFFFF));
    }

    @Test
    public void visibleColorsStayAsChosen() {
        assertEquals(0xFF000000, AppTheme.readable(0xFF000000, false));
        assertEquals(0xFFFFFFFF, AppTheme.readable(0xFFFFFFFF, true));
        assertEquals(0xFFE53935, AppTheme.readable(0xFFE53935, true));
    }
}
