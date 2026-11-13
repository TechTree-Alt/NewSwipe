package com.alternative_studios.newswipe.keyboard;

import com.alternative_studios.newswipe.L;
import com.alternative_studios.newswipe.Prefs;

import java.util.function.BiFunction;

/**
 * 기능키 밀기: 기호 키·언어 전환 키·스페이스바·엔터 키·지우기 키(와 더한 키)를 위·아래·왼쪽·오른쪽으로
 * 밀었을 때 실행할 기능을 키·방향마다 직접 정한다 (기능 목록은 {@link SwipeAction}).
 * 정하지 않은 방향은 기본 동작을 따른다: 기호 키 위 = 이모지 열기, 지우기 키 왼쪽 = 단어 지우기,
 * 지우기 키 위 = 줄에서 커서 왼쪽 모두 지우기, 스페이스바 = 커서 자유 이동, 나머지는 없음.
 */
public final class FunctionSwipes {
    public static final int MODE = 0, GLOBE = 1, SPACE = 2, ENTER = 3, DELETE = 4;
    /** 기능키 순서·유무 사용자화에서 더할 수 있는 키 (켰을 때만 편집 화면에 보인다). */
    public static final int EMOJI_KEY = 5, CLIPBOARD = 6, CURSOR = 7, VOICE = 8;
    /** 편집 화면에 보이는 이름 (이름과 키 모양). */
    private static String[] labels() {
        return new String[]{
            L.t("기호 키 (?123·가·ABC)", "Symbol key (?123·가·ABC)"), L.t("언어 전환 키 (🌐︎)", "Globe key (🌐︎)"), L.t("스페이스바 (␣)", "Space bar (␣)"), L.t("엔터 키 (↵)", "Enter key (↵)"), L.t("지우기 키 (⌫)", "Delete key (⌫)"),
            L.t("이모지 키", "Emoji key"), L.t("클립보드 키", "Clipboard key"), L.t("커서 이동 키", "Cursor key"), L.t("음성 입력 키", "Voice input key")};
    }
    private static String[] hints() {
        return new String[]{
            L.t("글자 자판과 기호 자판을 오가는 왼쪽 아래 키", "Bottom-left key that switches between the letter and symbol keyboards"),
            L.t("언어 전환. 기호 자판에서는 이 자리의 이모지 키", "Switches languages. The emoji key in this spot on the symbol keyboard"),
            L.t("띄어쓰기", "Space"),
            L.t("입력란에 따라 줄바꿈·보내기·검색 등", "New line, send, search, etc., depending on the field"),
            L.t("지우기", "Delete"),
            L.t("이모지 창 열기", "Opens the emoji panel"),
            L.t("클립보드 창 열기", "Opens the clipboard panel"),
            L.t("밀어서 커서 이동 (처음에는 네 방향 모두 커서 이동)", "Swipe to move the cursor (all four directions move the cursor by default)"),
            L.t("음성 입력", "Voice input"),
    };
    }
    /** 칸마다 맨 아래 줄 키의 ID (BottomKeys). 지우기 키는 맨 아래 줄 키가 아니라 null. 편집 화면에 보일지 정할 때 쓴다. */
    private static final String[] BOTTOM_IDS = {BottomKeys.MODE, BottomKeys.GLOBE, BottomKeys.SPACE, BottomKeys.ENTER, null,
            BottomKeys.EMOJI, BottomKeys.CLIPBOARD, BottomKeys.CURSOR, BottomKeys.VOICE};

    /** 처음부터 '커서 자유 이동'이 기본인 칸: 스페이스바와 커서 이동 키. */
    static boolean cursorSlot(int slot) {
        return slot == SPACE || slot == CURSOR;
    }

