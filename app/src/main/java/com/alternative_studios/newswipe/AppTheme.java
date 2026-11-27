package com.alternative_studios.newswipe;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Color;
import android.os.Build;

import com.alternative_studios.newswipe.ui.Metro;
import com.alternative_studios.newswipe.ui.NeuUi;
import com.alternative_studios.newswipe.ui.Ui;

/**
 * 화면 모드(시스템/라이트/다크)와 강조 색(시스템/지정)을 풀어서 실제 색으로 돌려준다.
 * 설정 화면과 키보드가 같은 규칙을 쓴다.
 */
public final class AppTheme {
    public static final int DEFAULT_ACCENT = 0xFF2F62E8;

    public final boolean dark;
    /** Metro UI 모드 (실험적): 순수한 검정·흰색 바탕, 크롬 색 입력란, 옅은 회색 글자 (ui.Metro). */
    public final boolean metro;
    /** Neumorphism 모드 (Metro가 아닐 때만): 바탕과 카드가 같은 색이고, 그림자로 솟아오르거나 들어간 모양을 그린다 (ui.NeuUi). */
    public final boolean neu;
    public final int bg, card, text, hint, accent, onAccent;
    /**
     * 키보드의 강조색 키에 쓰는 색. 설정 화면의 {@link #accent}는 바탕에 묻히지 않게 보정될 수 있지만,
     * 키보드는 사용자가 고른 색을 그대로 쓴다 (강조색 키는 면을 칠하므로 바탕과 비슷해도 키 모양으로 구분된다).
     */
    public final int keyAccent, keyOnAccent;

    private AppTheme(boolean dark, boolean metro, boolean neu, int accent, int onAccent, int keyAccent, int keyOnAccent) {
        this.dark = dark;
        this.metro = metro;
        this.neu = neu;
        this.accent = accent;
        this.onAccent = onAccent;
        this.keyAccent = keyAccent;
        this.keyOnAccent = keyOnAccent;
        if (metro) {
            bg = dark ? Metro.DARK_BG : Metro.LIGHT_BG;
            card = dark ? Metro.DARK_CHROME : Metro.LIGHT_CHROME;
            text = dark ? Metro.DARK_FG : Metro.LIGHT_FG;
            hint = Metro.SUBTLE;
            return;
        }
        if (neu) {
            // 바탕과 면이 같은 색 (면은 그림자로 드러난다). 글자는 보통 모드와 같다.
            bg = card = dark ? NeuUi.DARK_SURFACE : NeuUi.LIGHT_SURFACE;
            text = dark ? 0xFFECECEF : 0xFF1D1E21;
            hint = dark ? 0xFF9A9CA3 : 0xFF656A73;
            return;
        }
        bg = dark ? 0xFF121316 : 0xFFF4F5F8;
        card = dark ? 0xFF1E1F23 : 0xFFFFFFFF;
        text = dark ? 0xFFECECEF : 0xFF1D1E21;
        hint = dark ? 0xFF9A9CA3 : 0xFF6B7079;
    }

    public static AppTheme of(Context context) {
        Prefs prefs = new Prefs(context);
        boolean dark = isDark(context, prefs);
        boolean metro = prefs.metro();
        int key = keyAccent(context, prefs, dark);
        int shown = accent(context, prefs, dark);
        int onShown = metro ? metroInk(shown, onAccent(context, prefs, dark, shown)) : onAccent(context, prefs, dark, shown);
        // 설정 화면과 키보드가 함께 쓰는 Metro 상태·색 (Ui.round 등 Context 없이 부르는 곳이 읽는다).
        Metro.set(metro, dark, shown, onShown);
        boolean neu = !metro && prefs.neumorphism();
        NeuUi.set(neu, dark);
        int onKey = onAccent(context, prefs, dark, key);
        return new AppTheme(dark, metro, neu, shown, onShown, key, metro ? metroInk(key, onKey) : onKey);
    }

    /**
     * Metro: Windows Phone은 강조색 타일 위의 글자를 늘 흰색으로 썼다. 노랑·연두처럼 아주 밝은 색(상대 휘도 0.5 이상)만
     * 원래 고른 짙은 글자를 둔다.
     */
    private static int metroInk(int accent, int fallback) {
        return luminance(accent) < 0.5 ? 0xFFFFFFFF : fallback;
    }

    /** 설정 화면의 강조 색이 시인성 때문에 고른 색(키보드에 쓰는 색)과 다르게 보정되었는지. */
    public boolean accentAdjusted() {
        return (accent & 0xFFFFFF) != (keyAccent & 0xFFFFFF);
    }

