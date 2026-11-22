package com.alternative_studios.newswipe.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;

/**
 * Material 3 Expressive 스타일 슬라이더. 두꺼운 둥근 트랙을 손잡이(세로 막대)가 둘로 나누고,
 * 손잡이 양옆에 틈이 있다. 오른쪽 끝에는 작은 정지점이 있다.
 */
public final class ExpressiveSlider extends View {

    public interface OnChange {
        void onChange(int progress, boolean fromUser);
    }

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final RectF rect = new RectF();
    private final int accent;
    private final float trackH, handleW, handleH, gap, innerRadius, side;
    private final int touchSlop;
    private int max = 100;
    private int progress;
    private OnChange listener;
    private boolean dragging, pressed;
    private float downX, downY;

    public ExpressiveSlider(Context context, int accent) {
        super(context);
        this.accent = accent;
        trackH = Ui.dp(context, 10);
        handleW = Ui.dp(context, 4);
        handleH = Ui.dp(context, 28);
        gap = Ui.dp(context, 4);
        innerRadius = Ui.dp(context, 2);
        side = Ui.dp(context, 4);
        touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
        setClickable(true);
        setFocusable(true);
        if (NeuUi.on) NeuUi.unclip(this, NeuUi.SMALL);   // 손잡이 그림자가 잘리지 않게
    }

    public void setMax(int max) {
        this.max = Math.max(1, max);
        invalidate();
    }

    public void setProgress(int progress) {
        this.progress = Math.max(0, Math.min(max, progress));
        invalidate();
    }

