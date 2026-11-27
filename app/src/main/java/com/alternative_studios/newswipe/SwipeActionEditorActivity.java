package com.alternative_studios.newswipe;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.alternative_studios.newswipe.keyboard.KeyboardSwipes;
import com.alternative_studios.newswipe.keyboard.KeyboardTheme;
import com.alternative_studios.newswipe.keyboard.SwipeAction;
import com.alternative_studios.newswipe.keyboard.SwipeSpec;
import com.alternative_studios.newswipe.ui.ExpressiveChoiceButton;
import com.alternative_studios.newswipe.ui.Metro;
import com.alternative_studios.newswipe.ui.Ui;

/**
 * 키보드 밀기 편집 화면: 자판을 한 손가락·두 손가락으로 네 방향으로 밀 때 실행할 기능을 정한다 ({@link KeyboardSwipes}).
 * 손가락 수마다 카드 하나에 방향별 칸을 놓고, 칸을 누르면 고를 수 있는 기능 목록이 나온다.
 * (기능키·도구 막대 버튼·Fn 키의 밀기와 길게 누르기는 키보드 사용자화에서 그 키를 눌러 정한다: KeyEditPanel.)
 */
public final class SwipeActionEditorActivity extends Activity {

    private Prefs prefs;
    private final SwipeSpec spec = KeyboardSwipes.SPEC;
    private static String title() {
        return L.t("키보드 밀기 사용자화", "Customize keyboard swipes");
    }
    private LinearLayout list;
    private KeyboardTheme keyboardTheme;
    private String themeSignature;
    private int hintColor;
    /** 칸마다 지난번에 그린 '기능이 정해졌는지': 바뀐 칸만 모양이 스프링으로 바뀌게 한다. */
    private final java.util.Map<String, Boolean> shown = new java.util.HashMap<>();

    /** 키보드 밀기 편집 화면을 연다. */
    static Intent intent(Context context) {
        return new Intent(context, SwipeActionEditorActivity.class);
    }

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
        hintColor = colors.hint;

        ScrollView scroll = new ScrollView(this);
        Metro.scrollIndicator(scroll);   // Metro: WP8처럼 끄는 동안만 보이는 가는 표시
        scroll.setBackgroundColor(colors.bg);
        scroll.setFillViewport(true);
        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        int pad = Metro.pagePad(this, 16);
        list.setPadding(pad, 0, pad, Ui.dp(this, 32));
        scroll.addView(list);
        setContentView(new SettingsFrame(this, title(), getString(R.string.app_name)).wrap(scroll));
        render();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (!themeSignature.equals(AppTheme.signature(this))) SettingsFrame.recreateQuietly(this);   // 옛 색이 한 프레임 보이지 않게
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
        TextView note = text(L.t("자판을 한 손가락·두 손가락으로 위·아래·왼쪽·오른쪽으로 밀 때 실행할 기능을 고릅니다.", "Choose what happens when you swipe the keyboard up, down, left or right with one or two fingers."), 13, hintColor);
        note.setPadding(Ui.dp(this, 4), 0, 0, Ui.dp(this, 4));
        list.addView(note);
        for (int slot = 0; slot < spec.slotCount(); slot++) {
            list.addView(slotCard(slot));
        }
        list.addView(ResetDialog.button(this, title(), () -> {
            prefs.resetSwipeActions(spec.group);
            render();
        }));
    }

    /** 칸 하나의 카드: 이름·설명과 방향별 칸. 실제 키보드와 비슷하게 키보드 배경색 위에 흰 칸을 놓는다. */
    private View slotCard(int slot) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(Ui.round(keyboardTheme.background, Ui.dp(this, 16)));
        int p = Ui.dp(this, 8);
        card.setPadding(p, p, p, p);
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        clp.topMargin = Ui.dp(this, 12);
        card.setLayoutParams(clp);

        String hintText = spec.cardHint(slot);
        boolean hasHint = hintText != null && !hintText.isEmpty();
        TextView name = text(spec.cardTitle(slot), 17, keyboardTheme.text);
        // 설명이 없으면 빈 줄을 만들지 않고, 설명이 주던 아래 여백만 이름에 준다.
        name.setPadding(Ui.dp(this, 6), Ui.dp(this, 2), 0, hasHint ? 0 : Ui.dp(this, 4));
        card.addView(name);
        if (hasHint) {
            TextView hint = text(hintText, 11, keyboardTheme.hint);
            hint.setPadding(Ui.dp(this, 6), 0, Ui.dp(this, 6), Ui.dp(this, 4));
            card.addView(hint);
        }

        // Material 3 Expressive 연결된 버튼 그룹: 기능이 정해진 칸은 강조색 알약, 나머지는 안쪽 모서리만 작게 둥근 칸.
        LinearLayout row = new LinearLayout(this);
        row.setClipChildren(false);
        row.setBaselineAligned(false);   // 이름이 두 줄인 칸이 있어도 칸이 아래로 밀리지 않게
        int[] dirs = spec.dirs(slot);
        ExpressiveChoiceButton[] cells = new ExpressiveChoiceButton[dirs.length];
        for (int i = 0; i < dirs.length; i++) {
            cells[i] = cell(slot, dirs[i], i == 0, i == dirs.length - 1);
            row.addView(cells[i], cellParams());
        }
        for (int i = 0; i < cells.length; i++) {
            cells[i].setNeighbors(i > 0 ? cells[i - 1] : null, i + 1 < cells.length ? cells[i + 1] : null);
        }
        card.addView(row);
        return card;
    }

    private LinearLayout.LayoutParams cellParams() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, Ui.dp(this, 76), 1f);
        lp.setMargins(Ui.dp(this, 1), Ui.dp(this, 2.5f), Ui.dp(this, 1), Ui.dp(this, 2.5f));
        return lp;
    }

    private ExpressiveChoiceButton cell(int slot, int dir, boolean first, boolean last) {
        String action = spec.get(prefs, slot, dir);
        boolean assigned = !SwipeAction.NONE.equals(action);
        String key = slot + ":" + dir;
        boolean was = shown.containsKey(key) ? shown.get(key) : assigned;
        shown.put(key, assigned);
        ExpressiveChoiceButton cell = new ExpressiveChoiceButton(this, SwipeAction.shortLabel(action), was, first, last,
                keyboardTheme.accent, keyboardTheme.onAccent, keyboardTheme.hint, keyboardTheme.hint);
        cell.setIdleColors(keyboardTheme.key, keyboardTheme.keyPressed);
        cell.setSubLabel(spec.dirLabel(slot, dir));
        cell.setOnClickListener(v -> edit(slot, dir));
        if (was != assigned) cell.post(() -> cell.setChosen(assigned, true));   // 방금 바뀐 칸
        return cell;
    }

    private void edit(int slot, int dir) {
        ActionPicker.show(this, spec.dialogTitle(slot, dir), spec.choices(slot, dir), spec.get(prefs, slot, dir),
                id -> {
                    spec.set(prefs, slot, dir, id);
                    render();
                },
                () -> {
                    spec.reset(prefs, slot, dir);
                    render();
                });
    }

    /** 뒤로 가기: 검색창이나 키보드 시험 입력창이 열려 있으면 그것부터 닫고, 그다음에 이전 화면으로 간다 (Android 12 이하). */
    @Override
    public void onBackPressed() {
        if (!SettingsFrame.consumeBack(this) && !SettingsFrame.metroBack(this)) super.onBackPressed();
    }
}
