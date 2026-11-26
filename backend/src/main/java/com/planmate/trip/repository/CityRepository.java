package com.planmate.trip.repository;

import com.planmate.trip.entity.City;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * CITY (도시) 넣기·꺼내기 (TRP-01-02).
 */
public interface CityRepository extends JpaRepository<City, Long> {

    // TRP-01-02 같은 도시는 한 번만 등록: 구글 장소 ID로 이미 등록된 도시 찾기
    Optional<City> findByGooglePlaceId(String googlePlaceId);
}