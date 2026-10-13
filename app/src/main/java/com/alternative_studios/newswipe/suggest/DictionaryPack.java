package com.alternative_studios.newswipe.suggest;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Properties;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * 사전 팩: 앱에 들어 있는 기본 사전 대신 쓰는 한국어 사전. 사용자가 파일로 받아 설정에서 가져온다 (앱은 인터넷을 쓰지 않는다).
 * 팩은 zip 파일 하나이며 다음을 담는다 (tools/make_dict_pack.py가 만든다).
 * <ul>
 *   <li>{@code manifest.properties} (필수, UTF-8): format, lang=ko, name, version, description, license, attribution,
 *       sha256.dict_ko.bin, sha256.dict_ko_next.bin</li>
 *   <li>{@code dict_ko.bin} (필수): 단어 사전 ({@link WordDictionary})</li>
 *   <li>{@code dict_ko_next.bin} (선택): 다음 단어 사전 ({@link NextWords}, 단어 사전과 짝이 맞아야 한다)</li>
 *   <li>{@code LICENSE.txt} (선택): 데이터의 라이선스 전문</li>
 * </ul>
 * 남이 만든 파일을 읽으므로 이름이 정해진 항목만 꺼내고, 크기에 상한을 두고, 다 읽어 본 뒤에만 바꿔 넣는다.
 * 안드로이드에 기대지 않는 순수 자바 클래스라 단위 테스트로 검증한다.
 */
public final class DictionaryPack {
    /** 이 앱이 읽을 수 있는 가장 높은 팩 형식. */
    public static final int FORMAT = 1;
    static final String MANIFEST = "manifest.properties", DICT = "dict_ko.bin", NEXT = "dict_ko_next.bin",
            LICENSE = "LICENSE.txt";
    /** 항목별 크기 상한 (단어 사전의 글자 영역 상한 16MB, 단어 쌍 400만 개 정도). */
    static final long MAX_DICT = 17L << 20, MAX_NEXT = 40L << 20, MAX_TEXT = 256L << 10;
    private static final String DIR = "dictpack_ko";

    /** 팩을 가져올 수 없는 이유 (사용자에게 그대로 보여 준다). */
    public static final class InvalidPackException extends IOException {
        InvalidPackException(String message) {
            super(message);
        }
    }

    /** 설치된 팩의 정보. */
    public static final class Info {
        public final String name, version, description, license, attribution, licenseText;
        public final int words, pairs;
        public final long bytes;

        Info(Properties p, String licenseText, long bytes) {
            name = p.getProperty("name", "이름 없는 사전");
            version = p.getProperty("version", "");
            description = p.getProperty("description", "");
            license = p.getProperty("license", "");
            attribution = p.getProperty("attribution", "");
            this.licenseText = licenseText;
            words = parseInt(p.getProperty("words"));
            pairs = parseInt(p.getProperty("pairs"));
            this.bytes = bytes;
        }
    }

    /** 읽어 들인 사전 (next는 없을 수 있다). */
    static final class Loaded {
        final WordDictionary dict;
        final NextWords next;

        Loaded(WordDictionary dict, NextWords next) {
            this.dict = dict;
            this.next = next;
        }
    }

    /** 설치·삭제할 때마다 올린다. 같은 프로세스의 키보드가 사전을 다시 읽어야 하는지 아는 데 쓴다. */
    private static volatile int changes;
    private static final Object LOCK = new Object();

    private DictionaryPack() {
    }

    public static int changes() {
        return changes;
    }

    static File dir(File filesDir) {
        return new File(filesDir, DIR);
    }

