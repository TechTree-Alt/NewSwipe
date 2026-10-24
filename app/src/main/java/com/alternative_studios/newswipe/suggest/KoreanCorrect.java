package com.alternative_studios.newswipe.suggest;


/**
 * 한국어 자동 수정: 사전에 없는 말을 오타 비용(자판에서 가까운 키, 같은 키의 다른 동작 ...)과 앞 단어와의 관계로 고친다.
 * 사전에 없지만 한국어 낱말로 그럴듯한 말(활용형·구어체·사전 단어의 앞부분·명사 + 조사)은 고치지 않는다 ({@link #plausible}).
 */
final class KoreanCorrect {
    private final KoreanSuggest ks;
    private final WordDictionary dict;
    private final KoreanMorph morph;
    private final TypoModel typo;

    /** 말이 한국어 낱말처럼 생겼는지 (SHAPE_RULE). */
    private final SyllableModel shape;
    /** 사전에 없지만 맞는 말로 알아볼 말 (없으면 null). 있으면 그만큼 더 드문 단어로도 고친다 (SHAPE_MIN_KNOWN). */
    private volatile KnownWords known;
    /** 사전 단어마다 ln(빈도) + SHAPE_LM·ln P(단어) (미리 재 두어 고칠 단어를 찾을 때 편집 거리를 잴 단어를 빨리 거른다). */
    private final float[] prior;

    KoreanCorrect(KoreanSuggest ks, WordDictionary dict, KoreanMorph morph, TypoModel typo) {
        this.ks = ks;
        this.dict = dict;
        this.morph = morph;
        this.typo = typo;
        this.shape = new SyllableModel(dict);
        prior = new float[dict.size()];
        for (int id = 0; id < prior.length; id++) {
            prior[id] = (float) (Math.log(morph.prob(dict.score(id)) * 1e6) + SHAPE_LM * shape.logProb(dict.word(id)));
        }
        dict.idsByLength(ks.scoreFor(SHAPE_SCAN_FREQ));   // 처음 고칠 때 기다리지 않게 미리 만든다
        particlePrior();                                   // 사전을 읽는 백그라운드에서 미리 잰다
    }

    /**
     * 자동 수정: 고칠 단어의 최소 빈도(100만 어절당; 앞 단어와 이어지는 단어 쌍이 있을 때는 MIN_FREQ_CONTEXT), 1·2위 점수 차.
     * 사전 점수는 사전 크기에 따라 기준이 달라지므로 빈도로 정한다 (드문 단어로 고치면 맞는 말을 잘못 고치기 쉽다).
     */
    static double CORRECT_MIN_FREQ = 200, CORRECT_MIN_FREQ_CONTEXT = 145;

    static double CORRECT_MARGIN = 0.7;

    /**
     * 고칠지를 단어의 빈도 하나(최소 빈도)가 아니라 세 가지를 함께 보고 정한다 (끄면 평가용으로 예전처럼 최소 빈도만 본다).
     * <pre>  ln(빈도) − SHAPE_COST·(오타 비용 − 0.6) + SHAPE_LM·(ln P(고칠 말) − ln P(친 말)) [+ 앞 단어와 이어지면 SHAPE_CONTEXT] ≥ ln(SHAPE_MIN)</pre>
     * 오타 비용이 싼 실수(같은 키의 다른 동작: 사옹하세요 → 사용하세요)는 드문 말로도 고치고, 비싼 편집(빠뜨리기·더 치기: 혼수 →
     * 혼술)은 흔한 말로만 고친다. P는 음절 모형({@link SyllableModel})의 확률로, 친 말이 낯선 음절 이음(자댕)이고 고칠 말이 낱말다울수록
     * 오타일 가능성이 크다. 사전에 없지만 맞는 말(통학, 미역)은 낱말다워서 덜 고친다. 계수는 Chatbot_data·NSMC·SmileStyle dev에서
     * 맞췄다: 최소 빈도 100과 견주어 오타 고침은 비슷하거나 많고 잘못 고침은 3분의 1쯤 적다 (dict/README.md).
     */
    static boolean SHAPE_RULE = true;

    static double SHAPE_COST = 8, SHAPE_LM = 0.2, SHAPE_CONTEXT = 0.32, SHAPE_MIN = 3000;

    /**
     * 맞는 말 목록({@link KnownWords})이 있을 때의 기준. 사전에 없지만 맞는 말(공황, 배탈)을 목록으로 알아보므로 기준을 낮춰
     * 흔한 말의 오타(실다 → 싶다, 자덩입니다 → 자동입니다)를 더 고친다. 명사 + 조사의 명사를 고칠 때도 쓴다.
     */
    static double SHAPE_MIN_KNOWN = 1200;

    /**
     * 맞는 말 목록이 있을 때, 키 하나를 잘못 누른 오타(바로 옆·위·아래 키, 같은 키의 다른 동작, 같은 키 두 번: 오타 비용
     * {@link TypoModel#NEIGHBOR} 이하)로 고치는 단어의 기준. 빠뜨리기·더 치기보다 분명한 오타라 더 낮다 (다끔 → 가끔).
     */
    static double SHAPE_MIN_ONE_KEY = 500;

    /** 틀린 꼴이 분명한 말(끝난 말 + 요: 되니요)의 기준. 맞는 말일 수 없으니 훨씬 낮다. */
    static double SHAPE_MIN_WRONG = 100;

    /** 명사 + 조사에서 명사만 고칠 때의 기준 (자댕입니다 → 자동입니다). */
    static double SHAPE_MIN_NOUN = 3000;

    /**
     * SHAPE_RULE에서 고칠 단어로 보는 가장 드문 빈도 (100만 어절당). 뉴스·웹 말뭉치에서 드물지만 대화에서 흔한 말(글자 6.9,
     * 이야기이다 2.6)도 키 하나 오타의 유일한 후보면 고친다 (SHAPE_MIN_UNIQUE). 미리 잰 값으로 걸러 느려지지 않는다.
     */
    static double SHAPE_SCAN_FREQ = 1;

    /**
     * 후보가 사실상 하나일 때의 기준 (맞는 말 목록이 있을 때만): 키 하나를 잘못 누른 오타(대각선 제외)로 고치는 단어가 다른
     * 모든 후보보다 UNIQUE_MARGIN 이상 앞서면, 드문 단어여도 이 기준만 넘으면 고친다 (굴자 → 글자, 학습니 → 학습이).
     */
    static double SHAPE_MIN_UNIQUE = 200;

    static double UNIQUE_MARGIN = 1.0;

