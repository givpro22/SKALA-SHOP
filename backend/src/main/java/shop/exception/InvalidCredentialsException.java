package shop.exception;

/**
 * 로그인 실패. 계약 §8.4.
 *
 * <p>메시지에 아이디를 담지 않는다. 다른 예외들은 디버깅을 위해 식별자를 넣지만
 * 여기서는 그 값이 그대로 응답으로 나가 "존재하는 아이디"를 알려주게 된다.
 */
public class InvalidCredentialsException extends BusinessException {

	public InvalidCredentialsException() {
		super(ErrorCode.INVALID_CREDENTIALS, ErrorCode.INVALID_CREDENTIALS.defaultMessage());
	}
}
