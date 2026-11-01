package com.alternative_studios.newswipe;

import static org.junit.Assert.assertArrayEquals;

import android.content.SharedPreferences;

import org.junit.Test;

import java.lang.reflect.Constructor;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/** '언어 순서 사용자화'의 저장값: 네 언어가 한 번씩, 모르는 값·빠진 언어는 기본 차례로 채운다. */
public class LanguageOrderTest {
    private static final class FakeSp implements SharedPreferences {
        final Map<String, Object> m = new HashMap<>();
        @Override public Map<String, ?> getAll() { return m; }
        @Override public String getString(String k, String d) { Object o = m.get(k); return o == null ? d : (String) o; }
        @Override public Set<String> getStringSet(String k, Set<String> d) { return d; }
        @Override public int getInt(String k, int d) { return d; }
        @Override public long getLong(String k, long d) { return d; }
        @Override public float getFloat(String k, float d) { return d; }
        @Override public boolean getBoolean(String k, boolean d) { return d; }
        @Override public boolean contains(String k) { return m.containsKey(k); }
        @Override public Editor edit() { throw new UnsupportedOperationException(); }
        @Override public void registerOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener l) { }
        @Override public void unregisterOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener l) { }
    }

    private static String[] order(String saved) throws Exception {
        FakeSp sp = new FakeSp();
        if (saved != null) sp.m.put(Prefs.LANGUAGE_ORDER, saved);
        Constructor<Prefs> c = Prefs.class.getDeclaredConstructor(SharedPreferences.class, boolean.class, String.class,
                boolean.class);
        c.setAccessible(true);
        return c.newInstance(sp, false, Prefs.PROFILE_PORTRAIT, false).languageOrder();
    }

    @Test
    public void parsesAndFillsMissing() throws Exception {
        assertArrayEquals(new String[]{"ko", "en", "ja", "zh"}, order(null));
        assertArrayEquals(new String[]{"zh", "en", "ko", "ja"}, order("zh,en,ko,ja"));
        assertArrayEquals(new String[]{"en", "ko", "ja", "zh"}, order("en,xx,en,ko"));   // 모르는 값·중복은 버린다
    }
}
