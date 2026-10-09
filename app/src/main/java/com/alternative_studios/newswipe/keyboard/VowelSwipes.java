package com.alternative_studios.newswipe.keyboard;

import com.alternative_studios.newswipe.Prefs;
import com.alternative_studios.newswipe.hangul.HangulComposer;

/**
 * 모음 키를 밀어서 입력하는 이중모음.
 * <ul>
 *   <li>아래로: ㅣ계 이중모음 (ㅗ→ㅛ, ㅓ→ㅕ, ㅏ→ㅑ, ㅜ→ㅠ, ㅐ→ㅒ, ㅔ→ㅖ). '밀어서 ㅣ계 이중모음'을 따른다.</li>
 *   <li>위·왼쪽·오른쪽: 조합형 이중모음 (모든 한글 배열, '밀어서 조합형 이중모음'을 따른다).
 *       기본은 ㅜ 왼쪽·위·오른쪽 = ㅝ·ㅟ·ㅞ, ㅗ 왼쪽·위·오른쪽 = ㅘ·ㅚ·ㅙ, ㅡ 위 = ㅢ이고,
 *       '밀어서 조합형 이중모음 입력 편집'에서 키·방향마다 그 모음이 들어 있는 겹모음으로 바꿀 수 있다.</li>
 * </ul>
 * 겹모음은 조합기에 그대로 한 글자로 들어가므로 앞 모음을 먼저 치지 않아도 된다 (초성 뒤에서 ㅗ← = '와').
 */
public final class VowelSwipes {
    /** 조합형 이중모음을 밀어서 입력할 수 있는 모음 키들 (편집 화면에 보이는 순서). */
    public static final String SOURCES = "ㅜㅗㅡㅓㅏㅣㅔㅐ";
    /** SOURCES의 각 키에 놓을 수 있는 조합형 이중모음: 그 모음이 들어 있는 것. */
    private static final String[] OPTIONS_BY_KEY = {
            "ㅝㅞㅟ",   // ㅜ
            "ㅘㅙㅚ",   // ㅗ
            "ㅢ",       // ㅡ
            "ㅝ",       // ㅓ
            "ㅘ",       // ㅏ
            "ㅚㅟㅢ",   // ㅣ
            "ㅞ",       // ㅔ
            "ㅙ",       // ㅐ
    };

    private VowelSwipes() {
    }

    /** 아래로 밀 때 입력되는 ㅣ계 이중모음 (ㅏ→ㅑ, ㅗ→ㅛ 등). 없으면 null. */
    public static String iotizedFor(char vowel) {
        if (!HangulComposer.isVowel(vowel)) return null;
        char d = HangulComposer.doubled(vowel);
        return d == 0 || !isIotized(String.valueOf(d)) ? null : String.valueOf(d);
    }

    /** 조합형 이중모음의 기본 배치: ㅜ 왼쪽·위·오른쪽 = ㅝ·ㅟ·ㅞ, ㅗ = ㅘ·ㅚ·ㅙ, ㅡ 위 = ㅢ. 없으면 null. */
    public static String compoundDefault(char vowel, int dir) {
        switch (dir) {
            case Key.SWIPE_LEFT: return vowel == 'ㅜ' ? "ㅝ" : vowel == 'ㅗ' ? "ㅘ" : null;
            case Key.SWIPE_UP: return vowel == 'ㅜ' ? "ㅟ" : vowel == 'ㅗ' ? "ㅚ" : vowel == 'ㅡ' ? "ㅢ" : null;
            case Key.SWIPE_RIGHT: return vowel == 'ㅜ' ? "ㅞ" : vowel == 'ㅗ' ? "ㅙ" : null;
            default: return null;   // 아래쪽은 ㅣ계 이중모음 자리
        }
    }

    /**
     * vowel 키를 dir(위·왼쪽·오른쪽)로 밀 때의 조합형 이중모음: 편집에서 고른 값, 고른 적이 없으면 기본 배치.
     * "없음"을 골랐거나 그 키에 놓을 수 없는 값(가져온 설정 등)이면 null.
     */
    public static String compoundFor(Prefs prefs, char vowel, int dir) {
        if (dir == Key.SWIPE_DOWN || !HangulComposer.isVowel(vowel)) return null;
        String stored = prefs == null ? null : prefs.swipeVowelOverride(vowel, SwipeCustom.dirName(dir));
        if (stored == null) return compoundDefault(vowel, dir);
        return stored.length() == 1 && optionsFor(vowel).indexOf(stored.charAt(0)) >= 0 ? stored : null;
    }

    /** 이 모음 키에 놓을 수 있는 조합형 이중모음들. 없으면 빈 문자열. */
    public static String optionsFor(char vowel) {
        int i = SOURCES.indexOf(vowel);
        return i < 0 ? "" : OPTIONS_BY_KEY[i];
    }

    /** ㅣ계 이중모음 (나머지 이중모음은 두 모음을 합친 조합형). */
    private static final String IOTIZED = "ㅑㅕㅛㅠㅒㅖ";

    public static boolean isIotized(String vowel) {
        return vowel != null && vowel.length() == 1 && IOTIZED.indexOf(vowel.charAt(0)) >= 0;
    }

    /**
     * vowel 키를 dir(Key.SWIPE_*)로 밀었을 때 입력되는 기본 이중모음 (밀어서 글자 입력 완전 사용자화의 기본값). 없으면 null.
     * 아래 = ㅣ계 이중모음, 위·왼쪽·오른쪽 = 조합형 이중모음 기본 배치 (모든 한글 배열 공통).
     */
    public static String defaultFor(char vowel, int dir) {
        return dir == Key.SWIPE_DOWN ? iotizedFor(vowel) : compoundDefault(vowel, dir);
    }
}
