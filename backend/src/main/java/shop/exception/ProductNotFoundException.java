package shop.exception;

/** BR-4 — 상품 미존재. */
public class ProductNotFoundException extends EntityNotFoundException {

	public ProductNotFoundException(Long productId) {
		super(ErrorCode.PRODUCT_NOT_FOUND, "상품을 찾을 수 없습니다. (id: %d)".formatted(productId));
	}
}
