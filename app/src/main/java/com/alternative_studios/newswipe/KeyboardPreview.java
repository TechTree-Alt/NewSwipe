package com.alternative_studios.newswipe;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

import com.alternative_studios.newswipe.keyboard.Key;
import com.alternative_studios.newswipe.keyboard.KeyboardLayout;
import com.alternative_studios.newswipe.keyboard.KeyboardTheme;
import com.alternative_studios.newswipe.keyboard.KeyboardView;
import com.alternative_studios.newswipe.keyboard.ToolbarButtons;
import com.alternative_studios.newswipe.ui.IconButton;
import com.alternative_studios.newswipe.ui.Metro;
import com.alternative_studios.newswipe.ui.Ui;

/**
 * '키보드 사용자화' 화면 아래에 붙는, 실제 키보드와 같은 크기·모양의 미리보기 (도구 막대 + 자판).
 * <ul>
 *   <li>기본 상태: 영역(도구 막대·숫자 줄·글자 키·기능키)마다 윤곽선과 이름표를 그린다. 키보드 전체 여백은 설정 목록의 슬라이더로만 바꾼다.
 *       영역을 누르면 그 영역을 고른 것으로 알린다 (설정 화면이 그 영역의 설정 창을 연다).</li>
 *   <li>영역을 고르면 그 영역이 미리보기의 세로 가운데로 미끄러져 오고 위아래의 다른 영역은 흐려진다. 그 영역의 키·도구 막대 버튼마다
 *       윤곽선이 그려져 하나씩 눌러 고를 수 있다 (설정 화면이 그 키·버튼의 설정 창을 연다). 흐린 다른 영역을 누르면 그 영역으로 옮겨 간다.</li>
 *   <li>손잡이를 끌어 크기를 바로 바꾼다: 고른 영역의 위 가장자리 = 높이,
 *       자·모음 균형 배열·분리 키보드의 두 묶음은 묶음의 왼쪽·오른쪽 가장자리 = 그 경계(폭 5% 단위), 묶음 가운데의 ↔ 버튼 = 가로 위치.
 *       실제 키보드처럼 화면 아래에 붙어 있어, 위 가장자리를 위로 끌면 키보드가 위로 자란다. 영역마다의 여백은 설정 창의 슬라이더로만 바꾼다.</li>
 * </ul>
 * 손잡이 위를 눌렀다가 움직이지 않고 떼면 그 자리의 키를 누른 것으로 본다 (가장자리 키도 그대로 고를 수 있다).
 * 값은 바뀔 때마다 바로 저장하고, 그리기는 모두 실제 키보드 뷰(KeyboardView)가 한다.
 */
final class KeyboardPreview extends FrameLayout {

    /** 미리보기에 보일 자판. */
    static final int TAB_KOREAN = 0, TAB_ENGLISH = 1, TAB_SYMBOLS = 2;

    /** 고른 것의 종류. */
    static final int SEL_KEY = 0, SEL_TOOL_BUTTON = 1, SEL_TOOLBAR = 2, SEL_AREA = 3;

    /** 미리보기의 영역. */
    static final int AREA_TOOLBAR = 0, AREA_NUMBER = 1, AREA_LETTERS = 2, AREA_FUNCTION = 3, AREAS = 4;
    static String[] areaNames() {
        return new String[]{L.t("도구 막대", "Toolbar"), L.t("숫자 줄", "Number row"), L.t("글자 키", "Letter keys"), L.t("기능키", "Function keys")};
    }
    /**
     * 분리 키보드의 왼쪽·오른쪽 묶음과 그 사이의 가운데 공간 (윤곽선을 그릴 때 글자 키 영역을 나눠 쓴다).
     * 두 묶음은 함께 '글자 키'로 고르고, 가운데 공간은 따로 고른다.
     */
    static final int AREA_LEFT = 4, AREA_RIGHT = 5, AREA_MIDDLE = 6;

    /** 분리 키보드의 묶음·가운데 공간 영역 이름. */
    static String splitAreaName(int area) {
        switch (area) {
            case AREA_LEFT: return L.t("왼쪽 묶음", "Left block");
            case AREA_RIGHT: return L.t("오른쪽 묶음", "Right block");
            case AREA_MIDDLE: return L.t("가운데 공간", "Center gap");
            default: return areaNames()[area];
        }
    }
    /** 자판 탭 이름 (TAB_* 차례). */
    static String[] tabNames() {
        return new String[]{L.t("한글", "Korean"), L.t("영어", "English"), L.t("기호", "Symbols")};
    }

    /** 영역 이름. 글자 키는 자판마다 따로라 탭 이름을 붙인다 (예: 영어 글자 키). */
    static String areaName(int area, int tab) {
        if (area >= AREAS) return splitAreaName(area);   // 분리 키보드의 가운데 공간 등
        return area == AREA_LETTERS ? tabNames()[tab] + " " + areaNames()[area] : areaNames()[area];
    }

    /** 고른 것. 자판을 다시 만들어도 같은 키를 다시 찾을 수 있게 키 객체 대신 종류·자리 이름·몇 번째인지로 기억한다. */
    static final class Selection {
        final int kind;
        /** SEL_KEY: 키 종류(Key.*)와 설정에 쓰는 자리 이름, 같은 키가 여럿일 때 몇 번째인지. */
        final int keyType;
        final String slot;
        final int occurrence;
        /** SEL_TOOL_BUTTON: 도구 막대 버튼 ID. */
        final String toolId;
        /** 고른 자판 (SEL_KEY). */
        final int tab;

        private Selection(int kind, int keyType, String slot, int occurrence, String toolId, int tab) {
            this.kind = kind;
            this.keyType = keyType;
            this.slot = slot;
            this.occurrence = occurrence;
            this.toolId = toolId;
            this.tab = tab;
        }

        static Selection toolbar() {
            return new Selection(SEL_TOOLBAR, -1, null, 0, null, -1);
        }

        static Selection tool(String id) {
            return new Selection(SEL_TOOL_BUTTON, -1, null, 0, id, -1);
        }

        /** 키 하나 (같은 키가 여럿이면 첫 번째). */
        static Selection key(Key k, int tab) {
            return new Selection(SEL_KEY, k.type, k.slot(), 0, null, tab);
        }

        static Selection area(int id) {
            return new Selection(SEL_AREA, id, null, 0, null, -1);
        }

        /** SEL_AREA의 영역 (AREA_*). */
        int area() {
            return keyType;
        }

        boolean same(Selection o) {
            return o != null && kind == o.kind && keyType == o.keyType && occurrence == o.occurrence && tab == o.tab
                    && java.util.Objects.equals(slot, o.slot) && java.util.Objects.equals(toolId, o.toolId);
        }
    }

    interface Listener {
        /** 키·버튼·도구 막대를 눌렀다 (빈 곳이면 null). */
        void onTap(Selection s);

        /** 손잡이를 끌어 크기 값(prefKey)을 바꾸고 저장했다. */
        void onSizeChanged(String prefKey);
    }

    // ---------------------------------------------------------------- 손잡이

    /**
     * 미리보기의 손잡이: 고른 영역의 높이(위 가장자리: 도구 막대·숫자 줄·글자 키·기능키), 두 묶음의 왼쪽·오른쪽 경계(묶음의 양 옆 가장자리). 모두 가장자리에 붙은 막대 모양이다.
     * 손잡이마다 끄는 방향·이름·저장 키를 한곳에 둔다.
     */
    enum Handle {
        TOOLBAR(true, L.t("도구 막대 높이", "Toolbar height"), Prefs.TOOLBAR_HEIGHT),
        NUMBER_ROW(true, L.t("숫자 줄 높이", "Number row height"), Prefs.NUMBER_ROW_HEIGHT),
        HEIGHT(true, L.t("글자 키 높이", "Letter key height"), Prefs.KEYBOARD_HEIGHT),
        FN_ROW(true, L.t("기능키 높이", "Function key height"), Prefs.FN_ROW_HEIGHT),
        // 자·모음 균형 배열·분리 키보드의 두 묶음: 왼쪽(자음) 묶음과 오른쪽(모음) 묶음의 왼쪽·오른쪽 가장자리.
        // 끌면 그 경계만 움직여 묶음의 폭·가로 위치가 함께 바뀐다. 저장 키는 한글 배열마다 다르다.
        B_LEFT_START(false, L.t("왼쪽 경계", "Left edge"), null),
        B_LEFT_END(false, L.t("오른쪽 경계", "Right edge"), null),
        B_RIGHT_START(false, L.t("왼쪽 경계", "Left edge"), null),
        B_RIGHT_END(false, L.t("오른쪽 경계", "Right edge"), null),
        // 두 묶음 가운데의 ↔ 버튼: 좌우로 끌면 폭은 그대로 두고 묶음을 옮긴다 (가로 위치).
        B_LEFT_MOVE(false, L.t("가로 위치", "Horizontal position"), null),
        B_RIGHT_MOVE(false, L.t("가로 위치", "Horizontal position"), null),
        // 글자 키가 두 묶음으로 나뉘어 있을 때 오른쪽 묶음 위의 높이 손잡이 (HEIGHT는 왼쪽 묶음 위). 둘 다 글자 키 전체 높이를 바꾼다.
        HEIGHT_RIGHT(true, L.t("글자 키 높이", "Letter key height"), Prefs.KEYBOARD_HEIGHT);

        /** 위아래로 끄는지 (아니면 좌우). */
        final boolean vertical;
        final String label;
        private final String key;

        Handle(boolean vertical, String label, String key) {
            this.vertical = vertical;
            this.label = label;
            this.key = key;
        }

        /** 두 묶음의 손잡이(경계·↔ 버튼)인지. */
        boolean balanced() {
            return this == B_LEFT_START || this == B_LEFT_END || this == B_RIGHT_START || this == B_RIGHT_END || knob();
        }

        /** 동그란 ↔ 버튼으로 그리는 손잡이 (묶음 가로 위치). */
        boolean knob() {
            return this == B_LEFT_MOVE || this == B_RIGHT_MOVE;
        }

        /** 왼쪽(자음) 묶음의 손잡이인지. */
        boolean leftBlock() {
            return this == B_LEFT_START || this == B_LEFT_END || this == B_LEFT_MOVE;
        }

        /** 높이 손잡이인지 (도구 막대·숫자 줄·글자 키·기능키). */
        boolean height() {
            return this == TOOLBAR || this == NUMBER_ROW || this == HEIGHT || this == HEIGHT_RIGHT || this == FN_ROW;
        }

        /** 값 뒤에 붙는 단위: 높이와 묶음 폭은 %, 여백은 dp, 묶음 가로 위치는 없음. */
        String unit() {
            if (knob()) return "";
            return "%";
        }
    }

    /** 손잡이 h가 바꾸는 설정 키. 두 묶음의 경계는 폭 키 (가로 위치 키도 함께 바꾼다). */
    private String handleKey(Handle h) {
        switch (h) {
            case B_LEFT_START:
            case B_LEFT_END: return prefs.balancedConsonantWidthKey();
            case B_RIGHT_START:
            case B_RIGHT_END: return prefs.balancedVowelWidthKey();
            case B_LEFT_MOVE: return prefs.balancedConsonantPosKey();
            case B_RIGHT_MOVE: return prefs.balancedVowelPosKey();
            case HEIGHT:
            case HEIGHT_RIGHT: return ownSizes() ? prefs.heightKey() : h.key;
            case FN_ROW: return ownSizes() ? prefs.fnRowHeightKey() : h.key;
            case NUMBER_ROW: return ownSizes() ? prefs.numberRowHeightKey() : h.key;
            case TOOLBAR: return ownSizes() ? prefs.toolbarHeightKey() : h.key;
            default: return h.key;
        }
    }

    /**
     * 높이 손잡이가 세로 모드와 따로 정하는 값(그 프로필의 키)을 바꾸는지: 가로 모드·폴더블 내부 화면 키보드를
     * 편집할 때 (그 키보드의 보기를 받은 미리보기).
     */
    private boolean ownSizes() {
        return prefs.profileSize();
    }

    /** 끄는 동안 보이는 이름. 두 묶음의 경계는 그 묶음의 폭으로 보인다. */
    private String handleName(Handle h) {
        if (h.balanced()) {
            // 영어·기호 자판의 균형 배열도 자음·모음이 아니라 왼쪽·오른쪽 묶음이다.
            boolean split = prefs.splitView() || prefs.board() != Prefs.BOARD_KOREAN;
            if (split) return h.leftBlock()
                    ? (h.knob() ? L.t("왼쪽 묶음 가로 위치", "Left block position") : L.t("왼쪽 묶음 폭", "Left block width"))
                    : (h.knob() ? L.t("오른쪽 묶음 가로 위치", "Right block position") : L.t("오른쪽 묶음 폭", "Right block width"));
            return h.leftBlock()
                    ? (h.knob() ? L.t("자음 가로 위치", "Consonant position") : L.t("자음 키 폭", "Consonant key width"))
                    : (h.knob() ? L.t("모음 가로 위치", "Vowel position") : L.t("모음 키 폭", "Vowel key width"));
        }
        return h.label;
    }

    /** 분리 키보드의 왼쪽·오른쪽 묶음과 그 사이 가운데 공간의 윤곽선 (미리보기 좌표). 없으면 false. */
    private boolean splitAreaBounds(int area, RectF out) {
        switch (area) {
            case AREA_LEFT: return blockBounds(true, out);
            case AREA_RIGHT: return blockBounds(false, out);
            case AREA_MIDDLE: {
                // 두 묶음 사이의 빈 곳 (글자 줄 높이). 묶음이 맞닿아 빈 곳이 거의 없으면 없다.
                RectF l = new RectF(), r = new RectF();
                if (!blockBounds(true, l) || !blockBounds(false, r)) return false;
                float gap = Ui.dp(getContext(), 2);
                out.set(l.right + gap, Math.min(l.top, r.top), r.left - gap, Math.max(l.bottom, r.bottom));
                return out.width() >= Ui.dp(getContext(), 12);
            }
            default:
                return false;
        }
    }

    /**
     * 키보드 사용자화(영역 층)에서 분리 키보드로 나뉜 글자 키를 왼쪽·오른쪽 묶음과 가운데 공간으로 그리는지
     * ('분리 키보드 사용'을 켰고 한글·영어 자판일 때).
     */
    private boolean editorSplit() {
        return prefs.splitView()
                && blockBounds(true, new RectF()) && blockBounds(false, new RectF());
    }

