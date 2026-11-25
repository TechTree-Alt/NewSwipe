package com.alternative_studios.newswipe;

import android.content.Context;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.alternative_studios.newswipe.ui.NeuUi;
import com.alternative_studios.newswipe.ui.Chevron;
import com.alternative_studios.newswipe.ui.ExpressiveSlider;
import com.alternative_studios.newswipe.ui.ExpressiveSwitch;
import com.alternative_studios.newswipe.ui.Metro;
import com.alternative_studios.newswipe.ui.Ui;

import java.util.function.Consumer;
import java.util.function.IntConsumer;

/**
 * 설정 화면의 공통 조각: 소제목과 카드, 스위치 줄, 슬라이더, 하위 묶음, 설명, 버튼.
 * 설정 화면(SettingsActivity)과 키보드 사용자화의 키 창(KeyEditPanel)이 같은 모양을 쓰도록 한곳에서 만든다.
 * 글자 크기(항목 16sp, 소제목 14sp 강조색, 설명 13sp)는 설정 검색이 화면 글자를 모을 때 기대하는 값이라 바꾸지 않는다.
 * Metro UI 모드에서는 글자를 크고 가늘게 바꾸되 원래 크기를 글자에 남겨 검색이 그대로 찾는다 (Metro.text).
 * 카드는 바탕 없이 사라지고, 소제목은 강조색 소문자, 버튼은 테두리만 있는 네모가 된다.
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

        /** 사용자가 슬라이더에서 손을 뗄 때마다 부를 작업 (끄는 동안에는 부르지 않는다). */
        void onRelease(Runnable r) {
            bar.setOnClickListener(v -> r.run());
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
        if (Metro.on) Metro.text(t, 14, Metro.regular(), 13);
        t.setLineSpacing(Ui.dp(c, 3), 1f);
        t.setPadding(0, Ui.dp(c, 6), 0, Ui.dp(c, 6));
        return t;
    }

    /** parent에 소제목과 빈 카드를 더하고 카드를 돌려준다. */
    LinearLayout section(LinearLayout parent, String name) {
        TextView h = text(name, 14, accent);
        h.setPadding(Ui.dp(c, 4), Ui.dp(c, 20), 0, Ui.dp(c, 8));
        if (Metro.on) {
            // Windows Phone의 묶음 머리글: 강조색의 크고 가는 소문자. 카드가 없으므로 줄과 같은 왼쪽 선에 맞춘다.
            h.setText(Metro.lower(name));
            Metro.text(h, 24, Metro.light(), 14);
            h.setPadding(0, Ui.dp(c, 24), 0, Ui.dp(c, 2));
        }
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
        if (NeuUi.on) NeuUi.card(box, Ui.dp(c, 20));   // Neumorphism: 바탕에서 부드럽게 솟아오른 카드
        if (Metro.on) {
            // Metro에는 카드가 없다: 항목이 검정·흰 바탕 위에 바로 놓인다.
            box.setBackground(null);
            box.setPadding(0, 0, 0, Ui.dp(c, 4));
        }
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
        if (Metro.on) Metro.text(t, 20, Metro.light(), 16);
        mark(t, row);
        texts.addView(t);
        if (desc != null) {
            TextView d = text(desc, 13, hint);
            if (Metro.on) Metro.text(d, 14, Metro.regular(), 13);
            texts.addView(d);
        }
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

    /** badge()로 붙인 표시임을 알리는 태그. */
    private static final Object BADGE = new Object();

    /** badge()로 붙인 표시인지 (설정 검색 목록을 모을 때 항목 이름·설명으로 보지 않는다). */
    static boolean isBadge(View v) {
        return v.getTag() == BADGE;
    }

    /**
     * toggle()·slider()·valueRow()로 만든 줄의 이름 글자 바로 오른쪽에 작은 표시(예: '세로만')를 붙인다. 이름 글자는 그대로라
     * 검색·깜빡임·접근성 이름은 바뀌지 않는다. 이미 붙어 있으면 글만 바꾼다.
     */
    static void badge(View row, String text, int accent) {
        TextView label = firstText(row);
        if (label == null || !(label.getParent() instanceof ViewGroup)) return;
        ViewGroup parent = (ViewGroup) label.getParent();
        for (int i = 0; i < parent.getChildCount(); i++) {
            View v = parent.getChildAt(i);
            if (isBadge(v)) {
                ((TextView) v).setText(text);
                return;
            }
        }
        Context c = row.getContext();
        int idx = parent.indexOfChild(label);
        ViewGroup.LayoutParams lp = label.getLayoutParams();
        parent.removeViewAt(idx);
        LinearLayout line = new LinearLayout(c);
        line.setOrientation(LinearLayout.HORIZONTAL);
        line.setGravity(Gravity.CENTER_VERTICAL);
        line.addView(label, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        TextView b = new TextView(c);
        b.setTag(BADGE);
        b.setText(text);
        b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        b.setTextColor(accent);
        b.setSingleLine(true);
        b.setBackground(Ui.round((accent & 0x00FFFFFF) | 0x24000000, Ui.dp(c, 8)));
        b.setPadding(Ui.dp(c, 6), Ui.dp(c, 1), Ui.dp(c, 6), Ui.dp(c, 1));
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        blp.leftMargin = Ui.dp(c, 6);
        line.addView(b, blp);
        parent.addView(line, idx, lp);
    }

    /** v 안에서 처음 만나는 글자 (줄의 이름 글자). */
    private static TextView firstText(View v) {
        if (v instanceof TextView) return (TextView) v;
        if (v instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) {
                TextView t = firstText(g.getChildAt(i));
                if (t != null) return t;
            }
        }
        return null;
    }

    /** toggle()로 만든 줄의 설명 글. 설명 없이 만들었으면 null. */
    static TextView descOf(View toggleRow) {
        ViewGroup texts = (ViewGroup) ((ViewGroup) toggleRow).getChildAt(0);
        return texts.getChildCount() > 1 ? (TextView) texts.getChildAt(1) : null;
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
        if (Metro.on) Metro.text(t, 20, Metro.light(), 16);
        mark(t, box);
        top.addView(t, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView v = text(value + unit, 14, hint);
        if (Metro.on) Metro.text(v, 18, Metro.light(), 14);
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
        if (Metro.on) Metro.text(t, 20, Metro.light(), 16);
        mark(t, row);
        row.addView(t, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        TextView v = text(value, 16, accent);
        if (Metro.on) Metro.text(v, 18, Metro.light(), 16);
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
        Metro.tilt(row);
        return new ValueRow(row, v);
    }

    /**
     * 누르면 드롭다운이 펼쳐지는 값 줄. 펼쳐지는 목록이 누른 줄을 덮어 반응이 되므로 줄에는 눌림 물결을 그리지 않는다.
     */
    ValueRow dropdownRow(String label, String value, View.OnClickListener l) {
        ValueRow r = valueRow(label, value, l);
        r.view.setBackground(null);
        return r;
    }

    /**
     * Metro: 직접 만든 실행 버튼 줄을 다른 Metro 버튼처럼 바꾼다 (글자 색 테두리의 네모, 누르는 동안 강조색으로 채워지고 글자는 그 위의 색).
     * Metro 모드가 아니면 아무것도 하지 않는다.
     */
    static void metroButton(View row) {
        if (!Metro.on) return;
        Context c = row.getContext();
        AppTheme t = AppTheme.of(c);
        android.graphics.drawable.GradientDrawable idle = Ui.round(0, 0);
        idle.setStroke(Ui.dp(c, 2), t.text);
        android.graphics.drawable.StateListDrawable bg = new android.graphics.drawable.StateListDrawable();
        bg.addState(new int[]{android.R.attr.state_pressed}, Ui.round(t.accent, 0));
        bg.addState(new int[0], idle);
        row.setBackground(bg);
        metroInk(row, new android.content.res.ColorStateList(new int[][]{{android.R.attr.state_pressed}, {}},
                new int[]{t.onAccent, t.text}), true);
        Metro.tilt(row);
    }

    private static void metroInk(View v, android.content.res.ColorStateList ink, boolean root) {
        if (!root) v.setDuplicateParentStateEnabled(true);
        if (v instanceof TextView) {
            ((TextView) v).setTextColor(ink);
        } else if (v instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) metroInk(g.getChildAt(i), ink, false);
        }
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
        if (Metro.on) {
            // Windows Phone의 버튼: 글자 색 테두리만 있는 네모. 누르는 동안 강조색으로 채워지고 글자는 그 위의 색이 된다.
            android.graphics.drawable.GradientDrawable idle = Ui.round(0, 0);
            idle.setStroke(Ui.dp(c, 2), text);
            android.graphics.drawable.GradientDrawable down = Ui.round(accent, 0);
            down.setStroke(Ui.dp(c, 2), accent);
            android.graphics.drawable.StateListDrawable bg = new android.graphics.drawable.StateListDrawable();
            bg.addState(new int[]{android.R.attr.state_pressed}, down);
            bg.addState(new int[0], idle);
            row.setBackground(bg);
            Metro.text(t, 18, Metro.regular(), 15);
            t.setTextColor(new android.content.res.ColorStateList(
                    new int[][]{{android.R.attr.state_pressed}, {}}, new int[]{onAccent, text}));
            t.setDuplicateParentStateEnabled(true);
            row.setPadding(h, Ui.dp(c, 14), h, Ui.dp(c, 14));
        }
        if (NeuUi.on) {
            // Neumorphism: 솟아오른 버튼, 누르는 동안 안으로 들어간다. 그림자 자리만큼 위아래를 띄운다.
            NeuUi.button(row, NeuUi.surface, Ui.dp(c, 14));
        }
        mark(t, row);
        row.addView(t, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        row.addView(trailing);
        row.setClickable(true);
        row.setFocusable(true);
        row.setOnClickListener(l);
        Metro.tilt(row);
        LinearLayout.LayoutParams lp = matchWrap();
        lp.topMargin = Ui.dp(c, NeuUi.on ? 10 : 6);
        lp.bottomMargin = Ui.dp(c, NeuUi.on ? 10 : 6);
        row.setLayoutParams(lp);
        return row;
    }
}
