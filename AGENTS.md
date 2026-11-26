# PlanMate 공통 AGENTS 규칙

AI 코딩 도구가 이 레포에서 코드를 쓰기 전에 읽는 팀 공통 규칙이다.
파트별 규칙은 `docs/agents/AGENTS_A.md` ~ `AGENTS_D.md`에 있다. 작업 전에 내 파트 파일을 같이 읽는다.
파트 파일과 이 파일이 부딪히면 이 파일을 따른다.

## 1. 기준 문서

- 세부기능정의서 v33 (세부 기능 142개): 기능 동작, 검증 규칙, 안내 문구의 기준. `docs/세부기능정의서_v33.xlsx`
- DB설계서 v7 (테이블 38개): `docs/DB설계서_v7.xlsx`. `backend/src/main/resources/db/schema.sql`이 같은 내용
- 폴더 구조: `docs/PlanMate_폴더구조4.md` (클래스·파일 이름도 여기 적힌 그대로 쓴다)
- API 약속: `contracts/<기능 폴더 이름>.yaml`
- 문서가 레포에 없으면 사람에게 해당 기능 ID 내용을 달라고 한다.
- 문서에 없는 규칙·화면·문구는 지어내지 않는다. 문서끼리 다르거나 애매하면 코드를 쓰기 전에 사람에게 묻는다.
- 문서가 새 버전으로 바뀌면 이 파일과 `docs/agents/`의 버전 표시도 같이 고친다.

## 2. 파트 담당

E 파트 기능은 A 파트 팀원이 구현한다. 그래서 담당은 A·B·C·D 네 파트다.

| 파트 | 백엔드 `com.planmate.` | 프론트 `src/features/` | 파트 규칙 |
|---|---|---|---|
| A | auth, member, participant, notification, admin, common/security, savedplace, weather, checklist, cs | auth, member, participant, notification, admin, savedplace, weather, checklist, export, cs | AGENTS_A.md |
| B | trip, schedule, collab | trip, schedule, collab | AGENTS_B.md |
| C | place, ai, route | place, ai, route | AGENTS_C.md |
| D | community, budget | community, budget | AGENTS_D.md |
| 공통 | common (security 제외), PlanMateApplication.java | app/, shared/ | 관리 담당 1명 |

- savedplace, weather, checklist, export, cs는 원래 E 파트 기능이다. 지금은 A 담당이다.
- `contracts/`의 yaml은 같은 이름 폴더를 맡은 파트가 쓴다. `common.yaml`은 공통 관리 담당이 쓴다.

## 3. 개발환경 버전 (모두 같은 버전)

| 구분 | 버전 | 비고 |
|---|---|---|
| JDK | 21 (LTS, Temurin 권장) | build.gradle의 toolchain 21 |
| Spring Boot | 4.1.x | 처음 만든 사람이 고른 패치 번호를 build.gradle에 고정 |
| Spring Framework·Security·Data JPA, Hibernate, Jackson | Boot가 정한 버전 | 직접 버전 숫자를 적지 않는다 (Framework 7, Security 7, Hibernate 7, Jackson 3) |
| Gradle | 레포의 Gradle Wrapper | `gradle/wrapper/gradle-wrapper.properties`에 적힌 버전 |
| 빌드 스크립트 | Groovy (`build.gradle`) | Kotlin DSL 쓰지 않음 |
| Oracle DB | 21c XE | 19 이상이면 동작. 11g XE는 Hibernate 7이 지원하지 않아 쓰지 않음 |
| Oracle JDBC | Initializr의 Oracle Driver 그대로 | 버전은 Boot가 정함 |
| Node.js | 24 LTS | |
| npm | Node 24에 들어 있는 것 | 설치는 `npm ci` |
| Vite | 8.x | create-vite 기본 |
| React | 19.x | create-vite 기본 |
| TypeScript | 7.0.x | create-vite 기본 |
| 린트 | oxlint | create-vite 기본. ESLint·typescript-eslint 추가하지 않음 |
| react-router | 8.x | `react-router-dom` 패키지는 v8에 없음 |
| html2canvas | 1.4.x | 일정표 PNG 저장(export)에만 사용 |

