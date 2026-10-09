package com.alternative_studios.newswipe.keyboard;

import com.alternative_studios.newswipe.Prefs;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 자판 배열 정의.
 *
 * <p>한글은 커키 키보드의 '단모음' 자판과 같은 8열 배열이 기본이고, 설정에서 NewSwipe 단모음(7열)을 고를 수 있다.
 * 쌍자음·이중모음이 있는 키는 아래로 스와이프하면 그 글자가 입력된다.
 * NewSwipe 단모음은 Shift 키가 없고, ㅂ·ㅈ·ㄷ·ㄱ를 오른쪽으로 밀어 ㅍ·ㅊ·ㅌ·ㅋ을 입력한다.
 * 획을 더하는 방향이라 자연스러워 키에 ㅊ·ㅌ을 함께 적지는 않는다.
 * 겹받침의 앞 자음(ㄱ ㄴ ㄹ ㅂ)은 위·왼쪽·오른쪽으로 스와이프해 겹받침을 입력한다 ({@link FinalSwipes}).
 */
public final class KeyboardLayout {
    public static final int KOREAN = 0;
    public static final int ENGLISH = 1;
    public static final int SYMBOLS = 2;
    public static final int SYMBOLS_2 = 3;
    public static final int NUMBER = 4;

    public static final class Row {
        public final Key[] keys;
        /** 일반 줄 = 1.0 기준 상대 높이. */
        public final float height;

        Row(float height, Key... keys) {
            this.height = height;
            this.keys = keys;
        }
    }

    public final int kind;
    /** 한 줄에 들어가는 1.0 너비 키의 수. */
    public final int columns;
    public final Row[] rows;

    private KeyboardLayout(int kind, int columns, List<Row> rows) {
        this.kind = kind;
        this.columns = columns;
        this.rows = rows.toArray(new Row[0]);
    }

    public boolean isLetters() {
        return kind == KOREAN || kind == ENGLISH;
    }

    public float totalHeight() {
        float h = 0;
        for (Row r : rows) h += r.height;
        return h;
    }

    // ---------------------------------------------------------------- 배열

    private static final String[][] KOREAN_ROWS = {
            {"ㅂ", "ㅈ", "ㄷ", "ㄱ", "ㅅ", "ㅗ", "ㅐ", "ㅔ"},
            {"ㅁ", "ㄴ", "ㅇ", "ㄹ", "ㅎ", "ㅓ", "ㅏ", "ㅣ"},
            {"ㅋ", "ㅌ", "ㅊ", "ㅍ", "ㅜ", "ㅡ"},
    };
    /** NewSwipe 단모음. 셋째 줄 끝에 ⌫가 오고, Shift 키는 없다 (쌍자음은 아래로, ㅊ·ㅌ은 ㅈ·ㄷ를 위로 밀기). */
    private static final String[][] D7_ROWS = {
            {"ㅂ", "ㅈ", "ㄷ", "ㄱ", "ㅗ", "ㅐ", "ㅔ"},
            {"ㅁ", "ㄴ", "ㅇ", "ㄹ", "ㅓ", "ㅏ", "ㅣ"},
            {"ㅍ", "ㅋ", "ㅎ", "ㅅ", "ㅜ", "ㅡ"},
    };
    private static final String[][][] D7_POPUPS = {
            {{"1"}, {"2"}, {"3"}, {"4"}, {"5"}, {"6"}, {"7"}},
            {{"@", "*", "★", "☆", "※"}, {"#", "&"}, {"₩", "$", "€", "£", "¥"}, {"%", "‰"}, {"8"}, {"9"}, {"0"}},
            {{"-", "–", "—", "·"}, {"_", "~"}, {"(", "[", "{", "<", "「", "『"},
                    {")", "]", "}", ">", "」", "』"}, {"!", "¡"}, {"?", "¿"}},
    };

    /** NewSwipe 배열에서 오른쪽으로 밀어 입력하는 자음: ㅂ→ㅍ, ㅈ→ㅊ, ㄷ→ㅌ, ㄱ→ㅋ. */
    private static final String NS_RIGHT_FROM = "ㅂㅈㄷㄱ", NS_RIGHT_TO = "ㅍㅊㅌㅋ";

    /** NewSwipe 배열에서 key를 오른쪽으로 밀었을 때 들어갈 자음 (ㅂ→ㅍ, ㅈ→ㅊ, ㄷ→ㅌ, ㄱ→ㅋ). 없으면 null. */
    public static String nsRight(String key) {
        int i = key == null || key.length() != 1 ? -1 : NS_RIGHT_FROM.indexOf(key.charAt(0));
        return i < 0 ? null : String.valueOf(NS_RIGHT_TO.charAt(i));
    }

    /** NewSwipe 배열(NewSwipe 단모음)인지: Shift 키가 없고, ㅂ·ㅈ·ㄷ·ㄱ를 오른쪽으로 밀어 ㅍ·ㅊ·ㅌ·ㅋ. */
    private static boolean newSwipe(Prefs prefs) {
        return prefs != null && prefs.koreanNewSwipe();
    }

    /** 한글 자판 한 줄의 칸 수: NewSwipe 단모음은 7, 8열 단모음은 8. */
    public static int koreanColumns(Prefs prefs) {
        return newSwipe(prefs) ? 7 : 8;
    }

    /** NewSwipe 배열의 r번째 글자 줄. 셋째 줄 끝에는 ⌫(없애면 빈 칸)를 둔다. */
    private static Row nsRow(int r, Prefs prefs) {
        Key[] letters = letterKeys(D7_ROWS[r], D7_POPUPS[r], true, prefs);
        List<Key> keys = new ArrayList<>(java.util.Arrays.asList(letters));
        if (r == 2) keys.add(fn(deleteKey(prefs) ? Key.DELETE : Key.SPACER, "", 1f));
        return new Row(1f, keys.toArray(new Key[0]));
    }

