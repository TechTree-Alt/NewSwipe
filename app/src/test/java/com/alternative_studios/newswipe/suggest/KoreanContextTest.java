package com.alternative_studios.newswipe.suggest;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.BeforeClass;
import org.junit.Test;

import java.io.ByteArrayInputStream;

/** 자동 수정에서 그럴듯한 말을 두는 것, 앞 단어 끝 글자로 본 다음 단어, 자판 칸 위치대로의 이웃 키. */
public class KoreanContextTest {
    private static WordDictionary dict;
    private static NextWords next;

    @BeforeClass
    public static void setUp() throws Exception {
        dict = KoreanSuggestTest.buildDict(new String[][]{
                // 용언 어간 가·같·먹 (가고, 가는 ...)
                {"가고", "200"}, {"가는", "200"}, {"가지", "180"}, {"같고", "180"}, {"같지", "170"}, {"같은", "220"},
                {"같이", "230"}, {"먹고", "180"}, {"먹는", "170"}, {"근데", "230"},
                // 명사 고민, 화장실·화장품 (화장은 사전에 없다)
                {"고민", "170"}, {"고민을", "120"}, {"고민의", "100"}, {"화장실", "160"}, {"화장품", "150"}, {"가장", "240"},
                // 끝 글자 '을'로 끝나는 앞 단어 넷과 그 뒤에 오는 말
                {"밥을", "150"}, {"빵을", "120"}, {"물을", "140"}, {"약을", "120"}, {"먹었어", "160"}, {"많이", "200"},
                {"감사합니다", "255"},
        });
        next = NextWords.load(new ByteArrayInputStream(KoreanSuggestTest.buildNext(dict, new String[][]{
                {"밥을", "먹었어", "0.5"}, {"빵을", "먹었어", "0.5"}, {"물을", "먹었어", "0.3"}, {"약을", "먹었어", "0.6"},
        }, 0.6)), dict.size());
        dict.warmUpChosung();
    }

    private static KoreanSuggest model() {
        return new KoreanSuggest(dict, next, new TypoModel());
    }

    @Test
    public void plausibleWordsAreNotTypos() {
        KoreanSuggest m = model();
        assertTrue(m.plausible("간대"));      // 가 + ㄴ대
        assertTrue(m.plausible("같애"));      // 같 + 애
        assertTrue(m.plausible("먹으러"));    // 먹 + 으러
        assertTrue(m.plausible("화장"));      // 화장실·화장품의 앞부분
        assertTrue(m.plausible("고민이야"));  // 고민 + 이야
        assertFalse(m.plausible("감사함니다"));
        assertFalse(m.plausible("갇고"));
    }

    @Test
    public void autoCorrectLeavesPlausibleWordsAndFixesTypos() {
        KoreanSuggest m = model();
        assertNull(m.correct("같애", null, KoreanSuggest.FIELD_NORMAL, null, null));   // 같이로 고치지 않는다
        assertNull(m.correct("간대", null, KoreanSuggest.FIELD_NORMAL, null, null));   // 근데로 고치지 않는다
        assertNull(m.correct("화장", null, KoreanSuggest.FIELD_NORMAL, null, null));   // 가장으로 고치지 않는다
        assertEquals("감사합니다", m.correct("감사함니다", null, KoreanSuggest.FIELD_NORMAL, null, null));
    }

    @Test
    public void oneEdit() {
        assertTrue(KoreanSuggest.oneEdit(TypoModel.letters("가장"), 4, TypoModel.letters("가쟝"), 4));
        assertTrue(KoreanSuggest.oneEdit(TypoModel.letters("가장"), 4, TypoModel.letters("갖앙"), 4));   // 맞바꾸기
        char[] a = TypoModel.letters("현타"), b = TypoModel.letters("한다");
        assertFalse(KoreanSuggest.oneEdit(a, a.length, b, b.length));   // 두 번 바꾸기
        char[] c = TypoModel.letters("가장");
        assertFalse(KoreanSuggest.oneEdit(c, c.length, c, c.length));   // 같은 말
    }

    @Test
    public void endingContextBacksOffForUnknownPreviousWord() {
        EndingContext e = EndingContext.build(dict, next);
        int g = e.group("떡을");   // 사전에 없는 앞 단어도 끝 글자 '을'로 찾는다
        assertTrue(g >= 0);
        assertEquals(4, e.heads(g));
        assertTrue(e.find(g, dict.indexOf("먹었어")) >= 0);
        assertEquals(-1, e.group("떡이"));
        // 빈도가 높은 '많이'보다 '을' 뒤에 자주 오는 '먹었어'를 먼저 보인다
        KoreanSuggest.Result r = model().suggest("ㅁ", "떡을", KoreanSuggest.FIELD_NORMAL, null, null, null, 3);
        assertEquals("먹었어", r.words[0]);
        r = model().suggest("ㅁ", null, KoreanSuggest.FIELD_NORMAL, null, null, null, 3);
        assertEquals("많이", r.words[0]);
    }

    @Test
    public void keyGridFollowsScreenColumns() {
        TypoModel t = new TypoModel();
        // 8열 단모음: 셋째 줄 맨 왼쪽은 Shift 자리라 ㅜ는 ㅓ 아래에 있다 (ㄹ과는 떨어져 있다)
        assertEquals(TypoModel.NEIGHBOR, t.substitute('ㅜ', 'ㅓ'), 0f);
        assertEquals(TypoModel.CROSS, t.substitute('ㅜ', 'ㄹ'), 0f);
        assertEquals(TypoModel.NEIGHBOR, t.substitute('ㅋ', 'ㄴ'), 0f);
    }
}
