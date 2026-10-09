package com.alternative_studios.newswipe.ui;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.animation.DecelerateInterpolator;
import android.widget.TextView;

/**
 * Material 3 Expressive 스타일의 채운 버튼 (첫 시작 가이드의 '시작하기'·'다음으로' 등).
 * 평소에는 알약 모양이고, 누르는 동안 모서리가 각진 둥근 네모로 바뀌며 살짝 작아졌다가, 놓으면 스프링으로 되튀며 알약으로 돌아온다.
 */
public final class ExpressiveButton extends TextView {
    private static final long PRESS_IN_MS = 90, PRESS_OUT_MS = 480;

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private final int fill, ink;
    private final float pressedRadius;
    private float press;   // 0 = 놓음, 1 = 누름 (스프링으로 0 아래로 조금 내려갈 수 있다)
    private ValueAnimator pressAnim;
    private boolean down;

    /**
     * @param fill 버튼 면 색 (강조색)
     * @param ink  글자색 (강조색 위의 글자색)
     */
    public ExpressiveButton(Context c, String label, int fill, int ink) {
        super(c);
        this.fill = fill;
        this.ink = ink;
        this.pressedRadius = Ui.dp(c, 12);
        setText(label);
        setTextColor(ink);
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 17);
        setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        setGravity(Gravity.CENTER);
        setMinHeight(Ui.dp(c, 64));
        setMinWidth(Ui.dp(c, 168));
        setPadding(Ui.dp(c, 36), Ui.dp(c, 18), Ui.dp(c, 36), Ui.dp(c, 18));
        setClickable(true);
        setFocusable(true);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float w = getWidth(), h = getHeight();
        float full = h / 2f;
        float p = Math.max(-0.3f, Math.min(1f, press));
        float radius = Math.max(0f, Math.min(full, full + (pressedRadius - full) * p));
        // 누르면 모양이 가운데로 살짝 줄어든다 (놓을 때는 스프링으로 원래 크기를 조금 넘었다 돌아온다).
        float inset = Math.min(w, h) * 0.04f * p;
        rect.set(inset, inset, w - inset, h - inset);
        paint.setColor(fill);
        canvas.drawRoundRect(rect, radius, radius, paint);
        if (p > 0.01f) {   // 누른 느낌: 면 위에 글자색을 옅게 얹는다
            paint.setColor((ink & 0x00FFFFFF) | (Math.round(0x29 * Math.min(1f, p)) << 24));
            canvas.drawRoundRect(rect, radius, radius, paint);
        }
        canvas.save();
        float textScale = 1f - 0.04f * Math.max(0f, p);
        canvas.scale(textScale, textScale, w / 2f, h / 2f);
        super.onDraw(canvas);
        canvas.restore();
    }

    private void animatePress(boolean toDown) {
        if (pressAnim != null) pressAnim.cancel();
        pressAnim = ValueAnimator.ofFloat(press, toDown ? 1f : 0f);
        pressAnim.setDuration(toDown ? PRESS_IN_MS : PRESS_OUT_MS);
        pressAnim.setInterpolator(toDown ? new DecelerateInterpolator() : Spring.BOUNCY);
        pressAnim.addUpdateListener(a -> {
            press = (float) a.getAnimatedValue();
            invalidate();
        });
        pressAnim.start();
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        if (!isEnabled() || !isClickable()) return super.onTouchEvent(e);
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                down = true;
                animatePress(true);
                performHapticFeedback(android.os.Build.VERSION.SDK_INT >= 34
                        ? HapticFeedbackConstants.SEGMENT_FREQUENT_TICK : HapticFeedbackConstants.CLOCK_TICK);
                break;
            case MotionEvent.ACTION_MOVE:
                if (down && (e.getX() < 0 || e.getX() > getWidth() || e.getY() < 0 || e.getY() > getHeight())) {
                    down = false;
                    animatePress(false);
                }
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                if (down) {
                    down = false;
                    animatePress(false);
                }
                break;
            default:
                break;
        }
        return super.onTouchEvent(e);
    }

    @Override
    public void setEnabled(boolean enabled) {
        super.setEnabled(enabled);
        if (!enabled && down) {
            down = false;
            animatePress(false);
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        if (pressAnim != null) pressAnim.cancel();
        super.onDetachedFromWindow();
    }
}
