package shop.exception;

import shop.domain.CartItem;

/**
 * BR-24 — 카트 라인 수량이 {@code 1 ~ 99} 범위를 벗어남.
 *
 * <p>요청 DTO의 {@code @Min(1) @Max(99)}가 <b>단건 요청</b>은 막지만, 이미 담긴 수량과의
 * <b>합산 결과</b>는 막지 못한다 — 90개가 담긴 상품에 20개를 더 담는 요청은 형식상 유효하다.
 * 그 판정은 카트 상태를 아는 엔티티만 할 수 있어 여기서 던진다.
 *
 * <p>상한을 넘긴 요청을 <b>99로 잘라내지 않고 거부한다.</b> 조용히 잘라내면 사용자가 보낸 것과
 * 다른 결과가 저장되고 화면은 성공으로 보인다.
 */
public class InvalidCartQuantityException extends BusinessException {

	public InvalidCartQuantityException(int quantity) {
		super(ErrorCode.VALIDATION_ERROR,
				"장바구니 수량은 1개 이상 %d개 이하여야 합니다. (요청 결과 수량: %d개)"
						.formatted(CartItem.MAX_QUANTITY, quantity));
	}
}