    /**
     * 후보가 사실상 하나이고 세 글자 이상인 말의 가운데(끝 글자가 아닌) 글자 모음 하나만 옆 키로 틀렸을 때의 기준 (키버드 → 키보드,
     * 바이얼린 → 바이올린, 커풀의 → 커플의). 친 말이 낱말답지 않을 때만(음절 모형 UNIQUE_VOWEL_LM 이하) 쓴다. 끝 글자는 어미·조사가
     * 바뀌어 맞는 말이 되기 쉽고(예민함 → 예민한, 비교돼 → 비교도), 두 글자 말은 맞는 구어가 많다(선톡 → 선택).
     */
    static double SHAPE_MIN_UNIQUE_VOWEL = 5;
    static double UNIQUE_VOWEL_LM = -20;

    /**
     * 대각선으로 이웃한 키를 잘못 누른 것은 드물다: 대각선 키 없이(바로 옆·위·아래 키, 그 밖의 편집으로) 설명되는 고칠 단어가
     * 하나라도 있으면 그중에서만 고르고, 대각선 키를 잘못 눌렀다고 봐야 하는 단어는 그런 단어가 없을 때만 고른다
     * (배일 → 매일, 내일이 아니라). 끄면 평가용으로 모두 함께 견준다.
     */
    static boolean DIAGONAL_LAST = true;

    /** 자동 수정에서 허용하는 오타 비용 (세 글자 이하, 네 글자 이상). */
    static float CORRECT_MAX_SHORT = 1.0f, CORRECT_MAX_LONG = 1.6f;

    /** 자동 수정에서 이 글자 수 이하의 말은 자모 한 번의 편집(바꾸기·빠뜨리기·더하기·맞바꾸기)으로 되는 단어로만 고친다. */
    static int CORRECT_ONE_EDIT_SYLL = 3;

    /**
     * 사전에 없는 word를 고칠 단어. 오타 비용(자판에서 가까운 키, 같은 키의 다른 동작 등)과 앞 단어와의 관계를 함께 본다.
     * 비슷하게 그럴듯한 후보가 둘 이상이면 고치지 않는다. 사전 단어(조사가 붙은 것 포함)인지는 부르는 쪽이 이미 보았다.
     */
    /** 맞는 말 목록을 쓴다 (사전과 함께 읽은 뒤, null이면 쓰지 않는다). */
    void setKnown(KnownWords k) {
        known = k;
    }

    /** 고친 단어와 겨룬 다음 후보를 추천란에 보일 만한 차이 (ln). */
    static double ALT_MARGIN = 3;
    /** 마지막 correct()의 다음 후보 (추천란에 보인다, 화면 스레드에서만 쓴다). */
    private String alternative, alternativeFor;

    /** word를 마지막으로 고칠 때 겨룬 다음 후보 (없으면 null). */
    String alternativeFor(String word) {
        return word.equals(alternativeFor) ? alternative : null;
    }

    String correct(String word, String prev, int field, UserDictionary user, UsageHistory usage) {
        alternativeFor = word;
        alternative = null;
        int syll = KoreanMorph.hangulSyllables(word);
        if (syll < 2 || syll != word.length()) return null;
        if (dict.contains(word) || withParticle(word)) return null;
        if (KEEP_PLAUSIBLE && plausible(word)) return null;
        KoreanSuggest.Context cx = ks.context(prev, field, user, usage);
        if (SPELL_RULES) {
            String r = spellFix(word, cx);
            if (r != null) return r;
        }
        if (!SHAPE_RULE) return search(word, word, cx, false, 0, 0, Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY);
        KnownWords k = known;
        if (k != null && k.contains(word)) return null;   // 사전에 없지만 말뭉치에 나온 맞는 말
        // 사전 단어 + 요인데 조사로 받지 않은 말(되니요)은 틀린 꼴이 분명하다
        String[] sp = WordSuggester.splitParticle(word);
        boolean wrong = sp != null && dict.contains(sp[0]);
        double min = wrong ? SHAPE_MIN_WRONG : k != null ? SHAPE_MIN_KNOWN : SHAPE_MIN;
        double oneKey = wrong ? SHAPE_MIN_WRONG : k != null ? Math.min(SHAPE_MIN_KNOWN, SHAPE_MIN_ONE_KEY) : SHAPE_MIN;
        double unique = wrong || k == null ? Double.POSITIVE_INFINITY : Math.log(SHAPE_MIN_UNIQUE);
        String r = search(word, word, cx, false, Math.log(min), Math.log(oneKey), unique, wrong ? Double.POSITIVE_INFINITY : vowelLog(word, k));
        if (r == null && !wrong && PARTICLE_FIX) r = particleFix(word, cx);
        if (r != null || sp == null || wrong || sp[0].length() < 2 || k != null && k.contains(sp[0])) return r;
        // 명사 + 조사에서 명사만 틀린 말: 명사를 고쳐 조사를 다시 붙인다 (자댕입니다 → 자동입니다)
        double nounMin = k != null ? SHAPE_MIN_KNOWN : SHAPE_MIN_NOUN;
        String noun = search(sp[0], sp[0], cx, true, Math.log(nounMin), Math.log(k != null ? Math.min(nounMin, SHAPE_MIN_ONE_KEY) : nounMin),
                unique, vowelLog(sp[0], k));
        if (noun == null) return null;
        String[] back = WordSuggester.splitParticle(noun + sp[1]);   // 받침에 맞는 조사인지 (이/가, 은/는 ...)
        return back != null && back[0].equals(noun) ? noun + sp[1] : null;
    }

    /** endingCandidates: 고친 말이 친 말보다 이만큼(음절 모형의 ln) 더 낱말다워야 한다. */
    static double ENDING_LM_GAIN = 4;

    private static final String CHO = "ㄱㄲㄴㄷㄸㄹㅁㅂㅃㅅㅆㅇㅈㅉㅊㅋㅌㅍㅎ";
    private static final String JUNG = "ㅏㅐㅑㅒㅓㅔㅕㅖㅗㅘㅙㅚㅛㅜㅝㅞㅟㅠㅡㅢㅣ";
    private static final String JONG = "\0ㄱㄲㄳㄴㄵㄶㄷㄹㄺㄻㄼㄽㄾㄿㅀㅁㅂㅄㅅㅆㅇㅈㅊㅋㅌㅍㅎ";

