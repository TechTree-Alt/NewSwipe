package com.alternative_studios.newswipe.ui;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.animation.DecelerateInterpolator;
import android.widget.TextView;

/**
 * Material 3 Expressive 스타일 '연결된 버튼 그룹'의 한 칸.
 * 고른 칸은 알약 모양 강조색, 나머지는 옅은 면에 안쪽 모서리만 작게 둥글고 (줄 양 끝의 바깥 모서리는 알약처럼 둥글다),
 * 고르면 모서리가 스프링으로 늘어나며 색이 바뀌고, 누르는 동안 모서리가 각지면서 살짝 눌린다.
 * 그림은 직접 그려 프레임마다 Path 하나만 다시 만든다.
 */
public final class ExpressiveChoiceButton extends TextView {
    private static final long SELECT_MS = 450, PRESS_IN_MS = 90, PRESS_OUT_MS = 420;

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final RectF rect = new RectF();
    private final float[] radii = new float[8];
    private final int accent, onAccent, text, hint;
    private int idle, overlay;
    private final float small, pressed;
    private boolean first, last;
    private float sel;      // 0 = 고르지 않음, 1 = 고름 (스프링으로 1을 조금 넘을 수 있다)
    private float press;    // 0 = 놓음, 1 = 누름
    private ValueAnimator selAnim, pressAnim;
    private boolean down;
    private boolean selecting;   // 지금 고르는 중인지 (해제되는 칸은 늘어나지 않고 모양만 바뀐다)
    private final String label;
    private String sub;
    private final android.text.TextPaint subPaint = new android.text.TextPaint(Paint.ANTI_ALIAS_FLAG);
    private CharSequence subShown;     // 칸 폭에 맞춰 줄인 보조 글자 (폭이 바뀔 때만 다시 계산)
    private float subShownRoom = -1f;
    private Extra extra;

    /** 칸 안에 글자 말고 더 그릴 것 (예: 키 둘레의 방향별 글자). 모양과 같이 찌그러지는 캔버스에 그려진다. */
    public interface Extra {
        /** @param ink 보조 글자색: 고르지 않으면 힌트색, 고르면 강조색 위의 글자색 */
        void draw(Canvas canvas, float w, float h, int ink);
    }
    private ExpressiveChoiceButton prev, next;
    private float extL, extR;   // 누르거나 고를 때 옆 칸 쪽으로 늘어난 폭 (px). 옆 칸은 그만큼 마주 보는 가장자리가 줄어든다.

    public ExpressiveChoiceButton(Context c, String label, boolean selected, boolean first, boolean last,
                                  int accent, int onAccent, int text, int hint) {
        super(c);
        this.accent = accent;
        this.onAccent = onAccent;
        this.text = text;
        this.hint = hint;
        this.idle = (hint & 0x00FFFFFF) | 0x26000000;
        this.overlay = (hint & 0x00FFFFFF) | 0x33000000;
        this.small = Ui.dp(c, 8);
        this.pressed = Ui.dp(c, 6);
        this.first = first;
        this.last = last;
        this.label = label;
        setText(label);
        setMaxLines(2);
        setGravity(Gravity.CENTER);
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        setTypeface(android.graphics.Typeface.DEFAULT_BOLD);   // 나열 버튼의 글자는 늘 굵게
        setPadding(Ui.dp(c, 6), Ui.dp(c, 12), Ui.dp(c, 6), Ui.dp(c, 12));
        setClickable(true);
        setFocusable(true);
        sel = selected ? 1f : 0f;
        setSelected(selected);
        applyTextColor();
    }

    /** 고르지 않은 칸의 면 색과 눌렀을 때 덧칠하는 색을 직접 정한다 (기본은 글자 힌트 색을 옅게 쓴다). */
    public void setIdleColors(int idle, int overlay) {
        this.idle = idle;
        this.overlay = overlay;
        invalidate();
    }

    /** 이름 글자 크기 (sp). */
    public void setLabelSize(float sp) {
        setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
    }

