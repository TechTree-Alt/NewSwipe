package com.alternative_studios.newswipe.ui;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Bitmap;
import android.graphics.Paint;
import android.graphics.RectF;
import android.text.TextPaint;
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
 *   <li>강조색 네모, 모서리·그림자 없음. 글자와 아이콘은 흰색 (WP8 강조색이 아닌 아주 밝은 색만 {@link Metro#onAccent}의 짙은 색).</li>
 *   <li>앞면: 가운데 흰 단색 아이콘, 왼쪽 아래에 첫 글자가 대문자인 제목 (중간 19자·넓은 39자쯤, 넘치면 말줄임 없이 타일 끝에서
 *       잘린다). 작은 타일은 제목도 뒷면도 없다 (작은 타일의 Title·BackTitle·BackContent는 NA).
 *       아이콘은 Iconic 템플릿처럼 그림의 실제 높이로 맞춘다: 공식 Mail 타일(중간)의 그림은 타일의 세로 29%·가로 38%이고, 지침의
 *       "best fit"은 336px 타일에 130px(39%)까지다. 작은 타일의 그림은 타일의 44~49% (공식 IE·음악 타일).</li>
 *   <li>Iconic 템플릿의 숫자 ({@link Tile#setCount}): 그림 오른쪽에 그림과 세로 가운데를 맞춰 놓고, 그림과 숫자를 한 덩어리로
 *       가로 가운데에 둔다 (공식 도식: 숫자 높이는 중간 타일의 21%·작은 타일의 18%, 그림과의 간격은 7%·11%).</li>
 *   <li>사진 타일 ({@link Tile#setArt}): 앞면 전체를 그림으로 채우고 제목은 그 위에 그림에 맞는 색으로 쓴다 (People·사진 타일처럼).</li>
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
    /**
     * 타일 안의 글: 왼쪽 여백 12px, 제목의 아래 여백 6px (중간 타일 210px 기준, 타일 크기에 비례). 글자 크기는 공식 도식에서 잰 값:
     * 제목의 대문자 높이가 타일의 5.2~6.0% (Mail·Contoso)라 17px, 뒷면 글은 대문자 높이 7.8%·줄 간격 11%라 23px에 줄 간격 1.05em.
     */
    private static final float TEXT_LEFT_WP = 12f, TITLE_BOTTOM_WP = 6f, BACK_TOP_WP = 6f, TITLE_FONT_WP = 17f, BACK_FONT_WP = 23f;
    /** 제목 줄의 높이·ascent (em), 뒷면 글의 줄 간격 (em). */
    private static final float LINE_EM = 1.33f, ASCENT_EM = 1.0791f, BACK_LINE_EM = 1.05f;
    /** 숫자의 높이 (em, Roboto의 숫자·대문자 높이). */
    private static final float DIGIT_EM = 0.711f;
    /** 타일 아이콘의 선 굵기 (24칸 기준). 그림을 줄인 만큼 선이 가늘어 보이지 않게 기본(1.9)보다 조금 굵다: 공식 Mail 그림의 선은 그림 높이의 12%. */
    private static final float ICON_STROKE = 2.2f;

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

        /** Iconic 템플릿의 숫자 (그림 오른쪽). 0 이하면 숫자 없이 그림만 가운데. 99를 넘으면 99. */
        public void setCount(int count) {
            int n = Math.max(0, Math.min(99, count));
            if (face.count == n) return;
            face.count = n;
            face.invalidate();
        }

        /** 뒷면 글을 바꾼다. */
        public void setBackContent(String text) {
            String t = text == null ? "" : text;
            if (t.equals(face.backContent)) return;
            face.backContent = t;
            face.linesWidth = -1;
            face.invalidate();
        }

        /** 앞면을 그림으로 채운다 (사진 타일). ink는 그림 위에 쓰는 제목 색. null이면 다시 아이콘. */
        public void setArt(Art art, int ink) {
            face.art = art;
            face.artInk = ink;
            face.invalidate();
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

    /** 아이콘마다 그림이 실제로 차지하는 영역 (24칸 기준). 한 번 그려 보고 잰다. */
    private static final android.util.SparseArray<RectF> GLYPHS = new android.util.SparseArray<>();

    static RectF glyph(int icon) {
        RectF r = GLYPHS.get(icon);
        if (r != null) return r;
        final int n = 96;
        Bitmap b = Bitmap.createBitmap(n, n, Bitmap.Config.ARGB_8888);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(0xFFFFFFFF);
        Icons.draw(new Canvas(b), icon, n / 2f, n / 2f, n, p, ICON_STROKE);
        int[] px = new int[n * n];
        b.getPixels(px, 0, n, 0, 0, n, n);
        b.recycle();
        int l = n, t = n, rr = -1, bb = -1;
        for (int y = 0; y < n; y++) {
            for (int x = 0; x < n; x++) {
                if ((px[y * n + x] >>> 24) < 64) continue;
                if (x < l) l = x;
                if (x > rr) rr = x;
                if (y < t) t = y;
                if (y > bb) bb = y;
            }
        }
        float k = 24f / n;
        r = rr < 0 ? new RectF(2, 2, 22, 22) : new RectF(l * k, t * k, (rr + 1) * k, (bb + 1) * k);
        GLYPHS.put(icon, r);
        return r;
    }

    /** 사진 타일의 앞면 그림. */
    public interface Art {
        void draw(Canvas c, int w, int h);
    }

    /**
     * 키보드를 작게 그린 그림 (키보드 테마의 색): 네 줄의 키, 맨 아래 줄은 기능키·스페이스바·강조색 엔터. 제목이 들어갈 아래쪽은
     * 비워 둔다. 그림은 파일 없이 그때그때 그린다.
     */
    public static final class KeyboardArt implements Art {
        private final int background, key, functionKey, accent;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

        public KeyboardArt(int background, int key, int functionKey, int accent) {
            this.background = background;
            this.key = key;
            this.functionKey = functionKey;
            this.accent = accent;
        }

        @Override
        public void draw(Canvas c, int w, int h) {
            paint.setColor(background);
            c.drawRect(0, 0, w, h, paint);
            float pad = 0.045f * h, gap = 0.03f * h, top = pad, bottom = 0.74f * h;
            float rowH = (bottom - top - 3 * gap) / 4f, left = pad, width = w - 2 * pad;
            float unit = (width - 9 * gap) / 10f;
            // 1·2줄: 글자 키 10개, 9개 (둘째 줄은 반 칸 들여 놓는다).
            for (int i = 0; i < 10; i++) key(c, left + i * (unit + gap), top, unit, rowH, key);
            float y = top + rowH + gap;
            for (int i = 0; i < 9; i++) key(c, left + (unit + gap) / 2f + i * (unit + gap), y, unit, rowH, key);
            // 3줄: Shift, 글자 키 7개, 지우기.
            y += rowH + gap;
            float side = (width - 7 * unit - 8 * gap) / 2f;
            key(c, left, y, side, rowH, functionKey);
            for (int i = 0; i < 7; i++) key(c, left + side + gap + i * (unit + gap), y, unit, rowH, key);
            key(c, left + width - side, y, side, rowH, functionKey);
            // 4줄: 기호, 지구본, 스페이스바, 마침표, 엔터.
            y += rowH + gap;
            float wide = unit * 1.5f + gap / 2f, x = left;
            key(c, x, y, wide, rowH, functionKey);
            x += wide + gap;
            key(c, x, y, unit, rowH, functionKey);
            x += unit + gap;
            float space = width - 2 * wide - 2 * unit - 4 * gap;
            key(c, x, y, space, rowH, key);
            x += space + gap;
            key(c, x, y, unit, rowH, functionKey);
            x += unit + gap;
            key(c, x, y, wide, rowH, accent);
        }

        private void key(Canvas c, float x, float y, float w, float h, int color) {
            paint.setColor(color);
            c.drawRect(x, y, x + w, y + h, paint);
        }
    }

    /** 타일의 앞면·뒷면을 그리는 면. */
    private static final class Face extends View {
        private final int size, icon;
        private final String title;
        String backContent;
        int count;
        Art art;
        int artInk;
        private final Paint iconPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint bgPaint = new Paint();
        private final TextPaint textPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        private final Paint countPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        /** 지금 그리는 면 (뒤집기의 반쯤에서 바뀐다). */
        private boolean back;
        /** 넘어갈(또는 이미 보이는) 면: 뒤집기를 시작할 때 바뀐다. */
        boolean target;
        ValueAnimator flip;
        /** 뒷면 글을 끊어 둔 줄 (폭이 바뀌면 다시 끊는다). */
        private String[] backLines;
        int linesWidth = -1;

        Face(Context c, int size, int icon, String title, String backContent) {
            super(c);
            this.size = size;
            this.icon = icon;
            this.title = title == null ? "" : title;
            this.backContent = backContent == null ? "" : backContent;
            textPaint.setTypeface(Metro.regular());
            countPaint.setTypeface(Metro.regular());
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
            float s = scale();
            float left = TEXT_LEFT_WP * s, title = TITLE_FONT_WP * s;
            if (art != null && !back) {
                // 사진 타일의 앞면: 그림이 타일을 채우고 제목은 그 위에.
                c.save();
                c.clipRect(0, 0, w, h);
                art.draw(c, w, h);
                textPaint.setColor(artInk);
                textPaint.setTextSize(title);
                if (size != SMALL) drawTitle(c, h, s, left, title);
                c.restore();
                return;
            }
            // 바탕은 자기 영역에만 칠한다. drawColor는 캔버스의 클립 전체를 칠하는데, 판과 타일은 틸트·턴스타일로 튀어나온
            // 가장자리를 자르지 않으려 자식을 자르지 않으므로(setClipChildren(false)) 클립이 목록 전체라 다른 타일까지 덮는다.
            bgPaint.setColor(Metro.accent);
            c.drawRect(0, 0, w, h, bgPaint);
            int ink = ink(Metro.accent);
            textPaint.setColor(ink);
            if (back && hasBack()) {
                // 뒷면: 위쪽에 BackContent 세 줄, 왼쪽 아래에 BackTitle.
                float font = BACK_FONT_WP * s;
                textPaint.setTextSize(font);
                if (linesWidth != w) {
                    linesWidth = w;
                    backLines = wrap(backContent, w - 2 * left, 3);
                }
                // 글은 타일 밖으로 나가지 않게 타일 가장자리에서 자른다 (WP8 시작 화면처럼 말줄임표 없이 잘린다).
                c.save();
                c.clipRect(0, 0, w, h);
                float y = BACK_TOP_WP * s + ASCENT_EM * font;
                for (String line : backLines) {
                    c.drawText(line, left, y, textPaint);
                    y += BACK_LINE_EM * font;
                }
                textPaint.setTextSize(title);
                drawTitle(c, h, s, left, title);
                c.restore();
                return;
            }
            // 앞면: 흰 단색 아이콘을 그림의 실제 높이로 맞춰 가운데에 (넓은 타일도 중간 타일과 같은 크기). 숫자가 있으면 그림 오른쪽에.
            boolean small = size == SMALL;
            RectF g = glyph(icon);
            float u = Math.min((small ? 0.44f : 0.30f) * h / g.height(), (small ? 0.58f : 0.40f) * h / g.width());
            float glyphW = g.width() * u, cy = h / 2f, cx = w / 2f;
            if (count > 0) {
                String n = String.valueOf(count);
                float digitH = (small ? 0.18f : 0.21f) * h;
                countPaint.setColor(ink);
                countPaint.setTextSize(digitH / DIGIT_EM);
                float gap = (small ? 0.11f : 0.074f) * h, numW = countPaint.measureText(n);
                float x0 = (w - (glyphW + gap + numW)) / 2f;
                cx = x0 + glyphW / 2f;
                c.drawText(n, x0 + glyphW + gap, cy + digitH / 2f, countPaint);
            }
            iconPaint.setColor(ink);
            Icons.draw(c, icon, cx - (g.centerX() - 12f) * u, cy - (g.centerY() - 12f) * u, 24f * u, iconPaint, ICON_STROKE);
            if (!small) {
                textPaint.setTextSize(title);
                c.save();
                c.clipRect(0, 0, w, h);
                drawTitle(c, h, s, left, title);
                c.restore();
            }
        }

        /**
         * 타일 위의 글자·아이콘 색. WP8 시작 화면은 강조색 20가지 모두(노랑·연두 포함)에 흰 글자를 썼으므로 그 색이면 늘 흰색,
         * 직접 고른 다른 색이면 그 색에 맞춘 글자 색({@link Metro#onAccent}: 아주 밝은 색은 짙은 글자).
         */
        private static int ink(int accent) {
            for (int a : Metro.ACCENTS) if ((a & 0xFFFFFF) == (accent & 0xFFFFFF)) return 0xFFFFFFFF;
            return Metro.onAccent;
        }

        /**
         * 왼쪽 아래의 제목 (한 줄). 넘치는 글은 말줄임하지 않고 타일 가장자리에서 잘린다 (WP8 시작 화면의 'Internet Explo'처럼).
         * 부른 쪽이 타일 영역으로 잘라 둔다.
         */
        private void drawTitle(Canvas c, int h, float s, float left, float font) {
            if (title.isEmpty()) return;
            float baseline = h - TITLE_BOTTOM_WP * s - (LINE_EM - ASCENT_EM) * font;
            c.drawText(title, left, baseline, textPaint);
        }

        /**
         * text를 폭 max에 맞춰 낱말 단위로 끊는다 (낱말이 너무 길면 글자 단위). Flip 타일의 글은 저절로 줄바꿈되지 않으므로 직접 끊고,
         * 마지막 줄에 남는 글은 그대로 두어 타일 가장자리에서 잘리게 한다.
         */
        private String[] wrap(String text, float max, int maxLines) {
            java.util.List<String> lines = new java.util.ArrayList<>();
            String rest = text.trim();
            while (!rest.isEmpty() && lines.size() < maxLines) {
                if (lines.size() == maxLines - 1 || textPaint.measureText(rest) <= max) {
                    lines.add(rest);
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
