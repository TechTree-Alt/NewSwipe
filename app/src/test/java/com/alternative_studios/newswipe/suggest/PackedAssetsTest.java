package com.alternative_studios.newswipe.suggest;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import java.io.ByteArrayInputStream;
import java.io.IOException;

import org.junit.Test;

/**
 * 앱에 든 사전의 압축 형식: dict/build/pack_assets.py가 NSD1·NSN1을 열마다 모은 NSD2·NSN2로 바꾸고 zlib으로 압축한 파일을
 * 예전 형식과 똑같은 내용으로 읽는지. 아래 파일은 모두 pack_assets.py로 만든 것이다.
 */
public class PackedAssetsTest {
    /** 감사(200) 감사합니다(255) 안녕(200) 안녕하세요(250) 안녕히(100). */
    static final String NSD1 = "4e53443105000000250000000002c810acacc00203ff69d5c8b2e4b20002c848c555b10203fa58d538c194c602016488d7";
    static final String NSD2_ZLIB = "78daf30b763162656060e06600012606262626662666c613ff4ffc4a1158b3e640e6d5139b9e6cf2381aba31e2aac5c129c73aae030033c4128e";
    /** 같은 사전, 글자 배치 1 (낮은 바이트들 다음 높은 바이트들). */
    static final String NSD2_SPLIT_ZLIB = "78daf30b763162656060e0066246062606262626662666c613ff4ffc4a11589379e289476884c5948e3507ae6eda7474e3d583c7ae030024d4128f";
    /** 감사 → 안녕(P=e^-0.75), 안녕하세요(e^-1.5), λ=128/255; 안녕 → 안녕하세요(e^-0.75), λ=64/255. */
    static final String NSN1 = "4e534e3105000000000000410000803f0200000003000000008002020102400103030603";
    static final String NSN2_ZLIB = "78daf30bf63362650003470686067b26208319c46362626c70606266646663060044e90303";

    private static WordDictionary dict(String hex) throws IOException {
        return WordDictionary.load(new ByteArrayInputStream(KnownWordsTest.hex(hex)));
    }

    @Test
    public void packedDictionaryHasSameWords() throws IOException {
        WordDictionary a = dict(NSD1);
        for (String packed : new String[]{NSD2_ZLIB, NSD2_SPLIT_ZLIB}) {
            WordDictionary b = dict(packed);
            assertEquals(5, b.size());
            for (int i = 0; i < a.size(); i++) {
                assertEquals(a.word(i), b.word(i));
                assertEquals(a.score(i), b.score(i));
            }
            assertEquals("안녕하세요", b.word(3));
            assertEquals(250, b.score(3));
        }
    }

    @Test
    public void packedNextWordsHaveSamePairs() throws IOException {
        NextWords a = NextWords.load(new ByteArrayInputStream(KnownWordsTest.hex(NSN1)), 5);
        NextWords b = NextWords.load(new ByteArrayInputStream(KnownWordsTest.hex(NSN2_ZLIB)), 5);
        assertEquals(2, b.groups());
        assertEquals(3, b.pairs());
        for (int g = 0; g < a.groups(); g++) {
            assertEquals(a.head(g), b.head(g));
            assertEquals(a.lambda(g), b.lambda(g), 0);
            assertEquals(a.from(g), b.from(g));
            assertEquals(a.to(g), b.to(g));
        }
        for (int i = 0; i < a.pairs(); i++) {
            assertEquals(a.word(i), b.word(i));
            assertEquals(a.prob(i), b.prob(i), 0);
        }
        assertEquals(3, b.word(b.find(b.group(2), 3)));
    }

    @Test
    public void rejectsBrokenPackedFiles() {
        byte[] cut = java.util.Arrays.copyOf(KnownWordsTest.hex(NSD2_ZLIB), 20);   // 압축이 중간에 끝남
        byte[] mismatch = KnownWordsTest.hex(NSN2_ZLIB);
        try {
            WordDictionary.load(new ByteArrayInputStream(cut));
            fail("받아들이면 안 된다");
        } catch (IOException expected) {
            // 깨진 압축
        }
        try {
            NextWords.load(new ByteArrayInputStream(mismatch), 6);   // 다른 단어 사전
            fail("받아들이면 안 된다");
        } catch (IOException expected) {
            // 단어 수가 다르다
        }
    }
}
