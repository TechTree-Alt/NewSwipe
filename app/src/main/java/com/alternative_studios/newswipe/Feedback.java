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
    /** 진동 단계: 1 약하게(틱), 2 기본(클릭), 3 강하게(강한 클릭). 키 종류별·밀기 진동은 이 단계를 그대로 쓴다. */
    static final int WEAK = 1, BASE = 2, STRONG = 3;
    /**
     * 일반 키 진동 방식 (Prefs.vibrateStyle): 0 = 틱, 1 = 클릭, 3 = 직접 설정, 4 = 시스템 키보드 진동.
     * 2는 예전의 '강하게'로, 이제 클릭으로 본다 ({@link #normalizeStyle}).
     */
    static final int STYLE_TICK = 0, STYLE_CLICK = 1, STYLE_CUSTOM = 3, STYLE_SYSTEM = 4;
    /** 시스템 키보드 진동을 울릴 뷰 (키보드 창). 없으면 기본 진동으로 대신한다. */
    private View hapticView;
    private boolean systemKey;
    private boolean vibrate, sound, perKey, gesture;
    private float volume;
    private int deleteLevel = BASE, spaceLevel = BASE, enterLevel = BASE, gestureLevel = BASE,
            shiftLevel = BASE, symbolLevel = BASE, languageLevel = BASE, toolbarLevel = BASE;
    /** 단계별 고정 진동 (인덱스 = 단계). */
    private final VibrationEffect[] levels = new VibrationEffect[4];
    /** 일반 키를 눌렀을 때의 진동 (약하게·기본·강하게 중 하나이거나 직접 설정한 값). */
    private VibrationEffect keyEffect;
    private int builtSignature = Integer.MIN_VALUE;
    private int customMs, customAmp;

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

    /** 틱·클릭의 세기를 조절할 수 있는가 (아니면 설정 화면에서 세기 대신 직접 설정(길이·강도)을 보인다). */
    boolean supportsPrimitives() {
        return primitives;
    }

    /** 시스템 키보드 진동(performHapticFeedback)을 울릴 뷰. 키보드 창을 새로 만들 때마다 알려 준다. */
    void setHapticView(View view) {
        hapticView = view;
    }

    void configure(Prefs prefs) {
        vibrate = prefs.vibrate();
        perKey = prefs.vibratePerKey();
        deleteLevel = clampLevel(prefs.vibrateDelete());
        spaceLevel = clampLevel(prefs.vibrateSpace());
        enterLevel = clampLevel(prefs.vibrateEnter());
        shiftLevel = clampLevel(prefs.vibrateShift());
        symbolLevel = clampLevel(prefs.vibrateSymbol());
        languageLevel = clampLevel(prefs.vibrateLanguage());
        toolbarLevel = clampLevel(prefs.vibrateToolbar());
        gesture = prefs.vibrateGestureOn();
        gestureLevel = clampLevel(prefs.vibrateGestureLevel());
        int style = prefs.vibrateStyle(), ms = prefs.vibrateMs(), amp = prefs.vibrateAmp();
        customMs = ms;
        customAmp = amp;
        int signature = (style * 1009 + ms) * 1009 + amp;
        if (signature != builtSignature) {
            builtSignature = signature;
            for (int level = WEAK; level <= STRONG; level++) levels[level] = fixed(level, ms);
            keyEffect = keyEffectFor(style, ms, amp);
        }
        systemKey = normalizeStyle(style, primitives) == STYLE_SYSTEM;
        sound = prefs.sound();
        volume = prefs.soundVolume() / 100f;
    }

    private static int clampLevel(int v) {
        return Math.max(WEAK, Math.min(STRONG, v));
    }

    /**
     * 저장된 진동 방식을 지금 기기에서 쓸 방식으로 바꾼다. 예전의 '강하게'(2)는 클릭으로 본다.
     * 틱·클릭의 세기를 조절할 수 있는 기기에서는 직접 설정이 '세기를 고른 클릭'과 같으므로 클릭으로 본다.
     */
    static int normalizeStyle(int style, boolean primitives) {
        if (style == STYLE_TICK || style == STYLE_SYSTEM) return style;
        if (style == STYLE_CUSTOM && !primitives) return STYLE_CUSTOM;
        return STYLE_CLICK;
    }

    /**
     * 일반 키 진동. 세기를 조절할 수 있는 기기에서는 틱·클릭 primitive를 강도 %의 세기로 울린다.
     * 아니면 틱·클릭은 미리 정해진 효과(세기 고정), 직접 설정은 길이 ms와 강도 %의 한 번 진동이다.
     * 시스템 진동은 키보드 창이 없을 때(설정 화면 밖 등) 클릭으로 대신한다.
     */
    private VibrationEffect keyEffectFor(int style, int ms, int amp) {
        style = normalizeStyle(style, primitives);
        if (style == STYLE_CUSTOM) return custom(ms, amp);
        if (primitives && style != STYLE_SYSTEM) {
            return primitive(style == STYLE_TICK ? VibrationEffect.Composition.PRIMITIVE_TICK
                    : VibrationEffect.Composition.PRIMITIVE_CLICK, amp / 100f);
        }
        return fixed(style == STYLE_TICK ? WEAK : BASE, ms);
    }

    /**
     * 단계마다 정해진 진동 (키 종류별·밀기 진동, 반복 입력): 약하게 = 틱, 기본 = 클릭, 강하게 = 강한 클릭
     * (Android 10부터, 미리 정해진 효과). 그 아래 버전에서는 길이 ms로 비슷하게 흉내 낸다.
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

    /** 직접 설정: 길이 ms와 강도 %. 강도 100%는 기기 기본 강도, 강도를 조절할 수 없는 기기에서는 늘 기본 강도. */
    private VibrationEffect custom(int ms, int ampPct) {
        int amplitude = VibrationEffect.DEFAULT_AMPLITUDE;
        if (vibrator != null && vibrator.hasAmplitudeControl() && ampPct < 100) {
            amplitude = Math.max(1, Math.min(255, Math.round(ampPct * 2.55f)));
        }
        return VibrationEffect.createOneShot(Math.max(1, ms), amplitude);
    }

    /** 설정 화면에서 지금 설정을 느껴 보게 한다. level 0이면 일반 키 진동 (시스템 진동이면 view로 울린다). */
    void preview(int level, View view) {
        if (level == 0 && systemKey && view != null) {
            playSystem(view);
            return;
        }
        play(level == 0 ? keyEffect : levels[clampLevel(level)]);
    }

    /** 설정 화면의 고르기 창: 아직 저장하지 않은 일반 키 진동 방식 style을 느껴 보게 한다 (세기·길이는 지금 설정). */
    void previewStyle(int style, View view) {
        if (style == STYLE_SYSTEM && view != null) playSystem(view);
        else play(keyEffectFor(style, customMs, customAmp));
    }

    /** 일반 키 진동: 시스템 키보드 진동이면 키보드 창으로, 아니면 정해 둔 진동으로 울린다. */
    private void playKey() {
        if (systemKey && hapticView != null && hapticView.isAttachedToWindow()) playSystem(hapticView);
        else play(keyEffect);
    }

    /**
     * 시스템 키보드 진동 (기기 제조사가 키보드용으로 맞춰 둔 진동). 시스템의 터치 진동·키보드 진동 설정을 따르므로
     * 시스템에서 꺼 두면 울리지 않는다.
     */
    private static void playSystem(View view) {
        try {
            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
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

    /** 밀기·길게 누르기가 끝났을 때의 진동 (설정에서 켠 경우만). */
    void onGesture() {
        if (vibrate && gesture) play(levels[gestureLevel]);
    }

    /** 도구 막대 버튼을 눌렀거나 도구 막대를 밀었을 때의 진동 (키 종류별 설정을 켰으면 그 단계). */
    void onToolbar() {
        if (vibrate) {
            if (perKey) play(levels[toolbarLevel]);
            else playKey();
        }
        playClickSound(null);
    }

    /** 키를 누르고 있어 글자가 반복 입력되거나 연속으로 지워질 때, 한 글자마다 울리는 틱. */
    void onRepeat() {
        if (vibrate) play(levels[WEAK]);
    }

    void onKey(Key key) {
        if (vibrate) {
            VibrationEffect e = null;   // null이면 일반 키 진동
            if (perKey && key != null) {
                switch (key.type) {
                    case Key.DELETE: e = levels[deleteLevel]; break;
                    case Key.SPACE: e = levels[spaceLevel]; break;
                    case Key.ENTER: e = levels[enterLevel]; break;
                    case Key.SHIFT: case Key.FUNCTION:   // 한글 자판의 Fn 키는 Shift 자리라 Shift 키로 본다
                        e = levels[shiftLevel];
                        break;
                    case Key.TO_SYMBOLS: case Key.TO_LETTERS: case Key.SYMBOL_PAGE:
                        e = levels[symbolLevel];
                        break;
                    case Key.LANGUAGE: case Key.EMOJI: e = levels[languageLevel]; break;
                    default: break;
                }
            }
            if (e != null) play(e);
            else playKey();
        }
        playClickSound(key);
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
