package shop.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/** 검증 실패 상세(계약 §4.2). {@code VALIDATION_ERROR}일 때만 채워진다. */
@Schema(description = "검증 실패 필드 상세")
public record FieldError(

		@Schema(description = "실패 필드명", example = "items[0].quantity")
		String field,

		@Schema(description = "거부된 값의 문자열 표현", example = "0", nullable = true)
		String rejectedValue,

		@Schema(description = "실패 사유", example = "1 이상이어야 합니다")
		String reason) {
}
