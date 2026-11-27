# Metro UI 모드 (실험적)

설정 › 테마 맨 위의 **모드**에서 **Metro**를 고르면 (기본·Neumorphism·Metro 가운데 하나) 앱 전체(설정 화면과 키보드)의 모양과 움직임이
Windows Phone 7·8·8.1의 Metro UI처럼 바뀝니다. 이 문서는 무엇을 참고했고 어떻게 구현했는지 정리합니다.

## 참고 자료

- **microsoft-ui-xaml** (Microsoft, MIT, `github.com/microsoft/microsoft-ui-xaml`): WP8.1(WinRT XAML)의 `Windows.UI.Xaml.Phone`을
  이어받은 소스. 피벗의 숫자는 여기서 옮겼습니다 (코드는 가져오지 않았습니다).
  - `dxaml/phone/lib/Pivot_Partial.cpp`(PivotAnimator), `dxaml/xcp/components/pivot/PivotStateMachine.cpp`: 피벗
  - `dxaml/phone/lib/ThemeTransitions.cpp`, `NavigateTransitionHelper.h`(CommonNavigationTransitionInfo): 턴스타일·깃털의 시간
- **Windows Phone Toolkit** (Microsoft, Ms-PL, `github.com/microsoftarchive/WindowsPhoneToolkit`):
  턴스타일·틸트·스위블의 숫자는 이 소스에서 그대로 옮겼습니다. 코드는 가져오지 않았습니다.
  - `Transitions/Storyboards/Turnstile*.xaml`, `Effects/TurnstileFeatherEffect.cs`: 턴스타일·깃털의 각도·곡선·축. 시간은 이 값이
    실기기보다 느려 microsoft-ui-xaml의 OS 값을 씁니다 (아래 '움직임')
  - `Effects/TiltEffect.cs`: 누른 자리로 기울어지는 틸트 (WP8.1의 PointerDownThemeAnimation 값은 공개 소스에 없어 이것을 씁니다)
  - `Transitions/Storyboards/SwivelFullScreenIn.xaml`, `SlideUpFadeIn.xaml`
  - `Microsoft.Phone.Controls.Toolkit.WP8/Themes/Generic.xaml`: ToggleSwitch 템플릿
- **Windows Phone 8 SDK의 기본 리소스** (`C:\Program Files (x86)\Microsoft SDKs\Windows Phone\v8.0\Design`의 사본):
  - `ThemeResources.xaml` (Dark·Light): 테마 색, 글꼴 크기(PhoneFontSize*), 여백, PhonePivotUnselectedItemOpacity 등
  - `System.Windows.xaml`: Button(PhoneButtonBase)·ToggleButton·CheckBox·RadioButton·TextBox·Slider·ScrollViewer 템플릿
  - `Microsoft.Phone`의 `generic.xaml`: Pivot·PivotHeaderItem·PivotItem 템플릿
