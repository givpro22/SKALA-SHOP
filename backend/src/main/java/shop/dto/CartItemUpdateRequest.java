package shop.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * 계약 §9.2.4 — 수량 변경. <b>절대값이다. 더하지 않는다.</b>
 *
 * <p><b>{@code quantity: 0}을 "삭제"로 해석하지 않는다.</b> 0을 보내면 400
 * {@code VALIDATION_ERROR}이고 삭제는 {@code DELETE}가 한다. 0을 삭제로 받으면 수량 입력창에서
 * 지웠다가 다시 쓰는 흔한 조작이 <b>라인 삭제</b>가 된다.
 */
@Schema(description = "장바구니 수량 변경 요청")
public record CartItemUpdateRequest(

		@Schema(description = "변경할 수량. 절대값이며 기존 수량에 더해지지 않는다. "
				+ "0은 삭제가 아니라 400이다",
				example = "2", minimum = "1", maximum = "99")
		@NotNull(message = "수량은 필수입니다.")
		@Min(value = 1, message = "수량은 1개 이상이어야 합니다.")
		@Max(value = 99, message = "수량은 99개 이하여야 합니다.")
		Integer quantity) {
}
