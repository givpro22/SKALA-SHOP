package shop.exception;

/**
 * BR-9 — 이미 취소된 주문에 취소를 재요청.
 *
 * <p>허용하면 환급이 두 번 일어나 포인트가 무한 증식한다. 상태 전이표(§3)에 없는 전이다.
 */
public class InvalidOrderStateException extends BusinessException {

	public InvalidOrderStateException(Long orderId) {
		super(ErrorCode.ALREADY_CANCELED, "이미 취소된 주문입니다. (id: %d)".formatted(orderId));
	}
}
