package com.alternative_studios.newswipe.keyboard;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;

/**
 * Metro UI 모드의 아이콘: {@link Icons}의 모든 아이콘을 Windows Phone 8의 글리프처럼 다시 그린다. {@link Icons#draw}가 Metro 모드에서
 * 이것을 부르므로, 키보드의 키·도구 막대·편집 칩·설정 화면·타일이 모두 같은 모양을 쓴다.
 *
 * <p>Windows Phone의 원본 아이콘(SDK의 app bar 아이콘, Segoe UI Symbol 글꼴)은 Android 앱에 옮겨 담아도 되는 사용 허락이 없어
 * 옮기지 않았다. 대신 WP8 app bar 아이콘 지침("App bar icon buttons for Windows Phone": 흰 단색, 단순한 기하 도형, 같은 굵기의 선)과
 * Segoe UI Symbol 글리프의 특징을 따라 새로 그렸다:
 * <ul>
 *   <li>모서리는 둥글리지 않는다: 선 끝은 잘린 끝(BUTT), 꺾인 곳은 뾰족하게(MITER). 둥근 모서리 네모는 각진 네모로.</li>
 *   <li>원은 원 그대로 (지구본·시계·돋보기·스마일·정보).</li>
 *   <li>선은 모두 같은 굵기({@link Icons#draw}가 받은 굵기)이고, WP 글리프처럼 꽉 찬 면(켜진 Shift, 스피커, 고정된 핀, 클립보드의 집게,
 *       플라스크의 액체)을 섞는다. 점은 둥근 점 대신 작은 네모.</li>
 *   <li>가로·세로·45° 선을 쓰고, 좌표는 24칸 안 2~22칸 (원래 아이콘과 같은 크기라 놓인 자리는 그대로다).</li>
 * </ul>
 * 좌표는 24×24 칸 기준이고, 캔버스는 {@link Icons#draw}가 이미 옮기고 돌려 두었다 (방향이 있는 아이콘은 위쪽을 향한 모양 하나를 그린다).
 */
final class MetroIcons {
    private static final Path path = new Path();
    private static final RectF rect = new RectF();

    private MetroIcons() {
    }

