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
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.alternative_studios.newswipe.clipboard.ClipboardHistory;
import com.alternative_studios.newswipe.keyboard.FunctionSwipes;
import com.alternative_studios.newswipe.keyboard.Icons;
import com.alternative_studios.newswipe.keyboard.KeyboardSwipes;
import com.alternative_studios.newswipe.keyboard.ToolbarSwipes;
import com.alternative_studios.newswipe.suggest.UserDictionary;
import com.alternative_studios.newswipe.ui.Chevron;
import com.alternative_studios.newswipe.ui.ExpressiveSlider;
import com.alternative_studios.newswipe.ui.ExpressiveSwitch;
import com.alternative_studios.newswipe.ui.IconView;
import com.alternative_studios.newswipe.ui.IconButton;
import com.alternative_studios.newswipe.ui.Ui;

import java.io.File;

/** 설정 화면. 런처에서 앱을 열면 이 화면이 뜬다. */
public final class SettingsActivity extends Activity {

    /** 하위 메뉴를 열 때 넘기는 값. 없으면 첫 화면이다. */
    static final String EXTRA_SECTION = "section";

    private Prefs prefs;
    private LinearLayout list;
    private TextView status;
    private View setupCard, setupHeading, appCard;
    /** 첫 화면의 설정 메뉴 (제목과 카드). 시작하기를 처음 마치기 전에는 흐리게 하고 막는다. */
    private View menuHeading, menuCard;
    private int textColor, hintColor, accentColor, cardColor, onAccentColor;
    /** 클립보드 '이미지 저장' 스위치 (끄기를 취소하면 되돌린다). */
    private ExpressiveSwitch clipImagesSwitch;
    private String themeSignature;
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

