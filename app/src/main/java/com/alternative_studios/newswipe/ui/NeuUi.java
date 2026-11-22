package com.alternative_studios.newswipe.ui;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BlurMaskFilter;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.view.View;

/**
 * Neumorphism 모드의 앱 화면(설정·편집 화면·대화상자) 모양. 키보드는 {@link Neu}가 그린다.
 *
 * <p>Neumorphism은 바탕과 같은 색의 면이 바탕에서 부드럽게 솟아오르거나(왼쪽 위 밝은 그림자, 오른쪽 아래 어두운 그림자)
 * 안으로 들어간(테두리 안쪽 그림자) 모양으로 위계를 나타낸다. 카드·버튼은 솟아오르고, 누르면 들어가며, 입력란·스위치·슬라이더의
 * 홈과 고른 칸은 들어가 있다.
 *
 * <p>가볍게 그린다: 솟아오른 그림자는 모서리 반지름(과 그림자 크기)마다 작은 조각 하나만 한 번 구워 두고, 아홉 조각으로 나눠
 * 면 크기에 맞게 늘려 붙인다 (카드가 크거나 많아도 비트맵은 몇 장뿐이다). 안으로 들어간 그림자는 흐린 굵은 선 하나로 그린다.
 * 모두 메인 스레드에서만 부른다.
 */
public final class NeuUi {
    private NeuUi() {
    }

    /** Neumorphism 모드인지 (Metro 모드가 아니고 Neumorphism을 골랐을 때). AppTheme.of가 맞춘다. */
    public static volatile boolean on;
    public static boolean dark;
    /** 바탕(=면) 색, 밝은 그림자, 어두운 그림자. */
    public static int surface = 0xFFE6E8EC, light = 0xFFFFFFFF, shade = 0x73A0A8B6;

    /** 라이트: 키보드의 Neumorphism 바탕과 같은 옅은 회색. 다크: 짙은 회색. */
    public static final int LIGHT_SURFACE = 0xFFE6E8EC, DARK_SURFACE = 0xFF202125;

    public static void set(boolean enabled, boolean isDark) {
        on = enabled;
        dark = isDark;
        surface = isDark ? DARK_SURFACE : LIGHT_SURFACE;
        // 키보드와 같은 그림자 (KeyboardTheme의 흰색 키 값): 라이트는 흰색 100%·푸른 회색 45%, 다크는 흰색 13%·검정 70%.
        light = isDark ? 0x21FFFFFF : 0xFFFFFFFF;
        shade = isDark ? 0xB3000000 : 0x73A0A8B6;
    }

    /**
     * 안으로 들어간 홈의 면 색: 바탕보다 조금 어둡다. 다크 모드는 밝은 그림자가 약해 홈이 바탕에 묻히므로 더 어둡게 한다.
     */
    public static int well() {
        return Neu.blend(surface, 0xFF000000, dark ? 0.3f : 0.035f);
    }

    /** 스위치·슬라이더 손잡이의 면 색: 바탕보다 조금 밝아 홈(특히 강조색으로 찬 홈) 위에서 또렷하다. */
    public static int knob() {
        return dark ? Neu.blend(surface, 0xFFFFFFFF, 0.22f) : 0xFFF8F9FB;
    }

    /** 그림자 크기: 작은 것(버튼·스위치)과 큰 것(카드). */
    public static final int SMALL = 0, LARGE = 1;

    private static float offset(Context c, int size) {
        return Ui.dp(c, size == LARGE ? 4 : 2.5f);
    }

    private static float blur(Context c, int size) {
        return Ui.dp(c, size == LARGE ? 6 : 3);
    }

    /** 그림자가 면 밖으로 번지는 너비 (이만큼 둘레가 비어 있어야 잘리지 않는다). */
    public static float spread(Context c, int size) {
        return offset(c, size) + blur(c, size) * 2;
    }

    // ---------------------------------------------------------------- 솟아오른 그림자 (아홉 조각)

