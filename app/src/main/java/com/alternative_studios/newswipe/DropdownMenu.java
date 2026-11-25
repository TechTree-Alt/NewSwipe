package com.alternative_studios.newswipe;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.text.style.RelativeSizeSpan;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.PathInterpolator;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.TextView;

import com.alternative_studios.newswipe.ui.Metro;
import com.alternative_studios.newswipe.ui.NeuUi;
import com.alternative_studios.newswipe.ui.Ui;

import java.util.function.IntConsumer;
import java.util.function.ObjIntConsumer;

/**
 * 항목이 몇 개뿐인 고르기(최대 {@link #MAX_ITEMS}개)를 대화상자 대신 누른 줄 위에 펼치는 드롭다운 목록 (맨 윗 항목이 누른 줄을 덮는다).
 *
 * <ul>
 * <li>기본: Material 3 메뉴. 카드보다 한 단계 높은 면의 둥근 상자(16dp)가 그림자로 떠 있고, 누른 줄의 오른쪽 끝(지금 값
 *     자리)에 맞춰 Material의 표준 움직임(살짝 작은 크기에서 커지며 나타남, 150ms 감속 곡선)으로 펼쳐진다.
 *     고른 항목은 옅은 강조색 알약에 강조색 글자와 체크 표시.</li>
 * <li>Neumorphism: 바탕과 같은 색의 상자가 큰 그림자로 솟아오른다. 고른 항목은 안으로 들어간 홈, 누르는 동안의 항목도 들어간다.
 *     부드럽게 떠오르며(크기 96% → 100%, 감속) 나타난다.</li>
 * <li>Metro: Windows Phone Toolkit의 ListPicker처럼 떠 있는 창 없이 그 자리에서 아래 내용을 밀어내며 펼쳐진다(ExponentialEase
 *     EaseOut). 흰 바탕에 강조색 테두리의 네모, 고른 항목은 표시 없이 강조색 글자, 누르면 틸트.</li>
 * </ul>
 *
 * <p>기본·Neumorphism에서는 누른 줄의 화살표(›)가 펼치는 동안 아래(⌄)로 돌고, 닫히면 돌아온다.
 *
 * <p>가볍게: XML·ListView·어댑터 없이 항목마다 TextView 하나(기본값 표시는 같은 글자에 붙인 작은 글씨)로 만들고, 움직이는 동안만 하드웨어 층으로 그린다.
 * 열 때마다 새로 만들고 닫으면 모두 놓는다. 한 번에 하나만 열린다.
 */
final class DropdownMenu {
    /** 이보다 항목이 많으면 드롭다운 대신 대화상자로 고른다. */
    static final int MAX_ITEMS = 6;

    private static java.lang.ref.WeakReference<DropdownMenu> current;

    private final View anchor;
    private final Context c;
    private final AppTheme t;
    private final String[] items;
    private int selected;
    private int defaultIndex = -1;
    private ObjIntConsumer<View> preview;
    private Runnable onDismiss;
    private IntConsumer onPick;

    private TextView[] rows;
    private PopupWindow popup;
    private View menu;
    /** Metro: 그 자리에 끼워 넣은 목록 (팝업이 아닐 때). */
    private View inline;
    private boolean closing;
    /** 펼쳐지는 기준점의 세로 위치 (상자 안, 누른 줄의 가운데). */
    private float pivotY;
    /** 미리 듣기를 하면서도 목록을 열어 둔다 (여러 개를 비교). */
    private boolean keepOpen;
    /** 누른 줄의 화살표 (펼친 동안 아래로 돌린다). 없으면 null. */
    private View chevron;
    private final View.OnAttachStateChangeListener detach = new View.OnAttachStateChangeListener() {
        @Override
        public void onViewAttachedToWindow(View v) {
        }

        @Override
        public void onViewDetachedFromWindow(View v) {
            closeNow();   // 화면이 사라지면 움직임 없이 바로 닫는다 (창이 남지 않게)
        }
    };

    private DropdownMenu(View anchor, String[] items, int selected) {
        this.anchor = anchor;
        this.c = anchor.getContext();
        this.t = AppTheme.of(c);
        this.items = items;
        this.selected = selected;
    }

    /** anchor(누른 줄) 아래에 items를 펼칠 준비를 한다. selected는 지금 고른 차례 (없으면 -1). */
    static DropdownMenu of(View anchor, String[] items, int selected) {
        return new DropdownMenu(anchor, items, selected);
    }