    public void setOnChangeListener(OnChange l) {
        listener = l;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        // 눈에 보이는 크기는 작게 하되 터치 영역은 40dp 이상으로 둔다.
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), Math.max(Math.round(handleH), Ui.dp(getContext(), 40)));
    }

    /** 홈의 양끝: Neumorphism은 끝에 선 손잡이(반지름 11dp)와 그 그림자가 뷰 밖으로 잘리지 않게 더 띄운다. */
    private float edge() {
        return NeuUi.on ? Ui.dp(getContext(), 20) : side;
    }

    private float trackLeft() {
        return edge();
    }

    private float trackRight() {
        return getWidth() - edge();
    }

    private float handleX() {
        float usable = trackRight() - trackLeft();
        return trackLeft() + usable * progress / max;
    }

    /**
     * Metro UI 모드: Windows Phone 8의 슬라이더. 두툼한 네모 막대의 왼쪽을 강조색으로 채우고, 나머지는 글자 색을 옅게 깔며,
     * 지금 값 자리에 글자 색의 가는 네모 손잡이를 세운다.
     */
    private void drawMetro(Canvas c) {
        float dp = Ui.dp(getContext(), 1);
        float cy = getHeight() / 2f;
        float th = 10 * dp, hw = 6 * dp, hh = 22 * dp;
        float hx = handleX();
        float left = trackLeft(), right = trackRight();
        paint.setStyle(Paint.Style.FILL);
        paint.setColor((Metro.fg & 0x00FFFFFF) | 0x33000000);
        c.drawRect(left, cy - th / 2f, right, cy + th / 2f, paint);
        paint.setColor(accent);
        c.drawRect(left, cy - th / 2f, hx, cy + th / 2f, paint);
        paint.setColor(Metro.fg);
        float l = Math.max(left - hw / 2f, Math.min(right - hw / 2f, hx - hw / 2f));
        c.drawRect(l, cy - hh / 2f, l + hw, cy + hh / 2f, paint);
    }

    /**
     * Neumorphism: 안으로 들어간 홈, 그 안의 왼쪽을 강조색으로 채우고, 지금 값 자리에 솟아오른 바탕색 손잡이(가운데 강조색 점).
     */
    private void drawNeu(Canvas c) {
        float dp = Ui.dp(getContext(), 1);
        float cy = getHeight() / 2f;
        float th = 12 * dp, knob = (pressed ? 24 : 22) * dp;
        float left = trackLeft(), right = trackRight(), hx = handleX();
        rect.set(left - th / 2f, cy - th / 2f, right + th / 2f, cy + th / 2f);
        NeuUi.drawInset(getContext(), c, rect, th / 2f, NeuUi.surface, paint);
        float in = 3 * dp;
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(accent);
        rect.set(left - th / 2f + in, cy - th / 2f + in, Math.max(hx, left - th / 2f + in + (th - 2 * in)), cy + th / 2f - in);
        c.drawRoundRect(rect, rect.height() / 2f, rect.height() / 2f, paint);
        rect.set(hx - knob / 2f, cy - knob / 2f, hx + knob / 2f, cy + knob / 2f);
        NeuUi.drawRaised(getContext(), c, rect, knob / 2f, NeuUi.SMALL);
        paint.setColor(NeuUi.knob());
        c.drawCircle(hx, cy, knob / 2f, paint);
        paint.setColor(accent);
        c.drawCircle(hx, cy, 3.5f * dp, paint);
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
        float top = cy - trackH / 2f, bottom = cy + trackH / 2f;
        float hx = handleX();
        float hw = pressed ? handleW * 0.6f : handleW;
        float r = trackH / 2f;

        paint.setStyle(Paint.Style.FILL);
        // 활성 구간 (왼쪽): 바깥쪽은 둥글게, 손잡이 쪽은 작은 반지름
        float activeRight = hx - hw / 2f - gap;
        if (activeRight > trackLeft() + 1) {
            paint.setColor(accent);
            rect.set(trackLeft(), top, activeRight, bottom);
            path.reset();
            path.addRoundRect(rect, new float[]{r, r, innerRadius, innerRadius, innerRadius, innerRadius, r, r},
                    Path.Direction.CW);
            c.drawPath(path, paint);
        }
        // 비활성 구간 (오른쪽)
        float inactiveLeft = hx + hw / 2f + gap;
        if (inactiveLeft < trackRight() - 1) {
            paint.setColor((accent & 0x00FFFFFF) | 0x3D000000);
            rect.set(inactiveLeft, top, trackRight(), bottom);
            path.reset();
            path.addRoundRect(rect, new float[]{innerRadius, innerRadius, r, r, r, r, innerRadius, innerRadius},
                    Path.Direction.CW);
            c.drawPath(path, paint);
            // 끝 정지점
            paint.setColor(accent);
            c.drawCircle(trackRight() - r, cy, Ui.dp(getContext(), 1.5f), paint);
        }
        // 손잡이
        paint.setColor(accent);
        rect.set(hx - hw / 2f, cy - handleH / 2f, hx + hw / 2f, cy + handleH / 2f);
        c.drawRoundRect(rect, hw / 2f, hw / 2f, paint);
    }

    private void updateFromX(float x) {
        float usable = trackRight() - trackLeft();
        float f = usable <= 0 ? 0 : (x - trackLeft()) / usable;
        int value = Math.round(Math.max(0f, Math.min(1f, f)) * max);
        if (value != progress) {
            progress = value;
            invalidate();
            performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK);
            if (listener != null) listener.onChange(progress, true);
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        if (!isEnabled()) return false;   // 흐리게 막아 둔 슬라이더 (예: Metro UI의 키 곡률)
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                pressed = true;
                dragging = false;
                downX = e.getX();
                downY = e.getY();
                invalidate();
                return true;
            case MotionEvent.ACTION_MOVE:
                if (!dragging && Math.abs(e.getX() - downX) > touchSlop
                        && Math.abs(e.getX() - downX) > Math.abs(e.getY() - downY)) {
                    dragging = true;
                    if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(true);
                }
                if (dragging) updateFromX(e.getX());
                return true;
            case MotionEvent.ACTION_UP:
                if (!dragging) updateFromX(e.getX());   // 탭하면 그 위치로
                pressed = false;
                dragging = false;
                invalidate();
                performClick();
                return true;
            case MotionEvent.ACTION_CANCEL:
                pressed = false;
                dragging = false;
                invalidate();
                return true;
            default:
                return true;
        }
    }

    @Override
    public boolean performClick() {
        return super.performClick();
    }
}
