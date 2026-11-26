package com.planmate.trip.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * CITY (도시) — 여행 도시. 처음 선택될 때 자동 등록 (TRP-01-02).
 * B 파트가 주인이고, C 파트는 CityService로 사용한다.
 */
@Entity
@Table(name = "CITY")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class City {

    /** 지도 제공자 (DB설계서 허용값: KAKAO, GOOGLE). 대한민국 = KAKAO, 그 밖 = GOOGLE */
    public enum MapProvider {
        KAKAO, GOOGLE
    }

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "SEQ_CITY_GEN")
    @SequenceGenerator(name = "SEQ_CITY_GEN", sequenceName = "SEQ_CITY", allocationSize = 1)
    @Column(name = "CITY_ID")
    private Long cityId;

    // 같은 도시는 한 번만 등록 (UQ_CITY_01)
    @Column(name = "GOOGLE_PLACE_ID", nullable = false, length = 300)
    private String googlePlaceId;

    @Column(name = "NAME_KO", nullable = false, length = 100)
    private String nameKo;

    @Column(name = "NAME_EN", nullable = false, length = 100)
    private String nameEn;

    // 국가 코드 2자리 (예: KR, JP)
    @Column(name = "COUNTRY_CODE", nullable = false, length = 2)
    private String countryCode;

    // IANA 시간대 이름 (예: Asia/Tokyo)
    @Column(name = "TIMEZONE", nullable = false, length = 50)
    private String timezone;

    @Enumerated(EnumType.STRING)
    @Column(name = "MAP_PROVIDER", nullable = false, length = 10)
    private MapProvider mapProvider;

    @Column(name = "CENTER_LAT", nullable = false, precision = 10, scale = 7)
    private BigDecimal centerLat;

    @Column(name = "CENTER_LNG", nullable = false, precision = 10, scale = 7)
    private BigDecimal centerLng;

    private City(String googlePlaceId, String nameKo, String nameEn, String countryCode,
                 String timezone, MapProvider mapProvider, BigDecimal centerLat, BigDecimal centerLng) {
        this.googlePlaceId = googlePlaceId;
        this.nameKo = nameKo;
        this.nameEn = nameEn;
        this.countryCode = countryCode;
        this.timezone = timezone;
        this.mapProvider = mapProvider;
        this.centerLat = centerLat;
        this.centerLng = centerLng;
    }

    /**
     * TRP-01-02 처음 선택된 도시 등록.
     * 지도 제공자는 국가로 정한다: KR = KAKAO, 그 밖 = GOOGLE
     */
    public static City register(String googlePlaceId, String nameKo, String nameEn, String countryCode,
                                String timezone, BigDecimal centerLat, BigDecimal centerLng) {
        MapProvider provider = "KR".equals(countryCode) ? MapProvider.KAKAO : MapProvider.GOOGLE;
        return new City(googlePlaceId, nameKo, nameEn, countryCode, timezone, provider, centerLat, centerLng);
    }
}