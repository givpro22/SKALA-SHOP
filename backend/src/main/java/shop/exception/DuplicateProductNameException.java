package shop.exception;

/** BR-16 — 상품명 중복. 수정 시 자기 자신은 중복으로 보지 않는다. */
public class DuplicateProductNameException extends BusinessException {

	public DuplicateProductNameException(String name) {
		super(ErrorCode.DUPLICATE_PRODUCT_NAME, "이미 존재하는 상품명입니다. (name: %s)".formatted(name));
	}
}
