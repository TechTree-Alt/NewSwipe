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
    }

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
            if (dropRieul && (e.startsWith("네") || e.startsWith("니"))) {
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
    }
}
