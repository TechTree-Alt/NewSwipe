package com.alternative_studios.newswipe.keyboard;

import android.content.Context;

import com.alternative_studios.newswipe.AppTheme;

/** 키보드 색상. 기기의 다크 모드 설정을 따른다. */
public final class KeyboardTheme {
    public final boolean dark;
    public final int background;
    public final int key;
    public final int keyPressed;
    public final int functionKey;
    public final int accent;
    public final int onAccent;
    public final int text;
    public final int hint;
    public final int popup;
    public final int popupSelected;
    public final int divider;

    private KeyboardTheme(boolean dark, int accentColor, int onAccentColor) {
        this.dark = dark;
        if (dark) {
            background = 0xFF1B1C1F;
            key = 0xFF3A3B40;
            keyPressed = 0xFF55575D;
            functionKey = 0xFF2A2B2F;
            text = 0xFFECECEF;
            hint = 0xFF9A9CA3;
            popup = 0xFF4A4C52;
            divider = 0xFF33343A;
        } else {
            background = 0xFFE6E8EC;
            key = 0xFFFFFFFF;
            keyPressed = 0xFFD2D6DC;
            functionKey = 0xFFCDD1D8;
            text = 0xFF1D1E21;
            hint = 0xFF7D828B;
            popup = 0xFFFFFFFF;
            divider = 0xFFD5D8DE;
        }
        accent = accentColor;
        onAccent = onAccentColor;
        popupSelected = accentColor;
    }

    private static KeyboardTheme cached;

    /** 설정(화면 모드, 강조 색)이 바뀌지 않았다면 같은 객체를 돌려준다. */
    public static synchronized KeyboardTheme of(Context context) {
        AppTheme t = AppTheme.of(context);
        if (cached == null || cached.dark != t.dark || cached.accent != t.accent || cached.onAccent != t.onAccent) {
            cached = new KeyboardTheme(t.dark, t.accent, t.onAccent);
        }
        return cached;
    }
}
