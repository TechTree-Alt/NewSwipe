package com.alternative_studios.newswipe;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.Editable;
import android.text.InputType;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextWatcher;
import android.text.style.ForegroundColorSpan;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.PathInterpolator;
import android.view.inputmethod.InputMethodInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.alternative_studios.newswipe.keyboard.Icons;
import com.alternative_studios.newswipe.keyboard.KeyboardTheme;
import com.alternative_studios.newswipe.ui.ExpressiveButton;
import com.alternative_studios.newswipe.ui.OnboardingArt;
import com.alternative_studios.newswipe.ui.Spring;
import com.alternative_studios.newswipe.ui.Ui;

/**
 * 앱을 처음 열 때 한 번 보여 주는 첫 시작 가이드.
 * 1. 앱 이름·아이콘과 '시작하기' (또는 '설정 가져오기'로 바로 끝내기) → 2. 키보드 사용 설정·기본 키보드로 선택
 * → 3. 자음 키를 아래로 밀어 된소리 입력해 보기 → 4. 모음 키를 아래로 밀거나 두 번 탭해 ㅣ계 이중모음 입력해 보기 → 5. 축하.
 * 마치면 설정 화면으로 넘어가고, 다시는 뜨지 않는다.
 */
public final class OnboardingActivity extends Activity {

    private static final int PAGE_WELCOME = 0, PAGE_SETUP = 1, PAGE_SWIPE = 2, PAGE_VOWEL = 3, PAGE_FINISH = 4;
    /** 쪽 표시 점은 2~4번째 화면(따라 하는 단계)에만 보인다. */
    private static final int FIRST_STEP = PAGE_SETUP, STEP_COUNT = 3;
    private static final int REQ_IMPORT = 1;

    private Prefs prefs;
    private AppTheme colors;
    private KeyboardTheme keys;
    private FrameLayout pages;
    private View[] pageViews;
    private int page;
    private OnboardingArt.PageDots dots;

    // 1번째 화면
    private View welcomeHero, welcomeTitle, welcomeSub, welcomeButton, welcomeImport;
    private boolean welcomePlayed;

    // 2번째 화면
    private OnboardingArt.MiniKeyboard miniKeyboard;
    private View enableButton, selectButton, nextButton;
    private CheckMark enableCheck, selectCheck;
    private boolean nextShown;

    // 3·4번째 화면
    private Practice swipePractice, vowelPractice;

    // 5번째 화면
    private OnboardingArt.ShapeBadge finishBadge;
    private OnboardingArt.Confetti confetti;
    private View finishTitle, finishSub, finishButton;

    private Object backCallback;   // Android 13+의 OnBackInvokedCallback (첫 화면이 아닐 때만 등록)

    /** {사용 설정됨, 기본 키보드로 선택됨}. 설정 화면의 '시작하기'와 같이 쓴다. */
    static boolean[] keyboardState(Context c) {
        InputMethodManager imm = (InputMethodManager) c.getSystemService(INPUT_METHOD_SERVICE);
        String id = null;
        if (imm != null) {
            for (InputMethodInfo imi : imm.getEnabledInputMethodList()) {
                if (imi.getPackageName().equals(c.getPackageName())) id = imi.getId();
            }
        }
        String current = Settings.Secure.getString(c.getContentResolver(), Settings.Secure.DEFAULT_INPUT_METHOD);
        return new boolean[]{id != null, id != null && id.equals(current)};
    }

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(AppTheme.wrap(base));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = new Prefs(this);
        colors = AppTheme.of(this);
        keys = KeyboardTheme.of(this);
        // 가이드를 시작했다고 적어 둔다: 도중에 키보드를 고르고 닫아도 다음에 가이드를 다시 보여 준다 (Prefs.onboardingDone 참고).
        if (!prefs.raw().contains(Prefs.ONBOARDING_DONE)) {
            prefs.raw().edit().putBoolean(Prefs.ONBOARDING_DONE, false).apply();
        }

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(colors.bg);

