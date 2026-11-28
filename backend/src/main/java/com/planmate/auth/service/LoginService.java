package com.planmate.auth.service;

import com.planmate.auth.dto.LoginRequest;
import com.planmate.auth.dto.LoginResponse;
import com.planmate.auth.dto.SignupRequest;
import com.planmate.auth.entity.LoginAttempt;
import com.planmate.auth.entity.LoginAttempt.Result;
import com.planmate.auth.repository.LoginAttemptRepository;
import com.planmate.common.exception.BusinessException;
import com.planmate.common.exception.ErrorCode;
import com.planmate.common.security.AccountStatusChecker;
import com.planmate.common.security.LoginMember;
import com.planmate.common.security.SessionPolicyFilter;
import com.planmate.common.util.DateTimeUtil;
import com.planmate.member.entity.Member;
import com.planmate.member.repository.MemberRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.data.domain.Limit;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextHolderStrategy;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;
import java.util.regex.Pattern;

/**
 * 이메일 로그인·로그아웃·로그인 상태 (ACC-02-01·02).
 * 컨트롤러가 authenticate → establishSession을 차례로 부른다
 * (한 메서드로 묶으면 같은 클래스 안 호출이라 @Transactional이 무시됨).
 * 비밀번호·이메일·IP·세션·토큰 값은 로그에 남기지 않는다.
 */
@Service
public class LoginService {

    /** 계정 제한: 15분 안 5회 실패면 마지막 실패부터 15분 제한 (A-P06) */
    static final int ACCOUNT_MAX_FAILS = 5;
    static final Duration ACCOUNT_FAIL_WINDOW = Duration.ofMinutes(15);
    static final Duration ACCOUNT_LOCK = Duration.ofMinutes(15);
    /** 계정 제한 판정에 볼 실패 기록 범위 (최근 30분, 마지막 성공 이후) */
    static final Duration ACCOUNT_LOOKBACK = Duration.ofMinutes(30);
    /** IP 제한: 15분 안 50회 (A-P06 초기안) */
    static final int IP_MAX_ATTEMPTS = 50;
    static final Duration IP_WINDOW = Duration.ofMinutes(15);

    private static final int EMAIL_MAX_LENGTH = SignupRequest.EMAIL_MAX_LENGTH;
    private static final Pattern EMAIL_FORMAT = Pattern.compile(SignupRequest.EMAIL_PATTERN);
    // 64자 이하 ASCII 가시문자 (A-P01). 길이 하한·문자 종류는 비밀번호 비교에 맡긴다
    private static final Pattern PASSWORD_FORMAT = Pattern.compile("^[!-~]{1,64}$");

    private final MemberRepository memberRepository;
    private final LoginAttemptRepository loginAttemptRepository;
    private final AuthHasher authHasher;
    private final PasswordEncoder passwordEncoder;
    private final AccountStatusChecker accountStatusChecker;
    private final CsrfTokenRepository csrfTokenRepository;
    private final SecurityContextRepository securityContextRepository = new HttpSessionSecurityContextRepository();
    // 없는 회원에도 비밀번호 비교를 돌려 응답 시간을 맞추는 가짜 해시
    private final String dummyPasswordHash;

    public LoginService(MemberRepository memberRepository, LoginAttemptRepository loginAttemptRepository,
                        AuthHasher authHasher, PasswordEncoder passwordEncoder,
                        AccountStatusChecker accountStatusChecker, CsrfTokenRepository csrfTokenRepository) {
        this.memberRepository = memberRepository;
        this.loginAttemptRepository = loginAttemptRepository;
        this.authHasher = authHasher;
        this.passwordEncoder = passwordEncoder;
        this.accountStatusChecker = accountStatusChecker;
        this.csrfTokenRepository = csrfTokenRepository;
        byte[] random = new byte[32];
        new SecureRandom().nextBytes(random);
        this.dummyPasswordHash = passwordEncoder.encode(Base64.getEncoder().encodeToString(random));
    }

    /**
     * (1) 인증 (ACC-02-01). 실패 기록이 예외 뒤에도 남도록 BusinessException으로는 롤백하지 않는다.
     * 이용 제한(MEMBER_RESTRICTION) 회원도 로그인된다(커뮤니티 쓰기만 막힘).
     */
    @Transactional(noRollbackFor = BusinessException.class)
    public Member authenticate(LoginRequest request, String clientIp) {
        LocalDateTime now = DateTimeUtil.now();
        String ipHash = authHasher.ipHash(clientIp);
        String email = request.email();
        String password = request.password();

        // IP 제한: 15분 안 50회 이상이면 거부(기록하지 않음)
        if (loginAttemptRepository.countByIpHashAndAttemptedAtAfter(ipHash, now.minus(IP_WINDOW)) >= IP_MAX_ATTEMPTS) {
            throw new BusinessException(ErrorCode.LOGIN_ATTEMPTS_LIMITED);
        }

        // 계정 제한: 254자 이하일 때만 판정. 제한 중이면 비밀번호도 확인하지 않고 기록하지 않음
        boolean emailStorable = email != null && email.length() <= EMAIL_MAX_LENGTH;
        if (emailStorable && isAccountLocked(email, now)) {
            throw new BusinessException(ErrorCode.LOGIN_ATTEMPTS_LIMITED);
        }
        String recordedEmail = emailStorable ? email : null;

        // 형식이 틀리면 비밀번호 비교 없이 같은 실패로 처리
        if (!emailStorable || !EMAIL_FORMAT.matcher(email).matches()
                || password == null || !PASSWORD_FORMAT.matcher(password).matches()) {
            throw fail(recordedEmail, ipHash, now, ErrorCode.LOGIN_FAILED);
        }

        // 이메일 회원(PROVIDER = EMAIL, 비밀번호 해시 있음)만 인정. 소셜 회원은 서비스 비밀번호가 없다
        Member member = memberRepository.findByEmailNormalized(email)
                .filter(found -> found.getProvider() == Member.Provider.EMAIL && found.getPasswordHash() != null)
                .orElse(null);
        boolean matched = passwordEncoder.matches(password, member != null ? member.getPasswordHash() : dummyPasswordHash);
        if (member == null || !matched) {
            throw fail(recordedEmail, ipHash, now, ErrorCode.LOGIN_FAILED);
        }
        // 탈퇴·탈퇴 처리 중 계정 (비밀번호가 맞았을 때만 알려 준다)
        if (!member.isActive()) {
            throw fail(recordedEmail, ipHash, now, ErrorCode.ACCOUNT_UNAVAILABLE);
        }

        loginAttemptRepository.save(LoginAttempt.record(recordedEmail, ipHash, Result.SUCCESS, now));
        return member;
    }