    /**
     * 키보드 사용자화(영역 층)에서 한글 글자 키를 자·모음 균형 배열의 자음·모음 쪽 윤곽선으로 그리는지
     * (분리 키보드 없이 자·모음 균형 배열을 쓰고 한글 자판일 때).
     */
    private boolean editorBalanced() {
        if (prefs.splitView()) return false;
        // 한글·영어·기호 자판이 상세 배열을 따로 정한다 (prefs는 탭의 자판 보기: 따라가기면 한글 자판의 것).
        boolean on = tab == TAB_KOREAN ? prefs.balancedLayout() : prefs.boardBalanced();
        return on && blockBounds(true, new RectF()) && blockBounds(false, new RectF());
    }

    /** 키보드 사용자화가 받은 설정 보기 (한글 자판의 것). refresh가 탭에 맞는 자판의 보기(boardView)를 prefs에 둔다. */
    private final Prefs basePrefs;

    /** 글자 키가 두 묶음으로 나뉘어 그려지는지 (키보드 사용자화의 분리 키보드·자·모음 균형 배열, 가로 모드·폴더블 내부 화면의 분리 키보드). */
    private boolean splitLetters() {
        return editorSplit() || editorBalanced();
    }

    /** 키보드 사용자화에서 고른 영역이 자·모음 균형 배열의 글자 키(자음·모음 쪽)인지. */
    private boolean editorBalancedSelected() {
        return editorBalanced() && selection != null && selection.kind == SEL_AREA && selection.area() == AREA_LETTERS;
    }

    /** 키보드 사용자화에서 고른 영역이 분리 키보드의 글자 키(두 묶음)인지. */
    private boolean editorBlocksSelected() {
        return editorSplit() && selection != null && selection.kind == SEL_AREA && selection.area() == AREA_LETTERS;
    }

    /** 글자 줄 하나의 왼쪽·오른쪽 끝 (미리보기 좌표, 줄 전체 10칸). 자판이 없으면 false. */
    private boolean letterRowSpan(float[] out) {
        KeyboardLayout l = keys.getLayout();
        if (l == null) return false;
        int r = numberRowShown ? 1 : 0;
        if (r >= l.rows.length || l.rows[r].keys.length == 0) return false;
        Key[] ks = l.rows[r].keys;
        out[0] = ks[0].rect.left;
        out[1] = ks[ks.length - 1].rect.right;
        return out[1] > out[0];
    }

    /** 자·모음 경계선의 가로 위치 (미리보기 좌표). */
    private float boundaryX() {
        float[] span = new float[2];
        if (!letterRowSpan(span)) return getWidth() / 2f;
        return (span[0] + span[1]) / 2f;   // 늘 가운데에서 나눈다
    }

    /**
     * 균형 배열의 자음(consonant) 또는 모음 쪽 키들을 감싼 윤곽선 (Shift·⌫ 포함). 없으면 false.
     * 두 쪽이 맞닿아(폭 100% 등) 윤곽선이 겹치거나 너무 붙으면, 맞닿은 곳을 가운데로 두고 둘 사이에 2dp를 띄운다.
     */
    boolean blockBounds(boolean consonant, RectF out) {
        if (!rawBlockBounds(consonant, out)) return false;
        RectF other = new RectF();
        if (!rawBlockBounds(!consonant, other)) return true;
        float gap = Ui.dp(getContext(), 2);
        float leftEdge = consonant ? out.right : other.right, rightEdge = consonant ? other.left : out.left;
        if (leftEdge + gap > rightEdge) {
            float mid = (leftEdge + rightEdge) / 2f;
            if (consonant) out.right = Math.min(out.right, mid - gap / 2f);
            else out.left = Math.max(out.left, mid + gap / 2f);
        }
        return true;
    }

    /**
     * (x, y)가 쿼티 모양 분리 자판(영어·두벌식) 두 묶음의 글자 키 위인지. 엇갈린 g·v는 왼쪽 묶음 윤곽선 밖으로 조금 나와 있어
     * 윤곽선만 보면 가운데 공간을 누른 것처럼 보이므로, 영역을 고를 때 키 자리를 먼저 본다.
     */
    private boolean onStaggeredBlockKey(float x, float y) {
        KeyboardLayout l = keys.getLayout();
        if (l == null || !l.staggerOutline) return false;
        int first = numberRowShown ? 1 : 0;
        float ky = y - keys.getTop();
        for (int r = first; r <= l.rows.length - 2; r++) {
            for (Key k : l.rows[r].keys) {
                if (k.type == Key.SPACER || k.type == Key.GAP || isHidden(k)) continue;
                if (k.rect.contains(x, ky)) return true;
            }
        }
        return false;
    }

    /** blockBounds에서 두 쪽 사이의 간격을 맞추기 전의 윤곽선. */
    private boolean rawBlockBounds(boolean consonant, RectF out) {
        KeyboardLayout l = keys.getLayout();
        // 분리 키보드(가로 모드·폴더블 내부 화면)는 두벌식도 가운데에서 나눠 묶음으로 놓는다.
        if (l == null || !(prefs.splitView() || prefs.balancedLayout())) return false;
        boolean split = prefs.splitView();
        Context c = getContext();
        float pad = outlinePadX(c), bx = boundaryX();
        float gx = Ui.dp(c, prefs.keyGapXDp()) / 2f, gy = Ui.dp(c, prefs.keyGapYDp()) / 2f;
        // 분리 키보드의 두 묶음에는 숫자 줄(1~5, 6~0)도 들어간다. 세로 모드의 균형 배열은 숫자 줄을 뺀 글자 키만 감싼다.
        int first = numberRowShown ? 1 : 0, last = l.rows.length - 2;
        // 쿼티 모양 자판(영어의 분리 키보드·균형 배열, 두벌식 분리 키보드)과 기호 자판(분리 키보드·균형 배열)은 줄이 엇갈려
        // g·v(기호 자판은 :)가 왼쪽 묶음 끝 밖으로 나와 있다. 왼쪽 묶음 윤곽선의 오른쪽 가장자리는 맨 윗 글자 줄의 t(기호 자판은 ^ 또는 5)에
        // 맞춘다: 한글 자판의 묶음 윤곽선과 같은 자리다. 오른쪽 묶음은 y(= 또는 6)가 묶음 시작에 있어 그대로 감싼다.
        boolean stagger = l.staggerOutline && consonant;
        int topLetters = last - 2;
        float inner = Float.NEGATIVE_INFINITY;
        boolean any = false;
        for (int r = first; r <= last; r++) {
            // 분리 키보드: 줄 가운데의 빈틈(SPACER)이 두 묶음을 가른다. 묶음이 맞닿아 빈틈이 없으면(폭 100%) 가운데에 걸친 키
            // (g·v 등)의 중심이 경계선과 정확히 같을 수 있어, 경계선 위의 키는 왼쪽 묶음으로 본다.
            int cut = -1;
            if (split) {
                Key[] rowKeys = l.rows[r].keys;
                for (int i = 0; i < rowKeys.length; i++) if (rowKeys[i].isDragCursor()) cut = i;
            }
            Key[] rowKeys = l.rows[r].keys;
            // 키가 어느 쪽 묶음 것인지 자판이 표시해 둔다 (한쪽이 가운데를 넘어 옮겨 가도 맞게 감싼다).
            boolean marked = false;
            for (Key k : rowKeys) marked |= k.isRightBlock();
            for (int i = 0; i < rowKeys.length; i++) {
                Key k = rowKeys[i];
                if (k.type == Key.SPACER || k.type == Key.GAP || isHidden(k)) continue;
                boolean isLeft = cut >= 0 ? i < cut : marked ? !k.isRightBlock()
                        : split ? k.rect.centerX() <= bx + 1f : k.rect.centerX() < bx;
                if (isLeft != consonant) continue;
                float left = k.rect.left + gx, top = keys.getTop() + k.rect.top + gy;
                float right = k.rect.right - gx, bottom = keys.getTop() + k.rect.bottom - gy;
                if (!any) out.set(left, top, right, bottom);
                else out.union(left, top, right, bottom);
                any = true;
                if (stagger && r <= topLetters) inner = Math.max(inner, right);
            }
        }
        if (!any) return false;
        if (stagger && inner > out.left) out.right = inner;
        out.inset(-pad, -outlinePadY(c));
        return true;
    }

    /** 읽는 설정: refresh()가 키보드 사용자화가 받은 보기(basePrefs)에서 지금 탭 자판의 보기로 바꾼다. */
    private Prefs prefs;
    private final Listener listener;
    private final LinearLayout column;
    private final LinearLayout bar;
    private final KeyboardView keys;
    private final Overlay overlay;
    private KeyboardTheme theme;
    private int tab = TAB_KOREAN;
    private boolean symbolsPage2;
    private Selection selection;
    /** 고른 키 (자판을 다시 만들 때마다 다시 찾는다). */
    private Key selectedKey;
    /** 지금 자판에 숫자 줄이 있는지 (숫자 줄 손잡이를 둘지). */
    private boolean numberRowShown;

    // ---------------------------------------------------------------- 고른 영역을 가운데로

    /**
     * 가운데로 끌어온 영역 (AREA_*, 없으면 -1). 영역이나 그 안의 키·버튼을 고르면 그 영역이 미리보기 세로 가운데로 미끄러져 오고,
     * 위아래의 다른 영역은 흐려지며, 그 영역의 키·버튼마다 윤곽선이 그려져 하나씩 고를 수 있다.
     */
    private int focusArea = -1;
    /** 바로 앞에 끌어와 있던 영역: 옮겨 가는 동안 그 영역의 키 윤곽선이 흐려지며 영역 윤곽선으로 돌아간다. */
    private int fromArea = -1;
    /** 옮겨 가는 움직임의 진행 (0 = 출발, 1 = 도착). 움직이지 않을 때는 1. */
    private float focusT = 1f;
    /** 움직임을 시작할 때 그리던 모습: 자판을 내린 거리(px), 흐림 정도(0~1), 흐리지 않은 구멍(자판 좌표). */
    private float shiftFrom, dimFrom;
    private final RectF holeFrom = new RectF();
    private boolean holeFromValid;
    private android.animation.ValueAnimator focusAnim;
    /**
     * 고른 영역의 굵은 윤곽선 진하기 (0~1): 영역 자체를 고르면 1, 그 안의 키·버튼을 고르면 0으로 짧게 흐려진다.
     * 옮겨 가는 동안 떠나는 영역의 윤곽선은 fromOutline에서 함께 흐려진다.
     */
    private float areaOutline, fromOutline;
    private android.animation.ValueAnimator outlineAnim;
    /** 흐린 곳을 덮는 바탕색의 진하기. */
    private static final float SCRIM = 0.62f;

    /** 가운데로 끌어온 영역 (AREA_*, 없으면 -1). */
    int focusArea() {
        return focusArea;
    }

    /** 고른 것이 속한 영역: 영역은 그 영역, 키는 그 키가 있는 줄의 영역, 도구 막대 버튼은 도구 막대. 없으면 -1. */
    private int focusAreaOf(Selection s) {
        if (s == null) return -1;
        switch (s.kind) {
            case SEL_AREA: return s.area();
            case SEL_TOOL_BUTTON:
            case SEL_TOOLBAR: return AREA_TOOLBAR;
            case SEL_KEY: return selectedKey == null ? -1 : areaOfKey(selectedKey);
            default: return -1;
        }
    }

    /** 키가 있는 영역: 숫자 줄(맨 윗줄, 숫자 줄을 켰을 때), 기능키(맨 아랫줄), 나머지는 글자 키. */
    private int areaOfKey(Key key) {
        KeyboardLayout l = keys.getLayout();
        if (l == null) return -1;
        for (int r = 0; r < l.rows.length; r++) {
            for (Key k : l.rows[r].keys) {
                if (k != key) continue;
                if (numberRowShown && r == 0) return AREA_NUMBER;
                return r == l.rows.length - 1 ? AREA_FUNCTION : AREA_LETTERS;
            }
        }
        return -1;
    }

    /** 영역의 줄 범위 {첫 줄, 끝 줄} (자판의 줄 번호). 줄이 없는 영역(도구 막대·가운데 공간·꺼 둔 숫자 줄)이면 false. */
    private boolean rowRange(int area, int[] out) {
        KeyboardLayout l = keys.getLayout();
        if (l == null || l.rows.length == 0) return false;
        int letterStart = numberRowShown ? 1 : 0;
        switch (area) {
            case AREA_NUMBER:
                if (!numberRowShown) return false;
                out[0] = out[1] = 0;
                break;
            case AREA_LETTERS:
                out[0] = letterStart;
                out[1] = l.rows.length - 2;
                break;
            case AREA_FUNCTION:
                out[0] = out[1] = l.rows.length - 1;
                break;
            default:
                return false;
        }
        return out[1] >= out[0];
    }

    /**
     * 가운데로 끌어올 때 맞추는 영역의 자리 (자판 좌표): 영역 윤곽선과 같다. 두 묶음으로 나뉜 글자 키는 두 묶음을 합친 것.
     * 없으면 false.
     */
    private boolean focusBounds(int area, RectF out) {
        if (area == AREA_MIDDLE) return splitAreaBounds(AREA_MIDDLE, out);
        if (area == AREA_LETTERS && splitLetters()) {
            RectF r = new RectF();
            if (!blockBounds(true, out) || !blockBounds(false, r)) return false;
            out.union(r);
            return true;
        }
        return areaBounds(area, out);
    }

    /**
     * 흐리지 않고 남기는 구멍 (자판 좌표): 고른 영역의 줄 전체 (가로로는 미리보기 끝까지). 분리 키보드의 가운데 공간은
     * 두 묶음 사이만 남긴다. 고른 영역 안을 누르면 그 영역의 키·버튼을 고르는 자리이기도 하다.
     */
    private boolean focusHole(int area, RectF out) {
        if (area < 0 || !focusBounds(area, out)) return false;
        if (area != AREA_MIDDLE) {
            out.left = 0;
            out.right = getWidth();
        }
        return true;
    }

    /** 고른 영역을 가운데로 가져오려면 자판을 내릴 거리 (px, 위로 올리면 음수). 고른 영역이 없으면 0. */
    private float targetShift() {
        RectF r = new RectF();
        if (focusArea < 0 || getHeight() == 0 || !focusBounds(focusArea, r)) return 0f;
        // 글자 키는 미리보기 위쪽에 자판 탭이 뜨므로 그 아래 남은 자리의 가운데에 둔다.
        float top = focusArea == AREA_LETTERS ? tabsReserve() : 0f;
        return (top + getHeight()) / 2f - r.centerY();
    }

    // ---------------------------------------------------------------- 자판 탭 (글자 키를 고르면 미리보기 위쪽에)

    /** 한글·영어·기호 자판 탭 (설정 화면이 만들어 준다). 글자 키를 가운데로 끌어온 동안만 미리보기 안쪽 위에 떠 있다. */
    private View tabsView;
    /** 자판 탭 띠 아래로 글자 키까지 두는 틈. */
    private static final int TABS_GAP_DP = 6;

