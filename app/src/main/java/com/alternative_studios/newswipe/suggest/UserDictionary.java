package com.alternative_studios.newswipe.suggest;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 사용자가 직접 입력한 단어를 기억해 두는 작은 사전. 기본 사전에 없는 단어(이름, 줄임말, 새 낱말)만 담는다.
 * 단어를 두 번 이상 입력해야 '아는 단어'로 쳐서, 한 번의 오타가 추천이나 자동 수정에 끼어들지 않게 한다.
 * 기기 안의 파일에만 저장하며 밖으로 나가지 않는다.
 */
public final class UserDictionary {
    public static final int MAX_WORDS = 3000;
    public static final int KNOWN_COUNT = 2;

    private static final class Entry {
        int count;
        long last;

        Entry(int count, long last) {
            this.count = count;
            this.last = last;
        }
    }

    private static UserDictionary shared;

    /** 키보드 서비스와 설정 화면이 같은 프로세스에서 하나를 같이 쓴다 (지우기가 메모리에도 바로 반영되도록). */
    public static synchronized UserDictionary shared(File filesDir) {
        if (shared == null) shared = new UserDictionary(new File(filesDir, "userwords.txt"));
        return shared;
    }

    private final File file;
    private final Object fileLock = new Object();   // 파일 읽기·쓰기 순서를 지킨다 (단어 목록 잠금과 따로 둔다)
    private Map<String, Entry> words = new HashMap<>();
    private boolean loaded, dirty;
    private int sinceSave;
    private int generation;   // release()/clear()마다 올린다. 읽는 도중에 바뀌면 읽은 결과를 버린다.
    /** 사용자가 단어를 지우거나(하나·모두) 백업에서 바꿔 넣을 때마다 올린다. 키보드가 따로 들고 있는 상태를 비우는 데 쓴다. */
    private volatile int editVersion;

    public int editVersion() {
        return editVersion;
    }

    UserDictionary(File file) {
        this.file = file;
    }

