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
 * 한국어 추천의 성능을 잰다 (dict/eval/run_eval.sh로 돌린다). 앱의 추천 코드를 그대로 쓴다.
 *
 * <p>평가 문장의 단어를 자판에서 치듯 한 키씩 조합기({@link HangulComposer})에 넣으며, 키마다 보이는 추천 3개에
 * 그 단어가 있으면 누른다고 보고 아낀 키 수를 센다 (추천을 누르면 단어와 띄어쓰기가 한 번에 들어간다).
 * 단어를 치기 전(앞 단어만 있을 때)의 다음 단어 예측도 같은 방식으로 센다.
 * <ul>
 *   <li>키 절약률 = 아낀 키 / (모든 단어의 키 + 띄어쓰기). '첫 칸만'은 추천의 첫 칸만 보고 누르는 사용자일 때 (세 칸을 다 훑는
 *       이상적인 사용자와 얼마나 다른지 본다). 단어 종류(사전 단어, 사전 단어 + 조사, 그 밖)별로도 나눠 찍는다.</li>
 *   <li>잘못 고침 = 바르게 친 단어를 스페이스에서 다른 단어로 바꾼 비율</li>
 *   <li>오타 고침 = 일부러 넣은 오타(옆 키, 같은 키의 다른 동작, 빠뜨리기, 더 치기, 맞바꾸기)를 원래 단어로 고친 비율</li>
 *   <li>만들어 낸 후보 = 보인 추천 칸 중 사전에도 내 기록에도 없는 말(이어 붙인 말, 조사를 붙인 말)의 비율과 그중 맞힌 비율.
 *       키 절약률은 틀린 후보가 보여도 깎이지 않으므로, 엉뚱한 후보가 늘지 않았는지 이것으로 함께 본다 (SHOW_MADE=1이면 예시 40개를, SHOW_MADE=500이면 500개까지 찍는다)</li>
 * </ul>
 * 사용법: java ... SuggestEval DICT_DIR TEST_FILE [옵션]
 *   --old            예전 방식(접두사 + 빈도만, 받침 넘김 없음, 예전 자동 수정)으로 잰다
 *   --max N          평가할 문장 수 (앞에서부터 고르게 뽑는다)
 *   --learn          문장을 치는 동안 쓴 기록을 쌓아 개인화 효과까지 잰다
 *   --learn-gaps     --learn과 함께: 예전 학습의 빈틈을 흉내 낸다 (문장 끝 단어(보내기 버튼으로 보냄)와 ㅋㅋ가 붙은 단어는 배우지 않는다)
 *   --no-predict     다음 단어 예측을 끈다
 *   --field N        입력란 종류 (0 보통, 1 검색)
 *   --per-sentence F 문장마다 {키 수, 아낀 키 수}를 F에 쓴다 (compare_ksr.py로 두 설정을 견준다)
 *   --set NAME=VAL   KoreanSuggest의 계수를 바꿔 본다
 *   --layout 8|7     오타를 넣을 자판 (8 = 8열 단모음, 7 = NewSwipe 단모음). 자동 수정의 이웃 키도 이 자판으로 맞춘다
 *   --seed N         오타를 넣는 난수 씨앗 (기본 7)
 *
 * <p>평가 문장은 저장소에 든 ko_chat_test.txt(직접 쓴 문장, 작다) 말고도 fetch_public_sets.sh로 받는 공개 말뭉치
 * (NSMC 평가용 리뷰, Chatbot_data의 질문)를 dev(계수 맞추기)와 test(마지막 확인)로 나눠 쓴다. 계수는 dev로만 맞춘다.
 */
public final class SuggestEval {
    private static final String CHO = "ㄱㄲㄴㄷㄸㄹㅁㅂㅃㅅㅆㅇㅈㅉㅊㅋㅌㅍㅎ";
    private static final String JUNG = "ㅏㅐㅑㅒㅓㅔㅕㅖㅗㅘㅙㅚㅛㅜㅝㅞㅟㅠㅡㅢㅣ";
    private static final String JONG = "\0ㄱㄲㄳㄴㄵㄶㄷㄹㄺㄻㄼㄽㄾㄿㅀㅁㅂㅄㅅㅆㅇㅈㅊㅋㅌㅍㅎ";

