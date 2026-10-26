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
import java.util.Map;

/**
 * 자동 수정이 고치지 않은(비슷한 후보가 여럿이라) 틀린 말에서 사용자가 추천란의 어떤 단어를 골랐는지 기억한다. 같은 말에서
 * 같은 단어를 {@link #CHOSEN}번 고르면 그다음부터는 그 단어로 자동 수정한다. 말마다 마지막에 고른 단어 하나만 센다 (다른
 * 단어를 고르면 그 단어로 처음부터 센다). 자동 수정을 지우기 키로 되돌리면 잊는다. 기기 안의 파일에만 저장한다
 * (설정 내보내기에서 학습한 단어를 고르면 담는다).
 */
public final class CorrectionMemory {
    /** 이만큼 고르면 자동 수정한다. */
    public static final int CHOSEN = 2;
    static final int MAX = 2000;

    private static final class Entry {
        String target;
        int count;
        long last;

        Entry(String target, int count, long last) {
            this.target = target;
            this.count = count;
            this.last = last;
        }
    }

    private static CorrectionMemory shared;

    /** 키보드 서비스와 설정 화면이 같은 프로세스에서 하나를 같이 쓴다. */
    public static synchronized CorrectionMemory shared(File filesDir) {
        if (shared == null) shared = new CorrectionMemory(new File(filesDir, "picked_corrections.txt"));
        return shared;
    }

    private final File file;
    private Map<String, Entry> map = new HashMap<>();
    private boolean loaded;

    CorrectionMemory(File file) {
        this.file = file;
    }

    /** 파일을 읽는다 (백그라운드 스레드에서 부른다). */
    public synchronized void load() {
        if (loaded) return;
        Map<String, Entry> m = new HashMap<>();
        if (file.exists()) {
            try (BufferedReader r = new BufferedReader(new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))) {
                String line;
                while ((line = r.readLine()) != null && m.size() < MAX) {
                    String[] p = line.split("\t");
                    if (p.length != 4 || p[0].isEmpty() || p[1].isEmpty()) continue;
                    try {
                        m.put(p[0], new Entry(p[1], Integer.parseInt(p[2]), Long.parseLong(p[3])));
                    } catch (NumberFormatException ignored) {
                        // 깨진 줄은 건너뛴다.
                    }
                }
            } catch (IOException ignored) {
                // 읽은 데까지만 쓴다.
            }
        }
        for (Map.Entry<String, Entry> e : map.entrySet()) m.put(e.getKey(), e.getValue());   // 읽기 전에 들어온 것
        map = m;
        loaded = true;
    }

    /** 화면 스레드용: typed를 CHOSEN번 이상 같은 단어로 골랐으면 그 단어 (아직 읽지 않았으면 null). */
    public synchronized String lookupIfLoaded(String typed) {
        if (!loaded) return null;
        Entry e = map.get(typed);
        return e != null && e.count >= CHOSEN ? e.target : null;
    }

    /** typed에서 chosen을 골랐다고 센다 (백그라운드 스레드에서 부른다). */
    public synchronized void record(String typed, String chosen, long now) {
        load();
        Entry e = map.get(typed);
        if (e == null || !e.target.equals(chosen)) {
            e = new Entry(chosen, 0, now);
            map.put(typed, e);
        }
        e.count++;
        e.last = now;
        if (map.size() > MAX) trim();
        save();
    }

    /** typed를 잊는다 (자동 수정을 되돌렸을 때). 있었으면 true. */
    public synchronized boolean forget(String typed) {
        load();
        if (map.remove(typed) == null) return false;
        save();
        return true;
    }

    /** 모두 잊는다 (학습한 단어를 모두 지울 때). */
    public synchronized void clear() {
        map = new HashMap<>();
        loaded = true;
        file.delete();
    }

    /** 내보내기용: {틀린 말, 고른 단어, 횟수, 마지막 시각} 목록. 백그라운드 스레드에서 부른다. */
    public synchronized List<String[]> snapshot() {
        load();
        List<String[]> out = new ArrayList<>(map.size());
        for (Map.Entry<String, Entry> e : map.entrySet()) {
            Entry v = e.getValue();
            out.add(new String[]{e.getKey(), v.target, String.valueOf(v.count), String.valueOf(v.last)});
        }
        return out;
    }

    /** 가져오기용: 전부 rows(snapshot과 같은 모양)로 바꾸고 파일에 쓴다. 깨진 줄은 버린다. 들어간 개수를 돌려준다. */
    public synchronized int replaceAll(List<String[]> rows) {
        Map<String, Entry> m = new HashMap<>();
        for (String[] p : rows) {
            if (m.size() >= MAX) break;
            if (p.length != 4 || !clean(p[0]) || !clean(p[1])) continue;
            try {
                m.put(p[0], new Entry(p[1], Math.max(1, Integer.parseInt(p[2])), Long.parseLong(p[3])));
            } catch (NumberFormatException ignored) {
                // 깨진 줄은 건너뛴다.
            }
        }
        map = m;
        loaded = true;
        if (m.isEmpty()) file.delete();
        else save();
        return m.size();
    }

    private static boolean clean(String s) {
        return s != null && !s.isEmpty() && s.indexOf('\t') < 0 && s.indexOf('\n') < 0;
    }

    /** 가장 오래전에 고른 것부터 10%를 덜어 낸다. */
    private void trim() {
        List<Map.Entry<String, Entry>> all = new ArrayList<>(map.entrySet());
        all.sort((a, b) -> Long.compare(a.getValue().last, b.getValue().last));
        for (int i = 0; i < MAX / 10; i++) map.remove(all.get(i).getKey());
    }

    private void save() {
        File tmp = new File(file.getPath() + ".tmp");
        try (BufferedWriter w = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(tmp), StandardCharsets.UTF_8))) {
            for (Map.Entry<String, Entry> e : map.entrySet()) {
                w.write(e.getKey() + "\t" + e.getValue().target + "\t" + e.getValue().count + "\t" + e.getValue().last + "\n");
            }
        } catch (IOException e) {
            tmp.delete();
            return;
        }
        if (!tmp.renameTo(file)) {
            file.delete();
            tmp.renameTo(file);
        }
    }
}
