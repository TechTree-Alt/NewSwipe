package com.alternative_studios.newswipe.clipboard;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Outline;
import android.graphics.drawable.Drawable;
import android.view.ContextThemeWrapper;
import android.view.WindowManager;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.view.ViewTreeObserver;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.alternative_studios.newswipe.keyboard.Icons;
import com.alternative_studios.newswipe.keyboard.KeyboardTheme;
import com.alternative_studios.newswipe.ui.IconButton;
import com.alternative_studios.newswipe.ui.Ui;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

/** 클립보드 기록 화면. 항목을 누르면 붙여넣고, 오른쪽 버튼으로 고정·삭제할 수 있다. 이미지는 미리보기로 보인다. */
@SuppressLint("ViewConstructor")
public final class ClipboardPanel extends LinearLayout {

    public interface Listener {
        void onClipPaste(String text);

        void onClipPasteImage(ClipboardHistory.Item item);

        /** 이미지 미리보기를 백그라운드에서 읽어 메인 스레드에서 돌려준다 (읽지 못하면 null). */
        void onClipThumbnail(ClipboardHistory.Item item, Consumer<Bitmap> done);

        void onClipBack();

        void onClipPin(ClipboardHistory.Item item, boolean pinned);

        void onClipDelete(ClipboardHistory.Item item);

        void onClipClear();
    }

    private final KeyboardTheme theme;
    private final Listener listener;
    private final LinearLayout list;
    private final TextView clear;
    /** 고정·삭제를 누른 뒤 다음 show()에서 항목이 새 자리로 움직이는 애니메이션을 보여 준다. */
    private boolean animateNext;
    private static final long MOVE_MS = 200;
    /** 항목 텍스트 크기(sp)와 최대 줄 수. 이미지도 이 줄 수만큼의 높이를 넘지 않는다. */
    private static final float TEXT_SP = 14;
    private static final int MAX_LINES = 4;
    private int maxContentHeight;
    /** 항목 사이 간격 (dp). */
    private static final int GAP_DP = 6;
    /** 한 칸의 목표 폭 (dp). */
    private static final int CELL_DP = 190;
    /** 읽어 둔 이미지 미리보기 (이미지 이름 → 그림). 패널을 놓으면 함께 사라지고, 기록에서 빠진 이미지는 show()에서 버린다. */
    private final Map<String, Bitmap> thumbs = new HashMap<>();