    /** @param icon 돌리거나 뒤집은 뒤의 모양 (Icons.shape), @param original 부른 쪽이 고른 아이콘 (켜짐·고정 상태를 가린다) */
    static void draw(Canvas c, int icon, int original, Paint p) {
        p.setStrokeCap(Paint.Cap.BUTT);
        p.setStrokeJoin(Paint.Join.MITER);
        p.setStrokeMiter(4f);
        p.setStyle(Paint.Style.STROKE);
        path.reset();
        switch (icon) {
            case Icons.SHIFT:
            case Icons.SHIFT_ON:
            case Icons.SHIFT_LOCK:
                // WP8 키보드의 Shift: 속이 빈 위 화살표, 켜지면 꽉 찬 화살표, 고정하면 그 아래 막대.
                path.moveTo(12, 3.5f);
                path.lineTo(20.5f, 12);
                path.lineTo(15.5f, 12);
                path.lineTo(15.5f, 18);
                path.lineTo(8.5f, 18);
                path.lineTo(8.5f, 12);
                path.lineTo(3.5f, 12);
                path.close();
                if (original != Icons.SHIFT) p.setStyle(Paint.Style.FILL_AND_STROKE);
                c.drawPath(path, p);
                if (original == Icons.SHIFT_LOCK) {
                    p.setStyle(Paint.Style.FILL);
                    float h = p.getStrokeWidth() / 2f;
                    c.drawRect(8.5f - h, 20.5f - h, 15.5f + h, 21.5f + h, p);
                }
                break;
            case Icons.DELETE:
                // ⌫: 왼쪽이 뾰족한 오각형 안의 ×.
                path.moveTo(8, 5);
                path.lineTo(21, 5);
                path.lineTo(21, 19);
                path.lineTo(8, 19);
                path.lineTo(1.5f, 12);
                path.close();
                c.drawPath(path, p);
                c.drawLine(11.5f, 8.5f, 18, 15.5f, p);
                c.drawLine(18, 8.5f, 11.5f, 15.5f, p);
                break;
            case Icons.ENTER:
                // ↵: 위에서 내려와 왼쪽으로 꺾이는 선과 뾰족한 화살촉.
                path.moveTo(19.5f, 4.5f);
                path.lineTo(19.5f, 14);
                path.lineTo(5, 14);
                c.drawPath(path, p);
                arrowHead(c, p, 4, 14, 180, 5.5f);
                break;
            case Icons.SEND:
                // 종이비행기: 오른쪽을 가리키는 각진 화살 날개.
                path.moveTo(3, 4);
                path.lineTo(21, 12);
                path.lineTo(3, 20);
                path.lineTo(6, 12);
                path.close();
                c.drawPath(path, p);
                c.drawLine(6, 12, 13, 12, p);
                break;
            case Icons.NEXT:
                c.drawLine(3.5f, 12, 19, 12, p);
                arrowHead(c, p, 20, 12, 0, 6.5f);
                break;
            case Icons.BACK:
                // WP의 뒤로 화살표: 가로선과 뾰족한 화살촉.
                c.drawLine(5, 12, 20.5f, 12, p);
                arrowHead(c, p, 4, 12, 180, 6.5f);
                break;
            case Icons.DONE:
                path.moveTo(4, 12.5f);
                path.lineTo(9.5f, 18);
                path.lineTo(20, 7);
                c.drawPath(path, p);
                break;
            case Icons.GLOBE: {
                // 원, 세로 타원(경선), 적도, 위아래 위선 (Segoe UI Symbol의 지구본).
                c.drawCircle(12, 12, 9, p);
                rect.set(7.5f, 3, 16.5f, 21);
                c.drawOval(rect, p);
                c.drawLine(3, 12, 21, 12, p);
                float d = 4.6f, half = (float) Math.sqrt(81 - d * d);
                c.drawLine(12 - half, 12 - d, 12 + half, 12 - d, p);
                c.drawLine(12 - half, 12 + d, 12 + half, 12 + d, p);
                break;
            }
            case Icons.EMOJI:
                // WP8 키보드의 이모티콘 키: 원 안의 네모난 눈과 웃는 입.
                c.drawCircle(12, 12, 9, p);
                p.setStyle(Paint.Style.FILL);
                c.drawRect(8, 8.5f, 10, 10.5f, p);
                c.drawRect(14, 8.5f, 16, 10.5f, p);
                p.setStyle(Paint.Style.STROKE);
                rect.set(7.5f, 8.5f, 16.5f, 16.5f);
                c.drawArc(rect, 25, 130, false, p);
                break;
            case Icons.CLIPBOARD:
                // 각진 판, 꽉 찬 집게, 글줄 둘.
                c.drawRect(5, 5, 19, 21, p);
                clip(c, p);
                c.drawLine(8.5f, 12, 15.5f, 12, p);
                c.drawLine(8.5f, 16, 13.5f, 16, p);
                break;
            case Icons.PASTE:
                c.drawRect(5, 5, 19, 21, p);
                clip(c, p);
                c.drawLine(12, 9.5f, 12, 16.5f, p);
                arrowHead(c, p, 12, 17.5f, 90, 3.5f);
                break;
            case Icons.MIC:
                // WP의 마이크: 둥근 머리, 받침 고리, 기둥, 바닥.
                rect.set(9, 3, 15, 15);
                c.drawRoundRect(rect, 3, 3, p);
                rect.set(5.5f, 6.5f, 18.5f, 17.5f);
                c.drawArc(rect, 0, 180, false, p);
                c.drawLine(12, 17.5f, 12, 21, p);
                c.drawLine(8.5f, 21, 15.5f, 21, p);
                break;
            case Icons.SETTINGS: {
                // 톱니바퀴: 곧은 변의 톱니 8개 (원호 없이 꺾은선으로), 가운데 구멍.
                float outer = 10f, inner = 7.3f, halfTop = 11f, halfBase = 15.5f;
                for (int i = 0; i < 8; i++) {
                    float mid = 45f * i;
                    if (i == 0) path.moveTo(ex(inner, mid - halfBase), ey(inner, mid - halfBase));
                    path.lineTo(ex(outer, mid - halfTop), ey(outer, mid - halfTop));
                    path.lineTo(ex(outer, mid + halfTop), ey(outer, mid + halfTop));
                    path.lineTo(ex(inner, mid + halfBase), ey(inner, mid + halfBase));
                    path.lineTo(ex(inner, mid + 45 - halfBase), ey(inner, mid + 45 - halfBase));
                }
                path.close();
                c.drawPath(path, p);
                c.drawCircle(12, 12, 3.2f, p);
                break;
            }
            case Icons.HIDE:
                chevron(c, p, 9, 15);
                break;
            case Icons.CHEVRON_UP:
                chevron(c, p, 15, 9);
                break;
            case Icons.SEARCH:
                // Windows Phone·Windows 8의 돋보기: 알은 오른쪽 위, 손잡이는 왼쪽 아래.
                c.drawCircle(14, 10, 6.5f, p);
                c.drawLine(9.4f, 14.6f, 3.5f, 20.5f, p);
                break;
            case Icons.PIN:
            case Icons.PIN_FILLED:
                // 압정 (Pin to Start): 45° 기울어 바늘이 왼쪽 아래를 향한다. 고정된 상태는 꽉 찬 머리.
                c.save();
                c.rotate(45, 12, 12);
                path.moveTo(8.5f, 3);
                path.lineTo(15.5f, 3);
                path.lineTo(14.5f, 9.5f);
                path.lineTo(18, 13.5f);
                path.lineTo(6, 13.5f);
                path.lineTo(9.5f, 9.5f);
                path.close();
                if (original == Icons.PIN_FILLED) p.setStyle(Paint.Style.FILL_AND_STROKE);
                c.drawPath(path, p);
                p.setStyle(Paint.Style.STROKE);
                c.drawLine(12, 13.5f, 12, 21.5f, p);
                c.restore();
                break;
            case Icons.CLOSE:
                c.drawLine(5, 5, 19, 19, p);
                c.drawLine(19, 5, 5, 19, p);
                break;
            case Icons.TRASH:
                // WP app bar의 delete: 뚜껑 막대와 손잡이, 곧은 통, 세로줄 둘.
                c.drawLine(3.5f, 6.5f, 20.5f, 6.5f, p);
                path.moveTo(9, 6.5f);
                path.lineTo(9, 3.5f);
                path.lineTo(15, 3.5f);
                path.lineTo(15, 6.5f);
                path.moveTo(6, 6.5f);
                path.lineTo(6, 20.5f);
                path.lineTo(18, 20.5f);
                path.lineTo(18, 6.5f);
                c.drawPath(path, p);
                c.drawLine(10, 10, 10, 17, p);
                c.drawLine(14, 10, 14, 17, p);
                break;
            case Icons.RECENT:
                c.drawCircle(12, 12, 9, p);
                path.moveTo(12, 6.5f);
                path.lineTo(12, 12);
                path.lineTo(16, 14.5f);
                c.drawPath(path, p);
                break;
            case Icons.UNDO:
                // 왼쪽으로 꺾여 돌아가는 화살 (꼬리는 반원), 뾰족한 화살촉.
                path.moveTo(6, 10);
                path.lineTo(13.5f, 10);
                rect.set(9.5f, 10, 18.5f, 18);
                path.arcTo(rect, -90, 180, false);
                path.lineTo(9.5f, 18);
                c.drawPath(path, p);
                arrowHead(c, p, 5, 10, 180, 4.5f);
                break;
            case Icons.MENU_KEYBOARD:
                // 각진 자판, 네모난 키 두 줄, 스페이스바.
                c.drawRect(2, 5.5f, 22, 18.5f, p);
                p.setStyle(Paint.Style.FILL);
                for (int i = 0; i < 5; i++) c.drawRect(4.6f + i * 3.25f, 8.1f, 6.4f + i * 3.25f, 9.9f, p);
                for (int i = 0; i < 4; i++) c.drawRect(6.2f + i * 3.25f, 11.1f, 8f + i * 3.25f, 12.9f, p);
                p.setStyle(Paint.Style.STROKE);
                c.drawLine(8, 15.5f, 16, 15.5f, p);
                break;
            case Icons.ONE_HAND:
                // 휴대폰 아래 왼쪽에 몰린 자판 (네모 키 넷).
                c.drawRect(5, 2.5f, 19, 21.5f, p);
                p.setStyle(Paint.Style.FILL);
                for (int r = 0; r < 2; r++) {
                    for (int col = 0; col < 2; col++) {
                        c.drawRect(7.6f + col * 3.6f, 12.4f + r * 3.6f, 10.2f + col * 3.6f, 15f + r * 3.6f, p);
                    }
                }
                break;
            case Icons.FLOAT:
                // 휴대폰 가운데에 뜬 자판 (네모 키 넷)과 그 아래 손잡이.
                c.drawRect(5, 2.5f, 19, 21.5f, p);
                p.setStyle(Paint.Style.FILL);
                for (int r = 0; r < 2; r++) {
                    for (int col = 0; col < 2; col++) {
                        c.drawRect(9.2f + col * 3.2f, 8.2f + r * 3.2f, 11.6f + col * 3.2f, 10.6f + r * 3.2f, p);
                    }
                }
                c.drawRect(10, 16, 14, 17.2f, p);
                break;
            case Icons.EXPAND:
                // 가운데에서 오른쪽 위·왼쪽 아래로 펼치는 두 화살표 (각진 꺾쇠).
                c.drawLine(13.5f, 10.5f, 19, 5, p);
                path.moveTo(13.5f, 4.5f);
                path.lineTo(19.5f, 4.5f);
                path.lineTo(19.5f, 10.5f);
                c.drawLine(10.5f, 13.5f, 5, 19, p);
                path.moveTo(4.5f, 13.5f);
                path.lineTo(4.5f, 19.5f);
                path.lineTo(10.5f, 19.5f);
                c.drawPath(path, p);
                break;
            case Icons.MENU_THEME: {
                // 테마: 시작 화면의 타일 넷 (하나는 강조색처럼 꽉 차 있다).
                p.setStyle(Paint.Style.FILL);
                c.drawRect(3, 3, 11, 11, p);
                p.setStyle(Paint.Style.STROKE);
                float h = p.getStrokeWidth() / 2f;
                c.drawRect(13 + h, 3 + h, 21 - h, 11 - h, p);
                c.drawRect(3 + h, 13 + h, 11 - h, 21 - h, p);
                c.drawRect(13 + h, 13 + h, 21 - h, 21 - h, p);
                break;
            }
            case Icons.MENU_MOVE:
                // 사방 화살표: 십자 선과 꽉 찬 세모 화살촉 넷.
                c.drawLine(12, 6, 12, 18, p);
                c.drawLine(6, 12, 18, 12, p);
                for (int deg = 0; deg < 360; deg += 90) solidHead(c, p, 12, 12, deg, 9, 3.2f, 3.2f);
                break;
            case Icons.MENU_WORDS:
                // 전구: 원호 머리와 각진 목을 끊김 없이 한 붓으로 (원호의 양 끝에서 바로 목이 이어진다), 그 아래 받침 줄.
                rect.set(6, 3, 18, 15);
                path.arcTo(rect, 130, 280, true);   // (8.14, 13.6)에서 시계 방향으로 (15.86, 13.6)까지
                path.lineTo(14.6f, 16.5f);
                path.lineTo(9.4f, 16.5f);
                path.close();                       // 원호의 시작점으로 돌아가 뾰족하게 잇는다
                c.drawPath(path, p);
                c.drawLine(9.5f, 19.5f, 14.5f, 19.5f, p);
                break;
            case Icons.MENU_SOUND:
                // WP의 소리: 꽉 찬 스피커와 소리 물결 둘.
                path.moveTo(3, 9);
                path.lineTo(7, 9);
                path.lineTo(12, 4.5f);
                path.lineTo(12, 19.5f);
                path.lineTo(7, 15);
                path.lineTo(3, 15);
                path.close();
                p.setStyle(Paint.Style.FILL_AND_STROKE);
                c.drawPath(path, p);
                p.setStyle(Paint.Style.STROKE);
                rect.set(9, 7.5f, 18, 16.5f);
                c.drawArc(rect, -50, 100, false, p);
                rect.set(6, 3.5f, 22, 20.5f);
                c.drawArc(rect, -48, 96, false, p);
                break;
            case Icons.MENU_INFO: {
                // 정보: 원 안의 i (네모 점과 곧은 기둥).
                c.drawCircle(12, 12, 9, p);
                c.drawLine(12, 10.5f, 12, 17.5f, p);
                p.setStyle(Paint.Style.FILL);
                float w = p.getStrokeWidth() / 2f + 0.1f;
                c.drawRect(12 - w, 6.6f, 12 + w, 6.6f + 2 * w, p);
                break;
            }
            case Icons.EXPORT:
            case Icons.IMPORT:
                // WP app bar의 upload·download: 받침 위로 나가거나 받침으로 들어오는 화살표.
                tray(c, p);
                if (original == Icons.EXPORT) {
                    c.drawLine(12, 15.5f, 12, 5, p);
                    arrowHead(c, p, 12, 4, 270, 4.5f);
                } else {
                    c.drawLine(12, 3.5f, 12, 14, p);
                    arrowHead(c, p, 12, 15, 90, 4.5f);
                }
                break;
            case Icons.MENU_BACKUP:
                // 받침 위에 나란한 위·아래 화살표 (내보내기와 가져오기). 두 화살표는 y = 8.5를 중심으로 위아래가 정확히
                // 뒤집힌 모양이라 같은 높이(3.5 ~ 13.5)를 차지한다. 촉의 날개는 길게(4.6) 두어 굵은 선(타일)에서도 촉 안쪽이
                // 메워지지 않게 하고, 날개끼리 닿지 않게 두 화살표를 9칸 떨어뜨린다 (날개 끝은 받침 안쪽 4.25 ~ 19.75).
                tray(c, p);
                c.drawLine(7.5f, 13.5f, 7.5f, 5, p);
                arrowHead(c, p, 7.5f, 3.5f, 270, 4.6f);
                c.drawLine(16.5f, 3.5f, 16.5f, 12, p);
                arrowHead(c, p, 16.5f, 13.5f, 90, 4.6f);
                break;
            case Icons.MENU_LAB:
                // 삼각 플라스크: 각진 외곽선과 꽉 찬 액체.
                path.moveTo(7.5f, 3.5f);
                path.lineTo(16.5f, 3.5f);
                path.moveTo(10, 3.5f);
                path.lineTo(10, 9.5f);
                path.lineTo(4, 20.5f);
                path.lineTo(20, 20.5f);
                path.lineTo(14, 9.5f);
                path.lineTo(14, 3.5f);
                c.drawPath(path, p);
                path.reset();
                path.moveTo(7.27f, 14.5f);
                path.lineTo(16.73f, 14.5f);
                path.lineTo(20, 20.5f);
                path.lineTo(4, 20.5f);
                path.close();
                p.setStyle(Paint.Style.FILL_AND_STROKE);
                c.drawPath(path, p);
                break;
            case Icons.CUT:
                // 가위: 손잡이 원 둘과 엇갈린 곧은 날.
                c.drawCircle(7, 17.5f, 3, p);
                c.drawCircle(17, 17.5f, 3, p);
                c.drawLine(8.7f, 15, 17, 3, p);
                c.drawLine(15.3f, 15, 7, 3, p);
                break;
            case Icons.COPY:
                // 겹친 두 장: 앞장은 각진 네모, 뒷장은 가리지 않은 왼쪽 위만.
                c.drawRect(8.5f, 8.5f, 20.5f, 20.5f, p);
                path.moveTo(8.5f, 15.5f);
                path.lineTo(3.5f, 15.5f);
                path.lineTo(3.5f, 3.5f);
                path.lineTo(15.5f, 3.5f);
                path.lineTo(15.5f, 8.5f);
                c.drawPath(path, p);
                break;
            case Icons.SELECT_ALL:
                // 점선 테두리 (모서리와 각 변 가운데)와 꽉 찬 네모.
                path.moveTo(4, 7);
                path.lineTo(4, 4);
                path.lineTo(7, 4);
                path.moveTo(17, 4);
                path.lineTo(20, 4);
                path.lineTo(20, 7);
                path.moveTo(20, 17);
                path.lineTo(20, 20);
                path.lineTo(17, 20);
                path.moveTo(7, 20);
                path.lineTo(4, 20);
                path.lineTo(4, 17);
                path.moveTo(11, 4);
                path.lineTo(13, 4);
                path.moveTo(20, 11);
                path.lineTo(20, 13);
                path.moveTo(11, 20);
                path.lineTo(13, 20);
                path.moveTo(4, 11);
                path.lineTo(4, 13);
                c.drawPath(path, p);
                p.setStyle(Paint.Style.FILL);
                c.drawRect(8, 8, 16, 16, p);
                break;
            case Icons.WORD_UP:
                chevron(c, p, 11.5f, 6.5f);
                chevron(c, p, 17.5f, 12.5f);
                break;
            case Icons.TAB:
                c.drawLine(3, 12, 15, 12, p);
                arrowHead(c, p, 16, 12, 0, 5);
                c.drawLine(20.5f, 6, 20.5f, 18, p);
                break;
            case Icons.TEXT_EDIT:
                // I자 커서 (위아래 가로 막대)와 양옆의 각진 꺾쇠.
                c.drawLine(12, 5, 12, 19, p);
                c.drawLine(9.5f, 5, 14.5f, 5, p);
                c.drawLine(9.5f, 19, 14.5f, 19, p);
                path.moveTo(6.5f, 8.5f);
                path.lineTo(3, 12);
                path.lineTo(6.5f, 15.5f);
                path.moveTo(17.5f, 8.5f);
                path.lineTo(21, 12);
                path.lineTo(17.5f, 15.5f);
                c.drawPath(path, p);
                break;
            default:
                break;
        }
        // 부른 쪽이 같은 Paint로 다른 것을 그릴 수 있으므로 Icons.draw가 남기던 상태로 돌려 둔다.
        p.setStyle(Paint.Style.FILL);
        p.setStrokeCap(Paint.Cap.ROUND);
        p.setStrokeJoin(Paint.Join.ROUND);
    }

