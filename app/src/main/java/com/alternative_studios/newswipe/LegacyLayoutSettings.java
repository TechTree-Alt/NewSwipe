package com.alternative_studios.newswipe;

import android.content.SharedPreferences;

/**
 * 1.9.1 이하에서 2.0.0으로 올라올 때, 자판 모양에 관한 예전 값을 한 번 옮겨 업데이트 전과 같은 모습을 지킨다.
 * <ul>
 *   <li>자·모음 균형 배열·분리 키보드의 두 묶음: 2.0.0은 묶음 폭·위치의 기본값(폭 90%, 위치 -10·10)과 저장 방식(안쪽 경계의
 *       자리)이 바뀌었고 '자·모음 경계 위치' 설정을 없앴다. 예전에 쓰던(켜 두었거나 값을 정해 둔) 배열·프로필은 예전 값으로 보이던
 *       자리를 새 방식으로 적어 둔다. 쓰지 않던 곳은 새 기본값을 쓴다.</li>
 *   <li>분리 키보드의 '좌·우 균형 배열': 2.0.0은 가로 모드·폴더블 내부 화면도 세로 모드 값을 따르고, 값이 없을 때 단모음(8열)은
 *       켜짐이다. 예전 가로 모드(없으면 폴더블 내부 화면) 값을, 정한 적이 없으면 예전 기본값(꺼짐)을 적어 둔다.</li>
 *   <li>가로 모드·폴더블 내부 화면의 '세로 위치'(키보드를 화면 아래에서 띄우는 높이)는 없앴으므로 그 높이만큼 그 프로필의
 *       아래쪽 여백을 늘린다.</li>
 *   <li>한 손 모드는 이제 키보드 전체 여백을 쓰지 않고 자기 '아래쪽 여백'(예전 '세로 위치')만 쓴다. 예전에는 둘을 더한 높이에
 *       자판이 떠 있었으므로, 예전 키보드 아래쪽 여백을 한 손 모드 아래쪽 여백에 더해 같은 자리에 둔다.</li>
 * </ul>
 * 예전 버전을 쓰던 설정에서만 옮긴다 (처음 설치했거나 이미 2.0.0을 쓰던 설정은 표시만 남긴다). 예전 버전에서 내보낸 설정 파일을
 * 가져온 뒤에도 다시 돈다 ({@link LegacySwipeSettings#migrateIfNeeded}가 부른다).
 */
final class LegacyLayoutSettings {
    /** 옮기기를 마쳤다는 표시. */
    static final String DONE = "layout_v1";

    private static final String[] LAYOUTS = {Prefs.KOREAN_LAYOUT_8, Prefs.KOREAN_LAYOUT_D7, Prefs.KOREAN_LAYOUT_DUBEOLSIK};
    private static final String[] SPLIT_PROFILES = {Prefs.PROFILE_LANDSCAPE, Prefs.PROFILE_LARGE_PORTRAIT,
            Prefs.PROFILE_LARGE_LANDSCAPE};

    // 예전 값의 키 (지금은 읽기만 한다)
    private static final String BOUNDARY = Prefs.BALANCED_BOUNDARY;
    private static final String LANDSCAPE_REACH = "split_reach_column", LARGE_REACH = "large_split_reach_column_port";
    private static final String PORTRAIT_REACH = "portrait_split_reach_column";

    /** 예전 묶음 폭·위치의 범위와 기본값. */
    private static final int OLD_WIDTH_DEFAULT = 100, OLD_WIDTH_MAX = 100, OLD_PORTRAIT_WIDTH_MIN = 60, OLD_SPLIT_WIDTH_MIN = 30;
    private static final int OLD_POS_DEFAULT = 50;
    private static final int OLD_BOUNDARY_MIN = 30, OLD_BOUNDARY_MAX = 70, OLD_BOUNDARY_DEFAULT = 50;
    private static final float ROW = 10f, HALF = 5f;

    private LegacyLayoutSettings() {
    }