    public static boolean isDark(Context context, Prefs prefs) {
        switch (prefs.themeMode()) {
            case Prefs.THEME_LIGHT:
                return false;
            case Prefs.THEME_DARK:
                return true;
            default:
                return (context.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK)
                        == Configuration.UI_MODE_NIGHT_YES;
        }
    }

    private static boolean useSystemAccent(Prefs prefs) {
        return Prefs.ACCENT_SYSTEM.equals(prefs.accentMode()) && Build.VERSION.SDK_INT >= 31;
    }

    /** 보정하지 않은 강조 색 (시스템 색, 기본 파랑의 다크 모드 짝, 또는 고른 색 그대로). 키보드가 쓴다. */
    static int keyAccent(Context context, Prefs prefs, boolean dark) {
        if (useSystemAccent(prefs)) {
            return context.getColor(dark ? android.R.color.system_accent1_200 : android.R.color.system_accent1_600);
        }
        int c = prefs.accentColor();
        // 기본 파랑은 다크 모드에서 어두운 바탕에 어울리는 밝은 파랑을 쓴다 (예전 모양 유지).
        if (c == DEFAULT_ACCENT && dark) return 0xFF7FA6FF;
        return c;
    }

    /** 설정 화면에 쓰는 강조 색. 고른 색이 바탕에 묻히면 {@link #readable}로 보정한다. */
    static int accent(Context context, Prefs prefs, boolean dark) {
        int c = keyAccent(context, prefs, dark);
        if (useSystemAccent(prefs) || c == 0xFF7FA6FF) return c;
        return readable(c, dark);
    }

    /**
     * 강조 색과 바탕·카드의 대비(WCAG 비율)가 이보다 낮을 때만 색을 고친다. 다크 모드의 검정·라이트 모드의 흰색처럼
     * 거의 안 보이는 경우만 잡고, 주황·노랑처럼 대비는 조금 낮아도 잘 보이는 색은 고른 그대로 둔다.
     */
    static final double FIX_BELOW = 1.5;
    /** 고칠 때는 이 대비까지 올려서 확실히 보이게 한다. */
    static final double MIN_CONTRAST = 3.0;

    /**
     * 지정한 강조 색이 바탕에 묻히면(대비가 {@link #FIX_BELOW} 미만: 다크 모드의 검정, 라이트 모드의 흰색 등) 바탕에서 멀어지는 쪽으로
     * (다크는 흰색, 라이트는 검정 쪽으로) 조금씩 섞어 대비가 {@link #MIN_CONTRAST}를 넘는 가장 가까운 색을 쓴다.
     * 원래 색의 색조는 최대한 남고, 이미 잘 보이는 색은 그대로 둔다. 고른 색 자체(설정값)는 바꾸지 않는다.
     */
    static int readable(int color, boolean dark) {
        int bg = dark ? 0xFF121316 : 0xFFF4F5F8;
        int card = dark ? 0xFF1E1F23 : 0xFFFFFFFF;
        int target = dark ? 0xFFFFFFFF : 0xFF000000;
        color |= 0xFF000000;
        if (contrast(color, bg) >= FIX_BELOW && contrast(color, card) >= FIX_BELOW) return color;
        for (int step = 0; step <= 20; step++) {
            int c = blend(color, target, step / 20f);
            if (contrast(c, bg) >= MIN_CONTRAST && contrast(c, card) >= MIN_CONTRAST) return c;
        }
        return target;
    }

    private static int blend(int a, int b, float t) {
        int r = Math.round(((a >> 16) & 0xFF) * (1 - t) + ((b >> 16) & 0xFF) * t);
        int g = Math.round(((a >> 8) & 0xFF) * (1 - t) + ((b >> 8) & 0xFF) * t);
        int bl = Math.round((a & 0xFF) * (1 - t) + (b & 0xFF) * t);
        return 0xFF000000 | (r << 16) | (g << 8) | bl;
    }

    /** WCAG 상대 휘도 (android.graphics.Color.luminance와 같은 식, JVM 테스트에서도 쓸 수 있게 직접 계산). */
    static double luminance(int c) {
        return 0.2126 * channel((c >> 16) & 0xFF) + 0.7152 * channel((c >> 8) & 0xFF) + 0.0722 * channel(c & 0xFF);
    }

    private static double channel(int v) {
        double s = v / 255.0;
        return s <= 0.04045 ? s / 12.92 : Math.pow((s + 0.055) / 1.055, 2.4);
    }

    static double contrast(int a, int b) {
        double la = luminance(a), lb = luminance(b);
        return (Math.max(la, lb) + 0.05) / (Math.min(la, lb) + 0.05);
    }

