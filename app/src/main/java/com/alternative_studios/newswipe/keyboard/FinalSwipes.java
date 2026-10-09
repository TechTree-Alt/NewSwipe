package com.alternative_studios.newswipe.keyboard;

import com.alternative_studios.newswipe.Prefs;
import com.alternative_studios.newswipe.hangul.HangulComposer;

/**
 * 겹받침 스와이프 배치. 겹받침의 앞 자음 키를 위·왼쪽·오른쪽으로 밀면 겹받침이 입력된다.
 * (아래쪽은 쌍자음 스와이프가 쓴다.) 설정에서 키·방향마다 바꿀 수 있다.
 */
public final class FinalSwipes {
    public static final int UP = 0, LEFT = 1, RIGHT = 2;
    /** 설정 이름에 쓰는 방향 이름. */
    public static final String[] DIR_NAMES = {"up", "left", "right"};
    /** 화면에 보이는 방향 이름. */
    public static final String[] DIR_LABELS = {"위쪽", "왼쪽", "오른쪽"};
    /** 겹받침의 앞 자음이 되는 키들. */
    public static final String SOURCES = "ㄱㄴㄹㅂ";

    private FinalSwipes() {
    }

    /** 기본 배치. 없으면 null. */
    public static String defaultFor(char consonant, int dir) {
        switch (consonant) {
            case 'ㄱ': return dir == RIGHT ? "ㄳ" : null;
            case 'ㄴ': return dir == UP ? "ㄵ" : dir == RIGHT ? "ㄶ" : null;
            case 'ㄹ': return dir == LEFT ? "ㄻ" : dir == UP ? "ㄺ" : dir == RIGHT ? "ㅀ" : null;
            case 'ㅂ': return dir == RIGHT ? "ㅄ" : null;
            default: return null;
        }
    }

    /** 이 자음 키에 놓을 수 있는 겹받침들. */
    public static String options(char consonant) {
        return HangulComposer.compoundFinalsStartingWith(consonant);
    }

    /** 사용자가 고른 값이 있으면 그것을, 없으면 기본 배치를 돌려준다. "없음"이거나 쓸 수 없는 값이면 null. */
    public static String get(Prefs prefs, char consonant, int dir) {
        String v = prefs == null ? null : prefs.swipeFinalOverride(consonant, DIR_NAMES[dir]);
        if (v == null) return defaultFor(consonant, dir);
        // 가져온 설정 등에서 엉뚱한 값이 들어와도 그 자음의 겹받침이 아니면 무시한다.
        return v.length() == 1 && options(consonant).indexOf(v.charAt(0)) >= 0 ? v : null;
    }
}
