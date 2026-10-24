package com.alternative_studios.newswipe.ui;

import android.graphics.Bitmap;
import android.graphics.BlurMaskFilter;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.view.ViewGroup;

import com.alternative_studios.newswipe.keyboard.KeyboardTheme;

/**
 * Neumorphism 모양: 바탕에서 부드럽게 솟아오른 면(왼쪽 위 밝은 그림자, 오른쪽 아래 어두운 그림자)과
 * 누르면 안으로 들어간 면(테두리 안쪽 그림자). 자판 키, 도구 막대 버튼, 클립보드·이모지 창의 버튼이 같이 쓴다.
 *
 * <p>흐린 그림자는 그리기 비싸다. 솟아오른 그림자는 크기마다 한 번만 절반 해상도 비트맵으로 구워 두고(모든 뷰가 함께 쓴다)
 * 다시 그릴 때는 비트맵 하나만 늘려 붙인다. 화면에 나오는 크기는 몇 가지뿐이라 비트맵 몇 장(한 장에 수십 KB)이면 된다.
 * 흐림을 그때그때 그리는 것은 눌린 것(보통 한두 개)뿐이다. 모두 메인 스레드에서만 부른다.
 */
public final class Neu {
    private Neu() {
    }

    private static final int CACHE = 24;
    private static final int FIELDS = 6;
    private static final float SCALE = 0.5f;
    private static final Bitmap[] bitmaps = new Bitmap[CACHE];
    private static final int[] keys = new int[CACHE * FIELDS];
    private static int next;
    private static final Paint bitmapPaint = new Paint(Paint.FILTER_BITMAP_FLAG);
    private static final Paint insetPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private static final Path path = new Path();
    private static final RectF rect = new RectF();

    static {
        insetPaint.setStyle(Paint.Style.STROKE);
    }

    private static final int SHADERS = 24;   // 키·칩 크기 × 흰색·회색. 모자라면 그릴 때마다 새로 만들게 되므로 넉넉히
    private static final LinearGradient[] shaders = new LinearGradient[SHADERS];
    private static final int[] shaderKeys = new int[SHADERS * 5];
    private static int nextShader;

    /**
     * 솟아오른 면. 밝은 테마의 흰 키 면은 왼쪽 위가 바탕색이고 대각선을 따라 흰색이 되는 그라데이션이라
     * 살짝 오목하게 들어간 느낌이 든다. 면의 왼쪽 위가 흰 밝은 그림자보다 어두워져 그쪽 윤곽도 또렷하다.
     * 회색 기능키 면도 흰 키와 어울리도록 같은 방향으로 약하게 그라데이션을 준다 (회색이 그대로 보이도록 왼쪽 위는 기능키 색보다
     * 3% 어둡게, 오른쪽 아래는 바탕색 쪽으로 20%만 밝게). 회색 면이 그림자로 번져 보이지 않게 하는 것은 진한 그림자가 맡는다
     * ({@link #darkShadow}). 강조색 키의 면은 한 가지 색이다. 모양을 정하는 값은 모드마다 고정이다 ({@link KeyboardTheme#neuTune}).
     */
    public static void drawFace(Canvas c, RectF r, float radius, int face, KeyboardTheme t, Paint fill) {
        if (face != t.key && face != t.functionKey) {
            fill.setColor(face);
            c.drawRoundRect(r, radius, radius, fill);
            return;
        }
        int w = Math.max(1, Math.round(r.width())), h = Math.max(1, Math.round(r.height()));
        fill.setColor(0xFF000000);
        // 흰 면은 왼쪽 위 모서리가 밝을수록 흰 밝은 그림자와 섞여 번져 보인다 (기기에서는 바탕색과 흰색의 중간에서 번졌다).
        // 모서리는 바탕색에서 흰색 쪽으로 30%만 올리고, 대신 대각선의 50% 지점에서 벌써 흰색이 되게 해 면 전체는 밝게 한다.
        if (face == t.key) {
            fill.setShader(concave(w, h, blend(t.background, face, t.tune(KeyboardTheme.T_KEY_START)), face,
                    t.tune(KeyboardTheme.T_KEY_END)));
        } else {
            // 회색 면: 면 색을 밝은 쪽(+, t.lift)이나 검정 쪽(-)으로 옮긴 뒤, 왼쪽 위는 검정 쪽으로, 오른쪽 아래는 밝은 쪽으로 조금 옮긴다.
            // 밝은 쪽을 바탕색으로 두면 다크 모드(바탕색이 면보다 어둡다)에서 '밝게'가 어두워진다.
            float shift = t.tune(KeyboardTheme.T_FN_SHIFT);
            int base = shift >= 0 ? blend(face, t.lift, shift) : blend(face, 0xFF000000, -shift);
            fill.setShader(concave(w, h, blend(base, 0xFF000000, t.tune(KeyboardTheme.T_FN_START)),
                    blend(base, t.lift, t.tune(KeyboardTheme.T_FN_END_TINT)), t.tune(KeyboardTheme.T_FN_END)));
        }
        c.save();
        c.translate(r.left, r.top);
        c.drawRoundRect(0, 0, r.width(), r.height(), radius, radius, fill);
        c.restore();
        fill.setShader(null);
    }