- 버전을 바꾸거나 라이브러리를 추가하려면 먼저 팀에 공유한다. AI는 build.gradle·package.json에 의존성을 마음대로 추가하지 않는다.
- 백엔드는 레포의 gradlew로만 빌드한다(PC에 깔린 gradle 쓰지 않음). 프론트는 package-lock.json을 커밋하고 `npm ci`로 설치한다.

### 처음 만들 때 (1명이 만들고 커밋)
- 백엔드: Spring Initializr - Gradle - Groovy, Java 21, Spring Boot 4.1.x, Group `com.planmate`, Name `PlanMate`, Package `com.planmate`
  - 의존성: Spring Web, Spring Data JPA, Validation, Spring Security, Spring Session JDBC, WebSocket, Oracle Driver, Lombok
- 프론트: `npm create vite@latest frontend -- --template react-ts` 후 `npm install react-router html2canvas`

### Spring Boot 4에서 주의할 점 (AI가 옛날 3.x 코드를 쓰기 쉬움)
- 웹 의존성은 `spring-boot-starter-webmvc` (`spring-boot-starter-web`은 옛 이름)
- 테스트 의존성은 기능별 `-test` 스타터 (예: `spring-boot-starter-webmvc-test`, `spring-boot-starter-data-jpa-test`)
- Jackson 3: import는 `tools.jackson...`. 어노테이션(`com.fasterxml.jackson.annotation`)만 옛 패키지 그대로. 설정은 `ObjectMapper` 대신 `JsonMapper` 빈
- 테스트: `@MockBean` 대신 `@MockitoBean`. `@SpringBootTest`에서 MockMvc를 쓰려면 `@AutoConfigureMockMvc`
- Spring Security는 람다 방식으로만 쓴다 (`http.authorizeHttpRequests(auth -> ...)`)
- `javax.*` 대신 `jakarta.*`
- 외부 API 호출은 `RestClient`로 한다 (`RestTemplate` 쓰지 않음)
- Hibernate dialect는 적지 않는다 (자동으로 고름)

## 4. 실행·확인 명령

