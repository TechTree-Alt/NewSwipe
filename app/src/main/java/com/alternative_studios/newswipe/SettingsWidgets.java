package com.alternative_studios.newswipe;

import android.content.Context;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.alternative_studios.newswipe.ui.Chevron;
import com.alternative_studios.newswipe.ui.ExpressiveSlider;
import com.alternative_studios.newswipe.ui.ExpressiveSwitch;
import com.alternative_studios.newswipe.ui.Ui;

import java.util.function.Consumer;
import java.util.function.IntConsumer;

/**
 * 설정 화면의 공통 조각: 소제목과 카드, 스위치 줄, 슬라이더, 하위 묶음, 설명, 버튼.
 * 설정 화면(SettingsActivity)과 키보드 사용자화의 키 창(KeyEditPanel)이 같은 모양을 쓰도록 한곳에서 만든다.
 * 글자 크기(항목 16sp, 소제목 14sp 강조색, 설명 13sp)는 설정 검색이 화면 글자를 모을 때 기대하는 값이라 바꾸지 않는다.
 */
final class SettingsWidgets {
    /** 항목 이름 글자와 그 항목 전체 줄을 알려 받는다 (설정 검색 결과로 들어왔을 때 그 줄을 깜빡이려고). 없으면 null. */
    interface ItemMarker {
        void mark(TextView label, View row);
    }

    /** 슬라이더 줄과, 바깥에서 값이 바뀌었을 때(손잡이·되돌리기) 모양을 맞추는 방법. */
    static final class Slider {
        final View view;
        private final TextView value;
        private final ExpressiveSlider bar;
        private final int min, max, step;
        private final String unit;

        Slider(View view, TextView value, ExpressiveSlider bar, int min, int max, int step, String unit) {
            this.view = view;
            this.value = value;
            this.bar = bar;
            this.min = min;
            this.max = max;
            this.step = step;
            this.unit = unit;
        }

        /** 사용자 입력 없이 값을 보여 준다 (콜백은 부르지 않는다). */
        void set(int v) {
            int c = Math.max(min, Math.min(max, v));
            value.setText(c + unit);
            bar.setProgress(Math.round((c - min) / (float) step));
        }
    }

    /** 이름과 지금 값을 보여 주는 줄 (누르면 값을 고르는 창이 뜬다). */
    static final class ValueRow {
        final View view;
        private final TextView value;

        ValueRow(View view, TextView value) {
            this.view = view;
            this.value = value;
        }

        void set(String v) {
            value.setText(v);
        }
    }

    private final Context c;
    private final int text, hint, accent, onAccent, card;
    private final ItemMarker marker;

    SettingsWidgets(Context context, AppTheme colors, ItemMarker marker) {
        this.c = context;
        this.text = colors.text;
        this.hint = colors.hint;
        this.accent = colors.accent;
        this.onAccent = colors.onAccent;
        this.card = colors.card;
        this.marker = marker;
    }

    private void mark(TextView label, View row) {
        if (marker != null) marker.mark(label, row);
    }