    /**
     * 자판 탭 띠를 미리보기 안쪽 맨 위에 가로 끝까지 놓는다 (처음에는 숨겨 둔다). 띠는 불투명한 바탕을 가져
     * 그 뒤의 흐려진 미리보기를 가린다.
     */
    void setTabsView(View v) {
        tabsView = v;
        v.setLayoutParams(new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, android.view.Gravity.TOP));
        v.setAlpha(0f);
        v.setVisibility(GONE);
        addView(v);
        applyFocus();
    }

    /** 자판 탭이 차지하는 미리보기 위쪽 높이 (탭이 없으면 0). */
    private float tabsReserve() {
        if (tabsView == null) return 0f;
        Context c = getContext();
        return Ui.dp(c, TABS_GAP_DP) + tabsView.getMeasuredHeight();
    }

    /** 자판 탭을 글자 키를 끌어온 만큼 보이게 한다 (위에서 살짝 내려오며 또렷해진다). */
    private void applyTabs() {
        if (tabsView == null) return;
        float a = focusWeight(AREA_LETTERS);
        tabsView.setAlpha(a);
        tabsView.setTranslationY(-(1f - a) * Ui.dp(getContext(), 8));
        int vis = a > 0.01f ? VISIBLE : GONE;
        if (tabsView.getVisibility() == vis) return;
        // 배치 중(크기가 바뀐 column의 알림)에 보이기를 바꾸면 그 배치 안에서 다시 배치를 청하게 되므로 다음 차례로 미룬다.
        if (isInLayout()) post(this::applyTabs);
        else tabsView.setVisibility(vis);
    }

    private static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }

    /** 지금 흐린 정도 (0 = 흐리지 않음, 1 = 다 흐림). */
    private float dimNow() {
        return lerp(dimFrom, focusArea >= 0 ? 1f : 0f, focusT);
    }

    /** 지금 흐리지 않은 구멍 (자판 좌표). 출발 자리에서 도착 자리로 옮겨 간다. 구멍이 없으면 false. */
    private boolean holeNow(RectF out) {
        if (focusArea < 0) {   // 흐림이 걷히는 중: 구멍은 출발 자리에 둔 채 흐림만 옅어진다
            if (!holeFromValid) return false;
            out.set(holeFrom);
            return true;
        }
        if (!focusHole(focusArea, out)) return false;
        if (focusT < 1f && holeFromValid) {
            out.set(lerp(holeFrom.left, out.left, focusT), lerp(holeFrom.top, out.top, focusT),
                    lerp(holeFrom.right, out.right, focusT), lerp(holeFrom.bottom, out.bottom, focusT));
        }
        return true;
    }

    /**
     * 영역 a를 얼마나 끌어왔는지 (0~1): 도착하는 영역은 움직임을 따라 0 → 1, 떠나는 영역은 1 → 0.
     * 그만큼 영역 윤곽선이 키 윤곽선으로 바뀐다.
     */
    private float focusWeight(int a) {
        if (a < 0) return 0f;
        float w = 0f;
        if (a == focusArea) w += focusT;
        if (a == fromArea && fromArea != focusArea) w += 1f - focusT;
        return Math.min(1f, w);
    }

    /**
     * 고른 것이 속한 영역이 바뀌었으면 그 영역을 가운데로 가져오는 움직임을 시작한다 (M3 강조 감속 곡선).
     * 움직이는 중에 다시 바뀌어도 지금 그린 자리에서 이어서 움직여 튀지 않는다.
     */
    private void updateFocus() {
        int target = focusAreaOf(selection);
        animateOutline(selection != null && selection.kind == SEL_AREA && target >= 0);
        if (target == focusArea) {
            applyFocus();
            return;
        }
        fromOutline = areaOutline;
        shiftFrom = column.getTranslationY();
        dimFrom = dimNow();
        holeFromValid = holeNow(holeFrom);
        fromArea = focusArea;
        focusArea = target;
        if (focusAnim != null) focusAnim.cancel();
        focusAnim = null;
        if (getWidth() == 0 || !isAttachedToWindow()) {   // 아직 그리기 전: 움직이지 않고 바로 그 자리에
            focusT = 1f;
            applyFocus();
            return;
        }
        focusT = 0f;
        android.animation.ValueAnimator a = android.animation.ValueAnimator.ofFloat(0f, 1f);
        // 들어갈 때는 조금 길고 부드럽게, 돌아올 때는 짧게 (M3: 화면에 들어오는 움직임이 나가는 움직임보다 길다).
        a.setDuration(target >= 0 ? (fromArea >= 0 ? 380 : 450) : 320);
        a.setInterpolator(new android.view.animation.PathInterpolator(0.2f, 0f, 0f, 1f));
        a.addUpdateListener(an -> {
            focusT = (float) an.getAnimatedValue();
            applyFocus();
        });
        a.addListener(new android.animation.AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(android.animation.Animator an) {
                if (focusAnim != an) return;
                focusAnim = null;
                fromArea = focusArea;
                overlay.updateExclusion();
            }
        });
        focusAnim = a;
        a.start();
    }

    /** 굵은 영역 윤곽선을 보이거나(영역을 골랐다) 흐려 없앤다(키·버튼을 골랐다). */
    private void animateOutline(boolean on) {
        float target = on ? 1f : 0f;
        if (outlineAnim != null && outlineAnim.isRunning()) {
            if (((Float) outlineAnim.getAnimatedValue()) == target) return;
        } else if (areaOutline == target) {
            return;
        }
        if (outlineAnim != null) outlineAnim.cancel();
        outlineAnim = android.animation.ValueAnimator.ofFloat(areaOutline, target);
        outlineAnim.setDuration(160);
        outlineAnim.setInterpolator(new android.view.animation.PathInterpolator(0.2f, 0f, 0f, 1f));
        outlineAnim.addUpdateListener(a -> {
            areaOutline = (float) a.getAnimatedValue();
            overlay.invalidate();
        });
        outlineAnim.start();
    }

    /** 지금 진행대로 자판을 내리고(translationY) 덮개를 다시 그린다. 덮개는 같은 거리만큼 내려 그린다. */
    private void applyFocus() {
        float shift = lerp(shiftFrom, targetShift(), focusT);
        if (column.getTranslationY() != shift) column.setTranslationY(shift);
        applyTabs();
        overlay.invalidate();
    }

    KeyboardPreview(Context context, Prefs prefs, Listener listener) {
        super(context);
        this.prefs = prefs;
        this.basePrefs = prefs;
        this.listener = listener;
        column = new LinearLayout(context);
        column.setOrientation(LinearLayout.VERTICAL);
        bar = new LinearLayout(context);
        bar.setGravity(android.view.Gravity.CENTER_VERTICAL);
        keys = new KeyboardView(context);
        keys.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        bar.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
        addView(column, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        keys.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> {
            if (r - l != or - ol) updateBarEdges();
        });
        // 크기가 바뀌면(손잡이·슬라이더로 높이를 바꾸는 등) 고른 영역이 계속 가운데에 오게 다시 맞춘다.
        column.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> applyFocus());
        overlay = new Overlay(context);
        addView(overlay, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        refresh();
    }

    /**
     * 미리보기를 줄여 보이는 배율 (설정 창을 화면 왼쪽 절반에 띄우는 동안 오른쪽 절반으로 줄인다). 손잡이는 화면 좌표로 끄는 거리를
     * 재므로, 이 배율로 나눠 손가락 아래의 손잡이가 손가락을 그대로 따라오게 한다.
     */
    private float touchScale = 1f;

    void setTouchScale(float scale) {
        touchScale = scale > 0f ? scale : 1f;
    }

    /** 크기는 도구 막대 + 자판이 정한다. 위에 덮은 손잡이 층은 그 크기를 그대로 따른다 (덮개가 높이를 키우지 않게). */
    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        measureChild(column, widthMeasureSpec, MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
        int w = column.getMeasuredWidth(), h = column.getMeasuredHeight();
        overlay.measure(MeasureSpec.makeMeasureSpec(w, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(h, MeasureSpec.EXACTLY));
        // 자판 탭은 제 높이대로 (숨겨 둔 동안에도 재어 두어, 글자 키를 고를 때 그 아래 자리를 미리 안다).
        if (tabsView != null) {
            measureChildWithMargins(tabsView, MeasureSpec.makeMeasureSpec(w, MeasureSpec.EXACTLY), 0,
                    MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED), 0);
        }
        setMeasuredDimension(w, h);
    }

    void setTab(int t) {
        if (tab == t) return;
        tab = t;
        symbolsPage2 = false;
        // 영역을 고른 채면 다른 자판에서도 그대로 둔다 (그 자판에 없는 영역이면 refresh가 푼다). 키는 자판마다 달라
        // 그 키가 있던 영역을 고른 것으로 바꾼다 (자판 탭은 글자 키를 가운데로 끌어왔을 때만 보이므로, 탭을 바꿔도 글자 키에 머문다).
        if (selection != null && selection.kind != SEL_AREA) selection = focusArea >= 0 ? Selection.area(focusArea) : null;
        refresh();
    }

    int tab() {
        return tab;
    }

    /**
     * 고른 것을 바꾼다 (null = 고르지 않음). 자판에 없는 키면 고르지 않은 것으로 본다.
     * 고른 것이 속한 영역이 바뀌면 그 영역을 가운데로 가져온다 (null이면 기본 상태로 돌아온다).
     */
    void select(Selection s) {
        selection = s;
        resolveSelection();
        updateFocus();
        overlay.invalidate();
        overlay.post(overlay::updateExclusion);   // 보이는 손잡이가 바뀌었다
    }

    Selection selection() {
        return selection;
    }

    /** 고른 키 (SEL_KEY일 때). 없으면 null. */
    Key selectedKey() {
        return selectedKey;
    }

    /** 지금 미리보기가 그린 자판. */
    KeyboardLayout layout() {
        return keys.getLayout();
    }

    /** 설정을 다시 읽어 모양·크기·자판을 맞춘다. 같은 값이면 뷰는 그대로라 자주 불러도 된다. */
    void refresh() {
        Context c = getContext();
        // 키보드 사용자화: 상세 배열(자·모음 균형 배열·분리 키보드)은 한글·영어·기호 자판이 따로 정한다 (영어·기호 자판은 한글 자판
        // 따라가기면 한글 자판의 보기). 손잡이도 그 자판의 값을 바꾼다.
        prefs = basePrefs.boardView(tab == TAB_ENGLISH ? Prefs.BOARD_ENGLISH : tab == TAB_SYMBOLS ? Prefs.BOARD_SYMBOLS : Prefs.BOARD_KOREAN);
        theme = KeyboardTheme.of(c);
        setBackgroundColor(theme.background);
        keys.setTheme(theme);
        keys.configure(prefs.longPressMs(), prefs.swipeThresholdDp(), false,
                prefs.keyTextSize());
        keys.setKeyShadow(prefs.keyShadow(), prefs.keyShadowStrength());
        keys.setKeyRadius(Ui.dp(c, prefs.keyRadiusDp()));
        keys.setGridColors(prefs.gridColors());
        keys.setPopupHints(!prefs.popupHintHidden(), prefs.popupHintPosition());
        keys.setDubeolsikHint(prefs.dubeolsikHintShown());
        keys.setNumberHintHidden(prefs.numberHintHidden());
        keys.setRepeatChars(prefs.repeatChars());
        keys.setKeyGaps(Ui.dp(c, prefs.keyGapXDp()), Ui.dp(c, prefs.keyGapYDp()));
        int padT = Ui.dp(c, prefs.padTopDp()), padB = Ui.dp(c, prefs.padBottomDp());
        // 키보드와 같다: 도구 막대를 자판 아래에 두면 자판의 아래쪽 여백은 도구 막대 밑으로 옮긴다 (전체 높이는 그대로).
        int lift = barLifted() ? padB : 0;
        // 위쪽 여백도 키보드 전체의 바깥 여백: 도구 막대가 자판 위에 있으면 도구 막대 위로 옮긴다 (키보드와 같다).
        int raise = barRaised() ? padT : 0;
        keys.setInsets(Ui.dp(c, prefs.padLeftDp()), padT - raise, Ui.dp(c, prefs.padRightDp()), padB - lift);
        float[][] areaPads = new float[AREAS][4];
        for (int a = 0; a < AREAS; a++) for (int side = 0; side < 4; side++) areaPads[a][side] = Ui.dp(c, prefs.areaPadDp(a, side));
        keys.setAreaInsets(areaPads);
        keys.setSpaceLabel(prefs.spaceLangLabel() ? (tab == TAB_ENGLISH ? "English" : tab == TAB_KOREAN ? "한국어" : null) : null);
        KeyboardLayout l;
        if (tab == TAB_ENGLISH) l = KeyboardLayout.english(prefs);
        else if (tab == TAB_SYMBOLS) l = symbolsPage2 ? KeyboardLayout.symbols2(true, prefs) : KeyboardLayout.symbols(true, prefs);
        else l = KeyboardLayout.korean(prefs);
        // 분리 키보드는 묶음 앞에 빈틈(GAP)이 있을 수 있어, 앞의 빈틈은 건너뛰고 첫 키가 숫자인지 본다.
        Key firstKey = null;
        if (l.rows.length > 0) {
            for (Key k : l.rows[0].keys) {
                if (k.type == Key.GAP || k.type == Key.SPACER) continue;
                firstKey = k;
                break;
            }
        }
        // 기호 자판도 숫자 줄을 켜면(5줄 배열) 첫 줄을 숫자 줄로 다룬다: 글자 키와 따로 '숫자 줄' 영역이다 (한글·영어 자판과 같다).
        numberRowShown = prefs.numberRow() && firstKey != null && KeyboardLayout.isDigit(firstKey.label);
        // 키보드와 같은 높이: 글자 4줄 + 숫자 줄 (기호 자판도 같은 높이를 쓴다).
        float rows = KeyboardLayout.rowUnits(prefs);
        keys.setKeyboardHeight(Math.round(Ui.dp(c, 54) * rows * prefs.keyboardHeight() / 100f) + padT + padB - lift - raise
                + Ui.dp(c, prefs.areaPadVerticalDp()));
        keys.setLayout(l);
        rebuildToolbar(lift, raise);
        resolveSelection();
        updateFocus();   // 고른 키가 없어졌거나 다른 영역으로 옮겨 갔다 (숫자 줄을 껐다 등)
        overlay.invalidate();
        overlay.post(overlay::updateExclusion);   // 여백이 바뀌면 손잡이 자리도 바뀐다 (배치가 끝난 뒤에 잰다)
    }

    /** 도구 막대와 자판의 배치를 마지막으로 만든 설정 (같으면 뷰를 다시 만들지 않는다: 손잡이를 끄는 동안 자주 불린다). */
    private String barSignature, columnSignature;

    /**
     * 도구 막대 줄이 보이는지. '도구 막대 표시'를 꺼도 단어 추천을 쓰면 키보드는 그 줄(버튼 없이)을 남겨 둔다.
     * 단축어는 입력할 때만 그 줄이 잠깐 나타나므로(자동 완성 칩처럼) 평소 모습인 미리보기에서는 따지지 않는다.
     * 일본어·중국어 자판(실험실)을 켜도 변환 후보 자리로 남지만, 미리보기는 한글·영어·기호 자판이라 따지지 않는다.
     */
    boolean barShown() {
        return prefs.toolbar() || prefs.suggestWords();
    }

    /** 도구 막대를 자판 아래에 두어 아래쪽 여백이 도구 막대 밑으로 옮겨졌는지. */
    /** 도구 막대 줄이 자판 위에 있어 위쪽 여백을 그 위로 옮기는지. */
    private boolean barRaised() {
        return barShown() && !prefs.toolbarBottom();
    }

    private boolean barLifted() {
        return barShown() && prefs.toolbarBottom();
    }

    /** 도구 막대 줄이 아예 없을 때 그 자리를 알리는 띠 (실제 키보드에는 없다): 눌러서 도구 막대 설정을 연다. */
    private android.widget.TextView placeholder;
    /** 숫자 줄을 꺼 두었을 때 그 자리를 알리는 띠 (키보드 사용자화에만, 실제 키보드에는 없다): 눌러서 숫자 줄 설정을 연다. */
    private android.widget.TextView numberPlaceholder;

    private android.widget.TextView placeholder() {
        if (placeholder == null) placeholder = dashedStrip();
        return placeholder;
    }

    private android.widget.TextView numberPlaceholder() {
        if (numberPlaceholder == null) numberPlaceholder = dashedStrip();
        return numberPlaceholder;
    }

    /**
     * 꺼 둔 영역의 빈 자리 (뷰는 자리만 차지한다). 점선 테두리와 안내 글씨는 덮개가 그 영역의 윤곽선 자리에 그린다
     * (drawOffStrips): 다른 영역 윤곽선과 꼭 맞게.
     */
    private android.widget.TextView dashedStrip() {
        return new android.widget.TextView(getContext());
    }

    /** 숫자 줄 빈 칸이 미리보기에 놓여 있는지. */
    private boolean numberOff() {
        return numberPlaceholder != null && numberPlaceholder.getParent() != null;
    }

    /** 꺼 두어 빈 칸만 남은 영역인지 (도구 막대·숫자 줄). */
    private boolean areaOff(int a) {
        if (a == AREA_TOOLBAR) return placeholder != null && placeholder.getParent() != null;
        if (a == AREA_NUMBER) return numberOff();
        return false;
    }

    /**
     * 도구 막대의 왼쪽·오른쪽 여백 (px): 키보드 여백 + 도구 막대 영역 여백. 분리 키보드에서 '도구 막대가 묶음 좌우를 따라가기'를
     * 켰으면(기본) 왼쪽 묶음의 왼쪽 끝·오른쪽 묶음의 오른쪽 끝에 맞춘다 (실제 키보드와 같다).
     */
    private int barPadLeft() {
        Context c = getContext();
        float[] e = prefs.toolbarFollowBlocks() ? keys.keyExtent() : null;
        if (e != null) return Math.round(e[0]) + Ui.dp(c, prefs.areaPadDp(AREA_TOOLBAR, Prefs.SIDE_LEFT));
        return Ui.dp(c, prefs.padLeftDp() + prefs.areaPadDp(AREA_TOOLBAR, Prefs.SIDE_LEFT));
    }

    private int barPadRight() {
        Context c = getContext();
        float[] e = prefs.toolbarFollowBlocks() ? keys.keyExtent() : null;
        if (e != null) return Math.round(keys.getWidth() - e[1]) + Ui.dp(c, prefs.areaPadDp(AREA_TOOLBAR, Prefs.SIDE_RIGHT));
        return Ui.dp(c, prefs.padRightDp() + prefs.areaPadDp(AREA_TOOLBAR, Prefs.SIDE_RIGHT));
    }

    /** 키가 배치된 뒤 (크기가 정해진 뒤) 도구 막대의 좌우 여백을 다시 맞춘다. */
    private void updateBarEdges() {
        int l = barPadLeft(), r = barPadRight();
        if (bar.getPaddingLeft() == l && bar.getPaddingRight() == r) return;
        bar.setPadding(l, 0, r, 0);
        barSignature = null;   // 다음 refresh에서 버튼을 새 여백으로 다시 만든다
        overlay.invalidate();
    }

    /** 도구 막대: 실제와 같은 자리·순서·크기의 버튼들 (누를 수 없고, 누르면 그 버튼을 고른다). */
    private void rebuildToolbar(int lift, int raise) {
        Context c = getContext();
        int height = Math.round(Ui.dp(c, 40) * prefs.toolbarHeight() / 100f);
        float scale = prefs.toolButtonSize() / 100f;
        int width = Math.round(Ui.dp(c, 40) * scale);
        String[][] order = prefs.toolOrder();
        StringBuilder sig = new StringBuilder().append(height).append(',').append(width).append(',')
                .append(barPadLeft()).append(',').append(barPadRight()).append(',')
                .append(theme.background).append(',').append(theme.functionKey).append(',').append(theme.text).append('|');
        boolean buttons = prefs.toolbar();   // 꺼 두면 버튼 없이 추천 단어 자리만 남는다
        sig.append(buttons).append('|');
        for (String[] side : order) {
            for (String id : side) if (prefs.toolShown(id)) sig.append(id).append(',');
            sig.append('|');
        }
        if (!sig.toString().equals(barSignature)) {
            barSignature = sig.toString();
            bar.removeAllViews();
            bar.setPadding(barPadLeft(), 0, barPadRight(), 0);
            for (int side = 0; side < 2 && buttons; side++) {
                if (side == 1) bar.addView(new View(c), new LinearLayout.LayoutParams(0, 1, 1f));
                for (String id : order[side]) {
                    if (!prefs.toolShown(id)) continue;
                    IconButton b = new IconButton(c, ToolbarButtons.icon(id), theme.text, theme.keyPressed, null);
                    b.setIconSize(Ui.dp(c, 22) * scale);
                    b.setToolbarStyle(theme, height);
                    b.setClickable(false);
                    b.setFocusable(false);
                    b.setTag(id);
                    bar.addView(b, new LinearLayout.LayoutParams(width, height));
                }
            }
        }
        boolean shown = barShown();
        boolean bottom = prefs.toolbarBottom();
        int barT = Ui.dp(c, prefs.areaPadDp(AREA_TOOLBAR, Prefs.SIDE_TOP));
        int barB = Ui.dp(c, prefs.areaPadDp(AREA_TOOLBAR, Prefs.SIDE_BOTTOM));
        // 키보드 사용자화에서 숫자 줄을 꺼 두면 그 자리에 빈 칸을 남겨 다시 눌러 켤 수 있게 한다. 기호 자판도 숫자 줄을 켜면 첫 줄이
        // 숫자 줄 영역이라 한글·영어 자판과 똑같이 빈 칸을 둔다 (숫자 줄을 끈 기호 자판의 1~0 줄은 글자 키다).
        boolean numberGap = !prefs.numberRow();
        String colSig = shown + "," + bottom + "," + height + "," + lift + "," + raise + "," + barT + "," + barB + "," + numberGap;
        if (colSig.equals(columnSignature)) return;
        columnSignature = colSig;
        column.removeAllViews();
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, height);
        // 도구 막대 영역의 위·아래 여백은 막대 바로 둘레에, 키보드 전체 여백은 그 바깥에 온다.
        blp.bottomMargin = lift + barB;   // 자판 아래에 두면 아래쪽 여백이 도구 막대 밑에 온다
        blp.topMargin = raise + barT;     // 자판 위에 두면 위쪽 여백이 도구 막대 위에 온다
        LinearLayout.LayoutParams klp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        if (!shown) {
            column.addView(placeholder(), new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(c, 30)));
        } else if (!bottom) {
            column.addView(bar, blp);
        }
        if (numberGap) column.addView(numberPlaceholder(), new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(c, 30)));
        column.addView(keys, klp);
        if (shown && bottom) column.addView(bar, blp);
    }

    /** 고른 키를 지금 자판에서 다시 찾는다. 없으면 고르지 않은 것으로 돌린다. */
    private void resolveSelection() {
        selectedKey = null;
        if (selection == null) return;
        if (selection.kind == SEL_AREA && selection.area() == AREA_MIDDLE) {
            if (!editorSplit() || !splitAreaBounds(AREA_MIDDLE, new RectF())) selection = null;   // 분리 키보드를 껐다 등
            return;
        }
        if (selection.kind == SEL_AREA) {
            // 숫자 줄을 껐다 등. 숫자 줄 영역은 꺼져 있어도 고른 채로 둔다 (그 창에서 다시 켤 수 있게).
            if (!areaBounds(selection.area(), new RectF()) && selection.area() != AREA_NUMBER) selection = null;
            return;
        }
        if (selection.kind == SEL_TOOL_BUTTON) {
            if (!prefs.toolbar() || !prefs.toolShown(selection.toolId)) selection = null;
            return;
        }
        KeyboardLayout l = keys.getLayout();
        if (selection.tab != tab || l == null) {
            selection = null;
            return;
        }
        Key first = null;
        int seen = 0;
        for (KeyboardLayout.Row row : l.rows) {
            for (Key k : row.keys) {
                if (k.type != selection.keyType || !k.slot().equals(selection.slot)) continue;
                if (first == null) first = k;
                if (seen++ == selection.occurrence) {
                    selectedKey = k;
                    return;
                }
            }
        }
        selectedKey = first;
        if (first == null) selection = null;
    }

    /** 키를 고를 수 있는지 (빈 자리·빈틈·숨은 키·기호 쪽 넘김 키는 고르지 않는다). */
    private static boolean selectable(Key k) {
        return k.type != Key.SPACER && k.type != Key.GAP && k.type != Key.SYMBOL_PAGE && !isHidden(k);
    }

    /** 분리 키보드의 빈 자리에 숨겨 둔 키(g, v)는 그리지 않으므로 고르지 않는다. */
    private static boolean isHidden(Key k) {
        return k.isHidden() || k.rect.width() <= 0;
    }

    /** 키가 자판에서 몇 번째 같은 키인지 (분리 키보드의 두 스페이스바 등). */
    private int occurrenceOf(Key key) {
        int seen = 0;
        for (KeyboardLayout.Row row : keys.getLayout().rows) {
            for (Key k : row.keys) {
                if (k == key) return seen;
                if (k.type == key.type && k.slot().equals(key.slot())) seen++;
            }
        }
        return 0;
    }

    // ---------------------------------------------------------------- 눌러 고르기

    /**
     * (x, y)를 눌렀다 (자판 좌표: 가운데로 끌어오느라 내린 거리를 뺀 자리). 고른 영역의 줄 안이면 그 영역의 키·버튼을
     * (키가 아닌 곳이면 그 영역 자체를), 그 밖이면 누른 곳의 영역을 고른다. 영역 밖의 빈 곳이면 고른 것을 푼다.
     */
    private void tapAt(float x, float y) {
        // 조이스틱은 기능키 영역의 키지만 윗줄(글자 키)에 걸쳐 있다: 글자 키를 끌어온 때가 아니면 기능키 영역 쪽으로 친다.
        if (focusArea != AREA_LETTERS) {
            for (Key joy : joysticks()) {
                if (Math.hypot(x - joy.rect.centerX(), y - keys.getTop() - joy.rect.centerY()) > joy.rect.width() / 2f) continue;
                listener.onTap(focusArea == AREA_FUNCTION
                        ? new Selection(SEL_KEY, joy.type, joy.slot(), occurrenceOf(joy), null, tab) : Selection.area(AREA_FUNCTION));
                return;
            }
        }
        RectF hole = new RectF();
        if (focusArea >= 0 && focusHole(focusArea, hole) && hole.contains(x, y)) {
            listener.onTap(pickInFocus(x, y));
            return;
        }
        listener.onTap(areaAt(x, y));
    }

    /** 가운데로 끌어온 영역 안에서 누른 키·도구 막대 버튼. 키가 아닌 곳(여백·빈틈)이면 그 영역 자체. */
    private Selection pickInFocus(float x, float y) {
        if (focusArea == AREA_TOOLBAR) {
            if (bar.getParent() != null) {
                for (int i = 0; i < bar.getChildCount(); i++) {
                    View b = bar.getChildAt(i);
                    if (b.getTag() instanceof String && x >= b.getLeft() && x < b.getRight()) return Selection.tool((String) b.getTag());
                }
            }
            return Selection.area(AREA_TOOLBAR);
        }
        // 분리 키보드: 글자 키 줄 안이라도 두 묶음 사이의 가운데 공간은 따로 고른다 (윤곽선 밖으로 나온 g·v는 글자 키다).
        if (focusArea == AREA_LETTERS && editorSplit() && !onStaggeredBlockKey(x, y)) {
            RectF m = new RectF();
            if (splitAreaBounds(AREA_MIDDLE, m) && m.contains(x, y)) return Selection.area(AREA_MIDDLE);
        }
        Key k = keyIn(focusArea, x, y - keys.getTop());
        if (k == null) return Selection.area(focusArea);
        if (k.type == Key.SYMBOL_PAGE) {
            // 기호 자판의 1/2쪽 넘김: 다른 쪽을 보여 주고 영역은 그대로 고른 채 둔다 (고른 키는 다른 쪽에 없을 수 있다).
            symbolsPage2 = !symbolsPage2;
            selection = Selection.area(focusArea);
            refresh();
            return Selection.area(focusArea);
        }
        return new Selection(SEL_KEY, k.type, k.slot(), occurrenceOf(k), null, tab);
    }

    /** 영역 area의 줄에 있는 키 가운데 자판 좌표(y는 자판 뷰 기준)의 키 (빈틈이면 손가락 하나 폭 안의 가장 가까운 키). */
    private Key keyIn(int area, float x, float y) {
        int[] range = new int[2];
        if (!rowRange(area, range)) return null;
        KeyboardLayout l = keys.getLayout();
        Key best = null;
        float bestDist = Ui.dp(getContext(), 16);
        // 키 사이에 끼운 조이스틱(커서 이동 키)은 이웃 키들 위에 얹혀 있어 먼저 본다.
        for (int r = range[0]; r <= range[1]; r++) {
            for (Key k : l.rows[r].keys) {
                if (k.isJoystick() && Math.hypot(x - k.rect.centerX(), y - k.rect.centerY()) <= k.rect.width() / 2f) return k;
            }
        }
        for (int r = range[0]; r <= range[1]; r++) {
            for (Key k : l.rows[r].keys) {
                if (k.type == Key.SPACER || k.type == Key.GAP || isHidden(k) || k.isJoystick()) continue;
                RectF kr = k.rect;
                float dx = x < kr.left ? kr.left - x : x > kr.right ? x - kr.right : 0;
                float dy = y < kr.top ? kr.top - y : y > kr.bottom ? y - kr.bottom : 0;
                float d = (float) Math.hypot(dx, dy);
                if (d == 0) return k;
                if (d < bestDist) {
                    bestDist = d;
                    best = k;
                }
            }
        }
        return best;
    }

    /** (x, y)를 감싼 영역 (경계 근처면 가까운 영역). 영역 밖의 빈 곳이면 null. */
    private Selection areaAt(float x, float y) {
        RectF r = new RectF();
        // 분리 키보드: 두 묶음 사이의 가운데 공간은 따로 고른다 (글자 키 영역 안에 있다).
        // 윤곽선 밖으로 나온 g·v를 눌렀으면 가운데 공간이 아니라 글자 키다.
        if (editorSplit() && onStaggeredBlockKey(x, y)) return Selection.area(AREA_LETTERS);
        if (editorSplit() && splitAreaBounds(AREA_MIDDLE, r) && r.contains(x, y)) return Selection.area(AREA_MIDDLE);
        int best = -1;
        float bestDist = Ui.dp(getContext(), 12);
        for (int a = 0; a < AREAS; a++) {
            if (!areaBounds(a, r)) continue;
            float dx = x < r.left ? r.left - x : x > r.right ? x - r.right : 0;
            float dy = y < r.top ? r.top - y : y > r.bottom ? y - r.bottom : 0;
            float d = (float) Math.hypot(dx, dy);
            if (d < bestDist || (d == 0 && bestDist > 0)) {
                bestDist = d;
                best = a;
            }
        }
        return best < 0 ? null : Selection.area(best);
    }

    // ---------------------------------------------------------------- 끌어서 크기 바꾸기


    /** 고른 영역의 굵은 윤곽선 굵기. */
    static final float THICK_DP = 2.5f;

    /**
     * 고른 영역의 굵은 윤곽선 (자판 좌표). 두 묶음으로 나뉜 글자 키는 묶음마다 하나씩 (높이·경계 손잡이가 묶음마다 걸쳐 있다).
     * 그 밖에는 영역 윤곽선 하나. 몇 개를 채웠는지 돌려준다.
     */
    int selectedOutlines(int area, RectF a, RectF b) {
        if (area == AREA_LETTERS && splitLetters()) {
            int n = 0;
            if (blockBounds(true, a)) {
                n++;
            }
            RectF second = n == 0 ? a : b;
            if (blockBounds(false, second)) {
                n++;
            }
            return n;
        }
        if (!focusBounds(area, a)) return 0;
        return 1;
    }

    /**
     * 굵기 strokeDp인 영역 윤곽선의 가운데가 키 모양 가장자리에서 나오는 거리: 선의 안쪽 가장자리가 키 모양에 딱 붙는 자리
     * (굵기의 절반). 키 사이 간격(gapPx)이 좁으면 이웃 영역의 윤곽선과 늘 2dp는 띄도록 줄인다 (아주 좁으면 키 안쪽으로 든다).
     * 키 색이 바탕과 비슷한 기능키에서도 선과 키 사이에 바탕이 비쳐 떨어져 보이지 않는다.
     */
    private float outlineOut(float strokeDp, float gapPx) {
        Context c = getContext();
        return Math.min(Ui.dp(c, strokeDp) / 2f, (gapPx - Ui.dp(c, 2)) / 2f);
    }

    /** 얇은 영역 윤곽선이 키 모양 위·아래 가장자리에서 나오는 거리 (영역 자리를 잴 때의 기준). */
    private float outlinePadY(Context c) {
        return outlineOut(THIN_DP, Ui.dp(c, prefs.keyGapYDp()));
    }

    /** 얇은 영역 윤곽선이 키 모양 왼쪽·오른쪽 가장자리에서 나오는 거리. */
    private float outlinePadX(Context c) {
        return outlineOut(THIN_DP, Ui.dp(c, prefs.keyGapXDp()));
    }

    /** 굵은 윤곽선은 영역 자리(얇은 윤곽선 기준)에서 좌우로 이만큼 더 나온다 (선의 안쪽 가장자리가 키에 붙게). */
    private float thickExtraX() {
        Context c = getContext();
        return outlineOut(THICK_DP, Ui.dp(c, prefs.keyGapXDp())) - outlinePadX(c);
    }

    /** 굵은 윤곽선이 위아래로 더 나오는 거리. */
    private float thickExtraY() {
        Context c = getContext();
        return outlineOut(THICK_DP, Ui.dp(c, prefs.keyGapYDp())) - outlinePadY(c);
    }

    /** 키 층 윤곽선 사이에 늘 남기는 틈. */
    private static final float KEY_OUTLINE_GAP_DP = 2f;
    /** 키 층 윤곽선이 키 모양 가장자리에서 나오는 여유 (틈이 넉넉할 때). */
    private static final float KEY_OUTLINE_OUT_DP = 1.5f;

    /**
     * 키 층 윤곽선이 모양 가장자리에서 나오는 여유: 보통 1.5dp지만, 이웃한 모양 사이(gapPx)가 좁으면 두 윤곽선 사이가
     * 늘 2dp로 남도록 줄인다 (아주 좁으면 모양 안쪽으로 든다).
     */
    private float keyOutlineOut(float gapPx) {
        Context c = getContext();
        return Math.min(Ui.dp(c, KEY_OUTLINE_OUT_DP), (gapPx - Ui.dp(c, KEY_OUTLINE_GAP_DP)) / 2f);
    }

    /** 키 k의 키 층 윤곽선 (미리보기 좌표). 상하·좌우 모두 이웃 키 윤곽선과 2dp 띈다. */
    void keyOutline(Key k, RectF out) {
        Context c = getContext();
        if (k.isJoystick()) {
            // 조이스틱: rect는 누르는 범위(보이는 원보다 넓다)라 보이는 원 둘레에 맞춘 정사각형으로.
            float cx = k.rect.centerX(), cy = keys.getTop() + k.rect.centerY();
            float rad = KeyboardView.joystickRadius(k) + Math.max(Ui.dp(c, 1f), keyOutlineOut(Ui.dp(c, prefs.keyGapXDp())));
            out.set(cx - rad, cy - rad, cx + rad, cy + rad);
            return;
        }
        float gapX = Ui.dp(c, prefs.keyGapXDp()), gapY = Ui.dp(c, prefs.keyGapYDp());
        float gx = gapX / 2f, gy = gapY / 2f, ox = keyOutlineOut(gapX), oy = keyOutlineOut(gapY);
        out.set(k.rect.left + gx - ox, keys.getTop() + k.rect.top + gy - oy,
                k.rect.right - gx + ox, keys.getTop() + k.rect.bottom - gy + oy);
    }

    /**
     * 도구 막대 버튼 b의 키 층 윤곽선 (미리보기 좌표). 칩은 버튼 자리에서 좌우 2dp씩 들어가 있어 이웃 칩과 4dp 띄므로,
     * 좌우는 이웃 버튼 윤곽선과 2dp 띄도록 칩 가장자리에서 1dp 나온다. 위아래는 칩 가장자리에서 1.5dp.
     */
    void toolOutline(View b, RectF out) {
        Context c = getContext();
        float chipGap = Ui.dp(c, 4), ox = keyOutlineOut(chipGap), oy = Ui.dp(c, KEY_OUTLINE_OUT_DP);
        float inset = Math.max(0, (b.getHeight() - Ui.chipHeight(c, b.getHeight())) / 2f) - oy;
        out.set(b.getLeft() + chipGap / 2f - ox, bar.getTop() + b.getTop() + inset,
                b.getRight() - chipGap / 2f + ox, bar.getTop() + b.getBottom() - inset);
    }

    /** 지금 자판의 조이스틱(키 사이에 끼운 커서 이동 키)들. 자판이 바뀔 때만 다시 모은다 (그릴 때마다 만들지 않게). */
    private final java.util.ArrayList<Key> joyList = new java.util.ArrayList<>();
    private KeyboardLayout joyListLayout;

    private java.util.List<Key> joysticks() {
        KeyboardLayout l = keys.getLayout();
        if (l != joyListLayout) {
            joyListLayout = l;
            joyList.clear();
            if (l != null) for (KeyboardLayout.Row row : l.rows) for (Key k : row.keys) if (k.isJoystick()) joyList.add(k);
        }
        return joyList;
    }

    /** 조이스틱 둘레를 따라 그리는 윤곽선 모양 (그릴 때마다 새로 만들지 않고 다시 쓴다). 메인 스레드에서만 쓴다. */
    private final android.graphics.Path shapePath = new android.graphics.Path(), circlePath = new android.graphics.Path();

    /**
     * 윤곽선 o를 조이스틱(Metro 모드에서만 있다)에 맞춰 그린다. 조이스틱이 없으면 보통처럼(모서리 rr) 그린다.
     * <ul>
     *   <li>wrap: 조이스틱을 감싼다 (기능키 영역의 윤곽선). 선의 가운데가 조이스틱 면에서 out만큼 나온 원과 합친다.
     *       나뉜 윤곽선(분리 키보드)은 조이스틱이 걸친 쪽만.</li>
     *   <li>아니면 조이스틱 자리를 파낸다 (글자 키 영역·이웃 키의 윤곽선). 키 모양이 조이스틱 둘레에서 둥글게 파여 있으므로,
     *       선도 파인 가장자리에서 out만큼 조이스틱 쪽으로 나온 원을 따라 파인다. 조이스틱 윤곽선과는 늘 2dp 띈다.</li>
     * </ul>
     * 조이스틱이 없거나 걸치지 않으면 보통처럼 그린다.
     */
    private void drawJoystickAware(Canvas c, RectF o, float rr, Paint p, boolean wrap, float out) {
        java.util.List<Key> joys = joysticks();
        boolean started = false;
        for (int i = 0; i < joys.size(); i++) {
            Key joy = joys.get(i);
            float cx = joy.rect.centerX(), cy = keys.getTop() + joy.rect.centerY(), rad;
            if (wrap) {
                if (cx < o.left || cx > o.right) continue;
                rad = KeyboardView.joystickRadius(joy) + out;
            } else {
                rad = joystickNotch(joy, out, p.getStrokeWidth());
                float nx = Math.max(o.left, Math.min(cx, o.right)), ny = Math.max(o.top, Math.min(cy, o.bottom));
                if (Math.hypot(nx - cx, ny - cy) >= rad) continue;   // 이 윤곽선에는 닿지 않는다
            }
            if (!started) {
                started = true;
                shapePath.rewind();
                shapePath.addRect(o, android.graphics.Path.Direction.CW);   // 조이스틱은 Metro 모드에서만 있다: 모서리 없는 네모
            }
            circlePath.rewind();
            circlePath.addCircle(cx, cy, rad, android.graphics.Path.Direction.CW);
            shapePath.op(circlePath, wrap ? android.graphics.Path.Op.UNION : android.graphics.Path.Op.DIFFERENCE);
        }
        if (started) c.drawPath(shapePath, p);
        else Metro.roundRect(c, o, rr, rr, p);
    }

    /**
     * 조이스틱 자리를 파낸 원의 반지름 (윤곽선의 가운데 자리): 이웃 키를 파낸 가장자리에서 out만큼 조이스틱 쪽.
     * 조이스틱 윤곽선과 늘 2dp(굵은 선이면 굵기만큼 더)는 띈다.
     */
    private float joystickNotch(Key joy, float out, float strokeWidth) {
        Context c = getContext();
        float joyOutline = KeyboardView.joystickRadius(joy) + Math.max(Ui.dp(c, 1f), keyOutlineOut(Ui.dp(c, prefs.keyGapXDp())));
        float apart = joyOutline + Ui.dp(c, THIN_DP) / 2f + Ui.dp(c, KEY_OUTLINE_GAP_DP) + strokeWidth / 2f;
        return Math.max(apart, keys.joystickHoleRadius(joy) - Math.max(0f, out));
    }

    /** 키 윤곽선 o를 그린다: 조이스틱은 원으로 (Metro에서도), 이웃 키는 조이스틱 자리를 파내고. */
    private void drawKeyShape(Canvas c, Key k, RectF o, float rr, Paint p) {
        if (k.isJoystick()) c.drawOval(o, p);
        else drawJoystickAware(c, o, rr, p, false, keyOutlineOut(Ui.dp(getContext(), prefs.keyGapXDp())));
    }

    boolean areaBounds(int area, RectF out) {
        Context c = getContext();
        float pad = outlinePadX(c);
        if (area == AREA_TOOLBAR) {
            if (bar.getParent() == null) return areaOff(AREA_TOOLBAR) && offStripBounds(placeholder, out);
            // 도구 막대의 좌우는 버튼 자리(막대의 좌우 여백 안쪽)를 따라간다: 자판의 키 윤곽선과 같은 선.
            // 키 윤곽선은 키 모양 가장자리(자리에서 키 좌우 간격의 절반 안쪽)에서 pad만큼 나온다: 도구 막대도 같은 규칙.
            float gx = Ui.dp(c, prefs.keyGapXDp()) / 2f;
            float inL = Math.max(0f, bar.getPaddingLeft() + gx - pad);
            float inR = Math.max(0f, bar.getPaddingRight() + gx - pad);
            // 위·아래는 키 영역과 같은 규칙: 줄 가장자리에서 키 상하 간격의 절반만큼 들어갔다가 윤곽선 여유만큼 나온다.
            // 위아래 여유는 outlinePadY: 선이 칩에 붙고, 간격이 좁아도 도구 막대와 숫자 줄 윤곽선 사이가 다른 영역들처럼 띈다.
            float gy = Ui.dp(c, prefs.keyGapYDp()) / 2f, vPad = outlinePadY(c);
            out.set(bar.getLeft() + inL, bar.getTop() + gy - vPad, bar.getRight() - inR, bar.getBottom() - gy + vPad);
            return true;
        }
        KeyboardLayout l = keys.getLayout();
        if (l == null || l.rows.length == 0) return false;
        int first, last;
        int letterStart = numberRowShown ? 1 : 0;
        switch (area) {
            case AREA_NUMBER:
                if (!numberRowShown) {
                    return numberOff() && offStripBounds(numberPlaceholder, out);
                }
                first = last = 0;
                break;
            case AREA_LETTERS:
                first = letterStart;
                last = l.rows.length - 2;
                break;
            default:
                first = last = l.rows.length - 1;
                break;
        }
        if (last < first) return false;
        float gx = Ui.dp(c, prefs.keyGapXDp()) / 2f, gy = Ui.dp(c, prefs.keyGapYDp()) / 2f;
        boolean any = false;
        for (int r = first; r <= last; r++) {
            for (Key k : l.rows[r].keys) {
                if (k.type == Key.SPACER || k.type == Key.GAP || isHidden(k) || k.isJoystick()) continue;   // 조이스틱은 영역 윤곽에 넣지 않는다
                float left = k.rect.left + gx, top = keys.getTop() + k.rect.top + gy;
                float right = k.rect.right - gx, bottom = keys.getTop() + k.rect.bottom - gy;
                if (!any) out.set(left, top, right, bottom);
                else out.union(left, top, right, bottom);
                any = true;
            }
        }
        if (!any) return false;
        out.inset(-pad, -outlinePadY(c));
        return true;
    }

    /**
     * 꺼 둔 영역 빈 칸 v의 테두리: 좌우는 글자 키·기능키 윤곽선의 좌우 끝에 맞추고 (다른 영역 윤곽선과 한 줄로),
     * 위아래는 빈 칸에서 윤곽선 여유만큼 들어간다.
     */
    private boolean offStripBounds(View v, RectF out) {
        Context c = getContext();
        float pad = Ui.dp(c, 3);
        float left = v.getLeft() + pad, right = v.getRight() - pad;
        RectF r = new RectF();
        boolean any = false;
        for (int a : new int[]{AREA_LETTERS, AREA_FUNCTION}) {
            if (!areaBounds(a, r)) continue;
            if (!any) {
                left = r.left;
                right = r.right;
            } else {
                left = Math.min(left, r.left);
                right = Math.max(right, r.right);
            }
            any = true;
        }
        out.set(left, v.getTop() + pad, right, v.getBottom() - pad);
        return out.width() > 0 && out.height() > 0;
    }

    /** 손잡이 h가 지금 보이는지. */
    private boolean handleShown(Handle h) {
        if (keys.getLayout() == null) return false;
        // 키보드 사용자화의 분리 키보드·자·모음 균형 배열: 글자 키를 고르면 두 묶음의 왼쪽·오른쪽 경계 손잡이.
        if (h.balanced()) return editorBlocksSelected() || editorBalancedSelected();
        if (selection == null || selection.kind != SEL_AREA) return false;
        // 영역을 고르면 그 영역 위 가장자리의 높이 손잡이 (영역마다의 여백은 설정 창의 슬라이더로만 바꾼다).
        // 키를 골랐을 때는 손잡이가 없다 (키 간격은 '키 모양'의 슬라이더로).
        switch (selection.area()) {
            case AREA_TOOLBAR: return h == Handle.TOOLBAR && bar.getParent() != null;
            case AREA_NUMBER: return h == Handle.NUMBER_ROW && numberRowShown;
            // 두 묶음으로 나뉘어 있으면 묶음마다 하나씩 (둘 다 글자 키 전체 높이를 바꾼다).
            case AREA_LETTERS: return h == Handle.HEIGHT || (h == Handle.HEIGHT_RIGHT && splitLetters());
            case AREA_MIDDLE: return false;
            default: return h == Handle.FN_ROW;
        }
    }

    /**
     * 높이 손잡이 h가 붙는 윤곽선 (그 위 가장자리에 손잡이를 둔다): 영역 층에서 그리는 영역 윤곽선과 같다
     * (영역의 키를 감싼 자리, 두 묶음으로 나뉜 글자 키는 두 묶음을 합친 것). 가로 모드·폴더블 내부 화면은 고른 영역·묶음의 윤곽선.
     */
    private boolean heightEdgeBounds(Handle h, RectF out) {
        int a = h == Handle.TOOLBAR ? AREA_TOOLBAR : h == Handle.NUMBER_ROW ? AREA_NUMBER : h == Handle.FN_ROW ? AREA_FUNCTION : AREA_LETTERS;
        if (a == AREA_LETTERS && (editorSplit() || editorBalanced())) {
            RectF r = new RectF();
            if (!blockBounds(true, out) || !blockBounds(false, r)) return false;
            out.union(r);
            return true;
        }
        return areaBounds(a, out);
    }

    /** 손잡이의 가운데 (미리보기 좌표). 보이지 않으면 false. */
    private boolean handleCenter(Handle h, float[] out) {
        if (!handleEdge(h, out)) return false;
        if (h.knob()) return true;   // ↔ 버튼은 묶음 한가운데
        // 모든 손잡이는 조절하는 윤곽선의 안쪽에 둔다: 미리보기 가장자리에서 잘리지 않고, 화면 가장자리의 시스템 몸짓과도
        // 겹치지 않으며, 맞닿은 두 묶음의 경계 손잡이도 서로 떨어져 보인다. 거리는 고른 영역의 굵은 윤곽선 가운데에서 6dp
        // (굵은 윤곽선은 영역 자리에서 thickExtra만큼 더 나와 있다).
        float in = Ui.dp(getContext(), HANDLE_GAP_DP + THICK_DP / 2f);
        switch (h) {
            case B_LEFT_START:
            case B_RIGHT_START: out[0] += in - thickExtraX(); break;
            case B_LEFT_END:
            case B_RIGHT_END: out[0] -= in - thickExtraX(); break;
            default: out[1] += in - thickExtraY(); break;   // 위 가장자리의 높이 손잡이
        }
        return true;
    }

    /** 손잡이 가운데가 굵은 윤곽선 가운데에서 떨어진 거리 (한 손 모드 미리보기도 같이 쓴다). */
    static final float HANDLE_INSET_DP = 6f;
    /** 손잡이 가운데가 굵은 윤곽선 안쪽 가장자리에서 떨어진 거리. */
    private static final float HANDLE_GAP_DP = HANDLE_INSET_DP - THICK_DP / 2f;
    /** 얇은 윤곽선 굵기 (영역마다의 윤곽선, 키 윤곽선). */
    static final float THIN_DP = 1.25f;

    /** 손잡이가 조절하는 경계선 위의 점 (미리보기 좌표). 보이지 않으면 false. */
    private boolean handleEdge(Handle h, float[] out) {
        if (!handleShown(h)) return false;
        switch (h) {
            case HEIGHT:
            case HEIGHT_RIGHT:
            case TOOLBAR:
            case NUMBER_ROW:
            case FN_ROW: {
                // 높이 손잡이: 고른 영역 윤곽선의 위 가장자리 가운데. 위·아래로 끌어 그 영역의 높이를 바꾼다.
                RectF r = new RectF();
                // 글자 키가 두 묶음으로 나뉘어 있으면 각 묶음 위 가장자리 가운데 (HEIGHT는 왼쪽, HEIGHT_RIGHT는 오른쪽 묶음).
                if ((h == Handle.HEIGHT || h == Handle.HEIGHT_RIGHT) && splitLetters()) {
                    if (!blockBounds(h == Handle.HEIGHT, r)) return false;
                    out[0] = r.centerX();
                    out[1] = Math.max(r.top, 0);
                    return true;
                }
                if (!heightEdgeBounds(h, r)) return false;
                out[0] = r.centerX();
                out[1] = Math.max(r.top, 0);
                // 글자 키는 자판 가로 가운데에: 두 묶음의 폭·위치가 달라 윤곽선이 한쪽으로 치우쳐도 줄 전체의 가운데에 둔다.
                float[] span = new float[2];
                if (h == Handle.HEIGHT && letterRowSpan(span)) out[0] = (span[0] + span[1]) / 2f;
                return true;
            }
            case B_LEFT_START:
            case B_LEFT_END:
            case B_RIGHT_START:
            case B_RIGHT_END: {
                // 묶음 윤곽선의 왼쪽·오른쪽 가장자리, 세로 가운데.
                RectF r = new RectF();
                if (!blockBounds(h.leftBlock(), r)) return false;
                out[0] = h == Handle.B_LEFT_START || h == Handle.B_RIGHT_START ? r.left : r.right;
                out[1] = r.centerY();
                return true;
            }
            case B_LEFT_MOVE:
            case B_RIGHT_MOVE: {
                // ↔ 버튼: 묶음 윤곽선의 한가운데.
                RectF r = new RectF();
                if (!blockBounds(h.leftBlock(), r)) return false;
                out[0] = r.centerX();
                out[1] = r.centerY();
                return true;
            }
            default:
                return false;
        }
    }

    /** (x, y) 가까이 있는 손잡이 (가장 가까운 것). 없으면 null. */
    private Handle handleAt(float x, float y) {
        float[] p = new float[2];
        float reach = Ui.dp(getContext(), 22);
        Handle best = null;
        float bestDist = Float.MAX_VALUE;
        for (Handle h : Handle.values()) {
            if (!handleCenter(h, p)) continue;
            // 손잡이 모양(긴 쪽 28dp)을 따라 잰 거리.
            float half = Ui.dp(getContext(), 15);
            float dx = Math.abs(x - p[0]), dy = Math.abs(y - p[1]);
            if (h.knob()) {
                // 동그란 버튼: 가운데에서의 거리 (반지름만큼은 버튼 안).
                float d = Math.max(0, (float) Math.hypot(dx, dy) - Ui.dp(getContext(), 13));
                if (d <= reach && d < bestDist) {
                    bestDist = d;
                    best = h;
                }
                continue;
            }
            if (h.vertical) dx = Math.max(0, dx - half);
            else dy = Math.max(0, dy - half);
            float d = (float) Math.hypot(dx, dy);
            if (d <= reach && d < bestDist) {
                bestDist = d;
                best = h;
            }
        }
        return best;
    }

    /** 손잡이를 끌기 시작할 때의 값 (px 또는 %, 손잡이마다 다르다). */
    private float dragStart;
    private int dragValue;
    /** 두 묶음의 경계를 끌기 시작할 때의 자리 {왼쪽 시작, 왼쪽 폭, 오른쪽 시작, 오른쪽 폭} (칸, 줄 전체 10칸). */
    private float[] dragBlocks;

    private void beginDrag(Handle h) {
        Context c = getContext();
        if (h.balanced()) dragBlocks = prefs.balancedExtents();
        switch (h) {
            case TOOLBAR: dragStart = bar.getHeight(); break;
            case NUMBER_ROW: dragStart = Ui.dp(c, 54) * prefs.keyboardHeight() / 100f * prefs.numberRowHeight() / 100f; break;
            case FN_ROW: dragStart = Ui.dp(c, 54) * prefs.keyboardHeight() / 100f * prefs.fnRowHeight() / 100f; break;
            case HEIGHT:
            case HEIGHT_RIGHT: dragStart = Ui.dp(c, 54) * rowUnits() * prefs.keyboardHeight() / 100f; break;
            default: dragStart = valueOf(h); break;
        }
        dragValue = valueOf(h);
    }

    private float rowUnits() {
        return KeyboardLayout.rowUnits(prefs);
    }

    private int valueOf(Handle h) {
        switch (h) {
            case B_LEFT_START:
            case B_LEFT_END: return prefs.balancedConsonantWidth();
            case B_RIGHT_START:
            case B_RIGHT_END: return prefs.balancedVowelWidth();
            case B_LEFT_MOVE: return prefs.balancedConsonantPos();
            case B_RIGHT_MOVE: return prefs.balancedVowelPos();
            case TOOLBAR: return prefs.toolbarHeight();
            case NUMBER_ROW: return prefs.numberRowHeight();
            case HEIGHT:
            case HEIGHT_RIGHT: return prefs.keyboardHeight();
            default: return prefs.fnRowHeight();   // FN_ROW
        }
    }

    /** 높이 값(%)은 5% 단위로 움직인다. */
    private static int snap5(float v) {
        return Math.round(v / 5f) * 5;
    }

    private static int clamp(int v, int min, int max) {
        return Math.max(min, Math.min(max, v));
    }

    /** 처음 누른 곳에서 손가락이 움직인 거리(화면 기준 px)로 새 값을 정한다. 바뀌었으면 저장하고 다시 그린다. */
    private void dragTo(Handle h, float dx, float dy) {
        Context c = getContext();
        float density = getResources().getDisplayMetrics().density;
        int v;
        switch (h) {
            case TOOLBAR:
                v = clamp(snap5((dragStart - dy) / Ui.dp(c, 40) * 100f), 70, 150);
                break;
            case NUMBER_ROW:
                v = clamp(snap5((dragStart - dy) / (Ui.dp(c, 54) * prefs.keyboardHeight() / 100f) * 100f),
                        Prefs.NUMBER_ROW_HEIGHT_MIN, Prefs.NUMBER_ROW_HEIGHT_MAX);
                break;
            case FN_ROW:
                v = clamp(snap5((dragStart - dy) / (Ui.dp(c, 54) * prefs.keyboardHeight() / 100f) * 100f),
                        Prefs.FN_ROW_HEIGHT_MIN, Prefs.FN_ROW_HEIGHT_MAX);
                break;
            case HEIGHT_RIGHT:
            case HEIGHT:
                v = clamp(snap5((dragStart - dy) / (Ui.dp(c, 54) * rowUnits()) * 100f),
                        ownSizes() ? Prefs.PROFILE_HEIGHT_MIN : 70, 140);
                break;
            case B_LEFT_START:
            case B_LEFT_END:
            case B_RIGHT_START:
            case B_RIGHT_END:
                dragBlockEdge(h, dx);
                return;
            case B_LEFT_MOVE:
            case B_RIGHT_MOVE:
                dragBlockMove(h, dx);
                return;
            default: return;
        }
        String key = handleKey(h);
        if (v == dragValue) return;
        dragValue = v;
        prefs.raw().edit().putInt(key, v).apply();
        refresh();
        overlay.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
        listener.onSizeChanged(key);
    }

    private String valueText(Handle h, int v) {
        return v + h.unit();
    }

    /**
     * 묶음 경계 손잡이를 끈다: 끄는 쪽 가장자리만 손가락을 따라 움직이고 반대쪽 가장자리는 그대로다. 그 결과로 그 묶음의
     * 폭과 가로 위치를 함께 저장한다. 다른 묶음 쪽으로는 그 묶음 가장자리까지만, 줄 끝까지만 가고, 폭은 정한 범위 안에서만 바뀐다.
     */
    private void dragBlockEdge(Handle h, float dx) {
        float[] span = new float[2];
        if (dragBlocks == null || !letterRowSpan(span)) return;
        float du = dx / (span[1] - span[0]) * 10f;   // 손가락이 움직인 거리 (칸)
        float ls = dragBlocks[0], le = ls + dragBlocks[1], rs = dragBlocks[2], re = rs + dragBlocks[3];
        float minW = 5f * prefs.balancedWidthMin() / 100f, maxW = 5f * prefs.balancedWidthMax() / 100f;
        boolean left = h.leftBlock();
        float start, end, room;   // room: 다른 묶음·줄 끝까지 넓힐 수 있는 가장 큰 폭 (칸)
        switch (h) {
            case B_LEFT_START: end = le; start = clampF(ls + du, Math.max(0f, le - maxW), le - minW); room = le; break;
            case B_LEFT_END: start = ls; end = clampF(le + du, ls + minW, Math.min(rs, ls + maxW)); room = rs - ls; break;
            case B_RIGHT_START: end = re; start = clampF(rs + du, Math.max(le, re - maxW), re - minW); room = re - le; break;
            default: start = rs; end = clampF(re + du, rs + minW, Math.min(10f, rs + maxW)); room = 10f - rs; break;   // B_RIGHT_END
        }
        // 폭은 5% 단위로 움직인다. 다른 묶음·줄 끝에 닿을 때만 그 자리(1% 단위로 내림)에서 멈춘다.
        // 움직이지 않는 가장자리에 맞춰 시작을 다시 잡는다.
        int roomPct = (int) Math.floor(Math.min(room, maxW) / 5f * 100f + 1e-3f);
        int width = Math.max(prefs.balancedWidthMin(), Math.min(snap5((end - start) / 5f * 100f), roomPct));
        float w = 5f * width / 100f;
        boolean movesStart = h == Handle.B_LEFT_START || h == Handle.B_RIGHT_START;
        float s = movesStart ? end - w : start;
        // 가로 위치는 안쪽 경계의 자리: 왼쪽 묶음은 오른쪽 경계, 오른쪽 묶음은 왼쪽 경계.
        int pos = clamp(left ? Prefs.leftPosOf(s + w) : Prefs.rightPosOf(s), Prefs.BALANCED_POS_MIN, Prefs.BALANCED_POS_MAX);
        String wKey = left ? prefs.balancedConsonantWidthKey() : prefs.balancedVowelWidthKey();
        String pKey = left ? prefs.balancedConsonantPosKey() : prefs.balancedVowelPosKey();
        int oldPos = left ? prefs.balancedConsonantPos() : prefs.balancedVowelPos();
        if (width == dragValue && pos == oldPos) return;
        dragValue = width;
        prefs.raw().edit().putInt(wKey, width).putInt(pKey, pos).apply();
        refresh();
        overlay.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
        listener.onSizeChanged(wKey);
        listener.onSizeChanged(pKey);
    }

    /**
     * ↔ 버튼을 끈다: 묶음의 폭은 그대로 두고 손가락을 따라 옮긴다 (가로 위치, 5 단위). 다른 묶음 가장자리와 줄 끝에 닿으면
     * 그 자리에서 멈춘다.
     */
    private void dragBlockMove(Handle h, float dx) {
        float[] span = new float[2];
        if (dragBlocks == null || !letterRowSpan(span)) return;
        float du = dx / (span[1] - span[0]) * 10f;
        boolean left = h.leftBlock();
        float ls = dragBlocks[0], lw = dragBlocks[1], rs = dragBlocks[2], rw = dragBlocks[3];
        float w = left ? lw : rw;
        float lo = left ? 0f : ls + lw, hi = left ? rs - lw : 10f - rw;   // 시작이 갈 수 있는 범위
        float start = clampF((left ? ls : rs) + du, lo, Math.max(lo, hi));
        // 가로 위치는 안쪽 경계의 자리: 왼쪽 묶음은 오른쪽 경계(시작 + 폭), 오른쪽 묶음은 왼쪽 경계(시작).
        int pos = left ? Prefs.leftPosOf(start + w) : Prefs.rightPosOf(start);
        int snapped = snap5(pos);
        float snappedStart = Prefs.posToUnits(snapped) - (left ? w : 0f);
        if (snappedStart >= lo - 1e-4f && snappedStart <= hi + 1e-4f) pos = snapped;   // 닿은 곳이 아니면 5 단위
        pos = clamp(pos, Prefs.BALANCED_POS_MIN, Prefs.BALANCED_POS_MAX);
        if (pos == dragValue) return;
        dragValue = pos;
        String key = left ? prefs.balancedConsonantPosKey() : prefs.balancedVowelPosKey();
        prefs.raw().edit().putInt(key, pos).apply();
        refresh();
        overlay.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
        listener.onSizeChanged(key);
    }

    private static float clampF(float v, float min, float max) {
        return Math.max(min, Math.min(max, v));
    }

    // ---------------------------------------------------------------- 덮개: 손잡이·고른 것 그리기와 터치

    private final class Overlay extends View {
        private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint label = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF tmp = new RectF();
        private final float[] point = new float[2];
        private final int slop;
        private Handle active;
        private boolean dragging, moved;
        /** 누른 자리 (x는 덮개 좌표, y는 자판 좌표: 가운데로 끌어오느라 내린 거리를 뺀 자리). */
        private float downX, downY, downRawX, downRawY;
        private final java.util.List<Rect> exclusion = new java.util.ArrayList<>();
        private final RectF outlineB = new RectF();
        private final android.graphics.Path holePath = new android.graphics.Path();

        Overlay(Context c) {
            super(c);
            slop = ViewConfiguration.get(c).getScaledTouchSlop();
            stroke.setStyle(Paint.Style.STROKE);
            label.setTextAlign(Paint.Align.CENTER);
            label.setTextSize(Ui.dp(c, 14));
            label.setFakeBoldText(!com.alternative_studios.newswipe.ui.Metro.on);   // Metro에는 굵은 글자가 없다
            setContentDescription(L.t("키보드 미리보기. 영역을 누르면 그 영역이 가운데로 오고 설정 창이 열립니다. 그다음 그 영역의 키나 버튼을 "
                    + "누르면 그 설정 창이 열립니다.",
                    "Keyboard preview. Tap an area to bring it to the center and open its settings, then tap a key or button in it "
                    + "to open its settings."));
        }

        private final Paint areaLabel = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF area = new RectF();

        /**
         * 영역마다 얇은 둥근 윤곽선과 가운데의 '눌러서 … 설정' 이름표. 가운데로 끌어온 영역은 굵은 윤곽선(drawSelected)과
         * 키 윤곽선을 그리므로 끌어온 만큼(focusWeight) 흐려 없앤다. 이름표는 흐림이 짙어질수록(dim) 함께 사라진다.
         */
        private void drawAreas(Canvas c, float dim) {
            Context ctx = getContext();
            int accent = theme.accent;
            areaLabel.setTextSize(Ui.dp(ctx, 11));
            areaLabel.setTextAlign(Paint.Align.CENTER);
            areaLabel.setFakeBoldText(!com.alternative_studios.newswipe.ui.Metro.on);   // Metro에는 굵은 글자가 없다
            // 모서리도 키 모서리를 따라 돈다 (선이 키에서 나온 거리만큼 더 둥글게).
            float r = Math.max(0f, Ui.dp(ctx, prefs.keyRadiusDp()) + outlinePadX(ctx));
            String suffix = L.t(" 설정", " settings");
            stroke.setStrokeWidth(Ui.dp(ctx, THIN_DP));
            boolean split = editorSplit(), balanced = !split && editorBalanced();
            for (int a = 0; a < AREAS; a++) {
                if ((balanced || split) && a == AREA_LETTERS) {
                    // 자·모음 균형 배열: 글자 키를 자음·모음 쪽 윤곽선(함께 고른다)으로 나눠 그린다.
                    // 분리 키보드: 왼쪽·오른쪽 묶음(함께 고른다)과 그 사이의 가운데 공간으로 나눠 그린다.
                    int[] sides = balanced ? new int[]{AREA_LEFT, AREA_RIGHT} : new int[]{AREA_LEFT, AREA_RIGHT, AREA_MIDDLE};
                    for (int side : sides) {
                        boolean got = side == AREA_MIDDLE ? splitAreaBounds(AREA_MIDDLE, area) : blockBounds(side == AREA_LEFT, area);
                        if (!got) continue;
                        float w = focusWeight(side == AREA_MIDDLE ? AREA_MIDDLE : AREA_LETTERS);
                        // 묶음 경계를 끄는 동안은 그 묶음의 자리를 보여 준다.
                        boolean drag = dragging && active != null && active.balanced() && side != AREA_MIDDLE
                                && active.leftBlock() == (side == AREA_LEFT);
                        float rr = Math.min(r, Math.min(area.width(), area.height()) / 2f);
                        float outlineAlpha = drag ? 1f : 0.55f * (1f - w);
                        if (outlineAlpha > 0.01f) {
                            stroke.setColor(withAlpha(accent, outlineAlpha));
                            drawJoystickAware(c, area, rr, stroke, false, outlinePadX(ctx));   // 조이스틱 자리는 파낸다
                        }
                        float pillAlpha = (1f - dim) * (1f - w);
                        if (pillAlpha < 0.01f) continue;
                        String name = side == AREA_MIDDLE ? splitAreaName(AREA_MIDDLE) : areaNames()[AREA_LETTERS];   // 두 묶음 모두 "글자 키"
                        String text = L.t("눌러서 ", "Tap for ") + name + suffix;
                        float room = area.width() - Ui.dp(ctx, 4);
                        if (areaLabel.measureText(text) + Ui.dp(ctx, 16) > room) text = name;
                        if (areaLabel.measureText(text) + Ui.dp(ctx, 16) > room) continue;
                        drawPill(c, text, area, pillAlpha);
                    }
                    continue;
                }
                if (!areaBounds(a, area)) continue;   // 영역의 키를 감싼 자리 (영역의 위·아래 여백은 넣지 않는다)
                // 맨 위·맨 아래 윤곽선이 미리보기 가장자리에서 잘리지 않게 테두리 굵기의 절반만큼은 안쪽에 둔다 (기본 상태일 때).
                float edge = Ui.dp(ctx, 1.5f);
                area.top = Math.max(area.top, edge);
                area.bottom = Math.min(area.bottom, getHeight() - edge);
                boolean off = areaOff(a);   // 꺼 둔 영역: 점선 테두리, 이름표 대신 배경 글씨 (drawOffStrips)
                float w = focusWeight(a);
                float rr = Math.min(r, Math.min(area.width(), area.height()) / 2f);
                float outlineAlpha = 0.55f * (1f - w);
                if (outlineAlpha > 0.01f) {
                    int color = withAlpha(accent, outlineAlpha);
                    Paint outline = off ? dashed(color, Ui.dp(ctx, THIN_DP)) : stroke;
                    outline.setColor(color);
                    // 기능키 영역은 조이스틱을 감싸고, 다른 영역은 조이스틱 자리를 파낸다.
                    boolean wrap = a == AREA_FUNCTION;
                    drawJoystickAware(c, area, rr, outline, wrap,
                            wrap ? Math.max(outlinePadX(ctx), Ui.dp(ctx, THIN_DP) / 2f) : outlinePadX(ctx));
                }
                float pillAlpha = (1f - dim) * (1f - w);
                if (!off && pillAlpha > 0.01f) drawPill(c, L.t("눌러서 ", "Tap for ") + areaName(a, tab) + suffix, area, pillAlpha);
            }
        }

        /** 영역 r 한가운데에 강조색 알약 이름표를 그린다. */
        private void drawPill(Canvas c, String name, RectF r, float alpha) {
            Context ctx = getContext();
            Paint.FontMetrics fm = areaLabel.getFontMetrics();
            float tw = areaLabel.measureText(name), th = fm.descent - fm.ascent;
            float cx = r.centerX(), cy = r.centerY();
            float ph = Ui.dp(ctx, 8), pv = Ui.dp(ctx, 3);
            tmp.set(cx - tw / 2 - ph, cy - th / 2 - pv, cx + tw / 2 + ph, cy + th / 2 + pv);
            fill.setColor(withAlpha(theme.accent, alpha));
            Metro.roundRect(c, tmp, tmp.height() / 2, tmp.height() / 2, fill);
            areaLabel.setColor(withAlpha(theme.onAccent, alpha));
            c.drawText(name, tmp.centerX(), tmp.centerY() - (fm.ascent + fm.descent) / 2, areaLabel);
        }

        /**
         * 꺼 둔 도구 막대·숫자 줄 빈 칸의 배경 글씨 'OOO 꺼짐 · 눌러서 설정' (점선 테두리는 drawAreas가 그린다).
         */
        private void drawOffStrips(Canvas c) {
            Context ctx = getContext();
            hintPaint.setTextSize(Ui.dp(ctx, 12));
            hintPaint.setTextAlign(Paint.Align.CENTER);
            hintPaint.setColor(withAlpha(theme.text, 0.6f));
            Paint.FontMetrics fm = hintPaint.getFontMetrics();
            for (int a : new int[]{AREA_TOOLBAR, AREA_NUMBER}) {
                if (!areaOff(a) || !offBounds(a, area)) continue;
                String text = (a == AREA_TOOLBAR ? L.t("도구 막대", "Toolbar") : L.t("숫자 줄", "Number row")) + L.t(" 꺼짐 · 눌러서 설정", " off · Tap to set");
                c.drawText(text, area.centerX(), area.centerY() - (fm.ascent + fm.descent) / 2, hintPaint);
            }
        }

        /** 꺼 둔 영역의 테두리 자리: 다른 영역 윤곽선과 같은 규칙 (미리보기 가장자리에서 잘리지 않게). */
        private boolean offBounds(int a, RectF out) {
            if (!areaBounds(a, out)) return false;
            float edge = Ui.dp(getContext(), 1.5f);
            out.top = Math.max(out.top, edge);
            out.bottom = Math.min(out.bottom, getHeight() - edge);
            return true;
        }

        private final Paint dash = new Paint(Paint.ANTI_ALIAS_FLAG);
        private android.graphics.DashPathEffect dashEffect;

        /** 점선 붓 (꺼 둔 영역의 테두리). */
        private Paint dashed(int color, float width) {
            if (dashEffect == null) {
                Context ctx = getContext();
                dashEffect = new android.graphics.DashPathEffect(new float[]{Ui.dp(ctx, 4), Ui.dp(ctx, 3)}, 0);
                dash.setStyle(Paint.Style.STROKE);
                dash.setPathEffect(dashEffect);
            }
            dash.setColor(color);
            dash.setStrokeWidth(width);
            return dash;
        }

        private final Paint hintPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

        /** 영역 a의 키·도구 막대 버튼마다 얇은 윤곽선 (그 영역을 가운데로 끌어온 만큼 또렷하게). */
        private void drawAreaKeys(Canvas c, int a, float alpha) {
            if (a < 0 || alpha < 0.01f) return;
            Context ctx = getContext();
            stroke.setStrokeWidth(Ui.dp(ctx, THIN_DP));
            stroke.setColor(withAlpha(theme.accent, 0.6f * alpha));
            if (a == AREA_TOOLBAR) {
                if (bar.getParent() == null) return;
                for (int i = 0; i < bar.getChildCount(); i++) {
                    View b = bar.getChildAt(i);
                    if (!(b.getTag() instanceof String)) continue;
                    toolOutline(b, area);
                    Metro.roundRect(c, area, area.height() / 2, area.height() / 2, stroke);
                }
                return;
            }
            int[] range = new int[2];
            if (!rowRange(a, range)) return;
            KeyboardLayout l = keys.getLayout();
            float kr = Ui.dp(ctx, prefs.keyRadiusDp()) + Ui.dp(ctx, 1.5f);
            for (int r = range[0]; r <= range[1]; r++) {
                for (Key k : l.rows[r].keys) {
                    if (!selectable(k)) continue;
                    keyOutline(k, area);
                    drawKeyShape(c, k, area, Math.min(kr, Math.min(area.width(), area.height()) / 2f), stroke);
                }
            }
        }

        /**
         * 가운데로 끌어온 영역 밖을 바탕색으로 덮어 흐리게 한다 (dim만큼). 구멍은 그 영역의 줄 전체라 위아래의 다른 영역만 흐려진다.
         * shift는 자판을 내린 거리: 덮개 전체(내려 생긴 빈 곳까지)를 덮는다.
         */
        private void drawScrim(Canvas c, float shift, float dim) {
            if (dim < 0.01f) return;
            c.save();
            if (holeNow(tmp)) {
                holePath.rewind();
                float rr = focusArea == AREA_MIDDLE || fromArea == AREA_MIDDLE
                        ? Math.min(Ui.dp(getContext(), prefs.keyRadiusDp()) + Ui.dp(getContext(), 4), Math.min(tmp.width(), tmp.height()) / 2f) : 0f;
                holePath.addRoundRect(tmp, rr, rr, android.graphics.Path.Direction.CW);
                if (focusArea == AREA_FUNCTION) {   // 기능키 영역의 조이스틱은 윗줄에 걸쳐 있어도 흐리지 않는다
                    for (Key joy : joysticks()) {
                        holePath.addCircle(joy.rect.centerX(), keys.getTop() + joy.rect.centerY(), keys.joystickHoleRadius(joy),
                                android.graphics.Path.Direction.CW);
                    }
                    holePath.setFillType(android.graphics.Path.FillType.WINDING);
                }
                c.clipOutPath(holePath);
            }
            fill.setColor(withAlpha(theme.background, SCRIM * dim));
            c.drawRect(0, -shift, getWidth(), getHeight() - shift, fill);
            c.restore();
        }

        /** 고른 영역 a의 굵은 강조색 윤곽선 (끌어온 만큼). 높이·경계 손잡이가 이 윤곽선에 걸쳐 있다. 꺼 둔 영역은 점선. */
        private void drawSelected(Canvas c, int a, float alpha) {
            if (a < 0 || alpha < 0.01f) return;
            Context ctx = getContext();
            int n = selectedOutlines(a, area, outlineB);
            // 굵은 선도 안쪽 가장자리가 키에 붙도록 영역 자리에서 조금 더 내어 그린다.
            float ex = thickExtraX(), ey = thickExtraY();
            area.inset(-ex, -ey);
            outlineB.inset(-ex, -ey);
            float r = Math.max(0f, Ui.dp(ctx, prefs.keyRadiusDp()) + outlineOut(THICK_DP, Ui.dp(ctx, prefs.keyGapXDp())));
            int color = withAlpha(theme.accent, alpha);
            Paint p = areaOff(a) ? dashed(color, Ui.dp(ctx, THICK_DP)) : stroke;
            p.setColor(color);
            p.setStrokeWidth(Ui.dp(ctx, THICK_DP));
            // 기능키 영역은 조이스틱을 감싸고 (선의 안쪽 가장자리가 조이스틱 면에 붙는다), 다른 영역은 조이스틱 자리를 파낸다.
            float out = a == AREA_FUNCTION ? Ui.dp(ctx, THICK_DP) / 2f : outlineOut(THICK_DP, Ui.dp(ctx, prefs.keyGapXDp()));
            for (int i = 0; i < n; i++) {
                RectF o = i == 0 ? area : outlineB;
                float rr = Math.min(r, Math.min(o.width(), o.height()) / 2f);
                drawJoystickAware(c, o, rr, p, a == AREA_FUNCTION, out);
            }
        }

        private int withAlpha(int color, float a) {
            int base = color >>> 24;
            return (Math.round(base * Math.max(0f, Math.min(1f, a))) << 24) | (color & 0x00FFFFFF);
        }

        /** 손잡이 h의 막대 자리 (point가 가운데). */
        private void handleRect(Handle h, float[] p, boolean on, RectF out) {
            Context ctx = getContext();
            float lenHalf = Ui.dp(ctx, on ? 16 : 11), thickHalf = Ui.dp(ctx, on ? 2.33f : 1.67f);   // 굵기는 예전의 2/3
            if (h.vertical) out.set(p[0] - lenHalf, p[1] - thickHalf, p[0] + lenHalf, p[1] + thickHalf);
            else out.set(p[0] - thickHalf, p[1] - lenHalf, p[0] + thickHalf, p[1] + lenHalf);
        }

        /** 손잡이 h를 그릴 진하기: 고른 영역을 끌어온 만큼. */
        private float handleAlpha(Handle h, float dim) {
            return selection != null && selection.kind == SEL_AREA ? focusWeight(selection.area()) : 1f;
        }

        @Override
        protected void onDraw(Canvas c) {
            Context ctx = getContext();
            int accent = theme.accent;
            float shift = column.getTranslationY(), dim = dimNow();
            // 자판과 같은 거리만큼 내려 그린다: 아래의 모든 자리는 자판 좌표다.
            c.save();
            c.translate(0, shift);
            drawAreas(c, dim);
            drawOffStrips(c);
            drawScrim(c, shift, dim);
            // 키 윤곽선은 흐림 위에 그린다: 영역 윤곽선보다 조금 밖으로 나오는 위·아래 줄 키의 테두리가 흐림에 덮이지 않게.
            if (fromArea != focusArea) drawAreaKeys(c, fromArea, focusWeight(fromArea));
            drawAreaKeys(c, focusArea, focusWeight(focusArea));
            if (fromArea != focusArea) drawSelected(c, fromArea, focusWeight(fromArea) * fromOutline);
            drawSelected(c, focusArea, focusWeight(focusArea) * areaOutline);
            // 고른 키·도구 막대 버튼: 강조색 굵은 테두리.
            RectF sel = selectionBounds();
            if (sel != null) {
                float r = Math.min(Ui.dp(ctx, prefs.keyRadiusDp()) + Ui.dp(ctx, 3), Math.min(sel.width(), sel.height()) / 2f);
                stroke.setStrokeWidth(Ui.dp(ctx, 2.5f));
                stroke.setColor(accent);
                if (selection.kind == SEL_TOOL_BUTTON) r = sel.height() / 2f;
                if (selection.kind == SEL_KEY && selectedKey != null) drawKeyShape(c, selectedKey, sel, r, stroke);
                else Metro.roundRect(c, sel, r, r, stroke);
            }
            // 손잡이: 강조색 알약에 바탕색 테두리 (키 위에서도 보이게). 끄는 중인 손잡이는 더 크게.
            for (Handle h : Handle.values()) {
                if (!handleCenter(h, point)) continue;
                float a = handleAlpha(h, dim);
                if (a < 0.01f) continue;
                boolean on = dragging && h == active;
                if (h.knob()) {
                    drawKnob(c, point[0], point[1], on, a);
                    continue;
                }
                handleRect(h, point, on, tmp);
                float thickHalf = Math.min(tmp.width(), tmp.height()) / 2f;
                stroke.setStrokeWidth(Ui.dp(ctx, 2));
                stroke.setColor(withAlpha(theme.background, a));
                Metro.roundRect(c, tmp, thickHalf, thickHalf, stroke);
                fill.setColor(withAlpha(accent, a));
                Metro.roundRect(c, tmp, thickHalf, thickHalf, fill);
            }
            c.restore();
            // 끄는 동안: 무엇을 얼마로 바꾸는지, 고른 영역 위에 남는 자리(자판 탭 아래부터 영역 윤곽선 위까지)의 가운데에 보여 준다.
            if (dragging && active != null) {
                String text = handleName(active) + "  " + valueText(active, dragValue);
                float tw = label.measureText(text);
                float cx = getWidth() / 2f;
                float padH = Ui.dp(ctx, 14), padV = Ui.dp(ctx, 9);
                Paint.FontMetrics fm = label.getFontMetrics();
                float th = fm.descent - fm.ascent;
                float top = focusArea == AREA_LETTERS && tabsView != null ? tabsView.getMeasuredHeight() : 0f;
                float areaTop = focusArea >= 0 && focusBounds(focusArea, tmp) ? tmp.top + shift : getHeight() / 2f;
                // 자리가 이름표보다 좁으면 위쪽에 붙여 잘리지 않게 한다.
                float cy = Math.max((top + areaTop) / 2f, top + th / 2 + padV + Ui.dp(ctx, 2));
                tmp.set(cx - tw / 2 - padH, cy - th / 2 - padV, cx + tw / 2 + padH, cy + th / 2 + padV);
                fill.setColor(accent);
                Metro.roundRect(c, tmp, tmp.height() / 2, tmp.height() / 2, fill);
                label.setColor(theme.onAccent);
                c.drawText(text, cx, cy - (fm.ascent + fm.descent) / 2, label);
            }
        }

        /** 묶음 가로 위치 버튼: 강조색 원에 ↔, 바탕색 테두리. 끄는 동안은 조금 크게. */
        private void drawKnob(Canvas c, float x, float y, boolean on, float alpha) {
            Context ctx = getContext();
            float rad = Ui.dp(ctx, on ? 14 : 12);
            stroke.setStrokeWidth(Ui.dp(ctx, 2));
            stroke.setColor(withAlpha(theme.background, alpha));
            fill.setColor(withAlpha(theme.accent, alpha));
            c.drawCircle(x, y, rad, fill);
            c.drawCircle(x, y, rad, stroke);
            stroke.setColor(withAlpha(theme.onAccent, alpha));
            stroke.setStrokeCap(Paint.Cap.ROUND);
            float len = Ui.dp(ctx, 6.5f), head = Ui.dp(ctx, 3.5f);
            c.drawLine(x - len, y, x + len, y, stroke);
            c.drawLine(x - len + head, y - head, x - len, y, stroke);
            c.drawLine(x - len + head, y + head, x - len, y, stroke);
            c.drawLine(x + len - head, y - head, x + len, y, stroke);
            c.drawLine(x + len - head, y + head, x + len, y, stroke);
            stroke.setStrokeCap(Paint.Cap.BUTT);
        }

        /** 고른 것의 테두리 (자판 좌표). 없으면 null. */
        private RectF selectionBounds() {
            if (selection == null) return null;
            if (selection.kind == SEL_KEY && selectedKey != null) {
                keyOutline(selectedKey, tmp);
                return tmp;
            }
            // 남은 것은 도구 막대 버튼 하나 (영역은 가운데로 끌어오고 흐림으로 보여 준다).
            if (selection.kind != SEL_TOOL_BUTTON || bar.getParent() == null) return null;
            for (int i = 0; i < bar.getChildCount(); i++) {
                View b = bar.getChildAt(i);
                if (selection.toolId.equals(b.getTag())) {
                    toolOutline(b, tmp);
                    return tmp;
                }
            }
            return null;
        }

        @Override
        protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
            super.onLayout(changed, left, top, right, bottom);
            updateExclusion();
        }

        /** 화면 양옆의 손잡이(키보드 여백, 바깥쪽 묶음 경계)를 끌 때 시스템 '뒤로 가기' 몸짓이 가로채지 않게 한다 (Android 10+). */
        private void updateExclusion() {
            if (android.os.Build.VERSION.SDK_INT < 29) return;
            exclusion.clear();
            int half = Ui.dp(getContext(), 36), wide = Ui.dp(getContext(), 32);
            float shift = column.getTranslationY();
            for (Handle h : new Handle[]{Handle.B_LEFT_START, Handle.B_RIGHT_END}) {
                if (!handleCenter(h, point)) continue;
                int x = Math.round(point[0]), y = Math.round(point[1] + shift);
                exclusion.add(new Rect(x - wide, y - half, x + wide, y + half));
            }
            setSystemGestureExclusionRects(exclusion);
        }

        @Override
        public boolean onTouchEvent(MotionEvent e) {
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    getParent().requestDisallowInterceptTouchEvent(true);
                    downX = e.getX();
                    downY = e.getY() - column.getTranslationY();   // 자판 좌표
                    downRawX = e.getRawX();
                    downRawY = e.getRawY();
                    active = handleAt(downX, downY);
                    dragging = false;
                    moved = false;
                    return true;
                case MotionEvent.ACTION_MOVE: {
                    float dx = (e.getRawX() - downRawX) / touchScale, dy = (e.getRawY() - downRawY) / touchScale;
                    if (!dragging && !moved && Math.hypot(dx, dy) > slop) {
                        moved = true;
                        // 손잡이를 그 방향으로 끌기 시작했을 때만 크기를 바꾼다 (손잡이 위에서 옆으로 밀면 아무것도 하지 않는다).
                        boolean handleDrag = active != null
                                && (active.vertical ? Math.abs(dy) >= Math.abs(dx) : Math.abs(dx) >= Math.abs(dy));
                        if (handleDrag) {
                            dragging = true;
                            beginDrag(active);
                            // 여기부터 잰다: 손가락이 움직임 문턱을 넘는 동안의 거리만큼 값이 튀지 않게.
                            downRawX = e.getRawX();
                            downRawY = e.getRawY();
                            dx = 0;
                            dy = 0;
                            performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
                            invalidate();
                        }
                    }
                    // 화면 좌표로 잰다: 키보드가 커지면 이 뷰도 위로 자라 뷰 안의 좌표가 바뀌기 때문이다.
                    if (dragging) dragTo(active, dx, dy);
                    return true;
                }
                case MotionEvent.ACTION_UP:
                    if (dragging) {
                        dragging = false;
                        active = null;
                        dragBlocks = null;
                        invalidate();
                        updateExclusion();
                    } else if (!moved) {
                        playSoundEffect(android.view.SoundEffectConstants.CLICK);
                        tapAt(downX, downY);
                    }
                    return true;
                case MotionEvent.ACTION_CANCEL:
                    dragging = false;
                    active = null;
                    dragBlocks = null;
                    invalidate();
                    return true;
                default:
                    return true;
            }
        }
    }
}
