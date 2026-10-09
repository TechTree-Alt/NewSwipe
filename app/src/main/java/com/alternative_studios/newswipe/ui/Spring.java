package com.alternative_studios.newswipe.ui;

import android.view.animation.Interpolator;

/**
 * 감쇠 스프링 곡선. 목표를 한 번 넘었다가 돌아와 자리 잡는 '쫄깃한' 움직임이다 (Material 3 Expressive의 공간 스프링).
 * 정규화한 시간 [0, 1] 동안 거의 정착하도록 고정 진동수를 쓰고, 계산은 닫힌 식이라 프레임마다 가볍다.
 */
public final class Spring implements Interpolator {
    /** 기본 스프링: 감쇠비 0.6 (목표를 약 9% 넘었다가 돌아온다). */
    public static final Spring BOUNCY = new Spring(0.6f);
    /** 더 얌전한 스프링: 감쇠비 0.8 (살짝만 넘는다). */
    public static final Spring SOFT = new Spring(0.8f);

    private final float decay;     // ζ·ω0
    private final float damped;    // ωd
    private final float ratio;     // ζ·ω0 / ωd

    private Spring(float dampingRatio) {
        // 정규화한 시간 1에서 포락선이 e^-4 (≈2%)로 줄어들도록 ω0를 잡는다.
        float w0 = 4f / dampingRatio;
        decay = dampingRatio * w0;
        damped = w0 * (float) Math.sqrt(1f - dampingRatio * dampingRatio);
        ratio = decay / damped;
    }

    @Override
    public float getInterpolation(float u) {
        if (u <= 0f) return 0f;
        if (u >= 1f) return 1f;
        double env = Math.exp(-decay * u);
        return (float) (1.0 - env * (Math.cos(damped * u) + ratio * Math.sin(damped * u)));
    }
}
