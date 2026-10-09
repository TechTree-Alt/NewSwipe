package com.alternative_studios.newswipe;

import android.content.Context;
import android.content.SharedPreferences;

/** 설정값. 키보드 서비스와 설정 화면이 함께 쓴다. */
public final class Prefs {
    public static final String FILE = "settings";

    public static final String KEYBOARD_HEIGHT = "keyboard_height";   // %, 70~140
    public static final String KEY_TEXT_SIZE = "key_text_size";       // %, 70~140
    public static final String LONG_PRESS_MS = "long_press_ms";       // 100~800
    public static final String SWIPE_THRESHOLD = "swipe_threshold";   // 밀어서 글자 입력의 미는 거리, dp, 12~48
    public static final String FN_SWIPE_THRESHOLD = "fn_swipe_threshold";   // 밀어서 기능의 미는 거리, dp, 12~48
    public static final String VIBRATE = "vibrate";
    public static final String VIBRATE_MS = "vibrate_ms";             // 1~40
    public static final String SOUND = "sound";
    public static final String SOUND_VOLUME = "sound_volume";         // %, 0~100
    public static final String KEY_PREVIEW = "key_preview";
    public static final String NUMBER_ROW = "number_row";
    public static final String LONG_PRESS_CHARS = "long_press_chars";   // 키를 길게 눌러 문자 입력
    public static final String AUTO_CAP = "auto_cap";
    public static final String DOUBLE_SPACE_PERIOD = "double_space_period";
    public static final String VOICE_KEY = "voice_key";
    public static final String DOUBLE_TAP_VOWEL = "double_tap_vowel";
    public static final String DOUBLE_TAP_CONSONANT = "double_tap_consonant";       // 자음 두 번 탭 → 쌍자음
    public static final String DOUBLE_TAP_CONSONANT_MS = "double_tap_consonant_ms"; // 80~500
    public static final String SPACE_CURSOR = "space_cursor";
    public static final String CLIPBOARD_HISTORY = "clipboard_history";
    public static final String CLIPBOARD_IMAGES = "clipboard_images";
    public static final String LANGUAGE = "language";                 // "ko" | "en"
    public static final String PAD_LEFT = "pad_left";                 // dp, 0~24
    public static final String PAD_RIGHT = "pad_right";               // dp, 0~24
    public static final String PAD_TOP = "pad_top";                   // dp, 0~24
    public static final String KEY_GAP_X = "key_gap_x";               // 키 좌우 여백 dp, 0~12
    public static final String KEY_GAP_Y = "key_gap_y";               // 키 상하 여백 dp, 0~20
    public static final String PAD_BOTTOM = "pad_bottom";             // dp, 0~48
    public static final String PERIOD_COMMA = "period_comma";         // 쉼표 키 대신 온점 길게 누르기
    /** 한글 자판의 Shift 키를 없앤다 (켜짐 = Shift 키 없음, 그 자리는 빈칸). */
    public static final String KOREAN_SHIFT_HIDDEN = "korean_shift_hidden";
    public static final String KOREAN_SHIFT_FN = "korean_shift_fn";           // Shift 키를 껐을 때 그 자리에 Fn 키 놓기
    public static final String PERIOD_KEY_HIDDEN = "period_key_hidden";       // 글자·기호 자판 아래 줄의 온점 키 없애기
    /** 한글 자판 배열: "danmoeum8"(기본), "danmoeum7". 없앤 배열의 값이 남아 있으면 기본 배열로 본다. */
    public static final String KOREAN_LAYOUT = "korean_layout";
    public static final String KOREAN_LAYOUT_8 = "danmoeum8";       // 단모음 (8열)
    public static final String KOREAN_LAYOUT_D7 = "danmoeum7";      // NewSwipe 단모음 (7열)
    public static final String GRID_COLORS = "grid_colors";   // 색 정렬 (테마): Shift·⌫는 글자 키 색, 스페이스바는 기능키 색
    public static final String GRID_LAYOUT = "grid_layout";                   // 아래 줄 기능키를 한글 키 폭에 맞춰 격자로 정렬
    /** 자·모음 균형 레이아웃. 한글 배열마다 따로 저장한다: 이 이름 + "_" + 배열 값 (예: balanced_layout_danmoeum7). */
    public static final String BALANCED_LAYOUT = "balanced_layout";
    /** 가로 모드 분리 키보드: 가로 모드에서 자·모음 균형 레이아웃으로 자판을 나눈다. 한글 배열마다 따로 저장한다: 이 이름 + "_" + 배열 값. */
    public static final String LANDSCAPE_SPLIT = "landscape_split";
    public static final String LANDSCAPE_SIZE = "landscape_size";                  // 가로 모드 분리 키보드: 키보드 높이·글자 크기를 따로 정하기
    /** 실험실 '대화면 별도 레이아웃': 대화면(폴더블 내부 화면 등)에서는 가로·세로 키보드를 따로 정한 값으로 쓴다. */
    public static final String LARGE_LAYOUT = "large_layout";
    /** 대화면 기준: 화면의 짧은 쪽 폭(dp)이 이 값 이상이면 대화면. 400~1000. */
    public static final String LARGE_SCREEN_DP = "large_screen_dp";
    public static final int LARGE_SCREEN_DP_DEFAULT = 600, LARGE_SCREEN_DP_MIN = 400, LARGE_SCREEN_DP_MAX = 1000;
    /** 대화면 키보드의 값은 모두 이 말로 시작한다 (예: large_split_port_danmoeum7, large_balanced_vowel_width_land_danmoeum7). */
    private static final String LARGE_PREFIX = "large_";
    /**
     * 키보드 모습의 묶음(프로필). 세로 모드는 기본 값을, 가로 모드는 '가로 모드 키보드 사용자화'의 값을,
     * 대화면 세로·가로는 '대화면 키보드 사용자화'의 값을 쓴다.
     */
    public static final String PROFILE_PORTRAIT = "", PROFILE_LANDSCAPE = "land",
            PROFILE_LARGE_PORTRAIT = "large_port", PROFILE_LARGE_LANDSCAPE = "large_land";
    public static final String KEYBOARD_HEIGHT_LAND = "keyboard_height_land";      // 가로 모드의 키보드 높이 %, 70~140
    public static final String KEY_TEXT_SIZE_LAND = "key_text_size_land";          // 가로 모드의 키 글자 크기 %, 70~140
    public static final String BALANCED_VOWEL_WIDTH = "balanced_vowel_width";   // 모음 키 폭: 자음 키 폭의 %
    public static final String BALANCED_VOWEL_POS = "balanced_vowel_pos";       // 모음 3열 위치: 오른쪽 절반의 남는 폭에서 왼쪽 0 ~ 오른쪽 100
    public static final String BALANCED_CONSONANT_WIDTH = "balanced_consonant_width";   // 자음 키 폭: 기본 폭의 %
    public static final String BALANCED_CONSONANT_POS = "balanced_consonant_pos";       // 자음 열 위치: 왼쪽 절반의 남는 폭에서 왼쪽 0 ~ 오른쪽 100
    /** 가로 모드의 값은 위 이름 뒤에 이 말을 붙여 따로 저장한다 (예: balanced_vowel_width_land_danmoeum7). */
    private static final String LANDSCAPE_SUFFIX = "_land";
    public static final int BALANCED_WIDTH_MIN = 60, BALANCED_WIDTH_MAX = 165, BALANCED_WIDTH_DEFAULT = 100;
    public static final int BALANCED_CONSONANT_WIDTH_MAX = 100;
    /** 가로 모드는 키를 더 좁게 (30%까지) 줄일 수 있다. */
    public static final int BALANCED_WIDTH_MIN_LANDSCAPE = 30;
    public static final String TWO_FINGER_UNDO = "two_finger_undo";           // 두 손가락으로 밀어 실행 취소·다시 실행
    public static final String SWIPE_KEYBOARD_CUSTOM = "swipe_keyboard_custom"; // 키보드 밀기 완전 사용자화
    public static final String SPACE_LANG_SWIPE = "space_lang_swipe";         // 스페이스바를 좌우로 밀어 한/영 전환
    public static final String POPUP_HINT_HIDDEN = "popup_hint_hidden";       // 키 오른쪽 위의 길게 눌러 입력할 문자 힌트 없애기
    // 커서 이동 속도 (%, 100 = 기본). 스페이스바·문자 키, 좌우·상하 따로.
    public static final String SPACE_CURSOR_SPEED_H = "space_cursor_speed_h";
    public static final String SPACE_CURSOR_SPEED_V = "space_cursor_speed_v";
    public static final String CHAR_CURSOR_SPEED_H = "char_cursor_speed_h";
    public static final String CHAR_CURSOR_SPEED_V = "char_cursor_speed_v";
    public static final String CHAR_CURSOR = "char_cursor";                   // 문자·숫자 키를 밀어 커서 이동 (트랙패드)
    public static final String CHAR_CURSOR_H = "char_cursor_h";               // 그중 좌우 이동
    public static final String CHAR_CURSOR_V = "char_cursor_v";               // 그중 상하 이동
    public static final String DELETE_KEY_HIDDEN = "delete_key_hidden";       // 글자·기호 자판의 ⌫ 없애기 (빈칸으로 남김)
    public static final String SPACE_KEY_HIDDEN = "space_key_hidden";         // 스페이스바 없애기 (기능키 순서·유무 사용자화)
    public static final String BOTTOM_KEY_ORDER = "bottom_key_order";         // 맨 아래 줄 기능키 순서 (BottomKeys)
    public static final String ENTER_KEY_HIDDEN = "enter_key_hidden";         // 글자·기호 자판 아래 줄의 엔터 키 없애기
    public static final String MODE_KEY_HIDDEN = "mode_key_hidden";           // 글자 자판·기호 자판의 기호 키(?123·가·ABC) 없애기
    public static final String LANGUAGE_KEY_HIDDEN = "language_key_hidden";   // 글자 자판의 지구본(한/영 전환) 키 없애기
    public static final String KEY_RADIUS = "key_radius";   // 키 모서리 곡률 dp, 0~24 (기본 7)
    public static final String KEY_SHADOW = "key_shadow";
    public static final String KEY_SHADOW_STRENGTH = "key_shadow_strength";   // %, 10~100
    public static final String TOOLBAR = "toolbar";
    public static final String TOOLBAR_BOTTOM = "toolbar_bottom";   // 도구 막대를 자판 아래에 둔다
    public static final String TOOLBAR_HEIGHT = "toolbar_height";   // %, 70~150 (100 = 40dp)
    public static final String TOOL_BUTTON_SIZE = "tool_button_size";   // %, 70~150 (100 = 가로 40dp, 아이콘 22dp)
    public static final String TOOL_CLIPBOARD = "tool_clipboard";
    public static final String TOOL_EMOJI = "tool_emoji";
    public static final String TOOL_SETTINGS = "tool_settings";
    public static final String TOOL_HIDE = "tool_hide";
    public static final String TOOL_EMOJI_SWIPE = "tool_emoji_swipe";     // 이모지 버튼을 아래로 밀어 마지막으로 쓴 이모지 입력
    public static final String TOOL_CLIPBOARD_SWIPE = "tool_clipboard_swipe"; // 클립보드 버튼을 위로 밀어 복사, 아래로 밀어 붙여넣기
    public static final String TOOL_UNDO_SWIPE = "tool_undo_swipe";       // 실행 취소 버튼을 아래로 밀어 다시 실행
    public static final String TOOLBAR_SWIPE = "toolbar_swipe";   // 도구 막대를 밀어 줄 처음·끝, 한 줄 위·아래로 이동
    public static final String TOOL_ORDER = "tool_order";   // 도구 막대 버튼 순서와 붙는 쪽 (ToolbarButtons)
    public static final String TOOL_UNDO = "tool_undo";   // 도구 막대의 실행 취소 버튼
    public static final String TOOL_ONE_HAND = "tool_one_hand";   // 도구 막대의 한 손 모드 버튼

