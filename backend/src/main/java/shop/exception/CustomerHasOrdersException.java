package shop.exception;

/**
 * BR-18 — 주문 이력이 있는 고객 삭제 시도.
 *
 * <p><b>취소된 주문도 이력으로 센다</b>(스펙 §2.3).
 */
public class CustomerHasOrdersException extends BusinessException {

	public CustomerHasOrdersException(Long customerId) {
		super(ErrorCode.CUSTOMER_HAS_ORDERS,
				"주문 이력이 있는 고객은 삭제할 수 없습니다. (id: %d)".formatted(customerId));
	}
}
