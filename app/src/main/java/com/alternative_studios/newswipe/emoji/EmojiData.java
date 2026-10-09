package com.alternative_studios.newswipe.emoji;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * 이모지 목록과 검색. 데이터는 assets/emoji.tsv (tools/generate_emoji_data.py가 만듦).
 * 안드로이드 의존성이 없어 단위 테스트할 수 있다.
 */
public final class EmojiData {

    /** 탭 순서. 첫 번째(최근)는 데이터에 없고 사용 기록으로 채운다. */
    public static final String[] GROUPS = {
            "recent", "smileys", "people", "nature", "food", "travel", "activities", "objects", "symbols", "flags",
    };
    /** 탭 아이콘으로 쓸 대표 이모지. 각 탭에 실제로 들어 있는 이모지 중에서 고른다. */
    public static final String[] GROUP_ICONS = {
            "", "😀", "👋", "🐶", "🍔", "✈️", "⚽", "💡", "❤️", "🚩",
    };
    /** 하트는 기호 탭, 날씨는 자연 탭, 시계는 사물 탭에 있다 (tools/generate_emoji_data.py의 SUBGROUPS). */
    public static final String[] GROUP_NAMES = {
            "최근 사용", "표정", "사람·손", "동물·자연·날씨", "음식", "여행·장소", "활동", "사물", "하트·기호", "깃발",
    };

    public static final class Emoji {
        public final String value;
        /** 피부색 변형. 없으면 빈 배열. */
        public final String[] variants;
        /** 한국어/영어 키워드 원문('|'로 이어진 한 줄). 검색을 처음 쓸 때 나눈다. */
        private final String rawKo, rawEn;
        private String[] keywords;   // 한국어 + 영어 키워드 (소문자). 처음 쓸 때 만든다.
        private int koCount;

        Emoji(String value, String[] variants, String rawKo, String rawEn) {
            this.value = value;
            this.variants = variants;
            this.rawKo = rawKo;
            this.rawEn = rawEn;
        }

        /**
         * 검색용 키워드를 돌려준다. 검색을 쓰지 않으면 문자열 수만 개를 만들지 않는다.
         * 잠그지 않고 만들므로 검색은 한 번에 한 스레드에서만 해야 한다 (키보드는 검색 전용 스레드 하나에서 한다).
         */
        String[] keywords() {
            if (keywords == null) {
                String[] ko = rawKo.isEmpty() ? new String[0] : rawKo.split("\\|");
                String[] en = rawEn.isEmpty() ? new String[0] : rawEn.split("\\|");
                String[] kw = new String[ko.length + en.length];
                System.arraycopy(ko, 0, kw, 0, ko.length);
                System.arraycopy(en, 0, kw, ko.length, en.length);
                koCount = ko.length;
                keywords = kw;
            }
            return keywords;
        }

        int koCount() {
            keywords();
            return koCount;
        }

        /** 한국어 이름 (없으면 영어 이름). */
        public String name() {
            String first = !rawKo.isEmpty() ? rawKo : rawEn;
            int bar = first.indexOf('|');
            if (bar >= 0) first = first.substring(0, bar);
            return first.isEmpty() ? value : first;
        }
    }

    /** 기기 글꼴이 그릴 수 있는 이모지인지 판단 (안드로이드에서는 Paint.hasGlyph). */
    public interface GlyphFilter {
        boolean canShow(String emoji);
    }

    private final List<List<Emoji>> groups = new ArrayList<>();
    private final List<Emoji> all = new ArrayList<>();

    private EmojiData() {
        for (int i = 0; i < GROUPS.length; i++) groups.add(new ArrayList<>());
    }

    public static EmojiData parse(Reader reader, GlyphFilter filter) throws IOException {
        EmojiData d = new EmojiData();
        BufferedReader br = new BufferedReader(reader, 16 * 1024);
        String line;
        while ((line = br.readLine()) != null) {
            if (line.isEmpty() || line.charAt(0) == '#') continue;
            String[] f = line.split("\t", -1);
            if (f.length < 5) continue;
            int g = indexOf(GROUPS, f[0]);
            if (g < 0) continue;
            String value = f[1];
            if (filter != null && !filter.canShow(value)) continue;
            String[] variants = f[2].isEmpty() ? new String[0] : f[2].split(" ");
            if (filter != null && variants.length > 0 && !filter.canShow(variants[0])) variants = new String[0];
            Emoji e = new Emoji(value, variants, f[3], f[4]);
            d.groups.get(g).add(e);
            d.all.add(e);
        }
        return d;
    }

    private static int indexOf(String[] a, String s) {
        for (int i = 0; i < a.length; i++) if (a[i].equals(s)) return i;
        return -1;
    }

    public List<Emoji> group(int index) {
        return Collections.unmodifiableList(groups.get(index));
    }

    public int size() {
        return all.size();
    }

    public Emoji find(String value) {
        for (Emoji e : all) {
            if (e.value.equals(value)) return e;
            for (String v : e.variants) if (v.equals(value)) return e;
        }
        return null;
    }

    /**
     * 한국어 또는 영어로 검색한다. 띄어쓰기로 나눈 모든 단어가 맞아야 한다.
     * 이름과 정확히 같으면 가장 앞, 단어로 시작하면 그다음, 포함하면 그다음 순서.
     */
    public List<Emoji> search(String query, int limit) {
        String q = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        List<Emoji> out = new ArrayList<>();
        if (q.isEmpty()) return out;
        String[] tokens = q.split("\\s+");
        List<int[]> scored = new ArrayList<>();
        for (int i = 0; i < all.size(); i++) {
            Emoji e = all.get(i);
            int total = 0;
            for (String t : tokens) {
                int s = score(e, t);
                if (s == 0) {
                    total = 0;
                    break;
                }
                total += s;
            }
            if (total == 0 && tokens.length > 1) {
                // 여러 단어 검색어를 통째로 이름과 맞춰 본다 ("웃는 얼굴").
                total = score(e, q);
            }
            if (total > 0) scored.add(new int[]{total, i});
        }
        Collections.sort(scored, (a, b) -> a[0] != b[0] ? b[0] - a[0] : a[1] - b[1]);
        for (int i = 0; i < scored.size() && out.size() < limit; i++) out.add(all.get(scored.get(i)[1]));
        return out;
    }

    private static int score(Emoji e, String t) {
        int best = 0;
        String[] keywords = e.keywords();
        int koCount = e.koCount();
        for (int i = 0; i < keywords.length; i++) {
            String k = keywords[i];
            boolean isName = i == 0 || i == koCount;
            int s;
            if (k.equals(t)) s = isName ? 100 : 60;
            else if (k.startsWith(t)) s = isName ? 70 : 30;
            else if (k.contains(t)) s = isName ? 20 : 10;
            else continue;
            if (s > best) best = s;
        }
        return best;
    }
}
