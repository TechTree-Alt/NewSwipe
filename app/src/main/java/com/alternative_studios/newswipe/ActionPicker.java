package com.alternative_studios.newswipe;

import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Typeface;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.alternative_studios.newswipe.keyboard.SwipeAction;
import com.alternative_studios.newswipe.ui.Ui;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;

/**
 * 밀기·누르기·길게 누르기에 줄 기능을 고르는 창. 기능이 많아져 한 줄 목록으로는 찾기 어려우므로
 * {@link SwipeAction}의 갈래(커서 이동, 선택·복사·붙여넣기, 지우기 등)로 묶어 보여 준다.
 * 위의 갈래 칩을 누르면 그 갈래로 바로 넘어가고, 목록을 넘기면 지금 보이는 갈래의 칩이 강조된다.
 * 뷰 몇 개만 쓰는 가벼운 창이며, 모양은 설정 화면(강조색 옅은 바탕, 둥근 칩)과 같은 규칙을 따른다.
 */
final class ActionPicker {
    private ActionPicker() {
    }

    /**
     * @param ids     고를 수 있는 기능 ID들 (SwipeSpec.choices). 이 안에 있는 것만 보인다.
     * @param current 지금 정해진 기능 ID
     * @param onPick  기능을 고르면 그 ID로 부른다 (창은 저절로 닫힌다)
     * @param onReset '기본값'을 누르면 부른다
     */
    static AlertDialog show(Context activity, CharSequence title, String[] ids, String current,
                            Consumer<String> onPick, Runnable onReset) {
        Context c = AppTheme.dialogContext(activity);
        AppTheme colors = AppTheme.of(activity);
        AlertDialog[] dialog = new AlertDialog[1];
        List<String> allowed = Arrays.asList(ids);

        LinearLayout root = new LinearLayout(c);
        root.setOrientation(LinearLayout.VERTICAL);

        ScrollView scroll = new ScrollView(c);
        scroll.setVerticalFadingEdgeEnabled(true);
        LinearLayout list = new LinearLayout(c);
        list.setOrientation(LinearLayout.VERTICAL);
        int side = Ui.dp(c, 12);
        list.setPadding(side, 0, side, Ui.dp(c, 8));
        scroll.addView(list);

        View[] picked = new View[1];
        View.OnClickListener choose = v -> {
            String id = (String) v.getTag();
            if (android.os.Build.VERSION.SDK_INT >= 30) {
                v.performHapticFeedback(android.view.HapticFeedbackConstants.CONFIRM);   // 닫히기 전에
            }
            if (dialog[0] != null) dialog[0].dismiss();
            onPick.accept(id);
        };

        // 갈래마다 머리글과 기능 줄. 고를 것이 하나도 없는 갈래는 칩도 머리글도 만들지 않는다.
        List<TextView> headers = new ArrayList<>();
        List<String> names = new ArrayList<>();
        List<String> placed = new ArrayList<>();
        for (int cat = 0; cat < SwipeAction.categoryCount(); cat++) {
            List<String> inCat = new ArrayList<>();
            for (String id : SwipeAction.categoryIds(cat)) if (allowed.contains(id)) inCat.add(id);
            if (inCat.isEmpty()) continue;
            if (!headers.isEmpty()) list.addView(divider(c, colors));
            TextView header = header(c, colors, SwipeAction.categoryName(cat), headers.isEmpty());
            list.addView(header);
            headers.add(header);
            names.add(SwipeAction.categoryName(cat));
            for (String id : inCat) {
                View row = row(c, colors, id, id.equals(current), choose);
                if (id.equals(current)) picked[0] = row;
                list.addView(row);
                placed.add(id);
            }
        }
        // 갈래에 아직 넣지 않은 기능이 있어도 잃어버리지 않게 '기타'로 보인다.
        List<String> rest = new ArrayList<>();
        for (String id : ids) if (!placed.contains(id)) rest.add(id);
        if (!rest.isEmpty()) {
            if (!headers.isEmpty()) list.addView(divider(c, colors));
            TextView header = header(c, colors, L.t("기타", "Other"), headers.isEmpty());
            list.addView(header);
            headers.add(header);
            names.add(L.t("기타", "Other"));
            for (String id : rest) {
                View row = row(c, colors, id, id.equals(current), choose);
                if (id.equals(current)) picked[0] = row;
                list.addView(row);
            }
        }

        // 갈래 칩 줄 (가로로 넘긴다).
        TextView[] chips = new TextView[headers.size()];
        HorizontalScrollView chipScroll = new HorizontalScrollView(c);
        chipScroll.setHorizontalScrollBarEnabled(false);
        chipScroll.setClipToPadding(false);
        LinearLayout chipRow = new LinearLayout(c);
        chipRow.setPadding(Ui.dp(c, 16), Ui.dp(c, 12), Ui.dp(c, 16), Ui.dp(c, 10));
        chipScroll.addView(chipRow);
        int[] active = {-1};
        // 칩을 눌러 넘어가는 동안에는 그 칩을 그대로 둔다 (짧은 마지막 갈래 때문에 다른 칩으로 바뀌지 않게). 목록을 만지면 풀린다.
        boolean[] pinned = {false};
        Runnable[] mark = new Runnable[1];
        for (int i = 0; i < chips.length; i++) {
            int index = i;
            TextView chip = chip(c, names.get(i));
            chip.setOnClickListener(v -> {
                pinned[0] = true;
                scroll.smoothScrollTo(0, headers.get(index).getTop());
                active[0] = index;
                mark[0].run();
            });
            chips[i] = chip;
            chipRow.addView(chip);
        }
        mark[0] = () -> {
            for (int i = 0; i < chips.length; i++) styleChip(c, colors, chips[i], i == active[0]);
            if (active[0] >= 0) {
                View on = chips[active[0]];
                int left = on.getLeft() - Ui.dp(c, 16);
                int right = on.getRight() + Ui.dp(c, 16) - chipScroll.getWidth();
                int x = chipScroll.getScrollX();
                if (left < x) chipScroll.smoothScrollTo(left, 0);
                else if (right > x) chipScroll.smoothScrollTo(right, 0);
            }
        };
        // 목록을 넘기면, 화면 위쪽에 걸친 갈래를 칩에 표시한다. 맨 아래에 닿으면 마지막 갈래.
        scroll.setOnTouchListener((v, e) -> {
            if (e.getActionMasked() == android.view.MotionEvent.ACTION_DOWN) pinned[0] = false;
            return false;
        });
        scroll.setOnScrollChangeListener((v, x, y, ox, oy) -> {
            if (pinned[0]) return;
            int now = 0;
            int line = y + Ui.dp(c, 24);
            for (int i = 0; i < headers.size(); i++) if (headers.get(i).getTop() <= line) now = i;
            if (!scroll.canScrollVertically(1) && y > 0) now = headers.size() - 1;
            if (now != active[0]) {
                active[0] = now;
                mark[0].run();
            }
        });
        active[0] = headers.isEmpty() ? -1 : 0;
        if (chips.length > 1) root.addView(chipScroll, SettingsWidgets.matchWrap());
        root.addView(scroll, SettingsWidgets.matchWrap());

        dialog[0] = AppTheme.accentBuilder(c)
                .setTitle(title)
                .setView(root)
                .setNeutralButton(L.t("기본값", "Default"), (d, w) -> onReset.run())
                .setNegativeButton(L.t("취소", "Cancel"), null)
                .create();
        // 지금 고른 기능이 처음부터 보이게 (첫 배치가 끝난 뒤 그 줄이 위에서 조금 아래에 오도록).
        scroll.post(() -> {
            if (picked[0] != null) {
                int target = picked[0].getTop() - Ui.dp(c, 56);
                scroll.scrollTo(0, Math.max(0, target));
            }
            mark[0].run();
        });
        mark[0].run();
        dialog[0].show();
        return dialog[0];
    }

