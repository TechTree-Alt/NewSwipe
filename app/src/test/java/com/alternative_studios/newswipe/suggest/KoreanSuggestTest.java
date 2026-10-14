package com.alternative_studios.newswipe.suggest;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.TreeMap;

public class KoreanSuggestTest {
    @Rule public TemporaryFolder tmp = new TemporaryFolder();

    private static WordDictionary dict;
    private static NextWords next;
    private static final float LN_TOP = 8f, LN_BOTTOM = 1f;

    /** build_ko_dict.py와 같은 형식으로 작은 사전을 만든다. words = {단어, 점수} 쌍. */
    static WordDictionary buildDict(String[][] words) throws Exception {
        String[][] w = words.clone();
        Arrays.sort(w, (a, b) -> a[0].compareTo(b[0]));
        ByteArrayOutputStream blob = new ByteArrayOutputStream();
        String prev = "";
        for (String[] e : w) {
            int common = 0;
            while (common < prev.length() && common < e[0].length() && prev.charAt(common) == e[0].charAt(common)) common++;
            String tail = e[0].substring(common);
            blob.write(common);
            blob.write(tail.length());
            blob.write(Integer.parseInt(e[1]));
            blob.write(tail.getBytes(StandardCharsets.UTF_16LE));
            prev = e[0];
        }
        ByteBuffer head = ByteBuffer.allocate(12).order(ByteOrder.LITTLE_ENDIAN);
        head.put("NSD1".getBytes(StandardCharsets.US_ASCII)).putInt(w.length).putInt(blob.size());
        ByteArrayOutputStream all = new ByteArrayOutputStream();
        all.write(head.array());
        all.write(blob.toByteArray());
        return WordDictionary.load(new ByteArrayInputStream(all.toByteArray()));
    }

    /** build_ko_model.py와 같은 NSN1 형식. pairs = {앞 단어, 다음 단어, P(다음|앞)}. */
    static byte[] buildNext(WordDictionary d, String[][] pairs, double lambda) {
        TreeMap<Integer, TreeMap<Integer, Double>> groups = new TreeMap<>();
        for (String[] p : pairs) {
            groups.computeIfAbsent(d.indexOf(p[0]), k -> new TreeMap<>()).put(d.indexOf(p[1]), Double.parseDouble(p[2]));
        }
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        ByteArrayOutputStream probs = new ByteArrayOutputStream();
        int prevH = 0;
        for (int h : groups.keySet()) {
            varint(body, h - prevH);
            body.write((int) Math.round(lambda * 255));
            varint(body, groups.get(h).size());
            int prevW = 0;
            for (var e : groups.get(h).entrySet()) {
                varint(body, e.getKey() - prevW);
                probs.write((int) Math.min(255, Math.round(-Math.log(e.getValue()) * 4)));
                prevW = e.getKey();
            }
            prevH = h;
        }
        ByteBuffer head = ByteBuffer.allocate(24).order(ByteOrder.LITTLE_ENDIAN);
        head.put("NSN1".getBytes(StandardCharsets.US_ASCII)).putInt(d.size()).putFloat(LN_TOP).putFloat(LN_BOTTOM)
                .putInt(groups.size()).putInt(pairs.length);
        ByteArrayOutputStream all = new ByteArrayOutputStream();
        all.writeBytes(head.array());
        all.writeBytes(body.toByteArray());
        all.writeBytes(probs.toByteArray());
        return all.toByteArray();
    }

    private static void varint(ByteArrayOutputStream out, int n) {
        while (true) {
            int x = n & 0x7F;
            n >>>= 7;
            if (n != 0) out.write(x | 0x80);
            else {
                out.write(x);
                return;
            }
        }
    }