        FrameLayout top = new FrameLayout(this);
        dots = new OnboardingArt.PageDots(this, STEP_COUNT, colors.accent, (colors.hint & 0x00FFFFFF) | 0x4D000000);
        top.addView(dots, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER));
        root.addView(top, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 40)));

        pages = new FrameLayout(this);
        pageViews = new View[]{buildWelcome(), buildSetup(), buildSwipe(), buildVowel(), buildFinish()};
        for (View v : pageViews) {
            v.setVisibility(View.GONE);
            pages.addView(v, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT));
        }
        root.addView(pages, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        Ui.padForSystemBars(root);
        setContentView(root);

        page = savedInstanceState == null ? PAGE_WELCOME : savedInstanceState.getInt("page", PAGE_WELCOME);
        pageViews[page].setVisibility(View.VISIBLE);
        boolean stepPage = isStep(page);
        dots.setAlpha(stepPage ? 1f : 0f);
        if (stepPage) dots.setPosition(page - FIRST_STEP, false);
        onPageShown(savedInstanceState == null);
    }

    @Override
    protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        out.putInt("page", page);
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateSetup(true);
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) updateSetup(true);   // 입력기 선택 창을 닫고 돌아왔을 때
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQ_IMPORT || resultCode != RESULT_OK || data == null || data.getData() == null) return;
        // 문제없이 가져왔으면 가이드를 건너뛰고 바로 설정 화면으로 간다 (다른 기기에서 이미 익힌 사람).
        SettingsImport.onPicked(this, data.getData(), this::finishOnboarding);
    }

    private static boolean isStep(int p) {
        return p >= FIRST_STEP && p < FIRST_STEP + STEP_COUNT;
    }

    // ---------------------------------------------------------------- 1. 환영

    private View buildWelcome() {
        LinearLayout root = column();
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setPadding(root.getPaddingLeft(), root.getPaddingTop(), root.getPaddingRight(), Ui.dp(this, 12));

        root.addView(new View(this), new LinearLayout.LayoutParams(1, 0, 1f));

        // 아이콘 뒤에 물결 모양(Material 3 Expressive 모양)이 옅은 강조색으로 천천히 돈다.
        FrameLayout hero = new FrameLayout(this);
        OnboardingArt.ShapeBadge halo = new OnboardingArt.ShapeBadge(this,
                (colors.accent & 0x00FFFFFF) | 0x26000000, 9, 0.07f);
        int haloSize = Ui.dp(this, 216);
        hero.addView(halo, new FrameLayout.LayoutParams(haloSize, haloSize, Gravity.CENTER));
        ImageView icon = new ImageView(this);
        icon.setImageDrawable(getDrawable(R.mipmap.ic_launcher));   // 적응형 아이콘: 기기의 아이콘 모양으로 잘려 그려진다
        icon.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        int size = Ui.dp(this, 136);
        hero.addView(icon, new FrameLayout.LayoutParams(size, size, Gravity.CENTER));
        root.addView(hero, new LinearLayout.LayoutParams(haloSize, haloSize));
        welcomeHero = hero;

        TextView title = text(getString(R.string.app_name), 44, colors.text, true);
        title.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams tlp = wrap();
        tlp.topMargin = Ui.dp(this, 12);
        root.addView(title, tlp);
        welcomeTitle = title;

        TextView sub = text("밀어서 입력하고 조작하는 한글 입력 시스템", 15, colors.hint, false);
        sub.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams slp = wrap();
        slp.topMargin = Ui.dp(this, 8);
        root.addView(sub, slp);
        welcomeSub = sub;

        root.addView(new View(this), new LinearLayout.LayoutParams(1, 0, 1f));

        ExpressiveButton start = new ExpressiveButton(this, "시작하기", colors.accent, colors.onAccent);
        start.setOnClickListener(v -> goTo(PAGE_SETUP));
        root.addView(start, bottomButton());
        welcomeButton = start;

        // 오른쪽 아래: 다른 기기에서 내보낸 설정을 바로 가져온다.
        View importButton = textButton("설정 가져오기", Icons.IMPORT, v -> SettingsImport.pick(this, REQ_IMPORT));
        LinearLayout.LayoutParams ilp = wrap();
        ilp.gravity = Gravity.END;
        ilp.topMargin = Ui.dp(this, 8);
        ilp.rightMargin = -Ui.dp(this, 12);   // 화면 가장자리 쪽으로 붙인다 (글자 버튼의 안쪽 여백만큼)
        root.addView(importButton, ilp);
        welcomeImport = importButton;
        return wrapScroll(root);
    }

    /** 처음 열 때 아이콘이 스프링으로 커지고, 글자와 버튼이 차례로 떠오른다. */
    private void animateWelcome() {
        if (!android.animation.ValueAnimator.areAnimatorsEnabled()) return;
        welcomeHero.setScaleX(0.5f);
        welcomeHero.setScaleY(0.5f);
        welcomeHero.setAlpha(0f);
        welcomeHero.setRotation(-30f);
        welcomeHero.animate().scaleX(1f).scaleY(1f).alpha(1f).rotation(0f).setDuration(800).setStartDelay(80)
                .setInterpolator(Spring.BOUNCY).start();
        int i = 0;
        for (View v : new View[]{welcomeTitle, welcomeSub, welcomeButton, welcomeImport}) {
            v.setAlpha(0f);
            v.setTranslationY(Ui.dp(this, 24));
            v.animate().alpha(1f).translationY(0f).setDuration(600).setStartDelay(260 + 90L * i++)
                    .setInterpolator(Spring.SOFT).start();
        }
    }

    // ---------------------------------------------------------------- 2. 키보드 설정

    private View buildSetup() {
        LinearLayout root = column();
        root.addView(pageTitle("키보드 설정"));
        root.addView(pageNote("NewSwipe로 입력하려면 아래 두 가지를 차례로 해 주세요."));

        miniKeyboard = new OnboardingArt.MiniKeyboard(this,
                new int[]{keys.background, keys.key, keys.functionKey, keys.text, colors.accent, colors.onAccent});
        LinearLayout.LayoutParams mlp = matchWrap();
        mlp.topMargin = Ui.dp(this, 28);
        root.addView(miniKeyboard, mlp);

        LinearLayout.LayoutParams first = matchWrap();
        first.topMargin = Ui.dp(this, 28);
        enableCheck = new CheckMark(this, colors.accent, colors.onAccent);
        enableButton = stepButton("1. 키보드 사용 설정", v ->
                startActivity(new Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)));
        root.addView(stepRow(enableButton, enableCheck), first);

        LinearLayout.LayoutParams second = matchWrap();
        second.topMargin = Ui.dp(this, 12);
        selectCheck = new CheckMark(this, colors.accent, colors.onAccent);
        selectButton = stepButton("2. NewSwipe를 기본 키보드로 선택", v -> {
            InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
            if (imm != null) imm.showInputMethodPicker();
        });
        root.addView(stepRow(selectButton, selectCheck), second);

        root.addView(new View(this), new LinearLayout.LayoutParams(1, 0, 1f));

        ExpressiveButton next = new ExpressiveButton(this, "다음으로", colors.accent, colors.onAccent);
        next.setOnClickListener(v -> goTo(PAGE_SWIPE));
        next.setVisibility(View.INVISIBLE);   // 두 가지를 다 마치면 나타난다 (자리는 미리 잡아 둔다)
        root.addView(next, bottomButton());
        nextButton = next;
        return wrapScroll(root);
    }

    /** 단계 버튼: 옅은 강조색 면에 강조색 글자 (Material 3 Expressive의 색조 버튼). */
    private View stepButton(String label, View.OnClickListener l) {
        int tonal = (colors.accent & 0x00FFFFFF) | 0x1F000000;
        ExpressiveButton b = new ExpressiveButton(this, label, tonal, colors.accent);
        b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        b.setMinWidth(0);
        b.setPadding(Ui.dp(this, 16), Ui.dp(this, 16), Ui.dp(this, 16), Ui.dp(this, 16));
        // 체크 알약이 나오며 폭이 줄어도 한 줄을 지키도록 글자가 조금 작아질 수 있다 (줄이 바뀌어 높이가 출렁이지 않게).
        b.setMaxLines(1);
        b.setAutoSizeTextTypeUniformWithConfiguration(12, 16, 1, TypedValue.COMPLEX_UNIT_SP);
        b.setOnClickListener(l);
        return b;
    }

    /** 처음에는 버튼이 줄을 꽉 채우고, 단계를 마치면 오른쪽에서 체크 알약이 자라나며 버튼이 그만큼 줄어든다. */
    private View stepRow(View button, View check) {
        LinearLayout row = new LinearLayout(this);
        row.addView(button, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        row.addView(check, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT));
        return row;
    }

    /** 키보드 상태를 읽어 체크 표시·버튼 흐림·'다음으로'를 맞춘다. */
    private void updateSetup(boolean animate) {
        if (enableButton == null) return;
        boolean[] state = keyboardState(this);
        boolean enabled = state[0], selected = state[1];
        if (selected && !prefs.setupDone()) prefs.raw().edit().putBoolean(Prefs.SETUP_DONE, true).apply();
        boolean anim = animate && page == PAGE_SETUP;
        setStepDone(enableButton, enableCheck, enabled, anim);
        setStepDone(selectButton, selectCheck, selected, anim);
        boolean both = enabled && selected;
        if (both != nextShown) {
            nextShown = both;
            if (both) popIn(nextButton, anim, 150);
            else {
                nextButton.animate().cancel();
                nextButton.setVisibility(View.INVISIBLE);
            }
        }
    }

    private static void setStepDone(View button, CheckMark check, boolean done, boolean animate) {
        button.setEnabled(!done);
        float alpha = done ? 0.38f : 1f;
        if (animate) button.animate().alpha(alpha).setDuration(120).setStartDelay(0).start();
        else button.setAlpha(alpha);
        check.setShown(done, animate);
    }

    // ---------------------------------------------------------------- 3·4. 입력해 보기

    private View buildSwipe() {
        String[] row = {"ㅂ", "ㅈ", "ㄷ", "ㄱ", "ㅅ"};
        swipePractice = new Practice("밀어서 된소리 입력",
                "자음 키를 아래로 밀어 된소리를 입력하세요",
                "빨간색 꼬까옷",
                "ㅂ을 아래로 밀면 ㅃ, ㄱ을 아래로 밀면 ㄲ",
                PAGE_VOWEL,
                new OnboardingArt.Step(row, 0, "ㅃ", false),
                new OnboardingArt.Step(row, 3, "ㄲ", false));
        return swipePractice.view;
    }

    private View buildVowel() {
        vowelPractice = new Practice("ㅣ계 이중모음 입력",
                "모음 키를 아래로 밀거나 두 번 연속 탭해서 ㅣ계 이중모음을 입력하세요",
                "여유 있는 요즘",
                "ㅓ를 아래로 밀거나 두 번 탭하면 ㅕ, ㅜ는 ㅠ, ㅗ는 ㅛ",
                PAGE_FINISH,
                new OnboardingArt.Step(new String[]{"ㅎ", "ㅓ", "ㅏ", "ㅣ"}, 1, "ㅕ", false),
                new OnboardingArt.Step(new String[]{"ㅊ", "ㅍ", "ㅜ", "ㅡ"}, 2, "ㅠ", true),
                new OnboardingArt.Step(new String[]{"ㅅ", "ㅗ", "ㅐ", "ㅔ"}, 1, "ㅛ", false));
        return vowelPractice.view;
    }

    /** 따라 입력해 보는 화면: 시연 그림, 따라 칠 글, 입력창. 다 맞게 치면 '다음으로'가 나타난다. */
    private final class Practice {
        final View view;
        final String target;
        final OnboardingArt.KeyDemo demo;
        final EditText input;
        final TextView targetView;
        final View done, next, skip;
        boolean ok;

        Practice(String title, String note, String target, String tip, int nextPage, OnboardingArt.Step... steps) {
            this.target = target;
            LinearLayout root = column();
            root.addView(pageTitle(title));
            root.addView(pageNote(note));

            demo = new OnboardingArt.KeyDemo(OnboardingActivity.this, new int[]{keys.background, keys.key,
                    keys.keyPressed, keys.text, colors.hint, colors.accent, colors.onAccent}, steps);
            LinearLayout.LayoutParams dlp = matchWrap();
            dlp.topMargin = Ui.dp(OnboardingActivity.this, 12);
            root.addView(demo, dlp);

            Context c = OnboardingActivity.this;
            LinearLayout card = new LinearLayout(c);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setGravity(Gravity.CENTER_HORIZONTAL);
            card.setBackground(Ui.round(colors.card, Ui.dp(c, 28)));
            int p = Ui.dp(c, 20);
            card.setPadding(p, Ui.dp(c, 20), p, p);

            card.addView(text("따라 입력해 보세요", 13, colors.hint, false), wrap());
            targetView = text(target, 30, colors.text, true);
            targetView.setGravity(Gravity.CENTER);
            LinearLayout.LayoutParams plp = wrap();
            plp.topMargin = Ui.dp(c, 4);
            card.addView(targetView, plp);
            TextView tipView = text(tip, 13, colors.hint, false);
            tipView.setGravity(Gravity.CENTER);
            LinearLayout.LayoutParams tlp = wrap();
            tlp.topMargin = Ui.dp(c, 4);
            card.addView(tipView, tlp);

            input = new EditText(c);
            input.setHint("여기에 입력해 보세요");
            input.setTextColor(colors.text);
            input.setHintTextColor(colors.hint);
            input.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
            input.setGravity(Gravity.CENTER);
            input.setSingleLine(true);
            input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
            input.setBackground(Ui.round(colors.bg, Ui.dp(c, 16)));
            input.setPadding(Ui.dp(c, 16), Ui.dp(c, 14), Ui.dp(c, 16), Ui.dp(c, 14));
            tintCursor(input);
            input.addTextChangedListener(new TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
                @Override public void onTextChanged(CharSequence s, int start, int before, int count) { }
                @Override public void afterTextChanged(Editable s) {
                    update(s.toString());
                }
            });
            LinearLayout.LayoutParams ilp = matchWrap();
            ilp.topMargin = Ui.dp(c, 16);
            card.addView(input, ilp);

            TextView doneView = text("잘하셨어요!", 15, colors.accent, true);
            doneView.setGravity(Gravity.CENTER);
            doneView.setVisibility(View.GONE);
            LinearLayout.LayoutParams olp = matchWrap();
            olp.topMargin = Ui.dp(c, 12);
            card.addView(doneView, olp);
            done = doneView;

            LinearLayout.LayoutParams clp = matchWrap();
            clp.topMargin = Ui.dp(c, 12);
            root.addView(card, clp);
            root.addView(new View(c), new LinearLayout.LayoutParams(1, 0, 1f));

            // 아래쪽: 다 입력하면 '다음으로'가, 그전에는 '건너뛰기'가 보인다.
            FrameLayout bottom = new FrameLayout(c);
            TextView skipView = text("건너뛰기", 15, colors.hint, false);
            skipView.setGravity(Gravity.CENTER);
            int sp = Ui.dp(c, 16);
            skipView.setPadding(sp * 2, sp, sp * 2, sp);
            skipView.setBackground(Ui.ripple((colors.hint & 0x00FFFFFF) | 0x33000000, null, Ui.dp(c, 24)));
            skipView.setClickable(true);
            skipView.setFocusable(true);
            skipView.setOnClickListener(v -> goTo(nextPage));
            bottom.addView(skipView, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER));
            skip = skipView;
            ExpressiveButton nextView = new ExpressiveButton(c, "다음으로", colors.accent, colors.onAccent);
            nextView.setOnClickListener(v -> goTo(nextPage));
            nextView.setVisibility(View.INVISIBLE);
            bottom.addView(nextView, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER));
            next = nextView;
            root.addView(bottom, bottomButton());
            view = wrapScroll(root);
        }

        /** 맞게 입력한 앞부분을 강조색으로 칠하고, 다 맞으면 칭찬과 '다음으로' 버튼을 띄운다. */
        void update(String typed) {
            String t = typed.trim();
            int n = 0;
            while (n < Math.min(t.length(), target.length()) && t.charAt(n) == target.charAt(n)) n++;
            SpannableString s = new SpannableString(target);
            if (n > 0) s.setSpan(new ForegroundColorSpan(colors.accent), 0, n, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            targetView.setText(s);

            boolean match = t.equals(target);
            if (match == ok) return;
            ok = match;
            Ui.setVisibleAnimated(done, match);
            if (match) {
                next.performHapticFeedback(Build.VERSION.SDK_INT >= 30
                        ? HapticFeedbackConstants.CONFIRM : HapticFeedbackConstants.KEYBOARD_TAP);
                skip.setVisibility(View.INVISIBLE);
                popIn(next, true, 0);
                // 키보드가 올라와 있으면 '다음으로'가 가려질 수 있으니, 칭찬 글이 펼쳐진 뒤 맨 아래까지 올린다.
                ScrollView scroll = (ScrollView) view;
                scroll.postDelayed(() -> scroll.smoothScrollTo(0, scroll.getChildAt(0).getHeight()), 250);
                // 따라 칠 글이 한 번 통통 튄다.
                if (android.animation.ValueAnimator.areAnimatorsEnabled()) {
                    targetView.setScaleX(1.12f);
                    targetView.setScaleY(1.12f);
                    targetView.animate().scaleX(1f).scaleY(1f).setDuration(500).setStartDelay(0)
                            .setInterpolator(Spring.BOUNCY).start();
                }
            } else {
                next.animate().cancel();
                next.setVisibility(View.INVISIBLE);
                skip.setVisibility(View.VISIBLE);
            }
        }

        void focus() {
            if (isFinishing() || isDestroyed() || pageViews[page] != view) return;
            input.requestFocus();
            InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
            if (imm != null) imm.showSoftInput(input, InputMethodManager.SHOW_IMPLICIT);
        }
    }

    private void tintCursor(EditText e) {
        e.setHighlightColor((colors.accent & 0x00FFFFFF) | 0x55000000);
        if (Build.VERSION.SDK_INT >= 29) {
            android.graphics.drawable.Drawable cursor = e.getTextCursorDrawable();
            if (cursor != null) {
                cursor = cursor.mutate();
                cursor.setTint(colors.accent);
                e.setTextCursorDrawable(cursor);
            }
        }
    }

    // ---------------------------------------------------------------- 5. 축하

    private View buildFinish() {
        FrameLayout frame = new FrameLayout(this);
        LinearLayout root = column();
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.addView(new View(this), new LinearLayout.LayoutParams(1, 0, 1f));

        finishBadge = new OnboardingArt.ShapeBadge(this, colors.accent, 12, 0.06f);
        finishBadge.setCheck(colors.onAccent);
        int size = Ui.dp(this, 168);
        root.addView(finishBadge, new LinearLayout.LayoutParams(size, size));

        TextView title = text("축하합니다!\nNewSwipe의 기본기를\n모두 익히셨습니다.", 30, colors.text, true);
        title.setGravity(Gravity.CENTER);
        title.setLineSpacing(Ui.dp(this, 4), 1f);
        LinearLayout.LayoutParams tlp = wrap();
        tlp.topMargin = Ui.dp(this, 36);
        root.addView(title, tlp);
        finishTitle = title;

        TextView sub = text("설정에서 입맛에 맞게 모습과 동작을 바꿀 수 있습니다.", 15, colors.hint, false);
        sub.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams slp = wrap();
        slp.topMargin = Ui.dp(this, 12);
        root.addView(sub, slp);
        finishSub = sub;

        root.addView(new View(this), new LinearLayout.LayoutParams(1, 0, 1f));

        ExpressiveButton settings = new ExpressiveButton(this, "설정하기", colors.accent, colors.onAccent);
        settings.setOnClickListener(v -> finishOnboarding());
        root.addView(settings, bottomButton());
        finishButton = settings;

        frame.addView(wrapScroll(root), new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        confetti = new OnboardingArt.Confetti(this, colors.accent);
        frame.addView(confetti, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        return frame;
    }

    /** 배지가 돌며 튀어나오고, 체크가 그려지고, 색종이가 터진다. 글자와 버튼은 차례로 떠오른다. */
    private void celebrate() {
        if (!android.animation.ValueAnimator.areAnimatorsEnabled()) {
            finishBadge.setCheckProgress(1f);
            return;
        }
        finishBadge.animate().cancel();
        finishBadge.setScaleX(0f);
        finishBadge.setScaleY(0f);
        finishBadge.setRotation(-120f);
        finishBadge.setCheckProgress(0f);
        finishBadge.animate().scaleX(1f).scaleY(1f).rotation(0f).setDuration(900).setStartDelay(150)
                .setInterpolator(Spring.BOUNCY).start();
        android.animation.ValueAnimator check = android.animation.ValueAnimator.ofFloat(0f, 1f);
        check.setDuration(420);
        check.setStartDelay(520);
        check.setInterpolator(new PathInterpolator(0.2f, 0f, 0f, 1f));
        check.addUpdateListener(a -> finishBadge.setCheckProgress((float) a.getAnimatedValue()));
        check.start();
        int i = 0;
        for (View v : new View[]{finishTitle, finishSub, finishButton}) {
            v.animate().cancel();
            v.setAlpha(0f);
            v.setTranslationY(Ui.dp(this, 24));
            v.animate().alpha(1f).translationY(0f).setDuration(600).setStartDelay(420 + 100L * i++)
                    .setInterpolator(Spring.SOFT).start();
        }
        confetti.postDelayed(() -> {
            if (page != PAGE_FINISH) return;
            int[] at = new int[2], base = new int[2];
            finishBadge.getLocationInWindow(at);
            confetti.getLocationInWindow(base);
            confetti.burst(at[0] - base[0] + finishBadge.getWidth() / 2f,
                    at[1] - base[1] + finishBadge.getHeight() / 2f);
            confetti.performHapticFeedback(Build.VERSION.SDK_INT >= 30
                    ? HapticFeedbackConstants.CONFIRM : HapticFeedbackConstants.KEYBOARD_TAP);
        }, 600);
    }

    @SuppressWarnings("deprecation")   // overridePendingTransition: 설정 화면으로 넘어가는 전환 효과 (Android 14 이상에서도 동작한다)
    private void finishOnboarding() {
        hideKeyboard();
        prefs.raw().edit().putBoolean(Prefs.ONBOARDING_DONE, true).apply();
        startActivity(new Intent(this, SettingsActivity.class));
        finish();
        overridePendingTransition(R.anim.onboarding_enter, R.anim.onboarding_exit);
    }

    private void hideKeyboard() {
        InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        View focus = getCurrentFocus();
        if (imm != null && focus != null) imm.hideSoftInputFromWindow(focus.getWindowToken(), 0);
    }

    // ---------------------------------------------------------------- 화면 넘기기

    /**
     * Material 3 Expressive의 '가로 축 공유' 전환: 나가는 화면은 진행 방향 반대로 조금 밀리며 빠르게 사라지고,
     * 들어오는 화면은 진행 방향에서 공간 스프링으로 미끄러져 들어오며 나타난다. 뒤로 가면 방향이 반대다.
     */
    private void goTo(int to) {
        if (to == page) return;
        View out = pageViews[page], in = pageViews[to];
        float dir = to > page ? 1f : -1f;
        float shift = Ui.dp(this, 48) * dir;
        boolean keepKeyboard = practice(to) != null && practice(page) != null;
        if (!keepKeyboard) hideKeyboard();
        page = to;

        boolean motion = android.animation.ValueAnimator.areAnimatorsEnabled();
        out.animate().cancel();
        if (motion) {
            out.animate().translationX(-shift).alpha(0f).setDuration(100).setStartDelay(0)
                    .setInterpolator(new PathInterpolator(0.3f, 0f, 1f, 1f))
                    .withEndAction(() -> {
                        out.setVisibility(View.GONE);
                        out.setTranslationX(0f);
                        out.setAlpha(1f);
                    }).start();
        } else {
            out.setVisibility(View.GONE);
        }
        in.animate().cancel();
        in.setVisibility(View.VISIBLE);
        if (motion) {
            in.setTranslationX(shift);
            in.setAlpha(0f);
            in.animate().translationX(0f).alpha(1f).setDuration(320).setStartDelay(30)
                    .setInterpolator(Spring.SOFT).start();
        } else {
            in.setTranslationX(0f);
            in.setAlpha(1f);
        }

        boolean step = isStep(to);
        dots.animate().cancel();
        dots.animate().alpha(step ? 1f : 0f).setDuration(150).setStartDelay(0).start();
        if (step) dots.setPosition(to - FIRST_STEP, dots.getAlpha() > 0f && motion);
        onPageShown(true);
    }

    private Practice practice(int p) {
        return p == PAGE_SWIPE ? swipePractice : p == PAGE_VOWEL ? vowelPractice : null;
    }

    private void onPageShown(boolean first) {
        updateSetup(false);
        switch (page) {
            case PAGE_WELCOME:
                if (first && !welcomePlayed) animateWelcome();
                welcomePlayed = true;
                break;
            case PAGE_SETUP:
                if (first) miniKeyboard.playIntro();
                break;
            case PAGE_SWIPE:
            case PAGE_VOWEL:
                Practice p = practice(page);
                p.demo.restart();
                pages.postDelayed(p::focus, first ? 200 : 0);
                break;
            case PAGE_FINISH:
                if (first) celebrate();
                else finishBadge.setCheckProgress(1f);
                break;
            default:
                break;
        }
        updateBackCallback();
    }

    private void updateBackCallback() {
        if (Build.VERSION.SDK_INT < 33) return;
        android.window.OnBackInvokedDispatcher d = getOnBackInvokedDispatcher();
        if (page > PAGE_WELCOME && backCallback == null) {
            android.window.OnBackInvokedCallback cb = () -> goTo(page - 1);
            d.registerOnBackInvokedCallback(android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT, cb);
            backCallback = cb;
        } else if (page == PAGE_WELCOME && backCallback != null) {
            d.unregisterOnBackInvokedCallback((android.window.OnBackInvokedCallback) backCallback);
            backCallback = null;
        }
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onBackPressed() {   // Android 12 이하
        if (page > PAGE_WELCOME) goTo(page - 1);
        else super.onBackPressed();
    }

    // ---------------------------------------------------------------- 화면 조각

    /** 나타날 때 스프링으로 커지며 보인다. */
    private static void popIn(View v, boolean animate, long delay) {
        v.animate().cancel();
        v.setVisibility(View.VISIBLE);
        if (!animate || !android.animation.ValueAnimator.areAnimatorsEnabled()) {
            v.setAlpha(1f);
            v.setScaleX(1f);
            v.setScaleY(1f);
            return;
        }
        v.setAlpha(0f);
        v.setScaleX(0.7f);
        v.setScaleY(0.7f);
        v.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(600).setStartDelay(delay)
                .setInterpolator(Spring.BOUNCY).start();
    }

    private LinearLayout column() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = Ui.dp(this, 24);
        root.setPadding(pad, Ui.dp(this, 16), pad, Ui.dp(this, 32));
        return root;
    }

    /** 작은 화면이나 키보드가 올라왔을 때도 다 볼 수 있게 스크롤로 감싼다. 내용이 짧으면 화면을 꽉 채운다 (빈 칸이 늘어난다). */
    private ScrollView wrapScroll(View content) {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setOverScrollMode(View.OVER_SCROLL_IF_CONTENT_SCROLLS);
        scroll.addView(content, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        return scroll;
    }

    /** 아이콘이 붙은 글자 버튼 (Material 3 텍스트 버튼). */
    private View textButton(String label, int icon, View.OnClickListener l) {
        LinearLayout b = new LinearLayout(this);
        b.setGravity(Gravity.CENTER_VERTICAL);
        int h = Ui.dp(this, 16);
        b.setPadding(Ui.dp(this, 12), Ui.dp(this, 10), h, Ui.dp(this, 10));
        b.setMinimumHeight(Ui.dp(this, 48));
        b.setBackground(Ui.ripple((colors.accent & 0x00FFFFFF) | 0x33000000, null, Ui.dp(this, 24)));
        View iconView = new View(this) {
            private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);

            @Override
            protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                int s = Ui.dp(getContext(), 20);
                setMeasuredDimension(s, s);
            }

            @Override
            protected void onDraw(Canvas c) {
                p.setColor(colors.accent);
                Icons.draw(c, icon, getWidth() / 2f, getHeight() / 2f, Ui.dp(getContext(), 18), p);
            }
        };
        iconView.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        b.addView(iconView);
        TextView t = text(label, 15, colors.accent, true);
        LinearLayout.LayoutParams tlp = wrap();
        tlp.leftMargin = Ui.dp(this, 8);
        b.addView(t, tlp);
        b.setClickable(true);
        b.setFocusable(true);
        b.setContentDescription(label);
        b.setOnClickListener(l);
        return b;
    }

    private TextView pageTitle(String s) {
        TextView t = text(s, 30, colors.text, true);
        t.setLayoutParams(matchWrap());
        return t;
    }

    private TextView pageNote(String s) {
        TextView t = text(s, 16, colors.hint, false);
        t.setLineSpacing(Ui.dp(this, 3), 1f);
        LinearLayout.LayoutParams lp = matchWrap();
        lp.topMargin = Ui.dp(this, 10);
        t.setLayoutParams(lp);
        return t;
    }

    private TextView text(String s, float sp, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextColor(color);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        if (bold) t.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        return t;
    }

    /** 화면 아래 가운데의 버튼 자리. */
    private LinearLayout.LayoutParams bottomButton() {
        LinearLayout.LayoutParams lp = wrap();
        lp.gravity = Gravity.CENTER_HORIZONTAL;
        lp.topMargin = Ui.dp(this, 16);
        return lp;
    }

    private static LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private static LinearLayout.LayoutParams wrap() {
        return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    /**
     * 단계를 마치면 버튼 오른쪽에 나타나는 체크 알약 (버튼과 같은 높이의 세로로 긴 알약, 강조색 면에 체크).
     * 나타날 때 폭이 0에서 스프링으로 자라나며 옆 버튼을 밀어 줄이고, 체크는 조금 늦게 커진다.
     */
    private static final class CheckMark extends View {
        private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path path = new Path();
        private final android.graphics.RectF rect = new android.graphics.RectF();
        private final int pillWidth, gap;
        private boolean shown;
        private float progress;   // 0 = 숨김, 1 = 다 나옴 (스프링으로 1을 조금 넘을 수 있다)
        private android.animation.ValueAnimator anim;

        CheckMark(Context c, int accent, int onAccent) {
            super(c);
            fill.setColor(accent);
            stroke.setColor(onAccent);
            stroke.setStyle(Paint.Style.STROKE);
            stroke.setStrokeCap(Paint.Cap.ROUND);
            stroke.setStrokeJoin(Paint.Join.ROUND);
            stroke.setStrokeWidth(Ui.dp(c, 3));
            pillWidth = Ui.dp(c, 56);
            gap = Ui.dp(c, 8);
            setContentDescription("완료");
            setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        }

        void setShown(boolean show, boolean animate) {
            if (show == shown) return;
            shown = show;
            setImportantForAccessibility(show ? IMPORTANT_FOR_ACCESSIBILITY_YES : IMPORTANT_FOR_ACCESSIBILITY_NO);
            if (anim != null) anim.cancel();
            float to = show ? 1f : 0f;
            if (!animate || !android.animation.ValueAnimator.areAnimatorsEnabled()) {
                setProgress(to);
                return;
            }
            anim = android.animation.ValueAnimator.ofFloat(progress, to);
            anim.setDuration(show ? 380 : 150);
            anim.setInterpolator(show ? Spring.BOUNCY : new PathInterpolator(0.3f, 0f, 0.8f, 0.15f));
            anim.addUpdateListener(a -> setProgress((float) a.getAnimatedValue()));
            anim.start();
            if (show) performHapticFeedback(Build.VERSION.SDK_INT >= 30
                    ? HapticFeedbackConstants.CONFIRM : HapticFeedbackConstants.KEYBOARD_TAP);
        }

        /** 알약 폭과 버튼과의 간격을 progress에 맞춘다. 줄의 버튼은 남은 폭을 채우므로 같이 줄었다 늘어난다. */
        private void setProgress(float p) {
            progress = p;
            LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) getLayoutParams();
            float k = Math.max(0f, p);
            if (lp != null) {
                lp.width = Math.round(pillWidth * k);
                lp.leftMargin = Math.round(gap * Math.min(1f, k));
                setLayoutParams(lp);
            }
            invalidate();
        }

        @Override
        protected void onDetachedFromWindow() {
            if (anim != null) anim.cancel();
            super.onDetachedFromWindow();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            float w = getWidth(), h = getHeight();
            if (w <= 0f) return;
            rect.set(0, 0, w, h);
            float r = Math.min(w, h) / 2f;
            canvas.drawRoundRect(rect, r, r, fill);
            // 체크는 알약이 반쯤 자란 뒤에 가운데에서 커진다.
            float c = Math.max(0f, Math.min(1.1f, (progress - 0.45f) / 0.55f));
            if (c <= 0f) return;
            float s = Ui.dp(getContext(), 26) * c;
            canvas.save();
            canvas.translate(w / 2f, h / 2f);
            path.rewind();
            path.moveTo(-s * 0.42f, s * 0.02f);
            path.lineTo(-s * 0.13f, s * 0.3f);
            path.lineTo(s * 0.44f, -s * 0.28f);
            canvas.drawPath(path, stroke);
            canvas.restore();
        }
    }
}