    /**
     * zip을 읽어 검사한 뒤 설치한다 (이미 있던 팩은 바꾼다). 백그라운드 스레드에서 부른다.
     * 받아들일 수 없는 팩이면 {@link InvalidPackException}을 던지고 지금 팩은 그대로 둔다.
     */
    public static Info install(InputStream zip, File filesDir) throws IOException {
        synchronized (LOCK) {
            File tmp = new File(filesDir, DIR + ".tmp");
            deleteTree(tmp);
            if (!tmp.mkdirs()) throw new IOException("임시 폴더를 만들지 못했습니다");
            try {
                Info info = unpackAndCheck(zip, tmp);
                File dir = dir(filesDir), old = new File(filesDir, DIR + ".old");
                deleteTree(old);
                if (dir.exists() && !dir.renameTo(old)) throw new IOException("지금 사전을 옮기지 못했습니다");
                if (!tmp.renameTo(dir)) {
                    old.renameTo(dir);   // 되돌린다
                    throw new IOException("사전을 설치하지 못했습니다");
                }
                deleteTree(old);
                changes++;
                return info;
            } finally {
                deleteTree(tmp);
            }
        }
    }

    private static Info unpackAndCheck(InputStream zip, File into) throws IOException {
        boolean manifest = false, dict = false, next = false, license = false;
        try (ZipInputStream z = new ZipInputStream(new java.io.BufferedInputStream(zip, 16 * 1024))) {
            ZipEntry e;
            while ((e = z.getNextEntry()) != null) {
                if (e.isDirectory()) continue;
                String name = e.getName();
                long max;
                switch (name) {   // 폴더 안의 파일이나 다른 이름은 꺼내지 않는다 (경로를 따라가지 않는다)
                    case MANIFEST: max = MAX_TEXT; manifest = check(manifest, name); break;
                    case DICT: max = MAX_DICT; dict = check(dict, name); break;
                    case NEXT: max = MAX_NEXT; next = check(next, name); break;
                    case LICENSE: max = MAX_TEXT; license = check(license, name); break;
                    default: continue;
                }
                copyLimited(z, new File(into, name), max);
            }
        } catch (java.util.zip.ZipException ex) {
            throw new InvalidPackException("사전 팩(zip) 파일이 아닙니다");
        }
        if (!manifest) throw new InvalidPackException("사전 팩 정보(" + MANIFEST + ")가 없습니다");
        if (!dict) throw new InvalidPackException("단어 사전(" + DICT + ")이 없습니다");

        Properties p = readProperties(new File(into, MANIFEST));
        int format = parseInt(p.getProperty("format"));
        if (format <= 0) throw new InvalidPackException("사전 팩 형식을 알 수 없습니다");
        if (format > FORMAT) throw new InvalidPackException("이 버전의 앱이 읽을 수 없는 새 형식입니다. 앱을 업데이트해 주세요");
        if (!"ko".equals(p.getProperty("lang"))) throw new InvalidPackException("한국어 사전 팩이 아닙니다");
        verifyHash(p, new File(into, DICT));
        if (next) verifyHash(p, new File(into, NEXT));

        Loaded l;
        try {
            l = read(into, true);
        } catch (IOException | RuntimeException ex) {
            throw new InvalidPackException("사전 파일이 깨졌거나 단어 사전과 다음 단어 사전의 짝이 맞지 않습니다");
        }
        // 화면에 보일 수는 실제로 읽은 값으로 적어 둔다.
        p.setProperty("words", String.valueOf(l.dict.size()));
        p.setProperty("pairs", String.valueOf(l.next == null ? 0 : l.next.pairs()));
        try (OutputStream out = new FileOutputStream(new File(into, MANIFEST))) {
            p.store(new OutputStreamWriter(out, StandardCharsets.UTF_8), null);
        }
        String text = license ? readText(new File(into, LICENSE)) : "";
        return new Info(p, text, size(into));
    }

    private static boolean check(boolean seen, String name) throws InvalidPackException {
        if (seen) throw new InvalidPackException(name + "이(가) 두 번 들어 있습니다");
        return true;
    }

    private static void copyLimited(InputStream in, File to, long max) throws IOException {
        byte[] buf = new byte[16 * 1024];
        long total = 0;
        try (OutputStream out = new FileOutputStream(to)) {
            for (int n; (n = in.read(buf)) > 0; ) {
                total += n;
                if (total > max) throw new InvalidPackException(to.getName() + "이(가) 너무 큽니다");
                out.write(buf, 0, n);
            }
        }
    }

