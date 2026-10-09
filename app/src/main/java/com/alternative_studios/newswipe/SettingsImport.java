package com.alternative_studios.newswipe;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Toast;

import com.alternative_studios.newswipe.ui.Ui;

/**
 * 설정 가져오기: 파일 고르기 → (암호화돼 있으면) 비밀번호 → 확인 → 적용.
 * 설정 화면의 '설정 가져오기 및 내보내기'와 첫 시작 가이드의 '설정 가져오기'가 같이 쓴다.
 */
final class SettingsImport {
    private SettingsImport() {
    }

    /** 파일 고르기 창을 연다. 결과는 activity의 onActivityResult로 오며, 그때 {@link #onPicked}를 부른다. */
    static void pick(Activity activity, int requestCode) {
        activity.startActivityForResult(
                new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*"),
                requestCode);
    }

    /**
     * 고른 파일을 읽어 가져온다.
     *
     * @param onApplied 설정을 문제없이 적용한 뒤에 부를 작업 (화면 다시 만들기 등)
     */
    static void onPicked(Activity activity, Uri uri, Runnable onApplied) {
        String text;
        try {
            text = SettingsBackup.read(activity, uri);
        } catch (Exception e) {
            Toast.makeText(activity, "가져오지 못했습니다: " + e.getMessage(), Toast.LENGTH_LONG).show();
            return;
        }
        if (SettingsBackup.isEncrypted(text)) askPassword(activity, text, onApplied);
        else confirm(activity, text, onApplied);
    }

    /** 암호화한 백업의 비밀번호를 묻고, 풀리면 가져오기를 확인한다. 틀리면 다시 묻는다. */
    private static void askPassword(Activity activity, String text, Runnable onApplied) {
        Context dc = AppTheme.dialogContext(activity);
        LinearLayout box = new LinearLayout(dc);
        box.setOrientation(LinearLayout.VERTICAL);
        int pad = Ui.dp(activity, 20);
        box.setPadding(pad, Ui.dp(activity, 4), pad, 0);
        EditText pw = passwordField(dc, "비밀번호");
        box.addView(pw);
        AppTheme.accentBuilder(dc)
                .setTitle("암호화된 백업 파일")
                .setMessage("백업 파일을 내보낼 때 정한 비밀번호를 입력하세요.")
                .setView(box)
                .setPositiveButton("확인", (d, w) -> {
                    char[] password = chars(pw);
                    pw.getText().clear();
                    Toast.makeText(activity, "암호를 푸는 중입니다…", Toast.LENGTH_SHORT).show();
                    new Thread(() -> {
                        String plain = null, error = null;
                        boolean wrong = false;
                        try {
                            plain = SettingsBackup.decrypt(text, password);
                        } catch (BackupCrypto.WrongPasswordException e) {
                            wrong = true;
                        } catch (Exception e) {
                            error = e.getMessage();
                        } finally {
                            java.util.Arrays.fill(password, '\0');
                        }
                        String p = plain, err = error;
                        boolean wr = wrong;
                        activity.runOnUiThread(() -> {
                            if (activity.isFinishing() || activity.isDestroyed()) return;
                            if (p != null) {
                                confirm(activity, p, onApplied);
                            } else if (wr) {
                                Toast.makeText(activity, "비밀번호가 맞지 않습니다", Toast.LENGTH_SHORT).show();
                                askPassword(activity, text, onApplied);
                            } else {
                                Toast.makeText(activity, "가져오지 못했습니다: " + err, Toast.LENGTH_LONG).show();
                            }
                        });
                    }).start();
                })
                .setNegativeButton("취소", null)
                .show();
    }

    /** 가져올 내용(풀린 JSON)을 확인받고 적용한다. */
    private static void confirm(Activity activity, String json, Runnable onApplied) {
        AppTheme.dialogBuilder(activity)
                .setTitle("설정 가져오기")
                .setMessage("현재 설정을 백업 파일의 내용으로 바꿉니다. 현재 설정은 사라지니, 필요하면 먼저 내보내 두세요.")
                .setPositiveButton("가져오기", (d, w) -> {
                    try {
                        int[] n = SettingsBackup.apply(activity, json);
                        Toast.makeText(activity, "설정 " + n[0] + "개를 가져왔습니다"
                                + (n[1] >= 0 ? " (학습한 단어 " + n[1] + "개 포함)" : ""), Toast.LENGTH_SHORT).show();
                        d.dismiss();
                        onApplied.run();
                    } catch (Exception e) {
                        Toast.makeText(activity, "가져오지 못했습니다: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    }
                })
                .setNegativeButton("취소", null)
                .show();
    }

    /** 비밀번호 입력란 (가려진 글자). */
    static EditText passwordField(Context dc, String hint) {
        EditText e = new EditText(dc);
        e.setHint(hint);
        e.setSingleLine(true);
        e.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
        e.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO);
        return e;
    }

    static char[] chars(EditText e) {
        android.text.Editable t = e.getText();
        char[] out = new char[t.length()];
        t.getChars(0, t.length(), out, 0);
        return out;
    }
}
