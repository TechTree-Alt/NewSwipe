package com.alternative_studios.newswipe;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

import com.alternative_studios.newswipe.keyboard.Icons;
import com.alternative_studios.newswipe.keyboard.KeyboardLayout;
import com.alternative_studios.newswipe.keyboard.KeyboardTheme;
import com.alternative_studios.newswipe.keyboard.KeyboardView;
import com.alternative_studios.newswipe.keyboard.ToolbarButtons;
import com.alternative_studios.newswipe.ui.IconButton;
import com.alternative_studios.newswipe.ui.Ui;

/**
 * '한 손 모드' 설정 화면 아래에 붙는, 실제 키보드와 같은 크기·모양의 한 손 모드 미리보기 (왼쪽으로 쏠린 모습).
 * 폴더블 내부 화면 키보드 사용자화의 미리보기처럼 손잡이를 끌어 값을 바로 바꾼다:
 * <ul>
 *   <li>자판 오른쪽 가장자리 = 자판 폭 (좌우로 끈다, 5% 단위)</li>
 *   <li>자판 위 가장자리 = 키 높이 (위·아래로 끈다, 5% 단위)</li>
 *   <li>자판 아래 가장자리 = 세로 위치 (위로 끌면 자판이 화면 아래에서 떠오른다, 4dp 단위)</li>
 * </ul>
 * 실제 키보드처럼 화면 아래에 붙어 있어, 키를 키우거나 자판을 띄우면 미리보기가 위로 자란다.
 * 값은 바뀔 때마다 바로 저장하고, 설정 화면이 슬라이더를 맞추도록 알린다.
 */
final class OneHandPreview extends FrameLayout {

    interface Listener {
        /** 손잡이를 끌어 값(prefKey)을 바꾸고 저장했다. */
        void onSizeChanged(String prefKey);
    }

    /** 손잡이: 끄는 방향(위아래인지)·이름·저장 키·단위. */
    enum Handle {
        WIDTH(false, "자판 폭", Prefs.ONE_HAND_WIDTH, "%"),
        HEIGHT(true, "키 높이", Prefs.ONE_HAND_HEIGHT, "%"),
        LIFT(true, "세로 위치", Prefs.ONE_HAND_LIFT, "dp");

        final boolean vertical;
        final String label, key, unit;

        Handle(boolean vertical, String label, String key, String unit) {
            this.vertical = vertical;
            this.label = label;
            this.key = key;
            this.unit = unit;
        }
    }

    static final int WIDTH_MIN = 60, WIDTH_MAX = 90, HEIGHT_MIN = 60, HEIGHT_MAX = 100, LIFT_MIN = 0, LIFT_MAX = 160;

    private final Prefs prefs;
    private final Listener listener;
    private final KeyboardTheme theme;
    private final LinearLayout body, column, side;
    private final View lift;
    private final KeyboardView keys;
    private final Overlay overlay;

    OneHandPreview(Context c, Prefs prefs, Listener listener) {
        super(c);
        this.prefs = prefs;
        this.listener = listener;
        theme = KeyboardTheme.of(c);

        LinearLayout frame = new LinearLayout(c);
        frame.setOrientation(LinearLayout.VERTICAL);
        frame.setBackgroundColor(theme.background);
        frame.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);

        body = new LinearLayout(c);
        body.setOrientation(LinearLayout.HORIZONTAL);
        frame.addView(body, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        lift = new View(c);
        frame.addView(lift, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0));

