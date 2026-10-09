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

import com.alternative_studios.newswipe.keyboard.KeyboardTheme;
import com.alternative_studios.newswipe.keyboard.ToolbarButtons;
import com.alternative_studios.newswipe.ui.ExpressiveSwitch;
import com.alternative_studios.newswipe.ui.IconButton;
import com.alternative_studios.newswipe.ui.Ui;

import java.util.ArrayList;
import java.util.List;

/**
 * 도구 막대 버튼을 켜고 끄고, 왼쪽·오른쪽 중 어디에 어떤 순서로 둘지 바꾸는 화면 (도구 막대 버튼 순서·유무 사용자화).
 * 한 카드 안에 '왼쪽'·'오른쪽' 제목을 두고, ≡를 잡고 끌면 제목을 넘어 다른 쪽으로도 옮길 수 있다.
 */
public final class ToolbarButtonsEditorActivity extends Activity {

    private Prefs prefs;
    private LinearLayout list, keyList, preview;
    /** 목록 안의 '오른쪽' 제목. 이보다 위의 줄은 왼쪽, 아래의 줄은 오른쪽 버튼이다. */
    private View rightHeader;
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
        setContentView(new SettingsFrame(this, "도구 막대 버튼 순서·유무 사용자화", getString(R.string.app_name)).wrap(scroll));
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
        TextView note = text("버튼을 켜고 끄거나, 오른쪽의 ≡를 잡고 끌어 순서를 바꿉니다.", 13, hintColor);
        note.setPadding(Ui.dp(this, 4), 0, 0, Ui.dp(this, 4));
        list.addView(note);

        // 미리보기: 키보드 색으로 그린 도구 막대. 눌러도 반응하지 않는다.
        preview = new LinearLayout(this);
        preview.setGravity(Gravity.CENTER_VERTICAL);
        preview.setBackground(Ui.round(keyboardTheme.background, Ui.dp(this, 16)));
        preview.setPadding(Ui.dp(this, prefs.padLeftDp()), Ui.dp(this, 4), Ui.dp(this, prefs.padRightDp()), Ui.dp(this, 4));
        preview.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
        LinearLayout.LayoutParams plp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        plp.topMargin = Ui.dp(this, 12);
        list.addView(preview, plp);

