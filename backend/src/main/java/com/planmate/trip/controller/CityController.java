package com.planmate.trip.controller;

import com.planmate.common.response.ApiResponse;
import com.planmate.trip.dto.CityResponse;
import com.planmate.trip.dto.CitySearchResponse;
import com.planmate.trip.dto.CitySelectRequest;
import com.planmate.trip.service.CityService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 도시 검색·선택 요청 받기 (TRP-01-02). 약속: contracts/trip.yaml
 */
@RestController
@RequestMapping("/api/cities")
@RequiredArgsConstructor
public class CityController {

    private final CityService cityService;

    // TRP-01-02 도시 검색 (최대 3개, 결과 없으면 빈 배열). 예) GET /api/cities/search?keyword=도쿄
    @GetMapping("/search")
    public ApiResponse<List<CitySearchResponse>> search(
            @RequestParam
            @NotBlank(message = "입력값을 확인해 주세요.")
            @Size(max = 50, message = "입력값을 확인해 주세요.")
            String keyword) {
        return ApiResponse.ok(cityService.search(keyword));
    }

    // TRP-01-02 도시 선택 (처음 고른 도시면 CITY에 등록)
    @PostMapping
    public ApiResponse<CityResponse> select(@Valid @RequestBody CitySelectRequest request) {
        return ApiResponse.ok(cityService.select(request.googlePlaceId()));
    }
}