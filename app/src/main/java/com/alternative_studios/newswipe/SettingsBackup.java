package com.alternative_studios.newswipe;

import android.content.ComponentName;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import com.alternative_studios.newswipe.clipboard.ClipboardHistory;
import com.alternative_studios.newswipe.suggest.UserDictionary;

/**
 * 설정을 JSON 파일로 내보내고 다시 가져온다.
 *
 * <p>설정값 전체(길게 누르기 문자 편집 포함)와 런처 아이콘 표시 여부를 담는다.
 * 마지막 사용 언어는 설정이 아니라 사용 기록이라 제외한다.
 * 학습한 단어, 최근·고정 이모지, 클립보드 기록(고정 항목 포함, 이미지는 제외)은 내보낼 때 사용자가 고른 경우에만 담는다 (가져올 때 파일에 있으면 그대로 바꾼다).
 */
public final class SettingsBackup {
    private static final String APP = "newswipe-settings";
    private static final int VERSION = 1;
    private static final int MAX_BYTES = 4 << 20;
    private static final int MAX_KEYS = 4000;
    private static final int MAX_STRING = 4000;

    private SettingsBackup() {
    }

    private static boolean excluded(String key) {
        return Prefs.RECENT_EMOJI.equals(key) || Prefs.PINNED_EMOJI.equals(key) || Prefs.LANGUAGE.equals(key)
                || Prefs.ONE_HAND.equals(key) || Prefs.ONBOARDING_DONE.equals(key);
    }

    private static ComponentName launcherAlias(Context context) {
        return new ComponentName(context, context.getPackageName() + ".LauncherAlias");
    }

    public static boolean launcherShown(Context context) {
        return context.getPackageManager().getComponentEnabledSetting(launcherAlias(context))
                != PackageManager.COMPONENT_ENABLED_STATE_DISABLED;
    }

    public static void setLauncherShown(Context context, boolean shown) {
        context.getPackageManager().setComponentEnabledSetting(launcherAlias(context),
                shown ? PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                        : PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.DONT_KILL_APP);
    }

    /** 현재 설정을 JSON 문자열로 만든다. */
    public static String toJson(Context context, boolean includeWords, boolean includeEmoji,
            boolean includeClipboard) throws JSONException {
        JSONObject settings = new JSONObject();
        for (Map.Entry<String, ?> e : new Prefs(context).raw().getAll().entrySet()) {
            if (excluded(e.getKey())) continue;
            Object v = e.getValue();
            JSONObject item = new JSONObject();
            if (v instanceof Boolean) item.put("type", "bool");
            else if (v instanceof Integer) item.put("type", "int");
            else if (v instanceof Long) item.put("type", "long");
            else if (v instanceof Float) item.put("type", "float");
            else if (v instanceof String) item.put("type", "string");
            else continue;
            item.put("value", v);
            settings.put(e.getKey(), item);
        }
        JSONObject root = new JSONObject();
        root.put("app", APP);
        root.put("version", VERSION);
        root.put("launcher_icon_shown", launcherShown(context));
        root.put("settings", settings);
        if (includeWords) {
            org.json.JSONArray arr = new org.json.JSONArray();
            for (String[] w : UserDictionary.shared(context.getFilesDir()).snapshot()) {
                arr.put(new org.json.JSONArray().put(w[0]).put(Integer.parseInt(w[1])).put(Long.parseLong(w[2])));
            }
            root.put("user_words", arr);
        }
        if (includeEmoji) {
            root.put("recent_emoji", new Prefs(context).recentEmoji());
            root.put("pinned_emoji", new Prefs(context).pinnedEmoji());
        }
        if (includeClipboard) {
            org.json.JSONArray arr = new org.json.JSONArray();
            for (String[] c : clipboard(context).snapshot(System.currentTimeMillis())) {
                arr.put(new org.json.JSONArray().put(c[0]).put(Long.parseLong(c[1])).put("1".equals(c[2])));
            }
            root.put("clipboard", arr);
        }
        return root.toString(2);
    }

    private static ClipboardHistory clipboard(Context context) {
        return new ClipboardHistory(new java.io.File(context.getFilesDir(), "clipboard.txt"));
    }

