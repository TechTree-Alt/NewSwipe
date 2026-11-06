package com.alternative_studios.newswipe;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.text.InputFilter;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.alternative_studios.newswipe.hangul.HangulComposer;
import com.alternative_studios.newswipe.keyboard.FinalSwipes;
import com.alternative_studios.newswipe.keyboard.FnKeyActions;
import com.alternative_studios.newswipe.keyboard.FunctionSwipes;
import com.alternative_studios.newswipe.keyboard.Icons;
import com.alternative_studios.newswipe.keyboard.Key;
import com.alternative_studios.newswipe.keyboard.KeyboardLayout;
import com.alternative_studios.newswipe.keyboard.KeyboardTheme;
import com.alternative_studios.newswipe.keyboard.LongPressActions;
import com.alternative_studios.newswipe.keyboard.SwipeAction;
import com.alternative_studios.newswipe.keyboard.SwipeCustom;
import com.alternative_studios.newswipe.keyboard.SwipeSpec;
import com.alternative_studios.newswipe.keyboard.ToolbarButtons;
import com.alternative_studios.newswipe.keyboard.ToolbarSwipes;
import com.alternative_studios.newswipe.keyboard.VowelSwipes;
import com.alternative_studios.newswipe.ui.Chevron;
import com.alternative_studios.newswipe.ui.ExpressiveChoiceButton;
import com.alternative_studios.newswipe.ui.ExpressiveSlider;
import com.alternative_studios.newswipe.ui.ExpressiveSwitch;
import com.alternative_studios.newswipe.ui.IconButton;
import com.alternative_studios.newswipe.ui.Ui;

/**
 * '키보드 편집'에서 미리보기의 키·도구 막대 버튼·도구 막대를 눌렀을 때 그 위에 뜨는 편집 칸.
 * 고른 것 하나의 누르기·길게 누르기·위아래 왼쪽 오른쪽 밀기·길게 눌러 연속 입력을 한곳에서 바꾼다.
 * <p>
 * 기본 밀기·길게 누르기 규칙(쌍자음, 겹받침, 단위 삭제 등)을 쓰는 동안에는 이 키에 해당하는 규칙 스위치를 보여 주고,
 * '완전 사용자화'를 켜면 방향마다 글자·기능을 직접 정하는 칸이 된다. 칸에 보이는 값은 실제 자판을 만들어 읽은 값이라
 * 키보드에서 일어나는 일과 같다.
 */
final class KeyEditPanel {

    interface Host {
        /** 설정을 다시 읽어 미리보기를 바로 그리고, 고른 키를 새 자판에서 다시 찾아 돌려준다 (없으면 null). */
        Key refreshNow();

        /** 편집 칸을 닫는다 (고른 것도 푼다). */
        void close();

        /** 크기 설정(prefKey)이 이 칸에서 바뀌었다. */
        void onSizeChanged(String prefKey);
    }

    private static final int[] DIRS = SwipeCustom.DIRS;   // 위, 아래, 왼쪽, 오른쪽
    private static final int MAX_SWIPE_LENGTH = 8;

    private final Activity a;
    private final Prefs prefs;
    private final Host host;
    private final KeyboardTheme kt;
    private final int textColor, hintColor, accentColor, onAccentColor, cardColor;
    /** 칸을 이루는 뷰들. */
    private final LinearLayout root;
    /** 손잡이로 바뀔 수 있는 크기 슬라이더: 설정 키 → 값을 다시 읽어 맞추는 일. */
    private final java.util.Map<String, Runnable> sizeSync = new java.util.HashMap<>();

    private KeyboardPreview.Selection sel;
    private Key key;
    /** 고른 키의 자판이 한글인지 (길게 누르기·밀기 설정의 묶음 이름을 정한다). */
    private boolean korean;

    KeyEditPanel(Activity a, Prefs prefs, Host host) {
        this.a = a;
        this.prefs = prefs;
        this.host = host;
        kt = KeyboardTheme.of(a);
        AppTheme colors = AppTheme.of(a);
        textColor = colors.text;
        hintColor = colors.hint;
        accentColor = colors.accent;
        onAccentColor = colors.onAccent;
        cardColor = colors.card;
        root = new LinearLayout(a);
        root.setOrientation(LinearLayout.VERTICAL);
    }

    View view() {
        return root;
    }

    /** 고른 것을 편집하는 칸을 새로 만든다. */
    void show(KeyboardPreview.Selection s, Key k) {
        sel = s;
        key = k;
        korean = s.tab == KeyboardPreview.TAB_KOREAN || s.tab == KeyboardPreview.TAB_SYMBOLS;
        root.removeAllViews();
        sizeSync.clear();
        if (s.kind == KeyboardPreview.SEL_TOOLBAR) buildToolbar();
        else if (s.kind == KeyboardPreview.SEL_TOOL_BUTTON) buildToolButton(s.toolId);
        else if (k.type == Key.CHAR) buildCharKey();
        else buildFunctionKey();
    }

    /** 미리보기 손잡이로 크기가 바뀌었을 때, 이 칸에 같은 값의 슬라이더가 있으면 맞춘다. */
    void syncSize(String prefKey) {
        Runnable r = sizeSync.get(prefKey);
        if (r != null) r.run();
    }

    // ================================================================ 글자 키

    private String slot() {
        return key.slot();
    }

    private String group() {
        return KeyboardLayout.groupOf(korean, slot());
    }

    /** 한글 자판의 한글 자음·모음 키인지. */
    private boolean hangul() {
        return sel.tab == KeyboardPreview.TAB_KOREAN && KeyboardLayout.GROUP_KO.equals(group()) && slot().length() == 1;
    }

    private boolean vowel() {
        return hangul() && HangulComposer.isVowel(slot().charAt(0));
    }

    /** 밀어서 글자 입력을 정할 수 있는 키: 한글·영어 글자 키와 쉼표·온점 키 (숫자 줄·기호는 밀어서 입력하지 않는다). */
    private boolean swipeEditable() {
        String s = slot();
        if (",".equals(s) || ".".equals(s)) return true;
        String g = group();
        return (KeyboardLayout.GROUP_KO.equals(g) && sel.tab == KeyboardPreview.TAB_KOREAN)
                || (KeyboardLayout.GROUP_EN.equals(g) && sel.tab == KeyboardPreview.TAB_ENGLISH);
    }

    private void buildCharKey() {
        String shown = key.label;
        String kind;
        String g = group();
        if (",".equals(slot())) kind = "쉼표 자리의 키 · 모든 자판이 함께 씁니다";
        else if (".".equals(slot())) kind = "온점 자리의 키 · 모든 자판이 함께 씁니다";
        else if (KeyboardLayout.GROUP_NUM.equals(g)) kind = "숫자 줄 · 한글·영어 자판이 함께 씁니다";
        else if (KeyboardLayout.GROUP_SYM.equals(g)) kind = "기호 · 같은 기호는 모든 자판이 함께 씁니다";
        else if (vowel()) kind = "한글 모음";
        else if (hangul()) kind = "한글 자음";
        else kind = "영어 글자";
        header(shown, "'" + shown + "' 키", kind);

        // ---- 누르기
        LinearLayout press = null;
        if (",".equals(slot()) || ".".equals(slot())) {
            press = section("누르기");
            press.addView(valueRow("입력할 글자", prefs.keyChar(slot()), v -> editSlotChar(slot())));
        }
        if (hangul() && !vowel() && HangulComposer.doubled(slot().charAt(0)) != 0) {
            if (press == null) press = section("누르기");
            press.addView(toggle("두 번 눌러 쌍자음",
                    "같은 자음을 빠르게 두 번 누르면 쌍자음을 입력합니다 (" + slot() + slot() + "→"
                            + HangulComposer.doubled(slot().charAt(0)) + "). 모든 자음에 적용됩니다.",
                    prefs.doubleTapConsonant(), on -> put(Prefs.DOUBLE_TAP_CONSONANT, on)));
        }
        if (vowel() && VowelSwipes.iotizedFor(slot().charAt(0)) != null && !prefs.koreanDubeolsik()) {
            if (press == null) press = section("누르기");
            press.addView(toggle("두 번 눌러 이중모음",
                    "같은 모음을 연달아 누르면 이중모음을 입력합니다 (" + slot() + slot() + "→"
                            + VowelSwipes.iotizedFor(slot().charAt(0)) + "). 모든 모음에 적용됩니다.",
                    prefs.doubleTapVowel(), on -> put(Prefs.DOUBLE_TAP_VOWEL, on)));
        }

        // ---- 길게 누르기 (문자 입력 · 연속 입력 · 없음)
        LinearLayout lp = section("길게 누르기");
        LinearLayout lpBody = new LinearLayout(a);
        lpBody.setOrientation(LinearLayout.VERTICAL);
        lp.addView(Ui.choiceRow(a, new String[]{"문자 입력", "연속 입력", "없음"}, longPressMode(), accentColor, onAccentColor,
                textColor, hintColor, 0, i -> {
                    setLongPressMode(i);
                    fillLongPress(lpBody);
                }));
        lp.addView(lpBody);
        fillLongPress(lpBody);

        // ---- 밀기
        if (swipeEditable()) buildCharSwipe();

        // ---- 숫자 줄
        if (KeyboardLayout.GROUP_NUM.equals(g)) {
            LinearLayout row = section("숫자 줄");
            row.addView(slider("숫자 줄 높이", Prefs.NUMBER_ROW_HEIGHT, prefs.numberRowHeight(),
                    Prefs.NUMBER_ROW_HEIGHT_MIN, Prefs.NUMBER_ROW_HEIGHT_MAX, 1, "%"));
            row.addView(toggle("숫자 줄 표시", "끄면 자판 위의 1~0 숫자 줄이 사라집니다.", prefs.numberRow(), on -> {
                put(Prefs.NUMBER_ROW, on);
                if (!on) host.close();   // 고른 숫자 키가 사라졌다
            }));
        }

        resetRow("'" + shown + "' 키의 길게 누르기·밀기", this::resetCharKey);
    }

