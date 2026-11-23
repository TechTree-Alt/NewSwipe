package com.alternative_studios.newswipe.keyboard;

import com.alternative_studios.newswipe.L;
import com.alternative_studios.newswipe.Prefs;

import java.util.function.BiFunction;

/**
 * 도구 막대 밀기: 도구 막대 자체와 도구 막대의 버튼을
 * 위·아래·왼쪽·오른쪽으로 밀 때 실행할 기능을 직접 정한다 (기능 목록은 {@link SwipeAction}).
 * 정하지 않은 방향은 기본 동작을 따른다: 도구 막대 왼쪽 = 줄 맨 앞, 오른쪽 = 줄 맨 뒤, 위·아래 = 한 줄 위·아래,
 * 클립보드 버튼 위 = 복사, 아래 = 붙여넣기, 이모지 버튼 아래 = 최근 이모지, 실행 취소 버튼 아래 = 다시 실행,
 * 한 손 모드 버튼 왼쪽·오른쪽 = 그쪽 한 손 모드, 나머지는 없음 (버튼에서 '없음'인 방향은 도구 막대를 민 것으로 본다).
 */
public final class ToolbarSwipes {
    /** 도구 막대 자체. 네 방향을 모두 쓴다. */
    public static final int BAR = 0;
    /** 도구 막대의 버튼들. 위·아래만 쓴다. */
    public static final int CLIPBOARD = 1, EMOJI = 2, VOICE = 3, UNDO = 4, SETTINGS = 5, HIDE = 6, ONE_HAND = 7, FLOAT = 8, TEXT_EDIT = 9;
    private static String[] labels() {
        return new String[]{L.t("도구 막대", "Toolbar"), L.t("클립보드", "Clipboard"), L.t("이모지", "Emoji"), L.t("음성 입력", "Voice input"), L.t("실행 취소", "Undo"), L.t("설정", "Settings"), L.t("키보드 숨기기", "Hide keyboard"),
            L.t("한 손 모드", "One-handed mode"), L.t("떠 있는 키보드", "Floating keyboard"), L.t("텍스트 편집", "Text editing")};
    }
    /** 칸 이름 (도구 막대 자체와 버튼 ID). 밀기와 길게 누르기가 같은 이름을 쓴다. */
    private static final String[] SLOT_NAMES =
            {"bar", "clipboard", "emoji", "voice", "undo", "settings", "hide", "onehand", "float", "textedit"};

    public static final SwipeSpec SPEC = new SwipeSpec("toolbar", SLOT_NAMES) {
        @Override
        public String defaultAction(int slot, int dir) {
            if (slot == BAR && dir == Key.SWIPE_LEFT) return SwipeAction.LINE_START;
            if (slot == BAR && dir == Key.SWIPE_RIGHT) return SwipeAction.LINE_END;
            if (slot == BAR && dir == Key.SWIPE_UP) return SwipeAction.CURSOR_UP;
            if (slot == BAR && dir == Key.SWIPE_DOWN) return SwipeAction.CURSOR_DOWN;
            if (slot == CLIPBOARD && dir == Key.SWIPE_UP) return SwipeAction.COPY;
            if (slot == ONE_HAND && dir == Key.SWIPE_LEFT) return SwipeAction.ONE_HAND_LEFT;
            if (slot == ONE_HAND && dir == Key.SWIPE_RIGHT) return SwipeAction.ONE_HAND_RIGHT;
            if (dir == Key.SWIPE_DOWN) {
                if (slot == CLIPBOARD) return SwipeAction.PASTE;
                if (slot == EMOJI) return SwipeAction.EMOJI_LATEST;
                if (slot == UNDO) return SwipeAction.REDO;
            }
            return SwipeAction.NONE;
        }

        /** 모르는 값과 '문자 키를 트랙패드처럼' 쓰는 커서 자유 이동(문자 키 전용)은 기본값으로 본다. */
        @Override
        public String resolve(String stored, int slot, int dir) {
            if (stored == null || !SwipeAction.isKnown(stored) || SwipeAction.FREE_CURSOR.equals(stored)
                    || SwipeAction.globeUpOnly(stored)) {
                return defaultAction(slot, dir);
            }
            return stored;
        }

        @Override
        public String cardTitle(int slot) {
            return slot == BAR ? labels()[slot] : labels()[slot] + L.t(" 버튼", " button");
        }

        @Override
        public String cardHint(int slot) {
            if (slot == ONE_HAND) {
                return L.t("처음에는 왼쪽·오른쪽으로 밀면 그쪽 한 손 모드가 됩니다.", "By default, swiping left or right turns on one-handed mode on that side.");
            }
            return slot == BAR
                    ? L.t("도구 막대의 빈 곳뿐만 아니라 추천 단어 위나 도구 막대의 버튼 위에서 시작해도 도구 막대 밀기가 됩니다.", "Toolbar swipes also work when they start on a suggestion or a toolbar button, not only on empty space.")
                    : "";
        }

        @Override
        public int[] dirs(int slot) {
            return SwipeCustom.DIRS;
        }

        @Override
        public String[] choices(int slot) {
            return SwipeAction.idsForToolbar();
        }
    };

    /** 도구 막대 길게 누르기를 저장하는 칸의 방향 자리 (방향 자리 하나를 '길게 누르기'로 빌려 쓴다). */
    public static final int LONG = Key.SWIPE_DOWN;