    /** 한 손 모드 상태: "off" | "left" | "right". 키보드에서 켜고 끄는 상태라 설정 내보내기에서는 뺀다. */
    public static final String ONE_HAND = "one_hand";
    public static final String ONE_HAND_OFF = "off", ONE_HAND_LEFT = "left", ONE_HAND_RIGHT = "right";
    /** 한 손 모드 버튼을 눌러 켤 때 쓰는 쪽 (마지막으로 쓴 쪽). */
    public static final String ONE_HAND_LAST = "one_hand_last";
    public static final String ONE_HAND_WIDTH = "one_hand_width";     // 자판 폭: 화면 폭의 %, 60~90
    public static final String ONE_HAND_HEIGHT = "one_hand_height";   // 키 높이: 보통 키 높이의 %, 60~100
    public static final String ONE_HAND_LIFT = "one_hand_lift";       // 아래에서 띄우는 높이(dp), 0~160
    public static final String ONE_HAND_WIDTH_LAND = "one_hand_width_land";   // 가로 모드의 자판 폭: 화면 폭의 %, 30~90
    public static final int ONE_HAND_WIDTH_LAND_DEFAULT = 50;
    public static final int ONE_HAND_WIDTH_DEFAULT = 80, ONE_HAND_HEIGHT_DEFAULT = 90, ONE_HAND_LIFT_DEFAULT = 0;
    public static final String THEME_MODE = "theme_mode";
    public static final String ACCENT_MODE = "accent_mode";
    public static final String ACCENT_COLOR = "accent_color";
    public static final String THEME_SYSTEM = "system", THEME_LIGHT = "light", THEME_DARK = "dark";
    public static final String ACCENT_SYSTEM = "system", ACCENT_CUSTOM = "custom";
    public static final String SPACE_CURSOR_H = "space_cursor_h";   // 스페이스바 좌우 이동
    public static final String SPACE_CURSOR_V = "space_cursor_v";   // 스페이스바 상하 이동
    public static final String LONG_PRESS_DELETE = "long_press_delete";   // 지우기 키를 길게 눌러 연속 삭제
    public static final String SWIPE_FN_CUSTOM = "swipe_fn_custom";       // 기능키 밀어서 기능 완전 사용자화
    public static final String SWIPE_TOOLBAR_CUSTOM = "swipe_toolbar_custom"; // 도구 막대 밀어서 기능 완전 사용자화
    public static final String MODE_KEY_EMOJI = "mode_key_emoji";         // 왼쪽 아래 기호 키를 위로 밀어 이모지 열기
    public static final String MODE_KEY_LONG_PRESS = "mode_key_settings";  // 왼쪽 아래 기호 키를 길게 눌러 이모지 열기 (예전에는 설정 열기라 키 이름이 그대로)
    public static final String LONG_PRESS_CUSTOM = "long_press_custom";   // 기능키 길게 누르기 기능 완전 사용자화
    public static final String DELETE_PRESS_MS = "delete_press_ms";       // 기능키 길게 누르기 시간 200~800 (옛 이름 그대로 저장)
    public static final String DELETE_WORD_SWIPE = "delete_word_swipe";   // 지우기 키를 왼쪽으로 밀어 단어 삭제
    public static final String SWIPE_DOUBLE = "swipe_double";             // 아래로 밀어 쌍자음
    public static final String D7_SS_UP = "d7_ss_up";                     // NewSwipe 단모음: ㅅ을 위로 밀어도 ㅆ
    /** 실험실 '스와이프 방향 판정': 세로 움직임이 가로 움직임의 몇 %를 넘어야 아래·위로 치는지 (100~300). */
    public static final String SWIPE_DOWN_RATIO = "swipe_down_ratio", SWIPE_UP_RATIO = "swipe_up_ratio";
    public static final int SWIPE_DOWN_RATIO_DEFAULT = 150, SWIPE_UP_RATIO_DEFAULT = 120,
            SWIPE_RATIO_MIN = 100, SWIPE_RATIO_MAX = 300;
    public static final String DELETE_HIT_SHRINK = "delete_hit_shrink";           // ⌫의 위·왼쪽 터치 인식 범위 좁히기
    public static final String DELETE_HIT_SHRINK_PCT = "delete_hit_shrink_pct";   // %, 5~50
    public static final String SPACE_HIT_SHRINK = "space_hit_shrink";             // 스페이스바의 위쪽 터치 인식 범위 좁히기
    public static final String SPACE_HIT_SHRINK_PCT = "space_hit_shrink_pct";     // %, 5~50
    public static final String SETUP_DONE = "setup_done";   // 시작하기를 한 번 마쳤는지 (그전까지 설정 메뉴를 막는다)
    public static final String ONBOARDING_DONE = "onboarding_done";   // 첫 시작 가이드를 끝까지 봤는지
    public static final String SWIPE_IOTIZED = "swipe_iotized";           // 모음 키를 밀어 ㅣ계 이중모음 (ㅑ ㅕ ㅛ ㅠ ㅒ ㅖ)
    public static final String SWIPE_COMPOUND_VOWEL = "swipe_compound_vowel"; // 모음 키를 밀어 조합형 이중모음 (ㅘ ㅙ ㅚ ㅝ ㅞ ㅟ ㅢ)
    public static final String SWIPE_FINAL = "swipe_final";               // 밀어서 겹받침
    public static final String PERIOD_SWIPE_COMMA = "period_swipe_comma"; // 온점 키를 위로 밀어 쉼표
    public static final String SWIPE_CUSTOM = "swipe_custom";             // 스와이프 입력 완전 사용자화
    public static final String SUGGEST_WORDS = "suggest_words";           // 입력 중인 단어에 맞는 추천 단어
    public static final String SUGGEST_FULL_BAR = "suggest_full_bar";     // 추천이 뜨는 동안 도구 막대 전체를 추천에 쓴다
    public static final String AUTO_CORRECT = "auto_correct";             // 스페이스를 누를 때 오타를 고침
    public static final String SUGGEST_SPACE = "suggest_space";           // 추천 단어를 누르면 뒤에 공백을 붙인다 (한글·영어 모두)
    public static final String LEARN_WORDS = "learn_words";               // 입력한 단어를 기억해 추천에 쓴다
    public static final String CONFIRM_LEARNED_DELETE = "confirm_learned_delete";   // 학습한 단어를 지울 때 묻기
    public static final String RECENT_EMOJI = "recent_emoji";
    public static final String PINNED_EMOJI = "pinned_emoji";   // 최근 이모지 맨 앞에 고정한 이모지

