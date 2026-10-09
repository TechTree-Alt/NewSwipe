package com.alternative_studios.newswipe.keyboard;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class BottomKeysTest {
    @Test
    public void missingOrderIsDefault() {
        assertArrayEquals(BottomKeys.DEFAULT_ORDER, BottomKeys.order(null));
        assertArrayEquals(BottomKeys.DEFAULT_ORDER, BottomKeys.order(""));
    }

    @Test
    public void storedOrderIsKept() {
        String[] o = {"enter", "space", "mode", "period", "globe", "comma"};
        assertArrayEquals(o, BottomKeys.order(BottomKeys.join(o)));
    }

    @Test
    public void unknownDuplicateAndMissingKeysAreFixed() {
        // 모르는 키와 중복은 버리고, 빠진 키는 기본 순서대로 뒤에 붙인다.
        assertArrayEquals(new String[]{"space", "enter", "mode", "comma", "globe", "period"},
                BottomKeys.order("space, nope,enter,space"));
        assertEquals(6, BottomKeys.order("mode,mode,mode").length);
    }
}
