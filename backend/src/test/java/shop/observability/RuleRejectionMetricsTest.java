package shop.observability;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import shop.domain.Customer;
import shop.domain.Product;
import shop.dto.OrderCreateRequest;
import shop.dto.OrderItemRequest;
import shop.exception.BusinessException;
import shop.repository.CustomerRepository;
import shop.repository.OrderRepository;
import shop.repository.ProductRepository;
import shop.service.OrderService;

/**
 * {@code shop.rule.rejected} 계측 판정. spring-observability §5.
 *
 * <h2>왜 절대값이 아니라 전후 차이인가</h2>
 *
 * <p><b>이 주제는 실패가 조용하다.</b> 아무 일도 하지 않는 aspect 와 사건이 없어서 0 인 카운터는
 * 출력이 같다. 그래서 "카운터가 존재한다"거나 "값이 0 보다 크다"로는 아무것도 증명되지 않는다.
 * 규칙을 강제로 발동시키고 <b>증가분이 실제 거부 건수와 정확히 같은지</b>만이 판정이 된다.
 *
 * <h2>왜 코드별로 비교하는가</h2>
 *
 * <p>합계만 맞추면 두 오류가 서로를 가린다 — {@code OUT_OF_STOCK} 을 두 번 세고
 * {@code CONCURRENT_UPDATE} 를 한 번도 못 세도 합계는 맞을 수 있다. 실패 목록에서 기대 맵을
 * 만들어 코드별로 대조하면 그 상쇄가 성립하지 않는다.
 *
 * <h2>변이 확인</h2>
 *
 * <p>{@link RuleRejectionMetrics} 의 {@code @Aspect} 를 떼면 증가분이 0 이 되어 이 테스트가
 * 실패해야 한다. 실패하지 않으면 다른 경로가 세고 있는 것이고, 그때 그 계측은 AOP 의 증거가 아니다.
 */
@SpringBootTest
@ActiveProfiles("test")
class RuleRejectionMetricsTest {

	private static final String METRIC = "shop.rule.rejected";

	@Autowired
	private MeterRegistry registry;
	@Autowired
	private OrderService orderService;
	@Autowired
	private ProductRepository productRepository;
	@Autowired
	private CustomerRepository customerRepository;
	@Autowired
	private OrderRepository orderRepository;
	@Autowired
	private TransactionTemplate transactionTemplate;

	@BeforeEach
	void clean() {
		transactionTemplate.executeWithoutResult(s -> {
			orderRepository.deleteAll();
			productRepository.deleteAll();
			customerRepository.deleteAll();
		});
	}

	// ------------------------------------------------------------------
	// 1. 낙관적 락 충돌 — 09b 와 같은 레이스를 그대로 쓴다
	// ------------------------------------------------------------------

	@Test
	@DisplayName("1. 09b 레이스의 409 건수와 shop.rule.rejected 증가분이 정확히 같다")
	void 락_충돌_건수와_카운터_증가분이_일치한다() throws Exception {
		Map<String, Double> before = snapshot();

		// 09b 테스트 1b 와 동일한 형태 — 재고 100 / 8스레드. 재고가 충분하므로 OUT_OF_STOCK 이
		// 원천 불가능하고, 실패가 있다면 그것은 반드시 락 충돌이다.
		List<Throwable> failures = new ArrayList<>();
		int attempts = 0;
		for (int attempt = 1; attempt <= 5 && failures.isEmpty(); attempt++) {
			clean();
			attempts = attempt;
			Long productId = saveProduct("계측용 키캡 세트-락", 12_000, 100);
			List<Runnable> tasks = new ArrayList<>();
			for (int i = 0; i < 8; i++) {
				Long buyerId = saveCustomer("구매자" + i, "buyer%d@skala.shop".formatted(i), 1_000_000);
				tasks.add(() -> orderService.createOrder(order(buyerId, productId, 1)));
			}
			failures.addAll(race(tasks));
		}

		Map<String, Double> after = snapshot();
		Map<String, Integer> expected = expectedByCode(failures);

		report("1. 낙관적 락 충돌 (재고 100 · 8스레드 · 시도 %d회)".formatted(attempts),
				before, after, expected, failures);

		assertThat(failures)
				.as("5회 시도에서 락 충돌을 한 번도 관측하지 못했다 — 계측 이전에 락부터 확인해야 한다")
				.isNotEmpty();
		assertThat(failures)
				.as("재고가 충분하므로 실패 원인은 낙관적 락뿐이어야 한다 (409 CONCURRENT_UPDATE)")
				.allMatch(OptimisticLockingFailureException.class::isInstance);

		assertThat(delta(before, after))
				.as("증가분이 코드별로 실제 거부 건수와 같아야 한다. "
						+ "같지 않으면 셋 중 하나다 — 자기 호출로 프록시를 안 탔거나, "
						+ "포인트컷이 서비스를 안 덮거나, 예외가 서비스 밖에서 변환됐다")
				.containsExactlyInAnyOrderEntriesOf(toDouble(expected));
	}

	// ------------------------------------------------------------------
	// 2. BusinessException 경로 — 타이밍에 기대지 않는 결정적 판정
	// ------------------------------------------------------------------