    private final SharedPreferences sp;
    /** 한 손 모드용 보기: 자·모음 균형 레이아웃을 끈 것으로 본다 (가운데 빈틈 없이 한쪽으로 모은다). */
    private final boolean oneHandView;
    /** 이 보기가 읽는 프로필 (PROFILE_*). 세로 모드가 아니면 그 프로필의 분리 키보드·키 폭·위치·크기 값을 읽는다. */
    private final String profile;
    /** 설정 화면에서 프로필 값을 고칠 때 쓰는 보기: 그 프로필의 분리 키보드가 켜진 것으로 본다. */
    private final boolean profileEdit;

    public Prefs(Context context) {
        this(new SafePrefs(context.getSharedPreferences(FILE, Context.MODE_PRIVATE)), false, PROFILE_PORTRAIT, false);
    }

    private Prefs(SharedPreferences sp, boolean oneHandView, String profile, boolean profileEdit) {
        this.sp = sp;
        this.oneHandView = oneHandView;
        this.profile = profile;
        this.profileEdit = profileEdit;
    }

    /** 이 프로필(PROFILE_*)의 키 폭·위치·크기를 읽는 설정 보기. 값은 같다. */
    public Prefs profileView(String profile) {
        return this.profile.equals(profile) && !profileEdit ? this : new Prefs(sp, oneHandView, profile, false);
    }

    /** 설정 화면에서 이 프로필의 값(분리 키보드의 키 폭·위치, 키보드 높이·글자 크기)을 고치고 미리보는 설정 보기. */
    public Prefs profileEditView(String profile) {
        return profileEdit && this.profile.equals(profile) ? this : new Prefs(sp, false, profile, true);
    }

    /** 이 보기의 프로필. */
    public String profile() { return profile; }

    /**
     * 한 손 모드의 자판을 만들 때 쓰는 설정 보기. 값은 같고, 자·모음 균형 레이아웃만 꺼진 것으로 본다
     * (설정 화면의 균형 레이아웃 스위치는 그대로다).
     */
    public Prefs oneHandView() {
        return oneHandView ? this : new Prefs(sp, true, profile, profileEdit);
    }

    /** 한 손 모드 상태 (ONE_HAND_OFF·LEFT·RIGHT). */
    public String oneHand() {
        String v = sp.getString(ONE_HAND, ONE_HAND_OFF);
        return ONE_HAND_LEFT.equals(v) || ONE_HAND_RIGHT.equals(v) ? v : ONE_HAND_OFF;
    }

    public boolean oneHandOn() { return !ONE_HAND_OFF.equals(oneHand()); }

    /** 버튼으로 켤 때 쓰는 쪽. 처음에는 오른쪽. */
    public String oneHandLast() {
        return ONE_HAND_LEFT.equals(sp.getString(ONE_HAND_LAST, ONE_HAND_RIGHT)) ? ONE_HAND_LEFT : ONE_HAND_RIGHT;
    }

    /** 한 손 모드를 켜거나(left·right) 끈다(off). 켜면 그 쪽을 다음에 버튼으로 켤 쪽으로 기억한다. */
    public void setOneHand(String side) {
        SharedPreferences.Editor e = sp.edit().putString(ONE_HAND, side);
        if (!ONE_HAND_OFF.equals(side)) e.putString(ONE_HAND_LAST, side);
        e.apply();
    }

    public int oneHandWidth() { return clamp(sp.getInt(ONE_HAND_WIDTH, ONE_HAND_WIDTH_DEFAULT), 60, 90); }
    /** 이 프로필(대화면 세로·가로)의 한 손 모드 자판 폭을 저장하는 키. 대화면이 아닌 프로필에는 없다(null). */
    public String oneHandWidthKey() {
        return large() ? LARGE_PREFIX + ONE_HAND_WIDTH + "_" + largeSide() : null;
    }

    /**
     * 이 보기의 프로필에서 쓸 한 손 모드 자판 폭 (화면 폭의 %, 30~90).
     * 대화면 세로·가로는 따로 정한 값을, 정하기 전에는 대화면이 아닌 세로·가로 모드의 값을 쓴다.
     */
    public int oneHandWidthForProfile() {
        int fallback = profile.equals(PROFILE_LARGE_LANDSCAPE) || profile.equals(PROFILE_LANDSCAPE)
                ? oneHandWidthLand() : oneHandWidth();
        String key = oneHandWidthKey();
        return key == null ? fallback : clamp(sp.getInt(key, fallback), 30, 90);
    }

    /** 화면이 가로 방향일 때 쓰는 자판 폭 (화면 폭의 %). */
    public int oneHandWidthLand() { return clamp(sp.getInt(ONE_HAND_WIDTH_LAND, ONE_HAND_WIDTH_LAND_DEFAULT), 30, 90); }
    public int oneHandHeight() { return clamp(sp.getInt(ONE_HAND_HEIGHT, ONE_HAND_HEIGHT_DEFAULT), 60, 100); }
    public int oneHandLiftDp() { return clamp(sp.getInt(ONE_HAND_LIFT, ONE_HAND_LIFT_DEFAULT), 0, 160); }

    /** 글자 키를 밀 때 아래로 치는 데 필요한 (세로 ÷ 가로) 비율. */
    public float swipeDownRatio() {
        return clamp(sp.getInt(SWIPE_DOWN_RATIO, SWIPE_DOWN_RATIO_DEFAULT), SWIPE_RATIO_MIN, SWIPE_RATIO_MAX) / 100f;
    }
    /** 위로 치는 데 필요한 같은 비율. */
    public float swipeUpRatio() {
        return clamp(sp.getInt(SWIPE_UP_RATIO, SWIPE_UP_RATIO_DEFAULT), SWIPE_RATIO_MIN, SWIPE_RATIO_MAX) / 100f;
    }
    public int swipeDownRatioPct() { return Math.round(swipeDownRatio() * 100); }
    public int swipeUpRatioPct() { return Math.round(swipeUpRatio() * 100); }

