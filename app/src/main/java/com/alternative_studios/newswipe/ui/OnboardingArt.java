package com.alternative_studios.newswipe.ui;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.view.View;
import android.view.animation.LinearInterpolator;
import android.view.animation.PathInterpolator;

/**
 * 첫 시작 가이드의 그림들. 모두 코드로 그리고, 화면에 보일 때만 움직인다.
 * 시스템에서 애니메이션을 끄면 (개발자 옵션의 애니메이터 길이 배율 0 등) 움직이지 않는 그림으로 보여 준다.
 */
public final class OnboardingArt {
    private OnboardingArt() {
    }

    /** Material 3 Expressive의 강조 곡선 (빠르게 출발해 천천히 멈춤). */
    static final PathInterpolator EMPHASIZED = new PathInterpolator(0.05f, 0.7f, 0.1f, 1f);

    /** 화면에 보이는 동안 0→1을 되풀이하는 시계를 가진 뷰. */
    abstract static class Looping extends View {
        private final long period;
        private ValueAnimator clock;
        /** 이번 주기에서의 위치 (0~1). */
        protected float phase;
        /** 지금까지 지난 주기 수. */
        protected int cycle;

        Looping(Context c, long period) {
            super(c);
            this.period = period;
            setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        }

        static boolean motionEnabled() {
            return ValueAnimator.areAnimatorsEnabled();
        }

        @Override
        public void onVisibilityAggregated(boolean isVisible) {
            super.onVisibilityAggregated(isVisible);
            if (isVisible) start();
            else stop();
        }

        @Override
        protected void onDetachedFromWindow() {
            stop();
            super.onDetachedFromWindow();
        }

        /** 처음부터 다시 시작한다 (화면에 다시 들어올 때). */
        public void restart() {
            stop();
            cycle = 0;
            phase = 0f;
            if (isShown()) start();
            invalidate();
        }

        private void start() {
            if (clock != null || !motionEnabled()) {
                invalidate();
                return;
            }
            clock = ValueAnimator.ofFloat(0f, 1f);
            clock.setDuration(period);
            clock.setRepeatCount(ValueAnimator.INFINITE);
            clock.setInterpolator(new LinearInterpolator());
            clock.addUpdateListener(a -> {
                phase = (float) a.getAnimatedValue();
                invalidate();
            });
            clock.addListener(new android.animation.AnimatorListenerAdapter() {
                @Override
                public void onAnimationRepeat(android.animation.Animator animation) {
                    cycle++;
                }
            });
            clock.start();
        }

        private void stop() {
            if (clock != null) {
                clock.cancel();
                clock = null;
            }
        }
    }

    // ---------------------------------------------------------------- 모양 배지

    /**
     * Material 3 Expressive 모양 라이브러리의 '쿠키'처럼 둘레가 물결치는 모양. 천천히 돈다.
     * 가운데에 체크 표시를 그릴 수 있다 (마지막 화면).
     */
    public static final class ShapeBadge extends Looping {
        private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint check = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path shape = new Path();
        private final Path checkPath = new Path();
        private final int lobes;
        private final float depth;
        private boolean showCheck;
        private float checkProgress = 1f;
        private float lastSize = -1f;

        /**
         * @param lobes 물결 수
         * @param depth 물결 깊이 (반지름에 대한 비율, 0.04~0.12쯤)
         */
        public ShapeBadge(Context c, int color, int lobes, float depth) {
            super(c, 24000);
            this.lobes = lobes;
            this.depth = depth;
            fill.setColor(color);
            check.setStyle(Paint.Style.STROKE);
            check.setStrokeCap(Paint.Cap.ROUND);
            check.setStrokeJoin(Paint.Join.ROUND);
        }

        /** 가운데에 체크 표시를 그린다. */
        public void setCheck(int color) {
            showCheck = true;
            check.setColor(color);
        }