    /** 0 = 문자 입력, 1 = 연속 입력, 2 = 없음. */
    private int longPressMode() {
        if (prefs.repeatChar(group(), slot())) return 1;
        String[] items = currentPopup();
        return items.length == 0 ? 2 : 0;
    }

    /** 길게 눌러 입력할 문자 (고친 것이 있으면 그것, 없으면 기본값). */
    private String[] currentPopup() {
        String[] o = prefs.popupOverride(group(), slot());
        if (o != null) return o;
        String[] d = KeyboardLayout.defaultPopup(korean, prefs, slot(), prefs.periodComma());
        return d == null ? new String[0] : d;
    }

    private void setLongPressMode(int mode) {
        String g = group(), s = slot();
        if (mode == 1) {
            prefs.setRepeatChar(g, s, true);
            if (!prefs.longPressRepeat()) {
                put(Prefs.LONG_PRESS_REPEAT, true);
                Toast.makeText(a, "'길게 눌러 연속 입력'을 켰습니다", Toast.LENGTH_SHORT).show();
            }
            refreshKey();
            return;
        }
        prefs.setRepeatChar(g, s, false);
        if (mode == 2) {
            prefs.setPopupOverride(g, s, new String[0]);
        } else {
            String[] o = prefs.popupOverride(g, s);
            if (o != null && o.length == 0) prefs.resetPopupOverride(g, s);   // '없음'에서 돌아오면 기본 문자로
            if (currentPopup().length == 0) editPopup();   // 기본 문자가 없는 키(숫자 등)는 바로 적게 한다
            if (!prefs.longPressChars()) {
                put(Prefs.LONG_PRESS_CHARS, true);
                Toast.makeText(a, "'길게 눌러 문자 입력'을 켰습니다", Toast.LENGTH_SHORT).show();
            }
        }
        refreshKey();
    }

    /** 미리보기를 지금 설정으로 다시 그리고 고른 키를 새 자판에서 다시 잡는다. */
    private void refreshKey() {
        Key k = host.refreshNow();
        if (k != null) key = k;
    }

    private void fillLongPress(LinearLayout body) {
        body.removeAllViews();
        int mode = longPressMode();
        if (mode == 0) {
            String[] items = currentPopup();
            body.addView(valueRow("길게 눌러 고를 문자", items.length == 0 ? "없음" : joinItems(items), v -> editPopup()));
            if (!prefs.longPressChars()) {
                body.addView(note("'길게 눌러 문자 입력'이 꺼져 있어 모든 키에서 쓰지 않습니다."));
                body.addView(toggle("길게 눌러 문자 입력", "모든 키에 적용됩니다.", false, on -> {
                    put(Prefs.LONG_PRESS_CHARS, on);
                    refreshKey();
                    body.postDelayed(() -> fillLongPress(body), 300);   // 스위치가 움직인 뒤 안내를 맞춘다
                }));
            } else {
                body.addView(note("첫 번째 문자는 키 오른쪽 위에 작게 보입니다."
                        + (".".equals(slot()) ? " 이메일·인터넷 주소 입력란에서는 .com 같은 주소가 대신 나옵니다." : "")));
            }
        } else if (mode == 1) {
            body.addView(note("누르고 있는 동안 이 글자를 계속 입력합니다. 길게 눌러 고를 문자는 나오지 않습니다."));
            if (!prefs.longPressRepeat()) {
                body.addView(toggle("길게 눌러 연속 입력", "꺼져 있어 지금은 쓰지 않습니다. 모든 키에 적용됩니다.", false, on -> {
                    put(Prefs.LONG_PRESS_REPEAT, on);
                    refreshKey();
                    body.postDelayed(() -> fillLongPress(body), 300);
                }));
            }
        } else {
            body.addView(note("길게 눌러도 아무것도 하지 않습니다."));
        }
    }

    private static String joinItems(String[] items) {
        boolean single = true;
        for (String it : items) if (it.codePointCount(0, it.length()) != 1) single = false;
        return String.join(single ? " " : ", ", items);
    }

    // ---- 글자 키 밀기

    private LinearLayout swipePad;

    private void buildCharSwipe() {
        LinearLayout card = section("밀기");
        swipePad = new LinearLayout(a);
        swipePad.setOrientation(LinearLayout.VERTICAL);
        card.addView(swipePad);
        fillCharPad();

        LinearLayout standard = new LinearLayout(a);
        standard.setOrientation(LinearLayout.VERTICAL);
        addCharRules(standard);
        View customNote = note("방향 칸을 눌러 입력할 글자를 직접 정합니다. 비워 두면 그 방향은 아무것도 입력하지 않습니다.");
        boolean custom = prefs.swipeCustom();
        standard.setVisibility(custom ? View.GONE : View.VISIBLE);
        customNote.setVisibility(custom ? View.VISIBLE : View.GONE);
        card.addView(standard);
        card.addView(customNote);
        card.addView(toggle("🛠️ 밀어서 글자 입력 완전 사용자화",
                "모든 키를 위·아래·왼쪽·오른쪽으로 밀 때 입력할 글자를 키마다 직접 정합니다. 켜면 쌍자음·겹받침 같은 규칙 대신 정한 글자를 씁니다.",
                custom, on -> {
                    put(Prefs.SWIPE_CUSTOM, on);
                    Ui.setVisibleAnimated(standard, !on);
                    Ui.setVisibleAnimated(customNote, on);
                    refreshCharPad();
                }));
    }

