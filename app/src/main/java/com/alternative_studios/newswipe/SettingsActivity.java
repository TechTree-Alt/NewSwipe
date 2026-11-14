package com.alternative_studios.newswipe;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.graphics.Color;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.alternative_studios.newswipe.clipboard.ClipboardHistory;
import com.alternative_studios.newswipe.keyboard.Icons;
import com.alternative_studios.newswipe.keyboard.KeyboardSwipes;
import com.alternative_studios.newswipe.suggest.CorrectionMemory;
import com.alternative_studios.newswipe.suggest.DictionaryPack;
import com.alternative_studios.newswipe.suggest.UsageHistory;
import com.alternative_studios.newswipe.suggest.UserDictionary;
import com.alternative_studios.newswipe.ui.Chevron;
import com.alternative_studios.newswipe.ui.ExpressiveSwitch;
import com.alternative_studios.newswipe.ui.IconView;
import com.alternative_studios.newswipe.ui.IconButton;
import com.alternative_studios.newswipe.ui.Ui;

import java.io.File;

/** 설정 화면. 런처에서 앱을 열면 이 화면이 뜬다. */
public final class SettingsActivity extends Activity {

    /** 하위 메뉴를 열 때 넘기는 값. 없으면 첫 화면이다. */
    static final String EXTRA_SECTION = "section";
    /** 검색 결과로 열 때 스크롤해서 깜빡일 항목의 이름 (화면에 보이는 글자). */
    static final String EXTRA_HIGHLIGHT = "highlight";
    /** 키보드 사용자화에서 열 영역·키 (검색 결과에서 들어올 때): EDITOR_PART로 시작하는 검색 화면 id. */
    static final String EXTRA_TARGET = "target";
    /**
     * 검색 목록에서 키보드 사용자화의 영역 창·키 창을 가리키는 화면 id: "keyboard:a:<영역>:<이름>" 또는 "keyboard:k:<키 종류>:<이름>".
     * 영역·키 창의 설정은 목록에 없으므로 검색 목록을 모을 때 그 창들도 따로 만들어 모은다 (harvestEditorParts).
     */
    private static final String EDITOR_PART = "keyboard:";
    /** 검색 목록을 모을 때 영역·키 창들을 모으라는 표시 (HARVEST_IDS). */
    private static final String EDITOR_PARTS = "keyboard:parts";
    /**
     * 검색 목록에서 가로 모드·폴더블 내부 화면 키보드 사용자화의 설정 창(키보드 전체·영역)을 가리키는 화면 id:
     * "profilepart:<화면 id>:<영역 (-1 = 키보드 전체)>:<이름>". 그 화면의 미리보기에서 그 창을 열어 항목을 찾는다.
     */
    private static final String PROFILE_PART = "profilepart:";

    private Prefs prefs;
    private LinearLayout list;
    /** 스크롤 목록 위에 고정되어 스크롤해도 늘 보이는 곳 (키보드 미리보기). 담은 것이 없으면 숨긴다. */
    private LinearLayout pinned;
    /** 스크롤 목록 아래(화면 맨 아래)에 고정되는 곳: 키보드 사용자화의 미리보기가 실제 키보드 자리에 놓인다. */
    private LinearLayout pinnedBottom;
    private ScrollView scrollView;
    /** 스크롤 목록을 감싼 틀. 키 편집 창(시트)이 그 위에 겹친다. */
    private android.widget.FrameLayout middle;
    private TextView status;
    private View setupCard, setupHeading, appCard;
    /** 첫 화면의 설정 메뉴 (제목과 카드). 시작하기를 처음 마치기 전에는 흐리게 하고 막는다. */
    private View menuHeading, menuCard;
    private int textColor, hintColor, accentColor, cardColor, onAccentColor;
    /** 클립보드 '이미지 저장' 스위치 (끄기를 취소하면 되돌린다). */
    private ExpressiveSwitch clipImagesSwitch;
    private String themeSignature;
    /** 설정 화면의 공통 조각 (키 창과 같은 모양). */
    private SettingsWidgets widgets;
    /**
     * 설정을 가져올 때마다 늘어난다. 뒤에 쌓여 있던 다른 설정 화면은 돌아왔을 때 이 값이 바뀌었으면
     * 가져온 값으로 다시 그린다 (옛 값이 보이거나, 옛 화면에서 만진 값이 가져온 설정을 덮지 않게).
     */
    private static int importGeneration;
    private int builtGeneration;

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(AppTheme.wrap(base));   // 고른 화면 모드로 리소스(night)를 맞춘다
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = new Prefs(this);
        // 설정 화면은 세로로 고정이지만(매니페스트), 가로 모드·폴더블 내부 화면 키보드 사용자화는 화면을 돌려 가며 미리보기를 볼 수 있다.
        String openId = getIntent().getStringExtra(EXTRA_SECTION);
        if (LANDSCAPE_AREA.equals(openId) || LARGE_AREA.equals(openId)) {
            setRequestedOrientation(android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
        }
        // 앱을 처음 열면 첫 시작 가이드부터 보여 준다 (가이드를 마치면 이 화면을 다시 연다).
        if (getIntent().getStringExtra(EXTRA_SECTION) == null && !prefs.onboardingDone()) {
            startActivity(new Intent(this, OnboardingActivity.class));
            finish();
            return;
        }
        AppTheme colors = AppTheme.of(this);
        themeSignature = AppTheme.signature(this);
        builtGeneration = importGeneration;
        int bg = colors.bg;
        cardColor = colors.card;
        textColor = colors.text;
        hintColor = colors.hint;
        accentColor = colors.accent;
        onAccentColor = colors.onAccent;
        widgets = new SettingsWidgets(this, colors, this::markItem);

        // 위쪽 머리글(고정) + 아래 스크롤 목록
        String sectionId = getIntent().getStringExtra(EXTRA_SECTION);
        String sectionName = sectionId == null ? null : sectionTitle(sectionId);
        // 폴더블 내부 화면 키보드 사용자화는 지금 화면 방향의 설정을 보여 주므로 제목에도 방향을 붙인다.
        if (LARGE_AREA.equals(sectionId) && largeProfileNow() != null) {
            sectionName = Prefs.PROFILE_LARGE_LANDSCAPE.equals(largeProfileNow()) ? L.t("폴더블 내부 화면 가로 키보드 사용자화", "Customize inner screen landscape keyboard") : L.t("폴더블 내부 화면 세로 키보드 사용자화", "Customize inner screen portrait keyboard");
        }
        SettingsFrame frame = sectionName == null
                ? new SettingsFrame(this, getString(R.string.app_name), L.t("밀어서 입력하고 조작하는 한국어 입력 시스템", "Swipe to type and control, in Korean and English"))
                        .withSearch(this::onSearchQuery)
                : LEARNED.equals(sectionId) || BLOCKED.equals(sectionId)
                ? new SettingsFrame(this, sectionName, getString(R.string.app_name)).withSearch(this::onWordSearch, L.t("단어 검색", "Search words"))
                : new SettingsFrame(this, sectionName, getString(R.string.app_name));
        if (KEYBOARD.equals(sectionId)) frame.withAction(Icons.UNDO, L.t("마지막 변경 되돌리기", "Undo last change"), v -> undoLast()).keyboardOverlays();
        else if (LANDSCAPE_AREA.equals(sectionId) || LARGE_AREA.equals(sectionId)) {
            // 키보드 사용자화처럼 머리글에 되돌리기 버튼 (이 화면에서 바꾼 것만 되돌린다)
            frame.withAction(Icons.UNDO, L.t("마지막 변경 되돌리기", "Undo last change"), v -> undoLast());
            frame.keyboardOverlays();   // 아래 미리보기 위로 실제 키보드가 덮는다
        }
        else if ("onehand".equals(sectionId)) frame.keyboardOverlays();   // 미리보기를 실제 키보드처럼 화면 아래에 붙인다
        ScrollView scroll = new ScrollView(this);
        scrollView = scroll;
        scroll.setFillViewport(true);
        scroll.setId(0x5E771001);   // 화면 모드를 바꿔 다시 만들 때 스크롤 위치를 복원한다
        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        int pad = Ui.dp(this, 16);
        list.setPadding(pad, 0, pad, Ui.dp(this, 32));
        scroll.addView(list);
        pinned = new LinearLayout(this);
        pinned.setOrientation(LinearLayout.VERTICAL);
        pinned.setPadding(pad, 0, pad, Ui.dp(this, 4));
        pinned.setVisibility(View.GONE);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.addView(pinned, matchWrap());
        // 스크롤 목록 위에 겹쳐 띄우는 층 (키보드 사용자화의 키 편집 창)을 놓을 수 있게 틀로 감싼다.
        middle = new android.widget.FrameLayout(this);
        middle.addView(scroll, new android.widget.FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        content.addView(middle, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        pinnedBottom = new LinearLayout(this);
        pinnedBottom.setOrientation(LinearLayout.VERTICAL);
        pinnedBottom.setVisibility(View.GONE);
        content.addView(pinnedBottom, matchWrap());
        setContentView(frame.wrap(content));
        undoButton = frame.actionButton();
        build();
        // 검색 결과에서 들어왔으면 그 항목까지 스크롤하고 깜빡여 알려 준다.
        String highlight = getIntent().getStringExtra(EXTRA_HIGHLIGHT);
        String target = getIntent().getStringExtra(EXTRA_TARGET);
        if (highlight != null && target != null && editorPreview != null) {
            scroll.postDelayed(() -> openEditorTarget(target, highlight), 250);
        } else if (highlight != null && target != null && target.startsWith(PROFILE_PART) && profilePreview != null) {
            scroll.postDelayed(() -> openProfileTarget(target, highlight), 250);
        } else if (highlight != null) {
            scroll.postDelayed(() -> highlightItem(scroll, highlight), 250);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        // 다른 화면에서 화면 모드나 강조 색을 바꿨으면 이 화면도 새 색으로 다시 만든다.
        if (!themeSignature.equals(AppTheme.signature(this)) || builtGeneration != importGeneration) {
            recreate();
            return;
        }
        updateStatus();
        refreshLayoutPreview();   // 기능키 순서·유무 화면에서 돌아왔을 때 미리보기에 바로 반영한다
        refreshOneHandPreview();
        refreshEditorPanel();   // 언어 순서·기능키 순서 화면 등에서 돌아왔을 때
    }

    @Override
    protected void onDestroy() {
        if (isFinishing() && editorPreview != null) {   // 키보드 사용자화를 떠난다: 되돌리기 기록도 버린다
            EditorUndo.clearAll();
        } else if (isFinishing() && profilePreview != null) {   // 가로 모드·폴더블 내부 화면 키보드 사용자화를 떠난다
            EditorUndo.leaveScope();
        }
        if (editorPrefListener != null) prefs.raw().unregisterOnSharedPreferenceChangeListener(editorPrefListener);
        super.onDestroy();
    }

    /** 되돌리기 단계: 화면을 한 번 눌렀다 뗄 때까지(손잡이·슬라이더 끌기) 바뀐 값을 한 단계로 묶는다. */
    @Override
    public boolean dispatchTouchEvent(android.view.MotionEvent e) {
        if (e.getActionMasked() == android.view.MotionEvent.ACTION_DOWN) EditorUndo.touchDown();
        try {
            return super.dispatchTouchEvent(e);
        } finally {
            int action = e.getActionMasked();
            // 떼는 순간에 저장하는 값까지 이 터치에 넣고 나서 끝낸다.
            if (action == android.view.MotionEvent.ACTION_UP || action == android.view.MotionEvent.ACTION_CANCEL) EditorUndo.touchUp();
        }
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) updateStatus();   // 입력기 선택 창을 닫고 돌아왔을 때
    }

    // ---------------------------------------------------------------- 화면 구성

    /**
     * 키보드 사용자화: 미리보기에서 키보드·영역·키를 눌러 설정하는 화면 (예전의 자판 레이아웃·자판 모양·입력 동작·길게 누르기·
     * 밀어서 글자 입력·밀어서 기능·도구 막대 메뉴를 합쳤다). 목록의 기본값으로 되돌리기는 설정 범위(Prefs.editorResetChoices)로 고른다.
     */
    private static final String KEYBOARD = "keyboard";
    /** 입력 언어 화면 (LanguageOrderEditorActivity). 메뉴와 검색에서 설정 화면 대신 그 화면을 연다. */
    private static final String LANGUAGES = "languages";

    /** 하위 메뉴 정의: {id, 제목, 설명}. */
    private static String[][] sections() {
        return new String[][]{
            {LANGUAGES, L.t("입력 언어", "Input languages"), L.t("한국어·영어, 언어 전환 순서", "Korean, English, switching order")},
            {KEYBOARD, L.t("키보드 사용자화", "Customize keyboard"), L.t("자판 배열, 모양, 입력, 길게 누르기, 밀기", "Layout, look, input, long press, swipe")},
            {"theme", L.t("테마", "Theme"), L.t("화면 모드, 강조 색", "Light/dark, accent color")},
            {"feedback", L.t("소리 및 진동", "Sound & vibration"), L.t("키를 누를 때의 진동과 소리", "Vibration and sound when you press keys")},
            {"words", L.t("추천, 학습 및 완성", "Suggestions, learning & autofill"), L.t("단어 추천, 학습, 단축어, 자동 완성", "Word suggestions, learning, text shortcuts, autofill")},
            {"clipemoji", L.t("클립보드 및 이모지", "Clipboard & emoji"), L.t("클립보드 기록, 편집 버튼, 이모지 크기·추천", "Clipboard history, edit buttons, emoji size and suggestions")},
            {"onehand", L.t("한 손 모드", "One-handed mode"), L.t("자판 폭·키 높이, 세로 위치", "Keyboard width, key height, vertical position")},
            {"backup", L.t("설정 가져오기 및 내보내기", "Import & export settings"), L.t("모든 설정을 파일로 저장하거나 불러오기", "Save all settings to a file or load them")},
            {"lab", L.t("실험실", "Labs"), L.t("밀기 방향 판정, 외국어 자판, 외장 키보드", "Swipe direction, other languages, physical keyboard")},
    };
    }

    /** 첫 화면 맨 위에 설정 메뉴와 따로 두는 항목. */
    private static String[] appEntry() {
        return new String[]{"app", L.t("정보", "About"), L.t("앱 버전, 개인정보 처리방침, 라이선스, 문의", "App version, privacy policy, licenses, contact")};
    }

    /** 메뉴에는 없고 다른 화면에서 여는 하위 화면. */
    private static final String LEARNED = "learned", BLOCKED = "blocked", PRIVACY = "privacy", LICENSES = "licenses",
            HALL_OF_FAME = "halloffame",
            LANDSCAPE_AREA = "landscapearea",
            LARGE_AREA = "largearea";

    private static final String SOURCE_URL = "https://github.com/TechTree-Alt/NewSwipe";
    private static final String ISSUES_URL = SOURCE_URL + "/issues";

    private static String sectionTitle(String id) {
        if (id.startsWith(EDITOR_PART)) return L.t("키보드 사용자화", "Customize keyboard") + " · " + id.substring(id.lastIndexOf(':') + 1);
        if (id.startsWith(PROFILE_PART)) {
            String[] parts = id.split(":", 4);
            String screen = parts.length < 4 ? null : sectionTitle(parts[1]);
            return screen == null ? null : screen + " · " + parts[3];
        }
        for (String[] s : sections()) if (s[0].equals(id)) return s[1];
        if (appEntry()[0].equals(id)) return appEntry()[1];
        switch (id) {
            case LEARNED: return L.t("학습한 단어", "Learned words");
            case BLOCKED: return L.t("학습하지 않는 단어", "Blocked words");
            case PRIVACY: return L.t("개인정보 처리방침", "Privacy policy");
            case LICENSES: return L.t("오픈소스 라이선스", "Open source licenses");
            case HALL_OF_FAME: return L.t("명예의 전당", "Hall of fame");
            case LANDSCAPE_AREA: return L.t("가로 모드 키보드 사용자화", "Customize landscape keyboard");
            case LARGE_AREA: return L.t("폴더블 내부 화면 키보드 사용자화", "Customize inner screen keyboard");
            default: return null;
        }
    }

    private void build() {
        String id = getIntent().getStringExtra(EXTRA_SECTION);
        if (id == null || sectionTitle(id) == null) {
            buildMain();
            return;
        }
        buildScreen(id);
        // 키보드 사용자화는 목록 안에 따로 둔다.
        if (hasReset(id) && !KEYBOARD.equals(id)) addResetButton(id);
    }

    /** 설정 화면 하나의 내용을 지금의 list에 만든다 (설정 검색이 화면 글자를 모을 때도 이것을 쓴다). */
    private void buildScreen(String id) {
        switch (id) {
            case KEYBOARD: buildKeyboardEditor(); break;
            case "onehand": buildOneHand(); break;
            case "theme": buildTheme(); break;
            case "words": buildWords(); break;
            case "clipemoji": buildTools(); break;
            case LEARNED: buildLearned(); break;
            case BLOCKED: buildBlocked(); break;
            case "feedback": buildFeedback(); break;
            case "backup": buildBackup(); break;
            case PRIVACY: buildPrivacy(); break;
            case LICENSES: buildLicenses(); break;
            case HALL_OF_FAME: buildHallOfFame(); break;
            case LANDSCAPE_AREA: buildLandscapeArea(); break;
            case "lab": buildLab(); break;
            case LARGE_AREA: buildLargeArea(); break;
            default: buildApp(); break;
        }
    }

    /** 기본값으로 되돌릴 설정이 있는 메뉴인지 (설정 메뉴와 키 위치·폭, 가로 모드·폴더블 내부 화면 키보드 사용자화 화면. 정보·가져오기 및 내보내기·학습한 단어 등은 제외). */
    private static boolean hasReset(String id) {
        if (LANDSCAPE_AREA.equals(id) || LARGE_AREA.equals(id)) return true;
        for (String[] sec : sections()) if (sec[0].equals(id) && !"backup".equals(id)) return true;
        return false;
    }

    /** 메뉴 맨 아래의 '기본값으로 되돌리기'. 확인을 거쳐 이 메뉴 안의 모든 값을 기본값으로 바꾼다. */
    private void addResetButton(String id) {
        list.addView(ResetDialog.button(this, sectionTitle(id), () -> reset(id)));
    }

    /** 이 화면이 담당하는 값만 기본값으로 바꾼다 (확인은 ResetDialog가 이미 받았다). */
    private void reset(String id) {
        if (LANDSCAPE_AREA.equals(id)) prefs.profileEditView(Prefs.PROFILE_LANDSCAPE).resetProfileArea();
        else if (LARGE_AREA.equals(id)) {
            // 지금 화면 방향(폴더블 내부 화면 세로·가로)의 값과 폴더블 내부 화면 판정 기준을 되돌린다.
            String now = largeProfileNow();
            if (now != null) prefs.profileEditView(now).resetProfileArea();
            prefs.raw().edit().remove(Prefs.LARGE_SCREEN_DP).apply();
        }
        else prefs.resetSection(id);
        importGeneration++;   // 뒤에 쌓인 설정 화면도 돌아오면 기본값으로 다시 그린다
        recreate();
        Toast.makeText(this, L.t("기본값으로 되돌렸습니다", "Restored defaults"), Toast.LENGTH_SHORT).show();
    }

    private void buildMain() {
        // 시작하기 (이미 기본 키보드로 쓰고 있으면 updateStatus()가 접는다)
        LinearLayout setup = section(L.t("시작하기", "Get started"));
        setupCard = setup;
        setupHeading = list.getChildAt(list.indexOfChild(setup) - 1);
        status = new TextView(this);
        status.setTextColor(textColor);
        status.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        status.setPadding(0, 0, 0, Ui.dp(this, 8));
        setup.addView(status);
        setup.addView(button(L.t("1. 키보드 사용 설정", "1. Enable the keyboard"), v ->
                startActivity(new Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))));
        setup.addView(button(L.t("2. NewSwipe를 기본 키보드로 선택", "2. Select NewSwipe as default"), v -> {
            InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
            if (imm != null) imm.showInputMethodPicker();
        }));

        // 정보: 설정 메뉴와 따로 둔다.
        LinearLayout app = menuCard(card());
        appCard = app;
        app.addView(menuRow(menuIcon(appEntry()[0]), appEntry()[1], appEntry()[2], v -> openSection(appEntry()[0])));

        LinearLayout menu = menuCard(section(L.t("설정", "Settings")));
        menuCard = menu;
        menuHeading = list.getChildAt(list.indexOfChild(menu) - 1);
        for (int i = 0; i < sections().length; i++) {
            String[] sec = sections()[i];
            if (i > 0) menu.addView(divider());
            menu.addView(menuRow(menuIcon(sec[0]), sec[1], sec[2], v -> openSection(sec[0])));
        }

        // 검색하는 동안에는 위 내용을 통째로 숨기고 검색 결과를 대신 보여 준다.
        mainWrap = new LinearLayout(this);
        mainWrap.setOrientation(LinearLayout.VERTICAL);
        while (list.getChildCount() > 0) {
            View child = list.getChildAt(0);
            ViewGroup.LayoutParams lp = child.getLayoutParams();
            list.removeViewAt(0);
            mainWrap.addView(child, lp);
        }
        list.addView(mainWrap, matchWrap());
        searchResults = menuCard(new LinearLayout(this));
        searchResults.setOrientation(LinearLayout.VERTICAL);
        searchResults.setBackground(Ui.round(cardColor, Ui.dp(this, 16)));
        searchResults.setVisibility(View.GONE);
        LinearLayout.LayoutParams rlp = matchWrap();
        rlp.topMargin = Ui.dp(this, 8);
        list.addView(searchResults, rlp);
    }

    /** 줄이 카드 가장자리까지 닿도록 안쪽 여백을 없애고, 리플이 둥근 모서리를 넘지 않게 자른다. */
    private static LinearLayout menuCard(LinearLayout card) {
        card.setPadding(0, 0, 0, 0);
        card.setOutlineProvider(android.view.ViewOutlineProvider.BACKGROUND);
        card.setClipToOutline(true);
        return card;
    }

    private void openSection(String id) {
        if (LANGUAGES.equals(id)) {
            startActivity(new Intent(this, LanguageOrderEditorActivity.class));
            return;
        }
        startActivity(new Intent(this, SettingsActivity.class).putExtra(EXTRA_SECTION, id));
    }

    // ---------------------------------------------------------------- 설정 검색

    private LinearLayout mainWrap, searchResults;

    /**
     * 설정 검색에서 항목 이름만으로는 못 찾는 말(줄임말·영어·같은 뜻의 다른 말)을 더해 주는 보조 사전:
     * {화면 id, 항목 이름, 같이 찾을 낱말}. 항목 목록 자체는 설정 화면을 실제로 만들어 모으므로 여기 없는 항목도 검색된다.
     * 이름이 바뀌어 맞는 항목이 없어진 줄은 그냥 무시된다 (검색이 깨지지 않고, 그 낱말로만 못 찾게 된다).
     */
    private static String[][] searchSynonyms() {
        return new String[][]{
            {KEYBOARD, L.t("글자 키 높이", "Letter key height"), L.t("키보드 높이 키 높이 크기", "keyboard height key height size")},
            {KEYBOARD, L.t("키 글자 크기", "Key text size"), L.t("폰트 글씨 크기", "font text size")},
            {KEYBOARD, L.t("키 곡률", "Key corner radius"), L.t("둥글기 모서리 라운드", "round rounded corners")},
            {KEYBOARD, L.t("그림자 세기", "Shadow strength"), L.t("그림자", "shadow")},
            {KEYBOARD, L.t("키 그림자", "Key shadow"), L.t("입체감", "depth 3d")},
            {KEYBOARD, L.t("숫자 줄 높이", "Number row height"), L.t("숫자 행 크기", "number row size")},
            {KEYBOARD, L.t("기능키 높이", "Function key height"), L.t("맨 아래 줄 스페이스바 줄 높이 크기", "bottom row space bar row height size")},
            {KEYBOARD, L.t("키 누름 미리보기", "Key press popup"), L.t("말풍선 팝업 확대", "popup bubble enlarge preview")},
            {KEYBOARD, L.t("글자 키 힌트 표시", "Show letter key hints"), L.t("힌트 숨기기 없애기 보이기 길게 눌러 입력할 문자", "hint hide show long press characters")},
            {KEYBOARD, L.t("힌트 위치", "Hint position"), L.t("글자 키 힌트 자리 왼쪽 위 오른쪽 위 가운데 아래 중간", "letter key hint position top left top right center bottom middle")},
            {KEYBOARD, L.t("숫자 키 힌트 없애기", "Hide number key hints"), L.t("힌트 숨기기", "hint hide")},
            {KEYBOARD, L.t("스페이스바에 언어 이름 보이기", "Show language name on space bar"), L.t("스페이스 띄어쓰기 언어 표시 한국어 영어 일본어 중국어", "space label language korean english japanese chinese")},
            {KEYBOARD, L.t("왼쪽 여백", "Left padding"), L.t("패딩 간격", "padding margin spacing")},
            {KEYBOARD, L.t("오른쪽 여백", "Right padding"), L.t("패딩 간격", "padding margin spacing")},
            {KEYBOARD, L.t("위쪽 여백", "Top padding"), L.t("패딩 간격", "padding margin spacing")},
            {KEYBOARD, L.t("아래쪽 여백", "Bottom padding"), L.t("패딩 간격", "padding margin spacing")},
            {KEYBOARD, L.t("키 사이 좌우 간격", "Horizontal key gap"), L.t("키 간격 갭", "key gap spacing")},
            {KEYBOARD, L.t("키 사이 상하 간격", "Vertical key gap"), L.t("키 간격 갭", "key gap spacing")},
            {"clipemoji", L.t("이모지 크기", "Emoji size"), L.t("이모티콘 크기 글자 키우기 크게 이모지 창", "emoji size bigger large emoji panel")},
            {KEYBOARD, L.t("상세 배열", "Advanced layout"), L.t("자·모음 균형 배열 분리 키보드 스플릿 split 자음 모음 반반 균형 한글 자판 배열 두벌식 단모음 쿼티 qwerty 레이아웃", "balanced consonant vowel layout split keyboard halves korean layout dubeolsik danmoeum qwerty")},
            {KEYBOARD, L.t("가로 모드 키보드 사용자화", "Customize landscape keyboard"), L.t("가로 화면 landscape", "landscape horizontal")},
            {KEYBOARD, L.t("숫자 줄 표시", "Show number row"), L.t("숫자 줄 1234567890 숫자열", "number row 1234567890 digits")},
            {KEYBOARD, L.t("기능키 순서·유무 사용자화", "Customize function keys"), L.t("맨 아래 줄 하단 키 쉼표 지구본 온점 엔터", "bottom row keys comma globe period enter")},
            {KEYBOARD, L.t("격자 정렬", "Grid alignment"), L.t("그리드 정렬 칸", "grid align columns")},
            // 화면 모드·강조 색은 소제목뿐이라 검색 목록에 없다: 테마 화면 자체로 찾게 한다.
            {"theme", L.t("테마", "Theme"), L.t("다크 라이트 어두운 밝은 야간 화면 모드 색상 컬러 포인트 강조 색", "dark light night mode theme color accent")},
            {"theme", L.t("색 정렬", "Color alignment"), L.t("키 색", "Key colors")},
            {KEYBOARD, L.t("문자 길게 누르기 시간", "Character long-press delay"), L.t("롱프레스 길게 누르기 시간 지연 특수문자 반복 입력 연타 연속 입력", "long press delay time special characters repeat")},
            {KEYBOARD, L.t("기능키 길게 누르기 시간", "Function key long-press delay"), L.t("롱프레스 시간 길게 눌러 지우기 이모지 입력기 선택", "long press delay hold delete emoji keyboard picker")},
            {KEYBOARD, L.t("쌍자음 인식 시간", "Double consonant tap window"), L.t("연속 탭 연속 누르기 된소리 ㄲ ㅆ 시간", "double tap repeat ㄲ ㅆ time")},
            {KEYBOARD, L.t("쌍자음 입력", "Double consonant input"), L.t("된소리 ㄲ ㅆ ㅃ 자음 연속 탭 연속 누르기 두 번 아래로 밀기", "double consonants ㄲ ㅆ ㅃ double tap swipe down")},
            {KEYBOARD, L.t("이중모음 입력", "Y-vowel input"), L.t("ㅑ ㅛ ㅕ 모음 연속 탭 연속 누르기 두 번 아래로 밀기", "ㅑ ㅛ ㅕ vowels double tap swipe down")},
            {KEYBOARD, L.t("영어 자동 대문자", "English auto-capitalize"), L.t("대소문자 자동 대문자 shift 첫 글자", "capital letters uppercase shift first letter sentence")},
            {KEYBOARD, L.t("두 번 눌러 마침표", "Double-tap for period"), L.t("점 온점 더블 스페이스바", "period dot double tap space")},
            {KEYBOARD, L.t("인식 범위 좁히기", "Shrink touch area"), L.t("지우기 백스페이스 스페이스바 오타 오입력 터치 영역", "delete backspace space bar typos mistaps touch area")},
            {KEYBOARD, L.t("미는 거리", "Swipe distance"), L.t("스와이프 거리 민감도 밀어서 쌍자음 겹받침 이중모음 ㄲ ㅆ ㅘ 쉼표 글자 입력", "swipe distance sensitivity double consonants final clusters ㄲ ㅆ ㅘ comma characters")},
            {KEYBOARD, L.t("기능키 미는 거리", "Function key swipe distance"), L.t("스와이프 기능 스페이스바 커서 이동 언어 전환 단어 삭제 이모지 도구 막대 밀기 복사 붙여넣기", "swipe actions space bar cursor language switch delete word emoji toolbar copy paste")},
            {KEYBOARD, L.t("키보드 밀기 사용자화", "Customize keyboard swipes"), L.t("두 손가락 실행 취소 다시 실행 undo redo 트랙패드 커서 자유 이동 문자 키", "two fingers undo redo trackpad free cursor letter keys")},
            {KEYBOARD, L.t("커서 이동 속도 좌우", "Cursor speed (horizontal)"), L.t("스페이스바 커서 키 트랙패드", "space bar cursor key trackpad")},
            {KEYBOARD, L.t("입력 언어", "Input languages"), L.t("지구본 언어 차례 순서 바꾸기 한국어 영어 일본어 중국어", "globe language order switch korean english japanese chinese")},
            {LANGUAGES, L.t("입력 언어", "Input languages"), L.t("지구본 언어 차례 순서 바꾸기 한국어 영어 일본어 중국어 자판 끄기 켜기", "globe language order switch korean english japanese chinese keyboard on off")},
            {KEYBOARD, L.t("키보드 밀기 사용자화", "Customize keyboard swipes"), L.t("자판 스와이프 기능", "keyboard swipe actions gestures")},
            {KEYBOARD, L.t("위로 밀어서 추천 단어 입력", "Swipe up to pick suggestions"), L.t("추천 단어 스와이프 고르기 자판 위로", "suggestions swipe pick keyboard up")},
            {"words", L.t("도구 막대 전체를 쓰기", "Use full toolbar"), L.t("추천 툴바 전체", "suggestions toolbar full width")},
            {"words", L.t("추천 단어 뒤에 공백 포함", "Add space after suggestions"), L.t("띄어쓰기 스페이스", "space after word")},
            {"words", L.t("단어 추천", "Word suggestions"), L.t("자동 완성 예측 추천", "autocomplete prediction suggestions")},
            {"clipemoji", L.t("이모지 추천", "Emoji suggestions"), L.t("이모티콘 추천", "emoji suggestions")},
            {"words", L.t("단축어 사용자화", "Customize text shortcuts"), L.t("줄임말 약어 텍스트 확장", "abbreviations text expansion snippets")},
            {"words", L.t("단축어 사용", "Use text shortcuts"), L.t("줄임말 약어 텍스트 확장", "abbreviations text expansion snippets")},
            {"words", L.t("자동 수정", "Autocorrect"), L.t("오타 교정 자동 고침", "typos correction spelling")},
            {"words", L.t("입력한 단어 학습", "Learn typed words"), L.t("사용자 사전 개인화", "personal dictionary personalization")},
            {"words", L.t("단어를 지울 때 확인", "Confirm before deleting words"), L.t("삭제 확인 학습한 단어", "delete confirm learned words")},
            {"words", L.t("학습한 단어 보기", "View learned words"), L.t("사용자 사전 목록", "personal dictionary list")},
            {"words", L.t("학습하지 않는 단어 보기", "View blocked words"), L.t("학습 제외 차단 지운 단어 다시 학습", "blocked excluded deleted words learn again")},
            {"words", L.t("학습한 단어 모두 지우기", "Delete all learned words"), L.t("사용자 사전 초기화 삭제", "personal dictionary reset clear")},
            {"words", L.t("자동 완성 제안", "Autofill suggestions"), L.t("오토필 Autofill Google 삼성패스 비밀번호 아이디 계정 칩 인증번호", "autofill google samsung pass passwords username account chips codes")},
            {"feedback", L.t("키 진동", "Key vibration"), L.t("햅틱 진동 켜기 끄기", "haptics vibration on off")},
            {"feedback", L.t("진동 길이", "Vibration length"), L.t("햅틱 ms 밀리초 진동 시간", "haptics ms milliseconds vibration duration")},
            {"feedback", L.t("진동 강도", "Vibration strength"), L.t("햅틱 세기 진폭", "haptics strength amplitude")},
            {"feedback", L.t("키 종류별로 다르게", "Different for each key type"), L.t("진동 지우기 스페이스 엔터 Shift 기호 언어 전환 도구 막대 키 햅틱", "vibration delete space enter shift symbols language switch toolbar keys haptics")},
            {"feedback", L.t("밀기·길게 누르기 진동", "Vibrate on swipe and long press"), L.t("햅틱 스와이프 롱프레스", "haptics swipe long press")},
            {"feedback", L.t("키 소리", "Key sound"), L.t("클릭음 사운드 소리 켜기", "click sound audio on")},
            {"feedback", L.t("소리 크기", "Sound volume"), L.t("볼륨 클릭음", "volume click sound")},
            {KEYBOARD, L.t("도구 막대 표시", "Show toolbar"), L.t("툴바 켜기 끄기", "toolbar on off")},
            {KEYBOARD, L.t("도구 막대 버튼 사용자화", "Customize toolbar buttons"), L.t("툴바 버튼 순서 음성 입력 마이크 클립보드 이모지 실행 취소", "toolbar buttons order voice input microphone clipboard emoji undo")},
            {KEYBOARD, L.t("자판 아래에 두기", "Place below keyboard"), L.t("툴바 위치 아래 위", "toolbar position bottom top")},
            {KEYBOARD, L.t("도구 막대 높이", "Toolbar height"), L.t("툴바 높이 크기", "toolbar height size")},
            {KEYBOARD, L.t("도구 버튼 크기", "Toolbar button size"), L.t("툴바 아이콘 크기", "toolbar icon size")},
            {"clipemoji", L.t("이미지 저장", "Save images"), L.t("클립보드 사진", "clipboard photos pictures")},
            {"clipemoji", L.t("클립보드 기록 저장", "Save clipboard history"), L.t("복사 기록 히스토리", "copy history")},
            {"clipemoji", L.t("고정 항목 분리", "Separate pinned items"), L.t("클립보드 고정 핀 페이지 따로", "clipboard pinned pin page separate")},
            {"clipemoji", L.t("선택하면 편집 버튼 보이기", "Show edit buttons on selection"), L.t("잘라내기 복사 붙여넣기 전체 선택 편집 칩", "cut copy paste select all edit chips")},
            {"clipemoji", L.t("클립보드 기록 모두 지우기", "Delete all clipboard history"), L.t("복사 기록 삭제", "copy history delete clear")},
            {"onehand", L.t("자판 폭", "Keyboard width"), L.t("한 손 모드 너비", "one-handed mode width")},
            {"onehand", L.t("가로 모드 자판 폭", "Landscape keyboard width"), L.t("한 손 모드 너비 가로", "one-handed mode width landscape")},
            {"onehand", L.t("키 높이", "Key height"), L.t("한 손 모드 높이", "one-handed mode height")},
            {"onehand", L.t("세로 위치", "Vertical position"), L.t("한 손 모드 위치 올리기", "one-handed mode position raise")},
            {"lab", L.t("일본어 자판", "Japanese keyboard"), L.t("일본어 히라가나 가타카나 한자 변환 플릭 로마자 사전 팩 japanese", "japanese hiragana katakana kanji conversion flick romaji dictionary pack")},
            {"lab", L.t("반각 공백 쓰기", "Use half-width spaces"), L.t("일본어 스페이스 전각 공백 반각 공백 띄어쓰기", "japanese space full-width half-width spacing")},
            {"lab", L.t("중국어 자판", "Chinese keyboard"), L.t("중국어 병음 한자 간체 사전 팩 chinese pinyin", "chinese pinyin hanzi simplified dictionary pack")},
            {KEYBOARD, L.t("기호 자판 대신 숫자 패드 사용", "Use number pad instead of symbols"), L.t("숫자 패드 넘버 패드 3×4 숫자 자판 기호 키 ?123 계산기 전화", "number pad numpad 3x4 digits symbol key ?123 calculator phone")},
            {"lab", L.t("아래로 밀기 엄격도", "Swipe-down strictness"), L.t("스와이프 방향 판정 민감도", "swipe direction detection sensitivity")},
            {"lab", L.t("위로 밀기 엄격도", "Swipe-up strictness"), L.t("스와이프 방향 판정 민감도", "swipe direction detection sensitivity")},
            {LARGE_AREA, L.t("폴더블 내부 화면 판정 기준", "Inner screen threshold"), L.t("대화면 폴더블 큰 화면 태블릿 dp 짧은 쪽 폭", "large screen foldable big screen tablet dp shortest width")},
            {KEYBOARD, L.t("폴더블 내부 화면 키보드 사용자화", "Customize inner screen keyboard"), L.t("대화면 폴더블 큰 화면 태블릿", "large screen foldable big screen tablet")},
            {KEYBOARD, L.t("폴더블 내부 화면 별도 키보드", "Separate inner screen keyboard"), L.t("대화면 레이아웃 폴더블 큰 화면 태블릿", "large screen layout foldable big screen tablet")},
            {"lab", L.t("한·영 전환 키 바꾸기", "Change Korean/English switch keys"), L.t("한영 한/영 전환 언어 전환 키 외장 키보드 블루투스 USB 물리 하드웨어", "han yeong korean english switch language key physical keyboard bluetooth usb hardware")},
            {"lab", L.t("한·영 키", "Han/Yeong key"), L.t("한영 한/영 전환 언어 전환 키 외장 키보드", "han yeong korean english switch language key physical keyboard")},
            {"lab", L.t("한·영 키 직접 지정", "Assign switch key"), L.t("한영 한/영 전환 키 바꾸기 외장 키보드", "han yeong korean english switch key change physical keyboard")},
            {"lab", L.t("언어 전환 알림", "Language switch notice"), L.t("한영 전환 외장 키보드", "korean english switch physical keyboard")},
            {"lab", L.t("도구 막대 보이기", "Keep toolbar visible"), L.t("외장 키보드 물리 키보드 블루투스 연결 툴바 클립보드 이모지", "physical keyboard hardware bluetooth connected toolbar clipboard emoji")},
            {"app", L.t("개인정보 처리방침", "Privacy policy"), L.t("프라이버시 privacy 정보", "privacy")},
            {"app", L.t("오픈소스 라이선스", "Open source licenses"), L.t("라이센스 license 정보", "license")},
            {"app", L.t("소스 코드", "Source code"), L.t("깃허브 github 정보", "github source")},
            {"app", L.t("문의", "Contact"), L.t("이슈 버그 제보 연락 정보", "issue bug report contact")},
            {"app", L.t("명예의 전당", "Hall of fame"), L.t("비공개 테스트 테스터 감사 정보", "closed test testers thanks")},
            {"app", L.t("런처에 앱 아이콘 표시", "Show app icon in launcher"), L.t("앱 아이콘 숨기기 홈 화면", "app icon hide home screen launcher")},
            {"backup", L.t("설정 가져오기", "Import settings"), L.t("복원 불러오기 import", "restore load import")},
            {"backup", L.t("설정 내보내기", "Export settings"), L.t("백업 저장 export", "backup save export")},
    };
    }

    /** 검색 화면에 마지막으로 들어온 검색어 (검색 목록이 준비되면 그 검색어로 다시 찾는다). */
    private String lastQuery = "";

    private void onSearchQuery(String raw) {
        if (mainWrap == null || searchResults == null) return;
        String query = raw == null ? "" : raw.trim();
        lastQuery = query;
        boolean on = !query.isEmpty();
        mainWrap.setVisibility(on ? View.GONE : View.VISIBLE);
        searchResults.setVisibility(on ? View.VISIBLE : View.GONE);
        if (!on) return;
        searchResults.removeAllViews();
        ensureSearchIndex();
        if (searchEntries == null) {   // 설정 화면의 글자를 모으는 중이다 (처음 한 번만)
            searchResults.addView(searchNote(L.t("검색 목록을 준비하는 중입니다…", "Preparing search…")));
            return;
        }
        // 항목: {화면 id, 이름, 설명·같이 찾을 낱말, "m" = 설정 메뉴, (메뉴만) 보일 설명}. 메뉴는 상수에서, 나머지는 모아 둔 글자에서 온다.
        java.util.List<String[]> entries = new java.util.ArrayList<>();
        for (String[] sec : sections()) {
            String extra = synonyms().get(sec[0] + "\0" + sec[1]);   // 화면 자체에 붙인 같이 찾을 낱말
            entries.add(new String[]{sec[0], sec[1], extra == null ? sec[2] : sec[2] + " " + extra, "m", sec[2]});
        }
        entries.add(new String[]{appEntry()[0], appEntry()[1], appEntry()[2], "m", appEntry()[2]});
        for (String[] e : searchEntries) {
            String extra = synonyms().get(synonymScreen(e[0]) + "\0" + e[1]);
            entries.add(new String[]{e[0], e[1], extra == null ? e[2] : e[2] + " " + extra, ""});
        }
        java.util.List<String[]> hits = new java.util.ArrayList<>();   // 이름에서 찾은 것을 먼저
        java.util.List<String[]> weak = new java.util.ArrayList<>();
        for (String[] e : entries) {
            if (matchesAll(query, e[1])) hits.add(e);
            else if (matchesAll(query, e[1] + " " + e[2] + " " + sectionTitle(e[0]))) weak.add(e);
        }
        hits.addAll(weak);
        boolean unlocked = prefs.setupDone();
        int shown = 0;
        for (String[] e : hits) {
            if (shown >= 40) break;
            String section = e[0], title = e[1];
            boolean isMenu = "m".equals(e[3]);
            if (shown > 0) searchResults.addView(divider());
            View row = menuRow(menuIcon(section), title, isMenu ? e[4] : sectionTitle(section),
                    v -> openSection(section, isMenu ? null : title));
            if (!unlocked) {   // 시작하기를 마치기 전에는 설정 메뉴처럼 누를 수 없다
                row.setAlpha(0.38f);
                setEnabledDeep(row, false);
            }
            searchResults.addView(row);
            shown++;
        }
        if (shown == 0) searchResults.addView(searchNote(L.t("일치하는 설정이 없습니다", "No matching settings")));
    }

    /** 같이 찾을 낱말(searchSynonyms())을 찾을 때 쓰는 화면 id: 창을 가리키는 id는 그 창이 있는 화면. */
    private static String synonymScreen(String id) {
        if (id.startsWith(EDITOR_PART)) return KEYBOARD;
        if (id.startsWith(PROFILE_PART)) {
            String[] parts = id.split(":", 3);
            return parts.length < 3 ? id : parts[1];
        }
        return id;
    }

    private TextView searchNote(String text) {
        TextView none = new TextView(this);
        none.setText(text);
        none.setTextColor(hintColor);
        none.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        none.setGravity(Gravity.CENTER);
        none.setPadding(0, Ui.dp(this, 24), 0, Ui.dp(this, 24));
        return none;
    }

    private java.util.Map<String, String> synonymMap;

    private java.util.Map<String, String> synonyms() {
        if (synonymMap == null) {
            synonymMap = new java.util.HashMap<>();
            for (String[] r : searchSynonyms()) synonymMap.put(r[0] + "\0" + r[1], r[2]);
        }
        return synonymMap;
    }

    // ---- 검색 목록: 설정 화면을 실제로 만들어 항목 이름을 모은다
    //
    // 설정을 추가하거나 이름을 바꿔도 검색 목록을 따로 고칠 필요가 없다. 부담을 줄이려고
    //  - 검색창을 처음 열어 글자를 입력할 때만 모으고,
    //  - 화면마다 한 번씩 나눠서 (사이마다 화면이 반응하게) 만들고, 미리보기 자판처럼 무거운 부분은 건너뛰며,
    //  - 글자만 뽑고 뷰는 곧바로 버리고, 결과는 앱 버전별 파일에 저장해 다음부터는 파일만 읽는다.

    /** 모은 항목: {화면 id, 이름, 설명}. 아직 모으지 않았으면 null. */
    private java.util.List<String[]> searchEntries;
    private boolean harvestRunning;
    /** 화면을 검색용으로 만드는 중인지 (무거운 미리보기를 건너뛴다). */
    private boolean harvesting;
    private final android.os.Handler uiHandler = new android.os.Handler(android.os.Looper.getMainLooper());

    /** 검색 목록에 넣을 화면 (개인정보 처리방침 같은 안내문과 학습한 단어는 뺀다). */
    private static final String[] HARVEST_IDS = {KEYBOARD, EDITOR_PARTS, "theme", "feedback", "words", "clipemoji", "onehand", "backup", "lab", "app",
            LANDSCAPE_AREA, LARGE_AREA};

    private void ensureSearchIndex() {
        if (searchEntries != null || harvestRunning) return;
        java.util.List<String[]> cached = readSearchCache();
        if (cached != null) {
            searchEntries = cached;
            return;
        }
        harvestRunning = true;
        stepHarvest(new java.util.ArrayList<>(), 0);
    }

    private void stepHarvest(java.util.List<String[]> out, int index) {
        if (isFinishing() || isDestroyed()) {
            harvestRunning = false;
            return;
        }
        if (index >= HARVEST_IDS.length) {
            // 같은 화면의 같은 이름은 하나로 합친다.
            java.util.Map<String, String[]> unique = new java.util.LinkedHashMap<>();
            for (String[] e : out) unique.putIfAbsent(e[0] + "\0" + e[1], e);
            searchEntries = new java.util.ArrayList<>(unique.values());
            harvestRunning = false;
            writeSearchCache(searchEntries);
            if (!lastQuery.isEmpty()) onSearchQuery(lastQuery);
            return;
        }
        if (EDITOR_PARTS.equals(HARVEST_IDS[index])) harvestEditorParts(out);
        else harvestScreen(HARVEST_IDS[index], out);
        uiHandler.post(() -> stepHarvest(out, index + 1));
    }

    /** 검색 목록에 넣는 기능키 창 (영역 창은 모두 넣는다). 지금 자판에 없는 키는 건너뛴다. */
    private static final int[] HARVEST_KEYS = {com.alternative_studios.newswipe.keyboard.Key.SPACE,
            com.alternative_studios.newswipe.keyboard.Key.DELETE, com.alternative_studios.newswipe.keyboard.Key.ENTER,
            com.alternative_studios.newswipe.keyboard.Key.SHIFT, com.alternative_studios.newswipe.keyboard.Key.TO_SYMBOLS,
            com.alternative_studios.newswipe.keyboard.Key.LANGUAGE, com.alternative_studios.newswipe.keyboard.Key.EMOJI,
            com.alternative_studios.newswipe.keyboard.Key.EMOJI_KEY, com.alternative_studios.newswipe.keyboard.Key.FUNCTION,
            com.alternative_studios.newswipe.keyboard.Key.CLIPBOARD, com.alternative_studios.newswipe.keyboard.Key.CURSOR_PAD,
            com.alternative_studios.newswipe.keyboard.Key.VOICE};
    /** 검색 목록에 넣는 글자 키 창: 따로 정할 것(입력할 글자)이 있는 쉼표·온점 자리. */
    private static final String[] HARVEST_CHAR_SLOTS = {",", "."};

    /**
     * 키보드 사용자화의 영역 창·키보드 전체 설정 창·키 창·도구 막대 버튼 창을 보이지 않는 곳에 만들어 항목 이름을 모은다
     * (목록에는 없는 설정들). 창마다 그 창 자체("… 설정")도 넣어, 창 이름으로 찾아 그 창을 바로 열 수 있게 한다.
     * 메인 화면처럼 키보드 사용자화가 아닌 화면에서도 모으므로, 창이 쓰는 칸(키 편집 칸·안내 칸)은 여기서 임시로 만든다.
     */
    private void harvestEditorParts(java.util.List<String[]> out) {
        LinearLayout savedList = list, savedNote = areaNoteBox, savedSplit = splitHolder, savedBalanced = balancedHolder;
        KeyEditPanel savedPanel = keyPanel;
        TextView savedSpaceNote = toolbarSpaceNote;
        ExpressiveSwitch savedClipSwitch = clipImagesSwitch;
        int savedTab = letterTab;
        KeyEditPanel.Host idle = new KeyEditPanel.Host() {
            @Override public com.alternative_studios.newswipe.keyboard.Key refreshNow() { return null; }
            @Override public void close() { }
            @Override public void onSizeChanged(String prefKey) { }
        };
        harvesting = true;
        try {
            areaNoteBox = new LinearLayout(this);
            areaNoteBox.setOrientation(LinearLayout.VERTICAL);
            keyPanel = new KeyEditPanel(this, prefs, idle);
            for (int a = 0; a < KeyboardPreview.AREAS; a++) {
                final int area = a;
                if (a == KeyboardPreview.AREA_LETTERS) {
                    // 글자 키 창은 자판마다 항목이 달라 자판마다 모은다 ("t:자판:이름").
                    for (int t = 0; t < KeyboardPreview.tabNames().length; t++) {
                        letterTab = t;
                        String name = KeyboardPreview.areaName(a, t);
                        harvestPart(EDITOR_PART + "t:" + t + ":" + name, name + L.t(" 설정", " settings"), out, scratch -> buildArea(area));
                    }
                    letterTab = KeyboardPreview.TAB_KOREAN;
                    continue;
                }
                String name = KeyboardPreview.areaNames()[a];
                harvestPart(EDITOR_PART + "a:" + a + ":" + name, name + L.t(" 설정", " settings"), out, scratch -> {
                    if (area == KeyboardPreview.AREA_TOOLBAR) {   // 도구 막대 창은 위에 도구 막대 칸(KeyEditPanel)이 있다
                        keyPanel = new KeyEditPanel(this, prefs, idle);
                        keyPanel.show(KeyboardPreview.Selection.toolbar(), null);
                        scratch.addView(keyPanel.view());
                    }
                    buildArea(area);
                });
            }
            harvestPart(EDITOR_PART + "b:0:" + L.t("키보드 전체 설정", "Whole keyboard settings"), L.t("키보드 전체 설정", "Whole keyboard settings"), out, scratch -> buildBoard());
            com.alternative_studios.newswipe.keyboard.KeyboardLayout l =
                    com.alternative_studios.newswipe.keyboard.KeyboardLayout.korean(prefs);
            for (int type : HARVEST_KEYS) {
                com.alternative_studios.newswipe.keyboard.Key k = firstKey(l, type);
                if (k == null) continue;
                harvestPanel(out, idle, KeyboardPreview.Selection.key(k, KeyboardPreview.TAB_KOREAN), k, "k:" + type);
            }
            for (String slot : HARVEST_CHAR_SLOTS) {
                com.alternative_studios.newswipe.keyboard.Key k = charKey(l, slot);
                if (k == null) continue;
                harvestPanel(out, idle, KeyboardPreview.Selection.key(k, KeyboardPreview.TAB_KOREAN), k, "c:" + slot);
            }
            for (String id : com.alternative_studios.newswipe.keyboard.ToolbarButtons.ALL) {
                harvestPanel(out, idle, KeyboardPreview.Selection.tool(id), null, "o:" + id);
            }
        } catch (RuntimeException ignored) {
            // 자판을 못 읽었어도 이미 모은 창은 쓴다.
        } finally {
            harvesting = false;
            list = savedList;
            areaNoteBox = savedNote;
            splitHolder = savedSplit;
            balancedHolder = savedBalanced;
            keyPanel = savedPanel;
            toolbarSpaceNote = savedSpaceNote;
            clipImagesSwitch = savedClipSwitch;
            letterTab = savedTab;
        }
        // 키 창의 '길게 누르면' 옆에 보이는 지금 기능 이름(없음·이모지 열기 등)과 되돌리기 버튼은 설정 이름이 아니다.
        java.util.Set<String> values = new java.util.HashSet<>();
        for (String id : com.alternative_studios.newswipe.keyboard.SwipeAction.idsForSpaceLongPress()) {
            values.add(com.alternative_studios.newswipe.keyboard.SwipeAction.label(id));
        }
        values.add(L.t("기본값으로 되돌리기", "Reset to default"));
        values.addAll(java.util.Arrays.asList(inputModes()));   // 쌍자음·이중모음 입력 줄 옆의 지금 값
        values.add(detailLayouts()[DETAIL_NONE]);   // 상세 배열 줄 옆의 지금 값 (나머지 둘은 설정 이름으로도 찾게 둔다)
        out.removeIf(e -> e[0].startsWith(EDITOR_PART) && values.contains(e[1]) && !e[1].endsWith(L.t(" 설정", " settings")));
    }

    /** 키·도구 막대 버튼 창 하나를 만들어 모은다. where는 화면 id의 종류와 값 ("k:키 종류", "c:글자 자리", "o:버튼 ID"). */
    private void harvestPanel(java.util.List<String[]> out, KeyEditPanel.Host host, KeyboardPreview.Selection sel,
                              com.alternative_studios.newswipe.keyboard.Key k, String where) {
        KeyEditPanel panel = new KeyEditPanel(this, prefs, host);
        try {
            panel.show(sel, k);
        } catch (RuntimeException e) {
            return;
        }
        String title = panel.title();
        if (title == null || title.isEmpty()) return;
        String sheetTitle = title + L.t(" 설정", " settings");
        for (String[] e : out) if (e[0].startsWith(EDITOR_PART) && e[1].equals(sheetTitle)) return;   // 같은 창 (예: 이모지 키 두 종류)
        harvestPart(EDITOR_PART + where + ":" + title, sheetTitle, out, scratch -> scratch.addView(panel.view()));
    }

    /**
     * 창 하나를 build로 빈 칸에 만들어 항목 이름을 모은다. 맨 앞에는 창 자체(창 머리글에 보이는 이름)를 넣는다.
     * 한 창을 못 만들어도 다른 창은 계속 모은다.
     */
    private void harvestPart(String id, String sheetTitle, java.util.List<String[]> out,
                             java.util.function.Consumer<LinearLayout> build) {
        LinearLayout scratch = new LinearLayout(this);
        scratch.setOrientation(LinearLayout.VERTICAL);
        LinearLayout saved = list;
        list = scratch;
        java.util.List<String[]> found = new java.util.ArrayList<>();
        try {
            build.accept(scratch);
            collectLabels(id, scratch, found, new View[1], new String[1][]);
        } catch (RuntimeException ignored) {
            // 만들다 만 창이라도 그때까지 모은 항목과 창 이름은 넣는다.
        } finally {
            list = saved;
            areaNoteBox.removeAllViews();
        }
        out.add(new String[]{id, sheetTitle, String.join(" ", names(found))});
        out.addAll(found);
    }

    /** 모은 항목들의 이름 (창 자체를 찾을 때 같이 견줄 낱말). 너무 길지 않게 앞쪽만. */
    private static java.util.List<String> names(java.util.List<String[]> found) {
        java.util.List<String> n = new java.util.ArrayList<>();
        for (String[] e : found) {
            if (n.size() >= 12) break;
            n.add(e[1]);
        }
        return n;
    }

    /** 자판에서 글자 자리가 slot인 첫 글자 키. */
    private static com.alternative_studios.newswipe.keyboard.Key charKey(
            com.alternative_studios.newswipe.keyboard.KeyboardLayout l, String slot) {
        if (l == null) return null;
        for (com.alternative_studios.newswipe.keyboard.KeyboardLayout.Row row : l.rows) {
            for (com.alternative_studios.newswipe.keyboard.Key k : row.keys) {
                if (k.type == com.alternative_studios.newswipe.keyboard.Key.CHAR && slot.equals(k.slot())) return k;
            }
        }
        return null;
    }

    private static com.alternative_studios.newswipe.keyboard.Key firstKey(
            com.alternative_studios.newswipe.keyboard.KeyboardLayout l, int type) {
        if (l == null) return null;
        for (com.alternative_studios.newswipe.keyboard.KeyboardLayout.Row row : l.rows) {
            for (com.alternative_studios.newswipe.keyboard.Key k : row.keys) if (k.type == type) return k;
        }
        return null;
    }

    /**
     * 검색 결과로 키보드 사용자화에 들어왔다: 그 영역·키·버튼의 창을 열고 항목을 찾아 깜빡인다.
     * label이 창 이름("… 설정")이면 창만 연다.
     */
    private void openEditorTarget(String target, String label) {
        if (isFinishing() || isDestroyed() || editorPreview == null) return;
        String[] parts = target.split(":", 4);
        if (parts.length < 4) return;
        KeyboardPreview.Selection sel;
        String kind = parts[1], arg = parts[2];
        if ("c".equals(kind) || "o".equals(kind)) {
            // 글자 키(쉼표·온점 자리)와 도구 막대 버튼은 한글 자판의 키 설정 페이지에서 고른다.
            showEditorTab(KeyboardPreview.TAB_KOREAN);
            editorPreview.setLayer(KeyboardPreview.LAYER_KEY);
            if ("c".equals(kind)) {
                com.alternative_studios.newswipe.keyboard.Key k = charKey(editorPreview.layout(), arg);
                if (k == null) return;
                sel = KeyboardPreview.Selection.key(k, KeyboardPreview.TAB_KOREAN);
            } else {
                if (!prefs.toolbar() || !prefs.toolShown(arg)) {   // 도구 막대나 그 버튼을 감춰 두면 도구 막대 창을 연다
                    editorPreview.setLayer(KeyboardPreview.LAYER_AREA);
                    sel = KeyboardPreview.Selection.area(KeyboardPreview.AREA_TOOLBAR);
                } else {
                    sel = KeyboardPreview.Selection.tool(arg);
                }
            }
        } else {
            int n;
            try {
                n = Integer.parseInt(arg);
            } catch (NumberFormatException e) {
                return;
            }
            if ("a".equals(kind)) {
                editorPreview.setLayer(KeyboardPreview.LAYER_AREA);
                sel = KeyboardPreview.Selection.area(n);
            } else if ("t".equals(kind)) {   // 글자 키 창: 그 자판으로 바꿔 연다 (탭 버튼도 맞춘다)
                if (n < 0 || n >= KeyboardPreview.tabNames().length) return;
                showEditorTab(n);
                editorPreview.setLayer(KeyboardPreview.LAYER_AREA);
                sel = KeyboardPreview.Selection.area(KeyboardPreview.AREA_LETTERS);
            } else if ("b".equals(kind)) {
                editorPreview.setLayer(KeyboardPreview.LAYER_BOARD);
                sel = KeyboardPreview.Selection.board();
            } else {
                showEditorTab(KeyboardPreview.TAB_KOREAN);
                com.alternative_studios.newswipe.keyboard.Key k = firstKey(editorPreview.layout(), n);
                if (k == null) return;
                editorPreview.setLayer(KeyboardPreview.LAYER_KEY);
                sel = KeyboardPreview.Selection.key(k, KeyboardPreview.TAB_KOREAN);
            }
        }
        onPreviewTap(sel);
        if (!panelOpen() || label.equals(parts[3]) || label.equals(parts[3] + L.t(" 설정", " settings"))) return;   // 창 자체를 찾았으면 창만 연다
        // 창이 올라온 뒤에 찾는다 (배치가 끝나야 위치를 안다).
        sheet.scroll().postDelayed(() -> highlightItem(sheet.scroll(), sheet.body(), label), 450);
    }

    /** 미리보기의 자판을 tab으로 바꾼다 (위의 탭 버튼도 맞춘다). */
    private void showEditorTab(int tab) {
        if (editorPreview.tab() == tab) return;
        if (editorTabs != null && tab < editorTabs.getChildCount()) editorTabs.getChildAt(tab).performClick();
        if (editorPreview.tab() != tab) editorPreview.setTab(tab);
    }

    /** 화면 하나를 보이지 않는 곳에 만들어 항목 이름을 모으고 버린다. 진짜 화면이 쓰는 값은 건드리지 않게 되돌려 놓는다. */
    private void harvestScreen(String id, java.util.List<String[]> out) {
        LinearLayout savedList = list;
        ExpressiveSwitch savedClipSwitch = clipImagesSwitch;
        LinearLayout scratch = new LinearLayout(this);
        scratch.setOrientation(LinearLayout.VERTICAL);
        harvesting = true;
        list = scratch;
        try {
            buildScreen(id);
            collectLabels(id, scratch, out, new View[1], new String[1][]);
        } catch (RuntimeException ignored) {
            // 한 화면을 못 만들어도 나머지는 계속 모은다.
        } finally {
            harvesting = false;
            list = savedList;
            clipImagesSwitch = savedClipSwitch;
            scratch.removeAllViews();
        }
    }

    /**
     * 만들어 둔 화면의 글자를 위에서 아래로 훑는다. 크기와 색으로 구분한다:
     * 15~18sp = 항목 이름(스위치·슬라이더·버튼), 14sp 강조색 = 소제목(검색에 넣지 않는다), 그 항목 이름 바로 아래(같은 칸)의 작은 글자 = 설명.
     * 슬라이더 값(예: "12ms")이나 화면 아래 안내문은 모으지 않는다.
     */
    private void collectLabels(String id, View v, java.util.List<String[]> out, View[] lastParent, String[][] last) {
        if (v.getTag() instanceof ProfilePartTag && v instanceof ViewGroup && !id.startsWith(PROFILE_PART)) {
            // 가로 모드·폴더블 내부 화면의 설정 창: 창 자체와 그 안의 항목을 그 창의 id로 모은다.
            ProfilePartTag t = (ProfilePartTag) v.getTag();
            String partId = PROFILE_PART + id + ":" + t.area + ":" + t.name;
            out.add(new String[]{partId, t.name.endsWith(L.t(" 설정", " settings")) ? t.name : t.name + L.t(" 설정", " settings"), ""});
            last[0] = null;
            lastParent[0] = null;
            ViewGroup g = (ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) collectLabels(partId, g.getChildAt(i), out, lastParent, last);
            last[0] = null;
            lastParent[0] = null;
            return;
        }
        if (v instanceof TextView) {
            TextView t = (TextView) v;
            String text = t.getText().toString().trim();
            if (!text.isEmpty()) {
                float px = t.getTextSize();
                boolean label = px >= spPx(15) - 0.5f && px <= spPx(18) + 0.5f;
                boolean heading = !label && Math.abs(px - spPx(14)) < 0.5f && t.getCurrentTextColor() == accentColor;
                if (heading) {
                    // 섹션 이름(소제목)은 검색에 넣지 않는다. 그 아래 항목들이 각자 검색된다.
                    last[0] = null;
                    lastParent[0] = null;
                } else if (label || heading) {
                    last[0] = new String[]{id, singleLine(text), ""};
                    lastParent[0] = (View) t.getParent();
                    out.add(last[0]);
                } else if (last[0] != null && t.getParent() == lastParent[0] && !(text.length() <= 8 && hasDigit(text))) {
                    last[0][2] = (last[0][2].isEmpty() ? "" : last[0][2] + " ") + singleLine(text);
                }
            }
        }
        if (v instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) collectLabels(id, g.getChildAt(i), out, lastParent, last);
        }
    }