        /** 체크 표시를 획 순서대로 그려 나간다 (0 = 없음, 1 = 다 그림). */
        public void setCheckProgress(float p) {
            checkProgress = p;
            invalidate();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            float w = getWidth(), h = getHeight();
            float size = Math.min(w, h);
            if (size != lastSize) {
                lastSize = size;
                buildShape(size / 2f);
            }
            canvas.save();
            canvas.translate(w / 2f, h / 2f);
            canvas.save();
            canvas.rotate(phase * 360f);
            canvas.drawPath(shape, fill);
            canvas.restore();
            if (showCheck && checkProgress > 0f) {
                float r = size / 2f;
                check.setStrokeWidth(r * 0.14f);
                float x0 = -r * 0.36f, y0 = r * 0.02f, x1 = -r * 0.1f, y1 = r * 0.27f, x2 = r * 0.38f, y2 = -r * 0.24f;
                float l1 = (float) Math.hypot(x1 - x0, y1 - y0), l2 = (float) Math.hypot(x2 - x1, y2 - y1);
                float d = Math.max(0f, Math.min(1f, checkProgress)) * (l1 + l2);
                checkPath.rewind();
                checkPath.moveTo(x0, y0);
                if (d <= l1) {
                    checkPath.lineTo(x0 + (x1 - x0) * d / l1, y0 + (y1 - y0) * d / l1);
                } else {
                    checkPath.lineTo(x1, y1);
                    float e = (d - l1) / l2;
                    checkPath.lineTo(x1 + (x2 - x1) * e, y1 + (y2 - y1) * e);
                }
                canvas.drawPath(checkPath, check);
            }
            canvas.restore();
        }

        private void buildShape(float radius) {
            shape.rewind();
            int steps = 360;
            for (int i = 0; i <= steps; i++) {
                double t = 2 * Math.PI * i / steps;
                double r = radius * (1 - depth + depth * Math.cos(lobes * t));
                float x = (float) (r * Math.cos(t)), y = (float) (r * Math.sin(t));
                if (i == 0) shape.moveTo(x, y);
                else shape.lineTo(x, y);
            }
            shape.close();
        }
    }

    // ---------------------------------------------------------------- 키를 밀거나 두 번 탭하는 시연

    /** 시연 한 단계: 한 줄의 키들 중 index번째 키를 아래로 밀거나 두 번 탭해 result를 입력한다. */
    public static final class Step {
        final String[] row;
        final int index;
        final String result;
        final boolean doubleTap;

        public Step(String[] row, int index, String result, boolean doubleTap) {
            this.row = row;
            this.index = index;
            this.result = result;
            this.doubleTap = doubleTap;
        }
    }

    /**
     * 자판 한 줄을 그려 놓고, 손가락이 키를 아래로 밀거나 두 번 탭하면 위에 결과 글자 말풍선이 튀어나오는 시연.
     * 단계를 차례로 되풀이한다.
     */
    public static final class KeyDemo extends Looping {
        private static final long STEP_MS = 2400;

        private final Step[] steps;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint captionPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF rect = new RectF();
        private final Path path = new Path();
        private final int plate, key, keyPressed, text, hint, accent, onAccent;
        private final float dp;

        /**
         * @param colors {키보드 바탕, 키, 누른 키, 글자, 보조 글자, 강조, 강조 위 글자}
         */
        public KeyDemo(Context c, int[] colors, Step... steps) {
            super(c, STEP_MS * steps.length);
            this.steps = steps;
            plate = colors[0];
            key = colors[1];
            keyPressed = colors[2];
            text = colors[3];
            hint = colors[4];
            accent = colors[5];
            onAccent = colors[6];
            dp = Ui.dp(c, 1);
            textPaint.setTextAlign(Paint.Align.CENTER);
            captionPaint.setTextAlign(Paint.Align.CENTER);
            captionPaint.setColor(hint);
            captionPaint.setTextSize(android.util.TypedValue.applyDimension(android.util.TypedValue.COMPLEX_UNIT_SP, 13,
                    c.getResources().getDisplayMetrics()));
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            int w = MeasureSpec.getSize(widthMeasureSpec);
            setMeasuredDimension(w, Math.round(196 * dp));
        }