    /** 아직 옮기지 않았으면 옮긴다. legacyUser: 예전 버전에서 쓰던 설정인지 (밀기 규칙을 옮기기 전에 판단한 값). */
    static void migrateIfNeeded(Prefs prefs, boolean legacyUser) {
        SharedPreferences sp = prefs.raw();
        if (sp.getBoolean(DONE, false)) return;
        if (legacyUser) {
            SharedPreferences.Editor e = sp.edit();
            migrateBlocks(sp, e);
            migrateReach(sp, e);
            migrateLift(sp, e);
            migrateOneHandBottom(sp, e);
            e.apply();
        }
        sp.edit().putBoolean(DONE, true).apply();
    }

    // ---------------------------------------------------------------- 두 묶음의 폭·위치

    private static void migrateBlocks(SharedPreferences sp, SharedPreferences.Editor e) {
        // 세로 모드의 자·모음 균형 배열 (예전에는 단모음 두 배열에만 있었다).
        for (String layout : new String[]{Prefs.KOREAN_LAYOUT_8, Prefs.KOREAN_LAYOUT_D7}) {
            migratePortraitBalanced(sp, e, layout);
        }
        // 가로 모드·폴더블 내부 화면의 분리 키보드 (두벌식은 그때 실험실이었어도 배열 이름으로 저장했다).
        for (String profile : SPLIT_PROFILES) {
            for (String layout : LAYOUTS) migrateSplit(sp, e, profile, layout);
        }
    }

    private static String key(String base, String profile, boolean split, String layout) {
        return Prefs.balancedKeyFor(base, profile, split, layout);
    }

    private static void migratePortraitBalanced(SharedPreferences sp, SharedPreferences.Editor e, String layout) {
        boolean d8 = Prefs.KOREAN_LAYOUT_8.equals(layout);
        String on = Prefs.BALANCED_LAYOUT + "_" + layout;
        String cw = key(Prefs.BALANCED_CONSONANT_WIDTH, "", false, layout), cp = key(Prefs.BALANCED_CONSONANT_POS, "", false, layout);
        String vw = key(Prefs.BALANCED_VOWEL_WIDTH, "", false, layout), vp = key(Prefs.BALANCED_VOWEL_POS, "", false, layout);
        String bd = key(BOUNDARY, "", false, layout);
        // 예전 판의 균형 배열 켜짐: 배열별 값, 없으면 (8열 단모음만) 배열별로 나누기 전의 값.
        boolean used = sp.contains(on) ? sp.getBoolean(on, false) : d8 && sp.getBoolean(Prefs.BALANCED_LAYOUT, false);
        boolean custom = sp.contains(cw) || sp.contains(cp) || sp.contains(vw) || sp.contains(vp) || sp.contains(bd)
                || (d8 && (sp.contains(Prefs.BALANCED_CONSONANT_WIDTH) || sp.contains(Prefs.BALANCED_CONSONANT_POS)
                || sp.contains(Prefs.BALANCED_VOWEL_WIDTH) || sp.contains(Prefs.BALANCED_VOWEL_POS)));
        if (!used && !custom) return;

        int consonantWidth = clamp(oldInt(sp, cw, d8 ? Prefs.BALANCED_CONSONANT_WIDTH : null, OLD_WIDTH_DEFAULT),
                OLD_PORTRAIT_WIDTH_MIN, OLD_WIDTH_MAX);
        int vowelWidth = clamp(oldInt(sp, vw, d8 ? Prefs.BALANCED_VOWEL_WIDTH : null, OLD_WIDTH_DEFAULT),
                OLD_PORTRAIT_WIDTH_MIN, OLD_WIDTH_MAX);
        int consonantPos = clamp(oldInt(sp, cp, d8 ? Prefs.BALANCED_CONSONANT_POS : null, OLD_POS_DEFAULT), 0, 100);
        int vowelPos = clamp(oldInt(sp, vp, d8 ? Prefs.BALANCED_VOWEL_POS : null, OLD_POS_DEFAULT), 0, 100);
        int boundary = clamp(sp.getInt(bd, OLD_BOUNDARY_DEFAULT), OLD_BOUNDARY_MIN, OLD_BOUNDARY_MAX);

        // 예전 세로 모드 균형 배열: 자음 쪽(왼쪽 boundary%)과 모음 쪽(나머지) 안에서 각자 폭%로 줄이고 남는 폭에서 위치에 놓았다.
        float left = ROW * boundary / 100f, right = ROW - left;
        float lw = left * consonantWidth / 100f, rw = right * vowelWidth / 100f;
        float ls = (left - lw) * consonantPos / 100f;
        float rs = left + (right - rw) * vowelPos / 100f;
        writeBlocks(e, "", false, layout, lw, ls, rw, rs);
        // 옮긴 예전 값은 지운다 (새 값이 있으면 읽지 않지만, 남겨 두면 기본값으로 되돌릴 때 헷갈린다).
        e.remove(cp).remove(vp).remove(bd);
        if (d8) {
            e.remove(Prefs.BALANCED_CONSONANT_WIDTH).remove(Prefs.BALANCED_CONSONANT_POS)
                    .remove(Prefs.BALANCED_VOWEL_WIDTH).remove(Prefs.BALANCED_VOWEL_POS);
        }
    }

