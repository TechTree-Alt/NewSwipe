package com.alternative_studios.newswipe.suggest;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class TextMirrorTest {
    @Test
    public void needsFetchUntilFilled() {
        TextMirror m = new TextMirror();
        m.start(0, 0);
        assertNull(m.currentWord(false));
        m.fill("", 32);
        assertEquals("", m.currentWord(false));
        m.commit("h");
        m.commit("i");
        assertEquals("hi", m.currentWord(false));
        m.commit(" ");
        assertEquals("", m.currentWord(false));
    }

    @Test
    public void followsHangulComposing() {
        TextMirror m = new TextMirror();
        m.start(3, 3);
        m.fill("오늘 ", 32);
        m.setComposing("ㅎ");
        assertEquals("ㅎ", m.currentWord(true));
        m.setComposing("하");
        m.commit("하");
        m.setComposing("ㄴ");
        assertEquals("하ㄴ", m.currentWord(true));
        m.finishComposing();
        assertEquals("하ㄴ", m.currentWord(true));
    }

    @Test
    public void fillStripsComposingAndRejectsMismatch() {
        TextMirror m = new TextMirror();
        m.start(0, 0);
        m.setComposing("가");
        m.fill("나가", 32);
        assertEquals("나가", m.currentWord(true));
        TextMirror n = new TextMirror();
        n.start(0, 0);
        n.setComposing("가");
        n.fill("나다", 32);   // 앱 상태가 예상과 다름
        assertFalse(n.isValid());
    }

    @Test
    public void belatedSelectionUpdatesKeepMirror() {
        TextMirror m = new TextMirror();
        m.start(0, 0);
        m.fill("", 32);
        m.commit("a");   // 1
        m.commit("b");   // 2
        m.commit("c");   // 3
        m.onSelection(1, 1);   // 첫 편집의 소식이 늦게 도착
        assertTrue(m.isValid());
        m.onSelection(3, 3);
        assertTrue(m.isValid());
        assertEquals("abc", m.currentWord(false));
        m.onSelection(3, 3);   // 같은 위치를 한 번 더 알려 와도 괜찮다
        assertTrue(m.isValid());
    }

    @Test
    public void externalCursorMoveForgets() {
        TextMirror m = new TextMirror();
        m.start(0, 0);
        m.fill("", 32);
        m.commit("abc");
        m.onSelection(3, 3);
        m.onSelection(1, 1);   // 사용자가 커서를 옮겼다
        assertFalse(m.isValid());
        assertNull(m.currentWord(false));
        // 다시 읽으면 그 위치부터 따라간다
        m.fill("a", 32);
        m.commit("x");
        m.onSelection(2, 2);
        assertTrue(m.isValid());
        assertEquals("ax", m.currentWord(false));
    }

    @Test
    public void selectionRangeForgets() {
        TextMirror m = new TextMirror();
        m.start(0, 0);
        m.fill("abc", 32);
        m.onSelection(0, 3);
        assertFalse(m.isValid());
    }

    @Test
    public void unknownStartPositionResyncsOnFirstUpdate() {
        TextMirror m = new TextMirror();
        m.start(-1, -1);
        m.fill("abc", 32);
        m.commit("d");
        m.onSelection(4, 4);   // 기준 위치를 몰랐으니 믿지 않고 다시 읽게 한다
        assertFalse(m.isValid());
        m.fill("abcd", 32);
        m.commit("e");
        m.onSelection(5, 5);
        assertTrue(m.isValid());
        assertEquals("abcde", m.currentWord(false));
    }

    @Test
    public void deleteKeyOnPlainCharacters() {
        TextMirror m = new TextMirror();
        m.start(0, 0);
        m.fill("", 32);
        m.commit("ab");
        m.deleteKey();
        assertEquals("a", m.currentWord(false));
        m.onSelection(1, 1);
        assertTrue(m.isValid());
        m.deleteKey();
        m.deleteKey();   // 입력란 맨 앞: 지울 것이 없다
        assertEquals("", m.currentWord(false));
        assertTrue(m.isValid());
    }

    @Test
    public void deleteKeyOnEmojiForgets() {
        TextMirror m = new TextMirror();
        m.start(0, 0);
        m.fill("", 32);
        m.commit("hi \uD83D\uDE00");
        m.deleteKey();
        assertFalse(m.isValid());
        m.start(0, 0);
        m.fill("", 32);
        m.commit("\u2764\uFE0F");   // 이모지 표현 선택자
        m.deleteKey();
        assertFalse(m.isValid());
        m.start(0, 0);
        m.fill("", 32);
        m.commit("a\n");
        m.deleteKey();
        assertFalse(m.isValid());
    }

    @Test
    public void deleteBeyondKnownTextForgets() {
        TextMirror m = new TextMirror();
        m.start(40, 40);
        m.fill("abcdefghijklmnopqrstuvwxyzabcdefgh", 32);   // 32자보다 길게 와서 처음부터가 아님
        for (int i = 0; i < 40; i++) m.deleteKey();
        assertFalse(m.isValid());
    }

    @Test
    public void longWordBeyondKeptTextAsksApp() {
        TextMirror m = new TextMirror();
        m.start(0, 0);
        m.fill("", 32);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < TextMirror.KEEP + 5; i++) sb.append('a');
        m.commit(sb.toString());
        assertNull(m.currentWord(false));   // 기억한 범위 밖까지 이어지는 단어
        m.commit(" b");
        assertEquals("b", m.currentWord(false));
    }

    @Test
    public void replaceTailFollowsCorrections() {
        TextMirror m = new TextMirror();
        m.start(0, 0);
        m.fill("", 32);
        m.commit("I teh");
        m.replaceTail(3, "the ");
        assertEquals("", m.currentWord(false));
        m.replaceTail(4, "teh");   // 되돌리기
        assertEquals("teh", m.currentWord(false));
        m.onSelection(5, 5);
        assertTrue(m.isValid());
        m.replaceTail(10, "x");   // 처음부터 아는 글자보다 많이 지움: 입력란 처음부터라 괜찮다
        assertEquals("x", m.currentWord(false));
    }

    @Test
    public void replaceTailBeyondKnownTextForgetsText() {
        TextMirror m = new TextMirror();
        m.start(100, 100);
        m.fill("0123456789012345678901234567890123456789", 32);
        m.replaceTail(45, "z");
        assertFalse(m.isValid());
    }

    @Test
    public void forgetDropsPosition() {
        TextMirror m = new TextMirror();
        m.start(0, 0);
        m.fill("abc", 32);
        m.forget();
        assertNull(m.currentWord(false));
        m.onSelection(7, 7);   // 새 기준
        m.fill("abcdefg", 32);
        m.commit("h");
        m.onSelection(8, 8);
        assertEquals("abcdefgh", m.currentWord(false));
    }

    @Test
    public void manyEditsBeforeAnyUpdateStayValid() {
        TextMirror m = new TextMirror();
        m.start(0, 0);
        m.fill("", 32);
        for (int i = 0; i < 40; i++) m.commit("a");   // 기다리는 위치 목록(16개)을 넘친다
        m.onSelection(40, 40);
        assertTrue(m.isValid());
        m.onSelection(3, 3);   // 아주 오래된 소식: 목록에서 밀려나 있으면 바깥 변화로 본다 (안전한 쪽)
        assertFalse(m.isValid());
    }

    @Test
    public void composingBackspaceUpdatesWord() {
        TextMirror m = new TextMirror();
        m.start(0, 0);
        m.fill("", 32);
        m.setComposing("수");
        m.setComposing("숫");
        m.onSelection(1, 1);
        assertEquals("숫", m.currentWord(true));
        m.setComposing("수");   // 지우기: 커서 위치는 그대로
        assertEquals("수", m.currentWord(true));
        m.onSelection(1, 1);
        assertTrue(m.isValid());
        assertEquals("수", m.currentWord(true));
    }
}
