package com.alternative_studios.newswipe.edit;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.text.TextPaint;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.alternative_studios.newswipe.L;
import com.alternative_studios.newswipe.keyboard.Icons;
import com.alternative_studios.newswipe.keyboard.KeyboardTheme;
import com.alternative_studios.newswipe.ui.IconButton;
import com.alternative_studios.newswipe.ui.Neu;
import com.alternative_studios.newswipe.ui.Ui;

/**
 * 텍스트 편집 창. 클립보드 창처럼 자판과 도구 막대 자리를 덮는다.
 * <pre>
 *   [←] 텍스트 편집                    [↶] [↷]
 *   [ 단어« ][       위쪽 끝       ][ 단어» ]
 *   [       ][  ↖  ][  ↑  ][  ↗  ][       ]
 *   [왼쪽 끝][  ←  ][ 선택 ][  →  ][오른쪽 끝]
 *   [       ][  ↙  ][  ↓  ][  ↘  ][       ]
 *   [   ⌦   ][      아래쪽 끝      ][   ⌫   ]
 *   [잘라내기 ][  복사  ][ 붙여넣기 ][ 전체 선택 ]
 * </pre>
 * 가운데 '선택'을 누르면 선택 모드가 켜지고 꺼진다. 선택 모드에서는 이동 버튼이 커서 대신 선택 영역의 끝을 옮긴다.
 * 이동·지우기 버튼은 누르고 있으면 되풀이한다. 실제 동작은 키보드 서비스가 한다 ({@link Listener#onEditAction}).
 */
@SuppressLint("ViewConstructor")
public final class TextEditPanel extends LinearLayout {

    public interface Listener {
        void onEditBack();

        /** 버튼을 눌렀다 (진동·소리). repeat면 누르고 있어서 되풀이하는 중이다. */
        void onEditPress(boolean repeat);

        /** 버튼의 동작 (아래 상수). */
        void onEditAction(int action);
    }

    public static final int MOVE_LEFT = 1, MOVE_RIGHT = 2, MOVE_UP = 3, MOVE_DOWN = 4,
            MOVE_UP_LEFT = 5, MOVE_UP_RIGHT = 6, MOVE_DOWN_LEFT = 7, MOVE_DOWN_RIGHT = 8,
            LINE_START = 9, LINE_END = 10, TEXT_START = 11, TEXT_END = 12, WORD_LEFT = 13, WORD_RIGHT = 14,
            SELECT = 15, CUT = 16, COPY = 17, PASTE = 18, SELECT_ALL = 19,
            BACKSPACE = 20, FORWARD_DELETE = 21, UNDO = 22, REDO = 23;

    /** 격자의 칸 수: 가로 20칸(방향 버튼 다섯 줄은 4칸씩, 편집 버튼 줄은 5칸씩), 세로 6줄. */
    private static final int COLS = 20, ROWS = 6;

    private final KeyboardTheme theme;
    private final Listener listener;
    private final LinearLayout header;
    private final Pad pad;
    private final PadKey select, cut, copy;
    private float radius;
    private boolean selecting, hasSelection;

    public TextEditPanel(Context context, KeyboardTheme theme, int topBarHeight, Listener listener) {
        super(context);
        this.theme = theme;
        this.listener = listener;
        this.radius = Ui.dp(context, 7);
        setOrientation(VERTICAL);
        setBackgroundColor(theme.background);

        // 맨 위 줄: 뒤로 가기·제목·실행 취소·다시 실행 (클립보드 창 맨 위 줄과 같은 모양).
        header = new LinearLayout(context);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(Ui.dp(context, 4), 0, Ui.dp(context, 8), 0);
        IconButton back = headerButton(context, Icons.BACK, L.t("키보드로 돌아가기", "Back to keyboard"), topBarHeight,
                v -> listener.onEditBack());
        header.addView(back, new LayoutParams(Ui.dp(context, 44), topBarHeight));
        TextView title = new TextView(context);
        title.setText(L.t("텍스트 편집", "Text editing"));
        title.setTextColor(theme.text);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        title.setSingleLine(true);
        LayoutParams titleLp = new LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        titleLp.leftMargin = Ui.dp(context, 10);
        header.addView(title, titleLp);
        header.addView(headerButton(context, Icons.UNDO, L.t("실행 취소", "Undo"), topBarHeight,
                v -> listener.onEditAction(UNDO)), new LayoutParams(Ui.dp(context, 44), topBarHeight));
        header.addView(headerButton(context, Icons.REDO, L.t("다시 실행", "Redo"), topBarHeight,
                v -> listener.onEditAction(REDO)), new LayoutParams(Ui.dp(context, 44), topBarHeight));
        addView(header, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, topBarHeight));

