package com.alternative_studios.newswipe;

import android.view.View;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

import java.util.List;

import static org.junit.Assert.assertTrue;

/** 설정 검색 목록: 섹션 이름(소제목)은 넣지 않고, 소제목에 붙어 있던 낱말로도 그 화면을 찾는다. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 36, qualifiers = "ko-rKR-w411dp-h914dp-port-xxhdpi")
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

}