    private static final java.util.HashMap<Long, Bitmap> SHADOWS = new java.util.HashMap<>();
    private static final Paint bitmapPaint = new Paint(Paint.FILTER_BITMAP_FLAG);
    private static final Rect src = new Rect();
    private static final RectF dst = new RectF();

    /**
     * r(px) 모서리의 솟아오른 그림자 조각: 한 변이 (r + 번짐) × 2 + 1px인 정사각형 가운데에 모서리가 r인 면을 두고, 왼쪽 위로 밝은
     * 그림자, 오른쪽 아래로 어두운 그림자를 흐리게 그린다. 가운데 한 줄은 늘려 붙일 곳이다.
     */
    private static Bitmap shadow(int r, int o, int b, int lightColor, int darkColor) {
        long key = ((long) r << 40) ^ ((long) o << 32) ^ ((long) b << 24) ^ ((long) lightColor * 31 + darkColor);
        Bitmap bm = SHADOWS.get(key);
        if (bm != null) return bm;
        int pad = o + b * 2;
        int side = (r + pad) * 2 + 1;
        bm = Bitmap.createBitmap(side, side, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bm);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        if (b > 0) p.setMaskFilter(new BlurMaskFilter(b, BlurMaskFilter.Blur.NORMAL));
        float l = pad, t = pad, rr = side - pad, bb = side - pad;
        p.setColor(lightColor);
        c.drawRoundRect(l - o, t - o, rr - o, bb - o, r, r, p);
        p.setColor(darkColor);
        c.drawRoundRect(l + o, t + o, rr + o, bb + o, r, r, p);
        if (SHADOWS.size() > 32) SHADOWS.clear();
        SHADOWS.put(key, bm);
        return bm;
    }

    /** 면 f(모서리 radius)의 솟아오른 그림자를 그린다. 면은 그리지 않는다 (그림자 위에 따로 칠한다). */
    public static void drawRaised(Context ctx, Canvas c, RectF f, float radius, int size) {
        float rMax = Math.min(f.width(), f.height()) / 2f;
        int r = Math.max(0, Math.round(Math.min(radius, rMax)));
        int o = Math.round(offset(ctx, size)), b = Math.round(blur(ctx, size));
        Bitmap bm = shadow(r, o, b, light, shade);
        int pad = o + b * 2, cs = r + pad, side = bm.getWidth();
        float[] dx = {f.left - pad, f.left - pad + cs, f.right + pad - cs, f.right + pad};
        float[] dy = {f.top - pad, f.top - pad + cs, f.bottom + pad - cs, f.bottom + pad};
        int[] sx = {0, cs, cs + 1, side};
        for (int i = 0; i < 3; i++) {
            if (dx[i + 1] <= dx[i]) continue;
            for (int j = 0; j < 3; j++) {
                if (dy[j + 1] <= dy[j]) continue;
                src.set(sx[i], sx[j], sx[i + 1], sx[j + 1]);
                dst.set(dx[i], dy[j], dx[i + 1], dy[j + 1]);
                c.drawBitmap(bm, src, dst, bitmapPaint);
            }
        }
    }

    // ---------------------------------------------------------------- 안으로 들어간 면

    private static final Paint insetPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private static final Path path = new Path();
    private static final RectF tmp = new RectF();
    private static float insetBlur = -1;

    static {
        insetPaint.setStyle(Paint.Style.STROKE);
    }

    /** 안으로 들어간 면: face로 칠하고 테두리 안쪽 왼쪽 위에 어두운 그림자, 오른쪽 아래에 밝은 그림자를 넣는다. */
    public static void drawInset(Context ctx, Canvas c, RectF f, float radius, int face, Paint fill) {
        float r = Math.min(radius, Math.min(f.width(), f.height()) / 2f);
        float o = Ui.dp(ctx, 2), b = Ui.dp(ctx, 3);
        fill.setStyle(Paint.Style.FILL);
        fill.setColor(face == surface ? well() : face);   // 바탕색 홈은 조금 어둡게 (바탕에 묻히지 않게)
        c.drawRoundRect(f, r, r, fill);
        if (insetBlur != b) {
            insetPaint.setMaskFilter(new BlurMaskFilter(b, BlurMaskFilter.Blur.NORMAL));
            insetPaint.setStrokeWidth(b * 2);
            insetBlur = b;
        }
        path.reset();
        path.addRoundRect(f, r, r, Path.Direction.CW);
        c.save();
        c.clipPath(path);
        insetPaint.setColor(shade);
        tmp.set(f);
        tmp.offset(o, o);
        tmp.inset(-b, -b);
        c.drawRoundRect(tmp, r + b, r + b, insetPaint);
        insetPaint.setColor(light);
        tmp.offset(-o * 2, -o * 2);
        c.drawRoundRect(tmp, r + b, r + b, insetPaint);
        c.restore();
    }

