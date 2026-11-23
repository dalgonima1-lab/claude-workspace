# 사업 비서 안드로이드 앱

갤럭시 폴드 8용 개인 앱. 갤럭시 AI 통화 요약·문자·카톡 내용을 공유 버튼으로 넘기면
중계 서버(`../relay-server`)가 Claude로 분석하고, 앱이 거래처 기록·삼성 캘린더 일정·할 일·장애 티켓으로 등록한다.

## 설치 (APK 직접 설치)

1. `app-debug.apk`를 폰으로 옮긴다 (카톡 나에게 보내기, 구글 드라이브, USB 등).
2. 폰에서 파일을 열면 "출처를 알 수 없는 앱" 허용 안내가 나온다. 사용 중인 파일 앱(내 파일, 크롬 등)에 설치 허용을 켠다.
3. 설치 후 **사업 비서** 앱을 열고 **설정** 탭에서:
   - 서버 주소: `https://claude-workspace-relay-server.vercel.app` (기본값으로 들어 있음)
   - 서버 토큰: Vercel 환경 변수 `APP_SHARED_TOKEN`에 넣은 값
   - **연결 테스트**를 눌러 "연결 정상"을 확인 → **저장**
   - **캘린더 권한 허용** → 일정을 넣을 삼성 캘린더 선택 → **저장**

## 사용법

- 통화 후 전화 앱의 통화 요약(또는 전사본) 화면에서 **공유** → **사업 비서에 정리**
- 문자·카톡 내용도 길게 눌러 공유 → 사업 비서에 정리
- 앱 안 **직접 입력**으로 붙여넣기나 메모 입력 (키보드 마이크로 말하기 가능)
- 분석 결과 확인 카드에서 소속·상대·사업·유형을 고치고, 등록할 일정·할 일·금전을 체크한 뒤 **등록**
- **답장 초안**을 누르면 확인 문자 초안을 만들어 복사하거나 문자 앱으로 보낼 수 있다

## 화면

| 탭 | 내용 |
|---|---|
| 오늘 | 오늘 일정, 7일 내 기한 할 일, 미완료 장애 수, 이번 주 받을 돈, 최근 기록 |
| 거래처 | 발주처·협력업체·자재·내부로 묶인 거래처 목록. 펼친 화면은 왼쪽 목록 + 오른쪽 타임라인 |
| 장애 | 접수 → 방문 예정 → 조치 중 → 완료 보드. 버튼으로 다음 단계 이동 |
| 설정 | 서버 주소·토큰, 기본 소속, 캘린더 선택, 할 일 알림 |

## 빌드

```bash
# Android SDK(플랫폼 35, 빌드툴 35.0.0)와 JDK 17+ 필요
echo "sdk.dir=/path/to/android-sdk" > local.properties
./gradlew :app:assembleDebug
# → app/build/outputs/apk/debug/app-debug.apk
```

Android Studio에서는 이 폴더(`biz-assistant/android`)를 열고 Run 하면 된다.

## 구조

```
app/src/main/java/com/jpntech/bizassistant/
  BizApp.kt                  Application: DB, 설정, 중계 서버 클라이언트 조립
  MainActivity.kt            하단 탭(접힘) / 왼쪽 레일(펼침) 네비게이션
  share/ShareActivity.kt     공유 수신 입구 (ACTION_SEND text/plain)
  data/remote/               중계 서버 요청·응답 모델, OkHttp 클라이언트
  data/db/                   Room: orgs, projects, records, events, tasks, money, tickets
  data/settings/             DataStore 설정
  data/Repository.kt         분석 요청, 확인 카드 → DB·캘린더·알림 저장
  data/ConfirmDraft.kt       확인 카드에서 편집하는 모델
  calendar/CalendarWriter.kt 공용 캘린더(CalendarContract)에 일정 쓰기 → 삼성 캘린더에 표시
  reminders/Reminders.kt     WorkManager로 기한 전날 09:00 알림
  ui/confirm/                분석 중 → 확인 카드 → 등록 완료
  ui/today, ui/orgs, ui/tickets, ui/settings, ui/input
```

## 알려진 제한 (1단계)

- 갤럭시 AI 요약·전사본은 공유 버튼으로만 받는다 (자동 수집 없음).
- 녹음 파일 감시, 명함·영수증 촬영, 아침 브리핑, 입금 문자 대조는 2단계 이후.
- 데이터는 폰 안에만 저장된다 (백업 없음). 앱을 지우면 기록도 지워진다.
