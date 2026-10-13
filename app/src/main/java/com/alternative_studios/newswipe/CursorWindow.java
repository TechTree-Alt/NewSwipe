package com.alternative_studios.newswipe;

import java.text.BreakIterator;

/**
 * 밀어서 좌우로 커서를 옮길 때, 방향 키 대신 커서 위치를 직접 정해(setSelection) 옮기기 위해 커서 주변 글을 들고 있는다.
 *
 * <p>방향 키는 입력란이 나중에 처리하므로(앱이 키를 따로 줄 세운다), 키를 보낸 직후 입력란에 글자를 물으면 아직 커서가
 * 움직이기 전의 글을 돌려줄 때가 있다. 그 답을 믿고 키를 더 보내면 글의 맨 앞·맨 뒤를 넘겨 앱이 포커스를 다른 영역으로 넘긴다.
 * 커서 위치를 직접 정하는 요청은 글자를 묻는 요청과 같은 순서로 바로 처리되므로 늘 맞는 글을 보고, 글 밖으로 나갈 일도 없다.
 */
final class CursorWindow {

    /** 글의 맨 앞·맨 뒤에 닿아 더 갈 수 없다. */
    static final int EDGE = -1;
    /** 들고 있는 글 밖이라 모른다: 커서 주변 글을 다시 읽어야 한다. */
    static final int UNKNOWN = -2;

    /** 커서 앞뒤로 한 번에 읽는 글자 수. 이만큼 움직일 동안 다시 묻지 않는다. */
    static final int WINDOW = 32;

    private String text;
    /** text[0]의 글 안 위치. */
    private int offset;
    /** text가 글의 끝까지 담고 있는지 (아니면 뒤에 글이 더 있을 수 있다). */
    private boolean reachesEnd;
    /** 지금 커서의 글 안 위치. */
    private int pos;
    private BreakIterator graphemes;

    void reset() {
        text = null;
    }

    /**
     * 커서 주변 글을 넣는다. text는 글 안 위치 offset부터이고 커서는 그 안의 cursor 자리에 있다.
     * after는 커서 뒤로 읽으려 한 글자 수다 (그보다 적게 왔으면 글의 끝까지 온 것이다).
     */
    void set(CharSequence text, int offset, int cursor, int after) {
        this.text = text.toString();
        this.offset = offset;
        this.pos = offset + cursor;
        this.reachesEnd = text.length() - cursor < after;
        if (graphemes == null) graphemes = BreakIterator.getCharacterInstance();
        graphemes.setText(this.text);
    }

    /**
     * 커서를 한 글자 묶음(이모지 등은 한 묶음) 앞(backward)·뒤로 옮길 글 안 위치. 옮겼다고 기억한다.
     * 글의 끝이면 {@link #EDGE}, 들고 있는 글 밖이면 {@link #UNKNOWN} (다시 읽고 부른다).
     */
    int next(boolean backward) {
        if (text == null) return UNKNOWN;
        int i = pos - offset;
        if (i < 0 || i > text.length()) return UNKNOWN;
        int j;
        if (backward) {
            if (i == 0) return offset == 0 ? EDGE : UNKNOWN;
            j = graphemes.preceding(i);
            // 들고 있는 글의 첫 묶음은 앞이 잘려 있을 수 있으므로, 앞에 글이 더 있으면 다시 읽어 확인한다.
            if (j == BreakIterator.DONE || (j == 0 && offset > 0)) return UNKNOWN;
        } else {
            if (i == text.length()) return reachesEnd ? EDGE : UNKNOWN;
            j = graphemes.following(i);
            if (j == BreakIterator.DONE || (j == text.length() && !reachesEnd)) return UNKNOWN;
        }
        pos = offset + j;
        return pos;
    }
}
