package com.alternative_studios.newswipe;

import android.app.Activity;
import android.content.Context;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.alternative_studios.newswipe.keyboard.Icons;
import com.alternative_studios.newswipe.ui.NeuUi;
import com.alternative_studios.newswipe.ui.ExpressiveIconButton;
import com.alternative_studios.newswipe.ui.Metro;
import com.alternative_studios.newswipe.ui.Ui;

/**
 * 설정 화면의 틀: 위에 고정되는 머리글(제목·부제와 '키보드 열기' 버튼)과 그 아래 입력 시험 창, 나머지는 스크롤 내용.
 * 설정을 바꾸는 동안 키보드를 계속 띄워 두고 결과를 볼 수 있다. 설정 화면과 편집 화면이 함께 쓴다.
 * 설정 첫 화면은 withSearch()로 '키보드 열기' 대신 동그란 검색 버튼과 검색창을 둔다.
 */
final class SettingsFrame {
    private final Activity activity;
    private final AppTheme colors;
    private final String title, subtitle;
    private LinearLayout testBar;
    private EditText testInput;
    private ExpressiveIconButton testButton, searchButton;
    /** 입력창이 열려 있어야 하는 상태. 애니메이션 중에도 버튼 글자를 바로 바꾸기 위해 따로 기억한다. */
    private boolean testBarWanted;
    /** 설정 검색 (첫 화면 전용): 검색어가 바뀔 때마다 부른다. null이면 검색 없이 '키보드 열기'를 쓴다. */
    private java.util.function.Consumer<String> onSearch;
    private LinearLayout searchBar;
    private EditText searchInput;
    private boolean searchWanted;
    /** 검색 버튼과 검색창에 보이는 이름 (설정 검색, 단어 검색). */
    private String searchName = L.t("설정 검색", "Search settings");
    /** Android 13+의 뒤로 가기 처리 (입력창이 열려 있는 동안만 등록한다). */
    private Object backCallback;
    /**
     * 12 이하에서 Activity.onBackPressed가 이 틀을 찾는 데 쓴다. 틀이 화면(Activity)을 잡고 있어서 값을 그대로 넣으면
     * WeakHashMap의 키(화면)가 영영 풀리지 않으므로 값도 약하게 잡는다 (틀은 화면의 버튼들이 잡고 있어 살아 있다).
     */
    private static final java.util.Map<Activity, java.lang.ref.WeakReference<SettingsFrame>> FRAMES =
            new java.util.WeakHashMap<>();

    SettingsFrame(Activity activity, String title, String subtitle) {
        this.activity = activity;
        this.colors = AppTheme.of(activity);
        this.title = title;
        this.subtitle = subtitle;
        FRAMES.put(activity, new java.lang.ref.WeakReference<>(this));
    }

    /**
     * 뒤로 가기: 검색창이나 키보드 시험 입력창이 열려 있으면 그것부터 닫는다. 닫았으면 true.
     * Android 12 이하에서는 화면(Activity)의 onBackPressed가 이것을 부르고, 13 이상은 입력창이 열려 있는 동안 등록한 콜백이 부른다.
     */
    /** activity의 설정 틀 (없으면 null). */
    private static SettingsFrame frameOf(Activity activity) {
        java.lang.ref.WeakReference<SettingsFrame> ref = FRAMES.get(activity);
        return ref == null ? null : ref.get();
    }

    static boolean consumeBack(Activity activity) {
        SettingsFrame frame = frameOf(activity);
        return frame != null && frame.closeInputIfOpen();
    }

    private boolean closeInputIfOpen() {
        if (appBar != null && appBar.isOpen()) {   // Metro: 펼친 앱 바부터 접는다
            appBar.setOpen(false);
            return true;
        }
        if (searchWanted) {
            toggleSearch();
            return true;
        }
        if (testBarWanted) {
            toggleTestKeyboard();
            return true;
        }
        return false;
    }

    /** 입력창이 열려 있는 동안에만 Android 13+의 뒤로 가기 콜백을 등록하고, 닫히면 풀어서 평소 뒤로 가기가 그대로 되게 한다. */
    private void updateBackCallback() {
        if (android.os.Build.VERSION.SDK_INT < 33) return;
        boolean needed = searchWanted || testBarWanted || (appBar != null && appBar.isOpen());
        android.window.OnBackInvokedDispatcher dispatcher = activity.getOnBackInvokedDispatcher();
        if (needed && backCallback == null) {
            android.window.OnBackInvokedCallback cb = this::closeInputIfOpen;
            backCallback = cb;
            dispatcher.registerOnBackInvokedCallback(android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT, cb);
        } else if (!needed && backCallback != null) {
            dispatcher.unregisterOnBackInvokedCallback((android.window.OnBackInvokedCallback) backCallback);
            backCallback = null;
        }
    }

