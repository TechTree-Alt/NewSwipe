package com.alternative_studios.newswipe;

import android.content.Context;
import android.media.AudioManager;
import android.os.Build;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.os.VibratorManager;
import android.view.HapticFeedbackConstants;
import android.view.View;

import com.alternative_studios.newswipe.keyboard.Key;

/** 키 진동과 키음. */
final class Feedback {
    private final AudioManager audio;
    private final Vibrator vibrator;
    /** 진동기 유무는 바뀌지 않으므로 한 번만 확인한다. */
    private final boolean hasVibrator;
    /** 짧고 또렷한 기본 진동(primitive)을 세기만 바꿔 울릴 수 있는 기기인가 (Android 11부터, 기기마다 다르다). */
    private final boolean primitives;
    /** 미리 정해진 효과의 단계 ({@link #fixed}): 1 틱, 2 클릭, 3 강한 클릭. */
    static final int WEAK = 1, BASE = 2, STRONG = 3;
    /**
     * 진동 방식 (Prefs.vibrateStyle 등): 0 = 틱(EFFECT_TICK), 1 = 클릭(EFFECT_CLICK), 2 = 강한 클릭(EFFECT_HEAVY_CLICK),
     * 3 = 사용자 설정, 4 = 시스템 기본. 틱·클릭·강한 클릭은 단계 1~3과 같은 진동이다 (방식 + 1 = 단계).
     * 사용자 설정은 primitive를 쓸 수 있으면 primitive 틱·클릭 중 하나를 고른 세기로, 아니면(일반 키만) 길이·강도의 한 번 진동.
     */
    static final int STYLE_NONE = -1, STYLE_TICK = 0, STYLE_CLICK = 1, STYLE_HEAVY_CLICK = 2, STYLE_CUSTOM = 3, STYLE_SYSTEM = 4;
    /** 사용자 설정의 primitive (Prefs.vibratePrimitive 등): 0 = PRIMITIVE_TICK, 1 = PRIMITIVE_CLICK. */
    static final int PRIMITIVE_TICK = 0, PRIMITIVE_CLICK = 1;
    /**
     * 진동을 쓰는 곳: 일반 키, 반복 입력·연속 지우기, 밀기·길게 누르기, 그리고 키 종류별 진동
     * (지우기·스페이스·엔터·Shift·기호·언어 전환·도구 막대, Prefs.VIBRATE_KEYS 차례). 모두 진동 방식과 세기를 따로 고른다.
     */
    static final int CH_KEY = 0, CH_REPEAT = 1, CH_GESTURE = 2, CH_DELETE = 3, CH_SPACE = 4, CH_ENTER = 5,
            CH_SHIFT = 6, CH_SYMBOL = 7, CH_LANGUAGE = 8, CH_TOOLBAR = 9;
    private static final int CHANNELS = 10;
    /** 시스템 진동을 울릴 뷰 (키보드 창). 없으면 클릭으로 대신한다. */
    private View hapticView;
    private boolean vibrate, sound, perKey, gesture, repeat;
    private float volume;
    /** 곳(CH_*)마다 진동 방식·primitive·세기 %. */
    private final int[] style = new int[CHANNELS], primitive = new int[CHANNELS], amp = new int[CHANNELS];
    /** 곳(CH_*)마다 울릴 진동 (시스템 기본이면 키보드 창이 없을 때 대신 울릴 클릭). */
    private final VibrationEffect[] effect = new VibrationEffect[CHANNELS];
    /** 일반 키 사용자 설정(primitive를 못 쓰는 기기)의 길이 ms. */
    private int customMs;

