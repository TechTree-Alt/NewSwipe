package com.alternative_studios.newswipe.keyboard;

import com.alternative_studios.newswipe.Prefs;

import java.util.function.BiFunction;

/**
 * 키보드 밀기: 자판 위를 한 손가락·두 손가락으로 위·아래·왼쪽·오른쪽으로 밀 때 실행할 기능 (기능 목록은 {@link SwipeAction}).
 * <ul>
 *   <li>한 손가락: 문자 키에서 밀기 시작했는데 그 방향에 밀어서 입력할 글자가 없을 때.
 *       '커서 자유 이동'을 고르면 '문자 키를 밀어서 커서 이동'과 똑같이 커서를 옮긴다.</li>
 *   <li>두 손가락: 두 손가락을 함께 같은 방향으로 밀 때. 그동안 누른 키는 입력하지 않는다.</li>
 * </ul>
 * 완전 사용자화를 끄면 '두 손가락으로 밀어서 실행 취소'만 쓴다 (왼쪽 = 실행 취소, 오른쪽 = 다시 실행).
 * 완전 사용자화에서 정하지 않은 칸도 이 기본값을 따른다.
 */
public final class KeyboardSwipes {
    public static final int ONE = 0, TWO = 1;
    private static final String[] LABELS = {"한 손가락", "두 손가락"};
    private static final String[] HINTS = {
            "'커서 자유 이동'을 고르면 문자 키를 트랙패드처럼 씁니다.",
            "두 손가락을 함께 같은 방향으로 밀 때.",
    };

    public static final SwipeSpec SPEC = new SwipeSpec("kb", new String[]{"one", "two"}) {
        /** 두 손가락 왼쪽 = 실행 취소, 오른쪽 = 다시 실행. */
        @Override
        public String defaultAction(int slot, int dir) {
            if (slot == TWO && dir == Key.SWIPE_LEFT) return SwipeAction.UNDO;
            if (slot == TWO && dir == Key.SWIPE_RIGHT) return SwipeAction.REDO;
            return SwipeAction.NONE;
        }

        /** 모르는 값, '커서 자유 이동 (스페이스바)', 두 손가락의 '커서 자유 이동'(한 손가락 전용)은 기본값으로 본다. */
        @Override
        public String resolve(String stored, int slot, int dir) {
            if (stored == null || !SwipeAction.isKnown(stored) || SwipeAction.CURSOR_MOVE.equals(stored)
                    || (slot != ONE && SwipeAction.FREE_CURSOR.equals(stored))) {
                return defaultAction(slot, dir);
            }
            return stored;
        }

        @Override
        public String title() {
            return "키보드 밀기 편집";
        }

        @Override
        public String note() {
            return "자판을 한 손가락·두 손가락으로 위·아래·왼쪽·오른쪽으로 밀 때 실행할 기능을 고릅니다.";
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
            return SwipeAction.idsForKeyboard(slot == ONE);
        }

        @Override
        public String dialogTitle(int slot, int dir) {
            return LABELS[slot] + "으로 " + SwipeCustom.label(dir) + "으로 밀기";
        }
    };

    private final String[][] table;

    private KeyboardSwipes(String[][] table) {
        this.table = table;
    }

    /** overrides: (칸 이름, 방향 이름) → 저장된 값 (정하지 않았으면 null). */
    static KeyboardSwipes from(BiFunction<String, String, String> overrides) {
        return new KeyboardSwipes(SPEC.table(overrides));
    }

    /** 완전 사용자화를 켰을 때의 배치. */
    public static KeyboardSwipes load(Prefs prefs) {
        return new KeyboardSwipes(SPEC.table(prefs));
    }

    /** 완전 사용자화를 껐을 때: 두 손가락 실행 취소·다시 실행만. */
    public static KeyboardSwipes twoFingerUndo() {
        return from(null);
    }

    public String action(int slot, int dir) {
        return table[slot][dir];
    }

    /** 이 손가락 수로 미는 기능이 하나라도 있는지. */
    public boolean uses(int slot) {
        for (String a : table[slot]) if (!SwipeAction.NONE.equals(a)) return true;
        return false;
    }

    /** 한 손가락으로 이 축(가로면 true)을 밀어 커서 자유 이동을 하는지. */
    public boolean freeCursor(boolean horizontal) {
        return horizontal
                ? SwipeAction.FREE_CURSOR.equals(table[ONE][Key.SWIPE_LEFT])
                        || SwipeAction.FREE_CURSOR.equals(table[ONE][Key.SWIPE_RIGHT])
                : SwipeAction.FREE_CURSOR.equals(table[ONE][Key.SWIPE_UP])
                        || SwipeAction.FREE_CURSOR.equals(table[ONE][Key.SWIPE_DOWN]);
    }
}
