package com.alternative_studios.newswipe;

import android.app.Activity;
import android.content.Context;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.alternative_studios.newswipe.keyboard.BottomKeys;
import com.alternative_studios.newswipe.keyboard.KeyboardLayout;
import com.alternative_studios.newswipe.keyboard.KeyboardTheme;
import com.alternative_studios.newswipe.keyboard.KeyboardView;
import com.alternative_studios.newswipe.ui.Chevron;
import com.alternative_studios.newswipe.ui.ExpressiveSwitch;
import com.alternative_studios.newswipe.ui.Ui;

/** 맨 아래 줄 키(기호 키, 쉼표, 지구본 키, 스페이스바, 온점, 엔터)를 켜고 끄고 순서를 바꾸고, Shift 키·⌫를 켜고 끄는 화면 (하단 키 완전 사용자화). */
public final class BottomKeysEditorActivity extends Activity {

    private Prefs prefs;
    private LinearLayout list, keyList;
    /** 실제 키보드 뷰로 그리는 맨 아래 줄 미리보기 (글자·아이콘이 키보드와 똑같다). */
    private KeyboardView preview;
    private KeyboardTheme keyboardTheme;
    private String themeSignature;
    private int bg, card, textColor, hintColor, accentColor, onAccentColor;

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(AppTheme.wrap(base));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = new Prefs(this);
        keyboardTheme = KeyboardTheme.of(this);
        AppTheme colors = AppTheme.of(this);
        themeSignature = AppTheme.signature(this);
        bg = colors.bg;
        card = colors.card;
        textColor = colors.text;
        hintColor = colors.hint;
        accentColor = colors.accent;
        onAccentColor = colors.onAccent;

        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(bg);
        scroll.setFillViewport(true);
        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        int pad = Ui.dp(this, 16);
        list.setPadding(pad, 0, pad, Ui.dp(this, 32));
        scroll.addView(list);
        setContentView(new SettingsFrame(this, "기능키 순서·유무 사용자화", getString(R.string.app_name)).wrap(scroll));
        render();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (!themeSignature.equals(AppTheme.signature(this))) recreate();
    }

    private TextView text(String s, int sp, int color) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color);
        return t;
    }

    private void render() {
        list.removeAllViews();
        TextView note = text("오른쪽의 ≡를 잡고 끌어 순서를 바꿀 수 있습니다.", 13, hintColor);
        note.setPadding(Ui.dp(this, 4), 0, 0, Ui.dp(this, 4));
        list.addView(note);

        // 미리보기: 실제 키보드 뷰에 아래 두 줄(Shift·⌫ 줄과 맨 아래 줄)만 넣어 그린다. 눌러도 반응하지 않는다.
        preview = new KeyboardView(this);
        preview.setTheme(keyboardTheme);
        preview.configure(prefs.longPressMs(), prefs.swipeThresholdDp(), false, prefs.longPressChars(),
                false, prefs.keyTextSize());
        preview.setKeyShadow(prefs.keyShadow(), prefs.keyShadowStrength());
        preview.setKeyRadius(Ui.dp(this, prefs.keyRadiusDp()));
        preview.setGridColors(prefs.gridColors());
        preview.setPopupHints(prefs.longPressChars() && !prefs.popupHintHidden());
        // 키보드와 같은 좌우 여백과 한 줄 높이(키보드 높이 설정 반영)를 쓴다.
        int p = Ui.dp(this, 4);
        preview.setKeyGaps(Ui.dp(this, prefs.keyGapXDp()), Ui.dp(this, prefs.keyGapYDp()));
        preview.setInsets(Ui.dp(this, prefs.padLeftDp()), p, Ui.dp(this, prefs.padRightDp()), p);
        preview.setKeyboardHeight(Math.round(Ui.dp(this, 54) * 2 * prefs.keyboardHeight() / 100f) + 2 * p);
        preview.setOnTouchListener((v, ev) -> true);
        preview.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
        // 둥근 모서리로 자른다.
        preview.setOutlineProvider(new android.view.ViewOutlineProvider() {
            @Override
            public void getOutline(View view, android.graphics.Outline outline) {
                outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), Ui.dp(view.getContext(), 16));
            }
        });
        preview.setClipToOutline(true);
        LinearLayout.LayoutParams plp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        plp.topMargin = Ui.dp(this, 12);
        list.addView(preview, plp);
        updatePreview();

        // 키 목록 (지금 순서대로)
        LinearLayout keys = new LinearLayout(this);
        keyList = keys;
        keys.setOrientation(LinearLayout.VERTICAL);
        keys.setBackground(Ui.round(card, Ui.dp(this, 16)));
        // 줄을 카드 안쪽으로 조금 들여 놓아, 끌어 올린 줄의 둥근 모서리와 그림자가 카드 가장자리에서 잘리지 않게 한다.
        int inset = Ui.dp(this, 6);
        keys.setPadding(inset, Ui.dp(this, 4), inset, Ui.dp(this, 4));
        keys.setClipChildren(false);
        keys.setClipToPadding(false);
        list.setClipChildren(false);
        LinearLayout.LayoutParams klp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        klp.topMargin = Ui.dp(this, 12);
        list.addView(keys, klp);
        String[] order = prefs.bottomKeyOrder();
        for (String id : order) keys.addView(keyRow(id));

        // Shift 키·⌫: 글자 사이 양 끝에 고정이라 순서는 바꾸지 않고 켜고 끄기만 한다.
        LinearLayout fixed = new LinearLayout(this);
        fixed.setOrientation(LinearLayout.VERTICAL);
        fixed.setBackground(Ui.round(card, Ui.dp(this, 16)));
        fixed.setPadding(Ui.dp(this, 6), Ui.dp(this, 4), Ui.dp(this, 6), Ui.dp(this, 4));
        LinearLayout.LayoutParams flp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        flp.topMargin = Ui.dp(this, 12);
        list.addView(fixed, flp);
        // Shift 키를 끄면 그 자리에 Fn 키를 놓을 수 있다. Fn 키를 켜면 그 동작을 정하는 버튼이 나온다.
        // 토글과 같은 묶음 안에 둔다 (토글을 켰을 때만 보인다). 버튼의 위아래 여백까지 함께 접히도록
        // 여백 없는 틀로 감싸 그 틀을 펼치고 접는다. 버튼을 바로 접으면 높이가 0이 된 뒤 남은 여백이 한꺼번에 사라져 끊겨 보인다.
        LinearLayout fnEdit = new LinearLayout(this);
        fnEdit.setOrientation(LinearLayout.VERTICAL);
        fnEdit.addView(fnEditButton());
        View fnToggle = fixedRow("Shift 키 대신 Fn 키 사용",
                "한글 자판의 Shift 자리에 Fn 키를 놓습니다. NewSwipe 단모음 배열에는 Shift 자리가 없어 나타나지 않습니다.",
                prefs.koreanShiftFn(), "Shift 키 대신 Fn 키 사용",
                on -> {
                    prefs.raw().edit().putBoolean(Prefs.KOREAN_SHIFT_FN, on).apply();
                    Ui.setVisibleAnimated(fnEdit, on);
                });
        // 하위 항목은 왼쪽의 강조색 선이 들여쓰기를 대신하므로 줄 자체의 왼쪽 여백은 없앤다.
        fnToggle.setPadding(0, fnToggle.getPaddingTop(), fnToggle.getPaddingRight(), fnToggle.getPaddingBottom());
        View fnGroup = subGroup(16, fnToggle, fnEdit);   // 카드 안 줄의 왼쪽 여백(10dp) + 설정 화면의 들여쓰기(6dp)
        // NewSwipe 단모음에는 Shift 자리가 없어, 꺼진 채 흐리게 두고 바꿀 수 없게 한다 (저장된 값은 건드리지 않는다).
        boolean noShiftSlot = prefs.koreanNewSwipe();
        View shiftRow = fixedRow("Shift 키 (⇧)",
                noShiftSlot ? "이 레이아웃에서는 사용할 수 없습니다." : "꺼도 한글 자판에서만 사라집니다.",
                !noShiftSlot && !prefs.koreanShiftHidden(),
                on -> {
                    prefs.raw().edit().putBoolean(Prefs.KOREAN_SHIFT_HIDDEN, !on).apply();
                    Ui.setVisibleAnimated(fnGroup, !on);
                });
        if (noShiftSlot) setRowDisabled(shiftRow);
        fixed.addView(shiftRow);
        fnGroup.setVisibility(!noShiftSlot && prefs.koreanShiftHidden() ? View.VISIBLE : View.GONE);
        fnEdit.setVisibility(prefs.koreanShiftFn() ? View.VISIBLE : View.GONE);
        fixed.addView(fnGroup);
        fixed.addView(fixedRow("지우기 키 (⌫)", null,
                !prefs.deleteKeyHidden(),
                on -> prefs.raw().edit().putBoolean(Prefs.DELETE_KEY_HIDDEN, !on).apply()));
        list.addView(ResetDialog.button(this, "기능키 순서·유무 사용자화", () -> {
            prefs.resetBottomKeys();
            render();
        }));
    }

    /** Fn 키의 탭·길게 누르기·밀기 기능을 정하는 화면으로 가는 버튼. 설정 화면의 하위 버튼과 같은 모양이다. */
    private View fnEditButton() {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        int h = Ui.dp(this, 14);
        row.setPadding(h, Ui.dp(this, 12), h, Ui.dp(this, 12));
        row.setBackground(Ui.ripple(accentColor & 0x00FFFFFF | 0x33000000,
                Ui.round(accentColor & 0x00FFFFFF | 0x1F000000, Ui.dp(this, 12)), Ui.dp(this, 12)));
        row.addView(text("Fn 키 사용자화", 15, accentColor),
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        row.addView(new Chevron(this, accentColor));
        row.setClickable(true);
        row.setFocusable(true);
        row.setOnClickListener(v -> startActivity(SwipeActionEditorActivity.intent(this,
                com.alternative_studios.newswipe.keyboard.FnKeyActions.SPEC)));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, Ui.dp(this, 6), Ui.dp(this, 10), Ui.dp(this, 6));
        row.setLayoutParams(lp);
        return row;
    }

    /** 설정 화면과 같은 하위 항목 묶음: 왼쪽에 옅은 강조색 세로선을 두고 그 오른쪽에 항목들을 놓는다. */
    private View subGroup(int leftMarginDp, View... children) {
        LinearLayout group = new LinearLayout(this);
        group.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams glp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        glp.leftMargin = Ui.dp(this, leftMarginDp);
        group.setLayoutParams(glp);

        View bar = new View(this);
        bar.setBackground(Ui.round((accentColor & 0x00FFFFFF) | 0x66000000, Ui.dp(this, 2)));
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(Ui.dp(this, 3),
                ViewGroup.LayoutParams.MATCH_PARENT);
        blp.topMargin = Ui.dp(this, 6);
        blp.bottomMargin = Ui.dp(this, 6);
        group.addView(bar, blp);

        LinearLayout column = new LinearLayout(this);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setPadding(Ui.dp(this, 14), 0, 0, 0);
        for (View child : children) column.addView(child);
        group.addView(column, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        return group;
    }

    /** fixedRow로 만든 줄을 쓸 수 없는 상태로: 글자를 흐리게 하고 스위치를 끈다. */
    private static void setRowDisabled(View row) {
        ViewGroup g = (ViewGroup) row;
        g.getChildAt(0).setAlpha(0.5f);   // 이름·설명
        g.getChildAt(g.getChildCount() - 1).setEnabled(false);   // 스위치
    }

    /** 순서를 바꾸지 않는 키(Shift 키·⌫)의 줄: 이름·설명과 켜고 끄기. */
    private View fixedRow(String label, String hint, boolean shown, java.util.function.Consumer<Boolean> onChange) {
        return fixedRow(label, hint, shown, label + " 표시", onChange);
    }

    private View fixedRow(String label, String hint, boolean shown, String description,
                          java.util.function.Consumer<Boolean> onChange) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(Ui.dp(this, 10), Ui.dp(this, 8), Ui.dp(this, 10), Ui.dp(this, 8));
        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        texts.addView(text(label, 16, textColor));
        if (hint != null) texts.addView(text(hint, 12, hintColor));
        row.addView(texts, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        ExpressiveSwitch sw = new ExpressiveSwitch(this, accentColor, onAccentColor, hintColor, card);
        sw.setCheckedImmediately(shown);
        sw.setContentDescription(description);
        sw.setOnCheckedChangeListener(checked -> {
            onChange.accept(checked);
            updatePreview();
        });
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        slp.leftMargin = Ui.dp(this, 8);
        row.addView(sw, slp);
        return row;
    }

    /** 키 한 줄: 이름·설명, 켜고 끄기, 끌어서 옮기는 손잡이(≡). */
    private View keyRow(String id) {
        LinearLayout row = new LinearLayout(this);
        row.setTag(id);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(Ui.dp(this, 10), Ui.dp(this, 8), 0, Ui.dp(this, 8));
        row.setBackground(Ui.round(card, Ui.dp(this, 12)));   // 끌어 올렸을 때 아래 줄이 비치지 않게
        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        texts.addView(text(BottomKeys.label(id), 16, textColor));
        String hint = BottomKeys.hint(id);
        if (hint != null) texts.addView(text(hint, 12, hintColor));
        row.addView(texts, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        ExpressiveSwitch sw = new ExpressiveSwitch(this, accentColor, onAccentColor, hintColor, card);
        sw.setCheckedImmediately(prefs.bottomKeyShown(id));
        sw.setContentDescription(BottomKeys.label(id) + " 표시");
        sw.setOnCheckedChangeListener(checked -> {
            if (!checked && shownCount() <= 1) {
                // 맨 아래 줄이 비지 않도록 마지막 키는 끌 수 없다.
                Toast.makeText(this, "적어도 한 키는 남겨 두어야 합니다", Toast.LENGTH_SHORT).show();
                sw.post(sw::toggle);
                return;
            }
            prefs.setBottomKeyShown(id, checked);
            updatePreview();
        });
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        slp.leftMargin = Ui.dp(this, 8);
        row.addView(sw, slp);

        View handle = new DragHandle(this, hintColor);
        handle.setContentDescription(BottomKeys.label(id) + " 순서 바꾸기");
        handle.setOnTouchListener((v, ev) -> onHandleTouch(row, ev));
        // 끌 수 없는 화면 읽기(TalkBack) 사용자를 위해 '위로·아래로 옮기기' 동작도 둔다.
        handle.setAccessibilityDelegate(new View.AccessibilityDelegate() {
            @Override
            public void onInitializeAccessibilityNodeInfo(View host, android.view.accessibility.AccessibilityNodeInfo info) {
                super.onInitializeAccessibilityNodeInfo(host, info);
                info.addAction(new android.view.accessibility.AccessibilityNodeInfo.AccessibilityAction(
                        ACTION_MOVE_UP, "위로 옮기기"));
                info.addAction(new android.view.accessibility.AccessibilityNodeInfo.AccessibilityAction(
                        ACTION_MOVE_DOWN, "아래로 옮기기"));
            }

            @Override
            public boolean performAccessibilityAction(View host, int action, Bundle args) {
                if (action == ACTION_MOVE_UP) return moveBy(row, -1);
                if (action == ACTION_MOVE_DOWN) return moveBy(row, 1);
                return super.performAccessibilityAction(host, action, args);
            }
        });
        row.addView(handle, new LinearLayout.LayoutParams(Ui.dp(this, 44), Ui.dp(this, 44)));
        return row;
    }

    // ---------------------------------------------------------------- 끌어서 순서 바꾸기

    private static final int ACTION_MOVE_UP = 0x7E570001, ACTION_MOVE_DOWN = 0x7E570002;

    /** 줄을 한 칸 위(-1)나 아래(+1)로 옮기고 순서를 저장한다. */
    private boolean moveBy(View row, int delta) {
        if (dragging != null) return false;
        int from = keyList.indexOfChild(row), to = from + delta;
        if (from < 0 || to < 0 || to >= keyList.getChildCount()) return false;
        keyList.removeView(row);
        keyList.addView(row, to);
        saveOrder();
        return true;
    }

    private void saveOrder() {
        String[] ids = new String[keyList.getChildCount()];
        for (int i = 0; i < ids.length; i++) ids[i] = (String) keyList.getChildAt(i).getTag();
        prefs.setBottomKeyOrder(ids);
        updatePreview();
    }

    /** 끌기를 시작할 때의 줄 순서 (화면 배치 그대로). */
    private final java.util.List<View> dragRows = new java.util.ArrayList<>();
    private View dragging;
    private int dragFrom, dragTo;
    private float dragDownY;

    private boolean onHandleTouch(View row, android.view.MotionEvent ev) {
        switch (ev.getActionMasked()) {
            case android.view.MotionEvent.ACTION_DOWN:
                if (dragging != null) return false;
                dragging = row;
                dragRows.clear();
                for (int i = 0; i < keyList.getChildCount(); i++) dragRows.add(keyList.getChildAt(i));
                dragFrom = dragTo = dragRows.indexOf(row);
                dragDownY = ev.getRawY();
                keyList.getParent().requestDisallowInterceptTouchEvent(true);   // 끄는 동안 화면이 스크롤되지 않게
                // 크기는 그대로 두고 그림자만 띄워, 끌어 올린 줄의 좌우가 카드 밖으로 넘치지 않게 한다.
                row.animate().translationZ(Ui.dp(this, 8)).setDuration(120).start();
                row.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS);
                return true;
            case android.view.MotionEvent.ACTION_MOVE: {
                if (dragging != row) return false;
                // 목록 안에서만 움직이게 한다.
                float dy = ev.getRawY() - dragDownY;
                float minDy = dragRows.get(0).getTop() - row.getTop();
                float maxDy = dragRows.get(dragRows.size() - 1).getBottom() - row.getBottom();
                dy = Math.max(minDy, Math.min(maxDy, dy));
                row.setTranslationY(dy);
                // 끌고 있는 줄의 가운데가 지나간 줄 수만큼 자리를 옮긴다.
                float center = row.getTop() + dy + row.getHeight() / 2f;
                int to = 0;
                for (View other : dragRows) {
                    if (other == row) continue;
                    if (center > other.getTop() + other.getHeight() / 2f) to++;
                }
                if (to != dragTo) {
                    dragTo = to;
                    if (android.os.Build.VERSION.SDK_INT >= 27) {
                        row.performHapticFeedback(android.view.HapticFeedbackConstants.TEXT_HANDLE_MOVE);
                    }
                    layoutOthers();
                }
                return true;
            }
            case android.view.MotionEvent.ACTION_UP:
            case android.view.MotionEvent.ACTION_CANCEL:
                if (dragging != row) return false;
                drop();
                return true;
            default:
                return dragging == row;
        }
    }

    /** 끌고 있는 줄을 dragTo에 놓았다고 보고, 나머지 줄들을 그 순서의 자리로 미끄러뜨린다. */
    private void layoutOthers() {
        java.util.List<View> order = orderWithDrop();
        float top = dragRows.get(0).getTop();
        for (View v : order) {
            if (v != dragging) v.animate().translationY(top - v.getTop()).setDuration(150).start();
            top += v.getHeight();
        }
    }

    private java.util.List<View> orderWithDrop() {
        java.util.List<View> order = new java.util.ArrayList<>(dragRows);
        order.remove(dragging);
        order.add(dragTo, dragging);
        return order;
    }

    /** 손을 떼면 끌던 줄을 새 자리에 내려놓고, 순서를 저장한 뒤 목록을 그 순서로 다시 놓는다. */
    private void drop() {
        View row = dragging;
        java.util.List<View> order = orderWithDrop();
        float top = dragRows.get(0).getTop();
        for (View v : order) {
            if (v == row) break;
            top += v.getHeight();
        }
        row.animate().translationY(top - row.getTop()).translationZ(0).setDuration(150)
                .withEndAction(() -> {
                    keyList.removeAllViews();
                    for (View v : order) {
                        v.animate().cancel();
                        v.setTranslationY(0);
                        keyList.addView(v);
                    }
                    if (dragTo != dragFrom) saveOrder();
                    dragging = null;
                })
                .start();
    }

    /** 끌어서 옮기는 손잡이(≡): 가로줄 세 개. */
    private static final class DragHandle extends View {
        private final android.graphics.Paint paint = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);

        DragHandle(Context context, int color) {
            super(context);
            paint.setColor(color);
            paint.setStrokeWidth(Ui.dp(context, 2));
            paint.setStrokeCap(android.graphics.Paint.Cap.ROUND);
        }

        @Override
        protected void onDraw(android.graphics.Canvas c) {
            float cx = getWidth() / 2f, cy = getHeight() / 2f;
            float hw = Ui.dp(getContext(), 8), gap = Ui.dp(getContext(), 5);
            for (int i = -1; i <= 1; i++) c.drawLine(cx - hw, cy + i * gap, cx + hw, cy + i * gap, paint);
        }
    }

    private int shownCount() {
        int n = 0;
        for (String id : BottomKeys.DEFAULT_ORDER) if (prefs.bottomKeyShown(id)) n++;
        return n;
    }

    /** 실제 자판과 같은 계산으로 만든 한글 자판의 맨 아래 줄을 그린다. */
    private void updatePreview() {
        preview.setLayout(KeyboardLayout.bottomRowPreview(prefs));
    }
}
