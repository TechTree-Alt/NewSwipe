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
import android.widget.LinearLayout;
import android.widget.TextView;

import com.alternative_studios.newswipe.L;
import com.alternative_studios.newswipe.R;

/**
 * Metro UI 모드 (실험적): Windows Phone 8의 디자인 언어를 흉내 낸다.
 *
 * <ul>
 *   <li>색: WP8 SDK의 ThemeResources.xaml (Dark/Light) 값 그대로. 순수한 검정·흰색 바탕, 크롬 #1F1F1F·#DDDDDD, 옅은 글자
 *       (PhoneSubtleColor), 흐린 글자(PhoneDisabledColor), 강조색.</li>
 *   <li>모양: 모서리 없는 네모, 그림자·그라데이션 없음 ({@link Ui#round}·{@link Ui#ripple}이 이 모드에서 네모·눌림 없음이 된다).</li>
 *   <li>글자: SDK의 글꼴 크기(PhoneFontSize*)를 {@link #sp}로 옮긴다. Segoe WP 대신 sans-serif(-light, -medium).</li>
 *   <li>움직임: 턴스타일 화면 전환, 깃털처럼 줄줄이 들어오는 목록, 누른 자리로 기울어지는 틸트, 스위블.</li>
 * </ul>
 *
 * <p>크기: WP8 화면은 480px 폭이고 Android 폰은 대개 360dp 폭이라, SDK의 px 값에 0.75를 곱해 dp(글자는 sp)로 쓴다 ({@link #px}).
 *
 * <p>움직임의 값은 Microsoft가 공개한 Windows Phone Toolkit(Ms-PL) 소스의 값을 그대로 옮겼다:
 * Transitions/Storyboards/Turnstile*.xaml (들어올 때 -80°→0° 350ms ExponentialEase EaseOut 지수 6, 나갈 때 0°→50° 250ms
 * EaseIn 지수 6, 돌아갈 때 0°→-80°, 다시 들어올 때 50°→0°, 회전축은 화면 왼쪽 가장자리), Effects/TurnstileFeatherEffect.cs
 * (깃털: 회전축이 화면 폭의 20% 바깥 왼쪽, 항목마다 40ms씩 늦게), Effects/TiltEffect.cs (최대 0.3 rad, 가운데를 누르면 25px
 * 들어감, 떼고 200ms 뒤 100ms 동안 로그 곡선으로 돌아옴), Swivel*.xaml.
 *
 * <p>{@link #on}과 색은 {@code AppTheme.of}가 부를 때마다 설정값에 맞춰진다 (설정 화면과 키보드가 같은 프로세스다). 메인 스레드에서만 쓴다.
 */
public final class Metro {
    private Metro() {
    }

    /** Metro UI 모드가 켜져 있는지. */
    public static volatile boolean on;
    /**
     * 지금 화면 모드의 Metro 색 (ThemeResources.xaml): 글자 PhoneForeground, 바탕 PhoneBackground, 크롬 PhoneChrome(앱 바·메시지 상자),
     * 옅은 글자 PhoneSubtle, 흐린 글자 PhoneDisabled(스크롤 표시), 강조색과 그 위의 글자. 반투명 색은 바탕 위에 얹은 불투명 색으로 둔다.
     */
    public static int fg = 0xFFFFFFFF, bg = 0xFF000000, chrome = 0xFF1F1F1F, subtle = 0xFF999999, disabled = 0xFF666666,
            accent = 0xFF1BA1E2, onAccent = 0xFFFFFFFF;
    public static boolean dark = true;

