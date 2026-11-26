package com.planmate.trip.controller;

import com.planmate.common.exception.BusinessException;
import com.planmate.common.exception.ErrorCode;
import com.planmate.common.response.ApiResponse;
import com.planmate.trip.dto.TripCreateRequest;
import com.planmate.trip.dto.TripCreateResponse;
import com.planmate.trip.dto.TripCreationCheckResponse;
import com.planmate.trip.service.TripService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 여행 생성·목록·상세·수정 요청 받기 (TRP-01~04). 약속: contracts/trip.yaml
 * 지금은 TRP-01 여행 생성만 있다.
 */
@RestController
@RequestMapping("/api/trips")
@RequiredArgsConstructor
public class TripController {

    private final TripService tripService;

    // TRP-01-01 여행 생성 가능 확인 ('새 여행 만들기'를 누르면 생성 화면을 열기 전에 부름)
    @GetMapping("/creation-check")
    public ApiResponse<TripCreationCheckResponse> checkCreatable() {
        return ApiResponse.ok(tripService.checkCreatable(currentMemberId()));
    }

    // TRP-01-03 여행 생성 저장
    @PostMapping
    public ApiResponse<TripCreateResponse> create(@Valid @RequestBody TripCreateRequest request) {
        return ApiResponse.ok(tripService.create(request, currentMemberId()));
    }

    // 로그인한 회원 ID
    private Long currentMemberId() {
        // TODO(A에 요청): pjh/security에서 로그인 회원 ID를 꺼내는 방법이 정해지면 바꾼다
        throw new BusinessException(ErrorCode.LOGIN_REQUIRED);
    }
}