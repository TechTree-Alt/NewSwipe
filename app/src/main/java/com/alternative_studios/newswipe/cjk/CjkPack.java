package com.alternative_studios.newswipe.cjk;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Properties;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * 일본어·중국어 변환 팩. 앱에는 엔진만 들어 있고, 사전은 사용자가 파일로 받아 실험실에서 가져온다 (앱은 인터넷을 쓰지 않는다).
 * 팩이 없으면 그 언어의 자판을 켤 수 없다. zip 하나에 다음을 담는다 (dict/build/build_cjk_pack.py가 만든다).
 * <ul>
 *   <li>{@code manifest.properties} (필수, UTF-8): format, lang=ja|zh, name, version, description, license,
 *       attribution, sha256.convert.bin</li>
 *   <li>{@code convert.bin} (필수): 변환 사전 ({@link ConversionDictionary})</li>
 *   <li>{@code LICENSE.txt} (선택): 데이터의 라이선스 전문</li>
 * </ul>
 * 남이 만든 파일을 읽으므로 이름이 정해진 항목만 꺼내고, 크기에 상한을 두고, 다 읽어 본 뒤에만 바꿔 넣는다.
 */
public final class CjkPack {
    public static final int FORMAT = 1;
    static final String MANIFEST = "manifest.properties", DICT = "convert.bin", LICENSE = "LICENSE.txt";
    static final long MAX_DICT = 48L << 20, MAX_TEXT = 256L << 10;

    public static final class InvalidPackException extends IOException {
        InvalidPackException(String message) {
            super(message);
        }
    }

    /** 설치된 팩의 정보. */
    public static final class Info {
        public final String lang, name, version, description, license, attribution, licenseText;
        public final int words;
        public final long bytes;

        Info(Properties p, String licenseText, long bytes) {
            lang = p.getProperty("lang", "");
            name = p.getProperty("name", "이름 없는 사전");
            version = p.getProperty("version", "");
            description = p.getProperty("description", "");
            license = p.getProperty("license", "");
            attribution = p.getProperty("attribution", "");
            this.licenseText = licenseText;
            words = parseInt(p.getProperty("words"));
            this.bytes = bytes;
        }
    }

    private static volatile int changes;
    private static final Object LOCK = new Object();

    private CjkPack() {
    }

    /** 설치·삭제할 때마다 오른다. 키보드가 사전을 다시 읽어야 하는지 아는 데 쓴다. */
    public static int changes() {
        return changes;
    }

    /** 언어 이름 (화면용). */
    public static String languageName(String lang) {
        return CjkComposer.JA.equals(lang) ? "일본어" : CjkComposer.ZH.equals(lang) ? "중국어" : lang;
    }

    static boolean supported(String lang) {
        return CjkComposer.JA.equals(lang) || CjkComposer.ZH.equals(lang);
    }

    static File dir(File filesDir, String lang) {
        return new File(filesDir, "cjkpack_" + lang);
    }

