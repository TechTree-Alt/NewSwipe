package com.alternative_studios.newswipe;

import java.text.BreakIterator;

/**
 * 밀어서 커서 이동할 때 각 방향으로 몇 칸 더 갈 수 있는지 기억해 둔다.
 *
 * <p>글의 맨 앞에서 왼쪽 키(맨 뒤에서 오른쪽 키, 첫 줄·마지막 줄 끝에서 위·아래 키)를 보내면 일부 앱(삼성 메시지, 카카오톡 등)은
 * 그 키를 처리하지 않고 화면의 다른 영역(대화면의 채팅 목록 등)으로 포커스를 넘긴다. 그래서 갈 수 없는 쪽으로는 키를 보내지
 * 않는다. 칸마다 입력란에 글자를 묻지 않도록, 한 번 물을 때 여러 글자를 읽어 확실히 갈 수 있는 칸 수만큼 묻지 않고 보낸다.
 *
 * <p>칸 수는 실제보다 적게 세는 쪽으로만 틀린다(다 쓰면 다시 묻는다). 실제보다 많이 세면 끝에서 키가 넘어가기 때문이다.
 */
final class CursorBudget {

    static final int LEFT = 0, RIGHT = 1, UP = 2, DOWN = 3;

    /** 한 번에 읽는 글자 수. 좌우는 이만큼 움직일 동안, 위아래는 이 안의 줄바꿈 수만큼 다시 묻지 않는다. */
    static final int WINDOW_H = 32;
    static final int WINDOW_V = 256;
    /** 입력란이 글자를 알려 주지 않을 때 막지 않고(묻지 않고) 보낼 칸 수. 이런 입력란에서는 예전처럼 늘 키를 보낸다. */
    static final int BLIND = 16;

    private static final int UNKNOWN = -1;

    /** 입력란에서 커서 앞(backward)이나 뒤 글자를 n자까지 읽는다. 알 수 없으면 null. */
    interface TextSource {
        CharSequence read(boolean backward, int n);
    }

    private final int[] left = {UNKNOWN, UNKNOWN, UNKNOWN, UNKNOWN};
    private BreakIterator graphemes;

    /** 기억한 칸 수를 모두 잊는다 (새로 밀기 시작할 때, 입력란이 바뀌거나 글이 바뀌었을 때). */
    void reset() {
        for (int i = 0; i < left.length; i++) left[i] = UNKNOWN;
    }

    /** dir 쪽으로 갈 수 있는 칸 수를 알고 있는지 (모르면 다음 {@link #take}에서 입력란에 묻는다). */
    boolean known(int dir) {
        return left[dir] != UNKNOWN;
    }

    /**
     * dir 쪽으로 한 칸 갈 수 있으면 그 한 칸을 쓰고 true. 모르면 src로 글자를 읽어 센다.
     * 갈 수 없으면 false이며, 다시 반대쪽으로 움직이거나 reset할 때까지 묻지 않고 계속 false다.
     */
    boolean take(int dir, TextSource src) {
        if (left[dir] == UNKNOWN) left[dir] = count(dir, src.read(dir == LEFT || dir == UP, dir <= RIGHT ? WINDOW_H : WINDOW_V));
        if (left[dir] == 0) return false;
        // 센 칸을 다 쓰면 끝에 닿았는지 모르므로 다음에 다시 묻는다 (0은 '끝에 닿았다'로만 쓴다).
        if (--left[dir] == 0) left[dir] = UNKNOWN;
        // 한 칸 움직였으면 반대쪽으로는 적어도 한 칸 되돌아갈 수 있다.
        int back = dir ^ 1;
        if (left[back] != UNKNOWN) left[back]++;
        // 좌우로 움직이면 위아래로 갈 수 있는 줄 수가, 위아래로 움직이면 좌우 글자 수가 달라지므로 다른 축은 잊는다.
        int other = dir <= RIGHT ? UP : LEFT;
        left[other] = left[other + 1] = UNKNOWN;
        return true;
    }

    private int count(int dir, CharSequence text) {
        if (text == null) return BLIND;
        if (text.length() == 0) return 0;
        int n = dir <= RIGHT ? simpleGraphemes(text) : newlines(text);
        return Math.max(n, 1);   // 글자가 있으면 적어도 한 칸은 움직인다
    }

    /**
     * 한 글자짜리 글자 묶음(grapheme)의 수. 이모지처럼 여러 글자가 한 묶음인 것은 앱마다 묶는 방식이 달라
     * 실제보다 많이 셀 수 있으므로 세지 않는다 (그런 곳에서는 칸마다 다시 묻는다).
     */
    int simpleGraphemes(CharSequence text) {
        if (graphemes == null) graphemes = BreakIterator.getCharacterInstance();
        graphemes.setText(text.toString());
        int n = 0;
        for (int start = graphemes.first(), end = graphemes.next(); end != BreakIterator.DONE;
             start = end, end = graphemes.next()) {
            if (end - start == 1) n++;
        }
        return n;
    }

    /** 줄바꿈 수: 한 줄을 넘기는 데 적어도 한 번은 위·아래로 움직일 수 있다 (자동 줄바꿈된 줄은 세지 않아 적게 센다). */
    static int newlines(CharSequence text) {
        int n = 0;
        for (int i = 0; i < text.length(); i++) if (text.charAt(i) == '\n') n++;
        return n;
    }
}