    /** 강조 색 {@code c} 위에 얹는 글자·아이콘 색. */
    static int onAccent(Context context, Prefs prefs, boolean dark, int c) {
        if (useSystemAccent(prefs)) {
            return context.getColor(dark ? android.R.color.system_accent1_800 : android.R.color.system_accent1_0);
        }
        if (c == 0xFF7FA6FF) return 0xFF0B1A3A;   // 기본 파랑의 다크 모드 짝
        // 흰 글자와 어두운 글자 중 대비가 더 큰 쪽을 고른다.
        double l = Color.luminance(c);
        double whiteContrast = 1.05 / (l + 0.05);
        double darkContrast = (l + 0.05) / (Color.luminance(0xFF1D1E21) + 0.05);
        return whiteContrast >= darkContrast ? 0xFFFFFFFF : 0xFF1D1E21;
    }

    /** 키보드와 같은 모양(둥근 모서리의 시스템 대화상자)으로 보이게 하는 대화상자용 컨텍스트. */
    public static Context dialogContext(Context context) {
        boolean dark = isDark(context, new Prefs(context));
        return new android.view.ContextThemeWrapper(context, dark
                ? android.R.style.Theme_DeviceDefault_Dialog_Alert
                : android.R.style.Theme_DeviceDefault_Light_Dialog_Alert);
    }

    /**
     * 앱의 대화상자 빌더 (activity에서 쓴다). 시스템 AlertDialog 대신 앱이 직접 그리는 {@link AppDialog}라서 설정의 강조 색을 따르고,
     * Metro UI 모드에서는 Windows Phone의 메시지 상자 모양이 된다. 쓰는 법은 AlertDialog.Builder와 같다.
     */
    public static AppDialog.Builder dialogBuilder(Context activity) {
        return accentBuilder(dialogContext(activity));
    }

    /** 이미 {@link #dialogContext}로 감싼 컨텍스트로 만드는 {@link #dialogBuilder}. */
    public static AppDialog.Builder accentBuilder(Context themed) {
        return new AppDialog.Builder(themed);
    }

    /** 대화상자 안의 입력란 밑줄·커서, 라디오·체크 표시를 앱의 강조 색으로 칠한다 (AppDialog가 뜰 때 부른다). */
    static void tintViews(android.view.View v, int accent, int hint) {
        tint(v, accent, hint);
    }

    /** 시스템 목록 칸의 표시가 라디오(한 개 고르기)인지: 목록이 한 개만 고르게 되어 있으면 라디오다. */
    private static boolean isRadioMark(android.widget.CheckedTextView v) {
        android.view.ViewParent p = v.getParent();
        return p instanceof android.widget.AbsListView
                && ((android.widget.AbsListView) p).getChoiceMode() == android.widget.AbsListView.CHOICE_MODE_SINGLE;
    }

