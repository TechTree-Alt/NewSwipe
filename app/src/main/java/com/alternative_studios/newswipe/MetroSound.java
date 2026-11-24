package com.alternative_studios.newswipe;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.SoundPool;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Random;

/**
 * Metro 모드의 키음: Windows Phone 키보드처럼 짧고 부드러운 "톡" 소리.
 * 녹음 파일을 쓰지 않고 처음 쓸 때 직접 만든다 (높이가 살짝 내려가며 빨리 사라지는 사인파 + 아주 짧은 잡음 어택).
 * 일반 키·지우기·스페이스·엔터는 높이와 길이를 조금씩 달리한다.
 * 시스템 키음과 같은 시스템 소리 볼륨을 따르므로 무음·진동 모드에서는 울리지 않는다.
 */
final class MetroSound {
    static final int STANDARD = 0, DELETE = 1, SPACE = 2, ENTER = 3;
    private static final int RATE = 44100;
    /** 소리를 바꾸면 올려서 예전에 만들어 둔 파일을 다시 만든다. */
    private static final int VERSION = 1;

    private final Context context;
    private SoundPool pool;
    private final int[] ids = new int[4];
    private final boolean[] loaded = new boolean[4];

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
            for (int i = 0; i < ids.length; i++) {
                if (ids[i] == sampleId) loaded[i] = status == 0;
            }
        });
        File dir = new File(context.getCacheDir(), "metro_sound");
        //noinspection ResultOfMethodCallIgnored
        dir.mkdirs();
        for (int i = 0; i < ids.length; i++) {
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
        if (kind < 0 || kind >= ids.length || !loaded[kind] || volume <= 0) return;
        float v = Math.min(1f, volume);
        pool.play(ids[kind], v, v, 1, 0, 1f);
    }

    void release() {
        if (pool != null) {
            pool.release();
            pool = null;
        }
        for (int i = 0; i < ids.length; i++) {
            ids[i] = 0;
            loaded[i] = false;
        }
    }

    /**
     * 키 하나의 소리. 주파수가 f0에서 f1로 짧게 미끄러지는 몸통(나무·플라스틱을 톡 두드린 느낌),
     * 그 한 옥타브 반 위의 얇은 배음, 1~2 ms의 거친 잡음 어택을 섞고 지수적으로 사라지게 한다.
     */
    static short[] synth(int kind) {
        double f0, f1, tau, durMs, noise, overtone;
        switch (kind) {
            case DELETE:   // 조금 낮고 짧게
                f0 = 1250; f1 = 820; tau = 0.0050; durMs = 38; noise = 0.30; overtone = 0.22;
                break;
            case SPACE:    // 낮고 둥글게
                f0 = 900; f1 = 560; tau = 0.0070; durMs = 50; noise = 0.22; overtone = 0.16;
                break;
            case ENTER:    // 가장 낮고 조금 길게
                f0 = 760; f1 = 470; tau = 0.0090; durMs = 60; noise = 0.20; overtone = 0.14;
                break;
            default:       // 일반 키
                f0 = 1500; f1 = 980; tau = 0.0055; durMs = 40; noise = 0.32; overtone = 0.25;
                break;
        }
        int n = (int) (RATE * durMs / 1000);
        double[] buf = new double[n];
        Random rnd = new Random(17 + kind);
        double phase = 0, phase2 = 0, prevNoise = 0, peak = 0;
        double glide = 0.004;   // 높이가 내려가는 시간 (s)
        for (int i = 0; i < n; i++) {
            double t = (double) i / RATE;
            double f = f1 + (f0 - f1) * Math.exp(-t / glide);
            phase += 2 * Math.PI * f / RATE;
            phase2 += 2 * Math.PI * f * 2.6 / RATE;
            double attack = Math.min(1, t / 0.0004);              // 0.4 ms 동안 올라와 딸깍 소리를 막는다
            double env = attack * Math.exp(-t / tau);
            double body = Math.sin(phase) + overtone * Math.sin(phase2) * Math.exp(-t / (tau * 0.5));
            // 고역만 남긴 잡음 (앞뒤 차이), 1.5 ms 안에 사라진다.
            double w = rnd.nextDouble() * 2 - 1;
            double hp = w - prevNoise;
            prevNoise = w;
            double click = noise * hp * attack * Math.exp(-t / 0.0015);
            double s = body * env + click;
            // 끝 3 ms는 서서히 줄여 뚝 끊기지 않게 한다.
            double tail = Math.min(1, (n - 1 - i) / (RATE * 0.003));
            buf[i] = s * tail;
            peak = Math.max(peak, Math.abs(buf[i]));
        }
        short[] out = new short[n];
        double gain = peak > 0 ? 0.85 * 32767 / peak : 0;
        for (int i = 0; i < n; i++) out[i] = (short) Math.round(buf[i] * gain);
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
