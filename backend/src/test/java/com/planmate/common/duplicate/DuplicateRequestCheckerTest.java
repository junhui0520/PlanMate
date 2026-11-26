package com.planmate.common.duplicate;

import com.planmate.common.exception.BusinessException;
import com.planmate.common.exception.ErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/** 같은 요청 두 번 처리 방지 확인 (DB·Spring 컨텍스트 없이) */
@ExtendWith(MockitoExtension.class)
class DuplicateRequestCheckerTest {

    private static final String KEY = "req-key-1";

    @Mock
    private RequestLogRepository requestLogRepository;

    @InjectMocks
    private DuplicateRequestChecker checker;

    @Test
    void 기록이_없으면_empty() {
        given(requestLogRepository.findById(KEY)).willReturn(Optional.empty());

        assertThat(checker.findProcessed(KEY, 1L)).isEmpty();
    }

    @Test
    void 같은_회원의_기록이면_그_기록을_돌려준다() {
        RequestLog log = RequestLog.success(KEY, 1L, 100L);
        given(requestLogRepository.findById(KEY)).willReturn(Optional.of(log));

        Optional<RequestLog> result = checker.findProcessed(KEY, 1L);

        assertThat(result).containsSame(log);
        assertThat(result.get().getTripId()).isEqualTo(100L);
    }

    @Test
    void 다른_회원의_키면_DUPLICATE_REQUEST() {
        given(requestLogRepository.findById(KEY)).willReturn(Optional.of(RequestLog.success(KEY, 1L, 100L)));

        assertThatThrownBy(() -> checker.findProcessed(KEY, 2L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.DUPLICATE_REQUEST);
    }

    @Test
    void 빈_키나_65자_키는_INVALID_INPUT() {
        String key65 = "a".repeat(65);

        assertThatThrownBy(() -> checker.findProcessed("", 1L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.INVALID_INPUT);
        assertThatThrownBy(() -> checker.findProcessed(key65, 1L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.INVALID_INPUT);
        assertThatThrownBy(() -> checker.record(" ", 1L, 100L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.INVALID_INPUT);
        assertThatThrownBy(() -> checker.record(key65, 1L, 100L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.INVALID_INPUT);

        verify(requestLogRepository, never()).findById(any());
        verify(requestLogRepository, never()).saveAndFlush(any());
    }

    @Test
    void 동시_요청의_키_충돌은_DUPLICATE_REQUEST() {
        given(requestLogRepository.saveAndFlush(any(RequestLog.class)))
                .willThrow(new DataIntegrityViolationException("PK_REQUEST_LOG"));

        assertThatThrownBy(() -> checker.record(KEY, 1L, 100L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.DUPLICATE_REQUEST);
    }

    @Test
    void 정상_기록은_SUCCESS로_한_번_저장한다() {
        checker.record(KEY, 1L, 100L);

        ArgumentCaptor<RequestLog> captor = ArgumentCaptor.forClass(RequestLog.class);
        verify(requestLogRepository, times(1)).saveAndFlush(captor.capture());
        RequestLog saved = captor.getValue();
        assertThat(saved.getRequestKey()).isEqualTo(KEY);
        assertThat(saved.getMemberId()).isEqualTo(1L);
        assertThat(saved.getTripId()).isEqualTo(100L);
        assertThat(saved.getResult()).isEqualTo(RequestLog.Result.SUCCESS);
        assertThat(saved.getProcessedAt()).isNotNull();
        assertThat(saved.isNew()).isTrue();
    }
}