    private static final String[][][] KOREAN_POPUPS = {
            {{"1"}, {"2"}, {"3"}, {"4"}, {"5"}, {"6"}, {"7"}, {"8"}},
            {{"@"}, {"#"}, {"₩", "$", "€", "£", "¥"}, {"%", "‰"}, {"&"},
                    {"*", "★", "☆", "※"}, {"9"}, {"0"}},
            {{"-", "–", "—", "·"}, {"_", "~"}, {"(", "[", "{", "<", "「", "『"},
                    {")", "]", "}", ">", "」", "』"}, {"!", "¡"}, {"?", "¿"}},
    };

    private static final String[][] ENGLISH_ROWS = {
            {"q", "w", "e", "r", "t", "y", "u", "i", "o", "p"},
            {"a", "s", "d", "f", "g", "h", "j", "k", "l"},
            {"z", "x", "c", "v", "b", "n", "m"},
    };
    private static final String[][][] ENGLISH_POPUPS = {
            {{"1"}, {"2"}, {"3", "é", "è", "ê", "ë", "ē"}, {"4"}, {"5", "þ"}, {"6", "ý", "ÿ"},
                    {"7", "ú", "ù", "û", "ü", "ū"}, {"8", "í", "ì", "î", "ï", "ī"},
                    {"9", "ó", "ò", "ô", "ö", "õ", "ø", "œ"}, {"0"}},
            {{"@", "à", "á", "â", "ä", "ã", "å", "æ"}, {"#", "ß", "ś", "š"}, {"$", "₩", "€", "£", "¥", "ð"},
                    {"%"}, {"&"}, {"-", "_"}, {"+", "="}, {"(", "[", "{", "<"}, {")", "]", "}", ">"}},
            {{"*", "ž", "ź"}, {"\""}, {"'", "ç", "ć", "č"}, {":"}, {";"}, {"!", "ñ", "ń"}, {"?"}},
    };

    private static final String[] COMMA_POPUP = {";", ":", "/", "'", "\""};
    /** 이메일·인터넷 주소 입력란에서 온점 키를 길게 누르면 나오는 도메인 (사용자가 정한 길게 누르기 문자 대신). */
    static final String[] DOMAIN_PERIOD_POPUP = {".com", ".net", ".org", ".co.kr", ".kr", ".ac.kr"};
    private static final String[] PERIOD_POPUP = {"?", "!", "…", "~", "·", "'", "\"", ":", ";"};

    /** 길게 누르기 문자 편집 화면에서 쓰는 키 묶음 이름. */
    public static final String GROUP_KO = "ko", GROUP_EN = "en", GROUP_SYM = "sym";

    /** 편집할 수 있는 키들(줄 단위). 마지막 줄은 쉼표/온점으로, 두 언어가 함께 쓴다. 한글은 설정한 배열을 따른다. */
    public static String[][] editableRows(boolean korean, Prefs prefs) {
        String[][] letters = korean ? (newSwipe(prefs) ? D7_ROWS : KOREAN_ROWS) : ENGLISH_ROWS;
        String[][] rows = new String[letters.length + 1][];
        System.arraycopy(letters, 0, rows, 0, letters.length);
        rows[letters.length] = new String[]{",", "."};
        return rows;
    }

    /**
     * 이메일·인터넷 주소 입력란용: 하단 온점 키(".")를 길게 누르면 사용자가 정한 문자 대신 .com·.net·.org·.co.kr 같은 도메인이 나온다.
     * 온점 키를 다른 글자로 바꿨으면 그 키는 건드리지 않는다. 숫자 자판에는 하단 온점 키가 없다.
     */
    public static void useDomainPeriodPopup(KeyboardLayout layout) {
        for (Row row : layout.rows) {
            for (Key k : row.keys) {
                if (k.type == Key.CHAR && ".".equals(k.slot()) && ".".equals(k.label)) {
                    k.popup = DOMAIN_PERIOD_POPUP;
                    k.noRepeat = true;
                }
            }
        }
    }

    public static String groupOf(boolean korean, String label) {
        return label.equals(",") || label.equals(".") ? GROUP_SYM : korean ? GROUP_KO : GROUP_EN;
    }

    /** 고치지 않았을 때의 길게 누르기 문자. */
    public static String[] defaultPopup(boolean korean, Prefs prefs, String label, boolean periodComma) {
        if (label.equals(",")) return COMMA_POPUP;
        if (label.equals(".")) return periodPopup(periodComma);
        boolean ns = newSwipe(prefs);
        String[][] letters = korean ? (ns ? D7_ROWS : KOREAN_ROWS) : ENGLISH_ROWS;
        String[][][] popups = korean ? (ns ? D7_POPUPS : KOREAN_POPUPS) : ENGLISH_POPUPS;
        for (int r = 0; r < letters.length; r++) {
            for (int i = 0; i < letters[r].length; i++) {
                if (letters[r][i].equals(label)) return popups[r][i];
            }
        }
        return null;
    }

    private static String[] periodPopup(boolean periodComma) {
        if (!periodComma) return PERIOD_POPUP;
        String[] p = new String[PERIOD_POPUP.length + 1];
        p[0] = ",";
        System.arraycopy(PERIOD_POPUP, 0, p, 1, PERIOD_POPUP.length);
        return p;
    }

    /** 사용자가 고친 값이 있으면 그것을, 없으면 기본값을 돌려준다. 빈 배열은 "없음"이라 null로 바꾼다. */
    private static String[] popupFor(Prefs prefs, boolean korean, String label, String[] def) {
        if (prefs == null) return def;
        String[] o = prefs.popupOverride(groupOf(korean, label), label);
        if (o == null) return def;
        return o.length == 0 ? null : o;
    }