    /** 머리글 오른쪽 '키보드 열기' 버튼 앞에 놓는 버튼 (없으면 null): 아이콘, 설명, 눌렀을 때. */
    private int actionIcon;
    private String actionDescription;
    private View.OnClickListener actionListener;
    private ExpressiveIconButton actionButton;

    /** Metro 앱 바에서 그 버튼 아래에 보이는 짧은 이름. */
    private String actionHint;

    /**
     * 머리글의 '키보드 열기' 버튼 왼쪽에 버튼 하나를 더 둔다 (옅은 바탕에 강조색 아이콘). Metro는 앱 바의 버튼이 되고,
     * hint가 펼친 앱 바에서 버튼 아래에 보인다.
     */
    SettingsFrame withAction(int icon, String description, String hint, View.OnClickListener l) {
        actionIcon = icon;
        actionDescription = description;
        actionHint = hint;
        actionListener = l;
        return this;
    }

    /** Metro 앱 바의 메뉴 항목 {이름, 동작} (Metro가 아니면 쓰지 않는다). */
    private final java.util.List<Object[]> menuItems = new java.util.ArrayList<>();

    /**
     * Metro UI: 앱 바 메뉴에 항목을 더한다 (점 세 개를 누르면 보이는 소문자 글 줄). 자주 쓰지 않는 동작을 둔다.
     * Metro가 아니면 아무것도 하지 않는다 (그 동작은 부른 쪽이 화면 안에 따로 둔다).
     */
    SettingsFrame withMenuItem(String label, Runnable action) {
        if (Metro.on) menuItems.add(new Object[]{label, action});
        return this;
    }

    /**
     * 앱 바의 키보드 버튼을 먼저 받아 볼 쪽 (처리했으면 true). 머리글을 덮는 설정 창이 열려 있으면 그 창의 입력 시험 창을 연다.
     */
    private java.util.function.BooleanSupplier keyboardOverride;

    void setKeyboardOverride(java.util.function.BooleanSupplier handler) {
        keyboardOverride = handler;
    }

    /** Metro 모드의 앱 바 (Metro가 아니면 null). */
    private Metro.AppBar appBar;
    /** 앱 바를 펼친 동안 화면 위에 깔아 바깥을 누르면 접히게 하는 막. */
    private View appBarScrim;

    /** withAction으로 더한 버튼 (wrap 뒤에만 있다). */
    ExpressiveIconButton actionButton() {
        return actionButton;
    }

    /** 키보드 열기 대신 검색 버튼과 검색창을 둔다. 검색창을 닫으면 빈 검색어로 알린다. */
    /** 키보드가 화면 아래를 그대로 덮게 한다 (화면을 줄이지 않는다). 키보드 사용자화처럼 아래에 미리보기를 둔 화면에서 쓴다. */
    private boolean keyboardOverlays;

