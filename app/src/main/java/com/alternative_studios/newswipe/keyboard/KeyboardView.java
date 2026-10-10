package com.alternative_studios.newswipe.keyboard;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.os.Handler;
import android.os.Looper;
import android.util.TypedValue;
import android.view.MotionEvent;
import android.view.View;

import java.util.ArrayList;
import java.util.Locale;

/**
 * 자판을 직접 그리고 터치를 처리하는 뷰. 키 하나하나를 뷰로 만들지 않아서 메모리를 적게 쓴다.
 *
 * <p>제스처:
 * <ul>
 *   <li>탭 — 키의 글자</li>
 *   <li>아래로 스와이프 — 쌍자음·이중모음 (ㄱ→ㄲ, ㅏ→ㅑ …), 모음은 왼쪽으로도 ({@link VowelSwipes})</li>
 *   <li>위·왼쪽·오른쪽으로 스와이프 — 겹받침 (ㄹ→ㄺ …, {@link FinalSwipes})</li>
 *   <li>길게 누르기 — 특수문자 팝업, 손가락을 옆으로 옮겨 선택</li>
 *   <li>스페이스바 좌우 드래그 — 커서 이동</li>
 *   <li>백스페이스 왼쪽으로 밀기 — 단어 삭제, 길게 누르기 — 연속 삭제</li>
 * </ul>
 */
public final class KeyboardView extends View {

    public interface Listener {
        /** 키를 누른 순간 (진동·소리용). */
        void onKeyPress(Key key);

        /** 키를 탭했다 (손을 뗌). Shift는 누르는 순간 호출된다. */
        void onKeyTap(Key key);

        /** 키를 스와이프했다. text는 그 방향에 배치된 글자 (쌍자음, 겹받침 …). */
        void onKeySwipe(Key key, String text);

        void onPopupChar(Key key, String text);

        /**
         * 길게 눌러 연속 입력으로 정한 글자 키를 누르고 있는 동안 반복해서 입력한다.
         * 탭이 아니므로 '자음 연속 탭으로 쌍자음' 같은 연속 탭 판단에 들어가지 않는다.
         */
        void onKeyRepeat(Key key);

        /** 기능키를 길게 눌렀다 (지구본 → 입력기 선택, 기호 키 → 이모지 열기). */
        void onKeyLongPress(Key key);

        /** 왼쪽 아래 기호 키(?123, 가, ABC)를 위로 밀었다 (이모지 창 열기). */
        void onModeKeySwipeUp(Key key);

        /** 밀어서 기능 완전 사용자화: 키를 밀어서 정해 둔 기능(SwipeAction ID)을 실행하라는 요청. */
        void onKeyFunction(Key key, String actionId);

        void onDeleteRepeat();

        void onDeleteWord();

        /** 지우기 키를 위로 밀었다: 커서가 있는 줄에서 커서 왼쪽을 모두 지운다. */
        void onDeleteToLineStart();

        void onCursorMove(int direction);

        /** 스페이스바를 위(-1)/아래(+1)로 밀어 커서를 한 줄 옮긴다. */
        void onCursorMoveVertical(int direction);

        /** Shift를 누른 채 다른 키를 친 뒤 Shift에서 손을 뗐다. */
        void onShiftChordEnd();

        /**
         * 밀기나 길게 누르기가 인식됐다 (밀기·길게 누르기 진동용). 밀기는 손을 떼어 실행할 때, 길게 누르기는 인식한 순간 한 번 부른다.
         * 누르고 있는 동안 반복 입력·연속 지우기로 넘어가는 길게 누르기는 반복마다 따로 울리므로 부르지 않는다.
         */
        default void onGesture() {
        }
    }

    private static final int NORMAL = 0;
    private static final int SWIPED = 1;
    private static final int POPUP = 2;
    private static final int CURSOR = 3;
    private static final int DELETE_WORD = 4;
    /** 지우기 키를 위로 밀고 있는 중 (손을 떼면 커서 왼쪽 줄 내용을 모두 지운다). */
    private static final int DELETE_LINE_START = 9;
    private static final int REPEAT = 5;
    private static final int CONSUMED = 6;
    /** 왼쪽 아래 기호 키를 위로 밀고 있는 중 (손을 떼면 이모지 창을 연다). */
    private static final int EMOJI_SWIPE = 7;
    /** 밀어서 기능 완전 사용자화로 정한 기능을 실행하려고 키를 밀고 있는 중 (손을 떼면 실행한다). */
    private static final int FN_SWIPE = 8;
    /** 길게 눌러 연속 입력하는 글자 키를 누르고 있는 중 (손을 뗄 때까지 계속 입력한다). */
    private static final int CHAR_REPEAT = 10;

    private static final class Pointer {
        int id;
        Key key;
        float downX, downY;
        int mode;
        /** mode가 SWIPED·FN_SWIPE일 때의 방향 (Key.SWIPE_*). */
        int swipeDir;
        /** mode가 FN_SWIPE일 때 실행할 기능 (SwipeAction ID). */
        String fnAction;
        float cursorAnchorX, cursorAnchorY;
        /** 마지막으로 본 손가락 위치 (두 손가락 밀기를 알아채는 데 쓴다). */
        float lastX, lastY;
        /** 문자 키를 밀어 시작한 커서 이동: 스페이스바 설정 대신 이 축 설정(freeH·freeV)을 따른다. */
        boolean freeCursor, freeH, freeV;
        /** 분리 키보드 가운데 빈 공간에서 시작한 커서 이동: 스페이스바의 좌우·상하 설정을 따른다. */
        boolean gap;
        boolean chord;
    }

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ArrayList<Pointer> pointers = new ArrayList<>(4);
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint icon = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF tmp = new RectF();
    private final Paint.FontMetrics fm = new Paint.FontMetrics();

    private Listener listener;
    private KeyboardLayout layout;
    private KeyboardTheme theme;
    private int shiftState;          // 0 꺼짐, 1 한 번, 2 고정
    private int enterIcon = Icons.ENTER;
    private int fixedHeight;
    private float overflowTop;

    // 설정
    private int longPressMs = 350;
    /** 지우기 키를 길게 눌러 연속 삭제하는 기능의 사용 여부와 시간. */
    /** 지구본 키(키보드 선택)와 기호 키(이모지 열기)를 길게 누르는 시간. 설정으로 바꾸지 않는다. */
    private boolean longPressDelete = true;
    /** 왼쪽 아래 기호 키(?123, 가, ABC)를 길게 눌러 설정을 여는 기능의 사용 여부. */
    private boolean modeKeyLongPress = true;
    /** 같은 키를 위로 밀어 이모지 창을 여는 기능의 사용 여부. */
    private boolean modeKeyEmoji = true;
    /** 밀어서 기능 완전 사용자화 (꺼져 있으면 null). 켜져 있으면 다섯 기능 키의 기본 밀기 설정은 쓰지 않는다. */
    private FunctionSwipes fnSwipes;
    /** 지우기 키를 왼쪽으로 밀어 단어 단위로 지우는 기능의 사용 여부. */
    private boolean deleteWordSwipe = true;
    /** 기능키 길게 누르기 시간 (모든 기능키 공통). */
    private int fnPressMs = 350;
    /** 글자를 밀어 입력하는 거리 ('밀어서 글자 입력'의 미는 거리). */
    private float swipeThreshold;
    /** 글자 키 스와이프 방향 판정 비율 (SwipeDirection): 세로가 가로의 몇 배를 넘어야 아래·위로 치는지. */
    private float downRatio = SwipeDirection.DOWN_OVER_HORIZONTAL, upRatio = SwipeDirection.UP_OVER_HORIZONTAL;
    /** 기능키·키보드를 밀어 기능을 실행하는 거리 ('밀어서 기능'의 미는 거리). */
    private float fnSwipeThreshold;
    private boolean showPreview = true;
    private boolean showHints = true;
    /** 숫자 키(1~0)의 힌트만 숨긴다. */
    private boolean numberHintHidden;
    /** 키를 길게 눌러 문자를 입력하는 기능. 끄면 팝업도, 키 위의 작은 글자도 없다. */
    private boolean longPressChars = true;
    private boolean keyShadow;
    private int keyShadowStrength = 50;
    private Paint shadowPaint;
    private boolean spaceCursor = true;
    private boolean gapCursor = true;
    /** 문자·숫자 키를 트랙패드처럼 밀어 커서를 옮기는 기능. 그 방향에 밀어서 입력할 글자가 있으면 글자가 먼저다. */
    private boolean charCursor;
    /** 커서 이동이 시작된 뒤 손가락이 움직이는 거리당 커서가 움직이는 빠르기 (1 = 기본). 스페이스바·문자 키, 좌우·상하 따로. */
    private float spaceSpeedH = 1f, spaceSpeedV = 1f, charSpeedH = 1f, charSpeedV = 1f;
    /** 스페이스바를 좌우로 밀어 한/영 전환 (손을 떼면 바꾼다). 스페이스바 좌우 커서 이동과 함께 쓰지 않는다. */
    private boolean spaceLangSwipe;
    /** 키보드 밀기 (한 손가락·두 손가락). 쓰지 않으면 null. */
    private KeyboardSwipes kbSwipes;
    /**
     * 두 손가락 밀기를 기다리는 중: 앞 손가락(deferred)의 입력을 미루고 두 번째 손가락(second)과 함께 지켜본다.
     * 둘이 같은 방향으로 충분히 움직이면 두 손가락 밀기, 아니면 어느 한 손가락을 뗄 때 평소처럼 앞 키부터 입력한다.
     */
    private Pointer deferred, second;
    private float deferredStartX, deferredStartY;
    /** 문자 키 커서 이동의 방향별 사용 여부. */
    private boolean charCursorH = true, charCursorV = true;
    /** 스페이스바 커서 이동의 방향별 사용 여부. */
    private boolean cursorH = true, cursorV = true;
    private float textScale = 1f;