    public static KeyboardLayout korean(Prefs prefs) {
        if (balanced(prefs)) return balancedLayout(prefs, prefs.numberRow());
        if (newSwipe(prefs)) return nsLayout(prefs, prefs.numberRow());
        boolean numberRow = prefs != null && prefs.numberRow();
        int shift = koreanShiftSlot(prefs);
        List<Row> rows = new ArrayList<>();
        if (numberRow) rows.add(numberRow());
        rows.add(letterRow(KOREAN_ROWS[0], KOREAN_POPUPS[0], true, prefs));
        rows.add(letterRow(KOREAN_ROWS[1], KOREAN_POPUPS[1], true, prefs));
        rows.add(bottomLetterRow(letterKeys(KOREAN_ROWS[2], KOREAN_POPUPS[2], true, prefs), 1f, shift, deleteKey(prefs)));
        rows.add(spaceRow(Key.TO_SYMBOLS, "?123", prefs));
        return new KeyboardLayout(KOREAN, 8, rows);
    }

    private static boolean balanced(Prefs prefs) {
        return prefs != null && prefs.balancedLayout();
    }

    /** 자·모음 균형 레이아웃의 전체 폭 (자음 쪽 반 + 모음 쪽 반). 맨 아래 줄의 폭(SPACE_ROW_UNITS)과 같다. */
    private static final int BALANCED_COLUMNS = 10;
    /** 반쪽의 폭: 자음 키들이 꼭 채운다. */
    private static final float BALANCED_HALF = BALANCED_COLUMNS / 2f;
    /** 오른쪽 절반의 모음 열 수. */
    private static final int BALANCED_VOWEL_COLUMNS = 3;

    /** 자·모음 균형 레이아웃에서 한 줄의 자음 수: 8열 단모음은 5개 (Shift 자리 포함), NewSwipe 배열은 4개. */
    private static int balancedConsonants(Prefs prefs) {
        return newSwipe(prefs) ? 4 : 5;
    }

    /** '모음 키 폭'(자음 키 폭의 %)의 최댓값: 모음 열들이 오른쪽 절반을 넘지 않는 데까지 (5칸 단위로 내림). */
    public static int balancedWidthMax(Prefs prefs) {
        float consonantW = BALANCED_HALF / balancedConsonants(prefs);
        int max = (int) Math.floor(BALANCED_HALF / (BALANCED_VOWEL_COLUMNS * consonantW) * 100f / 5f) * 5;
        return Math.min(Prefs.BALANCED_WIDTH_MAX, max);
    }

    /**
     * 자·모음 균형 레이아웃: 자판을 가운데에서 정확히 반으로 나눠 왼쪽 절반에는 자음이 꽉 차게(8열 단모음은 5열,
     * NewSwipe 배열은 4열), 오른쪽 절반에는 모음 열(3열)을 놓는다. 모음 키 폭은 자음 키 폭의 '모음 폭'%이고
     * (기본 같은 폭), 오른쪽 절반의 남는 폭 안에서 '모음 위치'에 따라 놓인다 (기본 가운데).
     * 자음도 '자음 폭'·'자음 위치'로 왼쪽 절반 안에서 폭과 위치를 바꿀 수 있다.
     */
    private static KeyboardLayout balancedLayout(Prefs prefs, boolean numberRow) {
        List<Row> rows = new ArrayList<>();
        if (numberRow) rows.add(prefs != null && prefs.splitView() ? splitNumberRow(prefs) : numberRow());
        for (int r = 0; r < 3; r++) rows.add(balancedRow(r, prefs));
        rows.add(spaceRow(Key.TO_SYMBOLS, "?123", prefs));
        return new KeyboardLayout(KOREAN, BALANCED_COLUMNS, rows);
    }

    /**
     * 자·모음 균형 레이아웃의 r번째 글자 줄. 고른 배열의 같은 줄을 자음(왼쪽 절반)과 모음(오른쪽 절반)으로 나눈다.
     * ⌫는 셋째 줄의 모음 쪽 끝에 온다. 8열 단모음의 셋째 줄은 왼쪽 끝에 Shift 자리가 온다 (NewSwipe 단모음에는 없다).
     * 자음과 모음 사이·모음 오른쪽의 빈틈은 누르면 가까운 키가 눌린다.
     */
    private static Row balancedRow(int r, Prefs prefs) {
        boolean ns = newSwipe(prefs);
        String[][] rows = ns ? D7_ROWS : KOREAN_ROWS;
        String[][][] popups = ns ? D7_POPUPS : KOREAN_POPUPS;
        Key[] letters = letterKeys(rows[r], popups[r], true, prefs);
        boolean deleteHere = r == 2;
        List<Key> consonants = new ArrayList<>(), vowels = new ArrayList<>();
        if (!ns && r == 2) {
            int shift = koreanShiftSlot(prefs);
            consonants.add(fn(shift, shift == Key.FUNCTION ? "Fn" : "", 1f));
        }
        for (Key k : letters) {
            boolean vowel = com.alternative_studios.newswipe.hangul.HangulComposer.isVowel(k.label.charAt(0));
            (vowel ? vowels : consonants).add(k);
        }
        if (deleteHere) vowels.add(fn(deleteKey(prefs) ? Key.DELETE : Key.SPACER, "", 1f));
        return splitRow(consonants, vowels, 1f, BALANCED_HALF / balancedConsonants(prefs),
                balancedWidthMax(prefs), prefs);
    }

