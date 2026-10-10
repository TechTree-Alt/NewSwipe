package com.alternative_studios.newswipe;

import android.content.Context;
import android.media.AudioManager;
import android.os.Build;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.os.VibratorManager;

import com.alternative_studios.newswipe.keyboard.Key;

/** 키 진동과 키음. */
final class Feedback {
    private final AudioManager audio;
    private final Vibrator vibrator;
    /** 진동기 유무는 바뀌지 않으므로 한 번만 확인한다. */
    private final boolean hasVibrator;
    /** 진동 단계: 1 약하게(틱), 2 기본(클릭), 3 강하게(강한 클릭). 키 종류별·밀기 진동은 이 단계를 그대로 쓴다. */
    static final int WEAK = 1, BASE = 2, STRONG = 3;
    private boolean vibrate, sound, perKey, gesture;
    private float volume;
    private int deleteLevel = BASE, spaceLevel = BASE, enterLevel = BASE, gestureLevel = BASE,
            shiftLevel = BASE, symbolLevel = BASE, languageLevel = BASE, toolbarLevel = BASE;
    /** 단계별 고정 진동 (인덱스 = 단계). */
    private final VibrationEffect[] levels = new VibrationEffect[4];
    /** 일반 키를 눌렀을 때의 진동 (약하게·기본·강하게 중 하나이거나 직접 설정한 값). */
    private VibrationEffect keyEffect;
    private int builtSignature = Integer.MIN_VALUE;

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
        int signature = (style * 1009 + ms) * 1009 + amp;
        if (signature != builtSignature) {
            builtSignature = signature;
            for (int level = WEAK; level <= STRONG; level++) levels[level] = fixed(level, ms);
            keyEffect = style >= 0 && style <= 2 ? levels[style + 1] : custom(ms, amp);
        }
        sound = prefs.sound();
        volume = prefs.soundVolume() / 100f;
    }

    private static int clampLevel(int v) {
        return Math.max(WEAK, Math.min(STRONG, v));
    }

    /**
     * 단계마다 정해진 진동: 약하게 = 틱, 기본 = 클릭, 강하게 = 강한 클릭 (Android 10부터).
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

    /** 직접 설정: 길이 ms와 강도 %. 강도 100%는 기기 기본 강도, 강도를 조절할 수 없는 기기에서는 늘 기본 강도. */
    private VibrationEffect custom(int ms, int ampPct) {
        int amplitude = VibrationEffect.DEFAULT_AMPLITUDE;
        if (vibrator != null && vibrator.hasAmplitudeControl() && ampPct < 100) {
            amplitude = Math.max(1, Math.min(255, Math.round(ampPct * 2.55f)));
        }
        return VibrationEffect.createOneShot(Math.max(1, ms), amplitude);
    }

    /** 설정 화면에서 지금 설정을 느껴 보게 한다. level 0이면 일반 키 진동. */
    void preview(int level) {
        play(level == 0 ? keyEffect : levels[clampLevel(level)]);
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
        if (vibrate) play(perKey ? levels[toolbarLevel] : keyEffect);
        playClickSound(null);
    }

    /** 키를 누르고 있어 글자가 반복 입력되거나 연속으로 지워질 때, 한 글자마다 울리는 틱. */
    void onRepeat() {
        if (vibrate) play(levels[WEAK]);
    }

    void onKey(Key key) {
        if (vibrate) {
            VibrationEffect e = keyEffect;
            if (perKey && key != null) {
                switch (key.type) {
                    case Key.DELETE: e = levels[deleteLevel]; break;
                    case Key.SPACE: e = levels[spaceLevel]; break;
                    case Key.ENTER: e = levels[enterLevel]; break;
                    case Key.SHIFT: e = levels[shiftLevel]; break;
                    case Key.TO_SYMBOLS: case Key.TO_LETTERS: case Key.SYMBOL_PAGE:
                        e = levels[symbolLevel];
                        break;
                    case Key.LANGUAGE: case Key.EMOJI: e = levels[languageLevel]; break;
                    default: break;
                }
            }
            play(e);
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