        // 위쪽 머리글(고정) + 아래 스크롤 목록
        String sectionId = getIntent().getStringExtra(EXTRA_SECTION);
        String sectionName = sectionId == null ? null : sectionTitle(sectionId);
        SettingsFrame frame = sectionName == null
                ? new SettingsFrame(this, getString(R.string.app_name), "밀어서 입력하고 조작하는 한글 입력 시스템")
                : new SettingsFrame(this, sectionName, getString(R.string.app_name));
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setId(0x5E771001);   // 화면 모드를 바꿔 다시 만들 때 스크롤 위치를 복원한다
        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        int pad = Ui.dp(this, 16);
        list.setPadding(pad, 0, pad, Ui.dp(this, 32));
        scroll.addView(list);
        setContentView(frame.wrap(scroll));
        build();
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
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) updateStatus();   // 입력기 선택 창을 닫고 돌아왔을 때
    }

    // ---------------------------------------------------------------- 화면 구성

    /** 하위 메뉴 정의: {id, 제목, 설명}. */
    private static final String[][] SECTIONS = {
            {"layout", "자판 레이아웃", "한글 자판 배열, 숫자 줄, 기능키 배열"},
            {"theme", "테마", "화면 모드, 강조 색"},
            {"look", "자판 모양", "키보드 높이, 글자 크기, 여백"},
            {"input", "입력 동작", "쌍자음, 이중모음, 자동 대소문자, 마침표"},
            {"keys", "길게 누르기", "문자 입력, 기능키 길게 누르기"},
            {"swipe", "밀어서 글자 입력", "쌍자음, 이중모음, 완전 사용자화"},
            {"swipefn", "밀어서 기능", "기능키·도구 막대 밀기, 완전 사용자화"},
            {"feedback", "소리 및 진동", "키를 누를 때의 진동과 소리"},
            {"tools", "도구 막대와 클립보드", "도구 막대 버튼·위치·높이, 클립보드 기록"},
            {"words", "단어 추천", "단어 추천, 단축어, 자동 수정, 입력한 단어 학습"},
            {"onehand", "한 손 모드", "자판 폭·키 높이, 세로 위치"},
            {"backup", "설정 가져오기 및 내보내기", "모든 설정을 파일로 저장하거나 불러오기"},
            {"lab", "실험실", "대화면 별도 레이아웃, 스와이프 방향 판정"},
    };

    /** 첫 화면 맨 위에 설정 메뉴와 따로 두는 항목. */
    private static final String[] APP = {"app", "정보", "앱 버전, 개인정보 처리방침, 라이선스, 문의"};
    /** 첫 화면에서 정보 바로 아래에 두는 사용법. */
    private static final String[] GUIDE = {"usage", "사용법", "NewSwipe의 간단한 사용법"};

    /** 메뉴에는 없고 다른 화면에서 여는 하위 화면. */
    private static final String LEARNED = "learned", USAGE = "usage", PRIVACY = "privacy", LICENSES = "licenses",
            KEY_AREA = "keyarea", LANDSCAPE_AREA = "landscapearea",
            LARGE_AREA = "largearea";

    private static final String SOURCE_URL = "https://github.com/TechTree-Alt/NewSwipe";
    private static final String ISSUES_URL = SOURCE_URL + "/issues";

    private static String sectionTitle(String id) {
        for (String[] s : SECTIONS) if (s[0].equals(id)) return s[1];
        if (APP[0].equals(id)) return APP[1];
        switch (id) {
            case LEARNED: return "학습한 단어";
            case USAGE: return "사용법";
            case PRIVACY: return "개인정보 처리방침";
            case LICENSES: return "오픈소스 라이선스";
            case KEY_AREA: return "키 위치·폭 사용자화";
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
        switch (id) {
            case "layout": buildKeyboardLayout(); break;
            case "look": buildLook(); break;
            case "onehand": buildOneHand(); break;
            case "theme": buildTheme(); break;
            case "keys": buildKeys(); break;
            case "input": buildInput(); break;
            case "swipe": buildSwipe(); break;
            case "swipefn": buildSwipeFunctions(); break;
            case "words": buildWords(); break;
            case LEARNED: buildLearned(); break;
            case "feedback": buildFeedback(); break;
            case "tools": buildTools(); break;
            case "backup": buildBackup(); break;
            case USAGE: buildUsage(); break;
            case PRIVACY: buildPrivacy(); break;
            case LICENSES: buildLicenses(); break;
            case KEY_AREA: buildKeyArea(); break;
            case LANDSCAPE_AREA: buildLandscapeArea(); break;
            case "lab": buildLab(); break;
            case LARGE_AREA: buildLargeArea(); break;
            default: buildApp(); break;
        }
        if (hasReset(id)) addResetButton(id);
    }

    /** 기본값으로 되돌릴 설정이 있는 메뉴인지 (설정 메뉴와 키 위치·폭, 가로 모드·대화면 키보드 사용자화 화면. 정보·사용법·가져오기 및 내보내기·학습한 단어 등은 제외). */
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
            prefs.profileEditView(Prefs.PROFILE_LARGE_PORTRAIT).resetProfileArea();
            prefs.profileEditView(Prefs.PROFILE_LARGE_LANDSCAPE).resetProfileArea();
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

        // 정보와 사용법: 설정 메뉴와 따로 둔다.
        LinearLayout app = menuCard(card());
        appCard = app;
        app.addView(menuRow(menuIcon(APP[0]), APP[1], APP[2], v -> openSection(APP[0])));
        app.addView(divider());
        app.addView(menuRow(Icons.MENU_USAGE, GUIDE[1], GUIDE[2], v -> openSection(USAGE)));

        LinearLayout menu = menuCard(section("설정"));
        menuCard = menu;
        menuHeading = list.getChildAt(list.indexOfChild(menu) - 1);
        for (int i = 0; i < SECTIONS.length; i++) {
            String[] sec = SECTIONS[i];
            if (i > 0) menu.addView(divider());
            menu.addView(menuRow(menuIcon(sec[0]), sec[1], sec[2], v -> openSection(sec[0])));
        }
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

    private void buildLook() {
        LinearLayout look = card();
        look.addView(slider("키보드 높이", Prefs.KEYBOARD_HEIGHT, prefs.keyboardHeight(), 70, 140, 5, "%"));
        look.addView(slider("키 글자 크기", Prefs.KEY_TEXT_SIZE, prefs.keyTextSize(), 70, 140, 5, "%"));
        look.addView(slider("키 곡률", Prefs.KEY_RADIUS, prefs.keyRadiusDp(), 0, 24, 1, "dp"));
        View shadowStrength = subGroup(slider("그림자 세기", Prefs.KEY_SHADOW_STRENGTH, prefs.keyShadowStrength(), 10, 100, 5, "%"));
        look.addView(toggle("키 그림자", "키 아래에 그림자를 그려 입체감을 줍니다.",
                Prefs.KEY_SHADOW, prefs.keyShadow(), shadowStrength));
        look.addView(shadowStrength);
        look.addView(toggle("키 누름 미리보기", "누른 키를 말풍선으로 크게 보여 줍니다.",
                Prefs.KEY_PREVIEW, prefs.keyPreview()));
        look.addView(toggle("길게 눌러 입력할 문자 힌트 없애기",
                "키의 오른쪽 위에 표시되는 길게 눌러 입력할 문자 힌트를 없앱니다.",
                Prefs.POPUP_HINT_HIDDEN, prefs.popupHintHidden()));
        look.addView(toggle("숫자 키 힌트 없애기",
                "숫자 키(1~0)에서만 길게 눌러 입력할 문자 힌트를 없앱니다.",
                Prefs.NUMBER_HINT_HIDDEN, prefs.numberHintHidden()));
        LinearLayout pad = section("여백");
        pad.addView(slider("왼쪽 여백", Prefs.PAD_LEFT, prefs.padLeftDp(), 0, 24, 1, "dp"));
        pad.addView(slider("오른쪽 여백", Prefs.PAD_RIGHT, prefs.padRightDp(), 0, 24, 1, "dp"));
        pad.addView(slider("위쪽 여백", Prefs.PAD_TOP, prefs.padTopDp(), 0, 24, 1, "dp"));
        pad.addView(slider("아래쪽 여백", Prefs.PAD_BOTTOM, prefs.padBottomDp(), 0, 48, 1, "dp"));
        pad.addView(slider("키 좌우 여백", Prefs.KEY_GAP_X, prefs.keyGapXDp(), 0, 12, 1, "dp"));
        pad.addView(slider("키 상하 여백", Prefs.KEY_GAP_Y, prefs.keyGapYDp(), 0, 20, 1, "dp"));
    }

    /** 자판 레이아웃: 한글 자판 배열을 고르고, 고른 배열을 실제 키보드 뷰로 미리 보여 준다. */
    private void buildKeyboardLayout() {
        LinearLayout korean = section("한글 자판 배열");
        String[] koreanLayouts = {Prefs.KOREAN_LAYOUT_8, Prefs.KOREAN_LAYOUT_D7};
        int selected = prefs.koreanD7() ? 1 : 0;
        // 배열을 고르면 화면을 다시 만들지 않고 안내문·토글·미리보기를 그 자리에서 바꾼다 (선택 버튼의 스프링이 끊기지 않게).
        TextView layoutNote = note(layoutNoteText());
        // 모든 한글 배열에서 쓸 수 있고, 배열마다 따로 켜고 끈다. 격자 정렬과 함께 쓰면 기능키가 나뉜 자음 키 폭에 맞춰진다.
        // 토글의 하위 항목(강조선·들여쓰기)으로 보이는 버튼이다.
        // 버튼은 위아래 여백이 있어, 버튼 자체를 접으면 높이가 0이 된 뒤 남은 여백이 한꺼번에 사라져 끊겨 보인다.
        // 그래서 여백 없는 틀(fold)로 감싸 그 틀을 펼치고 접는다.
        View keyArea = fold(subGroup(button("키 위치·폭 사용자화", v -> openSection(KEY_AREA))));
        keyArea.setVisibility(prefs.balancedLayout() ? View.VISIBLE : View.GONE);
        View balancedRow = toggle("자·모음 균형 레이아웃",
                "자판을 정확히 반으로 나눠 왼쪽 절반에는 자음을, 오른쪽 절반에는 모음을 놓습니다.",
                prefs.balancedLayout(), on -> {
                    prefs.raw().edit().putBoolean(prefs.balancedLayoutKey(), on).apply();
                    Ui.setVisibleAnimated(keyArea, on);
                    refreshLayoutPreview();
                });
        LinearLayout choices = choiceRowNow(new String[]{"단모음", "NewSwipe 단모음"}, selected,
                i -> {
                    prefs.raw().edit().putString(Prefs.KOREAN_LAYOUT, koreanLayouts[i]).apply();
                    layoutNote.setText(layoutNoteText());
                    // 균형 레이아웃은 배열마다 따로 기억하므로 고른 배열의 값으로 스위치를 맞춘다.
                    ((ExpressiveSwitch) ((ViewGroup) balancedRow).getChildAt(1)).setCheckedSilently(prefs.balancedLayout());
                    Ui.setVisibleAnimated(keyArea, prefs.balancedLayout());
                    refreshLayoutPreview();
                });
        korean.addView(choices);
        korean.addView(layoutPreview());
        korean.addView(layoutNote);
        korean.addView(balancedRow);
        korean.addView(keyArea);

        // 가로 모드일 때의 자판 모양(분리 키보드, 키보드 높이·글자 크기)을 세로 모드와 따로 정한다.
        LinearLayout landscape = section("가로 모드");
        landscape.addView(button("가로 모드 키보드 사용자화", v -> openSection(LANDSCAPE_AREA)));
        landscape.addView(note("화면을 가로로 돌렸을 때의 자판 모양을 따로 정합니다."));

        LinearLayout numberKeys = section("숫자 키");
        numberKeys.addView(toggle("숫자 줄 표시", "자판 위에 1~0 숫자 줄을 보여 줍니다.", prefs.numberRow(), on -> {
            prefs.raw().edit().putBoolean(Prefs.NUMBER_ROW, on).apply();
            refreshLayoutPreview();
        }));

        LinearLayout layoutKeys = section("기능키");
        layoutKeys.addView(button("기능키 순서·유무 사용자화", v ->
                startActivity(new Intent(this, BottomKeysEditorActivity.class))));
        layoutKeys.addView(note("맨 아래 줄 기능키의 순서와 유무를 바꿉니다."));

        LinearLayout keyLayout = section("키 정렬");
        // 바꾸면 위의 미리보기도 바로 다시 그린다.
        keyLayout.addView(toggle("격자 정렬", GRID_DESC, prefs.gridLayout(), on -> {
            prefs.raw().edit().putBoolean(Prefs.GRID_LAYOUT, on).apply();
            refreshLayoutPreview();
        }));
    }

    /** 고른 한글 배열의 설명. */
    private String layoutNoteText() {
        return prefs.koreanD7()
                ? "NewSwipe 단모음은 거센소리 일부를 빼 한 줄을 7칸으로 줄인 배열입니다. "
                        + "거센소리(ㅍ·ㅊ·ㅌ·ㅋ)는 ㅂ·ㅈ·ㄷ·ㄱ를 오른쪽으로 밀어 입력합니다."
                : "단모음은 두벌식 키보드에서 ㅣ계 이중모음(ㅑ·ㅕ·ㅛ·ㅠ)을 빼 한 줄을 8칸으로 줄인 배열입니다. "
                        + "빠진 글자는 모음 키를 아래로 밀어 입력합니다.";
    }

    private static final String GRID_DESC = "아래 줄의 기능키를 한글 키와 같은 폭으로 해 줄을 맞춥니다. "
            + "\n오쏘리니어 키보드에서 영감을 받았습니다.";
    /**
     * 키 위치·폭 사용자화: 미리보기를 보면서 자·모음 균형 레이아웃의 자음 열(왼쪽 절반)과 모음 3열(오른쪽 절반)의
     * 폭과 가로 위치를 정한다. 가로 모드의 값은 '가로 모드 키보드 사용자화'에서 따로 정한다.
     */
    private void buildKeyArea() {
        LinearLayout card = section("자음·모음 열");
        card.addView(layoutPreview(null));
        card.addView(note("자음 키 폭은 왼쪽 절반을, 모음 키 폭은 오른쪽 절반을 꽉 채우는 폭에 대한 비율입니다 (100%면 꽉 참). 가로 위치는 각 절반 안에서 키들이 놓이는 곳입니다 "
                + "(0 = 왼쪽 끝, 50 = 가운데, 100 = 오른쪽 끝)."));
        addKeyAreaControls(card, prefs);
    }

    /**
     * 가로 모드 키보드 사용자화: 분리 키보드를 켜면 가로 모드에서 자판이 자음·모음으로 나뉘고,
     * 그 열의 폭과 가로 위치를 세로 모드와 따로 정한다. 키보드 높이·글자 크기도 따로 정할 수 있다.
     */
    private void buildLandscapeArea() {
        addProfileArea(Prefs.PROFILE_LANDSCAPE, "가로 모드", "화면을 가로로 돌렸을 때");
    }

    /** 실험실: 아직 시험 중인 기능. */
    private void buildLab() {
        LinearLayout intro = card();
        intro.addView(note("아직 시험 중인 기능입니다. 동작이 바뀌거나 없어질 수 있습니다."));

        LinearLayout direction = section("스와이프 방향 판정");
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
    }

    /** 대화면 키보드 사용자화: 위의 연결된 버튼 그룹으로 세로·가로를 고르고, 고른 쪽 키보드를 '가로 모드 키보드 사용자화'와 같은 항목으로 따로 정한다. */
    private void buildLargeArea() {
        int start = list.getChildCount();
        addProfileArea(Prefs.PROFILE_LARGE_PORTRAIT, "대화면 세로", "대화면을 세로로 들었을 때");
        int mid = list.getChildCount();
        addProfileArea(Prefs.PROFILE_LARGE_LANDSCAPE, "대화면 가로", "대화면을 가로로 들었을 때");
        int end = list.getChildCount();
        // 방향마다 만든 카드들을 한 묶음씩 모아, 고른 쪽만 보이게 한다.
        LinearLayout portBox = new LinearLayout(this), landBox = new LinearLayout(this);
        portBox.setOrientation(LinearLayout.VERTICAL);
        landBox.setOrientation(LinearLayout.VERTICAL);
        java.util.List<View> moved = new java.util.ArrayList<>();
        for (int i = start; i < end; i++) moved.add(list.getChildAt(i));
        list.removeViews(start, end - start);
        for (int i = 0; i < moved.size(); i++) (i < mid - start ? portBox : landBox).addView(moved.get(i));
        landBox.setVisibility(View.GONE);

        LinearLayout pick = card();
        pick.addView(Ui.choiceRow(this, new String[]{"세로", "가로"}, 0, accentColor, onAccentColor, textColor, hintColor, 0, i -> {
            portBox.setVisibility(i == 0 ? View.VISIBLE : View.GONE);
            landBox.setVisibility(i == 1 ? View.VISIBLE : View.GONE);
        }));
        pick.addView(note("대화면이 세로일 때와 가로일 때의 키보드 크기·형태를 따로 정합니다."));
        list.addView(portBox);
        list.addView(landBox);
    }

    /**
     * 한 프로필(가로 모드, 대화면 세로·가로)의 분리 키보드와 크기 설정. name은 섹션·스위치 이름 앞에 붙는 말,
     * when은 설명에 쓰는 '언제 쓰는 값인지'.
     */
    private void addProfileArea(String profile, String name, String when) {
        Prefs edit = prefs.profileEditView(profile);
        LinearLayout card = section(name + " 분리 키보드");
        LinearLayout splitBox = new LinearLayout(this);
        splitBox.setOrientation(LinearLayout.VERTICAL);
        splitBox.setVisibility(edit.profileSplit() ? View.VISIBLE : View.GONE);
        splitBox.addView(layoutPreview(profile));
        splitBox.addView(note(when + " 쓰는 값입니다. "
                + "왼쪽 덩어리(자음, 숫자 1~5, 스페이스바와 그 왼쪽 키)와 오른쪽 덩어리(모음, 숫자 6~0, 스페이스바와 그 오른쪽 키)가 각각 함께 움직입니다. "
                + "자음 키 폭·모음 키 폭은 각 덩어리의 폭으로, 100%면 자기 절반을 꽉 채웁니다. 가로 위치는 각 절반 안에서 덩어리가 놓이는 곳입니다 "
                + "(0 = 왼쪽 끝, 50 = 가운데, 100 = 오른쪽 끝)."));
        addKeyAreaControls(splitBox, edit);
        String splitKey = edit.splitKey();
        card.addView(toggle(name + " 분리 키보드",
                when + " 자판을 가운데에서 반으로 나눠 왼쪽 절반에는 자음을, 오른쪽 절반에는 모음을 놓습니다. 스페이스바도 양쪽에 하나씩 나뉩니다.",
                edit.profileSplit(), on -> {
                    prefs.raw().edit().putBoolean(splitKey, on).apply();
                    Ui.setVisibleAnimated(splitBox, on);
                }));
        card.addView(splitBox);

        // 맨 아래 줄: 나뉜 스페이스바를 이을지, 이었을 때 하단 기능키 폭
        LinearLayout bottom = section(name + " 분리 키보드 맨 아래 줄");
        View fnWidth = subGroup(
                slider("하단 기능키 폭", edit.splitFnWidthKey(), edit.splitFnWidthSetting(), 50, 200, 5, "%", this::refreshLayoutPreview),
                note("스페이스바·Shift·⌫를 뺀 맨 아래 줄 키(기호 키·쉼표·지구본·온점·엔터)의 폭입니다. "
                        + "줄이거나 늘린 만큼 스페이스바가 늘거나 줄어듭니다."));
        fnWidth.setVisibility(edit.splitSpaceJoin() ? View.VISIBLE : View.GONE);
        String joinKey = edit.splitSpaceJoinKey();
        bottom.addView(toggle("스페이스바 잇기",
                when + " 쓰는 값입니다. 양쪽 덩어리에 하나씩 나뉜 스페이스바를 가운데로 이어 하나로 만듭니다.",
                edit.splitSpaceJoin(), on -> {
                    prefs.raw().edit().putBoolean(joinKey, on).apply();
                    Ui.setVisibleAnimated(fnWidth, on);
                    refreshLayoutPreview();
                }));
        bottom.addView(fnWidth);

        // 두 덩어리 사이의 빈 공간: 프로필마다 따로 정한다.
        LinearLayout gap = section(name + " 분리 키보드 가운데 빈 공간");
        gap.addView(toggle("밀어서 커서 이동",
                when + " 쓰는 값입니다. 분리 키보드의 두 덩어리 사이 빈 공간을 밀어 커서를 옮깁니다. "
                        + "좌우·상하 이동과 속도는 스페이스바 설정('밀어서 기능')을 따르고, 끄면 빈 공간은 눌러도 밀어도 반응하지 않습니다.",
                edit.splitGapCursorKey(), edit.splitGapCursor()));

        // 키보드 높이·글자 크기: 켜기 전에는 세로 모드('자판 모양')의 값을 그대로 쓴다.
        LinearLayout size = section(name + " 크기");
        LinearLayout sizeBox = new LinearLayout(this);
        sizeBox.setOrientation(LinearLayout.VERTICAL);
        View sizeGroup = subGroup(sizeBox);
        sizeGroup.setVisibility(edit.profileSize() ? View.VISIBLE : View.GONE);
        sizeBox.addView(slider("키보드 높이", edit.heightKey(), edit.keyboardHeight(), 70, 140, 5, "%"));
        sizeBox.addView(slider("키 글자 크기", edit.textSizeKey(), edit.keyTextSize(), 70, 140, 5, "%"));
        String sizeKey = edit.sizeKey();
        size.addView(toggle("키보드 높이·글자 크기 따로 정하기",
                when + " 쓸 키보드 높이와 키 글자 크기를 따로 정합니다.",
                edit.profileSize(), on -> {
                    prefs.raw().edit().putBoolean(sizeKey, on).apply();
                    Ui.setVisibleAnimated(sizeGroup, on);
                }));
        size.addView(sizeGroup);

        // 대화면 세로·가로는 한 손 모드 자판 폭도 따로 정한다 (정하기 전에는 세로·가로 모드의 값).
        String widthKey = edit.oneHandWidthKey();
        if (widthKey != null) {
            LinearLayout oneHand = section(name + " 한 손 모드");
            oneHand.addView(slider("자판 폭", widthKey, edit.oneHandWidthForProfile(), 30, 90, 5, "%"));
            oneHand.addView(note(when + " 한 손 모드에서 쓰는 자판 폭(화면 폭의 %)입니다."));
        }
    }

    /** 자음·모음의 폭·가로 위치 슬라이더. p는 세로 또는 가로 모드용 설정 보기다. */
    private void addKeyAreaControls(LinearLayout card, Prefs p) {
        card.addView(slider("자음 키 폭", p.balancedConsonantWidthKey(), p.balancedConsonantWidth(),
                p.balancedWidthMin(), Prefs.BALANCED_CONSONANT_WIDTH_MAX, 5, "%", this::refreshLayoutPreview));
        card.addView(slider("자음 가로 위치", p.balancedConsonantPosKey(), p.balancedConsonantPos(), 0, 100, 5, "",
                this::refreshLayoutPreview));
        card.addView(slider("모음 키 폭", p.balancedVowelWidthKey(), p.balancedVowelWidth(),
                p.balancedWidthMin(),
                com.alternative_studios.newswipe.keyboard.KeyboardLayout.balancedWidthMax(p), 5, "%",
                this::refreshLayoutPreview));
        card.addView(slider("모음 가로 위치", p.balancedVowelPosKey(), p.balancedVowelPos(), 0, 100, 5, "",
                this::refreshLayoutPreview));
    }

    // ---------------------------------------------------------------- 한 손 모드

    /** 한 손 모드: 쏠릴 쪽을 고르고, 미리보기를 보면서 자판 폭·키 높이·세로 위치를 정한다. */
    private void buildOneHand() {
        LinearLayout card = section("한 손 모드");
        card.addView(oneHandPreview());
        card.addView(note("자판과 도구 막대가 한쪽으로 쏠려 한 손으로 편하게 입력할 수 있습니다.\n"
                + "도구 막대의 한 손 모드 버튼으로 켜고, 버튼을 왼쪽·오른쪽으로 밀면 그쪽으로 쏠린 한 손 모드가 됩니다."));

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
        oneHandKeys.setPopupHints(prefs.longPressChars() && !prefs.popupHintHidden());
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
        oneHandKeys.configure(prefs.longPressMs(), prefs.swipeThresholdDp(), false, prefs.longPressChars(),
                false, Math.round(prefs.keyTextSize() * prefs.oneHandHeight() / 100f));
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
                        kt.hint, kt.keyPressed, null);
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
        for (java.util.Map.Entry<com.alternative_studios.newswipe.keyboard.KeyboardView, String> e : layoutPreviews.entrySet()) {
            refreshLayoutPreview(e.getKey(), previewPrefs(e.getValue()));
        }
    }

    /** 미리보기가 읽는 설정 보기: 프로필이 있으면 그 프로필의 편집 보기. */
    private Prefs previewPrefs(String profile) {
        return profile == null ? prefs : prefs.profileEditView(profile);
    }

    private void refreshLayoutPreview(com.alternative_studios.newswipe.keyboard.KeyboardView view, Prefs p) {
        if (view == null) return;
        view.setGridColors(p.gridColors());
        view.setLayout(com.alternative_studios.newswipe.keyboard.KeyboardLayout.korean(p));
    }

    /** 가로 방향 미리보기(가로 모드, 대화면 가로)의 키 높이·글자 크기 비율. */
    private static final float LANDSCAPE_PREVIEW_SCALE = 0.65f;

    private View layoutPreview() { return layoutPreview(null); }

    /**
     * 지금 설정으로 만든 한글 자판을 실제 키보드 뷰로 그린다. 눌러도 반응하지 않는다.
     * profile이 있으면 그 프로필의 키 폭·위치로 그리고, 가로 방향이면 가로로 넓은 자판처럼 키 높이와 글자 크기를 줄인다.
     */
    private View layoutPreview(String profile) {
        Prefs prefs = previewPrefs(profile);
        boolean landscape = Prefs.PROFILE_LANDSCAPE.equals(profile) || Prefs.PROFILE_LARGE_LANDSCAPE.equals(profile);
        com.alternative_studios.newswipe.keyboard.KeyboardTheme kt =
                com.alternative_studios.newswipe.keyboard.KeyboardTheme.of(this);
        com.alternative_studios.newswipe.keyboard.KeyboardView preview =
                new com.alternative_studios.newswipe.keyboard.KeyboardView(this);
        preview.setTheme(kt);
        // 가로 모드 미리보기는 키가 낮아진 만큼 글자도 작게 그려 키 안에 들어가게 한다.
        preview.configure(prefs.longPressMs(), prefs.swipeThresholdDp(), false, prefs.longPressChars(),
                false, Math.round(prefs.keyTextSize() * (landscape ? LANDSCAPE_PREVIEW_SCALE : 1f)));
        preview.setKeyShadow(prefs.keyShadow(), prefs.keyShadowStrength());
        preview.setKeyRadius(Ui.dp(this, prefs.keyRadiusDp()));
        preview.setGridColors(prefs.gridColors());
        preview.setPopupHints(prefs.longPressChars() && !prefs.popupHintHidden());
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
    }

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

    private void buildKeys() {
        LinearLayout chars = section("문자 입력");
        // 길게 눌러 문자 입력과 길게 눌러 연속 입력이 함께 쓰는 시간이라 두 토글 위에 둔다.
        chars.addView(slider("문자 길게 누르기 시간", Prefs.LONG_PRESS_MS, prefs.longPressMs(), 100, 800, 20, "ms"));
        View editPopup = button("길게 눌러 입력할 문자 편집", v ->
                startActivity(new Intent(this, PopupEditorActivity.class)));
        View charGroup = subGroup(editPopup);
        chars.addView(toggle("길게 눌러 문자 입력", "키를 길게 눌러 특수문자를 입력합니다.",
                Prefs.LONG_PRESS_CHARS, prefs.longPressChars(), charGroup));
        chars.addView(charGroup);
        View repeatGroup = subGroup(button("길게 눌러 연속 입력 편집", v ->
                startActivity(PopupEditorActivity.repeatIntent(this))));
        chars.addView(toggle("길게 눌러 연속 입력", "고른 글자를 길게 누르고 있으면 계속 입력합니다.",
                Prefs.LONG_PRESS_REPEAT, prefs.longPressRepeat(), repeatGroup));
        chars.addView(repeatGroup);

        LinearLayout fn = section("기능키");
        // 모든 기능키(⌫·기호 키·🌐︎·Shift 키·엔터 키·Fn 키)가 같은 시간을 쓴다. 기본 설정·완전 사용자화 모두 같다.
        fn.addView(slider("기능키 길게 누르기 시간", Prefs.DELETE_PRESS_MS, prefs.deletePressMs(), 200, 800, 50, "ms"));
        // 완전 사용자화를 켜면 아래 기본 설정은 쓰지 않으므로 숨기고, 편집 버튼을 보여 준다.
        LinearLayout standard = new LinearLayout(this);
        standard.setOrientation(LinearLayout.VERTICAL);
        standard.addView(toggle("길게 눌러 지우기",
                "⌫를 길게 누르고 있으면 계속 지웁니다. 끄면 한 글자씩만 지워집니다.",
                Prefs.LONG_PRESS_DELETE, prefs.longPressDelete()));
        standard.addView(toggle("기호 키를 길게 눌러 이모지 창 열기",
                "기호 키(숫자 자판의 ?123 포함)를 길게 누르면 이모지 창을 엽니다.",
                Prefs.MODE_KEY_LONG_PRESS, prefs.modeKeyLongPress()));
        View customGroup = subGroup(
                button("기능키 길게 누르기 기능 편집", v ->
                        startActivity(SwipeActionEditorActivity.intent(this,
                                com.alternative_studios.newswipe.keyboard.LongPressActions.SPEC))));
        boolean custom = prefs.longPressCustom();
        standard.setVisibility(custom ? View.GONE : View.VISIBLE);
        customGroup.setVisibility(custom ? View.VISIBLE : View.GONE);
        fn.addView(standard);
        fn.addView(toggle("기능키 길게 누르기 기능 완전 사용자화",
                "Shift 키·기호 키·🌐︎·엔터 키·⌫를 길게 누를 때 실행할 기능을 직접 정합니다.",
                custom, on -> {
                    prefs.raw().edit().putBoolean(Prefs.LONG_PRESS_CUSTOM, on).apply();
                    Ui.setVisibleAnimated(standard, !on);
                    Ui.setVisibleAnimated(customGroup, on);
                }));
        fn.addView(customGroup);
    }

    private void buildInput() {
        LinearLayout input = card();
        View doubleTapTime = subGroup(
                slider("쌍자음 인식 시간", Prefs.DOUBLE_TAP_CONSONANT_MS, prefs.doubleTapConsonantMs(),
                        80, 500, 10, "ms"),
                note("두 번째 탭이 이 시간 안에 들어와야 쌍자음이 됩니다. 값을 줄이면 '학교'가 '하꾜'로 바뀌는 일이 줄고, "
                        + "대신 쌍자음을 낼 때 더 빨리 눌러야 합니다."));
        input.addView(toggle("자음 연속 탭으로 쌍자음",
                "같은 자음을 빠르게 두 번 누르면 쌍자음을 입력합니다 (예: ㄱㄱ→ㄲ, ㅅㅅ→ㅆ). 기존 단모음 자판과 같은 방식이며, 밀어서 쌍자음과 함께 쓸 수 있습니다.",
                Prefs.DOUBLE_TAP_CONSONANT, prefs.doubleTapConsonant(), doubleTapTime));
        input.addView(doubleTapTime);
        input.addView(toggle("모음 연속 탭으로 이중모음", "같은 모음을 연달아 누르면 이중모음을 입력합니다 (예: ㅏㅏ→ㅑ, ㅗㅗ→ㅛ).",
                Prefs.DOUBLE_TAP_VOWEL, prefs.doubleTapVowel()));
        input.addView(toggle("영어 자동 대문자", "문장 첫 글자를 대문자로 시작합니다.", Prefs.AUTO_CAP, prefs.autoCap()));
        input.addView(toggle("스페이스바 두 번으로 마침표", "스페이스바를 빠르게 두 번 누르면 '. '을 넣습니다.",
                Prefs.DOUBLE_SPACE_PERIOD, prefs.doubleSpacePeriod()));
        View deleteShrink = subGroup(slider("줄이는 정도", Prefs.DELETE_HIT_SHRINK_PCT, prefs.deleteHitShrinkPct(),
                5, 50, 5, "%"));
        input.addView(toggle("⌫ 버튼 인식 범위 좁히기",
                "⌫의 가장자리를 누르면 ⌫ 대신 이웃 키를 누른 것으로 간주해 잘못 지워지는 일을 줄입니다. 아래쪽은 아랫줄이 글자 키일 때만 적용됩니다.",
                Prefs.DELETE_HIT_SHRINK, prefs.deleteHitShrink(), deleteShrink));
        input.addView(deleteShrink);
        View spaceShrink = subGroup(slider("줄이는 정도", Prefs.SPACE_HIT_SHRINK_PCT, prefs.spaceHitShrinkPct(),
                5, 50, 5, "%"));
        input.addView(toggle("스페이스바 인식 범위 좁히기",
                "스페이스바의 위쪽 가장자리를 누르면 윗줄 키를 누른 것으로 간주해 스페이스바가 잘못 눌리는 일을 줄입니다.",
                Prefs.SPACE_HIT_SHRINK, prefs.spaceHitShrink(), spaceShrink));
        input.addView(spaceShrink);
    }

    private void buildSwipe() {
        LinearLayout swipe = card();
        swipe.addView(slider("미는 거리", Prefs.SWIPE_THRESHOLD, prefs.swipeThresholdDp(), 12, 48, 2, "dp"));
        swipe.addView(note("값이 작을수록 살짝만 밀어도 인식합니다."));
        // 완전 사용자화를 켜면 쌍자음·겹받침 설정은 쓰지 않으므로 숨기고, 편집 버튼을 보여 준다.
        LinearLayout standard = new LinearLayout(this);
        standard.setOrientation(LinearLayout.VERTICAL);
        // NewSwipe 배열 전용 (ㅅ을 위로 밀어도 ㅆ). 밀어서 쌍자음의 하위 설정이다.
        // 기본 꺼짐.
        if (prefs.koreanNewSwipe()) {
            View ssUp = subGroup(toggle("ㅅ을 위로 밀어서 ㅆ 입력",
                    "ㅅ을 아래로 밀 때뿐만 아니라 위로 밀 때도 ㅆ을 입력합니다. NewSwipe 단모음에서만 쓰입니다.",
                    Prefs.D7_SS_UP, prefs.ssUp()));
            standard.addView(toggle("밀어서 쌍자음", "자음 키를 아래로 밀어 쌍자음을 입력합니다 (예: ㄱ→ㄲ, ㅂ→ㅃ).",
                    Prefs.SWIPE_DOUBLE, prefs.swipeDouble(), ssUp));
            standard.addView(ssUp);
        } else {
            standard.addView(toggle("밀어서 쌍자음", "자음 키를 아래로 밀어 쌍자음을 입력합니다 (예: ㄱ→ㄲ, ㅂ→ㅃ).",
                    Prefs.SWIPE_DOUBLE, prefs.swipeDouble()));
        }
        View finalEdit = button("겹받침 밀어서 글자 입력 편집", v ->
                startActivity(new Intent(this, SwipeFinalEditorActivity.class)));
        View finalGroup = subGroup(finalEdit);
        standard.addView(toggle("밀어서 겹받침", "자음 키를 밀어 겹받침을 입력합니다 (예: ㄹ을 위로 밀면 ㄺ).",
                Prefs.SWIPE_FINAL, prefs.swipeFinal(), finalGroup));
        standard.addView(finalGroup);
        standard.addView(toggle("밀어서 ㅣ계 이중모음",
                "모음 키를 아래로 밀어 ㅣ계 이중모음(ㅑ, ㅕ, ㅛ, ㅠ, ㅒ, ㅖ)을 입력합니다.",
                Prefs.SWIPE_IOTIZED, prefs.swipeIotized()));
        View vowelGroup = subGroup(button("밀어서 조합형 이중모음 입력 편집", v ->
                startActivity(new Intent(this, SwipeVowelEditorActivity.class))));
        standard.addView(toggle("밀어서 조합형 이중모음",
                "모음 키를 위·왼쪽·오른쪽으로 밀어 조합형 이중모음을 입력합니다. 기본은 "
                        + "ㅜ를 왼쪽·위·오른쪽으로 ㅝ·ㅟ·ㅞ, ㅗ를 왼쪽·위·오른쪽으로 ㅘ·ㅚ·ㅙ, ㅡ를 위로 ㅢ입니다.",
                Prefs.SWIPE_COMPOUND_VOWEL, prefs.swipeCompoundVowel(), vowelGroup));
        standard.addView(vowelGroup);
        standard.addView(toggle("온점 키를 위로 밀어서 쉼표 입력", "온점 키를 위로 밀면 쉼표(,)를 입력합니다.",
                Prefs.PERIOD_SWIPE_COMMA, prefs.periodSwipeComma()));
        View customEdit = subGroup(button("밀어서 글자 입력 편집", v ->
                startActivity(new Intent(this, SwipeCustomEditorActivity.class))));
        boolean custom = prefs.swipeCustom();
        standard.setVisibility(custom ? View.GONE : View.VISIBLE);
        customEdit.setVisibility(custom ? View.VISIBLE : View.GONE);
        swipe.addView(standard);
        swipe.addView(toggle("밀어서 글자 입력 완전 사용자화", "모든 키를 위·아래·왼쪽·오른쪽으로 밀 때 입력할 글자를 직접 정합니다.",
                custom, on -> {
                    prefs.raw().edit().putBoolean(Prefs.SWIPE_CUSTOM, on).apply();
                    Ui.setVisibleAnimated(standard, !on);
                    Ui.setVisibleAnimated(customEdit, on);
                }));
        swipe.addView(customEdit);
    }

    /**
     * 한글 입력이 아니라 기능을 하는 키·버튼을 미는 동작들. 미는 거리는 '밀어서 글자 입력'과 따로 정한다.
     * 맨 아래의 완전 사용자화를 켜면 스페이스바·지우기 키·기호 키의 기본 밀기 설정은 쓰지 않고,
     * 다섯 기능 키를 밀 때 실행할 기능을 편집 화면에서 직접 정한다.
     */
    private void buildSwipeFunctions() {
        LinearLayout common = card();
        common.addView(slider("미는 거리", Prefs.FN_SWIPE_THRESHOLD, prefs.fnSwipeThresholdDp(), 12, 48, 2, "dp"));
        common.addView(note("값이 작을수록 살짝만 밀어도 인식합니다."));

        // 기본 밀기 설정: 완전 사용자화를 켜면 쓰지 않으므로 숨기고, 편집 버튼을 보여 준다.
        LinearLayout keys = section("기능키");
        LinearLayout standard = new LinearLayout(this);
        standard.setOrientation(LinearLayout.VERTICAL);
        // 스페이스바를 좌우로 밀기는 커서 좌우 이동과 언어 전환 중 하나만 쓴다: 한쪽을 켜면 다른 쪽을 끈다.
        ExpressiveSwitch[] cursorHSwitch = new ExpressiveSwitch[1], langSwitch = new ExpressiveSwitch[1];
        // 속도 슬라이더는 그 방향 이동을 켰을 때만 보인다 (설명은 둘 중 하나라도 켜져 있을 때).
        View speedH = slider("좌우 이동 속도", Prefs.SPACE_CURSOR_SPEED_H, prefs.spaceCursorSpeedH(), 50, 300, 10, "%");
        View speedV = slider("상하 이동 속도", Prefs.SPACE_CURSOR_SPEED_V, prefs.spaceCursorSpeedV(), 50, 300, 10, "%");
        View speedNote = note("값이 클수록 손가락을 조금만 밀어도 커서가 빠르게 움직입니다.");
        boolean[] axes = {prefs.spaceCursorH(), prefs.spaceCursorV()};
        Runnable showSpeeds = speedVisibility(speedH, speedV, speedNote, axes);
        View cursorH = toggle("좌우 이동", "글자 단위로 커서를 옮깁니다.", axes[0], on -> {
            prefs.raw().edit().putBoolean(Prefs.SPACE_CURSOR_H, on).apply();
            axes[0] = on;
            showSpeeds.run();
            if (on && langSwitch[0] != null && langSwitch[0].isChecked()) langSwitch[0].toggle();
        });
        cursorHSwitch[0] = switchOf(cursorH);
        View cursorV = toggle("상하 이동", "줄 단위로 커서를 옮깁니다 (여러 줄 입력란).", axes[1], on -> {
            prefs.raw().edit().putBoolean(Prefs.SPACE_CURSOR_V, on).apply();
            axes[1] = on;
            showSpeeds.run();
        });
        View cursorGroup = subGroup(cursorH, speedH, cursorV, speedV, speedNote);
        standard.addView(toggle("스페이스바를 밀어서 커서 이동", "스페이스바를 밀어 커서를 옮깁니다.",
                Prefs.SPACE_CURSOR, prefs.spaceCursor(), cursorGroup));
        standard.addView(cursorGroup);
        View lang = toggle("스페이스바를 밀어서 언어 전환",
                "스페이스바를 좌우로 밀면 한/영을 전환합니다.",
                prefs.spaceLangSwipe(), on -> {
                    prefs.raw().edit().putBoolean(Prefs.SPACE_LANG_SWIPE, on).apply();
                    if (on && cursorHSwitch[0].isChecked()) cursorHSwitch[0].toggle();
                });
        langSwitch[0] = switchOf(lang);
        standard.addView(lang);
        standard.addView(toggle("⌫ 밀어서 단위 삭제",
                "⌫를 왼쪽으로 밀면 단어를, 위로 밀면 줄에서 커서 왼쪽을 모두 지웁니다. "
                        + "커서가 줄의 맨 앞이면 위로 밀었을 때 윗줄과 합칩니다.",
                Prefs.DELETE_WORD_SWIPE, prefs.deleteWordSwipe()));
        standard.addView(toggle("기호 키를 위로 밀어서 이모지 열기",
                "기호 키를 위로 밀면 이모지 창이 열립니다.",
                Prefs.MODE_KEY_EMOJI, prefs.modeKeyEmoji()));
        View customEdit = subGroup(button("기능키 밀어서 기능 편집", v ->
                startActivity(SwipeActionEditorActivity.intent(this, FunctionSwipes.SPEC))));
        boolean custom = prefs.swipeFnCustom();
        standard.setVisibility(custom ? View.GONE : View.VISIBLE);
        customEdit.setVisibility(custom ? View.VISIBLE : View.GONE);
        keys.addView(standard);
        keys.addView(toggle("기능키 밀어서 기능 완전 사용자화",
                "기호 키·🌐︎·스페이스바·엔터 키·"
                        + "⌫를 위·아래·왼쪽·오른쪽으로 밀 때 실행할 기능을 직접 정합니다.",
                custom, on -> {
                    prefs.raw().edit().putBoolean(Prefs.SWIPE_FN_CUSTOM, on).apply();
                    Ui.setVisibleAnimated(standard, !on);
                    Ui.setVisibleAnimated(customEdit, on);
                }));
        keys.addView(customEdit);

        // 기본 밀기 설정: 완전 사용자화를 켜면 쓰지 않으므로 숨기고, 편집 버튼을 보여 준다.
        LinearLayout bar = section("도구 막대");
        LinearLayout barStandard = new LinearLayout(this);
        barStandard.setOrientation(LinearLayout.VERTICAL);
        barStandard.addView(toggle("도구 막대를 밀어서 커서 이동",
                "도구 막대에서 왼쪽으로 밀면 줄의 맨 앞으로, 오른쪽으로 밀면 줄의 맨 뒤로, "
                        + "위·아래로 밀면 한 줄 위·아래로 이동합니다 (여러 줄 입력란).",
                Prefs.TOOLBAR_SWIPE, prefs.toolbarSwipe()));
        barStandard.addView(toggle("클립보드 버튼을 밀어서 복사·붙여넣기",
                "클립보드 버튼을 위로 밀면 선택한 텍스트를 복사하고, 아래로 밀면 지금 클립보드의 내용을 붙여 넣습니다.",
                Prefs.TOOL_CLIPBOARD_SWIPE, prefs.toolClipboardSwipe()));
        barStandard.addView(toggle("이모지 버튼을 아래로 밀어서 입력",
                "이모지 버튼을 아래로 밀면 마지막으로 쓴 이모지를 바로 입력합니다.",
                Prefs.TOOL_EMOJI_SWIPE, prefs.toolEmojiSwipe()));
        barStandard.addView(toggle("실행 취소 버튼을 아래로 밀어서 다시 실행",
                "실행 취소 버튼을 아래로 밀면 되돌린 것을 다시 실행합니다 (Ctrl+Shift+Z).",
                Prefs.TOOL_UNDO_SWIPE, prefs.toolUndoSwipe()));
        View barCustomEdit = subGroup(button("도구 막대 밀어서 기능 편집", v ->
                startActivity(SwipeActionEditorActivity.intent(this, ToolbarSwipes.SPEC))));
        boolean barCustom = prefs.swipeToolbarCustom();
        barStandard.setVisibility(barCustom ? View.GONE : View.VISIBLE);
        barCustomEdit.setVisibility(barCustom ? View.VISIBLE : View.GONE);
        bar.addView(barStandard);
        bar.addView(toggle("도구 막대 밀어서 기능 완전 사용자화",
                "도구 막대와 도구 막대의 버튼을 위·아래·왼쪽·오른쪽으로 밀 때 실행할 기능을 직접 정합니다.",
                barCustom, on -> {
                    prefs.raw().edit().putBoolean(Prefs.SWIPE_TOOLBAR_CUSTOM, on).apply();
                    Ui.setVisibleAnimated(barStandard, !on);
                    Ui.setVisibleAnimated(barCustomEdit, on);
                }));
        bar.addView(barCustomEdit);

        // 커서 이동과 키보드 밀기. 키보드 밀기 완전 사용자화를 켜면 문자 키 커서 이동과 두 손가락 실행 취소는
        // 편집 화면('커서 자유 이동' 등)이 대신하므로 숨긴다. 스페이스바 커서 이동은 위 '기능키' 섹션에 있다.
        LinearLayout board = section("커서 이동 · 키보드 밀기");
        LinearLayout boardStandard = new LinearLayout(this);
        boardStandard.setOrientation(LinearLayout.VERTICAL);
        View charSpeedH = slider("좌우 이동 속도", Prefs.CHAR_CURSOR_SPEED_H, prefs.charCursorSpeedH(), 50, 300, 10, "%");
        View charSpeedV = slider("상하 이동 속도", Prefs.CHAR_CURSOR_SPEED_V, prefs.charCursorSpeedV(), 50, 300, 10, "%");
        View charSpeedNote = note("값이 클수록 손가락을 조금만 밀어도 커서가 빠르게 움직입니다.");
        boolean[] charAxes = {prefs.charCursorH(), prefs.charCursorV()};
        Runnable showCharSpeeds = speedVisibility(charSpeedH, charSpeedV, charSpeedNote, charAxes);
        View charCursorGroup = subGroup(
                toggle("좌우 이동", "글자 단위로 커서를 옮깁니다.", charAxes[0], on -> {
                    prefs.raw().edit().putBoolean(Prefs.CHAR_CURSOR_H, on).apply();
                    charAxes[0] = on;
                    showCharSpeeds.run();
                }),
                charSpeedH,
                toggle("상하 이동", "줄 단위로 커서를 옮깁니다 (여러 줄 입력란).", charAxes[1], on -> {
                    prefs.raw().edit().putBoolean(Prefs.CHAR_CURSOR_V, on).apply();
                    charAxes[1] = on;
                    showCharSpeeds.run();
                }),
                charSpeedV, charSpeedNote);
        boardStandard.addView(toggle("문자 키를 밀어서 커서 이동",
                "문자·숫자 키 전체를 트랙패드처럼 밀어 커서를 옮깁니다. "
                        + "밀기 시작한 키에 그 방향으로 밀어서 입력할 글자가 있으면 글자 입력이 우선됩니다.",
                Prefs.CHAR_CURSOR, prefs.charCursor(), charCursorGroup));
        boardStandard.addView(charCursorGroup);
        boardStandard.addView(toggle("두 손가락으로 밀어서 실행 취소",
                "자판을 두 손가락으로 왼쪽으로 밀면 실행 취소(Ctrl+Z), 오른쪽으로 밀면 다시 실행합니다.",
                Prefs.TWO_FINGER_UNDO, prefs.twoFingerUndo()));
        View boardCustomEdit = subGroup(button("키보드 밀기 편집", v ->
                startActivity(SwipeActionEditorActivity.intent(this, KeyboardSwipes.SPEC))));
        boolean boardCustom = prefs.swipeKeyboardCustom();
        boardStandard.setVisibility(boardCustom ? View.GONE : View.VISIBLE);
        boardCustomEdit.setVisibility(boardCustom ? View.VISIBLE : View.GONE);
        board.addView(boardStandard);
        board.addView(toggle("키보드 밀기 완전 사용자화",
                "자판을 한 손가락·두 손가락으로 위·아래·왼쪽·오른쪽으로 밀 때 실행할 기능을 직접 정합니다.",
                boardCustom, on -> {
                    prefs.raw().edit().putBoolean(Prefs.SWIPE_KEYBOARD_CUSTOM, on).apply();
                    Ui.setVisibleAnimated(boardStandard, !on);
                    Ui.setVisibleAnimated(boardCustomEdit, on);
                }));
        board.addView(boardCustomEdit);
    }

    private void buildWords() {
        LinearLayout suggest = section("단어 추천");
        View fullBar = subGroup(toggle("도구 막대 전체를 쓰기",
                "추천이 나오는 동안 도구 막대 왼쪽의 버튼까지 숨기고 도구 막대 전체를 추천에 씁니다.",
                Prefs.SUGGEST_FULL_BAR, prefs.suggestFullBar()),
                toggle("추천 단어 뒤에 공백 포함",
                        "추천 단어를 눌러 입력할 때 단어 뒤에 공백을 붙입니다. 한글과 영어 모두에 적용되며, 끄면 공백 없이 단어만 입력됩니다.",
                        Prefs.SUGGEST_SPACE, prefs.suggestSpace()));
        suggest.addView(toggle("단어 추천", "입력 중인 단어에 맞는 단어를 도구 막대에 보여 줍니다. "
                + "누르면 그 단어로 바꾸고, 학습한 단어를 길게 누르면 학습한 단어에서 지웁니다.",
                Prefs.SUGGEST_WORDS, prefs.suggestWords(), fullBar));
        suggest.addView(fullBar);
        suggest.addView(toggle("이모지 추천", "이모지 창을 열면 커서 앞에 쓴 글(마지막 네 단어)에 맞는 이모지를 최근 탭 맨 위 한 줄에 보여 줍니다. "
                + "비밀번호 입력란에서는 글을 읽지 않습니다.", Prefs.EMOJI_SUGGEST, prefs.emojiSuggest()));

        LinearLayout shortcut = section("단축어");
        View shortcutGroup = subGroup(button("단축어 편집", v -> startActivity(new Intent(this, ShortcutEditorActivity.class))));
        shortcut.addView(toggle("단축어 사용", "사용자가 정한 줄임말을 입력하면 정해 둔 문장을 추천란에 보여 줍니다 (예: ㅈㄱㅈ → 지금 가는 중)",
                Prefs.SHORTCUTS_ENABLED, prefs.shortcutsEnabled(), shortcutGroup));
        shortcut.addView(shortcutGroup);

        LinearLayout correct = section("자동 수정");
        correct.addView(toggle("자동 수정", "스페이스바를 누를 때 사전에 없는 단어를 한 글자만 고치면 되는 흔한 단어로 바꿉니다. "
                + "바꾼 직후 ⌫를 누르면 입력한 그대로 돌아오고, 같은 단어를 두 번 되돌리면 학습해서 더는 고치지 않습니다.",
                Prefs.AUTO_CORRECT, prefs.autoCorrect()));

        LinearLayout learn = section("입력한 단어 학습");
        learn.addView(toggle("입력한 단어 학습", "내가 자주 입력하는 단어를 기억해 추천에 먼저 보여 주고 자동 수정에서 빼 줍니다. "
                + "단어 추천이나 자동 수정을 켰을 때만 동작합니다.",
                Prefs.LEARN_WORDS, prefs.learnWords()));
        learn.addView(toggle("단어를 지울 때 확인", "학습한 단어 목록의 휴지통 버튼이나 추천 막대에서 길게 눌러 지울 때 정말 지울지 묻습니다.",
                Prefs.CONFIRM_LEARNED_DELETE, prefs.confirmLearnedDelete()));
        learn.addView(button("학습한 단어 보기", v -> openSection(LEARNED)));
        learn.addView(trashButton("학습한 단어 모두 지우기", v -> AppTheme.dialogBuilder(this)
                .setTitle("학습한 단어 모두 지우기")
                .setMessage("키보드가 기억한 단어를 모두 지웁니다. 되돌릴 수 없습니다.")
                .setPositiveButton("모두 지우기", (d, w) -> {
                    UserDictionary.shared(getFilesDir()).clear();
                    Toast.makeText(this, "학습한 단어를 지웠습니다", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("취소", null)
                .show()));

        LinearLayout about = section("개인 정보");
        about.addView(note("모든 처리는 기기 안에서만 하며 인터넷을 쓰지 않습니다. 비밀번호·이메일 입력란에서는 학습을 하지 않습니다."));
    }

    /** 한 번에 그리는 학습한 단어 줄 수. 더 있으면 '더 보기'로 이어서 그린다 (수천 줄을 한꺼번에 만들지 않도록). */
    private static final int LEARNED_PAGE = 200;

    /** 학습한 단어 목록 (많이 쓴 순). 줄마다 오른쪽 휴지통 버튼으로 지운다. */
    private void buildLearned() {
        UserDictionary dict = UserDictionary.shared(getFilesDir());
        java.util.List<String[]> all = dict.snapshot();
        all.sort((a, b) -> {
            int c = Integer.compare(Integer.parseInt(b[1]), Integer.parseInt(a[1]));
            return c != 0 ? c : Long.compare(Long.parseLong(b[2]), Long.parseLong(a[2]));
        });
        if (all.isEmpty()) {
            card().addView(note("아직 학습한 단어가 없습니다. 단어 추천이나 자동 수정을 켜고 입력하면, "
                    + "자주 쓰는 단어를 기억해 여기에 보여 줍니다."));
            return;
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
                ? "학습한 단어를 모두 지웠습니다."
                : "학습한 단어 " + remaining[0] + "개, 후보 " + remaining[1] + "개. 많이 쓴 순서입니다. "
                + "학습한 단어는 추천에 먼저 나오고 자동 수정에서 빠집니다.");
        updateSummary.run();

        if (!known.isEmpty()) {
            LinearLayout words = learnedCard("학습한 단어");
            addLearnedRows(words, known, 0, dict, remaining, 0, updateSummary);
        }
        if (!candidates.isEmpty()) {
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
        int to = Math.min(all.size(), from + LEARNED_PAGE);
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
            dict.remove(word, korean);
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
            Toast.makeText(this, "'" + word + "'" + com.alternative_studios.newswipe.suggest.WordSuggester.objectParticle(word) + " 삭제했습니다", Toast.LENGTH_SHORT).show();
        };
        trash.setOnClickListener(v -> {
            if (prefs.confirmLearnedDelete()) confirmLearnedDelete(word, delete);
            else delete.run();
        });
        row.addView(trash, new LinearLayout.LayoutParams(Ui.dp(this, 44), Ui.dp(this, 44)));
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
                .setTitle("학습한 단어 삭제")
                .setMessage("'" + word + "'" + com.alternative_studios.newswipe.suggest.WordSuggester.objectParticle(word) + " 삭제하시겠습니까?")
                .setView(box)
                .setPositiveButton("삭제", (d, w) -> {
                    // 취소했을 때는 체크해도 저장하지 않는다 (실수로 체크한 채 닫는 경우).
                    if (dontAsk.isChecked()) prefs.raw().edit().putBoolean(Prefs.CONFIRM_LEARNED_DELETE, false).apply();
                    delete.run();
                })
                .setNegativeButton("취소", null)
                .show();
    }

    private void buildFeedback() {
        LinearLayout fb = card();
        View vibrateStrength = subGroup(slider("진동 세기", Prefs.VIBRATE_MS, prefs.vibrateMs(), 1, 40, 1, "ms"));
        fb.addView(toggle("키 진동", null, Prefs.VIBRATE, prefs.vibrate(), vibrateStrength));
        fb.addView(vibrateStrength);
        View soundVolume = subGroup(slider("소리 크기", Prefs.SOUND_VOLUME, prefs.soundVolume(), 0, 100, 5, "%"));
        fb.addView(toggle("키 소리", null, Prefs.SOUND, prefs.sound(), soundVolume));
        fb.addView(soundVolume);
    }

    private void buildTools() {
        LinearLayout tools = section("도구 막대");
        View toolButtons = subGroup(button("도구 막대 버튼 순서·유무 사용자화", v ->
                startActivity(new Intent(this, ToolbarButtonsEditorActivity.class))),
                note("도구 막대 버튼의 유무와 순서를 바꿉니다."));
        tools.addView(toggle("도구 막대 표시", "다양한 도구를 빠르게 쓸 수 있는 막대를 보여 줍니다.",
                Prefs.TOOLBAR, prefs.toolbar(), toolButtons));
        tools.addView(toolButtons);
        // 위치와 높이는 도구 막대를 꺼도 추천 단어 줄에 그대로 쓰이므로 스위치와 따로 둔다.
        tools.addView(toggle("자판 아래에 두기",
                "도구 막대를 자판 위가 아니라 아래에 놓습니다. 추천 단어도 그 자리에 뜹니다. 엄지가 닿기 쉬워집니다.",
                Prefs.TOOLBAR_BOTTOM, prefs.toolbarBottom()));
        tools.addView(slider("도구 막대 높이", Prefs.TOOLBAR_HEIGHT, prefs.toolbarHeight(), 70, 150, 5, "%"));
        tools.addView(slider("도구 버튼 크기", Prefs.TOOL_BUTTON_SIZE, prefs.toolButtonSize(), 70, 150, 5, "%"));
        tools.addView(note("도구 버튼의 아이콘은 막대보다 커지지 않습니다."));

        LinearLayout clip = section("클립보드");
        View clipImagesToggle = toggle("이미지 저장", "복사한 이미지도 클립보드에 저장됩니다 (최대 "
                        + ClipboardHistory.MAX_IMAGES + "개).",
                prefs.clipboardImages(), this::onClipImagesChanged);
        clipImagesSwitch = switchOf(clipImagesToggle);
        View clipImages = subGroup(clipImagesToggle);
        clip.addView(toggle("클립보드 기록 저장", "복사·잘라낸 내용을 최근 " + ClipboardHistory.MAX_ITEMS
                        + "개까지 모아 둡니다. 고정하지 않은 항목은 24시간 뒤 지워지고, "
                        + "비밀번호처럼 민감한 정보로 표시된 복사는 저장하지 않습니다.",
                Prefs.CLIPBOARD_HISTORY, prefs.clipboardHistory(), clipImages));
        clip.addView(clipImages);
        clip.addView(trashButton("클립보드 기록 모두 지우기", v -> AppTheme.dialogBuilder(this)
                .setTitle("클립보드 기록 모두 지우기")
                .setMessage("저장된 클립보드 기록을 모두 지웁니다. 고정한 항목도 함께 지워지며 되돌릴 수 없습니다.")
                .setPositiveButton("모두 지우기", (d, w) -> {
                    new ClipboardHistory(new File(getFilesDir(), "clipboard.txt")).clearAll();
                    Toast.makeText(this, "클립보드 기록을 지웠습니다", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("취소", null)
                .show()));
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
        // 정보: 앱 이름과 버전 아래에 개인정보 처리방침·라이선스·소스 코드·문의를 둔다 (사용법은 첫 화면에 따로 있다).
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

    /** 사용법 화면. 설정 화면의 설명보다 크고 진한 글자로, 처음 쓰는 사람도 따라 할 수 있게 풀어 쓴다. */
    private void buildUsage() {
        buildGuide("guide/usage.txt", false);
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

    /** 사용법·개인정보 처리방침의 본문. 설정 설명(note)보다 크고 진한 글자로 쓴다. */
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

    private static final int REQ_EXPORT = 1, REQ_IMPORT = 2;
    private boolean exportWithWords, exportWithEmoji, exportWithClipboard;

    /**
     * 학습한 단어·최근 이모지·클립보드 기록은 개인적인 사용 기록이라, 내보낼 때마다 담을지 고르게 한다 (기본은 담지 않음).
     * 모두 비어 있으면 묻지 않고 바로 내보낸다. 목록(setMultiChoiceItems) 대신 체크박스를 직접 쌓아
     * 항목이 적을 때 목록이 스크롤되며 잘리지 않게 한다.
     */
    private void chooseExportExtras() {
        boolean hasWords = !UserDictionary.shared(getFilesDir()).isEmpty();
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
        android.widget.CheckBox words = hasWords ? exportCheck(dc, box, "학습한 단어") : null;
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
        } else if (requestCode == REQ_IMPORT) {
            SettingsImport.onPicked(this, uri, () -> {
                importGeneration++;
                recreate();
            });
        }
    }

    /**
     * 위 설정의 하위 설정들을 묶는다. 왼쪽 들여쓰기와 세로 안내선으로 위계를 보여 준다.
     * 위 설정이 꺼지면 묶음 전체가 숨겨진다.
     */
    private View subGroup(View... children) {
        LinearLayout group = new LinearLayout(this);
        group.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams glp = matchWrap();
        glp.leftMargin = Ui.dp(this, 6);
        group.setLayoutParams(glp);

        View bar = new View(this);
        bar.setBackground(Ui.round((accentColor & 0x00FFFFFF) | 0x66000000, Ui.dp(this, 2)));
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(Ui.dp(this, 3),
                ViewGroup.LayoutParams.MATCH_PARENT);
        blp.topMargin = Ui.dp(this, 6);
        blp.bottomMargin = Ui.dp(this, 6);
        group.addView(bar, blp);

        LinearLayout column = new LinearLayout(this);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setPadding(Ui.dp(this, 14), 0, 0, 0);
        for (View child : children) column.addView(child);
        group.addView(column, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        return group;
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

    /** 하위 메뉴로 들어가는 줄: 제목, 설명, 오른쪽 화살표. */
    /** 설정 메뉴 항목의 왼쪽 아이콘. */
    private static int menuIcon(String id) {
        switch (id) {
            case "layout": return Icons.MENU_LAYOUT;
            case "look": return Icons.MENU_KEYBOARD;
            case "onehand": return Icons.ONE_HAND;
            case "theme": return Icons.MENU_THEME;
            case "keys": return Icons.MENU_LONG_PRESS;
            case "input": return Icons.MENU_INPUT;
            case "swipe": return Icons.MENU_SWIPE;
            case "swipefn": return Icons.MENU_MOVE;
            case "words": return Icons.MENU_WORDS;
            case "feedback": return Icons.MENU_SOUND;
            case "tools": return Icons.MENU_TOOLBAR;
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

    /** 제목 없는 카드. 하위 화면의 첫 카드에 쓴다. */
    private LinearLayout card() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(Ui.round(cardColor, Ui.dp(this, 16)));
        int p = Ui.dp(this, 16);
        card.setPadding(p, Ui.dp(this, 8), p, Ui.dp(this, 8));
        LinearLayout.LayoutParams lp = matchWrap();
        lp.topMargin = Ui.dp(this, 8);
        list.addView(card, lp);
        return card;
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

    private LinearLayout section(String name) {
        TextView h = new TextView(this);
        h.setText(name);
        h.setTextColor(accentColor);
        h.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        h.setPadding(Ui.dp(this, 4), Ui.dp(this, 20), 0, Ui.dp(this, 8));
        list.addView(h);
        LinearLayout card = card();
        ((LinearLayout.LayoutParams) card.getLayoutParams()).topMargin = 0;
        return card;
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

    private View toggle(String label, String desc, boolean value, java.util.function.Consumer<Boolean> onChange) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, Ui.dp(this, 8), 0, Ui.dp(this, 8));
        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        TextView t = new TextView(this);
        t.setText(label);
        t.setTextColor(textColor);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        texts.addView(t);
        if (desc != null) {
            TextView d = new TextView(this);
            d.setText(desc);
            d.setTextColor(hintColor);
            d.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
            texts.addView(d);
        }
        row.addView(texts, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        ExpressiveSwitch sw = new ExpressiveSwitch(this, accentColor, onAccentColor, hintColor, cardColor);
        sw.setCheckedImmediately(value);
        sw.setOnCheckedChangeListener(checked -> onChange.accept(checked));
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        slp.leftMargin = Ui.dp(this, 12);
        row.addView(sw, slp);
        row.setOnClickListener(v -> sw.performClick());
        return row;
    }

    /**
     * 커서 이동 속도 슬라이더의 보이기: 좌우·상하 이동(axes[0]·axes[1])을 켠 방향의 슬라이더만, 설명은 하나라도 켜져 있을 때.
     * 처음 상태를 바로 맞추고, 돌려준 Runnable을 부르면 바뀐 상태로 (애니메이션과 함께) 다시 맞춘다.
     */
    private static Runnable speedVisibility(View speedH, View speedV, View note, boolean[] axes) {
        speedH.setVisibility(axes[0] ? View.VISIBLE : View.GONE);
        speedV.setVisibility(axes[1] ? View.VISIBLE : View.GONE);
        note.setVisibility(axes[0] || axes[1] ? View.VISIBLE : View.GONE);
        return () -> {   // 이미 그 상태면 setVisibleAnimated가 아무것도 하지 않는다 (진행 중인 애니메이션은 되돌린다)
            Ui.setVisibleAnimated(speedH, axes[0]);
            Ui.setVisibleAnimated(speedV, axes[1]);
            Ui.setVisibleAnimated(note, axes[0] || axes[1]);
        };
    }

    /** toggle()로 만든 줄의 스위치 (줄의 맨 오른쪽). */
    private static ExpressiveSwitch switchOf(View toggleRow) {
        ViewGroup row = (ViewGroup) toggleRow;
        return (ExpressiveSwitch) row.getChildAt(row.getChildCount() - 1);
    }

    private View slider(String label, String key, int value, int min, int max, int step, String unit) {
        return slider(label, key, value, min, max, step, unit, null);
    }

    /** @param onChange 사용자가 값을 바꿀 때마다 (저장한 뒤에) 부를 작업. 없으면 null. */
    private View slider(String label, String key, int value, int min, int max, int step, String unit, Runnable onChange) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(0, Ui.dp(this, 8), 0, Ui.dp(this, 4));
        LinearLayout top = new LinearLayout(this);
        TextView t = new TextView(this);
        t.setText(label);
        t.setTextColor(textColor);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        top.addView(t, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView v = new TextView(this);
        v.setTextColor(hintColor);
        v.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        v.setText(value + unit);
        top.addView(v);
        box.addView(top);
        ExpressiveSlider bar = new ExpressiveSlider(this, accentColor);
        bar.setMax((max - min) / step);
        // 단위에 맞지 않는 값(단위를 바꾸기 전에 저장한 값 등)은 가장 가까운 칸에 둔다.
        bar.setProgress(Math.round((Math.max(min, Math.min(max, value)) - min) / (float) step));
        bar.setOnChangeListener((progress, fromUser) -> {
            int val = min + progress * step;
            v.setText(val + unit);
            if (fromUser) {
                prefs.raw().edit().putInt(key, val).apply();
                if (onChange != null) onChange.run();
            }
        });
        box.addView(bar, matchWrap());
        return box;
    }

    /** 눌러서 실행하는 항목. 제목 글자와 구분되도록 옅은 강조색 바탕과 화살표를 둔다. */
    private View button(String label, View.OnClickListener l) {
        return button(label, new Chevron(this, accentColor), l);
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

    private View button(String label, View trailing, View.OnClickListener l) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        int h = Ui.dp(this, 14);
        row.setPadding(h, Ui.dp(this, 12), h, Ui.dp(this, 12));
        int tint = (accentColor & 0x00FFFFFF) | 0x1F000000;
        row.setBackground(Ui.ripple(accentColor & 0x00FFFFFF | 0x33000000,
                Ui.round(tint, Ui.dp(this, 12)), Ui.dp(this, 12)));
        TextView t = new TextView(this);
        t.setText(label);
        t.setTextColor(accentColor);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        row.addView(t, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        row.addView(trailing);
        row.setClickable(true);
        row.setFocusable(true);
        row.setOnClickListener(l);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = Ui.dp(this, 6);
        lp.bottomMargin = Ui.dp(this, 6);
        row.setLayoutParams(lp);
        return row;
    }

    private TextView note(String s) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextColor(hintColor);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        t.setLineSpacing(Ui.dp(this, 3), 1f);
        t.setPadding(0, Ui.dp(this, 6), 0, Ui.dp(this, 6));
        return t;
    }

    private static LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }
}
