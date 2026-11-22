package com.alternative_studios.newswipe.ui;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.content.Context;
import android.graphics.Typeface;
import android.util.TypedValue;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.Interpolator;
import android.view.animation.LinearInterpolator;
import android.widget.FrameLayout;
import android.widget.TextView;

import com.alternative_studios.newswipe.R;

/**
 * Metro UI 모드 (실험적): Windows Phone 7·8의 디자인 언어를 흉내 낸다.
 *
 * <ul>
 *   <li>색: 순수한 검정(다크)·흰색(라이트) 바탕, 강조색 타일, 크롬(#1F1F1F·#DDDDDD), 옅은 글자(#999999).</li>
 *   <li>모양: 모서리가 없는 네모, 그림자·그라데이션 없음 ({@link Ui#round}·{@link Ui#ripple}이 이 모드에서 네모·단색 눌림이 된다).</li>
 *   <li>글자: 크고 가는 글자(Segoe WP 대신 sans-serif-light), 작은 대문자 앱 이름 + 큰 소문자 제목 (피벗 머리글).</li>
 *   <li>움직임: 턴스타일 화면 전환, 깃털처럼 줄줄이 들어오는 목록, 누른 자리로 기울어지는 틸트.</li>
 * </ul>
 *
 * <p>움직임의 값은 Microsoft가 공개한 Windows Phone Toolkit(Ms-PL) 소스의 값을 그대로 옮겼다:
 * Transitions/Storyboards/Turnstile*.xaml (들어올 때 -80°→0° 350ms ExponentialEase EaseOut 지수 6, 나갈 때 0°→50° 250ms
 * EaseIn 지수 6, 돌아갈 때 0°→-80°, 다시 들어올 때 50°→0°, 회전축은 화면 왼쪽 가장자리), Effects/TurnstileFeatherEffect.cs
 * (깃털: 회전축이 화면 폭의 20% 바깥 왼쪽, 항목마다 40ms씩 늦게), Effects/TiltEffect.cs (최대 0.3 rad, 가운데를 누르면 25px
 * 들어감, 떼고 200ms 뒤 100ms 동안 로그 곡선으로 돌아옴), ToggleSwitch 템플릿 (Generic.xaml).
 *
 * <p>{@link #on}과 색은 {@code AppTheme.of}가 부를 때마다 설정값에 맞춰진다 (설정 화면과 키보드가 같은 프로세스다). 메인 스레드에서만 쓴다.
 */
public final class Metro {
    private Metro() {
    }

    /** Metro UI 모드가 켜져 있는지. */
    public static volatile boolean on;
    /** 지금 화면 모드의 Metro 색: 글자(전경), 바탕, 크롬(앱 바·입력란), 옅은 글자, 강조색과 그 위의 글자. */
    public static int fg = 0xFFFFFFFF, bg = 0xFF000000, chrome = 0xFF1F1F1F, subtle = 0xFF999999, accent = 0xFF1BA1E2,
            onAccent = 0xFFFFFFFF;
    public static boolean dark = true;

    /** PhoneBackgroundColor·PhoneForegroundColor·PhoneChromeColor (다크). */
    public static final int DARK_BG = 0xFF000000, DARK_FG = 0xFFFFFFFF, DARK_CHROME = 0xFF1F1F1F;
    /** 라이트: 바탕 흰색, 글자는 검정 87%(#DE000000을 흰 바탕에 얹은 색), 크롬 #DDDDDD. */
    public static final int LIGHT_BG = 0xFFFFFFFF, LIGHT_FG = 0xFF212121, LIGHT_CHROME = 0xFFDDDDDD;
    /** PhoneSubtleColor: 다크의 흰색 60%, 라이트의 검정 40%가 각 바탕 위에서 모두 #999999가 된다. */
    public static final int SUBTLE = 0xFF999999;

    /**
     * Windows Phone 8의 강조색 20가지 (테마 설정 화면의 순서). lime, green, emerald, teal, cyan, cobalt, indigo, violet,
     * pink, magenta, crimson, red, orange, amber, yellow, brown, olive, steel, mauve, taupe.
     */
    public static final int[] ACCENTS = {
            0xFFA4C400, 0xFF60A917, 0xFF008A00, 0xFF00ABA9, 0xFF1BA1E2,
            0xFF0050EF, 0xFF6A00FF, 0xFFAA00FF, 0xFFF472D0, 0xFFD80073,
            0xFFA20025, 0xFFE51400, 0xFFFA6800, 0xFFF0A30A, 0xFFE3C800,
            0xFF825A2C, 0xFF6D8764, 0xFF647687, 0xFF76608A, 0xFF87794E,
    };

