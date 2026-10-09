package com.alternative_studios.newswipe;

import android.app.Activity;
import android.content.Context;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.alternative_studios.newswipe.suggest.WordSuggester;
import com.alternative_studios.newswipe.ui.Ui;

/** 설정 화면 맨 아래의 '기본값으로 되돌리기'. 확인을 거친 뒤에만 되돌린다. */
final class ResetDialog {
    private ResetDialog() {
    }

    /** 'OOO을(를) 기본값으로 되돌리겠습니까?'를 묻고, 확인하면 onConfirm을 부른다. */
    static void confirm(Context context, String name, Runnable onConfirm) {
        AppTheme.accentBuilder(AppTheme.dialogContext(context))
                .setTitle("기본값으로 되돌리기")
                .setMessage(name + WordSuggester.objectParticle(name) + " 기본값으로 되돌리겠습니까?")
                .setPositiveButton("확인", (d, w) -> onConfirm.run())
                .setNegativeButton("취소", null)
                .show();
    }

    /** 편집 화면 맨 아래에 놓는 버튼 (이 화면이 담당하는 값만 되돌리는 onReset을 넘긴다). */
    static TextView button(Activity activity, String name, Runnable onReset) {
        int accent = AppTheme.of(activity).accent;
        TextView reset = new TextView(activity);
        reset.setText("기본값으로 되돌리기");
        reset.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 15);
        reset.setTextColor(accent);
        reset.setGravity(Gravity.CENTER);
        reset.setPadding(0, Ui.dp(activity, 14), 0, Ui.dp(activity, 14));
        reset.setBackground(Ui.ripple(accent & 0x00FFFFFF | 0x33000000,
                Ui.round(accent & 0x00FFFFFF | 0x1F000000, Ui.dp(activity, 12)), Ui.dp(activity, 12)));
        reset.setOnClickListener(v -> confirm(activity, name, onReset));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = Ui.dp(activity, 24);
        reset.setLayoutParams(lp);
        return reset;
    }
}
