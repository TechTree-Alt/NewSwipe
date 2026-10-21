package com.alternative_studios.newswipe.suggest;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class DictionaryPackTest {
    @Rule public TemporaryFolder tmp = new TemporaryFolder();

    private static final String[][] WORDS = {{"안녕", "200"}, {"안녕하세요", "250"}, {"좋은", "200"}, {"하루", "150"}};

    private static byte[] dict(String[][] words) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        // KoreanSuggestTest와 같은 방법으로 만든다 (NSD1).
        WordDictionary d = KoreanSuggestTest.buildDict(words);
        String[][] sorted = words.clone();
        java.util.Arrays.sort(sorted, (a, b) -> a[0].compareTo(b[0]));
        ByteArrayOutputStream blob = new ByteArrayOutputStream();
        String prev = "";
        for (String[] e : sorted) {
            int common = 0;
            while (common < prev.length() && common < e[0].length() && prev.charAt(common) == e[0].charAt(common)) common++;
            String tail = e[0].substring(common);
            blob.write(common);
            blob.write(tail.length());
            blob.write(Integer.parseInt(e[1]));
            blob.write(tail.getBytes(StandardCharsets.UTF_16LE));
            prev = e[0];
        }
        java.nio.ByteBuffer head = java.nio.ByteBuffer.allocate(12).order(java.nio.ByteOrder.LITTLE_ENDIAN);
        head.put("NSD1".getBytes(StandardCharsets.US_ASCII)).putInt(sorted.length).putInt(blob.size());
        out.write(head.array());
        out.write(blob.toByteArray());
        assertEquals(words.length, d.size());
        return out.toByteArray();
    }

    private static byte[] next(String[][] words) throws Exception {
        return KoreanSuggestTest.buildNext(KoreanSuggestTest.buildDict(words), new String[][]{{"좋은", "하루", "0.3"}}, 0.5);
    }

    private static String sha(byte[] b) throws Exception {
        StringBuilder sb = new StringBuilder();
        for (byte x : MessageDigest.getInstance("SHA-256").digest(b)) sb.append(String.format("%02x", x & 0xFF));
        return sb.toString();
    }

    private static byte[] zip(Map<String, byte[]> entries) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ZipOutputStream z = new ZipOutputStream(out)) {
            for (Map.Entry<String, byte[]> e : entries.entrySet()) {
                z.putNextEntry(new ZipEntry(e.getKey()));
                z.write(e.getValue());
                z.closeEntry();
            }
        }
        return out.toByteArray();
    }

    private static byte[] manifest(String extra) {
        return ("format=1\nlang=ko\nname=시험 사전\nversion=1\nlicense=CC0\nattribution=테스트\n" + extra)
                .getBytes(StandardCharsets.UTF_8);
    }

    private Map<String, byte[]> goodPack() throws Exception {
        byte[] d = dict(WORDS), n = next(WORDS);
        Map<String, byte[]> m = new LinkedHashMap<>();
        m.put(DictionaryPack.MANIFEST, manifest("sha256.dict_ko.bin=" + sha(d) + "\nsha256.dict_ko_next.bin=" + sha(n) + "\n"));
        m.put(DictionaryPack.DICT, d);
        m.put(DictionaryPack.NEXT, n);
        m.put(DictionaryPack.LICENSE, "라이선스 전문".getBytes(StandardCharsets.UTF_8));
        return m;
    }

    private void expectInvalid(Map<String, byte[]> pack, File files) throws Exception {
        try {
            DictionaryPack.install(new ByteArrayInputStream(zip(pack)), files);
            fail("받아들이면 안 된다");
        } catch (DictionaryPack.InvalidPackException expected) {
            // 사용자에게 보일 이유가 있어야 한다
            assertFalse(expected.getMessage().isEmpty());
        }
    }

    @Test
    public void installsReadsAndRemoves() throws Exception {
        File files = tmp.newFolder();
        assertNull(DictionaryPack.installed(files));
        assertNull(DictionaryPack.load(files));
        int before = DictionaryPack.changes();
        DictionaryPack.Info info = DictionaryPack.install(new ByteArrayInputStream(zip(goodPack())), files);
        assertEquals("시험 사전", info.name);
        assertEquals(4, info.words);
        assertEquals(1, info.pairs);
        assertEquals("라이선스 전문", info.licenseText);
        assertTrue(DictionaryPack.changes() != before);

        DictionaryPack.Info again = DictionaryPack.installed(files);
        assertNotNull(again);
        assertEquals("시험 사전", again.name);   // 한글이 그대로 남는다
        assertEquals(4, again.words);
        DictionaryPack.Loaded l = DictionaryPack.load(files);
        assertNotNull(l);
        assertEquals(4, l.dict.size());
        assertEquals(1, l.next.pairs());

        DictionaryPack.remove(files);
        assertNull(DictionaryPack.installed(files));
        assertNull(DictionaryPack.load(files));
    }

    @Test
    public void readsKnownWords() throws Exception {
        File files = tmp.newFolder();
        assertNull(DictionaryPack.load(installWith(files, null)).known);   // 없어도 된다
        DictionaryPack.Loaded l = DictionaryPack.load(installWith(files, KnownWordsTest.hex(KnownWordsTest.FILE)));
        assertTrue(l.known.contains("공황"));
        byte[] broken = KnownWordsTest.hex(KnownWordsTest.FILE);
        broken[0] = 'X';
        Map<String, byte[]> m = goodPack();
        m.put(DictionaryPack.KNOWN, broken);
        expectInvalid(m, tmp.newFolder());   // 가져올 때 깨진 목록은 받지 않는다
    }

    private File installWith(File files, byte[] known) throws Exception {
        Map<String, byte[]> m = goodPack();
        if (known != null) m.put(DictionaryPack.KNOWN, known);
        DictionaryPack.install(new ByteArrayInputStream(zip(m)), files);
        return files;
    }

    @Test
    public void nextWordsAreOptional() throws Exception {
        File files = tmp.newFolder();
        Map<String, byte[]> m = goodPack();
        m.remove(DictionaryPack.NEXT);
        m.put(DictionaryPack.MANIFEST, manifest(""));   // 해시는 적지 않아도 된다
        DictionaryPack.Info info = DictionaryPack.install(new ByteArrayInputStream(zip(m)), files);
        assertEquals(0, info.pairs);
        assertNull(DictionaryPack.load(files).next);
    }

    @Test
    public void rejectsBadPacksAndKeepsCurrentOne() throws Exception {
        File files = tmp.newFolder();
        DictionaryPack.install(new ByteArrayInputStream(zip(goodPack())), files);

        Map<String, byte[]> noDict = goodPack();
        noDict.remove(DictionaryPack.DICT);
        expectInvalid(noDict, files);

        Map<String, byte[]> noManifest = goodPack();
        noManifest.remove(DictionaryPack.MANIFEST);
        expectInvalid(noManifest, files);

        Map<String, byte[]> badHash = goodPack();
        badHash.put(DictionaryPack.DICT, dict(new String[][]{{"다른", "100"}}));
        expectInvalid(badHash, files);

        Map<String, byte[]> broken = goodPack();
        broken.put(DictionaryPack.MANIFEST, manifest(""));
        broken.put(DictionaryPack.DICT, "NSD1 broken".getBytes(StandardCharsets.US_ASCII));
        expectInvalid(broken, files);

        // 다른 사전으로 만든 다음 단어 사전
        Map<String, byte[]> mismatch = goodPack();
        mismatch.put(DictionaryPack.MANIFEST, manifest(""));
        mismatch.put(DictionaryPack.NEXT, next(new String[][]{{"좋은", "200"}, {"하루", "150"}}));
        expectInvalid(mismatch, files);

        Map<String, byte[]> future = goodPack();
        future.put(DictionaryPack.MANIFEST, "format=99\nlang=ko\n".getBytes(StandardCharsets.UTF_8));
        expectInvalid(future, files);

        Map<String, byte[]> english = goodPack();
        english.put(DictionaryPack.MANIFEST, "format=1\nlang=en\n".getBytes(StandardCharsets.UTF_8));
        expectInvalid(english, files);

        try {
            DictionaryPack.install(new ByteArrayInputStream("zip이 아니다".getBytes(StandardCharsets.UTF_8)), files);
            fail();
        } catch (DictionaryPack.InvalidPackException expected) {
            // zip이 아닌 파일
        }
        // 실패한 가져오기는 지금 팩을 건드리지 않는다
        assertEquals("시험 사전", DictionaryPack.installed(files).name);
        assertEquals(4, DictionaryPack.load(files).dict.size());
    }

    @Test
    public void ignoresUnknownAndNestedEntries() throws Exception {
        File files = tmp.newFolder();
        Map<String, byte[]> m = goodPack();
        m.put("../escape.txt", "x".getBytes(StandardCharsets.UTF_8));
        m.put("sub/dict_ko.bin", "x".getBytes(StandardCharsets.UTF_8));
        m.put("readme.md", "x".getBytes(StandardCharsets.UTF_8));
        DictionaryPack.install(new ByteArrayInputStream(zip(m)), files);
        assertFalse(new File(files.getParentFile(), "escape.txt").exists());
        assertFalse(new File(DictionaryPack.dir(files), "readme.md").exists());
        assertEquals(4, DictionaryPack.load(files).dict.size());
    }

    @Test
    public void rejectsOversizedEntry() throws Exception {
        File files = tmp.newFolder();
        Map<String, byte[]> m = goodPack();
        m.put(DictionaryPack.LICENSE, new byte[(int) DictionaryPack.MAX_TEXT + 1]);
        expectInvalid(m, files);
    }

    @Test
    public void brokenInstalledPackFallsBack() throws Exception {
        File files = tmp.newFolder();
        DictionaryPack.install(new ByteArrayInputStream(zip(goodPack())), files);
        try (FileOutputStream out = new FileOutputStream(new File(DictionaryPack.dir(files), DictionaryPack.DICT))) {
            out.write("NSD1 broken".getBytes(StandardCharsets.US_ASCII));
        }
        assertNull(DictionaryPack.load(files));   // 키보드는 기본 사전을 쓴다
    }
}
