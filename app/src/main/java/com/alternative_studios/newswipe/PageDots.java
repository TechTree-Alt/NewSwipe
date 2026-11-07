package com.alternative_studios.newswipe;

import android.content.Context;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.alternative_studios.newswipe.ui.Ui;

import java.util.function.IntConsumer;

/**
 * 키보드 편집 미리보기 위의 페이지 표시: 페이지마다 점 하나, 지금 페이지는 강조색으로 조금 크게.
 * 페이지를 넘기면 점 오른쪽에 페이지 이름을 잠깐 띄우고, 화면 읽기에는 이름을 읽어 준다.
 * 점을 누르면 그 페이지로, 줄의 다른 곳을 누르면 다음 페이지로 넘긴다.
 */
final class PageDots extends LinearLayout {
    private final String[] names;
    private final int accent, idle;
    private final LinearLayout dots;
    private final TextView flash;
    private int page;

    /** @param onPick 사용자가 고른 페이지 번호를 받는다 */
    PageDots(Context c, String[] names, AppTheme colors, IntConsumer onPick) {
        super(c);
        this.names = names;
        this.accent = colors.accent;
        this.idle = (colors.hint & 0x00FFFFFF) | 0x66000000;
        setGravity(Gravity.CENTER);
        setPadding(0, Ui.dp(c, 4), 0, Ui.dp(c, 4));
        setBackgroundColor(colors.bg);
        setClipChildren(false);
        dots = new LinearLayout(c);
        dots.setGravity(Gravity.CENTER_VERTICAL);
        // 고른 점은 1.25배로 커지므로 점보다 높은 줄에 놓아 위아래가 잘리지 않게 한다.
        dots.setClipChildren(false);
        for (int i = 0; i < names.length; i++) {
            final int p = i;
            View dot = new View(c);
            LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(Ui.dp(c, 8), Ui.dp(c, 8));
            dlp.setMargins(Ui.dp(c, 4), 0, Ui.dp(c, 4), 0);
            dots.addView(dot, dlp);
            dot.setContentDescription(names[i] + " 페이지");
            dot.setOnClickListener(v -> onPick.accept(p));
        }
        // 점은 늘 가운데: 양옆에 같은 비율의 칸을 두고, 페이지 이름은 오른쪽 칸에 띄운다 (점이 밀리지 않는다).
        addView(new View(c), new LinearLayout.LayoutParams(0, 1, 1f));
        addView(dots, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, Ui.dp(c, 14)));
        flash = new TextView(c);
        flash.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        flash.setTextColor(accent);
        flash.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        flash.setAlpha(0f);
        flash.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);   // 읽기는 announce로 한다
        LinearLayout.LayoutParams flp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        flp.leftMargin = Ui.dp(c, 8);
        addView(flash, flp);
        // 점이 작아도 누르기 쉽게 줄 전체를 누르면 다음 페이지로.
        setOnClickListener(v -> onPick.accept((page + 1) % names.length));
    }

    /** 지금 페이지를 보인다. */
    void setPage(int p) {
        page = p;
        for (int i = 0; i < dots.getChildCount(); i++) {
            View dot = dots.getChildAt(i);
            boolean on = i == p;
            dot.setBackground(Ui.round(on ? accent : idle, Ui.dp(getContext(), 4)));
            dot.animate().scaleX(on ? 1.25f : 1f).scaleY(on ? 1.25f : 1f).setDuration(160).start();
        }
        setContentDescription(names[p] + " 페이지. 눌러서 " + names[(p + 1) % names.length] + " 페이지로 넘기기");
    }

    /** 페이지를 넘겼다: 점 옆에 그 페이지 이름을 잠깐 보였다가 흐리게 지운다. */
    void flashName(int p) {
        String name = names[p] + " 페이지";
        flash.setText(name);
        flash.animate().cancel();
        flash.animate().alpha(1f).setDuration(120).setStartDelay(0).withEndAction(() ->
                flash.animate().alpha(0f).setStartDelay(1100).setDuration(260).start()).start();
        announceForAccessibility(name);
    }
}
