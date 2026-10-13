package com.alternative_studios.newswipe.suggest;

import com.alternative_studios.newswipe.hangul.HangulComposer;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * 한국어 추천의 성능을 잰다 (tools/eval/run_eval.sh로 돌린다). 앱의 추천 코드를 그대로 쓴다.
 *
 * <p>평가 문장의 단어를 자판에서 치듯 한 키씩 조합기({@link HangulComposer})에 넣으며, 키마다 보이는 추천 3개에
 * 그 단어가 있으면 누른다고 보고 아낀 키 수를 센다 (추천을 누르면 단어와 띄어쓰기가 한 번에 들어간다).
 * 단어를 치기 전(앞 단어만 있을 때)의 다음 단어 예측도 같은 방식으로 센다.
 * <ul>
 *   <li>키 절약률 = 아낀 키 / (모든 단어의 키 + 띄어쓰기)</li>
 *   <li>잘못 고침 = 바르게 친 단어를 스페이스에서 다른 단어로 바꾼 비율</li>
 *   <li>오타 고침 = 일부러 넣은 오타(옆 키, 같은 키의 다른 동작, 빠뜨리기, 더 치기, 맞바꾸기)를 원래 단어로 고친 비율</li>
 * </ul>
 * 사용법: java ... SuggestEval DICT_DIR TEST_FILE [옵션]
 *   --old            예전 방식(접두사 + 빈도만, 받침 넘김 없음, 예전 자동 수정)으로 잰다
 *   --max N          평가할 문장 수 (앞에서부터 고르게 뽑는다)
 *   --learn          문장을 치는 동안 쓴 기록을 쌓아 개인화 효과까지 잰다
 *   --no-predict     다음 단어 예측을 끈다
 *   --field N        입력란 종류 (0 보통, 1 검색)
 *   --set NAME=VAL   KoreanSuggest의 계수를 바꿔 본다
 */
public final class SuggestEval {
    private static final String CHO = "ㄱㄲㄴㄷㄸㄹㅁㅂㅃㅅㅆㅇㅈㅉㅊㅋㅌㅍㅎ";
    private static final String JUNG = "ㅏㅐㅑㅒㅓㅔㅕㅖㅗㅘㅙㅚㅛㅜㅝㅞㅟㅠㅡㅢㅣ";
    private static final String JONG = "\0ㄱㄲㄳㄴㄵㄶㄷㄹㄺㄻㄼㄽㄾㄿㅀㅁㅂㅄㅅㅆㅇㅈㅊㅋㅌㅍㅎ";

