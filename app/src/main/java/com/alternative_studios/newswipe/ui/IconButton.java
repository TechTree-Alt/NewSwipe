package com.alternative_studios.newswipe.ui;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.drawable.InsetDrawable;
import android.graphics.drawable.RippleDrawable;
import android.view.View;

import com.alternative_studios.newswipe.keyboard.Icons;
import com.alternative_studios.newswipe.keyboard.KeyboardTheme;

/** 코드로 그린 아이콘 하나를 보여 주는 버튼. */
public final class IconButton extends View {
    /** 칩 배경을 씌운 도구 버튼의 선 굵기 (24칸 그림 기준). 자판 기능키 아이콘(⌫, 🌐 등)과 같은 굵기로 보이게 줄인다. */
    private static final float CHIP_STROKE = 1.5f;
    private float stroke = 1.9f;
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

    /**
     * 배경을 칩으로 바꾼다: 버튼 영역에서 좌우 insetH, 위아래 insetV만큼 들어간 둥근 바탕(fill)이고, 누르면 pressed 색으로
     * 물결이 퍼진다. 터치 영역은 버튼 전체 그대로다.
     */
    public void setChip(int fill, int pressed, int insetH, int insetV, float radius) {
        // 도구 버튼 아이콘은 자판 기능키 아이콘보다 조금 크게 그려서, 같은 굵기(1.9)로는 더 굵어 보인다.
        stroke = CHIP_STROKE;
        invalidate();
        InsetDrawable content = new InsetDrawable(Ui.round(fill, radius), insetH, insetV, insetH, insetV);
        InsetDrawable mask = new InsetDrawable(Ui.round(0xFFFFFFFF, radius), insetH, insetV, insetH, insetV);
        setBackground(new RippleDrawable(ColorStateList.valueOf(pressed), content, mask));
    }

    /**
     * 도구 막대 버튼의 모양: 기능키 색 칩 바탕에 글자 색 아이콘. 키보드의 도구 막대와 설정 화면의 미리보기가 같이 쓴다.
     * @param barHeight 버튼이 놓이는 도구 막대 높이 (px). 칩은 여기서 위아래를 조금 뺀 높이다.
     */
    public void setToolbarStyle(KeyboardTheme theme, int barHeight) {
        Context c = getContext();
        setColor(theme.text);
        int insetV = Math.max(0, (barHeight - Ui.chipHeight(c, barHeight)) / 2);
        // 눌린 키 색은 밝은 테마의 기능키 색과 거의 같아서, 누를 때는 글자 색을 옅게 덮어 눌린 것이 보이게 한다.
        setChip(theme.functionKey, Ui.tint(theme.text), Ui.dp(c, 2), insetV, Ui.chipRadius(c));
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
        Icons.draw(canvas, icon, getWidth() / 2f, getHeight() / 2f, size, paint, stroke);
    }
}
