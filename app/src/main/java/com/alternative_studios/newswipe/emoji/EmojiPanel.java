package com.alternative_studios.newswipe.emoji;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.drawable.ColorDrawable;
import android.os.Handler;
import android.os.Looper;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.animation.DecelerateInterpolator;
import android.widget.BaseAdapter;
import android.widget.FrameLayout;
import android.widget.GridView;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.TextView;

import com.alternative_studios.newswipe.keyboard.Icons;
import com.alternative_studios.newswipe.keyboard.KeyboardTheme;
import com.alternative_studios.newswipe.ui.IconButton;
import com.alternative_studios.newswipe.ui.Ui;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** 이모지 자판: 위쪽 검색창, 가운데 이모지 격자, 아래쪽 분류 탭. */
@SuppressLint("ViewConstructor")
public final class EmojiPanel extends LinearLayout {

    public interface Listener {
        void onEmoji(String emoji);

        void onEmojiDelete();

        void onEmojiBack();

        void onEmojiSearch();

        void onEmojiKeyPress();

        /** 최근 이모지 탭에서 길게 눌렀을 때: 고정돼 있으면 풀고, 아니면 고정한다. */
        void onEmojiPinToggle(String emoji);
    }

    private final KeyboardTheme theme;
    private final Listener listener;
    /** 지금 탭을 보여 주는 쪽. */
    private final Page page;
    /** 좌우로 미는 동안 옆 탭을 미리 보여 주는 쪽. 처음 밀 때 만든다. */
    private Page peek;
    private final TextView back;
    private final View[] tabs = new View[EmojiData.GROUPS.length];
    private final Handler handler = new Handler(Looper.getMainLooper());
    private EmojiData data;
    private List<String> recent = new ArrayList<>();
    private List<String> pinned = new ArrayList<>();
    private int group = 1;
    private PopupWindow tonePopup;

