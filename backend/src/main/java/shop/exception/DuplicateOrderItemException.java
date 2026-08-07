package shop.exception;

/**
 * BR-2 — {@code items} 배열에 같은 {@code productId}가 2회 이상.
 *
 * <p><b>라인을 합산하지 않는다.</b> 합산하면 재고 검증 대상 수량이 요청과 달라져 어떤 규칙이
 * 적용됐는지 판정이 모호해진다.
 */
public class DuplicateOrderItemException extends BusinessException {

	public DuplicateOrderItemException(Long productId) {
		super(ErrorCode.DUPLICATE_ORDER_ITEM,
				"주문 항목에 동일한 상품이 중복되었습니다. (productId: %d)".formatted(productId));
	}
}
