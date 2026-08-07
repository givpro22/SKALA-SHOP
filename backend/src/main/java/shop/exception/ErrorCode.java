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

	PRODUCT_NOT_FOUND(HttpStatus.NOT_FOUND, "상품을 찾을 수 없습니다."),
	CUSTOMER_NOT_FOUND(HttpStatus.NOT_FOUND, "고객을 찾을 수 없습니다."),
	ORDER_NOT_FOUND(HttpStatus.NOT_FOUND, "주문을 찾을 수 없습니다."),
	ENDPOINT_NOT_FOUND(HttpStatus.NOT_FOUND, "요청하신 경로를 찾을 수 없습니다."),

	METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "지원하지 않는 요청 방식입니다."),

	DUPLICATE_PRODUCT_NAME(HttpStatus.CONFLICT, "이미 존재하는 상품명입니다."),
	DUPLICATE_EMAIL(HttpStatus.CONFLICT, "이미 존재하는 이메일입니다."),
	PRODUCT_IN_USE(HttpStatus.CONFLICT, "주문에 사용된 상품은 삭제할 수 없습니다."),
	CUSTOMER_HAS_ORDERS(HttpStatus.CONFLICT, "주문 이력이 있는 고객은 삭제할 수 없습니다."),
	// 어느 엔티티에서 충돌했는지 노출하지 않는다(계약 §5.1).
	CONCURRENT_UPDATE(HttpStatus.CONFLICT, "요청이 동시에 처리되어 반영되지 않았습니다. 다시 시도해 주세요."),

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