    private static void migrateSplit(SharedPreferences sp, SharedPreferences.Editor e, String profile, String layout) {
        String cw = key(Prefs.BALANCED_CONSONANT_WIDTH, profile, true, layout), cp = key(Prefs.BALANCED_CONSONANT_POS, profile, true, layout);
        String vw = key(Prefs.BALANCED_VOWEL_WIDTH, profile, true, layout), vp = key(Prefs.BALANCED_VOWEL_POS, profile, true, layout);
        boolean custom = sp.contains(cw) || sp.contains(cp) || sp.contains(vw) || sp.contains(vp);
        if (!splitOn(sp, profile) && !custom) return;

        int consonantWidth = clamp(sp.getInt(cw, OLD_WIDTH_DEFAULT), OLD_SPLIT_WIDTH_MIN, OLD_WIDTH_MAX);
        int vowelWidth = clamp(sp.getInt(vw, OLD_WIDTH_DEFAULT), OLD_SPLIT_WIDTH_MIN, OLD_WIDTH_MAX);
        int consonantPos = clamp(sp.getInt(cp, OLD_POS_DEFAULT), 0, 100);
        int vowelPos = clamp(sp.getInt(vp, OLD_POS_DEFAULT), 0, 100);
        // 예전 분리 키보드: 묶음은 자기 절반의 폭%이고, 절반 안의 남는 폭에서 위치에 놓았다.
        float lw = HALF * consonantWidth / 100f, rw = HALF * vowelWidth / 100f;
        float ls = (HALF - lw) * consonantPos / 100f;
        float rs = HALF + (HALF - rw) * vowelPos / 100f;
        writeBlocks(e, profile, true, layout, lw, ls, rw, rs);
        e.remove(cp).remove(vp);
    }

    /** 예전 판에서 이 프로필의 분리 키보드를 켰는지 (예전에 배열마다 저장하던 값까지). */
    private static boolean splitOn(SharedPreferences sp, String profile) {
        String k = Prefs.PROFILE_LANDSCAPE.equals(profile) ? Prefs.LANDSCAPE_SPLIT : "large_split_port";
        if (sp.contains(k)) return sp.getBoolean(k, false);
        return sp.getBoolean(k + "_" + Prefs.KOREAN_LAYOUT_8, false) || sp.getBoolean(k + "_" + Prefs.KOREAN_LAYOUT_D7, false);
    }

