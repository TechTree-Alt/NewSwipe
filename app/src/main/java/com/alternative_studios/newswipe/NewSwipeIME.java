package com.alternative_studios.newswipe;

import android.content.ClipData;
import android.content.ClipDescription;
import android.content.ClipboardManager;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Typeface;
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
import android.view.inputmethod.InlineSuggestionsRequest;
import android.view.inputmethod.InlineSuggestionsResponse;
import android.view.inputmethod.InputConnection;
import android.view.inputmethod.InputContentInfo;
import android.view.inputmethod.InputMethodInfo;
import android.view.inputmethod.InputMethodManager;
import android.view.inputmethod.InputMethodSubtype;
import android.view.inputmethod.SurroundingText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.alternative_studios.newswipe.clipboard.ClipImageProvider;
import com.alternative_studios.newswipe.clipboard.ClipImages;
import com.alternative_studios.newswipe.clipboard.ClipboardHistory;
import com.alternative_studios.newswipe.clipboard.ClipboardPanel;
import com.alternative_studios.newswipe.cjk.Candidate;
import com.alternative_studios.newswipe.cjk.CjkComposer;
import com.alternative_studios.newswipe.cjk.CjkPack;
import com.alternative_studios.newswipe.emoji.EmojiData;
import com.alternative_studios.newswipe.emoji.EmojiPanel;
import com.alternative_studios.newswipe.emoji.EmojiRepository;
import com.alternative_studios.newswipe.hangul.Dubeolsik;
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
import com.alternative_studios.newswipe.ui.CandidateView;
import com.alternative_studios.newswipe.ui.ClipChip;
import com.alternative_studios.newswipe.ui.EditChip;
import com.alternative_studios.newswipe.ui.IconButton;
import com.alternative_studios.newswipe.ui.Neu;
import com.alternative_studios.newswipe.ui.SwipeVertical;
import com.alternative_studios.newswipe.ui.SwipeStrip;
import com.alternative_studios.newswipe.suggest.Shortcuts;
import com.alternative_studios.newswipe.suggest.TextMirror;
import com.alternative_studios.newswipe.suggest.UsageHistory;
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
    /** 자동 완성 제안 칩 (Android 11 이상에서만 만든다). */
    private InlineAutofill autofill;
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
     * 한 손 모드가 꺼져 있으면 column만 꽉 차게 들어가고 sideStrip은 숨긴다. liftSpace는 가로 모드·대화면의
     * '세로 위치'를 정했을 때만 보인다.
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
    /** 이모지 검색줄의 뒤로 가기 칩과 검색창 (도구 막대 높이에 맞춰 sizeToolbar()가 크기를 정한다). */
    private IconButton searchBack;
    private LinearLayout searchPill;
    private LinearLayout suggestBar;
    /** 글자를 선택했을 때 뜨는 편집 칩 줄 (도구 막대 안, 추천 단어 줄 자리). */
    private FrameLayout editBar;
    /** 자동 완성 칩 줄 (도구 막대 안, 추천 단어 줄 자리). */
    private View autofillBar;
    private View toolSpacer;
    /** 방금 복사한 내용을 붙여 넣는 칩과 그 자리 (도구 막대 안, 왼쪽·오른쪽 도구 버튼 사이의 빈 공간). */
    private ClipChip.Slot clipChipSlot;
    private ClipChip clipChip;
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
    /** 실험실의 일본어("ja")·중국어("zh") 자판을 쓰는 중이면 그 언어. 한글·영어면 null (korean이 둘을 가른다). */
    private String cjkLang;
    /** 일본어·중국어 조합기 (사전을 읽은 뒤에 생긴다). 언어마다 하나씩 두고 다시 쓴다. */
    private final java.util.Map<String, CjkComposer> cjkComposers = new java.util.HashMap<>();
    /** 사전을 읽는 중인 언어. */
    private final java.util.Set<String> cjkLoading = new java.util.HashSet<>();
    /** 사전을 읽을 때의 {@link CjkPack#changes()}. 팩을 바꾸거나 지우면 다시 읽는다. */
    private int cjkPackChanges = -1;
    /** 실험실에서 켰고 사전 팩도 있는 언어 (applySettings에서 정한다). */
    private boolean cfgJa, cfgZh;
    /** 이메일 입력란 등에서 영어로 바꾸기 전에 쓰던 일본어·중국어 (그 칸을 떠나면 돌아간다). */
    private String autoEnglishFrom;
    private CandidateView candidateBar, candidateGrid;
    private boolean candidateGridOpen;
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
    /** 한국어 단어와 이어 쓴 단어의 기록 (추천 순서를 내 말버릇에 맞춘다). */
    private UsageHistory usageHistory;
    private String[] suggestions = new String[0];
    /** 지금 추천란의 단어가 앞 단어만 보고 예측한 다음 단어인지 (누르면 지우지 않고 커서 자리에 넣는다). */
    private boolean predictionShown;
    /** 마지막으로 보인 다음 단어 예측과 그 앞 단어: 다음 단어를 학습할 때 고르지 않은 예측인지 본다. */
    private String predictedAfter;
    private String[] predictedWords;
    /** 스페이스를 누르면 자동 수정될 단어가 있는 추천 칸 (굵게 보인다). 없으면 -1. */
    private int correctionShown = -1;
    /** 지금 입력란의 종류 (WordSuggester.FIELD_*: 검색·보통). 한국어 추천 순위에 쓴다. */
    private int fieldKind = WordSuggester.FIELD_NORMAL;
    /** 지금 추천란에 보이는 것이 단축어의 문장인지 (추천란 전체를 이 문장 하나가 채운다). */
    private boolean shortcutShown;
    private Shortcuts shortcuts = Shortcuts.of(null);
    private boolean cfgShortcuts;
    private String correctedFrom, correctedTo;   // 방금 자동 수정한 단어 (지우기 키로 되돌리기 위해)
    private String correctedSuffix = " ";        // 자동 수정하며 뒤에 넣은 글자 (스페이스나 문장 부호)
    private final java.util.HashSet<String> correctionIgnored = new java.util.HashSet<>();
    /** correctionIgnored를 채울 때의 학습한 단어 편집 번호. 설정에서 단어를 지우면 바뀌어 비운다. */
    private int ignoredEditVersion;
    private String suggestWord = "";
    private boolean domainField;   // 이메일 주소·인터넷 주소 입력란: 온점 키를 길게 누르면 .com 등이 나온다
    private boolean passwordField;   // 비밀번호 입력란: 복사한 내용 칩의 미리보기를 가린다
    /** 이메일 입력란 등에서 영어로 시작했다 (설정에는 저장하지 않는다). 그런 칸이 아닌 곳으로 가면 한글로 돌아간다. */
    private boolean autoEnglish;
    /**
     * 영어로 시작한 칸에서 사용자가 직접 언어를 바꿨으면 그 칸 (앱 패키지와 칸 ID). 같은 칸을 다시 시작하거나
     * 키보드를 내렸다 다시 올려도 다시 영어로 바꾸지 않는다. 다른 칸으로 가면 잊는다.
     */
    private String autoEnglishDeclined;
    /**
     * 입력란이 새로 연결된 뒤(onStartInput) 아직 선택 영역이 바뀌지 않았다: EditorInfo에 담겨 온 커서 앞 글자를
     * 믿고 쓸 수 있다 (키보드를 내렸다 다시 올리면 같은 EditorInfo가 다시 오므로 그때는 쓰지 않는다).
     */
    private boolean initialTextFresh;
    /**
     * 방금 복사한 텍스트를 붙여 넣는 칩을 보여 줄 차례인지. 복사한 뒤 CLIP_CHIP_MS 동안 유지되고(글자를 입력해도),
     * 붙여 넣거나 시간이 지나면 사라진다. 추천 단어·자동 완성 칩이 떠 있는 동안에는 그 자리를 양보했다가 다시 뜬다.
     */
    private boolean clipChipActive;
    /** 칩의 미리보기 (복사한 글의 앞부분 한 줄). 민감한 정보로 표시된 복사면 null이고 내용은 기억하지 않는다. */
    private String clipChipPreview;
    private static final long CLIP_CHIP_MS = 60_000;
    private final Runnable expireClipChip = this::dismissClipChip;
    private boolean shortcutAllowed;   // 현재 입력란에서 단축어를 쓸 수 있는지 (비밀번호만 제외, 이메일 입력란은 허용)
    private boolean suggestAllowed;   // 현재 입력란에서 추천을 쓸 수 있는지 (비밀번호·이메일 등은 제외, 인터넷 주소 입력란은 허용)
    /** 설정값 (applySettings에서 읽어 둔다. 키마다 설정 파일을 읽지 않기 위해). */
    private boolean cfgSuggest, cfgAutoCorrect, cfgLearn, cfgPredict;
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
        inputManager = (android.hardware.input.InputManager) getSystemService(INPUT_SERVICE);
        if (inputManager != null) inputManager.registerInputDeviceListener(inputDeviceListener, main);
        hwKeyboardConnected = findHwKeyboard();
        if (Build.VERSION.SDK_INT >= 30) {
            autofill = new InlineAutofill(this, new InlineAutofill.Host() {
                @Override
                public void onAutofillBarChanged() {
                    onAutofillChipsChanged();
                }

                @Override
                public void onAutofillChipClicked() {
                    feedback.onToolbar();
                }
            });
        }
        String lang = prefs.language();
        refreshCjkAvailability();
        cjkLang = cjkAvailable(lang) ? lang : null;
        korean = cjkLang == null && !"en".equals(lang);
        emojiRepo = new EmojiRepository(this, io);
        userWords = UserDictionary.shared(getFilesDir());
        usageHistory = UsageHistory.shared(getFilesDir());
        suggester = new WordSuggester(this, userWords, usageHistory);
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
                if (key == null || Prefs.HW_TOOLBAR.equals(key)) {
                    updateInputViewShown();
                    refreshHwToolbar();
                }
                // key가 null이면 설정 전체가 지워진 경우(설정 가져오기 등)라 반영한다.
                if (keyboard == null || (key != null && !affectsKeyboard(key))) return;
                main.removeCallbacks(applyPrefsRunnable);
                main.postDelayed(applyPrefsRunnable, 50);
            };

    /** 사용할 때마다 설정에서 직접 읽는 값이나 키보드 화면과 상관없는 값은 다시 그릴 필요가 없다. */
    private static boolean affectsKeyboard(String key) {
        return !(Prefs.LANGUAGE.equals(key) || Prefs.RECENT_EMOJI.equals(key) || Prefs.PINNED_EMOJI.equals(key)
                || Prefs.CLIPBOARD_HISTORY.equals(key) || Prefs.CLIPBOARD_IMAGES.equals(key) || Prefs.CLIPBOARD_SENSITIVE.equals(key) || Prefs.CLIPBOARD_CHIP.equals(key) || Prefs.AUTO_CAP.equals(key)
                || Prefs.DOUBLE_SPACE_PERIOD.equals(key) || Prefs.CONFIRM_LEARNED_DELETE.equals(key)
                || Prefs.ONE_HAND_LAST.equals(key) || Prefs.ENGLISH_FIELDS.equals(key)
                || key.startsWith(Prefs.HW_PREFIX));   // 외장 키보드 설정은 키를 누를 때 읽는다
    }

    @Override
    public void onDestroy() {
        if (clipboard != null) clipboard.removePrimaryClipChangedListener(this);
        if (inputManager != null) inputManager.unregisterInputDeviceListener(inputDeviceListener);
        prefs.raw().unregisterOnSharedPreferenceChangeListener(prefListener);
        // 백그라운드 작업이 보내 둔 화면 갱신도 모두 버린다 (끝난 서비스를 붙잡거나, 닫힌 작업 줄에 다시 일을 맡기지 않게).
        main.removeCallbacksAndMessages(null);
        dismissForgetDialog();
        // 이미 맡긴 단어 기록이 끝난 뒤에 저장하고 내리도록 같은 작업 줄에 넣는다 (shutdown은 남은 작업을 마저 한다).
        runIo(this::releaseLearned);
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
                releaseCjk();
                suggester.release();
                warmingKo = warmingEn = false;
                runIo(this::releaseLearned);
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
        releaseCjk();
        runIo(this::releaseLearned);
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

    /** 외장 키보드를 연결하면 시스템이 자판을 감춘다. 실험실 '도구 막대 보이기'를 켰으면 도구 막대만 보인다. */
    @Override
    public boolean onEvaluateInputViewShown() {
        return super.onEvaluateInputViewShown() || (prefs != null && prefs.hwToolbar() && hwKeyboardConnected);
    }

    /** 외장 키보드가 연결되어 있는지. 장치가 붙고 빠질 때마다 다시 센다 (시스템 설정 값이 바뀌기를 기다리지 않는다). */
    private boolean hwKeyboardConnected;

    private android.hardware.input.InputManager inputManager;

    private final android.hardware.input.InputManager.InputDeviceListener inputDeviceListener =
            new android.hardware.input.InputManager.InputDeviceListener() {
                @Override
                public void onInputDeviceAdded(int deviceId) {
                    onInputDevicesChanged();
                }

                @Override
                public void onInputDeviceRemoved(int deviceId) {
                    onInputDevicesChanged();
                }

                @Override
                public void onInputDeviceChanged(int deviceId) {
                    onInputDevicesChanged();
                }
            };

    /** 외장 키보드를 연결하거나 빼면 곧바로 도구 막대만 보이기와 자판 전체 보이기를 바꾼다. */
    private void onInputDevicesChanged() {
        boolean connected = findHwKeyboard();
        if (connected == hwKeyboardConnected) return;
        hwKeyboardConnected = connected;
        updateInputViewShown();
        refreshHwToolbar();
    }

    /** 연결된 장치 가운데 외장 키보드(가상 장치가 아닌 글자 자판)가 있는지. isHardwareKey()와 같은 기준이다. */
    private static boolean findHwKeyboard() {
        for (int id : android.view.InputDevice.getDeviceIds()) {
            android.view.InputDevice d = android.view.InputDevice.getDevice(id);
            if (d != null && !d.isVirtual() && d.supportsSource(android.view.InputDevice.SOURCE_KEYBOARD)
                    && d.getKeyboardType() == android.view.InputDevice.KEYBOARD_TYPE_ALPHABETIC) return true;
        }
        return false;
    }

    /**
     * 외장 키보드가 있으면 시스템은 입력란이 저절로 요청한 표시(SHOW_IMPLICIT)를 받지 않는다.
     * 도구 막대만 보일 때는 화면을 가리지 않으므로 입력란에 들어갈 때마다 보인다.
     */
    @Override
    public boolean onShowInputRequested(int flags, boolean configChange) {
        return hwToolbarOnly() || super.onShowInputRequested(flags, configChange);
    }

    /** 외장 키보드를 연결해 화면 자판 대신 도구 막대만 보이는 상태인지 (실험실 '도구 막대 보이기'). */
    private boolean hwToolbarOnly() {
        return prefs != null && prefs.hwToolbar() && hwKeyboardConnected && !hwFullKeyboard
                && !super.onEvaluateInputViewShown();
    }

    /** 도구 막대만 보일 때 '키보드 보이기' 버튼을 눌러, 외장 키보드가 있어도 자판 전체를 보이는 중인지. 키보드를 감추면 풀린다. */
    private boolean hwFullKeyboard;

    /** 도구 막대의 '키보드 숨기기' 버튼. 도구 막대만 보일 때는 자판 전체를 보이고, 아니면 키보드를 감춘다. */
    private void onHideButton() {
        if (hwToolbarOnly()) {
            hwFullKeyboard = true;
            refreshHwToolbar();
        } else {
            requestHideSelf(0);
        }
    }

    /** 도구 막대만 보일 때는 '키보드 숨기기' 버튼을 위아래로 뒤집어 '키보드 보이기' 버튼으로 보인다. */
    private void updateHideButton() {
        if (hideButton == null) return;
        boolean show = hwToolbarOnly();
        hideButton.setFlipped(show);
        hideButton.setContentDescription(show ? "키보드 보이기" : "키보드 숨기기");
    }

    /** 지금 자판 자리를 비우고 도구 막대만 보이는지. 이모지·클립보드·검색 화면은 원래 크기로 보인다. */
    private boolean toolbarOnlyNow() {
        return panel == PANEL_KEYBOARD && hwToolbarOnly();
    }

    /** 마지막으로 applySettings()를 한 때의 hwToolbarOnly(). 자판 높이·한 손 모드 배치가 이 상태를 따른다. */
    private boolean appliedBarOnly;

    /** 외장 키보드를 연결·해제했거나 '도구 막대 보이기'를 바꿨으면 자판 크기와 배치를 다시 맞춘다. */
    private void refreshHwToolbar() {
        if (!hwKeyboardConnected || super.onEvaluateInputViewShown()) hwFullKeyboard = false;   // 외장 키보드를 뺐다
        if (keyboard == null || hwToolbarOnly() == appliedBarOnly) return;
        keyboard.cancelAll();
        applySettings();
        setLayoutKind(layoutKind);
        applyTopSlotVisibility();
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
        // 창 관리자에 직접 물은 시스템 바 높이와 인셋 콜백 값 중 큰 쪽만큼 키보드를 위로 띄운다 (refreshBottomInset).
        root.setOnApplyWindowInsetsListener((v, insets) -> {
            refreshBottomInset(insets);
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
        forgetBar = null;   // 도구 막대의 확인 줄은 필요할 때 만든다 (새 화면에는 아직 없다)
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
                resultStripHeight()));

        content = new FrameLayout(this);
        content.setClipChildren(false);
        candidateGrid = null;   // 예전 화면의 격자는 버린다
        candidateGridOpen = false;
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
        refreshHwToolbar();
        main.removeCallbacks(releaseWhileHidden);
        applyNavigationBarStyle();
        refreshBottomInset(null);
        if (root != null) root.requestApplyInsets();   // 마지막으로 받은 인셋을 다시 받아 콜백 값을 채운다
    }

    /**
     * 키보드 창 아래쪽을 가리는 하단 바 높이만큼 자판을 띄운다: 창 관리자에게 직접 물은 시스템 바 높이 + 키보드 창에만 붙는 영역.
     * 1) 시스템 바(내비게이션 바·작업 표시줄) 높이는 물을 때마다 새로 계산되어, 탐색 방법을 바꾼 직후처럼 이미 떠 있는
     *    키보드 창에 인셋 콜백이 안 오거나 옛 값이 올 때도 맞다 (API 30 이상).
     * 2) 일부 기기(Vivo의 '입력 방법 빠른 전환' 하단 바 등)가 키보드 창에만 더하는 영역은 인셋 콜백 값에만 들어 있다.
     *    콜백이 올 때 '콜백 값이 창 관리자 값보다 얼마나 큰지'만 따로 기억해 둔다. 콜백 값을 통째로 기억하면 탐색 방법을
     *    버튼에서 제스처로 바꾼 직후 옛(큰) 값이 남아 자판 아래에 빈 띠가 생긴다. 탐색 방법을 바꾼 직후에는 콜백이
     *    바꾸기 전의 값을 다시 보내기도 해서, 콜백과 창 관리자 값의 탐색 방법(제스처인지)이 다르면 그 콜백은 버린다.
     * 호출하는 때: 키보드가 올라올 때, 화면 설정이 바뀔 때, 인셋 콜백이 올 때.
     *
     * @param callbackInsets 인셋 콜백이 방금 준 값. 콜백에서 부른 것이 아니면 null.
     */
    private void refreshBottomInset(WindowInsets callbackInsets) {
        if (root == null) return;
        WindowInsets freshInsets = freshInsets();
        int fresh = freshInsets == null ? -1 : legacyBottomInset(freshInsets);
        if (callbackInsets != null) {
            int callback = legacyBottomInset(callbackInsets);
            if (fresh < 0) {
                callbackBottom = callback;   // 창 관리자에게 물을 수 없는 기기: 콜백 값을 그대로 쓴다
            } else if (gestureNavigation(callbackInsets) != gestureNavigation(freshInsets)) {
                // 콜백이 탐색 방법을 바꾸기 전의 옛 값이다 (버튼 → 제스처 직후에 버튼 시절의 큰 값이 다시 오는 경우 등).
                // 이 값으로 차이를 재면 자판 아래에 빈 띠가 생기므로 버린다.
                windowOnlyBottom = 0;
            } else {
                windowOnlyBottom = Math.max(0, callback - fresh);
            }
        }
        int bottom = fresh >= 0 ? fresh + windowOnlyBottom : callbackBottom;
        if (bottom < 0) return;   // 어느 값도 모른다: 지금 그대로 둔다
        if (root.getPaddingBottom() != bottom) root.setPadding(0, 0, 0, bottom);
    }

    /** 키보드 창에만 붙는 하단 영역의 높이 (인셋 콜백 값 - 창 관리자 값). 키보드가 내려가면 0으로 비운다. */
    private int windowOnlyBottom;
    /** 창 관리자에게 물을 수 없는 기기에서 인셋 콜백이 마지막으로 준 하단 높이 (-1 = 아직 모른다). */
    private int callbackBottom = -1;

    /** 창 관리자가 지금 알려 주는 하단 바 높이. 알 수 없으면 -1. */
    private int freshBottomInset() {
        WindowInsets insets = freshInsets();
        return insets == null ? -1 : legacyBottomInset(insets);
    }

    /** 창 관리자가 지금 새로 계산해 주는 이 창의 인셋. 물을 수 없으면 null. */
    private WindowInsets freshInsets() {
        if (Build.VERSION.SDK_INT < 30) return null;
        try {
            android.view.WindowManager wm = getWindow() != null && getWindow().getWindow() != null
                    ? getWindow().getWindow().getWindowManager() : null;
            if (wm == null) wm = getSystemService(android.view.WindowManager.class);
            return wm == null ? null : wm.getCurrentWindowMetrics().getWindowInsets();
        } catch (RuntimeException e) {
            return null;
        }
    }

    /**
     * 이 인셋이 제스처 탐색 상태에서 나온 것인지: 제스처 탐색은 화면 왼쪽·오른쪽 가장자리에 뒤로 가기 손짓 영역이 있고,
     * 버튼 탐색에는 없다. 키보드 창 전용 하단 영역과 상관없이 탐색 방법만 보여 주는 신호다.
     */
    private static boolean gestureNavigation(WindowInsets insets) {
        if (Build.VERSION.SDK_INT < 30) return false;
        android.graphics.Insets g = insets.getInsets(WindowInsets.Type.systemGestures());
        return g.left > 0 || g.right > 0;
    }

    private static int legacyBottomInset(WindowInsets insets) {
        if (Build.VERSION.SDK_INT >= 30) {
            return Math.max(insets.getInsets(WindowInsets.Type.navigationBars()).bottom,
                    insets.getInsets(WindowInsets.Type.tappableElement()).bottom);
        }
        return insets.getSystemWindowInsetBottom();
    }

    @Override
    public void onConfigurationChanged(android.content.res.Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        refreshBottomInset(null);   // 폴더블을 접고 펴거나 탐색 방법을 바꾸면 하단 바가 달라진다
        hwKeyboardConnected = findHwKeyboard();   // 외장 키보드를 연결하거나 뺐다
        updateInputViewShown();
        refreshHwToolbar();
    }

    @Override
    public void onWindowHidden() {
        super.onWindowHidden();
        windowHidden = true;
        hwFullKeyboard = false;   // 다음에 올라올 때는 다시 도구 막대만 보인다
        windowOnlyBottom = 0;   // 옛 값이 남아 다음에 올라올 때 자판 아래에 빈 띠가 생기지 않게 한다
        callbackBottom = -1;
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
        // 방금 복사한 내용 칩은 빈 공간 자리에 뜬다 (도구 버튼은 그대로 둔다).
        clipChipSlot = buildClipChipSlot();
        bar.addView(clipChipSlot, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));
        // 추천 단어는 왼쪽 버튼들 오른쪽에 뜬다. 추천이 있으면 빈 공간 대신 이 영역을 쓰고 오른쪽 버튼들은 접는다.
        suggestBar = buildSuggestBar();
        suggestBar.setVisibility(View.GONE);
        bar.addView(suggestBar, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));
        // 일본어·중국어 변환 후보도 같은 자리에 뜬다 (조합 중인 동안, 막대 전체를 쓴다).
        candidateBar = buildCandidateBar();
        bar.addView(candidateBar, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));
        // 글자를 선택하면 같은 자리에 편집 칩(잘라내기·복사·붙여넣기·전체 선택)이 뜬다.
        editBar = buildEditBar();
        bar.addView(editBar, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));
        // 자동 완성 칩도 같은 자리에 뜬다 (추천 단어와 함께 뜨면 칩이 먼저).
        autofillBar = autofill != null ? autofill.createBar(theme) : null;
        if (autofillBar != null) {
            bar.addView(autofillBar, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));
        }
        oneHandButton = toolButton(Icons.ONE_HAND, "한 손 모드", v -> toggleOneHand());
        bar.addView(oneHandButton);
        settingsButton = toolButton(Icons.SETTINGS, "설정", v -> openSettings());
        bar.addView(settingsButton);
        hideButton = toolButton(Icons.HIDE, "키보드 숨기기", v -> onHideButton());
        bar.addView(hideButton);
        // 버튼을 위·아래·왼쪽·오른쪽으로 밀면 정해 둔 기능을 실행한다 (기본: 클립보드 위 = 복사·아래 = 붙여넣기,
        // 이모지 아래 = 최근 이모지, 실행 취소 아래 = 다시 실행, 한 손 모드 왼쪽·오른쪽 = 그쪽 한 손 모드).
        attachToolSwipe(clipboardButton, ToolbarSwipes.CLIPBOARD);
        attachToolSwipe(emojiButton, ToolbarSwipes.EMOJI);
        attachToolSwipe(voiceButton, ToolbarSwipes.VOICE);
        attachToolSwipe(undoButton, ToolbarSwipes.UNDO);
        attachToolSwipe(settingsButton, ToolbarSwipes.SETTINGS);
        attachToolSwipe(hideButton, ToolbarSwipes.HIDE);
        attachToolSwipe(oneHandButton, ToolbarSwipes.ONE_HAND);
        // 기능이 있는 방향은 버튼이 처리하고, 없는 방향은 도구 막대를 그 방향으로 민 것으로 본다.
        // 자동 완성 칩 줄은 좌우로 밀어 스크롤하므로, 그 줄 위의 좌우 밀기는 도구 막대가 가로채지 않는다.
        bar.setSwipeOwner((v, dir) -> v == autofillBar || v == candidateBar
                ? dir == Key.SWIPE_LEFT || dir == Key.SWIPE_RIGHT
                : !SwipeAction.NONE.equals(toolButtonAction(toolSlotOf(v), dir)));
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
        toolbar.removeView(clipChipSlot);
        toolbar.removeView(suggestBar);
        toolbar.removeView(candidateBar);
        toolbar.removeView(editBar);
        if (autofillBar != null) toolbar.removeView(autofillBar);
        for (String id : order[0]) toolbar.addView(toolButtonOf(id));
        toolbar.addView(toolSpacer, new LinearLayout.LayoutParams(0, 1, 1f));
        toolbar.addView(clipChipSlot, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));
        toolbar.addView(suggestBar, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));
        toolbar.addView(candidateBar, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));
        toolbar.addView(editBar, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));
        if (autofillBar != null) {
            toolbar.addView(autofillBar, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));
        }
        for (String id : order[1]) toolbar.addView(toolButtonOf(id));
        refreshToolbarVisibility();
    }

    private void attachToolSwipe(View button, int slot) {
        SwipeVertical.attachFourWay(button, dir -> !SwipeAction.NONE.equals(toolButtonAction(slot, dir)),
                () -> Ui.dp(this, prefs.fnSwipeThresholdDp()),
                dir -> {
                    feedback.onToolbar();
                    String action = toolButtonAction(slot, dir);
                    // 한 손 모드 버튼의 기본 좌우 밀기는 그쪽 한 손 모드로 바꾼다 (이미 그쪽이어도 끄지 않는다).
                    if (slot == ToolbarSwipes.ONE_HAND && dir == Key.SWIPE_LEFT && SwipeAction.ONE_HAND_LEFT.equals(action)) {
                        setOneHand(Prefs.ONE_HAND_LEFT);
                    } else if (slot == ToolbarSwipes.ONE_HAND && dir == Key.SWIPE_RIGHT
                            && SwipeAction.ONE_HAND_RIGHT.equals(action)) {
                        setOneHand(Prefs.ONE_HAND_RIGHT);
                    } else {
                        onKeyFunction(null, action);
                    }
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

    /** 도구 막대 버튼을 이 방향으로 밀 때 실행할 기능. */
    private String toolButtonAction(int slot, int dir) {
        if (slot < 0) return SwipeAction.NONE;
        return toolbarSwipes.action(slot, dir);
    }

    /** 도구 막대 자체를 이 방향으로 밀 때 실행할 기능. */
    private String toolbarAction(int dir) {
        return toolbarSwipes.action(ToolbarSwipes.BAR, dir);
    }

    private IconButton toolButton(int icon, String desc, View.OnClickListener l) {
        IconButton b = new IconButton(this, icon, theme.text, theme.keyPressed, desc);
        b.setOnClickListener(v -> {
            feedback.onToolbar();
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
        dropStalePanels();
        float scale = prefs.toolButtonSize() / 100f;
        int width = Math.round(Ui.dp(this, 40) * scale);
        float icon = Ui.dp(this, 22) * scale;
        // 버튼마다 자동 완성 칩과 같은 모양의 칩 배경을 씌운다 (IconButton.setToolbarStyle).
        int chipH = Ui.chipHeight(this, toolbarHeight);
        for (String id : ToolbarButtons.ALL) {
            View v = toolButtonOf(id);
            ViewGroup.LayoutParams lp = v.getLayoutParams();
            if (lp != null && (lp.width != width || lp.height != toolbarHeight)) {
                lp.width = width;
                lp.height = toolbarHeight;
                v.setLayoutParams(lp);
            }
            ((IconButton) v).setIconSize(icon);
            ((IconButton) v).setToolbarStyle(theme, toolbarHeight);
        }
        if (searchBar != null) {
            searchBack.setToolbarStyle(theme, toolbarHeight);
            ViewGroup.LayoutParams lp = searchPill.getLayoutParams();
            if (lp.height != chipH) {
                lp.height = chipH;
                searchPill.setLayoutParams(lp);
            }
        }
        if (editBar != null) {
            LinearLayout row = (LinearLayout) editBar.getChildAt(0);
            for (int i = 0; i < row.getChildCount(); i++) {
                ViewGroup.LayoutParams lp = row.getChildAt(i).getLayoutParams();
                if (lp.height != chipH) {
                    lp.height = chipH;
                    row.getChildAt(i).setLayoutParams(lp);
                }
            }
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

    /** 추천 단어 앞의 빈 칸. 분리 키보드에서는 남는 폭을 모두 가져 추천 단어 세 개가 오른쪽 끝에 붙는다. */
    private View suggestLead;

    /** 방금 복사한 내용 칩의 자리. 칩은 도구 막대 한가운데에 뜨고(버튼에 밀릴 때만 비켜 간다) 내용이 길면 줄어든다. */
    private ClipChip.Slot buildClipChipSlot() {
        ClipChip.Slot slot = new ClipChip.Slot(this);
        slot.setVisibility(View.GONE);
        clipChip = new ClipChip(this, theme.text, theme.hint, theme.key, theme.keyPressed);
        Neu.apply(clipChip, theme, theme.key, Ui.chipRadius(this));
        clipChip.setOnClickListener(v -> {
            feedback.onToolbar();
            pasteClipChip();
        });
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER);
        lp.leftMargin = Ui.dp(this, 4);
        lp.rightMargin = Ui.dp(this, 4);
        slot.addView(clipChip, lp);
        return slot;
    }

    /** 칩 하나의 최대 폭 (dp): 가로 모드처럼 막대가 아주 넓어도 칩이 길게 늘어나지 않게 한다. */
    private static final int EDIT_CHIP_MAX_DP = 96;

    /**
     * 글자를 선택했을 때 뜨는 편집 칩 줄: 잘라내기·복사·붙여넣기·전체 선택. 칩은 같은 폭으로 줄을 나눠 쓰고
     * (칩 하나가 EDIT_CHIP_MAX_DP보다 넓어지지 않게 줄 전체를 가운데에 모은다), 모양은 자동 완성 칩과 같다.
     * 칩마다 이름 앞에 아이콘이 있고, 칩이 좁아 이름이 들어가지 않으면 아이콘만 보인다.
     */
    private FrameLayout buildEditBar() {
        FrameLayout frame = new FrameLayout(this);
        frame.setVisibility(View.GONE);
        LinearLayout row = new LinearLayout(this) {
            @Override
            protected void onMeasure(int widthSpec, int heightSpec) {
                int max = Ui.dp(getContext(), EDIT_CHIP_MAX_DP) * getChildCount();
                if (MeasureSpec.getSize(widthSpec) > max) widthSpec = MeasureSpec.makeMeasureSpec(max, MeasureSpec.EXACTLY);
                super.onMeasure(widthSpec, heightSpec);
            }
        };
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.addView(editChip(Icons.CUT, "잘라내기", v -> contextAction(android.R.id.cut)));
        row.addView(editChip(Icons.COPY, "복사", v -> copySelection(false)));
        row.addView(editChip(Icons.PASTE, "붙여넣기", v -> contextAction(android.R.id.paste)));
        row.addView(editChip(Icons.SELECT_ALL, "전체 선택", v -> contextAction(android.R.id.selectAll)));
        frame.addView(row, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT, Gravity.CENTER));
        return frame;
    }

    private EditChip editChip(int icon, String label, View.OnClickListener l) {
        EditChip t = new EditChip(this, icon, label, theme.text, theme.key, theme.keyPressed);
        Neu.apply(t, theme, theme.key, Ui.chipRadius(this));
        t.setOnClickListener(v -> {
            feedback.onToolbar();
            l.onClick(v);
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, Ui.chipHeight(this, toolbarHeightPx()), 1f);
        lp.leftMargin = Ui.dp(this, 2);
        lp.rightMargin = Ui.dp(this, 2);
        t.setLayoutParams(lp);
        return t;
    }

    private LinearLayout buildSuggestBar() {
        suggestions = new String[0];   // 새 막대는 비어 있다 (예전 막대의 내용과 비교하지 않도록)
        shortcutShown = false;
        suggestWord = "";
        LinearLayout bar = new LinearLayout(this);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        suggestLead = new View(this);
        bar.addView(suggestLead, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 0f));
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
        // 도구 막대 자리에 놓이므로 도구 막대와 같은 높이를 쓰고, 칩(뒤로·검색창)도 도구 막대 칩과 같은 높이다.
        // 높이는 sizeToolbar()가 설정의 도구 막대 높이에 맞춘다. 좌우 여백은 applySettings()가 도구 막대와 같게 맞춘다
        // (이모지 창 맨 위 줄과 같다).
        IconButton back = new IconButton(this, Icons.BACK, theme.text, theme.keyPressed, "이모지로 돌아가기");
        searchBack = back;
        back.setOnClickListener(v -> showPanel(PANEL_EMOJI));
        bar.addView(back, new LinearLayout.LayoutParams(Ui.dp(this, 44), ViewGroup.LayoutParams.MATCH_PARENT));
        // 검색창은 이모지 창 맨 위 줄의 검색창과 같은 모양이다: 돋보기 아이콘과 글자, 칩과 같은 높이의 둥근 바탕.
        searchPill = new LinearLayout(this);
        searchPill.setGravity(Gravity.CENTER_VERTICAL);
        searchPill.setBackground(Ui.round(theme.functionKey, Ui.chipRadius(this)));
        Neu.apply(searchPill, theme, theme.functionKey, Ui.chipRadius(this));
        searchPill.setPadding(Ui.dp(this, 10), 0, Ui.dp(this, 12), 0);
        IconButton glass = new IconButton(this, Icons.SEARCH, theme.hint, theme.keyPressed, null);
        glass.setClickable(false);
        searchPill.addView(glass, new LinearLayout.LayoutParams(Ui.dp(this, 28), ViewGroup.LayoutParams.MATCH_PARENT));
        searchText = new TextView(this);
        searchText.setSingleLine(true);
        searchText.setEllipsize(TextUtils.TruncateAt.START);
        searchText.setGravity(Gravity.CENTER_VERTICAL);
        searchText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        searchText.setPadding(Ui.dp(this, 6), 0, 0, 0);
        searchPill.addView(searchText, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));
        LinearLayout.LayoutParams pillLp = new LinearLayout.LayoutParams(0, Ui.chipHeight(this, toolbarHeightPx()), 1f);
        pillLp.leftMargin = Ui.dp(this, 2);
        pillLp.rightMargin = Ui.dp(this, 2);
        bar.addView(searchPill, pillLp);
        return bar;
    }

    /** 설정값을 뷰에 적용한다. 설정 화면에서 돌아올 때마다 호출된다. */
    private void applySettings() {
        refreshCjkAvailability();
        if (cjkLang != null && !cjkAvailable(cjkLang)) {
            switchLanguage("ko");   // 실험실에서 껐거나 팩을 지웠다
        } else if (cjkLang != null) {
            ensureCjk(cjkLang);
        }
        boolean wasWords = cfgWords, wasLearn = cfgLearn, wasSuggest = cfgSuggest;
        cfgSuggest = prefs.suggestWords();
        cfgShortcuts = prefs.shortcutsEnabled();
        shortcuts = cfgShortcuts ? Shortcuts.of(prefs.shortcuts()) : Shortcuts.of(null);
        cfgAutoCorrect = prefs.autoCorrect();   // 단어 추천과 따로 켜고 끈다
        cfgWords = cfgSuggest || cfgAutoCorrect;
        cfgLearn = cfgWords && prefs.learnWords();
        cfgPredict = prefs.predictNext();
        suggester.setUserEnabled(cfgLearn);
        suggester.setSuggestEnabled(cfgSuggest);   // 자동 수정만 쓰면 추천에만 쓰는 초성 색인을 만들지 않는다
        suggester.setKeyRows(KeyboardLayout.koreanLetterRows(prefs), KeyboardLayout.koreanAspirateSwipe(prefs));
        if (suggester.packChanged()) {
            // 설정에서 사전 팩을 가져오거나 지웠다: 읽어 둔 사전을 놓고 필요할 때 새 사전을 읽는다.
            suggester.release();
            warmingKo = warmingEn = false;
        }
        feedback.configure(prefs);
        if (emojiPanel != null) emojiPanel.setSizeLevel(prefs.emojiSize());
        applyResultSize();
        composer.setDoubleTapVowel(prefs.doubleTapVowel());
        WordSuggester.setDoubleTapVowel(prefs.doubleTapVowel());   // '안너'도 '안녀…'로 찾는다
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
        keyboard.setGapCursor(sizePrefs.splitGapCursor(), sizePrefs.splitGapSpeed());
        keyboard.setCursorSpeed(prefs.spaceCursorSpeedH(), prefs.spaceCursorSpeedV(),
                prefs.charCursorSpeedH(), prefs.charCursorSpeedV());
        keyboard.setSpaceLangSwipe(prefs.spaceLangSwipe());
        keyboard.setSpaceSelect(prefs.spaceSelect());
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
        keyboard.setNumberHintHidden(prefs.numberHintHidden());
        keyboard.setRepeatChars(prefs.repeatChars());
        applySuggestLead();
        keyboard.setHitShrink(prefs.deleteHitShrink() ? prefs.deleteHitShrinkPct() / 100f : 0f,
                prefs.spaceHitShrink() ? prefs.spaceHitShrinkPct() / 100f : 0f);
        keyboard.setCharCursor(!prefs.swipeKeyboardCustom() && prefs.charCursor(),
                prefs.charCursorH(), prefs.charCursorV());
        // 키보드 밀기: 완전 사용자화를 켜면 편집한 배치를, 끄면 '두 손가락으로 밀어서 실행 취소'만 쓴다.
        keyboard.setPickSwipe(prefs.swipePickSuggestion());
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
            if (!wasWords || (cfgLearn && !wasLearn) || (cfgSuggest && !wasSuggest)) warmSuggester();
            if (panel == PANEL_KEYBOARD) scheduleSuggest();   // 추천을 껐으면 이 안에서 지운다
        } else {
            clearSuggestions();
            suggester.release();
            warmingKo = warmingEn = false;
            if (panel == PANEL_KEYBOARD) scheduleSuggest();   // 단축어는 단어 추천을 꺼도 쓴다
        }
        if (wasLearn && !cfgLearn) runIo(this::releaseLearned);   // 학습을 끄면 학습한 단어도 메모리에서 내린다
        float rows = 4 + (prefs.numberRow() ? KeyboardLayout.numberRowHeight(prefs) : 0f);
        keyboard.setKeyGaps(Ui.dp(this, prefs.keyGapXDp()), Ui.dp(this, prefs.keyGapYDp()));
        keyboard.setInsets(Ui.dp(this, prefs.padLeftDp()), Ui.dp(this, prefs.padTopDp()),
                Ui.dp(this, prefs.padRightDp()), Ui.dp(this, prefs.padBottomDp()));
        // 도구 막대의 양 끝 버튼이 자판의 양 끝 키와 같은 선에 오도록 '자판 모양'의 좌우 여백을 따른다.
        if (toolbar != null) toolbar.setPadding(Ui.dp(this, prefs.padLeftDp()), 0, Ui.dp(this, prefs.padRightDp()), 0);
        if (searchBar != null) searchBar.setPadding(Ui.dp(this, prefs.padLeftDp()), 0, Ui.dp(this, prefs.padRightDp()), 0);
        applyPanelInsets();
        arrangeToolbar();
        if (topSlot != null) {
            sizeToolbar();
            placeTopSlot();
        }
        appliedBarOnly = hwToolbarOnly();
        updateHideButton();
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

    /** 한 손 모드가 실제로 적용되는 상태. 외장 키보드로 도구 막대만 보일 때는 쓰지 않는다 (막대가 좁아지지 않게). */
    private String activeOneHand() {
        if (hwToolbarOnly()) return Prefs.ONE_HAND_OFF;
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
     * 아래에는 '세로 위치'만큼 빈 칸을 두어 자판을 띄운다 (한 손 모드가 아니어도 가로 모드·대화면의 세로 위치를 쓴다).
     */
    private void applyOneHand() {
        if (body == null) return;
        String side = activeOneHand();
        boolean on = !Prefs.ONE_HAND_OFF.equals(side);
        Prefs profilePrefs = prefs.profileView(profile());
        int width = profilePrefs.oneHandWidthForProfile();
        // 세로 위치: 한 손 모드는 한 손 모드의 값을, 아니면 가로 모드·대화면에서 정한 값을 쓴다 (세로 모드는 0).
        // 외장 키보드로 도구 막대만 보일 때는 띄우지 않는다 (막대 아래에 빈 칸만 남는다).
        int lift = appliedBarOnly ? 0 : Ui.dp(this, on ? prefs.oneHandLiftDp() : profilePrefs.profileLiftDp());
        String signature = side + "/" + width + "/" + lift;
        if (signature.equals(appliedOneHand)) return;
        appliedOneHand = signature;
        // 한 손 모드를 켜고 끌 때마다 자동 완성 칩을 다시 배치한다 (한 손 모드에서는 열쇠만, 아니면 계정 칩 모두).
        if (autofill != null) autofill.setCompact(on);
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
    public void onStartInput(EditorInfo attribute, boolean restarting) {
        super.onStartInput(attribute, restarting);
        initialTextFresh = true;
        hwForwarded.clear();
        // 외장 키보드를 쓰면 자판 화면이 뜨지 않아 onStartInputView가 불리지 않는다. 입력란에 맞는 언어는 여기서도 정한다.
        applyFieldLanguage(attribute, restarting);
    }

    @Override
    public void onStartInputView(EditorInfo info, boolean restarting) {
        super.onStartInputView(info, restarting);
        windowHidden = false;   // 창이 실제로 보이기(onWindowShown) 전에 불리므로 여기서도 켠다
        if (KeyboardTheme.of(this) != theme) {
            setInputView(onCreateInputView());
        }
        applySettings();
        composer.reset();
        resetCjk();
        hadComposing = false;
        // 다른 앱에 다녀오면 입력란에 조합 중 표시(밑줄)가 남아 있는 일이 있다. 조합 상태는 위에서 비웠으므로
        // 그 표시도 확정해 둔다. 그대로 두면 다음 글자의 setComposingText가 남은 조합 영역(마지막 글자)을 덮어 지운다.
        InputConnection startIc = getCurrentInputConnection();
        if (startIc != null) startIc.finishComposingText();
        lastWasSpace = false;
        if (!restarting || panel == PANEL_SEARCH) showPanel(PANEL_KEYBOARD);
        int cls = info.inputType & InputType.TYPE_MASK_CLASS;
        suggestAllowed = suggestAllowedFor(info);
        fieldKind = fieldKindFor(info);
        shortcutAllowed = shortcutAllowedFor(info);
        domainField = domainFieldFor(info);
        passwordField = passwordFieldFor(info);
        // 시크릿 모드 등 앱이 학습하지 말라고 표시한 입력란에서는 단어를 학습하지 않는다 (추천은 그대로).
        learnAllowed = (info.imeOptions & EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING) == 0;
        mirror.start(info.initialSelStart, info.initialSelEnd);
        if (initialTextFresh) seedMirror(info);
        initialTextFresh = false;
        selStart = info.initialSelStart;
        selEnd = info.initialSelEnd;
        forgetCursorEdges();
        clearSuggestions();
        applyFieldLanguage(info, restarting);
        if (cls == InputType.TYPE_CLASS_NUMBER || cls == InputType.TYPE_CLASS_PHONE
                || cls == InputType.TYPE_CLASS_DATETIME) {
            setLayoutKind(KeyboardLayout.NUMBER);
        } else {
            setLayoutKind(lettersKind());
        }
        keyboard.setEnterIcon(enterIcon(info));
        setShift(0);
        updateAutoCap();
        warmSuggester();   // 첫 글자부터 추천이 나오도록 미리 읽어 둔다
        scheduleSuggest();
        refreshToolbarVisibility();   // 입력란이 바뀌면 복사한 내용 칩의 가림 여부도 다시 정한다 (비밀번호 입력란)
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
        if (cfgLearn) runIo(() -> {   // 바뀐 것이 있을 때만 쓴다
            userWords.save();
            usageHistory.save();
        });
    }

    @Override
    public void onFinishInput() {
        finishComposingOnLeave();
        super.onFinishInput();
        composer.reset();
        resetCjk();
        hadComposing = false;
        mirror.forget();
        clearSuggestions();
        if (autofill != null) autofill.clear();   // 입력란을 떠나면 그 칸의 자동 완성 칩도 버린다
        copyPending = false;   // 다른 칸으로 갔으면 지난 칸의 복사 결과는 알리지 않는다
        main.removeCallbacks(copyRefused);
    }

    // ---------------------------------------------------------------- 자동 완성 (Android 11 이상)

    /**
     * 자동 완성 서비스에 칩을 도구 막대 자리에 보여 줄 수 있다고 알린다. 칩은 서비스가 채울 수 있는 칸에서만 온다.
     * 설정에서 껐으면 null을 돌려준다 (그러면 서비스가 앱 안에 원래의 자동 완성 목록을 띄운다).
     */
    @Override
    public InlineSuggestionsRequest onCreateInlineSuggestionsRequest(android.os.Bundle uiExtras) {
        if (autofill == null || !prefs.autofillInline()) return null;
        KeyboardTheme t = theme != null ? theme : KeyboardTheme.of(this);
        int height = toolbarHeight > 0 ? toolbarHeight : toolbarHeightPx();
        // 칩은 쪽으로 넘기며 보므로, 한 칩이 칩 줄(왼쪽 도구 버튼을 뺀 폭에서 쪽 넘김 버튼·고정 칩 자리를 뺀 폭)에
        // 들어갈 만큼만 키운다. 긴 글은 서비스가 줄여 보인다.
        int maxWidth = Math.max(Ui.dp(this, 96), Math.min(Ui.dp(this, 260), chipSlotWidth() - autofill.reservedWidth()));
        return autofill.request(t, height, maxWidth);
    }

    /** 칩 줄 자리의 폭 (px): 도구 막대 폭에서 안 가려지는 왼쪽 도구 버튼들의 폭을 뺀 값 ('도구 막대 전체를 쓰기'면 막대 전체). */
    private int chipSlotWidth() {
        int width = toolbar != null && toolbar.getWidth() > 0 ? toolbar.getWidth() : getResources().getDisplayMetrics().widthPixels;
        width -= (toolbar != null ? toolbar.getPaddingLeft() + toolbar.getPaddingRight() : 0);
        if (!prefs.suggestFullBar() && prefs.toolbar()) {
            String[][] order = toolOrder != null ? toolOrder : prefs.toolOrder();
            for (String id : order[0]) {
                if (!prefs.toolShown(id)) continue;
                // 시스템이 onCreateInputView보다 먼저 칩 요청을 부르면 도구 버튼이 아직 없다 (기본 폭으로 센다).
                View button = toolButtonOf(id);
                ViewGroup.LayoutParams lp = button != null ? button.getLayoutParams() : null;
                width -= lp != null && lp.width > 0 ? lp.width : Ui.dp(this, 40);
            }
        }
        return width;
    }

    /** 자동 완성 서비스가 보낸 칩을 보여 준다 (빈 목록이면 감춘다). */
    @Override
    public boolean onInlineSuggestionsResponse(InlineSuggestionsResponse response) {
        if (autofill == null) return false;
        autofill.setCompact(!Prefs.ONE_HAND_OFF.equals(activeOneHand()));   // 칩을 놓기 전에 지금 모드에 맞춘다
        autofill.show(response.getInlineSuggestions());
        return true;
    }

    @Override
    public void onUpdateSelection(int oldSelStart, int oldSelEnd, int newSelStart, int newSelEnd,
                                  int candidatesStart, int candidatesEnd) {
        super.onUpdateSelection(oldSelStart, oldSelEnd, newSelStart, newSelEnd, candidatesStart, candidatesEnd);
        String mirrored = mirror.text();
        mirror.onSelection(newSelStart, newSelEnd);   // 이모지 검색 중에도 실제 입력란의 커서 변화는 따라간다
        // 메신저의 보내기 버튼처럼 앱이 입력란을 한 번에 비웠다: 스페이스 없이 끝난 마지막 단어도 학습한다.
        if (newSelStart == 0 && newSelEnd == 0 && oldSelStart > 1 && oldSelStart == oldSelEnd
                && mirrored != null && mirror.text() == null && panel != PANEL_SEARCH) {
            learnFromText(mirrored);
        }
        initialTextFresh = false;   // EditorInfo의 커서 앞 글자는 이제 지난 내용일 수 있다
        boolean hadSelection = selStart >= 0 && selEnd >= 0 && selStart != selEnd;
        selStart = newSelStart;
        selEnd = newSelEnd;
        if (panel == PANEL_SEARCH) return;
        // 글자를 선택하거나 선택을 풀면 선택 편집 칩을 띄우거나 감춘다.
        if (hadSelection != (newSelStart >= 0 && newSelEnd >= 0 && newSelStart != newSelEnd)) onAutofillChipsChanged();
        // 사용자가 커서를 옮기면 조합 중인 글자를 확정하고 조합을 끝낸다.
        if (hadComposing && (candidatesEnd == -1 || newSelStart != candidatesEnd || newSelEnd != candidatesEnd)) {
            composer.reset();
            resetCjk();
            hadComposing = false;
            InputConnection ic = getCurrentInputConnection();
            if (ic != null) ic.finishComposingText();
            mirror.forget();
        }
        if (composer.isEmpty()) updateAutoCap();
        scheduleSuggest();
    }

    /**
     * 입력란을 연결할 때 함께 온 커서 앞 글자로 따라 적을 글자를 미리 채운다 (Android 11부터). 첫 글자부터 추천을 찾을 때
     * 앱에 다시 묻지 않아도 된다. 앱이 보내 주지 않았거나 선택 영역이 있으면 예전처럼 처음 필요할 때 앱에 묻는다.
     * 추천·단축어를 쓰지 않는 칸(비밀번호 등)에서는 읽지 않는다.
     */
    private void seedMirror(EditorInfo info) {
        if (Build.VERSION.SDK_INT < 30) return;
        if (!suggestAllowed && !shortcutAllowed) return;
        int sel = info.initialSelStart;
        if (sel < 0 || sel != info.initialSelEnd) return;
        CharSequence before;
        try {
            before = info.getInitialTextBeforeCursor(TextMirror.KEEP, 0);
        } catch (RuntimeException e) {
            return;
        }
        if (before == null) return;
        // 요청보다 덜 왔더라도 커서 위치보다 짧으면 앱이 잘라 보낸 것이라, 입력란의 처음부터라고 보지 않는다.
        int requested = before.length() < TextMirror.KEEP && before.length() != sel ? before.length() : TextMirror.KEEP;
        mirror.fill(before, requested);
    }

    /**
     * 이메일 입력란이나 앱이 영어 입력을 요청한 칸이면 영어 자판으로 시작하고, 그런 칸을 떠나면 원래의 한글로 돌아간다.
     * 설정에 저장한 언어는 바꾸지 않는다. 사용자가 그 칸에서 직접 언어를 바꿨으면 그 선택을 따른다.
     * 자판을 놓기(setLayoutKind) 전에 부른다. 추천은 바로 앞에서 비웠다.
     */
    private void applyFieldLanguage(EditorInfo info, boolean restarting) {
        if (prefs.englishFields() && englishFieldFor(info)) {
            if (autoEnglishDeclined != null && (restarting || autoEnglishDeclined.equals(fieldKey(info)))) return;
            autoEnglishDeclined = null;
            if (korean || cjkLang != null) {
                commitComposing();
                autoEnglishFrom = cjkLang;
                setCjkLang(null);
                korean = false;
                autoEnglish = true;
            }
            return;
        }
        autoEnglishDeclined = null;
        if (autoEnglish) {
            autoEnglish = false;
            setCjkLang(cjkAvailable(autoEnglishFrom) ? autoEnglishFrom : null);
            korean = cjkLang == null;
            autoEnglishFrom = null;
        }
    }

    /**
     * 영어로 시작할 칸: 이메일 주소 입력란, 앱이 영어(ASCII) 입력을 요청한 칸, 첫 언어로 영어를 알려 준 칸.
     * 인터넷 주소 입력란(웹 브라우저 주소창)은 검색어도 받으므로 빼고, 숫자 등 글자가 아닌 입력란도 뺀다.
     */
    private static boolean englishFieldFor(EditorInfo info) {
        int type = info.inputType;
        if ((type & InputType.TYPE_MASK_CLASS) != InputType.TYPE_CLASS_TEXT) return false;
        int variation = type & InputType.TYPE_MASK_VARIATION;
        if (variation == InputType.TYPE_TEXT_VARIATION_URI) return false;
        if (variation == InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
                || variation == InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS) return true;
        if ((info.imeOptions & EditorInfo.IME_FLAG_FORCE_ASCII) != 0) return true;
        android.os.LocaleList hints = info.hintLocales;
        return hints != null && !hints.isEmpty() && "en".equals(hints.get(0).getLanguage());
    }

    /** 입력란을 가리키는 이름 (앱 패키지와 칸 ID). */
    private static String fieldKey(EditorInfo info) {
        return info.packageName + "#" + info.fieldId;
    }

    private static boolean passwordFieldFor(EditorInfo info) {
        int type = info.inputType;
        int cls = type & InputType.TYPE_MASK_CLASS;
        int variation = type & InputType.TYPE_MASK_VARIATION;
        if (cls == InputType.TYPE_CLASS_NUMBER) return variation == InputType.TYPE_NUMBER_VARIATION_PASSWORD;
        return cls == InputType.TYPE_CLASS_TEXT && (variation == InputType.TYPE_TEXT_VARIATION_PASSWORD
                || variation == InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
                || variation == InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD);
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
        if (keyboard == null) return;   // 외장 키보드만 쓰면 자판 화면을 만들지 않은 채 언어를 바꿀 수 있다
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
                l = KeyboardLayout.symbols(backLabel(), prefs);
                break;
            case KeyboardLayout.SYMBOLS_2:
                l = KeyboardLayout.symbols2(backLabel(), prefs);
                break;
            case KeyboardLayout.JAPANESE:
                l = KeyboardLayout.japanese(prefs, prefs.jaQwerty());
                break;
            case KeyboardLayout.CHINESE:
                l = KeyboardLayout.chinese(prefs);
                break;
            case KeyboardLayout.NUMBER:
                l = KeyboardLayout.number();
                break;
            default:
                l = KeyboardLayout.korean(prefs);
                break;
        }
        if (domainField && kind != KeyboardLayout.NUMBER) KeyboardLayout.useDomainPeriodPopup(l);
        keyboard.setLayout(l);
        boolean shiftable = kind == KeyboardLayout.ENGLISH || kind == KeyboardLayout.CHINESE
                || (kind == KeyboardLayout.JAPANESE && this.prefs.jaQwerty());
        if (!shiftable && shiftState != 0) setShift(0);
        applySuggestLead();   // 분리 키보드인지에 따라 추천 단어 자리가 달라진다
    }

    private void showPanel(int p) {
        dismissForgetDialog();   // 삭제 확인 줄이 다른 화면(검색창 등) 위에 남지 않게
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
        onAutofillChipsChanged();   // 자판이 아닌 화면에서는 칩 줄을 가린다
        searchBar.setVisibility(p == PANEL_SEARCH ? View.VISIBLE : View.GONE);
        resultScroll.setVisibility(p == PANEL_SEARCH ? View.VISIBLE : View.GONE);
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
            emojiPanel.setBackLabel(backLabel());
            emojiPanel.reset();
            suggestEmoji();
        } else if (p == PANEL_CLIPBOARD) {
            ensureClipPanel();
            clipPanel.setVisibility(View.VISIBLE);
            refreshClipboard();
        } else if (p == PANEL_SEARCH) {
            // 이모지 이름은 한글·영어로만 찾으므로 일본어·중국어에서는 검색창에만 영어 자판을 쓴다.
            if (cjkLang != null) setLayoutKind(KeyboardLayout.ENGLISH);
            else if (!lettersLayout()) setLayoutKind(lettersKind());
            keyboard.setEnterIcon(Icons.SEARCH);
            updateSearch();
        } else {
            if (cjkLang != null && layoutKind == KeyboardLayout.ENGLISH) setLayoutKind(lettersKind());   // 검색에서 돌아왔다
            EditorInfo ei = getCurrentInputEditorInfo();
            if (ei != null) keyboard.setEnterIcon(enterIcon(ei));
        }
        updatePanelSizes();
    }

    /** 도구 막대를 끈 상태로 자판을 보여 주는 중인지. 검색 패널은 도구 막대 자리에 검색창이 있어 해당하지 않는다. */
    private boolean toolbarGone() {
        // 일본어·중국어에서는 변환 후보가 뜰 자리를 늘 남겨 둔다 (후보가 뜰 때마다 자판이 움직이지 않게).
        return cjkLang == null && !prefs.toolbar() && !toolbarOnlyNow() && !((cfgSuggest && suggestAllowed) || (!shortcuts.isEmpty() && shortcutAllowed))
                && panel == PANEL_KEYBOARD && !autofillShowing() && !editShowing();
    }

    private void updatePanelSizes() {
        if (content == null) return;
        boolean full = panel == PANEL_EMOJI || panel == PANEL_CLIPBOARD;
        // 외장 키보드로 도구 막대만 보일 때는 자판 자리를 비운다. 이모지·클립보드 창은 그때도 원래 크기로 연다.
        boolean barOnly = toolbarOnlyNow();
        keyboard.setVisibility(full || barOnly ? View.GONE : View.VISIBLE);
        int h = barOnly ? 0 : keyboardHeight + (full ? toolbarHeight : 0);
        ViewGroup.LayoutParams lp = content.getLayoutParams();
        if (lp != null && lp.height != h) {
            lp.height = h;
            content.setLayoutParams(lp);
        }
        if (emojiPanel != null) setHeight(emojiPanel, keyboardHeight + toolbarHeight);
        if (clipPanel != null) setHeight(clipPanel, keyboardHeight + toolbarHeight);
        // 자판 위에 도구 막대가 있으면 키 미리보기가 그 위까지 올라갈 수 있다 (아래에 두었거나 껐으면 자판 안에서 멈춘다).
        keyboard.setOverflowTop(panel == PANEL_SEARCH ? toolbarHeight + resultStripHeight()
                : toolbarGone() || prefs.toolbarBottom() ? 0 : toolbarHeight);
    }

    private static void setHeight(View v, int h) {
        ViewGroup.LayoutParams lp = v.getLayoutParams();
        if (lp != null && lp.height != h) {
            lp.height = h;
            v.setLayoutParams(lp);
        }
    }

    /** 이모지·클립보드 패널을 만들 때 쓴 맨 위 줄 높이 (도구 막대 높이). 높이 설정이 바뀌면 패널을 다시 만든다. */
    private int emojiPanelTop, clipPanelTop;

    /**
     * 도구 막대 높이가 바뀌었으면, 예전 높이로 만든 이모지·클립보드 패널을 버린다 (다음에 열 때 새 높이로 만든다).
     * 지금 보고 있는 패널은 그대로 둔다.
     */
    private void dropStalePanels() {
        if (emojiPanel != null && emojiPanelTop != toolbarHeight && panel != PANEL_EMOJI && panel != PANEL_SEARCH) {
            // 패널만 버린다 (읽어 둔 이모지 데이터는 그대로 두고 새 패널에 다시 넘긴다).
            emojiPanel.dismissTones();
            if (content != null) content.removeView(emojiPanel);
            emojiPanel = null;
        }
        if (clipPanel != null && clipPanelTop != toolbarHeight && panel != PANEL_CLIPBOARD) releaseClipboard();
    }

    /**
     * 이모지·클립보드 창이 키보드의 여백 설정(좌우·위·아래)을 따르게 한다. 맨 위 줄은 도구 막대와 같은 좌우 여백,
     * 이모지 격자·아래 줄과 클립보드 카드는 자판 키의 가장자리에 맞춘다.
     */
    private void applyPanelInsets() {
        int l = Ui.dp(this, prefs.padLeftDp()), r = Ui.dp(this, prefs.padRightDp());
        int halfGap = Ui.dp(this, prefs.keyGapXDp()) / 2;
        int t = Ui.dp(this, prefs.padTopDp()), b = Ui.dp(this, prefs.padBottomDp());
        if (emojiPanel != null) emojiPanel.setInsets(l, r, l + halfGap, t, r + halfGap, b);
        if (clipPanel != null) clipPanel.setInsets(l, r, l + halfGap, t, r + halfGap, b);
    }

    private void ensureEmojiPanel() {
        if (emojiPanel != null) return;
        // 패널 맨 위 줄(뒤로 가기·검색창)은 도구 막대 자리라 도구 막대 높이를 따른다.
        emojiPanelTop = toolbarHeight;
        emojiPanel = new EmojiPanel(this, theme, toolbarHeight, this);
        emojiPanel.setSizeLevel(prefs.emojiSize());
        applyPanelInsets();
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

    /** 이모지 추천에 넘기는 커서 앞 글의 길이. 마지막 몇 단어만 보므로 짧게 읽는다. */
    private static final int EMOJI_SUGGEST_CONTEXT = 64;
    /** 이모지 추천을 맡길 때마다 올리는 번호 (화면 스레드에서만 쓴다). 창을 다시 열면 이전 추천을 버린다. */
    private int emojiSuggestSeq;

    /**
     * 이모지 창을 열 때 커서 앞의 글에 맞는 이모지를 골라 최근 탭 맨 위에 보여 준다.
     * 키워드를 훑는 일은 검색과 같은 스레드에서 한다 (EmojiData는 한 스레드에서만 쓴다). 비밀번호 입력란에서는 글을 읽지 않는다.
     */
    private void suggestEmoji() {
        final int seq = ++emojiSuggestSeq;
        final EmojiPanel target = emojiPanel;
        InputConnection ic = getCurrentInputConnection();
        if (target == null || ic == null || !shortcutAllowed || !prefs.emojiSuggest()) return;
        CharSequence before = ic.getTextBeforeCursor(EMOJI_SUGGEST_CONTEXT, 0);
        if (before == null || before.length() == 0) return;
        String read = before.toString();
        if (read.length() >= EMOJI_SUGGEST_CONTEXT) {
            // 앞이 잘렸을 수 있다. 잘린 첫 단어('…생일'이 아니라 '…일')로 엉뚱하게 맞추지 않도록 버린다.
            int space = 0;
            while (space < read.length() && !Character.isWhitespace(read.charAt(space))) space++;
            read = read.substring(space);
        }
        final String text = read;
        emojiRepo.load(d -> {
            if (seq != emojiSuggestSeq || target != emojiPanel || panel != PANEL_EMOJI) return;
            try {
                searchIo.execute(() -> {
                    List<String> found = d.suggest(text, 16);
                    if (found.isEmpty()) return;
                    main.post(() -> {
                        if (seq == emojiSuggestSeq && target == emojiPanel && panel == PANEL_EMOJI) {
                            target.setSuggestions(found);
                        }
                    });
                });
            } catch (java.util.concurrent.RejectedExecutionException ignored) {
                // 서비스가 끝나는 중
            }
        });
    }

    private void ensureClipPanel() {
        if (clipPanel != null) return;
        // 패널 맨 위 줄(뒤로 가기·제목·모두 지우기)은 도구 막대 자리라 도구 막대 높이를 따른다.
        clipPanelTop = toolbarHeight;
        clipPanel = new ClipboardPanel(this, theme, toolbarHeight, this);
        applyPanelInsets();
        content.addView(clipPanel, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                keyboardHeight + toolbarHeight));
    }

    // ---------------------------------------------------------------- KeyboardView.Listener

    @Override
    public void onKeyPress(Key key) {
        deleteRepeatChecked = false;
        feedback.onKey(key);
    }

    /** 밀기·길게 누르기가 인식됐다 (KeyboardView가 알려 준다). Fn 키 탭처럼 기능을 실행하는 탭은 여기로 오지 않는다. */
    @Override
    public void onGesture() {
        feedback.onGesture();
    }

    @Override
    public void onKeyRepeat(Key key) {
        feedback.onRepeat();
        lastWasSpace = false;
        // 탭이 아니라 길게 눌러 입력하는 것이라 연속 탭(쌍자음·이중모음) 판단에서 빠진다.
        typeText(shiftState != 0 && lettersLayout() ? key.shifted : key.output, false);
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
                setLayoutKind(lettersKind());
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
        // 더 지울 것이 없어 커서가 멈췄으면 진동하지 않는다 (지우기 자체는 예전처럼 보낸다).
        if (!nothingToDelete()) feedback.onRepeat();
        // 선택 영역 확인(앱에 묻고 기다리는 호출)은 연속 삭제의 첫 번째에서만 한다.
        // 첫 삭제 뒤에는 선택 영역이 사라지므로 이후에는 묻지 않아도 된다.
        handleDelete(deleteRepeatChecked);
        deleteRepeatChecked = true;
    }

    /**
     * 연속 삭제에서 더 지울 것이 없는지: 조합 중인 글자도, 선택 영역도 없이 커서가 입력란 맨 앞이다 (검색 중이면 검색어가 비었다).
     * 앱이 알려 준 커서 위치로 판단해 앱에 묻지 않는다. 위치를 모르면 지울 것이 있다고 본다.
     */
    private boolean nothingToDelete() {
        if (!composer.isEmpty() || cjkComposing()) return false;
        if (panel == PANEL_SEARCH) return search.length() == 0;
        return selStart == 0 && selEnd == 0;
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
        if (cjkComposing()) {   // 조합 중인 읽기를 모두 지운다
            cancelCjk();
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
        if (cjkComposing()) {   // 조합 중인 읽기를 모두 지운다
            cancelCjk();
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

    /**
     * 커서 이동 틱 사이의 최소 간격. 빠르게 밀면 한 번의 터치 이벤트에 여러 칸이 움직이는데, 칸마다 진동기를 부르면
     * 진동이 뭉개지고 입력란에 묻는 호출도 늘어나므로 이 간격 안의 칸은 틱 없이(묻지도 않고) 옮긴다.
     */
    private static final long CURSOR_TICK_GAP_MS = 30;
    private long lastCursorTick;

    /** 지금 커서 이동 틱을 울릴 차례인지 (마지막 틱에서 CURSOR_TICK_GAP_MS가 지났는지). */
    private boolean cursorTickDue() {
        return SystemClock.uptimeMillis() - lastCursorTick >= CURSOR_TICK_GAP_MS;
    }

    private void cursorTick() {
        lastCursorTick = SystemClock.uptimeMillis();
        feedback.onRepeat();
    }

    /** 각 방향으로 몇 칸 더 갈 수 있는지 (끝에서 방향 키를 보내 포커스가 다른 영역으로 넘어가지 않게). */
    private final CursorBudget cursorBudget = new CursorBudget();
    private final CursorBudget.TextSource cursorText = (backward, n) -> {
        InputConnection ic = getCurrentInputConnection();
        if (ic == null) return null;
        return backward ? ic.getTextBeforeCursor(n, 0) : ic.getTextAfterCursor(n, 0);
    };

    /** 좌우 이동에서 커서 위치를 직접 정할 때 쓰는 커서 주변 글. */
    private final CursorWindow cursorWindow = new CursorWindow();
    /** 이 입력란은 커서 주변 글과 위치를 알려 주지 않는다: 이번 밀기에서는 좌우도 방향 키로 옮긴다. */
    private boolean cursorWindowUnsupported;

    /**
     * 방향 키를 보낸 뒤 입력란이 그 키를 처리했다고 볼 때까지의 시간. 앱은 받은 키를 따로 줄 세워 나중에 처리하므로,
     * 그 전에 입력란에 글자나 위치를 물으면 커서가 움직이기 전의 답이 올 수 있다 (그 답을 믿으면 끝을 넘겨 키를 보낸다).
     */
    private static final long ARROW_SETTLE_MS = 100;
    private long lastArrowKey;

    /** 기억한 커서 주변 글과 칸 수를 모두 잊는다 (새로 밀기 시작할 때, 입력란이 바뀌었을 때). */
    private void forgetCursorEdges() {
        cursorBudget.reset();
        cursorWindow.reset();
        cursorWindowUnsupported = false;
    }

    /** 새로 밀어서 커서 이동을 시작했다: 그사이 커서나 글이 바뀌었을 수 있으므로 기억한 칸 수를 잊는다. */
    @Override
    public void onCursorStart() {
        forgetCursorEdges();
    }

    /** 밀어서 커서 이동(스페이스바·분리 키보드 가운데 빈 공간·글자 키): 커서가 실제로 한 칸 움직일 때마다 약한 진동(틱). */
    @Override
    public void onCursorMove(int direction) {
        if (moveCursor(direction) && cursorTickDue()) cursorTick();
    }

    /** 밀어서 커서 이동으로 한 줄 옮긴다. 실제로 움직였을 때만 약한 진동(틱)이 울린다. */
    @Override
    public void onCursorMoveVertical(int direction) {
        if (moveCursorVertical(direction) && cursorTickDue()) cursorTick();
    }

    /**
     * 커서를 왼쪽(-1)/오른쪽(+1)으로 한 칸 옮기고, 실제로 움직였는지 돌려준다. 글의 맨 앞·맨 뒤면 키를 보내지 않는다
     * (일부 앱은 끝에서 받은 방향 키로 다른 화면 영역에 포커스를 넘긴다).
     */
    private boolean moveCursor(int direction) {
        return moveCursor(direction < 0 ? CursorBudget.LEFT : CursorBudget.RIGHT,
                direction < 0 ? KeyEvent.KEYCODE_DPAD_LEFT : KeyEvent.KEYCODE_DPAD_RIGHT);
    }

    /** 커서를 위(-1)/아래(+1)로 한 줄 옮기고, 실제로 움직였는지 돌려준다 ({@link #moveCursor(int)} 참고). */
    private boolean moveCursorVertical(int direction) {
        if (!multiLineField()) return false;
        return moveCursor(direction < 0 ? CursorBudget.UP : CursorBudget.DOWN,
                direction < 0 ? KeyEvent.KEYCODE_DPAD_UP : KeyEvent.KEYCODE_DPAD_DOWN);
    }

    private boolean moveCursor(int dir, int keyCode) {
        if (panel == PANEL_SEARCH) return false;
        correctedTo = null;
        if (!composer.isEmpty() || hadComposing) {
            commitComposing();
            cursorBudget.reset();
            cursorWindow.reset();
        }
        InputConnection ic = getCurrentInputConnection();
        if (ic == null) return false;
        if (!textField()) {
            // 터미널 등 글을 알려 주지 않는 입력란: 방향 키를 그대로 앱에 넘긴다 (끝을 알 수 없고, 앱이 키를 직접 쓴다).
            sendDownUpKeyEvents(keyCode);
            mirror.forget();
            return true;
        }
        if (selStart >= 0 && selEnd >= 0 && selStart != selEnd) {
            // 선택한 글자가 있으면 방향 키처럼 선택을 풀며 그쪽 끝으로 커서를 옮긴다. 선택이 글의 맨 앞·맨 뒤에 닿아 있어도
            // 방향 키를 보내지 않으므로 포커스가 넘어가지 않는다.
            boolean back = dir == CursorBudget.LEFT || dir == CursorBudget.UP;
            int pos = back ? Math.min(selStart, selEnd) : Math.max(selStart, selEnd);
            ic.setSelection(pos, pos);
            // 입력란이 바뀐 위치를 알려 주기 전에 이어서 밀어도 선택이 풀린 것으로 보도록 바로 기억해 둔다.
            selStart = selEnd = pos;
            cursorBudget.reset();
            cursorWindow.reset();
            mirror.forget();
            return true;
        }
        if (dir <= CursorBudget.RIGHT && !cursorWindowUnsupported) {
            int to = cursorWindow.next(dir == CursorBudget.LEFT);
            if (to == CursorWindow.UNKNOWN) {
                if (arrowKeyPending()) return false;   // 위아래로 옮긴 키를 아직 처리 중일 수 있다: 이번 칸은 건너뛴다
                if (readCursorWindow(ic)) {
                    to = cursorWindow.next(dir == CursorBudget.LEFT);
                } else {
                    cursorWindowUnsupported = true;
                }
            }
            if (to >= 0) {
                ic.setSelection(to, to);
                selStart = selEnd = to;
                cursorBudget.reset();   // 위아래로 갈 수 있는 줄 수가 바뀌었을 수 있다
                mirror.forget();
                return true;
            }
            if (to == CursorWindow.EDGE) return false;
            // 아주 긴 글자 묶음 등으로 위치를 정하지 못했다: 아래의 방향 키로 옮긴다
        }
        // 방향 키로 옮긴다 (위아래, 또는 커서 위치를 알려 주지 않는 입력란의 좌우). 끝에서는 보내지 않는다.
        if (!cursorBudget.known(dir) && arrowKeyPending()) return false;
        if (!cursorBudget.take(dir, cursorText)) return false;
        sendDownUpKeyEvents(keyCode);
        lastArrowKey = SystemClock.uptimeMillis();
        cursorWindow.reset();
        mirror.forget();
        return true;
    }

    /** 방금 보낸 방향 키를 입력란이 아직 처리하지 않았을 수 있는지 ({@link #ARROW_SETTLE_MS} 참고). */
    private boolean arrowKeyPending() {
        return SystemClock.uptimeMillis() - lastArrowKey < ARROW_SETTLE_MS;
    }

    /** 커서 앞뒤 글과 커서 위치를 입력란에서 읽어 둔다 (Android 12부터). 알려 주지 않으면 false. */
    private boolean readCursorWindow(InputConnection ic) {
        if (Build.VERSION.SDK_INT < 31) return false;
        try {
            SurroundingText st = ic.getSurroundingText(CursorWindow.WINDOW, CursorWindow.WINDOW, 0);
            if (st == null || st.getOffset() < 0 || st.getSelectionStart() != st.getSelectionEnd()) return false;
            cursorWindow.set(st.getText(), st.getOffset(), st.getSelectionStart(), CursorWindow.WINDOW);
            return true;
        } catch (RuntimeException e) {
            return false;   // 이 입력란이 지원하지 않는다: 방향 키로 옮긴다
        }
    }

    /** 글을 다루는 입력란인지. 터미널 등은 글 종류를 정하지 않고(TYPE_NULL) 방향 키를 직접 받는다. */
    private boolean textField() {
        EditorInfo ei = getCurrentInputEditorInfo();
        return ei != null && (ei.inputType & InputType.TYPE_MASK_CLASS) != InputType.TYPE_NULL;
    }

    /** 한 줄짜리 입력란에서 위/아래 키를 보내면 다른 입력란으로 포커스가 넘어가므로, 줄 이동은 여러 줄 입력란에서만 한다. */
    private boolean multiLineField() {
        EditorInfo ei = getCurrentInputEditorInfo();
        return ei != null && (ei.inputType & InputType.TYPE_MASK_CLASS) == InputType.TYPE_CLASS_TEXT
                && (ei.inputType & (InputType.TYPE_TEXT_FLAG_MULTI_LINE | InputType.TYPE_TEXT_FLAG_IME_MULTI_LINE)) != 0;
    }

    // ---------------------------------------------------------------- 스페이스바를 길게 누른 뒤 밀어서 선택

    /** 선택 모드를 시작할 때의 선택 시작점 (움직이지 않는 끝). 움직이는 끝은 선택 영역의 다른 쪽이다. */
    private int selectAnchor = -1;

    @Override
    public void onSelectStart() {
        if (panel == PANEL_SEARCH) return;
        correctedTo = null;
        commitComposing();
        selectAnchor = selStart;
    }

    @Override
    public void onSelectMove(int direction) {
        if (extendSelection(direction < 0 ? KeyEvent.KEYCODE_DPAD_LEFT : KeyEvent.KEYCODE_DPAD_RIGHT,
                direction < 0, cursorTickDue())) {
            cursorTick();
        }
    }

    @Override
    public void onSelectMoveVertical(int direction) {
        if (!multiLineField()) return;
        if (extendSelection(direction < 0 ? KeyEvent.KEYCODE_DPAD_UP : KeyEvent.KEYCODE_DPAD_DOWN,
                direction < 0, cursorTickDue())) {
            cursorTick();
        }
    }

    /**
     * Shift+방향 키를 보내 선택 영역의 움직이는 끝을 옮긴다 (입력란이 Shift를 보고 선택을 넓히거나 좁힌다).
     * check면 실제로 움직일 수 있는지 돌려준다 (글의 맨 앞·맨 뒤에 닿았으면 false).
     */
    private boolean extendSelection(int keyCode, boolean backward, boolean check) {
        if (panel == PANEL_SEARCH) return false;
        InputConnection ic = getCurrentInputConnection();
        if (ic == null) return false;
        correctedTo = null;
        commitComposing();
        boolean moves = check && selectionCanMove(ic, backward);
        long t = SystemClock.uptimeMillis();
        int meta = KeyEvent.META_SHIFT_ON | KeyEvent.META_SHIFT_LEFT_ON;
        ic.sendKeyEvent(new KeyEvent(t, t, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_SHIFT_LEFT, 0, meta));
        ic.sendKeyEvent(new KeyEvent(t, t, KeyEvent.ACTION_DOWN, keyCode, 0, meta));
        ic.sendKeyEvent(new KeyEvent(t, t, KeyEvent.ACTION_UP, keyCode, 0, meta));
        ic.sendKeyEvent(new KeyEvent(t, t, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_SHIFT_LEFT, 0, 0));
        mirror.forget();
        return moves;
    }

    /** 선택 영역의 움직이는 끝이 그쪽으로 더 갈 수 있는지. 선택 영역 안쪽으로 줄어드는 쪽이면 늘 갈 수 있다. */
    private boolean selectionCanMove(InputConnection ic, boolean backward) {
        if (selStart < 0 || selEnd < 0) return true;
        int lo = Math.min(selStart, selEnd), hi = Math.max(selStart, selEnd);
        int focus = selStart == selectAnchor ? selEnd : selStart;
        if (backward) return focus > lo || lo > 0;
        if (focus < hi) return true;
        CharSequence next = ic.getTextAfterCursor(1, 0);
        return next == null || next.length() > 0;
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
        if (cjkTyping(letters)) {
            typeCjk(s);
            if (shiftState == 1 && !keyboard.isShiftHeld()) setShift(0);
            return;
        }
        if (korean && letters && s.length() == 1 && HangulComposer.isJamo(s.charAt(0))) {
            composer.input(s.charAt(0), tap, android.os.SystemClock.uptimeMillis());
            syncComposer();
        } else {
            commitComposing();
            if (s.length() == 1 && CORRECT_PUNCT.indexOf(s.charAt(0)) >= 0 && punctCorrectActive()) {
                // 문장 부호도 스페이스처럼 단어를 끝내므로 앞 단어를 고친다 (굴자. → 글자.). 기호 자판으로 바꿔 친 물음표도.
                InputConnection ic = getCurrentInputConnection();
                CharSequence before = ic == null ? null : ic.getTextBeforeCursor(32, 0);
                mirror.fill(before, 32);
                if (ic != null && tryAutoCorrect(ic, before, s)) {
                    if (shiftState == 1 && letters && !keyboard.isShiftHeld()) setShift(0);
                    scheduleSuggest();
                    return;
                }
            }
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
        if (cjkComposing() && panel != PANEL_SEARCH) {
            commitComposing();   // 병음의 '처럼 조합 영역에만 보이던 글자가 남지 않게 읽기 그대로 확정한다
            return;
        }
        if (panel == PANEL_SEARCH || (!hadComposing && composer.isEmpty())) return;
        InputConnection ic = getCurrentInputConnection();
        if (ic != null) ic.finishComposingText();
        composer.reset();
        hadComposing = false;
        mirror.forget();
    }

    private void commitComposing() {
        if (cjkComposing()) {
            CjkComposer c = cjkComposer();
            commitCjk(c.chinese() ? c.commitRaw() : c.enter());
            return;
        }
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
        if (cjkComposing()) {
            commitCjk(cjkComposer().space());
            return;
        }
        commitComposing();
        if (panel == PANEL_SEARCH) {
            commitText(" ");
            return;
        }
        InputConnection ic = getCurrentInputConnection();
        if (ic == null) return;
        long now = SystemClock.uptimeMillis();
        if (prefs.doubleSpacePeriod() && cjkLang == null && lastWasSpace && now - lastSpaceTime < 1200) {
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
            if (tryAutoCorrect(ic, before, " ")) return;
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
        if (cjkComposing()) {
            cjkComposer().backspace();
            syncCjk(null);
            return;
        }
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
        if (cjkComposing()) {   // 조합 중이면 엔터는 확정만 한다 (줄을 바꾸거나 보내지 않는다)
            commitCjk(cjkComposer().enter());
            return;
        }
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
            setLayoutKind(lettersKind());
            updateAutoCap();
        } else {
            commitComposing();
            learnCurrentWord(null);   // 기호 자판에서 ?·! 등을 치면 단어가 끝난 것이다 (글자 자판일 때 기록해 둔다)
            setLayoutKind(KeyboardLayout.SYMBOLS);
        }
    }

    /** 지구본 키: 한글 → 영어 → (실험실에서 켰으면) 일본어 → 중국어 → 한글. */
    private void toggleLanguage() {
        String now = currentLanguage(), next;
        switch (now) {
            case "ko": next = "en"; break;
            case "en": next = cfgJa ? CjkComposer.JA : cfgZh ? CjkComposer.ZH : "ko"; break;
            case CjkComposer.JA: next = cfgZh ? CjkComposer.ZH : "ko"; break;
            default: next = "ko"; break;
        }
        switchLanguage(next);
    }

    /** 지금 언어: "ko" | "en" | "ja" | "zh". */
    private String currentLanguage() {
        return cjkLang != null ? cjkLang : korean ? "ko" : "en";
    }

    private void switchLanguage(String next) {
        commitComposing();
        // 이메일 입력란 등에서 영어로 시작했어도 사용자가 직접 바꿨으면 그 선택을 따른다 (그 칸을 떠나도 되돌리지 않는다).
        if (autoEnglish) {
            EditorInfo ei = getCurrentInputEditorInfo();
            autoEnglishDeclined = ei != null ? fieldKey(ei) : "";
        }
        autoEnglish = false;
        autoEnglishFrom = null;
        setCjkLang(cjkAvailable(next) ? next : null);
        korean = cjkLang == null && !"en".equals(next);
        clearSuggestions();
        warmSuggester();
        prefs.setLanguage(currentLanguage());
        setShift(0);
        if (lettersKindOf(layoutKind)) {
            setLayoutKind(lettersKind());
        } else {
            setLayoutKind(layoutKind);   // 기호 자판의 '가/ABC' 표시만 바뀐다.
        }
        updateAutoCap();
        if (panel == PANEL_SEARCH) updateSearch();
    }

    // ---------------------------------------------------------------- 일본어·중국어 (실험실)

    /** 실험실에서 켰고 사전 팩도 있는 언어를 다시 정한다. 팩이 바뀌었으면 읽어 둔 사전을 버린다. */
    private void refreshCjkAvailability() {
        File dir = getFilesDir();
        cfgJa = prefs.cjkEnabled(CjkComposer.JA) && CjkPack.installed(dir, CjkComposer.JA) != null;
        cfgZh = prefs.cjkEnabled(CjkComposer.ZH) && CjkPack.installed(dir, CjkComposer.ZH) != null;
        if (cjkPackChanges != CjkPack.changes()) {
            cjkPackChanges = CjkPack.changes();
            cjkComposers.clear();
        }
    }

    private boolean cjkAvailable(String lang) {
        return CjkComposer.JA.equals(lang) ? cfgJa : CjkComposer.ZH.equals(lang) && cfgZh;
    }

    /** 일본어·중국어 자판으로 바꾸거나(lang) 그만 쓴다(null). 그 언어의 사전은 처음 쓸 때 백그라운드에서 읽는다. */
    private void setCjkLang(String lang) {
        if (!java.util.Objects.equals(lang, cjkLang)) {
            resetCjk();
            cjkLang = lang;
        }
        if (lang != null) ensureCjk(lang);
        updateCandidates();
    }

    private void ensureCjk(String lang) {
        if (cjkComposers.containsKey(lang) || cjkLoading.contains(lang)) return;
        cjkLoading.add(lang);
        final int gen = CjkPack.changes();
        final File dir = getFilesDir();
        runIo(() -> {
            com.alternative_studios.newswipe.cjk.ConversionDictionary d = CjkPack.load(dir, lang);
            CjkComposer c = d == null ? null : new CjkComposer(lang, d);   // 병음 음절표도 여기서 만든다
            main.post(() -> {
                cjkLoading.remove(lang);
                if (c != null && gen == CjkPack.changes()) cjkComposers.put(lang, c);
            });
        });
    }

    /** 쓰지 않는 동안 사전을 놓아 준다 (다시 쓰면 읽는다). 조합 중이면 그대로 둔다. */
    private void releaseCjk() {
        if (!cjkComposing()) cjkComposers.clear();
    }

    private CjkComposer cjkComposer() {
        return cjkLang == null ? null : cjkComposers.get(cjkLang);
    }

    /** 일본어·중국어 조합 중인 글자가 있는지. */
    private boolean cjkComposing() {
        CjkComposer c = cjkComposer();
        return c != null && !c.isEmpty();
    }

    /** 지금 친 글자를 일본어·중국어 조합기로 보낼지 (그 언어의 글자 자판에서, 이모지 검색이 아닐 때). */
    private boolean cjkTyping(boolean letters) {
        return cjkLang != null && letters && panel == PANEL_KEYBOARD
                && (layoutKind == KeyboardLayout.JAPANESE || layoutKind == KeyboardLayout.CHINESE);
    }

    /** 지금 언어의 글자 자판. */
    private int lettersKind() {
        if (CjkComposer.JA.equals(cjkLang)) return KeyboardLayout.JAPANESE;
        if (CjkComposer.ZH.equals(cjkLang)) return KeyboardLayout.CHINESE;
        return korean ? KeyboardLayout.KOREAN : KeyboardLayout.ENGLISH;
    }

    private static boolean lettersKindOf(int kind) {
        return kind == KeyboardLayout.KOREAN || kind == KeyboardLayout.ENGLISH || kind == KeyboardLayout.JAPANESE
                || kind == KeyboardLayout.CHINESE;
    }

    /** 기호 자판·이모지 창에서 글자 자판으로 돌아가는 키의 이름. */
    private String backLabel() {
        if (CjkComposer.JA.equals(cjkLang)) return "あ";
        if (CjkComposer.ZH.equals(cjkLang)) return "中";
        return korean ? "가" : "ABC";
    }

    /** 중국어 자판에서 조합을 끝내며 넣는 문장 부호는 전각으로. */
    private static final String ZH_PUNCT_FROM = ",.?!:;()", ZH_PUNCT_TO = "，。？！：；（）";
    /** 일본어 읽기에 그대로 넣는 기호 (변환하면 그대로 남는다). */
    private static final String JA_READING_MARKS = "゛、。ー「」『』（）？！…・～";

    private static boolean jaReadingText(String s) {
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            boolean kana = (c >= 'ぁ' && c <= 'ゖ') || (c >= 'ァ' && c <= 'ヶ');
            if (!kana && JA_READING_MARKS.indexOf(c) < 0) return false;
        }
        return !s.isEmpty();
    }

    /** 일본어·중국어 자판에서 친 글자. */
    private void typeCjk(String s) {
        CjkComposer c = cjkComposer();
        if (c == null) {   // 사전을 읽는 중이다 (처음 바꾼 직후 잠깐): 친 글자를 그대로 넣는다
            commitText(s);
            return;
        }
        char ch = s.length() == 1 ? s.charAt(0) : 0;
        String done;
        if (c.chinese()) {
            if ((ch >= 'a' && ch <= 'z') || (ch == '\'' && !c.isEmpty())) {
                done = c.typeLetter(ch);
            } else {   // 대문자·숫자·문장 부호: 조합 중인 병음을 첫 후보로 끝내고 그 글자를 넣는다
                int p = ch == 0 ? -1 : ZH_PUNCT_FROM.indexOf(ch);
                done = c.commitBest() + (p >= 0 ? String.valueOf(ZH_PUNCT_TO.charAt(p)) : s);
            }
        } else if (prefs.jaQwerty() && ((ch >= 'a' && ch <= 'z') || ch == '-')) {
            done = c.typeLetter(ch);
        } else if (jaReadingText(s)) {
            done = c.typeKana(s);
        } else {   // 숫자·영어 대문자·다른 기호: 조합을 그대로 확정하고 넣는다
            done = c.enter() + s;
        }
        commitCjk(done);
    }

    /** done을 확정하고(없으면 null), 조합기의 조합 영역과 후보를 입력란·후보 막대에 맞춘다. */
    private void commitCjk(String done) {
        CjkComposer c = cjkComposer();
        String comp = c == null ? "" : c.composingText();
        InputConnection ic = getCurrentInputConnection();
        if (ic != null) {
            ic.beginBatchEdit();
            if (done != null && !done.isEmpty()) {
                ic.commitText(done, 1);
                mirror.commit(done);
            } else if (comp.isEmpty() && hadComposing) {
                ic.commitText("", 1);   // 조합 중이던 읽기를 모두 지웠다
                mirror.forget();
            }
            if (!comp.isEmpty()) {
                ic.setComposingText(comp, 1);
                mirror.setComposing(comp);
            }
            ic.endBatchEdit();
        }
        hadComposing = !comp.isEmpty();
        updateCandidates();
    }

    private void syncCjk(String done) {
        commitCjk(done);
    }

    /** 조합 중인 읽기를 입력하지 않고 모두 지운다. */
    private void cancelCjk() {
        CjkComposer c = cjkComposer();
        if (c != null) c.reset();
        commitCjk(null);
    }

    /** 조합 상태만 비운다 (입력란은 건드리지 않는다: 커서를 옮겼거나 입력란을 떠났다). */
    private void resetCjk() {
        for (CjkComposer c : cjkComposers.values()) c.reset();
        updateCandidates();
    }

    /** 후보 막대(와 펼친 격자)를 조합기의 후보로 채운다. 후보가 없으면 감춘다. */
    private void updateCandidates() {
        if (candidateBar == null) return;
        CjkComposer c = cjkComposer();
        java.util.List<String> texts = new java.util.ArrayList<>();
        int highlight = -1;
        if (c != null && !c.isEmpty() && panel == PANEL_KEYBOARD) {
            for (Candidate x : c.candidates()) texts.add(x.text);
            highlight = c.highlight();
        }
        if (cjkLang != null) {
            candidateBar.setLanguage(cjkLang);
            if (candidateGrid != null) candidateGrid.setLanguage(cjkLang);
        }
        candidateBar.setCandidates(texts, highlight);
        if (candidateGrid != null) candidateGrid.setCandidates(texts, highlight);
        boolean show = !texts.isEmpty();
        if (!show) showCandidateGrid(false);
        if ((candidateBar.getVisibility() == View.VISIBLE) != show) {
            candidateBar.setVisibility(show ? View.VISIBLE : View.GONE);
            refreshToolbarVisibility();
        }
    }

    private boolean candidatesShown() {
        return candidateBar != null && candidateBar.getVisibility() == View.VISIBLE;
    }

    private final CandidateView.Listener candidateListener = new CandidateView.Listener() {
        @Override
        public void onCandidate(int index) {
            feedback.onKey(null);
            CjkComposer c = cjkComposer();
            if (c == null) return;
            showCandidateGrid(false);
            commitCjk(c.pick(index));
        }

        @Override
        public void onExpand() {
            feedback.onToolbar();
            showCandidateGrid(!candidateGridOpen);
        }
    };

    private CandidateView buildCandidateBar() {
        CandidateView v = new CandidateView(this, false);
        v.setTheme(theme);
        v.setListener(candidateListener);
        v.setVisibility(View.GONE);
        return v;
    }

    /** 후보를 자판 자리를 덮는 격자로 펼치거나 접는다. */
    private void showCandidateGrid(boolean open) {
        if (open == candidateGridOpen && (!open || candidateGrid != null)) return;
        candidateGridOpen = open;
        if (candidateBar != null) candidateBar.setExpanded(open);
        if (open && candidateGrid == null && content != null) {
            candidateGrid = new CandidateView(this, true);
            candidateGrid.setTheme(theme);
            candidateGrid.setListener(candidateListener);
            content.addView(candidateGrid, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT));
        }
        if (candidateGrid != null) {
            candidateGrid.setVisibility(open ? View.VISIBLE : View.GONE);
            if (open) {
                if (cjkLang != null) candidateGrid.setLanguage(cjkLang);
                CjkComposer c = cjkComposer();
                java.util.List<String> texts = new java.util.ArrayList<>();
                if (c != null) for (Candidate x : c.candidates()) texts.add(x.text);
                candidateGrid.setCandidates(texts, c == null ? -1 : c.highlight());
            }
        }
    }

    /** 외장 키보드에서 일본어·중국어 조합에 쓰는 키 (누른 키를 떼는 것도 앱에 보내지 않는다). */
    private final java.util.Set<Integer> hwCjkKeys = new java.util.HashSet<>();

    /**
     * 외장 키보드의 키를 일본어(로마자)·중국어(병음) 조합에 쓴다. 조합 중에는 스페이스(변환·고르기), 엔터(확정),
     * 지우기, Esc(취소), 숫자 1~9(그 후보 고르기)도 여기서 처리한다. 처리했으면 true.
     */
    private boolean hwCjkKey(int keyCode, KeyEvent event) {
        CjkComposer c = cjkComposer();
        if (c == null || !hwTextField()) return false;
        if (keyCode >= KeyEvent.KEYCODE_A && keyCode <= KeyEvent.KEYCODE_Z && !event.isShiftPressed()) {
            correctedTo = null;
            commitCjk(c.typeLetter((char) ('a' + keyCode - KeyEvent.KEYCODE_A)));
            return true;
        }
        if (c.isEmpty()) return false;
        switch (keyCode) {
            case KeyEvent.KEYCODE_SPACE:
                commitCjk(c.space());
                return true;
            case KeyEvent.KEYCODE_ENTER:
            case KeyEvent.KEYCODE_NUMPAD_ENTER:
                commitCjk(c.enter());
                return true;
            case KeyEvent.KEYCODE_DEL:
                c.backspace();
                commitCjk(null);
                return true;
            case KeyEvent.KEYCODE_ESCAPE:
                cancelCjk();
                return true;
            case KeyEvent.KEYCODE_MINUS:
                if (c.chinese() || event.isShiftPressed()) return false;
                commitCjk(c.typeLetter('-'));
                return true;
            case KeyEvent.KEYCODE_APOSTROPHE:
                if (!c.chinese() || event.isShiftPressed()) return false;
                commitCjk(c.typeLetter('\''));
                return true;
            default:
                break;
        }
        if (keyCode >= KeyEvent.KEYCODE_1 && keyCode <= KeyEvent.KEYCODE_9 && !event.isShiftPressed()) {
            int i = keyCode - KeyEvent.KEYCODE_1;
            if (i < c.candidates().size()) {
                commitCjk(c.pick(i));
                return true;
            }
        }
        return false;   // 다른 키는 조합을 확정한 뒤 앱에 넘긴다 (hwForward)
    }

    /** 외장 키보드로 글자를 조합할 입력란인지 (숫자·전화번호 입력란 등은 키를 그대로 둔다). */
    private boolean hwTextField() {
        EditorInfo ei = getCurrentInputEditorInfo();
        return ei != null && (ei.inputType & InputType.TYPE_MASK_CLASS) == InputType.TYPE_CLASS_TEXT;
    }

    // ---------------------------------------------------------------- 외장 키보드

    /** 조합 중인 글자를 확정한 뒤 입력란에 그대로 보낸 키 (뗄 때도 같이 보낸다). */
    private final java.util.Set<Integer> hwForwarded = new java.util.HashSet<>();
    /** 혼자 눌렀다 떼면 한·영을 바꾸는 키(오른쪽 Alt, 직접 지정한 Ctrl 등)를 누른 뒤 다른 키를 누르지 않았으면 그 키 코드. 아니면 0. */
    private int hwAloneKey;

    /**
     * 외장 키보드(블루투스·USB)의 키. 한글이면 글자 키를 두벌식 자모로 조합하고, 영어면 앱에 그대로 넘긴다.
     * 한·영 전환 키는 실험실 '외장 키보드'에서 고른다. 자판의 키는 입력란에 직접 보내므로 여기로 오지 않는다.
     */
    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode != hwAloneKey) hwAloneKey = 0;
        InputConnection ic = getCurrentInputConnection();
        // 뒤로 가기·볼륨 같은 시스템 키와 이모지 검색 화면에서는 예전처럼 시스템과 앱에 맡긴다.
        if (ic == null || !isHardwareKey(event) || event.isSystem() || panel == PANEL_SEARCH) {
            return super.onKeyDown(keyCode, event);
        }
        if (isAloneLangKey(keyCode)) {
            // 떼는 순간 바꾼다: 다른 키와 함께 누르면(Alt+Tab, Ctrl+C 등) 원래 키로 쓴다.
            if (event.getRepeatCount() == 0) {
                hwAloneKey = (event.getModifiers() & ~modifierMetaOf(keyCode)) == 0 ? keyCode : 0;
            }
            return super.onKeyDown(keyCode, event);
        }
        if (isLangKey(keyCode, event)) {
            if (event.getRepeatCount() == 0) hwToggleLanguage();
            return true;
        }
        if (event.isCtrlPressed() || event.isAltPressed() || event.isMetaPressed()) {
            // 단축키(Ctrl+C 등)는 앱이 처리한다. 조합 중인 글자는 먼저 확정한다.
            return hwForward(ic, keyCode, event);
        }
        if (hwCjkKey(keyCode, event)) {
            hwCjkKeys.add(keyCode);
            return true;
        }
        char jamo = hwHangulAllowed() && keyCode >= KeyEvent.KEYCODE_A && keyCode <= KeyEvent.KEYCODE_Z
                ? Dubeolsik.jamo((char) ('a' + keyCode - KeyEvent.KEYCODE_A), event.isShiftPressed()) : 0;
        if (jamo != 0) {
            correctedTo = null;
            lastWasSpace = false;
            composer.input(jamo, false, event.getEventTime());
            syncComposer();
            scheduleSuggest();
            return true;
        }
        if (keyCode == KeyEvent.KEYCODE_DEL && !composer.isEmpty()) {
            composer.backspace();
            syncComposer();
            scheduleSuggest();
            return true;
        }
        if (KeyEvent.isModifierKey(keyCode)) return super.onKeyDown(keyCode, event);
        return hwForward(ic, keyCode, event);
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        if (keyCode == hwAloneKey && hwAloneKey != 0) {
            hwAloneKey = 0;
            hwToggleLanguage();
            return super.onKeyUp(keyCode, event);   // 수정 키를 뗀 것은 앱도 알아야 한다
        }
        if (hwCjkKeys.remove(keyCode)) return true;   // 누를 때 일본어·중국어 조합에 쓴 키
        if (hwForwarded.remove(keyCode)) {
            InputConnection ic = getCurrentInputConnection();
            if (ic != null) ic.sendKeyEvent(event);
            return true;
        }
        if (!isHardwareKey(event) || event.isSystem() || panel == PANEL_SEARCH) {
            return super.onKeyUp(keyCode, event);
        }
        if (!isAloneLangKey(keyCode) && isLangKey(keyCode, event)) return true;   // 누를 때 한·영을 바꾼 키
        if (hwHangulAllowed() && keyCode >= KeyEvent.KEYCODE_A && keyCode <= KeyEvent.KEYCODE_Z
                && !event.isCtrlPressed() && !event.isAltPressed() && !event.isMetaPressed()) {
            return true;   // 누를 때 자모로 넣은 키
        }
        return super.onKeyUp(keyCode, event);
    }

    /** 리눅스 입력 장치의 한·영 키 스캔 코드 (KEY_HANGEUL). 안드로이드 키 코드가 따로 없어 기기마다 다르게 온다. */
    private static final int SCAN_HANGEUL = 122;

    /**
     * 누를 때 한·영을 바꾸는 키: 한·영 키, Shift+Space, Ctrl+Space, 직접 지정한 키.
     * 실험실 '한·영 전환 키 바꾸기'를 켰을 때만 고른 대로 쓰고, 끄면 한·영 키와 Shift+Space(와 오른쪽 Alt)로 바꾼다.
     */
    private boolean isLangKey(int keyCode, KeyEvent event) {
        boolean custom = prefs.hwLangCustom();
        if ((!custom || prefs.hwLangKey())
                && (keyCode == KeyEvent.KEYCODE_LANGUAGE_SWITCH || event.getScanCode() == SCAN_HANGEUL)) {
            return true;
        }
        if (keyCode == KeyEvent.KEYCODE_SPACE) {
            if (event.hasModifiers(KeyEvent.META_SHIFT_ON)) return !custom || prefs.hwShiftSpace();
            if (event.hasModifiers(KeyEvent.META_CTRL_ON)) return custom && prefs.hwCtrlSpace();
            return false;
        }
        if (!custom) return false;
        int customKey = prefs.hwCustomKey(), scan = prefs.hwCustomScan();
        boolean hit = customKey != 0 ? keyCode == customKey
                : scan != 0 && keyCode == KeyEvent.KEYCODE_UNKNOWN && event.getScanCode() == scan;
        return hit && event.getModifiers() == 0;
    }

    /** 혼자 눌렀다 떼면 한·영을 바꾸는 수정 키: 오른쪽 Alt, 또는 직접 지정한 수정 키(오른쪽 Ctrl 등). */
    private boolean isAloneLangKey(int keyCode) {
        if (!KeyEvent.isModifierKey(keyCode)) return false;
        boolean custom = prefs.hwLangCustom();
        return (keyCode == KeyEvent.KEYCODE_ALT_RIGHT && (!custom || prefs.hwRightAlt()))
                || (custom && keyCode == prefs.hwCustomKey());
    }

    /** 수정 키를 누르고 있을 때 켜지는 메타 상태 (다른 수정 키 없이 그 키 혼자 눌렸는지 볼 때 쓴다). */
    private static int modifierMetaOf(int keyCode) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_ALT_LEFT: case KeyEvent.KEYCODE_ALT_RIGHT:
                return KeyEvent.META_ALT_MASK;
            case KeyEvent.KEYCODE_CTRL_LEFT: case KeyEvent.KEYCODE_CTRL_RIGHT:
                return KeyEvent.META_CTRL_MASK;
            case KeyEvent.KEYCODE_SHIFT_LEFT: case KeyEvent.KEYCODE_SHIFT_RIGHT:
                return KeyEvent.META_SHIFT_MASK;
            case KeyEvent.KEYCODE_META_LEFT: case KeyEvent.KEYCODE_META_RIGHT:
                return KeyEvent.META_META_MASK;
            case KeyEvent.KEYCODE_SYM: return KeyEvent.META_SYM_ON;
            case KeyEvent.KEYCODE_FUNCTION: return KeyEvent.META_FUNCTION_ON;
            default: return 0;
        }
    }

    /**
     * 한글 조합과 상관없는 키. 조합 중인 글자가 없으면 앱이 그대로 받게 두고, 있으면 먼저 확정한 뒤 같은 입력 통로로 보낸다
     * (그냥 넘기면 키가 확정한 글자보다 먼저 앱에 닿아 '안녕 '이 '안 녕'처럼 될 수 있다).
     */
    private boolean hwForward(InputConnection ic, int keyCode, KeyEvent event) {
        mirror.forget();   // 앱이 키로 무엇을 바꿀지 모른다
        if (composer.isEmpty() && !hadComposing) return super.onKeyDown(keyCode, event);
        commitComposing();
        ic.sendKeyEvent(event);
        hwForwarded.add(keyCode);
        return true;
    }

    /** 외장 키보드에서 온 키인지 (화면 키보드나 가상 장치에서 온 키는 건드리지 않는다). */
    private static boolean isHardwareKey(KeyEvent event) {
        android.view.InputDevice d = event.getDevice();
        return d != null && !d.isVirtual() && event.isFromSource(android.view.InputDevice.SOURCE_KEYBOARD)
                && d.getKeyboardType() == android.view.InputDevice.KEYBOARD_TYPE_ALPHABETIC;
    }

    /** 지금 입력란에 외장 키보드로 한글을 넣을지. 숫자·전화번호 입력란 등에서는 키를 그대로 둔다. */
    private boolean hwHangulAllowed() {
        if (!korean) return false;
        EditorInfo ei = getCurrentInputEditorInfo();
        return ei != null && (ei.inputType & InputType.TYPE_MASK_CLASS) == InputType.TYPE_CLASS_TEXT;
    }

    /** 외장 키보드로 한·영을 바꾼다. 자판이 보이지 않으면(도구 막대만 보일 때도) 바뀐 언어를 짧게 알려 준다 (실험실에서 끌 수 있다). */
    private void hwToggleLanguage() {
        // 외장 키보드의 한·영 키는 한글과 영어만 오간다 (일본어·중국어에서 누르면 한글로).
        switchLanguage(korean ? "en" : "ko");
        if (prefs.hwToast() && (!isInputViewShown() || toolbarOnlyNow())) Toast.makeText(this, korean ? "한글" : "English", Toast.LENGTH_SHORT).show();
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

    /** 한국어 추천 순위에 쓰는 입력란 종류: 검색 칸(주소창, 검색 동작)에서는 조사가 붙은 말을 뒤로 미룬다. */
    private static int fieldKindFor(EditorInfo info) {
        int variation = info.inputType & InputType.TYPE_MASK_VARIATION;
        int action = info.imeOptions & EditorInfo.IME_MASK_ACTION;
        if ((info.inputType & InputType.TYPE_MASK_CLASS) != InputType.TYPE_CLASS_TEXT) return WordSuggester.FIELD_NORMAL;
        if (variation == InputType.TYPE_TEXT_VARIATION_URI || action == EditorInfo.IME_ACTION_SEARCH) {
            return WordSuggester.FIELD_SEARCH;
        }
        return WordSuggester.FIELD_NORMAL;
    }

    /** 학습한 단어와 쓴 기록을 저장하고 메모리에서 내린다 (백그라운드 스레드에서). */
    private void releaseLearned() {
        userWords.release();
        usageHistory.release();
    }

    /** 이메일 주소나 인터넷 주소(웹 브라우저의 주소창 등)를 적는 입력란인지. */
    private static boolean domainFieldFor(EditorInfo info) {
        int type = info.inputType;
        if ((type & InputType.TYPE_MASK_CLASS) != InputType.TYPE_CLASS_TEXT) return false;
        int variation = type & InputType.TYPE_MASK_VARIATION;
        return variation == InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
                || variation == InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS
                || variation == InputType.TYPE_TEXT_VARIATION_URI;
    }

    /** 단축어는 비밀번호 입력란만 빼고 쓴다 (이메일 주소 입력란에서도 쓴다). */
    private static boolean shortcutAllowedFor(EditorInfo info) {
        int type = info.inputType;
        if ((type & InputType.TYPE_MASK_CLASS) != InputType.TYPE_CLASS_TEXT) return false;
        switch (type & InputType.TYPE_MASK_VARIATION) {
            case InputType.TYPE_TEXT_VARIATION_PASSWORD:
            case InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD:
            case InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD:
                return false;
            default:
                return true;
        }
    }

    private final Runnable suggestRunnable = this::updateSuggestions;

    /** 지금 입력란에서 단어 추천을 쓰는지. */
    private boolean suggestOn() {
        return cfgSuggest && suggestAllowed && panel == PANEL_KEYBOARD && cjkLang == null;
    }

    /** 지금 입력란에서 단축어를 쓰는지 (정해 둔 단축어가 있을 때). */
    private boolean shortcutsOn() {
        return cfgShortcuts && !shortcuts.isEmpty() && shortcutAllowed && panel == PANEL_KEYBOARD && cjkLang == null;
    }

    /** 지금 입력란에서 사전을 쓰는 기능(추천·자동 수정·학습)을 쓰는지. */
    private boolean wordsOn() {
        return cfgWords && suggestAllowed && panel == PANEL_KEYBOARD && cjkLang == null;
    }

    private boolean autoCorrectActive() {
        return cfgAutoCorrect && wordsOn() && lettersLayout();
    }

    /** 문장 부호로 앞 단어를 자동 수정하는지: 글자 자판과 기호 자판 (숫자 자판은 아니다). */
    private boolean punctCorrectActive() {
        KeyboardLayout l = keyboard == null ? null : keyboard.getLayout();
        return cfgAutoCorrect && wordsOn() && l != null
                && (l.isLetters() || l.kind == KeyboardLayout.SYMBOLS || l.kind == KeyboardLayout.SYMBOLS_2);
    }

    private boolean learningOn() {
        return cfgLearn && wordsOn() && lettersLayout();
    }

    /** 지금 언어의 사전(과 학습한 단어)을 백그라운드에서 읽어 둔다. 이미 읽었거나 읽는 중이면 아무것도 하지 않는다. */
    private void warmSuggester() {
        if (!cfgWords || !suggestAllowed || windowHidden || cjkLang != null) return;   // 일본어·중국어는 단어 추천을 쓰지 않는다
        final boolean lang = korean;
        if (lang ? warmingKo : warmingEn) return;
        if (suggester.isSettled(lang) && (!cfgLearn || (userWords.isLoaded() && suggester.historyLoaded()))
                && (!lang || suggester.chosungReady())) return;
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
        if ((!suggestOn() && !shortcutsOn()) || !lettersLayout()) {
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
        if ((!suggestOn() && !shortcutsOn()) || windowHidden || !lettersLayout()) return;
        InputConnection ic = getCurrentInputConnection();
        if (ic == null) return;
        String word = wordBeforeCursor(ic);
        // 입력 중인 단어가 정해 둔 단축어면 기존 추천은 모두 숨기고 그 문장이 추천란 전체를 채운다.
        String phrase = shortcutsOn() ? shortcuts.lookup(word) : null;
        if (phrase != null) {
            applySuggestions(word, new String[]{phrase}, true, -1, false);
            return;
        }
        if (!suggestOn()) {
            showSuggestions(word, new String[0]);
            return;
        }
        // 입력 중인 단어가 없으면 한글은 앞 단어 다음에 올 말을 예측한다 (설정에서 켰을 때).
        boolean predict = word.isEmpty() && korean && cfgPredict;
        if (word.isEmpty() && !predict) {
            showSuggestions(word, new String[0]);
            return;
        }
        if (!suggester.isSettled(korean)) {
            // 사전을 아직 읽지 않았다 (처음이거나 한동안 내려 두었다). 다 읽으면 다시 찾는다. 화면은 기다리지 않는다.
            warmSuggester();
            showSuggestions(word, new String[0]);
            return;
        }
        // 학습한 단어·쓴 기록을 다시 읽어 둔다 (설정에서 고친 뒤 등)
        if (cfgLearn && (!userWords.isLoaded() || !suggester.historyLoaded())) warmSuggester();
        String prev = korean ? prevWordBeforeCursor(ic) : null;
        if (predict) {
            WordSuggester.Suggestions predicted = suggester.predictIfLoaded(prev, fieldKind);
            if (predicted.words.length > 0) {
                predictedAfter = prev;
                predictedWords = predicted.words;
            }
            showSuggestions(word, predicted);
            return;
        }
        // 자동 수정이 켜져 있으면 스페이스에서 고칠 단어를 맨 앞에 굵게 보인다 (이번에 되돌린 단어는 고치지 않으니 보이지 않는다).
        syncIgnoredWithUserWords();
        boolean preview = autoCorrectActive() && !correctionIgnored.contains(word);
        showSuggestions(word, suggester.suggestIfLoaded(word, prev, korean, fieldKind, preview));
    }

    /** 입력 중인 단어 바로 앞의 한글 단어 (없으면 null). 따라 적은 글자로 알 수 없을 때만 앱에 묻는다. */
    private String prevWordBeforeCursor(InputConnection ic) {
        String all = mirror.text();
        if (all != null) return WordSuggester.previousWord(all);
        CharSequence before = ic.getTextBeforeCursor(TextMirror.KEEP, 0);
        mirror.fill(before, TextMirror.KEEP);
        return WordSuggester.previousWord(before);
    }

    private void showSuggestions(String word, String[] found) {
        applySuggestions(word, found, false, -1, false);
    }

    private void showSuggestions(String word, WordSuggester.Suggestions s) {
        applySuggestions(word, s.words, false, s.correction, s.prediction);
    }

    /** 단축어의 문장이 추천란 전체를 채울 때는 첫 칸만 보이고 길면 뒤를 …로 줄인다. 아니면 칸 모두 같은 폭이고 길면 앞을 줄인다. */
    private void setShortcutLook(boolean on) {
        if (suggestBar == null) return;
        applySuggestLead();
        suggestViews[0].setEllipsize(on ? TextUtils.TruncateAt.END : TextUtils.TruncateAt.START);
        for (int i = 1; i < suggestViews.length; i++) suggestViews[i].setVisibility(on ? View.GONE : View.VISIBLE);
    }

    /** 분리 키보드를 쓰는 중인지 (가로 모드·대화면에서 분리를 켰고 한 손 모드가 아닐 때). */
    private boolean splitActive() {
        return Prefs.ONE_HAND_OFF.equals(activeOneHand()) && prefs.profileView(profile()).splitView();
    }

    /** 분리 키보드에서 추천 단어 한 칸의 폭 (dp). 추천 단어는 이 폭에서 앞이 …로 줄어든다. */
    private static final int SPLIT_SUGGEST_DP = 104;

    /**
     * 분리 키보드에서는 추천 단어 세 개를 정해 둔 폭으로 오른쪽 끝에 붙여, 오른쪽 엄지로 고르기 쉽게 한다
     * (앞의 빈 칸이 남는 폭을 모두 가진다). 단축어의 문장도 이 오른쪽 자리(세 칸 폭 전체)에만 나온다.
     * 분리 키보드가 아니면 세 칸이 같은 폭이고, 단축어의 문장은 추천란 전체를 채운다.
     */
    private void applySuggestLead() {
        if (suggestLead == null) return;
        boolean split = splitActive();
        setLeadWeight(suggestLead, split ? 1f : 0f);
        for (int i = 0; i < suggestViews.length; i++) {
            TextView v = suggestViews[i];
            if (v == null) continue;
            LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) v.getLayoutParams();
            int one = Ui.dp(this, SPLIT_SUGGEST_DP);
            int width = !split ? 0 : shortcutShown && i == 0 ? one * suggestViews.length : one;
            float weight = split ? 0f : 1f;
            if (lp.width != width || lp.weight != weight) {
                lp.width = width;
                lp.weight = weight;
                v.setLayoutParams(lp);
            }
        }
    }

    private static void setLeadWeight(View lead, float weight) {
        LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) lead.getLayoutParams();
        if (lp.weight != weight) {
            lp.weight = weight;
            lead.setLayoutParams(lp);
        }
    }

    /**
     * @param correction 스페이스에서 자동 수정될 단어의 칸 (굵게 보인다, 없으면 -1)
     * @param prediction 입력 중인 단어 없이 예측한 다음 단어인지
     */
    private void applySuggestions(String word, String[] found, boolean shortcut, int correction, boolean prediction) {
        suggestWord = word;
        predictionShown = prediction && found.length > 0;
        if (shortcut == shortcutShown && correction == correctionShown
                && java.util.Arrays.equals(found, suggestions)) return;   // 그대로면 다시 그리지 않는다
        boolean wasShowing = suggestions.length > 0;
        suggestions = found;
        correctionShown = correction;
        if (shortcut != shortcutShown) {
            shortcutShown = shortcut;
            setShortcutLook(shortcut);
        }
        if (suggestBar == null) return;
        for (int i = 0; i < suggestViews.length; i++) {
            TextView t = suggestViews[i];
            CharSequence text = i < found.length ? found[i] : "";
            if (!TextUtils.equals(t.getText(), text)) t.setText(text);
            Typeface face = i == correction ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT;
            if (t.getTypeface() != face) t.setTypeface(face);
            t.setEnabled(i < found.length);
        }
        boolean showing = found.length > 0;
        if (showing != wasShowing) {
            applySuggestBarVisibility();
            refreshToolbarVisibility();
        }
    }

    private void clearSuggestions() {
        if (main != null) main.removeCallbacks(suggestRunnable);
        suggestWord = "";
        predictionShown = false;
        if (suggestions.length == 0) return;
        suggestions = new String[0];
        correctionShown = -1;
        if (shortcutShown) {
            shortcutShown = false;
            setShortcutLook(false);
        }
        if (suggestBar != null) {
            for (TextView t : suggestViews) t.setText("");
            suggestBar.setVisibility(View.GONE);
        }
        refreshToolbarVisibility();
    }

    /** 자동 완성 칩이 떠 있는지 (자판 화면에서만). */
    private boolean autofillShowing() {
        return autofill != null && autofill.shown() && panel == PANEL_KEYBOARD;
    }

    /**
     * 선택 편집 칩이 떠 있는지: 자판 화면에서 글자를 선택했을 때 (설정에서 껐거나 자동 완성 칩이 떠 있으면 뜨지 않는다).
     */
    private boolean editShowing() {
        return editBar != null && panel == PANEL_KEYBOARD && prefs.selectionChips() && !autofillShowing()
                && selStart >= 0 && selEnd >= 0 && selStart != selEnd;
    }

    /** 추천 단어 줄은 추천이 있고 자동 완성 칩·선택 편집 칩이 없을 때만 보인다 (칩이 같은 자리를 쓴다). */
    private void applySuggestBarVisibility() {
        if (suggestBar != null) {
            suggestBar.setVisibility(suggestions.length > 0 && !autofillShowing() && !editShowing()
                    ? View.VISIBLE : View.GONE);
        }
    }

    /** 자동 완성 칩이나 선택 편집 칩이 생기거나 사라졌다: 추천 단어 줄과 도구 버튼, 도구 막대 자리를 다시 맞춘다. */
    private void onAutofillChipsChanged() {
        applySuggestBarVisibility();
        refreshToolbarVisibility();
        applyTopSlotVisibility();
    }

    /** 도구 막대는 추천이나 자동 완성 칩이 떠 있거나 검색 중이거나 설정에서 껐으면 가린다. */
    private void refreshToolbarVisibility() {
        if (toolbar == null) return;
        // 추천 단어 줄이나 자동 완성 칩 줄이 떠 있는 동안: 오른쪽 버튼들이 접히고 그 줄이 자리를 쓴다.
        boolean candidates = candidatesShown();
        boolean editing = !candidates && editShowing();   // 조합 중에는 변환 후보가 먼저다
        if (editBar != null) editBar.setVisibility(editing ? View.VISIBLE : View.GONE);
        if (autofillBar != null && autofill != null) {
            autofillBar.setVisibility(!candidates && autofill.shown() ? View.VISIBLE : View.GONE);
        }
        boolean suggesting = (suggestBar != null && suggestBar.getVisibility() == View.VISIBLE) || autofillShowing()
                || editing || candidates;
        boolean on = prefs.toolbar() || toolbarOnlyNow();   // 도구 막대만 보일 때는 꺼 두었어도 보인다
        toolbar.setVisibility(panel == PANEL_SEARCH || !(on || suggesting) ? View.GONE : View.VISIBLE);
        // 설정에서 '도구 막대 전체를 쓰기'를 켰으면 추천이 떠 있는 동안 왼쪽 버튼들도 사라진다.
        // 선택 편집 칩은 칩 네 개가 넉넉히 들어가도록 언제나 막대 전체를 쓴다.
        boolean left = on && !(suggesting && (editing || candidates || prefs.suggestFullBar()));
        // 오른쪽 버튼들은 추천이 떠 있는 동안 사라져서 추천이 쓸 공간을 내준다.
        boolean right = on && !suggesting;
        String[][] order = toolOrder != null ? toolOrder : prefs.toolOrder();
        for (String id : order[0]) toolButtonOf(id).setVisibility(left && prefs.toolShown(id) ? View.VISIBLE : View.GONE);
        for (String id : order[1]) toolButtonOf(id).setVisibility(right && prefs.toolShown(id) ? View.VISIBLE : View.GONE);
        // 방금 복사한 내용 칩은 추천·자동 완성 칩이 없을 때 빈 공간 자리에 뜬다 (도구 버튼은 그대로 보인다).
        boolean chip = !suggesting && on && clipChipShowing();
        toolSpacer.setVisibility(suggesting || chip ? View.GONE : View.VISIBLE);
        if (clipChipSlot != null) {
            if (chip) {
                boolean masked = clipChipPreview == null || passwordField;
                clipChip.setLabel(masked ? "••••••" : clipChipPreview);
                clipChip.setContentDescription(masked ? "복사한 내용 붙여넣기" : "붙여넣기: " + clipChipPreview);
            }
            clipChipSlot.setVisibility(chip ? View.VISIBLE : View.GONE);
        }
    }

    /** 방금 복사한 내용 칩을 보여 줄 수 있는지 (자판 화면에서, 설정에서 켰을 때). */
    private boolean clipChipShowing() {
        return clipChipActive && panel == PANEL_KEYBOARD && prefs.clipboardChip();
    }

    /** 방금 복사한 텍스트로 칩을 띄운다. 민감한 정보면 내용은 기억하지 않고 가린 채 보인다. */
    private void offerClipChip(CharSequence text, boolean sensitive) {
        // 비밀번호 입력란에서 복사했으면 다른 입력란이나 앱으로 가도 가린다 (그때는 미리보기 글을 아예 기억하지 않는다).
        // 키보드가 내려가 있을 때의 복사는 다른 앱에서 한 것이라 지난 입력란을 따르지 않는다.
        boolean masked = sensitive || (passwordField && !windowHidden);
        String preview = masked ? null : clipPreview(text);
        if (!masked && preview == null) {
            dismissClipChip();   // 빈 글이나 공백만 복사했다
            return;
        }
        clipChipActive = true;
        clipChipPreview = preview;
        main.removeCallbacks(expireClipChip);
        main.postDelayed(expireClipChip, CLIP_CHIP_MS);
        refreshToolbarVisibility();
    }

    /** 칩에 보일 한 줄: 앞부분만 잘라 줄바꿈·연속 공백을 한 칸으로 줄인다. 보일 글자가 없으면 null. */
    private static String clipPreview(CharSequence text) {
        if (text == null) return null;
        CharSequence head = text.length() > 200 ? text.subSequence(0, 200) : text;
        String s = head.toString().replaceAll("\\s+", " ").trim();
        if (s.isEmpty()) return null;
        return s.length() > 60 ? s.substring(0, Character.isHighSurrogate(s.charAt(59)) ? 59 : 60) : s;
    }

    /** 칩을 거둔다 (붙여 넣었거나, 시간이 지났거나, 클립보드가 비었다). */
    private void dismissClipChip() {
        if (main != null) main.removeCallbacks(expireClipChip);
        if (!clipChipActive) return;
        clipChipActive = false;
        clipChipPreview = null;
        refreshToolbarVisibility();
    }

    /** 칩을 눌렀다: 지금 클립보드의 텍스트를 커서 자리에 붙여 넣는다 (칩에 보인 미리보기가 아니라 원래 내용 전체). */
    private void pasteClipChip() {
        CharSequence text = null;
        try {
            ClipData clip = clipboard != null ? clipboard.getPrimaryClip() : null;
            if (clip != null && clip.getItemCount() > 0) text = clip.getItemAt(0).getText();
        } catch (RuntimeException ignored) {
            // 클립보드를 읽지 못했다
        }
        dismissClipChip();
        if (text == null || text.length() == 0) return;
        correctedTo = null;
        lastWasSpace = false;
        pasteText(text.toString());
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
        if (predictionShown) {
            applyPrediction(replacement);
            return;
        }
        correctedTo = null;
        commitComposing();
        InputConnection ic = getCurrentInputConnection();
        if (ic == null) return;
        CharSequence before = ic.getTextBeforeCursor(TextMirror.KEEP, 0);
        mirror.fill(before, TextMirror.KEEP);
        String word = before == null ? "" : WordSuggester.currentWord(before, korean);
        if (word.isEmpty()) {
            // 그사이 단어가 끝났거나 커서가 옮겨졌다. 엉뚱한 글자를 지우지 않도록 넣지 않는다.
            clearSuggestions();
            scheduleSuggest();
            return;
        }
        // 설정의 '추천 단어 뒤에 공백 포함'을 따른다 (한글·영어 모두). 단축어의 문장은 늘 공백 없이 넣는다.
        boolean withSpace = prefs.suggestSpace() && !shortcutShown;
        String inserted = withSpace ? replacement + " " : replacement;
        ic.beginBatchEdit();
        ic.deleteSurroundingText(word.length(), 0);
        ic.commitText(inserted, 1);
        ic.endBatchEdit();
        mirror.replaceTail(word.length(), inserted);
        // 단축어의 문장은 학습하지 않는다
        if (!shortcutShown) {
            if (korean && cfgLearn && learnAllowed && !replacement.equals(word)) {
                runIo(() -> suggester.notePick(word, replacement));   // 같은 오타에서 여러 번 고르면 그 단어로 자동 수정한다
            }
            learnWord(replacement, korean ? WordSuggester.previousWord(before) : null, false);
        }
        lastWasSpace = withSpace;
        if (withSpace) lastSpaceTime = SystemClock.uptimeMillis();
        composer.reset();
        hadComposing = false;
        clearSuggestions();
        updateAutoCap();
    }

    /** 예측한 다음 단어를 눌렀을 때: 지울 글자 없이 커서 자리에 넣고 (설정에 따라) 띄어쓴다. */
    private void applyPrediction(String replacement) {
        correctedTo = null;
        InputConnection ic = getCurrentInputConnection();
        if (ic == null) return;
        CharSequence before = ic.getTextBeforeCursor(TextMirror.KEEP, 0);
        mirror.fill(before, TextMirror.KEEP);
        String prev = WordSuggester.previousWord(before);
        if (!composer.isEmpty() || prev == null || !WordSuggester.currentWord(before, true).isEmpty()) {
            // 그사이 글자를 쳤거나 커서가 옮겨졌다. 엉뚱한 자리에 넣지 않는다.
            clearSuggestions();
            scheduleSuggest();
            return;
        }
        boolean withSpace = prefs.suggestSpace();
        String inserted = withSpace ? replacement + " " : replacement;
        ic.commitText(inserted, 1);
        mirror.commit(inserted);
        learnWord(replacement, prev, false);
        lastWasSpace = withSpace;
        if (withSpace) lastSpaceTime = SystemClock.uptimeMillis();
        clearSuggestions();
        updateAutoCap();
    }

    /**
     * 추천을 길게 누르면: 학습한 단어면 학습한 단어에서 지운다. 기본 사전 단어는 내 입력 기록만 지운다.
     * 지운 단어는 다시 학습하지 않는다 (설정의 '학습하지 않는 단어 보기'에서 되돌린다).
     */
    private boolean forgetSuggestion(int index) {
        if (index >= suggestions.length || !cfgLearn || shortcutShown) return false;
        final String word = suggestions[index];
        final boolean lang = korean;
        if (!userWords.isKnownIfLoaded(word, lang)) {
            if (lang && usageHistory.countIfLoaded(word) > 0) {
                // 기본 사전 단어지만 내가 써서 앞으로 나온 단어: 내 입력 기록만 지워 원래 순서로 돌린다.
                feedback.onKey(null);
                Runnable forget = () -> runIo(() -> {
                    usageHistory.remove(word);
                    userWords.block(word, lang);
                    main.post(() -> {
                        Toast.makeText(this, "'" + word + "'의 입력 기록을 지웠습니다. 다시 학습하지 않습니다",
                                Toast.LENGTH_SHORT).show();
                        updateSuggestions();
                    });
                });
                if (!prefs.confirmLearnedDelete()
                        || !confirmOnBar("'" + word + "'의 입력 기록을 지울까요?", "지우기", TextUtils.TruncateAt.MIDDLE, forget)) {
                    forget.run();
                }
                return true;
            }
            Toast.makeText(this, "기본 사전의 단어라 지울 수 없습니다", Toast.LENGTH_SHORT).show();
            return true;
        }
        feedback.onKey(null);
        Runnable delete = () -> runIo(() -> {
            userWords.forget(word, lang);
            if (lang) usageHistory.remove(word);
            main.post(() -> {
                Toast.makeText(this, "'" + word + "'" + WordSuggester.objectParticle(word) + " 삭제했습니다. 다시 학습하지 않습니다",
                        Toast.LENGTH_SHORT).show();
                updateSuggestions();
            });
        });
        if (prefs.confirmLearnedDelete()) confirmForget(word, delete);
        else delete.run();
        return true;
    }

    /**
     * 도구 막대 자리 위에 덮어 보여 주는 확인 줄 (학습한 단어 삭제, 음성 입력 안내 등).
     * 따로 창(대화상자)을 띄우지 않는다. 키보드 창에 붙인 대화상자는 뜨는 순간 포커스가 옮겨 가면서
     * 시스템이 키보드를 닫아 버리는 일이 있었다.
     */
    private LinearLayout forgetBar;
    private TextView forgetText, forgetOk;
    private Runnable forgetAction;

    private void confirmForget(String word, Runnable delete) {
        confirmOnBar("'" + word + "'" + WordSuggester.objectParticle(word) + " 삭제할까요?", "삭제",
                TextUtils.TruncateAt.MIDDLE, delete);
    }

    /** 확인 줄을 띄운다. okLabel을 누르면 action을 실행한다. 도구 막대 자리가 보이지 않으면 false. */
    private boolean confirmOnBar(String message, String okLabel, TextUtils.TruncateAt ellipsize, Runnable action) {
        if (topSlot == null || !topSlot.isShown()) return false;
        if (forgetBar == null) {
            forgetBar = new LinearLayout(this);
            forgetBar.setGravity(Gravity.CENTER_VERTICAL);
            forgetBar.setBackgroundColor(theme.background);
            forgetBar.setClickable(true);   // 아래의 도구 막대·추천이 눌리지 않게
            forgetBar.setPadding(Ui.dp(this, 12), 0, Ui.dp(this, 4), 0);
            forgetText = new TextView(this);
            forgetText.setTextColor(theme.text);
            forgetText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
            forgetText.setSingleLine(true);
            forgetBar.addView(forgetText, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            forgetBar.addView(forgetButton("취소", theme.text, theme.functionKey, v -> dismissForgetDialog()));
            forgetOk = forgetButton("", theme.onAccent, theme.accent, v -> {
                Runnable a = forgetAction;
                dismissForgetDialog();
                if (a != null) a.run();
            });
            forgetBar.addView(forgetOk);
            topSlot.addView(forgetBar, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT));
        }
        forgetAction = action;
        forgetText.setEllipsize(ellipsize);
        forgetText.setText(message);
        forgetOk.setText(okLabel);
        forgetBar.setVisibility(View.VISIBLE);
        forgetBar.bringToFront();
        return true;
    }

    /** 확인 줄의 칩 버튼. 취소는 기능키 색, 확인은 강조 색 바탕이다. */
    private TextView forgetButton(String label, int color, int fill, View.OnClickListener l) {
        TextView b = Ui.chipButton(this, label, color, fill, toolbarHeightPx());
        Neu.apply(b, theme, fill, Ui.chipRadius(this));
        b.setOnClickListener(l);
        return b;
    }

    /** 확인 줄을 닫는다 (키보드가 내려가거나 다시 만들어질 때도 부른다). */
    private void dismissForgetDialog() {
        forgetAction = null;
        if (forgetBar != null) forgetBar.setVisibility(View.GONE);
    }

    /**
     * 커서 앞에 방금 끝낸 단어를 사용자 사전에 기록한다.
     * before: 이미 앱에서 읽어 둔 커서 앞 글자 (없으면 null; 따라 적은 글자를 쓰고, 그것도 없으면 앱에 묻는다).
     */
    private void learnCurrentWord(CharSequence before) {
        if (!learningOn()) return;
        String word, prev;
        if (before != null) {
            word = WordSuggester.currentWord(before, korean);
            prev = korean ? WordSuggester.previousWord(before) : null;
        } else {
            InputConnection ic = getCurrentInputConnection();
            if (ic == null) return;
            word = wordBeforeCursor(ic);
            prev = korean && !word.isEmpty() ? prevWordBeforeCursor(ic) : null;
        }
        if (korean) word = WordSuggester.learnablePart(word);   // '알겠어ㅋㅋ' → 알겠어
        if (word.isEmpty()) return;
        // 자동 수정을 되돌려 이번에 그냥 두는 단어는 되돌릴 때만 센다 (두 번 되돌려야 학습되도록).
        syncIgnoredWithUserWords();
        if (cfgAutoCorrect && correctionIgnored.contains(word)) return;
        learnWord(word, prev, false);
    }

    /** 커서 앞 글자 text의 마지막 단어를 학습한다 (입력란이 비워지기 직전에 따라 적어 둔 글자). */
    private void learnFromText(String text) {
        if (!learningOn()) return;
        String word = WordSuggester.currentWord(text, korean);
        if (korean) word = WordSuggester.learnablePart(word);
        if (word.isEmpty()) return;
        syncIgnoredWithUserWords();
        if (cfgAutoCorrect && correctionIgnored.contains(word)) return;
        learnWord(word, korean ? WordSuggester.previousWord(text) : null, false);
    }

    /** 단어 기록은 파일을 읽고 쓸 수 있어 백그라운드에서 한다. */
    /** 지금 입력란에서 단어를 학습해도 되는지 (IME_FLAG_NO_PERSONALIZED_LEARNING이 없을 때). */
    private boolean learnAllowed = true;

    /** prev = 바로 앞 단어 (한글만 쓴다, 없으면 null). */
    private void learnWord(String word, String prev, boolean strong) {
        // 이 앞 단어 다음에 예측을 보였는데 다른 말을 쳤으면 그 예측을 덜 보인다 (예측을 골랐거나 직접 쳐도 맞았으면 그대로)
        String[] shown = prev != null && prev.equals(predictedAfter) ? predictedWords : null;
        predictedAfter = null;
        predictedWords = null;
        if (!cfgLearn || !suggestAllowed || !learnAllowed || cjkLang != null) return;
        final boolean lang = korean;
        runIo(() -> {
            if (lang && shown != null) suggester.noteIgnoredPrediction(prev, shown, word);
            suggester.learn(word, prev, lang, strong);
            suggester.saveIfNeeded();
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

    /** 단어를 끝내며 자동 수정하는 문장 부호 (스페이스와 함께). */
    private static final String CORRECT_PUNCT = ".,?!~…;:";

    /**
     * 스페이스나 문장 부호(suffix)를 눌렀을 때 입력한 단어를 고칠 수 있으면 고치고 suffix를 넣는다. 고쳤으면 true.
     * before = 앱에서 읽은 커서 앞 글자.
     */
    private boolean tryAutoCorrect(InputConnection ic, CharSequence before, String suffix) {
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
        if (!suggester.isSettled(korean)) {
            // 사전을 아직 읽지 않았다 (단어 추천을 끄고 자동 수정만 쓰면 추천이 대신 읽어 주지 않는다). 이번에는 두고 읽어 둔다.
            warmSuggester();
            return false;
        }
        String prev = korean ? WordSuggester.previousWord(before) : null;
        String fixed = suggester.autoCorrect(word, prev, korean, fieldKind);
        if (fixed == null) return false;
        learnWord(fixed, prev, false);   // 고친 단어도 내가 쓴 말로 센다 (되돌리면 입력한 그대로를 다시 센다)
        replaceBeforeCursor(ic, word, fixed + suffix);
        mirror.replaceTail(word.length(), fixed + suffix);
        correctedFrom = word;
        correctedTo = fixed;
        correctedSuffix = suffix;
        lastWasSpace = false;
        clearSuggestions();
        updateAutoCap();
        return true;
    }

    /**
     * 커서 바로 앞의 글자 expected를 replacement로 바꾼다 (자동 수정과 그 되돌리기). Android 14부터는 입력란에 한 번의
     * 바꾸기(replaceText)로 맡겨, 앱의 실행 취소 기록과 화면에 지우기·넣기 두 번이 아니라 한 번의 편집으로 남게 한다.
     * 그 자리를 입력란에 다시 물어 커서 바로 앞이 정말 expected이고 위치를 알 때만 쓰며, 아니면 예전처럼 지우고 넣는다.
     */
    private void replaceBeforeCursor(InputConnection ic, String expected, String replacement) {
        int n = expected.length();
        if (Build.VERSION.SDK_INT >= 34) {
            try {
                SurroundingText st = ic.getSurroundingText(n, 0, 0);
                if (st != null && st.getOffset() >= 0 && st.getSelectionStart() == st.getSelectionEnd()) {
                    CharSequence t = st.getText();
                    int cursor = st.getSelectionStart();
                    if (cursor == t.length() && cursor >= n
                            && TextUtils.equals(t.subSequence(cursor - n, cursor), expected)) {
                        int end = st.getOffset() + cursor;
                        if (ic.replaceText(end - n, end, replacement, 1, null)) return;
                    }
                }
            } catch (RuntimeException ignored) {
                // 이 입력란이 지원하지 않는다: 아래의 예전 방식으로 바꾼다
            }
        }
        ic.beginBatchEdit();
        ic.deleteSurroundingText(n, 0);
        ic.commitText(replacement, 1);
        ic.endBatchEdit();
    }

    /**
     * 자동 수정 직후 지우기 키를 누르면 (함께 넣은 스페이스·문장 부호를 지우고) 입력한 그대로 되돌리고, 같은 단어는 이번에
     * 다시 고치지 않는다. 추천란에서 여러 번 골라 고치게 된 단어였으면 그 기억도 지운다.
     */
    private boolean undoAutoCorrect() {
        if (correctedTo == null) return false;
        String from = correctedFrom, to = correctedTo + correctedSuffix;
        correctedTo = null;
        InputConnection ic = getCurrentInputConnection();
        if (ic == null) return false;
        CharSequence before = ic.getTextBeforeCursor(to.length(), 0);
        if (before == null || !before.toString().equals(to)) return false;
        replaceBeforeCursor(ic, to, from);
        mirror.replaceTail(to.length(), from);
        syncIgnoredWithUserWords();
        correctionIgnored.add(from);
        if (korean) runIo(() -> suggester.forgetPick(from));
        learnWord(from, null, false);   // 되돌릴 때마다 한 번씩 센다. 두 번 되돌리면 학습되어 더는 고치지 않는다.
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
        feedback.onToolbar();
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

    /** 추천 단어 줄이 떠 있는지 (자판을 위로 밀어 추천 단어를 입력할 수 있을 때). */
    @Override
    public boolean suggestionsShown() {
        return panel == PANEL_KEYBOARD && suggestBar != null && suggestBar.getVisibility() == View.VISIBLE
                && suggestions.length > 0;
    }

    @Override
    public void onPickSuggestion(int slot) {
        if (!suggestionsShown()) return;
        // 단축어의 문장은 첫 칸 하나가 추천란을 채우므로 어느 자리에서 밀어도 그 문장을 입력한다.
        int index = shortcutShown ? 0 : slot;
        if (index >= suggestions.length) return;
        feedback.onKey(null);
        applySuggestion(index);
    }

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
                forgetCursorEdges();   // 한 번씩 옮기는 동작: 그사이 커서가 어디로 갔는지 모르므로 새로 센다
                moveCursor(-1);
                break;
            case SwipeAction.CURSOR_RIGHT:
                forgetCursorEdges();
                moveCursor(1);
                break;
            case SwipeAction.CURSOR_UP:
                forgetCursorEdges();
                moveCursorVertical(-1);   // 여러 줄 입력란에서만 움직인다
                break;
            case SwipeAction.CURSOR_DOWN:
                forgetCursorEdges();
                moveCursorVertical(1);
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
            case SwipeAction.SELECT_ALL_COPY:
                copySelection(true);
                break;
            case SwipeAction.COPY:
                copySelection(false);
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

    /** 선택한 글자를 복사하고, 실제로 복사했으면 키보드 위에 알린다 (선택한 글자가 없으면 아무것도 보이지 않는다). */
    private void copySelection(boolean selectAllFirst) {
        if (!beginEdit()) return;
        InputConnection ic = getCurrentInputConnection();
        if (selectAllFirst) ic.performContextMenuAction(android.R.id.selectAll);
        CharSequence selected = ic.getSelectedText(0);   // 복사하면 앱이 선택을 풀 수 있어 먼저 읽는다
        boolean hasSelection = (selected != null && selected.length() > 0) || selStart != selEnd;
        ic.performContextMenuAction(android.R.id.copy);
        endEdit();
        EditorInfo ei = getCurrentInputEditorInfo();
        main.removeCallbacks(copyRefused);
        copyPending = false;
        if (ei != null && passwordFieldFor(ei) && (selectAllFirst || hasSelection)) {
            // 비밀번호 입력란은 글자를 가렸는지(앱의 '비밀번호 보기' 등)에 따라 복사를 막기도 하고 허락하기도 한다.
            // 입력란 종류로는 알 수 없으므로, 클립보드가 실제로 바뀌는지 잠깐 기다려 보고 알린다.
            copyPending = true;
            main.postDelayed(copyRefused, COPY_WAIT_MS);
        } else if (selected != null && selected.length() > 0) {
            showMessage("복사했습니다");
        }
    }

    /** 비밀번호 입력란에서 복사를 요청하고 클립보드가 바뀌기를 기다리는 중인지. */
    private boolean copyPending;
    /** 이만큼 기다려도 클립보드가 바뀌지 않으면 입력란이 복사를 막은 것으로 본다. */
    private static final long COPY_WAIT_MS = 300;
    private final Runnable copyRefused = () -> {
        copyPending = false;
        showMessage("복사가 제한되는 내용입니다.");
    };

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
            // 켜 둔 음성 입력기의 음성 서브타입 중 지금 자판 언어(한글 = ko, 영어 = en)와 맞는 것을 먼저 고른다.
            // 언어별 서브타입이 없으면 처음 찾은 음성 서브타입을 쓴다.
            InputMethodInfo firstImi = null, matchImi = null;
            InputMethodSubtype first = null, match = null;
            String lang = cjkLang != null ? cjkLang : korean ? "ko" : "en";
            for (InputMethodInfo imi : imm.getEnabledInputMethodList()) {
                if (imi.getPackageName().equals(getPackageName())) continue;
                for (InputMethodSubtype st : imm.getEnabledInputMethodSubtypeList(imi, true)) {
                    if (!"voice".equals(st.getMode())) continue;
                    if (first == null) { first = st; firstImi = imi; }
                    if (match == null && subtypeLanguage(st).equals(lang)) { match = st; matchImi = imi; }
                }
            }
            if (match == null) { match = first; matchImi = firstImi; }
            if (match != null) {
                commitComposing();
                switchInputMethod(matchImi.getId(), match);
                return;
            }
        }
        // 켜 둔 음성 입력기가 없다: Google 음성 입력(음성 인식 및 합성 앱)을 설치하거나 켜도록 안내한다.
        if (!installed(GOOGLE_SPEECH)) {
            askOnBar("음성 입력 앱을 설치할까요?", this::openSpeechAppStore);
        } else {
            askOnBar("Google 음성 입력을 활성화해주세요", this::openInputMethodSettings);
        }
    }

    /** 서브타입 로케일의 언어 부분 (예: "ko_KR" -> "ko"). 로케일이 비어 있으면 빈 문자열. */
    private static String subtypeLanguage(InputMethodSubtype st) {
        String loc = st.getLocale();
        if (loc == null) return "";
        int i = 0;
        while (i < loc.length() && loc.charAt(i) != '_' && loc.charAt(i) != '-') i++;
        return loc.substring(0, i).toLowerCase(java.util.Locale.ROOT);
    }

    /** Google 음성 입력을 제공하는 '음성 인식 및 합성(Speech Recognition & Synthesis)' 앱. */
    private static final String GOOGLE_SPEECH = "com.google.android.tts";

    /** 도구 막대에 묻고 확인을 누르면 action을 실행한다. 도구 막대 자리가 가려져 있으면 알림만 띄우고 바로 실행한다. */
    private void askOnBar(String message, Runnable action) {
        if (confirmOnBar(message, "확인", TextUtils.TruncateAt.END, action)) return;
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
        action.run();
    }

    private boolean installed(String pkg) {
        try {
            getPackageManager().getApplicationInfo(pkg, 0);
            return true;
        } catch (android.content.pm.PackageManager.NameNotFoundException e) {
            return false;
        }
    }

    /**
     * 음성 인식 및 합성 앱의 설치 페이지를 연다. 비보 등 제조사 기기는 market:// 주소를 자체 앱스토어가 받으므로,
     * 구글 플레이 스토어 앱을 직접 지정해 바로 거기로 보낸다. 플레이 스토어가 없으면 웹 주소로 연다.
     */
    private void openSpeechAppStore() {
        Intent play = new Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=" + GOOGLE_SPEECH));
        play.setPackage(PLAY_STORE);
        if (startNewTask(play)) return;
        startNewTask(new Intent(Intent.ACTION_VIEW,
                Uri.parse("https://play.google.com/store/apps/details?id=" + GOOGLE_SPEECH)));
    }

    private static final String PLAY_STORE = "com.android.vending";

    private void openInputMethodSettings() {
        startNewTask(new Intent(android.provider.Settings.ACTION_INPUT_METHOD_SETTINGS));
    }

    /** 키보드에서 다른 화면을 연다 (서비스에서 열므로 새 작업으로). 열 수 있는 앱이 없으면 false. */
    private boolean startNewTask(Intent i) {
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            startActivity(i);
            return true;
        } catch (android.content.ActivityNotFoundException e) {
            return false;
        }
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

    /** 이모지 검색 결과 칸의 한 변. 이모지 창의 칸 높이와 같다 (기본 단계는 예전의 46dp). */
    private int resultCellPx() {
        return EmojiPanel.cellHeightPx(this, prefs.emojiSize());
    }

    /** 결과 줄 높이: 칸 + 위아래 2dp씩 (기본 단계는 예전의 50dp). */
    private int resultStripHeight() {
        return resultCellPx() + Ui.dp(this, 4);
    }

    /** 이모지 크기 설정이 바뀌면 이미 만들어 둔 결과 칸과 줄 높이를 새 크기로 맞춘다. */
    private void applyResultSize() {
        if (resultScroll == null) return;
        ViewGroup.LayoutParams slp = resultScroll.getLayoutParams();
        if (slp != null && slp.height != resultStripHeight()) {
            slp.height = resultStripHeight();
            resultScroll.setLayoutParams(slp);
        }
        float sp = EmojiPanel.textSp(prefs.emojiSize());
        int cell = resultCellPx();
        for (TextView t : resultViews) {
            t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);   // 같은 크기면 아무 일도 하지 않는다
            ViewGroup.LayoutParams lp = t.getLayoutParams();
            if (lp != null && (lp.width != cell || lp.height != cell)) {   // 설정이 바뀔 때마다 불리므로 바뀐 때만 다시 배치한다
                lp.width = cell;
                lp.height = cell;
                t.setLayoutParams(lp);
            }
        }
    }

    /** i번째 결과 칸에 emoji를 넣어 돌려준다. 모자라면 그때 만든다. */
    private View resultView(int i, String emoji) {
        while (resultViews.size() <= i) {
            TextView t = new TextView(this);
            t.setTextSize(TypedValue.COMPLEX_UNIT_SP, EmojiPanel.textSp(prefs.emojiSize()));
            t.setGravity(Gravity.CENTER);
            t.setBackground(Ui.ripple(theme.keyPressed, null, Ui.dp(this, 10)));
            t.setOnClickListener(v -> {
                feedback.onKey(null);
                onEmoji(((TextView) v).getText().toString());
            });
            t.setLayoutParams(new LinearLayout.LayoutParams(resultCellPx(), resultCellPx()));
            resultViews.add(t);
        }
        TextView t = resultViews.get(i);
        t.setText(emoji);
        return t;
    }

    // ---------------------------------------------------------------- 클립보드

    @Override
    public void onPrimaryClipChanged() {
        if (copyPending) {
            // 비밀번호 입력란에서 요청한 복사가 실제로 됐다.
            copyPending = false;
            main.removeCallbacks(copyRefused);
            showMessage("복사했습니다");
        }
        boolean history = prefs.clipboardHistory(), chip = prefs.clipboardChip();
        if ((!history && !chip) || clipboard == null) return;
        ClipData clip;
        try {
            clip = clipboard.getPrimaryClip();
        } catch (RuntimeException e) {
            return;
        }
        if (clip == null || clip.getItemCount() == 0) {
            dismissClipChip();   // 클립보드를 비웠다
            return;
        }
        ClipDescription desc = clip.getDescription();
        PersistableBundle extras = desc != null ? desc.getExtras() : null;
        boolean sensitive = extras != null && extras.getBoolean(SENSITIVE_EXTRA, false);
        ClipData.Item first = clip.getItemAt(0);
        if (chip) {
            // 방금 복사한 텍스트를 칩으로 띄운다. 비밀번호처럼 민감한 정보도 붙여 넣을 수 있게 하되 내용은 가린다.
            if (first.getUri() == null && first.getText() != null) offerClipChip(first.getText(), sensitive);
            else dismissClipChip();
        }
        // 비밀번호 관리자 등이 민감 정보로 표시한 복사와, 비밀번호 입력란에서 한 복사(글자가 보이게 바꾼 칸 포함)는
        // 클립보드 기록에 저장하지 않는다 (설정에서 '민감한 정보도 저장'을 켠 경우만 저장한다). 키보드가 내려가 있을 때의 복사는 다른 앱에서 한 것이라 지난 입력란을 따르지 않는다.
        if (!history || ((sensitive || (passwordField && !windowHidden)) && !prefs.clipboardSensitive())) return;
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