	@Test
	@DisplayName("2. 재고 부족 거부 1건에 카운터가 정확히 1 오른다 (code=OUT_OF_STOCK)")
	void 비즈니스_규칙_거부가_코드별로_집계된다() {
		Long productId = saveProduct("계측용 키캡 세트-재고", 12_000, 1);
		Long buyerId = saveCustomer("구매자", "buyer-oos@skala.shop", 1_000_000);

		orderService.createOrder(order(buyerId, productId, 1)); // 재고 1 → 0

		Map<String, Double> before = snapshot();
		List<Throwable> failures = new ArrayList<>();
		try {
			orderService.createOrder(order(buyerId, productId, 1)); // 재고 0 → 거부
		} catch (RuntimeException e) {
			failures.add(e);
		}
		Map<String, Double> after = snapshot();
		Map<String, Integer> expected = expectedByCode(failures);

		report("2. 재고 부족 거부 (단일 스레드)", before, after, expected, failures);

		assertThat(expected).containsExactly(Map.entry("OUT_OF_STOCK", 1));
		assertThat(delta(before, after))
				.as("거부 1건 → 정확히 1 (0 이면 aspect 가 안 걸린 것, 2 면 중첩 호출을 두 번 센 것)")
				.containsExactlyInAnyOrderEntriesOf(toDouble(expected));
	}

	// ------------------------------------------------------------------
	// 계측 헬퍼
	// ------------------------------------------------------------------

	/** {@code code} 태그별 현재 값. {@code registry.counter(...)} 로 읽으면 없던 카운터가 생기므로 find 로 읽는다. */
	private Map<String, Double> snapshot() {
		Map<String, Double> byCode = new TreeMap<>();
		for (Counter c : registry.find(METRIC).counters()) {
			byCode.merge(c.getId().getTag("code"), c.count(), Double::sum);
		}
		return byCode;
	}

	private Map<String, Double> delta(Map<String, Double> before, Map<String, Double> after) {
		Map<String, Double> d = new TreeMap<>();
		for (Map.Entry<String, Double> e : after.entrySet()) {
			double diff = e.getValue() - before.getOrDefault(e.getKey(), 0.0);
			if (diff != 0.0) {
				d.put(e.getKey(), diff);
			}
		}
		return d;
	}

	/** 실제로 던져진 예외에서 "이만큼 올랐어야 한다"를 만든다. 기대값을 손으로 적지 않는 이유다. */
	private Map<String, Integer> expectedByCode(List<Throwable> failures) {
		Map<String, Integer> m = new TreeMap<>();
		for (Throwable t : failures) {
			m.merge(codeOf(t), 1, Integer::sum);
		}
		return m;
	}

	private String codeOf(Throwable t) {
		if (t instanceof BusinessException be) {
			return be.getErrorCode().name();
		}
		if (t instanceof OptimisticLockingFailureException) {
			return "CONCURRENT_UPDATE"; // GlobalExceptionHandler 가 409 로 매핑하는 그 코드
		}
		return "UNEXPECTED:" + t.getClass().getSimpleName();
	}

	private Map<String, Double> toDouble(Map<String, Integer> m) {
		Map<String, Double> d = new LinkedHashMap<>();
		m.forEach((k, v) -> d.put(k, (double) v));
		return d;
	}

	/** 캡처 59 의 원본이 되는 출력. 단언 전에 찍어 실패해도 숫자가 남게 한다. */
	private void report(String title, Map<String, Double> before, Map<String, Double> after,
			Map<String, Integer> expected, List<Throwable> failures) {
		StringBuilder sb = new StringBuilder();
		sb.append("\n[shop.rule.rejected] ").append(title).append('\n');
		sb.append("  실제 거부 : ").append(failures.size()).append("건  ").append(expected).append('\n');
		sb.append("  카운터 전 : ").append(before).append('\n');
		sb.append("  카운터 후 : ").append(after).append('\n');
		sb.append("  증가분    : ").append(delta(before, after)).append('\n');
		sb.append("  판정      : 증가분 == 실제 거부 건수 ? ")
				.append(delta(before, after).equals(toDouble(expected)) ? "일치" : "불일치").append('\n');
		System.out.println(sb);
	}

	// ------------------------------------------------------------------
	// 픽스처 헬퍼 (09b 와 동일)
	// ------------------------------------------------------------------

	private List<Throwable> race(List<Runnable> tasks) throws Exception {
		int n = tasks.size();
		ExecutorService pool = Executors.newFixedThreadPool(n);
		CountDownLatch start = new CountDownLatch(1);
		CountDownLatch done = new CountDownLatch(n);
		AtomicInteger success = new AtomicInteger();
		List<Throwable> failures = java.util.Collections.synchronizedList(new ArrayList<>());

		for (Runnable task : tasks) {
			pool.submit(() -> {
				try {
					start.await();
					task.run();
					success.incrementAndGet();
				} catch (Throwable t) {
					failures.add(t);
				} finally {
					done.countDown();
				}
			});
		}
		start.countDown();
		done.await(30, TimeUnit.SECONDS);
		pool.shutdown();
		pool.awaitTermination(10, TimeUnit.SECONDS);
		return List.copyOf(failures);
	}

	private OrderCreateRequest order(Long customerId, Long productId, int quantity) {
		return new OrderCreateRequest(customerId, List.of(new OrderItemRequest(productId, quantity)));
	}

	private Long saveProduct(String name, int price, int stock) {
		return transactionTemplate.execute(
				s -> productRepository.save(Product.create(name, null, price, stock, null)).getId());
	}

	private Long saveCustomer(String name, String email, int point) {
		return transactionTemplate.execute(
				s -> customerRepository.save(Customer.create(name, email, point)).getId());
	}
}
