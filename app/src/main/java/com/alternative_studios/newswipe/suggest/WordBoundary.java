package com.alternative_studios.newswipe.suggest;

/**
 * 커서를 단어 단위로 옮길 때 몇 글자(UTF-16 단위)를 건너뛸지 정한다.
 * 커서 쪽의 공백을 건너뛴 뒤, 글자·숫자가 이어지면 그 묶음 전체를, 아니면 문장 부호 하나를 건너뛴다
 * (단어 지우기와 같은 기준). 왼쪽으로는 앞 단어의 처음, 오른쪽으로는 다음 단어의 끝에 선다.
 */
public final class WordBoundary {
    private WordBoundary() {
    }

    /**
     * @param text    왼쪽으로 옮길 때는 커서 앞의 글, 오른쪽으로 옮길 때는 커서 뒤의 글
     * @param forward 오른쪽으로 옮기는지
     */
    public static int distance(CharSequence text, boolean forward) {
        if (text == null || text.length() == 0) return 0;
        int len = text.length();
        int i = forward ? 0 : len;
        // 공백
        while (forward ? i < len : i > 0) {
            int cp = forward ? Character.codePointAt(text, i) : Character.codePointBefore(text, i);
            if (!Character.isWhitespace(cp)) break;
            i += forward ? Character.charCount(cp) : -Character.charCount(cp);
        }
        if (forward ? i >= len : i <= 0) return forward ? i : len - i;
        int first = forward ? Character.codePointAt(text, i) : Character.codePointBefore(text, i);
        if (!Character.isLetterOrDigit(first)) {
            i += forward ? Character.charCount(first) : -Character.charCount(first);   // 문장 부호 하나
        } else {
            while (forward ? i < len : i > 0) {
                int cp = forward ? Character.codePointAt(text, i) : Character.codePointBefore(text, i);
                if (!Character.isLetterOrDigit(cp)) break;
                i += forward ? Character.charCount(cp) : -Character.charCount(cp);
            }
        }
        return forward ? i : len - i;
    }
}
