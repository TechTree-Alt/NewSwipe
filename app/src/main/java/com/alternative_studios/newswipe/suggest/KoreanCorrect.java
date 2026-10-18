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

    KoreanCorrect(KoreanSuggest ks, WordDictionary dict, KoreanMorph morph, TypoModel typo) {
        this.ks = ks;
        this.dict = dict;
        this.morph = morph;
        this.typo = typo;
    }

    /**
     * 자동 수정: 고칠 단어의 최소 빈도(100만 어절당; 앞 단어와 이어지는 단어 쌍이 있을 때는 MIN_FREQ_CONTEXT), 1·2위 점수 차.
     * 사전 점수는 사전 크기에 따라 기준이 달라지므로 빈도로 정한다 (드문 단어로 고치면 맞는 말을 잘못 고치기 쉽다).
     */
    static double CORRECT_MIN_FREQ = 200, CORRECT_MIN_FREQ_CONTEXT = 145;

    static double CORRECT_MARGIN = 0.7;

    /** 자동 수정에서 허용하는 오타 비용 (세 글자 이하, 네 글자 이상). */
    static float CORRECT_MAX_SHORT = 1.0f, CORRECT_MAX_LONG = 1.6f;

    /** 자동 수정에서 이 글자 수 이하의 말은 자모 한 번의 편집(바꾸기·빠뜨리기·더하기·맞바꾸기)으로 되는 단어로만 고친다. */
    static int CORRECT_ONE_EDIT_SYLL = 3;

    /**
     * 사전에 없는 word를 고칠 단어. 오타 비용(자판에서 가까운 키, 같은 키의 다른 동작 등)과 앞 단어와의 관계를 함께 본다.
     * 비슷하게 그럴듯한 후보가 둘 이상이면 고치지 않는다. 사전 단어(조사가 붙은 것 포함)인지는 부르는 쪽이 이미 보았다.
     */
    String correct(String word, String prev, int field, UserDictionary user, UsageHistory usage) {
        int syll = KoreanMorph.hangulSyllables(word);
        if (syll < 2 || syll != word.length()) return null;
        if (dict.contains(word) || WordSuggester.isDictWordWithParticle(dict, word)) return null;
        if (KEEP_PLAUSIBLE && plausible(word)) return null;
        KoreanSuggest.Context cx = ks.context(prev, field, user, usage);
        if (SPELL_RULES) {
            String r = spellFix(word, cx);
            if (r != null) return r;
        }
        char[] p = TypoModel.letters(word);
        float maxCost = syll <= 3 ? CORRECT_MAX_SHORT : CORRECT_MAX_LONG;
        char[] t = new char[72];
        double best = Double.NEGATIVE_INFINITY, second = Double.NEGATIVE_INFINITY;
        int bestId = -1;
        int minScore = ks.scoreFor(CORRECT_MIN_FREQ);
        for (int id : dict.strongWords(ks.scoreFor(CORRECT_MIN_FREQ_CONTEXT))) {
            int len = dict.length(id);
            if (len < syll - 1 || len > syll + 1 || len > 24) continue;
            String w = dict.word(id);
            int tn = TypoModel.letters(w.toCharArray(), 0, len, t);
            if (Math.abs(tn - p.length) > 2) continue;
            float c = typo.distance(p, p.length, t, tn, maxCost);
            if (c > maxCost || c <= 0f) continue;
            // 짧은 말은 싼 편집 두 번(현타 → 한다, 파도 → 봐도)으로도 다른 흔한 말이 되기 쉬워 한 번의 편집만 고친다
            if (syll <= CORRECT_ONE_EDIT_SYLL && !oneEdit(p, p.length, t, tn)) continue;
            boolean context = cx.hasPair(id);
            if (dict.score(id) < minScore && !context) continue;
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

    /** word를 맞춤법 오타 규칙으로 고친 사전 단어. 규칙을 두 군데까지 적용한다 (잇엇다 → 있었다). 없으면 null. */
    private String spellFix(String word, KoreanSuggest.Context cx) {
        String best = null;
        double bestS = 0;
        for (String a : spellEdits(word)) {
            String fix = known(a) ? a : null;
            if (fix == null) {
                for (String b : spellEdits(a)) {
                    if (known(b)) {
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

    private boolean known(String w) {
        return dict.contains(w) || WordSuggester.isDictWordWithParticle(dict, w);
    }

    /** 규칙 하나를 한 자리에 적용한 꼴들. */
    static java.util.List<String> spellEdits(String w) {
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
        }
        return out;
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
