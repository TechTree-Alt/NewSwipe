package com.alternative_studios.newswipe.keyboard;

import com.alternative_studios.newswipe.L;

/**
 * 기능키 길게 누르기: Shift 키·기호 키·언어 전환 키·엔터 키·지우기 키와 더한 키(이모지·클립보드·
 * 커서 이동·음성 입력)를 길게 눌렀을 때 실행할 기능을 키마다 직접 정한다 (기능 목록은 {@link SwipeAction}).
 * 정하지 않은 키는 기본 동작을 따른다: 기호 키 = 이모지 열기, 언어 전환 키 = 입력 방법 전환 창, 지우기 키 = 계속 지우기,
 * 스페이스바 = 밀어서 선택, Shift 키·엔터 키 = 없음.
 * 밀어서 기능 편집과 같은 틀({@link SwipeSpec})을 쓰고, 방향 대신 '길게 누르기' 칸 하나({@link #DIR})만 둔다.
 */
public final class LongPressActions {
    public static final int SHIFT = 0, MODE = 1, GLOBE = 2, ENTER = 3, DELETE = 4;
    /** 기능키 순서·유무 사용자화에서 더할 수 있는 키 (켰을 때만 편집 화면에 보인다, 처음에는 없음). */
    public static final int EMOJI_KEY = 5, CLIPBOARD = 6, CURSOR = 7, VOICE = 8;
    /** 스페이스바. 처음에는 '밀어서 선택'이다. */
    public static final int SPACE = 9;
    /** 길게 누르기를 저장하는 칸 (SwipeSpec의 방향 자리를 하나만 쓴다). */
    public static final int DIR = Key.SWIPE_DOWN;
    private static String[] labels() {
        return new String[]{
            L.t("Shift 키 (⇧)", "Shift key (⇧)"), L.t("기호 키 (?123·가·ABC)", "Symbol key (?123·가·ABC)"), L.t("언어 전환 키 (🌐︎)", "Globe key (🌐︎)"), L.t("엔터 키 (↵)", "Enter key (↵)"), L.t("지우기 키 (⌫)", "Delete key (⌫)"),
            L.t("이모지 키", "Emoji key"), L.t("클립보드 키", "Clipboard key"), L.t("커서 이동 키", "Cursor key"), L.t("음성 입력 키", "Voice input key"), L.t("스페이스바", "Space bar")};
    }
    private static String[] hints() {
        return new String[]{
            L.t("영어 자판의 대문자 키", "Shift key on the English keyboard"),
            L.t("글자 자판과 기호 자판을 오가는 왼쪽 아래 키", "Bottom-left key that switches between the letter and symbol keyboards"),
            L.t("언어 전환. 기호 자판에서는 이 자리의 이모지 키", "Switches languages. The emoji key in this spot on the symbol keyboard"),
            L.t("입력란에 따라 줄바꿈·보내기·검색 등", "New line, send, search, etc., depending on the field"),
            L.t("지우기", "Delete"),
            L.t("이모지 창 열기", "Opens the emoji panel"),
            L.t("클립보드 창 열기", "Opens the clipboard panel"),
            L.t("상하좌우로 밀면 커서가 움직입니다.", "Swipe in any direction to move the cursor."),
            L.t("음성 입력", "Voice input"),
            L.t("띄어쓰기", "Space"),
    };
    }
    /** 더할 수 있는 키 칸의 맨 아래 줄 키 ID (BottomKeys). 편집 화면에 보일지 정할 때 쓴다. */
    private static final String[] EXTRA_IDS = {BottomKeys.EMOJI, BottomKeys.CLIPBOARD, BottomKeys.CURSOR, BottomKeys.VOICE};