    public EmojiPanel(Context context, KeyboardTheme theme, int topBarHeight, Listener listener) {
        super(context);
        this.theme = theme;
        this.listener = listener;
        setOrientation(VERTICAL);
        setBackgroundColor(theme.background);

        // 위쪽 줄: [←] [검색창]. 화살표는 클립보드 창의 것과 같은 모양·크기다.
        LinearLayout searchRow = new LinearLayout(context);
        searchRow.setGravity(Gravity.CENTER_VERTICAL);
        searchRow.setPadding(Ui.dp(context, 4), 0, Ui.dp(context, 8), 0);
        IconButton topBack = new IconButton(context, Icons.BACK, theme.text, theme.keyPressed, "키보드로 돌아가기");
        topBack.setOnClickListener(v -> {
            listener.onEmojiKeyPress();
            listener.onEmojiBack();
        });
        searchRow.addView(topBack, new LayoutParams(Ui.dp(context, 44), ViewGroup.LayoutParams.MATCH_PARENT));
        LinearLayout search = new LinearLayout(context);
        search.setGravity(Gravity.CENTER_VERTICAL);
        search.setBackground(Ui.ripple(theme.keyPressed, Ui.round(theme.functionKey, Ui.dp(context, 20)),
                Ui.dp(context, 20)));
        search.setPadding(Ui.dp(context, 10), 0, Ui.dp(context, 12), 0);
        IconButton icon = new IconButton(context, Icons.SEARCH, theme.hint, theme.keyPressed, null);
        icon.setClickable(false);
        search.addView(icon, new LayoutParams(Ui.dp(context, 28), ViewGroup.LayoutParams.MATCH_PARENT));
        TextView hint = new TextView(context);
        hint.setText("이모지 검색 (한글·영어)");
        hint.setTextColor(theme.hint);
        hint.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        hint.setPadding(Ui.dp(context, 6), 0, 0, 0);
        search.addView(hint);
        search.setOnClickListener(v -> listener.onEmojiSearch());
        LayoutParams searchLp = new LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f);
        searchLp.topMargin = Ui.dp(context, 6);
        searchLp.bottomMargin = Ui.dp(context, 6);
        searchRow.addView(search, searchLp);
        addView(searchRow, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, topBarHeight));

        // 이모지 격자. 좌우로 밀어서도 탭을 넘기며, 미는 동안 옆 탭이 붙어서 따라 들어온다.
        FrameLayout gridFrame = new SwipeFrame(context);
        page = new Page(context);
        gridFrame.addView(page, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        addView(gridFrame, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        // 아래쪽 줄: [가/ABC] [분류 탭…] [⌫]
        LinearLayout bar = new LinearLayout(context);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(Ui.dp(context, 4), 0, Ui.dp(context, 4), 0);
        back = new TextView(context);
        back.setGravity(Gravity.CENTER);
        back.setTextColor(theme.text);
        back.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        back.setBackground(Ui.ripple(theme.keyPressed, Ui.round(theme.functionKey, Ui.dp(context, 7)),
                Ui.dp(context, 7)));
        back.setOnClickListener(v -> {
            listener.onEmojiKeyPress();
            listener.onEmojiBack();
        });
        LayoutParams side = new LayoutParams(Ui.dp(context, 52), Ui.dp(context, 36));
        bar.addView(back, side);
        LinearLayout tabRow = new LinearLayout(context);
        for (int i = 0; i < tabs.length; i++) {
            View tab;
            if (i == 0) {
                tab = new IconButton(context, Icons.RECENT, theme.hint, theme.keyPressed, EmojiData.GROUP_NAMES[0]);
            } else {
                TextView t = new TextView(context);
                t.setText(EmojiData.GROUP_ICONS[i]);
                t.setGravity(Gravity.CENTER);
                t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 17);
                t.setContentDescription(EmojiData.GROUP_NAMES[i]);
                t.setBackground(Ui.ripple(theme.keyPressed, null, Ui.dp(context, 16)));
                tab = t;
            }
            final int index = i;
            tab.setOnClickListener(v -> showGroup(index));
            tabs[i] = tab;
            tabRow.addView(tab, new LayoutParams(0, Ui.dp(context, 36), 1f));
        }
        // 분류 탭이 양옆의 '가'·지우기 버튼에 붙지 않게 여백을 둔다.
        LayoutParams tabsLp = new LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        tabsLp.leftMargin = Ui.dp(context, 6);
        tabsLp.rightMargin = Ui.dp(context, 6);
        bar.addView(tabRow, tabsLp);
        IconButton del = new IconButton(context, Icons.DELETE, theme.text, theme.keyPressed, "지우기");
        del.setBackground(Ui.ripple(theme.keyPressed, Ui.round(theme.functionKey, Ui.dp(context, 7)),
                Ui.dp(context, 7)));
        setRepeating(del);
        bar.addView(del, new LayoutParams(Ui.dp(context, 52), Ui.dp(context, 36)));
        addView(bar, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(context, 46)));
    }

    public void setBackLabel(String label) {
        back.setText(label);
    }

    public void setData(EmojiData d, List<String> recentEmoji, List<String> pinnedEmoji) {
        data = d;
        recent = recentEmoji;
        pinned = pinnedEmoji;
        showGroup(group == 0 && !hasRecent() ? 1 : group);
    }

    public void setRecent(List<String> recentEmoji) {
        recent = recentEmoji;
        refreshRecent();
    }

    public void setPinned(List<String> pinnedEmoji) {
        pinned = pinnedEmoji;
        refreshRecent();
    }

    /**
     * 최근 탭을 보고 있으면 스크롤 위치를 지킨 채 다시 그린다 (고정을 바꿀 때 맨 위로 튀지 않게).
     * 자리가 바뀐 이모지는 원래 자리에서 새 자리로 짧게 미끄러지고, 새로 보이는 이모지는 서서히 나타난다.
     */
    private void refreshRecent() {
        if (group != 0 || data == null) return;
        GridView grid = page.grid;
        Adapter adapter = page.adapter;
        Map<String, int[]> before = new HashMap<>();
        int first = grid.getFirstVisiblePosition();
        for (int i = 0; i < grid.getChildCount() && first + i < adapter.getCount(); i++) {
            View child = grid.getChildAt(i);
            before.put(adapter.getItem(first + i), new int[]{child.getLeft(), child.getTop()});
        }
        adapter.set(itemsFor(0), 0);
        page.updateNotes();
        if (before.isEmpty()) return;
        grid.getViewTreeObserver().addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
            @Override
            public boolean onPreDraw() {
                grid.getViewTreeObserver().removeOnPreDrawListener(this);
                int top = grid.getFirstVisiblePosition();
                for (int i = 0; i < grid.getChildCount() && top + i < adapter.getCount(); i++) {
                    View child = grid.getChildAt(i);
                    int[] old = before.get(adapter.getItem(top + i));
                    child.animate().cancel();
                    if (old == null) {
                        child.setAlpha(0f);
                        child.animate().alpha(1f).setDuration(MOVE_MS).start();
                        continue;
                    }
                    child.setAlpha(1f);
                    float dx = old[0] - child.getLeft(), dy = old[1] - child.getTop();
                    child.setTranslationX(dx);
                    child.setTranslationY(dy);
                    if (dx != 0 || dy != 0) {
                        child.animate().translationX(0f).translationY(0f).setDuration(MOVE_MS)
                                .setInterpolator(new DecelerateInterpolator()).start();
                    }
                }
                return true;
            }
        });
    }

    private static final long MOVE_MS = 200;

    private boolean hasRecent() {
        return !recent.isEmpty() || !pinned.isEmpty();
    }

    /** 패널을 새로 열 때: 최근 사용이 있으면 그 탭부터. */
    public void reset() {
        group = hasRecent() ? 0 : 1;
        if (data != null) showGroup(group);
    }

    private void showGroup(int index) {
        group = index;
        dismissTones();
        page.show(index);
        for (int i = 0; i < tabs.length; i++) {
            boolean sel = i == index;
            tabs[i].setAlpha(sel ? 1f : 0.55f);
            tabs[i].setBackground(sel
                    ? Ui.round(theme.functionKey, Ui.dp(getContext(), 16))
                    : Ui.ripple(theme.keyPressed, null, Ui.dp(getContext(), 16)));
        }
    }

    /** 탭에 보일 이모지 목록. 데이터를 아직 읽지 않았으면 비어 있다. */
    private List<String> itemsFor(int index) {
        List<String> items = new ArrayList<>();
        if (data == null) return items;
        if (index == 0) return EmojiRepository.withPinned(pinned, recent);
        for (EmojiData.Emoji e : data.group(index)) items.add(e.value);
        return items;
    }

    private boolean showTones(View anchor, String value) {
        if (data == null) return false;
        EmojiData.Emoji e = data.find(value);
        if (e == null || e.variants.length == 0) return false;
        Context c = getContext();
        LinearLayout row = new LinearLayout(c);
        row.setBackground(Ui.round(theme.popup, Ui.dp(c, 10)));
        row.setElevation(Ui.dp(c, 6));
        row.setPadding(Ui.dp(c, 4), Ui.dp(c, 4), Ui.dp(c, 4), Ui.dp(c, 4));
        List<String> all = new ArrayList<>();
        all.add(e.value);
        for (String v : e.variants) all.add(v);
        final PopupWindow pw = new PopupWindow(row, ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, false);   // 포커스를 가져가면 입력이 끊긴다
        for (String v : all) {
            TextView t = new TextView(c);
            t.setText(v);
            t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 26);
            t.setGravity(Gravity.CENTER);
            t.setBackground(Ui.ripple(theme.keyPressed, null, Ui.dp(c, 8)));
            t.setOnClickListener(x -> {
                pw.dismiss();
                listener.onEmojiKeyPress();
                listener.onEmoji(v);
            });
            row.addView(t, new LayoutParams(Ui.dp(c, 44), Ui.dp(c, 48)));
        }
        pw.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        pw.setOutsideTouchable(true);
        pw.setClippingEnabled(false);
        dismissTones();
        tonePopup = pw;
        try {
            int w = Ui.dp(c, 44) * all.size() + Ui.dp(c, 8);
            pw.showAsDropDown(anchor, (anchor.getWidth() - w) / 2, -anchor.getHeight() - Ui.dp(c, 60));
        } catch (RuntimeException ex) {
            return false;
        }
        return true;
    }

    public void dismissTones() {
        if (tonePopup != null) {
            tonePopup.dismiss();
            tonePopup = null;
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        dismissTones();
        handler.removeCallbacksAndMessages(null);
    }

    @SuppressLint("ClickableViewAccessibility")
    private void setRepeating(View v) {
        final Runnable repeat = new Runnable() {
            @Override
            public void run() {
                listener.onEmojiDelete();
                handler.postDelayed(this, 60);
            }
        };
        v.setOnTouchListener((view, ev) -> {
            switch (ev.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    view.setPressed(true);
                    listener.onEmojiKeyPress();
                    listener.onEmojiDelete();
                    handler.postDelayed(repeat, 400);
                    return true;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    view.setPressed(false);
                    handler.removeCallbacks(repeat);
                    return true;
                default:
                    return true;
            }
        });
    }

    private final class Adapter extends BaseAdapter {
        private List<String> items = new ArrayList<>();
        /** 이 목록이 어느 탭인지 (최근 탭에서만 고정 핀을 그린다). */
        private int group = -1;

        void set(List<String> list, int group) {
            items = list;
            this.group = group;
            notifyDataSetChanged();
        }

        /** 알리지 않고 내용만 바꾼다. 다른 탭으로 바꿀 때 격자를 처음 상태로 되돌리는 setAdapter와 함께 쓴다. */
        void replace(List<String> list, int group) {
            items = list;
            this.group = group;
        }

        @Override
        public int getCount() {
            return items.size();
        }

        @Override
        public String getItem(int position) {
            return items.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            EmojiCell t = (EmojiCell) convertView;
            if (t == null) {
                t = new EmojiCell(parent.getContext());
                t.setGravity(Gravity.CENTER);
                t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 26);
                t.setLayoutParams(new GridView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                        Ui.dp(parent.getContext(), 46)));
                t.setBackground(Ui.ripple(theme.keyPressed, null, Ui.dp(parent.getContext(), 10)));
                t.setTextColor(theme.text);
            }
            String e = items.get(position);
            t.setText(e);
            boolean isPinned = group == 0 && pinned.contains(e);   // 이 목록(Adapter)의 탭
            t.setPinned(isPinned);
            t.setContentDescription(isPinned ? e + " (고정됨)" : e);
            return t;
        }
    }

    /**
     * 한 탭의 내용: 이모지 격자, 비어 있을 때의 안내, 최근 탭 끝의 고정 방법 안내.
     * 지금 탭(page)과, 좌우로 미는 동안 옆 탭을 보여 주는 쪽(peek)이 같은 모양을 쓴다.
     */
    @SuppressLint("ViewConstructor")
    private final class Page extends FrameLayout {
        final GridView grid;
        final Adapter adapter = new Adapter();
        private final TextView empty;
        private final TextView hint;
        private boolean showHint;

        Page(Context context) {
            super(context);
            grid = new GridView(context);
            grid.setNumColumns(GridView.AUTO_FIT);
            grid.setColumnWidth(Ui.dp(context, 44));
            grid.setStretchMode(GridView.STRETCH_COLUMN_WIDTH);
            grid.setSelector(new ColorDrawable(Color.TRANSPARENT));
            grid.setVerticalScrollBarEnabled(false);
            grid.setPadding(Ui.dp(context, 4), 0, Ui.dp(context, 4), 0);
            grid.setClipToPadding(false);
            grid.setAdapter(adapter);
            grid.setOnItemClickListener((p, v, pos, id) -> {
                listener.onEmojiKeyPress();
                listener.onEmoji(adapter.getItem(pos));
            });
            grid.setOnItemLongClickListener((p, v, pos, id) -> {
                if (adapter.group != 0) return showTones(v, adapter.getItem(pos));
                listener.onEmojiKeyPress();
                listener.onEmojiPinToggle(adapter.getItem(pos));
                return true;
            });
            addView(grid);
            // 고정 방법 안내는 클립보드처럼 목록 끝에 붙인다. GridView는 꼬리말을 둘 수 없어서,
            // 격자 아래에 안내 높이만큼 여백을 두고 마지막 줄 바로 밑에 안내를 따라 붙인다.
            hint = new TextView(context);
            hint.setText("이모지를 길게 눌러 고정합니다. 다시 길게 눌러 고정을 해제합니다.");
            hint.setTextColor(theme.hint);
            hint.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
            hint.setGravity(Gravity.CENTER);
            hint.setPadding(Ui.dp(context, 16), Ui.dp(context, 12), Ui.dp(context, 16), Ui.dp(context, 16));
            hint.setVisibility(GONE);
            addView(hint, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP));
            grid.setOnScrollListener(new android.widget.AbsListView.OnScrollListener() {
                @Override
                public void onScrollStateChanged(android.widget.AbsListView view, int state) {
                }

                @Override
                public void onScroll(android.widget.AbsListView view, int first, int visible, int total) {
                    positionHint();
                }
            });
            grid.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> positionHint());
            hint.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> positionHint());
            empty = new TextView(context);
            empty.setTextColor(theme.hint);
            empty.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
            empty.setGravity(Gravity.CENTER);
            empty.setText("불러오는 중…");
            addView(empty, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT));
        }

        /** index 탭을 맨 위부터 보여 준다. */
        void show(int index) {
            for (int i = 0; i < grid.getChildCount(); i++) {
                // 다른 탭으로 넘어갈 때 진행 중이던 이동 애니메이션이 남지 않게 한다.
                View child = grid.getChildAt(i);
                child.animate().cancel();
                child.setTranslationX(0f);
                child.setTranslationY(0f);
                child.setAlpha(1f);
            }
            // 다른 탭이므로 스크롤을 맨 위로 되돌린다. GridView는 데이터가 바뀌면 보던 위치를 되살리려 해서
            // setSelection(0)만으로는 덮어쓰일 수 있으므로, 어댑터를 다시 달아 스크롤 상태를 통째로 초기화한다
            // (움직이던 관성 스크롤도 함께 멈춘다).
            adapter.replace(itemsFor(index), index);
            grid.setAdapter(adapter);
            updateNotes();
        }

        /** 비어 있을 때의 안내와 고정 방법 안내를 목록에 맞춘다. */
        void updateNotes() {
            boolean none = adapter.getCount() == 0;
            empty.setVisibility(none ? VISIBLE : GONE);
            empty.setText(data == null ? "불러오는 중…" : "최근 사용한 이모지가 없습니다");
            showHint = adapter.group == 0 && !none;
            hint.setVisibility(showHint ? INVISIBLE : GONE);   // 자리는 positionHint가 정한 뒤 보인다
            positionHint();
        }

        /** 안내를 격자의 마지막 줄 바로 밑에 둔다. 마지막 줄이 화면에 없으면 숨긴다. */
        private void positionHint() {
            int pad = showHint ? hint.getHeight() : 0;
            if (grid.getPaddingBottom() != pad) {
                grid.setPadding(grid.getPaddingLeft(), grid.getPaddingTop(), grid.getPaddingRight(), pad);
            }
            if (!showHint) return;
            int count = adapter.getCount(), children = grid.getChildCount();
            if (children == 0 || grid.getFirstVisiblePosition() + children < count) {
                hint.setVisibility(INVISIBLE);
                return;
            }
            hint.setTranslationY(grid.getChildAt(children - 1).getBottom());
            hint.setVisibility(VISIBLE);
        }
    }

    /** 이모지 칸. 고정한 이모지는 오른쪽 위에 작은 핀을 그린다. */
    @SuppressLint("ViewConstructor")
    private final class EmojiCell extends TextView {
        private final Paint pinPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private boolean pinned;

        EmojiCell(Context context) {
            super(context);
            pinPaint.setColor(theme.accent);
        }

        void setPinned(boolean on) {
            if (pinned == on) return;
            pinned = on;
            invalidate();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            if (!pinned) return;
            float size = Ui.dp(getContext(), 12);
            float inset = Ui.dp(getContext(), 3);
            Icons.draw(canvas, Icons.PIN_FILLED, getWidth() - inset - size / 2f, inset + size / 2f, size, pinPaint);
        }
    }

    /**
     * 격자를 담는 틀. 가로로 미는 손짓을 가로채 지금 탭(page)을 손가락을 따라 옮기고, 그 옆에 미는 방향의
     * 옆 탭(peek)을 붙여 함께 움직인다. 놓으면 옆 탭으로 넘기거나 제자리로 돌린다.
     * 세로로 미는 손짓(격자 스크롤)과 길게 누르기는 그대로 격자가 받는다.
     */
    private final class SwipeFrame extends FrameLayout {
        private final int slop;
        private final float minFling;
        private VelocityTracker velocity;
        private float downX, downY;
        private boolean dragging;
        private boolean animating;
        /** peek이 붙어 있는 쪽: +1이면 오른쪽(다음 탭), -1이면 왼쪽(이전 탭), 0이면 없음. */
        private int peekDir;

        SwipeFrame(Context context) {
            super(context);
            ViewConfiguration vc = ViewConfiguration.get(context);
            slop = vc.getScaledTouchSlop();
            minFling = vc.getScaledMinimumFlingVelocity() * 4f;
        }

        @Override
        public boolean onInterceptTouchEvent(MotionEvent ev) {
            track(ev);
            switch (ev.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    downX = ev.getX();
                    downY = ev.getY();
                    dragging = false;
                    break;
                case MotionEvent.ACTION_MOVE:
                    if (!dragging) startDragIfHorizontal(ev);
                    break;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    if (!dragging) recycleTracker();
                    break;
                default:
                    break;
            }
            return dragging;
        }

        @Override
        public boolean onTouchEvent(MotionEvent ev) {
            track(ev);
            switch (ev.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    // 빈 자리(최근 이모지가 없을 때 등)에서 시작해도 밀 수 있게 받는다.
                    downX = ev.getX();
                    downY = ev.getY();
                    dragging = false;
                    return true;
                case MotionEvent.ACTION_MOVE:
                    if (!dragging) startDragIfHorizontal(ev);
                    if (dragging) drag(ev.getX() - downX);
                    return true;
                case MotionEvent.ACTION_UP:
                    if (dragging) finish(ev.getX() - downX);
                    dragging = false;
                    recycleTracker();
                    return true;
                case MotionEvent.ACTION_CANCEL:
                    if (dragging) settle(0);
                    dragging = false;
                    recycleTracker();
                    return true;
                default:
                    return true;
            }
        }

        private void startDragIfHorizontal(MotionEvent ev) {
            float dx = ev.getX() - downX, dy = ev.getY() - downY;
            if (data == null || animating || Math.abs(dx) < slop || Math.abs(dx) < Math.abs(dy) * 1.5f) return;
            dragging = true;
            downX = ev.getX();   // 문턱만큼 튀지 않게 여기서부터 따라간다
            dismissTones();
            ViewGroup parent = (ViewGroup) getParent();
            if (parent != null) parent.requestDisallowInterceptTouchEvent(true);
        }

        /**
         * 손가락을 따라 옮긴다. 미는 방향에 옆 탭이 있으면 그 내용을 peek에 채워 옆에 붙이고,
         * 첫 탭에서 오른쪽, 마지막 탭에서 왼쪽처럼 넘어갈 곳이 없으면 조금만 따라온다.
         */
        private void drag(float dx) {
            int dir = dx < 0 ? 1 : dx > 0 ? -1 : peekDir;
            int target = group + dir;
            boolean edge = dir != 0 && (target < 0 || target >= EmojiData.GROUPS.length);
            if (edge) {
                hidePeek();
                setOffset(dx * 0.25f);
                return;
            }
            if (dir != 0 && (dir != peekDir || peek == null || peek.adapter.group != target)) {
                if (peek == null) {
                    peek = new Page(getContext());
                    addView(peek, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT));
                }
                peek.show(target);
                peek.setVisibility(VISIBLE);
                peekDir = dir;
            }
            setOffset(dx);
        }

        private void hidePeek() {
            peekDir = 0;
            if (peek != null) {
                peek.animate().cancel();
                peek.setVisibility(GONE);
                peek.setTranslationX(0f);
            }
        }

        private void finish(float dx) {
            float vx = 0;
            if (velocity != null) {
                velocity.computeCurrentVelocity(1000);
                vx = velocity.getXVelocity();
            }
            // +1이면 다음 탭(왼쪽으로 밀었다), -1이면 이전 탭, 0이면 제자리
            final int dir = dx < -getWidth() / 4f || (vx < -minFling && dx < 0) ? 1
                    : dx > getWidth() / 4f || (vx > minFling && dx > 0) ? -1 : 0;
            int next = group + dir;
            if (dir == 0 || dir != peekDir || next < 0 || next >= EmojiData.GROUPS.length) {
                settle(0);
                return;
            }
            // 지금 탭은 밀던 쪽으로 나가고 옆 탭이 제자리에 온다. 다 오면 지금 탭을 새 탭으로 바꾸고 peek을 숨긴다
            // (둘 다 맨 위를 보여 주고 있어 바꾸는 순간이 보이지 않는다).
            animating = true;
            animateOffset(-dir * getWidth(), 200, () -> {
                showGroup(next);
                page.setTranslationX(0f);
                hidePeek();
                animating = false;
            });
        }

        private void settle(float to) {
            animating = true;
            animateOffset(to, 160, () -> {
                hidePeek();
                animating = false;
            });
        }

        /** 지금 탭을 x만큼, peek은 그 바로 옆(한 화면 너비만큼 떨어진 곳)에 둔다. */
        private void setOffset(float x) {
            page.setTranslationX(x);
            if (peek != null && peekDir != 0) peek.setTranslationX(x + peekDir * getWidth());
        }

        private void animateOffset(float to, long ms, Runnable end) {
            page.animate().cancel();
            page.animate().translationX(to).setDuration(ms).setInterpolator(new DecelerateInterpolator())
                    .withEndAction(end).start();
            if (peek != null && peekDir != 0) {
                peek.animate().cancel();
                peek.animate().translationX(to + peekDir * getWidth()).setDuration(ms)
                        .setInterpolator(new DecelerateInterpolator()).start();
            }
        }

        private void track(MotionEvent ev) {
            if (velocity == null) velocity = VelocityTracker.obtain();
            velocity.addMovement(ev);
        }

        private void recycleTracker() {
            if (velocity != null) {
                velocity.recycle();
                velocity = null;
            }
        }

        @Override
        protected void onDetachedFromWindow() {
            super.onDetachedFromWindow();
            recycleTracker();
            page.animate().cancel();
            page.setTranslationX(0f);
            hidePeek();
            animating = false;
            dragging = false;
        }
    }
}
