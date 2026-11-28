package com.alternative_studios.newswipe.ui;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.text.TextPaint;
import android.text.TextUtils;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Interpolator;
import android.view.animation.PathInterpolator;
import android.widget.FrameLayout;

import com.alternative_studios.newswipe.keyboard.Icons;

/**
 * Metro UI: 메인 화면의 메뉴를 Windows Phone 8 시작 화면처럼 라이브 타일로 놓는 판.
 *
 * <p>크기: WP8 SDK의 타일 크기(WXGA 768px 폭 화면 기준, "Tiles for Windows Phone 8")는 작은 타일 159×159, 중간 336×336,
 * 넓은 타일 691×336이다. 넓은 타일은 중간 둘과 그 사이 간격이므로 간격은 691 - 2×336 = 19px, 중간 타일은 작은 둘과 간격
 * (2×159 + 18 = 336)이다. 480px 폭(WVGA, ×0.625)으로 옮기면 작은 99, 중간 210, 넓은 432×210, 간격 12px이고, 화면 왼쪽 여백
 * 24px + 210 + 12 + 210 = 456으로 오른쪽에도 24px가 남는다. 이 앱은 그 값을 {@link Metro#px}로 dp에 옮기고, 판의 실제 폭에
 * 맞춰 작은 타일 한 칸의 크기를 정한다 (폰은 중간 타일 두 줄 = 작은 칸 네 줄, 넓은 화면은 중간 타일이 더 들어가는 만큼 칸을 늘린다:
 * WP8 GDR3의 '타일 더 보기'처럼).
 *
 * <p>놓는 차례: 시작 화면처럼 차례대로 위에서부터 빈 자리를 채운다. 중간·넓은 타일은 작은 칸 둘 단위(짝수 칸)에 맞추고, 작은
 * 타일은 중간 타일 하나 자리를 넷이서 나눠 쓴다.
 *
 * <p>모양과 움직임 (Flip Tile 템플릿, "Flip Tile template design guidelines"):
 * <ul>
 *   <li>강조색 네모, 모서리·그림자 없음. 글자와 아이콘은 흰색 (아주 밝은 강조색은 {@link Metro#onAccent}의 짙은 색).</li>
 *   <li>앞면: 아이콘, 왼쪽 아래에 제목 (중간 19자·넓은 39자쯤). 작은 타일은 제목도 뒷면도 없다 (작은 타일의 Title·BackTitle·
 *       BackContent는 NA).</li>
 *   <li>뒷면: 위쪽에 BackContent (중간은 13자씩 3줄, 넓은 타일은 27자씩 3줄쯤, 글은 저절로 줄바꿈되지 않으므로 직접 끊는다),
 *       왼쪽 아래에 BackTitle.</li>
 *   <li>타일은 정해지지 않은 간격으로 앞뒤가 뒤집힌다 (가로축을 중심으로 돈다). 한 번에 한 타일만, 화면이 보이는 동안만,
 *       시스템 애니메이션이 꺼져 있으면 뒤집지 않는다.</li>
 *   <li>누르면 틸트 ({@link Metro#tilt}), 다른 화면으로 넘어갈 때는 다른 타일들이 깃털처럼 돌아 나간 뒤 누른 타일이 마지막으로
 *       돌아 나간다 (SettingsFrame이 정한다). 타일은 판 전체의 왼쪽 바깥을 축으로 돌아, 판이 한 장처럼 돈다 ({@link Metro}의 turn).</li>
 * </ul>
 */
public final class MetroTiles extends ViewGroup {
    public static final int SMALL = 0, MEDIUM = 1, WIDE = 2;

    /** 480px 폭 화면 기준의 중간 타일 한 변과 타일 사이 간격 (px). */
    private static final float MEDIUM_WP = 210f, GAP_WP = 12f;
    /** 타일 안의 글: 왼쪽 여백 12px, 제목의 아래 여백 6px, 글자 PhoneFontSizeNormal 20px (중간 타일 210px 기준, 타일 크기에 비례). */
    private static final float TEXT_LEFT_WP = 12f, TITLE_BOTTOM_WP = 6f, BACK_TOP_WP = 6f, FONT_WP = 20f;
    /** Segoe WP의 줄 높이·ascent (em). */
    private static final float LINE_EM = 1.33f, ASCENT_EM = 1.0791f;

    private final int gap;
    private final float mediumTarget;
    /** 작은 칸의 가로 수 (짝수, 4 이상)와 작은 칸 한 변 (px). onMeasure가 정한다. */
    private int units = 4, cell;

