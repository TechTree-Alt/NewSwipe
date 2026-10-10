package com.alternative_studios.newswipe;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.os.Bundle;
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

import com.alternative_studios.newswipe.keyboard.Icons;
import com.alternative_studios.newswipe.suggest.Shortcuts;
import com.alternative_studios.newswipe.ui.Chevron;
import com.alternative_studios.newswipe.ui.IconButton;
import com.alternative_studios.newswipe.ui.Ui;

import java.util.Map;

/** 단축어(줄임말 → 문장)를 추가·고치고·지우는 화면. 줄임말을 입력하면 그 문장이 추천란에 뜬다. */
public final class ShortcutEditorActivity extends Activity {

    private Prefs prefs;
    private LinearLayout list;
    private String themeSignature;
    private int bg, cardColor, textColor, hintColor, accentColor;

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(AppTheme.wrap(base));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = new Prefs(this);
        AppTheme colors = AppTheme.of(this);
        themeSignature = AppTheme.signature(this);
        bg = colors.bg;
        cardColor = colors.card;
        textColor = colors.text;
        hintColor = colors.hint;
        accentColor = colors.accent;

        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(bg);
        scroll.setFillViewport(true);
        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        int pad = Ui.dp(this, 16);
        list.setPadding(pad, 0, pad, Ui.dp(this, 32));
        scroll.addView(list);
        setContentView(new SettingsFrame(this, "단축어 편집", getString(R.string.app_name)).wrap(scroll));
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
        TextView note = text("줄임말을 입력하면 정해 둔 문장이 추천란에 뜹니다 (예: 'ㅈㄱㅈ' → '지금 가는 중'). "
                + "줄임말은 한글(자음·모음 포함)이나 영어로만 정할 수 있고, 영어는 대소문자를 가리지 않습니다. "
                + "문장을 눌러 입력하면 줄임말이 그 문장으로 바뀝니다.", 13, hintColor);
        note.setLineSpacing(Ui.dp(this, 3), 1f);
        note.setPadding(Ui.dp(this, 4), 0, 0, Ui.dp(this, 8));
        list.addView(note);

        list.addView(actionRow("단축어 추가", v -> edit(null)));

        Map<String, String> all = prefs.shortcuts();
        if (all.isEmpty()) {
            TextView empty = text("아직 정한 단축어가 없습니다.", 14, hintColor);
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(0, Ui.dp(this, 32), 0, Ui.dp(this, 32));
            list.addView(empty);
            return;
        }
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(Ui.round(cardColor, Ui.dp(this, 16)));
        card.setPadding(0, Ui.dp(this, 4), 0, Ui.dp(this, 4));
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        clp.topMargin = Ui.dp(this, 12);
        list.addView(card, clp);
        for (Map.Entry<String, String> e : all.entrySet()) {
            if (card.getChildCount() > 0) card.addView(divider());
            card.addView(entryRow(e.getKey(), e.getValue()));
        }

