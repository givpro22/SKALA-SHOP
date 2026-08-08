package shop.exception;

import java.util.List;

import shop.dto.FieldError;

/**
 * 계약 §9.2.1 · §9.2.6 — 상품 검색 쿼리 파라미터가 허용 집합·범위를 벗어남.
 *
 * <p><b>판정 규칙(§9.2.6):</b> 선언된 타입으로 값을 <b>읽을 수조차 없으면</b>
 * {@code TYPE_MISMATCH}({@code page=abc}), 읽히는데 <b>집합·범위 밖이면</b>
 * {@code VALIDATION_ERROR}({@code sort=price,asc}, {@code size=51})다. 이 예외는 후자 전용이다.
 *
 * <p><b>{@code fieldErrors}를 반드시 싣는다.</b> {@code TYPE_MISMATCH}는 {@code fieldErrors: null}
 * 이라 세 파라미터 중 무엇이 틀렸는지 알려주지 못한다. 허용값이 셋뿐인 {@code sort}는 사용자가
 * 오타를 낼 가능성이 가장 높은 자리인데 거기서 {@code null}을 주면 프론트가 안내할 것이 없다 —
 * {@code sort}를 enum 으로 바인딩하지 않고 {@code String}으로 받아 여기서 검증하는 이유가 이것이다.
 *
 * <p><b>{@code size} 상한을 넘긴 요청을 조용히 50으로 자르지 않고 거부한다.</b> 자르면 클라이언트는
 * 자기가 요청한 만큼 받았다고 믿는다. 상한이 아예 없으면 {@code size=100000} 한 번으로 전체
 * 테이블이 직렬화된다. 반면 <b>범위를 벗어난 {@code page}는 오류가 아니다</b> — 200 + 빈 배열이다.
 */
public class InvalidSearchParameterException extends BusinessException {

	public InvalidSearchParameterException(String field, Object rejectedValue, String reason) {
		super(ErrorCode.VALIDATION_ERROR,
				ErrorCode.VALIDATION_ERROR.defaultMessage(),
				List.of(new FieldError(field,
						rejectedValue == null ? null : String.valueOf(rejectedValue),
						reason)));
	}
}
