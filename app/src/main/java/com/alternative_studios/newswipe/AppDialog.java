package com.alternative_studios.newswipe;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.drawable.ColorDrawable;
import android.os.Build;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.alternative_studios.newswipe.ui.Metro;
import com.alternative_studios.newswipe.ui.NeuUi;
import com.alternative_studios.newswipe.ui.Ui;

/**
 * 앱의 대화상자. 시스템 AlertDialog는 기기의 강조 색을 써서 설정에서 고른 강조 색과 달랐으므로, 같은 일을 하는 창을 직접 만든다.
 * {@link Builder}는 AlertDialog.Builder에서 앱이 쓰는 부분(제목, 글, 직접 만든 뷰, 목록·한 개 고르기·여러 개 고르기,
 * 확인·취소·중립 버튼)을 같은 이름과 같은 동작으로 갖고 있다.
 *
 * <ul>
 *   <li>보통: 가운데에 뜨는 둥근 카드, 강조 색 글자 버튼, 강조 색 라디오·체크 표시. 열리고 닫히는 움직임은 시스템 대화상자 그대로다.</li>
 *   <li>Metro UI: Windows Phone Toolkit의 CustomMessageBox처럼 화면 맨 위에 폭을 꽉 채운 크롬 색 상자가 붙고, 나머지 화면은
 *       바탕색 60%로 덮인다. 버튼은 같은 폭의 테두리 버튼이 나란히 놓이고, 스위블로 들어오고(SwivelBackwardIn) 나간다(SwivelBackwardOut).</li>
 * </ul>
 *
 * <p>가볍게 만든다: XML을 부풀리지 않고 뷰 몇 개를 코드로 바로 만든다 (시스템 AlertDialog는 레이아웃 XML 몇 개를 부풀린다).
 * 목록은 항목이 많지 않아(수십 개 이하) ListView 대신 줄을 바로 쌓는다. 창 그림자·흐림 같은 비싼 효과는 쓰지 않는다.
 */
public final class AppDialog extends Dialog {
    public static final int BUTTON_POSITIVE = DialogInterface.BUTTON_POSITIVE;
    public static final int BUTTON_NEGATIVE = DialogInterface.BUTTON_NEGATIVE;
    public static final int BUTTON_NEUTRAL = DialogInterface.BUTTON_NEUTRAL;

    private final AppTheme colors;
    private final boolean metro;
    private final Context ui;
    private Button positive, negative, neutral;
    private LinearLayout rows;
    /** Metro: 화면 맨 위의 상자와 그 뒤를 덮는 막. */
    private View box, overlay;
    private boolean leaving;
    private ValueAnimator anim;