    /**
     * 추천란에만 보일, 끝 글자의 어미를 키 하나(대각선 제외) 잘못 친 말의 바른 꼴 (강력해지깈 → 강력해지긴, 예약하라거 →
     * 예약하라고). 사전에 없어도 용언 어간 + 어미로 그럴듯하고 친 말보다 훨씬 낱말다운 꼴 (많아야 max개). 자동 수정에는 쓰지
     * 않는다: 재 보니 맞는 구어(갈궈, 극혐, 떨어지는게)를 바꾸는 일이 바른 고침보다 많았다. 사용자가 추천란에서 여러 번
     * 고르면 CorrectionMemory가 그다음부터 고친다.
     */
    java.util.List<String> endingCandidates(String word, int max) {
        java.util.List<String> out = new java.util.ArrayList<>();
        int n = word.length();
        if (n < 2 || KoreanMorph.hangulSyllables(word) != n || dict.contains(word) || withParticle(word) || plausible(word)) return out;
        KnownWords k = known;
        if (k != null && k.contains(word)) return out;
        int s = word.charAt(n - 1) - 0xAC00;
        int[] part = {s / 588, (s / 28) % 21, s % 28};
        String[] set = {CHO, JUNG, JONG};
        double lmTyped = shape.logProb(word);
        java.util.List<double[]> rank = new java.util.ArrayList<>();
        for (int slot = 0; slot < 3; slot++) {
            if (slot == 2 && part[2] == 0) continue;   // 받침을 더하는 것은 키 하나를 잘못 누른 것이 아니다
            char a = set[slot].charAt(part[slot]);
            for (int b = slot == 2 ? 1 : 0; b < set[slot].length(); b++) {
                if (b == part[slot] || typo.substituteDirect(a, set[slot].charAt(b)) > TypoModel.NEIGHBOR + 1e-4f) continue;
                int[] q = part.clone();
                q[slot] = b;
                String v = word.substring(0, n - 1) + (char) (0xAC00 + q[0] * 588 + q[1] * 28 + q[2]);
                double gain = shape.logProb(v) - lmTyped;
                if (gain < ENDING_LM_GAIN || !(dict.contains(v) || verbForm(v))) continue;
                out.add(v);
                rank.add(new double[]{gain, out.size() - 1});
            }
        }
        rank.sort((x, y) -> Double.compare(y[0], x[0]));   // 더 낱말다운 꼴부터
        java.util.List<String> best = new java.util.ArrayList<>();
        for (double[] r : rank) if (best.size() < max) best.add(out.get((int) r[1]));
        return best;
    }

    /** 동사에만 붙는 어미 (청유·현재 관형형 등): 형용사 어간에 붙은 말(좋자, 좋는)은 낱말다운 꼴로 보지 않는다. */
    private static final String[] VERB_ONLY_ENDINGS = {"자", "자고", "자며", "는", "는데", "는다", "는지라", "려고", "러"};

    /** 형용사 어간인지: 관형형이 -은이고(좋은) -는은 없다(좋는). 있다·없다처럼 -는을 쓰는 말은 형용사로 보지 않는다. */
    private boolean adjectiveStem(String stem) {
        return dict.contains(stem + "은") && !dict.contains(stem + "는");
    }

    /** 명사 + 조사에서 조사만 틀린 말을 고칠지 ({@link #particleFix}). */
    static boolean PARTICLE_FIX = true;
    /** 명사 뒤에 붙어 조사처럼 보이지만 조사가 아닌 말: 친 말이 이것으로 끝나면 조사를 잘못 친 것으로 보지 않는다. */
    private static final String NOT_PARTICLE_TYPO = "들님씨적상성화일인임쯤별용간째형류짓감값질쪽편층돼되해하한할함된될기대으라여";

    /**
     * 명사 + 조사에서 조사 한 글자의 모음이나 받침만 키 하나(대각선 제외) 잘못 친 말 (생선응 → 생선을, 그림자릉 → 그림자를).
     * 앞부분이 사전의 명사이고, 친 끝 글자가 조사·접미사(들, 님, 의)·용언 조각(돼, 한, 기)·명사(애)가 아니어야 한다.
     * 받침에 맞는 조사가 되는 글자가 여럿이면(응 → 을·은) 명사 + 조사 꼴이 사전에서 두 배 넘게 흔한 쪽을 고르고, 비슷하면
     * 고치지 않는다 (사람응: 사람을·사람은). 초성까지 다르면 다른 말이기 쉬워(인형티, 낚시한) 보지 않는다.
     */
    private String particleFix(String word, KoreanSuggest.Context cx) {
        int n = word.length();
        if (n < 3) return null;
        String noun = word.substring(0, n - 1), last = word.substring(n - 1);
        if (!dict.contains(noun) || !morph.nounLike(noun)) return null;
        if (WordSuggester.isParticle(last) || NOT_PARTICLE_TYPO.indexOf(last.charAt(0)) >= 0
                || dict.contains(last) && morph.nounLike(last)) return null;
        int s = last.charAt(0) - 0xAC00;
        if (s < 0 || s > 11171) return null;
        int[] part = {s / 588, (s / 28) % 21, s % 28};
        String[] set = {CHO, JUNG, JONG};
        java.util.List<String> cands = new java.util.ArrayList<>();
        java.util.List<Double> freqs = new java.util.ArrayList<>();
        for (int slot = 1; slot < 3; slot++) {   // 중성·받침만 (초성이 같아야 한다)
            char a = set[slot].charAt(part[slot]);
            for (int b = 0; b < set[slot].length(); b++) {
                // 받침을 빼거나 더하는 것은 키 하나를 잘못 누른 것이 아니다
                if (b == part[slot] || slot == 2 && (part[2] == 0 || b == 0)) continue;
                if (typo.substituteDirect(a, set[slot].charAt(b)) > TypoModel.NEIGHBOR + 1e-4f) continue;
                int[] q = part.clone();
                q[slot] = b;
                String p = String.valueOf((char) (0xAC00 + q[0] * 588 + q[1] * 28 + q[2]));
                String[] back = WordSuggester.splitParticle(noun + p);   // 받침에 맞는 조사인지
                if (back == null || !back[0].equals(noun) || !back[1].equals(p)) continue;
                int id = dict.indexOf(noun + p);
                cands.add(p);
                freqs.add(id >= 0 ? morph.prob(dict.score(id)) : 0);   // 명사 + 조사 꼴의 빈도 (사전에 없으면 0)
            }
        }
        if (cands.isEmpty()) return null;
        if (cands.size() == 1) return noun + cands.get(0);
        // 둘 이상이면 앞 단어 다음에 그 조사 종류가 얼마나 오는지(전체 비율에 견준 배수)를 곱한다 (dev에서 바른 고름 +1%p, 틀린 고름 그대로)
        double[] after = new double[KoreanSuggest.PARTICLE_CLASS_COUNT];
        ks.particleClassesAfter(cx, after);
        double total = 0;
        for (double v : after) total += v;
        double[] prior = particlePrior();
        String found = null;
        double best = 0, second = 0;
        for (int i = 0; i < cands.size(); i++) {
            int k = KoreanSuggest.particleClass(cands.get(i));
            double f = freqs.get(i);
            if (total > 0 && k >= 0 && prior[k] > 0) f *= (after[k] + 0.5 * prior[k]) / (total + 0.5) / prior[k];
            if (found == null || f > best) {
                second = Math.max(second, best);
                best = f;
                found = cands.get(i);
            } else {
                second = Math.max(second, f);
            }
        }
        if (best == 0 || best < 2 * second) return null;   // 뚜렷이 그럴듯한 꼴이 없다
        return noun + found;
    }