    // 크기
    private float gapX, gapY;   // 키 사이 간격 (좌우·상하)
    private float radius;   // 키 모서리 곡률 (설정의 '키 곡률')
    private final float cursorStep, cursorStepY;
    private float padL, padT, padR, padB;

    // 길게 누르기
    private Pointer longPressTarget;
    private final Runnable longPressRunnable = this::onLongPress;
    private final Runnable repeatRunnable = new Runnable() {
        @Override
        public void run() {
            if (listener != null) listener.onDeleteRepeat();
            handler.postDelayed(this, 50);
        }
    };

    /** 길게 누르면 팝업 대신 계속 입력하는 글자 ("<자판>_<글자>", 설정의 '길게 눌러 연속 입력'). */
    private java.util.Set<String> repeatChars = java.util.Collections.emptySet();
    private Pointer charRepeatPointer;
    private final Runnable charRepeatRunnable = new Runnable() {
        @Override
        public void run() {
            Pointer p = charRepeatPointer;
            if (p == null || !pointers.contains(p) || listener == null) return;
            listener.onKeyRepeat(p.key);
            handler.postDelayed(this, 60);
        }
    };

    public void setRepeatChars(java.util.Set<String> chars) {
        repeatChars = chars;
        invalidate();
    }

    private boolean isRepeatChar(Key k) {
        return k.type == Key.CHAR && !k.noRepeat && !repeatChars.isEmpty() && layout != null
                && repeatChars.contains(KeyboardLayout.groupOf(layout.kind == KeyboardLayout.KOREAN, k.slot()) + "_" + k.slot());
    }

    // 팝업 (길게 누르기 문자 선택)
    private Pointer popupPointer;
    private String[] popupItems;
    private int popupCols, popupSelected;
    /** 팝업이 열린 뒤 손가락이 실제로 움직였는지. 움직이기 전에는 첫 번째 문자를 유지한다. */
    private boolean popupMoved;
    private float popupLeft, popupTop, popupCellW, popupCellH;
    /** 말풍선 글자 배율: 칸보다 넓은 글자(.co.kr 같은 여러 글자)는 칸 안에 들어가도록 줄인다. */
    private float popupTextScale = 1f;

    public KeyboardView(Context context) {
        super(context);
        density = context.getResources().getDisplayMetrics().density;
        gapX = dp(5);
        gapY = dp(9);
        padL = padR = dp(3);
        padB = dp(4);
        radius = dp(7);
        cursorStep = dp(11);
        cursorStepY = dp(28);   // 한 줄 이동은 손가락 떨림에 흔들리지 않도록 가로보다 넓게 잡는다
        swipeThreshold = dp(22);
        fnSwipeThreshold = dp(22);
        text.setTextAlign(Paint.Align.CENTER);
        theme = KeyboardTheme.of(context);
        updateTextSizes();
    }

    // ---------------------------------------------------------------- 설정

    public void setListener(Listener l) {
        listener = l;
    }

    /** 키 사이 간격(px): 좌우, 상하. 그리기만 바뀌고 키가 눌리는 범위는 그대로다. */
    public void setKeyGaps(int x, int y) {
        if (gapX == x && gapY == y) return;
        gapX = x;
        gapY = y;
        invalidate();
    }

    /** 자판 둘레의 여백(px). */
    public void setInsets(int left, int top, int right, int bottom) {
        if (padL == left && padT == top && padR == right && padB == bottom) return;
        padL = left;
        padT = top;
        padR = right;
        padB = bottom;
        layoutKeys(getWidth(), getHeight());
        invalidate();
    }

    public void setLayout(KeyboardLayout l) {
        cancelAll();
        layout = l;
        requestLayout();
        layoutKeys(getWidth(), getHeight());
        invalidate();
    }

    public KeyboardLayout getLayout() {
        return layout;
    }

    public void setTheme(KeyboardTheme t) {
        theme = t;
        invalidate();
    }

    public void setShiftState(int s) {
        if (shiftState != s) {
            shiftState = s;
            invalidate();
        }
    }

    public void setEnterIcon(int i) {
        enterIcon = i;
        invalidate();
    }

    public void setKeyboardHeight(int px) {
        if (fixedHeight != px) {
            fixedHeight = px;
            requestLayout();
        }
    }

    /** 미리보기·팝업이 뷰 위쪽으로 넘어가 그려질 수 있는 높이. */
    public void setOverflowTop(float px) {
        overflowTop = px;
    }

    /** 키(와 미리보기·길게 누르기 팝업)의 모서리 곡률 (px). 키가 작으면 그리기에서 알아서 반원 이하로 줄어든다. */
    public void setKeyRadius(float px) {
        if (radius != px) {
            radius = px;
            invalidate();
        }
    }

    /** @param strength 0~100 */
    public void setKeyShadow(boolean on, int strength) {
        if (keyShadow != on || keyShadowStrength != strength) {
            keyShadow = on;
            keyShadowStrength = strength;
            invalidate();
        }
    }

    public void setSpaceLangSwipe(boolean on) {
        spaceLangSwipe = on;
    }

    /** 커서 이동 속도 (%). 100 = 기본. */
    public void setCursorSpeed(int spaceH, int spaceV, int charH, int charV) {
        spaceSpeedH = Math.max(10, spaceH) / 100f;
        spaceSpeedV = Math.max(10, spaceV) / 100f;
        charSpeedH = Math.max(10, charH) / 100f;
        charSpeedV = Math.max(10, charV) / 100f;
    }

    public void setKeyboardSwipes(KeyboardSwipes k) {
        kbSwipes = k;
    }

    /** 숫자 키(1~0)에는 길게 눌러 입력할 문자 힌트를 그리지 않을지. */
    public void setNumberHintHidden(boolean on) {
        if (numberHintHidden != on) {
            numberHintHidden = on;
            invalidate();
        }
    }

    /** 키 오른쪽 위의 작은 글자(길게 눌러 입력할 문자 힌트)를 그릴지. 길게 눌러 입력하는 기능과는 상관없다. */
    public void setPopupHints(boolean on) {
        if (showHints != on) {
            showHints = on;
            invalidate();
        }
    }