    /**
     * w×h 면의 왼쪽 위(dark)에서 오른쪽 아래(lit)로 가는 그라데이션. 대각선의 end 지점에서 lit이 되고 그 뒤는 lit이다.
     * 크기·색마다 한 번만 만든다.
     */
    private static LinearGradient concave(int w, int h, int dark, int lit, float end) {
        int e = Math.round(end * 100);
        for (int i = 0; i < SHADERS; i++) {
            int k = i * 5;
            if (shaders[i] != null && shaderKeys[k] == w && shaderKeys[k + 1] == h && shaderKeys[k + 2] == dark
                    && shaderKeys[k + 3] == lit && shaderKeys[k + 4] == e) {
                return shaders[i];
            }
        }
        int i = nextShader;
        nextShader = (nextShader + 1) % SHADERS;
        shaders[i] = new LinearGradient(0, 0, w * end, h * end, dark, lit, Shader.TileMode.CLAMP);
        int k = i * 5;
        shaderKeys[k] = w;
        shaderKeys[k + 1] = h;
        shaderKeys[k + 2] = dark;
        shaderKeys[k + 3] = lit;
        shaderKeys[k + 4] = e;
        return shaders[i];
    }

    /**
     * 솟아오른 면의 밝은 그림자 색. 흰색(글자) 키·회색 키·강조색 키가 따로 정한다 (KeyboardTheme.neuShadow).
     * 다크 모드의 강조색 면은 아주 밝아서 같은 값이면 그림자가 면에 눌려 작아 보이므로 두 배쯤 진하다.
     */
    public static int raisedLight(KeyboardTheme t, int face) {
        if (face == t.accent) return t.neuLightAccent;
        return face == t.functionKey ? t.neuLightFunction : t.neuLight;
    }

    /**
     * 눌려 들어간 면의 안쪽 밝은 그림자(오른쪽·아래 가장자리). 라이트 모드의 강조색 면은 채도가 높아 순백색 그림자가
     * 하얀 띠로 보이므로, 다크 모드와 같은 옅은 흰색(13%)을 쓴다. 다른 면은 밝은 그림자 그대로다.
     */
    public static int pressedLight(KeyboardTheme t, int face) {
        return face == t.accent && !t.dark ? 0x21FFFFFF : t.neuLight;
    }

    /**
     * 솟아오른 면의 어두운 그림자 색. 흰색(글자) 키·회색 키·강조색 키가 따로 정한다 (KeyboardTheme.neuShadow).
     * 라이트 모드의 회색 면은 면 색이 보통 그림자와 거의 같아 면이 그림자로 번져 보이므로 흰색 키보다 진하다.
     */
    public static int darkShadow(KeyboardTheme t, int face) {
        if (face == t.accent) return t.neuDarkAccent;
        return face == t.functionKey ? t.neuDarkFunction : t.neuDark;
    }

    /**
     * 솟아오른 면의 그림자 흐림. 회색 면은 진한 그림자를 쓰므로, 흐림까지 같으면 가려지지 않는 아래쪽(맨 아래 줄·도구 막대)에서
     * 그림자가 넓게 고여 오른쪽보다 크고 진해 보인다. 그래서 회색 면의 그림자는 덜 번지게(60%) 해 면에 붙어 보이게 한다.
     */
    public static float shadowBlur(KeyboardTheme t, int face, float blur) {
        return face == t.functionKey && !t.dark ? blur * 0.6f : blur;
    }

    /** 그림자가 면 밖으로 번지는 너비: 이만큼 둘레를 비워 두면 그림자가 잘리지 않는다. */
    public static float spread(float offset, float blur) {
        return offset + blur * 2;
    }