    /** 가운데(12, 12)에서 반지름 r, 12시에서 시계 방향으로 deg도인 점. */
    private static float ex(float r, float deg) {
        return 12 + r * (float) Math.cos(Math.toRadians(deg - 90));
    }

    private static float ey(float r, float deg) {
        return 12 + r * (float) Math.sin(Math.toRadians(deg - 90));
    }

    /** 가로 6칸 폭의 꺾쇠: 양 끝이 y = from, 꼭짓점이 y = tip (위·아래를 가리킨다). */
    private static void chevron(Canvas c, Paint p, float from, float tip) {
        path.reset();
        path.moveTo(5.5f, from);
        path.lineTo(12, tip);
        path.lineTo(18.5f, from);
        c.drawPath(path, p);
        path.reset();
    }

    /**
     * 뾰족한 화살촉 (열린 꺾쇠): 꼭짓점의 바깥 끝이 (tipX, tipY)에 오고, deg 방향(0 = 오른쪽, 90 = 아래)을 가리킨다. len은 날개 길이.
     * 선 굵기만큼 꼭짓점을 안쪽으로 당겨 MITER로 꺾인 끝이 tip을 넘지 않게 한다.
     */
    private static void arrowHead(Canvas c, Paint p, float tipX, float tipY, float deg, float len) {
        float inset = p.getStrokeWidth() / 2f * 1.4142f;   // 90° 꼭짓점의 MITER 길이 (반 굵기 × √2)
        double a = Math.toRadians(deg);
        float dx = (float) Math.cos(a), dy = (float) Math.sin(a);
        float vx = tipX - dx * inset, vy = tipY - dy * inset;
        float bx = -dx * len * 0.7071f, by = -dy * len * 0.7071f;   // 꼭짓점에서 뒤로
        float nx = -dy * len * 0.7071f, ny = dx * len * 0.7071f;    // 옆으로
        path.reset();
        path.moveTo(vx + bx + nx, vy + by + ny);
        path.lineTo(vx, vy);
        path.lineTo(vx + bx - nx, vy + by - ny);
        c.drawPath(path, p);
        path.reset();
    }

