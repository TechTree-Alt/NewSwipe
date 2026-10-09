package com.alternative_studios.newswipe;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

import java.util.Collections;
import java.util.List;

public class GuideTextTest {
    @Test
    public void splitsIntoCards() {
        List<GuideText.Section> s = GuideText.parse(
                "첫 카드\n\n둘째 줄\n\n## 제목 A\n본문 A\n\n## 제목 B\n본문 B\n~~~\n원문\n줄\n", null);
        assertEquals(3, s.size());
        assertNull(s.get(0).title);
        assertEquals("첫 카드\n\n둘째 줄", s.get(0).body);
        assertEquals("제목 A", s.get(1).title);
        assertEquals("본문 A", s.get(1).body);
        assertNull(s.get(1).small);
        assertEquals("본문 B", s.get(2).body);
        assertEquals("원문\n줄", s.get(2).small);
    }

    @Test
    public void replacesValuesAndSkipsEmptyIntro() {
        List<GuideText.Section> s = GuideText.parse("## 기록\n최대 {N}개\n", Collections.singletonMap("N", "30"));
        assertEquals(1, s.size());
        assertEquals("최대 30개", s.get(0).body);
    }
}
