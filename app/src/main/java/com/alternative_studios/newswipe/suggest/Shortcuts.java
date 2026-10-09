package com.alternative_studios.newswipe.suggest;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * 단축어: 입력 중인 단어가 사용자가 정해 둔 줄임말과 같으면 그 문장을 추천에 보여 준다 ('ㅈㄱㅈ' → '지금 가는 중').
 * 안드로이드 의존성이 없어 단위 테스트할 수 있다. 저장은 {@link com.alternative_studios.newswipe.Prefs}가 한다.
 */
public final class Shortcuts {
    public static final int MAX_ENTRIES = 100;
    public static final int MAX_KEY = 20;
    public static final int MAX_PHRASE = 300;

    private final Map<String, String> map = new HashMap<>();

    private Shortcuts() {
    }

    /** 영어 줄임말은 대소문자를 가리지 않는다. */
    public static String normalize(String key) {
        return key.toLowerCase(Locale.ROOT);
    }

    /** 줄임말 앞뒤 공백과 문장 앞뒤 공백·줄바꿈을 정리한다. */
    public static String cleanPhrase(String phrase) {
        return phrase.replace('\r', ' ').replace('\n', ' ').trim();
    }

    /**
     * 줄임말과 문장을 저장해도 되는지 확인한다.
     *
     * @return 안 되면 이유(사용자에게 보여 줄 문구), 되면 null
     */
    public static String validate(String key, String phrase) {
        if (key == null || key.isEmpty()) return "단축어를 적어 주세요";
        if (key.length() > MAX_KEY) return "단축어는 " + MAX_KEY + "자까지 쓸 수 있습니다";
        if (!(WordSuggester.currentWord(key, true).equals(key) || WordSuggester.currentWord(key, false).equals(key))
                || key.indexOf('\'') >= 0) {
            return "단축어는 한글(자음·모음 포함)이나 영어 글자만 쓸 수 있고, 한글과 영어를 섞을 수 없습니다";
        }
        if (phrase == null || cleanPhrase(phrase).isEmpty()) return "문장을 적어 주세요";
        if (cleanPhrase(phrase).length() > MAX_PHRASE) return "문장은 " + MAX_PHRASE + "자까지 쓸 수 있습니다";
        return null;
    }

    /** @param entries 줄임말 → 문장. 쓸 수 없는 줄임말은 건너뛴다. */
    public static Shortcuts of(Map<String, String> entries) {
        Shortcuts s = new Shortcuts();
        if (entries != null) {
            for (Map.Entry<String, String> e : entries.entrySet()) {
                if (validate(e.getKey(), e.getValue()) == null && s.map.size() < MAX_ENTRIES) {
                    s.map.put(normalize(e.getKey()), cleanPhrase(e.getValue()));
                }
            }
        }
        return s;
    }

    public boolean isEmpty() {
        return map.isEmpty();
    }

    /** 입력 중인 단어와 같은 줄임말의 문장. 없으면 null. */
    public String lookup(String word) {
        if (word == null || word.isEmpty() || map.isEmpty()) return null;
        return map.get(normalize(word));
    }
}
