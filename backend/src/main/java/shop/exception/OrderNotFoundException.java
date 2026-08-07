package shop.exception;

/** BR-8 — 주문 미존재. */
public class OrderNotFoundException extends EntityNotFoundException {

	public OrderNotFoundException(Long orderId) {
		super(ErrorCode.ORDER_NOT_FOUND, "주문을 찾을 수 없습니다. (id: %d)".formatted(orderId));
	}
}