    /** 8열 단모음과 NewSwipe 단모음의 글자 줄 (화면의 칸 위치대로, 빈 문자열은 글자가 아닌 칸). 앱의 KeyboardLayout과 같다. */
    static final String[][] ROWS_8 = {
            {"ㅂ", "ㅈ", "ㄷ", "ㄱ", "ㅅ", "ㅗ", "ㅐ", "ㅔ"},
            {"ㅁ", "ㄴ", "ㅇ", "ㄹ", "ㅎ", "ㅓ", "ㅏ", "ㅣ"},
            {"", "ㅋ", "ㅌ", "ㅊ", "ㅍ", "ㅜ", "ㅡ"},
    };
    static final String[][] ROWS_7 = {
            {"ㅂ", "ㅈ", "ㄷ", "ㄱ", "ㅗ", "ㅐ", "ㅔ"},
            {"ㅁ", "ㄴ", "ㅇ", "ㄹ", "ㅓ", "ㅏ", "ㅣ"},
            {"ㅍ", "ㅋ", "ㅎ", "ㅅ", "ㅜ", "ㅡ"},
    };
    /** 같은 키의 다른 동작으로 잘못 나오는 글자 (밀기·두 번 누르기). NewSwipe 단모음은 ㄱ을 오른쪽으로 밀어 ㅋ을 넣는다. */
    private static final String[] SAME_KEY_8 = {"ㄱㄲ", "ㄷㄸ", "ㅂㅃ", "ㅅㅆ", "ㅈㅉ", "ㅏㅑ", "ㅓㅕ", "ㅗㅛ", "ㅜㅠ", "ㅐㅒ", "ㅔㅖ", "ㅐㅔ"};
    private static final String[] SAME_KEY_7 = {"ㄱㄲㅋ", "ㄷㄸㅌ", "ㅂㅃㅍ", "ㅅㅆ", "ㅈㅉㅊ", "ㅏㅑ", "ㅓㅕ", "ㅗㅛ", "ㅜㅠ", "ㅐㅒ", "ㅔㅖ", "ㅐㅔ"};
    private static String[][] rows = ROWS_8;
    private static String[] sameKey = SAME_KEY_8;
    private static long seed = 7;

