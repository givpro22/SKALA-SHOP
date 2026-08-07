package shop.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
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

import shop.domain.Customer;
import shop.domain.Product;
import shop.dto.OrderCreateRequest;
import shop.dto.OrderItemRequest;
import shop.repository.CustomerRepository;
import shop.repository.OrderRepository;
import shop.repository.ProductRepository;

/**
 * 낙관적 락 동시성 테스트. 도메인 스펙 §6.6.
 *
 * <p><b>이 테스트가 차별화 포인트의 결정적 증거다.</b> 화면 캡처는 타이밍에 좌우되어 확률적이지만,
 * 여기는 레이스를 반복 관측하므로 재현된다.
 *
 * <p>설계 원칙 두 가지가 네 테스트를 관통한다.
 * <ul>
 *   <li><b>불변식은 무조건 단언, 예외 타입은 조건부로만.</b> {@code CountDownLatch}는 동시 "출발"만
 *       보장하고 트랜잭션 구간의 "겹침"은 보장하지 않는다. 겹치지 않으면 충돌이 나지 않으므로
 *       예외 타입을 무조건 단언하면 간헐 실패한다.</li>
 *   <li><b>예외 타입을 단언하려면 그 예외 외에는 실패할 수 없게 만든다.</b> 1b가 재고를 100으로,
 *       2b가 가격을 보유 포인트의 40%로 잡은 이유다. 정상적인 사업적 실패
 *       ({@code OUT_OF_STOCK}/{@code INSUFFICIENT_POINT})가 원천 불가능해지면
 *       "실패했다면 그것은 락이다"가 논리적으로 참이 된다.</li>
 * </ul>
 *
 * <p><b>불변식 단언은 등식이어야 한다.</b> {@code stock >= 0} / {@code point >= 0}으로는 결함을
 * 잡지 못한다 — 락이 없으면 두 트랜잭션이 같은 값을 읽고 각자 한 번씩만 차감하므로 최종값이
 * 정상보다 <b>크고, 그런데 여전히 양수</b>다. {@code == 초기 − 성공분} 등식만이 잡는다.
 */
@SpringBootTest
@ActiveProfiles("test")
class OrderConcurrencyTest {

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
	// 테스트 1a — 오버셀 불변식 (타이밍 무관, 절대 간헐 실패하지 않음)
	// ------------------------------------------------------------------

	@Test
	@DisplayName("1a. 재고 1에 동시 주문 2건 — 오버셀이 발생하지 않는다")
	void 재고_불변식이_지켜진다() throws Exception {
		Long productId = saveProduct("한정판 키캡 세트", 12_000, 1);
		Long buyerA = saveCustomer("구매자A", "a@skala.shop", 1_000_000);
		Long buyerB = saveCustomer("구매자B", "b@skala.shop", 1_000_000);

		RaceResult result = race(List.of(
				() -> orderService.createOrder(order(buyerA, productId, 1)),
				() -> orderService.createOrder(order(buyerB, productId, 1))));

		// 겹쳤든 안 겹쳤든 아래 셋은 모두 성립한다 → 예외 타입은 단언하지 않는다.
		assertThat(result.successCount()).isLessThanOrEqualTo(1);
		assertThat(stockOf(productId))
				.as("최종 재고는 초기 1에서 성공 건수만큼만 줄어야 하고 음수가 될 수 없다")
				.isEqualTo(1 - result.successCount())
				.isGreaterThanOrEqualTo(0);
		assertThat(orderRepository.count())
				.as("실패한 트랜잭션은 주문을 남기지 않는다(BR-19)")
				.isEqualTo(result.successCount());
	}

	// ------------------------------------------------------------------
	// 테스트 1b — Product 락이 막았음을 특정
	// ------------------------------------------------------------------

	@Test
	@DisplayName("1b. 재고 100에 동시 주문 8건 — 실패가 있다면 그것은 반드시 락 충돌이다")
	void 재고_락이_충돌을_만든다() throws Exception {
		final int initialStock = 100;
		final int threads = 8;
		boolean conflictObserved = false;

		// 단언 C — 충돌이 관측될 때까지 최대 5회. 재고 100이면 5회 × 8건 = 40건까지 감당된다.
		for (int attempt = 1; attempt <= 5 && !conflictObserved; attempt++) {
			clean();
			Long productId = saveProduct("한정판 키캡 세트", 12_000, initialStock);
			List<Runnable> tasks = new ArrayList<>();
			for (int i = 0; i < threads; i++) {
				Long buyerId = saveCustomer("구매자" + i, "buyer%d@skala.shop".formatted(i), 1_000_000);
				tasks.add(() -> orderService.createOrder(order(buyerId, productId, 1)));
			}

			RaceResult result = race(tasks);

			// 단언 A (무조건) — 락 부재를 직접 잡는다. 락이 없으면 lost update로
			// 최종 재고가 (100 − 성공수)보다 커진다.
			assertThat(stockOf(productId))
					.as("최종 재고 == 초기 − 성공 건수")
					.isEqualTo(initialStock - result.successCount());
			assertThat(orderRepository.count()).isEqualTo(result.successCount());

			// 단언 B (조건부) — 재고가 충분해 OUT_OF_STOCK이 불가능하므로,
			// 실패가 있다면 그것은 반드시 락 충돌이다.
			if (!result.failures().isEmpty()) {
				assertThat(result.failures())
						.as("재고가 충분하므로 실패 원인은 낙관적 락뿐이어야 한다")
						.allMatch(OptimisticLockingFailureException.class::isInstance);
				conflictObserved = true;
			}
		}

		// 5회 모두 미관측이면 완화하지 말고 실패로 남긴다 —
		// @Version 누락이나 트랜잭션 경계 문제일 수 있으므로 조사 대상이다.
		assertThat(conflictObserved)
				.as("5회 시도에서 락 충돌을 한 번도 관측하지 못했다 (@Version 누락 또는 트랜잭션 경계 확인 필요)")
				.isTrue();
	}

