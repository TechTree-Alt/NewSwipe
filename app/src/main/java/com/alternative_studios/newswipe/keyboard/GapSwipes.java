package com.alternative_studios.newswipe.keyboard;

import com.alternative_studios.newswipe.Prefs;

import java.util.function.BiFunction;

/**
 * 분리 키보드 가운데 빈 공간 밀기: 도구 막대처럼 위·아래·왼쪽·오른쪽으로 밀 때 실행할 기능을 직접 정한다
 * (기능 목록은 {@link SwipeAction}). 기본은 네 방향 모두 '커서 자유 이동'이다.
 * 가로 모드·폴더블 내부 화면 세로·가로가 각각 따로 정하므로 칸이 프로필마다 하나씩 있다 ({@link Prefs#gapSlot()}).
 */
public final class GapSwipes {
    public static final String[] SLOTS = {"portrait", "landscape", "large_port", "large_land"};
    private static final String[] LABELS = {"가운데 공간", "가운데 공간 (가로 모드)", "가운데 공간 (폴더블 세로)",
            "가운데 공간 (폴더블 가로)"};

    public static final SwipeSpec SPEC = new SwipeSpec("gap", SLOTS) {
        @Override
        public String defaultAction(int slot, int dir) {
            return SwipeAction.CURSOR_MOVE;
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
        public String title() {
            return "가운데 공간 밀어서 기능 사용자화";
        }

        @Override
        public String note() {
            return "분리 키보드 가운데의 빈 공간을 위·아래·왼쪽·오른쪽으로 밀 때 실행할 기능을 고릅니다.";
        }

        @Override
        public String cardTitle(int slot) {
            return LABELS[slot];
        }

        @Override
        public String cardHint(int slot) {
            return "";
        }

        @Override
        public String[] choices(int slot) {
            return SwipeAction.idsFor(-1);
        }
    };

    private final String[] row;

    private GapSwipes(String[] row) {
        this.row = row;
    }

    /**
     * 이 프로필(slot)의 배치. 예전의 '밀어서 커서 이동' 스위치를 껐던 프로필은 정하지 않은 방향을 없음으로 둔다.
     *
     * @param legacyOff 예전 스위치가 꺼져 있었는지
     */
    public static GapSwipes load(Prefs prefs, int slot, boolean legacyOff) {
        BiFunction<String, String, String> overrides = (s, d) -> {
            String stored = prefs.swipeActionOverride(SPEC.group, s, d);
            return stored == null && legacyOff ? SwipeAction.NONE : stored;
        };
        return new GapSwipes(SPEC.table(overrides)[slot]);
    }

    public String action(int dir) {
        return row[dir];
    }

    public boolean cursorH() {
        return SwipeAction.CURSOR_MOVE.equals(row[Key.SWIPE_LEFT]) || SwipeAction.CURSOR_MOVE.equals(row[Key.SWIPE_RIGHT]);
    }

    public boolean cursorV() {
        return SwipeAction.CURSOR_MOVE.equals(row[Key.SWIPE_UP]) || SwipeAction.CURSOR_MOVE.equals(row[Key.SWIPE_DOWN]);
    }

    /** 어느 방향이든 정해 둔 기능이 있는지 (없으면 가운데 공간은 아무 일도 하지 않는다). */
    public boolean any() {
        for (String a : row) if (!SwipeAction.NONE.equals(a)) return true;
        return false;
    }
}
