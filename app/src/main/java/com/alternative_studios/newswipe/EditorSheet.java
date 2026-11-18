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
import com.alternative_studios.newswipe.ui.ExpressiveIconButton;
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
    /** 헤더 오른쪽: 되돌리기(없을 수 있다)와 키보드 열기. 창이 머리글을 덮어도 같은 일을 할 수 있게 둔다. */
    private ExpressiveIconButton undoButton, keyboardButton;
    /** 키보드 열기를 누르면 헤더 아래에 펼치는 입력 시험 창. */
    private final LinearLayout testRow;
    private final android.widget.EditText testInput;
    private boolean testWanted;
    /** 창을 화면 왼쪽 절반에 띄울 때 놓는 틀 (화면 전체를 덮는다, 없으면 null). */
    private FrameLayout splitHost;
    /** 지금 왼쪽 절반(splitHost)에 놓여 있는지. */
    private boolean split;
    /** 스프링이 넘칠 때를 위해 창 아래로 더 둔 길이. */
    private final int overshoot;
    /** 뒤의 목록이 조금 보이게 창 위에 두는 틈. */
    private final int topGap;

    /**
     * @param parent         창과 막을 겹쳐 놓을 틀 (설정 목록이 든 곳)
     * @param onCloseRequest X나 막을 눌렀을 때 (닫기는 부르는 쪽이 close로 한다)
     */
    EditorSheet(Context context, FrameLayout parent, Runnable onCloseRequest) {
        this(context, parent, onCloseRequest, null);
    }

    /** @param onUndo 헤더의 되돌리기 버튼을 눌렀을 때 (null이면 버튼을 두지 않는다) */
    EditorSheet(Context context, FrameLayout parent, Runnable onCloseRequest, View.OnClickListener onUndo) {
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
        overshoot = Ui.dp(c, 40);
        topGap = Ui.dp(c, 14);   // 뒤의 목록이 조금 보여 그 위에 뜬 창이라는 것을 알 수 있게
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
        // 오른쪽: 머리글과 같은 되돌리기·키보드 열기 버튼 (창이 머리글을 덮으므로 여기서도 쓸 수 있게).
        LinearLayout actions = new LinearLayout(c);
        actions.setGravity(Gravity.CENTER_VERTICAL);
        int gap = Ui.dp(c, 8);
        int actionsWidth = 0;
        if (onUndo != null) {
            undoButton = new ExpressiveIconButton(c, Icons.UNDO, colors.accent, colors.onAccent, L.t("마지막 변경 되돌리기", "Undo last change"));
            undoButton.setActive(true);   // 옅은 바탕: 키보드 열기와 구분되는 보조 버튼
            undoButton.setOnClickListener(onUndo);
            LinearLayout.LayoutParams ulp = new LinearLayout.LayoutParams(size, size);
            ulp.rightMargin = gap;
            actions.addView(undoButton, ulp);
            actionsWidth += size + gap;
        }
        keyboardButton = new ExpressiveIconButton(c, Icons.MENU_KEYBOARD, colors.accent, colors.onAccent, L.t("키보드 열기", "Open keyboard"));
        keyboardButton.setOnClickListener(v -> toggleTestInput());
        actions.addView(keyboardButton, new LinearLayout.LayoutParams(size, size));
        actionsWidth += size;
        header.addView(actions, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.END | Gravity.CENTER_VERTICAL));
        // 가운데(X와 오른쪽 버튼들 사이): 고른 것의 이름. 오른쪽 버튼이 더 넓어 왼쪽에 붙여 자리를 넉넉히 쓴다.
        title = new TextView(c);
        title.setTextColor(colors.text);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 17);
        title.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        title.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        title.setSingleLine(true);
        title.setEllipsize(android.text.TextUtils.TruncateAt.END);
        FrameLayout.LayoutParams tlp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER_VERTICAL);
        tlp.leftMargin = size + gap;
        tlp.rightMargin = actionsWidth + gap;
        header.addView(title, tlp);
        sheet.addView(header, SettingsWidgets.matchWrap());

        // 헤더 아래 입력 시험 창 (머리글의 것과 같은 모양). 키보드 열기를 누르면 펼친다.
        testRow = new LinearLayout(c);
        testRow.setVisibility(View.GONE);
        int tp = Ui.dp(c, 16);
        testRow.setPadding(tp, Ui.dp(c, 4), tp, Ui.dp(c, 8));
        testInput = new android.widget.EditText(c);
        testInput.setHint(L.t("여기에 입력해 보세요", "Type here"));
        testInput.setTextColor(colors.text);
        testInput.setHintTextColor(colors.hint);
        testInput.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        testInput.setMaxLines(3);
        testInput.setBackground(Ui.round(colors.card, Ui.dp(c, 12)));
        testInput.setPadding(Ui.dp(c, 14), Ui.dp(c, 10), Ui.dp(c, 14), Ui.dp(c, 10));
        testRow.addView(testInput, SettingsWidgets.matchWrap());
        sheet.addView(testRow, SettingsWidgets.matchWrap());

        scroll = new ScrollView(c);
        scroll.setFillViewport(true);
        body = new LinearLayout(c);
        body.setOrientation(LinearLayout.VERTICAL);
        int pad = Ui.dp(c, 16);
        body.setPadding(pad, 0, pad, Ui.dp(c, 32));
        scroll.addView(body);
        sheet.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        place(false);
    }

    /**
     * 창을 놓을 틀을 바꾼다. 보통은 설정 목록 위(parent)를 다 덮고, 왼쪽 절반이면 화면 전체 틀(splitHost)의 왼쪽 절반에
     * 위에서 아래(미리보기 옆)까지 놓는다.
     */
    private void place(boolean half) {
        split = half;
        ViewGroup now = (ViewGroup) sheet.getParent();
        if (now != null) now.removeView(sheet);
        FrameLayout host = half ? splitHost : parent;
        int width = ViewGroup.LayoutParams.MATCH_PARENT;
        if (half) width = Math.max(0, host.getWidth() - host.getPaddingLeft() - host.getPaddingRight()) / 2;
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(width, ViewGroup.LayoutParams.MATCH_PARENT,
                Gravity.START | Gravity.TOP);
        lp.topMargin = topGap;
        lp.bottomMargin = -overshoot;
        host.addView(sheet, lp);
    }

    /** 창을 화면 왼쪽 절반에 띄울 수 있게 화면 전체를 덮는 틀을 준다 (open(true)에 쓴다). */
    void setSplitHost(FrameLayout host) {
        splitHost = host;
    }

    /** 창이 화면 왼쪽 절반에 떠 있는지. */
    boolean isSplit() {
        return split;
    }

    /**
     * 창을 폭 width로 띄웠을 때 맨 위(틈 포함)부터 내용 속 count번째 항목의 아래 끝까지의 높이: 이만큼 보여야 항목 count개가
     * 온전히 보인다. 항목은 카드 안의 한 줄 한 줄(선택 버튼 줄, 설명 글, 스위치·슬라이더 줄 등)과 rows(설정 검색에 쓰는 항목 줄)
     * 가운데 지금 보이는 것이다. 카드 밖의 소제목·안내 글과 줄 사이의 구분선은 세지 않는다. 항목이 count개보다 적으면 마지막
     * 항목까지, 하나도 없으면 내용 전체까지 잰다. 창이 숨어 있어도 잴 수 있게 헤더·내용을 그 폭으로 직접 재고 배치해 본다.
     */
    int heightToShow(java.util.Set<View> rows, int count, int width) {
        int wSpec = View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY);
        int free = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED);
        int top = topGap;
        for (int i = 0; i < sheet.getChildCount(); i++) {
            View child = sheet.getChildAt(i);
            if (child == scroll || child.getVisibility() == View.GONE) continue;
            child.measure(wSpec, free);
            top += child.getMeasuredHeight();
        }
        body.measure(wSpec, free);
        body.layout(0, 0, body.getMeasuredWidth(), body.getMeasuredHeight());
        java.util.ArrayList<Integer> bottoms = new java.util.ArrayList<>();
        for (int i = 0; i < body.getChildCount(); i++) collectItems(body.getChildAt(i), 0, false, rows, bottoms);
        java.util.Collections.sort(bottoms);
        int end = bottoms.isEmpty() ? body.getMeasuredHeight() - body.getPaddingBottom()
                : bottoms.get(Math.min(count, bottoms.size()) - 1);
        return top + end;
    }

    /**
     * v와 그 안에서 항목을 찾아 그 아래 끝(내용 위에서부터)을 bottoms에 더한다. y는 v의 부모가 내용 위에서 떨어진 거리,
     * inCard는 v가 카드 안에 있는지. 카드·하위 설정 묶음·바탕 없는 세로 틀은 그 안을 살피고, 카드 안의 나머지 보이는 것은
     * 그대로 하나의 항목이다.
     */
    private static void collectItems(View v, int y, boolean inCard, java.util.Set<View> rows, java.util.List<Integer> bottoms) {
        if (v.getVisibility() != View.VISIBLE || v.getHeight() == 0) return;
        if (rows.contains(v)) {
            bottoms.add(y + v.getBottom());
            return;
        }
        Object tag = v.getTag();
        boolean card = tag == SettingsWidgets.CARD;
        boolean container = card || tag == SettingsWidgets.SUB_GROUP
                || (v instanceof LinearLayout && ((LinearLayout) v).getOrientation() == LinearLayout.VERTICAL
                && v.getBackground() == null && !v.isClickable());
        if (container) {
            ViewGroup g = (ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) collectItems(g.getChildAt(i), y + v.getTop(), inCard || card, rows, bottoms);
            return;
        }
        // 카드 안의 구분선·하위 설정의 세로 안내선 같은 맨 View는 항목이 아니다.
        if (inCard && v.getClass() != View.class) bottoms.add(y + v.getBottom());
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

    /** 헤더의 되돌리기 버튼 (onUndo 없이 만들었으면 null). 되돌릴 것이 있는지에 따라 부르는 쪽이 켜고 끈다. */
    View undoButton() {
        return undoButton;
    }

    /** 헤더의 키보드 열기: 입력 시험 창을 펼쳐 키보드를 띄우거나, 접고 키보드를 내린다. */
    void toggleTestInput() {
        setTestInput(!testWanted);
    }

    private void setTestInput(boolean on) {
        if (testWanted == on) return;
        testWanted = on;
        keyboardButton.setActive(on);   // 열려 있으면 옅은 바탕에 강조색 아이콘
        keyboardButton.setContentDescription(on ? L.t("키보드 닫기", "Close keyboard") : L.t("키보드 열기", "Open keyboard"));
        android.view.inputmethod.InputMethodManager imm =
                (android.view.inputmethod.InputMethodManager) c.getSystemService(Context.INPUT_METHOD_SERVICE);
        if (!on) {
            if (imm != null) imm.hideSoftInputFromWindow(testInput.getWindowToken(), 0);
            testInput.clearFocus();
            Ui.setVisibleAnimated(testRow, false);
            return;
        }
        Ui.setVisibleAnimated(testRow, true, this::focusTestInput);
        testRow.post(this::focusTestInput);
    }

    private void focusTestInput() {
        if (!testWanted) return;
        if (!testInput.hasFocus()) testInput.requestFocus();
        if (!testInput.hasFocus()) return;
        android.view.inputmethod.InputMethodManager imm =
                (android.view.inputmethod.InputMethodManager) c.getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) imm.showSoftInput(testInput, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT);
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
        View host = (View) sheet.getParent();
        int h = host != null ? host.getHeight() : 0;
        return h > 0 ? h : Ui.dp(c, 800);
    }

    /**
     * 창을 아래에서 위로 올린다: Material 3 Expressive 기본 공간 스프링 (SpringMotion, 강성 700·감쇠비 0.85).
     * 빠르게 올라와 아주 살짝 넘었다가 반 픽셀 안으로 들어올 때까지 매끄럽게 자리 잡는다.
     * 방금 만든 내용의 배치가 첫 프레임을 잡아먹어 움직임이 늦게 시작해 보이지 않도록, 배치가 끝난 다음 프레임에 시작한다.
     */
    void open() {
        open(false);
    }

    /**
     * open()과 같되, half면 창을 화면 왼쪽 절반에 위에서 아래까지 띄운다 (미리보기가 커서 보통처럼 띄우면 항목이 2개도 온전히 보이지 않을 때, 부르는 쪽이
     * 미리보기를 오른쪽 절반으로 줄인다). 화면 전체 틀이 없거나 아직 배치 전이면 보통처럼 띄운다.
     */
    void open(boolean half) {
        closing = false;
        cancelMotion();
        if (half && (splitHost == null || splitHost.getWidth() == 0)) half = false;
        if (half != split) {
            place(half);
            sheet.setVisibility(View.GONE);   // 다른 틀로 옮겼으면 아래에서 새로 올린다
        }
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
        setTestInput(false);   // 창과 함께 입력 시험 창도 닫고 키보드를 내린다
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
                        if (split) place(false);   // 다음에 보통처럼 열 수 있게 제자리로 돌려 둔다
                        if (onHidden != null) onHidden.run();
                    }
                }).start();
        scrim.animate().alpha(0f).setDuration(200).setInterpolator(accel).setListener(null).start();
    }
}
