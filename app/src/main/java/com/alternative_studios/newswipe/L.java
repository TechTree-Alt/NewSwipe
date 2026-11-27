package com.alternative_studios.newswipe;

import android.content.Context;
import android.os.Build;

import java.util.Locale;

/**
 * 화면에 보이는 글의 언어. 앱 언어(설정 › 언어 › 인터페이스 언어, 시스템을 따르면 시스템 언어)가 한국어면 한국어로,
 * 그 밖의 언어면 영어로 보인다.
 *
 * <p>글은 쓰는 자리에서 두 언어를 나란히 적는다: {@code L.t("설정", "Settings")}. 같은 말은 어디서나 같은 영어로 옮긴다
 * (용어는 저장소의 docs/TERMS.md 참고).
 *
 * <p>Android 13 이상에서는 인터페이스 언어를 시스템의 앱별 언어로 저장하므로 {@link Locale#getDefault()}가 그 언어가 된다.
 * 그보다 낮은 버전에서는 설정값으로 저장하고 {@link #init}이 읽어 둔 값을 쓴다. 언어를 바꾸면 다시 그리는 화면부터 새 언어로
 * 보이므로, 글을 static final 값에 담아 두지 않는다.
 */
public final class L {

    private L() { }

    /** Android 12 이하에서 쓰는 인터페이스 언어 설정 키 ("": 시스템, "ko", "en"). */
    static final String PREF_UI_LANGUAGE = "ui_language";

    /** Android 12 이하에서 고른 언어 (null이면 시스템). */
    private static volatile String override;

    /** 저장된 인터페이스 언어를 읽어 둔다 (화면과 키보드가 만들어질 때 부른다). */
    public static void init(Context c) {
        if (Build.VERSION.SDK_INT >= 33) {
            override = null;
            return;
        }
        String v = new Prefs(c).raw().getString(PREF_UI_LANGUAGE, "");
        override = v.isEmpty() ? null : v;
    }

    /** 고른 인터페이스 언어: "" (시스템), "ko", "en". */
    static String language(Context c) {
        if (Build.VERSION.SDK_INT >= 33) {
            android.app.LocaleManager lm = c.getSystemService(android.app.LocaleManager.class);
            android.os.LocaleList l = lm == null ? null : lm.getApplicationLocales();
            if (l == null || l.isEmpty()) return "";
            return "ko".equals(l.get(0).getLanguage()) ? "ko" : "en";
        }
        return new Prefs(c).raw().getString(PREF_UI_LANGUAGE, "");
    }

    /** 인터페이스 언어를 바꾼다 ("": 시스템을 따름). */
    static void setLanguage(Context c, String tag) {
        if (Build.VERSION.SDK_INT >= 33) {
            android.app.LocaleManager lm = c.getSystemService(android.app.LocaleManager.class);
            if (lm != null) lm.setApplicationLocales(tag.isEmpty()
                    ? android.os.LocaleList.getEmptyLocaleList() : android.os.LocaleList.forLanguageTags(tag));
            return;
        }
        new Prefs(c).raw().edit().putString(PREF_UI_LANGUAGE, tag).apply();
        override = tag.isEmpty() ? null : tag;
    }

    /** 지금 화면 언어가 한국어인지. */
    public static boolean ko() {
        String o = override;
        return "ko".equals(o != null ? o : Locale.getDefault().getLanguage());
    }

    /** 지금 화면 언어의 글: 한국어면 ko, 그 밖에는 en. */
    public static String t(String ko, String en) {
        return ko() ? ko : en;
    }
}
