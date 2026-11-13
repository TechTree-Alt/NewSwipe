package com.alternative_studios.newswipe;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.SharedPreferences;

import org.junit.Test;

import java.lang.reflect.Constructor;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** 입력 언어(한국어·영어 켜기)와 화면 언어(L). */
public class InputLanguagesTest {
    private static final class FakeSp implements SharedPreferences {
        final Map<String, Object> m = new HashMap<>();
        @Override public Map<String, ?> getAll() { return m; }
        @Override public String getString(String k, String d) { Object o = m.get(k); return o == null ? d : (String) o; }
        @Override public Set<String> getStringSet(String k, Set<String> d) { return d; }
        @Override public int getInt(String k, int d) { return d; }
        @Override public long getLong(String k, long d) { return d; }
        @Override public float getFloat(String k, float d) { return d; }
        @Override public boolean getBoolean(String k, boolean d) { Object o = m.get(k); return o == null ? d : (Boolean) o; }
        @Override public boolean contains(String k) { return m.containsKey(k); }
        @Override public Editor edit() { throw new UnsupportedOperationException(); }
        @Override public void registerOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener l) { }
        @Override public void unregisterOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener l) { }
    }

    private static Prefs prefs(FakeSp sp) throws Exception {
        Constructor<Prefs> c = Prefs.class.getDeclaredConstructor(SharedPreferences.class, boolean.class, String.class,
                boolean.class);
        c.setAccessible(true);
        return c.newInstance(sp, false, Prefs.PROFILE_PORTRAIT, false);
    }

    @Test
    public void bothLanguagesOnByDefault() throws Exception {
        Prefs p = prefs(new FakeSp());
        assertTrue(p.inputKorean());
        assertTrue(p.inputEnglish());
        assertFalse(p.singleLanguage());
    }

    @Test
    public void englishOnlyIsASingleLanguage() throws Exception {
        FakeSp sp = new FakeSp();
        sp.m.put(Prefs.INPUT_KO, false);
        Prefs p = prefs(sp);
        assertFalse(p.languageOn("ko"));
        assertTrue(p.languageOn("en"));
        assertTrue(p.singleLanguage());
        // 실험실에서 일본어를 켜면 언어가 둘이 된다.
        sp.m.put(Prefs.CJK_PREFIX + "ja", true);
        assertFalse(p.singleLanguage());
    }

    @Test
    public void bothOffMeansBothOn() throws Exception {
        // 잘못 가져온 설정 등으로 둘 다 꺼져 있어도 입력할 언어가 없어지지 않는다.
        FakeSp sp = new FakeSp();
        sp.m.put(Prefs.INPUT_KO, false);
        sp.m.put(Prefs.INPUT_EN, false);
        Prefs p = prefs(sp);
        assertTrue(p.inputKorean());
        assertTrue(p.inputEnglish());
    }

    @Test
    public void everyGuideHasAnEnglishVersion() {
        // 영어 화면은 assets/guide/en/의 같은 이름 파일을 연다 (SettingsActivity.buildGuide).
        java.io.File dir = new java.io.File("src/main/assets/guide");
        if (!dir.isDirectory()) dir = new java.io.File("app/src/main/assets/guide");
        java.io.File[] files = dir.listFiles((d, n) -> n.endsWith(".txt"));
        assertTrue(files != null && files.length > 0);
        for (java.io.File f : files) assertTrue(f.getName(), new java.io.File(dir, "en/" + f.getName()).isFile());
    }

    @Test
    public void textFollowsTheDefaultLocale() {
        Locale before = Locale.getDefault();
        try {
            Locale.setDefault(Locale.KOREA);
            assertTrue(L.ko());
            assertEquals("설정", L.t("설정", "Settings"));
            Locale.setDefault(Locale.US);
            assertEquals("Settings", L.t("설정", "Settings"));
            Locale.setDefault(Locale.JAPAN);   // 한국어가 아닌 언어는 모두 영어로
            assertEquals("Settings", L.t("설정", "Settings"));
        } finally {
            Locale.setDefault(before);
        }
    }
}
