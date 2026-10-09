package com.alternative_studios.newswipe.ui;

import android.annotation.SuppressLint;
import android.content.Context;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.widget.LinearLayout;

import com.alternative_studios.newswipe.keyboard.Key;

import java.util.function.BiPredicate;

/**
 * 위·아래·왼쪽·오른쪽으로 미는 손짓을 알아채는 가로 줄(도구 막대). 안에 있는 버튼이나 추천 위에서 시작해도
 * 충분히 밀면 손짓으로 받고, 안쪽 버튼의 누르기는 취소한다. 켜지 않은 방향은 터치를 건드리지 않고 그대로 안쪽에 전달한다.
 * 위·아래로 밀기를 직접 처리하는 버튼({@link #setVerticalOwner}) 위에서 시작한 그 방향 밀기는 그 버튼에 맡긴다.
 */
@SuppressLint("ViewConstructor")
public final class SwipeStrip extends LinearLayout {
    public interface Listener {
        /** @param dir Key.SWIPE_UP·SWIPE_DOWN·SWIPE_LEFT·SWIPE_RIGHT */
        void onSwipe(int dir);
    }

    private final int captureAt;
    private Listener listener;
    /** Key.SWIPE_* 순서로, 그 방향 밀기를 받는지. */
    private final boolean[] dirs = new boolean[4];
    private float distance;
    private BiPredicate<View, Integer> verticalOwner = (v, dir) -> false;
    private java.util.function.Predicate<View> horizontalOwner = v -> false;
    /** 이번 터치를 시작한 버튼이 좌우 밀기를 직접 처리하는지. */
    private boolean ownedHorizontal;
    private float downX, downY;
    private boolean tracking, captured, fired;
    /** 이번 터치를 시작한 버튼이 위(0)·아래(1) 밀기를 직접 처리하는지. */
    private final boolean[] owned = new boolean[2];

    public SwipeStrip(Context context) {
        super(context);
        // 손가락이 살짝 흔들리는 것(눌러서 누르기)과 구분하려고 터치 문턱의 두 배부터 가로채기 시작한다.
        captureAt = ViewConfiguration.get(context).getScaledTouchSlop() * 2;
    }

    public void setListener(Listener l) {
        listener = l;
    }

    /** 이 버튼이 이 방향(Key.SWIPE_UP·SWIPE_DOWN) 밀기를 직접 처리하는지 (터치할 때마다 묻는다). */
    public void setVerticalOwner(BiPredicate<View, Integer> owner) {
        verticalOwner = owner == null ? (v, dir) -> false : owner;
    }

    /** 이 버튼이 좌우 밀기를 직접 처리하는지 (한 손 모드 버튼). 그 버튼 위에서 시작한 좌우 밀기는 버튼에 맡긴다. */
    public void setHorizontalOwner(java.util.function.Predicate<View> owner) {
        horizontalOwner = owner == null ? v -> false : owner;
    }

    /** @param distancePx 이만큼 밀면 한 번 알린다 */
    public void configure(boolean up, boolean down, boolean left, boolean right, float distancePx) {
        dirs[Key.SWIPE_UP] = up;
        dirs[Key.SWIPE_DOWN] = down;
        dirs[Key.SWIPE_LEFT] = left;
        dirs[Key.SWIPE_RIGHT] = right;
        this.distance = distancePx;
    }

    private boolean anyEnabled() {
        return dirs[0] || dirs[1] || dirs[2] || dirs[3];
    }

    /** 한 축으로 뚜렷하게 민 방향. 대각선이면 -1. */
    private static int direction(float dx, float dy) {
        if (Math.abs(dx) > Math.abs(dy) * 1.5f) return dx < 0 ? Key.SWIPE_LEFT : Key.SWIPE_RIGHT;
        if (Math.abs(dy) > Math.abs(dx) * 1.5f) return dy < 0 ? Key.SWIPE_UP : Key.SWIPE_DOWN;
        return -1;
    }

    /** 이번 터치에서 이 방향 밀기를 받는지. */
    private boolean accepts(int dir) {
        if (dir < 0 || !dirs[dir]) return false;
        if (dir == Key.SWIPE_UP) return !owned[0];
        if (dir == Key.SWIPE_DOWN) return !owned[1];
        return !ownedHorizontal;   // 좌우 밀기는 버튼 위에서 시작해도 도구 막대가 받는다 (좌우를 직접 처리하는 버튼은 빼고)
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        if (!anyEnabled()) return false;
        switch (ev.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                start(ev);
                return false;
            case MotionEvent.ACTION_MOVE:
                if (tracking && !captured) {
                    float dx = ev.getX() - downX, dy = ev.getY() - downY;
                    if (Math.max(Math.abs(dx), Math.abs(dy)) > captureAt && accepts(direction(dx, dy))) {
                        captured = true;
                        return true;   // 안쪽 버튼에는 취소가 전달되고, 이 뒤는 onTouchEvent가 받는다
                    }
                }
                return captured;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                reset();
                return false;
            default:
                return captured;
        }
    }

    @Override
    @SuppressLint("ClickableViewAccessibility")
    public boolean onTouchEvent(MotionEvent ev) {
        if (!anyEnabled()) return false;
        switch (ev.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                start(ev);   // 안쪽 버튼이 없는 빈 곳에서 시작한 경우: 이어서 받으려면 true를 돌려줘야 한다
                return true;
            case MotionEvent.ACTION_MOVE:
                if (!tracking) return false;
                float dx = ev.getX() - downX, dy = ev.getY() - downY;
                int dir = direction(dx, dy);
                if (!fired && accepts(dir) && Math.max(Math.abs(dx), Math.abs(dy)) >= distance) {
                    fired = true;
                    captured = true;
                    if (listener != null) listener.onSwipe(dir);
                }
                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                reset();
                return true;
            default:
                return true;
        }
    }

    private void start(MotionEvent ev) {
        downX = ev.getX();
        downY = ev.getY();
        tracking = true;
        captured = false;
        fired = false;
        View child = childAt(downX, downY);
        owned[0] = child != null && verticalOwner.test(child, Key.SWIPE_UP);
        owned[1] = child != null && verticalOwner.test(child, Key.SWIPE_DOWN);
        ownedHorizontal = child != null && horizontalOwner.test(child);
    }

    private View childAt(float x, float y) {
        for (int i = getChildCount() - 1; i >= 0; i--) {
            View c = getChildAt(i);
            if (c.getVisibility() == VISIBLE && x >= c.getLeft() && x < c.getRight()
                    && y >= c.getTop() && y < c.getBottom()) {
                return c;
            }
        }
        return null;
    }

    private void reset() {
        tracking = false;
        captured = false;
        fired = false;
        owned[0] = owned[1] = false;
        ownedHorizontal = false;
    }
}