    /** 실험실 '대화면 별도 레이아웃' (기본 꺼짐). */
    public boolean largeLayout() { return sp.getBoolean(LARGE_LAYOUT, false); }
    public int largeScreenDp() {
        return clamp(sp.getInt(LARGE_SCREEN_DP, LARGE_SCREEN_DP_DEFAULT), LARGE_SCREEN_DP_MIN, LARGE_SCREEN_DP_MAX);
    }

    /**
     * 지금 화면에서 쓸 프로필. '대화면 별도 레이아웃'이 켜져 있고 화면의 짧은 쪽 폭이 기준 이상이면 대화면 세로·가로,
     * 아니면 세로·가로 모드. 짧은 쪽 폭으로 재므로 같은 화면을 돌려도 대화면인지는 바뀌지 않는다.
     */
    public String profileFor(Context c) {
        android.content.res.Configuration cfg = c.getResources().getConfiguration();
        boolean land = cfg.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE;
        if (largeLayout() && cfg.smallestScreenWidthDp >= largeScreenDp()) {
            return land ? PROFILE_LARGE_LANDSCAPE : PROFILE_LARGE_PORTRAIT;
        }
        return land ? PROFILE_LANDSCAPE : PROFILE_PORTRAIT;
    }

    private static int clamp(int v, int min, int max) {
        return Math.max(min, Math.min(max, v));
    }

    // ---------------------------------------------------------------- 메뉴별 기본값으로 되돌리기

    private static String[] keys(String... k) { return k; }

    /** 메뉴(SettingsActivity의 하위 메뉴 id)별로 그 메뉴가 저장하는 값의 키. */
    private static final java.util.Map<String, String[]> MENU_KEYS = new java.util.HashMap<>();
    /** 메뉴별로 키 앞부분이 같은 값들 (배열·방향·칸마다 따로 저장하는 값). */
    private static final java.util.Map<String, String[]> MENU_PREFIXES = new java.util.HashMap<>();
    static {
        MENU_KEYS.put("layout", keys(KOREAN_LAYOUT, GRID_LAYOUT, NUMBER_ROW, LANDSCAPE_SPLIT, LANDSCAPE_SIZE,
                KEYBOARD_HEIGHT_LAND, KEY_TEXT_SIZE_LAND,
                BOTTOM_KEY_ORDER, MODE_KEY_HIDDEN, PERIOD_COMMA, LANGUAGE_KEY_HIDDEN, SPACE_KEY_HIDDEN,
                PERIOD_KEY_HIDDEN, ENTER_KEY_HIDDEN, DELETE_KEY_HIDDEN, KOREAN_SHIFT_HIDDEN, KOREAN_SHIFT_FN));
        MENU_PREFIXES.put("layout", keys("balanced_", LANDSCAPE_SPLIT + "_", "swipe_fk_"));
        MENU_KEYS.put("theme", keys(THEME_MODE, ACCENT_MODE, ACCENT_COLOR, GRID_COLORS));
        MENU_KEYS.put("look", keys(KEYBOARD_HEIGHT, KEY_TEXT_SIZE, KEY_RADIUS, KEY_SHADOW, KEY_SHADOW_STRENGTH,
                KEY_PREVIEW, POPUP_HINT_HIDDEN, PAD_LEFT, PAD_RIGHT, PAD_TOP, PAD_BOTTOM, KEY_GAP_X, KEY_GAP_Y));
        MENU_KEYS.put("input", keys(AUTO_CAP, DOUBLE_SPACE_PERIOD, DOUBLE_TAP_VOWEL, DOUBLE_TAP_CONSONANT,
                DOUBLE_TAP_CONSONANT_MS, DELETE_HIT_SHRINK, DELETE_HIT_SHRINK_PCT, SPACE_HIT_SHRINK,
                SPACE_HIT_SHRINK_PCT));
        MENU_KEYS.put("keys", keys(LONG_PRESS_CHARS, LONG_PRESS_MS, LONG_PRESS_DELETE, DELETE_PRESS_MS,
                MODE_KEY_LONG_PRESS, LONG_PRESS_CUSTOM));
        MENU_PREFIXES.put("keys", keys("popup_", "swipe_lp_"));
        MENU_KEYS.put("swipe", keys(SWIPE_THRESHOLD, SWIPE_DOUBLE, D7_SS_UP, SWIPE_IOTIZED, SWIPE_COMPOUND_VOWEL,
                SWIPE_FINAL, PERIOD_SWIPE_COMMA, SWIPE_CUSTOM));
        MENU_PREFIXES.put("swipe", keys("swipe_custom_", "swipe_final_", "swipe_vowel_"));
        MENU_KEYS.put("swipefn", keys(FN_SWIPE_THRESHOLD, SPACE_CURSOR, SPACE_CURSOR_H, SPACE_CURSOR_V,
                SPACE_CURSOR_SPEED_H, SPACE_CURSOR_SPEED_V, CHAR_CURSOR, CHAR_CURSOR_H, CHAR_CURSOR_V,
                CHAR_CURSOR_SPEED_H, CHAR_CURSOR_SPEED_V, SPACE_LANG_SWIPE, TWO_FINGER_UNDO, SWIPE_KEYBOARD_CUSTOM,
                SWIPE_FN_CUSTOM, SWIPE_TOOLBAR_CUSTOM, DELETE_WORD_SWIPE, MODE_KEY_EMOJI, TOOLBAR_SWIPE,
                TOOL_EMOJI_SWIPE, TOOL_CLIPBOARD_SWIPE, TOOL_UNDO_SWIPE));
        MENU_PREFIXES.put("swipefn", keys("swipe_fn_", "swipe_toolbar_", "swipe_kb_"));
        MENU_KEYS.put("feedback", keys(VIBRATE, VIBRATE_MS, SOUND, SOUND_VOLUME));
        MENU_KEYS.put("tools", keys(TOOLBAR, TOOLBAR_BOTTOM, TOOLBAR_HEIGHT, TOOL_BUTTON_SIZE, CLIPBOARD_HISTORY, CLIPBOARD_IMAGES, TOOL_ORDER, TOOL_CLIPBOARD,
                TOOL_EMOJI, VOICE_KEY, TOOL_UNDO, TOOL_SETTINGS, TOOL_ONE_HAND, TOOL_HIDE));
        MENU_KEYS.put("words", keys(SUGGEST_WORDS, SUGGEST_FULL_BAR, SUGGEST_SPACE, AUTO_CORRECT, LEARN_WORDS,
                CONFIRM_LEARNED_DELETE));
        // 실험실: 대화면 별도 레이아웃과 대화면 키보드 사용자화의 값은 모두 large_로 시작한다.
        MENU_KEYS.put("lab", keys(SWIPE_DOWN_RATIO, SWIPE_UP_RATIO));
        MENU_PREFIXES.put("lab", keys(LARGE_PREFIX));
        MENU_KEYS.put("onehand", keys(ONE_HAND_WIDTH, ONE_HAND_WIDTH_LAND, ONE_HAND_HEIGHT, ONE_HAND_LIFT));
    }

    /** 이 메뉴가 저장한 모든 값을 지워 기본값으로 되돌린다 (학습한 단어·클립보드 기록 같은 사용 기록과 한 손 모드의 켜짐 상태는 그대로). */
    public void resetSection(String menu) {
        java.util.Set<String> exact = new java.util.HashSet<>(java.util.Arrays.asList(
                MENU_KEYS.containsKey(menu) ? MENU_KEYS.get(menu) : new String[0]));
        String[] prefixes = MENU_PREFIXES.containsKey(menu) ? MENU_PREFIXES.get(menu) : new String[0];
        SharedPreferences.Editor e = sp.edit();
        for (String key : sp.getAll().keySet()) {
            boolean hit = exact.contains(key);
            for (String p : prefixes) if (key.startsWith(p)) hit = true;
            if (hit) e.remove(key);
        }
        e.apply();
    }

    // ---------------------------------------------------------------- 하위 화면별 기본값으로 되돌리기

    private void removeKeys(String[] exact, String[] prefixes, String... except) {
        java.util.Set<String> keep = new java.util.HashSet<>(java.util.Arrays.asList(except));
        java.util.Set<String> drop = new java.util.HashSet<>(java.util.Arrays.asList(exact));
        SharedPreferences.Editor e = sp.edit();
        for (String key : sp.getAll().keySet()) {
            boolean hit = drop.contains(key);
            for (String p : prefixes) if (key.startsWith(p)) hit = true;
            if (hit && !keep.contains(key)) e.remove(key);
        }
        e.apply();
    }

