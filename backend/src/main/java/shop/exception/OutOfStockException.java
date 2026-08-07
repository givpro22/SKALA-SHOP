package shop.exception;

/**
 * BR-5 — 재고 부족. 메시지에 상품명·현재 재고·요청 수량을 포함한다.
 *
 * <p>{@code CONCURRENT_UPDATE}와 혼동하지 않는다. 이쪽은 <b>검증 단계</b>에서 재고가 모자란
 * 경우이고, 낙관적 락 충돌은 검증을 모두 통과한 뒤 commit 시점에 발생한다(계약 §5.1).
 */
public class OutOfStockException extends BusinessException {

	public OutOfStockException(String productName, int currentStock, int requestedQuantity) {
		super(ErrorCode.OUT_OF_STOCK,
				"재고가 부족합니다. 상품: %s, 현재 재고: %d개, 요청 수량: %d개"
						.formatted(productName, currentStock, requestedQuantity));
	}
}
