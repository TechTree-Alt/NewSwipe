package com.alternative_studios.newswipe.keyboard;

import com.alternative_studios.newswipe.Prefs;
import com.alternative_studios.newswipe.hangul.HangulComposer;

/**
 * 밀어서 글자 입력: 키마다 위·왼쪽·오른쪽·아래로 밀었을 때 입력할 글자를 정한다.
 * 정하지 않은 방향은 기본 배치(자음 아래 = 쌍자음, 자음 나머지 = 겹받침 기본값, NewSwipe 단모음의 ㅂ·ㅈ·ㄷ·ㄱ 오른쪽 = 거센소리,
 * 모음 아래·왼쪽·오른쪽·위 = 이중모음, 온점 키 위 = 쉼표)를 따른다.
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
        if (".".equals(label) && dir == Key.SWIPE_UP) return ",";   // 온점 키를 위로 밀어 쉼표
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

    /** 영어 글자 키의 아래로 밀기가 대문자로 고정되어 있는지 ('영어 자판 아래로 밀어 대문자' 스위치). */
    public static boolean englishUpperSwipe(Prefs prefs, boolean korean, String label, int dir) {
        return prefs != null && !korean && dir == Key.SWIPE_DOWN && prefs.englishSwipeUpper() && label != null
                && label.length() == 1 && Character.isLetter(label.charAt(0))
                && KeyboardLayout.GROUP_EN.equals(KeyboardLayout.groupOf(false, label));
    }

    /**
     * 한글 자음·모음 키의 아래로 밀기가 기본 배치(쌍자음·ㅣ계 이중모음)로 고정되어 있는지: '쌍자음 입력'·'이중모음 입력'이
     * '아래로 밀기'나 '둘 다'이고 그 키에 기본 글자(ㄱ→ㄲ, ㅏ→ㅑ …)가 있을 때. 기본 글자가 없는 키(ㅁ·ㅇ·ㅑ …)는 바꿀 수 있다.
     */
    public static boolean hangulDownLocked(Prefs prefs, boolean korean, String label, int dir) {
        if (prefs == null || !korean || dir != Key.SWIPE_DOWN || label == null || label.length() != 1
                || !KeyboardLayout.GROUP_KO.equals(KeyboardLayout.groupOf(true, label))) return false;
        boolean vowel = HangulComposer.isVowel(label.charAt(0));
        if (!(vowel ? prefs.swipeDownVowel() : prefs.swipeDownConsonant())) return false;
        return defaultFor(true, prefs.koreanNewSwipe(), label, dir) != null;
    }

    /** 사용자가 정한 값이 있으면 그것을, 없으면 기본값을 돌려준다. "없음"이면 null. */
    public static String get(Prefs prefs, boolean korean, String label, int dir) {
        if (englishUpperSwipe(prefs, korean, label, dir)) return label.toUpperCase(java.util.Locale.ROOT);
        if (hangulDownLocked(prefs, korean, label, dir)) {   // 쌍자음·이중모음을 아래로 밀기로 입력하면 기본 배치로 고정
            return defaultFor(true, prefs.koreanNewSwipe(), label, dir);
        }
        String v = prefs == null ? null
                : prefs.swipeCustomOverride(KeyboardLayout.groupOf(korean, label), label, NAMES[dir]);
        if (v == null) {
            String def = defaultFor(korean, prefs != null && prefs.koreanNewSwipe(), label, dir);
            // 쌍자음·이중모음 입력 방식에서 '연속 탭'만 골랐으면 기본 배치의 아래로 밀기(쌍자음·ㅣ계 이중모음)는 쓰지 않는다.
            // (밀기를 쓰는 방식이면 위에서 이미 기본 배치로 고정했다.)
            if (def != null && prefs != null && korean && dir == Key.SWIPE_DOWN && label.length() == 1
                    && KeyboardLayout.GROUP_KO.equals(KeyboardLayout.groupOf(true, label))) {
                boolean vowel = HangulComposer.isVowel(label.charAt(0));
                if (vowel ? !prefs.swipeDownVowel() : !prefs.swipeDownConsonant()) return null;
            }
            return def;
        }
        return v.isEmpty() ? null : v;
    }
}