    private volatile double[] particlePrior;

    /** 사전에서 명사 + 한 글자 조사 꼴의 조사 종류별 비율 (처음 쓸 때 한 번 잰다). */
    private double[] particlePrior() {
        double[] p = particlePrior;
        if (p != null) return p;
        p = new double[KoreanSuggest.PARTICLE_CLASS_COUNT];
        double sum = 0;
        for (int id = 0; id < dict.size(); id++) {
            char last = dict.lastChar(id);
            if (KoreanSuggest.PARTICLE_CLASSES.indexOf(last) < 0 || dict.length(id) < 2) continue;
            String[] sp = WordSuggester.splitParticle(dict.word(id));
            if (sp == null || sp[1].length() != 1) continue;
            double f = morph.prob(dict.score(id));
            p[KoreanSuggest.particleClass(sp[1])] += f;
            sum += f;
        }
        if (sum > 0) for (int k = 0; k < p.length; k++) p[k] /= sum;
        particlePrior = p;
        return p;
    }

    /** word에 SHAPE_MIN_UNIQUE_VOWEL을 쓸 수 있으면 그 ln, 아니면 +∞. */
    private double vowelLog(String word, KnownWords k) {
        if (k == null || word.length() < 3 || shape.logProb(word) > UNIQUE_VOWEL_LM) return Double.POSITIVE_INFINITY;
        return Math.log(SHAPE_MIN_UNIQUE_VOWEL);
    }

    /** 용언의 어간을 잘못 친 말은 고칠 말의 활용형 전체 빈도로 본다 ({@link #stemFamilyGain}). */
    static boolean STEM_FAMILY = true;

    /**
     * 친 말과 후보 w가 끝의 몇 글자(어미 쪽)는 같고 앞부분만 다를 때, 친 말의 앞부분으로 시작하는 말이 사전에 하나도 없으면
     * (궁근하네: 궁근…) 앞부분을 잘못 친 것이 분명하므로, w 하나의 빈도 대신 w의 앞부분으로 시작하는 말 전체의 빈도(궁금하네 대신
     * 궁금해·궁금한·궁금하다 …)로 본다. 그만큼의 ln 배수를 돌려준다 (해당하지 않으면 0). cache는 끝 글자 수마다의 결과.
     */
    private double stemFamilyGain(String word, String w, int id, int[] cache) {
        int n = word.length(), m = w.length(), k = 0;
        while (k < Math.min(n, m) - 1 && word.charAt(n - 1 - k) == w.charAt(m - 1 - k)) k++;
        if (k == 0 || n - k < 2) return 0;
        // 같은 끝이 조사면 명사를 잘못 친 것이라 다른 드문 명사(지망에서, 진담을)일 수 있다: 용언의 어간만 본다
        String[] sp = WordSuggester.splitParticle(w);
        if (sp != null && sp[1].length() >= k) return 0;
        if (cache[k] == 0) {
            int[] r = dict.prefixRange(word.substring(0, n - k));
            cache[k] = r[0] >= r[1] ? 1 : 2;
        }
        if (cache[k] != 1) return 0;
        String stem = w.substring(0, m - k);
        int sid = dict.indexOf(stem);
        double fam = prefixFreq(stem) + (sid >= 0 ? morph.prob(dict.score(sid)) : 0), f = morph.prob(dict.score(id));
        return fam > f ? Math.log(fam / f) : 0;
    }

    private static final int[] NO_IDS = new int[0];

    private static int[] lengthList(int[][] byLen, int len) {
        return len >= 1 && len < byLen.length ? byLen[len] : NO_IDS;
    }

    /** 자모열 p(개수 have)와 t에서 짝이 없는 자모의 수 (left는 계산용). 편집 한 번은 이 수를 많아야 둘 줄인다. */
    static int jamoDiff(int[] have, int[] left, int pn, char[] t, int tn) {
        System.arraycopy(have, 0, left, 0, have.length);
        int diff = pn + tn;
        for (int j = 0; j < tn; j++) {
            int x = t[j] - 0x3131;
            if (x >= 0 && x < left.length && left[x] > 0) {
                left[x]--;
                diff -= 2;
            }
        }
        return diff;
    }

    /** typed와 w가 끝 글자가 아닌 한 글자의 모음만 다른지 (초성·받침은 같다). */
    static boolean middleVowelEdit(String typed, String w) {
        int n = typed.length();
        if (w.length() != n) return false;
        int at = -1;
        for (int i = 0; i < n; i++) {
            if (typed.charAt(i) == w.charAt(i)) continue;
            if (at >= 0) return false;
            at = i;
        }
        if (at < 0 || at == n - 1) return false;
        int a = typed.charAt(at) - 0xAC00, b = w.charAt(at) - 0xAC00;
        if (a < 0 || a > 11171 || b < 0 || b > 11171) return false;
        return a / 588 == b / 588 && a % 28 == b % 28;
    }

