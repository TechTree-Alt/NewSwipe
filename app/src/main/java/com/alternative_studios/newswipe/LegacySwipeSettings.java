package com.alternative_studios.newswipe;

import android.content.SharedPreferences;

import com.alternative_studios.newswipe.hangul.HangulComposer;
import com.alternative_studios.newswipe.keyboard.FinalSwipes;
import com.alternative_studios.newswipe.keyboard.FunctionSwipes;
import com.alternative_studios.newswipe.keyboard.Key;
import com.alternative_studios.newswipe.keyboard.KeyboardLayout;
import com.alternative_studios.newswipe.keyboard.KeyboardSwipes;
import com.alternative_studios.newswipe.keyboard.LongPressActions;
import com.alternative_studios.newswipe.keyboard.SwipeAction;
import com.alternative_studios.newswipe.keyboard.SwipeCustom;
import com.alternative_studios.newswipe.keyboard.SwipeSpec;
import com.alternative_studios.newswipe.keyboard.ToolbarSwipes;
import com.alternative_studios.newswipe.keyboard.VowelSwipes;

/**
 * 예전 '기본 밀기·길게 누르기 규칙' 스위치(밀어서 쌍자음, 스페이스바 커서 이동, 길게 눌러 지우기 …)와 '완전 사용자화' 스위치를
 * 없애고, 모든 키·방향의 글자·기능을 키마다 정한 값으로만 쓰게 바꾸면서 남은 예전 설정을 한 번 옮긴다.
 * <p>
 * 완전 사용자화를 꺼 두었던 묶음은 그 스위치들이 만들던 동작을 키·방향마다 그대로 적어 넣는다 (기본값과 같은 칸은 비워 둔다).
 * 그래서 옮긴 뒤에도 키보드는 옮기기 전과 똑같이 동작한다. 완전 사용자화를 켜 두었던 묶음은 이미 키마다 정한 값을 쓰고 있었으므로
 * 그대로 둔다. 옮긴 뒤에는 예전 스위치 값을 지운다. 처음 설치해 설정이 하나도 없으면 옮길 것 없이 새 기본값을 쓴다.
 * 예전 버전에서 내보낸 설정 파일을 가져온 뒤에도 다시 부른다 ({@link SettingsBackup}).
 */
final class LegacySwipeSettings {
    /** 옮기기를 마쳤다는 표시. */
    static final String DONE = "swipe_tables_v1";

    // 예전 스위치 (지금은 읽기만 하고, 옮긴 뒤 지운다)
    private static final String SWIPE_CUSTOM = "swipe_custom", SWIPE_FN_CUSTOM = "swipe_fn_custom",
            LONG_PRESS_CUSTOM = "long_press_custom", SWIPE_TOOLBAR_CUSTOM = "swipe_toolbar_custom",
            SWIPE_KEYBOARD_CUSTOM = "swipe_keyboard_custom";
    private static final String SWIPE_DOUBLE = "swipe_double", D7_SS_UP = "d7_ss_up", SWIPE_IOTIZED = "swipe_iotized",
            SWIPE_COMPOUND_VOWEL = "swipe_compound_vowel", SWIPE_FINAL = "swipe_final",
            PERIOD_SWIPE_COMMA = "period_swipe_comma";
    private static final String SPACE_CURSOR = "space_cursor", SPACE_CURSOR_H = "space_cursor_h",
            SPACE_CURSOR_V = "space_cursor_v", SPACE_LANG_SWIPE = "space_lang_swipe", GLOBE_SWIPE_LANG = "globe_swipe_lang",
            DELETE_WORD_SWIPE = "delete_word_swipe", MODE_KEY_EMOJI = "mode_key_emoji";
    private static final String LONG_PRESS_DELETE = "long_press_delete", MODE_KEY_LONG_PRESS = "mode_key_settings";
    private static final String TOOLBAR_SWIPE = "toolbar_swipe", TOOL_CLIPBOARD_SWIPE = "tool_clipboard_swipe",
            TOOL_EMOJI_SWIPE = "tool_emoji_swipe", TOOL_UNDO_SWIPE = "tool_undo_swipe";
    private static final String CHAR_CURSOR = "char_cursor", CHAR_CURSOR_H = "char_cursor_h",
            CHAR_CURSOR_V = "char_cursor_v", TWO_FINGER_UNDO = "two_finger_undo";