    public static void main(String[] args) throws Exception {
        String dir = args[0], testFile = args[1];
        boolean old = false, learn = false, predict = true, typoOnly = false, typo = true;
        String perSentence = null;
        boolean learnGaps = false;
        int max = Integer.MAX_VALUE;
        int field = KoreanSuggest.FIELD_NORMAL;
        for (int i = 2; i < args.length; i++) {
            switch (args[i]) {
                case "--old": old = true; break;
                case "--learn": learn = true; break;
                case "--no-predict": predict = false; break;
                case "--typo-only": typoOnly = true; break;
                case "--per-sentence": perSentence = args[++i]; break;
                case "--learn-gaps": learnGaps = true; break;
                case "--no-typo": typo = false; break;
                case "--max": max = Integer.parseInt(args[++i]); break;
                case "--field": field = Integer.parseInt(args[++i]); break;
                case "--set": set(args[++i]); break;
                case "--layout": {
                    boolean seven = args[++i].equals("7");
                    rows = seven ? ROWS_7 : ROWS_8;
                    sameKey = seven ? SAME_KEY_7 : SAME_KEY_8;
                    break;
                }
                case "--seed": seed = Long.parseLong(args[++i]); break;
                default: throw new IllegalArgumentException(args[i]);
            }
        }
        Runtime rt = Runtime.getRuntime();
        long heap0 = usedHeap(rt);
        long l0 = System.nanoTime();
        WordDictionary d;
        try (InputStream in = new FileInputStream(new File(dir, "dict_ko.bin"))) {
            d = WordDictionary.load(in);
        }
        long l1 = System.nanoTime();
        NextWords next = null;
        File nf = new File(dir, "dict_ko_next.bin");
        if (!old && nf.exists()) {
            try (InputStream in = new FileInputStream(nf)) {
                next = NextWords.load(in, d.size());
            }
        }
        long l2 = System.nanoTime();
        d.warmUpChosung();
        long l3 = System.nanoTime();
        long heap1 = usedHeap(rt);
        System.out.printf("읽기: 단어 사전 %.0f ms, 다음 단어 사전 %.0f ms, 초성 색인 %.0f ms / 메모리 %.2f MB (단어 %d개, 단어 쌍 %d개)%n",
                (l1 - l0) / 1e6, (l2 - l1) / 1e6, (l3 - l2) / 1e6, (heap1 - heap0) / 1048576.0, d.size(),
                next == null ? 0 : next.pairs());
        long b0 = System.nanoTime();
        TypoModel tm = new TypoModel();
        tm.setRows(rows);
        KoreanSuggest ks = new KoreanSuggest(d, next, tm);
        System.out.printf("순위 모형 만들기 %.0f ms%n", (System.nanoTime() - b0) / 1e6);
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
        long keys = 0, saved = 0, predSaved = 0, words = 0, predHits = 0, predShown = 0, predTried = 0, savedTop1 = 0;
        long[] catKeys = new long[3], catSaved = new long[3], catWords = new long[3];   // 사전 단어, 사전 단어 + 조사, 그 밖
        long[] lat = new long[1 << 16];
        int latN = 0;
        long hitAt1 = 0, wrongFix = 0, oovWords = 0, oovWrong = 0;
        long t0 = System.nanoTime(), calls = 0, madeShown = 0, madeHit = 0, shownSlots = 0;
        List<String> made = System.getenv("SHOW_MADE") != null ? new ArrayList<>() : null;
        int madeMax = made == null ? 0 : System.getenv("SHOW_MADE").matches("\\d{2,}") ? Integer.parseInt(System.getenv("SHOW_MADE")) : 40;
        StringBuilder rows = new StringBuilder();
        StringBuilder why = System.getenv("WHY") != null ? new StringBuilder() : null;
        for (String line : lines) {
            long keys0 = keys, saved0 = saved;
            String prev = null;
            String[] rawTokens = line.split("\\s+");
            int lastWord = -1;
            for (int ti = 0; ti < rawTokens.length; ti++) if (strip(rawTokens[ti]) != null) lastWord = ti;
            int ti = -1;
            for (String raw : rawTokens) {
                ti++;
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
                int cat = d.contains(core) ? 0 : WordSuggester.isDictWordWithParticle(d, core) ? 1 : 2;
                catWords[cat]++;
                catKeys[cat] += k.length + 1;
                boolean done = false, doneTop1 = false;
                if (predict && !old && prev != null) {
                    predTried++;
                    long c0 = System.nanoTime();
                    KoreanSuggest.Result r = ks.predict(prev, field, user, usage, WordSuggester.MAX);
                    if (latN < lat.length) lat[latN++] = System.nanoTime() - c0;
                    calls++;
                    if (r.words.length > 0) predShown++;
                    int at = indexOf(r.words, core);
                    if (at >= 0) {
                        predHits++;
                        saved += k.length;
                        predSaved += k.length;
                        done = true;
                        if (at == 0) {
                            savedTop1 += k.length;
                            doneTop1 = true;
                        }
                    }
                }
                int savedWord = done ? k.length : 0;
                HangulComposer hc = new HangulComposer();
                StringBuilder committed = new StringBuilder();
                for (int i = 0; i < k.length && !(done && doneTop1); i++) {
                    hc.input(k[i], false, i * 1000L);
                    committed.append(hc.takeCommit());
                    String typed = committed + hc.getComposing();
                    if (i == k.length - 1) break;   // 다 쳤으면 스페이스를 누른다
                    String[] shown;
                    long c0 = System.nanoTime();
                    if (old) {
                        shown = oldSuggest(d, typed);
                    } else {
                        shown = ks.suggest(typed, prev, field, user, usage, null, WordSuggester.MAX).words;
                    }
                    if (latN < lat.length) lat[latN++] = System.nanoTime() - c0;
                    calls++;
                    int at = indexOf(shown, core);
                    if (done) {   // 세 칸 중에서는 이미 골랐다: 첫 칸만 보는 사용자를 위해 계속 친다
                        if (at == 0) {
                            savedTop1 += k.length - (i + 1);
                            doneTop1 = true;
                        }
                        continue;
                    }
                    for (String w : shown) {
                        // 사전에도 내 기록에도 없는 말 = 키보드가 만들어 낸 후보 (이어 붙인 말, 조사를 붙인 말)
                        if (d.contains(w) || (user != null && user.knownCountIfLoaded(w) > 0)) continue;
                        madeShown++;
                        if (w.equals(core)) madeHit++;
                        if (made != null && made.size() < madeMax && !w.equals(core)) made.add(typed + "→" + w);
                    }
                    shownSlots += shown.length;
                    if (at >= 0) {
                        if (at == 0) {
                            hitAt1++;
                            savedTop1 += k.length - (i + 1);
                            doneTop1 = true;
                        }
                        saved += k.length - (i + 1);
                        savedWord = k.length - (i + 1);
                        done = true;
                    }
                }
                catSaved[cat] += savedWord;
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
                if (why != null) {
                    why.append(core).append('\t').append(k.length).append('\t').append(savedWord).append('\t').append("DPO".charAt(cat))
                            .append('\t').append(prev == null ? "" : prev).append('\n');
                }
                boolean gap = learnGaps && (ti == lastWord || raw.matches(".*[ㄱ-ㅣ]$"));
                if (learn && !gap) {
                    // 앱과 같은 학습 (WordSuggester.learnInto). 시각 대신 단어 순번을 쓴다.
                    WordSuggester.learnInto(d, user, usage, core, prev, true, false, words);
                    if (WordSuggester.learnable(core, true)) ks.noteLearned(core);
                }
                prev = breakAfter ? null : core;
            }
            rows.append(keys - keys0).append('\t').append(saved - saved0).append('\n');
        }
        if (why != null) Files.write(new File(System.getenv("WHY")).toPath(), why.toString().getBytes(StandardCharsets.UTF_8));
        if (perSentence != null) Files.write(new File(perSentence).toPath(), rows.toString().getBytes(StandardCharsets.UTF_8));
        double ms = (System.nanoTime() - t0) / 1e6;
        System.out.printf("문장 %d, 단어 %,d, 키 %,d%n", lines.size(), words, keys);
        System.out.printf("키 절약률 %.2f%% (그중 다음 단어 예측 %.2f%%), 첫 칸만 %.2f%%%n", 100.0 * saved / keys,
                100.0 * predSaved / keys, 100.0 * savedTop1 / keys);
        String[] catName = {"사전 단어", "사전 단어+조사", "그 밖(사전에 없음)"};
        StringBuilder cats = new StringBuilder("단어 종류별 (단어 비율 / 키 비율 / 키 절약률):");
        for (int c = 0; c < 3; c++) {
            cats.append(String.format(" %s %.1f%%/%.1f%%/%.1f%%", catName[c], 100.0 * catWords[c] / Math.max(1, words),
                    100.0 * catKeys[c] / Math.max(1, keys), 100.0 * catSaved[c] / Math.max(1, catKeys[c])));
        }
        System.out.println(cats);
        if (predTried > 0) {
            System.out.printf("예측: 보인 비율 %.1f%%, 맞힌 비율(보인 것 중) %.1f%%%n",
                    100.0 * predShown / predTried, predShown == 0 ? 0 : 100.0 * predHits / predShown);
        }
        System.out.printf("잘못 고침: 전체 단어의 %.2f%%, 사전에 없는 단어의 %.1f%% (%d/%d)%n",
                100.0 * wrongFix / words, oovWords == 0 ? 0 : 100.0 * oovWrong / oovWords, oovWrong, oovWords);
        System.out.printf("추천 한 번에 평균 %.3f ms (중앙값 %.3f, 99%% %.3f, 최대 %.3f ms)%n", ms / Math.max(1, calls),
                percentile(lat, latN, 0.5), percentile(lat, latN, 0.99), percentile(lat, latN, 1.0));
        System.out.printf("만들어 낸 후보(사전에 없는 말): 보인 추천 칸의 %.1f%%, 그중 맞힌 것 %.1f%%%n",
                100.0 * madeShown / Math.max(1, shownSlots), 100.0 * madeHit / Math.max(1, madeShown));
        if (made != null) for (String m : made) System.out.println("  " + m);
        if (typo) typoTest(d, ks, lines, old);
    }

