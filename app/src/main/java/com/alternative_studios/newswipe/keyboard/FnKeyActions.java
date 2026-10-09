package com.alternative_studios.newswipe.keyboard;

import com.alternative_studios.newswipe.Prefs;

import java.util.ArrayList;
import java.util.List;

/**
 * Fn 키: 한글 자판에서 Shift 키를 끄고 'Shift 키 대신 Fn 키 사용'을 켜면 Shift 자리에 놓이는 키.
 * 탭·길게 누르기·위·아래·왼쪽·오른쪽으로 밀기마다 실행할 기능을 직접 정한다 (기능 목록은 {@link SwipeAction}).
 * 밀어서 기능 편집과 같은 틀({@link SwipeSpec})을 쓰고, '누르기' 칸에는 방향 자리 두 개를 탭·길게 누르기로 쓴다.
 */
public final class FnKeyActions {
    /** 칸: 누르기(탭·길게 누르기), 밀기(네 방향). */
    public static final int PRESS = 0, SWIPE = 1;
    /** '누르기' 칸에서 방향 자리를 빌려 쓰는 탭·길게 누르기. */
    public static final int TAP = Key.SWIPE_DOWN, LONG = Key.SWIPE_UP;

    public static final SwipeSpec SPEC = new SwipeSpec("fk", new String[]{"press", "swipe"}) {
        @Override
        public String defaultAction(int slot, int dir) {
            return slot == PRESS && dir == TAP ? SwipeAction.CLIPBOARD_OPEN : SwipeAction.NONE;
        }

        /** 모르는 값은 기본값으로, 고를 수 없는 기능(스페이스바 전용 커서 이동 등)은 없음으로 본다. */
        @Override
        public String resolve(String stored, int slot, int dir) {
            if (stored == null || !SwipeAction.isKnown(stored)) return defaultAction(slot, dir);
            for (String id : choices(slot, dir)) if (id.equals(stored)) return stored;
            return SwipeAction.NONE;
        }

        @Override
        protected String dirKey(int slot, int dir) {
            if (slot == PRESS) return dir == LONG ? "long" : "tap";
            return super.dirKey(slot, dir);
        }

        @Override
        public String title() {
            return "Fn 키 사용자화";
        }

        @Override
        public String note() {
            return "한글 자판의 Shift 자리에 놓인 Fn 키를 탭하거나 길게 누르거나 밀 때 실행할 기능을 고릅니다.";
        }

        @Override
        public String cardTitle(int slot) {
            return slot == PRESS ? "Fn 키 누르기" : "Fn 키 밀기";
        }

        @Override
        public String cardHint(int slot) {
            return slot == PRESS ? "탭하거나 길게 누를 때" : "위·아래·왼쪽·오른쪽으로 밀 때";
        }

        @Override
        public int[] dirs(int slot) {
            return slot == PRESS ? new int[]{TAP, LONG} : SwipeCustom.DIRS;
        }

        @Override
        public String dirLabel(int slot, int dir) {
            if (slot == PRESS) return dir == LONG ? "길게 누르기" : "탭";
            return dirLabel(dir);
        }

        @Override
        public String[] choices(int slot) {
            return SwipeAction.idsForToolbar();
        }

        /** 길게 누르기에는 '계속 지우기'도 고를 수 있다. 한글 자판에만 있는 키라 Caps Lock은 뺀다. */
        @Override
        public String[] choices(int slot, int dir) {
            if (slot != PRESS || dir != LONG) return choices(slot);
            List<String> ids = new ArrayList<>();
            for (String id : SwipeAction.idsForLongPress()) if (!SwipeAction.CAPS_LOCK.equals(id)) ids.add(id);
            return ids.toArray(new String[0]);
        }

        @Override
        public String dialogTitle(int slot, int dir) {
            if (slot == PRESS) return "Fn 키 " + (dir == LONG ? "길게 누르기" : "탭");
            return "Fn 키 " + SwipeCustom.label(dir) + "으로 밀기";
        }
    };

    /** {@link #actions}의 배열에서 탭·길게 누르기 자리. 밀기는 2 + 방향(Key.SWIPE_*). */
    public static final int I_TAP = 0, I_LONG = 1, I_SWIPE = 2;

    private FnKeyActions() {
    }

    /** 설정의 배치로 만든 {탭, 길게 누르기, 아래, 위, 왼쪽, 오른쪽 밀기} 기능 ID 배열. */
    public static String[] actions(Prefs prefs) {
        String[][] t = SPEC.table(prefs);
        String[] out = new String[I_SWIPE + 4];
        out[I_TAP] = t[PRESS][TAP];
        out[I_LONG] = t[PRESS][LONG];
        for (int dir = 0; dir < 4; dir++) out[I_SWIPE + dir] = t[SWIPE][dir];
        return out;
    }
}