    /**
     * 사전에 없는 word를 고칠 사전 단어 (없으면 null). noun이면 명사만 본다. SHAPE_RULE이면 minLog = ln(기준)이고 키 하나를
     * 잘못 누른 오타로 고치는 단어는 oneKeyLog를 쓴다. 아니면 예전처럼 최소 빈도(CORRECT_MIN_FREQ)를 넘는 단어 중에서 고른다.
     * 비슷하게 그럴듯한 후보가 둘 이상이면 고치지 않는다. 기준을 넘는 단어가 없어도, 키 하나 오타(대각선 제외)로 고치는 단어가
     * 다른 모든 후보보다 UNIQUE_MARGIN 이상 앞서고 uniqueLog를 넘으면 그 단어로 고친다.
     */
    private String search(String word, String typed, KoreanSuggest.Context cx, boolean noun, double minLog, double oneKeyLog,
                          double uniqueLog, double vowelLog) {
        int syll = word.length();
        char[] p = TypoModel.letters(word);
        float maxCost = syll <= 3 ? CORRECT_MAX_SHORT : CORRECT_MAX_LONG;
        char[] t = new char[72];
        // [0] 대각선 키 없이 설명되는 단어, [1] 대각선 키를 잘못 눌렀다고 봐야 하는 단어
        double[] best = {Double.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY};
        double[] second = {Double.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY};
        int[] bestId = {-1, -1};
        float diagCost = 0;                                   // [1]의 가장 그럴듯한 단어의 오타 비용
        float minDirect = Float.POSITIVE_INFINITY;            // 기준과 상관없이 본, 대각선 키 없이 설명되는 단어의 가장 싼 비용
        double allBest = Double.NEGATIVE_INFINITY, allSecond = Double.NEGATIVE_INFINITY;   // 기준과 상관없이 본, 대각선 키 없이 설명되는 단어의 1·2위
        int allBestId = -1, allSecondId = -1, uniqueId = -1;
        double uniqueS = Double.NEGATIVE_INFINITY;   // 키 하나 오타(대각선 제외)로 고치는 단어 중 가장 그럴듯한 것
        int minScore = ks.scoreFor(CORRECT_MIN_FREQ);
        double lmTyped = SHAPE_RULE ? shape.logProb(typed) : 0;
        double scanLog = Math.min(Math.min(minLog, oneKeyLog), Math.min(uniqueLog, vowelLog));
        // 자모 개수 차이로 편집 거리의 아래 한계를 먼저 본다 (가장 싼 바꾸기도 VARIANT라 다른 자모 둘마다 그 이상 든다)
        int[] have = new int[51], left = new int[51];
        for (char ch : p) if (ch >= 0x3131 && ch < 0x3131 + 51) have[ch - 0x3131]++;
        int maxDiff = (int) Math.floor(maxCost / (TypoModel.VARIANT / 2)) + 1;   // 이만큼 다르면 maxCost를 넘는다
        long pMask = TypoModel.jamoMask(p, p.length);
        KoreanSuggest.Context.PairScan pairs = cx.pairScan();
        int[] noStem = new int[syll + 1];   // stemFamilyGain: 끝에서 k글자를 뗀 앞부분으로 시작하는 사전 단어가 없으면 1, 있으면 2 (0 = 아직)
        // 점수가 기준 이상이고 글자 수가 ±1인 단어를 사전 순서대로 본다 (글자 수별 목록 셋을 번호 순으로 합친다)
        int[][] byLen = dict.idsByLength(ks.scoreFor(SHAPE_RULE ? SHAPE_SCAN_FREQ : CORRECT_MIN_FREQ_CONTEXT));
        int[] la = lengthList(byLen, syll - 1), lb = lengthList(byLen, syll), lc = lengthList(byLen, syll + 1);
        int ia = 0, ib = 0, ic = 0;
        while (true) {
            int va = ia < la.length ? la[ia] : Integer.MAX_VALUE, vb = ib < lb.length ? lb[ib] : Integer.MAX_VALUE,
                    vc = ic < lc.length ? lc[ic] : Integer.MAX_VALUE;
            int id = Math.min(va, Math.min(vb, vc));
            if (id == Integer.MAX_VALUE) break;
            if (id == va) ia++;
            else if (id == vb) ib++;
            else ic++;
            int len = dict.length(id);
            if (len < syll - 1 || len > syll + 1 || len > 24) continue;
            double partial = 0;
            if (SHAPE_RULE) {
                // 오타 비용을 뺀 부분을 먼저 본다: 가장 싼 오타(같은 키의 다른 동작)여도 기준에 못 미치면 편집 거리를 재지 않는다
                partial = prior[id] - SHAPE_LM * lmTyped;
                if (partial + SHAPE_CONTEXT - SHAPE_COST * (TypoModel.VARIANT - 0.6) < scanLog) continue;
                if (pairs.has(id)) partial += SHAPE_CONTEXT;
            }
            // 풀기 전에 음절 표로 어림해 거른다 (대부분의 후보가 여기서 빠진다)
            int bound = dict.jamoDiffBound(id, pMask, p.length);
            if (bound >= maxDiff) continue;
            int tn = dict.letters(id, t);
            if (Math.abs(tn - p.length) > 2 || jamoDiff(have, left, p.length, t, tn) >= maxDiff) continue;
            float c = typo.distance(p, p.length, t, tn, maxCost);
            if (c > maxCost || c <= 0f) continue;
            String w = dict.word(id);
            // 짧은 말은 싼 편집 두 번(현타 → 한다, 파도 → 봐도)으로도 다른 흔한 말이 되기 쉬워 한 번의 편집만 고친다
            if (syll <= CORRECT_ONE_EDIT_SYLL && !oneEdit(p, p.length, t, tn)) continue;
            // 드문 명사(키보드)는 조사가 붙은 꼴이 사전에 없어 명사로 보이지 않는다: 가운데 모음 하나만 틀린 경우는 그대로 본다
            if (noun && !morph.nounLike(w) && !(vowelLog < Double.POSITIVE_INFINITY && middleVowelEdit(word, w))) continue;
            boolean context = pairs.has(id);
            boolean oneKey = c <= TypoModel.NEIGHBOR + 1e-4f;
            int pool = DIAGONAL_LAST && typo.distanceDirect(p, p.length, t, tn, maxCost) > c + 1e-4f ? 1 : 0;
            double s = Math.log(cx.score(w, id)) - KoreanSuggest.TYPO_PENALTY * c;
            if (pool == 0) {
                minDirect = Math.min(minDirect, c);
                if (s > allBest) {
                    allSecond = allBest;
                    allSecondId = allBestId;
                    allBest = s;
                    allBestId = id;
                } else if (s > allSecond) {
                    allSecond = s;
                    allSecondId = id;
                }
            }
            if (SHAPE_RULE) {
                double v = partial - SHAPE_COST * (c - 0.6) + (STEM_FAMILY && oneKey ? stemFamilyGain(word, w, id, noStem) : 0);
                double uLog = vowelLog < uniqueLog && middleVowelEdit(word, w) ? vowelLog : uniqueLog;
                if (oneKey && pool == 0 && v >= uLog && s > uniqueS) {
                    uniqueS = s;
                    uniqueId = id;
                }
                if (v < (oneKey ? oneKeyLog : minLog)) continue;
            } else if (dict.score(id) < minScore && !context) {
                continue;
            }
            if (s > best[pool]) {
                second[pool] = best[pool];
                best[pool] = s;
                bestId[pool] = id;
                if (pool == 1) diagCost = c;
            } else if (s > second[pool]) {
                second[pool] = s;
            }
        }
        if (bestId[0] >= 0) {
            if (best[0] - second[0] < CORRECT_MARGIN) return null;
            // 고친 단어와 겨룬 다음 후보(기준과 상관없이)가 그리 뒤지지 않으면 추천란에도 보인다 (좋자 → 좋다, 그다음 좋지)
            int alt = allBestId == bestId[0] ? allSecondId : allBestId;
            double altS = allBestId == bestId[0] ? allSecond : allBest;
            if (alt >= 0 && alt != bestId[0] && best[0] - altS < ALT_MARGIN) alternative = dict.word(alt);
            return dict.word(bestId[0]);
        }
        // 대각선 키를 잘못 눌렀다고 봐야 하는 단어는, 같은 값싼 오타로 설명되는 다른 꼴(바로 옆·위·아래 키)이 없을 때만 고른다
        // (배일 → 내일이 아니다: 매일은 기준에 조금 못 미쳐도 있는 꼴이다)
        if (bestId[1] >= 0 && minDirect > diagCost + 1e-4f) {
            return best[1] - second[1] < CORRECT_MARGIN ? null : dict.word(bestId[1]);
        }
        // 후보가 사실상 하나: 키 하나 오타로 고치는 단어가 (대각선 키를 뺀) 다른 모든 후보보다 뚜렷이 앞선다
        if (uniqueId >= 0 && uniqueId == allBestId && allBest - allSecond >= UNIQUE_MARGIN) return dict.word(uniqueId);
        return null;
    }

