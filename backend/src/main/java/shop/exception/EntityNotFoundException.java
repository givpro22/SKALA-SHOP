package shop.exception;

/**
 * 조회 대상이 없을 때의 공통 부모(404 계열).
 *
 * <p>{@code jakarta.persistence.EntityNotFoundException}과 이름이 겹친다. 이 프로젝트에서는
 * 항상 {@code shop.exception} 쪽을 쓰며, JPA 예외를 import하지 않도록 주의한다.
 */
public abstract class EntityNotFoundException extends BusinessException {

	protected EntityNotFoundException(ErrorCode errorCode, String message) {
		super(errorCode, message);
	}
}