    SettingsFrame keyboardOverlays() {
        keyboardOverlays = true;
        activity.getWindow().setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING
                | android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN);
        return this;
    }

    SettingsFrame withSearch(java.util.function.Consumer<String> onQuery) {
        this.onSearch = onQuery;
        return this;
    }

    /** withSearch와 같지만 검색 버튼·검색창에 name을 보인다 (학습한 단어 화면의 '단어 검색'). */
    SettingsFrame withSearch(java.util.function.Consumer<String> onQuery, String name) {
        this.searchName = name;
        return withSearch(onQuery);
    }

    /** 머리글·입력 시험 창과 content(스크롤 뷰)를 한 화면으로 묶어 돌려준다. setContentView에 넘기면 된다. */
    View wrap(View content) {
        return wrap(content, null);
    }

    /** 머리글·입력 창·content를 덮는 틀 (wrap 뒤에만 있다). 설정 창(시트)을 머리글 높이까지 올려 띄울 때 쓴다. */
    private android.widget.FrameLayout overlayHost;

    android.widget.FrameLayout overlayHost() {
        return overlayHost;
    }

    /** 화면 전체(머리글부터 bottom까지)를 덮는 틀 (bottom을 준 wrap 뒤에만 있다, 아니면 null). 설정 창을 화면 왼쪽 절반에 띄울 때 쓴다. */
    private android.widget.FrameLayout fullHost;

    android.widget.FrameLayout fullHost() {
        return fullHost;
    }

    /**
     * wrap(content)와 같되, bottom(없으면 null)을 화면 맨 아래에 따로 둔다 (키보드 미리보기).
     * 머리글·입력 창·content는 overlayHost 틀 안에 들어가, 그 위에 겹쳐 띄우는 창은 머리글까지 덮고 bottom은 가리지 않는다.
     */
    View wrap(View content, View bottom) {
        View root = wrapPlain(content, bottom);
        if (!Metro.on) return root;
        // Metro UI: 화면 전체를 턴스타일 틀로 감싼다. 시스템 전환 효과 대신 들어올 때 머리글·목록이 깃털처럼 돌아 들어온다.
        Metro.noSystemTransition(activity);
        page = new Metro.Page(activity);
        page.setContent(root);
        page.setFeatherItems(() -> {
            java.util.List<View> items = new java.util.ArrayList<>();
            // 아래 미리보기(키보드·한 손 모드)는 맨 앞 차례로 머리글과 함께 바로 들어온다. 목록 끝에 두면 위의 줄들이 다 들어올
            // 때까지(수백 ms) 보이지 않다가 늦게 나타난다 (다른 화면에서 돌아올 때 특히 눈에 띈다).
            if (bottom != null) items.add(bottom);
            // 위에 띄운 설정 창이 열려 있으면 그 항목들도 함께 돈다 (창은 누르기를 막으려 clickable이라 통째로 돌지 않게 속을 모은다).
            collectOverlayFeather(fullHost, items);
            collectOverlayFeather(overlayHost, items);
            if (header != null) collectFeather(header, items);
            if (searchBar != null) collectFeather(searchBar, items);
            if (testBar != null) collectFeather(testBar, items);
            collectFeather(content, items);
            return items;
        });
        page.playEnterOnFirstDraw(!enterAsReturn);
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            // 뒤로 가기도 턴스타일로 나간다. 검색창·입력창·설정 창이 열려 있으면 나중에 등록한 그쪽 콜백이 먼저 불린다.
            android.window.OnBackInvokedCallback cb = () -> metroBack(activity);
            activity.getOnBackInvokedDispatcher().registerOnBackInvokedCallback(
                    android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT, cb);
        }
        return page;
    }

    /** Metro: 이 화면이 처음 그려질 때 돌아온 화면처럼 들어온다 (wrap 전에 정한다). */
    private boolean enterAsReturn;

    /** Metro: 처음 그려질 때 새 화면 대신 돌아온 화면의 턴스타일로 들어온다 (돌아와 새 색으로 다시 만든 화면). wrap 전에 부른다. */
    SettingsFrame enterAsReturn() {
        enterAsReturn = true;
        return this;
    }

    /**
     * 화면을 다시 만들기(recreate) 직전에 부른다. 창 배경을 next의 바탕색으로 바꿔 둔다 (다시 만드는 사이에 창 배경이 드러나도
     * 새 색이다). 화면 내용은 그대로 두어, 옛 화면이 새 색의 화면으로 바로 바뀐다.
     *
     * <p>예외: Metro에서 다른 화면을 열어 돌아 나가 있던(항목이 다 숨고 바탕·앱 바만 남은) 화면이 돌아올 때 다시 만들면, 다시 만들기
     * 전에 한 번 더 그려지는 옛 화면은 옛 색의 바탕·앱 바뿐이다. 그 한 프레임이 옛 색으로 깜빡이지 않게 들어오는 움직임을 멈추고
     * 내용을 숨겨 새 바탕색만 보이게 한다.
     */
    static void prepareRecreate(Activity activity, AppTheme next) {
        android.view.Window w = activity.getWindow();
        w.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(next.bg));
        SettingsFrame frame = frameOf(activity);
        if (frame == null || frame.page == null || !frame.page.away()) return;
        frame.page.freeze();
        Ui.edgeToEdge(w, next.dark);
        View content = w.getDecorView().findViewById(android.R.id.content);
        if (content != null) content.setVisibility(View.INVISIBLE);
    }

    /** {@link #prepareRecreate}를 거쳐 화면을 지금 설정의 색으로 다시 만든다. */
    static void recreateQuietly(Activity activity) {
        prepareRecreate(activity, AppTheme.of(activity));
        activity.recreate();
    }

    /** Metro 모드의 턴스타일 틀 (Metro 모드가 아니면 null). */
    private Metro.Page page;

    private View header;

    /** host 위에 띄운 창(metro_feather_overlay 표시) 가운데 보이는 것의 항목들을 위에서 아래 차례로 모은다. */
    private static void collectOverlayFeather(ViewGroup host, java.util.List<View> out) {
        if (host == null) return;
        for (int i = 0; i < host.getChildCount(); i++) {
            View c = host.getChildAt(i);
            if (c.getVisibility() != View.VISIBLE || c.getTag(R.id.metro_feather_overlay) == null || !(c instanceof ViewGroup)) continue;
            ViewGroup g = (ViewGroup) c;
            for (int k = 0; k < g.getChildCount(); k++) collectFeather(g.getChildAt(k), out);
        }
    }

    /**
     * 깃털처럼 돌아 들어올 항목을 위에서 아래 차례로 모은다: 세로로 쌓는 틀(카드·목록)은 속으로 들어가 줄마다 따로 돌고,
     * 그 밖의 뷰(줄, 버튼, 미리보기)는 통째로 돈다.
     */
    private static void collectFeather(View v, java.util.List<View> out) {
        if (v.getVisibility() != View.VISIBLE) return;
        if (v instanceof com.alternative_studios.newswipe.ui.MetroTiles) {   // 타일 판: 타일마다 놓인 자리 차례로
            ((com.alternative_studios.newswipe.ui.MetroTiles) v).featherOrder(out);
            return;
        }
        boolean container = v instanceof android.widget.ScrollView
                || (v instanceof LinearLayout && ((LinearLayout) v).getOrientation() == LinearLayout.VERTICAL && !v.isClickable())
                || (v.getClass() == android.widget.FrameLayout.class && !v.isClickable());
        if (container && ((ViewGroup) v).getChildCount() > 0) {
            ViewGroup g = (ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) collectFeather(g.getChildAt(i), out);
        } else {
            out.add(v);
        }
    }

    /**
     * Metro 모드의 뒤로 가기: 화면이 턴스타일로 돌아 나간 뒤 닫힌다. 처리했으면 true (Metro 모드가 아니거나 틀이 없으면 false:
     * 평소대로 닫는다). Android 12 이하에서는 화면의 onBackPressed가 super.onBackPressed() 대신 이것을 부른다.
     */
    static boolean metroBack(Activity activity) {
        SettingsFrame frame = frameOf(activity);
        if (frame == null || frame.page == null) return false;
        frame.page.back(() -> Metro.finishQuietly(activity));
        return true;
    }

    /** Metro: 다음 화면 전환에서 v(누른 타일)가 맨 마지막에 돌아 나가게 한다 (Metro가 아니면 아무것도 하지 않는다). */
    static void metroLastOut(Activity activity, View v) {
        SettingsFrame frame = frameOf(activity);
        if (frame != null && frame.page != null) frame.page.setLastOut(v);
    }

    /**
     * Metro 모드에서 이 앱의 다른 화면을 열 때: 지금 화면이 턴스타일로 돌아 나간 뒤 start를 부른다. 처리했으면 true
     * (아니면 부른 쪽이 바로 연다). 다른 앱의 화면(시스템 설정, 브라우저)은 그 앱의 전환 효과를 쓰므로 여기서 다루지 않는다.
     */
    static boolean metroForward(Activity activity, android.content.Intent intent, Runnable start) {
        SettingsFrame frame = frameOf(activity);
        if (frame == null || frame.page == null || intent.getComponent() == null
                || !activity.getPackageName().equals(intent.getComponent().getPackageName())) return false;
        frame.page.forward(start);
        return true;
    }

    /** Metro 머리글의 작은 제목과 큰 제목 (Metro가 아니면 null). 피벗을 놓으면 큰 제목 자리에 섹션 이름 줄이 들어간다. */
    private TextView metroSmall, metroBig;
    private Metro.Pivot pivot;

    /**
     * Metro UI: 이 화면의 섹션들을 Windows Phone 피벗으로 오가게 한다 (wrap 뒤에, 내용을 다 만든 다음 부른다).
     * 머리글 첫 줄에는 메뉴 이름이 작게, 그 아래에는 큰 제목 대신 섹션 이름들이 이어지고, 가로로 밀거나 이름을 누르면 그 섹션으로 간다.
     *
     * @param pages 섹션마다 한 장 (같은 자리에 겹쳐 둔 뷰), names와 차례가 같다
     */
    Metro.Pivot installPivot(String pivotTitle, String[] names, View[] pages, int index, Metro.Pivot.Host host) {
        if (page == null || metroBig == null) return null;
        // Pivot 템플릿: 제목은 PivotTitleStyle(SemiBold 22.667px), Margin 24,17,0,-7. 머리글 이름은 PivotHeaderItem Padding 21
        // (제목보다 3px 왼쪽), 내용은 PivotItemMargin 12,28,12,0만큼 아래에서 시작한다.
        // 피벗 제목은 메뉴의 원래 이름 (피벗이 없는 화면의 큰 제목은 짧은 이름, 작은 제목은 앱 이름).
        metroSmall.setText(Metro.upper(pivotTitle != null ? pivotTitle : title));
        metroSmall.setTextSize(TypedValue.COMPLEX_UNIT_SP, Metro.sp(Metro.FONT_MEDIUM));
        metroSmall.setTypeface(Metro.semibold());
        int[] tl = Metro.segoeLine(metroSmall);   // 글자 크기가 바뀌었으니 줄을 다시 맞춘다
        LinearLayout.LayoutParams slp = (LinearLayout.LayoutParams) metroSmall.getLayoutParams();
        slp.topMargin = tl[0];
        slp.bottomMargin = tl[1];
        Metro.PivotHeader names2 = new Metro.PivotHeader(activity, names, index);   // 줄은 스스로 Segoe WP의 줄
        ViewGroup parent = (ViewGroup) metroBig.getParent();
        int at = parent.indexOfChild(metroBig);
        LinearLayout.LayoutParams lp = matchWrap();
        lp.leftMargin = -Metro.px(activity, 3);   // 이름은 제목보다 3px 왼쪽 (PivotHeaderItem Padding 21, 제목 24)
        lp.topMargin = -Metro.px(activity, 7);    // 제목 Margin 아래 -7
        lp.bottomMargin = -names2.overhang();      // g·y 끝을 그리려고 늘린 만큼 (줄의 자리는 그대로)
        parent.removeViewAt(at);
        parent.addView(names2, at, lp);
        metroBig = null;
        pivot = new Metro.Pivot(page, names2, pages, index, host);
        return pivot;
    }

    private View wrapPlain(View content, View bottom) {
        Ui.edgeToEdge(activity.getWindow(), colors.dark);
        // 창 배경을 화면 바탕색과 같게: 테마의 windowBackground(settings_background)는 시스템의 다크/라이트를 따르고 Metro·
        // Neumorphism 바탕과 색이 달라서, 화면 내용이 보이지 않는 순간(Metro 턴스타일이 다 돌아 나가 화면을 숨긴 뒤 닫히거나 다음
        // 화면이 뜨기 전, 돌아와 다시 그리기 전)에 그 색이 깜빡였다.
        activity.getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(colors.bg));
        LinearLayout root = new LinearLayout(activity);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(colors.bg);
        LinearLayout upper = new LinearLayout(activity);
        upper.setOrientation(LinearLayout.VERTICAL);
        header = Metro.on ? buildMetroHeader() : buildHeader();
        upper.addView(header, matchWrap());
        // Metro: 검색·키보드 버튼은 앱 바에 있으므로, 그 버튼을 쓰는 검색창·입력창보다 먼저 만든다 (붙이는 자리는 아래).
        if (Metro.on) appBar = buildAppBar(activity);
        if (onSearch != null) {
            buildSearchBar();
            upper.addView(searchBar, matchWrap());
        } else {
            buildTestBar();
            upper.addView(testBar, matchWrap());
        }
        upper.addView(content, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        overlayHost = new android.widget.FrameLayout(activity);
        overlayHost.addView(upper, new android.widget.FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        root.addView(overlayHost, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        // Windows Phone 앱 바: 화면 내용 아래, 미리보기(키보드처럼 화면 맨 아래에 붙는 것)가 있으면 그 위.
        if (appBar != null) root.addView(appBar, matchWrap());
        if (bottom == null) {
            Ui.padForSystemBars(root, !keyboardOverlays);
            return root;
        }
        root.addView(bottom, matchWrap());
        // 화면 전체를 덮는 틀로 한 번 더 감싼다: bottom(미리보기)까지 내려오는 창을 여기에 겹쳐 띄울 수 있다.
        fullHost = new android.widget.FrameLayout(activity);
        fullHost.setBackgroundColor(colors.bg);
        fullHost.addView(root, new android.widget.FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        Ui.padForSystemBars(fullHost, !keyboardOverlays);
        return fullHost;
    }

    private static LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private View buildHeader() {
        Context c = activity;
        LinearLayout header = new LinearLayout(c);
        header.setGravity(Gravity.CENTER_VERTICAL);
        int pad = Ui.dp(c, 16);
        header.setPadding(pad + Ui.dp(c, 4), Ui.dp(c, 12), pad, Ui.dp(c, 8));
        LinearLayout titles = new LinearLayout(c);
        titles.setOrientation(LinearLayout.VERTICAL);
        TextView titleView = new TextView(c);
        titleView.setText(title);
        titleView.setTextColor(colors.text);
        titleView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 24);
        titleView.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        titles.addView(titleView);
        TextView sub = new TextView(c);
        sub.setText(subtitle);
        sub.setTextColor(colors.hint);
        sub.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        titles.addView(sub);
        header.addView(titles, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        int size = Ui.dp(c, 44);
        if (onSearch != null) {
            // 강조색 동그라미 안에 검색 아이콘
            testButton = null;
            searchButton = circleButton(c, Icons.SEARCH, searchName, v -> toggleSearch());
            header.addView(searchButton, new LinearLayout.LayoutParams(size, size));
            return header;
        }
        if (actionListener != null) {
            actionButton = circleButton(c, actionIcon, actionDescription, actionListener);
            actionButton.setActive(true);   // 옅은 바탕: 키보드 열기와 구분되는 보조 버튼
            LinearLayout.LayoutParams alp = new LinearLayout.LayoutParams(size, size);
            alp.rightMargin = Ui.dp(c, 8);
            header.addView(actionButton, alp);
        }
        // 강조색 동그라미 안에 키보드 아이콘. 입력 시험 창이 열려 있으면 색을 뒤집어 알린다.
        testButton = circleButton(c, Icons.MENU_KEYBOARD, L.t("키보드 열기", "Open keyboard"), v -> toggleTestKeyboard());
        header.addView(testButton, new LinearLayout.LayoutParams(size, size));
        return header;
    }

    /**
     * Metro UI의 머리글: Windows Phone 8 SDK의 페이지 템플릿(TitlePanel). 첫 줄에 작은 대문자 앱 이름(ApplicationTitle,
     * PhoneTextNormalStyle 20px), 그 아래에 화면 이름을 크고 가는 소문자(PageTitle, PhoneTextTitle1Style SemiLight 72px, 위로 7px 당김)로
     * 쓰고 화면보다 길면 끝에서 잘린다. 아래로 28px 띄워 내용이 시작한다. 섹션 피벗을 놓으면({@link #installPivot}) Pivot 템플릿이 된다.
     * 버튼은 화면 아래의 앱 바에 있다.
     */
    private View buildMetroHeader() {
        Context c = activity;
        LinearLayout header = new LinearLayout(c);
        header.setOrientation(LinearLayout.VERTICAL);
        int pad = Metro.pagePad(c, 16);   // 목록과 같은 왼쪽 선 (WP: TitlePanel 12px + 글 12px = 24px)
        header.setPadding(pad, Metro.px(c, 17), pad, Metro.px(c, 28));
        header.setClipChildren(true);
        // 큰 제목·피벗 이름은 Segoe WP의 줄(기준선 아래 0.25 em)에 맞추느라 아래 바깥 여백이 음수일 수 있어, g·y의 끝이 머리글의
        // 아래 안쪽 여백(28px)으로 내려간다. 안쪽 여백에서 자르면 그 끝이 잘리므로 자르지 않는다 (글은 여전히 제 칸 안에서만 그린다).
        header.setClipToPadding(false);

        boolean main = title.equals(activity.getString(R.string.app_name));
        TextView small = new TextView(c);
        small.setText(Metro.upper(main ? title : subtitle));
        small.setTextColor(colors.text);
        small.setTextSize(TypedValue.COMPLEX_UNIT_SP, Metro.sp(Metro.FONT_NORMAL));
        small.setTypeface(Metro.regular());
        small.setSingleLine(true);
        small.setEllipsize(android.text.TextUtils.TruncateAt.END);
        // 줄은 Silverlight TextBlock의 줄 (Segoe WP: 높이 1.33 em, 기준선 1.0791 em): 이 기기 글꼴과 한글 대체 글꼴에 흔들리지 않는다.
        int[] sl = Metro.segoeLine(small);
        LinearLayout.LayoutParams slp = matchWrap();
        slp.topMargin = sl[0];
        slp.bottomMargin = sl[1];
        header.addView(small, slp);
        metroSmall = small;

        TextView big = new TextView(c);
        big.setText(Metro.lower(main ? L.t("설정", "settings") : title));
        big.setTextColor(colors.text);
        big.setTextSize(TypedValue.COMPLEX_UNIT_SP, Metro.sp(Metro.FONT_EXTRA_EXTRA_LARGE));
        big.setTypeface(Metro.light());
        big.setSingleLine(true);
        big.setHorizontallyScrolling(true);   // 화면보다 길면 줄이지 않고 화면 끝에서 잘린다
        int[] bl = Metro.segoeLine(big);
        LinearLayout.LayoutParams blp = matchWrap();
        blp.leftMargin = -Metro.px(c, 3);   // PageTitle Margin 9 (ApplicationTitle은 12)
        blp.topMargin = -Metro.px(c, 7) + bl[0];
        blp.bottomMargin = bl[1];
        header.addView(big, blp);
        metroBig = big;
        return header;
    }

    /**
     * Metro UI의 앱 바: 검색, 또는 (더한 버튼과) 키보드 열기 버튼과 메뉴 항목. 펼친 동안은 머리글·목록 위에 막을 깔아
     * 바깥을 누르면 접힌다 (뒤로 가기도 접는다).
     */
    private Metro.AppBar buildAppBar(Context c) {
        Metro.AppBar bar = new Metro.AppBar(c);
        if (onSearch != null) {
            testButton = null;
            searchButton = circleButton(c, Icons.SEARCH, searchName, v -> {
                bar.setOpen(false);
                toggleSearch();
            });
            bar.addButton(searchButton, L.t("검색", "search"));
        } else {
            if (actionListener != null) {
                actionButton = circleButton(c, actionIcon, actionDescription, v -> {
                    bar.setOpen(false);
                    actionListener.onClick(v);
                });
                bar.addButton(actionButton, actionHint != null ? actionHint : actionDescription);
            }
            testButton = circleButton(c, Icons.MENU_KEYBOARD, L.t("키보드 열기", "Open keyboard"), v -> {
                bar.setOpen(false);
                if (keyboardOverride != null && keyboardOverride.getAsBoolean()) return;
                toggleTestKeyboard();
            });
            bar.addButton(testButton, L.t("키보드", "keyboard"));
        }
        for (Object[] item : menuItems) bar.addMenuItem((String) item[0], (Runnable) item[1]);
        bar.setOnOpenChanged(() -> {
            if (bar.isOpen()) {
                appBarScrim = new View(c);
                appBarScrim.setOnClickListener(v -> bar.setOpen(false));
                overlayHost.addView(appBarScrim, new android.widget.FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT));
            } else if (appBarScrim != null) {
                overlayHost.removeView(appBarScrim);
                appBarScrim = null;
            }
            updateBackCallback();
        });
        return bar;
    }

    /** 강조색 동그라미 안에 아이콘 하나를 둔 버튼 (누르면 모양이 바뀌고 진동이 울린다). */
    private ExpressiveIconButton circleButton(Context c, int icon, String description, View.OnClickListener l) {
        ExpressiveIconButton b = new ExpressiveIconButton(c, icon, colors.accent, colors.onAccent, description);
        b.setOnClickListener(l);
        return b;
    }

    /** 머리글 아래에 고정되는 검색창. */
    private void buildSearchBar() {
        Context c = activity;
        searchBar = new LinearLayout(c);
        searchBar.setVisibility(View.GONE);
        int pad = Metro.pagePad(c, 16);
        searchBar.setPadding(pad, 0, pad, Ui.dp(c, 8));
        searchInput = new EditText(c);
        searchInput.setHint(searchName);
        searchInput.setTextColor(colors.text);
        searchInput.setHintTextColor(colors.hint);
        searchInput.setSingleLine(true);
        searchInput.setInputType(InputType.TYPE_CLASS_TEXT);
        searchInput.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH);
        searchInput.setBackground(Ui.round(colors.card, Ui.dp(c, 12)));
        if (NeuUi.on) NeuUi.sunken(searchInput, Ui.dp(c, 12));   // Neumorphism: 안으로 들어간 입력란
        searchInput.setPadding(Ui.dp(c, 14), Ui.dp(c, 10), Ui.dp(c, 14), Ui.dp(c, 10));
        if (Metro.on) Metro.textBox(searchInput);
        searchInput.addTextChangedListener(new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int n) { }
            @Override public void onTextChanged(CharSequence s, int a, int b, int n) { }
            @Override public void afterTextChanged(android.text.Editable e) {
                if (searchWanted) onSearch.accept(e.toString());
            }
        });
        searchInput.setOnEditorActionListener((v, action, ev) -> {
            hideKeyboard();   // 결과를 훑어볼 수 있게 키보드만 내린다
            return true;
        });
        searchBar.addView(searchInput, matchWrap());
    }

    private void hideKeyboard() {
        InputMethodManager imm = (InputMethodManager) activity.getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) imm.hideSoftInputFromWindow(searchInput.getWindowToken(), 0);
    }

    private void toggleSearch() {
        searchWanted = !searchWanted;
        searchButton.setActive(searchWanted);   // 열려 있으면 옅은 바탕에 강조색 아이콘
        if (!searchWanted) {
            hideKeyboard();
            searchInput.setText("");
            searchInput.clearFocus();
            Ui.setVisibleAnimated(searchBar, false);
            onSearch.accept("");
            updateBackCallback();
            return;
        }
        Ui.setVisibleAnimated(searchBar, true, this::focusSearch);
        searchBar.post(this::focusSearch);
        updateBackCallback();
    }

    private void focusSearch() {
        if (!searchWanted || activity.isFinishing() || activity.isDestroyed()) return;
        if (!searchInput.hasFocus()) searchInput.requestFocus();
        if (!searchInput.hasFocus()) return;
        InputMethodManager imm = (InputMethodManager) activity.getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) imm.showSoftInput(searchInput, InputMethodManager.SHOW_IMPLICIT);
    }

    /** 머리글 아래에 고정되는 입력창. */
    private void buildTestBar() {
        Context c = activity;
        testBar = new LinearLayout(c);
        testBar.setVisibility(View.GONE);
        int pad = Metro.pagePad(c, 16);
        testBar.setPadding(pad, 0, pad, Ui.dp(c, 8));
        testInput = new EditText(c);
        testInput.setHint(L.t("여기에 입력해 보세요", "Type here"));
        testInput.setTextColor(colors.text);
        testInput.setHintTextColor(colors.hint);
        testInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        testInput.setMaxLines(3);
        testInput.setBackground(Ui.round(colors.card, Ui.dp(c, 12)));
        if (NeuUi.on) NeuUi.sunken(testInput, Ui.dp(c, 12));   // Neumorphism: 안으로 들어간 입력란
        testInput.setPadding(Ui.dp(c, 14), Ui.dp(c, 10), Ui.dp(c, 14), Ui.dp(c, 10));
        if (Metro.on) Metro.textBox(testInput);
        testBar.addView(testInput, matchWrap());
        updateTestButton();
    }

    private void updateTestButton() {
        // 열려 있으면 옅은 바탕에 강조색 아이콘, 닫혀 있으면 강조색 바탕에 흰 아이콘.
        if (testButton == null) return;
        testButton.setActive(testBarWanted);
        testButton.setContentDescription(testBarWanted ? L.t("키보드 닫기", "Close keyboard") : L.t("키보드 열기", "Open keyboard"));
    }

    private void toggleTestKeyboard() {
        testBarWanted = !testBarWanted;
        InputMethodManager imm = (InputMethodManager) activity.getSystemService(Context.INPUT_METHOD_SERVICE);
        if (!testBarWanted) {
            if (imm != null) imm.hideSoftInputFromWindow(testInput.getWindowToken(), 0);
            Ui.setVisibleAnimated(testBar, false);
            testInput.clearFocus();
        } else {
            // 입력창이 아직 펼쳐지는 중에는 포커스와 키보드 연결이 제대로 되지 않을 수 있어서,
            // 화면 배치 직후와 펼침이 끝난 뒤에 다시 한 번 확인한다.
            Ui.setVisibleAnimated(testBar, true, this::focusTestInput);
            testBar.post(this::focusTestInput);
        }
        updateTestButton();
        updateBackCallback();
    }

    /** 입력 시험 창에 포커스를 주고 키보드를 연결한다. 이미 포커스가 있으면 키보드만 띄운다. */
    private void focusTestInput() {
        if (!testBarWanted || activity.isFinishing() || activity.isDestroyed()) return;
        testInput.setCursorVisible(true);
        if (!testInput.hasFocus()) testInput.requestFocus();
        if (!testInput.hasFocus()) return;
        InputMethodManager imm = (InputMethodManager) activity.getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) imm.showSoftInput(testInput, InputMethodManager.SHOW_IMPLICIT);
    }
}