    public static void main(String[] args) throws Exception {
        String dir = args[0], testFile = args[1];
        boolean old = false, learn = false, predict = true, typoOnly = false, typo = true;
        int max = Integer.MAX_VALUE;
        int field = KoreanSuggest.FIELD_NORMAL;
        for (int i = 2; i < args.length; i++) {
            switch (args[i]) {
                case "--old": old = true; break;
                case "--learn": learn = true; break;
                case "--no-predict": predict = false; break;
                case "--typo-only": typoOnly = true; break;
                case "--no-typo": typo = false; break;
                case "--max": max = Integer.parseInt(args[++i]); break;
                case "--field": field = Integer.parseInt(args[++i]); break;
                case "--set": set(args[++i]); break;
                default: throw new IllegalArgumentException(args[i]);
            }
        }
        WordDictionary d;
        try (InputStream in = new FileInputStream(new File(dir, "dict_ko.bin"))) {
            d = WordDictionary.load(in);
        }
        NextWords next = null;
        File nf = new File(dir, "dict_ko_next.bin");
        if (!old && nf.exists()) {
            try (InputStream in = new FileInputStream(nf)) {
                next = NextWords.load(in, d.size());
            }
        }
        d.warmUpChosung();
        KoreanSuggest ks = new KoreanSuggest(d, next, new TypoModel());
        UserDictionary user = null;
        UsageHistory usage = null;
        if (learn) {
            File tmp = Files.createTempDirectory("eval").toFile();
            user = new UserDictionary(new File(tmp, "u.txt"));
            usage = new UsageHistory(new File(tmp, "h.txt"));
            user.load();
            usage.load();
        }

        List<String> lines = new ArrayList<>();
        for (String l : Files.readAllLines(new File(testFile).toPath(), StandardCharsets.UTF_8)) {
            if (!l.isEmpty() && !l.startsWith("#")) lines.add(l);
        }
        if (lines.size() > max) {
            List<String> pick = new ArrayList<>();
            for (int i = 0; i < max; i++) pick.add(lines.get((int) ((long) i * lines.size() / max)));
            lines = pick;
        }

        if (typoOnly) {
            typoTest(d, ks, lines, old);
            return;
        }
        long keys = 0, saved = 0, predSaved = 0, words = 0, predHits = 0, predShown = 0, predTried = 0;
        long hitAt1 = 0, wrongFix = 0, oovWords = 0, oovWrong = 0;
        long t0 = System.nanoTime(), calls = 0;
        for (String line : lines) {
            String prev = null;
            for (String raw : line.split("\\s+")) {
                String core = strip(raw);
                boolean breakBefore = core == null || !raw.startsWith(core);
                boolean breakAfter = core == null || !raw.endsWith(core);
                if (core == null) {
                    prev = null;
                    continue;
                }
                if (breakBefore) prev = null;
                char[] k = keysOf(core);
                words++;
                keys += k.length + 1;
                boolean done = false;
                if (predict && !old && prev != null) {
                    predTried++;
                    KoreanSuggest.Result r = ks.predict(prev, field, user, usage, WordSuggester.MAX);
                    calls++;
                    if (r.words.length > 0) predShown++;
                    if (indexOf(r.words, core) >= 0) {
                        predHits++;
                        saved += k.length;
                        predSaved += k.length;
                        done = true;
                    }
                }
                HangulComposer hc = new HangulComposer();
                StringBuilder committed = new StringBuilder();
                for (int i = 0; i < k.length && !done; i++) {
                    hc.input(k[i], false, i * 1000L);
                    committed.append(hc.takeCommit());
                    String typed = committed + hc.getComposing();
                    if (i == k.length - 1) break;   // 다 쳤으면 스페이스를 누른다
                    String[] shown;
                    if (old) {
                        shown = oldSuggest(d, typed);
                    } else {
                        shown = ks.suggest(typed, prev, field, user, usage, null, WordSuggester.MAX).words;
                    }
                    calls++;
                    int at = indexOf(shown, core);
                    if (at >= 0) {
                        if (at == 0) hitAt1++;
                        saved += k.length - (i + 1);
                        done = true;
                    }
                }
                if (!done) {
                    // 다 치고 스페이스: 바르게 친 단어를 잘못 고치는지
                    String fix;
                    if (old) fix = d.contains(core) || WordSuggester.isDictWordWithParticle(d, core) ? null : d.correct(core, 140);
                    else fix = userKnows(user, core) ? null : ks.correct(core, prev, KoreanSuggest.FIELD_NORMAL, user, usage);
                    if (old && core.length() < 2) fix = null;
                    boolean inDict = d.contains(core) || WordSuggester.isDictWordWithParticle(d, core);
                    if (!inDict) {
                        oovWords++;
                        if (fix != null) oovWrong++;
                    }
                    if (fix != null) {
                        wrongFix++;
                        if (System.getenv("SHOW_WRONG") != null) System.out.println("  잘못 고침: " + prev + " " + core + " → " + fix);
                    }
                }
                if (learn) {
                    String p = prev;
                    usage.record(p != null && WordSuggester.learnable(p, true) ? p : null, core, words);
                    if (WordSuggester.learnable(core, true) && !d.contains(core)) {
                        String stem = WordSuggester.particleStem(core);
                        if (stem == null || !d.contains(stem)) user.add(stem != null ? stem : core, true, false, words);
                    }
                }
                prev = breakAfter ? null : core;
            }
        }
        double ms = (System.nanoTime() - t0) / 1e6;
        System.out.printf("문장 %d, 단어 %,d, 키 %,d%n", lines.size(), words, keys);
        System.out.printf("키 절약률 %.2f%% (그중 다음 단어 예측 %.2f%%)%n", 100.0 * saved / keys, 100.0 * predSaved / keys);
        if (predTried > 0) {
            System.out.printf("예측: 보인 비율 %.1f%%, 맞힌 비율(보인 것 중) %.1f%%%n",
                    100.0 * predShown / predTried, predShown == 0 ? 0 : 100.0 * predHits / predShown);
        }
        System.out.printf("잘못 고침: 전체 단어의 %.2f%%, 사전에 없는 단어의 %.1f%% (%d/%d)%n",
                100.0 * wrongFix / words, oovWords == 0 ? 0 : 100.0 * oovWrong / oovWords, oovWrong, oovWords);
        System.out.printf("추천 한 번에 평균 %.3f ms%n", ms / Math.max(1, calls));
        if (typo) typoTest(d, ks, lines, old);
    }

