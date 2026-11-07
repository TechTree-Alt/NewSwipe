package com.alternative_studios.newswipe;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.os.Looper;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ScrollView;
import android.widget.TextView;

import com.alternative_studios.newswipe.keyboard.Key;
import com.alternative_studios.newswipe.keyboard.KeyboardLayout;
import com.alternative_studios.newswipe.keyboard.KeyboardView;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import org.robolectric.shadows.ShadowDialog;

import java.io.File;
import java.io.FileOutputStream;

import static org.junit.Assert.*;
import static org.junit.Assert.assertArrayEquals;
import static org.robolectric.Shadows.shadowOf;

/**
 * 키보드 편집 화면의 UI 시험 (Robolectric): 미리보기 페이지·영역·키 고르기, 설정 창, 손잡이와 슬라이더, 되돌리기,
 * 기본값으로 되돌리기, 바로 적용 등을 실제 화면을 만들어 눌러 보며 확인한다. -Dshots=폴더를 주면 화면을 찍어 남긴다.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 36, qualifiers = "w411dp-h914dp-port-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class EditorUiTest {
    /** 화면을 찍어 둘 폴더 (-Dshots=폴더로 줄 때만 찍고 과정을 출력한다). */
    static final File OUT = System.getProperty("shots") != null ? new File(System.getProperty("shots")) : null;

    static void save(Bitmap b, String name) throws Exception {
        if (OUT == null) return;
        OUT.mkdirs();
        try (FileOutputStream f = new FileOutputStream(new File(OUT, name + ".png"))) {
            b.compress(Bitmap.CompressFormat.PNG, 100, f);
        }
    }

    static void idle() { shadowOf(Looper.getMainLooper()).idle(); }

    static EditorSheet sheetOf(Activity a) {
        try {
            java.lang.reflect.Field f = SettingsActivity.class.getDeclaredField("sheet");
            f.setAccessible(true);
            return (EditorSheet) f.get(a);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /** 키 편집 창이 열려 있으면 그 스크롤, 아니면 설정 목록 스크롤. */
    static ScrollView cur(Activity a) {
        try {
            java.lang.reflect.Method open = SettingsActivity.class.getDeclaredMethod("panelOpen");
            open.setAccessible(true);
            if ((Boolean) open.invoke(a)) return sheetOf(a).scroll();
            java.lang.reflect.Field fld = SettingsActivity.class.getDeclaredField("scrollView");
            fld.setAccessible(true);
            return (ScrollView) fld.get(a);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    static void shot(Activity a, String name) throws Exception {
        shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(700));
        idle();
        View root = a.getWindow().getDecorView();
        Bitmap b = Bitmap.createBitmap(root.getWidth(), root.getHeight(), Bitmap.Config.ARGB_8888);
        root.draw(new Canvas(b));
        save(b, name);
    }

    static <T extends View> T find(View v, Class<T> c) {
        if (c.isInstance(v)) return c.cast(v);
        if (v instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) {
                T t = find(g.getChildAt(i), c);
                if (t != null) return t;
            }
        }
        return null;
    }

    static void texts(View v, StringBuilder out, String indent) {
        if (v.getVisibility() != View.VISIBLE) return;
        if (v instanceof TextView) {
            String t = ((TextView) v).getText().toString();
            if (!t.isEmpty()) out.append(indent).append(t.replace('\n', '/')).append('\n');
        }
        if (v instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) texts(g.getChildAt(i), out, indent);
        }
    }

    static String visibleTexts(View v) {
        StringBuilder b = new StringBuilder();
        texts(v, b, "  ");
        return b.toString();
    }

    static SettingsActivity launch(String section) {
        EditorUndo.clearAll();   // 앞 시험의 되돌리기 기록 (정적) 비우기
        Intent i = new Intent(RuntimeEnvironment.getApplication(), SettingsActivity.class);
        if (section != null) i.putExtra("section", section);
        ActivityController<SettingsActivity> c = Robolectric.buildActivity(SettingsActivity.class, i).setup();
        idle();
        return c.get();
    }

    static long t0;

    static void touch(View target, int action, float x, float y) {
        MotionEvent e = MotionEvent.obtain(t0, SystemClock.uptimeMillis(), action, x, y, 0);
        target.dispatchTouchEvent(e);
        e.recycle();
    }

    static void tap(View overlay, float x, float y) {
        t0 = SystemClock.uptimeMillis();
        touch(overlay, MotionEvent.ACTION_DOWN, x, y);
        touch(overlay, MotionEvent.ACTION_UP, x, y);
        idle();
    }

    static Prefs DBG;
    static void drag(View overlay, float x, float y, float dx, float dy) {
        t0 = SystemClock.uptimeMillis();
        touch(overlay, MotionEvent.ACTION_DOWN, x, y);
        for (int s = 1; s <= 10; s++) {
            touch(overlay, MotionEvent.ACTION_MOVE, x + dx * s / 10f, y + dy * s / 10f);
            idle();
            if (DBG != null) log("  step " + s + " gap=" + DBG.keyGapXDp() + " raw=" + DBG.raw().getInt(Prefs.KEY_GAP_X, -1));
        }
        touch(overlay, MotionEvent.ACTION_UP, x + dx, y + dy);
        idle();
    }

    static Key key(KeyboardPreview p, String slot, int type) {
        for (KeyboardLayout.Row r : p.layout().rows) for (Key k : r.keys) if (k.type == type && (slot == null || slot.equals(k.slot()))) return k;
        throw new AssertionError("no key " + slot + " " + type);
    }

    static void tapKey(KeyboardPreview p, Key k) {
        KeyboardView kv = find(p, KeyboardView.class);
        tap(p.getChildAt(1), k.rect.centerX(), kv.getTop() + k.rect.centerY());
    }

    static void log(String s) {
        if (OUT != null) System.out.println(s);
    }

    @Test
    public void editor() throws Exception {
        SettingsActivity a = launch("keyboard");
        Prefs prefs = new Prefs(a);
        shot(a, "01-overview");
        KeyboardPreview p = find(a.getWindow().getDecorView(), KeyboardPreview.class);
        p.setLayer(KeyboardPreview.LAYER_KEY);
        idle();
        assertNotNull(p);
        log("preview " + p.getWidth() + "x" + p.getHeight() + " at y=" + p.getTop());
        View overlay = p.getChildAt(1);
        ScrollView scroll = find(a.getWindow().getDecorView(), ScrollView.class);
        for (View v = scroll; v != null; v = v.getParent() instanceof View ? (View) v.getParent() : null)
            log("H " + v.getClass().getSimpleName() + " h=" + v.getHeight() + " mh=" + v.getMeasuredHeight() + " lp=" + (v.getLayoutParams() == null ? "-" : v.getLayoutParams().height));
        log("OVERVIEW:\n" + visibleTexts(cur(a)).substring(0, Math.min(1500, visibleTexts(cur(a)).length())));

        // ---- 키 고르기: ㄱ
        tapKey(p, key(p, "ㄱ", Key.CHAR));
        assertNotNull(p.selection());
        shot(a, "02-key-giyeok");
        cur(a).scrollTo(0, 1000);
        shot(a, "02b-key-giyeok-swipe");
        cur(a).scrollTo(0, 99999);
        shot(a, "02c-key-giyeok-end");
        cur(a).scrollTo(0, 0);
        log("PANEL ㄱ:\n" + visibleTexts(cur(a)));

        // 아래 칸 누르기 → 규칙 설명 창
        ViewGroup panel = (ViewGroup) cur(a).getChildAt(0);
        View down = findText(panel, "ㄲ");
        assertNotNull("down cell", down);
        down.performClick();
        idle();
        AlertDialog d = (AlertDialog) ShadowDialog.getLatestDialog();
        assertNotNull(d);
        log("DIALOG: " + shadowOf(d).getTitle());
        shot(a, "03-dialog");
        d.dismiss();
        idle();

        // ---- 키를 고른 동안에는 키 손잡이만: 키보드 높이·여백 손잡이는 숨는다
        java.lang.reflect.Method shownM = KeyboardPreview.class.getDeclaredMethod("handleShown", KeyboardPreview.Handle.class);
        shownM.setAccessible(true);
        log("selected: height=" + shownM.invoke(p, KeyboardPreview.Handle.values()[2]) + " padL=" + shownM.invoke(p, KeyboardPreview.Handle.values()[3]));
        assertFalse((Boolean) shownM.invoke(p, KeyboardPreview.Handle.values()[2]));
        assertFalse((Boolean) shownM.invoke(p, KeyboardPreview.Handle.values()[3]));
        assertFalse((Boolean) shownM.invoke(p, KeyboardPreview.Handle.values()[0]));
        a.onBackPressed(); idle();
        log("deselected: height=" + shownM.invoke(p, KeyboardPreview.Handle.values()[2]) + " padL=" + shownM.invoke(p, KeyboardPreview.Handle.values()[3]));
        assertFalse((Boolean) shownM.invoke(p, KeyboardPreview.Handle.values()[2]));   // 키보드 높이는 글자 키 영역을 골랐을 때만
        assertFalse((Boolean) shownM.invoke(p, KeyboardPreview.Handle.values()[3]));   // 여백 손잡이는 키보드 페이지에만
        assertFalse((Boolean) shownM.invoke(p, KeyboardPreview.Handle.values()[6]));
        assertFalse((Boolean) shownM.invoke(p, KeyboardPreview.Handle.values()[0]));   // 도구 막대 높이는 도구 막대를 골랐을 때만
        p.setLayer(KeyboardPreview.LAYER_BOARD);
        shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(400));
        assertFalse((Boolean) shownM.invoke(p, KeyboardPreview.Handle.values()[3]));   // 누르기 전에는 손잡이 없음
        p.select(KeyboardPreview.Selection.board());
        idle();
        log("board: padL=" + shownM.invoke(p, KeyboardPreview.Handle.values()[3]) + " padT=" + shownM.invoke(p, KeyboardPreview.Handle.values()[6]) + " padB=" + shownM.invoke(p, KeyboardPreview.Handle.values()[5])
                + " padR=" + shownM.invoke(p, KeyboardPreview.Handle.values()[4]) + " height=" + shownM.invoke(p, KeyboardPreview.Handle.values()[2]));
        assertTrue((Boolean) shownM.invoke(p, KeyboardPreview.Handle.values()[3]) && (Boolean) shownM.invoke(p, KeyboardPreview.Handle.values()[4]) && (Boolean) shownM.invoke(p, KeyboardPreview.Handle.values()[5])
                && (Boolean) shownM.invoke(p, KeyboardPreview.Handle.values()[6]));
        assertFalse((Boolean) shownM.invoke(p, KeyboardPreview.Handle.values()[2]) || (Boolean) shownM.invoke(p, KeyboardPreview.Handle.values()[0]));
        shot(a, "05b-board-layer");
        // ---- 크기 손잡이: 키보드 높이 (글자 줄 맨 위 가운데) 위로 60px
        int before = prefs.keyboardHeight();
        KeyboardView kv = find(p, KeyboardView.class);
        // 위쪽 여백 손잡이 (아무것도 고르지 않았을 때, 자판 위 가장자리)
        int padTop0 = prefs.padTopDp();
        java.lang.reflect.Method hcTop = KeyboardPreview.class.getDeclaredMethod("handleCenter", KeyboardPreview.Handle.class, float[].class);
        hcTop.setAccessible(true);
        float[] tp = new float[2];
        hcTop.invoke(p, KeyboardPreview.Handle.values()[6], tp);
        drag(overlay, tp[0], tp[1], 0, -150);
        log("padTop " + padTop0 + " -> " + prefs.padTopDp());
        assertTrue(prefs.padTopDp() > padTop0);
        // 위쪽 여백은 도구 막대 위에 온다: 도구 막대는 여백 안쪽, 자판은 도구 막대 바로 밑
        View topBar = ((ViewGroup) p.getChildAt(0)).getChildAt(0);
        float dn = a.getResources().getDisplayMetrics().density;
        log("raised bar top=" + topBar.getTop() + " keys top=" + kv.getTop() + " bar bottom=" + topBar.getBottom());
        assertEquals(Math.round(prefs.padTopDp() * dn), topBar.getTop());
        assertEquals(topBar.getBottom(), kv.getTop());
        shot(a, "05c-top-padding-above-toolbar");
        prefs.raw().edit().putInt(Prefs.PAD_TOP, 0).commit();
        idle();
        // 왼쪽 여백 오른쪽으로 끌기
        int padBefore = prefs.padLeftDp();
        float midY = kv.getTop() + kv.getHeight() / 2f;
        drag(overlay, Math.max(prefs.padLeftDp(), 3) * a.getResources().getDisplayMetrics().density, midY - 0, 60, 0);
        log("padLeft " + padBefore + " -> " + prefs.padLeftDp());
        assertTrue(prefs.padLeftDp() > padBefore);
        p.setLayer(KeyboardPreview.LAYER_KEY);
        shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(400));
        p.select(KeyboardPreview.Selection.area(KeyboardPreview.AREA_LETTERS));
        idle();
        android.graphics.RectF letters = new android.graphics.RectF();
        p.areaBounds(KeyboardPreview.AREA_LETTERS, letters);
        float[] knob = handle(p, 2);   // ↕ 버튼
        drag(overlay, knob[0], knob[1], 0, -170);
        p.select(null);
        idle();
        log("height " + before + " -> " + prefs.keyboardHeight());
        assertTrue(prefs.keyboardHeight() > before);


        shot(a, "04-after-drags");
        a.onBackPressed(); idle();

        // ---- 스페이스바
        tapKey(p, key(p, null, Key.SPACE));
        shot(a, "05-space");
        log("PANEL space:\n" + visibleTexts(cur(a)));
        // ---- 지우기
        tapKey(p, key(p, null, Key.DELETE));
        log("PANEL delete:\n" + visibleTexts(cur(a)));
        shot(a, "06-delete");

        // ---- 도구 막대 버튼 (첫 버튼)
        ViewGroup column = (ViewGroup) p.getChildAt(0);
        ViewGroup bar = (ViewGroup) column.getChildAt(0);
        View first = bar.getChildAt(0);
        tap(overlay, first.getLeft() + first.getWidth() / 2f, bar.getTop() + first.getHeight() / 2f);
        log("PANEL toolbutton:\n" + visibleTexts(cur(a)));
        shot(a, "07-toolbutton");
        // 도구 막대 빈 곳
        tap(overlay, p.getWidth() / 2f, bar.getTop() + bar.getHeight() / 2f);
        log("PANEL toolbar:\n" + visibleTexts(cur(a)));
        shot(a, "08-toolbar");

        // ---- 뒤로 가기로 닫기
        a.onBackPressed();
        idle();
        assertNull(p.selection());
        assertTrue(visibleTexts(cur(a)).contains("키 모양") && visibleTexts(cur(a)).contains("키 사이 좌우 여백"));
        assertFalse(visibleTexts(cur(a)).contains("글자 키 높이"));

        // ---- 영어 자판, 기호 자판
        p.setTab(KeyboardPreview.TAB_ENGLISH);
        idle();
        tapKey(p, key(p, "q", Key.CHAR));
        shot(a, "09-english-q");
        log("PANEL q:\n" + visibleTexts(cur(a)));
        p.setTab(KeyboardPreview.TAB_SYMBOLS);
        idle();
        shot(a, "10-symbols");
        tapKey(p, key(p, null, Key.SYMBOL_PAGE));
        shot(a, "11-symbols2");

        // ---- 숫자 줄 켜고 숫자 키
        prefs.raw().edit().putBoolean(Prefs.NUMBER_ROW, true).apply();
        p.setTab(KeyboardPreview.TAB_KOREAN);
        idle();
        tapKey(p, key(p, "1", Key.CHAR));
        shot(a, "12-number");
        log("PANEL 1:\n" + visibleTexts(cur(a)));
        // 숫자 줄 손잡이
        a.onBackPressed(); idle();   // 크기 손잡이는 고른 키가 없을 때만 보인다
        int nrBefore = prefs.numberRowHeight();
        p.select(KeyboardPreview.Selection.area(KeyboardPreview.AREA_NUMBER));
        idle();
        android.graphics.RectF nr = new android.graphics.RectF();
        p.areaBounds(KeyboardPreview.AREA_NUMBER, nr);
        java.lang.reflect.Method hc = KeyboardPreview.class.getDeclaredMethod("handleCenter", KeyboardPreview.Handle.class, float[].class);
        hc.setAccessible(true);
        float[] pt = new float[2];
        hc.invoke(p, KeyboardPreview.Handle.values()[1], pt);
        log("number row handle y=" + pt[1] + " outline top=" + nr.top);
        float inset = 9 * a.getResources().getDisplayMetrics().density;
        assertEquals(nr.centerY(), pt[1], 1f);   // 숫자 줄 가운데의 ↕ 버튼
        shot(a, "12b-number-handle");
        drag(overlay, pt[0], pt[1], 0, -60);
        log("numberRow " + nrBefore + " -> " + prefs.numberRowHeight());
        // 도구 막대 손잡이
        int tbBefore = prefs.toolbarHeight();
        p.select(KeyboardPreview.Selection.area(KeyboardPreview.AREA_TOOLBAR));
        idle();
        float[] tbp = new float[2];
        hc.invoke(p, KeyboardPreview.Handle.values()[0], tbp);
        drag(overlay, tbp[0], tbp[1], 0, -120);
        log("toolbar " + tbBefore + " -> " + prefs.toolbarHeight());
        shot(a, "13-number-drags");
    }

    static void click(AlertDialog d, int which) {
        d.getButton(which).performClick();
        idle();
    }

    @Test
    public void flows() throws Exception {
        SettingsActivity a = launch("keyboard");
        Prefs prefs = new Prefs(a);
        KeyboardPreview p = find(a.getWindow().getDecorView(), KeyboardPreview.class);
        p.setLayer(KeyboardPreview.LAYER_KEY);
        idle();
        ScrollView scroll = find(a.getWindow().getDecorView(), ScrollView.class);
        log("OVERVIEW has custom toggle: " + visibleTexts(cur(a)).contains("완전 사용자화"));
        assertFalse(visibleTexts(cur(a)).contains("완전 사용자화"));
        assertFalse(visibleTexts(cur(a)).contains("밀어서 쌍자음"));
        // ㄹ 위쪽: 빠른 선택으로 ㄼ
        tapKey(p, key(p, "ㄹ", Key.CHAR));
        log("PANEL ㄹ:\n" + visibleTexts(cur(a)));
        assertFalse(visibleTexts(cur(a)).contains("완전 사용자화"));
        tapDir(cur(a), "위쪽");
        AlertDialog d = (AlertDialog) ShadowDialog.getLatestDialog();
        log("up dialog: " + shadowOf(d).getTitle());
        android.widget.EditText in = find(d.getWindow().getDecorView(), android.widget.EditText.class);
        log("prefilled: " + in.getText());
        assertEquals("ㄺ", in.getText().toString());
        findText(d.getWindow().getDecorView(), "ㄼ").performClick();
        assertEquals("ㄼ", in.getText().toString());
        shot(a, "40-chips");
        click(d, AlertDialog.BUTTON_POSITIVE);
        assertEquals("ㄼ", key(p, "ㄹ", Key.CHAR).swipeText(Key.SWIPE_UP));
        // 아래: 직접 적기
        tapDir(cur(a), "아래쪽");
        AlertDialog t = (AlertDialog) ShadowDialog.getLatestDialog();
        in = find(t.getWindow().getDecorView(), android.widget.EditText.class);
        in.setText("ㄹㄹ");
        click(t, AlertDialog.BUTTON_POSITIVE);
        assertEquals("ㄹㄹ", key(p, "ㄹ", Key.CHAR).swipeText(Key.SWIPE_DOWN));
        // 길게 누르기 모드
        findText(cur(a), "연속 입력").performClick(); idle();
        assertTrue(prefs.repeatChar("ko", "ㄹ"));
        findText(cur(a), "없음").performClick(); idle();
        findText(cur(a), "문자 입력").performClick(); idle();
        assertNull(prefs.popupOverride("ko", "ㄹ"));
        // 이 키 기본값으로
        cur(a).scrollTo(0, 99999);
        findText(cur(a), "기본값으로 되돌리기").performClick(); idle();
        click((AlertDialog) ShadowDialog.getLatestDialog(), AlertDialog.BUTTON_POSITIVE);
        assertEquals("ㄺ", key(p, "ㄹ", Key.CHAR).swipeText(Key.SWIPE_UP));
        // 지우기 키: 바로 방향 칸
        tapKey(p, key(p, null, Key.DELETE));
        shot(a, "41-delete");
        tapDir(cur(a), "왼쪽");
        d = (AlertDialog) ShadowDialog.getLatestDialog();
        shadowOf(d).clickOnItem(1);   // 실행 취소
        idle();
        assertEquals(com.alternative_studios.newswipe.keyboard.SwipeAction.UNDO,
                com.alternative_studios.newswipe.keyboard.FunctionSwipes.SPEC.get(prefs, com.alternative_studios.newswipe.keyboard.FunctionSwipes.DELETE, Key.SWIPE_LEFT));
        // 길게 누르기 값 줄
        findText(cur(a), "계속 지우기 (누르고 있는 동안)").performClick(); idle();
        d = (AlertDialog) ShadowDialog.getLatestDialog();
        log("lp dialog: " + shadowOf(d).getTitle());
        d.dismiss(); idle();
        // 🌐︎: 위쪽에서만 '언어 고르기'
        tapKey(p, key(p, null, Key.LANGUAGE));
        tapDir(cur(a), "위쪽");
        d = (AlertDialog) ShadowDialog.getLatestDialog();
        boolean pickUp = false;
        for (CharSequence it : shadowOf(d).getItems()) pickUp |= it.toString().startsWith("언어 고르기");
        d.dismiss(); idle();
        tapDir(cur(a), "왼쪽");
        d = (AlertDialog) ShadowDialog.getLatestDialog();
        boolean pickLeft = false;
        for (CharSequence it : shadowOf(d).getItems()) pickLeft |= it.toString().startsWith("언어 고르기");
        d.dismiss(); idle();
        log("language pick up=" + pickUp + " left=" + pickLeft);
        assertTrue(pickUp && !pickLeft);
        shot(a, "42-globe");
        a.onBackPressed(); idle();
        // 설정 검색 목록
        java.lang.reflect.Method hs = SettingsActivity.class.getDeclaredMethod("harvestScreen", String.class, java.util.List.class);
        hs.setAccessible(true);
        java.util.List<String[]> out = new java.util.ArrayList<>();
        hs.invoke(a, "keyboard", out);
        StringBuilder names = new StringBuilder();
        for (String[] e : out) names.append(e[1]).append(" | ");
        log("overview harvested " + out.size() + ": " + names);
        assertTrue(out.size() < 25);   // 목록에는 영역·키 창에 없는 것만
        java.lang.reflect.Method hp = SettingsActivity.class.getDeclaredMethod("harvestEditorParts", java.util.List.class);
        hp.setAccessible(true);
        java.util.List<String[]> parts = new java.util.ArrayList<>();
        hp.invoke(a, parts);
        java.util.Map<String, String> where = new java.util.HashMap<>();
        for (String[] e : parts) where.putIfAbsent(e[1], e[0]);
        log("parts harvested " + parts.size() + ": " + where);
        assertTrue(where.get("자음 연속 탭으로 쌍자음").startsWith("keyboard:a:2:"));
        assertTrue(where.get("두 번 눌러 마침표").startsWith("keyboard:k:"));
        assertTrue(where.get("도구 막대 표시").startsWith("keyboard:a:0:"));
        assertTrue(where.containsKey("기호 자판 대신 숫자 패드 사용"));
        // 검색 결과에서 연 것처럼: 스페이스바 창이 열리고 항목을 찾는다
        java.lang.reflect.Method ot = SettingsActivity.class.getDeclaredMethod("openEditorTarget", String.class, String.class);
        ot.setAccessible(true);
        ot.invoke(a, where.get("두 번 눌러 마침표"), "두 번 눌러 마침표");
        shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(800));
        assertTrue(visibleTexts(cur(a)).contains("두 번 눌러 마침표"));
        a.onBackPressed(); idle();
        ot.invoke(a, where.get("자음 연속 탭으로 쌍자음"), "자음 연속 탭으로 쌍자음");
        shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(800));
        assertTrue(visibleTexts(cur(a)).contains("자음 연속 탭으로 쌍자음"));
        a.onBackPressed(); idle();
        shot(a, "43-overview");
        cur(a).scrollTo(0, 3000);
        shot(a, "43b-overview-end");
    }

    /** 실제 키보드 뷰에 손가락을 대 보고 어떤 동작이 나오는지 기록한다. */
    @Test
    public void keyboardTouches() throws Exception {
        Activity a = Robolectric.buildActivity(Activity.class).setup().get();
        Prefs prefs = new Prefs(a);
        java.util.List<String> calls = new java.util.ArrayList<>();
        KeyboardView kv = new KeyboardView(a);
        kv.setListener((KeyboardView.Listener) java.lang.reflect.Proxy.newProxyInstance(
                KeyboardView.Listener.class.getClassLoader(), new Class<?>[]{KeyboardView.Listener.class},
                (proxy, m, args) -> {
                    StringBuilder b = new StringBuilder(m.getName());
                    if (args != null) for (Object o : args) b.append(' ').append(o instanceof Key ? ((Key) o).label : o);
                    calls.add(b.toString());
                    return m.getReturnType() == boolean.class ? false : null;
                }));
        kv.configure(300, 22, false, 100);
        kv.setFunctionSwipeThreshold(22);
        kv.setFunctionLongPressMs(300);
        kv.setLongPressActions(com.alternative_studios.newswipe.keyboard.LongPressActions.actions(prefs));
        com.alternative_studios.newswipe.keyboard.FunctionSwipes.SPEC.set(prefs,
                com.alternative_studios.newswipe.keyboard.FunctionSwipes.GLOBE, Key.SWIPE_UP,
                com.alternative_studios.newswipe.keyboard.SwipeAction.LANGUAGE_PICK);
        kv.setFunctionSwipes(com.alternative_studios.newswipe.keyboard.FunctionSwipes.load(prefs));
        kv.setKeyboardSwipes(com.alternative_studios.newswipe.keyboard.KeyboardSwipes.load(prefs));
        kv.setLanguagePopup(new String[]{"한국어", "English"}, 0);
        kv.setKeyboardHeight(700);
        kv.setLayout(KeyboardLayout.korean(prefs));
        a.setContentView(kv);
        idle();
        log("kv " + kv.getWidth() + "x" + kv.getHeight());
        android.graphics.RectF del = keyOf(kv, Key.DELETE).rect, space = keyOf(kv, Key.SPACE).rect,
                mode = keyOf(kv, Key.TO_SYMBOLS).rect, globe = keyOf(kv, Key.LANGUAGE).rect;
        // ⌫ 왼쪽으로 밀기 → 단어 지우기
        swipe(kv, del.centerX(), del.centerY(), -200, 0);
        log("delete left: " + calls); assertTrue(calls.toString().contains("onKeyFunction  delete_word") || calls.toString().contains("delete_word"));
        calls.clear();
        // ⌫ 길게 누르기 → 계속 지우기
        hold(kv, del.centerX(), del.centerY(), 900);
        log("delete hold: " + calls); assertTrue(calls.contains("onDeleteRepeat false"));
        // 600ms 동안 기본 속도(초당 20글자)로 지운다: 길게 누르기 300ms 뒤 약 600ms
        long n = calls.stream().filter(c -> c.equals("onDeleteRepeat false")).count();
        log("char repeats in 600ms: " + n);
        calls.clear();
        // 지우는 속도를 초당 5글자로 늦추면 덜 지운다
        kv.setDeleteRepeatRates(5, 5);
        hold(kv, del.centerX(), del.centerY(), 900);
        long slow = calls.stream().filter(c -> c.equals("onDeleteRepeat false")).count();
        log("char repeats at 5/s: " + slow);
        assertTrue(slow < n);
        calls.clear();
        // ⌫ 길게 누르기를 '단어 지우기 (누르고 있는 동안)'로
        com.alternative_studios.newswipe.keyboard.SwipeAction.class.getName();
        com.alternative_studios.newswipe.keyboard.LongPressActions.SPEC.set(prefs,
                com.alternative_studios.newswipe.keyboard.LongPressActions.DELETE,
                com.alternative_studios.newswipe.keyboard.LongPressActions.DIR,
                com.alternative_studios.newswipe.keyboard.SwipeAction.DELETE_WORD_REPEAT);
        kv.setLongPressActions(com.alternative_studios.newswipe.keyboard.LongPressActions.actions(prefs));
        hold(kv, del.centerX(), del.centerY(), 900);
        log("delete word hold: " + calls);
        assertTrue(calls.contains("onDeleteRepeat true"));
        assertFalse(calls.contains("onDeleteRepeat false"));
        calls.clear();
        // 스페이스바 길게 누르기: 처음에는 밀어서 선택
        touch(kv, MotionEvent.ACTION_DOWN, space.centerX(), space.centerY());
        shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(500));
        for (int s2 = 1; s2 <= 6; s2++) touch(kv, MotionEvent.ACTION_MOVE, space.centerX() + s2 * 30, space.centerY());
        touch(kv, MotionEvent.ACTION_UP, space.centerX() + 180, space.centerY());
        idle();
        log("space hold+move: " + calls);
        assertTrue(calls.toString().contains("onSelectStart"));
        calls.clear();
        // 스페이스바 길게 누르기를 다른 기능(음성 입력)으로
        com.alternative_studios.newswipe.keyboard.LongPressActions.SPEC.set(prefs,
                com.alternative_studios.newswipe.keyboard.LongPressActions.SPACE,
                com.alternative_studios.newswipe.keyboard.LongPressActions.DIR,
                com.alternative_studios.newswipe.keyboard.SwipeAction.VOICE);
        kv.setLongPressActions(com.alternative_studios.newswipe.keyboard.LongPressActions.actions(prefs));
        hold(kv, space.centerX(), space.centerY(), 900);
        log("space hold voice: " + calls);
        assertTrue(calls.toString().contains("onKeyFunction  voice"));
        assertFalse(calls.toString().contains("onSelectStart"));
        com.alternative_studios.newswipe.keyboard.LongPressActions.SPEC.reset(prefs,
                com.alternative_studios.newswipe.keyboard.LongPressActions.SPACE,
                com.alternative_studios.newswipe.keyboard.LongPressActions.DIR);
        com.alternative_studios.newswipe.keyboard.LongPressActions.SPEC.reset(prefs,
                com.alternative_studios.newswipe.keyboard.LongPressActions.DELETE,
                com.alternative_studios.newswipe.keyboard.LongPressActions.DIR);
        kv.setLongPressActions(com.alternative_studios.newswipe.keyboard.LongPressActions.actions(prefs));
        calls.clear();
        // 스페이스바 오른쪽으로 밀기 → 커서 이동
        swipe(kv, space.centerX(), space.centerY(), 250, 0);
        log("space right: " + calls); assertTrue(calls.toString().contains("onCursorMove"));
        calls.clear();
        // 기호 키 길게 → 이모지 열기
        hold(kv, mode.centerX(), mode.centerY(), 900);
        log("mode hold: " + calls); assertTrue(calls.toString().contains("emoji_open"));
        calls.clear();
        // 🌐︎ 위로 밀기 → 언어 말풍선에서 고르기
        swipe(kv, globe.centerX(), globe.centerY(), 0, -250);
        log("globe up: " + calls); assertTrue(calls.toString().contains("onPopupChar"));
        calls.clear();
        // ㄱ 아래로 밀기 → ㄲ
        android.graphics.RectF g = keyOfLabel(kv, "ㄱ").rect;
        swipe(kv, g.centerX(), g.centerY(), 0, 200);
        log("ㄱ down: " + calls); assertTrue(calls.toString().contains("onKeySwipe ㄱ ㄲ"));
    }

    static Key keyOf(KeyboardView kv, int type) {
        for (KeyboardLayout.Row r : kv.getLayout().rows) for (Key k : r.keys) if (k.type == type) return k;
        throw new AssertionError(type);
    }

    static Key keyOfLabel(KeyboardView kv, String label) {
        for (KeyboardLayout.Row r : kv.getLayout().rows) for (Key k : r.keys) if (label.equals(k.label)) return k;
        throw new AssertionError(label);
    }

    static void swipe(View v, float x, float y, float dx, float dy) {
        t0 = SystemClock.uptimeMillis();
        touch(v, MotionEvent.ACTION_DOWN, x, y);
        for (int s = 1; s <= 8; s++) {
            shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(10));
            touch(v, MotionEvent.ACTION_MOVE, x + dx * s / 8f, y + dy * s / 8f);
        }
        touch(v, MotionEvent.ACTION_UP, x + dx, y + dy);
        idle();
    }

    static void hold(View v, float x, float y, long ms) {
        t0 = SystemClock.uptimeMillis();
        touch(v, MotionEvent.ACTION_DOWN, x, y);
        shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(ms));
        touch(v, MotionEvent.ACTION_UP, x, y);
        idle();
    }

    @Test
    public void dark() throws Exception {
        new Prefs(RuntimeEnvironment.getApplication()).raw().edit().putString(Prefs.THEME_MODE, Prefs.THEME_DARK).commit();
        SettingsActivity a = launch("keyboard");
        KeyboardPreview p = find(a.getWindow().getDecorView(), KeyboardPreview.class);
        p.setLayer(KeyboardPreview.LAYER_KEY);
        idle();
        tapKey(p, key(p, "ㅗ", Key.CHAR));
        shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(90));
        View dv = a.getWindow().getDecorView();
        Bitmap mid = Bitmap.createBitmap(dv.getWidth(), dv.getHeight(), Bitmap.Config.ARGB_8888);
        dv.draw(new Canvas(mid));
        save(mid, "31-dark-mid");
        ScrollView scroll = find(a.getWindow().getDecorView(), ScrollView.class);
        cur(a).scrollTo(0, 900);
        shot(a, "30-dark-vowel");
    }

    @Test
    public void toolbarAndPopup() throws Exception {
        Prefs pre = new Prefs(RuntimeEnvironment.getApplication());
        pre.raw().edit().putBoolean(Prefs.TOOLBAR_BOTTOM, true).putInt(Prefs.PAD_BOTTOM, 12).commit();
        SettingsActivity a = launch("keyboard");
        Prefs prefs = new Prefs(a);
        float dens = a.getResources().getDisplayMetrics().density;
        KeyboardPreview p = find(a.getWindow().getDecorView(), KeyboardPreview.class);
        p.setLayer(KeyboardPreview.LAYER_KEY);
        idle();
        ScrollView scroll = find(a.getWindow().getDecorView(), ScrollView.class);
        ViewGroup column = (ViewGroup) p.getChildAt(0);
        KeyboardView kv = find(p, KeyboardView.class);
        View bar = column.getChildAt(column.getChildCount() - 1);
        log("bottom bar: keys " + kv.getTop() + "-" + kv.getBottom() + " bar " + bar.getTop() + "-" + bar.getBottom() + " preview h " + p.getHeight());
        assertEquals(kv.getBottom(), bar.getTop());
        assertEquals(Math.round(12 * dens), p.getHeight() - bar.getBottom());
        shot(a, "50-toolbar-bottom");
        // 아래쪽 여백 손잡이: 도구 막대 밑 가장자리에서 위로 끌기
        p.setLayer(KeyboardPreview.LAYER_BOARD);   // 여백 손잡이는 키보드 페이지에서 키보드를 골랐을 때
        shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(400));
        p.select(KeyboardPreview.Selection.board());
        idle();
        drag(p.getChildAt(1), p.getWidth() / 2f, bar.getBottom(), 0, -90);
        log("padBottom 12 -> " + prefs.padBottomDp());
        assertTrue(prefs.padBottomDp() > 12);
        assertEquals(Math.round(prefs.padBottomDp() * dens), p.getHeight() - column.getChildAt(column.getChildCount() - 1).getBottom());
        p.setLayer(KeyboardPreview.LAYER_KEY);
        shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(400));

        // 길게 누르기: 문자를 정하고 '없음' → '문자 입력'으로 돌아오면 되살아난다
        prefs.raw().edit().putBoolean(Prefs.TOOLBAR_BOTTOM, false).commit();
        idle();
        tapKey(p, key(p, "ㄱ", Key.CHAR));
        prefs.setPopupOverride("ko", "ㄱ", new String[]{"x", "y"});
        findText(cur(a), "없음").performClick(); idle();
        assertEquals(0, prefs.popupOverride("ko", "ㄱ").length);
        findText(cur(a), "문자 입력").performClick(); idle();
        log("restored: " + java.util.Arrays.toString(prefs.popupOverride("ko", "ㄱ")));
        assertArrayEquals(new String[]{"x", "y"}, prefs.popupOverride("ko", "ㄱ"));
        // 기본 문자였으면 기본으로
        findText(cur(a), "없음").performClick(); idle();
        prefs.resetPopupOverride("ko", "ㄱ");
        a.onBackPressed(); idle();

        // 도구 막대 끄기 + 단어 추천 켜기: 버튼 없는 줄이 남는다
        assertFalse(visibleTexts(cur(a)).contains("도구 막대 표시"));
        prefs.raw().edit().putBoolean(Prefs.TOOLBAR, false).putBoolean(Prefs.SUGGEST_WORDS, true).commit();
        idle();
        View strip = column.getChildAt(0);
        log("strip: " + strip.getClass().getSimpleName() + " h=" + strip.getHeight() + " children=" + ((ViewGroup) strip).getChildCount());
        assertEquals(0, ((ViewGroup) strip).getChildCount());
        assertEquals(Math.round(40 * dens * prefs.toolbarHeight() / 100f), strip.getHeight());
        shot(a, "51-strip-no-buttons");
        // 단어 추천·단축어도 끄면 알림 띠, 눌러서 도구 막대 설정
        prefs.raw().edit().putBoolean(Prefs.SUGGEST_WORDS, false).putBoolean(Prefs.SHORTCUTS_ENABLED, false).commit();
        idle();
        View ph = column.getChildAt(0);
        log("placeholder: " + ph.getClass().getSimpleName() + " " + (ph instanceof TextView ? ((TextView) ph).getText() : ""));
        assertTrue(ph instanceof TextView);
        tap(p.getChildAt(1), p.getWidth() / 2f, ph.getHeight() / 2f);
        assertNull(p.selection());   // 키 페이지에서는 고르지 않는다
        p.setLayer(KeyboardPreview.LAYER_AREA);
        shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(400));
        tap(p.getChildAt(1), p.getWidth() / 2f, ph.getHeight() / 2f);
        log("PANEL toolbar off:\n" + visibleTexts(cur(a)));
        assertTrue(visibleTexts(cur(a)).contains("도구 막대 표시"));
        assertTrue(visibleTexts(cur(a)).contains("도구 버튼의 아이콘은 막대보다 커지지 않습니다."));
        shot(a, "52-placeholder-panel");
        // 다시 켜기: 패널은 그대로, 버튼이 돌아온다
        View row = (View) findText(cur(a), "도구 막대 표시").getParent().getParent();
        row.performClick(); idle();
        assertTrue(prefs.toolbar());
        assertNotNull(p.selection());
        log("bar children after on: " + ((ViewGroup) column.getChildAt(0)).getChildCount());
        assertTrue(((ViewGroup) column.getChildAt(0)).getChildCount() > 0);
    }

    static float[] handle(KeyboardPreview p, int h) throws Exception {
        java.lang.reflect.Method m = KeyboardPreview.class.getDeclaredMethod("handleCenter", KeyboardPreview.Handle.class, float[].class);
        m.setAccessible(true);
        float[] pt = new float[2];
        assertTrue("handle " + h, (Boolean) m.invoke(p, KeyboardPreview.Handle.values()[h], pt));
        return pt;
    }

    static View byDesc(View v, String desc) {
        if (desc.contentEquals(String.valueOf(v.getContentDescription())) && v.isShown()) return v;
        if (v instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) {
                View f = byDesc(g.getChildAt(i), desc);
                if (f != null) return f;
            }
        }
        return null;
    }

    static boolean open(SettingsActivity a) throws Exception {
        java.lang.reflect.Method m = SettingsActivity.class.getDeclaredMethod("panelOpen");
        m.setAccessible(true);
        return (Boolean) m.invoke(a);
    }

    @Test
    public void appliesImmediately() throws Exception {
        SettingsActivity a = launch("keyboard");
        Prefs prefs = new Prefs(a);
        View decor = a.getWindow().getDecorView();
        KeyboardPreview p = find(decor, KeyboardPreview.class);
        p.setLayer(KeyboardPreview.LAYER_KEY);
        idle();
        tapKey(p, key(p, "ㄱ", Key.CHAR));
        assertNotNull(findText(decor, "'ㄱ' 키"));
        assertFalse(visibleTexts(cur(a)).contains("쌍자음"));
        tapKey(p, key(p, null, Key.SPACE));
        String t = visibleTexts(cur(a));
        log("SPACE:\n" + t);
        assertTrue(t.contains("두 번 눌러 마침표") && t.contains("언어 이름 보이기") && t.contains("인식 범위 좁히기")
                && t.contains("커서 이동 속도 좌우"));
        assertFalse(t.contains("순서·유무"));
        shot(a, "87-space-key");
        assertTrue(t.contains("길게 누르면") && t.contains("밀어서 선택"));
        View lpRow = findText(cur(a), "길게 누르면");
        while (lpRow != null && !lpRow.isClickable()) lpRow = (View) lpRow.getParent();
        lpRow.performClick(); idle();
        AlertDialog sd = (AlertDialog) ShadowDialog.getLatestDialog();
        sd.dismiss(); idle();
        cur(a).scrollTo(0, 99999); idle();
        shot(a, "87c-space-longpress");
        tapKey(p, key(p, null, Key.DELETE));
        t = visibleTexts(cur(a));
        log("DELETE:\n" + t);
        assertTrue(t.contains("지우는 속도") && t.contains("글자/초"));
        com.alternative_studios.newswipe.keyboard.LongPressActions.SPEC.set(prefs,
                com.alternative_studios.newswipe.keyboard.LongPressActions.DELETE,
                com.alternative_studios.newswipe.keyboard.LongPressActions.DIR,
                com.alternative_studios.newswipe.keyboard.SwipeAction.DELETE_WORD_REPEAT);
        tapKey(p, key(p, null, Key.SPACE)); tapKey(p, key(p, null, Key.DELETE));
        t = visibleTexts(cur(a));
        assertTrue(t.contains("단어 지우기 (누르고 있는 동안)") && t.contains("단어/초"));
        shot(a, "89-delete-word");
        com.alternative_studios.newswipe.keyboard.LongPressActions.SPEC.set(prefs,
                com.alternative_studios.newswipe.keyboard.LongPressActions.DELETE,
                com.alternative_studios.newswipe.keyboard.LongPressActions.DIR,
                com.alternative_studios.newswipe.keyboard.SwipeAction.EMOJI_OPEN);
        tapKey(p, key(p, null, Key.SPACE)); tapKey(p, key(p, null, Key.DELETE));
        assertFalse(visibleTexts(cur(a)).contains("지우는 속도"));
        com.alternative_studios.newswipe.keyboard.LongPressActions.SPEC.reset(prefs,
                com.alternative_studios.newswipe.keyboard.LongPressActions.DELETE,
                com.alternative_studios.newswipe.keyboard.LongPressActions.DIR);
        prefs.raw().edit().putBoolean(Prefs.NUMBER_ROW, true).commit();
        p.setTab(KeyboardPreview.TAB_KOREAN); idle();
        tapKey(p, key(p, "1", Key.CHAR));
        t = visibleTexts(cur(a));
        assertFalse(t.contains("숫자 줄 높이") || t.contains("숫자 줄 표시"));
        shot(a, "88-number-key");
        prefs.raw().edit().putBoolean(Prefs.NUMBER_ROW, false).commit();
        p.setTab(KeyboardPreview.TAB_KOREAN); idle();
        tapKey(p, key(p, "ㄱ", Key.CHAR));
        assertNull(byDesc(decor, "저장"));
        shot(a, "70-sheet-top");
        findText(cur(a), "연속 입력").performClick(); idle();
        assertTrue(prefs.repeatChar("ko", "ㄱ"));   // 바로 저장
        findText(cur(a), "문자 입력").performClick(); idle();
        cur(a).scrollTo(0, 1100);
        shot(a, "71-sheet-swipe");
        tapKey(p, key(p, "ㄴ", Key.CHAR));   // 묻지 않고 바로 바뀐다
        assertNotNull(findText(decor, "'ㄴ' 키"));
        findText(cur(a), "없음").performClick(); idle();
        byDesc(decor, "편집 닫기").performClick(); idle();
        assertFalse(open(a));
        assertEquals(0, prefs.popupOverride("ko", "ㄴ").length);   // X로 닫아도 남는다
        // 기능키와 도구 막대
        tapKey(p, key(p, null, Key.SPACE));
        shot(a, "72-sheet-space");
        ViewGroup column = (ViewGroup) p.getChildAt(0);
        View bar = column.getChildAt(0);
        tap(p.getChildAt(1), p.getWidth() / 2f, bar.getHeight() / 2f);   // 키 페이지: 도구 막대 빈 곳은 고르지 않는다
        assertNull(p.selection());
        shot(a, "73-sheet-toolbar");
        shot(a, "74-overview");
    }

    static Bitmap grab(Activity a) {
        View dv = a.getWindow().getDecorView();
        Bitmap b = Bitmap.createBitmap(dv.getWidth(), dv.getHeight(), Bitmap.Config.ARGB_8888);
        dv.draw(new Canvas(b));
        return b;
    }

    static void tapArea(KeyboardPreview p, int area) {
        android.graphics.RectF r = new android.graphics.RectF();
        assertTrue("area " + area, p.areaBounds(area, r));
        tap(p.getChildAt(1), r.centerX() + r.width() / 5f, r.centerY());
    }

    @Test
    public void areasPagesUndoAndMotion() throws Exception {
        new Prefs(RuntimeEnvironment.getApplication()).raw().edit().putBoolean(Prefs.NUMBER_ROW, true).commit();
        SettingsActivity a = launch("keyboard");
        Prefs prefs = new Prefs(a);
        View decor = a.getWindow().getDecorView();
        KeyboardPreview p = find(decor, KeyboardPreview.class);
        assertEquals(KeyboardPreview.LAYER_AREA, p.layer());
        shot(a, "80-area-layer");
        // 영역: 글자 키
        tapArea(p, KeyboardPreview.AREA_LETTERS);
        assertNotNull(findText(decor, "글자 키"));
        String t = visibleTexts(cur(a));
        log("LETTERS:\n" + t);
        assertTrue(t.contains("한글 자판 배열") && t.contains("자음 연속 탭으로 쌍자음") && t.contains("영어 자동 대문자")
                && t.contains("키보드 밀기 편집"));
        assertFalse(t.contains("스페이스바 두 번으로 마침표"));
        String lt = visibleTexts(cur(a));
        assertTrue(lt.contains("글자 키 높이"));
        assertTrue(lt.indexOf("한글 자판 배열") < lt.indexOf("모양") && lt.indexOf("모양") < lt.indexOf("입력"));
        shot(a, "81-area-letters");
        // 열 때 움직임: 끝나면 정확히 제자리 (끝에서 남은 거리를 한 번에 맞추지 않는다)
        View sheet = sheetOf(a).view();
        log("sheet translationY after settle = " + sheet.getTranslationY());
        assertEquals(0f, sheet.getTranslationY(), 0.01f);
        // 기능키
        tapArea(p, KeyboardPreview.AREA_FUNCTION);
        t = visibleTexts(cur(a));
        log("FN:\n" + t);
        assertTrue(t.contains("기능키 순서·유무 사용자화") && t.contains("기능키 높이") && t.contains("격자 정렬")
                && t.contains("기능 미는 거리"));
        assertFalse(t.contains("마침표") || t.contains("언어 이름") || t.contains("숫자 패드") || t.contains("언어 순서")
                || t.contains("커서"));
        shot(a, "82-area-fn");
        // 기능키 높이 손잡이: 기능키 윤곽선 위 가장자리
        java.lang.reflect.Method hc = KeyboardPreview.class.getDeclaredMethod("handleCenter", KeyboardPreview.Handle.class, float[].class);
        hc.setAccessible(true);
        java.lang.reflect.Method shownM = KeyboardPreview.class.getDeclaredMethod("handleShown", KeyboardPreview.Handle.class);
        shownM.setAccessible(true);
        assertTrue((Boolean) shownM.invoke(p, KeyboardPreview.Handle.values()[7]));
        assertFalse((Boolean) shownM.invoke(p, KeyboardPreview.Handle.values()[2]));
        android.graphics.RectF fr = new android.graphics.RectF();
        p.areaBounds(KeyboardPreview.AREA_FUNCTION, fr);
        float[] fpt = new float[2];
        hc.invoke(p, KeyboardPreview.Handle.values()[7], fpt);
        log("fn handle y=" + fpt[1] + " outline top=" + fr.top);
        assertEquals(fr.centerY(), fpt[1], 1f);   // 기능키 줄 가운데의 ↕ 버튼
        int fn0 = prefs.fnRowHeight();
        java.lang.reflect.Method ha = KeyboardPreview.class.getDeclaredMethod("handleAt", float.class, float.class);
        ha.setAccessible(true);
        log("handleAt=" + ha.invoke(p, p.getWidth() / 2f, fpt[1]) + " overlay=" + p.getChildAt(1).getClass().getSimpleName()
                + " sel=" + p.getClass());
        drag(p.getChildAt(1), fpt[0], fpt[1], 0, -120);
        log("fnRow " + fn0 + " -> " + prefs.fnRowHeight());
        assertTrue(prefs.fnRowHeight() > fn0);
        shot(a, "82b-fn-taller");
        byDesc(decor, "마지막 변경 되돌리기").performClick(); idle();
        log("fnRow after undo " + prefs.fnRowHeight());
        assertEquals(fn0, prefs.fnRowHeight());
        // 숫자 줄
        tapArea(p, KeyboardPreview.AREA_NUMBER);
        t = visibleTexts(cur(a));
        assertTrue(t.contains("숫자 줄 높이") && t.contains("숫자 키 힌트 없애기"));
        shot(a, "83-area-number");
        // 도구 막대: 도구 막대 칸 + 클립보드·이모지
        tapArea(p, KeyboardPreview.AREA_TOOLBAR);
        t = visibleTexts(cur(a));
        assertTrue(t.contains("도구 막대 표시"));
        assertFalse(t.contains("클립보드 기록 저장") || t.contains("이모지 크기"));
        cur(a).scrollTo(0, 99999);
        shot(a, "84-area-toolbar");
        // 되돌리기: 영역 창에서 바꾼 값
        boolean before = prefs.raw().getBoolean(Prefs.TOOLBAR_BOTTOM, false);
        View emojiRow = (View) findText(cur(a), "자판 아래에 두기").getParent().getParent();
        emojiRow.performClick(); idle();
        assertEquals(!before, prefs.raw().getBoolean(Prefs.TOOLBAR_BOTTOM, false));
        View undo = byDesc(decor, "마지막 변경 되돌리기");
        assertNotNull(undo);
        assertTrue(undo.isEnabled());
        undo.performClick(); idle();
        log("toolbar bottom " + before + " -> undo -> " + prefs.raw().getBoolean(Prefs.TOOLBAR_BOTTOM, false));
        assertEquals(before, prefs.raw().getBoolean(Prefs.TOOLBAR_BOTTOM, false));
        assertFalse(byDesc(decor, "마지막 변경 되돌리기").isEnabled());
        // 손잡이로 높이를 끌면 한 번의 되돌리기로 끈 처음 값으로
        a.onBackPressed(); idle();
        int h0 = prefs.keyboardHeight();
        p.select(KeyboardPreview.Selection.area(KeyboardPreview.AREA_LETTERS));
        idle();
        KeyboardView kv = find(p, KeyboardView.class);
        android.graphics.RectF lr = new android.graphics.RectF();
        p.areaBounds(KeyboardPreview.AREA_LETTERS, lr);
        float[] lk = handle(p, 2);
        drag(p.getChildAt(1), lk[0], lk[1], 0, -200);
        log("height " + h0 + " -> " + prefs.keyboardHeight());
        assertTrue(prefs.keyboardHeight() > h0);
        byDesc(decor, "마지막 변경 되돌리기").performClick(); idle();
        assertEquals(h0, prefs.keyboardHeight());
        // 좌우로 밀어 페이지 넘기기
        swipe(p.getChildAt(1), p.getWidth() * 0.8f, p.getHeight() / 2f, -p.getWidth() * 0.5f, 0);
        shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(400));
        log("layer after swipe left: " + p.layer());
        assertEquals(KeyboardPreview.LAYER_KEY, p.layer());
        shot(a, "85-key-layer");
        // 키 페이지에서 키를 누르면 키 편집
        tapKey(p, key(p, "ㄱ", Key.CHAR));
        assertNotNull(findText(decor, "'ㄱ' 키"));
        // 넘기면 창이 닫힌다
        swipe(p.getChildAt(1), p.getWidth() * 0.2f, p.getHeight() / 2f, p.getWidth() * 0.5f, 0);
        shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(400));
        assertEquals(KeyboardPreview.LAYER_AREA, p.layer());
        assertFalse(open(a));
        // 조금만 밀면 제자리
        swipe(p.getChildAt(1), p.getWidth() * 0.5f, p.getHeight() / 2f, -60, 0);
        shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(400));
        assertEquals(KeyboardPreview.LAYER_AREA, p.layer());
        // 영역 페이지에서 영역을 누르면: 설정 창(맨 아래 '여백'), 가장자리의 여백 손잡이, 가운데의 ↕ 버튼
        java.lang.reflect.Method shown = KeyboardPreview.class.getDeclaredMethod("handleShown", KeyboardPreview.Handle.class);
        shown.setAccessible(true);
        tapArea(p, KeyboardPreview.AREA_LETTERS);
        assertTrue(open(a));
        cur(a).scrollTo(0, 99999); idle();
        String pt = visibleTexts(cur(a));
        log("LETTERS SHEET END:\n" + pt);
        assertTrue(pt.contains("여백") && pt.contains("왼쪽 여백") && pt.contains("아래쪽 여백"));
        assertTrue((Boolean) shown.invoke(p, KeyboardPreview.Handle.values()[8]) && (Boolean) shown.invoke(p, KeyboardPreview.Handle.values()[9]) && (Boolean) shown.invoke(p, KeyboardPreview.Handle.values()[10])
                && (Boolean) shown.invoke(p, KeyboardPreview.Handle.values()[11]) && (Boolean) shown.invoke(p, KeyboardPreview.Handle.values()[2]));
        assertFalse((Boolean) shown.invoke(p, KeyboardPreview.Handle.values()[3]));
        // ↕ 버튼: 영역 가운데. 누르면 안내, 위로 끌면 키보드 높이가 커진다
        java.lang.reflect.Method hcm0 = KeyboardPreview.class.getDeclaredMethod("handleCenter", KeyboardPreview.Handle.class, float[].class);
        hcm0.setAccessible(true);
        float[] kp = new float[2];
        hcm0.invoke(p, KeyboardPreview.Handle.values()[2], kp);
        android.graphics.RectF lr0 = new android.graphics.RectF();
        p.areaBounds(KeyboardPreview.AREA_LETTERS, lr0);
        assertEquals(lr0.centerX(), kp[0], 1f);   // 글자 키 영역은 정중앙 (위·아래 여백 손잡이는 가장자리라 겹치지 않는다)
        assertEquals(lr0.centerY(), kp[1], 1f);
        float[] topH = handle(p, 9);
        assertEquals(lr0.centerX(), topH[0], 1f);
        java.lang.reflect.Field kh = KeyboardPreview.class.getDeclaredField("knobHint");
        kh.setAccessible(true);
        assertNull(kh.get(p));
        // 누르고 있는 동안 안내가 보이고, 떼면 잠깐 뒤 사라진다
        touch(p.getChildAt(1), MotionEvent.ACTION_DOWN, kp[0], kp[1]);
        assertEquals(KeyboardPreview.Handle.HEIGHT, kh.get(p));
        assertTrue(open(a));   // 버튼을 눌러도 창은 그대로
        shot(a, "85h-knob-hint");
        touch(p.getChildAt(1), MotionEvent.ACTION_UP, kp[0], kp[1]);
        idle();
        java.lang.reflect.Field kt = KeyboardPreview.class.getDeclaredField("knobHintUntil");
        kt.setAccessible(true);
        assertTrue((Long) kt.get(p) < Long.MAX_VALUE);
        int kbh0 = prefs.keyboardHeight();
        drag(p.getChildAt(1), kp[0], kp[1], 0, -150);
        log("knob height " + kbh0 + " -> " + prefs.keyboardHeight());
        assertEquals(0, prefs.keyboardHeight() % 5);   // 높이는 5% 단위로 움직인다
        assertTrue(prefs.keyboardHeight() > kbh0);
        prefs.raw().edit().putInt(Prefs.KEYBOARD_HEIGHT, kbh0).commit();
        idle();
        java.lang.reflect.Method hcm = KeyboardPreview.class.getDeclaredMethod("handleCenter", KeyboardPreview.Handle.class, float[].class);
        hcm.setAccessible(true);
        float[] lp = new float[2];
        hcm.invoke(p, KeyboardPreview.Handle.values()[8], lp);
        drag(p.getChildAt(1), lp[0], lp[1], 150, 0);
        int lettersLeft = prefs.areaPadDp(KeyboardPreview.AREA_LETTERS, Prefs.SIDE_LEFT);
        log("letters left pad 0 -> " + lettersLeft);
        assertTrue(lettersLeft > 0);
        assertTrue(visibleTexts(cur(a)).contains(lettersLeft + "dp"));   // 슬라이더도 같이 움직였다
        float[] bp = new float[2];
        hcm.invoke(p, KeyboardPreview.Handle.values()[11], bp);
        drag(p.getChildAt(1), bp[0], bp[1], 0, -150);
        int lettersBottom = prefs.areaPadDp(KeyboardPreview.AREA_LETTERS, Prefs.SIDE_BOTTOM);
        log("letters bottom pad 0 -> " + lettersBottom);
        assertTrue(lettersBottom > 0);
        Key q = key(p, "ㅂ", Key.CHAR);
        Key sym = key(p, null, Key.TO_SYMBOLS);
        assertTrue(q.rect.left > sym.rect.left + 10);
        shot(a, "85e-pad-letters");
        // 도구 막대도 네 방향 모두
        tapArea(p, KeyboardPreview.AREA_TOOLBAR);
        assertTrue((Boolean) shown.invoke(p, KeyboardPreview.Handle.values()[8]) && (Boolean) shown.invoke(p, KeyboardPreview.Handle.values()[10])
                && (Boolean) shown.invoke(p, KeyboardPreview.Handle.values()[9]) && (Boolean) shown.invoke(p, KeyboardPreview.Handle.values()[11]) && (Boolean) shown.invoke(p, KeyboardPreview.Handle.values()[0]));
        cur(a).scrollTo(0, 99999); idle();
        assertTrue(visibleTexts(cur(a)).contains("위쪽 여백"));
        View barV = ((ViewGroup) p.getChildAt(0)).getChildAt(0);
        int barTop0 = barV.getTop(), keysTop0 = find(p, KeyboardView.class).getTop();
        float[] tb = new float[2];
        hcm.invoke(p, KeyboardPreview.Handle.values()[11], tb);
        drag(p.getChildAt(1), tb[0], tb[1], 0, -150);
        int barBottomPad = prefs.areaPadDp(KeyboardPreview.AREA_TOOLBAR, Prefs.SIDE_BOTTOM);
        int keysTop1 = find(p, KeyboardView.class).getTop();
        log("toolbar bottom pad 0 -> " + barBottomPad + " gap bar->keys " + (keysTop0 - barV.getBottom()) + " -> "
                + (keysTop1 - barV.getBottom()));
        assertTrue(barBottomPad > 0);
        assertEquals(Math.round(barBottomPad * a.getResources().getDisplayMetrics().density), keysTop1 - barV.getBottom());
        shot(a, "85g-pad-toolbar");
        prefs.raw().edit().remove(Prefs.areaPadKey(KeyboardPreview.AREA_TOOLBAR, Prefs.SIDE_BOTTOM)).commit();
        idle();
        a.onBackPressed(); idle();
        shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(400));
        prefs.raw().edit().remove(Prefs.areaPadKey(KeyboardPreview.AREA_LETTERS, Prefs.SIDE_LEFT))
                .remove(Prefs.areaPadKey(KeyboardPreview.AREA_LETTERS, Prefs.SIDE_BOTTOM)).commit();
        idle();
        // 오른쪽으로 밀면 키보드 페이지 (더 밀어도 그대로)
        swipe(p.getChildAt(1), p.getWidth() * 0.2f, p.getHeight() / 2f, p.getWidth() * 0.5f, 0);
        shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(400));
        assertEquals(KeyboardPreview.LAYER_BOARD, p.layer());
        assertFalse((Boolean) shown.invoke(p, KeyboardPreview.Handle.values()[3]));   // 누르기 전에는 손잡이 없음
        shot(a, "85b-board-layer");
        android.graphics.RectF br = new android.graphics.RectF();
        p.boardBounds(br);
        tap(p.getChildAt(1), br.centerX(), br.centerY());
        assertTrue(open(a));
        assertTrue((Boolean) shown.invoke(p, KeyboardPreview.Handle.values()[3]) && (Boolean) shown.invoke(p, KeyboardPreview.Handle.values()[6]));   // 눌러서 고르면 키보드 전체 여백 손잡이
        pt = visibleTexts(cur(a));
        assertTrue(pt.contains("키보드 여백") && pt.contains("위쪽 여백"));
        shot(a, "85f-board-sheet");
        a.onBackPressed(); idle();
        shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(400));
        swipe(p.getChildAt(1), p.getWidth() * 0.2f, p.getHeight() / 2f, p.getWidth() * 0.5f, 0);
        shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(400));
        assertEquals(KeyboardPreview.LAYER_BOARD, p.layer());
        swipe(p.getChildAt(1), p.getWidth() * 0.8f, p.getHeight() / 2f, -p.getWidth() * 0.5f, 0);
        shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(400));
        assertEquals(KeyboardPreview.LAYER_AREA, p.layer());
        // 숫자 줄을 꺼도 창은 그대로이고, 빈 칸이 남아 다시 켤 수 있다
        tapArea(p, KeyboardPreview.AREA_NUMBER);
        View nrRow = findText(cur(a), "숫자 줄 표시");
        while (!nrRow.isClickable()) nrRow = (View) nrRow.getParent();
        nrRow.performClick(); idle();
        shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(300));
        assertFalse(prefs.numberRow());
        assertTrue(open(a));
        assertNotNull(p.selection());
        shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(400));
        assertFalse(visibleTexts(cur(a)).contains("숫자 줄 높이"));   // 끄면 나머지 항목은 감춘다
        shot(a, "85c-number-off");
        a.onBackPressed(); idle();
        shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(400));
        // 목록의 '숫자 줄 설정' 버튼으로 다시 연다
        View nrButton = findText(cur(a), "숫자 줄 설정");
        while (!nrButton.isClickable()) nrButton = (View) nrButton.getParent();
        nrButton.performClick(); idle();
        shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(400));
        assertTrue(open(a));
        shot(a, "85d-number-sheet-from-list");
        nrRow = findText(cur(a), "숫자 줄 표시");
        while (!nrRow.isClickable()) nrRow = (View) nrRow.getParent();
        nrRow.performClick(); idle();
        assertTrue(prefs.numberRow());
        shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(400));
        assertTrue(visibleTexts(cur(a)).contains("숫자 줄 높이"));
        a.onBackPressed(); idle();
        shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(400));
        // 넘기는 중간 모습
        t0 = SystemClock.uptimeMillis();
        touch(p.getChildAt(1), MotionEvent.ACTION_DOWN, p.getWidth() * 0.8f, p.getHeight() / 2f);
        for (int i = 1; i <= 6; i++) touch(p.getChildAt(1), MotionEvent.ACTION_MOVE, p.getWidth() * 0.8f - i * 60, p.getHeight() / 2f);
        Bitmap mid = grab(a);
        save(mid, "86-paging-mid");
        touch(p.getChildAt(1), MotionEvent.ACTION_UP, p.getWidth() * 0.8f - 360, p.getHeight() / 2f);
        idle();
    }

    @Test
    public void clipEmojiMenuAndIcons() throws Exception {
        SettingsActivity m = launch("clipemoji");
        String t = visibleTexts(cur(m));
        log("CLIPEMOJI:\n" + t);
        assertTrue(t.contains("클립보드 기록 저장") && t.contains("이모지 크기") && t.contains("이모지 추천"));
        shot(m, "90-clipemoji");
        new Prefs(RuntimeEnvironment.getApplication()).raw().edit().putBoolean(Prefs.ONBOARDING_DONE, true).commit();
        SettingsActivity main = launch(null);
        assertNotNull(findText(main.getWindow().getDecorView(), "클립보드 및 이모지"));
        shot(main, "91-main");
        Prefs prefs = new Prefs(RuntimeEnvironment.getApplication());
        String[][] cases = {{"100", "100"}, {"130", "100"}, {"100", "130"}, {"100", "80"}};
        for (String[] cs : cases) {
            prefs.raw().edit().putInt(Prefs.FN_ROW_HEIGHT, Integer.parseInt(cs[0]))
                    .putInt(Prefs.KEY_TEXT_SIZE, Integer.parseInt(cs[1])).commit();
            SettingsActivity a = launch("keyboard");
            KeyboardPreview p = find(a.getWindow().getDecorView(), KeyboardPreview.class);
            KeyboardView kv = find(p, KeyboardView.class);
            Bitmap b = Bitmap.createBitmap(kv.getWidth(), kv.getHeight(), Bitmap.Config.ARGB_8888);
            kv.draw(new Canvas(b));
            int y0 = (int) (kv.getHeight() * 0.62f);
            Bitmap crop = Bitmap.createBitmap(b, 0, y0, b.getWidth(), b.getHeight() - y0);
            save(crop, "92-icons-fn" + cs[0] + "-text" + cs[1]);
        }
        prefs.raw().edit().remove(Prefs.FN_ROW_HEIGHT).remove(Prefs.KEY_TEXT_SIZE).commit();
    }

    @Test
    public void paddingOutlinesFollow() throws Exception {
        Prefs pre = new Prefs(RuntimeEnvironment.getApplication());
        pre.raw().edit().putBoolean(Prefs.NUMBER_ROW, true).commit();
        SettingsActivity a = launch("keyboard");
        Prefs prefs = new Prefs(a);
        KeyboardPreview p = find(a.getWindow().getDecorView(), KeyboardPreview.class);
        java.lang.reflect.Method hc = KeyboardPreview.class.getDeclaredMethod("handleCenter", KeyboardPreview.Handle.class, float[].class);
        hc.setAccessible(true);
        float in = 9 * a.getResources().getDisplayMetrics().density;
        float[] pt = new float[2];
        android.graphics.RectF r = new android.graphics.RectF();
        for (int[] pads : new int[][]{{0, 4, 3}, {20, 40, 16}}) {
            prefs.raw().edit().putInt(Prefs.PAD_TOP, pads[0]).putInt(Prefs.PAD_BOTTOM, pads[1])
                    .putInt(Prefs.PAD_LEFT, pads[2]).putInt(Prefs.PAD_RIGHT, pads[2]).commit();
            idle();
            p.setLayer(KeyboardPreview.LAYER_BOARD);
            shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(400));
            p.select(KeyboardPreview.Selection.board());
            idle();
            p.boardBounds(r);
            hc.invoke(p, KeyboardPreview.Handle.values()[6], pt); assertEquals(r.top + in, pt[1], 1f);
            hc.invoke(p, KeyboardPreview.Handle.values()[5], pt); assertEquals(r.bottom - in, pt[1], 1f);
            hc.invoke(p, KeyboardPreview.Handle.values()[3], pt); assertEquals(r.left + in, pt[0], 1f);
            hc.invoke(p, KeyboardPreview.Handle.values()[4], pt); assertEquals(r.right - in, pt[0], 1f);
            // 좌우는 키를 따라간다
            Key q = key(p, "ㅂ", Key.CHAR);
            log("board left " + r.left + " key left " + q.rect.left + " padL " + pads[2]);
            assertTrue(Math.abs(r.left - q.rect.left) < 12 * a.getResources().getDisplayMetrics().density);
            shot(a, "95-board-pad-" + pads[0]);
        }
        // 여백 층: 글자 키 위·아래 여백만큼 윤곽선과 손잡이가 바깥으로
        prefs.raw().edit().putInt(Prefs.areaPadKey(KeyboardPreview.AREA_LETTERS, Prefs.SIDE_TOP), 12)
                .putInt(Prefs.areaPadKey(KeyboardPreview.AREA_LETTERS, Prefs.SIDE_BOTTOM), 16).commit();
        idle();
        p.setLayer(KeyboardPreview.LAYER_AREA);
        shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(400));
        p.select(KeyboardPreview.Selection.area(KeyboardPreview.AREA_LETTERS));
        idle();
        android.graphics.RectF ar = new android.graphics.RectF(), pr = new android.graphics.RectF();
        p.areaBounds(KeyboardPreview.AREA_LETTERS, ar);
        p.padBounds(KeyboardPreview.AREA_LETTERS, pr);
        float d = a.getResources().getDisplayMetrics().density;
        assertEquals(ar.top - 12 * d, pr.top, 1f);
        assertEquals(ar.bottom + 16 * d, pr.bottom, 1f);
        hc.invoke(p, KeyboardPreview.Handle.values()[9], pt); assertEquals(pr.top + in, pt[1], 1f);
        hc.invoke(p, KeyboardPreview.Handle.values()[11], pt); assertEquals(pr.bottom - in, pt[1], 1f);
        shot(a, "96-pad-letters-outer");
        prefs.raw().edit().remove(Prefs.PAD_TOP).remove(Prefs.PAD_BOTTOM).remove(Prefs.PAD_LEFT).remove(Prefs.PAD_RIGHT)
                .remove(Prefs.areaPadKey(KeyboardPreview.AREA_LETTERS, Prefs.SIDE_TOP))
                .remove(Prefs.areaPadKey(KeyboardPreview.AREA_LETTERS, Prefs.SIDE_BOTTOM)).commit();
    }

    @Test
    public void outlinesTouchWithoutGaps() throws Exception {
        Prefs pre = new Prefs(RuntimeEnvironment.getApplication());
        pre.raw().edit().putBoolean(Prefs.NUMBER_ROW, true).putInt(Prefs.KEY_GAP_Y, 0).putInt(Prefs.PAD_TOP, 0)
                .putInt(Prefs.PAD_BOTTOM, 0).commit();
        SettingsActivity a = launch("keyboard");
        KeyboardPreview p = find(a.getWindow().getDecorView(), KeyboardPreview.class);
        android.graphics.RectF bar = new android.graphics.RectF(), num = new android.graphics.RectF(),
                let = new android.graphics.RectF(), fn = new android.graphics.RectF();
        p.areaBounds(KeyboardPreview.AREA_TOOLBAR, bar);
        p.areaBounds(KeyboardPreview.AREA_NUMBER, num);
        p.areaBounds(KeyboardPreview.AREA_LETTERS, let);
        p.areaBounds(KeyboardPreview.AREA_FUNCTION, fn);
        log("outlines bar.bottom=" + bar.bottom + " num.top=" + num.top + " num.bottom=" + num.bottom + " let.top=" + let.top
                + " let.bottom=" + let.bottom + " fn.top=" + fn.top);
        assertTrue(bar.bottom >= num.top);   // 도구 막대와 숫자 줄도 겹친다
        assertTrue(num.bottom >= let.top);
        assertTrue(let.bottom >= fn.top);
        shot(a, "97-outlines-touch");
        pre.raw().edit().remove(Prefs.KEY_GAP_Y).remove(Prefs.PAD_TOP).remove(Prefs.PAD_BOTTOM).commit();
    }

    static int count(View v, String text) {
        int n = 0;
        if (v.getVisibility() == View.GONE) return 0;   // 가려진 칸은 세지 않는다
        if (v instanceof TextView && text.contentEquals(((TextView) v).getText())) n++;
        if (v instanceof ViewGroup) for (int i = 0; i < ((ViewGroup) v).getChildCount(); i++) n += count(((ViewGroup) v).getChildAt(i), text);
        return n;
    }

    /** 시트 본문에서 '기본값으로 되돌리기'가 맨 아래 마지막 항목인지. */
    static void assertResetLast(SettingsActivity a, String what) throws Exception {
        ScrollView sc = sheetOf(a).scroll();
        ViewGroup body = (ViewGroup) sc.getChildAt(0);
        assertEquals(what, 1, count(body, "기본값으로 되돌리기"));
        View last = null;
        for (int i = body.getChildCount() - 1; i >= 0 && last == null; i--) {
            View c = body.getChildAt(i);
            if (c.getVisibility() == View.VISIBLE && c instanceof ViewGroup && ((ViewGroup) c).getChildCount() > 0) last = c;
        }
        ViewGroup lg = (ViewGroup) last;
        View tail = lg.getChildAt(lg.getChildCount() - 1);
        while (tail instanceof ViewGroup && ((ViewGroup) tail).getChildCount() > 0 && !(tail instanceof TextView)) {
            tail = ((ViewGroup) tail).getChildAt(((ViewGroup) tail).getChildCount() - 1);
        }
        assertTrue(what + " last=" + tail, tail instanceof TextView && "기본값으로 되돌리기".contentEquals(((TextView) tail).getText()));
    }

    @Test
    public void resetButtonsAtBottomOfEverySheet() throws Exception {
        Prefs pre = new Prefs(RuntimeEnvironment.getApplication());
        pre.raw().edit().putBoolean(Prefs.NUMBER_ROW, true).commit();
        SettingsActivity a = launch("keyboard");
        Prefs prefs = new Prefs(a);
        KeyboardPreview p = find(a.getWindow().getDecorView(), KeyboardPreview.class);
        for (int area = 0; area < KeyboardPreview.AREAS; area++) {
            tapArea(p, area);
            shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(300));
            assertResetLast(a, "area " + area);
        }
        // 글자 키 영역: 값을 바꾸고 되돌리면 기본값
        prefs.raw().edit().putInt(Prefs.KEYBOARD_HEIGHT, 120).putInt(Prefs.areaPadKey(2, Prefs.SIDE_LEFT), 10).commit();
        idle();
        tapArea(p, KeyboardPreview.AREA_LETTERS);
        shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(300));
        View resetTv = findText(sheetOf(a).scroll(), "기본값으로 되돌리기");
        resetTv.performClick(); idle();
        click((AlertDialog) ShadowDialog.getLatestDialog(), AlertDialog.BUTTON_POSITIVE);
        idle();
        assertEquals(100, prefs.keyboardHeight());
        assertEquals(0, prefs.areaPadDp(2, Prefs.SIDE_LEFT));
        // 키 시트들
        p.setLayer(KeyboardPreview.LAYER_KEY);
        shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(400));
        tapKey(p, key(p, "ㄱ", Key.CHAR));
        assertResetLast(a, "char key");
        tapKey(p, key(p, null, Key.SPACE));
        assertResetLast(a, "space key");
        tapKey(p, key(p, null, Key.DELETE));
        assertResetLast(a, "delete key");
        // 키보드 전체 여백
        p.setLayer(KeyboardPreview.LAYER_BOARD);
        shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(400));
        android.graphics.RectF br = new android.graphics.RectF();
        p.boardBounds(br);
        tap(p.getChildAt(1), br.centerX(), br.centerY());
        assertResetLast(a, "board");
        shot(a, "98-board-reset");
    }

    static void tapDir(View root, String dirLabel) {
        View cell = findSub(root, dirLabel);
        assertNotNull("cell " + dirLabel, cell);
        cell.performClick();
        idle();
    }

    /** 방향 이름(작은 글자)을 가진 칸. */
    static View findSub(View v, String sub) {
        if (v instanceof com.alternative_studios.newswipe.ui.ExpressiveChoiceButton && v.isShown()
                && v.getContentDescription() != null && v.getContentDescription().toString().startsWith(sub + "으로")) return v;
        if (v instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) {
                View f = findSub(g.getChildAt(i), sub);
                if (f != null) return f;
            }
        }
        return null;
    }

    static View findText(View v, String text) {
        if (v instanceof TextView && v.isShown() && text.contentEquals(((TextView) v).getText())) return v;
        if (v instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) {
                View f = findText(g.getChildAt(i), text);
                if (f != null) return f;
            }
        }
        return null;
    }
}