        @Override
        protected void onDraw(Canvas canvas) {
            boolean moving = motionEnabled();
            float total = phase * steps.length;
            int si = moving ? Math.min(steps.length - 1, (int) total) : 0;
            float t = moving ? total - si : 0.5f;   // 움직이지 않을 때는 말풍선이 뜬 순간을 보여 준다
            Step s = steps[si];

            float w = getWidth();
            int n = s.row.length;
            float plateW = Math.min(w, 340 * dp);
            float gap = 6 * dp, pad = 8 * dp;
            float keyW = (plateW - 2 * pad - gap * (n - 1)) / n;
            float keyH = 54 * dp;
            float left = (w - plateW) / 2f;
            float keyTop = 72 * dp;

            // 단계가 바뀔 때 줄 전체가 살짝 흐려졌다 나타난다.
            float rowAlpha = moving ? Math.min(1f, Math.min(t / 0.06f, (1f - t) / 0.06f)) : 1f;

            paint.setColor(plate);
            rect.set(left, keyTop - pad, left + plateW, keyTop + keyH + pad);
            canvas.drawRoundRect(rect, 18 * dp, 18 * dp, paint);

            // 누름 정도와 손가락 위치
            float press, fingerDy = 0f, fingerAlpha, ring = -1f, bubble;
            if (s.doubleTap) {
                float tap1 = window(t, 0.14f, 0.24f), tap2 = window(t, 0.32f, 0.42f);
                press = Math.max(tap1, tap2);
                fingerAlpha = ramp(t, 0.06f, 0.12f) * (1f - ramp(t, 0.5f, 0.58f));
                if (t >= 0.14f && t < 0.3f) ring = (t - 0.14f) / 0.16f;
                else if (t >= 0.32f && t < 0.48f) ring = (t - 0.32f) / 0.16f;
                bubble = popIn(t, 0.42f) * (1f - ramp(t, 0.84f, 0.92f));
            } else {
                press = ramp(t, 0.12f, 0.17f) * (1f - ramp(t, 0.5f, 0.55f));
                float move = EMPHASIZED.getInterpolation(ramp(t, 0.18f, 0.42f));
                fingerDy = move * keyH * 0.62f;
                fingerAlpha = ramp(t, 0.04f, 0.12f) * (1f - ramp(t, 0.52f, 0.62f));
                bubble = popIn(t, 0.3f) * (1f - ramp(t, 0.84f, 0.92f));
            }
            if (!moving) {
                press = 1f;
                fingerAlpha = 1f;
                fingerDy = s.doubleTap ? 0f : keyH * 0.62f;
                bubble = 1f;
            }

            textPaint.setTextSize(22 * dp);
            for (int i = 0; i < n; i++) {
                float kx = left + pad + i * (keyW + gap);
                boolean demo = i == s.index;
                float p = demo ? press : 0f;
                paint.setColor(blend(key, keyPressed, p));
                paint.setAlpha(Math.round(255 * (demo ? 1f : 0.55f + 0.45f * rowAlpha)));
                float squash = p * 1.5f * dp;
                rect.set(kx + squash, keyTop + squash, kx + keyW - squash, keyTop + keyH - squash);
                canvas.drawRoundRect(rect, 10 * dp, 10 * dp, paint);
                textPaint.setColor(demo ? text : blend(text, plate, 0.45f));
                textPaint.setAlpha(Math.round(255 * rowAlpha));
                drawCentered(canvas, s.row[i], kx + keyW / 2f, keyTop + keyH / 2f, textPaint);
            }

            float cx = left + pad + s.index * (keyW + gap) + keyW / 2f;
            float cy = keyTop + keyH / 2f;

            // 미는 자취: 손가락이 지나간 길을 강조색 선과 화살표로 남긴다.
            if (!s.doubleTap && fingerDy > 2 * dp) {
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeCap(Paint.Cap.ROUND);
                paint.setStrokeJoin(Paint.Join.ROUND);
                paint.setStrokeWidth(4 * dp);
                paint.setColor(accent);
                paint.setAlpha(Math.round(255 * fingerAlpha));
                float y0 = cy, y1 = cy + fingerDy;
                path.rewind();
                path.moveTo(cx, y0);
                path.lineTo(cx, y1);
                float a = Math.min(8 * dp, fingerDy * 0.5f);
                path.moveTo(cx - a, y1 - a);
                path.lineTo(cx, y1);
                path.lineTo(cx + a, y1 - a);
                canvas.drawPath(path, paint);
                paint.setStyle(Paint.Style.FILL);
            }

            // 두 번 탭: 탭마다 퍼지는 고리
            if (ring >= 0f) {
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(3 * dp);
                paint.setColor(accent);
                paint.setAlpha(Math.round(200 * (1f - ring)));
                canvas.drawCircle(cx, cy, 18 * dp + ring * 22 * dp, paint);
                paint.setStyle(Paint.Style.FILL);
            }

            // 손가락
            if (fingerAlpha > 0.01f) {
                float fy = cy + fingerDy;
                float r = 17 * dp * (1f - 0.08f * press);
                paint.setColor(text);
                paint.setAlpha(Math.round(0x40 * fingerAlpha));
                canvas.drawCircle(cx, fy, r, paint);
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(2 * dp);
                paint.setAlpha(Math.round(0x80 * fingerAlpha));
                canvas.drawCircle(cx, fy, r, paint);
                paint.setStyle(Paint.Style.FILL);
            }

            // 결과 글자 말풍선: 강조색 알약이 스프링으로 튀어나온다.
            if (bubble > 0.01f) {
                float bw = Math.max(keyW, 52 * dp), bh = 52 * dp;
                float by = keyTop - 14 * dp - bh / 2f;
                canvas.save();
                canvas.translate(cx, by + bh / 2f);
                canvas.scale(bubble, bubble);
                paint.setColor(accent);
                rect.set(-bw / 2f, -bh, bw / 2f, 0f);
                canvas.drawRoundRect(rect, bh / 2f, bh / 2f, paint);
                textPaint.setColor(onAccent);
                textPaint.setAlpha(255);
                textPaint.setTextSize(26 * dp);
                drawCentered(canvas, s.result, 0f, -bh / 2f, textPaint);
                canvas.restore();
            }

            // 아래 설명: 지금 단계의 동작
            String caption = s.doubleTap
                    ? s.row[s.index] + " 두 번 탭 → " + s.result
                    : s.row[s.index] + " 아래로 밀기 → " + s.result;
            captionPaint.setAlpha(Math.round(255 * rowAlpha));
            canvas.drawText(caption, w / 2f, keyTop + keyH + pad + 44 * dp, captionPaint);
        }

