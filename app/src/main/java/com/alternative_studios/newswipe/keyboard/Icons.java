package com.alternative_studios.newswipe.keyboard;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;

/**
 * 키보드 아이콘을 코드로 그린다. 이미지 리소스를 쓰지 않아 앱이 가볍다.
 * 좌표는 24×24 칸 기준이다.
 */
public final class Icons {
    public static final int SHIFT = 1;
    public static final int SHIFT_ON = 2;
    public static final int SHIFT_LOCK = 3;
    public static final int DELETE = 4;
    public static final int ENTER = 5;
    public static final int GLOBE = 6;
    public static final int EMOJI = 7;
    public static final int CLIPBOARD = 8;
    public static final int MIC = 9;
    public static final int SETTINGS = 10;
    public static final int HIDE = 11;
    public static final int SEARCH = 12;
    public static final int BACK = 13;
    public static final int PIN = 14;
    public static final int CLOSE = 15;
    public static final int SEND = 16;
    public static final int NEXT = 17;
    public static final int DONE = 18;
    public static final int RECENT = 19;
    public static final int TRASH = 20;
    /** 속이 채워진 핀 (고정된 상태). */
    public static final int PIN_FILLED = 21;
    /** 실행 취소 (왼쪽 위로 되돌아가는 화살표). */
    public static final int UNDO = 22;

    // 설정 메뉴의 항목 아이콘
    public static final int MENU_KEYBOARD = 23;   // 자판 모양
    public static final int MENU_THEME = 24;      // 테마 (팔레트)
    public static final int MENU_LONG_PRESS = 25; // 길게 누르기
    public static final int MENU_INPUT = 26;      // 입력 동작 (글자 커서)
    public static final int MENU_SWIPE = 27;      // 밀어서 글자 입력 (ㄱ 키와 오른쪽 화살표)
    public static final int MENU_MOVE = 28;       // 밀어서 기능 (사방으로 밀기)
    public static final int MENU_WORDS = 29;      // 단어 추천 (전구)
    public static final int MENU_SOUND = 30;      // 소리 및 진동
    public static final int MENU_TOOLBAR = 31;    // 도구 막대, 클립보드, 이모지
    public static final int MENU_INFO = 32;       // 정보
    public static final int MENU_BACKUP = 33;     // 설정 가져오기 및 내보내기
    public static final int MENU_USAGE = 34;      // 사용법 (펼친 책)
    public static final int EXPORT = 35;          // 설정 내보내기 (받침 위로 나가는 화살표)
    public static final int IMPORT = 36;          // 설정 가져오기 (받침으로 들어오는 화살표)
    public static final int MENU_LAYOUT = 37;     // 자판 레이아웃 (네 칸으로 놓인 키)
    public static final int ONE_HAND = 38;        // 한 손 모드 (휴대폰 아래 한쪽에 몰린 자판)
    public static final int EXPAND = 39;          // 한 손 모드 끄기 (양쪽으로 펼치는 화살표)
    public static final int MENU_LAB = 40;        // 실험실 (삼각 플라스크)

    private static final Path path = new Path();
    private static final RectF oval = new RectF();

    private Icons() {
    }

    /** 톱니바퀴 아이콘의 점: 가운데(12, 12)에서 반지름 r, 위쪽(12시)에서 시계 방향으로 deg도. */
    private static float gearX(float r, float deg) {
        return 12 + r * (float) Math.cos(Math.toRadians(deg - 90));
    }

    private static float gearY(float r, float deg) {
        return 12 + r * (float) Math.sin(Math.toRadians(deg - 90));
    }

