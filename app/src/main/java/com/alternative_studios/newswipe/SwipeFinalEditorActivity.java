package com.alternative_studios.newswipe;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.alternative_studios.newswipe.keyboard.FinalSwipes;
import com.alternative_studios.newswipe.keyboard.KeyboardLayout;
import com.alternative_studios.newswipe.keyboard.KeyboardTheme;
import com.alternative_studios.newswipe.ui.ExpressiveChoiceButton;
import com.alternative_studios.newswipe.ui.Ui;

/** 자음 키를 위·왼쪽·오른쪽으로 밀었을 때 입력되는 겹받침을 고치는 화면. */
public final class SwipeFinalEditorActivity extends Activity {

    /** 칸이 보이는 순서: 왼쪽, 위쪽, 오른쪽. */
    private static final int[] DISPLAY_ORDER = {FinalSwipes.LEFT, FinalSwipes.UP, FinalSwipes.RIGHT};

    private Prefs prefs;
    private LinearLayout list;
    private KeyboardTheme keyboardTheme;
    private String themeSignature;
    private int bg, textColor, hintColor;
    /** 칸마다 지난번에 그린 '정해졌는지': 바뀐 칸만 모양이 스프링으로 바뀌게 한다. */
    private final java.util.Map<String, Boolean> shown = new java.util.HashMap<>();

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
        bg = colors.bg;
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
        setContentView(new SettingsFrame(this, "겹받침 밀어서 글자 입력 편집", getString(R.string.app_name)).wrap(scroll));
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
        TextView note = text("자음 키를 각 방향으로 밀 때 입력되는 겹받침을 고릅니다. 아래쪽은 쌍자음 입력에 쓰여서 바꿀 수 없습니다."
                + (prefs.koreanNewSwipe() ? " NewSwipe 단모음에서는 ㅂ·ㅈ·ㄷ·ㄱ의 오른쪽이 거센소리 입력에 쓰여서 바꿀 수 없습니다." : ""), 13, hintColor);
        note.setPadding(Ui.dp(this, 4), 0, 0, Ui.dp(this, 4));
        list.addView(note);

        for (int i = 0; i < FinalSwipes.SOURCES.length(); i++) list.addView(keyCard(FinalSwipes.SOURCES.charAt(i)));
        list.addView(ResetDialog.button(this, "겹받침 밀어서 글자 입력 편집", () -> {
            prefs.resetFinalSwipes();
            render();
        }));
    }

    /** 자음 하나의 카드: 키 글자와 방향별 칸 세 개. 실제 키보드와 비슷하게 키보드 배경색 위에 흰 키를 놓는다. */
    private View keyCard(char consonant) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setBackground(Ui.round(keyboardTheme.background, Ui.dp(this, 16)));
        int p = Ui.dp(this, 8);
        card.setPadding(p, p, p, p);
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        clp.topMargin = Ui.dp(this, 12);
        card.setLayoutParams(clp);

        TextView label = text(String.valueOf(consonant), 28, keyboardTheme.text);
        label.setGravity(Gravity.CENTER);
        card.addView(label, new LinearLayout.LayoutParams(Ui.dp(this, 56), ViewGroup.LayoutParams.WRAP_CONTENT));
        // Material 3 Expressive 연결된 버튼 그룹: 정해진 칸은 강조색 알약, 없음은 안쪽 모서리만 작게 둥근 칸.
        ExpressiveChoiceButton[] cells = new ExpressiveChoiceButton[DISPLAY_ORDER.length];
        for (int i = 0; i < cells.length; i++) {
            cells[i] = cell(consonant, DISPLAY_ORDER[i]);
            card.addView(cells[i], cellParams());
        }
        card.setClipChildren(false);
        card.setBaselineAligned(false);   // 칸마다 글자 줄 수가 달라도 위아래로 어긋나지 않게
        ExpressiveChoiceButton.link(cells);
        return card;
    }

    private LinearLayout.LayoutParams cellParams() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, Ui.dp(this, 64), 1f);
        lp.setMargins(Ui.dp(this, 1), Ui.dp(this, 2.5f), Ui.dp(this, 1), Ui.dp(this, 2.5f));
        return lp;
    }

    private ExpressiveChoiceButton cell(char consonant, int dir) {
        // NewSwipe 배열: ㄱ·ㅂ의 오른쪽은 ㅋ·ㅍ 자리라 고정해서 보여 주고 바꿀 수 없게 한다.
        String fixed = prefs.koreanNewSwipe() && dir == FinalSwipes.RIGHT
                ? KeyboardLayout.nsRight(String.valueOf(consonant)) : null;
        if (fixed != null) {
            ExpressiveChoiceButton cell = new ExpressiveChoiceButton(this, fixed, false, false, false,
                    keyboardTheme.accent, keyboardTheme.onAccent, keyboardTheme.hint, keyboardTheme.hint);
            cell.setIdleColors(keyboardTheme.functionKey, keyboardTheme.functionKey);
            cell.setLabelSize(22);
            cell.setSubLabel(FinalSwipes.DIR_LABELS[dir] + " (고정)", 4, 22);
            cell.setClickable(false);
            return cell;
        }
        String current = FinalSwipes.get(prefs, consonant, dir);
        boolean assigned = current != null;
        String key = consonant + ":" + dir;
        boolean was = shown.containsKey(key) ? shown.get(key) : assigned;
        shown.put(key, assigned);
        ExpressiveChoiceButton cell = new ExpressiveChoiceButton(this, assigned ? current : "없음", was, false, false,
                keyboardTheme.accent, keyboardTheme.onAccent, keyboardTheme.hint, keyboardTheme.hint);
        cell.setIdleColors(keyboardTheme.key, keyboardTheme.keyPressed);
        cell.setLabelSize(assigned ? 22 : 14);
        cell.setSubLabel(FinalSwipes.DIR_LABELS[dir], 4, 22);
        cell.setOnClickListener(v -> edit(consonant, dir));
        if (was != assigned) cell.post(() -> cell.setChosen(assigned, true));   // 방금 바뀐 칸
        return cell;
    }

    private void edit(char consonant, int dir) {
        String options = FinalSwipes.options(consonant);
        String[] items = new String[options.length() + 1];
        for (int i = 0; i < options.length(); i++) items[i] = String.valueOf(options.charAt(i));
        items[options.length()] = "없음";
        String current = FinalSwipes.get(prefs, consonant, dir);
        int checked = current == null ? options.length() : options.indexOf(current.charAt(0));
        String dirName = FinalSwipes.DIR_NAMES[dir];
        AppTheme.dialogBuilder(this)
                .setTitle("'" + consonant + "' " + FinalSwipes.DIR_LABELS[dir] + "으로 밀기")
                .setSingleChoiceItems(items, checked, (d, which) -> {
                    prefs.setSwipeFinalOverride(consonant, dirName,
                            which < options.length() ? items[which] : "");
                    d.dismiss();
                    render();
                    if (android.os.Build.VERSION.SDK_INT >= 30) {
                        list.performHapticFeedback(android.view.HapticFeedbackConstants.CONFIRM);
                    }
                })
                .setNeutralButton("기본값", (d, w) -> {
                    prefs.resetSwipeFinalOverride(consonant, dirName);
                    render();
                })
                .setNegativeButton("취소", null)
                .show();
    }

    /** 뒤로 가기: 검색창이나 키보드 시험 입력창이 열려 있으면 그것부터 닫고, 그다음에 이전 화면으로 간다 (Android 12 이하). */
    @Override
    public void onBackPressed() {
        if (!SettingsFrame.consumeBack(this)) super.onBackPressed();
    }
}