    private static final String[] NONE = {};

    /** 이 보기(세로 또는 가로)에서 지금 한글 배열의 자음·모음 키 폭·가로 위치를 기본값으로 되돌린다. */
    public void resetBalancedKeys() {
        java.util.List<String> keys = new java.util.ArrayList<>(java.util.Arrays.asList(balancedVowelWidthKey(),
                balancedVowelPosKey(), balancedConsonantWidthKey(), balancedConsonantPosKey()));
        // 배열별로 나누기 전의 값(세로 모드의 8열 단모음)이 이어지지 않게 그 값도 지운다.
        if (!profileValues() && !koreanNewSwipe()) {
            keys.addAll(java.util.Arrays.asList(BALANCED_VOWEL_WIDTH, BALANCED_VOWEL_POS, BALANCED_CONSONANT_WIDTH,
                    BALANCED_CONSONANT_POS));
        }
        removeKeys(keys.toArray(new String[0]), NONE);
    }

    /** 프로필 사용자화 화면(가로 모드·대화면)의 이 프로필 값. 그 프로필의 편집 보기(profileEditView)에서 부른다. */
    public void resetProfileArea() {
        resetBalancedKeys();
        removeKeys(new String[]{splitKey(), sizeKey(), heightKey(), textSizeKey(), oneHandWidthKey()},
                new String[]{splitKey() + "_"});   // 예전에 배열마다 저장하던 분리 키보드 값도 함께
    }

    /** '기능키 순서·유무 사용자화' 화면의 값. */
    public void resetBottomKeys() {
        removeKeys(new String[]{BOTTOM_KEY_ORDER, MODE_KEY_HIDDEN, PERIOD_COMMA, LANGUAGE_KEY_HIDDEN,
                SPACE_KEY_HIDDEN, PERIOD_KEY_HIDDEN, ENTER_KEY_HIDDEN, DELETE_KEY_HIDDEN, KOREAN_SHIFT_HIDDEN,
                KOREAN_SHIFT_FN}, NONE);
    }

    /** '도구 막대 버튼 순서·유무 사용자화' 화면의 값. */
    public void resetToolButtons() {
        removeKeys(new String[]{TOOL_ORDER, TOOL_CLIPBOARD, TOOL_EMOJI, VOICE_KEY, TOOL_UNDO, TOOL_SETTINGS,
                TOOL_ONE_HAND, TOOL_HIDE}, NONE);
    }

    /** '길게 누르기 문자 편집' 화면의 값. */
    public void resetPopups() { removeKeys(NONE, new String[]{"popup_"}); }

    /** 기능 편집 화면(기능키·도구 막대·키보드 밀기·길게 누르기·Fn 키)의 값. 같은 이름으로 시작하는 '완전 사용자화' 스위치는 그대로 둔다. */
    public void resetSwipeActions(String group) {
        removeKeys(NONE, new String[]{"swipe_" + group + "_"}, SWIPE_FN_CUSTOM, SWIPE_TOOLBAR_CUSTOM);
    }

    /** '밀어서 글자 입력 편집' 화면의 값. */
    public void resetSwipeCustoms() { removeKeys(NONE, new String[]{"swipe_custom_"}); }

    /** '겹받침 밀어서 글자 입력 편집' 화면의 값. */
    public void resetFinalSwipes() { removeKeys(NONE, new String[]{"swipe_final_"}); }

    /** '밀어서 조합형 이중모음 입력 편집' 화면의 값. */
    public void resetVowelSwipes() { removeKeys(NONE, new String[]{"swipe_vowel_"}); }

    public SharedPreferences raw() {
        return sp;
    }

    /** 키보드 높이 (%). 가로 모드·대화면 보기에서 따로 정했으면 그 값 (정하기 전에는 세로 모드 값). */
    public int keyboardHeight() {
        int v = sp.getInt(KEYBOARD_HEIGHT, 100);
        return profileSize() ? clamp(sp.getInt(heightKey(), v), 70, 140) : v;
    }
    /** 키 글자 크기 (%). 가로 모드·대화면 보기에서 따로 정했으면 그 값 (정하기 전에는 세로 모드 값). */
    public int keyTextSize() {
        int v = sp.getInt(KEY_TEXT_SIZE, 100);
        return profileSize() ? clamp(sp.getInt(textSizeKey(), v), 70, 140) : v;
    }
    public int longPressMs() { return sp.getInt(LONG_PRESS_MS, 340); }   // 설정 단위(20ms)에 맞춘 기본값
    public int swipeThresholdDp() { return sp.getInt(SWIPE_THRESHOLD, 22); }
    /** '밀어서 기능'의 미는 거리. 따로 정한 적이 없으면 (나누기 전처럼) 글자 입력의 미는 거리를 따른다. */
    public int fnSwipeThresholdDp() { return sp.getInt(FN_SWIPE_THRESHOLD, swipeThresholdDp()); }
    public boolean vibrate() { return sp.getBoolean(VIBRATE, true); }
    public int vibrateMs() { return sp.getInt(VIBRATE_MS, 12); }
    public boolean sound() { return sp.getBoolean(SOUND, false); }
    public int soundVolume() { return sp.getInt(SOUND_VOLUME, 50); }
    public boolean keyPreview() { return sp.getBoolean(KEY_PREVIEW, true); }
    public boolean numberRow() { return sp.getBoolean(NUMBER_ROW, false); }
    public boolean longPressChars() { return sp.getBoolean(LONG_PRESS_CHARS, true); }
    public boolean autoCap() { return sp.getBoolean(AUTO_CAP, true); }
    public boolean doubleSpacePeriod() { return sp.getBoolean(DOUBLE_SPACE_PERIOD, true); }
    public boolean voiceKey() { return sp.getBoolean(VOICE_KEY, true); }
    public boolean doubleTapVowel() { return sp.getBoolean(DOUBLE_TAP_VOWEL, true); }
    /** 기본은 꺼짐: 쌍자음은 밀어서 입력하므로 '학교→하꾜' 문제가 없다. 켜면 예전 단모음처럼 두 번 탭으로도 입력. */
    public boolean doubleTapConsonant() { return sp.getBoolean(DOUBLE_TAP_CONSONANT, false); }
    public int doubleTapConsonantMs() { return sp.getInt(DOUBLE_TAP_CONSONANT_MS, 200); }
    public boolean spaceCursorH() { return sp.getBoolean(SPACE_CURSOR_H, true); }
    public boolean spaceCursorV() { return sp.getBoolean(SPACE_CURSOR_V, true); }
    public boolean spaceCursor() { return sp.getBoolean(SPACE_CURSOR, true); }
    public boolean clipboardHistory() { return sp.getBoolean(CLIPBOARD_HISTORY, true); }
    public boolean clipboardImages() { return sp.getBoolean(CLIPBOARD_IMAGES, true); }
    public int keyGapXDp() { return clamp(sp.getInt(KEY_GAP_X, 5), 0, 12); }
    public int keyGapYDp() { return clamp(sp.getInt(KEY_GAP_Y, 9), 0, 20); }
    public int padLeftDp() { return sp.getInt(PAD_LEFT, 3); }
    public int padRightDp() { return sp.getInt(PAD_RIGHT, 3); }
    public int padTopDp() { return sp.getInt(PAD_TOP, 0); }
    public int padBottomDp() { return sp.getInt(PAD_BOTTOM, 4); }
    public boolean periodComma() { return sp.getBoolean(PERIOD_COMMA, true); }
    public boolean periodKeyHidden() { return sp.getBoolean(PERIOD_KEY_HIDDEN, false); }
    public boolean twoFingerUndo() { return sp.getBoolean(TWO_FINGER_UNDO, false); }
    public boolean swipeKeyboardCustom() { return sp.getBoolean(SWIPE_KEYBOARD_CUSTOM, false); }

