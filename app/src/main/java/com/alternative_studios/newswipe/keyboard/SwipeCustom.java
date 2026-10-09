package com.alternative_studios.newswipe.keyboard;

import com.alternative_studios.newswipe.Prefs;
import com.alternative_studios.newswipe.hangul.HangulComposer;

/**
 * 스와이프 입력 완전 사용자화. 켜면 쌍자음·겹받침 스와이프 설정은 쓰지 않고,
 * 키마다 위·왼쪽·오른쪽·아래로 밀었을 때 입력할 글자를 직접 정한다.
 * 정하지 않은 방향은 기본 배치(자음 아래 = 쌍자음, 자음 나머지 = 겹받침 기본값,
 * 모음 아래·왼쪽·오른쪽·위 = 이중모음)를 따른다.
 */
public final class SwipeCustom {
    /** 편집 화면에 보이는 방향 순서. */
    public static final int[] DIRS = {Key.SWIPE_UP, Key.SWIPE_DOWN, Key.SWIPE_LEFT, Key.SWIPE_RIGHT};
    public static final String[] LABELS = {"위쪽", "아래쪽", "왼쪽", "오른쪽"};
    private static final String[] NAMES = {"down", "up", "left", "right"};   // Key.SWIPE_* 순서

    private SwipeCustom() {
    }

    public static String dirName(int dir) {
        return NAMES[dir];
    }

    /** 화면에 보이는 방향 이름 (위쪽·아래쪽·왼쪽·오른쪽). */
    public static String label(int dir) {
        for (int i = 0; i < DIRS.length; i++) if (DIRS[i] == dir) return LABELS[i];
        return "";
    }

    /**
     * 고치지 않았을 때의 글자. 없으면 null.
     * newSwipe면 NewSwipe 배열(ㅂ·ㅈ·ㄷ·ㄱ 오른쪽 = ㅍ·ㅊ·ㅌ·ㅋ). 모음은 모든 배열이 같다.
     */
    public static String defaultFor(boolean korean, boolean newSwipe, String label, int dir) {
        if (!korean || label.length() != 1 || !KeyboardLayout.GROUP_KO.equals(KeyboardLayout.groupOf(true, label))) {
            return null;
        }
        char c = label.charAt(0);
        if (HangulComposer.isVowel(c)) return VowelSwipes.defaultFor(c, dir);   // 밀어서 이중모음
        switch (dir) {
            case Key.SWIPE_DOWN: {
                char d = HangulComposer.doubled(c);
                return d == 0 ? null : String.valueOf(d);
            }
            case Key.SWIPE_UP:
                return FinalSwipes.defaultFor(c, FinalSwipes.UP);
            case Key.SWIPE_LEFT: return FinalSwipes.defaultFor(c, FinalSwipes.LEFT);
            default:
                if (newSwipe && KeyboardLayout.nsRight(label) != null) return KeyboardLayout.nsRight(label);
                return FinalSwipes.defaultFor(c, FinalSwipes.RIGHT);
        }
    }

    /** 사용자가 정한 값이 있으면 그것을, 없으면 기본값을 돌려준다. "없음"이면 null. */
    public static String get(Prefs prefs, boolean korean, String label, int dir) {
        String v = prefs == null ? null
                : prefs.swipeCustomOverride(KeyboardLayout.groupOf(korean, label), label, NAMES[dir]);
        if (v == null) {
            return defaultFor(korean, prefs != null && prefs.koreanNewSwipe(), label, dir);
        }
        return v.isEmpty() ? null : v;
    }
}
