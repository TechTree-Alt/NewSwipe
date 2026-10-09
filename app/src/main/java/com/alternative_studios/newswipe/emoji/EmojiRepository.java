package com.alternative_studios.newswipe.emoji;

import android.content.Context;
import android.graphics.Paint;
import android.os.Handler;
import android.os.Looper;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Executor;

/**
 * 이모지 데이터를 처음 쓸 때 백그라운드에서 읽어 둔다.
 * 기기 글꼴로 그릴 수 없는 이모지(아직 지원하지 않는 최신 이모지)는 빼고 보여 준다.
 * 시스템 이모지 글꼴이 업데이트되면 다음 로드 때 자동으로 나타난다.
 */
public final class EmojiRepository {
    public interface Callback {
        void onLoaded(EmojiData data);
    }

    private static final int MAX_RECENT = 32;
    private static final int MAX_PINNED = 32;

    private final Context app;
    /** 키보드의 백그라운드 작업 스레드를 같이 쓴다 (스레드를 따로 만들지 않는다). */
    private final Executor background;
    private final Handler main = new Handler(Looper.getMainLooper());
    private volatile EmojiData data;
    private boolean loading;
    /** 읽는 도중에 release()가 불렸으면, 다 읽은 뒤 붙잡지 않고 버린다. */
    private boolean discardWhenLoaded;
    private final List<Callback> waiting = new ArrayList<>();

    public EmojiRepository(Context context, Executor background) {
        app = context.getApplicationContext();
        this.background = background;
    }

    public EmojiData peek() {
        return data;
    }

    public void load(Callback cb) {
        if (data != null) {
            cb.onLoaded(data);
            return;
        }
        waiting.add(cb);
        discardWhenLoaded = false;
        if (loading) return;
        loading = true;
        Runnable task = () -> {
            EmojiData d = null;
            Paint paint = new Paint();
            try (InputStreamReader r = new InputStreamReader(app.getAssets().open("emoji.tsv"),
                    StandardCharsets.UTF_8)) {
                d = EmojiData.parse(r, paint::hasGlyph);
            } catch (IOException ignored) {
                // 에셋이 없을 수 없지만, 실패해도 키보드는 계속 동작해야 한다.
            }
            final EmojiData result = d;
            main.post(() -> {
                loading = false;
                if (discardWhenLoaded) {
                    discardWhenLoaded = false;
                    return;
                }
                data = result;
                List<Callback> cbs = new ArrayList<>(waiting);
                waiting.clear();
                if (result != null) for (Callback c : cbs) c.onLoaded(result);
            });
        };
        try {
            background.execute(task);
        } catch (java.util.concurrent.RejectedExecutionException e) {
            // 키보드 서비스가 끝나는 중이라 작업 줄이 닫혔다. 기다리던 것도 버린다.
            loading = false;
            waiting.clear();
        }
    }

    /** 메모리가 부족할 때 데이터를 놓아 준다. 다음에 열 때 다시 읽는다. */
    public void release() {
        // 기다리던 콜백은 놓아 주는 패널을 붙잡고 있으므로 함께 지운다.
        waiting.clear();
        data = null;
        if (loading) discardWhenLoaded = true;
    }

    public static List<String> parseRecent(String stored) {
        if (stored == null || stored.isEmpty()) return new ArrayList<>();
        return new ArrayList<>(Arrays.asList(stored.split(" ")));
    }

    public static String pushRecent(String stored, String emoji) {
        List<String> list = parseRecent(stored);
        list.remove(emoji);
        list.add(0, emoji);
        while (list.size() > MAX_RECENT) list.remove(list.size() - 1);
        return String.join(" ", list);
    }

    /** 고정돼 있으면 풀고, 아니면 맨 뒤에 고정한다 (먼저 고정한 것이 앞). */
    public static String togglePinned(String stored, String emoji) {
        List<String> list = parseRecent(stored);
        if (!list.remove(emoji)) {
            list.add(emoji);
            while (list.size() > MAX_PINNED) list.remove(0);
        }
        return String.join(" ", list);
    }

    /** 최근 이모지 탭에 보일 순서: 고정한 이모지, 그 뒤에 나머지 최근 이모지. */
    public static List<String> withPinned(List<String> pinned, List<String> recent) {
        List<String> all = new ArrayList<>(pinned);
        for (String e : recent) if (!pinned.contains(e)) all.add(e);
        return all;
    }
}
