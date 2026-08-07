package shop.exception;

/** BR-15 — 이메일 중복. 수정 시 자기 자신은 중복으로 보지 않는다. */
public class DuplicateEmailException extends BusinessException {

	public DuplicateEmailException(String email) {
		super(ErrorCode.DUPLICATE_EMAIL, "이미 존재하는 이메일입니다. (email: %s)".formatted(email));
	}
}
