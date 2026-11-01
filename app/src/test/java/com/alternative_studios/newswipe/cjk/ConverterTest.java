package com.alternative_studios.newswipe.cjk;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.junit.Test;

public class ConverterTest {

    private static List<String> texts(List<Candidate> cs) {
        List<String> out = new ArrayList<>();
        for (Candidate c : cs) out.add(c.text);
        return out;
    }

    @Test
    public void dictionaryLookup() {
        ConversionDictionary d = CjkTestData.dict(CjkTestData.ZH);
        int i = d.find(ConversionDictionary.utf8("ni'hao"));
        assertTrue(i >= 0);
        assertEquals("你好", d.firstText(i));
        assertEquals(-1, d.find(ConversionDictionary.utf8("nih")));
        assertTrue(d.hasPrefix(ConversionDictionary.utf8("zhong'")));
        assertFalse(d.hasPrefix(ConversionDictionary.utf8("q")));
    }

    @Test
    public void rejectsBrokenFile() {
        byte[] b = CjkTestData.bytes(CjkTestData.ZH);
        b[b.length - 3] = 0;   // 값 영역의 길이를 깨뜨리지는 않지만, 머리를 깨뜨린다
        b[0] = 'X';
        try {
            ConversionDictionary.load(new ByteArrayInputStream(b));
            fail();
        } catch (IOException expected) {
            // 형식이 아니다
        }
        byte[] c = CjkTestData.bytes(CjkTestData.ZH);
        byte[] cut = java.util.Arrays.copyOf(c, c.length - 5);
        try {
            ConversionDictionary.load(new ByteArrayInputStream(cut));
            fail();
        } catch (IOException expected) {
            // 잘린 파일
        }
    }

    @Test
    public void pinyinWordsAndSentence() {
        PinyinConverter p = new PinyinConverter(CjkTestData.dict(CjkTestData.ZH));
        List<Candidate> c = p.convert("nihao", 20);
        assertEquals("你好", c.get(0).text);
        assertEquals(5, c.get(0).consumed);
        assertTrue(texts(c).contains("你"));
        // 짧은 후보는 읽기의 앞부분만 쓴다
        for (Candidate x : c) if (x.text.equals("你")) assertEquals(2, x.consumed);
        // 사전에 없는 말은 단어를 이어 문장 후보를 만든다
        assertEquals("你好吗", p.convert("nihaoma", 20).get(0).text);
        assertEquals("我们中国", p.convert("womenzhongguo", 20).get(0).text);
        // 병음 그대로도 고를 수 있다
        assertTrue(texts(p.convert("nihao", 20)).contains("nihao"));
    }

    @Test
    public void pinyinSegmentation() {
        PinyinConverter p = new PinyinConverter(CjkTestData.dict(CjkTestData.ZH));
        assertEquals("先", p.convert("xian", 20).get(0).text);   // 한 음절을 앞세운다
        assertEquals("西安", p.convert("xi'an", 20).get(0).text);   // '로 나누면 두 음절
        assertEquals("xian", p.display("xian"));
        assertEquals("ni'hao", p.display("nihao"));
        assertEquals("xi'an", p.display("xi'an"));
        assertEquals("ni'h", p.display("nih"));
    }

    @Test
    public void pinyinPartialLastSyllable() {
        PinyinConverter p = new PinyinConverter(CjkTestData.dict(CjkTestData.ZH));
        List<String> t = texts(p.convert("nih", 20));
        assertTrue(t.contains("你好"));
        assertTrue(t.contains("你会"));
        assertTrue(t.indexOf("你好") < t.indexOf("你"));
    }

