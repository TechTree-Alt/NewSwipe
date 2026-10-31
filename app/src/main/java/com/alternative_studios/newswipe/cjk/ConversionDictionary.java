package com.alternative_studios.newswipe.cjk;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * 일본어·중국어 변환 사전: 읽기(중국어는 병음 음절을 '로 이은 것 "ni'hao", 일본어는 히라가나 "きょう")마다
 * 바꿔 쓸 글자들과 그 비용(작을수록 자주 쓴다)을 담는다. dict/build/build_cjk_pack.py가 만든다.
 *
 * <p>형식 (빅 엔디언):
 * <pre>
 *   "NSC1"  u32 키 수 N  u32 키 영역 길이  u32 값 영역 길이
 *   i32[N+1] 키 시작 위치   i32[N+1] 값 시작 위치
 *   키 영역: 읽기(UTF-8)를 바이트 순서로 정렬해 이어 붙인 것
 *   값 영역: 키마다 {u8 비용, u8 길이, 글자(UTF-8)}를 비용이 작은 순서로
 * </pre>
 * 사전 전체를 바이트 배열 두 개와 정수 배열 두 개로만 들고 있어 단어가 수십만 개여도 메모리를 적게 쓴다
 * (문자열 객체를 만들지 않는다). 안드로이드에 기대지 않는 순수 자바 클래스라 단위 테스트로 검증한다.
 */
public final class ConversionDictionary {
    static final int MAGIC = 0x4E534331;   // "NSC1"
    /** 받아들이는 크기 상한 (팩 하나는 수 MB이므로 넉넉하다). */
    static final int MAX_KEYS = 4_000_000, MAX_BLOB = 64 << 20;

    private final int[] keyOff, valOff;
    private final byte[] keys, vals;

    /** 글자 하나와 그 비용. */
    public static final class Entry {
        public final String text;
        public final int cost;

        Entry(String text, int cost) {
            this.text = text;
            this.cost = cost;
        }
    }

    private ConversionDictionary(int[] keyOff, int[] valOff, byte[] keys, byte[] vals) {
        this.keyOff = keyOff;
        this.valOff = valOff;
        this.keys = keys;
        this.vals = vals;
    }

    /** 사전을 읽는다. 형식이 맞지 않으면 IOException. */
    public static ConversionDictionary load(InputStream in) throws IOException {
        DataInputStream d = new DataInputStream(new java.io.BufferedInputStream(in, 32 * 1024));
        if (d.readInt() != MAGIC) throw new IOException("변환 사전 형식이 아닙니다");
        int n = d.readInt(), kl = d.readInt(), vl = d.readInt();
        if (n < 0 || n > MAX_KEYS || kl < 0 || kl > MAX_BLOB || vl < 0 || vl > MAX_BLOB) {
            throw new IOException("변환 사전의 크기가 잘못되었습니다");
        }
        int[] ko = new int[n + 1], vo = new int[n + 1];
        for (int i = 0; i <= n; i++) ko[i] = d.readInt();
        for (int i = 0; i <= n; i++) vo[i] = d.readInt();
        byte[] keys = new byte[kl], vals = new byte[vl];
        d.readFully(keys);
        d.readFully(vals);
        // 위치가 거꾸로 가거나 영역을 넘으면 깨진 파일이다 (찾는 도중에 배열 밖을 읽지 않게 여기서 막는다).
        if (ko[0] != 0 || vo[0] != 0 || ko[n] != kl || vo[n] != vl) throw new IOException("변환 사전이 깨졌습니다");
        for (int i = 0; i < n; i++) {
            if (ko[i] > ko[i + 1] || vo[i] > vo[i + 1]) throw new IOException("변환 사전이 깨졌습니다");
        }
        ConversionDictionary dict = new ConversionDictionary(ko, vo, keys, vals);
        for (int i = 0; i + 1 < n; i++) {
            if (dict.compareKeys(i, i + 1) >= 0) throw new IOException("변환 사전의 순서가 맞지 않습니다");
        }
        for (int i = 0; i < n; i++) dict.checkValues(i);
        return dict;
    }

    private void checkValues(int i) throws IOException {
        int p = valOff[i], end = valOff[i + 1];
        while (p < end) {
            if (p + 2 > end) throw new IOException("변환 사전이 깨졌습니다");
            int len = vals[p + 1] & 0xFF;
            p += 2 + len;
            if (len == 0 || p > end) throw new IOException("변환 사전이 깨졌습니다");
        }
    }

    public int size() {
        return keyOff.length - 1;
    }

    /** 사전이 쓰는 메모리 (바이트, 대략). */
    public long bytes() {
        return keys.length + vals.length + 8L * keyOff.length;
    }

    static byte[] utf8(String s) {
        return s.getBytes(StandardCharsets.UTF_8);
    }

    /** i번째 키와 q를 바이트 순서로 비교한다 (q가 i번째 키의 앞부분이면 음수). */
    private int compare(int i, byte[] q) {
        int a = keyOff[i], al = keyOff[i + 1] - a;
        int n = Math.min(al, q.length);
        for (int j = 0; j < n; j++) {
            int c = (keys[a + j] & 0xFF) - (q[j] & 0xFF);
            if (c != 0) return c;
        }
        return al - q.length;
    }

    private int compareKeys(int i, int j) {
        int a = keyOff[i], al = keyOff[i + 1] - a, b = keyOff[j], bl = keyOff[j + 1] - b;
        int n = Math.min(al, bl);
        for (int k = 0; k < n; k++) {
            int c = (keys[a + k] & 0xFF) - (keys[b + k] & 0xFF);
            if (c != 0) return c;
        }
        return al - bl;
    }

    /** q 이상인 첫 키의 번호 (없으면 size()). */
    public int lowerBound(byte[] q) {
        int lo = 0, hi = size();
        while (lo < hi) {
            int mid = (lo + hi) >>> 1;
            if (compare(mid, q) < 0) lo = mid + 1;
            else hi = mid;
        }
        return lo;
    }

    /** q와 똑같은 키의 번호. 없으면 -1. */
    public int find(byte[] q) {
        int i = lowerBound(q);
        return i < size() && compare(i, q) == 0 ? i : -1;
    }

    /** i번째 키가 q로 시작하는지. */
    public boolean startsWith(int i, byte[] q) {
        if (i < 0 || i >= size()) return false;
        int a = keyOff[i];
        if (keyOff[i + 1] - a < q.length) return false;
        for (int j = 0; j < q.length; j++) if (keys[a + j] != q[j]) return false;
        return true;
    }

    /** i번째 키가 q로 시작하고, 그 뒤가 비었거나 sep 바이트인지 (병음 음절 경계). */
    public boolean startsWithUnit(int i, byte[] q, byte sep) {
        if (!startsWith(i, q)) return false;
        int len = keyOff[i + 1] - keyOff[i];
        return len == q.length || keys[keyOff[i] + q.length] == sep;
    }

    /** q로 시작하는 키가 하나라도 있는지. */
    public boolean hasPrefix(byte[] q) {
        return startsWith(lowerBound(q), q);
    }

    /** i번째 키의 읽기. */
    public String key(int i) {
        return new String(keys, keyOff[i], keyOff[i + 1] - keyOff[i], StandardCharsets.UTF_8);
    }

    /** i번째 키의 가장 작은 비용. */
    public int bestCost(int i) {
        return valOff[i] < valOff[i + 1] ? vals[valOff[i]] & 0xFF : 255;
    }

    /** i번째 키의 글자들을 비용 순서로 out에 더한다 (max개까지). */
    public void entries(int i, List<Entry> out, int max) {
        int p = valOff[i], end = valOff[i + 1], n = 0;
        while (p < end && n < max) {
            int cost = vals[p] & 0xFF, len = vals[p + 1] & 0xFF;
            out.add(new Entry(new String(vals, p + 2, len, StandardCharsets.UTF_8), cost));
            p += 2 + len;
            n++;
        }
    }

    /** i번째 키의 첫(가장 자주 쓰는) 글자. */
    public String firstText(int i) {
        int p = valOff[i];
        return new String(vals, p + 2, vals[p + 1] & 0xFF, StandardCharsets.UTF_8);
    }

    // ---------------------------------------------------------------- 만들기 (테스트·도구용)

    /** 정렬된 키와 값으로 사전 파일을 만든다 (단위 테스트에서 쓴다). 키는 서로 달라야 한다. */
    static byte[] build(java.util.Map<String, List<Entry>> data) {
        java.util.List<byte[]> ks = new java.util.ArrayList<>();
        for (String k : data.keySet()) ks.add(utf8(k));
        ks.sort((x, y) -> {
            int n = Math.min(x.length, y.length);
            for (int i = 0; i < n; i++) {
                int c = (x[i] & 0xFF) - (y[i] & 0xFF);
                if (c != 0) return c;
            }
            return x.length - y.length;
        });
        java.io.ByteArrayOutputStream kb = new java.io.ByteArrayOutputStream(), vb = new java.io.ByteArrayOutputStream();
        int[] ko = new int[ks.size() + 1], vo = new int[ks.size() + 1];
        for (int i = 0; i < ks.size(); i++) {
            byte[] k = ks.get(i);
            kb.write(k, 0, k.length);
            java.util.List<Entry> es = new java.util.ArrayList<>(data.get(new String(k, StandardCharsets.UTF_8)));
            es.sort((a, b) -> a.cost - b.cost);
            for (Entry e : es) {
                byte[] t = utf8(e.text);
                vb.write(Math.max(0, Math.min(255, e.cost)));
                vb.write(t.length);
                vb.write(t, 0, t.length);
            }
            ko[i + 1] = kb.size();
            vo[i + 1] = vb.size();
        }
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        try (java.io.DataOutputStream d = new java.io.DataOutputStream(out)) {
            d.writeInt(MAGIC);
            d.writeInt(ks.size());
            d.writeInt(kb.size());
            d.writeInt(vb.size());
            for (int v : ko) d.writeInt(v);
            for (int v : vo) d.writeInt(v);
            kb.writeTo(d);
            vb.writeTo(d);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        return out.toByteArray();
    }

    static Entry entry(String text, int cost) {
        return new Entry(text, cost);
    }
}
