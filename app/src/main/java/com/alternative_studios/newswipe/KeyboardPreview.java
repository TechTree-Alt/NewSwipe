package com.alternative_studios.newswipe;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

import com.alternative_studios.newswipe.keyboard.Key;
import com.alternative_studios.newswipe.keyboard.KeyboardLayout;
import com.alternative_studios.newswipe.keyboard.KeyboardTheme;
import com.alternative_studios.newswipe.keyboard.KeyboardView;
import com.alternative_studios.newswipe.keyboard.ToolbarButtons;
import com.alternative_studios.newswipe.ui.IconButton;
import com.alternative_studios.newswipe.ui.Ui;

/**
 * '키보드 사용자화' 화면 아래에 붙는, 실제 키보드와 같은 크기·모양의 미리보기 (도구 막대 + 자판).
 * <ul>
 *   <li>손잡이를 끌어 크기를 바로 바꾼다: 도구 막대·숫자 줄·글자 줄 맨 위 가장자리 = 높이, 자판 양옆·아래 가장자리 = 여백,
 *       고른 키의 오른쪽·아래 가장자리 = 키 좌우·상하 여백. 실제 키보드처럼 화면 아래에 붙어 있어, 위 가장자리를 위로 끌면
 *       키보드가 위로 자란다.</li>
 *   <li>키·도구 막대 버튼·도구 막대를 누르면 고른 것으로 알린다 (설정 화면이 그 아래 동작을 편집하는 칸을 연다).</li>
 * </ul>
 * 손잡이 위를 눌렀다가 움직이지 않고 떼면 그 자리의 키를 누른 것으로 본다 (가장자리 키도 그대로 고를 수 있다).
 * 값은 바뀔 때마다 바로 저장하고, 그리기는 모두 실제 키보드 뷰(KeyboardView)가 한다.
 */
final class KeyboardPreview extends FrameLayout {

    /** 미리보기에 보일 자판. */
    static final int TAB_KOREAN = 0, TAB_ENGLISH = 1, TAB_SYMBOLS = 2;

    /** 고른 것의 종류. */
    static final int SEL_KEY = 0, SEL_TOOL_BUTTON = 1, SEL_TOOLBAR = 2, SEL_AREA = 3, SEL_BOARD = 4;

    /**
     * 윤곽선 층 (애플워치 페이스 편집처럼 좌우로 밀어 넘긴다): 영역 층은 도구 막대·숫자 줄·글자 키·기능키를 크게 감싸
     * 그 영역 전체의 설정을 고르고, 키 층은 키 하나하나를 감싸 그 키의 동작을 고른다.
     */
    /** 윤곽선 층 (왼쪽부터): 키보드 전체 · 영역 · 키 하나. */
    static final int LAYER_BOARD = 0, LAYER_AREA = 1, LAYER_KEY = 2, LAYERS = 3;
    /** 영역 층의 영역. */
    static final int AREA_TOOLBAR = 0, AREA_NUMBER = 1, AREA_LETTERS = 2, AREA_FUNCTION = 3, AREAS = 4;
    static final String[] AREA_NAMES = {"도구 막대", "숫자 줄", "글자 키", "기능키"};
    /** 자판 탭 이름 (TAB_* 차례). */
    static final String[] TAB_NAMES = {"한글", "영어", "기호"};

    /** 영역 이름. 글자 키는 자판마다 따로라 탭 이름을 붙인다 (예: 영어 글자 키). */
    static String areaName(int area, int tab) {
        return area == AREA_LETTERS ? TAB_NAMES[tab] + " " + AREA_NAMES[area] : AREA_NAMES[area];
    }

    /** 고른 것. 자판을 다시 만들어도 같은 키를 다시 찾을 수 있게 키 객체 대신 종류·자리 이름·몇 번째인지로 기억한다. */
    static final class Selection {
        final int kind;
        /** SEL_KEY: 키 종류(Key.*)와 설정에 쓰는 자리 이름, 같은 키가 여럿일 때 몇 번째인지. */
        final int keyType;
        final String slot;
        final int occurrence;
        /** SEL_TOOL_BUTTON: 도구 막대 버튼 ID. */
        final String toolId;
        /** 고른 자판 (SEL_KEY). */
        final int tab;

        private Selection(int kind, int keyType, String slot, int occurrence, String toolId, int tab) {
            this.kind = kind;
            this.keyType = keyType;
            this.slot = slot;
            this.occurrence = occurrence;
            this.toolId = toolId;
            this.tab = tab;
        }

        /** 키보드 전체 (키보드 층). */
        static Selection board() {
            return new Selection(SEL_BOARD, -1, null, 0, null, -1);
        }

        static Selection toolbar() {
            return new Selection(SEL_TOOLBAR, -1, null, 0, null, -1);
        }

        static Selection tool(String id) {
            return new Selection(SEL_TOOL_BUTTON, -1, null, 0, id, -1);
        }

        /** 키 하나 (같은 키가 여럿이면 첫 번째). */
        static Selection key(Key k, int tab) {
            return new Selection(SEL_KEY, k.type, k.slot(), 0, null, tab);
        }

        static Selection area(int id) {
            return new Selection(SEL_AREA, id, null, 0, null, -1);
        }

        /** SEL_AREA의 영역 (AREA_*). */
        int area() {
            return keyType;
        }

        boolean same(Selection o) {
            return o != null && kind == o.kind && keyType == o.keyType && occurrence == o.occurrence && tab == o.tab
                    && java.util.Objects.equals(slot, o.slot) && java.util.Objects.equals(toolId, o.toolId);
        }
    }

    interface Listener {
        /** 키·버튼·도구 막대를 눌렀다 (빈 곳이면 null). */
        void onTap(Selection s);

        /** 손잡이를 끌어 크기 값(prefKey)을 바꾸고 저장했다. */
        void onSizeChanged(String prefKey);

        /** 윤곽선 층이 바뀌었다 (LAYER_*). */
        void onLayerChanged(int layer);
    }

    // ---------------------------------------------------------------- 손잡이

    /**
     * 미리보기의 손잡이: 영역 높이(↕ 버튼: 도구 막대·숫자 줄·글자 키·기능키), 키보드 전체 여백(키보드 층),
     * 고른 영역의 여백(영역 층). 손잡이마다 끄는 방향·이름·저장 키를 한곳에 둔다.
     */
    enum Handle {
        TOOLBAR(true, "도구 막대 높이", Prefs.TOOLBAR_HEIGHT, -1),
        NUMBER_ROW(true, "숫자 줄 높이", Prefs.NUMBER_ROW_HEIGHT, -1),
        HEIGHT(true, "글자 키 높이", Prefs.KEYBOARD_HEIGHT, -1),
        PAD_LEFT(false, "왼쪽 여백", Prefs.PAD_LEFT, -1),
        PAD_RIGHT(false, "오른쪽 여백", Prefs.PAD_RIGHT, -1),
        PAD_BOTTOM(true, "아래쪽 여백", Prefs.PAD_BOTTOM, -1),
        PAD_TOP(true, "위쪽 여백", Prefs.PAD_TOP, -1),
        FN_ROW(true, "기능키 높이", Prefs.FN_ROW_HEIGHT, -1),
        // 영역 여백: 저장 키는 고른 영역마다 다르다 (key(area)).
        AREA_L(false, "왼쪽 여백", null, Prefs.SIDE_LEFT),
        AREA_T(true, "위쪽 여백", null, Prefs.SIDE_TOP),
        AREA_R(false, "오른쪽 여백", null, Prefs.SIDE_RIGHT),
        AREA_B(true, "아래쪽 여백", null, Prefs.SIDE_BOTTOM),
        // 균형 배열 사용자화 (setBalancedMode): 자음·모음 쪽 가운데의 ↔ 버튼 (좌우로 끌면 키 폭, 길게 누른 뒤 끌면 가로 위치)과
        // 자·모음 경계선의 손잡이. 저장 키는 한글 배열마다 다르다 (handleKey).
        B_CONSONANT(false, "자음 키 폭", null, -1),
        B_VOWEL(false, "모음 키 폭", null, -1),
        B_BOUNDARY(false, "자·모음 경계 위치", null, -1);

        /** 위아래로 끄는지 (아니면 좌우). */
        final boolean vertical;
        final String label;
        private final String key;
        /** 영역 여백 손잡이의 방향 (Prefs.SIDE_*). 영역 여백이 아니면 -1. */
        final int side;

        Handle(boolean vertical, String label, String key, int side) {
            this.vertical = vertical;
            this.label = label;
            this.key = key;
            this.side = side;
        }

        /** 동그란 버튼으로 그리는 손잡이: 영역 높이(↕)와 균형 배열의 자음·모음 쪽(↔). */
        boolean knob() {
            return this == TOOLBAR || this == NUMBER_ROW || this == HEIGHT || this == FN_ROW || this == B_CONSONANT
                    || this == B_VOWEL;
        }

        /** 균형 배열 사용자화의 손잡이인지. */
        boolean balanced() {
            return this == B_CONSONANT || this == B_VOWEL || this == B_BOUNDARY;
        }

        boolean areaPad() {
            return side >= 0;
        }

        /** 바꾸는 설정 키. 영역 여백은 고른 영역(area)의 그 방향 여백. */
        String key(int area) {
            return areaPad() ? Prefs.areaPadKey(area, side) : key;
        }

        /** 값 뒤에 붙는 단위. */
        String unit() {
            return knob() || this == B_BOUNDARY ? "%" : "dp";
        }
    }

    /** 손잡이 h가 바꾸는 설정 키. 균형 배열의 ↔ 버튼은 길게 누른 뒤 끌면 가로 위치를 바꾼다. */
    private String handleKey(Handle h) {
        switch (h) {
            case B_CONSONANT: return positionDrag ? prefs.balancedConsonantPosKey() : prefs.balancedConsonantWidthKey();
            case B_VOWEL: return positionDrag ? prefs.balancedVowelPosKey() : prefs.balancedVowelWidthKey();
            case B_BOUNDARY: return prefs.balancedBoundaryKey();
            default: return h.key(selection == null ? 0 : selection.area());
        }
    }

