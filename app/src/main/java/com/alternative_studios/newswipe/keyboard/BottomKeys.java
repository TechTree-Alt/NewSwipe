package com.alternative_studios.newswipe.keyboard;

import java.util.ArrayList;
import java.util.List;

/**
 * 하단 키 완전 사용자화: 맨 아래 줄 키들(기호 키, 쉼표, 지구본 키, 스페이스바, 온점, 엔터)의 순서와 표시 여부.
 * 순서는 키 ID를 쉼표로 이은 문자열로 저장한다. 표시 여부는 키마다 따로 저장한다 (Prefs).
 */
public final class BottomKeys {
    public static final String MODE = "mode", COMMA = "comma", GLOBE = "globe", SPACE = "space",
            PERIOD = "period", ENTER = "enter";
    /** 기본 순서. */
    public static final String[] DEFAULT_ORDER = {MODE, COMMA, GLOBE, SPACE, PERIOD, ENTER};

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
            default: return id;
        }
    }

    /** 편집 화면에 보이는 작은 설명. 없으면 null. */
    public static String hint(String id) {
        return GLOBE.equals(id) ? "기호 자판에서는 이모지 키도 함께 없어집니다." : null;
    }

    /**
     * 저장된 순서(없으면 null)를 풀어낸다. 모르는 ID와 중복은 버리고, 빠진 키는 기본 순서대로 뒤에 붙인다.
     * 그래서 결과에는 언제나 여섯 키가 한 번씩 들어 있다.
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

    public static boolean isKnown(String id) {
        for (String k : DEFAULT_ORDER) if (k.equals(id)) return true;
        return false;
    }
}
