package shop.exception;

import org.springframework.http.HttpStatus;

/**
 * API 계약 §5 에러 코드표. 코드 문자열과 HTTP 상태를 한 곳에 묶는다.
 *
 * <p>{@link BusinessException}이 이 enum을 들고 있으므로 전역 핸들러는 예외 하나만 잡아
 * 상태코드와 응답 본문을 만들 수 있다. 예외 클래스마다 핸들러를 추가하지 않는다.
 *
 * <p>코드 문자열은 enum 이름 그대로다({@link #code()}). 표에 없는 코드를 임의로 추가하지 않는다.
 */
public enum ErrorCode {

	VALIDATION_ERROR(HttpStatus.BAD_REQUEST, "요청 값이 올바르지 않습니다."),
	MALFORMED_REQUEST(HttpStatus.BAD_REQUEST, "요청 본문을 해석할 수 없습니다."),
	TYPE_MISMATCH(HttpStatus.BAD_REQUEST, "요청 값의 형식이 올바르지 않습니다."),
	OUT_OF_STOCK(HttpStatus.BAD_REQUEST, "재고가 부족합니다."),
	INSUFFICIENT_POINT(HttpStatus.BAD_REQUEST, "포인트가 부족합니다."),
	ALREADY_CANCELED(HttpStatus.BAD_REQUEST, "이미 취소된 주문입니다."),
	DUPLICATE_ORDER_ITEM(HttpStatus.BAD_REQUEST, "주문 항목에 동일한 상품이 중복되었습니다."),
	// 계약 §9.5. 라인이 0건인 카트로 체크아웃한 경우.
	CART_EMPTY(HttpStatus.BAD_REQUEST, "장바구니가 비어 있습니다."),

	PRODUCT_NOT_FOUND(HttpStatus.NOT_FOUND, "상품을 찾을 수 없습니다."),
	CUSTOMER_NOT_FOUND(HttpStatus.NOT_FOUND, "고객을 찾을 수 없습니다."),
	ORDER_NOT_FOUND(HttpStatus.NOT_FOUND, "주문을 찾을 수 없습니다."),
	CART_ITEM_NOT_FOUND(HttpStatus.NOT_FOUND, "장바구니에 없는 상품입니다."),
	ENDPOINT_NOT_FOUND(HttpStatus.NOT_FOUND, "요청하신 경로를 찾을 수 없습니다."),

	METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "지원하지 않는 요청 방식입니다."),

	/*
	 * 인증 실패 셋(계약 §8.4).
	 *
	 * INVALID_CREDENTIALS 는 아이디가 없을 때와 비밀번호가 틀렸을 때를 **구분하지 않는다.**
	 * 구분하면 "이 아이디는 존재한다"가 응답으로 새어 나가 계정 열거에 쓰인다.
	 */
	INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "아이디 또는 비밀번호가 올바르지 않습니다."),
	UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "로그인이 필요합니다."),
	TOKEN_EXPIRED(HttpStatus.UNAUTHORIZED, "로그인이 만료되었습니다. 다시 로그인해 주세요."),

	/*
	 * 계약 §9.4.3 — 401과 다르다. 토큰은 유효하고 역할이 맞지 않는 경우다.
	 *
	 * 401로 뭉뚱그리면 구매자가 관리 URL을 눌렀을 때 로그인 화면으로 튕기고, 다시 로그인해도
	 * 같은 일이 반복된다. 사용자는 자기 비밀번호를 의심한다 — 사용자가 취할 행동이 다르면
	 * 코드도 달라야 한다(만료와 위조를 나눈 것과 같은 판단).
	 */
	FORBIDDEN(HttpStatus.FORBIDDEN, "이 작업을 수행할 권한이 없습니다."),

	DUPLICATE_PRODUCT_NAME(HttpStatus.CONFLICT, "이미 존재하는 상품명입니다."),
	DUPLICATE_EMAIL(HttpStatus.CONFLICT, "이미 존재하는 이메일입니다."),
	DUPLICATE_USERNAME(HttpStatus.CONFLICT, "이미 존재하는 계정 아이디입니다."),
	PRODUCT_IN_USE(HttpStatus.CONFLICT, "주문에 사용된 상품은 삭제할 수 없습니다."),
	CUSTOMER_HAS_ORDERS(HttpStatus.CONFLICT, "주문 이력이 있는 고객은 삭제할 수 없습니다."),
	// 어느 엔티티에서 충돌했는지 노출하지 않는다(계약 §5.1).
	CONCURRENT_UPDATE(HttpStatus.CONFLICT, "요청이 동시에 처리되어 반영되지 않았습니다. 다시 시도해 주세요."),

	/*
	 * 계약 §9.5.2 — CONCURRENT_UPDATE 와 같은 409지만 다른 코드다. 프론트는 code 로 분기해야 한다.
	 *
	 * CONCURRENT_UPDATE 는 DB 행의 version 이 바뀐 것이라 "재시도"가 답이지만, CART_STALE 은
	 * 사용자가 본 총액이 바뀐 것이라 **같은 expectedTotalPrice 로 재시도하면 반드시 다시 실패한다.**
	 * 재시도 버튼을 달면 무한히 실패한다 — 반드시 카트를 다시 읽어야 한다.
	 *
	 * 400 이 아닌 이유(§9.5.3): 클라이언트가 잘못 보낸 것이 아니다. 보낼 때는 맞았고 그 사이
	 * 서버 상태가 바뀌었다. 400 을 주면 사용자는 자기가 뭘 잘못 입력했는지 찾게 되는데 잘못
	 * 입력한 것이 없다.
	 */
	CART_STALE(HttpStatus.CONFLICT, "장바구니 금액이 변경되었습니다. 확인 후 다시 시도해 주세요."),
	SHOPPER_PROFILE_CONFLICT(HttpStatus.CONFLICT, "이미 사용 중인 이메일이라 구매자 정보를 만들 수 없습니다."),

	// 스택트레이스·예외 메시지를 담지 않는다. 원문은 서버 로그에 ERROR로 남긴다.
	INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "서버 내부 오류가 발생했습니다.");

	private final HttpStatus status;
	private final String defaultMessage;

	ErrorCode(HttpStatus status, String defaultMessage) {
		this.status = status;
		this.defaultMessage = defaultMessage;
	}

	public String code() {
		return name();
	}

	public HttpStatus status() {
		return status;
	}

	public String defaultMessage() {
		return defaultMessage;
	}
}