    /**
     * 솟아오른 면의 그림자를 (l, t, r, b) 면 둘레에 그린다. 면은 그리지 않는다 (그림자를 모두 깐 뒤 면을 그려야
     * 이웃의 그림자가 면을 덮지 않는다).
     */
    public static void drawRaised(Canvas c, float l, float t, float r, float b, float radius,
                                  float offset, float blur, int light, int dark) {
        int w = Math.round(r - l), h = Math.round(b - t);
        if (w <= 0 || h <= 0) return;
        Bitmap bm = shadow(w, h, radius, offset, blur, light, dark);
        float pad = spread(offset, blur);
        rect.set(l - pad, t - pad, r + pad, b + pad);
        c.drawBitmap(bm, null, rect, bitmapPaint);
    }

    private static Bitmap shadow(int w, int h, float radius, float offset, float blur, int light, int dark) {
        int rq = Math.round(radius * 4), oq = Math.round(offset * 16) << 16 | Math.round(blur * 16);
        for (int i = 0; i < CACHE; i++) {
            int k = i * FIELDS;
            if (bitmaps[i] != null && keys[k] == w && keys[k + 1] == h && keys[k + 2] == rq && keys[k + 3] == oq
                    && keys[k + 4] == light && keys[k + 5] == dark) {
                return bitmaps[i];
            }
        }
        float s = SCALE, o = offset * s, bl = blur * s, pad = spread(offset, blur) * s, rr = radius * s;
        Bitmap bm = Bitmap.createBitmap((int) Math.ceil(w * s + pad * 2), (int) Math.ceil(h * s + pad * 2),
                Bitmap.Config.ARGB_8888);
        Canvas bc = new Canvas(bm);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        if (bl > 0) p.setMaskFilter(new BlurMaskFilter(bl, BlurMaskFilter.Blur.NORMAL));
        // 빛은 왼쪽 위에서: 왼쪽 위로 밝은 그림자, 오른쪽 아래로 어두운 그림자.
        p.setColor(light);
        bc.drawRoundRect(pad - o, pad - o, pad - o + w * s, pad - o + h * s, rr, rr, p);
        p.setColor(dark);
        bc.drawRoundRect(pad + o, pad + o, pad + o + w * s, pad + o + h * s, rr, rr, p);
        int i = next;
        next = (next + 1) % CACHE;
        int k = i * FIELDS;
        bitmaps[i] = bm;
        keys[k] = w;
        keys[k + 1] = h;
        keys[k + 2] = rq;
        keys[k + 3] = oq;
        keys[k + 4] = light;
        keys[k + 5] = dark;
        return bm;
    }

    /** 눌린 면: face로 칠하고 테두리 안쪽에 그림자를 넣어 오목하게 들어간 모양을 만든다. */
    public static void drawInset(Canvas c, RectF r, float radius, float offset, float blur, int face, int light, int dark,
                                 Paint fill) {
        fill.setColor(face);
        c.drawRoundRect(r, radius, radius, fill);
        if (insetPaint.getStrokeWidth() != blur * 2) {   // 흐림 필터는 크기가 바뀔 때만 새로 만든다
            insetPaint.setMaskFilter(blur > 0 ? new BlurMaskFilter(blur, BlurMaskFilter.Blur.NORMAL) : null);
            insetPaint.setStrokeWidth(blur * 2);
        }
        path.reset();
        path.addRoundRect(r, radius, radius, Path.Direction.CW);
        c.save();
        c.clipPath(path);
        // 테두리 바깥을 감싸는 굵은 선을 비켜 그리면, 선의 흐린 안쪽 가장자리만 면 안으로 들어와 안쪽 그림자가 된다.
        insetPaint.setColor(dark);
        rect.set(r);
        rect.offset(offset, offset);
        rect.inset(-blur, -blur);
        c.drawRoundRect(rect, radius + blur, radius + blur, insetPaint);
        insetPaint.setColor(light);
        rect.offset(-offset * 2, -offset * 2);
        c.drawRoundRect(rect, radius + blur, radius + blur, insetPaint);
        c.restore();
    }

    /** Neumorphism 모드면 v의 바탕을 fill 색 면으로 바꾼다 (꺼져 있으면 그대로 둔다). */
    public static void apply(View v, KeyboardTheme t, int fill, float radius) {
        apply(v, t, fill, radius, 0, 0);
    }

    /**
     * insetH·insetV: 면을 뷰 가장자리에서 들여 그리는 거리. 면은 원래 바탕과 같은 크기이고 그림자만 뷰 밖으로 번지므로,
     * 그림자가 잘리지 않게 붙은 뒤 부모 두 단계의 자식 자르기를 끈다.
     */
    public static void apply(View v, KeyboardTheme t, int fill, float radius, int insetH, int insetV) {
        if (!t.neu) return;
        v.setBackground(new Chip(t, fill, radius, insetH, insetV));
        unclipParents(v);
    }

