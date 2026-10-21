package com.alternative_studios.newswipe.suggest;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.ByteArrayInputStream;
import java.io.IOException;

import org.junit.Test;

/** 맞는 말 목록 (블룸 필터): dict/build/build_known_words.py가 만든 파일을 같은 해시로 읽는지. */
public class KnownWordsTest {
    /** build_known_words.py로 만든 작은 목록 (공황, 배탈, 명언; 비트 256개, 해시 3개). */
    static final String FILE =
            "4e534b310001000003000000030000000000000000000000000000000028000000040400020280040000080000";

    static byte[] hex(String s) {
        byte[] b = new byte[s.length() / 2];
        for (int i = 0; i < b.length; i++) b[i] = (byte) Integer.parseInt(s.substring(2 * i, 2 * i + 2), 16);
        return b;
    }

    @Test
    public void hashMatchesBuildScript() {
        // build_known_words.fnv()가 낸 값
        assertEquals(0xd0c8e41345516783L, KnownWords.fnv("공황", KnownWords.SEED1));
        assertEquals(0x38d96c5bdeae841eL, KnownWords.fnv("공황", KnownWords.SEED2));
        assertEquals(0x478aaebd2996aeb0L, KnownWords.fnv("배탈이", KnownWords.SEED1));
    }

    @Test
    public void readsFileFromBuildScript() throws IOException {
        KnownWords k = KnownWords.load(new ByteArrayInputStream(hex(FILE)));
        assertTrue(k.contains("공황"));
        assertTrue(k.contains("배탈"));
        assertTrue(k.contains("명언"));
        assertFalse(k.contains("공항"));
        assertFalse(k.contains("자덩"));
    }

    @Test
    public void rejectsBrokenFile() {
        byte[] b = hex(FILE);
        b[0] = 'X';
        try {
            KnownWords.load(new ByteArrayInputStream(b));
            fail("받아들이면 안 된다");
        } catch (IOException expected) {
            // 형식이 다르다
        }
    }

    @Test
    public void knownWordIsNotCorrected() throws Exception {
        WordDictionary dict = KoreanSuggestTest.buildDict(new String[][]{
                {"공항", "250"}, {"공항이", "200"}, {"공항에", "200"}, {"공항을", "200"}, {"사람", "220"}, {"하고", "230"},
        });
        KoreanSuggest ks = new KoreanSuggest(dict, null, new TypoModel());
        assertEquals("공항", ks.correct("공황", null, KoreanSuggest.FIELD_NORMAL, null, null));   // 목록이 없으면 고친다
        ks.setKnownWords(KnownWords.load(new ByteArrayInputStream(hex(FILE))));
        assertNull(ks.correct("공황", null, KoreanSuggest.FIELD_NORMAL, null, null));    // 목록에 있는 맞는 말
    }
}
