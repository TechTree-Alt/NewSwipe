package com.alternative_studios.newswipe.suggest;

/**
 * 커서 앞 글자를 키보드가 직접 따라가며 기억해 둔다.
 * 추천 단어를 찾을 때마다 앱에 커서 앞 글자를 묻지 않아도 되게 한다. 앱에 묻는 호출은 앱이 바쁘면 화면 스레드를 막는다.
 *
 * <p>키보드가 보낸 편집은 그대로 따라 적고, 앱이 알려 주는 커서 위치(onUpdateSelection)가 예상과 다르면
 * (사용자가 커서를 옮겼거나 앱이 글자를 바꿨으면) 기억을 버린다. 기억이 없으면 호출한 쪽이 앱에 다시 물어 채운다.
 * 추천 표시처럼 틀려도 곧 바로잡히는 곳에만 쓰고, 글자를 지우고 바꾸는 동작은 앱에 직접 물어서 한다.
 *
 * <p>위치는 앱이 쓰는 UTF-16 단위 절대 위치다. committedEnd = 조합 중인 글자 바로 앞 위치.
 */
public final class TextMirror {
    /** 기억해 두는 최대 글자 수. 추천에는 마지막 단어만 필요하다. */
    static final int KEEP = 48;
    private static final int RING = 16;

    private final StringBuilder text = new StringBuilder();
    private boolean valid;
    private boolean fromStart;   // text가 입력란의 처음부터인지 (더 앞에 글자가 없음)
    private String composing = "";
    private int committedEnd = -1;   // 모르면 -1
    /** 아직 앱이 알려 오지 않은, 키보드 편집 뒤의 예상 커서 위치들 (오래된 것부터). */
    private final int[] pending = new int[RING];
    private int pendingCount;

    /** 새 입력란을 시작했다. 커서 위치를 알면 넘겨준다 (모르면 -1). */
    public void start(int selStart, int selEnd) {
        forget();
        committedEnd = selStart >= 0 && selStart == selEnd ? selStart : -1;
    }

    /** 무엇이 바뀌었는지 모르는 편집을 했다 (엔터, 커서 이동 키, 선택 영역 지우기 등). */
    public void forget() {
        valid = false;
        text.setLength(0);
        composing = "";
        committedEnd = -1;
        pendingCount = 0;
    }

    public boolean isValid() {
        return valid;
    }

    /** 앱에서 읽은 커서 앞 글자로 채운다. requested = 요청한 글자 수 (덜 왔으면 입력란 처음부터다). */
    public void fill(CharSequence before, int requested) {
        if (before == null) return;
        String s = before.toString();
        if (!composing.isEmpty()) {
            if (!s.endsWith(composing)) return;   // 앱 상태가 예상과 다르다. 다음 기회에 다시 읽는다.
            s = s.substring(0, s.length() - composing.length());
        }
        text.setLength(0);
        text.append(s);
        fromStart = before.length() < requested;
        valid = true;
        trim();
    }

    /** 확정 글자를 넣었다 (조합 중인 글자가 있었다면 그 자리를 대신한다). */
    public void commit(String s) {
        if (valid) {
            text.append(s);
            trim();
        }
        if (committedEnd >= 0) committedEnd += s.length();
        composing = "";
        expect();
    }

    /** 조합 중인 글자를 바꿨다. */
    public void setComposing(String s) {
        composing = s;
        expect();
    }

    /** 조합 중인 글자를 그대로 확정했다. */
    public void finishComposing() {
        String c = composing;
        composing = "";
        if (valid) {
            text.append(c);
            trim();
        }
        if (committedEnd >= 0) committedEnd += c.length();
        expect();
    }

