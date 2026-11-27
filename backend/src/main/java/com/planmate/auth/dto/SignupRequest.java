package com.planmate.auth.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.Locale;
import java.util.Set;

/**
 * 가입 입력값 (ACC-01-04). 화면 검사와 같은 규칙을 서버에서 다시 검사한다(ACC-01-01 J).
 * 정규식·문구 상수는 다른 DTO(VerificationSendRequest)와 ACC-04·05에서도 다시 쓴다.
 * TODO(ACC-05-02): 집 주소(선택) 받기 — ACC-01-04 입력값, 11/23 결정
 */
public record SignupRequest(
        @NotBlank(message = EMAIL_MESSAGE)
        @Size(max = EMAIL_MAX_LENGTH, message = EMAIL_MESSAGE)
        @Pattern(regexp = EMAIL_PATTERN, message = EMAIL_MESSAGE)
        String email,

        @NotBlank(message = INVALID_INPUT_MESSAGE)
        @Size(max = 100, message = INVALID_INPUT_MESSAGE)
        String confirmToken,

        // trim 금지. 초과값은 자르지 않고 오류 (A-P01)
        @NotNull(message = PASSWORD_MESSAGE)
        @Pattern(regexp = PASSWORD_PATTERN, message = PASSWORD_MESSAGE)
        String password,

        String passwordConfirm,

        @NotNull(message = NICKNAME_MESSAGE)
        @Pattern(regexp = NICKNAME_PATTERN, message = NICKNAME_MESSAGE)
        String nickname,

        @NotNull(message = INVALID_INPUT_MESSAGE)
        @AssertTrue(message = INVALID_INPUT_MESSAGE)
        Boolean requiredAgreed
) {

    /** 이메일 최대 길이 (A-P03) */
    public static final int EMAIL_MAX_LENGTH = 254;
    /** ASCII 이메일: 아이디@도메인(점으로 나뉜 2단 이상). '+' 별칭·마침표는 그대로 둔다 (A-P03) */
    public static final String EMAIL_PATTERN =
            "^[A-Za-z0-9._%+\\-]+@[A-Za-z0-9](?:[A-Za-z0-9\\-]*[A-Za-z0-9])?(?:\\.[A-Za-z0-9](?:[A-Za-z0-9\\-]*[A-Za-z0-9])?)+$";
    /**
     * 비밀번호 (A-F01·A-P01): 8~64자, ASCII 가시문자(0x21~0x7E, 공백 없음)만, 영문·숫자·특수문자 모두 포함.
     * 대소문자는 같은 종류(영문)로 본다.
     */
    public static final String PASSWORD_PATTERN =
            "^(?=.*[A-Za-z])(?=.*[0-9])(?=.*[!-/:-@\\[-`{-~])[!-~]{8,64}$";
    /** 닉네임 (A-F02): 2~12자, 한글 완성형·영문·숫자·밑줄. 공백·이모지 불가 */
    public static final String NICKNAME_PATTERN = "^[가-힣A-Za-z0-9_]{2,12}$";
    /** 닉네임 예약어 (A-F02). 영문 대소문자 무시 */
    public static final Set<String> RESERVED_NICKNAMES = Set.of("관리자", "운영자", "admin", "administrator", "운영팀");

    public static final String EMAIL_MESSAGE = "이메일 주소를 확인해 주세요.";
    public static final String PASSWORD_MESSAGE = "비밀번호는 영문·숫자·특수문자를 모두 포함해 8~64자로 입력해 주세요.";
    public static final String PASSWORD_CONFIRM_MESSAGE = "비밀번호가 일치하지 않아요.";
    public static final String NICKNAME_MESSAGE = "닉네임은 한글·영문·숫자·밑줄로 2~12자 입력해 주세요.";
    public static final String NICKNAME_RESERVED_MESSAGE = "사용할 수 없는 닉네임이에요.";
    public static final String INVALID_INPUT_MESSAGE = "입력값을 확인해 주세요.";

    public SignupRequest {
        email = normalizeEmail(email);
    }

    /** 이메일 정규화 (A-P03): 앞뒤 공백 제거 + 소문자. '+' 별칭·마침표는 지우지 않는다 */
    public static String normalizeEmail(String email) {
        return email == null ? null : email.strip().toLowerCase(Locale.ROOT);
    }

    /** 비밀번호 확인값과 완전 일치 (A-P01). 필드 오류 이름: passwordConfirmed */
    @AssertTrue(message = PASSWORD_CONFIRM_MESSAGE)
    public boolean isPasswordConfirmed() {
        return password != null && password.equals(passwordConfirm);
    }

    /** 닉네임 예약어 차단 (A-F02). null은 다른 규칙이 잡는다. 필드 오류 이름: nicknameAllowed */
    @AssertTrue(message = NICKNAME_RESERVED_MESSAGE)
    public boolean isNicknameAllowed() {
        return nickname == null || !RESERVED_NICKNAMES.contains(nickname.toLowerCase(Locale.ROOT));
    }

    // 비밀번호·확인값을 로그에 남기지 않는다
    @Override
    public String toString() {
        return "SignupRequest[email=" + email + ", nickname=" + nickname + ", requiredAgreed=" + requiredAgreed
                + ", confirmToken=***, password=***, passwordConfirm=***]";
    }
}
