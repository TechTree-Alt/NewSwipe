package com.alternative_studios.newswipe.ui;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

import com.alternative_studios.newswipe.keyboard.Icons;

/**
 * Material 3 Expressive 스타일의 동그란 아이콘 버튼 (ExpressiveButton의 아이콘 버튼판).
 * 평소에는 동그라미이고, 누르는 동안 모서리가 각진 둥근 네모로 바뀌며 살짝 작아졌다가, 놓으면 스프링으로 되튀며 동그라미로 돌아온다.
 * 누르는 순간 짧은 진동이 울린다. setActive(true)면 옅은 바탕에 강조색 아이콘의 모서리가 둥근 네모로 스프링과 함께 바뀌어
 * '켜짐'을 나타내고, 끄면 다시 동그라미로 돌아온다.
 */
public final class ExpressiveIconButton extends View {
    private static final long PRESS_IN_MS = 90, PRESS_OUT_MS = 480;

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private final int icon;
    private final int accent, onAccent;
    private final float pressedRadius, activeRadius, activePressedRadius;
    private boolean active;
    private float shape;   // 0 = 동그라미, 1 = 모서리가 둥근 네모 (켜짐). 스프링으로 오가므로 조금 넘을 수 있다
    private ValueAnimator shapeAnim;
    private float press;   // 0 = 놓음, 1 = 누름 (스프링으로 0 아래로 조금 내려갈 수 있다)
    private ValueAnimator pressAnim;
    private boolean down;

    /**
     * @param accent   버튼 면 색 (강조색)
     * @param onAccent 강조색 위의 아이콘 색
     */
    public ExpressiveIconButton(Context c, int icon, int accent, int onAccent, String description) {
        super(c);
        this.icon = icon;
        this.accent = accent;
        this.onAccent = onAccent;
        this.pressedRadius = Ui.dp(c, 12);
        this.activeRadius = Ui.dp(c, 14);
        this.activePressedRadius = Ui.dp(c, 8);
        setContentDescription(description);
        setClickable(true);
        setFocusable(true);
    }

    /** 켜짐 상태: 옅은 바탕에 강조색 아이콘. */
    public void setActive(boolean on) {
        if (active == on) return;
        active = on;
        if (shapeAnim != null) shapeAnim.cancel();
        shapeAnim = ValueAnimator.ofFloat(shape, on ? 1f : 0f);
        shapeAnim.setDuration(PRESS_OUT_MS);
        shapeAnim.setInterpolator(Spring.BOUNCY);
        shapeAnim.addUpdateListener(a -> {
            shape = (float) a.getAnimatedValue();
            invalidate();
        });
        shapeAnim.start();
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float w = getWidth(), h = getHeight();
        float full = Math.min(w, h) / 2f;
        float p = Math.max(-0.3f, Math.min(1f, press));
        float sh = Math.max(-0.2f, Math.min(1.2f, shape));
        float rest = full + (activeRadius - full) * sh;                           // 동그라미 ↔ 켜짐의 둥근 네모
        float pressed = pressedRadius + (activePressedRadius - pressedRadius) * sh;
        float radius = Math.max(0f, Math.min(full, rest + (pressed - rest) * p));
        // 누르면 모양이 가운데로 살짝 줄어든다 (놓을 때는 스프링으로 원래 크기를 조금 넘었다 돌아온다).
        float inset = Math.min(w, h) * 0.06f * p;
        rect.set(inset, inset, w - inset, h - inset);
        int ink = active ? accent : onAccent;
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(active ? (accent & 0x00FFFFFF) | 0x33000000 : accent);
        canvas.drawRoundRect(rect, radius, radius, paint);
        if (p > 0.01f) {   // 누른 느낌: 면 위에 아이콘 색을 옅게 얹는다
            paint.setColor((ink & 0x00FFFFFF) | (Math.round(0x29 * Math.min(1f, p)) << 24));
            canvas.drawRoundRect(rect, radius, radius, paint);
        }
        paint.setColor(ink);
        float iconSize = Math.min(Math.min(w, h) * 0.5f, Ui.dp(getContext(), 24)) * (1f - 0.06f * Math.max(0f, p));
        Icons.draw(canvas, icon, w / 2f, h / 2f, iconSize, paint);
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
    protected void onDetachedFromWindow() {
        if (pressAnim != null) pressAnim.cancel();
        if (shapeAnim != null) shapeAnim.cancel();
        super.onDetachedFromWindow();
    }
}
