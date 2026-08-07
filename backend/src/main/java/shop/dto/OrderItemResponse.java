package shop.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import shop.domain.OrderItem;

/** 계약 §3.4. */
@Schema(description = "주문 항목 응답")
public record OrderItemResponse(

		@Schema(description = "주문 항목 id", example = "3")
		Long orderItemId,

		@Schema(description = "상품 id", example = "5")
		Long productId,

		@Schema(description = "현재 상품명. 주문 시점 상품명이 아니라 연관 엔티티에서 읽은 값이다",
				example = "노이즈캔슬링 헤드폰")
		String productName,

		@Schema(description = "주문 수량", example = "1")
		int quantity,

		@Schema(description = "주문 시점 단가 스냅샷. 현재 product.price와 다를 수 있다", example = "210000")
		int orderPrice,

		@Schema(description = "orderPrice × quantity. 응답 생성 시 계산하며 DB에 저장하지 않는다",
				example = "210000")
		int subtotal) {

	public static OrderItemResponse from(OrderItem item) {
		return new OrderItemResponse(
				item.getId(),
				item.getProduct().getId(),
				item.getProduct().getName(),
				item.getQuantity(),
				item.getOrderPrice(),
				item.getSubtotal());
	}
}
