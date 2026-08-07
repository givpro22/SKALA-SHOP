package shop.dto;

import java.time.LocalDateTime;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonFormat;

import io.swagger.v3.oas.annotations.media.Schema;
import shop.domain.Order;
import shop.domain.OrderStatus;

/** 계약 §3.3. */
@Schema(description = "주문 응답")
public record OrderResponse(

		@Schema(description = "주문 id", example = "3")
		Long orderId,

		@Schema(description = "주문 고객 id", example = "4")
		Long customerId,

		@Schema(description = "주문 고객명. 목록 화면에서 고객을 재조회하지 않도록 함께 내린다",
				example = "최구매")
		String customerName,

		@Schema(description = "주문 항목 배열. 최소 1건")
		List<OrderItemResponse> items,

		@Schema(description = "주문 시점에 확정 저장된 총액(BR-11)", example = "210000")
		int totalPrice,

		@Schema(description = "주문 상태. 대문자 문자열이며 숫자가 아니다", example = "ORDERED",
				allowableValues = { "ORDERED", "CANCELED" })
		OrderStatus status,

		@Schema(description = "주문 시각", example = "2026-08-07T16:05:00")
		@JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss")
		LocalDateTime orderedAt,

		@Schema(description = "취소 시각. ORDERED면 null", example = "null", nullable = true)
		@JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss")
		LocalDateTime canceledAt) {

	public static OrderResponse from(Order order) {
		return new OrderResponse(
				order.getId(),
				order.getCustomer().getId(),
				order.getCustomer().getName(),
				order.getOrderItems().stream().map(OrderItemResponse::from).toList(),
				order.getTotalPrice(),
				order.getStatus(),
				order.getOrderedAt(),
				order.getCanceledAt());
	}
}
