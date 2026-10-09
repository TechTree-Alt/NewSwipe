package com.alternative_studios.newswipe.keyboard;

/**
 * 글자 키를 민 손가락의 움직임(dx, dy: 오른쪽·아래가 +)이 어느 방향인지 정한다.
 *
 * <p>엄지로 좌우로 밀면 손가락이 아래로도 함께 처지는 일이 많아, 좌우를 후하게 본다:
 * 가로 움직임이 세로 움직임의 일정 비율만 되어도 좌우로 친다. 특히 아래는 오른쪽·왼쪽 밀기와 헷갈리기 쉬워
 * 아래로 훨씬 더 곧게 밀어야 아래로 친다. 위쪽은 비교적 덜 헷갈려서 조금만 후하게 본다.
 */
final class SwipeDirection {
    /** 세로 움직임이 가로 움직임의 몇 배를 넘어야 아래로 치는지의 기본값 (1이면 가로·세로가 같을 때 경계). */
    public static final float DOWN_OVER_HORIZONTAL = 1.5f;
    /** 위로 치는 데 필요한 같은 비율의 기본값. */
    public static final float UP_OVER_HORIZONTAL = 1.2f;

    private SwipeDirection() {
    }

    /** Key.SWIPE_LEFT·RIGHT·UP·DOWN 중 하나 (기본 비율). */
    static int of(float dx, float dy) {
        return of(dx, dy, DOWN_OVER_HORIZONTAL, UP_OVER_HORIZONTAL);
    }

    /**
     * @param downRatio 세로가 가로의 몇 배를 넘어야 아래로 치는지
     * @param upRatio   세로가 가로의 몇 배를 넘어야 위로 치는지
     */
    static int of(float dx, float dy, float downRatio, float upRatio) {
        float ax = Math.abs(dx), ay = Math.abs(dy);
        float need = dy > 0 ? downRatio : upRatio;
        if (ay > ax * need) return dy > 0 ? Key.SWIPE_DOWN : Key.SWIPE_UP;
        return dx > 0 ? Key.SWIPE_RIGHT : Key.SWIPE_LEFT;
    }
}
