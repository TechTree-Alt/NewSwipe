package com.alternative_studios.newswipe.emoji;

import com.alternative_studios.newswipe.L;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 이모지 목록과 검색. 데이터는 assets/emoji.bin (tools/generate_emoji_data.py가 만들어 zlib으로 압축한 글, {@link #parse}).
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
    public static String[] groupNames() {
        return new String[]{
            L.t("최근 사용", "Recent"), L.t("표정", "Smileys"), L.t("사람·손", "People & hands"), L.t("동물·자연·날씨", "Animals, nature & weather"), L.t("음식", "Food"), L.t("여행·장소", "Travel & places"), L.t("활동", "Activities"), L.t("사물", "Objects"), L.t("하트·기호", "Hearts & symbols"), L.t("깃발", "Flags"),
    };
    }

    public static final class Emoji {
        public final String value;
        /** 피부색 변형. 없으면 빈 배열. */
        public final String[] variants;
        /** 한국어/영어 키워드 원문('|'로 이어진 한 줄). 검색을 처음 쓸 때 나눈다. */
        private final String rawKo, rawEn;
        private String[] keywords;   // 한국어 + 영어 키워드 (소문자). 처음 쓸 때 만든다.
        private int koCount;
        /** 앞에서부터 이름으로 보는 키워드 수 (한국어, 영어). 보통 1이고, 나라 국기는 '깃발: …'까지다. */
        private int koNames, enNames;

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
                koNames = nameCount(ko, L.t("깃발: ", "Flag: "));
                enNames = nameCount(en, "flag: ");
                keywords = kw;
            }
            return keywords;
        }

        /**
         * i번째 키워드가 이름인지. 각 언어의 첫 키워드가 이름이고, 나라 국기는 나라 이름·다른 이름('한국', '우리나라')부터
         * '깃발: 대한민국'까지가 모두 이름이다 (tools/generate_emoji_data.py의 flag_words). 이름으로 맞으면 먼저 보여 준다.
         */
        boolean isName(int i) {
            keywords();
            return i < koCount ? i < koNames : i - koCount < enNames;
        }

        /** 이름으로 볼 키워드 수: prefix로 시작하는 키워드까지, 없으면 첫 키워드 하나. */
        private static int nameCount(String[] words, String prefix) {
            for (int i = 0; i < words.length; i++) if (words[i].startsWith(prefix)) return i + 1;
            return Math.min(1, words.length);
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

    /**
     * 이모지 목록을 읽는다. 한 줄에 한 이모지(그룹 \t 이모지 \t 변형 \t 한국어 검색어 \t 영어 검색어)거나, '#columns N' 줄
     * 다음에 열마다 N줄씩(그룹 N줄, 이모지 N줄, ...) 모은 것이다. 앱에 든 파일은 뒤의 것이다 (같은 값끼리 붙어 있어 더 잘 압축된다).
     */
    public static EmojiData parse(Reader reader, GlyphFilter filter) throws IOException {
        EmojiData d = new EmojiData();
        BufferedReader br = new BufferedReader(reader, 16 * 1024);
        List<String[]> rows = new ArrayList<>();
        String line;
        while ((line = br.readLine()) != null) {
            if (line.startsWith("#columns ")) {
                rows = columns(br, line.substring(9).trim());
                break;
            }
            if (line.isEmpty() || line.charAt(0) == '#') continue;
            rows.add(line.split("\t", -1));
        }
        for (String[] f : rows) {
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

    /** '#columns N' 다음의 열 다섯 개(열마다 N줄)를 이모지마다의 칸으로 되돌린다. */
    private static List<String[]> columns(BufferedReader br, String count) throws IOException {
        int n;
        try {
            n = Integer.parseInt(count);
        } catch (NumberFormatException e) {
            throw new IOException("bad emoji columns");
        }
        if (n < 0 || n > 100_000) throw new IOException("bad emoji columns");
        String[][] f = new String[n][5];
        for (int col = 0; col < 5; col++) {
            for (int i = 0; i < n; i++) {
                String line = br.readLine();
                if (line == null) throw new IOException("emoji columns cut short");
                f[i][col] = line;
            }
        }
        return java.util.Arrays.asList(f);
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

    // ---------------------------------------------------------------- 추천 (이모지 창을 열 때)

    /** 추천에 보는 커서 앞 단어 수 (커서에서 가까운 쪽부터). */
    private static final int SUGGEST_TOKENS = 12;
    /** 추천에 보는 커서 뒤 단어 수 (커서에서 가까운 쪽부터). */
    private static final int SUGGEST_TOKENS_AFTER = 6;
    /** 이보다 많은 이모지에 붙은 키워드(얼굴·손 등)는 너무 흔해 추천에 쓰지 않는다. */
    private static final int GENERIC_KEYWORD = 24;
    /** 영어 단어 끝에 붙어도 같은 단어로 보는 꼬리 (cakes, partying …). */
    private static final String[] ENGLISH_TAILS = {"s", "es", "ed", "d", "ing", "er", "ers"};

    /**
     * 사전(CLDR) 키워드에 없지만 대화에서 흔히 쓰는 말 → 이모지. 추천에만 쓴다.
     * 키는 단어의 앞부분으로 맞추므로 어간까지만 적는다 ('고마' → 고마워·고마워요).
     */
    private static final String[][] CHAT_WORDS = {
            {"미안", "🙏 🥺 😢"}, {"죄송", "🙇 🙏"}, {"고마", "🙏 🥰 😊"}, {"배고", "🍚 🤤 😋"},
            {"배불", "😋 😌"}, {"맛있", "😋 🤤 👍"}, {"졸려", "😪 🥱 😴"}, {"졸리", "😪 🥱 😴"},
            {"피곤", "😩 🥱 😴"}, {"잘자", "😴 🌙 💤"}, {"화이팅", "💪 🔥 👊"}, {"파이팅", "💪 🔥 👊"},
            {"힘내", "💪 🔥 🙌"}, {"대박", "😮 🤩 👍 🔥"}, {"최고", "👍 🏆 🥇"}, {"수고", "👏 🙇 💪"},
            {"잘했", "👏 👍 💯"}, {"웃겨", "🤣 😂"}, {"웃기", "🤣 😂"}, {"ㅋㅋ", "😆 🤣 😂"},
            {"ㅠㅠ", "😭 😢 🥺"}, {"ㅜㅜ", "😭 😢 🥺"}, {"슬퍼", "😢 😭"}, {"슬프", "😢 😭"},
            {"화나", "😠 😡 😤"}, {"화났", "😠 😡 😤"}, {"짜증", "😤 😠 😡"}, {"더워", "🥵 ☀️ 🔥"},
            {"더운", "🥵 ☀️"}, {"추워", "🥶 ❄️ ⛄"}, {"추운", "🥶 ❄️"}, {"보고싶", "🥺 💕 😢"},
            {"시험", "📝 📚 ✏️"}, {"망했", "😱 😭 🫠"}, {"오키", "👌 👍"}, {"ㅇㅋ", "👌 👍"},
    };

    /** 키워드(소문자) → 그 키워드를 가진 이모지 (데이터 순). 추천을 처음 쓸 때 만든다. 검색과 같은 스레드에서만 쓴다. */
    private Map<String, List<Emoji>> index;
    /** 어떤 이모지의 이름인 키워드 → 색인 목록 앞쪽에 있는, 그 키워드가 이름인 이모지 수. */
    private Map<String, Integer> named;

    /**
     * 글(커서 앞의 입력)에 맞는 이모지를 추천한다. 커서에 가까운 단어부터 본다.
     * 단어의 앞부분이 키워드와 같으면 맞은 것으로 본다 (가장 긴 키워드 하나): '축하합니다!' → '축하',
     * '생일이야' → '생일'. 한국어는 두 글자 이상 키워드만 쓰며 어미·조사가 붙어도 되고,
     * 영어는 두 글자 이상 키워드가 단어 전체와 같거나 정해 둔 꼬리(s, ing …)만 붙은 경우에 쓴다. 너무 흔한 키워드는 건너뛴다.
     * 단어가 바로 이름인 이모지(나라 이름 → 국기)를 먼저 담고, 나머지는 커서에 가까운 단어부터 하나씩 번갈아 담아
     * 한 단어가 자리를 다 차지하지 않게 한다.
     * 잠그지 않으므로 검색과 같은 한 스레드에서만 불러야 한다.
     */
    public List<String> suggest(CharSequence text, int limit) {
        return suggest(text, null, limit);
    }

    /**
     * 커서 앞의 글(before)과 커서 뒤의 글(after)을 함께 보고 추천한다. 앞은 마지막 {@link #SUGGEST_TOKENS}단어,
     * 뒤는 처음 {@link #SUGGEST_TOKENS_AFTER}단어만 본다. 커서에서 가까운 단어부터 앞뒤를 번갈아 본다
     * (앞 1번째 → 뒤 1번째 → 앞 2번째 → 뒤 2번째 …). 같은 거리면 방금 쓴 앞 단어가 먼저다. 커서가 단어 한가운데('생|일')에 있으면 두 조각을 한 단어로 붙여 본다.
     */
    public List<String> suggest(CharSequence before, CharSequence after, int limit) {
        List<String> out = new ArrayList<>();
        if ((before == null && after == null) || limit <= 0) return out;
        String b = before == null ? "" : before.toString();
        String a = after == null ? "" : after.toString();
        String[] head = split(b);
        String[] tail = split(a);
        // 커서가 단어 안에 있으면 앞 글의 마지막 조각과 뒤 글의 첫 조각이 한 단어다.
        if (head.length > 0 && tail.length > 0 && !b.isEmpty() && !a.isEmpty()
                && !Character.isWhitespace(b.charAt(b.length() - 1)) && !Character.isWhitespace(a.charAt(0))) {
            head[head.length - 1] = head[head.length - 1] + tail[0];
            tail[0] = "";
        }
        if (index == null) buildIndex();
        List<List<Emoji>> lists = new ArrayList<>();
        List<String> keys = new ArrayList<>();
        List<String> near = new ArrayList<>();   // 커서 앞 단어, 커서에 가까운 순
        for (int i = head.length - 1; i >= 0 && near.size() < SUGGEST_TOKENS; i--) {
            String token = clean(head[i]);
            if (!token.isEmpty()) near.add(token);
        }
        List<String> next = new ArrayList<>();   // 커서 뒤 단어, 커서에 가까운 순
        for (int i = 0; i < tail.length && next.size() < SUGGEST_TOKENS_AFTER; i++) {
            String token = clean(tail[i]);
            if (!token.isEmpty()) next.add(token);
        }
        // 커서에서 가까운 단어부터: 앞 1번째, 뒤 1번째, 앞 2번째, 뒤 2번째 … 한쪽이 떨어지면 남은 쪽만 이어 본다.
        for (int i = 0; i < Math.max(near.size(), next.size()); i++) {
            if (i < near.size()) addToken(near.get(i), lists, keys);
            if (i < next.size()) addToken(next.get(i), lists, keys);
        }
        Set<String> seen = new HashSet<>();
        // 단어가 바로 이름인 이모지('일본' → 🇯🇵, '피자' → 🍕)를 먼저 담는다.
        for (int t = 0; t < lists.size() && out.size() < limit; t++) {
            Integer n = named.get(keys.get(t));
            for (int i = 0; n != null && i < n && out.size() < limit; i++) {
                String v = lists.get(t).get(i).value;
                if (seen.add(v)) out.add(v);
            }
        }
        for (int n = 0, left = lists.size(); left > 0 && out.size() < limit; n++) {
            left = 0;
            for (int t = 0; t < lists.size() && out.size() < limit; t++) {
                List<Emoji> list = lists.get(t);
                if (n >= list.size()) continue;
                left++;
                String v = list.get(n).value;
                if (seen.add(v)) out.add(v);
            }
        }
        return out;
    }

    private static String[] split(String text) {
        String t = text.trim();
        return t.isEmpty() ? new String[0] : t.split("\\s+");
    }

    /** 단어 하나(clean을 거친, 비어 있지 않은 단어)에 맞는 키워드의 이모지 목록을 lists에 더한다. */
    private void addToken(String token, List<List<Emoji>> lists, List<String> keys) {
        String key = matchKeyword(token);
        if (key == null) return;
        lists.add(index.get(key));
        keys.add(key);
        // '중국어'는 그 자체가 키워드(㊗️)라 '중국'을 건너뛴다. 한국어 단어의 앞부분이 어떤 이모지의 이름이면(🇨🇳) 함께 쓴다.
        String shorter = shorterName(token, key);
        if (shorter != null) {
            lists.add(index.get(shorter));
            keys.add(shorter);
        }
    }

    /**
     * 키워드 색인을 만든다. 키워드마다 그 키워드가 이름인 이모지('일본' → 🇯🇵)를 앞에 두고 나머지는 데이터 순서대로 둔다.
     * 어떤 이모지의 이름인 키워드는 이모지가 많이 붙어 있어도 흔한 키워드로 보지 않는다.
     */
    private void buildIndex() {
        Map<String, List<Emoji>> m = new HashMap<>();
        Map<String, Integer> names = new HashMap<>();
        for (int pass = 0; pass < 2; pass++) {   // 0: 이름으로 붙은 것, 1: 나머지
            for (Emoji e : all) {
                String[] kw = e.keywords();
                for (int i = 0; i < kw.length; i++) {
                    String k = kw[i];
                    if (k.isEmpty() || (pass == 0) != e.isName(i)) continue;
                    List<Emoji> list = m.get(k);
                    if (list == null) {
                        list = new ArrayList<>(2);
                        m.put(k, list);
                    }
                    if (list.contains(e)) continue;
                    list.add(e);
                    if (pass == 0) names.put(k, list.size());
                }
            }
        }
        Map<String, Emoji> byValue = new HashMap<>();
        for (Emoji e : all) byValue.put(e.value, e);
        for (String[] w : CHAT_WORDS) {
            List<Emoji> list = m.get(w[0]);
            if (list == null) {
                list = new ArrayList<>(3);
                m.put(w[0], list);
            }
            for (String v : w[1].split(" ")) {
                Emoji e = byValue.get(v);   // 기기 글꼴에 없어 빠진 이모지는 건너뛴다
                if (e != null && !list.contains(e)) list.add(e);
            }
            if (list.isEmpty()) m.remove(w[0]);
        }
        index = m;
        named = names;
    }

    /** 단어 앞뒤의 문장 부호·기호를 떼고 소문자로 바꾼다 ('축하합니다!' → '축하합니다'). */
    private static String clean(String token) {
        int start = 0, end = token.length();
        while (start < end && !Character.isLetterOrDigit(token.codePointAt(start))) {
            start += Character.charCount(token.codePointAt(start));
        }
        while (end > start && !Character.isLetterOrDigit(token.codePointBefore(end))) {
            end -= Character.charCount(token.codePointBefore(end));
        }
        return token.substring(start, end).toLowerCase(Locale.ROOT);
    }

    /** 단어에 맞는 가장 긴 키워드. 없으면 null. */
    private String matchKeyword(String token) {
        boolean hangul = isHangul(token);
        int cps = token.codePointCount(0, token.length());
        for (int n = cps; n >= 1; n--) {
            String key = token.substring(0, token.offsetByCodePoints(0, n));
            List<Emoji> list = index.get(key);
            if (list == null || (list.size() > GENERIC_KEYWORD && !named.containsKey(key))) continue;
            if (n < 2) break;   // 한 글자 키워드('자', 'i' …)는 뜻이 너무 여러 가지라 쓰지 않는다
            if (n == cps) return key;   // 단어 전체가 키워드
            if (hangul) return key;   // 한국어: 어미·조사가 붙은 경우
            if (isEnglishTail(token.substring(key.length()))) return key;
        }
        return null;
    }

    /** 한국어 단어에서 key보다 짧은, 어떤 이모지의 이름인 가장 긴 앞부분(두 글자 이상). 없으면 null. */
    private String shorterName(String token, String key) {
        if (named.containsKey(key) || !isHangul(token)) return null;
        for (int n = key.codePointCount(0, key.length()) - 1; n >= 2; n--) {
            String k = token.substring(0, token.offsetByCodePoints(0, n));
            if (named.containsKey(k)) return k;
        }
        return null;
    }

    private static boolean isHangul(String token) {
        for (int i = 0; i < token.length(); i++) {
            char c = token.charAt(i);
            if ((c >= '가' && c <= '힣') || (c >= 'ㄱ' && c <= 'ㅣ')) return true;
        }
        return false;
    }

    private static boolean isEnglishTail(String rest) {
        for (String t : ENGLISH_TAILS) if (t.equals(rest)) return true;
        return false;
    }

    private static int score(Emoji e, String t) {
        int best = 0;
        String[] keywords = e.keywords();
        for (int i = 0; i < keywords.length; i++) {
            String k = keywords[i];
            boolean isName = e.isName(i);
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
