package com.alternative_studios.newswipe.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.View;

import com.alternative_studios.newswipe.keyboard.Icons;

/** 코드로 그린 아이콘 하나를 보여 주는 버튼. */
public final class IconButton extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private int icon;
    private float iconSize;

    public IconButton(Context context, int icon, int color, int pressColor, String description) {
        super(context);
        this.icon = icon;
        this.iconSize = Ui.dp(context, 22);
        paint.setColor(color);
        setClickable(true);
        setFocusable(true);
        setContentDescription(description);
        setBackground(Ui.ripple(pressColor, null, Ui.dp(context, 20)));
    }

    public void setIcon(int icon) {
        if (this.icon == icon) return;
        this.icon = icon;
        invalidate();
    }

    /** 아이콘 크기 (px). 도구 막대 높이에 맞춰 바꾼다. */
    public void setIconSize(float px) {
        if (iconSize == px) return;
        iconSize = px;
        invalidate();
    }

    public void setColor(int color) {
        paint.setColor(color);
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float size = Math.min(iconSize, Math.min(getWidth(), getHeight()) * 0.7f);
        Icons.draw(canvas, icon, getWidth() / 2f, getHeight() / 2f, size, paint);
    }
}
