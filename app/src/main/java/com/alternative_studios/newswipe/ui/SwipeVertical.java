package com.alternative_studios.newswipe.ui;

import android.annotation.SuppressLint;
import android.view.MotionEvent;
import android.view.View;

import com.alternative_studios.newswipe.keyboard.Key;

import java.util.function.IntConsumer;
import java.util.function.IntPredicate;
import java.util.function.IntSupplier;

/**
 * 버튼을 밀었을 때 한 번 실행하는 동작을 붙인다. 평소의 누르기(클릭)는 그대로 동작하고,
 * 동작이 있는 방향으로 충분히 밀면 버튼의 누르기는 취소하고 동작만 실행한다.
 */
public final class SwipeVertical {
    private SwipeVertical() {
    }

    /**
     * 네 방향(위·아래·왼쪽·오른쪽) 밀기를 붙인다. 가로로 뚜렷하게 밀면 좌우로 본다.
     *
     * @param enabled     방향(Key.SWIPE_*)마다 동작이 있는지. false인 방향으로 밀면 아무 일도 하지 않는다
     * @param thresholdPx 이만큼 밀면 실행한다 (enabled·thresholdPx는 터치할 때마다 읽으므로 설정이 바뀌면 바로 따른다)
     * @param action      민 방향(Key.SWIPE_*)을 받아 실행한다
     */
    public static void attachFourWay(View v, IntPredicate enabled, IntSupplier thresholdPx, IntConsumer action) {
        attachFourWay(v, enabled, thresholdPx, action, null);
    }

    /** 손을 뗄 때까지 끌어 쓰는 기능(커서 자유 이동)을 위한 연결. */
    public interface Drag {
        /** 이 방향 밀기가 끌어 쓰는 기능인지. */
        boolean isDrag(int dir);

        void start(int dir, float x, float y);

        void move(float x, float y);
    }

    /** @param drag 끌어 쓰는 방향이면 action 대신 이것을 부른다 (null이면 쓰지 않는다) */
    public static void attachFourWay(View v, IntPredicate enabled, IntSupplier thresholdPx, IntConsumer action,
                                     Drag drag) {
        attachFourWay(v, enabled, thresholdPx, action, drag, null);
    }

    /** 길게 누르기를 위한 연결. 손가락을 거의 움직이지 않고 정한 시간만큼 누르고 있으면 실행한다. */
    public interface Hold {
        /** 길게 누르기 기능이 있으면 기다릴 시간 (ms), 없으면 0 이하 (터치할 때마다 묻는다). */
        int delayMs();

        /** 길게 눌렀다 (버튼의 누르기는 취소된다). true를 돌려주면 손을 뗄 때까지 손가락 위치를 {@link #move}로 알린다. */
        boolean fire(float x, float y);

        void move(float x, float y);
    }

    /** @param hold 길게 누르기 (null이면 쓰지 않는다) */
    @SuppressLint("ClickableViewAccessibility")
    public static void attachFourWay(View v, IntPredicate enabled, IntSupplier thresholdPx, IntConsumer action,
                                     Drag drag, Hold hold) {
        final float[] down = new float[2];
        final float[] last = new float[2];
        final boolean[] fired = new boolean[1];
        final boolean[] dragging = new boolean[1];
        // 길게 누르기: [0] 기다리는 중, [1] 실행했다, [2] 실행한 뒤 손가락을 따라가는 중
        final boolean[] held = new boolean[3];
        final int slop = android.view.ViewConfiguration.get(v.getContext()).getScaledTouchSlop();
        final Runnable longPress = () -> {
            if (!held[0]) return;
            held[0] = false;
            held[1] = true;
            // 버튼의 누르기를 취소하고, 이어지는 손짓을 도구 막대가 밀기로 가로채지 않게 한다.
            MotionEvent cancel = MotionEvent.obtain(0, 0, MotionEvent.ACTION_CANCEL, last[0], last[1], 0);
            v.onTouchEvent(cancel);
            cancel.recycle();
            if (v.getParent() != null) v.getParent().requestDisallowInterceptTouchEvent(true);
            held[2] = hold.fire(last[0], last[1]);
        };
        v.setOnTouchListener((view, ev) -> {
            last[0] = ev.getX();
            last[1] = ev.getY();
            switch (ev.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    down[0] = ev.getX();
                    down[1] = ev.getY();
                    fired[0] = false;
                    dragging[0] = false;
                    held[0] = held[1] = held[2] = false;
                    view.removeCallbacks(longPress);
                    if (hold != null) {
                        int ms = hold.delayMs();
                        if (ms > 0) {
                            held[0] = true;
                            view.postDelayed(longPress, ms);
                        }
                    }
                    return false;
                case MotionEvent.ACTION_MOVE: {
                    if (held[1]) {
                        if (held[2]) hold.move(ev.getX(), ev.getY());
                        return true;
                    }
                    if (held[0] && Math.max(Math.abs(ev.getX() - down[0]), Math.abs(ev.getY() - down[1])) > slop) {
                        held[0] = false;   // 밀기 시작: 길게 누르기가 아니다
                        view.removeCallbacks(longPress);
                    }
                    if (dragging[0]) {
                        drag.move(ev.getX(), ev.getY());
                        return true;
                    }
                    if (fired[0]) return true;
                    float dx = ev.getX() - down[0], dy = ev.getY() - down[1];
                    int dir;
                    if (Math.abs(dx) > Math.abs(dy) * 1.5f) dir = dx < 0 ? Key.SWIPE_LEFT : Key.SWIPE_RIGHT;
                    else if (Math.abs(dy) > Math.abs(dx) * 1.5f) dir = dy < 0 ? Key.SWIPE_UP : Key.SWIPE_DOWN;
                    else return false;
                    if (Math.max(Math.abs(dx), Math.abs(dy)) <= thresholdPx.getAsInt() || !enabled.test(dir)) return false;
                    fired[0] = true;
                    MotionEvent cancel = MotionEvent.obtain(ev);
                    cancel.setAction(MotionEvent.ACTION_CANCEL);
                    view.onTouchEvent(cancel);
                    cancel.recycle();
                    if (drag != null && drag.isDrag(dir)) {
                        dragging[0] = true;
                        drag.start(dir, ev.getX(), ev.getY());
                    } else {
                        action.accept(dir);
                    }
                    return true;
                }
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL: {
                    boolean consumed = fired[0] || held[1];
                    view.removeCallbacks(longPress);
                    held[0] = held[1] = held[2] = false;
                    fired[0] = false;
                    dragging[0] = false;
                    return consumed;
                }
                default:
                    return fired[0] || held[1];
            }
        });
    }
}
