package com.alternative_studios.newswipe;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.SharedPreferences;

import org.junit.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class SafePrefsTest {
    /** 값을 그대로 돌려주되, SharedPreferences처럼 타입이 다르면 ClassCastException을 던지는 가짜. */
    private static final class Fake implements SharedPreferences {
        final Map<String, Object> m = new HashMap<>();

        @SuppressWarnings("unchecked")
        private <T> T get(String key, T def) {
            return m.containsKey(key) ? (T) m.get(key) : def;
        }

        @Override public Map<String, ?> getAll() { return m; }
        @Override public String getString(String k, String d) { return (String) get(k, d); }
        @Override public Set<String> getStringSet(String k, Set<String> d) { return get(k, d); }
        @Override public int getInt(String k, int d) { return (Integer) get(k, d); }
        @Override public long getLong(String k, long d) { return (Long) get(k, d); }
        @Override public float getFloat(String k, float d) { return (Float) get(k, d); }
        @Override public boolean getBoolean(String k, boolean d) { return (Boolean) get(k, d); }
        @Override public boolean contains(String k) { return m.containsKey(k); }
        @Override public Editor edit() { return null; }
        @Override public void registerOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener l) { }
        @Override public void unregisterOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener l) { }
    }

    @Test
    public void wrongTypeFallsBackToDefault() {
        Fake fake = new Fake();
        fake.m.put("int", "text");
        fake.m.put("bool", 7);
        fake.m.put("str", 3);
        fake.m.put("long", "x");
        fake.m.put("float", true);
        SafePrefs sp = new SafePrefs(fake);
        assertEquals(100, sp.getInt("int", 100));
        assertTrue(sp.getBoolean("bool", true));
        assertEquals("d", sp.getString("str", "d"));
        assertEquals(5L, sp.getLong("long", 5L));
        assertEquals(1.5f, sp.getFloat("float", 1.5f), 0f);
    }

    @Test
    public void correctTypeAndMissingKeyAreUnchanged() {
        Fake fake = new Fake();
        fake.m.put("int", 42);
        fake.m.put("bool", true);
        SafePrefs sp = new SafePrefs(fake);
        assertEquals(42, sp.getInt("int", 0));
        assertTrue(sp.getBoolean("bool", false));
        assertEquals(9, sp.getInt("missing", 9));
        assertFalse(sp.contains("missing"));
        assertTrue(sp.contains("int"));
    }
}