| 할 일 | 명령 (Windows는 `gradlew.bat`) |
|---|---|
| 백엔드 실행 | `cd backend` 후 `./gradlew bootRun --args='--spring.profiles.active=local'` |
| 백엔드 빌드·테스트 | `./gradlew build` |
| 프론트 설치 | `cd frontend` 후 `npm ci` |
| 프론트 실행 | `npm run dev` (http://localhost:5173) |
| 프론트 빌드·린트 | `npm run build`, `npm run lint` |

- DB 테이블은 각자 자기 Oracle 계정에 `schema.sql`을 직접 실행해서 만든다. 서버가 자동으로 만들지 않는다.
- 내 컴퓨터 설정(DB 주소·계정)은 `application-local.yml`에 둔다(git에 안 올림).

## 5. 폴더·이름 규칙

- 기능 폴더 안: controller/, service/, client/(외부 API), job/(정해진 시간 작업), repository/, dto/, entity/. collab만 websocket/이 있다.
- DTO: 받는 값은 `~Request`, 보내는 값은 `~Response`
- DB는 대문자+밑줄(`TRIP_ID`), 자바·JSON·TypeScript는 camelCase(`tripId`)
- 프론트: pages/는 주소가 있는 화면, components/는 화면 안 부품, api/는 서버 호출 함수, hooks/는 반복 로직
- 새 파일은 폴더구조4.md의 같은 종류 파일과 같은 모양으로 만든다.

## 6. 백엔드 규칙

### 계층
- controller는 요청을 받고 응답만 만든다. 규칙 검사와 처리는 service, DB 읽기·쓰기는 repository.
- 화면에서 막는 값도 서버에서 다시 검사한다(화면을 거치지 않은 요청 대비). 기준은 기능정의서 "검증 규칙".

### 응답·에러
- 모든 응답은 `ApiResponse {success, data, message}`, 목록은 `PageResponse`.
  - 실패하면 `data`에 `{code, fieldErrors}`가 들어간다. code는 ErrorCode 이름이다(형식은 contracts/common.yaml).
- 규칙 위반은 `throw new BusinessException(ErrorCode.XXX)`. 안내 문구는 기능정의서 "예외·오류 안내 문구"를 그대로 ErrorCode에 넣는다.
  - ErrorCode는 `ErrorCode.java`의 자기 파트 구역(`// ===== B 파트 ...`)에만 추가한다. 공통 파일이라 추가 전에 팀에 공유한다.
  - 입력 검증은 `@Valid @RequestBody` DTO로 하고, 검증 어노테이션 `message`에 기능정의서 문구를 넣는다.
- try-catch로 에러를 삼키지 않는다. `GlobalExceptionHandler`가 한곳에서 바꾼다.

### JPA·DB
- 테이블은 schema.sql로만 만든다. `spring.jpa.hibernate.ddl-auto: none`. 엔티티에 맞춰 테이블을 바꾸지 않는다.
- 엔티티에 DB 이름을 그대로 적는다: `@Table(name = "TRIP")`, `@Column(name = "TRIP_ID")`
- PK는 시퀀스 `SEQ_테이블명`: `@GeneratedValue(strategy = GenerationType.SEQUENCE, generator = ...)` + `@SequenceGenerator(sequenceName = "SEQ_TRIP", allocationSize = 1)`
  - 예외: EXCHANGE_RATE(통화+발표 날짜 복합 키), RECOMMENDED_STAY(카테고리 문자 키), REQUEST_LOG(요청 식별값 문자 키), SPRING_SESSION 두 테이블(Spring Session이 관리, 엔티티 만들지 않음)
- 자료형: NUMBER(19) -> Long, 작은 NUMBER -> Integer, 소수 있는 NUMBER -> BigDecimal, DATE -> LocalDate, TIMESTAMP -> LocalDateTime, CLOB -> String + `@Lob`, 시각 VARCHAR2(5) `"HH:mm"`(24시간) -> String
- Y/N 칸(CHAR(1)) -> Boolean + `@Convert(converter = org.hibernate.type.YesNoConverter.class)`
- 상태값(ACTIVE·TRASHED 등) -> enum + `@Enumerated(EnumType.STRING)`. 값 목록은 DB설계서 "허용값" 그대로.
- VERSION 칸(VISIT, VISIT_MEMO)에 JPA `@Version`을 쓰지 않는다. 동시 수정은 나중에 저장한 값으로 덮어쓰는 규칙이라 서비스에서 저장할 때 직접 +1 한다.
- 자식 행 삭제는 DB의 ON DELETE CASCADE / SET NULL에 맡긴다. JPA `CascadeType.REMOVE`·`orphanRemoval`로 같은 일을 또 하지 않는다.
- 유일 조건 위반(`DataIntegrityViolationException`)은 잡아서 맞는 ErrorCode로 바꾼다. 조건부 유일 인덱스(여행당 OWNER 1명, 대기 소유권 이전 1건, 소셜 회원)는 DB에만 있으니 서비스에서도 먼저 검사한다.
- 같은 요청 두 번 처리 방지(여행 생성·일정 저장·게시·복제·AI 적용)는 `common/duplicate/DuplicateRequestChecker`를 쓴다. 요청 식별값은 화면이 만들어 요청 본문 `requestKey`(1~64자, UUID 권장)로 보낸다. 서비스의 `@Transactional` 안에서 `findProcessed(requestKey, memberId)` → 업무 처리 → `record(requestKey, memberId, tripId)` 순서로 부른다.

### 다른 파트 데이터
- 다른 파트 테이블은 직접 조회·수정하지 않는다. 그 파트의 Service 메서드를 부른다(8번 표).
- 다른 파트 엔티티를 `@ManyToOne`으로 묶지 않는다. ID 값(`Long memberId`)으로 들고 있는다.

### 권한
- 로그인·계정 상태: `AccountStatusChecker`, 여행 자료: `TripAccessChecker`, 관리자: `AdminAccessChecker` (모두 common/security, A 담당). 각자 권한 검사 코드를 따로 만들지 않는다.
  - 주소 규칙: 모든 API는 `/api`로 시작한다. `/api/auth/**` 공개, `/api/admin/**` 관리자, 나머지 `/api/**` 로그인 필요. 로그인 없이 열어야 하는 API는 A에게 요청한다(SecurityConfig는 A 담당).
  - 서비스에서 로그인 회원은 `accountStatusChecker.requireLoginMemberId()`로 얻는다. 여행 자료는 저장 직전에 `tripAccessChecker.requireParticipant / requireEditor / requireOwner(…, ErrorCode)`를 부른다. 기능별 거부 문구(예: "소유자만 초대할 수 있어요.")는 자기 ErrorCode 구역에 두고 넘긴다.
  - 여행 상태(TRIP.STATUS)는 B가 구현한 `TripStatusReader`로 읽는다. 휴지통 여행의 소유자 전용 동작은 `requireOwnerOfTrashed`를 쓴다.
- 여행 역할은 TRIP_MEMBER.ROLE = OWNER / EDITOR / VIEWER. 저장 직전에 현재 역할을 다시 확인한다.
- 휴지통(TRIP.STATUS = TRASHED) 여행은 참여자 접근을 막는다.
- 이용 제한 회원은 커뮤니티(PUB) 쓰기만 막힌다. 여행·일정·AI·경비·보관함·문의는 쓸 수 있다.

### 시간
- 여행 안의 날짜·시각은 여행지 현지 기준으로, 시간대 변환 없이 저장한다(CITY.TIMEZONE은 IANA 이름).
- 기록 시각(CREATED_AT 등)은 서버 시각 LocalDateTime. 정해진 시간 작업(`@Scheduled`)은 `zone = "Asia/Seoul"`.

### 알림
- 알림은 별도 기능이 아니라 각 동작의 결과로 남긴다. 저장은 A의 `NotificationService`만 부른다(NOTIFICATION 테이블 직접 쓰지 않음).
- 알림 저장이 실패해도 원래 동작은 실패시키지 않는다.

### 외부 API·비밀값
- 키·비밀번호는 코드에 쓰지 않는다. application.yml에서 `${환경변수}`로 읽는다. 이름은 `.env.example`을 따르고, 새로 필요하면 `.env.example`에 이름만(값 없이) 추가하고 팀에 알린다.
- REST API 키(카카오 REST, 구글 장소·경로, 한국수출입은행, AI 등)는 서버에서만 쓴다. 화면에는 지도 표시용 JavaScript 키만 둔다(`VITE_`로 시작, 허용 도메인 제한).
- 외부 API는 각 기능의 client/에서만 부른다.
- 비밀번호·인증번호·세션·소셜 토큰·초대 토큰 원문을 로그·URL·에러 응답에 남기지 않는다. 단, 서비스 초대 링크(`/invite/:token`) 자체에는 초대 토큰이 들어간다. 그 밖의 URL 매개변수·로그·에러 응답에는 남기지 않는다.

## 7. 프론트 규칙

- 서버 호출은 `shared/api/client.ts`를 거친다(서버 주소·로그인 쿠키·공통 에러 처리). 각 기능은 `api/<기능>Api.ts`에 함수로 둔다. POST·PUT·PATCH·DELETE의 CSRF 헤더(`X-XSRF-TOKEN`)는 client.ts가 자동으로 붙인다.
- `VITE_` 환경변수는 `frontend/.env.local`에 둔다(Vite는 레포 맨 위 .env를 읽지 않는다).
- 실시간 연결은 `shared/api/websocket.ts`를 거친다.
- 공통 부품(Button, Input, Modal, ConfirmDialog, Toast, Loading, EmptyState)은 `shared/components` 것을 쓴다. 기능 폴더에 같은 부품을 또 만들지 않는다. 상태 창은 `useToast()`(`shared/hooks/useToast.ts`)로 띄운다.
- 화면 주소는 `app/router.tsx`에 모여 있다. 자기 page를 만들면 자기 경로 줄의 `element`만 page 컴포넌트로 바꾼다.
- 지도는 C의 `features/place/components/MapView.tsx`만 쓴다.
- 날짜·금액 표시 함수는 `shared/utils`(date.ts, money.ts)에 모은다. 형식은 기능정의서 "화면 표시"를 따른다.
- `any`를 쓰지 않는다. 서버 응답 타입은 contracts에 맞춰 api 파일에 적는다.
- 스타일은 `shared/styles/variables.css` 값을 쓴다. CSS 라이브러리는 팀 합의 없이 추가하지 않는다.
- 1차는 PC 화면만 만든다(기준 폭 1024px 이상).

## 8. 파트끼리 같이 쓰는 것

다른 파트 코드는 고치지 않고 부르기만 한다.

| 쓰는 것 | 주인 | 부르는 파트 |
|---|---|---|
| AccountStatusChecker, TripAccessChecker, AdminAccessChecker | A | 전체 |
| ParticipantService (TRIP_MEMBER, 초대 링크) | A | B, C, D |
| NotificationService (알림 저장) | A | 전체 |
| MemberService (닉네임), HomeAddressService (집 주소) | A | B, C, D |
| SavedPlaceService (내 보관함) | A (원래 E) | B, C |
| TripService (여행 생성·상태·인원), CityService (CITY) | B | C, D |
| ScheduleSaveService (일정 저장) | B | C (AI 적용), D (복제) |
| CandidateService (공동 후보), SyncService (변경 전파) | B | A, C, D |
| 공통 장소 형식, 권장 체류시간, RouteService (구간 경로) | C | A, B, D |
| FareService (FARE_EXPENSE_LINK) | C | D |
| MapView.tsx | C | B, D |
| ReportService (REPORT), 공개 게시물 상태 | D | A, B |
| BudgetService (경비 항목) | D | A, B, C |
| DuplicateRequestChecker | 공통 | B, C, D |

- 메서드 이름·입력·출력은 먼저 담당 파트와 맞춘다.
- 필요한 메서드가 아직 없으면 그 파트 파일을 고치지 않는다. 내 코드에 `// TODO(A에 요청): 메서드 이름` 처럼 자리만 남기고 사람에게 알린다.

## 9. 수정 범위

- 내 파트 폴더만 고친다.
- 다른 파트 폴더, common/, shared/, app/, build.gradle, package.json, application.yml, schema.sql, contracts/common.yaml, AGENTS.md, CLAUDE.md, docs/는 사람이 시킬 때만 고친다. 고칠 때는 "팀에 먼저 공유해야 하는 파일"이라고 알린다.
  - 예외: `app/router.tsx`에서 자기 page 경로의 `element`를 바꾸는 것은 허용한다. 경로 추가·변경은 사람이 시킬 때만 한다.
- DB 컬럼이 더 필요하면 코드보다 DB설계서·ERD를 먼저 고쳐야 한다고 사람에게 알린다.
- API를 새로 만들거나 바꾸면 `contracts/<기능>.yaml`도 같이 고친다.
- 한 번에 세부 기능 하나씩 작업한다. 기능정의서에 없는 기능을 더하지 않는다.

## 10. git

* main·develop에 직접 push하지 않는다. 작업 브랜치는 `이니셜/작업내용` 형식을 쓴다(영어 소문자, 단어 사이는 하이픈 `-`, 이니셜만으로 된 브랜치 금지).
  * A 파트(+원래 E): `pjh/...` (예: `pjh/login`, `pjh/invite`, `pjh/checklist`)
  * B 파트: `khy/...` (예: `khy/trip-create`, `khy/schedule`)
  * C 파트: `pjy/...` (예: `pjy/map-search`, `pjy/ai-recommend`)
  * D 파트: `kjh/...` (예: `kjh/community`, `kjh/expense`)
- 커밋 메시지: `feat:` `fix:` `docs:` `refactor:` `test:` + 한국어 설명 + 기능 ID (예: `feat: 여행 생성 API (TRP-01-03)`)
- AI는 사람이 시킬 때만 commit·push 한다.
- git에 올리지 않는 파일: `.env`, `application-local.yml`, `CLAUDE.local.md`, `build/`, `.gradle/`, `node_modules/`, `.idea/`

## 11. 작업을 끝낼 때

- 백엔드는 `./gradlew build`, 프론트는 `npm run build`와 `npm run lint`가 성공하는지 확인한다.
- 기능정의서의 검증 규칙·안내 문구와 맞는지 확인한다.
- 바꾼 파일 목록과 이유를 사람에게 쉬운 말로 설명한다.
- 코드 주석은 한국어로, 기능 ID를 함께 적는다 (예: `// TRP-01-01 제목 1~50자`).

## 12. Claude Code 쓰는 사람

- 레포 맨 위 `CLAUDE.md`가 이 파일을 불러온다.
- 내 파트 규칙을 자동으로 불러오려면 레포 맨 위에 `CLAUDE.local.md`를 만들고 아래처럼 적는다(파트 글자만 바꿈). 이 파일은 git에 올리지 않는다.

```
@docs/agents/AGENTS_B.md
```