    /**
     * (2) 로그인 상태 만들기 (ACC-02-01). 트랜잭션 없음.
     * 로그인 전 세션 식별값을 교체하고(이전 세션 속성은 넘기지 않음), CSRF 토큰도 새로 만든다.
     */
    public LoginResponse establishSession(Member member, HttpServletRequest request, HttpServletResponse response) {
        HttpSession previous = request.getSession(false);
        if (previous != null) {
            previous.invalidate();
        }
        HttpSession session = request.getSession(true);

        LoginMember loginMember = LoginMember.from(member);
        SecurityContextHolderStrategy strategy = SecurityContextHolder.getContextHolderStrategy();
        SecurityContext context = strategy.createEmptyContext();
        context.setAuthentication(loginMember.toAuthentication());
        strategy.setContext(context);
        securityContextRepository.saveContext(context, request, response);

        // 최대 시간 판정용 로그인 시각 (SessionPolicyFilter가 System.currentTimeMillis()와 비교)
        session.setAttribute(SessionPolicyFilter.LOGIN_AT_ATTRIBUTE, System.currentTimeMillis());
        // 관리자는 처음부터 무활동 15분 (A-P08). 회원은 application.yml의 60분 그대로
        if (loginMember.isAdmin()) {
            session.setMaxInactiveInterval((int) SessionPolicyFilter.ADMIN_IDLE.toSeconds());
        }

        renewCsrfToken(request, response);
        return LoginResponse.from(member);
    }

    /**
     * (3) 현재 로그인 로그아웃 (ACC-02-02). 이 기기의 세션만 끝낸다(다른 기기 세션은 유지).
     * 세션이 없거나 이미 만료돼도 예외 없이 끝낸다(로그아웃 완료로 봄).
     */
    public void logout(HttpServletRequest request, HttpServletResponse response) {
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        SecurityContextHolder.getContextHolderStrategy().clearContext();
        renewCsrfToken(request, response);
    }

    /**
     * (4) 로그인 상태 (GET /api/auth/me). 비로그인·탈퇴 회원·회원 행 없음은 모두 null(예외 없음).
     * 세션을 새로 만들지 않는다.
     */
    @Transactional(readOnly = true)
    public LoginResponse findMe() {
        return accountStatusChecker.findLoginMemberId()
                .flatMap(memberRepository::findById)
                .map(LoginResponse::from)
                .orElse(null);
    }

    // 최근 실패 5개가 15분 안에 몰려 있고, 마지막 실패 + 15분 전이면 제한
    private boolean isAccountLocked(String email, LocalDateTime now) {
        LocalDateTime since = now.minus(ACCOUNT_LOOKBACK);
        LocalDateTime lastSuccess = loginAttemptRepository.findLastAttemptedAt(email, Result.SUCCESS);
        if (lastSuccess != null && lastSuccess.isAfter(since)) {
            since = lastSuccess; // 성공하면 실패 횟수 초기화
        }
        List<LocalDateTime> fails = loginAttemptRepository.findRecentAttemptedAt(email, Result.FAIL, since,
                Limit.of(ACCOUNT_MAX_FAILS));
        if (fails.size() < ACCOUNT_MAX_FAILS) {
            return false;
        }
        LocalDateTime latest = fails.get(0);
        LocalDateTime fifth = fails.get(ACCOUNT_MAX_FAILS - 1);
        boolean clustered = !Duration.between(fifth, latest).minus(ACCOUNT_FAIL_WINDOW).isPositive();
        return clustered && now.isBefore(latest.plus(ACCOUNT_LOCK));
    }

    private BusinessException fail(String email, String ipHash, LocalDateTime now, ErrorCode code) {
        loginAttemptRepository.save(LoginAttempt.record(email, ipHash, Result.FAIL, now));
        return new BusinessException(code);
    }

    // CSRF 토큰 교체: 새 토큰을 만들어 저장하면 이 응답에 새 XSRF-TOKEN Set-Cookie가 1개 실린다
    private void renewCsrfToken(HttpServletRequest request, HttpServletResponse response) {
        CsrfToken token = csrfTokenRepository.generateToken(request);
        csrfTokenRepository.saveToken(token, request, response);
    }
}
