package com.alternative_studios.newswipe;

import android.content.SharedPreferences;
import android.os.Handler;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 키보드 사용자화의 되돌리기 기록. 키보드 사용자화 값(Prefs.isEditorKey)이 바뀔 때마다 바뀌기 전 값을 기억해 한 단계로 쌓는다.
 * 한 번에 함께 바뀐 값들(키 하나를 기본값으로, 길게 누르기 '없음' 등)은 한 단계이고, 화면을 한 번 눌렀다 뗄 때까지
 * (슬라이더·손잡이를 끄는 동안) 바뀐 값들도 한 단계로 합친다. 화면을 다시 만들어도(기본값으로 되돌리기, 화면 모드) 남도록 기록은 정적으로 두고,
 * 키보드 사용자화를 떠나면 {@link #clearAll}로 버린다.
 */
final class EditorUndo {
    /** 기억해 둔 값이 '없었음'을 나타내는 표시. */
    private static final Object ABSENT = new Object();
    private static final int MAX_STEPS = 50;

    private static final ArrayDeque<Map<String, Object>> steps = new ArrayDeque<>();
    /** 키보드 사용자화 값의 마지막 모습 (바뀐 값의 이전 값을 알려고 둔다). null이면 아직 시작하지 않았다. */
    private static Map<String, Object> snapshot;
    /** 지금 화면을 누르고 있는 터치의 번호 (0이면 누르고 있지 않다). 한 터치 동안 바뀐 값은 한 단계로 합친다. */
    private static int touch;
    private static int touchSeq;
    /** 마지막 단계를 쌓은 터치의 번호 (0이면 터치 밖에서 쌓았거나 더 합치지 않는다). */
    private static int lastStepTouch;
    /** 되돌리는 중 (그 변경은 기록하지 않는다). */
    private static boolean undoing;

    /**
     * 이 기록을 쓰는 화면 하나(범위)가 시작한 때 쌓여 있던 단계 수. 가로 모드·폴더블 내부 화면 키보드 사용자화는 키보드 사용자화에서
     * 열려 같은 기록을 쓰지만, 그 위의 단계(키보드 사용자화에서 바꾼 값)는 되돌리지 않는다.
     */
    private static int floor;
    private static String scope;
    /** 키보드 사용자화 화면이 열려 있는지 (가로 모드·폴더블 화면을 떠날 때 그 아래 기록을 남길지 정한다). */
    private static boolean rootActive;

    private final Prefs prefs;
    private final Handler handler;
    /** 되돌릴 수 있는지가 바뀔 수 있을 때 부른다 (되돌리기 버튼을 맞춘다). */
    private final Runnable onChange;
    /** 지금 메시지 안에서 바뀐 값들 (다음 메시지에서 한 단계로 묶는다). */
    private Map<String, Object> pending;
    /** pending이 바뀐 터치의 번호. */
    private int pendingTouch;

    EditorUndo(Prefs prefs, Handler handler, Runnable onChange) {
        this.prefs = prefs;
        this.handler = handler;
        this.onChange = onChange;
        if (snapshot == null) snapshot = editorValues();
    }

    /** 키보드 사용자화를 떠난다: 기록을 버린다. */
    static void clearAll() {
        steps.clear();
        snapshot = null;
        lastStepTouch = 0;
        floor = 0;
        scope = null;
        rootActive = false;
    }

    /** 키보드 사용자화 화면이 열렸다: 가로 모드·폴더블 화면은 이 화면에서 열려 기록을 이어 쓴다. */
    static void markRoot() {
        rootActive = true;
    }

    /**
     * 가로 모드·폴더블 내부 화면 키보드 사용자화(범위 id)가 열렸다: 이 화면에서 바꾼 값만 되돌린다. 같은 화면을 다시 만들 때
     * (회전·되돌린 뒤 다시 그리기)는 처음 시작한 자리를 그대로 둔다.
     */
    void enterScope(String id) {
        if (id.equals(scope)) return;
        scope = id;
        floor = steps.size();
    }

    /** 그 화면을 떠난다: 이 화면에서 쌓은 단계는 버린다 (키보드 사용자화에서 열었으면 그 아래 기록은 그대로, 아니면 모두). */
    static void leaveScope() {
        if (scope == null) return;
        if (!rootActive) {
            clearAll();
            return;
        }
        while (steps.size() > floor) steps.removeLast();
        lastStepTouch = 0;
        floor = 0;
        scope = null;
    }

    /** 화면을 누르기 시작했다 (첫 손가락). 뗄 때까지 바뀐 값은 한 단계가 된다. */
    static void touchDown() {
        touch = ++touchSeq;
    }

    /** 화면에서 손을 뗐다 (또는 터치가 취소됐다). 다음 변경은 새 단계다. */
    static void touchUp() {
        touch = 0;
    }

    boolean canUndo() {
        return steps.size() > floor || pending != null;
    }

    private Map<String, Object> editorValues() {
        Map<String, Object> out = new HashMap<>();
        for (Map.Entry<String, ?> e : prefs.raw().getAll().entrySet()) {
            if (Prefs.isEditorKey(e.getKey())) out.put(e.getKey(), e.getValue());
        }
        return out;
    }

    /** 설정 값 key가 바뀌었다 (SharedPreferences 알림). key가 null이면 설정이 통째로 지워졌다 (가져오기 등). */
    void record(String key) {
        if (snapshot == null) return;
        if (key == null) {   // 기록을 새로 시작한다
            snapshot = editorValues();
            steps.clear();
            onChange.run();
            return;
        }
        if (!Prefs.isEditorKey(key)) return;
        Object now = prefs.raw().getAll().get(key);
        Object old = snapshot.containsKey(key) ? snapshot.get(key) : ABSENT;
        if (now == null) snapshot.remove(key);
        else snapshot.put(key, now);
        if (undoing || Objects.equals(old, now == null ? ABSENT : now)) return;
        if (pending == null) {
            pending = new LinkedHashMap<>();
            pendingTouch = touch;
            handler.post(this::seal);
        }
        pending.putIfAbsent(key, old);
    }

    /** 한 메시지 안에서 바뀐 값들을 한 단계로 쌓는다. 앞 단계와 같은 터치 동안 바뀌었으면 앞 단계에 합친다. */
    private void seal() {
        Map<String, Object> step = pending;
        int t = pendingTouch;
        pending = null;
        // 그사이 키보드 사용자화를 떠나 기록을 버렸으면 (clearAll) 쌓지 않는다.
        if (step == null || step.isEmpty() || snapshot == null) return;
        boolean merge = t != 0 && t == lastStepTouch && !steps.isEmpty();
        if (merge) {
            // 앞 단계의 '바뀌기 전 값'은 그대로 둔다 (앞 단계에 없던 값만 더한다).
            Map<String, Object> top = steps.peekLast();
            for (Map.Entry<String, Object> en : step.entrySet()) top.putIfAbsent(en.getKey(), en.getValue());
        } else {
            steps.addLast(step);
        }
        lastStepTouch = t;
        // 합쳐서 결국 원래 값으로 돌아왔으면 (끌다가 제자리로 돌아옴) 되돌릴 것이 없다.
        Map<String, Object> top = steps.peekLast();
        if (top != null && isNoop(top)) {
            steps.removeLast();
            lastStepTouch = 0;   // 그 아래 단계는 다른 조작이라 합치지 않는다
        }
        while (steps.size() > MAX_STEPS) {
            steps.removeFirst();
            if (floor > 0) floor--;
        }
        onChange.run();
    }

    private boolean isNoop(Map<String, Object> step) {
        for (Map.Entry<String, Object> e : step.entrySet()) {
            Object now = snapshot.containsKey(e.getKey()) ? snapshot.get(e.getKey()) : ABSENT;
            if (!Objects.equals(now, e.getValue())) return false;
        }
        return true;
    }

    /** 마지막 단계를 되돌린다: 그 단계에서 바뀐 값들을 바뀌기 전 값으로 (없던 값은 지운다). 되돌린 것이 없으면 false. */
    boolean undo() {
        if (pending != null) seal();
        if (steps.size() <= floor) return false;
        Map<String, Object> step = steps.pollLast();
        if (step == null) return false;
        undoing = true;
        SharedPreferences.Editor e = prefs.raw().edit();
        for (Map.Entry<String, Object> en : step.entrySet()) {
            String k = en.getKey();
            Object v = en.getValue();
            if (v == ABSENT) e.remove(k);
            else if (v instanceof Boolean) e.putBoolean(k, (Boolean) v);
            else if (v instanceof Integer) e.putInt(k, (Integer) v);
            else if (v instanceof Long) e.putLong(k, (Long) v);
            else if (v instanceof Float) e.putFloat(k, (Float) v);
            else if (v instanceof String) e.putString(k, (String) v);
            else if (v instanceof Set) {
                @SuppressWarnings("unchecked") Set<String> set = (Set<String>) v;
                e.putStringSet(k, set);
            }
        }
        e.apply();
        // 바뀐 값 알림이 나중에 오더라도 기록하지 않게, 알림이 다 지난 뒤에 푼다.
        handler.post(() -> {
            undoing = false;
            if (snapshot != null) snapshot = editorValues();   // 그사이 키보드 사용자화를 떠났으면 다시 시작하지 않는다
        });
        lastStepTouch = 0;
        onChange.run();
        return true;
    }
}