    /**
     * @param size 아이콘 한 변 길이(px)
     * @param p    색이 지정된 Paint. 선 굵기·스타일은 이 메서드가 정한다.
     */
    public static void draw(Canvas c, int icon, float cx, float cy, float size, Paint p) {
        float u = size / 24f;
        c.save();
        c.translate(cx - size / 2f, cy - size / 2f);
        c.scale(u, u);
        p.setStrokeWidth(1.9f);
        p.setStrokeCap(Paint.Cap.ROUND);
        p.setStrokeJoin(Paint.Join.ROUND);
        p.setStyle(Paint.Style.STROKE);
        path.reset();
        switch (icon) {
            case SHIFT:
            case SHIFT_ON:
            case SHIFT_LOCK:
                path.moveTo(12, 3.5f);
                path.lineTo(20.5f, 12.5f);
                path.lineTo(15.5f, 12.5f);
                path.lineTo(15.5f, 18.5f);
                path.lineTo(8.5f, 18.5f);
                path.lineTo(8.5f, 12.5f);
                path.lineTo(3.5f, 12.5f);
                path.close();
                if (icon != SHIFT) p.setStyle(Paint.Style.FILL_AND_STROKE);
                c.drawPath(path, p);
                if (icon == SHIFT_LOCK) {
                    p.setStyle(Paint.Style.STROKE);
                    c.drawLine(8.5f, 21.5f, 15.5f, 21.5f, p);
                }
                break;
            case DELETE:
                path.moveTo(8, 5);
                path.lineTo(21, 5);
                path.lineTo(21, 19);
                path.lineTo(8, 19);
                path.lineTo(2.5f, 12);
                path.close();
                c.drawPath(path, p);
                c.drawLine(11.5f, 9, 17, 15, p);
                c.drawLine(17, 9, 11.5f, 15, p);
                break;
            case ENTER:
                path.moveTo(19.5f, 5);
                path.lineTo(19.5f, 13);
                path.lineTo(5, 13);
                c.drawPath(path, p);
                path.reset();
                path.moveTo(9, 8.5f);
                path.lineTo(4.5f, 13);
                path.lineTo(9, 17.5f);
                c.drawPath(path, p);
                break;
            case SEND:
                path.moveTo(3.5f, 4.5f);
                path.lineTo(21, 12);
                path.lineTo(3.5f, 19.5f);
                path.lineTo(6.5f, 12);
                path.close();
                c.drawPath(path, p);
                c.drawLine(6.5f, 12, 12, 12, p);
                break;
            case NEXT:
                c.drawLine(4, 12, 19, 12, p);
                path.moveTo(13.5f, 6.5f);
                path.lineTo(19.5f, 12);
                path.lineTo(13.5f, 17.5f);
                c.drawPath(path, p);
                break;
            case DONE:
                path.moveTo(4.5f, 12.5f);
                path.lineTo(9.5f, 17.5f);
                path.lineTo(19.5f, 6.5f);
                c.drawPath(path, p);
                break;
            case GLOBE:
                c.drawCircle(12, 12, 9, p);
                oval.set(7.8f, 3, 16.2f, 21);
                c.drawOval(oval, p);
                c.drawLine(3, 12, 21, 12, p);
                c.drawLine(12, 3, 12, 21, p);
                break;
            case EMOJI:
                c.drawCircle(12, 12, 9, p);
                p.setStyle(Paint.Style.FILL);
                c.drawCircle(9, 10, 1.2f, p);
                c.drawCircle(15, 10, 1.2f, p);
                p.setStyle(Paint.Style.STROKE);
                oval.set(7.5f, 9, 16.5f, 17);
                c.drawArc(oval, 20, 140, false, p);
                break;
            case CLIPBOARD:
                oval.set(5, 5, 19, 21);
                c.drawRoundRect(oval, 2, 2, p);
                // 클립은 속을 채워서, 그 밑을 지나가는 판의 윗변이 비쳐 보이지 않게 한다.
                oval.set(9, 3, 15, 7);
                p.setStyle(Paint.Style.FILL_AND_STROKE);
                c.drawRoundRect(oval, 1, 1, p);
                p.setStyle(Paint.Style.STROKE);
                c.drawLine(8.5f, 12, 15.5f, 12, p);
                c.drawLine(8.5f, 16, 13.5f, 16, p);
                break;
            case MIC:
                oval.set(9, 3, 15, 15);
                c.drawRoundRect(oval, 3, 3, p);
                oval.set(5.5f, 6.5f, 18.5f, 17.5f);
                c.drawArc(oval, 0, 180, false, p);
                c.drawLine(12, 17.5f, 12, 21, p);
                break;
            case SETTINGS: {
                // 톱니바퀴: 톱니 8개를 한 붓으로 잇고(톱니 끝·골은 원호), 가운데에 구멍.
                float outer = 9.6f, inner = 7.4f, halfTop = 10f, halfBase = 15f;
                path.moveTo(gearX(inner, -halfBase), gearY(inner, -halfBase));
                for (int i = 0; i < 8; i++) {
                    float mid = 45f * i;   // 이 톱니의 가운데 각도
                    path.lineTo(gearX(outer, mid - halfTop), gearY(outer, mid - halfTop));
                    oval.set(12 - outer, 12 - outer, 12 + outer, 12 + outer);
                    path.arcTo(oval, mid - halfTop - 90, 2 * halfTop, false);
                    path.lineTo(gearX(inner, mid + halfBase), gearY(inner, mid + halfBase));
                    oval.set(12 - inner, 12 - inner, 12 + inner, 12 + inner);
                    path.arcTo(oval, mid + halfBase - 90, 45 - 2 * halfBase, false);
                }
                path.close();
                c.drawPath(path, p);
                c.drawCircle(12, 12, 3.1f, p);
                break;
            }
            case HIDE:
                path.moveTo(6, 9.5f);
                path.lineTo(12, 15.5f);
                path.lineTo(18, 9.5f);
                c.drawPath(path, p);
                break;
            case SEARCH:
                c.drawCircle(10.5f, 10.5f, 6, p);
                c.drawLine(15, 15, 20.5f, 20.5f, p);
                break;
            case BACK:
                c.drawLine(5, 12, 20, 12, p);
                path.moveTo(10.5f, 6.5f);
                path.lineTo(5, 12);
                path.lineTo(10.5f, 17.5f);
                c.drawPath(path, p);
                break;
            case PIN:
            case PIN_FILLED:
                path.moveTo(9, 3.5f);
                path.lineTo(15, 3.5f);
                path.lineTo(14, 10);
                path.lineTo(17.5f, 14);
                path.lineTo(6.5f, 14);
                path.lineTo(10, 10);
                path.close();
                if (icon == PIN_FILLED) p.setStyle(Paint.Style.FILL_AND_STROKE);
                c.drawPath(path, p);
                p.setStyle(Paint.Style.STROKE);
                c.drawLine(12, 14, 12, 21, p);
                break;
            case CLOSE:
                c.drawLine(6, 6, 18, 18, p);
                c.drawLine(18, 6, 6, 18, p);
                break;
            case TRASH:
                c.drawLine(4, 7, 20, 7, p);
                path.moveTo(9, 7);
                path.lineTo(9, 4.5f);
                path.lineTo(15, 4.5f);
                path.lineTo(15, 7);
                c.drawPath(path, p);
                path.reset();
                path.moveTo(6, 7);
                path.lineTo(7, 19.5f);
                path.lineTo(17, 19.5f);
                path.lineTo(18, 7);
                c.drawPath(path, p);
                c.drawLine(10, 11, 10, 16, p);
                c.drawLine(14, 11, 14, 16, p);
                break;
            case RECENT:
                c.drawCircle(12, 12, 9, p);
                path.moveTo(12, 7);
                path.lineTo(12, 12);
                path.lineTo(15.5f, 14);
                c.drawPath(path, p);
                break;
            case UNDO:
                // 화살촉(왼쪽)과, 거기서 오른쪽으로 갔다가 아래로 돌아오는 꼬리.
                // 다른 아이콘과 크기가 맞도록 (5.5~18.5, 6~18)의 칸 가운데(12, 12)에 놓는다.
                path.moveTo(9.5f, 6);
                path.lineTo(5.5f, 10);
                path.lineTo(9.5f, 14);
                path.moveTo(5.5f, 10);
                path.lineTo(13.5f, 10);
                path.cubicTo(16.3f, 10, 18.5f, 11.8f, 18.5f, 14);
                path.cubicTo(18.5f, 16.2f, 16.3f, 18, 13.5f, 18);
                path.lineTo(9.5f, 18);
                c.drawPath(path, p);
                break;
            case MENU_KEYBOARD:
                oval.set(2, 5.5f, 22, 18.5f);
                c.drawRoundRect(oval, 2.5f, 2.5f, p);
                p.setStyle(Paint.Style.FILL);
                for (int i = 0; i < 5; i++) c.drawCircle(5.5f + i * 3.25f, 9, 0.9f, p);      // 윗줄 키
                for (int i = 0; i < 4; i++) c.drawCircle(7.1f + i * 3.25f, 12, 0.9f, p);     // 가운뎃줄 키 (반 칸 어긋나게)
                p.setStyle(Paint.Style.STROKE);
                c.drawLine(8, 15.3f, 16, 15.3f, p);                                          // 스페이스바
                break;
            case ONE_HAND:
                // 휴대폰 화면 아래 왼쪽에 작게 몰린 자판(점 네 개)과, 오른쪽의 빈 곳.
                oval.set(5, 2.5f, 19, 21.5f);
                c.drawRoundRect(oval, 2.6f, 2.6f, p);
                p.setStyle(Paint.Style.FILL);
                for (int r = 0; r < 2; r++) {
                    for (int col = 0; col < 2; col++) {
                        oval.set(7.6f + col * 3.6f, 12.4f + r * 3.6f, 10.2f + col * 3.6f, 15f + r * 3.6f);
                        c.drawRoundRect(oval, 0.7f, 0.7f, p);
                    }
                }
                p.setStyle(Paint.Style.STROKE);
                break;
            case EXPAND:
                // 가운데에서 오른쪽 위·왼쪽 아래로 펼치는 두 화살표.
                c.drawLine(13.5f, 10.5f, 19.5f, 4.5f, p);
                path.moveTo(14, 4.5f);
                path.lineTo(19.5f, 4.5f);
                path.lineTo(19.5f, 10);
                c.drawPath(path, p);
                c.drawLine(10.5f, 13.5f, 4.5f, 19.5f, p);
                path.reset();
                path.moveTo(4.5f, 14);
                path.lineTo(4.5f, 19.5f);
                path.lineTo(10, 19.5f);
                c.drawPath(path, p);
                break;
            case MENU_LAYOUT:
                // 2×2로 놓인 둥근 키 네 개: 키를 어떻게 배열하는지.
                for (int r = 0; r < 2; r++) {
                    for (int col = 0; col < 2; col++) {
                        float x = 3.5f + col * 9.5f, y = 3.5f + r * 9.5f;
                        oval.set(x, y, x + 7.5f, y + 7.5f);
                        c.drawRoundRect(oval, 2f, 2f, p);
                    }
                }
                break;
            case MENU_THEME:
                // 화가의 팔레트: 오른쪽 아래가 움푹 들어간 둥근 판, 엄지 구멍, 물감 자리
                path.moveTo(12, 3);
                path.cubicTo(6.5f, 3, 3, 7, 3, 12);
                path.cubicTo(3, 17, 7, 21, 12, 21);
                path.cubicTo(13.6f, 21, 14.6f, 20, 14.6f, 18.6f);
                path.cubicTo(14.6f, 17, 15.8f, 16, 17.2f, 16);
                path.cubicTo(19.5f, 16, 21, 14.5f, 21, 12);
                path.cubicTo(21, 7, 17, 3, 12, 3);
                path.close();
                c.drawPath(path, p);
                p.setStyle(Paint.Style.FILL);
                c.drawCircle(6.9f, 10.2f, 1.35f, p);
                c.drawCircle(9.8f, 7.2f, 1.35f, p);
                c.drawCircle(14.4f, 6.9f, 1.35f, p);
                c.drawCircle(17.2f, 10.3f, 1.35f, p);
                p.setStyle(Paint.Style.STROKE);
                c.drawCircle(8.6f, 15, 2, p);      // 엄지 구멍
                break;
            case MENU_LONG_PRESS:
                p.setStyle(Paint.Style.FILL);
                c.drawCircle(12, 12, 2.3f, p);
                p.setStyle(Paint.Style.STROKE);
                c.drawCircle(12, 12, 5.8f, p);
                oval.set(3, 3, 21, 21);
                c.drawArc(oval, 195, 60, false, p);   // 눌렀을 때 퍼지는 물결
                c.drawArc(oval, 15, 60, false, p);
                break;
            case MENU_INPUT:
                c.drawLine(12, 5, 12, 19, p);
                c.drawLine(9, 5, 15, 5, p);
                c.drawLine(9, 19, 15, 19, p);
                break;
            case MENU_SWIPE:
                // ㄱ 키: 둥근 네모(키) 안에 ㄱ. ㄱ은 획이 위·오른쪽에 몰려 있어 살짝 왼쪽 아래로 놓아야 가운데로 보인다.
                oval.set(1, 5.5f, 14, 18.5f);
                c.drawRoundRect(oval, 3, 3, p);
                path.moveTo(4.6f, 10);
                path.lineTo(9.6f, 10);
                path.lineTo(9.6f, 14.4f);
                // 오른쪽 화살표: 키를 밀어서 입력한다는 뜻 (키와 띄워 둔다)
                path.moveTo(17.2f, 12);
                path.lineTo(22.8f, 12);
                path.moveTo(20.2f, 9.4f);
                path.lineTo(22.8f, 12);
                path.lineTo(20.2f, 14.6f);
                c.drawPath(path, p);
                break;
            case MENU_MOVE:
                c.drawLine(12, 3.5f, 12, 20.5f, p);
                c.drawLine(3.5f, 12, 20.5f, 12, p);
                path.moveTo(9.6f, 6);   // 위
                path.lineTo(12, 3.5f);
                path.lineTo(14.4f, 6);
                path.moveTo(9.6f, 18);  // 아래
                path.lineTo(12, 20.5f);
                path.lineTo(14.4f, 18);
                path.moveTo(6, 9.6f);   // 왼쪽
                path.lineTo(3.5f, 12);
                path.lineTo(6, 14.4f);
                path.moveTo(18, 9.6f);  // 오른쪽
                path.lineTo(20.5f, 12);
                path.lineTo(18, 14.4f);
                c.drawPath(path, p);
                break;
            case MENU_WORDS:
                oval.set(6.5f, 3.5f, 17.5f, 14.5f);
                c.drawArc(oval, 125, 290, false, p);
                path.moveTo(8.85f, 13.5f);
                path.lineTo(9.6f, 16.5f);
                path.lineTo(14.4f, 16.5f);
                path.lineTo(15.15f, 13.5f);
                c.drawPath(path, p);
                c.drawLine(10, 19.3f, 14, 19.3f, p);
                break;
            case MENU_SOUND:
                path.moveTo(3.5f, 9.5f);
                path.lineTo(7.5f, 9.5f);
                path.lineTo(12, 5.5f);
                path.lineTo(12, 18.5f);
                path.lineTo(7.5f, 14.5f);
                path.lineTo(3.5f, 14.5f);
                path.close();
                c.drawPath(path, p);
                oval.set(7.5f, 7.5f, 16.5f, 16.5f);
                c.drawArc(oval, -45, 90, false, p);
                oval.set(4, 4, 20, 20);
                c.drawArc(oval, -40, 80, false, p);
                break;
            case MENU_TOOLBAR:
                oval.set(2.5f, 7, 21.5f, 17);
                c.drawRoundRect(oval, 2.5f, 2.5f, p);
                p.setStyle(Paint.Style.FILL);
                c.drawCircle(7, 12, 1.1f, p);
                c.drawCircle(12, 12, 1.1f, p);
                c.drawCircle(17, 12, 1.1f, p);
                p.setStyle(Paint.Style.STROKE);
                break;
            case MENU_INFO:
                c.drawCircle(12, 12, 9, p);
                c.drawLine(12, 11, 12, 16.5f, p);
                p.setStyle(Paint.Style.FILL);
                c.drawCircle(12, 7.8f, 1.15f, p);
                p.setStyle(Paint.Style.STROKE);
                break;
            case EXPORT:
            case IMPORT:
                // 설정 가져오기 및 내보내기 메뉴 아이콘과 같은 받침에, 가운데 화살표 하나 (내보내기 = 위로, 가져오기 = 아래로).
                path.moveTo(4, 15);
                path.lineTo(4, 20);
                path.lineTo(20, 20);
                path.lineTo(20, 15);
                path.moveTo(12, 4);
                path.lineTo(12, 15);
                if (icon == EXPORT) {
                    path.moveTo(8, 8);
                    path.lineTo(12, 4);
                    path.lineTo(16, 8);
                } else {
                    path.moveTo(8, 11);
                    path.lineTo(12, 15);
                    path.lineTo(16, 11);
                }
                c.drawPath(path, p);
                break;
            case MENU_USAGE:
                // 가운데 책등을 사이에 두고 좌우 대칭으로 펼친 책.
                path.moveTo(12, 7);
                path.cubicTo(9.6f, 5.4f, 6.4f, 5, 3, 5.6f);
                path.lineTo(3, 18.4f);
                path.cubicTo(6.4f, 17.8f, 9.6f, 18.2f, 12, 19.8f);
                path.cubicTo(14.4f, 18.2f, 17.6f, 17.8f, 21, 18.4f);
                path.lineTo(21, 5.6f);
                path.cubicTo(17.6f, 5, 14.4f, 5.4f, 12, 7);
                path.lineTo(12, 19.8f);
                c.drawPath(path, p);
                break;
            case MENU_LAB:
                // 좁은 목 아래로 넓어지는 삼각 플라스크, 입구 테두리와 안에 담긴 액체의 수면.
                path.moveTo(8, 3.5f);
                path.lineTo(16, 3.5f);
                path.moveTo(10, 3.5f);
                path.lineTo(10, 9.5f);
                path.lineTo(4.5f, 19);
                path.quadTo(4, 20.5f, 5.6f, 20.5f);
                path.lineTo(18.4f, 20.5f);
                path.quadTo(20, 20.5f, 19.5f, 19);
                path.lineTo(14, 9.5f);
                path.lineTo(14, 3.5f);
                path.moveTo(7.2f, 14.5f);
                path.lineTo(16.8f, 14.5f);
                c.drawPath(path, p);
                break;
            case MENU_BACKUP:
                // 받침 위에 나란히 선 위쪽 화살표(내보내기)와 아래쪽 화살표(가져오기).
                // 두 화살표는 가운데(x=12)를 기준으로 좌우 대칭이고 길이가 같으며, 받침에 닿지 않는다.
                path.moveTo(4, 15);
                path.lineTo(4, 20);
                path.lineTo(20, 20);
                path.lineTo(20, 15);
                path.moveTo(8.5f, 13);
                path.lineTo(8.5f, 4);
                path.moveTo(5.75f, 6.75f);
                path.lineTo(8.5f, 4);
                path.lineTo(11.25f, 6.75f);
                path.moveTo(15.5f, 4);
                path.lineTo(15.5f, 13);
                path.moveTo(12.75f, 10.25f);
                path.lineTo(15.5f, 13);
                path.lineTo(18.25f, 10.25f);
                c.drawPath(path, p);
                break;
            default:
                break;
        }
        p.setStyle(Paint.Style.FILL);
        c.restore();
    }
}