    /**
     * 가운데에서 반으로 나뉜 한 줄: 왼쪽 절반에 consonants, 오른쪽 절반에 vowels를 놓는다.
     * 자음 키는 왼쪽 절반을 꽉 채우는 폭의 '자음 폭'%이고 남는 폭 안에서 '자음 위치'에 따라 놓인다.
     * 모음 키는 baseW(자음 키 한 칸의 폭)의 '모음 폭'%(최대 maxPct, 오른쪽 절반을 넘지 않게)이고 '모음 위치'에 따라 놓인다.
     * 숫자 줄·영어 자판의 분리도 같은 규칙을 쓴다.
     */
    private static Row splitRow(List<Key> consonants, List<Key> vowels, float height, float baseW, int maxPct, Prefs prefs) {
        float cw = BALANCED_HALF / consonants.size()
                * (prefs == null ? Prefs.BALANCED_WIDTH_DEFAULT : prefs.balancedConsonantWidth()) / 100f;
        float cpos = (prefs == null ? 50 : prefs.balancedConsonantPos()) / 100f;
        float cfree = Math.max(0f, BALANCED_HALF - consonants.size() * cw);
        float cgapLeft = cfree * cpos, cgapRight = cfree - cgapLeft;
        int pct = prefs == null ? Prefs.BALANCED_WIDTH_DEFAULT : prefs.balancedVowelWidth();
        float v = baseW * Math.min(pct, maxPct) / 100f;
        if (!vowels.isEmpty()) v = Math.min(v, BALANCED_HALF / vowels.size());
        float pos = (prefs == null ? 50 : prefs.balancedVowelPos()) / 100f;
        float free = Math.max(0f, BALANCED_HALF - vowels.size() * v);
        float gapLeft = free * pos, gapRight = free - gapLeft;
        List<Key> keys = new ArrayList<>();
        if (cgapLeft > 0.001f) keys.add(fn(Key.GAP, "", cgapLeft));
        for (Key k : consonants) {
            k.weight = cw;
            keys.add(k);
        }
        if (cgapRight > 0.001f) keys.add(fn(Key.GAP, "", cgapRight));
        if (gapLeft > 0.001f) keys.add(fn(Key.GAP, "", gapLeft));
        for (Key k : vowels) {
            k.weight = v;
            keys.add(k);
        }
        if (gapRight > 0.001f) keys.add(fn(Key.GAP, "", gapRight));
        return new Row(height, keys.toArray(new Key[0]));
    }

    /** 가로 모드 분리 키보드의 숫자 줄: 1~5는 왼쪽(자음 설정), 6~0은 오른쪽(모음 설정). */
    private static Row splitNumberRow(Prefs prefs) {
        String[] n = {"1", "2", "3", "4", "5", "6", "7", "8", "9", "0"};
        List<Key> left = new ArrayList<>(), right = new ArrayList<>();
        for (int i = 0; i < n.length; i++) (i < 5 ? left : right).add(ch(n[i], null));
        return splitRow(left, right, 0.78f, BALANCED_HALF / 5f, 1000, prefs);
    }

    /**
     * 가로 모드 분리 키보드의 영어 자판: 줄마다 왼쪽 절반(자음 설정)과 오른쪽 절반(모음 설정)으로 나눈다.
     * 왼쪽은 q w e r t / a s d f g / Shift z x c v, 오른쪽은 y u i o p / h j k l / b n m ⌫이다.
     */
    private static KeyboardLayout splitEnglish(Prefs prefs) {
        List<Row> rows = new ArrayList<>();
        if (prefs.numberRow()) rows.add(splitNumberRow(prefs));
        int[] leftCount = {5, 5, 4};   // 줄마다 왼쪽에 놓는 글자 수 (셋째 줄은 Shift 자리를 하나 더 둔다)
        for (int r = 0; r < 3; r++) {
            Key[] letters = letterKeys(ENGLISH_ROWS[r], ENGLISH_POPUPS[r], false, prefs);
            List<Key> left = new ArrayList<>(), right = new ArrayList<>();
            if (r == 2) left.add(fn(Key.SHIFT, "", 1f));
            for (int i = 0; i < letters.length; i++) (i < leftCount[r] ? left : right).add(letters[i]);
            if (r == 2) right.add(fn(deleteKey(prefs) ? Key.DELETE : Key.SPACER, "", 1f));
            rows.add(splitRow(left, right, 1f, BALANCED_HALF / 5f, 1000, prefs));
        }
        rows.add(spaceRow(Key.TO_SYMBOLS, "?123", prefs));
        return new KeyboardLayout(ENGLISH, BALANCED_COLUMNS, rows);
    }

    /**
     * NewSwipe 배열 (NewSwipe 단모음 7열): Shift 키 없음 (쌍자음은 아래로 밀기).
     * 셋째 줄 끝에 ⌫가 오고, ⌫를 없애면 그 자리는 빈 칸으로 남는다.
     */
    private static KeyboardLayout nsLayout(Prefs prefs, boolean numberRow) {
        List<Row> rows = new ArrayList<>();
        if (numberRow) rows.add(numberRow());
        rows.add(nsRow(0, prefs));
        rows.add(nsRow(1, prefs));
        rows.add(nsRow(2, prefs));
        rows.add(spaceRow(Key.TO_SYMBOLS, "?123", prefs));
        return new KeyboardLayout(KOREAN, koreanColumns(prefs), rows);
    }

    public static KeyboardLayout english(Prefs prefs) {
        if (prefs != null && prefs.splitView()) return splitEnglish(prefs);   // 가로 모드 분리 키보드
        boolean numberRow = prefs != null && prefs.numberRow();
        List<Row> rows = new ArrayList<>();
        if (numberRow) rows.add(numberRow());
        rows.add(letterRow(ENGLISH_ROWS[0], ENGLISH_POPUPS[0], false, prefs));
        rows.add(letterRow(ENGLISH_ROWS[1], ENGLISH_POPUPS[1], false, prefs));
        Key[] bottomLetters = letterKeys(ENGLISH_ROWS[2], ENGLISH_POPUPS[2], false, prefs);
        rows.add(prefs != null && prefs.gridLayout() ? gridBottomLetterRow(bottomLetters, deleteKey(prefs), prefs)
                : bottomLetterRow(bottomLetters, 1.5f, Key.SHIFT, deleteKey(prefs)));
        rows.add(spaceRow(Key.TO_SYMBOLS, "?123", prefs));
        return new KeyboardLayout(ENGLISH, 10, rows);
    }

