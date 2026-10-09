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
     * @param enabled     방향(Key.SWIPE_UP·SWIPE_DOWN)마다 동작이 있는지. false인 방향으로 밀면 아무 일도 하지 않는다
     * @param thresholdPx 이만큼 밀면 실행한다
     *                    (enabled·thresholdPx는 터치할 때마다 읽으므로 설정이 바뀌면 바로 따른다)
     * @param action      민 방향(Key.SWIPE_UP·SWIPE_DOWN)을 받아 실행한다
     */
    @SuppressLint("ClickableViewAccessibility")
    public static void attach(View v, IntPredicate enabled, IntSupplier thresholdPx, IntConsumer action) {
        final float[] down = new float[2];
        final boolean[] state = new boolean[3];   // [0] 위 동작 있음, [1] 아래 동작 있음, [2] 이미 실행했다
        v.setOnTouchListener((view, ev) -> {
            switch (ev.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    down[0] = ev.getX();
                    down[1] = ev.getY();
                    state[0] = enabled.test(Key.SWIPE_UP);
                    state[1] = enabled.test(Key.SWIPE_DOWN);
                    state[2] = false;
                    return false;   // 누르기 모양(눌림 효과)은 버튼이 그대로 처리한다
                case MotionEvent.ACTION_MOVE: {
                    if (state[2]) return true;
                    if (!state[0] && !state[1]) return false;
                    float dx = ev.getX() - down[0], dy = ev.getY() - down[1];
                    if (Math.abs(dy) <= thresholdPx.getAsInt() || Math.abs(dy) <= Math.abs(dx) * 1.5f) return false;
                    int dir = dy < 0 ? Key.SWIPE_UP : Key.SWIPE_DOWN;
                    if (!state[dir == Key.SWIPE_UP ? 0 : 1]) return false;
                    state[2] = true;
                    // 버튼에는 취소를 보내 눌림을 풀고, 손을 뗄 때 클릭이 되지 않게 한다.
                    MotionEvent cancel = MotionEvent.obtain(ev);
                    cancel.setAction(MotionEvent.ACTION_CANCEL);
                    view.onTouchEvent(cancel);
                    cancel.recycle();
                    action.accept(dir);
                    return true;
                }
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL: {
                    boolean consumed = state[2];
                    state[0] = state[1] = state[2] = false;
                    return consumed;
                }
                default:
                    return state[2];
            }
        });
    }

    /**
     * 네 방향(위·아래·왼쪽·오른쪽) 밀기를 붙인다. 쓰는 방법은 {@link #attach}와 같고, 가로로 뚜렷하게 밀면 좌우로 본다.
     *
     * @param enabled 방향(Key.SWIPE_*)마다 동작이 있는지
     */
    @SuppressLint("ClickableViewAccessibility")
    public static void attachFourWay(View v, IntPredicate enabled, IntSupplier thresholdPx, IntConsumer action) {
        final float[] down = new float[2];
        final boolean[] fired = new boolean[1];
        v.setOnTouchListener((view, ev) -> {
            switch (ev.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    down[0] = ev.getX();
                    down[1] = ev.getY();
                    fired[0] = false;
                    return false;
                case MotionEvent.ACTION_MOVE: {
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
                    action.accept(dir);
                    return true;
                }
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL: {
                    boolean consumed = fired[0];
                    fired[0] = false;
                    return consumed;
                }
                default:
                    return fired[0];
            }
        });
    }
}