    /**
     * 칸 단위 자리(왼쪽 묶음 폭 lw·시작 ls, 오른쪽 묶음 폭 rw·시작 rs)를 새 방식의 값으로 적는다:
     * 폭은 자기 절반의 %, 위치는 안쪽 경계의 자리 (가운데 0, 양 끝 ±100).
     */
    private static void writeBlocks(SharedPreferences.Editor e, String profile, boolean split, String layout,
                                    float lw, float ls, float rw, float rs) {
        e.putInt(key(Prefs.BALANCED_CONSONANT_WIDTH, profile, split, layout), Math.round(lw / HALF * 100f));
        e.putInt(key(Prefs.BALANCED_VOWEL_WIDTH, profile, split, layout), Math.round(rw / HALF * 100f));
        e.putInt(key(Prefs.BALANCED_CONSONANT_EDGE, profile, split, layout),
                clamp(Math.round((ls + lw - HALF) / HALF * 100f), Prefs.BALANCED_POS_MIN, Prefs.BALANCED_POS_MAX));
        e.putInt(key(Prefs.BALANCED_VOWEL_EDGE, profile, split, layout),
                clamp(Math.round((rs - HALF) / HALF * 100f), Prefs.BALANCED_POS_MIN, Prefs.BALANCED_POS_MAX));
    }

    /** 배열별 값, 없으면 배열별로 나누기 전의 값(legacy, 없으면 null), 그것도 없으면 def. */
    private static int oldInt(SharedPreferences sp, String key, String legacy, int def) {
        if (sp.contains(key)) return sp.getInt(key, def);
        return legacy != null ? sp.getInt(legacy, def) : def;
    }

    // ---------------------------------------------------------------- 좌·우 균형 배열

    private static void migrateReach(SharedPreferences sp, SharedPreferences.Editor e) {
        if (sp.contains(PORTRAIT_REACH)) return;
        boolean old = sp.contains(LANDSCAPE_REACH) ? sp.getBoolean(LANDSCAPE_REACH, false) : sp.getBoolean(LARGE_REACH, false);
        // 배열별 값이 없을 때 모든 배열이 이어 쓰는 값이다. 예전에도 모든 배열이 한 값을 함께 썼다.
        e.putBoolean(PORTRAIT_REACH, old);
        e.remove(LANDSCAPE_REACH).remove(LARGE_REACH);
    }

    // ---------------------------------------------------------------- 세로 위치 → 아래쪽 여백

    private static void migrateLift(SharedPreferences sp, SharedPreferences.Editor e) {
        String[][] lifts = {{"lift_land", Prefs.PROFILE_LANDSCAPE}, {"large_lift_port", Prefs.PROFILE_LARGE_PORTRAIT},
                {"large_lift_land", Prefs.PROFILE_LARGE_LANDSCAPE}};
        int portraitBottom = sp.getInt(Prefs.PAD_BOTTOM, 4);
        for (String[] l : lifts) {
            int lift = clamp(sp.getInt(l[0], 0), 0, Prefs.LEGACY_LIFT_MAX);
            e.remove(l[0]);
            if (lift == 0) continue;
            String padKey = Prefs.profilePadKeyFor(l[1], "b");
            int bottom = clamp(sp.getInt(padKey, portraitBottom), 0, Prefs.PAD_BOTTOM_MAX);
            e.putInt(padKey, bottom + lift);
        }
    }

    // ---------------------------------------------------------------- 한 손 모드 아래쪽 여백

    /**
     * 예전 한 손 모드: 키보드 아래쪽 여백(모든 방향이 함께 쓰던 한 값) 아래에 세로 위치만큼 띄웠다. 이제는 한 손 모드 아래쪽
     * 여백 하나이고, 가로 모드·폴더블 내부 화면은 정하기 전에는 이 값을 따르므로 이 값 하나에 더하면 모든 방향이 예전 자리다.
     */
    private static void migrateOneHandBottom(SharedPreferences sp, SharedPreferences.Editor e) {
        int lift = clamp(sp.getInt(Prefs.ONE_HAND_LIFT, Prefs.ONE_HAND_LIFT_DEFAULT), 0, Prefs.LEGACY_LIFT_MAX);
        int bottom = clamp(sp.getInt(Prefs.PAD_BOTTOM, 4), 0, Prefs.PAD_BOTTOM_MAX);
        if (bottom > 0) e.putInt(Prefs.ONE_HAND_LIFT, lift + bottom);
    }

    private static int clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }
}
