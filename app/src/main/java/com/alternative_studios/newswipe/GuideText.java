package com.alternative_studios.newswipe;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 사용법·개인정보 처리방침·오픈소스 라이선스처럼 긴 안내 글(assets/guide/*.txt)을 카드 단위로 나눈다.
 * <ul>
 *   <li>"## 제목" 줄이 새 카드를 시작한다. 첫 "##" 앞의 글은 제목 없는 첫 카드다.</li>
 *   <li>카드 안의 "~~~" 줄 뒤는 작은 글자로 보이는 부분이다 (라이선스 원문).</li>
 *   <li>"{이름}"은 화면에서 실제 값으로 바뀐다 (예: {CLIP_MAX}).</li>
 * </ul>
 */
final class GuideText {
    /** 카드 하나. title이 null이면 제목 없는 카드, small이 null이면 작은 글자 부분이 없다. */
    static final class Section {
        final String title, body, small;

        Section(String title, String body, String small) {
            this.title = title;
            this.body = body;
            this.small = small;
        }
    }

    private GuideText() {
    }

    static List<Section> parse(String text, Map<String, String> values) {
        if (values != null) {
            for (Map.Entry<String, String> e : values.entrySet()) text = text.replace("{" + e.getKey() + "}", e.getValue());
        }
        List<Section> out = new ArrayList<>();
        String title = null;
        StringBuilder body = new StringBuilder(), small = null;
        for (String line : text.replace("\r\n", "\n").split("\n", -1)) {
            if (line.startsWith("## ")) {
                add(out, title, body, small);
                title = line.substring(3).trim();
                body = new StringBuilder();
                small = null;
            } else if (line.trim().equals("~~~")) {
                small = new StringBuilder();
            } else {
                (small != null ? small : body).append(line).append('\n');
            }
        }
        add(out, title, body, small);
        return out;
    }

    private static void add(List<Section> out, String title, StringBuilder body, StringBuilder small) {
        String b = body.toString().trim(), s = small == null ? null : small.toString().trim();
        if (title == null && b.isEmpty() && s == null) return;
        out.add(new Section(title, b, s));
    }
}
