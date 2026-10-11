package com.alternative_studios.newswipe;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.text.InputType;

import org.junit.Test;

/** 자동 완성 칩을 '비밀번호 입력란에서만' 보여 줄 때 쓰는 비밀번호 칸 판별. */
public class InputFieldsTest {
    @Test
    public void passwordVariationsArePasswordFields() {
        int text = InputType.TYPE_CLASS_TEXT;
        assertTrue(InputFields.isPassword(text | InputType.TYPE_TEXT_VARIATION_PASSWORD));
        assertTrue(InputFields.isPassword(text | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD));
        assertTrue(InputFields.isPassword(text | InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD));
        assertTrue(InputFields.isPassword(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD));
        // 다른 플래그가 붙어도 비밀번호 칸이다.
        assertTrue(InputFields.isPassword(text | InputType.TYPE_TEXT_VARIATION_PASSWORD
                | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS));
    }

    @Test
    public void otherFieldsAreNotPasswordFields() {
        int text = InputType.TYPE_CLASS_TEXT;
        assertFalse(InputFields.isPassword(text));
        assertFalse(InputFields.isPassword(text | InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS));
        assertFalse(InputFields.isPassword(text | InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS));
        assertFalse(InputFields.isPassword(text | InputType.TYPE_TEXT_VARIATION_URI));
        assertFalse(InputFields.isPassword(InputType.TYPE_CLASS_NUMBER));
        assertFalse(InputFields.isPassword(InputType.TYPE_CLASS_PHONE));
        assertFalse(InputFields.isPassword(InputType.TYPE_NULL));
    }
}
