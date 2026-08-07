package shop.config;

import java.time.LocalDateTime;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import shop.domain.Customer;
import shop.domain.Product;
import shop.dto.OrderCreateRequest;
import shop.dto.OrderItemRequest;
import shop.dto.OrderResponse;
import shop.repository.CustomerRepository;
import shop.repository.ProductRepository;
import shop.service.OrderService;

/**
 * {@code local} 프로파일 시드(도메인 스펙 §6). <b>prod에서는 절대 실행되지 않는다.</b>
 *
 * <p>캡처 05(포인트 부족) · 06(재고 부족) · 08(이중 취소) · 09(낙관적 락)는 특정 DB 상태에서만
 * 재현된다. 손으로 데이터를 만들어 찍으면 다시 찍어야 할 때 같은 상태를 복원할 수 없으므로
 * 코드로 남긴다. {@code ddl-auto: create-drop}이라 매 기동마다 같은 상태가 재구성된다.
 *
 * <p><b>3단계로 나눈 이유(§6.4):</b>
 * <ol>
 *   <li>상품·고객을 리포지토리로 저장 — JPA가 {@code version}을 0으로 채운다.
 *       {@code data.sql}로 직접 INSERT하면 {@code version}이 {@code null}이 되어 기동과 조회는
 *       정상인데 <b>첫 주문에서야</b> 터진다.</li>
 *   <li>주문은 <b>실제 {@code OrderService}를 호출해</b> 만든다. 재고 차감·포인트 차감·환급이
 *       비즈니스 로직을 그대로 거치므로 §6.4 검산표와 자동으로 일치한다. 손으로 계산한 최종값을
 *       옮겨 적을 일이 없다.</li>
 *   <li>타임스탬프만 확정값으로 덮어쓴다. {@code createOrder}는 {@code now()}를 찍으므로
 *       §6.3의 명시값과 어긋나고, 두 주문이 같은 초에 몰려 목록 정렬이 흔들린다.</li>
 * </ol>
 *
 * <p>3단계는 엔티티에 setter를 추가하지 않고 JPQL 벌크 업데이트로 처리한다. 시드 하나 때문에
 * {@code Order}에 시간 변경 경로를 열면 불변식이 무너진다.
 */
@Component
@Profile("local")
@RequiredArgsConstructor
public class LocalSeedRunner implements CommandLineRunner {

	private static final Logger log = LoggerFactory.getLogger(LocalSeedRunner.class);

	private final ProductRepository productRepository;
	private final CustomerRepository customerRepository;
	private final OrderService orderService;
	private final TransactionTemplate transactionTemplate;

	@PersistenceContext
	private EntityManager em;

	@Override
	public void run(String... args) {
		if (productRepository.count() > 0) {
			return;
		}
		seedProducts();
		seedCustomers();
		seedOrders();
		log.info("local 시드 완료 - 상품 {}건, 고객 {}건", productRepository.count(), customerRepository.count());
	}

	/**
	 * 재고는 <b>주문이 반영되기 전의 초기값</b>이다. 아래 주문 시드가 실행되면 §6.1의 최종값이 된다.
	 * (상품 2: 30 → 29, 상품 1: 50 → 48 → 취소 복원 50)
	 */
	private void seedProducts() {
		productRepository.saveAll(List.of(
				Product.create("무선 마우스", "조용한 클릭, 2.4GHz 무선", 25_000, 50),
				Product.create("기계식 키보드", "적축 텐키리스", 89_000, 30),
				// 캡처 06 — 재고 부족 재현용. 3개를 주문하면 반드시 거부된다.
				Product.create("27인치 모니터", "QHD 75Hz", 320_000, 2),
				Product.create("USB-C 허브", "7포트, PD 100W", 45_000, 15),
				Product.create("노이즈캔슬링 헤드폰", "최대 30시간 재생", 210_000, 8),
				// 캡처 09 — 낙관적 락 재현용. 재고를 100으로 크게 둔 것은 의도다(§6.6):
				// OUT_OF_STOCK이 발생할 수 없으므로 "실패했다면 그것은 락이다"가 참이 된다.
				Product.create("한정판 키캡 세트", "PBT 이중사출", 12_000, 100)));
	}

	private void seedCustomers() {
		customerRepository.saveAll(List.of(
				Customer.create("김스칼라", "kim@skala.shop", 2_000_000),
				// 캡처 05 — 포인트 부족 재현용. 최저가 상품(25,000)보다도 적다.
				Customer.create("이포인트", "lee@skala.shop", 10_000),
				Customer.create("박취소", "park@skala.shop", 300_000),
				Customer.create("최구매", "choi@skala.shop", 500_000)));
	}

	/**
	 * <b>생성 순서가 중요하다.</b> §6.3은 주문 #1이 고객 3(취소됨), 주문 #2가 고객 1(주문됨)로
	 * 고객 번호와 <b>일부러 엇갈려</b> 있다. 고객 순서대로 만들면 id가 뒤집혀 캡처 07(취소 성공)과
	 * 08(이중 취소 차단)의 대상 주문이 서로 바뀐다.
	 */
	private void seedOrders() {
		// 주문 #1 — 고객 3, 상품 1 × 2. 생성 직후 취소해 CANCELED 상태로 만든다.
		OrderResponse canceled = orderService.createOrder(
				new OrderCreateRequest(3L, List.of(new OrderItemRequest(1L, 2))));
		orderService.cancelOrder(canceled.orderId());

		// 주문 #2 — 고객 1, 상품 2 × 1. ORDERED 상태로 남는다.
		OrderResponse ordered = orderService.createOrder(
				new OrderCreateRequest(1L, List.of(new OrderItemRequest(2L, 1))));

		overwriteTimestamps(canceled.orderId(), ordered.orderId());
	}

	private void overwriteTimestamps(Long canceledOrderId, Long orderedOrderId) {
		transactionTemplate.executeWithoutResult(status -> {
			em.createQuery("update Order o set o.orderedAt = :orderedAt, o.canceledAt = :canceledAt where o.id = :id")
					.setParameter("orderedAt", LocalDateTime.of(2026, 8, 1, 10, 0, 0))
					.setParameter("canceledAt", LocalDateTime.of(2026, 8, 2, 9, 0, 0))
					.setParameter("id", canceledOrderId)
					.executeUpdate();

			em.createQuery("update Order o set o.orderedAt = :orderedAt where o.id = :id")
					.setParameter("orderedAt", LocalDateTime.of(2026, 8, 5, 14, 30, 0))
					.setParameter("id", orderedOrderId)
					.executeUpdate();

			// 벌크 업데이트는 영속성 컨텍스트를 우회하므로, 남아 있는 1차 캐시를 비워
			// 이후 조회가 DB의 값을 읽게 한다.
			em.clear();
		});
	}
}
