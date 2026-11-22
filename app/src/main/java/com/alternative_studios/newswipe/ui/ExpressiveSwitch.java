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
 *
 * <p>Metro UI 모드에서는 Windows Phone의 ToggleSwitch를 그린다 (Windows Phone Toolkit의 템플릿 비율: 트랙 89×34에 3px 글자 색
 * 테두리와 4px 바탕색 틈, 그 안을 손잡이 왼쪽까지 강조색으로 채우고, 손잡이는 트랙보다 위아래로 조금 긴 20×38의 글자 색 막대이며
 * 양옆을 바탕색 4px로 띄운다). 상태가 바뀔 때는 아주 짧게(지수 15의 EaseOut) 움직인다.
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
        w = Ui.dp(context, Metro.on ? 60 : 52);
        h = Ui.dp(context, Metro.on ? 23 : 32);
        border = Ui.dp(context, 2);
        setClickable(true);
        setFocusable(true);
        if (NeuUi.on) Neu.unclipParents(this);   // 끝에 선 손잡이의 그림자가 잘리지 않게
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
        progressAnim.setDuration(Metro.on ? 120 : 260);
        progressAnim.setInterpolator(Metro.on ? METRO_EASE : new OvershootInterpolator(1.4f));
        progressAnim.addUpdateListener(a -> {
            progress = (float) a.getAnimatedValue();
            invalidate();
        });
        progressAnim.start();
    }

    private static final android.view.animation.Interpolator METRO_EASE = new Metro.Exp(15, true);

    private void animatePress(float to) {
        if (Metro.on) return;   // Windows Phone의 스위치는 누를 때 모양이 바뀌지 않는다
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

    /** Windows Phone의 ToggleSwitch (트랙 60×23dp, 손잡이 13×27dp). */
    private void drawMetro(Canvas c) {
        float dp = Ui.dp(getContext(), 1);
        float cy = getHeight() / 2f;
        float top = cy - h / 2f, bottom = cy + h / 2f;
        float t = Math.max(0f, Math.min(1f, progress));
        float stroke = 2 * dp, gap = 2.5f * dp, thumbW = 13 * dp, thumbH = h + 4 * dp, thumbGap = 3 * dp;
        paint.setStyle(Paint.Style.FILL);
        // 테두리 (글자 색)
        paint.setColor(Metro.fg);
        c.drawRect(0, top, w, bottom, paint);
        paint.setColor(Metro.bg);
        c.drawRect(stroke, top + stroke, w - stroke, bottom - stroke, paint);
        // 손잡이 자리: 왼쪽 끝 ↔ 오른쪽 끝
        float thumbL = (w - thumbW) * t;
        // 안쪽 채움 (강조색): 손잡이 왼쪽까지
        float inL = stroke + gap, inR = w - stroke - gap;
        float fillR = Math.min(inR, thumbL);
        if (fillR > inL) {
            paint.setColor(accent);
            c.drawRect(inL, top + stroke + gap, fillR, bottom - stroke - gap, paint);
        }
        // 손잡이: 양옆을 바탕색으로 띄운 글자 색 막대
        paint.setColor(Metro.bg);
        c.drawRect(thumbL - thumbGap, cy - thumbH / 2f, thumbL + thumbW + thumbGap, cy + thumbH / 2f, paint);
        paint.setColor(Metro.fg);
        c.drawRect(thumbL, cy - thumbH / 2f, thumbL + thumbW, cy + thumbH / 2f, paint);
    }

    /**
     * Neumorphism: 안으로 들어간 홈(켜지면 강조색이 차오른다)과, 그 안에서 솟아오른 바탕색 손잡이. 누르는 동안 손잡이가 조금 커진다.
     */
    private void drawNeu(Canvas c) {
        float dp = Ui.dp(getContext(), 1);
        float cy = getHeight() / 2f;
        float t = Math.max(0f, Math.min(1f, progress));
        rect.set(0, cy - h / 2f, w, cy + h / 2f);
        int groove = t <= 0f ? NeuUi.surface : blend(NeuUi.well(), accent, 0.9f * t);
        NeuUi.drawInset(getContext(), c, rect, h / 2f, groove, paint);
        float size = h - 8 * dp + 3 * dp * press;
        float travel = w - h;
        float cx = h / 2f + travel * progress;
        rect.set(cx - size / 2f, cy - size / 2f, cx + size / 2f, cy + size / 2f);
        NeuUi.drawRaised(getContext(), c, rect, size / 2f, NeuUi.SMALL);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(NeuUi.knob());
        c.drawCircle(cx, cy, size / 2f, paint);
    }

    @Override
    protected void onDraw(Canvas c) {
        if (Metro.on) {
            drawMetro(c);
            return;
        }
        if (NeuUi.on) {
            drawNeu(c);
            return;
        }
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