    /** PhoneBackgroundColor·PhoneForegroundColor·PhoneChromeColor (다크). */
    public static final int DARK_BG = 0xFF000000, DARK_FG = 0xFFFFFFFF, DARK_CHROME = 0xFF1F1F1F;
    /** 라이트: 바탕 흰색, 글자는 #DE000000(검정 87%)을 흰 바탕에 얹은 색, 크롬 #DDDDDD. */
    public static final int LIGHT_BG = 0xFFFFFFFF, LIGHT_FG = 0xFF212121, LIGHT_CHROME = 0xFFDDDDDD;
    /** PhoneSubtleColor: 다크의 #99FFFFFF, 라이트의 #66000000이 각 바탕 위에서 모두 #999999가 된다. */
    public static final int SUBTLE = 0xFF999999;
    /** PhoneDisabledColor: 다크 #66FFFFFF(검정 위 #666666), 라이트 #4D000000(흰 위 #B3B3B3). */
    public static final int DARK_DISABLED = 0xFF666666, LIGHT_DISABLED = 0xFFB3B3B3;

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
        disabled = isDark ? DARK_DISABLED : LIGHT_DISABLED;
        accent = accentColor;
        onAccent = onAccentColor;
    }

    // ---------------------------------------------------------------- 곡선

    /**
     * Silverlight의 ExponentialEase: EaseIn은 (e^(kt) - 1) / (e^k - 1), EaseOut은 그것을 뒤집은 것.
     * Windows Phone의 화면 전환은 거의 모두 지수 6을 쓴다 (천천히 시작해 빠르게 사라지거나, 빠르게 들어와 부드럽게 멈춘다).
     */
    public static final class Exp implements Interpolator {
        private final double k, denominator;
        private final boolean out;

        public Exp(double exponent, boolean easeOut) {
            k = exponent;
            out = easeOut;
            denominator = Math.exp(exponent) - 1;   // 프레임마다 다시 계산하지 않게
        }

        private double in(double t) {
            return (Math.exp(k * t) - 1) / denominator;
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

    // ---------------------------------------------------------------- 크기·글자

    /** WP 화면(480px 폭)의 px를 Android dp로 옮기는 비율 (360dp 폭 기준). */
    public static final float PX = 0.75f;

    /** WP의 px 값을 이 기기의 px로 (480px 폭 화면 기준 값 → dp → px). */
    public static int px(Context c, float wpPx) {
        return Ui.dp(c, wpPx * PX);
    }

    /** WP의 글꼴 크기(px)를 sp로. */
    public static float sp(float wpPx) {
        return wpPx * PX;
    }

    /** SDK의 글꼴 크기 (ThemeResources.xaml의 PhoneFontSize*, px). */
    public static final float FONT_SMALL = 18.667f, FONT_NORMAL = 20f, FONT_MEDIUM = 22.667f, FONT_MEDIUM_LARGE = 25.333f,
            FONT_LARGE = 32f, FONT_EXTRA_LARGE = 42.667f, FONT_EXTRA_EXTRA_LARGE = 72f;

    private static Typeface light, semibold, regular;

    /** Segoe WP Light·SemiLight 대신 쓰는 가는 글꼴 (큰 제목·항목 이름, 묶음 머리글). */
    public static Typeface light() {
        if (light == null) light = Typeface.create("sans-serif-light", Typeface.NORMAL);
        return light;
    }

    /** Segoe WP Semibold 대신 (피벗 제목, 버튼, 메시지 상자 제목). */
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

    /**
     * v(줄)를 누르면 그 안의 target만 곧게 들어간다 (토글 줄: Windows Phone은 ToggleSwitch의 스위치만 틸트한다). target을 직접 누르면
     * target이 스스로 {@link #onTiltTouch}로 누른 쪽으로 기운다.
     */
    public static void tilt(View v, View target) {
        if (on) v.setOnTouchListener((row, e) -> {
            tiltTouch(row, target, e);
            return false;
        });
    }

    /** 커스텀 뷰가 자기 onTouchEvent에서 직접 부른다 (OnTouchListener를 쓸 수 없을 때). */
    public static void onTiltTouch(View v, MotionEvent e) {
        tiltTouch(v, v, e);
    }

    /** touched를 누르는 동안 target을 기울인다 (둘이 다르면 기울지 않고 곧게 들어간다). */
    private static void tiltTouch(View touched, View target, MotionEvent e) {
        if (!on) return;
        boolean self = touched == target;
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                if (!touched.isEnabled() || !touched.isClickable()) return;
                applyTilt(target, self ? e.getX() : target.getWidth() / 2f, self ? e.getY() : target.getHeight() / 2f);
                break;
            case MotionEvent.ACTION_MOVE:
                if (TILTED.get(target) == null) return;
                float slop = android.view.ViewConfiguration.get(touched.getContext()).getScaledTouchSlop();
                if (e.getX() < -slop || e.getY() < -slop || e.getX() > touched.getWidth() + slop
                        || e.getY() > touched.getHeight() + slop) {
                    releaseTilt(target, false);   // 손가락이 벗어났다 (스크롤 등)
                } else if (self) {
                    applyTilt(target, e.getX(), e.getY());
                }
                break;
            case MotionEvent.ACTION_UP:
                releaseTilt(target, true);
                break;
            case MotionEvent.ACTION_CANCEL:
                releaseTilt(target, false);
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
            perspective(v);
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
    /** 깃털 차례의 한도: 이보다 뒤의 항목은 마지막 차례와 함께 들어온다 (들어오는 시간이 너무 길어지지 않게). */
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
        perspective(v);
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

            // 하드웨어 층은 쓰지 않는다: 회전은 그리기를 다시 하지 않는 속성이고 불투명도도 0/1로만 바뀌어 층이 필요 없다.
            // 깃털 효과는 항목 십수 개가 함께 돌아, 층을 쓰면 화면 전환마다 GPU 메모리를 십수 MB씩 잠깐 쓴다.
            @Override
            public void onAnimationCancel(Animator animation) {
                canceled = true;
            }

            @Override
            public void onAnimationEnd(Animator animation) {
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
        perspective(v);
        v.setRotationX(-30f);
        v.animate().rotationX(0f).setDuration(IN_MS).setInterpolator(EASE_OUT)   // 회전만 하므로 층 없이
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
        float pageW = page.getWidth();
        android.graphics.Rect seen = new android.graphics.Rect();
        int n = 0;
        for (View item : items) {
            if (item.getVisibility() != View.VISIBLE || item.getWidth() == 0 || item.getHeight() == 0) continue;
            // 실제로 보이는 것만: 스크롤 목록 밖으로 잘린 줄(아래 미리보기 뒤에 가려진 줄 등)은 세지 않는다.
            // 세면 한도가 그런 줄로 차서, 맨 끝의 아래 미리보기가 움직이지 않고 그냥 나타난다.
            if (!item.getGlobalVisibleRect(seen)) continue;
            item.getLocationInWindow(il);
            float pivot = -(il[0] - pl[0]) + FEATHER_CENTER * pageW;
            // 한도를 넘은 항목도 빼지 않고 마지막 차례와 함께 들어온다 (아래 미리보기처럼 끝에 있는 것이 그냥 생기지 않게).
            int order = Math.min(n, FEATHER_MAX - 1);
            turn(item, pivot, forward ? FORWARD_IN : BACKWARD_IN, 0f, IN_MS,
                    order * (forward ? FORWARD_IN_STEP : BACKWARD_IN_STEP), false, null);
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

        /**
         * 처음 그려지기 직전에 들어오는 턴스타일을 튼다 (화면 내용은 그 전에 다 만들어진다).
         * 키보드의 도구 막대에서 설정을 열면 처음 그릴 때 키보드가 아직 떠 있어 창이 키보드 높이만큼 줄어 있다. 그대로 틀면 그 아래의
         * 항목은 보이지 않는 것으로 쳐서 빠지고, 키보드가 내려간 뒤 움직이지 않고 그냥 나타난다. 그래서 키보드가 내려가 창이 다 펴질
         * 때까지 화면을 감춰 두었다가 튼다 (오래 걸리면 ENTER_IME_WAIT_MS 뒤에 그냥 튼다).
         */
        public void playEnterOnFirstDraw() {
            getViewTreeObserver().addOnPreDrawListener(new android.view.ViewTreeObserver.OnPreDrawListener() {
                @Override
                public boolean onPreDraw() {
                    getViewTreeObserver().removeOnPreDrawListener(this);
                    if (imeCovering()) {
                        enterAfterIme();
                    } else {
                        enter(true);
                    }
                    return true;
                }
            });
        }

        private static final long ENTER_IME_WAIT_MS = 600;

        /** 키보드(입력기)가 이 창을 덮어 창이 줄어 있는지. */
        private boolean imeCovering() {
            android.view.WindowInsets in = getRootWindowInsets();
            if (in == null) return false;
            if (android.os.Build.VERSION.SDK_INT >= 30) {
                return in.getInsets(android.view.WindowInsets.Type.ime()).bottom > 0;
            }
            // Android 9~10: 시스템 창 여백이 하단 바보다 훨씬 크면 키보드가 덮고 있다.
            return in.getSystemWindowInsetBottom() - in.getStableInsetBottom() > Math.round(80 * getResources().getDisplayMetrics().density);
        }

        private void enterAfterIme() {
            content.setAlpha(0f);
            busy = true;
            final boolean[] done = {false};
            final View.OnLayoutChangeListener[] holder = new View.OnLayoutChangeListener[1];
            Runnable go = () -> {
                if (done[0]) return;
                done[0] = true;
                removeOnLayoutChangeListener(holder[0]);
                // 새 크기로 다 배치된 다음 프레임에 튼다 (항목들의 자리가 정해진 뒤).
                getViewTreeObserver().addOnPreDrawListener(new android.view.ViewTreeObserver.OnPreDrawListener() {
                    @Override
                    public boolean onPreDraw() {
                        getViewTreeObserver().removeOnPreDrawListener(this);
                        enter(true);
                        return true;
                    }
                });
                invalidate();
            };
            holder[0] = (v, l, t, r, b, ol, ot, or, ob) -> {
                if (!imeCovering()) go.run();
            };
            addOnLayoutChangeListener(holder[0]);
            postDelayed(go, ENTER_IME_WAIT_MS);
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
                    if (getWindowVisibility() == VISIBLE && hasWindowFocus() && content.getAlpha() == 0f) enter(true);
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
                enter(true);   // 다른 화면에서 돌아왔다: 처음 열 때와 같은 깃털 효과로 들어온다
            }
        }

        /** 가로로 밀어 옆 섹션으로 넘기는 피벗 (없으면 null). */
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
     * 피벗 머리글: 섹션 이름들을 크고 가는 소문자로 한 줄에 이어 쓰고 화면 끝에서 자른다. 맨 왼쪽(첫 자리)의 이름부터 고리 차례로
     * 쓰되 멈춰 있을 때는 이름마다 한 번씩만, 움직이는 동안에는 화면 끝까지 이어 그린다. 고른 이름만 글자 색, 나머지는 옅게 쓴다. 줄 전체의 가로 자리(offset)는 {@link Pivot}이 움직인다.
     * 이름은 직접 그려 프레임마다 글자를 다시 배치하지 않는다.
     *
     * <p>Microsoft.Phone의 Pivot 템플릿: PivotHeaderItem은 PhoneFontFamilySemiLight, PivotHeaderFontSize 72px, Padding 21,0,1,0
     * (이름 사이 22px), 고르지 않은 이름은 PhonePivotUnselectedItemOpacity (다크 0.4, 라이트 0.23144).
     */
    public static final class PivotHeader extends View {
        private final android.text.TextPaint paint = new android.text.TextPaint(android.graphics.Paint.ANTI_ALIAS_FLAG);
        /** 섹션 이름 (소문자). */
        private final String[] names;
        /** 이름의 폭 + 이름 사이 (PivotHeadersControl.GetItemWidth: 글자 폭 + Padding 21,1). 처음 한 번만 잰다. */
        private final float[] widths;
        /** 맨 왼쪽에 오는 이름(VisualFirstIndex)과 고른 이름. 넘어가는 동안 둘이 잠시 다르다. */
        private int first, selected;
        /** 이름 줄 전체의 가로 자리 (px). */
        private float offset;
        private java.util.function.IntConsumer onTap;
        private final android.graphics.Paint.FontMetrics fm = new android.graphics.Paint.FontMetrics();

        public PivotHeader(Context c, String[] names, int index) {
            super(c);
            this.names = new String[names.length];
            for (int i = 0; i < names.length; i++) this.names[i] = lower(names[i]);
            paint.setTypeface(light());
            paint.setTextSize(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, sp(FONT_EXTRA_EXTRA_LARGE),
                    c.getResources().getDisplayMetrics()));
            paint.getFontMetrics(fm);
            float gap = px(c, 22);
            widths = new float[names.length];
            for (int i = 0; i < names.length; i++) widths[i] = paint.measureText(this.names[i]) + gap;
            first = selected = index;
            setContentDescription(names[index]);
        }

        /** k번째 뒤의 이름(1 = 바로 다음)을 눌렀을 때. */
        public void setOnTap(java.util.function.IntConsumer l) {
            onTap = l;
        }

        private int ring(int i) {
            int n = names.length;
            return (i % n + n) % n;
        }

        /** i번째 이름의 폭 (이름 사이 포함). */
        float widthOf(int i) {
            return widths[ring(i)];
        }

        int selected() {
            return selected;
        }

        void setSelected(int i) {
            selected = i;
            setContentDescription(names[i]);
            invalidate();
        }

        void setFirst(int i) {
            first = i;
            invalidate();
        }

        int first() {
            return first;
        }

        float offset() {
            return offset;
        }

        void setOffset(float px) {
            if (offset == px) return;
            offset = px;
            invalidate();
        }

        /** 글자 줄의 높이는 글꼴의 위아래 끝(top·bottom)까지: Silverlight TextBlock의 줄 높이(글자 크기의 약 1.33배)와 같다. */
        @Override
        protected void onMeasure(int widthSpec, int heightSpec) {
            int h = Math.round(fm.bottom - fm.top) + getPaddingTop() + getPaddingBottom();
            setMeasuredDimension(MeasureSpec.getSize(widthSpec), h);
        }

        @Override
        protected void onDraw(android.graphics.Canvas c) {
            float y = getPaddingTop() - fm.top;
            // 고르지 않은 이름: 다크는 흰색 40%, 라이트는 검정 87%(#DE000000)의 23.144% (= 검정 20%).
            int dim = dark ? 0x66FFFFFF : 0x33000000;
            // 멈춰 있을 때는 이름마다 한 번씩만 쓴다. 움직이는 동안에는 줄을 화면 끝까지 고리 차례로 이어 그려, 다음다음 이름이
            // 넘기기가 끝날 때까지 비어 있지 않게 한다. 되풀이되는 이름(고리를 한 바퀴 넘은 이름, 오른쪽으로 밀 때 왼쪽에 들어오는
            // 앞 이름)은 움직인 만큼 서서히 나타나고, 그 자리를 내주는 오른쪽 끝의 같은 이름은 서서히 사라진다
            // (PivotHeadersControl.FadeInItemIfNeeded: 끝에 돌아 붙는 이름은 페이드 인).
            int n = names.length;
            float move = offset < 0 ? Math.min(1f, -offset / widths[ring(first)])
                    : offset > 0 ? Math.min(1f, offset / widths[ring(first - 1)]) : 0f;
            float x = offset;
            // 왼쪽으로 밀 때 더 그리는 이름은 하나(고리를 한 바퀴 넘은 첫 이름)면 된다: 넘기기가 끝나 첫 자리가 바뀌면
            // 그 이름이 고리의 마지막 이름이 되어 같은 자리에 그대로 남는다.
            for (int k = 0; x < getWidth() && k <= n; k++) {
                int i = ring(first + k);
                float alpha = 1f;
                if (k == n) alpha = offset < 0 ? move : 0f;                 // 한 바퀴 넘은 이름: 왼쪽으로 밀 때만
                else if (k == n - 1 && offset > 0 && n > 1) alpha = 1f - move;   // 앞 이름이 왼쪽으로 옮겨 가는 중
                if (alpha <= 0f) {
                    x += widths[i];
                    continue;
                }
                // 한 바퀴 넘은 이름은 지금 고른 이름이라도 되풀이된 자리이므로 옅게 (넘기기가 끝나면 그 자리는 고르지 않은 이름).
                drawName(c, i, x, y, dim, alpha, k == n);
                x += widths[i];
            }
            if (offset > 0 && n > 1) {
                int i = ring(first - 1);
                drawName(c, i, offset - widths[i], y, dim, move, false);
            }
        }

        private void drawName(android.graphics.Canvas c, int i, float x, float y, int dim, float alpha, boolean repeat) {
            int color = i == selected && !repeat ? fg : dim;
            paint.setColor(color);
            paint.setAlpha(Math.round((color >>> 24) * Math.max(0f, Math.min(1f, alpha))));
            c.drawText(names[i], x, y, paint);
        }

        @Override
        public boolean onTouchEvent(MotionEvent e) {
            if (e.getActionMasked() == MotionEvent.ACTION_DOWN) return onTap != null;
            if (e.getActionMasked() == MotionEvent.ACTION_UP && onTap != null) {
                // 누른 자리의 이름 (지금 이름은 0): Windows Phone 피벗처럼 머리글의 이름을 누르면 그 섹션으로 간다.
                float x = offset + widths[ring(first)];
                if (e.getX() <= x) return true;   // 지금 이름을 눌렀다
                for (int k = 1; k < names.length; k++) {
                    x += widthOf(first + k);
                    if (e.getX() < x) {
                        if (ring(first + k) != selected) {
                            performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP);
                            onTap.accept(k);
                        }
                        break;
                    }
                }
            }
            return true;
        }
    }

    /**
     * WinRT XAML(WP8.1)의 크기 단위(유효 픽셀): WP8.1 폰은 화면 폭이 400 유효 픽셀쯤이라 360dp 폭 기준으로 0.9를 곱한다.
     */
    private static float epx(Context c, float v) {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v * 0.9f, c.getResources().getDisplayMetrics());
    }

    /** KeySpline 0.1,0.9 0.2,1.0 (Pivot의 fly-in·머리글 움직임): 빠르게 들어와 길게 멈춘다. */
    private static final Interpolator FLY_SPLINE = new android.view.animation.PathInterpolator(0.1f, 0.9f, 0.2f, 1f);

    /**
     * Windows Phone 8.1 피벗: 한 메뉴 안의 섹션(페이지)들을 가로로 밀어 오간다. 페이지는 모두 같은 자리에 겹쳐 두고 지금 페이지
     * (미는 동안에는 그 옆 페이지까지)만 보인다. 움직임은 WP8.1의 Pivot을 이어받은 Microsoft의 공개 소스(microsoft-ui-xaml의
     * dxaml/phone/lib/Pivot_Partial.cpp, dxaml/xcp/components/pivot/PivotStateMachine.cpp)를 따른다. 줄마다 따로 늦게 들어오는
     * SlideInAnimationGroup 효과는 쓰지 않는다:
     * <ul>
     * <li>미는 동안 (DirectManipulation 패닝): 페이지들이 손가락을 그대로 따라가 옆 페이지가 붙어 들어온다. 이름 줄은 한 화면을 밀 때
     *     이름 하나만큼 따라간다.</li>
     * <li>손을 떼면 (관성 + 한 칸짜리 스냅 포인트, MandatorySingle): 튕긴 속도로 멈출 자리를 내다보고 가장 가까운 칸(최대 한 칸)으로
     *     감속하며 붙는다. 다른 칸에 닿아 멈추면 그때 고른 이름이 바뀐다.</li>
     * <li>머리글의 이름을 누르면 (fly-out → fly-in): 지금 페이지가 83ms 동안 7px 밀려나며 67ms 만에 사라지고, 새 페이지가 20px 옆에서
     *     767ms 동안 KeySpline(0.1,0.9,0.2,1)으로 들어오며 333ms 만에 나타난다. 이름 줄은 850ms 동안 같은 곡선으로 옮겨 간다.</li>
     * </ul>
     *
     * <p>가로로 끄는 뷰(슬라이더, 키보드 미리보기, 가로 스크롤) 위에서 시작한 손가락은 그 뷰가 쓴다.
     */
    public static final class Pivot {
        public interface Host {
            /** 지금 넘길 수 있는지 (설정 창이 열려 있으면 false). */
            boolean canPivot();

            /** v가 가로로 끄는 동작을 직접 쓰는지 (그 위에서 시작한 손가락은 피벗이 가져가지 않는다). */
            boolean usesHorizontalDrag(View v);

            /** 지금 페이지가 index로 바뀌었다. */
            void onSelected(int index);
        }

        /** 손을 뗀 뒤 붙는 데 걸리는 가장 긴 시간 (PivotStateMachine.c_pivotFlickAnimationDuration 0.4초)과 가장 짧은 시간. */
        private static final long SNAP_MAX_MS = 400, SNAP_MIN_MS = 200;
        /** 관성으로 멈출 자리를 내다보는 시간 (초): 튕긴 속도 × 이 시간만큼 더 간다고 본다. */
        private static final float INERTIA_S = 0.3f;
        /** PivotAnimator: fly-out 83ms·7px·불투명도 67ms, fly-in 767ms·20px·불투명도 333ms. */
        private static final long FLY_OUT_MS = 83, FLY_OUT_FADE_MS = 67, FLY_IN_MS = 767, FLY_IN_FADE_MS = 333;
        private static final float FLY_OUT_PX = 7, FLY_IN_PX = 20;

        private final Page page;
        private final PivotHeader header;
        private final View[] pages;
        private final Host host;
        private final int slop;
        private int index;
        private float downX, downY, startX, dx;
        private boolean dragging, blocked, down;
        private android.view.VelocityTracker velocity;
        /** 페이지와 이름 줄의 움직임 (없으면 null). */
        private ValueAnimator content, names;

        public Pivot(Page page, PivotHeader header, View[] pages, int index, Host host) {
            this.page = page;
            this.header = header;
            this.pages = pages;
            this.host = host;
            this.index = index;
            slop = android.view.ViewConfiguration.get(page.getContext()).getScaledTouchSlop();
            for (int i = 0; i < pages.length; i++) pages[i].setVisibility(i == index ? View.VISIBLE : View.INVISIBLE);
            header.setOnTap(k -> {
                if (!busy() && host.canPivot()) flyTo(k);
            });
            page.setPivot(this);
        }

        public int index() {
            return index;
        }

        /** 움직임 없이 i번째 페이지로 바꾼다 (검색 결과로 들어왔을 때). */
        public void select(int i) {
            if (busy() || i == index) return;
            show(i);
            header.setFirst(i);
            header.setSelected(i);
            header.setOffset(0f);
        }

        private boolean busy() {
            return content != null || names != null;
        }

        /** 페이지 i만 제자리에 보인다. */
        private void show(int i) {
            for (View p : pages) {
                p.setTranslationX(0f);
                p.setAlpha(1f);
                p.setVisibility(View.INVISIBLE);
            }
            pages[i].setVisibility(View.VISIBLE);
            index = i;
            dx = 0;
            host.onSelected(i);
        }

        private int at(int k) {
            int n = pages.length;
            return ((index + k) % n + n) % n;
        }

        // ------------------------------------------------ 손가락

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
                    pan(e.getX() - startX);
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
            blocked = busy() || !host.canPivot() || usesDrag(page, downX, downY);
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

        /**
         * 내용을 d만큼 옮긴다: 지금 페이지와 민 쪽의 옆 페이지가 나란히 붙어 움직이고(반대쪽 옆 페이지는 숨긴다), 이름 줄은 한 화면에
         * 이름 하나만큼(왼쪽으로는 지금 이름, 오른쪽으로는 앞 이름의 폭) 따라간다.
         */
        private void pan(float d) {
            dx = d;
            float w = Math.max(1, page.getWidth());
            View in = d < 0 ? pages[at(1)] : d > 0 ? pages[at(-1)] : null;
            for (int i = 0; i < pages.length; i++) {
                if (i != index && pages[i] != in) pages[i].setVisibility(View.INVISIBLE);
            }
            pages[index].setTranslationX(d);
            if (in != null && in != pages[index]) {
                in.setTranslationX(d < 0 ? d + w : d - w);
                in.setVisibility(View.VISIBLE);
            }
            float item = d < 0 ? header.widthOf(index) : header.widthOf(index - 1);
            header.setOffset(d / w * item);
            // 고른 이름은 페이지가 반 넘게 넘어가면 바로 옆 이름으로 밝아진다 (반 아래로 돌아오면 다시 지금 이름).
            int lit = Math.abs(d) > w / 2f ? at(d < 0 ? 1 : -1) : index;
            if (lit != header.selected()) header.setSelected(lit);
        }

        /**
         * 손을 뗐을 때: 관성으로 멈출 자리(지금 자리 + 속도 × 0.3초)에 가장 가까운 칸으로 붙는다. 칸은 한 번에 하나까지(MandatorySingle).
         * 감속 곡선의 첫 속도가 손가락 속도와 이어지도록 시간을 정한다 (200~400ms).
         */
        private void release(float vx) {
            float w = Math.max(1, page.getWidth());
            float projected = dx + vx * INERTIA_S;
            int dir = projected <= -w / 2f ? 1 : projected >= w / 2f ? -1 : 0;
            float to = -dir * w, from = dx;
            float dist = Math.abs(to - from);
            // 감속(EaseOut cubic)의 첫 속도는 3 × 거리 / 시간이므로, 손가락 속도와 같아지는 시간을 쓴다.
            long ms = Math.abs(vx) > 1 ? Math.round(3000 * dist / Math.abs(vx)) : SNAP_MAX_MS;
            ms = Math.max(SNAP_MIN_MS, Math.min(SNAP_MAX_MS, ms));
            page.setBusy(dir != 0);
            content = run(ms, EASE_OUT_CUBIC, f -> pan(from + (to - from) * f), () -> {
                content = null;
                page.setBusy(false);
                if (dir != 0) {
                    // 페이지가 자리에 닿아 멈춘 때 고른 섹션이 바뀐다 (이름은 반 넘게 넘어갔을 때 이미 밝아졌다).
                    int target = at(dir);
                    show(target);
                    header.setFirst(target);
                    header.setSelected(target);
                }
                header.setOffset(0f);
                for (View p : pages) if (p != pages[index]) p.setVisibility(View.INVISIBLE);
            });
        }

        /**
         * 머리글에서 k번째 뒤의 이름을 눌렀을 때 (PivotAnimator의 fly-out → fly-in). 지금 페이지가 7px 밀려나며 사라지고(83ms, 67ms),
         * 새 페이지가 20px 옆에서 들어오며 나타난다(767ms, 333ms, KeySpline). 이름 줄은 누른 이름이 지금 자리에 오도록 850ms 동안
         * 같은 곡선으로 옮겨 간다.
         */
        private void flyTo(int k) {
            int target = at(k);
            if (target == index) return;
            Context c = page.getContext();
            View out = pages[index];
            page.setBusy(true);
            // 이름 줄: 지금 이름부터 누른 이름 앞까지의 폭만큼 왼쪽으로.
            float shift = 0;
            for (int i = 0; i < k; i++) shift += header.widthOf(header.first() + i);
            float distance = shift;
            names = run(FLY_OUT_MS + FLY_IN_MS, FLY_SPLINE, f -> header.setOffset(-distance * f), () -> {
                names = null;
                header.setFirst(target);
                header.setOffset(0f);
            });
            float outPx = epx(c, FLY_OUT_PX), inPx = epx(c, FLY_IN_PX);
            content = run(FLY_OUT_MS, null, f -> {
                out.setTranslationX(-outPx * f);
                out.setAlpha(Math.max(0f, 1f - f * FLY_OUT_MS / FLY_OUT_FADE_MS));
            }, () -> {
                // fly-in: 고른 섹션이 바뀐다 (SetSelectedIndex).
                show(target);
                header.setSelected(target);
                View in = pages[target];
                in.setTranslationX(inPx);
                in.setAlpha(0f);
                page.setBusy(false);
                content = run(FLY_IN_MS, null, f -> {
                    in.setTranslationX(inPx * (1f - FLY_SPLINE.getInterpolation(f)));
                    float fade = Math.min(1f, f * FLY_IN_MS / FLY_IN_FADE_MS);
                    in.setAlpha(FLY_SPLINE.getInterpolation(fade));
                }, () -> {
                    content = null;
                    in.setTranslationX(0f);
                    in.setAlpha(1f);
                });
            });
        }

        /** ms 동안 0 → 1로 움직이며 step을 부르고, 끝나면 done을 부른다 (ease가 null이면 곧게). */
        private static ValueAnimator run(long ms, Interpolator ease, java.util.function.Consumer<Float> step, Runnable done) {
            ValueAnimator a = ValueAnimator.ofFloat(0f, 1f);
            a.setDuration(ms);
            a.setInterpolator(ease != null ? ease : new LinearInterpolator());
            a.addUpdateListener(an -> step.accept((Float) an.getAnimatedValue()));
            a.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    done.run();
                }
            });
            a.start();
            return a;
        }
    }

    /** 감속 곡선 (EaseOut cubic): 손을 뗀 뒤 칸에 붙는 관성. */
    private static final Interpolator EASE_OUT_CUBIC = t -> {
        float u = 1f - t;
        return 1f - u * u * u;
    };

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

    // ---------------------------------------------------------------- 앱 바

    /**
     * Windows Phone 8의 앱 바 (공식 지침 "App bar for Windows Phone", "Essential graphics ... App bar"):
     * <ul>
     * <li>화면 맨 아래, 폭을 꽉 채운 높이 72px(→ 54dp)의 크롬 색 막대. 불투명이라 화면 내용은 그 위까지만 쓴다.</li>
     * <li>자주 쓰는 동작을 아이콘 버튼으로 최대 네 개, 가운데에 모아 둔다. 버튼은 48px(→ 36dp) 동그라미 안의 아이콘(앱 바가 동그라미를 그린다).</li>
     * <li>오른쪽의 점 세 개를 누르면(또는 막대를 위로 튕기면) 펼쳐져 버튼 아래에 짧은 이름(text hint)이, 그 아래에 메뉴 항목이 보인다.
     *     메뉴 항목은 소문자, 다섯 개까지. 점·바깥·뒤로 가기·항목·버튼을 누르면 접힌다.</li>
     * </ul>
     */
    public static final class AppBar extends LinearLayout {
        private final LinearLayout buttons, menu;
        private final java.util.List<TextView> hints = new java.util.ArrayList<>();
        private final View dots;
        private boolean open;
        private Runnable onOpenChanged;
        private float downY;

        public AppBar(Context c) {
            super(c);
            setOrientation(VERTICAL);
            setBackgroundColor(chrome);
            setClickable(true);   // 막대를 눌러도 아래 화면으로 넘어가지 않는다
            FrameLayout row = new FrameLayout(c);
            buttons = new LinearLayout(c);
            buttons.setGravity(android.view.Gravity.CENTER_HORIZONTAL);
            row.addView(buttons, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT));
            dots = new View(c) {
                private final android.graphics.Paint p = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);

                @Override
                protected void onDraw(android.graphics.Canvas canvas) {
                    // 오른쪽 위의 점 세 개 (4px → 3dp 지름, 간격 8px → 6dp)
                    p.setColor(fg);
                    float r = Ui.dp(getContext(), 1.5f), gap = Ui.dp(getContext(), 6);
                    float cy = Ui.dp(getContext(), 10), right = getWidth() - Ui.dp(getContext(), 18);
                    for (int i = 0; i < 3; i++) canvas.drawCircle(right - i * gap, cy, r, p);
                }
            };
            dots.setContentDescription(L.t("더 보기", "More"));
            dots.setOnClickListener(v -> setOpen(!open));
            row.addView(dots, new FrameLayout.LayoutParams(Ui.dp(c, 64), Ui.dp(c, 40), android.view.Gravity.END | android.view.Gravity.TOP));
            addView(row, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
            row.setMinimumHeight(Ui.dp(c, 54));
            menu = new LinearLayout(c);
            menu.setOrientation(VERTICAL);
            menu.setPadding(0, Ui.dp(c, 4), 0, Ui.dp(c, 12));
            menu.setVisibility(GONE);
            addView(menu, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        }

        /** 아이콘 버튼을 더한다 (최대 네 개). hint는 펼쳤을 때 버튼 아래에 보이는 짧은 이름. */
        public void addButton(View button, String hint) {
            Context c = getContext();
            LinearLayout col = new LinearLayout(c);
            col.setOrientation(VERTICAL);
            col.setGravity(android.view.Gravity.CENTER_HORIZONTAL);
            LayoutParams blp = new LayoutParams(Ui.dp(c, 40), Ui.dp(c, 40));
            blp.topMargin = Ui.dp(c, 7);
            blp.bottomMargin = Ui.dp(c, 7);
            col.addView(button, blp);
            TextView t = new TextView(c);
            t.setText(lower(hint));
            t.setTextColor(fg);
            t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
            t.setTypeface(regular());
            t.setGravity(android.view.Gravity.CENTER_HORIZONTAL);
            t.setSingleLine(true);
            t.setVisibility(GONE);
            col.addView(t, new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));
            hints.add(t);
            buttons.addView(col, new LayoutParams(Ui.dp(c, 76), LayoutParams.WRAP_CONTENT));
        }

        /** 메뉴 항목을 더한다 (소문자로 보인다, 다섯 개까지 권장). 누르면 앱 바를 접고 action을 부른다. */
        public void addMenuItem(String label, Runnable action) {
            Context c = getContext();
            TextView t = new TextView(c);
            t.setText(lower(label));
            t.setTextColor(fg);
            t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
            t.setTypeface(regular());
            t.setGravity(android.view.Gravity.CENTER_VERTICAL);
            t.setMinHeight(Ui.dp(c, 48));
            t.setPadding(Ui.dp(c, 18), 0, Ui.dp(c, 18), 0);
            t.setOnClickListener(v -> {
                setOpen(false);
                action.run();
            });
            tilt(t);
            menu.addView(t, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        }

        /** 펼쳐지거나 접힐 때 (바깥을 누르면 접히도록 부른 쪽이 막을 깔고 걷는 데 쓴다). */
        public void setOnOpenChanged(Runnable r) {
            onOpenChanged = r;
        }

        public boolean isOpen() {
            return open;
        }

        /** 펼치거나 접는다: 버튼 아래의 이름과 메뉴 항목이 막대에서 밀려 올라온다. */
        public void setOpen(boolean o) {
            if (open == o) return;
            open = o;
            for (TextView t : hints) Ui.setVisibleAnimated(t, o);
            if (menu.getChildCount() > 0) Ui.setVisibleAnimated(menu, o);
            if (onOpenChanged != null) onOpenChanged.run();
        }

        /** 막대를 위로 튕기면 펼치고, 펼친 채 아래로 튕기면 접는다. */
        @Override
        public boolean onInterceptTouchEvent(MotionEvent e) {
            if (e.getActionMasked() == MotionEvent.ACTION_DOWN) downY = e.getY();
            if (e.getActionMasked() == MotionEvent.ACTION_MOVE) {
                float d = e.getY() - downY, slop = android.view.ViewConfiguration.get(getContext()).getScaledTouchSlop() * 2;
                if ((d < -slop && !open) || (d > slop && open)) {
                    setOpen(!open);
                    return true;
                }
            }
            return false;
        }

        @Override
        public boolean onTouchEvent(MotionEvent e) {
            return true;
        }
    }

    // ---------------------------------------------------------------- 버튼·스크롤 표시

    /** 테두리 굵기 (dp): PhoneBorderThickness 3px. */
    public static final float BORDER_DP = 3 * PX;

    /**
     * 버튼 글자: SDK의 PhoneButtonBase처럼 PhoneFontFamilySemiBold, PhoneFontSizeMedium (22.667px → 17sp).
     * logicalSp는 설정 검색이 보는 원래 크기 ({@link #text} 참고).
     */
    public static void buttonText(TextView t, float logicalSp) {
        text(t, sp(FONT_MEDIUM), semibold(), logicalSp);
    }

    /**
     * Button 템플릿(PhoneButtonBase)의 바탕: 평소에는 글자 색 3px 테두리의 빈 네모, 누르는 동안 강조색으로 채운다(WP8),
     * 꺼지면 테두리가 PhoneDisabled. 버튼마다 새로 만든다 (상태를 나눠 갖지 않게).
     */
    public static android.graphics.drawable.Drawable buttonBackground(Context c) {
        int stroke = Ui.dp(c, BORDER_DP);
        android.graphics.drawable.GradientDrawable idle = Ui.round(0, 0), off = Ui.round(0, 0);
        idle.setStroke(stroke, fg);
        off.setStroke(stroke, disabled);
        android.graphics.drawable.StateListDrawable s = new android.graphics.drawable.StateListDrawable();
        s.addState(new int[]{-android.R.attr.state_enabled}, off);
        s.addState(new int[]{android.R.attr.state_pressed}, Ui.round(accent, 0));
        s.addState(new int[0], idle);
        return s;
    }

    /** 버튼 글자 색: 평소 글자 색, 누르는 동안 강조색 위의 색(PhoneButtonBasePressedForeground), 꺼지면 PhoneDisabled. */
    public static android.content.res.ColorStateList buttonInk() {
        return new android.content.res.ColorStateList(
                new int[][]{{-android.R.attr.state_enabled}, {android.R.attr.state_pressed}, {}},
                new int[]{disabled, onAccent, fg});
    }

    /** 버튼의 안쪽 여백: 테두리 3px + Padding 10,5,10,6px. */
    public static void buttonPadding(View v) {
        Context c = v.getContext();
        v.setPadding(px(c, 13), px(c, 8), px(c, 13), px(c, 9));
    }

    /**
     * 스크롤 틀의 세로 스크롤 표시. Metro: WP8의 ScrollViewer처럼 끄는 동안만 나타났다가 손을 떼고 곧 사라지는 5px(→ 4dp) 폭의
     * PhoneDisabled 네모 (누를 수는 없다). 보통 모드: 시스템 스크롤 막대 그대로.
     */
    public static void scrollIndicator(View scroll) {
        scroll.setVerticalScrollBarEnabled(true);
        if (!on) return;
        scroll.setScrollbarFadingEnabled(true);
        scroll.setScrollBarDefaultDelayBeforeFade(500);   // ScrollStates의 VisualTransition 0.5초
        if (android.os.Build.VERSION.SDK_INT >= 29) {
            scroll.setVerticalScrollbarThumbDrawable(new android.graphics.drawable.ColorDrawable(disabled));
            scroll.setVerticalScrollbarTrackDrawable(null);
            scroll.setScrollBarSize(px(scroll.getContext(), 5));
        }
    }

    /**
     * Windows Phone의 TextBox (WP8 SDK 템플릿): 평소에는 PhoneTextBoxBrush 네모(다크 #BFFFFFFF → #BFBFBF, 라이트 #26000000 → #D9D9D9),
     * 입력 중에는 PhoneTextBoxEditBackground(다크 흰색, 라이트 투명 = 흰 바탕)에 강조색 3px 테두리(PhoneTextBoxEditBorder).
     * 글자 PhoneTextBoxForeground(다크 검정, 라이트 검정 87%), PhoneFontSizeMediumLarge, 안쪽 여백은 테두리 3px + 1,2px + 2px.
     */
    public static void textBox(android.widget.EditText e) {
        Context c = e.getContext();
        android.graphics.drawable.GradientDrawable idle = Ui.round(dark ? 0xFFBFBFBF : 0xFFD9D9D9, 0);
        android.graphics.drawable.GradientDrawable focus = Ui.round(0xFFFFFFFF, 0);
        focus.setStroke(Ui.dp(c, BORDER_DP), accent);
        android.graphics.drawable.StateListDrawable bg = new android.graphics.drawable.StateListDrawable();
        bg.addState(new int[]{android.R.attr.state_focused}, focus);
        bg.addState(new int[0], idle);
        e.setBackgroundTintList(null);
        e.setBackground(bg);
        e.setTextColor(dark ? 0xFF000000 : 0xDE000000);
        e.setHintTextColor(0xFF666666);
        e.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp(FONT_MEDIUM_LARGE));
        e.setPadding(px(c, 6), px(c, 7), px(c, 6), px(c, 7));
        e.setHighlightColor((accent & 0x00FFFFFF) | 0x66000000);   // 고른 글자 바탕 SelectionBackground = 강조색
        if (android.os.Build.VERSION.SDK_INT >= 29 && e.getTextCursorDrawable() != null) {
            android.graphics.drawable.Drawable caret = e.getTextCursorDrawable().mutate();
            caret.setTint(dark ? 0xFF000000 : 0xDE000000);   // PhoneTextCaretBrush
            e.setTextCursorDrawable(caret);
        }
    }

    // ---------------------------------------------------------------- 체크 상자·라디오

    /**
     * Windows Phone 8의 CheckBox·RadioButton 표시 (CompoundButton·CheckedTextView에 붙인다). WP8 SDK의 기본 템플릿을 따른다:
     * <ul>
     * <li>상자 CheckBackground: 32×32px(→ 24dp), 테두리 PhoneBorderThickness 3px. 라디오는 같은 크기의 동그라미.</li>
     * <li>체크 CheckMark: Path "M0,123 L39,93 L124,164 L256,18 L295,49 L124,240 z"를 23×21px로 늘려(Stretch=Fill) 가운데에.
     *     라디오의 점은 16×16px 동그라미.</li>
     * <li>색 (WP8 ThemeResources): 상자 PhoneRadioCheckBoxBrush 투명, 테두리와 체크는 글자 색(PhoneForeground).</li>
     * <li>누르는 동안: 상자를 PhoneRadioCheckBoxPressedBrush(강조색)로 채우고 체크·점은 PhoneButtonBasePressedForeground(흰색).</li>
     * <li>꺼지면 테두리와 체크가 PhoneDisabled.</li>
     * </ul>
     */
    public static final class Check extends android.graphics.drawable.Drawable {
        private final boolean radio;
        private final int size;
        private final float stroke;
        private final android.graphics.Paint p = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
        private final android.graphics.Path mark = new android.graphics.Path();
        private boolean checked, pressed, enabled = true;

        public Check(Context c, boolean radio) {
            this.radio = radio;
            this.size = px(c, 32);
            this.stroke = Ui.dp(c, BORDER_DP);
        }

        /**
         * 가운데 (cx, cy)에 한 변(지름) 2×half인 상자·동그라미와, 켜졌으면 체크·점을 그린다. Drawable을 붙일 수 없는 곳(대화상자 목록의
         * 표시)도 쓴다. stroke는 테두리 굵기(px), pressed는 누르는 동안(상자를 강조색으로 채운다).
         */
        public static void paint(android.graphics.Canvas c, float cx, float cy, float half, float stroke, boolean radio,
                                 boolean checked, boolean pressed, boolean enabled, android.graphics.Paint p,
                                 android.graphics.Path mark) {
            int ink = enabled ? fg : disabled;
            if (pressed) {
                p.setStyle(android.graphics.Paint.Style.FILL);
                p.setColor(accent);
                if (radio) c.drawCircle(cx, cy, half, p);
                else c.drawRect(cx - half, cy - half, cx + half, cy + half, p);
            }
            // 테두리는 상자 안쪽에 (XAML의 Border·Ellipse Stroke처럼).
            float in = half - stroke / 2f;
            p.setStyle(android.graphics.Paint.Style.STROKE);
            p.setStrokeWidth(stroke);
            p.setColor(ink);
            if (radio) c.drawCircle(cx, cy, in, p);
            else c.drawRect(cx - in, cy - in, cx + in, cy + in, p);
            if (!checked) return;
            p.setStyle(android.graphics.Paint.Style.FILL);
            p.setColor(pressed ? onAccent : ink);
            if (radio) {
                c.drawCircle(cx, cy, half * 0.5f, p);   // 16px 점 / 32px 동그라미
                return;
            }
            // CheckMark (x 0~295, y 18~240)를 23×21px(상자 32px 기준)에 늘려 가운데에.
            float w = half * 2 * 23f / 32f, h = half * 2 * 21f / 32f, l = cx - w / 2f, t = cy - h / 2f;
            float sx = w / 295f, sy = h / 222f;
            mark.reset();
            mark.moveTo(l, t + (123 - 18) * sy);
            mark.lineTo(l + 39 * sx, t + (93 - 18) * sy);
            mark.lineTo(l + 124 * sx, t + (164 - 18) * sy);
            mark.lineTo(l + 256 * sx, t);
            mark.lineTo(l + 295 * sx, t + (49 - 18) * sy);
            mark.lineTo(l + 124 * sx, t + (240 - 18) * sy);
            mark.close();
            c.drawPath(mark, p);
        }

        @Override public int getIntrinsicWidth() { return size; }
        @Override public int getIntrinsicHeight() { return size; }
        @Override public boolean isStateful() { return true; }

        @Override
        protected boolean onStateChange(int[] state) {
            boolean on = false, down = false, usable = false;
            for (int s : state) {
                if (s == android.R.attr.state_checked) on = true;
                if (s == android.R.attr.state_pressed) down = true;
                if (s == android.R.attr.state_enabled) usable = true;
            }
            if (on == checked && down == pressed && usable == enabled) return false;
            checked = on;
            pressed = down && usable;
            enabled = usable;
            invalidateSelf();
            return true;
        }

        @Override
        public void draw(android.graphics.Canvas c) {
            android.graphics.Rect b = getBounds();
            paint(c, b.exactCenterX(), b.exactCenterY(), size / 2f, stroke, radio, checked, pressed, enabled, p, mark);
        }

        @Override public void setAlpha(int alpha) { p.setAlpha(alpha); }
        @Override public void setColorFilter(android.graphics.ColorFilter cf) { p.setColorFilter(cf); }
        @Override public int getOpacity() { return android.graphics.PixelFormat.TRANSLUCENT; }
    }
}
