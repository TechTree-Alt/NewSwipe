package com.alternative_studios.newswipe;

import android.content.ClipData;
import android.content.ClipDescription;
import android.content.ClipboardManager;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.inputmethodservice.InputMethodService;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.PersistableBundle;
import android.os.SystemClock;
import android.text.InputType;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.view.inputmethod.InputContentInfo;
import android.view.inputmethod.InputMethodInfo;
import android.view.inputmethod.InputMethodManager;
import android.view.inputmethod.InputMethodSubtype;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.alternative_studios.newswipe.clipboard.ClipImageProvider;
import com.alternative_studios.newswipe.clipboard.ClipImages;
import com.alternative_studios.newswipe.clipboard.ClipboardHistory;
import com.alternative_studios.newswipe.clipboard.ClipboardPanel;
import com.alternative_studios.newswipe.emoji.EmojiData;
import com.alternative_studios.newswipe.emoji.EmojiPanel;
import com.alternative_studios.newswipe.emoji.EmojiRepository;
import com.alternative_studios.newswipe.hangul.HangulComposer;
import com.alternative_studios.newswipe.keyboard.FunctionSwipes;
import com.alternative_studios.newswipe.keyboard.ToolbarButtons;
import com.alternative_studios.newswipe.keyboard.ToolbarSwipes;
import com.alternative_studios.newswipe.keyboard.KeyboardSwipes;
import com.alternative_studios.newswipe.keyboard.Icons;
import com.alternative_studios.newswipe.keyboard.Key;
import com.alternative_studios.newswipe.keyboard.KeyboardLayout;
import com.alternative_studios.newswipe.keyboard.KeyboardTheme;
import com.alternative_studios.newswipe.keyboard.KeyboardView;
import com.alternative_studios.newswipe.keyboard.SwipeAction;
import com.alternative_studios.newswipe.ui.IconButton;
import com.alternative_studios.newswipe.ui.SwipeVertical;
import com.alternative_studios.newswipe.ui.SwipeStrip;
import com.alternative_studios.newswipe.suggest.TextMirror;
import com.alternative_studios.newswipe.suggest.UserDictionary;
import com.alternative_studios.newswipe.suggest.WordSuggester;
import com.alternative_studios.newswipe.ui.Ui;

