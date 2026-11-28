package com.planmate.place.service;

import com.planmate.place.dto.PlaceResponse;
import com.planmate.place.dto.PlaceResponse.Category;
import com.planmate.place.dto.PlaceResponse.Provider;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 장소 키워드 검색·자동완성·정보 표준화 (PLC-01).
 * 지금은 PLC-01-02 정보 표준화만 있다. 키워드 검색·자동완성(PLC-01-01)은 만들 때 이 파일에 더한다.
 *
 * 표준화 규칙 (PLC-01-02)
 * - 카카오는 x = 경도, y = 위도(문자열)라 순서를 바꿔 숫자 lat·lng로 만든다.
 * - 좌표는 소수 7자리. 위도 -90~90, 경도 -180~180.
 * - 비었거나 (0, 0)이거나 범위를 벗어난 좌표는 lat·lng 둘 다 null = '위치 미확인' (임의 좌표를 넣지 않음).
 * - 주소는 도로명 우선.
 * - 조회 시각(fetchedAt)은 부르는 쪽이 DateTimeUtil.now()로 넘긴다.
 */
@Service
public class PlaceSearchService {

    /** PLC-01-02 도시 중심에서 이 거리를 넘으면 경고만 표시 (저장은 허용, 근교 포함 여행) */
    public static final long FAR_DISTANCE_METERS = 100_000L;

    private static final int COORDINATE_SCALE = 7;
    private static final BigDecimal LAT_LIMIT = BigDecimal.valueOf(90);
    private static final BigDecimal LNG_LIMIT = BigDecimal.valueOf(180);
    private static final double EARTH_RADIUS_METERS = 6_371_000d;

    // ===== PLC-01-02 카테고리 대응표 (사용자 확정 11/28) =====
    // 카카오 코드값은 카카오맵 REST API '키워드로 장소 검색'의 CategoryGroupCode 표,
    // 구글 유형 이름은 Places API(New) '장소 유형' 표 A와 대조했다(11/28).
    // 단, 카카오 분류명 "교통,수송"은 문서에 없어 실제 응답으로 확인해야 한다.

    // 카카오 category_group_code -> 내부 코드. 표에 없는 코드(CS2 편의점 등)는 ETC
    private static final Map<String, Category> KAKAO_GROUP_CATEGORY = Map.of(
            "AT4", Category.ATTRACTION, // 관광명소
            "CT1", Category.ATTRACTION, // 문화시설
            "FD6", Category.FOOD,       // 음식점
            "CE7", Category.CAFE,       // 카페
            "MT1", Category.SHOPPING,   // 대형마트
            "AD5", Category.LODGING,    // 숙박
            "SW8", Category.TRANSPORT); // 지하철역

    // 공항·기차역은 카카오 그룹 코드가 없어 분류명(category_name) 앞부분으로 판단
    private static final String KAKAO_TRANSPORT_NAME_PREFIX = "교통,수송";

    // 구글 Places API(New) 장소 유형 -> 내부 코드. primaryType을 먼저 보고, 해당 없으면 types를 앞에서부터 본다
    private static final Set<String> GOOGLE_TRANSPORT = Set.of(
            "airport", "international_airport", "train_station", "subway_station",
            "light_rail_station", "bus_station", "transit_station");
    private static final Set<String> GOOGLE_LODGING = Set.of(
            "lodging", "hotel", "motel", "hostel", "resort_hotel", "guest_house", "bed_and_breakfast",
            "inn", "japanese_inn", "budget_japanese_inn", "extended_stay_hotel", "private_guest_room");
    private static final Set<String> GOOGLE_CAFE = Set.of(
            "cafe", "coffee_shop", "tea_house", "cat_cafe", "dog_cafe", "coffee_roastery", "coffee_stand",
            "bakery", "dessert_shop", "cake_shop", "pastry_shop", "ice_cream_shop", "donut_shop");
    private static final Set<String> GOOGLE_FOOD = Set.of(
            "restaurant", "steak_house", "diner", "bistro", "food_court", "cafeteria", "deli",
            "gastropub", "noodle_shop", "sandwich_shop", "kebab_shop"); // + "_restaurant"로 끝나는 유형
    private static final Set<String> GOOGLE_SHOPPING = Set.of(
            "shopping_mall", "department_store", "market", "supermarket", "store",
            "gift_shop", "flea_market", "farmers_market", "hypermarket", "discount_supermarket"); // + "_store"로 끝나는 유형
    private static final Set<String> GOOGLE_ATTRACTION = Set.of(
            "tourist_attraction", "museum", "art_gallery", "park", "amusement_park",
            "aquarium", "zoo", "national_park", "historical_landmark",
            "historical_place", "cultural_landmark", "monument", "castle", "art_museum", "history_museum",
            "observation_deck", "botanical_garden", "garden", "plaza", "beach", "scenic_spot",
            "buddhist_temple", "shinto_shrine", "city_park", "state_park", "water_park", "wildlife_park");
    // 편의점은 카카오(CS2 -> ETC)와 맞춰 ETC. "_store" 규칙보다 먼저 확정하고, types의 뒤 유형(store 등)을 더 보지 않는다
    private static final Set<String> GOOGLE_ETC = Set.of("convenience_store");

