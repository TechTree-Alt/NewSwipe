package com.alternative_studios.newswipe;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.PathInterpolator;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.alternative_studios.newswipe.keyboard.Icons;
import com.alternative_studios.newswipe.ui.IconButton;
import com.alternative_studios.newswipe.ui.SpringMotion;
import com.alternative_studios.newswipe.ui.Ui;

/**
 * 키보드 사용자화의 설정 창: 설정 목록 위에 겹친 시트. 위쪽 모서리가 둥글고 그림자가 지며, 헤더의 X로 닫는다.
 * 뒤의 목록은 어두운 막으로 가려(누르면 닫힌다) 지금 보는 것이 목록 위에 뜬 창이라는 것을 알 수 있게 한다.
 * 미리보기(화면 아래)는 가리지 않아 창을 연 채 다른 키를 눌러 바로 바꿀 수 있다. 바꾼 값은 그때그때 저장된다(창은 보여 주기만).
 */
final class EditorSheet {
    private final Context c;
    private final FrameLayout parent;
    private final View scrim;
    private final LinearLayout sheet;
    private final TextView title;
    private final ScrollView scroll;
    private final LinearLayout body;
    /** 열 때 움직이는 스프링 (닫을 때는 sheet.animate()를 쓴다). */
    private ValueAnimator spring;
    private boolean closing;

    /**
     * @param parent         창과 막을 겹쳐 놓을 틀 (설정 목록이 든 곳)
     * @param onCloseRequest X나 막을 눌렀을 때 (닫기는 부르는 쪽이 close로 한다)
     */
    EditorSheet(Context context, FrameLayout parent, Runnable onCloseRequest) {
        this.c = context;
        this.parent = parent;
        AppTheme colors = AppTheme.of(context);
        scrim = new View(c);
        scrim.setBackgroundColor(0x66000000);
        scrim.setAlpha(0f);
        scrim.setVisibility(View.GONE);
        scrim.setOnClickListener(v -> onCloseRequest.run());
        scrim.setContentDescription(L.t("설정 창 닫기", "Close settings"));
        parent.addView(scrim, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));

        sheet = new LinearLayout(c);
        sheet.setOrientation(LinearLayout.VERTICAL);
        float r = Ui.dp(c, 28);   // Material 3 바텀 시트의 큰 모서리
        GradientDrawable bg = new GradientDrawable();
        // 다크 모드에서는 그림자가 잘 보이지 않으므로 Material 3처럼 시트 바탕을 한 단계 밝게 해 층을 구분한다.
        boolean darkBg = Color.luminance(colors.bg) < 0.2f;
        bg.setColor(darkBg ? blend(colors.bg, colors.card, 0.55f) : colors.bg);
        bg.setCornerRadii(new float[]{r, r, r, r, 0, 0, 0, 0});
        sheet.setBackground(bg);
        sheet.setElevation(Ui.dp(c, 12));
        sheet.setClickable(true);   // 시트 위의 빈 곳을 눌러도 뒤의 막(닫기)으로 넘어가지 않게
        sheet.setVisibility(View.GONE);
        // 스프링이 목표를 살짝 넘어 올라갈 때 아래가 비어 보이지 않게 시트를 틀 아래로 조금 더 길게 둔다 (넘친 부분은 잘린다).
        int overshoot = Ui.dp(c, 40);
        sheet.setPadding(0, 0, 0, overshoot);