    public boolean spaceLangSwipe() { return sp.getBoolean(SPACE_LANG_SWIPE, false); }
    public boolean popupHintHidden() { return sp.getBoolean(POPUP_HINT_HIDDEN, false); }
    public boolean deleteHitShrink() { return sp.getBoolean(DELETE_HIT_SHRINK, false); }
    public int deleteHitShrinkPct() { return sp.getInt(DELETE_HIT_SHRINK_PCT, 10); }
    public boolean spaceHitShrink() { return sp.getBoolean(SPACE_HIT_SHRINK, false); }
    public int spaceHitShrinkPct() { return sp.getInt(SPACE_HIT_SHRINK_PCT, 10); }
    public boolean setupDone() { return sp.getBoolean(SETUP_DONE, false); }
    /**
     * 첫 시작 가이드를 마쳤는지. 가이드가 생기기 전부터 쓰던 사람(값이 없고 시작하기를 이미 마침)은 가이드를 띄우지 않는다.
     * 가이드는 시작할 때 이 값을 false로 적어 두므로, 가이드 도중 키보드를 고른 뒤 앱을 닫아도 다음에 다시 이어 본다.
     */
    public boolean onboardingDone() {
        return sp.contains(ONBOARDING_DONE) ? sp.getBoolean(ONBOARDING_DONE, false) : setupDone();
    }
    public int spaceCursorSpeedH() { return sp.getInt(SPACE_CURSOR_SPEED_H, 100); }
    public int spaceCursorSpeedV() { return sp.getInt(SPACE_CURSOR_SPEED_V, 100); }
    public int charCursorSpeedH() { return sp.getInt(CHAR_CURSOR_SPEED_H, 100); }
    public int charCursorSpeedV() { return sp.getInt(CHAR_CURSOR_SPEED_V, 100); }
    public boolean charCursor() { return sp.getBoolean(CHAR_CURSOR, false); }
    public boolean charCursorH() { return sp.getBoolean(CHAR_CURSOR_H, true); }
    public boolean charCursorV() { return sp.getBoolean(CHAR_CURSOR_V, true); }
    public boolean deleteKeyHidden() { return sp.getBoolean(DELETE_KEY_HIDDEN, false); }
    public boolean spaceKeyHidden() { return sp.getBoolean(SPACE_KEY_HIDDEN, false); }

    /** 하단 키 순서. 저장된 적이 없거나 일부가 빠졌으면 기본 순서로 채운다. */
    public String[] bottomKeyOrder() {
        return com.alternative_studios.newswipe.keyboard.BottomKeys.order(sp.getString(BOTTOM_KEY_ORDER, null));
    }

    public void setBottomKeyOrder(String[] order) {
        sp.edit().putString(BOTTOM_KEY_ORDER, com.alternative_studios.newswipe.keyboard.BottomKeys.join(order)).apply();
    }

    /**
     * 맨 아래 줄 기능키를 보이는지. 키마다 따로 저장한 '없애기' 설정을 쓴다.
     * 쉼표는 '쉼표 키 대신 온점 길게 누르기'(PERIOD_COMMA)가 꺼져 있을 때 보인다.
     */
    public boolean bottomKeyShown(String id) {
        switch (id) {
            case com.alternative_studios.newswipe.keyboard.BottomKeys.MODE: return !modeKeyHidden();
            case com.alternative_studios.newswipe.keyboard.BottomKeys.COMMA: return !periodComma();
            case com.alternative_studios.newswipe.keyboard.BottomKeys.GLOBE: return !languageKeyHidden();
            case com.alternative_studios.newswipe.keyboard.BottomKeys.SPACE: return !spaceKeyHidden();
            case com.alternative_studios.newswipe.keyboard.BottomKeys.PERIOD: return !periodKeyHidden();
            case com.alternative_studios.newswipe.keyboard.BottomKeys.ENTER: return !enterKeyHidden();
            default: return false;
        }
    }

    public void setBottomKeyShown(String id, boolean shown) {
        String key;
        boolean value = !shown;
        switch (id) {
            case com.alternative_studios.newswipe.keyboard.BottomKeys.MODE: key = MODE_KEY_HIDDEN; break;
            case com.alternative_studios.newswipe.keyboard.BottomKeys.COMMA: key = PERIOD_COMMA; break;
            case com.alternative_studios.newswipe.keyboard.BottomKeys.GLOBE: key = LANGUAGE_KEY_HIDDEN; break;
            case com.alternative_studios.newswipe.keyboard.BottomKeys.SPACE: key = SPACE_KEY_HIDDEN; break;
            case com.alternative_studios.newswipe.keyboard.BottomKeys.PERIOD: key = PERIOD_KEY_HIDDEN; break;
            case com.alternative_studios.newswipe.keyboard.BottomKeys.ENTER: key = ENTER_KEY_HIDDEN; break;
            default: return;
        }
        sp.edit().putBoolean(key, value).apply();
    }

    /** 격자 정렬. 자·모음 균형 레이아웃에서는 기능키 폭을 나뉜 자음 키 폭에 맞춘다. */
    public boolean gridLayout() { return sp.getBoolean(GRID_LAYOUT, false); }
    /** 지금 한글 배열의 저장값 ("danmoeum8", "danmoeum7"). */
    public String koreanLayoutId() {
        return koreanD7() ? KOREAN_LAYOUT_D7 : KOREAN_LAYOUT_8;
    }

    /** 지금 한글 배열의 자·모음 균형 레이아웃 설정 키. */
    public String balancedLayoutKey() { return BALANCED_LAYOUT + "_" + koreanLayoutId(); }

    /**
     * 지금 한글 배열에서 자·모음 균형 레이아웃을 쓰는지. 배열마다 따로 기억한다.
     * 배열별로 나누기 전의 설정(이름 뒤에 배열이 없는 값)은 8열 단모음의 값으로 이어 쓴다.
     */
    public boolean balancedLayout() {
        if (oneHandView) return false;
        if (profileValues()) return true;   // 가로 모드·대화면 분리 키보드는 세로 모드의 균형 레이아웃과 상관없이 나눈다
        String key = balancedLayoutKey();
        if (sp.contains(key)) return sp.getBoolean(key, false);
        return !koreanNewSwipe() && sp.getBoolean(BALANCED_LAYOUT, false);
    }
    private boolean large() { return profile.startsWith(LARGE_PREFIX); }

    /** 대화면 프로필의 방향 이름 ("port", "land"). */
    private String largeSide() { return PROFILE_LARGE_PORTRAIT.equals(profile) ? "port" : "land"; }

    /**
     * 이 프로필의 '분리 키보드' 켜짐 설정 키. 어떤 한글 배열을 골랐든 같은 값이다.
     * (예전에는 배열마다 따로 저장했다: 이 키 + "_" + 배열. 새 값이 없으면 그 값을 이어 쓴다.)
     */
    public String splitKey() {
        return large() ? LARGE_PREFIX + "split_" + largeSide() : LANDSCAPE_SPLIT;
    }

    /** 예전에 배열마다 따로 저장하던 '분리 키보드' 설정 키. */
    private String legacySplitKey() { return splitKey() + "_" + koreanLayoutId(); }

    /** 이 프로필의 '키보드 높이·글자 크기 따로 정하기' 설정 키와 그 값의 키. */
    public String sizeKey() { return large() ? LARGE_PREFIX + "size_" + largeSide() : LANDSCAPE_SIZE; }
    public String heightKey() { return large() ? LARGE_PREFIX + KEYBOARD_HEIGHT + "_" + largeSide() : KEYBOARD_HEIGHT_LAND; }
    public String textSizeKey() { return large() ? LARGE_PREFIX + KEY_TEXT_SIZE + "_" + largeSide() : KEY_TEXT_SIZE_LAND; }

    /** 이 프로필에서 자판을 자음(왼쪽)·모음(오른쪽)으로 나누는지. 한글 배열과 상관없이 켜고 끈 상태가 유지된다. 세로 모드는 늘 false. */
    public boolean profileSplit() {
        if (profile.isEmpty()) return false;
        return sp.contains(splitKey()) ? sp.getBoolean(splitKey(), false) : sp.getBoolean(legacySplitKey(), false);
    }

    /** 이 프로필의 '키보드 높이·글자 크기 따로 정하기'. 세로 모드는 늘 false. */
    public boolean profileSize() { return !profile.isEmpty() && sp.getBoolean(sizeKey(), false); }