        // 버튼 목록: '왼쪽' 제목, 왼쪽 버튼들, '오른쪽' 제목, 오른쪽 버튼들
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
        String[][] order = prefs.toolOrder();
        keys.addView(header("왼쪽", false));
        for (String id : order[0]) keys.addView(buttonRow(id));
        rightHeader = header("오른쪽", true);
        keys.addView(rightHeader);
        for (String id : order[1]) keys.addView(buttonRow(id));
        updatePreview();
        list.addView(ResetDialog.button(this, "도구 막대 버튼 순서·유무 사용자화", () -> {
            prefs.resetToolButtons();
            render();
        }));
    }

    /** 목록 안의 구역 제목 ('왼쪽'·'오른쪽'). 끌던 줄이 지나가면 함께 밀려나지만 직접 잡을 수는 없다. */
    private View header(String label, boolean divided) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        if (divided) {
            View line = new View(this);
            line.setBackgroundColor(hintColor & 0x00FFFFFF | 0x33000000);
            LinearLayout.LayoutParams llp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                    Math.max(1, Ui.dp(this, 1)));
            llp.leftMargin = llp.rightMargin = Ui.dp(this, 10);
            llp.topMargin = Ui.dp(this, 6);
            box.addView(line, llp);
        }
        TextView t = text(label, 13, accentColor);
        t.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        t.setPadding(Ui.dp(this, 10), Ui.dp(this, divided ? 10 : 8), Ui.dp(this, 10), Ui.dp(this, 4));
        box.addView(t);
        box.setBackgroundColor(card);   // 끌던 줄이 지나갈 때 아래 줄이 비치지 않게
        return box;
    }

    /** 버튼 한 줄: 이름·설명, 켜고 끄기, 끌어서 옮기는 손잡이(≡). */
    private View buttonRow(String id) {
        LinearLayout row = new LinearLayout(this);
        row.setTag(id);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(Ui.dp(this, 10), Ui.dp(this, 8), 0, Ui.dp(this, 8));
        row.setBackground(Ui.round(card, Ui.dp(this, 12)));   // 끌어 올렸을 때 아래 줄이 비치지 않게
        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        texts.addView(text(ToolbarButtons.label(id), 16, textColor));
        String hint = ToolbarButtons.hint(id);
        if (hint != null) texts.addView(text(hint, 12, hintColor));
        row.addView(texts, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        ExpressiveSwitch sw = new ExpressiveSwitch(this, accentColor, onAccentColor, hintColor, card);
        sw.setCheckedImmediately(prefs.toolShown(id));
        sw.setContentDescription(ToolbarButtons.label(id) + " 표시");
        sw.setOnCheckedChangeListener(checked -> {
            prefs.setToolShown(id, checked);
            updatePreview();
        });
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        slp.leftMargin = Ui.dp(this, 8);
        row.addView(sw, slp);

        View handle = new DragHandle(this, hintColor);
        handle.setContentDescription(ToolbarButtons.label(id) + " 순서 바꾸기");
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

    private static final int ACTION_MOVE_UP = 0x7E570011, ACTION_MOVE_DOWN = 0x7E570012;

    /** 줄을 한 칸 위(-1)나 아래(+1)로 옮기고 저장한다. '오른쪽' 제목을 넘으면 다른 쪽으로 옮겨 간다. */
    private boolean moveBy(View row, int delta) {
        if (dragging != null) return false;
        int from = keyList.indexOfChild(row), to = from + delta;
        if (from < 0 || to < 1 || to >= keyList.getChildCount()) return false;   // 맨 위 '왼쪽' 제목 위로는 못 간다
        keyList.removeView(row);
        keyList.addView(row, to);
        saveOrder();
        return true;
    }

    /** 목록의 줄 순서대로 왼쪽·오른쪽 배치를 저장한다. */
    private void saveOrder() {
        List<String> left = new ArrayList<>(), right = new ArrayList<>();
        boolean isRight = false;
        for (int i = 0; i < keyList.getChildCount(); i++) {
            View v = keyList.getChildAt(i);
            if (v == rightHeader) isRight = true;
            else if (v.getTag() instanceof String) (isRight ? right : left).add((String) v.getTag());
        }
        prefs.setToolOrder(left.toArray(new String[0]), right.toArray(new String[0]));
        updatePreview();
    }

    /** 끌기를 시작할 때의 줄 순서 (맨 위 '왼쪽' 제목을 뺀 나머지, '오른쪽' 제목 포함). */
    private final List<View> dragRows = new ArrayList<>();
    private View dragging;
    private int dragFrom, dragTo;
    private float dragDownY;

    private boolean onHandleTouch(View row, android.view.MotionEvent ev) {
        switch (ev.getActionMasked()) {
            case android.view.MotionEvent.ACTION_DOWN:
                if (dragging != null) return false;
                dragging = row;
                dragRows.clear();
                for (int i = 1; i < keyList.getChildCount(); i++) dragRows.add(keyList.getChildAt(i));
                dragFrom = dragTo = dragRows.indexOf(row);
                dragDownY = ev.getRawY();
                keyList.getParent().requestDisallowInterceptTouchEvent(true);   // 끄는 동안 화면이 스크롤되지 않게
                row.animate().translationZ(Ui.dp(this, 8)).setDuration(120).start();
                row.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS);
                return true;
            case android.view.MotionEvent.ACTION_MOVE: {
                if (dragging != row) return false;
                // 끌고 있는 줄의 가운데가 맨 위 줄의 가운데 조금 위부터 맨 아래 줄의 가운데 조금 아래까지 움직이게 한다.
                // 줄의 위아래 끝으로 막으면, 줄보다 낮은 제목('오른쪽')이 맨 끝에 있을 때 줄의 가운데가 제목의 가운데를
                // 넘지 못해 빈 구역으로 옮길 수 없었다.
                float dy = ev.getRawY() - dragDownY;
                View first = dragRows.get(0), last = dragRows.get(dragRows.size() - 1);
                float rowCenter = row.getTop() + row.getHeight() / 2f;
                float minDy = first.getTop() + first.getHeight() / 2f - 1 - rowCenter;
                float maxDy = last.getTop() + last.getHeight() / 2f + 1 - rowCenter;
                dy = Math.max(minDy, Math.min(maxDy, dy));
                row.setTranslationY(dy);
                // 끌고 있는 줄의 가운데가 지나간 줄(제목 포함) 수만큼 자리를 옮긴다.
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
        List<View> order = orderWithDrop();
        float top = dragRows.get(0).getTop();
        for (View v : order) {
            if (v != dragging) v.animate().translationY(top - v.getTop()).setDuration(150).start();
            top += v.getHeight();
        }
    }

    private List<View> orderWithDrop() {
        List<View> order = new ArrayList<>(dragRows);
        order.remove(dragging);
        order.add(dragTo, dragging);
        return order;
    }

    /** 손을 떼면 끌던 줄을 새 자리에 내려놓고, 배치를 저장한 뒤 목록을 그 순서로 다시 놓는다. */
    private void drop() {
        View row = dragging;
        List<View> order = orderWithDrop();
        float top = dragRows.get(0).getTop();
        for (View v : order) {
            if (v == row) break;
            top += v.getHeight();
        }
        row.animate().translationY(top - row.getTop()).translationZ(0).setDuration(150)
                .withEndAction(() -> {
                    View first = keyList.getChildAt(0);   // '왼쪽' 제목
                    keyList.removeAllViews();
                    keyList.addView(first);
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

    /** 켜 둔 버튼만 지금 배치대로 키보드 색의 도구 막대에 그린다. */
    private void updatePreview() {
        preview.removeAllViews();
        String[][] order = prefs.toolOrder();
        for (String id : order[0]) addPreviewButton(id);
        preview.addView(new View(this), new LinearLayout.LayoutParams(0, 1, 1f));
        for (String id : order[1]) addPreviewButton(id);
    }

    private void addPreviewButton(String id) {
        if (!prefs.toolShown(id)) return;
        IconButton b = new IconButton(this, ToolbarButtons.icon(id), keyboardTheme.hint, keyboardTheme.keyPressed,
                ToolbarButtons.label(id));
        b.setClickable(false);
        b.setFocusable(false);
        preview.addView(b, new LinearLayout.LayoutParams(Ui.dp(this, 40), Ui.dp(this, 40)));
    }
}