    public static KeyboardLayout symbols(boolean korean, Prefs prefs) {
        String back = korean ? "가" : "ABC";
        List<Row> rows = new ArrayList<>();
        // 숫자 줄을 켜면 한글/영어 자판과 같은 5줄(첫 줄 0.78)로 맞추고, 둘째 줄에 기호 줄을 더 넣는다.
        boolean five = prefs != null && prefs.numberRow();
        rows.add(charRow(new String[]{"1", "2", "3", "4", "5", "6", "7", "8", "9", "0"},
                new String[][]{{"¹", "½", "⅓", "¼"}, {"²", "⅔"}, {"³", "¾"}, {"⁴"}, null, null, null, null,
                        null, {"ⁿ", "∅"}}, five ? 0.78f : 1f));
        if (five) {
            rows.add(charRow(new String[]{"~", "`", "$", "%", "^", "=", "[", "]", "{", "}"},
                    new String[][]{{"∼"}, null, {"€", "£", "¥", "¢", "₹"}, {"‰"}, {"ˆ"}, {"≠", "≈", "≤", "≥"},
                            {"〈", "「", "《"}, {"〉", "」", "》"}, {"〔", "『"}, {"〕", "』"}}));
        }
        rows.add(charRow(new String[]{"@", "#", "₩", "_", "&", "-", "+", "(", ")", "/"},
                new String[][]{null, {"№"}, {"$", "€", "£", "¥", "¢", "₹"}, null, null, {"–", "—", "·"},
                        {"±"}, {"<", "[", "{"}, {">", "]", "}"}, {"\\", "|"}}));
        List<Key> r3 = new ArrayList<>();
        r3.add(fn(Key.SYMBOL_PAGE, "=\\<", 1.5f));
        String[] s3 = {"*", "\"", "'", ":", ";", "!", "?"};
        String[][] p3 = {{"★", "†", "‡"}, {"“", "”", "«", "»"}, {"‘", "’", "‚"}, null, null, {"¡"}, {"¿"}};
        for (int i = 0; i < s3.length; i++) r3.add(ch(s3[i], p3[i]));
        r3.add(fn(deleteKey(prefs) ? Key.DELETE : Key.SPACER, "", 1.5f));
        rows.add(gridSides(r3, prefs));
        rows.add(spaceRow(Key.TO_LETTERS, back, prefs, true));
        return new KeyboardLayout(SYMBOLS, 10, rows);
    }

    public static KeyboardLayout symbols2(boolean korean, Prefs prefs) {
        String back = korean ? "가" : "ABC";
        boolean five = prefs != null && prefs.numberRow();
        List<Row> rows = new ArrayList<>();
        List<Key> r3 = new ArrayList<>();
        r3.add(fn(Key.SYMBOL_PAGE, "?123", 1.5f));
        if (five) {
            // 숫자 줄을 켠 5줄 배열: 첫 줄은 1쪽과 같은 숫자 줄로 고정하고, 나머지는 1쪽과 겹치지 않는 기호로 채운다.
            rows.add(charRow(new String[]{"1", "2", "3", "4", "5", "6", "7", "8", "9", "0"},
                    new String[][]{{"¹", "½", "⅓", "¼"}, {"²", "⅔"}, {"³", "¾"}, {"⁴"}, null, null, null, null,
                            null, {"ⁿ", "∅"}}, 0.78f));
            rows.add(charRow(new String[]{"<", ">", "|", "\\", "×", "÷", "±", "≠", "≈", "°"},
                    new String[][]{{"〈", "《", "≤", "«"}, {"〉", "》", "≥", "»"}, {"¦"}, null, null, null, {"∓"},
                            {"≒"}, {"∼", "≅"}, {"℃", "℉", "′", "″"}}));
            rows.add(charRow(new String[]{"※", "★", "☆", "○", "●", "◎", "♡", "♪", "←", "→"},
                    new String[][]{{"‡"}, {"✦"}, {"✧"}, {"◇", "□", "△", "▽"}, {"◆", "■", "▲", "▼"}, {"◉"},
                            {"♥", "❤"}, {"♬", "♫"}, {"↑", "↓", "↔"}, {"⇒", "⇐", "⇔"}}));
            String[] s3 = {"€", "£", "¥", "©", "®", "™", "…"};
            String[][] p3 = {{"₿"}, {"₤"}, {"元", "円"}, {"℗"}, {"℠"}, null, {"·", "⋯", "¶", "§"}};
            for (int i = 0; i < s3.length; i++) r3.add(ch(s3[i], p3[i]));
        } else {
            rows.add(charRow(new String[]{"~", "`", "|", "•", "√", "π", "÷", "×", "¶", "∆"},
                    new String[][]{{"∼"}, null, {"¦"}, {"·", "…", "°"}, {"∞", "≈", "≠"}, {"Ω", "μ", "∑"},
                            null, null, {"§"}, null}));
            rows.add(charRow(new String[]{"※", "♡", "☆", "○", "^", "°", "=", "{", "}", "\\"},
                    new String[][]{{"♪", "♬", "☎"}, {"♥", "❤"}, {"★"},
                            {"●", "◎", "◇", "◆", "□", "■", "△", "▲", "▽", "▼"},
                            {"↑", "→", "←", "↓", "↔"}, {"℃", "℉"}, {"≠", "≈", "≤", "≥"}, null, null, null}));
            String[] s3 = {"%", "©", "®", "™", "✓", "[", "]"};
            String[][] p3 = {{"‰"}, null, null, {"℠"}, {"✔", "✗", "✘"}, {"<", "〈", "《"}, {">", "〉", "》"}};
            for (int i = 0; i < s3.length; i++) r3.add(ch(s3[i], p3[i]));
        }
        r3.add(fn(deleteKey(prefs) ? Key.DELETE : Key.SPACER, "", 1.5f));
        rows.add(gridSides(r3, prefs));
        rows.add(spaceRow(Key.TO_LETTERS, back, prefs, true));
        return new KeyboardLayout(SYMBOLS_2, 10, rows);
    }

