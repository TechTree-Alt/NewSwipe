package com.alternative_studios.newswipe;

import android.os.Looper;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

import static com.alternative_studios.newswipe.EditorUiTest.*;
import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

/**
 * 키보드 사용자화에서 화면 폭이 500dp 이상이고 미리보기가 커서(바형 폰의 가로 키보드 등) 설정 창을 보통처럼 띄우면 보이는
 * 높이가 250dp보다 낮을 때, 설정 창을 화면 왼쪽 절반에 띄우고 미리보기를 오른쪽 절반으로 줄이며, 창을 닫으면 실제 크기로
 * 되돌리는지 확인한다.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 36, qualifiers = "ko-rKR-w411dp-h914dp-port-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class PreviewSplitUiTest {

    private static <T> T field(Object o, String name) throws Exception {
        java.lang.reflect.Field f = SettingsActivity.class.getDeclaredField(name);
        f.setAccessible(true);
        @SuppressWarnings("unchecked") T t = (T) f.get(o);
        return t;
    }

    private static void settle() {
        shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(700));
        idle();
    }

    /** 화면(창) 좌표로 누른다: 줄여 놓은 미리보기도 뷰 틀이 좌표를 바꿔 넘겨야 한다. */
    private static void tapScreen(View decor, float x, float y) {
        long t = SystemClock.uptimeMillis();
        MotionEvent down = MotionEvent.obtain(t, t, MotionEvent.ACTION_DOWN, x, y, 0);
        decor.dispatchTouchEvent(down);
        down.recycle();
        MotionEvent up = MotionEvent.obtain(t, t + 50, MotionEvent.ACTION_UP, x, y, 0);
        decor.dispatchTouchEvent(up);
        up.recycle();
        idle();
    }

    @Test
    @Config(qualifiers = "ko-rKR-w800dp-h360dp-land-xxhdpi")
    public void tallPreviewSplitsScreenWhileSheetIsOpen() throws Exception {
        Prefs pre = new Prefs(RuntimeEnvironment.getApplication());
        pre.raw().edit().putInt(Prefs.KEYBOARD_HEIGHT_LAND, 260).commit();
        try {
            SettingsActivity a = launch("keyboard");
            View decor = a.getWindow().getDecorView();
            LinearLayout bottom = field(a, "pinnedBottom");
            FrameLayout full = field(a, "fullHost"), overlay = field(a, "overlayHost");
            settle();
            int screen = full.getHeight() - full.getPaddingTop() - full.getPaddingBottom();
            int h = bottom.getHeight();
            log("preview " + h + " / screen " + screen);
            KeyboardPreview p = find(decor, KeyboardPreview.class);

            tapArea(p, KeyboardPreview.AREA_LETTERS);
            settle();
            EditorSheet sheet = sheetOf(a);
            assertTrue(sheet.isOpen());
            assertTrue(describe(a), sheet.isSplit());
            // 창: 화면 전체 틀의 왼쪽 절반, 아래 끝까지
            View sv = sheet.view();
            assertSame(full, sv.getParent());
            int contentW = full.getWidth() - full.getPaddingLeft() - full.getPaddingRight();
            assertEquals(contentW / 2, sv.getWidth());
            assertEquals(full.getPaddingLeft(), sv.getLeft());
            assertTrue(sv.getBottom() >= full.getHeight() - full.getPaddingBottom());
            // 미리보기: 오른쪽 아래 모서리에 붙어 절반으로 줄고, 줄어든 만큼 위의 목록이 자란다
            h = bottom.getHeight();   // 영역을 고르면 미리보기가 조금 커질 수 있다 (줄인 자리는 따라 맞춘다)
            assertEquals(0.5f, bottom.getScaleX(), 0.001f);
            assertEquals(0.5f, bottom.getScaleY(), 0.001f);
            assertEquals(bottom.getWidth(), bottom.getPivotX(), 0.5f);
            assertEquals(h, bottom.getPivotY(), 0.5f);
            assertEquals(-Math.round(h * 0.5f), ((LinearLayout.LayoutParams) bottom.getLayoutParams()).topMargin);
            assertEquals(screen - Math.round(h * 0.5f), overlay.getHeight(), 1);
            View toggleHolder = field(a, "previewToggleHolder");
            assertEquals(View.INVISIBLE, toggleHolder.getVisibility());
            shot(a, "split-open");

            // 줄인 미리보기를 화면 좌표로 눌러도 그 영역을 고른다
            android.graphics.RectF r = new android.graphics.RectF();
            assertTrue(p.areaBounds(KeyboardPreview.AREA_FUNCTION, r));
            View ov = p.getChildAt(1);
            // 손잡이 층의 왼쪽 위 모서리(창 좌표, 변환 포함)에서 줄인 배율만큼 떨어진 곳
            int[] loc = new int[2];
            ov.getLocationInWindow(loc);
            float sc = bottom.getScaleX();
            float[] pt = {loc[0] + (r.centerX() + r.width() / 5f) * sc, loc[1] + r.centerY() * sc};
            log("tap at " + pt[0] + "," + pt[1]);
            tapScreen(decor, pt[0], pt[1]);
            assertNotNull(p.selection());
            assertEquals(KeyboardPreview.AREA_FUNCTION, p.selection().area());
            assertTrue(sheet.isSplit());

            // 닫으면 실제 크기로 돌아가고 창은 제자리(목록 위)로
            a.onBackPressed();
            settle();
            assertFalse(sheet.isOpen());
            assertFalse(sheet.isSplit());
            assertSame(overlay, sv.getParent());
            assertEquals(1f, bottom.getScaleX(), 0.001f);
            assertEquals(0, ((LinearLayout.LayoutParams) bottom.getLayoutParams()).topMargin);
            assertEquals(View.VISIBLE, toggleHolder.getVisibility());
            assertEquals(1f, toggleHolder.getAlpha(), 0.001f);
            shot(a, "split-closed");
        } finally {
            pre.raw().edit().remove(Prefs.KEYBOARD_HEIGHT_LAND).commit();
        }
    }

    /** 실패했을 때 알아보기 쉽게: 화면 폭·높이와 미리보기 높이(dp). */
    private static String describe(SettingsActivity a) throws Exception {
        LinearLayout bottom = field(a, "pinnedBottom");
        FrameLayout full = field(a, "fullHost");
        float d = a.getResources().getDisplayMetrics().density;
        return "screen " + (full.getWidth() - full.getPaddingLeft() - full.getPaddingRight()) / d
                + "x" + (full.getHeight() - full.getPaddingTop() - full.getPaddingBottom()) / d
                + " preview " + bottom.getHeight() / d;
    }

    @Test
    public void shortPreviewKeepsSheetAboveIt() throws Exception {
        SettingsActivity a = launch("keyboard");
        View decor = a.getWindow().getDecorView();
        LinearLayout bottom = field(a, "pinnedBottom");
        FrameLayout overlay = field(a, "overlayHost");
        settle();
        KeyboardPreview p = find(decor, KeyboardPreview.class);
        tapArea(p, KeyboardPreview.AREA_LETTERS);
        settle();
        EditorSheet sheet = sheetOf(a);
        assertTrue(sheet.isOpen());
        assertFalse(describe(a), sheet.isSplit());
        assertSame(overlay, sheet.view().getParent());
        assertEquals(1f, bottom.getScaleX(), 0.001f);
        a.onBackPressed();
        settle();
        assertFalse(sheet.isOpen());
    }

    /** 화면 폭이 500dp보다 좁으면 미리보기가 커도 나누지 않는다 (반으로 나눈 설정 창이 너무 좁아서). */
    @Test
    @Config(qualifiers = "ko-rKR-w480dp-h360dp-land-xxhdpi")
    public void narrowScreenKeepsSheetAboveTallPreview() throws Exception {
        Prefs pre = new Prefs(RuntimeEnvironment.getApplication());
        pre.raw().edit().putInt(Prefs.KEYBOARD_HEIGHT_LAND, 140).commit();
        try {
            SettingsActivity a = launch("keyboard");
            View decor = a.getWindow().getDecorView();
            LinearLayout bottom = field(a, "pinnedBottom");
            FrameLayout overlay = field(a, "overlayHost");
            settle();
            KeyboardPreview p = find(decor, KeyboardPreview.class);
            tapArea(p, KeyboardPreview.AREA_LETTERS);
            settle();
            EditorSheet sheet = sheetOf(a);
            assertTrue(sheet.isOpen());
            assertFalse(describe(a), sheet.isSplit());
            assertSame(overlay, sheet.view().getParent());
            assertEquals(1f, bottom.getScaleX(), 0.001f);
        } finally {
            pre.raw().edit().remove(Prefs.KEYBOARD_HEIGHT_LAND).commit();
        }
    }
}
