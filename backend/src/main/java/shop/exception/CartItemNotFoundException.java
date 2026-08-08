package shop.exception;

/** BR-34 — 내 카트에 없는 {@code productId}로 수량 변경·라인 삭제. */
public class CartItemNotFoundException extends EntityNotFoundException {

	public CartItemNotFoundException(Long productId) {
		super(ErrorCode.CART_ITEM_NOT_FOUND,
				"장바구니에 없는 상품입니다. (상품 id: %d)".formatted(productId));
	}
}
