package com.alternative_studios.newswipe;

import android.app.Activity;
import android.content.Context;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.alternative_studios.newswipe.keyboard.Icons;
import com.alternative_studios.newswipe.ui.IconButton;
import com.alternative_studios.newswipe.ui.Ui;

/**
 * 설정 화면의 틀: 위에 고정되는 머리글(제목·부제와 '키보드 열기' 버튼)과 그 아래 입력 시험 창, 나머지는 스크롤 내용.
 * 설정을 바꾸는 동안 키보드를 계속 띄워 두고 결과를 볼 수 있다. 설정 화면과 편집 화면이 함께 쓴다.
 * 설정 첫 화면은 withSearch()로 '키보드 열기' 대신 동그란 검색 버튼과 검색창을 둔다.
 */
final class SettingsFrame {
    private final Activity activity;
    private final AppTheme colors;
    private final String title, subtitle;
    private LinearLayout testBar;
    private EditText testInput;
    private TextView testButton;
    /** 입력창이 열려 있어야 하는 상태. 애니메이션 중에도 버튼 글자를 바로 바꾸기 위해 따로 기억한다. */
    private boolean testBarWanted;
    /** 설정 검색 (첫 화면 전용): 검색어가 바뀔 때마다 부른다. null이면 검색 없이 '키보드 열기'를 쓴다. */
    private java.util.function.Consumer<String> onSearch;
    private LinearLayout searchBar;
    private EditText searchInput;
    private boolean searchWanted;
    /** Android 13+의 뒤로 가기 처리 (입력창이 열려 있는 동안만 등록한다). */
    private Object backCallback;
    /** 12 이하에서 Activity.onBackPressed가 이 틀을 찾는 데 쓴다. */
    private static final java.util.Map<Activity, SettingsFrame> FRAMES = new java.util.WeakHashMap<>();

    SettingsFrame(Activity activity, String title, String subtitle) {
        this.activity = activity;
        this.colors = AppTheme.of(activity);
        this.title = title;
        this.subtitle = subtitle;
        FRAMES.put(activity, this);
    }

    /**
     * 뒤로 가기: 검색창이나 키보드 시험 입력창이 열려 있으면 그것부터 닫는다. 닫았으면 true.
     * Android 12 이하에서는 화면(Activity)의 onBackPressed가 이것을 부르고, 13 이상은 입력창이 열려 있는 동안 등록한 콜백이 부른다.
     */
    static boolean consumeBack(Activity activity) {
        SettingsFrame frame = FRAMES.get(activity);
        return frame != null && frame.closeInputIfOpen();
    }

    private boolean closeInputIfOpen() {
        if (searchWanted) {
            toggleSearch();
            return true;
        }
        if (testBarWanted) {
            toggleTestKeyboard();
            return true;
        }
        return false;
    }

    /** 입력창이 열려 있는 동안에만 Android 13+의 뒤로 가기 콜백을 등록하고, 닫히면 풀어서 평소 뒤로 가기가 그대로 되게 한다. */
    private void updateBackCallback() {
        if (android.os.Build.VERSION.SDK_INT < 33) return;
        boolean needed = searchWanted || testBarWanted;
        android.window.OnBackInvokedDispatcher dispatcher = activity.getOnBackInvokedDispatcher();
        if (needed && backCallback == null) {
            android.window.OnBackInvokedCallback cb = this::closeInputIfOpen;
            backCallback = cb;
            dispatcher.registerOnBackInvokedCallback(android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT, cb);
        } else if (!needed && backCallback != null) {
            dispatcher.unregisterOnBackInvokedCallback((android.window.OnBackInvokedCallback) backCallback);
            backCallback = null;
        }
    }

    /** 키보드 열기 대신 검색 버튼과 검색창을 둔다. 검색창을 닫으면 빈 검색어로 알린다. */
    SettingsFrame withSearch(java.util.function.Consumer<String> onQuery) {
        this.onSearch = onQuery;
        return this;
    }