import java.io.File;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** NewSwipe 키보드 서비스. */
public final class NewSwipeIME extends InputMethodService implements KeyboardView.Listener,
        EmojiPanel.Listener, ClipboardPanel.Listener, ClipboardManager.OnPrimaryClipChangedListener {

    private static final int PANEL_KEYBOARD = 0;
    private static final int PANEL_EMOJI = 1;
    private static final int PANEL_CLIPBOARD = 2;
    private static final int PANEL_SEARCH = 3;
    private static final String SENSITIVE_EXTRA = "android.content.extra.IS_SENSITIVE";

    private Prefs prefs;
    private Feedback feedback;
    private final HangulComposer composer = new HangulComposer();
    private ClipboardManager clipboard;
    private ClipboardHistory clipHistory;
    private EmojiRepository emojiRepo;
    /** 파일 읽기·쓰기 같은 뒷일. 키 입력·화면 그리기와 CPU를 다투지 않도록 낮은 우선순위로 돌린다. */
    private final ExecutorService io = Executors.newSingleThreadExecutor(r -> new Thread(() -> {
        android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_BACKGROUND);
        r.run();
    }, "NewSwipe-io"));
    /**
     * 이모지 검색. 글자를 칠 때마다 결과가 바로 나와야 하므로, 이미지 복사 등으로 길어질 수 있는 io 줄에 세우지 않고
     * 따로 돌린다 (보통 우선순위). 쉬는 동안에는 스레드를 남기지 않는다.
     */
    private final java.util.concurrent.ThreadPoolExecutor searchIo = new java.util.concurrent.ThreadPoolExecutor(
            0, 1, 30, java.util.concurrent.TimeUnit.SECONDS, new java.util.concurrent.LinkedBlockingQueue<>(),
            r -> new Thread(r, "NewSwipe-search"));
    private final Handler main = new Handler(Looper.getMainLooper());

    // 뷰
    private KeyboardTheme theme;
    private LinearLayout root;
    private FrameLayout topSlot;
    private SwipeStrip toolbar;
    private IconButton clipboardButton, emojiButton, settingsButton, hideButton;
    private IconButton voiceButton, undoButton, oneHandButton;
    /**
     * 한 손 모드 배치: body(가로) = [column(도구 막대·검색 결과·자판) | sideStrip(반대편 빈 곳)], 그 아래 liftSpace.
     * 한 손 모드가 꺼져 있으면 column만 꽉 차게 들어가고 sideStrip·liftSpace는 숨긴다.
     */
    private LinearLayout body, column, sideStrip;
    private View liftSpace;
    private IconButton sideSwap, sideExpand;
    /** 마지막으로 적용한 한 손 모드 배치 (상태·폭·띄우는 높이). 바뀌었을 때만 뷰를 다시 놓는다. */
    private String appliedOneHand;
    /** 도구 막대 밀기 배치. 완전 사용자화를 켜면 편집한 배치, 끄면 기본 밀기 설정으로 만든 배치 (설정이 바뀔 때 만든다). */
    private ToolbarSwipes toolbarSwipes = ToolbarSwipes.standard(true, true, true, true);
    /** 입력란이 알려 준 마지막 선택 영역 (모르면 -1). 단어 단위 커서 이동이 입력란에 다시 묻지 않도록 기억한다. */
    private int selStart = -1, selEnd = -1;
    private LinearLayout searchBar;
    private LinearLayout suggestBar;
    private View toolSpacer;
    /** 도구 막대 버튼 배치 {왼쪽, 오른쪽} (ToolbarButtons ID). 바뀌었을 때만 버튼을 다시 놓는다. */
    private String[][] toolOrder;
    private final TextView[] suggestViews = new TextView[WordSuggester.MAX];
    private TextView searchText;
    private HorizontalScrollView resultScroll;
    private LinearLayout results;
    /** 이모지 검색 결과 칸. 글자를 칠 때마다 새로 만들지 않고 다시 쓴다 (입력 뷰를 새로 만들 때 비운다). */
    private final java.util.ArrayList<TextView> resultViews = new java.util.ArrayList<>();
    private TextView resultNote;
    private FrameLayout content;
    private KeyboardView keyboard;
    private EmojiPanel emojiPanel;
    private ClipboardPanel clipPanel;
    private int toolbarHeight, keyboardHeight;

    // 상태
    private boolean korean = true;
    private int layoutKind = KeyboardLayout.KOREAN;
    private int panel = PANEL_KEYBOARD;
    private int shiftState;
    private long lastShiftTime;
    private boolean hadComposing;
    private boolean lastWasSpace;
    private long lastSpaceTime;
    private final StringBuilder search = new StringBuilder();
    private String searchComposing = "";
    private WordSuggester suggester;
    private UserDictionary userWords;
    private String[] suggestions = new String[0];
    private String correctedFrom, correctedTo;   // 방금 자동 수정한 단어 (지우기 키로 되돌리기 위해)
    private final java.util.HashSet<String> correctionIgnored = new java.util.HashSet<>();
    /** correctionIgnored를 채울 때의 학습한 단어 편집 번호. 설정에서 단어를 지우면 바뀌어 비운다. */
    private int ignoredEditVersion;
    private String suggestWord = "";
    private boolean suggestAllowed;   // 현재 입력란에서 추천을 쓸 수 있는지 (비밀번호·이메일 등은 제외, 인터넷 주소 입력란은 허용)
    /** 설정값 (applySettings에서 읽어 둔다. 키마다 설정 파일을 읽지 않기 위해). */
    private boolean cfgSuggest, cfgAutoCorrect, cfgLearn;
    /** 사전이 필요한지 (단어 추천이나 자동 수정 중 하나라도 켜져 있으면). */
    private boolean cfgWords;
    /** 커서 앞 글자를 따라 적어 둔 것. 추천을 찾을 때 앱에 묻는 횟수를 줄인다. */
    private final TextMirror mirror = new TextMirror();
    /** 사전을 읽는 작업을 이미 맡겼는지 (언어별). 같은 작업이 줄줄이 쌓이지 않게 한다. */
    private boolean warmingKo, warmingEn;
    /** 키보드 창이 내려가 있는지. 내려가 있는 동안에는 추천을 찾지도, 사전을 읽지도 않는다 (놓아 준 사전이 다시 올라오지 않게). */
    private boolean windowHidden = true;

    // ---------------------------------------------------------------- 생명주기

    @Override
    public void onCreate() {
        setTheme(R.style.Theme_NewSwipe_Ime);   // super.onCreate()보다 먼저 지정해야 키보드 창에 적용된다.
        super.onCreate();
        prefs = new Prefs(this);
        feedback = new Feedback(this);
        korean = prefs.korean();
        emojiRepo = new EmojiRepository(this, io);
        userWords = UserDictionary.shared(getFilesDir());
        suggester = new WordSuggester(this, userWords);
        clipHistory = new ClipboardHistory(new File(getFilesDir(), "clipboard.txt"));
        clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        if (clipboard != null) clipboard.addPrimaryClipChangedListener(this);
        pruneClipboard();
        prefs.raw().registerOnSharedPreferenceChangeListener(prefListener);
    }

    private final Runnable applyPrefsRunnable = () -> {
        if (keyboard == null) return;
        if (KeyboardTheme.of(this) != theme) {
            // 화면 모드나 강조 색이 바뀌었으면 입력 뷰를 새 색으로 다시 만든다.
            setInputView(onCreateInputView());
            setLayoutKind(layoutKind);
            EditorInfo ei = getCurrentInputEditorInfo();
            if (ei != null) keyboard.setEnterIcon(enterIcon(ei));
            applyNavigationBarStyle();
            return;
        }
        applySettings();
        setLayoutKind(layoutKind);   // 숫자 줄·Shift 키·쉼표 키·길게 누르기 문자는 자판을 다시 만들어야 한다.
    };

    /**
     * 설정 화면에서 값을 바꾸면 떠 있는 키보드에 바로 반영한다.
     * 슬라이더를 끄는 동안 값이 연달아 바뀌므로 짧게 모아서 한 번만 적용한다.
     */
    private final android.content.SharedPreferences.OnSharedPreferenceChangeListener prefListener =
            (sp, key) -> {
                // 최근·고정 이모지가 바뀌면(이모지 입력, 고정, 설정 가져오기) 이모지 패널에 반영한다.
                if (Prefs.RECENT_EMOJI.equals(key) && emojiPanel != null) {
                    emojiPanel.setRecent(EmojiRepository.parseRecent(prefs.recentEmoji()));
                }
                if (Prefs.PINNED_EMOJI.equals(key) && emojiPanel != null) {
                    emojiPanel.setPinned(EmojiRepository.parseRecent(prefs.pinnedEmoji()));
                }
                // key가 null이면 설정 전체가 지워진 경우(설정 가져오기 등)라 반영한다.
                if (keyboard == null || (key != null && !affectsKeyboard(key))) return;
                main.removeCallbacks(applyPrefsRunnable);
                main.postDelayed(applyPrefsRunnable, 50);
            };

    /** 사용할 때마다 설정에서 직접 읽는 값이나 키보드 화면과 상관없는 값은 다시 그릴 필요가 없다. */
    private static boolean affectsKeyboard(String key) {
        return !(Prefs.LANGUAGE.equals(key) || Prefs.RECENT_EMOJI.equals(key) || Prefs.PINNED_EMOJI.equals(key)
                || Prefs.CLIPBOARD_HISTORY.equals(key) || Prefs.CLIPBOARD_IMAGES.equals(key) || Prefs.AUTO_CAP.equals(key)
                || Prefs.DOUBLE_SPACE_PERIOD.equals(key) || Prefs.CONFIRM_LEARNED_DELETE.equals(key)
                || Prefs.ONE_HAND_LAST.equals(key));
    }

    @Override
    public void onDestroy() {
        if (clipboard != null) clipboard.removePrimaryClipChangedListener(this);
        prefs.raw().unregisterOnSharedPreferenceChangeListener(prefListener);
        // 백그라운드 작업이 보내 둔 화면 갱신도 모두 버린다 (끝난 서비스를 붙잡거나, 닫힌 작업 줄에 다시 일을 맡기지 않게).
        main.removeCallbacksAndMessages(null);
        dismissForgetDialog();
        // 이미 맡긴 단어 기록이 끝난 뒤에 저장하고 내리도록 같은 작업 줄에 넣는다 (shutdown은 남은 작업을 마저 한다).
        runIo(userWords::release);
        io.shutdown();
        searchSeq++;   // 지금 돌고 있는 검색이 끝나서 보내는 결과도 버린다
        searchIo.shutdownNow();   // 남은 검색은 보여 줄 곳이 없다
        super.onDestroy();
    }

    @Override
    public void onTrimMemory(int level) {
        super.onTrimMemory(level);
        if (level >= TRIM_MEMORY_BACKGROUND && panel != PANEL_EMOJI && panel != PANEL_SEARCH) {
            searchWaiting = false;
            releaseEmoji();
            if (!isInputViewShown()) {
                suggester.release();
                warmingKo = warmingEn = false;
                runIo(userWords::release);
                if (panel != PANEL_CLIPBOARD) runIo(clipHistory::release);
            }
        }
    }

    /** 키보드를 닫고 이만큼 지나면 이모지·클립보드 패널을 놓아 준다 (곧 다시 열면 그대로 쓴다). */
    private static final long RELEASE_DELAY_MS = 60_000;

    private final Runnable releaseWhileHidden = () -> {
        if (isInputViewShown()) return;
        // 숨긴 채로 이모지·클립보드·검색 화면에 머물러 있었다면 자판으로 돌려놓고 놓아 준다.
        if (panel != PANEL_KEYBOARD) showPanel(PANEL_KEYBOARD);
        searchWaiting = false;
        releaseEmoji();
        releaseClipboard();
        pruneClipboard();
        suggester.release();
        warmingKo = warmingEn = false;
        runIo(userWords::release);
        correctionIgnored.clear();
    };

    /** 24시간이 지난 클립보드 항목과 그 이미지 파일을 지우고 기록을 메모리에서 내린다. */
    private void pruneClipboard() {
        runIo(() -> {
            clipHistory.prune(System.currentTimeMillis());
            clipHistory.release();
        });
    }

    /** 백그라운드 작업을 맡긴다. 서비스가 끝나는 중이면 조용히 버린다. */
    private void runIo(Runnable r) {
        if (io.isShutdown()) return;
        try {
            io.execute(r);
        } catch (java.util.concurrent.RejectedExecutionException ignored) {
            // 서비스가 끝나는 중
        }
    }

    /** 이모지 패널과 데이터를 놓아 준다. 화면 트리에서도 떼어 내야 메모리가 실제로 풀린다. */
    private void releaseEmoji() {
        if (emojiPanel != null) {
            emojiPanel.dismissTones();
            if (content != null) content.removeView(emojiPanel);
            emojiPanel = null;
        }
        // 검색 결과 칸도 놓아 준다 (검색 화면이 아닐 때만 불린다).
        if (results != null) results.removeAllViews();
        resultViews.clear();
        resultNote = null;
        emojiRepo.release();
    }

    private void releaseClipboard() {
        if (clipPanel != null) {
            clipPanel.dismissDialog();
            if (content != null) content.removeView(clipPanel);
            clipPanel = null;
        }
    }

    @Override
    public boolean onEvaluateFullscreenMode() {
        return false;   // 가로 화면에서도 전체 화면 입력창을 쓰지 않는다.
    }

    @Override
    public void onComputeInsets(Insets outInsets) {
        super.onComputeInsets(outInsets);
        // 툴바까지 키보드 영역으로 취급해 앱 화면이 툴바 위에서 끝나게 한다.
        if (root != null && root.getHeight() > 0 && !isFullscreenMode()) {
            int top = root.getTop() + (root.getParent() instanceof View ? ((View) root.getParent()).getTop() : 0);
            outInsets.contentTopInsets = top;
            outInsets.visibleTopInsets = top;
        }
    }

    @Override
    public View onCreateInputView() {
        dismissForgetDialog();   // 예전 화면에 붙은 창이 남지 않게
        theme = KeyboardTheme.of(this);
        emojiPanel = null;
        clipPanel = null;
        messageView = null;
        panel = PANEL_KEYBOARD;
        toolbarHeight = toolbarHeightPx();

        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(theme.background);
        root.setClipChildren(false);
        root.setClipToPadding(false);
        // 키보드 창이 내비게이션 바(비보 등의 키보드 하단 바 포함) 뒤까지 그려질 수 있으므로
        // 시스템이 알려 주는 하단 인셋만큼 키보드를 위로 띄운다.
        root.setOnApplyWindowInsetsListener((v, insets) -> {
            int bottom;
            if (Build.VERSION.SDK_INT >= 30) {
                bottom = Math.max(insets.getInsets(WindowInsets.Type.navigationBars()).bottom,
                        insets.getInsets(WindowInsets.Type.tappableElement()).bottom);
            } else {
                bottom = insets.getSystemWindowInsetBottom();
            }
            if (v.getPaddingBottom() != bottom) v.setPadding(0, 0, 0, bottom);
            return insets;
        });
        root.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
            @Override public void onViewAttachedToWindow(View v) { v.requestApplyInsets(); }
            @Override public void onViewDetachedFromWindow(View v) { }
        });

        // 한 손 모드에서는 column(도구 막대와 자판)이 한쪽으로 좁아지고, 반대편에 sideStrip이 붙는다.
        body = new LinearLayout(this);
        body.setOrientation(LinearLayout.HORIZONTAL);
        body.setClipChildren(false);
        body.setClipToPadding(false);
        column = new LinearLayout(this);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setClipChildren(false);
        column.setClipToPadding(false);
        sideStrip = buildSideStrip();
        liftSpace = new View(this);
        appliedOneHand = null;
        body.addView(column, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(body, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        liftSpace.setVisibility(View.GONE);
        root.addView(liftSpace, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0));

        topSlot = new FrameLayout(this);
        toolbar = buildToolbar();
        toolOrder = null;   // 새 막대는 기본 배치라, 다음 applySettings에서 설정의 배치로 다시 놓는다
        topSlot.addView(toolbar);
        searchBar = buildSearchBar();
        searchBar.setVisibility(View.GONE);
        topSlot.addView(searchBar);
        column.addView(topSlot, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, toolbarHeight));

        resultViews.clear();   // 예전 색으로 만든 칸은 버린다
        resultNote = null;
        resultScroll = new HorizontalScrollView(this);
        resultScroll.setHorizontalScrollBarEnabled(false);
        results = new LinearLayout(this);
        results.setGravity(Gravity.CENTER_VERTICAL);
        results.setPadding(Ui.dp(this, 6), 0, Ui.dp(this, 6), 0);
        resultScroll.addView(results);
        resultScroll.setVisibility(View.GONE);
        column.addView(resultScroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                Ui.dp(this, 50)));

        content = new FrameLayout(this);
        content.setClipChildren(false);
        keyboard = new KeyboardView(this);
        keyboard.setListener(this);
        keyboard.setTheme(theme);
        content.addView(keyboard, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        column.addView(content, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        applySettings();
        // 새 자판 뷰에도 바로 배열을 놓는다 (회전·테마 변경으로 다시 만들 때 onStartInputView 전에 배열이 비어 있지 않게).
        setLayoutKind(layoutKind);
        applyNavigationBarStyle();
        return root;
    }

    /**
     * 하단 바(내비게이션 바) 색과 아이콘 밝기를 키보드 테마에 맞춘다.
     * 창 테마(Theme.NewSwipe.Ime)는 시스템 화면 모드만 따르므로, 설정에서 시스템과 다른 화면 모드를 골랐을 때를 위해
     * 여기서 다시 지정한다. targetSdk 36 이상(기본 빌드)에서는 키보드 창이 하단 바 뒤까지 그려지는 것을 끌 수 없고
     * (windowOptOutEdgeToEdgeEnforcement 무시), 키보드가 하단 바 영역을 직접 배경색으로 채운다 (입력 뷰의 root 배경과
     * 아래쪽 인셋 패딩). 일부 기기(비보 등)의 하단 바 아이콘 색은 기기에서 확인해야 한다 (BUILDING.md '배포 전 기기에서 확인할 것').
     */
    private void applyNavigationBarStyle() {
        if (theme == null || getWindow() == null || getWindow().getWindow() == null) return;
        android.view.Window w = getWindow().getWindow();
        w.setNavigationBarColor(theme.background);
        View d = w.getDecorView();
        if (Build.VERSION.SDK_INT >= 30) {
            android.view.WindowInsetsController c = d.getWindowInsetsController();
            if (c != null) {
                c.setSystemBarsAppearance(theme.dark ? 0 : android.view.WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS,
                        android.view.WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS);
            }
        } else {
            // Android 9~10(minSdk 28)에는 WindowInsetsController가 없다.
            int f = d.getSystemUiVisibility();
            f = theme.dark ? f & ~View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR : f | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
            d.setSystemUiVisibility(f);
        }
    }

    @Override
    public void onWindowShown() {
        super.onWindowShown();
        windowHidden = false;
        main.removeCallbacks(releaseWhileHidden);
        applyNavigationBarStyle();
    }

    @Override
    public void onWindowHidden() {
        super.onWindowHidden();
        windowHidden = true;
        main.removeCallbacks(suggestRunnable);
        main.removeCallbacks(releaseWhileHidden);
        main.postDelayed(releaseWhileHidden, RELEASE_DELAY_MS);
    }

    private SwipeStrip buildToolbar() {
        SwipeStrip bar = new SwipeStrip(this);
        bar.setListener(this::onToolbarSwipe);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        // 좌우 여백은 applySettings에서 자판의 좌우 여백과 같게 맞춘다.
        clipboardButton = toolButton(Icons.CLIPBOARD, "클립보드", v -> showPanel(PANEL_CLIPBOARD));
        bar.addView(clipboardButton);
        emojiButton = toolButton(Icons.EMOJI, "이모지", v -> showPanel(PANEL_EMOJI));
        bar.addView(emojiButton);
        voiceButton = toolButton(Icons.MIC, "음성 입력", v -> startVoiceInput());
        bar.addView(voiceButton);
        undoButton = toolButton(Icons.UNDO, "실행 취소", v -> undo());
        bar.addView(undoButton);
        toolSpacer = new View(this);
        bar.addView(toolSpacer, new LinearLayout.LayoutParams(0, 1, 1f));
        // 추천 단어는 왼쪽 버튼들 오른쪽에 뜬다. 추천이 있으면 빈 공간 대신 이 영역을 쓰고 오른쪽 버튼들은 접는다.
        suggestBar = buildSuggestBar();
        suggestBar.setVisibility(View.GONE);
        bar.addView(suggestBar, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));
        oneHandButton = toolButton(Icons.ONE_HAND, "한 손 모드", v -> toggleOneHand());
        bar.addView(oneHandButton);
        settingsButton = toolButton(Icons.SETTINGS, "설정", v -> openSettings());
        bar.addView(settingsButton);
        hideButton = toolButton(Icons.HIDE, "키보드 숨기기", v -> requestHideSelf(0));
        bar.addView(hideButton);
        // 버튼을 위·아래로 밀면 정해 둔 기능을 실행한다 (기본: 아래로 밀기만 클립보드 = 붙여넣기, 이모지 = 최근 이모지,
        // 실행 취소 = 다시 실행).
        attachToolSwipe(clipboardButton, ToolbarSwipes.CLIPBOARD);
        attachToolSwipe(emojiButton, ToolbarSwipes.EMOJI);
        attachToolSwipe(voiceButton, ToolbarSwipes.VOICE);
        attachToolSwipe(undoButton, ToolbarSwipes.UNDO);
        attachToolSwipe(settingsButton, ToolbarSwipes.SETTINGS);
        attachToolSwipe(hideButton, ToolbarSwipes.HIDE);
        // 한 손 모드 버튼: 왼쪽·오른쪽으로 밀면 그쪽 한 손 모드, 위·아래는 다른 버튼처럼 정해 둔 기능.
        SwipeVertical.attachFourWay(oneHandButton,
                dir -> dir == Key.SWIPE_LEFT || dir == Key.SWIPE_RIGHT
                        || !SwipeAction.NONE.equals(toolButtonAction(ToolbarSwipes.ONE_HAND, dir)),
                () -> Ui.dp(this, prefs.swipeThresholdDp()), dir -> {
                    feedback.onKey(null);
                    if (dir == Key.SWIPE_LEFT) setOneHand(Prefs.ONE_HAND_LEFT);
                    else if (dir == Key.SWIPE_RIGHT) setOneHand(Prefs.ONE_HAND_RIGHT);
                    else onKeyFunction(null, toolButtonAction(ToolbarSwipes.ONE_HAND, dir));
                });
        bar.setHorizontalOwner(v -> v == oneHandButton);
        // 기능이 있는 방향은 버튼이 처리하고, 없는 방향은 도구 막대를 그 방향으로 민 것으로 본다.
        bar.setVerticalOwner((v, dir) -> !SwipeAction.NONE.equals(toolButtonAction(toolSlotOf(v), dir)));
        return bar;
    }

    private View toolButtonOf(String id) {
        switch (id) {
            case ToolbarButtons.CLIPBOARD: return clipboardButton;
            case ToolbarButtons.EMOJI: return emojiButton;
            case ToolbarButtons.VOICE: return voiceButton;
            case ToolbarButtons.UNDO: return undoButton;
            case ToolbarButtons.SETTINGS: return settingsButton;
            case ToolbarButtons.ONE_HAND: return oneHandButton;
            default: return hideButton;
        }
    }

    /**
     * 설정의 배치대로 버튼을 다시 놓는다: 왼쪽 버튼들, 빈 공간과 추천 단어 자리, 오른쪽 버튼들.
     * 버튼 객체는 그대로 두고 자리만 옮기므로 밀기 기능도 그대로 따라간다.
     */
    private void arrangeToolbar() {
        if (toolbar == null) return;
        String[][] order = prefs.toolOrder();
        if (toolOrder != null && java.util.Arrays.deepEquals(order, toolOrder)) return;
        toolOrder = order;
        for (String id : ToolbarButtons.ALL) toolbar.removeView(toolButtonOf(id));
        toolbar.removeView(toolSpacer);
        toolbar.removeView(suggestBar);
        for (String id : order[0]) toolbar.addView(toolButtonOf(id));
        toolbar.addView(toolSpacer, new LinearLayout.LayoutParams(0, 1, 1f));
        toolbar.addView(suggestBar, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));
        for (String id : order[1]) toolbar.addView(toolButtonOf(id));
        refreshToolbarVisibility();
    }

    private void attachToolSwipe(View button, int slot) {
        SwipeVertical.attach(button, dir -> !SwipeAction.NONE.equals(toolButtonAction(slot, dir)),
                () -> Ui.dp(this, prefs.fnSwipeThresholdDp()), dir -> {
                    feedback.onKey(null);
                    onKeyFunction(null, toolButtonAction(slot, dir));
                });
    }

    /** 도구 막대 버튼에 해당하는 칸 (ToolbarSwipes). 버튼이 아니면 -1. */
    private int toolSlotOf(View v) {
        if (v == clipboardButton) return ToolbarSwipes.CLIPBOARD;
        if (v == emojiButton) return ToolbarSwipes.EMOJI;
        if (v == voiceButton) return ToolbarSwipes.VOICE;
        if (v == undoButton) return ToolbarSwipes.UNDO;
        if (v == settingsButton) return ToolbarSwipes.SETTINGS;
        if (v == hideButton) return ToolbarSwipes.HIDE;
        if (v == oneHandButton) return ToolbarSwipes.ONE_HAND;
        return -1;
    }

    /** 도구 막대 버튼을 위·아래로 밀 때 실행할 기능. */
    private String toolButtonAction(int slot, int dir) {
        if (slot < 0 || (dir != Key.SWIPE_UP && dir != Key.SWIPE_DOWN)) return SwipeAction.NONE;
        return toolbarSwipes.action(slot, dir);
    }

    /** 도구 막대 자체를 이 방향으로 밀 때 실행할 기능. */
    private String toolbarAction(int dir) {
        return toolbarSwipes.action(ToolbarSwipes.BAR, dir);
    }

    private IconButton toolButton(int icon, String desc, View.OnClickListener l) {
        IconButton b = new IconButton(this, icon, theme.hint, theme.keyPressed, desc);
        b.setOnClickListener(v -> {
            feedback.onKey(null);
            l.onClick(v);
        });
        // 아이콘보다 조금 넓게만 잡아, 버튼이 많아도 추천이 쓸 공간이 넉넉하게 남게 한다. 폭은 sizeToolbar()가 설정의 버튼 크기에 맞춘다.
        b.setLayoutParams(new LinearLayout.LayoutParams(Ui.dp(this, 40), toolbarHeight));
        return b;
    }

    /** 설정의 '도구 막대 높이'(%)를 px로. 100%가 40dp. */
    private int toolbarHeightPx() {
        return Math.round(Ui.dp(this, 40) * prefs.toolbarHeight() / 100f);
    }

    /**
     * 도구 막대 높이와 도구 버튼 크기를 설정값으로 맞춘다. 둘은 따로 정한다: 막대 높이는 줄의 높이(터치 영역)만,
     * 버튼 크기는 버튼의 폭과 아이콘 크기를 바꾼다 (기본 폭 40dp, 아이콘 22dp). 아이콘은 막대보다 커지지 않는다 (IconButton이 70%로 제한).
     */
    private void sizeToolbar() {
        toolbarHeight = toolbarHeightPx();
        setHeight(topSlot, toolbarHeight);
        float scale = prefs.toolButtonSize() / 100f;
        int width = Math.round(Ui.dp(this, 40) * scale);
        float icon = Ui.dp(this, 22) * scale;
        for (String id : ToolbarButtons.ALL) {
            View v = toolButtonOf(id);
            ViewGroup.LayoutParams lp = v.getLayoutParams();
            if (lp != null && (lp.width != width || lp.height != toolbarHeight)) {
                lp.width = width;
                lp.height = toolbarHeight;
                v.setLayoutParams(lp);
            }
            ((IconButton) v).setIconSize(icon);
        }
    }

    /**
     * 도구 막대(추천 단어·검색창 자리)를 자판 위 또는 아래에 놓는다. 검색 중에는 검색창과 결과가 자판 위에 있어야 하므로
     * 설정과 관계없이 위에 둔다.
     */
    private void placeTopSlot() {
        if (column == null || topSlot == null) return;
        boolean bottom = prefs.toolbarBottom() && panel != PANEL_SEARCH;
        int at = column.indexOfChild(topSlot);
        if (bottom ? at == column.getChildCount() - 1 : at == 0) return;
        ViewGroup.LayoutParams lp = topSlot.getLayoutParams();
        column.removeView(topSlot);
        if (bottom) column.addView(topSlot, lp);
        else column.addView(topSlot, 0, lp);
    }

    private LinearLayout buildSuggestBar() {
        suggestions = new String[0];   // 새 막대는 비어 있다 (예전 막대의 내용과 비교하지 않도록)
        suggestWord = "";
        LinearLayout bar = new LinearLayout(this);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        for (int i = 0; i < suggestViews.length; i++) {
            final int index = i;
            TextView t = new TextView(this);
            t.setSingleLine(true);
            t.setEllipsize(TextUtils.TruncateAt.START);   // 길면 앞을 …로 줄이고 뒤(어미)를 보인다
            t.setGravity(Gravity.CENTER);
            t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
            t.setTextColor(theme.text);
            t.setPadding(Ui.dp(this, 6), 0, Ui.dp(this, 6), 0);
            t.setBackground(Ui.ripple(theme.keyPressed, null, 0));
            t.setOnClickListener(v -> {
                feedback.onKey(null);
                applySuggestion(index);
            });
            t.setOnLongClickListener(v -> forgetSuggestion(index));
            suggestViews[i] = t;
            bar.addView(t, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));
        }
        return bar;
    }

    private LinearLayout buildSearchBar() {
        LinearLayout bar = new LinearLayout(this);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(Ui.dp(this, 4), Ui.dp(this, 5), Ui.dp(this, 6), Ui.dp(this, 5));
        IconButton back = new IconButton(this, Icons.BACK, theme.text, theme.keyPressed, "이모지로 돌아가기");
        back.setOnClickListener(v -> showPanel(PANEL_EMOJI));
        bar.addView(back, new LinearLayout.LayoutParams(Ui.dp(this, 44), ViewGroup.LayoutParams.MATCH_PARENT));
        searchText = new TextView(this);
        searchText.setSingleLine(true);
        searchText.setEllipsize(TextUtils.TruncateAt.START);
        searchText.setGravity(Gravity.CENTER_VERTICAL);
        searchText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        searchText.setPadding(Ui.dp(this, 14), 0, Ui.dp(this, 14), 0);
        searchText.setBackground(Ui.round(theme.functionKey, Ui.dp(this, 20)));
        bar.addView(searchText, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));
        IconButton clear = new IconButton(this, Icons.CLOSE, theme.hint, theme.keyPressed, "검색어 지우기");
        clear.setOnClickListener(v -> {
            composer.reset();
            search.setLength(0);
            searchComposing = "";
            updateSearch();
        });
        bar.addView(clear, new LinearLayout.LayoutParams(Ui.dp(this, 40), ViewGroup.LayoutParams.MATCH_PARENT));
        return bar;
    }

    /** 설정값을 뷰에 적용한다. 설정 화면에서 돌아올 때마다 호출된다. */
    private void applySettings() {
        boolean wasWords = cfgWords, wasLearn = cfgLearn;
        cfgSuggest = prefs.suggestWords();
        cfgAutoCorrect = prefs.autoCorrect();   // 단어 추천과 따로 켜고 끈다
        cfgWords = cfgSuggest || cfgAutoCorrect;
        cfgLearn = cfgWords && prefs.learnWords();
        suggester.setUserEnabled(cfgLearn);
        feedback.configure(prefs);
        composer.setDoubleTapVowel(prefs.doubleTapVowel());
        composer.setDoubleTapConsonant(prefs.doubleTapConsonant(), prefs.doubleTapConsonantMs());
        // 가로 모드·대화면에서 따로 정한 키보드 높이·글자 크기는 그 화면에서만 쓴다.
        Prefs sizePrefs = prefs.profileView(profile());
        // 한 손 모드에서는 키 높이를 줄인 만큼 키 글자도 같은 비율로 줄인다 (도구 막대는 줄어든 자판 바로 위에 붙어 따라온다).
        float oneHandScale = !Prefs.ONE_HAND_OFF.equals(activeOneHand()) ? prefs.oneHandHeight() / 100f : 1f;
        keyboard.configure(prefs.longPressMs(), prefs.swipeThresholdDp(), prefs.keyPreview(),
                prefs.longPressChars(), prefs.spaceCursor(), Math.round(sizePrefs.keyTextSize() * oneHandScale));
        keyboard.setDirectionRatios(prefs.swipeDownRatio(), prefs.swipeUpRatio());
        keyboard.setFunctionSwipeThreshold(prefs.fnSwipeThresholdDp());
        keyboard.setCursorAxes(prefs.spaceCursorH(), prefs.spaceCursorV());
        keyboard.setCursorSpeed(prefs.spaceCursorSpeedH(), prefs.spaceCursorSpeedV(),
                prefs.charCursorSpeedH(), prefs.charCursorSpeedV());
        keyboard.setSpaceLangSwipe(prefs.spaceLangSwipe());
        keyboard.setDeleteLongPress(prefs.longPressDelete(), prefs.deletePressMs());
        keyboard.setModeKeyLongPress(prefs.modeKeyLongPress());
        keyboard.setLongPressActions(prefs.longPressCustom()
                ? com.alternative_studios.newswipe.keyboard.LongPressActions.actions(prefs) : null);
        keyboard.setModeKeyEmoji(prefs.modeKeyEmoji());
        keyboard.setFunctionSwipes(prefs.swipeFnCustom() ? FunctionSwipes.load(prefs) : null);
        keyboard.setFnKeyActions(com.alternative_studios.newswipe.keyboard.FnKeyActions.actions(prefs));
        keyboard.setDeleteWordSwipe(prefs.deleteWordSwipe());
        // 키보드 밀기 완전 사용자화를 켜면 문자 키 커서 이동은 편집 화면의 '커서 자유 이동'을 따른다.
        keyboard.setPopupHints(prefs.longPressChars() && !prefs.popupHintHidden());
        keyboard.setHitShrink(prefs.deleteHitShrink() ? prefs.deleteHitShrinkPct() / 100f : 0f,
                prefs.spaceHitShrink() ? prefs.spaceHitShrinkPct() / 100f : 0f);
        keyboard.setCharCursor(!prefs.swipeKeyboardCustom() && prefs.charCursor(),
                prefs.charCursorH(), prefs.charCursorV());
        // 키보드 밀기: 완전 사용자화를 켜면 편집한 배치를, 끄면 '두 손가락으로 밀어서 실행 취소'만 쓴다.
        keyboard.setKeyboardSwipes(prefs.swipeKeyboardCustom() ? KeyboardSwipes.load(prefs)
                : prefs.twoFingerUndo() ? KeyboardSwipes.twoFingerUndo() : null);
        toolbarSwipes = prefs.swipeToolbarCustom() ? ToolbarSwipes.load(prefs)
                : ToolbarSwipes.standard(prefs.toolbarSwipe(), prefs.toolClipboardSwipe(), prefs.toolEmojiSwipe(),
                        prefs.toolUndoSwipe());
        // 지우기 키를 밀어 단어를 지울 때와 같은 거리('밀어서 기능'의 미는 거리의 1.5배)를 쓴다.
        if (toolbar != null) {
            toolbar.configure(!SwipeAction.NONE.equals(toolbarAction(Key.SWIPE_UP)),
                    !SwipeAction.NONE.equals(toolbarAction(Key.SWIPE_DOWN)),
                    !SwipeAction.NONE.equals(toolbarAction(Key.SWIPE_LEFT)),
                    !SwipeAction.NONE.equals(toolbarAction(Key.SWIPE_RIGHT)),
                    Ui.dp(this, prefs.fnSwipeThresholdDp()) * 1.5f);
        }
        keyboard.setKeyShadow(prefs.keyShadow(), prefs.keyShadowStrength());
        keyboard.setKeyRadius(Ui.dp(this, prefs.keyRadiusDp()));
        keyboard.setGridColors(prefs.gridColors());
        if (panel == PANEL_KEYBOARD) topSlot.setVisibility(toolbarGone() ? View.GONE : View.VISIBLE);
        refreshToolbarVisibility();
        if (cfgWords) {
            if (!wasWords || (cfgLearn && !wasLearn)) warmSuggester();
            if (panel == PANEL_KEYBOARD) scheduleSuggest();   // 추천을 껐으면 이 안에서 지운다
        } else {
            clearSuggestions();
            suggester.release();
            warmingKo = warmingEn = false;
        }
        if (wasLearn && !cfgLearn) runIo(userWords::release);   // 학습을 끄면 학습한 단어도 메모리에서 내린다
        float rows = 4 + (prefs.numberRow() ? 0.78f : 0f);
        keyboard.setKeyGaps(Ui.dp(this, prefs.keyGapXDp()), Ui.dp(this, prefs.keyGapYDp()));
        keyboard.setInsets(Ui.dp(this, prefs.padLeftDp()), Ui.dp(this, prefs.padTopDp()),
                Ui.dp(this, prefs.padRightDp()), Ui.dp(this, prefs.padBottomDp()));
        // 도구 막대의 양 끝 버튼이 자판의 양 끝 키와 같은 선에 오도록 '자판 모양'의 좌우 여백을 따른다.
        if (toolbar != null) toolbar.setPadding(Ui.dp(this, prefs.padLeftDp()), 0, Ui.dp(this, prefs.padRightDp()), 0);
        arrangeToolbar();
        if (topSlot != null) {
            sizeToolbar();
            placeTopSlot();
        }
        // 한 손 모드에서는 키 높이를 '키 높이' 비율만큼 줄인다.
        keyboardHeight = Math.round(Ui.dp(this, 54) * rows * sizePrefs.keyboardHeight() / 100f * oneHandScale)
                + Ui.dp(this, prefs.padTopDp()) + Ui.dp(this, prefs.padBottomDp());
        keyboard.setKeyboardHeight(keyboardHeight);
        applyOneHand();
        updatePanelSizes();
    }

    // ---------------------------------------------------------------- 한 손 모드

    /** 한 손 모드에서 자판 반대편 빈 곳: 반대편으로 옮기는 화살표와 일반 모드로 돌아가는 확장 아이콘. */
    private LinearLayout buildSideStrip() {
        LinearLayout strip = new LinearLayout(this);
        strip.setOrientation(LinearLayout.VERTICAL);
        strip.setGravity(Gravity.CENTER);
        strip.setVisibility(View.GONE);
        sideSwap = sideButton(Icons.NEXT, "반대편으로 옮기기", v ->
                setOneHand(Prefs.ONE_HAND_LEFT.equals(prefs.oneHand()) ? Prefs.ONE_HAND_RIGHT : Prefs.ONE_HAND_LEFT));
        strip.addView(sideSwap);
        sideExpand = sideButton(Icons.EXPAND, "한 손 모드 끄기", v -> setOneHand(Prefs.ONE_HAND_OFF));
        strip.addView(sideExpand);
        return strip;
    }

    private IconButton sideButton(int icon, String desc, View.OnClickListener l) {
        IconButton b = new IconButton(this, icon, theme.hint, theme.keyPressed, desc);
        int r = Ui.dp(this, 12);
        b.setBackground(Ui.ripple(theme.keyPressed, Ui.round(theme.functionKey, r), r));
        b.setOnClickListener(v -> {
            feedback.onKey(null);
            l.onClick(v);
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 48));
        lp.setMargins(Ui.dp(this, 6), Ui.dp(this, 6), Ui.dp(this, 6), Ui.dp(this, 6));
        b.setLayoutParams(lp);
        return b;
    }

    /** 지금 화면에서 쓸 키보드 모습의 묶음: 세로·가로 모드, 또는 (실험실 '대화면 별도 레이아웃'이 켜져 있으면) 대화면 세로·가로. */
    private String profile() {
        return prefs.profileFor(this);
    }

    /** 한 손 모드가 실제로 적용되는 상태. */
    private String activeOneHand() {
        return prefs.oneHand();
    }

    private void toggleOneHand() {
        setOneHand(prefs.oneHandOn() ? Prefs.ONE_HAND_OFF : prefs.oneHandLast());
    }

    /** 기능(밀어서 기능 등)의 '한 손 모드 (왼쪽·오른쪽)': 그쪽 한 손 모드를 켜고, 이미 그쪽이면 끈다. */
    private void toggleOneHand(String side) {
        setOneHand(side.equals(prefs.oneHand()) ? Prefs.ONE_HAND_OFF : side);
    }

    /** 한 손 모드를 바꾸고 바로 적용한다 (설정이 바뀐 것을 알아채는 쪽에서도 한 번 더 적용하지만 바뀐 것이 없어 그냥 지나간다). */
    private void setOneHand(String side) {
        if (side.equals(prefs.oneHand())) return;
        prefs.setOneHand(side);
        if (keyboard == null) return;
        keyboard.cancelAll();
        applySettings();
        setLayoutKind(layoutKind);   // 자·모음 균형 레이아웃은 한 손 모드에서 빈틈 없는 배열로 바뀐다
    }

    /**
     * 한 손 모드 배치를 뷰에 반영한다: column을 '자판 폭'만큼만 쓰고 한쪽으로 붙이며, 반대편에 sideStrip을 둔다.
     * 아래에는 '세로 위치'만큼 빈 칸을 두어 자판을 띄운다.
     */
    private void applyOneHand() {
        if (body == null) return;
        String side = activeOneHand();
        boolean on = !Prefs.ONE_HAND_OFF.equals(side);
        int width = prefs.profileView(profile()).oneHandWidthForProfile();
        int lift = on ? Ui.dp(this, prefs.oneHandLiftDp()) : 0;
        String signature = side + "/" + width + "/" + lift;
        if (signature.equals(appliedOneHand)) return;
        appliedOneHand = signature;
        body.removeAllViews();
        if (on) {
            boolean left = Prefs.ONE_HAND_LEFT.equals(side);
            LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, width);
            LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 100 - width);
            if (left) {
                body.addView(column, clp);
                body.addView(sideStrip, slp);
            } else {
                body.addView(sideStrip, slp);
                body.addView(column, clp);
            }
            // 화살표는 자판을 옮길 쪽(지금 빈 곳 쪽)을 가리킨다.
            sideSwap.setIcon(left ? Icons.NEXT : Icons.BACK);
            sideStrip.setVisibility(View.VISIBLE);
        } else {
            body.addView(column, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT));
            sideStrip.setVisibility(View.GONE);
        }
        ViewGroup.LayoutParams llp = liftSpace.getLayoutParams();
        llp.height = lift;
        liftSpace.setLayoutParams(llp);
        liftSpace.setVisibility(lift > 0 ? View.VISIBLE : View.GONE);
    }

    // ---------------------------------------------------------------- 입력 시작/종료

    @Override
    public void onStartInputView(EditorInfo info, boolean restarting) {
        super.onStartInputView(info, restarting);
        windowHidden = false;   // 창이 실제로 보이기(onWindowShown) 전에 불리므로 여기서도 켠다
        if (KeyboardTheme.of(this) != theme) {
            setInputView(onCreateInputView());
        }
        applySettings();
        composer.reset();
        hadComposing = false;
        // 다른 앱에 다녀오면 입력란에 조합 중 표시(밑줄)가 남아 있는 일이 있다. 조합 상태는 위에서 비웠으므로
        // 그 표시도 확정해 둔다. 그대로 두면 다음 글자의 setComposingText가 남은 조합 영역(마지막 글자)을 덮어 지운다.
        InputConnection startIc = getCurrentInputConnection();
        if (startIc != null) startIc.finishComposingText();
        lastWasSpace = false;
        if (!restarting || panel == PANEL_SEARCH) showPanel(PANEL_KEYBOARD);
        int cls = info.inputType & InputType.TYPE_MASK_CLASS;
        suggestAllowed = suggestAllowedFor(info);
        // 시크릿 모드 등 앱이 학습하지 말라고 표시한 입력란에서는 단어를 학습하지 않는다 (추천은 그대로).
        learnAllowed = (info.imeOptions & EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING) == 0;
        mirror.start(info.initialSelStart, info.initialSelEnd);
        selStart = info.initialSelStart;
        selEnd = info.initialSelEnd;
        clearSuggestions();
        if (cls == InputType.TYPE_CLASS_NUMBER || cls == InputType.TYPE_CLASS_PHONE
                || cls == InputType.TYPE_CLASS_DATETIME) {
            setLayoutKind(KeyboardLayout.NUMBER);
        } else {
            setLayoutKind(korean ? KeyboardLayout.KOREAN : KeyboardLayout.ENGLISH);
        }
        keyboard.setEnterIcon(enterIcon(info));
        setShift(0);
        updateAutoCap();
        warmSuggester();   // 첫 글자부터 추천이 나오도록 미리 읽어 둔다
        scheduleSuggest();
        applyTopSlotVisibility();
    }

    @Override
    public void onFinishInputView(boolean finishingInput) {
        finishComposingOnLeave();
        super.onFinishInputView(finishingInput);
        if (keyboard != null) keyboard.cancelAll();
        if (emojiPanel != null) emojiPanel.dismissTones();
        if (clipPanel != null) clipPanel.dismissDialog();
        dismissForgetDialog();
        if (cfgLearn) runIo(userWords::save);   // 바뀐 것이 있을 때만 쓴다
    }

    @Override
    public void onFinishInput() {
        finishComposingOnLeave();
        super.onFinishInput();
        composer.reset();
        hadComposing = false;
        mirror.forget();
        clearSuggestions();
    }

    @Override
    public void onUpdateSelection(int oldSelStart, int oldSelEnd, int newSelStart, int newSelEnd,
                                  int candidatesStart, int candidatesEnd) {
        super.onUpdateSelection(oldSelStart, oldSelEnd, newSelStart, newSelEnd, candidatesStart, candidatesEnd);
        mirror.onSelection(newSelStart, newSelEnd);   // 이모지 검색 중에도 실제 입력란의 커서 변화는 따라간다
        selStart = newSelStart;
        selEnd = newSelEnd;
        if (panel == PANEL_SEARCH) return;
        // 사용자가 커서를 옮기면 조합 중인 글자를 확정하고 조합을 끝낸다.
        if (hadComposing && (candidatesEnd == -1 || newSelStart != candidatesEnd || newSelEnd != candidatesEnd)) {
            composer.reset();
            hadComposing = false;
            InputConnection ic = getCurrentInputConnection();
            if (ic != null) ic.finishComposingText();
            mirror.forget();
        }
        if (composer.isEmpty()) updateAutoCap();
        scheduleSuggest();
    }

    private static int enterIcon(EditorInfo info) {
        if ((info.imeOptions & EditorInfo.IME_FLAG_NO_ENTER_ACTION) != 0) return Icons.ENTER;
        switch (info.imeOptions & EditorInfo.IME_MASK_ACTION) {
            case EditorInfo.IME_ACTION_SEARCH:
                return Icons.SEARCH;
            case EditorInfo.IME_ACTION_SEND:
                return Icons.SEND;
            case EditorInfo.IME_ACTION_GO:
            case EditorInfo.IME_ACTION_NEXT:
                return Icons.NEXT;
            case EditorInfo.IME_ACTION_DONE:
                return Icons.DONE;
            default:
                return Icons.ENTER;
        }
    }

    // ---------------------------------------------------------------- 자판/패널 전환

    private void setLayoutKind(int kind) {
        layoutKind = kind;
        // 한 손 모드에서는 자·모음 균형 레이아웃의 가운데 빈틈을 없앤 배열을 쓴다.
        Prefs prefs = !Prefs.ONE_HAND_OFF.equals(activeOneHand()) ? this.prefs.oneHandView() : this.prefs;
        // 자·모음 균형 레이아웃의 키 폭·위치는 가로 모드·대화면일 때 세로 모드와 따로 정한 값을 쓴다.
        // 화면을 돌리면 시스템이 입력 뷰를 다시 만들고(onCreateInputView → applySettings), 입력을 다시 시작하므로 여기서 방향을 읽으면 된다.
        prefs = prefs.profileView(profile());
        KeyboardLayout l;
        switch (kind) {
            case KeyboardLayout.ENGLISH:
                l = KeyboardLayout.english(prefs);
                break;
            case KeyboardLayout.SYMBOLS:
                l = KeyboardLayout.symbols(korean, prefs);
                break;
            case KeyboardLayout.SYMBOLS_2:
                l = KeyboardLayout.symbols2(korean, prefs);
                break;
            case KeyboardLayout.NUMBER:
                l = KeyboardLayout.number();
                break;
            default:
                l = KeyboardLayout.korean(prefs);
                break;
        }
        keyboard.setLayout(l);
        if (kind != KeyboardLayout.ENGLISH && shiftState != 0) setShift(0);
    }

    private void showPanel(int p) {
        if (p != PANEL_SEARCH && panel == PANEL_SEARCH) {
            composer.reset();
            search.setLength(0);
            searchComposing = "";
        }
        if (p != PANEL_KEYBOARD && p != PANEL_SEARCH) commitComposing();
        panel = p;
        if (root == null) return;
        boolean full = p == PANEL_EMOJI || p == PANEL_CLIPBOARD;
        placeTopSlot();
        topSlot.setVisibility(full || (p == PANEL_KEYBOARD && toolbarGone()) ? View.GONE : View.VISIBLE);
        refreshToolbarVisibility();
        searchBar.setVisibility(p == PANEL_SEARCH ? View.VISIBLE : View.GONE);
        resultScroll.setVisibility(p == PANEL_SEARCH ? View.VISIBLE : View.GONE);
        keyboard.setVisibility(full ? View.GONE : View.VISIBLE);
        if (p != PANEL_KEYBOARD) clearSuggestions();
        else scheduleSuggest();
        keyboard.cancelAll();
        if (emojiPanel != null) {
            emojiPanel.setVisibility(p == PANEL_EMOJI ? View.VISIBLE : View.GONE);
            emojiPanel.dismissTones();
        }
        if (clipPanel != null) clipPanel.setVisibility(p == PANEL_CLIPBOARD ? View.VISIBLE : View.GONE);

        if (p == PANEL_EMOJI) {
            ensureEmojiPanel();
            emojiPanel.setVisibility(View.VISIBLE);
            emojiPanel.setBackLabel(korean ? "가" : "ABC");
            emojiPanel.reset();
        } else if (p == PANEL_CLIPBOARD) {
            ensureClipPanel();
            clipPanel.setVisibility(View.VISIBLE);
            refreshClipboard();
        } else if (p == PANEL_SEARCH) {
            if (!lettersLayout()) setLayoutKind(korean ? KeyboardLayout.KOREAN : KeyboardLayout.ENGLISH);
            keyboard.setEnterIcon(Icons.SEARCH);
            updateSearch();
        } else {
            EditorInfo ei = getCurrentInputEditorInfo();
            if (ei != null) keyboard.setEnterIcon(enterIcon(ei));
        }
        updatePanelSizes();
    }

    /** 도구 막대를 끈 상태로 자판을 보여 주는 중인지. 검색 패널은 도구 막대 자리에 검색창이 있어 해당하지 않는다. */
    private boolean toolbarGone() {
        return !prefs.toolbar() && !(cfgSuggest && suggestAllowed) && panel == PANEL_KEYBOARD;
    }

    private void updatePanelSizes() {
        if (content == null) return;
        boolean full = panel == PANEL_EMOJI || panel == PANEL_CLIPBOARD;
        int h = keyboardHeight + (full ? toolbarHeight : 0);
        ViewGroup.LayoutParams lp = content.getLayoutParams();
        if (lp != null && lp.height != h) {
            lp.height = h;
            content.setLayoutParams(lp);
        }
        if (emojiPanel != null) setHeight(emojiPanel, keyboardHeight + toolbarHeight);
        if (clipPanel != null) setHeight(clipPanel, keyboardHeight + toolbarHeight);
        // 자판 위에 도구 막대가 있으면 키 미리보기가 그 위까지 올라갈 수 있다 (아래에 두었거나 껐으면 자판 안에서 멈춘다).
        keyboard.setOverflowTop(panel == PANEL_SEARCH ? toolbarHeight + Ui.dp(this, 50)
                : toolbarGone() || prefs.toolbarBottom() ? 0 : toolbarHeight);
    }

    private static void setHeight(View v, int h) {
        ViewGroup.LayoutParams lp = v.getLayoutParams();
        if (lp != null && lp.height != h) {
            lp.height = h;
            v.setLayoutParams(lp);
        }
    }

    private void ensureEmojiPanel() {
        if (emojiPanel != null) return;
        emojiPanel = new EmojiPanel(this, theme, Ui.dp(this, 40), this);   // 패널 위쪽 탭 줄은 도구 막대 높이와 관계없이 기본 높이
        // 데이터를 읽는 동안 열려도 처음 탭(최근이 있으면 최근 탭)을 바로 고를 수 있게 사용 기록부터 넘긴다.
        emojiPanel.setRecent(EmojiRepository.parseRecent(prefs.recentEmoji()));
        emojiPanel.setPinned(EmojiRepository.parseRecent(prefs.pinnedEmoji()));
        content.addView(emojiPanel, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                keyboardHeight + toolbarHeight));
        final EmojiPanel target = emojiPanel;
        emojiRepo.load(d -> {
            if (target == emojiPanel) target.setData(d, EmojiRepository.parseRecent(prefs.recentEmoji()),
                    EmojiRepository.parseRecent(prefs.pinnedEmoji()));
        });
    }

    private void ensureClipPanel() {
        if (clipPanel != null) return;
        clipPanel = new ClipboardPanel(this, theme, Ui.dp(this, 40), this);
        content.addView(clipPanel, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                keyboardHeight + toolbarHeight));
    }

    // ---------------------------------------------------------------- KeyboardView.Listener

    @Override
    public void onKeyPress(Key key) {
        deleteRepeatChecked = false;
        feedback.onKey(key);
    }

    @Override
    public void onKeyTap(Key key) {
        if (key.type != Key.SPACE) lastWasSpace = false;
        switch (key.type) {
            case Key.CHAR:
                if (shiftState != 0 && lettersLayout()) {
                    typeText(key.shifted, false);
                } else {
                    typeText(key.output, true);
                }
                break;
            case Key.SPACE:
                handleSpace();
                break;
            case Key.DELETE:
                handleDelete();
                break;
            case Key.ENTER:
                handleEnter();
                break;
            case Key.SHIFT:
                handleShift();
                break;
            case Key.TO_SYMBOLS:
                commitComposing();
                setLayoutKind(KeyboardLayout.SYMBOLS);
                break;
            case Key.TO_LETTERS:
                setLayoutKind(korean ? KeyboardLayout.KOREAN : KeyboardLayout.ENGLISH);
                updateAutoCap();
                break;
            case Key.SYMBOL_PAGE:
                setLayoutKind(layoutKind == KeyboardLayout.SYMBOLS ? KeyboardLayout.SYMBOLS_2 : KeyboardLayout.SYMBOLS);
                break;
            case Key.LANGUAGE:
                toggleLanguage();
                break;
            case Key.EMOJI:
                showPanel(PANEL_EMOJI);
                break;
            default:
                break;
        }
    }

    @Override
    public void onKeySwipe(Key key, String text) {
        lastWasSpace = false;
        typeText(text, false);
    }

    @Override
    public void onPopupChar(Key key, String text) {
        lastWasSpace = false;
        typeText(text, false);
    }

    @Override
    public void onKeyLongPress(Key key) {
        if (key.type == Key.LANGUAGE) {
            showImePicker();
        } else if (key.type == Key.TO_SYMBOLS || key.type == Key.TO_LETTERS) {
            // 숫자 자판의 ?123도 같은 기호 키로 본다 (완전 사용자화에서도 같은 칸을 쓴다).
            showPanel(PANEL_EMOJI);
        }
    }

    @Override
    public void onModeKeySwipeUp(Key key) {
        if (layoutKind != KeyboardLayout.NUMBER) showPanel(PANEL_EMOJI);   // 숫자 입력란에는 이모지를 쓰지 않는다
    }

    @Override
    public void onDeleteRepeat() {
        // 선택 영역 확인(앱에 묻고 기다리는 호출)은 연속 삭제의 첫 번째에서만 한다.
        // 첫 삭제 뒤에는 선택 영역이 사라지므로 이후에는 묻지 않아도 된다.
        handleDelete(deleteRepeatChecked);
        deleteRepeatChecked = true;
    }

    /** 이번 지우기 키 누름에서 선택 영역을 이미 확인했는지. 키를 누를 때마다 초기화된다. */
    private boolean deleteRepeatChecked;

    @Override
    public void onDeleteWord() {
        if (panel == PANEL_SEARCH) {
            composer.reset();
            search.setLength(0);
            searchComposing = "";
            updateSearch();
            return;
        }
        commitComposing();
        InputConnection ic = getCurrentInputConnection();
        if (ic == null) return;
        correctedTo = null;
        CharSequence sel = ic.getSelectedText(0);
        if (sel != null && sel.length() > 0) {
            ic.commitText("", 1);
            mirror.forget();
            scheduleSuggest();
            return;
        }
        CharSequence before = ic.getTextBeforeCursor(64, 0);
        if (before == null || before.length() == 0) return;
        int i = before.length();
        while (i > 0 && Character.isWhitespace(before.charAt(i - 1))) i--;
        if (i > 0 && !Character.isLetterOrDigit(before.charAt(i - 1))) {
            i--;   // 문장 부호 하나
        } else {
            while (i > 0 && Character.isLetterOrDigit(before.charAt(i - 1))) i--;
        }
        ic.deleteSurroundingText(before.length() - i, 0);
        mirror.replaceTail(before.length() - i, "");
        scheduleSuggest();
    }

    /**
     * 지우기 키를 위로 밀었을 때: 커서가 있는 줄에서 커서 왼쪽을 모두 지운다 (줄바꿈은 지우지 않는다).
     * 커서가 이미 줄의 맨 앞이면 지우기 키를 한 번 누른 것처럼 윗줄과 합친다.
     */
    @Override
    public void onDeleteToLineStart() {
        if (panel == PANEL_SEARCH) {
            composer.reset();
            search.setLength(0);
            searchComposing = "";
            updateSearch();
            return;
        }
        commitComposing();
        InputConnection ic = getCurrentInputConnection();
        if (ic == null) return;
        correctedTo = null;
        CharSequence sel = ic.getSelectedText(0);
        if (sel != null && sel.length() > 0) {   // 선택한 글자가 있으면 그것만 지운다 (단어 지우기와 같다)
            ic.commitText("", 1);
        } else if (!joinWithPreviousLine(ic)) {
            int before = charsToLineEdge(ic, true);
            if (before > 0) ic.deleteSurroundingText(before, 0);
        }
        mirror.forget();
        scheduleSuggest();
    }

    @Override
    public void onCursorMove(int direction) {
        if (panel == PANEL_SEARCH) return;
        correctedTo = null;
        commitComposing();
        sendDownUpKeyEvents(direction < 0 ? KeyEvent.KEYCODE_DPAD_LEFT : KeyEvent.KEYCODE_DPAD_RIGHT);
        mirror.forget();
    }

    @Override
    public void onCursorMoveVertical(int direction) {
        if (panel == PANEL_SEARCH) return;
        // 한 줄짜리 입력란에서 위/아래 키를 보내면 다른 입력란으로 포커스가 넘어가므로 여러 줄 입력란에서만 움직인다.
        EditorInfo ei = getCurrentInputEditorInfo();
        if (ei == null || (ei.inputType & InputType.TYPE_MASK_CLASS) != InputType.TYPE_CLASS_TEXT
                || (ei.inputType & (InputType.TYPE_TEXT_FLAG_MULTI_LINE | InputType.TYPE_TEXT_FLAG_IME_MULTI_LINE)) == 0) {
            return;
        }
        correctedTo = null;
        commitComposing();
        sendDownUpKeyEvents(direction < 0 ? KeyEvent.KEYCODE_DPAD_UP : KeyEvent.KEYCODE_DPAD_DOWN);
        mirror.forget();
    }

    @Override
    public void onShiftChordEnd() {
        if (shiftState == 1) setShift(0);
    }

    // ---------------------------------------------------------------- 입력 처리

    /** 글자 하나(또는 문자열)를 입력한다. tap = 그냥 탭했는지 (모음·자음 연속 탭 판단용). */
    private void typeText(String s, boolean tap) {
        if (s == null || s.isEmpty()) return;
        correctedTo = null;
        boolean letters = lettersLayout();
        if (korean && letters && s.length() == 1 && HangulComposer.isJamo(s.charAt(0))) {
            composer.input(s.charAt(0), tap, android.os.SystemClock.uptimeMillis());
            syncComposer();
        } else {
            commitComposing();
            if (letters && !Character.isLetterOrDigit(s.charAt(0))) learnCurrentWord(null);
            commitText(s);
        }
        if (shiftState == 1 && letters && !keyboard.isShiftHeld()) setShift(0);
        scheduleSuggest();
    }

    /** 조합기 상태를 편집기(또는 검색창)에 반영한다. */
    private void syncComposer() {
        String commit = composer.takeCommit();
        String comp = composer.getComposing();
        if (panel == PANEL_SEARCH) {
            search.append(commit);
            searchComposing = comp;
            updateSearch();
            return;
        }
        InputConnection ic = getCurrentInputConnection();
        if (ic == null) return;
        ic.beginBatchEdit();
        if (!commit.isEmpty()) {
            ic.commitText(commit, 1);
            mirror.commit(commit);
        }
        if (!comp.isEmpty() || (hadComposing && commit.isEmpty())) {
            ic.setComposingText(comp, 1);
            mirror.setComposing(comp);
        }
        ic.endBatchEdit();
        hadComposing = !comp.isEmpty();
    }

    /**
     * 키보드가 내려가거나 입력란을 떠날 때 조합 중인 글자를 그대로 확정한다 (입력란에 보이는 글자는 바뀌지 않는다).
     * 조합 상태만 비우고 입력란의 조합 영역을 남겨 두면, 돌아와서 친 글자가 그 영역을 덮어 마지막 글자가 사라진다.
     */
    private void finishComposingOnLeave() {
        if (panel == PANEL_SEARCH || (!hadComposing && composer.isEmpty())) return;
        InputConnection ic = getCurrentInputConnection();
        if (ic != null) ic.finishComposingText();
        composer.reset();
        hadComposing = false;
        mirror.forget();
    }

    private void commitComposing() {
        if (composer.isEmpty() && !hadComposing) return;
        composer.flush();
        String s = composer.takeCommit();
        if (panel == PANEL_SEARCH) {
            search.append(s);
            searchComposing = "";
            updateSearch();
            return;
        }
        InputConnection ic = getCurrentInputConnection();
        if (ic != null) {
            if (!s.isEmpty()) {
                ic.commitText(s, 1);
                mirror.commit(s);
            } else {
                ic.finishComposingText();
                mirror.finishComposing();
            }
        }
        hadComposing = false;
    }

    private void commitText(String s) {
        if (panel == PANEL_SEARCH) {
            search.append(s);
            updateSearch();
            return;
        }
        InputConnection ic = getCurrentInputConnection();
        if (ic != null) {
            ic.commitText(s, 1);
            mirror.commit(s);
        }
    }

    private void handleSpace() {
        commitComposing();
        if (panel == PANEL_SEARCH) {
            commitText(" ");
            return;
        }
        InputConnection ic = getCurrentInputConnection();
        if (ic == null) return;
        long now = SystemClock.uptimeMillis();
        if (prefs.doubleSpacePeriod() && lastWasSpace && now - lastSpaceTime < 1200) {
            // 바로 앞이 스페이스라 고치거나 기억할 단어가 없다. 글자를 바꾸므로 앱에서 직접 읽는다.
            correctedTo = null;
            CharSequence before = ic.getTextBeforeCursor(2, 0);
            if (before != null && before.length() == 2 && before.charAt(1) == ' '
                    && Character.isLetterOrDigit(before.charAt(0))) {
                ic.beginBatchEdit();
                ic.deleteSurroundingText(1, 0);
                ic.commitText(". ", 1);
                ic.endBatchEdit();
                mirror.replaceTail(1, ". ");
                lastWasSpace = false;
                return;
            }
        }
        // 자동 수정은 글자를 바꾸므로 앱에서 직접 읽고(한 번만 읽어 학습에도 쓴다), 학습만 할 때는 따라 적은 글자를 쓴다.
        CharSequence before = null;
        if (autoCorrectActive()) {
            before = ic.getTextBeforeCursor(32, 0);
            mirror.fill(before, 32);
            if (tryAutoCorrect(ic, before)) return;
        }
        learnCurrentWord(before);
        correctedTo = null;
        ic.commitText(" ", 1);
        mirror.commit(" ");
        lastWasSpace = true;
        lastSpaceTime = now;
    }

    private void handleDelete() {
        handleDelete(false);
    }

    /** @param skipSelectionCheck 연속 삭제 중이라 선택 영역 확인을 건너뛴다. */
    private void handleDelete(boolean skipSelectionCheck) {
        lastWasSpace = false;
        if (!composer.isEmpty()) {
            composer.backspace();
            syncComposer();
            // '숫'→'수'처럼 조합 중인 글자만 바뀌면 커서 위치가 그대로라 앱이 알려 오지 않을 수 있다. 직접 다시 찾는다.
            scheduleSuggest();
            return;
        }
        if (panel == PANEL_SEARCH) {
            if (search.length() > 0) {
                int cut = search.offsetByCodePoints(search.length(), -1);
                search.setLength(cut);
            }
            updateSearch();
            return;
        }
        if (undoAutoCorrect()) return;
        deleteBeforeCursor(skipSelectionCheck);
        scheduleSuggest();
    }

    private void deleteBeforeCursor(boolean skipSelectionCheck) {
        InputConnection ic = getCurrentInputConnection();
        if (ic == null) return;
        CharSequence sel = skipSelectionCheck ? null : ic.getSelectedText(0);
        if (sel != null && sel.length() > 0) {
            ic.commitText("", 1);
            mirror.forget();
        } else {
            sendDownUpKeyEvents(KeyEvent.KEYCODE_DEL);
            mirror.deleteKey();
        }
    }

    private void handleEnter() {
        correctedTo = null;
        commitComposing();
        if (panel == PANEL_SEARCH) return;
        EditorInfo ei = getCurrentInputEditorInfo();
        InputConnection ic = getCurrentInputConnection();
        if (ic == null) return;
        learnCurrentWord(null);
        mirror.forget();   // 엔터가 줄을 바꿀지, 보내기를 할지는 앱 마음이다
        int action = ei == null ? EditorInfo.IME_ACTION_NONE : ei.imeOptions & EditorInfo.IME_MASK_ACTION;
        boolean noAction = ei == null || (ei.imeOptions & EditorInfo.IME_FLAG_NO_ENTER_ACTION) != 0;
        if (!noAction && action != EditorInfo.IME_ACTION_NONE && action != EditorInfo.IME_ACTION_UNSPECIFIED) {
            ic.performEditorAction(action);
        } else {
            sendDownUpKeyEvents(KeyEvent.KEYCODE_ENTER);
        }
    }

    private void handleShift() {
        long now = SystemClock.uptimeMillis();
        boolean quick = now - lastShiftTime < 350;
        lastShiftTime = now;
        int before = shiftState;
        // 빠르게 두 번 누르면 Caps Lock. Shift가 이미 켜져 있었으면(자동 대문자 등) 첫 탭이 끄고
        // 둘째 탭이 다시 켜게 되므로, 그 경우에도 둘째 탭에서 Caps Lock으로 간다.
        if (shiftState == 0) setShift(quick && lastShiftTapTurnedOff ? 2 : 1);
        else if (shiftState == 1) setShift(quick ? 2 : 0);
        else setShift(0);
        lastShiftTapTurnedOff = before == 1 && shiftState == 0;
    }

    /** 바로 앞의 Shift 탭이 켜져 있던 Shift(1)를 끈 것인지. */
    private boolean lastShiftTapTurnedOff;

    private void setShift(int s) {
        shiftState = s;
        if (keyboard != null) keyboard.setShiftState(s);
    }

    /** 영어 자판에서 문장 첫 글자면 Shift를 켠다. */
    private void updateAutoCap() {
        if (keyboard == null || shiftState == 2) return;
        if (korean || layoutKind != KeyboardLayout.ENGLISH || panel == PANEL_SEARCH || !prefs.autoCap()) {
            if (shiftState == 1 && layoutKind != KeyboardLayout.ENGLISH) setShift(0);
            return;
        }
        EditorInfo ei = getCurrentInputEditorInfo();
        InputConnection ic = getCurrentInputConnection();
        if (ei == null || ic == null || ei.inputType == InputType.TYPE_NULL) return;
        setShift(ic.getCursorCapsMode(ei.inputType) != 0 ? 1 : 0);
    }

    /** 기호 키처럼: 기호 자판이면 글자 자판으로, 아니면 기호 자판으로 바꾼다. */
    private void toggleSymbols() {
        if (layoutKind == KeyboardLayout.SYMBOLS || layoutKind == KeyboardLayout.SYMBOLS_2) {
            setLayoutKind(korean ? KeyboardLayout.KOREAN : KeyboardLayout.ENGLISH);
            updateAutoCap();
        } else {
            commitComposing();
            setLayoutKind(KeyboardLayout.SYMBOLS);
        }
    }

    private void toggleLanguage() {
        commitComposing();
        korean = !korean;
        clearSuggestions();
        warmSuggester();
        prefs.setKorean(korean);
        setShift(0);
        if (layoutKind == KeyboardLayout.KOREAN || layoutKind == KeyboardLayout.ENGLISH) {
            setLayoutKind(korean ? KeyboardLayout.KOREAN : KeyboardLayout.ENGLISH);
        } else {
            setLayoutKind(layoutKind);   // 기호 자판의 '가/ABC' 표시만 바뀐다.
        }
        updateAutoCap();
        if (panel == PANEL_SEARCH) updateSearch();
    }

    // ---------------------------------------------------------------- 단어 추천

    private static boolean suggestAllowedFor(EditorInfo info) {
        int type = info.inputType;
        if ((type & InputType.TYPE_MASK_CLASS) != InputType.TYPE_CLASS_TEXT) return false;
        switch (type & InputType.TYPE_MASK_VARIATION) {
            case InputType.TYPE_TEXT_VARIATION_PASSWORD:
            case InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD:
            case InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD:
            case InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS:
            case InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS:
            case InputType.TYPE_TEXT_VARIATION_FILTER:
                return false;
            default:
                return true;
        }
    }

    private final Runnable suggestRunnable = this::updateSuggestions;

    /** 지금 입력란에서 단어 추천을 쓰는지. */
    private boolean suggestOn() {
        return cfgSuggest && suggestAllowed && panel == PANEL_KEYBOARD;
    }

    /** 지금 입력란에서 사전을 쓰는 기능(추천·자동 수정·학습)을 쓰는지. */
    private boolean wordsOn() {
        return cfgWords && suggestAllowed && panel == PANEL_KEYBOARD;
    }

    private boolean autoCorrectActive() {
        return cfgAutoCorrect && wordsOn() && lettersLayout();
    }

    private boolean learningOn() {
        return cfgLearn && wordsOn() && lettersLayout();
    }

    /** 지금 언어의 사전(과 학습한 단어)을 백그라운드에서 읽어 둔다. 이미 읽었거나 읽는 중이면 아무것도 하지 않는다. */
    private void warmSuggester() {
        if (!cfgWords || !suggestAllowed || windowHidden) return;
        final boolean lang = korean;
        if (lang ? warmingKo : warmingEn) return;
        if (suggester.isSettled(lang) && (!cfgLearn || userWords.isLoaded())) return;
        if (lang) warmingKo = true;
        else warmingEn = true;
        final int gen = suggester.generation();
        runIo(() -> {
            suggester.warmUp(lang, gen);
            main.post(() -> {
                if (lang) warmingKo = false;
                else warmingEn = false;
                scheduleSuggest();
            });
        });
    }

    /**
     * 지금 자판이 글자 자판(한글·영어)인지. 입력 뷰를 새로 만든 직후(화면 회전, 테마 변경)에는 자판을 놓기 전이라
     * 아직 배열이 없으므로 false로 본다 (그대로 읽으면 NullPointerException으로 키보드가 죽는다).
     */
    private boolean lettersLayout() {
        KeyboardLayout l = keyboard == null ? null : keyboard.getLayout();
        return l != null && l.isLetters();
    }

    /** 글자가 바뀔 때마다 부르지만, 짧게 모아서 한 번만 찾는다. */
    private void scheduleSuggest() {
        if (suggester == null || keyboard == null) return;
        main.removeCallbacks(suggestRunnable);
        if (!suggestOn() || !lettersLayout()) {
            if (suggestions.length > 0 || !suggestWord.isEmpty()) clearSuggestions();   // 꺼져 있으면 키마다 하는 일이 없다
            return;
        }
        if (windowHidden) return;   // 내려가 있는 동안 앱이 글자를 바꿔도(보낸 뒤 입력란 비우기 등) 찾지 않는다
        main.postDelayed(suggestRunnable, 30);
    }

    /** 커서 앞의 입력 중인 단어. 따라 적은 글자로 알 수 없을 때만 앱에 묻는다. */
    private String wordBeforeCursor(InputConnection ic) {
        String word = mirror.currentWord(korean);
        if (word != null) return word;
        CharSequence before = ic.getTextBeforeCursor(32, 0);
        mirror.fill(before, 32);
        return before == null ? "" : WordSuggester.currentWord(before, korean);
    }

    private void updateSuggestions() {
        if (!suggestOn() || windowHidden || !lettersLayout()) return;
        InputConnection ic = getCurrentInputConnection();
        if (ic == null) return;
        String word = wordBeforeCursor(ic);
        if (word.isEmpty()) {
            showSuggestions(word, new String[0]);
            return;
        }
        if (!suggester.isSettled(korean)) {
            // 사전을 아직 읽지 않았다 (처음이거나 한동안 내려 두었다). 다 읽으면 다시 찾는다. 화면은 기다리지 않는다.
            warmSuggester();
            showSuggestions(word, new String[0]);
            return;
        }
        if (cfgLearn && !userWords.isLoaded()) warmSuggester();   // 학습한 단어를 다시 읽어 둔다 (설정에서 고친 뒤 등)
        showSuggestions(word, suggester.suggestIfLoaded(word, korean));
    }

    private void showSuggestions(String word, String[] found) {
        suggestWord = word;
        if (java.util.Arrays.equals(found, suggestions)) return;   // 그대로면 다시 그리지 않는다
        boolean wasShowing = suggestions.length > 0;
        suggestions = found;
        if (suggestBar == null) return;
        for (int i = 0; i < suggestViews.length; i++) {
            TextView t = suggestViews[i];
            CharSequence text = i < found.length ? found[i] : "";
            if (!TextUtils.equals(t.getText(), text)) t.setText(text);
            t.setEnabled(i < found.length);
        }
        boolean showing = found.length > 0;
        if (showing != wasShowing) {
            suggestBar.setVisibility(showing ? View.VISIBLE : View.GONE);
            refreshToolbarVisibility();
        }
    }

    private void clearSuggestions() {
        if (main != null) main.removeCallbacks(suggestRunnable);
        suggestWord = "";
        if (suggestions.length == 0) return;
        suggestions = new String[0];
        if (suggestBar != null) {
            for (TextView t : suggestViews) t.setText("");
            suggestBar.setVisibility(View.GONE);
        }
        refreshToolbarVisibility();
    }

    /** 도구 막대는 추천이 떠 있거나 검색 중이거나 설정에서 껐으면 가린다. */
    private void refreshToolbarVisibility() {
        if (toolbar == null) return;
        boolean suggesting = suggestBar != null && suggestBar.getVisibility() == View.VISIBLE;
        boolean on = prefs.toolbar();
        toolbar.setVisibility(panel == PANEL_SEARCH || !(on || suggesting) ? View.GONE : View.VISIBLE);
        // 설정에서 '도구 막대 전체를 쓰기'를 켰으면 추천이 떠 있는 동안 왼쪽 버튼들도 사라진다.
        boolean left = on && !(suggesting && prefs.suggestFullBar());
        // 오른쪽 버튼들은 추천이 떠 있는 동안 사라져서 추천이 쓸 공간을 내준다.
        boolean right = on && !suggesting;
        String[][] order = toolOrder != null ? toolOrder : prefs.toolOrder();
        for (String id : order[0]) toolButtonOf(id).setVisibility(left && prefs.toolShown(id) ? View.VISIBLE : View.GONE);
        for (String id : order[1]) toolButtonOf(id).setVisibility(right && prefs.toolShown(id) ? View.VISIBLE : View.GONE);
        toolSpacer.setVisibility(suggesting ? View.GONE : View.VISIBLE);
    }

    private void applyTopSlotVisibility() {
        if (panel == PANEL_KEYBOARD && topSlot != null) topSlot.setVisibility(toolbarGone() ? View.GONE : View.VISIBLE);
        if (keyboard != null) updatePanelSizes();
    }

    /**
     * 추천 단어를 눌렀을 때: 입력 중이던 단어를 추천 단어로 바꾸고 띄어쓴다.
     * 추천 목록은 조금 늦게 바뀌므로, 지울 단어는 누른 순간 앱에서 직접 읽어 정한다.
     */
    private void applySuggestion(int index) {
        if (index >= suggestions.length) return;
        String replacement = suggestions[index];
        correctedTo = null;
        commitComposing();
        InputConnection ic = getCurrentInputConnection();
        if (ic == null) return;
        CharSequence before = ic.getTextBeforeCursor(32, 0);
        mirror.fill(before, 32);
        String word = before == null ? "" : WordSuggester.currentWord(before, korean);
        if (word.isEmpty()) {
            // 그사이 단어가 끝났거나 커서가 옮겨졌다. 엉뚱한 글자를 지우지 않도록 넣지 않는다.
            clearSuggestions();
            scheduleSuggest();
            return;
        }
        // 설정의 '추천 단어 뒤에 공백 포함'을 따른다 (한글·영어 모두).
        boolean withSpace = prefs.suggestSpace();
        String inserted = withSpace ? replacement + " " : replacement;
        ic.beginBatchEdit();
        ic.deleteSurroundingText(word.length(), 0);
        ic.commitText(inserted, 1);
        ic.endBatchEdit();
        mirror.replaceTail(word.length(), inserted);
        learnWord(replacement, false);
        lastWasSpace = withSpace;
        if (withSpace) lastSpaceTime = SystemClock.uptimeMillis();
        composer.reset();
        hadComposing = false;
        clearSuggestions();
        updateAutoCap();
    }

    /** 추천을 길게 누르면: 학습한 단어면 학습한 단어에서 지운다. */
    private boolean forgetSuggestion(int index) {
        if (index >= suggestions.length || !cfgLearn) return false;
        final String word = suggestions[index];
        final boolean lang = korean;
        if (!userWords.isKnownIfLoaded(word, lang)) {
            Toast.makeText(this, "기본 사전의 단어라 지울 수 없습니다", Toast.LENGTH_SHORT).show();
            return true;
        }
        feedback.onKey(null);
        Runnable delete = () -> runIo(() -> {
            userWords.remove(word, lang);
            main.post(() -> {
                Toast.makeText(this, "'" + word + "'" + WordSuggester.objectParticle(word) + " 삭제했습니다",
                        Toast.LENGTH_SHORT).show();
                updateSuggestions();
            });
        });
        if (prefs.confirmLearnedDelete()) confirmForget(word, delete);
        else delete.run();
        return true;
    }

    /** 추천 막대에서 학습한 단어를 지울지 묻는 창. 키보드 창에 붙여 띄운다. */
    private android.app.AlertDialog forgetDialog;

    private void confirmForget(String word, Runnable delete) {
        dismissForgetDialog();
        if (suggestBar == null || suggestBar.getWindowToken() == null) return;
        android.content.Context themed = new android.view.ContextThemeWrapper(this, theme.dark
                ? android.R.style.Theme_DeviceDefault_Dialog_Alert
                : android.R.style.Theme_DeviceDefault_Light_Dialog_Alert);
        android.widget.CheckBox dontAsk = new android.widget.CheckBox(themed);
        dontAsk.setText("다시 보지 않음");
        dontAsk.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        FrameLayout box = new FrameLayout(themed);
        int pad = Ui.dp(this, 20);
        box.setPadding(pad, Ui.dp(this, 4), pad, 0);
        box.addView(dontAsk);
        android.app.AlertDialog dialog = com.alternative_studios.newswipe.AppTheme.accentBuilder(themed)
                .setTitle("학습한 단어 삭제")
                .setMessage("'" + word + "'" + WordSuggester.objectParticle(word) + " 삭제하시겠습니까?")
                .setView(box)
                .setPositiveButton("삭제", (d, w) -> {
                    // 취소할 때는 체크해도 저장하지 않는다.
                    if (dontAsk.isChecked()) prefs.raw().edit().putBoolean(Prefs.CONFIRM_LEARNED_DELETE, false).apply();
                    delete.run();
                })
                .setNegativeButton("취소", null)
                .create();
        android.view.Window window = dialog.getWindow();
        if (window != null) {
            android.view.WindowManager.LayoutParams lp = window.getAttributes();
            lp.token = suggestBar.getWindowToken();
            lp.type = android.view.WindowManager.LayoutParams.TYPE_APPLICATION_ATTACHED_DIALOG;
            window.setAttributes(lp);
            window.addFlags(android.view.WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM);
        }
        dialog.setOnDismissListener(d -> {
            if (forgetDialog == d) forgetDialog = null;
        });
        forgetDialog = dialog;
        try {
            dialog.show();
        } catch (RuntimeException e) {
            forgetDialog = null;   // 키보드 창이 막 사라진 경우
        }
    }

    /** 키보드가 내려가거나 다시 만들어질 때 창도 닫는다 (남아서 키보드 화면을 붙잡지 않게). */
    private void dismissForgetDialog() {
        if (forgetDialog == null) return;
        try {
            if (forgetDialog.isShowing()) forgetDialog.dismiss();
        } catch (RuntimeException ignored) {
            // 키보드 창이 이미 사라진 경우
        }
        forgetDialog = null;
    }

    /**
     * 커서 앞에 방금 끝낸 단어를 사용자 사전에 기록한다.
     * before: 이미 앱에서 읽어 둔 커서 앞 글자 (없으면 null; 따라 적은 글자를 쓰고, 그것도 없으면 앱에 묻는다).
     */
    private void learnCurrentWord(CharSequence before) {
        if (!learningOn()) return;
        String word;
        if (before != null) {
            word = WordSuggester.currentWord(before, korean);
        } else {
            InputConnection ic = getCurrentInputConnection();
            if (ic == null) return;
            word = wordBeforeCursor(ic);
        }
        if (word.isEmpty()) return;
        // 자동 수정을 되돌려 이번에 그냥 두는 단어는 되돌릴 때만 센다 (두 번 되돌려야 학습되도록).
        syncIgnoredWithUserWords();
        if (cfgAutoCorrect && correctionIgnored.contains(word)) return;
        learnWord(word, false);
    }

    /** 단어 기록은 파일을 읽고 쓸 수 있어 백그라운드에서 한다. */
    /** 지금 입력란에서 단어를 학습해도 되는지 (IME_FLAG_NO_PERSONALIZED_LEARNING이 없을 때). */
    private boolean learnAllowed = true;

    private void learnWord(String word, boolean strong) {
        if (!cfgLearn || !suggestAllowed || !learnAllowed) return;
        final boolean lang = korean;
        runIo(() -> {
            suggester.learn(word, lang, strong);
            if (userWords.needsSave()) userWords.save();
        });
    }

    /**
     * 설정에서 학습한 단어를 지웠으면(하나든 모두든), 이번에 되돌려서 그냥 두던 단어 목록도 비운다.
     * 그러지 않으면 지운 단어를 키보드를 내렸다 올릴 때까지 계속 고치지 않는다.
     */
    private void syncIgnoredWithUserWords() {
        int v = userWords.editVersion();
        if (v != ignoredEditVersion) {
            ignoredEditVersion = v;
            correctionIgnored.clear();
        }
    }

    /** 스페이스를 눌렀을 때 입력한 단어를 고칠 수 있으면 고치고 띄어 쓴다. 고쳤으면 true. before = 앱에서 읽은 커서 앞 글자. */
    private boolean tryAutoCorrect(InputConnection ic, CharSequence before) {
        if (before == null) return false;
        String word = WordSuggester.currentWord(before, korean);
        // 읽은 글자 전체가 한 단어면 더 긴 글자 덩어리의 뒷부분일 수 있어 고치지 않는다.
        syncIgnoredWithUserWords();
        if (word.isEmpty() || word.length() == before.length() && before.length() == 32
                || correctionIgnored.contains(word)) {
            return false;
        }
        if (cfgLearn && !userWords.isLoaded()) {
            // 학습한 단어를 아직(또는 설정에서 고친 뒤 다시) 읽지 않았다. 내 단어를 잘못 고치지 않도록 이번에는 두고, 읽어 둔다.
            warmSuggester();
            return false;
        }
        String fixed = suggester.autoCorrect(word, korean);
        if (fixed == null) return false;
        ic.beginBatchEdit();
        ic.deleteSurroundingText(word.length(), 0);
        ic.commitText(fixed + " ", 1);
        ic.endBatchEdit();
        mirror.replaceTail(word.length(), fixed + " ");
        correctedFrom = word;
        correctedTo = fixed;
        lastWasSpace = false;
        clearSuggestions();
        updateAutoCap();
        return true;
    }

    /** 자동 수정 직후 지우기 키를 누르면 입력한 그대로 되돌리고, 같은 단어는 이번에 다시 고치지 않는다. */
    private boolean undoAutoCorrect() {
        if (correctedTo == null) return false;
        String from = correctedFrom, to = correctedTo;
        correctedTo = null;
        InputConnection ic = getCurrentInputConnection();
        if (ic == null) return false;
        CharSequence before = ic.getTextBeforeCursor(to.length() + 1, 0);
        if (before == null || !before.toString().equals(to + " ")) return false;
        ic.beginBatchEdit();
        ic.deleteSurroundingText(to.length() + 1, 0);
        ic.commitText(from, 1);
        ic.endBatchEdit();
        mirror.replaceTail(to.length() + 1, from);
        syncIgnoredWithUserWords();
        correctionIgnored.add(from);
        learnWord(from, false);   // 되돌릴 때마다 한 번씩 센다. 두 번 되돌리면 학습되어 더는 고치지 않는다.
        scheduleSuggest();
        return true;
    }

    /**
     * 입력란의 글자나 커서를 직접 바꾸는 동작 앞에서: 조합 중인 글자를 먼저 확정하고(그래야 앱이 한 번의 입력으로 알고
     * 되돌리거나 옮긴다) 자동 수정 되돌리기 같은 상태를 정리한다. 입력 연결이 없으면 false.
     */
    private boolean beginEdit() {
        if (getCurrentInputConnection() == null) return false;
        commitComposing();
        correctedTo = null;
        lastWasSpace = false;
        return true;
    }

    /** 글자나 커서가 바뀐 뒤: 따라 적어 둔 것은 버리고 추천을 새로 찾는다. */
    private void endEdit() {
        mirror.forget();
        clearSuggestions();
        scheduleSuggest();
    }

    /**
     * 실제 키보드처럼 Ctrl(+Shift)을 누른 채 키를 보낸다 (Ctrl 누름 → Shift 누름 → 키 누름·뗌 → 뗌).
     * 지원하는 건 앱의 입력란이라, 지원하지 않는 앱에서는 아무 일도 일어나지 않는다.
     */
    private void sendCtrlKey(int keyCode, boolean shift) {
        InputConnection ic = getCurrentInputConnection();
        if (ic == null) return;
        long t = SystemClock.uptimeMillis();
        int ctrl = KeyEvent.META_CTRL_ON | KeyEvent.META_CTRL_LEFT_ON;
        int meta = shift ? ctrl | KeyEvent.META_SHIFT_ON | KeyEvent.META_SHIFT_LEFT_ON : ctrl;
        ic.sendKeyEvent(new KeyEvent(t, t, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_CTRL_LEFT, 0, ctrl));
        if (shift) ic.sendKeyEvent(new KeyEvent(t, t, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_SHIFT_LEFT, 0, meta));
        ic.sendKeyEvent(new KeyEvent(t, t, KeyEvent.ACTION_DOWN, keyCode, 0, meta));
        ic.sendKeyEvent(new KeyEvent(t, t, KeyEvent.ACTION_UP, keyCode, 0, meta));
        if (shift) ic.sendKeyEvent(new KeyEvent(t, t, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_SHIFT_LEFT, 0, ctrl));
        ic.sendKeyEvent(new KeyEvent(t, t, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_CTRL_LEFT, 0, 0));
    }

    /** 입력란에 Ctrl+Z를 보낸다. */
    private void undo() {
        if (!beginEdit()) return;
        sendCtrlKey(KeyEvent.KEYCODE_Z, false);
        endEdit();
    }

    /** 입력란에 Ctrl+Shift+Z를 보낸다 (되돌린 것을 다시 실행). */
    private void redo() {
        if (!beginEdit()) return;
        sendCtrlKey(KeyEvent.KEYCODE_Z, true);
        endEdit();
    }

    private void moveCursorToLineEdge(boolean end) {
        sendDownUpKeyEvents(end ? KeyEvent.KEYCODE_MOVE_END : KeyEvent.KEYCODE_MOVE_HOME);
    }

    /** 이모지 버튼을 아래로 밀었을 때: 마지막으로 쓴 이모지를 입력한다. */
    private void insertLastEmoji() {
        List<String> recent = EmojiRepository.parseRecent(prefs.recentEmoji());
        if (recent.isEmpty()) {
            Toast.makeText(this, "최근 사용한 이모지가 없습니다", Toast.LENGTH_SHORT).show();
            return;
        }
        onEmoji(recent.get(0));
    }

    /** 이모지 창에서 맨 앞에 고정한 이모지(가장 먼저 고정한 것)를 입력한다. */
    private void insertFirstPinnedEmoji() {
        List<String> pinned = EmojiRepository.parseRecent(prefs.pinnedEmoji());
        if (pinned.isEmpty()) {
            Toast.makeText(this, "고정한 이모지가 없습니다", Toast.LENGTH_SHORT).show();
            return;
        }
        onEmoji(pinned.get(0));
    }

    /** 클립보드 창 맨 위에 보이는 고정 항목을 입력한다. */
    private void pasteFirstPinnedClip() {
        runIo(() -> {
            ClipboardHistory.Item pinned = clipHistory.firstPinned(System.currentTimeMillis());
            main.post(() -> {
                if (pinned != null && pinned.isImage()) pasteImage(pinned);
                else pasteClipOrNotify(pinned == null ? null : pinned.text, "고정한 클립보드 항목이 없습니다");
            });
        });
    }

    private void pasteClipOrNotify(String text, String emptyMessage) {
        if (text == null || text.isEmpty()) {
            Toast.makeText(this, emptyMessage, Toast.LENGTH_SHORT).show();
            return;
        }
        pasteText(text);
    }

    /** 도구 막대를 밀었을 때 정해 둔 기능을 실행한다 (기본: 왼쪽 = 줄의 맨 앞, 오른쪽 = 줄의 맨 끝, 위아래 = 한 줄 위·아래). */
    private void onToolbarSwipe(int dir) {
        if (panel != PANEL_KEYBOARD) return;
        String action = toolbarAction(dir);
        if (SwipeAction.NONE.equals(action)) return;
        feedback.onKey(null);
        onKeyFunction(null, action);
    }

    private void showImePicker() {
        InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (imm != null) imm.showInputMethodPicker();
    }

    /** 이모지 검색 중에 실제 입력란을 건드리지 않고 해도 되는 기능. */
    private static final List<String> SEARCH_SAFE = java.util.Arrays.asList(SwipeAction.NONE, SwipeAction.IME_PICKER,
            SwipeAction.LANGUAGE, SwipeAction.HIDE_KEYBOARD, SwipeAction.SETTINGS, SwipeAction.DELETE_WORD,
            SwipeAction.DELETE_LINE_START, SwipeAction.SPACE, SwipeAction.BACKSPACE,
            SwipeAction.EMOJI_OPEN, SwipeAction.CLIPBOARD_OPEN, SwipeAction.ONE_HAND_LEFT, SwipeAction.ONE_HAND_RIGHT);

    /** 밀어서 기능 완전 사용자화로 정해 둔 기능(SwipeAction ID)을 실행한다. */
    @Override
    public void onKeyFunction(Key key, String actionId) {
        if (actionId == null) return;
        if (panel == PANEL_SEARCH && !SEARCH_SAFE.contains(actionId)) return;
        switch (actionId) {
            case SwipeAction.CLIPBOARD_OPEN:
                showPanel(PANEL_CLIPBOARD);
                break;
            case SwipeAction.CLIPBOARD_PINNED:
                pasteFirstPinnedClip();
                break;
            case SwipeAction.EMOJI_OPEN:
                showPanel(PANEL_EMOJI);
                break;
            case SwipeAction.EMOJI_LATEST:
                insertLastEmoji();
                break;
            case SwipeAction.EMOJI_PINNED:
                insertFirstPinnedEmoji();
                break;
            case SwipeAction.IME_PICKER:
                showImePicker();
                break;
            case SwipeAction.LANGUAGE:
                toggleLanguage();
                break;
            case SwipeAction.SYMBOLS:
                toggleSymbols();
                break;
            case SwipeAction.ENTER:
                handleEnter();
                break;
            case SwipeAction.SPACE:
                handleSpace();
                break;
            case SwipeAction.LINE_START:
            case SwipeAction.LINE_END:
                if (beginEdit()) {
                    moveCursorToLineEdge(SwipeAction.LINE_END.equals(actionId));
                    endEdit();
                }
                break;
            case SwipeAction.TEXT_START:
            case SwipeAction.TEXT_END:
                if (beginEdit()) {
                    sendCtrlKey(SwipeAction.TEXT_END.equals(actionId)
                            ? KeyEvent.KEYCODE_MOVE_END : KeyEvent.KEYCODE_MOVE_HOME, false);
                    endEdit();
                }
                break;
            case SwipeAction.CURSOR_LEFT:
                onCursorMove(-1);
                break;
            case SwipeAction.CURSOR_RIGHT:
                onCursorMove(1);
                break;
            case SwipeAction.CURSOR_UP:
                onCursorMoveVertical(-1);   // 여러 줄 입력란에서만 움직인다
                break;
            case SwipeAction.CURSOR_DOWN:
                onCursorMoveVertical(1);
                break;
            case SwipeAction.WORD_LEFT:
            case SwipeAction.WORD_RIGHT:
                if (beginEdit()) {
                    moveCursorByWord(SwipeAction.WORD_RIGHT.equals(actionId));
                    endEdit();
                }
                break;
            case SwipeAction.BACKSPACE:
            case SwipeAction.DELETE_REPEAT:   // 길게 누르기 전용 (밀기 등 한 번 실행하는 곳에서는 한 글자 지우기)
                handleDelete();
                break;
            case SwipeAction.CAPS_LOCK:
                if (layoutKind == KeyboardLayout.ENGLISH) setShift(2);
                break;
            case SwipeAction.DELETE_WORD:
                onDeleteWord();
                break;
            case SwipeAction.DELETE_LINE_START:
                onDeleteToLineStart();
                break;
            case SwipeAction.DELETE_LINE:
                if (beginEdit()) {
                    deleteLine();
                    endEdit();
                }
                break;
            case SwipeAction.UNDO:
                undo();
                break;
            case SwipeAction.REDO:
                redo();
                break;
            case SwipeAction.SELECT_ALL:
                contextAction(android.R.id.selectAll);
                break;
            case SwipeAction.COPY:
                copySelection();
                break;
            case SwipeAction.CUT:
                contextAction(android.R.id.cut);
                break;
            case SwipeAction.PASTE:
                contextAction(android.R.id.paste);
                break;
            case SwipeAction.VOICE:
                startVoiceInput();
                break;
            case SwipeAction.HIDE_KEYBOARD:
                requestHideSelf(0);
                break;
            case SwipeAction.SETTINGS:
                openSettings();
                break;
            case SwipeAction.ONE_HAND_LEFT:
                toggleOneHand(Prefs.ONE_HAND_LEFT);
                break;
            case SwipeAction.ONE_HAND_RIGHT:
                toggleOneHand(Prefs.ONE_HAND_RIGHT);
                break;
            default:
                break;
        }
    }

    /**
     * 커서를 단어 단위로 옮긴다. 앱마다 Ctrl+←/→ 지원이 달라서(안드로이드 기본 입력란은 잘 따르지 않는다)
     * 단어 경계를 직접 찾아 커서를 놓는다. 커서 위치는 입력란이 알려 준 선택 영역을 쓰고,
     * 그것을 모르거나 입력란의 글을 읽을 수 없는 앱에서만 Ctrl+←/→를 보낸다.
     */
    private void moveCursorByWord(boolean right) {
        InputConnection ic = getCurrentInputConnection();
        if (ic == null) return;
        CharSequence around = right ? ic.getTextAfterCursor(256, 0) : ic.getTextBeforeCursor(256, 0);
        if (around == null || selStart < 0 || selEnd < 0) {
            sendCtrlKey(right ? KeyEvent.KEYCODE_DPAD_RIGHT : KeyEvent.KEYCODE_DPAD_LEFT, false);
            return;
        }
        // 선택 영역이 있으면 옮기는 쪽 끝에서 시작한다.
        int start = Math.min(selStart, selEnd), end = Math.max(selStart, selEnd);
        int n = com.alternative_studios.newswipe.suggest.WordBoundary.distance(around, right);
        int pos = right ? end + n : Math.max(0, start - n);
        ic.setSelection(pos, pos);
        // 입력란이 바뀐 위치를 알려 주기 전에 다시 밀어도 이어서 움직이도록 바로 기억해 둔다.
        selStart = selEnd = pos;
    }

    /** 입력란의 메뉴 동작(모두 선택, 복사, 잘라내기, 붙여넣기)을 실행한다. */
    private void contextAction(int id) {
        if (!beginEdit()) return;
        getCurrentInputConnection().performContextMenuAction(id);
        endEdit();
    }

    /** 선택한 글자를 복사하고, 복사했는지(선택한 글자가 없었는지)를 키보드 위에 알린다. */
    private void copySelection() {
        if (!beginEdit()) return;
        InputConnection ic = getCurrentInputConnection();
        CharSequence selected = ic.getSelectedText(0);   // 복사하면 앱이 선택을 풀 수 있어 먼저 읽는다
        ic.performContextMenuAction(android.R.id.copy);
        endEdit();
        showMessage(selected != null && selected.length() > 0 ? "복사했습니다" : "선택된 내용이 없습니다");
    }

    /** 안내 문구를 보여 주는 칸 (키보드 위쪽 가운데). */
    private TextView messageView;
    private final Runnable hideMessage = () -> {
        if (messageView != null) messageView.animate().alpha(0f).setDuration(150)
                .withEndAction(() -> { if (messageView != null) messageView.setVisibility(View.GONE); }).start();
    };

    /**
     * 키보드 위에 짧은 안내 문구를 잠깐 보여 준다. 시스템 토스트는 다른 앱 위에서 보이지 않는 경우가 있어
     * (알림·토스트 설정, 기기 정책) 키보드 창 안에 직접 그린다.
     */
    private void showMessage(String text) {
        if (content == null) return;
        if (messageView == null || messageView.getParent() != content) {
            TextView v = new TextView(this);
            v.setTextColor(0xFFFFFFFF);
            v.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
            v.setGravity(Gravity.CENTER);
            int h = Ui.dp(this, 16), vv = Ui.dp(this, 9);
            v.setPadding(h, vv, h, vv);
            android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
            bg.setColor(0xE6202124);
            bg.setCornerRadius(Ui.dp(this, 20));
            v.setBackground(bg);
            v.setVisibility(View.GONE);
            FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP | Gravity.CENTER_HORIZONTAL);
            lp.topMargin = Ui.dp(this, 10);
            content.addView(v, lp);
            messageView = v;
        }
        main.removeCallbacks(hideMessage);
        messageView.animate().cancel();
        messageView.setText(text);
        messageView.setAlpha(1f);
        messageView.setVisibility(View.VISIBLE);
        messageView.bringToFront();
        main.postDelayed(hideMessage, 1200);
    }

    /**
     * 커서가 있는 줄의 글자를 모두 지운다 (줄바꿈은 남겨 빈 줄이 된다).
     * 이미 빈 줄이면 지우기 키를 한 번 누른 것처럼 윗줄과 합친다.
     */
    private void deleteLine() {
        InputConnection ic = getCurrentInputConnection();
        if (ic == null) return;
        int before = charsToLineEdge(ic, true), after = charsToLineEdge(ic, false);
        if (before + after > 0) ic.deleteSurroundingText(before, after);
        else if (joinWithPreviousLine(ic)) mirror.forget();
    }

    /**
     * 커서가 줄의 맨 앞(바로 앞 글자가 줄바꿈)이면 그 줄바꿈을 지워 커서 뒤의 내용을 윗줄 끝에 붙이고 true를 돌려준다.
     * 글의 맨 앞이거나 줄 중간이면 아무것도 하지 않고 false.
     */
    private static boolean joinWithPreviousLine(InputConnection ic) {
        CharSequence prev = ic.getTextBeforeCursor(1, 0);
        if (prev == null || prev.length() != 1 || prev.charAt(0) != '\n') return false;
        ic.deleteSurroundingText(1, 0);
        return true;
    }

    /**
     * 커서에서 같은 줄의 처음(backward)이나 끝까지의 글자 수. 앱에 한꺼번에 많이 묻지 않도록 조금씩 늘려 가며 읽는다.
     * 줄이 너무 길면(10만 자) 거기까지만 센다.
     */
    private static int charsToLineEdge(InputConnection ic, boolean backward) {
        int want = 256;
        while (true) {
            CharSequence s = backward ? ic.getTextBeforeCursor(want, 0) : ic.getTextAfterCursor(want, 0);
            if (s == null) return 0;
            int len = s.length();
            if (backward) {
                int i = len;
                while (i > 0 && s.charAt(i - 1) != '\n') i--;
                if (i > 0) return len - i;
            } else {
                int i = 0;
                while (i < len && s.charAt(i) != '\n') i++;
                if (i < len) return i;
            }
            if (len < want || want >= 100_000) return len;   // 글의 처음·끝까지 왔다
            want *= 4;
        }
    }

    private void startVoiceInput() {
        InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (imm != null) {
            for (InputMethodInfo imi : imm.getEnabledInputMethodList()) {
                if (imi.getPackageName().equals(getPackageName())) continue;
                for (InputMethodSubtype st : imm.getEnabledInputMethodSubtypeList(imi, true)) {
                    if ("voice".equals(st.getMode())) {
                        commitComposing();
                        switchInputMethod(imi.getId(), st);
                        return;
                    }
                }
            }
        }
        Toast.makeText(this, "사용 중인 음성 입력이 없습니다. 기기 설정에서 'Google 음성 입력' 등을 켜 주세요.",
                Toast.LENGTH_LONG).show();
    }

    private void openSettings() {
        // 이미 열린 설정 화면들을 모두 닫고 첫 화면 하나만 새로 연다. CLEAR_TOP만 쓰면 맨 위의 설정 화면(하위 화면일 수 있다)
        // 자리에 첫 화면이 새로 생겨, 뒤로 가기를 눌러도 아래에 남은 첫 화면이 또 보였다.
        Intent i = new Intent(this, SettingsActivity.class);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(i);
    }

    // ---------------------------------------------------------------- 이모지

    @Override
    public void onEmoji(String emoji) {
        InputConnection ic = getCurrentInputConnection();
        if (ic != null) {
            if (panel == PANEL_SEARCH) {
                ic.commitText(emoji, 1);   // 검색창이 아니라 실제 입력란에 넣는다.
            } else {
                commitComposing();
                ic.commitText(emoji, 1);
            }
            mirror.commit(emoji);
        }
        String recent = EmojiRepository.pushRecent(prefs.recentEmoji(), emoji);
        prefs.setRecentEmoji(recent);   // 이모지 패널에는 설정 변경 리스너가 반영한다
    }

    @Override
    public void onEmojiDelete() {
        deleteBeforeCursor(false);
    }

    @Override
    public void onEmojiBack() {
        showPanel(PANEL_KEYBOARD);
    }

    @Override
    public void onEmojiSearch() {
        composer.reset();
        search.setLength(0);
        searchComposing = "";
        showPanel(PANEL_SEARCH);
    }

    @Override
    public void onEmojiKeyPress() {
        feedback.onKey(null);
    }

    @Override
    public void onEmojiPinToggle(String emoji) {
        prefs.setPinnedEmoji(EmojiRepository.togglePinned(prefs.pinnedEmoji(), emoji));   // 패널에는 리스너가 반영한다
    }

    private boolean searchWaiting;
    /**
     * 검색을 맡길 때마다 올리는 번호 (화면 스레드에서만 올린다). 늦게 끝난 이전 검색의 결과가
     * 새 검색어의 결과를 덮어쓰지 않도록, 결과를 보여 줄 때 이 번호가 그대로인지 확인한다.
     */
    private volatile int searchSeq;

    private void updateSearch() {
        if (searchText == null) return;
        String q = search + searchComposing;
        if (q.isEmpty()) {
            searchText.setText(korean ? "이모지 검색 (한글)" : "Search emoji (English)");
            searchText.setTextColor(theme.hint);
        } else {
            searchText.setText(q);
            searchText.setTextColor(theme.text);
        }
        final int seq = ++searchSeq;   // 아직 끝나지 않은 이전 검색의 결과는 버린다
        EmojiData data = emojiRepo.peek();
        if (data == null) {
            results.removeAllViews();
            // 데이터를 읽는 동안 글자를 칠 때마다 콜백이 쌓이지 않도록 한 번만 기다린다.
            if (!searchWaiting) {
                searchWaiting = true;
                emojiRepo.load(d -> {
                    searchWaiting = false;
                    updateSearch();
                });
            }
            return;
        }
        if (q.trim().isEmpty()) {
            showSearchResults(Collections.emptyList(), q);
            return;
        }
        // 이모지 2천여 개의 키워드를 훑는 일이라 화면 스레드에서 하지 않는다. 결과가 올 때까지는 이전 결과를 그대로 둔다.
        try {
            searchIo.execute(() -> {
                if (seq != searchSeq) return;   // 그사이 글자를 더 쳤다
                List<EmojiData.Emoji> found = data.search(q, 40);
                main.post(() -> {
                    if (seq == searchSeq && panel == PANEL_SEARCH) showSearchResults(found, q);
                });
            });
        } catch (java.util.concurrent.RejectedExecutionException ignored) {
            // 서비스가 끝나는 중
        }
    }

    /** 검색 결과(없으면 고정·최근 이모지나 안내)를 결과 줄에 보여 준다. */
    private void showSearchResults(List<EmojiData.Emoji> found, String q) {
        results.removeAllViews();
        if (found.isEmpty()) {
            List<String> recent = EmojiRepository.withPinned(EmojiRepository.parseRecent(prefs.pinnedEmoji()),
                    EmojiRepository.parseRecent(prefs.recentEmoji()));
            if (q.trim().isEmpty() && !recent.isEmpty()) {
                for (int i = 0; i < recent.size(); i++) results.addView(resultView(i, recent.get(i)));
            } else {
                if (resultNote == null) {
                    resultNote = new TextView(this);
                    resultNote.setTextColor(theme.hint);
                    resultNote.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
                    resultNote.setPadding(Ui.dp(this, 10), 0, 0, 0);
                }
                resultNote.setText(q.trim().isEmpty() ? "검색어를 입력하세요" : "검색 결과가 없습니다");
                results.addView(resultNote);
            }
        } else {
            for (int i = 0; i < found.size(); i++) results.addView(resultView(i, found.get(i).value));
        }
        resultScroll.scrollTo(0, 0);
    }

    /** i번째 결과 칸에 emoji를 넣어 돌려준다. 모자라면 그때 만든다. */
    private View resultView(int i, String emoji) {
        while (resultViews.size() <= i) {
            TextView t = new TextView(this);
            t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 26);
            t.setGravity(Gravity.CENTER);
            t.setBackground(Ui.ripple(theme.keyPressed, null, Ui.dp(this, 10)));
            t.setOnClickListener(v -> {
                feedback.onKey(null);
                onEmoji(((TextView) v).getText().toString());
            });
            t.setLayoutParams(new LinearLayout.LayoutParams(Ui.dp(this, 46), Ui.dp(this, 46)));
            resultViews.add(t);
        }
        TextView t = resultViews.get(i);
        t.setText(emoji);
        return t;
    }

    // ---------------------------------------------------------------- 클립보드

    @Override
    public void onPrimaryClipChanged() {
        if (!prefs.clipboardHistory() || clipboard == null) return;
        ClipData clip;
        try {
            clip = clipboard.getPrimaryClip();
        } catch (RuntimeException e) {
            return;
        }
        if (clip == null || clip.getItemCount() == 0) return;
        ClipDescription desc = clip.getDescription();
        if (desc != null) {
            PersistableBundle extras = desc.getExtras();
            // 비밀번호 관리자 등이 민감 정보로 표시한 복사는 저장하지 않는다.
            if (extras != null && extras.getBoolean(SENSITIVE_EXTRA, false)) return;
        }
        ClipData.Item first = clip.getItemAt(0);
        String imageMime = imageMime(desc, first.getUri());
        if (imageMime != null) {
            if (prefs.clipboardImages()) saveClipImage(first.getUri(), imageMime);
            return;
        }
        CharSequence text = first.getText();
        if (text == null) return;
        // 아주 긴 텍스트를 통째로 문자열로 만들지 않고, 저장할 길이만큼만 잘라 온다.
        final String s = text.length() > ClipboardHistory.MAX_LENGTH
                ? text.subSequence(0, ClipboardHistory.MAX_LENGTH).toString() : text.toString();
        final long now = System.currentTimeMillis();
        runIo(() -> {
            clipHistory.add(s, now);
            main.post(() -> {
                if (panel == PANEL_CLIPBOARD) refreshClipboard();
            });
        });
    }

    /** 복사한 것이 이미지면 그 형식(image/...), 아니면 null. */
    private String imageMime(ClipDescription desc, Uri uri) {
        if (uri == null || !"content".equals(uri.getScheme())) return null;
        if (desc != null) {
            for (int i = 0; i < desc.getMimeTypeCount(); i++) {
                String m = ClipboardHistory.cleanMime(desc.getMimeType(i));
                if (m != null && !m.equals("image/*")) return m;
            }
        }
        try {
            return ClipboardHistory.cleanMime(getContentResolver().getType(uri));
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** 이미지 원본을 그대로 복사하고 미리보기를 만들어 기록에 넣는다 (이 키보드가 클립보드에 넣은 이미지면 앞으로 옮기기만 한다). */
    private void saveClipImage(Uri uri, String mime) {
        final long now = System.currentTimeMillis();
        final String own = ClipImageProvider.idOf(this, uri);
        runIo(() -> {
            String id = own != null ? own
                    : ClipImages.save(getContentResolver(), uri, clipHistory.imageDir(), getCacheDir());
            if (id == null) return;
            clipHistory.addImage(id, mime, now);
            main.post(() -> {
                if (panel == PANEL_CLIPBOARD) refreshClipboard();
            });
        });
    }

    private void refreshClipboard() {
        if (clipPanel == null) return;
        final boolean enabled = prefs.clipboardHistory();
        final String current = currentClipText();
        if (!enabled) {
            clipPanel.show(Collections.emptyList(), false, current);
            return;
        }
        runIo(() -> {
            List<ClipboardHistory.Item> items = clipHistory.items(System.currentTimeMillis());
            main.post(() -> {
                if (clipPanel != null) clipPanel.show(items, true, current);
            });
        });
    }

    private String currentClipText() {
        if (clipboard == null) return null;
        try {
            ClipData clip = clipboard.getPrimaryClip();
            if (clip == null || clip.getItemCount() == 0) return null;
            CharSequence t = clip.getItemAt(0).getText();
            if (t == null) return null;
            return t.length() > ClipboardHistory.MAX_LENGTH
                    ? t.subSequence(0, ClipboardHistory.MAX_LENGTH).toString() : t.toString();
        } catch (RuntimeException e) {
            return null;
        }
    }

    @Override
    public void onClipPaste(String text) {
        feedback.onKey(null);
        pasteText(text);
    }

    @Override
    public void onClipPasteImage(ClipboardHistory.Item item) {
        feedback.onKey(null);
        pasteImage(item);
    }

    @Override
    public void onClipThumbnail(ClipboardHistory.Item item, java.util.function.Consumer<Bitmap> done) {
        File f = ClipboardHistory.thumbnail(clipHistory.imageDir(), item.text);
        runIo(() -> {
            Bitmap b = BitmapFactory.decodeFile(f.getPath());
            main.post(() -> done.accept(b));
        });
    }

    /**
     * 이미지를 붙여 넣는다. 입력란이 이미지를 받으면 바로 넣고, 받지 않으면 시스템 클립보드에 담아
     * 앱의 붙여넣기 메뉴로 넣을 수 있게 한다.
     */
    private void pasteImage(ClipboardHistory.Item item) {
        if (!ClipboardHistory.original(clipHistory.imageDir(), item.text).isFile()) {
            Toast.makeText(this, "이미지 파일을 찾을 수 없습니다", Toast.LENGTH_SHORT).show();
            refreshClipboard();
            return;
        }
        Uri uri = ClipImageProvider.uri(this, item.text, item.mime);
        InputConnection ic = getCurrentInputConnection();
        if (ic != null && acceptsContent(getCurrentInputEditorInfo(), item.mime)) {
            commitComposing();
            InputContentInfo info = new InputContentInfo(uri, new ClipDescription("이미지", new String[]{item.mime}));
            if (ic.commitContent(info, InputConnection.INPUT_CONTENT_GRANT_READ_URI_PERMISSION, null)) return;
        }
        if (clipboard != null) {
            try {
                clipboard.setPrimaryClip(ClipData.newUri(getContentResolver(), "이미지", uri));
                Toast.makeText(this, "이 입력란은 이미지를 바로 받지 않아 클립보드에 담았습니다. 앱의 붙여넣기 메뉴로 넣어 보세요.",
                        Toast.LENGTH_LONG).show();
                return;
            } catch (RuntimeException ignored) {
                // 클립보드에 넣지 못하면 아래 안내
            }
        }
        Toast.makeText(this, "이 입력란에는 이미지를 붙여 넣을 수 없습니다", Toast.LENGTH_SHORT).show();
    }

    private static boolean acceptsContent(EditorInfo info, String mime) {
        if (info == null || info.contentMimeTypes == null) return false;
        for (String accepted : info.contentMimeTypes) {
            if (ClipDescription.compareMimeTypes(mime, accepted)) return true;
        }
        return false;
    }

    /** 텍스트를 커서 자리에 붙여 넣는다 (키를 누르는 소리·진동은 부르는 쪽이 정한다). */
    private void pasteText(String text) {
        InputConnection ic = getCurrentInputConnection();
        if (ic != null) {
            commitComposing();
            ic.commitText(text, 1);
            mirror.commit(text);
        }
    }

    @Override
    public void onClipBack() {
        showPanel(PANEL_KEYBOARD);
    }

    @Override
    public void onClipPin(ClipboardHistory.Item item, boolean pinned) {
        runIo(() -> {
            boolean ok = clipHistory.setPinned(item, pinned);
            main.post(() -> {
                if (!ok) {
                    Toast.makeText(this, "클립보드 항목은 " + ClipboardHistory.MAX_PINNED + "개까지 고정할 수 있습니다",
                            Toast.LENGTH_SHORT).show();
                }
                refreshClipboard();
            });
        });
    }

    @Override
    public void onClipDelete(ClipboardHistory.Item item) {
        runIo(() -> {
            clipHistory.remove(item);
            main.post(this::refreshClipboard);
        });
    }

    @Override
    public void onClipClear() {
        runIo(() -> {
            clipHistory.clearUnpinned();
            main.post(this::refreshClipboard);
        });
    }
}
