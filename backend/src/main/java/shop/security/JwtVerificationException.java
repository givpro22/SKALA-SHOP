package shop.security;

import shop.exception.ErrorCode;

/**
 * 토큰 검증 실패. {@code BusinessException} 을 상속하지 <b>않는다.</b>
 *
 * <p>이 예외는 필터 체인에서 발생하므로 {@code @RestControllerAdvice} 가 잡지 못한다.
 * DispatcherServlet 에 도달하기 전이기 때문이다. 전역 핸들러가 잡는 계층에 넣어 두면
 * "핸들러가 처리한다"고 착각하게 되므로 계층을 분리했다 —
 * 실제 응답은 {@link JwtAuthenticationFilter} 가 직접 쓴다.
 */
public class JwtVerificationException extends RuntimeException {

	private final transient ErrorCode errorCode;

	public JwtVerificationException(ErrorCode errorCode) {
		super(errorCode.defaultMessage());
		this.errorCode = errorCode;
	}

	public ErrorCode getErrorCode() {
		return errorCode;
	}
}
