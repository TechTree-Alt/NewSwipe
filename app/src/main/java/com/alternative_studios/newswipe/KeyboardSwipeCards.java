package com.alternative_studios.newswipe;

import android.app.Activity;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.alternative_studios.newswipe.keyboard.KeyboardSwipes;
import com.alternative_studios.newswipe.keyboard.KeyboardTheme;
import com.alternative_studios.newswipe.keyboard.SwipeAction;
import com.alternative_studios.newswipe.keyboard.SwipeSpec;
import com.alternative_studios.newswipe.ui.ExpressiveChoiceButton;
import com.alternative_studios.newswipe.ui.Ui;

/**
 * 키보드 밀기 (글자 키 설정의 '밀기' 섹션): 자판을 한 손가락·두 손가락으로 네 방향으로 밀 때 실행할 기능을 정한다 ({@link KeyboardSwipes}).
 * 손가락 수마다 카드 하나에 방향별 칸을 놓고, 칸을 누르면 고를 수 있는 기능 목록이 나온다.
 * (기능키·도구 막대 버튼·Fn 키의 밀기와 길게 누르기는 키보드 사용자화에서 그 키를 눌러 정한다: KeyEditPanel.)
 */
final class KeyboardSwipeCards {

    /** 카드 이름 (설정 화면 검색도 이 이름으로 찾는다). */
    static String title(int slot) {
        return slot == KeyboardSwipes.ONE ? L.t("한 손가락으로 밀기", "One-finger swipes")
                : L.t("두 손가락으로 밀기", "Two-finger swipes");
    }

    private final Activity a;
    private final Prefs prefs;
    private final SwipeSpec spec = KeyboardSwipes.SPEC;
    private final KeyboardTheme keyboardTheme;
    private final LinearLayout box;
    /** 카드 이름과 카드: 설정 화면 검색이 항목을 찾도록 알린다. */
    private final java.util.function.BiConsumer<TextView, View> mark;
    /** 칸마다 지난번에 그린 '기능이 정해졌는지': 바뀐 칸만 모양이 스프링으로 바뀌게 한다. */
    private final java.util.Map<String, Boolean> shown = new java.util.HashMap<>();

    private KeyboardSwipeCards(Activity a, Prefs prefs, java.util.function.BiConsumer<TextView, View> mark) {
        this.a = a;
        this.prefs = prefs;
        this.mark = mark;
        keyboardTheme = KeyboardTheme.of(a);
        box = new LinearLayout(a);
        box.setOrientation(LinearLayout.VERTICAL);
    }

    /** 한 손가락·두 손가락 카드를 담은 틀. 칸의 기능을 바꾸면 그 자리에서 다시 그린다. */
    static View create(Activity a, Prefs prefs, java.util.function.BiConsumer<TextView, View> mark) {
        KeyboardSwipeCards cards = new KeyboardSwipeCards(a, prefs, mark);
        cards.render();
        return cards.box;
    }

    private TextView text(String s, int sp, int color) {
        TextView t = new TextView(a);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color);
        return t;
    }

    private void render() {
        box.removeAllViews();
        for (int slot = 0; slot < spec.slotCount(); slot++) box.addView(slotCard(slot));
    }

    /** 칸 하나의 카드: 이름·설명과 방향별 칸. 실제 키보드와 비슷하게 키보드 배경색 위에 흰 칸을 놓는다. */
    private View slotCard(int slot) {
        LinearLayout card = new LinearLayout(a);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(Ui.round(keyboardTheme.background, Ui.dp(a, 16)));
        int p = Ui.dp(a, 8);
        card.setPadding(p, p, p, p);
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        clp.topMargin = Ui.dp(a, 12);
        card.setLayoutParams(clp);

        String hintText = spec.cardHint(slot);
        boolean hasHint = hintText != null && !hintText.isEmpty();
        TextView name = text(title(slot), 17, keyboardTheme.text);
        // 설명이 없으면 빈 줄을 만들지 않고, 설명이 주던 아래 여백만 이름에 준다.
        name.setPadding(Ui.dp(a, 6), Ui.dp(a, 2), 0, hasHint ? 0 : Ui.dp(a, 4));
        card.addView(name);
        if (mark != null) mark.accept(name, card);
        if (hasHint) {
            TextView hint = text(hintText, 11, keyboardTheme.hint);
            hint.setPadding(Ui.dp(a, 6), 0, Ui.dp(a, 6), Ui.dp(a, 4));
            card.addView(hint);
        }

        // Material 3 Expressive 연결된 버튼 그룹: 기능이 정해진 칸은 강조색 알약, 나머지는 안쪽 모서리만 작게 둥근 칸.
        LinearLayout row = new LinearLayout(a);
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
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, Ui.dp(a, 76), 1f);
        lp.setMargins(Ui.dp(a, 1), Ui.dp(a, 2.5f), Ui.dp(a, 1), Ui.dp(a, 2.5f));
        return lp;
    }

    private ExpressiveChoiceButton cell(int slot, int dir, boolean first, boolean last) {
        String action = spec.get(prefs, slot, dir);
        boolean assigned = !SwipeAction.NONE.equals(action);
        String key = slot + ":" + dir;
        boolean was = shown.containsKey(key) ? shown.get(key) : assigned;
        shown.put(key, assigned);
        ExpressiveChoiceButton cell = new ExpressiveChoiceButton(a, SwipeAction.shortLabel(action), was, first, last,
                keyboardTheme.accent, keyboardTheme.onAccent, keyboardTheme.hint, keyboardTheme.hint);
        cell.setIdleColors(keyboardTheme.key, keyboardTheme.keyPressed);
        cell.setSubLabel(spec.dirLabel(slot, dir));
        cell.setOnClickListener(v -> edit(slot, dir));
        if (was != assigned) cell.post(() -> cell.setChosen(assigned, true));   // 방금 바뀐 칸
        return cell;
    }

    private void edit(int slot, int dir) {
        ActionPicker.show(a, spec.dialogTitle(slot, dir), spec.choices(slot, dir), spec.get(prefs, slot, dir),
                id -> {
                    spec.set(prefs, slot, dir, id);
                    render();
                },
                () -> {
                    spec.reset(prefs, slot, dir);
                    render();
                });
    }
}
