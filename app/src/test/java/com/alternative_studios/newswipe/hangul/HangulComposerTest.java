package com.alternative_studios.newswipe.hangul;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class HangulComposerTest {

    /**
     * 키 입력을 흉내 낸다. 자모는 탭, '↓' 다음 자모는 아래로 스와이프, '<'는 백스페이스.
     * 반환값은 화면에 보이는 전체 문자열(확정 + 조합 중).
     */
    private static String type(HangulComposer h, String keys) {
        StringBuilder out = new StringBuilder();
        boolean swipe = false;
        for (char c : keys.toCharArray()) {
            if (c == '↓') {
                swipe = true;
                continue;
            }
            if (c == '<') {
                out.append(h.takeCommit());
                if (!h.backspace() && out.length() > 0) out.setLength(out.length() - 1);
            } else if (swipe) {
                h.input(HangulComposer.doubled(c), false);
            } else {
                h.input(c, true);
            }
            swipe = false;
            out.append(h.takeCommit());
        }
        return out + h.getComposing();
    }

    private static String type(String keys) {
        return type(new HangulComposer(), keys);
    }

    @Test
    public void basicSyllables() {
        assertEquals("한글", type("ㅎㅏㄴㄱㅡㄹ"));
        assertEquals("안녕하세요", type("ㅇㅏㄴㄴ↓ㅓㅇㅎㅏㅅㅔㅇ↓ㅗ"));
        assertEquals("닭이", type("ㄷㅏㄹㄱㅇㅣ"));
        assertEquals("달가", type("ㄷㅏㄹㄱㅏ"));
        assertEquals("없어", type("ㅇㅓㅂㅅㅇㅓ"));
        assertEquals("왜", type("ㅇㅗㅐ"));
        assertEquals("의사", type("ㅇㅡㅣㅅㅏ"));
        assertEquals("뭐해", type("ㅁㅜㅓㅎㅐ"));
    }

    @Test
    public void consecutiveConsonantTapsNeverDouble() {
        // 단모음 키보드의 '하꾜' 문제: 자음 연속 탭은 쌍자음이 되지 않아야 한다.
        assertEquals("학교", type("ㅎㅏㄱㄱ↓ㅗ"));
        assertEquals("박사", type("ㅂㅏㄱㅅㅏ"));
        assertEquals("ㅋㅋㅋ", type("ㅋㅋㅋ"));
        assertEquals("ㄱㄱ", type("ㄱㄱ"));
    }

    @Test
    public void swipeDownGivesDoubleConsonantsAndVowels() {
        assertEquals("하꾜", type("ㅎㅏ↓ㄱ↓ㅗ"));
        assertEquals("빵", type("↓ㅂㅏㅇ"));
        assertEquals("있다", type("ㅇㅣ↓ㅅㄷㅏ"));
        assertEquals("떡볶이", type("↓ㄷㅓㄱㅂㅗ↓ㄱㅇㅣ"));
        assertEquals("짜증", type("↓ㅈㅏㅈㅡㅇ"));
        assertEquals("얘기", type("ㅇ↓ㅐㄱㅣ"));
        assertEquals("예", type("ㅇ↓ㅔ"));
        assertEquals("유", type("ㅇ↓ㅜ"));
        // ㄸ·ㅃ·ㅉ는 받침이 될 수 없으므로 다음 글자 초성으로 간다.
        assertEquals("아ㄸ", type("ㅇㅏ↓ㄷ"));
    }

    /**
     * 시간을 넣어 키 입력을 흉내 낸다. 자모 앞의 '!'는 직전 탭 바로 뒤(60ms)에 빠르게 친 것,
     * 표시가 없으면 보통 속도(300ms 뒤)로 친 것이다. '↓', '<'는 type()과 같다.
     */
    private static String typeTimed(HangulComposer h, String keys) {
        StringBuilder out = new StringBuilder();
        boolean swipe = false, fast = false;
        long time = 1000;
        for (char c : keys.toCharArray()) {
            if (c == '↓') {
                swipe = true;
                continue;
            }
            if (c == '!') {
                fast = true;
                continue;
            }
            time += fast ? 60 : 300;
            if (c == '<') {
                out.append(h.takeCommit());
                if (!h.backspace() && out.length() > 0) out.setLength(out.length() - 1);
            } else if (swipe) {
                h.input(HangulComposer.doubled(c), false, time);
            } else {
                h.input(c, true, time);
            }
            swipe = fast = false;
            out.append(h.takeCommit());
        }
        return out + h.getComposing();
    }

    private static HangulComposer doubleTapConsonant(int windowMs) {
        HangulComposer h = new HangulComposer();
        h.setDoubleTapConsonant(true, windowMs);
        return h;
    }

    @Test
    public void doubleTapConsonantWithinWindow() {
        assertEquals("까", typeTimed(doubleTapConsonant(200), "ㄱ!ㄱㅏ"));
        assertEquals("하꾜", typeTimed(doubleTapConsonant(200), "ㅎㅏㄱ!ㄱㅛ"));
        assertEquals("있다", typeTimed(doubleTapConsonant(200), "ㅇㅣㅅ!ㅅㄷㅏ"));      // 받침 ㅆ
        assertEquals("아빠", typeTimed(doubleTapConsonant(200), "ㅇㅏㅂ!ㅂㅏ"));       // ㅃ는 받침이 될 수 없다
        assertEquals("떡", typeTimed(doubleTapConsonant(200), "ㄷ!ㄷㅓㄱ"));
        assertEquals("짜", typeTimed(doubleTapConsonant(200), "ㅈ!ㅈㅏ"));
    }

    @Test
    public void slowConsonantTapsStayTwoConsonants() {
        // 시간보다 천천히 치면 '학교'가 '하꾜'로 바뀌지 않는다.
        assertEquals("학교", typeTimed(doubleTapConsonant(200), "ㅎㅏㄱㄱㅛ"));
        assertEquals("박사", typeTimed(doubleTapConsonant(200), "ㅂㅏㄱㅅㅏ"));
        // 인식 시간을 줄이면 빠르게 쳐도 두 자음으로 남는다 (60ms > 50ms).
        assertEquals("학교", typeTimed(doubleTapConsonant(50), "ㅎㅏㄱ!ㄱㅛ"));
    }

    @Test
    public void doubleTapConsonantOnlyForSameTappedKey() {
        assertEquals("ㅋㅋㅋ", typeTimed(doubleTapConsonant(200), "ㅋ!ㅋ!ㅋ"));     // 쌍자음이 없는 자음
        assertEquals("ㄲㄱ", typeTimed(doubleTapConsonant(200), "ㄱ!ㄱ!ㄱ"));       // 세 번째는 새 ㄱ
        assertEquals("ㄲ", typeTimed(doubleTapConsonant(200), "↓ㄱ"));
        assertEquals("ㄲㄱ", typeTimed(doubleTapConsonant(200), "↓ㄱ!ㄱ"));         // 밀어서 넣은 뒤의 탭은 새 ㄱ
        assertEquals("ㄲ", typeTimed(doubleTapConsonant(200), "ㄱ<ㄱ!ㄱ"));          // 지운 뒤 다시 두 번 탭
        assertEquals("ㄱ", typeTimed(doubleTapConsonant(200), "ㄱ!<!ㄱ"));          // 사이에 지우기가 끼면 새 ㄱ
        assertEquals("각", typeTimed(doubleTapConsonant(200), "ㄱ!ㅏ!ㄱ"));         // 사이에 모음이 끼면 받침 ㄱ
    }

    @Test
    public void doubleTapConsonantOffByDefault() {
        assertEquals("ㄱㄱ", typeTimed(new HangulComposer(), "ㄱ!ㄱ"));
        assertEquals("학교", typeTimed(new HangulComposer(), "ㅎㅏㄱ!ㄱㅛ"));
    }

    @Test
    public void backspaceAfterDoubleTapConsonant() {
        HangulComposer h = doubleTapConsonant(200);
        assertEquals("핚", typeTimed(h, "ㅎㅏㄱ!ㄱ"));
        assertTrue(h.backspace());
        assertEquals("하", h.getComposing());   // 쌍자음 전체가 한 번에 지워진다
    }

    @Test
    public void doubleTapVowel() {
        assertEquals("갸", type("ㄱㅏㅏ"));
        assertEquals("여행", type("ㅇㅓㅓㅎㅐㅇ"));
        assertEquals("학교", type("ㅎㅏㄱㄱㅗㅗ"));
        assertEquals("ㅑ", type("ㅏㅏ"));
        assertEquals("가갸", type("ㄱㅏㄱㅏㅏ"));
        // 겹모음의 뒷부분은 이중모음으로 바뀌지 않는다.
        assertEquals("와ㅏ", type("ㅇㅗㅏㅏ"));
    }

    @Test
    public void tripleTapVowel() {
        assertEquals("햐ㅏ", type("ㅎㅏㅏㅏ"));
    }

    @Test
    public void doubleTapVowelCanBeDisabled() {
        HangulComposer h = new HangulComposer();
        h.setDoubleTapVowel(false);
        assertEquals("가ㅏ", type(h, "ㄱㅏㅏ"));
    }

    @Test
    public void swipeThenTapDoesNotDouble() {
        // 스와이프로 ㅑ를 넣은 뒤 ㅏ를 탭하면 그대로 ㅏ가 붙는다.
        assertEquals("갸ㅏ", type("ㄱ↓ㅏㅏ"));
    }

    @Test
    public void backspaceRemovesOneJamo() {
        HangulComposer h = new HangulComposer();
        assertEquals("닭", type(h, "ㄷㅏㄹㄱ"));
        assertTrue(h.backspace());
        assertEquals("달", h.getComposing());
        assertTrue(h.backspace());
        assertEquals("다", h.getComposing());
        assertTrue(h.backspace());
        assertEquals("ㄷ", h.getComposing());
        assertTrue(h.backspace());
        assertEquals("", h.getComposing());
        assertFalse(h.backspace());
    }

    @Test
    public void backspaceAfterFinalMovedToNextSyllable() {
        assertEquals("가ㄱ", type("ㄱㅏㄱㅏ<"));
        assertEquals("와", type("ㅇㅗㅏㅣ<"));
        assertEquals("왜", type("ㅇㅗㅏㅣ"));
    }

    @Test
    public void nonJamoFlushesComposition() {
        HangulComposer h = new HangulComposer();
        assertEquals("가.", type(h, "ㄱㅏ."));
        assertEquals("", h.getComposing());
    }

    @Test
    public void flushCommitsComposing() {
        HangulComposer h = new HangulComposer();
        h.input('ㄱ', true);
        h.input('ㅏ', true);
        h.flush();
        assertEquals("가", h.takeCommit());
        assertTrue(h.isEmpty());
    }

    @Test
    public void compoundFinalSwipeGoesIntoFinalPosition() {
        assertEquals("닭", type("ㄷㅏㄺ"));
        assertEquals("값", type("ㄱㅏㅄ"));
        assertEquals("넋", type("ㄴㅓㄳ"));
        assertEquals("앉", type("ㅇㅏㄵ"));
        assertEquals("닳", type("ㄷㅏㅀ"));
    }

    @Test
    public void compoundFinalMovesToNextSyllableAsSplit() {
        assertEquals("달기", type("ㄷㅏㄺㅣ"));
        assertEquals("갑시", type("ㄱㅏㅄㅣ"));
    }

    @Test
    public void backspaceRemovesWholeCompoundFinal() {
        assertEquals("다", type("ㄷㅏㄺ<"));
    }

    @Test
    public void compoundFinalWithoutFinalSlotIsTypedAsIs() {
        assertEquals("ㄺ", type("ㄺ"));
        assertEquals("달ㄺ", type("ㄷㅏㄹㄺ"));
        assertEquals("ㄺ가", type("ㄺㄱㅏ"));
        assertEquals("ㄺㅏ", type("ㄺㅏ"));
    }

    @Test
    public void compoundFinalHelpers() {
        assertEquals("ㄺㄻㄼㄽㄾㄿㅀ", HangulComposer.compoundFinalsStartingWith('ㄹ'));
        assertEquals("ㄳ", HangulComposer.compoundFinalsStartingWith('ㄱ'));
        assertEquals("", HangulComposer.compoundFinalsStartingWith('ㅁ'));
        assertEquals('ㄹ', HangulComposer.compoundFirst('ㄺ'));
        assertTrue(HangulComposer.isCompoundFinal('ㅄ'));
        assertFalse(HangulComposer.isCompoundFinal('ㅅ'));
    }

    @Test
    public void compoundVowelInputDirectly() {
        HangulComposer h = new HangulComposer();
        h.input('ㅇ', true);
        h.input('ㅘ', false);                       // 밀어서 이중모음: ㅇ+ㅘ = 와
        assertEquals("와", h.getComposing());
        h.input('ㅂ', true);
        assertEquals("왑", h.getComposing());      // 이어서 받침도 붙는다
        h.input('ㅢ', false);                       // 받침이 다음 글자 초성으로 넘어간다
        assertEquals("와", h.takeCommit());
        assertEquals("븨", h.getComposing());
    }

    @Test
    public void compoundVowelBackspaceRemovesOneVowelAtATime() {
        HangulComposer h = new HangulComposer();
        h.input('ㅎ', true);
        h.input('ㅝ', false);
        assertEquals("훠", h.getComposing());
        assertTrue(h.backspace());
        assertEquals("후", h.getComposing());      // 겹받침과 달리 한 모음씩 지운다 (ㅝ → ㅜ)
        assertTrue(h.backspace());
        assertEquals("ㅎ", h.getComposing());
        assertTrue(h.backspace());
        assertTrue(h.isEmpty());
        assertFalse(h.backspace());
    }

    @Test
    public void everyCompoundVowelBacksOffToItsFirstVowel() {
        String compound = "ㅘㅙㅝㅞㅟㅢ";
        String whole = "화홰훠훼휘희";      // ㅎ + 겹모음
        String backedOff = "호호후후후흐";   // 한 번 지운 뒤: ㅎ + 앞 모음 (ㅙ는 ㅗ, ㅞ는 ㅜ)
        for (int i = 0; i < compound.length(); i++) {
            HangulComposer h = new HangulComposer();
            h.input('ㅎ', true);
            h.input(compound.charAt(i), false);
            assertEquals(String.valueOf(whole.charAt(i)), h.getComposing());
            assertTrue(h.backspace());
            assertEquals(String.valueOf(backedOff.charAt(i)), h.getComposing());
            assertTrue(h.backspace());
            assertEquals("ㅎ", h.getComposing());
        }
    }

    @Test
    public void compoundVowelWithFinalStillMovesFinalToNextSyllable() {
        HangulComposer h = new HangulComposer();
        h.input('ㅂ', true);
        h.input('ㅏ', true);
        h.input('ㄱ', true);                        // 박
        h.input('ㅘ', false);                       // 받침이 넘어가 '바' + '과'
        assertEquals("바", h.takeCommit());
        assertEquals("과", h.getComposing());
        h.backspace();
        assertEquals("고", h.getComposing());      // ㅘ → ㅗ
    }

    @Test
    public void compoundVowelAfterSyllableStartsNewOne() {
        HangulComposer h = new HangulComposer();
        h.input('ㄱ', true);
        h.input('ㅗ', true);                        // 고
        h.input('ㅙ', false);                       // 모음 뒤에 겹모음: 앞 글자를 확정하고 단독 모음
        assertEquals("고", h.takeCommit());
        assertEquals("ㅙ", h.getComposing());
    }
}
