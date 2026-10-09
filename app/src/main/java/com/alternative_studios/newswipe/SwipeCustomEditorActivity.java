package com.alternative_studios.newswipe;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.os.Bundle;
import android.text.InputFilter;
import android.text.InputType;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.alternative_studios.newswipe.keyboard.Key;
import com.alternative_studios.newswipe.keyboard.KeyboardLayout;
import com.alternative_studios.newswipe.keyboard.KeyboardTheme;
import com.alternative_studios.newswipe.keyboard.SwipeCustom;
import com.alternative_studios.newswipe.ui.ExpressiveChoiceButton;
import com.alternative_studios.newswipe.ui.Ui;

/** 키마다 위·왼쪽·오른쪽·아래로 밀었을 때 입력할 글자를 마음대로 정하는 화면 (스와이프 입력 완전 사용자화). */
public final class SwipeCustomEditorActivity extends Activity {
    private static final int MAX_LENGTH = 8;

    private Prefs prefs;
    private LinearLayout list;
    private LinearLayout keysCard;   // 한글/영어 탭 아래의 키 카드
    private boolean korean = true;
    private KeyboardTheme keyboardTheme;
    private String themeSignature;
    private int bg, cardColor, textColor, hintColor, accentColor, onAccentColor;
    /** 키마다 지난번에 그린 '고쳤는지': 바뀐 키만 모양이 스프링으로 바뀌게 한다. */
    private final java.util.Map<String, Boolean> shown = new java.util.HashMap<>();
    private final Paint dirPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

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
        setContentView(new SettingsFrame(this, "밀어서 글자 입력 편집", getString(R.string.app_name)).wrap(scroll));
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
        TextView note = text("키를 눌러 위·아래·왼쪽·오른쪽으로 밀 때 입력할 글자를 정합니다. 키 둘레의 작은 글자가 "
                + "각 방향에 정해진 글자입니다.", 13, hintColor);
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
        card.setBackground(Ui.round(keyboardTheme.background, Ui.dp(this, 16)));
        int p = Ui.dp(this, 8);
        card.setPadding(p, p, p, p);
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        clp.topMargin = Ui.dp(this, 12);
        list.addView(card, clp);
        keysCard = card;
        fillKeys(card);
        list.addView(ResetDialog.button(this, "밀어서 글자 입력 편집", () -> {
            prefs.resetSwipeCustoms();
            render();
        }));
    }

    /** 자판 키 칸들을 채운다 (한글/영어 탭을 바꿀 때는 이 카드만 다시 채운다). */
    private void fillKeys(LinearLayout card) {

        // 실제 자판과 비슷한 위치에 놓는다 (길게 누르기 편집 화면과 같은 배치).
        boolean noShift = korean && prefs.koreanNewSwipe();   // Shift 자리가 없는 NewSwipe 배열 (NewSwipe 단모음)
        boolean seven = korean && KeyboardLayout.koreanColumns(prefs) == 7;   // 한 줄 7칸 (NewSwipe 단모음)
        String[][] rows = KeyboardLayout.editableRows(korean, prefs);
        int columns = korean ? KeyboardLayout.koreanColumns(prefs) : 10;
        for (int r = 0; r < rows.length; r++) {
            String[] row = rows[r];
            float lead, between = 0;
            if (r == rows.length - 1) {
                lead = korean ? (seven ? 0.96f : 1.1f) : 1.5f;
                between = korean ? (seven ? 3.33f : 3.8f) : 5.0f;
            } else if (korean) {
                lead = r == 2 && !noShift ? 1f : 0f;
            } else {
                lead = r == 0 ? 0f : r == 1 ? 0.5f : 1.5f;
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
        TextView extra = text("쉼표·온점 키는 한글/영어가 함께 씁니다.",
                12, keyboardTheme.hint);
        extra.setPadding(Ui.dp(this, 4), Ui.dp(this, 8), 0, 0);
        card.addView(extra);
    }

    private View spacer(float weight) {
        View v = new View(this);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, 1, weight);
        lp.leftMargin = Math.round(Ui.dp(this, 5) * weight);
        v.setLayoutParams(lp);
        return v;
    }

    private LinearLayout.LayoutParams cellParams() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, Ui.dp(this, 68), 1f);
        int m = Ui.dp(this, 2.5f);
        lp.setMargins(m, m, m, m);
        return lp;
    }

    private String current(String label, int dir) {
        return SwipeCustom.get(prefs, korean, label, dir);
    }

    /** 이 키의 네 방향 중 하나라도 고쳤는지. */
    private boolean customized(String label) {
        String group = KeyboardLayout.groupOf(korean, label);
        for (int dir : SwipeCustom.DIRS) {
            if (prefs.swipeCustomOverride(group, label, SwipeCustom.dirName(dir)) != null) return true;
        }
        return false;
    }

    /**
     * 키 글자를 가운데에, 네 방향 글자를 둘레에 작게 그린 키. 방향 글자를 고친 키는 강조색 알약으로 보인다.
     */
    private ExpressiveChoiceButton keyCell(String label) {
        boolean custom = customized(label);
        String key = (korean ? "ko:" : "en:") + label;
        boolean was = shown.containsKey(key) ? shown.get(key) : custom;
        shown.put(key, custom);
        ExpressiveChoiceButton cell = new ExpressiveChoiceButton(this, KeyboardLayout.displayLabel(prefs, label), was, false, false,
                keyboardTheme.accent, keyboardTheme.onAccent, keyboardTheme.text, keyboardTheme.hint);
        cell.setIdleColors(keyboardTheme.key, keyboardTheme.keyPressed);
        cell.setLabelSize(18);
        cell.setPadding(0, 0, 0, 0);
        // 네 방향 글자는 키를 만들 때 한 번만 읽는다 (그릴 때마다 설정을 읽으면 애니메이션 동안 낭비다). 고치면 화면을 다시 만든다.
        String up = current(label, Key.SWIPE_UP), down = current(label, Key.SWIPE_DOWN),
                left = current(label, Key.SWIPE_LEFT), right = current(label, Key.SWIPE_RIGHT);
        cell.setExtra((c, w, h, ink) -> {
            dirPaint.setTextAlign(Paint.Align.CENTER);
            dirPaint.setColor(ink);
            dirPaint.setTextSize(Ui.dp(this, 11));
            float m = Ui.dp(this, 11);
            drawAt(c, up, w / 2, m);
            drawAt(c, down, w / 2, h - m);
            drawAt(c, left, m, h / 2);
            drawAt(c, right, w - m, h / 2);
        });
        cell.setOnClickListener(v -> edit(label));
        if (was != custom) cell.post(() -> cell.setChosen(custom, true));   // 방금 바뀐 키
        return cell;
    }

    private void drawAt(Canvas c, String s, float cx, float cy) {
        if (s == null) return;
        // 글자가 칸보다 길면 첫 글자만 보인다 (자세한 내용은 눌러서 확인).
        String shownText = s.length() > 2 ? s.substring(0, 1) + "…" : s;
        float y = cy - (dirPaint.ascent() + dirPaint.descent()) / 2;
        c.drawText(shownText, cx, y, dirPaint);
    }

    private void edit(String label) {
        String group = KeyboardLayout.groupOf(korean, label);
        int pad = Ui.dp(this, 20);
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(pad, Ui.dp(this, 8), pad, 0);
        box.addView(text("입력할 글자를 적어 주세요. 비워 두면 그 방향은 아무것도 입력하지 않습니다 "
                + "(최대 " + MAX_LENGTH + "자).", 13, hintColor));
        EditText[] inputs = new EditText[SwipeCustom.DIRS.length];
        for (int i = 0; i < inputs.length; i++) {
            LinearLayout line = new LinearLayout(this);
            line.setGravity(android.view.Gravity.CENTER_VERTICAL);
            TextView name = text(SwipeCustom.LABELS[i], 15, textColor);
            line.addView(name, new LinearLayout.LayoutParams(Ui.dp(this, 64), ViewGroup.LayoutParams.WRAP_CONTENT));
            EditText input = new EditText(this);
            input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
            input.setSingleLine(true);
            input.setFilters(new InputFilter[]{new InputFilter.LengthFilter(MAX_LENGTH)});
            String cur = current(label, SwipeCustom.DIRS[i]);
            input.setText(cur == null ? "" : cur);
            line.addView(input, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            inputs[i] = input;
            box.addView(line);
        }
        ScrollView sv = new ScrollView(this);
        sv.addView(box);
        AppTheme.dialogBuilder(this)
                .setTitle("'" + KeyboardLayout.displayLabel(prefs, label) + "' 밀어서 글자 입력")
                .setView(sv)
                .setPositiveButton("저장", (d, w) -> {
                    for (int i = 0; i < inputs.length; i++) {
                        prefs.setSwipeCustomOverride(group, label, SwipeCustom.dirName(SwipeCustom.DIRS[i]),
                                inputs[i].getText().toString());
                    }
                    render();
                    if (android.os.Build.VERSION.SDK_INT >= 30) {
                        list.performHapticFeedback(android.view.HapticFeedbackConstants.CONFIRM);
                    }
                })
                .setNeutralButton("기본값", (d, w) -> {
                    for (int dir : SwipeCustom.DIRS) {
                        prefs.resetSwipeCustomOverride(group, label, SwipeCustom.dirName(dir));
                    }
                    render();
                })
                .setNegativeButton("취소", null)
                .show();
    }
}
