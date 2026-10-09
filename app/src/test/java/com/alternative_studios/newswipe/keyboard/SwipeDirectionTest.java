package com.alternative_studios.newswipe.keyboard;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class SwipeDirectionTest {
    @Test
    public void straightSwipesKeepTheirDirection() {
        assertEquals(Key.SWIPE_RIGHT, SwipeDirection.of(30, 0));
        assertEquals(Key.SWIPE_LEFT, SwipeDirection.of(-30, 0));
        assertEquals(Key.SWIPE_DOWN, SwipeDirection.of(0, 30));
        assertEquals(Key.SWIPE_UP, SwipeDirection.of(0, -30));
    }

    @Test
    public void sidewaysSwipeThatDriftsDownStillCountsAsSideways() {
        assertEquals(Key.SWIPE_RIGHT, SwipeDirection.of(30, 30));    // 45도: 이전에는 아래가 이길 수 있었다
        assertEquals(Key.SWIPE_RIGHT, SwipeDirection.of(22, 30));
        assertEquals(Key.SWIPE_LEFT, SwipeDirection.of(-20, 30));
        assertEquals(Key.SWIPE_RIGHT, SwipeDirection.of(20, 29));    // 세로가 가로의 1.5배(30)보다 작다
    }

    @Test
    public void downNeedsToBeClearlyVertical() {
        assertEquals(Key.SWIPE_DOWN, SwipeDirection.of(20, 31));
        assertEquals(Key.SWIPE_DOWN, SwipeDirection.of(-10, 30));
    }

    @Test
    public void upIsOnlySlightlyMoreLenient() {
        assertEquals(Key.SWIPE_UP, SwipeDirection.of(20, -25));
        assertEquals(Key.SWIPE_RIGHT, SwipeDirection.of(20, -23));
    }

    @Test
    public void ratiosCanBeChanged() {
        assertEquals(Key.SWIPE_DOWN, SwipeDirection.of(30, 31, 1.0f, 1.0f));   // 100%: 더 많이 움직인 쪽
        assertEquals(Key.SWIPE_RIGHT, SwipeDirection.of(30, 31, 2.0f, 1.0f));  // 200%: 아래는 두 배 넘어야
        assertEquals(Key.SWIPE_DOWN, SwipeDirection.of(10, 31, 2.0f, 1.0f));
        assertEquals(Key.SWIPE_RIGHT, SwipeDirection.of(30, -31, 1.0f, 2.0f));
        assertEquals(Key.SWIPE_UP, SwipeDirection.of(10, -31, 1.0f, 2.0f));
    }
}
