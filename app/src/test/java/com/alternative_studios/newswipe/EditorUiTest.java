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
import com.alternative_studios.newswipe.ui.ExpressiveSwitch;
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
 * 키보드 사용자화 화면의 UI 시험 (Robolectric): 미리보기 페이지·영역·키 고르기, 설정 창, 손잡이와 슬라이더, 되돌리기,
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
        // 쌍자음 입력이 '아래로 밀기'(기본)인 동안 ㄱ의 아래쪽은 쌍자음으로 고정이라 창이 열리지 않는다
        down.performClick();
        idle();
        assertNull(ShadowDialog.getLatestDialog());
        prefs.setConsonantInputMode(Prefs.INPUT_TAP);   // 연속 탭만 쓰면 아래쪽을 정할 수 있다
        idle();
        findSub(cur(a), "아래쪽").performClick();
        idle();
        AlertDialog d = (AlertDialog) ShadowDialog.getLatestDialog();
        assertNotNull(d);
        log("DIALOG: " + shadowOf(d).getTitle());
        shot(a, "03-dialog");
        d.dismiss();
        prefs.setConsonantInputMode(Prefs.INPUT_SWIPE);
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
        // 바로 밑은 자판, 숫자 줄을 꺼 두었으면 그 자리의 점선 빈 칸 (그 바로 밑이 자판)
        View underBar = ((ViewGroup) p.getChildAt(0)).getChildAt(1);
        assertEquals(topBar.getBottom(), underBar.getTop());
        if (underBar != kv) {
            assertFalse(prefs.numberRow());
            assertEquals(underBar.getBottom(), kv.getTop());
        }
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
        float[] knob = handle(p, 2);   // 글자 키 위 가장자리의 높이 손잡이
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
        assertTrue(visibleTexts(cur(a)).contains("가로 모드"));
        assertFalse(visibleTexts(cur(a)).contains("숫자 줄"));   // 숫자 줄은 꺼도 미리보기의 빈 칸을 눌러 연다: 목록에 섹션이 없다
        assertFalse(visibleTexts(cur(a)).contains("키 사이 좌우 간격"));   // 키 모양·분리 키보드는 '키보드 전체 설정' 창으로 옮겼다
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
        p.padBounds(KeyboardPreview.AREA_NUMBER, nr);
        java.lang.reflect.Method hc = KeyboardPreview.class.getDeclaredMethod("handleCenter", KeyboardPreview.Handle.class, float[].class);
        hc.setAccessible(true);
        float[] pt = new float[2];
        hc.invoke(p, KeyboardPreview.Handle.values()[1], pt);
        log("number row handle y=" + pt[1] + " outline top=" + nr.top);
        float inset = 9 * a.getResources().getDisplayMetrics().density;
        assertEquals(Math.max(nr.top, 0) + inset, pt[1], 1f);   // 숫자 줄 윤곽선 위 가장자리(조금 안쪽)의 높이 손잡이
        assertEquals(nr.centerX(), pt[0], 1f);
        shot(a, "12b-number-handle");
        drag(overlay, pt[0], pt[1], 0, -60);
        log("numberRow " + nrBefore + " -> " + prefs.numberRowHeight());
        assertTrue(prefs.numberRowHeight() > nrBefore);   // 위 가장자리를 위로 끌면 높아진다
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
        // 갈래로 묶인 기능 창: '지우기·실행 취소' 머리글과 칩이 있고, 줄을 누르면 고른다
        assertNotNull(findText(d.getWindow().getDecorView(), "지우기·실행 취소"));
        ((View) findText(d.getWindow().getDecorView(), "실행 취소").getParent()).performClick();
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
        boolean pickUp = findText(d.getWindow().getDecorView(), "언어 고르기 (말풍선에서 골라 바꾸기)") != null;
        d.dismiss(); idle();
        tapDir(cur(a), "왼쪽");
        d = (AlertDialog) ShadowDialog.getLatestDialog();
        boolean pickLeft = findText(d.getWindow().getDecorView(), "언어 고르기 (말풍선에서 골라 바꾸기)") != null;
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
        // 글자 키 창은 자판마다: 한글 항목은 한글 글자 키, 영어 항목은 영어 글자 키 창에서 찾는다
        assertEquals("keyboard:t:0:한글 글자 키", where.get("쌍자음 입력"));
        assertEquals("keyboard:t:1:영어 글자 키", where.get("영어 자동 대문자"));
        assertTrue(where.get("글자 키 높이").startsWith("keyboard:t:"));
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
        ot.invoke(a, where.get("쌍자음 입력"), "쌍자음 입력");
        shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(800));
        assertTrue(visibleTexts(cur(a)).contains("쌍자음 입력"));
        a.onBackPressed(); idle();
        // 영어 글자 키의 항목: 영어 탭으로 바뀌어 영어 글자 키 창이 열린다 (탭 버튼도 영어)
        ot.invoke(a, where.get("영어 자동 대문자"), "영어 자동 대문자");
        shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(800));
        KeyboardPreview pv = find(a.getWindow().getDecorView(), KeyboardPreview.class);
        assertEquals(KeyboardPreview.TAB_ENGLISH, pv.tab());
        assertNotNull(findText(a.getWindow().getDecorView(), "영어 글자 키 설정"));
        assertTrue(visibleTexts(cur(a)).contains("영어 자동 대문자"));
        a.onBackPressed(); idle();
        findText(a.getWindow().getDecorView(), "한글").performClick();
        shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(500));
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
        // 도구 막대를 끄면 스위치만 남고 다른 설정과 설명(안내·여백 포함)은 감춘다
        assertFalse(visibleTexts(cur(a)).contains("도구 버튼의 아이콘은 막대보다 커지지 않습니다."));
        assertFalse(visibleTexts(cur(a)).contains("도구 막대 높이"));
        assertFalse(visibleTexts(cur(a)).contains("밀기"));
        assertFalse(visibleTexts(cur(a)).contains("여백"));
        assertFalse(visibleTexts(cur(a)).contains("도구 버튼 하나하나") || visibleTexts(cur(a)).contains("도구 막대 버튼 하나하나"));
        shot(a, "52-placeholder-panel");
        // 다시 켜기: 패널은 그대로, 버튼이 돌아온다
        View row = (View) findText(cur(a), "도구 막대 표시").getParent().getParent();
        row.performClick(); idle();
        assertTrue(prefs.toolbar());
        assertNotNull(p.selection());
        assertTrue(visibleTexts(cur(a)).contains("도구 버튼의 아이콘은 막대보다 커지지 않습니다."));
        assertTrue(visibleTexts(cur(a)).contains("도구 막대 버튼 하나하나의 설정은"));
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
        assertNotNull(findText(decor, "'ㄱ' 키 설정"));
        // 쌍자음 입력이 '아래로 밀기'라 ㄱ의 아래쪽은 고정이다 (안내가 나오고 눌러도 입력 창이 열리지 않는다)
        assertTrue(visibleTexts(cur(a)).contains("아래쪽은 쌍자음으로 고정됩니다"));
        findSub(cur(a), "아래쪽").performClick();
        idle();
        assertNull(ShadowDialog.getLatestDialog());
        tapKey(p, key(p, null, Key.SPACE));
        String t = visibleTexts(cur(a));
        log("SPACE:\n" + t);
        assertTrue(t.contains("두 번 눌러 마침표") && t.contains("언어 이름 보이기") && t.contains("인식 범위 좁히기")
                && t.contains("커서 이동 속도 좌우"));
        assertFalse(t.contains("순서·유무"));
        shot(a, "87-space-key");
        assertTrue(t.contains("길게 누르면") && t.contains("길게 누른 뒤 밀어서 글자 선택"));
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
        assertNotNull(findText(decor, "'ㄴ' 키 설정"));
        findText(cur(a), "없음").performClick(); idle();
        byDesc(decor, "설정 창 닫기").performClick(); idle();
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
        assertNotNull(findText(decor, "한글 글자 키 설정"));
        String t = visibleTexts(cur(a));
        log("LETTERS:\n" + t);
        assertTrue(t.contains("한글 자판 배열") && t.contains("쌍자음 입력") && t.contains("키보드 밀기 사용자화"));
        assertFalse(t.contains("영어 자동 대문자"));   // 영어 글자 키 창에만 있다
        // 쌍자음·이중모음 입력 방식: 줄을 누르면 고르는 창 (아래로 밀기·연속 탭·둘 다)
        assertTrue(t.contains("쌍자음 입력\n  아래로 밀기") && t.contains("이중모음 입력\n  둘 다"));
        assertFalse(t.contains("쌍자음 인식 시간"));   // 연속 탭을 쓸 때만
        ((View) findText(cur(a), "쌍자음 입력").getParent()).performClick(); idle();
        AlertDialog modes = (AlertDialog) ShadowDialog.getLatestDialog();
        assertEquals(3, modes.getListView().getCount());
        // 다른 고르기 창과 같은 단추: 기본값(가운데), 취소
        assertTrue(modes.getButton(AlertDialog.BUTTON_NEUTRAL).isShown() && "기본값".contentEquals(modes.getButton(AlertDialog.BUTTON_NEUTRAL).getText()));
        assertTrue(modes.getButton(AlertDialog.BUTTON_NEGATIVE).isShown());
        shadowOf(modes).clickOnItem(1);   // 연속 탭
        shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(400));
        Prefs ip = new Prefs(a);
        assertTrue(ip.doubleTapConsonant() && !ip.swipeDownConsonant());
        assertEquals(Prefs.INPUT_TAP, ip.consonantInputMode());
        assertNull(key(p, "ㄱ", Key.CHAR).swipeText(Key.SWIPE_DOWN));   // 아래로 밀어 쌍자음은 쓰지 않는다
        String t2 = visibleTexts(cur(a));
        assertTrue(t2.contains("쌍자음 입력\n  두 번 연속으로 누르기") && t2.contains("쌍자음 인식 시간"));
        // 되돌리기로 원래 방식으로 (뒤의 되돌리기 시험이 빈 기록에서 시작하게)
        byDesc(decor, "마지막 변경 되돌리기").performClick(); idle();
        assertEquals(Prefs.INPUT_SWIPE, ip.consonantInputMode());
        assertFalse(byDesc(decor, "마지막 변경 되돌리기").isEnabled());
        assertEquals("ㄲ", key(p, "ㄱ", Key.CHAR).swipeText(Key.SWIPE_DOWN));
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
                && t.contains("기능키 미는 거리"));
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
        p.padBounds(KeyboardPreview.AREA_FUNCTION, fr);
        float[] fpt = new float[2];
        hc.invoke(p, KeyboardPreview.Handle.values()[7], fpt);
        log("fn handle y=" + fpt[1] + " outline top=" + fr.top);
        assertEquals(fr.top + 9 * a.getResources().getDisplayMetrics().density, fpt[1], 1f);   // 기능키 윤곽선 위 가장자리의 손잡이
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
        assertNotNull(findText(decor, "'ㄱ' 키 설정"));
        // 넘기면 창이 닫힌다
        swipe(p.getChildAt(1), p.getWidth() * 0.2f, p.getHeight() / 2f, p.getWidth() * 0.5f, 0);
        shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(400));
        assertEquals(KeyboardPreview.LAYER_AREA, p.layer());
        assertFalse(open(a));
        // 조금만 밀면 제자리
        swipe(p.getChildAt(1), p.getWidth() * 0.5f, p.getHeight() / 2f, -60, 0);
        shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(400));
        assertEquals(KeyboardPreview.LAYER_AREA, p.layer());
        // 영역 페이지에서 영역을 누르면: 설정 창(맨 아래 '여백' 슬라이더)과 그 영역 위 가장자리의 높이 손잡이.
        // 영역마다의 여백은 슬라이더로만 바꾼다 (여백 손잡이는 없다).
        java.lang.reflect.Method shown = KeyboardPreview.class.getDeclaredMethod("handleShown", KeyboardPreview.Handle.class);
        shown.setAccessible(true);
        tapArea(p, KeyboardPreview.AREA_LETTERS);
        assertTrue(open(a));
        cur(a).scrollTo(0, 99999); idle();
        String pt = visibleTexts(cur(a));
        log("LETTERS SHEET END:\n" + pt);
        assertTrue(pt.contains("여백") && pt.contains("왼쪽 여백") && pt.contains("아래쪽 여백"));
        for (KeyboardPreview.Handle h : KeyboardPreview.Handle.values()) {
            assertEquals(h.name(), h == KeyboardPreview.Handle.HEIGHT, (Boolean) shown.invoke(p, h));
        }
        // 높이 손잡이: 글자 키 윤곽선 위 가장자리(조금 안쪽), 가로 가운데. 위로 끌면 키보드 높이가 커진다 (5% 단위)
        java.lang.reflect.Method hcm0 = KeyboardPreview.class.getDeclaredMethod("handleCenter", KeyboardPreview.Handle.class, float[].class);
        hcm0.setAccessible(true);
        float[] kp = new float[2];
        hcm0.invoke(p, KeyboardPreview.Handle.HEIGHT, kp);
        android.graphics.RectF lr0 = new android.graphics.RectF();
        p.padBounds(KeyboardPreview.AREA_LETTERS, lr0);
        float in9 = 9 * a.getResources().getDisplayMetrics().density;
        assertEquals(lr0.centerX(), kp[0], 1f);
        assertEquals(Math.max(lr0.top, 0) + in9, kp[1], 1f);
        assertFalse(java.util.Arrays.stream(KeyboardPreview.class.getDeclaredFields()).anyMatch(f -> f.getName().startsWith("knob")));
        shot(a, "85h-height-handle");
        int kbh0 = prefs.keyboardHeight();
        drag(p.getChildAt(1), kp[0], kp[1], 0, -150);
        log("letters height " + kbh0 + " -> " + prefs.keyboardHeight());
        assertEquals(0, prefs.keyboardHeight() % 5);
        assertTrue(prefs.keyboardHeight() > kbh0);
        assertTrue(open(a));   // 끌어도 창은 그대로
        prefs.raw().edit().putInt(Prefs.KEYBOARD_HEIGHT, kbh0).commit();
        idle();
        // 아래로 끌면 낮아진다
        hcm0.invoke(p, KeyboardPreview.Handle.HEIGHT, kp);
        drag(p.getChildAt(1), kp[0], kp[1], 0, 80);
        assertTrue(prefs.keyboardHeight() < kbh0);
        prefs.raw().edit().putInt(Prefs.KEYBOARD_HEIGHT, kbh0).commit();
        idle();
        // 도구 막대: 위 가장자리의 높이 손잡이만
        tapArea(p, KeyboardPreview.AREA_TOOLBAR);
        for (KeyboardPreview.Handle h : KeyboardPreview.Handle.values()) {
            assertEquals(h.name(), h == KeyboardPreview.Handle.TOOLBAR, (Boolean) shown.invoke(p, h));
        }
        cur(a).scrollTo(0, 99999); idle();
        assertTrue(visibleTexts(cur(a)).contains("위쪽 여백"));   // 여백은 슬라이더로
        float[] tb = new float[2];
        hcm0.invoke(p, KeyboardPreview.Handle.TOOLBAR, tb);
        int tbh0 = prefs.toolbarHeight();
        drag(p.getChildAt(1), tb[0], tb[1], 0, -60);
        log("toolbar height " + tbh0 + " -> " + prefs.toolbarHeight());
        assertTrue(prefs.toolbarHeight() > tbh0);
        shot(a, "85g-toolbar-height");
        prefs.raw().edit().remove(Prefs.TOOLBAR_HEIGHT).commit();
        idle();
        a.onBackPressed(); idle();
        shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(400));
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
        assertTrue(pt.contains("키 모양") && pt.contains("키 사이 좌우 간격"));
        assertFalse(pt.contains("분리 키보드"));   // 분리 키보드는 한글 글자 키 창의 '상세 배열'에서 정한다
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
        // 목록에는 숫자 줄 섹션이 없고, 미리보기에 남은 점선 빈 칸을 눌러 다시 연다
        assertNull(findText(cur(a), "숫자 줄 설정"));
        android.graphics.RectF nrGap = new android.graphics.RectF();
        assertTrue(p.areaBounds(KeyboardPreview.AREA_NUMBER, nrGap));
        tap(p.getChildAt(1), nrGap.centerX(), nrGap.centerY());
        shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(400));
        assertTrue(open(a));
        assertEquals(KeyboardPreview.AREA_NUMBER, p.selection().area());
        shot(a, "85d-number-sheet-from-gap");
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
        // 여백 층: 글자 키 위·아래 여백만큼 윤곽선과 높이 손잡이가 바깥으로
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
        // 높이 손잡이는 여백을 더한 윤곽선의 위 가장자리에 붙는다
        hc.invoke(p, KeyboardPreview.Handle.HEIGHT, pt); assertEquals(pr.top + in, pt[1], 1f);
        shot(a, "96-pad-letters-outer");
        prefs.raw().edit().remove(Prefs.PAD_TOP).remove(Prefs.PAD_BOTTOM).remove(Prefs.PAD_LEFT).remove(Prefs.PAD_RIGHT)
                .remove(Prefs.areaPadKey(KeyboardPreview.AREA_LETTERS, Prefs.SIDE_TOP))
                .remove(Prefs.areaPadKey(KeyboardPreview.AREA_LETTERS, Prefs.SIDE_BOTTOM)).commit();
    }

    /** 키 상하 간격이 좁아도(8dp 이하) 위아래로 맞닿은 영역 윤곽선은 겹치지 않고 늘 같은 틈(2dp)을 둔다. */
    @Test
    public void outlinesKeepEvenGaps() throws Exception {
        Prefs pre = new Prefs(RuntimeEnvironment.getApplication());
        pre.raw().edit().putBoolean(Prefs.NUMBER_ROW, true).putInt(Prefs.KEY_GAP_Y, 0).putInt(Prefs.PAD_TOP, 0)
                .putInt(Prefs.PAD_BOTTOM, 0).commit();
        SettingsActivity a = launch("keyboard");
        KeyboardPreview p = find(a.getWindow().getDecorView(), KeyboardPreview.class);
        float d = a.getResources().getDisplayMetrics().density;
        android.graphics.RectF bar = new android.graphics.RectF(), num = new android.graphics.RectF(),
                let = new android.graphics.RectF(), fn = new android.graphics.RectF();
        for (int gap : new int[]{0, 4, 6, 8}) {
            pre.raw().edit().putInt(Prefs.KEY_GAP_Y, gap).commit();
            idle();
            p.areaBounds(KeyboardPreview.AREA_TOOLBAR, bar);
            p.areaBounds(KeyboardPreview.AREA_NUMBER, num);
            p.areaBounds(KeyboardPreview.AREA_LETTERS, let);
            p.areaBounds(KeyboardPreview.AREA_FUNCTION, fn);
            log("outlines gap=" + gap + " bar.bottom=" + bar.bottom + " num.top=" + num.top + " num.bottom=" + num.bottom
                    + " let.top=" + let.top + " let.bottom=" + let.bottom + " fn.top=" + fn.top);
            // 좌우 가장자리도 다른 영역들과 같은 선 (도구 막대만 튀어나오지 않는다)
            assertEquals(num.left, bar.left, 1.5f);
            assertEquals(num.right, bar.right, 1.5f);
            assertEquals(let.left, bar.left, 1.5f);
            // 위아래로 맞닿은 영역 윤곽선 사이는 간격과 상관없이 2dp
            assertEquals("gap=" + gap, 2 * d, num.top - bar.bottom, 1.5f);
            assertEquals("gap=" + gap, 2 * d, let.top - num.bottom, 1.5f);
            assertEquals("gap=" + gap, 2 * d, fn.top - let.bottom, 1.5f);
        }
        pre.raw().edit().putInt(Prefs.KEY_GAP_Y, 0).commit();
        idle();
        shot(a, "97-outlines-even-gaps");
        pre.raw().edit().remove(Prefs.KEY_GAP_Y).remove(Prefs.PAD_TOP).remove(Prefs.PAD_BOTTOM).commit();
    }

    /** 키 페이지: 키 사이 간격이 좁아도 이웃한 키 윤곽선은 상하·좌우 모두 2dp 이상 띈다 (도구 막대 버튼끼리도). */
    @Test
    public void keyOutlinesKeepEvenGaps() throws Exception {
        Prefs pre = new Prefs(RuntimeEnvironment.getApplication());
        pre.raw().edit().putBoolean(Prefs.NUMBER_ROW, true).commit();
        SettingsActivity a = launch("keyboard");
        KeyboardPreview p = find(a.getWindow().getDecorView(), KeyboardPreview.class);
        KeyboardView kv = find(p, KeyboardView.class);
        float d = a.getResources().getDisplayMetrics().density;
        android.graphics.RectF r1 = new android.graphics.RectF(), r2 = new android.graphics.RectF();
        for (int gap : new int[]{0, 2, 4, 6, 8}) {
            pre.raw().edit().putInt(Prefs.KEY_GAP_X, gap).putInt(Prefs.KEY_GAP_Y, gap).commit();
            idle();
            KeyboardLayout l = kv.getLayout();
            // 좌우: 숫자 줄의 맞닿은 두 키
            Key[] row0 = l.rows[0].keys, row1 = l.rows[1].keys;
            p.keyOutline(row0[0], r1);
            p.keyOutline(row0[1], r2);
            assertEquals(row0[0].rect.right, row0[1].rect.left, 1f);
            assertTrue("x gap=" + gap, r2.left - r1.right >= 2 * d - 1.5f);
            // 상하: 숫자 줄 첫 키와 그 아래 줄 첫 키
            p.keyOutline(row1[0], r2);
            assertTrue("y gap=" + gap, r2.top - r1.bottom >= 2 * d - 1.5f);
        }
        // 도구 막대에서 맞닿은 두 버튼
        com.alternative_studios.newswipe.ui.IconButton tool = find(p, com.alternative_studios.newswipe.ui.IconButton.class);
        ViewGroup bar = tool != null && tool.getTag() instanceof String ? (ViewGroup) tool.getParent() : null;
        View prev = null;
        for (int i = 0; bar != null && i < bar.getChildCount(); i++) {
            View b = bar.getChildAt(i);
            if (!(b.getTag() instanceof String)) { prev = null; continue; }
            if (prev != null && prev.getRight() == b.getLeft()) {
                p.toolOutline(prev, r1);
                p.toolOutline(b, r2);
                assertTrue(r2.left - r1.right >= 2 * d - 1.5f);
            }
            prev = b;
        }
        shot(a, "97-key-outlines-even-gaps");
        pre.raw().edit().remove(Prefs.KEY_GAP_X).remove(Prefs.KEY_GAP_Y).commit();
    }

    /** 한글 자판 배열에서 두벌식을 고르고, 균형 배열·격자 정렬과 함께 미리보기와 설정 창이 맞게 바뀌는지. */
    @Test
    public void dubeolsikLayout() throws Exception {
        Prefs pre = new Prefs(RuntimeEnvironment.getApplication());
        pre.raw().edit().putBoolean(Prefs.NUMBER_ROW, true).commit();
        SettingsActivity a = launch("keyboard");
        Prefs prefs = new Prefs(a);
        View decor = a.getWindow().getDecorView();
        KeyboardPreview p = find(decor, KeyboardPreview.class);
        tapArea(p, KeyboardPreview.AREA_LETTERS);
        String before = visibleTexts(cur(a));
        // 세 배열 중 두벌식이 맨 왼쪽
        assertTrue(before, before.contains("  두벌식\n  단모음\n  NewSwipe"));
        assertFalse(visibleTexts(cur(a)).contains("실험실"));
        findText(cur(a), "두벌식").performClick();
        idle();
        assertTrue(prefs.koreanDubeolsik());
        assertEquals(Prefs.KOREAN_LAYOUT_DUBEOLSIK, prefs.raw().getString(Prefs.KOREAN_LAYOUT, null));
        assertEquals(10, p.layout().columns);
        key(p, "ㅛ", Key.CHAR);
        key(p, null, Key.SHIFT);
        assertTrue(visibleTexts(cur(a)).contains("컴퓨터 키보드와 같은 표준 한글 배열입니다."));
        assertTrue(visibleTexts(cur(a)).contains("상세 배열\n  사용 안 함"));
        assertFalse(visibleTexts(cur(a)).contains("반으로 나"));   // '사용 안 함'이면 상세 배열 설명이 없다
        assertFalse(visibleTexts(cur(a)).contains("왼쪽 묶음 폭"));
        assertFalse(visibleTexts(cur(a)).contains("영어 자판도 균형 배열"));   // 균형 배열을 켠 뒤에만 보인다
        assertFalse(visibleTexts(cur(a)).contains("영어 자판 아래로 밀어 대문자 입력"));   // 영어 글자 키 창에만 있다
        shot(a, "a0-dubeolsik");
        // 균형 배열: 자음은 왼쪽 절반, 모음은 오른쪽 절반으로 나누되 각 쪽 안의 자리는 일반 두벌식 그대로
        // 상세 배열: 스페이스바 길게 누르기처럼 줄을 눌러 사용 안 함·자·모음 균형 배열·분리 키보드 중에서 고른다
        ((View) findText(cur(a), "상세 배열").getParent()).performClick();
        idle();
        AlertDialog detail = (AlertDialog) ShadowDialog.getLatestDialog();
        assertEquals(3, detail.getListView().getCount());
        shadowOf(detail).clickOnItem(2);   // 분리 키보드
        idle();
        assertTrue(prefs.portraitSplit());
        assertTrue(prefs.raw().getBoolean(Prefs.PORTRAIT_SPLIT, false));
        shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(600));
        String st = visibleTexts(cur(a));
        assertTrue(st, st.contains("상세 배열\n  분리 키보드"));
        // 고르면 창을 다시 열지 않아도 분리 키보드 섹션이 바로 보이고, 설명은 분리 키보드의 것만
        assertTrue(st, st.contains("왼쪽 묶음 폭") && st.contains("오른쪽 묶음 가로 위치"));
        assertTrue(st, st.contains("분리 키보드는 키보드 전체를 반으로 나눕니다. 영어 자판과 기호 자판에도 적용됩니다."));
        assertFalse(st, st.contains("자·모음 균형 배열은"));
        assertFalse(st, st.contains("자음 키 폭"));   // 자·모음 균형 배열 섹션은 균형 배열을 골랐을 때만
        assertFalse(st, st.contains("자·모음 분리 배열"));   // 두벌식에는 분리 키보드의 하위 토글이 없다
        findText(cur(a), "단모음").performClick();   // 분리 키보드는 모든 배열이 함께 쓴다
        shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(600));
        assertTrue(prefs.portraitSplit());
        String dt = visibleTexts(cur(a));
        assertTrue(dt, dt.contains("상세 배열\n  분리 키보드"));
        // 단모음: 분리 키보드의 하위 항목으로 '자·모음 분리 배열' 토글이 상세 배열 바로 아래(설명보다 위)에 보인다
        assertTrue(dt, dt.contains("상세 배열\n  분리 키보드\n  자·모음 분리 배열"));
        findText(cur(a), "NewSwipe 단모음").performClick();
        shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(600));
        String nt = visibleTexts(cur(a));
        assertTrue(nt, nt.contains("좌·우 균형 배열") && !nt.contains("자·모음 분리 배열"));
        findText(cur(a), "두벌식").performClick();
        shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(600));
        assertTrue(prefs.portraitSplit());
        assertFalse(visibleTexts(cur(a)).contains("좌·우 균형 배열"));
        ((View) findText(cur(a), "상세 배열").getParent()).performClick();
        idle();
        shadowOf((AlertDialog) ShadowDialog.getLatestDialog()).clickOnItem(1);   // 자·모음 균형 배열
        idle();
        assertFalse(prefs.portraitSplit());
        assertTrue(prefs.balancedLayout());
        assertTrue(prefs.raw().getBoolean(Prefs.BALANCED_LAYOUT, false));
        shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(600));
        String bt = visibleTexts(cur(a));
        assertFalse(bt, bt.contains("왼쪽 묶음 폭"));   // 분리 키보드 섹션은 접힌다
        assertTrue(bt, bt.contains("자·모음 균형 배열은 한글 글자 키 영역을 반으로 나눠 각각에 자음과 모음을 놓습니다."));
        assertFalse(bt, bt.contains("분리 키보드는"));
        // 균형 배열 사용자화의 슬라이더가 한글 글자 키 창의 '자·모음 균형 배열' 섹션으로 나와 바로 보인다
        assertTrue(bt, bt.contains("자음 키 폭") && bt.contains("모음 가로 위치"));
        assertFalse(bt, bt.contains("자·모음 경계 위치"));   // 경계 위치 설정은 없앴다
        assertFalse(bt, bt.contains("균형 배열 사용자화"));
        // 미리보기: 글자 키를 고른 채이면 자음·모음 쪽의 왼쪽·오른쪽 경계 손잡이가 보인다
        java.lang.reflect.Method bShown = KeyboardPreview.class.getDeclaredMethod("handleShown", KeyboardPreview.Handle.class);
        bShown.setAccessible(true);
        for (KeyboardPreview.Handle bh : new KeyboardPreview.Handle[]{KeyboardPreview.Handle.B_LEFT_START, KeyboardPreview.Handle.B_LEFT_END,
                KeyboardPreview.Handle.B_RIGHT_START, KeyboardPreview.Handle.B_RIGHT_END}) assertTrue(bh.name(), (Boolean) bShown.invoke(p, bh));
        assertTrue(visibleTexts(cur(a)).contains("영어 자판도 균형 배열"));
        View enRow = (View) findText(cur(a), "영어 자판도 균형 배열").getParent().getParent();
        enRow.performClick();
        idle();
        assertTrue(prefs.englishBalanced());
        enRow.performClick();   // 다시 끈다
        idle();
        assertFalse(prefs.englishBalanced());
        KeyboardView kv = find(p, KeyboardView.class);
        Key m = key(p, "ㅁ", Key.CHAR), b = key(p, "ㅂ", Key.CHAR), o = key(p, "ㅗ", Key.CHAR), yo = key(p, "ㅛ", Key.CHAR);
        assertEquals(b.rect.left + b.rect.width() / 2f, m.rect.left, 1.5f);   // ㅁ은 ㅂ보다 반 칸 들어간다
        assertEquals(yo.rect.left + yo.rect.width() / 2f, o.rect.left, 1.5f);   // ㅗ도 ㅛ보다 반 칸
        Key del = key(p, null, Key.DELETE), e = key(p, "ㅔ", Key.CHAR);
        assertEquals(e.rect.width() * 1.5f, del.rect.width(), 2f);   // ⌫는 일반 자판처럼 1.5칸 (늘리지 않는다)
        assertEquals(e.rect.right, del.rect.right, 1.5f);
        assertTrue(key(p, "ㅎ", Key.CHAR).rect.right <= kv.getWidth() / 2f + 1f);
        assertTrue(o.rect.left >= kv.getWidth() / 2f - 1f);
        cur(a).scrollTo(0, 450);   // 균형 배열의 하위 묶음(균형 배열 사용자화, 영어 자판도 균형 배열)이 보이게
        shot(a, "a1-dubeolsik-balanced");
        cur(a).scrollTo(0, 0);
        // 격자 정렬과도 함께 쓴다
        prefs.raw().edit().putBoolean(Prefs.GRID_LAYOUT, true).commit();
        idle();
        assertEquals(10, p.layout().columns);
        shot(a, "a2-dubeolsik-balanced-grid");
        // 균형 배열 값은 모든 한글 배열이 함께 쓴다
        findText(cur(a), "단모음").performClick();
        idle();
        assertFalse(prefs.koreanDubeolsik());
        assertTrue(prefs.balancedLayout());
        findText(cur(a), "두벌식").performClick();
        idle();
        assertTrue(prefs.balancedLayout());
        a.onBackPressed();
        idle();
        // 가로 모드 분리 키보드: 영어 자판처럼 가운데에서 나눠 자음은 왼쪽, 모음은 오른쪽
        prefs.raw().edit().putBoolean(Prefs.LANDSCAPE_SPLIT, true).remove(Prefs.GRID_LAYOUT).commit();
        SettingsActivity land = launch("landscapearea");
        String lt = visibleTexts(land.getWindow().getDecorView());
        assertFalse(lt.contains("자·모음 분리 배열"));   // 두벌식은 이미 5열씩이라 쓰지 않는다
        shot(land, "a3-dubeolsik-landscape");
        // 기능키 순서·유무: 두벌식은 Shift가 늘 있다
        Activity bk = Robolectric.buildActivity(BottomKeysEditorActivity.class).setup().get();
        idle();
        // 두벌식도 Shift 키를 없앨 수 있다 (두벌식만의 설정)
        ViewGroup shiftRow = (ViewGroup) findText(bk.getWindow().getDecorView(), "Shift 키 (⇧)").getParent().getParent();
        View shiftSwitch = shiftRow.getChildAt(1);
        assertTrue(shiftSwitch.isEnabled());
        shiftSwitch.performClick();
        idle();
        assertTrue(prefs.raw().getBoolean(Prefs.DUBEOLSIK_SHIFT_HIDDEN, false));
        assertFalse(prefs.raw().contains(Prefs.KOREAN_SHIFT_HIDDEN));
        pre.raw().edit().remove(Prefs.DUBEOLSIK_SHIFT_HIDDEN).commit();
        pre.raw().edit().remove(Prefs.KOREAN_LAYOUT).remove(Prefs.LANDSCAPE_SPLIT).remove(Prefs.NUMBER_ROW)
                .remove(Prefs.BALANCED_LAYOUT).remove(Prefs.PORTRAIT_SPLIT).commit();
    }

    /**
     * 영역·키보드 전체를 고른 채 한글·영어·기호 탭을 바꾸면 창이 그대로 열려 있고, 글자 키 창은 그 자판의 항목으로 바뀐다.
     * 키를 고른 채 바꾸면 창이 닫힌다.
     */
    @Test
    public void tabSwitchKeepsTheAreaSheet() throws Exception {
        SettingsActivity a = launch("keyboard");
        View decor = a.getWindow().getDecorView();
        KeyboardPreview p = find(decor, KeyboardPreview.class);
        tapArea(p, KeyboardPreview.AREA_LETTERS);
        assertNotNull(findText(decor, "한글 글자 키 설정"));
        View tabs = findText(decor, "영어");
        // 영어 탭: 같은 영역이 고른 채로, 창은 영어 글자 키의 항목으로
        tabs.performClick();
        shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(500));
        assertEquals(KeyboardPreview.TAB_ENGLISH, p.tab());
        assertNotNull(p.selection());
        assertEquals(KeyboardPreview.SEL_AREA, p.selection().kind);
        assertTrue(sheetOf(a).isOpen());
        assertNotNull(findText(decor, "영어 글자 키 설정"));
        String en = visibleTexts(cur(a));
        assertTrue(en.contains("영어 자동 대문자") && en.contains("영어 자판 아래로 밀어 대문자 입력")
                && en.contains("이메일 입력란은 영어로 시작") && en.contains("글자 키 높이"));
        assertFalse(en.contains("한글 자판 배열") || en.contains("쌍자음 입력"));
        assertTrue(en.contains("영어 글자 키 하나하나의 설정은"));
        shot(a, "b0-english-letters");
        // 기호 탭: 공통 항목만
        findText(decor, "기호").performClick();
        shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(500));
        assertTrue(sheetOf(a).isOpen());
        assertNotNull(findText(decor, "기호 글자 키 설정"));
        String sym = visibleTexts(cur(a));
        assertTrue(sym.contains("글자 키 높이") && sym.contains("문자 길게 누르기 시간"));
        assertFalse(sym.contains("영어 자동 대문자") || sym.contains("한글 자판 배열") || sym.contains("\n  입력\n"));   // 입력 섹션이 없다
        // 기능키 영역도 그대로 유지된다
        tapArea(p, KeyboardPreview.AREA_FUNCTION);
        findText(decor, "한글").performClick();
        shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(500));
        assertTrue(sheetOf(a).isOpen());
        assertNotNull(findText(decor, "기능키 설정"));
        // 키를 고른 채 바꾸면 닫힌다
        p.setLayer(KeyboardPreview.LAYER_KEY);
        shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(400));
        tapKey(p, key(p, "ㄱ", Key.CHAR));
        assertTrue(sheetOf(a).isOpen());
        findText(decor, "영어").performClick();
        shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(500));
        assertFalse(sheetOf(a).isOpen());
        assertNull(p.selection());
    }

    /** 글자 키 창의 '기본값으로 되돌리기'는 그 자판의 값과 함께 쓰는 값만 되돌린다. */
    @Test
    public void letterResetIsPerKeyboard() throws Exception {
        Prefs pre = new Prefs(RuntimeEnvironment.getApplication());
        pre.raw().edit().putBoolean(Prefs.AUTO_CAP, false).putBoolean(Prefs.DOUBLE_TAP_VOWEL, false)
                .putInt(Prefs.LONG_PRESS_MS, 500).commit();
        SettingsActivity a = launch("keyboard");
        View decor = a.getWindow().getDecorView();
        KeyboardPreview p = find(decor, KeyboardPreview.class);
        p.setTab(KeyboardPreview.TAB_ENGLISH);
        idle();
        tapArea(p, KeyboardPreview.AREA_LETTERS);
        shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(300));
        assertNotNull(findText(decor, "영어 글자 키 설정"));
        findText(sheetOf(a).scroll(), "기본값으로 되돌리기").performClick(); idle();
        click((AlertDialog) ShadowDialog.getLatestDialog(), AlertDialog.BUTTON_POSITIVE);
        idle();
        assertFalse(pre.raw().contains(Prefs.AUTO_CAP));          // 영어 글자 키의 값
        assertFalse(pre.raw().contains(Prefs.LONG_PRESS_MS));     // 함께 쓰는 값
        assertTrue(pre.raw().contains(Prefs.DOUBLE_TAP_VOWEL));   // 한글 글자 키의 값은 그대로
        pre.raw().edit().remove(Prefs.DOUBLE_TAP_VOWEL).commit();
    }

    /** 영어 자판 아래로 밀어 대문자 입력: 키 설정 창의 아래로 밀기는 대문자로 고정되어 바꿀 수 없다. */
    @Test
    public void englishSwipeDownUpperLocksTheKeySetting() throws Exception {
        Prefs pre = new Prefs(RuntimeEnvironment.getApplication());
        pre.raw().edit().putBoolean(Prefs.ENGLISH_SWIPE_UPPER, true).commit();
        SettingsActivity a = launch("keyboard");
        View decor = a.getWindow().getDecorView();
        KeyboardPreview p = find(decor, KeyboardPreview.class);
        p.setTab(KeyboardPreview.TAB_ENGLISH);
        idle();
        p.setLayer(KeyboardPreview.LAYER_KEY);
        shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(400));
        assertEquals("Q", key(p, "q", Key.CHAR).swipeText(Key.SWIPE_DOWN));
        tapKey(p, key(p, "q", Key.CHAR));
        assertNotNull(findText(decor, "'q' 키 설정"));
        assertTrue(visibleTexts(cur(a)).contains("대문자로 고정됩니다"));
        // 아래쪽 칸을 눌러도 입력 창이 열리지 않는다 (알림만)
        View down = findSub(cur(a), "아래쪽");
        assertNotNull(down);
        down.performClick();
        idle();
        assertNull(ShadowDialog.getLatestDialog());
        pre.raw().edit().remove(Prefs.ENGLISH_SWIPE_UPPER).commit();
    }

    /**
     * 자·모음 균형 배열: 키보드 사용자화 미리보기에서 글자 키를 고르면 자음·모음 쪽마다 왼쪽·오른쪽 가장자리에 경계 손잡이가 생긴다.
     * 끄는 쪽 가장자리만 손가락을 따라 움직이고(반대쪽은 그대로) 폭(5% 단위)·가로 위치가 함께 바뀌며, 슬라이더도 따라간다.
     * 묶음 가운데의 ↔ 버튼을 끌면 폭은 그대로 묶음이 옮겨 간다.
     * 다른 쪽 묶음 가장자리를 넘지 못하고, 자리가 있으면 폭이 100%를 넘는다. 두 쪽 윤곽선 사이는 늘 2dp 이상이고,
     * 글자 키 높이 손잡이는 윤곽선 위 가장자리 가운데에 있다.
     */
    @Test
    public void balancedBlocksInTheEditor() throws Exception {
        Prefs pre = new Prefs(RuntimeEnvironment.getApplication());
        // 두 쪽이 가운데에서 맞닿는 모양(폭 100%, 위치 0)에서 시작한다 (기본값은 폭 90%라 가운데가 벌어져 있다)
        pre.raw().edit().putBoolean(pre.balancedLayoutKey(), true)
                .putInt(pre.balancedConsonantWidthKey(), 100).putInt(pre.balancedConsonantPosKey(), 0)
                .putInt(pre.balancedVowelWidthKey(), 100).putInt(pre.balancedVowelPosKey(), 0).commit();
        SettingsActivity a = launch("keyboard");
        View decor = a.getWindow().getDecorView();
        KeyboardPreview p = find(decor, KeyboardPreview.class);
        tapArea(p, KeyboardPreview.AREA_LETTERS);
        assertTrue(open(a));
        assertTrue(visibleTexts(cur(a)).contains("자음 키 폭"));
        View overlay = p.getChildAt(1);
        java.lang.reflect.Method hc = KeyboardPreview.class.getDeclaredMethod("handleCenter", KeyboardPreview.Handle.class, float[].class);
        hc.setAccessible(true);
        float dp = a.getResources().getDisplayMetrics().density, in = 9 * dp;
        android.graphics.RectF l = new android.graphics.RectF(), r = new android.graphics.RectF();
        assertTrue(p.blockBounds(true, l) && p.blockBounds(false, r));
        // 두 쪽 모두 폭 100%로 맞닿아도 윤곽선 사이는 2dp 이상
        assertTrue((r.left - l.right) + " < 2dp", r.left - l.right >= 2 * dp - 0.5f);
        // 경계 손잡이: 각 쪽 윤곽선의 왼쪽·오른쪽 가장자리(조금 안쪽), 세로 가운데
        float[] ls = new float[2], le = new float[2], rs = new float[2], re = new float[2], height = new float[2];
        assertTrue((Boolean) hc.invoke(p, KeyboardPreview.Handle.B_LEFT_START, ls));
        assertTrue((Boolean) hc.invoke(p, KeyboardPreview.Handle.B_LEFT_END, le));
        assertTrue((Boolean) hc.invoke(p, KeyboardPreview.Handle.B_RIGHT_START, rs));
        assertTrue((Boolean) hc.invoke(p, KeyboardPreview.Handle.B_RIGHT_END, re));
        assertEquals(l.left + in, ls[0], 1f);
        assertEquals(l.right - in, le[0], 1f);
        assertEquals(r.left + in, rs[0], 1f);
        assertEquals(r.right - in, re[0], 1f);
        assertEquals(l.centerY(), ls[1], 1f);
        // 글자 키 높이 손잡이: 두 쪽 각각의 위 가장자리 가운데에 하나씩, 둘 다 글자 키 전체 높이를 바꾼다
        float[] height2 = new float[2];
        assertTrue((Boolean) hc.invoke(p, KeyboardPreview.Handle.HEIGHT, height));
        assertTrue((Boolean) hc.invoke(p, KeyboardPreview.Handle.HEIGHT_RIGHT, height2));
        assertEquals(l.centerX(), height[0], 1f);
        assertEquals(l.top + in, height[1], 1f);
        assertEquals(r.centerX(), height2[0], 1f);
        assertEquals(r.top + in, height2[1], 1f);
        int kh0 = pre.keyboardHeight();
        drag(overlay, height2[0], height2[1], 0, -120);
        log("letters height (right handle) " + kh0 + " -> " + pre.keyboardHeight());
        assertTrue(pre.keyboardHeight() > kh0);
        pre.raw().edit().putInt(Prefs.KEYBOARD_HEIGHT, kh0).commit();
        idle();
        assertTrue((Boolean) hc.invoke(p, KeyboardPreview.Handle.HEIGHT, height));
        drag(overlay, height[0], height[1], 0, 150);   // 움직임 문턱을 넘고도 5% 넘게 움직이는 거리
        assertTrue("height " + kh0 + " -> " + pre.keyboardHeight(), pre.keyboardHeight() < kh0);
        pre.raw().edit().putInt(Prefs.KEYBOARD_HEIGHT, kh0).commit();
        idle();
        assertTrue(p.blockBounds(true, l) && p.blockBounds(false, r));
        shot(a, "c0-balanced-editor");
        float unit = (r.right - l.left) / 10f;   // 한 칸 (px)
        // 자음 쪽 오른쪽 경계를 왼쪽으로 한 칸: 폭이 줄고 왼쪽 경계(시작)는 그대로, 슬라이더도 따라간다
        float[] e0 = pre.balancedExtents();
        int w0 = pre.balancedConsonantWidth();
        drag(overlay, le[0], le[1], -unit, 0);
        float[] e1 = pre.balancedExtents();
        int w1 = pre.balancedConsonantWidth();
        log("consonant width " + w0 + " -> " + w1 + ", start " + e0[0] + " -> " + e1[0]);
        assertTrue(w1 < w0);
        assertEquals(0, w1 % 5);   // 폭은 5% 단위로 움직인다
        assertEquals(e0[0], e1[0], 0.06f);
        assertTrue(visibleTexts(cur(a)).contains(w1 + "%"));
        // 모음 쪽 왼쪽 경계를 왼쪽으로 많이 끌면 자음 쪽 끝에서 멈추고(겹치지 않는다), 오른쪽 끝은 그대로라 폭이 100%를 넘는다
        assertTrue((Boolean) hc.invoke(p, KeyboardPreview.Handle.B_RIGHT_START, rs));
        drag(overlay, rs[0], rs[1], -400, 0);
        float[] e2 = pre.balancedExtents();
        log("vowel width " + pre.balancedVowelWidth() + " extents " + java.util.Arrays.toString(e2));
        assertTrue(e2[0] + e2[1] <= e2[2] + 1e-4f);
        assertEquals(e1[0] + e1[1], e2[2], 0.06f);
        assertEquals(10f, e2[2] + e2[3], 0.06f);
        assertTrue(pre.balancedVowelWidth() > 100);
        assertTrue(p.blockBounds(true, l) && p.blockBounds(false, r));
        assertTrue(r.left - l.right >= 2 * dp - 0.5f);
        // 자음 쪽 왼쪽 경계를 오른쪽으로: 시작이 옮겨 가고 끝은 그대로
        assertTrue((Boolean) hc.invoke(p, KeyboardPreview.Handle.B_LEFT_START, ls));
        drag(overlay, ls[0], ls[1], unit * 0.6f, 0);   // 폭이 최솟값(60%)보다 넓게 남는 거리
        float[] e3 = pre.balancedExtents();
        assertTrue(e3[0] > e2[0] + 0.05f);
        assertEquals(e2[0] + e2[1], e3[0] + e3[1], 0.06f);
        assertEquals(0, pre.balancedConsonantWidth() % 5);
        // 모음 쪽 오른쪽 경계를 왼쪽으로: 끝이 옮겨 가고 시작은 그대로
        assertTrue((Boolean) hc.invoke(p, KeyboardPreview.Handle.B_RIGHT_END, re));
        drag(overlay, re[0], re[1], -unit * 0.6f, 0);
        float[] e4 = pre.balancedExtents();
        assertTrue(e4[2] + e4[3] < 10f - 0.05f);
        assertEquals(e3[2], e4[2], 0.06f);
        assertEquals(0, pre.balancedVowelWidth() % 5);
        shot(a, "c1-balanced-editor-edges");
        // 가운데의 ↔ 버튼: 폭은 그대로 두고 묶음을 옮긴다. 자음 쪽은 왼쪽 끝에서, 모음 쪽은 오른쪽 끝에서 멈춘다
        float[] lm = new float[2], rm = new float[2];
        assertTrue((Boolean) hc.invoke(p, KeyboardPreview.Handle.B_LEFT_MOVE, lm));
        assertTrue(p.blockBounds(true, l));
        assertEquals(l.centerX(), lm[0], 1f);
        assertEquals(l.centerY(), lm[1], 1f);
        int cw4 = pre.balancedConsonantWidth();
        drag(overlay, lm[0], lm[1], -unit * 2, 0);
        float[] e5 = pre.balancedExtents();
        log("consonant moved " + java.util.Arrays.toString(e4) + " -> " + java.util.Arrays.toString(e5));
        assertEquals(0f, e5[0], 0.06f);
        assertEquals(cw4, pre.balancedConsonantWidth());
        assertTrue((Boolean) hc.invoke(p, KeyboardPreview.Handle.B_RIGHT_MOVE, rm));
        int vw5 = pre.balancedVowelWidth();
        drag(overlay, rm[0], rm[1], unit * 2, 0);
        float[] e6 = pre.balancedExtents();
        assertEquals(10f, e6[2] + e6[3], 0.06f);
        assertEquals(vw5, pre.balancedVowelWidth());
        // 모음 쪽을 왼쪽으로 많이 옮기면 자음 쪽 끝에서 멈춘다 (겹치지 않는다)
        assertTrue((Boolean) hc.invoke(p, KeyboardPreview.Handle.B_RIGHT_MOVE, rm));
        drag(overlay, rm[0], rm[1], -unit * 8, 0);
        float[] e7 = pre.balancedExtents();
        assertEquals(e6[0] + e6[1], e7[2], 0.06f);
        assertEquals(vw5, pre.balancedVowelWidth());
        shot(a, "c1b-balanced-editor-move");
        // 자음 쪽이 가운데를 넘어 옮겨 가도(60%, 오른쪽 경계 40 → 4~7칸) 윤곽선은 자음 키만, 모음 윤곽선은 모음 키만 감싼다
        pre.raw().edit().putInt(pre.balancedConsonantWidthKey(), 60).putInt(pre.balancedConsonantPosKey(), 40)
                .putInt(pre.balancedVowelWidthKey(), 60).putInt(pre.balancedVowelPosKey(), 40).commit();
        idle();
        assertTrue(p.blockBounds(true, l) && p.blockBounds(false, r));
        assertTrue(r.left - l.right >= 2 * dp - 0.5f);
        KeyboardLayout crossed = p.layout();
        int first = crossed.rows.length - 4;   // 글자 줄 세 개 (맨 아래 줄 위)
        for (int row = first; row < crossed.rows.length - 1; row++) {
            for (Key k : crossed.rows[row].keys) {
                if (k.type == Key.GAP || k.type == Key.SPACER || k.isHidden()) continue;
                android.graphics.RectF side = k.isRightBlock() ? r : l;
                assertTrue(k.label + " " + k.rect + " " + side, k.rect.centerX() > side.left && k.rect.centerX() < side.right);
            }
        }
        shot(a, "c2-balanced-editor-crossed");
        pre.resetBalancedKeys();
        pre.raw().edit().remove(pre.balancedLayoutKey()).commit();
    }

    /**
     * 가로 모드 분리 키보드: 세로 모드 '키보드 사용자화'처럼 왼쪽·오른쪽 묶음을 합쳐 '글자 키'로 함께 고른다.
     * 이름표·설정 창 제목도 '글자 키'이고, 창에는 두 묶음의 폭·가로 위치와 글자 키 높이가 함께 있다.
     */
    @Test
    public void landscapeSplitBlocksAreOneLettersArea() throws Exception {
        Prefs pre = new Prefs(RuntimeEnvironment.getApplication());
        pre.raw().edit().putBoolean(Prefs.LANDSCAPE_SPLIT, true).commit();
        SettingsActivity a = launch("landscapearea");
        View decor = a.getWindow().getDecorView();
        KeyboardPreview p = find(decor, KeyboardPreview.class);
        android.graphics.RectF lb = new android.graphics.RectF(), rb = new android.graphics.RectF();
        assertTrue(p.profileAreaBounds(KeyboardPreview.AREA_LEFT, lb) && p.profileAreaBounds(KeyboardPreview.AREA_RIGHT, rb));
        // 오른쪽 묶음을 눌러도 글자 키
        tap(p.getChildAt(1), rb.centerX(), rb.top + rb.height() / 4f);
        assertNotNull(p.selection());
        assertEquals(KeyboardPreview.AREA_LETTERS, p.selection().area());
        assertNotNull(findText(decor, "글자 키 설정"));
        assertNull(findText(decor, "오른쪽 묶음 설정"));
        String t = visibleTexts(decor);
        log("LANDSCAPE LETTERS:\n" + t);
        assertTrue(t, t.contains("왼쪽 묶음 폭") && t.contains("왼쪽 묶음 가로 위치") && t.contains("오른쪽 묶음 폭")
                && t.contains("오른쪽 묶음 가로 위치") && t.contains("글자 키 높이"));
        // 두 묶음의 경계 손잡이·↔ 버튼과 묶음마다의 높이 손잡이가 함께 보인다
        java.lang.reflect.Method shown = KeyboardPreview.class.getDeclaredMethod("handleShown", KeyboardPreview.Handle.class);
        shown.setAccessible(true);
        for (KeyboardPreview.Handle h : new KeyboardPreview.Handle[]{KeyboardPreview.Handle.B_LEFT_START, KeyboardPreview.Handle.B_LEFT_END,
                KeyboardPreview.Handle.B_RIGHT_START, KeyboardPreview.Handle.B_RIGHT_END, KeyboardPreview.Handle.B_LEFT_MOVE,
                KeyboardPreview.Handle.B_RIGHT_MOVE, KeyboardPreview.Handle.HEIGHT, KeyboardPreview.Handle.HEIGHT_RIGHT}) {
            assertTrue(h.name(), (Boolean) shown.invoke(p, h));
        }
        java.lang.reflect.Method hc = KeyboardPreview.class.getDeclaredMethod("handleCenter", KeyboardPreview.Handle.class, float[].class);
        hc.setAccessible(true);
        float[] hl = new float[2], hr = new float[2];
        assertTrue((Boolean) hc.invoke(p, KeyboardPreview.Handle.HEIGHT, hl));
        assertTrue((Boolean) hc.invoke(p, KeyboardPreview.Handle.HEIGHT_RIGHT, hr));
        assertEquals(lb.centerX(), hl[0], 1f);
        assertEquals(rb.centerX(), hr[0], 1f);
        shot(a, "d0-landscape-split-letters");
        // 같은 글자 키(왼쪽 묶음)를 다시 누르면 창이 닫힌다
        tap(p.getChildAt(1), lb.centerX(), lb.top + lb.height() / 4f);
        assertNull(p.selection());
        pre.raw().edit().remove(Prefs.LANDSCAPE_SPLIT).commit();
    }

    /** 가로 모드 키보드 사용자화도 머리글에 되돌리기 버튼이 있고, 이 화면에서 바꾼 값만 되돌린다. */
    @Test
    public void landscapeAreaHasUndoInHeader() throws Exception {
        Prefs pre = new Prefs(RuntimeEnvironment.getApplication());
        SettingsActivity a = launch("landscapearea");
        View decor = a.getWindow().getDecorView();
        View undo = byDesc(decor, "마지막 변경 되돌리기");
        assertNotNull(undo);
        assertFalse("바꾼 것이 없으면 꺼져 있다", undo.isEnabled());
        // 도구 막대 높이를 바꾸면 켜지고, 누르면 원래 값(세로 모드 값을 따름)으로 돌아간다.
        int before = pre.profileView(Prefs.PROFILE_LANDSCAPE).toolbarHeight();
        String key = pre.profileEditView(Prefs.PROFILE_LANDSCAPE).toolbarHeightKey();
        pre.raw().edit().putInt(key, 80).commit();
        shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(100));
        idle();
        assertTrue(undo.isEnabled());
        undo.performClick();
        idle();
        assertEquals(before, pre.profileView(Prefs.PROFILE_LANDSCAPE).toolbarHeight());
        assertFalse(pre.raw().contains(key));
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
        // 감춰 둔 칸(예: 도구 막대 창의 '도구 막대 공간을 완전히 없애려면…' 안내)은 보이지 않으니 따지지 않는다.
        View tail = lastShownChild((ViewGroup) last);
        while (tail instanceof ViewGroup && !(tail instanceof TextView) && lastShownChild((ViewGroup) tail) != null) {
            tail = lastShownChild((ViewGroup) tail);
        }
        assertTrue(what + " last=" + tail, tail instanceof TextView && "기본값으로 되돌리기".contentEquals(((TextView) tail).getText()));
    }

    private static View lastShownChild(ViewGroup g) {
        for (int i = g.getChildCount() - 1; i >= 0; i--) if (g.getChildAt(i).getVisibility() != View.GONE) return g.getChildAt(i);
        return null;
    }

    /**
     * 도구 막대를 꺼도 단어 추천이 켜져 있으면 도구 막대 창의 되돌리기 아래에 그 자리를 없애는 법을 알리고,
     * '단어 추천'을 누르면 '추천, 학습 및 완성' 메뉴를 그 스위치를 깜빡이도록 연다.
     */
    @Test
    public void toolbarSpaceNoteWhenSuggestKeepsBar() throws Exception {
        String note = "도구 막대 공간을 완전히 없애려면 '추천, 학습 및 완성'에서 단어 추천을 끄세요.";
        Prefs pre = new Prefs(RuntimeEnvironment.getApplication());
        pre.raw().edit().putBoolean(Prefs.TOOLBAR, true).putBoolean(Prefs.SUGGEST_WORDS, true).commit();
        SettingsActivity a = launch("keyboard");
        KeyboardPreview p = find(a.getWindow().getDecorView(), KeyboardPreview.class);
        tapArea(p, KeyboardPreview.AREA_TOOLBAR);
        shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(300));
        ScrollView sc = sheetOf(a).scroll();
        assertNull("도구 막대가 켜져 있으면 안내가 없다", findText(sc, note));
        // 도구 막대 표시를 끄면 안내가 되돌리기 아래에 나타난다.
        ((View) findText(sc, "도구 막대 표시").getParent().getParent()).performClick();
        shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(600));
        assertFalse(pre.toolbar());
        TextView t = (TextView) findText(sc, note);
        assertNotNull(t);
        ViewGroup parent = (ViewGroup) t.getParent();
        View before = parent.getChildAt(parent.indexOfChild(t) - 1);
        assertTrue(before instanceof TextView && "기본값으로 되돌리기".contentEquals(((TextView) before).getText()));
        // 강조색 '단어 추천'을 누르면 검색으로 찾은 것처럼 그 메뉴를 연다.
        android.text.Spanned sp = (android.text.Spanned) t.getText();
        android.text.style.ClickableSpan[] links = sp.getSpans(0, sp.length(), android.text.style.ClickableSpan.class);
        assertEquals(1, links.length);
        assertEquals("단어 추천", sp.subSequence(sp.getSpanStart(links[0]), sp.getSpanEnd(links[0])).toString());
        links[0].onClick(t);
        Intent next = shadowOf(a).getNextStartedActivity();
        assertEquals("words", next.getStringExtra(SettingsActivity.EXTRA_SECTION));
        assertEquals("단어 추천", next.getStringExtra(SettingsActivity.EXTRA_HIGHLIGHT));
        // 도구 막대 표시를 다시 켜면 안내도 사라진다.
        ((View) findText(sc, "도구 막대 표시").getParent().getParent()).performClick();
        shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(600));
        assertTrue(pre.toolbar());
        assertNull(findText(sc, note));
    }

    /** Neumorphism 디자인을 켜면 키보드 전체 설정의 키 그림자 스위치는 꺼진 모양으로 흐리게 막히고 설명이 바뀐다. */
    @Test
    public void keyShadowDisabledWithNeumorphism() throws Exception {
        Prefs pre = new Prefs(RuntimeEnvironment.getApplication());
        pre.raw().edit().putBoolean(Prefs.NEUMORPHISM, true).putBoolean(Prefs.KEY_SHADOW, true).commit();
        SettingsActivity a = launch("keyboard");
        KeyboardPreview p = find(a.getWindow().getDecorView(), KeyboardPreview.class);
        p.setLayer(KeyboardPreview.LAYER_BOARD);
        shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(400));
        android.graphics.RectF br = new android.graphics.RectF();
        p.boardBounds(br);
        tap(p.getChildAt(1), br.centerX(), br.centerY());
        shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(300));
        ScrollView sc = sheetOf(a).scroll();
        assertNotNull(findText(sc, "Neumorphism 디자인 사용 중에는 사용할 수 없습니다."));
        assertNull(findText(sc, "그림자 세기"));
        ViewGroup row = (ViewGroup) findText(sc, "키 그림자").getParent().getParent();
        ExpressiveSwitch sw = (ExpressiveSwitch) row.getChildAt(row.getChildCount() - 1);
        assertFalse(sw.isChecked());
        assertFalse(sw.isEnabled());
        row.performClick();
        sw.performClick();
        idle();
        assertFalse(sw.isChecked());
        assertTrue("저장된 값은 그대로", pre.keyShadow());
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

    /**
     * 설정 검색은 메인 화면에서 모은다: 메인 화면에는 키 편집 칸·안내 칸이 없어도 키보드 사용자화의 영역·키·버튼 창 항목과
     * 그 창 자체("… 설정")가 검색 목록에 들어가고, 고르면 키보드 사용자화에서 그 창이 열린다.
     */
    @Test
    public void searchHarvestsEditorPartsFromMainScreen() throws Exception {
        // 첫 시작 가이드를 마쳐야 메인 화면이 열린다 (아니면 가이드로 넘어가고 이 화면은 닫힌다).
        new Prefs(RuntimeEnvironment.getApplication()).raw().edit().putBoolean(Prefs.ONBOARDING_DONE, true).commit();
        SettingsActivity main = launch(null);
        java.lang.reflect.Method hp = SettingsActivity.class.getDeclaredMethod("harvestEditorParts", java.util.List.class);
        hp.setAccessible(true);
        java.util.List<String[]> parts = new java.util.ArrayList<>();
        hp.invoke(main, parts);
        java.util.Map<String, String> where = new java.util.HashMap<>();
        for (String[] e : parts) where.putIfAbsent(e[1], e[0]);
        log("main harvested " + parts.size() + ": " + where);
        // 영역 창의 항목 (도구 막대 창이 맨 처음이라, 예전에는 여기서 멈춰 아무것도 모으지 못했다)
        assertTrue(where.get("도구 막대 표시").startsWith("keyboard:a:0:"));
        assertTrue(where.get("숫자 줄 표시").startsWith("keyboard:a:1:"));
        assertEquals("keyboard:t:0:한글 글자 키", where.get("쌍자음 입력"));
        assertEquals("keyboard:t:1:영어 글자 키", where.get("영어 자동 대문자"));
        assertTrue(where.get("기능키 높이").startsWith("keyboard:a:3:"));
        assertTrue(where.get("왼쪽 여백") != null);
        assertTrue(where.get("두 번 눌러 마침표").startsWith("keyboard:k:"));
        // 창 자체도 찾는다
        assertEquals("keyboard:a:0:도구 막대", where.get("도구 막대 설정"));
        assertEquals("keyboard:t:0:한글 글자 키", where.get("한글 글자 키 설정"));
        assertEquals("keyboard:b:0:키보드 전체 설정", where.get("키보드 전체 설정"));
        String space = null, comma = null, tool = null;
        for (String[] e : parts) {
            if (space == null && e[0].startsWith("keyboard:k:" + Key.SPACE + ":") && e[1].endsWith(" 설정")) space = e[0];
            // 쉼표 키는 기본으로 꺼져 있어 자판에 없으므로 모으지 않는다. 온점 키는 기본으로 있다.
            if (comma == null && e[0].startsWith("keyboard:c:.:")) comma = e[0];
            if (tool == null && e[0].startsWith("keyboard:o:")) tool = e[0];
        }
        assertNotNull(space);
        assertNotNull(comma);
        assertNotNull(tool);
        // 메인 화면의 값은 그대로 (임시로 만든 칸을 되돌렸다)
        java.lang.reflect.Field kp = SettingsActivity.class.getDeclaredField("keyPanel");
        kp.setAccessible(true);
        assertNull(kp.get(main));

        // 검색 결과를 고른 것처럼 키보드 사용자화를 열면 그 창이 열린다.
        SettingsActivity a = launch("keyboard");
        java.lang.reflect.Method ot = SettingsActivity.class.getDeclaredMethod("openEditorTarget", String.class, String.class);
        ot.setAccessible(true);
        ot.invoke(a, comma, comma.substring(comma.lastIndexOf(':') + 1) + " 설정");
        shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(800));
        assertTrue(sheetOf(a).isOpen());
        assertTrue(visibleTexts(cur(a)).contains("입력할 글자"));
        a.onBackPressed(); idle();
        ot.invoke(a, where.get("숫자 줄 표시"), "숫자 줄 표시");
        shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(800));
        assertTrue(sheetOf(a).isOpen());
        assertTrue(visibleTexts(cur(a)).contains("숫자 줄 표시"));
        a.onBackPressed(); idle();
        ot.invoke(a, tool, tool.substring(tool.lastIndexOf(':') + 1) + " 설정");
        shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(800));
        assertTrue(sheetOf(a).isOpen());
        a.onBackPressed(); idle();
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
        // 선택 버튼은 폭이 좁으면 이름을 두 줄로 나눠 보이므로 ("NewSwipe\n단모음") 줄바꿈은 빈칸으로 본다.
        if (v instanceof TextView && v.isShown()
                && text.equals(((TextView) v).getText().toString().replace('\n', ' '))) return v;
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
