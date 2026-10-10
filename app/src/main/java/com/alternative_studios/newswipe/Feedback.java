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
    /** 진동 단계: 0 끔, 1 약하게, 2 기본, 3 강하게. */
    static final int OFF = 0, WEAK = 1, BASE = 2, STRONG = 3;
    private boolean vibrate, sound, perKey;
    private float volume;
    private int deleteLevel = BASE, spaceLevel = BASE, enterLevel = BASE, gestureLevel = OFF;
    /** 단계별 진동 (인덱스 = 단계). 설정이 바뀔 때만 다시 만든다. */
    private final VibrationEffect[] effects = new VibrationEffect[4];
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
        gestureLevel = clampLevel(prefs.vibrateGesture());
        int mode = prefs.vibrateMode(), ms = prefs.vibrateMs(), amp = prefs.vibrateAmp();
        int signature = (mode * 1009 + ms) * 1009 + amp;
        if (signature != builtSignature) {
            builtSignature = signature;
            for (int level = WEAK; level <= STRONG; level++) effects[level] = build(level, mode, ms, amp);
        }
        sound = prefs.sound();
        volume = prefs.soundVolume() / 100f;
    }

    private static int clampLevel(int v) {
        return Math.max(OFF, Math.min(STRONG, v));
    }

    /**
     * @param mode 0 직접 설정(길이 ms + 강도 %), 1 기본 클릭, 2 틱, 3 강한 클릭
     * 시스템 효과는 Android 10부터 쓸 수 있어, 그 아래에서는 직접 설정으로 대신한다.
     */
    private VibrationEffect build(int level, int mode, int ms, int ampPct) {
        if (mode != 0 && Build.VERSION.SDK_INT >= 29) {
            // 약하게 / 기본 / 강하게 에 해당하는 시스템 효과 (EFFECT_TICK = 2, EFFECT_CLICK = 0, EFFECT_HEAVY_CLICK = 5)
            final int tick = VibrationEffect.EFFECT_TICK, click = VibrationEffect.EFFECT_CLICK,
                    heavy = VibrationEffect.EFFECT_HEAVY_CLICK;
            int[][] table = {{tick, click, heavy}, {tick, tick, click}, {click, heavy, heavy}};
            return VibrationEffect.createPredefined(table[Math.max(1, Math.min(3, mode)) - 1][level - 1]);
        }
        float scale = level == WEAK ? 0.6f : 1f;
        int len = Math.max(1, Math.round(ms * (level == WEAK ? 0.6f : level == STRONG ? 1.6f : 1f)));
        int amplitude = VibrationEffect.DEFAULT_AMPLITUDE;
        boolean controllable = vibrator != null && vibrator.hasAmplitudeControl();
        float pct = ampPct * scale;
        if (controllable && pct < 100f) amplitude = Math.max(1, Math.min(255, Math.round(pct * 2.55f)));
        return VibrationEffect.createOneShot(len, amplitude);
    }

    /** 설정 화면에서 지금 설정을 느껴 보게 한다. */
    void preview(int level) {
        play(level);
    }

    private void play(int level) {
        if (level <= OFF || !hasVibrator || effects[level] == null) return;
        try {
            vibrator.vibrate(effects[level]);
        } catch (RuntimeException ignored) {
            // 일부 기기는 진동 서비스 호출이 실패할 수 있다.
        }
    }

    /** 밀기·길게 누르기가 끝났을 때의 진동 (설정에서 켠 경우만). */
    void onGesture() {
        if (vibrate) play(gestureLevel);
    }

    void onKey(Key key) {
        if (vibrate) {
            int level = BASE;
            if (perKey && key != null) {
                switch (key.type) {
                    case Key.DELETE: level = deleteLevel; break;
                    case Key.SPACE: level = spaceLevel; break;
                    case Key.ENTER: level = enterLevel; break;
                    default: break;
                }
            }
            play(level);
        }
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