    @BeforeClass
    public static void setUp() throws Exception {
        dict = buildDict(new String[][]{
                {"안녕", "200"}, {"안녕하세요", "250"}, {"안녕히", "120"}, {"하루", "150"}, {"하늘", "160"}, {"하나", "170"},
                {"좋은", "200"}, {"아침", "140"}, {"아침에", "100"}, {"오늘", "220"}, {"감사합니다", "255"}, {"감사", "200"},
                {"친구", "180"}, {"친구를", "150"}, {"친구가", "140"}, {"가방", "120"}, {"갑자기", "150"}, {"수고하세요", "160"},
                {"수고", "130"}, {"사과", "120"}, {"사람", "200"}, {"사람이", "150"}, {"했다", "200"}, {"했어", "150"},
                {"했어요", "140"}, {"비행기", "120"}, {"보고", "200"}, {"모교", "60"}, {"되요", "40"}, {"돼요", "200"}, {"친절", "135"}, {"밥", "150"}, {"먹었어", "160"}, {"먹고", "150"},
                {"추천", "150"}, {"추천하고", "120"}, {"추천했다", "120"}, {"좋고", "140"}, {"좋지", "130"},
                {"됐어", "150"}, {"됐다", "140"}, {"듣고", "120"}, {"듣지", "110"}, {"스타일", "120"}, {"스타일을", "100"},
                {"추천하는", "110"}, {"추천되는", "110"}, {"추천되고", "100"}, {"먹을", "120"}, {"사람한테", "100"},
                {"피곤한", "120"}, {"피곤해서", "110"}, {"있다", "200"}, {"있다고", "150"}, {"있다는", "150"}, {"있고", "150"},
                {"일찍", "200"}, {"일찍이", "60"}, {"상관", "120"}, {"상관이", "110"}, {"엄마", "180"}, {"엄마를", "120"},
        });
        next = NextWords.load(new ByteArrayInputStream(buildNext(dict, new String[][]{
                {"좋은", "하루", "0.6"}, {"좋은", "아침", "0.25"}, {"좋은", "사람", "0.1"},
                {"오늘", "하루", "0.05"}, {"감사합니다", "수고하세요", "0.2"}, {"밥", "먹었어", "0.3"},
        }, 0.6)), dict.size());
        dict.warmUpChosung();
    }

    private KoreanSuggest model() {
        return new KoreanSuggest(dict, next, new TypoModel());
    }

    private static String[] words(KoreanSuggest.Result r) {
        return r.words;
    }

    @Test
    public void movedFinalReadsTrailingConsonantAsNextInitial() {
        assertEquals("안녕하ㅅ", HangulRange.movedFinal("안녕핫"));
        assertEquals("달ㄱ", HangulRange.movedFinal("닭"));
        assertEquals("가ㅂ", HangulRange.movedFinal("갑"));
        assertNull(HangulRange.movedFinal("가"));
        assertNull(HangulRange.movedFinal("ㄱ"));
        assertTrue(HangulRange.startsWith("안녕하세요", "안녕핫"));
        assertTrue(HangulRange.startsWith("가방", "갑"));
        assertTrue(HangulRange.startsWith("갑자기", "갑"));
        assertFalse(HangulRange.startsWith("가방", "갓"));
    }

    @Test
    public void lookupFindsWordsWhileFinalIsStillAttached() {
        // 두벌식으로 '안녕하세요'를 치면 '안녕핫'이 잠깐 보인다.
        assertArrayEquals(new String[]{"안녕하세요"}, dict.lookup("안녕핫", 3));
        List<String> gab = Arrays.asList(dict.lookup("갑", 3));
        assertTrue(gab.contains("갑자기"));
        assertTrue(gab.contains("가방"));
    }

    @Test
    public void indexOfAndChosung() {
        assertTrue(dict.indexOf("하루") >= 0);
        assertEquals(-1, dict.indexOf("하룻"));
        assertEquals("감사합니다", dict.word(dict.chosungIds("ㄱㅅㅎㄴㄷ", 3)[0]));
        // 글자 수가 같은 단어를 먼저
        assertEquals("수고", dict.word(dict.chosungIds("ㅅㄱ", 3)[0]));
        assertTrue(KoreanSuggest.isInitials("ㅅㄱ"));
        assertFalse(KoreanSuggest.isInitials("ㅋㅋㅋ"));   // 한 자음을 되풀이한 말은 아니다
        assertFalse(KoreanSuggest.isInitials("ㅅ"));
        assertFalse(KoreanSuggest.isInitials("수ㄱ"));
    }

