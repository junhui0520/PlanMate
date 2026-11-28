package com.planmate.place.service;

import com.planmate.place.dto.PlaceResponse;
import com.planmate.place.dto.PlaceResponse.Category;
import com.planmate.place.dto.PlaceResponse.Provider;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * PLC-01-02 장소 정보 표준화: 좌표 순서·위치 미확인·주소·카테고리 대응표·먼 장소 판단.
 * 외부 호출 없이 변환 규칙만 확인한다.
 */
class PlaceSearchServiceTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 28, 20, 0, 0);

    private final PlaceSearchService service = new PlaceSearchService();

    private PlaceResponse kakao(String x, String y) {
        return service.standardizeKakao("8134236", "서울시청", "서울 중구 세종대로 110", "서울 중구 태평로1가 31",
                x, y, "PO3", "사회,공공기관 > 행정기관", NOW);
    }

    // ===== 좌표 =====

    @Test
    void 카카오는_x가_경도_y가_위도라서_순서를_바꿔_넣는다() {
        PlaceResponse place = kakao("126.9779692", "37.5662952");

        assertThat(place.provider()).isEqualTo(Provider.KAKAO);
        assertThat(place.lat()).isEqualByComparingTo("37.5662952");
        assertThat(place.lng()).isEqualByComparingTo("126.9779692");
        assertThat(place.fetchedAt()).isEqualTo(NOW);
    }

    @Test
    void 구글_좌표는_소수_7자리로_맞춘다() {
        PlaceResponse place = service.standardizeGoogle("ChIJ-tokyo-station", "도쿄역", "일본 도쿄도 지요다구 마루노우치 1초메",
                35.681236249, 139.7671248, "train_station", List.of("train_station", "point_of_interest"), NOW);

        assertThat(place.provider()).isEqualTo(Provider.GOOGLE);
        assertThat(place.lat()).isEqualTo(new BigDecimal("35.6812362"));
        assertThat(place.lng()).isEqualTo(new BigDecimal("139.7671248"));
        assertThat(place.lat().scale()).isEqualTo(7);
    }

    @Test
    void 영영_좌표와_빈_좌표는_위치_미확인이라_지도에_찍히지_않는다() {
        assertThat(kakao("0", "0").lat()).isNull();
        assertThat(kakao("0", "0").lng()).isNull();
        assertThat(kakao(null, null).lat()).isNull();
        assertThat(kakao("abc", "37.5").lat()).isNull();
    }

    @Test
    void 한쪽만_빈_좌표면_다른_쪽도_비운다() {
        PlaceResponse place = kakao("", "37.5662952");

        assertThat(place.lat()).isNull();
        assertThat(place.lng()).isNull();
    }

    @Test
    void 범위를_벗어난_좌표는_위치_미확인() {
        assertThat(kakao("126.97", "90.0000001").lat()).isNull();
        assertThat(kakao("180.0000001", "37.5").lat()).isNull();
        assertThat(kakao("-180", "-90").lat()).isNotNull();
    }

    @Test
    void 경도만_0인_좌표는_위치_미확인이_아니다() {
        // 경도 0인 실제 장소(그리니치 근처)가 있으므로 둘 다 0일 때만 미확인
        PlaceResponse place = service.standardizeGoogle("ChIJ-greenwich", "그리니치 천문대", "London SE10 8XJ",
                51.4769, 0.0, "museum", List.of(), NOW);

        assertThat(place.lat()).isNotNull();
        assertThat(place.lng()).isNotNull();
    }

    // ===== 주소·식별값 =====

    @Test
    void 카카오_주소는_도로명을_먼저_쓰고_없으면_지번() {
        assertThat(kakao("126.9779692", "37.5662952").address()).isEqualTo("서울 중구 세종대로 110");

        PlaceResponse noRoad = service.standardizeKakao("1", "어떤 장소", "  ", "서울 중구 태평로1가 31",
                "126.9779692", "37.5662952", "AT4", "여행 > 관광,명소", NOW);
        assertThat(noRoad.address()).isEqualTo("서울 중구 태평로1가 31");
    }

    @Test
    void 빈_장소ID와_장소명은_null로_넘긴다() {
        PlaceResponse place = service.standardizeKakao(" ", "", "서울 중구 세종대로 110", null,
                "126.9779692", "37.5662952", "PO3", null, NOW);

        assertThat(place.placeId()).isNull();
        assertThat(place.placeName()).isNull();
    }

    @Test
    void 주소_변환_결과는_제공자_ADDRESS이고_장소ID가_없다() {
        PlaceResponse home = service.standardizeKakaoAddress("서울 중구 세종대로 110", "서울 중구 태평로1가 31",
                "126.9779692", "37.5662952", NOW);

        assertThat(home.provider()).isEqualTo(Provider.ADDRESS);
        assertThat(home.placeId()).isNull();
        assertThat(home.address()).isEqualTo("서울 중구 세종대로 110");
        assertThat(home.lat()).isEqualByComparingTo("37.5662952");
        assertThat(home.lng()).isEqualByComparingTo("126.9779692");

        PlaceResponse overseas = service.standardizeGoogleAddress("1 Chome Marunouchi, Chiyoda City, Tokyo",
                35.6812362, 139.7671248, NOW);
        assertThat(overseas.provider()).isEqualTo(Provider.ADDRESS);
        assertThat(overseas.placeId()).isNull();
    }

    // ===== 카테고리 대응표 =====

    @Test
    void 카카오_그룹_코드를_내부_카테고리로_바꾼다() {
        assertThat(PlaceSearchService.kakaoCategory("AT4", null)).isEqualTo(Category.ATTRACTION);
        assertThat(PlaceSearchService.kakaoCategory("CT1", null)).isEqualTo(Category.ATTRACTION);
        assertThat(PlaceSearchService.kakaoCategory("FD6", null)).isEqualTo(Category.FOOD);
        assertThat(PlaceSearchService.kakaoCategory("CE7", null)).isEqualTo(Category.CAFE);
        assertThat(PlaceSearchService.kakaoCategory("MT1", null)).isEqualTo(Category.SHOPPING);
        assertThat(PlaceSearchService.kakaoCategory("AD5", null)).isEqualTo(Category.LODGING);
        assertThat(PlaceSearchService.kakaoCategory("SW8", null)).isEqualTo(Category.TRANSPORT);
        assertThat(PlaceSearchService.kakaoCategory("BK9", "금융,보험 > 금융서비스 > 은행")).isEqualTo(Category.ETC);
        assertThat(PlaceSearchService.kakaoCategory(null, null)).isEqualTo(Category.ETC);
    }

    @Test
    void 카카오_공항_기차역은_분류명으로_교통() {
        assertThat(PlaceSearchService.kakaoCategory("", "교통,수송 > 항공 > 공항")).isEqualTo(Category.TRANSPORT);
    }

    @Test
    void 구글은_primaryType을_먼저_보고_없으면_types를_앞에서부터_본다() {
        assertThat(PlaceSearchService.googleCategory("ramen_restaurant", List.of())).isEqualTo(Category.FOOD);
        assertThat(PlaceSearchService.googleCategory(null, List.of("point_of_interest", "cafe", "restaurant")))
                .isEqualTo(Category.CAFE);
        assertThat(PlaceSearchService.googleCategory("airport", null)).isEqualTo(Category.TRANSPORT);
        assertThat(PlaceSearchService.googleCategory("hotel", null)).isEqualTo(Category.LODGING);
        assertThat(PlaceSearchService.googleCategory("clothing_store", null)).isEqualTo(Category.SHOPPING);
        assertThat(PlaceSearchService.googleCategory("tourist_attraction", null)).isEqualTo(Category.ATTRACTION);
        assertThat(PlaceSearchService.googleCategory(null, List.of("establishment"))).isEqualTo(Category.ETC);
    }

    @Test
    void 구글_추가_유형도_내부_카테고리로_바꾼다() {
        assertThat(PlaceSearchService.googleCategory("buddhist_temple", null)).isEqualTo(Category.ATTRACTION);
        assertThat(PlaceSearchService.googleCategory("shinto_shrine", null)).isEqualTo(Category.ATTRACTION);
        assertThat(PlaceSearchService.googleCategory("castle", null)).isEqualTo(Category.ATTRACTION);
        assertThat(PlaceSearchService.googleCategory("steak_house", null)).isEqualTo(Category.FOOD);
        assertThat(PlaceSearchService.googleCategory("noodle_shop", null)).isEqualTo(Category.FOOD);
        assertThat(PlaceSearchService.googleCategory("bakery", null)).isEqualTo(Category.CAFE);
        assertThat(PlaceSearchService.googleCategory("tea_house", null)).isEqualTo(Category.CAFE);
        assertThat(PlaceSearchService.googleCategory("gift_shop", null)).isEqualTo(Category.SHOPPING);
        assertThat(PlaceSearchService.googleCategory("japanese_inn", null)).isEqualTo(Category.LODGING);
    }

    @Test
    void 디저트_식당은_식당_인터넷카페와_술집은_기타() {
        assertThat(PlaceSearchService.googleCategory("dessert_restaurant", null)).isEqualTo(Category.FOOD);
        assertThat(PlaceSearchService.googleCategory("internet_cafe", null)).isEqualTo(Category.ETC);
        assertThat(PlaceSearchService.googleCategory("bar", null)).isEqualTo(Category.ETC);
    }

    @Test
    void 편의점은_카카오와_맞춰_기타이고_뒤의_store로_넘어가지_않는다() {
        assertThat(PlaceSearchService.kakaoCategory("CS2", "가정,생활 > 편의점")).isEqualTo(Category.ETC);
        assertThat(PlaceSearchService.googleCategory("convenience_store", List.of("convenience_store", "food", "store")))
                .isEqualTo(Category.ETC);
        assertThat(PlaceSearchService.googleCategory(null, List.of("convenience_store", "store")))
                .isEqualTo(Category.ETC);
        // 다른 "_store"는 그대로 쇼핑
        assertThat(PlaceSearchService.googleCategory("book_store", null)).isEqualTo(Category.SHOPPING);
    }

    // ===== 도시 중심에서의 거리 =====

    @Test
    void 도시_중심에서_100km를_넘으면_먼_장소() {
        BigDecimal centerLat = new BigDecimal("37.0000000");
        BigDecimal centerLng = new BigDecimal("127.0000000");

        // 같은 경도에서 위도 0.9도 = 약 100.08km, 0.89도 = 약 98.96km
        PlaceResponse over = kakao("127.0", "37.9");
        PlaceResponse under = kakao("127.0", "37.89");

        assertThat(service.distanceMeters(over, centerLat, centerLng)).isBetween(100_000L, 100_200L);
        assertThat(service.isFarFromCenter(over, centerLat, centerLng)).isTrue();
        assertThat(service.isFarFromCenter(under, centerLat, centerLng)).isFalse();
    }

    @Test
    void 위치_미확인_장소는_거리가_없고_먼_장소도_아니다() {
        PlaceResponse unknown = kakao("0", "0");
        BigDecimal centerLat = new BigDecimal("37.5");
        BigDecimal centerLng = new BigDecimal("127.0");

        assertThat(service.distanceMeters(unknown, centerLat, centerLng)).isNull();
        assertThat(service.isFarFromCenter(unknown, centerLat, centerLng)).isFalse();
    }
}
