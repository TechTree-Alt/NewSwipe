package com.alternative_studios.newswipe.clipboard;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.util.List;

public class ClipboardHistoryTest {
    private static final long HOUR = 60L * 60 * 1000;

    private static File tempFile() throws IOException {
        File f = File.createTempFile("clip", ".txt");
        f.delete();
        f.deleteOnExit();
        return f;
    }

    @Test
    public void newestFirstAndDeduplicated() throws IOException {
        ClipboardHistory h = new ClipboardHistory(tempFile());
        h.add("a", 1);
        h.add("b", 2);
        h.add("a", 3);
        List<ClipboardHistory.Item> items = h.items(4);
        assertEquals(2, items.size());
        assertEquals("a", items.get(0).text);
        assertEquals("b", items.get(1).text);
    }

    @Test
    public void keepsAtMost30() throws IOException {
        ClipboardHistory h = new ClipboardHistory(tempFile());
        for (int i = 0; i < 40; i++) h.add("t" + i, i);
        List<ClipboardHistory.Item> items = h.items(100);
        assertEquals(30, items.size());
        assertEquals("t39", items.get(0).text);
    }

    @Test
    public void expiresAfter24HoursUnlessPinned() throws IOException {
        ClipboardHistory h = new ClipboardHistory(tempFile());
        h.add("old", 0);
        h.add("keep", 0);
        h.setPinned(h.items(0).get(0), true);   // "keep"
        h.add("new", 23 * HOUR);
        List<ClipboardHistory.Item> items = h.items(24 * HOUR + 1);
        assertEquals(2, items.size());
        assertEquals("keep", items.get(0).text);   // 고정 항목이 먼저
        assertEquals("new", items.get(1).text);
    }

    @Test
    public void persistsAcrossInstances() throws IOException {
        File f = tempFile();
        ClipboardHistory h = new ClipboardHistory(f);
        h.add("줄\n바꿈\t탭 \\ 역슬래시", 10);
        h.setPinned(h.items(10).get(0), true);
        ClipboardHistory h2 = new ClipboardHistory(f);
        List<ClipboardHistory.Item> items = h2.items(11);
        assertEquals(1, items.size());
        assertEquals("줄\n바꿈\t탭 \\ 역슬래시", items.get(0).text);
        assertTrue(items.get(0).pinned);
    }

    @Test
    public void ignoresBlankText() throws IOException {
        ClipboardHistory h = new ClipboardHistory(tempFile());
        h.add("   ", 1);
        h.add(null, 1);
        assertEquals(0, h.items(2).size());
    }

    @Test
    public void pinIsLimited() throws IOException {
        File f = File.createTempFile("clip", ".txt");
        f.delete();
        ClipboardHistory h = new ClipboardHistory(f);
        for (int i = 0; i < ClipboardHistory.MAX_PINNED + 1; i++) h.add("t" + i, 1000 + i);
        List<ClipboardHistory.Item> items = h.items(2000);
        for (int i = 0; i < ClipboardHistory.MAX_PINNED; i++) assertTrue(h.setPinned(items.get(i), true));
        assertFalse(h.setPinned(items.get(ClipboardHistory.MAX_PINNED), true));
        assertTrue(h.setPinned(items.get(0), false));   // 하나 풀면 다시 고정할 수 있다
        assertTrue(h.setPinned(items.get(ClipboardHistory.MAX_PINNED), true));
    }

    @Test
    public void pinAndRemoveWorkAfterReload() throws IOException {
        File f = File.createTempFile("clip", ".txt");
        f.delete();
        ClipboardHistory h = new ClipboardHistory(f);
        h.add("a", 1000);
        h.add("b", 1001);
        List<ClipboardHistory.Item> shown = h.items(2000);   // 화면이 들고 있는 항목
        h.release();                                         // 파일에서 다시 읽게 한다 (객체가 바뀐다)
        h.setPinned(shown.get(1), true);                      // "a"
        assertTrue(h.items(2000).get(0).pinned);
        assertEquals("a", h.items(2000).get(0).text);
        h.release();
        h.remove(shown.get(0));                               // "b"
        assertEquals(1, h.items(2000).size());
    }

