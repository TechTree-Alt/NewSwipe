package com.alternative_studios.newswipe.suggest;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;

public class UserDictionaryTest {
    @Rule public TemporaryFolder tmp = new TemporaryFolder();

    @Test
    public void knownOnlyAfterTwoUses() throws Exception {
        UserDictionary d = new UserDictionary(new File(tmp.getRoot(), "w.txt"));
        d.add("뚜비", true, false, 1);
        assertFalse(d.isKnown("뚜비", true));
        assertEquals(0, d.lookup("뚜", true, 3).length);
        d.add("뚜비", true, false, 2);
        assertTrue(d.isKnown("뚜비", true));
        assertArrayEquals(new String[]{"뚜비"}, d.lookup("뚜", true, 3));
        assertArrayEquals(new String[]{"뚜비"}, d.lookup("ㄸ", true, 3));
        assertEquals(0, d.lookup("뚜비", true, 3).length);   // 입력한 그대로인 단어
    }

    @Test
    public void strongAddIsKnownImmediately() throws Exception {
        UserDictionary d = new UserDictionary(new File(tmp.getRoot(), "w.txt"));
        d.add("recieve", false, true, 1);
        assertTrue(d.isKnown("Recieve", false));
        assertArrayEquals(new String[]{"recieve"}, d.lookup("REC", false, 3));
    }

    @Test
    public void ranksByCountAndPersists() throws Exception {
        File f = new File(tmp.getRoot(), "w.txt");
        UserDictionary d = new UserDictionary(f);
        for (int i = 0; i < 2; i++) d.add("nopa", false, false, 1);
        for (int i = 0; i < 5; i++) d.add("nova", false, false, 2);
        assertArrayEquals(new String[]{"nova", "nopa"}, d.lookup("no", false, 3));
        d.add("iPhone", false, true, 3);
        d.save();
        UserDictionary again = new UserDictionary(f);
        assertEquals(0, again.size());   // 읽기 전에는 메모리에 올리지 않는다
        assertFalse(again.isEmpty());    // 하지만 비어 있지 않다는 건 안다
        again.load();
        assertEquals(3, again.size());
        assertArrayEquals(new String[]{"nova", "nopa"}, again.lookup("no", false, 3));
        assertArrayEquals(new String[]{"iPhone"}, again.lookup("iph", false, 3));
    }

    @Test
    public void clearRemovesFileAndMemory() throws Exception {
        File f = new File(tmp.getRoot(), "w.txt");
        UserDictionary d = new UserDictionary(f);
        d.add("뚜비", true, true, 1);
        d.save();
        assertTrue(f.exists());
        d.clear();
        assertFalse(f.exists());
        assertEquals(0, d.size());
    }

    @Test
    public void trimsWhenTooBig() throws Exception {
        UserDictionary d = new UserDictionary(new File(tmp.getRoot(), "w.txt"));
        for (int i = 0; i <= UserDictionary.MAX_WORDS; i++) d.add("w" + i, false, false, i);
        assertTrue(d.size() <= UserDictionary.MAX_WORDS);
    }

    @Test
    public void learnableWords() {
        assertTrue(WordSuggester.learnable("뚜비", true));
        assertFalse(WordSuggester.learnable("뚜", true));
        assertFalse(WordSuggester.learnable("ㅋㅋ", true));
        assertTrue(WordSuggester.learnable("don't", false));
        assertFalse(WordSuggester.learnable("ab", false));
        assertFalse(WordSuggester.learnable("a1b2", false));
    }

    @Test
    public void snapshotAndReplaceAllRoundTrip() throws Exception {
        UserDictionary a = new UserDictionary(new File(tmp.getRoot(), "a.txt"));
        a.add("뚜비", true, true, 5);
        a.add("nova", false, false, 6);
        a.add("nova", false, false, 7);
        UserDictionary b = new UserDictionary(new File(tmp.getRoot(), "b.txt"));
        b.add("old", false, true, 1);
        java.util.List<String[]> items = a.snapshot();
        items.add(new String[]{"bad\tword", "2", "1"});
        items.add(new String[]{"x", "oops", "1"});
        assertEquals(2, b.replaceAll(items));
        assertTrue(b.isKnown("뚜비", true));
        assertTrue(b.isKnown("nova", false));
        assertFalse(b.isKnown("old", false));
        assertTrue(new File(tmp.getRoot(), "b.txt").exists());
    }

    @Test
    public void mainThreadReadsNeverLoad() throws Exception {
        File f = new File(tmp.getRoot(), "w.txt");
        UserDictionary a = new UserDictionary(f);
        a.add("뚜비", true, true, 1);
        a.save();
        UserDictionary b = new UserDictionary(f);
        assertEquals(0, b.lookupIfLoaded("뚜", true, 3).length);
        assertFalse(b.isKnownIfLoaded("뚜비", true));
        assertFalse(b.isLoaded());
        b.load();
        assertTrue(b.isKnownIfLoaded("뚜비", true));
    }

    @Test
    public void snapshotAndReplaceDoNotKeepUnloadedDataInMemory() throws Exception {
        File f = new File(tmp.getRoot(), "w.txt");
        UserDictionary a = new UserDictionary(f);
        a.add("뚜비", true, true, 1);
        a.save();
        UserDictionary b = new UserDictionary(f);
        assertEquals(1, b.snapshot().size());
        assertFalse(b.isLoaded());
        assertEquals(0, b.size());
        java.util.List<String[]> items = new java.util.ArrayList<>();
        items.add(new String[]{"nova", "3", "5"});
        assertEquals(1, b.replaceAll(items));
        assertFalse(b.isLoaded());
        b.load();
        assertTrue(b.isKnownIfLoaded("nova", false));
        assertFalse(b.isKnownIfLoaded("뚜비", true));
    }