    /** 암호화한 파일의 표시와, 암호문에 함께 묶어 바꿔치기를 막는 값. */
    private static final String CIPHER = "AES-256-GCM/PBKDF2-HMAC-SHA256";
    private static final byte[] AAD = (APP + "|" + VERSION + "|" + CIPHER).getBytes(StandardCharsets.UTF_8);

    /**
     * 설정을 파일로 내보낸다. password가 있으면 내용 전체를 그 비밀번호로 암호화한다 (BackupCrypto).
     * 시간이 걸릴 수 있으니 (비밀번호에서 키를 만드는 데 1초 안팎) 백그라운드에서 부른다.
     */
    public static void export(Context context, Uri uri, boolean includeWords, boolean includeEmoji,
            boolean includeClipboard, char[] password) throws IOException, JSONException,
            java.security.GeneralSecurityException {
        String json = toJson(context, includeWords, includeEmoji, includeClipboard);
        if (password != null) {
            BackupCrypto.Sealed sealed = BackupCrypto.encrypt(json.getBytes(StandardCharsets.UTF_8), password, AAD);
            JSONObject env = new JSONObject();
            env.put("app", APP);
            env.put("version", VERSION);
            env.put("encrypted", CIPHER);
            env.put("iterations", sealed.iterations);
            env.put("salt", BackupCrypto.b64(sealed.salt));
            env.put("iv", BackupCrypto.b64(sealed.iv));
            env.put("data", BackupCrypto.b64(sealed.data));
            json = env.toString(2);
        }
        byte[] data = json.getBytes(StandardCharsets.UTF_8);
        try (OutputStream out = context.getContentResolver().openOutputStream(uri, "wt")) {
            if (out == null) throw new IOException("파일을 열 수 없습니다");
            out.write(data);
        }
    }

    /** 백업 파일을 글자로 읽는다. */
    public static String read(Context context, Uri uri) throws IOException {
        String text;
        try (InputStream in = context.getContentResolver().openInputStream(uri)) {
            if (in == null) throw new IOException("파일을 열 수 없습니다");
            ByteArrayOutputStream buf = new ByteArrayOutputStream();
            byte[] chunk = new byte[8192];
            int n;
            while ((n = in.read(chunk)) > 0) {
                buf.write(chunk, 0, n);
                if (buf.size() > MAX_BYTES) throw new IOException("백업 파일이 너무 큽니다");
            }
            text = buf.toString("UTF-8");
        }
        return text;
    }

    /** 비밀번호로 암호화한 백업인지. */
    public static boolean isEncrypted(String text) {
        try {
            JSONObject root = new JSONObject(text);
            return APP.equals(root.optString("app")) && root.has("encrypted");
        } catch (JSONException e) {
            return false;
        }
    }

    /** 암호화한 백업을 풀어 원래 JSON을 돌려준다. 비밀번호가 틀리면 WrongPasswordException. 백그라운드에서 부른다. */
    public static String decrypt(String text, char[] password) throws JSONException,
            java.security.GeneralSecurityException, BackupCrypto.WrongPasswordException {
        JSONObject env = new JSONObject(text);
        if (!CIPHER.equals(env.optString("encrypted"))) throw new JSONException("지원하지 않는 암호화 방식입니다");
        BackupCrypto.Sealed sealed;
        try {
            sealed = new BackupCrypto.Sealed(env.getInt("iterations"), BackupCrypto.unb64(env.getString("salt")),
                    BackupCrypto.unb64(env.getString("iv")), BackupCrypto.unb64(env.getString("data")));
        } catch (IllegalArgumentException e) {
            throw new JSONException("암호화된 내용이 손상되었습니다");
        }
        return new String(BackupCrypto.decrypt(sealed, password, AAD), StandardCharsets.UTF_8);
    }

