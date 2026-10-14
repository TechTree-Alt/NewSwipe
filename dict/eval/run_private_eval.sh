#!/bin/bash
# 공개할 수 없는 말뭉치(국립국어원 메신저 말뭉치 등)로 추천을 평가한다. 문장은 임시 폴더에만 두고 끝나면 지운다.
# 화면에는 집계(키 절약률 등)만 찍으므로, 그 결과는 다른 곳에 옮겨 적어도 원문이 나가지 않는다.
#
#   dict/eval/run_private_eval.sh 말뭉치_폴더_또는_zip [사전팩.zip]
#
# 환경 변수: FIELD=original_form (발화의 다른 키를 쓸 때), MAX=20000 (나눈 쪽마다 최대 문장 수),
#            LEARN_MAX=3000 (학습 평가에 쓸 문장 수),
#            DEVICE=스마트폰 KEYBOARD=2벌식 (화자의 기기·자판으로 거를 때, 국립국어원 메신저 말뭉치)
set -e
ROOT=$(cd "$(dirname "$0")/../.." && pwd)
CORPUS=$1
PACK=$2
if [ -z "$CORPUS" ]; then
    echo "사용법: $0 말뭉치_폴더_또는_zip [사전팩.zip]" >&2
    exit 2
fi
# 예시 문장을 찍는 평가 옵션은 끈다
unset SHOW_MADE SHOW_WRONG WHY
TMP=$(mktemp -d)
trap 'rm -rf "$TMP"' EXIT

python3 "$ROOT/dict/eval/nikl_extract.py" "$CORPUS" "$TMP/text" --field "${FIELD:-form}" --max "${MAX:-20000}" \
    --device "${DEVICE:-}" --keyboard "${KEYBOARD:-}"

# 평가기를 한 번 컴파일한다 (run_eval.sh와 같은 방법)
SRC=$ROOT/app/src/main/java/com/alternative_studios/newswipe
STUB=$TMP/stub/android/content
mkdir -p "$STUB/res" "$TMP/classes"
echo 'package android.content; public abstract class Context { public abstract Context getApplicationContext(); public abstract android.content.res.AssetManager getAssets(); public abstract java.io.File getFilesDir(); }' > "$STUB/Context.java"
echo 'package android.content.res; public abstract class AssetManager { public abstract java.io.InputStream open(String n) throws java.io.IOException; }' > "$STUB/res/AssetManager.java"
javac -nowarn -encoding UTF-8 -d "$TMP/classes" "$STUB/Context.java" "$STUB/res/AssetManager.java" \
    "$SRC"/suggest/*.java "$SRC"/hangul/*.java "$ROOT/dict/eval/SuggestEval.java" 2>&1 | grep -v '^Note:\|JAVA_TOOL_OPTIONS' || true

run() {
    java -Xmx3g -Dstdout.encoding=UTF-8 -Dfile.encoding=UTF-8 -cp "$TMP/classes" \
        com.alternative_studios.newswipe.suggest.SuggestEval "$@" 2>&1 \
        | grep -E '^(문장|키 절약률|예측|잘못 고침|만들어 낸|오타|읽기)' || true
}

DIRS=("$ROOT/app/src/main/assets")
NAMES=("기본 사전")
if [ -n "$PACK" ]; then
    mkdir -p "$TMP/pack"
    unzip -q -o "$PACK" -d "$TMP/pack"
    DIRS+=("$TMP/pack")
    NAMES+=("사전 팩")
fi

for i in "${!DIRS[@]}"; do
    for side in dev test; do
        echo "== ${NAMES[$i]} / $side"
        run "${DIRS[$i]}" "$TMP/text/$side.txt"
    done
    echo "== ${NAMES[$i]} / test, 쓰면서 학습 (앞 ${LEARN_MAX:-3000}문장)"
    run "${DIRS[$i]}" "$TMP/text/test.txt" --learn --no-typo --max "${LEARN_MAX:-3000}"
done