    /** 숫자 입력란(전화번호, 숫자)용 자판. */
    public static KeyboardLayout number() {
        List<Row> rows = new ArrayList<>();
        rows.add(new Row(1f, ch("1", null), ch("2", null), ch("3", null),
                fnChar("-", new String[]{"+", "*", "/", "#", "(", ")", "=", "N", ";"})));
        rows.add(new Row(1f, ch("4", null), ch("5", null), ch("6", null), fn(Key.SPACE, "", 1f)));
        rows.add(new Row(1f, ch("7", null), ch("8", null), ch("9", null), fn(Key.DELETE, "", 1f)));
        // 아래 줄 양옆은 전화번호 입력에 쓰는 * 키와 # 키다 (온점·쉼표는 # 키를 길게 눌러 입력한다).
        rows.add(new Row(1f, fnChar("*", new String[]{"+", "/", "=", "(", ")", ";"}), ch("0", new String[]{"+"}),
                fnChar("#", new String[]{".", ",", ":", "/", "+", "*"}), fn(Key.ENTER, "", 1f)));
        return new KeyboardLayout(NUMBER, 4, rows);
    }

    // ---------------------------------------------------------------- 도우미

    private static Row numberRow() {
        String[] n = {"1", "2", "3", "4", "5", "6", "7", "8", "9", "0"};
        Key[] keys = new Key[n.length];
        for (int i = 0; i < n.length; i++) keys[i] = ch(n[i], null);
        return new Row(0.78f, keys);
    }

    private static Key[] letterKeys(String[] labels, String[][] popups, boolean korean, Prefs prefs) {
        Key[] keys = new Key[labels.length];
        for (int i = 0; i < labels.length; i++) {
            String l = labels[i];
            String shifted;
            String swipe = null;
            boolean custom = prefs != null && prefs.swipeCustom();
            char c0 = l.charAt(0);
            boolean vowel = korean && com.alternative_studios.newswipe.hangul.HangulComposer.isVowel(c0);
            if (korean) {
                char d = com.alternative_studios.newswipe.hangul.HangulComposer.doubled(c0);
                shifted = d != 0 ? String.valueOf(d) : l;
                // 아래로 밀어 쌍자음. 모음의 이중모음은 아래에서 따로 정한다.
                if (d != 0 && !vowel && !custom && (prefs == null || prefs.swipeDouble())) swipe = shifted;
            } else {
                shifted = l.toUpperCase(Locale.ROOT);
            }
            keys[i] = new Key(Key.CHAR, l, l, shifted, swipe, popupFor(prefs, korean, l, popups[i]), 1f);
            if (custom) {
                applyCustomSwipes(keys[i], prefs, korean, l);
            } else if (korean && (prefs == null || prefs.swipeFinal())) {
                keys[i].swipeUp = FinalSwipes.get(prefs, l.charAt(0), FinalSwipes.UP);
                keys[i].swipeLeft = FinalSwipes.get(prefs, l.charAt(0), FinalSwipes.LEFT);
                keys[i].swipeRight = FinalSwipes.get(prefs, l.charAt(0), FinalSwipes.RIGHT);
            }
            // NewSwipe 배열: ㅂ·ㅈ·ㄷ·ㄱ를 오른쪽으로 밀어 ㅍ·ㅊ·ㅌ·ㅋ. 겹받침 설정과 상관없이 오른쪽은 이 자음이 쓴다.
            if (korean && !custom && newSwipe(prefs) && nsRight(l) != null) keys[i].swipeRight = nsRight(l);
            // ㅅ은 위로 밀어도 ㅆ: 'ㅅ을 위로 밀어서 ㅆ 입력'을 켰을 때 ('밀어서 쌍자음'을 켰을 때만).
            if (korean && !custom && newSwipe(prefs) && prefs.swipeDouble() && prefs.ssUp() && "ㅅ".equals(l)) keys[i].swipeUp = "ㅆ";
            if (vowel && !custom) {
                // 밀어서 이중모음 (VowelSwipes): 아래로 ㅣ계 이중모음('밀어서 ㅣ계 이중모음'),
                // 위·왼쪽·오른쪽으로 조합형 이중모음 ('밀어서 조합형 이중모음', 편집 가능. 모든 한글 배열 공통).
                boolean iotized = prefs == null || prefs.swipeIotized();
                boolean compound = compoundVowels(prefs);
                keys[i].swipeDown = iotized ? VowelSwipes.iotizedFor(c0) : null;
                keys[i].swipeUp = compound ? VowelSwipes.compoundFor(prefs, c0, Key.SWIPE_UP) : null;
                keys[i].swipeLeft = compound ? VowelSwipes.compoundFor(prefs, c0, Key.SWIPE_LEFT) : null;
                keys[i].swipeRight = compound ? VowelSwipes.compoundFor(prefs, c0, Key.SWIPE_RIGHT) : null;
            }
        }
        return keys;
    }

    /** 밀어서 조합형 이중모음을 쓰는지 (모든 한글 배열 공통). */
    public static boolean compoundVowels(Prefs prefs) {
        return prefs != null && prefs.swipeCompoundVowel();
    }

    /** 스와이프 완전 사용자화: 네 방향 모두 사용자 설정(없으면 기본 배치)을 따른다. */
    private static void applyCustomSwipes(Key k, Prefs prefs, boolean korean, String label) {
        k.swipeDown = SwipeCustom.get(prefs, korean, label, Key.SWIPE_DOWN);
        k.swipeUp = SwipeCustom.get(prefs, korean, label, Key.SWIPE_UP);
        k.swipeLeft = SwipeCustom.get(prefs, korean, label, Key.SWIPE_LEFT);
        k.swipeRight = SwipeCustom.get(prefs, korean, label, Key.SWIPE_RIGHT);
    }