        TextView clear = text("단축어 모두 지우기", 15, accentColor);
        clear.setGravity(Gravity.CENTER);
        clear.setPadding(0, Ui.dp(this, 14), 0, Ui.dp(this, 14));
        clear.setBackground(Ui.ripple(accentColor & 0x00FFFFFF | 0x33000000,
                Ui.round(accentColor & 0x00FFFFFF | 0x1F000000, Ui.dp(this, 12)), Ui.dp(this, 12)));
        clear.setOnClickListener(v -> AppTheme.accentBuilder(AppTheme.dialogContext(this))
                .setTitle("단축어 모두 지우기")
                .setMessage("정해 둔 단축어 " + all.size() + "개를 모두 지웁니다. 되돌릴 수 없습니다.")
                .setPositiveButton("모두 지우기", (d, w) -> {
                    prefs.clearShortcuts();
                    render();
                })
                .setNegativeButton("취소", null)
                .show());
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = Ui.dp(this, 24);
        list.addView(clear, lp);
    }

    /** 설정 화면의 하위 버튼과 같은 모양. */
    private View actionRow(String label, View.OnClickListener l) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        int h = Ui.dp(this, 14);
        row.setPadding(h, Ui.dp(this, 12), h, Ui.dp(this, 12));
        row.setBackground(Ui.ripple(accentColor & 0x00FFFFFF | 0x33000000,
                Ui.round(accentColor & 0x00FFFFFF | 0x1F000000, Ui.dp(this, 12)), Ui.dp(this, 12)));
        row.addView(text(label, 15, accentColor), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        row.addView(new Chevron(this, accentColor));
        row.setClickable(true);
        row.setFocusable(true);
        row.setOnClickListener(l);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = Ui.dp(this, 6);
        row.setLayoutParams(lp);
        return row;
    }

    /** 단축어 사이의 구분선 (학습한 단어 화면과 같은 모양). */
    private View divider() {
        View v = new View(this);
        v.setBackgroundColor(hintColor & 0x00FFFFFF | 0x26000000);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1);
        lp.leftMargin = Ui.dp(this, 16);
        lp.rightMargin = Ui.dp(this, 16);
        v.setLayoutParams(lp);
        return v;
    }

    /** 단축어 한 줄: 줄임말과 문장, 오른쪽에 지우기(휴지통) 버튼. 줄을 누르면 고친다. */
    private View entryRow(String key, String phrase) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(Ui.dp(this, 16), Ui.dp(this, 8), Ui.dp(this, 6), Ui.dp(this, 8));
        row.setBackground(Ui.ripple(accentColor & 0x00FFFFFF | 0x33000000, null, 0));
        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        TextView k = text(key, 16, accentColor);
        k.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        texts.addView(k);
        TextView p = text(phrase, 14, textColor);
        p.setMaxLines(2);
        p.setEllipsize(android.text.TextUtils.TruncateAt.END);
        texts.addView(p);
        row.addView(texts, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        IconButton trash = new IconButton(this, Icons.TRASH, hintColor, hintColor & 0x00FFFFFF | 0x33000000,
                "'" + key + "' 단축어 지우기");
        trash.setOnClickListener(v -> AppTheme.accentBuilder(AppTheme.dialogContext(this))
                .setTitle("단축어 삭제")
                .setMessage("'" + key + "' 단축어를 삭제하시겠습니까?")
                .setPositiveButton("삭제", (d, w) -> {
                    prefs.removeShortcut(key);
                    render();
                })
                .setNegativeButton("취소", null)
                .show());
        row.addView(trash, new LinearLayout.LayoutParams(Ui.dp(this, 44), Ui.dp(this, 44)));
        row.setClickable(true);
        row.setFocusable(true);
        row.setOnClickListener(v -> edit(key));
        return row;
    }

    /** 추가(key가 null)하거나 고치는 창. 지우기는 목록의 휴지통 버튼으로 한다. */
    private void edit(String key) {
        boolean adding = key == null;
        EditText keyInput = new EditText(this);
        keyInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        keyInput.setSingleLine(true);
        keyInput.setHint("단축어 (예: ㅈㄱㅈ)");
        keyInput.setFilters(new InputFilter[]{new InputFilter.LengthFilter(Shortcuts.MAX_KEY)});
        EditText phraseInput = new EditText(this);
        phraseInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        phraseInput.setHint("문장 (예: 지금 가는 중)");
        phraseInput.setMaxLines(4);
        phraseInput.setFilters(new InputFilter[]{new InputFilter.LengthFilter(Shortcuts.MAX_PHRASE)});
        if (!adding) {
            keyInput.setText(key);
            phraseInput.setText(prefs.shortcuts().get(key));
        }
        int pad = Ui.dp(this, 20);
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(pad, Ui.dp(this, 8), pad, 0);
        box.addView(keyInput);
        box.addView(phraseInput);
        AlertDialog.Builder b = AppTheme.dialogBuilder(this)
                .setTitle(adding ? "단축어 추가" : "단축어 고치기")
                .setView(box)
                .setPositiveButton("저장", null)
                .setNegativeButton("취소", null);
        AlertDialog d = b.create();
        d.setOnShowListener(x -> d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String newKey = keyInput.getText().toString().trim();
            String phrase = phraseInput.getText().toString();
            String error = Shortcuts.validate(newKey, phrase);
            Map<String, String> all = prefs.shortcuts();
            String norm = Shortcuts.normalize(newKey);
            if (error == null && (adding || !norm.equals(key)) && all.containsKey(norm)) error = "이미 있는 단축어입니다";
            if (error == null && adding && all.size() >= Shortcuts.MAX_ENTRIES) {
                error = "단축어는 " + Shortcuts.MAX_ENTRIES + "개까지 정할 수 있습니다";
            }
            if (error != null) {
                Toast.makeText(this, error, Toast.LENGTH_SHORT).show();
                return;
            }
            if (!adding && !norm.equals(key)) prefs.removeShortcut(key);   // 줄임말을 바꿨으면 옛 줄임말은 지운다
            prefs.setShortcut(newKey, phrase);
            render();
            d.dismiss();
        }));
        d.show();
    }

    /** 뒤로 가기: 검색창이나 키보드 시험 입력창이 열려 있으면 그것부터 닫고, 그다음에 이전 화면으로 간다 (Android 12 이하). */
    @Override
    public void onBackPressed() {
        if (!SettingsFrame.consumeBack(this)) super.onBackPressed();
    }
}