    @Test
    public void releaseSavesThenFrees() throws Exception {
        File f = new File(tmp.getRoot(), "w.txt");
        UserDictionary d = new UserDictionary(f);
        d.add("뚜비", true, true, 1);   // 아직 저장 안 됨 (10개 미만)
        d.release();
        assertFalse(d.isLoaded());
        assertEquals(0, d.size());
        assertTrue(f.exists());
        assertTrue(d.isKnown("뚜비", true));   // 다시 읽으면 남아 있다
    }

    @Test
    public void clearIsNotUndoneByEarlierSnapshotSave() throws Exception {
        File f = new File(tmp.getRoot(), "w.txt");
        UserDictionary d = new UserDictionary(f);
        d.add("뚜비", true, true, 1);
        d.clear();
        d.save();   // 지운 뒤라 쓸 것이 없다
        assertFalse(f.exists());
        assertTrue(d.isEmpty());
        assertFalse(d.isKnown("뚜비", true));
    }

    @Test
    public void wordsAddedBeforeLoadAreMerged() throws Exception {
        File f = new File(tmp.getRoot(), "w.txt");
        UserDictionary a = new UserDictionary(f);
        a.add("nova", false, false, 1);
        a.save();   // 횟수 1만 저장 (needsSave와 관계없이 바로 쓰기)
        UserDictionary b = new UserDictionary(f);
        b.add("nova", false, false, 2);   // add는 먼저 읽고 더한다
        assertTrue(b.isKnownIfLoaded("nova", false));
    }

    @Test
    public void corruptFileIsSkipped() throws Exception {
        File f = new File(tmp.getRoot(), "w.txt");
        java.nio.file.Files.write(f.toPath(), "ok\t3\t1\nbad line\nx\tNaN\t1\n\t2\t1\n".getBytes("UTF-8"));
        UserDictionary d = new UserDictionary(f);
        d.load();
        assertEquals(1, d.size());
        assertTrue(d.isKnownIfLoaded("ok", false));
    }

    @Test
    public void removeWhenLoaded() throws Exception {
        File f = new File(tmp.getRoot(), "w.txt");
        UserDictionary d = new UserDictionary(f);
        d.add("뚜비", true, true, 1);
        d.add("nova", false, true, 2);
        assertTrue(d.remove("뚜비", true));
        assertFalse(d.isKnownIfLoaded("뚜비", true));
        assertTrue(d.remove("Nova", false));   // 대소문자를 가리지 않는다
        assertFalse(d.remove("없는말", true));
        UserDictionary again = new UserDictionary(f);   // 파일에도 반영됐다
        again.load();
        assertEquals(0, again.size());
    }

    @Test
    public void removeWhenNotLoadedEditsFileOnly() throws Exception {
        File f = new File(tmp.getRoot(), "w.txt");
        UserDictionary a = new UserDictionary(f);
        a.add("뚜비", true, true, 1);
        a.add("iPhone", false, true, 2);
        a.save();
        UserDictionary b = new UserDictionary(f);
        assertTrue(b.remove("IPHONE", false));
        assertFalse(b.isLoaded());
        b.load();
        assertTrue(b.isKnownIfLoaded("뚜비", true));
        assertFalse(b.isKnownIfLoaded("iPhone", false));
    }

    @Test
    public void knownCheckIgnoresEnglishCase() throws Exception {
        UserDictionary d = new UserDictionary(new File(tmp.getRoot(), "w.txt"));
        d.add("iPhone", false, true, 1);
        assertTrue(d.isKnownIfLoaded("IPHONE", false));
        assertTrue(d.isKnownIfLoaded("iphone", false));
    }

    @Test
    public void twoPlainUsesMakeKnown() throws Exception {
        // 자동 수정 되돌리기는 이제 한 번에 1씩 센다: 두 번 되돌려야 '아는 단어'가 된다.
        UserDictionary d = new UserDictionary(new File(tmp.getRoot(), "w.txt"));
        d.add("teh", false, false, 1);
        assertFalse(d.isKnownIfLoaded("teh", false));
        d.add("teh", false, false, 2);
        assertTrue(d.isKnownIfLoaded("teh", false));
    }

    @Test
    public void editsBumpVersionAndClearStaysUsable() throws Exception {
        File f = new File(tmp.getRoot(), "w.txt");
        UserDictionary d = new UserDictionary(f);
        d.add("teh", false, false, 1);
        d.add("teh", false, false, 2);
        int v0 = d.editVersion();
        assertTrue(d.remove("teh", false));
        assertTrue(d.editVersion() != v0);
        assertFalse(d.isKnownIfLoaded("teh", false));

        d.add("nova", false, true, 3);
        int v1 = d.editVersion();
        d.clear();
        assertTrue(d.editVersion() != v1);
        assertTrue(d.isLoaded());   // 지운 뒤에도 '빈 사전을 읽은 상태'라 자동 수정이 멈추지 않는다
        assertFalse(d.isKnownIfLoaded("nova", false));
        int v2 = d.editVersion();
        d.release();
        assertEquals(v2, d.editVersion());   // 메모리에서 내리는 건 편집이 아니다
    }
}
