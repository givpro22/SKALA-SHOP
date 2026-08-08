package shop.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * 계약 §9.2.3 — 담기. <b>상대값이다. 이미 담긴 수량에 더한다</b>(BR-24).
 *
 * <p>{@code PUT}(수량 변경)은 절대값이다. 담기 버튼은 누를 때마다 늘어야 하고 수량 입력창은
 * 입력한 값이 되어야 한다 — 둘을 같은 시맨틱으로 만들면 한쪽이 반드시 어색해진다.
 *
 * <p>여기의 {@code @Max(99)}는 <b>단건 요청만</b> 막는다. 90개가 담긴 상품에 20개를 더 담는 요청은
 * 형식상 유효하므로, 합산 결과의 상한은 {@code CartItem.applyQuantity}가 검사한다.
 */
@Schema(description = "장바구니 담기 요청")
public record CartItemAddRequest(

		@Schema(description = "담을 상품 id", example = "4")
		@NotNull(message = "상품 id는 필수입니다.")
		Long productId,

		@Schema(description = "담을 수량. 이미 담긴 수량에 더해진다", example = "1", minimum = "1", maximum = "99")
		@NotNull(message = "수량은 필수입니다.")
		@Min(value = 1, message = "수량은 1개 이상이어야 합니다.")
		@Max(value = 99, message = "수량은 99개 이하여야 합니다.")
		Integer quantity) {
}
