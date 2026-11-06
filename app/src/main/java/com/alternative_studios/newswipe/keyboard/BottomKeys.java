package com.alternative_studios.newswipe.keyboard;

import java.util.ArrayList;
import java.util.List;

/**
 * 하단 키 완전 사용자화: 맨 아래 줄 키들(기호 키, 쉼표, 지구본 키, 스페이스바, 온점, 엔터와, 처음에는 꺼져 있는
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

    private BottomKeys() {
    }

    /** 하단 키 편집 화면에 보이는 이름 (이름과 키 모양). */
    public static String label(String id) {
        switch (id) {
            case MODE: return "기호 키 (?123·가·ABC)";
            case COMMA: return "쉼표 키 (,)";
            case GLOBE: return "언어 전환 키 (🌐︎)";
            case SPACE: return "스페이스바 (␣)";
            case PERIOD: return "온점 키 (.)";
            case ENTER: return "엔터 키 (↵)";
            case EMOJI: return "이모지 키";
            case CLIPBOARD: return "클립보드 키";
            case CURSOR: return "커서 이동 키";
            case VOICE: return "음성 입력 키";
            default: return id;
        }
    }

    /** 편집 화면에 보이는 작은 설명. 없으면 null. */
    public static String hint(String id) {
        switch (id) {
            case GLOBE: return "기호 자판에서는 이모지 키도 함께 없어집니다.";
            case CURSOR: return "상하좌우로 밀면 커서가 움직입니다.";
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
