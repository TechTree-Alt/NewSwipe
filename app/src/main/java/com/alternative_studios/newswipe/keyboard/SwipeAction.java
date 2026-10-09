package com.alternative_studios.newswipe.keyboard;

/**
 * 기능 키(기호 키, 지구본 키, 스페이스바, 엔터 키, 지우기 키)를 밀었을 때 고를 수 있는 기능 목록.
 * 설정에는 ID 문자열로 저장하고, 화면에는 이름(label)을 보여 준다. 실제 동작은 키보드 서비스가 한다.
 */
public final class SwipeAction {
    public static final String NONE = "none";
    /** 스페이스바를 밀어 커서를 옮긴다 (손을 떼지 않고 계속 밀면 계속 움직인다). 스페이스바에서만 고를 수 있다. */
    public static final String CURSOR_MOVE = "cursor_move";
    /** 문자 키를 밀어 트랙패드처럼 커서를 옮긴다 ('문자 키를 밀어서 커서 이동'과 같다). 키보드 한 손가락 밀기에서만 고를 수 있다. */
    public static final String FREE_CURSOR = "free_cursor";
    public static final String CLIPBOARD_OPEN = "clipboard_open";
    public static final String CLIPBOARD_PINNED = "clipboard_pinned";
    public static final String EMOJI_OPEN = "emoji_open";
    public static final String EMOJI_LATEST = "emoji_latest";
    public static final String EMOJI_PINNED = "emoji_pinned";
    public static final String LINE_START = "line_start";
    public static final String LINE_END = "line_end";
    public static final String TEXT_START = "text_start";
    public static final String TEXT_END = "text_end";
    public static final String CURSOR_LEFT = "cursor_left";
    public static final String CURSOR_RIGHT = "cursor_right";
    public static final String CURSOR_UP = "cursor_up";
    public static final String CURSOR_DOWN = "cursor_down";
    public static final String WORD_LEFT = "word_left";
    public static final String WORD_RIGHT = "word_right";
    /** 지우기 키를 누른 것과 같다 (한 글자 지우기). */
    public static final String BACKSPACE = "backspace";
    public static final String DELETE_WORD = "delete_word";
    public static final String DELETE_LINE_START = "delete_line_start";
    public static final String DELETE_LINE = "delete_line";
    public static final String UNDO = "undo";
    public static final String REDO = "redo";
    public static final String SELECT_ALL = "select_all";
    public static final String COPY = "copy";
    public static final String CUT = "cut";
    public static final String PASTE = "paste";
    public static final String LANGUAGE = "language";
    /** 기호 자판을 연다. 이미 기호 자판이면 글자 자판으로 돌아간다 (기호 키와 같다). */
    public static final String SYMBOLS = "symbols";
    /** 엔터 키와 같다 (입력란에 따라 줄바꿈·보내기·검색 등). */
    public static final String ENTER = "enter";
    /** 스페이스바를 누른 것과 같다 (띄어쓰기). */
    public static final String SPACE = "space";
    public static final String IME_PICKER = "ime_picker";
    public static final String VOICE = "voice";
    public static final String HIDE_KEYBOARD = "hide_keyboard";
    public static final String SETTINGS = "settings";
    /** 왼쪽(오른쪽)으로 쏠린 한 손 모드를 켠다. 이미 그쪽 한 손 모드면 끈다. */
    public static final String ONE_HAND_LEFT = "one_hand_left";
    public static final String ONE_HAND_RIGHT = "one_hand_right";
    /** 길게 누르기 전용: 누르고 있는 동안 계속 지우기. */
    public static final String DELETE_REPEAT = "delete_repeat";
    /** 길게 누르기 전용: 영어 자판에서 Caps Lock(대문자 고정) 켜기. */
    public static final String CAPS_LOCK = "caps_lock";

