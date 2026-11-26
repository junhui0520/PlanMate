package com.planmate.common.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 정해진 시간에 도는 작업(@Scheduled) 켜기 (환율·영구 삭제·알림 정리 등).
 * 시간대는 각 @Scheduled에 zone = DateTimeUtil.SCHEDULER_ZONE("Asia/Seoul")을 적는다 (AGENTS §6).
 * 예: @Scheduled(cron = "0 0 3 * * *", zone = DateTimeUtil.SCHEDULER_ZONE)
 */
@Configuration
@EnableScheduling
public class SchedulerConfig {
}