    /** 갈래 머리글: 강조색의 작은 글씨 (설정 화면의 묶음 제목처럼). */
    private static TextView header(Context c, AppTheme colors, String name, boolean first) {
        TextView t = new TextView(c);
        t.setText(name);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        t.setTextColor(colors.accent);
        t.setPadding(Ui.dp(c, 12), Ui.dp(c, first ? 4 : 12), Ui.dp(c, 12), Ui.dp(c, 4));
        t.setAccessibilityHeading(true);
        return t;
    }

    /** 갈래 사이의 가는 구분선 (글자색을 옅게 깐 선). */
    private static View divider(Context c, AppTheme colors) {
        View v = new View(c);
        v.setBackgroundColor((colors.text & 0x00FFFFFF) | 0x26000000);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Math.max(1, Ui.dp(c, 1)));
        lp.topMargin = Ui.dp(c, 8);
        lp.leftMargin = lp.rightMargin = Ui.dp(c, 12);
        v.setLayoutParams(lp);
        v.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        return v;
    }

    /** 이 창에서 보이는 기능 이름: '없음'만 뜻이 더 잘 드러나게 풀어 쓴다 (칩이나 메뉴에는 그대로 '없음'). */
    static String pickerLabel(String id) {
        return SwipeAction.NONE.equals(id) ? L.t("아무 기능 안 함", "Do nothing") : SwipeAction.label(id);
    }

    /** 기능 한 줄. 지금 고른 줄은 강조색을 옅게 깐 둥근 바탕에 강조색 글씨와 ✓. */
    private static View row(Context c, AppTheme colors, String id, boolean selected, View.OnClickListener onClick) {
        LinearLayout row = new LinearLayout(c);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setMinimumHeight(Ui.dp(c, 52));
        row.setPadding(Ui.dp(c, 12), Ui.dp(c, 6), Ui.dp(c, 12), Ui.dp(c, 6));
        float r = Ui.dp(c, 14);
        int press = Ui.tint(colors.text);
        row.setBackground(Ui.ripple(press, selected ? Ui.round(Ui.tint(colors.accent), r) : null, r));
        row.setTag(id);
        row.setOnClickListener(onClick);
        row.setSelected(selected);

        TextView label = new TextView(c);
        label.setText(pickerLabel(id));
        label.setTextSize(TypedValue.COMPLEX_UNIT_SP, 17);
        label.setTextColor(selected ? colors.accent : colors.text);
        if (selected) label.setTypeface(Typeface.DEFAULT_BOLD);
        row.addView(label, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        if (selected) {
            TextView check = new TextView(c);
            check.setText("✓");
            check.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
            check.setTypeface(Typeface.DEFAULT_BOLD);
            check.setTextColor(colors.accent);
            check.setPadding(Ui.dp(c, 8), 0, 0, 0);
            check.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            row.addView(check);
            row.setContentDescription(pickerLabel(id) + L.t(", 선택됨", ", selected"));
        }
        return row;
    }

    private static TextView chip(Context c, String name) {
        TextView t = new TextView(c);
        t.setText(name);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        t.setGravity(Gravity.CENTER);
        t.setSingleLine(true);
        t.setEllipsize(TextUtils.TruncateAt.END);
        t.setPadding(Ui.dp(c, 14), 0, Ui.dp(c, 14), 0);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, Ui.dp(c, 32));
        lp.rightMargin = Ui.dp(c, 6);
        t.setLayoutParams(lp);
        return t;
    }

    /** 칩 모양: 지금 갈래는 강조색 알약에 강조색 위 글씨, 나머지는 글자색을 옅게 깐 알약. */
    private static void styleChip(Context c, AppTheme colors, TextView chip, boolean on) {
        float r = Ui.chipRadius(c);
        int fill = on ? colors.accent : Ui.tint(colors.text);
        chip.setTextColor(on ? colors.onAccent : colors.text);
        chip.setTypeface(on ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
        chip.setBackground(Ui.ripple(Ui.tint(on ? colors.onAccent : colors.text), Ui.round(fill, r), r));
        chip.setSelected(on);
    }
}