    // ===== PLC-01-02 검색 결과 변환 =====

    /** 카카오 키워드 검색 결과 1건 -> 공통 장소 형식. lat = y, lng = x */
    public PlaceResponse standardizeKakao(String id, String placeName, String roadAddressName, String addressName,
                                          String x, String y, String categoryGroupCode, String categoryName,
                                          LocalDateTime fetchedAt) {
        return place(Provider.KAKAO, id, placeName,
                firstNonBlank(roadAddressName, addressName),
                parseCoordinate(y), parseCoordinate(x),
                kakaoCategory(categoryGroupCode, categoryName),
                fetchedAt);
    }

    /** 구글 Places API(New) 결과 1건 -> 공통 장소 형식. 좌표는 location.latitude·longitude (숫자) */
    public PlaceResponse standardizeGoogle(String id, String displayName, String formattedAddress,
                                           Double latitude, Double longitude,
                                           String primaryType, List<String> types,
                                           LocalDateTime fetchedAt) {
        return place(Provider.GOOGLE, id, displayName, formattedAddress,
                toDecimal(latitude), toDecimal(longitude),
                googleCategory(primaryType, types),
                fetchedAt);
    }

    // ===== PLC-01-02 주소 -> 좌표 변환 결과 (장소 ID 없음 = ADDRESS, 개인 출발 장소에만 사용) =====

    /** 국내: 카카오 주소 검색 결과 1건. 도로명 우선, lat = y, lng = x */
    public PlaceResponse standardizeKakaoAddress(String roadAddressName, String addressName,
                                                 String x, String y, LocalDateTime fetchedAt) {
        return place(Provider.ADDRESS, null, null,
                firstNonBlank(roadAddressName, addressName),
                parseCoordinate(y), parseCoordinate(x),
                null, fetchedAt);
    }

    /** 해외: 구글 Geocoding 결과 1건 */
    public PlaceResponse standardizeGoogleAddress(String formattedAddress, Double latitude, Double longitude,
                                                  LocalDateTime fetchedAt) {
        return place(Provider.ADDRESS, null, null, formattedAddress,
                toDecimal(latitude), toDecimal(longitude),
                null, fetchedAt);
    }

    // ===== PLC-01-02 도시 중심에서의 거리 =====

    /**
     * 도시 중심에서의 직선거리(m, 반올림). 위치 미확인이면 null.
     * 먼 장소 경고(PLC-01-02)와 검색 결과 카드의 거리 표시(PLC-01-01)에 쓴다.
     */
    public Long distanceMeters(PlaceResponse place, BigDecimal centerLat, BigDecimal centerLng) {
        if (place.lat() == null || place.lng() == null || centerLat == null || centerLng == null) {
            return null;
        }
        double lat1 = Math.toRadians(centerLat.doubleValue());
        double lat2 = Math.toRadians(place.lat().doubleValue());
        double dLat = lat2 - lat1;
        double dLng = Math.toRadians(place.lng().doubleValue() - centerLng.doubleValue());
        // 하버사인 공식 (지구를 반지름 6,371km 구로 보고 계산)
        double h = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(lat1) * Math.cos(lat2) * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        double meters = 2 * EARTH_RADIUS_METERS * Math.asin(Math.min(1d, Math.sqrt(h)));
        return Math.round(meters);
    }

