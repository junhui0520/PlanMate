package com.planmate.common.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/**
 * JPA 설정: 생성·수정 시각 자동 기록(Auditing) 켜기.
 * 공통 BaseEntity는 두지 않는다. 테이블마다 시각 칼럼 이름이 달라서,
 * 필요한 엔티티가 @EntityListeners(AuditingEntityListener.class)·@CreatedDate·@LastModifiedDate를 직접 쓴다.
 */
@Configuration
@EnableJpaAuditing
public class JpaConfig {
}
