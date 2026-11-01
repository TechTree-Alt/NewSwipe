package com.alternative_studios.newswipe.cjk;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 일본어·중국어 입력 중인 글자(조합 영역)의 상태. 읽기를 모으고, 후보를 고르면 그만큼 읽기를 써 버린다.
 * 읽기를 다 쓰면 고른 글자 전체를 확정한다. 확정할 글자는 메서드가 돌려준다 (없으면 null).
 *
 * <ul>
 *   <li>중국어: 읽기는 병음 글자와 '. 스페이스는 첫 후보(또는 고른 후보)를 고른다. 엔터는 병음 그대로 확정한다.</li>
 *   <li>일본어: 읽기는 히라가나(로마자는 가나로 바꿔 모은다). 스페이스는 변환 후보를 차례로 고르고(조합 영역에 보인다),
 *       엔터는 고른 후보(없으면 가나 그대로)를 확정한다.</li>
 * </ul>
 * 안드로이드에 기대지 않는다.
 */
public final class CjkComposer {
    public static final String JA = "ja", ZH = "zh";
    /** 한 번에 만드는 후보 수의 상한. */
    public static final int MAX_CANDIDATES = 80;

    public final String lang;
    private final Converter converter;
    private final PinyinConverter pinyin;
    private final StringBuilder reading = new StringBuilder();
    /** 일본어 로마자 입력에서 아직 가나가 되지 않은 글자. */
    private String pending = "";
    /** 지금까지 고른 후보 {글자, 그 후보가 쓴 읽기}. */
    private final List<String[]> picked = new ArrayList<>();
    /** 일본어 스페이스 변환으로 고른 후보 번호 (조합 영역에 보인다). 없으면 -1. */
    private int highlight = -1;
    private String cachedFor;
    private List<Candidate> cached = Collections.emptyList();

    public CjkComposer(String lang, ConversionDictionary dict) {
        this.lang = lang;
        if (ZH.equals(lang)) {
            pinyin = new PinyinConverter(dict);
            converter = pinyin;
        } else {
            pinyin = null;
            converter = new KanaConverter(dict);
        }
    }

    public boolean chinese() {
        return pinyin != null;
    }

    public boolean isEmpty() {
        return reading.length() == 0 && pending.isEmpty() && picked.isEmpty();
    }

    public int highlight() {
        return highlight;
    }

    public void reset() {
        reading.setLength(0);
        pending = "";
        picked.clear();
        highlight = -1;
    }

    /** 중국어 병음 글자·'(a~z, ') 또는 일본어 로마자 글자를 더한다. 일본어 변환 중이었으면 그 변환을 확정해 돌려준다. */
    public String typeLetter(char c) {
        String done = confirmHighlight();
        c = Character.toLowerCase(c);
        if (chinese()) {
            if (c == '\'' && (reading.length() == 0 || reading.charAt(reading.length() - 1) == '\'')) return done;
            reading.append(c);
        } else {
            pending = Kana.romaji(pending + c, reading);
        }
        return done;
    }

    /** 일본어: 가나·기호를 읽기에 더한다. "゛"는 마지막 글자의 탁점·반탁점·작은 글자를 돌린다. */
    public String typeKana(String s) {
        if (s.equals("゛")) {
            if (highlight >= 0) highlight = -1;
            int last = reading.length() - 1;
            char next = last >= 0 ? Kana.cycle(reading.charAt(last)) : 0;
            if (next != 0) reading.setCharAt(last, next);
            return null;
        }
        String done = confirmHighlight();
        flushPending();
        reading.append(s);
        return done;
    }

    private void flushPending() {
        if (!pending.isEmpty()) {
            reading.append(Kana.finishRomaji(pending));
            pending = "";
        }
    }

    /** 지울 것이 있었으면 true (변환 취소, 로마자, 읽기, 고른 후보 되돌리기 순서). */
    public boolean backspace() {
        if (highlight >= 0) {
            highlight = -1;
            return true;
        }
        if (!pending.isEmpty()) {
            pending = pending.substring(0, pending.length() - 1);
            return true;
        }
        if (reading.length() > 0) {
            reading.setLength(reading.length() - 1);
            if (chinese() && reading.length() > 0 && reading.charAt(reading.length() - 1) == '\'') {
                reading.setLength(reading.length() - 1);
            }
            return true;
        }
        if (!picked.isEmpty()) {
            String[] last = picked.remove(picked.size() - 1);   // 고른 후보를 되돌려 다시 고르게 한다
            reading.insert(0, last[1]);
            return true;
        }
        return false;
    }

