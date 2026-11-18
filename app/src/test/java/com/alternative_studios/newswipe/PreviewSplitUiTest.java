package com.alternative_studios.newswipe;

import android.os.Looper;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

import com.alternative_studios.newswipe.ui.Ui;

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
 * 키보드 사용자화에서 미리보기가 커서(바형 폰의 가로 키보드 등) 설정 창을 보통처럼 띄우면 항목이 2개도 온전히 보이지 않을 때
 * 설정 창을 화면 왼쪽 절반에 띄우고 미리보기를 오른쪽 절반으로 줄이며, 창을 닫으면 실제 크기로 되돌리는지 확인한다.
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
            assertTrue(sheet.isSplit());
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

    /** 실패했을 때 알아보기 쉽게: 화면·미리보기 높이와 창 속 항목들의 위치(dp). */
    private static String describe(SettingsActivity a, EditorSheet sheet) throws Exception {
        LinearLayout bottom = field(a, "pinnedBottom");
        FrameLayout full = field(a, "fullHost");
        float d = a.getResources().getDisplayMetrics().density;
        StringBuilder b = new StringBuilder();
        b.append("screen ").append((full.getHeight() - full.getPaddingTop() - full.getPaddingBottom()) / d)
                .append(" preview ").append(bottom.getHeight() / d).append(" items:");
        java.util.Map<android.widget.TextView, java.lang.ref.WeakReference<View>> rows = field(a, "itemRow");
        for (java.util.Map.Entry<android.widget.TextView, java.lang.ref.WeakReference<View>> e : rows.entrySet()) {
            View v = e.getValue().get();
            if (v == null) continue;
            int y = v.getTop();
            android.view.ViewParent q = v.getParent();
            boolean shown = v.getVisibility() == View.VISIBLE;
            while (q instanceof View && q != sheet.body()) {
                shown &= ((View) q).getVisibility() == View.VISIBLE;
                y += ((View) q).getTop();
                q = q.getParent();
            }
            if (q != sheet.body()) continue;
            b.append(" [").append(e.getKey().getText()).append(' ').append(y / d).append('+').append(v.getHeight() / d)
                    .append(shown ? "" : " hidden").append(']');
        }
        return b.toString();
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
        assertFalse(describe(a, sheet), sheet.isSplit());
        assertSame(overlay, sheet.view().getParent());
        assertEquals(1f, bottom.getScaleX(), 0.001f);
        a.onBackPressed();
        settle();
        assertFalse(sheet.isOpen());
    }

    /**
     * 창이 항목 2개를 온전히 보이려면 필요한 높이: 틈 + 헤더 + 내용 위부터 둘째 항목의 아래 끝까지. 항목은 카드 안의 줄(설명 글,
     * 선택 버튼 줄 등 표시 없는 것도)과 표시한 항목 줄이다. 카드 밖의 소제목, 구분선, 숨긴 줄은 세지 않고 바탕 없는 세로 틀과
     * 하위 설정 묶음은 그 안을 센다. 항목이 모자라면 마지막 항목까지 잰다.
     */
    @Test
    public void heightToShowCountsVisibleItemsInsideCards() {
        SettingsActivity a = launch("keyboard");
        FrameLayout host = new FrameLayout(a);
        EditorSheet sheet = new EditorSheet(a, host, () -> { });
        LinearLayout body = sheet.body();
        SettingsWidgets w = new SettingsWidgets(a, AppTheme.of(a), null);
        int u = Ui.dp(a, 10);
        int width = Ui.dp(a, 400);
        java.util.Set<View> none = java.util.Collections.emptySet();

        body.addView(fixed(new android.widget.TextView(a), 3 * u));   // 카드 밖의 소제목: 항목이 아니다
        LinearLayout card = w.card(body);                               // 카드: 위 여백 8dp, 안쪽 위 8dp
        card.addView(fixed(new View(a), u));                            // 구분선: 항목이 아니다
        View hidden = fixed(new android.widget.TextView(a), 5 * u);
        hidden.setVisibility(View.GONE);
        card.addView(hidden);
        LinearLayout wrapper = new LinearLayout(a);                     // 바탕 없는 세로 틀: 그 안을 센다
        wrapper.setOrientation(LinearLayout.VERTICAL);
        View first = fixed(new android.widget.TextView(a), 4 * u);
        wrapper.addView(first);
        card.addView(wrapper);
        View second = fixed(new LinearLayout(a), 6 * u);                // 가로 줄 (선택 버튼 줄 등)
        card.addView(second);
        View third = fixed(new android.widget.TextView(a), 2 * u);
        card.addView(w.subGroup(third));

        int above = 3 * u + Ui.dp(a, 8) + Ui.dp(a, 8) + u;               // 소제목 + 카드 위 여백 + 카드 안쪽 + 구분선
        int head = sheet.heightToShow(none, 1, width) - (above + 4 * u);  // 틈 + 헤더
        assertEquals(head + above + 4 * u + 6 * u, sheet.heightToShow(none, 2, width));
        assertEquals(head + above + 4 * u + 6 * u + 2 * u, sheet.heightToShow(none, 3, width));
        assertEquals(head + above + 4 * u + 6 * u + 2 * u, sheet.heightToShow(none, 9, width));

        // 표시한 항목 줄은 카드 밖에 있어도 세고, 그 안은 따로 살피지 않는다.
        LinearLayout outside = new LinearLayout(a);
        outside.setOrientation(LinearLayout.VERTICAL);
        body.addView(outside, 0);
        outside.addView(fixed(new android.widget.TextView(a), u));
        outside.addView(fixed(new android.widget.TextView(a), u));
        java.util.Set<View> marked = java.util.Collections.singleton(outside);
        assertEquals(head + 2 * u, sheet.heightToShow(marked, 1, width));
        assertEquals(head + 2 * u + above + 4 * u, sheet.heightToShow(marked, 2, width));
    }

    private static View fixed(View v, int h) {
        v.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, h));
        return v;
    }
}
