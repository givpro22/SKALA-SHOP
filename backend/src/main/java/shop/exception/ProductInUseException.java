package shop.exception;

/**
 * BR-17 — 주문에 참조된 상품 삭제 시도.
 *
 * <p><b>취소된 주문의 라인도 참조로 센다.</b> 주문 이력은 보존 대상이고, 참조 상품이 사라지면
 * 이력이 깨지기 때문이다(스펙 §2.3).
 */
public class ProductInUseException extends BusinessException {

	public ProductInUseException(Long productId) {
		super(ErrorCode.PRODUCT_IN_USE,
				"주문에 사용된 상품은 삭제할 수 없습니다. (id: %d)".formatted(productId));
	}
}
