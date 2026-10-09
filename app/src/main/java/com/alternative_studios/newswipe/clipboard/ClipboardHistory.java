package com.alternative_studios.newswipe.clipboard;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 클립보드 기록. 최근 30개(그중 이미지는 5개)를 앱 전용 저장소에 보관하고,
 * 고정하지 않은 항목은 24시간이 지나면 지운다.
 * 이미지는 원본 파일과 작은 미리보기 파일을 {@link #IMAGE_DIR}에 두고, 항목이 사라지면 파일도 함께 지운다.
 * 안드로이드 의존성이 없어 단위 테스트할 수 있다 (파일 복사와 미리보기 만들기는 {@link ClipImages}).
 */
public final class ClipboardHistory {
    public static final int MAX_ITEMS = 30;
    public static final long EXPIRE_MS = 24L * 60 * 60 * 1000;
    /** 너무 긴 텍스트는 잘라서 저장한다. */
    public static final int MAX_LENGTH = 20_000;
    /** 고정할 수 있는 항목 수. 이미 이보다 많이 고정돼 있던 기록은 그대로 두고, 새로 고정하는 것만 막는다. */
    public static final int MAX_PINNED = 10;
    /** 고정하지 않은 이미지는 최근 이만큼만 둔다 (MAX_ITEMS 안에 함께 센다). */
    public static final int MAX_IMAGES = 5;
    /** 이보다 큰 이미지는 저장하지 않는다. */
    public static final long MAX_IMAGE_BYTES = 30L * 1024 * 1024;
    /** 이미지 파일을 두는 폴더 (기록 파일과 같은 폴더 아래). */
    public static final String IMAGE_DIR = "clip_images";
    private static final String THUMB_SUFFIX = ".t";

    public static final class Item {
        /** 텍스트. 이미지면 이미지 파일 이름(내용의 해시). */
        public final String text;
        /** 이미지의 형식 (예: image/png). 텍스트 항목이면 null. */
        public final String mime;
        public final long time;
        public boolean pinned;

        Item(String text, long time, boolean pinned) {
            this(text, null, time, pinned);
        }

        Item(String text, String mime, long time, boolean pinned) {
            this.text = text;
            this.mime = mime;
            this.time = time;
            this.pinned = pinned;
        }

        public boolean isImage() {
            return mime != null;
        }

        /** 화면에서 항목을 구별하는 값 (텍스트와 이미지 이름이 겹치지 않게). */
        public String key() {
            return isImage() ? "\0img:" + text : text;
        }
    }

    private final File file;
    private final File imageDir;
    private final List<Item> items = new ArrayList<>();   // 최신 항목이 앞
    private boolean loaded;
    private long knownStamp;

    public ClipboardHistory(File file) {
        this.file = file;
        this.imageDir = file == null ? null : new File(file.getAbsoluteFile().getParentFile(), IMAGE_DIR);
    }

    /** filesDir 아래의 이미지 폴더 (이미지를 내보내는 ContentProvider용). */
    public static File imageDir(File filesDir) {
        return new File(filesDir, IMAGE_DIR);
    }

    public File imageDir() {
        return imageDir;
    }

    /** 이미지 원본 파일. */
    public static File original(File imageDir, String id) {
        return new File(imageDir, id);
    }

    /** 이미지 미리보기 파일 (긴 변 256px). */
    public static File thumbnail(File imageDir, String id) {
        return new File(imageDir, id + THUMB_SUFFIX);
    }

    /** 이미지 이름은 내용 해시의 16진수 32자리다. 경로를 벗어나는 이름을 막는 데도 쓴다. */
    public static boolean isImageId(String id) {
        if (id == null || id.length() != 32) return false;
        for (int i = 0; i < id.length(); i++) {
            char c = id.charAt(i);
            if (!(c >= '0' && c <= '9' || c >= 'a' && c <= 'f')) return false;
        }
        return true;
    }

    /** image/로 시작하고 형식 이름에 쓰는 글자만 있으면 그대로, 아니면 null. */
    public static String cleanMime(String mime) {
        if (mime == null || !mime.startsWith("image/") || mime.length() <= 6 || mime.length() > 100) return null;
        for (int i = 0; i < mime.length(); i++) {
            char c = mime.charAt(i);
            if (!(Character.isLetterOrDigit(c) && c < 128 || c == '/' || c == '.' || c == '+' || c == '-' || c == '_')) {
                return null;
            }
        }
        return mime.toLowerCase(java.util.Locale.ROOT);
    }

    /** 새로 복사된 텍스트를 맨 앞에 넣는다. 같은 텍스트가 있으면 앞으로 옮긴다. */
    public synchronized void add(String text, long now) {
        load();
        if (text == null || text.trim().isEmpty()) return;
        if (text.length() > MAX_LENGTH) text = text.substring(0, MAX_LENGTH);
        boolean pinned = false;
        for (int i = items.size() - 1; i >= 0; i--) {
            Item it = items.get(i);
            if (!it.isImage() && it.text.equals(text)) {
                pinned = it.pinned;
                items.remove(i);
            }
        }
        items.add(0, new Item(text, now, pinned));
        trim(now);
        save();
    }

    /**
     * 이미지를 맨 앞에 넣는다. 원본과 미리보기 파일은 이미 {@link #imageDir()}에 있어야 한다.
     * 같은 이미지(같은 이름)이 있으면 고정 여부를 이어받아 앞으로 옮긴다.
     */
    public synchronized void addImage(String id, String mime, long now) {
        load();
        mime = cleanMime(mime);
        if (!isImageId(id) || mime == null) return;
        boolean pinned = false;
        for (int i = items.size() - 1; i >= 0; i--) {
            Item it = items.get(i);
            if (it.isImage() && it.text.equals(id)) {
                pinned = it.pinned;
                items.remove(i);
            }
        }
        items.add(0, new Item(id, mime, now, pinned));
        trim(now);
        save();
    }

    /** 현재 항목 (고정 항목 먼저, 그다음 최신순). */
    public synchronized List<Item> items(long now) {
        load();
        if (trim(now)) save();
        List<Item> out = new ArrayList<>(items.size());
        for (Item i : items) if (i.pinned) out.add(i);
        for (Item i : items) if (!i.pinned) out.add(i);
        return Collections.unmodifiableList(out);
    }

    /** 클립보드 창 맨 위에 보이는 고정 항목(고정한 것 중 가장 최근에 복사한 것). 없으면 null. */
    public synchronized Item firstPinned(long now) {
        load();
        if (trim(now)) save();
        for (Item i : items) if (i.pinned) return i;
        return null;
    }

    /** 기한이 지난 항목을 지우고, 기록에 없는 이미지 파일(복사 중에 끊긴 것 등)도 지운다. 저장 공간 정리용. */
    public synchronized void prune(long now) {
        load();
        if (trim(now)) save();
        if (imageDir == null) return;
        File[] files = imageDir.listFiles();
        if (files == null) return;
        java.util.Set<String> keep = new java.util.HashSet<>();
        for (Item i : items) {
            if (!i.isImage()) continue;
            keep.add(i.text);
            keep.add(i.text + THUMB_SUFFIX);
        }
        for (File f : files) {
            //noinspection ResultOfMethodCallIgnored
            if (!keep.contains(f.getName())) f.delete();
        }
        if (keep.isEmpty()) {
            //noinspection ResultOfMethodCallIgnored
            imageDir.delete();   // 빈 폴더도 남기지 않는다
        }
    }

    /**
     * 고정하거나 고정을 푼다. 화면이 들고 있던 항목은 그사이 파일을 다시 읽었으면 다른 객체일 수 있어
     * 텍스트(이미지는 이름)로 찾는다 (같은 텍스트는 add에서 하나로 합치므로 텍스트가 곧 항목이다).
     * 이미 MAX_PINNED개를 고정했으면 고정하지 않고 false를 돌려준다.
     */
    public synchronized boolean setPinned(Item item, boolean pinned) {
        load();
        Item it = find(item);
        if (it == null || it.pinned == pinned) return true;
        if (pinned && pinnedCount() >= MAX_PINNED) return false;
        it.pinned = pinned;
        save();
        return true;
    }

    public synchronized void remove(Item item) {
        load();
        Item it = find(item);
        if (it == null) return;
        items.remove(it);
        deleteFiles(it);
        save();
    }

    /**
     * 백업용: 보관 중인 텍스트 항목 {텍스트, 시각, 고정 여부("1"/"0")}. 고정 여부와 상관없이 저장된 순서(최신이 앞) 그대로.
     * 이미지는 백업에 넣지 않는다.
     */
    public synchronized List<String[]> snapshot(long now) {
        load();
        if (trim(now)) save();
        List<String[]> out = new ArrayList<>(items.size());
        for (Item i : items) {
            if (!i.isImage()) out.add(new String[]{i.text, Long.toString(i.time), i.pinned ? "1" : "0"});
        }
        return out;
    }

    /**
     * 백업에서 가져온 항목으로 텍스트 기록 전체를 바꾼다 (snapshot과 같은 형식). 이미지는 백업에 없으므로 그대로 둔다.
     * 바뀐 뒤의 항목 수를 돌려준다.
     */
    public synchronized int replaceAll(List<String[]> entries, long now) {
        load();
        for (int i = items.size() - 1; i >= 0; i--) {
            if (!items.get(i).isImage()) items.remove(i);
        }
        for (String[] e : entries) {
            if (e.length != 3 || e[0] == null || e[0].trim().isEmpty()) continue;
            String text = e[0].length() > MAX_LENGTH ? e[0].substring(0, MAX_LENGTH) : e[0];
            try {
                items.add(new Item(text, Long.parseLong(e[1]), "1".equals(e[2])));
            } catch (NumberFormatException ignored) {
                // 깨진 항목은 건너뛴다.
            }
        }
        // 남겨 둔 이미지와 가져온 텍스트를 최신순으로 합친다 (sort는 안정 정렬이라 같은 시각은 순서를 지킨다).
        items.sort((a, b) -> Long.compare(b.time, a.time));
        trim(now);
        save();
        return items.size();
    }

    private Item find(Item item) {
        for (Item i : items) {
            if (i.isImage() == item.isImage() && i.text.equals(item.text)) return i;
        }
        return null;
    }

    private int pinnedCount() {
        int n = 0;
        for (Item i : items) if (i.pinned) n++;
        return n;
    }

    /** 메모리에서 내린다. 다음에 쓸 때 파일에서 다시 읽는다 (항목 하나가 최대 MAX_LENGTH자라 들고 있지 않는다). */
    public synchronized void release() {
        items.clear();
        loaded = false;
    }

    /** 고정하지 않은 항목을 모두 지운다. */
    public synchronized void clearUnpinned() {
        load();
        for (int i = items.size() - 1; i >= 0; i--) {
            if (!items.get(i).pinned) deleteFiles(items.remove(i));
        }
        save();
    }

    /** 보관 중인 이미지가 있는지 (고정한 것 포함). */
    public synchronized boolean hasImages(long now) {
        load();
        if (trim(now)) save();
        for (Item i : items) if (i.isImage()) return true;
        return false;
    }

    /** 이미지 항목을 고정한 것까지 모두 지우고 이미지 폴더도 비운다 (이미지 저장을 껐을 때). */
    public synchronized void clearImages() {
        load();
        for (int i = items.size() - 1; i >= 0; i--) {
            if (items.get(i).isImage()) items.remove(i);
        }
        save();
        prune(Long.MIN_VALUE / 2);   // 기록에 없는 이미지 파일이 되었으니 모두 지워진다
    }

    public synchronized void clearAll() {
        load();
        items.clear();
        save();
        prune(Long.MIN_VALUE / 2);   // 이미지 폴더를 통째로 비운다
    }

    /**
     * 오래된 항목, 개수 초과분, 원본 파일이 사라진 이미지를 지운다. 바뀌었으면 true.
     * 이미지는 고정하지 않은 것 중 최근 MAX_IMAGES개만 남기고, MAX_ITEMS에도 함께 센다.
     */
    private boolean trim(long now) {
        boolean changed = false;
        int unpinned = 0;
        int images = 0;
        for (int i = 0; i < items.size(); i++) {
            Item it = items.get(i);
            if (it.isImage() && imageDir != null && !original(imageDir, it.text).exists()) {
                items.remove(i--);
                deleteFiles(it);
                changed = true;
                continue;
            }
            if (it.pinned) continue;
            if (now - it.time > EXPIRE_MS || unpinned >= MAX_ITEMS || it.isImage() && images >= MAX_IMAGES) {
                items.remove(i--);
                deleteFiles(it);
                changed = true;
            } else {
                unpinned++;
                if (it.isImage()) images++;
            }
        }
        return changed;
    }

    /** 이미지 항목의 원본·미리보기 파일을 지운다. 같은 이미지가 아직 기록에 남아 있으면 두지 않으므로 바로 지워도 된다. */
    private void deleteFiles(Item it) {
        if (!it.isImage() || imageDir == null || !isImageId(it.text)) return;
        //noinspection ResultOfMethodCallIgnored
        original(imageDir, it.text).delete();
        //noinspection ResultOfMethodCallIgnored
        thumbnail(imageDir, it.text).delete();
    }

    // ---------------------------------------------------------------- 저장 (한 줄에 하나, 탭 구분)
    // 텍스트: 고정(1/0) \t 시각 \t 텍스트(이스케이프)
    // 이미지:   i + 고정(1/0) \t 시각 \t 이름 \t 형식

    /** 파일이 다른 곳(설정 화면)에서 바뀌었으면 다시 읽는다. */
    private void load() {
        long stamp = stamp();
        if (loaded && stamp == knownStamp) return;
        loaded = true;
        knownStamp = stamp;
        items.clear();
        if (file == null || !file.exists()) return;
        try (BufferedReader r = new BufferedReader(new InputStreamReader(new FileInputStream(file),
                StandardCharsets.UTF_8))) {
            String line;
            while ((line = r.readLine()) != null) {
                String[] f = line.split("\t", 4);
                if (f.length < 3) continue;
                try {
                    if (f[0].startsWith("i")) {
                        String mime = f.length == 4 ? cleanMime(f[3]) : null;
                        if (!isImageId(f[2]) || mime == null) continue;
                        items.add(new Item(f[2], mime, Long.parseLong(f[1]), "i1".equals(f[0])));
                    } else {
                        // 텍스트에 탭은 이스케이프되어 있으므로 셋째 칸부터 끝까지가 텍스트다.
                        String text = f.length == 4 ? f[2] + "\t" + f[3] : f[2];
                        items.add(new Item(unescape(text), Long.parseLong(f[1]), "1".equals(f[0])));
                    }
                } catch (NumberFormatException ignored) {
                    // 깨진 줄은 건너뛴다.
                }
            }
        } catch (IOException ignored) {
            // 읽을 수 없으면 빈 기록으로 시작한다.
        }
    }

    private void save() {
        if (file == null) return;
        File tmp = new File(file.getPath() + ".tmp");
        try (Writer w = new OutputStreamWriter(new FileOutputStream(tmp), StandardCharsets.UTF_8)) {
            for (Item i : items) {
                if (i.isImage()) w.write('i');
                w.write(i.pinned ? "1" : "0");
                w.write('\t');
                w.write(Long.toString(i.time));
                w.write('\t');
                w.write(i.isImage() ? i.text + "\t" + i.mime : escape(i.text));
                w.write('\n');
            }
        } catch (IOException e) {
            return;
        }
        if (!tmp.renameTo(file)) {
            //noinspection ResultOfMethodCallIgnored
            tmp.delete();
        }
        knownStamp = stamp();
    }

    private long stamp() {
        if (file == null || !file.exists()) return 0;
        return file.lastModified() * 31 + file.length();
    }

    static String escape(String s) {
        StringBuilder b = new StringBuilder(s.length() + 8);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\\') b.append("\\\\");
            else if (c == '\n') b.append("\\n");
            else if (c == '\r') b.append("\\r");
            else if (c == '\t') b.append("\\t");
            else b.append(c);
        }
        return b.toString();
    }

    static String unescape(String s) {
        StringBuilder b = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\\' && i + 1 < s.length()) {
                char n = s.charAt(++i);
                b.append(n == 'n' ? '\n' : n == 'r' ? '\r' : n == 't' ? '\t' : n);
            } else {
                b.append(c);
            }
        }
        return b.toString();
    }
}
