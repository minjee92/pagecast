# Pagecast — 낭독 AI 앱 (Android 전용)

## 배경
영어 원서를 오디오북과 함께 들으며 읽는 경험을, 오디오가 없는 자작 소설·팬픽션에서도 누리기 위한 개인용 앱.
텍스트(나중에는 책 사진)를 넣으면 캐릭터별 목소리와 감정 연기로 읽어 주고, 읽는 위치를 하이라이트한다.
영어 공부(사전, 숙어, 하루 학습 목표)와 페이지별 장면 이미지도 지원할 예정이다.

## 로드맵
> 순서는 재검토 중이다: 핵심 가치인 "감정 내레이션"을 앞당기는 안과 비교한 뒤 확정한다.

| 단계 | 내용 | 서버 | INTERNET |
|---|---|---|---|
| **1 (완료)** | 붙여넣기·TXT → 단일 목소리 낭독 → 문장·단어 하이라이트, 위치 저장, 백그라운드·잠금화면 재생 | ✗ | ✗ |
| 2a 기반 | FeatureFlags·Edition, Page 모델 + DB v2(AutoMigration + 페이지 backfill), 설정(DataStore) | ✗ | ✗ |
| 2b 서재·커버 | 커버 썸네일, 제목 기반 기본 커버, Photo Picker 업로드(재인코딩, EXIF 제거), Coil 3 | ✗ | ✗ |
| 2c 불러오기 확장 | 구글 문서(가상 파일 내보내기), DOCX, EPUB, 텍스트 PDF(플랫폼 API만) | ✗ | ✗ |
| 2d 학습 목표 | 공부 모드, 책별 하루 목표, 이월, 3초 배너, 리마인더(선택·기본 꺼짐) | ✗ | ✗ |
| 2e 사전(오프라인) | 제스처 개편, 더블탭 → 영영 사전(Open English WordNet), 원형 찾기(morphy), 드래그 선택 | ✗ | ✗ |
| 2f 폴더블 | 좌우·상하 분할 레이아웃 + 플레이스홀더 이미지 | ✗ | ✗ |
| 3 OCR | 카메라 → ML Kit → 텍스트 정리(머리글·쪽번호 제거, 하이픈·문단 복원), 스캔 PDF — `ocr/` | ✗ | 미확인 |
| 4 서버 기반 공부 | 서버 함수 인프라(인증·한도), 영한·맥락 뜻·숙어(LLM), 커버 검색(Open Library) | ✓ | **처음 추가** |
| 5 대본화 | LLM이 `{화자, 대사, 감정}` 세그먼트로 변환, 캐릭터 목록 + 외형 묘사 — `script/` | ✓ | ✓ |
| 6 클라우드 TTS | 감정 연기, 캐릭터별 캐스팅(VoiceCast), 오디오 캐시, 오프라인 재생 | ✓ | ✓ |
| 7 장면 이미지 (풀 전용) | 페이지 → 장면 프롬프트 → 이미지 → 캐시, 미리 생성, 스타일 프리셋 | ✓ | ✓ |

## 기술 결정 (고정)
- Android 전용, 테스트 기기는 갤럭시 Z 플립8. minSdk 26 (`UtteranceProgressListener.onRangeStart`가 API 26+)
- Kotlin + Jetpack Compose, 단일 Activity, 단일 `:app` 모듈, 수동 DI(`di/AppContainer`)
- AGP 9 built-in Kotlin 사용 (`org.jetbrains.kotlin.android` 플러그인 적용하지 않음), 어노테이션 처리는 KSP
- 의존성 버전은 `gradle/libs.versions.toml`에서만 관리
- 백그라운드 재생·잠금화면: Media3 `TtsPlayer`(SimpleBasePlayer) + `PlaybackService`(MediaSessionService)
  - 책 1권 = MediaItem 1개, 이전·다음은 seekCommand로 문장 이동. 화면은 `PlaybackClient`(MediaController) 경유
  - 문장 위치 이동(탭)만 `NarrationController`를 직접 호출. 위치 저장은 앱 스코프 `ProgressRecorder`
- 로컬 저장: Room (스키마 JSON은 `app/schemas/`에 커밋, 버전 올릴 때 Migration 작성). 설정은 DataStore
- TTS는 반드시 `tts/TtsEngine` 인터페이스로 추상화
  - 1단계 구현체: `AndroidTtsEngine` (내장 TextToSpeech, Google TTS 우선). 단어 하이라이트는 `onRangeStart`
  - `Utterance`에 `voice`, `emotion`이 처음부터 있다. 내장 엔진은 emotion을 무시하고, 클라우드 엔진은 서버 함수로 전달
  - 클라우드 엔진: 서버 함수가 오디오 + 단어 타이밍을 반환 → 캐시 → 같은 인터페이스 뒤에서 재생