    public MetroTiles(Context c) {
        super(c);
        gap = Metro.px(c, GAP_WP);
        mediumTarget = Metro.px(c, MEDIUM_WP);
        setClipChildren(false);   // 틸트·턴스타일로 돌며 판 밖으로 나오는 가장자리를 자르지 않는다
        setClipToPadding(false);
    }

    /** 타일을 더한다 (놓는 차례 = 더한 차례). */
    public Tile add(int size, int icon, String title, String backContent, String accessibleName, View.OnClickListener l) {
        Tile t = new Tile(getContext(), size, icon, title, backContent);
        t.setContentDescription(accessibleName);
        t.setOnClickListener(l);
        addView(t);
        return t;
    }

    // ---------------------------------------------------------------- 배치

    @Override
    protected void onMeasure(int widthSpec, int heightSpec) {
        int width = MeasureSpec.getSize(widthSpec);
        if (MeasureSpec.getMode(widthSpec) == MeasureSpec.UNSPECIFIED) width = Math.round(2 * mediumTarget + gap);
        int inner = Math.max(0, width - getPaddingLeft() - getPaddingRight());
        // 중간 타일이 WP8의 크기(210px)보다 작아지지 않는 만큼만 줄을 늘린다 (반올림 오차로 폰에서 한 줄이 되지 않게 조금 봐준다).
        int mediums = Math.max(2, (int) Math.floor((inner + gap) / (mediumTarget + gap) + 0.02f));
        units = mediums * 2;
        cell = Math.max(1, (inner - (units - 1) * gap) / units);
        int rows = place();
        for (int i = 0; i < getChildCount(); i++) {
            View v = getChildAt(i);
            if (v.getVisibility() == GONE) continue;
            int[] s = span(v);
            v.measure(MeasureSpec.makeMeasureSpec(extent(s[0]), MeasureSpec.EXACTLY),
                    MeasureSpec.makeMeasureSpec(extent(s[1]), MeasureSpec.EXACTLY));
        }
        int height = rows == 0 ? 0 : extent(rows);
        setMeasuredDimension(width, height + getPaddingTop() + getPaddingBottom());
    }

    /** n칸에 걸친 길이 (칸 사이 간격 포함). */
    private int extent(int n) {
        return n * cell + (n - 1) * gap;
    }

    /** 타일 크기의 {가로 칸, 세로 칸}. */
    private static int[] span(View v) {
        int size = v instanceof Tile ? ((Tile) v).size : SMALL;
        switch (size) {
            case WIDE: return new int[]{4, 2};
            case MEDIUM: return new int[]{2, 2};
            default: return new int[]{1, 1};
        }
    }

    /** 타일마다 {칸, 줄}을 정해 태그처럼 Tile에 적고 쓴 줄 수를 돌려준다. 앞에서부터 들어갈 수 있는 첫 빈 자리 (중간·넓은 타일은 짝수 칸·줄). */
    private int place() {
        java.util.List<boolean[]> used = new java.util.ArrayList<>();
        int rows = 0;
        for (int i = 0; i < getChildCount(); i++) {
            View v = getChildAt(i);
            if (v.getVisibility() == GONE || !(v instanceof Tile)) continue;
            Tile t = (Tile) v;
            int[] s = span(t);
            int w = Math.min(s[0], units), h = s[1];
            int step = t.size == SMALL ? 1 : 2;
            boolean placed = false;
            for (int r = 0; !placed; r += step) {
                for (int c = 0; c + w <= units; c += step) {
                    if (free(used, r, c, w, h)) {
                        for (int y = r; y < r + h; y++) {
                            while (used.size() <= y) used.add(new boolean[units]);
                            for (int x = c; x < c + w; x++) used.get(y)[x] = true;
                        }
                        t.col = c;
                        t.row = r;
                        rows = Math.max(rows, r + h);
                        placed = true;
                        break;
                    }
                }
            }
        }
        return rows;
    }

    private static boolean free(java.util.List<boolean[]> used, int r, int c, int w, int h) {
        for (int y = r; y < r + h; y++) {
            if (y >= used.size()) continue;
            for (int x = c; x < c + w; x++) if (used.get(y)[x]) return false;
        }
        return true;
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        for (int i = 0; i < getChildCount(); i++) {
            View v = getChildAt(i);
            if (v.getVisibility() == GONE || !(v instanceof Tile)) continue;
            Tile tile = (Tile) v;
            int x = getPaddingLeft() + tile.col * (cell + gap), y = getPaddingTop() + tile.row * (cell + gap);
            v.layout(x, y, x + v.getMeasuredWidth(), y + v.getMeasuredHeight());
        }
    }

