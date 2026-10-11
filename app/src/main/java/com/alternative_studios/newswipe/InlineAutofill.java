package com.alternative_studios.newswipe;

import android.annotation.TargetApi;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.drawable.Icon;
import android.os.Bundle;
import android.util.Size;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.ViewConfiguration;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InlineSuggestion;
import android.view.inputmethod.InlineSuggestionsRequest;
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
 * 끝에 따로 놓는다. 칩은 스크롤하지 않고 줄에 온전히 들어가는 만큼씩 쪽으로 넘겨 본다 (좌우로 밀거나 화살표).
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

        /** 칩(또는 열쇠 버튼)을 눌렀다: 다른 도구 버튼처럼 누른 느낌(진동·소리)을 준다. */
        void onAutofillChipClicked();
    }

    private final Context context;
    private final Host host;
    private LinearLayout bar, pinned;
    private ChipRow row;
    private PageChevron prev, next;
    /** 스크롤하지 않는 대신 쪽(page)으로 넘긴다: 칩이 줄 폭에 다 들어가면 한 쪽이고, 아니면 칩 단위로 넘긴다. */
    private final List<InlineContentView> chipViews = new ArrayList<>();
    /** 지금 쪽의 첫 칩 번호. */
    private int first;
    /** 마지막으로 배치한 때의 칩 줄 폭 (바뀌었을 때만 다시 배치한다). */
    private int laidOutSlot = -1;
    /** 지금 보여 줄 칩이 있는지 (패널을 바꿔 막대를 잠깐 가려도 그대로다). */
    private boolean shown;
    /** 응답마다 늘어난다. 늦게 끝난 예전 응답의 칩 만들기 결과는 버린다. */
    private int generation;
    /** 요청할 때 정한 칩 높이 (px). 칩은 이 높이로 만든다 (요청한 크기 범위를 벗어나면 만들 수 없다). */
    private int chipHeight;

    InlineAutofill(Context context, Host host) {
        this.context = context;
        this.host = host;
    }

    /**
     * 도구 막대 안, 추천 단어 줄 자리에 놓을 칩 줄을 새로 만든다 (추천 단어처럼 왼쪽·오른쪽 도구 버튼 사이를 채운다).
     * 입력 화면을 다시 만들 때마다 부르고, 예전 막대와 칩은 버린다.
     */
    View createBar(KeyboardTheme theme) {
        // 예전 막대의 칩과 만들고 있던 칩은 버린다. 입력 화면을 만드는 중이라 IME에는 알리지 않는다
        // (새 막대는 감춘 채로 시작하고, 다음 응답이 오면 다시 보인다).
        generation++;
        removeChips();
        shown = false;
        bar = new LinearLayout(context);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setVisibility(View.GONE);
        // 칩은 서비스가 따로 그려 창 위에 얹는 화면이라 스크롤로 옮기면 가장자리에서 잘라 내는 시점이 칩 위치와 어긋나
        // 경계선이 흔들리고 도구 버튼 위로 잠깐 비쳐 보인다. 그래서 스크롤하지 않고, 줄에 온전히 들어가는 칩만 보여
        // 쪽(page)으로 넘긴다 (좌우로 밀거나 양옆 화살표를 누른다). 칩이 잘리는 일이 없다.
        prev = arrow(theme, -1, "이전 자동 완성");
        next = arrow(theme, 1, "다음 자동 완성");
        row = new ChipRow(context);
        // 이 쪽의 칩들을 칩 줄 가운데에 모아 놓는다 (가로·세로 모두 가운데).
        row.setGravity(Gravity.CENTER);
        bar.addView(prev, arrowParams());
        bar.addView(row, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT));
        bar.addView(next, arrowParams());
        // 남는 폭: 고정 칩(열쇠 등)을 오른쪽 끝으로 민다.
        bar.addView(new View(context), new LinearLayout.LayoutParams(0, 1, 1f));
        pinned = new LinearLayout(context);
        pinned.setGravity(Gravity.CENTER_VERTICAL);
        pinned.setPadding(Ui.dp(context, 4), 0, 0, 0);
        bar.addView(pinned, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        // 막대나 고정 칩의 폭이 정해지거나 바뀌면 쪽을 다시 나눈다.
        bar.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> relayoutPage());
        pinned.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> relayoutPage());
        return bar;
    }

    /** 쪽 넘김 표시의 폭(dp): 칩이 쓸 자리를 최대한 남기려고 얇은 〈 〉 모양만 둔다. */
    private static final int ARROW_DP = 18;
    private static final int PINNED_DP = 48;

    /** 칩 줄 폭에서 칩이 쓸 수 없는 폭 (px): 양옆 쪽 넘김 표시와 고정 칩(열쇠)의 대략적인 폭. 칩 최대 폭을 정할 때 뺀다. */
    int reservedWidth() {
        return Ui.dp(context, 2 * ARROW_DP + PINNED_DP);
    }

    private LinearLayout.LayoutParams arrowParams() {
        return new LinearLayout.LayoutParams(Ui.dp(context, ARROW_DP),
                chipHeight > 0 ? chipHeight : ViewGroup.LayoutParams.MATCH_PARENT);
    }

    /** 쪽을 넘기는 얇은 〈 〉: 배경 없이 모양만 그리고, 누르면 이전(-1)·다음(+1) 쪽으로 간다. */
    private PageChevron arrow(KeyboardTheme theme, int dir, String description) {
        PageChevron b = new PageChevron(context, theme.hint, dir < 0);
        b.setContentDescription(description);
        b.setBackground(Ui.ripple(theme.keyPressed, null, Ui.dp(context, 14)));
        b.setVisibility(View.GONE);
        b.setOnClickListener(v -> {
            host.onAutofillChipClicked();
            turnPage(dir);
        });
        return b;
    }

    /** 얇은 꺾쇠(〉, 왼쪽이면 〈)를 가운데에 그린다. */
    private static final class PageChevron extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path path = new Path();
        private final boolean left;

        PageChevron(Context c, int color, boolean left) {
            super(c);
            this.left = left;
            paint.setColor(color);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(Ui.dp(c, 2));
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setStrokeJoin(Paint.Join.ROUND);
            setClickable(true);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            float cx = getWidth() / 2f, cy = getHeight() / 2f;
            float hw = Ui.dp(getContext(), 3.5f) * (left ? -1 : 1), hh = Ui.dp(getContext(), 7f);
            path.reset();
            path.moveTo(cx - hw, cy - hh);
            path.lineTo(cx + hw, cy);
            path.lineTo(cx - hw, cy + hh);
            canvas.drawPath(path, paint);
        }
    }

    /**
     * 자동 완성 서비스에 보낼 요청: 칩의 크기 범위와 모양. toolbarHeight는 도구 막대 높이(px), maxWidth는 칩 하나의 최대 폭.
     */
    InlineSuggestionsRequest request(KeyboardTheme theme, int toolbarHeight, int maxWidth) {
        // 도구 막대 높이에서 위아래 6dp씩 남긴다. 도구 버튼은 아이콘(22dp)만 보이므로, 칩이 터치 영역 전체만큼 커 보이지 않고
        // 버튼들과 비슷한 높이로 느껴지게 한다 (기본 막대 40dp면 칩 28dp).
        chipHeight = Math.max(Ui.dp(context, 24), toolbarHeight - Ui.dp(context, 12));
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
            v.setOnClickListener(c -> host.onAutofillChipClicked());   // 누른 느낌(진동·소리). 채우기는 서비스가 한다
            boolean isPinned = suggestions.get(i).getInfo().isPinned();
            // 시스템이 넘겨준 칩에는 실제로 그려진 크기가 LayoutParams로 들어 있다. InlineContentView는 스스로 크기를
            // 재지 못하므로(WRAP_CONTENT면 칸 안에서 폭 0, 밖에서는 남는 폭을 모두 차지한다) 그 크기를 그대로 쓴다.
            ViewGroup.LayoutParams given = v.getLayoutParams();
            int w = given != null && given.width > 0 ? given.width : ViewGroup.LayoutParams.WRAP_CONTENT;
            int h = given != null && given.height > 0 ? given.height : chipHeight;
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(w, h);
            lp.leftMargin = margin;
            lp.rightMargin = margin;
            if (isPinned) {
                pinned.addView(v, lp);
            } else {
                row.addView(v, lp);
                chipViews.add(v);
            }
            any = true;
        }
        first = 0;
        laidOutSlot = -1;
        setShown(any);
        relayoutPage();
        bar.requestLayout();   // 아직 배치 전이면 막대가 배치될 때 위 리스너가 쪽을 나눈다
    }

    /** 이전(-1)·다음(+1) 쪽으로 넘긴다. 이미 끝이면 그대로. */
    private void turnPage(int dir) {
        int n = chipViews.size();
        if (n == 0) return;
        int target = dir > 0 ? first + Math.max(1, pageCount(first)) : prevPageStart();
        target = Math.max(0, Math.min(n - 1, target));
        if (target == first) return;
        first = target;
        laidOutSlot = -1;
        relayoutPage();
    }

    /** from번 칩부터 줄(row 폭)에 온전히 들어가는 칩 수 (적어도 1). */
    private int pageCount(int from) {
        int avail = row.getLayoutParams().width;
        int used = 0, count = 0;
        for (int i = from; i < chipViews.size(); i++) {
            int w = outerWidth(chipViews.get(i));
            if (count > 0 && used + w > avail) break;
            used += w;
            count++;
        }
        return count;
    }

    /** 지금 쪽의 바로 앞 쪽이 시작하는 칩 번호 (앞쪽에서 줄에 들어가는 만큼 거슬러 올라간다). */
    private int prevPageStart() {
        int avail = row.getLayoutParams().width;
        int used = 0, at = first;
        while (at > 0) {
            int w = outerWidth(chipViews.get(at - 1));
            if (at < first && used + w > avail) break;
            used += w;
            at--;
        }
        return at;
    }

    private static int outerWidth(View v) {
        LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) v.getLayoutParams();
        return Math.max(0, lp.width) + lp.leftMargin + lp.rightMargin;
    }

    /**
     * 칩을 쪽으로 나누어 배치한다. 막대 폭에서 고정 칩 폭을 뺀 만큼이 칩 줄 자리이고, 칩이 다 들어가면 한 쪽(화살표 없음),
     * 아니면 양옆 화살표 자리를 떼고 쪽을 나눈다. 쪽에 안 들어가는 칩은 숨긴다 (가리는 칩은 잘리지 않고 통째로 숨는다).
     */
    private void relayoutPage() {
        if (bar == null || row == null || chipViews.isEmpty()) return;
        int slot = bar.getWidth() - pinned.getWidth();
        if (slot <= 0 || slot == laidOutSlot) return;
        laidOutSlot = slot;
        int total = 0;
        for (View v : chipViews) total += outerWidth(v);
        boolean single = total <= slot;
        int arrowW = Ui.dp(context, ARROW_DP);
        int avail = single ? slot : Math.max(1, slot - 2 * arrowW);
        ViewGroup.LayoutParams rlp = row.getLayoutParams();
        if (rlp.width != avail) {
            rlp.width = avail;
            row.setLayoutParams(rlp);
        }
        for (View a : new View[]{prev, next}) {   // 버튼 높이는 칩 높이에 맞춘다 (칩 높이는 요청할 때 정해진다)
            ViewGroup.LayoutParams alp = a.getLayoutParams();
            if (chipHeight > 0 && alp.height != chipHeight) {
                alp.height = chipHeight;
                a.setLayoutParams(alp);
            }
        }
        if (single) first = 0;
        int count = pageCount(first);
        int shown = 0;
        for (int i = 0; i < chipViews.size(); i++) {
            InlineContentView v = chipViews.get(i);
            boolean on = i >= first && i < first + count;
            v.setVisibility(on ? View.VISIBLE : View.GONE);
            // 줄보다 넓은 칩이 혼자 남으면 줄 폭에서 잘라 도구 버튼 쪽으로 삐져나오지 않게 한다 (움직이지 않으니 어긋나지 않는다).
            v.setClipBounds(on && outerWidth(v) > avail ? new Rect(0, 0, Math.max(1, avail - 2 * Ui.dp(context, 2)), v.getHeight())
                    : null);
            if (on) shown++;
        }
        prev.setVisibility(single ? View.GONE : first > 0 ? View.VISIBLE : View.INVISIBLE);
        next.setVisibility(single ? View.GONE : first + count < chipViews.size() ? View.VISIBLE : View.INVISIBLE);
    }

    /** 칩 줄: 좌우로 밀면 쪽을 넘긴다 (스크롤하지 않고 손을 뗄 때 한 쪽씩). */
    private final class ChipRow extends LinearLayout {
        private final int slop = ViewConfiguration.get(getContext()).getScaledTouchSlop();
        private float downX, downY;
        private boolean dragging;

        ChipRow(Context c) {
            super(c);
        }

        @Override
        public boolean onInterceptTouchEvent(MotionEvent ev) {
            switch (ev.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    downX = ev.getX();
                    downY = ev.getY();
                    dragging = false;
                    break;
                case MotionEvent.ACTION_MOVE:
                    float dx = ev.getX() - downX, dy = ev.getY() - downY;
                    if (!dragging && Math.abs(dx) > slop * 2 && Math.abs(dx) > Math.abs(dy) * 1.5f) {
                        dragging = true;
                        getParent().requestDisallowInterceptTouchEvent(true);
                        return true;   // 칩에는 취소가 가고, 이 뒤는 onTouchEvent가 받는다
                    }
                    break;
                default:
                    break;
            }
            return dragging;
        }

        @Override
        public boolean onTouchEvent(MotionEvent ev) {
            switch (ev.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    return true;
                case MotionEvent.ACTION_UP:
                    float dx = ev.getX() - downX;
                    if (dragging && Math.abs(dx) > slop * 3) turnPage(dx < 0 ? 1 : -1);
                    dragging = false;
                    return true;
                case MotionEvent.ACTION_CANCEL:
                    dragging = false;
                    return true;
                default:
                    return true;
            }
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

    private void removeChips() {
        chipViews.clear();
        first = 0;
        laidOutSlot = -1;
        if (row != null) row.removeAllViews();
        if (pinned != null) pinned.removeAllViews();
    }

    private void setShown(boolean on) {
        boolean changed = shown != on;
        shown = on;
        if (bar != null) {
            bar.setVisibility(on ? View.VISIBLE : View.GONE);
        }
        if (changed) host.onAutofillBarChanged();
    }

    // ---------------------------------------------------------------- 칩 모양 (androidx.autofill.inline v1 형식)

    private static final String KEY_VERSIONS = "androidx.autofill.inline.ui.version:key";
    private static final String VERSION_1 = "androidx.autofill.inline.ui.version:v1";

    /** 칩 모양: 둥근 배경(키 색)에 글자(자판 글자 색), 부제목은 흐린 색. 아이콘만 있는 칩은 아이콘을 글자 색으로 칠한다. */
    private Bundle style(KeyboardTheme theme) {
        int h = Ui.dp(context, 14);
        // 누르면 배경이 눌린 키 색으로 바뀌도록 색 상태 목록으로 칠한다. 칩은 서비스가 그리는 화면이지만 배경 그림은
        // 이 목록으로 칠해져 오고, 칩 화면이 눌리면 그 상태(pressed)에 맞는 색이 된다.
        ColorStateList fill = new ColorStateList(
                new int[][]{{android.R.attr.state_pressed}, {}}, new int[]{theme.keyPressed, theme.key});
        Icon background = Icon.createWithResource(context, R.drawable.autofill_chip).setTintList(fill);
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
