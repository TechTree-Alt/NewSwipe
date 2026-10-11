package com.alternative_studios.newswipe;

import android.text.InputType;

/** 입력란 종류(EditorInfo.inputType)를 가려 보는 도움 함수. */
final class InputFields {
    private InputFields() {
    }

    /** 비밀번호 입력란인지: 글자 비밀번호(보이는 비밀번호·웹 비밀번호 포함)와 숫자 비밀번호(PIN). */
    static boolean isPassword(int inputType) {
        int cls = inputType & InputType.TYPE_MASK_CLASS;
        int variation = inputType & InputType.TYPE_MASK_VARIATION;
        if (cls == InputType.TYPE_CLASS_TEXT) {
            return variation == InputType.TYPE_TEXT_VARIATION_PASSWORD
                    || variation == InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
                    || variation == InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD;
        }
        return cls == InputType.TYPE_CLASS_NUMBER && variation == InputType.TYPE_NUMBER_VARIATION_PASSWORD;
    }
}
