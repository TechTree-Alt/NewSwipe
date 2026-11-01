package com.alternative_studios.newswipe.cjk;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** 가나 도우미: 히라가나↔가타카나, 탁점·반탁점·작은 글자 돌리기, 로마자 → 가나. */
public final class Kana {
    private Kana() {
    }

    public static boolean isHiragana(char c) {
        return c >= 'ぁ' && c <= 'ゖ';
    }

    public static String toKatakana(String s) {
        StringBuilder b = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            b.append(isHiragana(c) ? (char) (c + 0x60) : c);
        }
        return b.toString();
    }

    // ---------------------------------------------------------------- ゛゜小 키

    /** 한 번 누를 때마다 바뀌는 차례. 각 묶음은 처음으로 돌아온다 (예: は→ば→ぱ→は). */
    private static final String[] CYCLES = {
            "かが", "きぎ", "くぐ", "けげ", "こご", "さざ", "しじ", "すず", "せぜ", "そぞ",
            "ただ", "ちぢ", "つっづ", "てで", "とど", "はばぱ", "ひびぴ", "ふぶぷ", "へべぺ", "ほぼぽ",
            "あぁ", "いぃ", "うぅゔ", "えぇ", "おぉ", "やゃ", "ゆゅ", "よょ", "わゎ",
    };
    private static final Map<Character, Character> NEXT = new HashMap<>();

    static {
        for (String c : CYCLES) {
            for (int i = 0; i < c.length(); i++) NEXT.put(c.charAt(i), c.charAt((i + 1) % c.length()));
        }
    }

    /** ゛゜小 키를 눌렀을 때 c가 바뀔 글자. 바뀌지 않으면 0. */
    public static char cycle(char c) {
        Character n = NEXT.get(c);
        return n == null ? 0 : n;
    }

    // ---------------------------------------------------------------- 로마자

    private static final Map<String, String> ROMAJI = new HashMap<>();
    /** 로마자 표의 키의 모든 앞부분 (더 칠 글자를 기다릴지 아는 데 쓴다). */
    private static final Set<String> PREFIXES = new HashSet<>();

    private static void put(String r, String k) {
        ROMAJI.put(r, k);
    }

    static {
        String vowels = "aiueo";
        String[][] rows = {{"", "あいうえお"}, {"k", "かきくけこ"}, {"s", "さしすせそ"}, {"t", "たちつてと"},
                {"n", "なにぬねの"}, {"h", "はひふへほ"}, {"m", "まみむめも"}, {"r", "らりるれろ"},
                {"g", "がぎぐげご"}, {"z", "ざじずぜぞ"}, {"d", "だぢづでど"}, {"b", "ばびぶべぼ"},
                {"p", "ぱぴぷぺぽ"}, {"x", "ぁぃぅぇぉ"}, {"l", "ぁぃぅぇぉ"}};
        for (String[] r : rows) for (int i = 0; i < 5; i++) put(r[0] + vowels.charAt(i), String.valueOf(r[1].charAt(i)));
        put("ya", "や"); put("yu", "ゆ"); put("yo", "よ"); put("ye", "いぇ"); put("yi", "い");
        put("wa", "わ"); put("wo", "を"); put("wi", "うぃ"); put("we", "うぇ"); put("wu", "う");
        put("shi", "し"); put("chi", "ち"); put("tsu", "つ"); put("fu", "ふ"); put("ji", "じ");
        put("si", "し"); put("ti", "ち"); put("tu", "つ"); put("hu", "ふ"); put("zi", "じ"); put("di", "ぢ"); put("du", "づ");
        put("n'", "ん"); put("xn", "ん"); put("-", "ー");   // nn은 아래 romaji()가 다음 글자를 보고 정한다
        put("kwa", "くぁ"); put("gwa", "ぐぁ"); put("xka", "ゕ"); put("xke", "ゖ"); put("lka", "ゕ"); put("lke", "ゖ");
        put("tha", "てゃ"); put("thu", "てゅ"); put("the", "てぇ"); put("tho", "てょ");
        put("dha", "でゃ"); put("dhu", "でゅ"); put("dhe", "でぇ"); put("dho", "でょ");
        put("wyi", "ゐ"); put("wye", "ゑ"); put("whi", "うぃ"); put("whe", "うぇ"); put("who", "うぉ");
        put("xtu", "っ"); put("ltu", "っ"); put("xtsu", "っ"); put("xya", "ゃ"); put("xyu", "ゅ"); put("xyo", "ょ");
        put("lya", "ゃ"); put("lyu", "ゅ"); put("lyo", "ょ"); put("xwa", "ゎ"); put("lwa", "ゎ");
        put("fa", "ふぁ"); put("fi", "ふぃ"); put("fe", "ふぇ"); put("fo", "ふぉ");
        put("va", "ゔぁ"); put("vi", "ゔぃ"); put("vu", "ゔ"); put("ve", "ゔぇ"); put("vo", "ゔぉ");
        put("thi", "てぃ"); put("dhi", "でぃ"); put("twu", "とぅ"); put("dwu", "どぅ");
        put("tsa", "つぁ"); put("tsi", "つぃ"); put("tse", "つぇ"); put("tso", "つぉ");
        put("she", "しぇ"); put("che", "ちぇ"); put("je", "じぇ");
        // 요음: きゃ … (자음 + y + 모음), sh/ch/j 계열
        String[][] yoon = {{"k", "き"}, {"s", "し"}, {"t", "ち"}, {"n", "に"}, {"h", "ひ"}, {"m", "み"}, {"r", "り"},
                {"g", "ぎ"}, {"z", "じ"}, {"j", "じ"}, {"d", "ぢ"}, {"b", "び"}, {"p", "ぴ"}, {"c", "ち"}, {"f", "ふ"}};
        String small = "ゃゅょ", yv = "auo";
        for (String[] y : yoon) {
            for (int i = 0; i < 3; i++) put(y[0] + "y" + yv.charAt(i), y[1] + small.charAt(i));
        }
        for (int i = 0; i < 3; i++) {
            put("sh" + yv.charAt(i), "し" + small.charAt(i));
            put("ch" + yv.charAt(i), "ち" + small.charAt(i));
            put("j" + yv.charAt(i), "じ" + small.charAt(i));
        }
        for (String k : ROMAJI.keySet()) for (int i = 1; i < k.length(); i++) PREFIXES.add(k.substring(0, i));
    }

    /**
     * 로마자 조각을 가나로 바꾼다. 바꾼 가나는 out에 더하고, 아직 더 칠 글자를 기다리는 나머지를 돌려준다.
     * 예: "k" → 그대로 "k", "ka" → か, "kk" → っ + "k", "nk" → ん + "k".
     * nn은 MS-IME처럼 다음 글자를 보고 정한다: 모음·y가 오면 ん + な행(onna → おんな, konnichiha → こんにちは),
     * 자음이 오거나(n 포함) 끝나면 ん (konnnichiha → こんにちは, shinbunn → しんぶん). ん 뒤에 모음을 쓰려면 n'을 친다 (tan'i).
     */
    public static String romaji(String pending, StringBuilder out) {
        String p = pending;
        while (!p.isEmpty()) {
            if (p.startsWith("nn")) {
                if (p.length() == 2) return p;   // 다음 글자를 기다린다
                char c = p.charAt(2);
                out.append('ん');
                p = "aiueoy".indexOf(c) >= 0 ? p.substring(1) : p.substring(2);
                continue;
            }
            String hit = ROMAJI.get(p);
            if (hit != null) {
                out.append(hit);
                return "";
            }
            if (PREFIXES.contains(p)) return p;   // 더 칠 글자를 기다린다
            char a = p.charAt(0);
            if (p.length() >= 2 && a == p.charAt(1) && a != 'n' && isConsonant(a)) {
                out.append('っ');
            } else if (a == 'n' && p.length() >= 2) {
                out.append('ん');
            } else {
                out.append(a);   // 가나가 되지 않는 글자는 그대로
            }
            p = p.substring(1);
        }
        return p;
    }

    /** 칠 글자가 끝났을 때 남은 로마자를 마무리한다 (n → ん, 나머지는 그대로). */
    public static String finishRomaji(String pending) {
        if (pending.equals("n") || pending.equals("nn")) return "ん";
        return pending;
    }

    private static boolean isConsonant(char c) {
        return c >= 'a' && c <= 'z' && "aiueo".indexOf(c) < 0;
    }
}