- 공식 문서: [Theme resources for Windows Phone](https://learn.microsoft.com/en-us/previous-versions/windows/apps/ff769552(v=vs.105)),
  [Themes for Windows Phone](https://learn.microsoft.com/en-us/previous-versions/windows/apps/ff402557(v=vs.105)),
  [App bar for Windows Phone](https://learn.microsoft.com/en-us/previous-versions/windows/apps/ff431813(v=vs.105)),
  [App bar icon buttons](https://learn.microsoft.com/en-us/previous-versions/windows/apps/ff431806(v=vs.105))
- Toolkit의 `CustomMessageBox`, `ListPicker`, `ExpanderView` 템플릿과 `Swivel*.xaml`
- 크기: WP8 화면은 480px 폭이므로 SDK의 px 값에 0.75를 곱해 dp·sp로 옮깁니다 (360dp 폭 기준, `Metro.px`·`Metro.sp`).
  여백·크기는 모두 WP8(Silverlight) 기준입니다. WP8.1(WinRT) 값은 피벗의 움직임(400 유효 픽셀 폭 기준이라 0.9를 곱한다)과,
  WP8 문서에 수치가 없는 펼친 앱 바의 자리(WP8.1 셸을 잰 값을 WP8 px로 × 1.2)에만 씁니다.
- 첨부 이미지: Windows Phone 설정 화면(피벗 머리글), Windows 10 Mobile 키보드(다크)

## 디자인 원칙과 구현

| 원칙 | 구현 |
|---|---|
| 순수한 검정·흰색 바탕, 강조색 | `AppTheme`: 바탕 `#000000`/`#FFFFFF`, 글자 `#FFFFFF`/`#212121`(검정 87%), 크롬 `#1F1F1F`/`#DDDDDD`, 옅은 글자 `#999999`. 강조색 위 글자는 흰색 (아주 밝은 색 제외) |
| 둥근 모서리·그림자·물결 없음 | `Ui.round()`가 모서리 0, `Ui.ripple()`이 눌림 색 없는 바탕(목록·메뉴 줄은 틸트로 반응), `Ui.keyPress()`는 키보드 쪽에서 누르는 동안 강조색, `Ui.chipRadius()` 0. 이 세 곳을 쓰는 화면 대부분이 한꺼번에 바뀝니다 |
| 글자 | SDK 글꼴 크기를 그대로 옮긴다 (`Metro.FONT_*`): 설정 줄의 머리글·설명 PhoneFontSizeNormal 20px → 15sp(옅은 글자), 값·입력란 MediumLarge 25.333px → 19sp, 버튼 Medium 22.667px → 17sp SemiBold, 토글의 켬/끔·묶음 머리글 Large 32px → 24sp, 페이지 제목·피벗 이름 72px → 54sp. Segoe WP 대신 `sans-serif`(-light, -medium) |
| 머리글 | SDK 페이지 템플릿(TitlePanel): 위 17px, 작은 대문자 `NEWSWIPE`(PhoneTextNormalStyle 20px), 그 아래 화면 이름(PhoneTextTitle1Style 72px, 위로 7px 당김·왼쪽 3px), 아래 28px. 글자 줄은 Silverlight TextBlock의 줄처럼 Segoe WP의 높이 1.33 em·기준선 1.0791 em에 맞춰(`Metro.segoeLine`), 이 기기 글꼴과 한글 대체 글꼴의 줄 높이에 흔들리지 않는다. 섹션이 둘 이상인 메뉴는 Pivot 템플릿이 된다: 제목 PivotTitleStyle(SemiBold 22.667px), 이름 사이 22px, 고르지 않은 이름은 다크 40%·라이트 23% (아래 '피벗') |
| 스크롤 표시 | WP8 ScrollViewer처럼 끄는 동안만 나타났다가 0.5초 뒤 사라지는 5px(→ 4dp) 폭의 PhoneDisabled 네모(다크 #666666·라이트 #B3B3B3), 누를 수 없음 (`Metro.scrollIndicator`, 네모 모양은 Android 10 이상) |
| 좌우 여백 | WP8 페이지 템플릿: ContentPanel 12px + 글(PhoneHorizontalMargin) 12px = 24px → 18dp. 머리글·피벗·목록·검색창·입력 시험 창·편집 화면·설정 창이 모두 이 선에 맞고, 대화상자의 좌우 24px와도 같다. WP8.1(WinRT) 템플릿의 Margin 19 유효 픽셀(→ 17.1dp)과도 1dp 안쪽으로 맞는다 (`Metro.pagePad`) |
| 카드 없음 | `SettingsWidgets.card()`가 바탕 없이, 구분선·화살표(`Chevron`) 숨김. 섹션 소제목은 피벗의 이름이 되어 목록에서 빠진다 |
| 묶음 머리글 | 공식 텍스트 스타일 PhoneTextGroupHeaderStyle (설정 › about의 'phone information' 같은 제목): SemiLight 32px → 24sp, PhoneSubtleBrush의 옅은 회색 소문자. 피벗이 없는 화면(키보드 사용자화와 그 설정 창, 입력 언어)의 섹션 제목과, 묶은 피벗 페이지에 남는 제목('한국어 사전', '일본어 자판'·'중국어 자판') (`SettingsWidgets.section`) |
| 하위 설정 묶음 | Toolkit ExpanderView의 Line처럼: 옅은 회색(PhoneSubtleBrush) 3px(→ 2.25dp) 직선이 하위 항목의 내용만큼만 이어진다. 위 줄의 설명 바로 아래(6dp)에서 시작해 마지막으로 보이는 하위 항목의 글 아래(6dp)에서 끝나고(하위 항목을 켜고 끌 때마다 다시 맞춘다. 마지막 하위 항목이 펼치거나 접히는 동안에는 그 위 줄의 여백도 진행에 맞춰 함께 움직여 끝에 튀지 않는다), 항목 사이 여백은 선 밖으로 옮긴다 (보일 때만 쓰는 여백이라 펼치고 접을 때 높이와 함께 움직인다). 22dp 들여 놓은 선 오른쪽 12dp에 항목 (`SettingsWidgets.subGroup`) |
| 앱 목록 타일 | 설정 메뉴 아이콘이 강조색 네모 타일 + 흰 아이콘 (`IconView`). 메뉴 이름은 WP 설정 목록처럼 PhoneTextExtraLargeStyle(42.667px → 32sp), 설명은 PhoneTextSubtleStyle(15sp), 둘 다 목록 템플릿처럼 위로 6px 당긴다 |
| 선택 버튼 줄 | SDK ToggleButton: 글자 색 3px 테두리, 고른 칸은 테두리 안을 강조색으로 채운다(테두리는 그대로). SemiBold 글자, 누름 상태 없이 틸트만 |
| 강조색 고르기 | WP8 강조색 20가지를 네모 타일 4×5로 (`Metro.ACCENTS`) |
| ToggleSwitch | 툴킷 템플릿 그대로: 트랙 89×34px에 3px 글자 색 테두리와 4px 바탕색 틈, 안을 손잡이 왼쪽까지 강조색으로 채우고, 손잡이는 20×38px 글자 색 막대(양옆 4px 바탕색). VisualTransition처럼 50ms에 옮겨 간다. 틸트는 줄 전체가 아니라 스위치(ToggleSwitchButton)에만 걸린다 |
| 토글 줄 | Toolkit ToggleSwitch 템플릿처럼 위에 옅은 머리글(설정 이름), 그 아래 왼쪽에 '켬'/'끔'(24sp Light), 오른쪽에 스위치. 설명은 WP 설정 화면처럼 그 아래 옅은 글. 템플릿의 바깥 여백(위 5px, 아래 42px → 31dp)을 그대로 두어 토글과 토글 사이가 뚜렷이 나뉜다. 값 줄·슬라이더·버튼도 아래에 같은 항목 사이 여백을 둔다 (위는 PhoneTouchTargetOverhang 12px → 9dp) (`SettingsWidgets.metroToggle`, `gapBelow`) |
| 값 줄 | Toolkit ListPicker 템플릿처럼 위에 옅은 머리글(아래 8px), 그 아래 글자 색 3px 테두리 네모 안에 지금 값(19sp, 항목의 Padding 8,10px). 드롭다운은 그 네모 자리에서 목록으로 펼쳐진다: 흰 바탕(PhoneTextBoxEditBackground)에 강조색 3px 테두리, 항목은 19sp 보통 굵기·Padding 8,10px라 접힌 네모의 글자 자리와 펼친 목록의 고른 항목이 정확히 겹친다. 고른 항목을 중심으로 위아래로 펼쳐지고(300ms EaseOut), 접힐 때는 고른 항목으로 오므라든다(220ms) (`metroPicker`, `DropdownMenu.PICKER_BOX`) |
| 슬라이더 | SDK Slider 템플릿: 12px(→ 9dp) 높이의 크롬 색 막대 왼쪽을 강조색으로, 손잡이는 12×24px 글자 색 네모 |
| 버튼 | SDK PhoneButtonBase: 글 너비만큼의 글자 색 3px 테두리 네모 (테두리 3px + Padding 10,5,10,6px), SemiBold 17sp(PhoneFontSizeMedium 22.667px), 바깥 여백 12px(→ 9dp, 이어진 버튼 사이 18dp, 다른 항목 앞에서는 일반 항목 사이만큼, 바로 아래 설명 글은 그 버튼의 설명으로 붙이고 항목 사이 여백은 설명 아래로), 누르면 강조색으로 채워지고(WP8) 꺼지면 테두리·글자가 PhoneDisabled. WP8 Button처럼 글자만 담는다(가져오기·지우기 등 오른쪽 아이콘은 Metro에서 숨긴다). 하위 묶음의 첫 버튼은 위 여백 없이 세로선과 같은 높이에서 시작하고, 하위 묶음은 다른 틀의 맨 앞에 있어도 바로 위 줄(예: 도구 막대 표시)에 붙는다. 모든 Metro 버튼이 `Metro.buttonBackground`·`buttonInk`·`buttonPadding`을 같이 쓴다 |
| 앱 바 | 화면 맨 아래 54dp(72px) 크롬 색 막대 (`Metro.AppBar`). 가운데에 36dp(48px) 동그라미 아이콘 버튼(그림은 공식 지침대로 가운데 26×26px)(검색, 키보드, 되돌리기), 오른쪽 위 점 세 개. 점을 누르거나 막대를 위로 튕기면 버튼 아래 짧은 이름과 소문자 메뉴 항목(기본값으로 되돌리기)이 밀려 올라온다. 펼친 자리는 WP8 문서에 수치가 없어, 같은 셸 앱 바를 WP8.1 에뮬레이터에서 잰 값([Cornflower](https://github.com/wdcossey/Cornflower)의 CommandBar)을 WP8 px로(×1.2) 옮겼다: 이름 SemiBold 14px, 기준선은 동그라미 아래 24px; 메뉴 SemiLight 33.5px·왼쪽 25px, 첫 기준선은 이름 기준선 아래 65.5px, 줄마다 71px, 마지막 기준선 아래 120px에서 막대 끝. 바깥·점·뒤로 가기·항목·버튼을 누르면 접힌다. 버튼은 누르는 동안 강조색 (WP8). 키보드 사용자화의 설정 창(시트)은 머리글을 덮지만 앱 바는 덮지 않으므로, 창 머리글에는 되돌리기·키보드 버튼을 두지 않고 앱 바의 키보드 버튼이 창의 입력 시험 창을 연다 |

### 움직임

| 효과 | 값 | 쓰는 곳 |
|---|---|---|
| 턴스타일 (들어오기) | 원본 -80° → 0°, ExponentialEase EaseOut 지수 6, 화면 전체 428ms / 뒤로 돌아올 때 원본 50° → 0° | 화면이 열릴 때 / 다른 화면에서 돌아올 때 |
| 턴스타일 (나가기) | 원본 0° → 50°, EaseIn 지수 6, 화면 전체 128ms 뒤 사라짐 / 뒤로: 원본 0° → -80° | 다른 화면을 열 때 / 뒤로 갈 때 |
| 축 | PlaneProjection CenterOfRotationX -0.2 (요소 폭 20% 왼쪽 바깥) | 턴스타일·깃털의 모든 항목 |
| 깃털 | 항목마다 33ms씩 늦게. 나가기: 항목마다 64ms 돌고 사라지며, 128ms가 되면 남은 항목도 모두 사라지고 다음 화면이 열린다(그래서 실제로 도는 것은 위의 네 항목). 들어오기: 항목마다 150ms, OS 시간 64 + 33k ms에 시작하고 새 화면은 128ms에 나타나므로 맨 위 두 항목은 반쯤 돌아 온 채 보인다 | 머리글·목록 줄·아래 미리보기 (실제로 보이는 것만, 들어올 때 16번째부터는 같은 차례) |
| 틸트 | 최대 0.3 rad, 가운데 25px 들어감(약 2.5%), 떼고 200ms 뒤 100ms 로그 곡선 | 메뉴 줄, 값 줄, 버튼, 선택 칸, 색 타일 |
| 스위블 | 원본 RotationX -30° → 0° (위쪽 가장자리가 뒤로 젖혀졌다 선다), 350ms, EaseOut 6 | 키보드의 이모지·클립보드·텍스트 편집 창, 드롭다운 메뉴가 열릴 때 |
| 슬라이드 | 350ms, EaseOut 6 (스프링 대신) | 키 편집 창(시트), 펼치고 접기 |

- 시스템 화면 전환은 끄고(`Metro.noSystemTransition`) 턴스타일을 직접 그립니다. 화면 전체를 `Metro.Page`가 감싸
  전환하는 동안 누르기를 막고, 다른 화면에서 돌아와 다시 보일 때 돌아오는 턴스타일을 틉니다.
  깃털은 항목의 불투명도로 차례를 기다리는 항목을 숨기므로, 화면 코드는 목록 줄 자체에 불투명도를 치지 않습니다 (흐리게 막을
  때는 줄 안의 내용에 겁니다). 턴스타일은 끝나면 항목을 돌기 전의 불투명도로 되돌립니다.
  화면이 돌아 나갈 때는 항목만 숨기고 바탕과 앱 바는 그대로 둡니다: 다음 화면이 뜨거나 화면이 닫힐 때까지 창 배경이 드러나지
  않고, 앱 바도 깜빡이지 않습니다 (WP의 앱 바도 화면과 함께 돌지 않습니다). 창 배경도 화면 바탕색으로 맞춰 둡니다
  (테마의 windowBackground는 시스템의 다크/라이트를 따르는 다른 색입니다).
  화면 모드·강조 색 등을 바꿔 화면을 다시 만들 때(recreate)는 창 배경만 새 색으로 바꾸고 내용은 그대로 두어 색이 바로 바뀝니다.
  다만 Metro에서 다른 화면을 열어 돌아 나가 있던 화면이 돌아와 다시 만들어질 때는, 그 전에 한 번 더 그려지는 옛 화면(옛 색의
  바탕·앱 바)이 깜빡이지 않게 내용을 숨기고, 다시 만든 화면은 돌아온 화면의 턴스타일로 들어옵니다.
- 뒤로 가기: Android 13 이상은 `OnBackInvokedCallback`, 12 이하는 각 화면의 `onBackPressed`가
  `SettingsFrame.metroBack()`을 부릅니다. 펼친 앱 바·검색창·입력창·키 편집 창이 열려 있으면 그쪽이 먼저 닫힙니다.
  Metro 모드에서는 시스템의 예측형 뒤로 가기 미리보기 대신 턴스타일이 보입니다.
- 각도의 부호: Silverlight·WinRT의 PlaneProjection은 Android View와 회전 방향이 반대입니다 (Toolkit의 TiltEffect는 오른쪽을 누르면
  RotationY를 음수로, 아래쪽을 누르면 RotationX를 양수로 두어 누른 쪽을 들어가게 하고, Android는 rotationY가 양수일 때 오른쪽이,
  rotationX가 양수일 때 위쪽이 들어갑니다). 그래서 원본 각도의 부호를 뒤집어 씁니다: 새 화면은 오른쪽 끝이 멀리 물러난 채 돌아
  서고, 나가는 화면은 오른쪽 끝이 앞으로 나오며 돌아 나가고, 뒤로 갈 때는 오른쪽 끝이 멀리 물러나며 사라지고, 돌아온 화면은 앞으로
  나와 있던 오른쪽 끝이 제자리로 돌아갑니다. 스위블(대화상자·키보드 창)도 같은 이유로 부호를 뒤집습니다.
- 턴스타일의 시간: 처음에는 Toolkit 값(나가기 250ms·항목마다 50ms, 들어오기 350ms·항목마다 40ms, 나가기가 마지막 항목까지 끝나야
  다음 화면)을 썼지만, 실기기의 시스템 화면보다 눈에 띄게 느렸습니다 (항목 열 개면 전환 한 번에 1.4초 남짓). Toolkit은 WP7 시절에
  앱에서 시스템 움직임을 흉내 내려 만든 것이고, OS가 직접 그리는 전환(WP8.1 `Windows.UI.Xaml.Phone`의 `CommonNavigationTransitionInfo`,
  microsoft-ui-xaml의 `dxaml/phone/lib/ThemeTransitions.cpp`·`NavigateTransitionHelper.h`)은 같은 각도를 나가기 128ms(그때 나가는 화면을
  통째로 감춤), 들어오기 428ms(깃털 항목은 나가기 64ms·들어오기 150ms, 33ms 간격)로 씁니다. 이 값이면 같은 전환이 0.5초 남짓입니다.
  공식 지침([Animations, motion, and output for Windows Phone](https://learn.microsoft.com/en-us/previous-versions/windows/apps/hh202871(v=vs.105)))도
  "Each animation serves a purpose, and gets out of the way quickly"라고 합니다.
  OS는 나가는 화면과 들어오는 화면의 전환을 함께 틀어 128ms에 넘기므로, 여기서는 나가기(128ms)가 끝나면 다음 화면을 열고 다음 화면은
  그 넘기는 순간부터 틉니다 (`Metro.ENTER_SKIP_MS`). 축은 Toolkit의 -0.2를 그대로 씁니다 (OS는 -0.1에 CenterOfRotationZ -100).
- 원근: 카메라를 화면 폭의 2.2배 거리에 둡니다. `View.setCameraDistance`는 72단위 인치를 쓰므로
  px 거리 × densityDpi / 72를 넘깁니다.

### 피벗 (가로로 밀어 한 메뉴의 섹션 사이 오가기)

공식 지침(Pivot control design guidelines, App tabs)은 피벗을 한 영역 안의 관련된 보기 사이를 오가는 데 쓰고, 페이지는 네 개 이하로,
서로 다른 작업 사이를 오가는 데는 쓰지 말라고 합니다. 그래서 피벗은 메뉴와 메뉴 사이가 아니라 **한 메뉴 안의 섹션 사이**를 오갑니다
(`SettingsActivity.installSectionPivot`, `Metro.Pivot`).

- 메뉴에 들어가면 머리글 첫 줄에 메뉴의 원래 이름(예: 추천, 학습 및 완성)이 작은 대문자로, 그 아래에 섹션 이름들이 크고 가는 소문자로 이어집니다.
  피벗이 없는 화면(정보, 키보드 사용자화 등)은 첫 줄에 NEWSWIPE, 그 아래에 짧은 메뉴 이름을 크게 씁니다. 섹션 소제목은
  목록에서 빠지고 피벗 이름이 됩니다. 섹션이 하나뿐인 메뉴는 피벗 없이 큰 제목만 쓰고 소제목을 뺍니다.
- 가로로 밀거나 머리글의 이름을 누르면 그 섹션으로 갑니다. 끝에서 밀면 처음으로 돕니다. 모든 페이지는 한 화면 안에 겹쳐 있고
  지금 페이지만 보입니다 (화면을 바꾸지 않는다).
- 움직임은 WP8.1 기준입니다. WP8.1(WinRT XAML)의 Pivot을 이어받은 Microsoft의 공개 소스
  ([microsoft-ui-xaml](https://github.com/microsoft/microsoft-ui-xaml)의 `dxaml/phone/lib/Pivot_Partial.cpp`,
  `dxaml/xcp/components/pivot/PivotStateMachine.cpp`)의 값을 옮겼습니다
  (WinRT 크기 단위는 WP8.1 폰 폭 400을 360dp에 맞춰 0.9배). 공식 디자인 가이드라인 문서에는 움직임의 수치가 없습니다.
  - 미는 동안: 페이지들이 손가락을 그대로 따라가 옆 페이지가 붙어 들어오고(DirectManipulation 패닝), 이름 줄은 한 화면을 밀 때
    이름 하나만큼 따라갑니다.
  - 손을 떼면: 관성으로 멈출 자리(지금 자리 + 속도 × 0.3초)에 가장 가까운 칸으로, 한 번에 한 칸까지(MandatorySingle 스냅 포인트)
    감속하며 붙습니다 (감속 곡선의 첫 속도가 손가락 속도와 이어지게 200~400ms, 최대 시간은 PivotStateMachine의 0.4초).
    다른 칸에 닿아 멈추면(PivotStateMachine의 InFinishTransition → Idle) 그때 고른 이름이 바뀝니다.
  - 머리글의 이름을 누르면 (PivotAnimator의 fly-out → fly-in): 지금 페이지가 83ms 동안 7px 밀려나며 67ms 만에 사라지고,
    새 페이지가 20px 옆에서 767ms 동안 KeySpline(0.1, 0.9, 0.2, 1)으로 들어오며 333ms 만에 나타납니다. 이름 줄은 850ms 동안
    같은 곡선으로 누른 이름까지 옮겨 갑니다.
  - 머리글의 이름은 멈춰 있을 때 이름마다 한 번씩만 씁니다 (섹션이 적어도 되풀이하지 않는다). 넘기는 동안에는 줄 끝에 돌아 붙을
    다음 이름을 옅은 색으로 움직인 만큼 서서히 나타나게 해(PivotHeadersControl.FadeInItemIfNeeded처럼) 넘기기가 끝날 때까지 비어
    있지 않게 하고, 오른쪽으로 밀 때는 앞 이름이 왼쪽에서 나타나며 오른쪽 끝의 같은 이름은 서서히 사라집니다.
  - 줄마다 따로 늦게 들어오는 효과(SlideInAnimationGroup)는 쓰지 않습니다.
- 설정 검색 결과나 링크로 들어오면 그 항목(또는 섹션)이 있는 페이지로 옮긴 뒤 스크롤하고 깜빡입니다. 화면을 다시 만들면(테마 변경 등)
  보던 페이지로 돌아옵니다.
- 슬라이더, 키보드·한 손 모드 미리보기, 가로 스크롤 위에서 시작한 손가락은 그 뷰가 씁니다. 설정 창(시트)이 열려 있으면 넘기지 않습니다.
- 지침과 다른 점: 지침은 피벗 안에 슬라이더·토글·입력란을 두지 말라고 하지만, 설정 화면이라 그대로 둡니다. 키보드 사용자화와
  입력 언어는 아래 미리보기·편집 창과 얽혀 있어 피벗 없이 한 목록입니다.
- 페이지가 네 개 이하가 되도록 메뉴마다 섹션을 묶습니다 (`SettingsActivity.pivotPlan`). 여러 섹션을 묶은 페이지에서 섹션 소제목은
  페이지 이름과 같거나 바로 아래 첫 설정과 이름이 같으면 빼고(되풀이), 아니면 묶음 머리글로 남깁니다
  (예: 추천 및 수정의 '한국어 사전', 외국어 자판의 '일본어 자판'·'중국어 자판').
  - 테마: 모드(디자인 모드와 라이트/다크 모드, 각각 버튼 줄 위에 옅은 머리글) · 강조 색 · 키 색
  - 추천, 학습 및 완성: 추천 및 수정(단어 추천 + 자동 수정 + 한국어 사전) · 단어 학습 · 단축어 · 자동 완성. 안내문뿐인 '개인 정보'는 뺍니다.
  - 실험실: 밀기 방향 판정 · 외국어 자판(일본어 + 중국어) · 외장 키보드 · 떠 있는 키보드

### 키보드

- 모서리 없는 네모 키, 그림자 없음 (키 곡률·키 그림자 설정은 흐리게 막힘. 저장된 값은 그대로).
- 다크: 바탕 `#1F1F1F`, 글자 키 `#333333`, 기능키(Shift·⌫·?123·엔터) `#525252`, 힌트 `#A0A0A0`.
  라이트: 바탕 `#D9D9D9`, 글자 키 흰색, 기능키 `#B8B8B8`.
- 누른 키는 강조색으로 빛나고, 누름 미리보기는 같은 폭의 강조색 네모가 키 위에 붙어 솟아오른 듯 보입니다.
- 길게 누르기 팝업은 글자 색 2dp 테두리의 네모. 키 글자는 굵게 쓰지 않습니다.
- 도구 막대 버튼은 칩 바탕 없이 아이콘만, 누르는 동안 강조색 (WP8 앱 바 버튼처럼).

- 정보 화면: 앱 아이콘 그림 없이 이름·버전과 버튼만 둔다 (꾸밈 그림을 쓰지 않는다).

## 대화상자

시스템 AlertDialog는 기기의 강조 색을 써서 설정의 강조 색과 달랐으므로, 앱의 모든 대화상자를 직접 그리는 `AppDialog`로 바꿨습니다.
`AppTheme.dialogBuilder()`·`accentBuilder()`가 돌려주는 `AppDialog.Builder`는 AlertDialog.Builder와 같은 이름·같은 동작이라
(제목, 글, 직접 만든 뷰, 목록·한 개 고르기·여러 개 고르기, 세 버튼, `getButton`, 한 개 고르기는 저절로 닫히지 않음 등) 부르는 쪽은 그대로입니다.

- 보통: 가운데의 둥근 카드(28dp), 강조색 글자 버튼, 강조색 라디오·체크. 열리고 닫히는 움직임은 시스템 대화상자 그대로입니다.
- Metro: Windows Phone Toolkit의 CustomMessageBox처럼 화면 맨 위에 폭을 꽉 채운 크롬 색 상자(상태 표시줄 뒤까지), 나머지 화면은
  바탕색 60% 막(라이트 모드는 흰 막), 같은 폭의 테두리 버튼(확인이 왼쪽, 소문자), 한 개 고르기는 표시 없이 고른 글자가 강조색,
  여러 개 고르기와 직접 만든 뷰 안의 체크박스·라디오는 WP8 체크 상자(`Metro.Check`: SDK 기본 템플릿대로 32px(→ 24dp) 투명 상자에 글자 색 3px 테두리,
  켜지면 WP8의 CheckMark 경로 `M0,123 L39,93 L124,164 L256,18 L295,49 L124,240 z`를 23×21px로. 누르는 동안 상자는 강조색, 체크는 흰색.
  라디오는 같은 모양의 동그라미에 16px 점. 한 줄 높이 72px), 입력란은 WP TextBox(`Metro.textBox`: PhoneTextBoxBrush 네모 다크 #BFBFBF·라이트 #D9D9D9,
  입력 중에는 흰 바탕에 강조색 3px 테두리, 글자 다크 검정·라이트 검정 87%, 19sp, 커서는 글자 색). 설정 화면의 검색창·입력 시험 창도 같은 TextBox.
  글은 CustomMessageBox 템플릿대로 좌우 24px, 제목(Caption) 32px SemiBold, 글 22.667px SemiLight 위 11px, 버튼은 좌우·아래 24px·사이 24px.
  SwivelBackwardIn(처음 200ms 숨었다가 원본 -30° → 0°, 150ms)으로 들어오고 SwivelBackwardOut(원본 0° → 60°, 250ms)으로 나갑니다
  (Android rotationX는 부호가 반대라 +30° → 0°, 0° → -60°).
- 성능: XML을 부풀리지 않고 뷰 몇 개를 코드로 만듭니다 (시스템 AlertDialog는 레이아웃 XML을 부풀립니다). 목록은 수십 개 이하라
  ListView 대신 줄을 바로 쌓습니다. 그림자·흐림을 쓰지 않고, Metro의 스위블만 하드웨어 층으로 그립니다.
- AlertDialog처럼 입력란이 없는 창은 키보드를 가져가지 않습니다(FLAG_ALT_FOCUSABLE_IM): 열려 있던 키보드가 그대로 남습니다.

## 드롭다운 (ListPicker)

설정에서 항목이 6개 이하인 고르기(쌍자음·이중모음 입력, 상세 배열, 진동, 키 소리 등)는 대화상자 대신 `DropdownMenu`로 고릅니다.
보통 모드에서는 줄 아래에 뜨는 Material 3 메뉴지만, Metro에서는 Windows Phone Toolkit의 ListPicker처럼 떠 있는 창 없이
그 줄 바로 아래에서 아래 내용을 밀어내며 펼쳐집니다(350ms, EaseOut 지수 6). 흰 바탕(PhoneTextBoxEditBackground)에 강조색 3px 테두리의 네모,
항목은 19sp 보통 굵기(ListPickerItem), 고른 항목은 표시 없이 강조색 글자, 누르면 틸트. 줄을 다시 누르거나 항목을 고르면 접힙니다.

## 눌림 색

Windows Phone Toolkit의 템플릿(MenuItem, ListPickerItem, ContextMenu, ToggleSwitch 등)에는 Pressed 상태가 없고, 누르면 틸트로만 반응합니다.
그래서 Metro 모드에서는 설정의 목록 줄·값 줄·메뉴에 눌림 색을 칠하지 않고 틸트만 씁니다 (`Ui.ripple`이 바탕만 돌려줌).
WP8은 그 밖의 누르는 컨트롤(버튼, 체크 상자, 앱 바 버튼, 키보드)에는 강조색을 씁니다. 그래서 키보드의 키·도구 막대 버튼·칩·이모지·클립보드 창은 `Ui.keyPress`로 누르는 동안 강조색으로 채웁니다.
테두리 네모 버튼(Button)과 체크 상자·라디오(WP8의 PhoneRadioCheckBoxPressedBrush = 강조색)는 누르는 동안 강조색으로 채워지고, 키보드의 키도 누르면 강조색으로 빛납니다.

## 설정 검색

설정 검색은 글자 크기로 항목 이름(15~18sp)과 소제목(14sp 강조색)을 가려냅니다. Metro 모드에서 크기를 바꾼 글자는
`Metro.text()`가 원래 크기를 글자의 태그(`R.id.metro_search_px`)에 남기고, 검색은 `Metro.searchPx()`로 그 크기를 봅니다.

## 아직 원래 모양인 곳

- 첫 시작 가이드의 그림과 움직임, 토스트
- 설정 화면 안에서 따로 만든 일부 조각 (공통 조각을 쓰지 않는 곳은 색과 모서리만 바뀜)
- 플로팅 키보드의 손잡이 등 작은 장식

## 원칙 점검 (콘텐츠 우선, 디지털답게, 타이포그래피, 움직임)

- 굵은 글자를 쓰지 않는다: 위계는 크기와 가는 글꼴로 (SDK 템플릿이 SemiBold인 대화상자 제목·버튼만 semibold, 고른 줄은 굵기 대신 강조색).
- 그림자·그라데이션을 쓰지 않는다: 끌어 옮기는 줄도 떠오르지 않고, 명예의 전당 무지개는 단색 띠 여섯 개로 칠한다.
- 누를 수 있는 목록 줄은 색으로 덮는 대신 틸트로 반응한다 (기능 고르기 창 포함).
- 움직임은 회전·이동만 하므로 하드웨어 층을 쓰지 않는다 (항목 십수 개가 함께 돌 때 GPU 메모리를 아낀다).
- 피벗의 옆 메뉴 내용(화면 두 개 분량)은 앱이 뒤로 가면 놓아 주고, 돌아오는 움직임이 끝난 뒤 다시 만든다.

## 도구 막대의 칩

방금 복사한 내용 칩, 글자를 선택했을 때의 편집 칩(잘라내기·복사 등), 시스템 자동 완성 칩, 학습한 단어 지우기·클립보드 지우기의 취소 칩은
도구 막대 버튼처럼 바탕 없이 아이콘·글자만 보이고, 누르는 동안 강조색으로 채워지며 글자가 강조색 위의 색으로 바뀝니다.
강조색으로 칠한 확인·지우기 칩과 고정한 핀처럼 눈에 띄어야 하는 칩은 바탕(모서리 없는 네모)을 그대로 둡니다.