    public void setCharCursor(boolean on, boolean horizontal, boolean vertical) {
        charCursor = on;
        charCursorH = horizontal;
        charCursorV = vertical;
    }

    public void setDeleteWordSwipe(boolean on) {
        deleteWordSwipe = on;
    }

    /** 기능키 길게 누르기 완전 사용자화: 칸(LongPressActions.SHIFT~DELETE)마다 기능 ID. null이면 기본 동작. */
    private String[] longPressActions;

    public void setLongPressActions(String[] actions) {
        longPressActions = actions;
    }

    /** Fn 키의 기능: {탭, 길게 누르기, 아래·위·왼쪽·오른쪽 밀기} (FnKeyActions.actions). */
    private String[] fnKeyActions;

    public void setFnKeyActions(String[] actions) {
        fnKeyActions = actions;
    }

    /** Fn 키의 한 동작(FnKeyActions.I_*)에 정한 기능. 없음이면 null. */
    private String fnKeyAction(int index) {
        if (fnKeyActions == null) return null;
        String a = fnKeyActions[index];
        return SwipeAction.NONE.equals(a) ? null : a;
    }

    /**
     * 사용자화를 켰을 때 이 키를 길게 누르면 실행할 기능. 사용자화가 꺼져 있거나 대상이 아닌 키면 null.
     * Fn 키는 언제나 자기 설정(Fn 키 사용자화)을 따른다.
     */
    private String customLongPress(Key k) {
        if (k.type == Key.FUNCTION) return fnKeyAction(FnKeyActions.I_LONG);
        if (longPressActions == null) return null;
        int slot = LongPressActions.slotOf(k.type);
        if (slot < 0) return null;
        String a = longPressActions[slot];
        return SwipeAction.NONE.equals(a) ? null : a;
    }

    public void setModeKeyLongPress(boolean on) {
        modeKeyLongPress = on;
    }

    public void setModeKeyEmoji(boolean on) {
        modeKeyEmoji = on;
    }

    public void setFunctionSwipes(FunctionSwipes f) {
        fnSwipes = f;
    }

    public void setDeleteLongPress(boolean on, int ms) {
        longPressDelete = on;
        fnPressMs = ms;
    }

    /** 분리 키보드 가운데 빈 공간을 밀어 커서를 옮기는지. */
    public void setGapCursor(boolean on) {
        gapCursor = on;
    }

    public void setCursorAxes(boolean horizontal, boolean vertical) {
        cursorH = horizontal;
        cursorV = vertical;
    }

    /** '밀어서 기능'의 미는 거리. */
    public void setFunctionSwipeThreshold(int dpValue) {
        fnSwipeThreshold = dp(dpValue);
    }

    /** 글자 키 스와이프의 아래·위 판정 비율 (세로 움직임이 가로 움직임의 몇 배를 넘어야 하는지). */
    public void setDirectionRatios(float down, float up) {
        downRatio = down;
        upRatio = up;
    }

    public void configure(int longPressMs, int swipeThresholdDp, boolean preview, boolean hints,
                          boolean spaceCursor, int textScalePercent) {
        this.longPressMs = longPressMs;
        this.swipeThreshold = dp(swipeThresholdDp);
        this.showPreview = preview;
        this.showHints = hints;
        this.longPressChars = hints;
        this.spaceCursor = spaceCursor;
        this.textScale = textScalePercent / 100f;
        updateTextSizes();
        invalidate();
    }

