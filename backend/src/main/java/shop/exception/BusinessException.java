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
 * <h2>{@code fieldErrors}를 여기서 실을 수 없게 한 이유 (계약 §9.5.5)</h2>
 *
 * <p>계약의 불변식은 <b>{@code fieldErrors != null} ⟺ {@code code == "VALIDATION_ERROR"}</b>이며
 * 예외가 없다. 처음에는 이 클래스에 {@code fieldErrors}를 받는 생성자를 두었는데, 그러면
 * {@code CartStaleException}·{@code OutOfStockException} 같은 <b>다른 코드의 예외도 생성자 인자
 * 하나만 바꾸면 불변식을 깰 수 있다.</b> 규칙은 남고 그것을 막던 구조가 사라진 상태였다.
 *
 * <p>그래서 {@code fieldErrors}를 나르는 능력을 {@link ValidationException} 한 곳으로 옮겼다.
 * 그 클래스는 코드가 {@code VALIDATION_ERROR}로 <b>고정</b>되어 있으므로, 이제 불변식은 규칙이
 * 아니라 <b>타입으로 성립한다</b> — 다른 코드의 예외는 {@code fieldErrors}를 실을 방법 자체가 없다.
 * (계약 §9.5.5의 "구조 권고"를 채택한 것이며, 계약이 강제하는 것은 관측 결과다.)
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

	/**
	 * 기본은 {@code null}이다(계약 §4.1 — {@code VALIDATION_ERROR}일 때만 채워진다).
	 *
	 * <p>{@link ValidationException}만 이 값을 덮어쓴다. <b>빈 배열을 반환하지 않는다</b> —
	 * "검증 실패인데 필드를 못 찾았다"로 읽혀 {@code null}보다 나쁘다.
	 */
	public List<FieldError> getFieldErrors() {
		return null;
	}
}