        pad = new Pad(context);
        // 바깥 고리: 단어 이동(위 모서리), 끝으로 이동(네 변), 지우기(아래 모서리)
        add(Icons.WORD_LEFT, null, L.t("단어 왼쪽으로", "Word left"), WORD_LEFT, true, true, 0, 0, 4, 1);
        add(Icons.EDGE_UP, null, L.t("글 맨 앞으로", "To start of text"), TEXT_START, false, false, 4, 0, 12, 1);
        add(Icons.WORD_RIGHT, null, L.t("단어 오른쪽으로", "Word right"), WORD_RIGHT, true, true, 16, 0, 4, 1);
        add(Icons.EDGE_LEFT, null, L.t("줄 맨 앞으로", "To line start"), LINE_START, false, false, 0, 1, 4, 3);
        add(Icons.EDGE_RIGHT, null, L.t("줄 맨 뒤로", "To line end"), LINE_END, false, false, 16, 1, 4, 3);
        add(Icons.FORWARD_DELETE, null, L.t("뒤 글자 지우기", "Delete forward"), FORWARD_DELETE, true, true, 0, 4, 4, 1);
        add(Icons.EDGE_DOWN, null, L.t("글 맨 뒤로", "To end of text"), TEXT_END, false, false, 4, 4, 12, 1);
        add(Icons.DELETE, null, L.t("지우기", "Delete"), BACKSPACE, true, true, 16, 4, 4, 1);
        // 가운데: 선택 버튼과 그 둘레의 여덟 방향
        add(Icons.CHEVRON_UP_LEFT, null, L.t("왼쪽 위로", "Up and left"), MOVE_UP_LEFT, false, true, 4, 1, 4, 1);
        add(Icons.CHEVRON_UP, null, L.t("위로", "Up"), MOVE_UP, false, true, 8, 1, 4, 1);
        add(Icons.CHEVRON_UP_RIGHT, null, L.t("오른쪽 위로", "Up and right"), MOVE_UP_RIGHT, false, true, 12, 1, 4, 1);
        add(Icons.CHEVRON_LEFT, null, L.t("왼쪽으로", "Left"), MOVE_LEFT, false, true, 4, 2, 4, 1);
        select = add(0, L.t("선택", "Select"), L.t("선택 모드", "Selection mode"), SELECT, false, false, 8, 2, 4, 1);
        add(Icons.CHEVRON_RIGHT, null, L.t("오른쪽으로", "Right"), MOVE_RIGHT, false, true, 12, 2, 4, 1);
        add(Icons.CHEVRON_DOWN_LEFT, null, L.t("왼쪽 아래로", "Down and left"), MOVE_DOWN_LEFT, false, true, 4, 3, 4, 1);
        add(Icons.CHEVRON_DOWN, null, L.t("아래로", "Down"), MOVE_DOWN, false, true, 8, 3, 4, 1);
        add(Icons.CHEVRON_DOWN_RIGHT, null, L.t("오른쪽 아래로", "Down and right"), MOVE_DOWN_RIGHT, false, true, 12, 3, 4, 1);
        // 맨 아래 줄: 선택 편집 칩과 같은 네 가지
        cut = add(Icons.CUT, L.t("잘라내기", "Cut"), null, CUT, true, false, 0, 5, 5, 1);
        copy = add(Icons.COPY, L.t("복사", "Copy"), null, COPY, true, false, 5, 5, 5, 1);
        add(Icons.PASTE, L.t("붙여넣기", "Paste"), null, PASTE, true, false, 10, 5, 5, 1);
        add(Icons.SELECT_ALL, L.t("전체 선택", "Select all"), null, SELECT_ALL, true, false, 15, 5, 5, 1);
        addView(pad, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        restyle();
    }

    private IconButton headerButton(Context context, int icon, String desc, int barHeight, View.OnClickListener l) {
        IconButton b = new IconButton(context, icon, theme.text, theme.keyPressed, desc);
        b.setToolbarStyle(theme, barHeight);   // 도구 막대 버튼과 같은 칩
        b.setOnClickListener(v -> {
            listener.onEditPress(false);
            l.onClick(v);
        });
        return b;
    }

    /** 격자에 버튼 하나를 놓는다. function이면 기능키 색, repeat면 누르고 있는 동안 되풀이한다. */
    private PadKey add(int icon, String label, String desc, int action, boolean function, boolean repeat,
                       int x, int y, int w, int h) {
        PadKey k = new PadKey(getContext(), icon, label, action, function, repeat);
        k.setContentDescription(desc != null ? desc : label);
        pad.addView(k, new Cell(x, y, w, h));
        return k;
    }

