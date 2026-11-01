package com.alternative_studios.newswipe.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.widget.OverScroller;

import com.alternative_studios.newswipe.keyboard.KeyboardTheme;

import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * 일본어·중국어 변환 후보. 후보를 글자 칸으로 직접 그린다 (후보마다 뷰를 만들지 않는다).
 * <ul>
 *   <li>막대(strip): 도구 막대 자리에 한 줄로 놓고 좌우로 밀어 넘긴다. 오른쪽 끝의 펼치기 단추로 격자를 연다.</li>
 *   <li>격자(grid): 자판 자리를 덮어 후보를 여러 줄로 보이고 위아래로 넘긴다.</li>
 * </ul>
 */
public final class CandidateView extends View {
    public interface Listener {
        void onCandidate(int index);

        /** 막대의 펼치기 단추 (격자를 열거나 닫는다). */
        void onExpand();
    }

    private final boolean grid;
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG), fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint.FontMetrics fm = new Paint.FontMetrics();
    private final Path chevron = new Path();
    private final OverScroller scroller;
    private final GestureDetector gestures;
    private final RectF tmp = new RectF();
    private KeyboardTheme theme;
    private Listener listener;
    private List<String> items = Collections.emptyList();
    private int highlight = -1;
    private boolean expanded;
    private float[] cx = new float[0], cy = new float[0], cw = new float[0], ch = new float[0];
    private float contentSize;
    private int scroll;
    private int pressed = -1;
    private boolean pressedExpand;
    private final float pad, minCell, expandW, rowH;

    public CandidateView(Context context, boolean grid) {
        super(context);
        this.grid = grid;
        text.setTextAlign(Paint.Align.CENTER);
        pad = Ui.dp(context, 10);
        minCell = Ui.dp(context, grid ? 52 : 40);
        expandW = grid ? 0 : Ui.dp(context, 40);
        rowH = Ui.dp(context, 50);
        scroller = new OverScroller(context);
        gestures = new GestureDetector(context, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onDown(MotionEvent e) {
                scroller.forceFinished(true);
                pressedExpand = !grid && e.getX() >= getWidth() - expandW;
                pressed = pressedExpand ? -1 : hit(e.getX(), e.getY());
                invalidate();
                return true;
            }

            @Override
            public boolean onScroll(MotionEvent a, MotionEvent b, float dx, float dy) {
                pressed = -1;
                pressedExpand = false;
                scrollTo(scroll + Math.round(grid ? dy : dx));
                return true;
            }

            @Override
            public boolean onFling(MotionEvent a, MotionEvent b, float vx, float vy) {
                pressed = -1;
                int max = maxScroll();
                if (grid) scroller.fling(0, scroll, 0, Math.round(-vy), 0, 0, 0, max);
                else scroller.fling(scroll, 0, Math.round(-vx), 0, 0, max, 0, 0);
                postInvalidateOnAnimation();
                return true;
            }

            @Override
            public boolean onSingleTapUp(MotionEvent e) {
                if (listener == null) return true;
                if (pressedExpand) listener.onExpand();
                else if (pressed >= 0) listener.onCandidate(pressed);
                return true;
            }
        });
    }

    public void setTheme(KeyboardTheme t) {
        theme = t;
        invalidate();
    }

    public void setListener(Listener l) {
        listener = l;
    }

    /** 후보 글자를 그릴 언어 (같은 한자도 일본어·중국어 글꼴 모양이 다르다). */
    public void setLanguage(String lang) {
        Locale l = "ja".equals(lang) ? Locale.JAPAN : Locale.SIMPLIFIED_CHINESE;
        if (!l.equals(text.getTextLocale())) text.setTextLocale(l);
    }

    /** 막대의 펼치기 단추 모양 (격자가 열려 있으면 위를 가리킨다). */
    public void setExpanded(boolean on) {
        if (expanded != on) {
            expanded = on;
            invalidate();
        }
    }

    public void setCandidates(List<String> list, int highlight) {
        boolean same = list.equals(items);
        if (same && highlight == this.highlight) return;   // 그대로면 다시 그리지 않는다
        items = list;
        this.highlight = highlight;
        if (!same) {
            scroller.forceFinished(true);
            scroll = 0;
            pressed = -1;
            measureCells();
        }
        if (highlight >= 0) reveal(highlight);
        invalidate();
    }

    public boolean isEmpty() {
        return items.isEmpty();
    }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        measureCells();
    }

    private void measureCells() {
        int n = items.size();
        cx = new float[n];
        cy = new float[n];
        cw = new float[n];
        ch = new float[n];
        text.setTextSize(Ui.dp(getContext(), grid ? 19 : 18));
        float width = getWidth();
        if (!grid) {
            float x = 0;
            for (int i = 0; i < n; i++) {
                cw[i] = Math.max(minCell, text.measureText(items.get(i)) + 2 * pad);
                ch[i] = getHeight();
                cx[i] = x;
                x += cw[i];
            }
            contentSize = x;
            return;
        }
        // 격자: 칸 폭을 기본 폭의 배수로 맞춰 줄마다 세로로 가지런하게 놓는다.
        int columns = Math.max(1, Math.round(width / Math.max(minCell, Ui.dp(getContext(), 64))));
        float unit = width / columns;
        float x = 0, y = 0;
        for (int i = 0; i < n; i++) {
            float need = text.measureText(items.get(i)) + 2 * pad;
            float w = Math.min(width, (float) Math.ceil(need / unit) * unit);
            if (x > 0 && x + w > width + 0.5f) {
                x = 0;
                y += rowH;
            }
            cx[i] = x;
            cy[i] = y;
            cw[i] = w;
            ch[i] = rowH;
            x += w;
        }
        contentSize = n == 0 ? 0 : y + rowH;
    }

    private int maxScroll() {
        float visible = grid ? getHeight() : getWidth() - expandW;
        return Math.max(0, Math.round(contentSize - visible));
    }

    private void scrollTo(int s) {
        int v = Math.max(0, Math.min(maxScroll(), s));
        if (v != scroll) {
            scroll = v;
            invalidate();
        }
    }

    /** 스페이스로 고른 후보가 보이게 넘긴다. */
    private void reveal(int i) {
        if (i >= cx.length) return;
        float visible = grid ? getHeight() : getWidth() - expandW;
        float start = grid ? cy[i] : cx[i], size = grid ? ch[i] : cw[i];
        if (start < scroll) scrollTo(Math.round(start));
        else if (start + size > scroll + visible) scrollTo(Math.round(start + size - visible));
    }

    private int hit(float x, float y) {
        float px = grid ? x : x + scroll, py = grid ? y + scroll : y;
        for (int i = 0; i < cx.length; i++) {
            if (px >= cx[i] && px < cx[i] + cw[i] && py >= cy[i] && py < cy[i] + ch[i]) return i;
        }
        return -1;
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        boolean r = gestures.onTouchEvent(e);
        if (e.getActionMasked() == MotionEvent.ACTION_UP || e.getActionMasked() == MotionEvent.ACTION_CANCEL) {
            pressed = -1;
            pressedExpand = false;
            invalidate();
        }
        return r;
    }

    @Override
    public void computeScroll() {
        if (scroller.computeScrollOffset()) {
            scrollTo(grid ? scroller.getCurrY() : scroller.getCurrX());
            postInvalidateOnAnimation();
        }
    }

    @Override
    protected void onDraw(Canvas c) {
        if (theme == null) return;
        if (grid) c.drawColor(theme.background);
        float right = getWidth() - expandW;
        text.getFontMetrics(fm);
        float radius = Ui.dp(getContext(), 8), inset = Ui.dp(getContext(), 3);
        c.save();
        c.clipRect(0, 0, grid ? getWidth() : right, getHeight());
        for (int i = 0; i < cx.length; i++) {
            float x = grid ? cx[i] : cx[i] - scroll, y = grid ? cy[i] - scroll : 0;
            if (x + cw[i] < 0 || x > right || y + ch[i] < 0 || y > getHeight()) continue;
            if (i == pressed || i == highlight) {
                fill.setColor(i == pressed ? theme.keyPressed : theme.key);
                tmp.set(x + inset, y + inset, x + cw[i] - inset, y + ch[i] - inset);
                c.drawRoundRect(tmp, radius, radius, fill);
            }
            text.setColor(theme.text);
            text.setFakeBoldText(i == 0 && highlight < 0 && !grid);   // 스페이스로 들어갈 첫 후보
            c.drawText(items.get(i), x + cw[i] / 2f, y + ch[i] / 2f - (fm.ascent + fm.descent) / 2f, text);
        }
        text.setFakeBoldText(false);
        c.restore();
        if (!grid) drawExpand(c, right);
    }

    private void drawExpand(Canvas c, float left) {
        float cxm = left + expandW / 2f, cym = getHeight() / 2f;
        if (pressedExpand) {
            fill.setColor(theme.keyPressed);
            c.drawCircle(cxm, cym, Math.min(expandW, getHeight()) * 0.4f, fill);
        }
        fill.setColor(theme.text);
        fill.setStyle(Paint.Style.STROKE);
        fill.setStrokeWidth(Ui.dp(getContext(), 2));
        fill.setStrokeCap(Paint.Cap.ROUND);
        float hw = Ui.dp(getContext(), 6), hh = Ui.dp(getContext(), 3.5f) * (expanded ? -1 : 1);
        chevron.reset();
        chevron.moveTo(cxm - hw, cym - hh);
        chevron.lineTo(cxm, cym + hh);
        chevron.lineTo(cxm + hw, cym - hh);
        c.drawPath(chevron, fill);
        fill.setStyle(Paint.Style.FILL);
    }
}