    public ClipboardPanel(Context context, KeyboardTheme theme, int topBarHeight, Listener listener) {
        super(context);
        this.theme = theme;
        this.listener = listener;
        setOrientation(VERTICAL);
        setBackgroundColor(theme.background);

        LinearLayout header = new LinearLayout(context);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(Ui.dp(context, 4), 0, Ui.dp(context, 8), 0);
        IconButton back = new IconButton(context, Icons.BACK, theme.text, theme.keyPressed, "키보드로 돌아가기");
        back.setOnClickListener(v -> listener.onClipBack());
        header.addView(back, new LayoutParams(Ui.dp(context, 44), Ui.dp(context, 40)));
        TextView title = new TextView(context);
        title.setText("클립보드");
        title.setTextColor(theme.text);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        header.addView(title, new LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        clear = new TextView(context);
        clear.setText("모두 지우기");
        clear.setTextColor(theme.accent);
        clear.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        clear.setPadding(Ui.dp(context, 12), Ui.dp(context, 8), Ui.dp(context, 12), Ui.dp(context, 8));
        clear.setBackground(Ui.ripple(theme.keyPressed, null, Ui.dp(context, 16)));
        clear.setOnClickListener(v -> confirmClear());
        header.addView(clear);
        addView(header, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, topBarHeight));

        ScrollView scroll = new ScrollView(context);
        scroll.setVerticalScrollBarEnabled(false);
        list = new LinearLayout(context);
        list.setOrientation(VERTICAL);
        int p = Ui.dp(context, 8);
        list.setPadding(p, 0, p, p);
        scroll.addView(list, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        addView(scroll, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
    }

    /**
     * @param enabled 설정에서 기록 저장이 켜져 있는지
     * @param current 지금 클립보드에 있는 텍스트 (기록을 끈 경우에도 보여 준다)
     */
    public void show(List<ClipboardHistory.Item> items, boolean enabled, String current) {
        Map<String, int[]> before = new HashMap<>();
        if (animateNext) {
            for (View card : cards()) before.put((String) card.getTag(), position(card));
        }
        animateNext = false;
        list.removeAllViews();
        Set<String> shown = new HashSet<>();
        for (ClipboardHistory.Item item : items) if (item.isImage()) shown.add(item.text);
        thumbs.keySet().retainAll(shown);
        clear.setVisibility(enabled && !items.isEmpty() ? VISIBLE : GONE);
        if (!enabled) {
            if (current != null && !current.isEmpty()) {
                View card = itemView(current, null);
                LayoutParams lp = new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                lp.topMargin = Ui.dp(getContext(), GAP_DP);
                list.addView(card, lp);
            }
            list.addView(note("클립보드 기록이 꺼져 있습니다. 설정에서 켤 수 있습니다."));
            return;
        }
        if (items.isEmpty()) {
            list.addView(note("복사하거나 잘라낸 텍스트와 이미지가 여기에 모입니다.\n누르면 바로 붙여넣습니다."));
            return;
        }
        // 한 줄에 columns() 칸씩 놓는다. 한 줄의 항목은 높이를 맞춘다.
        Context c = getContext();
        int gap = Ui.dp(c, GAP_DP);
        int cols = columns();
        for (int i = 0; i < items.size(); i += cols) {
            LinearLayout row = new LinearLayout(c);
            row.setBaselineAligned(false);
            for (int k = i; k < i + cols; k++) {
                View card = k < items.size() ? itemView(items.get(k).text, items.get(k)) : new View(c);
                LayoutParams lp = new LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f);
                if (k > i) lp.leftMargin = gap;
                row.addView(card, lp);
            }
            LayoutParams lp = new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.topMargin = gap;
            list.addView(row, lp);
        }
        if (!before.isEmpty()) animateMoves(before);
        list.addView(note("고정하지 않은 항목은 24시간 뒤 지워지며, 최근 "
                + ClipboardHistory.MAX_ITEMS + "개(이미지는 " + ClipboardHistory.MAX_IMAGES
                + "개)까지 보관합니다. 아래 버튼으로 고정(최대 "
                + ClipboardHistory.MAX_PINNED + "개)하거나 삭제할 수 있습니다."));
    }

    /** 한 줄의 칸 수: 화면 폭에 맞춰 칸 폭이 CELL_DP쯤 되도록 한다 (보통 휴대폰 세로 화면은 2칸, 가로 모드·대화면은 더 많이). */
    private int columns() {
        int widthDp = getResources().getConfiguration().screenWidthDp;
        return Math.max(2, Math.min(8, widthDp / CELL_DP));
    }

    /** 기록 항목 카드들 (각 줄 안에 있다. 안내 문구와 빈자리는 빼고). */
    private List<View> cards() {
        List<View> out = new java.util.ArrayList<>();
        for (int i = 0; i < list.getChildCount(); i++) {
            View child = list.getChildAt(i);
            if (!(child instanceof LinearLayout) || child.getTag() != null) continue;
            ViewGroup row = (ViewGroup) child;
            for (int k = 0; k < row.getChildCount(); k++) {
                if (row.getChildAt(k).getTag() instanceof String) out.add(row.getChildAt(k));
            }
        }
        return out;
    }

    /** 목록 안에서 카드의 자리 (줄의 자리 + 줄 안의 자리). */
    private static int[] position(View card) {
        View row = (View) card.getParent();
        return new int[]{row.getLeft() + card.getLeft(), row.getTop() + card.getTop()};
    }

    /** 항목이 원래 자리에서 새 자리로 짧게 미끄러지게 한다 (옆 칸·윗줄로도). 원래 없던 항목은 서서히 나타난다. */
    private void animateMoves(Map<String, int[]> before) {
        list.getViewTreeObserver().addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
            @Override
            public boolean onPreDraw() {
                list.getViewTreeObserver().removeOnPreDrawListener(this);
                for (View card : cards()) {
                    int[] old = before.get((String) card.getTag());
                    int[] now = position(card);
                    if (old == null) {
                        card.setAlpha(0f);
                        card.animate().alpha(1f).setDuration(MOVE_MS).start();
                    } else if (old[0] != now[0] || old[1] != now[1]) {
                        card.setTranslationX(old[0] - now[0]);
                        card.setTranslationY(old[1] - now[1]);
                        card.animate().translationX(0f).translationY(0f).setDuration(MOVE_MS)
                                .setInterpolator(new DecelerateInterpolator()).start();
                    }
                }
                return true;
            }
        });
    }

    /** 항목 카드: 위에 내용(텍스트 또는 이미지), 아래 오른쪽에 고정·삭제 버튼. */
    private View itemView(String text, ClipboardHistory.Item item) {
        Context c = getContext();
        LinearLayout card = new LinearLayout(c);
        card.setOrientation(VERTICAL);
        card.setBackground(Ui.ripple(theme.keyPressed, Ui.round(theme.key, Ui.dp(c, 10)), Ui.dp(c, 10)));
        int p = Ui.dp(c, 10);
        card.setPadding(p, p, p, item == null ? p : 0);
        if (item != null && item.isImage()) {
            card.addView(imageView(item), new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
            card.setOnClickListener(v -> listener.onClipPasteImage(item));
        } else {
            TextView t = new TextView(c);
            t.setText(text.length() > 400 ? text.substring(0, 400) : text);
            t.setTextColor(theme.text);
            t.setTextSize(TypedValue.COMPLEX_UNIT_SP, TEXT_SP);
            t.setMaxLines(MAX_LINES);
            t.setEllipsize(TextUtils.TruncateAt.END);
            card.addView(t, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
            card.setOnClickListener(v -> listener.onClipPaste(text));
        }
        if (item != null) {
            card.setTag(item.key());   // 다시 그릴 때 같은 항목을 찾아 움직임을 이어 준다
            LinearLayout bar = new LinearLayout(c);
            bar.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
            LayoutParams barLp = new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            barLp.rightMargin = -Ui.dp(c, 6);   // 버튼 안쪽 여백만큼 카드 오른쪽 끝에 붙인다
            card.addView(bar, barLp);
            // 고정 / 삭제 버튼 (고정된 항목의 핀은 강조색)
            IconButton pin = new IconButton(c, item.pinned ? Icons.PIN_FILLED : Icons.PIN, item.pinned ? theme.accent : theme.hint, theme.keyPressed,
                    item.pinned ? "고정 해제" : "고정");
            pin.setOnClickListener(v -> {
                animateNext = true;
                listener.onClipPin(item, !item.pinned);
            });
            bar.addView(pin, new LayoutParams(Ui.dp(c, 36), Ui.dp(c, 36)));
            IconButton trash = new IconButton(c, Icons.TRASH, theme.hint, theme.keyPressed, "삭제");
            trash.setOnClickListener(v -> {
                animateNext = true;
                listener.onClipDelete(item);
            });
            bar.addView(trash, new LayoutParams(Ui.dp(c, 36), Ui.dp(c, 36)));
        }
        return card;
    }

    /** 이미지 미리보기. 읽어 둔 것이 없으면 자리를 잡아 두고 백그라운드에서 읽어 채운다. */
    private View imageView(ClipboardHistory.Item item) {
        Context c = getContext();
        FrameLayout box = new FrameLayout(c);
        ImageView img = new ThumbView(c, maxContentHeight(), Ui.dp(c, 56));
        img.setContentDescription("복사한 이미지");
        int r = Ui.dp(c, 6);
        img.setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View view, Outline outline) {
                outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), r);
            }
        });
        img.setClipToOutline(true);
        box.addView(img, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER));
        Bitmap cached = thumbs.get(item.text);
        if (cached != null) {
            img.setImageBitmap(cached);
        } else {
            listener.onClipThumbnail(item, b -> {
                if (b == null) return;
                thumbs.put(item.text, b);
                img.setImageBitmap(b);
            });
        }
        return box;
    }

    /**
     * 이미지 미리보기 칸. 카드 너비를 가득 채우도록 이미지 비율대로 키우되, 높이는 카드 너비와 maxHeight(텍스트 4줄)를 넘지 않는다
     * (세로로 긴 이미지는 높이에 맞춰 줄고 가운데에 놓인다). 칸 크기가 이미지와 같아 둥근 모서리가 이미지에 딱 맞는다.
     */
    @SuppressLint("ViewConstructor")
    private static final class ThumbView extends ImageView {
        private final int maxHeight;
        private final int emptyHeight;

        ThumbView(Context context, int maxHeight, int emptyHeight) {
            super(context);
            this.maxHeight = maxHeight;
            this.emptyHeight = emptyHeight;
            setScaleType(ScaleType.FIT_XY);
        }

        @Override
        protected void onMeasure(int widthSpec, int heightSpec) {
            int avail = MeasureSpec.getSize(widthSpec);
            Drawable d = getDrawable();
            if (d == null || d.getIntrinsicWidth() <= 0 || d.getIntrinsicHeight() <= 0 || avail <= 0) {
                setMeasuredDimension(avail, emptyHeight);   // 읽는 동안 자리만 잡아 둔다
                return;
            }
            float ratio = (float) d.getIntrinsicWidth() / d.getIntrinsicHeight();
            int h = Math.max(1, Math.min(Math.round(avail / ratio), Math.min(avail, maxHeight)));
            int w = Math.max(1, Math.min(avail, Math.round(h * ratio)));
            setMeasuredDimension(w, h);
        }
    }

    /**
     * 텍스트 MAX_LINES줄의 높이. 이미지 미리보기도 이보다 높아지지 않게 해 모든 항목의 높이를 맞춘다.
     * 한글은 대체 글꼴의 줄 간격이 기본 글꼴보다 커서, 글꼴 수치로 계산하지 않고 항목과 같은 TextView를 실제로 재어 구한다.
     */
    private int maxContentHeight() {
        if (maxContentHeight > 0) return maxContentHeight;
        TextView probe = new TextView(getContext());
        probe.setTextSize(TypedValue.COMPLEX_UNIT_SP, TEXT_SP);
        StringBuilder b = new StringBuilder("가");
        for (int i = 1; i < MAX_LINES; i++) b.append("\n가");
        probe.setText(b);
        int unspecified = MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED);
        probe.measure(unspecified, unspecified);
        maxContentHeight = probe.getMeasuredHeight();
        return maxContentHeight;
    }

    private View note(String s) {
        TextView t = new TextView(getContext());
        t.setText(s);
        t.setTextColor(theme.hint);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f);
        t.setGravity(Gravity.CENTER);
        int p = Ui.dp(getContext(), 16);
        t.setPadding(p, p, p, p);
        return t;
    }

    /** 지우기 전에 확인 창을 띄운다. 키보드 창에 붙은 대화상자로 보여 준다. */
    private AlertDialog confirmDialog;

    /** 키보드가 숨겨지거나 패널이 사라질 때 확인 창도 닫는다 (창이 남아 패널을 붙잡지 않게). */
    public void dismissDialog() {
        if (confirmDialog != null) {
            if (confirmDialog.isShowing()) {
                try {
                    confirmDialog.dismiss();
                } catch (RuntimeException ignored) {
                    // 키보드 창이 이미 사라진 경우
                }
            }
            confirmDialog = null;
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        dismissDialog();
    }

    private void confirmClear() {
        dismissDialog();
        Context themed = new ContextThemeWrapper(getContext(), theme.dark
                ? android.R.style.Theme_DeviceDefault_Dialog_Alert
                : android.R.style.Theme_DeviceDefault_Light_Dialog_Alert);
        AlertDialog dialog = com.alternative_studios.newswipe.AppTheme.accentBuilder(themed)
                .setTitle("클립보드 기록 지우기")
                .setMessage("고정하지 않은 클립보드 기록을 모두 지웁니다. 되돌릴 수 없습니다.")
                .setPositiveButton("모두 지우기", (d, w) -> listener.onClipClear())
                .setNegativeButton("취소", null)
                .create();
        android.view.Window window = dialog.getWindow();
        if (window != null) {
            WindowManager.LayoutParams lp = window.getAttributes();
            lp.token = getWindowToken();
            lp.type = WindowManager.LayoutParams.TYPE_APPLICATION_ATTACHED_DIALOG;
            window.setAttributes(lp);
            window.addFlags(WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM);
        }
        dialog.setOnDismissListener(d -> {
            if (confirmDialog == d) confirmDialog = null;
        });
        confirmDialog = dialog;
        dialog.show();
    }
}
