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
 * 모든 방향의 글자·기능을 키마다 직접 정한다 (정하지 않은 칸은 기본 배치). 칸에 보이는 값은 실제 자판을 만들어 읽은 값이라
 * 키보드에서 일어나는 일과 같다.
 */
final class KeyEditPanel {

    interface Host {
        /** 설정을 다시 읽어 미리보기를 바로 그리고, 고른 키를 새 자판에서 다시 찾아 돌려준다 (없으면 null). */
        Key refreshNow();

        /** 편집 창을 닫는다 (고른 키가 사라지게 바꾼 경우 등). */
        void close();

        /** 크기 설정(prefKey)이 이 칸에서 바뀌었다. */
        void onSizeChanged(String prefKey);
    }

    /** 맨 위 헤더 가운데에 보일 이름 (show()에서 정한다). */
    private String title = "";

    String title() {
        return title;
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

    /** 영역 창 안에 놓일 때는 '기본값으로 되돌리기'를 창 맨 아래에 한 번만 두므로 여기서는 만들지 않는다. */
    private boolean omitReset;

    void setOmitReset(boolean omit) {
        omitReset = omit;
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

        // ---- 누르기 (자음·모음 연속 탭처럼 모든 글자 키에 걸리는 설정은 '글자 키' 영역에 있다)
        if (",".equals(slot()) || ".".equals(slot())) {
            LinearLayout press = section("누르기");
            press.addView(valueRow("입력할 글자", prefs.keyChar(slot()), v -> editSlotChar(slot())));
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
            refreshKey();
            return;
        }
        prefs.setRepeatChar(g, s, false);
        if (mode == 2) {
            prefs.turnOffPopup(g, s);   // 정해 둔 문자는 넣어 두었다가 '문자 입력'으로 돌아오면 되살린다
        } else {
            String[] o = prefs.popupOverride(g, s);
            if (o != null && o.length == 0) prefs.turnOnPopup(g, s);   // '없음'에서 돌아오면 그전 문자로
            if (currentPopup().length == 0) editPopup();   // 기본 문자가 없는 키(숫자 등)는 바로 적게 한다
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
            body.addView(note("첫 번째 문자는 키 오른쪽 위에 작게 보입니다."
                    + (".".equals(slot()) ? " 이메일·인터넷 주소 입력란에서는 .com 같은 주소가 대신 나옵니다." : "")));
        } else if (mode == 1) {
            body.addView(note("누르고 있는 동안 이 글자를 계속 입력합니다. 길게 눌러 고를 문자는 나오지 않습니다."));
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
        card.addView(note("방향 칸을 눌러 그 방향으로 밀 때 입력할 글자를 정합니다. 비워 두면 그 방향은 아무것도 입력하지 않습니다."));
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
        swipePad.addView(dpad(key.label, labels, on, this::editSwipeText));
    }

    private String dialogTitle(int dir) {
        return "'" + key.label + "' " + SwipeCustom.label(dir) + "으로 밀기";
    }

    /** 글자 칸 아래에 놓는 빠른 선택: 이 키에 흔히 두는 글자 (쌍자음·거센소리·겹받침·이중모음, 온점 키는 쉼표). */
    private java.util.List<String> suggestions(int dir) {
        java.util.LinkedHashSet<String> out = new java.util.LinkedHashSet<>();
        String def = SwipeCustom.defaultFor(korean, prefs.koreanNewSwipe(), slot(), dir);
        if (def != null) out.add(def);
        if (".".equals(slot())) out.add(",");
        if (",".equals(slot())) out.add(".");
        if (hangul()) {
            char c = slot().charAt(0);
            if (vowel()) {
                if (VowelSwipes.iotizedFor(c) != null) out.add(VowelSwipes.iotizedFor(c));
                for (char o : VowelSwipes.optionsFor(c).toCharArray()) out.add(String.valueOf(o));
            } else {
                if (HangulComposer.doubled(c) != 0) out.add(String.valueOf(HangulComposer.doubled(c)));
                if (KeyboardLayout.nsRight(slot()) != null) out.add(KeyboardLayout.nsRight(slot()));
                for (char o : FinalSwipes.options(c).toCharArray()) out.add(String.valueOf(o));
            }
        }
        return new java.util.ArrayList<>(out);
    }

    /** 한 방향에 입력할 글자를 적는다. 아래의 빠른 선택을 누르면 그 글자가 칸에 들어간다. */
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
        java.util.List<String> picks = suggestions(dir);
        if (!picks.isEmpty()) {
            LinearLayout chips = new LinearLayout(a);
            chips.setPadding(0, Ui.dp(a, 4), 0, Ui.dp(a, 4));
            for (String pick : picks) {
                TextView chip = text(pick, 18, accentColor);
                chip.setGravity(Gravity.CENTER);
                chip.setMinWidth(Ui.dp(a, 44));
                chip.setPadding(Ui.dp(a, 10), Ui.dp(a, 6), Ui.dp(a, 10), Ui.dp(a, 6));
                chip.setBackground(Ui.ripple(accentColor & 0x00FFFFFF | 0x33000000,
                        Ui.round(accentColor & 0x00FFFFFF | 0x1F000000, Ui.dp(a, 10)), Ui.dp(a, 10)));
                chip.setOnClickListener(v -> {
                    input.setText(pick);
                    input.setSelection(input.getText().length());
                });
                LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT);
                clp.rightMargin = Ui.dp(a, 6);
                chips.addView(chip, clp);
            }
            android.widget.HorizontalScrollView hs = new android.widget.HorizontalScrollView(a);
            hs.setHorizontalScrollBarEnabled(false);
            hs.addView(chips);
            box.addView(hs);
        }
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

    private void buildFunctionKey() {
        String name = functionName();
        header(null, name, functionHint());
        if (key.type == Key.FUNCTION) {
            buildFnKey();
            return;
        }

        // ---- 누르기 (기능키의 순서·유무와 격자 정렬은 '기능키' 영역에 있다)
        if (key.type == Key.TO_SYMBOLS || key.type == Key.TO_LETTERS) {
            section("누르기").addView(toggle("기호 자판 대신 숫자 패드 사용",
                    "기호 키(?123)를 누르면 기호 자판 대신 3×4 숫자 패드가 열립니다. 숫자 패드의 ?!@ 키로 기호 자판을, 가·ABC 키로 글자 자판을 엽니다.",
                    prefs.numberPadForSymbols(), on -> put(Prefs.NUMBER_PAD_SYMBOLS, on)));
        } else if (key.type == Key.LANGUAGE) {
            LinearLayout press = section("누르기");
            press.addView(button("언어 순서 사용자화", v -> open(new Intent(a, LanguageOrderEditorActivity.class))));
            press.addView(note("🌐︎로 바꿀 언어와 '언어 고르기' 말풍선의 차례를 정합니다."));
        } else if (key.type == Key.SPACE) {
            buildSpacePress();
        } else if (key.type == Key.DELETE) {
            buildDeletePress();
        }

        // ---- 길게 누르기
        LinearLayout lp = section("길게 누르기");
        if (lpSlot() >= 0) buildLongPressAction(lp);

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
        String action = LongPressActions.SPEC.get(prefs, slot, LongPressActions.DIR);
        card.addView(valueRow("길게 누르면", SwipeAction.label(action),
                v -> pickAction(LongPressActions.SPEC, slot, LongPressActions.DIR,
                        "'" + functionName() + "' 길게 누르기", this::rebuild)));
        addLongPressDetail(card, action);
    }

    /**
     * 고른 길게 누르기 기능의 하위 항목: 누르고 있는 동안 지우는 기능이면 지우는 속도, 밀어서 선택이면 쓰는 법.
     * 지우는 속도는 같은 기능을 고른 모든 키가 함께 쓴다.
     */
    private void addLongPressDetail(LinearLayout card, String action) {
        if (SwipeAction.DELETE_REPEAT.equals(action)) {
            card.addView(subGroup(slider("지우는 속도", Prefs.DELETE_REPEAT_RATE, prefs.deleteRepeatRate(),
                    Prefs.DELETE_REPEAT_RATE_MIN, Prefs.DELETE_REPEAT_RATE_MAX, 1, "글자/초")));
        } else if (SwipeAction.DELETE_WORD_REPEAT.equals(action)) {
            card.addView(subGroup(slider("지우는 속도", Prefs.DELETE_WORD_REPEAT_RATE, prefs.deleteWordRepeatRate(),
                    Prefs.DELETE_WORD_REPEAT_RATE_MIN, Prefs.DELETE_WORD_REPEAT_RATE_MAX, 1, "단어/초")));
        } else if (SwipeAction.SPACE_SELECT.equals(action)) {
            card.addView(note("길게 눌러 진동이 오면, 손을 떼지 않고 좌우로 밀어 글자 단위로, 위아래로 밀어 줄 단위로 글자를 "
                    + "선택합니다. 밀지 않고 떼면 띄어쓰기가 입력됩니다."));
        }
    }

    private void buildFunctionSwipe() {
        int slot = fnSlot();
        LinearLayout card = section("밀기");
        card.addView(actionPad(FunctionSwipes.SPEC, slot, shortName()));
        if (key.type == Key.LANGUAGE) {
            card.addView(note("위쪽에 '언어 고르기'를 정하면 위로 밀었을 때 켜 둔 언어가 말풍선으로 올라옵니다. "
                    + "손을 떼지 않고 위아래로 옮겨 고릅니다."));
        } else if (key.type == Key.SPACE || key.type == Key.CURSOR_PAD) {
            card.addView(note("'커서 자유 이동'을 정한 방향으로 밀면 커서가 움직입니다 (좌우는 글자, 위아래는 줄 단위)."));
            card.addView(slider("커서 이동 속도 좌우", Prefs.SPACE_CURSOR_SPEED_H, prefs.spaceCursorSpeedH(), 50, 300, 10, "%"));
            card.addView(slider("커서 이동 속도 상하", Prefs.SPACE_CURSOR_SPEED_V, prefs.spaceCursorSpeedV(), 50, 300, 10, "%"));
            card.addView(note("스페이스바와 커서 키가 같은 속도를 씁니다. 값이 클수록 조금만 밀어도 커서가 빠르게 움직입니다."));
        }
    }

    /** 스페이스바 누르기: 두 번으로 마침표, 인식 범위, 언어 이름. */
    private void buildSpacePress() {
        LinearLayout press = section("누르기");
        press.addView(toggle("두 번 눌러 마침표", "스페이스바를 빠르게 두 번 누르면 '. '을 넣습니다.",
                prefs.doubleSpacePeriod(), on -> put(Prefs.DOUBLE_SPACE_PERIOD, on)));
        View shrink = subGroup(slider("줄이는 정도", Prefs.SPACE_HIT_SHRINK_PCT, prefs.spaceHitShrinkPct(), 5, 50, 5, "%"));
        shrink.setVisibility(prefs.spaceHitShrink() ? View.VISIBLE : View.GONE);
        press.addView(toggle("인식 범위 좁히기",
                "스페이스바의 위쪽 가장자리를 누르면 윗줄 키를 누른 것으로 간주해 스페이스바가 잘못 눌리는 일을 줄입니다.",
                prefs.spaceHitShrink(), on -> {
                    put(Prefs.SPACE_HIT_SHRINK, on);
                    Ui.setVisibleAnimated(shrink, on);
                }));
        press.addView(shrink);
        press.addView(toggle("언어 이름 보이기", "스페이스바에 지금 쓰는 언어를 작게 보여 줍니다.",
                prefs.spaceLangLabel(), on -> {
                    put(Prefs.SPACE_LANG_LABEL, on);
                    refreshKey();
                }));
    }

    /** ⌫ 누르기: 인식 범위. */
    private void buildDeletePress() {
        LinearLayout press = section("누르기");
        View shrink = subGroup(slider("줄이는 정도", Prefs.DELETE_HIT_SHRINK_PCT, prefs.deleteHitShrinkPct(), 5, 50, 5, "%"));
        shrink.setVisibility(prefs.deleteHitShrink() ? View.VISIBLE : View.GONE);
        press.addView(toggle("인식 범위 좁히기",
                "⌫의 가장자리를 누르면 ⌫ 대신 이웃 키를 누른 것으로 간주해 잘못 지워지는 일을 줄입니다. 아래쪽은 아랫줄이 글자 키일 때만 적용됩니다.",
                prefs.deleteHitShrink(), on -> {
                    put(Prefs.DELETE_HIT_SHRINK, on);
                    Ui.setVisibleAnimated(shrink, on);
                }));
        press.addView(shrink);
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
        addLongPressDetail(press, FnKeyActions.SPEC.get(prefs, FnKeyActions.PRESS, FnKeyActions.LONG));
        LinearLayout swipe = section("밀기");
        swipe.addView(actionPad(FnKeyActions.SPEC, FnKeyActions.SWIPE, "Fn"));
        resetRow("Fn 키의 기능", () -> {
            prefs.resetSwipeActions(FnKeyActions.SPEC.group);
            rebuild();
        });
    }

    // ================================================================ 도구 막대

    private void buildToolButton(String id) {
        header(null, ToolbarButtons.label(id), ToolbarButtons.hint(id) != null ? ToolbarButtons.hint(id) : "도구 막대 버튼");
        // 버튼의 순서·유무는 도구 막대(영역)에서 바꾼다.
        int slot = toolSlot(id);
        if (slot >= 0) buildToolbarSwipe(slot, ToolbarButtons.label(id));
        else section("버튼").addView(note("이 버튼은 따로 정할 것이 없습니다. 순서·유무는 도구 막대에서 바꿉니다."));
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
        header(null, "도구 막대", null);   // 제목만 (설명 줄은 두지 않는다)
        LinearLayout look = section("모양");
        View toolButtons = subGroup(button("도구 막대 버튼 사용자화", v -> open(new Intent(a, ToolbarButtonsEditorActivity.class))),
                note("도구 막대 버튼의 유무와 순서를 바꿉니다."));
        boolean shown = prefs.toolbar();
        toolButtons.setVisibility(shown ? View.VISIBLE : View.GONE);
        look.addView(toggle("도구 막대 표시", "다양한 도구를 빠르게 쓸 수 있는 막대를 보여 줍니다.", shown, on -> {
            put(Prefs.TOOLBAR, on);
            Ui.setVisibleAnimated(toolButtons, on);
        }));
        look.addView(toolButtons);
        // 위치와 높이는 도구 막대를 꺼도 추천 단어 줄에 그대로 쓰이므로 스위치와 따로 둔다.
        look.addView(toggle("자판 아래에 두기", "도구 막대를 자판 위가 아니라 아래에 놓습니다. ", prefs.toolbarBottom(),
                on -> put(Prefs.TOOLBAR_BOTTOM, on)));
        look.addView(slider("도구 막대 높이", Prefs.TOOLBAR_HEIGHT, prefs.toolbarHeight(), 70, 150, 5, "%"));
        look.addView(slider("도구 버튼 크기", Prefs.TOOL_BUTTON_SIZE, prefs.toolButtonSize(), 70, 150, 1, "%"));
        look.addView(note("도구 버튼의 아이콘은 막대보다 커지지 않습니다."));
        buildToolbarSwipe(ToolbarSwipes.BAR, "도구 막대");
        resetRow("도구 막대 밀기 기능", () -> {
            for (int dir : DIRS) ToolbarSwipes.SPEC.reset(prefs, ToolbarSwipes.BAR, dir);
            rebuild();
        });
    }

    private void buildToolbarSwipe(int slot, String name) {
        LinearLayout card = section("밀기");
        card.addView(actionPad(ToolbarSwipes.SPEC, slot, name));
        if (slot != ToolbarSwipes.BAR) card.addView(note("'없음'으로 둔 방향으로 밀면 도구 막대를 그 방향으로 민 것으로 봅니다."));
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

    /** 맨 위: 키 모양 그림과 설명. 이름은 창 헤더 가운데에 보인다 (title()). */
    private void header(String keyFace, String title, String subtitle) {
        this.title = title;
        LinearLayout row = new LinearLayout(a);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(Ui.dp(a, 4), Ui.dp(a, 6), 0, Ui.dp(a, 2));
        if (keyFace != null) {
            TextView face = text(keyFace, 24, kt.text);
            face.setGravity(Gravity.CENTER);
            face.setBackground(Ui.round(kt.key, Ui.dp(a, 12)));
            face.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            LinearLayout.LayoutParams flp = new LinearLayout.LayoutParams(Ui.dp(a, 48), Ui.dp(a, 48));
            flp.rightMargin = Ui.dp(a, 14);
            row.addView(face, flp);
        }
        if (subtitle != null && !subtitle.isEmpty()) {
            row.addView(text(subtitle, 14, hintColor), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        }
        if (row.getChildCount() > 0) root.addView(row);
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
        if (omitReset) return;
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
