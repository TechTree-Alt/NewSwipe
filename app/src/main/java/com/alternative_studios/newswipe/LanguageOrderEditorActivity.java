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

import com.alternative_studios.newswipe.cjk.CjkPack;
import com.alternative_studios.newswipe.ui.Ui;

import java.util.ArrayList;
import java.util.List;

/**
 * '🌐︎을 위로 밀어 언어 고르기'의 말풍선에 놓을 언어 차례를 정하는 화면 (밀어서 기능 › 기능키 › 언어 순서 사용자화).
 * 한국어·영어와, 실험실에서 켜고 사전 팩이 있는 일본어·중국어를 끌어서 옮긴다. 말풍선은 이 차례대로 위에서 아래로 보인다.
 * 화면 구성과 끌어서 옮기기는 '기능키 순서·유무 사용자화'(BottomKeysEditorActivity)와 같다.
 */
public final class LanguageOrderEditorActivity extends Activity {

    private static final String TITLE = "언어 순서 사용자화";
    private Prefs prefs;
    private LinearLayout list, langList;
    private String themeSignature;
    private int bg, card, textColor, hintColor;

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(AppTheme.wrap(base));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = new Prefs(this);
        AppTheme colors = AppTheme.of(this);
        themeSignature = AppTheme.signature(this);
        bg = colors.bg;
        card = colors.card;
        textColor = colors.text;
        hintColor = colors.hint;

        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(bg);
        scroll.setFillViewport(true);
        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        int pad = Ui.dp(this, 16);
        list.setPadding(pad, 0, pad, Ui.dp(this, 32));
        scroll.addView(list);
        setContentView(new SettingsFrame(this, TITLE, getString(R.string.app_name)).wrap(scroll));
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

    /** 말풍선에 나올 수 있는 언어인지: 한국어·영어는 늘, 일본어·중국어는 실험실에서 켜고 사전 팩이 있을 때. */
    private boolean available(String id) {
        if ("ko".equals(id) || "en".equals(id)) return true;
        return prefs.cjkEnabled(id) && CjkPack.installed(getFilesDir(), id) != null;
    }

    private static String name(String id) {
        switch (id) {
            case "ko": return "한국어";
            case "en": return "영어 (English)";
            case "ja": return "일본어 (日本語)";
            default: return "중국어 (中文)";
        }
    }

    private void render() {
        list.removeAllViews();
        TextView note = text("오른쪽의 ≡를 잡고 끌어 순서를 바꿀 수 있습니다. 🌐︎을 위로 밀면 이 순서대로 위에서 아래로 언어가 놓입니다.",
                13, hintColor);
        note.setPadding(Ui.dp(this, 4), 0, 0, Ui.dp(this, 4));
        list.addView(note);

        LinearLayout langs = new LinearLayout(this);
        langList = langs;
        langs.setOrientation(LinearLayout.VERTICAL);
        langs.setBackground(Ui.round(card, Ui.dp(this, 16)));
        int inset = Ui.dp(this, 6);
        langs.setPadding(inset, Ui.dp(this, 4), inset, Ui.dp(this, 4));
        langs.setClipChildren(false);
        langs.setClipToPadding(false);
        list.setClipChildren(false);
        LinearLayout.LayoutParams llp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        llp.topMargin = Ui.dp(this, 12);
        list.addView(langs, llp);
        for (String id : prefs.languageOrder()) if (available(id)) langs.addView(langRow(id));

        if (!available("ja") || !available("zh")) {
            TextView more = text("일본어·중국어는 설정 › 실험실에서 사전 팩을 가져와 켜면 여기에 나타납니다.", 13, hintColor);
            more.setPadding(Ui.dp(this, 4), Ui.dp(this, 10), 0, 0);
            list.addView(more);
        }
        list.addView(ResetDialog.button(this, TITLE, () -> {
            prefs.resetLanguageOrder();
            render();
        }));
    }

    /** 언어 한 줄: 이름과 끌어서 옮기는 손잡이(≡). */
    private View langRow(String id) {
        LinearLayout row = new LinearLayout(this);
        row.setTag(id);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(Ui.dp(this, 10), Ui.dp(this, 8), 0, Ui.dp(this, 8));
        row.setBackground(Ui.round(card, Ui.dp(this, 12)));   // 끌어 올렸을 때 아래 줄이 비치지 않게
        row.addView(text(name(id), 16, textColor), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        View handle = new DragHandle(this, hintColor);
        handle.setContentDescription(name(id) + " 순서 바꾸기");
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

    private boolean moveBy(View row, int delta) {
        if (dragging != null) return false;
        int from = langList.indexOfChild(row), to = from + delta;
        if (from < 0 || to < 0 || to >= langList.getChildCount()) return false;
        langList.removeView(row);
        langList.addView(row, to);
        saveOrder();
        return true;
    }

    /**
     * 화면에 보이는 차례를 저장한다. 보이지 않는 언어(켜지 않은 일본어·중국어)는 원래 자리를 지켜,
     * 나중에 켜면 예전에 정한 자리에 나온다.
     */
    private void saveOrder() {
        List<String> shown = new ArrayList<>();
        for (int i = 0; i < langList.getChildCount(); i++) shown.add((String) langList.getChildAt(i).getTag());
        String[] old = prefs.languageOrder();
        String[] out = new String[old.length];
        int next = 0;
        for (int i = 0; i < old.length; i++) out[i] = shown.contains(old[i]) ? shown.get(next++) : old[i];
        prefs.setLanguageOrder(out);
    }

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
                for (int i = 0; i < langList.getChildCount(); i++) dragRows.add(langList.getChildAt(i));
                dragFrom = dragTo = dragRows.indexOf(row);
                dragDownY = ev.getRawY();
                langList.getParent().requestDisallowInterceptTouchEvent(true);   // 끄는 동안 화면이 스크롤되지 않게
                row.animate().translationZ(Ui.dp(this, 8)).setDuration(120).start();
                row.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS);
                return true;
            case android.view.MotionEvent.ACTION_MOVE: {
                if (dragging != row) return false;
                float dy = ev.getRawY() - dragDownY;
                float minDy = dragRows.get(0).getTop() - row.getTop();
                float maxDy = dragRows.get(dragRows.size() - 1).getBottom() - row.getBottom();
                dy = Math.max(minDy, Math.min(maxDy, dy));
                row.setTranslationY(dy);
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
                    langList.removeAllViews();
                    for (View v : order) {
                        v.animate().cancel();
                        v.setTranslationY(0);
                        langList.addView(v);
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

    @Override
    public void onBackPressed() {
        if (!SettingsFrame.consumeBack(this)) super.onBackPressed();
    }
}
