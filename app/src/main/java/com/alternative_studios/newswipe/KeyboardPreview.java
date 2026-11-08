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
import com.alternative_studios.newswipe.ui.Ui;

/**
 * '키보드 사용자화' 화면 아래에 붙는, 실제 키보드와 같은 크기·모양의 미리보기 (도구 막대 + 자판).
 * <ul>
 *   <li>손잡이를 끌어 크기를 바로 바꾼다: 고른 영역(도구 막대·숫자 줄·글자 키·기능키)의 위 가장자리 = 높이,
 *       키보드 전체를 고르면 네 가장자리 = 키보드 여백, 자·모음 균형 배열·분리 키보드의 두 묶음은 묶음의 왼쪽·오른쪽 가장자리 =
 *       그 경계. 실제 키보드처럼 화면 아래에 붙어 있어, 위 가장자리를 위로 끌면 키보드가 위로 자란다.
 *       영역마다의 여백은 설정 창의 슬라이더로만 바꾼다.</li>
 *   <li>키·도구 막대 버튼·도구 막대를 누르면 고른 것으로 알린다 (설정 화면이 그 아래 동작을 편집하는 칸을 연다).</li>
 * </ul>
 * 손잡이 위를 눌렀다가 움직이지 않고 떼면 그 자리의 키를 누른 것으로 본다 (가장자리 키도 그대로 고를 수 있다).
 * 값은 바뀔 때마다 바로 저장하고, 그리기는 모두 실제 키보드 뷰(KeyboardView)가 한다.
 */
final class KeyboardPreview extends FrameLayout {

    /** 미리보기에 보일 자판. */
    static final int TAB_KOREAN = 0, TAB_ENGLISH = 1, TAB_SYMBOLS = 2;

    /** 고른 것의 종류. */
    static final int SEL_KEY = 0, SEL_TOOL_BUTTON = 1, SEL_TOOLBAR = 2, SEL_AREA = 3, SEL_BOARD = 4;

    /**
     * 윤곽선 층 (애플워치 페이스 편집처럼 좌우로 밀어 넘긴다): 영역 층은 도구 막대·숫자 줄·글자 키·기능키를 크게 감싸
     * 그 영역 전체의 설정을 고르고, 키 층은 키 하나하나를 감싸 그 키의 동작을 고른다.
     */
    /** 윤곽선 층 (왼쪽부터): 키보드 전체 · 영역 · 키 하나. */
    static final int LAYER_BOARD = 0, LAYER_AREA = 1, LAYER_KEY = 2, LAYERS = 3;
    /** 영역 층의 영역. */
    static final int AREA_TOOLBAR = 0, AREA_NUMBER = 1, AREA_LETTERS = 2, AREA_FUNCTION = 3, AREAS = 4;
    static final String[] AREA_NAMES = {"도구 막대", "숫자 줄", "글자 키", "기능키"};
    /**
     * 가로 모드·폴더블 내부 화면 키보드 사용자화의 영역 (AREA_LETTERS·AREA_FUNCTION과 함께 쓴다): 분리 키보드의 왼쪽·오른쪽 묶음과
     * 그 사이의 가운데 공간. 분리 키보드를 끄면 글자 키 하나다.
     */
    static final int AREA_LEFT = 4, AREA_RIGHT = 5, AREA_MIDDLE = 6;

    /** 가로 모드·폴더블 내부 화면 키보드 사용자화의 영역 이름. */
    static String profileAreaName(int area) {
        switch (area) {
            case AREA_LEFT: return "왼쪽 묶음";
            case AREA_RIGHT: return "오른쪽 묶음";
            case AREA_MIDDLE: return "가운데 공간";
            default: return AREA_NAMES[area];
        }
    }
    /** 자판 탭 이름 (TAB_* 차례). */
    static final String[] TAB_NAMES = {"한글", "영어", "기호"};

