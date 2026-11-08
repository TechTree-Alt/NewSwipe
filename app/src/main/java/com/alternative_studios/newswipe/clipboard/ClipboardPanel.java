package com.alternative_studios.newswipe.clipboard;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Typeface;
import android.graphics.Outline;
import android.graphics.drawable.Drawable;
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
import com.alternative_studios.newswipe.ui.Neu;
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

        /** 헤더 칩을 눌렀다 (도구 막대 버튼처럼 진동·소리를 낸다). */
        void onClipChipPress();

        void onClipPin(ClipboardHistory.Item item, boolean pinned);

        void onClipDelete(ClipboardHistory.Item item);

        void onClipClear();
    }

    private final KeyboardTheme theme;
    private final Listener listener;
    /** 일반 항목 목록(분리하지 않으면 고정 항목도 여기에)과, '고정 항목 분리'를 켰을 때의 고정 항목 목록. */
    private final LinearLayout list, pinList;
    /** 분리하지 않을 때 쓰는 세로 스크롤과, 분리했을 때 두 페이지(일반·고정)를 가로로 넘기는 스크롤. */
    private final ScrollView scroll, pinScroll;
    private final PageSwipe pager;
    private final LinearLayout tabs;
    private final IconButton tabNormal, tabPinned;
    private final TextView title;
    private final FrameLayout headerWrap;
    private final int barHeight;
    private boolean splitPinned, splitShown;
    private final IconButton clear;
    /** 맨 위 줄(뒤로·제목·모두 지우기)과, 모두 지우기를 누르면 그 자리에 대신 보이는 확인 줄. */
    private final LinearLayout header, confirmRow;
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

        header = new LinearLayout(context);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(Ui.dp(context, 4), 0, Ui.dp(context, 8), 0);
        IconButton back = new IconButton(context, Icons.BACK, theme.text, theme.keyPressed, "키보드로 돌아가기");
        back.setToolbarStyle(theme, topBarHeight);   // 도구 막대 버튼과 같은 칩
        back.setOnClickListener(v -> {
            listener.onClipChipPress();
            listener.onClipBack();
        });
        header.addView(back, new LayoutParams(Ui.dp(context, 44), topBarHeight));
        this.barHeight = topBarHeight;
        title = new TextView(context);
        title.setText("클립보드");
        title.setTextColor(theme.text);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        LayoutParams titleLp = new LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        titleLp.leftMargin = Ui.dp(context, 10);   // 뒤로 가기 칩에 붙지 않게
        header.addView(title, titleLp);
        // 모두 지우기: 휴지통 아이콘 칩 (뒤로 가기 칩과 같은 크기)
        clear = new IconButton(context, Icons.TRASH, theme.onAccent, theme.keyPressed, "모두 지우기");
        clear.setToolbarStyle(theme, topBarHeight, theme.accent, theme.onAccent);   // 이전 글자와 같은 색 (강조색 위 글자색)
        clear.setOnClickListener(v -> {
            listener.onClipChipPress();
            confirmClear();
        });
        header.addView(clear, new LayoutParams(Ui.dp(context, 44), topBarHeight));
        // 고정 항목 분리를 쓰면 두 페이지 칩이 헤더 정중앙에 놓인다 (위에 겹쳐 둔다).
        headerWrap = new FrameLayout(context);
        headerWrap.addView(header, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        addView(headerWrap, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, topBarHeight));

        // 모두 지우기 확인: 따로 창(대화상자)을 띄우지 않고 맨 위 줄을 확인 줄로 바꾼다.
        // 키보드 창에 붙인 대화상자는 뜨는 순간 포커스가 옮겨 가면서 시스템이 키보드를 닫아 버리는 일이 있었다.
        confirmRow = new LinearLayout(context);
        confirmRow.setGravity(Gravity.CENTER_VERTICAL);
        confirmRow.setPadding(Ui.dp(context, 14), 0, Ui.dp(context, 8), 0);
        TextView ask = new TextView(context);
        ask.setText("고정하지 않은 기록을 모두 지울까요?");
        ask.setTextColor(theme.text);
        ask.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        ask.setMaxLines(2);
        ask.setEllipsize(TextUtils.TruncateAt.END);
        confirmRow.addView(ask, new LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView cancel = Ui.chipButton(context, "취소", theme.text, theme.functionKey, topBarHeight);
        Neu.apply(cancel, theme, theme.functionKey, Ui.chipRadius(context));
        cancel.setOnClickListener(v -> {
            listener.onClipChipPress();
            dismissDialog();
        });
        confirmRow.addView(cancel);
        TextView ok = Ui.chipButton(context, "지우기", theme.onAccent, theme.accent, topBarHeight);
        Neu.apply(ok, theme, theme.accent, Ui.chipRadius(context));
        ok.setOnClickListener(v -> {
            listener.onClipChipPress();
            dismissDialog();
            listener.onClipClear();
        });
        confirmRow.addView(ok);
        confirmRow.setVisibility(GONE);
        addView(confirmRow, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, topBarHeight));

        scroll = new ScrollView(context);
        scroll.setVerticalScrollBarEnabled(false);
        list = newList(context);
        scroll.addView(list, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        // 고정 항목 분리: 왼쪽 페이지는 일반 항목(list), 오른쪽 페이지는 고정 항목(pinList). 위의 두 글자로 어느 쪽인지 보이고 눌러 넘길 수 있다.
        tabs = new LinearLayout(context);
        tabs.setGravity(Gravity.CENTER);
        tabPinned = tab(context, Icons.PIN, "고정 항목", 0, topBarHeight);
        tabNormal = tab(context, Icons.CLIPBOARD, "기록", 1, topBarHeight);
        tabs.addView(tabPinned, new LayoutParams(Ui.dp(context, 44), topBarHeight));
        tabs.addView(tabNormal, new LayoutParams(Ui.dp(context, 44), topBarHeight));
        tabs.setVisibility(GONE);
        headerWrap.addView(tabs, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.MATCH_PARENT, Gravity.CENTER));

        // 이모지 탭 전환과 같은 방식: 미는 동안 옆 페이지가 붙어서 따라오고, 놓으면 넘기거나 제자리로 돌아간다.
        pager = new PageSwipe(context);
        ScrollView normalScroll = new ScrollView(context);
        normalScroll.setVerticalScrollBarEnabled(false);
        pinScroll = new ScrollView(context);
        pinScroll.setVerticalScrollBarEnabled(false);
        pinList = newList(context);
        pinListRef = pinList;
        pinScroll.addView(pinList, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        pager.setPages(pinScroll, normalScroll);
        pager.setVisibility(GONE);
        // 일반 항목 목록은 분리했을 때 왼쪽 페이지에 들어간다 (list를 두 곳에 두지 않고 부모를 옮긴다).
        normalPage = normalScroll;
        addView(scroll, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        addView(pager, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
    }

    private final ScrollView normalPage;
    private View pinListRef;

    private LinearLayout newList(Context context) {
        LinearLayout l = new LinearLayout(context);
        l.setOrientation(VERTICAL);
        int p = Ui.dp(context, 8);
        l.setPadding(p, 0, p, p);
        return l;
    }

    /** 페이지 버튼: 도구 막대 버튼과 같은 모양의 아이콘 칩. 지금 페이지는 강조색 칩이다. */
    private IconButton tab(Context context, int icon, String description, int page, int barHeight) {
        IconButton t = new IconButton(context, icon, theme.text, theme.keyPressed, description);
        t.setToolbarStyle(theme, barHeight);
        t.setOnClickListener(v -> {
            listener.onClipChipPress();
            pager.goTo(page);
        });
        return t;
    }

    /** 지금 보이는 페이지의 버튼만 강조색 칩으로 보인다. */
    private void updateTabs() {
        boolean normal = pager.page() == 1;
        styleTab(tabNormal, normal);
        styleTab(tabPinned, !normal);
    }

    private void styleTab(IconButton t, boolean on) {
        if (on) t.setToolbarStyle(theme, barHeight, theme.accent, theme.onAccent);
        else t.setToolbarStyle(theme, barHeight);
    }

    /**
     * 두 페이지(일반·고정)를 좌우로 넘기는 틀. 이모지 탭 전환(EmojiPanel.SwipeFrame)과 같은 방식이다:
     * 가로로 미는 손짓을 가로채 지금 페이지를 손가락을 따라 옮기고 그 옆에 다른 페이지를 붙여 함께 움직이며,
     * 놓을 때 폭의 1/4 넘게 밀었거나 빠르게 튕겼으면 넘기고 아니면 제자리로 돌린다.
     * 세로로 미는 손짓(목록 스크롤)과 항목 누르기는 그대로 목록이 받는다.
     */
    private final class PageSwipe extends FrameLayout {
        private final int slop;
        private final float minFling;
        private android.view.VelocityTracker velocity;
        private View[] pages;
        private int cur;
        private float downX, downY;
        private boolean dragging, animating;

        PageSwipe(Context context) {
            super(context);
            android.view.ViewConfiguration vc = android.view.ViewConfiguration.get(context);
            slop = vc.getScaledTouchSlop();
            minFling = vc.getScaledMinimumFlingVelocity() * 4f;
        }

        void setPages(View first, View second) {
            pages = new View[]{first, second};
            for (View v : pages) {
                addView(v, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT));
            }
            reset();
        }

        int page() {
            return cur;
        }

        /** 첫 페이지로 돌아가 진행 중인 움직임을 멈춘다. */
        void reset() {
            reset(1);
        }

        void reset(int first) {
            for (View v : pages) {
                v.animate().cancel();
                v.setTranslationX(0f);
            }
            animating = false;
            dragging = false;
            cur = first;
            pages[first].setVisibility(VISIBLE);
            pages[1 - first].setVisibility(GONE);
        }

        /** 버튼으로 그 페이지로 넘긴다. */
        void goTo(int target) {
            if (target == cur || animating || getWidth() == 0) return;
            View to = pages[target];
            int dir = target > cur ? 1 : -1;
            to.setVisibility(VISIBLE);
            to.setTranslationX(dir * getWidth());
            animating = true;
            slide(-dir * getWidth(), 200, target);
        }

        @Override
        public boolean onInterceptTouchEvent(android.view.MotionEvent ev) {
            track(ev);
            switch (ev.getActionMasked()) {
                case android.view.MotionEvent.ACTION_DOWN:
                    downX = ev.getX();
                    downY = ev.getY();
                    dragging = false;
                    break;
                case android.view.MotionEvent.ACTION_MOVE:
                    if (!dragging) startDragIfHorizontal(ev);
                    break;
                case android.view.MotionEvent.ACTION_UP:
                case android.view.MotionEvent.ACTION_CANCEL:
                    if (!dragging) recycleTracker();
                    break;
                default:
                    break;
            }
            return dragging;
        }

        @Override
        public boolean onTouchEvent(android.view.MotionEvent ev) {
            track(ev);
            switch (ev.getActionMasked()) {
                case android.view.MotionEvent.ACTION_DOWN:
                    downX = ev.getX();
                    downY = ev.getY();
                    dragging = false;
                    return true;
                case android.view.MotionEvent.ACTION_MOVE:
                    if (!dragging) startDragIfHorizontal(ev);
                    if (dragging) drag(ev.getX() - downX);
                    return true;
                case android.view.MotionEvent.ACTION_UP:
                    if (dragging) finish(ev.getX() - downX);
                    dragging = false;
                    recycleTracker();
                    return true;
                case android.view.MotionEvent.ACTION_CANCEL:
                    if (dragging) slide(0, 160, cur);
                    dragging = false;
                    recycleTracker();
                    return true;
                default:
                    return true;
            }
        }

        private void startDragIfHorizontal(android.view.MotionEvent ev) {
            float dx = ev.getX() - downX, dy = ev.getY() - downY;
            if (animating || Math.abs(dx) < slop || Math.abs(dx) < Math.abs(dy) * 1.5f) return;
            dragging = true;
            downX = ev.getX();   // 문턱만큼 튀지 않게 여기서부터 따라간다
            ViewGroup parent = (ViewGroup) getParent();
            if (parent != null) parent.requestDisallowInterceptTouchEvent(true);
        }

        /** 손가락을 따라 옮긴다. 넘어갈 곳이 없는 쪽(첫 페이지에서 오른쪽, 둘째에서 왼쪽)으로는 조금만 따라온다. */
        private void drag(float dx) {
            int other = 1 - cur;
            boolean toOther = cur == 0 ? dx < 0 : dx > 0;
            if (!toOther) {
                pages[other].setVisibility(GONE);
                pages[cur].setTranslationX(dx * 0.25f);
                return;
            }
            int dir = cur == 0 ? 1 : -1;
            pages[other].setVisibility(VISIBLE);
            pages[cur].setTranslationX(dx);
            pages[other].setTranslationX(dx + dir * getWidth());
        }

        private void finish(float dx) {
            float vx = 0;
            if (velocity != null) {
                velocity.computeCurrentVelocity(1000);
                vx = velocity.getXVelocity();
            }
            // +1이면 다음(왼쪽으로 밀었다), -1이면 이전, 0이면 제자리
            int dir = dx < -getWidth() / 4f || (vx < -minFling && dx < 0) ? 1
                    : dx > getWidth() / 4f || (vx > minFling && dx > 0) ? -1 : 0;
            int next = cur + dir;
            if (dir == 0 || next < 0 || next > 1) {
                slide(0, 160, cur);
                return;
            }
            animating = true;
            slide(-dir * getWidth(), 200, next);
        }

        /** 지금 페이지를 x로 옮기고(옆 페이지는 그 옆에 붙여) 끝나면 target 페이지만 보이게 한다. */
        private void slide(float to, long ms, int target) {
            animating = true;
            int other = 1 - cur;
            int dir = cur == 0 ? 1 : -1;
            View now = pages[cur], side = pages[other];
            now.animate().cancel();
            side.animate().cancel();
            if (target != cur || side.getVisibility() == VISIBLE) {
                side.animate().translationX(to + dir * getWidth()).setDuration(ms)
                        .setInterpolator(new DecelerateInterpolator()).start();
            }
            now.animate().translationX(to).setDuration(ms).setInterpolator(new DecelerateInterpolator())
                    .withEndAction(() -> {
                        cur = target;
                        for (int i = 0; i < 2; i++) {
                            pages[i].setTranslationX(0f);
                            pages[i].setVisibility(i == cur ? VISIBLE : GONE);
                        }
                        animating = false;
                        updateTabs();
                    }).start();
        }

        private void track(android.view.MotionEvent ev) {
            if (velocity == null) velocity = android.view.VelocityTracker.obtain();
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
            if (pages != null) reset();
        }
    }

    /** 고정 항목을 따로 한 페이지에 모을지 ('고정 항목 분리'). 다음 show()부터 적용된다. */
    public void setSplitPinned(boolean on) {
        splitPinned = on;
    }

    /**
     * 키보드의 여백 설정을 따른다. 맨 위 줄(뒤로 가기·제목·모두 지우기, 지우기 확인 줄)은 도구 막대처럼 좌우 여백
     * (barLeft·barRight)만큼, 항목 카드는 자판 키의 가장자리(keyLeft·keyRight)에 맞춘다.
     * 카드 목록 위는 자판 위 여백(top)만큼, 아래는 자판 아래 여백(bottom)만큼 띄운다.
     */
    public void setInsets(int barLeft, int barRight, int keyLeft, int top, int keyRight, int bottom) {
        header.setPadding(barLeft, 0, barRight, 0);
        // 확인 줄의 글은 뒤로 가기 칩 자리만큼 들여 쓴다.
        confirmRow.setPadding(barLeft + Ui.dp(getContext(), 10), 0, barRight, 0);
        list.setPadding(keyLeft, top, keyRight, bottom);
        pinList.setPadding(keyLeft, top, keyRight, bottom);
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
        boolean split = splitPinned && enabled && !items.isEmpty();
        setSplit(split);
        list.removeAllViews();
        pinList.removeAllViews();
        Set<String> shown = new HashSet<>();
        for (ClipboardHistory.Item item : items) if (item.isImage()) shown.add(item.text);
        thumbs.keySet().retainAll(shown);
        clear.setVisibility(enabled && !items.isEmpty() ? VISIBLE : GONE);
        if (!enabled || items.isEmpty()) dismissDialog();   // 지울 것이 없어졌으면 확인 줄도 닫는다
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
        List<ClipboardHistory.Item> pinned = new java.util.ArrayList<>(), normal = new java.util.ArrayList<>();
        for (ClipboardHistory.Item it : items) (it.pinned ? pinned : normal).add(it);
        if (split) {
            addGrid(list, normal);
            if (normal.isEmpty()) list.addView(note("고정하지 않은 항목이 없습니다."));
            addGrid(pinList, pinned);
            if (pinned.isEmpty()) pinList.addView(note("고정한 항목이 여기에 모입니다.\n항목의 핀 버튼으로 고정할 수 있습니다."));
        } else {
            // 고정 항목을 위에 모으고, 분리선으로 일반 항목과 구분한다.
            addGrid(list, pinned);
            if (!pinned.isEmpty() && !normal.isEmpty()) list.addView(divider());
            addGrid(list, normal);
        }
        if (!before.isEmpty()) animateMoves(before);
        String footer = "클립보드 항목은 최대 " + ClipboardHistory.MAX_ITEMS + "개(이미지는 " + ClipboardHistory.MAX_IMAGES
                + "개)이며, 24시간 후 삭제됩니다. 핀 버튼으로 최대 " + ClipboardHistory.MAX_PINNED + "개까지 고정할 수 있습니다.";
        list.addView(note(footer));
        if (split) pinList.addView(note(footer));
    }

    /** 분리 여부에 따라 보이는 스크롤을 바꾸고, 일반 항목 목록을 그 안으로 옮긴다. */
    private void setSplit(boolean split) {
        if (split == splitShown && (list.getParent() != null)) return;
        splitShown = split;
        if (list.getParent() instanceof ViewGroup) ((ViewGroup) list.getParent()).removeView(list);
        ViewGroup host = split ? normalPage : scroll;
        host.addView(list, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        scroll.setVisibility(split ? GONE : VISIBLE);
        pager.setVisibility(split ? VISIBLE : GONE);
        tabs.setVisibility(split ? VISIBLE : GONE);
        title.setVisibility(split ? INVISIBLE : VISIBLE);   // 두 칩이 헤더 가운데에 오므로 '클립보드' 글자는 감춘다
        if (split) {
            pager.reset();
            updateTabs();
        }
    }

    /** 항목들을 한 줄에 columns() 칸씩 놓는다. 한 줄의 항목은 높이를 맞춘다. */
    private void addGrid(LinearLayout target, List<ClipboardHistory.Item> items) {
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
            target.addView(row, lp);
        }
    }

    /** 고정 항목과 일반 항목 사이의 분리선. */
    private View divider() {
        Context c = getContext();
        View v = new View(c);
        v.setBackgroundColor((theme.hint & 0x00FFFFFF) | 0x80000000);
        LayoutParams lp = new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Math.max(1, Ui.dp(c, 1)));
        lp.topMargin = Ui.dp(c, GAP_DP + 4);
        lp.bottomMargin = Ui.dp(c, 4);
        v.setLayoutParams(lp);
        return v;
    }

    /** 한 줄의 칸 수: 화면 폭에 맞춰 칸 폭이 CELL_DP쯤 되도록 한다 (보통 휴대폰 세로 화면은 2칸, 가로 모드·대화면은 더 많이). */
    private int columns() {
        int widthDp = getResources().getConfiguration().screenWidthDp;
        return Math.max(2, Math.min(8, widthDp / CELL_DP));
    }

    /** 기록 항목 카드들 (각 줄 안에 있다. 안내 문구와 빈자리는 빼고). */
    private List<View> cards() {
        List<View> out = new java.util.ArrayList<>();
        for (LinearLayout l : new LinearLayout[]{list, pinList}) {
            for (int i = 0; i < l.getChildCount(); i++) {
                View child = l.getChildAt(i);
                if (!(child instanceof LinearLayout) || child.getTag() != null) continue;
                ViewGroup row = (ViewGroup) child;
                for (int k = 0; k < row.getChildCount(); k++) {
                    if (row.getChildAt(k).getTag() instanceof String) out.add(row.getChildAt(k));
                }
            }
        }
        return out;
    }

    /** 목록 안에서 카드의 자리 (줄의 자리 + 줄 안의 자리). */
    private int[] position(View card) {
        View row = (View) card.getParent();
        int page = row.getParent() == pinListRef ? 1 : 0;   // 페이지가 바뀐 항목은 미끄러지지 않고 서서히 나타난다
        return new int[]{row.getLeft() + card.getLeft(), row.getTop() + card.getTop(), page};
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
                    if (old == null || old[2] != now[2]) {
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
            IconButton pin = new IconButton(c, item.pinned ? Icons.PIN_FILLED : Icons.PIN, theme.accent, theme.keyPressed,
                    item.pinned ? "고정 해제" : "고정");
            pin.setOnClickListener(v -> {
                animateNext = true;
                listener.onClipPin(item, !item.pinned);
            });
            bar.addView(pin, new LayoutParams(Ui.dp(c, 36), Ui.dp(c, 36)));
            // 아이콘만 있는 버튼: 핀은 강조 색(고정 여부는 채워졌는지로 구분), 삭제는 붉은색
            IconButton trash = new IconButton(c, Icons.TRASH, Ui.danger(theme.dark), theme.keyPressed, "지우기");
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

    /** 모두 지우기 확인 줄을 닫고 원래 맨 위 줄로 돌아간다 (키보드가 숨겨지거나 패널이 사라질 때도 부른다). */
    public void dismissDialog() {
        if (confirmRow.getVisibility() != VISIBLE) return;
        confirmRow.setVisibility(GONE);
        headerWrap.setVisibility(VISIBLE);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        dismissDialog();
    }

    /** 지우기 전에 맨 위 줄을 확인 줄로 바꾼다. */
    private void confirmClear() {
        headerWrap.setVisibility(GONE);
        confirmRow.setVisibility(VISIBLE);
    }
}