        column = new LinearLayout(c);
        column.setOrientation(LinearLayout.VERTICAL);
        if (prefs.toolbar()) column.addView(toolbarMock(c));
        keys = new KeyboardView(c);
        keys.setTheme(theme);
        keys.setKeyShadow(prefs.keyShadow(), prefs.keyShadowStrength());
        keys.setKeyRadius(Ui.dp(c, prefs.keyRadiusDp()));
        keys.setGridColors(prefs.gridColors());
        keys.setPopupHints(!prefs.popupHintHidden(), prefs.popupHintPosition());
        keys.setNumberHintHidden(prefs.numberHintHidden());
        keys.setKeyGaps(Ui.dp(c, prefs.keyGapXDp()), Ui.dp(c, prefs.keyGapYDp()));
        keys.setInsets(Ui.dp(c, prefs.padLeftDp()), Ui.dp(c, prefs.padTopDp()),
                Ui.dp(c, prefs.padRightDp()), Ui.dp(c, prefs.padBottomDp()));
        keys.setOnTouchListener((v, ev) -> true);
        column.addView(keys, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // 빈 곳: 키보드처럼 자판을 옮기는 화살표와 한 손 모드를 끄는 확장 아이콘 (누를 수 없음).
        side = new LinearLayout(c);
        side.setOrientation(LinearLayout.VERTICAL);
        side.setGravity(Gravity.CENTER);
        side.addView(sideButton(c, Icons.NEXT));
        side.addView(sideButton(c, Icons.EXPAND));
        body.addView(column, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        body.addView(side, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));

        addView(frame, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        overlay = new Overlay(c);
        addView(overlay, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        refresh();
    }

    /** 크기는 자판 쪽이 정한다. 위에 덮은 손잡이 층은 그 크기를 그대로 따른다. */
    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        View frame = getChildAt(0);
        measureChild(frame, widthMeasureSpec, heightMeasureSpec);
        int w = MeasureSpec.getSize(widthMeasureSpec), h = frame.getMeasuredHeight();
        overlay.measure(MeasureSpec.makeMeasureSpec(w, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(h, MeasureSpec.EXACTLY));
        setMeasuredDimension(w, h);
    }

    /** 키보드의 applyOneHand와 같은 규칙으로 지금 값에 맞춰 다시 놓는다. */
    void refresh() {
        Context c = getContext();
        int width = prefs.oneHandWidth();
        ((LinearLayout.LayoutParams) column.getLayoutParams()).weight = width;
        ((LinearLayout.LayoutParams) side.getLayoutParams()).weight = 100 - width;
        body.requestLayout();
        Prefs view = prefs.oneHandView();
        KeyboardLayout l = KeyboardLayout.korean(view);
        keys.setKeyboardHeight(Math.round(keyUnit() * KeyboardLayout.rowUnits(view) * prefs.oneHandHeight() / 100f)
                + Ui.dp(c, prefs.padTopDp()) + Ui.dp(c, prefs.padBottomDp()));
        // 키 높이를 줄인 만큼 키 글자도 같은 비율로 줄인다 (키보드와 같다).
        keys.configure(prefs.longPressMs(), prefs.swipeThresholdDp(), false,
                Math.round(prefs.keyTextSize() * prefs.oneHandHeight() / 100f));
        keys.setLayout(l);
        ViewGroup.LayoutParams llp = lift.getLayoutParams();
        llp.height = Ui.dp(c, prefs.oneHandLiftDp());
        lift.setLayoutParams(llp);
        overlay.invalidate();
        overlay.post(overlay::updateExclusion);
    }

    /** 보통 키 높이 (키 높이 100%일 때 한 줄의 px). */
    private float keyUnit() {
        return Ui.dp(getContext(), 54) * prefs.keyboardHeight() / 100f;
    }

    private View toolbarMock(Context c) {
        LinearLayout bar = new LinearLayout(c);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(Ui.dp(c, prefs.padLeftDp()), 0, Ui.dp(c, prefs.padRightDp()), 0);
        String[][] order = prefs.toolOrder();
        for (int s = 0; s < 2; s++) {
            if (s == 1) bar.addView(new View(c), new LinearLayout.LayoutParams(0, 1, 1f));
            for (String id : order[s]) {
                if (!prefs.toolShown(id)) continue;
                IconButton b = new IconButton(c, ToolbarButtons.icon(id), theme.text, theme.keyPressed, null);
                b.setToolbarStyle(theme, Ui.dp(c, 40));
                b.setClickable(false);
                b.setFocusable(false);
                bar.addView(b, new LinearLayout.LayoutParams(Ui.dp(c, 40), Ui.dp(c, 40)));
            }
        }
        bar.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(c, 40)));
        return bar;
    }