	// ------------------------------------------------------------------
	// 테스트 2a — 오버스펜딩 불변식 (Customer 락)
	// ------------------------------------------------------------------

	@Test
	@DisplayName("2a. 같은 고객이 서로 다른 상품 2건을 동시 주문 — 보유액을 초과해 쓸 수 없다")
	void 포인트_불변식이_지켜진다() throws Exception {
		final int p = 100_000;
		// 각 60,000 — 하나만 살 수 있고 둘 다는 못 산다.
		Long productA = saveProduct("상품A", 60_000, 100);
		Long productB = saveProduct("상품B", 60_000, 100);
		Long customerId = saveCustomer("최구매", "choi@skala.shop", p);

		// 상품 행이 서로 다르므로 Product 락은 발동하지 않는다. 경합하는 유일한 행이 Customer다.
		RaceResult result = race(List.of(
				() -> orderService.createOrder(order(customerId, productA, 1)),
				() -> orderService.createOrder(order(customerId, productB, 1))));

		assertThat(result.successCount()).isEqualTo(1);
		assertThat(pointOf(customerId)).isGreaterThanOrEqualTo(0);
		// 락이 없으면 둘 다 성공(successCount=2)하고 잔액은 한 번만 차감되어 40,000이 되는데,
		// 100,000 − 120,000 = −20,000과 어긋나 이 단언이 깨진다.
		// >= 0 단언만으로는 40,000이 양수라 통과해버린다.
		assertThat(pointOf(customerId))
				.as("최종 포인트 == P − 성공한 주문의 총액")
				.isEqualTo(p - 60_000 * result.successCount());
	}

	// ------------------------------------------------------------------
	// 테스트 2b — Customer 락이 막았음을 특정
	// ------------------------------------------------------------------

	@Test
	@DisplayName("2b. 포인트가 둘 다 살 만큼 충분할 때 — 실패가 있다면 그것은 반드시 락 충돌이다")
	void 포인트_락이_충돌을_만든다() throws Exception {
		final int p = 100_000;
		final int price = 40_000; // 둘 다 사도 80,000 ≤ P → INSUFFICIENT_POINT가 원천 불가능하다.
		boolean conflictObserved = false;

		for (int attempt = 1; attempt <= 5 && !conflictObserved; attempt++) {
			clean();
			Long productA = saveProduct("상품A", price, 100);
			Long productB = saveProduct("상품B", price, 100);
			Long customerId = saveCustomer("최구매", "choi@skala.shop", p);

			RaceResult result = race(List.of(
					() -> orderService.createOrder(order(customerId, productA, 1)),
					() -> orderService.createOrder(order(customerId, productB, 1))));

			// 단언 A (무조건)
			assertThat(pointOf(customerId))
					.as("최종 포인트 == P − 성공한 주문들의 총액")
					.isEqualTo(p - price * result.successCount());

			// 단언 B (조건부) — 잔액이 충분해 INSUFFICIENT_POINT가 불가능하므로,
			// 실패가 있다면 그것은 반드시 Customer 락 충돌이다.
			if (!result.failures().isEmpty()) {
				assertThat(result.failures())
						.as("포인트가 충분하므로 실패 원인은 낙관적 락뿐이어야 한다")
						.allMatch(OptimisticLockingFailureException.class::isInstance);
				conflictObserved = true;
			}
		}

		assertThat(conflictObserved)
				.as("5회 시도에서 Customer 락 충돌을 관측하지 못했다 (Customer의 @Version 확인 필요)")
				.isTrue();
	}

	// ------------------------------------------------------------------
	// 헬퍼
	// ------------------------------------------------------------------

	/**
	 * 모든 스레드를 같은 지점에서 출발시킨다.
	 *
	 * <p>{@code Thread.sleep}으로 타이밍을 맞추지 않는다 — 재현성이 없다.
	 */
	private RaceResult race(List<Runnable> tasks) throws Exception {
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
		return new RaceResult(success.get(), List.copyOf(failures));
	}

	private record RaceResult(int successCount, List<Throwable> failures) {
	}

	private OrderCreateRequest order(Long customerId, Long productId, int quantity) {
		return new OrderCreateRequest(customerId, List.of(new OrderItemRequest(productId, quantity)));
	}

	private Long saveProduct(String name, int price, int stock) {
		return transactionTemplate.execute(
				s -> productRepository.save(Product.create(name, null, price, stock)).getId());
	}

	private Long saveCustomer(String name, String email, int point) {
		return transactionTemplate.execute(
				s -> customerRepository.save(Customer.create(name, email, point)).getId());
	}

	private int stockOf(Long productId) {
		return transactionTemplate.execute(s -> productRepository.findById(productId).orElseThrow().getStock());
	}

	private int pointOf(Long customerId) {
		return transactionTemplate.execute(s -> customerRepository.findById(customerId).orElseThrow().getPoint());
	}
}