    private static boolean userKnows(UserDictionary user, String w) {
        if (user == null) return false;
        if (user.knownCountIfLoaded(w) > 0) return true;
        String stem = WordSuggester.particleStem(w);
        return stem != null && user.knownCountIfLoaded(stem) > 0;
    }

    /** 예전 방식: 접두사(마지막 글자 하나의 범위)가 맞는 단어를 점수순으로. */
    private static String[] oldSuggest(WordDictionary d, String typed) {
        List<String> out = new ArrayList<>();
        for (int id : d.lookupIds(typed, 60)) {
            String w = d.word(id);
            if (strictPrefix(w, typed)) out.add(w);
            if (out.size() == WordSuggester.MAX) break;
        }
        return out.toArray(new String[0]);
    }

    private static boolean strictPrefix(String w, String p) {
        int n = p.length();
        if (w.length() < n || !w.regionMatches(0, p, 0, n - 1)) return false;
        int[] r = HangulRange.of(p.charAt(n - 1));
        char c = w.charAt(n - 1);
        return r != null ? c >= r[0] && c <= r[1] : c == p.charAt(n - 1);
    }

    /** 일부러 오타를 넣어 고쳐지는지 본다. */
    private static void typoTest(WordDictionary d, KoreanSuggest ks, List<String> lines, boolean old) {
        Random rnd = new Random(7);
        TypoModel tm = new TypoModel();
        String[][] rows = {
                {"ㅂ", "ㅈ", "ㄷ", "ㄱ", "ㅅ", "ㅗ", "ㅐ", "ㅔ"},
                {"ㅁ", "ㄴ", "ㅇ", "ㄹ", "ㅎ", "ㅓ", "ㅏ", "ㅣ"},
                {"ㅋ", "ㅌ", "ㅊ", "ㅍ", "ㅜ", "ㅡ"},
        };
        int tried = 0, fixed = 0, wrong = 0;
        long correctNs = 0;
        for (String line : lines) {
            String prev = null;
            for (String raw : line.split("\\s+")) {
                String core = strip(raw);
                if (core == null || core.length() < 2 || !d.contains(core) || rnd.nextInt(3) != 0) {
                    prev = core;
                    continue;
                }
                char[] k = keysOf(core);
                char[] bad = typo(k, rows, rnd);
                String typed = compose(bad);
                if (typed == null || typed.equals(core) || d.contains(typed)
                        || WordSuggester.isDictWordWithParticle(d, typed) || !allSyllables(typed)) {
                    prev = core;
                    continue;
                }
                tried++;
                long c0 = System.nanoTime();
                String fix = old ? d.correct(typed, 140) : ks.correct(typed, prev, KoreanSuggest.FIELD_NORMAL, null, null);
                correctNs += System.nanoTime() - c0;
                if (core.equals(fix)) fixed++;
                else if (fix != null) wrong++;
                prev = core;
            }
        }
        System.out.printf("오타 %d개: 바르게 고침 %.1f%%, 다른 단어로 고침 %.1f%%%n", tried,
                100.0 * fixed / Math.max(1, tried), 100.0 * wrong / Math.max(1, tried));
        System.out.printf("자동 수정 한 번에 평균 %.3f ms%n", correctNs / 1e6 / Math.max(1, tried));
    }