    private static Row letterRow(String[] labels, String[][] popups, boolean korean, Prefs prefs) {
        return new Row(1f, letterKeys(labels, popups, korean, prefs));
    }

    /** ⌫를 보이는지 (기능키 순서·유무 사용자화). 숫자 자판의 ⌫는 늘 보인다. */
    private static boolean deleteKey(Prefs prefs) {
        return prefs == null || !prefs.deleteKeyHidden();
    }

    /** 한글 자판 Shift 자리의 키: Shift 키, (Shift 키를 끄고 Fn 키를 켰으면) Fn 키, 아니면 빈 칸. */
    private static int koreanShiftSlot(Prefs prefs) {
        if (prefs == null || !prefs.koreanShiftHidden()) return Key.SHIFT;
        return prefs.koreanShiftFn() ? Key.FUNCTION : Key.SPACER;
    }

    /** 맨 아래 글자 줄. 왼쪽 끝 키(leftType)·⌫가 없어도 그 자리는 빈 칸으로 남겨 배치가 바뀌지 않게 한다. */
    private static Row bottomLetterRow(Key[] letters, float sideWeight, int leftType, boolean delete) {
        Key[] keys = new Key[letters.length + 2];
        keys[0] = fn(leftType, leftType == Key.FUNCTION ? "Fn" : "", sideWeight);
        System.arraycopy(letters, 0, keys, 1, letters.length);
        keys[keys.length - 1] = fn(delete ? Key.DELETE : Key.SPACER, "", sideWeight);
        return new Row(1f, keys);
    }

    /**
     * 격자 정렬일 때 영어 자판의 맨 아래 글자 줄: Shift·지우기 키를 아래 줄 기능키와 같은 폭(한글 키 한 칸)으로 맞춘다.
     */
    private static Row gridBottomLetterRow(Key[] letters, boolean delete, Prefs prefs) {
        List<Key> keys = new ArrayList<>();
        keys.add(fn(Key.SHIFT, "", 1.5f));
        for (Key k : letters) keys.add(k);
        keys.add(fn(delete ? Key.DELETE : Key.SPACER, "", 1.5f));
        return gridSides(keys, prefs, true);
    }

    /** 격자 정렬을 켰으면 줄 양 끝의 기능키(Shift·기호 쪽 넘김·지우기)를 아래 줄 기능키와 같은 폭으로 맞춘다. */
    private static Row gridSides(List<Key> keys, Prefs prefs) {
        return gridSides(keys, prefs, prefs != null && prefs.gridLayout());
    }

    /**
     * 양 끝 키를 한글 키 한 칸 폭으로 줄이고, 남는 폭은 양 끝 키와 가운데 키들 사이의 빈 칸으로 둔다.
     * 그래서 양 끝 키가 아래 줄 기능키와 양 끝에서 세로로 줄을 맞춘다.
     */
    private static Row gridSides(List<Key> keys, Prefs prefs, boolean grid) {
        if (!grid || keys.size() < 2) return new Row(1f, keys.toArray(new Key[0]));
        Key first = keys.get(0), last = keys.get(keys.size() - 1);
        List<Key> middle = keys.subList(1, keys.size() - 1);
        float middleWidth = 0;
        for (Key k : middle) middleWidth += k.weight;
        float gridKey = gridKey(prefs);
        float gap = (SPACE_ROW_UNITS - 2 * gridKey - middleWidth) / 2f;
        first.weight = gridKey;
        last.weight = gridKey;
        List<Key> out = new ArrayList<>();
        out.add(first);
        if (gap > 0) out.add(fn(Key.SPACER, "", gap));
        out.addAll(middle);
        if (gap > 0) out.add(fn(Key.SPACER, "", gap));
        out.add(last);
        return new Row(1f, out.toArray(new Key[0]));
    }

    private static Row charRow(String[] labels, String[][] popups) {
        return charRow(labels, popups, 1f);
    }

    private static Row charRow(String[] labels, String[][] popups, float height) {
        Key[] keys = new Key[labels.length];
        for (int i = 0; i < labels.length; i++) keys[i] = ch(labels[i], popups[i]);
        return new Row(height, keys);
    }

    /** 기능키 순서·유무 편집 화면의 미리보기용: 한글 자판의 아래 두 줄 (Shift·⌫가 있는 줄과 맨 아래 줄). */
    public static KeyboardLayout bottomRowPreview(Prefs prefs) {
        List<Row> rows = new ArrayList<>();
        int shift = koreanShiftSlot(prefs);
        if (balanced(prefs)) {
            rows.add(balancedRow(2, prefs));
            rows.add(spaceRow(Key.TO_SYMBOLS, "?123", prefs));
            return new KeyboardLayout(KOREAN, BALANCED_COLUMNS, rows);
        }
        if (newSwipe(prefs)) {
            // NewSwipe 배열: 셋째 줄에는 Shift 키가 없다.
            rows.add(nsRow(2, prefs));
            rows.add(spaceRow(Key.TO_SYMBOLS, "?123", prefs));
            return new KeyboardLayout(KOREAN, koreanColumns(prefs), rows);
        }
        rows.add(bottomLetterRow(letterKeys(KOREAN_ROWS[2], KOREAN_POPUPS[2], true, prefs), 1f, shift, deleteKey(prefs)));
        rows.add(spaceRow(Key.TO_SYMBOLS, "?123", prefs));
        return new KeyboardLayout(KOREAN, 8, rows);
    }

    /** 맨 아래 줄: [?123] [,] [지구본] [스페이스] [.] [엔터]. 쉼표 키는 설정에 따라 빠진다. */
    private static Row spaceRow(int modeType, String modeLabel, Prefs prefs) {
        return spaceRow(modeType, modeLabel, prefs, false);
    }