    /** 끄는 동안 보이는 이름. 영역 여백은 영역 이름을 붙인다. */
    private String handleName(Handle h) {
        if (h.areaPad() && selection != null) return areaName(selection.area(), tab) + " " + h.label;
        if (positionDrag && h == Handle.B_CONSONANT) return "자음 가로 위치";
        if (positionDrag && h == Handle.B_VOWEL) return "모음 가로 위치";
        return h.label;
    }

    // ---------------------------------------------------------------- 균형 배열 사용자화

    /** 균형 배열 사용자화 화면: 한글 글자 키의 자음·모음 쪽 윤곽선과 그 ↔ 버튼, 경계선 손잡이만 쓴다 (층·고르기 없음). */
    private boolean balancedMode;
    /** ↔ 버튼을 길게 누른 뒤 끄는 중: 키 폭 대신 가로 위치를 바꾼다. */
    private boolean positionDrag;

    /** 균형 배열 사용자화 화면으로 쓴다: 키·영역을 고르지 않고 넘기지도 않으며, 자음·모음 쪽 손잡이만 보인다. */
    void setBalancedMode(boolean on) {
        balancedMode = on;
        selection = null;
        selectedKey = null;
        overlay.setContentDescription(on ? "자·모음 균형 배열 미리보기. 자음·모음 쪽 가운데의 버튼을 좌우로 끌면 키 폭이, "
                + "길게 누른 뒤 끌면 가로 위치가 바뀝니다. 가운데 경계선의 손잡이를 끌면 자·모음 경계 위치가 바뀝니다." : null);
        refresh();
    }

    /** 글자 줄 하나의 왼쪽·오른쪽 끝 (미리보기 좌표, 줄 전체 10칸). 자판이 없으면 false. */
    private boolean letterRowSpan(float[] out) {
        KeyboardLayout l = keys.getLayout();
        if (l == null) return false;
        int r = numberRowShown ? 1 : 0;
        if (r >= l.rows.length || l.rows[r].keys.length == 0) return false;
        Key[] ks = l.rows[r].keys;
        out[0] = ks[0].rect.left;
        out[1] = ks[ks.length - 1].rect.right;
        return out[1] > out[0];
    }

    /** 자·모음 경계선의 가로 위치 (미리보기 좌표). */
    private float boundaryX() {
        float[] span = new float[2];
        if (!letterRowSpan(span)) return getWidth() / 2f;
        return span[0] + (span[1] - span[0]) * prefs.balancedBoundary() / 100f;
    }

    /** 균형 배열의 자음(consonant) 또는 모음 쪽 키들을 감싼 윤곽선 (Shift·⌫ 포함). 없으면 false. */
    boolean blockBounds(boolean consonant, RectF out) {
        KeyboardLayout l = keys.getLayout();
        if (l == null || !prefs.balancedLayout()) return false;
        Context c = getContext();
        float pad = Ui.dp(c, 3), bx = boundaryX();
        float gx = Ui.dp(c, prefs.keyGapXDp()) / 2f, gy = Ui.dp(c, prefs.keyGapYDp()) / 2f;
        int first = numberRowShown ? 1 : 0, last = l.rows.length - 2;
        boolean any = false;
        for (int r = first; r <= last; r++) {
            for (Key k : l.rows[r].keys) {
                if (k.type == Key.SPACER || k.type == Key.GAP || isHidden(k)) continue;
                if ((k.rect.centerX() < bx) != consonant) continue;
                float left = k.rect.left + gx, top = keys.getTop() + k.rect.top + gy;
                float right = k.rect.right - gx, bottom = keys.getTop() + k.rect.bottom - gy;
                if (!any) out.set(left, top, right, bottom);
                else out.union(left, top, right, bottom);
                any = true;
            }
        }
        if (!any) return false;
        out.inset(-pad, -pad);
        return true;
    }

    private final Prefs prefs;
    private final Listener listener;
    private final LinearLayout column;
    private final LinearLayout bar;
    private final KeyboardView keys;
    private final Overlay overlay;
    private KeyboardTheme theme;
    private int tab = TAB_KOREAN;
    private boolean symbolsPage2;
    private Selection selection;
    /** 고른 키 (자판을 다시 만들 때마다 다시 찾는다). */
    private Key selectedKey;
    /** 지금 자판에 숫자 줄이 있는지 (숫자 줄 손잡이를 둘지). */
    private boolean numberRowShown;
    /** 지금 윤곽선 층과, 그리는 위치 (0 = 키보드 층, 1 = 영역 층, 2 = 키 층; 넘기는 동안 그 사이). */
    private int layer = LAYER_AREA;
    private float layerPos = LAYER_AREA;
    private android.animation.ValueAnimator layerAnim;

    int layer() {
        return layer;
    }

    /** 윤곽선 층을 바꾼다. 고른 것은 풀린다. 넘기는 모습은 잠깐 미끄러지며 바뀐다. */
    void setLayer(int l) {
        if (layer != l) {
            layer = l;
            selection = null;
            selectedKey = null;
            listener.onLayerChanged(l);
            overlay.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
        }
        animateLayerTo(l);
    }

    private void animateLayerTo(int l) {
        if (layerAnim != null) layerAnim.cancel();
        layerAnim = android.animation.ValueAnimator.ofFloat(layerPos, l);
        layerAnim.setDuration(Math.max(80, Math.round(260 * Math.abs(layerPos - l))));
        layerAnim.setInterpolator(new android.view.animation.PathInterpolator(0.2f, 0f, 0f, 1f));   // M3 강조 감속
        layerAnim.addUpdateListener(a -> {
            layerPos = (float) a.getAnimatedValue();
            overlay.invalidate();
        });
        layerAnim.start();
        overlay.post(overlay::updateExclusion);
    }

