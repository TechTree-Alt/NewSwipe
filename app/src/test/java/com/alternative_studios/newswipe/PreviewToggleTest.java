package com.alternative_studios.newswipe;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.view.View;

import com.alternative_studios.newswipe.ui.ExpressiveButton;
import com.alternative_studios.newswipe.ui.Ui;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

import static org.junit.Assert.*;

/**
 * 키보드 사용자화 화면 미리보기 위의 알약 버튼 ('미리보기 닫기'·'미리보기 열기'): 강조색 면과 꺾쇠가 실제로 그려지고,
 * 누르면 미리보기가 접히고 펼쳐지는지 본다. (가운데 정렬 TextView가 캔버스를 스크롤해 면·꺾쇠가 밖으로 밀렸던 일을 막는다.)
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 36, qualifiers = "ko-rKR-w411dp-h914dp-port-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class PreviewToggleTest {
    private static Object field(Object o, String name) throws Exception {
        java.lang.reflect.Field f = SettingsActivity.class.getDeclaredField(name);
        f.setAccessible(true);
        return f.get(o);
    }

    private static Bitmap draw(View v) {
        Bitmap b = Bitmap.createBitmap(v.getWidth(), v.getHeight(), Bitmap.Config.ARGB_8888);
        v.draw(new Canvas(b));
        return b;
    }

    private static boolean near(int a, int b) {
        return Math.abs(Color.red(a) - Color.red(b)) < 12 && Math.abs(Color.green(a) - Color.green(b)) < 12
                && Math.abs(Color.blue(a) - Color.blue(b)) < 12;
    }

    @Test
    public void pillDrawsAccentFaceAndChevronAndToggles() throws Exception {
        SettingsActivity a = EditorUiTest.launch("keyboard");
        EditorUiTest.idle();
        ExpressiveButton b = (ExpressiveButton) field(a, "previewToggle");
        assertNotNull(b);
        assertEquals(0, b.getScrollX());
        assertTrue(b.getWidth() > 0 && b.getHeight() > 0);
        assertEquals("미리보기 닫기", b.getText().toString());
        AppTheme t = AppTheme.of(a);
        Bitmap bmp = draw(b);
        int midY = bmp.getHeight() / 2;
        // 오른쪽 끝 안쪽 (글자 뒤 여백): 강조색 면
        int edge = bmp.getPixel(bmp.getWidth() - Ui.dp(a, 6), midY);
        assertTrue("face " + Integer.toHexString(edge), near(edge, t.accent));
        // 왼쪽 아이콘 자리: 강조색 면 위에 글자색(꺾쇠) 픽셀이 있다
        int from = b.getPaddingLeft() - Ui.dp(a, 6) - Ui.dp(a, 18), to = b.getPaddingLeft() - Ui.dp(a, 6);
        boolean ink = false;
        for (int x = Math.max(0, from); x < to && !ink; x++) {
            for (int y = 0; y < bmp.getHeight() && !ink; y++) {
                if (near(bmp.getPixel(x, y), t.onAccent) && !near(t.onAccent, t.accent)) ink = true;
            }
        }
        assertTrue("chevron drawn", ink);

        View bottom = (View) field(a, "pinnedBottom");
        assertEquals(View.VISIBLE, bottom.getVisibility());
        b.performClick();
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(800));
        assertEquals(View.GONE, bottom.getVisibility());
        assertEquals("미리보기 열기", b.getText().toString());
        b.performClick();
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(800));
        assertEquals(View.VISIBLE, bottom.getVisibility());
        assertEquals("미리보기 닫기", b.getText().toString());
    }
}
