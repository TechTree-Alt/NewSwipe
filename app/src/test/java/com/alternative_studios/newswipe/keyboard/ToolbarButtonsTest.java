package com.alternative_studios.newswipe.keyboard;

import static org.junit.Assert.assertArrayEquals;

import org.junit.Test;

public class ToolbarButtonsTest {
    @Test
    public void defaultsWhenNothingStored() {
        String[][] o = ToolbarButtons.order(null);
        assertArrayEquals(ToolbarButtons.DEFAULT_LEFT, o[0]);
        assertArrayEquals(ToolbarButtons.DEFAULT_RIGHT, o[1]);
    }

    @Test
    public void roundTripsBothSides() {
        String[] left = {"settings", "clipboard"};
        String[] right = {"hide", "emoji", "voice", "undo", "onehand"};
        String[][] o = ToolbarButtons.order(ToolbarButtons.join(left, right));
        assertArrayEquals(left, o[0]);
        assertArrayEquals(right, o[1]);
    }

    @Test
    public void emptySideIsKept() {
        String[][] o = ToolbarButtons.order(ToolbarButtons.join(ToolbarButtons.ALL, new String[0]));
        assertArrayEquals(ToolbarButtons.ALL, o[0]);
        assertArrayEquals(new String[0], o[1]);
    }

    @Test
    public void dropsUnknownAndDuplicatesAndAddsMissingToDefaultSide() {
        String[][] o = ToolbarButtons.order("emoji,bogus,emoji|clipboard,emoji");
        assertArrayEquals(new String[]{"emoji", "voice", "undo"}, o[0]);
        assertArrayEquals(new String[]{"clipboard", "onehand", "settings", "hide"}, o[1]);
    }

    @Test
    public void oneHandButtonIsAddedToSavedOrders() {
        // 한 손 모드 버튼이 생기기 전에 저장한 배치에는 오른쪽 끝에 붙는다.
        String[][] o = ToolbarButtons.order("clipboard,emoji,voice,undo|settings,hide");
        assertArrayEquals(new String[]{"clipboard", "emoji", "voice", "undo"}, o[0]);
        assertArrayEquals(new String[]{"settings", "hide", "onehand"}, o[1]);
        org.junit.Assert.assertEquals(Icons.ONE_HAND, ToolbarButtons.icon(ToolbarButtons.ONE_HAND));
    }
}
