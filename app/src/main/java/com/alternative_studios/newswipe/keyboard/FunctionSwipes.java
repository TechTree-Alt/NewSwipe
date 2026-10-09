package com.alternative_studios.newswipe.keyboard;

import com.alternative_studios.newswipe.Prefs;

import java.util.function.BiFunction;

/**
 * 기능키 밀어서 기능 완전 사용자화. 켜면 기호 키·언어 전환 키·스페이스바·엔터 키·지우기 키를 위·아래·왼쪽·오른쪽으로
 * 밀었을 때 실행할 기능을 키·방향마다 직접 정한다 (기능 목록은 {@link SwipeAction}).
 * 정하지 않은 방향은 기본 동작을 따른다: 기호 키 위 = 이모지 열기, 지우기 키 왼쪽 = 단어 지우기,
 * 지우기 키 위 = 줄에서 커서 왼쪽 모두 지우기, 스페이스바 = 커서 자유 이동, 나머지는 없음.
 */
public final class FunctionSwipes {
    public static final int MODE = 0, GLOBE = 1, SPACE = 2, ENTER = 3, DELETE = 4;
    /** 편집 화면에 보이는 이름 (이름과 키 모양). */
    private static final String[] LABELS = {
            "기호 키 (?123·가·ABC)", "언어 전환 키 (🌐︎)", "스페이스바 (␣)", "엔터 키 (↵)", "지우기 키 (⌫)"};
    private static final String[] HINTS = {
            "글자 자판과 기호 자판을 오가는 왼쪽 아래 키",
            "한/영 전환. 기호 자판에서는 이 자리의 이모지 키",
            "띄어쓰기",
            "입력란에 따라 줄바꿈·보내기·검색 등",
            "지우기",
    };

    public static final SwipeSpec SPEC = new SwipeSpec("fn", new String[]{"mode", "globe", "space", "enter", "delete"}) {
        @Override
        public String defaultAction(int slot, int dir) {
            if (slot == MODE && dir == Key.SWIPE_UP) return SwipeAction.EMOJI_OPEN;
            if (slot == DELETE && dir == Key.SWIPE_LEFT) return SwipeAction.DELETE_WORD;
            if (slot == DELETE && dir == Key.SWIPE_UP) return SwipeAction.DELETE_LINE_START;
            if (slot == SPACE) return SwipeAction.CURSOR_MOVE;
            return SwipeAction.NONE;
        }

        /** 모르는 값과 '커서 자유 이동 (문자 키)'는 기본값으로, 스페이스바가 아닌 키의 '커서 자유 이동 (스페이스바)'는 없음으로 본다. */
        @Override
        public String resolve(String stored, int slot, int dir) {
            if (stored == null || !SwipeAction.isKnown(stored) || SwipeAction.FREE_CURSOR.equals(stored)) {
                return defaultAction(slot, dir);
            }
            if (SwipeAction.CURSOR_MOVE.equals(stored) && slot != SPACE) return SwipeAction.NONE;
            return stored;
        }

        @Override
        public String title() {
            return "기능키 밀어서 기능 편집";
        }

        @Override
        public String note() {
            return "기능키를 각 방향으로 밀 때 실행할 기능을 고릅니다.";
        }

        @Override
        public String cardTitle(int slot) {
            return LABELS[slot];
        }

        @Override
        public String cardHint(int slot) {
            return HINTS[slot];
        }

        @Override
        public String[] choices(int slot) {
            return SwipeAction.idsFor(slot);
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
        return SwipeAction.CURSOR_MOVE.equals(table[SPACE][Key.SWIPE_LEFT])
                || SwipeAction.CURSOR_MOVE.equals(table[SPACE][Key.SWIPE_RIGHT]);
    }

    /** 스페이스바를 세로로 밀어 줄 단위로 커서를 옮기는 기능을 쓰는지 (위나 아래에 '커서 자유 이동'이 있을 때). */
    public boolean cursorV() {
        return SwipeAction.CURSOR_MOVE.equals(table[SPACE][Key.SWIPE_UP])
                || SwipeAction.CURSOR_MOVE.equals(table[SPACE][Key.SWIPE_DOWN]);
    }
}