    // ---------------------------------------------------------------- 바탕 (Drawable)

    /** 솟아오른 카드 바탕 (누르지 않는 면). 그림자가 뷰 밖으로 번지므로 그림자가 잘리지 않게 부모들의 자르기를 끈다. */
    public static void card(View v, float radiusPx) {
        v.setBackground(new Face(v.getContext(), surface, radiusPx, LARGE, false, false));
        unclip(v, LARGE);
    }

    /** 누르는 버튼 바탕: 평소에는 솟아오르고 누르는 동안 들어간다. fill은 면 색 (보통 바탕색, 강조 버튼은 강조색). */
    public static void button(View v, int fill, float radiusPx) {
        v.setBackground(new Face(v.getContext(), fill, radiusPx, SMALL, true, false));
        unclip(v, SMALL);
    }

    // ---------------------------------------------------------------- 그림자가 잘리지 않게

    /**
     * v의 그림자(뷰 밖으로 size만큼 번진다)가 잘리지 않게 한다. Android는 부모가 자식을 그 자식의 영역까지만 그리게 자르므로
     * (clipChildren), 바로 위 부모만 끄면 그 부모가 다시 자기 부모에게 잘려 그림자가 카드·줄 경계에서 잘린다.
     * 부모의 안쪽 여백 선에서 자르는 clipToPadding도 따로 있다. 그래서 위로 올라가며 부모의 두 자르기를 모두 끄되, 그림자가 그 부모 안에 다 들어가면 멈춘다 (그보다 위는 자를 필요가 없다).
     * 스크롤 틀은 끄지 않는다 (스크롤한 내용이 틀 밖으로 그려지면 안 된다). 배치가 바뀔 때마다 다시 본다.
     */
    public static void unclip(View v, int size) {
        if (!on) return;
        float pad = spread(v.getContext(), size);
        View.OnLayoutChangeListener l = (view, a, b, c, d, e, f, g, h) -> unclipChain(view, pad);
        v.addOnLayoutChangeListener(l);
        if (v.isLaidOut()) unclipChain(v, pad);
    }

    private static void unclipChain(View v, float pad) {
        float left = v.getLeft() + v.getTranslationX() - pad, top = v.getTop() + v.getTranslationY() - pad;
        float right = v.getRight() + v.getTranslationX() + pad, bottom = v.getBottom() + v.getTranslationY() + pad;
        View node = v;
        for (int depth = 0; depth < 12; depth++) {
            android.view.ViewParent p = node.getParent();
            if (!(p instanceof android.view.ViewGroup)) return;
            android.view.ViewGroup g = (android.view.ViewGroup) p;
            if (scrolls(g)) return;   // 스크롤 틀은 그대로 자른다 (그 안의 목록까지는 이미 껐다)
            if (g.getClipChildren()) g.setClipChildren(false);
            // 여백 자르기(clipToPadding)도 끈다: 자식 자르기와 따로 동작해, 켜져 있으면 부모의 안쪽 여백 선에서 그림자가 잘린다
            // (설정 목록은 좌우 여백만 있어 카드의 좌우가, 카드는 여백이 있어 그 안의 버튼 좌우가 잘렸다).
            if (g.getClipToPadding()) g.setClipToPadding(false);
            // 그림자가 이 부모 안에 다 들어가면 여기서 멈춘다.
            if (left >= 0 && top >= 0 && right <= g.getWidth() && bottom <= g.getHeight()) return;
            float dx = g.getLeft() + g.getTranslationX() - g.getScrollX(), dy = g.getTop() + g.getTranslationY() - g.getScrollY();
            left += dx;
            right += dx;
            top += dy;
            bottom += dy;
            node = g;
        }
    }

