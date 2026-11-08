package com.alternative_studios.newswipe.ui;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.view.View;
import android.view.animation.LinearInterpolator;

/**
 * 실제 시간으로 푸는 감쇠 스프링 (Material 3 Expressive의 공간 스프링: 강성·감쇠비로 정한다).
 * <p>
 * {@link Spring}은 정규화한 시간 [0, 1] 안에 거의(약 2%) 자리 잡는 곡선이라 버튼 모양처럼 작은 움직임에는 맞지만, 화면 높이만큼
 * 움직이는 시트에 쓰면 끝에서 남은 몇십 px가 한 번에 맞춰져 '끊겨' 보인다. 여기서는 남은 거리가 반 픽셀보다 작아질 때까지만
 * 움직이도록 길이를 스프링 자체에서 계산하므로 끝이 매끄럽고, 거리가 멀어도 같은 빠르기로 느껴진다.
 */
public final class SpringMotion {
    /** M3 Expressive 기본 공간 스프링의 강성. */
    public static final float STIFFNESS_DEFAULT = 700f;
    /** 살짝 한 번 넘었다가 자리 잡는 감쇠비 (넘는 양은 움직인 거리의 약 0.6%). */
    public static final float DAMPING_EXPRESSIVE = 0.85f;

    private SpringMotion() {
    }

    /**
     * v의 translationY를 지금 값에서 target으로 스프링으로 옮긴다. 애니메이션 동안 하드웨어 층으로 그려 가볍게 하고, 끝나면
     * 정확히 target에 둔다. 돌려준 애니메이터를 cancel()하면 그 자리에서 멈춘다.
     */
    public static ValueAnimator translateY(View v, float target, float stiffness, float dampingRatio, Runnable onEnd) {
        final float x0 = v.getTranslationY() - target;
        final double w0 = Math.sqrt(stiffness);
        final double z = Math.min(0.999, dampingRatio);
        final double wd = w0 * Math.sqrt(1 - z * z);
        final double b = z * w0 * x0 / wd;   // 처음 속도 0
        // 진폭의 포락선이 반 픽셀보다 작아지는 시간 (그 뒤에는 움직임이 보이지 않는다).
        double amp = Math.hypot(x0, b);
        double settle = amp <= 0.5 ? 0 : Math.log(amp / 0.5) / (z * w0);
        long ms = Math.max(1, Math.min(1500, Math.round(settle * 1000)));
        ValueAnimator va = ValueAnimator.ofFloat(0f, 1f);
        va.setDuration(ms);
        va.setInterpolator(new LinearInterpolator());
        va.addUpdateListener(a -> {
            double t = a.getAnimatedFraction() * ms / 1000.0;
            double x = Math.exp(-z * w0 * t) * (x0 * Math.cos(wd * t) + b * Math.sin(wd * t));
            v.setTranslationY((float) (target + x));
        });
        va.addListener(new AnimatorListenerAdapter() {
            private boolean cancelled;

            @Override
            public void onAnimationStart(Animator animation) {
                v.setLayerType(View.LAYER_TYPE_HARDWARE, null);
            }

            @Override
            public void onAnimationCancel(Animator animation) {
                cancelled = true;
            }

            @Override
            public void onAnimationEnd(Animator animation) {
                v.setLayerType(View.LAYER_TYPE_NONE, null);
                if (cancelled) return;
                v.setTranslationY(target);
                if (onEnd != null) onEnd.run();
            }
        });
        va.start();
        return va;
    }
}
