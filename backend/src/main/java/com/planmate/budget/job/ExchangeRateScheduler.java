package com.planmate.budget.job;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.planmate.budget.service.ExchangeRateService;
import com.planmate.common.util.DateTimeUtil;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * BUD-04-01 매일 오전 11시 이후 한국수출입은행 환율을 1번 받아 저장한다.
 * 실패하면 예외가 그대로 나가 Spring이 로그를 남기고, 저장하지 않으므로 직전 환율을 쓴다(다음 날 다시 조회).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ExchangeRateScheduler {

	private final ExchangeRateService exchangeRateService;

	// 서버를 켤 때 환율 표가 비어 있으면 바로 채울지 (기본 꺼짐: 테스트·빌드 때 은행 API와 DB를 건드리지 않게)
	// 켜는 법: bootRun --args='--spring.profiles.active=local --planmate.budget.exchange-rate.fill-on-startup=true'
	@Value("${planmate.budget.exchange-rate.fill-on-startup:false}")
	private boolean fillOnStartup;

	// 매일 11:05 (한국 시간). 발표가 11시 무렵이라 5분 여유
	@Scheduled(cron = "0 5 11 * * *", zone = DateTimeUtil.SCHEDULER_ZONE)
	public void refreshDaily() {
		exchangeRateService.refreshTodayRates();
	}

	// 옵션을 켰을 때만: 서버가 다 켜진 뒤 1번 받아 저장 (표가 비어 있으면 최근 발표 환율로 처음 채움)
	@EventListener(ApplicationReadyEvent.class)
	public void refreshOnStartup() {
		if (!fillOnStartup) {
			return;
		}
		log.info("fill-on-startup 켜짐: 환율 받아 저장 시도");
		exchangeRateService.refreshTodayRates();
	}

}
