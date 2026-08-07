package shop.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/** 계약 §2.5. 충전은 멱등하지 않다 — 두 번 호출하면 두 번 충전된다. */
@Schema(description = "포인트 충전 요청")
public record PointChargeRequest(

		@Schema(description = "충전 금액. 0 이하는 400 VALIDATION_ERROR", example = "50000", minimum = "1")
		@NotNull(message = "충전 금액은 필수입니다")
		@Min(value = 1, message = "충전 금액은 1 이상이어야 합니다")
		Integer amount) {
}
