package shop.exception;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.Modifier;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import shop.dto.FieldError;

/**
 * 계약 §9.5.5 불변식의 회귀 테스트 — <b>{@code fieldErrors != null} ⟺ {@code code == "VALIDATION_ERROR"}</b>.
 *
 * <h2>왜 테스트로 남기는가</h2>
 *
 * <p>이 불변식은 라운드 11에서 실제로 깨졌고(=&gt; {@code VALIDATION_ERROR} + {@code fieldErrors: null}),
 * <b>어느 생성자를 고르든 컴파일되고 상태코드·에러코드까지 맞아 코드 리뷰로 잡히지 않았다.</b>
 * 라운드 12에서 QA가 우회 경로를 <b>직접 컴파일해</b> 증명했고, 그래서 구조를 다시 조였다.
 *
 * <p><b>구조를 조인 것만으로는 충분하지 않다.</b> 구조는 다음 사람이 되돌릴 수 있고, 되돌렸다는 사실은
 * 응답을 눈으로 봐서는 드러나지 않는다. 이 테스트가 있으면 그 되돌림이 <b>빌드 실패로</b> 나타난다 —
 * QA가 매 라운드 손으로 확인하던 것을 빌드가 대신한다.
 *
 * <p><b>측정 실패가 통과로 읽히지 않게</b> 각 테스트는 "막혔다"를 <b>예외가 실제로 던져지는 것</b>으로
 * 확인한다. 컴파일이 안 되거나 클래스를 못 찾는 상황은 JUnit이 실패로 보고하며, 조용히 통과하지 않는다.
 */
@DisplayName("계약 §9.5.5 — fieldErrors 불변식")
class ValidationInvariantTest {

	/** 방향 2 검증용 — {@code VALIDATION_ERROR}를 일반 생성자로 만들려는 시도. */
	private static final class BypassProbeException extends BusinessException {
		private BypassProbeException() {
			super(ErrorCode.VALIDATION_ERROR, "우회 시도");
		}
	}

	/** {@code fieldErrors} 생성자를 {@code ValidationException} 밖에서 호출하려는 시도. */
	private static final class FieldErrorProbeException extends BusinessException {
		private FieldErrorProbeException() {
			super("우회 시도", List.of(new FieldError("f", "v", "r")));
		}
	}

	private static final class EmptyFieldsException extends ValidationException {
		private EmptyFieldsException(List<FieldError> fieldErrors) {
			super("빈 목록", fieldErrors);
		}
	}

	@Nested
	@DisplayName("방향 2 — VALIDATION_ERROR 이면 fieldErrors 가 반드시 있다")
	class Direction2 {

		@Test
		@DisplayName("일반 생성자로 VALIDATION_ERROR 를 만들 수 없다 (라운드 12 QA 탐침과 같은 형태)")
		void plainConstructorRejectsValidationError() {
			assertThatThrownBy(BypassProbeException::new)
					.isInstanceOf(IllegalArgumentException.class)
					.hasMessageContaining("ValidationException");
		}

		@Test
		@DisplayName("빈 fieldErrors 로는 ValidationException 을 만들 수 없다 — 빈 배열은 null 보다 나쁘다")
		void emptyFieldErrorsRejected() {
			assertThatThrownBy(() -> new EmptyFieldsException(List.of()))
					.isInstanceOf(IllegalArgumentException.class);
		}

		@Test
		@DisplayName("null fieldErrors 로도 만들 수 없다")
		void nullFieldErrorsRejected() {
			assertThatThrownBy(() -> new EmptyFieldsException(null))
					.isInstanceOf(IllegalArgumentException.class);
		}

		@Test
		@DisplayName("실제 검증 예외들은 코드가 VALIDATION_ERROR 이고 fieldErrors 가 비어 있지 않다")
		void realValidationExceptionsCarryFields() {
			List<BusinessException> exceptions = List.of(
					new InvalidCartQuantityException(100),
					new InvalidSearchParameterException("sort", "price,asc", "허용값 아님"));

			assertThat(exceptions).allSatisfy(e -> {
				assertThat(e.getErrorCode()).isEqualTo(ErrorCode.VALIDATION_ERROR);
				assertThat(e.getFieldErrors()).isNotNull().isNotEmpty();
				assertThat(e.getFieldErrors().get(0).field()).isNotBlank();
			});
		}
	}

	@Nested
	@DisplayName("방향 1 — fieldErrors 가 있으면 코드는 반드시 VALIDATION_ERROR 다")
	class Direction1 {

		@Test
		@DisplayName("fieldErrors 생성자는 ValidationException 전용이다")
		void fieldErrorConstructorIsValidationOnly() {
			assertThatThrownBy(FieldErrorProbeException::new)
					.isInstanceOf(IllegalArgumentException.class)
					.hasMessageContaining("ValidationException");
		}

		@Test
		@DisplayName("getErrorCode·getFieldErrors 가 final 이라 오버라이드로 우회할 수 없다")
		void gettersAreFinal() throws NoSuchMethodException {
			assertThat(Modifier.isFinal(BusinessException.class.getMethod("getErrorCode").getModifiers()))
					.as("getErrorCode() 가 final 이 아니면 서브클래스가 코드를 바꿔치기할 수 있다")
					.isTrue();
			assertThat(Modifier.isFinal(BusinessException.class.getMethod("getFieldErrors").getModifiers()))
					.as("getFieldErrors() 가 final 이 아니면 다른 코드의 예외가 fieldErrors 를 반환할 수 있다")
					.isTrue();
		}

		@Test
		@DisplayName("VALIDATION_ERROR 가 아닌 예외들은 fieldErrors 가 null 이다")
		void nonValidationExceptionsHaveNullFieldErrors() {
			List<BusinessException> exceptions = List.of(
					new CartStaleException(84_000, 89_000),
					new CartEmptyException(),
					new OutOfStockException("무선 마우스", 1, 3),
					new InsufficientPointException(10_000, 25_000),
					new CartItemNotFoundException(19L),
					new ShopperProfileConflictException("kim@skala.shop"),
					new ProductNotFoundException(9999L),
					new InvalidOrderStateException(1L),
					new InvalidCredentialsException());

			assertThat(exceptions).allSatisfy(e -> {
				assertThat(e.getErrorCode()).isNotEqualTo(ErrorCode.VALIDATION_ERROR);
				assertThat(e.getFieldErrors())
						.as("%s 가 fieldErrors 를 실으면 프론트는 코드별로 그 의미를 재판정해야 한다",
								e.getClass().getSimpleName())
						.isNull();
			});
		}
	}

	@Test
	@DisplayName("정상 경로는 그대로 동작한다 — 불변식이 기존 예외 생성을 막지 않는다")
	void normalConstructionStillWorks() {
		assertThatCode(() -> new CartStaleException(1, 2)).doesNotThrowAnyException();
		assertThatCode(() -> new InvalidCartQuantityException(0)).doesNotThrowAnyException();
	}
}
