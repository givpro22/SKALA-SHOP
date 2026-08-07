package shop.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/** 계약 §2.7. */
@Schema(description = "주문 항목")
public record OrderItemRequest(

		@Schema(description = "상품 id. 배열 내 중복 시 400 DUPLICATE_ORDER_ITEM", example = "5")
		@NotNull(message = "상품 id는 필수입니다")
		Long productId,

		@Schema(description = "주문 수량", example = "1", minimum = "1")
		@NotNull(message = "수량은 필수입니다")
		@Min(value = 1, message = "수량은 1 이상이어야 합니다")
		Integer quantity) {
}
