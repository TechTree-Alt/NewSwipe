package com.alternative_studios.newswipe.keyboard;

import com.alternative_studios.newswipe.L;
import java.util.ArrayList;
import java.util.List;

/**
 * 기능키 순서·유무 사용자화: 맨 아래 줄 키들(기호 키, 쉼표, 지구본 키, 스페이스바, 온점, 엔터와, 처음에는 꺼져 있는
 * 이모지·클립보드·커서 이동·음성 입력 키)의 순서와 표시 여부.
 * 순서는 키 ID를 쉼표로 이은 문자열로 저장한다. 표시 여부는 키마다 따로 저장한다 (Prefs).
 */
public final class BottomKeys {
    public static final String MODE = "mode", COMMA = "comma", GLOBE = "globe", SPACE = "space",
            PERIOD = "period", ENTER = "enter";
    /** 더할 수 있는 키 (처음에는 꺼져 있다). */
    public static final String EMOJI = "emoji", CLIPBOARD = "clipboard", CURSOR = "cursor", VOICE = "voice";
    /** 맨 아래 줄에 한꺼번에 켤 수 있는 키의 수 (스페이스바 포함, Shift·⌫ 제외). */
    public static final int MAX_SHOWN = 8;
    /** 기본 순서. 더할 수 있는 키는 뒤에 온다 (예전에 저장한 순서에도 뒤에 붙는다). */
    public static final String[] DEFAULT_ORDER = {MODE, COMMA, GLOBE, SPACE, PERIOD, ENTER, EMOJI, CLIPBOARD, CURSOR, VOICE};

    /**
     * 기호 자판에서 언어 전환 키 자리에 놓을 수 있는 키 (선택 창에 이 차례로 보인다). 맨 앞의 GLOBE는 '기호 자판에서도 그대로'이고,
     * 기본값은 이모지 키(EMOJI)다.
     */
    public static final String[] SYMBOL_GLOBE_CHOICES = {GLOBE, EMOJI, CLIPBOARD, CURSOR, VOICE};

    private BottomKeys() {
    }

    /** 하단 키 편집 화면에 보이는 이름 (이름과 키 모양). */
    public static String label(String id) {
        switch (id) {
            case MODE: return L.t("기호 키 (?123·가·ABC)", "Symbol key (?123·가·ABC)");
            case COMMA: return L.t("쉼표 키 (,)", "Comma key (,)");
            case GLOBE: return L.t("언어 전환 키 (🌐︎)", "Globe key (🌐︎)");
            case SPACE: return L.t("스페이스바 (␣)", "Space bar (␣)");
            case PERIOD: return L.t("온점 키 (.)", "Period key (.)");
            case ENTER: return L.t("엔터 키 (↵)", "Enter key (↵)");
            case EMOJI: return L.t("이모지 키", "Emoji key");
            case CLIPBOARD: return L.t("클립보드 키", "Clipboard key");
            case CURSOR: return L.t("커서 이동 키", "Cursor key");
            case VOICE: return L.t("음성 입력 키", "Voice input key");
            default: return id;
        }
    }

    /** 저장된 값(없으면 null)을 기호 자판의 언어 전환 키 자리에 놓을 키로 푼다. 모르는 값은 기본값(이모지 키)이다. */
    public static String symbolGlobeKey(String stored) {
        for (String id : SYMBOL_GLOBE_CHOICES) if (id.equals(stored)) return id;
        return EMOJI;
    }

    /** '기호 자판에서는 다른 키로'의 선택지 이름. */
    public static String symbolGlobeLabel(String id) {
        switch (id) {
            case EMOJI: return L.t("이모지 키 (기본값)", "Emoji key (default)");
            case GLOBE: return L.t("기호 자판에서도 그대로", "Keep on the symbol keyboard");
            default: return label(id);
        }
    }

    /** 편집 화면에 보이는 작은 설명. 없으면 null. */
    public static String hint(String id) {
        switch (id) {
            case CURSOR: return L.t("상하좌우로 밀면 커서가 움직입니다.", "Swipe in any direction to move the cursor.");
            default: return null;
        }
    }

    /**
     * 저장된 순서(없으면 null)를 풀어낸다. 모르는 ID와 중복은 버리고, 빠진 키는 기본 순서대로 뒤에 붙인다.
     * 그래서 결과에는 언제나 모든 키가 한 번씩 들어 있다.
     */
    public static String[] order(String stored) {
        List<String> out = new ArrayList<>();
        if (stored != null) {
            for (String id : stored.split(",")) {
                id = id.trim();
                if (isKnown(id) && !out.contains(id)) out.add(id);
            }
        }
        for (String id : DEFAULT_ORDER) if (!out.contains(id)) out.add(id);
        return out.toArray(new String[0]);
    }

    public static String join(String[] order) {
        return String.join(",", order);
    }

    /** 설정을 바꾸지 않았을 때 보이는지: 쉼표 키와 더할 수 있는 키(이모지·클립보드·커서 이동·음성 입력)는 꺼져 있다. */
    public static boolean shownByDefault(String id) {
        return !COMMA.equals(id) && !EMOJI.equals(id) && !CLIPBOARD.equals(id) && !CURSOR.equals(id) && !VOICE.equals(id);
    }

    public static boolean isKnown(String id) {
        for (String k : DEFAULT_ORDER) if (k.equals(id)) return true;
        return false;
    }
}