    @SuppressWarnings("deprecation")
    Feedback(Context context) {
        audio = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        Vibrator v;
        if (Build.VERSION.SDK_INT >= 31) {
            VibratorManager vm = (VibratorManager) context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE);
            v = vm != null ? vm.getDefaultVibrator() : null;
        } else {
            v = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
        }
        vibrator = v;
        hasVibrator = v != null && v.hasVibrator();
        boolean prim = false;
        if (hasVibrator && Build.VERSION.SDK_INT >= 30) {
            try {
                prim = v.areAllPrimitivesSupported(VibrationEffect.Composition.PRIMITIVE_TICK,
                        VibrationEffect.Composition.PRIMITIVE_CLICK);
            } catch (RuntimeException ignored) {
                // 일부 기기는 진동 서비스 호출이 실패할 수 있다.
            }
        }
        primitives = prim;
    }

    /** primitive 틱·클릭을 세기를 바꿔 울릴 수 있는가 (사용자 설정에서 primitive와 세기를 고를 수 있는가). */
    boolean supportsPrimitives() {
        return primitives;
    }

    /** 시스템 진동(performHapticFeedback)을 울릴 뷰. 키보드 창을 새로 만들 때마다 알려 준다. */
    void setHapticView(View view) {
        hapticView = view;
    }

    void configure(Prefs prefs) {
        vibrate = prefs.vibrate();
        perKey = prefs.vibratePerKey();
        gesture = prefs.vibrateGestureOn();
        repeat = prefs.vibrateRepeatOn();
        customMs = prefs.vibrateMs();
        set(CH_KEY, prefs.vibrateStyle(), prefs.vibratePrimitive(), prefs.vibrateAmp());
        set(CH_REPEAT, prefs.vibrateRepeatStyle(), prefs.vibrateRepeatPrimitive(), prefs.vibrateRepeatAmp());
        set(CH_GESTURE, prefs.vibrateGestureStyle(), prefs.vibrateGesturePrimitive(), prefs.vibrateGestureAmp());
        for (int i = 0; i < Prefs.VIBRATE_KEYS.length; i++) {
            String base = Prefs.VIBRATE_KEYS[i];
            set(CH_DELETE + i, prefs.vibrateKeyStyle(base), prefs.vibrateKeyPrimitive(base), prefs.vibrateKeyAmp(base));
        }
        sound = prefs.sound();
        volume = prefs.soundVolume() / 100f;
    }

    private void set(int channel, int s, int p, int a) {
        style[channel] = normalizeStyle(s, primitives || channel == CH_KEY);
        primitive[channel] = p == PRIMITIVE_TICK ? PRIMITIVE_TICK : PRIMITIVE_CLICK;
        amp[channel] = a;
        effect[channel] = style[channel] == STYLE_NONE ? null : effectFor(style[channel], primitive[channel], a);
    }

    /**
     * 저장된 진동 방식을 고를 수 있는 방식으로 바꾼다. 사용자 설정을 쓸 수 없으면(primitive를 못 쓰는 기기의 반복 입력·밀기)
     * 클릭으로 본다.
     *
     * @param customAllowed 사용자 설정을 고를 수 있는가
     */
    static int normalizeStyle(int style, boolean customAllowed) {
        if (style == STYLE_NONE || style == STYLE_TICK || style == STYLE_HEAVY_CLICK || style == STYLE_SYSTEM) return style;
        if (style == STYLE_CUSTOM && customAllowed) return STYLE_CUSTOM;
        return STYLE_CLICK;
    }

    /**
     * 진동 방식의 진동. 틱·클릭·강한 클릭은 미리 정해진 효과, 사용자 설정은 primitive(prim)를 세기 amp %로
     * (primitive를 못 쓰는 기기에서는 길이 ms·강도 amp %의 한 번 진동). 시스템 기본은 키보드 창이 없을 때 대신 울릴 클릭.
     */
    private VibrationEffect effectFor(int style, int prim, int amp) {
        switch (style) {
            case STYLE_TICK: return fixed(WEAK, customMs);
            case STYLE_HEAVY_CLICK: return fixed(STRONG, customMs);
            case STYLE_CUSTOM:
                if (primitives) {
                    return primitive(prim == PRIMITIVE_TICK ? VibrationEffect.Composition.PRIMITIVE_TICK
                            : VibrationEffect.Composition.PRIMITIVE_CLICK, amp / 100f);
                }
                return custom(customMs, amp);
            default: return fixed(BASE, customMs);
        }
    }

    /**
     * 단계마다 정해진 진동: 약하게 = 틱, 기본 = 클릭, 강하게 = 강한 클릭 (Android 10부터, 미리 정해진 효과).
     * 그 아래 버전에서는 길이 ms로 비슷하게 흉내 낸다.
     */
    private static VibrationEffect fixed(int level, int ms) {
        if (Build.VERSION.SDK_INT >= 29) {
            return VibrationEffect.createPredefined(level == WEAK ? VibrationEffect.EFFECT_TICK
                    : level == STRONG ? VibrationEffect.EFFECT_HEAVY_CLICK : VibrationEffect.EFFECT_CLICK);
        }
        return VibrationEffect.createOneShot(Math.max(1, Math.round(ms * (level == WEAK ? 0.6f : level == STRONG ? 1.6f : 1f))),
                VibrationEffect.DEFAULT_AMPLITUDE);
    }

    /** primitive 하나를 세기 scale(0~1)로 울리는 진동. primitives가 참일 때만 부른다. */
    private static VibrationEffect primitive(int id, float scale) {
        return VibrationEffect.startComposition()
                .addPrimitive(id, Math.max(0.05f, Math.min(1f, scale)))
                .compose();
    }

    /** 사용자 설정(primitive를 못 쓰는 기기): 길이 ms와 강도 %. 강도 100%는 기기 기본 강도, 강도를 조절할 수 없는 기기에서는 늘 기본 강도. */
    private VibrationEffect custom(int ms, int ampPct) {
        int amplitude = VibrationEffect.DEFAULT_AMPLITUDE;
        if (vibrator != null && vibrator.hasAmplitudeControl() && ampPct < 100) {
            amplitude = Math.max(1, Math.min(255, Math.round(ampPct * 2.55f)));
        }
        return VibrationEffect.createOneShot(Math.max(1, ms), amplitude);
    }

    /** 설정 화면: channel(CH_*)의 지금 진동을 느껴 보게 한다 (세기·길이 막대를 움직였을 때). */
    void previewChannel(int channel, View view) {
        previewStyle(channel, style[channel], view);
    }

    /** 설정 화면의 고르기 창: 아직 저장하지 않은 진동 방식 s를 channel(CH_*)의 지금 primitive·세기로 느껴 보게 한다. */
    void previewStyle(int channel, int s, View view) {
        if (s == STYLE_NONE) return;
        if (s == STYLE_SYSTEM && view != null) playSystem(view, systemFeedback(channel));
        else play(effectFor(normalizeStyle(s, primitives || channel == CH_KEY), primitive[channel], amp[channel]));
    }

    /** 설정 화면: 사용자 설정의 primitive p(PRIMITIVE_*)를 channel의 지금 세기로 느껴 보게 한다. */
    void previewPrimitive(int channel, int p) {
        if (primitives) play(effectFor(STYLE_CUSTOM, p, amp[channel]));
    }

    /**
     * 시스템 기본을 골랐을 때 쓰는 진동: 일반 키 = 키보드 누르기, 반복 입력 = 잦은 틱(Android 14부터, 그 전에는 시계 틱),
     * 밀기·길게 누르기 = 동작 인식(Android 14부터, 그 전에는 길게 누르기).
     */
    private static int systemFeedback(int channel) {
        boolean u = Build.VERSION.SDK_INT >= 34;
        switch (channel) {
            case CH_REPEAT: return u ? HapticFeedbackConstants.SEGMENT_FREQUENT_TICK : HapticFeedbackConstants.CLOCK_TICK;
            case CH_GESTURE: return u ? HapticFeedbackConstants.GESTURE_THRESHOLD_ACTIVATE : HapticFeedbackConstants.LONG_PRESS;
            default: return HapticFeedbackConstants.KEYBOARD_TAP;
        }
    }

    /** 일반 키 진동: 시스템 키보드 진동이면 키보드 창으로, 아니면 정해 둔 진동으로 울린다. */
    private void playKey() {
        playChannel(CH_KEY);
    }

    /** 시스템 기본이면 키보드 창으로 시스템 진동을, 아니면 정해 둔 진동을 울린다 (키보드 창이 없으면 정해 둔 진동). */
    private void playChannel(int channel) {
        if (style[channel] == STYLE_SYSTEM && hapticView != null && hapticView.isAttachedToWindow()) {
            playSystem(hapticView, systemFeedback(channel));
        } else {
            play(effect[channel]);
        }
    }

    /**
     * 시스템 진동 (기기 제조사가 맞춰 둔 진동). 시스템의 터치 진동·키보드 진동 설정을 따르므로
     * 시스템에서 꺼 두면 울리지 않는다.
     */
    private static void playSystem(View view, int feedback) {
        try {
            view.performHapticFeedback(feedback);
        } catch (RuntimeException ignored) {
            // 일부 기기는 진동 서비스 호출이 실패할 수 있다.
        }
    }

    private void play(VibrationEffect e) {
        if (!hasVibrator || e == null) return;
        try {
            vibrator.vibrate(e);
        } catch (RuntimeException ignored) {
            // 일부 기기는 진동 서비스 호출이 실패할 수 있다.
        }
    }

    /** 밀기·길게 누르기가 끝났을 때의 진동 (설정에서 켠 경우만, 키 진동과 따로 켜고 끈다). */
    void onGesture() {
        if (gesture) playChannel(CH_GESTURE);
    }

    /** 도구 막대 버튼을 눌렀거나 도구 막대를 밀었을 때의 진동 (키 종류별 설정을 켰으면 그 단계). */
    void onToolbar() {
        if (vibrate) {
            playChannel(perKey ? CH_TOOLBAR : CH_KEY);
        }
        playClickSound(null);
    }

    /** 키를 누르고 있어 글자가 반복 입력되거나 연속으로 지워질 때, 한 글자마다 울리는 진동 (키 진동과 따로 고른다). */
    void onRepeat() {
        if (repeat) playChannel(CH_REPEAT);
    }

    void onKey(Key key) {
        if (vibrate) playChannel(perKey && key != null ? channelOf(key.type) : CH_KEY);
        playClickSound(key);
    }

    /** 키 종류별 진동에서 그 키가 따르는 곳 (CH_*). 따로 정하지 않는 키는 일반 키. */
    private static int channelOf(int type) {
        switch (type) {
            case Key.DELETE: return CH_DELETE;
            case Key.SPACE: return CH_SPACE;
            case Key.ENTER: return CH_ENTER;
            case Key.SHIFT: case Key.FUNCTION: return CH_SHIFT;   // 한글 자판의 Fn 키는 Shift 자리라 Shift 키로 본다
            case Key.TO_SYMBOLS: case Key.TO_LETTERS: case Key.SYMBOL_PAGE: return CH_SYMBOL;
            case Key.LANGUAGE: case Key.EMOJI: return CH_LANGUAGE;
            default: return CH_KEY;
        }
    }

    private void playClickSound(Key key) {
        if (sound && audio != null && volume > 0) {
            int fx;
            switch (key == null ? Key.CHAR : key.type) {
                case Key.DELETE:
                    fx = AudioManager.FX_KEYPRESS_DELETE;
                    break;
                case Key.SPACE:
                    fx = AudioManager.FX_KEYPRESS_SPACEBAR;
                    break;
                case Key.ENTER:
                    fx = AudioManager.FX_KEYPRESS_RETURN;
                    break;
                default:
                    fx = AudioManager.FX_KEYPRESS_STANDARD;
                    break;
            }
            audio.playSoundEffect(fx, volume);
        }
    }
}