    /** 가져온 설정(암호화되지 않은 JSON)으로 현재 설정을 바꾼다. {설정 개수, 학습한 단어 개수(백업에 없으면 -1)}를 돌려준다. */
    public static int[] apply(Context context, String text) throws JSONException {
        JSONObject root = new JSONObject(text);
        if (!APP.equals(root.optString("app")) || root.optInt("version", 0) < 1) {
            throw new JSONException("NewSwipe 설정 백업 파일이 아닙니다");
        }
        JSONObject settings = root.getJSONObject("settings");
        if (settings.length() > MAX_KEYS) throw new JSONException("항목이 너무 많습니다");
        java.util.List<String[]> userWords = null;
        org.json.JSONArray wordArray = root.optJSONArray("user_words");
        if (wordArray != null) {
            userWords = new java.util.ArrayList<>();
            for (int i = 0; i < wordArray.length() && i < UserDictionary.MAX_WORDS; i++) {
                org.json.JSONArray a = wordArray.optJSONArray(i);
                if (a == null || a.length() != 3) continue;
                userWords.add(new String[]{a.optString(0), String.valueOf(a.optLong(1, 1)), String.valueOf(a.optLong(2, 0))});
            }
        }
        java.util.List<String[]> clipEntries = null;
        org.json.JSONArray clipArray = root.optJSONArray("clipboard");
        if (clipArray != null) {
            clipEntries = new java.util.ArrayList<>();
            for (int i = 0; i < clipArray.length() && i < 200; i++) {
                org.json.JSONArray a = clipArray.optJSONArray(i);
                if (a == null || a.length() != 3) continue;
                clipEntries.add(new String[]{a.optString(0), String.valueOf(a.optLong(1, 0)),
                        a.optBoolean(2, false) ? "1" : "0"});
            }
        }
        String recentEmoji = root.has("recent_emoji") ? root.optString("recent_emoji", "").trim() : null;
        if (recentEmoji != null && recentEmoji.length() > MAX_STRING) throw new JSONException("최근 이모지가 너무 깁니다");
        String pinnedEmoji = root.has("pinned_emoji") ? root.optString("pinned_emoji", "").trim() : null;
        if (pinnedEmoji != null && pinnedEmoji.length() > MAX_STRING) throw new JSONException("고정한 이모지가 너무 깁니다");

        // 먼저 전부 읽어 검증한 뒤 한 번에 적용한다 (중간에 실패해도 현재 설정이 그대로 남는다).
        java.util.List<String> keys = new java.util.ArrayList<>();
        java.util.List<Object> values = new java.util.ArrayList<>();
        java.util.Iterator<String> it = settings.keys();
        while (it.hasNext()) {
            String key = it.next();
            if (key.isEmpty() || key.length() > 100 || excluded(key)) continue;
            JSONObject item = settings.getJSONObject(key);
            Object value;
            switch (item.getString("type")) {
                case "bool": value = item.getBoolean("value"); break;
                case "int": value = item.getInt("value"); break;
                case "long": value = item.getLong("value"); break;
                case "float": value = (float) item.getDouble("value"); break;
                case "string":
                    String s = item.getString("value");
                    if (s.length() > MAX_STRING) throw new JSONException("값이 너무 깁니다: " + key);
                    value = s;
                    break;
                default: continue;
            }
            keys.add(key);
            values.add(value);
        }

        SharedPreferences sp = new Prefs(context).raw();
        SharedPreferences.Editor ed = sp.edit();
        for (String old : sp.getAll().keySet()) {
            if (!excluded(old)) ed.remove(old);
        }
        for (int i = 0; i < keys.size(); i++) {
            Object v = values.get(i);
            String k = keys.get(i);
            if (v instanceof Boolean) ed.putBoolean(k, (Boolean) v);
            else if (v instanceof Integer) ed.putInt(k, (Integer) v);
            else if (v instanceof Long) ed.putLong(k, (Long) v);
            else if (v instanceof Float) ed.putFloat(k, (Float) v);
            else ed.putString(k, (String) v);
        }
        if (recentEmoji != null) ed.putString(Prefs.RECENT_EMOJI, recentEmoji);
        if (pinnedEmoji != null) ed.putString(Prefs.PINNED_EMOJI, pinnedEmoji);
        ed.apply();
        if (root.has("launcher_icon_shown")) setLauncherShown(context, root.optBoolean("launcher_icon_shown", true));
        int words = userWords == null ? -1 : UserDictionary.shared(context.getFilesDir()).replaceAll(userWords);
        if (clipEntries != null) clipboard(context).replaceAll(clipEntries, System.currentTimeMillis());
        return new int[]{keys.size(), words};
    }
}
