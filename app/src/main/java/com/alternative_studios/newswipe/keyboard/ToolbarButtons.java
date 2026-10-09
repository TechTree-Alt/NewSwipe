package com.alternative_studios.newswipe.keyboard;

import java.util.ArrayList;
import java.util.List;

/**
 * 도구 막대 버튼의 순서와 붙는 쪽(왼쪽·오른쪽). 표시 여부는 버튼마다 따로 저장한다 (Prefs).
 * 순서는 "왼쪽 버튼들|오른쪽 버튼들" 꼴로, 각 쪽은 버튼 ID를 쉼표로 이어 저장한다.
 * 추천 단어는 왼쪽 버튼들 오른쪽에 뜨고, 추천이 떠 있는 동안 오른쪽 버튼들은 접힌다.
 */
public final class ToolbarButtons {
    public static final String CLIPBOARD = "clipboard", EMOJI = "emoji", VOICE = "voice", UNDO = "undo",
            SETTINGS = "settings", HIDE = "hide", ONE_HAND = "onehand";
    /** 기본 배치. */
    public static final String[] DEFAULT_LEFT = {CLIPBOARD, EMOJI, VOICE, UNDO};
    public static final String[] DEFAULT_RIGHT = {ONE_HAND, SETTINGS, HIDE};
    public static final String[] ALL = {CLIPBOARD, EMOJI, VOICE, UNDO, ONE_HAND, SETTINGS, HIDE};

    private ToolbarButtons() {
    }

    /** 편집 화면에 보이는 이름. */
    public static String label(String id) {
        switch (id) {
            case CLIPBOARD: return "클립보드 버튼";
            case EMOJI: return "이모지 버튼";
            case VOICE: return "음성 입력 버튼";
            case UNDO: return "실행 취소 버튼";
            case SETTINGS: return "설정 버튼";
            case HIDE: return "키보드 숨기기 버튼";
            case ONE_HAND: return "한 손 모드 버튼";
            default: return id;
        }
    }

    /** 편집 화면에 보이는 작은 설명. 없으면 null. */
    public static String hint(String id) {
        switch (id) {
            case VOICE: return "기기의 음성 입력(예: Google 음성 입력)을 사용합니다.";
            case UNDO: return "입력란에 Ctrl+Z를 보냅니다. 앱이 지원하면 방금 한 입력이나 지우기를 되돌립니다.";
            case ONE_HAND: return "누르면 한 손 모드를 켜고 끕니다. 왼쪽·오른쪽으로 밀면 그쪽으로 쏠린 한 손 모드가 됩니다.";
            default: return null;
        }
    }

    public static int icon(String id) {
        switch (id) {
            case CLIPBOARD: return Icons.CLIPBOARD;
            case EMOJI: return Icons.EMOJI;
            case VOICE: return Icons.MIC;
            case UNDO: return Icons.UNDO;
            case SETTINGS: return Icons.SETTINGS;
            case ONE_HAND: return Icons.ONE_HAND;
            default: return Icons.HIDE;
        }
    }

    /**
     * 저장된 배치(없으면 null)를 {왼쪽, 오른쪽}으로 풀어낸다. 모르는 ID와 중복은 버리고,
     * 빠진 버튼은 기본으로 붙던 쪽 끝에 붙인다. 그래서 결과에는 언제나 모든 버튼이 한 번씩 들어 있다.
     */
    public static String[][] order(String stored) {
        List<String> left = new ArrayList<>(), right = new ArrayList<>();
        if (stored != null) {
            String[] sides = stored.split("\\|", -1);
            for (int s = 0; s < Math.min(2, sides.length); s++) {
                for (String id : sides[s].split(",")) {
                    id = id.trim();
                    if (isKnown(id) && !left.contains(id) && !right.contains(id)) (s == 0 ? left : right).add(id);
                }
            }
        }
        for (String id : DEFAULT_LEFT) if (!left.contains(id) && !right.contains(id)) left.add(id);
        for (String id : DEFAULT_RIGHT) if (!left.contains(id) && !right.contains(id)) right.add(id);
        return new String[][]{left.toArray(new String[0]), right.toArray(new String[0])};
    }

    public static String join(String[] left, String[] right) {
        return String.join(",", left) + "|" + String.join(",", right);
    }

    public static boolean isKnown(String id) {
        for (String k : ALL) if (k.equals(id)) return true;
        return false;
    }
}
