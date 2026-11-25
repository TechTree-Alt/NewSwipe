package com.alternative_studios.newswipe;

/**
 * Neumorphism 모드의 키음: 말랑한 버튼을 눌러 바닥에 닿는 느낌의 부드러운 "톡".
 * 세 겹을 섞어 만든다.
 * <ol>
 *   <li>몸통: 높이가 살짝 내려가며 둥글게 사라지는 사인파와 그 위의 속 빈 배음 (말랑한 표면을 누르는 소리)</li>
 *   <li>감촉: 1 ms 남짓한 아주 작은 잡음 (손끝에 닿는 순간, 고음은 깎아 날카롭지 않게)</li>
 *   <li>바닥: 몇 ms 뒤 더 낮고 작게 한 번 더 울리는 소리 (끝까지 눌려 바닥에 닿는 깊이감)</li>
 * </ol>
 * 휴대폰 스피커는 400 Hz 아래를 잘 못 내므로 몸통은 그보다 높게 두고, 배음으로 낮은 소리처럼 들리게 한다.
 * 마지막에 고음을 한 번 더 깎아 전체를 부드럽게 한다.
 */
final class NeuSound {
    private static final int RATE = 44100;

    private NeuSound() {
    }

    /** kind(SynthSound.KEY 등) 소리의 16비트 표본. */
    static short[] synth(int kind) {
        double[] buf;
        switch (kind) {
            case SynthSound.FUNCTION:   // 더 크고 낮은 버튼
                buf = press(64, 660, 440, 0.0105, 0.020, 0.0115, 300, 0.42);
                break;
            case SynthSound.DELETE:     // 조금 높고 짧게
                buf = press(50, 900, 600, 0.0070, 0.026, 0.0085, 430, 0.30);
                break;
            case SynthSound.ACCENT:     // 말풍선이 부풀어 오르는 둥근 소리
                buf = bloom();
                break;
            default:                    // 글자 키
                buf = press(56, 800, 520, 0.0085, 0.022, 0.0095, 370, 0.36);
                break;
        }
        return finish(buf);
    }

    /**
     * 누르는 소리 한 번.
     *
     * @param durMs    길이
     * @param f0       몸통이 시작하는 높이 (Hz)
     * @param f1       몸통이 가라앉는 높이 (Hz)
     * @param tau      몸통이 사라지는 시간 상수 (s)
     * @param tick     감촉 잡음의 세기
     * @param bottomAt 바닥에 닿는 때 (s)
     * @param fb       바닥 소리의 높이 (Hz)
     * @param bottom   바닥 소리의 세기
     */
    private static double[] press(int durMs, double f0, double f1, double tau, double tick,
                                  double bottomAt, double fb, double bottom) {
        int n = RATE * durMs / 1000;
        double[] buf = new double[n];
        java.util.Random rnd = new java.util.Random(31);
        double ph = 0, ph2 = 0, phb = 0, lp = 0;
        for (int i = 0; i < n; i++) {
            double t = (double) i / RATE;
            // 몸통: 1.5 ms 동안 둥글게(반 코사인) 올라와 딸깍 없이 시작한다.
            double f = f1 + (f0 - f1) * Math.exp(-t / 0.006);
            ph += 2 * Math.PI * f / RATE;
            ph2 += 2 * Math.PI * f * 2.02 / RATE;
            double rise = t < 0.0015 ? 0.5 - 0.5 * Math.cos(Math.PI * t / 0.0015) : 1;
            double body = rise * Math.exp(-t / tau) * (Math.sin(ph) + 0.22 * Math.sin(ph2) * Math.exp(-t / (tau * 0.45)));
            // 감촉: 고음을 깎은 잡음이 1 ms 남짓 안에 사라진다.
            double w = rnd.nextDouble() * 2 - 1;
            lp += 0.35 * (w - lp);
            double touch = tick * 30 * lp * Math.min(1, t / 0.0002) * Math.exp(-t / 0.0011);
            // 바닥: 바닥에 닿는 때부터 짧게 낮은 소리.
            double tb = t - bottomAt, floor = 0;
            if (tb >= 0) {
                phb += 2 * Math.PI * fb * (1 + 0.25 * Math.exp(-tb / 0.003)) / RATE;
                double rb = tb < 0.001 ? 0.5 - 0.5 * Math.cos(Math.PI * tb / 0.001) : 1;
                floor = bottom * rb * Math.exp(-tb / 0.007) * Math.sin(phb);
            }
            buf[i] = body + touch + floor;
        }
        return buf;
    }

    /** 악센트 말풍선: 높이가 둥글게 올라가는 부드러운 "뽁"과 그 5도 위의 옅은 울림. */
    private static double[] bloom() {
        int n = RATE * 110 / 1000;
        double[] buf = new double[n];
        double ph = 0, ph2 = 0;
        for (int i = 0; i < n; i++) {
            double t = (double) i / RATE;
            double f = 520 + 300 * (1 - Math.exp(-t / 0.018));
            ph += 2 * Math.PI * f / RATE;
            ph2 += 2 * Math.PI * f * 1.5 / RATE;
            double rise = t < 0.004 ? 0.5 - 0.5 * Math.cos(Math.PI * t / 0.004) : 1;
            buf[i] = rise * Math.exp(-t / 0.024) * (Math.sin(ph) + 0.18 * Math.sin(ph2) * Math.exp(-t / 0.015));
        }
        return buf;
    }

    /** 고음을 깎고(1극 저역 통과, 약 4.5 kHz), 끝 3 ms를 줄이고, 최대 0.9로 맞춰 16비트로. */
    private static short[] finish(double[] buf) {
        int n = buf.length;
        double a = 1 - Math.exp(-2 * Math.PI * 4500.0 / RATE), y = 0, peak = 0;
        int fade = RATE * 3 / 1000;
        for (int i = 0; i < n; i++) {
            y += a * (buf[i] - y);
            buf[i] = y * Math.min(1, (double) (n - 1 - i) / fade);
            peak = Math.max(peak, Math.abs(buf[i]));
        }
        double gain = peak > 0 ? 0.9 * 32767 / peak : 0;
        short[] out = new short[n];
        for (int i = 0; i < n; i++) out[i] = (short) Math.round(buf[i] * gain);
        return out;
    }
}