    /** v의 그림자가 뷰 밖으로 번져도 잘리지 않도록 바로 위 부모(버튼이 놓인 줄)의 자식 자르기를 끈다 (붙어 있지 않으면 붙을 때). */
    public static void unclipParents(View v) {
        if (v.getParent() != null) {
            unclip(v);
            return;
        }
        v.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
            @Override
            public void onViewAttachedToWindow(View view) {
                view.removeOnAttachStateChangeListener(this);
                unclip(view);
            }

            @Override
            public void onViewDetachedFromWindow(View view) {
            }
        });
    }

    /**
     * 바로 위 부모만 끈다. 그림자를 쓰는 버튼은 모두 제 줄 안에 그림자가 들어갈 여유가 있다. 그 위(클립보드·이모지 창 전체)까지
     * 끄면 창이 스크롤 목록·넘기는 격자를 제 영역으로 잘라 주지 않아, 스크롤한 항목이 머리 줄 위로 그려진다.
     */
    private static void unclip(View v) {
        android.view.ViewParent p = v.getParent();
        if (!(p instanceof ViewGroup)) return;
        ViewGroup g = (ViewGroup) p;
        // 스크롤하는 틀이나 그것을 담은 줄은 자르기를 끄면 스크롤한 내용이 틀 밖까지 그려지므로 건드리지 않는다.
        if (scrolls(g)) return;
        for (int i = 0; i < g.getChildCount(); i++) if (scrolls(g.getChildAt(i))) return;
        g.setClipChildren(false);
    }

    private static boolean scrolls(View v) {
        return v instanceof android.widget.ScrollView || v instanceof android.widget.HorizontalScrollView
                || v instanceof android.widget.AbsListView;
    }

    /** 늘 안으로 들어가 있는 바탕 (고른 탭처럼 '켜진' 것을 나타낸다). */
    public static Drawable sunken(View v, KeyboardTheme t, int fill, float radius) {
        unclipParents(v);
        Chip c = new Chip(t, fill, radius, 0, 0);
        c.sunken = true;
        return c;
    }

    /** 버튼·칩 바탕: 평소에는 솟아오르고, 누르고 있는 동안 안으로 들어간다. */
    public static final class Chip extends Drawable {
        private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF face = new RectF();
        private final KeyboardTheme theme;
        private final int color, pressedColor, light, dark;
        private final float radius, offset, blur;
        private final int insetH, insetV;
        private boolean pressed, sunken;

        Chip(KeyboardTheme t, int color, float radius, int insetH, int insetV) {
            boolean accent = color == t.accent;
            theme = t;
            this.color = color;
            pressedColor = accent ? blend(t.accent, t.text, 0.12f) : t.keyPressed;
            light = pressedLight(t, color);
            dark = darkShadow(t, color);
            this.radius = radius;
            this.insetH = insetH;
            this.insetV = insetV;
            float density = android.content.res.Resources.getSystem().getDisplayMetrics().density;
            offset = 1.5f * density;
            blur = 2f * density;
        }

        @Override
        public void draw(Canvas c) {
            Rect b = getBounds();
            face.set(b.left + insetH, b.top + insetV, b.right - insetH, b.bottom - insetV);
            if (face.width() <= 0 || face.height() <= 0) return;
            float r = Math.min(radius, Math.min(face.width(), face.height()) / 2);
            if (pressed || sunken) {
                drawInset(c, face, r, offset, blur, sunken ? color : pressedColor, light, theme.neuDark, fill);
            } else {
                drawRaised(c, face.left, face.top, face.right, face.bottom, r, offset, shadowBlur(theme, color, blur),
                        raisedLight(theme, color), dark);
                drawFace(c, face, r, color, theme, fill);
            }
        }

        @Override
        public boolean isStateful() {
            return true;
        }

        @Override
        protected boolean onStateChange(int[] state) {
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

    public static int blend(int a, int b, float t) {
        return blendRaw(a, b, t);
    }

    private static int blendRaw(int a, int b, float t) {
        int ar = (a >> 16) & 0xFF, ag = (a >> 8) & 0xFF, ab = a & 0xFF;
        int br = (b >> 16) & 0xFF, bg = (b >> 8) & 0xFF, bb = b & 0xFF;
        return 0xFF000000 | ((int) (ar + (br - ar) * t) << 16) | ((int) (ag + (bg - ag) * t) << 8)
                | (int) (ab + (bb - ab) * t);
    }
}