    /**
     * zip을 검사해 설치한다 (같은 언어의 팩이 있으면 바꾼다). expectLang이 null이 아니면 그 언어의 팩만 받는다.
     * 백그라운드 스레드에서 부른다.
     */
    public static Info install(InputStream zip, File filesDir, String expectLang) throws IOException {
        synchronized (LOCK) {
            File tmp = new File(filesDir, "cjkpack.tmp");
            deleteTree(tmp);
            if (!tmp.mkdirs()) throw new IOException("임시 폴더를 만들지 못했습니다");
            try {
                Info info = unpackAndCheck(zip, tmp, expectLang);
                File dir = dir(filesDir, info.lang), old = new File(filesDir, "cjkpack.old");
                deleteTree(old);
                if (dir.exists() && !dir.renameTo(old)) throw new IOException("지금 사전을 옮기지 못했습니다");
                if (!tmp.renameTo(dir)) {
                    old.renameTo(dir);
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

    private static Info unpackAndCheck(InputStream zip, File into, String expectLang) throws IOException {
        boolean manifest = false, dict = false, license = false;
        try (ZipInputStream z = new ZipInputStream(new java.io.BufferedInputStream(zip, 16 * 1024))) {
            ZipEntry e;
            while ((e = z.getNextEntry()) != null) {
                if (e.isDirectory()) continue;
                String name = e.getName();
                long max;
                switch (name) {   // 정해진 이름만 꺼낸다 (경로를 따라가지 않는다)
                    case MANIFEST: max = MAX_TEXT; manifest = check(manifest, name); break;
                    case DICT: max = MAX_DICT; dict = check(dict, name); break;
                    case LICENSE: max = MAX_TEXT; license = check(license, name); break;
                    default: continue;
                }
                copyLimited(z, new File(into, name), max);
            }
        } catch (java.util.zip.ZipException ex) {
            throw new InvalidPackException("사전 팩(zip) 파일이 아닙니다");
        }
        if (!manifest) throw new InvalidPackException("사전 팩 정보(" + MANIFEST + ")가 없습니다");
        if (!dict) throw new InvalidPackException("변환 사전(" + DICT + ")이 없습니다");
        Properties p = readProperties(new File(into, MANIFEST));
        int format = parseInt(p.getProperty("format"));
        if (format <= 0) throw new InvalidPackException("사전 팩 형식을 알 수 없습니다");
        if (format > FORMAT) throw new InvalidPackException("이 버전의 앱이 읽을 수 없는 새 형식입니다. 앱을 업데이트해 주세요");
        String lang = p.getProperty("lang", "");
        if (!supported(lang)) throw new InvalidPackException("일본어·중국어 사전 팩이 아닙니다");
        if (expectLang != null && !expectLang.equals(lang)) {
            throw new InvalidPackException(languageName(lang) + " 사전 팩입니다. " + languageName(expectLang) + " 팩을 골라 주세요");
        }
        String want = p.getProperty("sha256." + DICT, "").trim();
        if (!want.isEmpty() && !want.equalsIgnoreCase(sha256(new File(into, DICT)))) {
            throw new InvalidPackException(DICT + "이(가) 손상되었습니다 (해시가 다릅니다)");
        }
        ConversionDictionary d;
        try (InputStream in = new FileInputStream(new File(into, DICT))) {
            d = ConversionDictionary.load(in);
        } catch (IOException | RuntimeException ex) {
            throw new InvalidPackException("변환 사전이 깨졌습니다");
        }
        if (d.size() == 0) throw new InvalidPackException("변환 사전이 비어 있습니다");
        if (CjkComposer.ZH.equals(lang) && new PinyinConverter(d).syllableCount() < 300) {
            throw new InvalidPackException("병음 음절(글자 하나의 읽기)이 너무 적습니다");
        }
        p.setProperty("words", String.valueOf(d.size()));
        try (OutputStream out = new FileOutputStream(new File(into, MANIFEST))) {
            p.store(new java.io.OutputStreamWriter(out, StandardCharsets.UTF_8), null);
        }
        return new Info(p, license ? readText(new File(into, LICENSE)) : "", size(into));
    }

    /** 설치된 팩의 정보. 없으면 null. 화면 스레드에서 불러도 된다 (사전은 읽지 않는다). */
    public static Info installed(File filesDir, String lang) {
        File dir = dir(filesDir, lang), m = new File(dir, MANIFEST);
        if (!m.isFile() || !new File(dir, DICT).isFile()) return null;
        try {
            File lic = new File(dir, LICENSE);
            Info info = new Info(readProperties(m), lic.isFile() ? readText(lic) : "", size(dir));
            return lang.equals(info.lang) ? info : null;
        } catch (IOException e) {
            return null;
        }
    }

    public static void remove(File filesDir, String lang) {
        synchronized (LOCK) {
            deleteTree(dir(filesDir, lang));
            changes++;
        }
    }

    /** 설치된 팩의 사전을 읽는다. 없거나 깨졌으면 null. 백그라운드 스레드에서 부른다. */
    public static ConversionDictionary load(File filesDir, String lang) {
        synchronized (LOCK) {
            File f = new File(dir(filesDir, lang), DICT);
            if (!f.isFile()) return null;
            try (InputStream in = new FileInputStream(f)) {
                return ConversionDictionary.load(in);
            } catch (IOException | RuntimeException e) {
                return null;
            }
        }
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

    static String sha256(File f) throws IOException {
        try (InputStream in = new FileInputStream(f)) {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] buf = new byte[16 * 1024];
            for (int n; (n = in.read(buf)) > 0; ) md.update(buf, 0, n);
            StringBuilder sb = new StringBuilder();
            for (byte b : md.digest()) sb.append(String.format("%02x", b & 0xFF));
            return sb.toString();
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IOException(e);
        }
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