    private AppDialog(Builder b) {
        super(b.context, b.colors.metro ? android.R.style.Theme_DeviceDefault_Dialog_NoActionBar
                : b.colors.dark ? android.R.style.Theme_DeviceDefault_Dialog_NoActionBar
                : android.R.style.Theme_DeviceDefault_Light_Dialog_NoActionBar);
        ui = b.context;
        colors = b.colors;
        metro = colors.metro;
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setCancelable(b.cancelable);
        if (b.onCancel != null) setOnCancelListener(b.onCancel);
        if (b.onDismiss != null) setOnDismissListener(b.onDismiss);
        View content = metro ? buildMetro(b) : buildNormal(b);
        setContentView(content);
        Window w = getWindow();
        if (w == null) return;
        w.setBackgroundDrawable(new ColorDrawable(0));
        if (metro) {
            // 화면 전체를 덮는 창: 위에 상자, 나머지는 막 (시스템의 검은 흐림 대신 바탕색 60%).
            w.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
            w.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            w.setWindowAnimations(0);   // 스위블은 직접 그린다
            if (Build.VERSION.SDK_INT >= 30) {
                // 상자가 상태 표시줄 뒤까지 올라가 붙는다 (Windows Phone의 메시지 상자처럼 화면 맨 위부터).
                w.setDecorFitsSystemWindows(false);
                w.addFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS);
                android.view.WindowInsetsController c = w.getInsetsController();
                int light = android.view.WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS;
                if (c != null) c.setSystemBarsAppearance(colors.dark ? 0 : light, light);
            }
        } else {
            android.util.DisplayMetrics m = ui.getResources().getDisplayMetrics();
            int width = Math.min(m.widthPixels - Ui.dp(ui, 40), Ui.dp(ui, 560));
            w.setLayout(width, ViewGroup.LayoutParams.WRAP_CONTENT);
            w.setGravity(Gravity.CENTER);
        }
    }

    /** 확인·취소·중립 버튼 (없으면 null). AlertDialog.getButton과 같다. */
    public TextView getButton(int which) {
        return which == BUTTON_POSITIVE ? positive : which == BUTTON_NEGATIVE ? negative : which == BUTTON_NEUTRAL ? neutral : null;
    }

    /** 목록(setItems·setSingleChoiceItems·setMultiChoiceItems)의 줄들을 담은 뷰 (목록이 없으면 null). */
    public ViewGroup getListView() {
        return rows;
    }

    // ---------------------------------------------------------------- 보통 모양

    private View buildNormal(Builder b) {
        Column root = new Column(ui, Ui.dp(ui, 48));
        root.setOrientation(LinearLayout.VERTICAL);
        int surface = colors.neu ? NeuUi.surface : colors.dark ? 0xFF26272C : 0xFFFFFFFF;
        root.setBackground(Ui.round(surface, Ui.dp(ui, 28)));
        root.setPadding(0, Ui.dp(ui, 24), 0, Ui.dp(ui, 16));
        int side = Ui.dp(ui, 24);
        if (b.title != null) {
            TextView t = text(b.title, 22, colors.text);
            t.setPadding(side, 0, side, Ui.dp(ui, b.message != null ? 12 : 16));
            root.addView(t);
        }
        if (b.message != null) {
            TextView m = text(b.message, 15, colors.hint);
            m.setLineSpacing(Ui.dp(ui, 3), 1f);
            m.setPadding(side, 0, side, Ui.dp(ui, 16));
            addMessage(root, m);
        }
        addMiddle(root, b);
        LinearLayout bar = new LinearLayout(ui);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(Ui.dp(ui, 12), Ui.dp(ui, 8), Ui.dp(ui, 12), 0);
        neutral = button(b.neutral, BUTTON_NEUTRAL, b.neutralListener, bar);
        View gap = new View(ui);
        bar.addView(gap, new LinearLayout.LayoutParams(0, 1, 1f));
        negative = button(b.negative, BUTTON_NEGATIVE, b.negativeListener, bar);
        positive = button(b.positive, BUTTON_POSITIVE, b.positiveListener, bar);
        if (bar.getChildCount() > 1) root.addView(bar, SettingsWidgets.matchWrap());
        return root;
    }

    // ---------------------------------------------------------------- Metro 모양

    private View buildMetro(Builder b) {
        FrameLayout frame = new FrameLayout(ui);
        // 막: Windows Phone의 CustomMessageBox는 바탕색(PhoneBackgroundColor)을 0x99로 깐다 (다크는 검정, 라이트는 흰색).
        overlay = new View(ui);
        overlay.setBackgroundColor((Metro.bg & 0x00FFFFFF) | 0x99000000);
        overlay.setOnClickListener(v -> {
            if (cancelableNow()) cancel();
        });
        frame.addView(overlay, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        Column root = new Column(ui, Ui.dp(ui, 72));
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Metro.chrome);
        root.setClickable(true);   // 상자를 눌러도 막에 닿지 않게
        int side = Ui.dp(ui, 18);
        root.setPadding(0, Ui.dp(ui, 12), 0, Ui.dp(ui, 9));
        if (b.title != null) {
            TextView t = text(b.title, 24, Metro.fg);
            t.setTypeface(Metro.semibold());
            t.setPadding(side, Ui.dp(ui, 6), side, Ui.dp(ui, b.message != null ? 8 : 14));
            root.addView(t);
        }
        if (b.message != null) {
            TextView m = text(b.message, 17, Metro.fg);
            m.setTypeface(Metro.light());
            m.setPadding(side, 0, side, Ui.dp(ui, 14));
            addMessage(root, m);
        }
        addMiddle(root, b);
        // 버튼: 같은 폭으로 나란히 (Windows Phone은 확인이 왼쪽, 취소가 오른쪽).
        LinearLayout bar = new LinearLayout(ui);
        bar.setPadding(Ui.dp(ui, 9), Ui.dp(ui, 12), Ui.dp(ui, 9), 0);
        positive = button(b.positive, BUTTON_POSITIVE, b.positiveListener, bar);
        neutral = button(b.neutral, BUTTON_NEUTRAL, b.neutralListener, bar);
        negative = button(b.negative, BUTTON_NEGATIVE, b.negativeListener, bar);
        if (bar.getChildCount() > 0) root.addView(bar, SettingsWidgets.matchWrap());
        box = root;
        frame.addView(root, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP));
        // 상태 표시줄 높이만큼 상자 안쪽을 내린다 (상자의 바탕은 상태 표시줄 뒤까지 칠해진다).
        int padTop = root.getPaddingTop();
        frame.setOnApplyWindowInsetsListener((v, insets) -> {
            int top;
            if (Build.VERSION.SDK_INT >= 30) {
                top = insets.getInsets(android.view.WindowInsets.Type.statusBars()
                        | android.view.WindowInsets.Type.displayCutout()).top;
            } else {
                top = 0;   // Android 10 이하: 창이 상태 표시줄 아래에서 시작한다
            }
            root.setPadding(root.getPaddingLeft(), padTop + top, root.getPaddingRight(), root.getPaddingBottom());
            return insets;
        });
        return frame;
    }

    private boolean cancelableNow() {
        // Dialog.mCancelable은 숨겨져 있으므로 만들 때 정한 값을 따로 기억한다.
        return cancelable;
    }

    private boolean cancelable = true;

    @Override
    public void setCancelable(boolean flag) {
        super.setCancelable(flag);
        cancelable = flag;
    }

    // ---------------------------------------------------------------- 가운데 (목록·직접 만든 뷰)

    /**
     * 글을 스크롤 안에 둔다: 라이선스 원문처럼 화면보다 긴 글이면 이 부분만 줄어들어(스크롤) 아래쪽과 버튼이 잘리지 않는다.
     * 짧은 글은 글 높이만큼만 차지한다.
     */
    private void addMessage(LinearLayout root, TextView m) {
        ScrollView scroll = new ClipScroll(ui);
        scroll.setVerticalScrollBarEnabled(!metro);   // Metro에는 스크롤 막대가 없다
        scroll.setVerticalFadingEdgeEnabled(true);
        scroll.addView(m);
        root.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
    }

    /** 목록이나 직접 만든 뷰를 둔다. 화면보다 길면 이 부분만 줄어들어(스크롤) 버튼은 늘 보인다. */
    private void addMiddle(LinearLayout root, Builder b) {
        View middle = null;
        if (b.items != null) {
            ScrollView scroll = new ClipScroll(ui);
            scroll.setVerticalScrollBarEnabled(!com.alternative_studios.newswipe.ui.Metro.on);   // Metro에는 스크롤 막대가 없다
            scroll.setVerticalFadingEdgeEnabled(true);
            rows = new LinearLayout(ui);
            rows.setOrientation(LinearLayout.VERTICAL);
            scroll.addView(rows);
            Mark[] marks = new Mark[b.items.length];
            for (int i = 0; i < b.items.length; i++) rows.addView(row(b, i, marks));
            if (b.choice == Builder.SINGLE && b.checkedItem >= 0 && b.checkedItem < b.items.length) {
                View checked = rows.getChildAt(b.checkedItem);
                // 고른 항목이 처음부터 보이게 (AlertDialog의 목록과 같다).
                scroll.post(() -> scroll.scrollTo(0, Math.max(0, checked.getTop() - scroll.getHeight() / 3)));
            }
            middle = scroll;
        } else if (b.view != null) {
            middle = b.view;
            if (middle.getParent() instanceof ViewGroup) ((ViewGroup) middle.getParent()).removeView(middle);
        }
        if (middle != null) {
            root.addView(middle, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        }
    }

    private View row(Builder b, int index, Mark[] marks) {
        LinearLayout row = new LinearLayout(ui);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setMinimumHeight(Ui.dp(ui, metro ? 56 : 52));
        int side = Ui.dp(ui, metro ? 18 : 24);
        row.setPadding(side, Ui.dp(ui, 6), side, Ui.dp(ui, 6));
        TextView t = text(b.items[index], metro ? 22 : 16, metro ? Metro.fg : colors.text);
        if (metro) t.setTypeface(Metro.light());
        Mark mark = null;
        boolean single = b.choice == Builder.SINGLE, multi = b.choice == Builder.MULTI;
        // Metro의 한 개 고르기는 표시 없이 고른 항목의 글자가 강조색이다 (Windows Phone의 ListPicker).
        if (multi || (single && !metro)) {
            mark = new Mark(ui, multi, metro);
            boolean on = multi ? b.checkedItems != null && b.checkedItems[index] : index == b.checkedItem;
            mark.setChecked(on);
            LinearLayout.LayoutParams mlp = new LinearLayout.LayoutParams(Ui.dp(ui, 24), Ui.dp(ui, 24));
            mlp.rightMargin = Ui.dp(ui, metro ? 14 : 20);
            row.addView(mark, mlp);
        }
        marks[index] = mark;
        if (single && metro && index == b.checkedItem) t.setTextColor(colors.accent);
        row.addView(t, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        row.setBackground(metro ? null : Ui.ripple((colors.text & 0x00FFFFFF) | 0x1F000000, null, 0));
        row.setClickable(true);
        row.setFocusable(true);
        final Mark m = mark;
        row.setOnClickListener(v -> {
            if (multi) {
                boolean on = !m.checked;
                m.setChecked(on);
                if (b.checkedItems != null) b.checkedItems[index] = on;
                if (b.multiListener != null) b.multiListener.onClick(this, index, on);
                return;
            }
            if (single) {
                for (int i = 0; i < marks.length; i++) {
                    if (marks[i] != null) marks[i].setChecked(i == index);
                    if (metro) {
                        TextView label = (TextView) ((ViewGroup) rows.getChildAt(i)).getChildAt(((ViewGroup) rows.getChildAt(i)).getChildCount() - 1);
                        label.setTextColor(i == index ? colors.accent : Metro.fg);
                    }
                }
                if (b.itemListener != null) b.itemListener.onClick(this, index);
                return;   // AlertDialog처럼 한 개 고르기는 저절로 닫히지 않는다 (부른 쪽이 닫는다)
            }
            if (b.itemListener != null) b.itemListener.onClick(this, index);
            dismiss();
        });
        Metro.tilt(row);
        row.setContentDescription(b.items[index]);
        return row;
    }

    // ---------------------------------------------------------------- 조각

    private TextView text(CharSequence s, float sp, int color) {
        TextView t = new TextView(ui);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color);
        return t;
    }

    /** 버튼 하나를 만들어 bar에 더한다 (label이 없으면 만들지 않고 null). 누르면 listener를 부르고 창을 닫는다. */
    private Button button(CharSequence label, int which, DialogInterface.OnClickListener listener, LinearLayout bar) {
        if (label == null) return null;
        Button b = new Button(ui);
        b.setText(metro ? Metro.lower(label.toString()) : label);
        b.setGravity(Gravity.CENTER);
        b.setSingleLine(true);
        b.setEllipsize(android.text.TextUtils.TruncateAt.END);
        if (metro) {
            // Windows Phone의 버튼: 글자 색 테두리, 누르면 강조색으로 채워진다.
            b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
            b.setTypeface(Metro.regular());
            android.graphics.drawable.GradientDrawable idle = Ui.round(0, 0);
            idle.setStroke(Ui.dp(ui, 2), Metro.fg);
            android.graphics.drawable.GradientDrawable down = Ui.round(colors.accent, 0);
            android.graphics.drawable.StateListDrawable bg = new android.graphics.drawable.StateListDrawable();
            bg.addState(new int[]{android.R.attr.state_pressed}, down);
            bg.addState(new int[0], idle);
            b.setBackground(bg);
            b.setTextColor(new android.content.res.ColorStateList(new int[][]{{android.R.attr.state_pressed}, {}},
                    new int[]{colors.onAccent, Metro.fg}));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, Ui.dp(ui, 54), 1f);
            lp.leftMargin = lp.rightMargin = Ui.dp(ui, 4);
            bar.addView(b, lp);
            Metro.tilt(b);
        } else {
            b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
            b.setTypeface(android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL));
            b.setTextColor(colors.accent);
            int h = Ui.dp(ui, 16);
            b.setPadding(h, 0, h, 0);
            b.setMinWidth(Ui.dp(ui, 48));
            b.setBackground(Ui.ripple((colors.accent & 0x00FFFFFF) | 0x29000000, null, Ui.dp(ui, 20)));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, Ui.dp(ui, 40));
            lp.leftMargin = Ui.dp(ui, 4);
            if (colors.neu) {
                // Neumorphism: 솟아오른 알약 버튼, 누르는 동안 들어간다. 그림자 자리만큼 띄운다.
                NeuUi.button(b, NeuUi.surface, Ui.dp(ui, 20));
                lp.leftMargin = Ui.dp(ui, 12);
                lp.topMargin = lp.bottomMargin = Ui.dp(ui, 6);
            }
            bar.addView(b, lp);
        }
        b.setOnClickListener(v -> {
            if (listener != null) listener.onClick(this, which);
            dismiss();
        });
        return b;
    }

    /** 대화상자 버튼: 끄면 흐려진다 (AlertDialog의 버튼처럼 getButton(...).setEnabled(false)를 쓴다). */
    private static final class Button extends TextView {
        Button(Context c) {
            super(c);
            setClickable(true);
            setFocusable(true);
        }

        @Override
        public void setEnabled(boolean enabled) {
            super.setEnabled(enabled);
            setAlpha(enabled ? 1f : 0.38f);
        }
    }

    /**
     * 내용을 스스로 자기 영역으로 잘라 그리는 스크롤. Neumorphism에서는 버튼 그림자가 잘리지 않게 상자(Column)가 자식을 자르지
     * 않으므로(NeuUi.unclip), 보통 ScrollView는 긴 글·목록이 영역 밖(버튼 뒤)까지 그려진다.
     */
    private static final class ClipScroll extends ScrollView {
        ClipScroll(Context c) {
            super(c);
        }

        @Override
        protected void dispatchDraw(Canvas canvas) {
            canvas.save();
            canvas.clipRect(getScrollX(), getScrollY(), getScrollX() + getWidth(), getScrollY() + getHeight());
            super.dispatchDraw(canvas);
            canvas.restore();
        }
    }

    /**
     * 높이를 화면보다 margin만큼 덜 쓰는 세로 줄. 내용이 길면 무게(1)를 준 가운데 부분(목록·뷰)만 줄어들고 제목과 버튼은 그대로 보인다.
     */
    private static final class Column extends LinearLayout {
        private final int margin;

        Column(Context c, int margin) {
            super(c);
            this.margin = margin;
        }

        @Override
        protected void onMeasure(int widthSpec, int heightSpec) {
            if (MeasureSpec.getMode(heightSpec) != MeasureSpec.UNSPECIFIED) {
                heightSpec = MeasureSpec.makeMeasureSpec(Math.max(0, MeasureSpec.getSize(heightSpec) - margin), MeasureSpec.AT_MOST);
            }
            super.onMeasure(widthSpec, heightSpec);
        }
    }

    /**
     * 라디오·체크 표시. 보통은 강조색 동그라미·둥근 네모, Metro는 Windows Phone의 체크 상자(글자 색 테두리의 네모와 체크).
     */
    private final class Mark extends View {
        private final boolean box, square;
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path check = new Path();
        private final android.graphics.RectF tmp = new android.graphics.RectF();
        boolean checked;

        Mark(Context c, boolean box, boolean metroLook) {
            super(c);
            this.box = box;
            this.square = metroLook;
            setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        }

        void setChecked(boolean on) {
            if (checked == on) return;
            checked = on;
            invalidate();
        }

        @Override
        protected void onDraw(Canvas c) {
            float dp = Ui.dp(getContext(), 1);
            float w = getWidth(), h = getHeight(), cx = w / 2f, cy = h / 2f;
            int on = square ? Metro.fg : colors.accent, off = square ? Metro.fg : colors.hint;
            p.setStrokeCap(Paint.Cap.ROUND);
            p.setStrokeJoin(Paint.Join.ROUND);
            if (NeuUi.on) {   // Neumorphism: 안으로 들어간 홈에 강조색 점·체크
                float s = 10 * dp;
                tmp.set(cx - s, cy - s, cx + s, cy + s);
                NeuUi.drawInset(getContext(), c, tmp, box ? 5 * dp : s, NeuUi.surface, p);
                if (!checked) return;
                p.setStyle(Paint.Style.FILL);
                p.setColor(colors.accent);
                if (!box) {
                    c.drawCircle(cx, cy, 4.5f * dp, p);
                    return;
                }
                float k = 6.5f * dp;
                check.reset();
                check.moveTo(cx - k * 0.5f, cy + k * 0.02f);
                check.lineTo(cx - k * 0.12f, cy + k * 0.4f);
                check.lineTo(cx + k * 0.55f, cy - k * 0.38f);
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(2.2f * dp);
                c.drawPath(check, p);
                return;
            }
            if (!box) {   // 라디오
                float r = 9 * dp;
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(2 * dp);
                p.setColor(checked ? on : off);
                c.drawCircle(cx, cy, r, p);
                if (checked) {
                    p.setStyle(Paint.Style.FILL);
                    c.drawCircle(cx, cy, 5 * dp, p);
                }
                return;
            }
            float s = 9 * dp;
            p.setStrokeWidth(2 * dp);
            if (square) {   // Windows Phone 체크 상자: 테두리는 그대로, 켜지면 안에 체크
                p.setStyle(Paint.Style.STROKE);
                p.setColor(off);
                c.drawRect(cx - s, cy - s, cx + s, cy + s, p);
            } else {
                p.setStyle(checked ? Paint.Style.FILL : Paint.Style.STROKE);
                p.setColor(checked ? on : off);
                c.drawRoundRect(cx - s, cy - s, cx + s, cy + s, 3 * dp, 3 * dp, p);
            }
            if (checked) {
                check.reset();
                check.moveTo(cx - s * 0.5f, cy + s * 0.02f);
                check.lineTo(cx - s * 0.12f, cy + s * 0.4f);
                check.lineTo(cx + s * 0.55f, cy - s * 0.38f);
                p.setStyle(Paint.Style.STROKE);
                p.setColor(square ? on : colors.onAccent);
                c.drawPath(check, p);
            }
        }
    }

    // ---------------------------------------------------------------- 보이기·닫기

    @Override
    public void show() {
        // AlertDialog처럼: 글자를 칠 칸이 없는 창은 키보드를 가져가지 않는다 (열려 있던 키보드가 그대로 남는다).
        Window w = getWindow();
        if (w != null) {
            View decor = w.getDecorView();
            if (!hasTextEditor(decor)) w.setFlags(WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM,
                    WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM);
            else w.clearFlags(WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM);
        }
        super.show();
        if (w != null) {
            View decor = w.getDecorView();
            // 직접 만든 뷰 안의 입력란 밑줄·커서, 체크 상자를 앱의 강조 색으로 칠한다.
            decor.post(() -> AppTheme.tintViews(decor, colors.accent, colors.hint));
        }
        if (metro && box != null) swivelIn();
    }

    private static boolean hasTextEditor(View v) {
        if (v.onCheckIsTextEditor()) return true;
        if (v instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) if (hasTextEditor(g.getChildAt(i))) return true;
        }
        return false;
    }

    /**
     * SwivelBackwardIn.xaml: 처음 200ms는 보이지 않다가(-45° → -30°), 나타나며 -30° → 0°로 150ms 동안 선다 (EaseOut 지수 6).
     * 막은 바로 깔려 누른 것에 곧바로 반응한다.
     */
    private void swivelIn() {
        box.setAlpha(0f);
        box.setRotationX(-45f);
        box.addOnLayoutChangeListener(new View.OnLayoutChangeListener() {
            @Override
            public void onLayoutChange(View v, int l, int t, int r, int b, int ol, int ot, int or, int ob) {
                box.removeOnLayoutChangeListener(this);
                box.setPivotX(box.getWidth() / 2f);
                box.setPivotY(box.getHeight() / 2f);
            }
        });
        Metro.perspective(box);
        if (anim != null) anim.cancel();
        anim = ValueAnimator.ofFloat(0f, 1f);
        anim.setDuration(350);
        anim.setInterpolator(new android.view.animation.LinearInterpolator());
        anim.addUpdateListener(a -> {
            float ms = a.getAnimatedFraction() * 350f;
            if (ms < 200f) {
                box.setAlpha(0f);
                box.setRotationX(-45f + 15f * Metro.EASE_OUT.getInterpolation(ms / 200f));
            } else {
                box.setAlpha(1f);
                box.setRotationX(-30f + 30f * Metro.EASE_OUT.getInterpolation((ms - 200f) / 150f));
            }
        });
        anim.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                if (!leaving) {
                    box.setAlpha(1f);
                    box.setRotationX(0f);
                }
            }
        });
        anim.start();
    }

    /** Metro: SwivelBackwardOut.xaml (0° → 60°, 250ms, EaseIn 지수 6, 끝에서 사라짐) 뒤에 닫는다. */
    @Override
    public void dismiss() {
        if (!metro || box == null || leaving || !isShowing() || !box.isAttachedToWindow() || ownerGone()) {
            if (anim != null) anim.cancel();
            super.dismiss();
            return;
        }
        leaving = true;
        if (anim != null) anim.cancel();
        box.setPivotX(box.getWidth() / 2f);
        box.setPivotY(box.getHeight() / 2f);
        float from = box.getRotationX();
        anim = ValueAnimator.ofFloat(0f, 1f);
        anim.setDuration(250);
        anim.setInterpolator(new android.view.animation.LinearInterpolator());
        anim.addUpdateListener(a -> {
            float t = a.getAnimatedFraction();
            box.setRotationX(from + (60f - from) * Metro.EASE_IN.getInterpolation(t));
            box.setAlpha(t >= 0.96f ? 0f : 1f);
        });
        anim.addListener(new AnimatorListenerAdapter() {
            private boolean done;

            @Override
            public void onAnimationEnd(Animator animation) {
                if (done) return;
                done = true;
                // 움직이는 동안 화면이 닫혔으면 창이 이미 없어 dismiss가 IllegalArgumentException을 낸다 (앱이 죽지 않게).
                try {
                    AppDialog.super.dismiss();
                } catch (IllegalArgumentException ignored) {
                    // 창이 이미 화면과 함께 사라졌다
                }
            }

            @Override
            public void onAnimationCancel(Animator animation) {
                onAnimationEnd(animation);
            }
        });
        anim.start();
    }

    /** 창을 띄운 화면이 닫히는 중이면 움직임 없이 바로 닫는다 (창이 화면보다 오래 남지 않게). */
    private boolean ownerGone() {
        Context c = ui;
        while (c instanceof android.content.ContextWrapper && !(c instanceof Activity)) {
            c = ((android.content.ContextWrapper) c).getBaseContext();
        }
        return c instanceof Activity && (((Activity) c).isFinishing() || ((Activity) c).isDestroyed());
    }

    // ---------------------------------------------------------------- 만들기

    /** AlertDialog.Builder처럼 쓰는 만들기 도구. 부르는 쪽 코드는 AlertDialog일 때와 같다. */
    public static final class Builder {
        static final int NONE = 0, SINGLE = 1, MULTI = 2;
        final Context context;
        final AppTheme colors;
        CharSequence title, message, positive, negative, neutral;
        DialogInterface.OnClickListener positiveListener, negativeListener, neutralListener, itemListener;
        DialogInterface.OnMultiChoiceClickListener multiListener;
        DialogInterface.OnCancelListener onCancel;
        DialogInterface.OnDismissListener onDismiss;
        View view;
        CharSequence[] items;
        int choice = NONE, checkedItem = -1;
        boolean[] checkedItems;
        boolean cancelable = true;

        /** @param themed 대화상자 안의 뷰를 만들 컨텍스트 (AppTheme.dialogContext로 감싼 화면) */
        public Builder(Context themed) {
            context = themed;
            colors = AppTheme.of(themed);
        }

        public Context getContext() {
            return context;
        }

        public Builder setTitle(CharSequence t) {
            title = t;
            return this;
        }

        public Builder setMessage(CharSequence m) {
            message = m;
            return this;
        }

        public Builder setView(View v) {
            view = v;
            return this;
        }

        public Builder setItems(CharSequence[] list, DialogInterface.OnClickListener l) {
            items = list;
            choice = NONE;
            itemListener = l;
            return this;
        }

        public Builder setSingleChoiceItems(CharSequence[] list, int checked, DialogInterface.OnClickListener l) {
            items = list;
            choice = SINGLE;
            checkedItem = checked;
            itemListener = l;
            return this;
        }

        public Builder setMultiChoiceItems(CharSequence[] list, boolean[] checked, DialogInterface.OnMultiChoiceClickListener l) {
            items = list;
            choice = MULTI;
            checkedItems = checked;
            multiListener = l;
            return this;
        }

        public Builder setPositiveButton(CharSequence label, DialogInterface.OnClickListener l) {
            positive = label;
            positiveListener = l;
            return this;
        }

        public Builder setNegativeButton(CharSequence label, DialogInterface.OnClickListener l) {
            negative = label;
            negativeListener = l;
            return this;
        }

        public Builder setNeutralButton(CharSequence label, DialogInterface.OnClickListener l) {
            neutral = label;
            neutralListener = l;
            return this;
        }

        public Builder setCancelable(boolean c) {
            cancelable = c;
            return this;
        }

        public Builder setOnCancelListener(DialogInterface.OnCancelListener l) {
            onCancel = l;
            return this;
        }

        public Builder setOnDismissListener(DialogInterface.OnDismissListener l) {
            onDismiss = l;
            return this;
        }

        public AppDialog create() {
            return new AppDialog(this);
        }

        public AppDialog show() {
            AppDialog d = create();
            d.show();
            return d;
        }
    }
}
