package shop.domain;

/**
 * 주문 상태. 도메인 스펙 §1.5.
 *
 * <p>DB에는 {@code @Enumerated(EnumType.STRING)}으로 문자열 저장한다. ordinal 저장은 값이
 * 추가되면 기존 행의 의미가 바뀌므로 쓰지 않는다.
 */
public enum OrderStatus {
	/** 주문 완료. 재고 차감·포인트 차감이 반영된 상태. */
	ORDERED,
	/** 주문 취소. 재고 복원·포인트 환급이 반영된 상태. */
	CANCELED
}
