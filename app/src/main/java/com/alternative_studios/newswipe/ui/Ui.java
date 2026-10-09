package com.alternative_studios.newswipe.ui;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.util.TypedValue;

/** 화면 단위·배경 도우미. */
public final class Ui {
    private Ui() {
    }

    public static int dp(Context c, float v) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v,
                c.getResources().getDisplayMetrics()));
    }

    public static GradientDrawable round(int color, float radiusPx) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(radiusPx);
        return g;
    }

    /** 눌렀을 때 물결 효과가 있는 배경. content가 null이면 투명 배경. */
    public static Drawable ripple(int pressColor, Drawable content, float radiusPx) {
        GradientDrawable mask = round(0xFFFFFFFF, radiusPx);
        return new RippleDrawable(ColorStateList.valueOf(pressColor), content, mask);
    }

    /**
     * 선택 버튼 줄 (테마 설정, 길게 누르기 문자 편집의 한글/영어 탭 등이 같이 쓴다).
     * Material 3 Expressive의 연결된 버튼 그룹으로, 고른 칸은 강조색 알약이고 나머지는 옅은 회색이다.
     */
    public static android.widget.LinearLayout choiceRow(Context c, String[] labels, int selected,
                                                        int accent, int onAccent, int text, int hint,
                                                        java.util.function.IntConsumer onSelect) {
        return choiceRow(c, labels, selected, accent, onAccent, text, hint, 380, onSelect);
    }

    /**
     * @param delayMs 고른 뒤 onSelect를 부르기까지 기다리는 시간. 화면을 다시 만드는 곳은 스프링이 자리 잡을 때까지(380) 기다리고,
     *                그 자리에서 화면을 갱신하는 곳은 0으로 바로 알려 움직임이 끊기지 않게 한다.
     */
    public static android.widget.LinearLayout choiceRow(Context c, String[] labels, int selected,
                                                        int accent, int onAccent, int text, int hint, long delayMs,
                                                        java.util.function.IntConsumer onSelect) {
        android.widget.LinearLayout row = new android.widget.LinearLayout(c);
        android.widget.LinearLayout.LayoutParams rlp = new android.widget.LinearLayout.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT, android.view.ViewGroup.LayoutParams.WRAP_CONTENT);
        rlp.topMargin = dp(c, 6);
        rlp.bottomMargin = dp(c, 4);
        row.setLayoutParams(rlp);
        row.setBaselineAligned(false);   // 칸마다 글자 줄 수가 달라도 위아래로 어긋나지 않게
        row.setClipChildren(false);   // 눌렀을 때 옆 칸 쪽으로 늘어난 모양이 잘리지 않게
        // Material 3 Expressive '연결된 버튼 그룹': 칸 사이는 2dp, 고른 칸은 알약 (ExpressiveChoiceButton).
        // 고르는 즉시 모양이 스프링으로 바뀌고, 그 모습이 보이도록 알림은 잠깐 뒤에 보낸다 (알림을 받으면 화면을 다시 만드는 곳이 많다).
        final ExpressiveChoiceButton[] cells = new ExpressiveChoiceButton[labels.length];
        final int[] chosen = {selected};
        final boolean[] pending = {false};
        final Runnable[] send = {null};
        for (int i = 0; i < labels.length; i++) {
            final int index = i;
            ExpressiveChoiceButton t = new ExpressiveChoiceButton(c, labels[i], i == selected, i == 0,
                    i == labels.length - 1, accent, onAccent, text, hint);
            cells[i] = t;
            t.setOnClickListener(v -> {
                if (pending[0] || index == chosen[0]) return;
                chosen[0] = index;
                for (int k = 0; k < cells.length; k++) cells[k].setChosen(k == index, true);
                if (v.isHapticFeedbackEnabled()) {
                    v.performHapticFeedback(android.os.Build.VERSION.SDK_INT >= 30
                            ? android.view.HapticFeedbackConstants.CONFIRM
                            : android.view.HapticFeedbackConstants.KEYBOARD_TAP);
                }
                if (delayMs <= 0) {
                    onSelect.accept(index);
                    return;
                }
                pending[0] = true;
                send[0] = () -> {
                    pending[0] = false;
                    onSelect.accept(index);
                };
                row.postDelayed(send[0], delayMs);   // 스프링이 거의 자리 잡은 뒤에 알린다 (그전에 화면을 다시 만들면 움직임이 끊겨 보인다)
            });
            android.widget.LinearLayout.LayoutParams lp = new android.widget.LinearLayout.LayoutParams(
                    0, android.view.ViewGroup.LayoutParams.MATCH_PARENT, 1f);   // 한 칸이 두 줄이어도 높이를 맞춘다
            lp.setMargins(dp(c, 1), 0, dp(c, 1), 0);
            row.addView(t, lp);
        }
        for (int i = 0; i < cells.length; i++) {
            cells[i].setNeighbors(i > 0 ? cells[i - 1] : null, i + 1 < cells.length ? cells[i + 1] : null);
        }
        row.addOnAttachStateChangeListener(new android.view.View.OnAttachStateChangeListener() {
            @Override public void onViewAttachedToWindow(android.view.View v) { }
            @Override public void onViewDetachedFromWindow(android.view.View v) {
                if (send[0] != null) row.removeCallbacks(send[0]);   // 화면이 닫혔으면 알리지 않는다
            }
        });
        return row;
    }

    /** Android 15부터 화면이 시스템 바 뒤까지 그려지므로 시스템 바·키보드·노치만큼 안쪽 여백을 준다. */
    public static void padForSystemBars(android.view.View v) {
        if (android.os.Build.VERSION.SDK_INT < 35) return;
        v.setOnApplyWindowInsetsListener((view, insets) -> {
            android.graphics.Insets i = insets.getInsets(android.view.WindowInsets.Type.systemBars()
                    | android.view.WindowInsets.Type.ime() | android.view.WindowInsets.Type.displayCutout());
            view.setPadding(i.left, i.top, i.right, i.bottom);
            return android.view.WindowInsets.CONSUMED;
        });
    }

    private static final java.util.WeakHashMap<android.view.View, android.animation.ValueAnimator> RUNNING =
            new java.util.WeakHashMap<>();

    /**
     * 뷰를 높이와 투명도 애니메이션으로 펼치거나 접는다 (하위 설정, 입력창 등).
     * 아직 화면에 배치되기 전이면 애니메이션 없이 바로 바꾼다.
     */
    public static void setVisibleAnimated(final android.view.View v, final boolean show) {
        setVisibleAnimated(v, show, null);
    }

    /** @param onEnd 애니메이션이 끝났을 때(또는 애니메이션 없이 바로 바꿨을 때) 부를 작업. 없으면 null. */
    public static void setVisibleAnimated(final android.view.View v, final boolean show, final Runnable onEnd) {
        android.animation.ValueAnimator old = RUNNING.remove(v);
        if (old != null) old.cancel();
        android.view.ViewGroup.LayoutParams lp = v.getLayoutParams();
        android.view.ViewParent parent = v.getParent();
        boolean visible = v.getVisibility() == android.view.View.VISIBLE;
        if (!(parent instanceof android.view.View) || ((android.view.View) parent).getWidth() == 0 || lp == null) {
            v.setVisibility(show ? android.view.View.VISIBLE : android.view.View.GONE);
            if (onEnd != null) v.post(onEnd);
            return;
        }
        // 진행 중이던 애니메이션이 없고 이미 원하는 상태면 할 일이 없다.
        if (show == visible && lp.height == android.view.ViewGroup.LayoutParams.WRAP_CONTENT && v.getAlpha() == 1f) {
            if (onEnd != null) v.post(onEnd);
            return;
        }
        final int from = visible ? v.getHeight() : 0;
        final float fromAlpha = visible ? v.getAlpha() : 0f;
        final int to;
        if (show) {
            android.view.View pv = (android.view.View) parent;
            int margins = 0;
            if (lp instanceof android.view.ViewGroup.MarginLayoutParams) {
                margins = ((android.view.ViewGroup.MarginLayoutParams) lp).leftMargin
                        + ((android.view.ViewGroup.MarginLayoutParams) lp).rightMargin;
            }
            int width = pv.getWidth() - pv.getPaddingLeft() - pv.getPaddingRight() - margins;
            lp.height = android.view.ViewGroup.LayoutParams.WRAP_CONTENT;
            v.measure(android.view.View.MeasureSpec.makeMeasureSpec(width, android.view.View.MeasureSpec.EXACTLY),
                    android.view.View.MeasureSpec.makeMeasureSpec(0, android.view.View.MeasureSpec.UNSPECIFIED));
            to = v.getMeasuredHeight();
            lp.height = from;
            v.setAlpha(fromAlpha);
            v.setVisibility(android.view.View.VISIBLE);
            v.setLayoutParams(lp);
        } else {
            to = 0;
        }
        final float toAlpha = show ? 1f : 0f;
        final android.animation.ValueAnimator a = android.animation.ValueAnimator.ofFloat(0f, 1f);
        a.setDuration(200);
        a.setInterpolator(new android.view.animation.PathInterpolator(0.2f, 0f, 0f, 1f));
        a.addUpdateListener(anim -> {
            float f = (float) anim.getAnimatedValue();
            android.view.ViewGroup.LayoutParams l = v.getLayoutParams();
            l.height = Math.round(from + (to - from) * f);
            v.setLayoutParams(l);
            v.setAlpha(fromAlpha + (toAlpha - fromAlpha) * f);
        });
        a.addListener(new android.animation.AnimatorListenerAdapter() {
            private boolean canceled;

            @Override
            public void onAnimationCancel(android.animation.Animator animation) {
                canceled = true;   // 새 애니메이션이 지금 상태에서 이어받는다
            }

            @Override
            public void onAnimationEnd(android.animation.Animator animation) {
                if (canceled) return;
                RUNNING.remove(v);
                android.view.ViewGroup.LayoutParams l = v.getLayoutParams();
                l.height = android.view.ViewGroup.LayoutParams.WRAP_CONTENT;
                v.setLayoutParams(l);
                v.setAlpha(1f);
                if (!show) v.setVisibility(android.view.View.GONE);
                if (onEnd != null) onEnd.run();
            }
        });
        RUNNING.put(v, a);
        a.start();
    }
}