    /** 변환할 읽기 (일본어 로마자의 남은 n은 ん으로 본다). */
    private String convertible() {
        if (chinese()) return reading.toString();
        return pending.equals("n") || pending.equals("nn") ? reading + "ん" : reading.toString();
    }

    /** 지금 읽기의 후보. 같은 읽기면 다시 찾지 않는다. */
    public List<Candidate> candidates() {
        String r = convertible();
        if (!r.equals(cachedFor)) {
            cachedFor = r;
            cached = r.isEmpty() ? Collections.emptyList() : converter.convert(r, MAX_CANDIDATES);
        }
        return cached;
    }

    /** 입력란의 조합 영역에 보일 글자. */
    public String composingText() {
        StringBuilder b = new StringBuilder();
        for (String[] p : picked) b.append(p[0]);
        List<Candidate> c = highlight >= 0 ? candidates() : null;
        if (c != null && highlight < c.size()) {
            Candidate h = c.get(highlight);
            b.append(h.text).append(reading, Math.min(h.consumed, reading.length()), reading.length());
        } else {   // 중국어도 친 그대로 보인다 (커서를 옮기면 보이던 글자가 그대로 입력란에 남는다)
            b.append(reading);
        }
        // 기다리는 nn은 이미 ん으로 보인다 (다음 글자에 따라 ん + な행이 될 수 있다).
        return b.append(chinese() || !pending.equals("nn") ? pending : "ん").toString();
    }

    /** i번째 후보를 고른다. 읽기를 다 썼으면 확정할 글자 전체를 돌려주고 비운다. */
    public String pick(int i) {
        List<Candidate> c = candidates();
        if (i < 0 || i >= c.size()) return null;
        Candidate x = c.get(i);
        String r = convertible();
        int used = Math.min(x.consumed, r.length());
        picked.add(new String[]{x.text, r.substring(0, used)});
        if (!chinese() && used >= r.length()) pending = "";   // 남은 n까지 썼다
        int fromReading = Math.min(used, reading.length());
        reading.delete(0, fromReading);
        while (chinese() && reading.length() > 0 && reading.charAt(0) == '\'') reading.deleteCharAt(0);
        highlight = -1;
        if (reading.length() == 0 && pending.isEmpty()) return takeAll();
        return null;
    }

    private String takeAll() {
        StringBuilder b = new StringBuilder();
        for (String[] p : picked) b.append(p[0]);
        reset();
        return b.toString();
    }

    /** 스페이스: 중국어는 후보를 고르고, 일본어는 다음 변환 후보를 조합 영역에 보인다. */
    public String space() {
        List<Candidate> c = candidates();
        if (c.isEmpty()) return commitRaw();
        if (chinese()) return pick(highlight >= 0 ? highlight : 0);
        highlight = (highlight + 1) % c.size();
        return null;
    }

    /** 일본어 변환 중(스페이스로 고른 후보가 있음)이면 그 후보와 나머지 읽기를 확정해 돌려준다. */
    public String confirmHighlight() {
        if (highlight < 0) return null;
        String first = pick(highlight);
        return first != null ? first : commitRaw();
    }

    /** 엔터: 일본어 변환 중이면 그 변환을, 아니면 읽기를 그대로 확정한다. */
    public String enter() {
        String h = confirmHighlight();
        return h != null ? h : commitRaw();
    }

    /** 고른 후보와 남은 읽기(중국어는 '를 뺀 병음)를 그대로 확정한다. */
    public String commitRaw() {
        StringBuilder b = new StringBuilder();
        for (String[] p : picked) b.append(p[0]);
        b.append(chinese() ? reading.toString().replace("'", "") : reading.toString());
        b.append(chinese() ? pending : Kana.finishRomaji(pending));
        reset();
        return b.toString();
    }

    /** 문장 부호 등으로 끝낼 때: 첫 후보를 이어 골라 다 확정한다 (후보가 없으면 그대로). */
    public String commitBest() {
        if (isEmpty()) return "";
        if (!chinese()) return enter();   // 일본어는 바꾸지 않은 가나를 그대로 둔다
        for (int guard = 0; guard < 64; guard++) {
            if (candidates().isEmpty()) return commitRaw();
            String done = pick(0);
            if (done != null) return done;
        }
        return commitRaw();
    }
}
