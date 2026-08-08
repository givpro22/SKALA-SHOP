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
 *
 * <p><b>{@code field}가 {@code quantity}인 이유:</b> 이 경로의 원인은 언제나 요청 본문의
 * {@code quantity} 하나이며, DTO 검증({@code @Max})이 잡는 경우와 <b>같은 필드명</b>을 쓴다.
 * 프론트는 단건 초과와 합산 초과를 구분할 필요 없이 같은 입력 칸에 오류를 표시할 수 있다.
 *
 * <p>{@link ValidationException}을 상속하므로 {@code fieldErrors}가 필수다. 이전에는
 * {@code BusinessException}의 2인자 생성자를 골라 <b>{@code VALIDATION_ERROR}인데
 * {@code fieldErrors: null}</b>로 나갔고(§9.5.5 불변식의 반대 방향 위반), 상태코드·코드가 맞아
 * 코드 리뷰로는 잡히지 않았다. 이제 그 생성자가 존재하지 않는다.
 */
public class InvalidCartQuantityException extends ValidationException {

	public InvalidCartQuantityException(int quantity) {
		super("quantity", quantity,
				"장바구니 수량은 1개 이상 %d개 이하여야 합니다. (요청 결과 수량: %d개)"
						.formatted(CartItem.MAX_QUANTITY, quantity));
	}
}