        private static void drawCentered(Canvas c, String s, float x, float y, Paint p) {
            Paint.FontMetrics fm = p.getFontMetrics();
            c.drawText(s, x, y - (fm.ascent + fm.descent) / 2f, p);
        }

        /** a~b 사이에서 0→1. */
        private static float ramp(float t, float a, float b) {
            if (t <= a) return 0f;
            if (t >= b) return 1f;
            return (t - a) / (b - a);
        }

        /** a~b 동안 눌렸다가 떼는 모양 (0→1→0). */
        private static float window(float t, float a, float b) {
            float m = (a + b) / 2f;
            return t < m ? ramp(t, a, m) : 1f - ramp(t, m, b);
        }

        /** at부터 스프링으로 튀어나온다 (0→1, 살짝 넘었다 돌아옴). */
        private static float popIn(float t, float at) {
            return Spring.BOUNCY.getInterpolation(ramp(t, at, at + 0.22f));
        }
    }

    // ---------------------------------------------------------------- 작은 키보드 (키보드 설정 화면)

    /** 단모음 자판을 작게 그린다. 처음 보일 때 키들이 물결처럼 차례로 튀어나오고, 이후 키가 하나씩 눌린다. */
    public static final class MiniKeyboard extends Looping {
        private static final String[][] ROWS = {
                {"ㅂ", "ㅈ", "ㄷ", "ㄱ", "ㅅ", "ㅗ", "ㅐ", "ㅔ"},
                {"ㅁ", "ㄴ", "ㅇ", "ㄹ", "ㅎ", "ㅓ", "ㅏ", "ㅣ"},
                {"⇧", "ㅋ", "ㅌ", "ㅊ", "ㅍ", "ㅜ", "ㅡ", "⌫"},
                {"!#1", ",", "", ".", "↵"},
        };
        /** 맨 아래 줄 키 폭 (보통 키 몇 칸인지). */
        private static final float[] BOTTOM = {1.5f, 1f, 3f, 1f, 1.5f};
        /** '새로운 시작'을 치는 순서: ㅅ ㅐ ㄹ ㅗ ㅇ ㅜ ㄴ (띄어쓰기) ㅅ ㅣ ㅈ ㅏ ㄱ. 키는 {줄, 칸}. */
        private static final int[][] TAPS = {{0, 4}, {0, 6}, {1, 3}, {0, 5}, {1, 2}, {2, 5}, {1, 1}, {3, 2},
                {0, 4}, {1, 7}, {0, 1}, {1, 6}, {0, 3}};
        /** 한 글자를 누르는 데 걸리는 시간 (ms). */
        private static final long TAP_MS = 300;
        /** 다 치고 나서 쉬는 칸 수 (다음 반복 전에 한숨 돌린다). */
        private static final int PAUSE = 4;

        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF rect = new RectF();
        private final int plate, key, fn, text, accent, onAccent;
        private final float dp;
        private long shownAt = -1;
        private ValueAnimator intro;
        private float introT = 1f;
        /** 등장이 끝나고 잠깐 쉰 뒤에 true가 된다. 그전에는 키를 누르지 않는다. */
        private boolean typing = true;
        private final Runnable startTyping = () -> {
            typing = true;
            restart();   // 타이핑은 첫 글자부터 시작한다
        };
        /** 등장이 끝나고 타이핑을 시작하기까지 쉬는 시간 (ms). */
        private static final long TYPING_DELAY_MS = 300;