    private static char[] typo(char[] k, String[][] rows, Random rnd) {
        StringBuilder s = new StringBuilder(new String(k));
        int i = rnd.nextInt(k.length);
        int kind = rnd.nextInt(10);
        char c = k[i];
        if (kind < 4) {   // 옆 키
            char n = neighbor(c, rows, rnd);
            if (n != 0) s.setCharAt(i, n);
        } else if (kind < 6) {   // 같은 키의 다른 동작
            String[] groups = {"ㄱㄲㅋ", "ㄷㄸㅌ", "ㅂㅃㅍ", "ㅅㅆ", "ㅈㅉㅊ", "ㅏㅑ", "ㅓㅕ", "ㅗㅛ", "ㅜㅠ", "ㅐㅒ", "ㅔㅖ", "ㅐㅔ"};
            for (String g : groups) {
                int p = g.indexOf(c);
                if (p >= 0) {
                    s.setCharAt(i, g.charAt((p + 1 + rnd.nextInt(g.length() - 1)) % g.length()));
                    break;
                }
            }
        } else if (kind < 8) {   // 빠뜨리기
            if (k.length > 3) s.deleteCharAt(i);
        } else if (kind < 9) {   // 더 치기 (같은 키 두 번)
            s.insert(i, c);
        } else if (i + 1 < k.length) {   // 맞바꾸기
            s.setCharAt(i, k[i + 1]);
            s.setCharAt(i + 1, c);
        }
        return s.toString().toCharArray();
    }

    private static char neighbor(char c, String[][] rows, Random rnd) {
        for (int r = 0; r < rows.length; r++) {
            for (int col = 0; col < rows[r].length; col++) {
                if (rows[r][col].charAt(0) != c) continue;
                List<Character> n = new ArrayList<>();
                if (col > 0) n.add(rows[r][col - 1].charAt(0));
                if (col + 1 < rows[r].length) n.add(rows[r][col + 1].charAt(0));
                if (r > 0 && col < rows[r - 1].length) n.add(rows[r - 1][col].charAt(0));
                if (r + 1 < rows.length && col < rows[r + 1].length) n.add(rows[r + 1][col].charAt(0));
                return n.isEmpty() ? 0 : n.get(rnd.nextInt(n.size()));
            }
        }
        return 0;
    }

    private static String compose(char[] k) {
        HangulComposer hc = new HangulComposer();
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < k.length; i++) {
            hc.input(k[i], false, i * 1000L);
            out.append(hc.takeCommit());
        }
        hc.flush();
        out.append(hc.takeCommit());
        return out.toString();
    }

    private static boolean allSyllables(String s) {
        for (int i = 0; i < s.length(); i++) if (s.charAt(i) < 0xAC00 || s.charAt(i) > 0xD7A3) return false;
        return true;
    }

    /** 단어를 치는 키 (자모 하나 = 키 하나; 겹모음·겹받침·쌍자음도 밀기 한 번). */
    static char[] keysOf(String w) {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < w.length(); i++) {
            int s = w.charAt(i) - 0xAC00;
            b.append(CHO.charAt(s / 588)).append(JUNG.charAt((s / 28) % 21));
            if (s % 28 != 0) b.append(JONG.charAt(s % 28));
        }
        return b.toString().toCharArray();
    }

    /** 어절 앞뒤의 문장 부호를 뗀 한글 부분. 한글만 남지 않으면 null (build_ko_model.py와 같은 기준). */
    static String strip(String raw) {
        int i = 0, j = raw.length();
        while (i < j && !hangul(raw.charAt(i))) i++;
        while (j > i && !hangul(raw.charAt(j - 1))) j--;
        if (i >= j) return null;
        for (int k = i; k < j; k++) if (!hangul(raw.charAt(k))) return null;
        return raw.substring(i, j);
    }

    private static boolean hangul(char c) {
        return c >= 0xAC00 && c <= 0xD7A3;
    }

    private static int indexOf(String[] a, String w) {
        for (int i = 0; i < a.length; i++) if (a[i].equals(w)) return i;
        return -1;
    }

    private static void set(String kv) throws Exception {
        String[] p = kv.split("=", 2);
        java.lang.reflect.Field f = KoreanSuggest.class.getDeclaredField(p[0]);
        f.setAccessible(true);
        Class<?> t = f.getType();
        if (t == double.class) f.setDouble(null, Double.parseDouble(p[1]));
        else if (t == float.class) f.setFloat(null, Float.parseFloat(p[1]));
        else if (t == int.class) f.setInt(null, Integer.parseInt(p[1]));
        else throw new IllegalArgumentException(kv);
    }
}
