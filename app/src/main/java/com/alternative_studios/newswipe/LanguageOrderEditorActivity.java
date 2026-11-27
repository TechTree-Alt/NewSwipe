package com.alternative_studios.newswipe;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.widget.LinearLayout;
import android.widget.ScrollView;

import com.alternative_studios.newswipe.ui.Ui;

/**
 * 입력 언어 화면: 켜고 끌 언어와 '🌐︎을 위로 밀어 언어 고르기'의 말풍선에 놓을 언어 차례를 정한다 (내용은 {@link LanguageOrderEditor}).
 * 한국어·영어와, 실험실에서 켜고 사전 팩이 있는 일본어·중국어를 끌어서 옮긴다. 말풍선은 이 차례대로 위에서 아래로 보인다.
 * 화면 구성과 끌어서 옮기기는 '기능키 순서·유무 사용자화'(BottomKeysEditorActivity)와 같다.
 * Metro UI에서는 이 화면 대신 설정 화면의 피벗 한 칸으로 연다 ({@link #intent}).
 */
public final class LanguageOrderEditorActivity extends Activity {
    private String themeSignature;

    private static String title() {
        return L.t("언어", "Languages");
    }

    /** 입력 언어 화면을 여는 인텐트: Metro UI에서는 다른 설정 메뉴와 가로로 밀어 오갈 수 있게 설정 화면의 한 칸으로 연다. */
    static Intent intent(Context c) {
        if (new Prefs(c).metro()) {
            return new Intent(c, SettingsActivity.class).putExtra(SettingsActivity.EXTRA_SECTION, SettingsActivity.LANGUAGES);
        }
        return new Intent(c, LanguageOrderEditorActivity.class);
    }

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(AppTheme.wrap(base));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        AppTheme colors = AppTheme.of(this);
        themeSignature = AppTheme.signature(this);
        ScrollView scroll = new ScrollView(this);
        com.alternative_studios.newswipe.ui.Metro.scrollIndicator(scroll);   // Metro: WP8처럼 끄는 동안만 보이는 가는 표시
        scroll.setBackgroundColor(colors.bg);
        scroll.setFillViewport(true);
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        int pad = Ui.dp(this, 16);
        list.setPadding(pad, 0, pad, Ui.dp(this, 32));
        scroll.addView(list);
        setContentView(new SettingsFrame(this, title(), getString(R.string.app_name)).wrap(scroll));
        new LanguageOrderEditor(this, list).render();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (!themeSignature.equals(AppTheme.signature(this))) recreate();
    }

    @Override
    public void onBackPressed() {
        if (!SettingsFrame.consumeBack(this) && !SettingsFrame.metroBack(this)) super.onBackPressed();
    }
}
