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
 * 키보드 편집 미리보기 위의 페이지 표시: 가운데에 페이지마다 점 하나(지금 페이지는 강조색으로 조금 크게),
 * 점 왼쪽에 지금 페이지의 이름, 오른쪽에 그 페이지가 하는 일의 짧은 설명. 화면 읽기에는 이름과 설명을 읽어 준다.
 * 점을 누르면 그 페이지로, 줄의 다른 곳을 누르면 다음 페이지로 넘긴다.
 */
final class PageDots extends LinearLayout {
    private final String[] names, descs;
    private final int accent, idle;
    private final LinearLayout dots;
    private final TextView nameView, descView;
    private int page;

    /**
     * @param names  페이지 이름 (점 왼쪽에 보인다)
     * @param descs  페이지 설명 (점 오른쪽에 보인다)
     * @param onPick 사용자가 고른 페이지 번호를 받는다
     */
    PageDots(Context c, String[] names, String[] descs, AppTheme colors, IntConsumer onPick) {
        super(c);
        this.names = names;
        this.descs = descs;
        this.accent = colors.accent;
        this.idle = (colors.hint & 0x00FFFFFF) | 0x66000000;
        setGravity(Gravity.CENTER_VERTICAL);
        setPadding(Ui.dp(c, 8), Ui.dp(c, 4), Ui.dp(c, 8), Ui.dp(c, 4));
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
            dot.setContentDescription(names[i]);
            dot.setOnClickListener(v -> onPick.accept(p));
        }
        // 점은 늘 가운데: 양옆에 같은 비율의 칸을 두고, 이름은 왼쪽 칸(점 쪽 끝), 설명은 오른쪽 칸(점 쪽 끝)에 둔다.
        nameView = side(c, colors.accent, true, Gravity.END | Gravity.CENTER_VERTICAL);
        nameView.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        LinearLayout.LayoutParams nlp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        nlp.rightMargin = Ui.dp(c, 6);
        addView(nameView, nlp);
        addView(dots, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, Ui.dp(c, 14)));
        descView = side(c, colors.hint, false, Gravity.START | Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        dlp.leftMargin = Ui.dp(c, 6);
        addView(descView, dlp);
        // 점이 작아도 누르기 쉽게 줄 전체를 누르면 다음 페이지로.
        setOnClickListener(v -> onPick.accept((page + 1) % names.length));
    }

    private static TextView side(Context c, int color, boolean right, int gravity) {
        TextView t = new TextView(c);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        t.setTextColor(color);
        t.setGravity(gravity);
        t.setTextAlignment(right ? View.TEXT_ALIGNMENT_VIEW_END : View.TEXT_ALIGNMENT_VIEW_START);
        t.setMaxLines(2);
        t.setEllipsize(android.text.TextUtils.TruncateAt.END);
        t.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);   // 읽기는 줄 전체의 설명으로 한다
        return t;
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
        nameView.setText(names[p]);
        descView.setText(descs[p]);
        setContentDescription(names[p] + ". " + descs[p] + " 눌러서 " + names[(p + 1) % names.length] + "(으)로 넘기기");
    }

    /** 사용자가 페이지를 넘겼다: 화면 읽기에 새 페이지의 이름과 설명을 읽어 준다. */
    void announce(int p) {
        announceForAccessibility(names[p] + ". " + descs[p]);
    }
}