    /** 기본값 항목에 작게 '기본'을 붙인다 (대화상자의 '기본값' 버튼 대신). */
    DropdownMenu defaultIndex(int i) {
        defaultIndex = i;
        return this;
    }

    /** 항목을 누를 때마다 미리 들려주거나 울려 준다 (소리·진동). 같은 항목을 다시 눌러도 울린다. */
    DropdownMenu preview(ObjIntConsumer<View> p) {
        preview = p;
        return this;
    }

    /**
     * 항목을 눌러도 목록을 닫지 않는다 (누른 항목은 바로 고른 값이 된다). 미리 울려 보며 여러 개를 비교할 때 쓴다.
     * 바깥을 누르거나(Metro는 줄을 다시 누르면) 닫힌다.
     */
    DropdownMenu keepOpen() {
        keepOpen = true;
        return this;
    }

    /** 목록이 닫힐 때 (어떻게 닫히든 한 번). */
    DropdownMenu onDismiss(Runnable r) {
        onDismiss = r;
        return this;
    }

    /** 펼친다. onPick은 다른 항목을 골랐을 때만 그 차례로 부른다. 같은 줄의 목록이 이미 열려 있으면 접기만 한다. */
    void show(IntConsumer pick) {
        onPick = pick;
        DropdownMenu open = current == null ? null : current.get();
        if (open != null && !open.closing) {
            open.close();
            if (open.anchor == anchor) return;
        }
        current = new java.lang.ref.WeakReference<>(this);
        anchor.addOnAttachStateChangeListener(detach);
        chevron = findChevron(anchor);
        turnChevron(true);
        if (Metro.on && anchor.getParent() instanceof LinearLayout
                && ((LinearLayout) anchor.getParent()).getOrientation() == LinearLayout.VERTICAL) {
            showInline((LinearLayout) anchor.getParent());
        } else {
            showPopup();
        }
    }