    KeyboardPreview(Context context, Prefs prefs, Listener listener) {
        super(context);
        this.prefs = prefs;
        this.listener = listener;
        column = new LinearLayout(context);
        column.setOrientation(LinearLayout.VERTICAL);
        bar = new LinearLayout(context);
        bar.setGravity(android.view.Gravity.CENTER_VERTICAL);
        keys = new KeyboardView(context);
        keys.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        bar.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
        addView(column, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        overlay = new Overlay(context);
        addView(overlay, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        refresh();
    }

    /** 크기는 도구 막대 + 자판이 정한다. 위에 덮은 손잡이 층은 그 크기를 그대로 따른다 (덮개가 높이를 키우지 않게). */
    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        measureChild(column, widthMeasureSpec, MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
        int w = column.getMeasuredWidth(), h = column.getMeasuredHeight();
        overlay.measure(MeasureSpec.makeMeasureSpec(w, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(h, MeasureSpec.EXACTLY));
        setMeasuredDimension(w, h);
    }

    void setTab(int t) {
        if (tab == t) return;
        tab = t;
        symbolsPage2 = false;
        // 영역·키보드 전체를 고른 채면 다른 자판에서도 그대로 둔다 (그 자판에 없는 영역이면 refresh가 푼다). 키는 자판마다 달라 푼다.
        if (selection != null && selection.kind != SEL_AREA && selection.kind != SEL_BOARD) selection = null;
        refresh();
    }

    int tab() {
        return tab;
    }

    /** 고른 것을 바꾼다 (null = 고르지 않음). 자판에 없는 키면 고르지 않은 것으로 본다. */
    void select(Selection s) {
        selection = s;
        resolveSelection();
        overlay.invalidate();
        overlay.post(overlay::updateExclusion);   // 보이는 손잡이가 바뀌었다
    }

    Selection selection() {
        return selection;
    }

    /** 고른 키 (SEL_KEY일 때). 없으면 null. */
    Key selectedKey() {
        return selectedKey;
    }

    /** 지금 미리보기가 그린 자판. */
    KeyboardLayout layout() {
        return keys.getLayout();
    }

    /** 설정을 다시 읽어 모양·크기·자판을 맞춘다. 같은 값이면 뷰는 그대로라 자주 불러도 된다. */
    void refresh() {
        Context c = getContext();
        theme = KeyboardTheme.of(c);
        setBackgroundColor(theme.background);
        keys.setTheme(theme);
        keys.configure(prefs.longPressMs(), prefs.swipeThresholdDp(), false,
                prefs.keyTextSize());
        keys.setKeyShadow(prefs.keyShadow(), prefs.keyShadowStrength());
        keys.setKeyRadius(Ui.dp(c, prefs.keyRadiusDp()));
        keys.setGridColors(prefs.gridColors());
        keys.setPopupHints(!prefs.popupHintHidden());
        keys.setNumberHintHidden(prefs.numberHintHidden());
        keys.setRepeatChars(prefs.repeatChars());
        keys.setKeyGaps(Ui.dp(c, prefs.keyGapXDp()), Ui.dp(c, prefs.keyGapYDp()));
        int padT = Ui.dp(c, prefs.padTopDp()), padB = Ui.dp(c, prefs.padBottomDp());
        // 키보드와 같다: 도구 막대를 자판 아래에 두면 자판의 아래쪽 여백은 도구 막대 밑으로 옮긴다 (전체 높이는 그대로).
        int lift = barLifted() ? padB : 0;
        // 위쪽 여백도 키보드 전체의 바깥 여백: 도구 막대가 자판 위에 있으면 도구 막대 위로 옮긴다 (키보드와 같다).
        int raise = barRaised() ? padT : 0;
        keys.setInsets(Ui.dp(c, prefs.padLeftDp()), padT - raise, Ui.dp(c, prefs.padRightDp()), padB - lift);
        float[][] areaPads = new float[AREAS][4];
        for (int a = 0; a < AREAS; a++) for (int side = 0; side < 4; side++) areaPads[a][side] = Ui.dp(c, prefs.areaPadDp(a, side));
        keys.setAreaInsets(areaPads);
        keys.setSpaceLabel(prefs.spaceLangLabel() ? (tab == TAB_ENGLISH ? "English" : tab == TAB_KOREAN ? "한국어" : null) : null);
        KeyboardLayout l;
        if (tab == TAB_ENGLISH) l = KeyboardLayout.english(prefs);
        else if (tab == TAB_SYMBOLS) l = symbolsPage2 ? KeyboardLayout.symbols2(true, prefs) : KeyboardLayout.symbols(true, prefs);
        else l = KeyboardLayout.korean(prefs);
        numberRowShown = tab != TAB_SYMBOLS && prefs.numberRow() && l.rows.length > 0 && l.rows[0].keys.length > 0
                && KeyboardLayout.isDigit(l.rows[0].keys[0].label);
        // 키보드와 같은 높이: 글자 4줄 + 숫자 줄 (기호 자판도 같은 높이를 쓴다).
        float rows = KeyboardLayout.rowUnits(prefs);
        keys.setKeyboardHeight(Math.round(Ui.dp(c, 54) * rows * prefs.keyboardHeight() / 100f) + padT + padB - lift - raise
                + Ui.dp(c, prefs.areaPadVerticalDp()));
        keys.setLayout(l);
        rebuildToolbar(lift, raise);
        resolveSelection();
        overlay.invalidate();
        overlay.post(overlay::updateExclusion);   // 여백이 바뀌면 손잡이 자리도 바뀐다 (배치가 끝난 뒤에 잰다)
    }

    /** 도구 막대와 자판의 배치를 마지막으로 만든 설정 (같으면 뷰를 다시 만들지 않는다: 손잡이를 끄는 동안 자주 불린다). */
    private String barSignature, columnSignature;

    /**
     * 도구 막대 줄이 보이는지. '도구 막대 표시'를 꺼도 단어 추천·단축어를 쓰면 키보드는 그 줄(버튼 없이)을 남겨 둔다.
     * 일본어·중국어 자판(실험실)을 켜도 변환 후보 자리로 남지만, 미리보기는 한글·영어·기호 자판이라 따지지 않는다.
     */
    boolean barShown() {
        return prefs.toolbar() || prefs.suggestWords()
                || (prefs.shortcutsEnabled() && !com.alternative_studios.newswipe.suggest.Shortcuts.of(prefs.shortcuts()).isEmpty());
    }

    /** 도구 막대를 자판 아래에 두어 아래쪽 여백이 도구 막대 밑으로 옮겨졌는지. */
    /** 도구 막대 줄이 자판 위에 있어 위쪽 여백을 그 위로 옮기는지. */
    private boolean barRaised() {
        return barShown() && !prefs.toolbarBottom();
    }

    private boolean barLifted() {
        return barShown() && prefs.toolbarBottom();
    }

    /** 도구 막대 줄이 아예 없을 때 그 자리를 알리는 띠 (실제 키보드에는 없다): 눌러서 도구 막대 설정을 연다. */
    private android.widget.TextView placeholder;

    private android.widget.TextView placeholder() {
        if (placeholder != null) return placeholder;
        Context c = getContext();
        AppTheme app = AppTheme.of(c);
        placeholder = new android.widget.TextView(c);
        // 안내 글자는 윤곽선 층 위에 그린다 (영역 층에서는 이름표가 대신 알린다: drawOffHints).
        android.graphics.drawable.GradientDrawable d = new android.graphics.drawable.GradientDrawable();
        d.setColor(app.bg);
        d.setStroke(Ui.dp(c, 1), app.hint, Ui.dp(c, 4), Ui.dp(c, 3));
        d.setCornerRadius(Ui.dp(c, 8));
        placeholder.setBackground(new android.graphics.drawable.InsetDrawable(d, Ui.dp(c, 8), Ui.dp(c, 3), Ui.dp(c, 8), Ui.dp(c, 3)));
        return placeholder;
    }

    /** 도구 막대의 왼쪽·오른쪽 여백 (dp): 키보드 여백 + 도구 막대 영역 여백. */
    private int barPadLeft() {
        return prefs.padLeftDp() + prefs.areaPadDp(AREA_TOOLBAR, Prefs.SIDE_LEFT);
    }

    private int barPadRight() {
        return prefs.padRightDp() + prefs.areaPadDp(AREA_TOOLBAR, Prefs.SIDE_RIGHT);
    }

    /** 도구 막대: 실제와 같은 자리·순서·크기의 버튼들 (누를 수 없고, 누르면 그 버튼을 고른다). */
    private void rebuildToolbar(int lift, int raise) {
        Context c = getContext();
        int height = Math.round(Ui.dp(c, 40) * prefs.toolbarHeight() / 100f);
        float scale = prefs.toolButtonSize() / 100f;
        int width = Math.round(Ui.dp(c, 40) * scale);
        String[][] order = prefs.toolOrder();
        StringBuilder sig = new StringBuilder().append(height).append(',').append(width).append(',')
                .append(barPadLeft()).append(',').append(barPadRight()).append(',')
                .append(theme.background).append(',').append(theme.functionKey).append(',').append(theme.text).append('|');
        boolean buttons = prefs.toolbar();   // 꺼 두면 버튼 없이 추천 단어 자리만 남는다
        sig.append(buttons).append('|');
        for (String[] side : order) {
            for (String id : side) if (prefs.toolShown(id)) sig.append(id).append(',');
            sig.append('|');
        }
        if (!sig.toString().equals(barSignature)) {
            barSignature = sig.toString();
            bar.removeAllViews();
            bar.setPadding(Ui.dp(c, barPadLeft()), 0, Ui.dp(c, barPadRight()), 0);
            for (int side = 0; side < 2 && buttons; side++) {
                if (side == 1) bar.addView(new View(c), new LinearLayout.LayoutParams(0, 1, 1f));
                for (String id : order[side]) {
                    if (!prefs.toolShown(id)) continue;
                    IconButton b = new IconButton(c, ToolbarButtons.icon(id), theme.text, theme.keyPressed, null);
                    b.setIconSize(Ui.dp(c, 22) * scale);
                    b.setToolbarStyle(theme, height);
                    b.setClickable(false);
                    b.setFocusable(false);
                    b.setTag(id);
                    bar.addView(b, new LinearLayout.LayoutParams(width, height));
                }
            }
        }
        boolean shown = barShown();
        boolean bottom = prefs.toolbarBottom();
        int barT = Ui.dp(c, prefs.areaPadDp(AREA_TOOLBAR, Prefs.SIDE_TOP));
        int barB = Ui.dp(c, prefs.areaPadDp(AREA_TOOLBAR, Prefs.SIDE_BOTTOM));
        String colSig = shown + "," + bottom + "," + height + "," + lift + "," + raise + "," + barT + "," + barB;
        if (colSig.equals(columnSignature)) return;
        columnSignature = colSig;
        column.removeAllViews();
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, height);
        // 도구 막대 영역의 위·아래 여백은 막대 바로 둘레에, 키보드 전체 여백은 그 바깥에 온다.
        blp.bottomMargin = lift + barB;   // 자판 아래에 두면 아래쪽 여백이 도구 막대 밑에 온다
        blp.topMargin = raise + barT;     // 자판 위에 두면 위쪽 여백이 도구 막대 위에 온다
        LinearLayout.LayoutParams klp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        if (!shown) {
            column.addView(placeholder(), new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(c, 30)));
        } else if (!bottom) {
            column.addView(bar, blp);
        }
        column.addView(keys, klp);
        if (shown && bottom) column.addView(bar, blp);
    }

    /** 고른 키를 지금 자판에서 다시 찾는다. 없으면 고르지 않은 것으로 돌린다. */
    private void resolveSelection() {
        selectedKey = null;
        if (selection == null) return;
        if (selection.kind == SEL_BOARD) return;
        if (selection.kind == SEL_AREA) {
            // 숫자 줄을 껐다 등. 숫자 줄 영역은 꺼져 있어도 고른 채로 둔다 (그 창에서 다시 켤 수 있게).
            if (!areaBounds(selection.area(), new RectF()) && selection.area() != AREA_NUMBER) selection = null;
            return;
        }
        if (selection.kind == SEL_TOOL_BUTTON) {
            if (!prefs.toolbar() || !prefs.toolShown(selection.toolId)) selection = null;
            return;
        }
        KeyboardLayout l = keys.getLayout();
        if (selection.tab != tab || l == null) {
            selection = null;
            return;
        }
        Key first = null;
        int seen = 0;
        for (KeyboardLayout.Row row : l.rows) {
            for (Key k : row.keys) {
                if (k.type != selection.keyType || !k.slot().equals(selection.slot)) continue;
                if (first == null) first = k;
                if (seen++ == selection.occurrence) {
                    selectedKey = k;
                    return;
                }
            }
        }
        selectedKey = first;
        if (first == null) selection = null;
    }

    /** 키를 고를 수 있는지 (빈 자리·빈틈·숨은 키·기호 쪽 넘김 키는 고르지 않는다). */
    private static boolean selectable(Key k) {
        return k.type != Key.SPACER && k.type != Key.GAP && k.type != Key.SYMBOL_PAGE && !isHidden(k);
    }

    /** 분리 키보드의 빈 자리에 숨겨 둔 키(g, v)는 그리지 않으므로 고르지 않는다. */
    private static boolean isHidden(Key k) {
        return k.isHidden() || k.rect.width() <= 0;
    }

    /** 키가 자판에서 몇 번째 같은 키인지 (분리 키보드의 두 스페이스바 등). */
    private int occurrenceOf(Key key) {
        int seen = 0;
        for (KeyboardLayout.Row row : keys.getLayout().rows) {
            for (Key k : row.keys) {
                if (k == key) return seen;
                if (k.type == key.type && k.slot().equals(key.slot())) seen++;
            }
        }
        return 0;
    }

    // ---------------------------------------------------------------- 눌러 고르기

    private void tapAt(float x, float y) {
        if (balancedMode) return;   // 균형 배열 사용자화: 고를 것이 없다
        if (layer == LAYER_BOARD) {   // 키보드 층: 키보드 전체 하나
            RectF r = new RectF();
            boolean inside = boardBounds(r) && y >= r.top - Ui.dp(getContext(), 12) && y <= r.bottom + Ui.dp(getContext(), 12);
            listener.onTap(inside ? Selection.board() : null);
            return;
        }
        if (layer == LAYER_AREA) {
            // 영역 층: 누른 곳을 감싼 영역 (경계 근처면 가까운 영역).
            RectF r = new RectF();
            int best = -1;
            float bestDist = Ui.dp(getContext(), 12);
            for (int a = 0; a < AREAS; a++) {
                if (!areaBounds(a, r)) continue;
                float dx = x < r.left ? r.left - x : x > r.right ? x - r.right : 0;
                float dy = y < r.top ? r.top - y : y > r.bottom ? y - r.bottom : 0;
                float d = (float) Math.hypot(dx, dy);
                if (d < bestDist || (d == 0 && bestDist > 0)) {
                    bestDist = d;
                    best = a;
                }
            }
            listener.onTap(best < 0 ? null : Selection.area(best));
            return;
        }
        // 키 페이지에서는 도구 막대 전체를 고르지 않는다 (도구 막대는 영역 페이지에서, 버튼은 하나씩 고른다).
        if (placeholder != null && placeholder.getParent() != null && y >= placeholder.getTop() && y < placeholder.getBottom()) {
            listener.onTap(null);
            return;
        }
        if (bar.getParent() != null && y >= bar.getTop() && y < bar.getBottom()) {
            for (int i = 0; i < bar.getChildCount(); i++) {
                View b = bar.getChildAt(i);
                if (b.getTag() instanceof String && x >= b.getLeft() && x < b.getRight()) {
                    listener.onTap(Selection.tool((String) b.getTag()));
                    return;
                }
            }
            listener.onTap(null);
            return;
        }
        Key k = keyAt(x, y - keys.getTop());
        if (k == null) {
            listener.onTap(null);
            return;
        }
        if (k.type == Key.SYMBOL_PAGE) {   // 기호 자판의 1/2쪽 넘김: 다른 쪽을 보여 준다
            symbolsPage2 = !symbolsPage2;
            selection = null;
            refresh();
            listener.onTap(null);
            return;
        }
        listener.onTap(new Selection(SEL_KEY, k.type, k.slot(), occurrenceOf(k), null, tab));
    }

    /** 자판 좌표(y는 자판 뷰 기준)의 키. 키 사이 빈틈·여백이면 가장 가까운 키 (손가락 하나 폭 안에 있을 때만). */
    private Key keyAt(float x, float y) {
        KeyboardLayout l = keys.getLayout();
        if (l == null) return null;
        Key best = null;
        float bestDist = Ui.dp(getContext(), 16);
        for (KeyboardLayout.Row row : l.rows) {
            for (Key k : row.keys) {
                if (k.type == Key.SPACER || k.type == Key.GAP || isHidden(k)) continue;
                RectF r = k.rect;
                float dx = x < r.left ? r.left - x : x > r.right ? x - r.right : 0;
                float dy = y < r.top ? r.top - y : y > r.bottom ? y - r.bottom : 0;
                float d = (float) Math.hypot(dx, dy);
                if (d == 0) return k;
                if (d < bestDist) {
                    bestDist = d;
                    best = k;
                }
            }
        }
        return best;
    }

    // ---------------------------------------------------------------- 끌어서 크기 바꾸기

    /**
     * 영역의 테두리 (미리보기 좌표, 그린 키를 감싼 크기). 지금 자판에 그 영역이 없으면 false.
     * 글자 키는 숫자 줄과 맨 아래 줄을 뺀 줄들 (Shift·⌫ 포함), 기능키는 맨 아래 줄이다.
     */
    /** 키보드 전체(도구 막대·자판)를 감싼 테두리 자리. */
    boolean boardBounds(RectF out) {
        if (column.getChildCount() == 0) return false;
        // 좌우는 키(와 영역 여백)를 따라가는 안쪽 가장자리, 위아래는 키보드 전체 여백의 바깥 가장자리.
        // 어느 쪽이든 그 여백을 늘리면 손가락을 따라 움직이는 변이다.
        Context c = getContext();
        float pad = Ui.dp(c, 2);
        float left = Float.MAX_VALUE, right = -Float.MAX_VALUE;
        RectF r = new RectF();
        for (int a = 0; a < AREAS; a++) {
            if (!areaBounds(a, r)) continue;
            left = Math.min(left, r.left - Ui.dp(c, prefs.areaPadDp(a, Prefs.SIDE_LEFT)));
            right = Math.max(right, r.right + Ui.dp(c, prefs.areaPadDp(a, Prefs.SIDE_RIGHT)));
        }
        if (left > right) {
            left = pad;
            right = getWidth() - pad;
        }
        out.set(Math.max(left, pad), column.getTop() + pad, Math.min(right, getWidth() - pad), column.getBottom() - pad);
        return out.height() > 0;
    }

    /** 영역 층의 영역 윤곽선: 영역에 그 영역의 위·아래 여백을 더한 자리 (여백의 바깥 가장자리). */
    boolean padBounds(int area, RectF out) {
        if (!areaBounds(area, out)) return false;
        Context c = getContext();
        out.top -= Ui.dp(c, prefs.areaPadDp(area, Prefs.SIDE_TOP));
        out.bottom += Ui.dp(c, prefs.areaPadDp(area, Prefs.SIDE_BOTTOM));
        return true;
    }

    boolean areaBounds(int area, RectF out) {
        Context c = getContext();
        float pad = Ui.dp(c, 3);
        if (area == AREA_TOOLBAR) {
            View v = bar.getParent() != null ? bar : placeholder != null && placeholder.getParent() != null ? placeholder : null;
            if (v == null) return false;
            // 도구 막대의 좌우는 버튼 자리(막대의 좌우 여백 안쪽)를 따라간다: 자판의 키 윤곽선과 같은 선.
            // 키 윤곽선은 키 모양 가장자리(자리에서 키 좌우 간격의 절반 안쪽)에서 pad만큼 나온다: 도구 막대도 같은 규칙.
            float gx = Ui.dp(c, prefs.keyGapXDp()) / 2f;
            float inL = v == bar ? Math.max(pad, bar.getPaddingLeft() + gx - pad) : pad;
            float inR = v == bar ? Math.max(pad, bar.getPaddingRight() + gx - pad) : pad;
            // 위·아래는 키 영역과 같은 규칙: 줄 가장자리에서 키 상하 간격의 절반만큼 들어갔다가 윤곽선 여유(pad)만큼 나온다.
            // 그래서 간격이 0이면 도구 막대와 숫자 줄 윤곽선이 다른 영역들처럼 빈틈없이 겹친다.
            float gy = v == bar ? Ui.dp(c, prefs.keyGapYDp()) / 2f : 0f;
            float vPad = v == bar ? -pad : pad;
            out.set(v.getLeft() + inL, v.getTop() + gy + vPad, v.getRight() - inR, v.getBottom() - gy - vPad);
            return true;
        }
        KeyboardLayout l = keys.getLayout();
        if (l == null || l.rows.length == 0) return false;
        int first, last;
        int letterStart = numberRowShown ? 1 : 0;
        switch (area) {
            case AREA_NUMBER:
                if (!numberRowShown) return false;
                first = last = 0;
                break;
            case AREA_LETTERS:
                first = letterStart;
                last = l.rows.length - 2;
                break;
            default:
                first = last = l.rows.length - 1;
                break;
        }
        if (last < first) return false;
        float gx = Ui.dp(c, prefs.keyGapXDp()) / 2f, gy = Ui.dp(c, prefs.keyGapYDp()) / 2f;
        boolean any = false;
        for (int r = first; r <= last; r++) {
            for (Key k : l.rows[r].keys) {
                if (k.type == Key.SPACER || k.type == Key.GAP || isHidden(k)) continue;
                float left = k.rect.left + gx, top = keys.getTop() + k.rect.top + gy;
                float right = k.rect.right - gx, bottom = keys.getTop() + k.rect.bottom - gy;
                if (!any) out.set(left, top, right, bottom);
                else out.union(left, top, right, bottom);
                any = true;
            }
        }
        if (!any) return false;
        out.inset(-pad, -pad);
        return true;
    }

    /** 손잡이 h가 지금 보이는지. */
    private boolean handleShown(Handle h) {
        if (keys.getLayout() == null) return false;
        // 균형 배열 사용자화 화면에서는 그 손잡이만 (균형 배열이 켜져 있을 때).
        if (h.balanced()) return balancedMode && prefs.balancedLayout() && (h == Handle.B_BOUNDARY
                || blockBounds(h == Handle.B_CONSONANT, new RectF()));
        if (balancedMode) return false;
        // 키보드 층: 키보드 전체를 눌러 골랐을 때만 그 여백 손잡이 (위·아래·왼쪽·오른쪽).
        if (layer == LAYER_BOARD) return selection != null && selection.kind == SEL_BOARD
                && (h == Handle.PAD_TOP || h == Handle.PAD_BOTTOM || h == Handle.PAD_LEFT || h == Handle.PAD_RIGHT);
        if (selection == null) return false;
        // 영역을 고르면 그 영역의 상하좌우 여백 손잡이와 가운데의 높이 손잡이(↕ 버튼).
        if (selection.kind == SEL_AREA && h.areaPad()) {
            int a = selection.area();
            if (a == AREA_TOOLBAR) return bar.getParent() != null;
            return a != AREA_NUMBER || numberRowShown;
        }
        // 높이 버튼(↕): 고른 영역의 높이. 키를 골랐을 때는 손잡이가 없다 (키 간격은 '키 모양'의 슬라이더로).
        switch (selection.kind) {
            case SEL_AREA:
                switch (selection.area()) {
                    case AREA_TOOLBAR: return h == Handle.TOOLBAR && bar.getParent() != null;
                    case AREA_NUMBER: return h == Handle.NUMBER_ROW && numberRowShown;
                    case AREA_LETTERS: return h == Handle.HEIGHT;
                    default: return h == Handle.FN_ROW;
                }
            default: return false;
        }
    }

    /** 손잡이의 가운데 (미리보기 좌표). 보이지 않으면 false. */
    private boolean handleCenter(Handle h, float[] out) {
        if (!handleEdge(h, out)) return false;
        if (h.knob() || h.balanced()) return true;   // 버튼은 영역 안쪽에, 경계선 손잡이는 경계선 위에 있다
        // 경계선 위가 아니라 윤곽선 안쪽에 둔다: 화면 가장자리의 시스템 몸짓(뒤로 가기·홈)과 겹치지 않게.
        float in = Ui.dp(getContext(), 9);
        switch (h) {
            case PAD_LEFT:
            case AREA_L: out[0] += in; break;
            case PAD_RIGHT:
            case AREA_R: out[0] -= in; break;
            case PAD_BOTTOM:
            case AREA_B: out[1] -= in; break;
            default: out[1] += in; break;   // 위 가장자리의 손잡이 (높이·위쪽 여백)
        }
        return true;
    }

    /** 손잡이가 조절하는 경계선 위의 점 (미리보기 좌표). 보이지 않으면 false. */
    private boolean handleEdge(Handle h, float[] out) {
        if (!handleShown(h)) return false;
        Context c = getContext();
        int w = getWidth();
        switch (h) {
            case TOOLBAR:
            case NUMBER_ROW:
            case HEIGHT:
            case FN_ROW: {
                // 높이 버튼(↕): 영역 세로 가운데, 가운데보다 왼쪽 (가운데의 위·아래 여백 손잡이와 겹치지 않게).
                // 위·아래로 끌어 그 영역의 높이를 바꾼다.
                RectF r = new RectF();
                int a = h == Handle.TOOLBAR ? AREA_TOOLBAR : h == Handle.NUMBER_ROW ? AREA_NUMBER : h == Handle.FN_ROW ? AREA_FUNCTION : AREA_LETTERS;
                if (!areaBounds(a, r)) return false;
                // 글자 키 영역은 높아서 가운데에 둬도 위·아래 여백 손잡이와 겹치지 않는다.
                out[0] = h == Handle.HEIGHT ? r.centerX() : Math.max(r.left + Ui.dp(c, 30), r.centerX() - Ui.dp(c, 64));
                out[1] = r.centerY();
                return true;
            }
            case PAD_TOP:
            case PAD_LEFT:
            case PAD_RIGHT:
            case PAD_BOTTOM: {
                // 키보드 층 윤곽선의 각 변 가운데 (손잡이는 윤곽선과 늘 같은 간격을 둔다).
                RectF r = new RectF();
                if (!boardBounds(r)) return false;
                out[0] = h == Handle.PAD_LEFT ? r.left : h == Handle.PAD_RIGHT ? r.right : r.centerX();
                out[1] = h == Handle.PAD_TOP ? r.top : h == Handle.PAD_BOTTOM ? r.bottom : r.centerY();
                return true;
            }
            case B_CONSONANT:
            case B_VOWEL: {
                // ↔ 버튼: 그 쪽 윤곽선의 한가운데.
                RectF r = new RectF();
                if (!blockBounds(h == Handle.B_CONSONANT, r)) return false;
                out[0] = r.centerX();
                out[1] = r.centerY();
                return true;
            }
            case B_BOUNDARY: {
                // 경계선 손잡이: 경계선 위, 글자 키 영역의 세로 가운데.
                RectF r = new RectF();
                if (!areaBounds(AREA_LETTERS, r)) return false;
                out[0] = boundaryX();
                out[1] = r.centerY();
                return true;
            }
            case AREA_L:
            case AREA_T:
            case AREA_R:
            case AREA_B: {
                // 고른 영역 윤곽선(위·아래 여백 포함)의 각 변 가운데.
                RectF r = new RectF();
                if (!padBounds(selection.area(), r)) return false;
                out[0] = h == Handle.AREA_L ? Math.max(r.left, Ui.dp(c, 3)) : h == Handle.AREA_R ? Math.min(r.right, w - Ui.dp(c, 3))
                        : r.centerX();
                out[1] = h == Handle.AREA_T ? Math.max(r.top, 0) : h == Handle.AREA_B ? Math.min(r.bottom, getHeight() - Ui.dp(c, 3))
                        : r.centerY();
                return true;
            }
            default:
                return false;
        }
    }

    /** (x, y) 가까이 있는 손잡이 (가장 가까운 것). 없으면 -1. */
    /** 높이 버튼 옆의 안내: 누르는 순간부터 손가락을 떼고 잠깐까지 보인다 (그 버튼과 사라질 시각). */
    private Handle knobHint;
    private long knobHintUntil;

    /** 높이 버튼을 눌렀다: 떼기 전까지 안내를 보인다. */
    private void holdKnobHint(Handle h) {
        knobHint = h;
        knobHintUntil = Long.MAX_VALUE;
        overlay.invalidate();
    }

    /** 손가락을 뗐다: 안내는 잠깐 더 보였다가 사라진다. */
    private void releaseKnobHint() {
        if (knobHint == null || knobHintUntil != Long.MAX_VALUE) return;
        knobHintUntil = android.os.SystemClock.uptimeMillis() + 1200;
        overlay.invalidate();
        overlay.postDelayed(overlay::invalidate, 1250);
    }

    private Handle handleAt(float x, float y) {
        float[] p = new float[2];
        float reach = Ui.dp(getContext(), 22);
        Handle best = null;
        float bestDist = Float.MAX_VALUE;
        for (Handle h : Handle.values()) {
            if (!handleCenter(h, p)) continue;
            // 손잡이 모양(긴 쪽 28dp)을 따라 잰 거리.
            float half = Ui.dp(getContext(), 15);
            float dx = Math.abs(x - p[0]), dy = Math.abs(y - p[1]);
            if (h.knob()) {
                // 동그란 버튼: 가운데에서의 거리 (반지름만큼은 버튼 안).
                float d = Math.max(0, (float) Math.hypot(dx, dy) - Ui.dp(getContext(), 13));
                if (d <= reach && d < bestDist) {
                    bestDist = d;
                    best = h;
                }
                continue;
            }
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

    /** 손잡이를 끌기 시작할 때의 값 (px 또는 %, 손잡이마다 다르다). */
    private float dragStart;
    private int dragValue;

    private void beginDrag(Handle h) {
        Context c = getContext();
        switch (h) {
            case TOOLBAR: dragStart = bar.getHeight(); break;
            case NUMBER_ROW: dragStart = Ui.dp(c, 54) * prefs.keyboardHeight() / 100f * prefs.numberRowHeight() / 100f; break;
            case FN_ROW: dragStart = Ui.dp(c, 54) * prefs.keyboardHeight() / 100f * prefs.fnRowHeight() / 100f; break;
            case HEIGHT: dragStart = Ui.dp(c, 54) * rowUnits() * prefs.keyboardHeight() / 100f; break;
            default: dragStart = valueOf(h); break;
        }
        dragValue = valueOf(h);
    }

    private float rowUnits() {
        return KeyboardLayout.rowUnits(prefs);
    }

    private int valueOf(Handle h) {
        switch (h) {
            case B_CONSONANT: return positionDrag ? prefs.balancedConsonantPos() : prefs.balancedConsonantWidth();
            case B_VOWEL: return positionDrag ? prefs.balancedVowelPos() : prefs.balancedVowelWidth();
            case B_BOUNDARY: return prefs.balancedBoundary();
            case TOOLBAR: return prefs.toolbarHeight();
            case NUMBER_ROW: return prefs.numberRowHeight();
            case HEIGHT: return prefs.keyboardHeight();
            case FN_ROW: return prefs.fnRowHeight();
            case PAD_LEFT: return prefs.padLeftDp();
            case PAD_RIGHT: return prefs.padRightDp();
            case PAD_BOTTOM: return prefs.padBottomDp();
            case PAD_TOP: return prefs.padTopDp();
            default: return prefs.areaPadDp(selection.area(), h.side);   // Handle.AREA_*
        }
    }

    /** 높이 값(%)은 5% 단위로 움직인다. */
    private static int snap5(float v) {
        return Math.round(v / 5f) * 5;
    }

    private static int clamp(int v, int min, int max) {
        return Math.max(min, Math.min(max, v));
    }

    /** 처음 누른 곳에서 손가락이 움직인 거리(화면 기준 px)로 새 값을 정한다. 바뀌었으면 저장하고 다시 그린다. */
    private void dragTo(Handle h, float dx, float dy) {
        Context c = getContext();
        float density = getResources().getDisplayMetrics().density;
        int v;
        switch (h) {
            case TOOLBAR:
                v = clamp(snap5((dragStart - dy) / Ui.dp(c, 40) * 100f), 70, 150);
                break;
            case NUMBER_ROW:
                v = clamp(snap5((dragStart - dy) / (Ui.dp(c, 54) * prefs.keyboardHeight() / 100f) * 100f),
                        Prefs.NUMBER_ROW_HEIGHT_MIN, Prefs.NUMBER_ROW_HEIGHT_MAX);
                break;
            case FN_ROW:
                v = clamp(snap5((dragStart - dy) / (Ui.dp(c, 54) * prefs.keyboardHeight() / 100f) * 100f),
                        Prefs.FN_ROW_HEIGHT_MIN, Prefs.FN_ROW_HEIGHT_MAX);
                break;
            case HEIGHT:
                v = clamp(snap5((dragStart - dy) / (Ui.dp(c, 54) * rowUnits()) * 100f), 70, 140);
                break;
            case PAD_LEFT: v = clamp(Math.round(dragStart + dx / density), 0, 24); break;
            case PAD_RIGHT: v = clamp(Math.round(dragStart - dx / density), 0, 24); break;
            case PAD_BOTTOM: v = clamp(Math.round(dragStart - dy / density), 0, 48); break;
            case PAD_TOP: v = clamp(Math.round(dragStart - dy / density), 0, 24); break;
            case B_CONSONANT:
            case B_VOWEL: {
                // 자음 쪽은 오른쪽으로, 모음 쪽은 왼쪽으로 끌면 키 폭이 넓어진다 (경계에서 멀어지는 쪽;
                // 가운데 놓인 쪽은 양옆으로 함께 늘어 손가락 거리의 두 배).
                // 길게 누른 뒤면 가로 위치: 그 쪽 안의 남는 폭에서 손가락을 따라 옮긴다. 모두 5단위.
                float[] span = new float[2];
                if (!letterRowSpan(span)) return;
                boolean cons = h == Handle.B_CONSONANT;
                float row = span[1] - span[0], b = prefs.balancedBoundary() / 100f;
                float half = row * (cons ? b : 1f - b);
                if (positionDrag) {
                    int w = cons ? prefs.balancedConsonantWidth() : prefs.balancedVowelWidth();
                    float free = half * (1f - w / 100f);
                    if (free < 1f) return;   // 꽉 채운 폭이면 옮길 자리가 없다
                    v = clamp(snap5(dragStart + dx / free * 100f), 0, 100);
                } else {
                    int max = cons ? Prefs.BALANCED_CONSONANT_WIDTH_MAX : KeyboardLayout.balancedWidthMax(prefs);
                    // 모음 쪽은 자음 쪽과 반대로: 왼쪽으로 끌면 넓어지고 오른쪽으로 끌면 좁아진다.
                    v = clamp(snap5(dragStart + (cons ? 2f : -2f) * dx / half * 100f), prefs.balancedWidthMin(), max);
                }
                break;
            }
            case B_BOUNDARY: {
                float[] span = new float[2];
                if (!letterRowSpan(span)) return;
                v = clamp(snap5(dragStart + dx / (span[1] - span[0]) * 100f), Prefs.BALANCED_BOUNDARY_MIN,
                        Prefs.BALANCED_BOUNDARY_MAX);
                break;
            }
            case AREA_L: v = clamp(Math.round(dragStart + dx / density), 0, Prefs.AREA_PAD_MAX); break;
            case AREA_R: v = clamp(Math.round(dragStart - dx / density), 0, Prefs.AREA_PAD_MAX); break;
            // 위·아래는 키보드 전체 위·아래 여백과 같이: 끌어 올리면 늘어난다 (키보드는 위로 자란다).
            default: v = clamp(Math.round(dragStart - dy / density), 0, Prefs.AREA_PAD_MAX); break;   // Handle.AREA_T·Handle.AREA_B
        }
        if (v == dragValue) return;
        dragValue = v;
        String key = handleKey(h);
        prefs.raw().edit().putInt(key, v).apply();
        refresh();
        overlay.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
        listener.onSizeChanged(key);
    }

    private String valueText(Handle h, int v) {
        return v + (positionDrag && h.balanced() ? "" : h.unit());   // 가로 위치는 0~100 (단위 없음)
    }

    // ---------------------------------------------------------------- 덮개: 손잡이·고른 것 그리기와 터치

    private final class Overlay extends View {
        private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint label = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF tmp = new RectF();
        private final float[] point = new float[2];
        private final int slop;
        private Handle active;
        private boolean dragging, moved;
        /** 좌우로 밀어 윤곽선 층을 넘기는 중 (손가락을 따라 layerPos가 움직인다). */
        private boolean paging;
        private float pageStart;
        private float downX, downY, downRawX, downRawY;
        private final java.util.List<Rect> exclusion = new java.util.ArrayList<>();

        /** ↔ 버튼을 길게 눌렀다: 이제부터 끌면 가로 위치를 바꾼다 (진동으로 알리고 안내를 바꾼다). */
        private final Runnable positionPress = () -> {
            if (moved || dragging || (active != Handle.B_CONSONANT && active != Handle.B_VOWEL)) return;
            positionDrag = true;
            performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
            invalidate();
        };

        Overlay(Context c) {
            super(c);
            slop = ViewConfiguration.get(c).getScaledTouchSlop();
            stroke.setStyle(Paint.Style.STROKE);
            label.setTextAlign(Paint.Align.CENTER);
            label.setTextSize(Ui.dp(c, 14));
            label.setFakeBoldText(true);
            setContentDescription("키보드 미리보기. 좌우로 밀어 키보드·영역·키 페이지를 넘깁니다. 키보드 전체나 영역, 키를 누르면 "
                    + "그 설정 창이 열리고, 크기와 여백은 창의 슬라이더로도 바꿀 수 있습니다.");
        }

        private final Paint areaLabel = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF area = new RectF();

        /** 영역마다 둥근 테두리와 가운데 이름표 ("도구 막대" + suffix). 그 층에서 고른 영역은 굵게 칠한다. */
        private void drawAreas(Canvas c, int which, float alpha, float dx, String suffix) {
            Context ctx = getContext();
            int accent = theme.accent;
            c.save();
            c.translate(dx, 0);
            areaLabel.setTextSize(Ui.dp(ctx, 11));
            areaLabel.setTextAlign(Paint.Align.CENTER);
            areaLabel.setFakeBoldText(true);
            float r = Ui.dp(ctx, prefs.keyRadiusDp()) + Ui.dp(ctx, 4);
            for (int a = 0; a < AREAS; a++) {
                if (!padBounds(a, area)) continue;   // 영역과 그 위·아래 여백까지
                // 맨 위·맨 아래 윤곽선이 미리보기 가장자리에서 잘리지 않게 테두리 굵기의 절반만큼은 안쪽에 둔다.
                float edge = Ui.dp(ctx, 1.5f);
                area.top = Math.max(area.top, edge);
                area.bottom = Math.min(area.bottom, getHeight() - edge);
                boolean chosen = layer == which && selection != null && selection.kind == SEL_AREA && selection.area() == a;
                stroke.setColor(withAlpha(accent, (chosen ? 1f : 0.55f) * alpha));
                // 고른 영역은 고른 키와 같은 굵기로 굵게.
                stroke.setStrokeWidth(Ui.dp(ctx, chosen ? 2.5f : 1.25f));
                float rr = Math.min(r, Math.min(area.width(), area.height()) / 2f);
                c.drawRoundRect(area, rr, rr, stroke);
                if (chosen) {
                    fill.setColor(withAlpha(accent, 0.12f * alpha));
                    c.drawRoundRect(area, rr, rr, fill);
                }
                // 이름표: 영역 한가운데의 작은 알약. 고른 영역은 이름표 없이 굵은 테두리만.
                if (!chosen) drawPill(c, "눌러서 " + areaName(a, tab) + suffix + (areaOff(a) ? " (꺼짐)" : ""), area, alpha);
            }
            c.restore();
        }

        /** 영역 r 한가운데에 강조색 알약 이름표를 그린다. */
        private void drawPill(Canvas c, String name, RectF r, float alpha) {
            Context ctx = getContext();
            Paint.FontMetrics fm = areaLabel.getFontMetrics();
            float tw = areaLabel.measureText(name), th = fm.descent - fm.ascent;
            float cx = r.centerX(), cy = r.centerY();
            float ph = Ui.dp(ctx, 8), pv = Ui.dp(ctx, 3);
            tmp.set(cx - tw / 2 - ph, cy - th / 2 - pv, cx + tw / 2 + ph, cy + th / 2 + pv);
            fill.setColor(withAlpha(theme.accent, alpha));
            c.drawRoundRect(tmp, tmp.height() / 2, tmp.height() / 2, fill);
            areaLabel.setColor(withAlpha(theme.onAccent, alpha));
            c.drawText(name, tmp.centerX(), tmp.centerY() - (fm.ascent + fm.descent) / 2, areaLabel);
        }

        /** 키보드 층: 도구 막대부터 자판 끝까지 키보드 전체를 감싼 테두리 하나와 이름표. 고르면 굵게. */
        private void drawBoard(Canvas c, float alpha, float dx) {
            if (!boardBounds(area)) return;
            Context ctx = getContext();
            c.save();
            c.translate(dx, 0);
            boolean chosen = layer == LAYER_BOARD && selection != null && selection.kind == SEL_BOARD;
            stroke.setStrokeWidth(Ui.dp(ctx, chosen ? 2.5f : 1.25f));
            stroke.setColor(withAlpha(theme.accent, (chosen ? 1f : 0.55f) * alpha));
            float rr = Ui.dp(ctx, prefs.keyRadiusDp()) + Ui.dp(ctx, 4);
            c.drawRoundRect(area, rr, rr, stroke);
            if (chosen) {
                fill.setColor(withAlpha(theme.accent, 0.12f * alpha));
                c.drawRoundRect(area, rr, rr, fill);
            } else {
                areaLabel.setTextSize(Ui.dp(ctx, 11));
                areaLabel.setTextAlign(Paint.Align.CENTER);
                areaLabel.setFakeBoldText(true);
                drawPill(c, "눌러서 키보드 여백 조절", area, alpha);
            }
            c.restore();
        }



        /** 꺼 두어 빈 칸만 남은 영역인지 (도구 막대·숫자 줄). */
        private boolean areaOff(int a) {
            if (a == AREA_TOOLBAR) return placeholder != null && placeholder.getParent() != null;
            return false;
        }

        /** 꺼진 도구 막대·숫자 줄 빈 칸의 안내 글자. 영역 층에서는 이름표와 겹치지 않게 흐려져 사라진다. */
        private void drawOffHints(Canvas c, float alpha) {
            if (alpha < 0.01f) return;
            Context ctx = getContext();
            hintPaint.setTextSize(Ui.dp(ctx, 12));
            hintPaint.setTextAlign(Paint.Align.CENTER);
            hintPaint.setColor(withAlpha(AppTheme.of(ctx).hint, alpha));
            Paint.FontMetrics fm = hintPaint.getFontMetrics();
            View v = placeholder;
            if (v == null || v.getParent() == null) return;
            float cy = column.getTop() + (v.getTop() + v.getBottom()) / 2f;
            c.drawText("도구 막대 꺼짐 · 눌러서 설정", getWidth() / 2f, cy - (fm.ascent + fm.descent) / 2, hintPaint);
        }

        private final Paint hintPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

        /**
         * 윤곽선 층: 영역 층은 영역마다 둥근 테두리와 이름표, 키 층은 키마다 얇은 테두리. 넘기는 동안(layerPos)
         * 두 층이 서로 반대쪽으로 조금 미끄러지며 흐려지고 또렷해진다.
         */
        private void drawLayers(Canvas c) {
            Context ctx = getContext();
            float shift = Ui.dp(ctx, 28);
            int accent = theme.accent;
            float boardAlpha = Math.max(0f, 1f - Math.abs(layerPos - LAYER_BOARD));
            float areaAlpha = Math.max(0f, 1f - Math.abs(layerPos - LAYER_AREA));
            float keyAlpha = Math.max(0f, 1f - Math.abs(layerPos - LAYER_KEY));
            // 키보드 층과 영역 층은 같은 영역 윤곽선을 쓰고 이름표만 다르다 (여백 조절 · 설정).
            if (boardAlpha > 0.01f) drawBoard(c, boardAlpha, (LAYER_BOARD - layerPos) * shift);
            if (areaAlpha > 0.01f) drawAreas(c, LAYER_AREA, areaAlpha, (LAYER_AREA - layerPos) * shift, " 설정");
            drawOffHints(c, keyAlpha);
            if (keyAlpha > 0.01f) {
                c.save();
                c.translate((LAYER_KEY - layerPos) * shift, 0);
                stroke.setStrokeWidth(Ui.dp(ctx, 1.25f));
                stroke.setColor(withAlpha(accent, 0.55f * keyAlpha));
                float gx = Ui.dp(ctx, prefs.keyGapXDp()) / 2f, gy = Ui.dp(ctx, prefs.keyGapYDp()) / 2f;
                float out = Ui.dp(ctx, 1.5f);
                float kr = Ui.dp(ctx, prefs.keyRadiusDp()) + out;
                KeyboardLayout l = keys.getLayout();
                if (l != null) {
                    for (KeyboardLayout.Row row : l.rows) {
                        for (Key k : row.keys) {
                            if (!selectable(k)) continue;
                            area.set(k.rect.left + gx - out, keys.getTop() + k.rect.top + gy - out,
                                    k.rect.right - gx + out, keys.getTop() + k.rect.bottom - gy + out);
                            float rr = Math.min(kr, Math.min(area.width(), area.height()) / 2f);
                            c.drawRoundRect(area, rr, rr, stroke);
                        }
                    }
                }
                if (bar.getParent() != null) {
                    for (int i = 0; i < bar.getChildCount(); i++) {
                        View b = bar.getChildAt(i);
                        if (!(b.getTag() instanceof String)) continue;
                        float inset = Math.max(0, (b.getHeight() - Ui.chipHeight(ctx, b.getHeight())) / 2f) - out;
                        area.set(b.getLeft() + Ui.dp(ctx, 2), bar.getTop() + b.getTop() + inset,
                                b.getRight() - Ui.dp(ctx, 2), bar.getTop() + b.getBottom() - inset);
                        c.drawRoundRect(area, area.height() / 2, area.height() / 2, stroke);
                    }
                }
                c.restore();
            }
        }

        private int withAlpha(int color, float a) {
            int base = color >>> 24;
            return (Math.round(base * Math.max(0f, Math.min(1f, a))) << 24) | (color & 0x00FFFFFF);
        }

        /** 균형 배열 사용자화: 자음·모음 쪽 윤곽선과 자·모음 경계선. 끄는 중인 쪽은 굵게 칠한다. */
        private void drawBalanced(Canvas c) {
            Context ctx = getContext();
            float r = Ui.dp(ctx, prefs.keyRadiusDp()) + Ui.dp(ctx, 4);
            for (boolean cons : new boolean[]{true, false}) {
                if (!blockBounds(cons, area)) continue;
                boolean on = dragging && active == (cons ? Handle.B_CONSONANT : Handle.B_VOWEL);
                stroke.setStrokeWidth(Ui.dp(ctx, on ? 2.5f : 1.25f));
                stroke.setColor(withAlpha(theme.accent, on ? 1f : 0.55f));
                float rr = Math.min(r, Math.min(area.width(), area.height()) / 2f);
                c.drawRoundRect(area, rr, rr, stroke);
                if (on) {
                    fill.setColor(withAlpha(theme.accent, 0.12f));
                    c.drawRoundRect(area, rr, rr, fill);
                }
            }
            if (prefs.balancedLayout() && areaBounds(AREA_LETTERS, area)) {
                boolean on = dragging && active == Handle.B_BOUNDARY;
                float x = boundaryX();
                stroke.setStrokeWidth(Ui.dp(ctx, on ? 2f : 1.25f));
                stroke.setColor(withAlpha(theme.accent, on ? 1f : 0.45f));
                // 두 윤곽선이 맞닿아 있어도 둥근 모서리 밖으로 튀어나오지 않게, 윤곽선의 모서리 반지름만큼 양 끝을 줄인다.
                float cut = Math.min(r, area.height() / 2f - Ui.dp(ctx, 1));
                c.drawLine(x, area.top + cut, x, area.bottom - cut, stroke);
            }
        }

        @Override
        protected void onDraw(Canvas c) {
            Context ctx = getContext();
            int accent = theme.accent;
            if (balancedMode) drawBalanced(c);
            else drawLayers(c);
            // 고른 것: 강조색 테두리.
            RectF sel = selectionBounds();   // 고른 키·도구 막대 버튼 (영역·키보드는 drawLayers에서)
            if (sel != null) {
                float r = Math.min(Ui.dp(ctx, prefs.keyRadiusDp()) + Ui.dp(ctx, 3), Math.min(sel.width(), sel.height()) / 2f);
                stroke.setStrokeWidth(Ui.dp(ctx, 2.5f));
                stroke.setColor(accent);
                c.drawRoundRect(sel, r, r, stroke);
            }
            // 손잡이: 강조색 알약에 바탕색 테두리 (키 위에서도 보이게). 끄는 중인 손잡이는 더 크게.
            for (Handle h : Handle.values()) {
                if (!handleCenter(h, point)) continue;
                boolean on = dragging && h == active;
                if (h.knob()) {
                    drawKnob(c, h, point[0], point[1], on);
                    continue;
                }
                float lenHalf = Ui.dp(ctx, on ? 16 : 11), thickHalf = Ui.dp(ctx, on ? 3.5f : 2.5f);
                if (h.vertical) tmp.set(point[0] - lenHalf, point[1] - thickHalf, point[0] + lenHalf, point[1] + thickHalf);
                else tmp.set(point[0] - thickHalf, point[1] - lenHalf, point[0] + thickHalf, point[1] + lenHalf);
                stroke.setStrokeWidth(Ui.dp(ctx, 2));
                stroke.setColor(theme.background);
                c.drawRoundRect(tmp, thickHalf, thickHalf, stroke);
                fill.setColor(accent);
                c.drawRoundRect(tmp, thickHalf, thickHalf, fill);
            }
            // 끄는 동안: 무엇을 얼마로 바꾸는지 자판 가운데에 크게 보여 준다.
            if (dragging && active != null) {
                String text = handleName(active) + "  " + valueText(active, dragValue);
                float tw = label.measureText(text);
                float cx = getWidth() / 2f;
                float cy = keys.getTop() + keys.getHeight() / 2f;
                float padH = Ui.dp(ctx, 14), padV = Ui.dp(ctx, 9);
                Paint.FontMetrics fm = label.getFontMetrics();
                float th = fm.descent - fm.ascent;
                tmp.set(cx - tw / 2 - padH, cy - th / 2 - padV, cx + tw / 2 + padH, cy + th / 2 + padV);
                fill.setColor(accent);
                c.drawRoundRect(tmp, tmp.height() / 2, tmp.height() / 2, fill);
                label.setColor(theme.onAccent);
                c.drawText(text, cx, cy - (fm.ascent + fm.descent) / 2, label);
            }
        }

        /** 높이 버튼: 강조색 원에 ↕, 바탕색 테두리. 눌렀으면 옆에 안내 알약. */
        private void drawKnob(Canvas c, Handle h, float x, float y, boolean on) {
            Context ctx = getContext();
            float rad = Ui.dp(ctx, on ? 14 : 12);
            stroke.setStrokeWidth(Ui.dp(ctx, 2));
            stroke.setColor(theme.background);
            fill.setColor(theme.accent);
            c.drawCircle(x, y, rad, fill);
            c.drawCircle(x, y, rad, stroke);
            // ↕(높이) 또는 ↔(균형 배열의 폭·위치): 줄과 양 끝 화살촉.
            stroke.setColor(theme.onAccent);
            stroke.setStrokeWidth(Ui.dp(ctx, 2));
            stroke.setStrokeCap(Paint.Cap.ROUND);
            float len = Ui.dp(ctx, 6.5f), head = Ui.dp(ctx, 3.5f);
            if (h.vertical) {
                c.drawLine(x, y - len, x, y + len, stroke);
                c.drawLine(x - head, y - len + head, x, y - len, stroke);
                c.drawLine(x + head, y - len + head, x, y - len, stroke);
                c.drawLine(x - head, y + len - head, x, y + len, stroke);
                c.drawLine(x + head, y + len - head, x, y + len, stroke);
            } else {
                c.drawLine(x - len, y, x + len, y, stroke);
                c.drawLine(x - len + head, y - head, x - len, y, stroke);
                c.drawLine(x - len + head, y + head, x - len, y, stroke);
                c.drawLine(x + len - head, y - head, x + len, y, stroke);
                c.drawLine(x + len - head, y + head, x + len, y, stroke);
            }
            stroke.setStrokeCap(Paint.Cap.BUTT);
            if (h == knobHint && !dragging && android.os.SystemClock.uptimeMillis() < knobHintUntil && h.balanced()) {
                // 균형 배열의 ↔ 버튼: 버튼 바로 아래에 두 줄 안내 (길게 누르고 있으면 위치 조절 안내를 굵게 앞에).
                drawHintLines(c, x, y + rad + Ui.dp(ctx, 8), positionDrag
                        ? new String[]{"좌우로 드래그해서 가로 위치 조절"}
                        : new String[]{"좌우로 드래그해서 폭 조절", "길게 누른 뒤 드래그해서 위치 조절"});
            } else if (h == knobHint && !dragging && android.os.SystemClock.uptimeMillis() < knobHintUntil) {
                String text = "위·아래로 드래그해서 크기 조절";
                areaLabel.setTextSize(Ui.dp(ctx, 11));
                areaLabel.setTextAlign(Paint.Align.CENTER);
                areaLabel.setFakeBoldText(true);
                Paint.FontMetrics fm = areaLabel.getFontMetrics();
                float tw = areaLabel.measureText(text), th = fm.descent - fm.ascent;
                float ph = Ui.dp(ctx, 8), pv = Ui.dp(ctx, 4), gap = Ui.dp(ctx, 8);
                float pillW = tw + 2 * ph;
                if (h == Handle.HEIGHT) {
                    // 글자 키: 버튼 바로 아래에 가운데 맞춰 놓는다.
                    float left = Math.max(Ui.dp(ctx, 4), Math.min(x - pillW / 2, getWidth() - Ui.dp(ctx, 4) - pillW));
                    float top = y + rad + gap;
                    tmp.set(left, top, left + pillW, top + th + 2 * pv);
                } else {
                    // 그 밖의 영역: 오른쪽, 자리가 없으면 왼쪽.
                    float left = x + rad + gap;
                    if (left + pillW > getWidth() - Ui.dp(ctx, 4)) left = x - rad - gap - pillW;
                    tmp.set(left, y - th / 2 - pv, left + pillW, y + th / 2 + pv);
                }
                fill.setColor(theme.text);
                c.drawRoundRect(tmp, tmp.height() / 2, tmp.height() / 2, fill);
                areaLabel.setColor(theme.background);
                c.drawText(text, tmp.centerX(), tmp.centerY() - (fm.ascent + fm.descent) / 2, areaLabel);
            }
        }

        /** (cx, top)을 위 가운데로 하는 어두운 알약에 안내 글자 줄들을 그린다 (화면 밖으로 나가지 않게). */
        private void drawHintLines(Canvas c, float cx, float top, String[] lines) {
            Context ctx = getContext();
            areaLabel.setTextSize(Ui.dp(ctx, 11));
            areaLabel.setTextAlign(Paint.Align.CENTER);
            areaLabel.setFakeBoldText(true);
            Paint.FontMetrics fm = areaLabel.getFontMetrics();
            float lh = fm.descent - fm.ascent, tw = 0;
            for (String l : lines) tw = Math.max(tw, areaLabel.measureText(l));
            float ph = Ui.dp(ctx, 8), pv = Ui.dp(ctx, 4), pillW = tw + 2 * ph;
            float left = Math.max(Ui.dp(ctx, 4), Math.min(cx - pillW / 2, getWidth() - Ui.dp(ctx, 4) - pillW));
            tmp.set(left, top, left + pillW, top + lh * lines.length + 2 * pv);
            fill.setColor(theme.text);
            float rr = Math.min(tmp.height() / 2, Ui.dp(ctx, 12));
            c.drawRoundRect(tmp, rr, rr, fill);
            areaLabel.setColor(theme.background);
            for (int i = 0; i < lines.length; i++) {
                c.drawText(lines[i], tmp.centerX(), top + pv + i * lh - fm.ascent, areaLabel);
            }
        }

        /** 고른 것의 테두리 (미리보기 좌표). 없으면 null. */
        private RectF selectionBounds() {
            if (selection == null) return null;
            Context ctx = getContext();
            float out = Ui.dp(ctx, 2);
            if (selection.kind == SEL_KEY && selectedKey != null) {
                RectF r = selectedKey.rect;
                float gx = Ui.dp(ctx, prefs.keyGapXDp()) / 2f, gy = Ui.dp(ctx, prefs.keyGapYDp()) / 2f;
                tmp.set(r.left + gx - out, keys.getTop() + r.top + gy - out, r.right - gx + out, keys.getTop() + r.bottom - gy + out);
                return tmp;
            }
            // 남은 것은 도구 막대 버튼 하나 (영역·키보드는 윤곽선 층에서 그린다).
            if (selection.kind != SEL_TOOL_BUTTON || bar.getParent() == null) return null;
            for (int i = 0; i < bar.getChildCount(); i++) {
                View b = bar.getChildAt(i);
                if (selection.toolId.equals(b.getTag())) {
                    float inset = Math.max(0, (b.getHeight() - Ui.chipHeight(ctx, b.getHeight())) / 2f) - out;
                    tmp.set(b.getLeft() + Ui.dp(ctx, 2), bar.getTop() + b.getTop() + inset,
                            b.getRight() - Ui.dp(ctx, 2), bar.getTop() + b.getBottom() - inset);
                    return tmp;
                }
            }
            return null;
        }

        @Override
        protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
            super.onLayout(changed, left, top, right, bottom);
            updateExclusion();
        }

        /** 화면 양옆의 여백 손잡이를 끌 때 시스템 '뒤로 가기' 몸짓이 가로채지 않게 한다 (Android 10+). */
        private void updateExclusion() {
            if (android.os.Build.VERSION.SDK_INT < 29) return;
            exclusion.clear();
            int half = Ui.dp(getContext(), 36), wide = Ui.dp(getContext(), 32);
            for (Handle h : new Handle[]{Handle.PAD_LEFT, Handle.PAD_RIGHT, Handle.AREA_L, Handle.AREA_R}) {
                if (!handleCenter(h, point)) continue;
                int x = Math.round(point[0]), y = Math.round(point[1]);
                exclusion.add(new Rect(x - wide, y - half, x + wide, y + half));
            }
            setSystemGestureExclusionRects(exclusion);
        }

        @Override
        public boolean onTouchEvent(MotionEvent e) {
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    getParent().requestDisallowInterceptTouchEvent(true);
                    downX = e.getX();
                    downY = e.getY();
                    downRawX = e.getRawX();
                    downRawY = e.getRawY();
                    active = handleAt(downX, downY);
                    positionDrag = false;
                    if (active != null && active.knob()) holdKnobHint(active);
                    // 균형 배열의 ↔ 버튼: 움직이지 않고 길게 누르면 가로 위치 조절로 바뀐다.
                    if (active == Handle.B_CONSONANT || active == Handle.B_VOWEL) {
                        postDelayed(positionPress, ViewConfiguration.getLongPressTimeout());
                    }
                    dragging = false;
                    moved = false;
                    paging = false;
                    return true;
                case MotionEvent.ACTION_MOVE: {
                    float dx = e.getRawX() - downRawX, dy = e.getRawY() - downRawY;
                    if (!dragging && !moved && Math.hypot(dx, dy) > slop) {
                        moved = true;
                        removeCallbacks(positionPress);   // 길게 누르기 전에 움직였다: 키 폭 조절
                        // 손잡이를 그 방향으로 끌기 시작했을 때만 크기를 바꾼다 (손잡이 위에서 옆으로 밀면 아무것도 하지 않는다).
                        boolean handleDrag = active != null
                                && (active.vertical ? Math.abs(dy) >= Math.abs(dx) : Math.abs(dx) >= Math.abs(dy));
                        if (!handleDrag && !balancedMode && Math.abs(dx) > Math.abs(dy) * 1.5f) {
                            // 손잡이를 끄는 것이 아닌 가로 밀기: 윤곽선 층 넘기기.
                            paging = true;
                            pageStart = layerPos;
                            if (layerAnim != null) layerAnim.cancel();
                            downRawX = e.getRawX();
                            dx = 0;
                        }
                        if (handleDrag) {
                            dragging = true;
                            beginDrag(active);
                            // 여기부터 잰다: 손가락이 움직임 문턱을 넘는 동안의 거리만큼 값이 튀지 않게.
                            downRawX = e.getRawX();
                            downRawY = e.getRawY();
                            dx = 0;
                            dy = 0;
                            performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
                            invalidate();
                        }
                    }
                    // 화면 좌표로 잰다: 키보드가 커지면 이 뷰도 위로 자라 뷰 안의 좌표가 바뀌기 때문이다.
                    if (dragging) dragTo(active, dx, dy);
                    if (paging) {
                        // 왼쪽으로 밀면 다음 층(키)으로, 오른쪽으로 밀면 앞 층(영역)으로. 손가락을 따라 움직인다.
                        layerPos = Math.max(Math.max(0f, layer - 1f),
                                Math.min(Math.min(LAYERS - 1f, layer + 1f), pageStart - dx / (getWidth() * 0.6f)));
                        invalidate();
                    }
                    return true;
                }
                case MotionEvent.ACTION_UP:
                    removeCallbacks(positionPress);
                    if (paging) {
                        paging = false;
                        // 3분의 1 넘게 넘겼거나 빠르게 밀었으면 그 층으로, 아니면 제자리로.
                        float moveX = e.getRawX() - downRawX;
                        int target = Math.abs(layerPos - pageStart) > 0.33f || Math.abs(moveX) > Ui.dp(getContext(), 72)
                                ? Math.max(0, Math.min(LAYERS - 1, layer + (moveX < 0 ? 1 : -1))) : layer;
                        setLayer(target);
                    } else if (dragging) {
                        dragging = false;
                        positionDrag = false;
                        knobHint = null;   // 끌고 난 뒤에는 안내를 다시 보이지 않는다
                        active = null;
                        invalidate();
                        updateExclusion();
                    } else if (!moved && active != null && active.knob()) {
                        // 높이 버튼을 눌렀다 뗐다: 안내는 이미 보였다가 잠깐 뒤 사라진다.
                        positionDrag = false;
                        releaseKnobHint();
                        active = null;
                    } else if (!moved) {
                        playSoundEffect(android.view.SoundEffectConstants.CLICK);
                        tapAt(downX, downY);
                    }
                    return true;
                case MotionEvent.ACTION_CANCEL:
                    removeCallbacks(positionPress);
                    positionDrag = false;
                    if (paging) {
                        paging = false;
                        animateLayerTo(layer);
                    }
                    dragging = false;
                    knobHint = null;
                    active = null;
                    invalidate();
                    return true;
                default:
                    return true;
            }
        }
    }
}
