package com.planmate.common.duplicate;

import com.planmate.common.exception.BusinessException;
import com.planmate.common.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;
import java.util.Optional;

/**
 * 같은 요청 두 번 처리 방지 (AGENTS §6).
 * 여행 생성(TRP-01-03)·일정 저장(SCH-06-01)·게시(PUB-01-03)·복제(PUB-07-02)·AI 적용(AIT-06-01)이 쓴다.
 * 요청 식별값(requestKey)은 화면이 만들어 보낸다(최대 64자).
 *
 * <p>사용 예 (업무 처리와 같은 트랜잭션 안에서 부른다):
 * <pre>{@code
 * @Transactional
 * public TripCreateResponse create(TripCreateRequest req, Long memberId) {
 *     Optional<RequestLog> done = duplicateRequestChecker.findProcessed(req.requestKey(), memberId);
 *     if (done.isPresent()) return  // done.get().getTripId()로 이전과 같은 응답
 *     // ... 업무 처리 ...
 *     duplicateRequestChecker.record(req.requestKey(), memberId, trip.getId());
 * }
 * }</pre>
 * 업무가 실패하면 기록도 함께 롤백되므로 같은 키로 다시 시도할 수 있다.
 * 오래된 기록 정리 작업은 보관 기준이 문서에 없어 만들지 않았다.
 */
@Component
@RequiredArgsConstructor
public class DuplicateRequestChecker {

    private static final int MAX_KEY_LENGTH = 64;   // REQUEST_LOG.REQUEST_KEY VARCHAR2(64)

    private final RequestLogRepository requestLogRepository;

    /**
     * 이미 처리한 요청인지 확인한다.
     * - 기록 없음: Optional.empty() -> 업무 처리를 진행
     * - 같은 회원의 기록: 그 기록 -> 이전 결과(tripId)로 같은 응답을 만든다
     * - 다른 회원의 키: DUPLICATE_REQUEST
     */
    public Optional<RequestLog> findProcessed(String requestKey, Long memberId) {
        validateKey(requestKey);
        Optional<RequestLog> found = requestLogRepository.findById(requestKey);
        if (found.isEmpty()) {
            return Optional.empty();
        }
        if (!Objects.equals(found.get().getMemberId(), memberId)) {
            throw new BusinessException(ErrorCode.DUPLICATE_REQUEST);
        }
        return found;
    }

    /**
     * 처리 성공을 기록한다. 반드시 업무 처리와 같은 트랜잭션 안에서 부른다(MANDATORY).
     * 동시에 들어온 같은 키는 유일 조건 위반이 나므로 DUPLICATE_REQUEST로 바꾸고, 그 트랜잭션은 롤백된다.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void record(String requestKey, Long memberId, Long tripId) {
        validateKey(requestKey);
        try {
            requestLogRepository.saveAndFlush(RequestLog.success(requestKey, memberId, tripId));
        } catch (DataIntegrityViolationException e) {
            throw new BusinessException(ErrorCode.DUPLICATE_REQUEST);
        }
    }

    // 요청 식별값: 비어 있지 않고 64자 이하
    private void validateKey(String requestKey) {
        if (requestKey == null || requestKey.isBlank() || requestKey.length() > MAX_KEY_LENGTH) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
    }
}
