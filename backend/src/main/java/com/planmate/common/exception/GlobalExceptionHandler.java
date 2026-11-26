package com.planmate.common.exception;

import com.planmate.common.response.ApiResponse;
import com.planmate.common.response.ApiResponse.ErrorData;
import com.planmate.common.response.ApiResponse.FieldErrorData;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.ArrayList;
import java.util.List;

/**
 * 모든 에러를 한곳에서 받아 공통 응답 {success:false, data:{code, fieldErrors}, message}로 바꾼다 (AGENTS §6).
 * HTTP 상태는 ErrorCode의 status를 쓴다. 로그에 요청 값(비밀번호·인증번호·토큰 등)을 남기지 않는다.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /** 서비스의 규칙 위반 */
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<ErrorData>> handleBusiness(BusinessException e) {
        log.debug("BusinessException: {}", e.getErrorCode().name());
        return toResponse(e.getErrorCode());
    }

    /** @Valid 요청 본문 검증 실패. 검증 어노테이션의 message에는 각 파트가 기능정의서 문구를 넣는다. */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<ErrorData>> handleMethodArgumentNotValid(MethodArgumentNotValidException e) {
        List<FieldErrorData> fieldErrors = new ArrayList<>();
        e.getBindingResult().getFieldErrors().forEach(error ->
                fieldErrors.add(new FieldErrorData(error.getField(), error.getDefaultMessage())));
        // 전역 오류(클래스 단위 검증)는 field에 objectName을 쓴다
        e.getBindingResult().getGlobalErrors().forEach(error ->
                fieldErrors.add(new FieldErrorData(error.getObjectName(), error.getDefaultMessage())));
        return toResponse(ErrorCode.INVALID_INPUT, fieldErrors);
    }

    /** 컨트롤러 메서드 파라미터(@RequestParam·@PathVariable 등) 검증 실패 */
    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ApiResponse<ErrorData>> handleHandlerMethodValidation(HandlerMethodValidationException e) {
        List<FieldErrorData> fieldErrors = new ArrayList<>();
        e.getParameterValidationResults().forEach(result -> {
            String parameterName = result.getMethodParameter().getParameterName();
            for (MessageSourceResolvable error : result.getResolvableErrors()) {
                // 객체 파라미터의 칸 오류면 칸 이름, 아니면 파라미터 이름
                String field = (error instanceof FieldError fieldError) ? fieldError.getField() : parameterName;
                fieldErrors.add(new FieldErrorData(field, error.getDefaultMessage()));
            }
        });
        return toResponse(ErrorCode.INVALID_INPUT, fieldErrors);
    }

    /** 서비스 등에서 @Validated로 검증한 값의 실패 */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiResponse<ErrorData>> handleConstraintViolation(ConstraintViolationException e) {
        List<FieldErrorData> fieldErrors = new ArrayList<>();
        e.getConstraintViolations().forEach(violation ->
                fieldErrors.add(new FieldErrorData(violation.getPropertyPath().toString(), violation.getMessage())));
        return toResponse(ErrorCode.INVALID_INPUT, fieldErrors);
    }

    /** 본문을 읽을 수 없음·필수 파라미터 없음·형식 불일치·지원하지 않는 Content-Type */
    @ExceptionHandler({
            HttpMessageNotReadableException.class,
            MissingServletRequestParameterException.class,
            MethodArgumentTypeMismatchException.class,
            HttpMediaTypeNotSupportedException.class
    })
    public ResponseEntity<ApiResponse<ErrorData>> handleBadRequest(Exception e) {
        log.debug("잘못된 요청 형식: {}", e.getClass().getSimpleName());
        return toResponse(ErrorCode.INVALID_INPUT);
    }

    /** 없는 주소 */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiResponse<ErrorData>> handleNoResource(NoResourceFoundException e) {
        return toResponse(ErrorCode.API_NOT_FOUND);
    }

    /** 지원하지 않는 HTTP 메서드 */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiResponse<ErrorData>> handleMethodNotSupported(HttpRequestMethodNotSupportedException e) {
        return toResponse(ErrorCode.METHOD_NOT_ALLOWED);
    }

    /** 서비스에서 변환하지 않은 유일 조건 위반. 원래는 각 서비스가 맞는 ErrorCode로 바꿔야 한다 (AGENTS §6). */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiResponse<ErrorData>> handleDataIntegrityViolation(DataIntegrityViolationException e) {
        log.warn("서비스에서 변환하지 않은 유일 조건 위반: {}", e.getClass().getSimpleName());
        return toResponse(ErrorCode.INTERNAL_SERVER_ERROR);
    }

    /** 그 밖의 모든 예외. 스택은 남기고 요청 값은 남기지 않는다. */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<ErrorData>> handleException(Exception e) {
        log.error("처리하지 못한 예외", e);
        return toResponse(ErrorCode.INTERNAL_SERVER_ERROR);
    }

    private ResponseEntity<ApiResponse<ErrorData>> toResponse(ErrorCode errorCode) {
        return ResponseEntity.status(errorCode.getStatus()).body(ApiResponse.fail(errorCode));
    }

    private ResponseEntity<ApiResponse<ErrorData>> toResponse(ErrorCode errorCode, List<FieldErrorData> fieldErrors) {
        return ResponseEntity.status(errorCode.getStatus()).body(ApiResponse.fail(errorCode, fieldErrors));
    }

    // 메서드 보안 미사용 — 접근 제어는 common/security 체커가 BusinessException으로 처리 (ACC-07)
}