    public static final SwipeSpec SPEC = new SwipeSpec("fn", new String[]{"mode", "globe", "space", "enter", "delete",
            "emoji", "clipboard", "cursor", "voice"}) {
        @Override
        public String defaultAction(int slot, int dir) {
            if (slot == MODE && dir == Key.SWIPE_UP) return SwipeAction.EMOJI_OPEN;
            if (slot == DELETE && dir == Key.SWIPE_LEFT) return SwipeAction.DELETE_WORD;
            if (slot == DELETE && dir == Key.SWIPE_UP) return SwipeAction.DELETE_LINE_START;
            if (cursorSlot(slot)) return SwipeAction.CURSOR_MOVE;
            return SwipeAction.NONE;
        }

        /**
         * 모르는 값과 '커서 자유 이동 (문자 키)'는 기본값으로 본다.
         */
        @Override
        public String resolve(String stored, int slot, int dir) {
            if (stored == null || !SwipeAction.isKnown(stored) || SwipeAction.FREE_CURSOR.equals(stored)) {
                return defaultAction(slot, dir);
            }
            if (SwipeAction.globeUpOnly(stored) && (slot != GLOBE || dir != Key.SWIPE_UP)) return SwipeAction.NONE;
            return stored;
        }

        /**
         * 켠 기능키의 칸만 편집 화면에 보인다 (기능키 순서·유무 사용자화에서 끈 키는 감춘다). 기호 키·스페이스바·엔터 키·⌫는
         * 맨 아래 줄에서 꺼도 숫자 패드(숫자 입력란 등)에 늘 있어 그 설정이 쓰이므로 늘 보인다.
         */
        @Override
        public boolean shown(Prefs prefs, int slot) {
            if (prefs == null || slot == MODE || slot == SPACE || slot == ENTER || slot == DELETE) return true;
            return prefs.bottomKeyShown(BOTTOM_IDS[slot]);
        }

        @Override
        public String title() {
            return L.t("기능키 밀어서 기능 사용자화", "Customize function key swipes");
        }

        @Override
        public String note() {
            return L.t("기능키를 각 방향으로 밀 때 실행할 기능을 고릅니다.", "Choose what happens when you swipe a function key in each direction.");
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
        public String[] choices(int slot) {
            return SwipeAction.idsFor(slot);
        }

        /** '언어 고르기'는 🌐︎를 위로 밀 때만 고를 수 있다. */
        @Override
        public String[] choices(int slot, int dir) {
            String[] ids = choices(slot);
            if (slot == GLOBE && dir == Key.SWIPE_UP) return ids;
            java.util.List<String> out = new java.util.ArrayList<>();
            for (String id : ids) if (!SwipeAction.globeUpOnly(id)) out.add(id);
            return out.toArray(new String[0]);
        }
    };

    private final String[][] table;

    private FunctionSwipes(String[][] table) {
        this.table = table;
    }

    /** 키 종류에 해당하는 칸. 이 기능의 대상이 아닌 키면 -1. */
    public static int slotOf(int keyType) {
        switch (keyType) {
            case Key.TO_SYMBOLS:
            case Key.TO_LETTERS:
                return MODE;
            case Key.LANGUAGE:
            case Key.EMOJI:
                return GLOBE;
            case Key.SPACE:
                return SPACE;
            case Key.ENTER:
                return ENTER;
            case Key.DELETE:
                return DELETE;
            case Key.EMOJI_KEY:
                return EMOJI_KEY;
            case Key.CLIPBOARD:
                return CLIPBOARD;
            case Key.CURSOR_PAD:
                return CURSOR;
            case Key.VOICE:
                return VOICE;
            default:
                return -1;
        }
    }

    /** overrides: (키 이름, 방향 이름) → 저장된 값 (정하지 않았으면 null). */
    static FunctionSwipes from(BiFunction<String, String, String> overrides) {
        return new FunctionSwipes(SPEC.table(overrides));
    }

    public static FunctionSwipes load(Prefs prefs) {
        return new FunctionSwipes(SPEC.table(prefs));
    }

    public String action(int slot, int dir) {
        return table[slot][dir];
    }

    /** 스페이스바를 가로로 밀어 커서를 옮기는 기능을 쓰는지 (왼쪽이나 오른쪽에 '커서 자유 이동'이 있을 때). */
    public boolean cursorH() {
        return cursorH(SPACE);
    }

    /** 스페이스바를 세로로 밀어 줄 단위로 커서를 옮기는 기능을 쓰는지 (위나 아래에 '커서 자유 이동'이 있을 때). */
    public boolean cursorV() {
        return cursorV(SPACE);
    }

    /** slot의 키를 가로로 밀어 커서를 옮기는지. */
    public boolean cursorH(int slot) {
        return SwipeAction.CURSOR_MOVE.equals(table[slot][Key.SWIPE_LEFT])
                || SwipeAction.CURSOR_MOVE.equals(table[slot][Key.SWIPE_RIGHT]);
    }

    /** slot의 키를 세로로 밀어 줄 단위로 커서를 옮기는지. */
    public boolean cursorV(int slot) {
        return SwipeAction.CURSOR_MOVE.equals(table[slot][Key.SWIPE_UP])
                || SwipeAction.CURSOR_MOVE.equals(table[slot][Key.SWIPE_DOWN]);
    }
}