    /** 영역 이름. 글자 키는 자판마다 따로라 탭 이름을 붙인다 (예: 영어 글자 키). */
    static String areaName(int area, int tab) {
        if (area >= AREAS) return profileAreaName(area);   // 분리 키보드의 가운데 공간 등
        return area == AREA_LETTERS ? TAB_NAMES[tab] + " " + AREA_NAMES[area] : AREA_NAMES[area];
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

        /** 키보드 전체 (키보드 층). */
        static Selection board() {
            return new Selection(SEL_BOARD, -1, null, 0, null, -1);
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

        /** 윤곽선 층이 바뀌었다 (LAYER_*). */
        void onLayerChanged(int layer);
    }

    // ---------------------------------------------------------------- 손잡이

    /**
     * 미리보기의 손잡이: 고른 영역의 높이(위 가장자리: 도구 막대·숫자 줄·글자 키·기능키), 키보드 전체 여백(키보드 층),
     * 두 묶음의 왼쪽·오른쪽 경계(묶음의 양 옆 가장자리), 세로 위치. 모두 가장자리에 붙은 막대 모양이다.
     * 손잡이마다 끄는 방향·이름·저장 키를 한곳에 둔다.
     */
    enum Handle {
        TOOLBAR(true, "도구 막대 높이", Prefs.TOOLBAR_HEIGHT),
        NUMBER_ROW(true, "숫자 줄 높이", Prefs.NUMBER_ROW_HEIGHT),
        HEIGHT(true, "글자 키 높이", Prefs.KEYBOARD_HEIGHT),
        PAD_LEFT(false, "왼쪽 여백", Prefs.PAD_LEFT),
        PAD_RIGHT(false, "오른쪽 여백", Prefs.PAD_RIGHT),
        PAD_BOTTOM(true, "아래쪽 여백", Prefs.PAD_BOTTOM),
        PAD_TOP(true, "위쪽 여백", Prefs.PAD_TOP),
        FN_ROW(true, "기능키 높이", Prefs.FN_ROW_HEIGHT),
        // 자·모음 균형 배열·분리 키보드의 두 묶음: 왼쪽(자음) 묶음과 오른쪽(모음) 묶음의 왼쪽·오른쪽 가장자리.
        // 끌면 그 경계만 움직여 묶음의 폭·가로 위치가 함께 바뀐다. 저장 키는 한글 배열마다 다르다.
        B_LEFT_START(false, "왼쪽 경계", null),
        B_LEFT_END(false, "오른쪽 경계", null),
        B_RIGHT_START(false, "왼쪽 경계", null),
        B_RIGHT_END(false, "오른쪽 경계", null),
        // 가로 모드·폴더블 내부 화면 키보드 사용자화 (setProfileMode): 키보드를 화면 아래에서 띄우는 높이. 저장 키는 프로필마다 다르다.
        LIFT(true, "세로 위치", null);

        /** 위아래로 끄는지 (아니면 좌우). */
        final boolean vertical;
        final String label;
        private final String key;

        Handle(boolean vertical, String label, String key) {
            this.vertical = vertical;
            this.label = label;
            this.key = key;
        }

        /** 두 묶음의 경계 손잡이인지. */
        boolean balanced() {
            return this == B_LEFT_START || this == B_LEFT_END || this == B_RIGHT_START || this == B_RIGHT_END;
        }

        /** 왼쪽(자음) 묶음의 손잡이인지. */
        boolean leftBlock() {
            return this == B_LEFT_START || this == B_LEFT_END;
        }

        /** 높이 손잡이인지 (도구 막대·숫자 줄·글자 키·기능키). */
        boolean height() {
            return this == TOOLBAR || this == NUMBER_ROW || this == HEIGHT || this == FN_ROW;
        }

        /** 값 뒤에 붙는 단위: 높이와 묶음 폭은 %, 여백과 세로 위치는 dp. */
        String unit() {
            return height() || balanced() ? "%" : "dp";
        }
    }

    /** 손잡이 h가 바꾸는 설정 키. 두 묶음의 경계는 폭 키 (가로 위치 키도 함께 바꾼다). */
    private String handleKey(Handle h) {
        switch (h) {
            case B_LEFT_START:
            case B_LEFT_END: return prefs.balancedConsonantWidthKey();
            case B_RIGHT_START:
            case B_RIGHT_END: return prefs.balancedVowelWidthKey();
            case HEIGHT: return profileEdit != null ? prefs.heightKey() : h.key;
            case FN_ROW: return profileEdit != null ? prefs.fnRowHeightKey() : h.key;
            case LIFT: return prefs.liftKey();
            default: return h.key;
        }
    }

    /** 끄는 동안 보이는 이름. 두 묶음의 경계는 그 묶음의 폭으로 보인다. */
    private String handleName(Handle h) {
        if (h.balanced()) {
            boolean split = profileEdit != null || prefs.splitView();
            if (split) return h.leftBlock() ? "왼쪽 묶음 폭" : "오른쪽 묶음 폭";
            return h.leftBlock() ? "자음 키 폭" : "모음 키 폭";
        }
        return h.label;
    }

    // ---------------------------------------------------------------- 손잡이만 쓰는 미리보기

    /** 층·고르기 없이 손잡이만 쓰는 미리보기 (가로 모드·폴더블 내부 화면 키보드 사용자화, setProfileMode). */
    private boolean balancedMode;

    // ---------------------------------------------------------------- 가로 모드·폴더블 내부 화면 키보드 사용자화

    /**
     * 가로 모드·폴더블 내부 화면 키보드 사용자화 화면: 그 프로필의 편집 보기(Prefs.profileEditView). 이 화면에서는 그 프로필이 따로 정하는
     * 값만 바꾼다: 고른 영역의 높이(위 가장자리 손잡이), 세로 위치(아래 가장자리 손잡이), 분리 키보드를 켰으면 묶음의 왼쪽·오른쪽 경계(양 옆 가장자리 손잡이).
     * 키·영역은 고르지 않는다 ('키보드 사용자화'에서만 바꾼다). null이면 보통 미리보기.
     */
    private Prefs profileEdit;
    /**
     * 그 프로필이 실제로 쓰는 화면 폭 (px). 이 폭으로 키보드를 그대로 만든 뒤 화면 폭에 맞춰 통째로 줄이거나 늘려 보여 준다
     * (키·여백·도구 막대의 비율이 실제와 똑같다). 0이면 화면 폭 그대로.
     */
    private int virtualWidth;
    /** 미리보기를 화면에 맞춘 배율 (virtualWidth를 쓰지 않으면 1). 손가락 움직임은 이 값으로 나눠 키보드 좌표로 바꾼다. */
    private float viewScale = 1f;

    /** 가로 모드·폴더블 내부 화면 키보드 사용자화 화면으로 쓴다 (edit = 그 프로필의 편집 보기, width = 그 프로필이 쓰는 화면 폭 px). */
    void setProfileMode(Prefs edit, int width) {
        profileEdit = edit;
        virtualWidth = width;
        balancedMode = true;   // 층·고르기 없이 손잡이만 쓴다
        selection = null;
        selectedKey = null;
        overlay.setContentDescription("키보드 미리보기. 영역을 누르면 그 영역의 설정 창이 열리고, 고른 영역 위 가장자리의 "
                + "손잡이를 위·아래로 끌면 그 영역의 높이가 바뀝니다. 키보드 아래 가장자리의 손잡이를 끌면 세로 위치가 바뀝니다. "
                + "분리 키보드를 켜면 묶음 왼쪽·오른쪽 가장자리의 손잡이를 좌우로 끌어 묶음의 경계를 옮깁니다.");
        refresh();
    }

    /** 키보드 좌표계의 폭·높이 (px). 화면에 맞춰 줄이거나 늘리기 전의 크기다. */
    private int vw() {
        return virtualWidth > 0 ? virtualWidth : getWidth();
    }

    private int vh() {
        return virtualWidth > 0 ? column.getHeight() : getHeight();
    }

    /** 키보드를 화면 아래에서 띄우는 높이 (px). 가로 모드·폴더블 내부 화면 키보드 사용자화에서만 있다. */
    private int liftPx() {
        return profileEdit == null ? 0 : Ui.dp(getContext(), prefs.profileLiftDp());
    }

    /** 지금 보이는 영역들 (분리 키보드면 왼쪽·오른쪽 묶음, 가운데 공간, 기능키, 아니면 글자 키, 기능키). */
    private int[] profileAreas() {
        if (prefs.profileSplit()) return new int[]{AREA_TOOLBAR, AREA_LEFT, AREA_MIDDLE, AREA_RIGHT, AREA_FUNCTION};
        return new int[]{AREA_TOOLBAR, AREA_LETTERS, AREA_FUNCTION};
    }

    /** 가로 모드·폴더블 내부 화면 키보드 사용자화 영역의 윤곽선 (미리보기 좌표). 없으면 false. */
    boolean profileAreaBounds(int area, RectF out) {
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
            case AREA_LETTERS:
                return !prefs.profileSplit() && areaBounds(AREA_LETTERS, out);
            case AREA_FUNCTION:
                return areaBounds(AREA_FUNCTION, out);
            case AREA_TOOLBAR:
                return areaBounds(AREA_TOOLBAR, out);
            default:
                return false;
        }
    }

    /**
     * 키보드 사용자화(영역 층)에서 분리 키보드로 나뉜 글자 키를 왼쪽·오른쪽 묶음과 가운데 공간으로 그리는지
     * ('분리 키보드 사용'을 켰고 한글·영어 자판일 때).
     */
    private boolean editorSplit() {
        return profileEdit == null && !balancedMode && tab != TAB_SYMBOLS && prefs.splitView()
                && blockBounds(true, new RectF()) && blockBounds(false, new RectF());
    }

    /**
     * 키보드 사용자화(영역 층)에서 한글 글자 키를 자·모음 균형 배열의 자음·모음 쪽 윤곽선으로 그리는지
     * (분리 키보드 없이 자·모음 균형 배열을 쓰고 한글 자판일 때).
     */
    private boolean editorBalanced() {
        return profileEdit == null && !balancedMode && tab == TAB_KOREAN && !prefs.splitView() && prefs.balancedLayout()
                && blockBounds(true, new RectF()) && blockBounds(false, new RectF());
    }

    /** 키보드 사용자화에서 고른 영역이 자·모음 균형 배열의 글자 키(자음·모음 쪽)인지. */
    private boolean editorBalancedSelected() {
        return editorBalanced() && selection != null && selection.kind == SEL_AREA && selection.area() == AREA_LETTERS;
    }

    /** 키보드 사용자화에서 고른 영역이 분리 키보드의 글자 키(두 묶음)인지. */
    private boolean editorBlocksSelected() {
        return editorSplit() && selection != null && selection.kind == SEL_AREA && selection.area() == AREA_LETTERS;
    }

    /** 가로 모드·폴더블 내부 화면 키보드 사용자화에서 고른 영역 (없으면 -1). */
    private int profileSelected() {
        return profileEdit != null && selection != null && selection.kind == SEL_AREA ? selection.area() : -1;
    }

    /** 세로 위치만큼 키보드 밑에 비워 두는 칸 (실제 키보드처럼 그만큼 위로 뜬다). */
    private View liftSpace;

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
        if (!letterRowSpan(span)) return vw() / 2f;
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

