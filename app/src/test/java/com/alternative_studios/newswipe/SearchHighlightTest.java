package com.alternative_studios.newswipe;

import android.content.Intent;
import android.os.Looper;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.robolectric.Shadows.shadowOf;

/**
 * 설정 검색의 모든 항목을 실제로 눌러 들어가 보고, 깜빡인 칸이 그 항목 전체(이름만이 아니라 값·스위치·슬라이더·버튼 바탕까지)이고
 * 다른 항목까지 덮지 않는지 확인한다. -Dshots=폴더를 주면 깜빡이는 순간을 찍어 남긴다.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 36, qualifiers = "w411dp-h914dp-port-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class SearchHighlightTest {

    private static Object field(Object o, String name) throws Exception {
        java.lang.reflect.Field f = SettingsActivity.class.getDeclaredField(name);
        f.setAccessible(true);
        return f.get(o);
    }

    private static Object call(Object o, String name, Class<?>[] types, Object... args) throws Exception {
        java.lang.reflect.Method m = SettingsActivity.class.getDeclaredMethod(name, types);
        m.setAccessible(true);
        return m.invoke(o, args);
    }

    private static float sp(View v, float sp) {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, sp, v.getResources().getDisplayMetrics());
    }

    /** 검색 목록이 모으는 기준과 같은 '항목 이름' 글자. */
    private static boolean isLabel(TextView t) {
        float px = t.getTextSize();
        return !t.getText().toString().trim().isEmpty() && px >= sp(t, 15) - 0.5f && px <= sp(t, 18) + 0.5f;
    }

    private static void labels(View v, List<TextView> out) {
        if (v.getVisibility() != View.VISIBLE) return;
        if (v instanceof TextView && isLabel((TextView) v)) out.add((TextView) v);
        if (v instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) labels(g.getChildAt(i), out);
        }
    }

    private static boolean contains(View outer, View inner) {
        for (View v = inner; v != null; v = v.getParent() instanceof View ? (View) v.getParent() : null) {
            if (v == outer) return true;
        }
        return false;
    }

    private static TextView findText(View v, String text) {
        if (v instanceof TextView && v.isShown() && text.contentEquals(((TextView) v).getText())) return (TextView) v;
        if (v instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) {
                TextView t = findText(g.getChildAt(i), text);
                if (t != null) return t;
            }
        }
        return null;
    }

    /** 섹션 이름(소제목)은 검색되지 않고, 소제목에 붙어 있던 낱말(다크 등)은 그 화면으로 찾아진다. */
    @SuppressWarnings("unchecked")
    @Test
    public void sectionNamesAreNotSearched() throws Exception {
        SettingsActivity main = EditorUiTest.launch("app");
        call(main, "ensureSearchIndex", new Class<?>[0]);
        for (int i = 0; i < 200 && field(main, "searchEntries") == null; i++) EditorUiTest.idle();
        List<String[]> entries = (List<String[]>) field(main, "searchEntries");
        java.util.Set<String> names = new java.util.HashSet<>();
        for (String[] e : entries) names.add(e[1]);
        for (String heading : new String[]{"모양", "밀기", "여백", "입력", "화면 모드", "강조 색", "키 색",
                "개인 정보", "자동 완성", "클립보드", "이모지", "크기와 위치", "폴더블 설정"}) {
            assertTrue("소제목 '" + heading + "'이 검색 목록에 있다", !names.contains(heading));
        }
        assertTrue(names.contains("도구 막대 높이"));   // 항목은 그대로 찾는다

        new Prefs(main).raw().edit().putBoolean(Prefs.ONBOARDING_DONE, true).commit();
        SettingsActivity home = EditorUiTest.launch(null);
        call(home, "ensureSearchIndex", new Class<?>[0]);
        for (int i = 0; i < 200 && field(home, "searchEntries") == null; i++) EditorUiTest.idle();
        call(home, "onSearchQuery", new Class<?>[]{String.class}, "다크");
        String shown = EditorUiTest.visibleTexts((View) field(home, "searchResults"));
        assertTrue("'다크'로 테마 화면을 찾지 못했다:\n" + shown, shown.contains("테마"));
        assertTrue("같이 찾을 낱말이 설명에 보인다:\n" + shown, !shown.contains("야간"));
    }

    @SuppressWarnings("unchecked")
    @Test
    public void everySearchResultFlashesWholeItem() throws Exception {
        SettingsActivity main = EditorUiTest.launch("app");   // 검색 목록을 모으고 결과를 여는 데만 쓴다
        call(main, "ensureSearchIndex", new Class<?>[0]);
        for (int i = 0; i < 200 && field(main, "searchEntries") == null; i++) EditorUiTest.idle();
        List<String[]> entries = (List<String[]>) field(main, "searchEntries");
        assertTrue("검색 목록을 모으지 못했다", entries != null && entries.size() > 50);

        List<String> problems = new ArrayList<>();
        int checked = 0;
        for (String[] e : entries) {
            String id = e[0], label = e[1];
            call(main, "openSection", new Class<?>[]{String.class, String.class}, id, label);
            Intent i = shadowOf(main).getNextStartedActivity();
            if (i == null) continue;
            SettingsActivity a;
            try {
                a = Robolectric.buildActivity(SettingsActivity.class, i).setup().get();
            } catch (RuntimeException ex) {
                problems.add(id + " | " + label + " | 화면을 못 열었다: " + ex);
                continue;
            }
            for (int s = 0; s < 40 && a.lastFlashed == null; s++) {
                shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(50));
            }
            View box = a.lastFlashed;
            boolean panelOnly = id.startsWith("keyboard:") || id.startsWith("profilepart:");
            String[] parts = id.split(":", 4);
            boolean sheetItself = panelOnly && parts.length == 4 && (label.equals(parts[3]) || label.equals(parts[3] + " 설정"));
            if (box == null) {
                if (!sheetItself) problems.add(id + " | " + label + " | 깜빡이지 않았다");
                a.finish();
                continue;
            }
            checked++;
            View root = a.getWindow().getDecorView();
            TextView target = findText(root, label);
            List<TextView> inBox = new ArrayList<>();
            labels(box, inBox);
            String kind = box.getClass().getSimpleName() + " " + box.getWidth() + "x" + box.getHeight();
            // 소제목은 글자 한 줄이 곧 항목이다 (화면 폭 전체에 걸친 글자).
            boolean heading = box instanceof TextView && !isLabel((TextView) box);
            if (!heading && box instanceof TextView && box.getParent() instanceof ViewGroup
                    && ((ViewGroup) box.getParent()).getChildCount() > 1) {
                problems.add(id + " | " + label + " | 이름 글자만 깜빡였다 (" + kind + ")");
            } else if (target != null && contains(box, target) && inBox.size() > 2) {
                StringBuilder names = new StringBuilder();
                for (TextView t : inBox) names.append('[').append(t.getText()).append(']');
                problems.add(id + " | " + label + " | 다른 항목까지 덮었다 " + names + " (" + kind + ")");
            }
            if (EditorUiTest.OUT != null) {
                shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(450));
                android.graphics.Bitmap b = android.graphics.Bitmap.createBitmap(root.getWidth(), root.getHeight(),
                        android.graphics.Bitmap.Config.ARGB_8888);
                android.graphics.Canvas cv = new android.graphics.Canvas(b);
                root.draw(cv);
                // 시험 환경에서는 애니메이션이 곧바로 끝나므로 깜빡인 칸의 자리를 직접 칠해 보인다.
                int[] at = new int[2];
                box.getLocationInWindow(at);
                android.graphics.Paint paint = new android.graphics.Paint();
                paint.setColor(0x66FF8800);
                cv.drawRect(at[0], at[1], at[0] + box.getWidth(), at[1] + box.getHeight(), paint);
                EditorUiTest.save(b, String.format("%03d", checked) + "-" + label.replaceAll("[^0-9A-Za-z가-힣]+", "_"));
                System.out.println(String.format("%03d", checked) + " " + id + " | " + label + " -> " + kind
                        + (target != null && contains(box, target) ? "" : " (접힌 항목 → 위 설정)"));
            }
            a.finish();
            EditorUiTest.idle();
        }
        System.out.println("검사한 항목 " + checked + " / " + entries.size());
        for (String p : problems) System.out.println("PROBLEM " + p);
        if (!problems.isEmpty()) fail(problems.size() + "개 항목의 강조가 이상하다:\n" + String.join("\n", problems));
    }
}
