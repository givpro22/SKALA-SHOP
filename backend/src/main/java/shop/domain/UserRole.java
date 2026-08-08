package shop.domain;

/**
 * 계정 역할. 도메인 스펙 §9.4.
 *
 * <p>역할이 둘이고 경계가 <b>경로로</b> 갈라져 있으므로({@code /api/shop/**} vs 나머지)
 * 권한 테이블·리소스별 ACL 없이 필드 하나로 표현된다. 확장 가능성을 위해 구조를 미리 만드는 것은
 * 지금 없는 요구에 지금 비용을 치르는 것이다.
 *
 * <p>{@code @Enumerated(EnumType.STRING)}으로 저장한다 — ordinal 저장은 값이 추가되면 기존 행의
 * 의미가 바뀐다({@link OrderStatus}와 같은 판단).
 */
public enum UserRole {

	/** 운영자. 상품·고객·관리자 주문 API를 쓴다. 구매하지 않는다. */
	ADMIN,

	/** 구매자. {@code /api/shop/**}를 쓴다. 상품·고객을 수정하지 않는다. */
	SHOPPER
}