    private static View findChevron(View v) {
        if (v instanceof com.alternative_studios.newswipe.ui.Chevron) return v.getVisibility() == View.VISIBLE ? v : null;
        if (v instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) v;
            for (int i = g.getChildCount() - 1; i >= 0; i--) {   // 화살표는 줄의 끝에 있다
                View found = findChevron(g.getChildAt(i));
                if (found != null) return found;
            }
        }
        return null;
    }

    /** 화살표를 아래(펼침)로, 또는 제자리(접힘)로 돌린다. 오른쪽에서 왼쪽으로 쓰는 언어는 화살표가 뒤집혀 있어 반대로 돈다. */
    private void turnChevron(boolean open) {
        if (chevron == null || Metro.on) return;
        float down = chevron.getLayoutDirection() == View.LAYOUT_DIRECTION_RTL ? -90f : 90f;
        chevron.animate().cancel();
        chevron.animate().rotation(open ? down : 0f).setDuration(open ? 200 : 150)
                .setInterpolator(STANDARD).start();
    }

    /** Material의 표준 곡선 (FastOutSlowIn). */
    private static final PathInterpolator STANDARD = new PathInterpolator(0.4f, 0f, 0.2f, 1f);

    private int dp(float v) {
        return Ui.dp(c, v);
    }

    // ---------------------------------------------------------------- 항목

    private TextView row(int i) {
        TextView v = new TextView(c);
        if (i == defaultIndex) {
            String tag = L.t("기본", "Default");
            SpannableString s = new SpannableString(items[i] + "   " + tag);
            int start = s.length() - tag.length();
            s.setSpan(new RelativeSizeSpan(0.78f), start, s.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            s.setSpan(new ForegroundColorSpan(Metro.on ? 0xFF8A8A8A : t.hint), start, s.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            v.setText(s);
        } else {
            v.setText(items[i]);
        }
        v.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
        v.setMinHeight(dp(Metro.on ? 52 : 48));
        v.setClickable(true);
        v.setFocusable(true);
        v.setOnClickListener(x -> pick(i));
        if (Metro.on) {
            v.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
            v.setTypeface(Metro.light());
            v.setPadding(dp(12), dp(4), dp(12), dp(4));
            Metro.tilt(v);
        } else {
            v.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
            int h = dp(NeuUi.on ? 16 : 12);
            v.setPadding(h, dp(8), h, dp(8));
            v.setCompoundDrawablePadding(dp(12));
            v.setCompoundDrawablesRelative(null, null, new CheckMark(dp(20), t.accent), null);
        }
        return v;
    }

    /** 고른 항목인지에 따라 줄의 글자색·바탕·체크를 맞춘다. */
    private void style(int i) {
        TextView v = rows[i];
        boolean on = i == selected;
        v.setSelected(on);   // 접근성: '선택됨'으로 읽힌다
        if (Metro.on) {
            // ListPicker: 흰 상자 위의 검은 글자, 고른 항목은 강조색 (표시 없이).
            v.setTextColor(on ? t.accent : 0xDE000000);
            return;
        }
        v.setTextColor(on ? t.accent : t.text);
        Drawable[] d = v.getCompoundDrawablesRelative();
        if (d[2] instanceof CheckMark) ((CheckMark) d[2]).set(on);
        float r = dp(NeuUi.on ? 14 : 12);
        if (NeuUi.on) {
            // 고른 줄은 들어간 홈 (고른 기능 줄과 같다), 나머지는 누르는 동안만 들어간다.
            if (on) {
                v.setBackground(NeuUi.sunkenFace(c, r));
            } else {
                StateListDrawable s = new StateListDrawable();
                s.addState(new int[]{android.R.attr.state_pressed}, NeuUi.sunkenFace(c, r));
                s.addState(new int[0], new ColorDrawable(0));
                v.setBackground(s);
            }
        } else {
            int press = (t.accent & 0x00FFFFFF) | 0x33000000;
            v.setBackground(Ui.ripple(press, on ? Ui.round((t.accent & 0x00FFFFFF) | 0x24000000, r) : null, r));
        }
    }

    private LinearLayout list() {
        LinearLayout box = new LinearLayout(c);
        box.setOrientation(LinearLayout.VERTICAL);
        rows = new TextView[items.length];
        for (int i = 0; i < items.length; i++) {
            rows[i] = row(i);
            style(i);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            if (NeuUi.on && i > 0) lp.topMargin = dp(4);   // 들어간 홈끼리 붙지 않게
            box.addView(rows[i], lp);
        }
        return box;
    }

    private void pick(int i) {
        if (closing) return;
        boolean changed = i != selected;
        if (changed) {
            int old = selected;
            selected = i;
            if (old >= 0) style(old);
            style(i);
        }
        if (keepOpen) {
            // 목록을 열어 둔 채 바로 고르고 울린다.
            if (changed) onPick.accept(i);
            if (preview != null) preview.accept(rows[i], i);
            return;
        }
        if (preview != null) preview.accept(rows[i], i);
        close();
        if (changed) onPick.accept(i);
    }

    // ---------------------------------------------------------------- Metro: 그 자리에서 펼치기 (ListPicker)

    private void showInline(LinearLayout parent) {
        LinearLayout box = list();
        // 펼친 ListPicker: 흰 바탕(PhoneTextBoxEditBackground)에 강조색 테두리의 네모.
        GradientDrawable bg = Ui.round(0xFFFFFFFF, 0);
        bg.setStroke(dp(2), t.accent);
        box.setBackground(bg);
        box.setPadding(dp(2), dp(6), dp(2), dp(6));
        // 펼치고 접는 동안 높이를 바꾸므로 여백 없는 틀로 감싼다 (Ui.setVisibleAnimated가 틀의 높이를 움직인다).
        LinearLayout frame = new LinearLayout(c);
        frame.setOrientation(LinearLayout.VERTICAL);
        frame.setClipChildren(true);
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        blp.topMargin = dp(2);
        blp.bottomMargin = dp(10);
        frame.addView(box, blp);
        frame.setVisibility(View.GONE);
        parent.addView(frame, parent.indexOfChild(anchor) + 1,
                new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        inline = frame;
        Ui.setVisibleAnimated(frame, true);
    }

    // ---------------------------------------------------------------- 기본·Neumorphism: 떠 있는 목록

    private void showPopup() {
        LinearLayout box = list();
        int radius;
        if (Metro.on) {
            // 끼워 넣을 수 없는 자리(세로 줄이 아닌 부모)의 Metro: 같은 ListPicker 모양으로 띄운다.
            GradientDrawable bg = Ui.round(0xFFFFFFFF, 0);
            bg.setStroke(dp(2), t.accent);
            box.setBackground(bg);
            box.setPadding(dp(2), dp(6), dp(2), dp(6));
            radius = 0;
        } else if (NeuUi.on) {
            radius = dp(22);
            box.setPadding(dp(8), dp(8), dp(8), dp(8));
        } else {
            // M3 메뉴: 카드보다 한 단계 높은 면 (글자색을 조금 섞는다), 그림자로 뜬다.
            radius = dp(16);
            box.setBackground(Ui.round(blend(t.card, t.text, t.dark ? 0.07f : 0.025f), radius));
            box.setPadding(dp(4), dp(4), dp(4), dp(4));
            box.setElevation(dp(6));
            box.setClipToOutline(true);
        }
        // 그림자 자리: 상자 둘레를 비워 둬야 창 경계에서 그림자가 잘리지 않는다.
        int pad = Metro.on ? 0 : NeuUi.on ? (int) Math.ceil(NeuUi.spread(c, NeuUi.LARGE)) : dp(12);
        FrameLayout root = new FrameLayout(c);
        root.setPadding(pad, pad, pad, pad);
        root.setClipToPadding(false);
        root.setClipChildren(false);
        root.addView(box, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        if (NeuUi.on) NeuUi.card(box, radius);

        // 너비: 항목이 다 보이는 만큼 (최소 180dp, 최대 화면 폭 - 32dp와 320dp 중 작은 것).
        Rect frame = new Rect();
        anchor.getWindowVisibleDisplayFrame(frame);
        int margin = dp(16);
        box.measure(View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        int maxW = Math.min(dp(320), frame.width() - margin * 2);
        // 잰 너비 그대로면 반올림 오차로 가장 긴 항목이 한 줄에 겨우 못 들어가 두 줄로 꺾일 수 있어 조금 여유를 둔다.
        int w = Math.max(Math.min(dp(180), maxW), Math.min(box.getMeasuredWidth() + dp(4), maxW));
        box.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        int h = box.getMeasuredHeight();

        // 자리: 누른 줄의 오른쪽 끝(지금 값)에 맞추고, 맨 윗 항목이 누른 줄을 덮도록 (두 줄의 세로 가운데를 맞춘다).
        // 화면 아래가 모자라면 그만큼 위로 올린다.
        int[] loc = new int[2];
        anchor.getLocationInWindow(loc);
        int[] win = new int[2];
        anchor.getRootView().getLocationOnScreen(win);
        int top = loc[1] + win[1];
        int center = top + anchor.getHeight() / 2;
        int right = loc[0] + win[0] + anchor.getWidth() - anchor.getPaddingRight() + dp(Metro.on ? 0 : 4);
        int y = center - box.getPaddingTop() - rows[0].getMeasuredHeight() / 2;
        y = Math.max(frame.top + margin, Math.min(y, frame.bottom - margin - h));
        int x = Math.max(frame.left + margin, Math.min(right - w, frame.right - margin - w));
        pivotY = Math.max(0, Math.min(h, center - y));   // 누른 줄 자리에서 펼쳐진다

        // 높이는 창이 직접 재게 한다 (글자 줄 수가 예상과 달라도 아래가 잘리지 않게).
        popup = new PopupWindow(root, w + pad * 2, ViewGroup.LayoutParams.WRAP_CONTENT, true);
        popup.setBackgroundDrawable(new ColorDrawable(0));
        popup.setOutsideTouchable(true);
        popup.setAnimationStyle(0);   // 창 움직임은 직접 그린다
        popup.setClippingEnabled(false);
        popup.setTouchInterceptor((v, e) -> {
            // 바깥을 누르면 움직이며 닫는다 (기본 처리는 움직임 없이 바로 닫는다).
            boolean outside = e.getAction() == MotionEvent.ACTION_OUTSIDE
                    || (e.getAction() == MotionEvent.ACTION_DOWN && (e.getX() < pad || e.getY() < pad
                    || e.getX() > v.getWidth() - pad || e.getY() > v.getHeight() - pad));
            if (outside) {
                close();
                return true;
            }
            return false;
        });
        popup.setOnDismissListener(this::finish);
        menu = box;
        popup.showAtLocation(anchor.getRootView(), Gravity.TOP | Gravity.START, x - win[0] - pad, y - win[1] - pad);
        enter(box, w, h);
    }

    /** 펼치는 움직임: 기본은 Material 메뉴처럼 모서리에서 커지며, Neumorphism은 부드럽게 떠오르고, Metro는 스위블. */
    private void enter(View box, int w, int h) {
        if (Metro.on) {
            Metro.swivelIn(box);
            return;
        }
        box.setPivotX(w);
        box.setPivotY(pivotY);
        box.setAlpha(0f);
        if (NeuUi.on) {
            box.setScaleX(0.96f);
            box.setScaleY(0.96f);
            box.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(220)
                    .setInterpolator(new DecelerateInterpolator(2f)).withLayer().start();
            return;
        }
        // Material 메뉴: 값 자리 모서리에서 살짝 작은 크기(80%)로 커지며 나타난다. 하나의 감속 곡선으로 짧게.
        box.setScaleX(0.8f);
        box.setScaleY(0.8f);
        box.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(150)
                .setInterpolator(new DecelerateInterpolator(1.5f)).withLayer().start();
    }

    // ---------------------------------------------------------------- 닫기

    /** 움직이며 닫는다. */
    void close() {
        if (closing) return;
        closing = true;
        turnChevron(false);
        if (inline != null) {
            View frame = inline;
            Ui.setVisibleAnimated(frame, false, () -> {
                if (frame.getParent() instanceof ViewGroup) ((ViewGroup) frame.getParent()).removeView(frame);
                finish();
            });
            return;
        }
        if (popup == null) {
            finish();
            return;
        }
        View box = menu;
        box.animate().cancel();
        if (Metro.on) {
            box.animate().alpha(0f).setDuration(100).withEndAction(this::dismissPopup).start();
            return;
        }
        // Material 메뉴는 닫힐 때 제자리에서 흐려지기만 한다. Neumorphism은 살짝 가라앉으며 흐려진다.
        float s = NeuUi.on ? 0.97f : 1f;
        box.animate().alpha(0f).scaleY(s).scaleX(s)
                .setDuration(NeuUi.on ? 140 : 100).setInterpolator(new PathInterpolator(0.3f, 0f, 1f, 1f))
                .withLayer().setListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        box.animate().setListener(null);
                        dismissPopup();
                    }
                }).start();
    }

    private void dismissPopup() {
        if (popup != null && popup.isShowing()) {
            try {
                popup.dismiss();
            } catch (RuntimeException ignored) {
                // 창이 이미 사라졌으면 할 일이 없다
            }
        }
        finish();
    }

    /** 움직임 없이 바로 닫는다. */
    private void closeNow() {
        closing = true;
        if (chevron != null) {
            chevron.animate().cancel();
            chevron.setRotation(0f);
        }
        if (menu != null) menu.animate().cancel();
        if (inline != null && inline.getParent() instanceof ViewGroup) ((ViewGroup) inline.getParent()).removeView(inline);
        dismissPopup();
    }

    private boolean finished;

    private void finish() {
        if (finished) return;
        finished = true;
        closing = true;
        turnChevron(false);   // 뒤로 가기 등으로 창이 바로 닫혀도 화살표는 돌아온다
        anchor.removeOnAttachStateChangeListener(detach);
        if (current != null && current.get() == this) current = null;
        if (onDismiss != null) onDismiss.run();
    }

    private static int blend(int a, int b, float f) {
        int ar = (a >> 16) & 0xFF, ag = (a >> 8) & 0xFF, ab = a & 0xFF;
        int br = (b >> 16) & 0xFF, bg = (b >> 8) & 0xFF, bb = b & 0xFF;
        return 0xFF000000 | (Math.round(ar + (br - ar) * f) << 16) | (Math.round(ag + (bg - ag) * f) << 8)
                | Math.round(ab + (bb - ab) * f);
    }

    /** 고른 항목 끝의 체크 표시 (고르지 않은 항목은 같은 자리를 비워 둬 글자 줄이 가지런하다). */
    private static final class CheckMark extends Drawable {
        private final int size;
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path path = new Path();
        private boolean on;

        CheckMark(int size, int color) {
            this.size = size;
            p.setColor(color);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeCap(Paint.Cap.ROUND);
            p.setStrokeJoin(Paint.Join.ROUND);
            p.setStrokeWidth(size * 0.11f);
            setBounds(0, 0, size, size);
        }

        void set(boolean checked) {
            if (on == checked) return;
            on = checked;
            invalidateSelf();
        }

        @Override
        public int getIntrinsicWidth() {
            return size;
        }

        @Override
        public int getIntrinsicHeight() {
            return size;
        }

        @Override
        public void draw(Canvas canvas) {
            if (!on) return;
            Rect b = getBounds();
            float s = b.width();
            path.reset();
            path.moveTo(b.left + s * 0.2f, b.top + s * 0.52f);
            path.lineTo(b.left + s * 0.42f, b.top + s * 0.73f);
            path.lineTo(b.left + s * 0.8f, b.top + s * 0.3f);
            canvas.drawPath(path, p);
        }

        @Override
        public void setAlpha(int alpha) {
            p.setAlpha(alpha);
        }

        @Override
        public void setColorFilter(ColorFilter cf) {
            p.setColorFilter(cf);
        }

        @Override
        public int getOpacity() {
            return PixelFormat.TRANSLUCENT;
        }
    }
}
