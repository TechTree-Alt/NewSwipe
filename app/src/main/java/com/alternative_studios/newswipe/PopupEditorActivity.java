package com.alternative_studios.newswipe;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.os.Bundle;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.alternative_studios.newswipe.keyboard.KeyboardLayout;
import com.alternative_studios.newswipe.keyboard.KeyboardTheme;
import com.alternative_studios.newswipe.ui.Ui;

/** 키를 길게 눌렀을 때 고를 수 있는 문자를 한글/영어 자판별로 고치는 화면. */
public final class PopupEditorActivity extends Activity {

    private Prefs prefs;
    private LinearLayout list;
    private LinearLayout keysCard;   // 한글/영어 탭 아래의 키 카드
    private boolean korean = true;
    private KeyboardTheme keyboardTheme;
    private String themeSignature;
    private int bg, cardColor, textColor, hintColor, accentColor, onAccentColor;

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(AppTheme.wrap(base));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = new Prefs(this);
        keyboardTheme = KeyboardTheme.of(this);
        korean = prefs.korean();
        AppTheme colors = AppTheme.of(this);
        themeSignature = AppTheme.signature(this);
        bg = colors.bg;
        cardColor = colors.card;
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
        setContentView(new SettingsFrame(this, "길게 누르기 문자 편집", getString(R.string.app_name)).wrap(scroll));
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
        TextView note = text("키를 길게 눌러 입력할 수 있는 문자를 고칩니다. 첫 번째 문자가 키 위에 작게 표시됩니다.", 13, hintColor);
        note.setPadding(Ui.dp(this, 4), 0, 0, Ui.dp(this, 12));
        list.addView(note);

        LinearLayout tabCard = new LinearLayout(this);
        tabCard.setOrientation(LinearLayout.VERTICAL);
        tabCard.setBackground(Ui.round(cardColor, Ui.dp(this, 16)));
        tabCard.setPadding(Ui.dp(this, 16), Ui.dp(this, 8), Ui.dp(this, 16), Ui.dp(this, 8));
        // 탭을 바꿀 때 화면을 다시 만들지 않고 키 카드만 그 자리에서 바꾼다 (선택 버튼의 스프링이 끊기지 않게).
        tabCard.addView(Ui.choiceRow(this, new String[]{"한글", "영어"}, korean ? 0 : 1,
                accentColor, onAccentColor, textColor, hintColor, 0, i -> {
                    korean = i == 0;
                    keysCard.removeAllViews();
                    fillKeys(keysCard);
                }));
        list.addView(tabCard, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        // 실제 키보드와 비슷하게 보이도록 키보드 배경색 위에 흰 키를 놓는다.
        card.setBackground(Ui.round(keyboardTheme.background, Ui.dp(this, 16)));
        int p = Ui.dp(this, 8);
        card.setPadding(p, p, p, p);
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        clp.topMargin = Ui.dp(this, 12);
        list.addView(card, clp);
        keysCard = card;
        fillKeys(card);
        list.addView(ResetDialog.button(this, "길게 누르기 문자 편집", () -> {
            prefs.resetPopups();
            render();
        }));
    }

    /** 자판 키 칸들을 채운다 (한글/영어 탭을 바꿀 때는 이 카드만 다시 채운다). */
    private void fillKeys(LinearLayout card) {

        // 실제 자판과 비슷한 위치에 놓는다: 줄마다 앞/뒤 빈 칸(칸 단위)을 두어 키 위치를 맞춘다.
        boolean noShift = korean && prefs.koreanNewSwipe();   // Shift 자리가 없는 NewSwipe 배열 (NewSwipe 단모음)
        boolean seven = korean && KeyboardLayout.koreanColumns(prefs) == 7;   // 한 줄 7칸 (NewSwipe 단모음)
        String[][] rows = KeyboardLayout.editableRows(korean, prefs);
        int columns = korean ? KeyboardLayout.koreanColumns(prefs) : 10;
        for (int r = 0; r < rows.length; r++) {
            String[] row = rows[r];
            float lead, between = 0;
            if (r == rows.length - 1) {
                // 맨 아래 줄: 쉼표는 지구본 키 왼쪽, 온점은 스페이스바 오른쪽 (칸 중심을 실제 키 중심에 맞춘다)
                lead = korean ? (seven ? 0.96f : 1.1f) : 1.5f;
                between = korean ? (seven ? 3.33f : 3.8f) : 5.0f;
            } else if (korean) {
                lead = r == 2 && !noShift ? 1f : 0f;   // 셋째 줄은 앞뒤에 Shift/지우기 자리 (7칸 배열은 없음)
            } else {
                lead = r == 0 ? 0f : r == 1 ? 0.5f : 1.5f;   // 둘째 줄은 가운데 정렬, 셋째 줄은 Shift/지우기 자리
            }
            float used = lead + row.length + (row.length > 1 ? between : 0);
            float trail = Math.max(0f, columns - used);
            LinearLayout line = new LinearLayout(this);
            if (lead > 0) line.addView(spacer(lead));
            for (int i = 0; i < row.length; i++) {
                if (i > 0 && between > 0) line.addView(spacer(between));
                line.addView(keyCell(row[i]), cellParams());
            }
            if (trail > 0) line.addView(spacer(trail));
            card.addView(line);
        }
        TextView extra = text("쉼표·온점 설정은 한글/영어가 함께 씁니다.", 12, hintColor);
        extra.setPadding(Ui.dp(this, 4), Ui.dp(this, 8), 0, 0);
        extra.setTextColor(keyboardTheme.hint);
        card.addView(extra);
    }

