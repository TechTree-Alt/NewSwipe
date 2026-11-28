package com.alternative_studios.newswipe.keyboard;

import com.alternative_studios.newswipe.L;

/**
 * '길게 누른 뒤 밀어 기능 실행': 길게 누르기 칸에 {@link SwipeAction#LONG_SWIPE}를 정한 키·버튼은 길게 눌러 진동이 온 뒤
 * 손을 떼지 않고 위·아래·왼쪽·오른쪽으로 밀어, 손을 뗄 때 그 방향에 정한 기능을 실행한다.
 * <p>
 * 방향마다의 기능은 길게 누르기 칸과 같은 묶음·칸 이름에 저장한다: 저장 키는
 * "swipe_" + 묶음 + "_" + 칸 + "_" + 길게 누르기 자리 이름 + "_ls_" + 방향 이름이다. 그래서 그 묶음을 되돌리면 함께 지워지고,
 * 키 창의 범위(Prefs.EditorScope)도 같은 앞글자로 함께 잡힌다. 처음에는 네 방향 모두 없음이다.
 * <p>
 * 길게 누르기 배치마다 이렇게 만든 배치를 LONG_SWIPES로 둔다 ({@link LongPressActions#LONG_SWIPES} 등). 칸 × 방향 표는
 * {@link SwipeSpec#table(com.alternative_studios.newswipe.Prefs)}로 읽는다.
 */
public final class LongSwipes {
    private LongSwipes() {
    }

    /**
     * 길게 누르기 배치 base의 baseDir 자리(길게 누르기)에 딸린 네 방향 배치. 칸은 base와 같고, 방향은 Key.SWIPE_*를 그대로 쓴다.
     */
    public static SwipeSpec of(SwipeSpec base, int baseDir) {
        return new SwipeSpec(base.group, base.slotNames()) {
            @Override
            public String defaultAction(int slot, int dir) {
                return SwipeAction.NONE;
            }

            @Override
            public String resolve(String stored, int slot, int dir) {
                if (stored == null) return SwipeAction.NONE;
                for (String id : choices(slot)) if (id.equals(stored)) return stored;
                return SwipeAction.NONE;
            }

            @Override
            protected String dirKey(int slot, int dir) {
                return base.storedDirKey(slot, baseDir) + "_ls_" + SwipeCustom.dirName(dir);
            }

            @Override
            public String cardTitle(int slot) {
                return base.cardTitle(slot);
            }

            @Override
            public String cardHint(int slot) {
                return "";
            }

            @Override
            public String[] choices(int slot) {
                return SwipeAction.idsForLongSwipe();
            }

            @Override
            public String dialogTitle(int slot, int dir) {
                String d = SwipeCustom.label(dir);
                return L.t(base.dialogTitle(slot, baseDir) + " 뒤 " + d + "으로 밀기",
                        base.dialogTitle(slot, baseDir) + ", then swipe " + d.toLowerCase(java.util.Locale.ROOT));
            }
        };
    }
}
