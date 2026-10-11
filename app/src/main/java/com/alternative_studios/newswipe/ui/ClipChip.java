package com.alternative_studios.newswipe.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.text.TextPaint;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.View;
import android.widget.FrameLayout;

import com.alternative_studios.newswipe.keyboard.Icons;

/**
 * 방금 복사한 내용을 붙여 넣는 칩: 둥근 바탕에 클립보드 아이콘과 내용 미리보기 한 줄.
 * 자리가 좁으면 글은 뒤를 …로 줄이고, 글을 넣을 자리가 없으면 아이콘만 보인다.
 * 높이는 도구 막대 높이에서 위아래를 조금 남긴 만큼이라 자동 완성 칩과 같은 크기로 보인다.
 * 칩은 {@link Slot}에 넣어 도구 막대의 한가운데에 놓는다.
 */
public final class ClipChip extends View {
    private final Paint iconPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint textPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final int padH, iconSize, gap, minHeight, inset;
    private String label = "";
    /** 지금 폭에 맞게 줄인 글 (그릴 자리가 없으면 빈 글). */
    private CharSequence fitted = "";

    public ClipChip(Context c, int textColor, int iconColor, int fill, int pressed) {
        super(c);
        iconPaint.setColor(iconColor);
        textPaint.setColor(textColor);
        textPaint.setTextSize(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 14,
                c.getResources().getDisplayMetrics()));
        padH = Ui.dp(c, 12);
        iconSize = Ui.dp(c, 18);
        gap = Ui.dp(c, 6);
        minHeight = Ui.dp(c, 24);
        inset = Ui.dp(c, 6);
        float r = Ui.dp(c, 14);
        setBackground(Ui.ripple(pressed, Ui.round(fill, r), r));
        setClickable(true);
        setFocusable(true);
    }

    /** 미리보기 글을 바꾼다 (한 줄로 보인다). */
    public void setLabel(String s) {
        String next = s == null ? "" : s;
        if (next.equals(label)) return;
        label = next;
        requestLayout();
        invalidate();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int want = padH + iconSize + (label.isEmpty() ? 0 : gap + (int) Math.ceil(textPaint.measureText(label))) + padH;
        int w = resolveSize(want, widthMeasureSpec);
        // 도구 막대 높이에서 위아래로 조금씩 남긴다 (막대가 아주 낮으면 남기지 않는다).
        int specH = MeasureSpec.getSize(heightMeasureSpec);
        int h = MeasureSpec.getMode(heightMeasureSpec) == MeasureSpec.UNSPECIFIED ? minHeight + 2 * inset
                : Math.max(Math.min(specH, minHeight), specH - 2 * inset);
        setMeasuredDimension(w, h);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        refit(w);
    }

    @Override
    public void requestLayout() {
        super.requestLayout();
        fitted = null;   // 글이 바뀌었을 수 있다. 그릴 때 다시 맞춘다.
    }

    private void refit(int w) {
        float avail = w - 2 * padH - iconSize - gap;
        if (label.isEmpty()) {
            fitted = "";
        } else if (textPaint.measureText(label) <= avail + 0.5f) {
            fitted = label;   // 다 들어가면 짧은 글(한두 글자)도 그대로 보인다
        } else {
            // 줄여야 하는데 두어 글자도 들어가지 않으면 글은 빼고 아이콘만 보인다.
            fitted = avail < textPaint.measureText("가…") ? ""
                    : TextUtils.ellipsize(label, textPaint, avail, TextUtils.TruncateAt.END);
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (fitted == null) refit(getWidth());
        float cy = getHeight() / 2f;
        boolean text = fitted.length() > 0;
        // 글이 없으면 아이콘을 가운데에 둔다.
        float iconX = text ? padH + iconSize / 2f : getWidth() / 2f;
        Icons.draw(canvas, Icons.CLIPBOARD, iconX, cy, iconSize, iconPaint);
        if (text) {
            Paint.FontMetrics fm = textPaint.getFontMetrics();
            float baseline = cy - (fm.ascent + fm.descent) / 2f;
            canvas.drawText(fitted, 0, fitted.length(), padH + iconSize + gap, baseline, textPaint);
        }
    }

    /**
     * 칩의 자리: 칩을 이 자리가 아니라 부모(도구 막대)의 한가운데에 놓는다. 양옆 도구 버튼 때문에 자리가 한쪽으로
     * 치우쳐 칩이 가운데에 들어가지 못할 때만 자리 안쪽으로 밀어 넣는다. 칩은 하나만 넣는다.
     */
    public static final class Slot extends FrameLayout {
        public Slot(Context c) {
            super(c);
        }

        @Override
        protected void onLayout(boolean changed, int l, int t, int r, int b) {
            View c = getChildCount() > 0 ? getChildAt(0) : null;
            if (c == null || c.getVisibility() == GONE) return;
            FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) c.getLayoutParams();
            int w = c.getMeasuredWidth(), h = c.getMeasuredHeight();
            int slotW = r - l;
            View parent = getParent() instanceof View ? (View) getParent() : null;
            int parentW = parent != null && parent.getWidth() > 0 ? parent.getWidth() : slotW;
            // 도구 막대의 가운데를 이 자리 기준 좌표로 옮겨, 칩의 가운데를 거기에 맞춘다.
            int left = parentW / 2 - l - w / 2;
            int max = slotW - lp.rightMargin - w;
            left = Math.max(lp.leftMargin, Math.min(left, max));
            int top = (b - t - h) / 2;
            c.layout(left, top, left + w, top + h);
        }
    }
}