    /** 걸린 시간(ns) 중 q 분위 (ms). */
    private static double percentile(long[] a, int n, double q) {
        if (n == 0) return 0;
        long[] b = java.util.Arrays.copyOf(a, n);
        java.util.Arrays.sort(b);
        return b[Math.min(n - 1, (int) Math.floor(q * (n - 1)))] / 1e6;
    }

    /** GC를 몇 번 돌린 뒤 쓰고 있는 힙 (읽어 둔 사전이 차지하는 메모리를 재기 위해). */
    private static long usedHeap(Runtime rt) {
        for (int i = 0; i < 4; i++) {
            System.gc();
            try {
                Thread.sleep(50);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
        }
        return rt.totalMemory() - rt.freeMemory();
    }

    private static boolean userKnows(UserDictionary user, String w) {
        if (user == null) return false;
        return WordSuggester.userKnows(user, w);   // 앱의 자동 수정과 같은 기준
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
        Random rnd = new Random(seed);
        int tried = 0, fixed = 0, wrong = 0, inBar = 0;
        int[] kindTried = new int[5], kindFixed = new int[5];
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
                int kind = rnd.nextInt(10);
                char[] bad = typo(k, kind, rnd);
                String typed = compose(bad);
                if (typed == null || typed.equals(core) || d.contains(typed)
                        || WordSuggester.isDictWordWithParticle(d, typed) || !allSyllables(typed)) {
                    prev = core;
                    continue;
                }
                tried++;
                int kk = kind < 4 ? 0 : kind < 6 ? 1 : kind < 8 ? 2 : kind < 9 ? 3 : 4;
                kindTried[kk]++;
                long c0 = System.nanoTime();
                String fix = old ? d.correct(typed, 140) : ks.correct(typed, prev, KoreanSuggest.FIELD_NORMAL, null, null);
                correctNs += System.nanoTime() - c0;
                if (core.equals(fix)) {
                    fixed++;
                    kindFixed[kk]++;
                } else if (fix != null) {
                    wrong++;
                }
                // 고치지 못했어도 다 친 뒤의 추천에 원래 단어가 보이면 눌러서 고칠 수 있다
                if (!old && indexOf(ks.suggest(typed, prev, KoreanSuggest.FIELD_NORMAL, null, null, fix, WordSuggester.MAX).words, core) >= 0) {
                    inBar++;
                }
                prev = core;
            }
        }
        System.out.printf("오타 %d개: 바르게 고침 %.1f%%, 다른 단어로 고침 %.1f%%%n", tried,
                100.0 * fixed / Math.max(1, tried), 100.0 * wrong / Math.max(1, tried));
        if (!old) System.out.printf("오타: 다 친 뒤 추천에 원래 단어가 보임 %.1f%%%n", 100.0 * inBar / Math.max(1, tried));
        String[] kindName = {"옆 키", "같은 키 다른 동작", "빠뜨리기", "더 치기", "맞바꾸기"};
        StringBuilder kinds = new StringBuilder("오타 종류별 바르게 고침:");
        for (int i = 0; i < 5; i++) {
            kinds.append(String.format(" %s %.1f%%(%d)", kindName[i], 100.0 * kindFixed[i] / Math.max(1, kindTried[i]), kindTried[i]));
        }
        System.out.println(kinds);
        System.out.printf("자동 수정 한 번에 평균 %.3f ms%n", correctNs / 1e6 / Math.max(1, tried));
    }

    private static char[] typo(char[] k, int kind, Random rnd) {
        StringBuilder s = new StringBuilder(new String(k));
        int i = rnd.nextInt(k.length);
        char c = k[i];
        if (kind < 4) {   // 옆 키
            char n = neighbor(c, rnd);
            if (n != 0) s.setCharAt(i, n);
        } else if (kind < 6) {   // 같은 키의 다른 동작 (자판마다 다르다)
            for (String g : sameKey) {
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

    /** 화면에서 바로 옆(같은 줄 양옆, 위·아래 줄 같은 칸)에 있는 글자 키 하나. 글자가 아닌 칸(빈 문자열)은 건너뛴다. */
    private static char neighbor(char c, Random rnd) {
        for (int r = 0; r < rows.length; r++) {
            for (int col = 0; col < rows[r].length; col++) {
                if (rows[r][col].isEmpty() || rows[r][col].charAt(0) != c) continue;
                List<Character> n = new ArrayList<>();
                addKey(n, r, col - 1);
                addKey(n, r, col + 1);
                addKey(n, r - 1, col);
                addKey(n, r + 1, col);
                return n.isEmpty() ? 0 : n.get(rnd.nextInt(n.size()));
            }
        }
        return 0;
    }

    private static void addKey(List<Character> out, int r, int col) {
        if (r < 0 || r >= rows.length || col < 0 || col >= rows[r].length || rows[r][col].isEmpty()) return;
        out.add(rows[r][col].charAt(0));
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
        else if (t == boolean.class) f.setBoolean(null, Boolean.parseBoolean(p[1]));
        else throw new IllegalArgumentException(kv);
    }
}
