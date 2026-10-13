#!/bin/bash
# 한국어 추천 평가 (tools/eval/SuggestEval.java). Android SDK 없이 JDK만으로 앱의 추천 코드를 컴파일해 돌린다.
#   tools/eval/run_eval.sh DICT_DIR TEST_FILE [SuggestEval 옵션...]
# 예: tools/eval/run_eval.sh app/src/main/assets tools/eval/ko_chat_test.txt
#     tools/eval/run_eval.sh app/src/main/assets tools/eval/ko_chat_test.txt --old
set -e
ROOT=$(cd "$(dirname "$0")/../.." && pwd)
OUT=${EVAL_OUT:-$(mktemp -d)}
SRC=$ROOT/app/src/main/java/com/alternative_studios/newswipe
STUB=$OUT/stub/android/content
mkdir -p "$STUB/res"
# WordSuggester가 쓰는 Android 클래스만 흉내 낸다 (평가에서는 쓰지 않는다).
echo 'package android.content; public abstract class Context { public abstract Context getApplicationContext(); public abstract android.content.res.AssetManager getAssets(); }' > "$STUB/Context.java"
echo 'package android.content.res; public abstract class AssetManager { public abstract java.io.InputStream open(String n) throws java.io.IOException; }' > "$STUB/res/AssetManager.java"
javac -nowarn -encoding UTF-8 -d "$OUT/classes" "$STUB/Context.java" "$STUB/res/AssetManager.java" \
    "$SRC"/suggest/*.java "$SRC"/hangul/*.java "$ROOT/tools/eval/SuggestEval.java" 2>&1 | grep -v '^Note:\|JAVA_TOOL_OPTIONS' || true
java -Xmx2g -Dstdout.encoding=UTF-8 -Dfile.encoding=UTF-8 -cp "$OUT/classes" com.alternative_studios.newswipe.suggest.SuggestEval "$@" 2>&1 | grep -v JAVA_TOOL_OPTIONS