    private IconButton sideButton(Context c, int icon) {
        IconButton b = new IconButton(c, icon, theme.hint, theme.keyPressed, null);
        b.setBackground(Ui.round(theme.functionKey, Ui.dp(c, 12)));
        b.setClickable(false);
        b.setFocusable(false);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(c, 48));
        lp.setMargins(Ui.dp(c, 6), Ui.dp(c, 6), Ui.dp(c, 6), Ui.dp(c, 6));
        b.setLayoutParams(lp);
        return b;
    }

    // ---------------------------------------------------------------- 손잡이

    /**
     * 손잡이가 붙는 윤곽선: 자판(키 부분)의 테두리를 조금 안으로 줄인 것 (미리보기 좌표). 세 손잡이가 모두 이 윤곽선의
     * 변을 바꾼다 (오른쪽 = 자판 폭, 위 = 키 높이, 아래 = 세로 위치).
     */
    private void outlineBounds(RectF out) {
        float top = body.getTop() + column.getTop() + keys.getTop();
        out.set(column.getLeft(), top, column.getRight(), top + keys.getHeight());
        float in = Ui.dp(getContext(), 2);
        out.inset(in, in);
    }

    /**
     * 손잡이의 가운데 (미리보기 좌표). 아직 배치되지 않았으면 false.
     * 키보드 사용자화처럼 경계선 위가 아니라 윤곽선 안쪽으로 같은 간격을 두고 각 변의 가운데에 둔다.
     */
    private boolean handleCenter(Handle h, float[] out) {
        if (column.getWidth() == 0) return false;
        RectF r = new RectF();
        outlineBounds(r);
        float in = Ui.dp(getContext(), 9);
        switch (h) {
            case WIDTH:
                out[0] = r.right - in;
                out[1] = r.centerY();
                return true;
            case HEIGHT:
                out[0] = r.centerX();
                out[1] = r.top + in;
                return true;
            default:   // LIFT
                out[0] = r.centerX();
                out[1] = r.bottom - in;
                return true;
        }
    }

    /** (x, y) 가까이 있는 손잡이 (가장 가까운 것). 없으면 null. */
    private Handle handleAt(float x, float y) {
        float[] p = new float[2];
        float reach = Ui.dp(getContext(), 22), half = Ui.dp(getContext(), 15);
        Handle best = null;
        float bestDist = Float.MAX_VALUE;
        for (Handle h : Handle.values()) {
            if (!handleCenter(h, p)) continue;
            float dx = Math.abs(x - p[0]), dy = Math.abs(y - p[1]);
            if (h.vertical) dx = Math.max(0, dx - half);
            else dy = Math.max(0, dy - half);
            float d = (float) Math.hypot(dx, dy);
            if (d <= reach && d < bestDist) {
                bestDist = d;
                best = h;
            }
        }
        return best;
    }

    private int valueOf(Handle h) {
        switch (h) {
            case WIDTH: return prefs.oneHandWidth();
            case HEIGHT: return prefs.oneHandHeight();
            default: return prefs.oneHandLiftDp();
        }
    }

    private static int clamp(int v, int min, int max) {
        return Math.max(min, Math.min(max, v));
    }

    /** 끌기 시작할 때의 값과, 지금 저장된 값. */
    private int dragStart, dragValue;

    /** 처음 누른 곳에서 손가락이 움직인 거리(화면 px)로 새 값을 정한다. 바뀌었으면 저장하고 다시 그린다. */
    private void dragTo(Handle h, float dx, float dy) {
        float density = getResources().getDisplayMetrics().density;
        int v;
        switch (h) {
            case WIDTH: {
                float pct = dragStart + dx / Math.max(1, getWidth()) * 100f;
                v = clamp(Math.round(pct / 5f) * 5, WIDTH_MIN, WIDTH_MAX);
                break;
            }
            case HEIGHT: {
                // 키 높이 1%가 자판 높이를 얼마나 바꾸는지로 나눈다 (위로 끌면 커진다).
                float perPct = keyUnit() * KeyboardLayout.rowUnits(prefs.oneHandView()) / 100f;
                float pct = dragStart - dy / Math.max(0.01f, perPct);
                v = clamp(Math.round(pct / 5f) * 5, HEIGHT_MIN, HEIGHT_MAX);
                break;
            }
            default: {
                float dp = dragStart - dy / density;
                v = clamp(Math.round(dp / 4f) * 4, LIFT_MIN, LIFT_MAX);
                break;
            }
        }
        if (v == dragValue) return;
        dragValue = v;
        prefs.raw().edit().putInt(h.key, v).apply();
        refresh();
        overlay.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
        listener.onSizeChanged(h.key);
    }

    private final class Overlay extends View {
        private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint label = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF tmp = new RectF();
        private final float[] point = new float[2];
        private final int slop;
        private final java.util.List<Rect> exclusion = new java.util.ArrayList<>();
        private Handle active;
        private boolean dragging, moved;
        private float downRawX, downRawY;

        Overlay(Context c) {
            super(c);
            slop = ViewConfiguration.get(c).getScaledTouchSlop();
            stroke.setStyle(Paint.Style.STROKE);
            label.setTextAlign(Paint.Align.CENTER);
            label.setTextSize(Ui.dp(c, 14));
            label.setFakeBoldText(true);
            setContentDescription("한 손 모드 미리보기. 자판 오른쪽 가장자리의 손잡이를 좌우로 끌면 자판 폭이, "
                    + "위 가장자리의 손잡이를 위·아래로 끌면 키 높이가, 아래 가장자리의 손잡이를 위·아래로 끌면 세로 위치가 바뀝니다. "
                    + "아래 목록의 슬라이더로도 바꿀 수 있습니다.");
        }

        @Override
        protected void onDraw(Canvas c) {
            Context ctx = getContext();
            // 자판의 윤곽선: 손잡이가 어느 변을 바꾸는지 보이게.
            outlineBounds(tmp);
            stroke.setStrokeWidth(Ui.dp(ctx, 1.5f));
            stroke.setColor(theme.accent);
            stroke.setAlpha(dragging ? 200 : 110);
            float rr = Ui.dp(ctx, 10);
            c.drawRoundRect(tmp, rr, rr, stroke);
            stroke.setAlpha(255);
            // 손잡이: 강조색 알약에 바탕색 테두리 (키 위에서도 보이게). 끄는 중인 손잡이는 더 크게.
            for (Handle h : Handle.values()) {
                if (!handleCenter(h, point)) continue;
                boolean on = dragging && h == active;
                float lenHalf = Ui.dp(ctx, on ? 16 : 11), thickHalf = Ui.dp(ctx, on ? 3.5f : 2.5f);
                if (h.vertical) tmp.set(point[0] - lenHalf, point[1] - thickHalf, point[0] + lenHalf, point[1] + thickHalf);
                else tmp.set(point[0] - thickHalf, point[1] - lenHalf, point[0] + thickHalf, point[1] + lenHalf);
                stroke.setStrokeWidth(Ui.dp(ctx, 2));
                stroke.setColor(theme.background);
                c.drawRoundRect(tmp, thickHalf, thickHalf, stroke);
                fill.setColor(theme.accent);
                c.drawRoundRect(tmp, thickHalf, thickHalf, fill);
            }
            // 끄는 동안: 무엇을 얼마로 바꾸는지 자판 가운데에 크게 보여 준다.
            if (dragging && active != null) {
                String text = active.label + "  " + dragValue + active.unit;
                float tw = label.measureText(text);
                outlineBounds(tmp);
                float cx = tmp.centerX(), cy = tmp.centerY();
                float padH = Ui.dp(ctx, 14), padV = Ui.dp(ctx, 9);
                Paint.FontMetrics fm = label.getFontMetrics();
                float th = fm.descent - fm.ascent;
                float halfW = Math.min(tw / 2 + padH, getWidth() / 2f);
                cx = Math.max(halfW, Math.min(getWidth() - halfW, cx));
                tmp.set(cx - halfW, cy - th / 2 - padV, cx + halfW, cy + th / 2 + padV);
                fill.setColor(theme.accent);
                c.drawRoundRect(tmp, tmp.height() / 2, tmp.height() / 2, fill);
                label.setColor(theme.onAccent);
                c.drawText(text, cx, cy - (fm.ascent + fm.descent) / 2, label);
            }
        }

        @Override
        protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
            super.onLayout(changed, left, top, right, bottom);
            updateExclusion();
        }

        /** 자판 폭 손잡이를 끌 때 시스템 '뒤로 가기' 몸짓이 가로채지 않게 한다 (Android 10+, 폭이 커서 화면 끝에 가까울 때). */
        void updateExclusion() {
            if (android.os.Build.VERSION.SDK_INT < 29) return;
            exclusion.clear();
            if (handleCenter(Handle.WIDTH, point)) {
                int half = Ui.dp(getContext(), 36), wide = Ui.dp(getContext(), 32);
                int x = Math.round(point[0]), y = Math.round(point[1]);
                exclusion.add(new Rect(x - wide, y - half, x + wide, y + half));
            }
            setSystemGestureExclusionRects(exclusion);
        }

        @Override
        public boolean onTouchEvent(MotionEvent e) {
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    active = handleAt(e.getX(), e.getY());
                    if (active == null) return false;   // 손잡이 밖: 아무것도 하지 않는다
                    getParent().requestDisallowInterceptTouchEvent(true);
                    downRawX = e.getRawX();
                    downRawY = e.getRawY();
                    dragging = false;
                    moved = false;
                    return true;
                case MotionEvent.ACTION_MOVE: {
                    float dx = e.getRawX() - downRawX, dy = e.getRawY() - downRawY;
                    if (!dragging && !moved && Math.hypot(dx, dy) > slop) {
                        moved = true;
                        // 손잡이를 그 방향으로 끌기 시작했을 때만 값을 바꾼다.
                        if (active.vertical ? Math.abs(dy) >= Math.abs(dx) : Math.abs(dx) >= Math.abs(dy)) {
                            dragging = true;
                            dragStart = valueOf(active);
                            dragValue = dragStart;
                            // 처음 누른 곳부터 잰다: 손잡이가 손가락을 그대로 따라온다 (값은 5%·4dp 단위로 끊으므로
                            // 움직임 문턱을 넘는 순간에 튀지 않는다).
                            performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
                            invalidate();
                        }
                    }
                    // 화면 좌표로 잰다: 자판이 커지거나 떠오르면 이 뷰도 위로 자라 뷰 안의 좌표가 바뀌기 때문이다.
                    if (dragging) dragTo(active, dx, dy);
                    return true;
                }
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    dragging = false;
                    active = null;
                    invalidate();
                    updateExclusion();
                    return true;
                default:
                    return true;
            }
        }
    }
}
