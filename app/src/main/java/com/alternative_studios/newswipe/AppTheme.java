package com.alternative_studios.newswipe;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Color;
import android.os.Build;

/**
 * 화면 모드(시스템/라이트/다크)와 강조 색(시스템/지정)을 풀어서 실제 색으로 돌려준다.
 * 설정 화면과 키보드가 같은 규칙을 쓴다.
 */
public final class AppTheme {
    public static final int DEFAULT_ACCENT = 0xFF2F62E8;

    public final boolean dark;
    public final int bg, card, text, hint, accent, onAccent;

    private AppTheme(boolean dark, int accent, int onAccent) {
        this.dark = dark;
        this.accent = accent;
        this.onAccent = onAccent;
        bg = dark ? 0xFF121316 : 0xFFF4F5F8;
        card = dark ? 0xFF1E1F23 : 0xFFFFFFFF;
        text = dark ? 0xFFECECEF : 0xFF1D1E21;
        hint = dark ? 0xFF9A9CA3 : 0xFF6B7079;
    }

    public static AppTheme of(Context context) {
        Prefs prefs = new Prefs(context);
        boolean dark = isDark(context, prefs);
        return new AppTheme(dark, accent(context, prefs, dark), onAccent(context, prefs, dark));
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

    static int accent(Context context, Prefs prefs, boolean dark) {
        if (useSystemAccent(prefs)) {
            return context.getColor(dark ? android.R.color.system_accent1_200 : android.R.color.system_accent1_600);
        }
        int c = prefs.accentColor();
        // 기본 파랑은 다크 모드에서 어두운 바탕에 어울리는 밝은 파랑을 쓴다 (예전 모양 유지).
        if (c == DEFAULT_ACCENT && dark) return 0xFF7FA6FF;
        return c;
    }

    static int onAccent(Context context, Prefs prefs, boolean dark) {
        if (useSystemAccent(prefs)) {
            return context.getColor(dark ? android.R.color.system_accent1_800 : android.R.color.system_accent1_0);
        }
        int c = accent(context, prefs, dark);
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

    /** 앱의 강조 색을 따르는 대화상자 빌더 (activity에서 쓴다). 시스템 강조 색 대신 설정의 강조 색이 쓰인다. */
    public static android.app.AlertDialog.Builder dialogBuilder(Context activity) {
        return accentBuilder(dialogContext(activity));
    }

    /** 이미 {@link #dialogContext}로 감싼 컨텍스트로 만드는 {@link #dialogBuilder}. */
    public static android.app.AlertDialog.Builder accentBuilder(Context themed) {
        return new AccentBuilder(themed);
    }

    /**
     * 시스템 대화상자는 기기의 강조 색을 쓰므로, 설정에서 강조 색을 직접 골랐으면 키보드·설정 화면과 색이 달라 보인다.
     * 대화상자가 뜰 때 버튼 글자, 라디오·체크 표시, 입력란의 밑줄과 커서를 앱의 강조 색으로 칠한다.
     */
    private static final class AccentBuilder extends android.app.AlertDialog.Builder {
        private final int accent, hint;

        AccentBuilder(Context themed) {
            super(themed);
            AppTheme t = AppTheme.of(themed);
            accent = t.accent;
            hint = t.hint;
        }

        @Override
        public android.app.AlertDialog create() {
            android.app.AlertDialog d = super.create();
            if (d.getWindow() != null) {
                d.getWindow().getDecorView().addOnAttachStateChangeListener(new android.view.View.OnAttachStateChangeListener() {
                    @Override public void onViewAttachedToWindow(android.view.View v) {
                        v.post(() -> tint(v, accent, hint));   // 내용이 다 붙은 뒤에 칠한다
                    }
                    @Override public void onViewDetachedFromWindow(android.view.View v) { }
                });
            }
            return d;
        }
    }

    private static void tint(android.view.View v, int accent, int hint) {
        int id = v.getId();
        if (v instanceof android.widget.TextView && (id == android.R.id.button1 || id == android.R.id.button2
                || id == android.R.id.button3)) {
            ((android.widget.TextView) v).setTextColor(accent);
        }
        android.content.res.ColorStateList states = new android.content.res.ColorStateList(
                new int[][]{{android.R.attr.state_checked}, {}}, new int[]{accent, hint});
        if (v instanceof android.widget.CheckedTextView) {
            ((android.widget.CheckedTextView) v).setCheckMarkTintList(states);
        } else if (v instanceof android.widget.CompoundButton) {
            ((android.widget.CompoundButton) v).setButtonTintList(states);
        } else if (v instanceof android.widget.EditText) {
            android.widget.EditText e = (android.widget.EditText) v;
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
        return p.themeMode() + "/" + p.accentMode() + "/" + p.accentColor();
    }

    /** 활동의 리소스(night 값)도 고른 화면 모드를 따르도록 기준 설정을 덮어쓴다. attachBaseContext에서 쓴다. */
    public static Context wrap(Context base) {
        Prefs prefs = new Prefs(base);
        String mode = prefs.themeMode();
        if (Prefs.THEME_SYSTEM.equals(mode)) return base;
        Configuration c = new Configuration(base.getResources().getConfiguration());
        int night = Prefs.THEME_DARK.equals(mode) ? Configuration.UI_MODE_NIGHT_YES : Configuration.UI_MODE_NIGHT_NO;
        c.uiMode = (c.uiMode & ~Configuration.UI_MODE_NIGHT_MASK) | night;
        return base.createConfigurationContext(c);
    }
}