    /**
     * 맞춤법 오타 규칙으로 고친다 (끄면 평가용으로 자판 오타만 본다). 소리가 같아 받침·모음을 잘못 적은 말은 자판 거리로는 흔한
     * 말에만 닿고(고칠 단어의 최소 빈도), 드문 활용형(재밋네요 → 재밌네요)은 놓친다. 규칙은 open-korean-text의 typos.txt와
     * Kiwi의 typo.dict·basicTypoSet(둘 다 Apache License 2.0)에서 실제 글에서 틀리는 것만 골랐다. 결과가 사전 단어(조사가 붙은
     * 것 포함)일 때만 고치므로, 사전에 있는 말(왠지, 잇다)은 건드리지 않는다.
     */
    static boolean SPELL_RULES = true;

    /** 통째로 바꾸는 말 {틀린 꼴, 바른 꼴} (어절 안 어디에 있어도). */
    private static final String[][] SPELL_WHOLE = {{"어자피", "어차피"}, {"댓가", "대가"}, {"오랫만", "오랜만"}};

    /** 되 + 아/어 꼴로 줄어야 할 뒷말 (되요 → 돼요, 되서 → 돼서, 되버린 → 돼버린, 되야 → 돼야). */
    private static final String DWAE_NEXT = "요서버야";

    /**
     * word를 맞춤법 오타 규칙으로 고친 사전 단어. 규칙을 두 군데까지 적용한다 (잇엇다 → 있었다, 힘들자나 → 힘들잖아). 소리대로
     * 적은 말의 규칙({@link #soundEdits})을 하나라도 썼으면 조사가 붙은 말은 받지 않는다 (찾아오네 → 찾아온+에, 바늘 → 반+을).
     * 없으면 null.
     */
    private String spellFix(String word, KoreanSuggest.Context cx) {
        String best = null;
        double bestS = 0;
        java.util.List<String> sound1 = new java.util.ArrayList<>(), sound2 = new java.util.ArrayList<>();
        java.util.List<String> edits = spellEdits(word, sound1);
        for (String a : edits) {
            boolean s1 = sound1.contains(a);
            String fix = known(a, s1) ? a : null;
            if (fix == null) {
                sound2.clear();
                for (String b : spellEdits(a, sound2)) {
                    if (known(b, s1 || sound2.contains(b))) {
                        fix = b;
                        break;
                    }
                }
            }
            if (fix == null) continue;
            int id = dict.indexOf(fix);
            double s = id >= 0 ? cx.score(fix, id) : 1e-12;   // 조사가 붙은 말은 사전 단어보다 뒤로
            if (best == null || s > bestS) {
                best = fix;
                bestS = s;
            }
        }
        return best;
    }

    private boolean known(String w, boolean sound) {
        return dict.contains(w) || !sound && withParticle(w);
    }

    /**
     * 사전 단어 + 조사인지. 끝난 말(되니, 하냐, 먹다) 뒤의 '요'는 명사 뒤가 아니면 틀린 말이라 넣지 않는다
     * (되니요 X → 되나요로 고칠 수 있다, 언니요·바다요 O).
     */
    private boolean withParticle(String w) {
        String[] sp = WordSuggester.splitParticle(w);
        if (sp == null || !dict.contains(sp[0])) return false;
        String stem = sp[0];
        if (modifierForm(stem)) return false;   // 도운 + 이: 용언의 관형형(도운·먹은·하는)에는 조사가 붙지 않는다
        return !sp[1].equals("요") || "니냐다".indexOf(stem.charAt(stem.length() - 1)) < 0 || morph.nounLike(stem);
    }

    /**
     * 사전에 있는 w가 명사가 아니라 용언의 관형형(도운, 먹은, 하는, 갈)인지: 끝 글자의 받침 ㄴ·ㄹ을 떼거나 '은·는·던'을 뗀
     * 어간에 다른 어미가 붙은 꼴(도우면, 먹고, 하고)이 사전에 있고, 명사로 쓰인 흔적이 없을 때.
     */
    private boolean modifierForm(String w) {
        int n = w.length();
        char c = w.charAt(n - 1);
        if (n < 2 || c < 0xAC00 || c > 0xD7A3 || morph.nounLike(w)) return false;   // 한 글자 말(알, 산)은 명사가 많다
        int t = (c - 0xAC00) % 28;
        if (t == 4 || t == 8) {   // ㄴ·ㄹ 받침
            String s0 = w.substring(0, n - 1) + (char) (c - t);
            if (dict.contains(s0 + "면") || dict.contains(s0 + "니까")) return true;
        }
        if (n >= 2 && "은는던".indexOf(c) >= 0 && dict.contains(w.substring(0, n - 1) + "고")) return true;
        return false;
    }

    /** 규칙 하나를 한 자리에 적용한 꼴들. 소리대로 적은 말의 규칙으로 만든 꼴은 sound에도 넣는다. */
    static java.util.List<String> spellEdits(String w, java.util.List<String> sound) {
        java.util.List<String> out = new java.util.ArrayList<>();
        for (String[] r : SPELL_WHOLE) {
            int at = w.indexOf(r[0]);
            if (at >= 0) out.add(w.substring(0, at) + r[1] + w.substring(at + r[0].length()));
        }
        for (int i = 0; i < w.length(); i++) {
            char c = w.charAt(i);
            if (c < 0xAC00 || c > 0xD7A3) continue;
            int jong = (c - 0xAC00) % 28;
            char to = 0;
            if (jong == 19) to = (char) (c + 1);   // ㅅ 받침 → ㅆ: 햇다 → 했다, 봣는데 → 봤는데, 겟 → 겠, 잇지만 → 있지만
            else if (c == '찬') to = '찮';   // 괜찬 → 괜찮, 귀찬 → 귀찮
            else if (c == '왠') to = '웬';   // 왠만하면 → 웬만하면 (왠지는 사전에 있다)
            else if (c == '읍') to = '습';   // 있읍니다 → 있습니다
            else if (c == '됬') to = '됐';
            else if (c == '됀' || c == '됄' || c == '됌' || c == '됍') to = (char) (c + 28);   // 됀다 → 된다, 됌 → 됨
            else if (c == '되' && i + 1 < w.length() && DWAE_NEXT.indexOf(w.charAt(i + 1)) >= 0) to = '돼';
            if (to != 0) out.add(w.substring(0, i) + to + w.substring(i + 1));
            if (SOUND_RULES) soundEdits(w, i, c, jong, sound);
        }
        out.addAll(sound);
        return out;
    }

