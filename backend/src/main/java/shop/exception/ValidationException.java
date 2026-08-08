package shop.exception;

import java.util.List;

import shop.dto.FieldError;

/**
 * 검증 실패 계열의 공통 부모. <b>코드가 {@code VALIDATION_ERROR}로 고정된다.</b> 계약 §9.5.5.
 *
 * <h2>이 클래스가 존재하는 이유는 불변식을 규칙이 아니라 타입으로 만들기 위해서다</h2>
 *
 * <p>계약의 불변식은 <b>{@code fieldErrors != null} ⟺ {@code code == "VALIDATION_ERROR"}</b>이고
 * 예외가 없다. 이전에는 {@link BusinessException}이 {@code fieldErrors} 생성자를 갖고 있어
 * 양방향 모두 <b>규칙으로만</b> 지켜졌고, 실제로 한쪽이 깨졌다 —
 * {@code InvalidCartQuantityException}이 2인자 생성자를 골라 {@code VALIDATION_ERROR}인데
 * {@code fieldErrors: null}로 나갔다. <b>어느 생성자를 고르든 컴파일되고 상태코드·코드도 맞아
 * 코드 리뷰로는 잡히지 않았다.</b>
 *
 * <p>이제 두 방향이 각각 구조로 막힌다.
 *
 * <table>
 *   <tr><th>방향</th><th>무엇이 막나</th></tr>
 *   <tr><td>{@code fieldErrors != null} ⟹ {@code VALIDATION_ERROR}</td>
 *       <td>{@code BusinessException}에 그 생성자가 <b>없다.</b> {@code CartStaleException} 등은
 *           실을 방법 자체가 없다</td></tr>
 *   <tr><td>{@code VALIDATION_ERROR} ⟹ {@code fieldErrors != null}</td>
 *       <td>이 클래스의 생성자가 {@code fieldErrors}를 <b>필수 인자</b>로 받고, 비어 있으면
 *           기동/호출 즉시 {@link IllegalArgumentException}으로 끊는다</td></tr>
 * </table>
 *
 * <p><b>빈 배열을 허용하지 않는 것이 핵심이다.</b> 허용하면 "검증 실패인데 필드를 못 찾았다"는
 * 응답이 나가는데, 그것은 {@code null}보다 나쁘고 §9.2.6이 {@code TYPE_MISMATCH}를 기각한
 * 바로 그 상태다. 여기서 끊으면 그 응답이 만들어질 수 없다.
 *
 * <p>이 구조의 <b>의도</b>는 불변식을 사람이 매 라운드 확인하지 않아도 되게 만드는 것이다 —
 * 구조가 막던 것을 규칙이 막게 되면 그 규칙은 누군가 매번 확인해야 하고, 그 누군가를 없애는 것이
 * 이 클래스의 목적이다.
 *
 * <p><b>다만 그 판단은 이 코드가 내리는 것이 아니다.</b> 의도대로 되었는지는 QA가 실제로 쳐 본 뒤
 * 확정하며, 확인 항목을 유지할지 내릴지도 QA가 정한다. 여기 적힌 것은 설계 의도이지 검증 결과가 아니다.
 */
public abstract class ValidationException extends BusinessException {

	private final transient List<FieldError> fieldErrors;

	protected ValidationException(String message, List<FieldError> fieldErrors) {
		super(ErrorCode.VALIDATION_ERROR, message);
		if (fieldErrors == null || fieldErrors.isEmpty()) {
			throw new IllegalArgumentException(
					"VALIDATION_ERROR 는 어느 필드가 틀렸는지 반드시 지목해야 한다 (계약 §9.5.5). "
							+ "fieldErrors 가 비어 있다: " + message);
		}
		this.fieldErrors = List.copyOf(fieldErrors);
	}

	/** 필드 하나를 지목하는 흔한 경우의 축약. */
	protected ValidationException(String field, Object rejectedValue, String reason) {
		this(ErrorCode.VALIDATION_ERROR.defaultMessage(),
				List.of(new FieldError(field,
						rejectedValue == null ? null : String.valueOf(rejectedValue),
						reason)));
	}

	@Override
	public List<FieldError> getFieldErrors() {
		return fieldErrors;
	}
}
