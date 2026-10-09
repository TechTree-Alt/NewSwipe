package com.alternative_studios.newswipe.keyboard;

/**
 * 기능키 길게 누르기 기능 완전 사용자화. 켜면 Shift 키·기호 키·언어 전환 키·엔터 키·지우기 키를 길게 눌렀을 때
 * 실행할 기능을 키마다 직접 정한다 (기능 목록은 {@link SwipeAction}).
 * 정하지 않은 키는 기본 동작을 따른다: 기호 키 = 이모지 열기, 언어 전환 키 = 입력 방법 전환 창, 지우기 키 = 계속 지우기,
 * Shift 키·엔터 키 = 없음.
 * 밀어서 기능 편집과 같은 틀({@link SwipeSpec})을 쓰고, 방향 대신 '길게 누르기' 칸 하나({@link #DIR})만 둔다.
 */
public final class LongPressActions {
    public static final int SHIFT = 0, MODE = 1, GLOBE = 2, ENTER = 3, DELETE = 4;
    /** 길게 누르기를 저장하는 칸 (SwipeSpec의 방향 자리를 하나만 쓴다). */
    public static final int DIR = Key.SWIPE_DOWN;
    private static final String[] LABELS = {
            "Shift 키 (⇧)", "기호 키 (?123·가·ABC)", "언어 전환 키 (🌐︎)", "엔터 키 (↵)", "지우기 키 (⌫)"};
    private static final String[] HINTS = {
            "영어 자판의 대문자 키",
            "글자 자판과 기호 자판을 오가는 왼쪽 아래 키",
            "한/영 전환. 기호 자판에서는 이 자리의 이모지 키",
            "입력란에 따라 줄바꿈·보내기·검색 등",
            "지우기",
    };

    public static final SwipeSpec SPEC = new SwipeSpec("lp", new String[]{"shift", "mode", "globe", "enter", "delete"}) {
        @Override
        public String defaultAction(int slot, int dir) {
            switch (slot) {
                case MODE: return SwipeAction.EMOJI_OPEN;
                case GLOBE: return SwipeAction.IME_PICKER;
                case DELETE: return SwipeAction.DELETE_REPEAT;
                default: return SwipeAction.NONE;
            }
        }

        /** 모르는 값은 기본값으로, 밀어야 하는 '커서 자유 이동'은 없음으로 본다. */
        @Override
        public String resolve(String stored, int slot, int dir) {
            if (stored == null || !SwipeAction.isKnown(stored)) return defaultAction(slot, dir);
            if (SwipeAction.CURSOR_MOVE.equals(stored) || SwipeAction.FREE_CURSOR.equals(stored)) return SwipeAction.NONE;
            return stored;
        }

        @Override
        public String title() {
            return "기능키 길게 누르기 기능 편집";
        }

        @Override
        public String note() {
            return "기능키를 길게 누를 때 실행할 기능을 고릅니다.";
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
        public int[] dirs(int slot) {
            return new int[]{DIR};
        }

        @Override
        public String dirLabel(int dir) {
            return "길게 누르기";
        }

        @Override
        public String[] choices(int slot) {
            return SwipeAction.idsForLongPress();
        }

        @Override
        public String dialogTitle(int slot, int dir) {
            return "'" + cardTitle(slot) + "' 길게 누르기";
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
            default: return -1;
        }
    }

    /** 설정의 배치로 칸마다 기능 ID를 담은 배열 (SHIFT~DELETE 순서). */
    public static String[] actions(com.alternative_studios.newswipe.Prefs prefs) {
        String[][] t = SPEC.table(prefs);
        String[] out = new String[t.length];
        for (int i = 0; i < t.length; i++) out[i] = t[i][DIR];
        return out;
    }
}