    /** 이 키에 해당하는 기본 밀기 규칙 스위치 (완전 사용자화를 끈 동안 쓴다). */
    private void addCharRules(LinearLayout box) {
        if (".".equals(slot())) {
            box.addView(toggle("온점 키를 위로 밀어서 쉼표 입력", "위로 밀면 쉼표(,)를 입력합니다.", prefs.periodSwipeComma(),
                    on -> rule(Prefs.PERIOD_SWIPE_COMMA, on)));
        }
        if (!hangul()) {
            if (!".".equals(slot())) box.addView(note("기본 설정에서는 이 키를 밀어 입력하는 글자가 없습니다. 직접 정하려면 아래 완전 사용자화를 켜세요."));
            return;
        }
        char c = slot().charAt(0);
        if (vowel()) {
            if (VowelSwipes.iotizedFor(c) != null) {
                box.addView(toggle("밀어서 ㅣ계 이중모음", "아래로 밀어 이중모음을 입력합니다 (" + c + " → " + VowelSwipes.iotizedFor(c) + "). 모든 모음에 적용됩니다.",
                        prefs.swipeIotized(), on -> rule(Prefs.SWIPE_IOTIZED, on)));
            }
            if (!VowelSwipes.optionsFor(c).isEmpty()) {
                box.addView(toggle("밀어서 조합형 이중모음", "위·왼쪽·오른쪽으로 밀어 조합형 이중모음을 입력합니다. 방향 칸을 눌러 고를 수 있습니다.",
                        prefs.swipeCompoundVowel(), on -> rule(Prefs.SWIPE_COMPOUND_VOWEL, on)));
            }
            return;
        }
        if (HangulComposer.doubled(c) != 0) {
            box.addView(toggle("밀어서 쌍자음", "아래로 밀어 쌍자음을 입력합니다 (" + c + " → " + HangulComposer.doubled(c) + "). 모든 자음에 적용됩니다.",
                    prefs.swipeDouble(), on -> rule(Prefs.SWIPE_DOUBLE, on)));
        }
        if (c == 'ㅅ' && prefs.koreanNewSwipe()) {
            box.addView(toggle("ㅅ을 위로 밀어서 ㅆ 입력", "아래로 밀 때뿐만 아니라 위로 밀 때도 ㅆ을 입력합니다 ('밀어서 쌍자음'을 켰을 때).",
                    prefs.ssUp(), on -> rule(Prefs.D7_SS_UP, on)));
        }
        if (FinalSwipes.SOURCES.indexOf(c) >= 0) {
            box.addView(toggle("밀어서 겹받침", "위·왼쪽·오른쪽으로 밀어 겹받침을 입력합니다. 방향 칸을 눌러 고를 수 있습니다.",
                    prefs.swipeFinal(), on -> rule(Prefs.SWIPE_FINAL, on)));
        }
    }

    private void rule(String prefKey, boolean on) {
        put(prefKey, on);
        refreshCharPad();
    }

    private void refreshCharPad() {
        refreshKey();
        fillCharPad();
    }

    private void fillCharPad() {
        swipePad.removeAllViews();
        String[] labels = new String[4];
        boolean[] on = new boolean[4];
        for (int dir : DIRS) {
            String s = key.swipeText(dir);
            on[dir] = s != null && !s.isEmpty();
            labels[dir] = on[dir] ? s : "없음";
        }
        swipePad.addView(dpad(key.label, labels, on, this::editCharSwipe));
    }

    /** 방향 칸을 눌렀을 때: 완전 사용자화면 글자를 적고, 아니면 그 방향의 규칙에 맞는 것을 고르거나 직접 정하기로 넘어간다. */
    private void editCharSwipe(int dir) {
        if (prefs.swipeCustom()) {
            editSwipeText(dir);
            return;
        }
        String title = dialogTitle(dir);
        String cur = key.swipeText(dir);
        if (hangul()) {
            char c = slot().charAt(0);
            if (vowel()) {
                if (dir == Key.SWIPE_DOWN && VowelSwipes.iotizedFor(c) != null) {
                    explainRule(dir, cur, "밀어서 ㅣ계 이중모음", Prefs.SWIPE_IOTIZED, prefs.swipeIotized());
                    return;
                }
                if (dir != Key.SWIPE_DOWN && !VowelSwipes.optionsFor(c).isEmpty()) {
                    if (!prefs.swipeCompoundVowel()) {
                        explainRule(dir, null, "밀어서 조합형 이중모음", Prefs.SWIPE_COMPOUND_VOWEL, false);
                    } else {
                        pickVowel(c, dir, title);
                    }
                    return;
                }
            } else {
                if (dir == Key.SWIPE_DOWN && HangulComposer.doubled(c) != 0) {
                    explainRule(dir, cur, "밀어서 쌍자음", Prefs.SWIPE_DOUBLE, prefs.swipeDouble());
                    return;
                }
                if (dir == Key.SWIPE_RIGHT && prefs.koreanNewSwipe() && KeyboardLayout.nsRight(slot()) != null) {
                    explainFixed(dir, cur, "NewSwipe 단모음에서는 오른쪽으로 밀어 거센소리를 입력합니다 (" + key.label + " → " + cur + ").");
                    return;
                }
                if (dir == Key.SWIPE_UP && c == 'ㅅ' && "ㅆ".equals(cur)) {
                    explainRule(dir, cur, "ㅅ을 위로 밀어서 ㅆ 입력", Prefs.D7_SS_UP, true);
                    return;
                }
                int fd = finalDir(dir);
                if (fd >= 0 && FinalSwipes.SOURCES.indexOf(c) >= 0) {
                    if (!prefs.swipeFinal()) explainRule(dir, null, "밀어서 겹받침", Prefs.SWIPE_FINAL, false);
                    else pickFinal(c, fd, title);
                    return;
                }
            }
        }
        if (".".equals(slot()) && dir == Key.SWIPE_UP) {
            explainRule(dir, cur, "온점 키를 위로 밀어서 쉼표 입력", Prefs.PERIOD_SWIPE_COMMA, prefs.periodSwipeComma());
            return;
        }
        explainFixed(dir, cur, null);
    }

    private static int finalDir(int dir) {
        return dir == Key.SWIPE_UP ? FinalSwipes.UP : dir == Key.SWIPE_LEFT ? FinalSwipes.LEFT
                : dir == Key.SWIPE_RIGHT ? FinalSwipes.RIGHT : -1;
    }

    private String dialogTitle(int dir) {
        return "'" + key.label + "' " + SwipeCustom.label(dir) + "으로 밀기";
    }

    private static final String CUSTOM_HELP = "글자를 직접 정하려면 '밀어서 글자 입력 완전 사용자화'를 켭니다. 켜면 모든 키의 밀기를 "
            + "키마다 직접 정하고, 쌍자음·겹받침 같은 규칙 스위치 대신 정한 글자를 씁니다.";

    /** 규칙 스위치 하나가 정하는 방향: 규칙을 켜고 끄거나, 직접 정하기로 넘어간다. */
    private void explainRule(int dir, String cur, String ruleName, String prefKey, boolean ruleOn) {
        String msg = ruleOn && cur != null
                ? "지금 입력되는 글자: " + cur + " ('" + ruleName + "' 규칙)"
                : "'" + ruleName + "' 규칙이 꺼져 있어 지금은 아무것도 입력하지 않습니다.";
        AppTheme.dialogBuilder(a)
                .setTitle(dialogTitle(dir))
                .setMessage(msg + "\n\n" + CUSTOM_HELP)
                .setPositiveButton("직접 정하기", (d, w) -> startCustom(dir))
                .setNeutralButton(ruleOn ? "규칙 끄기" : "규칙 켜기", (d, w) -> {
                    put(prefKey, !ruleOn);
                    rebuild();
                })
                .setNegativeButton("취소", null)
                .show();
    }

    /** 규칙이 없거나 바꿀 수 없는 방향. */
    private void explainFixed(int dir, String cur, String why) {
        String msg = why != null ? why : cur != null
                ? "지금 입력되는 글자: " + cur
                : "지금은 이 방향으로 밀어도 글자를 입력하지 않습니다.";
        AppTheme.dialogBuilder(a)
                .setTitle(dialogTitle(dir))
                .setMessage(msg + "\n\n" + CUSTOM_HELP)
                .setPositiveButton("직접 정하기", (d, w) -> startCustom(dir))
                .setNegativeButton("취소", null)
                .show();
    }

    /** 밀어서 글자 입력 완전 사용자화를 켜고 바로 그 방향의 글자를 적게 한다. */
    private void startCustom(int dir) {
        put(Prefs.SWIPE_CUSTOM, true);
        Toast.makeText(a, "'밀어서 글자 입력 완전 사용자화'를 켰습니다", Toast.LENGTH_SHORT).show();
        rebuild();
        editSwipeText(dir);
    }

    private void pickFinal(char c, int fd, String title) {
        String options = FinalSwipes.options(c);
        String[] items = new String[options.length() + 1];
        for (int i = 0; i < options.length(); i++) items[i] = String.valueOf(options.charAt(i));
        items[options.length()] = "없음";
        String current = FinalSwipes.get(prefs, c, fd);
        int checked = current == null ? options.length() : options.indexOf(current.charAt(0));
        String dirName = FinalSwipes.DIR_NAMES[fd];
        AppTheme.dialogBuilder(a)
                .setTitle(title)
                .setSingleChoiceItems(items, checked, (d, which) -> {
                    prefs.setSwipeFinalOverride(c, dirName, which < options.length() ? items[which] : "");
                    d.dismiss();
                    confirmed();
                    refreshCharPad();
                })
                .setNeutralButton("기본값", (d, w) -> {
                    prefs.resetSwipeFinalOverride(c, dirName);
                    refreshCharPad();
                })
                .setNegativeButton("취소", null)
                .show();
    }