    /**
     * 소리 나는 대로 적은 말을 되돌리는 규칙 (끄면 평가용). 감니다 → 갑니다, 그럴가요 → 그럴까요, 되네오 → 되네요,
     * 드러오세요 → 들어오세요, 조아 → 좋아. 자판 거리로 고칠 때의 최소 빈도보다 드문 활용형도 고친다.
     */
    static boolean SOUND_RULES = true;

    /** 받침으로 옮길 수 있는 첫소리(ㄱㄴㄷㄹㅁㅂㅅㅆㅈㅊㅌㅍ): 초성 번호 → 종성 번호 (0이면 옮기지 않는다). */
    private static final int[] CHO_TO_JONG = {1, 0, 4, 7, 0, 8, 16, 17, 0, 19, 20, 0, 22, 0, 23, 0, 25, 26, 0};

    private static void soundEdits(String w, int i, char c, int jong, java.util.List<String> out) {
        char next = i + 1 < w.length() ? w.charAt(i + 1) : 0;
        // ㅂ니다·ㅂ시다를 소리대로 ㅁ으로 적은 말: 감니다 → 갑니다, 드림니다 → 드립니다, 그럼시다 → 그럽시다
        if (jong == 16 && (next == '니' || next == '시')) out.add(w.substring(0, i) + (char) (c + 1) + w.substring(i + 1));
        // ㄹ 받침 뒤의 '까'를 '가'로 적은 말: 그럴가요 → 그럴까요
        if (jong == 8 && next == '가') out.add(w.substring(0, i + 1) + '까' + w.substring(i + 2));
        // 끝의 '요'를 '오'로 적은 말: 되네오 → 되네요, 하세오 → 하세요
        if (c == '오' && i > 0 && next == 0) out.add(w.substring(0, i) + '요');
        if (next < 0xAC00 || next > 0xD7A3) return;
        int nextCho = (next - 0xAC00) / 588;
        // 받침을 다음 글자의 첫소리로 넘겨 적은 말 (연음): 드러오세요 → 들어오세요, 머거서 → 먹어서. 두 글자 말은 사전의
        // 명사 + 조사와 겹치기 쉽고(바늘 → 반을, 카레 → 칼에), 어미 '-서'는 '-ㅆ어'와 겹쳐(힘내서 → 힘냈어) 옮기지 않는다.
        if (jong == 0 && CHO_TO_JONG[nextCho] > 0 && w.length() >= 3 && next != '서') {
            out.add(w.substring(0, i) + (char) (c + CHO_TO_JONG[nextCho]) + (char) (next + (11 - nextCho) * 588) + w.substring(i + 2));
        }
        // 소리 나지 않는 ㅎ 받침을 빠뜨린 말: 조아 → 좋아, 조으면 → 좋으면, 괜차나 → 괜찮아 (연음과 함께)
        if (nextCho == 11) {
            int to = jong == 0 ? 27 : jong == 4 ? 6 : jong == 8 ? 15 : 0;
            if (to > 0) out.add(w.substring(0, i) + (char) (c - jong + to) + w.substring(i + 1));
        }
    }

    /**
     * 자동 수정 전에 사전에 없는 말이 한국어 낱말로 그럴듯한지 본다. 사람이 쓴 대화에서 잘못 고친 말은 대부분 오타가 아니라
     * 사전에 없는 활용형·구어체(같애, 간대, 있대, 망했다, 고민이야)나 사전 단어의 앞부분인 명사(화장 → 화장실·화장품)였다.
     * 끄면(평가용) 예전처럼 사전에 없는 말을 모두 고칠 후보로 본다.
     */
    static boolean KEEP_PLAUSIBLE = true;

    /** 사전 단어의 앞부분으로 볼 최소 빈도 (그 말로 시작하는 더 긴 사전 단어들의 100만 어절당 빈도 합). */
    static double PLAUSIBLE_PREFIX_FREQ = 3;

    /** 용언 어간 바로 뒤에 붙는 어미 (구어체 포함). 받침에 섞여 들어가는 어미(간대의 ㄴ대)는 MERGED_ENDINGS. */
    private static final String[] FREE_ENDINGS = {
            "고", "지", "게", "네", "니", "냐", "다", "대", "래", "자", "죠", "지만", "기", "던", "더라", "든", "든지",
            "음", "어", "아", "애", "여", "어요", "아요", "어서", "아서", "어도", "아도", "어야", "아야", "러", "려고", "려",
            "면", "며", "으면", "으러", "으려고", "은", "을", "는", "는데", "은데", "을까", "을게", "을래", "잖아", "거든",
            "구나", "군", "겠다", "겠어", "겠지", "겠네", "네요", "지요", "대요", "냐고", "자고", "다고", "라고", "더니",
            "징", "당", "넹", "쥐", "셈", "듯", "음요", "는지라", "긴", "기는", "기도", "라고", "라며", "라는", "라면",
            "다니", "다며", "다면", "다는", "냐며", "자며", "거든요", "더라고", "더라고요", "잖아요",
    };

    /** 어간의 마지막 글자에 받침으로 들어가는 어미 첫소리와 그 뒤 (가+ㄴ대 = 간대, 가+ㄹ게 = 갈게, 되+ㅁ = 됨, 가+ㅂ니다). */
    private static final String[][] MERGED_ENDINGS = {
            {"ㄴ", "", "대", "데", "다", "지", "가", "거", "건", "걸", "게", "듯", "줄", "다고", "대요", "데요", "가요", "지도", "지라"},
            {"ㄹ", "", "게", "래", "까", "걸", "거", "건", "듯", "수", "때", "지", "께", "껄", "까요", "게요", "래요", "거야",
                    "꺼", "꺼야", "꺼에요", "거에요", "꺼라", "꺼니까"},
            {"ㅁ", ""},
            {"ㅂ", "니다", "니까", "시다"},
    };

