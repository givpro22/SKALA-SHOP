package shop.exception;

/** BR-3 — 고객 미존재. */
public class CustomerNotFoundException extends EntityNotFoundException {

	public CustomerNotFoundException(Long customerId) {
		super(ErrorCode.CUSTOMER_NOT_FOUND, "고객을 찾을 수 없습니다. (id: %d)".formatted(customerId));
	}
}
