package shop.observability;

import java.lang.ref.WeakReference;

import org.aspectj.lang.annotation.AfterThrowing;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Component;

import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import shop.exception.BusinessException;
import shop.exception.ErrorCode;

/**
 * 서비스 계층을 빠져나가는 <b>도메인 규칙 거부</b>를 세어 {@code shop.rule.rejected} 로 내보낸다.
 *
 * <p>이 앱에서 하나의 질문에 답하기 위해 존재한다 — <b>비즈니스 규칙이 실제로 몇 번 발동했는가.</b>
 * Actuator 가 없으면 이 숫자는 로그에만 남아 아무도 세지 않고, AOP 가 없으면 Actuator 는 JVM
 * 기본 지표만 보여준다. 둘이 붙어야 장치가 된다.
 *
 * <h2>왜 규칙마다가 아니라 예외 타입으로 자르는가</h2>
 *
 * <p>포인트 부족·재고 부족·중복 상품·이미 취소됨… 규칙마다 카운터를 심으면 <b>규칙이 늘 때마다
 * 계측을 빠뜨릴 자리가 하나씩 는다.</b> 서비스 경계에서 예외 타입 하나로 자르고 코드를 태그로 쓰면
 * 새 {@link ErrorCode} 는 아무것도 하지 않아도 자동으로 집계된다. 실제로 이 프로젝트는 에러 코드가
 * 27개고, 규칙마다 심었다면 27군데를 손대고 그중 몇 개는 빠졌을 것이다.
 *
 * <p><b>단, "자동으로 집계된다"는 서비스 계층에서 발생하는 코드에 한한다.</b>
 * {@code VALIDATION_ERROR}·{@code MALFORMED_REQUEST}·{@code TYPE_MISMATCH}·
 * {@code ENDPOINT_NOT_FOUND} 는 증가분이 <b>0</b> 이며, 그것이 맞다 — 요청 본문 파싱·{@code @Valid}
 * 검증·핸들러 탐색은 <b>서비스에 닿기 전</b>에 끝나므로 이 포인트컷을 지나가지 않는다. 그 넷은
 * 클라이언트가 잘못 보낸 것이지 <b>도메인 규칙이 발동한 것이 아니다.</b> 이 카운터가 답하는 질문은
 * <b>"비즈니스 규칙이 몇 번 발동했나"이지 "요청이 몇 번 실패했나"가 아니다</b> — 후자를 알고 싶으면
 * {@code http.server.requests} 의 {@code status} 태그를 보면 되고, 그건 Actuator 가 이미 준다.
 *
 * <p>이름은 {@code shop.rule.rejected} 하나, 구분은 태그({@code code}) 하나다. 코드마다 메트릭
 * 이름을 만들면 이름이 27개로 늘고 <b>합계를 낼 수 없다</b>.
 *
 * <h2>왜 두 예외인가 — 이건 측정해서 알게 된 것이다</h2>
 *
 * <p>처음에는 {@link BusinessException} 하나만 셌다. 그 상태로 09b 낙관적 락 레이스를 돌리자
 * <b>실제 거부 7건에 증가분 0</b>이 나왔다. 원인은 계층 설계에 이미 적혀 있다 —
 * {@code CONCURRENT_UPDATE} 는 의도적으로 {@code BusinessException} 계층 <b>밖</b>이다
 * (도메인 스펙 §5.1). 그 실패는 서비스 메서드 본문이 아니라 <b>commit 시점</b>에 나므로 감쌀 코드가
 * 남아 있지 않고, 그래서 {@code OptimisticLockingFailureException} 을 전역 핸들러가 직접 잡는다.
 *
 * <p>계측이 그 하나를 놓치면 <b>가장 세고 싶은 사건을 못 세는 계측</b>이 된다 — 낙관적 락은 이
 * 프로젝트의 차별화 포인트고, "규칙이 몇 번 발동했나"의 답에서 그것만 빠진다. 그러면서도 카운터는
 * 0 이 아닌 값을 계속 보여주므로 <b>비어 있다는 사실 자체가 드러나지 않는다.</b> 그래서 계층이 아니라
 * <b>에러 코드표</b>를 계측의 경계로 삼았다. 두 예외를 세지만 나가는 것은 여전히 메트릭 이름 하나,
 * 태그 하나이며, 태그 값은 전부 §5 에러 코드표에 있는 코드다.
 *
 * <h2>왜 순서를 명시하는가 — 그리고 왜 맨 앞은 안 되는가</h2>
 *
 * <p>어드바이스가 트랜잭션 인터셉터보다 <b>바깥</b>에 있어야 commit 시점 예외를 볼 수 있다. 순서를
 * 주지 않으면 둘 다 {@code LOWEST_PRECEDENCE} 라 등록 순서에 좌우되고, 안쪽에 놓이는 날에는
 * {@code CONCURRENT_UPDATE} 가 <b>조용히</b> 0 이 된다. 이 주제의 실패는 예외를 던지지 않는다.
 *
 * <p>그렇다고 {@code HIGHEST_PRECEDENCE} 로 두면 안 된다. 처음에 그렇게 했다가 모든 서비스 호출이
 * 이렇게 죽었다:
 *
 * <pre>IllegalStateException: No MethodInvocation found: ... note that advices with order
 * HIGHEST_PRECEDENCE will execute before ExposeInvocationInterceptor!</pre>
 *
 * <p>Spring 은 AspectJ 포인트컷 평가를 위해 {@code ExposeInvocationInterceptor} 를 체인 맨 앞
 * ({@code HIGHEST_PRECEDENCE + 1})에 꽂는다. 그보다 앞서면 현재 invocation 을 못 찾는다.
 * 그래서 <b>위아래 양쪽으로 경계가 있다</b> — {@code ExposeInvocationInterceptor} 보다는 뒤,
 * 트랜잭션 인터셉터({@code LOWEST_PRECEDENCE})보다는 앞. {@code +100} 은 그 사이의 여유 있는 값이다.
 *
 * <h2>왜 같은 예외를 두 번 세지 않는가</h2>
 *
 * <p>{@code ShopCheckoutService.checkout} 은 {@code OrderService.createOrder} 를 <b>다른 빈으로</b>
 * 호출한다(프록시를 탄다). 그래서 구매자 화면에서 재고 부족이 한 번 나면 어드바이스는 안쪽·바깥쪽
 * 두 번 걸리고, 카운터는 사건 1건에 2 가 오른다. 관리자 API 로 같은 거부를 내면 1 이 오른다 —
 * <b>같은 사건이 경로에 따라 다르게 세어지는 숫자는 아무 질문에도 답하지 못한다.</b>
 *
 * <p>전파되는 동안 예외는 <b>같은 인스턴스</b>이므로, 스레드마다 마지막으로 센 예외를 기억해 두고
 * 동일 인스턴스면 건너뛴다. 결과적으로 <b>가장 안쪽</b>, 즉 규칙이 실제로 발동한 자리에서 한 번만
 * 세어진다. {@link WeakReference} 로 들고 있어 풀링된 요청 스레드가 예외 객체를 붙잡아 두지 않는다.
 *
 * <h2>알아 둘 것 둘</h2>
 *
 * <ul>
 *   <li><b>롤백돼도 카운터는 남는다.</b> Micrometer 카운터는 메모리에 있고 트랜잭션에 참여하지
 *       않는다. 의도한 동작이다 — 거부는 실제로 일어난 사건이므로 롤백과 함께 사라지면 안 된다.
 *       이 값은 <b>DB 를 세는 것이 아니다.</b></li>
 *   <li><b>재기동하면 0 이 된다.</b> 인메모리 레지스트리라 그렇다. 보고서에 절대값을 쓸 때는
 *       언제부터의 값인지 함께 적는다.</li>
 * </ul>
 */