    @Test
    public void chosungSuggestion() {
        String[] r = words(model().suggest("ㄱㅅㅎㄴㄷ", null, KoreanSuggest.FIELD_NORMAL, null, null, null, 3));
        assertEquals("감사합니다", r[0]);
    }

    @Test
    public void typoModelPrefersSameKeyAndNeighbors() {
        TypoModel t = new TypoModel();
        assertTrue(t.substitute('ㅂ', 'ㅃ') < t.substitute('ㅂ', 'ㅈ'));   // 같은 키를 밀어 나오는 쌍자음
        assertTrue(t.substitute('ㅂ', 'ㅈ') < t.substitute('ㅂ', 'ㅎ'));   // 옆 키
        assertTrue(t.substitute('ㅐ', 'ㅔ') < TypoModel.NEIGHBOR);         // 소리가 같은 모음
        assertTrue(t.substitute('ㅏ', 'ㄱ') > t.substitute('ㅏ', 'ㅓ'));   // 모음↔자음은 더 비싸다
        char[] a = TypoModel.letters("안녕하새"), b = TypoModel.letters("안녕하세요");
        assertEquals(TypoModel.VARIANT, t.prefixDistance(a, a.length, b, b.length, 2f), 1e-6);
        char[] c = TypoModel.letters("감사합니당"), d = TypoModel.letters("감사합니다");
        assertEquals(TypoModel.INSERT, t.distance(c, c.length, d, d.length, 2f), 1e-6);
        char[] e = TypoModel.letters("감사함니다");
        assertTrue(t.distance(e, e.length, d, d.length, 2f) <= TypoModel.NEIGHBOR + 1e-6);   // ㅂ↔ㅁ 옆 키
    }

    @Test
    public void fuzzyPrefixFindsWordWithTypo() {
        String[] r = words(model().suggest("안녕하새", null, KoreanSuggest.FIELD_NORMAL, null, null, null, 3));
        assertEquals("안녕하세요", r[0]);
    }

    @Test
    public void previousWordReranks() {
        KoreanSuggest m = model();
        // 앞 단어 없이 'ㅎ'은 빈도순 (하나 > 하늘 > 하루)
        assertEquals("하나", words(m.suggest("하", null, KoreanSuggest.FIELD_NORMAL, null, null, null, 3))[0]);
        // '좋은' 다음에는 '하루'
        assertEquals("하루", words(m.suggest("하", "좋은", KoreanSuggest.FIELD_NORMAL, null, null, null, 3))[0]);
    }

    @Test
    public void predictsNextWordOnlyWhenConfident() {
        KoreanSuggest m = model();
        KoreanSuggest.Result r = m.predict("좋은", KoreanSuggest.FIELD_NORMAL, null, null, 3);
        assertTrue(r.prediction);
        assertArrayEquals(new String[]{"하루", "아침", "사람"}, r.words);
        assertEquals(0, m.predict("오늘", KoreanSuggest.FIELD_NORMAL, null, null, 3).words.length);   // 확률이 낮다
        assertEquals(0, m.predict("하늘", KoreanSuggest.FIELD_NORMAL, null, null, 3).words.length);   // 정보가 없다
        assertEquals(0, m.predict(null, KoreanSuggest.FIELD_NORMAL, null, null, 3).words.length);
    }

    @Test
    public void particlesForLearnedNames() throws Exception {
        UserDictionary user = new UserDictionary(new File(tmp.getRoot(), "u.txt"));
        user.add("민수", true, true, 1);
        user.load();
        KoreanSuggest m = model();
        List<String> r = Arrays.asList(words(m.suggest("민수ㄱ", null, KoreanSuggest.FIELD_NORMAL, user, null, null, 3)));
        assertTrue(r.toString(), r.contains("민수가"));
        r = Arrays.asList(words(m.suggest("민수ㄹ", null, KoreanSuggest.FIELD_NORMAL, user, null, null, 3)));
        assertTrue(r.toString(), r.contains("민수를") || r.contains("민수랑"));
        // 받침 있는 말에는 받침 뒤 조사
        r = Arrays.asList(words(m.suggest("사람ㅇ", null, KoreanSuggest.FIELD_NORMAL, null, null, null, 3)));
        assertTrue(r.toString(), r.contains("사람을") || r.contains("사람은") || r.contains("사람이"));
        assertFalse(r.contains("사람를"));
    }

