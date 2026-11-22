# 키보드 서비스와 설정 화면은 AndroidManifest.xml에 등록되어 있어 자동으로 보존된다.
-repackageclasses
# Mozc JNI는 등록 시 클래스와 메서드 이름을, Protobuf Lite는 직렬화 시 필드 이름을 사용한다.
-keep class com.google.android.apps.inputmethod.libs.mozc.session.MozcJni { *; }
-keepclassmembers class org.mozc.android.inputmethod.japanese.protobuf.** extends com.google.protobuf.GeneratedMessageLite { <fields>; }