- 도메인 모델: `Book`, `Chapter`, `Segment(text, speakerId, emotion)`, `Character(+appearance)`, `VoiceCast`, `ReadingProgress`
  - 2a에서 추가: `Page`, `PageRead`, `StudyGoal`, `DailyStudyRecord`
- 문장 분할: `java.text.BreakIterator`(문장 단위) + 약어 예외(Mr., Dr. 등) 병합 보정만
- 화면 간에는 본문이 아니라 `bookId`만 전달

### 확정된 설계 결정 (2026-10-19)
- **버전**: 앱 1개(풀) + `FeatureFlags`. 기능 진입점은 모두 FeatureFlags 뒤에 둔다(라이트를 나중에 분리해도 큰 리팩터링이 없게).
  라이트 = 내레이터 1명 + 감정 연기 + 학습 모드, 장면 이미지·캐릭터별 목소리 없음(감정 연기 때문에 라이트도 클라우드 TTS 비용 있음).
  OCR과 커버 검색은 두 버전 모두 포함.
- **페이지**: 가져올 때 1,500자 기준으로 가상 페이지를 계산해 저장(화면 크기와 무관, Chapter를 넘지 않음). OCR 사진·PDF 쪽은 실제 페이지.
- **읽음**: 페이지 Segment의 90% 이상 낭독 완료, 또는 수동 "읽음" 버튼. 다시 읽은 페이지 포함 여부는 설정(기본: 포함 안 함).
- **학습 목표**: 하루 경계는 사용자 지정(기본 04:00, 로컬 시간대). 이월은 직전 기록일 부족분, 상한은 하루 목표 1배(최대 2배).
  이월 안내는 그날 처음 열 때 3초 배너. 리마인더(WorkManager)는 선택 기능, 기본 꺼짐. 날짜 계산은 `Clock` 주입으로 테스트.
- **제스처**: 일시정지 중 탭 = 즉시 위치 이동, 재생 중 탭 = 더블탭 판정 후 그 문장부터 재생,
  더블탭 = 사전(재생 중이면 자동 일시정지), 길게 누른 뒤 드래그 = 표현 선택(한 문단, 최대 8단어).
- **사전**: `DictionaryProvider` 인터페이스. 오프라인은 Open English WordNet(CC BY 4.0), 영한·맥락 뜻·숙어는 4단계 LLM.
  사전 데이터는 저장소에 넣지 않고 빌드 스크립트가 SHA-256 고정 공식 릴리스를 받아 생성.
- **불러오기**: PDF는 플랫폼 API(`PdfRenderer` API 35+ / `PdfRendererPreV` S Ext 13+)만 쓰고 미지원 기기는 안내.
  DOCX는 Zip + XmlPullParser, EPUB은 Zip + OPF + jsoup. AGPL 라이브러리(MuPDF, iText)는 쓰지 않는다.
- **커버 검색**: Open Library를 앱에서 HTTPS로 직접 호출. 검색 버튼을 눌렀을 때만 호출(자동 검색 금지),
  User-Agent에는 앱 이름 + 저장소 URL만(연락처 이메일 없음).
- **폴더블**: 기기 모델명이 아니라 WindowManager(FoldingFeature)와 창 크기 클래스로 분기.
  Flip 플렉스 모드 = 위 페이지, 아래 이미지 + 작은 재생 버튼.
- **장면 이미지**: 처음 켤 때 본문 전송 동의를 받는다. 월 예산 $10으로 시작. 모델·가격은 7단계 시작 시 재조사.
- **서버 플랫폼**: 4단계에서 비교 후 결정. App Check·Play Integrity 연동 용이성에 가중치.

## 패키지 구조 (`com.pagecast.app`)
```
di/        AppContainer (수동 DI)
domain/    model, repository(인터페이스), text(SentenceSplitter, TextInputValidator)
data/      local(Room), repository(구현), importer(BookImporter, DocumentInspector)
tts/       TtsEngine 인터페이스, android/AndroidTtsEngine
playback/  NarrationController, ProgressRecorder, TtsPlayer, PlaybackService, PlaybackClient
ui/        theme, navigation, library, importer, reader
util/      AppLog (숫자 메타데이터만 받는 로그 래퍼)
```
이후 단계에서 `ocr/`, `script/`, `study/`, `dictionary/` 등을 추가한다. 빈 패키지는 미리 만들지 않는다.