    @Test
    public void usageHistoryPersonalizesRanking() throws Exception {
        UsageHistory h = new UsageHistory(new File(tmp.getRoot(), "h.txt"));
        h.load();
        KoreanSuggest m = model();
        assertEquals("하나", words(m.suggest("하", null, KoreanSuggest.FIELD_NORMAL, null, h, null, 3))[0]);
        for (int i = 0; i < 30; i++) h.record("파란", "하늘", i);
        assertEquals("하늘", words(m.suggest("하", null, KoreanSuggest.FIELD_NORMAL, null, h, null, 3))[0]);
        // 내가 '파란' 다음에 두 번 이상 쓴 말은 예측한다
        assertEquals("하늘", m.predict("파란", KoreanSuggest.FIELD_NORMAL, null, h, 3).words[0]);
    }

    @Test
    public void searchFieldPrefersBareNouns() {
        KoreanSuggest m = model();
        String[] normal = words(m.suggest("친", null, KoreanSuggest.FIELD_NORMAL, null, null, null, 3));
        String[] search = words(m.suggest("친", null, KoreanSuggest.FIELD_SEARCH, null, null, null, 3));
        assertArrayEquals(new String[]{"친구", "친구를", "친구가"}, normal);
        assertArrayEquals(new String[]{"친구", "친절", "친구를"}, search);   // 조사가 붙은 말은 뒤로
    }

    @Test
    public void correctsWithKeyboardAwareCost() {
        KoreanSuggest m = model();
        assertEquals("감사합니다", m.correct("감사함니다", null, KoreanSuggest.FIELD_NORMAL, null, null));
        assertEquals("돼요", m.correct("돼여", null, KoreanSuggest.FIELD_NORMAL, null, null));
        assertNull(m.correct("감사", null, KoreanSuggest.FIELD_NORMAL, null, null));           // 사전 단어
        assertNull(m.correct("친구를", null, KoreanSuggest.FIELD_NORMAL, null, null));         // 사전 단어
        assertNull(m.correct("사과는", null, KoreanSuggest.FIELD_NORMAL, null, null));         // 사전 단어 + 조사
        assertNull(m.correct("하", null, KoreanSuggest.FIELD_NORMAL, null, null));             // 한 글자
        // 드문 단어로는 고치지 않는다 (맞게 친 단어를 바꾸기 쉽다)
        assertNull(m.correct("모규", null, KoreanSuggest.FIELD_NORMAL, null, null));
    }

    @Test
    public void suggestionPutsCorrectionFirst() {
        KoreanSuggest.Result r = model().suggest("감사함니다", null, KoreanSuggest.FIELD_NORMAL, null, null, "감사합니다", 3);
        assertEquals(0, r.correction);
        assertEquals("감사합니다", r.words[0]);
        assertEquals(new ArrayList<>(new java.util.LinkedHashSet<>(Arrays.asList(r.words))).size(), r.words.length);
    }

    @Test
    public void joinsWordsTypedWithoutSpace() {
        // '밥먹었'은 사전에 없지만 '밥' 다음에 '먹었어'가 자주 온다: 띄어 쓰지 않은 그대로 이어 붙여 보인다.
        assertEquals("밥먹었어", words(model().suggest("밥먹었", null, KoreanSuggest.FIELD_NORMAL, null, null, null, 3))[0]);
        assertTrue(Arrays.asList(words(model().suggest("밥머", null, KoreanSuggest.FIELD_NORMAL, null, null, null, 3)))
                .contains("밥먹었어"));
        // '안녕' 다음에 '하'로 시작하는 말이 온다는 근거(단어 쌍)가 없으면 '안녕하루'처럼 이어 붙이지 않는다.
        assertArrayEquals(new String[]{"안녕하세요"},
                words(model().suggest("안녕하", null, KoreanSuggest.FIELD_NORMAL, null, null, null, 3)));
    }

