package com.alternative_studios.newswipe;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
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

import com.alternative_studios.newswipe.keyboard.Icons;
import com.alternative_studios.newswipe.keyboard.KeyboardLayout;
import com.alternative_studios.newswipe.keyboard.KeyboardTheme;
import com.alternative_studios.newswipe.ui.ExpressiveChoiceButton;
import com.alternative_studios.newswipe.ui.IconButton;
import com.alternative_studios.newswipe.ui.Ui;

/**
 * 키를 길게 눌렀을 때 고를 수 있는 문자를 한글/영어 자판별로 고치는 화면.
 * 같은 화면을 '길게 눌러 연속 입력' 편집에도 쓴다: 이때는 키를 누를 때마다 그 글자의 연속 입력이 켜지고 꺼진다.
 */
public final class PopupEditorActivity extends Activity {

    private static final String EXTRA_REPEAT = "repeat";

    /** '길게 눌러 연속 입력' 편집 화면을 여는 인텐트. */
    public static Intent repeatIntent(Context context) {
        return new Intent(context, PopupEditorActivity.class).putExtra(EXTRA_REPEAT, true);
    }

    private boolean repeatMode;
    /** 연속 입력 편집 화면에서 키 칸이 마지막으로 그려진 상태 (화면을 다시 만들어도 바뀐 칸만 스프링으로 바뀌게). */
    private final java.util.Map<String, Boolean> shown = new java.util.HashMap<>();
    /** 길게 눌러 문자 입력 화면에서 연속 입력으로 정해 둔 글자들 ("<자판>_<글자>"). 이 글자는 길게 눌러 입력할 문자를 쓸 수 없다. */
    private java.util.Set<String> repeatChars = java.util.Collections.emptySet();

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
        repeatMode = getIntent().getBooleanExtra(EXTRA_REPEAT, false);
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
        setContentView(new SettingsFrame(this, repeatMode ? "길게 눌러 연속 입력 편집" : "길게 누르기 문자 편집", getString(R.string.app_name)).wrap(scroll));
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
        TextView note = text(repeatMode
                ? "연속으로 입력할 글자를 누르면 켜지고, 다시 누르면 꺼집니다. 켠 글자는 길게 누르고 있는 동안 계속 입력되며, 길게 눌러 입력할 문자는 나오지 않습니다."
                : "키를 길게 눌러 입력할 수 있는 문자를 고칩니다. 첫 번째 문자가 키 위에 작게 표시됩니다.", 13, hintColor);
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
        list.addView(ResetDialog.button(this, repeatMode ? "길게 눌러 연속 입력 편집" : "길게 누르기 문자 편집", () -> {
            if (repeatMode) prefs.resetRepeatChars();
            else prefs.resetPopups();
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
        repeatChars = repeatMode ? java.util.Collections.emptySet() : prefs.repeatChars();
        boolean anyDisabled = false;
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
            line.setClipChildren(false);
            line.setBaselineAligned(false);
            if (lead > 0) line.addView(spacer(lead));
            ExpressiveChoiceButton[] keys = new ExpressiveChoiceButton[row.length];
            for (int i = 0; i < row.length; i++) {
                if (i > 0 && between > 0) line.addView(spacer(between));
                keys[i] = keyCell(row[i]);
                anyDisabled |= !keys[i].isEnabled();
                line.addView(keys[i], cellParams());
            }
            if (between == 0) {
                // 이웃한 키끼리는 눌렀을 때 서로 밀어 준다 (키마다 둥근 모서리는 그대로).
                ExpressiveChoiceButton.link(keys);
                for (ExpressiveChoiceButton k : keys) k.setPosition(false, false);
            }
            if (trail > 0) line.addView(spacer(trail));
            card.addView(line);
        }
        TextView extra = text(anyDisabled
                ? "쉼표·온점 설정은 한글/영어가 함께 씁니다.\n흐리게 보이는 글자는 '길게 눌러 연속 입력'으로 정해 두어 길게 눌러 입력할 문자를 쓸 수 없습니다."
                : "쉼표·온점 설정은 한글/영어가 함께 씁니다.", 12, hintColor);
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

    /**
     * 키 칸. 연속 입력 편집 화면에서는 누를 때마다 그 글자의 연속 입력이 켜지고 꺼지며(켠 글자는 강조색 알약),
     * 길게 눌러 문자 입력 화면에서는 길게 눌러 입력할 문자를 아래에 작게 보여 준다
     * (연속 입력으로 정한 글자는 흐리게 보이고 누를 수 없다).
     */
    private ExpressiveChoiceButton keyCell(String label) {
        String group = KeyboardLayout.groupOf(korean, label);
        boolean on = repeatMode && prefs.repeatChar(group, label);
        String key = (korean ? "ko:" : "en:") + label;
        boolean was = repeatMode && shown.containsKey(key) ? shown.get(key) : on;
        if (repeatMode) shown.put(key, on);
        ExpressiveChoiceButton cell = new ExpressiveChoiceButton(this, KeyboardLayout.displayLabel(prefs, label), was, false, false,
                keyboardTheme.accent, keyboardTheme.onAccent, keyboardTheme.text, keyboardTheme.hint);
        cell.setIdleColors(keyboardTheme.key, keyboardTheme.keyPressed);
        cell.setLabelSize(20);
        cell.setPadding(0, 0, 0, 0);
        if (repeatMode) {
            cell.setOnClickListener(v -> {
                boolean now = !prefs.repeatChar(group, label);
                prefs.setRepeatChar(group, label, now);
                shown.put(key, now);
                cell.setChosen(now, true);
            });
            if (was != on) cell.post(() -> cell.setChosen(on, true));   // 방금 바뀐 키
            return cell;
        }
        String[] items = current(label);
        if (items.length > 0) cell.setSubLabel(String.join(singleChars(items) ? "" : " ", items), 6, 16);
        if (repeatChars.contains(group + "_" + label)) {
            cell.setEnabled(false);
            cell.setAlpha(0.38f);
        } else {
            cell.setOnClickListener(v -> edit(label));
        }
        return cell;
    }

    /**
     * 길게 누르기 문자를 한 줄에 하나씩 고치는 대화상자. 문자마다 입력란이 따로 있어,
     * 띄어쓰기가 들어간 문장도 하나의 항목으로 넣을 수 있다.
     */
    /** 문자가 모두 한 글자(이모지 등 한 코드포인트 포함)인지. 문장이 섞이면 키에 작게 보일 때 띄어서 보여 준다. */
    private static boolean singleChars(String[] items) {
        for (String it : items) if (it.codePointCount(0, it.length()) != 1) return false;
        return true;
    }

    private void edit(String label) {
        String group = KeyboardLayout.groupOf(korean, label);
        int pad = Ui.dp(this, 20);
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(pad, Ui.dp(this, 8), pad, 0);
        TextView help = text("길게 눌렀을 때 고를 문자를 한 칸에 하나씩 적어 주세요. 띄어쓰기가 있는 문장도 됩니다. "
                + "첫 번째 칸이 키 위에 작게 표시되고, 모두 비우면 길게 누르기가 없어집니다.", 13, hintColor);
        help.setPadding(0, 0, 0, Ui.dp(this, 8));
        box.addView(help);
        LinearLayout rows = new LinearLayout(this);
        rows.setOrientation(LinearLayout.VERTICAL);
        box.addView(rows);
        for (String item : current(label)) addRow(rows, item, false);
        // 입력란이 하나도 없는 채로 대화상자를 띄우면 시스템이 '글자를 칠 수 없는 창'으로 보고 키보드를 막는다.
        if (rows.getChildCount() == 0) addRow(rows, "", true);
        TextView add = text("+ 문자 추가", 15, accentColor);
        add.setGravity(Gravity.CENTER_VERTICAL);
        add.setPadding(Ui.dp(this, 4), Ui.dp(this, 12), Ui.dp(this, 4), Ui.dp(this, 12));
        add.setOnClickListener(v -> addRow(rows, "", true));
        box.addView(add);
        ScrollView scroll = new ScrollView(this);
        scroll.addView(box);
        android.app.AlertDialog dialog = AppTheme.dialogBuilder(this)
                .setTitle("'" + KeyboardLayout.displayLabel(prefs, label) + "' 길게 누르기")
                .setView(scroll)
                .setPositiveButton("저장", (d, w) -> {
                    java.util.List<String> items = new java.util.ArrayList<>();
                    for (int i = 0; i < rows.getChildCount(); i++) {
                        EditText input = (EditText) ((LinearLayout) rows.getChildAt(i)).getChildAt(0);
                        // 줄바꿈은 저장 형식의 구분자라 넣을 수 없다 (한 줄 입력란이라 보통 들어오지 않는다).
                        String v = input.getText().toString().replace('\n', ' ').replace('\r', ' ').trim();
                        if (!v.isEmpty()) items.add(v);
                    }
                    prefs.setPopupOverride(group, label, items.toArray(new String[0]));
                    render();
                })
                .setNeutralButton("기본값", (d, w) -> {
                    prefs.resetPopupOverride(group, label);
                    render();
                })
                .setNegativeButton("취소", null)
                .show();
        android.view.Window window = dialog.getWindow();
        if (window != null) {
            // 칸을 더한 뒤에도 키보드가 올라오게 하고, 올라와도 칸이 가려지지 않게 창 크기를 맞춘다.
            window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM);
            window.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
                    | android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);
        }
    }

    /** 문자 한 칸(입력란 + 지우기 버튼)을 목록 끝에 더한다. */
    private void addRow(LinearLayout rows, String text, boolean focus) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        input.setSingleLine(true);
        input.setHint("문자 또는 문장");
        input.setText(text);
        row.addView(input, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        int press = (textColor & 0x00FFFFFF) | 0x33000000;
        IconButton remove = new IconButton(this, Icons.CLOSE, hintColor, press, "이 문자 지우기");
        remove.setOnClickListener(v -> rows.removeView(row));
        row.addView(remove, new LinearLayout.LayoutParams(Ui.dp(this, 40), Ui.dp(this, 40)));
        rows.addView(row);
        if (focus) input.requestFocus();
    }
}
