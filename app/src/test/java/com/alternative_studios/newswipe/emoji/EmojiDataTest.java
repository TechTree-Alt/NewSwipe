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

    @Test
    public void suggestMatchesWordWithEnding() throws IOException {
        EmojiData d = load();
        List<String> s = d.suggest("오늘 정말 축하합니다!", 16);
        assertEquals("🥳", s.get(0));   // 이름에 '축하'가 들어간 이모지보다 키워드 순서대로, 문장 부호는 뗀다
        assertTrue(s.contains("🎂"));
        assertTrue(d.suggest("생일이야", 16).contains("🎂"));
        assertTrue(d.suggest("피자 먹자", 16).contains("🍕"));
    }

    @Test
    public void suggestInterleavesWords() throws IOException {
        EmojiData d = load();
        // '축하'에 맞는 이모지가 많아도 '생일'의 이모지가 앞쪽에 함께 나온다.
        List<String> s = d.suggest("생일 축하해", 6);
        assertEquals(6, s.size());
        assertTrue(s.contains("💐"));
        assertTrue(s.contains("👏"));
        assertEquals(s.size(), new java.util.HashSet<>(s).size());   // 중복 없음
    }

    @Test
    public void suggestEnglishTails() throws IOException {
        EmojiData d = load();
        assertTrue(d.suggest("I want cakes", 16).contains("🎂"));
        assertTrue(d.suggest("partying tonight", 16).contains("🥳"));
        assertTrue(!d.suggest("I", 16).contains("ℹ️"));   // 한 글자 키워드는 쓰지 않는다
    }

    @Test
    public void suggestChatWords() throws IOException {
        EmojiData d = load();
        assertTrue(d.suggest("미안해", 16).contains("🙏"));
        assertTrue(d.suggest("수고하셨습니다", 16).contains("👏"));
        assertTrue(d.suggest("ㅠㅠ", 16).contains("😭"));
    }

    @Test
    public void suggestSkipsWeakMatches() throws IOException {
        EmojiData d = load();
        assertTrue(d.suggest("불편해", 16).isEmpty());   // '불'(한 글자)로 🔥를 고르지 않는다
        assertTrue(d.suggest("잘 자", 16).isEmpty());   // '자'(한 글자)로 자를 고르지 않는다
        assertTrue(d.suggest("얼굴이", 16).isEmpty());   // 너무 흔한 키워드
        assertTrue(d.suggest("", 16).isEmpty());
        assertTrue(d.suggest("   ", 16).isEmpty());
        assertTrue(d.suggest(null, 16).isEmpty());
        assertTrue(d.suggest("축하", 0).isEmpty());
        assertEquals(3, d.suggest("축하", 3).size());
    }

    @Test
    public void suggestLooksAtLastWordsOnly() throws IOException {
        EmojiData d = load();
        // 커서에서 먼 단어(다섯 번째 앞)는 보지 않는다.
        assertTrue(d.suggest("피자 하나 둘 셋 넷", 16).isEmpty());
        assertTrue(d.suggest("피자 하나 둘 셋", 16).contains("🍕"));
    }

    @Test
    public void countryNamesFindFlagFirst() throws IOException {
        EmojiData d = load();
        for (String q : new String[]{"대한민국", "한국", "우리나라", "korea"}) {
            assertEquals(q, "🇰🇷", d.search(q, 5).get(0).value);
            assertEquals(q, "🇰🇷", d.suggest(q + "에서", 5).get(0));
        }
        assertEquals("🇺🇸", d.search("미국", 5).get(0).value);
        assertEquals("🇺🇸", d.suggest("미국 가요", 5).get(0));
        assertEquals("🇯🇵", d.search("일본", 5).get(0).value);   // 🗾·🏯처럼 '일본' 키워드만 있는 것보다 앞
        assertEquals("🇯🇵", d.suggest("일본 여행", 5).get(0));   // '일본'은 이모지가 많아도 국기 이름이라 쓴다
        assertEquals("🇭🇰", d.search("홍콩", 5).get(0).value);   // '홍콩(중국 특별행정구)'의 괄호 앞
        assertTrue(d.suggest("us", 5).isEmpty());   // 두 글자 약어는 넣지 않는다
    }

    @Test
    public void suggestUsesCountryInsideLongerWord() throws IOException {
        EmojiData d = load();
        // '중국어'는 그 자체로 키워드(㊗️ ㊙️)지만 '중국' 국기도 함께 추천한다.
        assertTrue(d.suggest("중국어 하는 사람", 16).contains("🇨🇳"));
        assertTrue(d.suggest("중국어", 4).contains("🇨🇳"));
        assertTrue(d.suggest("일본어", 4).contains("🇯🇵"));
        assertTrue(d.suggest("한국어", 4).contains("🇰🇷"));
    }
}
