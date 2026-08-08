package shop.exception;

/**
 * 계약 §9.5 — 라인이 0건인 카트로 체크아웃(검증 2번).
 *
 * <p>빈 카트를 <b>조회</b>하는 것은 정상이다(BR-35). 오류가 되는 것은 그 상태로 결제를 시도할
 * 때뿐이라, 예외는 체크아웃 경로에서만 던져진다.
 */
public class CartEmptyException extends BusinessException {

	public CartEmptyException() {
		super(ErrorCode.CART_EMPTY, "장바구니가 비어 있어 주문할 수 없습니다.");
	}
}