    /** 머리글·입력 시험 창과 content(스크롤 뷰)를 한 화면으로 묶어 돌려준다. setContentView에 넘기면 된다. */
    View wrap(View content) {
        LinearLayout root = new LinearLayout(activity);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(colors.bg);
        root.addView(buildHeader(), matchWrap());
        if (onSearch != null) {
            buildSearchBar();
            root.addView(searchBar, matchWrap());
        } else {
            buildTestBar();
            root.addView(testBar, matchWrap());
        }
        root.addView(content, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        Ui.padForSystemBars(root);
        return root;
    }

    private static LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private View buildHeader() {
        Context c = activity;
        LinearLayout header = new LinearLayout(c);
        header.setGravity(Gravity.CENTER_VERTICAL);
        int pad = Ui.dp(c, 16);
        header.setPadding(pad + Ui.dp(c, 4), Ui.dp(c, 12), pad, Ui.dp(c, 8));
        LinearLayout titles = new LinearLayout(c);
        titles.setOrientation(LinearLayout.VERTICAL);
        TextView titleView = new TextView(c);
        titleView.setText(title);
        titleView.setTextColor(colors.text);
        titleView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 24);
        titleView.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        titles.addView(titleView);
        TextView sub = new TextView(c);
        sub.setText(subtitle);
        sub.setTextColor(colors.hint);
        sub.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        titles.addView(sub);
        header.addView(titles, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        if (onSearch != null) {
            // 강조색 동그라미 안에 검색 아이콘
            int size = Ui.dp(c, 44);
            IconButton search = new IconButton(c, Icons.SEARCH, colors.onAccent, 0x33FFFFFF, "설정 검색");
            search.setBackground(Ui.ripple(0x33FFFFFF, ovalOf(colors.accent), size / 2f));
            search.setOnClickListener(v -> toggleSearch());
            header.addView(search, new LinearLayout.LayoutParams(size, size));
            return header;
        }
        testButton = new TextView(c);
        testButton.setTextColor(colors.onAccent);
        testButton.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        testButton.setGravity(Gravity.CENTER);
        testButton.setPadding(Ui.dp(c, 16), Ui.dp(c, 10), Ui.dp(c, 16), Ui.dp(c, 10));
        testButton.setBackground(Ui.ripple(0x33FFFFFF, Ui.round(colors.accent, Ui.dp(c, 20)), Ui.dp(c, 20)));
        testButton.setClickable(true);
        testButton.setOnClickListener(v -> toggleTestKeyboard());
        header.addView(testButton);
        return header;
    }

    private static android.graphics.drawable.GradientDrawable ovalOf(int color) {
        android.graphics.drawable.GradientDrawable g = new android.graphics.drawable.GradientDrawable();
        g.setShape(android.graphics.drawable.GradientDrawable.OVAL);
        g.setColor(color);
        return g;
    }

    /** 머리글 아래에 고정되는 검색창. */
    private void buildSearchBar() {
        Context c = activity;
        searchBar = new LinearLayout(c);
        searchBar.setVisibility(View.GONE);
        int pad = Ui.dp(c, 16);
        searchBar.setPadding(pad, 0, pad, Ui.dp(c, 8));
        searchInput = new EditText(c);
        searchInput.setHint("설정 검색");
        searchInput.setTextColor(colors.text);
        searchInput.setHintTextColor(colors.hint);
        searchInput.setSingleLine(true);
        searchInput.setInputType(InputType.TYPE_CLASS_TEXT);
        searchInput.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH);
        searchInput.setBackground(Ui.round(colors.card, Ui.dp(c, 12)));
        searchInput.setPadding(Ui.dp(c, 14), Ui.dp(c, 10), Ui.dp(c, 14), Ui.dp(c, 10));
        searchInput.addTextChangedListener(new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int n) { }
            @Override public void onTextChanged(CharSequence s, int a, int b, int n) { }
            @Override public void afterTextChanged(android.text.Editable e) {
                if (searchWanted) onSearch.accept(e.toString());
            }
        });
        searchInput.setOnEditorActionListener((v, action, ev) -> {
            hideKeyboard();   // 결과를 훑어볼 수 있게 키보드만 내린다
            return true;
        });
        searchBar.addView(searchInput, matchWrap());
    }

    private void hideKeyboard() {
        InputMethodManager imm = (InputMethodManager) activity.getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) imm.hideSoftInputFromWindow(searchInput.getWindowToken(), 0);
    }

    private void toggleSearch() {
        searchWanted = !searchWanted;
        if (!searchWanted) {
            hideKeyboard();
            searchInput.setText("");
            searchInput.clearFocus();
            Ui.setVisibleAnimated(searchBar, false);
            onSearch.accept("");
            updateBackCallback();
            return;
        }
        Ui.setVisibleAnimated(searchBar, true, this::focusSearch);
        searchBar.post(this::focusSearch);
        updateBackCallback();
    }

    private void focusSearch() {
        if (!searchWanted || activity.isFinishing() || activity.isDestroyed()) return;
        if (!searchInput.hasFocus()) searchInput.requestFocus();
        if (!searchInput.hasFocus()) return;
        InputMethodManager imm = (InputMethodManager) activity.getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) imm.showSoftInput(searchInput, InputMethodManager.SHOW_IMPLICIT);
    }

    /** 머리글 아래에 고정되는 입력창. */
    private void buildTestBar() {
        Context c = activity;
        testBar = new LinearLayout(c);
        testBar.setVisibility(View.GONE);
        int pad = Ui.dp(c, 16);
        testBar.setPadding(pad, 0, pad, Ui.dp(c, 8));
        testInput = new EditText(c);
        testInput.setHint("여기에 입력해 보세요");
        testInput.setTextColor(colors.text);
        testInput.setHintTextColor(colors.hint);
        testInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        testInput.setMaxLines(3);
        testInput.setBackground(Ui.round(colors.card, Ui.dp(c, 12)));
        testInput.setPadding(Ui.dp(c, 14), Ui.dp(c, 10), Ui.dp(c, 14), Ui.dp(c, 10));
        testBar.addView(testInput, matchWrap());
        updateTestButton();
    }

    private void updateTestButton() {
        testButton.setText(testBarWanted ? "키보드 닫기" : "키보드 열기");
    }

    private void toggleTestKeyboard() {
        testBarWanted = !testBarWanted;
        InputMethodManager imm = (InputMethodManager) activity.getSystemService(Context.INPUT_METHOD_SERVICE);
        if (!testBarWanted) {
            if (imm != null) imm.hideSoftInputFromWindow(testInput.getWindowToken(), 0);
            Ui.setVisibleAnimated(testBar, false);
            testInput.clearFocus();
        } else {
            // 입력창이 아직 펼쳐지는 중에는 포커스와 키보드 연결이 제대로 되지 않을 수 있어서,
            // 화면 배치 직후와 펼침이 끝난 뒤에 다시 한 번 확인한다.
            Ui.setVisibleAnimated(testBar, true, this::focusTestInput);
            testBar.post(this::focusTestInput);
        }
        updateTestButton();
        updateBackCallback();
    }

    /** 입력 시험 창에 포커스를 주고 키보드를 연결한다. 이미 포커스가 있으면 키보드만 띄운다. */
    private void focusTestInput() {
        if (!testBarWanted || activity.isFinishing() || activity.isDestroyed()) return;
        testInput.setCursorVisible(true);
        if (!testInput.hasFocus()) testInput.requestFocus();
        if (!testInput.hasFocus()) return;
        InputMethodManager imm = (InputMethodManager) activity.getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) imm.showSoftInput(testInput, InputMethodManager.SHOW_IMPLICIT);
    }
}