        /** @param colors {키보드 바탕, 키, 기능키, 글자, 강조, 강조 위 글자} */
        public MiniKeyboard(Context c, int[] colors) {
            super(c, TAP_MS * (TAPS.length + PAUSE));
            plate = colors[0];
            key = colors[1];
            fn = colors[2];
            text = colors[3];
            accent = colors[4];
            onAccent = colors[5];
            dp = Ui.dp(c, 1);
            textPaint.setTextAlign(Paint.Align.CENTER);
        }

        /** 키들이 차례로 튀어나오는 등장 애니메이션을 처음부터 다시 한다. */
        public void playIntro() {
            if (intro != null) intro.cancel();
            removeCallbacks(startTyping);
            if (!motionEnabled()) {
                introT = 1f;
                typing = true;
                invalidate();
                return;
            }
            introT = 0f;
            typing = false;
            intro = ValueAnimator.ofFloat(0f, 1f);
            intro.setDuration(1200);   // 마지막 키(지연 0.555 + 길이 0.55)까지 스프링이 다 끝나는 시간
            intro.setInterpolator(new LinearInterpolator());
            intro.addUpdateListener(a -> {
                introT = (float) a.getAnimatedValue();
                invalidate();
            });
            intro.addListener(new android.animation.AnimatorListenerAdapter() {
                private boolean canceled;

                @Override
                public void onAnimationCancel(android.animation.Animator animation) {
                    canceled = true;
                }

                @Override
                public void onAnimationEnd(android.animation.Animator animation) {
                    if (canceled) return;
                    introT = 1f;
                    postDelayed(startTyping, TYPING_DELAY_MS);   // 잠깐 쉬었다가 첫 글자부터 친다
                }
            });
            intro.start();   // 등장 동안은 키가 눌리지 않는다 (onDraw는 introT < 1이면 누르지 않음)
        }

        @Override
        protected void onDetachedFromWindow() {
            if (intro != null) intro.cancel();
            removeCallbacks(startTyping);
            super.onDetachedFromWindow();
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            int w = MeasureSpec.getSize(widthMeasureSpec);
            float plateW = Math.min(w, 340 * dp);
            float keyW = (plateW - 2 * 8 * dp - 7 * 4 * dp) / 8f;
            float keyH = keyW * 1.25f;
            setMeasuredDimension(w, Math.round(4 * keyH + 3 * 5 * dp + 2 * 10 * dp));
        }