    @Test
    public void kanaWordsAndSentence() {
        KanaConverter k = new KanaConverter(CjkTestData.dict(CjkTestData.JA));
        List<Candidate> c = k.convert("きょう", 20);
        assertEquals("今日", c.get(0).text);
        assertTrue(texts(c).contains("きょう"));
        assertTrue(texts(c).contains("キョウ"));
        assertEquals("今日はいい天気", k.convert("きょうはいいてんき", 20).get(0).text);
        assertEquals("電車に乗る", k.convert("でんしゃにのる", 20).get(0).text);
        // 앞의 단어만 바꾸는 후보: 고르면 나머지 읽기가 남는다
        for (Candidate x : k.convert("きょうはいいてんき", 40)) if (x.text.equals("今日")) assertEquals(3, x.consumed);
    }

    @Test
    public void kanaPrediction() {
        KanaConverter k = new KanaConverter(CjkTestData.dict(CjkTestData.JA));
        List<String> t = texts(k.convert("ありが", 20));
        assertTrue(t.contains("有り難う"));
    }

    @Test
    public void romaji() {
        assertEquals("きょう", romajiOf("kyou"));
        assertEquals("がっこう", romajiOf("gakkou"));
        assertEquals("しんぶん", romajiOf("shinbunn"));
        assertEquals("こんにちは", romajiOf("konnnitiha"));
        assertEquals("ちゃ", romajiOf("cha"));
        assertEquals("らーめん", romajiOf("ra-menn"));
        // nn은 다음 글자를 보고 정한다 (MS-IME와 같다)
        assertEquals("こんにちは", romajiOf("konnichiha"));
        assertEquals("おんな", romajiOf("onna"));
        assertEquals("たんい", romajiOf("tan'i"));
        assertEquals("しんぶん", romajiOf("shinbun"));
        assertEquals("くぁ", romajiOf("kwa"));
    }

    @Test
    public void pinyinExactSyllableBeforeExpanded() {
        PinyinConverter p = new PinyinConverter(CjkTestData.dict(CjkTestData.ZH));
        List<String> t = texts(p.convert("e", 20));
        assertEquals("饿", t.get(0));   // 끝까지 친 e가 넓혀 찾은 er(而)보다 먼저
        assertTrue(t.indexOf("而") > t.indexOf("额"));
    }

    @Test
    public void pinyinAbbreviation() {
        PinyinConverter p = new PinyinConverter(CjkTestData.dict(CjkTestData.ZH));
        assertEquals("北京", p.convert("bj", 20).get(0).text);   // 성모만 쳐도 (简拼)
        assertEquals("你好", p.convert("nh", 20).get(0).text);
        assertEquals("北京", p.convert("beij", 20).get(0).text);
        // 성모 뒤에 모음이 오면 음절을 이어 친 것이라 간이 병음으로 보지 않는다
        for (Candidate c : p.convert("nihao", 20)) assertTrue(c.consumed == 2 || c.consumed == 5);
    }

    private static String romajiOf(String keys) {
        StringBuilder out = new StringBuilder();
        String pending = "";
        for (char c : keys.toCharArray()) pending = Kana.romaji(pending + c, out);
        return out + Kana.finishRomaji(pending);
    }

    @Test
    public void dakutenCycle() {
        assertEquals('ば', Kana.cycle('は'));
        assertEquals('ぱ', Kana.cycle('ば'));
        assertEquals('は', Kana.cycle('ぱ'));
        assertEquals('っ', Kana.cycle('つ'));
        assertEquals(0, Kana.cycle('ん'));
        assertEquals("カタカナー", Kana.toKatakana("かたかなー"));
    }

    @Test
    public void chineseComposer() {
        CjkComposer c = new CjkComposer(CjkComposer.ZH, CjkTestData.dict(CjkTestData.ZH));
        for (char ch : "nihaoma".toCharArray()) assertNull(c.typeLetter(ch));
        assertEquals("nihaoma", c.composingText());
        // 앞 단어만 고르면 나머지 병음이 남는다
        int ni = texts(c.candidates()).indexOf("你好");
        assertNull(c.pick(ni));
        assertEquals("你好ma", c.composingText());
        // 지우기는 남은 병음을 지우고, 다 지우면 고른 후보를 되돌린다
        assertTrue(c.backspace());
        assertEquals("你好m", c.composingText());
        assertTrue(c.backspace());
        assertTrue(c.backspace());
        assertEquals("nihao", c.composingText());
        for (char ch : "ma".toCharArray()) c.typeLetter(ch);
        assertEquals("你好吗", c.space());
        assertTrue(c.isEmpty());
        for (char ch : "nihao".toCharArray()) c.typeLetter(ch);
        assertEquals("nihao", c.enter());   // 엔터는 병음 그대로
    }