    /** 커서 앞 n글자를 지우고 s를 넣었다 (조합 중인 글자는 없어야 한다). */
    public void replaceTail(int n, String s) {
        if (valid) {
            if (text.length() >= n) {
                text.setLength(text.length() - n);
                text.append(s);
                trim();
            } else if (fromStart) {
                text.setLength(0);
                text.append(s);
            } else {
                valid = false;
                text.setLength(0);
            }
        }
        if (committedEnd >= 0) committedEnd = Math.max(0, committedEnd - n) + s.length();
        composing = "";
        expect();
    }

    /**
     * 지우기 키를 보냈다. 앱이 몇 글자를 지울지는 앱 마음이라(이모지 묶음 등), 한 글자짜리 평범한 글자일 때만 따라 적고
     * 아니면 기억을 버린다.
     */
    public void deleteKey() {
        if (!valid || committedEnd < 0) {
            forget();
            return;
        }
        int n = text.length();
        if (n == 0) {
            if (fromStart) return;   // 입력란 맨 앞이라 지울 것이 없다
            forget();
            return;
        }
        char c = text.charAt(n - 1);
        if (Character.isSurrogate(c) || Character.getType(c) == Character.NON_SPACING_MARK
                || Character.getType(c) == Character.ENCLOSING_MARK || Character.getType(c) == Character.FORMAT
                || (c >= '︀' && c <= '️') || c == '\n' || c == '\r') {
            forget();
            return;
        }
        // 앞 글자와 합쳐져 한 글자로 보이는 경우(결합 문자)가 아니면 한 글자만 지워진다.
        text.setLength(n - 1);
        committedEnd--;
        expect();
    }

    private void expect() {
        if (committedEnd < 0) return;
        int cursor = committedEnd + composing.length();
        if (pendingCount == RING) {
            System.arraycopy(pending, 1, pending, 0, RING - 1);
            pendingCount--;
        }
        pending[pendingCount++] = cursor;
    }

    /** 앱이 알려 준 새 커서 위치. 키보드가 한 편집의 (늦게 도착한) 결과가 아니면 기억을 버린다. */
    public void onSelection(int selStart, int selEnd) {
        if (selStart != selEnd || selStart < 0) {
            forget();
            return;
        }
        if (committedEnd < 0) {
            // 기준 위치를 모르던 상태: 이 위치를 기준으로 삼되, 글자 기억은 믿을 수 없으니 버린다.
            valid = false;
            text.setLength(0);
            committedEnd = Math.max(0, selStart - composing.length());
            pendingCount = 0;
            return;
        }
        for (int i = 0; i < pendingCount; i++) {
            if (pending[i] == selStart) {
                // i번째 편집까지 앱에 반영됐다. 그 뒤 편집의 소식은 아직 오는 중이다.
                System.arraycopy(pending, i + 1, pending, 0, pendingCount - i - 1);
                pendingCount -= i + 1;
                return;
            }
        }
        if (pendingCount == 0 && selStart == committedEnd + composing.length()) return;
        // 예상하지 못한 위치: 사용자가 커서를 옮겼거나 앱이 글자를 바꿨다.
        valid = false;
        text.setLength(0);
        committedEnd = Math.max(0, selStart - composing.length());
        pendingCount = 0;
    }

    /**
     * 커서 앞의 입력 중인 단어. 기억이 없거나 단어가 기억한 범위 밖까지 이어질 수 있으면 null (앱에 물어야 한다).
     */
    public String currentWord(boolean korean) {
        if (!valid) return null;
        String all = composing.isEmpty() ? text.toString() : text + composing;
        String w = WordSuggester.currentWord(all, korean);
        if (w.length() == all.length() && !fromStart && !w.isEmpty()) return null;
        return w;
    }

    /** 커서 바로 앞 글자 (조합 중인 글자 포함). 기억이 없거나 비어 있으면 0. */
    public char lastChar() {
        if (!composing.isEmpty()) return composing.charAt(composing.length() - 1);
        return valid && text.length() > 0 ? text.charAt(text.length() - 1) : 0;
    }

    private void trim() {
        int over = text.length() - KEEP;
        if (over > 0) {
            text.delete(0, over);
            fromStart = false;
        }
    }
}
