package com.planmate.common.duplicate;

import org.springframework.data.jpa.repository.JpaRepository;

/** REQUEST_LOG (요청 기록) 넣기·꺼내기 */
public interface RequestLogRepository extends JpaRepository<RequestLog, String> {
}