    /** blockBounds에서 두 쪽 사이의 간격을 맞추기 전의 윤곽선. */
    private boolean rawBlockBounds(boolean consonant, RectF out) {
        KeyboardLayout l = keys.getLayout();
        // 분리 키보드(가로 모드·폴더블 내부 화면)는 두벌식도 가운데에서 나눠 묶음으로 놓는다.
        if (l == null || !(profileEdit != null ? prefs.profileSplit() : prefs.splitView() || prefs.balancedLayout())) return false;
        boolean split = profileEdit != null || prefs.splitView();
        Context c = getContext();
        float pad = Ui.dp(c, 3), bx = boundaryX();
        float gx = Ui.dp(c, prefs.keyGapXDp()) / 2f, gy = Ui.dp(c, prefs.keyGapYDp()) / 2f;
        // 분리 키보드의 두 묶음에는 숫자 줄(1~5, 6~0)도 들어간다. 세로 모드의 균형 배열은 숫자 줄을 뺀 글자 키만 감싼다.
        int first = numberRowShown && profileEdit == null ? 1 : 0, last = l.rows.length - 2;
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
            }
        }
        if (!any) return false;
        out.inset(-pad, -outlinePadY(c));
        return true;
    }

    /** 읽는 설정. 가로 모드·폴더블 내부 화면 키보드 사용자화에서는 refresh()가 그 프로필의 보기로 바꾼다. */
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
    /** 지금 윤곽선 층과, 그리는 위치 (0 = 키보드 층, 1 = 영역 층, 2 = 키 층; 넘기는 동안 그 사이). */
    private int layer = LAYER_AREA;
    private float layerPos = LAYER_AREA;
    private android.animation.ValueAnimator layerAnim;

    int layer() {
        return layer;
    }

    /** 윤곽선 층을 바꾼다. 고른 것은 풀린다. 넘기는 모습은 잠깐 미끄러지며 바뀐다. */
    void setLayer(int l) {
        if (layer != l) {
            layer = l;
            selection = null;
            selectedKey = null;
            listener.onLayerChanged(l);
            overlay.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
        }
        animateLayerTo(l);
    }

    private void animateLayerTo(int l) {
        if (layerAnim != null) layerAnim.cancel();
        layerAnim = android.animation.ValueAnimator.ofFloat(layerPos, l);
        layerAnim.setDuration(Math.max(80, Math.round(260 * Math.abs(layerPos - l))));
        layerAnim.setInterpolator(new android.view.animation.PathInterpolator(0.2f, 0f, 0f, 1f));   // M3 강조 감속
        layerAnim.addUpdateListener(a -> {
            layerPos = (float) a.getAnimatedValue();
            overlay.invalidate();
        });
        layerAnim.start();
        overlay.post(overlay::updateExclusion);
    }

    KeyboardPreview(Context context, Prefs prefs, Listener listener) {
        super(context);
        this.prefs = prefs;
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
        overlay = new Overlay(context);
        addView(overlay, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        refresh();
    }

    /** 크기는 도구 막대 + 자판이 정한다. 위에 덮은 손잡이 층은 그 크기를 그대로 따른다 (덮개가 높이를 키우지 않게). */
    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        if (virtualWidth > 0) {
            // 그 프로필의 실제 화면 폭으로 키보드를 만들고, 화면 폭에 맞춰 통째로 줄이거나 늘린다.
            int avail = MeasureSpec.getSize(widthMeasureSpec);
            viewScale = avail / (float) virtualWidth;
            column.measure(MeasureSpec.makeMeasureSpec(virtualWidth, MeasureSpec.EXACTLY),
                    MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
            int h = column.getMeasuredHeight();
            overlay.measure(MeasureSpec.makeMeasureSpec(virtualWidth, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(h, MeasureSpec.EXACTLY));
            for (View v : new View[]{column, overlay}) {
                v.setPivotX(0f);
                v.setPivotY(0f);
                v.setScaleX(viewScale);
                v.setScaleY(viewScale);
            }
            setMeasuredDimension(avail, Math.round(h * viewScale));
            return;
        }
        viewScale = 1f;
        for (View v : new View[]{column, overlay}) {
            v.setScaleX(1f);
            v.setScaleY(1f);
        }
        measureChild(column, widthMeasureSpec, MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
        int w = column.getMeasuredWidth(), h = column.getMeasuredHeight();
        overlay.measure(MeasureSpec.makeMeasureSpec(w, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(h, MeasureSpec.EXACTLY));
        setMeasuredDimension(w, h);
    }

    void setTab(int t) {
        if (tab == t) return;
        tab = t;
        symbolsPage2 = false;
        // 영역·키보드 전체를 고른 채면 다른 자판에서도 그대로 둔다 (그 자판에 없는 영역이면 refresh가 푼다). 키는 자판마다 달라 푼다.
        if (selection != null && selection.kind != SEL_AREA && selection.kind != SEL_BOARD) selection = null;
        refresh();
    }

    int tab() {
        return tab;
    }

    /** 고른 것을 바꾼다 (null = 고르지 않음). 자판에 없는 키면 고르지 않은 것으로 본다. */
    void select(Selection s) {
        selection = s;
        resolveSelection();
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
        // 가로 모드·폴더블 내부 화면: 분리 키보드를 켰으면 편집 보기(묶음 폭·위치를 그 프로필 값으로), 껐으면 실제 키보드처럼 나누지 않은 보기.
        if (profileEdit != null) prefs = profileEdit.profileSplit() ? profileEdit : profileEdit.profileView(profileEdit.profile());
        theme = KeyboardTheme.of(c);
        setBackgroundColor(theme.background);
        keys.setTheme(theme);
        keys.configure(prefs.longPressMs(), prefs.swipeThresholdDp(), false,
                prefs.keyTextSize());
        keys.setKeyShadow(prefs.keyShadow(), prefs.keyShadowStrength());
        keys.setKeyRadius(Ui.dp(c, prefs.keyRadiusDp()));
        keys.setGridColors(prefs.gridColors());
        keys.setPopupHints(!prefs.popupHintHidden());
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
        numberRowShown = tab != TAB_SYMBOLS && prefs.numberRow() && firstKey != null && KeyboardLayout.isDigit(firstKey.label);
        // 키보드와 같은 높이: 글자 4줄 + 숫자 줄 (기호 자판도 같은 높이를 쓴다).
        float rows = KeyboardLayout.rowUnits(prefs);
        keys.setKeyboardHeight(Math.round(Ui.dp(c, 54) * rows * prefs.keyboardHeight() / 100f) + padT + padB - lift - raise
                + Ui.dp(c, prefs.areaPadVerticalDp()));
        keys.setLayout(l);
        rebuildToolbar(lift, raise);
        resolveSelection();
        overlay.invalidate();
        overlay.post(overlay::updateExclusion);   // 여백이 바뀌면 손잡이 자리도 바뀐다 (배치가 끝난 뒤에 잰다)
    }

    /** 도구 막대와 자판의 배치를 마지막으로 만든 설정 (같으면 뷰를 다시 만들지 않는다: 손잡이를 끄는 동안 자주 불린다). */
    private String barSignature, columnSignature;

    /**
     * 도구 막대 줄이 보이는지. '도구 막대 표시'를 꺼도 단어 추천·단축어를 쓰면 키보드는 그 줄(버튼 없이)을 남겨 둔다.
     * 일본어·중국어 자판(실험실)을 켜도 변환 후보 자리로 남지만, 미리보기는 한글·영어·기호 자판이라 따지지 않는다.
     */
    boolean barShown() {
        return prefs.toolbar() || prefs.suggestWords()
                || (prefs.shortcutsEnabled() && !com.alternative_studios.newswipe.suggest.Shortcuts.of(prefs.shortcuts()).isEmpty());
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

    private android.widget.TextView placeholder() {
        if (placeholder != null) return placeholder;
        Context c = getContext();
        AppTheme app = AppTheme.of(c);
        placeholder = new android.widget.TextView(c);
        // 안내 글자는 윤곽선 층 위에 그린다 (영역 층에서는 이름표가 대신 알린다: drawOffHints).
        android.graphics.drawable.GradientDrawable d = new android.graphics.drawable.GradientDrawable();
        d.setColor(app.bg);
        d.setStroke(Ui.dp(c, 1), app.hint, Ui.dp(c, 4), Ui.dp(c, 3));
        d.setCornerRadius(Ui.dp(c, 8));
        placeholder.setBackground(new android.graphics.drawable.InsetDrawable(d, Ui.dp(c, 8), Ui.dp(c, 3), Ui.dp(c, 8), Ui.dp(c, 3)));
        return placeholder;
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
        int liftPx = liftPx();
        String colSig = shown + "," + bottom + "," + height + "," + lift + "," + raise + "," + barT + "," + barB + "," + liftPx;
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
        column.addView(keys, klp);
        if (shown && bottom) column.addView(bar, blp);
        if (liftPx > 0) {
            // 세로 위치: 키보드 밑의 빈 곳 (실제로는 뒤의 앱 화면이 보이는 곳이라 설정 화면 바탕색으로 칠한다).
            if (liftSpace == null) {
                liftSpace = new View(c);
                liftSpace.setBackgroundColor(AppTheme.of(c).bg);
            }
            column.addView(liftSpace, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, liftPx));
        }
    }

    /** 고른 키를 지금 자판에서 다시 찾는다. 없으면 고르지 않은 것으로 돌린다. */
    private void resolveSelection() {
        selectedKey = null;
        if (selection == null) return;
        if (selection.kind == SEL_BOARD) return;
        if (selection.kind == SEL_AREA && profileEdit != null) {
            // 분리 키보드를 켜고 끄면 영역이 바뀐다 (글자 키 ↔ 두 묶음·가운데 공간).
            if (!profileAreaBounds(selection.area(), new RectF())) selection = null;
            return;
        }
        if (selection.kind == SEL_AREA && selection.area() == AREA_MIDDLE) {
            if (!editorSplit() || !profileAreaBounds(AREA_MIDDLE, new RectF())) selection = null;   // 분리 키보드를 껐다 등
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

    private void tapAt(float x, float y) {
        if (profileEdit != null) {
            // 가로 모드·폴더블 내부 화면: 누른 곳을 감싼 영역 (경계 근처면 가까운 영역). 빈 곳이면 고르지 않음.
            RectF r = new RectF();
            int best = -1;
            float bestDist = Ui.dp(getContext(), 12);
            for (int a : profileAreas()) {
                if (!profileAreaBounds(a, r)) continue;
                float dx = x < r.left ? r.left - x : x > r.right ? x - r.right : 0;
                float dy = y < r.top ? r.top - y : y > r.bottom ? y - r.bottom : 0;
                float d = (float) Math.hypot(dx, dy);
                if (d <= bestDist) {
                    bestDist = d;
                    best = a;
                }
            }
            listener.onTap(best < 0 ? null : Selection.area(best));
            return;
        }
        if (balancedMode) return;   // 손잡이만 쓰는 미리보기: 고를 것이 없다
        if (layer == LAYER_BOARD) {   // 키보드 층: 키보드 전체 하나
            RectF r = new RectF();
            boolean inside = boardBounds(r) && y >= r.top - Ui.dp(getContext(), 12) && y <= r.bottom + Ui.dp(getContext(), 12);
            listener.onTap(inside ? Selection.board() : null);
            return;
        }
        if (layer == LAYER_AREA) {
            // 영역 층: 누른 곳을 감싼 영역 (경계 근처면 가까운 영역).
            RectF r = new RectF();
            // 분리 키보드: 두 묶음 사이의 가운데 공간은 따로 고른다 (글자 키 영역 안에 있다).
            if (editorSplit() && profileAreaBounds(AREA_MIDDLE, r) && r.contains(x, y)) {
                listener.onTap(Selection.area(AREA_MIDDLE));
                return;
            }
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
            listener.onTap(best < 0 ? null : Selection.area(best));
            return;
        }
        // 키 페이지에서는 도구 막대 전체를 고르지 않는다 (도구 막대는 영역 페이지에서, 버튼은 하나씩 고른다).
        if (placeholder != null && placeholder.getParent() != null && y >= placeholder.getTop() && y < placeholder.getBottom()) {
            listener.onTap(null);
            return;
        }
        if (bar.getParent() != null && y >= bar.getTop() && y < bar.getBottom()) {
            for (int i = 0; i < bar.getChildCount(); i++) {
                View b = bar.getChildAt(i);
                if (b.getTag() instanceof String && x >= b.getLeft() && x < b.getRight()) {
                    listener.onTap(Selection.tool((String) b.getTag()));
                    return;
                }
            }
            listener.onTap(null);
            return;
        }
        Key k = keyAt(x, y - keys.getTop());
        if (k == null) {
            listener.onTap(null);
            return;
        }
        if (k.type == Key.SYMBOL_PAGE) {   // 기호 자판의 1/2쪽 넘김: 다른 쪽을 보여 준다
            symbolsPage2 = !symbolsPage2;
            selection = null;
            refresh();
            listener.onTap(null);
            return;
        }
        listener.onTap(new Selection(SEL_KEY, k.type, k.slot(), occurrenceOf(k), null, tab));
    }

    /** 자판 좌표(y는 자판 뷰 기준)의 키. 키 사이 빈틈·여백이면 가장 가까운 키 (손가락 하나 폭 안에 있을 때만). */
    private Key keyAt(float x, float y) {
        KeyboardLayout l = keys.getLayout();
        if (l == null) return null;
        Key best = null;
        float bestDist = Ui.dp(getContext(), 16);
        for (KeyboardLayout.Row row : l.rows) {
            for (Key k : row.keys) {
                if (k.type == Key.SPACER || k.type == Key.GAP || isHidden(k)) continue;
                RectF r = k.rect;
                float dx = x < r.left ? r.left - x : x > r.right ? x - r.right : 0;
                float dy = y < r.top ? r.top - y : y > r.bottom ? y - r.bottom : 0;
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

    // ---------------------------------------------------------------- 끌어서 크기 바꾸기

    /**
     * 영역의 테두리 (미리보기 좌표, 그린 키를 감싼 크기). 지금 자판에 그 영역이 없으면 false.
     * 글자 키는 숫자 줄과 맨 아래 줄을 뺀 줄들 (Shift·⌫ 포함), 기능키는 맨 아래 줄이다.
     */
    /** 키보드 전체(도구 막대·자판)를 감싼 테두리 자리. */
    boolean boardBounds(RectF out) {
        if (column.getChildCount() == 0) return false;
        // 좌우는 키(와 영역 여백)를 따라가는 안쪽 가장자리, 위아래는 키보드 전체 여백의 바깥 가장자리.
        // 어느 쪽이든 그 여백을 늘리면 손가락을 따라 움직이는 변이다.
        Context c = getContext();
        float pad = Ui.dp(c, 2);
        float left = Float.MAX_VALUE, right = -Float.MAX_VALUE;
        RectF r = new RectF();
        for (int a = 0; a < AREAS; a++) {
            if (!areaBounds(a, r)) continue;
            left = Math.min(left, r.left - Ui.dp(c, prefs.areaPadDp(a, Prefs.SIDE_LEFT)));
            right = Math.max(right, r.right + Ui.dp(c, prefs.areaPadDp(a, Prefs.SIDE_RIGHT)));
        }
        if (left > right) {
            left = pad;
            right = vw() - pad;
        }
        out.set(Math.max(left, pad), column.getTop() + pad, Math.min(right, vw() - pad), column.getBottom() - pad);
        return out.height() > 0;
    }

    /**
     * 영역 윤곽선이 키 모양 위·아래 가장자리에서 나오는 여유. 보통은 3dp지만, 키 상하 간격이 좁으면
     * 위아래로 맞닿은 두 영역의 윤곽선 사이가 늘 2dp(간격 8dp일 때와 같은 틈)로 남도록 줄인다 (간격이 아주 좁으면 키 안쪽으로 든다).
     */
    private float outlinePadY(Context c) {
        float gy = Ui.dp(c, prefs.keyGapYDp()) / 2f;
        return Math.min(Ui.dp(c, 3), gy - Ui.dp(c, 1));
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

    /** 영역 층의 영역 윤곽선: 영역에 그 영역의 위·아래 여백을 더한 자리 (여백의 바깥 가장자리). */
    boolean padBounds(int area, RectF out) {
        if (!areaBounds(area, out)) return false;
        Context c = getContext();
        out.top -= Ui.dp(c, prefs.areaPadDp(area, Prefs.SIDE_TOP));
        out.bottom += Ui.dp(c, prefs.areaPadDp(area, Prefs.SIDE_BOTTOM));
        return true;
    }

    boolean areaBounds(int area, RectF out) {
        Context c = getContext();
        float pad = Ui.dp(c, 3);
        if (area == AREA_TOOLBAR) {
            View v = bar.getParent() != null ? bar : placeholder != null && placeholder.getParent() != null ? placeholder : null;
            if (v == null) return false;
            // 도구 막대의 좌우는 버튼 자리(막대의 좌우 여백 안쪽)를 따라간다: 자판의 키 윤곽선과 같은 선.
            // 키 윤곽선은 키 모양 가장자리(자리에서 키 좌우 간격의 절반 안쪽)에서 pad만큼 나온다: 도구 막대도 같은 규칙.
            float gx = Ui.dp(c, prefs.keyGapXDp()) / 2f;
            float inL = v == bar ? Math.max(pad, bar.getPaddingLeft() + gx - pad) : pad;
            float inR = v == bar ? Math.max(pad, bar.getPaddingRight() + gx - pad) : pad;
            // 위·아래는 키 영역과 같은 규칙: 줄 가장자리에서 키 상하 간격의 절반만큼 들어갔다가 윤곽선 여유(pad)만큼 나온다.
            // 위아래 여유는 outlinePadY: 간격이 좁아도 도구 막대와 숫자 줄 윤곽선 사이가 다른 영역들처럼 일정하게 띈다.
            float gy = v == bar ? Ui.dp(c, prefs.keyGapYDp()) / 2f : 0f;
            float vPad = v == bar ? -outlinePadY(c) : pad;
            out.set(v.getLeft() + inL, v.getTop() + gy + vPad, v.getRight() - inR, v.getBottom() - gy - vPad);
            return true;
        }
        KeyboardLayout l = keys.getLayout();
        if (l == null || l.rows.length == 0) return false;
        int first, last;
        int letterStart = numberRowShown ? 1 : 0;
        switch (area) {
            case AREA_NUMBER:
                if (!numberRowShown) return false;
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
                if (k.type == Key.SPACER || k.type == Key.GAP || isHidden(k)) continue;
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

    /** 손잡이 h가 지금 보이는지. */
    private boolean handleShown(Handle h) {
        if (keys.getLayout() == null) return false;
        // 가로 모드·폴더블 내부 화면 키보드 사용자화: 고른 영역의 높이·묶음 경계, 세로 위치.
        if (profileEdit != null) {
            // 세로 위치는 키보드 전체 설정이라 늘 보이고, 나머지는 고른 영역의 손잡이만 (키보드 사용자화의 영역 설정과 같다).
            int a = profileSelected();
            // 세로 모드 분리 키보드: 높이·세로 위치는 '키보드 사용자화'에서 정하므로 묶음 손잡이만 쓴다.
            if (profileEdit.profile().isEmpty() && (h == Handle.LIFT || h == Handle.HEIGHT || h == Handle.FN_ROW)) return false;
            // 기능키 영역을 골랐을 때는 아래 가장자리가 기능키 영역의 아래 윤곽선과 겹치므로 세로 위치 손잡이를 숨긴다.
            if (h == Handle.LIFT) return a != AREA_FUNCTION;
            if (h.balanced()) return a == (h.leftBlock() ? AREA_LEFT : AREA_RIGHT) && blockBounds(h.leftBlock(), new RectF());
            switch (h) {
                case HEIGHT: return a == AREA_LETTERS || a == AREA_LEFT || a == AREA_RIGHT;
                case FN_ROW: return a == AREA_FUNCTION;
                default: return false;
            }
        }
        if (h == Handle.LIFT) return false;
        // 키보드 사용자화의 분리 키보드·자·모음 균형 배열: 글자 키를 고르면 두 묶음의 왼쪽·오른쪽 경계 손잡이.
        if (h.balanced()) return !balancedMode && (editorBlocksSelected() || editorBalancedSelected());
        if (balancedMode) return false;
        // 키보드 층: 키보드 전체를 눌러 골랐을 때만 그 여백 손잡이 (위·아래·왼쪽·오른쪽).
        if (layer == LAYER_BOARD) return selection != null && selection.kind == SEL_BOARD
                && (h == Handle.PAD_TOP || h == Handle.PAD_BOTTOM || h == Handle.PAD_LEFT || h == Handle.PAD_RIGHT);
        if (selection == null || selection.kind != SEL_AREA) return false;
        // 영역을 고르면 그 영역 위 가장자리의 높이 손잡이 (영역마다의 여백은 설정 창의 슬라이더로만 바꾼다).
        // 키를 골랐을 때는 손잡이가 없다 (키 간격은 '키 모양'의 슬라이더로).
        switch (selection.area()) {
            case AREA_TOOLBAR: return h == Handle.TOOLBAR && bar.getParent() != null;
            case AREA_NUMBER: return h == Handle.NUMBER_ROW && numberRowShown;
            case AREA_LETTERS: return h == Handle.HEIGHT;
            case AREA_MIDDLE: return false;
            default: return h == Handle.FN_ROW;
        }
    }

    /**
     * 높이 손잡이 h가 붙는 윤곽선 (그 위 가장자리에 손잡이를 둔다): 영역 층에서 그리는 영역 윤곽선과 같다
     * (영역과 그 위·아래 여백, 두 묶음으로 나뉜 글자 키는 두 묶음을 합친 것). 가로 모드·폴더블 내부 화면은 고른 영역·묶음의 윤곽선.
     */
    private boolean heightEdgeBounds(Handle h, RectF out) {
        int a = h == Handle.TOOLBAR ? AREA_TOOLBAR : h == Handle.NUMBER_ROW ? AREA_NUMBER : h == Handle.FN_ROW ? AREA_FUNCTION : AREA_LETTERS;
        if (profileEdit != null) {
            int chosen = profileSelected();
            if (a == AREA_LETTERS && (chosen == AREA_LEFT || chosen == AREA_RIGHT)) return blockBounds(chosen == AREA_LEFT, out);
            return profileAreaBounds(a, out);
        }
        if (a == AREA_LETTERS && (editorSplit() || editorBalanced())) {
            RectF r = new RectF();
            if (!blockBounds(true, out) || !blockBounds(false, r)) return false;
            out.union(r);
            return true;
        }
        return padBounds(a, out);
    }

    /** 손잡이의 가운데 (미리보기 좌표). 보이지 않으면 false. */
    private boolean handleCenter(Handle h, float[] out) {
        if (!handleEdge(h, out)) return false;
        // 경계선 위가 아니라 윤곽선 안쪽에 둔다: 화면 가장자리의 시스템 몸짓(뒤로 가기·홈)과 겹치지 않고,
        // 맞닿은 두 묶음의 경계 손잡이도 서로 떨어져 보인다.
        float in = Ui.dp(getContext(), 9);
        switch (h) {
            case PAD_LEFT:
            case B_LEFT_START:
            case B_RIGHT_START: out[0] += in; break;
            case PAD_RIGHT:
            case B_LEFT_END:
            case B_RIGHT_END: out[0] -= in; break;
            case PAD_BOTTOM:
            case LIFT: out[1] -= in; break;
            default: out[1] += in; break;   // 위 가장자리의 손잡이 (높이·위쪽 여백)
        }
        return true;
    }

    /** 손잡이가 조절하는 경계선 위의 점 (미리보기 좌표). 보이지 않으면 false. */
    private boolean handleEdge(Handle h, float[] out) {
        if (!handleShown(h)) return false;
        switch (h) {
            case HEIGHT:
            case TOOLBAR:
            case NUMBER_ROW:
            case FN_ROW: {
                // 높이 손잡이: 고른 영역 윤곽선의 위 가장자리 가운데. 위·아래로 끌어 그 영역의 높이를 바꾼다.
                RectF r = new RectF();
                if (!heightEdgeBounds(h, r)) return false;
                out[0] = r.centerX();
                out[1] = Math.max(r.top, 0);
                // 글자 키는 자판 가로 가운데에: 두 묶음의 폭·위치가 달라 윤곽선이 한쪽으로 치우쳐도 줄 전체의 가운데에 둔다.
                float[] span = new float[2];
                int a = profileSelected();
                if (h == Handle.HEIGHT && a != AREA_LEFT && a != AREA_RIGHT && letterRowSpan(span)) out[0] = (span[0] + span[1]) / 2f;
                return true;
            }
            case PAD_TOP:
            case PAD_LEFT:
            case PAD_RIGHT:
            case PAD_BOTTOM: {
                // 키보드 층 윤곽선의 각 변 가운데 (손잡이는 윤곽선과 늘 같은 간격을 둔다).
                RectF r = new RectF();
                if (!boardBounds(r)) return false;
                out[0] = h == Handle.PAD_LEFT ? r.left : h == Handle.PAD_RIGHT ? r.right : r.centerX();
                out[1] = h == Handle.PAD_TOP ? r.top : h == Handle.PAD_BOTTOM ? r.bottom : r.centerY();
                return true;
            }
            case LIFT: {
                // 세로 위치: 키보드 아래 가장자리 (그 밑은 띄운 빈 곳) 가운데.
                RectF r = new RectF();
                if (!boardBounds(r)) return false;
                out[0] = r.centerX();
                out[1] = column.getTop() + column.getHeight() - liftPx();
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
            default:
                return false;
        }
    }

    /** (x, y) 가까이 있는 손잡이 (가장 가까운 것). 없으면 null. */
    private Handle handleAt(float x, float y) {
        float[] p = new float[2];
        // 줄여 그린 미리보기에서도 손으로 집기 쉽게, 줄인 만큼 터치 범위를 넓힌다.
        float reach = Ui.dp(getContext(), 22) / Math.min(1f, viewScale);
        Handle best = null;
        float bestDist = Float.MAX_VALUE;
        for (Handle h : Handle.values()) {
            if (!handleCenter(h, p)) continue;
            // 손잡이 모양(긴 쪽 28dp)을 따라 잰 거리.
            float half = Ui.dp(getContext(), 15);
            float dx = Math.abs(x - p[0]), dy = Math.abs(y - p[1]);
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
            case HEIGHT: dragStart = Ui.dp(c, 54) * rowUnits() * prefs.keyboardHeight() / 100f; break;
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
            case TOOLBAR: return prefs.toolbarHeight();
            case NUMBER_ROW: return prefs.numberRowHeight();
            case HEIGHT: return prefs.keyboardHeight();
            case FN_ROW: return prefs.fnRowHeight();
            case LIFT: return prefs.profileLiftDp();
            case PAD_LEFT: return prefs.padLeftDp();
            case PAD_RIGHT: return prefs.padRightDp();
            case PAD_BOTTOM: return prefs.padBottomDp();
            default: return prefs.padTopDp();   // PAD_TOP
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
            case HEIGHT:
                v = clamp(snap5((dragStart - dy) / (Ui.dp(c, 54) * rowUnits()) * 100f),
                        profileEdit != null ? Prefs.PROFILE_HEIGHT_MIN : 70, 140);
                break;
            case LIFT:   // 4dp 단위, 끌어 올리면 키보드가 위로 뜬다
                v = clamp(Math.round((dragStart - dy / density) / 4f) * 4, 0, 160);
                break;
            case PAD_LEFT: v = clamp(Math.round(dragStart + dx / density), 0, 24); break;
            case PAD_RIGHT: v = clamp(Math.round(dragStart - dx / density), 0, 24); break;
            case PAD_BOTTOM: v = clamp(Math.round(dragStart - dy / density), 0, 48); break;
            case PAD_TOP: v = clamp(Math.round(dragStart - dy / density), 0, 24); break;
            case B_LEFT_START:
            case B_LEFT_END:
            case B_RIGHT_START:
            case B_RIGHT_END:
                dragBlockEdge(h, dx);
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
        float start, end;
        switch (h) {
            case B_LEFT_START: end = le; start = clampF(ls + du, Math.max(0f, le - maxW), le - minW); break;
            case B_LEFT_END: start = ls; end = clampF(le + du, ls + minW, Math.min(rs, ls + maxW)); break;
            case B_RIGHT_START: end = re; start = clampF(rs + du, Math.max(le, re - maxW), re - minW); break;
            default: start = rs; end = clampF(re + du, rs + minW, Math.min(10f, rs + maxW)); break;   // B_RIGHT_END
        }
        // 폭은 1% 단위로 내림하고 (넘치지 않게), 움직이지 않는 가장자리에 맞춰 시작을 다시 잡는다.
        int width = Math.max(prefs.balancedWidthMin(), (int) Math.floor((end - start) / 5f * 100f + 1e-3f));
        float w = 5f * width / 100f;
        boolean movesStart = h == Handle.B_LEFT_START || h == Handle.B_RIGHT_START;
        float s = movesStart ? end - w : start;
        int pos = left ? Prefs.leftPos(w, s) : Prefs.rightPos(w, s);
        pos = left ? clamp(pos, 0, Prefs.BALANCED_LEFT_POS_MAX) : clamp(pos, Prefs.BALANCED_RIGHT_POS_MIN, 100);
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
        /** 좌우로 밀어 윤곽선 층을 넘기는 중 (손가락을 따라 layerPos가 움직인다). */
        private boolean paging;
        private float pageStart;
        private float downX, downY, downRawX, downRawY;
        private final java.util.List<Rect> exclusion = new java.util.ArrayList<>();

        Overlay(Context c) {
            super(c);
            slop = ViewConfiguration.get(c).getScaledTouchSlop();
            stroke.setStyle(Paint.Style.STROKE);
            label.setTextAlign(Paint.Align.CENTER);
            label.setTextSize(Ui.dp(c, 14));
            label.setFakeBoldText(true);
            setContentDescription("키보드 미리보기. 좌우로 밀어 키보드·영역·키 페이지를 넘깁니다. 키보드 전체나 영역, 키를 누르면 "
                    + "그 설정 창이 열리고, 크기와 여백은 창의 슬라이더로도 바꿀 수 있습니다.");
        }

        private final Paint areaLabel = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF area = new RectF();

        /** 영역마다 둥근 테두리와 가운데 이름표 ("도구 막대" + suffix). 그 층에서 고른 영역은 굵게 칠한다. */
        private void drawAreas(Canvas c, int which, float alpha, float dx, String suffix) {
            Context ctx = getContext();
            int accent = theme.accent;
            c.save();
            c.translate(dx, 0);
            areaLabel.setTextSize(Ui.dp(ctx, 11));
            areaLabel.setTextAlign(Paint.Align.CENTER);
            areaLabel.setFakeBoldText(true);
            float r = Ui.dp(ctx, prefs.keyRadiusDp()) + Ui.dp(ctx, 4);
            boolean split = editorSplit(), balanced = !split && editorBalanced();
            for (int a = 0; a < AREAS; a++) {
                if (balanced && a == AREA_LETTERS) {
                    // 자·모음 균형 배열: 글자 키를 자음·모음 쪽 윤곽선(함께 고른다)으로 나눠 그린다.
                    boolean chosen = layer == which && selection != null && selection.kind == SEL_AREA && selection.area() == AREA_LETTERS;
                    for (boolean cons : new boolean[]{true, false}) {
                        if (!blockBounds(cons, area)) continue;
                        boolean on = chosen || (layer == which && dragging && active != null && active.balanced()
                                && active.leftBlock() == cons);
                        stroke.setColor(withAlpha(accent, (on ? 1f : 0.55f) * alpha));
                        stroke.setStrokeWidth(Ui.dp(ctx, on ? 2.5f : 1.25f));
                        float rr = Math.min(r, Math.min(area.width(), area.height()) / 2f);
                        c.drawRoundRect(area, rr, rr, stroke);
                        if (on) {
                            fill.setColor(withAlpha(accent, 0.12f * alpha));
                            c.drawRoundRect(area, rr, rr, fill);
                            continue;
                        }
                        String name = AREA_NAMES[AREA_LETTERS];   // 두 쪽 모두 "글자 키"
                        String text = "눌러서 " + name + suffix;
                        float room = area.width() - Ui.dp(ctx, 4);
                        if (areaLabel.measureText(text) + Ui.dp(ctx, 16) > room) text = name;
                        if (areaLabel.measureText(text) + Ui.dp(ctx, 16) > room) continue;
                        drawPill(c, text, area, alpha);
                    }
                    continue;
                }
                if (split && a == AREA_LETTERS) {
                    // 분리 키보드: 글자 키는 왼쪽·오른쪽 묶음(함께 고른다)과 그 사이의 가운데 공간으로 나눠 그린다.
                    boolean chosen = layer == which && selection != null && selection.kind == SEL_AREA && selection.area() == AREA_LETTERS;
                    boolean midChosen = layer == which && selection != null && selection.kind == SEL_AREA && selection.area() == AREA_MIDDLE;
                    for (int side : new int[]{AREA_LEFT, AREA_RIGHT, AREA_MIDDLE}) {
                        if (!profileAreaBounds(side, area)) continue;
                        boolean on = side == AREA_MIDDLE ? midChosen : chosen;
                        stroke.setColor(withAlpha(accent, (on ? 1f : 0.55f) * alpha));
                        stroke.setStrokeWidth(Ui.dp(ctx, on ? 2.5f : 1.25f));
                        float rr = Math.min(r, Math.min(area.width(), area.height()) / 2f);
                        c.drawRoundRect(area, rr, rr, stroke);
                        if (on) {
                            fill.setColor(withAlpha(accent, 0.12f * alpha));
                            c.drawRoundRect(area, rr, rr, fill);
                            continue;
                        }
                        String name = side == AREA_MIDDLE ? profileAreaName(AREA_MIDDLE) : AREA_NAMES[AREA_LETTERS];   // 두 묶음 모두 "글자 키"
                        String text = "눌러서 " + name + suffix;
                        float room = area.width() - Ui.dp(ctx, 4);
                        if (areaLabel.measureText(text) + Ui.dp(ctx, 16) > room) text = name;
                        if (areaLabel.measureText(text) + Ui.dp(ctx, 16) > room) continue;
                        drawPill(c, text, area, alpha);
                    }
                    continue;
                }
                if (!padBounds(a, area)) continue;   // 영역과 그 위·아래 여백까지
                // 맨 위·맨 아래 윤곽선이 미리보기 가장자리에서 잘리지 않게 테두리 굵기의 절반만큼은 안쪽에 둔다.
                float edge = Ui.dp(ctx, 1.5f);
                area.top = Math.max(area.top, edge);
                area.bottom = Math.min(area.bottom, getHeight() - edge);
                boolean chosen = layer == which && selection != null && selection.kind == SEL_AREA && selection.area() == a;
                stroke.setColor(withAlpha(accent, (chosen ? 1f : 0.55f) * alpha));
                // 고른 영역은 고른 키와 같은 굵기로 굵게.
                stroke.setStrokeWidth(Ui.dp(ctx, chosen ? 2.5f : 1.25f));
                float rr = Math.min(r, Math.min(area.width(), area.height()) / 2f);
                c.drawRoundRect(area, rr, rr, stroke);
                if (chosen) {
                    fill.setColor(withAlpha(accent, 0.12f * alpha));
                    c.drawRoundRect(area, rr, rr, fill);
                }
                // 이름표: 영역 한가운데의 작은 알약. 고른 영역은 이름표 없이 굵은 테두리만.
                if (!chosen) drawPill(c, "눌러서 " + areaName(a, tab) + suffix + (areaOff(a) ? " (꺼짐)" : ""), area, alpha);
            }
            c.restore();
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
            c.drawRoundRect(tmp, tmp.height() / 2, tmp.height() / 2, fill);
            areaLabel.setColor(withAlpha(theme.onAccent, alpha));
            c.drawText(name, tmp.centerX(), tmp.centerY() - (fm.ascent + fm.descent) / 2, areaLabel);
        }

        /** 키보드 층: 도구 막대부터 자판 끝까지 키보드 전체를 감싼 테두리 하나와 이름표. 고르면 굵게. */
        private void drawBoard(Canvas c, float alpha, float dx) {
            if (!boardBounds(area)) return;
            Context ctx = getContext();
            c.save();
            c.translate(dx, 0);
            boolean chosen = layer == LAYER_BOARD && selection != null && selection.kind == SEL_BOARD;
            stroke.setStrokeWidth(Ui.dp(ctx, chosen ? 2.5f : 1.25f));
            stroke.setColor(withAlpha(theme.accent, (chosen ? 1f : 0.55f) * alpha));
            float rr = Ui.dp(ctx, prefs.keyRadiusDp()) + Ui.dp(ctx, 4);
            c.drawRoundRect(area, rr, rr, stroke);
            if (chosen) {
                fill.setColor(withAlpha(theme.accent, 0.12f * alpha));
                c.drawRoundRect(area, rr, rr, fill);
            } else {
                areaLabel.setTextSize(Ui.dp(ctx, 11));
                areaLabel.setTextAlign(Paint.Align.CENTER);
                areaLabel.setFakeBoldText(true);
                drawPill(c, "눌러서 키보드 전체 설정", area, alpha);
            }
            c.restore();
        }



        /** 꺼 두어 빈 칸만 남은 영역인지 (도구 막대·숫자 줄). */
        private boolean areaOff(int a) {
            if (a == AREA_TOOLBAR) return placeholder != null && placeholder.getParent() != null;
            return false;
        }

        /** 꺼진 도구 막대·숫자 줄 빈 칸의 안내 글자. 영역 층에서는 이름표와 겹치지 않게 흐려져 사라진다. */
        private void drawOffHints(Canvas c, float alpha) {
            if (alpha < 0.01f) return;
            Context ctx = getContext();
            hintPaint.setTextSize(Ui.dp(ctx, 12));
            hintPaint.setTextAlign(Paint.Align.CENTER);
            hintPaint.setColor(withAlpha(AppTheme.of(ctx).hint, alpha));
            Paint.FontMetrics fm = hintPaint.getFontMetrics();
            View v = placeholder;
            if (v == null || v.getParent() == null) return;
            float cy = column.getTop() + (v.getTop() + v.getBottom()) / 2f;
            c.drawText("도구 막대 꺼짐 · 눌러서 설정", getWidth() / 2f, cy - (fm.ascent + fm.descent) / 2, hintPaint);
        }

        private final Paint hintPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

        /**
         * 윤곽선 층: 영역 층은 영역마다 둥근 테두리와 이름표, 키 층은 키마다 얇은 테두리. 넘기는 동안(layerPos)
         * 두 층이 서로 반대쪽으로 조금 미끄러지며 흐려지고 또렷해진다.
         */
        private void drawLayers(Canvas c) {
            Context ctx = getContext();
            float shift = Ui.dp(ctx, 28);
            int accent = theme.accent;
            float boardAlpha = Math.max(0f, 1f - Math.abs(layerPos - LAYER_BOARD));
            float areaAlpha = Math.max(0f, 1f - Math.abs(layerPos - LAYER_AREA));
            float keyAlpha = Math.max(0f, 1f - Math.abs(layerPos - LAYER_KEY));
            // 키보드 층과 영역 층은 같은 영역 윤곽선을 쓰고 이름표만 다르다 (여백 조절 · 설정).
            if (boardAlpha > 0.01f) drawBoard(c, boardAlpha, (LAYER_BOARD - layerPos) * shift);
            if (areaAlpha > 0.01f) drawAreas(c, LAYER_AREA, areaAlpha, (LAYER_AREA - layerPos) * shift, " 설정");
            drawOffHints(c, keyAlpha);
            if (keyAlpha > 0.01f) {
                c.save();
                c.translate((LAYER_KEY - layerPos) * shift, 0);
                stroke.setStrokeWidth(Ui.dp(ctx, 1.25f));
                stroke.setColor(withAlpha(accent, 0.55f * keyAlpha));
                float kr = Ui.dp(ctx, prefs.keyRadiusDp()) + Ui.dp(ctx, 1.5f);
                KeyboardLayout l = keys.getLayout();
                if (l != null) {
                    for (KeyboardLayout.Row row : l.rows) {
                        for (Key k : row.keys) {
                            if (!selectable(k)) continue;
                            keyOutline(k, area);
                            float rr = Math.min(kr, Math.min(area.width(), area.height()) / 2f);
                            c.drawRoundRect(area, rr, rr, stroke);
                        }
                    }
                }
                if (bar.getParent() != null) {
                    for (int i = 0; i < bar.getChildCount(); i++) {
                        View b = bar.getChildAt(i);
                        if (!(b.getTag() instanceof String)) continue;
                        toolOutline(b, area);
                        c.drawRoundRect(area, area.height() / 2, area.height() / 2, stroke);
                    }
                }
                c.restore();
            }
        }

        private int withAlpha(int color, float a) {
            int base = color >>> 24;
            return (Math.round(base * Math.max(0f, Math.min(1f, a))) << 24) | (color & 0x00FFFFFF);
        }

        /**
         * 가로 모드·폴더블 내부 화면 키보드 사용자화: 키보드 사용자화의 영역 층처럼 영역마다 둥근 테두리와 가운데 이름표.
         * 고른 영역은 굵게 칠하고 이름표를 숨긴다.
         */
        private void drawProfileAreas(Canvas c) {
            Context ctx = getContext();
            areaLabel.setTextSize(Ui.dp(ctx, 11));
            areaLabel.setTextAlign(Paint.Align.CENTER);
            areaLabel.setFakeBoldText(true);
            float r = Ui.dp(ctx, prefs.keyRadiusDp()) + Ui.dp(ctx, 4);
            int chosenArea = profileSelected();
            for (int a : profileAreas()) {
                if (!profileAreaBounds(a, area)) continue;
                float edge = Ui.dp(ctx, 1.5f);
                area.top = Math.max(area.top, edge);
                area.bottom = Math.min(area.bottom, getHeight() - edge);
                boolean chosen = a == chosenArea;
                boolean dragged = dragging && active != null && active.balanced()
                        && a == (active.leftBlock() ? AREA_LEFT : AREA_RIGHT);
                stroke.setColor(withAlpha(theme.accent, chosen || dragged ? 1f : 0.55f));
                stroke.setStrokeWidth(Ui.dp(ctx, chosen || dragged ? 2.5f : 1.25f));
                float rr = Math.min(r, Math.min(area.width(), area.height()) / 2f);
                c.drawRoundRect(area, rr, rr, stroke);
                if (chosen) {
                    fill.setColor(withAlpha(theme.accent, 0.12f));
                    c.drawRoundRect(area, rr, rr, fill);
                    continue;
                }
                // 이름표: 좁은 영역(가운데 공간 등)에서는 짧게, 그래도 넘치면 그리지 않는다.
                String name = profileAreaName(a);
                String text = "눌러서 " + name + " 설정";
                float room = area.width() - Ui.dp(ctx, 4);
                if (areaLabel.measureText(text) + Ui.dp(ctx, 16) > room) text = name;
                if (areaLabel.measureText(text) + Ui.dp(ctx, 16) > room) continue;
                drawPill(c, text, area, 1f);
            }
        }

        @Override
        protected void onDraw(Canvas c) {
            Context ctx = getContext();
            int accent = theme.accent;
            if (profileEdit != null) drawProfileAreas(c);
            else if (!balancedMode) drawLayers(c);
            // 고른 것: 강조색 테두리.
            RectF sel = selectionBounds();   // 고른 키·도구 막대 버튼 (영역·키보드는 drawLayers에서)
            if (sel != null) {
                float r = Math.min(Ui.dp(ctx, prefs.keyRadiusDp()) + Ui.dp(ctx, 3), Math.min(sel.width(), sel.height()) / 2f);
                stroke.setStrokeWidth(Ui.dp(ctx, 2.5f));
                stroke.setColor(accent);
                c.drawRoundRect(sel, r, r, stroke);
            }
            // 손잡이: 강조색 알약에 바탕색 테두리 (키 위에서도 보이게). 끄는 중인 손잡이는 더 크게.
            for (Handle h : Handle.values()) {
                if (!handleCenter(h, point)) continue;
                boolean on = dragging && h == active;
                float lenHalf = Ui.dp(ctx, on ? 16 : 11), thickHalf = Ui.dp(ctx, on ? 3.5f : 2.5f);
                if (h.vertical) tmp.set(point[0] - lenHalf, point[1] - thickHalf, point[0] + lenHalf, point[1] + thickHalf);
                else tmp.set(point[0] - thickHalf, point[1] - lenHalf, point[0] + thickHalf, point[1] + lenHalf);
                stroke.setStrokeWidth(Ui.dp(ctx, 2));
                stroke.setColor(theme.background);
                c.drawRoundRect(tmp, thickHalf, thickHalf, stroke);
                fill.setColor(accent);
                c.drawRoundRect(tmp, thickHalf, thickHalf, fill);
            }
            // 끄는 동안: 무엇을 얼마로 바꾸는지 자판 가운데에 크게 보여 준다.
            if (dragging && active != null) {
                String text = handleName(active) + "  " + valueText(active, dragValue);
                float tw = label.measureText(text);
                float cx = getWidth() / 2f;
                float cy = keys.getTop() + keys.getHeight() / 2f;
                float padH = Ui.dp(ctx, 14), padV = Ui.dp(ctx, 9);
                Paint.FontMetrics fm = label.getFontMetrics();
                float th = fm.descent - fm.ascent;
                tmp.set(cx - tw / 2 - padH, cy - th / 2 - padV, cx + tw / 2 + padH, cy + th / 2 + padV);
                fill.setColor(accent);
                c.drawRoundRect(tmp, tmp.height() / 2, tmp.height() / 2, fill);
                label.setColor(theme.onAccent);
                c.drawText(text, cx, cy - (fm.ascent + fm.descent) / 2, label);
            }
        }

        /** 고른 것의 테두리 (미리보기 좌표). 없으면 null. */
        private RectF selectionBounds() {
            if (selection == null) return null;
            if (selection.kind == SEL_KEY && selectedKey != null) {
                keyOutline(selectedKey, tmp);
                return tmp;
            }
            // 남은 것은 도구 막대 버튼 하나 (영역·키보드는 윤곽선 층에서 그린다).
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
            for (Handle h : new Handle[]{Handle.PAD_LEFT, Handle.PAD_RIGHT, Handle.B_LEFT_START, Handle.B_RIGHT_END}) {
                if (!handleCenter(h, point)) continue;
                int x = Math.round(point[0]), y = Math.round(point[1]);
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
                    downY = e.getY();
                    downRawX = e.getRawX();
                    downRawY = e.getRawY();
                    active = handleAt(downX, downY);
                    dragging = false;
                    moved = false;
                    paging = false;
                    return true;
                case MotionEvent.ACTION_MOVE: {
                    float dx = (e.getRawX() - downRawX) / viewScale, dy = (e.getRawY() - downRawY) / viewScale;
                    if (!dragging && !moved && Math.hypot(dx, dy) * viewScale > slop) {
                        moved = true;
                        // 손잡이를 그 방향으로 끌기 시작했을 때만 크기를 바꾼다 (손잡이 위에서 옆으로 밀면 아무것도 하지 않는다).
                        boolean handleDrag = active != null
                                && (active.vertical ? Math.abs(dy) >= Math.abs(dx) : Math.abs(dx) >= Math.abs(dy));
                        if (!handleDrag && !balancedMode && Math.abs(dx) > Math.abs(dy) * 1.5f) {
                            // 손잡이를 끄는 것이 아닌 가로 밀기: 윤곽선 층 넘기기.
                            paging = true;
                            pageStart = layerPos;
                            if (layerAnim != null) layerAnim.cancel();
                            downRawX = e.getRawX();
                            dx = 0;
                        }
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
                    if (paging) {
                        // 왼쪽으로 밀면 다음 층(키)으로, 오른쪽으로 밀면 앞 층(영역)으로. 손가락을 따라 움직인다.
                        layerPos = Math.max(Math.max(0f, layer - 1f),
                                Math.min(Math.min(LAYERS - 1f, layer + 1f), pageStart - dx / (getWidth() * 0.6f)));
                        invalidate();
                    }
                    return true;
                }
                case MotionEvent.ACTION_UP:
                    if (paging) {
                        paging = false;
                        // 3분의 1 넘게 넘겼거나 빠르게 밀었으면 그 층으로, 아니면 제자리로.
                        float moveX = (e.getRawX() - downRawX) / viewScale;
                        int target = Math.abs(layerPos - pageStart) > 0.33f || Math.abs(moveX) > Ui.dp(getContext(), 72)
                                ? Math.max(0, Math.min(LAYERS - 1, layer + (moveX < 0 ? 1 : -1))) : layer;
                        setLayer(target);
                    } else if (dragging) {
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
                    if (paging) {
                        paging = false;
                        animateLayerTo(layer);
                    }
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
