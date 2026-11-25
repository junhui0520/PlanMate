package com.planmate.budget.client;

import java.math.BigDecimal;
import java.net.CookieManager;
import java.net.http.HttpClient;
import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * BUD-04-01 한국수출입은행 환율 API 호출.
 * 키(EXCHANGE_API_KEY)는 서버에서만 쓰고 화면·로그·에러 메시지에 남기지 않는다.
 */
@Component
public class KoreaEximClient {

	private static final String BASE_URL = "https://oapi.koreaexim.go.kr";
	private static final String PATH = "/site/program/financial/exchangeJSON";
	private static final String DATA_EXCHANGE = "AP01"; // 환율
	private static final int RESULT_SUCCESS = 1; // 1 성공, 2 DATA 코드 오류, 3 인증키 오류, 4 하루 호출 한도 초과
	private static final DateTimeFormatter SEARCH_DATE = DateTimeFormatter.BASIC_ISO_DATE; // yyyyMMdd
	private static final Pattern CUR_UNIT = Pattern.compile("([A-Z]{3})(?:\\((\\d+)\\))?"); // 예: USD, JPY(100)

	private final RestClient restClient;
	private final String apiKey;

	// 키가 없는 컴퓨터에서도 서버는 켜지게 기본값을 빈 값으로 둔다(부를 때 검사).
	public KoreaEximClient(@Value("${EXCHANGE_API_KEY:}") String apiKey) {
		// 은행 서버가 가끔 쿠키를 주고 같은 주소로 다시 보내므로(302), 쿠키를 기억하고 따라간다.
		HttpClient httpClient = HttpClient.newBuilder()
				.connectTimeout(Duration.ofSeconds(5))
				.followRedirects(HttpClient.Redirect.NORMAL)
				.cookieHandler(new CookieManager())
				.build();
		JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
		requestFactory.setReadTimeout(Duration.ofSeconds(10));
		this.restClient = RestClient.builder().baseUrl(BASE_URL).requestFactory(requestFactory).build();
		this.apiKey = apiKey;
	}

	/**
	 * 발표 날짜 하나의 환율 목록. 주말·공휴일·오전 11시 전처럼 발표가 없으면 빈 목록.
	 */
	public List<KoreaEximRate> fetchRates(LocalDate searchDate) {
		if (apiKey == null || apiKey.isBlank()) {
			throw new IllegalStateException("EXCHANGE_API_KEY가 설정되지 않았습니다.");
		}

		RawRate[] body;
		try {
			body = restClient.get()
					.uri(uri -> uri.path(PATH)
							.queryParam("authkey", apiKey)
							.queryParam("searchdate", searchDate.format(SEARCH_DATE))
							.queryParam("data", DATA_EXCHANGE)
							.build())
					.retrieve()
					.body(RawRate[].class);
		} catch (RestClientException e) {
			// RestClient 에러 메시지에는 요청 주소(키 포함)가 들어가므로, 원래 예외를 붙이지 않고 다시 던진다.
			throw new IllegalStateException("한국수출입은행 환율 API 호출 실패 (" + e.getClass().getSimpleName() + ")");
		}

		if (body == null || body.length == 0) {
			return List.of();
		}
		for (RawRate raw : body) {
			if (raw.result() == null || raw.result() != RESULT_SUCCESS) {
				throw new IllegalStateException("한국수출입은행 환율 API 오류 (result=" + raw.result() + ")");
			}
		}
		return Arrays.stream(body)
				.filter(raw -> raw.curUnit() != null && raw.dealBasR() != null)
				.map(KoreaEximClient::toRate)
				.toList();
	}

	// "JPY(100)" + "843.41" -> JPY, 단위 100, 843.41원 (BUD-04-01 100단위 통화는 단위까지 저장)
	static KoreaEximRate toRate(RawRate raw) {
		Matcher m = CUR_UNIT.matcher(raw.curUnit().trim());
		if (!m.matches()) {
			throw new IllegalStateException("알 수 없는 통화 표기: " + raw.curUnit());
		}
		int unit = m.group(2) == null ? 1 : Integer.parseInt(m.group(2));
		return new KoreaEximRate(m.group(1), unit, parseAmount(raw.dealBasR()), raw.curNm());
	}

	// "1,343.6" -> 1343.6
	static BigDecimal parseAmount(String text) {
		return new BigDecimal(text.replace(",", "").trim());
	}

	/** 한국수출입은행이 보내는 한 줄 (필요한 칸만 읽음) */
	@JsonIgnoreProperties(ignoreUnknown = true)
	record RawRate(
			@JsonProperty("result") Integer result,
			@JsonProperty("cur_unit") String curUnit,
			@JsonProperty("deal_bas_r") String dealBasR, // 매매 기준율
			@JsonProperty("cur_nm") String curNm) {
	}

	/** 통화 하나의 환율: 단위(1 또는 100)당 원화 */
	public record KoreaEximRate(String currency, int unit, BigDecimal rate, String currencyName) {
	}

}
