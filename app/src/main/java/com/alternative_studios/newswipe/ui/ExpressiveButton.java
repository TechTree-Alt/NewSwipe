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
    /** 글자 앞에 그리는 아이콘 (Icons.*, 0이면 없음)과 그 크기·글자와의 간격. */
    private int icon;
    private float iconSize, iconGap;

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
        if (Metro.on) setTypeface(Metro.regular());
        setClickable(true);
        setFocusable(true);
        if (NeuUi.on) NeuUi.unclip(this, NeuUi.SMALL);   // 솟아오른 그림자가 잘리지 않게
        // 그림자(elevation)를 줄 때 그림자도 눌린 모양을 따라가게 한다.
        setOutlineProvider(new android.view.ViewOutlineProvider() {
            @Override
            public void getOutline(android.view.View view, android.graphics.Outline outline) {
                float p = Math.max(-0.3f, Math.min(1f, press));
                float w = view.getWidth(), h = view.getHeight();
                float inset = Math.min(w, h) * 0.04f * p;
                outline.setRoundRect(Math.round(inset), Math.round(inset), Math.round(w - inset), Math.round(h - inset),
                        radius(h, p));
            }
        });
    }

    /**
     * 작은 알약 (미리보기 열기·닫기처럼 화면 위에 뜨는 보조 버튼): 높이 height에 맞게 글자를 줄이고 최소 크기를 없앤다.
     * 누를 때의 모양·진동은 그대로다.
     */
    public ExpressiveButton compact(float textSp, int padStartPx, int padEndPx) {
        setTextSize(TypedValue.COMPLEX_UNIT_SP, textSp);
        setMinHeight(0);
        setMinWidth(0);
        setMinimumHeight(0);
        setMinimumWidth(0);
        // setSingleLine은 가로 스크롤을 켜서, 가운데 정렬이면 TextView가 캔버스를 멀리 스크롤해 아래 onDraw에서
        // 직접 그리는 면·아이콘이 화면 밖으로 밀린다. 줄 수만 하나로 막는다.
        setHorizontallyScrolling(false);
        setMaxLines(1);
        setPadding(padStartPx, 0, padEndPx, 0);
        return this;
    }

    /** 글자 앞에 아이콘을 둔다 (0이면 뺀다). 아이콘 자리만큼 왼쪽 안쪽 여백이 늘어난다. */
    public ExpressiveButton setLeadingIcon(int icon) {
        float size = Ui.dp(getContext(), 18), gap = Ui.dp(getContext(), 6);
        int was = this.icon == 0 ? 0 : Math.round(iconSize + iconGap);
        int now = icon == 0 ? 0 : Math.round(size + gap);
        this.icon = icon;
        this.iconSize = size;
        this.iconGap = gap;
        if (was != now) setPadding(getPaddingLeft() - was + now, getPaddingTop(), getPaddingRight(), getPaddingBottom());
        invalidate();
        return this;
    }

    private float radius(float h, float p) {
        if (Metro.on) return 0f;   // Metro: 모서리 없는 네모 (강조색 타일)
        float full = h / 2f;
        return Math.max(0f, Math.min(full, full + (pressedRadius - full) * p));
    }

    /**
     * Neumorphism: 강조색 면이 바탕에서 솟아오르고, 누르는 동안 안으로 들어간다 (알약 모양 그대로).
     */
    private void drawNeu(Canvas canvas) {
        float sx = getScrollX(), sy = getScrollY();
        float w = getWidth(), h = getHeight();
        rect.set(sx, sy, sx + w, sy + h);
        float r = h / 2f;
        if (press > 0.5f) {
            com.alternative_studios.newswipe.ui.NeuUi.drawInset(getContext(), canvas, rect, r,
                    Neu.blend(fill, 0xFF000000, 0.08f), paint);
        } else {
            com.alternative_studios.newswipe.ui.NeuUi.drawRaised(getContext(), canvas, rect, r, com.alternative_studios.newswipe.ui.NeuUi.SMALL);
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(fill);
            canvas.drawRoundRect(rect, r, r, paint);
        }
        if (icon != 0) {
            paint.setColor(ink);
            float cx = sx + getPaddingLeft() - iconGap - iconSize / 2f;
            com.alternative_studios.newswipe.keyboard.Icons.draw(canvas, icon, cx, sy + h / 2f, iconSize, paint, 2.4f);
            paint.setStyle(Paint.Style.FILL);
        }
        super.onDraw(canvas);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (com.alternative_studios.newswipe.ui.NeuUi.on) {
            drawNeu(canvas);
            return;
        }
        // 캔버스는 TextView의 스크롤만큼 옮겨져 있다. 면·아이콘은 그만큼 더해 뷰 자리에 그린다 (글자는 super.onDraw가 맞춰 그린다).
        float sx = getScrollX(), sy = getScrollY();
        float w = getWidth(), h = getHeight();
        float p = Math.max(-0.3f, Math.min(1f, press));
        float radius = radius(h, p);
        // 누르면 모양이 가운데로 살짝 줄어든다 (놓을 때는 스프링으로 원래 크기를 조금 넘었다 돌아온다).
        // Metro에서는 모양은 그대로 두고 뷰 전체가 기울어진다 (틸트).
        float inset = Metro.on ? 0f : Math.min(w, h) * 0.04f * p;
        rect.set(sx + inset, sy + inset, sx + w - inset, sy + h - inset);
        paint.setColor(fill);
        canvas.drawRoundRect(rect, radius, radius, paint);
        if (p > 0.01f) {   // 누른 느낌: 면 위에 글자색을 옅게 얹는다
            paint.setColor((ink & 0x00FFFFFF) | (Math.round(0x29 * Math.min(1f, p)) << 24));
            canvas.drawRoundRect(rect, radius, radius, paint);
        }
        canvas.save();
        float textScale = Metro.on ? 1f : 1f - 0.04f * Math.max(0f, p);
        canvas.scale(textScale, textScale, sx + w / 2f, sy + h / 2f);
        if (icon != 0) {   // 글자 바로 앞 (왼쪽 안쪽 여백의 끝)
            paint.setColor(ink);
            float cx = sx + getPaddingLeft() - iconGap - iconSize / 2f;
            com.alternative_studios.newswipe.keyboard.Icons.draw(canvas, icon, cx, sy + h / 2f, iconSize, paint, 2.4f);
            paint.setStyle(Paint.Style.FILL);
        }
        super.onDraw(canvas);
        canvas.restore();
    }

    private void animatePress(boolean toDown) {
        if (pressAnim != null) pressAnim.cancel();
        pressAnim = ValueAnimator.ofFloat(press, toDown ? 1f : 0f);
        pressAnim.setDuration(toDown ? PRESS_IN_MS : Metro.on ? 100 : PRESS_OUT_MS);
        pressAnim.setInterpolator(toDown ? new DecelerateInterpolator() : Metro.on ? Metro.LOG : Spring.BOUNCY);
        pressAnim.addUpdateListener(a -> {
            press = (float) a.getAnimatedValue();
            invalidate();
            invalidateOutline();
        });
        pressAnim.start();
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        if (!isEnabled() || !isClickable()) return super.onTouchEvent(e);
        Metro.onTiltTouch(this, e);
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
