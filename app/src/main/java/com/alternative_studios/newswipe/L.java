package com.alternative_studios.newswipe;

import java.util.Locale;

/**
 * 화면에 보이는 글의 언어. 앱 언어(시스템 언어, Android 13 이상에서는 앱별 언어)가 한국어면 한국어로,
 * 그 밖의 언어면 영어로 보인다.
 *
 * <p>글은 쓰는 자리에서 두 언어를 나란히 적는다: {@code L.t("설정", "Settings")}. 같은 말은 어디서나 같은 영어로 옮긴다
 * (용어는 저장소의 docs/TERMS.md 참고).
 *
 * <p>언어는 부를 때마다 {@link Locale#getDefault()}로 정한다. Android는 앱 리소스(values-ko)와 기기 언어 목록을 맞춰
 * 기본 로케일을 고르므로, 기기 언어가 [일본어, 한국어]처럼 한국어를 두 번째로 두어도 한국어로 보인다. 언어를 바꾸면
 * 다시 그리는 화면부터 새 언어로 보이므로, 글을 static final 값에 담아 두지 않는다.
 */
public final class L {

    private L() { }

    /** 지금 화면 언어가 한국어인지. */
    public static boolean ko() {
        return "ko".equals(Locale.getDefault().getLanguage());
    }

    /** 지금 화면 언어의 글: 한국어면 ko, 그 밖에는 en. */
    public static String t(String ko, String en) {
        return ko() ? ko : en;
    }
}