    private static void tint(android.view.View v, int accent, int hint) {
        int id = v.getId();
        if (v instanceof android.widget.TextView && (id == android.R.id.button1 || id == android.R.id.button2
                || id == android.R.id.button3)) {
            ((android.widget.TextView) v).setTextColor(accent);
        }
        android.content.res.ColorStateList states = new android.content.res.ColorStateList(
                new int[][]{{android.R.attr.state_checked}, {}}, new int[]{accent, hint});
        if (NeuUi.on && v instanceof android.widget.CheckedTextView) {
            android.widget.CheckedTextView ct = (android.widget.CheckedTextView) v;
            android.graphics.drawable.Drawable was = ct.getCheckMarkDrawable();
            if (was != null && !(was instanceof NeuUi.Check)) {   // 표시가 없는 칸은 그대로, 이미 바꾼 칸은 다시 만들지 않는다
                ct.setCheckMarkTintList(null);
                ct.setCheckMarkDrawable(new NeuUi.Check(v.getContext(), isRadioMark(ct), accent));
            }
        } else if (NeuUi.on && (v instanceof android.widget.CheckBox || v instanceof android.widget.RadioButton)) {
            // Neumorphism: 시스템 체크박스·라디오 대신 안으로 들어간 홈 + 강조색 표시
            android.widget.CompoundButton cb = (android.widget.CompoundButton) v;
            cb.setButtonTintList(null);
            if (!(cb.getButtonDrawable() instanceof NeuUi.Check)) cb.setButtonDrawable(new NeuUi.Check(v.getContext(), v instanceof android.widget.RadioButton, accent));
        } else if (Metro.on && v instanceof android.widget.CheckedTextView) {
            android.widget.CheckedTextView ct = (android.widget.CheckedTextView) v;
            android.graphics.drawable.Drawable was = ct.getCheckMarkDrawable();
            if (was != null && !(was instanceof Metro.Check)) {
                ct.setCheckMarkTintList(null);
                ct.setCheckMarkDrawable(new Metro.Check(v.getContext(), isRadioMark(ct)));
            }
        } else if (Metro.on && (v instanceof android.widget.CheckBox || v instanceof android.widget.RadioButton)) {
            // Metro: 시스템 체크박스·라디오 대신 Windows Phone의 테두리 상자·동그라미
            android.widget.CompoundButton cb = (android.widget.CompoundButton) v;
            cb.setButtonTintList(null);
            if (!(cb.getButtonDrawable() instanceof Metro.Check)) cb.setButtonDrawable(new Metro.Check(v.getContext(), v instanceof android.widget.RadioButton));
            cb.setBackground(null);   // 시스템 물결 없이 틸트로만 반응한다
            // 상자와 글 사이: 템플릿의 ContentContainer Margin 12px. CompoundButton은 상자를 맨 앞에 두고 글을 안쪽 여백 뒤에 쓴다.
            int gap = Metro.px(v.getContext(), 12);
            if (cb.getPaddingStart() < gap) cb.setPaddingRelative(gap, cb.getPaddingTop(), cb.getPaddingEnd(), cb.getPaddingBottom());
            Metro.tilt(cb);
        } else if (v instanceof android.widget.CheckedTextView) {
            ((android.widget.CheckedTextView) v).setCheckMarkTintList(states);
        } else if (v instanceof android.widget.CompoundButton) {
            ((android.widget.CompoundButton) v).setButtonTintList(states);
        } else if (v instanceof android.widget.EditText) {
            android.widget.EditText e = (android.widget.EditText) v;
            if (Metro.on && !(e.getBackground() instanceof android.graphics.drawable.GradientDrawable)) {
                Metro.textBox(e);   // Windows Phone의 TextBox (부른 쪽이 직접 칠한 바탕은 그대로 둔다)
            } else {
                e.setBackgroundTintList(android.content.res.ColorStateList.valueOf(accent));
                e.setHighlightColor((accent & 0x00FFFFFF) | 0x55000000);
                if (Build.VERSION.SDK_INT >= 29) {
                    android.graphics.drawable.Drawable cursor = e.getTextCursorDrawable();
                    if (cursor != null) {
                        cursor = cursor.mutate();
                        cursor.setTint(accent);
                        e.setTextCursorDrawable(cursor);
                    }
                }
            }
        }
        if (v instanceof android.view.ViewGroup) {
            android.view.ViewGroup g = (android.view.ViewGroup) v;
            if (g instanceof android.widget.AbsListView) {
                // 목록의 칸은 나중에 만들어지거나 다시 쓰이므로, 붙을 때마다 칠한다.
                g.setOnHierarchyChangeListener(new android.view.ViewGroup.OnHierarchyChangeListener() {
                    @Override public void onChildViewAdded(android.view.View parent, android.view.View child) {
                        tint(child, accent, hint);
                    }
                    @Override public void onChildViewRemoved(android.view.View parent, android.view.View child) { }
                });
            }
            for (int i = 0; i < g.getChildCount(); i++) tint(g.getChildAt(i), accent, hint);
        }
    }

    /** 활동이 시작할 때 기준 설정이 바뀌었는지 비교하는 값. */
    public static String signature(Context context) {
        Prefs p = new Prefs(context);
        return p.themeMode() + "/" + p.accentMode() + "/" + p.accentColor() + "/" + p.metro() + "/" + p.neumorphism()
                + "/" + L.ko();   // 인터페이스 언어가 바뀌어도 뒤에 쌓인 화면을 다시 그린다
    }

    /** 활동의 리소스(night 값)도 고른 화면 모드를 따르도록 기준 설정을 덮어쓴다. attachBaseContext에서 쓴다. */
    public static Context wrap(Context base) {
        L.init(base);   // 인터페이스 언어 (Android 12 이하)
        Prefs prefs = new Prefs(base);
        String mode = prefs.themeMode();
        if (Prefs.THEME_SYSTEM.equals(mode)) return base;
        Configuration c = new Configuration(base.getResources().getConfiguration());
        int night = Prefs.THEME_DARK.equals(mode) ? Configuration.UI_MODE_NIGHT_YES : Configuration.UI_MODE_NIGHT_NO;
        c.uiMode = (c.uiMode & ~Configuration.UI_MODE_NIGHT_MASK) | night;
        return base.createConfigurationContext(c);
    }
}
