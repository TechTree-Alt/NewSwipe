package com.alternative_studios.newswipe.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.view.View;

/** 오른쪽을 가리키는 화살표(›). 글자 대신 직접 그려서 폰트에 따라 위아래로 치우치지 않는다. */
public final class Chevron extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final float boxW, boxH;

    public Chevron(Context context, int color) {
        super(context);
        paint.setColor(color);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(Ui.dp(context, 2));
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeJoin(Paint.Join.ROUND);
        boxW = Ui.dp(context, 24);
        boxH = Ui.dp(context, 24);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        setMeasuredDimension(Math.round(boxW), Math.round(boxH));
    }

    @Override
    protected void onDraw(Canvas c) {
        float cx = getWidth() / 2f, cy = getHeight() / 2f;
        float hw = Ui.dp(getContext(), 3.5f), hh = Ui.dp(getContext(), 6.5f);
        path.reset();
        path.moveTo(cx - hw, cy - hh);
        path.lineTo(cx + hw, cy);
        path.lineTo(cx - hw, cy + hh);
        c.drawPath(path, paint);
    }
}
