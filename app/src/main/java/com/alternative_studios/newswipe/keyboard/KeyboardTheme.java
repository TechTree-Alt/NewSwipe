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
    /** Newmorphism 모드: 키·버튼이 바탕에서 솟아오르고 누르면 안으로 들어간다 (ui.Neu). */
    public final boolean neu;
    /**
     * 글자 키(라이트 모드는 흰색)·기능키(회색) 면 그라데이션과 그림자를 맞추는 값 (% 단위, 인덱스는 T_*).
     * 그라데이션은 모드마다 고정({@link #LIGHT_TUNE}, {@link #DARK_TUNE})이고, 그림자 강도(뒤의 둘)만 라이트 모드의 설정 값이다.
     */
    public final int[] neuTune;
    public static final int T_KEY_START = 0, T_KEY_END = 1, T_FN_SHIFT = 2, T_FN_START = 3, T_FN_END_TINT = 4,
            T_FN_END = 5, T_LIGHT = 6, T_SHADOW = 7;
    /** 라이트 모드 그라데이션 값 (뒤의 둘, 그림자 강도는 설정에서 채운다). */
    private static final int[] LIGHT_TUNE = {0, 50, 5, 5, 0, 50};
    /** 다크 모드 값: 글자 키 면(왼쪽 위 20%, 끝 100%), 기능키 면(-30%, 20%, 0%, 100%), 밝은 그림자 12%, 어두운 그림자 80%. */
    private static final int[] DARK_TUNE = {20, 100, -30, 20, 0, 100, 12, 80};

    /** neuTune의 i번째 값을 0~1 비율(회색 면 색은 -1~1)로. */
    public float tune(int i) {
        return neuTune[i] / 100f;
    }
    /** Newmorphism 그림자: 왼쪽 위 밝은 그림자, 오른쪽 아래 어두운 그림자. */
    public final int neuLight;
    public final int neuDark;
    /** 회색 기능키 면의 어두운 그림자 (면이 그림자로 번져 보이지 않게 neuDark보다 진하다). */
    public final int neuDarkFunction;

    private KeyboardTheme(boolean dark, int accentColor, int onAccentColor, boolean neu, int[] tune) {
        this.dark = dark;
        this.neu = neu;
        neuTune = tune;
        int light = Math.round(tune[T_LIGHT] * 2.55f), shadow = Math.round(tune[T_SHADOW] * 2.55f);
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
            neuLight = light << 24 | 0xFFFFFF;
            neuDark = shadow << 24;
            neuDarkFunction = shadow << 24;
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
            neuLight = light << 24 | 0xFFFFFF;
            neuDark = shadow << 24 | 0xA0A8B6;
            neuDarkFunction = Math.min(255, Math.round(shadow * 1.29f)) << 24 | 0x808A9A;   // 회색 면은 기본 강도의 1.29배
        }
        accent = accentColor;
        onAccent = onAccentColor;
        popupSelected = accentColor;
    }

    private static KeyboardTheme cached;

    /** 설정(화면 모드, 강조 색, Newmorphism 모드)이 바뀌지 않았다면 같은 객체를 돌려준다. */
    public static synchronized KeyboardTheme of(Context context) {
        AppTheme t = AppTheme.of(context);
        Prefs prefs = new Prefs(context);
        boolean neu = prefs.newmorphism();
        int[] tune = t.dark ? DARK_TUNE : new int[]{LIGHT_TUNE[0], LIGHT_TUNE[1], LIGHT_TUNE[2], LIGHT_TUNE[3], LIGHT_TUNE[4],
                LIGHT_TUNE[5], prefs.neuLightLight(), prefs.neuLightShadow()};
        if (cached == null || cached.dark != t.dark || cached.accent != t.accent || cached.onAccent != t.onAccent
                || cached.neu != neu || !java.util.Arrays.equals(cached.neuTune, tune)) {
            cached = new KeyboardTheme(t.dark, t.accent, t.onAccent, neu, tune);
        }
        return cached;
    }
}
