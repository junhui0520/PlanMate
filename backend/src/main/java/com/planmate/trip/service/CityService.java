package com.planmate.trip.service;

import com.planmate.common.exception.BusinessException;
import com.planmate.common.exception.ErrorCode;
import com.planmate.trip.dto.CityResponse;
import com.planmate.trip.dto.CitySearchResponse;
import com.planmate.trip.entity.City;
import com.planmate.trip.repository.CityRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * 도시 검색·처음 선택 시 자동 등록 (TRP-01-02).
 * C 파트도 이 서비스로 도시(지도 제공자·시간대)를 조회한다.
 */
@Service
@RequiredArgsConstructor
public class CityService {

    private final CityRepository cityRepository;

    /**
     * TRP-01-02 도시 검색 (최대 3개, DB에 저장하지 않음).
     * 구글 장소 자동완성은 C 파트 client를 거친다.
     */
    public List<CitySearchResponse> search(String keyword) {
        // TODO(C에 요청): 구글 장소 자동완성(도시+국가, 한국어, 최대 3개) client 메서드
        //  예) List<CitySearchResponse> found = googleMapClient.searchCities(keyword.strip(), 3);
        //  호출이 실패하면 CITY_SEARCH_FAILED
        throw new BusinessException(ErrorCode.CITY_SEARCH_FAILED);
    }

    /**
     * TRP-01-02 도시 선택.
     * 이미 등록된 도시면 그대로 돌려주고, 처음 고른 도시면 CITY에 등록한다.
     */
    @Transactional
    public CityResponse select(String googlePlaceId) {
        // 1) 같은 도시는 한 번만 등록: 구글 장소 ID로 먼저 찾는다
        Optional<City> registered = cityRepository.findByGooglePlaceId(googlePlaceId);
        if (registered.isPresent()) {
            return CityResponse.from(registered.get());
        }

        // 2) 처음 고른 도시: 구글에서 상세 정보와 시간대를 받아 등록한다
        // TODO(C에 요청): 구글 장소 상세(도시명 한·영, 국가 코드, 중심 좌표) + 구글 시간대 API client 메서드
        //  받으면 아래처럼 등록한다. 시간대를 못 가져오면 등록하지 않고 CITY_INFO_FAILED
        //  City city = City.register(googlePlaceId, nameKo, nameEn, countryCode, timezone, lat, lng);
        //  return CityResponse.from(saveNewCity(city));
        throw new BusinessException(ErrorCode.CITY_INFO_FAILED);
    }

    /**
     * 도시 1개 조회 (TripService의 여행 생성·C 파트가 사용).
     * 없는 도시 ID면 INVALID_INPUT (화면을 거치지 않고 잘못된 ID를 보낸 경우).
     */
    @Transactional(readOnly = true)
    public City getCity(Long cityId) {
        return cityRepository.findById(cityId)
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_INPUT));
    }

    /*
     * 새 도시 저장. 두 사람이 같은 새 도시를 동시에 고르면 한쪽은 유일 조건(UQ_CITY_01) 위반이 난다.
     * 그때는 CITY_INFO_FAILED로 바꾼다. 화면이 [다시 시도]하면 1)에서 이미 등록된 도시를 찾는다.
     */
    private City saveNewCity(City city) {
        try {
            return cityRepository.saveAndFlush(city);
        } catch (DataIntegrityViolationException e) {
            throw new BusinessException(ErrorCode.CITY_INFO_FAILED);
        }
    }
}