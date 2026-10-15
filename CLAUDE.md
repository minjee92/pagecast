# Pagecast — 낭독 AI 앱 (Android 전용)

## 배경
영어 원서를 오디오북과 함께 들으며 읽는 경험을, 오디오가 없는 자작 소설·팬픽션에서도 누리기 위한 개인용 앱.
텍스트(나중에는 책 사진)를 넣으면 캐릭터별 목소리와 감정 연기로 읽어 주고, 읽는 위치를 하이라이트한다.

## 로드맵
1. **(현재)** 텍스트 붙여넣기/TXT 불러오기 → 단일 목소리 낭독 → 읽는 문장·단어 하이라이트
2. 카메라 촬영 → OCR(Google ML Kit) → 텍스트 정리(머리글/쪽번호 제거, 하이픈·문단 복원) — `ocr/` 패키지 추가
3. LLM으로 대본화: 문장을 `{화자, 대사, 감정 지시}` JSON 세그먼트로 변환 — `script/` 패키지 추가
4. 책별 캐릭터-목소리 캐스팅 저장, 클라우드 TTS(감정 연기 가능 엔진), 오디오 캐싱, 오프라인 재생

## 기술 결정 (고정)
- Android 전용, 테스트 기기는 갤럭시폰. minSdk 26 (`UtteranceProgressListener.onRangeStart`가 API 26+)
- Kotlin + Jetpack Compose, 단일 Activity, 단일 `:app` 모듈, 수동 DI(`di/AppContainer`)
- AGP 9 built-in Kotlin 사용 (`org.jetbrains.kotlin.android` 플러그인 적용하지 않음), 어노테이션 처리는 KSP
- 의존성 버전은 `gradle/libs.versions.toml`에서만 관리
- 백그라운드 재생·잠금화면 컨트롤: Media3 (`SimpleBasePlayer` + `MediaSessionService`)
- 로컬 저장: Room (스키마 JSON은 `app/schemas/`에 커밋, 버전 올릴 때 Migration 작성)
- TTS는 반드시 `tts/TtsEngine` 인터페이스로 추상화
  - 1단계 구현체: `AndroidTtsEngine` (내장 TextToSpeech, 무료, 키 불필요). 단어 하이라이트는 `onRangeStart`
  - `Utterance`에 `voice`, `emotion`이 처음부터 있다. 내장 엔진은 emotion을 무시하고, 클라우드 엔진은 서버 함수로 전달
  - 4단계 클라우드 엔진: 서버 함수가 오디오 + 단어 타이밍을 반환 → 캐시 → 같은 인터페이스 뒤에서 재생
  - Samsung TTS가 `onRangeStart`를 보내는지 불확실 → 단어 이벤트가 없으면 문장 하이라이트만 표시
- 도메인 모델: `Book`, `Chapter`, `Segment(text, speakerId, emotion)`, `Character`, `VoiceCast`, `ReadingProgress`
  - 1단계: 책당 Chapter 1개, 문장 1개 = Segment 1개, `speakerId = null`(내레이터), `emotion = NEUTRAL`
- 문장 분할: `java.text.BreakIterator`(문장 단위) + 약어 예외(Mr., Dr. 등) 병합 보정만
- 화면 간에는 본문이 아니라 `bookId`만 전달

## 패키지 구조 (`com.pagecast.app`)
```
di/        AppContainer (수동 DI)
domain/    model, repository(인터페이스), text(SentenceSplitter, TextInputValidator)
data/      local(Room), repository(구현), importer(SAF TXT 읽기)
tts/       TtsEngine 인터페이스, android/AndroidTtsEngine
playback/  NarrationController(재생 상태 머신), Media3 서비스
ui/        theme, navigation, library, importer, reader
```

## 보안 규칙 (항상 지킬 것) — 이 저장소는 public이다
- API 키, 비밀번호, 토큰을 앱 코드·리소스·BuildConfig·gradle 파일에 **절대** 넣지 않는다.
  3단계 이후 LLM·클라우드 TTS 호출은 **별도 서버 함수를 경유**한다(앱에는 키가 없다).
- `.gitignore`에 `local.properties`, `.env*`, `*.jks`, `*.keystore`, `build/`, `.idea/` 등이 있다. 서명 키는 저장소 밖에서 관리한다.
- 사용자 입력(붙여넣기, TXT)은 `TextInputValidator`로 크기 제한(붙여넣기 20만 자, TXT 5MB)과 UTF-8 엄격 검증을 거친다.
  스트림은 제한 크기까지만 읽고, 선언된 파일 크기를 믿지 않는다.
- **책 본문을 로그에 남기지 않는다.** 로그에는 길이·index 같은 메타데이터만. release 빌드는 R8로 `Log` 호출 제거.
- 잠금화면·알림에는 책 제목과 "문장 n/N"만 표시하고 본문은 넣지 않는다.
- `allowBackup=false`, 데이터 추출 규칙으로 클라우드 백업·기기 이전 제외. 1단계는 INTERNET 권한 없음.
- 저작권 있는 책 본문을 커밋하지 않는다. 테스트에는 직접 쓴 짧은 문장만 쓴다.
- OWASP Mobile Top 10 관점에서 취약한 부분이 보이면 즉시 알린다.

## 작업 규칙
- 설명과 커밋 메시지는 한국어로 쓴다.
- 의미 있는 단위로 커밋한다. main 병합은 로컬에서 하지 않고, push와 PR 생성은 사용자 확인 후 진행한다.
- lint는 에러 0개를 유지하고, 경고는 보고만 한다(`@Suppress`로 숨기지 않는다).

## 명령어
```bash
./gradlew testDebugUnitTest      # 단위 테스트
./gradlew lintDebug              # lint (리포트: app/build/reports/lint-results-debug.html)
./gradlew assembleDebug          # 디버그 APK
./gradlew installDebug           # 연결된 기기에 설치
```