    /** 옮긴 뒤 지우는 예전 값들. */
    static final String[] LEGACY_KEYS = {SWIPE_CUSTOM, SWIPE_FN_CUSTOM, LONG_PRESS_CUSTOM, SWIPE_TOOLBAR_CUSTOM,
            SWIPE_KEYBOARD_CUSTOM, SWIPE_DOUBLE, D7_SS_UP, SWIPE_IOTIZED, SWIPE_COMPOUND_VOWEL, SWIPE_FINAL,
            PERIOD_SWIPE_COMMA, SPACE_CURSOR, SPACE_CURSOR_H, SPACE_CURSOR_V, SPACE_LANG_SWIPE, GLOBE_SWIPE_LANG,
            DELETE_WORD_SWIPE, MODE_KEY_EMOJI, LONG_PRESS_DELETE, MODE_KEY_LONG_PRESS, TOOLBAR_SWIPE,
            TOOL_CLIPBOARD_SWIPE, TOOL_EMOJI_SWIPE, TOOL_UNDO_SWIPE, CHAR_CURSOR, CHAR_CURSOR_H, CHAR_CURSOR_V,
            TWO_FINGER_UNDO};
    /** 예전 겹받침·조합형 이중모음 편집 값 (이제는 글자 밀기 값으로 옮긴다). */
    static final String[] LEGACY_PREFIXES = {"swipe_final_", "swipe_vowel_"};

    private LegacySwipeSettings() {
    }

    /** 아직 옮기지 않았으면 옮긴다. 이미 옮겼으면 값 하나만 읽고 끝난다. */
    static void migrateIfNeeded(Prefs prefs) {
        SharedPreferences sp = prefs.raw();
        if (sp.getBoolean(DONE, false)) return;
        if (!sp.getAll().isEmpty()) migrate(prefs, sp);
        SharedPreferences.Editor e = sp.edit();
        for (String k : LEGACY_KEYS) e.remove(k);
        for (String k : sp.getAll().keySet()) {
            for (String p : LEGACY_PREFIXES) if (k.startsWith(p)) e.remove(k);
        }
        e.putBoolean(DONE, true).apply();
    }

    private static void migrate(Prefs prefs, SharedPreferences sp) {
        if (!sp.getBoolean(SWIPE_CUSTOM, false)) migrateChars(prefs, sp);
        if (!sp.getBoolean(SWIPE_FN_CUSTOM, false)) migrateFunctionSwipes(prefs, sp);
        if (!sp.getBoolean(LONG_PRESS_CUSTOM, false)) migrateLongPress(prefs, sp);
        if (!sp.getBoolean(SWIPE_TOOLBAR_CUSTOM, false)) migrateToolbar(prefs, sp);
        if (!sp.getBoolean(SWIPE_KEYBOARD_CUSTOM, false)) migrateKeyboard(prefs, sp);
    }

    // ---------------------------------------------------------------- 글자 밀기