    @Test
    public void generatesEndingsOnlyWhenRegular() {
        KoreanSuggest m = model();
        // 하다 활용: 사전에 '추천하고'·'추천했다'가 있으면 '추천해줘'를 만든다
        assertTrue(Arrays.asList(words(m.suggest("추천해ㅈ", null, KoreanSuggest.FIELD_NORMAL, null, null, null, 3)))
                .contains("추천해줘"));
        // 자음으로 시작하는 어미는 일반 어간에도 붙인다 (좋 + 겠지)
        assertTrue(Arrays.asList(words(m.suggest("좋겠ㅈ", null, KoreanSuggest.FIELD_NORMAL, null, null, null, 3)))
                .contains("좋겠지"));
        // 과거형 뒤 어미 (됐 + 나봐)
        assertTrue(Arrays.asList(words(m.suggest("됐나", null, KoreanSuggest.FIELD_NORMAL, null, null, null, 3)))
                .contains("됐나봐"));
        // 명사 + 이다 (스타일 + 이야)
        assertTrue(Arrays.asList(words(m.suggest("스타일이", null, KoreanSuggest.FIELD_NORMAL, null, null, null, 3)))
                .contains("스타일이야"));
        // 모음으로 시작하는 어미는 일반 어간에 붙이지 않는다 (불규칙 활용: 듣다 → 들어, '듣어'는 틀린 말)
        for (String w : words(m.suggest("듣", null, KoreanSuggest.FIELD_NORMAL, null, null, null, 3))) {
            assertFalse(w, w.startsWith("듣어") || w.startsWith("듣을"));
        }
    }

    @Test
    public void doesNotAttachParticlesToVerbForms() {
        KoreanSuggest m = model();
        // '추천되는'이 사전에 있다고 '추천되'를 명사로 보면 '추천되는요'가 나온다
        assertFalse(m.nounLike("추천되"));
        for (String w : words(m.suggest("추천되는", null, KoreanSuggest.FIELD_NORMAL, null, null, null, 3))) {
            assertFalse(w, w.endsWith("요"));
        }
        // 받침 뒤 '을' 하나만으로는 명사가 아니다 (먹을 → 먹이야 X), 용언 어간이 아닌 말은 명사로 본다 (스타일을)
        assertFalse(m.nounLike("먹"));
        assertTrue(m.nounLike("스타일"));
        assertTrue(m.nounLike("친구"));
        // 다로 끝난 활용형에 어미를 붙이지 않는다 (있다잖아 X)
        for (String w : words(m.suggest("있다ㅈ", null, KoreanSuggest.FIELD_NORMAL, null, null, null, 3))) {
            assertFalse(w, w.startsWith("있다잖") || w.startsWith("있다죠"));
        }
    }

    @Test
    public void keepsAdverbsAndPickyParticlesOut() {
        KoreanSuggest m = model();
        // '일찍이'는 맨말 '일찍'보다 훨씬 드물어 부사로 본다 (일찍한테 X). '상관이'는 흔해 명사로 본다.
        assertFalse(m.nounLike("일찍"));
        assertTrue(m.nounLike("상관"));
        // 뜻을 가리는 조사(마다)는 첫 글자를 다 친 뒤에만
        assertFalse(Arrays.asList(words(m.suggest("엄마ㅁ", null, KoreanSuggest.FIELD_NORMAL, null, null, null, 3)))
                .contains("엄마마다"));
        assertTrue(Arrays.asList(words(m.suggest("엄마마", null, KoreanSuggest.FIELD_NORMAL, null, null, null, 3)))
                .contains("엄마마다"));
    }