    /** 설정값에 맞춰 모드와 색을 바꾼다 ({@code AppTheme.of}가 부른다). */
    public static void set(boolean enabled, boolean isDark, int accentColor, int onAccentColor) {
        on = enabled;
        dark = isDark;
        fg = isDark ? DARK_FG : LIGHT_FG;
        bg = isDark ? DARK_BG : LIGHT_BG;
        chrome = isDark ? DARK_CHROME : LIGHT_CHROME;
        subtle = SUBTLE;
        accent = accentColor;
        onAccent = onAccentColor;
    }

    // ---------------------------------------------------------------- 곡선

    /**
     * Silverlight의 ExponentialEase: EaseIn은 (e^(kt) - 1) / (e^k - 1), EaseOut은 그것을 뒤집은 것.
     * Windows Phone의 화면 전환은 거의 모두 지수 6을 쓴다 (천천히 시작해 빠르게 사라지거나, 빠르게 들어와 부드럽게 멈춘다).
     */
    public static final class Exp implements Interpolator {
        private final double k;
        private final boolean out;

        public Exp(double exponent, boolean easeOut) {
            k = exponent;
            out = easeOut;
        }

        private double in(double t) {
            return (Math.exp(k * t) - 1) / (Math.exp(k) - 1);
        }

        @Override
        public float getInterpolation(float t) {
            if (t <= 0f) return 0f;
            if (t >= 1f) return 1f;
            return (float) (out ? 1 - in(1 - t) : in(t));
        }
    }

    public static final Interpolator EASE_OUT = new Exp(6, true);
    public static final Interpolator EASE_IN = new Exp(6, false);
    /** 틸트가 돌아올 때의 곡선: ln(t + 1) / ln 2 (TiltEffect.LogarithmicEase). */
    public static final Interpolator LOG = t -> (float) (Math.log(t + 1) / Math.log(2));

    // ---------------------------------------------------------------- 글자

    private static Typeface light, semibold, regular;

    /** Segoe WP Light 대신 쓰는 가는 글꼴 (큰 제목·항목 이름). */
    public static Typeface light() {
        if (light == null) light = Typeface.create("sans-serif-light", Typeface.NORMAL);
        return light;
    }

    /** Segoe WP Semibold 대신 (작은 대문자 앱 이름). */
    public static Typeface semibold() {
        if (semibold == null) semibold = Typeface.create("sans-serif-medium", Typeface.NORMAL);
        return semibold;
    }

    /** Segoe WP 대신 (설명 글·키 글자). Metro에는 굵은 글자가 거의 없다. */
    public static Typeface regular() {
        if (regular == null) regular = Typeface.create("sans-serif", Typeface.NORMAL);
        return regular;
    }