    @Test
    public void firstPinnedMatchesTopOfPanel() throws IOException {
        ClipboardHistory h = new ClipboardHistory(tempFile());
        assertEquals(null, h.firstPinned(1));
        h.add("a", 1);
        h.add("b", 2);
        h.add("c", 3);
        assertEquals(null, h.firstPinned(4));            // 고정한 것이 없다
        List<ClipboardHistory.Item> items = h.items(4);   // c, b, a
        h.setPinned(items.get(2), true);                  // a
        h.setPinned(items.get(1), true);                  // b
        assertEquals("b", h.firstPinned(5).text);         // 창 맨 위: 고정한 것 중 가장 최근(b)
        assertEquals("b", h.items(5).get(0).text);
    }

    @Test
    public void snapshotAndReplaceAllKeepPinnedAndTabs() throws IOException {
        ClipboardHistory h = new ClipboardHistory(tempFile());
        h.add("a\tb\nc", 1);
        h.add("b", 2);
        h.setPinned(h.items(3).get(1), true);             // a\tb\nc 고정
        List<String[]> snap = h.snapshot(3);

        ClipboardHistory other = new ClipboardHistory(tempFile());
        other.add("old", 1);
        assertEquals(2, other.replaceAll(snap, 3));
        List<ClipboardHistory.Item> items = other.items(3);
        assertEquals("a\tb\nc", items.get(0).text);      // 고정 항목이 먼저
        assertTrue(items.get(0).pinned);
        assertEquals("b", items.get(1).text);
        assertFalse(items.get(1).pinned);
    }

    // ---------------------------------------------------------------- 사진

    private static File tempDir() throws IOException {
        File d = File.createTempFile("clipdir", "");
        d.delete();
        d.mkdirs();
        return d;
    }

    private static String id(int n) {
        return String.format("%032x", n);
    }

    /** ClipImages가 하는 것처럼 원본·미리보기 파일을 둔다. */
    private static void putImage(ClipboardHistory h, int n) throws IOException {
        h.imageDir().mkdirs();
        ClipboardHistory.original(h.imageDir(), id(n)).createNewFile();
        ClipboardHistory.thumbnail(h.imageDir(), id(n)).createNewFile();
    }

    private static boolean hasFiles(ClipboardHistory h, int n) {
        return ClipboardHistory.original(h.imageDir(), id(n)).exists()
                || ClipboardHistory.thumbnail(h.imageDir(), id(n)).exists();
    }

    @Test
    public void imagesPersistAndDeduplicate() throws IOException {
        File f = new File(tempDir(), "clipboard.txt");
        ClipboardHistory h = new ClipboardHistory(f);
        putImage(h, 1);
        h.addImage(id(1), "image/png", 1);
        h.add("text", 2);
        h.addImage(id(1), "image/png", 3);   // 같은 사진은 앞으로 옮긴다
        List<ClipboardHistory.Item> items = new ClipboardHistory(f).items(4);
        assertEquals(2, items.size());
        assertTrue(items.get(0).isImage());
        assertEquals(id(1), items.get(0).text);
        assertEquals("image/png", items.get(0).mime);
        assertFalse(items.get(1).isImage());
    }

    @Test
    public void textEqualToImageNameIsSeparate() throws IOException {
        ClipboardHistory h = new ClipboardHistory(new File(tempDir(), "clipboard.txt"));
        putImage(h, 1);
        h.addImage(id(1), "image/jpeg", 1);
        h.add(id(1), 2);
        assertEquals(2, h.items(3).size());
        h.remove(h.items(3).get(0));         // 텍스트만 지운다
        assertEquals(1, h.items(3).size());
        assertTrue(h.items(3).get(0).isImage());
        assertTrue(hasFiles(h, 1));
    }

    @Test
    public void keepsAtMost5UnpinnedImagesAndDeletesFiles() throws IOException {
        ClipboardHistory h = new ClipboardHistory(new File(tempDir(), "clipboard.txt"));
        putImage(h, 0);
        h.addImage(id(0), "image/png", 0);
        h.setPinned(h.items(0).get(0), true);   // 고정한 사진은 개수에 넣지 않는다
        for (int i = 1; i <= 7; i++) {
            putImage(h, i);
            h.addImage(id(i), "image/png", i);
            h.add("t" + i, i);
        }
        List<ClipboardHistory.Item> items = h.items(10);
        int images = 0;
        for (ClipboardHistory.Item i : items) if (i.isImage()) images++;
        assertEquals(ClipboardHistory.MAX_IMAGES + 1, images);
        assertEquals(7 + ClipboardHistory.MAX_IMAGES + 1, items.size());   // 텍스트는 그대로
        assertTrue(hasFiles(h, 0));
        assertFalse(hasFiles(h, 1));
        assertFalse(hasFiles(h, 2));
        assertTrue(hasFiles(h, 3));
    }