    public static final SwipeSpec SPEC = new SwipeSpec("lp", new String[]{"shift", "mode", "globe", "enter", "delete",
            "emoji", "clipboard", "cursor", "voice", "space"}) {
        @Override
        public String defaultAction(int slot, int dir) {
            switch (slot) {
                case MODE: return SwipeAction.EMOJI_OPEN;
                case GLOBE: return SwipeAction.IME_PICKER;
                case DELETE: return SwipeAction.DELETE_REPEAT;
                case SPACE: return SwipeAction.SPACE_SELECT;
                default: return SwipeAction.NONE;
            }
        }

        /**
         * 켠 키의 칸만 편집 화면에 보인다: 언어 전환 키와 더한 키(이모지·클립보드·커서 이동·음성 입력)는 '기능키 순서·유무
         * 사용자화'에서 끄면 감춘다. Shift 키는 영어 자판에, 기호 키·엔터 키·⌫는 숫자 패드(숫자 입력란 등)에 늘 있어 그 설정이
         * 쓰이므로 늘 보인다.
         */
        @Override
        public boolean shown(com.alternative_studios.newswipe.Prefs prefs, int slot) {
            if (prefs == null) return true;
            if (slot == GLOBE) return prefs.bottomKeyShown(BottomKeys.GLOBE);
            if (slot >= EMOJI_KEY && slot <= VOICE) return prefs.bottomKeyShown(EXTRA_IDS[slot - EMOJI_KEY]);
            return true;
        }

        /** 모르는 값은 기본값으로, 밀어야 하는 '커서 자유 이동'은 없음으로 본다. */
        @Override
        public String resolve(String stored, int slot, int dir) {
            if (stored == null || !SwipeAction.isKnown(stored)) return defaultAction(slot, dir);
            if (SwipeAction.CURSOR_MOVE.equals(stored) || SwipeAction.FREE_CURSOR.equals(stored)) return SwipeAction.NONE;
            if (SwipeAction.SPACE_SELECT.equals(stored) && slot != SPACE) return SwipeAction.NONE;   // 스페이스바 전용
            return stored;
        }

        @Override
        public String title() {
            return L.t("기능키 길게 누르기 기능 사용자화", "Customize function key long press");
        }

        @Override
        public String note() {
            return L.t("기능키를 길게 누를 때 실행할 기능을 고릅니다.", "Choose what happens when you long press a function key.");
        }

        @Override
        public String cardTitle(int slot) {
            return labels()[slot];
        }

        @Override
        public String cardHint(int slot) {
            return hints()[slot];
        }

        @Override
        public int[] dirs(int slot) {
            return new int[]{DIR};
        }

        @Override
        public String dirLabel(int dir) {
            return L.t("길게 누르기", "Long press");
        }

        @Override
        public String[] choices(int slot) {
            return slot == SPACE ? SwipeAction.idsForSpaceLongPress() : SwipeAction.idsForLongPress();
        }

        @Override
        public String dialogTitle(int slot, int dir) {
            return L.t("'" + cardTitle(slot) + "' 길게 누르기", "Long press '" + cardTitle(slot) + "'");
        }
    };

    private LongPressActions() {
    }

    /** 키 종류에 해당하는 칸. 길게 누르기 사용자화의 대상이 아닌 키면 -1. */
    public static int slotOf(int keyType) {
        switch (keyType) {
            case Key.SHIFT: return SHIFT;
            case Key.TO_SYMBOLS:
            case Key.TO_LETTERS: return MODE;
            case Key.LANGUAGE:
            case Key.EMOJI: return GLOBE;
            case Key.ENTER: return ENTER;
            case Key.DELETE: return DELETE;
            case Key.EMOJI_KEY: return EMOJI_KEY;
            case Key.CLIPBOARD: return CLIPBOARD;
            case Key.CURSOR_PAD: return CURSOR;
            case Key.VOICE: return VOICE;
            case Key.SPACE: return SPACE;
            default: return -1;
        }
    }

    /** 설정의 배치로 칸마다 기능 ID를 담은 배열 (SHIFT~SPACE 순서). */
    public static String[] actions(com.alternative_studios.newswipe.Prefs prefs) {
        String[][] t = SPEC.table(prefs);
        String[] out = new String[t.length];
        for (int i = 0; i < t.length; i++) out[i] = t[i][DIR];
        return out;
    }
}