    /** 사전에 없는 word가 한국어 낱말로 그럴듯한지 (사전 단어의 앞부분, 용언 어간 + 어미, 명사 + 조사·서술격). */
    boolean plausible(String word) {
        int n = word.length();
        if (prefixFreq(word) * 1e6 >= PLAUSIBLE_PREFIX_FREQ) return true;
        String[] sp = WordSuggester.splitParticle(word);   // 명사 + 조사·서술격 (고민이야, 사람인듯, 친구예요)
        if (sp != null && dict.contains(sp[0]) && morph.nounLike(sp[0])) return true;
        return verbForm(word);
    }

    /** word가 용언 어간 + 어미로 그럴듯한지 (plausible에서 명사 + 조사와 사전 단어의 앞부분을 뺀 것). */
    boolean verbForm(String word) {
        int n = word.length();
        for (int k = n - 1; k >= 1; k--) {
            String end = word.substring(k), stem = word.substring(0, k);
            if (contains(FREE_ENDINGS, end) && morph.verbStem(stem) && !(contains(VERB_ONLY_ENDINGS, end) && adjectiveStem(stem))) return true;
        }
        if (eoForm(word)) return true;
        // 받침에 어미가 섞인 말: 마지막 몇 글자 중 한 글자의 받침을 떼어 어간으로 본다 (간대 → 가 + ㄴ대)
        for (int k = n; k >= Math.max(1, n - 2); k--) {
            char c = word.charAt(k - 1);
            int t = (c - 0xAC00) % 28;
            String rest = word.substring(k);
            for (String[] m : MERGED_ENDINGS) {
                if (t != finalIndex(m[0].charAt(0))) continue;
                String stem = word.substring(0, k - 1) + (char) (c - t);
                for (int i = 1; i < m.length; i++) {
                    if (m[i].equals(rest) && (morph.verbStem(stem) || copulaNoun(stem))) return true;
                }
            }
            // 과거형(받침 ㅆ) 뒤의 어미: 망했다 → 망하, 떨어졌대 → 떨어지
            if (t == 20 && (rest.isEmpty() || contains(FREE_ENDINGS, rest) || rest.equals("어") || rest.equals("음"))) {
                for (String stem : KoreanMorph.presentStems(word.substring(0, k - 1), c)) {
                    if (morph.verbStem(stem) || morph.haStem(stem)) return true;
                }
            }
        }
        return false;
    }

    /** 아/어 꼴 뒤에 오는 말 (EO_ENDINGS와 함께 본다): 힘들어져, 보내줘야, 행복해라, 먹어봐. */
    private static final String[] EO_TAIL_EXTRA = {"", "라", "져", "졌어", "지", "야겠네", "야지", "버려", "버렸네", "보고", "봐도", "봤는데"};

    /**
     * 용언의 아/어 꼴(먹어, 줄인 꼴 져·줘·봐·해·돼 ...) 뒤에 흔한 말이 붙은 말인지. 마지막 세 글자 안에서 아/어 꼴인 글자를 찾아
     * 어간을 되살리고(져 → 지, 줘 → 주, 봐 → 보, 해 → 하, 돼 → 되) 그 어간이 사전의 용언이어야 한다.
     */
    private boolean eoForm(String word) {
        int n = word.length();
        for (int k = n; k >= Math.max(2, n - 2); k--) {
            String rest = word.substring(k);
            if (!contains(KoreanForms.EO_ENDINGS, rest) && !contains(EO_TAIL_EXTRA, rest)) continue;
            char c = word.charAt(k - 1);
            if (c < 0xAC00 || c > 0xD7A3 || (c - 0xAC00) % 28 != 0) continue;
            int cho = (c - 0xAC00) / 588, jung = (c - 0xAC00) % 588 / 28;
            String head = word.substring(0, k - 1);
            // 줄이지 않은 꼴: 먹 + 어, 잡 + 아, 하 + 여
            if (cho == 11 && (jung == 0 || jung == 4 || jung == 6) && morph.verbStem(head)) return true;
            // 줄인 꼴: ㅕ ← ㅣ, ㅝ ← ㅜ, ㅘ ← ㅗ, ㅙ ← ㅚ, 해 ← 하
            int stemJung = jung == 6 ? 20 : jung == 14 ? 13 : jung == 9 ? 8 : jung == 10 ? 11 : -1;
            if (c == '해') {
                if (morph.haStem(head + '하')) return true;
                continue;
            }
            if (stemJung < 0) continue;
            String stem = head + (char) (0xAC00 + cho * 588 + stemJung * 28);
            if (morph.verbStem(stem)) return true;
        }
        return false;
    }

    /** word로 시작하는 더 긴 사전 단어들의 빈도 합 (단어 확률). 많아야 앞의 200개만 센다. */
    private double prefixFreq(String word) {
        int[] r = dict.prefixRange(word);
        double sum = 0;
        for (int i = r[0], seen = 0; i < r[1] && seen < 200; i++, seen++) {
            if (dict.length(i) > word.length()) sum += morph.prob(dict.score(i));
        }
        return sum;
    }

    /** 명사 + 이다의 어간 '○○이' (받침 없는 명사는 ○○): 고민이 → 고민, 친구 → 친구. */
    private boolean copulaNoun(String stem) {
        String noun = stem.endsWith("이") && stem.length() >= 2 ? stem.substring(0, stem.length() - 1) : stem;
        int id = dict.indexOf(noun);
        return id >= 0 && noun.length() >= 2 && morph.nounLike(noun);
    }

    /** 자모열 a와 b가 정확히 한 번의 편집(바꾸기·빠뜨리기·더하기·맞바꾸기)으로 같아지는지. */
    static boolean oneEdit(char[] a, int an, char[] b, int bn) {
        if (Math.abs(an - bn) > 1) return false;
        int p = 0, min = Math.min(an, bn);
        while (p < min && a[p] == b[p]) p++;
        if (p == an && p == bn) return false;
        if (an == bn) {
            if (sameTail(a, p + 1, an, b, p + 1, bn)) return true;
            return p + 1 < an && a[p] == b[p + 1] && a[p + 1] == b[p] && sameTail(a, p + 2, an, b, p + 2, bn);
        }
        return an > bn ? sameTail(a, p + 1, an, b, p, bn) : sameTail(a, p, an, b, p + 1, bn);
    }

    private static boolean sameTail(char[] a, int ai, int an, char[] b, int bi, int bn) {
        if (an - ai != bn - bi) return false;
        for (; ai < an; ai++, bi++) if (a[ai] != b[bi]) return false;
        return true;
    }

    private static int finalIndex(char jamo) {
        return "\0ㄱㄲㄳㄴㄵㄶㄷㄹㄺㄻㄼㄽㄾㄿㅀㅁㅂㅄㅅㅆㅇㅈㅊㅋㅌㅍㅎ".indexOf(jamo);
    }

    private static boolean contains(String[] a, String s) {
        for (String x : a) if (x.equals(s)) return true;
        return false;
    }
}
