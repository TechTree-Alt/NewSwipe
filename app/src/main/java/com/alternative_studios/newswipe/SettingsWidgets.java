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

    /** 설명 글 (작고 옅은 글자). Metro: PhoneTextSubtleStyle (PhoneFontSizeNormal 20px → 15sp, PhoneSubtleBrush). */
    TextView note(String s) {
        TextView t = text(s, 13, hint);
        if (Metro.on) {
            Metro.text(t, Metro.sp(Metro.FONT_NORMAL), Metro.regular(), 13);
            t.setTag(com.alternative_studios.newswipe.R.id.metro_note, Boolean.TRUE);
        }
        t.setLineSpacing(Ui.dp(c, 3), 1f);
        t.setPadding(0, Ui.dp(c, 6), 0, Ui.dp(c, 6));
        return t;
    }

    /** section()이 만든 소제목임을 알리는 태그 (Metro는 이것으로 목록을 섹션 페이지로 나눠 피벗에 담는다). */
    private static final Object SECTION_HEADER = new Object();

    /** section()이 만든 소제목인지. */
    static boolean isSectionHeader(View v) {
        return v.getTag() == SECTION_HEADER;
    }

    /** parent에 소제목과 빈 카드를 더하고 카드를 돌려준다. */
    LinearLayout section(LinearLayout parent, String name) {
        TextView h = text(name, 14, accent);
        h.setPadding(Ui.dp(c, 4), Ui.dp(c, 20), 0, Ui.dp(c, 8));
        if (Metro.on) {
            // Windows Phone의 묶음 머리글 PhoneTextGroupHeaderStyle: PhoneFontFamilySemiLight, PhoneFontSizeLarge(32px → 24sp),
            // PhoneSubtleBrush (설정 › about의 'phone information'처럼 옅은 회색 소문자). 카드가 없으므로 줄과 같은 왼쪽 선에 맞춘다.
            // 위 항목이 아래에 항목 사이 여백을 품고 있어 위는 조금만 띄운다.
            h.setText(Metro.lower(name));
            h.setTextColor(hint);
            Metro.text(h, Metro.sp(Metro.FONT_LARGE), Metro.light(), 14);
            h.setPadding(0, Ui.dp(c, 12), 0, Ui.dp(c, 2));
        }
        h.setTag(SECTION_HEADER);
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

    /**
     * Metro UI: box 안에 컨트롤의 머리글을 더한다 (토글·값 줄의 머리글과 같은 모양: PhoneFontSizeNormal 20px → 15sp, PhoneSubtleBrush).
     * 선택 버튼 줄처럼 자기 이름이 없는 컨트롤 위에 둔다. 검색으로 들어오면 box 전체를 깜빡인다.
     */
    TextView fieldHeader(LinearLayout box, String label) {
        TextView t = text(label, 16, hint);
        Metro.text(t, Metro.sp(Metro.FONT_NORMAL), Metro.regular(), 16);   // 검색은 원래 크기(16sp)로 항목 이름을 찾는다
        t.setPadding(0, Ui.dp(c, 6), 0, Ui.dp(c, 6));
        mark(t, box);
        box.addView(t);
        return t;
    }

    /** Metro 토글 아래 여백 (dp): ToggleSwitch 템플릿 Margin 아래 42px. 일반 항목 사이의 거리로도 쓴다. */
    static final int METRO_TOGGLE_GAP_DP = 31;
    /** 하위 묶음 안의 마지막 줄과 묶음 위 줄이 세로선과 남기는 거리 (dp). */
    private static final int METRO_LINE_GAP_DP = 6;
    /**
     * Metro에서 항목 사이 여백을 아래에 품은 줄의 표시 (태그 키). 값이 GAP_PADDING이면 그 여백이 아래 안쪽 여백(토글·값 줄·슬라이더),
     * GAP_MARGIN이면 아래 바깥 여백(버튼)이다.
     */
    private static final int R_GAP_END = com.alternative_studios.newswipe.R.id.metro_gap_end;
    private static final int GAP_PADDING = 1, GAP_MARGIN = 2;

    /** Metro: 줄 v의 아래에 항목 사이 여백을 둔다 (다음 항목과 METRO_TOGGLE_GAP_DP쯤 떨어지게). 태그에 {종류, 원래 여백 px}를 남긴다. */
    private void gapBelow(View v, boolean margin, int dp) {
        int px = Ui.dp(c, dp);
        v.setTag(R_GAP_END, new int[]{margin ? GAP_MARGIN : GAP_PADDING, px});
        setTrailing(v, px);
    }

    private static boolean gapIsMargin(View v) {
        Object t = v.getTag(R_GAP_END);
        return t instanceof int[] && ((int[]) t)[0] == GAP_MARGIN;
    }

    /** gapBelow로 둔 원래 여백 (px). */
    private static int naturalGap(View v) {
        Object t = v.getTag(R_GAP_END);
        return t instanceof int[] ? ((int[]) t)[1] : 0;
    }

    private static void setTrailing(View v, int px) {
        if (gapIsMargin(v)) {
            if (v.getLayoutParams() instanceof ViewGroup.MarginLayoutParams
                    && ((ViewGroup.MarginLayoutParams) v.getLayoutParams()).bottomMargin != px) {
                ((ViewGroup.MarginLayoutParams) v.getLayoutParams()).bottomMargin = px;
                v.requestLayout();
            }
        } else if (v.getPaddingBottom() != px) {
            v.setPadding(v.getPaddingLeft(), v.getPaddingTop(), v.getPaddingRight(), px);
        }
    }

    /** v(또는 그 안의 마지막 보이는 줄)가 아래 여백을 품은 줄이면 그 줄, 아니면 null. */
    private static View gapRow(View v) {
        if (v.getTag(R_GAP_END) != null) return v;
        if (v instanceof LinearLayout && ((LinearLayout) v).getOrientation() == LinearLayout.VERTICAL && !v.isClickable()) {
            ViewGroup g = (ViewGroup) v;
            for (int i = g.getChildCount() - 1; i >= 0; i--) {
                View ch = g.getChildAt(i);
                if (ch.getVisibility() == View.GONE) continue;
                return gapRow(ch);
            }
        }
        return null;
    }

    /** toggle()로 만든 줄의 설명 글임을 알리는 태그 (줄 모양이 모드마다 달라 위치 대신 이것으로 찾는다). */
    private static final Object DESC = new Object();

    /**
     * Metro UI의 토글 줄: Windows Phone Toolkit의 ToggleSwitch 템플릿처럼 위에 옅은 머리글(PhoneFontSizeNormal 20px → 15sp,
     * PhoneSubtleBrush), 그 아래 왼쪽에 '켬'/'끔'(PhoneFontSizeLarge 32px → 24sp, Light), 오른쪽에 스위치. 설명은 WP 설정 화면처럼
     * 그 아래에 옅은 글(PhoneTextSubtleStyle)로 붙는다. 줄 어디를 눌러도 스위치가 바뀐다.
     */
    private View metroToggle(String label, String desc, boolean value, Consumer<Boolean> onChange) {
        LinearLayout row = new LinearLayout(c);
        row.setOrientation(LinearLayout.VERTICAL);
        // Toolkit ToggleSwitch 템플릿의 바깥 여백 (위 5px, 아래 42px): 아래 여백이 토글과 토글 사이를 뚜렷이 나눈다.
        row.setPadding(0, Ui.dp(c, 4), 0, 0);
        gapBelow(row, false, METRO_TOGGLE_GAP_DP);
        TextView t = text(label, 16, hint);
        Metro.text(t, Metro.sp(Metro.FONT_NORMAL), Metro.regular(), 16);   // 검색은 원래 크기(16sp)로 항목 이름을 찾는다
        mark(t, row);
        row.addView(t);
        LinearLayout line = new LinearLayout(c);
        line.setGravity(Gravity.CENTER_VERTICAL);
        TextView state = new TextView(c);
        state.setTextColor(text);
        state.setTextSize(TypedValue.COMPLEX_UNIT_SP, Metro.sp(Metro.FONT_LARGE));
        state.setTypeface(Metro.light());
        state.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);   // 스위치가 상태를 읽어 준다
        line.addView(state, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        ExpressiveSwitch sw = new ExpressiveSwitch(c, accent, onAccent, hint, card);
        sw.setCheckedImmediately(value);
        sw.setStateLabel(state);
        sw.setOnCheckedChangeListener(onChange::accept);
        line.addView(sw, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        row.addView(line, matchWrap());
        if (desc != null) {
            TextView d = text(desc, 13, hint);
            Metro.text(d, Metro.sp(Metro.FONT_NORMAL), Metro.regular(), 13);
            d.setTag(DESC);
            d.setPadding(0, Ui.dp(c, 2), 0, 0);
            row.addView(d);
        }
        row.setOnClickListener(v -> sw.performClick());
        Metro.tilt(row, sw);   // WP는 스위치(ToggleSwitchButton)만 틸트한다
        return row;
    }

    /** 이름·설명과 오른쪽 스위치. 줄 어디를 눌러도 스위치가 바뀐다. */
    View toggle(String label, String desc, boolean value, Consumer<Boolean> onChange) {
        if (Metro.on) return metroToggle(label, desc, value, onChange);
        LinearLayout row = new LinearLayout(c);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, Ui.dp(c, 8), 0, Ui.dp(c, 8));
        LinearLayout texts = new LinearLayout(c);
        texts.setOrientation(LinearLayout.VERTICAL);
        TextView t = text(label, 16, text);
        mark(t, row);
        texts.addView(t);
        if (desc != null) {
            TextView d = text(desc, 13, hint);
            d.setTag(DESC);
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
        return toggleRow.findViewWithTag(DESC);
    }

    /** toggle()로 만든 줄의 스위치. */
    static ExpressiveSwitch switchOf(View toggleRow) {
        if (toggleRow instanceof ExpressiveSwitch) return (ExpressiveSwitch) toggleRow;
        if (toggleRow instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) toggleRow;
            for (int i = 0; i < g.getChildCount(); i++) {
                ExpressiveSwitch s = switchOf(g.getChildAt(i));
                if (s != null) return s;
            }
        }
        return null;
    }

    /** toggle()로 만든 줄을 쓸 수 없게 흐린다: 글자(이름·켬/끔·설명)는 옅게, 스위치는 스스로 흐려진다. */
    static void dimToggle(View toggleRow) {
        toggleRow.setEnabled(false);
        toggleRow.setClickable(false);
        ExpressiveSwitch sw = switchOf(toggleRow);
        if (sw != null) sw.setEnabled(false);
        dimTexts(toggleRow);
    }

    private static void dimTexts(View v) {
        if (v instanceof TextView) v.setAlpha(0.38f);
        else if (v instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) dimTexts(g.getChildAt(i));
        }
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
        if (Metro.on) {
            box.setPadding(0, Ui.dp(c, 4), 0, 0);
            gapBelow(box, false, METRO_TOGGLE_GAP_DP - 9);   // 막대 아래에서 다음 항목까지 일반 항목 사이만큼
        }
        LinearLayout top = new LinearLayout(c);
        TextView t = text(label, 16, text);
        // Metro: 토글·값 줄처럼 위에 옅은 머리글 (PhoneFontSizeNormal, PhoneSubtleBrush), 지금 값은 오른쪽에 글자 색으로.
        if (Metro.on) {
            t.setTextColor(hint);
            Metro.text(t, Metro.sp(Metro.FONT_NORMAL), Metro.regular(), 16);
        }
        mark(t, box);
        top.addView(t, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView v = text(value + unit, 14, hint);
        if (Metro.on) {
            v.setTextColor(text);
            Metro.text(v, Metro.sp(Metro.FONT_NORMAL), Metro.regular(), 14);
        }
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
        if (Metro.on) return metroPicker(label, value, l);
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

    /**
     * Metro UI의 값 줄: Windows Phone Toolkit의 ListPicker 템플릿처럼 위에 옅은 머리글(PhoneFontSizeNormal 20px → 15sp,
     * PhoneSubtleBrush, 아래 8px 여백), 그 아래 글자 색 3px 테두리의 네모 안에 지금 값(PhoneFontSizeMediumLarge 25.333px → 19sp).
     * 누르면 고르기가 열린다 (드롭다운이면 이 네모 자리에서 목록이 펼쳐진다: DropdownMenu.PICKER_BOX).
     */
    private ValueRow metroPicker(String label, String value, View.OnClickListener l) {
        LinearLayout row = new LinearLayout(c);
        row.setOrientation(LinearLayout.VERTICAL);
        // 위는 ListPicker 템플릿의 바깥 여백 PhoneTouchTargetOverhang (12px → 9dp), 아래는 다음 항목과 일반 항목 사이만큼.
        row.setPadding(0, Ui.dp(c, 9), 0, 0);
        gapBelow(row, false, METRO_TOGGLE_GAP_DP - 9);
        TextView t = text(label, 16, hint);
        Metro.text(t, Metro.sp(Metro.FONT_NORMAL), Metro.regular(), 16);   // 검색은 원래 크기(16sp)로 항목 이름을 찾는다
        mark(t, row);
        row.addView(t);
        LinearLayout box = new LinearLayout(c);
        box.setGravity(Gravity.CENTER_VERTICAL);
        android.graphics.drawable.GradientDrawable border = Ui.round(0, 0);
        border.setStroke(Ui.dp(c, Metro.BORDER_DP), text);
        box.setBackground(border);
        // 테두리 3px + ListPickerItem의 Padding 8,10px: 펼친 목록의 한 줄과 같은 자리에 글자가 온다 (DropdownMenu).
        box.setPadding(Metro.px(c, 11), Metro.px(c, 13), Metro.px(c, 11), Metro.px(c, 13));
        box.setTag(DropdownMenu.PICKER_BOX);
        TextView v = text(value, 16, text);
        Metro.text(v, Metro.sp(Metro.FONT_MEDIUM_LARGE), Metro.regular(), 16);
        v.setSingleLine(true);
        v.setEllipsize(android.text.TextUtils.TruncateAt.END);
        box.addView(v, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        LinearLayout.LayoutParams blp = matchWrap();
        blp.topMargin = Metro.px(c, 8);   // 머리글 아래 8px
        row.addView(box, blp);
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
        row.setBackground(Metro.buttonBackground(row.getContext()));
        metroInk(row, Metro.buttonInk(), true);
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
        // Metro: ExpanderView처럼 조금 더 들여 놓는다.
        return subGroup(c, accent, Metro.on ? 22 : 6, children);
    }

    /**
     * 하위 설정 묶음 (설정 화면과 편집 화면이 같이 쓴다): leftMarginDp만큼 들인 세로 안내선과 그 오른쪽의 항목들.
     * 보통은 옅은 강조색의 둥근 선(3dp)이 위아래를 조금 띄워 놓인다. Metro는 Windows Phone Toolkit의 ExpanderView처럼
     * 옅은 회색(PhoneSubtleBrush)의 가는 직선(StrokeThickness 3px)이 하위 항목의 내용만큼 이어진다.
     */
    static View subGroup(Context c, int accent, int leftMarginDp, View... children) {
        LinearLayout group = new LinearLayout(c);
        group.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams glp = matchWrap();
        glp.leftMargin = Ui.dp(c, leftMarginDp);
        group.setLayoutParams(glp);
        View line = new View(c);
        LinearLayout.LayoutParams blp;
        if (Metro.on) {
            line.setBackgroundColor(Metro.subtle);
            blp = new LinearLayout.LayoutParams(Ui.dp(c, Metro.BORDER_DP), ViewGroup.LayoutParams.MATCH_PARENT);
        } else {
            line.setBackground(Ui.round((accent & 0x00FFFFFF) | 0x66000000, Ui.dp(c, 2)));
            blp = new LinearLayout.LayoutParams(Ui.dp(c, 3), ViewGroup.LayoutParams.MATCH_PARENT);
            blp.topMargin = Ui.dp(c, 6);
            blp.bottomMargin = Ui.dp(c, 6);
        }
        group.addView(line, blp);
        final int line6 = Ui.dp(c, METRO_LINE_GAP_DP);
        LinearLayout column = new LinearLayout(c) {
            /**
             * Metro: 세로선이 마지막으로 보이는 하위 항목의 글에서 끝나도록, 잴 때마다 마지막으로 보이는 줄의 항목 사이 여백은 줄이고
             * 나머지는 원래대로 둔다. 하위 항목은 켜고 끄며 보였다 숨었다 하므로 만들 때 한 번 정해서는 맞지 않는다.
             * 마지막이 안쪽 하위 묶음이면 그 묶음의 아래 여백(선 밖에 두는 항목 사이 여백)을 없앤다.
             */
            @Override
            protected void onMeasure(int widthSpec, int heightSpec) {
                if (Metro.on) {
                    View lastVisible = null;
                    for (int i = getChildCount() - 1; i >= 0 && lastVisible == null; i--) {
                        if (getChildAt(i).getVisibility() != View.GONE) lastVisible = getChildAt(i);
                    }
                    for (int i = 0; i < getChildCount(); i++) {
                        View ch = getChildAt(i);
                        boolean last = ch == lastVisible;
                        View row = gapRow(ch);
                        if (row != null) setTrailing(row, last ? line6 : naturalGap(row));
                        Object m = ch.getTag(com.alternative_studios.newswipe.R.id.shown_margins);
                        if (m instanceof int[] && ((int[]) m).length > 2) {
                            int[] shown = (int[]) m;
                            int want = last ? 0 : shown[2];
                            if (shown[1] != want) {
                                shown[1] = want;   // 펼치고 접는 움직임도 이 값을 쓴다
                                if (ch.getVisibility() == View.VISIBLE && ch.getLayoutParams() instanceof MarginLayoutParams
                                        && !Ui.isAnimating(ch)) {
                                    ((MarginLayoutParams) ch.getLayoutParams()).bottomMargin = want;
                                }
                            }
                        }
                    }
                }
                super.onMeasure(widthSpec, heightSpec);
            }
        };
        column.setOrientation(LinearLayout.VERTICAL);
        column.setPadding(Ui.dp(c, Metro.on ? 12 : 14), 0, 0, 0);
        for (View child : children) column.addView(child);
        group.addView(column, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        if (Metro.on && children.length > 0) {
            // Toolkit ExpanderView처럼 세로선은 하위 항목의 내용만큼만: 위 줄 바로 아래에서 시작해 마지막 하위 항목의 글에서 끝난다.
            // 마지막 하위 항목이 품은 항목 사이 여백은 선 밖(묶음 아래)으로 옮겨 다음 항목과의 거리는 그대로 둔다.
            // (마지막으로 보이는 하위 항목의 여백은 column이 잴 때마다 줄인다.)
            glp.bottomMargin = Ui.dp(c, METRO_TOGGLE_GAP_DP) - line6;
            // {위, 아래, 원래 아래}: 펼치고 접을 때 이 여백도 함께 움직인다 (Ui.setVisibleAnimated). 바깥 묶음의 마지막이면 아래는 0.
            int[] shown = {0, glp.bottomMargin, glp.bottomMargin};
            group.setTag(com.alternative_studios.newswipe.R.id.shown_margins, shown);
            // 위 줄이 아래에 품은 항목 사이 여백은 묶음이 보일 때만 거슬러 올라가 덮는다 (음수 위 여백: 묶음을 숨기면 쓰이지 않아
            // 위 줄과 그다음 항목 사이는 원래 거리 그대로다). 위 줄은 화면에 붙은 뒤에야 알 수 있다.
            group.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
                @Override
                public void onViewAttachedToWindow(View v) {
                    if (!(v.getParent() instanceof ViewGroup)) return;
                    ViewGroup parent = (ViewGroup) v.getParent();
                    View prev = null;
                    for (int i = parent.indexOfChild(v) - 1; i >= 0 && prev == null; i--) {
                        View p = parent.getChildAt(i);
                        if (p.getVisibility() != View.GONE) prev = p;
                    }
                    View above = prev == null ? null : gapRow(prev);
                    // 위 줄의 원래 여백으로 정한다 (위 줄은 이 묶음이 숨어 있는 동안 마지막 줄이라 여백이 줄어 있을 수 있다).
                    int top = above == null ? 0 : -Math.max(0, naturalGap(above) - line6);
                    shown[0] = top;
                    if (glp.topMargin != top) {
                        glp.topMargin = top;
                        v.setLayoutParams(glp);
                    }
                }

                @Override
                public void onViewDetachedFromWindow(View v) {
                }
            });
        }
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
            // Windows Phone의 버튼 (PhoneButtonBase): 글자 색 테두리만 있는 네모, 글 너비만큼 (설정 › about의 'more info').
            // 누르는 동안 강조색으로 채워지고 글자는 그 위의 색이 된다.
            row.setBackground(Metro.buttonBackground(c));
            Metro.buttonText(t, 15);
            t.setTextColor(Metro.buttonInk());
            t.setDuplicateParentStateEnabled(true);
            Metro.buttonPadding(row);
        }
        if (NeuUi.on) {
            // Neumorphism: 솟아오른 버튼, 누르는 동안 안으로 들어간다. 그림자 자리만큼 위아래를 띄운다.
            NeuUi.button(row, NeuUi.surface, Ui.dp(c, 14));
        }
        mark(t, row);
        if (Metro.on) {
            // 글 너비만큼의 버튼: 오른쪽 아이콘(가져오기·내보내기 등)은 글 바로 뒤에.
            row.addView(t, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            if (trailing.getVisibility() != View.GONE) {
                LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                tlp.leftMargin = Ui.dp(c, 12);
                row.addView(trailing, tlp);
            } else {
                row.addView(trailing);
            }
        } else {
            row.addView(t, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            row.addView(trailing);
        }
        row.setClickable(true);
        row.setFocusable(true);
        row.setOnClickListener(l);
        Metro.tilt(row);
        LinearLayout.LayoutParams lp = matchWrap();
        lp.topMargin = Ui.dp(c, NeuUi.on ? 10 : 6);
        lp.bottomMargin = Ui.dp(c, NeuUi.on ? 10 : 6);
        row.setLayoutParams(lp);
        if (Metro.on) {
            // 글 너비만큼, 바깥 여백은 Button 템플릿의 PhoneTouchTargetOverhang(12px → 9dp). 버튼이 이어지면 그 사이는 그대로 두고,
            // 버튼 다음에 다른 항목이 오면 아래에 일반 항목 사이만큼 띄운다 (다음 줄은 화면에 붙은 뒤에야 알 수 있다).
            lp.width = ViewGroup.LayoutParams.WRAP_CONTENT;
            lp.topMargin = Ui.dp(c, 9);
            row.setTag(com.alternative_studios.newswipe.R.id.metro_button, Boolean.TRUE);
            gapBelow(row, true, METRO_TOGGLE_GAP_DP - 9);
            int stacked = Ui.dp(c, 9);   // 버튼마다 바깥 여백 12px: 이어진 버튼 사이는 24px (→ 18dp)
            int apart = Ui.dp(c, METRO_TOGGLE_GAP_DP - 9);
            row.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
                @Override
                public void onViewAttachedToWindow(View v) {
                    if (!(v.getParent() instanceof ViewGroup)) return;
                    ViewGroup parent = (ViewGroup) v.getParent();
                    View next = null;
                    for (int i = parent.indexOfChild(v) + 1; i < parent.getChildCount() && next == null; i++) {
                        if (parent.getChildAt(i).getVisibility() != View.GONE) next = parent.getChildAt(i);
                    }
                    boolean button = next != null && next.getTag(com.alternative_studios.newswipe.R.id.metro_button) != null;
                    boolean note = next != null && next.getTag(com.alternative_studios.newswipe.R.id.metro_note) != null;
                    // 바로 아래 설명 글은 이 버튼의 설명이다: 토글의 설명처럼 버튼에 붙이고, 항목 사이 여백은 설명 아래로 옮긴다.
                    int px = button ? stacked : note ? 0 : apart;
                    ((int[]) v.getTag(R_GAP_END))[1] = px;
                    setTrailing(v, px);
                    if (note) {
                        next.setTag(R_GAP_END, new int[]{GAP_PADDING, apart});
                        setTrailing(next, apart);
                    }
                }

                @Override
                public void onViewDetachedFromWindow(View v) {
                }
            });
        }
        return row;
    }
}
