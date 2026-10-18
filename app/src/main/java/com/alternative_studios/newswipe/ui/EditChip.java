package com.alternative_studios.newswipe.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.text.TextPaint;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;

import com.alternative_studios.newswipe.keyboard.Icons;

/**
 * 글자를 선택했을 때 뜨는 편집 칩 하나: 둥근 바탕 한가운데에 아이콘과 이름(잘라내기·복사 등)을 나란히 그린다.
 * 칩이 좁아 이름이 다 들어가지 않으면 이름은 빼고 아이콘만 보인다 (이름은 접근성 설명으로 남는다).
 * 줄의 칩들은 함께 바뀐다: 한 칩이라도 이름이 들어가지 않으면 모두 아이콘만 보여 줄이 들쭉날쭉하지 않다.
 */
public final class EditChip extends View {
    private final Paint iconPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint textPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final int icon, padH, iconSize, gap;
    private final String label;

    public EditChip(Context c, int icon, String label, int color, int fill, int pressed) {
        super(c);
        this.icon = icon;
        this.label = label;
        iconPaint.setColor(color);
        textPaint.setColor(color);
        textPaint.setTextSize(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 14,
                c.getResources().getDisplayMetrics()));
        padH = Ui.dp(c, 6);
        iconSize = Ui.dp(c, 16);
        gap = Ui.dp(c, 4);
        float r = Ui.chipRadius(c);
        setBackground(Ui.ripple(pressed, Ui.round(fill, r), r));
        setContentDescription(label);
        setClickable(true);
        setFocusable(true);
    }

    /** 이 칩의 폭에 아이콘과 이름이 다 들어가는지. */
    private boolean fits() {
        return 2 * padH + iconSize + gap + textPaint.measureText(label) <= getWidth();
    }

    /** 같은 줄의 편집 칩이 모두 이름까지 들어가는지. */
    private boolean rowFits() {
        if (!(getParent() instanceof ViewGroup)) return fits();
        ViewGroup row = (ViewGroup) getParent();
        for (int i = 0; i < row.getChildCount(); i++) {
            View v = row.getChildAt(i);
            if (v instanceof EditChip && v.getVisibility() == VISIBLE && !((EditChip) v).fits()) return false;
        }
        return true;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float cy = getHeight() / 2f;
        float textW = textPaint.measureText(label);
        boolean text = rowFits();
        // 아이콘과 이름을 한 덩어리로 칩 가운데에 둔다. 이름이 들어가지 않으면 아이콘만 가운데에.
        float left = (getWidth() - (text ? iconSize + gap + textW : iconSize)) / 2f;
        Icons.draw(canvas, icon, left + iconSize / 2f, cy, iconSize, iconPaint);
        if (text) {
            Paint.FontMetrics fm = textPaint.getFontMetrics();
            float baseline = cy - (fm.ascent + fm.descent) / 2f;
            canvas.drawText(label, left + iconSize + gap, baseline, textPaint);
        }
    }
}