    /** 턴스타일로 들고 날 차례: 화면에 놓인 자리대로 위에서 아래, 같은 줄은 왼쪽에서 오른쪽. */
    public void featherOrder(java.util.List<View> out) {
        java.util.List<Tile> tiles = new java.util.ArrayList<>();
        for (int i = 0; i < getChildCount(); i++) {
            View v = getChildAt(i);
            if (v.getVisibility() == VISIBLE && v instanceof Tile) tiles.add((Tile) v);
        }
        java.util.Collections.sort(tiles, (a, b) -> a.row != b.row ? Integer.compare(a.row, b.row) : Integer.compare(a.col, b.col));
        out.addAll(tiles);
    }

    // ---------------------------------------------------------------- 라이브 타일 뒤집기

    /**
     * 다음 뒤집기까지의 간격 (ms): 지침은 "정해지지 않은 간격으로 뒤집힌다"고만 하므로, 판 전체에서 2.5~5초마다 한 타일을 골라
     * 뒤집는다 (중간·넓은 타일 일곱 개면 한 타일은 20초 남짓에 한 번 앞뒤가 바뀐다).
     */
    private static final long FLIP_MIN_MS = 2500, FLIP_RANGE_MS = 2500;
    /**
     * 한꺼번에 뒷면을 보일 수 있는 타일 수. 이만큼 뒤집혀 있으면 그 가운데 하나가 앞면으로 돌아와야 다른 타일이 뒤집힌다
     * (뒤집히는 중인 타일도 넘어갈 면으로 센다).
     */
    private static final int MAX_BACK = 2;
    private final java.util.Random random = new java.util.Random();
    private final Runnable flipTick = this::flipOne;
    private boolean ticking;

    private void flipOne() {
        ticking = false;
        if (!mayFlip()) return;
        java.util.List<Tile> candidates = new java.util.ArrayList<>();
        android.graphics.Rect seen = new android.graphics.Rect();
        int backs = 0;
        for (int i = 0; i < getChildCount(); i++) {
            View v = getChildAt(i);
            if (v instanceof Tile && ((Tile) v).showsBack()) backs++;
        }
        // 뒷면이 MAX_BACK개면 뒷면인 타일만 (앞면으로 돌아온다) 고른다.
        boolean onlyBacks = backs >= MAX_BACK;
        for (int i = 0; i < getChildCount(); i++) {
            View v = getChildAt(i);
            if (!(v instanceof Tile)) continue;
            Tile t = (Tile) v;
            // 작은 타일은 뒷면이 없다. 누르고 있거나 턴스타일·틸트로 돌고 있는 타일, 화면 밖의 타일, 막아 둔 타일은 건너뛴다.
            if (t.size == SMALL || !t.hasBack() || !t.isEnabled() || t.isPressed() || t.flipping()
                    || t.getRotationX() != 0f || t.getRotationY() != 0f || t.getAlpha() < 1f
                    || !t.getGlobalVisibleRect(seen) || seen.height() < t.getHeight()) continue;
            if (onlyBacks && !t.showsBack()) continue;
            candidates.add(t);
        }
        if (!candidates.isEmpty()) candidates.get(random.nextInt(candidates.size())).flip();
        schedule();
    }

    /** 뒤집어도 되는지: 창에 붙어 보이고 있고, 시스템 애니메이션이 켜져 있다. */
    private boolean mayFlip() {
        return isAttachedToWindow() && getWindowVisibility() == VISIBLE && isShown() && ValueAnimator.areAnimatorsEnabled();
    }

    private void schedule() {
        removeCallbacks(flipTick);
        ticking = false;
        if (!mayFlip()) return;
        ticking = true;
        postDelayed(flipTick, FLIP_MIN_MS + (long) (random.nextFloat() * FLIP_RANGE_MS));
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        post(this::schedule);
    }

    @Override
    protected void onDetachedFromWindow() {
        removeCallbacks(flipTick);
        ticking = false;
        resetFaces();
        super.onDetachedFromWindow();
    }

    /**
     * 모든 타일을 움직임 없이 앞면으로 되돌린다. 메인 화면을 벗어나면(다른 화면이 덮어 창이 가려지거나, 설정 검색으로 판이 숨으면)
     * 부른다: 돌아오면 판은 처음 모습으로 들어온다. 화면이 가려진 뒤라 바뀌는 모습은 보이지 않는다.
     */
    private void resetFaces() {
        for (int i = 0; i < getChildCount(); i++) {
            View v = getChildAt(i);
            if (v instanceof Tile) ((Tile) v).face.reset();
        }
    }

