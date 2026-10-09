package com.alternative_studios.newswipe.ui;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.OvershootInterpolator;

/**
 * Material 3 Expressive 스타일 스위치. 52×32dp 알약형 트랙, 꺼짐은 작은 손잡이와 테두리,
 * 켜짐은 큰 손잡이 안에 체크 표시가 들어간다. 누르는 동안 손잡이가 커진다.
 */
public final class ExpressiveSwitch extends View {

    public interface OnCheckedChange {
        void onChange(boolean checked);
    }

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path check = new Path();
    private final RectF rect = new RectF();
    private final int accent, onAccent, outline, surface;
    private final float w, h, border;
    private boolean checked;
    private boolean pressed;
    private float progress;       // 0 = 꺼짐, 1 = 켜짐
    private float press;          // 0~1, 누르는 정도
    private ValueAnimator progressAnim, pressAnim;
    private OnCheckedChange listener;

    public ExpressiveSwitch(Context context, int accent, int onAccent, int outline, int surface) {
        super(context);
        this.accent = accent;
        this.onAccent = onAccent;
        this.outline = outline;
        this.surface = surface;
        w = Ui.dp(context, 52);
        h = Ui.dp(context, 32);
        border = Ui.dp(context, 2);
        setClickable(true);
        setFocusable(true);
    }

    /** 애니메이션 없이 값만 바꾼다 (초기 상태용). */
    public void setCheckedImmediately(boolean value) {
        checked = value;
        progress = value ? 1f : 0f;
        invalidate();
    }

    /** 알리지 않고 값을 바꾸며 움직임만 보여 준다 (바꾸기를 취소해 되돌릴 때). */
    public void setCheckedSilently(boolean value) {
        if (checked == value) return;
        checked = value;
        animateProgress(value ? 1f : 0f);
    }

    public boolean isChecked() {
        return checked;
    }

    public void setOnCheckedChangeListener(OnCheckedChange l) {
        listener = l;
    }

    public void toggle() {
        checked = !checked;
        animateProgress(checked ? 1f : 0f);
        if (android.os.Build.VERSION.SDK_INT >= 30) {
            performHapticFeedback(checked ? android.view.HapticFeedbackConstants.CONFIRM
                    : android.view.HapticFeedbackConstants.REJECT);
        } else {
            performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP);
        }
        if (listener != null) listener.onChange(checked);
    }

    private void animateProgress(float to) {
        if (progressAnim != null) progressAnim.cancel();
        progressAnim = ValueAnimator.ofFloat(progress, to);
        progressAnim.setDuration(260);
        progressAnim.setInterpolator(new OvershootInterpolator(1.4f));
        progressAnim.addUpdateListener(a -> {
            progress = (float) a.getAnimatedValue();
            invalidate();
        });
        progressAnim.start();
    }

    private void animatePress(float to) {
        if (pressAnim != null) pressAnim.cancel();
        pressAnim = ValueAnimator.ofFloat(press, to);
        pressAnim.setDuration(120);
        pressAnim.addUpdateListener(a -> {
            press = (float) a.getAnimatedValue();
            invalidate();
        });
        pressAnim.start();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        setMeasuredDimension(Math.round(w), Math.max(Math.round(h), Ui.dp(getContext(), 40)));
    }

    @Override
    protected void onDraw(Canvas c) {
        float cy = getHeight() / 2f;
        float left = 0, right = w, top = cy - h / 2f, bottom = cy + h / 2f;
        float t = Math.max(0f, Math.min(1f, progress));

        // 트랙: 꺼짐 = 표면색 + 테두리, 켜짐 = 강조색
        rect.set(left, top, right, bottom);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(blend(surface, accent, t));
        c.drawRoundRect(rect, h / 2f, h / 2f, paint);
        if (t < 1f) {
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(border);
            paint.setColor(withAlpha(outline, Math.round(255 * (1f - t))));
            rect.inset(border / 2f, border / 2f);
            c.drawRoundRect(rect, h / 2f, h / 2f, paint);
        }

        // 손잡이: 꺼짐 16dp → 켜짐 24dp, 누르면 28dp
        float dp = Ui.dp(getContext(), 1);
        float size = (16 + 8 * t) * dp + (28 * dp - (16 + 8 * t) * dp) * press;
        float travel = (w - h);
        float cx = h / 2f + travel * progress;
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(blend(outline, onAccent, t));
        c.drawCircle(cx, cy, size / 2f, paint);

        // 체크 표시 (켜질수록 또렷하게)
        if (t > 0.05f) {
            float a = Math.max(0f, Math.min(1f, (t - 0.3f) / 0.7f));
            float s = size * 0.5f;
            check.reset();
            check.moveTo(cx - s * 0.42f, cy + s * 0.02f);
            check.lineTo(cx - s * 0.1f, cy + s * 0.34f);
            check.lineTo(cx + s * 0.44f, cy - s * 0.3f);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(2f * dp);
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setStrokeJoin(Paint.Join.ROUND);
            paint.setColor(withAlpha(accent, Math.round(255 * a)));
            c.drawPath(check, paint);
        }
    }

    /** 끄면 흐리게 그리고 눌러도 바뀌지 않는다. */
    @Override
    public void setEnabled(boolean enabled) {
        super.setEnabled(enabled);
        setAlpha(enabled ? 1f : 0.38f);
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        if (!isEnabled()) return false;
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                pressed = true;
                animatePress(1f);
                return true;
            case MotionEvent.ACTION_UP:
                if (pressed) {
                    animatePress(0f);
                    pressed = false;
                    performClick();
                }
                return true;
            case MotionEvent.ACTION_CANCEL:
                pressed = false;
                animatePress(0f);
                return true;
            default:
                return true;
        }
    }

    @Override
    public boolean performClick() {
        if (!isEnabled()) return false;
        toggle();
        return super.performClick();
    }

    private static int withAlpha(int color, int alpha) {
        return (color & 0x00FFFFFF) | (alpha << 24);
    }

    private static int blend(int from, int to, float t) {
        int a = Math.round(((from >>> 24) * (1 - t)) + ((to >>> 24) * t));
        int r = Math.round((((from >> 16) & 0xFF) * (1 - t)) + (((to >> 16) & 0xFF) * t));
        int g = Math.round((((from >> 8) & 0xFF) * (1 - t)) + (((to >> 8) & 0xFF) * t));
        int b = Math.round(((from & 0xFF) * (1 - t)) + ((to & 0xFF) * t));
        return (a << 24) | (r << 16) | (g << 8) | b;
    }
}
