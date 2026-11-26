package com.planmate.common.util;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.regex.Pattern;

/**
 * 날짜·시간 공통 처리 (AGENTS §6 시간).
 * - 여행 안의 날짜·시각은 여행지 현지 기준, 시간대 변환 없이 저장한다.
 * - 기록 시각(CREATED_AT 등)은 서버 시각 LocalDateTime.
 * - 시각 칸 VARCHAR2(5)는 "HH:mm"(24시간) 문자열.
 * 5분 단위 같은 기능별 규칙은 여기에 넣지 않는다(각 파트 담당).
 */
public final class DateTimeUtil {

    /** 정해진 시간 작업 시간대. 사용: @Scheduled(cron = "...", zone = DateTimeUtil.SCHEDULER_ZONE) */
    public static final String SCHEDULER_ZONE = "Asia/Seoul";

    /** 시각 칸 형식 "HH:mm" (24시간) */
    public static final DateTimeFormatter HH_MM = DateTimeFormatter.ofPattern("HH:mm");

    // "00:00" ~ "23:59"
    private static final Pattern HH_MM_PATTERN = Pattern.compile("^([01]\\d|2[0-3]):[0-5]\\d$");

    private DateTimeUtil() {
    }

    /** 기록 시각: 서버 시각 */
    public static LocalDateTime now() {
        return LocalDateTime.now();
    }

    /** 여행지 현지 오늘. CITY.TIMEZONE의 IANA 이름(예: "Asia/Tokyo")을 받는다. */
    public static LocalDate todayIn(String ianaTimezone) {
        return LocalDate.now(ZoneId.of(ianaTimezone));
    }

    /** 24시간 "00:00"~"23:59" 형식인지 검사 */
    public static boolean isValidHhMm(String value) {
        return value != null && HH_MM_PATTERN.matcher(value).matches();
    }

    /** "HH:mm" 문자열 -> LocalTime */
    public static LocalTime parseHhMm(String value) {
        return LocalTime.parse(value, HH_MM);
    }

    /** LocalTime -> "HH:mm" 문자열 */
    public static String formatHhMm(LocalTime time) {
        return time.format(HH_MM);
    }
}
