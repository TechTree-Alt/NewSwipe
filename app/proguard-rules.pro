# NewSwipe는 리플렉션·XML 레이아웃·외부 라이브러리를 쓰지 않는다. 안드로이드 기본 규칙 파일 대신 이것만 쓴다 (app/build.gradle.kts).
# 키보드 서비스와 설정 화면은 AndroidManifest.xml에 등록되어 있어 자동으로 보존된다.
-repackageclasses
-allowaccessmodification
# 아래는 기본 규칙 파일에서 가져온 것: 네이티브 메서드, 열거형(EnumSet·EnumMap이 values()를 쓴다), Parcelable.
-keepclasseswithmembernames,includedescriptorclasses class * {
    native <methods>;
}
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}
-keepclassmembers class * implements android.os.Parcelable {
    public static final ** CREATOR;
}
