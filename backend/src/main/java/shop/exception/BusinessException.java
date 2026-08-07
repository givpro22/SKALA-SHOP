package shop.exception;

/**
 * 비즈니스 규칙 위반의 공통 부모. 도메인 스펙 §5의 예외 계층 루트다.
 *
 * <p>{@link ErrorCode}를 들고 있으므로 전역 핸들러가 이 타입 하나만 잡아 계층 전체의
 * 상태코드와 본문을 만들 수 있다.
 *
 * <p>낙관적 락 충돌({@code CONCURRENT_UPDATE})은 <b>이 계층에 넣지 않는다</b>(스펙 §5.1).
 * 그 예외는 서비스 메서드가 반환된 뒤 commit 시점에 발생할 수 있어 감쌀 코드가 남아 있지 않다.
 * Spring의 {@code OptimisticLockingFailureException}을 전역 핸들러에서 직접 잡는다.
 */
public abstract class BusinessException extends RuntimeException {

	private final transient ErrorCode errorCode;

	protected BusinessException(ErrorCode errorCode, String message) {
		super(message);
		this.errorCode = errorCode;
	}

	public ErrorCode getErrorCode() {
		return errorCode;
	}
}