    /** {ID, 목록에 보이는 이름, 편집 화면의 작은 칸에 보이는 짧은 이름}. 보이는 순서대로 적는다. */
    private static final String[][] ALL = {
            {NONE, "없음", "없음"},
            {UNDO, "실행 취소", "실행 취소"},
            {REDO, "다시 실행", "다시 실행"},
            {SELECT_ALL, "모두 선택", "모두 선택"},
            {COPY, "복사", "복사"},
            {CUT, "잘라내기", "잘라내기"},
            {PASTE, "붙여넣기 (지금 클립보드)", "붙여넣기"},
            {CLIPBOARD_OPEN, "클립보드 열기", "클립보드"},
            {CLIPBOARD_PINNED, "첫 번째 고정 클립보드 항목 붙여넣기", "고정 클립보드 붙여넣기"},
            {EMOJI_OPEN, "이모지 열기", "이모지"},
            {EMOJI_LATEST, "가장 최근 사용한 이모지 붙여넣기", "최근 이모지"},
            {EMOJI_PINNED, "첫 번째 고정 이모지 붙여넣기", "고정 이모지"},
            {IME_PICKER, "입력 방법 전환 창 열기", "입력기 선택"},
            {LANGUAGE, "한/영 전환", "한/영 전환"},
            {SYMBOLS, "기호 입력", "기호 입력"},
            {ENTER, "엔터", "엔터"},
            {SPACE, "띄어쓰기", "띄어쓰기"},
            {FREE_CURSOR, "커서 자유 이동 (문자 키를 트랙패드처럼)", "커서 자유 이동"},
            {CURSOR_MOVE, "커서 자유 이동 (스페이스바)", "커서 자유 이동"},
            {LINE_START, "커서를 줄의 맨 앞으로 이동", "줄 맨 앞"},
            {LINE_END, "커서를 줄의 맨 뒤로 이동", "줄 맨 뒤"},
            {TEXT_START, "커서를 글 전체의 맨 앞으로 이동", "글 맨 앞"},
            {TEXT_END, "커서를 글 전체의 맨 뒤로 이동", "글 맨 뒤"},
            {CURSOR_LEFT, "커서를 한 글자 왼쪽으로 이동", "한 칸 왼쪽"},
            {CURSOR_RIGHT, "커서를 한 글자 오른쪽으로 이동", "한 칸 오른쪽"},
            {CURSOR_UP, "커서를 줄 한 칸 위로 이동", "한 줄 위"},
            {CURSOR_DOWN, "커서를 줄 한 칸 아래로 이동", "한 줄 아래"},
            {WORD_LEFT, "커서를 단어 단위로 왼쪽으로 이동", "단어 왼쪽"},
            {WORD_RIGHT, "커서를 단어 단위로 오른쪽으로 이동", "단어 오른쪽"},
            {BACKSPACE, "한 글자 지우기 (⌫)", "한 글자 지우기"},
            {DELETE_WORD, "단어 지우기", "단어 지우기"},
            {DELETE_LINE_START, "줄에서 커서 왼쪽 모두 지우기", "왼쪽 모두 지우기"},
            {DELETE_LINE, "줄 전체 지우기", "줄 지우기"},
            {VOICE, "음성 입력", "음성 입력"},
            {HIDE_KEYBOARD, "키보드 숨기기", "키보드 숨기기"},
            {ONE_HAND_LEFT, "한 손 모드 (왼쪽)", "한 손 왼쪽"},
            {ONE_HAND_RIGHT, "한 손 모드 (오른쪽)", "한 손 오른쪽"},
            {SETTINGS, "NewSwipe 설정 열기", "설정 열기"},
            {DELETE_REPEAT, "계속 지우기 (누르고 있는 동안)", "계속 지우기"},
            {CAPS_LOCK, "Caps Lock 켜기 (영어 대문자 고정)", "Caps Lock"},
    };

    /** 길게 누르기에서만 쓰는 기능 (밀기 목록에는 보이지 않는다). */
    private static boolean longPressOnly(String id) {
        return DELETE_REPEAT.equals(id) || CAPS_LOCK.equals(id);
    }

    /** 기능키 길게 누르기에서 고를 수 있는 ID들 (밀어야 하는 '커서 자유 이동'은 뺀다). */
    public static String[] idsForLongPress() {
        java.util.List<String> ids = new java.util.ArrayList<>();
        for (String[] a : ALL) {
            if (!CURSOR_MOVE.equals(a[0]) && !FREE_CURSOR.equals(a[0])) ids.add(a[0]);
        }
        return ids.toArray(new String[0]);
    }

    private SwipeAction() {
    }

    /** 키 slot(FunctionSwipes.SPACE 등)에서 고를 수 있는 ID들. 화면에 보이는 순서. */
    public static String[] idsFor(int slot) {
        boolean space = slot == FunctionSwipes.SPACE;
        java.util.List<String> ids = new java.util.ArrayList<>();
        for (String[] a : ALL) {
            if (FREE_CURSOR.equals(a[0]) || longPressOnly(a[0])) continue;
            if (space || !CURSOR_MOVE.equals(a[0])) ids.add(a[0]);
        }
        return ids.toArray(new String[0]);
    }

    /** 키보드 밀기에서 고를 수 있는 ID들. '커서 자유 이동'은 한 손가락에서만 고를 수 있다. */
    public static String[] idsForKeyboard(boolean oneFinger) {
        java.util.List<String> ids = new java.util.ArrayList<>();
        for (String[] a : ALL) {
            if (CURSOR_MOVE.equals(a[0]) || (!oneFinger && FREE_CURSOR.equals(a[0])) || longPressOnly(a[0])) continue;
            ids.add(a[0]);
        }
        return ids.toArray(new String[0]);
    }

    /** 도구 막대에서 고를 수 있는 ID들 ('커서 이동'은 스페이스바 전용이라 뺀다). */
    public static String[] idsForToolbar() {
        return idsFor(-1);
    }

    public static boolean isKnown(String id) {
        return find(id) != null;
    }

    /** 목록에 보이는 이름. 모르는 ID면 "없음". */
    public static String label(String id) {
        String[] a = find(id);
        return a == null ? ALL[0][1] : a[1];
    }

    /** 편집 화면의 작은 칸에 보이는 짧은 이름. */
    public static String shortLabel(String id) {
        String[] a = find(id);
        return a == null ? ALL[0][2] : a[2];
    }

    private static String[] find(String id) {
        if (id == null) return null;
        for (String[] a : ALL) if (a[0].equals(id)) return a;
        return null;
    }
}