    /**
     * 도구 막대의 빈 공간과 도구 막대 버튼을 길게 누를 때 실행할 기능 (스페이스바 길게 누르기처럼). 칸은 밀기({@link #SPEC})와
     * 같고, 저장 키는 같은 묶음의 "swipe_toolbar_칸_long"이라 도구 막대·버튼 설정 되돌리기가 함께 지운다. 처음에는 모두 없음이다.
     */
    public static final SwipeSpec LONG_PRESS = new SwipeSpec("toolbar", SLOT_NAMES) {
        @Override
        public String defaultAction(int slot, int dir) {
            return SwipeAction.NONE;
        }

        /** 모르는 값과 고를 수 없는 기능(누르고 있는 동안 되풀이하는 지우기 등)은 없음으로 본다. */
        @Override
        public String resolve(String stored, int slot, int dir) {
            if (stored == null) return SwipeAction.NONE;
            for (String id : choices(slot)) if (id.equals(stored)) return stored;
            return SwipeAction.NONE;
        }

        @Override
        protected String dirKey(int slot, int dir) {
            return "long";
        }

        @Override
        public String cardTitle(int slot) {
            return SPEC.cardTitle(slot);
        }

        @Override
        public String cardHint(int slot) {
            return slot == BAR ? L.t("빈 공간을 길게 누를 때", "Long press on empty space") : L.t("길게 누를 때", "Long press");
        }

        @Override
        public int[] dirs(int slot) {
            return new int[]{LONG};
        }

        @Override
        public String dirLabel(int dir) {
            return L.t("길게 누르기", "Long press");
        }

        /** 스페이스바 길게 누르기와 같은 목록 ('밀어서 선택' 포함). 누르고 있는 동안 되풀이하는 지우기는 뺀다. */
        @Override
        public String[] choices(int slot) {
            java.util.List<String> ids = new java.util.ArrayList<>();
            for (String id : SwipeAction.idsForSpaceLongPress()) if (!SwipeAction.holdRepeat(id)) ids.add(id);
            return ids.toArray(new String[0]);
        }

        @Override
        public String dialogTitle(int slot, int dir) {
            return slot == BAR ? L.t("도구 막대 빈 공간 길게 누르기", "Long press empty toolbar space")
                    : L.t("'" + SPEC.cardTitle(slot) + "' 길게 누르기", "Long press '" + SPEC.cardTitle(slot) + "'");
        }
    };

    private final String[][] table;

    private ToolbarSwipes(String[][] table) {
        this.table = table;
    }

    /** overrides: (칸 이름, 방향 이름) → 저장된 값 (정하지 않았으면 null). */
    static ToolbarSwipes from(BiFunction<String, String, String> overrides) {
        return new ToolbarSwipes(SPEC.table(overrides));
    }

    public static ToolbarSwipes load(Prefs prefs) {
        return new ToolbarSwipes(SPEC.table(prefs));
    }

    /**
     * 예전 기본 설정 스위치로 만들던 배치 (예전 설정을 옮길 때 쓴다, LegacySwipeSettings): '도구 막대를 밀어서 커서 이동'(barSwipe), '클립보드 버튼을 밀어서 복사·붙여넣기'
     * (clipboard), '이모지 버튼을 아래로 밀어서 입력'(emojiDown), '실행 취소 버튼을 아래로 밀어서 다시 실행'(undoDown)을
     * 켠 것만 기본 동작을 쓰고 나머지는 없음.
     */
    public static ToolbarSwipes standard(boolean barSwipe, boolean clipboard, boolean emojiDown, boolean undoDown) {
        String[][] t = SPEC.table((BiFunction<String, String, String>) null);
        for (int slot = 0; slot < t.length; slot++) {
            boolean on = slot == BAR ? barSwipe
                    : slot == CLIPBOARD ? clipboard
                    : slot == EMOJI ? emojiDown
                    : slot == UNDO && undoDown;
            if (!on) java.util.Arrays.fill(t[slot], SwipeAction.NONE);
        }
        // 한 손 모드 버튼은 스위치와 상관없이 좌우로 밀면 그쪽 한 손 모드가 되었다.
        t[ONE_HAND][Key.SWIPE_LEFT] = SwipeAction.ONE_HAND_LEFT;
        t[ONE_HAND][Key.SWIPE_RIGHT] = SwipeAction.ONE_HAND_RIGHT;
        return new ToolbarSwipes(t);
    }

    public String action(int slot, int dir) {
        return table[slot][dir];
    }

    /** slot의 칸을 가로로 밀어 커서 자유 이동을 하는지. */
    public boolean cursorH(int slot) {
        return SwipeAction.CURSOR_MOVE.equals(table[slot][Key.SWIPE_LEFT])
                || SwipeAction.CURSOR_MOVE.equals(table[slot][Key.SWIPE_RIGHT]);
    }

    /** slot의 칸을 세로로 밀어 줄 단위로 커서 자유 이동을 하는지. */
    public boolean cursorV(int slot) {
        return SwipeAction.CURSOR_MOVE.equals(table[slot][Key.SWIPE_UP])
                || SwipeAction.CURSOR_MOVE.equals(table[slot][Key.SWIPE_DOWN]);
    }
}