    @Test
    public void expiredImageFilesAreDeleted() throws IOException {
        ClipboardHistory h = new ClipboardHistory(new File(tempDir(), "clipboard.txt"));
        putImage(h, 1);
        putImage(h, 2);
        h.addImage(id(1), "image/png", 0);
        h.addImage(id(2), "image/png", 0);
        h.setPinned(h.items(0).get(0), true);   // 2 고정
        h.prune(24 * HOUR + 1);
        assertFalse(hasFiles(h, 1));
        assertTrue(hasFiles(h, 2));
        assertEquals(1, h.items(24 * HOUR + 1).size());
    }

    @Test
    public void removeAndClearDeleteFiles() throws IOException {
        ClipboardHistory h = new ClipboardHistory(new File(tempDir(), "clipboard.txt"));
        for (int i = 1; i <= 3; i++) {
            putImage(h, i);
            h.addImage(id(i), "image/gif", i);
        }
        h.setPinned(h.items(4).get(0), true);   // 3 고정
        h.remove(h.items(4).get(1));            // 2 삭제
        assertFalse(hasFiles(h, 2));
        h.clearUnpinned();                      // 1 삭제
        assertFalse(hasFiles(h, 1));
        assertTrue(hasFiles(h, 3));
        h.clearAll();
        assertFalse(hasFiles(h, 3));
        assertFalse(h.imageDir().exists());
    }

    @Test
    public void pruneDeletesOrphansAndMissingOriginals() throws IOException {
        ClipboardHistory h = new ClipboardHistory(new File(tempDir(), "clipboard.txt"));
        putImage(h, 1);
        putImage(h, 2);
        h.addImage(id(1), "image/png", 1);
        h.addImage(id(2), "image/png", 1);
        putImage(h, 9);                                                  // 기록에 없는 파일
        ClipboardHistory.original(h.imageDir(), id(2)).delete();         // 원본이 사라진 사진
        h.prune(2);
        assertFalse(hasFiles(h, 9));
        assertFalse(hasFiles(h, 2));
        assertEquals(1, h.items(2).size());
    }

    @Test
    public void backupSkipsImagesAndImportKeepsThem() throws IOException {
        ClipboardHistory h = new ClipboardHistory(new File(tempDir(), "clipboard.txt"));
        putImage(h, 1);
        h.add("old", 1);
        h.addImage(id(1), "image/png", 2);
        List<String[]> snap = h.snapshot(3);
        assertEquals(1, snap.size());
        assertEquals("old", snap.get(0)[0]);
        h.replaceAll(java.util.Collections.singletonList(new String[]{"new", "3", "0"}), 4);
        List<ClipboardHistory.Item> items = h.items(4);
        assertEquals(2, items.size());
        assertEquals("new", items.get(0).text);
        assertTrue(items.get(1).isImage());
    }

    @Test
    public void rejectsBadNamesAndTypes() throws IOException {
        ClipboardHistory h = new ClipboardHistory(new File(tempDir(), "clipboard.txt"));
        h.addImage("../../etc", "image/png", 1);
        h.addImage(id(1), "text/plain", 1);
        assertEquals(0, h.items(2).size());
        assertFalse(ClipboardHistory.isImageId(id(1).toUpperCase().replace('0', 'A')));
        assertEquals("image/png", ClipboardHistory.cleanMime("image/PNG"));
        assertEquals(null, ClipboardHistory.cleanMime("image/png\tx"));
    }

    @Test
    public void clearImagesRemovesPinnedImagesAndKeepsText() throws IOException {
        ClipboardHistory h = new ClipboardHistory(new File(tempDir(), "clipboard.txt"));
        putImage(h, 1);
        putImage(h, 2);
        h.addImage(id(1), "image/png", 1);
        h.addImage(id(2), "image/png", 2);
        h.add("text", 3);
        h.setPinned(h.items(4).get(1), true);   // 사진 2 고정
        h.clearImages();
        List<ClipboardHistory.Item> items = h.items(4);
        assertEquals(1, items.size());
        assertEquals("text", items.get(0).text);
        assertFalse(hasFiles(h, 1));
        assertFalse(hasFiles(h, 2));
        assertFalse(h.imageDir().exists());
    }
}
