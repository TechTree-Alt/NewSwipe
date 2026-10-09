package com.alternative_studios.newswipe.keyboard;

import com.alternative_studios.newswipe.Prefs;

import java.util.function.BiFunction;

/**
 * 밀어서 기능 배치(기능키·도구 막대·키보드 밀기)의 공통 틀. 칸(키·버튼·손가락 수) × 방향마다 기능(SwipeAction ID)을 하나씩 둔다.
 * 저장값을 풀어내는 규칙과 기본값은 배치마다 다르고, 저장·불러오기와 편집 화면에 필요한 정보는 여기서 한 가지 방식으로 다룬다.
 * 저장 키는 "swipe_" + 묶음 이름 + "_" + 칸 이름 + "_" + 방향 이름이다 ({@link Prefs#swipeActionOverride}).
 */
public abstract class SwipeSpec {
    /** 설정 키에 쓰는 묶음 이름 (예: "fn"). */
    public final String group;
    private final String[] slotNames;

    protected SwipeSpec(String group, String[] slotNames) {
        this.group = group;
        this.slotNames = slotNames;
    }

    public final int slotCount() {
        return slotNames.length;
    }

    public final String slotName(int slot) {
        return slotNames[slot];
    }

    /** 정하지 않았을 때의 기능. */
    public abstract String defaultAction(int slot, int dir);

    /** 저장된 값(정한 적이 없으면 null)을 실제 기능으로 풀어낸다. 모르는 값이나 그 칸에서 쓸 수 없는 기능은 걸러 낸다. */
    public abstract String resolve(String stored, int slot, int dir);

    /** 저장된 값들로 칸 × 방향(Key.SWIPE_*) 표를 만든다. overrides: (칸 이름, 방향 이름) → 저장값 (없으면 null). */
    public final String[][] table(BiFunction<String, String, String> overrides) {
        String[][] t = new String[slotNames.length][4];
        for (int slot = 0; slot < slotNames.length; slot++) {
            for (int dir = 0; dir < 4; dir++) {
                String stored = overrides == null ? null : overrides.apply(slotNames[slot], dirKey(slot, dir));
                t[slot][dir] = resolve(stored, slot, dir);
            }
        }
        return t;
    }

    /** 설정에 저장된 배치로 만든 표. */
    public final String[][] table(Prefs prefs) {
        return table(prefs == null ? null : (slot, dir) -> prefs.swipeActionOverride(group, slot, dir));
    }

    /** 편집 화면용: 한 칸·방향의 저장된 값을 풀어낸 기능. */
    public final String get(Prefs prefs, int slot, int dir) {
        String stored = prefs == null ? null
                : prefs.swipeActionOverride(group, slotNames[slot], dirKey(slot, dir));
        return resolve(stored, slot, dir);
    }

    public final void set(Prefs prefs, int slot, int dir, String actionId) {
        prefs.setSwipeActionOverride(group, slotNames[slot], dirKey(slot, dir), actionId);
    }

    public final void reset(Prefs prefs, int slot, int dir) {
        prefs.resetSwipeActionOverride(group, slotNames[slot], dirKey(slot, dir));
    }

    /** 설정 키에 쓰는 방향 이름. 방향 자리를 다른 뜻(탭·길게 누르기 등)으로 쓰는 배치는 바꿔 쓴다. */
    protected String dirKey(int slot, int dir) {
        return SwipeCustom.dirName(dir);
    }

    // ---------------------------------------------------------------- 편집 화면

    /** 편집 화면 제목. */
    public abstract String title();

    /** 편집 화면 위쪽의 안내. */
    public abstract String note();

    /** 칸 카드의 이름. */
    public abstract String cardTitle(int slot);

    /** 칸 카드의 작은 설명. */
    public abstract String cardHint(int slot);

    /** 이 칸에서 정할 수 있는 방향들 (편집 화면에 보이는 순서). */
    public int[] dirs(int slot) {
        return SwipeCustom.DIRS;
    }

    /** 편집 화면의 칸 아래에 보이는 방향 이름. */
    public String dirLabel(int dir) {
        return SwipeCustom.label(dir);
    }

    /** 칸마다 방향 이름이 다른 배치를 위한 것. 기본은 {@link #dirLabel(int)}. */
    public String dirLabel(int slot, int dir) {
        return dirLabel(dir);
    }

    /** 이 칸에서 고를 수 있는 기능 ID들 (목록에 보이는 순서). */
    public abstract String[] choices(int slot);

    /** 칸·방향마다 고를 수 있는 기능이 다른 배치를 위한 것. 기본은 {@link #choices(int)}. */
    public String[] choices(int slot, int dir) {
        return choices(slot);
    }

    /** 칸·방향을 눌렀을 때 뜨는 기능 목록 창의 제목. */
    public String dialogTitle(int slot, int dir) {
        return "'" + cardTitle(slot) + "' " + SwipeCustom.label(dir) + "으로 밀기";
    }
}