    @Override
    protected void onWindowVisibilityChanged(int visibility) {
        super.onWindowVisibilityChanged(visibility);
        if (visibility == VISIBLE) {
            if (!ticking) post(this::schedule);
        } else {
            removeCallbacks(flipTick);
            ticking = false;
            resetFaces();
        }
    }

    @Override
    protected void onVisibilityChanged(View changed, int visibility) {
        super.onVisibilityChanged(changed, visibility);
        // 설정 검색 중에는 판이 숨는다: 그동안 쉬며 앞면으로 되돌리고, 다시 보이면 처음부터 이어 간다.
        if (!isAttachedToWindow()) return;
        if (isShown()) {
            if (!ticking) post(this::schedule);
        } else {
            removeCallbacks(flipTick);
            ticking = false;
            resetFaces();
        }
    }

    // ---------------------------------------------------------------- 타일

    /**
     * 타일 하나. 누르는 틀(틸트·턴스타일이 이 뷰를 돌린다)과 그 안의 면(앞뒤를 그리고, 뒤집기가 이 면을 돌린다)으로 나눠, 움직임이
     * 서로의 회전값을 덮어쓰지 않게 한다. 막아 둔 타일을 흐리게 하는 불투명도도 면에 건다.
     */
    public static final class Tile extends FrameLayout {
        final int size;
        int col, row;
        private final Face face;

        Tile(Context c, int size, int icon, String title, String backContent) {
            super(c);
            this.size = size;
            face = new Face(c, size, icon, title, backContent);
            addView(face, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
            setClickable(true);
            setFocusable(true);
            setClipChildren(false);
            Metro.tilt(this);
        }

        boolean hasBack() {
            return face.hasBack();
        }

        boolean flipping() {
            return face.flip != null;
        }

        /** 뒷면을 보이고 있거나 뒷면으로 넘어가는 중인지. */
        boolean showsBack() {
            return face.target;
        }

        void flip() {
            face.flip();
        }

        @Override
        public CharSequence getAccessibilityClassName() {
            return android.widget.Button.class.getName();
        }
    }

    /**
     * 뒤집기: 면이 가로축(가운데)을 중심으로 위쪽 가장자리가 뒤로 넘어가며 90°까지 돌고(천천히 시작해 빨라진다), 그 자리에서 다른 면으로
     * 바뀌어 -90°에서 0°로 돌아와 선다(빠르게 들어와 부드럽게 멈춘다). 반쪽마다 400ms.
     */
    private static final long FLIP_HALF_MS = 400;
    private static final Interpolator FLIP_IN = new PathInterpolator(0.55f, 0f, 1f, 0.45f),
            FLIP_OUT = new PathInterpolator(0f, 0.55f, 0.45f, 1f);

    /** 타일의 앞면·뒷면을 그리는 면. */
    private static final class Face extends View {
        private final int size, icon;
        private final String title, backContent;
        private final Paint iconPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint bgPaint = new Paint();
        private final TextPaint textPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        /** 지금 그리는 면 (뒤집기의 반쯤에서 바뀐다). */
        private boolean back;
        /** 넘어갈(또는 이미 보이는) 면: 뒤집기를 시작할 때 바뀐다. */
        boolean target;
        ValueAnimator flip;
        /** 뒷면 글을 끊어 둔 줄 (폭이 바뀌면 다시 끊는다). */
        private String[] backLines;
        private int linesWidth = -1;

        Face(Context c, int size, int icon, String title, String backContent) {
            super(c);
            this.size = size;
            this.icon = icon;
            this.title = title == null ? "" : title;
            this.backContent = backContent == null ? "" : backContent;
            textPaint.setTypeface(Metro.regular());
            setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        }

        boolean hasBack() {
            return size != SMALL && !backContent.isEmpty();
        }

        /** 이 타일의 크기 비율: 중간 타일 한 변(넓은 타일은 높이)을 480px 화면의 210px로 본다. 작은 타일은 그 절반쯤. */
        private float scale() {
            float side = size == SMALL ? getHeight() * MEDIUM_WP / 99f : getHeight();
            return side / MEDIUM_WP;
        }