    /** 키 모양 칸: 높이를 고정하고 사이를 띄운다. */
    /** 칸 단위(weight) 크기의 빈 자리. */
    private View spacer(float weight) {
        View v = new View(this);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, 1, weight);
        // 칸 하나(weight 1)는 좌우 margin 5dp를 따로 차지하므로, 빈 자리도 칸 수에 비례해 같은 만큼 더해야 위치가 맞는다.
        lp.leftMargin = Math.round(Ui.dp(this, 5) * weight);
        v.setLayoutParams(lp);
        return v;
    }

    private LinearLayout.LayoutParams cellParams() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, Ui.dp(this, 58), 1f);
        int m = Ui.dp(this, 2.5f);
        lp.setMargins(m, m, m, m);
        return lp;
    }

    private String[] current(String label) {
        String[] o = prefs.popupOverride(KeyboardLayout.groupOf(korean, label), label);
        if (o != null) return o;
        String[] d = KeyboardLayout.defaultPopup(korean, prefs, label, prefs.periodComma());
        return d == null ? new String[0] : d;
    }

    private View keyCell(String label) {
        LinearLayout cell = new LinearLayout(this);
        cell.setOrientation(LinearLayout.VERTICAL);
        cell.setGravity(Gravity.CENTER);
        String[] items = current(label);
        TextView k = text(label, 20, keyboardTheme.text);
        k.setGravity(Gravity.CENTER);
        cell.addView(k);
        // 비어 있으면 아무것도 보이지 않게 한다 ('-'를 직접 넣은 경우와 구분하기 위해).
        TextView h = text(items.length == 0 ? " " : String.join("", items), 11, keyboardTheme.hint);
        h.setGravity(Gravity.CENTER);
        h.setSingleLine(true);
        h.setEllipsize(android.text.TextUtils.TruncateAt.END);
        cell.addView(h);
        cell.setBackground(Ui.ripple(keyboardTheme.keyPressed,
                Ui.round(keyboardTheme.key, Ui.dp(this, 7)), Ui.dp(this, 7)));
        cell.setOnClickListener(v -> edit(label));
        return cell;
    }

    private void edit(String label) {
        String group = KeyboardLayout.groupOf(korean, label);
        EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        input.setText(String.join(" ", current(label)));
        input.setSelection(input.getText().length());
        int pad = Ui.dp(this, 20);
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(pad, Ui.dp(this, 8), pad, 0);
        TextView help = text("문자를 공백으로 구분해 적어 주세요. 비워 두면 길게 누르기가 없어집니다.", 13, hintColor);
        box.addView(help);
        box.addView(input);
        AppTheme.dialogBuilder(this)
                .setTitle("'" + label + "' 길게 누르기")
                .setView(box)
                .setPositiveButton("저장", (d, w) -> {
                    String v = input.getText().toString().trim();
                    String[] items = v.isEmpty() ? new String[0] : v.split("\\s+");
                    prefs.setPopupOverride(group, label, items);
                    render();
                })
                .setNeutralButton("기본값", (d, w) -> {
                    prefs.resetPopupOverride(group, label);
                    render();
                })
                .setNegativeButton("취소", null)
                .show();
    }
}