    static LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    TextView text(String s, float sp, int color) {
        TextView t = new TextView(c);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color);
        return t;
    }

    /** 설명 글 (작고 옅은 글자). */
    TextView note(String s) {
        TextView t = text(s, 13, hint);
        t.setLineSpacing(Ui.dp(c, 3), 1f);
        t.setPadding(0, Ui.dp(c, 6), 0, Ui.dp(c, 6));
        return t;
    }

    /** parent에 소제목과 빈 카드를 더하고 카드를 돌려준다. */
    LinearLayout section(LinearLayout parent, String name) {
        TextView h = text(name, 14, accent);
        h.setPadding(Ui.dp(c, 4), Ui.dp(c, 20), 0, Ui.dp(c, 8));
        parent.addView(h);
        LinearLayout box = card(parent);
        ((LinearLayout.LayoutParams) box.getLayoutParams()).topMargin = 0;
        return box;
    }

    /** parent에 빈 카드(둥근 바탕)를 더하고 돌려준다. */
    LinearLayout card(LinearLayout parent) {
        LinearLayout box = new LinearLayout(c);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setBackground(Ui.round(card, Ui.dp(c, 16)));
        int p = Ui.dp(c, 16);
        box.setPadding(p, Ui.dp(c, 8), p, Ui.dp(c, 8));
        LinearLayout.LayoutParams lp = matchWrap();
        lp.topMargin = Ui.dp(c, 8);
        parent.addView(box, lp);
        return box;
    }

    /** 이름·설명과 오른쪽 스위치. 줄 어디를 눌러도 스위치가 바뀐다. */
    View toggle(String label, String desc, boolean value, Consumer<Boolean> onChange) {
        LinearLayout row = new LinearLayout(c);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, Ui.dp(c, 8), 0, Ui.dp(c, 8));
        LinearLayout texts = new LinearLayout(c);
        texts.setOrientation(LinearLayout.VERTICAL);
        TextView t = text(label, 16, text);
        mark(t, row);
        texts.addView(t);
        if (desc != null) texts.addView(text(desc, 13, hint));
        row.addView(texts, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        ExpressiveSwitch sw = new ExpressiveSwitch(c, accent, onAccent, hint, card);
        sw.setCheckedImmediately(value);
        sw.setOnCheckedChangeListener(onChange::accept);
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        slp.leftMargin = Ui.dp(c, 12);
        row.addView(sw, slp);
        row.setOnClickListener(v -> sw.performClick());
        return row;
    }

    /** toggle()로 만든 줄의 스위치 (줄의 맨 오른쪽). */
    static ExpressiveSwitch switchOf(View toggleRow) {
        ViewGroup row = (ViewGroup) toggleRow;
        return (ExpressiveSwitch) row.getChildAt(row.getChildCount() - 1);
    }

    /**
     * 이름·값과 그 아래 슬라이더. 단위(step)에 맞지 않는 값(단위를 바꾸기 전에 저장한 값 등)은 가장 가까운 칸에 둔다.
     *
     * @param onUserChange 사용자가 값을 바꿀 때마다 새 값으로 부른다
     */
    Slider slider(String label, int value, int min, int max, int step, String unit, IntConsumer onUserChange) {
        LinearLayout box = new LinearLayout(c);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(0, Ui.dp(c, 8), 0, Ui.dp(c, 4));
        LinearLayout top = new LinearLayout(c);
        TextView t = text(label, 16, text);
        mark(t, box);
        top.addView(t, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView v = text(value + unit, 14, hint);
        top.addView(v);
        box.addView(top);
        ExpressiveSlider bar = new ExpressiveSlider(c, accent);
        bar.setMax((max - min) / step);
        bar.setProgress(Math.round((Math.max(min, Math.min(max, value)) - min) / (float) step));
        bar.setOnChangeListener((progress, fromUser) -> {
            int val = min + progress * step;
            v.setText(val + unit);
            if (fromUser) onUserChange.accept(val);
        });
        box.addView(bar, matchWrap());
        return new Slider(box, v, bar, min, max, step, unit);
    }

    /** 이름과 지금 값을 보여 주는 누를 수 있는 줄 (누르면 값을 고르는 창이 뜬다). */
    ValueRow valueRow(String label, String value, View.OnClickListener l) {
        LinearLayout row = new LinearLayout(c);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, Ui.dp(c, 12), 0, Ui.dp(c, 12));
        TextView t = text(label, 16, text);
        mark(t, row);
        row.addView(t, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        TextView v = text(value, 16, accent);
        v.setGravity(Gravity.END);
        v.setMaxLines(2);
        v.setEllipsize(android.text.TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams vlp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        vlp.leftMargin = Ui.dp(c, 12);
        vlp.rightMargin = Ui.dp(c, 6);
        row.addView(v, vlp);
        row.addView(new Chevron(c, accent));
        row.setBackground(Ui.ripple(hint & 0x00FFFFFF | 0x33000000, null, Ui.dp(c, 8)));
        row.setClickable(true);
        row.setFocusable(true);
        row.setOnClickListener(l);
        return new ValueRow(row, v);
    }

    /** 위 설정의 하위 설정들을 묶는다: 왼쪽 들여쓰기와 세로 안내선으로 위계를 보여 준다. */
    View subGroup(View... children) {
        LinearLayout group = new LinearLayout(c);
        group.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams glp = matchWrap();
        glp.leftMargin = Ui.dp(c, 6);
        group.setLayoutParams(glp);
        View line = new View(c);
        line.setBackground(Ui.round((accent & 0x00FFFFFF) | 0x66000000, Ui.dp(c, 2)));
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(Ui.dp(c, 3), ViewGroup.LayoutParams.MATCH_PARENT);
        blp.topMargin = Ui.dp(c, 6);
        blp.bottomMargin = Ui.dp(c, 6);
        group.addView(line, blp);
        LinearLayout column = new LinearLayout(c);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setPadding(Ui.dp(c, 14), 0, 0, 0);
        for (View child : children) column.addView(child);
        group.addView(column, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        return group;
    }

    /** 눌러서 실행하는 항목: 옅은 강조색 바탕과 오른쪽 화살표. */
    View button(String label, View.OnClickListener l) {
        return button(label, new Chevron(c, accent), l);
    }

    /** trailing(화살표·아이콘 등)을 오른쪽 끝에 둔 눌러서 실행하는 항목. */
    View button(String label, View trailing, View.OnClickListener l) {
        LinearLayout row = new LinearLayout(c);
        row.setGravity(Gravity.CENTER_VERTICAL);
        int h = Ui.dp(c, 14);
        row.setPadding(h, Ui.dp(c, 12), h, Ui.dp(c, 12));
        int tint = (accent & 0x00FFFFFF) | 0x1F000000;
        row.setBackground(Ui.ripple(accent & 0x00FFFFFF | 0x33000000, Ui.round(tint, Ui.dp(c, 12)), Ui.dp(c, 12)));
        TextView t = text(label, 15, accent);
        mark(t, row);
        row.addView(t, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        row.addView(trailing);
        row.setClickable(true);
        row.setFocusable(true);
        row.setOnClickListener(l);
        LinearLayout.LayoutParams lp = matchWrap();
        lp.topMargin = Ui.dp(c, 6);
        lp.bottomMargin = Ui.dp(c, 6);
        row.setLayoutParams(lp);
        return row;
    }
}
