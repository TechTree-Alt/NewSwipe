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
        String[] o = {"enter", "space", "mode", "period", "globe", "comma", "voice", "emoji", "cursor", "clipboard"};
        assertArrayEquals(o, BottomKeys.order(BottomKeys.join(o)));
        // 더할 수 있는 키가 생기기 전에 저장한 여섯 키의 순서: 그대로 두고 새 키는 뒤에 붙인다
        String[] old = {"enter", "space", "mode", "period", "globe", "comma"};
        assertArrayEquals(new String[]{"enter", "space", "mode", "period", "globe", "comma",
                "emoji", "clipboard", "cursor", "voice"}, BottomKeys.order(BottomKeys.join(old)));
    }

    @Test
    public void unknownDuplicateAndMissingKeysAreFixed() {
        // 모르는 키와 중복은 버리고, 빠진 키는 기본 순서대로 뒤에 붙인다.
        assertArrayEquals(new String[]{"space", "enter", "mode", "comma", "globe", "period",
                        "emoji", "clipboard", "cursor", "voice"},
                BottomKeys.order("space, nope,enter,space"));
        assertEquals(10, BottomKeys.order("mode,mode,mode").length);
    }
}