    /** 분리 키보드를 쓰는 보기인지: 숫자 줄과 영어 자판도 왼쪽·오른쪽으로 나눈다. */
    public boolean splitView() { return profileValues(); }

    /** 이 보기에서 분리 키보드가 켜져 있어 그 프로필의 키 폭·위치를 쓰는지 (한 손 모드에서는 쓰지 않아 한쪽으로 빈틈 없이 모인다). */
    private boolean profileValues() { return !profile.isEmpty() && !oneHandView && (profileEdit || profileSplit()); }

    /** 지금 한글 배열(과 프로필)의 키 폭·위치 설정 키. 세로 모드는 예전 이름을, 가로 모드는 _land를 붙인 이름을 그대로 쓴다. */
    private String balancedKey(String base) {
        if (!profileValues()) return base + "_" + koreanLayoutId();
        return large() ? LARGE_PREFIX + base + "_" + largeSide() + "_" + koreanLayoutId()
                : base + LANDSCAPE_SUFFIX + "_" + koreanLayoutId();
    }
    public String balancedVowelWidthKey() { return balancedKey(BALANCED_VOWEL_WIDTH); }
    public String balancedVowelPosKey() { return balancedKey(BALANCED_VOWEL_POS); }
    public String balancedConsonantWidthKey() { return balancedKey(BALANCED_CONSONANT_WIDTH); }
    public String balancedConsonantPosKey() { return balancedKey(BALANCED_CONSONANT_POS); }

    /** 배열별 값이 있으면 그것을, 없으면 배열별로 나누기 전의 값(세로 모드의 8열 단모음만)을, 그것도 없으면 기본값을 쓴다. */
    private int perLayoutInt(String key, String legacyKey, int def) {
        if (sp.contains(key)) return sp.getInt(key, def);
        return koreanNewSwipe() || profileValues() ? def : sp.getInt(legacyKey, def);
    }

    /** 지금 한글 배열의 자·모음 균형 레이아웃 모음 키 폭 (자음 키 폭의 %). */
    /** 지금 보기에서 자음·모음 키 폭의 최솟값 (%): 가로 모드·대화면 값은 30, 세로 모드는 60. */
    public int balancedWidthMin() { return profileValues() ? BALANCED_WIDTH_MIN_LANDSCAPE : BALANCED_WIDTH_MIN; }

    public int balancedVowelWidth() {
        int v = perLayoutInt(balancedVowelWidthKey(), BALANCED_VOWEL_WIDTH, BALANCED_WIDTH_DEFAULT);
        return Math.max(balancedWidthMin(), Math.min(BALANCED_WIDTH_MAX, v));
    }
    /** 지금 한글 배열의 자·모음 균형 레이아웃 모음 3열 위치 (0 = 오른쪽 절반의 왼쪽 끝, 50 = 가운데, 100 = 오른쪽 끝). */
    public int balancedVowelPos() {
        return Math.max(0, Math.min(100, perLayoutInt(balancedVowelPosKey(), BALANCED_VOWEL_POS, 50)));
    }
    /** 지금 한글 배열의 자·모음 균형 레이아웃 자음 키 폭 (왼쪽 절반을 꽉 채우는 폭의 %). */
    public int balancedConsonantWidth() {
        int v = perLayoutInt(balancedConsonantWidthKey(), BALANCED_CONSONANT_WIDTH, BALANCED_WIDTH_DEFAULT);
        return Math.max(balancedWidthMin(), Math.min(BALANCED_CONSONANT_WIDTH_MAX, v));
    }
    /** 지금 한글 배열의 자·모음 균형 레이아웃 자음 열 위치 (0 = 왼쪽 끝, 50 = 가운데, 100 = 가운데 선 쪽 끝). */
    public int balancedConsonantPos() {
        return Math.max(0, Math.min(100, perLayoutInt(balancedConsonantPosKey(), BALANCED_CONSONANT_POS, 50)));
    }
    /** 색 정렬: Shift·⌫·기호 쪽 넘김 키는 글자 키 색, 스페이스바는 기능키 색 (격자 정렬과 상관없이). */
    public boolean gridColors() { return sp.getBoolean(GRID_COLORS, false); }
    /** 한글 자판이 NewSwipe 단모음인지. */
    public boolean koreanD7() { return KOREAN_LAYOUT_D7.equals(sp.getString(KOREAN_LAYOUT, null)); }
    /** 한글 자판이 NewSwipe 배열(NewSwipe 단모음)인지: Shift 자리가 없고, ㅂ·ㅈ·ㄷ·ㄱ를 오른쪽으로 밀어 ㅍ·ㅊ·ㅌ·ㅋ을 입력한다. */
    public boolean koreanNewSwipe() { return koreanD7(); }
    public boolean enterKeyHidden() { return sp.getBoolean(ENTER_KEY_HIDDEN, false); }
    public boolean modeKeyHidden() { return sp.getBoolean(MODE_KEY_HIDDEN, false); }
    public boolean languageKeyHidden() { return sp.getBoolean(LANGUAGE_KEY_HIDDEN, false); }
    public boolean koreanShiftHidden() { return sp.getBoolean(KOREAN_SHIFT_HIDDEN, true); }
    /** 한글 자판에서 Shift 키를 껐을 때 그 자리에 Fn 키를 놓는지. */
    public boolean koreanShiftFn() { return sp.getBoolean(KOREAN_SHIFT_FN, false); }

    // 길게 누르기 문자 편집. 값은 글자들을 줄바꿈으로 이은 문자열이며, 빈 문자열은 "없음"이다.
    private static String popupKey(String group, String label) { return "popup_" + group + "_" + label; }

    /** 사용자가 고친 길게 누르기 문자. 고친 적이 없으면 null. */
    public String[] popupOverride(String group, String label) {
        String v = sp.getString(popupKey(group, label), null);
        if (v == null) return null;
        return v.isEmpty() ? new String[0] : v.split("\n");
    }

    public void setPopupOverride(String group, String label, String[] items) {
        sp.edit().putString(popupKey(group, label), String.join("\n", items)).apply();
    }

    public void resetPopupOverride(String group, String label) {
        sp.edit().remove(popupKey(group, label)).apply();
    }

    public boolean keyShadow() { return sp.getBoolean(KEY_SHADOW, false); }
    public int keyRadiusDp() { return clamp(sp.getInt(KEY_RADIUS, 7), 0, 24); }
    public int keyShadowStrength() { return sp.getInt(KEY_SHADOW_STRENGTH, 50); }
    public boolean suggestWords() { return sp.getBoolean(SUGGEST_WORDS, false); }
    public boolean suggestFullBar() { return sp.getBoolean(SUGGEST_FULL_BAR, false); }
    public boolean suggestSpace() { return sp.getBoolean(SUGGEST_SPACE, true); }
    public boolean autoCorrect() { return sp.getBoolean(AUTO_CORRECT, false); }
    public boolean learnWords() { return sp.getBoolean(LEARN_WORDS, true); }
    public boolean confirmLearnedDelete() { return sp.getBoolean(CONFIRM_LEARNED_DELETE, true); }
    public boolean toolbar() { return sp.getBoolean(TOOLBAR, true); }
    public boolean toolbarBottom() { return sp.getBoolean(TOOLBAR_BOTTOM, false); }
    public int toolbarHeight() { return clamp(sp.getInt(TOOLBAR_HEIGHT, 100), 70, 150); }
    public int toolButtonSize() { return clamp(sp.getInt(TOOL_BUTTON_SIZE, 100), 70, 150); }
    public boolean toolClipboard() { return sp.getBoolean(TOOL_CLIPBOARD, true); }
    public boolean toolEmoji() { return sp.getBoolean(TOOL_EMOJI, true); }
    public boolean toolSettings() { return sp.getBoolean(TOOL_SETTINGS, true); }
    public boolean toolHide() { return sp.getBoolean(TOOL_HIDE, true); }
    public boolean toolUndo() { return sp.getBoolean(TOOL_UNDO, true); }