    public void setExtra(Extra extra) {
        this.extra = extra;
        invalidate();
    }

    /** 줄 안에서의 자리: 줄 양 끝 칸은 바깥 모서리가 알약이다. 한 칸씩 떨어진 칸은 둘 다 false. */
    public void setPosition(boolean first, boolean last) {
        this.first = first;
        this.last = last;
        invalidate();
    }

    /** 칸들을 한 줄로 잇는다: 양 끝 칸의 바깥 모서리를 알약으로 하고 서로 옆 칸으로 연결한다. */
    public static void link(ExpressiveChoiceButton... cells) {
        for (int i = 0; i < cells.length; i++) {
            cells[i].setPosition(i == 0, i == cells.length - 1);
            cells[i].setNeighbors(i > 0 ? cells[i - 1] : null, i + 1 < cells.length ? cells[i + 1] : null);
        }
    }

    public void setSubLabel(String s) {
        setSubLabel(s, 8, 26);
    }

    /**
     * 글자 아래에 작게 덧붙이는 보조 글자 (예: 기능 이름 아래의 방향). 고른 칸에서는 강조색 위의 글자색으로 바뀐다.
     * 보조 글자를 쓰면 이름은 최대 두 줄이고 줄바꿈은 글자 단위로 둔다. 칸보다 길면 말줄임표로 줄인다.
     *
     * @param topDp 위 여백, bottomDp 보조 글자 자리까지 포함한 아래 여백 (dp)
     */
    public void setSubLabel(String s, int topDp, int bottomDp) {
        sub = s;
        subShown = null;
        subPaint.setTextSize(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 11, getResources().getDisplayMetrics()));
        subPaint.setTextAlign(Paint.Align.CENTER);
        setMaxLines(2);
        setEllipsize(android.text.TextUtils.TruncateAt.END);
        setPadding(Ui.dp(getContext(), 6), Ui.dp(getContext(), topDp), Ui.dp(getContext(), 6), Ui.dp(getContext(), bottomDp));
        requestLayout();
    }

    /** 같은 줄의 양옆 칸. 늘어나는 모양이 옆 칸의 가장자리를 밀어 주는 데 쓴다. */
    public void setNeighbors(ExpressiveChoiceButton prev, ExpressiveChoiceButton next) {
        this.prev = prev;
        this.next = next;
    }

    /**
     * 한글은 글자 단위로 줄이 바뀌어 '커서 자유 / 이동'이 '커서 자유 이 / 동'처럼 잘리므로, 한 줄에 다 들어가지 않으면
     * 띄어쓰기 자리에서 직접 나눈다. 두 줄의 폭이 가장 고르게 되는 자리를 고르고 (같으면 앞줄을 길게),
     * 재는 단계에서 글자를 정해 두어 처음부터 올바른 모양으로 그려진다.
     */
    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int w = MeasureSpec.getSize(widthMeasureSpec);
        if (MeasureSpec.getMode(widthMeasureSpec) != MeasureSpec.UNSPECIFIED && w > 0) {
            String want = wrap(label, w - getPaddingLeft() - getPaddingRight());
            if (!want.contentEquals(getText())) setText(want);
        }
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    }

    private String wrap(String text, int room) {
        if (getPaint().measureText(text) <= room) return text;
        String best = text;
        float bestWidth = Float.MAX_VALUE;
        for (int i = text.indexOf(' '); i >= 0; i = text.indexOf(' ', i + 1)) {
            String left = text.substring(0, i), right = text.substring(i + 1);
            float width = Math.max(getPaint().measureText(left), getPaint().measureText(right));
            if (width <= bestWidth) {   // 같으면 뒤쪽 자리 (앞줄이 길다)
                bestWidth = width;
                best = left + "\n" + right;
            }
        }
        return best;
    }

    /**
     * 누르거나 고를 때 폭이 좌우로 늘었다가 줄어드는 정도. 누르면 늘어나고, 고르는 동안은 같은 스프링을 따라
     * 늘었다가 제자리로 돌아온다 (목표를 넘으면 살짝 줄었다 돌아온다). 해제되는 칸은 모양과 색만 바뀐다. 늘어난 폭은 옆 칸 쪽으로 나누고 옆 칸은 그만큼 줄어든다.
     */
    private void updateStretch() {
        float s = 0.05f * press + (selecting ? 0.09f * 4f * sel * (1f - sel) : 0f);
        float d = getWidth() * s;
        if (prev != null && next != null) {
            extL = extR = d / 2f;
        } else {
            extL = prev != null ? d : 0f;
            extR = next != null ? d : 0f;
        }
        // 늘어난 모양이 이 칸 밖에도 그려지고 옆 칸의 모양도 바뀌므로, 세 칸과 줄을 한 번씩만 다시 그린다.
        invalidate();
        if (prev != null) prev.invalidate();
        if (next != null) next.invalidate();
        if (getParent() instanceof android.view.View) ((android.view.View) getParent()).invalidate();
    }

    /** 고르거나 해제한다. animate면 스프링으로 모양과 색이 바뀐다. */
    public void setChosen(boolean chosen, boolean animate) {
        setSelected(chosen);
        float to = chosen ? 1f : 0f;
        selecting = chosen && animate;   // 해제될 때는 좌우로 늘어나지 않는다
        if (selAnim != null) selAnim.cancel();
        if (!animate) {
            sel = to;
            applyTextColor();
            updateStretch();
            return;
        }
        selAnim = ValueAnimator.ofFloat(sel, to);
        selAnim.setDuration(SELECT_MS);
        selAnim.setInterpolator(Spring.BOUNCY);
        selAnim.addUpdateListener(a -> {
            sel = (float) a.getAnimatedValue();
            applyTextColor();
            updateStretch();
        });
        selAnim.start();
    }

    private void animatePress(boolean toDown) {
        if (pressAnim != null) pressAnim.cancel();
        pressAnim = ValueAnimator.ofFloat(press, toDown ? 1f : 0f);
        pressAnim.setDuration(toDown ? PRESS_IN_MS : PRESS_OUT_MS);
        pressAnim.setInterpolator(toDown ? new DecelerateInterpolator() : Spring.BOUNCY);
        pressAnim.addUpdateListener(a -> {
            press = (float) a.getAnimatedValue();
            updateStretch();
        });
        pressAnim.start();
    }

    private void applyTextColor() {
        setTextColor(blend(text, onAccent, clamp01(sel)));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float w = getWidth(), h = getHeight();
        float full = h / 2f;
        float t = clamp01(sel);
        // 고르지 않은 칸의 안쪽 모서리는 작게, 고른 칸은 알약으로. 줄 양 끝의 바깥 모서리는 늘 알약이다.
        float inner = lerp(small, full, sel);
        float left = first ? full : inner, right = last ? full : inner;
        left = lerp(left, pressed, press);
        right = lerp(right, pressed, press);
        left = clamp(left, 0f, full);
        right = clamp(right, 0f, full);
        radii[0] = radii[1] = radii[6] = radii[7] = left;    // 왼쪽 위·아래
        radii[2] = radii[3] = radii[4] = radii[5] = right;   // 오른쪽 위·아래
        // 늘어난 폭만큼 바깥으로, 옆 칸이 늘어난 만큼 안쪽으로 가장자리를 옮긴다.
        float l = -extL + (prev != null ? prev.extR : 0f), r = w + extR - (next != null ? next.extL : 0f);
        // 누르면 모양만 위아래로 살짝 눌리고, 놓으면 스프링으로 되튀며 제자리로 온다 (글자는 아래에서 같은 비율로 따로 줄인다).
        float squash = h * 0.035f * press;
        rect.set(l, squash, r, h - squash);
        path.rewind();
        path.addRoundRect(rect, radii, Path.Direction.CW);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(blend(idle, accent, t));
        canvas.drawPath(path, paint);
        // 누른 느낌: 고르지 않은 칸은 면 위에 덧칠색을, 고른 칸은 강조색 위에 글자색을 옅게 얹는다 (강조색이 회색으로 죽지 않게).
        float pr = clamp01(press);
        if (pr > 0.01f) {
            float off = 1f - t;
            if (off > 0.01f) {
                paint.setColor(withAlpha(overlay, Math.round((overlay >>> 24) * pr * off)));
                canvas.drawPath(path, paint);
            }
            if (t > 0.01f) {
                paint.setColor(withAlpha(onAccent, Math.round(0x29 * pr * t)));
                canvas.drawPath(path, paint);
            }
        }
        // 글자는 찌그러뜨리지 않고, 모양의 가운데가 옆 칸에 밀린 만큼 옆으로 밀려 움직인다 (옆 칸의 글자도 같이 밀린다).
        canvas.save();
        canvas.translate((l + r) / 2f - w / 2f, 0f);
        // 누르면 글자 전체가 같은 비율로 줄어든다 (가로세로 비율은 그대로). 놓을 때 되튀어도 원래 크기를 넘지 않게 한다.
        float textScale = 1f - 0.05f * clamp01(press);
        canvas.scale(textScale, textScale, w / 2f, h / 2f);
        super.onDraw(canvas);
        int ink = blend(hint, withAlpha(onAccent, 0xB3), t);
        if (sub != null) {
            float room = w - 2 * Ui.dp(getContext(), 6);
            if (subShown == null || subShownRoom != room) {
                subShown = android.text.TextUtils.ellipsize(sub, subPaint, Math.max(room, 1f),
                        android.text.TextUtils.TruncateAt.END);
                subShownRoom = room;
            }
            subPaint.setColor(ink);
            canvas.drawText(subShown, 0, subShown.length(), w / 2f, h - Ui.dp(getContext(), 10), subPaint);
        }
        if (extra != null) extra.draw(canvas, w, h, ink);
        canvas.restore();
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        if (!isEnabled() || !isClickable()) return super.onTouchEvent(e);
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                down = true;
                animatePress(true);
                performHapticFeedback(android.os.Build.VERSION.SDK_INT >= 34
                        ? HapticFeedbackConstants.SEGMENT_FREQUENT_TICK : HapticFeedbackConstants.CLOCK_TICK);
                break;
            case MotionEvent.ACTION_MOVE:
                if (down && (e.getX() < 0 || e.getX() > getWidth() || e.getY() < 0 || e.getY() > getHeight())) {
                    down = false;
                    animatePress(false);
                }
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                if (down) {
                    down = false;
                    animatePress(false);
                }
                break;
            default:
                break;
        }
        return super.onTouchEvent(e);
    }

    @Override
    protected void onDetachedFromWindow() {
        if (selAnim != null) selAnim.cancel();
        if (pressAnim != null) pressAnim.cancel();
        super.onDetachedFromWindow();
    }

    private static float clamp01(float v) {
        return clamp(v, 0f, 1f);
    }

    private static float clamp(float v, float min, float max) {
        return Math.max(min, Math.min(max, v));
    }

    private static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }

    private static int withAlpha(int color, int alpha) {
        return (color & 0x00FFFFFF) | (alpha << 24);
    }

    private static int blend(int from, int to, float t) {
        int a = Math.round(((from >>> 24) * (1 - t)) + ((to >>> 24) * t));
        int r = Math.round((((from >> 16) & 0xFF) * (1 - t)) + (((to >> 16) & 0xFF) * t));
        int g = Math.round((((from >> 8) & 0xFF) * (1 - t)) + (((to >> 8) & 0xFF) * t));
        int b = Math.round(((from & 0xFF) * (1 - t)) + ((to & 0xFF) * t));
        return (a << 24) | (r << 16) | (g << 8) | b;
    }
}
