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
 *   [잘라내기 ][  복사  ][ 붙여넣기 ][ 전체 선택 ]
 *   [ 단어« ][            ↑            ][ 단어» ]
 *   [               ][ 선택 ][               ]
 *   [       ←       ][      ][       →       ]
 *   [  ⌦  ][            ↓            ][  ⌫  ]
 *   [  Tab  ][      띄어쓰기       ][  엔터  ]
 * </pre>
 * 가운데 '선택'을 누르면 선택 모드가 켜지고 꺼진다. 선택 모드에서는 이동 버튼이 커서 대신 선택 영역의 끝을 옮긴다.
 * 상하좌우 버튼을 그 방향으로 밀면 그쪽 끝으로 간다: 왼쪽·오른쪽은 줄의 맨 앞·맨 뒤, 위·아래는 글 전체의 맨 앞·맨 뒤.
 * '선택'을 밀면 트랙패드처럼 커서가 따라 움직이고(선택 모드면 선택 영역의 끝이), 길게 누른 뒤 밀면 선택 모드를 켜고
 * 밀어서 고른다 (스페이스바를 길게 누른 뒤 밀어서 선택하는 것과 같다). '붙여넣기'를 길게 누르면 클립보드 창을 연다.
 * 이동·지우기 버튼은 누르고 있으면 되풀이한다. 글자를 선택하면 맨 위 줄 제목 자리에 고른 글자 수가 보인다. 실제 동작은 키보드 서비스가 한다 ({@link Listener#onEditAction}).
 */
@SuppressLint("ViewConstructor")
public final class TextEditPanel extends LinearLayout {

    public interface Listener {
        void onEditBack();

        /** 버튼을 눌렀다 (진동·소리). repeat면 누르고 있어서 되풀이하는 중이다. */
        void onEditPress(boolean repeat);

        /** 버튼의 동작 (아래 상수). */
        void onEditAction(int action);

        /** '선택' 버튼을 밀어 커서(선택 모드면 선택 영역의 끝)를 가로 dx, 세로 dy칸 옮긴다. */
        void onEditDrag(int dx, int dy);
    }

    public static final int MOVE_LEFT = 1, MOVE_RIGHT = 2, MOVE_UP = 3, MOVE_DOWN = 4,
            LINE_START = 9, LINE_END = 10, TEXT_START = 11, TEXT_END = 12, WORD_LEFT = 13, WORD_RIGHT = 14,
            SELECT = 15, CUT = 16, COPY = 17, PASTE = 18, SELECT_ALL = 19,
            BACKSPACE = 20, FORWARD_DELETE = 21, UNDO = 22, REDO = 23,
            /** '선택'을 길게 눌렀다: 선택 모드를 켠다 (이미 켜져 있으면 그대로). */
            SELECT_ON = 24,
            /** '붙여넣기'를 길게 눌렀다: 클립보드 창을 연다. */
            OPEN_CLIPBOARD = 25,
            ENTER = 26, SPACE = 27, TAB = 28;

    /** 격자의 칸 수: 가로 20칸(모서리 버튼은 4칸, 편집 버튼 줄은 5칸씩), 세로 6줄 (편집, 커서 이동 4줄, Tab·띄어쓰기·엔터). */
    private static final int COLS = 20, ROWS = 6;

    private final KeyboardTheme theme;
    private final Listener listener;
    private final LinearLayout header;
    private final Pad pad;
    private final PadKey select, cut, copy, enter;
    private final TextView title;
    private float radius;
    private boolean selecting;
    private int selectedCount, selectedSpaces = -1;
    /** '선택'을 밀 때 한 칸 옮기는 거리 (px, 스페이스바 커서 이동과 같다)와 길게 누르기 시간 (ms). */
    private float stepX, stepY;
    private int longPressMs = 350;
    /**
     * 되풀이하는 버튼(이동·단어 이동·지우기)을 누른 뒤 되풀이를 시작하기까지의 시간 (ms, 기능키 길게 누르기 시간과 같다)과,
     * 지우기 버튼(⌫·⌦)이 지우는 간격 (ms, 자판 지우기 키의 '지우는 속도'와 같다).
     */
    private long repeatStartMs = REPEAT_START_MS, deleteRepeatMs = REPEAT_MS;
    /** 상하좌우 버튼을 이만큼(px) 밀면 그쪽 끝으로 간다 (기능키 밀기 거리와 같다). */
    private float swipePx;

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
        title = new TextView(context);
        title.setText(titleText());
        title.setTextColor(theme.text);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        title.setSingleLine(true);
        title.setGravity(Gravity.CENTER_VERTICAL);
        // 선택한 글자 수가 길어지면(괄호 포함) 한 줄에 들어가도록 글자를 줄인다.
        title.setAutoSizeTextTypeUniformWithConfiguration(10, 16, 1, TypedValue.COMPLEX_UNIT_SP);
        LayoutParams titleLp = new LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f);
        titleLp.leftMargin = Ui.dp(context, 10);
        header.addView(title, titleLp);
        header.addView(headerButton(context, Icons.UNDO, L.t("실행 취소", "Undo"), topBarHeight,
                v -> listener.onEditAction(UNDO)), new LayoutParams(Ui.dp(context, 44), topBarHeight));
        header.addView(headerButton(context, Icons.REDO, L.t("다시 실행", "Redo"), topBarHeight,
                v -> listener.onEditAction(REDO)), new LayoutParams(Ui.dp(context, 44), topBarHeight));
        addView(header, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, topBarHeight));

        pad = new Pad(context);
        // 맨 위 줄: 선택 편집 칩과 같은 네 가지
        cut = add(Icons.CUT, L.t("잘라내기", "Cut"), null, CUT, true, false, 0, 0, 5, 1);
        copy = add(Icons.COPY, L.t("복사", "Copy"), null, COPY, true, false, 5, 0, 5, 1);
        PadKey paste = add(Icons.PASTE, L.t("붙여넣기", "Paste"), null, PASTE, true, false, 10, 0, 5, 1);
        paste.setLongAction(OPEN_CLIPBOARD);
        PadKey selectAll = add(Icons.SELECT_ALL, L.t("전체 선택", "Select all"), null, SELECT_ALL, true, false, 15, 0, 5, 1);
        // 네 버튼은 이름을 함께 보이거나 함께 감춘다: 하나라도 이름이 들어가지 않으면 모두 아이콘만 (줄이 들쭉날쭉하지 않게).
        PadKey[] editRow = {cut, copy, paste, selectAll};
        for (PadKey k : editRow) k.labelGroup = editRow;
        // 커서 이동: 상하좌우가 남는 자리를 모두 쓰고 선택은 가운데. 위쪽 모서리는 단어 단위 이동, 아래쪽 모서리는 지우기.
        // 상하좌우는 그 방향으로 밀면 그쪽 끝으로 간다.
        add(Icons.WORD_LEFT, null, L.t("단어 앞으로", "Word left"), WORD_LEFT, true, true, 0, 1, 4, 1);
        add(Icons.CHEVRON_UP, null, L.t("위로 (위로 밀면 글 맨 앞으로)", "Up (swipe up: start of text)"), MOVE_UP, false, true, 4, 1, 12, 1)
                .setSwipe(0, -1, TEXT_START);
        add(Icons.WORD_RIGHT, null, L.t("단어 뒤로", "Word right"), WORD_RIGHT, true, true, 16, 1, 4, 1);
        add(Icons.CHEVRON_LEFT, null, L.t("왼쪽으로 (왼쪽으로 밀면 줄 맨 앞으로)", "Left (swipe left: line start)"), MOVE_LEFT, false, true, 0, 2, 8, 2)
                .setSwipe(-1, 0, LINE_START);
        select = add(0, L.t("선택", "Select"), L.t("선택 모드", "Selection mode"), SELECT, false, false, 8, 2, 4, 2);
        select.drag = true;
        add(Icons.CHEVRON_RIGHT, null, L.t("오른쪽으로 (오른쪽으로 밀면 줄 맨 뒤로)", "Right (swipe right: line end)"), MOVE_RIGHT, false, true, 12, 2, 8, 2)
                .setSwipe(1, 0, LINE_END);
        add(Icons.FORWARD_DELETE, null, L.t("커서 뒤 글자 지우기", "Delete forward"), FORWARD_DELETE, true, true, 0, 4, 4, 1);
        add(Icons.CHEVRON_DOWN, null, L.t("아래로 (아래로 밀면 글 맨 뒤로)", "Down (swipe down: end of text)"), MOVE_DOWN, false, true, 4, 4, 12, 1)
                .setSwipe(0, 1, TEXT_END);
        add(Icons.DELETE, null, L.t("커서 앞 글자 지우기", "Delete"), BACKSPACE, true, true, 16, 4, 4, 1);
        // 맨 아래 줄: 자판 맨 아래 줄처럼 가운데에 넓은 띄어쓰기
        add(Icons.TAB, "Tab", L.t("탭", "Tab"), TAB, true, false, 0, 5, 4, 1);
        add(0, L.t("띄어쓰기", "Space"), null, SPACE, true, false, 4, 5, 12, 1);   // 양옆 Tab·엔터와 같은 기능키 색
        enter = add(Icons.ENTER, null, L.t("엔터", "Enter"), ENTER, true, false, 16, 5, 4, 1);
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

    /**
     * 선택한 글자 수와 그 가운데 띄어쓰기·줄바꿈(공백 글자) 수. 글자 수가 0이 아니면 맨 위 줄 제목 자리에 보이고,
     * 0이면 잘라내기·복사를 흐리게 보인다. 공백 수를 모르면(아주 길게 골라 글을 읽지 않았을 때) -1이고 괄호는 뺀다.
     */
    public void setSelectedCount(int count, int spaces) {
        if (selectedCount == count && selectedSpaces == spaces) return;
        selectedCount = count;
        selectedSpaces = spaces;
        cut.setDim(count == 0);
        copy.setDim(count == 0);
        title.setText(titleText());
    }

    private CharSequence titleText() {
        if (selectedCount <= 0) return L.t("텍스트 편집", "Text editing");
        String main = L.t(selectedCount + "자 선택됨", selectedCount + " selected");
        if (selectedSpaces < 0) return main;
        // 괄호 부분은 작고 옅게: 제목 줄이 좁으면 글자 크기가 함께 줄어든다 (자동 크기).
        String extra = L.t(" (띄어쓰기·줄바꿈 " + selectedSpaces + "자 포함)",
                " (incl. " + selectedSpaces + (selectedSpaces == 1 ? " space or line break)" : " spaces and line breaks)"));
        android.text.SpannableString s = new android.text.SpannableString(main + extra);
        s.setSpan(new android.text.style.RelativeSizeSpan(0.8f), main.length(), s.length(), 0);
        s.setSpan(new android.text.style.ForegroundColorSpan(theme.hint), main.length(), s.length(), 0);
        return s;
    }

    /** 엔터 버튼의 아이콘 (입력란에 따라 줄바꿈·보내기·검색 등, 자판의 엔터 키와 같다). */
    public void setEnterIcon(int icon) {
        enter.setIcon(icon);
    }

    /** 되풀이하는 버튼을 누른 뒤 되풀이를 시작하기까지의 시간과, 지우기 버튼을 누르고 있을 때 지우는 간격 (ms). */
    public void setRepeatTiming(long startMs, long deleteMs) {
        repeatStartMs = Math.max(1, startMs);
        deleteRepeatMs = Math.max(1, deleteMs);
    }

    /** 상하좌우 버튼을 이만큼(px) 밀면 그쪽 끝으로 간다. */
    public void setSwipeThreshold(float px) {
        swipePx = px;
    }

    /** '선택'을 밀 때 한 칸 옮기는 거리 (px)와 길게 누르기 시간 (ms). */
    public void setDragConfig(float stepX, float stepY, int longPressMs) {
        this.stepX = Math.max(1, stepX);
        this.stepY = Math.max(1, stepY);
        this.longPressMs = longPressMs;
    }

    /** 누르고 있던 버튼의 되풀이를 멈춘다 (창을 닫을 때). */
    public void cancelRepeat() {
        for (int i = 0; i < pad.getChildCount(); i++) ((PadKey) pad.getChildAt(i)).stop();
    }

    private void restyle() {
        for (int i = 0; i < pad.getChildCount(); i++) styleKey((PadKey) pad.getChildAt(i));
        cut.setDim(selectedCount == 0);
        copy.setDim(selectedCount == 0);
    }

    private void styleKey(PadKey k) {
        boolean on = k == select && selecting;
        int fill = on ? theme.accent : k.function ? theme.functionKey : theme.key;
        k.setBackground(Ui.keyPress(on ? Ui.tint(theme.onAccent) : theme.keyPressed, Ui.round(fill, radius), radius));
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

    /** 처음 누른 뒤 되풀이를 시작하기까지 (설정을 받기 전의 기본값)와, 이동 버튼의 되풀이 간격 (ms). */
    private static final long REPEAT_START_MS = 350, REPEAT_MS = 50;

    /** 격자의 버튼 하나: 아이콘이나 이름(둘 다 있으면 나란히, 좁으면 아이콘만)을 가운데에 그린다. */
    @SuppressLint("ClickableViewAccessibility")
    private final class PadKey extends View {
        private final Paint iconPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final TextPaint textPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        private final int action, iconSize, gap, padH, slop;
        private int icon;
        /** 이름을 함께 보이거나 감추는 버튼들 (null이면 혼자 정한다). */
        PadKey[] labelGroup;
        /** 길게 누르면 실행할 동작 (0이면 없음). */
        private int longAction;
        /** 트랙패드처럼 밀 수 있는 버튼('선택')인지와, 미는 중의 상태. */
        boolean drag;
        private boolean dragging, longFired;
        /** 밀면 끝으로 가는 버튼: 미는 방향(swipeX, swipeY)과 그때의 동작 (0이면 밀기 없음). */
        private int swipeX, swipeY, swipeAction;
        /** 밀기 버튼: 이번 누르기에서 이미 밀었는지, 되풀이를 시작했는지. */
        private boolean swiped, repeating;
        private float downX, downY, refX, refY;
        private final String label;
        final boolean function;
        private final boolean repeat;
        private boolean dim, held;
        private int color;

        /** '선택'을 길게 눌렀다: 선택 모드를 켜고, 이제부터 미는 만큼 선택 영역을 넓힌다. */
        private final Runnable longPress = () -> {
            if (!held || dragging) return;
            longFired = true;
            listener.onEditAction(SELECT_ON);
        };

        private final Runnable again = new Runnable() {
            @Override
            public void run() {
                if (!held) return;
                listener.onEditPress(true);
                listener.onEditAction(action);
                postDelayed(this, interval());
            }
        };

        PadKey(Context c, int icon, String label, int action, boolean function, boolean repeat) {
            super(c);
            this.icon = icon;
            this.label = label;
            this.action = action;
            this.function = function;
            this.repeat = repeat;
            iconSize = Ui.dp(c, 20);
            gap = Ui.dp(c, 6);
            padH = Ui.dp(c, 6);
            slop = android.view.ViewConfiguration.get(c).getScaledTouchSlop();
            textPaint.setTextSize(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 15, c.getResources().getDisplayMetrics()));
            setClickable(true);
            setFocusable(true);
            // 되풀이하지 않는 버튼과, 접근성 서비스가 누르는 경우: 한 번 실행한다.
            setOnClickListener(v -> fire());
        }

        /** 되풀이 간격 (ms): 지우기 버튼은 자판의 지우는 속도를 따르고, 나머지는 REPEAT_MS. */
        private long interval() {
            return action == BACKSPACE || action == FORWARD_DELETE ? deleteRepeatMs : REPEAT_MS;
        }

        /** 이 버튼의 폭에 아이콘과 이름이 다 들어가는지. */
        private boolean labelFits() {
            float size = Math.min(iconSize, Math.min(getWidth(), getHeight()) * 0.6f);
            return 2 * padH + size + gap + textPaint.measureText(label) <= getWidth();
        }

        /** 이름을 보일지: 아이콘이 없으면 늘, 묶음이면 묶음의 버튼이 모두 들어갈 때만. */
        private boolean showsLabel() {
            if (label == null) return false;
            if (icon == 0) return true;
            if (labelGroup == null) return labelFits();
            for (PadKey k : labelGroup) if (k.label != null && !k.labelFits()) return false;
            return true;
        }

        void setIcon(int icon) {
            if (this.icon == icon) return;
            this.icon = icon;
            invalidate();
        }

        /** 이 방향(dx, dy)으로 밀면 action을 실행한다. 그러면 누르기는 손을 뗄 때 실행한다 (밀기와 구분하려고). */
        PadKey setSwipe(int dx, int dy, int action) {
            swipeX = dx;
            swipeY = dy;
            swipeAction = action;
            return this;
        }

        void setLongAction(int action) {
            longAction = action;
            setHapticFeedbackEnabled(false);   // 진동·소리는 키보드 설정을 따른다 (onEditPress)
            setOnLongClickListener(v -> {
                listener.onEditPress(false);
                listener.onEditAction(longAction);
                return true;
            });
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
            dragging = false;
            removeCallbacks(again);
            removeCallbacks(longPress);
            removeCallbacks(startRepeat);
            repeating = false;
            setPressed(false);
        }

        @Override
        public boolean onTouchEvent(MotionEvent e) {
            if (drag) return onDragTouch(e);
            if (swipeAction != 0) return onSwipeTouch(e);
            if (!repeat) return super.onTouchEvent(e);
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    if (getBackground() != null) getBackground().setHotspot(e.getX(), e.getY());
                    setPressed(true);
                    held = true;
                    fire();
                    postDelayed(again, repeatStartMs);
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

        /** 되풀이를 시작한다 (밀기 버튼은 누를 때 바로 옮기지 않았으므로 이때 첫 칸을 옮긴다). */
        private final Runnable startRepeat = () -> {
            if (!held || swiped) return;
            repeating = true;
            listener.onEditAction(PadKey.this.action);
            postDelayed(again, interval());
        };

        /**
         * 밀면 끝으로 가는 상하좌우 버튼: 누를 때 진동만 하고, 정한 방향으로 밀기 거리만큼 밀면 그쪽 끝으로 간다.
         * 밀지 않고 떼면 한 칸 옮기고, 누르고 있으면 다른 이동 버튼처럼 되풀이한다 (되풀이를 시작한 뒤에는 밀기로 보지 않는다).
         */
        private boolean onSwipeTouch(MotionEvent e) {
            float x = e.getX(), y = e.getY();
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    if (getBackground() != null) getBackground().setHotspot(x, y);
                    setPressed(true);
                    held = true;
                    swiped = false;
                    repeating = false;
                    downX = x;
                    downY = y;
                    listener.onEditPress(false);
                    postDelayed(startRepeat, repeatStartMs);
                    return true;
                case MotionEvent.ACTION_MOVE: {
                    if (!held || swiped || repeating) return true;
                    float along = swipeX != 0 ? (x - downX) * swipeX : (y - downY) * swipeY;
                    float across = swipeX != 0 ? Math.abs(y - downY) : Math.abs(x - downX);
                    if (along >= Math.max(swipePx, slop) && along > across) {
                        swiped = true;
                        removeCallbacks(startRepeat);
                        listener.onEditPress(false);
                        listener.onEditAction(swipeAction);
                    }
                    return true;
                }
                case MotionEvent.ACTION_UP: {
                    boolean tap = held && !swiped && !repeating;
                    stop();
                    if (tap) listener.onEditAction(action);   // 누를 때 이미 진동했다
                    return true;
                }
                case MotionEvent.ACTION_CANCEL:
                    stop();
                    return true;
                default:
                    return true;
            }
        }

        /**
         * '선택' 버튼: 탭하면 선택 모드를 켜고 끈다. 밀면 트랙패드처럼 커서를 옮기고, 길게 누르면 선택 모드를 켠 뒤 미는 만큼
         * 선택한다. 칸 거리는 스페이스바 커서 이동과 같다.
         */
        private boolean onDragTouch(MotionEvent e) {
            float x = e.getX(), y = e.getY();
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    if (getBackground() != null) getBackground().setHotspot(x, y);
                    setPressed(true);
                    held = true;
                    dragging = false;
                    longFired = false;
                    downX = refX = x;
                    downY = refY = y;
                    listener.onEditPress(false);
                    postDelayed(longPress, longPressMs);
                    return true;
                case MotionEvent.ACTION_MOVE:
                    if (!held) return true;
                    if (!dragging && (Math.abs(x - downX) > slop || Math.abs(y - downY) > slop)) {
                        dragging = true;
                        removeCallbacks(longPress);
                    }
                    if (!dragging && !longFired) return true;
                    // 칸 거리만큼 밀 때마다 한 칸씩 옮긴다 (남은 거리는 다음으로 넘긴다).
                    int dx = 0, dy = 0;
                    while (Math.abs(x - refX) >= stepX && stepX > 0) {
                        int d = x > refX ? 1 : -1;
                        refX += d * stepX;
                        dx += d;
                    }
                    while (Math.abs(y - refY) >= stepY && stepY > 0) {
                        int d = y > refY ? 1 : -1;
                        refY += d * stepY;
                        dy += d;
                    }
                    // 한 번에 여러 칸이면 한 칸씩 보낸다 (대각선은 좌우·위아래를 한 칸씩 짝지어).
                    while (dx != 0 || dy != 0) {
                        int sx = Integer.signum(dx), sy = Integer.signum(dy);
                        listener.onEditDrag(sx, sy);
                        dx -= sx;
                        dy -= sy;
                    }
                    return true;
                case MotionEvent.ACTION_UP:
                    boolean tap = held && !dragging && !longFired;
                    stop();
                    if (tap) listener.onEditAction(action);   // 누를 때 이미 진동했다
                    return true;
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
            boolean text = showsLabel();
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