    private static void migrateChars(Prefs prefs, SharedPreferences sp) {
        boolean newSwipe = prefs.koreanNewSwipe();
        for (char c = 'ㄱ'; c <= 'ㅣ'; c++) {
            String label = String.valueOf(c);
            for (int dir : SwipeCustom.DIRS) {
                store(prefs, KeyboardLayout.GROUP_KO, label, dir, legacyKorean(prefs, sp, c, dir),
                        SwipeCustom.defaultFor(true, newSwipe, label, dir));
            }
        }
        for (char c = 'a'; c <= 'z'; c++) {
            for (int dir : SwipeCustom.DIRS) store(prefs, KeyboardLayout.GROUP_EN, String.valueOf(c), dir, null, null);
        }
        for (String slot : new String[]{",", "."}) {
            for (int dir : SwipeCustom.DIRS) {
                String old = ".".equals(slot) && dir == Key.SWIPE_UP && sp.getBoolean(PERIOD_SWIPE_COMMA, true) ? "," : null;
                store(prefs, KeyboardLayout.GROUP_SYM, slot, dir, old, SwipeCustom.defaultFor(true, newSwipe, slot, dir));
            }
        }
    }

    /** 예전 규칙 스위치로 한글 자모 키 c를 dir로 밀 때 입력되던 글자 (예전 KeyboardLayout.letterKeys와 같다). */
    static String legacyKorean(Prefs prefs, SharedPreferences sp, char c, int dir) {
        boolean newSwipe = prefs.koreanNewSwipe();
        boolean doubleOn = sp.getBoolean(SWIPE_DOUBLE, true);
        if (HangulComposer.isVowel(c)) {
            if (dir == Key.SWIPE_DOWN) return sp.getBoolean(SWIPE_IOTIZED, true) ? VowelSwipes.iotizedFor(c) : null;
            return sp.getBoolean(SWIPE_COMPOUND_VOWEL, true) ? VowelSwipes.compoundFor(prefs, c, dir) : null;
        }
        String label = String.valueOf(c);
        if (dir == Key.SWIPE_DOWN) {
            char d = HangulComposer.doubled(c);
            return doubleOn && d != 0 ? String.valueOf(d) : null;
        }
        if (dir == Key.SWIPE_RIGHT && newSwipe && KeyboardLayout.nsRight(label) != null) return KeyboardLayout.nsRight(label);
        if (dir == Key.SWIPE_UP && newSwipe && doubleOn && sp.getBoolean(D7_SS_UP, false) && c == 'ㅅ') return "ㅆ";
        if (!sp.getBoolean(SWIPE_FINAL, true)) return null;
        int fd = dir == Key.SWIPE_UP ? FinalSwipes.UP : dir == Key.SWIPE_LEFT ? FinalSwipes.LEFT : FinalSwipes.RIGHT;
        return FinalSwipes.get(prefs, c, fd);
    }

    /** old(예전에 입력되던 글자)가 새 기본값과 같으면 고친 값을 지우고, 다르면 그 글자(없으면 빈칸)를 적는다. */
    private static void store(Prefs prefs, String group, String label, int dir, String old, String def) {
        String dirName = SwipeCustom.dirName(dir);
        if (java.util.Objects.equals(old, def)) prefs.resetSwipeCustomOverride(group, label, dirName);
        else prefs.setSwipeCustomOverride(group, label, dirName, old == null ? "" : old);
    }

    // ---------------------------------------------------------------- 기능 밀기·길게 누르기

