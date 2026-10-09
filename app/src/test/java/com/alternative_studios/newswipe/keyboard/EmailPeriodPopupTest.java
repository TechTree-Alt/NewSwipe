package com.alternative_studios.newswipe.keyboard;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class EmailPeriodPopupTest {
    private static Key period(KeyboardLayout l) {
        for (KeyboardLayout.Row row : l.rows) {
            for (Key k : row.keys) if (k.type == Key.CHAR && ".".equals(k.label)) return k;
        }
        return null;
    }

    @Test
    public void emailFieldsReplaceThePeriodPopupWithDomains() {
        for (KeyboardLayout l : new KeyboardLayout[]{KeyboardLayout.korean(null), KeyboardLayout.english(null)}) {
            Key k = period(l);
            assertTrue(k != null);
            String[] before = k.popup;
            assertNotEquals(".com", before[0]);
            KeyboardLayout.useEmailPeriodPopup(l);
            assertArrayEquals(new String[]{".com", ".net", ".org", ".co.kr", ".kr", ".ac.kr"}, k.popup);
        }
    }

    @Test
    public void otherKeysAreUntouched() {
        KeyboardLayout l = KeyboardLayout.english(null);
        String[] aPopup = null;
        for (KeyboardLayout.Row row : l.rows) for (Key k : row.keys) if ("a".equals(k.label)) aPopup = k.popup;
        KeyboardLayout.useEmailPeriodPopup(l);
        for (KeyboardLayout.Row row : l.rows) for (Key k : row.keys) if ("a".equals(k.label)) assertEquals(aPopup, k.popup);
    }
}
