package com.alternative_studios.newswipe.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.View;

import com.alternative_studios.newswipe.keyboard.Icons;

/**
 * 코드로 그린 아이콘을 둥근 네모 바탕 위에 보여 주는 장식용 뷰 (설정 메뉴 줄의 왼쪽 아이콘).
 * 눌리지 않는 뷰라서 안에 넣은 줄의 누르기를 가로채지 않는다.
 */
public final class IconView extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final int icon;
    private final float iconSize;
    private final float boxSize;

    /** @param color 아이콘 색. 바탕은 같은 색을 옅게 깐다. */
    public IconView(Context context, int icon, int color, int boxDp, int iconDp) {
        super(context);
        this.icon = icon;
        paint.setColor(color);
        boxSize = Ui.dp(context, boxDp);
        iconSize = Ui.dp(context, iconDp);
        setBackground(Ui.round((color & 0x00FFFFFF) | 0x1F000000, Ui.dp(context, 12)));
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        setMeasuredDimension(Math.round(boxSize), Math.round(boxSize));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        Icons.draw(canvas, icon, getWidth() / 2f, getHeight() / 2f, iconSize, paint);
    }
}
