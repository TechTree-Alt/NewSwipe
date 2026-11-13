package com.alternative_studios.newswipe;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.View;

import com.alternative_studios.newswipe.keyboard.KeyboardTheme;

/**
 * 글자 키 힌트의 자리를 고르는 그림: 키 하나를 크게 그리고 그 둘레 여덟 곳(Prefs.HINT_POS_*) 가운데 하나를 눌러 고른다.
 * 고른 자리에는 힌트 글자를 강조 색 동그라미 안에, 나머지 자리에는 빈 동그라미를 그린다.
 * 다른 힌트가 이미 쓰는 자리(blocked)는 그 힌트 글자만 흐리게 그리고 고를 수 없다.
 */
final class HintPositionPicker extends View {
    interface OnPick {
        void onPick(int position);
    }

    private final KeyboardTheme theme;
    private final int accent, onAccent;
    private final OnPick onPick;
    /** 키 가운데 글자, 고를 힌트 글자, 다른 힌트가 쓰는 자리의 글자, 접근성 설명 앞머리. */
    private final String label, hint, blockedHint, name;
    /** 다른 힌트가 쓰고 있어 고를 수 없는 자리 (없으면 -1). */
    private final int blocked;
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint ring = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF key = new RectF();
    private final float[] px = new float[8], py = new float[8];
    private int selected;
    /** 손가락이 올라가 있는 자리 (없으면 -1). */
    private int pressed = -1;

    HintPositionPicker(Context c, KeyboardTheme theme, int accent, int onAccent, int selected, OnPick onPick) {
        this(c, theme, accent, onAccent, "ㄱ", "ㄲ", L.t("글자 키 힌트 위치", "Letter key hint position"), selected, -1, null, onPick);
    }

    HintPositionPicker(Context c, KeyboardTheme theme, int accent, int onAccent, String label, String hint, String name,
                       int selected, int blocked, String blockedHint, OnPick onPick) {
        super(c);
        this.label = label;
        this.hint = hint;
        this.name = name;
        this.blocked = blocked;
        this.blockedHint = blockedHint;
        this.theme = theme;
        this.accent = accent;
        this.onAccent = onAccent;
        this.selected = selected;
        this.onPick = onPick;
        ring.setStyle(Paint.Style.STROKE);
        ring.setStrokeWidth(dp(1.5f));
        text.setTextAlign(Paint.Align.CENTER);
        setFocusable(true);
        updateDescription();
    }

    private float dp(float v) {
        return v * getResources().getDisplayMetrics().density;
    }

    private void updateDescription() {
        setContentDescription(name + ": " + Prefs.hintPositionNames()[selected]);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int w = View.MeasureSpec.getSize(widthMeasureSpec);
        if (View.MeasureSpec.getMode(widthMeasureSpec) == View.MeasureSpec.UNSPECIFIED) w = Math.round(dp(280));
        setMeasuredDimension(w, Math.round(dp(196)));
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        float kw = Math.min(w - dp(32), dp(232)), kh = dp(164);
        key.set((w - kw) / 2f, (h - kh) / 2f, (w + kw) / 2f, (h + kh) / 2f);
        float inset = dp(26);
        float l = key.left + inset, r = key.right - inset, t = key.top + inset, b = key.bottom - inset;
        float cx = key.centerX(), cy = key.centerY();
        // 왼쪽 위부터 시계 방향 (Prefs.HINT_POS_* 차례).
        float[][] p = {{l, t}, {cx, t}, {r, t}, {r, cy}, {r, b}, {cx, b}, {l, b}, {l, cy}};
        for (int i = 0; i < 8; i++) {
            px[i] = p[i][0];
            py[i] = p[i][1];
        }
    }

    @Override
    protected void onDraw(Canvas c) {
        fill.setColor(theme.background);
        c.drawRoundRect(key.left - dp(8), key.top - dp(8), key.right + dp(8), key.bottom + dp(8), dp(20), dp(20), fill);
        fill.setColor(theme.key);
        c.drawRoundRect(key, dp(14), dp(14), fill);

        text.setColor(theme.text);
        text.setTextSize(dp(40));
        drawCentered(c, label, key.centerX(), key.centerY());

        float radius = dp(16);
        text.setTextSize(dp(16));
        for (int i = 0; i < 8; i++) {
            if (i == blocked) {
                if (blockedHint != null) {
                    text.setColor(theme.hint);
                    drawCentered(c, blockedHint, px[i], py[i]);
                }
            } else if (i == selected) {
                fill.setColor(accent);
                c.drawCircle(px[i], py[i], radius, fill);
                text.setColor(onAccent);
                drawCentered(c, hint, px[i], py[i]);
            } else {
                if (i == pressed) {
                    fill.setColor((accent & 0x00FFFFFF) | 0x33000000);
                    c.drawCircle(px[i], py[i], radius, fill);
                }
                ring.setColor(theme.hint);
                c.drawCircle(px[i], py[i], radius - dp(1), ring);
            }
        }
    }

    private void drawCentered(Canvas c, String s, float x, float y) {
        Paint.FontMetrics fm = text.getFontMetrics();
        c.drawText(s, x, y - (fm.ascent + fm.descent) / 2f, text);
    }

    /** 누른 곳에서 가장 가까운 자리. 키 가운데(글자 자리)나 너무 먼 곳이면 -1. */
    private int hit(float x, float y) {
        int best = -1;
        float bestD = dp(36) * dp(36);
        for (int i = 0; i < 8; i++) {
            if (i == blocked) continue;
            float dx = x - px[i], dy = y - py[i], d = dx * dx + dy * dy;
            if (d < bestD) {
                bestD = d;
                best = i;
            }
        }
        return best;
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_MOVE: {
                int h = hit(e.getX(), e.getY());
                if (h != pressed) {
                    pressed = h;
                    invalidate();
                }
                return true;
            }
            case MotionEvent.ACTION_UP: {
                int h = hit(e.getX(), e.getY());
                pressed = -1;
                if (h >= 0) {
                    selected = h;
                    updateDescription();
                    performClick();
                    onPick.onPick(h);
                }
                invalidate();
                return true;
            }
            case MotionEvent.ACTION_CANCEL:
                pressed = -1;
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