    /**
     * 키보드의 여백 설정을 따른다 (클립보드 창과 같은 인자). 맨 위 줄은 도구 막대와 같은 좌우 여백,
     * 버튼 격자는 자판 키의 가장자리에 맞춘다.
     */
    public void setInsets(int barLeft, int barRight, int keyLeft, int top, int keyRight, int bottom) {
        header.setPadding(barLeft, 0, barRight, 0);
        pad.setPadding(keyLeft - pad.gapX / 2, top, keyRight - pad.gapX / 2, bottom);
    }

    /** 자판 키와 같은 모서리 곡률과 키 사이 간격 (px). */
    public void setKeyStyle(float radius, int gapX, int gapY) {
        boolean changed = this.radius != radius;
        this.radius = radius;
        if (pad.gapX != gapX || pad.gapY != gapY) {
            int keyLeft = pad.getPaddingLeft() + pad.gapX / 2, keyRight = pad.getPaddingRight() + pad.gapX / 2;
            pad.gapX = gapX;
            pad.gapY = gapY;
            pad.setPadding(keyLeft - gapX / 2, pad.getPaddingTop(), keyRight - gapX / 2, pad.getPaddingBottom());
            pad.requestLayout();
        }
        if (changed) restyle();
    }

    /** 선택 모드인지 (가운데 버튼이 강조색으로 바뀐다). */
    public void setSelecting(boolean on) {
        if (selecting == on) return;
        selecting = on;
        styleKey(select);
        select.setContentDescription(on ? L.t("선택 모드 끄기", "Turn off selection mode")
                : L.t("선택 모드 켜기", "Turn on selection mode"));
    }

    /** 선택한 글자가 있는지. 없으면 잘라내기·복사를 흐리게 보인다. */
    public void setHasSelection(boolean has) {
        if (hasSelection == has) return;
        hasSelection = has;
        cut.setDim(!has);
        copy.setDim(!has);
    }

    /** 누르고 있던 버튼의 되풀이를 멈춘다 (창을 닫을 때). */
    public void cancelRepeat() {
        for (int i = 0; i < pad.getChildCount(); i++) ((PadKey) pad.getChildAt(i)).stop();
    }

    private void restyle() {
        for (int i = 0; i < pad.getChildCount(); i++) styleKey((PadKey) pad.getChildAt(i));
        cut.setDim(!hasSelection);
        copy.setDim(!hasSelection);
    }

    private void styleKey(PadKey k) {
        boolean on = k == select && selecting;
        int fill = on ? theme.accent : k.function ? theme.functionKey : theme.key;
        k.setBackground(Ui.ripple(on ? Ui.tint(theme.onAccent) : theme.keyPressed, Ui.round(fill, radius), radius));
        Neu.apply(k, theme, fill, radius);
        k.setColor(on ? theme.onAccent : theme.text);
    }

    // ---------------------------------------------------------------- 격자

    /** 격자에서 버튼이 차지하는 칸: 왼쪽 위 (x, y)부터 가로 w칸, 세로 h줄. */
    private static final class Cell extends ViewGroup.LayoutParams {
        final int x, y, w, h;

        Cell(int x, int y, int w, int h) {
            super(0, 0);
            this.x = x;
            this.y = y;
            this.w = w;
            this.h = h;
        }
    }

    /** 버튼을 COLS×ROWS 격자에 놓는 틀. 버튼 사이는 자판 키처럼 간격의 절반씩 비운다. */
    private static final class Pad extends ViewGroup {
        int gapX, gapY;

        Pad(Context c) {
            super(c);
            gapX = Ui.dp(c, 5);
            gapY = Ui.dp(c, 9);
        }

        @Override
        protected void onMeasure(int widthSpec, int heightSpec) {
            int w = MeasureSpec.getSize(widthSpec), h = MeasureSpec.getSize(heightSpec);
            setMeasuredDimension(w, h);
            float colW = (w - getPaddingLeft() - getPaddingRight()) / (float) COLS;
            float rowH = (h - getPaddingTop() - getPaddingBottom()) / (float) ROWS;
            for (int i = 0; i < getChildCount(); i++) {
                View v = getChildAt(i);
                Cell c = (Cell) v.getLayoutParams();
                int cw = Math.max(0, Math.round(c.w * colW) - gapX), ch = Math.max(0, Math.round(c.h * rowH) - gapY);
                v.measure(MeasureSpec.makeMeasureSpec(cw, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(ch, MeasureSpec.EXACTLY));
            }
        }

        @Override
        protected void onLayout(boolean changed, int l, int t, int r, int b) {
            float colW = (getWidth() - getPaddingLeft() - getPaddingRight()) / (float) COLS;
            float rowH = (getHeight() - getPaddingTop() - getPaddingBottom()) / (float) ROWS;
            for (int i = 0; i < getChildCount(); i++) {
                View v = getChildAt(i);
                Cell c = (Cell) v.getLayoutParams();
                int left = getPaddingLeft() + Math.round(c.x * colW) + gapX / 2;
                int top = getPaddingTop() + Math.round(c.y * rowH) + gapY / 2;
                v.layout(left, top, left + v.getMeasuredWidth(), top + v.getMeasuredHeight());
            }
        }
    }

