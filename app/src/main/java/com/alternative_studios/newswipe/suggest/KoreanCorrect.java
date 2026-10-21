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

    /** 틀린 꼴이 분명한 말(끝난 말 + 요: 되니요)의 기준. 맞는 말일 수 없으니 훨씬 낮다. */
    static double SHAPE_MIN_WRONG = 100;

    /** 명사 + 조사에서 명사만 고칠 때의 기준 (자댕입니다 → 자동입니다). */
    static double SHAPE_MIN_NOUN = 3000;

    /** SHAPE_RULE에서 고칠 단어로 보는 가장 드문 빈도 (100만 어절당). 더 드문 단어까지 보면 느리고 얻는 것이 거의 없다. */
    static double SHAPE_SCAN_FREQ = 10;

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

    String correct(String word, String prev, int field, UserDictionary user, UsageHistory usage) {
        int syll = KoreanMorph.hangulSyllables(word);
        if (syll < 2 || syll != word.length()) return null;
        if (dict.contains(word) || withParticle(word)) return null;
        if (KEEP_PLAUSIBLE && plausible(word)) return null;
        KoreanSuggest.Context cx = ks.context(prev, field, user, usage);
        if (SPELL_RULES) {
            String r = spellFix(word, cx);
            if (r != null) return r;
        }
        if (!SHAPE_RULE) return search(word, word, cx, false, 0);
        KnownWords k = known;
        if (k != null && k.contains(word)) return null;   // 사전에 없지만 말뭉치에 나온 맞는 말
        // 사전 단어 + 요인데 조사로 받지 않은 말(되니요)은 틀린 꼴이 분명하다
        String[] sp = WordSuggester.splitParticle(word);
        boolean wrong = sp != null && dict.contains(sp[0]);
        String r = search(word, word, cx, false, Math.log(wrong ? SHAPE_MIN_WRONG : k != null ? SHAPE_MIN_KNOWN : SHAPE_MIN));
        if (r != null || sp == null || wrong || sp[0].length() < 2 || k != null && k.contains(sp[0])) return r;
        // 명사 + 조사에서 명사만 틀린 말: 명사를 고쳐 조사를 다시 붙인다 (자댕입니다 → 자동입니다)
        String noun = search(sp[0], sp[0], cx, true, Math.log(k != null ? SHAPE_MIN_KNOWN : SHAPE_MIN_NOUN));
        if (noun == null) return null;
        String[] back = WordSuggester.splitParticle(noun + sp[1]);   // 받침에 맞는 조사인지 (이/가, 은/는 ...)
        return back != null && back[0].equals(noun) ? noun + sp[1] : null;
    }

    /**
     * 사전에 없는 word를 고칠 사전 단어 (없으면 null). noun이면 명사만 본다. SHAPE_RULE이면 minLog = ln(기준)이고, 아니면 예전처럼
     * 최소 빈도(minFreq)를 넘는 단어 중에서 고른다. 비슷하게 그럴듯한 후보가 둘 이상이면 고치지 않는다.
     */
    private String search(String word, String typed, KoreanSuggest.Context cx, boolean noun, double minLog) {
        int syll = word.length();
        char[] p = TypoModel.letters(word);
        float maxCost = syll <= 3 ? CORRECT_MAX_SHORT : CORRECT_MAX_LONG;
        char[] t = new char[72];
        double best = Double.NEGATIVE_INFINITY, second = Double.NEGATIVE_INFINITY;
        int bestId = -1;
        int minScore = ks.scoreFor(CORRECT_MIN_FREQ);
        double lmTyped = SHAPE_RULE ? shape.logProb(typed) : 0;
        for (int id : dict.strongWords(ks.scoreFor(SHAPE_RULE ? SHAPE_SCAN_FREQ : CORRECT_MIN_FREQ_CONTEXT))) {
            int len = dict.length(id);
            if (len < syll - 1 || len > syll + 1 || len > 24) continue;
            double partial = 0;
            if (SHAPE_RULE) {
                // 오타 비용을 뺀 부분을 먼저 본다: 가장 싼 오타(같은 키의 다른 동작)여도 기준에 못 미치면 편집 거리를 재지 않는다
                partial = prior[id] - SHAPE_LM * lmTyped;
                if (partial + SHAPE_CONTEXT - SHAPE_COST * (TypoModel.VARIANT - 0.6) < minLog) continue;
                if (cx.hasPair(id)) partial += SHAPE_CONTEXT;
            }
            String w = dict.word(id);
            int tn = TypoModel.letters(w.toCharArray(), 0, len, t);
            if (Math.abs(tn - p.length) > 2) continue;
            float c = typo.distance(p, p.length, t, tn, maxCost);
            if (c > maxCost || c <= 0f) continue;
            // 짧은 말은 싼 편집 두 번(현타 → 한다, 파도 → 봐도)으로도 다른 흔한 말이 되기 쉬워 한 번의 편집만 고친다
            if (syll <= CORRECT_ONE_EDIT_SYLL && !oneEdit(p, p.length, t, tn)) continue;
            boolean context = cx.hasPair(id);
            if (SHAPE_RULE) {
                if (partial - SHAPE_COST * (c - 0.6) < minLog) continue;
            } else if (dict.score(id) < minScore && !context) {
                continue;
            }
            if (noun && !morph.nounLike(w)) continue;
            double s = Math.log(cx.score(w, id)) - KoreanSuggest.TYPO_PENALTY * c;
            if (s > best) {
                second = best;
                best = s;
                bestId = id;
            } else if (s > second) {
                second = s;
            }
        }
        if (bestId < 0 || best - second < CORRECT_MARGIN) return null;
        return dict.word(bestId);
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
        return !sp[1].equals("요") || "니냐다".indexOf(stem.charAt(stem.length() - 1)) < 0 || morph.nounLike(stem);
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
            "징", "당", "넹", "쥐", "셈", "듯", "음요",
    };

    /** 어간의 마지막 글자에 받침으로 들어가는 어미 첫소리와 그 뒤 (가+ㄴ대 = 간대, 가+ㄹ게 = 갈게, 되+ㅁ = 됨, 가+ㅂ니다). */
    private static final String[][] MERGED_ENDINGS = {
            {"ㄴ", "", "대", "데", "다", "지", "가", "거", "건", "걸", "게", "듯", "줄", "다고", "대요", "데요", "가요", "지도"},
            {"ㄹ", "", "게", "래", "까", "걸", "거", "건", "듯", "수", "때", "지", "께", "껄", "까요", "게요", "래요", "거야"},
            {"ㅁ", ""},
            {"ㅂ", "니다", "니까", "시다"},
    };

    /** 사전에 없는 word가 한국어 낱말로 그럴듯한지 (사전 단어의 앞부분, 용언 어간 + 어미, 명사 + 조사·서술격). */
    boolean plausible(String word) {
        int n = word.length();
        if (prefixFreq(word) * 1e6 >= PLAUSIBLE_PREFIX_FREQ) return true;
        String[] sp = WordSuggester.splitParticle(word);   // 명사 + 조사·서술격 (고민이야, 사람인듯, 친구예요)
        if (sp != null && dict.contains(sp[0]) && morph.nounLike(sp[0])) return true;
        for (int k = n - 1; k >= 1; k--) {
            if (contains(FREE_ENDINGS, word.substring(k)) && morph.verbStem(word.substring(0, k))) return true;
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
