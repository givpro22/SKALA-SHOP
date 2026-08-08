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
 * <h2>{@code fieldErrors} 불변식을 여기서 강제한다 (계약 §9.5.5)</h2>
 *
 * <p>불변식은 <b>{@code fieldErrors != null} ⟺ {@code code == "VALIDATION_ERROR"}</b>이며 예외가 없다.
 * 이 클래스는 두 방향을 각각 다른 수단으로 막는다.
 *
 * <table>
 *   <tr><th>방향</th><th>막는 수단</th><th>언제 걸리나</th></tr>
 *   <tr><td>{@code fieldErrors != null} ⟹ {@code VALIDATION_ERROR}</td>
 *       <td>{@code fieldErrors}를 채우는 생성자가 {@code errorCode}를 <b>인자로 받지 않고</b>
 *           {@code VALIDATION_ERROR}로 고정한다. 게터는 {@code final}이라 오버라이드로 우회할 수 없다</td>
 *       <td><b>컴파일</b></td></tr>
 *   <tr><td>{@code VALIDATION_ERROR} ⟹ {@code fieldErrors != null}</td>
 *       <td>일반 생성자가 {@code VALIDATION_ERROR}를 <b>거부</b>한다. 그 코드로 가는 길은
 *           {@link ValidationException}뿐이고, 그 경로는 비어 있지 않은 {@code fieldErrors}를 요구한다</td>
 *       <td><b>생성 즉시</b>(fail-fast)</td></tr>
 * </table>
 *
 * <p><b>두 방향의 차단 시점이 다르다는 것을 분명히 해 둔다.</b> 앞은 컴파일이 막고, 뒤는 컴파일되지만
 * 객체를 만드는 순간 {@link IllegalArgumentException}으로 끊긴다 — 그 경로를 한 번이라도 실행하면
 * 즉시 드러나며, <b>잘못된 응답이 나가는 일은 없다.</b> 뒤쪽을 컴파일 시점으로 올리려면
 * {@code sealed} 계층이 필요한데, 그러면 예외를 추가할 때마다 중앙 목록을 고쳐야 해서
 * 비용이 이득을 넘는다고 판단했다.
 */
public abstract class BusinessException extends RuntimeException {

	private final transient ErrorCode errorCode;
	private final transient List<FieldError> fieldErrors;

	/**
	 * 일반 예외용. <b>{@code VALIDATION_ERROR}를 받지 않는다.</b>
	 *
	 * <p>받으면 {@code fieldErrors}가 {@code null}인 채로 그 코드가 나갈 수 있고, 그것이 라운드 11의
	 * 결함이었다. 그때는 부모에 2인자·3인자 생성자가 함께 있어 <b>어느 쪽을 골라도 컴파일되고
	 * 상태코드·코드까지 맞아</b> 코드 리뷰로 잡히지 않았다.
	 */
	protected BusinessException(ErrorCode errorCode, String message) {
		super(message);
		if (errorCode == ErrorCode.VALIDATION_ERROR) {
			throw new IllegalArgumentException(
					"VALIDATION_ERROR 는 ValidationException 으로만 만든다 (계약 §9.5.5). "
							+ "어느 필드가 틀렸는지 지목하지 않는 검증 실패는 존재할 수 없다: " + message);
		}
		this.errorCode = errorCode;
		this.fieldErrors = null;
	}

	/**
	 * {@link ValidationException} 전용. <b>{@code errorCode}를 인자로 받지 않는다</b> —
	 * {@code fieldErrors}를 싣는 유일한 경로이면서 코드가 {@code VALIDATION_ERROR}로 고정되므로,
	 * 방향 1이 <b>타입 수준에서</b> 성립한다.
	 *
	 * <p>패키지 전용이라 외부에서는 보이지 않고, 같은 패키지에서 {@code BusinessException}을 직접
	 * 상속해 호출하는 경우는 아래 {@code instanceof} 검사가 막는다.
	 */
	BusinessException(String message, List<FieldError> fieldErrors) {
		super(message);
		if (!(this instanceof ValidationException)) {
			throw new IllegalArgumentException(
					"fieldErrors 를 싣는 생성자는 ValidationException 전용이다 (계약 §9.5.5).");
		}
		if (fieldErrors == null || fieldErrors.isEmpty()) {
			throw new IllegalArgumentException(
					"VALIDATION_ERROR 는 어느 필드가 틀렸는지 반드시 지목해야 한다 (계약 §9.5.5). "
							+ "빈 fieldErrors 는 null 보다 나쁘다 — '검증 실패인데 필드를 못 찾았다'로 읽힌다: " + message);
		}
		this.errorCode = ErrorCode.VALIDATION_ERROR;
		this.fieldErrors = List.copyOf(fieldErrors);
	}

	/** {@code final} — 오버라이드로 코드를 바꿔치기하는 우회를 막는다. */
	public final ErrorCode getErrorCode() {
		return errorCode;
	}

	/**
	 * {@code VALIDATION_ERROR}가 아니면 항상 {@code null}이다(계약 §4.1).
	 *
	 * <p>{@code final}이다 — 오버라이드할 수 있으면 다른 코드의 예외가 {@code fieldErrors}를
	 * 반환하게 만들 수 있어 방향 1이 규칙으로만 남는다.
	 */
	public final List<FieldError> getFieldErrors() {
		return fieldErrors;
	}
}
