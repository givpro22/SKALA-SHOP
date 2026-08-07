package shop.dto;

import java.time.LocalDateTime;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonFormat;

import io.swagger.v3.oas.annotations.media.Schema;
import shop.exception.ErrorCode;

/**
 * 모든 실패 응답의 단일 형태(계약 §4.1). 상태코드와 무관하게 형태가 같아야 프론트가 한 곳에서 파싱한다.
 *
 * <p>{@code @JsonInclude(NON_NULL)}을 쓰지 않는다. {@code fieldErrors}는 값이 없을 때 키를
 * 생략하지 않고 {@code null}로 내려보낸다 — 키가 사라지면 프론트 타입이 흔들린다(계약 §0.4).
 */
@Schema(description = "실패 응답 공통 형태")
public record ErrorResponse(

		@Schema(description = "에러 코드. 프론트는 이 값으로 분기한다", example = "INSUFFICIENT_POINT")
		String code,

		@Schema(description = "사용자에게 보여줄 메시지. 구체 수치를 포함한다",
				example = "포인트가 부족합니다. 보유 포인트: 10,000원, 주문 총액: 25,000원")
		String message,

		@Schema(description = "서버 발생 시각", example = "2026-08-07T16:05:00")
		@JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss")
		LocalDateTime timestamp,

		@Schema(description = "요청 경로", example = "/api/orders")
		String path,

		@Schema(description = "VALIDATION_ERROR일 때만 채워진다. 그 외에는 null", nullable = true)
		List<FieldError> fieldErrors) {

	public static ErrorResponse of(ErrorCode errorCode, String message, String path) {
		return new ErrorResponse(errorCode.code(), message, LocalDateTime.now(), path, null);
	}

	public static ErrorResponse of(ErrorCode errorCode, String message, String path,
			List<FieldError> fieldErrors) {
		return new ErrorResponse(errorCode.code(), message, LocalDateTime.now(), path, fieldErrors);
	}
}
