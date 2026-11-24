package com.alternative_studios.newswipe;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.SoundPool;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Metro 모드의 키음: Windows Phone 7 키보드 소리를 본뜬 소리.
 * 녹음 파일을 앱에 넣지 않고, 원래 소리를 분석해 얻은 감쇠 사인파(주파수·감쇠·세기·위상) 몇 개를 더해 처음 쓸 때 만든다.
 * 그래서 앱 용량은 표 몇 개(1 KB 남짓)만 늘어난다.
 * 시스템 키음과 같은 시스템 소리 볼륨을 따르므로 무음·진동 모드에서는 울리지 않는다.
 */
final class MetroSound {
    /** 글자 키 / 기능 키(Shift·엔터·기호·스페이스 등) / 지우기 / 길게 눌러 여는 악센트 말풍선. */
    static final int KEY = 0, FUNCTION = 1, DELETE = 2, ACCENT = 3;
    private static final int COUNT = 4;
    private static final int RATE = 44100;
    /** 소리를 바꾸면 올려서 예전에 만들어 둔 파일을 다시 만든다. */
    private static final int VERSION = 2;

    /*
     * 소리마다 감쇠 사인파의 표: {주파수 Hz, 감쇠 1/s, 세기, 위상}의 반복.
     * 표본 n의 값은 세기 · e^(-감쇠 · n / RATE) · cos(2π · 주파수 · n / RATE - 위상)의 합 (최대 1 = 0 dBFS).
     */
    private static final double[] KEY0 = {
            713.2, 595.9, 2.1419, -1.560,
            591.1, 357.4, 1.5086, 1.516,
            1294.5, 1017.4, 0.3267, 1.978,
            650.3, 79.8, 0.1164, 1.498,
            870.5, 198.9, 0.0962, -1.405,
            299.6, 251.9, 0.0519, 2.667,
            1075.3, 116.6, 0.0204, 1.134,
            515.3, 55.9, 0.0195, -2.775,
            720.6, 0.0, 0.0103, -0.619,
            1003.3, 17.0, 0.0063, -0.328
    };
    private static final double[] FUNCTION0 = {
            931.4, 711.7, 1.9672, -1.380,
            762.9, 466.4, 1.4234, 1.602,
            1684.5, 1334.7, 0.3406, 2.365,
            1129.3, 278.2, 0.1219, -1.185,
            852.7, 68.4, 0.0804, 2.265,
            388.5, 341.8, 0.0565, 2.809,
            671.3, 77.6, 0.0222, -2.522,
            1392.6, 114.4, 0.0163, 1.295,
            1301.8, 22.1, 0.0072, -0.154,
            983.1, 0.0, 0.0046, -0.446
    };
    private static final double[] DELETE0 = {
            956.1, 285.8, 0.9973, 2.131,
            758.8, 302.3, 0.7410, -1.348,
            971.4, 164.0, 0.3263, -0.277,
            1326.9, 484.1, 0.1831, -2.736,
            860.9, 115.7, 0.1517, 0.028,
            1748.1, 567.7, 0.0920, -0.895,
            1117.3, 214.5, 0.0915, 2.033,
            443.4, 340.8, 0.0912, 0.756,
            643.3, 150.5, 0.0412, 0.336,
            1547.8, 184.5, 0.0245, -2.536,
            286.0, 79.2, 0.0076, 2.423,
            1260.7, 58.7, 0.0074, -0.602
    };
    private static final double[] ACCENT0 = {
            1002.9, 156.5, 0.1842, -0.635,
            956.4, 123.8, 0.1541, 1.704,
            1086.4, 76.3, 0.0522, -1.941,
            1175.2, 48.2, 0.0250, -2.905,
            876.4, 60.0, 0.0222, 2.473,
            1214.5, 80.8, 0.0185, 2.468,
            1132.9, 28.9, 0.0174, -2.097,
            830.7, 44.8, 0.0136, 2.993,
            701.8, 72.4, 0.0131, -2.582,
            1288.8, 36.0, 0.0069, 2.120,
            577.0, 35.9, 0.0060, 2.988,
            799.1, 17.8, 0.0031, -0.212
    };
    private static final double[] ACCENT1 = {
            1084.0, 1447.0, 6.8938, 0.820,
            990.4, 1424.1, 5.4537, -2.373,
            1420.3, 589.7, 1.3704, -2.140,
            2096.0, 905.4, 0.1650, 1.823,
            2917.3, 1286.7, 0.0988, -0.800,
            1548.1, 142.1, 0.0294, 3.037,
            2456.2, 612.5, 0.0293, 2.201,
            3807.3, 1575.2, 0.0291, -2.559,
            466.9, 344.2, 0.0168, 2.656,
            1900.0, 284.5, 0.0128, -0.932,
            922.1, 47.8, 0.0121, -0.998
    };