    /**
     * 맨 아래 줄의 전체 너비. 영어·기호 자판(10열)에는 그대로 맞고, 한글 자판(8열)에서는 같은 비율로 줄어들어
     * 어느 자판에서나 아래 줄 키들의 실제 폭이 같다.
     */
    private static final float SPACE_ROW_UNITS = 10f;
    /**
     * 격자 정렬에서 아래 줄 기능키 하나의 너비: 한글 자판의 글자 키 한 칸 (10 ÷ 8열, NewSwipe 단모음은 10 ÷ 7열).
     * 자·모음 균형 레이아웃이면 나뉜 자음 키 한 칸.
     */
    private static float gridKey(Prefs prefs) {
        // 자·모음 균형 레이아웃: 왼쪽 절반을 나눠 가진 자음 키 한 칸 (5열이면 1칸, 4열이면 1.25칸).
        if (balanced(prefs)) return BALANCED_HALF / balancedConsonants(prefs);
        return SPACE_ROW_UNITS / koreanColumns(prefs);
    }

    /**
     * 하단 키 완전 사용자화에서 정한 순서·표시 여부대로 맨 아래 줄을 만든다.
     * 기호 키·엔터 키는 1.5칸, 나머지는 1칸이고 (격자 정렬이면 모두 한글 키 한 칸), 스페이스바는 남는 폭을 모두 쓴다.
     * 스페이스바를 없앴으면 남은 키들이 같은 비율로 늘어나 줄을 채운다.
     *
     * @param emojiKey 지구본 자리에 이모지 키를 둔다 (기호 자판). 지구본 키를 없애면 이모지 키도 함께 없어진다.
     */
    private static Row spaceRow(int modeType, String modeLabel, Prefs prefs, boolean emojiKey) {
        boolean periodComma = prefs == null || prefs.periodComma();
        // 격자 정렬: 기능키를 모두 한글 글자 키 한 칸 폭으로 맞춰 한글 자판이 반듯한 격자가 되게 한다.
        boolean grid = prefs != null && prefs.gridLayout();
        float wideW = grid ? gridKey(prefs) : 1.5f, narrowW = grid ? gridKey(prefs) : 1f;
        // 분리 키보드(가로 모드·대화면)에서는 기능키 폭을 줄일 수 있다. 줄어든 만큼 스페이스바가 넓어진다.
        if (prefs != null) {
            float fnScale = prefs.fnKeyWidth() / 100f;
            wideW *= fnScale;
            narrowW *= fnScale;
        }
        String[] order = prefs == null ? BottomKeys.DEFAULT_ORDER : prefs.bottomKeyOrder();
        List<Key> keys = new ArrayList<>();
        int spaceAt = -1;
        float used = 0;
        for (String id : order) {
            boolean shown = prefs == null ? !BottomKeys.COMMA.equals(id) : prefs.bottomKeyShown(id);
            if (!shown) continue;
            Key k;
            switch (id) {
                case BottomKeys.MODE:
                    k = fn(modeType, modeLabel, wideW);
                    break;
                case BottomKeys.COMMA:
                    k = slotChar(",", prefs, popupFor(prefs, true, ",", COMMA_POPUP), narrowW);
                    break;
                case BottomKeys.GLOBE:
                    k = fn(emojiKey ? Key.EMOJI : Key.LANGUAGE, "", narrowW);
                    break;
                case BottomKeys.PERIOD:
                    k = slotChar(".", prefs, popupFor(prefs, true, ".", periodPopup(periodComma)), narrowW);
                    if (prefs == null || (!prefs.swipeCustom() && prefs.periodSwipeComma())) k.swipeUp = ",";
                    break;
                case BottomKeys.ENTER:
                    k = fn(Key.ENTER, "", wideW);
                    break;
                default:   // 스페이스바: 폭은 다른 키를 다 놓은 뒤에 정한다
                    spaceAt = keys.size();
                    continue;
            }
            used += k.weight;
            keys.add(k);
        }
        if (spaceAt >= 0) {
            keys.add(spaceAt, new Key(Key.SPACE, "", " ", " ", null, null, Math.max(0f, SPACE_ROW_UNITS - used)));
        } else if (used > 0 && used < SPACE_ROW_UNITS) {
            // 스페이스바가 없으면 남은 키들을 같은 비율로 늘려 줄을 채운다.
            float scale = SPACE_ROW_UNITS / used;
            for (Key k : keys) k.weight *= scale;
        }
        return new Row(1f, keys.toArray(new Key[0]));
    }

    private static Key customSwipes(Key k, Prefs prefs, boolean korean) {
        if (prefs != null && prefs.swipeCustom()) applyCustomSwipes(k, prefs, korean, k.slot());
        return k;
    }

    /** 키에 쓰는 글자: 하단 쉼표·온점 키를 다른 글자로 바꿨으면 그 글자 (설정에는 원래 자리 이름 slot으로 저장된다). */
    public static String displayLabel(Prefs prefs, String slot) {
        return prefs == null ? slot : prefs.keyChar(slot);
    }

    /** 하단 쉼표(",")·온점(".") 자리의 키. 사용자가 다른 글자로 바꿨으면 그 글자를 입력한다. */
    private static Key slotChar(String slot, Prefs prefs, String[] popup, float weight) {
        String c = displayLabel(prefs, slot);
        Key k = fnChar(c, popup, weight);
        k.slot = slot;
        return customSwipes(k, prefs, true);
    }

    private static Key ch(String label, String[] popup) {
        return new Key(Key.CHAR, label, label, label, null, popup, 1f);
    }

    /** 글자를 입력하지만 기능키 색으로 그리는 키 (쉼표, 마침표). */
    private static Key fnChar(String label, String[] popup) {
        return fnChar(label, popup, 1f);
    }

    private static Key fnChar(String label, String[] popup, float weight) {
        Key k = new Key(Key.CHAR, label, label, label, null, popup, weight);
        k.grayStyle = true;
        return k;
    }

    private static Key fn(int type, String label, float weight) {
        return new Key(type, label, null, null, null, null, weight);
    }
}
