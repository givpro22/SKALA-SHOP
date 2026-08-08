package shop.exception;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import shop.dto.ErrorResponse;
import shop.dto.FieldError;

/**
 * 전역 예외 처리. 계약 §5 에러 코드표와 1:1로 대응한다.
 *
 * <p>모든 실패 응답이 {@code {code, message, timestamp, path, fieldErrors}} 한 가지 형태로
 * 나가야 프론트가 한 곳에서 파싱한다.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

	private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

	/**
	 * 도메인 예외 전체를 하나로 처리한다. {@link BusinessException}이 {@link ErrorCode}를 들고
	 * 있으므로 예외가 늘어도 핸들러를 추가할 필요가 없다.
	 */
	@ExceptionHandler(BusinessException.class)
	public ResponseEntity<ErrorResponse> handleBusiness(BusinessException e, HttpServletRequest request) {
		ErrorCode code = e.getErrorCode();
		/*
		 * fieldErrors 는 대부분 null 이고, 어느 필드가 틀렸는지 지목할 수 있는 예외만 값을 싣는다
		 * (계약 §9.2.6 의 쿼리 파라미터 오류). null 을 그대로 넘기면 키는 남고 값만 null 이 되어
		 * 계약 §0.4("값이 없어도 키는 생략하지 않는다")를 지킨다.
		 */
		return ResponseEntity.status(code.status())
				.body(ErrorResponse.of(code, e.getMessage(), request.getRequestURI(), e.getFieldErrors()));
	}

	/**
	 * 낙관적 락 충돌 → 409 {@code CONCURRENT_UPDATE}.
	 *
	 * <p><b>Spring의 {@code OptimisticLockingFailureException}을 잡는다.</b>
	 * {@code jakarta.persistence.OptimisticLockException}을 잡으면 안 된다 —
	 * {@code JpaTransactionManager}가 commit 실패를 Spring 타입으로 번역해 던지므로,
	 * JPA 쪽을 잡으면 놓쳐서 500으로 샌다(스펙 §5.1).
	 *
	 * <p>충돌이 난 엔티티를 메시지에 노출하지 않는다(계약 §5.1). 이 시점에는 트랜잭션 전체가
	 * 이미 롤백되어 재고·포인트·주문 어느 것도 반영되지 않은 상태다(BR-19·BR-20).
	 */
	@ExceptionHandler(OptimisticLockingFailureException.class)
	public ResponseEntity<ErrorResponse> handleOptimisticLock(OptimisticLockingFailureException e,
			HttpServletRequest request) {
		log.warn("낙관적 락 충돌 - path={}, message={}", request.getRequestURI(), e.getMessage());
		ErrorCode code = ErrorCode.CONCURRENT_UPDATE;
		return ResponseEntity.status(code.status())
				.body(ErrorResponse.of(code, code.defaultMessage(), request.getRequestURI()));
	}

	/** {@code @Valid @RequestBody} 검증 실패. 필드 오류를 모아 내린다. */
	@ExceptionHandler(MethodArgumentNotValidException.class)
	public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException e,
			HttpServletRequest request) {
		List<FieldError> fieldErrors = e.getBindingResult().getFieldErrors().stream()
				.map(fe -> new FieldError(
						fe.getField(),
						fe.getRejectedValue() == null ? null : String.valueOf(fe.getRejectedValue()),
						fe.getDefaultMessage()))
				.toList();
		ErrorCode code = ErrorCode.VALIDATION_ERROR;
		return ResponseEntity.status(code.status())
				.body(ErrorResponse.of(code, code.defaultMessage(), request.getRequestURI(), fieldErrors));
	}

	/**
	 * 계약상 <b>선택</b> 항목이다. 이 프로젝트의 검증은 전부 {@code @Valid @RequestBody}라
	 * 발생 경로가 없지만, 나중에 {@code @Validated} 파라미터 검증이 추가돼도 500으로 새지 않도록 둔다.
	 */
	@ExceptionHandler(ConstraintViolationException.class)
	public ResponseEntity<ErrorResponse> handleConstraintViolation(ConstraintViolationException e,
			HttpServletRequest request) {
		List<FieldError> fieldErrors = e.getConstraintViolations().stream()
				.map(v -> new FieldError(
						String.valueOf(v.getPropertyPath()),
						v.getInvalidValue() == null ? null : String.valueOf(v.getInvalidValue()),
						v.getMessage()))
				.toList();
		ErrorCode code = ErrorCode.VALIDATION_ERROR;
		return ResponseEntity.status(code.status())
				.body(ErrorResponse.of(code, code.defaultMessage(), request.getRequestURI(), fieldErrors));
	}

	/** JSON 파싱 불가 / 본문 타입 불일치. */
	@ExceptionHandler(HttpMessageNotReadableException.class)
	public ResponseEntity<ErrorResponse> handleMalformed(HttpMessageNotReadableException e,
			HttpServletRequest request) {
		ErrorCode code = ErrorCode.MALFORMED_REQUEST;
		return ResponseEntity.status(code.status())
				.body(ErrorResponse.of(code, code.defaultMessage(), request.getRequestURI()));
	}

	/** 경로변수·쿼리 타입 불일치 ({@code /api/products/abc}). */
	@ExceptionHandler(MethodArgumentTypeMismatchException.class)
	public ResponseEntity<ErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException e,
			HttpServletRequest request) {
		ErrorCode code = ErrorCode.TYPE_MISMATCH;
		String message = "'%s' 값의 형식이 올바르지 않습니다. (입력: %s)"
				.formatted(e.getName(), String.valueOf(e.getValue()));
		return ResponseEntity.status(code.status())
				.body(ErrorResponse.of(code, message, request.getRequestURI()));
	}

	/**
	 * 매핑되지 않은 경로. <b>두 예외를 모두 잡는다.</b>
	 *
	 * <p>어느 쪽이 던져지는지는 {@code spring.web.resources.add-mappings} 값에 따라 갈린다 —
	 * 기본값(true)이면 정적 리소스 핸들러가 {@code /**}를 먼저 받아 {@code NoResourceFoundException}이
	 * 던져지고, {@code false}면 핸들러 탐색 실패까지 도달해 {@code NoHandlerFoundException}이
	 * 던져진다. 하나만 잡으면 설정이 바뀌는 순간 404 대신 500이 나간다.
	 */
	@ExceptionHandler({ NoHandlerFoundException.class, NoResourceFoundException.class })
	public ResponseEntity<ErrorResponse> handleNotFound(Exception e, HttpServletRequest request) {
		ErrorCode code = ErrorCode.ENDPOINT_NOT_FOUND;
		return ResponseEntity.status(code.status())
				.body(ErrorResponse.of(code, code.defaultMessage(), request.getRequestURI()));
	}

	@ExceptionHandler(HttpRequestMethodNotSupportedException.class)
	public ResponseEntity<ErrorResponse> handleMethodNotAllowed(HttpRequestMethodNotSupportedException e,
			HttpServletRequest request) {
		ErrorCode code = ErrorCode.METHOD_NOT_ALLOWED;
		String message = "지원하지 않는 요청 방식입니다. (%s)".formatted(e.getMethod());
		return ResponseEntity.status(code.status())
				.body(ErrorResponse.of(code, message, request.getRequestURI()));
	}

	/**
	 * 나머지 전부. <b>메시지에 예외 원문이나 스택트레이스를 담지 않는다</b>(계약 §5) —
	 * 내부 구조가 클라이언트로 새어나간다. 원문은 서버 로그에 ERROR로 남긴다.
	 */
	@ExceptionHandler(Exception.class)
	public ResponseEntity<ErrorResponse> handleUnexpected(Exception e, HttpServletRequest request) {
		log.error("처리되지 않은 예외 - path={}", request.getRequestURI(), e);
		ErrorCode code = ErrorCode.INTERNAL_ERROR;
		return ResponseEntity.status(code.status())
				.body(ErrorResponse.of(code, code.defaultMessage(), request.getRequestURI()));
	}
}