@Aspect
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 100)
@RequiredArgsConstructor
public class RuleRejectionMetrics {

	public static final String METRIC = "shop.rule.rejected";

	/**
	 * 이 스레드에서 마지막으로 센 예외. 중첩 서비스 호출에서 같은 인스턴스를 두 번 세지 않기 위한 것이다.
	 *
	 * <p>{@code WeakReference} 인 이유: 요청 스레드는 풀링되므로 강한 참조로 두면 마지막 예외가
	 * (그리고 그 스택트레이스가 붙잡는 것 전부가) 다음 요청까지 살아남는다.
	 */
	private static final ThreadLocal<WeakReference<Throwable>> LAST_COUNTED = new ThreadLocal<>();

	private final MeterRegistry registry;

	/** 서비스 계층 전체. 하위 패키지까지 포함한다. */
	@Pointcut("execution(* shop.service..*.*(..))")
	void serviceLayer() {
	}

	/** 도메인 규칙 위반 — 코드는 예외가 들고 있는 것을 그대로 쓴다. */
	@AfterThrowing(pointcut = "serviceLayer()", throwing = "ex")
	public void businessRuleRejected(BusinessException ex) {
		count(ex, ex.getErrorCode().name());
	}

	/**
	 * 낙관적 락 충돌 — {@code GlobalExceptionHandler} 가 409 로 매핑하는 그 코드로 태그를 맞춘다.
	 *
	 * <p>{@code jakarta.persistence.OptimisticLockException} 이 아니라 Spring 타입을 잡는다.
	 * {@code JpaTransactionManager} 가 commit 실패를 Spring 타입으로 번역해 던지기 때문이며,
	 * 전역 핸들러가 같은 판단을 한 이유와 같다(스펙 §5.1).
	 */
	@AfterThrowing(pointcut = "serviceLayer()", throwing = "ex")
	public void concurrencyRejected(OptimisticLockingFailureException ex) {
		count(ex, ErrorCode.CONCURRENT_UPDATE.name());
	}

	private void count(Throwable ex, String code) {
		if (alreadyCounted(ex)) {
			return;
		}
		registry.counter(METRIC, "code", code).increment();
	}

	private boolean alreadyCounted(Throwable ex) {
		WeakReference<Throwable> last = LAST_COUNTED.get();
		if (last != null && last.get() == ex) {
			return true;
		}
		LAST_COUNTED.set(new WeakReference<>(ex));
		return false;
	}
}
