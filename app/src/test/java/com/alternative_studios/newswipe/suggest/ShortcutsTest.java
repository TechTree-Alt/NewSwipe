package com.alternative_studios.newswipe.suggest;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.LinkedHashMap;
import java.util.Map;

public class ShortcutsTest {
    private static Shortcuts sample() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("ㅈㄱㅈ", "지금 가는 중");
        m.put("ㅎㅇㅈ", "지금은 회의 중입니다");
        m.put("OMW", "On my way");
        return Shortcuts.of(m);
    }

    @Test
    public void looksUpExactAbbreviation() {
        Shortcuts s = sample();
        assertEquals("지금 가는 중", s.lookup("ㅈㄱㅈ"));
        assertEquals("지금은 회의 중입니다", s.lookup("ㅎㅇㅈ"));
        assertNull(s.lookup("ㅈㄱ"));      // 줄임말의 앞부분만으로는 뜨지 않는다
        assertNull(s.lookup("ㅈㄱㅈㅈ"));
        assertNull(s.lookup(""));
        assertNull(s.lookup(null));
    }

    @Test
    public void englishIgnoresCase() {
        Shortcuts s = sample();
        assertEquals("On my way", s.lookup("omw"));
        assertEquals("On my way", s.lookup("OMW"));
        assertEquals("On my way", s.lookup("Omw"));
    }

    @Test
    public void validatesKeyAndPhrase() {
        assertNull(Shortcuts.validate("ㅈㄱㅈ", "지금 가는 중"));
        assertNull(Shortcuts.validate("omw", "On my way"));
        assertNotNull(Shortcuts.validate("", "문장"));
        assertNotNull(Shortcuts.validate("ㅈㄱㅈ", "  "));
        assertNotNull(Shortcuts.validate("ㅈ ㄱ", "문장"));        // 공백
        assertNotNull(Shortcuts.validate("ㅈㄱa", "문장"));        // 한글과 영어 섞기
        assertNotNull(Shortcuts.validate("12", "문장"));           // 숫자는 입력 중인 단어로 잡히지 않는다
        assertNotNull(Shortcuts.validate("ㅈ".repeat(Shortcuts.MAX_KEY + 1), "문장"));
        assertNotNull(Shortcuts.validate("ㅈㄱㅈ", "가".repeat(Shortcuts.MAX_PHRASE + 1)));
    }

    @Test
    public void skipsInvalidEntriesAndCleansPhrase() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("ㅈㄱㅈ", "  지금\n가는 중 ");
        m.put("bad key", "무시");
        Shortcuts s = Shortcuts.of(m);
        assertEquals("지금 가는 중", s.lookup("ㅈㄱㅈ"));
        assertNull(s.lookup("bad key"));
        assertTrue(Shortcuts.of(null).isEmpty());
    }
}
