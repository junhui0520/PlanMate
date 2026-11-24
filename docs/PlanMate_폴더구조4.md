# PlanMate 폴더 구조

기준: 세부기능정의서 v33 (세부 기능 142개), DB설계서 v7 (테이블 38개)
백엔드 Spring Boot + JPA + Oracle, 프론트 React + TypeScript

```
PlanMate/
├── .github/workflows/                                # PR 올리면 자동 빌드·검사하는 설정 (나중에 추가)
│   └── .gitkeep                                      # 빈 폴더를 git에 올리기 위한 빈 파일
├── backend/                                          # 서버 (Spring Boot + JPA + Oracle)
│   ├── build.gradle                                  # 사용할 라이브러리·빌드 설정
│   ├── settings.gradle                               # 프로젝트 이름 설정
│   ├── src/main/java/com/planmate/                   # 자바 코드
│   │   ├── PlanMateApplication.java                  # 서버 실행 시작점
│   │   ├── common/                                   # 공통 코드 (관리 담당 1명, 고칠 때 팀에 먼저 공유)
│   │   │   ├── config/
│   │   │   │   ├── WebConfig.java                    # CORS(프론트 주소 허용) 등 웹 설정
│   │   │   │   ├── WebSocketConfig.java              # WebSocket 연결 주소 설정
│   │   │   │   ├── JpaConfig.java                    # JPA 설정 (생성·수정 시각 자동 기록 등)
│   │   │   │   └── SchedulerConfig.java              # 정해진 시간에 도는 작업 켜기 (환율·영구 삭제 등)
│   │   │   ├── exception/
│   │   │   │   ├── ErrorCode.java                    # 에러 코드·안내 문구 목록
│   │   │   │   ├── BusinessException.java            # 규칙 위반일 때 던지는 공통 예외
│   │   │   │   └── GlobalExceptionHandler.java       # 모든 에러를 한곳에서 받아 공통 응답으로 바꿈
│   │   │   ├── response/
│   │   │   │   ├── ApiResponse.java                  # 공통 응답 모양 {success, data, message}
│   │   │   │   └── PageResponse.java                 # 목록(페이지) 응답 모양
│   │   │   ├── security/                             # [A] 로그인·권한 검사 (ACC-07)
│   │   │   │   ├── SecurityConfig.java               # 로그인이 필요한 주소·세션 설정
│   │   │   │   ├── AccountStatusChecker.java         # 로그인·계정 상태 판정 (ACC-07-01)
│   │   │   │   ├── TripAccessChecker.java            # 여행·개인 자료 접근 검사 (ACC-07-02)
│   │   │   │   └── AdminAccessChecker.java           # 관리자 검사 (ACC-07-03)
│   │   │   ├── duplicate/                            # 같은 요청 두 번 처리 방지 (여행 생성·일정 저장·게시·복제)
│   │   │   │   ├── DuplicateRequestChecker.java      # 같은 요청인지 확인
│   │   │   │   ├── RequestLogRepository.java         # REQUEST_LOG (요청 기록) 넣기·꺼내기
│   │   │   │   └── RequestLog.java                   # REQUEST_LOG 테이블과 연결된 클래스
│   │   │   └── util/
│   │   │       └── DateTimeUtil.java                 # 날짜·시간 형식 맞추기
│   │   ├── auth/                                     # [A] 가입·로그인·비밀번호 (ACC-01~04)
│   │   │   ├── controller/
│   │   │   │   ├── AuthController.java               # 이메일 가입·로그인·로그아웃·비밀번호 요청 받기
│   │   │   │   └── OAuthController.java              # 소셜 로그인 요청 받기 (ACC-03)
│   │   │   ├── service/
│   │   │   │   ├── SignupService.java                # 이메일 가입 처리 (ACC-01-01·04)
│   │   │   │   ├── EmailVerificationService.java     # 인증번호 발송·확인 (ACC-01-02·03, ACC-04-02)
│   │   │   │   ├── LoginService.java                 # 로그인·로그아웃 (ACC-02)
│   │   │   │   ├── OAuthService.java                 # 소셜 인증·최초 가입, state는 서버 세션에 저장 (ACC-03)
│   │   │   │   └── PasswordService.java              # 비밀번호 변경·재설정 (ACC-04)
│   │   │   ├── client/
│   │   │   │   ├── MailClient.java                   # 인증번호 메일 보내기
│   │   │   │   └── OAuthClient.java                  # 카카오·네이버·구글·Apple 인증 서버 호출
│   │   │   ├── repository/
│   │   │   │   ├── EmailVerificationRepository.java  # EMAIL_VERIFICATION (이메일 인증) 넣기·꺼내기
│   │   │   │   └── LoginAttemptRepository.java       # LOGIN_ATTEMPT (로그인 시도 기록) 넣기·꺼내기
│   │   │   ├── dto/
│   │   │   │   ├── SignupRequest.java                # 가입 입력값
│   │   │   │   ├── VerificationSendRequest.java      # 인증번호 받기 입력값
│   │   │   │   ├── VerificationConfirmRequest.java   # 인증번호 확인 입력값
│   │   │   │   ├── LoginRequest.java                 # 로그인 입력값
│   │   │   │   ├── LoginResponse.java                # 로그인 결과
│   │   │   │   ├── SocialSignupRequest.java          # 소셜 최초 가입 입력값
│   │   │   │   ├── PasswordChangeRequest.java        # 비밀번호 변경 입력값
│   │   │   │   └── PasswordResetRequest.java         # 비밀번호 재설정 입력값
│   │   │   └── entity/
│   │   │       ├── EmailVerification.java            # EMAIL_VERIFICATION 테이블과 연결된 클래스
│   │   │       └── LoginAttempt.java                 # LOGIN_ATTEMPT 테이블과 연결된 클래스
│   │   ├── member/                                   # [A] 내 정보·탈퇴 (ACC-05·06)
│   │   │   ├── controller/
│   │   │   │   ├── MemberController.java             # 내 정보 조회·수정 요청 받기 (ACC-05)
│   │   │   │   └── WithdrawalController.java         # 탈퇴 요청 받기 (ACC-06)
│   │   │   ├── service/
│   │   │   │   ├── MemberService.java                # 닉네임·프로필 사진 (ACC-05-01)
│   │   │   │   ├── HomeAddressService.java           # 집 주소 등록·수정·삭제, MEMBER의 집 주소 칸 (ACC-05-02)
│   │   │   │   └── WithdrawalService.java            # 탈퇴 영향 조회·탈퇴 확정 (ACC-06-01·03)
│   │   │   ├── job/
│   │   │   │   └── WithdrawalScheduler.java          # 2주 뒤 회원 삭제(개인 경비·체크리스트 먼저 삭제)·소셜 연결 해제 재처리 (ACC-06-03·04)
│   │   │   ├── repository/
│   │   │   │   ├── MemberRepository.java             # MEMBER (회원) 넣기·꺼내기
│   │   │   │   └── WithdrawalJobRepository.java      # WITHDRAWAL_JOB (탈퇴 처리 작업) 넣기·꺼내기
│   │   │   ├── dto/
│   │   │   │   ├── MyInfoResponse.java               # 내 정보 결과
│   │   │   │   ├── ProfileUpdateRequest.java         # 닉네임·프로필 사진 수정값
│   │   │   │   ├── HomeAddressRequest.java           # 집 주소 입력값
│   │   │   │   └── WithdrawalImpactResponse.java     # 탈퇴하면 영향받는 여행 목록
│   │   │   └── entity/
│   │   │       ├── Member.java                       # MEMBER 테이블과 연결된 클래스
│   │   │       └── WithdrawalJob.java                # WITHDRAWAL_JOB 테이블과 연결된 클래스
│   │   ├── participant/                              # [A] 초대·참여자·소유권 (COL-01~05)
│   │   │   ├── controller/
│   │   │   │   ├── InviteController.java             # 초대 링크 발급·폐기·참여 요청 받기 (COL-01·02)
│   │   │   │   ├── ParticipantController.java        # 참여자 목록·역할·나가기·내보내기 (COL-03·04)
│   │   │   │   └── OwnershipController.java          # 소유권 이전 요청·수락·취소 (COL-05)
│   │   │   ├── service/
│   │   │   │   ├── InviteService.java                # 초대 링크 발급·폐기·참여 등록
│   │   │   │   ├── ParticipantService.java           # 참여자 조회·역할 변경·나가기·내보내기
│   │   │   │   └── OwnershipService.java             # 소유권 이전 요청·수락·거절·만료
│   │   │   ├── repository/
│   │   │   │   ├── TripInviteRepository.java         # TRIP_INVITE (초대 링크) 넣기·꺼내기
│   │   │   │   ├── TripMemberRepository.java         # TRIP_MEMBER (여행 참여자) 넣기·꺼내기
│   │   │   │   └── OwnershipTransferRepository.java  # OWNERSHIP_TRANSFER (소유권 이전 요청) 넣기·꺼내기
│   │   │   ├── dto/
│   │   │   │   ├── InviteLinkResponse.java           # 초대 링크 결과
│   │   │   │   ├── InviteInfoResponse.java           # 초대받은 여행 정보 (참여 확인 화면용)
│   │   │   │   ├── ParticipantResponse.java          # 참여자 한 명 정보
│   │   │   │   ├── RoleChangeRequest.java            # 역할 변경 입력값
│   │   │   │   ├── OwnershipTransferRequest.java     # 소유권 이전 요청 입력값
│   │   │   │   └── OwnershipTransferResponse.java    # 소유권 이전 요청 상태
│   │   │   └── entity/
│   │   │       ├── TripInvite.java                   # TRIP_INVITE 테이블과 연결된 클래스
│   │   │       ├── TripMember.java                   # TRIP_MEMBER 테이블과 연결된 클래스
│   │   │       └── OwnershipTransfer.java            # OWNERSHIP_TRANSFER 테이블과 연결된 클래스
│   │   ├── notification/                             # [A] 알림함 (SUP-03) - 다른 파트는 NotificationService를 불러서 알림 저장
│   │   │   ├── controller/
│   │   │   │   └── NotificationController.java       # 알림함 조회·읽음 요청 받기 (SUP-03-01)
│   │   │   ├── service/
│   │   │   │   ├── NotificationService.java          # 알림 저장, 같은 사람·종류·대상이면 기존 알림을 안 읽음으로 (다른 파트가 호출, SUP-03-02)
│   │   │   │   └── NotificationMessageMaker.java     # 이벤트별 받을 사람·문구 정하기 (SUP-03-03)
│   │   │   ├── job/
│   │   │   │   └── NotificationScheduler.java        # 하루 1번 30일 지난 알림 삭제 (SUP-03-02)
│   │   │   ├── repository/
│   │   │   │   └── NotificationRepository.java       # NOTIFICATION (알림) 넣기·꺼내기
│   │   │   ├── dto/
│   │   │   │   ├── NotificationResponse.java         # 알림 한 개 정보
│   │   │   │   └── UnreadCountResponse.java          # 안 읽은 알림 개수 (배지용)
│   │   │   └── entity/
│   │   │       └── Notification.java                 # NOTIFICATION 테이블과 연결된 클래스
│   │   ├── admin/                                    # [A] 회원·신고 운영 (ADM-01·02)
│   │   │   ├── controller/
│   │   │   │   ├── AdminMemberController.java        # 회원 조회·이용 제한·해제 요청 받기 (ADM-01)
│   │   │   │   └── AdminReportController.java        # 신고 목록·상세·처리 요청 받기 (ADM-02)
│   │   │   ├── service/
│   │   │   │   ├── AdminMemberService.java           # 회원 목록·상세 조회·열람 기록 남기기 (ADM-01-01·04)
│   │   │   │   ├── MemberRestrictionService.java     # 이용 제한 설정·해제 (ADM-01-02·03)
│   │   │   │   ├── ReportReviewService.java          # 신고 검토·비공개·기각·결과 알림 (ADM-02)
│   │   │   │   └── ReauthService.java                # 관리자 조치 전 본인 재확인, 서버 세션에 저장 (ACC-07-03)
│   │   │   ├── job/
│   │   │   │   └── RestrictionExpireScheduler.java   # 이용 제한 기간 끝나면 자동 해제 (ADM-01-03)
│   │   │   ├── repository/
│   │   │   │   ├── MemberRestrictionRepository.java  # MEMBER_RESTRICTION (커뮤니티 이용 제한) 넣기·꺼내기
│   │   │   │   ├── MemberViewLogRepository.java      # MEMBER_VIEW_LOG (회원 정보 열람) 넣기·꺼내기
│   │   │   │   └── ReportActionRepository.java       # REPORT_ACTION (신고·콘텐츠 조치) 넣기·꺼내기
│   │   │   ├── dto/
│   │   │   │   ├── AdminMemberResponse.java          # 회원 목록 한 줄
│   │   │   │   ├── RestrictionRequest.java           # 이용 제한 입력값
│   │   │   │   ├── ActionLogResponse.java            # 조치 이력 한 줄 (이용 제한·신고 조치)
│   │   │   │   ├── ReportListResponse.java           # 신고 목록 한 줄
│   │   │   │   ├── ReportDetailResponse.java         # 신고 상세·현재 콘텐츠
│   │   │   │   └── ReportProcessRequest.java         # 신고 처리 입력값
│   │   │   └── entity/
│   │   │       ├── MemberRestriction.java            # MEMBER_RESTRICTION 테이블과 연결된 클래스
│   │   │       ├── MemberViewLog.java                # MEMBER_VIEW_LOG 테이블과 연결된 클래스
│   │   │       └── ReportAction.java                 # REPORT_ACTION 테이블과 연결된 클래스
│   │   ├── trip/                                     # [B] 여행 생성·목록·수정·삭제 (TRP-01~05)
│   │   │   ├── controller/
│   │   │   │   ├── TripController.java               # 여행 생성·목록·상세·수정 요청 받기 (TRP-01~04)
│   │   │   │   ├── CityController.java               # 도시 검색 요청 받기 (TRP-01-02)
│   │   │   │   ├── MainController.java               # 메인 화면 데이터 (TRP-02-03)
│   │   │   │   └── TrashController.java              # 삭제·휴지통·복원 요청 받기 (TRP-05)
│   │   │   ├── service/
│   │   │   │   ├── TripService.java                  # 여행 생성·목록·검색·상세·정보 수정 (TRP-01~03)
│   │   │   │   ├── TripPeriodService.java            # 기간 변경 확정·일정 비우기 (TRP-03-03)
│   │   │   │   ├── CityService.java                  # 도시 검색·처음 선택 시 자동 등록 (TRP-01-02)
│   │   │   │   ├── MainService.java                  # 다가오는 여행·인기 공개 여행 모으기 (TRP-02-03)
│   │   │   │   └── TrashService.java                 # 휴지통 이동·목록·복원·영구 삭제 (TRP-05)
│   │   │   ├── job/
│   │   │   │   └── TrashPurgeScheduler.java          # 보관 기간 끝난 여행 영구 삭제 (TRP-05-04)
│   │   │   ├── repository/
│   │   │   │   ├── TripRepository.java               # TRIP (여행) 넣기·꺼내기
│   │   │   │   ├── TripDayRepository.java            # TRIP_DAY (일차) 넣기·꺼내기
│   │   │   │   ├── CityRepository.java               # CITY (도시) 넣기·꺼내기
│   │   │   │   └── PurgeLogRepository.java           # PURGE_LOG (영구 삭제 기록) 넣기·꺼내기
│   │   │   ├── dto/
│   │   │   │   ├── TripCreateRequest.java            # 여행 생성 입력값
│   │   │   │   ├── TripUpdateRequest.java            # 여행 정보 수정 입력값
│   │   │   │   ├── TripPeriodChangeRequest.java      # 기간 변경 입력값
│   │   │   │   ├── TripSearchRequest.java            # 내 여행 검색 조건
│   │   │   │   ├── TripListResponse.java             # 내 여행 목록 한 줄
│   │   │   │   ├── TripDetailResponse.java           # 여행 상세 정보
│   │   │   │   ├── CityResponse.java                 # 도시 검색 결과
│   │   │   │   ├── MainResponse.java                 # 메인 화면 데이터
│   │   │   │   └── TrashListResponse.java            # 휴지통 목록 한 줄
│   │   │   └── entity/
│   │   │       ├── Trip.java                         # TRIP 테이블과 연결된 클래스
│   │   │       ├── TripDay.java                      # TRIP_DAY 테이블과 연결된 클래스
│   │   │       ├── City.java                         # CITY 테이블과 연결된 클래스
│   │   │       └── PurgeLog.java                     # PURGE_LOG 테이블과 연결된 클래스
│   │   ├── schedule/                                 # [B] 일정·공동 후보·메모 (SCH-01~06, PLC-05)
│   │   │   ├── controller/
│   │   │   │   ├── ScheduleController.java           # 시간표 조회·방문 추가·삭제·순서·수정·저장 (SCH-01~04·06)
│   │   │   │   ├── CandidateController.java          # 공동 후보 추가·삭제·목록·필수 방문 (PLC-05)
│   │   │   │   └── MemoController.java               # 메모 작성·수정·삭제·열람 (SCH-05)
│   │   │   ├── service/
│   │   │   │   ├── ScheduleService.java              # 시간표 조회·방문 일정 추가·삭제·수정·고정 (SCH-01~04)
│   │   │   │   ├── ScheduleSaveService.java          # 일정 저장 공통 처리 (SCH-06-01, AI 추천 적용도 이걸 호출)
│   │   │   │   ├── CandidateService.java             # 공동 후보·필수 방문·배치 상태 (PLC-05)
│   │   │   │   └── MemoService.java                  # 메모 (SCH-05)
│   │   │   ├── repository/
│   │   │   │   ├── VisitRepository.java              # VISIT (방문 항목) 넣기·꺼내기
│   │   │   │   ├── VisitMemoRepository.java          # VISIT_MEMO (메모) 넣기·꺼내기
│   │   │   │   └── TripCandidateRepository.java      # TRIP_CANDIDATE (공동 후보) 넣기·꺼내기
│   │   │   ├── dto/
│   │   │   │   ├── TimetableResponse.java            # 날짜별 시간표
│   │   │   │   ├── VisitAddRequest.java              # 방문 일정 추가 입력값
│   │   │   │   ├── VisitUpdateRequest.java           # 날짜·방문시간·체류시간 수정값
│   │   │   │   ├── VisitOrderRequest.java            # 순서 변경 입력값
│   │   │   │   ├── VisitFixRequest.java              # 날짜·시간 고정 설정값
│   │   │   │   ├── ScheduleSaveRequest.java          # 일정 저장 입력값
│   │   │   │   ├── CandidateAddRequest.java          # 공동 후보 추가 입력값
│   │   │   │   ├── CandidateResponse.java            # 공동 후보 한 개·배치 상태
│   │   │   │   ├── MemoRequest.java                  # 메모 입력값
│   │   │   │   └── MemoResponse.java                 # 메모 결과
│   │   │   └── entity/
│   │   │       ├── Visit.java                        # VISIT 테이블과 연결된 클래스
│   │   │       ├── VisitMemo.java                    # VISIT_MEMO 테이블과 연결된 클래스
│   │   │       └── TripCandidate.java                # TRIP_CANDIDATE 테이블과 연결된 클래스
│   │   ├── collab/                                   # [B] 변경 전파·동시 수정 (COL-06)
│   │   │   ├── websocket/
│   │   │   │   └── TripSyncHandler.java              # WebSocket 연결·메시지 주고받기
│   │   │   ├── service/
│   │   │   │   ├── SyncService.java                  # 저장된 내용을 같은 여행 참여자에게 전파 (COL-06-01)
│   │   │   │   └── ConcurrentEditService.java        # 동시 수정 시 나중에 저장한 값으로 덮어쓰기 (COL-06-02)
│   │   │   └── dto/
│   │   │       └── SyncMessage.java                  # 실시간으로 보내는 메시지 모양
│   │   ├── place/                                    # [C] 장소 검색·상세·지도 (PLC-01~03)
│   │   │   ├── controller/
│   │   │   │   └── PlaceController.java              # 장소 검색·상세 요청 받기 (PLC-01·02)
│   │   │   ├── service/
│   │   │   │   ├── PlaceSearchService.java           # 키워드 검색·자동완성·정보 표준화 (PLC-01)
│   │   │   │   └── PlaceDetailService.java           # 장소 상세(저장 없이 조회만)·권장 체류시간 (PLC-02-01)
│   │   │   ├── client/
│   │   │   │   ├── KakaoMapClient.java               # 국내 장소 검색 (카카오)
│   │   │   │   └── GoogleMapClient.java              # 해외 장소 검색 (구글)
│   │   │   ├── repository/
│   │   │   │   ├── RecommendedStayRepository.java    # RECOMMENDED_STAY (권장 체류시간 기준) 넣기·꺼내기
│   │   │   │   └── ApiCallLogRepository.java         # API_CALL_LOG (외부 호출 기록) 넣기·꺼내기
│   │   │   ├── dto/
│   │   │   │   ├── PlaceSearchRequest.java           # 검색어·지역 조건
│   │   │   │   ├── PlaceResponse.java                # 표준화한 장소 정보 (좌표·식별값)
│   │   │   │   └── PlaceDetailResponse.java          # 장소 상세 정보
│   │   │   └── entity/
│   │   │       ├── RecommendedStay.java              # RECOMMENDED_STAY 테이블과 연결된 클래스
│   │   │       └── ApiCallLog.java                   # API_CALL_LOG 테이블과 연결된 클래스
│   │   ├── ai/                                       # [C] AI 추천 (AIT-01~07) - 테이블 없음, 추천안은 화면이 들고 있음
│   │   │   ├── controller/
│   │   │   │   └── AiController.java                 # AI 추천 조건·요청·미리보기·적용·상태 요청 받기
│   │   │   ├── service/
│   │   │   │   ├── AiConditionService.java           # 추천 조건 검증·요청 데이터 구성, 조건은 저장 안 함 (AIT-01)
│   │   │   │   ├── AiRecommendService.java           # 날짜 배분·방문 순서·시간표 생성 (AIT-02)
│   │   │   │   ├── AiItemRetryService.java           # 항목별 다시 추천 (AIT-04)
│   │   │   │   ├── AiApplyService.java               # 추천 적용·재검증 (AIT-06, ScheduleSaveService 호출)
│   │   │   │   └── AiRequestService.java             # 1분 간격·중복 요청 방지(서버 메모리)·실패 처리 (AIT-07)
│   │   │   ├── client/
│   │   │   │   └── AiClient.java                     # AI 서비스 호출
│   │   │   └── dto/
│   │   │       ├── AiConditionRequest.java           # 추천 조건 입력값
│   │   │       ├── AiPlanResponse.java               # 추천안 (결과 상태·일차별 시간표·경고·추천 이유)
│   │   │       ├── AiItemRetryRequest.java           # 화면이 들고 있는 추천안 + 다시 추천할 항목
│   │   │       └── AiApplyRequest.java               # 적용할 추천안 + 생성 기준 상태
│   │   ├── route/                                    # [C] 이동수단·경로·교통비·개인 출발 (RTE-01~04)
│   │   │   ├── controller/
│   │   │   │   ├── RouteController.java              # 이동수단·구간 경로·교통비 요청 받기 (RTE-01~03)
│   │   │   │   └── PersonalTransferController.java   # 개인 출발 요청 받기 (RTE-04)
│   │   │   ├── service/
│   │   │   │   ├── TransportModeService.java         # 여행 기본 이동수단 (RTE-01)
│   │   │   │   ├── RouteService.java                 # 구간 경로·이동시간 조회·보관 (RTE-02)
│   │   │   │   ├── FareService.java                  # 교통비 조회·확인 후 경비 반영 (RTE-03)
│   │   │   │   └── PersonalTransferService.java      # 출발 공항 저장(본인만 열람)·안내 (RTE-04)
│   │   │   ├── client/
│   │   │   │   └── RouteApiClient.java               # 경로·이동시간·요금 외부 API 호출
│   │   │   ├── repository/
│   │   │   │   ├── RouteSegmentRepository.java       # ROUTE_SEGMENT (구간 경로) 넣기·꺼내기
│   │   │   │   ├── FareExpenseLinkRepository.java    # FARE_EXPENSE_LINK (교통비 반영 연결) 넣기·꺼내기
│   │   │   │   └── PersonalTransferRepository.java   # PERSONAL_TRANSFER (개인 출발) 넣기·꺼내기
│   │   │   ├── dto/
│   │   │   │   ├── TransportModeRequest.java         # 이동수단 선택값
│   │   │   │   ├── RouteSegmentResponse.java         # 구간 이동 정보
│   │   │   │   ├── FareResponse.java                 # 구간 교통비
│   │   │   │   ├── FareApplyRequest.java             # 교통비 경비 반영 입력값
│   │   │   │   ├── PersonalTransferRequest.java      # 출발 공항 선택값
│   │   │   │   └── PersonalTransferResponse.java     # 나의 출발 안내
│   │   │   └── entity/
│   │   │       ├── RouteSegment.java                 # ROUTE_SEGMENT 테이블과 연결된 클래스
│   │   │       ├── FareExpenseLink.java              # FARE_EXPENSE_LINK 테이블과 연결된 클래스
│   │   │       └── PersonalTransfer.java             # PERSONAL_TRANSFER 테이블과 연결된 클래스
│   │   ├── community/                                # [D] 공개 커뮤니티 (PUB-01~09)
│   │   │   ├── controller/
│   │   │   │   ├── PublicPostController.java         # 공유·게시·비공개·목록·상세 요청 받기 (PUB-01~04)
│   │   │   │   ├── PostReactionController.java       # 좋아요·북마크 요청 받기 (PUB-05·06)
│   │   │   │   ├── TripCloneController.java          # 내 여행으로 복제 요청 받기 (PUB-07)
│   │   │   │   ├── PostCommentController.java        # 공개 댓글 요청 받기 (PUB-08)
│   │   │   │   └── ReportController.java             # 신고 접수 요청 받기 (PUB-09)
│   │   │   ├── service/
│   │   │   │   ├── PublicPostService.java            # 게시·미리보기·비공개·내 게시글·제목·소개 수정 (PUB-01·02)
│   │   │   │   ├── PublicPostQueryService.java       # 공개 목록·검색·정렬·상세 (PUB-03·04)
│   │   │   │   ├── PostLikeService.java              # 좋아요·취소 (PUB-05)
│   │   │   │   ├── PostBookmarkService.java          # 북마크 저장·해제·목록 (PUB-06)
│   │   │   │   ├── TripCloneService.java             # 공개 일정 복제 (PUB-07)
│   │   │   │   ├── PostCommentService.java           # 댓글 작성·목록·수정·삭제·알림 (PUB-08)
│   │   │   │   └── ReportService.java                # 신고 접수·관리자 검토로 전달 (PUB-09)
│   │   │   ├── repository/
│   │   │   │   ├── PublicPostRepository.java         # PUBLIC_POST (공개 게시물) 넣기·꺼내기
│   │   │   │   ├── PostLikeRepository.java           # POST_LIKE (좋아요) 넣기·꺼내기
│   │   │   │   ├── PostBookmarkRepository.java       # POST_BOOKMARK (북마크) 넣기·꺼내기
│   │   │   │   ├── PostCommentRepository.java        # POST_COMMENT (댓글) 넣기·꺼내기
│   │   │   │   └── ReportRepository.java             # REPORT (신고) 넣기·꺼내기
│   │   │   ├── dto/
│   │   │   │   ├── PostCreateRequest.java            # 공개 정보 입력값 (사진 최대 1장)
│   │   │   │   ├── PostPreviewResponse.java          # 공개 미리보기
│   │   │   │   ├── PostUpdateRequest.java            # 공개용 제목·소개 수정값
│   │   │   │   ├── PostSearchRequest.java            # 도시·일수·제목 검색·정렬 조건
│   │   │   │   ├── PostListResponse.java             # 공개 목록 한 줄
│   │   │   │   ├── PostDetailResponse.java           # 공개 여행 상세
│   │   │   │   ├── MyPostResponse.java               # 내 게시글 한 줄
│   │   │   │   ├── CloneRequest.java                 # 복제 조건 입력값
│   │   │   │   ├── CommentRequest.java               # 댓글 입력값
│   │   │   │   ├── CommentResponse.java              # 댓글 한 개
│   │   │   │   └── ReportRequest.java                # 신고 입력값
│   │   │   └── entity/
│   │   │       ├── PublicPost.java                   # PUBLIC_POST 테이블과 연결된 클래스
│   │   │       ├── PostLike.java                     # POST_LIKE 테이블과 연결된 클래스
│   │   │       ├── PostBookmark.java                 # POST_BOOKMARK 테이블과 연결된 클래스
│   │   │       ├── PostComment.java                  # POST_COMMENT 테이블과 연결된 클래스
│   │   │       └── Report.java                       # REPORT 테이블과 연결된 클래스
│   │   ├── budget/                                   # [D] 예상 경비·환율 (BUD-01~04)
│   │   │   ├── controller/
│   │   │   │   ├── BudgetController.java             # 경비 목록·추가·수정·삭제·합계 요청 받기 (BUD-01~03)
│   │   │   │   └── ExchangeRateController.java       # 환율·원화 환산 요청 받기 (BUD-04)
│   │   │   ├── service/
│   │   │   │   ├── BudgetService.java                # 경비 항목 관리 (BUD-01)
│   │   │   │   ├── BudgetBaseService.java            # 예산 기준 계산·교통비 반영·근거 (BUD-02)
│   │   │   │   ├── BudgetSummaryService.java         # 공통 합계·내 1인 경비·부분 합계 (BUD-03)
│   │   │   │   └── ExchangeRateService.java          # 환율 조회·원화 합산 (BUD-04)
│   │   │   ├── client/
│   │   │   │   └── KoreaEximClient.java              # 한국수출입은행 환율 API 호출
│   │   │   ├── job/
│   │   │   │   └── ExchangeRateScheduler.java        # 매일 오전 11시 이후 환율 1번 조회 (BUD-04-01)
│   │   │   ├── repository/
│   │   │   │   ├── BudgetItemRepository.java         # BUDGET_ITEM (경비) 넣기·꺼내기
│   │   │   │   └── ExchangeRateRepository.java       # EXCHANGE_RATE (환율) 넣기·꺼내기
│   │   │   ├── dto/
│   │   │   │   ├── BudgetItemRequest.java            # 경비 항목 입력값
│   │   │   │   ├── BudgetItemResponse.java           # 경비 항목 한 줄
│   │   │   │   ├── BudgetSummaryResponse.java        # 통화별 합계·전체 합계
│   │   │   │   └── ExchangeRateResponse.java         # 환율 정보
│   │   │   └── entity/
│   │   │       ├── BudgetItem.java                   # BUDGET_ITEM 테이블과 연결된 클래스
│   │   │       └── ExchangeRate.java                 # EXCHANGE_RATE 테이블과 연결된 클래스
│   │   ├── savedplace/                               # [E] 장소 보관함 (PLC-04)
│   │   │   ├── controller/
│   │   │   │   └── SavedPlaceController.java         # 보관함 저장·삭제·목록 요청 받기
│   │   │   ├── service/
│   │   │   │   └── SavedPlaceService.java            # 즐겨찾기 저장·삭제·목록·검색 (PLC-04-01·02)
│   │   │   ├── repository/
│   │   │   │   └── SavedPlaceRepository.java         # SAVED_PLACE (찜) 넣기·꺼내기
│   │   │   ├── dto/
│   │   │   │   ├── SavedPlaceRequest.java            # 보관할 장소 입력값
│   │   │   │   └── SavedPlaceResponse.java           # 보관함 장소 한 개
│   │   │   └── entity/
│   │   │       └── SavedPlace.java                   # SAVED_PLACE 테이블과 연결된 클래스
│   │   ├── weather/                                  # [E] 날씨 (SUP-01) - 테이블 없음
│   │   │   ├── controller/
│   │   │   │   └── WeatherController.java            # 여행지 날씨 요청 받기
│   │   │   ├── service/
│   │   │   │   └── WeatherService.java               # 일자별 날씨·서버 1시간 캐시 (SUP-01-01)
│   │   │   ├── client/
│   │   │   │   └── OpenMeteoClient.java              # Open-Meteo 날씨 API 호출 (16일)
│   │   │   └── dto/
│   │   │       └── WeatherResponse.java              # 일자별 날씨·출처
│   │   ├── checklist/                                # [E] 체크리스트 (SUP-02)
│   │   │   ├── controller/
│   │   │   │   └── ChecklistController.java          # 체크리스트 요청 받기
│   │   │   ├── service/
│   │   │   │   └── ChecklistService.java             # 목록·추가·수정·삭제·완료 체크·개인 항목 (SUP-02)
│   │   │   ├── repository/
│   │   │   │   ├── ChecklistItemRepository.java      # CHECKLIST_ITEM (체크리스트) 넣기·꺼내기
│   │   │   │   └── ChecklistCheckRepository.java     # CHECKLIST_CHECK (체크리스트 체크) 넣기·꺼내기
│   │   │   ├── dto/
│   │   │   │   ├── ChecklistItemRequest.java         # 항목 입력값
│   │   │   │   └── ChecklistResponse.java            # 공동·개인 체크리스트 목록
│   │   │   └── entity/
│   │   │       ├── ChecklistItem.java                # CHECKLIST_ITEM 테이블과 연결된 클래스
│   │   │       └── ChecklistCheck.java               # CHECKLIST_CHECK 테이블과 연결된 클래스
│   │   └── cs/                                       # [E] FAQ·1:1 문의 (ADM-03~06)
│   │       ├── controller/
│   │       │   ├── FaqController.java                # FAQ 목록·내용 (ADM-03)
│   │       │   ├── AdminFaqController.java           # FAQ 등록·수정·공개·비공개 (ADM-04)
│   │       │   ├── InquiryController.java            # 1:1 문의 등록·내 내역 (ADM-05)
│   │       │   └── AdminInquiryController.java       # 문의 관리·답변 (ADM-06)
│   │       ├── service/
│   │       │   ├── FaqService.java                   # FAQ 조회·관리
│   │       │   └── InquiryService.java               # 문의 등록·조회·답변
│   │       ├── repository/
│   │       │   ├── FaqRepository.java                # FAQ (FAQ) 넣기·꺼내기
│   │       │   └── InquiryRepository.java            # INQUIRY (1:1 문의 (답변 포함)) 넣기·꺼내기
│   │       ├── dto/
│   │       │   ├── FaqRequest.java                   # FAQ 입력값
│   │       │   ├── FaqResponse.java                  # FAQ 한 개
│   │       │   ├── InquiryRequest.java               # 문의 입력값
│   │       │   ├── InquiryResponse.java              # 문의·답변 내용
│   │       │   └── InquiryAnswerRequest.java         # 답변 입력값 (INQUIRY에 함께 저장)
│   │       └── entity/
│   │           ├── Faq.java                          # FAQ 테이블과 연결된 클래스
│   │           └── Inquiry.java                      # INQUIRY 테이블과 연결된 클래스
│   ├── src/main/resources/                           # 설정·SQL
│   │   ├── application.yml                           # 공통 설정 (환경 변수 ${...}로 읽기)
│   │   ├── application-local.yml                     # 내 컴퓨터용 설정 (git에 안 올림)
│   │   └── db/
│   │       └── schema.sql                            # 테이블 생성 SQL (DB설계_v7_oracle.sql 내용)
│   └── src/test/java/com/planmate/                   # 테스트 (기능 폴더 이름 동일)
│       ├── trip/
│       │   └── service/
│       │       └── TripServiceTest.java              # 예시: TripService 테스트
│       └── ...                                       # 다른 기능도 같은 모양
├── contracts/                                        # FE·BE API 약속 문서 (기능 폴더 이름 동일)
│   ├── common.yaml                                   # 공통 응답·에러 형식
│   ├── auth.yaml                                     # [A] 가입·로그인·비밀번호 API
│   ├── member.yaml                                   # [A] 내 정보·탈퇴 API
│   ├── participant.yaml                              # [A] 초대·참여자·소유권 API
│   ├── notification.yaml                             # [A] 알림함 API
│   ├── admin.yaml                                    # [A] 회원·신고 운영 API
│   ├── trip.yaml                                     # [B] 여행 생성·목록·수정·삭제 API
│   ├── schedule.yaml                                 # [B] 일정·공동 후보·메모 API
│   ├── collab.yaml                                   # [B] 변경 전파·동시 수정 API
│   ├── place.yaml                                    # [C] 장소 검색·상세·지도 API
│   ├── ai.yaml                                       # [C] AI 추천 API
│   ├── route.yaml                                    # [C] 이동수단·경로·교통비·개인 출발 API
│   ├── community.yaml                                # [D] 공개 커뮤니티 API
│   ├── budget.yaml                                   # [D] 예상 경비·환율 API
│   ├── savedplace.yaml                               # [E] 장소 보관함 API
│   ├── weather.yaml                                  # [E] 날씨 API
│   ├── checklist.yaml                                # [E] 체크리스트 API
│   └── cs.yaml                                       # [E] FAQ·1:1 문의 API
├── frontend/                                         # 화면 (React + TypeScript)
│   ├── package.json                                  # 사용할 라이브러리 목록
│   ├── index.html                                    # 첫 화면 HTML
│   └── src/
│       ├── app/                                      # 앱 전체 설정
│       │   ├── main.tsx                              # 앱 시작점
│       │   ├── App.tsx                               # 최상위 화면
│       │   ├── router.tsx                            # 화면 주소 목록 (/trips, /login ...)
│       │   └── layouts/
│       │       ├── MainLayout.tsx                    # 상단 메뉴·알림 아이콘 등 공통 틀
│       │       └── AdminLayout.tsx                   # 관리자 화면 공통 틀
│       ├── features/                                 # 기능별 화면 (백엔드와 같은 이름)
│       │   ├── auth/                                 # [A]
│       │   │   ├── pages/
│       │   │   │   ├── SignupPage.tsx                # 이메일 가입 화면
│       │   │   │   ├── LoginPage.tsx                 # 로그인 화면
│       │   │   │   ├── SocialSignupPage.tsx          # 소셜 최초 가입 화면
│       │   │   │   ├── OAuthCallbackPage.tsx         # 소셜 로그인 후 돌아오는 화면
│       │   │   │   ├── PasswordFindPage.tsx          # 비밀번호 찾기 (재설정 인증)
│       │   │   │   ├── PasswordResetPage.tsx         # 새 비밀번호 입력
│       │   │   │   └── PasswordChangePage.tsx        # 로그인 상태에서 비밀번호 변경
│       │   │   ├── components/
│       │   │   │   ├── EmailInput.tsx                # 아이디 @ 도메인 두 칸 + 도메인 선택
│       │   │   │   ├── PasswordInput.tsx             # 가림 입력 + 눈 아이콘
│       │   │   │   ├── VerificationCodeInput.tsx     # 인증번호 6자리 + 05:00 타이머
│       │   │   │   └── SocialLoginButtons.tsx        # 카카오·네이버·구글·Apple 버튼
│       │   │   ├── api/
│       │   │   │   └── authApi.ts                    # 가입·로그인·비밀번호 서버 호출
│       │   │   └── hooks/
│       │   │       ├── useAuth.ts                    # 로그인 상태·내 정보 가져오기
│       │   │       └── useCountdown.ts               # 인증번호 남은 시간 타이머
│       │   ├── member/                               # [A]
│       │   │   ├── pages/
│       │   │   │   ├── MyPage.tsx                    # 내 정보 조회·수정
│       │   │   │   └── WithdrawalPage.tsx            # 탈퇴 영향 확인·탈퇴
│       │   │   ├── components/
│       │   │   │   ├── ProfileForm.tsx               # 닉네임·프로필 사진 수정
│       │   │   │   └── HomeAddressForm.tsx           # 집 주소 입력
│       │   │   └── api/
│       │   │       └── memberApi.ts                  # 내 정보·탈퇴 서버 호출
│       │   ├── participant/                          # [A]
│       │   │   ├── pages/
│       │   │   │   └── InviteJoinPage.tsx            # 초대 링크 접속·참여 확인 화면
│       │   │   ├── components/
│       │   │   │   ├── InviteLinkBox.tsx             # 초대 링크 발급·복사·폐기
│       │   │   │   ├── ParticipantList.tsx           # 참여자 목록·역할 변경·내보내기
│       │   │   │   └── OwnershipTransferModal.tsx    # 소유권 이전 요청 창
│       │   │   └── api/
│       │   │       └── participantApi.ts             # 초대·참여자·소유권 서버 호출
│       │   ├── notification/                         # [A]
│       │   │   ├── pages/
│       │   │   │   └── NotificationPage.tsx          # 알림함
│       │   │   ├── components/
│       │   │   │   ├── NotificationBell.tsx          # 상단 알림 아이콘·안 읽은 개수 배지
│       │   │   │   └── NotificationItem.tsx          # 알림 한 개 (누르면 대상으로 이동)
│       │   │   ├── api/
│       │   │   │   └── notificationApi.ts            # 알림 서버 호출
│       │   │   └── hooks/
│       │   │       └── useUnreadCount.ts             # 안 읽은 알림 개수 가져오기
│       │   ├── admin/                                # [A]
│       │   │   ├── pages/
│       │   │   │   ├── AdminMemberPage.tsx           # 회원 목록·제한 상태
│       │   │   │   ├── AdminMemberDetailPage.tsx     # 회원 상세·조치 이력
│       │   │   │   ├── AdminReportPage.tsx           # 신고 목록
│       │   │   │   └── AdminReportDetailPage.tsx     # 신고 상세·처리
│       │   │   ├── components/
│       │   │   │   ├── RestrictionModal.tsx          # 이용 제한 설정 창
│       │   │   │   └── ReauthModal.tsx               # 조치 전 본인 재확인 창
│       │   │   └── api/
│       │   │       └── adminApi.ts                   # 회원·신고 운영 서버 호출
│       │   ├── trip/                                 # [B]
│       │   │   ├── pages/
│       │   │   │   ├── MainPage.tsx                  # 메인 화면 (다가오는 여행·인기 공개 여행)
│       │   │   │   ├── TripListPage.tsx              # 내 여행 목록·검색
│       │   │   │   ├── TripCreatePage.tsx            # 여행 만들기
│       │   │   │   └── TrashPage.tsx                 # 휴지통
│       │   │   ├── components/
│       │   │   │   ├── TripCard.tsx                  # 여행 카드 한 개
│       │   │   │   ├── TripForm.tsx                  # 제목·인원·기간 입력 (생성·수정 같이 사용)
│       │   │   │   ├── CitySearch.tsx                # 도시 검색·선택
│       │   │   │   ├── TripEditModal.tsx             # 여행 정보 수정 창
│       │   │   │   └── PeriodChangeModal.tsx         # 기간 변경 확인 창
│       │   │   ├── api/
│       │   │   │   └── tripApi.ts                    # 여행 서버 호출
│       │   │   └── hooks/
│       │   │       └── useTripList.ts                # 내 여행 목록 가져오기
│       │   ├── schedule/                             # [B]
│       │   │   ├── pages/
│       │   │   │   └── TripDetailPage.tsx            # 여행 상세 (지도 + 시간표 편집 화면)
│       │   │   ├── components/
│       │   │   │   ├── DayTabs.tsx                   # 일차 선택 탭
│       │   │   │   ├── Timetable.tsx                 # 날짜별 시간표
│       │   │   │   ├── VisitItem.tsx                 # 방문 일정 한 개 (드래그로 순서 변경)
│       │   │   │   ├── VisitEditModal.tsx            # 날짜·방문시간·체류시간·고정 수정 창
│       │   │   │   ├── CandidatePanel.tsx            # 공동 후보 목록·배치 상태
│       │   │   │   ├── CandidateItem.tsx             # 공동 후보 한 개·필수 방문 표시
│       │   │   │   └── MemoPanel.tsx                 # 여행 내부 메모
│       │   │   ├── api/
│       │   │   │   └── scheduleApi.ts                # 일정·후보·메모 서버 호출
│       │   │   └── hooks/
│       │   │       └── useSchedule.ts                # 시간표 가져오기·저장
│       │   ├── collab/                               # [B]
│       │   │   ├── components/
│       │   │   │   └── SyncStatus.tsx                # 실시간 연결 상태 표시
│       │   │   └── hooks/
│       │   │       └── useTripSync.ts                # WebSocket 연결·다른 사람 변경 받아서 화면에 반영
│       │   ├── place/                                # [C]
│       │   │   ├── components/
│       │   │   │   ├── MapView.tsx                   # 공통 지도 (국내 카카오·해외 구글 자동 선택, PLC-03-01)
│       │   │   │   ├── KakaoMap.tsx                  # 카카오 지도
│       │   │   │   ├── GoogleMap.tsx                 # 구글 지도
│       │   │   │   ├── MapPins.tsx                   # 방문 순서 핀·목록 선택 연동 (PLC-03-02)
│       │   │   │   ├── RouteLine.tsx                 # 경로선·지도 범위 맞춤 (PLC-03-03)
│       │   │   │   ├── PlaceSearch.tsx               # 장소 검색창·자동완성
│       │   │   │   └── PlaceDetailPanel.tsx          # 장소 상세·권장 체류시간
│       │   │   ├── api/
│       │   │   │   └── placeApi.ts                   # 장소 서버 호출
│       │   │   └── hooks/
│       │   │       └── useMapScript.ts               # 지도 스크립트 불러오기
│       │   ├── ai/                                   # [C]
│       │   │   ├── components/
│       │   │   │   ├── AiConditionForm.tsx           # AI 추천 조건 입력
│       │   │   │   ├── AiPreview.tsx                 # 추천안 미리보기·이동시간 비교·항목별 다시 추천
│       │   │   │   └── AiStatus.tsx                  # 생성 중·실패·재시도 표시
│       │   │   ├── api/
│       │   │   │   └── aiApi.ts                      # AI 추천 서버 호출
│       │   │   └── hooks/
│       │   │       └── useAiRequest.ts               # 추천 요청·상태 확인
│       │   ├── route/                                # [C]
│       │   │   ├── components/
│       │   │   │   ├── TransportModeSelect.tsx       # 기본 이동수단 선택
│       │   │   │   ├── SegmentInfo.tsx               # 구간 이동시간·거리 표시
│       │   │   │   ├── FareInfo.tsx                  # 구간 교통비·경비 반영 버튼
│       │   │   │   ├── PersonalTransferForm.tsx      # 나의 출발 안내 (출발 공항 설정·카카오맵 길찾기)
│       │   │   │   └── ExternalRouteButton.tsx       # 외부 지도 길찾기 열기 (RTE-05)
│       │   │   └── api/
│       │   │       └── routeApi.ts                   # 경로·교통비 서버 호출
│       │   ├── community/                            # [D]
│       │   │   ├── pages/
│       │   │   │   ├── CommunityListPage.tsx         # 공개 여행 목록·검색·정렬
│       │   │   │   ├── PostDetailPage.tsx            # 공개 여행 상세·지도
│       │   │   │   ├── PostCreatePage.tsx            # 공유하기·공개 정보·사진 1장·미리보기
│       │   │   │   ├── MyPostsPage.tsx               # 내 게시글
│       │   │   │   └── MyBookmarksPage.tsx           # 내 북마크
│       │   │   ├── components/
│       │   │   │   ├── PostCard.tsx                  # 공개 여행 카드 한 개
│       │   │   │   ├── PostSearchBar.tsx             # 도시·일수·제목 검색
│       │   │   │   ├── LikeButton.tsx                # 좋아요
│       │   │   │   ├── BookmarkButton.tsx            # 북마크
│       │   │   │   ├── CommentList.tsx               # 댓글 목록·수정·삭제
│       │   │   │   ├── CommentForm.tsx               # 댓글 입력
│       │   │   │   ├── CloneModal.tsx                # 내 여행으로 복제 창
│       │   │   │   └── ReportModal.tsx               # 신고 창
│       │   │   └── api/
│       │   │       └── communityApi.ts               # 커뮤니티 서버 호출
│       │   ├── budget/                               # [D]
│       │   │   ├── pages/
│       │   │   │   └── BudgetPage.tsx                # 예상 경비 화면 (/trips/:id/budget)
│       │   │   ├── components/
│       │   │   │   ├── BudgetList.tsx                # 경비 목록
│       │   │   │   ├── BudgetItemForm.tsx            # 경비 항목 추가·수정
│       │   │   │   ├── BudgetSummary.tsx             # 통화별 합계·전체 합계·내 1인 경비
│       │   │   │   └── ExchangeRateWidget.tsx        # 환율 위젯
│       │   │   └── api/
│       │   │       └── budgetApi.ts                  # 경비·환율 서버 호출
│       │   ├── savedplace/                           # [E]
│       │   │   ├── pages/
│       │   │   │   └── SavedPlacePage.tsx            # 내 보관함 목록·검색
│       │   │   ├── components/
│       │   │   │   ├── SaveButton.tsx                # 장소 즐겨찾기 버튼
│       │   │   │   └── SavedPlaceItem.tsx            # 보관 장소 한 개
│       │   │   └── api/
│       │   │       └── savedPlaceApi.ts              # 보관함 서버 호출
│       │   ├── weather/                              # [E]
│       │   │   ├── components/
│       │   │   │   └── WeatherWidget.tsx             # 일자별 날씨·출처 표시
│       │   │   └── api/
│       │   │       └── weatherApi.ts                 # 날씨 서버 호출
│       │   ├── checklist/                            # [E]
│       │   │   ├── components/
│       │   │   │   ├── ChecklistPanel.tsx            # 공동·개인 체크리스트
│       │   │   │   ├── ChecklistItem.tsx             # 항목 한 개·완료 체크
│       │   │   │   └── ChecklistForm.tsx             # 항목 추가·수정
│       │   │   └── api/
│       │   │       └── checklistApi.ts               # 체크리스트 서버 호출
│       │   ├── export/                               # [E] 서버 없이 화면에서 처리
│       │   │   ├── components/
│       │   │   │   └── ExportPngButton.tsx           # 날짜별 일정표 PNG 저장 버튼 (SUP-04)
│       │   │   └── utils/
│       │   │       └── exportPng.ts                  # 화면을 PNG 파일로 바꾸기 (html2canvas)
│       │   └── cs/                                   # [E]
│       │       ├── pages/
│       │       │   ├── FaqPage.tsx                   # FAQ 목록·내용
│       │       │   ├── InquiryListPage.tsx           # 내 문의 내역
│       │       │   ├── InquiryWritePage.tsx          # 1:1 문의 작성·수정
│       │       │   ├── InquiryDetailPage.tsx         # 문의·답변 보기
│       │       │   ├── AdminFaqPage.tsx              # FAQ 등록·수정·공개·비공개
│       │       │   └── AdminInquiryPage.tsx          # 문의 관리·답변
│       │       ├── components/
│       │       │   ├── FaqItem.tsx                   # FAQ 한 개 (펼쳐서 보기)
│       │       │   ├── InquiryForm.tsx               # 문의 입력
│       │       │   └── AnswerForm.tsx                # 답변 입력
│       │       └── api/
│       │           └── csApi.ts                      # FAQ·문의 서버 호출
│       └── shared/                                   # 여러 기능이 같이 쓰는 것 (관리 담당 1명)
│           ├── api/
│           │   ├── client.ts                         # 서버 호출 기본 설정 (서버 주소·로그인 쿠키·에러 처리)
│           │   └── websocket.ts                      # WebSocket 연결 기본 설정
│           ├── components/
│           │   ├── Button.tsx                        # 버튼
│           │   ├── Input.tsx                         # 입력칸
│           │   ├── Modal.tsx                         # 팝업 창
│           │   ├── ConfirmDialog.tsx                 # 확인·취소 창
│           │   ├── Toast.tsx                         # 잠깐 뜨는 안내 문구
│           │   ├── Loading.tsx                       # 로딩 표시
│           │   └── EmptyState.tsx                    # 내용이 없을 때 표시
│           ├── hooks/
│           │   └── useDebounce.ts                    # 입력이 멈춘 뒤 검색하기 (자동완성용)
│           ├── utils/
│           │   ├── date.ts                           # 날짜·시간 표시 형식
│           │   ├── money.ts                          # 금액·통화 표시 형식
│           │   └── validation.ts                     # 공통 입력 검사
│           └── styles/
│               ├── global.css                        # 전체 기본 스타일
│               └── variables.css                     # 색·글자 크기 같은 공통 값
├── .env.example                                      # 환경 변수 이름 목록 (값은 비워서 올림)
├── .gitignore                                        # git에 올리지 않을 파일 목록
├── AGENTS.md                                         # AI 코딩 도구용 공통 규칙
└── README.md                                         # 프로젝트 소개·실행 방법
```

