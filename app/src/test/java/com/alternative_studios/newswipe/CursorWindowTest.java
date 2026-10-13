package com.alternative_studios.newswipe;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class CursorWindowTest {

    /** 글 full에서 커서 cursor 주변을 입력란처럼 앞뒤 WINDOW자씩 잘라 넣는다. */
    private static void read(CursorWindow w, String full, int cursor) {
        int from = Math.max(0, cursor - CursorWindow.WINDOW);
        int to = Math.min(full.length(), cursor + CursorWindow.WINDOW);
        w.set(full.substring(from, to), from, cursor - from, CursorWindow.WINDOW);
    }

    /** 끝까지 왼쪽(또는 오른쪽)으로 옮기며, 모르면 다시 읽는다. 지나간 위치 수를 돌려준다. */
    private static int walk(String full, int cursor, boolean backward) {
        CursorWindow w = new CursorWindow();
        int steps = 0;
        while (true) {
            int to = w.next(backward);
            if (to == CursorWindow.UNKNOWN) {
                read(w, full, cursor);
                to = w.next(backward);
            }
            if (to == CursorWindow.EDGE) return steps;
            assertEquals(true, to >= 0 && to <= full.length());
            cursor = to;
            steps++;
        }
    }

    @Test
    public void stopsAtStartAndEnd() {
        assertEquals(5, walk("안녕하세요", 5, true));
        assertEquals(3, walk("abcde", 2, false));
        assertEquals(0, walk("abc", 0, true));
        assertEquals(0, walk("abc", 3, false));
        assertEquals(0, walk("", 0, true));
    }

    @Test
    public void longTextCrossesWindows() {
        String s = "가".repeat(100) + "\n" + "b".repeat(100);
        assertEquals(201, walk(s, 201, true));
        assertEquals(201, walk(s, 0, false));
        assertEquals(101, walk(s, 100, false));
    }

    @Test
    public void emojiMovesAsOneStep() {
        String s = "a👍🏻b";   // a 👍🏻 b
        assertEquals(3, walk(s, s.length(), true));
        assertEquals(3, walk(s, 0, false));
    }

    @Test
    public void positionsAreAbsolute() {
        String s = "x".repeat(50);
        CursorWindow w = new CursorWindow();
        read(w, s, 40);
        assertEquals(39, w.next(true));
        assertEquals(40, w.next(false));
        assertEquals(41, w.next(false));
    }

    @Test
    public void unknownBeforeRead() {
        assertEquals(CursorWindow.UNKNOWN, new CursorWindow().next(true));
    }
}