        void flip() {
            if (flip != null || getWidth() == 0) return;
            target = !back;
            Metro.perspective(this);
            setPivotX(getWidth() / 2f);
            setPivotY(getHeight() / 2f);
            ValueAnimator a = ValueAnimator.ofFloat(0f, 2f);
            a.setDuration(FLIP_HALF_MS * 2);
            a.setInterpolator(new android.view.animation.LinearInterpolator());   // 반쪽마다 따로 곡선을 건다
            final boolean[] swapped = {false};
            a.addUpdateListener(an -> {
                float f = (float) an.getAnimatedValue();
                if (f < 1f) {
                    setRotationX(90f * FLIP_IN.getInterpolation(f));
                } else {
                    if (!swapped[0]) {
                        swapped[0] = true;
                        back = !back;
                        invalidate();
                    }
                    setRotationX(-90f * (1f - FLIP_OUT.getInterpolation(f - 1f)));
                }
            });
            a.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    // 끝났거나 멈췄다: 지금 그리는 면으로 곧게 세운다 (반도 돌기 전에 멈췄으면 원래 면).
                    if (flip != animation) return;   // reset이 이미 정리했다
                    target = back;
                    setRotationX(0f);
                    resetPivot();
                    flip = null;
                }
            });
            flip = a;
            a.start();
        }

        /** 뒤집기를 멈추고 앞면으로 곧게 세운다. */
        void reset() {
            ValueAnimator a = flip;
            flip = null;   // 먼저 지워 멈춘 움직임의 끝 처리가 끼어들지 않게
            if (a != null) a.cancel();
            boolean changed = back;
            back = false;
            target = false;
            setRotationX(0f);
            resetPivot();
            if (changed) invalidate();
        }

        @Override
        protected void onDetachedFromWindow() {
            if (flip != null) flip.cancel();
            super.onDetachedFromWindow();
        }

        @Override
        protected void onDraw(Canvas c) {
            int w = getWidth(), h = getHeight();
            // 바탕은 자기 영역에만 칠한다. drawColor는 캔버스의 클립 전체를 칠하는데, 판과 타일은 틸트·턴스타일로 튀어나온
            // 가장자리를 자르지 않으려 자식을 자르지 않으므로(setClipChildren(false)) 클립이 목록 전체라 다른 타일까지 덮는다.
            bgPaint.setColor(Metro.accent);
            c.drawRect(0, 0, w, h, bgPaint);
            int ink = Metro.onAccent;
            float s = scale();
            float left = TEXT_LEFT_WP * s, font = FONT_WP * s;
            textPaint.setColor(ink);
            textPaint.setTextSize(font);
            if (back && hasBack()) {
                // 뒷면: 위쪽에 BackContent 세 줄, 왼쪽 아래에 BackTitle.
                if (linesWidth != w) {
                    linesWidth = w;
                    backLines = wrap(backContent, w - 2 * left, 3);
                }
                float y = BACK_TOP_WP * s + ASCENT_EM * font;
                for (String line : backLines) {
                    c.drawText(line, left, y, textPaint);
                    y += LINE_EM * font;
                }
                drawTitle(c, w, h, s, left, font);
                return;
            }
            // 앞면: 가운데 아이콘. 중간·넓은 타일은 중간 타일 크기에 맞춘 같은 크기, 작은 타일은 타일의 절반.
            iconPaint.setColor(ink);
            float iconSize = size == SMALL ? h * 0.5f : h * 0.36f;
            Icons.draw(c, icon, w / 2f, h / 2f, iconSize, iconPaint, 1.6f);
            if (size != SMALL) drawTitle(c, w, h, s, left, font);
        }

        /** 왼쪽 아래의 제목 (한 줄, 넘치면 말줄임). */
        private void drawTitle(Canvas c, int w, int h, float s, float left, float font) {
            if (title.isEmpty()) return;
            CharSequence t = TextUtils.ellipsize(title, textPaint, w - 2 * left, TextUtils.TruncateAt.END);
            float baseline = h - TITLE_BOTTOM_WP * s - (LINE_EM - ASCENT_EM) * font;
            c.drawText(t, 0, t.length(), left, baseline, textPaint);
        }

        /** text를 폭 max에 맞춰 낱말 단위로 끊는다 (낱말이 너무 길면 글자 단위). 줄이 넘치면 마지막 줄을 말줄임한다. */
        private String[] wrap(String text, float max, int maxLines) {
            java.util.List<String> lines = new java.util.ArrayList<>();
            String rest = text.trim();
            while (!rest.isEmpty() && lines.size() < maxLines) {
                if (lines.size() == maxLines - 1 || textPaint.measureText(rest) <= max) {
                    lines.add(TextUtils.ellipsize(rest, textPaint, max, TextUtils.TruncateAt.END).toString());
                    break;
                }
                int fit = textPaint.breakText(rest, true, max, null);
                int cut = fit;
                int space = rest.lastIndexOf(' ', fit);
                if (space > 0) cut = space;
                if (cut <= 0) cut = Math.max(1, fit);
                lines.add(rest.substring(0, cut).trim());
                rest = rest.substring(cut).trim();
            }
            return lines.toArray(new String[0]);
        }
    }
}
