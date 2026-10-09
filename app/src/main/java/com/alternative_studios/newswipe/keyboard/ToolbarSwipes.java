package com.alternative_studios.newswipe.keyboard;

import com.alternative_studios.newswipe.Prefs;

import java.util.function.BiFunction;

/**
 * 도구 막대 밀어서 기능 완전 사용자화. 켜면 도구 막대 자체와 도구 막대의 버튼을
 * 위·아래·왼쪽·오른쪽으로 밀 때 실행할 기능을 직접 정한다 (기능 목록은 {@link SwipeAction}).
 * 정하지 않은 방향은 기본 동작을 따른다: 도구 막대 왼쪽 = 줄 맨 앞, 오른쪽 = 줄 맨 뒤, 위·아래 = 한 줄 위·아래,
 * 클립보드 버튼 위 = 복사, 아래 = 붙여넣기, 이모지 버튼 아래 = 최근 이모지, 실행 취소 버튼 아래 = 다시 실행,
 * 한 손 모드 버튼 왼쪽·오른쪽 = 그쪽 한 손 모드, 나머지는 없음 (버튼에서 '없음'인 방향은 도구 막대를 민 것으로 본다).
 */
public final class ToolbarSwipes {
    /** 도구 막대 자체. 네 방향을 모두 쓴다. */
    public static final int BAR = 0;
    /** 도구 막대의 버튼들. 위·아래만 쓴다. */
    public static final int CLIPBOARD = 1, EMOJI = 2, VOICE = 3, UNDO = 4, SETTINGS = 5, HIDE = 6, ONE_HAND = 7;
    private static final String[] LABELS = {"도구 막대", "클립보드", "이모지", "음성 입력", "실행 취소", "설정", "키보드 숨기기",
            "한 손 모드"};
    public static final SwipeSpec SPEC = new SwipeSpec("toolbar",
            new String[]{"bar", "clipboard", "emoji", "voice", "undo", "settings", "hide", "onehand"}) {
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

        /** 모르는 값과 커서 자유 이동(스페이스바·문자 키 전용)은 기본값으로 본다. */
        @Override
        public String resolve(String stored, int slot, int dir) {
            if (stored == null || !SwipeAction.isKnown(stored) || SwipeAction.CURSOR_MOVE.equals(stored)
                    || SwipeAction.FREE_CURSOR.equals(stored)) {
                return defaultAction(slot, dir);
            }
            return stored;
        }

        @Override
        public String title() {
            return "도구 막대 밀어서 기능 편집";
        }

        @Override
        public String note() {
            return "도구 막대를 밀거나 도구 막대의 버튼을 위·아래·왼쪽·오른쪽으로 밀 때 실행할 기능을 고릅니다. "
                    + "'없음'으로 둔 방향으로 밀면 도구 막대를 그 방향으로 민 것으로 봅니다.";
        }

        @Override
        public String cardTitle(int slot) {
            return slot == BAR ? LABELS[slot] : LABELS[slot] + " 버튼";
        }

        @Override
        public String cardHint(int slot) {
            if (slot == ONE_HAND) {
                return "처음에는 왼쪽·오른쪽으로 밀면 그쪽 한 손 모드가 됩니다.";
            }
            return slot == BAR
                    ? "도구 막대의 빈 곳뿐만 아니라 추천 단어 위나 도구 막대의 버튼 위에서 시작해도 도구 막대 밀기가 됩니다."
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
     * 완전 사용자화를 껐을 때의 배치: '도구 막대를 밀어서 커서 이동'(barSwipe), '클립보드 버튼을 밀어서 복사·붙여넣기'
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
        // 한 손 모드 버튼은 완전 사용자화를 꺼 둬도 좌우로 밀면 그쪽 한 손 모드가 된다.
        t[ONE_HAND][Key.SWIPE_LEFT] = SwipeAction.ONE_HAND_LEFT;
        t[ONE_HAND][Key.SWIPE_RIGHT] = SwipeAction.ONE_HAND_RIGHT;
        return new ToolbarSwipes(t);
    }

    public String action(int slot, int dir) {
        return table[slot][dir];
    }
}