    /** 소리마다 조각들: {표, 시작 표본, 길이}. 악센트 소리는 낮게 웅웅거리는 앞부분과 또렷한 끝 두 조각이다. */
    private static final Object[][][] PARTS = {
            {{KEY0, 0, 1346}},
            {{FUNCTION0, 0, 1063}},
            {{DELETE0, 0, 1024}},
            {{ACCENT0, 0, 2131}, {ACCENT1, 2131, 459}},
    };

    private final Context context;
    private SoundPool pool;
    private final int[] ids = new int[COUNT];
    private final boolean[] loaded = new boolean[COUNT];

    MetroSound(Context context) {
        this.context = context.getApplicationContext();
    }

    /** 소리를 만들고 읽어 둔다 (이미 읽었으면 아무것도 하지 않는다). */
    void prepare() {
        if (pool != null) return;
        pool = new SoundPool.Builder()
                .setMaxStreams(4)
                .setAudioAttributes(new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build())
                .build();
        pool.setOnLoadCompleteListener((p, sampleId, status) -> {
            for (int i = 0; i < COUNT; i++) {
                if (ids[i] == sampleId) loaded[i] = status == 0;
            }
        });
        File dir = new File(context.getCacheDir(), "metro_sound");
        File[] old = dir.listFiles();
        if (old != null) {   // 예전 판의 파일은 지운다
            for (File f : old) {
                if (!f.getName().endsWith("_v" + VERSION + ".wav")) //noinspection ResultOfMethodCallIgnored
                    f.delete();
            }
        }
        //noinspection ResultOfMethodCallIgnored
        dir.mkdirs();
        for (int i = 0; i < COUNT; i++) {
            File f = new File(dir, "key" + i + "_v" + VERSION + ".wav");
            try {
                if (!f.isFile() || f.length() == 0) writeWav(f, synth(i));
                ids[i] = pool.load(f.getPath(), 1);
            } catch (IOException | RuntimeException e) {
                ids[i] = 0;
            }
        }
    }

    void play(int kind, float volume) {
        if (pool == null) prepare();
        if (kind < 0 || kind >= COUNT || !loaded[kind] || volume <= 0) return;
        float v = Math.min(1f, volume);
        pool.play(ids[kind], v, v, 1, 0, 1f);
    }

    void release() {
        if (pool != null) {
            pool.release();
            pool = null;
        }
        for (int i = 0; i < COUNT; i++) {
            ids[i] = 0;
            loaded[i] = false;
        }
    }

    /** kind(KEY 등) 소리의 16비트 표본. */
    static short[] synth(int kind) {
        Object[][] parts = PARTS[kind];
        Object[] last = parts[parts.length - 1];
        int n = (int) last[1] + (int) last[2];
        double[] buf = new double[n];
        for (Object[] part : parts) {
            double[] m = (double[]) part[0];
            int start = (int) part[1], len = (int) part[2];
            for (int k = 0; k + 3 < m.length; k += 4) {
                double w = 2 * Math.PI * m[k] / RATE, r = Math.exp(-m[k + 1] / RATE), amp = m[k + 2], ph = m[k + 3];
                double e = amp;
                for (int i = 0; i < len; i++) {
                    buf[start + i] += e * Math.cos(w * i - ph);
                    e *= r;
                }
            }
        }
        double peak = 0;
        for (double v : buf) peak = Math.max(peak, Math.abs(v));
        double gain = 32767 * (peak > 0.95 ? 0.95 / peak : 1);
        int fade = RATE * 2 / 1000;   // 끝 2 ms는 서서히 줄여 뚝 끊기는 소리를 막는다
        short[] out = new short[n];
        for (int i = 0; i < n; i++) {
            double tail = Math.min(1, (double) (n - 1 - i) / fade);
            out[i] = (short) Math.round(buf[i] * tail * gain);
        }
        return out;
    }

    private static void writeWav(File f, short[] pcm) throws IOException {
        int dataLen = pcm.length * 2;
        ByteBuffer b = ByteBuffer.allocate(44 + dataLen).order(ByteOrder.LITTLE_ENDIAN);
        b.put(new byte[]{'R', 'I', 'F', 'F'}).putInt(36 + dataLen).put(new byte[]{'W', 'A', 'V', 'E'});
        b.put(new byte[]{'f', 'm', 't', ' '}).putInt(16).putShort((short) 1).putShort((short) 1)
                .putInt(RATE).putInt(RATE * 2).putShort((short) 2).putShort((short) 16);
        b.put(new byte[]{'d', 'a', 't', 'a'}).putInt(dataLen);
        for (short s : pcm) b.putShort(s);
        File tmp = new File(f.getPath() + ".tmp");
        try (FileOutputStream o = new FileOutputStream(tmp)) {
            o.write(b.array());
        }
        if (!tmp.renameTo(f)) throw new IOException("rename failed");
    }
}