    @Test
    public void attachesCompoundParticles() {
        KoreanSuggest m = model();
        // 뿐 → 뿐만, 뿐이야
        List<String> s = Arrays.asList(words(m.suggest("친구뿐", null, KoreanSuggest.FIELD_NORMAL, null, null, null, 3)));
        assertTrue(s.toString(), s.contains("친구뿐만") && s.contains("친구뿐이야"));
        // 한테는·한테도는 사전에 그 말 + 한테가 있을 때만 (평생한테는 같은 말이 나오지 않게)
        assertTrue(Arrays.asList(words(m.suggest("사람한테", null, KoreanSuggest.FIELD_NORMAL, null, null, null, 3)))
                .contains("사람한테는"));
        assertFalse(Arrays.asList(words(m.suggest("친구한테", null, KoreanSuggest.FIELD_NORMAL, null, null, null, 3)))
                .contains("친구한테는"));
        // 그냥 한테는 근거가 없어도 붙인다
        assertTrue(Arrays.asList(words(m.suggest("친구한", null, KoreanSuggest.FIELD_NORMAL, null, null, null, 3)))
                .contains("친구한테"));
    }

    @Test
    public void doesNotGiveVerbEndingsToAdjectives() {
        KoreanSuggest m = model();
        // 피곤하다는 형용사라 '피곤해줘'는 틀린 말, 추천하다는 동사라 '추천해줘'를 만든다
        for (String w : words(m.suggest("피곤해ㅈ", null, KoreanSuggest.FIELD_NORMAL, null, null, null, 3))) {
            assertFalse(w, w.startsWith("피곤해줘"));
        }
        assertTrue(Arrays.asList(words(m.suggest("추천해ㅈ", null, KoreanSuggest.FIELD_NORMAL, null, null, null, 3)))
                .contains("추천해줘"));
    }

    @Test
    public void joinsWordsTheUserTypedTogether() throws Exception {
        UsageHistory h = new UsageHistory(new File(tmp.getRoot(), "h.txt"));
        h.load();
        h.record("안녕", "하늘", 1);
        assertFalse(Arrays.asList(words(model().suggest("안녕하", null, KoreanSuggest.FIELD_NORMAL, null, h, null, 3)))
                .contains("안녕하늘"));   // 한 번 이어 쓴 것만으로는 근거가 아니다
        h.record("안녕", "하늘", 2);
        assertTrue(Arrays.asList(words(model().suggest("안녕하", null, KoreanSuggest.FIELD_NORMAL, null, h, null, 3)))
                .contains("안녕하늘"));   // 두 번 이어 쓴 말은 근거가 된다
    }

    @Test
    public void nextWordsRejectsOtherDictionary() {
        byte[] bin = buildNext(dict, new String[][]{{"좋은", "하루", "0.3"}}, 0.5);
        try {
            NextWords.load(new ByteArrayInputStream(bin), dict.size() + 1);
            org.junit.Assert.fail();
        } catch (java.io.IOException expected) {
            // 다른 사전으로 만든 파일은 쓰지 않는다
        }
    }

    @Test
    public void previousWordFromText() {
        assertEquals("좋은", WordSuggester.previousWord("오늘도 좋은 "));
        assertEquals("좋은", WordSuggester.previousWord("오늘도 좋은 하"));
        assertEquals("좋은", WordSuggester.previousWord("좋은  하"));
        assertNull(WordSuggester.previousWord("좋은."));
        assertNull(WordSuggester.previousWord("좋은. "));      // 문장 부호로 끊겼다
        assertNull(WordSuggester.previousWord("3월에 "));      // 숫자가 섞인 어절
        assertNull(WordSuggester.previousWord("좋은\n"));
        assertNull(WordSuggester.previousWord("하"));
        assertNull(WordSuggester.previousWord(null));
        // 단어 뒤에 붙인 ㅋㅋ·ㅠㅠ는 건너뛴다
        assertEquals("알겠어", WordSuggester.previousWord("알겠어ㅋㅋ 내"));
        assertEquals("알겠어", WordSuggester.previousWord("알겠어ㅠㅠ "));
        assertNull(WordSuggester.previousWord("ㅋㅋ 내"));   // 자음만 있는 말은 단어가 아니다
    }

    @Test
    public void learnablePartDropsTrailingJamo() {
        assertEquals("알겠어", WordSuggester.learnablePart("알겠어ㅋㅋ"));
        assertEquals("배고파", WordSuggester.learnablePart("배고파ㅠㅠ"));
        assertEquals("하루", WordSuggester.learnablePart("하루"));
        assertEquals("ㅋㅋ", WordSuggester.learnablePart("ㅋㅋ"));
    }
}
