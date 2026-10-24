package com.alternative_studios.newswipe.keyboard;

import android.content.Context;

import com.alternative_studios.newswipe.AppTheme;
import com.alternative_studios.newswipe.Prefs;

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
    /**
     * 면을 '밝게' 할 때 섞는 색. 라이트 모드는 키 면보다 밝은 바탕색, 다크 모드는 바탕색이 키 면보다 어두워서
     * 그쪽으로 섞으면 오히려 어두워지므로 흰색이다.
     */
    public final int lift;
    /** Neumorphism 모드: 키·버튼이 바탕에서 솟아오르고 누르면 안으로 들어간다 (ui.Neu). */
    public final boolean neu;
    /**
     * 글자 키(라이트 모드는 흰색)·기능키(회색) 면 그라데이션을 맞추는 값 (% 단위, 인덱스는 T_*).
     * 모드마다 고정이다 ({@link #LIGHT_TUNE}, {@link #DARK_TUNE}).
     */
    public final int[] neuTune;
    public static final int T_KEY_START = 0, T_KEY_END = 1, T_FN_SHIFT = 2, T_FN_START = 3, T_FN_END_TINT = 4,
            T_FN_END = 5;
    /** 라이트 모드 값: 글자 키 면(왼쪽 위 0%, 끝 50%), 기능키 면(5%, 5%, 0%, 50%). */
    private static final int[] LIGHT_TUNE = {0, 50, 5, 5, 0, 50};
    /** 다크 모드 값: 글자 키 면(왼쪽 위 20%, 끝 100%), 기능키 면(-30%, 20%, 0%, 100%). */
    private static final int[] DARK_TUNE = {20, 100, -30, 20, 0, 100};
    /** 그림자 강도 % — {흰색(글자) 키 밝은·어두운, 회색 키 밝은·어두운, 강조색 키 밝은·어두운}. */
    public final int[] neuShadow;
    private static final int[] LIGHT_SHADOW = {100, 45, 75, 55, 100, 75};
    /** 다크 모드의 강조색 면은 아주 밝아 밝은 그림자가 눌려 작아 보이므로 다른 키의 두 배쯤이다. */
    private static final int[] DARK_SHADOW = {13, 80, 13, 80, 27, 80};

    /** neuTune의 i번째 값을 0~1 비율(회색 면 색은 -1~1)로. */
    public float tune(int i) {
        return neuTune[i] / 100f;
    }
    /** Neumorphism 그림자: 왼쪽 위 밝은 그림자, 오른쪽 아래 어두운 그림자. 흰색(글자) 키 값이고 눌린 면 안쪽에도 쓴다. */
    public final int neuLight;
    public final int neuDark;
    /** 회색 기능키 면의 그림자. */
    public final int neuLightFunction, neuDarkFunction;
    /** 강조색 키 면의 그림자. */
    public final int neuLightAccent, neuDarkAccent;

    private KeyboardTheme(boolean dark, int accentColor, int onAccentColor, boolean neu, int[] tune, int[] shadows) {
        this.dark = dark;
        this.neu = neu;
        neuTune = tune;
        neuShadow = shadows;
        // 밝은 그림자는 흰색, 어두운 그림자는 다크 모드는 검은색이고 라이트 모드는 푸른 회색 (회색 키는 더 어두운 회색).
        int lo = dark ? 0 : 0xA0A8B6, loFn = dark ? 0 : 0x808A9A;
        neuLight = alpha(shadows[0]) | 0xFFFFFF;
        neuDark = alpha(shadows[1]) | lo;
        neuLightFunction = alpha(shadows[2]) | 0xFFFFFF;
        neuDarkFunction = alpha(shadows[3]) | loFn;
        neuLightAccent = alpha(shadows[4]) | 0xFFFFFF;
        neuDarkAccent = alpha(shadows[5]) | lo;
        if (dark) {
            background = 0xFF1B1C1F;
            key = 0xFF3A3B40;
            keyPressed = 0xFF55575D;
            functionKey = 0xFF2A2B2F;
            text = 0xFFECECEF;
            hint = 0xFF9A9CA3;
            popup = 0xFF4A4C52;
            divider = 0xFF33343A;
            lift = 0xFFFFFFFF;
        } else {
            background = 0xFFE6E8EC;
            key = 0xFFFFFFFF;
            keyPressed = 0xFFD2D6DC;
            functionKey = 0xFFCDD1D8;
            text = 0xFF1D1E21;
            hint = 0xFF7D828B;
            popup = 0xFFFFFFFF;
            divider = 0xFFD5D8DE;
            lift = 0xFFE6E8EC;
        }
        accent = accentColor;
        onAccent = onAccentColor;
        popupSelected = accentColor;
    }

    /** 강도 %를 색의 알파(맨 앞 바이트)로. */
    private static int alpha(int percent) {
        return Math.round(percent * 2.55f) << 24;
    }

    private static KeyboardTheme cached;

    /** 설정(화면 모드, 강조 색, Neumorphism 모드)이 바뀌지 않았다면 같은 객체를 돌려준다. */
    public static synchronized KeyboardTheme of(Context context) {
        AppTheme t = AppTheme.of(context);
        Prefs prefs = new Prefs(context);
        boolean neu = prefs.neumorphism();
        int[] tune = t.dark ? DARK_TUNE : LIGHT_TUNE;
        int[] shadows = t.dark ? DARK_SHADOW : LIGHT_SHADOW;
        if (cached == null || cached.dark != t.dark || cached.accent != t.accent || cached.onAccent != t.onAccent
                || cached.neu != neu || !java.util.Arrays.equals(cached.neuTune, tune)
                || !java.util.Arrays.equals(cached.neuShadow, shadows)) {
            cached = new KeyboardTheme(t.dark, t.accent, t.onAccent, neu, tune, shadows);
        }
        return cached;
    }
}
