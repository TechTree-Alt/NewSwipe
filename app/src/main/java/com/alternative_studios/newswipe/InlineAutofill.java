package com.alternative_studios.newswipe;

import android.annotation.TargetApi;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Rect;
import android.graphics.drawable.Icon;
import android.os.Bundle;
import android.util.Size;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InlineSuggestion;
import android.view.inputmethod.InlineSuggestionsRequest;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.inline.InlineContentView;
import android.widget.inline.InlinePresentationSpec;

import com.alternative_studios.newswipe.keyboard.KeyboardTheme;
import com.alternative_studios.newswipe.ui.Ui;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 자동 완성 서비스(Autofill with Google, 삼성 패스 등)의 제안을 도구 막대 자리에 칩으로 보여 준다 (Android 11 이상).
 *
 * <p>키보드가 {@link #request}로 "칩을 이 크기·모양으로 보여 줄 수 있다"고 알리면, 자동 완성 서비스가 채울 수 있는
 * 입력란(로그인 칸 등)에서 칩을 보내 준다. 칩은 서비스가 그리는 화면({@link InlineContentView})이라 키보드는 자리만
 * 내준다. 칩을 누르면 서비스가 입력란을 채운다. 서비스가 고정(pinned) 칩(예: 비밀번호 목록 열기)을 보내면 막대 오른쪽
 * 끝에 따로 놓는다.
 *
 * <p>칩의 모양은 AndroidX autofill 라이브러리(androidx.autofill.inline v1)가 읽는 Bundle 형식으로 넘긴다.
 * 라이브러리를 쓰지 않으려고 그 형식(키 이름)을 그대로 만든다.
 */
@TargetApi(30)
final class InlineAutofill {
    /** 한 번에 받는 제안 수 (고정 칩 제외). 많아야 도구 막대 하나를 넘지 않을 만큼. */
    private static final int MAX_SUGGESTIONS = 6;

    /** 막대가 보이거나 사라질 때 IME가 도구 막대 자리를 다시 맞추도록 알린다. */
    interface Host {
        void onAutofillBarChanged();
    }

    private final Context context;
    private final Host host;
    private LinearLayout bar, chips, pinned;
    private HorizontalScrollView scroll;
    /** 지금 보여 줄 칩이 있는지 (패널을 바꿔 막대를 잠깐 가려도 그대로다). */
    private boolean shown;
    /** 응답마다 늘어난다. 늦게 끝난 예전 응답의 칩 만들기 결과는 버린다. */
    private int generation;
    /** 막대 양옆 여백 (px). 도구 막대처럼 자판의 좌우 여백을 따른다. */
    private int padLeft, padRight;
    /** 요청할 때 정한 칩 높이 (px). 칩은 이 높이로 만든다 (요청한 크기 범위를 벗어나면 만들 수 없다). */
    private int chipHeight;

    InlineAutofill(Context context, Host host) {
        this.context = context;
        this.host = host;
    }

    /**
     * 도구 막대 자리(topSlot)에 겹쳐 놓을 막대를 새로 만든다. 입력 화면을 다시 만들 때마다 부르고, 예전 막대와 칩은 버린다.
     */
    View createBar(KeyboardTheme theme) {
        // 예전 막대의 칩과 만들고 있던 칩은 버린다. 입력 화면을 만드는 중이라 IME에는 알리지 않는다
        // (새 막대는 감춘 채로 시작하고, 다음 응답이 오면 다시 보인다).
        generation++;
        removeChips();
        shown = false;
        bar = new LinearLayout(context);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setBackgroundColor(theme.background);
        bar.setClickable(true);   // 아래의 도구 막대·추천이 눌리지 않게
        bar.setVisibility(View.GONE);
        scroll = new HorizontalScrollView(context);
        scroll.setHorizontalScrollBarEnabled(false);
        chips = new LinearLayout(context);
        chips.setGravity(Gravity.CENTER_VERTICAL);
        scroll.addView(chips, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        // 스크롤하거나 크기가 바뀌면 칩이 스크롤 칸 밖으로 삐져나오지 않게 다시 자른다.
        scroll.setOnScrollChangeListener((v, x, y, oldX, oldY) -> clipChips());
        scroll.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> clipChips());
        bar.addView(scroll, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));
        // 고정 칩(열쇠 등) 자리: 배경을 깔아 칩 줄과 나눈다. 줄의 칩은 이 앞(스크롤 칸 끝)에서 잘려 이 아래로 들어간다.
        pinned = new LinearLayout(context);
        pinned.setGravity(Gravity.CENTER_VERTICAL);
        pinned.setBackgroundColor(theme.background);
        pinned.setPadding(Ui.dp(context, 4), 0, 0, 0);
        bar.addView(pinned, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        bar.setPadding(padLeft, 0, padRight, 0);
        return bar;
    }

    /** 막대 양옆 여백 (px): 도구 막대와 같이 자판의 좌우 여백 안쪽에만 칩을 놓는다. */
    void setSidePadding(int left, int right) {
        padLeft = left;
        padRight = right;
        if (bar != null) bar.setPadding(left, 0, right, 0);
    }

    /**
     * 자동 완성 서비스에 보낼 요청: 칩의 크기 범위와 모양. toolbarHeight는 도구 막대 높이(px), maxWidth는 칩 하나의 최대 폭.
     */
    InlineSuggestionsRequest request(KeyboardTheme theme, int toolbarHeight, int maxWidth) {
        chipHeight = Math.max(Ui.dp(context, 24), toolbarHeight - Ui.dp(context, 8));
        Size min = new Size(Ui.dp(context, 32), chipHeight);
        Size max = new Size(Math.max(min.getWidth(), maxWidth), chipHeight);
        InlinePresentationSpec spec = new InlinePresentationSpec.Builder(min, max)
                .setStyle(style(theme))
                .build();
        return new InlineSuggestionsRequest.Builder(Collections.singletonList(spec))
                .setMaxSuggestionCount(MAX_SUGGESTIONS)
                .build();
    }

    /** 서비스가 보낸 제안을 칩으로 만들어 보여 준다. 빈 목록이면 막대를 감춘다. */
    void show(List<InlineSuggestion> suggestions) {
        int gen = ++generation;
        removeChips();
        if (bar == null || suggestions == null || suggestions.isEmpty()) {
            setShown(false);
            return;
        }
        int n = suggestions.size();
        InlineContentView[] made = new InlineContentView[n];
        int[] pending = {n};
        Size size = new Size(ViewGroup.LayoutParams.WRAP_CONTENT, chipHeight);
        for (int i = 0; i < n; i++) {
            final int at = i;
            InlineSuggestion s = suggestions.get(i);
            try {
                s.inflate(context, size, context.getMainExecutor(), view -> {
                    if (gen != generation) return;   // 그사이 새 응답이 왔거나 입력란을 떠났다
                    made[at] = view;
                    if (--pending[0] == 0) place(suggestions, made);
                });
            } catch (RuntimeException e) {
                // 크기가 맞지 않는 등으로 만들지 못한 칩은 건너뛴다.
                if (--pending[0] == 0 && gen == generation) place(suggestions, made);
            }
        }
    }

    /** 칩을 받은 순서대로 놓는다 (고정 칩은 오른쪽 끝에 따로). 하나도 없으면 막대를 감춘다. */
    private void place(List<InlineSuggestion> suggestions, InlineContentView[] made) {
        if (bar == null) return;
        int margin = Ui.dp(context, 2);
        boolean any = false;
        for (int i = 0; i < made.length; i++) {
            InlineContentView v = made[i];
            if (v == null) continue;
            boolean isPinned = suggestions.get(i).getInfo().isPinned();
            // 시스템이 넘겨준 칩에는 실제로 그려진 크기가 LayoutParams로 들어 있다. InlineContentView는 스스로 크기를
            // 재지 못하므로(WRAP_CONTENT면 스크롤 안에서는 폭 0, 밖에서는 남는 폭을 모두 차지한다) 그 크기를 그대로 쓴다.
            ViewGroup.LayoutParams given = v.getLayoutParams();
            int w = given != null && given.width > 0 ? given.width : ViewGroup.LayoutParams.WRAP_CONTENT;
            int h = given != null && given.height > 0 ? given.height : chipHeight;
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(w, h);
            lp.leftMargin = margin;
            lp.rightMargin = margin;
            (isPinned ? pinned : chips).addView(v, lp);
            any = true;
        }
        scroll.scrollTo(0, 0);
        setShown(any);
        scroll.post(this::clipChips);
    }

    /**
     * 줄의 칩을 스크롤 칸 안에 보이는 부분만 그리게 자른다. 칩은 창 위에 따로 얹혀 그려지는 화면이라 그냥 두면 스크롤해도
     * 막대의 좌우 여백이나 고정 칩(열쇠) 위로 삐져나온다. 창 아래로 내리면 둥근 모서리가 네모로 비치고 스크롤이 끊기므로,
     * 창 위에 둔 채 잘라 낸다 (Android 12부터 칩 화면이 이 자르기를 따른다).
     */
    private void clipChips() {
        if (scroll == null || chips == null) return;
        int from = scroll.getScrollX(), to = from + scroll.getWidth();
        for (int i = 0; i < chips.getChildCount(); i++) {
            View v = chips.getChildAt(i);
            int left = Math.max(0, from - v.getLeft());
            int right = Math.min(v.getWidth(), to - v.getLeft());
            Rect want = right > left ? new Rect(left, 0, right, v.getHeight()) : new Rect();
            if (!want.equals(v.getClipBounds())) v.setClipBounds(want);
        }
    }

    /** 입력란을 떠났을 때: 칩을 모두 버리고 막대를 감춘다. 아직 만들고 있는 칩도 버린다. */
    void clear() {
        generation++;
        removeChips();
        setShown(false);
    }

    /** 보여 줄 칩이 있는지. */
    boolean shown() {
        return shown && bar != null;
    }

    /** 다른 패널(검색·이모지 등)을 보는 동안에는 막대를 가린다. 칩은 그대로 두었다가 자판으로 돌아오면 다시 보인다. */
    void setPanelVisible(boolean keyboardPanel) {
        if (bar != null) bar.setVisibility(shown && keyboardPanel ? View.VISIBLE : View.GONE);
    }

    private void removeChips() {
        if (chips != null) chips.removeAllViews();
        if (pinned != null) pinned.removeAllViews();
    }

    private void setShown(boolean on) {
        boolean changed = shown != on;
        shown = on;
        if (bar != null) {
            bar.setVisibility(on ? View.VISIBLE : View.GONE);
            if (on) bar.bringToFront();
        }
        if (changed) host.onAutofillBarChanged();
    }

    // ---------------------------------------------------------------- 칩 모양 (androidx.autofill.inline v1 형식)

    private static final String KEY_VERSIONS = "androidx.autofill.inline.ui.version:key";
    private static final String VERSION_1 = "androidx.autofill.inline.ui.version:v1";

    /** 칩 모양: 둥근 배경(키 색)에 글자(자판 글자 색), 부제목은 흐린 색. 아이콘만 있는 칩은 아이콘을 글자 색으로 칠한다. */
    private Bundle style(KeyboardTheme theme) {
        int h = Ui.dp(context, 12);
        Icon background = Icon.createWithResource(context, R.drawable.autofill_chip).setTint(theme.key);
        Bundle v1 = new Bundle();
        v1.putBoolean("style_v1", true);
        v1.putBundle("chip_style", viewStyle(background, new int[]{h, 0, h, 0}));
        v1.putBundle("title_style", textStyle(theme.text, 14));
        v1.putBundle("subtitle_style", textStyle(theme.hint, 12));
        int s = Ui.dp(context, 8);
        v1.putBundle("single_icon_chip_style", viewStyle(background, new int[]{s, 0, s, 0}));
        Bundle icon = new Bundle();
        icon.putBoolean("image_view_style", true);
        icon.putParcelable("image_tint_list", ColorStateList.valueOf(theme.text));
        v1.putBundle("single_icon_chip_icon_style", icon);

        Bundle styles = new Bundle();
        styles.putBundle(VERSION_1, v1);
        ArrayList<String> versions = new ArrayList<>();
        versions.add(VERSION_1);
        styles.putStringArrayList(KEY_VERSIONS, versions);
        return styles;
    }

    private static Bundle viewStyle(Icon background, int[] padding) {
        Bundle b = new Bundle();
        b.putBoolean("view_style", true);
        b.putParcelable("background", background);
        b.putIntArray("padding", padding);
        return b;
    }

    private static Bundle textStyle(int color, float sp) {
        Bundle b = new Bundle();
        b.putBoolean("text_view_style", true);
        b.putInt("text_color", color);
        b.putInt("text_size_unit", TypedValue.COMPLEX_UNIT_SP);
        b.putFloat("text_size", sp);
        return b;
    }
}
