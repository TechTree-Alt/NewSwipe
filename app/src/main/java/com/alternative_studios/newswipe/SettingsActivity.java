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
        // 설정 화면은 세로로 고정이지만(매니페스트), 가로 모드·대화면 키보드 사용자화는 화면을 돌려 가며 미리보기를 볼 수 있다.
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
        // 대화면 키보드 사용자화는 지금 화면 방향의 설정을 보여 주므로 제목에도 방향을 붙인다.
        if (LARGE_AREA.equals(sectionId) && largeProfileNow() != null) {
            sectionName = Prefs.PROFILE_LARGE_LANDSCAPE.equals(largeProfileNow()) ? "대화면 가로 키보드 사용자화" : "대화면 세로 키보드 사용자화";
        }
        SettingsFrame frame = sectionName == null
                ? new SettingsFrame(this, getString(R.string.app_name), "밀어서 입력하고 조작하는 한국어 입력 시스템")
                        .withSearch(this::onSearchQuery)
                : LEARNED.equals(sectionId) || BLOCKED.equals(sectionId)
                ? new SettingsFrame(this, sectionName, getString(R.string.app_name)).withSearch(this::onWordSearch, "단어 검색")
                : new SettingsFrame(this, sectionName, getString(R.string.app_name));
        if (KEYBOARD.equals(sectionId)) frame.withAction(Icons.UNDO, "마지막 변경 되돌리기", v -> undoLast()).keyboardOverlays();
        else if (KEY_AREA.equals(sectionId) || LANDSCAPE_AREA.equals(sectionId) || LARGE_AREA.equals(sectionId)) {
            frame.keyboardOverlays();   // 아래 미리보기 위로 실제 키보드가 덮는다
        }
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
        }
        if (editorPrefListener != null) prefs.raw().unregisterOnSharedPreferenceChangeListener(editorPrefListener);
        super.onDestroy();
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

    /** 하위 메뉴 정의: {id, 제목, 설명}. */
    private static final String[][] SECTIONS = {
            {KEYBOARD, "키보드 사용자화", "자판 배열, 모양, 입력, 길게 누르기, 밀기"},
            {"theme", "테마", "화면 모드, 강조 색"},
            {"feedback", "소리 및 진동", "키를 누를 때의 진동과 소리"},
            {"words", "추천, 학습 및 완성", "단어 추천, 학습, 단축어, 자동 완성"},
            {"clipemoji", "클립보드 및 이모지", "클립보드 기록, 편집 버튼, 이모지 크기·추천"},
            {"onehand", "한 손 모드", "자판 폭·키 높이, 세로 위치"},
            {"backup", "설정 가져오기 및 내보내기", "모든 설정을 파일로 저장하거나 불러오기"},
            {"lab", "실험실", "대화면 레이아웃, 외국어 자판, 외장 키보드"},
    };

    /** 첫 화면 맨 위에 설정 메뉴와 따로 두는 항목. */
    private static final String[] APP = {"app", "정보", "앱 버전, 개인정보 처리방침, 라이선스, 문의"};

    /** 메뉴에는 없고 다른 화면에서 여는 하위 화면. */
    private static final String LEARNED = "learned", BLOCKED = "blocked", PRIVACY = "privacy", LICENSES = "licenses",
            KEY_AREA = "keyarea", LANDSCAPE_AREA = "landscapearea",
            LARGE_AREA = "largearea";

    private static final String SOURCE_URL = "https://github.com/TechTree-Alt/NewSwipe";
    private static final String ISSUES_URL = SOURCE_URL + "/issues";

    private static String sectionTitle(String id) {
        if (id.startsWith(EDITOR_PART)) return "키보드 사용자화 · " + id.substring(id.lastIndexOf(':') + 1);
        for (String[] s : SECTIONS) if (s[0].equals(id)) return s[1];
        if (APP[0].equals(id)) return APP[1];
        switch (id) {
            case LEARNED: return "학습한 단어";
            case BLOCKED: return "학습하지 않는 단어";
            case PRIVACY: return "개인정보 처리방침";
            case LICENSES: return "오픈소스 라이선스";
            case KEY_AREA: return "균형 배열 사용자화";
            case LANDSCAPE_AREA: return "가로 모드 키보드 사용자화";
            case LARGE_AREA: return "대화면 키보드 사용자화";
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
        // 키보드 사용자화는 목록 안에 따로 둔다. 대화면이 아니면 대화면 키보드 사용자화에는 안내 문구만 둔다.
        if (hasReset(id) && !KEYBOARD.equals(id) && !(LARGE_AREA.equals(id) && largeProfileNow() == null)) addResetButton(id);
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
            case KEY_AREA: buildKeyArea(); break;
            case LANDSCAPE_AREA: buildLandscapeArea(); break;
            case "lab": buildLab(); break;
            case LARGE_AREA: buildLargeArea(); break;
            default: buildApp(); break;
        }
    }

    /** 기본값으로 되돌릴 설정이 있는 메뉴인지 (설정 메뉴와 키 위치·폭, 가로 모드·대화면 키보드 사용자화 화면. 정보·가져오기 및 내보내기·학습한 단어 등은 제외). */
    private static boolean hasReset(String id) {
        if (KEY_AREA.equals(id) || LANDSCAPE_AREA.equals(id) || LARGE_AREA.equals(id)) return true;
        for (String[] sec : SECTIONS) if (sec[0].equals(id) && !"backup".equals(id)) return true;
        return false;
    }

    /** 메뉴 맨 아래의 '기본값으로 되돌리기'. 확인을 거쳐 이 메뉴 안의 모든 값을 기본값으로 바꾼다. */
    private void addResetButton(String id) {
        list.addView(ResetDialog.button(this, sectionTitle(id), () -> reset(id)));
    }

    /** 이 화면이 담당하는 값만 기본값으로 바꾼다 (확인은 ResetDialog가 이미 받았다). */
    private void reset(String id) {
        if (KEY_AREA.equals(id)) prefs.resetBalancedKeys();
        else if (LANDSCAPE_AREA.equals(id)) prefs.profileEditView(Prefs.PROFILE_LANDSCAPE).resetProfileArea();
        else if (LARGE_AREA.equals(id)) {
            // 지금 화면 방향(대화면 세로·가로)의 값만 되돌린다.
            String now = largeProfileNow();
            if (now != null) prefs.profileEditView(now).resetProfileArea();
        }
        else prefs.resetSection(id);
        importGeneration++;   // 뒤에 쌓인 설정 화면도 돌아오면 기본값으로 다시 그린다
        recreate();
        Toast.makeText(this, "기본값으로 되돌렸습니다", Toast.LENGTH_SHORT).show();
    }

    private void buildMain() {
        // 시작하기 (이미 기본 키보드로 쓰고 있으면 updateStatus()가 접는다)
        LinearLayout setup = section("시작하기");
        setupCard = setup;
        setupHeading = list.getChildAt(list.indexOfChild(setup) - 1);
        status = new TextView(this);
        status.setTextColor(textColor);
        status.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        status.setPadding(0, 0, 0, Ui.dp(this, 8));
        setup.addView(status);
        setup.addView(button("1. 키보드 사용 설정", v ->
                startActivity(new Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))));
        setup.addView(button("2. NewSwipe를 기본 키보드로 선택", v -> {
            InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
            if (imm != null) imm.showInputMethodPicker();
        }));

        // 정보: 설정 메뉴와 따로 둔다.
        LinearLayout app = menuCard(card());
        appCard = app;
        app.addView(menuRow(menuIcon(APP[0]), APP[1], APP[2], v -> openSection(APP[0])));

        LinearLayout menu = menuCard(section("설정"));
        menuCard = menu;
        menuHeading = list.getChildAt(list.indexOfChild(menu) - 1);
        for (int i = 0; i < SECTIONS.length; i++) {
            String[] sec = SECTIONS[i];
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
        startActivity(new Intent(this, SettingsActivity.class).putExtra(EXTRA_SECTION, id));
    }

    // ---------------------------------------------------------------- 설정 검색

    private LinearLayout mainWrap, searchResults;

    /**
     * 설정 검색에서 항목 이름만으로는 못 찾는 말(줄임말·영어·같은 뜻의 다른 말)을 더해 주는 보조 사전:
     * {화면 id, 항목 이름, 같이 찾을 낱말}. 항목 목록 자체는 설정 화면을 실제로 만들어 모으므로 여기 없는 항목도 검색된다.
     * 이름이 바뀌어 맞는 항목이 없어진 줄은 그냥 무시된다 (검색이 깨지지 않고, 그 낱말로만 못 찾게 된다).
     */
    private static final String[][] SEARCH_SYNONYMS = {
            {KEYBOARD, "글자 키 높이", "키보드 높이 키 높이 크기"},
            {KEYBOARD, "키 글자 크기", "폰트 글씨 크기"},
            {KEYBOARD, "키 곡률", "둥글기 모서리 라운드"},
            {KEYBOARD, "그림자 세기", "그림자"},
            {KEYBOARD, "키 그림자", "입체감"},
            {KEYBOARD, "숫자 줄 높이", "숫자 행 크기"},
            {KEYBOARD, "기능키 높이", "맨 아래 줄 스페이스바 줄 높이 크기"},
            {KEYBOARD, "키 누름 미리보기", "말풍선 팝업 확대"},
            {KEYBOARD, "글자 키 힌트 없애기", "힌트 숨기기 오른쪽 위 길게 눌러 입력할 문자"},
            {KEYBOARD, "숫자 키 힌트 없애기", "힌트 숨기기"},
            {KEYBOARD, "스페이스바에 언어 이름 보이기", "스페이스 띄어쓰기 언어 표시 한국어 영어 일본어 중국어"},
            {KEYBOARD, "왼쪽 여백", "패딩 간격"},
            {KEYBOARD, "오른쪽 여백", "패딩 간격"},
            {KEYBOARD, "위쪽 여백", "패딩 간격"},
            {KEYBOARD, "아래쪽 여백", "패딩 간격"},
            {KEYBOARD, "키 사이 좌우 간격", "키 간격 갭"},
            {KEYBOARD, "키 사이 상하 간격", "키 간격 갭"},
            {"clipemoji", "이모지 크기", "이모티콘 크기 글자 키우기 크게 이모지 창"},
            {KEYBOARD, "균형 배열 사용자화", "자음 모음 열 폭 위치 배치 키 위치 경계 균형 레이아웃"},
            {KEYBOARD, "자·모음 균형 배열", "자음 모음 반반 균형 한글 자판 배열 두벌식 단모음 쿼티 qwerty 레이아웃"},
            {KEYBOARD, "가로 모드 키보드 사용자화", "가로 화면 landscape"},
            {KEYBOARD, "숫자 줄 표시", "숫자 줄 1234567890 숫자열"},
            {KEYBOARD, "기능키 순서·유무 사용자화", "맨 아래 줄 하단 키 쉼표 지구본 온점 엔터"},
            {KEYBOARD, "격자 정렬", "그리드 정렬 칸"},
            {"theme", "화면 모드", "다크 라이트 어두운 밝은 야간"},
            {"theme", "강조 색", "색상 컬러 포인트 테마 색"},
            {"theme", "색 정렬", "키 색"},
            {KEYBOARD, "문자 길게 누르기 시간", "롱프레스 길게 누르기 시간 지연 특수문자 반복 입력 연타 연속 입력"},
            {KEYBOARD, "기능키 길게 누르기 시간", "롱프레스 시간 길게 눌러 지우기 이모지 입력기 선택"},
            {KEYBOARD, "쌍자음 인식 시간", "연속 탭 연속 누르기 된소리 ㄲ ㅆ 시간"},
            {KEYBOARD, "쌍자음 입력", "된소리 ㄲ ㅆ ㅃ 자음 연속 탭 연속 누르기 두 번 아래로 밀기"},
            {KEYBOARD, "이중모음 입력", "ㅑ ㅛ ㅕ 모음 연속 탭 연속 누르기 두 번 아래로 밀기"},
            {KEYBOARD, "영어 자동 대문자", "대소문자 자동 대문자 shift 첫 글자"},
            {KEYBOARD, "두 번 눌러 마침표", "점 온점 더블 스페이스바"},
            {KEYBOARD, "인식 범위 좁히기", "지우기 백스페이스 스페이스바 오타 오입력 터치 영역"},
            {KEYBOARD, "미는 거리", "스와이프 거리 민감도 밀어서 쌍자음 겹받침 이중모음 ㄲ ㅆ ㅘ 쉼표 글자 입력"},
            {KEYBOARD, "기능키 미는 거리", "스와이프 기능 스페이스바 커서 이동 언어 전환 단어 삭제 이모지 도구 막대 밀기 복사 붙여넣기"},
            {KEYBOARD, "키보드 밀기 사용자화", "두 손가락 실행 취소 다시 실행 undo redo 트랙패드 커서 자유 이동 문자 키"},
            {KEYBOARD, "커서 이동 속도 좌우", "스페이스바 커서 키 트랙패드"},
            {KEYBOARD, "언어 순서 사용자화", "지구본 언어 차례 순서 바꾸기 한국어 영어 일본어 중국어"},
            {KEYBOARD, "키보드 밀기 사용자화", "자판 스와이프 기능"},
            {KEYBOARD, "위로 밀어서 추천 단어 입력", "추천 단어 스와이프 고르기 자판 위로"},
            {"words", "도구 막대 전체를 쓰기", "추천 툴바 전체"},
            {"words", "추천 단어 뒤에 공백 포함", "띄어쓰기 스페이스"},
            {"words", "단어 추천", "자동 완성 예측 추천"},
            {"clipemoji", "이모지 추천", "이모티콘 추천"},
            {"words", "단축어 사용자화", "줄임말 약어 텍스트 확장"},
            {"words", "단축어 사용", "줄임말 약어 텍스트 확장"},
            {"words", "자동 수정", "오타 교정 자동 고침"},
            {"words", "입력한 단어 학습", "사용자 사전 개인화"},
            {"words", "단어를 지울 때 확인", "삭제 확인 학습한 단어"},
            {"words", "학습한 단어 보기", "사용자 사전 목록"},
            {"words", "학습하지 않는 단어 보기", "학습 제외 차단 지운 단어 다시 학습"},
            {"words", "학습한 단어 모두 지우기", "사용자 사전 초기화 삭제"},
            {"words", "자동 완성 제안", "오토필 Autofill Google 삼성패스 비밀번호 아이디 계정 칩 인증번호"},
            {"feedback", "키 진동", "햅틱 진동 켜기 끄기"},
            {"feedback", "진동 길이", "햅틱 ms 밀리초 진동 시간"},
            {"feedback", "진동 강도", "햅틱 세기 진폭"},
            {"feedback", "키 종류별로 다르게", "진동 지우기 스페이스 엔터 Shift 기호 언어 전환 도구 막대 키 햅틱"},
            {"feedback", "밀기·길게 누르기 진동", "햅틱 스와이프 롱프레스"},
            {"feedback", "키 소리", "클릭음 사운드 소리 켜기"},
            {"feedback", "소리 크기", "볼륨 클릭음"},
            {KEYBOARD, "도구 막대 표시", "툴바 켜기 끄기"},
            {KEYBOARD, "도구 막대 버튼 사용자화", "툴바 버튼 순서 음성 입력 마이크 클립보드 이모지 실행 취소"},
            {KEYBOARD, "자판 아래에 두기", "툴바 위치 아래 위"},
            {KEYBOARD, "도구 막대 높이", "툴바 높이 크기"},
            {KEYBOARD, "도구 버튼 크기", "툴바 아이콘 크기"},
            {"clipemoji", "이미지 저장", "클립보드 사진"},
            {"clipemoji", "클립보드 기록 저장", "복사 기록 히스토리"},
            {"clipemoji", "고정 항목 분리", "클립보드 고정 핀 페이지 따로"},
            {"clipemoji", "선택하면 편집 버튼 보이기", "잘라내기 복사 붙여넣기 전체 선택 편집 칩"},
            {"clipemoji", "클립보드 기록 모두 지우기", "복사 기록 삭제"},
            {"onehand", "자판 폭", "한 손 모드 너비"},
            {"onehand", "가로 모드 자판 폭", "한 손 모드 너비 가로"},
            {"onehand", "키 높이", "한 손 모드 높이"},
            {"onehand", "세로 위치", "한 손 모드 위치 올리기"},
            {"lab", "일본어 자판", "일본어 히라가나 가타카나 한자 변환 플릭 로마자 사전 팩 japanese"},
            {"lab", "반각 공백 쓰기", "일본어 스페이스 전각 공백 반각 공백 띄어쓰기"},
            {"lab", "중국어 자판", "중국어 병음 한자 간체 사전 팩 chinese pinyin"},
            {KEYBOARD, "기호 자판 대신 숫자 패드 사용", "숫자 패드 넘버 패드 3×4 숫자 자판 기호 키 ?123 계산기 전화"},
            {"lab", "아래로 밀기 엄격도", "스와이프 방향 판정 민감도"},
            {"lab", "위로 밀기 엄격도", "스와이프 방향 판정 민감도"},
            {"lab", "대화면 기준", "태블릿 폴더블 큰 화면 dp"},
            {"lab", "대화면 키보드 사용자화", "태블릿 폴더블 큰 화면"},
            {"lab", "대화면 별도 레이아웃", "태블릿 폴더블 큰 화면"},
            {"lab", "한·영 전환 키 바꾸기", "한영 한/영 전환 언어 전환 키 외장 키보드 블루투스 USB 물리 하드웨어"},
            {"lab", "한·영 키", "한영 한/영 전환 언어 전환 키 외장 키보드"},
            {"lab", "한·영 키 직접 지정", "한영 한/영 전환 키 바꾸기 외장 키보드"},
            {"lab", "언어 전환 알림", "한영 전환 외장 키보드"},
            {"lab", "도구 막대 보이기", "외장 키보드 물리 키보드 블루투스 연결 툴바 클립보드 이모지"},
            {"app", "개인정보 처리방침", "프라이버시 privacy 정보"},
            {"app", "오픈소스 라이선스", "라이센스 license 정보"},
            {"app", "소스 코드", "깃허브 github 정보"},
            {"app", "문의", "이슈 버그 제보 연락 정보"},
            {"app", "런처에 앱 아이콘 표시", "앱 아이콘 숨기기 홈 화면"},
            {"backup", "설정 가져오기", "복원 불러오기 import"},
            {"backup", "설정 내보내기", "백업 저장 export"},
    };

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
            searchResults.addView(searchNote("검색 목록을 준비하는 중입니다…"));
            return;
        }
        // 항목: {화면 id, 이름, 설명·같이 찾을 낱말, "m" = 설정 메뉴}. 메뉴는 상수에서, 나머지는 모아 둔 글자에서 온다.
        java.util.List<String[]> entries = new java.util.ArrayList<>();
        for (String[] sec : SECTIONS) entries.add(new String[]{sec[0], sec[1], sec[2], "m"});
        entries.add(new String[]{APP[0], APP[1], APP[2], "m"});
        for (String[] e : searchEntries) {
            String extra = synonyms().get((e[0].startsWith(EDITOR_PART) ? KEYBOARD : e[0]) + "\0" + e[1]);
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
            View row = menuRow(menuIcon(section), title, isMenu ? e[2] : sectionTitle(section),
                    v -> openSection(section, isMenu ? null : title));
            if (!unlocked) {   // 시작하기를 마치기 전에는 설정 메뉴처럼 누를 수 없다
                row.setAlpha(0.38f);
                setEnabledDeep(row, false);
            }
            searchResults.addView(row);
            shown++;
        }
        if (shown == 0) searchResults.addView(searchNote("일치하는 설정이 없습니다"));
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
            for (String[] r : SEARCH_SYNONYMS) synonymMap.put(r[0] + "\0" + r[1], r[2]);
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
            KEY_AREA, LANDSCAPE_AREA, LARGE_AREA};

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

    /** 검색 목록에 넣는 키 창 (영역 창은 모두 넣는다). */
    private static final int[] HARVEST_KEYS = {com.alternative_studios.newswipe.keyboard.Key.SPACE,
            com.alternative_studios.newswipe.keyboard.Key.DELETE, com.alternative_studios.newswipe.keyboard.Key.TO_SYMBOLS,
            com.alternative_studios.newswipe.keyboard.Key.LANGUAGE, com.alternative_studios.newswipe.keyboard.Key.ENTER};

    /** 키보드 사용자화의 영역 창과 주요 키 창을 보이지 않는 곳에 만들어 항목 이름을 모은다 (목록에는 없는 설정들). */
    private void harvestEditorParts(java.util.List<String[]> out) {
        LinearLayout savedList = list;
        ExpressiveSwitch savedClipSwitch = clipImagesSwitch;
        KeyEditPanel.Host idle = new KeyEditPanel.Host() {
            @Override public com.alternative_studios.newswipe.keyboard.Key refreshNow() { return null; }
            @Override public void close() { }
            @Override public void onSizeChanged(String prefKey) { }
        };
        harvesting = true;
        try {
            for (int a = 0; a < KeyboardPreview.AREAS; a++) {
                LinearLayout scratch = new LinearLayout(this);
                scratch.setOrientation(LinearLayout.VERTICAL);
                list = scratch;
                if (a == KeyboardPreview.AREA_TOOLBAR) {
                    KeyEditPanel panel = new KeyEditPanel(this, prefs, idle);
                    panel.show(KeyboardPreview.Selection.toolbar(), null);
                    scratch.addView(panel.view());
                }
                if (a == KeyboardPreview.AREA_LETTERS) {
                    // 글자 키 창은 자판마다 항목이 달라 자판마다 모은다 ("t:자판:이름").
                    for (int t = 0; t < KeyboardPreview.TAB_NAMES.length; t++) {
                        LinearLayout tabScratch = new LinearLayout(this);
                        tabScratch.setOrientation(LinearLayout.VERTICAL);
                        list = tabScratch;
                        letterTab = t;
                        buildArea(a);
                        collectLabels(EDITOR_PART + "t:" + t + ":" + KeyboardPreview.areaName(a, t), tabScratch, out,
                                new View[1], new String[1][]);
                    }
                    letterTab = KeyboardPreview.TAB_KOREAN;
                    continue;
                }
                buildArea(a);
                collectLabels(EDITOR_PART + "a:" + a + ":" + KeyboardPreview.AREA_NAMES[a], scratch, out, new View[1],
                        new String[1][]);
            }
            LinearLayout boardScratch = new LinearLayout(this);
            boardScratch.setOrientation(LinearLayout.VERTICAL);
            list = boardScratch;
            buildPad(-1);
            collectLabels(EDITOR_PART + "b:0:키보드 여백", boardScratch, out, new View[1], new String[1][]);
            com.alternative_studios.newswipe.keyboard.KeyboardLayout l =
                    com.alternative_studios.newswipe.keyboard.KeyboardLayout.korean(prefs);
            for (int type : HARVEST_KEYS) {
                com.alternative_studios.newswipe.keyboard.Key k = firstKey(l, type);
                if (k == null) continue;
                KeyEditPanel panel = new KeyEditPanel(this, prefs, idle);
                panel.show(KeyboardPreview.Selection.key(k, KeyboardPreview.TAB_KOREAN), k);
                collectLabels(EDITOR_PART + "k:" + type + ":" + panel.title(), panel.view(), out, new View[1],
                        new String[1][]);
            }
        } catch (RuntimeException ignored) {
            // 못 만든 창이 있어도 나머지 검색 목록은 쓴다.
        } finally {
            harvesting = false;
            list = savedList;
            clipImagesSwitch = savedClipSwitch;
        }
        // 키 창의 '길게 누르면' 옆에 보이는 지금 기능 이름(없음·이모지 열기 등)과 되돌리기 버튼은 설정 이름이 아니다.
        java.util.Set<String> values = new java.util.HashSet<>();
        for (String id : com.alternative_studios.newswipe.keyboard.SwipeAction.idsForSpaceLongPress()) {
            values.add(com.alternative_studios.newswipe.keyboard.SwipeAction.label(id));
        }
        values.add("기본값으로 되돌리기");
        values.addAll(java.util.Arrays.asList(INPUT_MODES));   // 쌍자음·이중모음 입력 줄 옆의 지금 값
        out.removeIf(e -> e[0].startsWith(EDITOR_PART) && values.contains(e[1]));
    }

    private static com.alternative_studios.newswipe.keyboard.Key firstKey(
            com.alternative_studios.newswipe.keyboard.KeyboardLayout l, int type) {
        if (l == null) return null;
        for (com.alternative_studios.newswipe.keyboard.KeyboardLayout.Row row : l.rows) {
            for (com.alternative_studios.newswipe.keyboard.Key k : row.keys) if (k.type == type) return k;
        }
        return null;
    }

    /** 검색 결과로 키보드 사용자화에 들어왔다: 그 영역·키의 창을 열고 항목을 찾아 깜빡인다. */
    private void openEditorTarget(String target, String label) {
        if (isFinishing() || isDestroyed() || editorPreview == null) return;
        String[] parts = target.split(":", 4);
        if (parts.length < 4) return;
        KeyboardPreview.Selection sel;
        try {
            int n = Integer.parseInt(parts[2]);
            if ("a".equals(parts[1])) {
                editorPreview.setLayer(KeyboardPreview.LAYER_AREA);
                sel = KeyboardPreview.Selection.area(n);
            } else if ("t".equals(parts[1])) {   // 글자 키 창: 그 자판으로 바꿔 연다 (탭 버튼도 맞춘다)
                if (n < 0 || n >= KeyboardPreview.TAB_NAMES.length) return;
                if (editorTabs != null && editorPreview.tab() != n) editorTabs.getChildAt(n).performClick();
                editorPreview.setTab(n);
                editorPreview.setLayer(KeyboardPreview.LAYER_AREA);
                sel = KeyboardPreview.Selection.area(KeyboardPreview.AREA_LETTERS);
            } else if ("b".equals(parts[1])) {
                editorPreview.setLayer(KeyboardPreview.LAYER_BOARD);
                sel = KeyboardPreview.Selection.board();
            } else {
                com.alternative_studios.newswipe.keyboard.Key k = firstKey(editorPreview.layout(), n);
                if (k == null) return;
                editorPreview.setLayer(KeyboardPreview.LAYER_KEY);
                sel = KeyboardPreview.Selection.key(k, KeyboardPreview.TAB_KOREAN);
            }
        } catch (NumberFormatException e) {
            return;
        }
        onPreviewTap(sel);
        // 창이 올라온 뒤에 찾는다 (배치가 끝나야 위치를 안다).
        sheet.scroll().postDelayed(() -> highlightItem(sheet.scroll(), sheet.body(), label), 450);
    }

    /** 화면 하나를 보이지 않는 곳에 만들어 항목 이름을 모으고 버린다. 진짜 화면이 쓰는 값은 건드리지 않게 되돌려 놓는다. */
    private void harvestScreen(String id, java.util.List<String[]> out) {
        LinearLayout savedList = list, savedBody = oneHandBody, savedColumn = oneHandColumn, savedSide = oneHandSide;
        View savedLift = oneHandLift;
        com.alternative_studios.newswipe.keyboard.KeyboardView savedKeys = oneHandKeys;
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
            oneHandBody = savedBody;
            oneHandColumn = savedColumn;
            oneHandSide = savedSide;
            oneHandLift = savedLift;
            oneHandKeys = savedKeys;
            clipImagesSwitch = savedClipSwitch;
            scratch.removeAllViews();
        }
    }

    /**
     * 만들어 둔 화면의 글자를 위에서 아래로 훑는다. 크기와 색으로 구분한다:
     * 15~18sp = 항목 이름(스위치·슬라이더·버튼), 14sp 강조색 = 소제목, 그 항목 이름 바로 아래(같은 칸)의 작은 글자 = 설명.
     * 슬라이더 값(예: "12ms")이나 화면 아래 안내문은 모으지 않는다.
     */
    private void collectLabels(String id, View v, java.util.List<String[]> out, View[] lastParent, String[][] last) {
        if (v instanceof TextView) {
            TextView t = (TextView) v;
            String text = t.getText().toString().trim();
            if (!text.isEmpty()) {
                float px = t.getTextSize();
                boolean label = px >= spPx(15) - 0.5f && px <= spPx(18) + 0.5f;
                boolean heading = !label && Math.abs(px - spPx(14)) < 0.5f && t.getCurrentTextColor() == accentColor;
                if (label || heading) {
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

    // ---- 검색 목록 파일 (앱 버전이 바뀌면 다시 모은다)

    private File searchCacheFile() {
        return new File(getFilesDir(), "settings_search.tsv");
    }

    private String searchCacheStamp() {
        try {
            // 뒤의 꼬리표는 메뉴 구성이 바뀌면 올린다 (같은 앱 버전이라도 예전 메뉴로 모은 목록을 쓰지 않게).
            return "v" + getPackageManager().getPackageInfo(getPackageName(), 0).getLongVersionCode() + "-k10";
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

    private void markItem(TextView label, View row) {
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

    /** root(스크롤 안의 내용) 안에서 이름이 label인 항목을 찾아 스크롤하고 깜빡인다. */
    private void highlightItem(ScrollView scroll, ViewGroup root, String label) {
        if (isFinishing() || isDestroyed()) return;
        TextView target = findLabel(root, label, true);
        if (target == null) target = findLabel(root, label, false);
        if (target == null) return;
        View hidden = outermostHidden(target, root);
        View box;
        if (hidden != null) {
            box = controllerOf(hidden);
        } else {
            java.lang.ref.WeakReference<View> ref = itemRow.get(target);
            box = ref == null ? null : ref.get();
            if (box == null) box = target;
        }
        if (box == null) return;
        int y = 0;
        for (View v = box; v != null && v != root; v = v.getParent() instanceof View ? (View) v.getParent() : null) {
            y += v.getTop();
        }
        scroll.smoothScrollTo(0, Math.max(0, y - Ui.dp(this, 96)));
        flash(box, root);
    }

    /**
     * 반투명 강조색을 항목 위에 두 번 깔았다 걷는다 (부드럽게 나타났다 사라진다).
     * 글자가 칸 가장자리에 붙어 보이지 않도록 항목보다 좌우·위아래로 조금 더 넓게 칠한다. 항목 안에서는 그릴 수 없는 바깥 여백이라
     * 항목이 아니라 항목을 담은 부모의 위에 그린다.
     */
    private void flash(View box, View root) {
        int w = box.getWidth(), h = box.getHeight();
        if (w == 0 || h == 0) return;
        int dx = Ui.dp(this, 10), dy = Ui.dp(this, 2);
        final View host = box.getParent() instanceof ViewGroup ? (View) box.getParent() : box;
        android.graphics.Rect bounds = host == box
                ? new android.graphics.Rect(-dx, -dy, w + dx, h + dy)
                : new android.graphics.Rect(box.getLeft() - dx, box.getTop() - dy, box.getRight() + dx, box.getBottom() + dy);
        android.graphics.drawable.GradientDrawable d = Ui.round(accentColor, Ui.dp(this, 12));
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
        if (v instanceof TextView && (!shownOnly || v.isShown()) && label.contentEquals(((TextView) v).getText())) {
            return (TextView) v;
        }
        if (v instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) {
                TextView found = findLabel(g.getChildAt(i), label, shownOnly);
                if (found != null) return found;
            }
        }
        return null;
    }

    private void openSection(String id, String highlight) {
        Intent i = new Intent(this, SettingsActivity.class);
        if (id.startsWith(EDITOR_PART)) i.putExtra(EXTRA_SECTION, KEYBOARD).putExtra(EXTRA_TARGET, id);
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
    /** 설정 창: 목록 위에 겹쳐 아래에서 올라오는 시트 (영역·키·키보드 여백). */
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
            tabs.addView(editorTabs = Ui.choiceRow(this, KeyboardPreview.TAB_NAMES, 0, accentColor, onAccentColor,
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
            pageDots = new PageDots(this, PAGE_NAMES, PAGE_DESCS, AppTheme.of(this), editorPreview::setLayer);
            pageDots.setPage(editorPreview.layer());
            pinnedBottom.addView(pageDots, matchWrap());
            pinnedBottom.addView(editorPreview, matchWrap());
            pinnedBottom.setVisibility(View.VISIBLE);

            buildSheet();

            // 목록의 스위치·슬라이더나 다른 편집 화면에서 값을 바꾸면 미리보기를 다시 그린다 (한 프레임에 한 번).
            editorUndo = new EditorUndo(prefs, uiHandler, this::updateUndoButton);
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

    /** 키보드 사용자화의 기본 목록을 box에 만든다 (키 모양, 숫자 줄, 가로 모드). */
    private void fillOverview(LinearLayout box) {
        LinearLayout saved = list;
        list = box;
        try {
            // 여기에는 키보드 전체에 걸리고 어느 영역·키 창에도 없는 것만 둔다. 나머지는 미리보기에서 영역이나 키를 눌러 바꾼다.
            buildLook();
            // 숫자 줄은 꺼 두면 미리보기에 없어 누를 수 없으므로 여기서도 그 설정 창을 연다.
            LinearLayout numberRow = section("숫자 줄");
            numberRow.addView(button("숫자 줄 설정", v -> openNumberRowSheet()));
            numberRow.addView(note("자판 위에 숫자 줄을 보이도록 설정할 수 있습니다."));
            LinearLayout landscape = section("가로 모드");
            landscape.addView(button("가로 모드 키보드 사용자화", v -> openSection(LANDSCAPE_AREA)));
            landscape.addView(note("화면을 가로로 돌렸을 때의 자판 모양을 따로 정합니다."));
            if (!harvesting) list.addView(editorResetButton());
        } finally {
            list = saved;
        }
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
        Toast.makeText(this, "마지막 변경을 되돌렸습니다", Toast.LENGTH_SHORT).show();
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
    /** 숫자 줄 영역의 설정 창을 연다 (숫자 줄을 꺼서 미리보기에 없을 때도). */
    private void openNumberRowSheet() {
        if (editorPreview == null) return;
        if (editorPreview.layer() != KeyboardPreview.LAYER_AREA) editorPreview.setLayer(KeyboardPreview.LAYER_AREA);
        onPreviewTap(KeyboardPreview.Selection.area(KeyboardPreview.AREA_NUMBER));
    }

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
        boolean area = s.kind == KeyboardPreview.SEL_AREA;
        int areaId = area ? s.area() : -1;
        // 키보드 층(키보드 전체)은 여백 슬라이더 창.
        boolean board = s.kind == KeyboardPreview.SEL_BOARD;
        if (board) {
            keyPanel.view().setVisibility(View.GONE);
            fillAreaBox(() -> {
                buildPad(-1);
                addAreaReset(-1);
            });
            sheet.setTitle("키보드 여백 설정");
            return;
        }
        boolean panel = !area || areaId == KeyboardPreview.AREA_TOOLBAR;
        if (panel) keyPanel.show(area ? KeyboardPreview.Selection.toolbar() : s, editorPreview.selectedKey());
        keyPanel.view().setVisibility(panel ? View.VISIBLE : View.GONE);
        fillArea(areaId);
        sheet.setTitle((area ? KeyboardPreview.areaName(areaId, editorPreview.tab()) : keyPanel.title()) + " 설정");
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
        fillAreaBox(() -> {
            buildArea(areaId);
            addAreaReset(areaId);
        });
    }

    /** 영역·키보드 여백 창 맨 아래의 '기본값으로 되돌리기' (그 창이 담당하는 값만). area -1은 키보드 전체 여백. */
    private void addAreaReset(int area) {
        String name = area < 0 ? "키보드 여백" : KeyboardPreview.areaName(area, letterTab) + " 설정";
        int tab = letterTab;
        list.addView(ResetDialog.button(this, name, () -> {
            prefs.resetEditorScope(area < 0 ? Prefs.SCOPE_BOARD : Prefs.AREA_SCOPES[area]);
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

    /**
     * 여백 창: area가 -1이면 키보드 전체 둘레의 여백, 아니면 그 영역의 여백 (키보드 전체 여백 안쪽에 더한다).
     * 미리보기의 손잡이를 끌면 이 슬라이더도 같이 움직인다.
     */
    private void buildPad(int area) {
        if (area < 0) {
            LinearLayout c = section("키보드 여백");
            c.addView(sizeSlider("왼쪽 여백", Prefs.PAD_LEFT, prefs::padLeftDp, 0, 24, 1, "dp"));
            c.addView(sizeSlider("오른쪽 여백", Prefs.PAD_RIGHT, prefs::padRightDp, 0, 24, 1, "dp"));
            c.addView(sizeSlider("위쪽 여백", Prefs.PAD_TOP, prefs::padTopDp, 0, 24, 1, "dp"));
            c.addView(sizeSlider("아래쪽 여백", Prefs.PAD_BOTTOM, prefs::padBottomDp, 0, 48, 1, "dp"));
            c.addView(note("키보드 전체 둘레의 여백입니다. 영역마다 따로 주는 여백은 '영역 설정' 페이지에서 그 영역을 눌러 "
                    + "창 맨 아래의 '여백'에서 정합니다."));
            return;
        }
        LinearLayout c = section("여백");
        c.addView(areaPadSlider("왼쪽 여백", area, Prefs.SIDE_LEFT));
        c.addView(areaPadSlider("오른쪽 여백", area, Prefs.SIDE_RIGHT));
        c.addView(areaPadSlider("위쪽 여백", area, Prefs.SIDE_TOP));
        c.addView(areaPadSlider("아래쪽 여백", area, Prefs.SIDE_BOTTOM));
    }

    private View areaPadSlider(String label, int area, int side) {
        return sizeSlider(label, Prefs.areaPadKey(area, side), () -> prefs.areaPadDp(area, side), 0, Prefs.AREA_PAD_MAX, 1, "dp");
    }

    /** 영역 설정 창의 분리된 안내 칸: 하나하나의 설정은 키 설정 페이지에 있다. pick은 조사가 붙은 말 ("키를", "버튼을"). */
    private void addKeyPageNote(String what, String pick) {
        card().addView(note(what + " 하나하나의 설정은 아래쪽 미리보기의 오른쪽 '키 설정' 페이지에서 원하는 " + pick
                + " 선택해 바꿀 수 있습니다."));
    }

    private void buildArea(int areaId) {
        switch (areaId) {
            case KeyboardPreview.AREA_TOOLBAR:
                // 도구 막대 칸은 위의 KeyEditPanel이 맡는다. 클립보드·이모지는 메인 화면의 '클립보드 및 이모지' 메뉴에 있다.
                // 도구 막대 표시를 끄면 안내와 여백도 감춘다 (설정 칸은 KeyEditPanel이 같이 감춘다).
                LinearLayout tb = new LinearLayout(this);
                tb.setOrientation(LinearLayout.VERTICAL);
                tb.setVisibility(prefs.toolbar() ? View.VISIBLE : View.GONE);
                keyPanel.setOnToolbarShown(on -> Ui.setVisibleAnimated(tb, on));
                list.addView(tb, matchWrap());
                LinearLayout savedList = list;
                list = tb;
                try {
                    addKeyPageNote("도구 막대 버튼", "버튼을");
                    buildPad(areaId);
                } finally {
                    list = savedList;
                }
                break;
            case KeyboardPreview.AREA_NUMBER: {
                LinearLayout c = section("모양");
                // 숫자 줄을 끄면 나머지 항목은 쓸 일이 없어 감춘다.
                LinearLayout rest = new LinearLayout(this);
                rest.setOrientation(LinearLayout.VERTICAL);
                rest.addView(sizeSlider("숫자 줄 높이", Prefs.NUMBER_ROW_HEIGHT, prefs::numberRowHeight,
                        Prefs.NUMBER_ROW_HEIGHT_MIN, Prefs.NUMBER_ROW_HEIGHT_MAX, 5, "%"));
                rest.addView(numberHintToggle());
                // 여백 섹션도 숫자 줄을 켰을 때만 보인다.
                LinearLayout padBox = new LinearLayout(this);
                padBox.setOrientation(LinearLayout.VERTICAL);
                c.addView(toggle("숫자 줄 표시", "자판 위에 1~0 숫자 줄을 보여 줍니다.", Prefs.NUMBER_ROW, prefs.numberRow(), rest, padBox));
                c.addView(rest, matchWrap());
                list.addView(padBox, matchWrap());
                LinearLayout saved = list;
                list = padBox;
                try {
                    addKeyPageNote("숫자 줄 키", "키를");
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
                if (ko) buildKoreanLayoutSection();
                LinearLayout size = section("모양");
                size.addView(sizeSlider("글자 키 높이", Prefs.KEYBOARD_HEIGHT, prefs::keyboardHeight, 70, 140, 5, "%"));
                if (ko) addKoreanInput(section("입력"));
                if (en) addEnglishInput(section("입력"));   // ⌫ 인식 범위는 ⌫ 키 설정에
                LinearLayout lp = section("길게 누르기");
                addLetterLongPress(lp);
                lp.addView(popupHintToggle());
                LinearLayout swipe = section("밀기");
                swipe.addView(swipeDistanceSlider());
                addKeyboardSwipe(swipe);
                addKeyPageNote(KeyboardPreview.areaName(areaId, letterTab), "키를");
                buildPad(areaId);
                break;
            }
            default: {
                // 기능키 전체에 걸리는 것만 둔다. 스페이스바·⌫·기호 키·🌐︎처럼 키 하나에 매인 설정은 '키' 페이지의 그 키에 있다.
                LinearLayout fn = section("모양");
                fn.addView(sizeSlider("기능키 높이", Prefs.FN_ROW_HEIGHT, prefs::fnRowHeight,
                        Prefs.FN_ROW_HEIGHT_MIN, Prefs.FN_ROW_HEIGHT_MAX, 5, "%"));
                addFunctionKeyLayout(fn);
                LinearLayout swipe = section("길게 누르기·밀기");
                swipe.addView(fnLongPressSlider());
                swipe.addView(fnSwipeDistanceSlider());
                addKeyPageNote("기능키", "키를");
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
    private static final String[] PAGE_NAMES = {"키보드 여백 설정", "영역 설정", "키 설정"};
    /** 미리보기 페이지 설명 (PAGE_NAMES와 같은 차례). */
    private static final String[] PAGE_DESCS = {"상하좌우 여백을 조절합니다", "각 영역의 모습과 기능을 설정합니다",
            "각 키의 기능과 동작을 설정합니다"};

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
        TextView b = ResetDialog.button(this, "키보드 사용자화", () -> { });
        b.setOnClickListener(v -> {
            String[][] groups = Prefs.editorResetChoices();
            String[] names = new String[groups.length];
            boolean[] checked = new boolean[names.length];
            for (int i = 0; i < names.length; i++) names[i] = groups[i][1];
            android.app.AlertDialog[] dialog = new android.app.AlertDialog[1];
            dialog[0] = AppTheme.dialogBuilder(this)
                    .setTitle("기본값으로 되돌릴 설정")
                    .setMultiChoiceItems(names, checked, (d, which, on) -> {
                        checked[which] = on;
                        boolean any = false;
                        for (boolean c : checked) any |= c;
                        dialog[0].getButton(android.app.AlertDialog.BUTTON_POSITIVE).setEnabled(any);
                    })
                    .setPositiveButton("되돌리기", (d, w) -> {
                        for (int i = 0; i < checked.length; i++) if (checked[i]) prefs.resetEditorScope(groups[i][0]);
                        importGeneration++;   // 뒤에 쌓인 설정 화면도 돌아오면 기본값으로 다시 그린다
                        recreate();
                        Toast.makeText(this, "기본값으로 되돌렸습니다", Toast.LENGTH_SHORT).show();
                    })
                    .setNegativeButton("취소", null)
                    .show();
            dialog[0].getButton(android.app.AlertDialog.BUTTON_POSITIVE).setEnabled(false);
        });
        return b;
    }

    /** 키 모양: 글자 크기, 곡률, 키 간격, 그림자, 누름 미리보기. 글자 키 높이는 글자 키 영역 창에, 여백은 미리보기의 키보드·여백 페이지에 있다. */
    private void buildLook() {
        LinearLayout look = section("키 모양");
        look.addView(slider("키 글자 크기", Prefs.KEY_TEXT_SIZE, prefs.keyTextSize(), 70, 140, 5, "%"));
        look.addView(slider("키 곡률", Prefs.KEY_RADIUS, prefs.keyRadiusDp(), 0, 24, 1, "dp"));
        look.addView(slider("키 사이 좌우 간격", Prefs.KEY_GAP_X, prefs.keyGapXDp(), 0, 12, 1, "dp"));
        look.addView(slider("키 사이 상하 간격", Prefs.KEY_GAP_Y, prefs.keyGapYDp(), 0, 20, 1, "dp"));
        View shadowStrength = subGroup(slider("그림자 세기", Prefs.KEY_SHADOW_STRENGTH, prefs.keyShadowStrength(), 10, 100, 5, "%"));
        look.addView(toggle("키 그림자", "키 아래에 그림자를 그려 입체감을 줍니다.",
                Prefs.KEY_SHADOW, prefs.keyShadow(), shadowStrength));
        look.addView(shadowStrength);
        look.addView(toggle("키 누름 미리보기", "누른 키를 말풍선으로 크게 보여 줍니다.",
                Prefs.KEY_PREVIEW, prefs.keyPreview()));
    }

    private View popupHintToggle() {
        return toggle("글자 키 힌트 없애기", "글자 키의 오른쪽 위에 표시되는 길게 눌러 입력할 문자 힌트를 없앱니다.",
                Prefs.POPUP_HINT_HIDDEN, prefs.popupHintHidden());
    }

    // ---- 목록과 영역 편집 창이 함께 쓰는 항목 (같은 설정이 두 곳에 보여도 이름·설명이 어긋나지 않게 한곳에서 만든다)

    private View numberHintToggle() {
        return toggle("숫자 키 힌트 없애기", "숫자 키(1~0)에서만 길게 눌러 입력할 문자 힌트를 없앱니다.",
                Prefs.NUMBER_HINT_HIDDEN, prefs.numberHintHidden());
    }

    /** 맨 아래 줄 기능키의 순서·유무와 격자 정렬. */
    private void addFunctionKeyLayout(LinearLayout c) {
        c.addView(button("기능키 순서·유무 사용자화", v -> startActivity(new Intent(this, BottomKeysEditorActivity.class))));
        c.addView(note("맨 아래 줄 기능키의 순서와 유무를 바꿉니다."));
        // 바꾸면 미리보기도 바로 다시 그린다.
        c.addView(toggle("격자 정렬", GRID_DESC, prefs.gridLayout(), on -> {
            prefs.raw().edit().putBoolean(Prefs.GRID_LAYOUT, on).apply();
            refreshLayoutPreview();
        }));
    }

    /** 쌍자음·이중모음 입력 방식의 이름 (Prefs.INPUT_* 차례). */
    private static final String[] INPUT_MODES = {"아래로 밀기", "두 번 연속으로 누르기", "둘 다"};

    /** 한글 글자 키의 입력 동작: 쌍자음·이중모음 입력 방식 (아래로 밀기·두 번 연속으로 누르기·둘 다). */
    private void addKoreanInput(LinearLayout input) {
        View doubleTapTime = subGroup(
                slider("쌍자음 인식 시간", Prefs.DOUBLE_TAP_CONSONANT_MS, prefs.doubleTapConsonantMs(),
                        80, 500, 10, "ms"),
                note("두 번째 누르기가 이 시간 안에 들어와야 쌍자음이 됩니다. 값을 줄이면 '학교'가 '하꾜'로 바뀌는 일이 줄고, "
                        + "대신 쌍자음을 낼 때 더 빨리 눌러야 합니다."));
        doubleTapTime.setVisibility(prefs.consonantInputMode() != Prefs.INPUT_SWIPE || harvesting ? View.VISIBLE : View.GONE);
        SettingsWidgets.ValueRow[] consonant = new SettingsWidgets.ValueRow[1];
        consonant[0] = widgets.valueRow("쌍자음 입력", INPUT_MODES[prefs.consonantInputMode()], v ->
                pickInputMode("쌍자음 입력", prefs.consonantInputMode(), Prefs.INPUT_SWIPE, mode -> {
                    prefs.setConsonantInputMode(mode);
                    consonant[0].set(INPUT_MODES[mode]);
                    Ui.setVisibleAnimated(doubleTapTime, mode != Prefs.INPUT_SWIPE);
                }));
        input.addView(consonant[0].view);
        input.addView(doubleTapTime);
        SettingsWidgets.ValueRow[] vowel = new SettingsWidgets.ValueRow[1];
        vowel[0] = widgets.valueRow("이중모음 입력", INPUT_MODES[prefs.vowelInputMode()], v ->
                pickInputMode("이중모음 입력", prefs.vowelInputMode(), Prefs.INPUT_BOTH, mode -> {
                    prefs.setVowelInputMode(mode);
                    vowel[0].set(INPUT_MODES[mode]);
                }));
        input.addView(vowel[0].view);
        input.addView(note("아래로 밀기는 자음·모음 키를 아래로 밀어(ㄱ→ㄲ, ㅏ→ㅑ), 두 번 연속으로 누르기는 같은 키를 두 번 눌러(ㄱㄱ→ㄲ, ㅏㅏ→ㅑ) "
                + "입력합니다. 키마다 아래로 밀기 글자를 따로 정했으면 그 글자를 씁니다."));
    }

    /** 쌍자음·이중모음 입력 방식을 고르는 창 (아래로 밀기·두 번 연속으로 누르기·둘 다). */
    private void pickInputMode(String title, int current, int defaultMode, java.util.function.IntConsumer onPick) {
        AppTheme.dialogBuilder(this)   // 다른 고르기 창(길게 누르기·밀기 기능)과 같은 모양: 목록, 기본값, 취소
                .setTitle(title)
                .setSingleChoiceItems(INPUT_MODES, current, (d, which) -> {
                    d.dismiss();
                    if (which != current) onPick.accept(which);
                })
                .setNeutralButton("기본값", (d, w) -> {
                    if (current != defaultMode) onPick.accept(defaultMode);
                })
                .setNegativeButton("취소", null)
                .show();
    }

    /** 영어 글자 키의 입력 동작: 자동 대문자, 아래로 밀어 대문자, 이메일 입력란. */
    private void addEnglishInput(LinearLayout input) {
        input.addView(toggle("영어 자동 대문자", "문장 첫 글자를 대문자로 시작합니다.", Prefs.AUTO_CAP, prefs.autoCap()));
        input.addView(toggle("영어 자판 아래로 밀어 대문자 입력",
                "영어 자판에서 글자 키를 아래로 밀면 그 키의 대문자를 입력합니다. 켜면 각 키 설정의 아래로 밀기는 대문자로 고정됩니다.",
                Prefs.ENGLISH_SWIPE_UPPER, prefs.englishSwipeUpper()));
        input.addView(toggle("이메일 입력란은 영어로 시작",
                "이메일 주소 입력란이나 앱이 영어 입력을 요청한 칸(아이디 입력란 등)에서는 영어 자판으로 시작하고, "
                        + "그 칸을 떠나면 원래 언어로 돌아갑니다. 웹 브라우저 주소창은 검색에도 쓰이므로 바꾸지 않습니다.",
                Prefs.ENGLISH_FIELDS, prefs.englishFields()));
    }

    /** 글자 키 길게 누르기 시간 (길게 눌러 문자 입력과 연속 입력이 함께 쓴다. 무엇을 할지는 키마다 정한다). */
    private void addLetterLongPress(LinearLayout c) {
        c.addView(slider("문자 길게 누르기 시간", Prefs.LONG_PRESS_MS, prefs.longPressMs(), 100, 800, 20, "ms"));
    }

    /** 모든 기능키(⌫·기호 키·🌐︎·Shift 키·엔터 키·Fn 키 등)가 같은 시간을 쓴다. */
    private View fnLongPressSlider() {
        return slider("기능키 길게 누르기 시간", Prefs.DELETE_PRESS_MS, prefs.deletePressMs(), 200, 800, 50, "ms");
    }

    private View swipeDistanceSlider() {
        return slider("미는 거리", Prefs.SWIPE_THRESHOLD, prefs.swipeThresholdDp(), 12, 48, 2, "dp");
    }

    private View fnSwipeDistanceSlider() {
        return slider("기능키 미는 거리", Prefs.FN_SWIPE_THRESHOLD, prefs.fnSwipeThresholdDp(), 12, 48, 2, "dp");
    }

    /** 키보드 밀기 편집(한 손가락·두 손가락)과 위로 밀어서 추천 단어 입력. */
    private void addKeyboardSwipe(LinearLayout board) {
        board.addView(button("키보드 밀기 사용자화", v ->
                startActivity(SwipeActionEditorActivity.intent(this, KeyboardSwipes.SPEC))));
        board.addView(note("자판을 한 손가락·두 손가락으로 위·아래·왼쪽·오른쪽으로 밀 때 실행할 기능을 정합니다. "
                + "한 손가락은 그 방향에 밀어서 입력할 글자가 없을 때 쓰입니다."));
        board.addView(toggle("위로 밀어서 추천 단어 입력",
                "추천 단어가 떠 있을 때 자판의 왼쪽·가운데·오른쪽에서 위로 밀면 첫째·둘째·셋째 추천 단어를 입력합니다. ",
                Prefs.SWIPE_PICK_SUGGESTION, prefs.swipePickSuggestion()));
    }

    /** 이모지 창의 이모지 크기 (기본·크게·더 크게). 고르면 아래 견본 글자도 같은 크기로 바뀐다. */
    private View emojiSizeRow() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(0, Ui.dp(this, 8), 0, Ui.dp(this, 4));
        TextView t = new TextView(this);
        t.setText("이모지 크기");
        t.setTextColor(textColor);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        markItem(t, box);
        box.addView(t);
        TextView sample = new TextView(this);
        sample.setText("😀 😍 👍 🎉");
        sample.setGravity(Gravity.CENTER);
        sample.setTextSize(TypedValue.COMPLEX_UNIT_SP, com.alternative_studios.newswipe.emoji.EmojiPanel.textSp(prefs.emojiSize()));
        box.addView(choiceRowNow(new String[]{"기본", "크게", "더 크게"}, prefs.emojiSize(), i -> {
            prefs.raw().edit().putInt(Prefs.EMOJI_SIZE, i).apply();
            sample.setTextSize(TypedValue.COMPLEX_UNIT_SP, com.alternative_studios.newswipe.emoji.EmojiPanel.textSp(i));
        }));
        LinearLayout.LayoutParams slp = matchWrap();
        slp.topMargin = Ui.dp(this, 6);
        box.addView(sample, slp);
        return box;
    }

    /** 한글 자판 배열: 배열 고르기와 설명, 자·모음 균형 배열(균형 배열 사용자화). */
    private void buildKoreanLayoutSection() {
        LinearLayout korean = section("한글 자판 배열");
        String[] koreanLayouts = {Prefs.KOREAN_LAYOUT_DUBEOLSIK, Prefs.KOREAN_LAYOUT_8, Prefs.KOREAN_LAYOUT_D7};
        int selected = prefs.koreanDubeolsik() ? 0 : prefs.koreanD7() ? 2 : 1;
        // 배열을 고르면 화면을 다시 만들지 않고 안내문·토글·미리보기를 그 자리에서 바꾼다 (선택 버튼의 스프링이 끊기지 않게).
        TextView layoutNote = note(layoutNoteText());
        // 모든 한글 배열에서 쓸 수 있고, 배열마다 따로 켜고 끈다. 격자 정렬과 함께 쓰면 기능키가 나뉜 자음 키 폭에 맞춰진다.
        // 토글의 하위 항목(강조선·들여쓰기)으로 보이는 버튼이다.
        // 버튼은 위아래 여백이 있어, 버튼 자체를 접으면 높이가 0이 된 뒤 남은 여백이 한꺼번에 사라져 끊겨 보인다.
        // 그래서 여백 없는 틀(fold)로 감싸 그 틀을 펼치고 접는다.
        // 두벌식에서만: 영어 자판도 같은 모양으로 나눈다. 균형 배열의 하위 항목으로 균형 배열 사용자화와 한 묶음에 둔다.
        View englishToggle = toggle("영어 자판도 균형 배열",
                "영어 자판도 두벌식처럼 가운데에서 나눠 왼쪽 5·5·4키, 오른쪽 5·4·3키로 놓습니다. 경계와 키 폭·위치 설정을 함께 씁니다.",
                prefs.raw().getBoolean(Prefs.ENGLISH_BALANCED, false), on -> {
                    prefs.raw().edit().putBoolean(Prefs.ENGLISH_BALANCED, on).apply();
                    refreshLayoutPreview();
                });
        View englishBalanced = fold(englishToggle);
        englishBalanced.setVisibility(englishBalancedShown() ? View.VISIBLE : View.GONE);
        View keyArea = fold(subGroup(button("균형 배열 사용자화", v -> openSection(KEY_AREA)), englishBalanced));
        keyArea.setVisibility(prefs.balancedLayout() ? View.VISIBLE : View.GONE);
        View balancedRow = toggle("자·모음 균형 배열",
                "자판을 둘로 나눠 왼쪽에는 자음을, 오른쪽에는 모음을 놓습니다.",
                prefs.balancedLayout(), on -> {
                    prefs.raw().edit().putBoolean(prefs.balancedLayoutKey(), on).apply();
                    Ui.setVisibleAnimated(keyArea, on);
                    refreshLayoutPreview();
                });
        LinearLayout choices = choiceRowNow(new String[]{"두벌식", "단모음", "NewSwipe 단모음"}, selected,
                i -> {
                    prefs.setKoreanLayout(koreanLayouts[i]);
                    layoutNote.setText(layoutNoteText());
                    // 균형 배열은 배열마다 따로 기억하므로 고른 배열의 값으로 스위치를 맞춘다.
                    ((ExpressiveSwitch) ((ViewGroup) balancedRow).getChildAt(1)).setCheckedSilently(prefs.balancedLayout());
                    Ui.setVisibleAnimated(keyArea, prefs.balancedLayout());
                    Ui.setVisibleAnimated(englishBalanced, englishBalancedShown());
                    refreshLayoutPreview();
                });
        korean.addView(choices);
        korean.addView(layoutNote);
        korean.addView(balancedRow);
        korean.addView(keyArea);
    }

    /** '영어 자판도 균형 배열'은 두벌식에서만 보인다 (균형 배열을 켰을 때 그 하위 묶음과 함께. 설정 검색 목록에는 늘 넣는다). */
    private boolean englishBalancedShown() {
        return harvesting || prefs.koreanDubeolsik();
    }

    /** 고른 한글 배열의 설명. */
    private String layoutNoteText() {
        if (prefs.koreanDubeolsik()) {
            return "두벌식은 컴퓨터 키보드와 같은 표준 한글 배열입니다. Shift를 누르고 ㅂ·ㅈ·ㄷ·ㄱ·ㅅ·ㅐ·ㅔ를 치면 "
                    + "ㅃ·ㅉ·ㄸ·ㄲ·ㅆ·ㅒ·ㅖ가 들어갑니다.";
        }
        return prefs.koreanD7()
                ? "NewSwipe 단모음은 거센소리 일부(ㅊ, ㅌ)를 빼 한 줄을 7칸으로 줄인 배열입니다. "
                        + "ㅊ·ㅌ은 ㅈ·ㄷ을 오른쪽으로 밀어 입력합니다."
                : "단모음은 두벌식 키보드에서 ㅣ계 이중모음(ㅑ·ㅕ·ㅛ·ㅠ)을 빼 한 줄을 8칸으로 줄인 배열입니다.";
    }

    private static final String GRID_DESC = "아래 줄의 기능키를 한글 키와 같은 폭으로 해 줄을 맞춥니다. "
            + "오쏘리니어 키보드에서 영감을 받았습니다.";
    /** 균형 배열 사용자화 화면 아래의 미리보기 (그 화면에서만). */
    private KeyboardPreview balancedPreview;

    /**
     * 균형 배열 사용자화: 키보드 사용자화처럼 화면 아래에 실제 키보드와 같은 미리보기를 붙이고, 자음·모음 쪽 윤곽선의
     * ↔ 버튼(좌우로 끌면 키 폭, 길게 누른 뒤 끌면 가로 위치)과 경계선 손잡이(자·모음 경계 위치)로 바로 바꾼다.
     * 같은 값을 아래 슬라이더로도 바꿀 수 있고, 손잡이를 끌면 슬라이더도 따라 움직인다. 가로 모드의 값은 따로 정한다.
     */
    private void buildKeyArea() {
        if (!harvesting) {
            balancedPreview = new KeyboardPreview(this, prefs, new KeyboardPreview.Listener() {
                @Override public void onTap(KeyboardPreview.Selection s) { }
                @Override public void onSizeChanged(String prefKey) { syncSizeSliders(prefKey); }
                @Override public void onLayerChanged(int layer) { }
            });
            balancedPreview.setBalancedMode(true);
            pinnedBottom.addView(balancedPreview, matchWrap());
            pinnedBottom.setVisibility(View.VISIBLE);
        }
        LinearLayout card = section("자음·모음 쪽");
        card.addView(note("아래 미리보기에서 자음·모음 쪽 가운데의 ↔ 버튼을 좌우로 끌면 키 폭이, 길게 누른 뒤 끌면 가로 위치가 "
                + "바뀝니다. 가운데 경계선의 손잡이를 끌면 자·모음 경계 위치가 5%씩 바뀝니다."));
        card.addView(balancedSlider("자·모음 경계 위치", prefs.balancedBoundaryKey(), prefs::balancedBoundary,
                Prefs.BALANCED_BOUNDARY_MIN, Prefs.BALANCED_BOUNDARY_MAX, "%"));
        card.addView(balancedSlider("자음 키 폭", prefs.balancedConsonantWidthKey(), prefs::balancedConsonantWidth,
                prefs.balancedWidthMin(), Prefs.BALANCED_CONSONANT_WIDTH_MAX, "%"));
        card.addView(balancedSlider("자음 가로 위치", prefs.balancedConsonantPosKey(), prefs::balancedConsonantPos, 0, 100, ""));
        card.addView(balancedSlider("모음 키 폭", prefs.balancedVowelWidthKey(), prefs::balancedVowelWidth,
                prefs.balancedWidthMin(), com.alternative_studios.newswipe.keyboard.KeyboardLayout.balancedWidthMax(prefs), "%"));
        card.addView(balancedSlider("모음 가로 위치", prefs.balancedVowelPosKey(), prefs::balancedVowelPos, 0, 100, ""));
        card.addView(note("자·모음 경계 위치는 자판 폭에서 자음 쪽이 차지하는 비율입니다 (50%면 가운데). "
                + "자음 키 폭은 경계 왼쪽을, 모음 키 폭은 경계 오른쪽을 꽉 채우는 폭에 대한 비율입니다 (100%면 꽉 참). "
                + "가로 위치는 각 쪽 안에서 키들이 놓이는 곳입니다 (0 = 왼쪽 끝, 50 = 가운데, 100 = 오른쪽 끝). "
                + "아래 줄의 키는 바뀌지 않습니다."));
    }

    /** 균형 배열 사용자화의 슬라이더 (5단위): 바꾸면 미리보기를 다시 그리고, 미리보기의 손잡이를 끌면 따라 움직인다. */
    private View balancedSlider(String label, String key, java.util.function.IntSupplier current, int min, int max,
                                String unit) {
        SettingsWidgets.Slider sl = sliderRow(label, key, current.getAsInt(), min, max, 5, unit, this::refreshLayoutPreview);
        if (!harvesting) sizeSliders.computeIfAbsent(key, k -> new java.util.ArrayList<>()).add(() -> sl.set(current.getAsInt()));
        return sl.view;
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

    /** 가로 모드·대화면 키보드 사용자화 화면 아래의 미리보기 (그 프로필의 값만 손잡이로 바꾼다). 다른 화면에서는 null. */
    private KeyboardPreview profilePreview;

    /**
     * 실제 키보드와 같은 크기·모양의 미리보기를 화면 아래에 붙인다 ('키보드 사용자화'와 같은 자리).
     * 글자 키 높이·세로 위치와, 분리 키보드면 두 묶음의 폭·가로 위치를 손잡이로 바꾼다. 키·영역은 고를 수 없다.
     */
    private void addProfilePreview(String profile) {
        if (harvesting) return;
        profileShown = profile;
        profilePreview = new KeyboardPreview(this, prefs, new KeyboardPreview.Listener() {
            @Override public void onTap(KeyboardPreview.Selection s) { onProfileTap(s); }
            @Override public void onSizeChanged(String prefKey) { syncSizeSliders(prefKey); }
            @Override public void onLayerChanged(int layer) { }
        });
        profilePreview.setProfileMode(prefs.profileEditView(profile), profileScreenWidth(profile));
        pinnedBottom.addView(profilePreview, matchWrap());
        pinnedBottom.setVisibility(View.VISIBLE);
        // 영역 설정 창: 목록 위로 아래에서 올라온다 (키보드 사용자화와 같다).
        sheet = new EditorSheet(this, middle, this::closeKeyPanel);
        areaBox = new LinearLayout(this);
        areaBox.setOrientation(LinearLayout.VERTICAL);
        sheet.body().addView(areaBox, matchWrap());
    }

    /** 미리보기가 그리는 프로필 (대화면은 세로·가로 버튼으로 바뀐다). */
    private String profileShown;

    /** 미리보기에서 영역을 눌렀다: 그 영역의 설정 창을 연다. 빈 곳이나 이미 고른 영역이면 닫는다. */
    private void onProfileTap(KeyboardPreview.Selection s) {
        KeyboardPreview.Selection now = profilePreview.selection();
        if (s == null || s.same(now)) {
            closeKeyPanel();
            return;
        }
        profilePreview.select(s);
        int area = s.area();
        String profile = profileShown;
        boolean split = prefs.profileEditView(profile).profileSplit();
        fillAreaBox(() -> buildProfileArea(profile, area, split));
        sheet.setTitle(KeyboardPreview.profileAreaName(area) + " 설정");
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
        intro.addView(note("세로 상태에서는 키보드 미리보기가 작게 보일 수 있습니다. 실제 크기의 미리보기를 원하시면 기기를 가로로 돌리세요."));
    }

    /** 가로 모드·대화면의 값 슬라이더: 바꾸면 미리보기를 다시 그리고, 미리보기의 손잡이를 끌면 따라 움직인다. */
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
        addProfileSplit(p, "가로 모드");
        addProfileWhole(p, "화면을 가로로 돌렸을 때");
        harvestProfileAreas(p);
    }

    /** 실험실: 아직 시험 중인 기능. */
    private void buildLab() {
        LinearLayout intro = card();
        intro.addView(note("아직 시험 중인 기능입니다. 동작이 바뀌거나 없어질 수 있습니다."));

        LinearLayout direction = section("밀기 방향 판정");
        direction.addView(slider("아래로 밀기 엄격도", Prefs.SWIPE_DOWN_RATIO, prefs.swipeDownRatioPct(),
                Prefs.SWIPE_RATIO_MIN, Prefs.SWIPE_RATIO_MAX, 10, "%"));
        direction.addView(slider("위로 밀기 엄격도", Prefs.SWIPE_UP_RATIO, prefs.swipeUpRatioPct(),
                Prefs.SWIPE_RATIO_MIN, Prefs.SWIPE_RATIO_MAX, 10, "%"));
        direction.addView(note("글자 키를 밀 때 위·아래로 인정되려면 세로로 움직인 거리가 가로로 움직인 거리의 이 비율을 넘어야 합니다. "
                + "값이 클수록 위·아래 판정이 엄격해져 좌우로 밀 때 손가락이 처져도 좌우로 인식됩니다. "
                + "기본값은 아래 150%, 위 120%입니다."));

        LinearLayout large = section("대화면 별도 레이아웃");
        View largeGroup = subGroup(
                slider("대화면 기준", Prefs.LARGE_SCREEN_DP, prefs.largeScreenDp(),
                        Prefs.LARGE_SCREEN_DP_MIN, Prefs.LARGE_SCREEN_DP_MAX, 20, "dp"),
                note("화면의 짧은 쪽 폭이 이 값(dp) 이상이면 대화면으로 봅니다. 보통 휴대폰은 360~480dp, "
                        + "폴더블 기기의 내부 화면과 태블릿은 600dp 이상입니다."),
                button("대화면 키보드 사용자화", v -> openSection(LARGE_AREA)));
        large.addView(toggle("대화면 별도 레이아웃",
                "폴더블 기기의 내부 화면이나 태블릿과 같은 대화면에서 키보드의 크기와 형태를 따로 정합니다.",
                Prefs.LARGE_LAYOUT, prefs.largeLayout(), largeGroup));
        large.addView(largeGroup);

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
        String name = ja ? "일본어 자판" : "중국어 자판";
        LinearLayout box = section(name);
        com.alternative_studios.newswipe.cjk.CjkPack.Info info =
                com.alternative_studios.newswipe.cjk.CjkPack.installed(getFilesDir(), lang);
        int req = ja ? REQ_CJK_JA : REQ_CJK_ZH;
        String release = SOURCE_URL + "/releases?q=" + (ja ? "ja-standard" : "zh-standard") + "&expanded=true";
        if (info == null) {
            box.addView(note((ja ? "히라가나 입력(12키 플릭·로마자)과 한자·가타카나 변환을 씁니다. "
                    : "병음으로 간체자를 입력합니다. ")
                    + "앱에는 변환 기능만 들어 있어, 사전 팩을 가져와야 켤 수 있습니다. 단어 추천·자동 수정·학습은 쓰지 않습니다."));
            box.addView(button("사전 팩 받는 곳", v -> openUrl(release)));
            box.addView(iconButton("사전 팩 가져오기", Icons.IMPORT, v -> SettingsImport.pick(this, req)));
            return;
        }
        View group = ja ? subGroup(choiceRow(new String[]{"12키 플릭", "로마자 (QWERTY)"}, prefs.jaQwerty() ? 1 : 0,
                        i -> prefs.raw().edit().putString(Prefs.CJK_JA_LAYOUT, i == 1 ? Prefs.JA_QWERTY : Prefs.JA_FLICK).apply()),
                note("12키 플릭: 키를 누르면 あ단, 왼쪽·위·오른쪽·아래로 밀면 い·う·え·お단이 들어갑니다. "
                        + "゛ 키는 앞 글자의 탁점·반탁점·작은 글자를 차례로 바꿉니다. "
                        + "스페이스로 변환 후보를 고르고 엔터로 확정합니다."),
                toggle("반각 공백 쓰기", "스페이스로 반각 공백을 넣습니다. 끄면 Gboard처럼 일본어를 입력하는 동안에는 "
                        + "전각 공백(　)을 넣습니다 (변환 중에는 변환 후보를 고릅니다).",
                        Prefs.CJK_JA_HALF_SPACE, prefs.jaHalfSpace()))
                : subGroup(note("스페이스로 첫 후보를 넣고, 엔터는 병음을 그대로 넣습니다. 음절을 직접 나누려면 '를 씁니다 (xi'an)."));
        box.addView(toggle(name, "지구본 키로 " + (ja ? "일본어" : "중국어") + " 자판으로 바꿀 수 있게 합니다.",
                Prefs.CJK_PREFIX + lang, prefs.cjkEnabled(lang), group));
        box.addView(group);
        StringBuilder b = new StringBuilder(info.name);
        if (!info.version.isEmpty()) b.append(" ").append(info.version);
        b.append("\n읽기 ").append(String.format(java.util.Locale.ROOT, "%,d", info.words)).append("개 · ")
                .append(String.format(java.util.Locale.ROOT, "%.1f", info.bytes / 1048576.0)).append("MB");
        if (!info.description.isEmpty()) b.append("\n").append(info.description);
        if (!info.attribution.isEmpty()) {
            b.append("\n출처: ").append(info.attribution);
            if (!info.license.isEmpty()) b.append(" (").append(info.license).append(")");
        }
        box.addView(note(b.toString()));
        if (!info.licenseText.isEmpty()) {
            box.addView(button("사전 팩의 라이선스 보기", v -> AppTheme.dialogBuilder(this)
                    .setTitle(info.name)
                    .setMessage(info.licenseText)
                    .setPositiveButton("닫기", null)
                    .show()));
        }
        box.addView(iconButton("다른 사전 팩 가져오기", Icons.IMPORT, v -> SettingsImport.pick(this, req)));
        box.addView(trashButton("사전 팩 지우기", v -> AppTheme.dialogBuilder(this)
                .setTitle("사전 팩 지우기")
                .setMessage("가져온 사전 팩을 지우고 " + name + "을 끕니다. 다시 쓰려면 팩을 다시 가져와야 합니다.")
                .setPositiveButton("지우기", (d, w) -> {
                    prefs.setCjkEnabled(lang, false);
                    com.alternative_studios.newswipe.cjk.CjkPack.remove(getFilesDir(), lang);
                    Toast.makeText(this, "사전 팩을 지웠습니다", Toast.LENGTH_SHORT).show();
                    recreate();
                })
                .setNegativeButton("취소", null)
                .show()));
    }

    /** 고른 일본어·중국어 사전 팩을 검사해 설치하고, 그 언어의 자판을 켠다. */
    private void importCjkPack(android.net.Uri uri, String lang) {
        Toast.makeText(this, "사전 팩을 확인하는 중입니다…", Toast.LENGTH_SHORT).show();
        android.content.Context app = getApplicationContext();
        new Thread(() -> {
            String message;
            boolean ok = false;
            try (java.io.InputStream in = app.getContentResolver().openInputStream(uri)) {
                if (in == null) throw new java.io.IOException("파일을 열지 못했습니다");
                com.alternative_studios.newswipe.cjk.CjkPack.Info info =
                        com.alternative_studios.newswipe.cjk.CjkPack.install(in, app.getFilesDir(), lang);
                message = "'" + info.name + "'을 가져와 " + com.alternative_studios.newswipe.cjk.CjkPack.languageName(lang)
                        + " 자판을 켰습니다. 지구본 키로 바꿀 수 있습니다";
                ok = true;
            } catch (com.alternative_studios.newswipe.cjk.CjkPack.InvalidPackException e) {
                message = "가져올 수 없는 사전 팩입니다: " + e.getMessage();
            } catch (Exception e) {
                message = "사전 팩을 가져오지 못했습니다: " + e.getMessage();
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
        LinearLayout hw = section("외장 키보드");
        hw.addView(note("한글은 두벌식 배열 기준입니다. 한·영 전환은 시스템 기본값을 쓰되, 바꿀 수 있습니다."));
        TextView customValue = new TextView(this);
        customValue.setTextColor(hintColor);
        customValue.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        customValue.setText(hwKeyName(prefs.hwCustomKey(), prefs.hwCustomScan()));
        View langGroup = subGroup(
                toggle("한·영 키", "키보드의 한·영 키(언어 전환 키)로 바꿉니다. "
                        + "기기에 따라 시스템이 이 키를 먼저 가져가 바뀌지 않으면 아래의 '한·영 키 직접 지정'을 써 보세요.",
                        Prefs.HW_LANG_KEY, prefs.hwLangKey()),
                toggle("Shift+Space", "Shift를 누른 채 스페이스바를 누르면 바꿉니다.",
                        Prefs.HW_SHIFT_SPACE, prefs.hwShiftSpace()),
                toggle("오른쪽 Alt", "오른쪽 Alt만 눌렀다 떼면 바꿉니다. 한·영 키를 누르면 오른쪽 Alt를 보내는 키보드가 많습니다. "
                        + "다른 키와 함께 누르면 원래의 Alt로 쓰입니다.",
                        Prefs.HW_RIGHT_ALT, prefs.hwRightAlt()),
                toggle("Ctrl+Space", "Ctrl을 누른 채 스페이스바를 누르면 바꿉니다. "
                        + "기기에 따라 시스템이 키보드 배열 전환에 먼저 쓸 수 있습니다.",
                        Prefs.HW_CTRL_SPACE, prefs.hwCtrlSpace()),
                button("한·영 키 직접 지정", customValue, v -> pickHardwareLangKey(customValue)),
                note("위에 없는 키(오른쪽 Ctrl, 한자 키 등)를 한·영 전환에 씁니다. Ctrl·Alt 같은 수정 키는 혼자 눌렀다 뗄 때만 바꿉니다."));
        hw.addView(toggle("한·영 전환 키 바꾸기", "한·영 전환을 다른 키 조합으로도 할 수 있게 바꿉니다.",
                Prefs.HW_LANG_CUSTOM, prefs.hwLangCustom(), langGroup));
        hw.addView(langGroup);
        hw.addView(toggle("언어 전환 알림", "화면 자판이 보이지 않을 때 한·영을 바꾸면 '한글' 또는 'English'를 잠깐 띄웁니다.",
                Prefs.HW_TOAST, prefs.hwToast()));
        hw.addView(toggle("도구 막대 보이기",
                "외장 키보드를 연결해 화면 자판이 감춰질 때도 도구 막대를 보입니다. "
                        + "클립보드와 이모지 창은 원래 크기로 열립니다. "
                        + "도구 막대의 '키보드 보이기' 버튼을 누르면 자판 전체가 보입니다.",
                Prefs.HW_TOOLBAR, prefs.hwToolbar()));
    }

    /** 외장 키보드에서 키를 하나 눌러 한·영 전환 키로 정한다. */
    private void pickHardwareLangKey(TextView valueView) {
        Context dc = AppTheme.dialogContext(this);
        TextView message = new TextView(dc);
        message.setText("외장 키보드에서 한·영 전환에 쓸 키를 누르세요.");
        message.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        message.setTextColor(textColor);
        int pad = Ui.dp(this, 24);
        message.setPadding(pad, Ui.dp(this, 8), pad, 0);
        android.app.AlertDialog dialog = AppTheme.accentBuilder(dc)
                .setTitle("한·영 키 직접 지정")
                .setView(message)
                .setNeutralButton("지정 해제", (d, w) -> saveHardwareLangKey(valueView, 0, 0))
                .setNegativeButton("취소", null)
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
                message.setText("글자·스페이스·엔터·지우기·탭 키는 입력에 쓰이므로 지정할 수 없습니다. 다른 키를 누르세요.");
                return true;
            }
            if (keyCode == KeyEvent.KEYCODE_CAPS_LOCK || keyCode == KeyEvent.KEYCODE_NUM_LOCK
                    || keyCode == KeyEvent.KEYCODE_SCROLL_LOCK) {
                // 잠금 키는 누를 때마다 시스템의 잠금 상태도 함께 바뀌어, 영어로 바꾸면 대문자로 입력되는 일이 생긴다.
                message.setText("잠금 키(Caps Lock 등)는 잠금 상태가 함께 바뀌어 지정할 수 없습니다. 다른 키를 누르세요.");
                return true;
            }
            int scan = keyCode == KeyEvent.KEYCODE_UNKNOWN ? event.getScanCode() : 0;
            if (keyCode == KeyEvent.KEYCODE_UNKNOWN && scan == 0) {
                message.setText("이 키는 알아볼 수 없습니다. 다른 키를 누르세요.");
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
            case 0: return scanCode != 0 ? "키 " + scanCode : "없음";
            case KeyEvent.KEYCODE_CTRL_RIGHT: return "오른쪽 Ctrl";
            case KeyEvent.KEYCODE_CTRL_LEFT: return "왼쪽 Ctrl";
            case KeyEvent.KEYCODE_ALT_RIGHT: return "오른쪽 Alt";
            case KeyEvent.KEYCODE_ALT_LEFT: return "왼쪽 Alt";
            case KeyEvent.KEYCODE_SHIFT_RIGHT: return "오른쪽 Shift";
            case KeyEvent.KEYCODE_SHIFT_LEFT: return "왼쪽 Shift";
            case KeyEvent.KEYCODE_META_RIGHT: return "오른쪽 Meta";
            case KeyEvent.KEYCODE_META_LEFT: return "왼쪽 Meta";
            case KeyEvent.KEYCODE_LANGUAGE_SWITCH: return "언어 전환 키";
            default:
                String name = KeyEvent.keyCodeToString(keyCode);
                return name.startsWith("KEYCODE_") ? name.substring(8) : name;
        }
    }

    /**
     * 지금 화면이 대화면이면 그 방향의 프로필 (대화면 세로·가로), 아니면 null (폴더블 기기의 외부 화면 등).
     * 화면의 짧은 쪽 폭이 '대화면 기준' 이상이면 대화면이다 (키보드가 고르는 규칙과 같다).
     */
    private String largeProfileNow() {
        android.content.res.Configuration cfg = getResources().getConfiguration();
        if (cfg.smallestScreenWidthDp < prefs.largeScreenDp()) return null;
        return cfg.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
                ? Prefs.PROFILE_LARGE_LANDSCAPE : Prefs.PROFILE_LARGE_PORTRAIT;
    }

    /**
     * 대화면 키보드 사용자화: 지금 화면 방향(대화면 세로·가로)의 설정을 보여 준다. 기기를 돌리면 다른 방향의 설정이 보인다.
     * 분리 키보드 켜기와 좌·우 균형 배열은 세로·가로가 함께 쓴다. 영역 설정은 아래 미리보기에서 영역을 눌러 연다.
     * 대화면이 아니면 안내 문구만 둔다.
     */
    private void buildLargeArea() {
        if (harvesting) {
            // 설정 검색: 두 방향의 항목을 모두 모은다.
            addProfileSplit(Prefs.PROFILE_LARGE_PORTRAIT, "대화면");
            for (String p : new String[]{Prefs.PROFILE_LARGE_PORTRAIT, Prefs.PROFILE_LARGE_LANDSCAPE}) {
                addProfileWhole(p, "대화면에서");
                addOneHandWidth(p, "대화면에서");
                harvestProfileAreas(p);
            }
            return;
        }
        String profile = largeProfileNow();
        if (profile == null) {
            LinearLayout c = card();
            c.addView(note("이 설정은 대화면에서만 보입니다. 대화면으로 전환하거나 대화면 기준을 조절하세요."));
            return;
        }
        boolean land = Prefs.PROFILE_LARGE_LANDSCAPE.equals(profile);
        String when = land ? "대화면을 가로로 들었을 때" : "대화면을 세로로 들었을 때";
        addProfilePreview(profile);
        String now = land ? "가로" : "세로", other = land ? "세로" : "가로";
        LinearLayout intro = card();
        intro.addView(note("대화면을 " + now + "로 들었을 때 키보드 모양을 정합니다. " + other + "일 때의 설정은 기기를 " + other + "로 돌리면 보입니다.\n"
                + "분리 키보드 사용 유무, 좌·우 균형 배열 사용 유무는 세로와 가로가 공유하지만, 나머지 설정은 세로·가로가 각각 별도로 설정할 수 있습니다.\n"
                + "여기에서 설정이 불가능한 항목은 '키보드 사용자화'의 설정과 공유합니다."));
        addProfileSplit(profile, "대화면");
        addProfileWhole(profile, when);
        addOneHandWidth(profile, when);
    }

    /**
     * '분리 키보드' 섹션 (키보드 전체): 분리 키보드 켜기와, 켰을 때 좌·우 균형 배열.
     * 켜고 끄면 미리보기의 영역도 바뀐다 (글자 키 ↔ 왼쪽·오른쪽 묶음과 가운데 공간).
     */
    private void addProfileSplit(String profile, String name) {
        Prefs edit = prefs.profileEditView(profile);
        LinearLayout card = section("분리 키보드");
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setVisibility(edit.profileSplit() || harvesting ? View.VISIBLE : View.GONE);
        String reachKey = edit.splitReachColumnKey();
        // 두벌식은 영어 자판처럼 일반 배열을 가운데에서 나눠, 4열로 맞추지 않는다 (검색 목록에는 늘 넣는다).
        if (!prefs.koreanDubeolsik() || harvesting) {
            box.addView(toggle("좌·우 균형 배열",
                    "한글 자판의 왼쪽·오른쪽 묶음을 4열로 맞춥니다.",
                    edit.splitReachColumn(), on -> {
                        prefs.raw().edit().putBoolean(reachKey, on).apply();
                        refreshLayoutPreview();
                    }));
        }
        String splitKey = edit.splitKey();
        card.addView(toggle(name + " 분리 키보드",
                "자판을 반으로 나눠 엄지손가락만으로 타이핑을 편하게 합니다.",
                edit.profileSplit(), on -> {
                    prefs.raw().edit().putBoolean(splitKey, on).apply();
                    Ui.setVisibleAnimated(box, on);
                    refreshLayoutPreview();
                }));
        card.addView(box);
    }

    /** '키보드 모양' 섹션 (키보드 전체): 키 글자 크기, 세로 위치. 가로 모드, 대화면 세로·가로가 각각 따로 정한다. */
    private void addProfileWhole(String profile, String when) {
        Prefs edit = prefs.profileEditView(profile);
        LinearLayout card = section("키보드 모양");
        card.addView(profileSlider("키 글자 크기", edit.textSizeKey(), edit::keyTextSize, Prefs.PROFILE_HEIGHT_MIN, 140, 5, "%"));
        card.addView(profileSlider("세로 위치", edit.liftKey(), edit::profileLiftDp, 0, 160, 4, "dp"));
        card.addView(note(when + " 쓰는 값입니다. 키 글자 크기는 정하기 전에는 세로 모드('키보드 사용자화')의 값을 따릅니다. "
                + "세로 위치는 키보드를 화면 아래에서 띄우는 높이로, 값이 클수록 키보드가 위로 올라갑니다 "
                + "(한 손 모드에서는 한 손 모드의 세로 위치를 씁니다).\n"
                + "그 밖의 값(도구 막대·숫자 줄 높이, 키보드·영역 여백, 키 사이 간격과 키 모양, 키마다 정한 설정)은 "
                + "세로 모드와 같은 값을 씁니다. 바꾸려면 '키보드 사용자화'에서 바꿉니다."));
    }

    /** 대화면 세로·가로의 한 손 모드 자판 폭 (정하기 전에는 세로·가로 모드의 값). */
    private void addOneHandWidth(String profile, String when) {
        Prefs edit = prefs.profileEditView(profile);
        String widthKey = edit.oneHandWidthKey();
        if (widthKey == null) return;
        LinearLayout oneHand = section("한 손 모드");
        oneHand.addView(slider("자판 폭", widthKey, edit.oneHandWidthForProfile(), 30, 90, 5, "%"));
        oneHand.addView(note(when + " 한 손 모드에서 쓰는 자판 폭(화면 폭의 %)입니다."));
    }

    /** 설정 검색용으로 화면을 만들 때는 영역 설정 창의 항목도 목록에 넣어 찾을 수 있게 한다. */
    private void harvestProfileAreas(String profile) {
        if (!harvesting) return;
        for (int a : new int[]{KeyboardPreview.AREA_TOOLBAR, KeyboardPreview.AREA_LETTERS, KeyboardPreview.AREA_LEFT, KeyboardPreview.AREA_RIGHT,
                KeyboardPreview.AREA_MIDDLE, KeyboardPreview.AREA_FUNCTION}) {
            buildProfileArea(profile, a, true);
        }
    }

    /**
     * 영역 하나의 설정 (그 영역 창에 들어간다). split은 분리 키보드에만 있는 항목(스페이스바 잇기)을 넣을지.
     * 미리보기의 손잡이를 끌면 슬라이더도 함께 움직인다.
     */
    private void buildProfileArea(String profile, int area, boolean split) {
        Prefs edit = prefs.profileEditView(profile);
        switch (area) {
            case KeyboardPreview.AREA_LEFT:
            case KeyboardPreview.AREA_RIGHT: {
                boolean left = area == KeyboardPreview.AREA_LEFT;
                String side = left ? "왼쪽" : "오른쪽";
                LinearLayout c = section("묶음 크기");
                if (left) {
                    c.addView(profileSlider(side + " 묶음 폭", edit.balancedConsonantWidthKey(), edit::balancedConsonantWidth,
                            edit.balancedWidthMin(), Prefs.BALANCED_CONSONANT_WIDTH_MAX, 5, "%"));
                    c.addView(profileSlider(side + " 묶음 가로 위치", edit.balancedConsonantPosKey(), edit::balancedConsonantPos,
                            0, 100, 5, ""));
                } else {
                    c.addView(profileSlider(side + " 묶음 폭", edit.balancedVowelWidthKey(), edit::balancedVowelWidth,
                            edit.balancedWidthMin(),
                            com.alternative_studios.newswipe.keyboard.KeyboardLayout.balancedWidthMax(edit), 5, "%"));
                    c.addView(profileSlider(side + " 묶음 가로 위치", edit.balancedVowelPosKey(), edit::balancedVowelPos,
                            0, 100, 5, ""));
                }
                c.addView(note((left ? "왼쪽 묶음(자음, 숫자 1~5, 스페이스바와 그 왼쪽 키)" : "오른쪽 묶음(모음, 숫자 6~0, 스페이스바와 그 오른쪽 키)")
                        + "이 함께 움직입니다. 묶음 폭은 100%면 자기 절반을 꽉 채웁니다. 가로 위치는 절반 안에서 묶음이 놓이는 곳입니다 "
                        + "(0 = 왼쪽 끝, 50 = 가운데, 100 = 오른쪽 끝). 미리보기에서 묶음 가운데의 ↔ 버튼을 좌우로 끌어도, "
                        + "길게 누른 뒤 끌어도 바뀝니다."));
                addProfileLetterHeight(edit);
                break;
            }
            case KeyboardPreview.AREA_LETTERS:
                addProfileLetterHeight(edit);
                break;
            case KeyboardPreview.AREA_TOOLBAR: {
                LinearLayout c = section("도구 막대");
                String followKey = edit.toolbarFollowKey();
                boolean on = edit.getRawFollow();
                c.addView(toggle("묶음 좌우 따라가기",
                        "분리 키보드에서 도구 막대의 양 끝이 왼쪽 묶음의 왼쪽 끝과 오른쪽 묶음의 오른쪽 끝을 따라갑니다. "
                                + "끄면 도구 막대는 키보드 전체 폭을 씁니다.",
                        on, v -> {
                            prefs.raw().edit().putBoolean(followKey, v).apply();
                            refreshLayoutPreview();
                        }));
                break;
            }
            case KeyboardPreview.AREA_MIDDLE: {
                LinearLayout c = section("분리 키보드 기능");
                View speed = subGroup(slider("이동 속도", edit.splitGapSpeedKey(), edit.splitGapSpeed(), 50, 300, 10, "%"));
                speed.setVisibility(edit.splitGapCursor() ? View.VISIBLE : View.GONE);
                c.addView(toggle("밀어서 커서 이동",
                        "분리 키보드 가운데의 빈 공간을 밀어 커서를 옮깁니다.",
                        edit.splitGapCursorKey(), edit.splitGapCursor(), speed));
                c.addView(speed);
                break;
            }
            case KeyboardPreview.AREA_FUNCTION: {
                LinearLayout c = section("기능키");
                c.addView(profileSlider("기능키 높이", edit.fnRowHeightKey(), edit::fnRowHeight,
                        Prefs.FN_ROW_HEIGHT_MIN, Prefs.FN_ROW_HEIGHT_MAX, 5, "%"));
                c.addView(note("맨 아래 줄(스페이스바·기능키)의 높이로, 일반 키 높이에 대한 비율입니다. 정하기 전에는 세로 모드의 값을 "
                        + "따릅니다. 미리보기에서 ↕ 버튼을 위·아래로 끌어도 바뀝니다."));
                if (!split) break;
                // 스페이스바 잇기 (분리 키보드): 이었을 때만 하단 기능키 폭을 정한다.
                View fnWidth = subGroup(
                        profileSlider("하단 기능키 폭", edit.splitFnWidthKey(), edit::splitFnWidthSetting, 50, 200, 5, "%"),
                        note("스페이스바·Shift·⌫를 뺀 맨 아래 줄 키(기호 키·쉼표·지구본·온점·엔터)의 폭입니다. "
                                + "줄이거나 늘린 만큼 스페이스바가 늘거나 줄어듭니다."));
                fnWidth.setVisibility(edit.splitSpaceJoin() ? View.VISIBLE : View.GONE);
                String joinKey = edit.splitSpaceJoinKey();
                c.addView(toggle("스페이스바 잇기",
                        "양쪽에 걸친 스페이스바를 이어 붙입니다.",
                        edit.splitSpaceJoin(), on -> {
                            prefs.raw().edit().putBoolean(joinKey, on).apply();
                            Ui.setVisibleAnimated(fnWidth, on);
                            refreshLayoutPreview();
                        }));
                c.addView(fnWidth);
                break;
            }
            default:
                break;
        }
    }

    /** 글자 키 높이 (글자 키·두 묶음 창이 함께 쓴다). */
    private void addProfileLetterHeight(Prefs edit) {
        LinearLayout c = section("글자 키");
        c.addView(profileSlider("글자 키 높이", edit.heightKey(), edit::keyboardHeight, Prefs.PROFILE_HEIGHT_MIN, 140, 5, "%"));
        c.addView(note("정하기 전에는 세로 모드('키보드 사용자화')의 값을 따릅니다. 미리보기에서 ↕ 버튼을 위·아래로 끌어도 바뀝니다."));
    }

    // ---------------------------------------------------------------- 한 손 모드

    /** 한 손 모드: 쏠릴 쪽을 고르고, 미리보기를 보면서 자판 폭·키 높이·세로 위치를 정한다. */
    private void buildOneHand() {
        LinearLayout card = section("한 손 모드");
        card.addView(oneHandPreview());
        card.addView(note("자판과 도구 막대가 한쪽으로 쏠려 한 손으로 편하게 입력할 수 있습니다.\n"
                + "도구 막대의 한 손 모드 버튼으로 켭니다. 버튼을 왼쪽·오른쪽으로 밀면 그쪽으로 쏠린 한 손 모드가 됩니다."));

        LinearLayout size = section("크기와 위치");
        size.addView(slider("자판 폭", Prefs.ONE_HAND_WIDTH, prefs.oneHandWidth(), 60, 90, 5, "%",
                this::refreshOneHandPreview));
        size.addView(slider("가로 모드 자판 폭", Prefs.ONE_HAND_WIDTH_LAND, prefs.oneHandWidthLand(), 30, 90, 5, "%"));
        size.addView(slider("키 높이", Prefs.ONE_HAND_HEIGHT, prefs.oneHandHeight(), 60, 100, 5, "%",
                this::refreshOneHandPreview));
        size.addView(slider("세로 위치", Prefs.ONE_HAND_LIFT, prefs.oneHandLiftDp(), 0, 160, 4, "dp",
                this::refreshOneHandPreview));
        size.addView(note("자판 폭은 화면 폭에 대한 비율이고, 키 높이는 보통 키 높이에 대한 비율입니다. "
                + "세로 위치는 자판을 화면 아래에서 띄우는 높이로, 값이 클수록 자판이 위로 올라갑니다."));
    }

    /** 한 손 모드 미리보기의 부분들 (값을 바꾸면 refreshOneHandPreview가 다시 놓는다). */
    private LinearLayout oneHandBody, oneHandColumn, oneHandSide;
    private View oneHandLift;
    private com.alternative_studios.newswipe.keyboard.KeyboardView oneHandKeys;

    /** 키보드와 같은 모양으로 그린 한 손 모드 미리보기: [도구 막대 + 자판 | 빈 곳의 화살표·확장 아이콘], 아래에 띄운 높이. */
    private View oneHandPreview() {
        if (harvesting) return new View(this);   // 설정 검색용으로 글자만 모을 때는 무거운 미리보기를 만들지 않는다
        com.alternative_studios.newswipe.keyboard.KeyboardTheme kt =
                com.alternative_studios.newswipe.keyboard.KeyboardTheme.of(this);
        LinearLayout frame = new LinearLayout(this);
        frame.setOrientation(LinearLayout.VERTICAL);
        frame.setBackgroundColor(kt.background);
        frame.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
        frame.setOutlineProvider(new android.view.ViewOutlineProvider() {
            @Override
            public void getOutline(View view, android.graphics.Outline outline) {
                outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), Ui.dp(view.getContext(), 16));
            }
        });
        frame.setClipToOutline(true);

        oneHandBody = new LinearLayout(this);
        oneHandBody.setOrientation(LinearLayout.HORIZONTAL);
        frame.addView(oneHandBody, matchWrap());
        oneHandLift = new View(this);
        frame.addView(oneHandLift, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0));

        oneHandColumn = new LinearLayout(this);
        oneHandColumn.setOrientation(LinearLayout.VERTICAL);
        if (prefs.toolbar()) oneHandColumn.addView(toolbarMock(kt));
        oneHandKeys = new com.alternative_studios.newswipe.keyboard.KeyboardView(this);
        oneHandKeys.setTheme(kt);
        applyOneHandPreviewText();
        oneHandKeys.setKeyShadow(prefs.keyShadow(), prefs.keyShadowStrength());
        oneHandKeys.setKeyRadius(Ui.dp(this, prefs.keyRadiusDp()));
        oneHandKeys.setGridColors(prefs.gridColors());
        oneHandKeys.setPopupHints(!prefs.popupHintHidden());
        oneHandKeys.setNumberHintHidden(prefs.numberHintHidden());
        oneHandKeys.setKeyGaps(Ui.dp(this, prefs.keyGapXDp()), Ui.dp(this, prefs.keyGapYDp()));
        oneHandKeys.setInsets(Ui.dp(this, prefs.padLeftDp()), Ui.dp(this, prefs.padTopDp()),
                Ui.dp(this, prefs.padRightDp()), Ui.dp(this, prefs.padBottomDp()));
        oneHandKeys.setOnTouchListener((v, ev) -> true);
        oneHandColumn.addView(oneHandKeys, matchWrap());

        oneHandSide = new LinearLayout(this);
        oneHandSide.setOrientation(LinearLayout.VERTICAL);
        oneHandSide.setGravity(Gravity.CENTER);
        oneHandSide.addView(previewSideButton(kt, Icons.NEXT));
        oneHandSide.addView(previewSideButton(kt, Icons.EXPAND));
        oneHandBody.addView(oneHandColumn, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        oneHandBody.addView(oneHandSide, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));

        refreshOneHandPreview();
        LinearLayout.LayoutParams lp = matchWrap();
        lp.topMargin = Ui.dp(this, 12);
        lp.bottomMargin = Ui.dp(this, 4);
        frame.setLayoutParams(lp);
        return frame;
    }

    /** 한 손 모드 미리보기의 키 글자 크기: 키 높이를 줄인 만큼 같은 비율로 줄인다 (키보드와 같다). */
    private void applyOneHandPreviewText() {
        oneHandKeys.configure(prefs.longPressMs(), prefs.swipeThresholdDp(), false, Math.round(prefs.keyTextSize() * prefs.oneHandHeight() / 100f));
    }

    /** 키보드 도구 막대와 같은 자리·순서로 보이는 버튼들 (누를 수 없음). */
    private View toolbarMock(com.alternative_studios.newswipe.keyboard.KeyboardTheme kt) {
        LinearLayout bar = new LinearLayout(this);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(Ui.dp(this, prefs.padLeftDp()), 0, Ui.dp(this, prefs.padRightDp()), 0);
        String[][] order = prefs.toolOrder();
        for (int side = 0; side < 2; side++) {
            if (side == 1) bar.addView(new View(this), new LinearLayout.LayoutParams(0, 1, 1f));
            for (String id : order[side]) {
                if (!prefs.toolShown(id)) continue;
                IconButton b = new IconButton(this, com.alternative_studios.newswipe.keyboard.ToolbarButtons.icon(id),
                        kt.text, kt.keyPressed, null);
                b.setToolbarStyle(kt, Ui.dp(this, 40));
                b.setClickable(false);
                b.setFocusable(false);
                bar.addView(b, new LinearLayout.LayoutParams(Ui.dp(this, 40), Ui.dp(this, 40)));
            }
        }
        bar.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 40)));
        return bar;
    }

    private IconButton previewSideButton(com.alternative_studios.newswipe.keyboard.KeyboardTheme kt, int icon) {
        IconButton b = new IconButton(this, icon, kt.hint, kt.keyPressed, null);
        int r = Ui.dp(this, 12);
        b.setBackground(Ui.round(kt.functionKey, r));
        b.setClickable(false);
        b.setFocusable(false);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 48));
        lp.setMargins(Ui.dp(this, 6), Ui.dp(this, 6), Ui.dp(this, 6), Ui.dp(this, 6));
        b.setLayoutParams(lp);
        return b;
    }

    /** 키보드의 applyOneHand와 같은 규칙으로 미리보기를 다시 놓는다. */
    private void refreshOneHandPreview() {
        if (oneHandBody == null) return;
        // 왼쪽 한 손 모드로 보여 준다: 자판은 왼쪽, 빈 곳은 오른쪽. 자판 폭 비율만 바꾼다.
        int width = prefs.oneHandWidth();
        ((LinearLayout.LayoutParams) oneHandColumn.getLayoutParams()).weight = width;
        ((LinearLayout.LayoutParams) oneHandSide.getLayoutParams()).weight = 100 - width;
        oneHandBody.requestLayout();
        com.alternative_studios.newswipe.keyboard.KeyboardLayout l =
                com.alternative_studios.newswipe.keyboard.KeyboardLayout.korean(prefs.oneHandView());
        oneHandKeys.setKeyboardHeight(Math.round(Ui.dp(this, 54) * l.totalHeight() * prefs.keyboardHeight() / 100f
                * prefs.oneHandHeight() / 100f) + Ui.dp(this, prefs.padTopDp()) + Ui.dp(this, prefs.padBottomDp()));
        applyOneHandPreviewText();
        oneHandKeys.setLayout(l);
        ViewGroup.LayoutParams llp = oneHandLift.getLayoutParams();
        llp.height = Ui.dp(this, prefs.oneHandLiftDp());
        oneHandLift.setLayoutParams(llp);
    }

    /** 이 화면의 자판 미리보기와 각각이 그리는 프로필 (null = 세로 모드의 기본 값). */
    private final java.util.Map<com.alternative_studios.newswipe.keyboard.KeyboardView, String> layoutPreviews =
            new java.util.LinkedHashMap<>();

    private void refreshLayoutPreview() {
        if (balancedPreview != null) balancedPreview.refresh();
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

    /** 가로 방향 미리보기(가로 모드, 대화면 가로)의 키 높이·글자 크기 비율. */
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
        preview.setPopupHints(!prefs.popupHintHidden());
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
        LinearLayout mode = section("화면 모드");
        String[] modeValues = {Prefs.THEME_SYSTEM, Prefs.THEME_LIGHT, Prefs.THEME_DARK};
        mode.addView(choiceRow(new String[]{"시스템", "라이트", "다크"}, indexOf(modeValues, prefs.themeMode()), i -> {
            prefs.raw().edit().putString(Prefs.THEME_MODE, modeValues[i]).apply();
            recreate();
        }));
        mode.addView(note("시스템을 고르면 기기의 라이트/다크 설정을 따릅니다. 설정 화면과 키보드에 모두 적용됩니다."));

        LinearLayout accent = section("강조 색");
        String[] accentValues = {Prefs.ACCENT_SYSTEM, Prefs.ACCENT_CUSTOM};
        accent.addView(choiceRow(new String[]{"시스템", "지정 색상"}, indexOf(accentValues, prefs.accentMode()), i -> {
            prefs.raw().edit().putString(Prefs.ACCENT_MODE, accentValues[i]).apply();
            recreate();
        }));
        if (Build.VERSION.SDK_INT < 31) {
            accent.addView(note("시스템 색은 Android 12 이상에서 기기의 색을 따릅니다. 이전 버전에서는 지정 색상이 쓰입니다."));
        } else {
            accent.addView(note("시스템을 고르면 기기의 강조 색(배경화면 색)을 따릅니다."));
        }
        if (Prefs.ACCENT_CUSTOM.equals(prefs.accentMode()) || Build.VERSION.SDK_INT < 31) {
            int current = prefs.accentColor();
            accent.addView(swatchRow(0, 6, current));
            accent.addView(swatchRow(6, 12, current));
            View hex = button("색 코드 직접 입력 (" + String.format("#%06X", current & 0xFFFFFF) + ")",
                    v -> askAccentHex(current));
            ((LinearLayout.LayoutParams) hex.getLayoutParams()).topMargin = Ui.dp(this, 16);   // 색 동그라미와 띄운다
            accent.addView(hex);
        }

        LinearLayout keyColors = section("키 색");
        keyColors.addView(toggle("색 정렬",
                "Shift 키와 ⌫를 글자 키 색으로, 스페이스바는 다른 기능키 색으로 바꿉니다.",
                Prefs.GRID_COLORS, prefs.gridColors()));

        if (SHOW_NEUMORPHISM) {
            LinearLayout neu = section("Neumorphism");
            neu.addView(toggle("Neumorphism 디자인",
                    "키와 도구 막대·클립보드·이모지 창의 버튼이 바탕에서 부드럽게 솟아오르고, 누르면 안으로 쑥 들어가는 모양으로 그립니다. "
                            + "켜면 키 그림자 대신 쓰입니다.",
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
                .setTitle("강조 색 코드")
                .setMessage("#RRGGBB 형식으로 적어 주세요.")
                .setView(box)
                .setPositiveButton("적용", (d, w) -> {
                    try {
                        String v = input.getText().toString().trim();
                        if (!v.startsWith("#")) v = "#" + v;
                        int color = Color.parseColor(v) | 0xFF000000;
                        prefs.raw().edit().putInt(Prefs.ACCENT_COLOR, color).apply();
                        d.dismiss();   // 대화상자를 닫고 다시 만들어야 창이 남지 않는다
                        recreate();
                    } catch (IllegalArgumentException e) {
                        Toast.makeText(this, "색 코드를 읽을 수 없습니다", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("취소", null)
                .show();
    }

    private void buildWords() {
        LinearLayout suggest = section("단어 추천");
        View fullBar = subGroup(toggle("도구 막대 전체를 쓰기",
                "추천이 나오는 동안 도구 막대 전체를 추천에 씁니다.",
                Prefs.SUGGEST_FULL_BAR, prefs.suggestFullBar()),
                toggle("추천 단어 뒤에 공백 포함",
                        "입력된 추천 단어 뒤에 공백을 붙입니다. 끄면 공백 없이 단어만 입력됩니다.",
                        Prefs.SUGGEST_SPACE, prefs.suggestSpace()),
                toggle("다음 단어 예측",
                        "앞 단어 다음에 자주 오는 단어를 미리 보여 줍니다 (예: '좋은' 다음에 하루·아침). ",
                        Prefs.PREDICT_NEXT, prefs.predictNext()));
        suggest.addView(toggle("단어 추천", "입력 중에 예측되는 단어를 도구 막대에 보여 줍니다. ",
                Prefs.SUGGEST_WORDS, prefs.suggestWords(), fullBar));
        suggest.addView(fullBar);

        LinearLayout shortcut = section("단축어");
        View shortcutGroup = subGroup(button("단축어 사용자화", v -> startActivity(new Intent(this, ShortcutEditorActivity.class))));
        shortcut.addView(toggle("단축어 사용", "사용자가 정한 줄임말을 입력하면 정해 둔 문장을 추천란에 보여 줍니다 (예: ㅈㄱㅈ → 지금 가는 중)",
                Prefs.SHORTCUTS_ENABLED, prefs.shortcutsEnabled(), shortcutGroup));
        shortcut.addView(shortcutGroup);

        LinearLayout correct = section("자동 수정");
        correct.addView(toggle("자동 수정", "스페이스바를 누를 때 오타로 추정되는 단어를 사전에 있는 단어로 바꿉니다. "
                + "바꾼 직후 ⌫를 누르면 되돌리고, 두 번 되돌리면 학습해서 더는 고치지 않습니다.",
                Prefs.AUTO_CORRECT, prefs.autoCorrect()));

        LinearLayout learn = section("입력한 단어 학습");
        learn.addView(toggle("입력한 단어 학습", "내가 자주 입력하는 단어를 기억해 추천에 먼저 보여 주고 자동 수정에서 뺍니다. "
                + "단어 추천이나 자동 수정을 켰을 때만 동작합니다.",
                Prefs.LEARN_WORDS, prefs.learnWords()));
        learn.addView(toggle("단어를 지울 때 확인", "학습한 단어를 지울 때 한 번 더 확인합니다.",
                Prefs.CONFIRM_LEARNED_DELETE, prefs.confirmLearnedDelete()));
        learn.addView(button("학습한 단어 보기", v -> openSection(LEARNED)));
        learn.addView(button("학습하지 않는 단어 보기", v -> openSection(BLOCKED)));
        learn.addView(trashButton("학습한 단어 모두 지우기", v -> AppTheme.dialogBuilder(this)
                .setTitle("학습한 단어 모두 지우기")
                .setMessage("키보드가 기억한 단어와, 추천 순서에 쓰는 입력 기록(자주 쓴 단어·이어 쓴 단어), "
                        + "추천란에서 골라 자동 수정하게 된 단어를 모두 지웁니다. "
                        + "되돌릴 수 없습니다.")
                .setPositiveButton("모두 지우기", (d, w) -> {
                    UserDictionary.shared(getFilesDir()).clear();
                    UsageHistory.shared(getFilesDir()).clear();
                    CorrectionMemory.shared(getFilesDir()).clear();
                    Toast.makeText(this, "학습한 단어를 지웠습니다", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("취소", null)
                .show()));

        // 자동 완성: 서비스(Autofill with Google, 삼성 패스 등)의 제안을 도구 막대 자리에 칩으로 (Android 11 이상).
        LinearLayout autofill = section("자동 완성");
        if (Build.VERSION.SDK_INT >= 30) {
            autofill.addView(toggle("자동 완성 제안",
                    "자동 완성 서비스의 내용(예: 아이디와 비밀번호, 인증 코드)을 도구 막대에 보여 줍니다. ",
                    Prefs.AUTOFILL_INLINE, prefs.autofillInline()));
            autofill.addView(note("기기 설정에서 고른 자동 완성 서비스(Autofill with Google, 삼성 패스 등)를 사용합니다. "));
        } else {
            autofill.addView(note("Android 11 이상에서 쓸 수 있습니다. 이 기기에서는 앱 안에 뜨는 원래의 자동 완성 목록을 쓰세요."));
        }

        LinearLayout about = section("개인 정보");
        about.addView(note("모든 처리는 기기 안에서만 하며 인터넷을 쓰지 않습니다. 비밀번호·이메일 입력란에서는 학습을 하지 않습니다."));

        buildDictionaryPack();
    }

    /**
     * 한국어 사전: 앱에 든 기본 사전 대신 쓸 사전 팩을 가져오거나 지운다. 팩은 사용자가 브라우저로 받은 zip 파일이며
     * (앱은 인터넷을 쓰지 않는다) 파일 고르기 창으로 가져온다.
     */
    private void buildDictionaryPack() {
        LinearLayout dict = section("한국어 사전");
        DictionaryPack.Info info = DictionaryPack.installed(getFilesDir());
        if (info == null) {
            dict.addView(note("NewSwipe에 포함된 기본 사전을 사용 중입니다. "
                    + "단어와 단어 쌍이 더 많은 사전 팩을 가져오면 추천이 더 잘 맞습니다."));
        } else {
            StringBuilder b = new StringBuilder();
            b.append(info.name);
            if (!info.version.isEmpty()) b.append(" ").append(info.version);
            b.append("\n단어 ").append(String.format(java.util.Locale.ROOT, "%,d", info.words)).append("개");
            if (info.pairs > 0) b.append(" · 단어 쌍 ").append(String.format(java.util.Locale.ROOT, "%,d", info.pairs)).append("개");
            b.append(" · ").append(String.format(java.util.Locale.ROOT, "%.1f", info.bytes / 1048576.0)).append("MB");
            if (!info.description.isEmpty()) b.append("\n").append(info.description);
            if (!info.attribution.isEmpty() || !info.license.isEmpty()) {
                b.append("\n출처: ").append(info.attribution);
                if (!info.license.isEmpty()) b.append(" (").append(info.license).append(")");
            }
            dict.addView(note(b.toString()));
        }
        dict.addView(button("사전 팩 받는 곳", v -> openUrl(SOURCE_URL + "/releases?q=ko-standard&expanded=true")));   // 사전 팩 릴리스만 (검색은 태그가 아니라 제목·설명에서 찾는다)
        dict.addView(iconButton("사전 팩 가져오기", Icons.IMPORT, v -> SettingsImport.pick(this, REQ_DICT)));
        if (info != null) {
            if (!info.licenseText.isEmpty()) {
                dict.addView(button("사전 팩의 라이선스 보기", v -> AppTheme.dialogBuilder(this)
                        .setTitle(info.name)
                        .setMessage(info.licenseText)
                        .setPositiveButton("닫기", null)
                        .show()));
            }
            dict.addView(trashButton("기본 사전으로 되돌리기", v -> AppTheme.dialogBuilder(this)
                    .setTitle("기본 사전으로 되돌리기")
                    .setMessage("가져온 사전 팩을 지우고 앱에 든 기본 사전을 씁니다. 학습한 단어와 입력 기록은 그대로 남습니다.")
                    .setPositiveButton("되돌리기", (d, w) -> {
                        DictionaryPack.remove(getFilesDir());
                        Toast.makeText(this, "기본 사전으로 되돌렸습니다", Toast.LENGTH_SHORT).show();
                        recreate();
                    })
                    .setNegativeButton("취소", null)
                    .show()));
        }
        dict.addView(note("사전 팩마다 데이터의 출처와 라이선스가 다르니 가져오기 전에 확인하세요."));
    }

    /** 고른 사전 팩을 검사해 설치한다 (크기가 커서 따로 처리한다). */
    private void importDictionaryPack(android.net.Uri uri) {
        Toast.makeText(this, "사전 팩을 확인하는 중입니다…", Toast.LENGTH_SHORT).show();
        android.content.Context app = getApplicationContext();
        new Thread(() -> {
            String message;
            boolean ok = false;
            try (java.io.InputStream in = app.getContentResolver().openInputStream(uri)) {
                if (in == null) throw new java.io.IOException("파일을 열지 못했습니다");
                DictionaryPack.Info info = DictionaryPack.install(in, app.getFilesDir());
                message = "'" + info.name + "' 사전을 가져왔습니다";
                ok = true;
            } catch (DictionaryPack.InvalidPackException e) {
                message = "가져올 수 없는 사전 팩입니다: " + e.getMessage();
            } catch (Exception e) {
                message = "사전 팩을 가져오지 못했습니다: " + e.getMessage();
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
        card().addView(note("'" + wordQuery + "'" + com.alternative_studios.newswipe.suggest.WordSuggester.objectParticle(wordQuery)
                + " 포함한 단어가 없습니다."));
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
            card().addView(note("아직 학습한 단어가 없습니다. 단어 추천이나 자동 수정을 켜고 입력하면, "
                    + "자주 쓰는 단어를 기억해 여기에 보여 줍니다."));
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
                ? (searching ? "찾은 단어를 모두 지웠습니다." : "학습한 단어를 모두 지웠습니다.")
                : searching
                ? "'" + wordQuery + "' 검색 결과: 학습한 단어 " + remaining[0] + "개, 후보 " + remaining[1] + "개 (많이 쓴 순서)"
                : "학습한 단어 " + remaining[0] + "개, 후보 " + remaining[1] + "개. 많이 쓴 순서입니다. "
                + "학습한 단어는 추천에 먼저 나오고 자동 수정에서 빠집니다.");
        updateSummary.run();

        if (!known.isEmpty()) {
            LinearLayout words = learnedCard("학습한 단어");
            addLearnedRows(words, known, 0, dict, remaining, 0, updateSummary);
        }
        if (!candidates.isEmpty() && searching) {
            // 검색 중에는 찾은 후보를 바로 보인다.
            addLearnedRows(learnedCard("후보 (한 번 입력한 단어)"), candidates, 0, dict, remaining, 1, updateSummary);
        } else if (!candidates.isEmpty()) {
            // 후보는 실제로 쓰이지 않으므로 접어 둔다.
            LinearLayout cands = learnedCard("후보 (한 번 입력한 단어)");
            TextView open = new TextView(this);
            open.setText("후보 " + candidates.size() + "개 보기 · 한 번 더 입력하면 학습됩니다");
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
            more.setText("더 보기 (" + (all.size() - to) + "개 남음)");
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
        d.setText(count + "회 입력");
        d.setTextColor(hintColor);
        d.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        texts.addView(d);
        row.addView(texts, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        IconButton trash = new IconButton(this, Icons.TRASH, hintColor, hintColor & 0x00FFFFFF | 0x33000000, "'" + word + "' 지우기");
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
            Toast.makeText(this, "'" + word + "'" + com.alternative_studios.newswipe.suggest.WordSuggester.objectParticle(word)
                    + " 지웠습니다. 다시 학습하지 않습니다", Toast.LENGTH_SHORT).show();
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
            card().addView(note("학습하지 않는 단어가 없습니다. 학습한 단어를 지우면(설정이나 추천을 길게 눌러서) "
                    + "그 단어는 다시 학습하지 않고 여기에 보입니다."));
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
                ? (searching ? "찾은 단어를 모두 목록에서 뺐습니다." : "학습하지 않는 단어가 없습니다.")
                : searching
                ? "'" + wordQuery + "' 검색 결과: 학습하지 않는 단어 " + remaining[0] + "개"
                : "학습하지 않는 단어 " + remaining[0] + "개. 지운 단어라 다시 입력해도 학습하지 않습니다. "
                + "되돌림 버튼을 누르면 목록에서 빼고 다시 학습합니다.");
        updateSummary.run();
        LinearLayout words = learnedCard("학습하지 않는 단어");
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
            more.setText("더 보기 (" + (all.size() - to) + "개 남음)");
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
                "'" + word + "' 다시 학습하기");
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
            Toast.makeText(this, "'" + word + "'" + com.alternative_studios.newswipe.suggest.WordSuggester.objectParticle(word)
                    + " 다시 학습합니다", Toast.LENGTH_SHORT).show();
        });
        row.addView(restore, new LinearLayout.LayoutParams(Ui.dp(this, 44), Ui.dp(this, 44)));
        return row;
    }

    /** 학습한 단어를 지울지 묻는다. '다시 보지 않음'을 체크하고 삭제하면 다음부터는 묻지 않는다. */
    private void confirmLearnedDelete(String word, Runnable delete) {
        Context dc = AppTheme.dialogContext(this);
        android.widget.CheckBox dontAsk = new android.widget.CheckBox(dc);
        dontAsk.setText("다시 보지 않음");
        dontAsk.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        android.widget.FrameLayout box = new android.widget.FrameLayout(dc);
        int pad = Ui.dp(this, 20);
        box.setPadding(pad, Ui.dp(this, 4), pad, 0);
        box.addView(dontAsk);
        AppTheme.accentBuilder(dc)
                .setTitle("학습한 단어 지우기")
                .setMessage("'" + word + "'" + com.alternative_studios.newswipe.suggest.WordSuggester.objectParticle(word) + " 지우시겠습니까? "
                        + "지운 단어는 다시 학습하지 않습니다 ('학습하지 않는 단어 보기'에서 되돌릴 수 있습니다).")
                .setView(box)
                .setPositiveButton("지우기", (d, w) -> {
                    // 취소했을 때는 체크해도 저장하지 않는다 (실수로 체크한 채 닫는 경우).
                    if (dontAsk.isChecked()) prefs.raw().edit().putBoolean(Prefs.CONFIRM_LEARNED_DELETE, false).apply();
                    delete.run();
                })
                .setNegativeButton("취소", null)
                .show();
    }

    private Feedback vibePreview;

    /** 방금 바꾼 진동 설정을 바로 느껴 보게 한다 (level 0 = 일반 키 진동, 1~3 = 약하게·기본·강하게). */
    private void previewVibration(int level) {
        if (vibePreview == null) vibePreview = new Feedback(this);
        vibePreview.configure(prefs);
        vibePreview.preview(level);
    }

    private static final String[] LEVEL_LABELS = {"약하게", "기본", "강하게"};

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
        box.addView(Ui.muteHaptics(choiceRowRepeat(LEVEL_LABELS, Math.max(1, Math.min(3, level)) - 1, i -> {
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
        customGroup.addView(slider("진동 길이", Prefs.VIBRATE_MS, prefs.vibrateMs(), 1, 40, 1, "ms",
                () -> previewVibration(0)));
        customGroup.addView(slider("진동 강도", Prefs.VIBRATE_AMP, prefs.vibrateAmp(), 10, 100, 5, "%",
                () -> previewVibration(0)));
        customGroup.addView(note("강도는 진동 세기를 조절할 수 있는 기기에서만 적용됩니다."));
        customGroup.setVisibility(prefs.vibrateStyle() == 3 ? View.VISIBLE : View.GONE);
        View styleRow = Ui.muteHaptics(choiceRowRepeat(new String[]{"약하게", "기본", "강하게", "직접 설정"},
                prefs.vibrateStyle(), i -> {
            prefs.raw().edit().putInt(Prefs.VIBRATE_STYLE, i).apply();
            Ui.setVisibleAnimated(customGroup, i == 3);
            previewVibration(0);
        }));
        LinearLayout perKeyBox = new LinearLayout(this);
        perKeyBox.setOrientation(LinearLayout.VERTICAL);
        perKeyBox.addView(levelRow("지우기 키", Prefs.VIBRATE_DELETE, prefs.vibrateDelete()));
        perKeyBox.addView(levelRow("스페이스 키", Prefs.VIBRATE_SPACE, prefs.vibrateSpace()));
        perKeyBox.addView(levelRow("엔터 키", Prefs.VIBRATE_ENTER, prefs.vibrateEnter()));
        perKeyBox.addView(levelRow("Shift 키 (Fn 키)", Prefs.VIBRATE_SHIFT, prefs.vibrateShift()));
        perKeyBox.addView(levelRow("기호 키", Prefs.VIBRATE_SYMBOL, prefs.vibrateSymbol()));
        perKeyBox.addView(levelRow("언어 전환 키", Prefs.VIBRATE_LANGUAGE, prefs.vibrateLanguage()));
        perKeyBox.addView(levelRow("도구 막대 키", Prefs.VIBRATE_TOOLBAR, prefs.vibrateToolbar()));
        perKeyBox.addView(note("그 밖의 키는 위에서 고른 진동을 씁니다."));
        View perKeySub = subGroup(perKeyBox);
        View gestureSub = subGroup(levelRow(null, Prefs.VIBRATE_GESTURE_LEVEL, prefs.vibrateGestureLevel()));
        View vibrateOptions = subGroup(styleRow, customGroup,
                toggle("키 종류별로 다르게", null,
                        Prefs.VIBRATE_PER_KEY, prefs.vibratePerKey(), perKeySub),
                perKeySub,
                toggle("밀기·길게 누르기 진동", "밀거나 길게 눌렀을 때도 진동합니다.",
                        Prefs.VIBRATE_GESTURE_ON, prefs.vibrateGestureOn(), gestureSub),
                gestureSub);
        fb.addView(toggle("키 진동", null, Prefs.VIBRATE, prefs.vibrate(), vibrateOptions));
        fb.addView(vibrateOptions);
        View soundVolume = subGroup(slider("소리 크기", Prefs.SOUND_VOLUME, prefs.soundVolume(), 0, 100, 5, "%"));
        fb.addView(toggle("키 소리", null, Prefs.SOUND, prefs.sound(), soundVolume));
        fb.addView(soundVolume);
    }

    private void buildTools() {
        LinearLayout clip = section("클립보드");
        View clipImagesToggle = toggle("이미지 저장", "복사한 이미지도 클립보드에 저장합니다 (최대 "
                        + ClipboardHistory.MAX_IMAGES + "개).",
                prefs.clipboardImages(), this::onClipImagesChanged);
        clipImagesSwitch = switchOf(clipImagesToggle);
        View clipSensitiveToggle = toggle("민감한 정보도 저장",
                "비밀번호와 같이 민감한 정보도 클립보드에 저장합니다. 보안에 주의하시기 바랍니다.",
                Prefs.CLIPBOARD_SENSITIVE, prefs.clipboardSensitive());
        View clipCloseToggle = toggle("항목 사용 시 클립보드 닫기",
                "클립보드 항목을 눌러 입력하면 클립보드 창을 자동으로 닫고 자판으로 돌아갑니다.",
                Prefs.CLIPBOARD_CLOSE_ON_USE, prefs.clipboardCloseOnUse());
        View clipSplitToggle = toggle("고정 항목 분리",
                "클립보드를 두 페이지로 나눠 왼쪽에 고정 항목을 따로 모아 둡니다.",
                Prefs.CLIPBOARD_SPLIT_PINNED, prefs.clipboardSplitPinned());
        View clipImages = subGroup(clipImagesToggle, clipSensitiveToggle, clipCloseToggle, clipSplitToggle);
        clip.addView(toggle("클립보드 기록 저장", "복사·잘라낸 내용을 최근 " + ClipboardHistory.MAX_ITEMS
                        + "개까지 모아 둡니다. 고정하지 않은 항목은 24시간 뒤 지워집니다. ",
                Prefs.CLIPBOARD_HISTORY, prefs.clipboardHistory(), clipImages));
        clip.addView(clipImages);
        clip.addView(toggle("선택하면 편집 버튼 보이기",
                "글자를 선택하면 도구 막대에 잘라내기·복사·붙여넣기·전체 선택 버튼이 뜹니다.",
                Prefs.SELECTION_CHIPS, prefs.selectionChips()));
        clip.addView(toggle("방금 복사한 내용 붙여넣기",
                "방금 복사한 텍스트를 도구 막대에 1분 간 보여 주어 빠르게 붙여 넣을 수 있게 합니다.",
                Prefs.CLIPBOARD_CHIP, prefs.clipboardChip()));
        clip.addView(trashButton("클립보드 기록 모두 지우기", v -> AppTheme.dialogBuilder(this)
                .setTitle("클립보드 기록 모두 지우기")
                .setMessage("저장된 클립보드 기록을 모두 지웁니다. 고정한 항목도 함께 지워지며 되돌릴 수 없습니다.")
                .setPositiveButton("모두 지우기", (d, w) -> {
                    new ClipboardHistory(new File(getFilesDir(), "clipboard.txt")).clearAll();
                    Toast.makeText(this, "클립보드 기록을 지웠습니다", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("취소", null)
                .show()));

        LinearLayout emoji = section("이모지");
        emoji.addView(emojiSizeRow());
        emoji.addView(toggle("이모지 추천", "이모지 창을 열면 커서 앞에 쓴 글(마지막 12단어)에 맞는 이모지를 보여 줍니다.", Prefs.EMOJI_SUGGEST, prefs.emojiSuggest()));
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
                .setTitle("이미지 저장 끄기")
                .setMessage("클립보드에 이미지가 있습니다. 이미지 저장 기능을 끌 시 클립보드의 이미지가 모두 사라집니다. 계속하시겠습니까?")
                .setPositiveButton("끄기", (d, w) -> {
                    done[0] = true;
                    prefs.raw().edit().putBoolean(Prefs.CLIPBOARD_IMAGES, false).apply();
                    history.clearImages();
                })
                .setNegativeButton("취소", null)
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
        version.setText("버전 " + versionName());
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

        about.addView(button("개인정보 처리방침", v -> openSection(PRIVACY)));
        about.addView(button("오픈소스 라이선스", v -> openSection(LICENSES)));
        about.addView(button("소스 코드", v -> openUrl(SOURCE_URL)));
        about.addView(button("문의", v -> openUrl(ISSUES_URL)));

        LinearLayout app = section("앱 아이콘");
        app.addView(launcherToggle());
    }

    private void openUrl(String url) {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url)));
        } catch (android.content.ActivityNotFoundException e) {
            Toast.makeText(this, "웹 브라우저를 열 수 없습니다: " + url, Toast.LENGTH_LONG).show();
        }
    }

    private void buildBackup() {
        LinearLayout backup = card();
        backup.addView(note("모든 설정을 파일로 저장하거나 불러옵니다. "
                + "학습한 단어와 최근 이모지(고정된 이모지 포함), 클립보드 기록(고정된 클립보드 항목 포함)은 "
                + "내보낼 때 같이 담을지 고를 수 있습니다. "
                + "클립보드 기록은 텍스트만 담기고 이미지는 담기지 않습니다. "
                + "이 항목들은 개인정보를 담고 있어, 담을 때는 비밀번호로 파일을 암호화하는 것을 추천합니다. "
                + "암호 없이 내보낸 파일은 공유 시 주의하시기 바랍니다."));
        backup.addView(iconButton("설정 가져오기", Icons.IMPORT, v -> SettingsImport.pick(this, REQ_IMPORT)));
        backup.addView(iconButton("설정 내보내기", Icons.EXPORT, v -> chooseExportExtras()));
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
        try (java.io.InputStream in = getAssets().open(asset)) {
            java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            for (int n; (n = in.read(buf)) > 0; ) bytes.write(buf, 0, n);
            text = new String(bytes.toByteArray(), java.nio.charset.StandardCharsets.UTF_8);
        } catch (java.io.IOException e) {
            card().addView(note("안내를 불러오지 못했습니다."));
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
        android.widget.CheckBox words = hasWords ? exportCheck(dc, box, "학습한 단어 (단어 사용 기록·자동 수정 기억 포함)") : null;
        android.widget.CheckBox emoji = hasEmoji ? exportCheck(dc, box, "최근 사용한 이모지 (고정한 이모지 포함)") : null;
        android.widget.CheckBox clip = hasClip ? exportCheck(dc, box, "클립보드 기록 (고정한 항목 포함, 이미지 제외)") : null;
        AppTheme.accentBuilder(dc)
                .setTitle("백업 파일에 함께 담을 기록")
                .setView(box)
                .setPositiveButton("내보내기", (d, w) -> {
                    boolean ww = words != null && words.isChecked(), we = emoji != null && emoji.isChecked(),
                            wc = clip != null && clip.isChecked();
                    // 개인적인 기록을 담을 때만 비밀번호로 암호화할지 묻는다.
                    if (ww || we || wc) askExportPassword(ww, we, wc);
                    else startExport(false, false, false, null);
                })
                .setNegativeButton("취소", null)
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
        info.setText("개인정보가 담기므로 비밀번호로 암호화하는 것을 강력히 권장합니다. 비밀번호는 " + BackupCrypto.MIN_PASSWORD
                + "자 이상의 영문·숫자·특수문자입니다. 비밀번호를 잊으면 파일을 열 수 없으니 잘 기억해 두세요.");
        info.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        box.addView(info);
        android.widget.EditText pw1 = SettingsImport.passwordField(dc, "비밀번호");
        android.widget.EditText pw2 = SettingsImport.passwordField(dc, "비밀번호 확인");
        box.addView(pw1);
        box.addView(pw2);
        TextView error = new TextView(dc);
        error.setTextColor(0xFFE5484D);
        error.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        error.setVisibility(View.GONE);
        box.addView(error);
        android.app.AlertDialog dialog = AppTheme.accentBuilder(dc)
                .setTitle("백업 파일 암호화")
                .setView(box)
                .setPositiveButton("암호화하여 내보내기", null)
                .setNeutralButton("암호 없이", (d, w) -> startExport(withWords, withEmoji, withClipboard, null))
                .setNegativeButton("취소", null)
                .create();
        dialog.setOnShowListener(d -> dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String problem = BackupCrypto.checkPassword(pw1.getText());
            if (problem == null && !pw1.getText().toString().equals(pw2.getText().toString())) {
                problem = "두 비밀번호가 다릅니다";
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
            if (password != null) Toast.makeText(this, "암호화하는 중입니다…", Toast.LENGTH_SHORT).show();
            // 비밀번호에서 키를 만드는 데 시간이 걸리므로 화면이 멈추지 않게 따로 처리한다.
            android.content.Context app = getApplicationContext();
            new Thread(() -> {
                String message;
                try {
                    SettingsBackup.export(app, uri, words, emoji, clip, password);
                    message = password != null ? "설정을 암호화하여 내보냈습니다" : "설정을 내보냈습니다";
                } catch (Exception e) {
                    message = "내보내지 못했습니다: " + e.getMessage();
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
        if (id.startsWith(EDITOR_PART)) return Icons.MENU_KEYBOARD;
        switch (id) {
            case KEYBOARD: return Icons.MENU_KEYBOARD;
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
        return toggle("런처에 앱 아이콘 표시",
                "끄면 홈 화면·앱 목록에서 앱 아이콘이 사라집니다. 설정은 시스템의 키보드 설정이나 "
                        + "키보드 도구 막대의 설정 버튼에서 열 수 있습니다. 설정 버튼도 끈 상태라면 "
                        + "시스템 설정 > 키보드에서 열어야 하니 주의하세요.",
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
            status.setText("✓ NewSwipe를 사용하고 있습니다");
            status.setTextColor(accentColor);
        } else if (enabled) {
            status.setText("사용 설정됨 · 2번을 눌러 기본 키보드로 선택하세요");
            status.setTextColor(textColor);
        } else {
            status.setText("1번을 눌러 NewSwipe를 켜 주세요");
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