    // ---------------------------------------------------------------- 버튼

    /**
     * 처음 누른 뒤 되풀이를 시작하기까지와, 되풀이 간격 (ms). 대각선은 위아래로 보낸 방향 키를 앱이 처리한 뒤에야
     * 다음 좌우 칸을 정할 수 있어 (키보드 서비스의 ARROW_SETTLE_MS) 더 느리게 되풀이한다.
     */
    private static final long REPEAT_START_MS = 400, REPEAT_MS = 50, DIAGONAL_REPEAT_MS = 120;

    /** 격자의 버튼 하나: 아이콘이나 이름(둘 다 있으면 나란히, 좁으면 아이콘만)을 가운데에 그린다. */
    @SuppressLint("ClickableViewAccessibility")
    private final class PadKey extends View {
        private final Paint iconPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final TextPaint textPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        private final int icon, action, iconSize, gap, padH;
        private final long interval;
        private final String label;
        final boolean function;
        private final boolean repeat;
        private boolean dim, held;
        private int color;

        private final Runnable again = new Runnable() {
            @Override
            public void run() {
                if (!held) return;
                listener.onEditPress(true);
                listener.onEditAction(action);
                postDelayed(this, interval);
            }
        };

        PadKey(Context c, int icon, String label, int action, boolean function, boolean repeat) {
            super(c);
            this.icon = icon;
            this.label = label;
            this.action = action;
            this.function = function;
            this.repeat = repeat;
            interval = action >= MOVE_UP_LEFT && action <= MOVE_DOWN_RIGHT ? DIAGONAL_REPEAT_MS : REPEAT_MS;
            iconSize = Ui.dp(c, 20);
            gap = Ui.dp(c, 6);
            padH = Ui.dp(c, 6);
            textPaint.setTextSize(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 15, c.getResources().getDisplayMetrics()));
            setClickable(true);
            setFocusable(true);
            // 되풀이하지 않는 버튼과, 접근성 서비스가 누르는 경우: 한 번 실행한다.
            setOnClickListener(v -> fire());
        }

        void setColor(int color) {
            this.color = color;
            applyColor();
        }

        void setDim(boolean dim) {
            this.dim = dim;
            applyColor();
        }

        private void applyColor() {
            int c = dim ? (color & 0x00FFFFFF) | 0x61000000 : color;   // 흐리게: 불투명도 38%
            iconPaint.setColor(c);
            textPaint.setColor(c);
            invalidate();
        }

        private void fire() {
            listener.onEditPress(false);
            listener.onEditAction(action);
        }

        void stop() {
            held = false;
            removeCallbacks(again);
            setPressed(false);
        }

        @Override
        public boolean onTouchEvent(MotionEvent e) {
            if (!repeat) return super.onTouchEvent(e);
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    if (getBackground() != null) getBackground().setHotspot(e.getX(), e.getY());
                    setPressed(true);
                    held = true;
                    fire();
                    postDelayed(again, REPEAT_START_MS);
                    return true;
                case MotionEvent.ACTION_MOVE:
                    // 손가락이 버튼 밖으로 나가면 되풀이를 멈춘다.
                    if (held && (e.getX() < 0 || e.getY() < 0 || e.getX() > getWidth() || e.getY() > getHeight())) stop();
                    return true;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    stop();
                    return true;
                default:
                    return true;
            }
        }

        @Override
        protected void onDetachedFromWindow() {
            stop();
            super.onDetachedFromWindow();
        }

        @Override
        protected void onWindowVisibilityChanged(int visibility) {
            super.onWindowVisibilityChanged(visibility);
            if (visibility != VISIBLE) stop();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            float cx = getWidth() / 2f, cy = getHeight() / 2f;
            float size = Math.min(iconSize, Math.min(getWidth(), getHeight()) * 0.6f);
            float textW = label == null ? 0 : textPaint.measureText(label);
            boolean text = label != null && (icon == 0 || 2 * padH + size + gap + textW <= getWidth());
            boolean drawIcon = icon != 0;
            float left = cx - ((drawIcon ? size : 0) + (drawIcon && text ? gap : 0) + (text ? textW : 0)) / 2f;
            if (drawIcon) {
                Icons.draw(canvas, icon, left + size / 2f, cy, size, iconPaint, 1.7f);
                left += size + gap;
            }
            if (text) {
                Paint.FontMetrics fm = textPaint.getFontMetrics();
                canvas.drawText(label, drawIcon ? left : cx - textW / 2f, cy - (fm.ascent + fm.descent) / 2f, textPaint);
            }
        }
    }
}