    // ---------------------------------------------------------------- 배치

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), fixedHeight);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        layoutKeys(w, h);
    }

    private void layoutKeys(int w, int h) {
        if (layout == null || w == 0 || h == 0) return;
        float unit = (w - padL - padR) / layout.columns;
        float rowUnit = (h - padT - padB) / layout.totalHeight();
        float y = padT;
        for (KeyboardLayout.Row row : layout.rows) {
            float rh = row.height * rowUnit;
            float sum = 0;
            for (Key k : row.keys) sum += k.weight;
            float scale = sum > layout.columns ? layout.columns / sum : 1f;
            float x = padL + (layout.columns - sum * scale) * unit / 2f;
            for (Key k : row.keys) {
                float kw = k.weight * scale * unit;
                k.rect.set(x, y, x + kw, y + rh);
                x += kw;
            }
            y += rh;
        }
    }

    /** 색 정렬: Shift·⌫는 글자 키 색, 스페이스바는 기능키 색, Shift 고정은 강조색 없이. */
    private boolean gridColors;

    public void setGridColors(boolean on) {
        if (gridColors == on) return;
        gridColors = on;
        invalidate();
    }

    /** ⌫(위·왼쪽)와 스페이스바(위쪽)의 터치 인식 범위에서 뺄 비율 (0이면 그대로). 키 모양은 바꾸지 않는다. */
    private float deleteShrink, spaceShrink;

    public void setHitShrink(float deleteFraction, float spaceFraction) {
        deleteShrink = deleteFraction;
        spaceShrink = spaceFraction;
    }

    private Key keyAt(float x, float y) {
        if (layout == null) return null;
        int ri = layout.rows.length - 1;
        for (int i = 0; i < layout.rows.length; i++) {
            KeyboardLayout.Row row = layout.rows[i];
            if (row.keys.length > 0 && y < row.keys[0].rect.bottom) {
                ri = i;
                break;
            }
        }
        Key k = keyInRow(layout.rows[ri], x);
        if (k == null) return null;
        // 좁힌 범위 밖(⌫의 위·왼쪽·아래 끝, 스페이스바의 위쪽 끝)을 누르면 그쪽 이웃 키(윗줄·왼쪽·아랫줄 키)를
        // 누른 것으로 본다. ㅣ·ㅡ 등을 누르려다 ⌫가, 윗줄 글자를 누르려다 스페이스바가 눌리는 일을 줄인다.
        float shrink = k.type == Key.DELETE ? deleteShrink : k.type == Key.SPACE ? spaceShrink : 0f;
        if (shrink <= 0f) return k;
        boolean delete = k.type == Key.DELETE;
        float bandY = k.rect.height() * shrink, bandX = k.rect.width() * shrink;
        // 각 가장자리에서 얼마나 떨어져 있는지 (좁힌 범위 안이면 그 가장자리 쪽 이웃이 후보).
        float fromTop = y - k.rect.top, fromLeft = x - k.rect.left, fromBottom = k.rect.bottom - y;
        Key best = null;
        float bestDist = Float.MAX_VALUE;
        if (fromTop < bandY && ri > 0) {
            Key above = keyInRow(layout.rows[ri - 1], x);
            if (above != null && above.type != Key.SPACER && above.type != Key.GAP) {
                best = above;
                bestDist = fromTop;
            }
        }
        if (delete && fromLeft < bandX && fromLeft < bestDist) {
            Key left = leftNeighbor(layout.rows[ri], k);
            if (left != null) {
                best = left;
                bestDist = fromLeft;
            }
        }
        if (delete && fromBottom < bandY && fromBottom < bestDist && ri < layout.rows.length - 1) {
            // 아래쪽은 글자 키일 때만 넘긴다 (맨 아래 기능키 줄의 엔터 등이 잘못 눌리지 않게).
            Key below = keyInRow(layout.rows[ri + 1], x);
            if (below != null && below.type == Key.CHAR && !below.isFunctional()) best = below;
        }
        // 모서리에서는 더 가까운 가장자리 쪽 이웃을 고른다.
        return best != null ? best : k;
    }

    /** 줄에서 바로 왼쪽의 실제 키 (빈칸은 건너뛴다). 없으면 null. */
    private static Key leftNeighbor(KeyboardLayout.Row row, Key k) {
        Key prev = null;
        for (Key other : row.keys) {
            if (other == k) return prev;
            if (other.type != Key.SPACER && other.type != Key.GAP) prev = other;
        }
        return null;
    }

    /** 줄에서 x 위치의 키. 키 사이 빈틈(GAP 포함)이면 가장 가까운 키. */
    private static Key keyInRow(KeyboardLayout.Row target, float x) {
        Key best = null;
        float bestDist = Float.MAX_VALUE;
        for (Key k : target.keys) {
            if (k.type == Key.GAP) continue;   // 빈틈은 건너뛰어 양옆 중 가까운 키로 간다
            if (x >= k.rect.left && x < k.rect.right) return k;
            float d = Math.min(Math.abs(x - k.rect.left), Math.abs(x - k.rect.right));
            if (d < bestDist) {
                bestDist = d;
                best = k;
            }
        }
        return best;
    }

    // ---------------------------------------------------------------- 그리기

    @Override
    protected void onDraw(Canvas c) {
        if (layout == null) return;
        // 미리보기가 위로 넘어가려고 클리핑을 껐으므로, drawColor를 쓰면 위쪽 툴바까지 덮어 버린다.
        fill.setColor(theme.background);
        c.drawRect(0, 0, getWidth(), getHeight(), fill);
        for (KeyboardLayout.Row row : layout.rows) {
            for (Key k : row.keys) if (k.type != Key.SPACER && k.type != Key.GAP && !k.hidden) drawKey(c, k);
        }
        // 미리보기 말풍선
        if (showPreview) {
            for (int i = 0; i < pointers.size(); i++) {
                Pointer p = pointers.get(i);
                if (p.key.type == Key.CHAR && (p.mode == NORMAL || p.mode == SWIPED)) {
                    String swiped = p.mode == SWIPED ? p.key.swipeText(p.swipeDir) : null;
                    drawPreview(c, p.key, swiped != null ? swiped : displayLabel(p.key));
                }
            }
        }
        if (popupItems != null) drawPopup(c);
    }

    private boolean isPressed(Key k) {
        for (int i = 0; i < pointers.size(); i++) {
            if (pointers.get(i).key == k) return true;
        }
        return false;
    }

    private String displayLabel(Key k) {
        if (k.type == Key.CHAR && layout.isLetters() && shiftState != 0) return k.shifted;
        return k.label;
    }

    /**
     * 키 아랫변 바로 밑에만 보이는 그림자 (삼성 키보드처럼). 조금 아래로 내려 그린 그림자를
     * 키 아래쪽 절반 영역으로만 잘라 내서 위쪽과 옆쪽에는 번지지 않게 한다.
     */
    private void drawShadow(Canvas c, RectF r) {
        if (shadowPaint == null) {
            shadowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            shadowPaint.setMaskFilter(new android.graphics.BlurMaskFilter(dp(1.6f), android.graphics.BlurMaskFilter.Blur.NORMAL));
        }
        int max = theme.dark ? 190 : 130;
        shadowPaint.setColor((Math.round(max * keyShadowStrength / 100f) << 24));
        float dy = dp(1.3f);
        float blur = dp(3);
        c.save();
        c.clipRect(r.left - blur, r.centerY(), r.right + blur, r.bottom + dy + blur);
        // 옆으로 번진 부분이 키 가장자리 밖으로 보이지 않도록 좌우를 살짝 줄여 그린다.
        c.drawRoundRect(r.left + dp(0.8f), r.top + dy, r.right - dp(0.8f), r.bottom + dy, radius, radius, shadowPaint);
        c.restore();
    }

    private void drawKey(Canvas c, Key k) {
        RectF r = tmp;
        r.set(k.rect.left + gapX / 2, k.rect.top + gapY / 2, k.rect.right - gapX / 2, k.rect.bottom - gapY / 2);
        boolean pressed = isPressed(k);
        int bg;
        int fg = theme.text;
        if (k.type == Key.ENTER) {
            bg = theme.accent;
            fg = theme.onAccent;
        } else if (gridColors && (k.type == Key.SHIFT || k.type == Key.FUNCTION || k.type == Key.DELETE || k.type == Key.SYMBOL_PAGE
                || layout.kind == KeyboardLayout.NUMBER && (k.type == Key.TO_SYMBOLS || ".".equals(k.label)))
                && !(layout.kind == KeyboardLayout.NUMBER && k.type == Key.DELETE)) {
            // 색 정렬: 글자 사이의 Shift·⌫·기호 쪽 넘김 키는 글자 키 색 (Shift 고정도 강조색 없이).
            // 숫자 자판에서는 ?123·.를 숫자 키 색으로 맞추고, ⌫는 스페이스바·- 키와 같은 기능키 색으로 둔다.
            bg = theme.key;
        } else if (gridColors && k.type == Key.SPACE) {
            bg = theme.functionKey;   // 스페이스바는 맨 아래 줄의 다른 기능키 색
        } else if (k.type == Key.SHIFT && shiftState == 2) {
            bg = theme.accent;
            fg = theme.onAccent;
        } else {
            bg = k.isFunctional() ? theme.functionKey : theme.key;
        }
        if (pressed) bg = k.type == Key.ENTER ? blend(theme.accent, theme.text, 0.25f) : theme.keyPressed;
        if (keyShadow && !pressed) drawShadow(c, r);
        fill.setColor(bg);
        c.drawRoundRect(r, radius, radius, fill);

        float cx = r.centerX(), cy = r.centerY();
        float iconSize = Math.min(r.height(), r.width()) * 0.52f;
        icon.setColor(fg);
        switch (k.type) {
            case Key.SHIFT:
                Icons.draw(c, shiftState == 0 ? Icons.SHIFT : shiftState == 1 ? Icons.SHIFT_ON : Icons.SHIFT_LOCK,
                        cx, cy, Math.min(iconSize, dp(24)), icon);
                return;
            case Key.DELETE:
                Icons.draw(c, Icons.DELETE, cx, cy, Math.min(iconSize, dp(24)), icon);
                return;
            case Key.ENTER:
                Icons.draw(c, enterIcon, cx, cy, Math.min(iconSize, dp(24)), icon);
                return;
            case Key.LANGUAGE:
                Icons.draw(c, Icons.GLOBE, cx, cy, Math.min(iconSize, dp(22)), icon);
                return;
            case Key.EMOJI:
                Icons.draw(c, Icons.EMOJI, cx, cy, Math.min(iconSize, dp(22)), icon);
                return;
            case Key.SPACE:
                // 글자·기호 자판의 스페이스바는 비워 두고, 숫자 자판에서만 ␣로 띄어쓰기 키임을 알린다.
                if (layout.kind != KeyboardLayout.NUMBER) return;
                text.setColor(fg);
                text.setTypeface(Typeface.DEFAULT);
                text.setTextSize(spChar);
                drawCentered(c, "␣", cx, cy);
                return;
            default:
                break;
        }
        String label = displayLabel(k);
        text.setColor(fg);
        // 기능 키(?123, ABC, 가 등)의 글자는 글자 수와 상관없이 같은 크기로 그린다.
        boolean longLabel = !label.isEmpty() && k.type != Key.CHAR;
        text.setTypeface(longLabel ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
        text.setTextSize(longLabel ? spLabel : layout.kind == KeyboardLayout.NUMBER ? spNumber : spChar);
        drawCentered(c, label, cx, cy);

        String hint = shiftState != 0 && layout.kind == KeyboardLayout.ENGLISH ? k.hintUpper() : k.hint();
        if (showHints && hint != null && layout.kind != KeyboardLayout.NUMBER && !isRepeatChar(k)
                && !(numberHintHidden && KeyboardLayout.isDigit(k.label))) {
            text.setColor(theme.hint);
            text.setTypeface(Typeface.DEFAULT);
            text.setTextSize(spHint);
            text.getFontMetrics(fm);
            c.drawText(hint, r.right - dp(7), r.top - fm.ascent + dp(2), text);
        }
    }

    private void drawCentered(Canvas c, String s, float cx, float cy) {
        text.getFontMetrics(fm);
        c.drawText(s, cx, cy - (fm.ascent + fm.descent) / 2f, text);
    }

    private void drawPreview(Canvas c, Key k, String label) {
        float w = Math.max(k.rect.width() * 1.05f, dp(46));
        float h = k.rect.height() * 1.15f;
        float left = Math.max(0, Math.min(getWidth() - w, k.rect.centerX() - w / 2));
        float top = Math.max(-overflowTop, k.rect.top + gapY / 2 - h - dp(4));
        tmp.set(left, top, left + w, top + h);
        fill.setColor(theme.divider);
        tmp.offset(0, dp(1));
        c.drawRoundRect(tmp, radius, radius, fill);
        tmp.offset(0, -dp(1));
        fill.setColor(theme.popup);
        c.drawRoundRect(tmp, radius, radius, fill);
        text.setColor(theme.text);
        text.setTypeface(Typeface.DEFAULT);
        text.setTextSize(spPreview);
        drawCentered(c, label, tmp.centerX(), tmp.centerY());
    }

    private void drawPopup(Canvas c) {
        int rows = (popupItems.length + popupCols - 1) / popupCols;
        tmp.set(popupLeft, popupTop, popupLeft + popupCols * popupCellW, popupTop + rows * popupCellH);
        fill.setColor(theme.divider);
        tmp.inset(-dp(1), -dp(1));
        c.drawRoundRect(tmp, radius, radius, fill);
        tmp.inset(dp(1), dp(1));
        fill.setColor(theme.popup);
        c.drawRoundRect(tmp, radius, radius, fill);
        text.setTypeface(Typeface.DEFAULT);
        text.setTextSize(spChar * popupTextScale);
        for (int i = 0; i < popupItems.length; i++) {
            float l = popupLeft + (i % popupCols) * popupCellW;
            float t = popupTop + (i / popupCols) * popupCellH;
            tmp.set(l + dp(2), t + dp(2), l + popupCellW - dp(2), t + popupCellH - dp(2));
            if (i == popupSelected) {
                fill.setColor(theme.popupSelected);
                c.drawRoundRect(tmp, radius, radius, fill);
                text.setColor(theme.onAccent);
            } else {
                text.setColor(theme.text);
            }
            drawCentered(c, popupItems[i], tmp.centerX(), tmp.centerY());
        }
    }

    // ---------------------------------------------------------------- 터치

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        long before = visualState();
        int action = e.getActionMasked();
        int idx = e.getActionIndex();
        switch (action) {
            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_POINTER_DOWN:
                if (action == MotionEvent.ACTION_DOWN) cancelAll();
                // 두 손가락 밀기를 쓰면, 한 손가락만 누르고 있을 때 들어온 두 번째 손가락은 바로 앞 키를 입력하지 않고 지켜본다.
                if (action == MotionEvent.ACTION_POINTER_DOWN && deferred == null && pointers.size() == 1
                        && kbSwipes != null && kbSwipes.uses(KeyboardSwipes.TWO) && releasable(pointers.get(0))) {
                    Pointer first = pointers.get(0);
                    cancelLongPress(first);
                    down(e.getPointerId(idx), e.getX(idx), e.getY(idx));
                    Pointer next = find(e.getPointerId(idx));
                    if (next != null) {
                        deferred = first;
                        second = next;
                        deferredStartX = first.lastX;
                        deferredStartY = first.lastY;
                    } else {
                        release(first);   // 두 번째 손가락이 빈 자리에 닿았으면 평소처럼
                        pointers.remove(first);
                    }
                    break;
                }
                deferred = second = null;
                // 빠르게 칠 때 앞 손가락을 떼기 전에 다음 키를 누르면, 앞 키를 먼저 입력한다.
                for (int i = pointers.size() - 1; i >= 0; i--) {
                    Pointer p = pointers.get(i);
                    if (p.key.type == Key.SHIFT) {
                        p.chord = true;
                    } else if (releasable(p)) {
                        release(p);
                        pointers.remove(i);
                    }
                }
                down(e.getPointerId(idx), e.getX(idx), e.getY(idx));
                break;
            case MotionEvent.ACTION_MOVE:
                for (int i = 0; i < e.getPointerCount(); i++) {
                    Pointer p = find(e.getPointerId(i));
                    if (p != null) move(p, e.getX(i), e.getY(i));
                }
                if (deferred != null) checkTwoFingerSwipe();
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_POINTER_UP: {
                Pointer p = find(e.getPointerId(idx));
                if (p != null) {
                    move(p, e.getX(idx), e.getY(idx));
                    if (deferred != null && p != deferred && pointers.contains(deferred)) {
                        // 두 번째 손가락을 먼저 떼면, 미뤄 둔 앞 키를 먼저 입력해 순서를 지킨다.
                        release(deferred);
                        pointers.remove(deferred);
                    }
                    if (p == deferred || p == second) deferred = second = null;
                    release(p);
                    pointers.remove(p);
                }
                break;
            }
            case MotionEvent.ACTION_CANCEL:
                cancelAll();
                break;
            default:
                break;
        }
        // 손가락이 움직일 때마다 전체를 다시 그리지 않고, 보이는 것이 바뀐 경우에만 다시 그린다.
        if (visualState() != before) invalidate();
        return true;
    }

    /**
     * 화면에 보이는 터치 상태를 하나의 값으로 요약한다: 눌린 키와 그 모드(미리보기 글자, 쌍자음 표시),
     * 팝업 열림과 선택 칸. 이 값이 그대로면 다시 그릴 필요가 없다.
     */
    private long visualState() {
        long h = popupItems == null ? 0 : 1 + System.identityHashCode(popupItems);
        h = h * 31 + popupSelected;
        h = h * 31 + pointers.size();
        for (int i = 0; i < pointers.size(); i++) {
            Pointer p = pointers.get(i);
            h = h * 31 + System.identityHashCode(p.key);
            h = h * 31 + p.mode;
            h = h * 31 + (p.mode == SWIPED ? p.swipeDir : 0);
        }
        return h;
    }

    private Pointer find(int id) {
        for (int i = 0; i < pointers.size(); i++) {
            if (pointers.get(i).id == id) return pointers.get(i);
        }
        return null;
    }

    private void down(int id, float x, float y) {
        Key k = keyAt(x, y);
        boolean gap = k != null && k.type == Key.SPACER && k.dragCursor && gapCursor;
        if (k == null || (!gap && (k.type == Key.SPACER || k.type == Key.GAP))) return;
        Pointer p = new Pointer();
        p.id = id;
        p.key = k;
        p.downX = x;
        p.downY = y;
        p.cursorAnchorX = x;
        p.cursorAnchorY = y;
        p.lastX = x;
        p.lastY = y;
        p.mode = NORMAL;
        pointers.add(p);
        if (gap) return;   // 눌러도 아무 키도 입력되지 않고 소리·진동도 없다. 밀면 커서만 움직인다.
        if (listener != null) {
            listener.onKeyPress(k);
            if (k.type == Key.SHIFT) {
                listener.onKeyTap(k);
                p.mode = CONSUMED;
            }
        }
        handler.removeCallbacks(longPressRunnable);
        // 길게 누를 수 있는 기능키인지 정한다 (시간은 모든 기능키가 같다).
        boolean del, lang;
        if (longPressActions != null || k.type == Key.FUNCTION) {
            // 기능키 길게 누르기 사용자화: 정한 기능이 있는 키만 길게 누를 수 있다.
            String action = customLongPress(k);
            del = SwipeAction.DELETE_REPEAT.equals(action);
            lang = action != null && !del;
        } else {
            del = k.type == Key.DELETE && longPressDelete;
            lang = k.type == Key.LANGUAGE
                    || (modeKeyLongPress && (k.type == Key.TO_SYMBOLS || k.type == Key.TO_LETTERS));
        }
        boolean chars = k.type == Key.CHAR && (k.popup != null && longPressChars || isRepeatChar(k));
        if (del || lang || chars) {
            longPressTarget = p;
            // 기능키는 모두 '기능키 길게 누르기 시간'을, 문자 키는 '문자 길게 누르기 시간'을 따른다.
            long delay = del || lang ? fnPressMs : longPressMs;
            handler.postDelayed(longPressRunnable, delay);
        }
    }

    /** 두 번째 손가락이 닿을 때 앞 키를 바로 입력해도 되는 상태 (아직 입력하지 않은 키). */
    private static boolean releasable(Pointer p) {
        return p.key.type != Key.SHIFT
                && (p.mode == NORMAL || p.mode == SWIPED || p.mode == EMOJI_SWIPE || p.mode == FN_SWIPE);
    }

    /** 두 손가락이 같은 방향으로 충분히 움직였으면 그 방향의 기능을 실행하고, 두 손가락의 입력은 모두 취소한다. */
    private void checkTwoFingerSwipe() {
        if (second == null || !pointers.contains(deferred) || !pointers.contains(second)) return;
        float d1x = deferred.lastX - deferredStartX, d1y = deferred.lastY - deferredStartY;
        float d2x = second.lastX - second.downX, d2y = second.lastY - second.downY;
        int dir = strictDirection(d1x, d1y);
        if (dir < 0 || dir != strictDirection(d2x, d2y)) return;
        float need = fnSwipeThreshold * 1.5f;
        if (swipeDistance(dir, d1x, d1y) < need || swipeDistance(dir, d2x, d2y) < need) return;
        String action = kbSwipes.action(KeyboardSwipes.TWO, dir);
        if (SwipeAction.NONE.equals(action)) return;
        for (Pointer p : new Pointer[]{deferred, second}) {
            cancelLongPress(p);
            if (p.mode == REPEAT) handler.removeCallbacks(repeatRunnable);
            if (p.mode == CHAR_REPEAT) stopCharRepeat();
            if (p == popupPointer) closePopup();
            p.mode = CONSUMED;
        }
        deferred = second = null;
        if (listener != null) {
            listener.onGesture();
            listener.onKeyFunction(null, action);
        }
    }

    /** 한 축으로 뚜렷하게 민 방향. 대각선이면 -1. */
    private static int strictDirection(float dx, float dy) {
        if (Math.abs(dx) > Math.abs(dy) * 1.5f) return dx < 0 ? Key.SWIPE_LEFT : Key.SWIPE_RIGHT;
        if (Math.abs(dy) > Math.abs(dx) * 1.5f) return dy < 0 ? Key.SWIPE_UP : Key.SWIPE_DOWN;
        return -1;
    }

    private void move(Pointer p, float x, float y) {
        p.lastX = x;
        p.lastY = y;
        float dx = x - p.downX, dy = y - p.downY;
        switch (p.mode) {
            case NORMAL:
            case SWIPED:
                if (p.key.type == Key.SPACER) {
                    // 분리 키보드 가운데 빈 공간: 충분히 밀면 커서 이동 (스페이스바와 같은 거리·축 설정).
                    if (deferred == null && ((cursorH && Math.abs(dx) > fnSwipeThreshold)
                            || (cursorV && Math.abs(dy) > fnSwipeThreshold * 1.5f))) {
                        p.mode = CURSOR;
                        p.gap = true;
                        p.cursorAnchorX = x;
                        p.cursorAnchorY = y;
                    }
                    break;
                }
                if (p.key.type == Key.FUNCTION) {
                    moveFnKey(p, dx, dy);
                    break;
                }
                int fnSlot = fnSwipes == null ? -1 : FunctionSwipes.slotOf(p.key.type);
                if (fnSlot >= 0) {
                    moveFunctionKey(p, fnSlot, dx, dy, x, y);
                    break;
                }
                if (p.key.type == Key.SPACE && spaceLangSwipe && Math.abs(dx) > fnSwipeThreshold
                        && Math.abs(dx) > Math.abs(dy)) {
                    // 스페이스바를 좌우로 밀어 한/영 전환: 손을 떼면 바꾼다 (되돌리면 취소).
                    p.mode = FN_SWIPE;
                    p.fnAction = SwipeAction.LANGUAGE;
                    p.swipeDir = dx > 0 ? Key.SWIPE_RIGHT : Key.SWIPE_LEFT;
                } else if (p.key.type == Key.SPACE && spaceCursor && deferred == null
                        && ((!spaceLangSwipe && cursorH && Math.abs(dx) > fnSwipeThreshold)
                        || (cursorV && Math.abs(dy) > fnSwipeThreshold * 1.5f))) {
                    // 좌우로 밀면 글자 단위, 위아래로 밀면 줄 단위로 커서를 옮긴다.
                    p.mode = CURSOR;
                    p.cursorAnchorX = x;
                    p.cursorAnchorY = y;
                    cancelLongPress(p);
                } else if (p.key.type == Key.DELETE && deleteWordSwipe && -dy > fnSwipeThreshold * 1.5f
                        && -dy > Math.abs(dx)) {
                    p.mode = DELETE_LINE_START;
                    cancelLongPress(p);
                } else if (p.key.type == Key.DELETE && deleteWordSwipe && dx < -fnSwipeThreshold * 1.5f) {
                    p.mode = DELETE_WORD;
                    cancelLongPress(p);
                } else if (modeKeyEmoji && isModeKey(p.key) && layout.kind != KeyboardLayout.NUMBER) {
                    // 손가락이 많이 움직였으면 길게 누르기(이모지 열기)는 취소하고, 위로 충분히 밀었으면 이모지 열기.
                    if (Math.hypot(dx, dy) > fnSwipeThreshold) cancelLongPress(p);
                    if (-dy > fnSwipeThreshold && -dy > Math.abs(dx)) p.mode = EMOJI_SWIPE;
                } else if (p.key.type == Key.CHAR) {
                    if (Math.hypot(dx, dy) > swipeThreshold) cancelLongPress(p);
                    // 움직인 방향이 문턱을 넘고 그 방향에 배치된 글자가 있으면 스와이프. 좌우로 밀 때 손가락이 아래로 처지는 것을
                    // 감안해 좌우는 후하게, 아래는 엄격하게 판정한다 (SwipeDirection).
                    int dir = SwipeDirection.of(dx, dy, downRatio, upRatio);
                    // 그 방향에 밀어서 입력할 글자가 있으면 커서 이동·키보드 밀기보다 글자 입력이 먼저다
                    // ('밀어서 기능'의 미는 거리가 더 짧아도 글자를 밀다가 커서가 움직이지 않게).
                    boolean letterThere = p.key.swipeText(dir) != null;
                    if (swipeDistance(dir, dx, dy) > swipeThreshold && letterThere) {
                        p.mode = SWIPED;
                        p.swipeDir = dir;
                    } else if (p.mode == SWIPED && swipeDistance(p.swipeDir, dx, dy) > swipeThreshold * 0.5f) {
                        // 한번 스와이프로 들어가면 문턱의 절반까지 되돌려도 유지한다.
                    } else if (charCursor && p.mode == NORMAL && deferred == null && !letterThere
                            && ((dir == Key.SWIPE_LEFT || dir == Key.SWIPE_RIGHT) ? charCursorH : charCursorV)
                            && swipeDistance(dir, dx, dy)
                            > fnSwipeThreshold * (dir == Key.SWIPE_UP || dir == Key.SWIPE_DOWN ? 1.5f : 1f)) {
                        // 그 방향에 밀어서 입력할 글자가 없으면 트랙패드처럼 커서를 옮긴다 (세로는 스페이스바처럼 1.5배).
                        p.mode = CURSOR;
                        p.freeCursor = true;
                        p.freeH = charCursorH;
                        p.freeV = charCursorV;
                        p.cursorAnchorX = x;
                        p.cursorAnchorY = y;
                        cancelLongPress(p);
                    } else if (kbSwipes != null && p.mode == NORMAL && deferred == null && !letterThere
                            && SwipeAction.FREE_CURSOR.equals(kbSwipes.action(KeyboardSwipes.ONE, dir))
                            && swipeDistance(dir, dx, dy)
                            > fnSwipeThreshold * (dir == Key.SWIPE_UP || dir == Key.SWIPE_DOWN ? 1.5f : 1f)) {
                        // 한 손가락 키보드 밀기의 '커서 자유 이동': 문자 키 커서 이동과 똑같이 움직인다.
                        p.mode = CURSOR;
                        p.freeCursor = true;
                        p.freeH = kbSwipes.freeCursor(true);
                        p.freeV = kbSwipes.freeCursor(false);
                        p.cursorAnchorX = x;
                        p.cursorAnchorY = y;
                        cancelLongPress(p);
                    } else if (kbSwipes != null && p.mode == NORMAL && deferred == null && !letterThere
                            && swipeDistance(dir, dx, dy) > fnSwipeThreshold
                            && !SwipeAction.NONE.equals(kbSwipes.action(KeyboardSwipes.ONE, dir))
                            && !SwipeAction.FREE_CURSOR.equals(kbSwipes.action(KeyboardSwipes.ONE, dir))) {
                        // 한 손가락 키보드 밀기: 손을 떼면 그 방향의 기능을 실행한다 (되돌리면 취소).
                        p.mode = FN_SWIPE;
                        p.fnAction = kbSwipes.action(KeyboardSwipes.ONE, dir);
                        p.swipeDir = dir;
                        cancelLongPress(p);
                    } else {
                        p.mode = NORMAL;
                    }
                }
                break;
            case CURSOR: {
                // 완전 사용자화를 켰으면 '커서 이동'을 정한 방향의 축만 움직인다.
                boolean curH = p.gap ? cursorH : p.freeCursor ? p.freeH
                        : fnSwipes != null ? fnSwipes.cursorH() : cursorH && !spaceLangSwipe;
                boolean curV = p.gap ? cursorV : p.freeCursor ? p.freeV : fnSwipes != null ? fnSwipes.cursorV() : cursorV;
                // 커서 이동 속도: 한 칸(한 줄) 움직이는 데 필요한 손가락 거리를 속도만큼 줄이거나 늘린다.
                float stepX = cursorStep / (p.freeCursor ? charSpeedH : spaceSpeedH);
                float stepY = cursorStepY / (p.freeCursor ? charSpeedV : spaceSpeedV);
                float d = x - p.cursorAnchorX;
                while (curH && Math.abs(d) >= stepX) {
                    int dir = d > 0 ? 1 : -1;
                    if (listener != null) listener.onCursorMove(dir);
                    p.cursorAnchorX += dir * stepX;
                    d = x - p.cursorAnchorX;
                }
                float dv = y - p.cursorAnchorY;
                while (curV && Math.abs(dv) >= stepY) {
                    int dir = dv > 0 ? 1 : -1;
                    if (listener != null) listener.onCursorMoveVertical(dir);
                    p.cursorAnchorY += dir * stepY;
                    dv = y - p.cursorAnchorY;
                }
                break;
            }
            case DELETE_WORD:
                if (dx > -fnSwipeThreshold) p.mode = NORMAL;
                break;
            case DELETE_LINE_START:
                if (-dy < fnSwipeThreshold) p.mode = NORMAL;
                break;
            case FN_SWIPE:
                // 한번 밀기로 들어가면 문턱의 절반까지 되돌려도 유지하고, 그보다 더 되돌리면 그냥 탭으로 돌아간다.
                if (swipeDistance(p.swipeDir, dx, dy) < fnSwipeThreshold * 0.5f) {
                    p.mode = NORMAL;
                    p.fnAction = null;
                }
                break;
            case EMOJI_SWIPE:
                // 한번 밀기로 들어가면 문턱의 절반까지 되돌려도 유지하고, 그보다 더 되돌리면 그냥 탭으로 돌아간다.
                if (-dy < fnSwipeThreshold * 0.5f) p.mode = NORMAL;
                break;
            case POPUP:
                if (p == popupPointer) updatePopupSelection(x, y);
                break;
            default:
                break;
        }
    }

    /** 해당 방향으로 얼마나 움직였는지. */
    private static float swipeDistance(int dir, float dx, float dy) {
        switch (dir) {
            case Key.SWIPE_DOWN: return dy;
            case Key.SWIPE_UP: return -dy;
            case Key.SWIPE_LEFT: return -dx;
            default: return dx;
        }
    }

    private void release(Pointer p) {
        cancelLongPress(p);
        if (listener == null) return;
        switch (p.mode) {
            case NORMAL:
                if (p.key.type == Key.FUNCTION) {
                    String tap = fnKeyAction(FnKeyActions.I_TAP);
                    if (tap != null) listener.onKeyFunction(p.key, tap);
                    markChord();
                } else if (p.key.type != Key.SHIFT && p.key.type != Key.SPACER) {
                    listener.onKeyTap(p.key);
                    markChord();
                }
                break;
            case SWIPED:
                String swiped = p.key.swipeText(p.swipeDir);
                if (swiped != null) {
                    listener.onGesture();
                    listener.onKeySwipe(p.key, swiped);
                }
                markChord();
                break;
            case POPUP:
                if (popupItems != null && popupSelected >= 0) {
                    listener.onPopupChar(p.key, popupItems[popupSelected]);
                    markChord();
                }
                closePopup();
                break;
            case DELETE_WORD:
                listener.onGesture();
                listener.onDeleteWord();
                break;
            case DELETE_LINE_START:
                listener.onGesture();
                listener.onDeleteToLineStart();
                break;
            case EMOJI_SWIPE:
                listener.onGesture();
                listener.onModeKeySwipeUp(p.key);
                markChord();
                break;
            case FN_SWIPE:
                if (p.fnAction != null) {
                    listener.onGesture();
                    listener.onKeyFunction(p.key, p.fnAction);
                }
                markChord();
                break;
            case REPEAT:
                handler.removeCallbacks(repeatRunnable);
                break;
            case CHAR_REPEAT:
                stopCharRepeat();
                break;
            case CONSUMED:
                if (p.key.type == Key.SHIFT && p.chord) listener.onShiftChordEnd();
                break;
            default:
                break;
        }
    }

    /**
     * 밀어서 기능 완전 사용자화를 켰을 때 기능 키의 밀기를 처리한다. 가장 많이 움직인 방향이 문턱을 넘으면 그 방향에
     * 정해 둔 기능을 실행할 준비를 하고(손을 떼면 실행), '커서 이동'이면 스페이스바 커서 이동을 시작한다.
     * 스페이스바를 세로로 밀 때는 가로 때보다 1.5배 더 밀어야 한다 (손가락이 떨리는 것과 구분하려고).
     */
    private void moveFunctionKey(Pointer p, int slot, float dx, float dy, float x, float y) {
        // 충분히 움직였으면 길게 누르기(지우기 반복, 이모지 열기, 입력기 선택 등)는 취소한다.
        if (Math.hypot(dx, dy) > fnSwipeThreshold) cancelLongPress(p);
        boolean horizontal = Math.abs(dx) > Math.abs(dy);
        int dir = horizontal ? (dx > 0 ? Key.SWIPE_RIGHT : Key.SWIPE_LEFT) : (dy > 0 ? Key.SWIPE_DOWN : Key.SWIPE_UP);
        float need = fnSwipeThreshold * (slot == FunctionSwipes.SPACE && !horizontal ? 1.5f : 1f);
        String action = swipeDistance(dir, dx, dy) > need ? fnSwipes.action(slot, dir) : null;
        if (SwipeAction.CURSOR_MOVE.equals(action)) {
            if (deferred != null) return;   // 두 손가락 밀기를 지켜보는 동안에는 커서를 움직이지 않는다
            p.mode = CURSOR;
            p.cursorAnchorX = x;
            p.cursorAnchorY = y;
            cancelLongPress(p);
        } else if (action != null && !SwipeAction.NONE.equals(action)) {
            p.mode = FN_SWIPE;
            p.fnAction = action;
            p.swipeDir = dir;
        }
    }

    /** Fn 키의 밀기: 가장 많이 움직인 방향이 문턱을 넘고 그 방향에 기능이 있으면 손을 뗄 때 실행할 준비를 한다. */
    private void moveFnKey(Pointer p, float dx, float dy) {
        if (Math.hypot(dx, dy) > fnSwipeThreshold) cancelLongPress(p);
        int dir = Math.abs(dx) > Math.abs(dy)
                ? (dx > 0 ? Key.SWIPE_RIGHT : Key.SWIPE_LEFT)
                : (dy > 0 ? Key.SWIPE_DOWN : Key.SWIPE_UP);
        String action = swipeDistance(dir, dx, dy) > fnSwipeThreshold ? fnKeyAction(FnKeyActions.I_SWIPE + dir) : null;
        if (action != null) {
            p.mode = FN_SWIPE;
            p.fnAction = action;
            p.swipeDir = dir;
        }
    }

    /** 왼쪽 아래의 자판 전환 키 (?123, 가, ABC). */
    private static boolean isModeKey(Key k) {
        return k.type == Key.TO_SYMBOLS || k.type == Key.TO_LETTERS;
    }

    private void markChord() {
        for (int i = 0; i < pointers.size(); i++) {
            if (pointers.get(i).key.type == Key.SHIFT) pointers.get(i).chord = true;
        }
    }

    private void stopCharRepeat() {
        handler.removeCallbacks(charRepeatRunnable);
        charRepeatPointer = null;
    }

    private void cancelLongPress(Pointer p) {
        if (longPressTarget == p) {
            handler.removeCallbacks(longPressRunnable);
            longPressTarget = null;
        }
    }

    private void onLongPress() {
        Pointer p = longPressTarget;
        longPressTarget = null;
        if (p == null || !pointers.contains(p)) return;
        Key k = p.key;
        // Shift 키는 누르는 순간 입력되므로(CONSUMED) 그대로 길게 누를 수 있다. 다른 키와 함께 눌렀으면(chord) 하지 않는다.
        boolean shiftHeld = k.type == Key.SHIFT && p.mode == CONSUMED && !p.chord;
        if (p.mode != NORMAL && !shiftHeld) return;
        String custom = customLongPress(k);
        if (custom != null) {
            if (SwipeAction.DELETE_REPEAT.equals(custom)) {
                p.mode = REPEAT;
                repeatRunnable.run();
            } else {
                p.mode = CONSUMED;
                if (listener != null) {
                    listener.onGesture();
                    listener.onKeyFunction(k, custom);
                }
            }
            invalidate();
            return;
        }
        if (shiftHeld) return;
        if (k.type == Key.DELETE) {
            p.mode = REPEAT;
            repeatRunnable.run();
        } else if (k.type == Key.LANGUAGE || k.type == Key.TO_SYMBOLS || k.type == Key.TO_LETTERS) {
            p.mode = CONSUMED;
            if (listener != null) {
                listener.onGesture();
                listener.onKeyLongPress(k);
            }
        } else if (isRepeatChar(k)) {
            p.mode = CHAR_REPEAT;
            charRepeatPointer = p;
            charRepeatRunnable.run();
            markChord();
        } else if (k.popup != null && longPressChars) {
            p.mode = POPUP;
            openPopup(p);
            if (listener != null) listener.onGesture();
        }
        invalidate();
    }

    private void openPopup(Pointer p) {
        String[] items = p.key.popup.clone();
        if (shiftState != 0 && layout.kind == KeyboardLayout.ENGLISH) {
            // 한 글자(é 같은 악센트 문자)만 Shift를 따른다. 두 글자 이상(단어)은 정해 둔 그대로 입력한다.
            for (int i = 0; i < items.length; i++) {
                if (Key.isSingleChar(items[i])) items[i] = items[i].toUpperCase(Locale.ROOT);
            }
        }
        popupItems = items;
        popupPointer = p;
        popupCellW = Math.max(dp(40), Math.min(p.key.rect.width(), dp(52)));
        popupCellH = Math.min(p.key.rect.height(), dp(54));
        // 여러 글자로 된 항목(.com, .co.kr 등)은 칸을 조금 넓히고, 그래도 넘치면 글자를 줄여 칸 안에 넣는다.
        text.setTypeface(Typeface.DEFAULT);
        text.setTextSize(spChar);
        float widest = 0f;
        for (String item : items) widest = Math.max(widest, text.measureText(item));
        float pad = dp(10);
        popupCellW = Math.max(popupCellW, Math.min(widest + pad, dp(64)));
        popupTextScale = widest + pad > popupCellW ? Math.max(0.3f, (popupCellW - pad) / widest) : 1f;
        float margin = dp(4);
        popupCols = Math.max(1, Math.min(items.length, (int) ((getWidth() - 2 * margin) / popupCellW)));
        int rows = (items.length + popupCols - 1) / popupCols;
        float w = popupCols * popupCellW;
        popupLeft = p.key.rect.centerX() - popupCellW / 2;
        popupLeft = Math.max(margin, Math.min(getWidth() - margin - w, popupLeft));
        popupTop = p.key.rect.top + gapY / 2 - rows * popupCellH - dp(4);
        popupTop = Math.max(-overflowTop, popupTop);
        popupSelected = 0;
        popupMoved = false;
    }

    private void updatePopupSelection(float x, float y) {
        if (!popupMoved) {
            // 손가락을 떼지 않고 그 자리에 있으면 팝업의 어느 칸 아래에 있든 첫 번째 문자가 선택돼야 한다.
            // 살짝 떨리는 정도(터치 슬롭 이내)로는 선택을 바꾸지 않는다.
            float dx = x - popupPointer.downX, dy = y - popupPointer.downY;
            if (dx * dx + dy * dy < dp(10) * dp(10)) return;
            popupMoved = true;
        }
        int rows = (popupItems.length + popupCols - 1) / popupCols;
        int col = (int) Math.floor((x - popupLeft) / popupCellW);
        int row = (int) Math.floor((y - popupTop) / popupCellH);
        col = Math.max(0, Math.min(popupCols - 1, col));
        row = Math.max(0, Math.min(rows - 1, row));
        popupSelected = Math.min(popupItems.length - 1, row * popupCols + col);
    }

    private void closePopup() {
        popupItems = null;
        popupPointer = null;
    }

    /** Shift 키를 누르고 있는 중인지 (누른 채 여러 글자를 대문자로 칠 때). */
    public boolean isShiftHeld() {
        for (int i = 0; i < pointers.size(); i++) {
            if (pointers.get(i).key.type == Key.SHIFT) return true;
        }
        return false;
    }

    public void cancelAll() {
        handler.removeCallbacks(longPressRunnable);
        handler.removeCallbacks(repeatRunnable);
        stopCharRepeat();
        longPressTarget = null;
        pointers.clear();
        deferred = second = null;
        closePopup();
        invalidate();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        cancelAll();
    }

    // ---------------------------------------------------------------- 단위

    /** 화면 밀도. 그릴 때마다 읽지 않고 한 번만 읽어 두며, 설정이 바뀌면 다시 읽는다. */
    private float density;

    private float dp(float v) {
        return v * density;
    }

    @Override
    protected void onConfigurationChanged(android.content.res.Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        density = getResources().getDisplayMetrics().density;
        updateTextSizes();
    }

    /**
     * 키 글자 크기(px): 기능키 글자(14sp), 글자 키(22sp), 숫자 자판(24sp), 길게 누르기 힌트(9.5sp), 미리보기(30sp).
     * 그릴 때마다 단위를 바꾸지 않도록 글자 크기 비율이나 화면 설정이 바뀔 때만 다시 구한다.
     * (Android 14부터 큰 글꼴 배율은 sp에 비례하지 않으므로 1sp 값을 곱하지 않고 크기마다 따로 구한다.)
     */
    private float spLabel, spChar, spNumber, spHint, spPreview;

    private void updateTextSizes() {
        spLabel = sp(14);
        spChar = sp(22);
        spNumber = sp(24);
        spHint = sp(9.5f);
        spPreview = sp(30);
    }

    private float sp(float v) {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, v, getResources().getDisplayMetrics()) * textScale;
    }

    private static int blend(int a, int b, float t) {
        int ar = (a >> 16) & 0xFF, ag = (a >> 8) & 0xFF, ab = a & 0xFF;
        int br = (b >> 16) & 0xFF, bg = (b >> 8) & 0xFF, bb = b & 0xFF;
        return 0xFF000000 | ((int) (ar + (br - ar) * t) << 16) | ((int) (ag + (bg - ag) * t) << 8)
                | (int) (ab + (bb - ab) * t);
    }
}