    @Test
    public void japaneseComposer() {
        CjkComposer c = new CjkComposer(CjkComposer.JA, CjkTestData.dict(CjkTestData.JA));
        c.typeKana("き");
        c.typeKana("よ");
        c.typeKana("゛");   // よ에는 탁점이 없어 작은 글자로
        assertEquals("きょ", c.composingText());
        c.typeKana("う");
        assertEquals("きょう", c.composingText());
        assertNull(c.space());   // 스페이스: 첫 변환 후보를 조합 영역에 보인다
        assertEquals("今日", c.composingText());
        assertTrue(c.backspace());   // 변환 취소
        assertEquals("きょう", c.composingText());
        c.space();
        assertEquals("今日", c.typeKana("は"));   // 다음 글자를 치면 변환을 확정한다
        assertEquals("は", c.composingText());
        assertEquals("は", c.enter());
        for (char ch : "kyou".toCharArray()) c.typeLetter(ch);
        assertEquals("きょう", c.composingText());
        assertEquals("今日", c.pick(0));
    }

    @Test
    public void packRejectsWrongLanguageAndKeepsOld() throws IOException {
        java.io.File dir = java.nio.file.Files.createTempDirectory("cjkpack").toFile();
        try {
            byte[] ja = zip("ja", CjkTestData.bytes(CjkTestData.JA));
            CjkPack.Info info = CjkPack.install(new ByteArrayInputStream(ja), dir, CjkComposer.JA);
            assertEquals("ja", info.lang);
            assertEquals(CjkTestData.JA.length, info.words);
            assertTrue(CjkPack.installed(dir, CjkComposer.JA) != null);
            assertNull(CjkPack.installed(dir, CjkComposer.ZH));
            try {
                CjkPack.install(new ByteArrayInputStream(ja), dir, CjkComposer.ZH);
                fail();
            } catch (CjkPack.InvalidPackException expected) {
                assertTrue(expected.getMessage().contains("일본어"));
            }
            // 병음 음절이 너무 적은 중국어 팩은 받지 않는다
            try {
                CjkPack.install(new ByteArrayInputStream(zip("zh", CjkTestData.bytes(CjkTestData.ZH))), dir, null);
                fail();
            } catch (CjkPack.InvalidPackException expected) {
                // 음절 부족
            }
            assertTrue(CjkPack.load(dir, CjkComposer.JA) != null);
            CjkPack.remove(dir, CjkComposer.JA);
            assertNull(CjkPack.installed(dir, CjkComposer.JA));
        } finally {
            for (java.io.File f : java.nio.file.Files.walk(dir.toPath()).sorted(java.util.Comparator.reverseOrder())
                    .map(java.nio.file.Path::toFile).toArray(java.io.File[]::new)) f.delete();
        }
    }

    private static byte[] zip(String lang, byte[] dict) throws IOException {
        java.io.ByteArrayOutputStream b = new java.io.ByteArrayOutputStream();
        try (java.util.zip.ZipOutputStream z = new java.util.zip.ZipOutputStream(b)) {
            z.putNextEntry(new java.util.zip.ZipEntry("manifest.properties"));
            z.write(("format=1\nlang=" + lang + "\nname=test\n").getBytes(java.nio.charset.StandardCharsets.UTF_8));
            z.putNextEntry(new java.util.zip.ZipEntry("convert.bin"));
            z.write(dict);
            z.putNextEntry(new java.util.zip.ZipEntry("../evil.txt"));
            z.write(1);
        }
        return b.toByteArray();
    }
}
