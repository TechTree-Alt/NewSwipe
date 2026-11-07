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
 * '키보드 편집' 화면 아래에 붙는, 실제 키보드와 같은 크기·모양의 미리보기 (도구 막대 + 자판).
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
    static final int SEL_KEY = 0, SEL_TOOL_BUTTON = 1, SEL_TOOLBAR = 2, SEL_AREA = 3;

    /**
     * 윤곽선 층 (애플워치 페이스 편집처럼 좌우로 밀어 넘긴다): 영역 층은 도구 막대·숫자 줄·글자 키·기능키를 크게 감싸
     * 그 영역 전체의 설정을 고르고, 키 층은 키 하나하나를 감싸 그 키의 동작을 고른다.
     */
    /** 윤곽선 층 (왼쪽부터): 키보드 전체 · 영역 · 키 하나. */
    static final int LAYER_BOARD = 0, LAYER_AREA = 1, LAYER_KEY = 2, LAYERS = 3;
    /** 영역 층의 영역. */
    static final int AREA_TOOLBAR = 0, AREA_NUMBER = 1, AREA_LETTERS = 2, AREA_FUNCTION = 3, AREAS = 4;
    static final String[] AREA_NAMES = {"도구 막대", "숫자 줄", "글자 키", "기능키"};

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

    private static final int H_TOOLBAR = 0, H_NUMBER_ROW = 1, H_HEIGHT = 2, H_PAD_LEFT = 3, H_PAD_RIGHT = 4,
            H_PAD_BOTTOM = 5, H_GAP_X = 6, H_GAP_Y = 7, H_PAD_TOP = 8, H_FN_ROW = 9, HANDLES = 10;
    /** 손잡이마다 위아래로 끄는지 (아니면 좌우). */
    private static final boolean[] VERTICAL = {true, true, true, false, false, true, false, true, true, true};
    private static final String[] HANDLE_NAMES = {"도구 막대 높이", "숫자 줄 높이", "키보드 높이", "왼쪽 여백", "오른쪽 여백",
            "아래쪽 여백", "키 좌우 여백", "키 상하 여백", "위쪽 여백", "기능키 높이"};
    private static final String[] HANDLE_KEYS = {Prefs.TOOLBAR_HEIGHT, Prefs.NUMBER_ROW_HEIGHT, Prefs.KEYBOARD_HEIGHT,
            Prefs.PAD_LEFT, Prefs.PAD_RIGHT, Prefs.PAD_BOTTOM, Prefs.KEY_GAP_X, Prefs.KEY_GAP_Y, Prefs.PAD_TOP, Prefs.FN_ROW_HEIGHT};

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
        selection = null;
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
        keys.configure(prefs.longPressMs(), prefs.swipeThresholdDp(), false, true,
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
        keys.setInsets(Ui.dp(c, prefs.padLeftDp()), padT, Ui.dp(c, prefs.padRightDp()), padB - lift);
        keys.setSpaceLabel(prefs.spaceLangLabel() ? (tab == TAB_ENGLISH ? "English" : tab == TAB_KOREAN ? "한국어" : null) : null);
        KeyboardLayout l;
        if (tab == TAB_ENGLISH) l = KeyboardLayout.english(prefs);
        else if (tab == TAB_SYMBOLS) l = symbolsPage2 ? KeyboardLayout.symbols2(true, prefs) : KeyboardLayout.symbols(true, prefs);
        else l = KeyboardLayout.korean(prefs);
        numberRowShown = tab != TAB_SYMBOLS && prefs.numberRow() && l.rows.length > 0 && l.rows[0].keys.length > 0
                && KeyboardLayout.isDigit(l.rows[0].keys[0].label);
        // 키보드와 같은 높이: 글자 4줄 + 숫자 줄 (기호 자판도 같은 높이를 쓴다).
        float rows = KeyboardLayout.rowUnits(prefs);
        keys.setKeyboardHeight(Math.round(Ui.dp(c, 54) * rows * prefs.keyboardHeight() / 100f) + padT + padB - lift);
        keys.setLayout(l);
        rebuildToolbar(lift);
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

    /** 숫자 줄을 껐을 때 그 자리에 남기는 빈 칸 (실제 키보드에는 없다): 눌러서 숫자 줄 영역을 고르고 다시 켤 수 있다. */
    private android.widget.TextView numberGap;

    private android.widget.TextView numberGap() {
        if (numberGap != null) return numberGap;
        Context c = getContext();
        AppTheme app = AppTheme.of(c);
        numberGap = new android.widget.TextView(c);

        android.graphics.drawable.GradientDrawable d = new android.graphics.drawable.GradientDrawable();
        d.setColor(app.bg);
        d.setStroke(Ui.dp(c, 1), app.hint, Ui.dp(c, 4), Ui.dp(c, 3));
        d.setCornerRadius(Ui.dp(c, 8));
        numberGap.setBackground(new android.graphics.drawable.InsetDrawable(d, Ui.dp(c, 8), Ui.dp(c, 3), Ui.dp(c, 8), Ui.dp(c, 3)));
        return numberGap;
    }

    /** 숫자 줄 빈 칸을 둘지: 숫자 줄을 껐고, 숫자 줄이 있는 자판(한글·영어)을 보고 있을 때. */
    private boolean numberGapShown() {
        return !prefs.numberRow() && tab != TAB_SYMBOLS;
    }

    /** 도구 막대: 실제와 같은 자리·순서·크기의 버튼들 (누를 수 없고, 누르면 그 버튼을 고른다). */
    private void rebuildToolbar(int lift) {
        Context c = getContext();
        int height = Math.round(Ui.dp(c, 40) * prefs.toolbarHeight() / 100f);
        float scale = prefs.toolButtonSize() / 100f;
        int width = Math.round(Ui.dp(c, 40) * scale);
        String[][] order = prefs.toolOrder();
        StringBuilder sig = new StringBuilder().append(height).append(',').append(width).append(',')
                .append(prefs.padLeftDp()).append(',').append(prefs.padRightDp()).append(',')
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
            bar.setPadding(Ui.dp(c, prefs.padLeftDp()), 0, Ui.dp(c, prefs.padRightDp()), 0);
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
        boolean gap = numberGapShown();
        int gapHeight = Math.round(Ui.dp(c, 54) * prefs.keyboardHeight() / 100f * prefs.numberRowHeight() / 100f);
        String colSig = shown + "," + bottom + "," + height + "," + lift + "," + gap + "," + gapHeight;
        if (colSig.equals(columnSignature)) return;
        columnSignature = colSig;
        column.removeAllViews();
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, height);
        blp.bottomMargin = lift;   // 자판 아래에 두면 아래쪽 여백이 도구 막대 밑에 온다
        LinearLayout.LayoutParams klp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        if (!shown) {
            column.addView(placeholder(), new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(c, 30)));
        } else if (!bottom) {
            column.addView(bar, blp);
        }
        if (gap) column.addView(numberGap(), new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, gapHeight));
        column.addView(keys, klp);
        if (shown && bottom) column.addView(bar, blp);
    }

    /** 고른 키를 지금 자판에서 다시 찾는다. 없으면 고르지 않은 것으로 돌린다. */
    private void resolveSelection() {
        selectedKey = null;
        if (selection == null) return;
        if (selection.kind == SEL_TOOLBAR) return;   // 도구 막대를 꺼도 그 자리(또는 알림 띠)는 고를 수 있다
        if (selection.kind == SEL_AREA) {
            if (!areaBounds(selection.area(), new RectF())) selection = null;   // 숫자 줄을 껐다 등
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
        if (layer == LAYER_BOARD) {   // 키보드 층은 손잡이로 여백만 바꾼다
            listener.onTap(null);
            return;
        }
        if (numberGap != null && numberGap.getParent() != null && y >= numberGap.getTop() && y < numberGap.getBottom()) {
            // 꺼진 숫자 줄 자리: 어느 층에서든 숫자 줄 영역을 고른다 (다시 켤 수 있게).
            if (layer != LAYER_AREA) setLayer(LAYER_AREA);
            listener.onTap(Selection.area(AREA_NUMBER));
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
        if (placeholder != null && placeholder.getParent() != null && y >= placeholder.getTop() && y < placeholder.getBottom()) {
            listener.onTap(Selection.toolbar());
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
            listener.onTap(Selection.toolbar());
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
    boolean areaBounds(int area, RectF out) {
        Context c = getContext();
        float pad = Ui.dp(c, 3);
        if (area == AREA_TOOLBAR) {
            View v = bar.getParent() != null ? bar : placeholder != null && placeholder.getParent() != null ? placeholder : null;
            if (v == null) return false;
            out.set(v.getLeft() + pad, v.getTop() + pad, v.getRight() - pad, v.getBottom() - pad);
            return true;
        }
        KeyboardLayout l = keys.getLayout();
        if (l == null || l.rows.length == 0) return false;
        int first, last;
        int letterStart = numberRowShown ? 1 : 0;
        switch (area) {
            case AREA_NUMBER:
                if (!numberRowShown) {
                    if (numberGap == null || numberGap.getParent() == null) return false;
                    out.set(numberGap.getLeft() + pad, numberGap.getTop() + pad, numberGap.getRight() - pad,
                            numberGap.getBottom() - pad);
                    return true;
                }
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
    private boolean handleShown(int h) {
        if (keys.getLayout() == null) return false;
        // 키보드 층: 키보드 전체의 여백 (위·아래·왼쪽·오른쪽). 다른 층에는 여백 손잡이가 없다.
        if (layer == LAYER_BOARD) return h == H_PAD_TOP || h == H_PAD_BOTTOM || h == H_PAD_LEFT || h == H_PAD_RIGHT;
        if (selection == null) return false;
        // 무언가를 고르면 그것을 조절하는 손잡이만: 영역은 그 영역의 높이, 키는 키 좌우·상하 여백, 도구 막대는 도구 막대 높이.
        switch (selection.kind) {
            case SEL_TOOLBAR: return h == H_TOOLBAR && bar.getParent() != null;
            case SEL_AREA:
                switch (selection.area()) {
                    case AREA_TOOLBAR: return h == H_TOOLBAR && bar.getParent() != null;
                    case AREA_NUMBER: return h == H_NUMBER_ROW && numberRowShown;
                    case AREA_LETTERS: return h == H_HEIGHT;
                    default: return h == H_FN_ROW;
                }
            case SEL_KEY: return (h == H_GAP_X || h == H_GAP_Y) && selectedKey != null && selectable(selectedKey);
            default: return false;
        }
    }

    /** 손잡이의 가운데 (미리보기 좌표). 보이지 않으면 false. */
    private boolean handleCenter(int h, float[] out) {
        if (!handleShown(h)) return false;
        Context c = getContext();
        int w = getWidth();
        int kTop = keys.getTop(), kBottom = keys.getBottom();
        float padL = Ui.dp(c, prefs.padLeftDp()), padR = Ui.dp(c, prefs.padRightDp());
        float padT = Ui.dp(c, prefs.padTopDp());
        // 자판 뷰 안의 아래쪽 여백 (도구 막대를 자판 아래에 두면 0이고, 여백은 도구 막대 밑에 있다).
        float padB = barLifted() ? 0 : Ui.dp(c, prefs.padBottomDp());
        KeyboardLayout l = keys.getLayout();
        switch (h) {
            case H_TOOLBAR:
                // 도구 막대의 위 가장자리 가운데. 자판 아래에 두어도 아래쪽 여백 손잡이(도구 막대 밑)와는 막대 높이만큼 떨어져 있다.
                out[0] = w / 2f;
                out[1] = bar.getTop() + (prefs.toolbarBottom() ? 0 : Ui.dp(c, 4));
                return true;
            case H_NUMBER_ROW:
            case H_HEIGHT:
            case H_FN_ROW: {
                // 영역 윤곽선의 위 가장자리: 숫자 줄, 또는 글자 줄 맨 위 (글자 줄을 키우면 숫자 줄도 같은 비율로 커진다).
                RectF r = new RectF();
                if (!areaBounds(h == H_NUMBER_ROW ? AREA_NUMBER : h == H_FN_ROW ? AREA_FUNCTION : AREA_LETTERS, r)) return false;
                out[0] = w / 2f;
                out[1] = Math.max(r.top, Ui.dp(c, 4));
                return true;
            }
            case H_PAD_TOP:
                // 자판 뷰의 위 가장자리 (위쪽 여백의 바깥쪽). 끌어 올리면 여백이 늘어 키보드가 위로 자란다.
                out[0] = w / 2f;
                out[1] = Math.max(kTop, Ui.dp(c, 4));
                return true;
            case H_PAD_LEFT:
                out[0] = Math.max(padL, Ui.dp(c, 3));
                out[1] = (kTop + padT + kBottom - padB) / 2f;
                return true;
            case H_PAD_RIGHT:
                out[0] = Math.min(w - padR, w - Ui.dp(c, 3));
                out[1] = (kTop + padT + kBottom - padB) / 2f;
                return true;
            case H_PAD_BOTTOM:
                out[0] = w / 2f;
                // 여백의 위 가장자리: 보통은 자판의 맨 아래 키 줄 밑, 도구 막대를 자판 아래에 두면 도구 막대 밑.
                float edge = barLifted() ? bar.getBottom() : kBottom - padB;
                out[1] = Math.min(edge, getHeight() - Ui.dp(c, 3));
                return true;
            case H_GAP_X: {
                RectF r = selectedKey.rect;
                out[0] = r.right - Ui.dp(c, prefs.keyGapXDp()) / 2f;
                out[1] = kTop + r.centerY();
                return true;
            }
            case H_GAP_Y: {
                RectF r = selectedKey.rect;
                // 맨 아래 줄 키의 아래 가장자리는 아래쪽 여백 손잡이와 겹칠 수 있어 키의 왼쪽 1/4에 둔다.
                float x = r.centerX();
                if (!barLifted() && Math.abs(x - w / 2f) < Ui.dp(c, 40) && kBottom - padB - (kTop + r.bottom) < Ui.dp(c, 8)) {
                    x = r.left + r.width() / 4f;
                }
                out[0] = x;
                out[1] = kTop + r.bottom - Ui.dp(c, prefs.keyGapYDp()) / 2f;
                return true;
            }
            default:
                return false;
        }
    }

    /** (x, y) 가까이 있는 손잡이 (가장 가까운 것). 없으면 -1. */
    private int handleAt(float x, float y) {
        float[] p = new float[2];
        float reach = Ui.dp(getContext(), 22);
        int best = -1;
        float bestDist = Float.MAX_VALUE;
        for (int h = 0; h < HANDLES; h++) {
            if (!handleCenter(h, p)) continue;
            // 손잡이 모양(긴 쪽 28dp)을 따라 잰 거리.
            float half = Ui.dp(getContext(), 18);
            float dx = Math.abs(x - p[0]), dy = Math.abs(y - p[1]);
            if (VERTICAL[h]) dx = Math.max(0, dx - half);
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

    private void beginDrag(int h) {
        Context c = getContext();
        switch (h) {
            case H_TOOLBAR: dragStart = bar.getHeight(); break;
            case H_NUMBER_ROW: dragStart = Ui.dp(c, 54) * prefs.keyboardHeight() / 100f * prefs.numberRowHeight() / 100f; break;
            case H_FN_ROW: dragStart = Ui.dp(c, 54) * prefs.keyboardHeight() / 100f * prefs.fnRowHeight() / 100f; break;
            case H_HEIGHT: dragStart = Ui.dp(c, 54) * rowUnits() * prefs.keyboardHeight() / 100f; break;
            default: dragStart = valueOf(h); break;
        }
        dragValue = valueOf(h);
    }

    private float rowUnits() {
        return KeyboardLayout.rowUnits(prefs);
    }

    private int valueOf(int h) {
        switch (h) {
            case H_TOOLBAR: return prefs.toolbarHeight();
            case H_NUMBER_ROW: return prefs.numberRowHeight();
            case H_HEIGHT: return prefs.keyboardHeight();
            case H_FN_ROW: return prefs.fnRowHeight();
            case H_PAD_LEFT: return prefs.padLeftDp();
            case H_PAD_RIGHT: return prefs.padRightDp();
            case H_PAD_BOTTOM: return prefs.padBottomDp();
            case H_PAD_TOP: return prefs.padTopDp();
            case H_GAP_X: return prefs.keyGapXDp();
            default: return prefs.keyGapYDp();
        }
    }

    private static int clamp(int v, int min, int max) {
        return Math.max(min, Math.min(max, v));
    }

    /** 처음 누른 곳에서 손가락이 움직인 거리(화면 기준 px)로 새 값을 정한다. 바뀌었으면 저장하고 다시 그린다. */
    private void dragTo(int h, float dx, float dy) {
        Context c = getContext();
        float density = getResources().getDisplayMetrics().density;
        int v;
        switch (h) {
            case H_TOOLBAR:
                v = clamp(Math.round((dragStart - dy) / Ui.dp(c, 40) * 100f), 70, 150);
                break;
            case H_NUMBER_ROW:
                v = clamp(Math.round((dragStart - dy) / (Ui.dp(c, 54) * prefs.keyboardHeight() / 100f) * 100f),
                        Prefs.NUMBER_ROW_HEIGHT_MIN, Prefs.NUMBER_ROW_HEIGHT_MAX);
                break;
            case H_FN_ROW:
                v = clamp(Math.round((dragStart - dy) / (Ui.dp(c, 54) * prefs.keyboardHeight() / 100f) * 100f),
                        Prefs.FN_ROW_HEIGHT_MIN, Prefs.FN_ROW_HEIGHT_MAX);
                break;
            case H_HEIGHT:
                v = clamp(Math.round((dragStart - dy) / (Ui.dp(c, 54) * rowUnits()) * 100f), 70, 140);
                break;
            case H_PAD_LEFT: v = clamp(Math.round(dragStart + dx / density), 0, 24); break;
            case H_PAD_RIGHT: v = clamp(Math.round(dragStart - dx / density), 0, 24); break;
            case H_PAD_BOTTOM: v = clamp(Math.round(dragStart - dy / density), 0, 48); break;
            case H_PAD_TOP: v = clamp(Math.round(dragStart - dy / density), 0, 24); break;
            // 키는 양쪽에서 여백의 절반씩 줄어들므로, 가장자리가 손가락을 따라가려면 움직인 거리의 두 배를 여백으로 바꾼다.
            case H_GAP_X: v = clamp(Math.round(dragStart - 2 * dx / density), 0, 12); break;
            default: v = clamp(Math.round(dragStart - 2 * dy / density), 0, 20); break;
        }
        if (v == dragValue) return;
        dragValue = v;
        prefs.raw().edit().putInt(HANDLE_KEYS[h], v).apply();
        refresh();
        overlay.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
        listener.onSizeChanged(HANDLE_KEYS[h]);
    }

    private static String valueText(int h, int v) {
        return v + (h == H_TOOLBAR || h == H_NUMBER_ROW || h == H_HEIGHT || h == H_FN_ROW ? "%" : "dp");
    }

    // ---------------------------------------------------------------- 덮개: 손잡이·고른 것 그리기와 터치

    private final class Overlay extends View {
        private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint label = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF tmp = new RectF();
        private final float[] point = new float[2];
        private final int slop;
        private int active = -1;
        private boolean dragging, moved;
        /** 좌우로 밀어 윤곽선 층을 넘기는 중 (손가락을 따라 layerPos가 움직인다). */
        private boolean paging;
        private float pageStart;
        private float downX, downY, downRawX, downRawY;
        private final java.util.List<Rect> exclusion = new java.util.ArrayList<>();

        Overlay(Context c) {
            super(c);
            slop = ViewConfiguration.get(c).getScaledTouchSlop();
            stroke.setStyle(Paint.Style.STROKE);
            label.setTextAlign(Paint.Align.CENTER);
            label.setTextSize(Ui.dp(c, 14));
            label.setFakeBoldText(true);
            setContentDescription("키보드 미리보기. 가장자리 손잡이를 끌어 크기를 바꿉니다. 좌우로 밀어 영역 페이지와 키 페이지를 "
                    + "넘기고, 영역이나 키를 눌러 설정을 바꿉니다. 같은 설정은 위 목록에서도 바꿀 수 있습니다.");
        }

        private final Paint areaLabel = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF area = new RectF();

        /** 꺼 두어 빈 칸만 남은 영역인지 (도구 막대·숫자 줄). */
        private boolean areaOff(int a) {
            if (a == AREA_TOOLBAR) return placeholder != null && placeholder.getParent() != null;
            if (a == AREA_NUMBER) return numberGap != null && numberGap.getParent() != null;
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
            View[] views = {placeholder, numberGap};
            String[] texts = {"도구 막대 꺼짐 · 눌러서 설정", "숫자 줄 꺼짐 · 눌러서 설정"};
            for (int i = 0; i < 2; i++) {
                View v = views[i];
                if (v == null || v.getParent() == null) continue;
                float cy = column.getTop() + (v.getTop() + v.getBottom()) / 2f;
                c.drawText(texts[i], getWidth() / 2f, cy - (fm.ascent + fm.descent) / 2, hintPaint);
            }
        }

        private final Paint hintPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

        /** 키보드 전체(도구 막대·숫자 줄 빈 칸·자판)를 감싼 테두리 자리. */
        private boolean boardBounds(RectF out) {
            if (column.getChildCount() == 0) return false;
            float pad = Ui.dp(getContext(), 2);
            View first = column.getChildAt(0), last = column.getChildAt(column.getChildCount() - 1);
            out.set(pad, column.getTop() + first.getTop() + pad, getWidth() - pad, column.getTop() + last.getBottom() - pad);
            return out.height() > 0;
        }

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
            if (boardAlpha > 0.01f && boardBounds(area)) {
                // 키보드 층: 도구 막대부터 자판 끝까지 키보드 전체를 감싼 테두리 하나.
                c.save();
                c.translate((LAYER_BOARD - layerPos) * shift, 0);
                stroke.setStrokeWidth(Ui.dp(ctx, 1.25f));
                stroke.setColor(withAlpha(accent, 0.55f * boardAlpha));
                float rr = Ui.dp(ctx, prefs.keyRadiusDp()) + Ui.dp(ctx, 4);
                c.drawRoundRect(area, rr, rr, stroke);
                c.restore();
            }
            if (areaAlpha > 0.01f) {
                c.save();
                c.translate((LAYER_AREA - layerPos) * shift, 0);
                stroke.setStrokeWidth(Ui.dp(ctx, 1.25f));   // 키 층의 테두리와 같은 굵기
                areaLabel.setTextSize(Ui.dp(ctx, 11));
                areaLabel.setTextAlign(Paint.Align.CENTER);
                areaLabel.setFakeBoldText(true);
                Paint.FontMetrics fm = areaLabel.getFontMetrics();
                float r = Ui.dp(ctx, prefs.keyRadiusDp()) + Ui.dp(ctx, 4);
                for (int a = 0; a < AREAS; a++) {
                    if (!areaBounds(a, area)) continue;
                    boolean chosen = selection != null && selection.kind == SEL_AREA && selection.area() == a;
                    stroke.setColor(withAlpha(accent, (chosen ? 1f : 0.55f) * areaAlpha));
                    // 고른 영역은 고른 키와 같은 굵기로 굵게.
                    stroke.setStrokeWidth(Ui.dp(ctx, chosen ? 2.5f : 1.25f));
                    float rr = Math.min(r, Math.min(area.width(), area.height()) / 2f);
                    c.drawRoundRect(area, rr, rr, stroke);
                    if (chosen) {
                        fill.setColor(withAlpha(accent, 0.12f * areaAlpha));
                        c.drawRoundRect(area, rr, rr, fill);
                    }
                    // 이름표: 영역 한가운데의 작은 알약 (높이 손잡이는 영역 위 가장자리에 있어 겹치지 않는다).
                    String name = AREA_NAMES[a] + (areaOff(a) ? " · 꺼짐" : "");
                    float tw = areaLabel.measureText(name), th = fm.descent - fm.ascent;
                    float cx = area.centerX(), cy = area.centerY();
                    float ph = Ui.dp(ctx, 8), pv = Ui.dp(ctx, 3);
                    tmp.set(cx - tw / 2 - ph, cy - th / 2 - pv, cx + tw / 2 + ph, cy + th / 2 + pv);
                    fill.setColor(withAlpha(accent, areaAlpha));
                    c.drawRoundRect(tmp, tmp.height() / 2, tmp.height() / 2, fill);
                    areaLabel.setColor(withAlpha(theme.onAccent, areaAlpha));
                    c.drawText(name, tmp.centerX(), tmp.centerY() - (fm.ascent + fm.descent) / 2, areaLabel);
                }
                c.restore();
            }
            drawOffHints(c, 1f - areaAlpha);
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

        @Override
        protected void onDraw(Canvas c) {
            Context ctx = getContext();
            int accent = theme.accent;
            drawLayers(c);
            // 고른 것: 강조색 테두리.
            RectF sel = selection != null && selection.kind == SEL_AREA ? null : selectionBounds();
            if (sel != null) {
                float r = Math.min(Ui.dp(ctx, prefs.keyRadiusDp()) + Ui.dp(ctx, 3), Math.min(sel.width(), sel.height()) / 2f);
                stroke.setStrokeWidth(Ui.dp(ctx, 2.5f));
                stroke.setColor(accent);
                c.drawRoundRect(sel, r, r, stroke);
            }
            // 손잡이: 강조색 알약에 바탕색 테두리 (키 위에서도 보이게). 끄는 중인 손잡이는 더 크게.
            for (int h = 0; h < HANDLES; h++) {
                if (!handleCenter(h, point)) continue;
                boolean on = dragging && h == active;
                float lenHalf = Ui.dp(ctx, on ? 20 : 14), thickHalf = Ui.dp(ctx, on ? 3.5f : 2.5f);
                if (VERTICAL[h]) tmp.set(point[0] - lenHalf, point[1] - thickHalf, point[0] + lenHalf, point[1] + thickHalf);
                else tmp.set(point[0] - thickHalf, point[1] - lenHalf, point[0] + thickHalf, point[1] + lenHalf);
                stroke.setStrokeWidth(Ui.dp(ctx, 2));
                stroke.setColor(theme.background);
                c.drawRoundRect(tmp, thickHalf, thickHalf, stroke);
                fill.setColor(h >= H_GAP_X && !on ? (accent & 0x00FFFFFF) | 0xCC000000 : accent);
                c.drawRoundRect(tmp, thickHalf, thickHalf, fill);
            }
            // 끄는 동안: 무엇을 얼마로 바꾸는지 자판 가운데에 크게 보여 준다.
            if (dragging && active >= 0) {
                String text = HANDLE_NAMES[active] + "  " + valueText(active, dragValue);
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

        /** 고른 것의 테두리 (미리보기 좌표). 없으면 null. */
        private RectF selectionBounds() {
            if (selection == null) return null;
            Context ctx = getContext();
            float out = Ui.dp(ctx, 2);
            if (selection.kind == SEL_AREA) return areaBounds(selection.area(), tmp) ? tmp : null;
            if (selection.kind == SEL_KEY && selectedKey != null) {
                RectF r = selectedKey.rect;
                float gx = Ui.dp(ctx, prefs.keyGapXDp()) / 2f, gy = Ui.dp(ctx, prefs.keyGapYDp()) / 2f;
                tmp.set(r.left + gx - out, keys.getTop() + r.top + gy - out, r.right - gx + out, keys.getTop() + r.bottom - gy + out);
                return tmp;
            }
            if (bar.getParent() == null) {
                // 도구 막대 줄이 없을 때는 알림 띠를 고른 것으로 보인다.
                if (selection.kind != SEL_TOOLBAR || placeholder == null || placeholder.getParent() == null) return null;
                tmp.set(placeholder.getLeft() + Ui.dp(ctx, 6), placeholder.getTop() + Ui.dp(ctx, 1),
                        placeholder.getRight() - Ui.dp(ctx, 6), placeholder.getBottom() - Ui.dp(ctx, 1));
                return tmp;
            }
            if (selection.kind == SEL_TOOLBAR) {
                tmp.set(bar.getLeft() + out, bar.getTop() + out, bar.getRight() - out, bar.getBottom() - out);
                return tmp;
            }
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
            for (int h : new int[]{H_PAD_LEFT, H_PAD_RIGHT, H_GAP_X}) {
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
                    dragging = false;
                    moved = false;
                    paging = false;
                    return true;
                case MotionEvent.ACTION_MOVE: {
                    float dx = e.getRawX() - downRawX, dy = e.getRawY() - downRawY;
                    if (!dragging && !moved && Math.hypot(dx, dy) > slop) {
                        moved = true;
                        // 손잡이를 그 방향으로 끌기 시작했을 때만 크기를 바꾼다 (손잡이 위에서 옆으로 밀면 아무것도 하지 않는다).
                        boolean handleDrag = active >= 0
                                && (VERTICAL[active] ? Math.abs(dy) >= Math.abs(dx) : Math.abs(dx) >= Math.abs(dy));
                        if (!handleDrag && Math.abs(dx) > Math.abs(dy) * 1.5f) {
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
                    if (paging) {
                        paging = false;
                        // 3분의 1 넘게 넘겼거나 빠르게 밀었으면 그 층으로, 아니면 제자리로.
                        float moveX = e.getRawX() - downRawX;
                        int target = Math.abs(layerPos - pageStart) > 0.33f || Math.abs(moveX) > Ui.dp(getContext(), 72)
                                ? Math.max(0, Math.min(LAYERS - 1, layer + (moveX < 0 ? 1 : -1))) : layer;
                        setLayer(target);
                    } else if (dragging) {
                        dragging = false;
                        active = -1;
                        invalidate();
                        updateExclusion();
                    } else if (!moved) {
                        playSoundEffect(android.view.SoundEffectConstants.CLICK);
                        tapAt(downX, downY);
                    }
                    return true;
                case MotionEvent.ACTION_CANCEL:
                    if (paging) {
                        paging = false;
                        animateLayerTo(layer);
                    }
                    dragging = false;
                    active = -1;
                    invalidate();
                    return true;
                default:
                    return true;
            }
        }
    }
}
