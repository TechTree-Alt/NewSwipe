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
    private boolean vibrate, sound;
    private int vibrateMs;
    private float volume;
    /** 진동 세기가 바뀔 때만 다시 만든다. */
    private VibrationEffect effect;

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
        int ms = prefs.vibrateMs();
        if (effect == null || ms != vibrateMs) {
            effect = VibrationEffect.createOneShot(Math.max(1, ms), VibrationEffect.DEFAULT_AMPLITUDE);
        }
        vibrateMs = ms;
        sound = prefs.sound();
        volume = prefs.soundVolume() / 100f;
    }

    void onKey(Key key) {
        if (vibrate && hasVibrator && effect != null) {
            try {
                vibrator.vibrate(effect);
            } catch (RuntimeException ignored) {
                // 일부 기기는 진동 서비스 호출이 실패할 수 있다.
            }
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