    /** 도구 막대 버튼 배치 {왼쪽, 오른쪽} (ToolbarButtons). */
    public String[][] toolOrder() {
        return com.alternative_studios.newswipe.keyboard.ToolbarButtons.order(sp.getString(TOOL_ORDER, null));
    }

    public void setToolOrder(String[] left, String[] right) {
        sp.edit().putString(TOOL_ORDER, com.alternative_studios.newswipe.keyboard.ToolbarButtons.join(left, right)).apply();
    }

    /** 도구 막대 버튼의 표시 여부를 저장하는 키. */
    public static String toolShownKey(String id) {
        switch (id) {
            case com.alternative_studios.newswipe.keyboard.ToolbarButtons.CLIPBOARD: return TOOL_CLIPBOARD;
            case com.alternative_studios.newswipe.keyboard.ToolbarButtons.EMOJI: return TOOL_EMOJI;
            case com.alternative_studios.newswipe.keyboard.ToolbarButtons.VOICE: return VOICE_KEY;
            case com.alternative_studios.newswipe.keyboard.ToolbarButtons.UNDO: return TOOL_UNDO;
            case com.alternative_studios.newswipe.keyboard.ToolbarButtons.SETTINGS: return TOOL_SETTINGS;
            case com.alternative_studios.newswipe.keyboard.ToolbarButtons.ONE_HAND: return TOOL_ONE_HAND;
            default: return TOOL_HIDE;
        }
    }

    public boolean toolShown(String id) { return sp.getBoolean(toolShownKey(id), true); }

    public void setToolShown(String id, boolean shown) { sp.edit().putBoolean(toolShownKey(id), shown).apply(); }

    public boolean toolbarSwipe() { return sp.getBoolean(TOOLBAR_SWIPE, true); }
    public boolean toolEmojiSwipe() { return sp.getBoolean(TOOL_EMOJI_SWIPE, true); }
    public boolean toolClipboardSwipe() { return sp.getBoolean(TOOL_CLIPBOARD_SWIPE, true); }
    public boolean toolUndoSwipe() { return sp.getBoolean(TOOL_UNDO_SWIPE, true); }

    public String themeMode() { return sp.getString(THEME_MODE, THEME_SYSTEM); }
    public String accentMode() { return sp.getString(ACCENT_MODE, ACCENT_CUSTOM); }
    public int accentColor() { return sp.getInt(ACCENT_COLOR, AppTheme.DEFAULT_ACCENT); }

    public boolean longPressDelete() { return sp.getBoolean(LONG_PRESS_DELETE, true); }
    public boolean modeKeyLongPress() { return sp.getBoolean(MODE_KEY_LONG_PRESS, true); }
    public boolean longPressCustom() { return sp.getBoolean(LONG_PRESS_CUSTOM, false); }
    public boolean modeKeyEmoji() { return sp.getBoolean(MODE_KEY_EMOJI, true); }
    public int deletePressMs() { return sp.getInt(DELETE_PRESS_MS, 350); }

    public boolean deleteWordSwipe() { return sp.getBoolean(DELETE_WORD_SWIPE, true); }

    public boolean swipeDouble() { return sp.getBoolean(SWIPE_DOUBLE, true); }
    /** NewSwipe 단모음에서 ㅅ을 위로 밀어도 ㅆ을 입력하는지. */
    public boolean d7SsUp() { return sp.getBoolean(D7_SS_UP, false); }
    /** 지금 배열에서 ㅅ을 위로 밀어도 ㅆ을 입력하는지 (NewSwipe 단모음만). */
    public boolean ssUp() { return koreanD7() && d7SsUp(); }

    public boolean swipeIotized() { return sp.getBoolean(SWIPE_IOTIZED, true); }
    public boolean swipeCompoundVowel() { return sp.getBoolean(SWIPE_COMPOUND_VOWEL, true); }
    public boolean swipeFinal() { return sp.getBoolean(SWIPE_FINAL, true); }
    public boolean periodSwipeComma() { return sp.getBoolean(PERIOD_SWIPE_COMMA, true); }

    public boolean swipeCustom() { return sp.getBoolean(SWIPE_CUSTOM, false); }
    public boolean swipeFnCustom() { return sp.getBoolean(SWIPE_FN_CUSTOM, false); }


    public boolean swipeToolbarCustom() { return sp.getBoolean(SWIPE_TOOLBAR_CUSTOM, false); }

    // 밀어서 기능 완전 사용자화(기능키 "fn"·도구 막대 "toolbar"·키보드 밀기 "kb", SwipeSpec).
    // 값은 기능 ID(SwipeAction)이며, 정한 적이 없으면 null이다.
    private static String swipeActionKey(String group, String slot, String dir) {
        return "swipe_" + group + "_" + slot + "_" + dir;
    }

    public String swipeActionOverride(String group, String slot, String dir) {
        return sp.getString(swipeActionKey(group, slot, dir), null);
    }

    public void setSwipeActionOverride(String group, String slot, String dir, String actionId) {
        sp.edit().putString(swipeActionKey(group, slot, dir), actionId).apply();
    }

    public void resetSwipeActionOverride(String group, String slot, String dir) {
        sp.edit().remove(swipeActionKey(group, slot, dir)).apply();
    }


    // 스와이프 완전 사용자화. 값은 입력할 글자이며, 빈 문자열은 "없음"이다.
    private static String swipeCustomKey(String group, String label, String dir) {
        return "swipe_custom_" + group + "_" + label + "_" + dir;
    }

    /** 사용자가 정한 스와이프 글자. 정한 적이 없으면 null, "없음"이면 빈 문자열. */
    public String swipeCustomOverride(String group, String label, String dir) {
        return sp.getString(swipeCustomKey(group, label, dir), null);
    }

    public void setSwipeCustomOverride(String group, String label, String dir, String value) {
        sp.edit().putString(swipeCustomKey(group, label, dir), value).apply();
    }

    public void resetSwipeCustomOverride(String group, String label, String dir) {
        sp.edit().remove(swipeCustomKey(group, label, dir)).apply();
    }

    // 겹받침 스와이프 편집. 값은 겹받침 한 글자이며, 빈 문자열은 "없음"이다.
    private static String swipeFinalKey(char consonant, String dir) {
        return "swipe_final_" + consonant + "_" + dir;
    }

    /** 사용자가 고른 겹받침. 고른 적이 없으면 null, "없음"을 골랐으면 빈 문자열. */
    public String swipeFinalOverride(char consonant, String dir) {
        return sp.getString(swipeFinalKey(consonant, dir), null);
    }

    public void setSwipeFinalOverride(char consonant, String dir, String value) {
        sp.edit().putString(swipeFinalKey(consonant, dir), value).apply();
    }

    public void resetSwipeFinalOverride(char consonant, String dir) {
        sp.edit().remove(swipeFinalKey(consonant, dir)).apply();
    }

    // 조합형 이중모음 밀어서 입력 편집. 값은 이중모음 한 글자이며, 빈 문자열은 "없음"이다.
    private static String swipeVowelKey(char vowel, String dir) {
        return "swipe_vowel_" + vowel + "_" + dir;
    }

    /** 사용자가 고른 조합형 이중모음. 고른 적이 없으면 null, "없음"을 골랐으면 빈 문자열. */
    public String swipeVowelOverride(char vowel, String dir) {
        return sp.getString(swipeVowelKey(vowel, dir), null);
    }

    public void setSwipeVowelOverride(char vowel, String dir, String value) {
        sp.edit().putString(swipeVowelKey(vowel, dir), value).apply();
    }

    public void resetSwipeVowelOverride(char vowel, String dir) {
        sp.edit().remove(swipeVowelKey(vowel, dir)).apply();
    }

    public boolean korean() { return !"en".equals(sp.getString(LANGUAGE, "ko")); }

    public void setKorean(boolean korean) {
        sp.edit().putString(LANGUAGE, korean ? "ko" : "en").apply();
    }

    public String recentEmoji() { return sp.getString(RECENT_EMOJI, ""); }

    public void setRecentEmoji(String value) {
        sp.edit().putString(RECENT_EMOJI, value).apply();
    }

    public String pinnedEmoji() { return sp.getString(PINNED_EMOJI, ""); }

    public void setPinnedEmoji(String value) {
        sp.edit().putString(PINNED_EMOJI, value).apply();
    }
}