## 보안 규칙 (항상 지킬 것) — 이 저장소는 public이다
- API 키, 비밀번호, 토큰을 앱 코드·리소스·BuildConfig·gradle 파일에 **절대** 넣지 않는다.
- 키가 필요한 기능(영한 사전, LLM 뜻풀이·대본화, 클라우드 TTS, 이미지 생성, 키가 필요한 커버 API)은 **모두 서버 함수를 경유**한다.
  서버 함수는 공개 엔드포인트이므로 App Check / Play Integrity와 설치·사용자별 한도로 남용을 막는다.
- **INTERNET 권한은 4단계에서 처음 추가**한다. 그 전 단계는 모두 기기 안에서 처리한다.
  추가할 때 `networkSecurityConfig`로 cleartext를 차단한다(HTTPS만).
- 서버로 보내는 책 내용은 최소화한다(선택한 단어·표현 + 그 문장). 장면 이미지만 예외로, 동의를 받은 뒤 페이지 본문을 보낸다.
  서버도 본문을 로그에 남기거나 저장하지 않는다.
- `.gitignore`에 `local.properties`, `.env*`, `*.jks`, `*.keystore`, `build/`, `.idea/` 등이 있다. 서명 키는 저장소 밖에서 관리한다.
- 사용자 입력(붙여넣기, TXT)은 `TextInputValidator`로 크기 제한(붙여넣기 20만 자, TXT 5MB)과 UTF-8 엄격 검증을 거친다.
  스트림은 제한 크기까지만 읽고, 선언된 파일 크기를 믿지 않는다.
- PDF·DOCX·EPUB은 신뢰할 수 없는 입력이다: 유지보수되는 라이브러리만, 파일 크기·페이지 수 상한, 메인 스레드 밖 + 시간 제한,
  파싱 실패 시 앱이 죽지 않고 안내. Zip은 항목 수·압축 해제 크기·압축률 상한(zip bomb), `..`·절대 경로 차단(zip slip),
  디스크에 풀지 않고 필요한 항목만 메모리로 읽는다. XML은 DOCDECL 처리 끔(XXE).
- 사용자가 올린 이미지는 다시 인코딩해 EXIF(특히 GPS)를 제거하고 테스트로 확인한다.
  다운로드 이미지는 크기 상한, 파일 헤더로 형식 판별(확장자 불신), 목표 크기로 줄여 디코드한다.
- **책 본문을 로그에 남기지 않는다.** 로그에는 길이·index 같은 메타데이터만(`AppLog`). release 빌드는 R8로 `Log` 호출 제거.
- 잠금화면·알림에는 책 제목과 "문장 n/N"만 표시하고 본문은 넣지 않는다.
  `PlaybackService`는 Media3 규약상 exported지만, 외부 컨트롤러에는 재생 관련 명령만 허용한다.
- `allowBackup=false`, 데이터 추출 규칙으로 클라우드 백업·기기 이전 제외.
- **커밋 금지**: 저작권 있는 책 본문, 그 책에서 생성한 장면 이미지, 다운로드한 커버, 본문이 들어간 사전 결과·캐시, 사전 원본 데이터.
  테스트에는 직접 쓴 짧은 문장만 쓴다. 번들 데이터의 라이선스 고지(OEWN CC BY 4.0 등)는 `NOTICE`와 앱 내 라이선스 화면에 표시한다.
- OWASP Mobile Top 10 관점에서 취약한 부분이 보이면 즉시 알린다.

## 작업 규칙
- 설명과 커밋 메시지는 한국어로 쓴다.
- 코드를 쓰기 전에 단계별 구현 계획을 보여 주고 승인을 받는다.
- 의미 있는 단위로 커밋한다. main 병합은 로컬에서 하지 않고, push와 PR 생성은 사용자 확인 후 진행한다.
- lint는 에러 0개를 유지하고, 경고는 보고만 한다(`@Suppress`로 숨기지 않는다).
- 외부 정보(라이선스, 가격, API 약관)는 확인된 것만 출처와 함께 쓰고, 확인 못 한 것은 "미확인"으로 표시한다.
- Android SDK 라이선스 동의는 자동으로 하지 않는다.

## 명령어
```bash
./gradlew testDebugUnitTest      # 단위 테스트
./gradlew lintDebug              # lint (리포트: app/build/reports/lint-results-debug.html)
./gradlew assembleDebug          # 디버그 APK
./gradlew installDebug           # 연결된 기기에 설치
```
