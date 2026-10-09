package com.alternative_studios.newswipe;

import android.content.SharedPreferences;

import java.util.Map;
import java.util.Set;

/**
 * 설정을 읽을 때 저장된 값의 타입이 기대와 다르면 (손으로 고친 백업 파일을 가져온 경우 등) 앱이 죽지 않고 기본값을 돌려준다.
 * SharedPreferences는 타입이 다른 값을 읽으면 ClassCastException을 던지는데, 키보드가 읽다가 죽으면 계속 다시 죽기 때문이다.
 * 쓰기와 리스너는 그대로 넘긴다.
 */
final class SafePrefs implements SharedPreferences {
    private final SharedPreferences base;

    SafePrefs(SharedPreferences base) {
        this.base = base;
    }

    @Override public Map<String, ?> getAll() { return base.getAll(); }

    @Override public String getString(String key, String defValue) {
        try {
            return base.getString(key, defValue);
        } catch (ClassCastException e) {
            return defValue;
        }
    }

    @Override public Set<String> getStringSet(String key, Set<String> defValues) {
        try {
            return base.getStringSet(key, defValues);
        } catch (ClassCastException e) {
            return defValues;
        }
    }

    @Override public int getInt(String key, int defValue) {
        try {
            return base.getInt(key, defValue);
        } catch (ClassCastException e) {
            return defValue;
        }
    }

    @Override public long getLong(String key, long defValue) {
        try {
            return base.getLong(key, defValue);
        } catch (ClassCastException e) {
            return defValue;
        }
    }

    @Override public float getFloat(String key, float defValue) {
        try {
            return base.getFloat(key, defValue);
        } catch (ClassCastException e) {
            return defValue;
        }
    }

    @Override public boolean getBoolean(String key, boolean defValue) {
        try {
            return base.getBoolean(key, defValue);
        } catch (ClassCastException e) {
            return defValue;
        }
    }

    @Override public boolean contains(String key) { return base.contains(key); }

    @Override public Editor edit() { return base.edit(); }

    @Override public void registerOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener l) {
        base.registerOnSharedPreferenceChangeListener(l);
    }

    @Override public void unregisterOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener l) {
        base.unregisterOnSharedPreferenceChangeListener(l);
    }
}