    private void pickVowel(char c, int dir, String title) {
        String options = VowelSwipes.optionsFor(c);
        String[] items = new String[options.length() + 1];
        for (int i = 0; i < options.length(); i++) items[i] = String.valueOf(options.charAt(i));
        items[options.length()] = "없음";
        String current = VowelSwipes.compoundFor(prefs, c, dir);
        int checked = current == null ? options.length() : Math.max(0, options.indexOf(current.charAt(0)));
        String dirName = SwipeCustom.dirName(dir);
        AppTheme.dialogBuilder(a)
                .setTitle(title)
                .setSingleChoiceItems(items, checked, (d, which) -> {
                    prefs.setSwipeVowelOverride(c, dirName, which < options.length() ? items[which] : "");
                    d.dismiss();
                    confirmed();
                    refreshCharPad();
                })
                .setNeutralButton("기본값", (d, w) -> {
                    prefs.resetSwipeVowelOverride(c, dirName);
                    refreshCharPad();
                })
                .setNegativeButton("취소", null)
                .show();
    }

    /** 완전 사용자화: 한 방향에 입력할 글자를 적는다. */
    private void editSwipeText(int dir) {
        String g = group(), s = slot();
        String dirName = SwipeCustom.dirName(dir);
        EditText input = new EditText(a);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        input.setSingleLine(true);
        input.setFilters(new InputFilter[]{new InputFilter.LengthFilter(MAX_SWIPE_LENGTH)});
        String cur = SwipeCustom.get(prefs, korean, s, dir);
        input.setText(cur == null ? "" : cur);
        input.setSelection(input.getText().length());
        input.setGravity(Gravity.CENTER);
        input.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22);
        LinearLayout box = dialogBox("입력할 글자를 적어 주세요 (최대 " + MAX_SWIPE_LENGTH
                + "자). 비워 두면 이 방향은 아무것도 입력하지 않습니다.");
        box.addView(input);
        AlertDialog d = AppTheme.dialogBuilder(a)
                .setTitle(dialogTitle(dir))
                .setView(box)
                .setPositiveButton("저장", (dlg, w) -> {
                    prefs.setSwipeCustomOverride(g, s, dirName, input.getText().toString());
                    confirmed();
                    refreshCharPad();
                })
                .setNeutralButton("기본값", (dlg, w) -> {
                    prefs.resetSwipeCustomOverride(g, s, dirName);
                    refreshCharPad();
                })
                .setNegativeButton("취소", null)
                .show();
        showKeyboardFor(d, input);
    }

    /** 길게 눌러 고를 문자를 한 칸에 하나씩 고치는 대화상자 (띄어쓰기가 있는 문장도 한 항목). */
    private void editPopup() {
        String g = group(), s = slot();
        LinearLayout box = dialogBox("길게 눌렀을 때 고를 문자를 한 칸에 하나씩 적어 주세요. 띄어쓰기가 있는 문장도 됩니다. "
                + "첫 번째 칸이 키 위에 작게 표시되고, 모두 비우면 길게 누르기가 없어집니다.");
        LinearLayout rows = new LinearLayout(a);
        rows.setOrientation(LinearLayout.VERTICAL);
        box.addView(rows);
        for (String item : currentPopup()) addPopupRow(rows, item, false);
        // 입력란이 하나도 없는 채로 대화상자를 띄우면 시스템이 '글자를 칠 수 없는 창'으로 보고 키보드를 막는다.
        if (rows.getChildCount() == 0) addPopupRow(rows, "", true);
        TextView add = text("+ 문자 추가", 15, accentColor);
        add.setGravity(Gravity.CENTER_VERTICAL);
        add.setPadding(Ui.dp(a, 4), Ui.dp(a, 12), Ui.dp(a, 4), Ui.dp(a, 12));
        add.setOnClickListener(v -> addPopupRow(rows, "", true));
        box.addView(add);
        ScrollView scroll = new ScrollView(a);
        scroll.addView(box);
        AlertDialog d = AppTheme.dialogBuilder(a)
                .setTitle("'" + key.label + "' 길게 누르기")
                .setView(scroll)
                .setPositiveButton("저장", (dlg, w) -> {
                    java.util.List<String> items = new java.util.ArrayList<>();
                    for (int i = 0; i < rows.getChildCount(); i++) {
                        EditText input = (EditText) ((LinearLayout) rows.getChildAt(i)).getChildAt(0);
                        // 줄바꿈은 저장 형식의 구분자라 넣을 수 없다 (한 줄 입력란이라 보통 들어오지 않는다).
                        String v = input.getText().toString().replace('\n', ' ').replace('\r', ' ').trim();
                        if (!v.isEmpty()) items.add(v);
                    }
                    prefs.setPopupOverride(g, s, items.toArray(new String[0]));
                    prefs.setRepeatChar(g, s, false);
                    confirmed();
                    rebuild();
                })
                .setNeutralButton("기본값", (dlg, w) -> {
                    prefs.resetPopupOverride(g, s);
                    rebuild();
                })
                .setNegativeButton("취소", (dlg, w) -> rebuild())
                .show();
        android.view.Window window = d.getWindow();
        if (window != null) {
            // 칸을 더한 뒤에도 키보드가 올라오게 하고, 올라와도 칸이 가려지지 않게 창 크기를 맞춘다.
            window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM);
            window.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
                    | android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);
        }
    }

    private void addPopupRow(LinearLayout rows, String value, boolean focus) {
        LinearLayout row = new LinearLayout(a);
        row.setGravity(Gravity.CENTER_VERTICAL);
        EditText input = new EditText(a);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        input.setSingleLine(true);
        input.setHint("문자 또는 문장");
        input.setText(value);
        row.addView(input, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        int press = (textColor & 0x00FFFFFF) | 0x33000000;
        IconButton remove = new IconButton(a, Icons.CLOSE, hintColor, press, "이 문자 지우기");
        remove.setOnClickListener(v -> rows.removeView(row));
        row.addView(remove, new LinearLayout.LayoutParams(Ui.dp(a, 40), Ui.dp(a, 40)));
        rows.addView(row);
        if (focus) input.requestFocus();
    }

    /** 쉼표·온점 자리의 키가 입력할 한 글자. */
    private void editSlotChar(String slot) {
        EditText input = new EditText(a);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        input.setSingleLine(true);
        input.setFilters(new InputFilter[]{new InputFilter.LengthFilter(2)});   // 코드 포인트 하나는 최대 두 칸
        input.setText(prefs.keyChar(slot));
        input.setSelection(input.getText().length());
        input.setGravity(Gravity.CENTER);
        input.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22);
        LinearLayout box = dialogBox("이 키가 입력할 글자를 한 글자만 적어 주세요 (예: ? ! …). 길게 누르기·밀기 설정은 그대로 이어집니다.");
        box.addView(input);
        AlertDialog d = AppTheme.dialogBuilder(a)
                .setTitle(",".equals(slot) ? "쉼표 자리의 키" : "온점 자리의 키")
                .setView(box)
                .setPositiveButton("저장", null)
                .setNeutralButton("기본값", (dlg, w) -> {
                    prefs.setKeyChar(slot, slot);
                    rebuild();
                })
                .setNegativeButton("취소", null)
                .create();
        d.setOnShowListener(x -> d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String s = input.getText().toString();
            if (s.isEmpty() || s.codePointCount(0, s.length()) != 1 || Character.isWhitespace(s.codePointAt(0))) {
                Toast.makeText(a, "공백이 아닌 한 글자만 적어 주세요", Toast.LENGTH_SHORT).show();
                return;
            }
            prefs.setKeyChar(slot, s);
            d.dismiss();
            confirmed();
            rebuild();
        }));
        d.show();
        showKeyboardFor(d, input);
    }

    private void resetCharKey() {
        String g = group(), s = slot();
        prefs.resetPopupOverride(g, s);
        prefs.setRepeatChar(g, s, false);
        for (int dir : DIRS) prefs.resetSwipeCustomOverride(g, s, SwipeCustom.dirName(dir));
        if (hangul()) {
            char c = s.charAt(0);
            if (FinalSwipes.SOURCES.indexOf(c) >= 0) for (String dn : FinalSwipes.DIR_NAMES) prefs.resetSwipeFinalOverride(c, dn);
            if (vowel()) for (int dir : DIRS) prefs.resetSwipeVowelOverride(c, SwipeCustom.dirName(dir));
        }
        rebuild();
    }

    // ================================================================ 기능키

    /** 길게 누르기 칸 (LongPressActions), 밀기 칸 (FunctionSwipes). 없으면 -1. */
    private int lpSlot() {
        return LongPressActions.slotOf(key.type);
    }

    private int fnSlot() {
        return FunctionSwipes.slotOf(key.type);
    }

    private String functionName() {
        if (key.type == Key.FUNCTION) return "Fn 키";
        if (key.type == Key.SPACE) return "스페이스바 (␣)";
        int lp = lpSlot();
        if (lp >= 0) return LongPressActions.SPEC.cardTitle(lp);
        int fn = fnSlot();
        return fn >= 0 ? FunctionSwipes.SPEC.cardTitle(fn) : "기능키";
    }

    private String functionHint() {
        if (key.type == Key.FUNCTION) return "한글 자판의 Shift 자리. 누르기·길게 누르기·밀기마다 기능을 정합니다.";
        if (key.type == Key.SPACE) return "띄어쓰기";
        int lp = lpSlot();
        if (lp >= 0) return LongPressActions.SPEC.cardHint(lp);
        int fn = fnSlot();
        return fn >= 0 ? FunctionSwipes.SPEC.cardHint(fn) : "";
    }

    /** 네 방향 판 가운데에 쓰는 짧은 이름. */
    private String shortName() {
        switch (key.type) {
            case Key.SPACE: return "␣";
            case Key.DELETE: return "⌫";
            case Key.ENTER: return "↵";
            case Key.SHIFT: return "⇧";
            case Key.LANGUAGE: return "🌐︎";
            case Key.EMOJI:
            case Key.EMOJI_KEY: return "☺";
            case Key.CLIPBOARD: return "📋︎";
            case Key.CURSOR_PAD: return "✥";
            case Key.VOICE: return "🎤︎";
            default: return key.label == null || key.label.isEmpty() ? "키" : key.label;
        }
    }

    private boolean bottomRowKey() {
        switch (key.type) {
            case Key.TO_SYMBOLS:
            case Key.TO_LETTERS:
            case Key.LANGUAGE:
            case Key.EMOJI:
            case Key.SPACE:
            case Key.ENTER:
            case Key.EMOJI_KEY:
            case Key.CLIPBOARD:
            case Key.CURSOR_PAD:
            case Key.VOICE:
                return true;
            default:
                return false;
        }
    }

    private void buildFunctionKey() {
        String name = functionName();
        header(null, name, functionHint());
        if (key.type == Key.FUNCTION) {
            buildFnKey();
            return;
        }

        // ---- 누르기
        LinearLayout press = section("누르기");
        if (key.type == Key.TO_SYMBOLS || key.type == Key.TO_LETTERS) {
            press.addView(toggle("기호 자판 대신 숫자 패드 사용",
                    "기호 키(?123)를 누르면 기호 자판 대신 3×4 숫자 패드가 열립니다. 숫자 패드의 ?!@ 키로 기호 자판을, 가·ABC 키로 글자 자판을 엽니다.",
                    prefs.numberPadForSymbols(), on -> put(Prefs.NUMBER_PAD_SYMBOLS, on)));
        }
        if (key.type == Key.LANGUAGE) {
            press.addView(button("언어 순서 사용자화", v -> open(new Intent(a, LanguageOrderEditorActivity.class))));
        }
        if (bottomRowKey() || key.type == Key.SHIFT || key.type == Key.DELETE) {
            press.addView(button(key.type == Key.SHIFT || key.type == Key.DELETE ? "Shift·⌫와 맨 아래 줄 키 순서·유무"
                    : "맨 아래 줄 키 순서·유무 바꾸기", v -> open(new Intent(a, BottomKeysEditorActivity.class))));
        }
        if (press.getChildCount() == 0) press.addView(note(functionHint()));

        // ---- 길게 누르기
        LinearLayout lp = section("길게 누르기");
        if (key.type == Key.SPACE) {
            lp.addView(toggle("스페이스바를 길게 누른 뒤 밀어서 선택",
                    "길게 눌러 진동이 오면, 손을 떼지 않고 좌우로 밀어 글자 단위로, 위아래로 밀어 줄 단위로 글자를 선택합니다. "
                            + "밀지 않고 떼면 띄어쓰기가 입력됩니다.",
                    prefs.spaceSelect(), on -> put(Prefs.SPACE_SELECT, on)));
        } else if (lpSlot() >= 0) {
            buildLongPressAction(lp);
        }

        // ---- 밀기
        if (fnSlot() >= 0) buildFunctionSwipe();

        resetRow(name + "의 길게 누르기·밀기 기능", () -> {
            if (lpSlot() >= 0) LongPressActions.SPEC.reset(prefs, lpSlot(), LongPressActions.DIR);
            if (fnSlot() >= 0) for (int dir : DIRS) FunctionSwipes.SPEC.reset(prefs, fnSlot(), dir);
            rebuild();
        });
    }

    private void buildLongPressAction(LinearLayout card) {
        int slot = lpSlot();
        LinearLayout standard = new LinearLayout(a);
        standard.setOrientation(LinearLayout.VERTICAL);
        switch (key.type) {
            case Key.DELETE:
                standard.addView(toggle("길게 눌러 지우기", "길게 누르고 있으면 계속 지웁니다. 끄면 한 글자씩만 지워집니다.",
                        prefs.longPressDelete(), on -> put(Prefs.LONG_PRESS_DELETE, on)));
                break;
            case Key.TO_SYMBOLS:
            case Key.TO_LETTERS:
                standard.addView(toggle("길게 눌러 이모지 창 열기", "기호 키(숫자 패드의 ?!@·가·ABC 포함)를 길게 누르면 이모지 창을 엽니다.",
                        prefs.modeKeyLongPress(), on -> put(Prefs.MODE_KEY_LONG_PRESS, on)));
                break;
            case Key.LANGUAGE:
            case Key.EMOJI:
                standard.addView(note("길게 누르면 입력 방법 전환 창을 엽니다."));
                break;
            default:
                standard.addView(note("기본 설정에서는 길게 눌러도 아무것도 하지 않습니다."));
                break;
        }
        LinearLayout custom = new LinearLayout(a);
        custom.setOrientation(LinearLayout.VERTICAL);
        Runnable fill = () -> {
            custom.removeAllViews();
            String action = LongPressActions.SPEC.get(prefs, slot, LongPressActions.DIR);
            custom.addView(valueRow("길게 누르면", SwipeAction.label(action),
                    v -> pickAction(LongPressActions.SPEC, slot, LongPressActions.DIR,
                            "'" + functionName() + "' 길게 누르기", this::rebuild)));
        };
        fill.run();
        boolean on = prefs.longPressCustom();
        standard.setVisibility(on ? View.GONE : View.VISIBLE);
        custom.setVisibility(on ? View.VISIBLE : View.GONE);
        card.addView(standard);
        card.addView(custom);
        card.addView(toggle("🛠️ 기능키 길게 누르기 기능 완전 사용자화",
                "Shift 키·기호 키·🌐︎·엔터 키·⌫ 등을 길게 누를 때 실행할 기능을 키마다 직접 정합니다.", on, checked -> {
                    put(Prefs.LONG_PRESS_CUSTOM, checked);
                    Ui.setVisibleAnimated(standard, !checked);
                    Ui.setVisibleAnimated(custom, checked);
                }));
    }

    private void buildFunctionSwipe() {
        int slot = fnSlot();
        LinearLayout card = section("밀기");
        LinearLayout pad = new LinearLayout(a);
        pad.setOrientation(LinearLayout.VERTICAL);
        Runnable fillPad = () -> {
            pad.removeAllViews();
            pad.addView(actionPad(FunctionSwipes.SPEC, slot, shortName()));
        };
        fillPad.run();

        LinearLayout standard = new LinearLayout(a);
        standard.setOrientation(LinearLayout.VERTICAL);
        addFunctionRules(standard);
        boolean on = prefs.swipeFnCustom();
        pad.setVisibility(on ? View.VISIBLE : View.GONE);
        standard.setVisibility(on ? View.GONE : View.VISIBLE);
        card.addView(pad);
        card.addView(standard);
        card.addView(toggle("🛠️ 기능키 밀어서 기능 완전 사용자화",
                "기호 키·🌐︎·스페이스바·엔터 키·⌫ 등을 위·아래·왼쪽·오른쪽으로 밀 때 실행할 기능을 키마다 직접 정합니다.",
                on, checked -> {
                    put(Prefs.SWIPE_FN_CUSTOM, checked);
                    Ui.setVisibleAnimated(standard, !checked);
                    Ui.setVisibleAnimated(pad, checked);
                }));
    }

    /** 기능키 밀기의 기본 규칙 중 이 키에 해당하는 것 (완전 사용자화를 끈 동안 쓴다). */
    private void addFunctionRules(LinearLayout box) {
        switch (key.type) {
            case Key.SPACE: {
                ExpressiveSwitch[] lang = new ExpressiveSwitch[1], horizontal = new ExpressiveSwitch[1];
                View axes = subGroup(
                        toggle("좌우 이동", "글자 단위로 커서를 옮깁니다.", prefs.spaceCursorH(), checked -> {
                            put(Prefs.SPACE_CURSOR_H, checked);
                            // 스페이스바를 좌우로 밀기는 커서 이동과 언어 전환 중 하나만 쓴다.
                            if (checked && lang[0] != null && lang[0].isChecked()) lang[0].toggle();
                        }),
                        toggle("상하 이동", "줄 단위로 커서를 옮깁니다 (여러 줄 입력란).", prefs.spaceCursorV(),
                                checked -> put(Prefs.SPACE_CURSOR_V, checked)));
                horizontal[0] = switchOf(((ViewGroup) axes).getChildAt(0));
                axes.setVisibility(prefs.spaceCursor() ? View.VISIBLE : View.GONE);
                box.addView(toggle("밀어서 커서 이동", "스페이스바를 밀어 커서를 옮깁니다.", prefs.spaceCursor(), checked -> {
                    put(Prefs.SPACE_CURSOR, checked);
                    Ui.setVisibleAnimated(axes, checked);
                }));
                box.addView(axes);
                View langRow = toggle("밀어서 언어 전환", "좌우로 밀면 언어를 전환합니다.", prefs.spaceLangSwipe(), checked -> {
                    put(Prefs.SPACE_LANG_SWIPE, checked);
                    if (checked && horizontal[0].isChecked()) horizontal[0].toggle();
                });
                lang[0] = switchOf(langRow);
                box.addView(langRow);
                break;
            }
            case Key.LANGUAGE:
            case Key.EMOJI: {
                View order = subGroup(button("언어 순서 사용자화", v -> open(new Intent(a, LanguageOrderEditorActivity.class))));
                order.setVisibility(prefs.globeSwipeLang() ? View.VISIBLE : View.GONE);
                box.addView(toggle("위로 밀어 언어 고르기", "🌐︎을 위로 밀어 언어를 선택합니다.", prefs.globeSwipeLang(), checked -> {
                    put(Prefs.GLOBE_SWIPE_LANG, checked);
                    Ui.setVisibleAnimated(order, checked);
                }));
                box.addView(order);
                break;
            }
            case Key.DELETE:
                box.addView(toggle("밀어서 단위 삭제", "왼쪽으로 밀면 단어를, 위로 밀면 줄에서 커서 왼쪽을 모두 지웁니다. "
                                + "커서가 줄의 맨 앞이면 위로 밀었을 때 윗줄과 합칩니다.",
                        prefs.deleteWordSwipe(), checked -> put(Prefs.DELETE_WORD_SWIPE, checked)));
                break;
            case Key.TO_SYMBOLS:
            case Key.TO_LETTERS:
                box.addView(toggle("위로 밀어서 이모지 열기", "기호 키를 위로 밀면 이모지 창이 열립니다.",
                        prefs.modeKeyEmoji(), checked -> put(Prefs.MODE_KEY_EMOJI, checked)));
                break;
            case Key.CURSOR_PAD:
                box.addView(note("상하좌우로 밀면 커서가 움직입니다 (스페이스바 커서 이동과 같은 속도)."));
                break;
            default:
                box.addView(note("기본 설정에서는 밀어도 아무것도 하지 않습니다. 기능을 정하려면 아래 완전 사용자화를 켜세요."));
                break;
        }
    }

    /** Fn 키: 누르기·길게 누르기·네 방향 밀기를 모두 직접 정한다. */
    private void buildFnKey() {
        LinearLayout press = section("누르기·길게 누르기");
        for (int dir : FnKeyActions.SPEC.dirs(FnKeyActions.PRESS)) {
            String action = FnKeyActions.SPEC.get(prefs, FnKeyActions.PRESS, dir);
            press.addView(valueRow(FnKeyActions.SPEC.dirLabel(FnKeyActions.PRESS, dir), SwipeAction.label(action),
                    v -> pickAction(FnKeyActions.SPEC, FnKeyActions.PRESS, dir,
                            FnKeyActions.SPEC.dialogTitle(FnKeyActions.PRESS, dir), this::rebuild)));
        }
        LinearLayout swipe = section("밀기");
        swipe.addView(actionPad(FnKeyActions.SPEC, FnKeyActions.SWIPE, "Fn"));
        LinearLayout more = section("Fn 키 자리");
        more.addView(button("Shift·⌫와 맨 아래 줄 키 순서·유무", v -> open(new Intent(a, BottomKeysEditorActivity.class))));
        resetRow("Fn 키의 기능", () -> {
            prefs.resetSwipeActions(FnKeyActions.SPEC.group);
            rebuild();
        });
    }

    // ================================================================ 도구 막대

    private void buildToolButton(String id) {
        header(null, ToolbarButtons.label(id), ToolbarButtons.hint(id) != null ? ToolbarButtons.hint(id) : "도구 막대 버튼");
        LinearLayout press = section("버튼");
        press.addView(button("도구 막대 버튼 순서·유무 바꾸기", v -> open(new Intent(a, ToolbarButtonsEditorActivity.class))));
        int slot = toolSlot(id);
        if (slot >= 0) buildToolbarSwipe(slot, ToolbarButtons.label(id));
        resetRow(ToolbarButtons.label(id) + "의 밀기 기능", () -> {
            if (slot >= 0) for (int dir : DIRS) ToolbarSwipes.SPEC.reset(prefs, slot, dir);
            rebuild();
        });
    }

    private static int toolSlot(String id) {
        for (int i = 0; i < ToolbarSwipes.SPEC.slotCount(); i++) if (ToolbarSwipes.SPEC.slotName(i).equals(id)) return i;
        return -1;
    }

    private void buildToolbar() {
        header(null, "도구 막대", "자판 위(또는 아래)의 버튼 줄. 추천 단어도 여기에 뜹니다.");
        LinearLayout look = section("모양");
        look.addView(slider("도구 막대 높이", Prefs.TOOLBAR_HEIGHT, prefs.toolbarHeight(), 70, 150, 1, "%"));
        look.addView(slider("도구 버튼 크기", Prefs.TOOL_BUTTON_SIZE, prefs.toolButtonSize(), 70, 150, 1, "%"));
        look.addView(toggle("자판 아래에 두기", "도구 막대를 자판 위가 아니라 아래에 놓습니다.", prefs.toolbarBottom(),
                on -> put(Prefs.TOOLBAR_BOTTOM, on)));
        look.addView(button("도구 막대 버튼 순서·유무 바꾸기", v -> open(new Intent(a, ToolbarButtonsEditorActivity.class))));
        look.addView(toggle("도구 막대 표시", "끄면 추천 단어가 뜰 때만 이 줄이 보입니다.", true, on -> {
            put(Prefs.TOOLBAR, on);
            if (!on) host.close();
        }));
        buildToolbarSwipe(ToolbarSwipes.BAR, "도구 막대");
        resetRow("도구 막대 밀기 기능", () -> {
            for (int dir : DIRS) ToolbarSwipes.SPEC.reset(prefs, ToolbarSwipes.BAR, dir);
            rebuild();
        });
    }

    private void buildToolbarSwipe(int slot, String name) {
        LinearLayout card = section("밀기");
        View pad = actionPad(ToolbarSwipes.SPEC, slot, name);
        LinearLayout standard = new LinearLayout(a);
        standard.setOrientation(LinearLayout.VERTICAL);
        switch (slot) {
            case ToolbarSwipes.BAR:
                standard.addView(toggle("도구 막대를 밀어서 커서 이동", "왼쪽으로 밀면 줄의 맨 앞으로, 오른쪽으로 밀면 줄의 맨 뒤로, "
                                + "위·아래로 밀면 한 줄 위·아래로 이동합니다 (여러 줄 입력란).",
                        prefs.toolbarSwipe(), on -> put(Prefs.TOOLBAR_SWIPE, on)));
                break;
            case ToolbarSwipes.CLIPBOARD:
                standard.addView(toggle("밀어서 복사·붙여넣기", "위로 밀면 선택한 텍스트를 복사하고, 아래로 밀면 지금 클립보드의 내용을 붙여 넣습니다.",
                        prefs.toolClipboardSwipe(), on -> put(Prefs.TOOL_CLIPBOARD_SWIPE, on)));
                break;
            case ToolbarSwipes.EMOJI:
                standard.addView(toggle("아래로 밀어서 최근 이모지 입력", "아래로 밀면 마지막으로 쓴 이모지를 바로 입력합니다.",
                        prefs.toolEmojiSwipe(), on -> put(Prefs.TOOL_EMOJI_SWIPE, on)));
                break;
            case ToolbarSwipes.UNDO:
                standard.addView(toggle("아래로 밀어서 다시 실행", "아래로 밀면 되돌린 것을 다시 실행합니다 (Ctrl+Shift+Z).",
                        prefs.toolUndoSwipe(), on -> put(Prefs.TOOL_UNDO_SWIPE, on)));
                break;
            case ToolbarSwipes.ONE_HAND:
                standard.addView(note("왼쪽·오른쪽으로 밀면 그쪽으로 쏠린 한 손 모드가 됩니다."));
                break;
            default:
                standard.addView(note("기본 설정에서는 이 버튼을 밀면 도구 막대를 민 것으로 봅니다."));
                break;
        }
        boolean on = prefs.swipeToolbarCustom();
        pad.setVisibility(on ? View.VISIBLE : View.GONE);
        standard.setVisibility(on ? View.GONE : View.VISIBLE);
        card.addView(pad);
        card.addView(standard);
        card.addView(toggle("🛠️ 도구 막대 밀어서 기능 완전 사용자화",
                "도구 막대와 도구 막대의 버튼을 위·아래·왼쪽·오른쪽으로 밀 때 실행할 기능을 직접 정합니다. "
                        + "'없음'으로 둔 방향으로 밀면 도구 막대를 그 방향으로 민 것으로 봅니다.",
                on, checked -> {
                    put(Prefs.SWIPE_TOOLBAR_CUSTOM, checked);
                    Ui.setVisibleAnimated(standard, !checked);
                    Ui.setVisibleAnimated(pad, checked);
                }));
    }

    // ================================================================ 공통 조각

    /** 바꾼 뒤 이 칸을 다시 만든다 (고른 키를 새 자판에서 다시 찾는다). */
    private void rebuild() {
        Key k = host.refreshNow();
        if (sel.kind == KeyboardPreview.SEL_KEY && k == null) {
            host.close();
            return;
        }
        show(sel, k);
    }

    private void put(String prefKey, boolean on) {
        prefs.raw().edit().putBoolean(prefKey, on).apply();
    }

    private void open(Intent i) {
        a.startActivity(i);
    }

    private void confirmed() {
        if (android.os.Build.VERSION.SDK_INT >= 30) root.performHapticFeedback(android.view.HapticFeedbackConstants.CONFIRM);
    }

    /** 맨 위: 키 모양 그림, 이름, 설명, 닫기 버튼. */
    private void header(String keyFace, String title, String subtitle) {
        LinearLayout row = new LinearLayout(a);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(Ui.dp(a, 4), Ui.dp(a, 14), 0, Ui.dp(a, 2));
        if (keyFace != null) {
            TextView face = text(keyFace, 24, kt.text);
            face.setGravity(Gravity.CENTER);
            face.setBackground(Ui.round(kt.key, Ui.dp(a, 12)));
            face.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            LinearLayout.LayoutParams flp = new LinearLayout.LayoutParams(Ui.dp(a, 52), Ui.dp(a, 52));
            flp.rightMargin = Ui.dp(a, 14);
            row.addView(face, flp);
        }
        LinearLayout texts = new LinearLayout(a);
        texts.setOrientation(LinearLayout.VERTICAL);
        TextView t = text(title, 20, textColor);
        t.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        texts.addView(t);
        if (subtitle != null && !subtitle.isEmpty()) texts.addView(text(subtitle, 13, hintColor));
        row.addView(texts, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        int press = (textColor & 0x00FFFFFF) | 0x33000000;
        IconButton close = new IconButton(a, Icons.CLOSE, textColor, press, "편집 닫기");
        close.setOnClickListener(v -> host.close());
        row.addView(close, new LinearLayout.LayoutParams(Ui.dp(a, 44), Ui.dp(a, 44)));
        root.addView(row);
    }

    private LinearLayout section(String name) {
        TextView h = text(name, 14, accentColor);
        h.setPadding(Ui.dp(a, 4), Ui.dp(a, 16), 0, Ui.dp(a, 8));
        root.addView(h);
        LinearLayout card = new LinearLayout(a);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(Ui.round(cardColor, Ui.dp(a, 16)));
        int p = Ui.dp(a, 16);
        card.setPadding(p, Ui.dp(a, 8), p, Ui.dp(a, 8));
        root.addView(card, matchWrap());
        return card;
    }

    private TextView text(String s, int sp, int color) {
        TextView t = new TextView(a);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color);
        return t;
    }

    private TextView note(String s) {
        TextView t = text(s, 13, hintColor);
        t.setLineSpacing(Ui.dp(a, 3), 1f);
        t.setPadding(0, Ui.dp(a, 6), 0, Ui.dp(a, 6));
        return t;
    }

    private LinearLayout dialogBox(String help) {
        int pad = Ui.dp(a, 20);
        LinearLayout box = new LinearLayout(a);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(pad, Ui.dp(a, 8), pad, 0);
        TextView t = text(help, 13, hintColor);
        t.setPadding(0, 0, 0, Ui.dp(a, 8));
        box.addView(t);
        return box;
    }

    private void showKeyboardFor(AlertDialog d, EditText input) {
        android.view.Window window = d.getWindow();
        if (window == null) return;
        window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM);
        window.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);
        input.requestFocus();
    }

    private View toggle(String label, String desc, boolean value, java.util.function.Consumer<Boolean> onChange) {
        LinearLayout row = new LinearLayout(a);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, Ui.dp(a, 8), 0, Ui.dp(a, 8));
        LinearLayout texts = new LinearLayout(a);
        texts.setOrientation(LinearLayout.VERTICAL);
        texts.addView(text(label, 16, textColor));
        if (desc != null) texts.addView(text(desc, 13, hintColor));
        row.addView(texts, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        ExpressiveSwitch sw = new ExpressiveSwitch(a, accentColor, onAccentColor, hintColor, cardColor);
        sw.setCheckedImmediately(value);
        sw.setOnCheckedChangeListener(checked -> onChange.accept(checked));
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        slp.leftMargin = Ui.dp(a, 12);
        row.addView(sw, slp);
        row.setOnClickListener(v -> sw.performClick());
        return row;
    }

    private static ExpressiveSwitch switchOf(View toggleRow) {
        ViewGroup row = (ViewGroup) toggleRow;
        return (ExpressiveSwitch) row.getChildAt(row.getChildCount() - 1);
    }

    /** 위 스위치의 하위 항목: 왼쪽에 강조선을 두고 들여 쓴다. */
    private View subGroup(View... children) {
        LinearLayout box = new LinearLayout(a);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(Ui.dp(a, 14), 0, 0, 0);
        android.graphics.drawable.GradientDrawable line = new android.graphics.drawable.GradientDrawable();
        line.setColor((accentColor & 0x00FFFFFF) | 0x66000000);
        line.setCornerRadius(Ui.dp(a, 2));
        android.graphics.drawable.LayerDrawable bg = new android.graphics.drawable.LayerDrawable(
                new android.graphics.drawable.Drawable[]{line});
        bg.setLayerSize(0, Ui.dp(a, 3), -1);
        bg.setLayerGravity(0, Gravity.START | Gravity.FILL_VERTICAL);
        bg.setLayerInset(0, Ui.dp(a, 2), Ui.dp(a, 8), 0, Ui.dp(a, 8));
        box.setBackground(bg);
        for (View c : children) box.addView(c);
        return box;
    }

    private View slider(String label, String prefKey, int value, int min, int max, int step, String unit) {
        LinearLayout box = new LinearLayout(a);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(0, Ui.dp(a, 8), 0, Ui.dp(a, 4));
        LinearLayout top = new LinearLayout(a);
        top.addView(text(label, 16, textColor), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView v = text(value + unit, 14, hintColor);
        top.addView(v);
        box.addView(top);
        ExpressiveSlider bar = new ExpressiveSlider(a, accentColor);
        bar.setMax((max - min) / step);
        bar.setProgress(Math.round((Math.max(min, Math.min(max, value)) - min) / (float) step));
        bar.setOnChangeListener((progress, fromUser) -> {
            int val = min + progress * step;
            v.setText(val + unit);
            if (fromUser) {
                prefs.raw().edit().putInt(prefKey, val).apply();
                host.onSizeChanged(prefKey);
            }
        });
        box.addView(bar, matchWrap());
        sizeSync.put(prefKey, () -> {
            int now = prefs.raw().getInt(prefKey, value);
            v.setText(Math.max(min, Math.min(max, now)) + unit);
            bar.setProgress(Math.round((Math.max(min, Math.min(max, now)) - min) / (float) step));
        });
        return box;
    }

    private View button(String label, View.OnClickListener l) {
        LinearLayout row = new LinearLayout(a);
        row.setGravity(Gravity.CENTER_VERTICAL);
        int h = Ui.dp(a, 14);
        row.setPadding(h, Ui.dp(a, 12), h, Ui.dp(a, 12));
        int tint = (accentColor & 0x00FFFFFF) | 0x1F000000;
        row.setBackground(Ui.ripple(accentColor & 0x00FFFFFF | 0x33000000, Ui.round(tint, Ui.dp(a, 12)), Ui.dp(a, 12)));
        row.addView(text(label, 15, accentColor), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        row.addView(new Chevron(a, accentColor));
        row.setClickable(true);
        row.setFocusable(true);
        row.setOnClickListener(l);
        LinearLayout.LayoutParams lp = matchWrap();
        lp.topMargin = Ui.dp(a, 6);
        lp.bottomMargin = Ui.dp(a, 6);
        row.setLayoutParams(lp);
        return row;
    }

    /** 이름과 지금 값을 보여 주는 누를 수 있는 줄 (누르면 값을 고치는 창이 뜬다). */
    private View valueRow(String label, String value, View.OnClickListener l) {
        LinearLayout row = new LinearLayout(a);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, Ui.dp(a, 12), 0, Ui.dp(a, 12));
        row.addView(text(label, 16, textColor), new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        TextView v = text(value, 16, accentColor);
        v.setGravity(Gravity.END);
        v.setMaxLines(2);
        v.setEllipsize(android.text.TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams vlp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        vlp.leftMargin = Ui.dp(a, 12);
        vlp.rightMargin = Ui.dp(a, 6);
        row.addView(v, vlp);
        row.addView(new Chevron(a, accentColor));
        row.setBackground(Ui.ripple(hintColor & 0x00FFFFFF | 0x33000000, null, Ui.dp(a, 8)));
        row.setClickable(true);
        row.setFocusable(true);
        row.setOnClickListener(l);
        return row;
    }

    /** 맨 아래 '기본값으로 되돌리기' (이 키·버튼의 값만). */
    private void resetRow(String name, Runnable onReset) {
        root.addView(ResetDialog.button(a, name, onReset));
    }

    /**
     * 위·아래·왼쪽·오른쪽 칸을 십자 모양으로 놓은 판: 가운데는 키, 둘레는 그 방향으로 밀었을 때 입력·실행하는 것.
     * labels·on은 Key.SWIPE_* 순서. 정해진 방향은 강조색 알약으로 보인다.
     */
    private View dpad(String center, String[] labels, boolean[] on, java.util.function.IntConsumer onTap) {
        LinearLayout pad = new LinearLayout(a);
        pad.setOrientation(LinearLayout.VERTICAL);
        pad.setBackground(Ui.round(kt.background, Ui.dp(a, 16)));
        int p = Ui.dp(a, 6);
        pad.setPadding(p, p, p, p);
        int[][] grid = {{-1, Key.SWIPE_UP, -1}, {Key.SWIPE_LEFT, -2, Key.SWIPE_RIGHT}, {-1, Key.SWIPE_DOWN, -1}};
        for (int[] line : grid) {
            LinearLayout row = new LinearLayout(a);
            row.setClipChildren(false);
            row.setBaselineAligned(false);
            for (int dir : line) {
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, Ui.dp(a, 58), 1f);
                int m = Ui.dp(a, 2.5f);
                lp.setMargins(m, m, m, m);
                if (dir == -1) {
                    row.addView(new View(a), lp);
                    continue;
                }
                if (dir == -2) {
                    ExpressiveChoiceButton c = new ExpressiveChoiceButton(a, center, false, false, false,
                            kt.accent, kt.onAccent, kt.text, kt.text);
                    c.setIdleColors(kt.functionKey, kt.functionKey);
                    c.setLabelSize(center.length() > 3 ? 14 : 22);
                    c.setClickable(false);
                    c.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
                    row.addView(c, lp);
                    continue;
                }
                ExpressiveChoiceButton c = new ExpressiveChoiceButton(a, labels[dir], on[dir], false, false,
                        kt.accent, kt.onAccent, kt.text, kt.hint);
                c.setIdleColors(kt.key, kt.keyPressed);
                c.setLabelSize(on[dir] && labels[dir].codePointCount(0, labels[dir].length()) <= 3 ? 20 : 13);
                c.setSubLabel(SwipeCustom.label(dir), 3, 12);
                c.setContentDescription(SwipeCustom.label(dir) + "으로 밀기: " + labels[dir]);
                c.setOnClickListener(v -> onTap.accept(dir));
                row.addView(c, lp);
            }
            pad.addView(row);
        }
        return pad;
    }

    /** 기능 배치(spec)의 한 칸을 네 방향 판으로: 칸을 누르면 기능 목록에서 고른다. */
    private View actionPad(SwipeSpec spec, int slot, String center) {
        String[] labels = new String[4];
        boolean[] on = new boolean[4];
        for (int dir : DIRS) {
            String action = spec.get(prefs, slot, dir);
            on[dir] = !SwipeAction.NONE.equals(action);
            labels[dir] = SwipeAction.shortLabel(action);
        }
        LinearLayout box = new LinearLayout(a);
        box.setOrientation(LinearLayout.VERTICAL);
        box.addView(dpad(center, labels, on, dir -> pickAction(spec, slot, dir, spec.dialogTitle(slot, dir), this::rebuild)));
        String hint = spec.cardHint(slot);
        if (hint != null && !hint.isEmpty() && spec != FunctionSwipes.SPEC) box.addView(note(hint));
        return box;
    }

    private void pickAction(SwipeSpec spec, int slot, int dir, String title, Runnable done) {
        String[] ids = spec.choices(slot, dir);
        String[] items = new String[ids.length];
        for (int i = 0; i < ids.length; i++) items[i] = SwipeAction.label(ids[i]);
        int checked = java.util.Arrays.asList(ids).indexOf(spec.get(prefs, slot, dir));
        AppTheme.dialogBuilder(a)
                .setTitle(title)
                .setSingleChoiceItems(items, checked, (d, which) -> {
                    spec.set(prefs, slot, dir, ids[which]);
                    d.dismiss();
                    confirmed();
                    done.run();
                })
                .setNeutralButton("기본값", (d, w) -> {
                    spec.reset(prefs, slot, dir);
                    done.run();
                })
                .setNegativeButton("취소", null)
                .show();
    }

    private static LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }
}