        @Override
        protected void onDraw(Canvas canvas) {
            float w = getWidth();
            float plateW = Math.min(w, 340 * dp);
            float pad = 8 * dp, gapX = 4 * dp, gapY = 5 * dp;
            float keyW = (plateW - 2 * pad - 7 * gapX) / 8f;
            float keyH = keyW * 1.25f;
            float left = (w - plateW) / 2f;
            paint.setColor(plate);
            rect.set(left, 0, left + plateW, getHeight());
            canvas.drawRoundRect(rect, 20 * dp, 20 * dp, paint);

            boolean moving = motionEnabled();
            int slots = TAPS.length + PAUSE;
            int slot = moving ? Math.min(slots - 1, (int) (phase * slots)) : -1;
            int tapIndex = slot < TAPS.length ? slot : -1;   // 쉬는 칸에서는 눌린 키가 없다
            float tapT = moving ? phase * slots - slot : 0f;
            float tapLevel = tapT < 0.2f ? tapT / 0.2f : Math.max(0f, 1f - (tapT - 0.2f) / 0.6f);
            if (introT < 1f || !typing) tapIndex = -1;

            textPaint.setTextSize(Math.min(keyW * 0.5f, 15 * dp));
            for (int r = 0; r < ROWS.length; r++) {
                float y = 10 * dp + r * (keyH + gapY);
                float x = left + pad;
                for (int i = 0; i < ROWS[r].length; i++) {
                    float units = r == 3 ? BOTTOM[i] : 1f;
                    float kw = keyW * units + gapX * (units - 1f);
                    String label = ROWS[r][i];
                    boolean function = r == 3 ? i != 2 : (r == 2 && (i == 0 || i == 7));
                    // 등장: 왼쪽 위부터 대각선 물결로 튀어나온다.
                    float delay = (i * 0.045f + r * 0.08f);
                    float k = introT >= 1f ? 1f : Spring.BOUNCY.getInterpolation(clamp((introT - delay) / 0.55f));
                    if (k <= 0.001f) {
                        x += kw + gapX;
                        continue;
                    }
                    boolean tapped = tapIndex >= 0 && TAPS[tapIndex][0] == r && TAPS[tapIndex][1] == i;
                    float lv = tapped ? tapLevel : 0f;
                    canvas.save();
                    canvas.translate(x + kw / 2f, y + keyH / 2f);
                    canvas.scale(k * (1f - 0.06f * lv), k * (1f - 0.06f * lv));
                    paint.setColor(blend(function ? fn : key, accent, lv));
                    rect.set(-kw / 2f, -keyH / 2f, kw / 2f, keyH / 2f);
                    canvas.drawRoundRect(rect, 7 * dp, 7 * dp, paint);
                    textPaint.setColor(blend(text, onAccent, lv));
                    Paint.FontMetrics fm = textPaint.getFontMetrics();
                    canvas.drawText(label, 0, -(fm.ascent + fm.descent) / 2f, textPaint);
                    canvas.restore();
                    x += kw + gapX;
                }
            }
        }
    }

    // ---------------------------------------------------------------- 축하 색종이

    /** 한 점에서 색종이가 터져 나와 떨어진다. 누르기를 가로채지 않는다. */
    public static final class Confetti extends View {
        private static final int COUNT = 70;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF rect = new RectF();
        private final float[] x = new float[COUNT], y = new float[COUNT], vx = new float[COUNT], vy = new float[COUNT],
                rot = new float[COUNT], vr = new float[COUNT], size = new float[COUNT];
        private final int[] color = new int[COUNT], kind = new int[COUNT];
        private final int[] palette;
        private ValueAnimator anim;
        private float t = 1f;
        private float originX, originY;

        public Confetti(Context c, int accent) {
            super(c);
            setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
            float[] hsv = new float[3];
            Color.colorToHSV(accent, hsv);
            palette = new int[]{
                    accent,
                    hue(hsv, 40f, 1f, 1f), hue(hsv, -40f, 1f, 1f),
                    hue(hsv, 150f, 0.8f, 1f), hue(hsv, 200f, 0.7f, 1f),
                    hue(hsv, 0f, 0.35f, 1.1f),
            };
        }

        private static int hue(float[] base, float shift, float sat, float val) {
            float[] h = {(base[0] + shift + 360f) % 360f, Math.min(1f, Math.max(0.35f, base[1] * sat)),
                    Math.min(1f, Math.max(0.6f, base[2] * val))};
            return Color.HSVToColor(h);
        }

        /** (cx, cy) 위치(이 뷰 기준)에서 터뜨린다. */
        public void burst(float cx, float cy) {
            if (!ValueAnimator.areAnimatorsEnabled()) return;
            originX = cx;
            originY = cy;
            java.util.Random rnd = new java.util.Random();
            float d = getResources().getDisplayMetrics().density;
            for (int i = 0; i < COUNT; i++) {
                double angle = Math.toRadians(-90 + (rnd.nextFloat() - 0.5f) * 150f);
                float speed = (420 + rnd.nextFloat() * 520) * d;
                vx[i] = (float) Math.cos(angle) * speed;
                vy[i] = (float) Math.sin(angle) * speed;
                x[i] = 0;
                y[i] = 0;
                rot[i] = rnd.nextFloat() * 360f;
                vr[i] = (rnd.nextFloat() - 0.5f) * 720f;
                size[i] = (5 + rnd.nextFloat() * 5) * d;
                color[i] = palette[rnd.nextInt(palette.length)];
                kind[i] = rnd.nextInt(3);
            }
            if (anim != null) anim.cancel();
            anim = ValueAnimator.ofFloat(0f, 1f);
            anim.setDuration(2600);
            anim.setInterpolator(new LinearInterpolator());
            anim.addUpdateListener(a -> {
                t = (float) a.getAnimatedValue();
                invalidate();
            });
            anim.start();
        }