    private float spPx(float sp) {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, sp, getResources().getDisplayMetrics());
    }

    private static boolean hasDigit(String s) {
        for (int i = 0; i < s.length(); i++) if (Character.isDigit(s.charAt(i))) return true;
        return false;
    }

    private static String singleLine(String s) {
        return s.replace('\t', ' ').replace('\n', ' ').replace('\r', ' ');
    }

    // ---- 검색 목록 파일 (앱을 업데이트하거나 다시 설치하면 다시 모은다)

    private File searchCacheFile() {
        return new File(getFilesDir(), "settings_search.tsv");
    }

    private String searchCacheStamp() {
        try {
            // lastUpdateTime은 설치·업데이트·재설치 때마다 바뀌므로, 버전 번호를 올리지 않은
            // 빌드를 덮어 설치해도 예전 메뉴로 모은 목록을 쓰지 않는다.
            android.content.pm.PackageInfo info =
                    getPackageManager().getPackageInfo(getPackageName(), 0);
            // 화면 언어가 바뀌면 이름도 바뀌므로 언어마다 따로 모은다.
            return "v" + info.getLongVersionCode() + "-t" + info.lastUpdateTime + "-" + (L.ko() ? "ko" : "en");
        } catch (PackageManager.NameNotFoundException e) {
            return "v?";
        }
    }

    private java.util.List<String[]> readSearchCache() {
        File f = searchCacheFile();
        if (!f.isFile()) return null;
        try {
            java.util.List<String> lines = java.nio.file.Files.readAllLines(f.toPath(),
                    java.nio.charset.StandardCharsets.UTF_8);
            if (lines.isEmpty() || !lines.get(0).equals(searchCacheStamp())) return null;
            java.util.List<String[]> out = new java.util.ArrayList<>();
            for (int i = 1; i < lines.size(); i++) {
                String[] cols = lines.get(i).split("\t", -1);
                if (cols.length == 3) out.add(cols);
            }
            return out.isEmpty() ? null : out;
        } catch (java.io.IOException | RuntimeException e) {
            return null;
        }
    }

    private void writeSearchCache(java.util.List<String[]> entries) {
        final String stamp = searchCacheStamp();
        final java.util.List<String[]> copy = new java.util.ArrayList<>(entries);
        final File f = searchCacheFile();
        new Thread(() -> {
            try {
                java.util.List<String> lines = new java.util.ArrayList<>();
                lines.add(stamp);
                for (String[] e : copy) lines.add(e[0] + "\t" + e[1] + "\t" + e[2]);
                java.nio.file.Files.write(f.toPath(), lines, java.nio.charset.StandardCharsets.UTF_8);
            } catch (java.io.IOException | RuntimeException ignored) {
                // 저장하지 못하면 다음에 다시 모은다.
            }
        }, "settings-search-cache").start();
    }

    /** 검색어의 낱말(띄어 쓴 것)이 모두 text에 들어 있는지. 낱말이 초성뿐이면 초성으로도 찾는다 (예: ㅈㄷ → 진동). */
    private static boolean matchesAll(String query, String text) {
        String lower = text.toLowerCase(java.util.Locale.ROOT);
        String initials = null;
        for (String token : query.toLowerCase(java.util.Locale.ROOT).split("\\s+")) {
            if (token.isEmpty() || lower.contains(token)) continue;
            if (!isInitials(token)) return false;
            if (initials == null) initials = initialsOf(lower);
            if (!initials.contains(token)) return false;
        }
        return true;
    }

    private static boolean isInitials(String s) {
        for (int i = 0; i < s.length(); i++) if (s.charAt(i) < 'ㄱ' || s.charAt(i) > 'ㅎ') return false;
        return true;
    }

    private static final String CHOSEONG = "ㄱㄲㄴㄷㄸㄹㅁㅂㅃㅅㅆㅇㅈㅉㅊㅋㅌㅍㅎ";

    /** 한글 음절은 첫 자음(초성)으로 바꾸고 나머지 글자는 그대로 둔 문자열. */
    private static String initialsOf(String s) {
        StringBuilder b = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            b.append(c >= '가' && c <= '힣' ? CHOSEONG.charAt((c - '가') / 588) : c);
        }
        return b.toString();
    }

    /**
     * 항목 이름 글자 → 그 항목 전체를 감싼 줄 (깜빡일 때 줄 전체를 칠한다).
     * 줄은 이름 글자를 자식으로 품고 있어서, 값을 그대로 넣으면 WeakHashMap의 키가 영영 풀리지 않는다. 그래서 값도 약하게 잡는다.
     */
    private final java.util.Map<TextView, java.lang.ref.WeakReference<View>> itemRow = new java.util.WeakHashMap<>();

    void markItem(TextView label, View row) {
        if (harvesting) return;   // 검색 목록을 모으려고 잠깐 만든 화면은 기억하지 않는다
        itemRow.put(label, new java.lang.ref.WeakReference<>(row));
    }

    /**
     * 검색 결과로 연 화면에서 그 항목(화면에 보이는 이름이 같은 글자)을 찾아 스크롤하고, 항목 전체를 반투명 강조색으로 두 번 깜빡인다.
     * 항목이 다른 설정 아래에 접혀 있으면 그것을 접어 둔 설정(접힌 칸 바로 위의 보이는 줄)을 대신 깜빡여, 무엇을 켜야 하는지 알려 준다.
     */
    private void highlightItem(ScrollView scroll, String label) {
        highlightItem(scroll, list, label);
    }

    /**
     * root(스크롤 안의 내용) 안에서 이름이 label인 항목을 찾아 스크롤하고 깜빡인다.
     * 설정이 꺼져 있어 화면에 만들지 않은 항목이면 그것을 켜는 설정(또는 왜 없는지 알리는 안내)을 깜빡인다.
     *
     * @return 무언가를 깜빡였는지 (root 안에 항목도, 그것을 켜는 설정도 없으면 false)
     */
    private boolean highlightItem(ScrollView scroll, ViewGroup root, String label) {
        if (isFinishing() || isDestroyed()) return false;
        // 섹션 제목과 항목의 이름이 같을 수 있다 (예: '단어 추천' 섹션의 '단어 추천' 스위치). 그때는 항목을 먼저 찾는다.
        TextView target = findLabel(root, label, true, itemRow::containsKey);
        if (target == null) target = findLabel(root, label, false, itemRow::containsKey);
        if (target == null) target = findLabel(root, label, true);
        if (target == null) target = findLabel(root, label, false);
        View hidden = target == null ? null : outermostHidden(target, root);
        View box;
        if (target == null) {
            box = absentReason(label, root);
        } else if (hidden != null) {
            box = controllerOf(hidden);
        } else {
            java.lang.ref.WeakReference<View> ref = itemRow.get(target);
            box = ref == null ? null : ref.get();
            if (box == null) box = itemBoxOf(target, root);
        }
        if (box == null) return false;
        int y = 0;
        for (View v = box; v != null && v != root; v = v.getParent() instanceof View ? (View) v.getParent() : null) {
            y += v.getTop();
        }
        scroll.smoothScrollTo(0, Math.max(0, y - Ui.dp(this, 96)));
        flash(box, root);
        return true;
    }

    /**
     * 설정이 꺼져 있어 만들지 않은 항목 이름 → 그 항목을 보이게 하는 설정 줄 (예: 분리 키보드를 켜야 생기는 항목 → 분리 키보드 스위치).
     * 검색 목록은 설정을 모두 켠 것처럼 모으므로, 이런 항목으로 들어오면 대신 이 줄을 깜빡인다.
     */
    private final java.util.Map<String, java.lang.ref.WeakReference<View>> absentItems = new java.util.HashMap<>();
    /** 이 화면의 항목을 하나도 만들지 않았을 때 그 까닭을 알리는 칸 (예: 폴더블 내부 화면이 아닐 때의 안내). 없으면 null. */
    private java.lang.ref.WeakReference<View> absentAll;

    /** labels의 항목은 지금 만들지 않았고 reason을 바꾸면 생긴다고 기억한다. */
    private void markAbsent(View reason, String... labels) {
        if (harvesting) return;
        for (String l : labels) absentItems.put(l, new java.lang.ref.WeakReference<>(reason));
    }

    /** 만들지 않은 항목 label 대신 깜빡일 root 안의 칸. 없으면 null. */
    private View absentReason(String label, View root) {
        java.lang.ref.WeakReference<View> ref = absentItems.get(label);
        View v = ref == null ? null : ref.get();
        if (v != null && v.isShown() && isInside(v, root)) return v;
        v = absentAll == null ? null : absentAll.get();
        return v != null && v.isShown() && isInside(v, root) ? v : null;
    }

    private static boolean isInside(View v, View root) {
        for (View p = v; p != null; p = p.getParent() instanceof View ? (View) p.getParent() : null) {
            if (p == root) return true;
        }
        return false;
    }

    /**
     * 항목 줄이 알려지지 않은 이름 글자(직접 만든 줄 등)의 항목 칸: 이름 글자에서 위로 올라가며, 다른 항목 이름이나 소제목을
     * 품지 않은 가장 바깥 칸. 이름 글자만 칠하면 강조가 항목의 일부(이름)에만 걸려 보이므로 이름·값·슬라이더를 함께 감싼다.
     */
    private View itemBoxOf(TextView label, View root) {
        // 항목 줄 안의 다른 글자(예: 값 줄의 지금 값)라면 그 줄을 쓴다.
        java.util.Set<View> rows = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
        for (java.lang.ref.WeakReference<View> ref : itemRow.values()) {
            View row = ref.get();
            if (row != null) rows.add(row);
        }
        for (View v = label; v != null && v != root; v = v.getParent() instanceof View ? (View) v.getParent() : null) {
            if (rows.contains(v)) return v;
        }
        View box = label;
        while (box.getParent() instanceof ViewGroup && box.getParent() != root) {
            ViewGroup parent = (ViewGroup) box.getParent();
            if (countLabels(parent, 2) > 1) break;
            box = parent;
        }
        return box;
    }

    /** v 안의 보이는 항목 이름·소제목 글자 수 (limit에 이르면 더 세지 않는다). collectLabels와 같은 기준이다. */
    private int countLabels(View v, int limit) {
        if (v.getVisibility() != View.VISIBLE) return 0;
        if (v instanceof TextView) {
            TextView t = (TextView) v;
            if (t.getText().toString().trim().isEmpty()) return 0;
            float px = t.getTextSize();
            boolean label = px >= spPx(15) - 0.5f && px <= spPx(18) + 0.5f;
            boolean heading = !label && Math.abs(px - spPx(14)) < 0.5f && t.getCurrentTextColor() == accentColor;
            return label || heading ? 1 : 0;
        }
        int n = 0;
        if (v instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) v;
            for (int i = 0; i < g.getChildCount() && n < limit; i++) n += countLabels(g.getChildAt(i), limit - n);
        }
        return n;
    }

    /**
     * 반투명 강조색을 항목 위에 두 번 깔았다 걷는다 (부드럽게 나타났다 사라진다).
     * 글자가 칸 가장자리에 붙어 보이지 않도록 항목보다 좌우·위아래로 조금 더 넓게 칠한다. 항목 안에서는 그릴 수 없는 바깥 여백이라
     * 항목이 아니라 항목을 담은 부모의 위에 그린다.
     */
    /** 마지막으로 깜빡인 칸 (시험에서 강조가 항목 전체에 걸렸는지 확인한다). */
    View lastFlashed;

    private void flash(View box, View root) {
        int w = box.getWidth(), h = box.getHeight();
        if (w == 0 || h == 0) return;
        lastFlashed = box;
        // 줄(바탕 없음)은 글자가 가장자리에 붙지 않게 좌우로 넓게 칠한다. 버튼처럼 둥근 바탕이 있는 항목은
        // 그 모양을 사방으로 똑같이 조금 키운 모양으로 칠한다 (모서리도 바탕의 둥글기에 넓힌 만큼 더해 나란하게).
        float shapeRadius = shapeRadius(box.getBackground());
        boolean shaped = shapeRadius >= 0;
        int dx = Ui.dp(this, shaped ? 4 : 10), dy = Ui.dp(this, shaped ? 4 : 2);
        final View host = box.getParent() instanceof ViewGroup ? (View) box.getParent() : box;
        android.graphics.Rect bounds = host == box
                ? new android.graphics.Rect(-dx, -dy, w + dx, h + dy)
                : new android.graphics.Rect(box.getLeft() - dx, box.getTop() - dy, box.getRight() + dx, box.getBottom() + dy);
        android.graphics.drawable.GradientDrawable d = Ui.round(accentColor, shaped ? shapeRadius + dx : Ui.dp(this, 12));
        d.setBounds(bounds);
        d.setAlpha(0);
        host.getOverlay().add(d);
        // 부모들은 자식이 자기 영역(안쪽 여백 포함) 밖에 그리지 못하게 잘라 낸다. 그대로 두면 넓힌 부분이 잘려 모서리가 각져 보이므로
        // 깜빡이는 동안만 항목을 담은 칸들(카드 안쪽)의 자르기를 끄고 끝나면 되돌린다.
        java.util.List<ViewGroup> unclipped = new java.util.ArrayList<>();
        java.util.List<boolean[]> saved = new java.util.ArrayList<>();
        for (android.view.ViewParent vp = host instanceof ViewGroup ? (ViewGroup) host : host.getParent();
                vp instanceof ViewGroup && vp != root; vp = vp.getParent()) {
            ViewGroup g = (ViewGroup) vp;
            unclipped.add(g);
            saved.add(new boolean[]{g.getClipChildren(), g.getClipToPadding()});
            g.setClipChildren(false);
            g.setClipToPadding(false);
        }
        android.animation.ValueAnimator pulse = android.animation.ValueAnimator.ofFloat(0f, 1f);
        pulse.setDuration(2000);
        pulse.addUpdateListener(a -> {
            double s = Math.sin(2 * Math.PI * (Float) a.getAnimatedValue());   // 0 → 1 → 0 → 1 → 0
            d.setAlpha((int) Math.round(120 * s * s));
        });
        pulse.addListener(new android.animation.AnimatorListenerAdapter() {
            @Override public void onAnimationEnd(android.animation.Animator animation) {
                host.getOverlay().remove(d);
                for (int i = 0; i < unclipped.size(); i++) {
                    unclipped.get(i).setClipChildren(saved.get(i)[0]);
                    unclipped.get(i).setClipToPadding(saved.get(i)[1]);
                }
            }
        });
        pulse.start();
    }

    /**
     * 칸의 바탕이 눈에 보이는 둥근 모양이면 그 모서리 반지름(px), 아니면 -1.
     * 누를 때만 보이는 물결(ripple)의 가림판은 모양으로 치지 않는다.
     */
    private static float shapeRadius(android.graphics.drawable.Drawable bg) {
        if (bg instanceof android.graphics.drawable.RippleDrawable) {
            android.graphics.drawable.RippleDrawable r = (android.graphics.drawable.RippleDrawable) bg;
            for (int i = 0; i < r.getNumberOfLayers(); i++) {
                if (r.getId(i) == android.R.id.mask) continue;
                float radius = shapeRadius(r.getDrawable(i));
                if (radius >= 0) return radius;
            }
            return -1;
        }
        if (bg instanceof android.graphics.drawable.GradientDrawable) {
            return Math.max(0, ((android.graphics.drawable.GradientDrawable) bg).getCornerRadius());
        }
        return -1;
    }

    /** label에서 위로 올라가며 만나는 접힌(GONE) 칸 중 가장 바깥의 것. 접힌 칸이 없으면 null. */
    private View outermostHidden(View label, View root) {
        View hidden = null;
        for (View v = label; v != null && v != root; v = v.getParent() instanceof View ? (View) v.getParent() : null) {
            if (v.getVisibility() == View.GONE) hidden = v;
        }
        return hidden;
    }

    /**
     * 접힌 칸을 열고 닫는 설정: 같은 부모 안에서 그 칸 바로 앞에 있는 보이는 줄.
     * 앞에 보이는 줄이 없으면 바로 뒤에 있는 보이는 줄 (예: 접힌 설정 아래에 '사용자화' 버튼만 있는 경우). 둘 다 없으면 null.
     */
    private static View controllerOf(View hidden) {
        if (!(hidden.getParent() instanceof ViewGroup)) return null;
        ViewGroup parent = (ViewGroup) hidden.getParent();
        int at = parent.indexOfChild(hidden);
        for (int i = at - 1; i >= 0; i--) {
            View sibling = parent.getChildAt(i);
            if (sibling.getVisibility() == View.VISIBLE && sibling.getHeight() > 2) return sibling;
        }
        for (int i = at + 1; i < parent.getChildCount(); i++) {
            View sibling = parent.getChildAt(i);
            if (sibling.getVisibility() == View.VISIBLE && sibling.getHeight() > 2) return sibling;
        }
        return null;
    }

    private static TextView findLabel(View v, String label, boolean shownOnly) {
        return findLabel(v, label, shownOnly, t -> true);
    }

    /** 이름이 label이고 accept를 통과하는 첫 글자 뷰. */
    private static TextView findLabel(View v, String label, boolean shownOnly, java.util.function.Predicate<TextView> accept) {
        if (v instanceof TextView && (!shownOnly || v.isShown()) && label.contentEquals(((TextView) v).getText())
                && accept.test((TextView) v)) {
            return (TextView) v;
        }
        if (v instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) {
                TextView found = findLabel(g.getChildAt(i), label, shownOnly, accept);
                if (found != null) return found;
            }
        }
        return null;
    }

    private void openSection(String id, String highlight) {
        if (LANGUAGES.equals(id)) {
            openSection(id);
            return;
        }
        Intent i = new Intent(this, SettingsActivity.class);
        if (id.startsWith(EDITOR_PART)) i.putExtra(EXTRA_SECTION, KEYBOARD).putExtra(EXTRA_TARGET, id);
        else if (id.startsWith(PROFILE_PART)) i.putExtra(EXTRA_SECTION, synonymScreen(id)).putExtra(EXTRA_TARGET, id);
        else i.putExtra(EXTRA_SECTION, id);
        if (highlight != null) i.putExtra(EXTRA_HIGHLIGHT, highlight);
        startActivity(i);
    }

    // ---------------------------------------------------------------- 키보드 사용자화

    /** 설정 설명에서 '미리보기에서 키를 눌러 바꾼다'를 알리는 말머리. */

    /** 키보드 사용자화 화면 아래에 붙는 미리보기와, 고른 키를 편집하는 칸. 다른 화면에서는 null이다. */
    private KeyboardPreview editorPreview;
    private KeyEditPanel keyPanel;
    /** 모든 설정 목록. */
    private LinearLayout overviewBox;
    /** 설정 창: 목록 위에 겹쳐 아래에서 올라오는 시트 (영역·키·키보드 전체 설정). */
    private EditorSheet sheet;
    /** 편집 칸을 연 동안 설정이 바뀌어, 닫을 때 모든 설정 목록을 다시 만들어야 하는지. */
    private boolean overviewStale;
    /** 미리보기 손잡이로도 바뀌는 크기 슬라이더: 설정 키 → 값을 다시 읽어 맞추는 일들. */
    private final java.util.Map<String, java.util.List<Runnable>> sizeSliders = new java.util.HashMap<>();
    private android.content.SharedPreferences.OnSharedPreferenceChangeListener editorPrefListener;
    private final Runnable editorRefresh = this::onEditorPrefsChanged;
    /** Android 13+: 편집 칸이 열린 동안 뒤로 가기로 칸을 닫는다. */
    private Object panelBackCallback;


    /**
     * 키보드 사용자화: 화면 아래에 실제 키보드와 같은 미리보기를 붙이고(키보드·영역·키 세 페이지), 위쪽 목록에는 어느 영역·키
     * 창에도 없는 키보드 전체 설정을 둔다. 미리보기에서 영역이나 키를 누르면 목록 위로 그 설정 창(시트)이 올라온다.
     */
    private void buildKeyboardEditor() {
        if (!harvesting) {
            editorPreview = new KeyboardPreview(this, prefs, new KeyboardPreview.Listener() {
                @Override
                public void onTap(KeyboardPreview.Selection s) {
                    onPreviewTap(s);
                }

                @Override
                public void onSizeChanged(String prefKey) {
                    syncSizeSliders(prefKey);
                }

                @Override
                public void onLayerChanged(int layer) {
                    closeKeyPanel();   // 다른 층으로 넘기면 고른 것이 풀린다
                    pageDots.setPage(layer);
                    pageDots.announce(layer);
                }
            });
            keyPanel = new KeyEditPanel(this, prefs, new KeyEditPanel.Host() {
                @Override
                public com.alternative_studios.newswipe.keyboard.Key refreshNow() {
                    editorPreview.refresh();
                    return editorPreview.selectedKey();
                }

                @Override
                public void close() {
                    closeKeyPanel();
                }

                @Override
                public void onSizeChanged(String prefKey) {
                    syncSizeSliders(prefKey);
                }

            });
            // 미리보기 바로 위: 어느 자판을 보며 편집할지.
            LinearLayout tabs = new LinearLayout(this);
            tabs.setOrientation(LinearLayout.VERTICAL);
            int pad = Ui.dp(this, 16);
            tabs.setPadding(pad, Ui.dp(this, 2), pad, Ui.dp(this, 6));
            tabs.setBackgroundColor(AppTheme.of(this).bg);
            tabs.addView(editorTabs = Ui.choiceRow(this, KeyboardPreview.tabNames(), 0, accentColor, onAccentColor,
                    textColor, hintColor, 0, i -> {
                        // 영역·키보드 전체의 창은 다른 자판에서도 연 채로 둔다 (글자 키 창은 그 자판의 항목으로 바뀐다).
                        // 키는 자판마다 달라 창을 닫는다.
                        KeyboardPreview.Selection s = editorPreview.selection();
                        boolean keep = panelOpen() && s != null
                                && (s.kind == KeyboardPreview.SEL_AREA || s.kind == KeyboardPreview.SEL_BOARD);
                        if (!keep) closeKeyPanel();
                        editorPreview.setTab(i);
                        if (keep) showSelection(editorPreview.selection());
                    }));
            pinnedBottom.addView(tabs, matchWrap());
            pageDots = new PageDots(this, pageNames(), pageDescs(), AppTheme.of(this), editorPreview::setLayer);
            pageDots.setPage(editorPreview.layer());
            pinnedBottom.addView(pageDots, matchWrap());
            pinnedBottom.addView(editorPreview, matchWrap());
            pinnedBottom.setVisibility(View.VISIBLE);

            buildSheet();

            // 목록의 스위치·슬라이더나 다른 편집 화면에서 값을 바꾸면 미리보기를 다시 그린다 (한 프레임에 한 번).
            editorUndo = new EditorUndo(prefs, uiHandler, this::updateUndoButton);
            EditorUndo.markRoot();
            updateUndoButton();
            editorPrefListener = (sp, key) -> {
                editorUndo.record(key);
                uiHandler.removeCallbacks(editorRefresh);
                uiHandler.post(editorRefresh);
                if (panelOpen()) overviewStale = true;
            };
            prefs.raw().registerOnSharedPreferenceChangeListener(editorPrefListener);
        }
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        list.addView(box, matchWrap());
        if (!harvesting) overviewBox = box;
        fillOverview(box);
    }

    /** 키보드 사용자화의 기본 목록을 box에 만든다 (가로 모드, 폴더블 내부 화면 별도 키보드). */
    private void fillOverview(LinearLayout box) {
        LinearLayout saved = list;
        list = box;
        try {
            // 여기에는 키보드 전체에 걸리고 어느 영역·키 창에도 없는 것만 둔다. 나머지는 미리보기에서 영역이나 키를 눌러 바꾼다.
            // 키 모양·분리 키보드는 '키보드 전체 설정' 창(미리보기의 키보드 층)에 있다.
            // 숫자 줄은 꺼 두어도 미리보기에 점선 빈 칸이 남아, 그것을 눌러 다시 켠다.
            LinearLayout landscape = section(L.t("가로 모드", "Landscape"));
            landscape.addView(button(L.t("가로 모드 키보드 사용자화", "Customize landscape keyboard"), v -> openSection(LANDSCAPE_AREA)));
            landscape.addView(note(L.t("화면을 가로로 돌렸을 때의 자판 모양을 따로 정합니다.", "Sets a separate keyboard look for when the screen is rotated sideways.")));
            addLargeLayoutSection();
            if (!harvesting) list.addView(editorResetButton());
        } finally {
            list = saved;
        }
    }

    /** 키보드 사용자화 목록의 '폴더블 내부 화면 별도 키보드' 섹션: 켜면 폴더블 내부 화면 키보드 사용자화로 가는 버튼이 보인다. */
    private void addLargeLayoutSection() {
        LinearLayout large = section(L.t("폴더블 설정", "Foldable"));
        View largeGroup = subGroup(button(L.t("폴더블 내부 화면 키보드 사용자화", "Customize inner screen keyboard"), v -> openSection(LARGE_AREA)));
        large.addView(toggle(L.t("폴더블 내부 화면 별도 키보드", "Separate inner screen keyboard"),
                L.t("폴더블 기기의 내부 화면에서 키보드의 모양을 따로 정합니다.", "Sets a separate keyboard look for the inner screen of foldable devices."),
                Prefs.LARGE_LAYOUT, prefs.largeLayout(), largeGroup));
        large.addView(largeGroup);
    }

    /**
     * 두 묶음(자음·모음 쪽, 왼쪽·오른쪽 묶음)의 폭·가로 위치 슬라이더 (5단위). 다른 묶음은 지금 자리에 둔 채 두 묶음 사이의
     * 빈 자리 안에서만 움직인다: 다른 묶음에 닿으면 슬라이더가 더 나아가지 않는다 (owner.fitBlockValue).
     * 미리보기에서 묶음 가장자리의 손잡이를 끌면 슬라이더도 따라 움직인다. after는 값을 바꾼 뒤 그 키로 할 일.
     */
    private View blockSlider(Prefs owner, String label, String key, java.util.function.IntSupplier current, int min, int max,
                             String unit, java.util.function.Consumer<String> after) {
        SettingsWidgets.Slider[] ref = new SettingsWidgets.Slider[1];
        SettingsWidgets.Slider sl = widgets.slider(label, current.getAsInt(), min, max, 5, unit, val -> {
            // 폭을 바꾸기 전에, 예전 방식에서 옮겨 읽고 있던 가로 위치를 새 방식으로 저장해 둔다 (안쪽 경계가 그 자리에 남게).
            String posKey = owner.pairedPosKey(key);
            if (posKey != null && !prefs.raw().contains(posKey)) {
                prefs.raw().edit().putInt(posKey, posKey.equals(owner.balancedConsonantPosKey())
                        ? owner.balancedConsonantPos() : owner.balancedVowelPos()).apply();
            }
            int fit = owner.fitBlockValue(key, current.getAsInt(), val);   // 저장하기 전의 값에서 출발
            prefs.raw().edit().putInt(key, fit).apply();
            if (fit != val && ref[0] != null) ref[0].set(fit);
            after.accept(key);
        });
        ref[0] = sl;
        if (!harvesting) sliderSink.computeIfAbsent(key, k -> new java.util.ArrayList<>()).add(() -> sl.set(current.getAsInt()));
        return sl.view;
    }

    /** 키 설정 창(열려 있으면)의 같은 값 슬라이더를 맞춘다. */
    private void syncKeyPanel(String key) {
        if (keyPanel != null) keyPanel.syncSize(key);
    }

    /** 손잡이로도 바뀌는 크기 슬라이더. 손잡이를 끌면 syncSizeSliders가 값을 다시 읽어 맞춘다. */
    private View sizeSlider(String label, String key, java.util.function.IntSupplier current, int min, int max, int step,
                            String unit) {
        SettingsWidgets.Slider sl = sliderRow(label, key, current.getAsInt(), min, max, step, unit, () -> {
            if (keyPanel != null) keyPanel.syncSize(key);
        });
        if (!harvesting) {
            sliderSink.computeIfAbsent(key, k -> new java.util.ArrayList<>()).add(() -> sl.set(current.getAsInt()));
        }
        return sl.view;
    }

    // ---- 되돌리기: 키보드 사용자화에서 마지막으로 바꾼 설정을 원래대로 (기록은 EditorUndo)

    private EditorUndo editorUndo;
    private View undoButton;

    /** 머리글의 되돌리기 버튼: 마지막 단계를 되돌리고 미리보기·열린 창·목록을 되돌린 값으로 다시 그린다. */
    private void undoLast() {
        if (editorUndo == null || !editorUndo.undo()) return;
        if (Build.VERSION.SDK_INT >= 30) undoButton.performHapticFeedback(android.view.HapticFeedbackConstants.CONFIRM);
        Toast.makeText(this, L.t("마지막 변경을 되돌렸습니다", "Undid the last change"), Toast.LENGTH_SHORT).show();
        if (editorPreview == null) {
            // 가로 모드·폴더블 내부 화면 키보드 사용자화: 목록·슬라이더·미리보기를 되돌린 값으로 다시 만든다.
            recreate();
            return;
        }
        editorPreview.refresh();
        overviewStale = true;
        if (panelOpen()) showSelection(editorPreview.selection());
        else rebuildOverviewIfStale();
    }

    private void updateUndoButton() {
        if (undoButton == null) return;
        boolean any = editorUndo != null && editorUndo.canUndo();
        undoButton.setEnabled(any);
        undoButton.setAlpha(any ? 1f : 0.38f);
    }

    private void syncSizeSliders(String key) {
        java.util.List<Runnable> syncs = sizeSliders.get(key);
        if (syncs != null) for (Runnable r : syncs) r.run();
        java.util.List<Runnable> area = areaSliders.get(key);
        if (area != null) for (Runnable r : area) r.run();
        if (keyPanel != null) keyPanel.syncSize(key);
    }

    private void onEditorPrefsChanged() {
        if (editorPreview == null || isFinishing() || isDestroyed()) return;
        editorPreview.refresh();
        // 고른 키가 없어졌으면(기능키를 끄거나 숫자 줄을 껐다) 편집 칸을 닫는다.
        if (panelOpen() && editorPreview.selection() == null) closeKeyPanel();
    }

    /** 다른 화면에서 돌아왔을 때: 미리보기와 열려 있는 편집 칸을 지금 설정으로 다시 만든다. */
    private void refreshEditorPanel() {
        if (editorPreview == null) return;
        editorPreview.refresh();
        if (!panelOpen()) return;
        showSelection(editorPreview.selection());
    }

    private boolean panelOpen() {
        return sheet != null && sheet.isOpen();
    }

    /** 설정 창을 만든다: 키 창(KeyEditPanel)과 그 아래 영역·여백 창(areaBox)을 담는다. */
    private void buildSheet() {
        sheet = new EditorSheet(this, middle, this::closeKeyPanel);
        areaNoteBox = widgets.card(sheet.body());
        sheet.body().removeView(areaNoteBox);
        sheet.body().addView(areaNoteBox, 0, areaNoteBox.getLayoutParams());
        areaNoteBox.setVisibility(View.GONE);
        sheet.body().addView(keyPanel.view(), matchWrap());
        areaBox = new LinearLayout(this);
        areaBox.setOrientation(LinearLayout.VERTICAL);
        areaBox.setVisibility(View.GONE);
        sheet.body().addView(areaBox, matchWrap());
    }

    private void openSheet() {
        sheet.open();
        setPanelBack(true);
    }

    /** 미리보기에서 키·버튼·도구 막대를 눌렀다: 그것을 편집하는 창을 연다. 빈 곳이면 닫는다. */
    private void onPreviewTap(KeyboardPreview.Selection s) {
        if (s == null) {
            closeKeyPanel();
            return;
        }
        if (panelOpen() && s.same(editorPreview.selection())) return;
        editorPreview.select(s);
        KeyboardPreview.Selection now = editorPreview.selection();
        if (now == null) return;   // 지금 자판에 없는 키
        showSelection(now);
        sheet.scroll().scrollTo(0, 0);
        if (panelOpen()) {
            // 이미 열려 있으면 내용만 바꾼다: 짧게 흐렸다가 또렷해져 다른 것으로 바뀐 것을 알린다.
            View body = sheet.scroll().getChildAt(0);
            body.animate().cancel();
            body.setAlpha(0.3f);
            body.animate().alpha(1f).setDuration(160).start();
            return;
        }
        openSheet();
    }

    /**
     * 고른 것의 편집 내용을 창에 채운다. 키·도구 막대 버튼·도구 막대는 키 편집 칸(KeyEditPanel)이, 영역은 그 영역 전체에
     * 해당하는 설정(areaBox)이 맡는다. 도구 막대 영역은 도구 막대 칸(KeyEditPanel)이 맡는다.
     */
    private void showSelection(KeyboardPreview.Selection s) {
        if (s == null) {
            closeKeyPanel();
            return;
        }
        areaNoteBox.removeAllViews();   // 영역 창이 다시 채울 때만 안내가 있다
        areaNoteBox.setVisibility(View.GONE);
        boolean area = s.kind == KeyboardPreview.SEL_AREA;
        int areaId = area ? s.area() : -1;
        // 키보드 층(키보드 전체)은 여백 슬라이더 창.
        boolean board = s.kind == KeyboardPreview.SEL_BOARD;
        if (board) {
            keyPanel.view().setVisibility(View.GONE);
            fillAreaBox(() -> {
                buildBoard();
                addAreaReset(-1);
            });
            sheet.setTitle(L.t("키보드 전체 설정", "Whole keyboard settings"));
            return;
        }
        boolean panel = !area || areaId == KeyboardPreview.AREA_TOOLBAR;
        if (panel) keyPanel.show(area ? KeyboardPreview.Selection.toolbar() : s, editorPreview.selectedKey());
        keyPanel.view().setVisibility(panel ? View.VISIBLE : View.GONE);
        fillArea(areaId);
        sheet.setTitle((area ? KeyboardPreview.areaName(areaId, editorPreview.tab()) : keyPanel.title()) + L.t(" 설정", " settings"));
    }

    /** 영역 편집 창의 크기 슬라이더 (손잡이를 끌면 같이 맞춘다). 창을 채울 때마다 새로 모은다. */
    private final java.util.Map<String, java.util.List<Runnable>> areaSliders = new java.util.HashMap<>();
    /** sizeSlider가 맞출 일을 넣을 곳: 보통은 목록(sizeSliders), 영역 창을 채우는 동안은 areaSliders. */
    private java.util.Map<String, java.util.List<Runnable>> sliderSink = sizeSliders;
    private LinearLayout areaBox;

    /** 영역 편집 내용을 areaBox에 만든다 (영역이 아니면 비운다). 목록을 만드는 조각을 그대로 써서 이름·설명이 같다. */
    private void fillArea(int areaId) {
        if (areaId < 0) {
            areaBox.removeAllViews();
            areaSliders.clear();
            areaBox.setVisibility(View.GONE);
            return;
        }
        letterTab = editorPreview.tab();
        toolbarSpaceNote = null;
        fillAreaBox(() -> {
            buildArea(areaId);
            addAreaReset(areaId);
            if (areaId == KeyboardPreview.AREA_TOOLBAR) addToolbarSpaceNote();
        });
    }

    /** 도구 막대 창 맨 아래(되돌리기 아래)의 '도구 막대 공간을 완전히 없애려면…' 안내. 도구 막대 창이 아니면 null. */
    private TextView toolbarSpaceNote;

    /** 도구 막대를 꺼 두었는데 단어 추천이 켜져 있어 키보드에 도구 막대 자리(빈 공간)가 남아 있는지. */
    private boolean toolbarSpaceLeft() {
        return !prefs.toolbar() && prefs.suggestWords();
    }

    /**
     * 도구 막대를 꺼도 단어 추천이 켜져 있으면 추천이 뜰 자리가 빈 채로 남는다. 그 자리를 없애는 법을 알리고,
     * 강조색 '단어 추천'을 누르면 검색으로 찾은 것처럼 '추천, 학습 및 완성' 메뉴를 열어 그 스위치를 깜빡인다.
     */
    private void addToolbarSpaceNote() {
        String link = L.t("단어 추천", "Word suggestions");
        String text = L.t("도구 막대 공간을 완전히 없애려면 '" + sectionTitle("words") + "'에서 " + link + "을 끄세요.",
                "To remove the toolbar space completely, turn off " + link + " in '" + sectionTitle("words") + "'.");
        android.text.SpannableString span = new android.text.SpannableString(text);
        int start = text.lastIndexOf(link);
        span.setSpan(new android.text.style.ClickableSpan() {
            @Override
            public void onClick(View widget) {
                openSection("words", link);
            }

            @Override
            public void updateDrawState(android.text.TextPaint ds) {
                ds.setColor(accentColor);
                ds.setUnderlineText(false);
            }
        }, start, start + link.length(), android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        TextView t = note("");
        t.setText(span);
        t.setMovementMethod(android.text.method.LinkMovementMethod.getInstance());
        t.setHighlightColor(android.graphics.Color.TRANSPARENT);
        t.setVisibility(toolbarSpaceLeft() ? View.VISIBLE : View.GONE);
        list.addView(t, matchWrap());
        toolbarSpaceNote = t;
    }

    /** 영역·키보드 전체 설정 창 맨 아래의 '기본값으로 되돌리기' (그 창이 담당하는 값만). area -1은 키보드 전체 여백. */
    private void addAreaReset(int area) {
        String name = area < 0 ? L.t("키보드 전체 설정", "Whole keyboard settings") : KeyboardPreview.areaName(area, letterTab) + L.t(" 설정", " settings");
        int tab = letterTab;
        list.addView(ResetDialog.button(this, name, () -> {
            prefs.resetEditorScope(area < 0 ? Prefs.SCOPE_BOARD
                    : area == KeyboardPreview.AREA_MIDDLE ? Prefs.SCOPE_MIDDLE : Prefs.AREA_SCOPES[area]);
            // 글자 키 창은 세 자판이 함께 쓰는 값과 그 자판에만 있는 값을 되돌린다.
            if (area == KeyboardPreview.AREA_LETTERS && tab == KeyboardPreview.TAB_KOREAN) {
                prefs.resetEditorScope(Prefs.SCOPE_LETTERS_KO);
            } else if (area == KeyboardPreview.AREA_LETTERS && tab == KeyboardPreview.TAB_ENGLISH) {
                prefs.resetEditorScope(Prefs.SCOPE_LETTERS_EN);
            }
            editorPreview.refresh();
            showSelection(editorPreview.selection());   // 창의 슬라이더·스위치를 되돌린 값으로 다시 만든다
        }));
    }

    /** areaBox를 비우고 build로 다시 채운다 (목록을 만드는 조각을 그대로 쓰도록 list를 잠깐 areaBox로 돌린다). */
    private void fillAreaBox(Runnable build) {
        areaBox.removeAllViews();
        areaSliders.clear();
        areaBox.setVisibility(View.VISIBLE);
        LinearLayout saved = list;
        list = areaBox;
        sliderSink = areaSliders;
        try {
            build.run();
        } finally {
            list = saved;
            sliderSink = sizeSliders;
        }
    }

    /** 키보드 전체 설정 창: 키 모양, 맨 아래에 키보드 여백. */
    private void buildBoard() {
        buildLook();
        buildPad(-1);
    }

    /**
     * 여백 창: area가 -1이면 키보드 전체 둘레의 여백, 아니면 그 영역의 여백 (키보드 전체 여백 안쪽에 더한다).
     * 키보드 전체 여백은 미리보기의 손잡이를 끌면 이 슬라이더도 같이 움직이고, 영역마다의 여백은 이 슬라이더로만 바꾼다.
     */
    private void buildPad(int area) {
        if (area < 0) {
            LinearLayout c = section(L.t("키보드 여백", "Keyboard padding"));
            c.addView(sizeSlider(L.t("왼쪽 여백", "Left padding"), Prefs.PAD_LEFT, prefs::padLeftDp, 0, 24, 1, "dp"));
            c.addView(sizeSlider(L.t("오른쪽 여백", "Right padding"), Prefs.PAD_RIGHT, prefs::padRightDp, 0, 24, 1, "dp"));
            c.addView(sizeSlider(L.t("위쪽 여백", "Top padding"), Prefs.PAD_TOP, prefs::padTopDp, 0, 24, 1, "dp"));
            c.addView(sizeSlider(L.t("아래쪽 여백", "Bottom padding"), Prefs.PAD_BOTTOM, prefs::padBottomDp, 0, 48, 1, "dp"));
            c.addView(note(L.t("키보드 전체 둘레의 여백입니다. 영역마다 따로 주는 여백은 '영역 설정' 페이지에서 그 영역을 눌러 "
                    + "창 맨 아래의 '여백'에서 정합니다.", "Padding around the whole keyboard. To set padding for each area, tap the area on the 'Areas' page and use 'Padding' at the bottom of its panel.")));
            return;
        }
        LinearLayout c = section(L.t("여백", "Padding"));
        c.addView(areaPadSlider(L.t("왼쪽 여백", "Left padding"), area, Prefs.SIDE_LEFT));
        c.addView(areaPadSlider(L.t("오른쪽 여백", "Right padding"), area, Prefs.SIDE_RIGHT));
        c.addView(areaPadSlider(L.t("위쪽 여백", "Top padding"), area, Prefs.SIDE_TOP));
        c.addView(areaPadSlider(L.t("아래쪽 여백", "Bottom padding"), area, Prefs.SIDE_BOTTOM));
    }

    private View areaPadSlider(String label, int area, int side) {
        return sizeSlider(label, Prefs.areaPadKey(area, side), () -> prefs.areaPadDp(area, side), 0, Prefs.AREA_PAD_MAX, 1, "dp");
    }

    /** 영역 설정 창의 분리된 안내 칸: 하나하나의 설정은 키 설정 페이지에 있다. pick은 조사가 붙은 말 ("키를", "버튼을"). */
    private void addKeyPageNote(String what, String pick) {
        // 설정 창 맨 위(areaNoteBox)에 둔다.
        areaNoteBox.removeAllViews();
        areaNoteBox.addView(note(L.t(what + " 하나하나의 설정은 아래쪽 미리보기의 오른쪽 '키 설정' 페이지에서 원하는 " + pick
                + " 선택해 바꿀 수 있습니다.", "To change each of the " + what.toLowerCase(java.util.Locale.ROOT)
                + " one by one, select it on the 'Keys' page on the right of the preview below.")));
        areaNoteBox.setVisibility(View.VISIBLE);
    }

    /** 설정 창 맨 위의 안내 카드 ('키 하나하나의 설정은 … 바꿀 수 있습니다.'). 영역 창에서만 보인다. */
    private LinearLayout areaNoteBox;

    private void buildArea(int areaId) {
        switch (areaId) {
            case KeyboardPreview.AREA_TOOLBAR:
                // 도구 막대 칸은 위의 KeyEditPanel이 맡는다. 클립보드·이모지는 메인 화면의 '클립보드 및 이모지' 메뉴에 있다.
                // 도구 막대 표시를 끄면 안내와 여백도 감춘다 (설정 칸은 KeyEditPanel이 같이 감춘다).
                LinearLayout tb = new LinearLayout(this);
                tb.setOrientation(LinearLayout.VERTICAL);
                tb.setVisibility(prefs.toolbar() ? View.VISIBLE : View.GONE);
                // 도구 막대 버튼 안내(설정 창 맨 위)도 도구 막대를 껐을 때는 감춘다.
                keyPanel.setOnToolbarShown(on -> {
                    Ui.setVisibleAnimated(tb, on);
                    Ui.setVisibleAnimated(areaNoteBox, on);
                    if (toolbarSpaceNote != null) Ui.setVisibleAnimated(toolbarSpaceNote, toolbarSpaceLeft());
                });
                list.addView(tb, matchWrap());
                LinearLayout savedList = list;
                list = tb;
                try {
                    if (prefs.portraitSplit()) buildProfileArea(Prefs.PROFILE_PORTRAIT, KeyboardPreview.AREA_TOOLBAR, true);
                    addKeyPageNote(L.t("도구 막대 버튼", "Toolbar buttons"), "버튼을");
                    if (!prefs.toolbar()) areaNoteBox.setVisibility(View.GONE);
                    buildPad(areaId);
                } finally {
                    list = savedList;
                }
                break;
            case KeyboardPreview.AREA_NUMBER: {
                LinearLayout c = section(L.t("모양", "Look"));
                // 숫자 줄을 끄면 나머지 항목은 쓸 일이 없어 감춘다.
                LinearLayout rest = new LinearLayout(this);
                rest.setOrientation(LinearLayout.VERTICAL);
                rest.addView(sizeSlider(L.t("숫자 줄 높이", "Number row height"), Prefs.NUMBER_ROW_HEIGHT, prefs::numberRowHeight,
                        Prefs.NUMBER_ROW_HEIGHT_MIN, Prefs.NUMBER_ROW_HEIGHT_MAX, 5, "%"));
                rest.addView(numberHintToggle());
                // 여백 섹션도 숫자 줄을 켰을 때만 보인다.
                LinearLayout padBox = new LinearLayout(this);
                padBox.setOrientation(LinearLayout.VERTICAL);
                c.addView(toggle(L.t("숫자 줄 표시", "Show number row"), L.t("자판 위에 1~0 숫자 줄을 보여 줍니다.", "Shows a 1–0 number row above the keyboard."), Prefs.NUMBER_ROW, prefs.numberRow(), rest, padBox));
                c.addView(rest, matchWrap());
                list.addView(padBox, matchWrap());
                LinearLayout saved = list;
                list = padBox;
                try {
                    addKeyPageNote(L.t("숫자 줄 키", "Number row keys"), "키를");
                    buildPad(areaId);
                } finally {
                    list = saved;
                }
                break;
            }
            case KeyboardPreview.AREA_LETTERS: {
                // 한글·영어·기호 자판의 글자 키는 따로 다룬다: 그 자판에만 있는 항목(한글 자판 배열·연속 탭, 영어 자동 대문자 등)은
                // 그 자판의 창에만 둔다. 높이·길게 누르기·밀기·여백은 세 자판이 함께 쓴다.
                boolean ko = letterTab == KeyboardPreview.TAB_KOREAN, en = letterTab == KeyboardPreview.TAB_ENGLISH;
                splitHolder = null;
                balancedHolder = null;
                if (ko) buildKoreanLayoutSection();
                LinearLayout size = section(L.t("모양", "Look"));
                size.addView(sizeSlider(L.t("글자 키 높이", "Letter key height"), Prefs.KEYBOARD_HEIGHT, prefs::keyboardHeight, 70, 140, 5, "%"));
                // 분리 키보드 섹션은 틀에 담아, 상세 배열에서 분리 키보드를 고르면 창을 다시 만들지 않고 그 자리에 채워 펼친다.
                if (letterTab != KeyboardPreview.TAB_SYMBOLS) {
                    splitHolder = new LinearLayout(this);
                    splitHolder.setOrientation(LinearLayout.VERTICAL);
                    list.addView(splitHolder, matchWrap());
                    fillSplitHolder();
                }
                // 자·모음 균형 배열 섹션도 같은 방식: 상세 배열에서 자·모음 균형 배열을 고르면 그 자리에 채워 펼친다 (한글 자판만).
                if (ko) {
                    balancedHolder = new LinearLayout(this);
                    balancedHolder.setOrientation(LinearLayout.VERTICAL);
                    list.addView(balancedHolder, matchWrap());
                    fillBalancedHolder();
                }
                if (ko) addKoreanInput(section(L.t("입력", "Input")));
                if (en) addEnglishInput(section(L.t("입력", "Input")));   // ⌫ 인식 범위는 ⌫ 키 설정에
                LinearLayout lp = section(L.t("길게 누르기", "Long press"));
                addLetterLongPress(lp);
                lp.addView(popupHintToggle());
                dubeolsikRow = null;
                if (en) section(L.t("한글 자모 힌트", "Korean letter hints")).addView(dubeolsikHintToggle());
                LinearLayout swipe = section(L.t("밀기", "Swipe"));
                swipe.addView(swipeDistanceSlider());
                addKeyboardSwipe(swipe);
                addKeyPageNote(KeyboardPreview.areaName(areaId, letterTab), "키를");
                buildPad(areaId);
                break;
            }
            case KeyboardPreview.AREA_MIDDLE:
                // 분리 키보드의 가운데 공간: 밀어서 커서 이동과 그 속도.
                buildProfileArea(Prefs.PROFILE_PORTRAIT, KeyboardPreview.AREA_MIDDLE, true);
                break;
            default: {
                // 기능키 전체에 걸리는 것만 둔다. 스페이스바·⌫·기호 키·🌐︎처럼 키 하나에 매인 설정은 '키' 페이지의 그 키에 있다.
                LinearLayout fn = section(L.t("모양", "Look"));
                fn.addView(sizeSlider(L.t("기능키 높이", "Function key height"), Prefs.FN_ROW_HEIGHT, prefs::fnRowHeight,
                        Prefs.FN_ROW_HEIGHT_MIN, Prefs.FN_ROW_HEIGHT_MAX, 5, "%"));
                addFunctionKeyLayout(fn);
                if (prefs.portraitSplit()) buildProfileArea(Prefs.PROFILE_PORTRAIT, KeyboardPreview.AREA_FUNCTION, true);
                LinearLayout swipe = section(L.t("길게 누르기·밀기", "Long press · Swipe"));
                swipe.addView(fnLongPressSlider());
                swipe.addView(fnSwipeDistanceSlider());
                addKeyPageNote(L.t("기능키", "Function keys"), "키를");
                buildPad(areaId);
                break;
            }
        }
    }

    /** 미리보기 위의 한글·영어·기호 탭 버튼 줄. */
    private LinearLayout editorTabs;
    /** 글자 키 창을 만들 자판 (KeyboardPreview.TAB_*): 창을 열 때 미리보기의 탭, 설정 검색 목록을 만들 때는 자판마다. */
    private int letterTab = KeyboardPreview.TAB_KOREAN;

    /** 미리보기 위의 페이지 표시 (키보드 · 영역 · 키). */
    private PageDots pageDots;
    /** 미리보기 페이지 이름 (KeyboardPreview.LAYER_* 차례). */
    private static String[] pageNames() {
        return new String[]{L.t("키보드 전체 설정", "Whole keyboard settings"), L.t("영역 설정", "Areas"), L.t("키 설정", "Keys")};
    }
    /** 미리보기 페이지 설명 (PAGE_NAMES와 같은 차례). */
    private static String[] pageDescs() {
        return new String[]{L.t("전체적인 모양을 설정합니다.", "Set the overall look."), L.t("각 영역의 모습과 기능을 설정합니다", "Set the look and actions of each area"),
            L.t("각 키의 기능과 동작을 설정합니다", "Set the actions of each key")};
    }

    /** 편집 창을 내려 닫고 목록으로 돌아간다 (창에서 바꾼 값이 있으면 다 내려간 뒤 목록을 다시 만든다). */
    private void closeKeyPanel() {
        if (editorPreview != null) editorPreview.select(null);
        if (profilePreview != null) profilePreview.select(null);
        if (!panelOpen()) return;
        setPanelBack(false);
        sheet.close(this::rebuildOverviewIfStale);
    }

    /** 편집 창에서 바꾼 값이 있으면 목록을 다시 만든다 (보던 자리는 그대로). */
    private void rebuildOverviewIfStale() {
        if (!overviewStale || overviewBox == null) return;
        overviewStale = false;
        int y = scrollView.getScrollY();
        overviewBox.removeAllViews();
        sizeSliders.clear();
        fillOverview(overviewBox);
        scrollView.post(() -> scrollView.scrollTo(0, y));
    }

    private void setPanelBack(boolean on) {
        if (Build.VERSION.SDK_INT < 33) return;
        android.window.OnBackInvokedDispatcher dispatcher = getOnBackInvokedDispatcher();
        if (on && panelBackCallback == null) {
            android.window.OnBackInvokedCallback cb = this::closeKeyPanel;
            panelBackCallback = cb;
            dispatcher.registerOnBackInvokedCallback(android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT, cb);
        } else if (!on && panelBackCallback != null) {
            dispatcher.unregisterOnBackInvokedCallback((android.window.OnBackInvokedCallback) panelBackCallback);
            panelBackCallback = null;
        }
    }

    /** 키보드 사용자화의 '기본값으로 되돌리기': 되돌릴 묶음을 고른다 (모두 한꺼번에 지우지 않게). */
    private View editorResetButton() {
        TextView b = ResetDialog.button(this, L.t("키보드 사용자화", "Customize keyboard"), () -> { });
        b.setOnClickListener(v -> {
            String[][] groups = Prefs.editorResetChoices();
            String[] names = new String[groups.length];
            boolean[] checked = new boolean[names.length];
            for (int i = 0; i < names.length; i++) names[i] = groups[i][1];
            android.app.AlertDialog[] dialog = new android.app.AlertDialog[1];
            dialog[0] = AppTheme.dialogBuilder(this)
                    .setTitle(L.t("기본값으로 되돌릴 설정", "Settings to reset"))
                    .setMultiChoiceItems(names, checked, (d, which, on) -> {
                        checked[which] = on;
                        boolean any = false;
                        for (boolean c : checked) any |= c;
                        dialog[0].getButton(android.app.AlertDialog.BUTTON_POSITIVE).setEnabled(any);
                    })
                    .setPositiveButton(L.t("되돌리기", "Reset"), (d, w) -> {
                        for (int i = 0; i < checked.length; i++) if (checked[i]) prefs.resetEditorScope(groups[i][0]);
                        importGeneration++;   // 뒤에 쌓인 설정 화면도 돌아오면 기본값으로 다시 그린다
                        recreate();
                        Toast.makeText(this, L.t("기본값으로 되돌렸습니다", "Restored defaults"), Toast.LENGTH_SHORT).show();
                    })
                    .setNegativeButton(L.t("취소", "Cancel"), null)
                    .show();
            dialog[0].getButton(android.app.AlertDialog.BUTTON_POSITIVE).setEnabled(false);
        });
        return b;
    }

    /** 키 모양: 글자 크기, 곡률, 키 간격, 그림자, 누름 미리보기. 글자 키 높이는 글자 키 영역 창에, 여백은 미리보기의 키보드·여백 페이지에 있다. */
    private void buildLook() {
        LinearLayout look = section(L.t("키 모양", "Key look"));
        look.addView(slider(L.t("키 글자 크기", "Key text size"), Prefs.KEY_TEXT_SIZE, prefs.keyTextSize(), 70, 140, 5, "%"));
        look.addView(slider(L.t("키 곡률", "Key corner radius"), Prefs.KEY_RADIUS, prefs.keyRadiusDp(), 0, 24, 1, "dp"));
        look.addView(slider(L.t("키 사이 좌우 간격", "Horizontal key gap"), Prefs.KEY_GAP_X, prefs.keyGapXDp(), 0, 12, 1, "dp"));
        look.addView(slider(L.t("키 사이 상하 간격", "Vertical key gap"), Prefs.KEY_GAP_Y, prefs.keyGapYDp(), 0, 20, 1, "dp"));
        View shadowStrength = subGroup(slider(L.t("그림자 세기", "Shadow strength"), Prefs.KEY_SHADOW_STRENGTH, prefs.keyShadowStrength(), 10, 100, 5, "%"));
        if (prefs.neumorphism()) {
            // Neumorphism 디자인이 키 그림자 대신 쓰이므로, 저장된 값은 그대로 두고 꺼진 모양으로 흐리게 막아 둔다.
            View row = toggle(L.t("키 그림자", "Key shadow"), L.t("Neumorphism 디자인 사용 중에는 사용할 수 없습니다.", "Not available while Neumorphism design is on."), false, on -> { });
            disableToggle(row);
            look.addView(row);
        } else {
            look.addView(toggle(L.t("키 그림자", "Key shadow"), L.t("키 아래에 그림자를 그려 입체감을 줍니다.", "Draws a shadow under the keys for depth."),
                    Prefs.KEY_SHADOW, prefs.keyShadow(), shadowStrength));
            look.addView(shadowStrength);
        }
        look.addView(toggle(L.t("키 누름 미리보기", "Key press popup"), L.t("누른 키를 말풍선으로 크게 보여 줍니다.", "Shows the pressed key enlarged in a popup."),
                Prefs.KEY_PREVIEW, prefs.keyPreview()));
    }

    /** '글자 키 힌트 표시' 스위치와, 켜져 있을 때만 보이는 '힌트 위치' 줄. 저장은 예전처럼 '없애기'(POPUP_HINT_HIDDEN)로 뒤집어 한다. */
    private View popupHintToggle() {
        SettingsWidgets.ValueRow[] pos = new SettingsWidgets.ValueRow[1];
        pos[0] = widgets.valueRow(L.t("힌트 위치", "Hint position"), Prefs.hintPositionNames()[prefs.popupHintPosition()], v ->
                pickHintPosition(p -> {
                    android.content.SharedPreferences.Editor e = prefs.raw().edit().putInt(Prefs.POPUP_HINT_POSITION, p);
                    // 두벌식 자모 힌트가 그 자리에 있으면 서로 자리를 바꾼다 (두 힌트가 겹치지 않게).
                    if (prefs.dubeolsikHintPositionRaw() == p) {
                        e.putInt(Prefs.DUBEOLSIK_HINT_POSITION, prefs.popupHintPosition());
                    }
                    e.apply();
                    pos[0].set(Prefs.hintPositionNames()[p]);
                    updateDubeolsikRow();
                    refreshLayoutPreview();
                }));
        View position = subGroup(pos[0].view);
        boolean shown = !prefs.popupHintHidden();
        position.setVisibility(shown ? View.VISIBLE : View.GONE);
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.addView(toggle(L.t("글자 키 힌트 표시", "Show letter key hints"), L.t("글자 키에 길게 눌러 입력할 문자 힌트를 작게 표시합니다.", "Shows a small hint on letter keys for the characters you can type by long press."), shown, on -> {
            prefs.raw().edit().putBoolean(Prefs.POPUP_HINT_HIDDEN, !on).apply();
            Ui.setVisibleAnimated(position, on);
            updateDubeolsikRow();
            refreshLayoutPreview();
        }), matchWrap());
        box.addView(position, matchWrap());
        return box;
    }

    /** 영어 글자 키 창의 '두벌식 자모 힌트 위치' 줄 (다른 창이면 null). 글자 키 힌트를 바꾸면 실제 자리가 바뀔 수 있어 다시 쓴다. */
    private SettingsWidgets.ValueRow dubeolsikRow;

    private void updateDubeolsikRow() {
        if (dubeolsikRow != null) dubeolsikRow.set(Prefs.hintPositionNames()[prefs.dubeolsikHintPosition()]);
    }

    /** '두벌식 자모 힌트 표시' 스위치와, 켜져 있을 때만 보이는 '힌트 위치' 줄 (영어 글자 키 창). */
    private View dubeolsikHintToggle() {
        SettingsWidgets.ValueRow[] pos = new SettingsWidgets.ValueRow[1];
        pos[0] = widgets.valueRow(L.t("힌트 위치", "Hint position"), Prefs.hintPositionNames()[prefs.dubeolsikHintPosition()], v ->
                pickDubeolsikHintPosition(p -> {
                    prefs.raw().edit().putInt(Prefs.DUBEOLSIK_HINT_POSITION, p).apply();
                    updateDubeolsikRow();
                    refreshLayoutPreview();
                }));
        dubeolsikRow = pos[0];
        View position = subGroup(pos[0].view);
        boolean shown = prefs.dubeolsikHint();
        position.setVisibility(shown ? View.VISIBLE : View.GONE);
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.addView(toggle(L.t("두벌식 자모 힌트 표시", "Show Dubeolsik hints"),
                L.t("영어 글자 키에 두벌식 자판에서 같은 자리에 있는 한글 자모(q → ㅂ, a → ㅁ)를 작게 표시합니다. "
                        + "Shift를 누르면 쌍자음·ㅒ·ㅖ로 바뀝니다.", "Shows the Korean letter in the same spot on the Dubeolsik layout (q → ㅂ, a → ㅁ) small on English letter keys. With Shift, it changes to the double consonant, ㅒ or ㅖ."), shown, on -> {
            prefs.raw().edit().putBoolean(Prefs.DUBEOLSIK_HINT, on).apply();
            Ui.setVisibleAnimated(position, on);
            refreshLayoutPreview();
        }), matchWrap());
        box.addView(position, matchWrap());
        return box;
    }

    /** 두벌식 자모 힌트의 자리를 고르는 창. 글자 키 힌트가 보이면 그 자리는 막아 두고 그 힌트 글자를 흐리게 보여 준다. */
    private void pickDubeolsikHintPosition(java.util.function.IntConsumer onPick) {
        int current = prefs.dubeolsikHintPosition();
        int blocked = prefs.popupHintHidden() ? -1 : prefs.popupHintPosition();
        android.app.AlertDialog[] dialog = new android.app.AlertDialog[1];
        HintPositionPicker picker = new HintPositionPicker(this,
                com.alternative_studios.newswipe.keyboard.KeyboardTheme.of(this), accentColor, onAccentColor,
                "r", "ㄱ", L.t("두벌식 자모 힌트 위치", "Dubeolsik hint position"), current, blocked, "4", p -> {
                    if (dialog[0] != null) dialog[0].dismiss();
                    if (p != current) onPick.accept(p);
                });
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        int pad = Ui.dp(this, 20);
        box.setPadding(pad, Ui.dp(this, 8), pad, 0);
        box.addView(picker, matchWrap());
        dialog[0] = AppTheme.dialogBuilder(this)
                .setTitle(L.t("두벌식 자모 힌트 위치", "Dubeolsik hint position"))
                .setMessage(blocked < 0 ? L.t("힌트를 표시할 자리를 누르세요.", "Tap where to show the hint.")
                        : L.t("힌트를 표시할 자리를 누르세요. 길게 눌러 입력할 문자 힌트가 있는 자리는 고를 수 없습니다.", "Tap where to show the hint. Spots with a long-press character hint can't be chosen."))
                .setView(box)
                .setNeutralButton(L.t("기본값", "Default"), (d, w) -> {
                    // 기본 자리(왼쪽 아래)가 글자 키 힌트 자리면 Prefs가 맞은편(오른쪽 위)에 그린다.
                    prefs.raw().edit().putInt(Prefs.DUBEOLSIK_HINT_POSITION, Prefs.HINT_POS_BOTTOM_LEFT).apply();
                    updateDubeolsikRow();
                    refreshLayoutPreview();
                })
                .setNegativeButton(L.t("취소", "Cancel"), null)
                .show();
    }

    /** 글자 키 힌트의 자리를 고르는 창: 키 그림에서 여덟 곳 가운데 하나를 누르면 바로 정하고 닫는다. */
    private void pickHintPosition(java.util.function.IntConsumer onPick) {
        int current = prefs.popupHintPosition();
        android.app.AlertDialog[] dialog = new android.app.AlertDialog[1];
        HintPositionPicker picker = new HintPositionPicker(this,
                com.alternative_studios.newswipe.keyboard.KeyboardTheme.of(this), accentColor, onAccentColor, current, p -> {
                    if (dialog[0] != null) dialog[0].dismiss();
                    if (p != current) onPick.accept(p);
                });
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        int pad = Ui.dp(this, 20);
        box.setPadding(pad, Ui.dp(this, 8), pad, 0);
        box.addView(picker, matchWrap());
        dialog[0] = AppTheme.dialogBuilder(this)
                .setTitle(L.t("힌트 위치", "Hint position"))
                .setMessage(L.t("힌트를 표시할 자리를 누르세요.", "Tap where to show the hint."))
                .setView(box)
                .setNeutralButton(L.t("기본값", "Default"), (d, w) -> {
                    if (current != Prefs.HINT_POS_TOP_RIGHT) onPick.accept(Prefs.HINT_POS_TOP_RIGHT);
                })
                .setNegativeButton(L.t("취소", "Cancel"), null)
                .show();
    }

    // ---- 목록과 영역 편집 창이 함께 쓰는 항목 (같은 설정이 두 곳에 보여도 이름·설명이 어긋나지 않게 한곳에서 만든다)

    private View numberHintToggle() {
        return toggle(L.t("숫자 키 힌트 없애기", "Hide number key hints"), L.t("숫자 키에서 길게 눌러 입력할 문자 힌트를 없앱니다.", "Hides the long-press character hints on number keys."),
                Prefs.NUMBER_HINT_HIDDEN, prefs.numberHintHidden());
    }

    /** 맨 아래 줄 기능키의 순서·유무와 격자 정렬. */
    private void addFunctionKeyLayout(LinearLayout c) {
        c.addView(button(L.t("기능키 순서·유무 사용자화", "Customize function keys"), v -> startActivity(new Intent(this, BottomKeysEditorActivity.class))));
        c.addView(note(L.t("맨 아래 줄 기능키의 순서와 유무를 바꿉니다.", "Changes the order and visibility of the function keys in the bottom row.")));
        // 바꾸면 미리보기도 바로 다시 그린다.
        c.addView(toggle(L.t("격자 정렬", "Grid alignment"), gridDesc(), prefs.gridLayout(), on -> {
            prefs.raw().edit().putBoolean(Prefs.GRID_LAYOUT, on).apply();
            refreshLayoutPreview();
        }));
    }

    /** 쌍자음·이중모음 입력 방식의 이름 (Prefs.INPUT_* 차례). */
    private static String[] inputModes() {
        return new String[]{L.t("아래로 밀기", "Swipe down"), L.t("두 번 연속으로 누르기", "Double tap"), L.t("둘 다", "Both")};
    }

    /** 한글 글자 키의 입력 동작: 쌍자음·이중모음 입력 방식 (아래로 밀기·두 번 연속으로 누르기·둘 다). */
    private void addKoreanInput(LinearLayout input) {
        View doubleTapTime = subGroup(
                slider(L.t("쌍자음 인식 시간", "Double consonant tap window"), Prefs.DOUBLE_TAP_CONSONANT_MS, prefs.doubleTapConsonantMs(),
                        80, 500, 10, "ms"),
                note(L.t("두 번째 누르기가 이 시간 안에 들어와야 쌍자음이 됩니다. 값을 줄이면 '학교'가 '하꾜'로 바뀌는 일이 줄고, "
                        + "대신 쌍자음을 낼 때 더 빨리 눌러야 합니다.", "The second tap must come within this time to make a double consonant. Lower values make '학교' turn into '하꾜' less often, but you need to tap faster for double consonants.")));
        doubleTapTime.setVisibility(prefs.consonantInputMode() != Prefs.INPUT_SWIPE || harvesting ? View.VISIBLE : View.GONE);
        SettingsWidgets.ValueRow[] consonant = new SettingsWidgets.ValueRow[1];
        consonant[0] = widgets.valueRow(L.t("쌍자음 입력", "Double consonant input"), inputModes()[prefs.consonantInputMode()], v ->
                pickInputMode(L.t("쌍자음 입력", "Double consonant input"), prefs.consonantInputMode(), Prefs.INPUT_SWIPE, mode -> {
                    prefs.setConsonantInputMode(mode);
                    consonant[0].set(inputModes()[mode]);
                    Ui.setVisibleAnimated(doubleTapTime, mode != Prefs.INPUT_SWIPE);
                }));
        input.addView(consonant[0].view);
        input.addView(doubleTapTime);
        SettingsWidgets.ValueRow[] vowel = new SettingsWidgets.ValueRow[1];
        vowel[0] = widgets.valueRow(L.t("이중모음 입력", "Y-vowel input"), inputModes()[prefs.vowelInputMode()], v ->
                pickInputMode(L.t("이중모음 입력", "Y-vowel input"), prefs.vowelInputMode(), Prefs.INPUT_BOTH, mode -> {
                    prefs.setVowelInputMode(mode);
                    vowel[0].set(inputModes()[mode]);
                }));
        input.addView(vowel[0].view);
        input.addView(note(L.t("아래로 밀기는 자음·모음 키를 아래로 밀어(ㄱ→ㄲ, ㅏ→ㅑ), 두 번 연속으로 누르기는 같은 키를 두 번 눌러(ㄱㄱ→ㄲ, ㅏㅏ→ㅑ) "
                + "입력합니다.", "Swipe down: swipe a consonant or vowel key down (ㄱ→ㄲ, ㅏ→ㅑ). Double tap: tap the same key twice (ㄱㄱ→ㄲ, ㅏㅏ→ㅑ).")));
    }

    /** 쌍자음·이중모음 입력 방식을 고르는 창 (아래로 밀기·두 번 연속으로 누르기·둘 다). */
    private void pickInputMode(String title, int current, int defaultMode, java.util.function.IntConsumer onPick) {
        AppTheme.dialogBuilder(this)   // 다른 고르기 창(길게 누르기·밀기 기능)과 같은 모양: 목록, 기본값, 취소
                .setTitle(title)
                .setSingleChoiceItems(inputModes(), current, (d, which) -> {
                    d.dismiss();
                    if (which != current) onPick.accept(which);
                })
                .setNeutralButton(L.t("기본값", "Default"), (d, w) -> {
                    if (current != defaultMode) onPick.accept(defaultMode);
                })
                .setNegativeButton(L.t("취소", "Cancel"), null)
                .show();
    }

    /** 영어 글자 키의 입력 동작: 자동 대문자, 아래로 밀어 대문자, 이메일 입력란. */
    private void addEnglishInput(LinearLayout input) {
        input.addView(toggle(L.t("영어 자동 대문자", "English auto-capitalize"), L.t("문장 첫 글자를 대문자로 시작합니다.", "Capitalizes the first letter of each sentence."), Prefs.AUTO_CAP, prefs.autoCap()));
        input.addView(toggle(L.t("영어 자판 아래로 밀어 대문자 입력", "Swipe down for capitals"),
                L.t("영어 자판에서 글자 키를 아래로 밀면 그 키의 대문자를 입력합니다. 켜면 각 키 설정의 아래로 밀기는 대문자로 고정됩니다.", "On the English keyboard, swipe a letter key down to type its capital. When on, swipe down in each key's settings is fixed to the capital letter."),
                Prefs.ENGLISH_SWIPE_UPPER, prefs.englishSwipeUpper()));
        input.addView(toggle(L.t("이메일 입력란은 영어로 시작", "Start email fields in English"),
                L.t("이메일 주소 입력란이나 앱이 영어 입력을 요청한 칸(아이디 입력란 등)에서는 영어 자판으로 시작하고, "
                        + "그 칸을 떠나면 원래 언어로 돌아갑니다. 웹 브라우저 주소창은 검색에도 쓰이므로 바꾸지 않습니다.", "Starts with the English keyboard on email address fields and fields where the app asks for English (such as usernames), then returns to the previous language when you leave. The browser address bar is not changed, since it is also used for search."),
                Prefs.ENGLISH_FIELDS, prefs.englishFields()));
    }

    /** 글자 키 길게 누르기 시간 (길게 눌러 문자 입력과 연속 입력이 함께 쓴다. 무엇을 할지는 키마다 정한다). */
    private void addLetterLongPress(LinearLayout c) {
        c.addView(slider(L.t("문자 길게 누르기 시간", "Character long-press delay"), Prefs.LONG_PRESS_MS, prefs.longPressMs(), 100, 800, 20, "ms"));
    }

    /** 모든 기능키(⌫·기호 키·🌐︎·Shift 키·엔터 키·Fn 키 등)가 같은 시간을 쓴다. */
    private View fnLongPressSlider() {
        return slider(L.t("기능키 길게 누르기 시간", "Function key long-press delay"), Prefs.DELETE_PRESS_MS, prefs.deletePressMs(), 200, 800, 50, "ms");
    }

    private View swipeDistanceSlider() {
        return slider(L.t("미는 거리", "Swipe distance"), Prefs.SWIPE_THRESHOLD, prefs.swipeThresholdDp(), 12, 48, 2, "dp");
    }

    private View fnSwipeDistanceSlider() {
        return slider(L.t("기능키 미는 거리", "Function key swipe distance"), Prefs.FN_SWIPE_THRESHOLD, prefs.fnSwipeThresholdDp(), 12, 48, 2, "dp");
    }

    /** 키보드 밀기 편집(한 손가락·두 손가락)과 위로 밀어서 추천 단어 입력. */
    private void addKeyboardSwipe(LinearLayout board) {
        board.addView(button(L.t("키보드 밀기 사용자화", "Customize keyboard swipes"), v ->
                startActivity(SwipeActionEditorActivity.intent(this, KeyboardSwipes.SPEC))));
        board.addView(note(L.t("자판을 한 손가락·두 손가락으로 위·아래·왼쪽·오른쪽으로 밀 때 실행할 기능을 정합니다. "
                + "한 손가락은 그 방향에 밀어서 입력할 글자가 없을 때 쓰입니다.", "Sets what happens when you swipe the keyboard up, down, left, or right with one or two fingers. One-finger swipes apply only when there is no character to type in that direction.")));
        board.addView(toggle(L.t("위로 밀어서 추천 단어 입력", "Swipe up to pick suggestions"),
                L.t("추천 단어가 떠 있을 때 자판의 왼쪽·가운데·오른쪽에서 위로 밀면 첫째·둘째·셋째 추천 단어를 입력합니다. ", "When suggestions are shown, swipe up from the left, center, or right of the keyboard to type the first, second, or third suggestion. "),
                Prefs.SWIPE_PICK_SUGGESTION, prefs.swipePickSuggestion()));
    }

    /** 이모지 창의 이모지 크기 (기본·크게·더 크게). 고르면 아래 견본 글자도 같은 크기로 바뀐다. */
    private View emojiSizeRow() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(0, Ui.dp(this, 8), 0, Ui.dp(this, 4));
        TextView t = new TextView(this);
        t.setText(L.t("이모지 크기", "Emoji size"));
        t.setTextColor(textColor);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        markItem(t, box);
        box.addView(t);
        TextView sample = new TextView(this);
        sample.setText("😀 😍 👍 🎉");
        sample.setGravity(Gravity.CENTER);
        sample.setTextSize(TypedValue.COMPLEX_UNIT_SP, com.alternative_studios.newswipe.emoji.EmojiPanel.textSp(prefs.emojiSize()));
        box.addView(choiceRowNow(new String[]{L.t("기본", "Default"), L.t("크게", "Large"), L.t("더 크게", "Larger")}, prefs.emojiSize(), i -> {
            prefs.raw().edit().putInt(Prefs.EMOJI_SIZE, i).apply();
            sample.setTextSize(TypedValue.COMPLEX_UNIT_SP, com.alternative_studios.newswipe.emoji.EmojiPanel.textSp(i));
        }));
        LinearLayout.LayoutParams slp = matchWrap();
        slp.topMargin = Ui.dp(this, 6);
        box.addView(sample, slp);
        return box;
    }

    /** 한글 자판 배열: 배열 고르기와 설명, 상세 배열 (사용 안 함·자·모음 균형 배열·분리 키보드)과 그 하위 항목. */
    private void buildKoreanLayoutSection() {
        LinearLayout korean = section(L.t("한글 자판 배열", "Korean layout"));
        String[] koreanLayouts = {Prefs.KOREAN_LAYOUT_DUBEOLSIK, Prefs.KOREAN_LAYOUT_8, Prefs.KOREAN_LAYOUT_D7};
        int selected = prefs.koreanDubeolsik() ? 0 : prefs.koreanD7() ? 2 : 1;
        // 배열을 고르면 화면을 다시 만들지 않고 안내문·토글·미리보기를 그 자리에서 바꾼다 (선택 버튼의 스프링이 끊기지 않게).
        TextView layoutNote = note(layoutNoteText());
        // 모든 한글 배열에서 쓸 수 있고, 모든 배열이 같은 값을 쓴다. 격자 정렬과 함께 쓰면 기능키가 나뉜 자음 키 폭에 맞춰진다.
        // 토글의 하위 항목(강조선·들여쓰기)으로 보이는 버튼이다.
        // 버튼은 위아래 여백이 있어, 버튼 자체를 접으면 높이가 0이 된 뒤 남은 여백이 한꺼번에 사라져 끊겨 보인다.
        // 그래서 여백 없는 틀(fold)로 감싸 그 틀을 펼치고 접는다.
        // 두벌식에서만: 영어 자판도 같은 모양으로 나눈다. 균형 배열의 하위 항목으로 둔다.
        View englishToggle = toggle(L.t("영어 자판도 균형 배열", "Balanced layout for English too"),
                L.t("영어 자판도 두벌식처럼 가운데에서 나눠 왼쪽 5·5·4키, 오른쪽 5·4·3키로 놓습니다. 경계와 키 폭·위치 설정을 함께 씁니다.", "Splits the English keyboard in the middle like Dubeolsik, with 5·5·4 keys on the left and 5·4·3 on the right. Uses the same edge, key width and position settings."),
                prefs.raw().getBoolean(Prefs.ENGLISH_BALANCED, false), on -> {
                    prefs.raw().edit().putBoolean(Prefs.ENGLISH_BALANCED, on).apply();
                    refreshLayoutPreview();
                });
        // 자·모음 균형 배열의 하위 항목: 영어 자판도 균형 배열 (두벌식에서만). 키 폭·위치는 아래 '자·모음 균형 배열' 섹션에 있다.
        View keyArea = fold(subGroup(englishToggle));
        // 상세 배열: 사용 안 함·자·모음 균형 배열·분리 키보드 중 하나 (스페이스바 길게 누르기처럼 줄을 눌러 고른다).
        // 균형 배열과 분리 키보드(세로 모드) 모두 모든 한글 배열이 함께 쓴다.
        // 분리 키보드의 하위 항목: 좌·우 균형 배열 (NewSwipe 단모음) 또는 자·모음 분리 배열 (단모음). 두벌식에는 없다.
        // 토글의 값과 이름이 분리 키보드 사용 여부·배열에 따라 달라지므로 보일 때마다 새로 만든다.
        LinearLayout splitSub = new LinearLayout(this);
        splitSub.setOrientation(LinearLayout.VERTICAL);
        splitSub.setLayoutParams(matchWrap());
        Runnable fillSplitSub = () -> {
            splitSub.removeAllViews();
            if (!prefs.portraitSplit() || prefs.koreanDubeolsik()) return;
            splitSub.addView(subGroup(splitArrangeToggle(prefs, prefs.splitReachColumnKey(), () -> {
                if (editorPreview != null) editorPreview.refresh();
                refreshLayoutPreview();
            })));
        };
        fillSplitSub.run();
        splitSub.setVisibility(splitSubShown() ? View.VISIBLE : View.GONE);
        // 상세 배열 아래의 설명: 고른 배열의 것만 보인다 ('사용 안 함'이면 감춘다).
        // 글에 위아래 여백이 있어 여백 없는 틀(fold)로 감싸 그 틀을 펼치고 접는다 (하위 항목과 같은 까닭).
        TextView detailNote = note(detailNoteText(detailLayout()));
        View detailNoteBox = fold(detailNote);
        detailNoteBox.setVisibility(detailLayout() == DETAIL_NONE ? View.GONE : View.VISIBLE);
        SettingsWidgets.ValueRow[] detail = new SettingsWidgets.ValueRow[1];
        detail[0] = widgets.valueRow(L.t("상세 배열", "Advanced layout"), detailLayouts()[detailLayout()], v ->
                pickDetailLayout(mode -> {
                    android.content.SharedPreferences.Editor e = prefs.raw().edit();
                    e.putBoolean(Prefs.PORTRAIT_SPLIT, mode == DETAIL_SPLIT);
                    if (mode != DETAIL_SPLIT) e.putBoolean(prefs.balancedLayoutKey(), mode == DETAIL_BALANCED);
                    e.apply();
                    detail[0].set(detailLayouts()[mode]);
                    Ui.setVisibleAnimated(keyArea, balancedSubShown());
                    showDetailNote(detailNoteBox, detailNote, mode);
                    if (balancedHolder != null) {
                        if (mode == DETAIL_BALANCED) {
                            fillBalancedHolder();
                            balancedHolder.setVisibility(View.GONE);
                            Ui.setVisibleAnimated(balancedHolder, true);
                        } else {
                            Ui.setVisibleAnimated(balancedHolder, false);
                        }
                    }
                    if (splitSubShown()) {
                        fillSplitSub.run();
                        splitSub.setVisibility(View.GONE);
                        Ui.setVisibleAnimated(splitSub, true);
                    } else {
                        Ui.setVisibleAnimated(splitSub, false);
                    }
                    if (splitHolder != null) {
                        if (mode == DETAIL_SPLIT) {
                            fillSplitHolder();
                            splitHolder.setVisibility(View.GONE);
                            Ui.setVisibleAnimated(splitHolder, true);
                        } else {
                            Ui.setVisibleAnimated(splitHolder, false);
                        }
                    }
                    refreshLayoutPreview();
                }));
        keyArea.setVisibility(balancedSubShown() ? View.VISIBLE : View.GONE);
        LinearLayout choices = choiceRowNow(new String[]{L.t("두벌식", "Dubeolsik"), L.t("단모음", "Danmoeum"), L.t("NewSwipe 단모음", "NewSwipe Danmoeum")}, selected,
                i -> {
                    prefs.setKoreanLayout(koreanLayouts[i]);
                    layoutNote.setText(layoutNoteText());
                    // 예전에 배열마다 저장한 균형 배열 값이 남아 있으면 배열에 따라 달라질 수 있으므로 상세 배열을 다시 맞춘다.
                    detail[0].set(detailLayouts()[detailLayout()]);
                    Ui.setVisibleAnimated(keyArea, balancedSubShown());
                    showDetailNote(detailNoteBox, detailNote, detailLayout());
                    // 하위 토글은 배열마다 이름이 달라 (두벌식은 없음) 고른 배열의 것으로 다시 만든다.
                    if (splitSubShown()) fillSplitSub.run();
                    Ui.setVisibleAnimated(splitSub, splitSubShown());
                    // 균형 배열의 키 폭·위치도 배열마다 따로 저장하므로 고른 배열의 것으로 다시 채운다.
                    if (detailLayout() == DETAIL_BALANCED) fillBalancedHolder();
                    // 분리 키보드 묶음 폭·위치는 배열마다 따로 저장하므로 고른 배열의 것으로 다시 채운다.
                    if (prefs.portraitSplit()) fillSplitHolder();
                    refreshLayoutPreview();
                });
        korean.addView(choices);
        korean.addView(layoutNote);
        korean.addView(detail[0].view);
        detailRow = detail[0].view;
        // 하위 항목(영어 자판도 균형 배열, 좌·우 균형 배열·자·모음 분리 배열)이 상세 배열 바로 아래, 설명은 그 아래.
        korean.addView(keyArea);
        korean.addView(splitSub);
        korean.addView(detailNoteBox);
    }

    /** 자·모음 균형 배열의 하위 항목(영어 자판도 균형 배열)이 보이는지: 자·모음 균형 배열을 골랐고 두벌식일 때. */
    private boolean balancedSubShown() {
        return detailLayout() == DETAIL_BALANCED && englishBalancedShown();
    }

    /** 분리 키보드의 하위 항목(좌·우 균형 배열·자·모음 분리 배열)이 보이는지: 분리 키보드를 골랐고 두벌식이 아닐 때. */
    private boolean splitSubShown() {
        return prefs.portraitSplit() && !prefs.koreanDubeolsik();
    }

    /** 상세 배열 아래의 설명 (고른 배열의 것). '사용 안 함'이면 빈 글. */
    private static String detailNoteText(int mode) {
        if (mode == DETAIL_BALANCED) return L.t("자·모음 균형 배열은 한글 글자 키 영역을 반으로 나눠 각각에 자음과 모음을 놓습니다.", "The balanced consonant/vowel layout splits the Korean letter keys in half, with consonants on one side and vowels on the other.");
        if (mode == DETAIL_SPLIT) return L.t("분리 키보드는 키보드 전체를 반으로 나눕니다. 영어 자판과 기호 자판에도 적용됩니다.", "The split keyboard splits the whole keyboard in half. It also applies to the English and symbol keyboards.");
        return "";
    }

    /** 상세 배열을 바꾸면 설명을 고른 배열의 것으로 바꾼다 ('사용 안 함'이면 접는다. 접는 동안은 앞의 글을 그대로 둔다). */
    private void showDetailNote(View box, TextView note, int mode) {
        if (mode != DETAIL_NONE) note.setText(detailNoteText(mode));
        Ui.setVisibleAnimated(box, mode != DETAIL_NONE);
    }

    /** 상세 배열의 이름 (DETAIL_* 차례). */
    private static String[] detailLayouts() {
        return new String[]{L.t("사용 안 함", "Off"), L.t("자·모음 균형 배열", "Balanced consonant/vowel layout"), L.t("분리 키보드", "Split keyboard")};
    }
    private static final int DETAIL_NONE = 0, DETAIL_BALANCED = 1, DETAIL_SPLIT = 2;

    /** 지금 한글 배열의 상세 배열: 세로 모드 분리 키보드가 켜져 있으면 분리 키보드, 아니면 균형 배열 설정. */
    private int detailLayout() {
        if (prefs.portraitSplit()) return DETAIL_SPLIT;
        return prefs.balancedLayoutSetting() ? DETAIL_BALANCED : DETAIL_NONE;
    }

    /** 상세 배열을 고르는 창 (사용 안 함·자·모음 균형 배열·분리 키보드). 다른 고르기 창과 같은 모양: 목록, 기본값, 취소. */
    private void pickDetailLayout(java.util.function.IntConsumer onPick) {
        int current = detailLayout();
        AppTheme.dialogBuilder(this)
                .setTitle(L.t("상세 배열", "Advanced layout"))
                .setSingleChoiceItems(detailLayouts(), current, (d, which) -> {
                    d.dismiss();
                    if (which != current) onPick.accept(which);
                })
                .setNeutralButton(L.t("기본값", "Default"), (d, w) -> {
                    if (current != DETAIL_NONE) onPick.accept(DETAIL_NONE);
                })
                .setNegativeButton(L.t("취소", "Cancel"), null)
                .show();
    }

    /** '영어 자판도 균형 배열'은 두벌식에서만 보인다 (균형 배열을 켰을 때 그 하위 묶음과 함께. 설정 검색 목록에는 늘 넣는다). */
    private boolean englishBalancedShown() {
        return harvesting || prefs.koreanDubeolsik();
    }

    /** 고른 한글 배열의 설명. */
    private String layoutNoteText() {
        if (prefs.koreanDubeolsik()) {
            return L.t("컴퓨터 키보드와 같은 표준 한글 배열입니다.", "The standard Korean layout, the same as on computer keyboards.");
        }
        return prefs.koreanD7()
                ? L.t("거센소리 일부(ㅊ, ㅌ)를 빼 한 줄을 7칸으로 줄인 배열입니다. "
                        + "ㅊ·ㅌ은 ㅈ·ㄷ을 오른쪽으로 밀어 입력합니다.", "Drops some aspirated consonants (ㅊ, ㅌ) to fit 7 keys per row. Type ㅊ and ㅌ by swiping ㅈ and ㄷ to the right.")
                : L.t("두벌식 키보드에서 ㅣ계 이중모음(ㅑ·ㅕ·ㅛ·ㅠ)을 빼 한 줄을 8칸으로 줄인 배열입니다.", "Drops the Y-vowels (ㅑ·ㅕ·ㅛ·ㅠ) from Dubeolsik to fit 8 keys per row.");
    }

    private static String gridDesc() {

        return L.t("아래 줄의 기능키를 한글 키와 같은 폭으로 해 줄을 맞춥니다. "
            + "오쏘리니어 키보드에서 영감을 받았습니다.", "Makes the function keys in the bottom row as wide as the Korean keys so the columns line up. Inspired by ortholinear keyboards.");

    }
    /** v를 스크롤 목록 위의 고정된 곳에 놓는다. 설정 검색용으로 화면을 만들 때는 그 임시 목록에 넣는다. */
    private void pinTop(View v) {
        if (harvesting) {
            list.addView(v);
            return;
        }
        pinned.addView(v);
        pinned.setVisibility(View.VISIBLE);
    }

    /** 가로 모드·폴더블 내부 화면 키보드 사용자화 화면 아래의 미리보기 (그 프로필의 값만 손잡이로 바꾼다). 다른 화면에서는 null. */
    private KeyboardPreview profilePreview;

    /**
     * 실제 키보드와 같은 크기·모양의 미리보기를 화면 아래에 붙인다 ('키보드 사용자화'와 같은 자리).
     * 글자 키 높이와, 분리 키보드면 두 묶음의 폭·가로 위치를 손잡이로 바꾼다. 키·영역은 고를 수 없다.
     */
    private void addProfilePreview(String profile) {
        if (harvesting) return;
        profileShown = profile;
        profilePreview = new KeyboardPreview(this, prefs, new KeyboardPreview.Listener() {
            @Override public void onTap(KeyboardPreview.Selection s) { onProfileTap(s); }
            @Override public void onSizeChanged(String prefKey) { syncSizeSliders(prefKey); }
            @Override public void onLayerChanged(int layer) {
                closeKeyPanel();   // 다른 페이지로 넘기면 고른 것이 풀린다
                if (profileDots != null) {
                    profileDots.setPage(layer);
                    profileDots.announce(layer);
                }
            }
        });
        profilePreview.setProfileMode(prefs.profileEditView(profile), profileScreenWidth(profile));
        // 되돌리기: 키보드 사용자화와 같은 기록을 쓰되, 이 화면에서 바꾼 것만 되돌린다.
        editorUndo = new EditorUndo(prefs, uiHandler, this::updateUndoButton);
        editorUndo.enterScope(getIntent().getStringExtra(EXTRA_SECTION));
        updateUndoButton();
        editorPrefListener = (sp, key) -> {
            editorUndo.record(key);
            // 키보드 사용자화도 같은 값을 기록하므로, 그쪽이 쌓은 단계도 반영되게 한 박자 뒤에 버튼을 맞춘다.
            uiHandler.post(() -> uiHandler.post(this::updateUndoButton));
        };
        prefs.raw().registerOnSharedPreferenceChangeListener(editorPrefListener);
        // 키보드 사용자화처럼 미리보기 위에 페이지 표시: 키보드 전체 설정 · 영역 설정 (키 설정은 세로 모드와 함께 쓴다).
        profileDots = new PageDots(this, java.util.Arrays.copyOf(pageNames(), 2), java.util.Arrays.copyOf(pageDescs(), 2),
                AppTheme.of(this), profilePreview::setLayer);
        profileDots.setPage(profilePreview.layer());
        pinnedBottom.addView(profileDots, matchWrap());
        pinnedBottom.addView(profilePreview, matchWrap());
        pinnedBottom.setVisibility(View.VISIBLE);
        // 영역 설정 창: 목록 위로 아래에서 올라온다 (키보드 사용자화와 같다).
        sheet = new EditorSheet(this, middle, this::closeKeyPanel);
        areaBox = new LinearLayout(this);
        areaBox.setOrientation(LinearLayout.VERTICAL);
        sheet.body().addView(areaBox, matchWrap());
    }

    /** 미리보기가 그리는 프로필 (폴더블 내부 화면은 세로·가로 버튼으로 바뀐다). */
    private String profileShown;
    /** 가로 모드·폴더블 내부 화면 미리보기 위의 페이지 표시 (키보드 전체 설정 · 영역 설정). */
    private PageDots profileDots;

    /** 미리보기에서 영역을 눌렀다: 그 영역의 설정 창을 연다. 빈 곳이나 이미 고른 영역이면 닫는다. */
    private void onProfileTap(KeyboardPreview.Selection s) {
        KeyboardPreview.Selection now = profilePreview.selection();
        if (s == null || s.same(now)) {
            closeKeyPanel();
            return;
        }
        profilePreview.select(s);
        String profile = profileShown;
        if (s.kind == KeyboardPreview.SEL_BOARD) {
            fillAreaBox(() -> buildProfileBoard(profile));
            sheet.setTitle(L.t("키보드 전체 설정", "Whole keyboard settings"));
            sheet.scroll().scrollTo(0, 0);
            if (!panelOpen()) openSheet();
            return;
        }
        int area = s.area();
        boolean split = prefs.profileEditView(profile).profileSplit();
        fillAreaBox(() -> buildProfileArea(profile, area, split));
        sheet.setTitle(KeyboardPreview.profileAreaName(area) + L.t(" 설정", " settings"));
        sheet.scroll().scrollTo(0, 0);
        if (!panelOpen()) openSheet();
    }

    /**
     * 그 프로필이 실제로 쓰는 화면 폭 (px): 가로 방향 프로필은 이 기기의 긴 쪽, 세로 방향은 짧은 쪽.
     * 미리보기는 이 폭으로 키보드를 그대로 만든 뒤 화면 폭에 맞춰 줄이거나 늘려, 어느 방향으로 들고 봐도 실제와 같다.
     */
    private int profileScreenWidth(String profile) {
        android.util.DisplayMetrics m = getResources().getDisplayMetrics();
        int longSide = Math.max(m.widthPixels, m.heightPixels), shortSide = Math.min(m.widthPixels, m.heightPixels);
        return isLandscapeProfile(profile) ? longSide : shortSide;
    }

    /**
     * 가로 모드 키보드 사용자화의 안내: 기기가 세로일 때만 '미리보기가 작게 보인다'고 알리고, 가로일 때는 아무 문구도 두지 않는다
     * (실제 크기라서).
     */
    private void addLandscapeIntro() {
        boolean landNow = getResources().getConfiguration().orientation
                == android.content.res.Configuration.ORIENTATION_LANDSCAPE;
        if (landNow && !harvesting) return;
        LinearLayout intro = card();
        intro.addView(note(L.t("세로 상태에서는 키보드 미리보기가 작게 보일 수 있습니다. 실제 크기의 미리보기를 원하시면 기기를 가로로 돌리세요.", "The keyboard preview may look small in portrait. For a full-size preview, rotate the device sideways.")));
    }

    /** 가로 모드·폴더블 내부 화면의 값 슬라이더: 바꾸면 미리보기를 다시 그리고, 미리보기의 손잡이를 끌면 따라 움직인다. */
    private View profileSlider(String label, String key, java.util.function.IntSupplier current, int min, int max, int step,
                               String unit) {
        SettingsWidgets.Slider sl = sliderRow(label, key, current.getAsInt(), min, max, step, unit, this::refreshLayoutPreview);
        if (!harvesting) sliderSink.computeIfAbsent(key, k -> new java.util.ArrayList<>()).add(() -> sl.set(current.getAsInt()));
        return sl.view;
    }

    /**
     * 가로 모드 키보드 사용자화: 아래 미리보기에서 영역(분리 키보드면 왼쪽·오른쪽 묶음, 가운데 공간, 기능키)을 누르면 그 영역의
     * 설정 창이 뜬다 (키보드 사용자화의 영역 설정과 같다). 목록에는 어느 영역에도 속하지 않는 키보드 전체 설정만 둔다.
     */
    private void buildLandscapeArea() {
        String p = Prefs.PROFILE_LANDSCAPE;
        addProfilePreview(p);   // 실제 키보드와 같은 미리보기를 화면 아래에 붙인다
        addLandscapeIntro();
        addProfileSplit(p, L.t("가로 모드", "Landscape"));
        // 키 글자 크기와 여백은 미리보기의 키보드 전체 설정 창에 있다.
        harvestProfileAreas(p);
    }

    /** 실험실: 아직 시험 중인 기능. */
    private void buildLab() {
        LinearLayout intro = card();
        intro.addView(note(L.t("아직 시험 중인 기능입니다. 동작이 바뀌거나 없어질 수 있습니다.", "These features are still experimental and may change or be removed.")));

        LinearLayout direction = section(L.t("밀기 방향 판정", "Swipe direction"));
        direction.addView(slider(L.t("아래로 밀기 엄격도", "Swipe-down strictness"), Prefs.SWIPE_DOWN_RATIO, prefs.swipeDownRatioPct(),
                Prefs.SWIPE_RATIO_MIN, Prefs.SWIPE_RATIO_MAX, 10, "%"));
        direction.addView(slider(L.t("위로 밀기 엄격도", "Swipe-up strictness"), Prefs.SWIPE_UP_RATIO, prefs.swipeUpRatioPct(),
                Prefs.SWIPE_RATIO_MIN, Prefs.SWIPE_RATIO_MAX, 10, "%"));
        direction.addView(note(L.t("글자 키를 밀 때 위·아래로 인정되려면 세로로 움직인 거리가 가로로 움직인 거리의 이 비율을 넘어야 합니다. "
                + "값이 클수록 위·아래 판정이 엄격해져 좌우로 밀 때 손가락이 처져도 좌우로 인식됩니다. "
                + "기본값은 아래 150%, 위 120%입니다.", "For a swipe on a letter key to count as up or down, the vertical distance must exceed this percentage of the horizontal distance. Higher values make up and down stricter, so sideways swipes are still recognized when your finger drifts. Defaults are 150% for down and 120% for up.")));

        addCjkKeyboard(com.alternative_studios.newswipe.cjk.CjkComposer.JA);
        addCjkKeyboard(com.alternative_studios.newswipe.cjk.CjkComposer.ZH);
        addHardwareKeyboard();
    }

    /**
     * 실험실 '일본어 자판'·'중국어 자판'. 앱에는 변환 엔진만 들어 있어, 그 언어의 사전 팩을 가져와야 켤 수 있다
     * (쓰지 않는 사람의 앱 크기를 늘리지 않는다). 켜면 지구본 키가 한글 → 영어 → 일본어 → 중국어 차례로 바꾼다.
     */
    private void addCjkKeyboard(String lang) {
        boolean ja = com.alternative_studios.newswipe.cjk.CjkComposer.JA.equals(lang);
        String name = ja ? L.t("일본어 자판", "Japanese keyboard") : L.t("중국어 자판", "Chinese keyboard");
        LinearLayout box = section(name);
        com.alternative_studios.newswipe.cjk.CjkPack.Info info =
                com.alternative_studios.newswipe.cjk.CjkPack.installed(getFilesDir(), lang);
        int req = ja ? REQ_CJK_JA : REQ_CJK_ZH;
        String release = SOURCE_URL + "/releases?q=" + (ja ? "ja-standard" : "zh-standard") + "&expanded=true";
        if (info == null) {
            box.addView(note((ja ? L.t("히라가나 입력(12키 플릭·로마자)과 한자·가타카나 변환을 씁니다. ", "Type hiragana (12-key flick or romaji) and convert to kanji and katakana. ")
                    : L.t("병음으로 간체자를 입력합니다. ", "Type simplified Chinese with pinyin. "))
                    + L.t("앱에는 변환 기능만 들어 있어, 사전 팩을 가져와야 켤 수 있습니다. 단어 추천·자동 수정·학습은 쓰지 않습니다.",
                    "The app contains only the conversion engine, so you need to import a dictionary pack to turn it on. Word suggestions, autocorrect and learning are not used.")));
            box.addView(button(L.t("사전 팩 받는 곳", "Get dictionary packs"), v -> openUrl(release)));
            box.addView(iconButton(L.t("사전 팩 가져오기", "Import dictionary pack"), Icons.IMPORT, v -> SettingsImport.pick(this, req)));
            return;
        }
        View group = ja ? subGroup(choiceRow(new String[]{L.t("12키 플릭", "12-key flick"), L.t("로마자 (QWERTY)", "Romaji (QWERTY)")}, prefs.jaQwerty() ? 1 : 0,
                        i -> prefs.raw().edit().putString(Prefs.CJK_JA_LAYOUT, i == 1 ? Prefs.JA_QWERTY : Prefs.JA_FLICK).apply()),
                note(L.t("12키 플릭: 키를 누르면 あ단, 왼쪽·위·오른쪽·아래로 밀면 い·う·え·お단이 들어갑니다. "
                        + "゛ 키는 앞 글자의 탁점·반탁점·작은 글자를 차례로 바꿉니다. "
                        + "스페이스로 변환 후보를 고르고 엔터로 확정합니다. ◀·▶ 키는 길게 누르면 커서가 계속 움직입니다.", "12-key flick: tap a key for the あ column; swipe left, up, right, or down for い, う, え, or お. The ゛ key cycles the previous character through dakuten, handakuten and small forms. Use space to pick a conversion and enter to confirm. Hold ◀ or ▶ to keep moving the cursor.")),
                sizeSlider(L.t("일본어 자판 높이", "Japanese keyboard height"), Prefs.CJK_JA_HEIGHT, prefs::jaHeight, Prefs.CJK_JA_HEIGHT_MIN,
                        Prefs.CJK_JA_HEIGHT_MAX, 5, "%"),
                note(L.t("일본어로 입력하는 동안의 자판(글자·기호) 높이입니다. 다른 자판 높이에 대한 비율이고, 100%면 같습니다.", "Height of the keyboard (letters and symbols) while typing Japanese, relative to the other keyboards. 100% means the same.")),
                toggle(L.t("반각 공백 쓰기", "Use half-width spaces"), L.t("스페이스로 반각 공백을 넣습니다. 끄면 Gboard처럼 일본어를 입력하는 동안에는 "
                        + "전각 공백(　)을 넣습니다 (변환 중에는 변환 후보를 고릅니다).", "Space types a half-width space. When off, it types a full-width space (　) while typing Japanese, like Gboard (during conversion it picks a candidate)."),
                        Prefs.CJK_JA_HALF_SPACE, prefs.jaHalfSpace()))
                : subGroup(note(L.t("스페이스로 첫 후보를 넣고, 엔터는 병음을 그대로 넣습니다. 음절을 직접 나누려면 '를 씁니다 (xi'an).", "Space enters the first candidate, and enter types the pinyin as is. To split syllables yourself, use ' (xi'an).")));
        box.addView(toggle(name, ja ? L.t("지구본 키로 일본어 자판으로 바꿀 수 있게 합니다.", "Lets you switch to the Japanese keyboard with the globe key.")
                        : L.t("지구본 키로 중국어 자판으로 바꿀 수 있게 합니다.", "Lets you switch to the Chinese keyboard with the globe key."),
                Prefs.CJK_PREFIX + lang, prefs.cjkEnabled(lang), group));
        box.addView(group);
        StringBuilder b = new StringBuilder(info.name);
        if (!info.version.isEmpty()) b.append(" ").append(info.version);
        b.append(L.t("\n읽기 ", "\n")).append(String.format(java.util.Locale.ROOT, "%,d", info.words)).append(L.t("개 · ", " readings · "))
                .append(String.format(java.util.Locale.ROOT, "%.1f", info.bytes / 1048576.0)).append("MB");
        if (!info.description.isEmpty()) b.append("\n").append(info.description);
        if (!info.attribution.isEmpty()) {
            b.append(L.t("\n출처: ", "\nSource: ")).append(info.attribution);
            if (!info.license.isEmpty()) b.append(" (").append(info.license).append(")");
        }
        box.addView(note(b.toString()));
        if (!info.licenseText.isEmpty()) {
            box.addView(button(L.t("사전 팩의 라이선스 보기", "View dictionary pack license"), v -> AppTheme.dialogBuilder(this)
                    .setTitle(info.name)
                    .setMessage(info.licenseText)
                    .setPositiveButton(L.t("닫기", "Close"), null)
                    .show()));
        }
        box.addView(iconButton(L.t("다른 사전 팩 가져오기", "Import another dictionary pack"), Icons.IMPORT, v -> SettingsImport.pick(this, req)));
        box.addView(trashButton(L.t("사전 팩 지우기", "Delete dictionary pack"), v -> AppTheme.dialogBuilder(this)
                .setTitle(L.t("사전 팩 지우기", "Delete dictionary pack"))
                .setMessage(L.t("가져온 사전 팩을 지우고 " + name + "을 끕니다. 다시 쓰려면 팩을 다시 가져와야 합니다.",
                        "Deletes the imported dictionary pack and turns off the " + name + ". To use it again, import the pack again."))
                .setPositiveButton(L.t("지우기", "Delete"), (d, w) -> {
                    prefs.setCjkEnabled(lang, false);
                    com.alternative_studios.newswipe.cjk.CjkPack.remove(getFilesDir(), lang);
                    Toast.makeText(this, L.t("사전 팩을 지웠습니다", "Dictionary pack deleted"), Toast.LENGTH_SHORT).show();
                    recreate();
                })
                .setNegativeButton(L.t("취소", "Cancel"), null)
                .show()));
    }

    /** 고른 일본어·중국어 사전 팩을 검사해 설치하고, 그 언어의 자판을 켠다. */
    private void importCjkPack(android.net.Uri uri, String lang) {
        Toast.makeText(this, L.t("사전 팩을 확인하는 중입니다…", "Checking dictionary pack…"), Toast.LENGTH_SHORT).show();
        android.content.Context app = getApplicationContext();
        new Thread(() -> {
            String message;
            boolean ok = false;
            try (java.io.InputStream in = app.getContentResolver().openInputStream(uri)) {
                if (in == null) throw new java.io.IOException(L.t("파일을 열지 못했습니다", "Couldn't open the file"));
                com.alternative_studios.newswipe.cjk.CjkPack.Info info =
                        com.alternative_studios.newswipe.cjk.CjkPack.install(in, app.getFilesDir(), lang);
                String langName = com.alternative_studios.newswipe.cjk.CjkPack.languageName(lang);
                message = L.t("'" + info.name + "'을 가져와 " + langName + " 자판을 켰습니다. 지구본 키로 바꿀 수 있습니다",
                        "Imported '" + info.name + "' and turned on the " + langName + " keyboard. Switch to it with the globe key");
                ok = true;
            } catch (com.alternative_studios.newswipe.cjk.CjkPack.InvalidPackException e) {
                message = L.t("가져올 수 없는 사전 팩입니다: ", "Can't import this dictionary pack: ") + e.getMessage();
            } catch (Exception e) {
                message = L.t("사전 팩을 가져오지 못했습니다: ", "Couldn't import the dictionary pack: ") + e.getMessage();
            }
            String m = message;
            boolean done = ok;
            runOnUiThread(() -> {
                if (done) prefs.setCjkEnabled(lang, true);
                Toast.makeText(app, m, Toast.LENGTH_LONG).show();
                if (done && !isFinishing()) recreate();
            });
        }).start();
    }

    /** 실험실 '외장 키보드': 블루투스·USB 키보드의 한·영 전환 키, 언어 전환 알림, 도구 막대. */
    private void addHardwareKeyboard() {
        LinearLayout hw = section(L.t("외장 키보드", "Physical keyboard"));
        hw.addView(note(L.t("한글은 두벌식 배열 기준입니다. 한·영 전환은 시스템 기본값을 쓰되, 바꿀 수 있습니다.", "Korean follows the Dubeolsik layout. Korean/English switching uses the system default, but you can change it.")));
        TextView customValue = new TextView(this);
        customValue.setTextColor(hintColor);
        customValue.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        customValue.setText(hwKeyName(prefs.hwCustomKey(), prefs.hwCustomScan()));
        View langGroup = subGroup(
                toggle(L.t("한·영 키", "Han/Yeong key"), L.t("키보드의 한·영 키(언어 전환 키)로 바꿉니다. "
                        + "기기에 따라 시스템이 이 키를 먼저 가져가 바뀌지 않으면 아래의 '한·영 키 직접 지정'을 써 보세요.", "Switches with the keyboard's Han/Yeong key (language switch key). If the system takes this key first on your device and nothing changes, try 'Assign switch key' below."),
                        Prefs.HW_LANG_KEY, prefs.hwLangKey()),
                toggle("Shift+Space", L.t("Shift를 누른 채 스페이스바를 누르면 바꿉니다.", "Switches when you press space while holding Shift."),
                        Prefs.HW_SHIFT_SPACE, prefs.hwShiftSpace()),
                toggle(L.t("오른쪽 Alt", "Right Alt"), L.t("오른쪽 Alt만 눌렀다 떼면 바꿉니다. 한·영 키를 누르면 오른쪽 Alt를 보내는 키보드가 많습니다. "
                        + "다른 키와 함께 누르면 원래의 Alt로 쓰입니다.", "Switches when you press and release right Alt alone. Many keyboards send right Alt from the Han/Yeong key. Pressed with other keys, it works as a normal Alt."),
                        Prefs.HW_RIGHT_ALT, prefs.hwRightAlt()),
                toggle("Ctrl+Space", L.t("Ctrl을 누른 채 스페이스바를 누르면 바꿉니다. "
                        + "기기에 따라 시스템이 키보드 배열 전환에 먼저 쓸 수 있습니다.", "Switches when you press space while holding Ctrl. On some devices the system may use it first to switch keyboard layouts."),
                        Prefs.HW_CTRL_SPACE, prefs.hwCtrlSpace()),
                button(L.t("한·영 키 직접 지정", "Assign switch key"), customValue, v -> pickHardwareLangKey(customValue)),
                note(L.t("위에 없는 키(오른쪽 Ctrl, 한자 키 등)를 한·영 전환에 씁니다. Ctrl·Alt 같은 수정 키는 혼자 눌렀다 뗄 때만 바꿉니다.", "Uses a key not listed above (right Ctrl, Hanja key, etc.) for Korean/English switching. Modifier keys like Ctrl or Alt switch only when pressed and released alone.")));
        hw.addView(toggle(L.t("한·영 전환 키 바꾸기", "Change Korean/English switch keys"), L.t("한·영 전환을 다른 키 조합으로도 할 수 있게 바꿉니다.", "Lets you switch between Korean and English with other key combinations too."),
                Prefs.HW_LANG_CUSTOM, prefs.hwLangCustom(), langGroup));
        hw.addView(langGroup);
        hw.addView(toggle(L.t("언어 전환 알림", "Language switch notice"), L.t("화면 자판이 보이지 않을 때 한·영을 바꾸면 '한글' 또는 'English'를 잠깐 띄웁니다.", "When the on-screen keyboard is hidden, briefly shows '한글' or 'English' when you switch."),
                Prefs.HW_TOAST, prefs.hwToast()));
        hw.addView(toggle(L.t("도구 막대 보이기", "Keep toolbar visible"),
                L.t("외장 키보드를 연결해 화면 자판이 감춰질 때도 도구 막대를 보입니다. "
                        + "클립보드와 이모지 창은 원래 크기로 열립니다. "
                        + "도구 막대의 '키보드 보이기' 버튼을 누르면 자판 전체가 보입니다.", "Shows the toolbar even when the on-screen keyboard is hidden because a physical keyboard is connected. Clipboard and emoji panels open at full size. Tap 'Show keyboard' on the toolbar to show the whole keyboard."),
                Prefs.HW_TOOLBAR, prefs.hwToolbar()));
    }

    /** 외장 키보드에서 키를 하나 눌러 한·영 전환 키로 정한다. */
    private void pickHardwareLangKey(TextView valueView) {
        Context dc = AppTheme.dialogContext(this);
        TextView message = new TextView(dc);
        message.setText(L.t("외장 키보드에서 한·영 전환에 쓸 키를 누르세요.", "Press the key on your physical keyboard to use for Korean/English switching."));
        message.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        message.setTextColor(textColor);
        int pad = Ui.dp(this, 24);
        message.setPadding(pad, Ui.dp(this, 8), pad, 0);
        android.app.AlertDialog dialog = AppTheme.accentBuilder(dc)
                .setTitle(L.t("한·영 키 직접 지정", "Assign switch key"))
                .setView(message)
                .setNeutralButton(L.t("지정 해제", "Unassign"), (d, w) -> saveHardwareLangKey(valueView, 0, 0))
                .setNegativeButton(L.t("취소", "Cancel"), null)
                .create();
        dialog.setOnKeyListener((d, keyCode, event) -> {
            // 시스템 키와 Esc는 그대로 둔다 (Esc는 창을 닫는 데 쓴다).
            if (event.isSystem() || keyCode == KeyEvent.KEYCODE_ESCAPE
                    || event.getDevice() == null || event.getDevice().isVirtual()) {
                return false;
            }
            if (event.getAction() != KeyEvent.ACTION_DOWN) return true;
            if (event.getRepeatCount() > 0) return true;
            if (event.isPrintingKey() || keyCode == KeyEvent.KEYCODE_ENTER || keyCode == KeyEvent.KEYCODE_DEL
                    || keyCode == KeyEvent.KEYCODE_TAB) {
                message.setText(L.t("글자·스페이스·엔터·지우기·탭 키는 입력에 쓰이므로 지정할 수 없습니다. 다른 키를 누르세요.", "Letter, space, enter, delete and tab keys are used for typing and can't be assigned. Press another key."));
                return true;
            }
            if (keyCode == KeyEvent.KEYCODE_CAPS_LOCK || keyCode == KeyEvent.KEYCODE_NUM_LOCK
                    || keyCode == KeyEvent.KEYCODE_SCROLL_LOCK) {
                // 잠금 키는 누를 때마다 시스템의 잠금 상태도 함께 바뀌어, 영어로 바꾸면 대문자로 입력되는 일이 생긴다.
                message.setText(L.t("잠금 키(Caps Lock 등)는 잠금 상태가 함께 바뀌어 지정할 수 없습니다. 다른 키를 누르세요.", "Lock keys (such as Caps Lock) can't be assigned because their lock state would change too. Press another key."));
                return true;
            }
            int scan = keyCode == KeyEvent.KEYCODE_UNKNOWN ? event.getScanCode() : 0;
            if (keyCode == KeyEvent.KEYCODE_UNKNOWN && scan == 0) {
                message.setText(L.t("이 키는 알아볼 수 없습니다. 다른 키를 누르세요.", "This key can't be recognized. Press another key."));
                return true;
            }
            saveHardwareLangKey(valueView, keyCode, scan);
            dialog.dismiss();
            return true;
        });
        dialog.show();
    }

    private void saveHardwareLangKey(TextView valueView, int keyCode, int scanCode) {
        prefs.raw().edit().putInt(Prefs.HW_CUSTOM_KEY, keyCode).putInt(Prefs.HW_CUSTOM_SCAN, scanCode).apply();
        valueView.setText(hwKeyName(keyCode, scanCode));
    }

    /** 직접 지정한 한·영 키의 이름 (설정 화면에 보인다). */
    private static String hwKeyName(int keyCode, int scanCode) {
        switch (keyCode) {
            case 0: return scanCode != 0 ? L.t("키 ", "Key ") + scanCode : L.t("없음", "None");
            case KeyEvent.KEYCODE_CTRL_RIGHT: return L.t("오른쪽 Ctrl", "Right Ctrl");
            case KeyEvent.KEYCODE_CTRL_LEFT: return L.t("왼쪽 Ctrl", "Left Ctrl");
            case KeyEvent.KEYCODE_ALT_RIGHT: return L.t("오른쪽 Alt", "Right Alt");
            case KeyEvent.KEYCODE_ALT_LEFT: return L.t("왼쪽 Alt", "Left Alt");
            case KeyEvent.KEYCODE_SHIFT_RIGHT: return L.t("오른쪽 Shift", "Right Shift");
            case KeyEvent.KEYCODE_SHIFT_LEFT: return L.t("왼쪽 Shift", "Left Shift");
            case KeyEvent.KEYCODE_META_RIGHT: return L.t("오른쪽 Meta", "Right Meta");
            case KeyEvent.KEYCODE_META_LEFT: return L.t("왼쪽 Meta", "Left Meta");
            case KeyEvent.KEYCODE_LANGUAGE_SWITCH: return L.t("언어 전환 키", "Language switch key");
            default:
                String name = KeyEvent.keyCodeToString(keyCode);
                return name.startsWith("KEYCODE_") ? name.substring(8) : name;
        }
    }

    /**
     * 지금 화면이 폴더블 내부 화면이면 그 방향의 프로필 (폴더블 내부 화면 세로·가로), 아니면 null (폴더블 기기의 외부 화면 등).
     * 화면의 짧은 쪽 폭이 '폴더블 내부 화면 판정 기준' 이상이면 폴더블 내부 화면이다 (키보드가 고르는 규칙과 같다).
     */
    private String largeProfileNow() {
        android.content.res.Configuration cfg = getResources().getConfiguration();
        if (cfg.smallestScreenWidthDp < prefs.largeScreenDp()) return null;
        return cfg.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
                ? Prefs.PROFILE_LARGE_LANDSCAPE : Prefs.PROFILE_LARGE_PORTRAIT;
    }

    /**
     * 폴더블 내부 화면 키보드 사용자화: 지금 화면 방향(폴더블 내부 화면 세로·가로)의 설정을 보여 준다. 기기를 돌리면 다른 방향의 설정이 보인다.
     * 분리 키보드 켜기와 자·모음 분리 배열은 세로·가로가 함께 쓴다. 영역 설정은 아래 미리보기에서 영역을 눌러 연다.
     * 폴더블 내부 화면이 아니면 안내 문구와 폴더블 내부 화면 판정 기준만 둔다.
     */
    private void buildLargeArea() {
        if (harvesting) {
            // 설정 검색: 두 방향의 항목을 모두 모은다.
            addProfileSplit(Prefs.PROFILE_LARGE_PORTRAIT, L.t("폴더블 내부 화면", "Inner screen"));
            for (String p : new String[]{Prefs.PROFILE_LARGE_PORTRAIT, Prefs.PROFILE_LARGE_LANDSCAPE}) {
                addOneHandWidth(p, L.t("폴더블 내부 화면에서", "On the inner screen"));
                harvestProfileAreas(p);
            }
            addLargeScreenThreshold();
            return;
        }
        String profile = largeProfileNow();
        if (profile == null) {
            LinearLayout c = card();
            c.addView(note(L.t("이 설정은 폴더블 내부 화면에서만 보입니다. 폴더블 내부 화면으로 전환하거나 폴더블 내부 화면 판정 기준을 조절하세요.", "These settings appear only on the inner screen of a foldable. Switch to the inner screen or adjust the inner screen threshold.")));
            absentAll = new java.lang.ref.WeakReference<>(c);   // 검색으로 이 화면의 항목을 찾아 들어오면 이 안내를 깜빡인다
            addLargeScreenThreshold();
            return;
        }
        boolean land = Prefs.PROFILE_LARGE_LANDSCAPE.equals(profile);
        String when = land ? L.t("폴더블 내부 화면을 가로로 들었을 때", "When the inner screen is held sideways") : L.t("폴더블 내부 화면을 세로로 들었을 때", "When the inner screen is held upright");
        addProfilePreview(profile);
        String now = land ? "가로" : "세로", other = land ? "세로" : "가로";
        LinearLayout intro = card();
        intro.addView(note(L.t("폴더블 내부 화면을 " + now + "로 들었을 때 키보드 모양을 정합니다. " + other + "일 때의 설정은 기기를 " + other + "로 돌리면 보입니다.\n"
                + "분리 키보드 사용 유무는 세로와 가로가 공유하지만, 나머지 설정은 세로·가로가 각각 별도로 설정할 수 있습니다.\n"
                + "여기에서 설정이 불가능한 항목은 '키보드 사용자화'의 설정과 공유합니다.",
                "Sets the keyboard look for when the inner screen is held " + (land ? "sideways" : "upright")
                + ". To see the settings for " + (land ? "portrait" : "landscape") + ", rotate the device.\n"
                + "Portrait and landscape share whether the split keyboard is on, but everything else is set separately for each.\n"
                + "Anything you can't set here is shared with 'Customize keyboard'.")));
        addProfileSplit(profile, L.t("폴더블 내부 화면", "Inner screen"));
        // 키 글자 크기와 여백은 미리보기의 키보드 전체 설정 창에 있다.
        addOneHandWidth(profile, when);
        addLargeScreenThreshold();
    }

    /**
     * '폴더블 내부 화면 판정 기준' 섹션. 슬라이더에서 손을 뗐을 때 이 화면이 폴더블 내부 화면인지(또는 그 방향이) 바뀌었으면
     * 화면을 다시 만들어 안내 문구 ↔ 설정을 바꿔 보여 준다.
     */
    private void addLargeScreenThreshold() {
        LinearLayout box = section(L.t("폴더블 내부 화면 판정 기준", "Inner screen threshold"));
        SettingsWidgets.Slider sl = sliderRow(L.t("폴더블 내부 화면 판정 기준", "Inner screen threshold"), Prefs.LARGE_SCREEN_DP, prefs.largeScreenDp(),
                Prefs.LARGE_SCREEN_DP_MIN, Prefs.LARGE_SCREEN_DP_MAX, 20, "dp", null);
        if (!harvesting) {
            String builtFor = largeProfileNow();
            sl.onRelease(() -> {
                if (!java.util.Objects.equals(builtFor, largeProfileNow())) recreate();
            });
        }
        box.addView(sl.view);
        int nowDp = getResources().getConfiguration().smallestScreenWidthDp;
        box.addView(note(L.t("화면의 짧은 쪽 폭이 이 값(dp) 이상이면 폴더블 내부 화면으로 봅니다. 보통 휴대폰은 360~480dp, "
                + "폴더블 기기의 내부 화면은 600dp 이상입니다. 지금 화면의 짧은 쪽 폭은 " + nowDp + "dp입니다.",
                "A screen whose shorter side is at least this wide (dp) counts as a foldable inner screen. Phones are usually 360–480dp, "
                + "and foldable inner screens are 600dp or more. The shorter side of this screen is " + nowDp + "dp.")));
    }

    /**
     * '분리 키보드' 섹션 (키보드 전체): 분리 키보드 켜기.
     * 켜고 끄면 미리보기의 영역도 바뀐다 (글자 키 ↔ 왼쪽·오른쪽 묶음과 가운데 공간).
     */
    /** 미리보기 영역 창에서 분리 키보드를 켰을 때만 만드는 항목 (buildProfileArea). */
    private static String[] profileSplitItems() {
        return new String[]{L.t("분리 키보드", "Split keyboard"), L.t("왼쪽 묶음 폭", "Left block width"), L.t("왼쪽 묶음 가로 위치", "Left block position"), L.t("오른쪽 묶음 폭", "Right block width"),
            L.t("오른쪽 묶음 가로 위치", "Right block position"), L.t("묶음 좌우 따라가기", "Follow blocks"), L.t("스페이스바 잇기", "Join space bar"), L.t("하단 기능키 폭", "Bottom function key width")};
    }

    private void addProfileSplit(String profile, String name) {
        Prefs edit = prefs.profileEditView(profile);
        LinearLayout card = section(L.t("분리 키보드", "Split keyboard"));
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setVisibility(edit.profileSplit() || harvesting ? View.VISIBLE : View.GONE);
        // 좌·우 균형 배열·자·모음 분리 배열은 따로 정하지 않고 세로 모드 '한글 글자 키 설정'의 상세 배열 값을 따른다.
        String splitKey = edit.splitKey();
        View splitSwitch = toggle(L.t(name + " 분리 키보드", "Split keyboard on " + name.toLowerCase(java.util.Locale.ROOT)),
                L.t("자판을 반으로 나눠 엄지손가락만으로 타이핑을 편하게 합니다.", "Splits the keyboard in half to make typing with your thumbs easier."),
                edit.profileSplit(), on -> {
                    prefs.raw().edit().putBoolean(splitKey, on).apply();
                    Ui.setVisibleAnimated(box, on);
                    refreshLayoutPreview();
                });
        card.addView(splitSwitch);
        card.addView(box);
        if (!edit.profileSplit()) markAbsent(splitSwitch, profileSplitItems());
    }

    /** '키보드 모양' 섹션 (키보드 전체): 키 글자 크기. 가로 모드, 폴더블 내부 화면 세로·가로가 각각 따로 정한다 (정하기 전에는 세로 모드 값). */
    private void addProfileWhole(String profile, String when) {
        Prefs edit = prefs.profileEditView(profile);
        LinearLayout card = section(L.t("키보드 모양", "Keyboard look"));
        card.addView(profileSlider(L.t("키 글자 크기", "Key text size"), edit.textSizeKey(), edit::keyTextSize, Prefs.PROFILE_HEIGHT_MIN, 140, 5, "%"));
    }

    /**
     * '여백' 섹션: 키보드 전체의 상하좌우 여백. 가로 모드, 폴더블 내부 화면 세로·가로가 각각 따로 정한다 (정하기 전에는 세로 모드 값).
     * 미리보기의 키보드 전체 설정 페이지에서 키보드를 고르면 가장자리의 손잡이로도 바꿀 수 있다.
     */
    private void addProfilePads(String profile) {
        Prefs edit = prefs.profileEditView(profile);
        LinearLayout card = section(L.t("여백", "Padding"));
        card.addView(profileSlider(L.t("왼쪽 여백", "Left padding"), edit.padKey(Prefs.SIDE_LEFT), edit::padLeftDp, 0, 24, 1, "dp"));
        card.addView(profileSlider(L.t("오른쪽 여백", "Right padding"), edit.padKey(Prefs.SIDE_RIGHT), edit::padRightDp, 0, 24, 1, "dp"));
        card.addView(profileSlider(L.t("위쪽 여백", "Top padding"), edit.padKey(Prefs.SIDE_TOP), edit::padTopDp, 0, 24, 1, "dp"));
        card.addView(profileSlider(L.t("아래쪽 여백", "Bottom padding"), edit.padKey(Prefs.SIDE_BOTTOM), edit::padBottomDp, 0, 48, 1, "dp"));
        card.addView(note(L.t("키보드 전체 둘레의 여백입니다. 정하기 전에는 세로 모드('키보드 사용자화')의 값을 따릅니다. "
                + "미리보기의 키보드 전체 설정 페이지에서 키보드를 고르면 가장자리의 손잡이를 끌어서도 바꿀 수 있습니다.", "Padding around the whole keyboard. Until set, it follows portrait mode ('Customize keyboard'). You can also drag the edge handles after selecting the keyboard on the Whole keyboard page of the preview.")));
    }

    /** 미리보기의 키보드 전체 설정 창: 그 프로필이 따로 정하는 키보드 모양(키 글자 크기)과 여백. */
    private void buildProfileBoard(String profile) {
        addProfileWhole(profile, profileWhen(profile));
        addProfilePads(profile);
    }

    /** 프로필 설명에 쓰는 '언제' (예: 화면을 가로로 돌렸을 때). */
    private static String profileWhen(String profile) {
        if (Prefs.PROFILE_LARGE_LANDSCAPE.equals(profile)) return L.t("폴더블 내부 화면을 가로로 들었을 때", "When the inner screen is held sideways");
        if (Prefs.PROFILE_LARGE_PORTRAIT.equals(profile)) return L.t("폴더블 내부 화면을 세로로 들었을 때", "When the inner screen is held upright");
        return L.t("화면을 가로로 돌렸을 때", "When the screen is rotated sideways");
    }

    /** 폴더블 내부 화면 세로·가로의 한 손 모드 자판 폭 (정하기 전에는 세로·가로 모드의 값). */
    private void addOneHandWidth(String profile, String when) {
        Prefs edit = prefs.profileEditView(profile);
        String widthKey = edit.oneHandWidthKey();
        if (widthKey == null) return;
        LinearLayout oneHand = section(L.t("한 손 모드", "One-handed mode"));
        oneHand.addView(slider(L.t("자판 폭", "Keyboard width"), widthKey, edit.oneHandWidthForProfile(), 30, 90, 5, "%"));
        oneHand.addView(note(L.t(when + " 한 손 모드에서 쓰는 자판 폭(화면 폭의 %)입니다.",
                when + ", the keyboard width in one-handed mode (% of the screen width).")));
    }

    /** 설정 검색용으로 화면을 만들 때는 영역 설정 창의 항목도 목록에 넣어 찾을 수 있게 한다. */
    private void harvestProfileAreas(String profile) {
        if (!harvesting) return;
        harvestProfilePart(-1, () -> buildProfileBoard(profile));   // 키보드 전체 설정 창 (키보드 모양·여백)
        for (int a : new int[]{KeyboardPreview.AREA_TOOLBAR, KeyboardPreview.AREA_LETTERS,
                KeyboardPreview.AREA_MIDDLE, KeyboardPreview.AREA_FUNCTION}) {
            harvestProfilePart(a, () -> buildProfileArea(profile, a, true));
        }
    }

    /** 검색 목록을 모을 때 설정 창 하나를 담는 칸의 표시: 그 안의 항목은 그 창(PROFILE_PART)의 것으로 모은다. */
    private static final class ProfilePartTag {
        final int area;
        final String name;

        ProfilePartTag(int area, String name) {
            this.area = area;
            this.name = name;
        }
    }

    /** 설정 창 하나(area -1 = 키보드 전체)를 표시한 칸에 만든다. collectLabels가 그 칸의 항목을 창의 id로 모은다. */
    private void harvestProfilePart(int area, Runnable build) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setTag(new ProfilePartTag(area, area < 0 ? L.t("키보드 전체 설정", "Whole keyboard settings") : KeyboardPreview.profileAreaName(area)));
        list.addView(box, matchWrap());
        LinearLayout saved = list;
        list = box;
        try {
            build.run();
        } catch (RuntimeException ignored) {
            // 한 창을 못 만들어도 나머지 창은 모은다.
        } finally {
            list = saved;
        }
    }

    /**
     * 검색 결과로 가로 모드·폴더블 내부 화면 키보드 사용자화에 들어왔다: 미리보기에서 그 창(키보드 전체·영역)을 열고 항목을 찾아 깜빡인다.
     * 폴더블 내부 화면은 지금 들고 있는 방향의 창을 연다 (세로·가로의 항목 이름은 같다). label이 창 이름이면 창만 연다.
     */
    private void openProfileTarget(String target, String label) {
        if (isFinishing() || isDestroyed() || profilePreview == null) return;
        String[] parts = target.split(":", 4);
        if (parts.length < 4) return;
        int area;
        try {
            area = Integer.parseInt(parts[2]);
        } catch (NumberFormatException e) {
            return;
        }
        if (area < 0) {
            profilePreview.setLayer(KeyboardPreview.LAYER_BOARD);
            onProfileTap(KeyboardPreview.Selection.board());
        } else {
            profilePreview.setLayer(KeyboardPreview.LAYER_AREA);
            onProfileTap(KeyboardPreview.Selection.area(area));
        }
        if (!panelOpen() || label.equals(parts[3]) || label.equals(parts[3] + L.t(" 설정", " settings"))) return;
        // 창이 올라온 뒤에 찾는다 (배치가 끝나야 위치를 안다).
        sheet.scroll().postDelayed(() -> {
            // 분리 키보드를 꺼 두어 창에 없는 항목이면 창을 닫고 목록의 분리 키보드 스위치를 깜빡인다.
            if (!highlightItem(sheet.scroll(), sheet.body(), label) && absentReason(label, list) != null) {
                sheet.close(() -> highlightItem(scrollView, list, label));
            }
        }, 450);
    }

    /**
     * 분리 키보드 한글 자판의 배열 토글. 단모음(8열)은 기본으로 양쪽 묶음을 4열씩 맞추고, '자·모음 분리 배열'을 켜면 자음과 모음이
     * 깔끔하게 나뉜다. NewSwipe 단모음은 원래대로 '좌·우 균형 배열'(꺼짐이 기본)이다.
     */
    private View splitArrangeToggle(Prefs p, String reachKey, Runnable after) {
        if (p.koreanD7()) {
            return toggle(L.t("좌·우 균형 배열", "Balanced left/right layout"), L.t("한글 자판의 왼쪽·오른쪽 묶음을 4열로 맞춥니다.", "Lines up the left and right blocks of the Korean keyboard in 4 columns."), p.splitReachColumn(), on -> {
                prefs.raw().edit().putBoolean(reachKey, on).apply();
                after.run();
            });
        }
        return toggle(L.t("자·모음 분리 배열", "Consonant/vowel split layout"),
                L.t("자음은 왼쪽, 모음은 오른쪽에 분리해 놓습니다.", "Places consonants on the left and vowels on the right."),
                p.splitSeparateJamo(), on -> {
                    prefs.raw().edit().putBoolean(reachKey, !on).apply();
                    after.run();
                });
    }

    /**
     * 키보드 사용자화 글자 키 창의 분리 키보드 항목: 왼쪽·오른쪽 묶음의 폭·가로 위치(미리보기의 묶음 경계 손잡이와 같이 움직인다)와,
     * 한글 자판이면 자·모음 분리 배열.
     */
    /** 글자 키 창의 분리 키보드 섹션을 담는 틀 (기호 자판 창이나 다른 창에서는 null). */
    private LinearLayout splitHolder;

    /**
     * 분리 키보드 섹션을 지금 설정으로 다시 채운다. 슬라이더의 설정 키가 분리 키보드 사용 여부에 따라 달라지므로
     * 켤 때마다 새로 만든다. 분리 키보드를 쓰지 않으면 비워 둔다.
     */
    private void fillSplitHolder() {
        if (splitHolder == null) return;
        splitHolder.removeAllViews();
        if (!prefs.portraitSplit()) return;
        LinearLayout saved = list;
        list = splitHolder;
        try {
            addEditorSplitBlocks();
        } finally {
            list = saved;
        }
    }

    /** 한글 글자 키 창의 '상세 배열' 줄 (자·모음 균형 배열·분리 키보드를 고른다). */
    private View detailRow;

    /** 한글 글자 키 창의 자·모음 균형 배열 섹션을 담는 틀 (다른 창에서는 null). */
    private LinearLayout balancedHolder;

    /**
     * 자·모음 균형 배열 섹션을 지금 설정으로 다시 채운다. 슬라이더의 설정 키가 한글 배열마다 달라 펼칠 때·배열을 바꿀 때 새로 만든다.
     * 자·모음 균형 배열을 쓰지 않으면 비워 둔다 (설정 검색 목록을 만들 때는 늘 넣는다).
     */
    private void fillBalancedHolder() {
        if (balancedHolder == null) return;
        balancedHolder.removeAllViews();
        if (detailLayout() != DETAIL_BALANCED && !harvesting) {
            if (detailRow != null) markAbsent(detailRow, balancedItems());
            return;
        }
        LinearLayout saved = list;
        list = balancedHolder;
        try {
            addEditorBalancedBlocks();
        } finally {
            list = saved;
        }
    }

    /**
     * 자·모음 균형 배열 섹션: 자음·모음 쪽의 키 폭·가로 위치. 아래 미리보기에서 글자 키 영역을 고르면
     * 자음·모음 쪽 왼쪽·오른쪽 가장자리의 손잡이로도 바꿀 수 있고, 손잡이를 끌면 슬라이더도 따라 움직인다.
     */
    /** 자·모음 균형 배열 섹션의 이름과 항목 (상세 배열에서 고르기 전에는 만들지 않는다). */
    private static String[] balancedItems() {
        return new String[]{L.t("자·모음 균형 배열", "Balanced consonant/vowel layout"), L.t("자음 키 폭", "Consonant key width"), L.t("자음 가로 위치", "Consonant position"), L.t("모음 키 폭", "Vowel key width"), L.t("모음 가로 위치", "Vowel position")};
    }

    private void addEditorBalancedBlocks() {
        LinearLayout c = section(balancedItems()[0]);
        c.addView(blockSlider(prefs, L.t("자음 키 폭", "Consonant key width"), prefs.balancedConsonantWidthKey(), prefs::balancedConsonantWidth, prefs.balancedWidthMin(), prefs.balancedWidthMax(), "%", this::syncKeyPanel));
        c.addView(blockSlider(prefs, L.t("자음 가로 위치", "Consonant position"), prefs.balancedConsonantPosKey(), prefs::balancedConsonantPos, Prefs.BALANCED_POS_MIN, Prefs.BALANCED_POS_MAX, "", this::syncKeyPanel));
        c.addView(blockSlider(prefs, L.t("모음 키 폭", "Vowel key width"), prefs.balancedVowelWidthKey(), prefs::balancedVowelWidth, prefs.balancedWidthMin(), prefs.balancedWidthMax(), "%", this::syncKeyPanel));
        c.addView(blockSlider(prefs, L.t("모음 가로 위치", "Vowel position"), prefs.balancedVowelPosKey(), prefs::balancedVowelPos, Prefs.BALANCED_POS_MIN, Prefs.BALANCED_POS_MAX, "", this::syncKeyPanel));
    }

    private void addEditorSplitBlocks() {
        LinearLayout c = section(L.t("분리 키보드", "Split keyboard"));
        c.addView(blockSlider(prefs, L.t("왼쪽 묶음 폭", "Left block width"), prefs.balancedConsonantWidthKey(), prefs::balancedConsonantWidth, prefs.balancedWidthMin(), prefs.balancedWidthMax(), "%", this::syncKeyPanel));
        c.addView(blockSlider(prefs, L.t("왼쪽 묶음 가로 위치", "Left block position"), prefs.balancedConsonantPosKey(), prefs::balancedConsonantPos, Prefs.BALANCED_POS_MIN, Prefs.BALANCED_POS_MAX, "", this::syncKeyPanel));
        c.addView(blockSlider(prefs, L.t("오른쪽 묶음 폭", "Right block width"), prefs.balancedVowelWidthKey(), prefs::balancedVowelWidth, prefs.balancedWidthMin(), prefs.balancedWidthMax(), "%", this::syncKeyPanel));
        c.addView(blockSlider(prefs, L.t("오른쪽 묶음 가로 위치", "Right block position"), prefs.balancedVowelPosKey(), prefs::balancedVowelPos, Prefs.BALANCED_POS_MIN, Prefs.BALANCED_POS_MAX, "", this::syncKeyPanel));
    }

    /**
     * 영역 하나의 설정 (그 영역 창에 들어간다). split은 분리 키보드에만 있는 항목(스페이스바 잇기)을 넣을지.
     * 미리보기의 손잡이를 끌면 슬라이더도 함께 움직인다.
     */
    private void buildProfileArea(String profile, int area, boolean split) {
        Prefs edit = prefs.profileEditView(profile);
        // 세로 모드 분리 키보드: 높이는 '키보드 사용자화'의 값을 쓴다 (여기서는 분리 키보드에만 있는 항목만).
        boolean portraitProfile = Prefs.PROFILE_PORTRAIT.equals(profile);
        switch (area) {
            case KeyboardPreview.AREA_LETTERS: {
                // 분리 키보드: 왼쪽·오른쪽 묶음을 합친 글자 키 (세로 모드 '키보드 사용자화'의 글자 키 창과 같다).
                if (split) {
                    LinearLayout c = section(L.t("분리 키보드", "Split keyboard"));
                    c.addView(blockSlider(edit, L.t("왼쪽 묶음 폭", "Left block width"), edit.balancedConsonantWidthKey(), edit::balancedConsonantWidth, edit.balancedWidthMin(), edit.balancedWidthMax(), "%", k -> refreshLayoutPreview()));
                    c.addView(blockSlider(edit, L.t("왼쪽 묶음 가로 위치", "Left block position"), edit.balancedConsonantPosKey(), edit::balancedConsonantPos, Prefs.BALANCED_POS_MIN, Prefs.BALANCED_POS_MAX, "", k -> refreshLayoutPreview()));
                    c.addView(blockSlider(edit, L.t("오른쪽 묶음 폭", "Right block width"), edit.balancedVowelWidthKey(), edit::balancedVowelWidth, edit.balancedWidthMin(), edit.balancedWidthMax(), "%", k -> refreshLayoutPreview()));
                    c.addView(blockSlider(edit, L.t("오른쪽 묶음 가로 위치", "Right block position"), edit.balancedVowelPosKey(), edit::balancedVowelPos, Prefs.BALANCED_POS_MIN, Prefs.BALANCED_POS_MAX, "", k -> refreshLayoutPreview()));
                }
                if (!portraitProfile) addProfileLetterHeight(edit);
                break;
            }
            case KeyboardPreview.AREA_TOOLBAR: {
                LinearLayout c = section(portraitProfile ? L.t("분리 키보드", "Split keyboard") : L.t("도구 막대", "Toolbar"));
                if (!portraitProfile) {
                    c.addView(profileSlider(L.t("도구 막대 높이", "Toolbar height"), edit.toolbarHeightKey(), edit::toolbarHeight, 70, 150, 5, "%"));
                    c.addView(note(L.t("도구 막대(버튼 줄)의 높이로, 기본 높이에 대한 비율입니다. 정하기 전에는 세로 모드의 값을 "
                            + "따릅니다. 미리보기에서 고른 영역 위 가장자리의 손잡이를 위·아래로 끌어도 바뀝니다.", "Height of the toolbar (button row), relative to the default height. Until set, it follows portrait mode. You can also drag the handle on the top edge of the area selected in the preview up or down.")));
                }
                // '묶음 좌우 따라가기'는 분리 키보드를 켰을 때만 (세로 모드 분리 키보드 창은 늘 분리 키보드다).
                if (split) {
                    String followKey = edit.toolbarFollowKey();
                    c.addView(toggle(L.t("묶음 좌우 따라가기", "Follow blocks"),
                            L.t("분리 키보드에서 도구 막대의 양 끝이 왼쪽 묶음의 왼쪽 끝과 오른쪽 묶음의 오른쪽 끝을 따라갑니다. "
                                    + "끄면 도구 막대는 키보드 전체 폭을 씁니다.", "On the split keyboard, the toolbar's ends follow the outer edges of the left and right blocks. When off, the toolbar uses the full keyboard width."),
                            edit.getRawFollow(), v -> {
                                prefs.raw().edit().putBoolean(followKey, v).apply();
                                refreshLayoutPreview();
                            }));
                }
                if (!portraitProfile) addProfileAreaPads(edit, KeyboardPreview.AREA_TOOLBAR);   // 맨 아래
                break;
            }
            case KeyboardPreview.AREA_MIDDLE: {
                LinearLayout c = section(portraitProfile ? L.t("가운데 공간", "Center gap") : L.t("분리 키보드 기능", "Split keyboard actions"));
                // 도구 막대처럼 네 방향마다 밀 때 실행할 기능을 정한다 (기본은 모두 '커서 자유 이동').
                KeyEditPanel gapPanel = new KeyEditPanel(this, edit, null);
                c.addView(gapPanel.standalonePress(com.alternative_studios.newswipe.keyboard.GapSwipes.SPEC,
                        com.alternative_studios.newswipe.keyboard.GapSwipes.pressSlot(edit.gapSlot())));
                c.addView(gapPanel.standalonePad(
                        com.alternative_studios.newswipe.keyboard.GapSwipes.SPEC, edit.gapSlot(), L.t("가운데 공간", "Center gap")));
                c.addView(note(L.t("'없음'으로 둔 방향으로는 밀어도 아무 일도 하지 않습니다. "
                        + "'커서 자유 이동'을 정한 방향으로 밀면 커서가 움직입니다 (좌우는 글자, 위아래는 줄 단위).", "Swiping in a direction set to 'None' does nothing. Swipe in a direction set to 'Free cursor' to move the cursor (by character left and right, by line up and down).")));
                c.addView(slider(L.t("커서 이동 속도", "Cursor speed"), edit.splitGapSpeedKey(), edit.splitGapSpeed(), 50, 300, 10, "%"));
                break;
            }
            case KeyboardPreview.AREA_FUNCTION: {
                LinearLayout c = section(portraitProfile ? L.t("분리 키보드", "Split keyboard") : L.t("기능키", "Function keys"));
                if (!portraitProfile) {
                    c.addView(profileSlider(L.t("기능키 높이", "Function key height"), edit.fnRowHeightKey(), edit::fnRowHeight,
                            Prefs.FN_ROW_HEIGHT_MIN, Prefs.FN_ROW_HEIGHT_MAX, 5, "%"));
                    c.addView(note(L.t("맨 아래 줄(스페이스바·기능키)의 높이로, 일반 키 높이에 대한 비율입니다. 정하기 전에는 세로 모드의 값을 "
                            + "따릅니다. 미리보기에서 고른 영역 위 가장자리의 손잡이를 위·아래로 끌어도 바뀝니다.", "Height of the bottom row (space bar and function keys), relative to the normal key height. Until set, it follows portrait mode. You can also drag the handle on the top edge of the area selected in the preview up or down.")));
                }
                if (!split) {
                    if (!portraitProfile) addProfileAreaPads(edit, KeyboardPreview.AREA_FUNCTION);
                    break;
                }
                String fnFollowKey = edit.fnFollowKey();
                c.addView(toggle(L.t("묶음 좌우 따라가기", "Follow blocks"),
                        L.t("분리 키보드에서 기능키 줄의 양 끝이 왼쪽 묶음의 왼쪽 끝과 오른쪽 묶음의 오른쪽 끝을 따라갑니다. "
                                + "끄면 기능키 줄은 키보드 전체 폭을 씁니다.", "On the split keyboard, the function key row's ends follow the outer edges of the left and right blocks. When off, the function key row uses the full keyboard width."),
                        edit.getRawFnFollow(), v -> {
                            prefs.raw().edit().putBoolean(fnFollowKey, v).apply();
                            refreshLayoutPreview();
                        }));
                // 스페이스바 잇기 (분리 키보드): 이었을 때만 하단 기능키 폭을 정한다.
                View fnWidth = subGroup(
                        profileSlider(L.t("하단 기능키 폭", "Bottom function key width"), edit.splitFnWidthKey(), edit::splitFnWidthSetting, 50, 200, 5, "%"),
                        note(L.t("스페이스바·Shift·⌫를 뺀 맨 아래 줄 키(기호 키·쉼표·지구본·온점·엔터)의 폭입니다. "
                                + "줄이거나 늘린 만큼 스페이스바가 늘거나 줄어듭니다.", "Width of the bottom row keys other than space, Shift and ⌫ (symbol key, comma, globe, period, enter). The space bar shrinks or grows to match.")));
                fnWidth.setVisibility(edit.splitSpaceJoin() ? View.VISIBLE : View.GONE);
                String joinKey = edit.splitSpaceJoinKey();
                c.addView(toggle(L.t("스페이스바 잇기", "Join space bar"),
                        L.t("양쪽에 걸친 스페이스바를 이어 붙입니다.", "Joins the space bar halves across both sides."),
                        edit.splitSpaceJoin(), on -> {
                            prefs.raw().edit().putBoolean(joinKey, on).apply();
                            Ui.setVisibleAnimated(fnWidth, on);
                            refreshLayoutPreview();
                        }));
                c.addView(fnWidth);
                if (!portraitProfile) addProfileAreaPads(edit, KeyboardPreview.AREA_FUNCTION);   // 맨 아래
                break;
            }
            default:
                break;
        }
    }

    /**
     * 가로 모드·폴더블 내부 화면의 영역(도구 막대·기능키) '여백' 섹션: 상하좌우 슬라이더. 세로 모드와 따로 정하고,
     * 정하기 전에는 세로 모드('키보드 사용자화')의 영역 여백을 따른다. 영역 창의 맨 아래에 둔다.
     */
    private void addProfileAreaPads(Prefs edit, int area) {
        LinearLayout c = section(L.t("여백", "Padding"));
        for (View v : new View[]{
                profileSlider(L.t("왼쪽 여백", "Left padding"), edit.profileAreaPadKey(area, Prefs.SIDE_LEFT), () -> edit.profileAreaPadDp(area, Prefs.SIDE_LEFT), 0, Prefs.AREA_PAD_MAX, 1, "dp"),
                profileSlider(L.t("오른쪽 여백", "Right padding"), edit.profileAreaPadKey(area, Prefs.SIDE_RIGHT), () -> edit.profileAreaPadDp(area, Prefs.SIDE_RIGHT), 0, Prefs.AREA_PAD_MAX, 1, "dp"),
                profileSlider(L.t("위쪽 여백", "Top padding"), edit.profileAreaPadKey(area, Prefs.SIDE_TOP), () -> edit.profileAreaPadDp(area, Prefs.SIDE_TOP), 0, Prefs.AREA_PAD_MAX, 1, "dp"),
                profileSlider(L.t("아래쪽 여백", "Bottom padding"), edit.profileAreaPadKey(area, Prefs.SIDE_BOTTOM), () -> edit.profileAreaPadDp(area, Prefs.SIDE_BOTTOM), 0, Prefs.AREA_PAD_MAX, 1, "dp"),
                note(L.t("키보드 전체 여백에 더해지는 영역 여백입니다. 정하기 전에는 세로 모드('키보드 사용자화')의 영역 여백을 따릅니다.", "Area padding added to the whole keyboard padding. Until set, it follows the area padding of portrait mode ('Customize keyboard')."))}) {
            c.addView(v);
        }
    }

    /** 글자 키 높이 (글자 키·두 묶음 창이 함께 쓴다). */
    private void addProfileLetterHeight(Prefs edit) {
        LinearLayout c = section(L.t("글자 키", "Letter keys"));
        c.addView(profileSlider(L.t("글자 키 높이", "Letter key height"), edit.heightKey(), edit::keyboardHeight, Prefs.PROFILE_HEIGHT_MIN, 140, 5, "%"));
        c.addView(note(L.t("정하기 전에는 세로 모드('키보드 사용자화')의 값을 따릅니다. 미리보기에서 고른 영역 위 가장자리의 손잡이를 위·아래로 끌어도 바뀝니다.", "Until set, it follows portrait mode ('Customize keyboard'). You can also drag the handle on the top edge of the area selected in the preview up or down.")));
    }

    // ---------------------------------------------------------------- 한 손 모드

    /** 한 손 모드 화면 아래의 미리보기 (손잡이로 자판 폭·키 높이·세로 위치를 바꾼다). 다른 화면에서는 null. */
    private OneHandPreview oneHandPreview;

    /**
     * 한 손 모드: 폴더블 내부 화면 키보드 사용자화처럼 실제 키보드와 같은 미리보기를 화면 아래에 붙이고,
     * 그 손잡이(자판 오른쪽 가장자리 = 자판 폭, 위 가장자리 = 키 높이, 아래 가장자리 = 세로 위치)로 값을 바꾼다.
     * 목록의 슬라이더도 같은 값을 바꾸고, 손잡이를 끌면 슬라이더도 따라 움직인다.
     */
    private void buildOneHand() {
        if (!harvesting) {
            oneHandPreview = new OneHandPreview(this, prefs, this::syncSizeSliders);
            pinnedBottom.addView(oneHandPreview, matchWrap());
            pinnedBottom.setVisibility(View.VISIBLE);
        }
        LinearLayout intro = card();
        intro.addView(note(L.t("자판과 도구 막대가 한쪽으로 쏠려 한 손으로 편하게 입력할 수 있습니다.\n"
                + "도구 막대의 한 손 모드 버튼으로 켭니다. 버튼을 왼쪽·오른쪽으로 밀면 그쪽으로 쏠린 한 손 모드가 됩니다.\n"
                + "아래 미리보기에서 자판 오른쪽 가장자리의 손잡이를 좌우로 끌면 자판 폭이, 위 가장자리의 손잡이를 끌면 키 높이가, "
                + "아래 가장자리의 손잡이를 위로 끌면 세로 위치가 바뀝니다.", "Moves the keyboard and toolbar to one side so you can type comfortably with one hand.\nTurn it on with the one-handed mode button on the toolbar. Swipe the button left or right to shift it to that side.\nIn the preview below, drag the handle on the right edge of the keyboard left or right to change the keyboard width, the top edge to change the key height, and the bottom edge up to change the vertical position.")));

        LinearLayout size = section(L.t("크기와 위치", "Size & position"));
        size.addView(oneHandSlider(L.t("자판 폭", "Keyboard width"), Prefs.ONE_HAND_WIDTH, prefs::oneHandWidth,
                OneHandPreview.WIDTH_MIN, OneHandPreview.WIDTH_MAX, 5, "%"));
        size.addView(slider(L.t("가로 모드 자판 폭", "Landscape keyboard width"), Prefs.ONE_HAND_WIDTH_LAND, prefs.oneHandWidthLand(), 30, 90, 5, "%"));
        size.addView(oneHandSlider(L.t("키 높이", "Key height"), Prefs.ONE_HAND_HEIGHT, prefs::oneHandHeight,
                OneHandPreview.HEIGHT_MIN, OneHandPreview.HEIGHT_MAX, 5, "%"));
        size.addView(oneHandSlider(L.t("세로 위치", "Vertical position"), Prefs.ONE_HAND_LIFT, prefs::oneHandLiftDp,
                OneHandPreview.LIFT_MIN, OneHandPreview.LIFT_MAX, 4, "dp"));
        size.addView(note(L.t("자판 폭은 화면 폭에 대한 비율이고, 키 높이는 보통 키 높이에 대한 비율입니다. "
                + "세로 위치는 자판을 화면 아래에서 띄우는 높이로, 값이 클수록 자판이 위로 올라갑니다.", "Keyboard width is relative to the screen width, and key height is relative to the normal key height. Vertical position is how high the keyboard sits above the bottom of the screen; higher values move it up.")));
    }

    /** 미리보기 손잡이로도 바뀌는 한 손 모드 슬라이더: 바꾸면 미리보기를 다시 그리고, 손잡이를 끌면 따라 움직인다. */
    private View oneHandSlider(String label, String key, java.util.function.IntSupplier current, int min, int max, int step,
                               String unit) {
        SettingsWidgets.Slider sl = sliderRow(label, key, current.getAsInt(), min, max, step, unit, this::refreshOneHandPreview);
        if (!harvesting) sliderSink.computeIfAbsent(key, k -> new java.util.ArrayList<>()).add(() -> sl.set(current.getAsInt()));
        return sl.view;
    }

    private void refreshOneHandPreview() {
        if (oneHandPreview != null) oneHandPreview.refresh();
    }

    /** 이 화면의 자판 미리보기와 각각이 그리는 프로필 (null = 세로 모드의 기본 값). */
    private final java.util.Map<com.alternative_studios.newswipe.keyboard.KeyboardView, String> layoutPreviews =
            new java.util.LinkedHashMap<>();

    private void refreshLayoutPreview() {
        if (profilePreview != null) {
            profilePreview.refresh();
            // 분리 키보드를 켜고 끄면 고른 영역이 없어질 수 있다 (글자 키 ↔ 두 묶음): 그 창을 닫는다.
            if (panelOpen() && profilePreview.selection() == null) closeKeyPanel();
        }
        for (java.util.Map.Entry<com.alternative_studios.newswipe.keyboard.KeyboardView, String> e : layoutPreviews.entrySet()) {
            refreshLayoutPreview(e.getKey(), previewPrefs(e.getValue()), isLandscapeProfile(e.getValue()));
        }
    }

    /**
     * 미리보기가 읽는 설정 보기: 프로필이 있으면 그 프로필의 편집 보기. 그 프로필의 분리 키보드를 껐으면
     * 실제 키보드처럼 나뉘지 않은 자판을 그리는 보통 보기.
     */
    private Prefs previewPrefs(String profile) {
        if (profile == null) return prefs;
        Prefs edit = prefs.profileEditView(profile);
        return edit.profileSplit() ? edit : prefs.profileView(profile);
    }

    /** 미리보기 하나를 지금 설정으로 다시 그린다: 자판 배열과 키 높이·글자 크기 (배열은 한 번만 만든다). */
    private void refreshLayoutPreview(com.alternative_studios.newswipe.keyboard.KeyboardView view, Prefs p, boolean landscape) {
        if (view == null) return;
        com.alternative_studios.newswipe.keyboard.KeyboardLayout l =
                com.alternative_studios.newswipe.keyboard.KeyboardLayout.korean(p);
        view.setGridColors(p.gridColors());
        applyPreviewSize(view, p, l, landscape);
        view.setLayout(l);
    }

    private static boolean isLandscapeProfile(String profile) {
        return Prefs.PROFILE_LANDSCAPE.equals(profile) || Prefs.PROFILE_LARGE_LANDSCAPE.equals(profile);
    }

    /**
     * 미리보기의 키 높이와 글자 크기를 설정에 맞춘다. 가로 방향 미리보기는 키가 낮아진 만큼 글자도 작게 그려
     * 키 안에 들어가게 한다.
     */
    private void applyPreviewSize(com.alternative_studios.newswipe.keyboard.KeyboardView view, Prefs p,
                                  com.alternative_studios.newswipe.keyboard.KeyboardLayout l, boolean landscape) {
        float scale = landscape ? LANDSCAPE_PREVIEW_SCALE : 1f;
        view.configure(p.longPressMs(), p.swipeThresholdDp(), false,
                Math.round(p.keyTextSize() * scale));
        int pad = Ui.dp(this, 4);
        view.setKeyboardHeight(Math.round(Ui.dp(this, 54) * l.totalHeight() * p.keyboardHeight() / 100f * scale) + 2 * pad);
    }

    /** 가로 방향 미리보기(가로 모드, 폴더블 내부 화면 가로)의 키 높이·글자 크기 비율. */
    private static final float LANDSCAPE_PREVIEW_SCALE = 0.65f;

    private View layoutPreview() { return layoutPreview(null); }

    /**
     * 지금 설정으로 만든 한글 자판을 실제 키보드 뷰로 그린다. 눌러도 반응하지 않는다.
     * profile이 있으면 그 프로필의 키 폭·위치로 그리고, 가로 방향이면 가로로 넓은 자판처럼 키 높이와 글자 크기를 줄인다.
     */
    private View layoutPreview(String profile) {
        if (harvesting) return new View(this);   // 설정 검색용으로 글자만 모을 때는 무거운 미리보기를 만들지 않는다
        Prefs prefs = previewPrefs(profile);
        boolean landscape = Prefs.PROFILE_LANDSCAPE.equals(profile) || Prefs.PROFILE_LARGE_LANDSCAPE.equals(profile);
        com.alternative_studios.newswipe.keyboard.KeyboardTheme kt =
                com.alternative_studios.newswipe.keyboard.KeyboardTheme.of(this);
        com.alternative_studios.newswipe.keyboard.KeyboardView preview =
                new com.alternative_studios.newswipe.keyboard.KeyboardView(this);
        preview.setTheme(kt);
        // 가로 모드 미리보기는 키가 낮아진 만큼 글자도 작게 그려 키 안에 들어가게 한다.
        preview.configure(prefs.longPressMs(), prefs.swipeThresholdDp(), false, Math.round(prefs.keyTextSize() * (landscape ? LANDSCAPE_PREVIEW_SCALE : 1f)));
        preview.setKeyShadow(prefs.keyShadow(), prefs.keyShadowStrength());
        preview.setKeyRadius(Ui.dp(this, prefs.keyRadiusDp()));
        preview.setGridColors(prefs.gridColors());
        preview.setPopupHints(!prefs.popupHintHidden(), prefs.popupHintPosition());
        preview.setDubeolsikHint(prefs.dubeolsikHintShown());
        preview.setNumberHintHidden(prefs.numberHintHidden());
        int p = Ui.dp(this, 4);
        preview.setKeyGaps(Ui.dp(this, prefs.keyGapXDp()), Ui.dp(this, prefs.keyGapYDp()));
        preview.setInsets(Ui.dp(this, prefs.padLeftDp()), p, Ui.dp(this, prefs.padRightDp()), p);
        com.alternative_studios.newswipe.keyboard.KeyboardLayout l =
                com.alternative_studios.newswipe.keyboard.KeyboardLayout.korean(prefs);
        preview.setKeyboardHeight(Math.round(Ui.dp(this, 54) * l.totalHeight() * prefs.keyboardHeight() / 100f
                * (landscape ? LANDSCAPE_PREVIEW_SCALE : 1f)) + 2 * p);
        preview.setLayout(l);
        preview.setOnTouchListener((v, ev) -> true);
        preview.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
        preview.setOutlineProvider(new android.view.ViewOutlineProvider() {
            @Override
            public void getOutline(View view, android.graphics.Outline outline) {
                outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), Ui.dp(view.getContext(), 16));
            }
        });
        preview.setClipToOutline(true);
        layoutPreviews.put(preview, profile);
        LinearLayout.LayoutParams lp = matchWrap();
        lp.topMargin = Ui.dp(this, 12);
        lp.bottomMargin = Ui.dp(this, 4);
        preview.setLayoutParams(lp);
        return preview;
    }

    private static final int[] PALETTE = {
            0xFF2F62E8, 0xFF039BE5, 0xFF00897B, 0xFF188038, 0xFFFBC02D, 0xFFF57C00,
            0xFFD93025, 0xFFD81B60, 0xFF7E57C2, 0xFF6D4C41, 0xFF5F6368, 0xFF1D1E21,
    };

    private void buildTheme() {
        LinearLayout mode = section(L.t("화면 모드", "Display mode"));
        String[] modeValues = {Prefs.THEME_SYSTEM, Prefs.THEME_LIGHT, Prefs.THEME_DARK};
        mode.addView(choiceRow(new String[]{L.t("시스템", "System"), L.t("라이트", "Light"), L.t("다크", "Dark")}, indexOf(modeValues, prefs.themeMode()), i -> {
            prefs.raw().edit().putString(Prefs.THEME_MODE, modeValues[i]).apply();
            recreate();
        }));
        mode.addView(note(L.t("시스템을 고르면 기기의 라이트/다크 설정을 따릅니다. 설정 화면과 키보드에 모두 적용됩니다.", "System follows your device's light/dark setting. Applies to both the settings and the keyboard.")));

        LinearLayout accent = section(L.t("강조 색", "Accent color"));
        String[] accentValues = {Prefs.ACCENT_SYSTEM, Prefs.ACCENT_CUSTOM};
        accent.addView(choiceRow(new String[]{L.t("시스템", "System"), L.t("지정 색상", "Custom color")}, indexOf(accentValues, prefs.accentMode()), i -> {
            prefs.raw().edit().putString(Prefs.ACCENT_MODE, accentValues[i]).apply();
            recreate();
        }));
        if (Build.VERSION.SDK_INT < 31) {
            accent.addView(note(L.t("시스템 색은 Android 12 이상에서 기기의 색을 따릅니다. 이전 버전에서는 지정 색상이 쓰입니다.", "System color follows your device colors on Android 12 and later. Earlier versions use the custom color.")));
        } else {
            accent.addView(note(L.t("시스템을 고르면 기기의 강조 색(배경화면 색)을 따릅니다.", "System follows your device's accent color (wallpaper color).")));
        }
        if (Prefs.ACCENT_CUSTOM.equals(prefs.accentMode()) || Build.VERSION.SDK_INT < 31) {
            int current = prefs.accentColor();
            accent.addView(swatchRow(0, 6, current));
            accent.addView(swatchRow(6, 12, current));
            View hex = button(L.t("색 코드 직접 입력 (", "Enter color code (") + String.format("#%06X", current & 0xFFFFFF) + ")",
                    v -> askAccentHex(current));
            ((LinearLayout.LayoutParams) hex.getLayoutParams()).topMargin = Ui.dp(this, 16);   // 색 동그라미와 띄운다
            accent.addView(hex);
        }

        LinearLayout keyColors = section(L.t("키 색", "Key colors"));
        keyColors.addView(toggle(L.t("색 정렬", "Color alignment"),
                L.t("Shift 키와 ⌫를 글자 키 색으로, 스페이스바는 다른 기능키 색으로 바꿉니다.", "Gives Shift and ⌫ the letter key color, and the space bar the other function key color."),
                Prefs.GRID_COLORS, prefs.gridColors()));

        if (SHOW_NEUMORPHISM) {
            LinearLayout neu = section("Neumorphism");
            neu.addView(toggle(L.t("Neumorphism 디자인", "Neumorphism design"),
                    L.t("키와 도구 막대·클립보드·이모지 창의 버튼이 바탕에서 부드럽게 솟아오르고, 누르면 안으로 쑥 들어가는 모양으로 그립니다. "
                            + "켜면 키 그림자 대신 쓰입니다.", "Draws keys and the buttons on the toolbar, clipboard and emoji panels as if they rise softly from the background and sink in when pressed. Replaces key shadows when on."),
                    Prefs.NEUMORPHISM, prefs.neumorphism()));
        }
    }

    /** Neumorphism 디자인 토글을 테마 화면에 보일지 (잠시 숨겨야 할 때 false로). 숨겨도 기능과 저장된 값은 그대로다. */
    private static final boolean SHOW_NEUMORPHISM = true;

    private static int indexOf(String[] values, String v) {
        for (int i = 0; i < values.length; i++) if (values[i].equals(v)) return i;
        return 0;
    }

    private View choiceRow(String[] labels, int selected, java.util.function.IntConsumer onSelect) {
        return Ui.choiceRow(this, labels, selected, accentColor, onAccentColor, textColor, hintColor, onSelect);
    }

    /** 고르는 즉시 onSelect를 부르는 선택 줄 (화면을 다시 만들지 않고 그 자리에서 갱신할 때). */
    private LinearLayout choiceRowNow(String[] labels, int selected, java.util.function.IntConsumer onSelect) {
        return Ui.choiceRow(this, labels, selected, accentColor, onAccentColor, textColor, hintColor, 0, onSelect);
    }

    /** choiceRowNow에 더해, 이미 고른 칸을 다시 눌러도 onSelect를 부른다 (진동을 다시 느껴 볼 수 있게). */
    private LinearLayout choiceRowRepeat(String[] labels, int selected, java.util.function.IntConsumer onSelect) {
        return Ui.choiceRow(this, labels, selected, accentColor, onAccentColor, textColor, hintColor, 0, onSelect, onSelect);
    }

    private View swatchRow(int from, int to, int current) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams rlp = matchWrap();
        rlp.topMargin = Ui.dp(this, 6);
        row.setLayoutParams(rlp);
        for (int i = from; i < to; i++) {
            final int color = PALETTE[i];
            final boolean selected = (color & 0xFFFFFF) == (current & 0xFFFFFF);
            final int mark = Color.luminance(color) > 0.5 ? 0xFF1D1E21 : 0xFFFFFFFF;
            final int ring = hintColor;
            View sw = new View(this) {
                private final android.graphics.Paint p = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);

                @Override
                protected void onDraw(android.graphics.Canvas c) {
                    float cx = getWidth() / 2f, cy = getHeight() / 2f, r = Math.min(cx, cy) - Ui.dp(getContext(), 3);
                    p.setStyle(android.graphics.Paint.Style.FILL);
                    p.setColor(color);
                    c.drawCircle(cx, cy, r, p);
                    if (selected) {
                        p.setStyle(android.graphics.Paint.Style.STROKE);
                        p.setStrokeWidth(Ui.dp(getContext(), 2));
                        p.setStrokeCap(android.graphics.Paint.Cap.ROUND);
                        p.setStrokeJoin(android.graphics.Paint.Join.ROUND);
                        p.setColor(mark);
                        android.graphics.Path path = new android.graphics.Path();
                        path.moveTo(cx - r * 0.38f, cy + r * 0.02f);
                        path.lineTo(cx - r * 0.1f, cy + r * 0.3f);
                        path.lineTo(cx + r * 0.4f, cy - r * 0.28f);
                        c.drawPath(path, p);
                    } else if (Math.abs(color - cardColor) < 0x101010) {
                        p.setStyle(android.graphics.Paint.Style.STROKE);
                        p.setStrokeWidth(Ui.dp(getContext(), 1));
                        p.setColor(ring);
                        c.drawCircle(cx, cy, r, p);
                    }
                }
            };
            sw.setClickable(true);
            sw.setContentDescription(String.format("#%06X", color & 0xFFFFFF));
            sw.setOnClickListener(v -> {
                prefs.raw().edit().putInt(Prefs.ACCENT_COLOR, color).apply();
                recreate();
            });
            row.addView(sw, new LinearLayout.LayoutParams(0, Ui.dp(this, 48), 1f));
        }
        return row;
    }

    private void askAccentHex(int current) {
        EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        input.setText(String.format("#%06X", current & 0xFFFFFF));
        input.setSelection(input.getText().length());
        int pad = Ui.dp(this, 20);
        LinearLayout box = new LinearLayout(this);
        box.setPadding(pad, Ui.dp(this, 8), pad, 0);
        box.addView(input, matchWrap());
        AppTheme.dialogBuilder(this)
                .setTitle(L.t("강조 색 코드", "Accent color code"))
                .setMessage(L.t("#RRGGBB 형식으로 적어 주세요.", "Enter it as #RRGGBB."))
                .setView(box)
                .setPositiveButton(L.t("적용", "Apply"), (d, w) -> {
                    try {
                        String v = input.getText().toString().trim();
                        if (!v.startsWith("#")) v = "#" + v;
                        int color = Color.parseColor(v) | 0xFF000000;
                        prefs.raw().edit().putInt(Prefs.ACCENT_COLOR, color).apply();
                        d.dismiss();   // 대화상자를 닫고 다시 만들어야 창이 남지 않는다
                        recreate();
                    } catch (IllegalArgumentException e) {
                        Toast.makeText(this, L.t("색 코드를 읽을 수 없습니다", "Can't read the color code"), Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton(L.t("취소", "Cancel"), null)
                .show();
    }

    private void buildWords() {
        LinearLayout suggest = section(L.t("단어 추천", "Word suggestions"));
        View fullBar = subGroup(toggle(L.t("도구 막대 전체를 쓰기", "Use full toolbar"),
                L.t("추천이 나오는 동안 도구 막대 전체를 추천에 씁니다.", "Uses the whole toolbar for suggestions while they are shown."),
                Prefs.SUGGEST_FULL_BAR, prefs.suggestFullBar()),
                toggle(L.t("추천 단어 뒤에 공백 포함", "Add space after suggestions"),
                        L.t("입력된 추천 단어 뒤에 공백을 붙입니다. 끄면 공백 없이 단어만 입력됩니다.", "Adds a space after the suggestion you pick. When off, only the word is typed."),
                        Prefs.SUGGEST_SPACE, prefs.suggestSpace()),
                toggle(L.t("다음 단어 예측", "Next-word prediction"),
                        L.t("앞 단어 다음에 자주 오는 단어를 미리 보여 줍니다 (예: '좋은' 다음에 하루·아침). ", "Shows words that often come after the previous word (e.g. after '좋은': 하루, 아침). "),
                        Prefs.PREDICT_NEXT, prefs.predictNext()));
        suggest.addView(toggle(L.t("단어 추천", "Word suggestions"), L.t("입력 중에 예측되는 단어를 도구 막대에 보여 줍니다. ", "Shows predicted words on the toolbar as you type. "),
                Prefs.SUGGEST_WORDS, prefs.suggestWords(), fullBar));
        suggest.addView(fullBar);

        LinearLayout shortcut = section(L.t("단축어", "Text shortcuts"));
        View shortcutGroup = subGroup(button(L.t("단축어 사용자화", "Customize text shortcuts"), v -> startActivity(new Intent(this, ShortcutEditorActivity.class))));
        shortcut.addView(toggle(L.t("단축어 사용", "Use text shortcuts"), L.t("사용자가 정한 줄임말을 입력하면 정해 둔 문장을 추천란에 보여 줍니다 (예: ㅈㄱㅈ → 지금 가는 중)", "Type a shortcut you set to see its phrase in the suggestions (e.g. ㅈㄱㅈ → 지금 가는 중)"),
                Prefs.SHORTCUTS_ENABLED, prefs.shortcutsEnabled(), shortcutGroup));
        shortcut.addView(shortcutGroup);

        LinearLayout correct = section(L.t("자동 수정", "Autocorrect"));
        correct.addView(toggle(L.t("자동 수정", "Autocorrect"), L.t("스페이스바를 누를 때 오타로 추정되는 단어를 사전에 있는 단어로 바꿉니다. "
                + "바꾼 직후 ⌫를 누르면 되돌리고, 두 번 되돌리면 학습해서 더는 고치지 않습니다.", "When you press space, replaces a likely typo with a word from the dictionary. Press ⌫ right after to undo; undo twice and it learns the word and stops correcting it."),
                Prefs.AUTO_CORRECT, prefs.autoCorrect()));

        LinearLayout learn = section(L.t("입력한 단어 학습", "Learn typed words"));
        learn.addView(toggle(L.t("입력한 단어 학습", "Learn typed words"), L.t("내가 자주 입력하는 단어를 기억해 추천에 먼저 보여 주고 자동 수정에서 뺍니다. "
                + "단어 추천이나 자동 수정을 켰을 때만 동작합니다.", "Remembers words you type often, shows them first in suggestions, and keeps autocorrect from changing them. Works only when word suggestions or autocorrect is on."),
                Prefs.LEARN_WORDS, prefs.learnWords()));
        learn.addView(toggle(L.t("단어를 지울 때 확인", "Confirm before deleting words"), L.t("학습한 단어를 지울 때 한 번 더 확인합니다.", "Asks again before you delete a learned word."),
                Prefs.CONFIRM_LEARNED_DELETE, prefs.confirmLearnedDelete()));
        learn.addView(button(L.t("학습한 단어 보기", "View learned words"), v -> openSection(LEARNED)));
        learn.addView(button(L.t("학습하지 않는 단어 보기", "View blocked words"), v -> openSection(BLOCKED)));
        learn.addView(trashButton(L.t("학습한 단어 모두 지우기", "Delete all learned words"), v -> AppTheme.dialogBuilder(this)
                .setTitle(L.t("학습한 단어 모두 지우기", "Delete all learned words"))
                .setMessage(L.t("키보드가 기억한 단어와, 추천 순서에 쓰는 입력 기록(자주 쓴 단어·이어 쓴 단어), "
                        + "추천란에서 골라 자동 수정하게 된 단어를 모두 지웁니다. "
                        + "되돌릴 수 없습니다.", "Deletes the words the keyboard remembers, the typing history used to order suggestions (frequent words and word pairs), and words picked from suggestions for autocorrect. This can't be undone."))
                .setPositiveButton(L.t("모두 지우기", "Delete all"), (d, w) -> {
                    UserDictionary.shared(getFilesDir()).clear();
                    UsageHistory.shared(getFilesDir()).clear();
                    CorrectionMemory.shared(getFilesDir()).clear();
                    Toast.makeText(this, L.t("학습한 단어를 지웠습니다", "Learned words deleted"), Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton(L.t("취소", "Cancel"), null)
                .show()));

        // 자동 완성: 서비스(Autofill with Google, 삼성 패스 등)의 제안을 도구 막대 자리에 칩으로 (Android 11 이상).
        LinearLayout autofill = section(L.t("자동 완성", "Autofill"));
        if (Build.VERSION.SDK_INT >= 30) {
            autofill.addView(toggle(L.t("자동 완성 제안", "Autofill suggestions"),
                    L.t("자동 완성 서비스의 내용(예: 아이디와 비밀번호, 인증 코드)을 도구 막대에 보여 줍니다. ", "Shows content from your autofill service (e.g. usernames, passwords, verification codes) on the toolbar. "),
                    Prefs.AUTOFILL_INLINE, prefs.autofillInline()));
            autofill.addView(note(L.t("기기 설정에서 고른 자동 완성 서비스(Autofill with Google, 삼성 패스 등)를 사용합니다. ", "Uses the autofill service chosen in device settings (Autofill with Google, Samsung Pass, etc.). ")));
        } else {
            autofill.addView(note(L.t("Android 11 이상에서 쓸 수 있습니다. 이 기기에서는 앱 안에 뜨는 원래의 자동 완성 목록을 쓰세요.", "Available on Android 11 and later. On this device, use the app's own autofill list.")));
        }

        LinearLayout about = section(L.t("개인 정보", "Privacy"));
        about.addView(note(L.t("모든 처리는 기기 안에서만 하며 인터넷을 쓰지 않습니다. 비밀번호·이메일 입력란에서는 학습을 하지 않습니다.", "Everything is processed on your device, without the internet. Nothing is learned on password or email fields.")));

        buildDictionaryPack();
    }

    /**
     * 한국어 사전: 앱에 든 기본 사전 대신 쓸 사전 팩을 가져오거나 지운다. 팩은 사용자가 브라우저로 받은 zip 파일이며
     * (앱은 인터넷을 쓰지 않는다) 파일 고르기 창으로 가져온다.
     */
    private void buildDictionaryPack() {
        LinearLayout dict = section(L.t("한국어 사전", "Korean dictionary"));
        DictionaryPack.Info info = DictionaryPack.installed(getFilesDir());
        if (info == null) {
            dict.addView(note(L.t("NewSwipe에 포함된 기본 사전을 사용 중입니다. "
                    + "단어와 단어 쌍이 더 많은 사전 팩을 가져오면 추천이 더 잘 맞습니다.", "Using the built-in dictionary included with NewSwipe. Import a dictionary pack with more words and word pairs for better suggestions.")));
        } else {
            StringBuilder b = new StringBuilder();
            b.append(info.name);
            if (!info.version.isEmpty()) b.append(" ").append(info.version);
            b.append(L.t("\n단어 ", "\n")).append(String.format(java.util.Locale.ROOT, "%,d", info.words)).append(L.t("개", " words"));
            if (info.pairs > 0) b.append(L.t(" · 단어 쌍 ", " · ")).append(String.format(java.util.Locale.ROOT, "%,d", info.pairs)).append(L.t("개", " word pairs"));
            b.append(" · ").append(String.format(java.util.Locale.ROOT, "%.1f", info.bytes / 1048576.0)).append("MB");
            if (!info.description.isEmpty()) b.append("\n").append(info.description);
            if (!info.attribution.isEmpty() || !info.license.isEmpty()) {
                b.append(L.t("\n출처: ", "\nSource: ")).append(info.attribution);
                if (!info.license.isEmpty()) b.append(" (").append(info.license).append(")");
            }
            dict.addView(note(b.toString()));
        }
        dict.addView(button(L.t("사전 팩 받는 곳", "Get dictionary packs"), v -> openUrl(SOURCE_URL + "/releases?q=ko-standard&expanded=true")));   // 사전 팩 릴리스만 (검색은 태그가 아니라 제목·설명에서 찾는다)
        dict.addView(iconButton(L.t("사전 팩 가져오기", "Import dictionary pack"), Icons.IMPORT, v -> SettingsImport.pick(this, REQ_DICT)));
        if (info != null) {
            if (!info.licenseText.isEmpty()) {
                dict.addView(button(L.t("사전 팩의 라이선스 보기", "View dictionary pack license"), v -> AppTheme.dialogBuilder(this)
                        .setTitle(info.name)
                        .setMessage(info.licenseText)
                        .setPositiveButton(L.t("닫기", "Close"), null)
                        .show()));
            }
            dict.addView(trashButton(L.t("기본 사전으로 되돌리기", "Restore built-in dictionary"), v -> AppTheme.dialogBuilder(this)
                    .setTitle(L.t("기본 사전으로 되돌리기", "Restore built-in dictionary"))
                    .setMessage(L.t("가져온 사전 팩을 지우고 앱에 든 기본 사전을 씁니다. 학습한 단어와 입력 기록은 그대로 남습니다.", "Deletes the imported dictionary pack and uses the built-in dictionary. Learned words and typing history are kept."))
                    .setPositiveButton(L.t("되돌리기", "Reset"), (d, w) -> {
                        DictionaryPack.remove(getFilesDir());
                        Toast.makeText(this, L.t("기본 사전으로 되돌렸습니다", "Restored the built-in dictionary"), Toast.LENGTH_SHORT).show();
                        recreate();
                    })
                    .setNegativeButton(L.t("취소", "Cancel"), null)
                    .show()));
        }
        dict.addView(note(L.t("사전 팩마다 데이터의 출처와 라이선스가 다르니 가져오기 전에 확인하세요.", "Each dictionary pack has its own data source and license, so check them before importing.")));
    }

    /** 고른 사전 팩을 검사해 설치한다 (크기가 커서 따로 처리한다). */
    private void importDictionaryPack(android.net.Uri uri) {
        Toast.makeText(this, L.t("사전 팩을 확인하는 중입니다…", "Checking dictionary pack…"), Toast.LENGTH_SHORT).show();
        android.content.Context app = getApplicationContext();
        new Thread(() -> {
            String message;
            boolean ok = false;
            try (java.io.InputStream in = app.getContentResolver().openInputStream(uri)) {
                if (in == null) throw new java.io.IOException(L.t("파일을 열지 못했습니다", "Couldn't open the file"));
                DictionaryPack.Info info = DictionaryPack.install(in, app.getFilesDir());
                message = L.t("'" + info.name + "' 사전을 가져왔습니다", "Imported the '" + info.name + "' dictionary");
                ok = true;
            } catch (DictionaryPack.InvalidPackException e) {
                message = L.t("가져올 수 없는 사전 팩입니다: ", "Can't import this dictionary pack: ") + e.getMessage();
            } catch (Exception e) {
                message = L.t("사전 팩을 가져오지 못했습니다: ", "Couldn't import the dictionary pack: ") + e.getMessage();
            }
            String m = message;
            boolean done = ok;
            runOnUiThread(() -> {
                Toast.makeText(app, m, Toast.LENGTH_LONG).show();
                if (done && !isFinishing()) recreate();
            });
        }).start();
    }

    /** 학습한 단어·학습하지 않는 단어 화면의 검색어 (비어 있으면 모두 보인다). */
    private String wordQuery = "";

    /**
     * 처음 그릴 때 읽어 정렬해 둔 학습한 단어({단어, 횟수, 마지막 시각}, 많이 쓴 순)와 학습하지 않는 단어. 검색할 때는 파일을
     * 다시 읽거나 정렬하지 않고 이것을 거른다. 지우거나 되돌리면 여기서도 뺀다.
     */
    private java.util.List<String[]> learnedCache;
    private java.util.List<String> blockedCache;
    /** 단어 검색은 입력이 멈추고 이만큼 지나서 다시 그린다 (한글은 자모를 칠 때마다 검색어가 바뀐다). */
    private static final int WORD_SEARCH_DELAY_MS = 200;
    private final Runnable wordSearchRedraw = this::redrawWordList;

    /** 단어 검색: 검색어가 바뀌면 잠깐 기다렸다가 목록을 그 검색어로 다시 그린다. */
    private void onWordSearch(String raw) {
        String q = raw == null ? "" : raw.trim();
        if (q.equals(wordQuery)) {
            uiHandler.removeCallbacks(wordSearchRedraw);   // 바뀌었다가 되돌아왔으면 다시 그리지 않는다
            return;
        }
        pendingWordQuery = q;
        uiHandler.removeCallbacks(wordSearchRedraw);
        uiHandler.postDelayed(wordSearchRedraw, q.isEmpty() ? 0 : WORD_SEARCH_DELAY_MS);
    }

    private String pendingWordQuery = "";

    private void redrawWordList() {
        if (isFinishing() || isDestroyed() || pendingWordQuery.equals(wordQuery)) return;
        wordQuery = pendingWordQuery;
        String id = getIntent().getStringExtra(EXTRA_SECTION);
        list.removeAllViews();
        if (LEARNED.equals(id)) buildLearned();
        else if (BLOCKED.equals(id)) buildBlocked();
    }

    /** 검색 결과가 없을 때의 안내. */
    private void noWordMatch() {
        card().addView(note(L.t("'" + wordQuery + "'" + com.alternative_studios.newswipe.suggest.WordSuggester.objectParticle(wordQuery)
                + " 포함한 단어가 없습니다.", "No words contain '" + wordQuery + "'.")));
    }

    /** 한 번에 그리는 학습한 단어 줄 수. 더 있으면 '더 보기'로 이어서 그린다 (수천 줄을 한꺼번에 만들지 않도록). */
    private static final int LEARNED_PAGE = 200;
    /** 검색하는 동안 한 번에 그리는 줄 수 (칠 때마다 다시 그리므로 적게). */
    private static final int SEARCH_PAGE = 50;

    private int wordPage() {
        return wordQuery.isEmpty() ? LEARNED_PAGE : SEARCH_PAGE;
    }

    /** 학습한 단어 목록 (많이 쓴 순). 줄마다 오른쪽 휴지통 버튼으로 지운다. */
    private void buildLearned() {
        UserDictionary dict = UserDictionary.shared(getFilesDir());
        if (learnedCache == null) {
            java.util.List<String[]> read = dict.snapshot();
            // 문자열을 비교할 때마다 숫자로 풀지 않게 한 번만 푼다
            java.util.Map<String[], long[]> key = new java.util.IdentityHashMap<>();
            for (String[] w : read) key.put(w, new long[]{Long.parseLong(w[1]), Long.parseLong(w[2])});
            read.sort((a, b) -> {
                long[] ka = key.get(a), kb = key.get(b);
                int c = Long.compare(kb[0], ka[0]);
                return c != 0 ? c : Long.compare(kb[1], ka[1]);
            });
            learnedCache = read;
        }
        java.util.List<String[]> all = new java.util.ArrayList<>(learnedCache);
        if (all.isEmpty()) {
            card().addView(note(L.t("아직 학습한 단어가 없습니다. 단어 추천이나 자동 수정을 켜고 입력하면, "
                    + "자주 쓰는 단어를 기억해 여기에 보여 줍니다.", "No learned words yet. Turn on word suggestions or autocorrect and type, and the words you use often will appear here.")));
            return;
        }
        boolean searching = !wordQuery.isEmpty();
        if (searching) {
            all.removeIf(w -> !UserDictionary.matchesQuery(w[0], wordQuery));
            if (all.isEmpty()) {
                noWordMatch();
                return;
            }
        }
        // 두 번 이상 쓴 단어(실제로 쓰이는 것)와, 한 번만 쓴 후보를 나눈다.
        java.util.List<String[]> known = new java.util.ArrayList<>(), candidates = new java.util.ArrayList<>();
        for (String[] w : all) {
            (Integer.parseInt(w[1]) >= UserDictionary.KNOWN_COUNT ? known : candidates).add(w);
        }
        TextView summary = note("");
        card().addView(summary);
        int[] remaining = {known.size(), candidates.size()};
        Runnable updateSummary = () -> summary.setText(remaining[0] + remaining[1] == 0
                ? (searching ? L.t("찾은 단어를 모두 지웠습니다.", "Deleted all matching words.") : L.t("학습한 단어를 모두 지웠습니다.", "Deleted all learned words."))
                : searching
                ? L.t("'" + wordQuery + "' 검색 결과: 학습한 단어 " + remaining[0] + "개, 후보 " + remaining[1] + "개 (많이 쓴 순서)",
                        "Results for '" + wordQuery + "': " + remaining[0] + " learned, " + remaining[1] + " candidates (most used first)")
                : L.t("학습한 단어 " + remaining[0] + "개, 후보 " + remaining[1] + "개. 많이 쓴 순서입니다. "
                + "학습한 단어는 추천에 먼저 나오고 자동 수정에서 빠집니다.",
                        remaining[0] + " learned words, " + remaining[1] + " candidates, most used first. "
                + "Learned words come first in suggestions and are never autocorrected."));
        updateSummary.run();

        if (!known.isEmpty()) {
            LinearLayout words = learnedCard(L.t("학습한 단어", "Learned words"));
            addLearnedRows(words, known, 0, dict, remaining, 0, updateSummary);
        }
        if (!candidates.isEmpty() && searching) {
            // 검색 중에는 찾은 후보를 바로 보인다.
            addLearnedRows(learnedCard(L.t("후보 (한 번 입력한 단어)", "Candidates (typed once)")), candidates, 0, dict, remaining, 1, updateSummary);
        } else if (!candidates.isEmpty()) {
            // 후보는 실제로 쓰이지 않으므로 접어 둔다.
            LinearLayout cands = learnedCard(L.t("후보 (한 번 입력한 단어)", "Candidates (typed once)"));
            TextView open = new TextView(this);
            open.setText(L.t("후보 " + candidates.size() + "개 보기 · 한 번 더 입력하면 학습됩니다",
                    "Show " + candidates.size() + " candidates · Type one once more to learn it"));
            open.setTextColor(accentColor);
            open.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
            open.setGravity(Gravity.CENTER);
            open.setPadding(Ui.dp(this, 16), Ui.dp(this, 14), Ui.dp(this, 16), Ui.dp(this, 14));
            open.setBackground(Ui.ripple(hintColor & 0x00FFFFFF | 0x33000000, null, 0));
            open.setOnClickListener(v -> {
                cands.removeView(open);
                addLearnedRows(cands, candidates, 0, dict, remaining, 1, updateSummary);
            });
            cands.addView(open);
        }
    }

    private LinearLayout learnedCard(String title) {
        return menuCard(section(title));
    }

    /** slot: remaining에서 이 목록의 개수가 들어 있는 칸 (0 = 학습한 단어, 1 = 후보). */
    private void addLearnedRows(LinearLayout words, java.util.List<String[]> all, int from, UserDictionary dict,
                                int[] remaining, int slot, Runnable updateSummary) {
        int to = Math.min(all.size(), from + wordPage());
        for (int i = from; i < to; i++) {
            String[] w = all.get(i);
            if (words.getChildCount() > 0) words.addView(divider());
            words.addView(learnedRow(words, w[0], Integer.parseInt(w[1]), dict, remaining, slot, updateSummary));
        }
        if (to < all.size()) {
            TextView more = new TextView(this);
            more.setText(L.t("더 보기 (" + (all.size() - to) + "개 남음)", "Show more (" + (all.size() - to) + " left)"));
            more.setTextColor(accentColor);
            more.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
            more.setGravity(Gravity.CENTER);
            more.setPadding(0, Ui.dp(this, 14), 0, Ui.dp(this, 14));
            more.setBackground(Ui.ripple(hintColor & 0x00FFFFFF | 0x33000000, null, 0));
            more.setOnClickListener(v -> {
                words.removeView(more);
                addLearnedRows(words, all, to, dict, remaining, slot, updateSummary);
            });
            words.addView(divider());
            words.addView(more);
        }
    }

    private View learnedRow(LinearLayout words, String word, int count, UserDictionary dict,
                            int[] remaining, int slot, Runnable updateSummary) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(Ui.dp(this, 16), Ui.dp(this, 8), Ui.dp(this, 6), Ui.dp(this, 8));
        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        TextView t = new TextView(this);
        t.setText(word);
        t.setTextColor(textColor);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        texts.addView(t);
        TextView d = new TextView(this);
        d.setText(L.t(count + "회 입력", count == 1 ? "Typed once" : "Typed " + count + " times"));
        d.setTextColor(hintColor);
        d.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        texts.addView(d);
        row.addView(texts, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        IconButton trash = new IconButton(this, Icons.TRASH, hintColor, hintColor & 0x00FFFFFF | 0x33000000, L.t("'" + word + "' 지우기", "Delete '" + word + "'"));
        Runnable delete = () -> {
            boolean korean = word.codePoints().anyMatch(c -> c >= 0xAC00 && c <= 0xD7A3);
            dict.forget(word, korean);   // 지운 단어는 다시 학습하지 않는다
            if (learnedCache != null) learnedCache.removeIf(w -> w[0].equals(word));
            if (blockedCache != null && !blockedCache.contains(word)) blockedCache.add(word);
            if (korean) UsageHistory.shared(getFilesDir()).remove(word);
            // 줄과 그 위(첫 줄이면 아래)의 구분선을 함께 뗀다.
            int idx = words.indexOfChild(row);
            if (idx > 0) words.removeViewAt(idx - 1);
            else if (words.getChildCount() > 1) words.removeViewAt(1);
            words.removeView(row);
            // '더 보기'만 남았을 때 맨 위에 남는 구분선도 뗀다.
            if (words.getChildCount() > 0 && !(words.getChildAt(0) instanceof TextView)
                    && !(words.getChildAt(0) instanceof LinearLayout)) {
                words.removeViewAt(0);
            }
            if (words.getChildCount() == 0) {
                // 다 지웠으면 빈 카드와 그 제목을 숨긴다.
                words.setVisibility(View.GONE);
                int at = list.indexOfChild(words);
                if (at > 0) list.getChildAt(at - 1).setVisibility(View.GONE);
            }
            remaining[slot]--;
            updateSummary.run();
            Toast.makeText(this, L.t("'" + word + "'" + com.alternative_studios.newswipe.suggest.WordSuggester.objectParticle(word)
                    + " 지웠습니다. 다시 학습하지 않습니다", "Deleted '" + word + "'. It won't be learned again"), Toast.LENGTH_SHORT).show();
        };
        trash.setOnClickListener(v -> {
            if (prefs.confirmLearnedDelete()) confirmLearnedDelete(word, delete);
            else delete.run();
        });
        row.addView(trash, new LinearLayout.LayoutParams(Ui.dp(this, 44), Ui.dp(this, 44)));
        return row;
    }

    /**
     * 학습하지 않는 단어 목록 (사용자가 학습한 단어나 입력 기록을 지운 단어, 사전 순). 줄마다 오른쪽 버튼으로 목록에서 빼면
     * 다시 학습한다.
     */
    private void buildBlocked() {
        UserDictionary dict = UserDictionary.shared(getFilesDir());
        if (blockedCache == null) blockedCache = dict.blockedSnapshot();
        java.util.List<String> all = new java.util.ArrayList<>(blockedCache);
        if (all.isEmpty()) {
            card().addView(note(L.t("학습하지 않는 단어가 없습니다. 학습한 단어를 지우면(설정이나 추천을 길게 눌러서) "
                    + "그 단어는 다시 학습하지 않고 여기에 보입니다.", "No blocked words. When you delete a learned word (in settings, or by long-pressing a suggestion), it won't be learned again and appears here.")));
            return;
        }
        boolean searching = !wordQuery.isEmpty();
        if (searching) {
            all.removeIf(w -> !UserDictionary.matchesQuery(w, wordQuery));
            if (all.isEmpty()) {
                noWordMatch();
                return;
            }
        }
        TextView summary = note("");
        card().addView(summary);
        int[] remaining = {all.size()};
        Runnable updateSummary = () -> summary.setText(remaining[0] == 0
                ? (searching ? L.t("찾은 단어를 모두 목록에서 뺐습니다.", "Removed all matching words from the list.") : L.t("학습하지 않는 단어가 없습니다.", "No blocked words."))
                : searching
                ? L.t("'" + wordQuery + "' 검색 결과: 학습하지 않는 단어 " + remaining[0] + "개",
                        "Results for '" + wordQuery + "': " + remaining[0] + " blocked words")
                : L.t("학습하지 않는 단어 " + remaining[0] + "개. 지운 단어라 다시 입력해도 학습하지 않습니다. "
                + "되돌림 버튼을 누르면 목록에서 빼고 다시 학습합니다.",
                        remaining[0] + " blocked words. These were deleted, so they aren't learned even if you type them again. "
                + "Tap the restore button to remove a word from the list and learn it again."));
        updateSummary.run();
        LinearLayout words = learnedCard(L.t("학습하지 않는 단어", "Blocked words"));
        addBlockedRows(words, all, 0, dict, remaining, updateSummary);
    }

    private void addBlockedRows(LinearLayout words, java.util.List<String> all, int from, UserDictionary dict,
                                int[] remaining, Runnable updateSummary) {
        int to = Math.min(all.size(), from + wordPage());
        for (int i = from; i < to; i++) {
            if (words.getChildCount() > 0) words.addView(divider());
            words.addView(blockedRow(words, all.get(i), dict, remaining, updateSummary));
        }
        if (to < all.size()) {
            TextView more = new TextView(this);
            more.setText(L.t("더 보기 (" + (all.size() - to) + "개 남음)", "Show more (" + (all.size() - to) + " left)"));
            more.setTextColor(accentColor);
            more.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
            more.setGravity(Gravity.CENTER);
            more.setPadding(0, Ui.dp(this, 14), 0, Ui.dp(this, 14));
            more.setBackground(Ui.ripple(hintColor & 0x00FFFFFF | 0x33000000, null, 0));
            more.setOnClickListener(v -> {
                words.removeView(more);
                addBlockedRows(words, all, to, dict, remaining, updateSummary);
            });
            words.addView(divider());
            words.addView(more);
        }
    }

    private View blockedRow(LinearLayout words, String word, UserDictionary dict, int[] remaining, Runnable updateSummary) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(Ui.dp(this, 16), Ui.dp(this, 8), Ui.dp(this, 6), Ui.dp(this, 8));
        TextView t = new TextView(this);
        t.setText(word);
        t.setTextColor(textColor);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        row.addView(t, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        IconButton restore = new IconButton(this, Icons.UNDO, hintColor, hintColor & 0x00FFFFFF | 0x33000000,
                L.t("'" + word + "' 다시 학습하기", "Learn '" + word + "' again"));
        restore.setOnClickListener(v -> {
            dict.unblock(word);
            if (blockedCache != null) blockedCache.remove(word);
            int idx = words.indexOfChild(row);
            if (idx > 0) words.removeViewAt(idx - 1);
            else if (words.getChildCount() > 1) words.removeViewAt(1);
            words.removeView(row);
            if (words.getChildCount() > 0 && !(words.getChildAt(0) instanceof TextView)
                    && !(words.getChildAt(0) instanceof LinearLayout)) {
                words.removeViewAt(0);
            }
            if (words.getChildCount() == 0) {
                words.setVisibility(View.GONE);
                int at = list.indexOfChild(words);
                if (at > 0) list.getChildAt(at - 1).setVisibility(View.GONE);
            }
            remaining[0]--;
            updateSummary.run();
            Toast.makeText(this, L.t("'" + word + "'" + com.alternative_studios.newswipe.suggest.WordSuggester.objectParticle(word)
                    + " 다시 학습합니다", "'" + word + "' will be learned again"), Toast.LENGTH_SHORT).show();
        });
        row.addView(restore, new LinearLayout.LayoutParams(Ui.dp(this, 44), Ui.dp(this, 44)));
        return row;
    }

    /** 학습한 단어를 지울지 묻는다. '다시 보지 않음'을 체크하고 삭제하면 다음부터는 묻지 않는다. */
    private void confirmLearnedDelete(String word, Runnable delete) {
        Context dc = AppTheme.dialogContext(this);
        android.widget.CheckBox dontAsk = new android.widget.CheckBox(dc);
        dontAsk.setText(L.t("다시 보지 않음", "Don't show again"));
        dontAsk.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        android.widget.FrameLayout box = new android.widget.FrameLayout(dc);
        int pad = Ui.dp(this, 20);
        box.setPadding(pad, Ui.dp(this, 4), pad, 0);
        box.addView(dontAsk);
        AppTheme.accentBuilder(dc)
                .setTitle(L.t("학습한 단어 지우기", "Delete learned word"))
                .setMessage(L.t("'" + word + "'" + com.alternative_studios.newswipe.suggest.WordSuggester.objectParticle(word) + " 지우시겠습니까? "
                        + "지운 단어는 다시 학습하지 않습니다 ('학습하지 않는 단어 보기'에서 되돌릴 수 있습니다).",
                        "Delete '" + word + "'? Deleted words aren't learned again (you can undo this in 'View blocked words')."))
                .setView(box)
                .setPositiveButton(L.t("지우기", "Delete"), (d, w) -> {
                    // 취소했을 때는 체크해도 저장하지 않는다 (실수로 체크한 채 닫는 경우).
                    if (dontAsk.isChecked()) prefs.raw().edit().putBoolean(Prefs.CONFIRM_LEARNED_DELETE, false).apply();
                    delete.run();
                })
                .setNegativeButton(L.t("취소", "Cancel"), null)
                .show();
    }

    private Feedback vibePreview;

    /** 방금 바꾼 진동 설정을 바로 느껴 보게 한다 (level 0 = 일반 키 진동, 1~3 = 약하게·기본·강하게). */
    private void previewVibration(int level) {
        if (vibePreview == null) vibePreview = new Feedback(this);
        vibePreview.configure(prefs);
        vibePreview.preview(level);
    }

    private static String[] levelLabels() {

        return new String[]{L.t("약하게", "Light"), L.t("기본", "Default"), L.t("강하게", "Strong")};

    }

    /** 약하게(틱)·기본(클릭)·강하게(강한 클릭) 중에서 고르는 줄. 고르면 저장하고 그 진동을 한 번 울려 준다. */
    private View levelRow(String label, String key, int level) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(0, Ui.dp(this, 8), 0, Ui.dp(this, 4));
        if (label != null) {
            TextView t = new TextView(this);
            t.setText(label);
            t.setTextColor(textColor);
            t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
            markItem(t, box);
            box.addView(t);
        }
        box.addView(Ui.muteHaptics(choiceRowRepeat(levelLabels(), Math.max(1, Math.min(3, level)) - 1, i -> {
            prefs.raw().edit().putInt(key, i + 1).apply();
            previewVibration(i + 1);
        })));
        return box;
    }

    private void buildFeedback() {
        LinearLayout fb = card();
        // 진동 방식: 약하게(틱)·기본(클릭)·강하게(강한 클릭)이거나 길이·강도를 직접 정한다.
        LinearLayout customGroup = new LinearLayout(this);
        customGroup.setOrientation(LinearLayout.VERTICAL);
        customGroup.addView(slider(L.t("진동 길이", "Vibration length"), Prefs.VIBRATE_MS, prefs.vibrateMs(), 1, 40, 1, "ms",
                () -> previewVibration(0)));
        customGroup.addView(slider(L.t("진동 강도", "Vibration strength"), Prefs.VIBRATE_AMP, prefs.vibrateAmp(), 10, 100, 5, "%",
                () -> previewVibration(0)));
        customGroup.addView(note(L.t("강도는 진동 세기를 조절할 수 있는 기기에서만 적용됩니다.", "Strength works only on devices that can adjust vibration strength.")));
        customGroup.setVisibility(prefs.vibrateStyle() == 3 ? View.VISIBLE : View.GONE);
        View styleRow = Ui.muteHaptics(choiceRowRepeat(new String[]{L.t("약하게", "Light"), L.t("기본", "Default"), L.t("강하게", "Strong"), L.t("직접 설정", "Custom")},
                prefs.vibrateStyle(), i -> {
            prefs.raw().edit().putInt(Prefs.VIBRATE_STYLE, i).apply();
            Ui.setVisibleAnimated(customGroup, i == 3);
            previewVibration(0);
        }));
        LinearLayout perKeyBox = new LinearLayout(this);
        perKeyBox.setOrientation(LinearLayout.VERTICAL);
        perKeyBox.addView(levelRow(L.t("지우기 키", "Delete key"), Prefs.VIBRATE_DELETE, prefs.vibrateDelete()));
        perKeyBox.addView(levelRow(L.t("스페이스 키", "Space key"), Prefs.VIBRATE_SPACE, prefs.vibrateSpace()));
        perKeyBox.addView(levelRow(L.t("엔터 키", "Enter key"), Prefs.VIBRATE_ENTER, prefs.vibrateEnter()));
        perKeyBox.addView(levelRow(L.t("Shift 키 (Fn 키)", "Shift key (Fn key)"), Prefs.VIBRATE_SHIFT, prefs.vibrateShift()));
        perKeyBox.addView(levelRow(L.t("기호 키", "Symbol key"), Prefs.VIBRATE_SYMBOL, prefs.vibrateSymbol()));
        perKeyBox.addView(levelRow(L.t("언어 전환 키", "Language switch key"), Prefs.VIBRATE_LANGUAGE, prefs.vibrateLanguage()));
        perKeyBox.addView(levelRow(L.t("도구 막대 키", "Toolbar keys"), Prefs.VIBRATE_TOOLBAR, prefs.vibrateToolbar()));
        perKeyBox.addView(note(L.t("그 밖의 키는 위에서 고른 진동을 씁니다.", "Other keys use the vibration chosen above.")));
        View perKeySub = subGroup(perKeyBox);
        View gestureSub = subGroup(levelRow(null, Prefs.VIBRATE_GESTURE_LEVEL, prefs.vibrateGestureLevel()));
        View vibrateOptions = subGroup(styleRow, customGroup,
                toggle(L.t("키 종류별로 다르게", "Different for each key type"), null,
                        Prefs.VIBRATE_PER_KEY, prefs.vibratePerKey(), perKeySub),
                perKeySub,
                toggle(L.t("밀기·길게 누르기 진동", "Vibrate on swipe and long press"), L.t("밀거나 길게 눌렀을 때도 진동합니다.", "Also vibrates when you swipe or long press."),
                        Prefs.VIBRATE_GESTURE_ON, prefs.vibrateGestureOn(), gestureSub),
                gestureSub);
        fb.addView(toggle(L.t("키 진동", "Key vibration"), null, Prefs.VIBRATE, prefs.vibrate(), vibrateOptions));
        fb.addView(vibrateOptions);
        View soundVolume = subGroup(slider(L.t("소리 크기", "Sound volume"), Prefs.SOUND_VOLUME, prefs.soundVolume(), 0, 100, 5, "%"));
        fb.addView(toggle(L.t("키 소리", "Key sound"), null, Prefs.SOUND, prefs.sound(), soundVolume));
        fb.addView(soundVolume);
    }

    private void buildTools() {
        LinearLayout clip = section(L.t("클립보드", "Clipboard"));
        View clipImagesToggle = toggle(L.t("이미지 저장", "Save images"), L.t("복사한 이미지도 클립보드에 저장합니다 (최대 " + ClipboardHistory.MAX_IMAGES + "개).",
                        "Also saves copied images to the clipboard (up to " + ClipboardHistory.MAX_IMAGES + ").") ,
                prefs.clipboardImages(), this::onClipImagesChanged);
        clipImagesSwitch = switchOf(clipImagesToggle);
        View clipSensitiveToggle = toggle(L.t("민감한 정보도 저장", "Save sensitive content"),
                L.t("비밀번호와 같이 민감한 정보도 클립보드에 저장합니다. 보안에 주의하시기 바랍니다.", "Also saves sensitive content such as passwords to the clipboard. Be careful with your security."),
                Prefs.CLIPBOARD_SENSITIVE, prefs.clipboardSensitive());
        View clipCloseToggle = toggle(L.t("항목 사용 시 클립보드 닫기", "Close clipboard after use"),
                L.t("클립보드 항목을 눌러 입력하면 클립보드 창을 자동으로 닫고 자판으로 돌아갑니다.", "Closes the clipboard and returns to the keyboard when you tap an item to type it."),
                Prefs.CLIPBOARD_CLOSE_ON_USE, prefs.clipboardCloseOnUse());
        View clipSplitToggle = toggle(L.t("고정 항목 분리", "Separate pinned items"),
                L.t("클립보드를 두 페이지로 나눠 왼쪽에 고정 항목을 따로 모아 둡니다.", "Splits the clipboard into two pages, with pinned items on the left."),
                Prefs.CLIPBOARD_SPLIT_PINNED, prefs.clipboardSplitPinned());
        View clipImages = subGroup(clipImagesToggle, clipSensitiveToggle, clipCloseToggle, clipSplitToggle);
        clip.addView(toggle(L.t("클립보드 기록 저장", "Save clipboard history"), L.t("복사·잘라낸 내용을 최근 " + ClipboardHistory.MAX_ITEMS
                        + "개까지 모아 둡니다. 고정하지 않은 항목은 24시간 뒤 지워집니다. ",
                        "Keeps the last " + ClipboardHistory.MAX_ITEMS + " items you copied or cut. Unpinned items are deleted after 24 hours. "),
                Prefs.CLIPBOARD_HISTORY, prefs.clipboardHistory(), clipImages));
        clip.addView(clipImages);
        clip.addView(toggle(L.t("선택하면 편집 버튼 보이기", "Show edit buttons on selection"),
                L.t("글자를 선택하면 도구 막대에 잘라내기·복사·붙여넣기·전체 선택 버튼이 뜹니다.", "When you select text, cut, copy, paste and select all buttons appear on the toolbar."),
                Prefs.SELECTION_CHIPS, prefs.selectionChips()));
        clip.addView(toggle(L.t("방금 복사한 내용 붙여넣기", "Paste recently copied text"),
                L.t("방금 복사한 텍스트를 도구 막대에 1분 간 보여 주어 빠르게 붙여 넣을 수 있게 합니다.", "Shows text you just copied on the toolbar for 1 minute so you can paste it quickly."),
                Prefs.CLIPBOARD_CHIP, prefs.clipboardChip()));
        clip.addView(trashButton(L.t("클립보드 기록 모두 지우기", "Delete all clipboard history"), v -> AppTheme.dialogBuilder(this)
                .setTitle(L.t("클립보드 기록 모두 지우기", "Delete all clipboard history"))
                .setMessage(L.t("저장된 클립보드 기록을 모두 지웁니다. 고정한 항목도 함께 지워지며 되돌릴 수 없습니다.", "Deletes all saved clipboard history, including pinned items. This can't be undone."))
                .setPositiveButton(L.t("모두 지우기", "Delete all"), (d, w) -> {
                    new ClipboardHistory(new File(getFilesDir(), "clipboard.txt")).clearAll();
                    Toast.makeText(this, L.t("클립보드 기록을 지웠습니다", "Clipboard history deleted"), Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton(L.t("취소", "Cancel"), null)
                .show()));

        LinearLayout emoji = section(L.t("이모지", "Emoji"));
        emoji.addView(emojiSizeRow());
        emoji.addView(toggle(L.t("이모지 추천", "Emoji suggestions"), L.t("이모지 창을 열면 커서 앞에 쓴 글(마지막 12단어)에 맞는 이모지를 보여 줍니다.", "When you open the emoji panel, shows emoji that match the text before the cursor (last 12 words)."), Prefs.EMOJI_SUGGEST, prefs.emojiSuggest()));
    }

    /**
     * 이미지 저장을 끄면 보관 중인 이미지를 고정한 것까지 모두 지운다.
     * 지울 이미지가 있으면 먼저 확인하고, 취소하면 스위치를 다시 켠다.
     */
    private void onClipImagesChanged(boolean checked) {
        ClipboardHistory history = new ClipboardHistory(new File(getFilesDir(), "clipboard.txt"));
        if (checked || !history.hasImages(System.currentTimeMillis())) {
            prefs.raw().edit().putBoolean(Prefs.CLIPBOARD_IMAGES, checked).apply();
            if (!checked) history.clearImages();   // 기한이 지나 남은 파일까지 정리한다
            return;
        }
        ExpressiveSwitch sw = clipImagesSwitch;
        boolean[] done = {false};
        AppTheme.dialogBuilder(this)
                .setTitle(L.t("이미지 저장 끄기", "Turn off saving images"))
                .setMessage(L.t("클립보드에 이미지가 있습니다. 이미지 저장 기능을 끌 시 클립보드의 이미지가 모두 사라집니다. 계속하시겠습니까?", "The clipboard has images. Turning off image saving removes all images from the clipboard. Continue?"))
                .setPositiveButton(L.t("끄기", "Turn off"), (d, w) -> {
                    done[0] = true;
                    prefs.raw().edit().putBoolean(Prefs.CLIPBOARD_IMAGES, false).apply();
                    history.clearImages();
                })
                .setNegativeButton(L.t("취소", "Cancel"), null)
                .setOnDismissListener(d -> {
                    if (!done[0] && sw != null) sw.setCheckedSilently(true);
                })
                .show();
    }

    private void buildApp() {
        // 정보: 앱 이름과 버전 아래에 개인정보 처리방침·라이선스·소스 코드·문의를 둔다.
        LinearLayout about = card();
        LinearLayout head = new LinearLayout(this);   // 가로 배치는 기본으로 글자 기준선을 맞춘다
        TextView name = new TextView(this);
        name.setText(R.string.app_name);
        name.setTextColor(textColor);
        name.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22);
        name.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        head.addView(name);
        TextView version = new TextView(this);
        version.setText(L.t("버전 ", "Version ") + versionName());
        version.setTextColor(hintColor);
        version.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        version.setTypeface(android.graphics.Typeface.MONOSPACE);
        LinearLayout.LayoutParams vlp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        vlp.leftMargin = Ui.dp(this, 10);
        head.addView(version, vlp);
        LinearLayout.LayoutParams hlp = matchWrap();
        hlp.topMargin = Ui.dp(this, 12);
        hlp.bottomMargin = Ui.dp(this, 8);
        about.addView(head, hlp);

        // 앱 아이콘: 런처와 같은 적응형 아이콘으로 그려 기기의 아이콘 모양(원·둥근 사각 등)으로 잘려 보이게 하고,
        // 바탕과 화살표를 런처의 파랑 대신 지금 고른 강조색으로 칠한다.
        android.widget.ImageView icon = new android.widget.ImageView(this);
        icon.setImageDrawable(new android.graphics.drawable.AdaptiveIconDrawable(
                new android.graphics.drawable.ColorDrawable(accentColor),
                new android.graphics.drawable.LayerDrawable(new android.graphics.drawable.Drawable[]{
                        getDrawable(R.drawable.ic_launcher_foreground), new IconArrow(accentColor)})));
        icon.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        int iconSize = Ui.dp(this, 96);
        LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(iconSize, iconSize);
        ilp.gravity = Gravity.CENTER_HORIZONTAL;
        ilp.topMargin = Ui.dp(this, 8);
        ilp.bottomMargin = Ui.dp(this, 20);
        about.addView(icon, ilp);

        about.addView(button(L.t("개인정보 처리방침", "Privacy policy"), v -> openSection(PRIVACY)));
        about.addView(button(L.t("오픈소스 라이선스", "Open source licenses"), v -> openSection(LICENSES)));
        about.addView(button(L.t("소스 코드", "Source code"), v -> openUrl(SOURCE_URL)));
        about.addView(button(L.t("문의", "Contact"), v -> openUrl(ISSUES_URL)));
        about.addView(hallOfFameButton());

        LinearLayout app = section(L.t("앱 아이콘", "App icon"));
        app.addView(launcherToggle());
    }

    /**
     * 명예의 전당에 올릴 출시 테스트 참여자 (닉네임, 프로필 사진). 사진은 앱 용량을 아끼려고
     * 160×160 WebP로 줄여 res/drawable-nodpi에 둔다 (한 장에 몇 KB).
     */
    private static final Object[][] HALL_OF_FAME_PEOPLE = {
            {"마로코기", R.drawable.hof_marokogi},
            {"ssonbin", R.drawable.hof_ssonbin},
            {"청보라", R.drawable.hof_cheongbora},
            {"ia297", R.drawable.hof_ia297},
            {"LinusJS", R.drawable.hof_linusjs},
            {"낮은하늘", R.drawable.hof_najeunhaneul},
    };

    /** 명예의 전당 버튼 바탕과 프로필 사진 테두리에 쓰는 옅은 무지개 색. */
    private static final int[] RAINBOW = {0xFFFFB3BA, 0xFFFFD8A8, 0xFFFFF3A6, 0xFFBDF2C4, 0xFFB5DEFF, 0xFFD5BEFF};

    /** 정보 화면의 '명예의 전당' 버튼. 다른 버튼과 달리 무지개빛 그라데이션 바탕으로 돋보이게 한다. */
    private View hallOfFameButton() {
        final int ink = 0xFF1F1B2E;   // 옅은 무지개 위에서는 화면 모드와 관계없이 짙은 글자가 잘 읽힌다
        View row = button(L.t("명예의 전당", "Hall of fame"),
                new Chevron(this, ink), v -> openSection(HALL_OF_FAME));
        android.graphics.drawable.GradientDrawable rainbow = new android.graphics.drawable.GradientDrawable(
                android.graphics.drawable.GradientDrawable.Orientation.LEFT_RIGHT,
                RAINBOW);
        float r = Ui.dp(this, 12);
        rainbow.setCornerRadius(r);
        row.setBackground(Ui.ripple(0x33000000, rainbow, r));
        TextView t = (TextView) ((ViewGroup) row).getChildAt(0);
        t.setTextColor(ink);
        t.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        return row;
    }

    /** 명예의 전당 신청 안내. 강조색 '신청서'를 누르면 신청서(Google 설문지)를 연다. */
    private TextView hallOfFameApplyNote() {
        String link = L.t("신청서", "application form");
        String text = L.t("Google Play에서 유료로 NewSwipe를 구매해 사전 테스트에 참여하신 분들은 명예의 전당에 이름을 올릴 수 있습니다. "
                        + "원하시는 분은 " + link + "를 작성해 주세요.",
                "If you bought NewSwipe on Google Play and joined the pre-release test, you can have your name in the hall of fame. "
                        + "If you'd like that, please fill out the " + link + ".");
        android.text.SpannableString span = new android.text.SpannableString(text);
        int start = text.lastIndexOf(link);
        span.setSpan(new android.text.style.ClickableSpan() {
            @Override
            public void onClick(View widget) {
                openUrl(HALL_OF_FAME_FORM_URL);
            }

            @Override
            public void updateDrawState(android.text.TextPaint ds) {
                ds.setColor(accentColor);
                ds.setUnderlineText(false);
            }
        }, start, start + link.length(), android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        TextView t = note("");
        t.setText(span);
        t.setMovementMethod(android.text.method.LinkMovementMethod.getInstance());
        t.setHighlightColor(android.graphics.Color.TRANSPARENT);
        return t;
    }

    private static final String HALL_OF_FAME_FORM_URL =
            "https://docs.google.com/forms/d/e/1FAIpQLSdFzCqaJ6oLivN73Gq8uxO9o-aiGtW04OgdLpjfNYDBHVjg1g/viewform?usp=header";

    /**
     * 명예의 전당에 올릴 참여자 전체 수. '외 N명'은 이 수에서 사진과 닉네임을 실은 사람 수를 빼서 구하므로,
     * HALL_OF_FAME_PEOPLE에 한 사람을 더하면 N이 저절로 하나 줄어든다.
     */
    private static final int HALL_OF_FAME_TOTAL = 40;
    private static final int HALL_OF_FAME_OTHERS = HALL_OF_FAME_TOTAL - HALL_OF_FAME_PEOPLE.length;

    private void buildHallOfFame() {
        LinearLayout c = card();
        c.addView(note(L.t("출시 테스트에 참여해 NewSwipe를 응원하고 함께 다듬어 주신 분들입니다. 감사합니다.",
                "People who joined the pre-release test, cheered NewSwipe on and helped shape it. Thank you.")));
        int size = Ui.dp(this, 56);
        for (Object[] p : HALL_OF_FAME_PEOPLE) {
            LinearLayout row = new LinearLayout(this);
            row.setGravity(Gravity.CENTER_VERTICAL);
            android.widget.ImageView photo = new android.widget.ImageView(this);
            photo.setImageDrawable(circlePhoto((Integer) p[1]));
            photo.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            row.addView(photo, new LinearLayout.LayoutParams(size, size));
            TextView name = new TextView(this);
            name.setText((String) p[0]);
            name.setTextColor(textColor);
            name.setTextSize(TypedValue.COMPLEX_UNIT_SP, 17);
            LinearLayout.LayoutParams nlp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            nlp.leftMargin = Ui.dp(this, 16);
            row.addView(name, nlp);
            LinearLayout.LayoutParams rlp = matchWrap();
            rlp.topMargin = Ui.dp(this, 12);
            rlp.bottomMargin = Ui.dp(this, 4);
            c.addView(row, rlp);
        }
        TextView more = note(L.t("외 " + HALL_OF_FAME_OTHERS + "명의 고마운 분들",
                "and " + HALL_OF_FAME_OTHERS + " other people we're grateful to"));
        LinearLayout.LayoutParams mlp = matchWrap();
        mlp.topMargin = Ui.dp(this, 16);
        c.addView(more, mlp);

        // 신청 안내는 참여자 목록과 따로 아래 카드에 둔다.
        card().addView(hallOfFameApplyNote(), matchWrap());
    }

    /** 정사각형 사진을 동그랗게 잘라 그리는 Drawable (외부 라이브러리 없이 BitmapShader로). */
    private android.graphics.drawable.Drawable circlePhoto(int res) {
        android.graphics.Bitmap bmp = android.graphics.BitmapFactory.decodeResource(getResources(), res);
        android.graphics.Paint paint = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG
                | android.graphics.Paint.FILTER_BITMAP_FLAG);
        android.graphics.BitmapShader shader = new android.graphics.BitmapShader(bmp,
                android.graphics.Shader.TileMode.CLAMP, android.graphics.Shader.TileMode.CLAMP);
        paint.setShader(shader);
        // 명예의 전당 버튼과 같은 무지개 색으로 둥글게 이어지는 테두리를 두른다 (흰 바탕 사진도 카드와 구분된다).
        android.graphics.Paint ring = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
        ring.setStyle(android.graphics.Paint.Style.STROKE);
        ring.setStrokeWidth(Ui.dp(this, 2.5f));
        int[] loop = java.util.Arrays.copyOf(RAINBOW, RAINBOW.length + 1);
        loop[RAINBOW.length] = RAINBOW[0];   // 한 바퀴 돌아 처음 색으로 이어지게 한다
        return new android.graphics.drawable.Drawable() {
            @Override public void draw(android.graphics.Canvas c) {
                android.graphics.Rect r = getBounds();
                android.graphics.Matrix m = new android.graphics.Matrix();
                m.setScale(r.width() / (float) bmp.getWidth(), r.height() / (float) bmp.getHeight());
                m.postTranslate(r.left, r.top);
                shader.setLocalMatrix(m);
                c.drawOval(r.left, r.top, r.right, r.bottom, paint);
                ring.setShader(new android.graphics.SweepGradient(r.exactCenterX(), r.exactCenterY(), loop, null));
                float in = ring.getStrokeWidth() / 2;
                c.drawOval(r.left + in, r.top + in, r.right - in, r.bottom - in, ring);
            }
            @Override public void setAlpha(int alpha) { paint.setAlpha(alpha); }
            @Override public void setColorFilter(android.graphics.ColorFilter cf) { paint.setColorFilter(cf); }
            @Override public int getOpacity() { return android.graphics.PixelFormat.TRANSLUCENT; }
        };
    }

    /**
     * 정보 화면 아이콘의 흰 키 위 화살표를 강조색으로 다시 그린다. ic_launcher_foreground.xml의 화살표와 같은 자리·굵기
     * (108 단위 좌표)로 덮고, 런처 아이콘처럼 위는 밝게 아래는 짙게 칠한다.
     */
    private static final class IconArrow extends android.graphics.drawable.Drawable {
        private final android.graphics.Paint paint = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
        private final android.graphics.Path path = new android.graphics.Path();
        private final int mid;

        IconArrow(int accent) {
            mid = accent | 0xFF000000;
            path.moveTo(54, 47);
            path.lineTo(54, 65);
            path.moveTo(46.5f, 58);
            path.lineTo(54, 65.5f);
            path.lineTo(61.5f, 58);
            paint.setStyle(android.graphics.Paint.Style.STROKE);
            paint.setStrokeWidth(6.4f);   // 원래 화살표(6)의 가장자리까지 덮는다
            paint.setStrokeCap(android.graphics.Paint.Cap.ROUND);
            paint.setStrokeJoin(android.graphics.Paint.Join.ROUND);
            paint.setShader(new android.graphics.LinearGradient(0, 47, 0, 65.5f,
                    mix(mid, 0xFFFFFFFF, 0.15f), mix(mid, 0xFF000000, 0.25f), android.graphics.Shader.TileMode.CLAMP));
        }

        private static int mix(int a, int b, float t) {
            int r = Math.round(android.graphics.Color.red(a) * (1 - t) + android.graphics.Color.red(b) * t);
            int g = Math.round(android.graphics.Color.green(a) * (1 - t) + android.graphics.Color.green(b) * t);
            int bl = Math.round(android.graphics.Color.blue(a) * (1 - t) + android.graphics.Color.blue(b) * t);
            return android.graphics.Color.rgb(r, g, bl);
        }

        @Override
        public void draw(android.graphics.Canvas c) {
            android.graphics.Rect r = getBounds();
            int save = c.save();
            c.translate(r.left, r.top);
            c.scale(r.width() / 108f, r.height() / 108f);
            c.drawPath(path, paint);
            c.restoreToCount(save);
        }

        @Override public void setAlpha(int alpha) { paint.setAlpha(alpha); }
        @Override public void setColorFilter(android.graphics.ColorFilter cf) { paint.setColorFilter(cf); }
        @Override public int getOpacity() { return android.graphics.PixelFormat.TRANSLUCENT; }
    }

    private void openUrl(String url) {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url)));
        } catch (android.content.ActivityNotFoundException e) {
            Toast.makeText(this, L.t("웹 브라우저를 열 수 없습니다: ", "Can't open a web browser: ") + url, Toast.LENGTH_LONG).show();
        }
    }

    private void buildBackup() {
        LinearLayout backup = card();
        backup.addView(note(L.t("모든 설정을 파일로 저장하거나 불러옵니다. "
                + "학습한 단어와 최근 이모지(고정된 이모지 포함), 클립보드 기록(고정된 클립보드 항목 포함)은 "
                + "내보낼 때 같이 담을지 고를 수 있습니다. "
                + "클립보드 기록은 텍스트만 담기고 이미지는 담기지 않습니다. "
                + "이 항목들은 개인정보를 담고 있어, 담을 때는 비밀번호로 파일을 암호화하는 것을 추천합니다. "
                + "암호 없이 내보낸 파일은 공유 시 주의하시기 바랍니다.", "Save all settings to a file or load them. When exporting, you can choose whether to include learned words, recent emoji (including pinned emoji), and clipboard history (including pinned items). Only text from the clipboard history is included, not images. These contain personal information, so encrypting the file with a password is recommended. Be careful when sharing a file exported without a password.")));
        backup.addView(iconButton(L.t("설정 가져오기", "Import settings"), Icons.IMPORT, v -> SettingsImport.pick(this, REQ_IMPORT)));
        backup.addView(iconButton(L.t("설정 내보내기", "Export settings"), Icons.EXPORT, v -> chooseExportExtras()));
    }

    private void buildPrivacy() {
        buildGuide("guide/privacy.txt", false);
    }

    private void buildLicenses() {
        buildGuide("guide/licenses.txt", true);
    }

    /**
     * assets의 안내 글을 카드로 그린다 (형식은 {@link GuideText}). 본문은 큰 글자(guide), 라이선스 원문은 작은 글자로 쓴다.
     *
     * @param noteIntro 제목 없는 첫 카드를 설정 화면의 설명처럼 작은 글자로 쓴다 (오픈소스 라이선스)
     */
    private void buildGuide(String asset, boolean noteIntro) {
        java.util.Map<String, String> values = new java.util.HashMap<>();
        values.put("CLIP_MAX", String.valueOf(ClipboardHistory.MAX_ITEMS));
        values.put("CLIP_IMAGES", String.valueOf(ClipboardHistory.MAX_IMAGES));
        values.put("USAGE_WORDS", String.valueOf(UsageHistory.MAX_WORDS));
        values.put("USAGE_PAIRS", String.valueOf(UsageHistory.MAX_PAIRS));
        values.put("ISSUES_URL", ISSUES_URL);
        String text;
        // 영어 화면에서는 같은 이름의 영어 글(assets/guide/en/)을 쓴다.
        if (!L.ko()) asset = asset.replace("guide/", "guide/en/");
        try (java.io.InputStream in = getAssets().open(asset)) {
            java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            for (int n; (n = in.read(buf)) > 0; ) bytes.write(buf, 0, n);
            text = new String(bytes.toByteArray(), java.nio.charset.StandardCharsets.UTF_8);
        } catch (java.io.IOException e) {
            card().addView(note(L.t("안내를 불러오지 못했습니다.", "Couldn't load this page.")));
            return;
        }
        for (GuideText.Section sec : GuideText.parse(text, values)) {
            LinearLayout c = sec.title == null ? card() : section(sec.title);
            if (!sec.body.isEmpty()) c.addView(sec.title == null && noteIntro ? note(sec.body) : guide(sec.body));
            if (sec.small != null) c.addView(licenseText(sec.small));
        }
    }

    /** 개인정보 처리방침 같은 안내 글의 본문. 설정 설명(note)보다 크고 진한 글자로 쓴다. */
    private TextView guide(String s) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextColor(textColor);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        t.setLineSpacing(Ui.dp(this, 5), 1f);
        t.setPadding(0, Ui.dp(this, 8), 0, Ui.dp(this, 8));
        return t;
    }

    /** 라이선스 원문. 길어서 작은 글자로 두고, 복사할 수 있게 한다. */
    private TextView licenseText(String s) {
        TextView t = note(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        t.setTextIsSelectable(true);
        return t;
    }

    private static final int REQ_EXPORT = 1, REQ_IMPORT = 2, REQ_DICT = 3, REQ_CJK_JA = 4, REQ_CJK_ZH = 5;
    private boolean exportWithWords, exportWithEmoji, exportWithClipboard;

    /**
     * 학습한 단어·최근 이모지·클립보드 기록은 개인적인 사용 기록이라, 내보낼 때마다 담을지 고르게 한다 (기본은 담지 않음).
     * 모두 비어 있으면 묻지 않고 바로 내보낸다. 목록(setMultiChoiceItems) 대신 체크박스를 직접 쌓아
     * 항목이 적을 때 목록이 스크롤되며 잘리지 않게 한다.
     */
    private void chooseExportExtras() {
        boolean hasWords = !UserDictionary.shared(getFilesDir()).isEmpty()
                || new File(getFilesDir(), "usage.txt").length() > 0
                || new File(getFilesDir(), "picked_corrections.txt").length() > 0;   // 단어 사용 기록·자동 수정 기억
        boolean hasEmoji = !prefs.recentEmoji().isEmpty() || !prefs.pinnedEmoji().isEmpty();
        boolean hasClip = !new ClipboardHistory(new File(getFilesDir(), "clipboard.txt"))
                .snapshot(System.currentTimeMillis()).isEmpty();   // 이미지는 백업에 넣지 않는다
        if (!hasWords && !hasEmoji && !hasClip) {
            startExport(false, false, false, null);
            return;
        }
        Context dc = AppTheme.dialogContext(this);
        LinearLayout box = new LinearLayout(dc);
        box.setOrientation(LinearLayout.VERTICAL);
        int pad = Ui.dp(this, 20);
        box.setPadding(pad, Ui.dp(this, 4), pad, Ui.dp(this, 8));
        android.widget.CheckBox words = hasWords ? exportCheck(dc, box, L.t("학습한 단어 (단어 사용 기록·자동 수정 기억 포함)", "Learned words (including word usage and autocorrect memory)")) : null;
        android.widget.CheckBox emoji = hasEmoji ? exportCheck(dc, box, L.t("최근 사용한 이모지 (고정한 이모지 포함)", "Recent emoji (including pinned emoji)")) : null;
        android.widget.CheckBox clip = hasClip ? exportCheck(dc, box, L.t("클립보드 기록 (고정한 항목 포함, 이미지 제외)", "Clipboard history (including pinned items, no images)")) : null;
        AppTheme.accentBuilder(dc)
                .setTitle(L.t("백업 파일에 함께 담을 기록", "Include in backup file"))
                .setView(box)
                .setPositiveButton(L.t("내보내기", "Export"), (d, w) -> {
                    boolean ww = words != null && words.isChecked(), we = emoji != null && emoji.isChecked(),
                            wc = clip != null && clip.isChecked();
                    // 개인적인 기록을 담을 때만 비밀번호로 암호화할지 묻는다.
                    if (ww || we || wc) askExportPassword(ww, we, wc);
                    else startExport(false, false, false, null);
                })
                .setNegativeButton(L.t("취소", "Cancel"), null)
                .show();
    }

    private android.widget.CheckBox exportCheck(Context dc, LinearLayout box, String label) {
        android.widget.CheckBox c = new android.widget.CheckBox(dc);
        c.setText(label);
        c.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        c.setPadding(Ui.dp(this, 8), Ui.dp(this, 8), 0, Ui.dp(this, 8));
        box.addView(c);
        return c;
    }

    /**
     * 학습한 단어·이모지·클립보드 기록을 담을 때, 백업 파일을 비밀번호로 암호화할지 묻는다.
     * 비밀번호는 두 번 입력해 같은지 확인한다. 규칙이 맞지 않거나 다르면 창을 닫지 않고 알려 준다.
     */
    private void askExportPassword(boolean withWords, boolean withEmoji, boolean withClipboard) {
        Context dc = AppTheme.dialogContext(this);
        LinearLayout box = new LinearLayout(dc);
        box.setOrientation(LinearLayout.VERTICAL);
        int pad = Ui.dp(this, 20);
        box.setPadding(pad, Ui.dp(this, 4), pad, 0);
        TextView info = new TextView(dc);
        info.setText(L.t("개인정보가 담기므로 비밀번호로 암호화하는 것을 강력히 권장합니다. 비밀번호는 " + BackupCrypto.MIN_PASSWORD
                + "자 이상의 영문·숫자·특수문자입니다. 비밀번호를 잊으면 파일을 열 수 없으니 잘 기억해 두세요.",
                "This contains personal information, so encrypting it with a password is strongly recommended. The password must be at least "
                + BackupCrypto.MIN_PASSWORD + " English letters, numbers or symbols. If you forget it, the file can't be opened, so keep it safe."));
        info.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        box.addView(info);
        android.widget.EditText pw1 = SettingsImport.passwordField(dc, L.t("비밀번호", "Password"));
        android.widget.EditText pw2 = SettingsImport.passwordField(dc, L.t("비밀번호 확인", "Confirm password"));
        box.addView(pw1);
        box.addView(pw2);
        TextView error = new TextView(dc);
        error.setTextColor(0xFFE5484D);
        error.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        error.setVisibility(View.GONE);
        box.addView(error);
        android.app.AlertDialog dialog = AppTheme.accentBuilder(dc)
                .setTitle(L.t("백업 파일 암호화", "Encrypt backup file"))
                .setView(box)
                .setPositiveButton(L.t("암호화하여 내보내기", "Encrypt and export"), null)
                .setNeutralButton(L.t("암호 없이", "No password"), (d, w) -> startExport(withWords, withEmoji, withClipboard, null))
                .setNegativeButton(L.t("취소", "Cancel"), null)
                .create();
        dialog.setOnShowListener(d -> dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String problem = BackupCrypto.checkPassword(pw1.getText());
            if (problem == null && !pw1.getText().toString().equals(pw2.getText().toString())) {
                problem = L.t("두 비밀번호가 다릅니다", "Passwords don't match");
            }
            if (problem != null) {
                error.setText(problem);
                error.setVisibility(View.VISIBLE);
                return;
            }
            char[] password = SettingsImport.chars(pw1);
            pw1.getText().clear();
            pw2.getText().clear();
            dialog.dismiss();
            startExport(withWords, withEmoji, withClipboard, password);
        }));
        dialog.show();
    }

    /** 파일 고르기 화면으로 넘어가는 동안 들고 있는 내보내기용 비밀번호 (내보낸 뒤 바로 지운다). */
    private char[] exportPassword;

    private void startExport(boolean withWords, boolean withEmoji, boolean withClipboard, char[] password) {
        if (exportPassword != null) java.util.Arrays.fill(exportPassword, '\0');
        exportPassword = password;
        exportWithWords = withWords;
        exportWithEmoji = withEmoji;
        exportWithClipboard = withClipboard;
        Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT)
                .addCategory(Intent.CATEGORY_OPENABLE)
                .setType("application/json")
                .putExtra(Intent.EXTRA_TITLE, "newswipe-settings-"
                        + new java.text.SimpleDateFormat("yyyyMMdd-HHmm", java.util.Locale.US)
                        .format(new java.util.Date()) + ".json");
        startActivityForResult(i, REQ_EXPORT);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null || data.getData() == null) return;
        android.net.Uri uri = data.getData();
        if (requestCode == REQ_EXPORT) {
            boolean words = exportWithWords, emoji = exportWithEmoji, clip = exportWithClipboard;
            char[] password = exportPassword;
            exportPassword = null;
            if (password != null) Toast.makeText(this, L.t("암호화하는 중입니다…", "Encrypting…"), Toast.LENGTH_SHORT).show();
            // 비밀번호에서 키를 만드는 데 시간이 걸리므로 화면이 멈추지 않게 따로 처리한다.
            android.content.Context app = getApplicationContext();
            new Thread(() -> {
                String message;
                try {
                    SettingsBackup.export(app, uri, words, emoji, clip, password);
                    message = password != null ? L.t("설정을 암호화하여 내보냈습니다", "Settings encrypted and exported") : L.t("설정을 내보냈습니다", "Settings exported");
                } catch (Exception e) {
                    message = L.t("내보내지 못했습니다: ", "Couldn't export: ") + e.getMessage();
                } finally {
                    if (password != null) java.util.Arrays.fill(password, '\0');
                }
                String m = message;
                runOnUiThread(() -> Toast.makeText(app, m, Toast.LENGTH_LONG).show());
            }).start();
        } else if (requestCode == REQ_DICT) {
            importDictionaryPack(uri);
        } else if (requestCode == REQ_CJK_JA || requestCode == REQ_CJK_ZH) {
            importCjkPack(uri, requestCode == REQ_CJK_JA ? com.alternative_studios.newswipe.cjk.CjkComposer.JA
                    : com.alternative_studios.newswipe.cjk.CjkComposer.ZH);
        } else if (requestCode == REQ_IMPORT) {
            SettingsImport.onPicked(this, uri, () -> {
                importGeneration++;
                recreate();
            });
        }
    }

    /**
     * view를 여백 없는 틀로 감싼다. 위아래 여백이 있는 줄(버튼 등)을 펼치고 접을 때는 줄 자체가 아니라 이 틀을
     * setVisibleAnimated로 움직여야 여백이 높이와 함께 부드럽게 줄어든다.
     */
    private View fold(View view) {
        LinearLayout frame = new LinearLayout(this);
        frame.setOrientation(LinearLayout.VERTICAL);
        frame.setLayoutParams(matchWrap());
        frame.addView(view);
        return frame;
    }

    private View divider() {
        View v = new View(this);
        v.setBackgroundColor(hintColor & 0x00FFFFFF | 0x26000000);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1);
        lp.leftMargin = Ui.dp(this, 16);
        lp.rightMargin = Ui.dp(this, 16);
        v.setLayoutParams(lp);
        return v;
    }

    /** 설정 메뉴 항목의 왼쪽 아이콘. */
    private static int menuIcon(String id) {
        if (id.startsWith(EDITOR_PART) || id.startsWith(PROFILE_PART)) return Icons.MENU_KEYBOARD;
        switch (id) {
            case KEYBOARD: return Icons.MENU_KEYBOARD;
            case LANGUAGES: return Icons.GLOBE;
            case "onehand": return Icons.ONE_HAND;
            case "theme": return Icons.MENU_THEME;
            case "words": return Icons.MENU_WORDS;
            case "clipemoji": return Icons.CLIPBOARD;
            case "feedback": return Icons.MENU_SOUND;
            case "backup": return Icons.MENU_BACKUP;
            case "lab": return Icons.MENU_LAB;
            default: return Icons.MENU_INFO;
        }
    }

    private View menuRow(int icon, String title, String desc, View.OnClickListener l) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(Ui.dp(this, 16), Ui.dp(this, 14), Ui.dp(this, 12), Ui.dp(this, 14));
        LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        ilp.rightMargin = Ui.dp(this, 14);
        row.addView(new IconView(this, icon, accentColor, 40, 22), ilp);
        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        TextView t = new TextView(this);
        t.setText(title);
        t.setTextColor(textColor);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        texts.addView(t);
        TextView d = new TextView(this);
        d.setText(desc);
        d.setTextColor(hintColor);
        d.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        texts.addView(d);
        row.addView(texts, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        row.addView(new Chevron(this, hintColor));
        row.setClickable(true);
        row.setFocusable(true);
        // 카드 가장자리까지 채우는 사각형 리플 (바깥 모서리는 카드의 clipToOutline이 둥글게 자른다)
        row.setBackground(Ui.ripple(hintColor & 0x00FFFFFF | 0x33000000, null, 0));
        row.setOnClickListener(l);
        return row;
    }

    /** 런처 아이콘 표시 여부는 설정값이 아니라 별칭의 사용 상태로 저장된다. */
    private View launcherToggle() {
        return toggle(L.t("런처에 앱 아이콘 표시", "Show app icon in launcher"),
                L.t("끄면 홈 화면·앱 목록에서 앱 아이콘이 사라집니다. 설정은 시스템의 키보드 설정이나 "
                        + "키보드 도구 막대의 설정 버튼에서 열 수 있습니다. 설정 버튼도 끈 상태라면 "
                        + "시스템 설정 > 키보드에서 열어야 하니 주의하세요.", "When off, the app icon disappears from the home screen and app list. You can still open settings from the system keyboard settings or the settings button on the keyboard toolbar. If you also turned off the settings button, you'll have to open it from System settings > Keyboard."),
                SettingsBackup.launcherShown(this), checked -> SettingsBackup.setLauncherShown(this, checked));
    }

    private String versionName() {
        try {
            PackageInfo pi = getPackageManager().getPackageInfo(getPackageName(), 0);
            return pi.versionName;
        } catch (PackageManager.NameNotFoundException e) {
            return "";
        }
    }

    private static void setEnabledDeep(View v, boolean enabled) {
        v.setEnabled(enabled);
        if (v instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) setEnabledDeep(g.getChildAt(i), enabled);
        }
    }

    private void updateStatus() {
        if (status == null) return;
        boolean[] state = OnboardingActivity.keyboardState(this);
        boolean enabled = state[0], selected = state[1];
        if (selected && !prefs.setupDone()) prefs.raw().edit().putBoolean(Prefs.SETUP_DONE, true).apply();
        // 처음 쓸 때는 시작하기를 마칠 때까지 설정 메뉴를 흐리게 하고 누를 수 없게 한다 (한 번 마치면 다시 막지 않는다).
        boolean locked = !prefs.setupDone();
        for (View v : new View[]{menuHeading, menuCard}) {
            if (v == null) continue;
            v.setAlpha(locked ? 0.38f : 1f);
            setEnabledDeep(v, !locked);
        }
        int setupVisibility = selected ? View.GONE : View.VISIBLE;
        if (setupCard != null) setupCard.setVisibility(setupVisibility);
        if (setupHeading != null) setupHeading.setVisibility(setupVisibility);
        if (appCard != null) {
            // 시작하기가 보이면 그 아래로 떨어뜨리고, 접히면 화면 맨 위에 붙인다.
            ((LinearLayout.LayoutParams) appCard.getLayoutParams()).topMargin = Ui.dp(this, selected ? 8 : 20);
            appCard.requestLayout();
        }
        if (selected) {
            status.setText(L.t("✓ NewSwipe를 사용하고 있습니다", "✓ NewSwipe is ready to use"));
            status.setTextColor(accentColor);
        } else if (enabled) {
            status.setText(L.t("사용 설정됨 · 2번을 눌러 기본 키보드로 선택하세요", "Enabled · Tap 2 to select it as the default keyboard"));
            status.setTextColor(textColor);
        } else {
            status.setText(L.t("1번을 눌러 NewSwipe를 켜 주세요", "Tap 1 to enable NewSwipe"));
            status.setTextColor(textColor);
        }
    }

    // ---------------------------------------------------------------- 화면 조각

    // ---- 공통 조각 (SettingsWidgets): 목록(list)에 더하거나, 만든 조각을 돌려준다.

    private LinearLayout section(String name) {
        return widgets.section(list, name);
    }

    private LinearLayout card() {
        return widgets.card(list);
    }

    private View toggle(String label, String desc, boolean value, java.util.function.Consumer<Boolean> onChange) {
        return widgets.toggle(label, desc, value, onChange);
    }

    /** toggle()로 만든 줄을 회색으로 흐리게 하고 눌러도 바뀌지 않게 한다 (스위치는 지금 보이는 상태 그대로). */
    private static void disableToggle(View toggleRow) {
        toggleRow.setEnabled(false);
        toggleRow.setClickable(false);
        switchOf(toggleRow).setEnabled(false);   // 스위치는 스스로 흐려진다
        ((ViewGroup) toggleRow).getChildAt(0).setAlpha(0.38f);   // 이름·설명
    }

    private static ExpressiveSwitch switchOf(View toggleRow) {
        return SettingsWidgets.switchOf(toggleRow);
    }

    /** @param onChange 사용자가 값을 바꿀 때마다 (저장한 뒤에) 부를 작업. 없으면 null. */
    private SettingsWidgets.Slider sliderRow(String label, String key, int value, int min, int max, int step, String unit,
                                             Runnable onChange) {
        return widgets.slider(label, value, min, max, step, unit, val -> {
            prefs.raw().edit().putInt(key, val).apply();
            if (onChange != null) onChange.run();
        });
    }

    private View slider(String label, String key, int value, int min, int max, int step, String unit, Runnable onChange) {
        return sliderRow(label, key, value, min, max, step, unit, onChange).view;
    }

    private View button(String label, View.OnClickListener l) {
        return widgets.button(label, l);
    }

    private View button(String label, View trailing, View.OnClickListener l) {
        return widgets.button(label, trailing, l);
    }

    private View subGroup(View... children) {
        return widgets.subGroup(children);
    }

    private TextView note(String s) {
        return widgets.note(s);
    }

    private static LinearLayout.LayoutParams matchWrap() {
        return SettingsWidgets.matchWrap();
    }

    private View toggle(String label, String desc, String key, boolean value) {
        return toggle(label, desc, value, checked -> prefs.raw().edit().putBoolean(key, checked).apply());
    }

    /** 스위치가 켜져 있을 때만 dependents를 보여 준다. */
    private View toggle(String label, String desc, String key, boolean value, View... dependents) {
        for (View d : dependents) d.setVisibility(value ? View.VISIBLE : View.GONE);
        return toggle(label, desc, value, checked -> {
            prefs.raw().edit().putBoolean(key, checked).apply();
            for (View d : dependents) Ui.setVisibleAnimated(d, checked);
        });
    }

    private View slider(String label, String key, int value, int min, int max, int step, String unit) {
        return slider(label, key, value, min, max, step, unit, null);
    }

    /** 무언가를 모두 지우는 항목. 화살표 대신 학습한 단어 목록과 같은 휴지통 아이콘을 둔다. */
    private View trashButton(String label, View.OnClickListener l) {
        return iconButton(label, Icons.TRASH, l);
    }

    /** 화살표 대신 하는 일을 나타내는 아이콘을 오른쪽 끝에 둔 항목. */
    private View iconButton(String label, int icon, View.OnClickListener l) {
        View iconView = new View(this) {
            private final android.graphics.Paint p = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);

            @Override
            protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                int size = Ui.dp(getContext(), 24);
                setMeasuredDimension(size, size);
            }

            @Override
            protected void onDraw(android.graphics.Canvas c) {
                p.setColor(accentColor);
                Icons.draw(c, icon, getWidth() / 2f, getHeight() / 2f, Ui.dp(getContext(), 22), p);
            }
        };
        iconView.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        return button(label, iconView, l);
    }

    /** 뒤로 가기: 검색창이나 키보드 시험 입력창이 열려 있으면 그것부터 닫고, 그다음에 이전 화면으로 간다 (Android 12 이하). */
    @Override
    public void onBackPressed() {
        if (SettingsFrame.consumeBack(this)) return;
        if (panelOpen()) {
            closeKeyPanel();
            return;
        }
        super.onBackPressed();
    }
}
