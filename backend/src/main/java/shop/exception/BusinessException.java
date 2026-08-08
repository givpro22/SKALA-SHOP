package shop.exception;

import java.util.List;

import shop.dto.FieldError;

/**
 * 비즈니스 규칙 위반의 공통 부모. 도메인 스펙 §5의 예외 계층 루트다.
 *
 * <p>{@link ErrorCode}를 들고 있으므로 전역 핸들러가 이 타입 하나만 잡아 계층 전체의
 * 상태코드와 본문을 만들 수 있다.
 *
 * <p>낙관적 락 충돌({@code CONCURRENT_UPDATE})은 <b>이 계층에 넣지 않는다</b>(스펙 §5.1).
 * 그 예외는 서비스 메서드가 반환된 뒤 commit 시점에 발생할 수 있어 감쌀 코드가 남아 있지 않다.
 * Spring의 {@code OptimisticLockingFailureException}을 전역 핸들러에서 직접 잡는다.
 *
 * <p><b>{@code fieldErrors}는 기본적으로 {@code null}이다</b>(계약 §4.1 — {@code VALIDATION_ERROR}일
 * 때만 채워진다). 어느 필드가 틀렸는지 지목할 수 있는 예외만 두 번째 생성자로 값을 싣는다 —
 * 계약 §9.2.6이 요구하는 쿼리 파라미터 오류가 그 경우다. 필드가 특정되지 않는 실패
 * ({@code OUT_OF_STOCK} 등)에 빈 배열을 넣으면 "검증 실패인데 필드를 못 찾았다"로 읽혀 더 나쁘다.
 */
public abstract class BusinessException extends RuntimeException {

	private final transient ErrorCode errorCode;
	private final transient List<FieldError> fieldErrors;

	protected BusinessException(ErrorCode errorCode, String message) {
		this(errorCode, message, null);
	}

	protected BusinessException(ErrorCode errorCode, String message, List<FieldError> fieldErrors) {
		super(message);
		this.errorCode = errorCode;
		this.fieldErrors = fieldErrors;
	}

	public ErrorCode getErrorCode() {
		return errorCode;
	}

	/** 없으면 {@code null}. 전역 핸들러가 그대로 응답에 싣는다. */
	public List<FieldError> getFieldErrors() {
		return fieldErrors;
	}
}