    private Map<String, Entry> readFile() {
        Map<String, Entry> map = new HashMap<>();
        if (!file.exists()) return map;
        try (BufferedReader r = new BufferedReader(new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))) {
            String line;
            while ((line = r.readLine()) != null && map.size() < MAX_WORDS) {
                String[] p = line.split("\t");
                if (p.length != 3 || p[0].isEmpty()) continue;
                try {
                    map.put(p[0], new Entry(Integer.parseInt(p[1]), Long.parseLong(p[2])));
                } catch (NumberFormatException ignored) {
                    // 깨진 줄은 건너뛴다.
                }
            }
        } catch (IOException ignored) {
            // 읽지 못하면 읽은 데까지만 쓴다.
        }
        return map;
    }

    /**
     * 파일을 읽어 메모리에 올린다. 백그라운드 스레드에서 부른다.
     * 파일은 잠금 밖에서 읽어서, 그동안 화면 스레드의 조회가 기다리지 않는다.
     */
    public void load() {
        int gen;
        synchronized (this) {
            if (loaded) return;
            gen = generation;
        }
        Map<String, Entry> map;
        synchronized (fileLock) {
            map = readFile();
        }
        synchronized (this) {
            if (loaded || gen != generation) return;
            // 읽는 동안 들어온 단어(add)가 있으면 합친다.
            for (Map.Entry<String, Entry> me : words.entrySet()) {
                Entry old = map.get(me.getKey());
                if (old == null) map.put(me.getKey(), me.getValue());
                else {
                    old.count += me.getValue().count;
                    old.last = Math.max(old.last, me.getValue().last);
                }
            }
            words = map;
            loaded = true;
        }
    }

    private void ensureLoaded() {
        boolean need;
        synchronized (this) {
            need = !loaded;
        }
        if (need) load();
    }

    /** 저장 형태: 한글은 그대로, 영어는 소문자 (중간에 대문자가 있는 iPhone 같은 말은 그대로). */
    static String key(String word, boolean korean) {
        if (korean) return word;
        for (int i = 1; i < word.length(); i++) if (Character.isUpperCase(word.charAt(i))) return word;
        return word.toLowerCase(Locale.ROOT);
    }

    /** 화면 스레드용: 아직 읽지 않았으면 기다리지 않고 false. */
    public synchronized boolean isKnownIfLoaded(String word, boolean korean) {
        if (!loaded) return false;
        Entry e = words.get(key(word, korean));
        if (e == null && !korean) {
            // 추천은 입력 모양(대문자 등)대로 보이므로 영어는 대소문자를 가리지 않고도 찾아본다.
            for (Map.Entry<String, Entry> me : words.entrySet()) {
                if (me.getKey().equalsIgnoreCase(word)) {
                    e = me.getValue();
                    break;
                }
            }
        }
        return e != null && e.count >= KNOWN_COUNT;
    }

    public boolean isKnown(String word, boolean korean) {
        ensureLoaded();
        return isKnownIfLoaded(word, korean);
    }

    /** 단어를 한 번 썼다고 기록한다. strong이면 곧바로 '아는 단어'가 된다 (사용자가 자동 수정을 되돌린 경우). */
    public void add(String word, boolean korean, boolean strong, long now) {
        ensureLoaded();
        addLoaded(word, korean, strong, now);
    }

    private synchronized void addLoaded(String word, boolean korean, boolean strong, long now) {
        String k = key(word, korean);
        Entry e = words.get(k);
        if (e == null) {
            e = new Entry(0, 0);
            words.put(k, e);
        }
        e.count = strong ? Math.max(e.count + 1, KNOWN_COUNT) : e.count + 1;
        e.last = now;
        dirty = true;
        sinceSave++;
        if (words.size() > MAX_WORDS) trim();
    }

    /** 가장 안 쓴 단어부터 10%를 덜어 낸다 (한 번만 쓴 것이 먼저). */
    private void trim() {
        List<Map.Entry<String, Entry>> all = new ArrayList<>(words.entrySet());
        all.sort((a, b) -> {
            int ca = a.getValue().count >= KNOWN_COUNT ? 1 : 0, cb = b.getValue().count >= KNOWN_COUNT ? 1 : 0;
            if (ca != cb) return ca - cb;
            return Long.compare(a.getValue().last, b.getValue().last);
        });
        for (int i = 0; i < MAX_WORDS / 10; i++) words.remove(all.get(i).getKey());
    }

    /** prefix로 시작하는 아는 단어를 많이 쓴 순서로 최대 max개. korean이면 덜 만든 글자도 맞춘다. */
    public String[] lookup(String prefix, boolean korean, int max) {
        ensureLoaded();
        return lookupIfLoaded(prefix, korean, max);
    }

    /** 화면 스레드용: 아직 읽지 않았으면 기다리지 않고 빈 결과. */
    public synchronized String[] lookupIfLoaded(String prefix, boolean korean, int max) {
        if (!loaded || words.isEmpty()) return new String[0];
        if (prefix.isEmpty() || max <= 0) return new String[0];
        String p = korean ? prefix : prefix.toLowerCase(Locale.ROOT);
        int n = p.length();
        char last = p.charAt(n - 1);
        int[] range = korean ? HangulRange.of(last) : null;
        if (korean && range == null && last >= 'ㄱ' && last <= 'ㅣ') return new String[0];
        List<Map.Entry<String, Entry>> hits = new ArrayList<>();
        for (Map.Entry<String, Entry> me : words.entrySet()) {
            Entry e = me.getValue();
            String w = me.getKey();
            if (e.count < KNOWN_COUNT) continue;
            if (w.length() < n) continue;
            if (!w.regionMatches(!korean, 0, p, 0, n - 1)) continue;
            char c = korean ? w.charAt(n - 1) : Character.toLowerCase(w.charAt(n - 1));
            boolean ok = range != null ? c >= range[0] && c <= range[1] : c == last;
            if (!ok) continue;
            if (w.length() == n && (range == null || w.equals(p))) continue;   // 입력한 그대로인 단어
            hits.add(me);
        }
        hits.sort((a, b) -> {
            int c = Integer.compare(b.getValue().count, a.getValue().count);
            return c != 0 ? c : Long.compare(b.getValue().last, a.getValue().last);
        });
        int m = Math.min(max, hits.size());
        String[] out = new String[m];
        for (int i = 0; i < m; i++) out[i] = hits.get(i).getKey();
        return out;
    }

    /** 백업용: {단어, 횟수, 마지막 사용 시각} 목록. 메모리에 올라와 있지 않으면 파일에서 읽기만 하고 올리지 않는다. */
    public List<String[]> snapshot() {
        Map<String, Entry> src;
        synchronized (this) {
            if (loaded) {
                src = new HashMap<>(words.size() * 2);
                for (Map.Entry<String, Entry> me : words.entrySet()) {
                    src.put(me.getKey(), new Entry(me.getValue().count, me.getValue().last));
                }
            } else {
                src = null;
            }
        }
        if (src == null) {
            synchronized (fileLock) {
                src = readFile();
            }
        }
        List<String[]> out = new ArrayList<>(src.size());
        for (Map.Entry<String, Entry> me : src.entrySet()) {
            out.add(new String[]{me.getKey(), String.valueOf(me.getValue().count), String.valueOf(me.getValue().last)});
        }
        return out;
    }

    /**
     * 백업에서 가져오기: 지금 사전을 이 목록으로 바꾸고 저장한다. 올바르지 않은 항목은 건너뛰고 들어간 개수를 돌려준다.
     * 메모리에 올라와 있지 않았으면 파일만 바꾸고 올리지 않는다.
     */
    public int replaceAll(List<String[]> items) {
        Map<String, Entry> map = new HashMap<>();
        for (String[] it : items) {
            if (map.size() >= MAX_WORDS) break;
            try {
                if (it.length != 3 || it[0].isEmpty() || it[0].length() > 24 || it[0].indexOf('\t') >= 0
                        || it[0].indexOf('\n') >= 0 || it[0].indexOf('\r') >= 0) continue;
                map.put(it[0], new Entry(Math.max(1, Math.min(Integer.parseInt(it[1]), 100000)), Long.parseLong(it[2])));
            } catch (NumberFormatException ignored) {
                // 깨진 항목은 건너뛴다.
            }
        }
        // 파일 잠금을 먼저 잡아, 그사이 백그라운드에서 읽던 옛 파일 내용이 메모리에 들어앉지 못하게 한다.
        synchronized (fileLock) {
            synchronized (this) {
                generation++;
                words = loaded ? map : new HashMap<>();
                dirty = false;
                sinceSave = 0;
            }
            writeFile(map);
            editVersion++;
        }
        return map.size();
    }

    /**
     * 학습한 단어 하나를 지운다. 영어는 대소문자를 가리지 않고 지운다 (추천에는 입력 모양대로 보이므로).
     * 메모리에 올라와 있지 않으면 파일만 고치고 올리지 않는다. 지웠으면 true.
     */
    public boolean remove(String word, boolean korean) {
        boolean wasLoaded;
        synchronized (this) {
            wasLoaded = loaded;
            if (wasLoaded) {
                boolean removed = removeFrom(words, word, korean);
                if (removed) {
                    dirty = true;
                    sinceSave = Math.max(sinceSave, 10);
                }
                if (!removed) return false;
            } else {
                removeFrom(words, word, korean);   // 읽기 전에 들어온 단어
            }
        }
        if (wasLoaded) {
            save();
            editVersion++;
            return true;
        }
        synchronized (fileLock) {
            Map<String, Entry> map = readFile();
            if (!removeFrom(map, word, korean)) return false;
            synchronized (this) {
                generation++;   // 그사이 읽던 옛 내용은 버린다
            }
            writeFile(map);
        }
        editVersion++;
        return true;
    }

    private static boolean removeFrom(Map<String, Entry> map, String word, boolean korean) {
        if (map.remove(key(word, korean)) != null) return true;
        if (korean) return false;
        boolean removed = false;
        for (java.util.Iterator<String> it = map.keySet().iterator(); it.hasNext(); ) {
            if (it.next().equalsIgnoreCase(word)) {
                it.remove();
                removed = true;
            }
        }
        return removed;
    }

    /** 메모리에 올라와 있는지 따지지 않고, 학습한 단어가 하나도 없는지. */
    public boolean isEmpty() {
        synchronized (this) {
            if (loaded) return words.isEmpty();
            if (!words.isEmpty()) return false;
        }
        return !file.exists() || file.length() == 0;
    }

    public synchronized boolean isLoaded() {
        return loaded;
    }

    public synchronized int size() {
        return words.size();
    }

    public synchronized boolean needsSave() {
        return dirty && sinceSave >= 10;
    }

    /**
     * 바뀐 것이 있으면 파일에 쓴다. 단어 목록을 복사한 뒤 잠금 밖에서 써서 화면 스레드의 조회가 기다리지 않는다.
     * (임시 파일에 쓴 뒤 바꿔치기해서 도중에 끊겨도 기존 파일이 남는다.)
     */
    public void save() {
        Map<String, Entry> copy;
        int gen;
        synchronized (this) {
            if (!dirty || !loaded) return;
            gen = generation;
            copy = new HashMap<>(words.size() * 2);
            for (Map.Entry<String, Entry> me : words.entrySet()) {
                copy.put(me.getKey(), new Entry(me.getValue().count, me.getValue().last));
            }
            dirty = false;
            sinceSave = 0;
        }
        boolean ok;
        synchronized (fileLock) {
            synchronized (this) {
                if (gen != generation) return;   // 그사이 지우기·가져오기가 있었다. 옛 내용을 되살리지 않는다.
            }
            ok = writeFile(copy);
        }
        if (!ok) {
            synchronized (this) {
                dirty = true;
            }
        }
    }

    private boolean writeFile(Map<String, Entry> map) {
        File tmp = new File(file.getPath() + ".tmp");
        try (BufferedWriter w = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(tmp), StandardCharsets.UTF_8))) {
            for (Map.Entry<String, Entry> me : map.entrySet()) {
                w.write(me.getKey() + "\t" + me.getValue().count + "\t" + me.getValue().last + "\n");
            }
        } catch (IOException e) {
            tmp.delete();
            return false;
        }
        return tmp.renameTo(file) || (file.delete() && tmp.renameTo(file));
    }

    /** 메모리에서 내린다 (저장하지 않은 것은 먼저 저장한다). 다음에 쓸 때 다시 읽는다. 백그라운드 스레드에서 부른다. */
    public void release() {
        save();
        synchronized (this) {
            if (dirty) return;   // 저장에 실패했으면 잃지 않도록 들고 있는다
            generation++;
            words = new HashMap<>();
            loaded = false;
        }
    }

    /** 학습한 단어를 모두 지운다 (파일도). */
    public void clear() {
        // 파일 잠금을 먼저 잡는다 (replaceAll과 같은 이유: 읽는 중이던 옛 내용이 되살아나지 않게).
        synchronized (fileLock) {
            synchronized (this) {
                generation++;
                words = new HashMap<>();
                // 파일까지 지웠으니 '빈 사전을 읽은 상태'다. 읽지 않은 상태로 두면 다시 읽을 때까지
                // 키보드가 학습한 단어를 모른다고 보고 자동 수정을 멈춘다.
                loaded = true;
                dirty = false;
                sinceSave = 0;
            }
            file.delete();
            new File(file.getPath() + ".tmp").delete();
        }
        editVersion++;
    }
}
