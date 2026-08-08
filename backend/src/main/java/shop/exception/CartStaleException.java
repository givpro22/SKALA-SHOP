package shop.exception;

/**
 * 계약 §9.5.2 — {@code expectedTotalPrice}가 서버 재계산 총액과 다름(검증 4번).
 *
 * <p><b>낙관적 락을 화면 계층으로 올린 것이다.</b> {@code @Version}은 DB 행의 버전을 검증하고
 * 이 예외는 사용자가 본 화면의 버전을 검증한다 — 원리가 같다. 읽은 시점과 쓰는 시점 사이에
 * 상태가 바뀌었는지를 쓰기 시점에 확인하고, 바뀌었으면 거부한다.
 *
 * <p><b>메시지에 두 금액을 모두 담는다.</b> "금액이 바뀌었다"만으로는 사용자가 얼마가 되었는지
 * 알 수 없어 다시 조회하는 것 말고 할 수 있는 일이 없다. 확인한 금액과 현재 금액을 함께 주면
 * 화면을 새로 읽기 전에도 무슨 일이 있었는지 읽힌다.
 *
 * <p>이 예외가 나가는 시점에 <b>서버 상태는 요청 이전과 완전히 동일하다</b> — 검증 4번은 재고·포인트를
 * 건드리기 전이며, 카트도 손대지 않은 채로 남는다(BR-29).
 */
public class CartStaleException extends BusinessException {

	public CartStaleException(int expectedTotalPrice, int actualTotalPrice) {
		super(ErrorCode.CART_STALE,
				"장바구니 금액이 변경되었습니다. 확인 후 다시 시도해 주세요. (확인하신 금액: %,d원, 현재 금액: %,d원)"
						.formatted(expectedTotalPrice, actualTotalPrice));
	}
}
