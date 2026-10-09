package com.alternative_studios.newswipe.keyboard;

import android.graphics.RectF;

/** 자판의 키 하나. */
public final class Key {
    public static final int CHAR = 0;
    public static final int SHIFT = 1;
    public static final int DELETE = 2;
    public static final int ENTER = 3;
    public static final int SPACE = 4;
    /** 기호 자판으로 (?123). */
    public static final int TO_SYMBOLS = 5;
    /** 문자 자판으로 돌아가기 (가/ABC). */
    public static final int TO_LETTERS = 6;
    /** 한/영 전환 (지구본). */
    public static final int LANGUAGE = 7;
    /** 기호 자판 1/2쪽 전환. */
    public static final int SYMBOL_PAGE = 8;

    /** 아무것도 그리지 않고 눌러도 반응하지 않는 빈 자리. */
    public static final int SPACER = 9;
    /** 이모지 패널 열기 (기호 자판에서 지구본 자리). */
    public static final int EMOJI = 10;
    /** Fn 키: 한글 자판의 Shift 자리. 탭·길게 누르기·밀기마다 사용자가 정한 기능을 실행한다 (FnKeyActions). */
    public static final int FUNCTION = 11;
    /**
     * 키 사이의 빈틈 (자·모음 균형 레이아웃에서 자음과 모음 사이). 그리지 않고, 누르면 가장 가까운 키를 누른 것으로 본다.
     * (SPACER는 그 자리를 눌러도 반응하지 않는 빈 자리다.)
     */
    public static final int GAP = 12;

    /** 스와이프 방향. */
    public static final int SWIPE_DOWN = 0, SWIPE_UP = 1, SWIPE_LEFT = 2, SWIPE_RIGHT = 3;

    public final int type;
    public final String label;
    /** 탭했을 때 입력되는 글자. */
    public final String output;
    /** Shift 상태에서 입력되는 글자 (영어 대문자, 한글 쌍자음/이중모음). */
    public final String shifted;
    /** 아래로 스와이프했을 때 입력되는 글자. 없으면 null. */
    String swipeDown;
    /** 위/왼쪽/오른쪽으로 스와이프했을 때 입력되는 글자 (겹받침). 없으면 null. */
    String swipeUp, swipeLeft, swipeRight;
    /** 길게 눌렀을 때 고를 수 있는 글자들. 첫 번째가 기본값. 없으면 null. 이메일·인터넷 주소 입력란의 온점 키만 나중에 바꾼다. */
    public String[] popup;
    /** 1.0 = 글자 키 한 칸. 자판을 만드는 동안에만 바뀐다 (스페이스바가 없는 아래 줄을 채울 때). */
    public float weight;
    /** 글자 키지만 기능키 색으로 그린다 (쉼표, 마침표). */
    boolean grayStyle;
    /**
     * 설정(길게 누르기·밀기·연속 입력)에 저장된 이 키의 자리 이름. 하단 쉼표·온점 키를 다른 글자로 바꾸면
     * label은 바뀐 글자이고 이 값은 원래 자리(",", ".")로 남는다. 바꾸지 않은 키는 null이다.
     */
    String slot;
    /** 그리지 않지만 누르면 입력되는 키 (분리 키보드의 빈 자리에 두는 g, v). */
    boolean hidden;
    /** 길게 눌러 연속 입력으로 정해 둔 글자여도 연속 입력을 쓰지 않는다 (이메일·인터넷 주소 입력란의 온점 키). */
    boolean noRepeat;

    /** 설정에 저장된 이 키의 이름 (label과 다를 수 있다). */
    public String slot() {
        return slot != null ? slot : label;
    }

    /** 자판 뷰가 배치할 때 채우는 위치. */
    public final RectF rect = new RectF();

    Key(int type, String label, String output, String shifted, String swipeDown, String[] popup, float weight) {
        this.type = type;
        this.label = label;
        this.output = output;
        this.shifted = shifted;
        this.swipeDown = swipeDown;
        this.popup = popup;
        this.weight = weight;
    }

    /** 해당 방향으로 스와이프했을 때 입력되는 글자. 없으면 null. */
    public String swipeText(int dir) {
        switch (dir) {
            case SWIPE_DOWN: return swipeDown;
            case SWIPE_UP: return swipeUp;
            case SWIPE_LEFT: return swipeLeft;
            case SWIPE_RIGHT: return swipeRight;
            default: return null;
        }
    }

    public boolean isFunctional() {
        return grayStyle || (type != CHAR && type != SPACE);
    }

    /**
     * 첫 번째 길게 누르기 글자 (키 위에 작게 표시). 첫 번째 항목이 단어처럼 길면 그 첫 글자 하나만 보인다.
     * 그릴 때마다 새 문자열을 만들지 않도록 길게 누르기 문자가 바뀔 때만 다시 만든다.
     */
    public String hint() {
        if (grayStyle || popup == null || popup.length == 0) return null;
        String first = popup[0];
        if (first == null || first.isEmpty()) return null;
        if (hintSource != first) {
            hintSource = first;
            hintFirst = first.substring(0, first.offsetByCodePoints(0, 1));   // 이모지처럼 두 칸짜리 글자도 한 글자로
            hintUpper = null;
        }
        return hintFirst;
    }

    private String hintSource, hintFirst;

    /** hint()의 대문자 (영어 자판 Shift 상태). 그릴 때마다 새 문자열을 만들지 않도록 처음 쓸 때 만들어 둔다. */
    private String hintUpper;

    public String hintUpper() {
        String h = hint();
        if (h == null) return null;
        if (!isSingleChar(popup[0])) return h;   // 단어는 Shift를 켜도 그대로 입력되므로 힌트도 그대로다
        if (hintUpper == null) hintUpper = h.toUpperCase(java.util.Locale.ROOT);
        return hintUpper;
    }

    /** 글자 하나(코드 포인트 하나)인지. 길게 눌러 입력하는 항목 중 Shift를 따르는 것은 이런 것뿐이다. */
    static boolean isSingleChar(String s) {
        return s != null && !s.isEmpty() && s.codePointCount(0, s.length()) == 1;
    }
}
