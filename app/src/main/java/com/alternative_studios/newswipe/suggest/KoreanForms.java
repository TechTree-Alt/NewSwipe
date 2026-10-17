package com.alternative_studios.newswipe.suggest;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 사전에 없는 꼴을 만들어 추천 후보에 넣는다: 명사 + 조사('민수ㄱ' → 민수가), 관형형 + 의존 명사(힘든거, 할수), 용언 + 어미
 * (추천해줘, 좋겠지, 됐나봐), 내가 둘 이상의 말에 붙여 쓴 어미(보여줘서·알려줘서 → 보내줘서). 점수는
 * {@link KoreanSuggest.Context}로 매긴다. 화면 스레드에서 부른다 ({@link #learnEndings}만 학습할 때 백그라운드에서).
 */
final class KoreanForms {
    private final WordDictionary dict;
    private final KoreanMorph morph;

    KoreanForms(WordDictionary dict, KoreanMorph morph) {
        this.dict = dict;
        this.morph = morph;
    }

    /** 조사를 붙여 만든 말의 확률에 곱하는 값 (사전에 그 말이 없을 때만 만든다). */
    static double PARTICLE_FACTOR = 0.6;

    /** 조사와, 명사 뒤에서의 대략적인 비율. 받침 뒤(앞), 받침 없는 말 뒤(뒤) 짝. 짝이 없으면 한 칸만. */
    static final String[][] PARTICLES = {
            {"이", "가", "0.15"}, {"을", "를", "0.15"}, {"은", "는", "0.13"}, {"에", null, "0.07"}, {"의", null, "0.05"},
            {"도", null, "0.05"}, {"으로", "로", "0.05"}, {"에서", null, "0.03"}, {"과", "와", "0.03"}, {"만", null, "0.02"},
            {"이랑", "랑", "0.015"}, {"한테", null, "0.012"}, {"까지", null, "0.01"}, {"에는", null, "0.01"},
            {"부터", null, "0.008"}, {"에게", null, "0.008"}, {"이나", "나", "0.006"}, {"처럼", null, "0.005"},
            {"보다", null, "0.005"}, {"하고", null, "0.005"}, {"에도", null, "0.005"}, {"으로는", "로는", "0.004"},
            {"에서는", null, "0.004"}, {"이라도", "라도", "0.003"},
            // 이다 (서술격): 스타일이야, 친구예요, 사람인데, 효과적일까
            {"이야", "야", "0.02"}, {"이에요", "예요", "0.01"}, {"인데", null, "0.008"}, {"이지", "지", "0.004"},
            {"이라서", "라서", "0.004"}, {"이니까", "니까", "0.004"}, {"이고", "고", "0.004"}, {"이면", "면", "0.004"},
            {"이라고", "라고", "0.004"}, {"이래", "래", "0.002"}, {"이었어", "였어", "0.004"}, {"이었는데", "였는데", "0.002"},
            {"일까", null, "0.003"}, {"인가", null, "0.003"}, {"인가봐", null, "0.002"}, {"이잖아", "잖아", "0.002"},
            {"이었다", "였다", "0.002"}, {"이라는", "라는", "0.003"}, {"이랑은", "랑은", "0.001"},
            // 그 밖의 조사
            {"한테서", null, "0.002"}, {"에게서", null, "0.001"}, {"께서", null, "0.001"}, {"께", null, "0.001"},
            {"밖에", null, "0.003"},
            {"뿐", null, "0.001"}, {"이든", "든", "0.002"}, {"보다는", null, "0.001"}, {"으로서", "로서", "0.001"},
            {"이란", "란", "0.002"}, {"에선", null, "0.002"}, {"에서도", null, "0.003"}, {"한테는", null, "0.002"},
            {"한테도", null, "0.002"}, {"까지도", null, "0.001"}, {"만큼", null, "0.002"},
            {"은요", "는요", "0.002"}, {"이요", "요", "0.002"}, {"엔", null, "0.002"},
            {"이며", "며", "0.001"}, {"이든지", "든지", "0.0005"}, {"이라든지", "라든지", "0.0003"},
            // 서술격 더
            {"입니다", null, "0.004"}, {"이었습니다", "였습니다", "0.001"}, {"인가요", null, "0.002"}, {"인지", null, "0.003"},
            {"이네", "네", "0.002"}, {"이네요", "네요", "0.001"}, {"이죠", "죠", "0.002"}, {"이라면", "라면", "0.003"},
            {"이었으면", "였으면", "0.001"}, {"이거든", "거든", "0.001"}, {"이거든요", "거든요", "0.001"}, {"인데요", null, "0.002"},
            {"이었지", "였지", "0.001"}, {"이겠지", "겠지", "0.001"}, {"일걸", null, "0.001"}, {"이래요", "래요", "0.001"},
            {"이라니", "라니", "0.001"}, {"이었네", "였네", "0.001"},
            // 겹조사: 뿐만, 밖에는, 까지만, 에서부터 ...
            {"뿐만", null, "0.001"}, {"뿐이야", null, "0.0006"}, {"뿐이에요", null, "0.0004"}, {"뿐인데", null, "0.0004"},
            {"뿐이다", null, "0.0004"}, {"뿐이지", null, "0.0003"}, {"뿐이고", null, "0.0002"}, {"뿐이었다", null, "0.0002"},
            {"뿐이었어", null, "0.0002"}, {"뿐이라고", null, "0.0002"}, {"뿐이니까", null, "0.0002"},
            {"밖에는", null, "0.0005"}, {"밖엔", null, "0.0003"}, {"밖에도", null, "0.0003"},
            {"만이", null, "0.002"}, {"만을", null, "0.002"}, {"만은", null, "0.001"}, {"만으로", null, "0.001"},
            {"만의", null, "0.001"}, {"만이라도", null, "0.0005"}, {"만도", null, "0.0003"}, {"만큼은", null, "0.0005"},
            {"까지는", null, "0.002"}, {"까지만", null, "0.001"}, {"까지의", null, "0.001"},
            {"에만", null, "0.001"},
            {"에서만", null, "0.001"}, {"에서의", null, "0.001"}, {"에서부터", null, "0.0005"},
            {"에게는", null, "0.001"}, {"에게도", null, "0.001"}, {"에게만", null, "0.0003"}, {"한테만", null, "0.0005"},
            {"부터는", null, "0.0005"}, {"부터도", null, "0.0003"}, {"보다도", null, "0.0005"},
            {"으로만", "로만", "0.001"}, {"으로도", "로도", "0.002"}, {"으로부터", "로부터", "0.001"}, {"으로써", "로써", "0.0005"},
            {"과는", "와는", "0.001"}, {"과도", "와도", "0.001"}, {"과의", "와의", "0.001"}, {"이랑도", "랑도", "0.0005"},
    };

    private static final double[] PARTICLE_PRIOR = new double[PARTICLES.length];

    /**
     * 사람 뒤에만 오는 조사 (수한테, 사업한테, 수께서 같은 말이 나오지 않게): 사전에 그 말 + 한테·에게·께가 있거나
     * 내가 쓴 말일 때만 붙인다. 근거 없는 사람 이름(오빠한테)은 한 번 쓰면 입력 기록으로 배운다.
     */
    private static final boolean[] PARTICLE_ANIMATE = new boolean[PARTICLES.length];

    static {
        for (int i = 0; i < PARTICLES.length; i++) {
            PARTICLE_PRIOR[i] = Double.parseDouble(PARTICLES[i][2]);
            String p = PARTICLES[i][0];
            PARTICLE_ANIMATE[i] = p.startsWith("께") || p.startsWith("한테") || p.startsWith("에게");
        }
    }

    /** 명사에 조사를 붙인 말을 후보에 넣는다 ('민수ㄱ' → 민수가, '친구ㄹ' → 친구를). 사전에 이미 있는 말은 사전 점수를 쓴다. */
    void addParticles(Map<String, Double> pool, KoreanSuggest.Context cx, String word) {
        int n = word.length();
        for (int k = Math.max(1, n - 3); k <= n; k++) {
            String stem = word.substring(0, k);
            char last = stem.charAt(k - 1);
            if (last < 0xAC00 || last > 0xD7A3) continue;
            int sid = dict.indexOf(stem);
            boolean mine = cx.user != null && cx.user.knownCountIfLoaded(stem) > 0;
            if (!mine && (sid < 0 || !morph.nounLike(stem))) continue;
            double ps = cx.score(stem, sid);
            if (ps <= 0) continue;
            boolean fin = (last - 0xAC00) % 28 != 0;
            boolean rieul = (last - 0xAC00) % 28 == 8;
            boolean animate = mine || morph.animate(stem);
            String rest = word.substring(k);   // 조사 자리에 이미 친 것 (stem + 조사가 word로 시작하려면 조사가 rest로 시작해야 한다)
            for (int i = 0; i < PARTICLES.length; i++) {
                if (PARTICLE_ANIMATE[i] && !animate) continue;
                String[] p = PARTICLES[i];
                String particle;
                if (p[1] == null) particle = p[0];
                else if (fin && !(rieul && p[1].startsWith("로"))) particle = p[0];   // ㄹ 받침 뒤에는 '로'
                else particle = p[1];
                // 한 글자 말 + 은요·는요·요는 '자는', '우는' 같은 용언 활용 뒤에 요를 붙인 꼴이 되기 쉽다
                if (k == 1 && particle.endsWith("요") && particle.length() <= 2) continue;
                if (!rest.isEmpty() && (particle.equals(rest) || !HangulRange.startsWith(particle, rest))) continue;
                String cand = stem + particle;
                if (dict.indexOf(cand) >= 0) continue;   // 사전에 있으면 사전 후보로 이미 들어간다
                double s = ps * PARTICLE_PRIOR[i] * PARTICLE_FACTOR;
                Double old = pool.get(cand);
                if (old == null || old < s) pool.put(cand, s);
            }
        }
    }

    /**
     * 관형형 뒤에 띄어 쓰지 않고 붙여 치는 의존 명사 (좋아하는게, 힘든거, 할수, 갈때). 메신저에서는 띄어 쓰지 않는 일이 많다.
     * ㄴ 관형형(-는, -은, -ㄴ, -던) 뒤와 ㄹ 관형형(-ㄹ, -을) 뒤가 다르다. 짝은 {말, 대략적인 비율}.
     */
    private static final String[][] BOUND_N = {
            {"게", "0.25"}, {"거", "0.2"}, {"건", "0.1"}, {"걸", "0.06"}, {"거야", "0.05"}, {"거지", "0.03"},
            {"거같아", "0.03"}, {"건데", "0.02"}, {"걸까", "0.02"}, {"지", "0.02"}, {"듯", "0.02"}, {"줄", "0.02"},
            {"거니", "0.01"}, {"거면", "0.01"}, {"거예요", "0.01"}, {"건지", "0.01"}, {"건가", "0.01"}, {"거라", "0.01"},
    };

    private static final String[][] BOUND_L = {
            {"수", "0.25"}, {"때", "0.2"}, {"거야", "0.1"}, {"거", "0.05"}, {"줄", "0.05"}, {"거같아", "0.03"}, {"걸", "0.03"},
            {"듯", "0.03"}, {"수도", "0.03"}, {"때마다", "0.02"}, {"때는", "0.02"}, {"지도", "0.02"}, {"수있어", "0.02"},
            {"수있을까", "0.01"}, {"거면", "0.01"}, {"거니까", "0.01"}, {"만큼", "0.01"},
    };

    static double BOUND_FACTOR = 0.2;

    void addBound(Map<String, Double> pool, KoreanSuggest.Context cx, String word) {
        int n = word.length();
        for (int k = n; k >= Math.max(1, n - 3); k--) {
            String x = word.substring(0, k);
            char last = x.charAt(k - 1);
            if (last < 0xAC00 || last > 0xD7A3) continue;
            int t = (last - 0xAC00) % 28;
            String[][] list;
            // 관형형인지: ㄴ 관형형은 사전에 '-데'(좋아하는데, 힘든데), ㄹ 관형형은 '-까·-게·-래'(할까, 할게, 갈래)가 있어야 한다
            if (t == 4 && (dict.contains(x + "데") || adnominalStem(x))) list = BOUND_N;
            else if (t == 8 && (dict.contains(x + "까") || dict.contains(x + "게") || dict.contains(x + "래"))) list = BOUND_L;
            else continue;
            int id = dict.indexOf(x);
            double px = cx.score(x, id);
            if (px <= 0) continue;
            for (String[] b : list) {
                String cand = x + b[0];
                if (cand.equals(word) || !HangulRange.startsWith(cand, word) || dict.indexOf(cand) >= 0) continue;
                double sc = px * Double.parseDouble(b[1]) * BOUND_FACTOR;
                Double old = pool.get(cand);
                if (old == null || old < sc) pool.put(cand, sc);
            }
        }
    }

    /** ㄴ 받침 말이 용언의 관형형인지: 어간 + 고가 사전에 있으면 (해주는 → 해주고, 생긴 → 생기고, 힘든 → 힘들고). */
    private boolean adnominalStem(String x) {
        String head = x.substring(0, x.length() - 1);
        char last = x.charAt(x.length() - 1);
        if (last == '는' || last == '던') return !head.isEmpty() && dict.contains(head + "고");
        char open = (char) (last - 4);   // 받침 ㄴ을 뗀 글자
        return dict.contains(head + open + "고") || dict.contains(head + (char) (open + 8) + "고");
    }

    /**
     * '○○하다'의 어미 (하다 활용은 규칙적이라 모두 붙인다). 사전에 '○○하고', '○○했다' 같은 활용형이 있는 말에만
     * 붙인다. 어미 목록(아래 일반 어간·과거형도)은 사람이 쓴 대화 문장(평가에 쓰지 않는 절반)에서 흔한 순서이고, 같은 어간이면
     * 앞의 것을 먼저 낸다.
     * 짝은 {어미, 대략적인 비율}.
     */
    private static final String[] HA_ENDINGS = {
            "해", "하고", "할까", "하면", "하네", "해도", "했어", "하는데", "하지", "했는데", "하게", "해야지", "해서", "해줘",
            "하겠어", "한데", "하려고", "하다",
            "하겠지", "할거야", "해볼까", "해요", "했습니다", "할까요", "합니다", "하니", "했으면", "했네", "하더라", "하니까", "했더니",
            "할걸", "하나봐", "했다", "했잖아", "하지만", "하네요", "했어요", "하냐", "하세요", "하자", "하던데", "했지", "하겠다", "할게요",
            "하잖아", "할게", "하겠네", "해봐", "할래", "해줘요", "했나봐", "했거든", "하기로", "하거든", "할래요", "해야돼",
    };

    /** 동사에만 붙는 하다 어미 (피곤해줘, 가능할래 같은 말이 나오지 않게). */
    private static final String[] HA_VERB_ONLY = {"해줘", "해줘요", "해봐", "해볼까", "하자", "하려고", "할게", "할게요", "할래",
            "할래요", "하기로", "하는데"};

    /** 형용사에만 붙는 하다 어미 (불쌍한데 O, 추천한데 X). */
    private static final String[] HA_ADJ_ONLY = {"한데"};

    /** 동사인지 알아보는 활용형 (형용사는 '피곤한다', '가능하자'가 없다). */
    private static final String[] HA_VERB_EVIDENCE = {"한다", "하는", "하자", "할게"};

    private static final String[] HA_VERB_ENDINGS, HA_ADJ_ENDINGS;

    static {
        List<String> adj = new ArrayList<>(Arrays.asList(HA_ENDINGS));
        adj.removeAll(Arrays.asList(HA_VERB_ONLY));
        HA_ADJ_ENDINGS = adj.toArray(new String[0]);
        List<String> verb = new ArrayList<>(Arrays.asList(HA_ENDINGS));
        verb.removeAll(Arrays.asList(HA_ADJ_ONLY));
        HA_VERB_ENDINGS = verb.toArray(new String[0]);
    }

    /** 일반 어간에 붙이는 어미: 자음으로 시작해 불규칙 활용과 상관없고 동사·형용사 모두에 붙는 것만. */
    private static final String[] STEM_ENDINGS = {
            "고", "지", "게", "네", "겠어", "니", "겠지", "겠다", "죠", "지만", "기로", "냐", "더라", "네요", "겠네", "잖아",
            "겠어요", "던데", "잖아요", "거든", "거든요", "더라고",
    };

    private static final String[] STEM_EVIDENCE = {"고", "지", "는", "네", "게", "기", "다", "던", "지만", "겠다"};

    /** 과거형(-았/었/였-, 받침 ㅆ) 뒤에 붙이는 어미. */
    private static final String[] PAST_ENDINGS = {
            "어", "고", "지", "네", "다", "는데", "을까", "나", "으면", "니", "겠지", "대", "습니다", "겠다", "나봐", "죠",
            "다고", "더니", "지만", "어요", "나요", "냐", "더라", "네요", "구나", "겠네", "잖아", "대요", "던데", "잖아요", "거든",
            "거든요", "을걸", "더라고",
    };

    private static final String[] PAST_EVIDENCE = {"어", "다", "지", "네", "는데", "고", "어요", "던", "으면"};

    /** 어미를 붙여 만든 말의 확률에 곱하는 값 (같은 어간의 사전 활용형 확률 합에 곱한다). */
    static double ENDING_FACTOR = 1.0;

    /**
     * 사전에 없는 활용형을 만들어 넣는다 ('추천해' → 추천해줘, '좋겠' → 좋겠지, '됐나' → 됐나봐). 사전에 같은 어간의 활용형이
     * 있어야 하며(하다는 하나, 일반 어간·과거형은 둘 이상), 규칙이 확실한 어미만 붙여 틀린 활용이 나오지 않게 한다.
     */
    private void addEndings(Map<String, Double> pool, KoreanSuggest.Context cx, String word) {
        int n = word.length();
        for (int k = n; k >= Math.max(1, n - 4); k--) {
            String base = word.substring(0, k);
            char last = base.charAt(k - 1);
            if (last < 0xAC00 || last > 0xD7A3) continue;
            // 하다: base가 '○○하'로 끝나거나, 입력이 '○○'까지이고 뒤에 하·해·했·할이 오는 중
            if (last == '하' && k >= 2) addHa(pool, cx, word, base.substring(0, k - 1));
            int t = (last - 0xAC00) % 28;
            if (t == 20) addPast(pool, cx, word, base);   // 받침 ㅆ
            // '있다', '했다'처럼 다로 끝난 말은 이미 끝난 활용형이다 (있다잖아, 했다겠네가 나오지 않게)
            if (last != '다' && !quoted(base)) {
                generate(pool, cx, word, base, STEM_EVIDENCE, 2, "고", endingsFor(cx, END_STEM, STEM_ENDINGS), t == 8);
                if (MORE_STEM) generate(pool, cx, word, base, STEM_EVIDENCE, 2, "고", MORE_STEM_ENDINGS, t == 8);
                if (VERB_ENDS && verbOnly(base)) generate(pool, cx, word, base, STEM_EVIDENCE, 2, "고",
                        existential(base) ? NEUN_ENDINGS : VERB_STEM_ENDINGS, t == 8);
                if (GUNA) generate(pool, cx, word, base, STEM_EVIDENCE, 2, "고", verbOnly(base) ? VERB_GUNA : ADJ_GUNA, t == 8);
                if (MEDIAL) addMedial(pool, cx, word, base);
                if (VERB_DA || FORMAL) addDirect(pool, cx, word, base);
                if ((MORE_STEM || VERB_ENDS) && t == 0) addRieulDropped(pool, cx, word, base);
            }
        }
        for (int k = n; k >= Math.max(1, n - 3); k--) addEo(pool, cx, word, word.substring(0, k));
        // '불쌍'처럼 하다 앞말까지 친 말 → 불쌍해, 불쌍하다
        if (n >= 2 && KoreanMorph.hangulSyllables(word) == n) addHa(pool, cx, word, word);
        // '추천해', '부업할'처럼 하다 활용 중간까지 친 말: 입력이 명사 + 해/했/할/한/합 ...
        for (int k = n - 1; k >= Math.max(1, n - 4); k--) {
            char c = word.charAt(k);
            if (c >= 0xAC00 && c <= 0xD7A3 && (c - 0xAC00) / 588 == 18 || c == 'ㅎ') {   // 초성 ㅎ (하·해·했·할 ...)
                addHa(pool, cx, word, word.substring(0, k));
            }
        }
    }

    /** '보자', '아니라'처럼 어간 + 자·라로 끝난 말 (보자던데, 아니라겠네가 나오지 않게). */
    private boolean quoted(String w) {
        char last = w.charAt(w.length() - 1);
        return w.length() >= 2 && (last == '자' || last == '라') && dict.contains(w.substring(0, w.length() - 1) + "고");
    }

    /**
     * '○○하다' 활용형. 형용사(피곤하다)에는 동사 어미를 붙이지 않는다. 한 글자 말은 뜻이 굳은 말(위해, 통해, 대해)이 많아,
     * 띄어 쓰지 않고 붙여 치는 안·못·잘·다와 사전에 '말해줘', '일할게' 같은 동사 활용이 있는 말에만 붙인다.
     */
    private void addHa(Map<String, Double> pool, KoreanSuggest.Context cx, String word, String stem) {
        char last = stem.charAt(stem.length() - 1);
        if (last == '야' || last == '해') return;   // 해야하는 → '해야'는 하다 앞말이 아니다
        boolean verb = false;
        for (String e : HA_VERB_EVIDENCE) verb |= dict.contains(stem + e);
        if (stem.length() == 1) {
            boolean ok = "안못잘다".indexOf(last) >= 0;
            for (String e : HA_VERB_ONLY) ok |= dict.contains(stem + e);
            if (!ok) return;
            verb = true;
        }
        generate(pool, cx, word, stem, KoreanMorph.HA_EVIDENCE, 1, null, verb ? HA_VERB_ENDINGS : HA_ADJ_ENDINGS, false);
        if (HA_MORE) generate(pool, cx, word, stem, KoreanMorph.HA_EVIDENCE, 1, null, verb ? HA_VERB_MORE : HA_ADJ_MORE, false);
    }

    /** 하다 어미 더 (문서 '어미'): 동사 -는지·-ㄴ다·-ㅂ시다, 과거 + -는지, 형용사 -ㄴ가·-구나. */
    static boolean HA_MORE = false;
    private static final String[] HA_VERB_MORE = {"하는지", "한다", "하는거", "하는게", "했는지", "한대", "한지", "하는구나",
            "합시다", "하셨어요"};
    private static final String[] HA_ADJ_MORE = {"한지", "한가", "하구나", "한가요", "했는지", "한거", "한게"};

    /** 만든 활용형을 이미 있는 후보 아래에 넣는다 (사전에 있는 '말자'가 만든 '말죠'에 밀리지 않게). 순서는 그대로 둔다. */
    void addBelow(Map<String, Double> pool, KoreanSuggest.Context cx, String word) {
        Map<String, Double> made = new HashMap<>();
        addEndings(made, cx, word);
        if (made.isEmpty()) return;
        double floor = Double.MAX_VALUE, top = 0;
        for (double v : pool.values()) floor = Math.min(floor, v);
        for (double v : made.values()) top = Math.max(top, v);
        double scale = pool.isEmpty() || top < floor ? 1 : floor * 0.9 / top;
        for (Map.Entry<String, Double> e : made.entrySet()) {
            if (!pool.containsKey(e.getKey())) pool.put(e.getKey(), e.getValue() * scale);
        }
    }

    /**
     * stem + 어미 중 입력 word로 시작할 수 있는 것을 후보에 넣는다. stem + evidence 활용형이 사전에 minEvidence개 이상
     * 있어야 하고, must가 있으면 stem + must도 사전에 있어야 한다 (일반 어간은 '먹고'처럼 -고 꼴이 있어야 어간으로 본다:
     * '할', '있을'은 할지·할게가 있어도 어간이 아니다). dropRieul: ㄹ 받침 어간이면 네·니 앞에서 ㄹ을 뗀다 (놀 → 노네, 사니).
     */
    private void generate(Map<String, Double> pool, KoreanSuggest.Context cx, String word, String stem, String[] evidence,
                          int minEvidence, String must, String[] endings, boolean dropRieul) {
        emit(pool, word, stem, endings, dropRieul, family(cx, stem, evidence, minEvidence, must));
    }

    /** stem + evidence 활용형의 확률 합 (활용형이 minEvidence개보다 적거나 stem + must가 없으면 0). */
    private double family(KoreanSuggest.Context cx, String stem, String[] evidence, int minEvidence, String must) {
        if (stem.isEmpty() || KoreanMorph.hangulSyllables(stem) != stem.length()) return 0;
        if (must != null && !dict.contains(stem + must)) return 0;
        double family = 0;
        int found = 0;
        for (String e : evidence) {
            int id = dict.indexOf(stem + e);
            if (id < 0) continue;
            found++;
            family += cx.score(stem + e, id);
        }
        return found < minEvidence ? 0 : family;
    }

    private void emit(Map<String, Double> pool, String word, String stem, String[] endings, boolean dropRieul,
                      double family) {
        if (family <= 0) return;
        for (int i = 0; i < endings.length; i++) {
            String e = endings[i];
            String s = stem;
            if (dropRieul && rieulDrops(e)) {
                char l = stem.charAt(stem.length() - 1);
                s = stem.substring(0, stem.length() - 1) + (char) (l - 8);
            }
            String cand = s + e;
            if (cand.equals(word) || !HangulRange.startsWith(cand, word) || dict.indexOf(cand) >= 0) continue;
            double sc = family * ENDING_FACTOR * (1 - 0.002 * i);   // 같은 어간이면 흔한 어미부터
            Double old = pool.get(cand);
            if (old == null || old < sc) pool.put(cand, sc);
        }
    }

    /**
     * 어간에 더 붙일 어미: 자음으로 시작해 불규칙 활용과 상관없는 것, 동사에만 붙는 것, 매개모음 '-으-' 계열 (dev에서 셋 다
     * 켜면 +0.12%p, 말뭉치마다 모두 올랐고 만들어 낸 후보의 적중률은 그대로).
     */
    static boolean MORE_STEM = true, VERB_ENDS = true, MEDIAL = true;

    /** 동사·형용사 어간 모두에 붙고 자음으로 시작하는 어미 (STEM_ENDINGS에 더). 사람이 쓴 대화 문장(dev)에서 흔한 순서. */
    private static final String[] MORE_STEM_ENDINGS = {"기", "던", "거나", "기도", "도록", "긴", "길", "기만", "길래", "진",
            "지도", "기는", "지는"};

    /** 동사 어간에만 붙는 어미 (형용사에는 '-는'이 없다: 예쁘는데 X). ㄹ 어간은 ㄴ 앞에서 ㄹ이 떨어진다 (만드는, 파는지). */
    private static final String[] VERB_STEM_ENDINGS = {"는", "는데", "는지", "는게", "는거", "느라", "나", "나요", "는건", "는걸",
            "고싶어", "고있어", "고싶다", "고있는데"};

    /** ㄹ 어간에서 ㄹ이 떨어지는 어미인지 (ㄴ·ㅅ으로 시작: 만드니, 만드네, 만드는, 만드나, 만드세요, 만드신). */
    private static boolean rieulDrops(String e) {
        char c = e.charAt(0);
        if (c < 0xAC00 || c > 0xD7A3) return false;
        int cho = (c - 0xAC00) / 588;
        return cho == 2 || cho == 9;   // ㄴ, ㅅ
    }

    /**
     * '만드'처럼 ㄹ이 떨어진 꼴까지 친 말: 끝 글자에 ㄹ을 단 꼴(만들)이 ㄹ 어간이면, ㄴ·ㅅ으로 시작하는 어미를 ㄹ을 떼고 붙인다
     * (만드는지, 만드니, 만드네요). 어간 '만들'은 입력 '만드'의 앞부분이 아니라 따로 찾는다.
     */
    private void addRieulDropped(Map<String, Double> pool, KoreanSuggest.Context cx, String word, String s) {
        String sl = withFinal(s, 8);
        if (sl == null) return;
        List<String> es = new ArrayList<>();
        for (String e : STEM_ENDINGS) if (rieulDrops(e)) es.add(e);
        if (VERB_ENDS && verbOnly(sl)) for (String e : VERB_STEM_ENDINGS) if (rieulDrops(e)) es.add(e);
        if (es.isEmpty()) return;
        generate(pool, cx, word, sl, STEM_EVIDENCE, 2, "고", es.toArray(new String[0]), true);
    }

    /** VERB_STEM_ENDINGS 중 '-는' 계열만 (없다처럼 -는은 붙지만 동작을 나타내지 않는 말: 없고싶다 X). */
    private static final String[] NEUN_ENDINGS = Arrays.copyOf(VERB_STEM_ENDINGS, 10);

    /**
     * 있다·없다(맛없다, 재밌다): -는이 붙지만 -ㄴ다·-려고·-ㅂ시다·-고 싶다 같은 동작의 어미나 관형 -(으)ㄴ은 붙지 않는다
     * (없는다·없으러·재밌고싶다·없은 X). 받침 ㅆ·ㅄ인 어간.
     */
    private static boolean existential(String stem) {
        int t = (stem.charAt(stem.length() - 1) - 0xAC00) % 28;
        return t == 20 || t == 18;
    }

    /** 동사 어간인지 (사전에 어간 + 는이 있다. ㄹ 어간은 ㄹ을 뗀 꼴 + 는). */
    private boolean verbOnly(String stem) {
        if (dict.contains(stem + "는")) return true;
        char l = stem.charAt(stem.length() - 1);
        return (l - 0xAC00) % 28 == 8 && dict.contains(stem.substring(0, stem.length() - 1) + (char) (l - 8) + "는");
    }

    /** 매개모음 '-으-' 뒤의 어미: 앞 꼴(먹으·가·만들·들으·도우) 바로 뒤에 붙는 것. 사람이 쓴 대화 문장에서 흔한 순서. */
    private static final String[] MEDIAL_ENDINGS = {"면", "니까", "려고", "면서", "세요", "러", "시면", "셨어", "시는", "려면",
            "니깐", "시고", "셔서", "시나요", "실래요", "셨는데", "시네요", "셨나요"};

    /** 앞 꼴 + ㄹ(먹을·갈·만들·들을·도울) 뒤에 붙는 어미. */
    private static final String[] MEDIAL_L_ENDINGS = {"까", "게", "래", "거야", "수", "때", "걸", "텐데", "까요", "게요", "래요",
            "지도", "거예요", "듯"};

    /** 받침 ㄷ·ㅂ·ㅅ·ㅎ: 불규칙일 수 있어 (듣다 → 들으면, 춥다 → 추우면, 낫다 → 나으면, 파랗다 → 파라면) 사전에 규칙 꼴이 있을 때만 '으'를 넣는다. */
    private static boolean irregularPossible(int jong) {
        return jong == 7 || jong == 17 || jong == 19 || jong == 27;
    }

    /** 동사에만 붙는 '-으-' 계열 어미 (목적 -러, 의도 -려고·-ㄹ래, 약속 -ㄹ게: 좋으러·좋을게 X). */
    private static final java.util.Set<String> MEDIAL_VERB_ONLY = new java.util.HashSet<>(Arrays.asList(
            "려고", "러", "려면", "실래요", "게", "래", "게요", "래요"));

    /**
     * 사전에 앞 꼴 b의 '-으-' 계열 활용형이 몇 개 있는지 {모두, ㄹ이 떨어지지 않는 어미(면·려고·러 ...)로 된 것, 동사에만 붙는 어미로 된 것}.
     * 같은 줄기로 다른 어미를 붙여도 되는 근거. ㄴ·ㅅ 앞의 꼴(만드니까·만드세요)만 있으면 b는 ㄹ이 떨어진 꼴일 수 있다.
     */
    private int[] medialEvidence(KoreanSuggest.Context cx, String b, double[] mass) {
        int found = 0, plain = 0, verb = 0;
        for (String e : new String[]{"면", "니까", "려고", "면서", "세요", "러", "시고", "시는"}) {
            String w = cut(b, e) + e;
            int id = dict.indexOf(w);
            if (id < 0) continue;
            found++;
            if (!rieulDrops(e)) plain++;
            if (MEDIAL_VERB_ONLY.contains(e)) verb++;
            if (mass != null) mass[0] += cx.score(w, id);
        }
        String bl = withFinal(b, 8);
        if (bl != null) {
            for (String e : new String[]{"까", "게", "래", "거야", "수", "때"}) {
                int id = dict.indexOf(bl + e);
                if (id < 0) continue;
                found++;   // ㄹ 꼴은 ㄹ 어간과 같아(만드 + ㄹ = 만들) 'ㄹ이 떨어지지 않는 근거'로 세지 않는다
                if (MEDIAL_VERB_ONLY.contains(e)) verb++;
                if (mass != null) mass[0] += cx.score(bl + e, id);
            }
        }
        return new int[]{found, plain, verb};
    }

    /** ㄹ 어간이면 ㄴ·ㅅ 앞에서 ㄹ을 뗀 앞 꼴. */
    private static String cut(String b, String e) {
        char l = b.charAt(b.length() - 1);
        if ((l - 0xAC00) % 28 == 8 && rieulDrops(e)) return b.substring(0, b.length() - 1) + (char) (l - 8);
        return b;
    }

    /** b의 끝 글자에 받침(jong)을 단 꼴 (받침이 이미 ㄹ이면 ㄹ은 그대로, ㅁ이면 ㄻ). 붙일 수 없으면 null. */
    private static String withFinal(String b, int jong) {
        char l = b.charAt(b.length() - 1);
        int t = (l - 0xAC00) % 28;
        if (t == 0) return b.substring(0, b.length() - 1) + (char) (l + jong);
        if (t == 8) return jong == 8 ? b : jong == 16 ? b.substring(0, b.length() - 1) + (char) (l + 2) : null;   // 만들 → 만들, 만듦
        return null;
    }

    /**
     * 매개모음 '-으-' 계열 어미 ('-(으)면', '-(으)니까', '-(으)려고', '-(으)시-', '-(으)ㄹ까', '-(으)ㅁ'). 앞 꼴은 어간에 따라 다르다:
     * 받침 없는 어간과 ㄹ 어간은 그대로(가면, 만들면; ㄹ은 ㄴ·ㅅ 앞에서 떨어진다: 만드니까, 만드세요), 받침 있는 어간은 '으'를
     * 넣는다(먹으면). 받침 ㄷ·ㅂ·ㅅ·ㅎ은 불규칙일 수 있어 사전에 '으'를 넣은 꼴이 있을 때만 넣는다. 불규칙 꼴(들으·도우·나으)은
     * 사용자가 바뀐 꼴을 쳤을 때, 사전에 그 꼴의 '-으-' 계열 활용이 둘 이상 있으면 같은 앞 꼴에 붙인다.
     */
    private void addMedial(Map<String, Double> pool, KoreanSuggest.Context cx, String word, String s) {
        char last = s.charAt(s.length() - 1);
        int t = (last - 0xAC00) % 28;
        if (t == 20) return;   // 받침 ㅆ(했·먹었)은 addPast가 맡는다
        String b = null;
        double fam = 0;
        double[] mass = new double[1];
        boolean verb, action = false, dropOnly = false;
        double stem = family(cx, s, STEM_EVIDENCE, 2, "고");
        boolean fromStem = stem > 0;
        if (stem > 0) {
            if (t == 0 || t == 8) {
                b = s;
            } else if (!irregularPossible(t) || regular(s)) {
                b = s + "으";
            }
            fam = stem;
            verb = verbOnly(s);
            action = verb && !existential(s);
        } else {
            verb = false;
        }
        if (b == null && t == 0) {   // 사용자가 바뀐 꼴을 쳤다: 들으·도우·나으 (불규칙 활용), 만드 (ㄹ이 떨어진 꼴)
            int[] ev = medialEvidence(cx, s, mass);
            if (ev[0] >= 2) {
                b = s;
                fam = mass[0];
                dropOnly = ev[1] == 0;   // 만드니까·만드세요만 있다: ㄹ 어간이 ㄴ·ㅅ 앞에서 떨어진 꼴이다 (만드면 X)
                verb = action = ev[2] > 0;
            }
        }
        if (b == null || fam <= 0) return;
        int i = 0;
        for (String e : MEDIAL_ENDINGS) {
            if (dropOnly && !rieulDrops(e) || !action && MEDIAL_VERB_ONLY.contains(e)) continue;
            put(pool, word, cut(b, e) + e, fam * ENDING_FACTOR * (1 - 0.002 * i++));
        }
        String bl = withFinal(b, 8);
        if (bl != null) {
            for (String e : MEDIAL_L_ENDINGS) {
                if (!action && MEDIAL_VERB_ONLY.contains(e)) continue;
                put(pool, word, bl + e, fam * ENDING_FACTOR * (1 - 0.002 * i++));
            }
        }
        String bm = dropOnly ? null : withFinal(b, 16);
        if (bm != null) put(pool, word, bm, fam * ENDING_FACTOR * (1 - 0.002 * i++));
        String bn = finalOn(b, 4);   // 앞 꼴 + ㄴ: 좋은, 추운, 큰, 긴, 만든
        if (ADJ_N && bn != null && !existential(s)) {
            if (dropOnly) verb = action = verbOnly(withFinal(s, 8));
            // 바뀐 꼴(추우·들으)은 형용사인지 확실하지 않으면 동사·형용사 모두에 맞는 것만 (들은데 X)
            boolean adj = !verb && (fromStem || dict.contains(bn + "데") || dict.contains(bn + "지") || dict.contains(bn + "가"));
            for (String e : adj ? N_ADJ_ENDINGS : N_VERB_ENDINGS) put(pool, word, bn + e, fam * ENDING_FACTOR * (1 - 0.002 * i++));
        }
        // 동사 -(으)ㅂ시다: 갑시다, 먹읍시다, 만듭시다, 들읍시다
        String bp = FORMAL && action ? finalOn(b, 17) : null;
        if (bp != null) put(pool, word, bp + "시다", fam * ENDING_FACTOR * (1 - 0.002 * i));
    }

    /** 어간에 바로 붙는 동사 -ㄴ다/-는다(간다, 만든다, 먹는대)와 -ㅂ니다/-습니다(갑니다, 만듭니다, 춥습니다). 만드처럼 ㄹ이 떨어진 꼴도. */
    private void addDirect(Map<String, Double> pool, KoreanSuggest.Context cx, String word, String s) {
        char last = s.charAt(s.length() - 1);
        int t = (last - 0xAC00) % 28;
        if (t == 20) return;
        double fam = family(cx, s, STEM_EVIDENCE, 2, "고");
        String stem = s;
        if (fam <= 0 && t == 0) {
            stem = withFinal(s, 8);
            if (stem == null) return;
            fam = family(cx, stem, STEM_EVIDENCE, 2, "고");
            t = 8;
        }
        if (fam <= 0) return;
        boolean verb = verbOnly(stem) && !existential(stem), open = t == 0 || t == 8;
        double sc = fam * ENDING_FACTOR;
        int i = 0;
        if (VERB_DA && verb) {
            String head = open ? finalOn(stem, 4) : stem + "는";
            for (String e : DA_ENDINGS) put(pool, word, head + e, sc * (1 - 0.002 * i++));
        }
        if (FORMAL) {
            String head = open ? finalOn(stem, 17) : stem + "습";
            for (String e : FORMAL_ENDINGS) put(pool, word, head + e, sc * (1 - 0.002 * i++));
        }
    }

    /**
     * 문서(어미, 불규칙 활용)를 보고 더한 어미 (실험 스위치). 과거 뒤 '-는' 계열(갔는지, 했는게), 앞 꼴 + ㄴ(형용사 좋은데·추운지·
     * 큰가, 동사 먹은거·간건), 동사 -ㄴ다/-는다(간다, 먹는대), -ㅂ니다/-습니다(갑니다, 만듭니다, 먹습니다), 동사 -는구나·형용사 -구나.
     */
    static boolean PAST_NEUN = false, ADJ_N = true, VERB_DA = true, FORMAL = true, GUNA = false;

    private static final String[] PAST_NEUN_ENDINGS = {"는지", "는게", "는거", "는데요", "는건", "는걸", "는지도", "는구나", "는군요"};
    private static final String[] N_ADJ_ENDINGS = {"데", "지", "가", "데요", "거", "건", "게", "걸", "가요", "지도"};
    private static final String[] N_VERB_ENDINGS = {"거", "건", "게", "걸"};
    private static final String[] DA_ENDINGS = {"다", "대", "다고", "대요", "다면"};
    private static final String[] FORMAL_ENDINGS = {"니다", "니까"};
    private static final String[] VERB_GUNA = {"는구나", "는군요", "는군"};
    private static final String[] ADJ_GUNA = {"구나", "군요", "군"};

    /** b의 끝 글자에 받침 ㄴ·ㄹ·ㅂ을 단 꼴. ㄹ 받침은 종성 ㄴ·ㄹ·ㅂ 앞에서 떨어진다 (만들 + ㄴ = 만든, 만들 + ㅂ = 만듭). */
    private static String finalOn(String b, int jong) {
        char l = b.charAt(b.length() - 1);
        int t = (l - 0xAC00) % 28;
        if (t == 0) return b.substring(0, b.length() - 1) + (char) (l + jong);
        if (t == 8) return b.substring(0, b.length() - 1) + (char) (l - 8 + jong);
        return null;
    }

    /**
     * 받침 ㄷ·ㅂ·ㅅ·ㅎ 어간이 규칙 활용인지: 사전에 '으'를 넣은 꼴(닫으면)이나 어간 + 아/어 꼴(닫아, 붙잡아, 씻어, 좋아)이 있다.
     * 불규칙이면 아/어 앞에서 어간이 바뀐다 (들어, 도와, 나아, 파래).
     */
    private boolean regular(String s) {
        // 은·을은 보지 않는다: 명사 + 조사와 같은 꼴이 있다 (짓을 → 짓으면 X)
        if (medialEvidence(null, s + "으", null)[0] > 0) return true;
        for (String e : new String[]{"아", "어", "아서", "어서", "아요", "어요", "았", "었"}) if (dict.contains(s + e)) return true;
        return false;
    }

    private void put(Map<String, Double> pool, String word, String cand, double sc) {
        if (cand.equals(word) || !HangulRange.startsWith(cand, word) || dict.indexOf(cand) >= 0) return;
        Double old = pool.get(cand);
        if (old == null || old < sc) pool.put(cand, sc);
    }

    /** 어미 종류: 아/어 꼴 뒤(보여+줘서), 과거·추측 글자 뒤(했+는데, 하겠+더라), 어간 뒤(먹+더라고요). */
    static final int END_AO = 0, END_PAST = 1, END_STEM = 2;

    /** 내가 쓴 어미를 다른 말에도 붙일지 (평가용), 그러려면 몇 개의 앞 꼴에 붙여 썼어야 하는지, 종류마다 최대 몇 개. */
    static boolean LEARN_ENDINGS = true;

    static int END_MIN_BASES = 2, END_MAX = 30;

    /**
     * 학습할 때 (백그라운드): w를 (앞 꼴, 어미)로 나눠 쓴 기록에 남긴다. 앞 꼴은 사전으로 확인되는 것만 (아/어 꼴은 앞 꼴 + 서,
     * 과거형은 앞 꼴 + 어·다·고, 어간은 어간 + 고와 는·지·다가 사전에 있어야 한다). 사전의 명사(사람 → 사 + 람)는 나누지 않는다.
     */
    void learnEndings(String w, UsageHistory usage, long now) {
        int n = w.length();
        if (usage == null || n < 2 || n > 12 || KoreanMorph.hangulSyllables(w) != n) return;
        if (dict.contains(w) && morph.nounLike(w)) return;
        String[] sp = WordSuggester.splitParticle(w);   // 가방이 = 가방 + 이 (가 + 방이가 아니다)
        if (sp != null && dict.contains(sp[0])) return;
        for (int k = 1; k < n; k++) {
            String base = w.substring(0, k), tail = w.substring(k);
            if (tail.length() > 4) continue;
            char c = base.charAt(k - 1);
            int t = (c - 0xAC00) % 28, jung = (c - 0xAC00) % 588 / 28;
            boolean particle = WordSuggester.isParticle(tail);   // 가방이 → 가 + 방이, 하이 → 하 + 이: 어미가 아니다
            if (particle) continue;
            if (t == 0 && (jung == 0 || jung == 4 || jung == 6 || jung == 9 || jung == 14 || jung == 1 || jung == 10)
                    && AFTER_AO.indexOf(tail.charAt(0)) >= 0 && dict.contains(base + "서")) {
                usage.recordEnding(END_AO, base, tail, now);
            }
            if (t == 20 && (dict.contains(base + "어") || dict.contains(base + "다") || dict.contains(base + "고"))) {
                usage.recordEnding(END_PAST, base, tail, now);
            }
            if (dict.contains(base + "고")
                    && (dict.contains(base + "는") || dict.contains(base + "지") || dict.contains(base + "다"))) {
                usage.recordEnding(END_STEM, base, tail, now);
            }
        }
    }

    /**
     * w를 {종류, 앞 꼴, 어미}로 나눈다 ({@link #learnEndings}와 같은 기준으로, 어미가 가장 짧게). 나눌 수 없으면 null.
     * 추천 순위에서 내 어미 버릇을 다른 말에도 옮길 때 쓴다 ({@link KoreanSuggest#E_MAX}). 화면 스레드에서 부르며 결과를 담아 둔다.
     */
    String[] split(String w) {
        String[] c = splitCache.get(w);
        if (c != null) return c.length == 0 ? null : c;
        if (splitCache.size() >= 4096) splitCache.clear();
        String[] r = splitNow(w);
        splitCache.put(w, r == null ? new String[0] : r);
        return r;
    }

    private final Map<String, String[]> splitCache = new HashMap<>();

    private String[] splitNow(String w) {
        int n = w.length();
        if (n < 2 || n > 12 || KoreanMorph.hangulSyllables(w) != n) return null;
        for (int k = n - 1; k >= 1 && n - k <= 4; k--) {
            String base = w.substring(0, k), tail = w.substring(k);
            if (WordSuggester.isParticle(tail)) continue;   // 가방이 → 가 + 방이: 어미가 아니다
            int kind = kindOf(base, tail);
            if (kind < 0) continue;
            // 사전의 명사(사람 → 사 + 람)와 명사 + 조사(가방이 → 가 + 방이)는 나누지 않는다
            if (dict.contains(w) && morph.nounLike(w)) return null;
            String[] sp = WordSuggester.splitParticle(w);
            if (sp != null && dict.contains(sp[0])) return null;
            return new String[]{String.valueOf(kind), base, tail};
        }
        return null;
    }

    /** base + tail이 어미 종류 무엇의 활용인지 (사전으로 확인되는 것만, {@link #learnEndings}와 같은 기준). 아니면 -1. */
    private int kindOf(String base, String tail) {
        char c = base.charAt(base.length() - 1);
        int t = (c - 0xAC00) % 28, jung = (c - 0xAC00) % 588 / 28;
        if (t == 0 && (jung == 0 || jung == 4 || jung == 6 || jung == 9 || jung == 14 || jung == 1 || jung == 10)
                && AFTER_AO.indexOf(tail.charAt(0)) >= 0 && dict.contains(base + "서")) {
            return END_AO;
        }
        if (t == 20 && (dict.contains(base + "어") || dict.contains(base + "다") || dict.contains(base + "고"))) return END_PAST;
        if (dict.contains(base + "고") && (dict.contains(base + "는") || dict.contains(base + "지") || dict.contains(base + "다"))) {
            return END_STEM;
        }
        return -1;
    }

    /**
     * 아/어 꼴 뒤에 오는 말의 첫 글자 (먹어+서·도·요·야, 보조 용언 줘·봐·버리·놓·있·가·오·지 ...). 가·보내처럼 아/어 꼴이 어간과
     * 같은 말에서 어간 뒤 어미(가+는, 가+고)를 아/어 꼴 뒤 어미로 잘못 배우지 않게 한다.
     */
    private static final String AFTER_AO = "서도요야줘주준줄봐보볼봤버놓놔둬두있가갈갔와올왔오지진져졌치";

    /** 이 종류의 어미 목록: 앱에 든 것 + 내가 둘 이상의 말에 붙여 쓴 것 (앞에 없는 것만, 뒤에 붙인다). */
    private String[] endingsFor(KoreanSuggest.Context cx, int kind, String[] fixed) {
        if (!LEARN_ENDINGS || cx.usage == null) return fixed;
        String[][] cache = cx.learnedEndings;
        if (cache[kind] != null) return cache[kind];
        List<String> learned = cx.usage.endingsIfLoaded(kind, END_MIN_BASES, END_MAX);
        String[] out = fixed;
        if (!learned.isEmpty()) {
            List<String> all = new ArrayList<>(Arrays.asList(fixed));
            for (String e : learned) if (!all.contains(e)) all.add(e);
            out = all.toArray(new String[0]);
        }
        cache[kind] = out;
        return out;
    }

    /** 아/어 꼴(먹어, 해, 바꿔) 뒤에 붙이는 말. 사람이 쓴 대화 문장(평가에 쓰지 않는 절반)에서 흔한 순서. */
    static final String[] EO_ENDINGS = {"도", "서", "야지", "요", "줘", "볼까", "봐", "야할까", "야겠다", "야겠지", "있어",
            "야해", "야돼", "줬어", "야되는데", "야겠어", "놓고", "줘요", "줄래", "줄게", "주세요", "봐야지", "버렸어", "봤어", "보니까"};

    private static final String[] EO_EVIDENCE = {"도", "요", "야", "줘", "봐", "야지"};

    /**
     * 아/어 꼴 뒤의 말 (먹어 → 먹어야지, 먹어봐 / 해야 → 해야할까). 불규칙 활용(들어, 도와)은 사용자가 이미 친 꼴이라 따질
     * 필요가 없다. 사전에 그 꼴 + 서(먹어서)가 있고 다른 활용(먹어도, 먹어요 ...)도 하나 이상 있어야 용언으로 본다.
     */
    private void addEo(Map<String, Double> pool, KoreanSuggest.Context cx, String word, String x) {
        char last = x.charAt(x.length() - 1);
        if (last < 0xAC00 || last > 0xD7A3 || (last - 0xAC00) % 28 != 0) return;
        int jung = (last - 0xAC00) % 588 / 28;
        if (jung != 0 && jung != 4 && jung != 6 && jung != 9 && jung != 14 && jung != 1 && jung != 10) return;   // ㅏㅓㅕㅘㅝㅐㅙ
        int id = dict.indexOf(x + "서");
        if (id < 0) return;
        double fam = cx.score(x + "서", id);
        int found = 0;
        for (String e : EO_EVIDENCE) {
            int j = dict.indexOf(x + e);
            if (j < 0) continue;
            found++;
            fam += cx.score(x + e, j);
        }
        if (found == 0) return;
        emit(pool, word, x, endingsFor(cx, END_AO, EO_ENDINGS), false, fam);
    }

    /** -겠- 뒤에 붙이는 어미 (과거형 어미 중 겠 뒤에 자연스러운 것만). */
    private static final String[] WILL_ENDINGS = {"어", "지", "네", "다", "는데", "고", "니", "냐", "죠", "어요", "습니다",
            "네요", "지만", "다고"};

    /**
     * 받침 ㅆ으로 끝난 base(떨어졌, 먹었, 못하겠) 뒤의 어미. 사전에 base의 활용형이 없어도, 줄기 전의 어간(떨어지, 먹, 못하)이
     * 사전에 있으면 만든다. 사용자가 과거형 글자를 이미 쳤으니 불규칙 활용을 따질 필요가 없다.
     */
    private void addPast(Map<String, Double> pool, KoreanSuggest.Context cx, String word, String base) {
        double fam = family(cx, base, PAST_EVIDENCE, 2, null);
        char last = base.charAt(base.length() - 1);
        String prefix = base.substring(0, base.length() - 1);
        boolean will = last == '겠';
        if (fam <= 0) {
            for (String stem : KoreanMorph.presentStems(prefix, last)) {
                fam = family(cx, stem, STEM_EVIDENCE, 2, "고");
                if (fam > 0) break;
            }
        }
        emit(pool, word, base, endingsFor(cx, END_PAST, will ? WILL_ENDINGS : PAST_ENDINGS), false, fam);
        if (PAST_NEUN && !will) emit(pool, word, base, PAST_NEUN_ENDINGS, false, fam);
    }
}
