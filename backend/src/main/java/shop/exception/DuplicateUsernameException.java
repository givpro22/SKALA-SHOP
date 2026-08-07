package shop.exception;

/** 계정 아이디 중복. 계약 §8.4. */
public class DuplicateUsernameException extends BusinessException {

	public DuplicateUsernameException(String username) {
		super(ErrorCode.DUPLICATE_USERNAME, "이미 존재하는 계정 아이디입니다. (username: %s)".formatted(username));
	}
}
