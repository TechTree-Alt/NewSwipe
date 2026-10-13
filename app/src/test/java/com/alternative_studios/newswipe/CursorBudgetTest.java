package com.alternative_studios.newswipe;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class CursorBudgetTest {

    /** 커서 위치를 가진 가짜 입력란. 몇 번 물었는지 센다. */
    private static final class Field implements CursorBudget.TextSource {
        final String text;
        int cursor;
        int reads;

        Field(String text, int cursor) {
            this.text = text;
            this.cursor = cursor;
        }

        @Override
        public CharSequence read(boolean backward, int n) {
            reads++;
            return backward ? text.substring(Math.max(0, cursor - n), cursor)
                    : text.substring(cursor, Math.min(text.length(), cursor + n));
        }
    }

    @Test
    public void stopsAtTextStartWithoutSendingKey() {
        Field f = new Field("안녕하세요", 2);
        CursorBudget b = new CursorBudget();
        assertTrue(b.take(CursorBudget.LEFT, f));
        f.cursor--;
        assertTrue(b.take(CursorBudget.LEFT, f));
        f.cursor--;
        assertFalse(b.take(CursorBudget.LEFT, f));
        assertFalse(b.take(CursorBudget.LEFT, f));
        assertEquals("센 칸을 다 쓴 뒤에 한 번만 더 묻는다", 2, f.reads);
    }

    @Test
    public void stopsAtTextEnd() {
        Field f = new Field("abc", 1);
        CursorBudget b = new CursorBudget();
        assertTrue(b.take(CursorBudget.RIGHT, f));
        f.cursor++;
        assertTrue(b.take(CursorBudget.RIGHT, f));
        f.cursor++;
        assertFalse(b.take(CursorBudget.RIGHT, f));
    }

    @Test
    public void atEdgeKeepsAnsweringWithoutAsking() {
        Field f = new Field("abc", 0);
        CursorBudget b = new CursorBudget();
        for (int i = 0; i < 50; i++) assertFalse(b.take(CursorBudget.LEFT, f));
        assertEquals(1, f.reads);
    }

    @Test
    public void movingBackOpensTheOtherSide() {
        Field f = new Field("ab", 0);
        CursorBudget b = new CursorBudget();
        assertFalse(b.take(CursorBudget.LEFT, f));
        assertTrue(b.take(CursorBudget.RIGHT, f));
        assertTrue(b.take(CursorBudget.LEFT, f));
        assertFalse(b.take(CursorBudget.LEFT, f));
    }

    @Test
    public void longTextAsksOncePerWindow() {
        Field f = new Field("x".repeat(200), 200);
        CursorBudget b = new CursorBudget();
        for (int i = 0; i < 200; i++) {
            assertTrue(b.take(CursorBudget.LEFT, f));
            f.cursor--;
        }
        assertFalse(b.take(CursorBudget.LEFT, f));
        assertTrue(f.reads <= 200 / CursorBudget.WINDOW_H + 2);
    }

    @Test
    public void emojiNeverOvercounted() {
        // 이모지는 앱마다 한 칸 크기가 다를 수 있어 세지 않고 칸마다 다시 묻는다. 그래도 끝에서는 멈춘다.
        Field f = new Field("👍🏻", 4);
        CursorBudget b = new CursorBudget();
        assertTrue(b.take(CursorBudget.LEFT, f));
        f.cursor = 0;
        assertFalse(b.take(CursorBudget.LEFT, f));
    }

    @Test
    public void unknownTextNeverBlocks() {
        CursorBudget b = new CursorBudget();
        for (int i = 0; i < 100; i++) assertTrue(b.take(CursorBudget.LEFT, (back, n) -> null));
    }

    @Test
    public void verticalCountsLines() {
        Field f = new Field("a\nb\nc", 5);
        CursorBudget b = new CursorBudget();
        assertTrue(b.take(CursorBudget.UP, f));
        assertTrue(b.take(CursorBudget.UP, f));
        f.cursor = 1;   // 첫 줄 끝: 한 번 더 위로 가면 맨 앞으로
        assertTrue(b.take(CursorBudget.UP, f));
        f.cursor = 0;
        assertFalse(b.take(CursorBudget.UP, f));
        assertFalse(b.take(CursorBudget.DOWN, new Field("abc", 3)));
    }

    @Test
    public void otherAxisForgottenAfterMove() {
        Field f = new Field("ab\ncd", 3);
        CursorBudget b = new CursorBudget();
        assertTrue(b.take(CursorBudget.UP, f));   // 줄 하나 위로 (UP 남은 칸 0)
        f.cursor = 2;
        assertTrue(b.take(CursorBudget.LEFT, f)); // 좌우로 움직이면 위아래는 다시 센다
        f.cursor = 1;
        assertTrue(b.take(CursorBudget.UP, f));   // 첫 줄이지만 글자가 앞에 있어 맨 앞으로 간다
        f.cursor = 0;
        assertFalse(b.take(CursorBudget.UP, f));
    }
}
