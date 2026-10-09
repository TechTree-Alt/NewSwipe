package com.alternative_studios.newswipe.suggest;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.BeforeClass;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

public class WordDictionaryTest {
    private static WordDictionary ko, en;

    /** build_*_dict.py와 같은 형식으로 작은 사전을 만든다. words = {단어, 점수} 쌍. */
    private static WordDictionary build(String[][] words) throws Exception {
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

    @BeforeClass
    public static void setUp() throws Exception {
        ko = build(new String[][]{
                {"안녕", "200"}, {"안녕하세요", "250"}, {"안녕히", "100"}, {"안", "50"}, {"안경", "90"},
                {"봐요", "150"}, {"보다", "120"}, {"봤어", "140"}, {"보내", "130"}, {"복", "20"},
                {"감사합니다", "255"}, {"감사", "200"}, {"값", "60"}, {"갔어요", "80"},
                {"와요", "70"}, {"외로워", "60"}, {"의사", "90"}, {"으악", "10"}, {"읽다", "30"}, {"읽어", "31"},
        });
        en = build(new String[][]{{"hello", "250"}, {"help", "200"}, {"helmet", "50"}, {"hell", "90"}, {"world", "100"}});
    }

    @Test
    public void roundTripsWords() {
        assertEquals(20, ko.size());
        boolean found = false;
        for (int i = 0; i < ko.size(); i++) if (ko.word(i).equals("안녕하세요")) found = true;
        assertTrue(found);
    }

    @Test
    public void openSyllableMatchesAnyFinalConsonant() {
        // "안"으로 끝나는 것은 '안'(완성)이고, "가"는 '감', '값', '갔'까지 맞춘다.
        assertArrayEquals(new String[]{"감사합니다", "감사", "갔어요"}, ko.lookup("가", 3));
    }

    @Test
    public void vowelCompoundsMatch() {
        // 보 → 봐, 봤도 맞는다
        String[] r = ko.lookup("보", 10);
        assertTrue(Arrays.asList(r).containsAll(Arrays.asList("봐요", "봤어", "보다", "보내", "복")));
        // 오 → 와, 외
        r = ko.lookup("오", 10);
        assertTrue(Arrays.asList(r).containsAll(Arrays.asList("와요", "외로워")));
        // 으 → 의
        r = ko.lookup("으", 10);
        assertTrue(Arrays.asList(r).containsAll(Arrays.asList("의사", "으악")));
    }

    @Test
    public void closedSyllableKeepsFinalConsonant() {
        // 읽 → 읽다, 읽어 (같은 받침만); 일은 해당 없음
        assertArrayEquals(new String[]{"읽어", "읽다"}, ko.lookup("읽", 5));
        // 갑 → ㅂ 받침과 ㅄ 받침(값)
        assertArrayEquals(new String[]{"값"}, ko.lookup("갑", 5));
    }

    @Test
    public void loneConsonantMatchesAnySyllableWithThatInitial() {
        String[] r = ko.lookup("ㄱ", 20);
        assertTrue(Arrays.asList(r).containsAll(Arrays.asList("감사", "값", "갔어요")));
        assertEquals(4, r.length);
        assertEquals(0, ko.lookup("ㅏ", 5).length);
    }

    @Test
    public void excludesExactMatchAndRanksByScore() {
        String[] r = ko.lookup("안녕", 5);
        assertArrayEquals(new String[]{"안녕하세요", "안녕히"}, r);
        assertArrayEquals(new String[]{"안녕하세요"}, ko.lookup("안녕", 1));
    }

    @Test
    public void englishCaseFollowsInput() {
        assertArrayEquals(new String[]{"hello", "help", "hell"}, WordSuggester.suggest(en, "hel", false));
        assertArrayEquals(new String[]{"Hello", "Help", "Hell"}, WordSuggester.suggest(en, "Hel", false));
        assertArrayEquals(new String[]{"HELLO", "HELP", "HELL"}, WordSuggester.suggest(en, "HEL", false));
        assertEquals(0, WordSuggester.suggest(en, "zzz", false).length);
    }

    @Test
    public void currentWordExtraction() {
        assertEquals("안녕", WordSuggester.currentWord("오늘 안녕", true));
        assertEquals("", WordSuggester.currentWord("안녕 ", true));
        assertEquals("ㄱ", WordSuggester.currentWord("안녕ㄱ", true).substring(2));
        assertEquals("hel", WordSuggester.currentWord("say hel", false));
        assertEquals("don't", WordSuggester.currentWord("I don't", false));
        assertEquals("", WordSuggester.currentWord("abc.", false));
    }

    @Test
    public void autoCorrect() {
        // 영어: 바꾸기·빠뜨리기·더하기·맞바꾸기, 사전에 있는 단어와 짧은 단어는 그대로
        assertEquals("hello", WordSuggester.autoCorrect(en, "hallo", false));
        assertEquals("hello", WordSuggester.autoCorrect(en, "helo", false));
        assertEquals("hello", WordSuggester.autoCorrect(en, "helllo", false));
        assertEquals("world", WordSuggester.autoCorrect(en, "wrold", false));
        assertEquals("Hello", WordSuggester.autoCorrect(en, "Hallo", false));
        assertEquals(null, WordSuggester.autoCorrect(en, "hello", false));
        assertEquals(null, WordSuggester.autoCorrect(en, "hx", false));
        assertEquals(null, WordSuggester.autoCorrect(en, "zzzzz", false));
        assertEquals(null, WordSuggester.autoCorrect(en, "heLlx", false));
        // 한글: 자모 하나 차이
        assertEquals("감사합니다", WordSuggester.autoCorrect(ko, "감사합니당", true));
        assertEquals(null, WordSuggester.autoCorrect(ko, "감사", true));
        assertEquals(null, WordSuggester.autoCorrect(ko, "ㅋㅋ", true));
        assertEquals(null, WordSuggester.autoCorrect(ko, "히", true));
    }

    private static void assertBad(byte[] data) {
        try {
            WordDictionary.load(new ByteArrayInputStream(data));
            org.junit.Assert.fail("should reject");
        } catch (java.io.IOException expected) {
            // 깨진 사전은 IOException으로 거절한다 (배열 범위 오류나 거대한 할당 없이)
        }
    }

    private static byte[] header(String magic, int n, int blob, byte[] body) {
        ByteBuffer b = ByteBuffer.allocate(12 + body.length).order(ByteOrder.LITTLE_ENDIAN);
        b.put(magic.getBytes(StandardCharsets.US_ASCII)).putInt(n).putInt(blob).put(body);
        return b.array();
    }

    @Test
    public void rejectsCorruptDictionaries() {
        assertBad(header("XXXX", 0, 0, new byte[0]));
        assertBad(header("NSD1", 1, 3, new byte[]{0, 5, 1}));               // 꼬리 글자가 모자람
        assertBad(header("NSD1", 1, 5, new byte[]{3, 1, 1, 'a', 0}));       // 앞 단어보다 긴 공통 접두사
        assertBad(header("NSD1", -1, 0, new byte[0]));
        assertBad(header("NSD1", 1, 1 << 30, new byte[0]));                // 터무니없이 큰 크기
        assertBad(header("NSD1", 5, 3, new byte[]{0, 0, 1}));               // 단어 수가 크기와 안 맞음
        assertBad(header("NSD1", 1, 10, new byte[]{0, 1, 1}));              // 파일이 중간에 끝남
    }

    @Test
    public void particleStemFollowsFinalConsonant() {
        assertEquals("글자", WordSuggester.particleStem("글자를"));
        assertEquals("바람", WordSuggester.particleStem("바람에도"));
        assertEquals("민수", WordSuggester.particleStem("민수가"));
        assertEquals("사람", WordSuggester.particleStem("사람이"));
        assertEquals("서울", WordSuggester.particleStem("서울로"));     // ㄹ 받침 뒤 '로'
        assertEquals("학교", WordSuggester.particleStem("학교에서"));
        assertEquals(null, WordSuggester.particleStem("친구을"));       // 받침 없는 말 뒤에 '을'은 조사가 아니다
        assertEquals(null, WordSuggester.particleStem("사람가"));       // 받침 뒤에 '가'도 아니다
        assertEquals(null, WordSuggester.particleStem("괴로워했다"));
        assertEquals(null, WordSuggester.particleStem("를"));
    }

    @Test
    public void autoCorrectLeavesDictWordPlusParticle() {
        // '감사'는 사전에 있으므로 '감사를'은 오타가 아니다 (사전에 없어도 고치지 않는다).
        assertEquals(null, WordSuggester.autoCorrect(ko, "감사를", true));
        assertTrue(WordSuggester.isDictWordWithParticle(ko, "감사를"));
        assertFalse(WordSuggester.isDictWordWithParticle(ko, "뚜비를"));
    }
}