    private static boolean scrolls(View v) {
        return v instanceof android.widget.ScrollView || v instanceof android.widget.HorizontalScrollView
                || v instanceof android.widget.AbsListView;
    }

    /** 면을 그라데이션(왼쪽 → 오른쪽)으로 칠하는 누르는 버튼 바탕 (명예의 전당 버튼의 무지개 등). */
    public static void button(View v, int[] gradient, float radiusPx) {
        Face f = new Face(v.getContext(), gradient[0], radiusPx, SMALL, true, false);
        f.gradient = gradient;
        v.setBackground(f);
        unclip(v, SMALL);
    }

    /** 늘 안으로 들어가 있는 바탕 (입력란, 아이콘 홈). */
    public static void sunken(View v, float radiusPx) {
        v.setBackground(new Face(v.getContext(), surface, radiusPx, SMALL, false, true));
    }

    /** 솟아오르거나 들어간 면을 그리는 바탕. */
    public static final class Face extends Drawable {
        private final Context ctx;
        private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF face = new RectF();
        private final int color, size;
        private final float radius;
        private final boolean pressable, sunken;
        private boolean pressed;
        /** 면을 칠할 그라데이션 색들 (없으면 한 가지 색). */
        int[] gradient;
        private android.graphics.LinearGradient shader;
        private float shaderW = -1;

        private void paintFace(Canvas c, float r) {
            if (shaderW != face.width()) {
                shader = new android.graphics.LinearGradient(face.left, 0, face.right, 0, gradient, null,
                        android.graphics.Shader.TileMode.CLAMP);
                shaderW = face.width();
            }
            fill.setColor(0xFF000000);
            fill.setShader(shader);
            c.drawRoundRect(face, r, r, fill);
            fill.setShader(null);
        }

        Face(Context ctx, int color, float radius, int size, boolean pressable, boolean sunken) {
            this.ctx = ctx;
            this.color = color;
            this.radius = radius;
            this.size = size;
            this.pressable = pressable;
            this.sunken = sunken;
        }

        @Override
        public void draw(Canvas c) {
            Rect b = getBounds();
            face.set(b);
            if (face.width() <= 0 || face.height() <= 0) return;
            float r = Math.min(radius, Math.min(face.width(), face.height()) / 2f);
            if (gradient != null) {
                // 그라데이션 면: 솟아오르거나, 누르는 동안은 면 위에 안쪽 그림자만 얹는다 (투명한 면으로).
                if (!pressed) drawRaised(ctx, c, face, r, size);
                paintFace(c, r);
                if (pressed) drawInset(ctx, c, face, r, 0x00000000, fill);
            } else if (sunken || pressed) {
                drawInset(ctx, c, face, r, pressed && color != surface ? Neu.blend(color, 0xFF000000, 0.08f) : color, fill);
            } else {
                drawRaised(ctx, c, face, r, size);
                fill.setColor(color);
                c.drawRoundRect(face, r, r, fill);
            }
        }

        @Override
        public void getOutline(Outline outline) {
            Rect b = getBounds();
            outline.setRoundRect(b, Math.min(radius, Math.min(b.width(), b.height()) / 2f));
            outline.setAlpha(0f);   // 그림자(elevation)는 쓰지 않는다
        }

        @Override
        public boolean isStateful() {
            return pressable;
        }

        @Override
        protected boolean onStateChange(int[] state) {
            if (!pressable) return false;
            boolean p = false;
            for (int s : state) if (s == android.R.attr.state_pressed) p = true;
            if (p == pressed) return false;
            pressed = p;
            invalidateSelf();
            return true;
        }

        @Override
        public void setAlpha(int alpha) {
        }

        @Override
        public void setColorFilter(ColorFilter cf) {
        }

        @Override
        public int getOpacity() {
            return PixelFormat.TRANSLUCENT;
        }
    }
}