        // 헤더: 왼쪽 X(닫기), 가운데 고른 것의 이름.
        FrameLayout header = new FrameLayout(c);
        int hp = Ui.dp(c, 12);
        header.setPadding(hp, Ui.dp(c, 12), hp, Ui.dp(c, 4));
        int press = (colors.text & 0x00FFFFFF) | 0x33000000;
        IconButton cancel = new IconButton(c, Icons.CLOSE, colors.text, press, L.t("설정 창 닫기", "Close settings"));
        cancel.setOnClickListener(v -> onCloseRequest.run());
        int size = Ui.dp(c, 44);
        header.addView(cancel, new FrameLayout.LayoutParams(size, size, Gravity.START | Gravity.CENTER_VERTICAL));
        title = new TextView(c);
        title.setTextColor(colors.text);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 17);
        title.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        title.setGravity(Gravity.CENTER);
        title.setSingleLine(true);
        title.setEllipsize(android.text.TextUtils.TruncateAt.END);
        FrameLayout.LayoutParams tlp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER);
        tlp.leftMargin = tlp.rightMargin = size + Ui.dp(c, 8);
        header.addView(title, tlp);
        sheet.addView(header, SettingsWidgets.matchWrap());

        scroll = new ScrollView(c);
        scroll.setFillViewport(true);
        body = new LinearLayout(c);
        body.setOrientation(LinearLayout.VERTICAL);
        int pad = Ui.dp(c, 16);
        body.setPadding(pad, 0, pad, Ui.dp(c, 32));
        scroll.addView(body);
        sheet.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT);
        lp.topMargin = Ui.dp(c, 14);   // 뒤의 목록이 조금 보여 그 위에 뜬 창이라는 것을 알 수 있게
        lp.bottomMargin = -overshoot;
        parent.addView(sheet, lp);
    }

    private static int blend(int a, int b, float t) {
        int r = Math.round(Color.red(a) + (Color.red(b) - Color.red(a)) * t);
        int g = Math.round(Color.green(a) + (Color.green(b) - Color.green(a)) * t);
        int bl = Math.round(Color.blue(a) + (Color.blue(b) - Color.blue(a)) * t);
        return 0xFF000000 | (r << 16) | (g << 8) | bl;
    }

    /** 창의 내용을 담는 곳 (스크롤 안). */
    LinearLayout body() {
        return body;
    }

    ScrollView scroll() {
        return scroll;
    }

    /** 움직이는 창 자체 (시험에서 자리를 확인할 때). */
    View view() {
        return sheet;
    }

    void setTitle(CharSequence t) {
        title.setText(t);
    }

    /** 열려 있는지 (닫히는 중이면 아니다). */
    boolean isOpen() {
        return sheet.getVisibility() == View.VISIBLE && !closing;
    }

    /** 창을 연 위치에서 아래로 내려 숨기는 거리 (틀의 높이만큼, 아직 배치 전이면 넉넉히). */
    private float hiddenY() {
        return parent.getHeight() > 0 ? parent.getHeight() : Ui.dp(c, 800);
    }

    /**
     * 창을 아래에서 위로 올린다: Material 3 Expressive 기본 공간 스프링 (SpringMotion, 강성 700·감쇠비 0.85).
     * 빠르게 올라와 아주 살짝 넘었다가 반 픽셀 안으로 들어올 때까지 매끄럽게 자리 잡는다.
     * 방금 만든 내용의 배치가 첫 프레임을 잡아먹어 움직임이 늦게 시작해 보이지 않도록, 배치가 끝난 다음 프레임에 시작한다.
     */
    void open() {
        closing = false;
        cancelMotion();
        if (sheet.getVisibility() != View.VISIBLE) sheet.setTranslationY(hiddenY());
        sheet.setVisibility(View.VISIBLE);
        scrim.setVisibility(View.VISIBLE);
        scrim.animate().alpha(1f).setDuration(180).setInterpolator(new PathInterpolator(0.2f, 0f, 0f, 1f))
                .setListener(null).start();
        sheet.post(() -> {
            if (closing || sheet.getVisibility() != View.VISIBLE) return;
            spring = SpringMotion.translateY(sheet, 0f, SpringMotion.STIFFNESS_DEFAULT, SpringMotion.DAMPING_EXPRESSIVE, null);
        });
    }

    private void cancelMotion() {
        if (spring != null) spring.cancel();
        spring = null;
        sheet.animate().cancel();
        scrim.animate().cancel();
    }

    /**
     * 창을 Material 3 강조 가속 곡선으로 빠르게 내려 닫는다 (나갈 때는 들어올 때보다 짧게).
     *
     * @param onHidden 다 내려가 숨겨진 뒤에 부른다 (닫히는 사이에 다시 열면 부르지 않는다)
     */
    void close(Runnable onHidden) {
        if (!isOpen()) return;
        closing = true;
        cancelMotion();
        PathInterpolator accel = new PathInterpolator(0.3f, 0f, 0.8f, 0.15f);
        sheet.animate().translationY(hiddenY())
                .setInterpolator(accel)
                .setDuration(200)
                .withLayer()
                .setListener(new AnimatorListenerAdapter() {
                    private boolean cancelled;

                    @Override
                    public void onAnimationCancel(Animator animation) {
                        cancelled = true;
                    }

                    @Override
                    public void onAnimationEnd(Animator animation) {
                        sheet.animate().setListener(null);
                        if (cancelled || !closing) return;   // 닫히는 사이에 다시 열었다
                        closing = false;
                        sheet.setVisibility(View.GONE);
                        scrim.setVisibility(View.GONE);
                        if (onHidden != null) onHidden.run();
                    }
                }).start();
        scrim.animate().alpha(0f).setDuration(200).setInterpolator(accel).setListener(null).start();
    }
}