    /**
     * 글자를 Metro 크기·글꼴로 바꾸되, 설정 검색이 항목 이름·소제목을 글자 크기로 가려내므로 원래 크기(logicalSp)를 남겨 둔다
     * ({@link #searchPx}).
     */
    public static void text(TextView t, float sp, Typeface face, float logicalSp) {
        t.setTag(R.id.metro_search_px, TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, logicalSp,
                t.getResources().getDisplayMetrics()));
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTypeface(face);
    }

    /** 설정 검색이 보는 글자 크기 (px): {@link #text}로 바꾼 글자는 원래 크기, 아니면 지금 크기. */
    public static float searchPx(TextView t) {
        Object o = t.getTag(R.id.metro_search_px);
        return o instanceof Float ? (Float) o : t.getTextSize();
    }

    /** Windows Phone의 제목·소제목은 영어일 때 소문자다 (한국어는 그대로). */
    public static String lower(String s) {
        return s == null ? null : s.toLowerCase(java.util.Locale.ROOT);
    }

    public static String upper(String s) {
        return s == null ? null : s.toUpperCase(java.util.Locale.ROOT);
    }

    /** 둥근 네모를 그리되 Metro 모드에서는 모서리 없는 네모로 그린다 (캔버스에 직접 그리는 미리보기 표시 등). */
    public static void roundRect(android.graphics.Canvas c, android.graphics.RectF r, float rx, float ry,
                                 android.graphics.Paint p) {
        if (on) c.drawRect(r, p);
        else c.drawRoundRect(r, rx, ry, p);
    }

    // ---------------------------------------------------------------- 3D

    /**
     * 원근: Silverlight PlaneProjection처럼 카메라를 화면 폭의 2.2배 거리에 둔다 (턴스타일에서 -80°로 앞에 나온 가장자리도
     * 지나치게 커지지 않는다). View.setCameraDistance(d)는 d를 densityDpi로 나눈 '인치'로 카메라를 두고, 그 인치는 72단위라서
     * 화면 px 거리 D를 주려면 D × densityDpi / 72를 넘겨야 한다.
     */
    public static void perspective(View v) {
        camera(v);
    }

    private static void camera(View v) {
        android.util.DisplayMetrics m = v.getResources().getDisplayMetrics();
        float px = Math.max(m.widthPixels, 1) * 2.2f;
        v.setCameraDistance(px * m.densityDpi / 72f);
    }

    // ---------------------------------------------------------------- 틸트

    /** 틸트를 거는 리스너 (누르기를 가로채지 않는다: 늘 false를 돌려 뷰가 평소대로 누르기를 처리한다). */
    private static final View.OnTouchListener TILT = (v, e) -> {
        onTiltTouch(v, e);
        return false;
    };
    private static final java.util.WeakHashMap<View, ValueAnimator> TILT_RETURN = new java.util.WeakHashMap<>();
    private static final java.util.WeakHashMap<View, Boolean> TILTED = new java.util.WeakHashMap<>();

    /** TiltEffect.MaxAngle (rad)·MaxDepression (480px 폭 화면의 px), 되돌아오기 전 기다림·되돌아오는 시간. */
    private static final double MAX_ANGLE = 0.3, MAX_DEPRESSION = 25;
    private static final long RETURN_DELAY = 200, RETURN_MS = 100;

    /**
     * 누르면 손가락 쪽으로 기울어지며 살짝 들어가는 Windows Phone의 틸트 효과를 v에 건다 (Metro 모드가 아니면 아무것도 하지 않는다).
     * v에 다른 OnTouchListener가 있으면 덮어쓰므로 그런 뷰에는 쓰지 않는다.
     */
    public static void tilt(View v) {
        if (on) v.setOnTouchListener(TILT);
    }

    /** 커스텀 뷰가 자기 onTouchEvent에서 직접 부른다 (OnTouchListener를 쓸 수 없을 때). */
    public static void onTiltTouch(View v, MotionEvent e) {
        if (!on) return;
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                if (!v.isEnabled() || !v.isClickable()) return;
                applyTilt(v, e.getX(), e.getY());
                break;
            case MotionEvent.ACTION_MOVE:
                if (TILTED.get(v) == null) return;
                float slop = android.view.ViewConfiguration.get(v.getContext()).getScaledTouchSlop();
                if (e.getX() < -slop || e.getY() < -slop || e.getX() > v.getWidth() + slop || e.getY() > v.getHeight() + slop) {
                    releaseTilt(v, false);   // 손가락이 벗어났다 (스크롤 등)
                } else {
                    applyTilt(v, e.getX(), e.getY());
                }
                break;
            case MotionEvent.ACTION_UP:
                releaseTilt(v, true);
                break;
            case MotionEvent.ACTION_CANCEL:
                releaseTilt(v, false);
                break;
            default:
                break;
        }
    }

    /**
     * TiltEffect.ApplyTiltEffect를 옮겼다. 누른 자리가 가운데에서 먼 만큼 기울고(가로·세로 몫을 나눠), 가운데에 가까울수록 깊이 들어간다.
     * Android의 rotationY는 양수일 때 오른쪽 가장자리가, rotationX는 양수일 때 위쪽 가장자리가 화면 안쪽으로 들어간다.
     */
    private static void applyTilt(View v, float x, float y) {
        ValueAnimator back = TILT_RETURN.remove(v);
        if (back != null) back.cancel();
        float w = v.getWidth(), h = v.getHeight();
        if (w <= 0 || h <= 0) return;
        double nx = Math.max(0, Math.min(1, x / w)), ny = Math.max(0, Math.min(1, y / h));
        double xm = Math.abs(nx - 0.5), ym = Math.abs(ny - 0.5);
        double mag = xm + ym;
        double xShare = mag > 0 ? xm / mag : 0;
        double angle = mag * MAX_ANGLE * 180 / Math.PI;
        // 들어가는 깊이: PlaneProjection의 원근(약 1000px 거리)에서 25px 들어가면 약 2.5% 작아 보인다.
        double scale = 1 - (1 - mag) * MAX_DEPRESSION / 1000.0;
        if (TILTED.get(v) == null) {
            TILTED.put(v, Boolean.TRUE);
            camera(v);
            v.setPivotX(w / 2f);
            v.setPivotY(h / 2f);
        }
        v.setRotationY((float) (angle * xShare * Math.signum(nx - 0.5)));
        v.setRotationX((float) (angle * (1 - xShare) * Math.signum(0.5 - ny)));
        v.setScaleX((float) scale);
        v.setScaleY((float) scale);
    }

    private static void releaseTilt(View v, boolean afterTap) {
        if (TILTED.remove(v) == null) return;
        final float rx = v.getRotationX(), ry = v.getRotationY(), s = v.getScaleX();
        ValueAnimator a = ValueAnimator.ofFloat(0f, 1f);
        a.setStartDelay(afterTap ? RETURN_DELAY : 0);   // 짧게 톡 쳐도 기울어진 모습이 보이게
        a.setDuration(RETURN_MS);
        a.setInterpolator(LOG);
        a.addUpdateListener(an -> {
            float f = (float) an.getAnimatedValue();
            v.setRotationX(rx * (1 - f));
            v.setRotationY(ry * (1 - f));
            float sc = s + (1 - s) * f;
            v.setScaleX(sc);
            v.setScaleY(sc);
        });
        a.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                if (TILT_RETURN.get(v) == a) TILT_RETURN.remove(v);
            }
        });
        TILT_RETURN.put(v, a);
        a.start();
    }

    // ---------------------------------------------------------------- 턴스타일

    /** 턴스타일 값 (Turnstile*.xaml, TurnstileFeatherEffect.cs). */
    public static final float FORWARD_IN = -80f, FORWARD_OUT = 50f, BACKWARD_IN = 50f, BACKWARD_OUT = -80f;
    public static final long IN_MS = 350, OUT_MS = 250, FORWARD_IN_STEP = 40, BACKWARD_IN_STEP = 50;
    /** 깃털 효과의 회전축: 화면 왼쪽 가장자리에서 화면 폭의 20%만큼 더 왼쪽. */
    private static final float FEATHER_CENTER = -0.2f;
    /** 깃털로 들어오는 항목 수의 한도 (그 뒤는 화면 밖이라 보이지 않는다). */
    private static final int FEATHER_MAX = 16;

    /**
     * v를 왼쪽 가장자리(pivotX)를 축으로 from°에서 to°로 돌린다. 들어올 때는 빠르게 들어와 부드럽게 멈추고(EaseOut),
     * 나갈 때는 천천히 시작해 빠르게 사라지며 끝에서 투명해진다(EaseIn, 마지막 4%에서 불투명도 1 → 0).
     * 늦게 시작하는 항목(delay)은 차례가 올 때까지 보이지 않는다.
     */
    public static ValueAnimator turn(View v, float pivotX, float from, float to, long ms, long delay, boolean out,
                                     Runnable onEnd) {
        v.setPivotX(pivotX);
        v.setPivotY(v.getHeight() / 2f);
        camera(v);
        v.setRotationY(from);
        if (!out) v.setAlpha(0f);
        ValueAnimator a = ValueAnimator.ofFloat(0f, 1f);
        a.setDuration(ms);
        a.setStartDelay(delay);
        a.setInterpolator(new LinearInterpolator());
        Interpolator curve = out ? EASE_IN : EASE_OUT;
        a.addUpdateListener(an -> {
            float t = an.getAnimatedFraction();
            v.setRotationY(from + (to - from) * curve.getInterpolation(t));
            v.setAlpha(out ? (t >= 0.96f ? 0f : 1f) : 1f);
        });
        a.addListener(new AnimatorListenerAdapter() {
            private boolean canceled;

            @Override
            public void onAnimationStart(Animator animation) {
                v.setLayerType(View.LAYER_TYPE_HARDWARE, null);
            }

            @Override
            public void onAnimationCancel(Animator animation) {
                canceled = true;
            }

            @Override
            public void onAnimationEnd(Animator animation) {
                v.setLayerType(View.LAYER_TYPE_NONE, null);
                if (!out || canceled) {
                    v.setRotationY(canceled && out ? 0f : to);
                    v.setAlpha(1f);
                    v.resetPivot();
                }
                if (onEnd != null && !canceled) onEnd.run();
            }
        });
        a.start();
        return a;
    }

    /**
     * 스위블: 창이 화면 앞쪽으로 젖혀진 채(-30°) 가로축을 중심으로 돌아 제자리에 선다 (SwivelFullScreenIn.xaml: RotationX -30° → 0°,
     * 350ms, EaseOut 지수 6, 축은 가운데). 키보드의 이모지·클립보드·텍스트 편집 창이 열릴 때 쓴다.
     */
    public static void swivelIn(View v) {
        if (v.getWidth() == 0 || v.getHeight() == 0) {
            v.post(() -> {
                if (v.getWidth() > 0 && v.getHeight() > 0) swivelIn(v);
            });
            return;
        }
        v.animate().cancel();
        v.setPivotX(v.getWidth() / 2f);
        v.setPivotY(v.getHeight() / 2f);
        camera(v);
        v.setRotationX(-30f);
        v.animate().rotationX(0f).setDuration(IN_MS).setInterpolator(EASE_OUT).withLayer()
                .withEndAction(v::resetPivot).start();
    }

    /**
     * 화면의 항목들이 깃털처럼 차례로 돌아 들어온다 (TurnstileFeatherEffect). items는 위에서 아래 차례이고, 화면 안에 보이는 것만
     * 움직인다. 모든 항목이 같은 축(화면 왼쪽 바깥)을 기준으로 돌아서 한 장의 문짝이 가지런히 열리듯 보인다.
     *
     * @param forward true면 새 화면이 들어올 때(-80° → 0°, 40ms 간격), false면 앞 화면으로 돌아올 때(50° → 0°, 50ms 간격)
     */
    public static void featherIn(View page, java.util.List<View> items, boolean forward) {
        int[] pl = new int[2], il = new int[2];
        page.getLocationInWindow(pl);
        int top = pl[1], bottom = pl[1] + page.getHeight();
        float pageW = page.getWidth();
        int n = 0;
        for (View item : items) {
            if (item.getVisibility() != View.VISIBLE || item.getWidth() == 0 || item.getHeight() == 0) continue;
            item.getLocationInWindow(il);
            if (il[1] >= bottom || il[1] + item.getHeight() <= top) continue;   // 화면 밖
            if (n >= FEATHER_MAX) break;
            float pivot = -(il[0] - pl[0]) + FEATHER_CENTER * pageW;
            turn(item, pivot, forward ? FORWARD_IN : BACKWARD_IN, 0f, IN_MS,
                    n * (forward ? FORWARD_IN_STEP : BACKWARD_IN_STEP), false, null);
            n++;
        }
    }

    /**
     * Metro 화면의 뿌리: 화면 전환(턴스타일) 동안 누르기를 막고, 다른 화면에서 돌아와 다시 보일 때 앞 화면으로 돌아오는 턴스타일을 튼다.
     * Metro 모드에서 SettingsFrame이 화면 전체를 이것으로 감싼다.
     */
    public static final class Page extends FrameLayout {
        private boolean busy, hidden;
        /** 화면 안에서 돌아 들어올 항목들 (머리글, 목록의 줄들). 없으면 화면 전체가 돈다. */
        private java.util.function.Supplier<java.util.List<View>> items;
        private View content;

        public Page(Context c) {
            super(c);
        }

        public void setContent(View v) {
            content = v;
            addView(v, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
        }

        public void setFeatherItems(java.util.function.Supplier<java.util.List<View>> s) {
            items = s;
        }

        /** 처음 그려지기 직전에 들어오는 턴스타일을 튼다 (화면 내용은 그 전에 다 만들어진다). */
        public void playEnterOnFirstDraw() {
            getViewTreeObserver().addOnPreDrawListener(new android.view.ViewTreeObserver.OnPreDrawListener() {
                @Override
                public boolean onPreDraw() {
                    getViewTreeObserver().removeOnPreDrawListener(this);
                    enter(true);
                    return true;
                }
            });
        }

        private void enter(boolean forward) {
            content.setRotationY(0f);
            content.setAlpha(1f);
            content.resetPivot();
            java.util.List<View> list = items == null ? null : items.get();
            busy = true;
            postDelayed(() -> busy = false, IN_MS / 2);   // 거의 다 들어오면 누를 수 있다
            if (list == null || list.isEmpty()) {
                turn(content, 0f, forward ? FORWARD_IN : BACKWARD_IN, 0f, IN_MS, 0, false, null);
            } else {
                featherIn(this, list, forward);
            }
        }

        /** 다른 화면으로 넘어간다: 이 화면이 문짝처럼 돌아 나간 뒤(0° → 50°) start를 부른다. */
        public void forward(Runnable start) {
            if (busy) return;
            busy = true;
            turn(content, 0f, 0f, FORWARD_OUT, OUT_MS, 0, true, () -> {
                busy = false;
                start.run();
                // 새 화면이 뜨지 않았으면(받는 앱이 없는 등) 1초 뒤에 이 화면을 다시 보여 준다.
                postDelayed(() -> {
                    if (getWindowVisibility() == VISIBLE && hasWindowFocus() && content.getAlpha() == 0f) enter(false);
                }, 1000);
            });
        }

        /** 뒤로 간다: 이 화면이 반대로 돌아 나간 뒤(0° → -80°) finish를 부른다. */
        public void back(Runnable finish) {
            if (busy) return;
            busy = true;
            turn(content, 0f, 0f, BACKWARD_OUT, OUT_MS, 0, true, finish);
        }

        public boolean busy() {
            return busy;
        }

        @Override
        protected void onWindowVisibilityChanged(int visibility) {
            super.onWindowVisibilityChanged(visibility);
            if (visibility != VISIBLE) {
                hidden = true;
            } else if (hidden) {
                hidden = false;
                busy = false;
                enter(false);   // 다른 화면에서 돌아왔다
            }
        }

        /** 가로로 밀어 옆 메뉴로 넘기는 피벗 (없으면 null). */
        private Pivot pivot;

        public void setPivot(Pivot p) {
            pivot = p;
        }

        void setBusy(boolean b) {
            busy = b;
        }

        @Override
        public boolean onInterceptTouchEvent(MotionEvent ev) {
            if (busy) return true;
            if (pivot != null && pivot.intercept(ev)) return true;
            return super.onInterceptTouchEvent(ev);
        }

        @Override
        public boolean onTouchEvent(MotionEvent ev) {
            if (busy) return true;
            if (pivot != null) return pivot.touch(ev);
            return super.onTouchEvent(ev);
        }
    }

    // ---------------------------------------------------------------- 피벗

    /**
     * 피벗 머리글: 지금 메뉴 이름을 크고 가는 소문자로 쓰고, 그 오른쪽에 다음 메뉴들의 이름이 옅게 이어지다 화면 끝에서 잘린다.
     * 가로로 미는 동안 이름 줄은 내용보다 느리게(한 화면을 넘길 때 이름 하나만큼) 움직이고 (패럴렉스), 옅은 색과 진한 색이 서로 바뀐다.
     * 이름은 직접 그려 프레임마다 글자를 다시 배치하지 않는다.
     */
    public static final class PivotHeader extends View {
        private final android.text.TextPaint paint = new android.text.TextPaint(android.graphics.Paint.ANTI_ALIAS_FLAG);
        /** 고리 차례의 메뉴 이름 (소문자). index가 지금 메뉴. */
        private final String[] names;
        private final int index;
        private final float gap;
        private float progress;   // -1(앞 메뉴) ~ 0(지금) ~ 1(다음 메뉴)
        private Runnable onPeek;
        private final android.graphics.Paint.FontMetrics fm = new android.graphics.Paint.FontMetrics();

        public PivotHeader(Context c, String[] names, int index) {
            super(c);
            this.names = new String[names.length];
            for (int i = 0; i < names.length; i++) this.names[i] = lower(names[i]);
            this.index = index;
            paint.setTypeface(light());
            paint.setTextSize(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 46, c.getResources().getDisplayMetrics()));
            paint.getFontMetrics(fm);
            gap = paint.measureText("  ");
            setContentDescription(names[index]);
        }

        /** 다음 메뉴 이름을 눌렀을 때. */
        public void setOnPeek(Runnable r) {
            onPeek = r;
        }

        public void setProgress(float p) {
            if (progress == p) return;
            progress = p;
            invalidate();
        }

        private String at(int k) {
            int n = names.length;
            return names[((index + k) % n + n) % n];
        }

        @Override
        protected void onMeasure(int widthSpec, int heightSpec) {
            int h = Math.round(fm.descent - fm.ascent) + getPaddingTop() + getPaddingBottom();
            setMeasuredDimension(MeasureSpec.getSize(widthSpec), h);
        }

        @Override
        protected void onDraw(android.graphics.Canvas c) {
            float y = getPaddingTop() - fm.ascent;
            float wCur = paint.measureText(at(0)), wPrev = paint.measureText(at(-1));
            // 한 화면을 넘기는 동안 이름 줄은 지금 이름(앞으로 갈 때는 앞 이름)의 폭만큼만 움직인다.
            float offset = progress >= 0 ? -progress * (wCur + gap) : -progress * (wPrev + gap);
            int dim = (fg & 0x00FFFFFF) | 0x4D000000;
            float p = Math.abs(progress);
            // 앞 메뉴 (뒤로 밀 때만 보인다)
            if (progress < 0) {
                paint.setColor(blendArgb(dim, fg, p));
                c.drawText(at(-1), offset - wPrev - gap, y, paint);
            }
            float x = offset;
            for (int k = 0; k < names.length && x < getWidth(); k++) {
                int color = k == 0 ? blendArgb(fg, dim, p) : k == 1 && progress > 0 ? blendArgb(dim, fg, p) : dim;
                paint.setColor(color);
                String s = at(k);
                c.drawText(s, x, y, paint);
                x += paint.measureText(s) + gap;
            }
        }

        @Override
        public boolean onTouchEvent(MotionEvent e) {
            if (e.getActionMasked() == MotionEvent.ACTION_DOWN) return onPeek != null;
            if (e.getActionMasked() == MotionEvent.ACTION_UP && onPeek != null
                    && e.getX() > paint.measureText(at(0)) + gap / 2) {
                performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP);
                onPeek.run();
            }
            return true;
        }

        private static int blendArgb(int a, int b, float t) {
            t = Math.max(0f, Math.min(1f, t));
            int aa = a >>> 24, ar = (a >> 16) & 0xFF, ag = (a >> 8) & 0xFF, ab = a & 0xFF;
            int ba = b >>> 24, br = (b >> 16) & 0xFF, bg = (b >> 8) & 0xFF, bb = b & 0xFF;
            return (Math.round(aa + (ba - aa) * t) << 24) | (Math.round(ar + (br - ar) * t) << 16)
                    | (Math.round(ag + (bg - ag) * t) << 8) | Math.round(ab + (bb - ab) * t);
        }
    }

    /**
     * Windows Phone 피벗처럼 가로로 밀어 설정 메뉴 사이를 오간다. 미는 동안 지금 내용(movers)은 손가락을 그대로 따라가고, 옆 메뉴의
     * 미리 만들어 둔 내용이 옆에서 따라 들어오며, 머리글 이름 줄은 더 느리게 움직인다. 손을 떼면 3분의 1을 넘겼거나 빠르게 튕겼을 때
     * 넘어가고(지수 곡선으로 마저 움직인 뒤 host.go), 아니면 제자리로 돌아온다.
     *
     * <p>가로로 끄는 뷰(슬라이더, 키보드 미리보기, 가로 스크롤) 위에서 시작한 손가락은 그 뷰가 쓴다. 옆 메뉴의 내용은 처음 한 번만
     * 만들어 보이지 않게 붙여 두므로, 미는 동안에는 위치만 바뀐다.
     */
    public static final class Pivot {
        public interface Host {
            /** 지금 넘길 수 있는지 (설정 창이 열려 있으면 false). */
            boolean canPivot();

            /** v가 가로로 끄는 동작을 직접 쓰는지 (그 위에서 시작한 손가락은 피벗이 가져가지 않는다). */
            boolean usesHorizontalDrag(View v);

            /** dir(-1 앞, +1 다음) 쪽 메뉴의 내용 (한 번만 불린다). */
            View preview(int dir);

            /** dir 쪽 메뉴로 넘어갔다: 그 메뉴 화면을 지금 모습 그대로 이어서 연다. */
            void go(int dir);
        }

        private final Page page;
        private final PivotHeader header;
        private final View area;
        private final View[] movers;
        private final Host host;
        private final int slop;
        private final float flingVelocity;
        private final FrameLayout[] holders = new FrameLayout[2];
        private float downX, downY, startX, dx;
        private boolean dragging, blocked, down;
        private android.view.VelocityTracker velocity;
        private ValueAnimator settle;

        public Pivot(Page page, PivotHeader header, View area, View[] movers, Host host) {
            this.page = page;
            this.header = header;
            this.area = area;
            this.movers = movers;
            this.host = host;
            android.view.ViewConfiguration vc = android.view.ViewConfiguration.get(page.getContext());
            slop = vc.getScaledTouchSlop();
            flingVelocity = vc.getScaledMinimumFlingVelocity() * 4f;
            page.setPivot(this);
            header.setOnPeek(() -> animateTo(1));
        }

        /** 옆 메뉴의 내용을 미리 만들어 보이지 않게 붙여 둔다 (화면이 한가할 때 부른다). */
        public void prepare() {
            for (int i = 0; i < 2; i++) holder(i == 0 ? -1 : 1);
        }

        private FrameLayout holder(int dir) {
            int i = dir < 0 ? 0 : 1;
            if (holders[i] == null) {
                View v = host.preview(dir);
                if (v == null) return null;
                FrameLayout h = new FrameLayout(page.getContext());
                h.setClipChildren(true);
                h.addView(v, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT));
                h.setVisibility(View.INVISIBLE);
                h.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
                page.addView(h, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, Math.max(1, area.getHeight())));
                holders[i] = h;
            }
            return holders[i];
        }

        /** 옆 메뉴 내용을 지금 내용 자리(area)에 맞춰 둔다. */
        private void place() {
            int[] a = new int[2], p = new int[2];
            area.getLocationInWindow(a);
            page.getLocationInWindow(p);
            for (int i = 0; i < 2; i++) {
                FrameLayout h = holder(i == 0 ? -1 : 1);
                if (h == null) continue;
                FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) h.getLayoutParams();
                if (lp.height != area.getHeight() || lp.topMargin != a[1] - p[1]) {
                    lp.height = Math.max(1, area.getHeight());
                    lp.topMargin = a[1] - p[1];
                    h.setLayoutParams(lp);
                }
                h.setVisibility(View.VISIBLE);
            }
        }

        boolean intercept(MotionEvent e) {
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    begin(e);
                    return false;
                case MotionEvent.ACTION_MOVE:
                    if (dragging) return true;
                    trackVelocity(e);
                    return maybeStart(e);
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    down = false;
                    return dragging;
                default:
                    return false;
            }
        }

        boolean touch(MotionEvent e) {
            trackVelocity(e);
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    begin(e);
                    return true;   // 아무도 받지 않은 손가락: 미는지 지켜본다
                case MotionEvent.ACTION_MOVE:
                    if (!dragging && !maybeStart(e)) return true;
                    update(e.getX() - startX);
                    return true;
                case MotionEvent.ACTION_UP:
                    if (dragging) {
                        velocity.computeCurrentVelocity(1000);
                        release(velocity.getXVelocity());
                    }
                    end();
                    return true;
                case MotionEvent.ACTION_CANCEL:
                    if (dragging) release(0);
                    end();
                    return true;
                default:
                    return true;
            }
        }

        private void begin(MotionEvent e) {
            down = true;
            dragging = false;
            downX = e.getX();
            downY = e.getY();
            if (velocity != null) velocity.recycle();
            velocity = android.view.VelocityTracker.obtain();
            velocity.addMovement(e);
            blocked = settle != null || !host.canPivot() || usesDrag(page, downX, downY);
        }

        private void trackVelocity(MotionEvent e) {
            if (velocity != null && e.getActionMasked() != MotionEvent.ACTION_DOWN) velocity.addMovement(e);
        }

        private void end() {
            down = false;
            dragging = false;
            if (velocity != null) {
                velocity.recycle();
                velocity = null;
            }
        }

        private boolean maybeStart(MotionEvent e) {
            if (blocked || !down) return false;
            float adx = Math.abs(e.getX() - downX), ady = Math.abs(e.getY() - downY);
            if (adx > slop && adx > ady * 1.5f) {
                dragging = true;
                startX = e.getX() - Math.signum(e.getX() - downX) * slop * 0.5f;   // 문턱만큼 튀지 않게
                place();
                page.getParent().requestDisallowInterceptTouchEvent(true);
                return true;
            }
            if (ady > slop) blocked = true;   // 세로 스크롤이 시작됐다
            return false;
        }

        /** 손가락이 놓인 곳의 뷰들 가운데 가로로 끄는 동작을 직접 쓰는 것이 있는지. */
        private boolean usesDrag(View v, float x, float y) {
            if (v.getVisibility() != View.VISIBLE) return false;
            if (v != page && (host.usesHorizontalDrag(v) || v.canScrollHorizontally(1) || v.canScrollHorizontally(-1))) return true;
            if (v instanceof android.view.ViewGroup) {
                android.view.ViewGroup g = (android.view.ViewGroup) v;
                for (int i = g.getChildCount() - 1; i >= 0; i--) {
                    View c = g.getChildAt(i);
                    if (c.getVisibility() != View.VISIBLE) continue;
                    float cx = x + g.getScrollX() - c.getLeft() - c.getTranslationX();
                    float cy = y + g.getScrollY() - c.getTop() - c.getTranslationY();
                    if (cx >= 0 && cy >= 0 && cx < c.getWidth() && cy < c.getHeight() && usesDrag(c, cx, cy)) return true;
                }
            }
            return false;
        }

        private void update(float d) {
            dx = d;
            float w = page.getWidth();
            for (View m : movers) if (m != null) m.setTranslationX(d);
            if (holders[0] != null) holders[0].setTranslationX(d - w);
            if (holders[1] != null) holders[1].setTranslationX(d + w);
            header.setProgress(w > 0 ? -d / w : 0f);
        }

        private void release(float vx) {
            float w = page.getWidth(), f = w > 0 ? -dx / w : 0f;
            int dir = 0;
            if (f > 1f / 3 || (vx < -flingVelocity && f > 0.05f)) dir = 1;
            else if (f < -1f / 3 || (vx > flingVelocity && f < -0.05f)) dir = -1;
            settleTo(dir);
        }

        /** 다음 메뉴 이름을 눌렀을 때: 민 것처럼 넘어간다. */
        private void animateTo(int dir) {
            if (settle != null || !host.canPivot()) return;
            place();
            settleTo(dir);
        }

        private void settleTo(int dir) {
            float w = page.getWidth();
            float from = dx, to = -dir * w;
            page.setBusy(true);
            settle = ValueAnimator.ofFloat(0f, 1f);
            settle.setDuration(Math.round(150 + 250 * Math.min(1f, Math.abs(to - from) / Math.max(1f, w))));
            settle.setInterpolator(new Exp(5, true));
            settle.addUpdateListener(a -> update(from + (to - from) * (float) a.getAnimatedValue()));
            settle.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationStart(Animator animation) {
                    for (View m : movers) if (m != null) m.setLayerType(View.LAYER_TYPE_HARDWARE, null);
                }

                @Override
                public void onAnimationEnd(Animator animation) {
                    for (View m : movers) if (m != null) m.setLayerType(View.LAYER_TYPE_NONE, null);
                    settle = null;
                    if (dir != 0) {
                        host.go(dir);   // 화면이 바뀌는 동안은 계속 누르기를 막는다
                        return;
                    }
                    for (FrameLayout h : holders) if (h != null) h.setVisibility(View.INVISIBLE);
                    page.setBusy(false);
                }
            });
            settle.start();
        }
    }

    /**
     * Metro 모드에서 이 화면이 열리고 닫힐 때 시스템 전환 효과를 끈다 (대신 턴스타일을 직접 그린다). onCreate에서 부른다.
     * Android 14부터는 화면마다 한 번 정해 두면 되고, 그 전에는 열릴 때와 닫힐 때마다 overridePendingTransition을 부른다.
     */
    @SuppressWarnings("deprecation")
    public static void noSystemTransition(Activity a) {
        if (android.os.Build.VERSION.SDK_INT >= 34) {
            a.overrideActivityTransition(Activity.OVERRIDE_TRANSITION_OPEN, 0, 0);
            a.overrideActivityTransition(Activity.OVERRIDE_TRANSITION_CLOSE, 0, 0);
        } else {
            a.overridePendingTransition(0, 0);
        }
    }

    /** 턴스타일로 나간 뒤 화면을 닫는다 (시스템 닫기 효과 없이). */
    @SuppressWarnings("deprecation")
    public static void finishQuietly(Activity a) {
        a.finish();
        if (android.os.Build.VERSION.SDK_INT < 34) a.overridePendingTransition(0, 0);
    }
}
