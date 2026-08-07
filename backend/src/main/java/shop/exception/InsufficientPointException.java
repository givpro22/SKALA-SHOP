package shop.exception;

/** BR-6 — 포인트 부족. 메시지에 보유 포인트·주문 총액을 포함한다. */
public class InsufficientPointException extends BusinessException {

	public InsufficientPointException(int currentPoint, int requiredAmount) {
		super(ErrorCode.INSUFFICIENT_POINT,
				"포인트가 부족합니다. 보유 포인트: %,d원, 주문 총액: %,d원"
						.formatted(currentPoint, requiredAmount));
	}
}