    /** 꽉 찬 세모 화살촉: (cx, cy)에서 dist만큼 deg 방향에 꼭짓점, 길이 len, 밑변 반폭 half. */
    private static void solidHead(Canvas c, Paint p, float cx, float cy, float deg, float dist, float len, float half) {
        double a = Math.toRadians(deg);
        float dx = (float) Math.cos(a), dy = (float) Math.sin(a);
        float tx = cx + dx * dist, ty = cy + dy * dist;
        float bx = tx - dx * len, by = ty - dy * len;
        path.reset();
        path.moveTo(tx, ty);
        path.lineTo(bx - dy * half, by + dx * half);
        path.lineTo(bx + dy * half, by - dx * half);
        path.close();
        Paint.Style old = p.getStyle();
        p.setStyle(Paint.Style.FILL);
        c.drawPath(path, p);
        p.setStyle(old);
        path.reset();
    }

    /** 클립보드의 꽉 찬 집게 (판의 윗변을 가린다). */
    private static void clip(Canvas c, Paint p) {
        p.setStyle(Paint.Style.FILL_AND_STROKE);
        c.drawRect(9, 3, 15, 7, p);
        p.setStyle(Paint.Style.STROKE);
    }

    /** 내보내기·가져오기·백업의 받침 (위가 트인 각진 그릇). */
    private static void tray(Canvas c, Paint p) {
        path.reset();
        path.moveTo(4, 14.5f);
        path.lineTo(4, 20);
        path.lineTo(20, 20);
        path.lineTo(20, 14.5f);
        c.drawPath(path, p);
        path.reset();
    }
}
