package shop.exception;

import java.util.List;

import shop.dto.FieldError;

/**
 * 검증 실패 계열의 공통 부모. <b>코드가 {@code VALIDATION_ERROR}로 고정된다.</b> 계약 §9.5.5.
 *
 * <h2>이 클래스가 존재하는 이유는 불변식을 규칙이 아니라 타입으로 만들기 위해서다</h2>
 *
 * <p>이전에는 {@link BusinessException}이 {@code fieldErrors} 생성자를 함께 갖고 있어 양방향이
 * <b>규칙으로만</b> 지켜졌고, 실제로 한쪽이 깨졌다 — {@code InvalidCartQuantityException}이 2인자
 * 생성자를 골라 {@code VALIDATION_ERROR}인데 {@code fieldErrors: null}로 나갔다. <b>어느 생성자를
 * 고르든 컴파일되고 상태코드·코드도 맞아 코드 리뷰로는 잡히지 않았다.</b>
 *
 * <p>지금은 {@code fieldErrors}를 싣는 생성자가 {@code errorCode}를 <b>인자로 받지 않고</b>
 * 부모의 게터가 {@code final}이라, "다른 코드의 예외가 {@code fieldErrors}를 갖는" 상태는
 * <b>컴파일되지 않는다.</b> 반대 방향은 부모의 일반 생성자가 {@code VALIDATION_ERROR}를 거부해
 * <b>생성 즉시</b> 끊긴다.
 *
 * <p><b>빈 배열을 허용하지 않는 것이 핵심이다.</b> 허용하면 "검증 실패인데 필드를 못 찾았다"는
 * 응답이 나가는데, 그것은 {@code null}보다 나쁘고 §9.2.6이 {@code TYPE_MISMATCH}를 기각한
 * 바로 그 상태다. 여기서 끊으면 그 응답이 만들어질 수 없다.
 *
 * <p>이 구조의 <b>의도</b>는 불변식을 사람이 매 라운드 확인하지 않아도 되게 만드는 것이다.
 * <b>다만 그 판단은 이 코드가 내리는 것이 아니다</b> — 의도대로 되었는지는 QA가 실제로 쳐 본 뒤
 * 확정하며, 확인 항목을 유지할지 내릴지도 QA가 정한다. 여기 적힌 것은 설계 의도이지 검증 결과가 아니다.
 */
public abstract class ValidationException extends BusinessException {

	protected ValidationException(String message, List<FieldError> fieldErrors) {
		super(message, fieldErrors);
	}

	/** 필드 하나를 지목하는 흔한 경우의 축약. */
	protected ValidationException(String field, Object rejectedValue, String reason) {
		this(ErrorCode.VALIDATION_ERROR.defaultMessage(),
				List.of(new FieldError(field,
						rejectedValue == null ? null : String.valueOf(rejectedValue),
						reason)));
	}
}
