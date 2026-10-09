package com.alternative_studios.newswipe.emoji;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.List;

public class EmojiDataTest {

    private static EmojiData load() throws IOException {
        File f = new File("src/main/assets/emoji.tsv");
        if (!f.exists()) f = new File("app/src/main/assets/emoji.tsv");
        try (Reader r = new InputStreamReader(new FileInputStream(f), StandardCharsets.UTF_8)) {
            return EmojiData.parse(r, null);
        }
    }

    private static boolean contains(List<EmojiData.Emoji> list, String emoji) {
        for (EmojiData.Emoji e : list) if (e.value.equals(emoji)) return true;
        return false;
    }

    @Test
    public void loadsAllGroups() throws IOException {
        EmojiData d = load();
        assertTrue(d.size() > 1900);
        for (int i = 1; i < EmojiData.GROUPS.length; i++) assertTrue(d.group(i).size() > 50);
        assertEquals(0, d.group(0).size());
    }

    @Test
    public void groupsAreRegrouped() throws IOException {
        EmojiData d = load();
        int smileys = 1, nature = 3, objects = 7, symbols = 8;
        assertTrue(contains(d.group(symbols), "❤️"));      // 하트는 표정이 아니라 기호 탭
        assertTrue(contains(d.group(symbols), "💯"));
        assertTrue(!contains(d.group(smileys), "❤️"));
        assertTrue(contains(d.group(nature), "☀️"));       // 날씨는 자연 탭
        assertTrue(contains(d.group(objects), "⏰"));      // 시계는 사물 탭
        assertEquals("💌", d.group(symbols).get(0).value);  // 기호 탭은 하트부터
        for (int i = 1; i < EmojiData.GROUPS.length; i++) {
            assertTrue(EmojiData.GROUPS[i], contains(d.group(i), EmojiData.GROUP_ICONS[i]));
        }
    }

    @Test
    public void searchKorean() throws IOException {
        EmojiData d = load();
        assertEquals("😀", d.search("활짝 웃는 얼굴", 10).get(0).value);
        assertTrue(contains(d.search("하트", 30), "❤️"));
        assertTrue(contains(d.search("고양이", 10), "🐱"));
        assertTrue(contains(d.search("등대", 5), "🛙"));      // Emoji 18.0
    }

    @Test
    public void nameIsFirstKoreanKeywordAndSearchOrderIsStable() throws IOException {
        EmojiData d = load();
        // 이름은 검색용 키워드를 나누기 전에도 첫 번째 한국어 이름이어야 한다.
        EmojiData.Emoji grin = d.find("😀");
        assertEquals("활짝 웃는 얼굴", grin.name());
        // 이름을 먼저 읽은 뒤에도 검색 결과가 같아야 한다 (지연 분할 확인).
        assertEquals("😀", d.search("활짝 웃는 얼굴", 10).get(0).value);
        assertEquals("활짝 웃는 얼굴", grin.name());
    }

    @Test
    public void searchEnglish() throws IOException {
        EmojiData d = load();
        assertTrue(contains(d.search("cat", 10), "🐱"));
        assertTrue(contains(d.search("Pickle", 5), "🫝"));   // Emoji 18.0, 대소문자 무시
        assertTrue(contains(d.search("thumbs up", 5), "👍"));
    }

    @Test
    public void skinToneVariants() throws IOException {
        EmojiData d = load();
        EmojiData.Emoji wave = d.find("👋");
        assertEquals(5, wave.variants.length);
        assertEquals(wave, d.find("👋🏽"));
    }

    @Test
    public void glyphFilterHidesUnsupported() throws IOException {
        File f = new File("src/main/assets/emoji.tsv");
        if (!f.exists()) f = new File("app/src/main/assets/emoji.tsv");
        try (Reader r = new InputStreamReader(new FileInputStream(f), StandardCharsets.UTF_8)) {
            EmojiData d = EmojiData.parse(r, e -> !e.equals("🫝"));
            assertTrue(d.search("pickle", 5).isEmpty() || !contains(d.search("pickle", 5), "🫝"));
        }
    }
}
