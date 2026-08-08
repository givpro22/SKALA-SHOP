package shop.domain;

/**
 * 카트 라인의 구매 가능 여부. 도메인 스펙 §9.5.
 *
 * <p><b>DB 컬럼이 아니다.</b> 조회 시점마다 재계산한다 — 재고는 다른 주문으로 계속 바뀌므로,
 * 저장하면 저장된 순간부터 거짓이 되고 갱신하려면 재고가 바뀔 때마다 모든 카트를 훑어야 한다.
 *
 * <p>{@link #INSUFFICIENT_STOCK}과 {@link #SOLD_OUT}을 나눈 것은 <b>화면 문구가 다르기 때문</b>이다.
 * 전자는 "수량을 줄이면 살 수 있다", 후자는 "지금은 살 수 없다"이며 사용자가 취할 행동이 정반대다.
 * 서버가 나누지 않으면 프론트가 {@code stock == 0}을 다시 판정해야 하고, 그 판정이 서버와 어긋날 수 있다.
 */
public enum CartItemAvailability {

	/** {@code product.stock >= quantity}. 체크아웃 통과. */
	AVAILABLE,

	/** {@code 0 < product.stock < quantity}. 체크아웃 시 400 {@code OUT_OF_STOCK}. */
	INSUFFICIENT_STOCK,

	/** {@code product.stock == 0}. 체크아웃 시 400 {@code OUT_OF_STOCK}. */
	SOLD_OUT;

	/** 스펙 §9.5의 판정식 하나뿐이다. 이 메서드 밖에서 재고를 다시 판정하지 않는다. */
	public static CartItemAvailability of(int stock, int quantity) {
		if (stock == 0) {
			return SOLD_OUT;
		}
		return stock >= quantity ? AVAILABLE : INSUFFICIENT_STOCK;
	}
}
