package com.alternative_studios.newswipe.keyboard;

import com.alternative_studios.newswipe.Prefs;

import java.util.function.BiFunction;

/**
 * 분리 키보드 가운데 빈 공간 밀기: 도구 막대처럼 위·아래·왼쪽·오른쪽으로 밀 때 실행할 기능을 직접 정한다
 * (기능 목록은 {@link SwipeAction}). 기본은 네 방향 모두 '커서 자유 이동'이다.
 * 가로 모드·폴더블 내부 화면 세로·가로가 각각 따로 정하므로 칸이 프로필마다 하나씩 있다 ({@link Prefs#gapSlot()}).
 */
public final class GapSwipes {
    /** 프로필마다의 밀기 칸 이름 (Prefs.gapSlot() 차례). 누르기 칸은 그 뒤에 "_press"를 붙인 이름으로 같은 차례에 이어진다. */
    public static final String[] SLOTS = {"portrait", "landscape", "large_port", "large_land"};
    private static final String[] LABELS = {"가운데 공간", "가운데 공간 (가로 모드)", "가운데 공간 (폴더블 세로)",
            "가운데 공간 (폴더블 가로)"};
    /** 누르기 칸에서 방향 자리를 빌려 쓰는 누르기·길게 누르기 (Fn 키와 같다). */
    public static final int TAP = Key.SWIPE_DOWN, LONG = Key.SWIPE_UP;
    private static final int N = SLOTS.length;

    private static String[] allSlots() {
        String[] all = new String[N * 2];
        for (int i = 0; i < N; i++) {
            all[i] = SLOTS[i];
            all[N + i] = SLOTS[i] + "_press";
        }
        return all;
    }

    /** 밀기 칸 번호(0~3)에 해당하는 누르기 칸 번호. */
    public static int pressSlot(int swipeSlot) {
        return N + swipeSlot;
    }

    private static boolean isPress(int slot) {
        return slot >= N;
    }

    public static final SwipeSpec SPEC = new SwipeSpec("gap", allSlots()) {
        @Override
        public String defaultAction(int slot, int dir) {
            return isPress(slot) ? SwipeAction.NONE : SwipeAction.CURSOR_MOVE;
        }

        @Override
        protected String dirKey(int slot, int dir) {
            if (isPress(slot)) return dir == LONG ? "long" : dir == TAP ? "tap" : super.dirKey(slot, dir);
            return super.dirKey(slot, dir);
        }

        @Override
        public int[] dirs(int slot) {
            return isPress(slot) ? new int[]{TAP, LONG} : SwipeCustom.DIRS;
        }

        @Override
        public String dirLabel(int slot, int dir) {
            if (isPress(slot)) return dir == LONG ? "길게 누르기" : "누르기";
            return dirLabel(dir);
        }

        @Override
        public String dialogTitle(int slot, int dir) {
            if (isPress(slot)) return "가운데 공간 " + (dir == LONG ? "길게 누르기" : "누르기");
            return super.dialogTitle(slot, dir);
        }

        /** 모르는 값과 '문자 키를 트랙패드처럼' 쓰는 커서 자유 이동(문자 키 전용)은 기본값으로 본다. */
        @Override
        public String resolve(String stored, int slot, int dir) {
            if (isPress(slot)) {
                if (stored == null || !SwipeAction.isKnown(stored)) return SwipeAction.NONE;
                for (String id : choices(slot, dir)) if (id.equals(stored)) return stored;
                return SwipeAction.NONE;
            }
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

        /** 누르기에는 끌어 쓰는 '커서 자유 이동'을 뺀다. 길게 누르기에는 '계속 지우기'와 '길게 누른 뒤 밀어서 글자 선택'도 고를 수 있다 (Caps Lock은 뺀다). */
        @Override
        public String[] choices(int slot, int dir) {
            if (!isPress(slot)) return choices(slot);
            java.util.List<String> ids = new java.util.ArrayList<>();
            if (dir == LONG) {
                for (String id : SwipeAction.idsForSpaceLongPress()) if (!SwipeAction.CAPS_LOCK.equals(id)) ids.add(id);
            } else {
                for (String id : choices(slot)) if (!SwipeAction.CURSOR_MOVE.equals(id)) ids.add(id);
            }
            return ids.toArray(new String[0]);
        }
    };

    private final String[] row;
    private final String tap, longPress;

    private GapSwipes(String[] row, String[] press) {
        this.row = row;
        this.tap = press[TAP];
        this.longPress = press[LONG];
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
        String[][] t = SPEC.table(overrides);
        return new GapSwipes(t[slot], t[pressSlot(slot)]);
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

    /** 누르기 기능. 없음이면 null. */
    public String tap() {
        return SwipeAction.NONE.equals(tap) ? null : tap;
    }

    /** 길게 누르기 기능. 없음이면 null. */
    public String longPress() {
        return SwipeAction.NONE.equals(longPress) ? null : longPress;
    }

    /** 밀기·누르기·길게 누르기 중 정해 둔 기능이 있는지 (없으면 가운데 공간은 아무 일도 하지 않는다). */
    public boolean any() {
        if (tap() != null || longPress() != null) return true;
        for (String a : row) if (!SwipeAction.NONE.equals(a)) return true;
        return false;
    }
}