## 기능 폴더 안의 하위 폴더

| 폴더 | 하는 일 |
|---|---|
| controller/ | 화면의 요청을 받아서 service에 넘김 |
| websocket/ | 실시간 연결 요청을 받음 (collab에만 있음) |
| service/ | 규칙 검사·실제 처리 (기능정의서의 검증 규칙·처리 흐름) |
| client/ | 외부 API 호출 (지도·AI·환율·날씨·메일·소셜 로그인) |
| job/ | 정해진 시간에 자동으로 도는 작업 (영구 삭제·환율 조회 등) |
| repository/ | DB에 넣고 꺼내기 (JPA가 기본 SQL을 대신 만들어줌) |
| dto/ | 화면과 주고받는 데이터 묶음 (Request = 받는 값, Response = 보내는 값) |
| entity/ | DB 테이블과 연결된 클래스 (@Entity) |

프론트 기능 폴더의 pages/는 주소가 있는 화면 하나, components/는 화면 안에 들어가는 부품, api/는 서버 호출 함수, hooks/는 화면에서 반복해 쓰는 로직이에요.

## 두 파트가 같이 쓰는 것

| 테이블·파일 | 둔 곳 | 다른 파트는 |
|---|---|---|
| TRIP_MEMBER (A·B) | participant/ | B는 ParticipantService를 불러서 사용 |
| CITY (B·C) | trip/ | 도시 검색(TRP-01-02)이 B 기능이라 trip에 둠. C는 CityService를 불러서 사용 |
| REPORT (A·D) | community/ | D가 접수, A(admin)는 ReportService를 불러서 검토 |
| FARE_EXPENSE_LINK (C·D) | route/ | D(budget)는 FareService를 불러서 사용 |
| REQUEST_LOG (B) | common/duplicate/ | 여행 생성·일정 저장·게시·복제·AI 적용이 같이 써서 공통에 둠 |
| MEMBER (A) | member/ | auth도 MemberRepository를 같이 씀 (같은 A 파트) |
| ScheduleSaveService (B) | schedule/ | C(ai)가 추천 적용할 때 호출 |
| NotificationService (A) | notification/ | 모든 파트가 알림 저장할 때 호출 |
| MapView.tsx (C) | frontend features/place/ | B·D 화면에서 가져다 씀 |

## 팀에서 확인할 것

- 프론트 pages/와 components/ 구분은 와이어프레임에서 주소가 따로 있는 화면인지에 따라 바꿈 (주소가 바뀌면 pages, 안 바뀌면 components)
- contracts/ 작성 형식(OpenAPI 등)은 구조를 만든 팀원과 맞춤
- 패키지 이름 com.planmate, 폴더 이름(auth, collab, cs 등)은 팀에서 바꿔도 됨
- 새 폴더에는 .gitkeep을 넣고, 실제 파일이 생기면 지움