    private static void migrateFunctionSwipes(Prefs prefs, SharedPreferences sp) {
        boolean cursor = sp.getBoolean(SPACE_CURSOR, true);
        boolean lang = sp.getBoolean(SPACE_LANG_SWIPE, false);
        for (int slot = 0; slot < FunctionSwipes.SPEC.slotCount(); slot++) {
            for (int dir : SwipeCustom.DIRS) {
                boolean horizontal = dir == Key.SWIPE_LEFT || dir == Key.SWIPE_RIGHT;
                String old = SwipeAction.NONE;
                switch (slot) {
                    case FunctionSwipes.MODE:
                        if (dir == Key.SWIPE_UP && sp.getBoolean(MODE_KEY_EMOJI, true)) old = SwipeAction.EMOJI_OPEN;
                        break;
                    case FunctionSwipes.GLOBE:
                        if (dir == Key.SWIPE_UP && sp.getBoolean(GLOBE_SWIPE_LANG, false)) old = SwipeAction.LANGUAGE_PICK;
                        break;
                    case FunctionSwipes.SPACE:
                        if (horizontal && lang) old = SwipeAction.LANGUAGE;
                        else if (cursor && sp.getBoolean(horizontal ? SPACE_CURSOR_H : SPACE_CURSOR_V, true)) {
                            old = SwipeAction.CURSOR_MOVE;
                        }
                        break;
                    case FunctionSwipes.DELETE:
                        if (sp.getBoolean(DELETE_WORD_SWIPE, true)) {
                            if (dir == Key.SWIPE_LEFT) old = SwipeAction.DELETE_WORD;
                            if (dir == Key.SWIPE_UP) old = SwipeAction.DELETE_LINE_START;
                        }
                        break;
                    case FunctionSwipes.CURSOR:
                        old = SwipeAction.CURSOR_MOVE;   // 예전에도 커서 이동 키는 네 방향 모두 커서를 옮겼다
                        break;
                    default:
                        break;
                }
                put(prefs, FunctionSwipes.SPEC, slot, dir, old);
            }
        }
    }

    private static void migrateLongPress(Prefs prefs, SharedPreferences sp) {
        for (int slot = 0; slot < LongPressActions.SPEC.slotCount(); slot++) {
            String old = SwipeAction.NONE;
            if (slot == LongPressActions.MODE && sp.getBoolean(MODE_KEY_LONG_PRESS, true)) old = SwipeAction.EMOJI_OPEN;
            if (slot == LongPressActions.GLOBE) old = SwipeAction.IME_PICKER;
            if (slot == LongPressActions.DELETE && sp.getBoolean(LONG_PRESS_DELETE, true)) old = SwipeAction.DELETE_REPEAT;
            put(prefs, LongPressActions.SPEC, slot, LongPressActions.DIR, old);
        }
    }

    private static void migrateToolbar(Prefs prefs, SharedPreferences sp) {
        ToolbarSwipes old = ToolbarSwipes.standard(sp.getBoolean(TOOLBAR_SWIPE, true), sp.getBoolean(TOOL_CLIPBOARD_SWIPE, true),
                sp.getBoolean(TOOL_EMOJI_SWIPE, true), sp.getBoolean(TOOL_UNDO_SWIPE, true));
        for (int slot = 0; slot < ToolbarSwipes.SPEC.slotCount(); slot++) {
            for (int dir : SwipeCustom.DIRS) put(prefs, ToolbarSwipes.SPEC, slot, dir, old.action(slot, dir));
        }
    }

    private static void migrateKeyboard(Prefs prefs, SharedPreferences sp) {
        boolean chars = sp.getBoolean(CHAR_CURSOR, false);
        boolean undo = sp.getBoolean(TWO_FINGER_UNDO, false);
        for (int dir : SwipeCustom.DIRS) {
            boolean horizontal = dir == Key.SWIPE_LEFT || dir == Key.SWIPE_RIGHT;
            boolean axis = sp.getBoolean(horizontal ? CHAR_CURSOR_H : CHAR_CURSOR_V, true);
            put(prefs, KeyboardSwipes.SPEC, KeyboardSwipes.ONE, dir, chars && axis ? SwipeAction.FREE_CURSOR : SwipeAction.NONE);
            put(prefs, KeyboardSwipes.SPEC, KeyboardSwipes.TWO, dir,
                    undo ? KeyboardSwipes.SPEC.defaultAction(KeyboardSwipes.TWO, dir) : SwipeAction.NONE);
        }
    }

    /** 예전 동작(old)이 새 기본값과 같으면 고친 값을 지우고, 다르면 그 기능을 적는다. */
    private static void put(Prefs prefs, SwipeSpec spec, int slot, int dir, String old) {
        if (old.equals(spec.defaultAction(slot, dir))) spec.reset(prefs, slot, dir);
        else spec.set(prefs, slot, dir, old);
    }
}