        @Override
        protected void onDetachedFromWindow() {
            if (anim != null) anim.cancel();
            super.onDetachedFromWindow();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            if (t >= 1f || anim == null) return;
            float sec = t * 2.6f;
            float g = 900 * getResources().getDisplayMetrics().density;
            float drag = 1.6f;
            // 공기 저항이 있는 포물선: v(t) = v0·e^(-k t) + 중력 쪽으로 끌림
            float e = (float) Math.exp(-drag * sec);
            float travel = (1f - e) / drag;
            float alpha = t < 0.7f ? 1f : 1f - (t - 0.7f) / 0.3f;
            for (int i = 0; i < COUNT; i++) {
                float px = originX + vx[i] * travel;
                float py = originY + vy[i] * travel + g / drag * (sec - travel);
                paint.setColor(color[i]);
                paint.setAlpha(Math.round(255 * alpha));
                canvas.save();
                canvas.translate(px, py);
                canvas.rotate(rot[i] + vr[i] * sec);
                float s = size[i];
                switch (kind[i]) {
                    case 0:
                        canvas.drawCircle(0, 0, s * 0.6f, paint);
                        break;
                    case 1:
                        rect.set(-s, -s * 0.4f, s, s * 0.4f);
                        canvas.drawRoundRect(rect, s * 0.4f, s * 0.4f, paint);
                        break;
                    default:
                        rect.set(-s * 0.6f, -s * 0.6f, s * 0.6f, s * 0.6f);
                        canvas.drawRoundRect(rect, s * 0.2f, s * 0.2f, paint);
                        break;
                }
                canvas.restore();
            }
        }
    }

    // ---------------------------------------------------------------- 쪽 표시

    /** 몇 번째 단계인지 보여 주는 점들. 지금 단계는 강조색 알약으로 늘어나고, 바뀔 때 스프링으로 옮겨 간다. */
    public static final class PageDots extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF rect = new RectF();
        private final int count, on, off;
        private float pos;
        private ValueAnimator anim;
        private final float dot, pill, gap;

        public PageDots(Context c, int count, int on, int off) {
            super(c);
            this.count = count;
            this.on = on;
            this.off = off;
            dot = Ui.dp(c, 8);
            pill = Ui.dp(c, 26);
            gap = Ui.dp(c, 8);
            setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        }

        public void setPosition(int index, boolean animate) {
            if (anim != null) anim.cancel();
            if (!animate) {
                pos = index;
                invalidate();
                return;
            }
            anim = ValueAnimator.ofFloat(pos, index);
            anim.setDuration(320);
            anim.setInterpolator(Spring.SOFT);
            anim.addUpdateListener(a -> {
                pos = (float) a.getAnimatedValue();
                invalidate();
            });
            anim.start();
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            int w = Math.round(pill + (count - 1) * (dot + gap));
            setMeasuredDimension(w, Math.round(dot));
        }

        @Override
        protected void onDraw(Canvas canvas) {
            float x = 0f;
            for (int i = 0; i < count; i++) {
                float k = Math.max(0f, 1f - Math.abs(i - pos));
                float wd = dot + (pill - dot) * k;
                paint.setColor(blend(off, on, k));
                rect.set(x, 0, x + wd, dot);
                canvas.drawRoundRect(rect, dot / 2f, dot / 2f, paint);
                x += wd + gap;
            }
        }
    }

    // ---------------------------------------------------------------- 공용

    static float clamp(float v) {
        return Math.max(0f, Math.min(1f, v));
    }

    static int blend(int from, int to, float t) {
        t = clamp(t);
        int a = Math.round(((from >>> 24) * (1 - t)) + ((to >>> 24) * t));
        int r = Math.round((((from >> 16) & 0xFF) * (1 - t)) + (((to >> 16) & 0xFF) * t));
        int g = Math.round((((from >> 8) & 0xFF) * (1 - t)) + (((to >> 8) & 0xFF) * t));
        int b = Math.round(((from & 0xFF) * (1 - t)) + ((to & 0xFF) * t));
        return (a << 24) | (r << 16) | (g << 8) | b;
    }
}