    /** PLC-01-02 도시 중심에서 100km를 넘는 장소인지 (경고만, 저장은 허용). 위치 미확인이면 false */
    public boolean isFarFromCenter(PlaceResponse place, BigDecimal centerLat, BigDecimal centerLng) {
        Long meters = distanceMeters(place, centerLat, centerLng);
        return meters != null && meters > FAR_DISTANCE_METERS;
    }

    // ===== 카테고리 변환 (위 대응표) =====

    static Category kakaoCategory(String categoryGroupCode, String categoryName) {
        if (categoryGroupCode != null) {
            Category mapped = KAKAO_GROUP_CATEGORY.get(categoryGroupCode.strip());
            if (mapped != null) {
                return mapped;
            }
        }
        if (categoryName != null && categoryName.strip().startsWith(KAKAO_TRANSPORT_NAME_PREFIX)) {
            return Category.TRANSPORT;
        }
        return Category.ETC;
    }

    static Category googleCategory(String primaryType, List<String> types) {
        Category mapped = googleCategoryOf(primaryType);
        if (mapped != null) {
            return mapped;
        }
        if (types != null) {
            for (String type : types) {
                mapped = googleCategoryOf(type);
                if (mapped != null) {
                    return mapped;
                }
            }
        }
        return Category.ETC;
    }

    // 유형 하나 -> 내부 코드. 해당 없으면 null (다음 유형을 본다)
    private static Category googleCategoryOf(String type) {
        if (type == null || type.isBlank()) {
            return null;
        }
        String t = type.strip();
        if (GOOGLE_ETC.contains(t)) {
            return Category.ETC;
        }
        if (GOOGLE_TRANSPORT.contains(t)) {
            return Category.TRANSPORT;
        }
        if (GOOGLE_LODGING.contains(t)) {
            return Category.LODGING;
        }
        if (GOOGLE_CAFE.contains(t)) {
            return Category.CAFE;
        }
        if (GOOGLE_FOOD.contains(t) || t.endsWith("_restaurant")) {
            return Category.FOOD;
        }
        if (GOOGLE_SHOPPING.contains(t) || t.endsWith("_store")) {
            return Category.SHOPPING;
        }
        if (GOOGLE_ATTRACTION.contains(t)) {
            return Category.ATTRACTION;
        }
        return null;
    }

    // ===== 좌표·문자열 도우미 =====

    // 공통 장소 형식 만들기. 좌표가 잘못됐으면 둘 다 비우고(위치 미확인), 맞으면 소수 7자리로 맞춘다
    private static PlaceResponse place(Provider provider, String placeId, String placeName, String address,
                                       BigDecimal lat, BigDecimal lng, Category category,
                                       LocalDateTime fetchedAt) {
        boolean valid = isValidCoordinate(lat, lng);
        return new PlaceResponse(
                provider,
                blankToNull(placeId),
                blankToNull(placeName),
                blankToNull(address),
                valid ? lat.setScale(COORDINATE_SCALE, RoundingMode.HALF_UP) : null,
                valid ? lng.setScale(COORDINATE_SCALE, RoundingMode.HALF_UP) : null,
                category,
                fetchedAt);
    }

    // 위도 -90~90, 경도 -180~180, 둘 다 있어야 하고 (0, 0)은 위치 미확인
    private static boolean isValidCoordinate(BigDecimal lat, BigDecimal lng) {
        if (lat == null || lng == null) {
            return false;
        }
        if (lat.abs().compareTo(LAT_LIMIT) > 0 || lng.abs().compareTo(LNG_LIMIT) > 0) {
            return false;
        }
        return !(lat.signum() == 0 && lng.signum() == 0);
    }

    // 문자열 좌표 -> 숫자. 비었거나 숫자가 아니면 null (임의 값으로 바꾸지 않고 '위치 미확인'으로 둔다)
    private static BigDecimal parseCoordinate(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return new BigDecimal(value.strip());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static BigDecimal toDecimal(Double value) {
        if (value == null || value.isNaN() || value.isInfinite()) {
            return null;
        }
        return BigDecimal.valueOf(value);
    }

    private static String blankToNull(String value) {
        return (value == null || value.isBlank()) ? null : value.strip();
    }

    private static String firstNonBlank(String first, String second) {
        String picked = blankToNull(first);
        return picked != null ? picked : blankToNull(second);
    }
}
