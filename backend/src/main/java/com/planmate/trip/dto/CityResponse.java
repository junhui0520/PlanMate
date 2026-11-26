package com.planmate.trip.dto;

import com.planmate.trip.entity.City;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

/**
 * 도시 정보 (contracts/trip.yaml CityResponse).
 * 선택 후 칸 아래 "현지 시간 UTC+9 · 지도: 구글" 표시에 쓴다 (TRP-01-02).
 */
public record CityResponse(
        Long cityId,
        String nameKo,
        String nameEn,
        String countryCode,
        String timezone,   // IANA 이름 (예: Asia/Tokyo)
        String utcOffset,  // 지금 시각 기준 UTC와의 차이 (예: +09:00). DB에 저장하지 않고 계산
        City.MapProvider mapProvider,
        BigDecimal centerLat,
        BigDecimal centerLng) {

    public static CityResponse from(City city) {
        return new CityResponse(
                city.getCityId(),
                city.getNameKo(),
                city.getNameEn(),
                city.getCountryCode().trim(), // CHAR(2)
                city.getTimezone(),
                utcOffsetNow(city.getTimezone()),
                city.getMapProvider(),
                city.getCenterLat(),
                city.getCenterLng());
    }

    // 서머타임이 있는 도시는 날짜에 따라 달라지므로 '지금' 기준으로 계산한다
    private static String utcOffsetNow(String timezone) {
        ZoneOffset offset = ZoneId.of(timezone).getRules().getOffset(Instant.now());
        // UTC 자체는 "Z"로 나와서 계약 형식(+00:00)에 맞춘다
        return offset.equals(ZoneOffset.UTC) ? "+00:00" : offset.getId();
    }
}