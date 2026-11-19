package com.alternative_studios.newswipe;

import android.content.Context;
import android.graphics.PixelFormat;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.TextView;

/**
 * 외장 키보드로 한·영을 바꿨을 때 '한글' 또는 'English'를 화면 아래쪽에 잠깐 띄우는 알림.
 *
 * <p>토스트는 쓰지 않는다: 안드로이드는 알림 권한이 없는 앱이 앞에 나와 있지 않으면 토스트를 보이지 않는데,
 * 자판이 감춰진 키보드는 앞에 나온 앱으로 치지 않아 다른 앱에서 타자를 칠 때 알림이 사라진다.
 * 대신 키보드 창의 토큰으로 키보드 전용 대화 창(TYPE_INPUT_METHOD_DIALOG)을 직접 붙인다.
 * 키보드라면 따로 권한 없이 붙일 수 있고, 자판이 보이든 감춰졌든 지금 입력 중인 앱 위에 그려진다.
 * 터치와 포커스는 받지 않으므로 뒤의 앱을 방해하지 않는다.</p>
 */
final class LangNotice {
    private static final long SHOW_MS = 1200;
    private static final long FADE_MS = 150;

    private final Context context;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Runnable hideRunnable = this::fadeOut;

    private WindowManager wm;
    private TextView view;

    LangNotice(Context context) {
        this.context = context;
    }

    /**
     * 알림을 띄운다. 이미 떠 있으면 글자만 바꾸고 시간을 새로 센다.
     *
     * @param token   키보드 창의 토큰 (없으면 띄울 수 없다)
     * @param bottomY 화면 아래 끝에서 알림 아래 끝까지의 거리 (px)
     * @return 띄웠으면 true. 창을 붙이지 못했으면 false (호출한 쪽이 다른 방법으로 알린다)
     */
    boolean show(WindowManager windowManager, IBinder token, CharSequence text, int bottomY) {
        if (windowManager == null || token == null) return false;
        main.removeCallbacks(hideRunnable);
        if (view != null && wm == windowManager) {
            view.setText(text);
            view.animate().cancel();
            view.setAlpha(1f);
            WindowManager.LayoutParams lp = (WindowManager.LayoutParams) view.getLayoutParams();
            if (lp != null && lp.y != bottomY) {
                lp.y = bottomY;
                try {
                    wm.updateViewLayout(view, lp);
                } catch (RuntimeException ignored) {
                    // 자리만 못 옮겼다
                }
            }
            main.postDelayed(hideRunnable, SHOW_MS);
            return true;
        }
        dismiss();

        TextView tv = new TextView(context);
        tv.setText(text);
        tv.setTextColor(0xFFFFFFFF);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        tv.setGravity(Gravity.CENTER);
        int padH = dp(20), padV = dp(10);
        tv.setPadding(padH, padV, padH, padV);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xE6323232);
        bg.setCornerRadius(dp(24));
        tv.setBackground(bg);
        tv.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);

        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_INPUT_METHOD_DIALOG,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT);
        lp.token = token;
        lp.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
        lp.y = bottomY;
        lp.windowAnimations = android.R.style.Animation_Toast;
        lp.setTitle("NewSwipe language");
        try {
            windowManager.addView(tv, lp);
        } catch (RuntimeException e) {
            // 토큰이 이미 사라졌거나 기기가 창을 받아 주지 않았다
            return false;
        }
        wm = windowManager;
        view = tv;
        main.postDelayed(hideRunnable, SHOW_MS);
        return true;
    }

    private void fadeOut() {
        TextView v = view;
        if (v == null) return;
        v.animate().alpha(0f).setDuration(FADE_MS).withEndAction(() -> {
            if (view == v) dismiss();
        }).start();
    }

    /** 떠 있는 알림을 곧바로 내린다. 키보드가 끝나거나 창 토큰이 바뀔 때 부른다. */
    void dismiss() {
        main.removeCallbacks(hideRunnable);
        TextView v = view;
        WindowManager w = wm;
        view = null;
        wm = null;
        if (v == null || w == null) return;
        v.animate().cancel();
        try {
            w.removeViewImmediate(v);
        } catch (RuntimeException ignored) {
            // 이미 떨어졌다
        }
    }

    private int dp(int v) {
        return Math.round(v * context.getResources().getDisplayMetrics().density);
    }
}