    private static void verifyHash(Properties p, File f) throws IOException {
        String want = p.getProperty("sha256." + f.getName());
        if (want == null || want.isEmpty()) return;   // 직접 만든 팩은 해시를 적지 않아도 된다
        if (!want.trim().equalsIgnoreCase(sha256(f))) {
            throw new InvalidPackException(f.getName() + "이(가) 손상되었습니다 (해시가 다릅니다)");
        }
    }

    static String sha256(File f) throws IOException {
        MessageDigest md;
        try {
            md = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IOException(e);
        }
        try (InputStream in = new FileInputStream(f)) {
            byte[] buf = new byte[16 * 1024];
            for (int n; (n = in.read(buf)) > 0; ) md.update(buf, 0, n);
        }
        StringBuilder sb = new StringBuilder();
        for (byte b : md.digest()) sb.append(String.format("%02x", b & 0xFF));
        return sb.toString();
    }

    /** 설치된 팩의 정보. 없으면 null. 화면 스레드에서 불러도 된다 (사전 파일은 읽지 않는다). */
    public static Info installed(File filesDir) {
        File dir = dir(filesDir);
        File m = new File(dir, MANIFEST);
        if (!m.isFile() || !new File(dir, DICT).isFile()) return null;
        try {
            File lic = new File(dir, LICENSE);
            return new Info(readProperties(m), lic.isFile() ? readText(lic) : "", size(dir));
        } catch (IOException e) {
            return null;
        }
    }

    /** 설치된 팩을 지우고 기본 사전으로 돌아간다. */
    public static void remove(File filesDir) {
        synchronized (LOCK) {
            deleteTree(dir(filesDir));
            changes++;
        }
    }

    /** 설치된 팩의 사전을 읽는다. 없거나 읽을 수 없으면 null (그때는 기본 사전을 쓴다). 백그라운드 스레드에서 부른다. */
    static Loaded load(File filesDir) {
        synchronized (LOCK) {
            File dir = dir(filesDir);
            if (!new File(dir, DICT).isFile()) return null;
            try {
                return read(dir, false);
            } catch (IOException | RuntimeException e) {
                return null;
            }
        }
    }

    /** strictNext: 다음 단어 사전이 있는데 읽지 못하면 실패로 본다 (가져올 때). 아니면 없는 것처럼 쓴다. */
    private static Loaded read(File dir, boolean strictNext) throws IOException {
        WordDictionary d;
        try (InputStream in = new FileInputStream(new File(dir, DICT))) {
            d = WordDictionary.load(in);
        }
        NextWords n = null;
        File nf = new File(dir, NEXT);
        if (nf.isFile()) {
            try (InputStream in = new FileInputStream(nf)) {
                n = NextWords.load(in, d.size());
            } catch (IOException | RuntimeException e) {
                if (strictNext) throw e;
            }
        }
        return new Loaded(d, n);
    }

    private static Properties readProperties(File f) throws IOException {
        Properties p = new Properties();
        try (InputStreamReader r = new InputStreamReader(new FileInputStream(f), StandardCharsets.UTF_8)) {
            p.load(r);
        } catch (IllegalArgumentException e) {
            throw new InvalidPackException("사전 팩 정보(" + MANIFEST + ")를 읽지 못했습니다");
        }
        return p;
    }

    private static String readText(File f) throws IOException {
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        try (InputStream in = new FileInputStream(f)) {
            byte[] buf = new byte[8192];
            for (int n; (n = in.read(buf)) > 0; ) b.write(buf, 0, n);
        }
        return new String(b.toByteArray(), StandardCharsets.UTF_8);
    }

    private static int parseInt(String s) {
        try {
            return s == null ? 0 : Integer.parseInt(s.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static long size(File dir) {
        long n = 0;
        File[] fs = dir.listFiles();
        if (fs != null) for (File f : fs) n += f.length();
        return n;
    }

    private static void deleteTree(File f) {
        File[] fs = f.listFiles();
        if (fs != null) for (File c : fs) deleteTree(c);
        f.delete();
    }
}
