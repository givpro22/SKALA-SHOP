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
import org.springframework.security.crypto.password.PasswordEncoder;

import shop.domain.Cart;
import shop.domain.CartItem;
import shop.domain.Customer;
import shop.domain.Product;
import shop.domain.ShopperProfile;
import shop.domain.User;
import shop.dto.CartItemAddRequest;
import shop.dto.OrderCreateRequest;
import shop.dto.OrderItemRequest;
import shop.dto.OrderResponse;
import shop.repository.CartItemRepository;
import shop.repository.CartRepository;
import shop.repository.CustomerRepository;
import shop.repository.ProductRepository;
import shop.repository.ShopperProfileRepository;
import shop.repository.UserRepository;
import shop.service.CartService;
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
	private final UserRepository userRepository;
	private final PasswordEncoder passwordEncoder;
	private final TransactionTemplate transactionTemplate;
	private final ShopperProfileRepository shopperProfileRepository;
	private final CartRepository cartRepository;
	private final CartItemRepository cartItemRepository;
	private final CartService cartService;

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
		seedUsers();
		seedShopperProfiles();
		seedCarts();
		log.info("local 시드 완료 - 상품 {}건, 고객 {}건, 계정 {}건, 카트 {}건",
				productRepository.count(), customerRepository.count(), userRepository.count(),
				cartRepository.count());
	}

	/**
	 * 재고는 <b>주문이 반영되기 전의 초기값</b>이다. 아래 주문 시드가 실행되면 §6.1의 최종값이 된다.
	 * (상품 2: 30 → 29, 상품 1: 50 → 48 → 취소 복원 50)
	 */
	private void seedProducts() {
		productRepository.saveAll(List.of(
				Product.create("무선 마우스", "조용한 클릭, 2.4GHz 무선", 25_000, 50, "/products/mouse.svg"),
				Product.create("기계식 키보드", "적축 텐키리스", 89_000, 30, "/products/keyboard.svg"),
				// 캡처 06 — 재고 부족 재현용. 3개를 주문하면 반드시 거부된다.
				Product.create("27인치 모니터", "QHD 75Hz", 320_000, 2, "/products/monitor.svg"),
				Product.create("USB-C 허브", "7포트, PD 100W", 45_000, 15, "/products/hub.svg"),
				Product.create("노이즈캔슬링 헤드폰", "최대 30시간 재생", 210_000, 8, "/products/headphone.svg"),
				// 캡처 09 — 낙관적 락 재현용. 재고를 100으로 크게 둔 것은 의도다(§6.6):
				// OUT_OF_STOCK이 발생할 수 없으므로 "실패했다면 그것은 락이다"가 참이 된다.
				Product.create("한정판 키캡 세트", "PBT 이중사출", 12_000, 100, "/products/keycap.svg")));

		seedCatalogProducts();
	}

	/**
	 * 상품 7~20 (스펙 §14.1). <b>id 1~6은 위에서 한 건도 바뀌지 않았다</b> — 캡처 04~09의 재현 조건이
	 * 전부 그 값·id·순서에 걸려 있다.
	 *
	 * <p>"실제 쇼핑몰처럼" 보이려면 목록이 한 화면을 넘어야 하고, 검색·정렬·페이지네이션이 <b>기동만으로</b>
	 * 의미를 가져야 한다. 6건으로는 {@code size=8} 페이지네이션이 1페이지로 끝나 화면에서 아무것도
	 * 증명하지 못한다. 20건이면 {@code size=8} → 3페이지(8/8/4), 기본 {@code size=12} → 2페이지다.
	 *
	 * <p>검색 검산: {@code 키보드} → 3건(2·7·8), {@code 마우스} → 2건(1·9),
	 * <b>{@code 자전거} → 0건</b>(어디에도 그 문자열이 없다 — "검색 결과 없음" 화면의 근거).
	 *
	 * <p><b>신규 14건의 {@code imageUrl}은 {@code null}이다.</b> {@code /products/*.svg}는 기존 6건 것만
	 * 실제로 존재하므로, 없는 경로를 넣으면 목록을 열 때마다 404 요청이 14건 나가고 콘솔이 오염되는데
	 * 화면 결과는 {@code null}일 때와 똑같다(대체 표시). {@code null}은 오류가 아니라 정상 경로다.
	 */
	private void seedCatalogProducts() {
		productRepository.saveAll(List.of(
				Product.create("무선 저소음 키보드", "펜타그래프, 블루투스 3채널", 62_000, 24, null),
				Product.create("키보드 손목받침대", "메모리폼", 18_000, 40, null),
				// kim 카트 2번째 라인.
				Product.create("게이밍 마우스패드", "대형 900×400", 15_000, 60, null),
				Product.create("FHD 웹캠", "1080p 30fps, 자동 초점", 55_000, 12, null),
				// choi 카트 — 체크아웃 성공 재현용.
				Product.create("블루투스 스피커", "20W, IPX7 방수", 78_000, 18, null),
				Product.create("노트북 거치대", "알루미늄, 6단 각도", 32_000, 25, null),
				Product.create("USB 콘덴서 마이크", "카디오이드, 헤드폰 모니터링", 120_000, 9, null),
				Product.create("모니터 암 싱글", "가스 스프링, VESA 100", 95_000, 7, null),
				Product.create("외장 SSD 1TB", "USB 3.2 Gen2, 1050MB/s", 135_000, 20, null),
				Product.create("멀티탭 6구", "개별 스위치, 3m", 22_000, 35, null),
				Product.create("노트북 파우치 14인치", "발수 코팅", 28_000, 30, null),
				Product.create("무선 충전 패드", "15W 고속 충전", 34_000, 22, null),
				// kim 카트 3번째 라인 · 최저가(PRICE_ASC 첫 항목).
				Product.create("HDMI 케이블 2m", "4K 60Hz 지원", 9_000, 80, null),
				// park 카트 — 재고 부족 재현용. **재고 1인데 카트에는 3개가 담긴다**(§14.4).
				Product.create("리퍼비시 무선 이어폰", "ANC, 리퍼 등급 A", 29_000, 1, null)));
	}

	/**
	 * 운영자 계정. 캡처 "로그인 성공"과 "토큰 없이 쓰기 거부"를 재현하려면 계정이 하나는 있어야 한다.
	 *
	 * <p>비밀번호는 시드에서도 <b>인코딩해서</b> 넣는다. 평문을 넣으면 로그인 시
	 * {@code passwordEncoder.matches} 가 항상 false 라, 계정은 보이는데 로그인만 안 되는
	 * 상태가 된다 — 원인을 찾기 가장 어려운 부류다.
	 */
	private void seedUsers() {
		/*
		 * 기존 계정. **값이 그대로라 기존 인증 캡처(15~20)가 전부 재현된다.**
		 * 이것이 유일한 ADMIN 이다 — 가입으로 만든 계정은 항상 SHOPPER 이므로 관리자를 늘리는
		 * API 경로가 존재하지 않는다(계약 §9.4.1).
		 */
		userRepository.save(User.createAdmin("admin@skala.shop", passwordEncoder.encode("skala1234")));

		// 구매자 계정 4건(§14.3). 비밀번호는 시드에서도 인코딩해서 넣는다 — 평문을 넣으면
		// matches 가 항상 false 라 계정은 보이는데 로그인만 안 되는 상태가 된다.
		userRepository.saveAll(List.of(
				User.create("kim@skala.shop", passwordEncoder.encode("skala1234")),
				User.create("lee@skala.shop", passwordEncoder.encode("skala1234")),
				User.create("park@skala.shop", passwordEncoder.encode("skala1234")),
				User.create("choi@skala.shop", passwordEncoder.encode("skala1234")),
				// id 6 — 결제 검증 전용(§14.6). 역할은 다른 구매자와 같은 SHOPPER 다.
				User.create("han@skala.shop", passwordEncoder.encode("skala1234"))));
	}

	/**
	 * 계정 2~5를 기존 고객 1~4에 연결한다(§14.3). 빈 카트도 함께 만든다.
	 *
	 * <p><b>시드가 프로필을 명시적으로 만들므로 BR-31의 자동 생성 경로를 타지 않는다.</b>
	 * 자동 생성은 새로 가입한 계정에서만 발생하며, 그 경로는 100,000 포인트를 주지만 여기서는
	 * 기존 고객의 포인트(1,911,000 / 10,000 / 300,000 / 500,000)가 그대로 쓰인다 —
	 * 포인트 부족 재현이 lee 의 10,000에 걸려 있어 덮어쓰면 안 된다.
	 */
	/**
	 * 계정 2~5를 기존 고객 1~4에 연결하고, 결제 검증 전용 계정(6 ↔ 5)을 붙인다.
	 *
	 * <h2>§14.6 — 소모되는 것은 계정이 아니라 카트다</h2>
	 *
	 * <p><b>{@code han@skala.shop} 의 카트는 비어 있고, 비워 두는 것이 이 픽스처의 전부다.</b>
	 *
	 * <p>§14.4·§14.5의 "순서 의존을 없앤다" 원칙이 <b>거부 케이스에만</b> 적용돼 있었다.
	 * lee(포인트 부족)·park(재고 부족)은 거부되므로 상태를 바꾸지 않아 몇 번이든 재현되지만,
	 * <b>성공 케이스인 choi 는 한 번 쓰면 카트가 빈다</b>(BR-29). 그래서 choi 는 사실상
	 * <b>일회용 픽스처</b>였고, 캡처 27이 그 한 번을 썼다. 그 뒤로 "체크아웃이 실제로 되는지"를
	 * 확인하려는 사람은 쓸 계정이 없어 <b>직접 계정을 만들어 시드를 오염시켰다.</b>
	 *
	 * <p><b>라인을 넣으면 안 된다.</b> 몇 개를 넣어도 소용없다 — 체크아웃은 카트 전체를 사므로
	 * 한 번에 전부 소비된다. 비워 두면 검증자가 매번 자기 카트를 만들어 쓰고,
	 * <b>재고가 남는 한 무한히 반복된다.</b> 담기부터가 검증 대상이라 흐름도 자연스럽다.
	 *
	 * <p>포인트 3,000,000은 최고가 상품(320,000)의 9회분이며, kim(1,911,000)과 자릿수가 같아
	 * 고객 목록 화면에서 튀지 않는다. <b>이름·이메일을 평범하게 둔 것도 의도다</b> —
	 * {@code qa@} 같은 주소가 고객 목록 캡처에 뜨면 데모 화면이 검증 도구처럼 보인다.
	 *
	 * <h2>결제 검증에는 상품 16 「멀티탭 6구」(22,000원, 재고 35)를 쓴다</h2>
	 *
	 * <p>어떤 캡처·픽스처도 그 상품을 참조하지 않는다. 반면 <b>3(재고 2) · 6(재고 100, 낙관적 락
	 * 재시도 여력) · 20(재고 1)</b> 은 특히 위험하고, 1·2·4·5·9·11·19도 참조 중이다.
	 *
	 * <p><b>금지 목록 대신 권장 하나를 남기는 이유:</b> 목록이 길수록 지켜지지 않고,
	 * <b>어긴 결과는 그 자리가 아니라 다른 라운드의 오탐으로 나타난다</b> —
	 * 원인과 증상이 떨어져 있어 추적이 가장 어려운 부류다.
	 */
	private void seedShopperProfiles() {
		linkShopper("kim@skala.shop", 1L);
		linkShopper("lee@skala.shop", 2L);
		linkShopper("park@skala.shop", 3L);
		linkShopper("choi@skala.shop", 4L);
		// §14.6 — 빈 카트가 붙는다. seedCarts 는 이 계정을 건드리지 않는다.
		linkShopper("han@skala.shop", 5L);
	}

	private void linkShopper(String username, Long customerId) {
		User user = userRepository.findByUsername(username).orElseThrow();
		Customer customer = customerRepository.findById(customerId).orElseThrow();
		shopperProfileRepository.save(ShopperProfile.create(user, customer));
		cartRepository.save(Cart.create(customer));
	}

	/**
	 * 카트 시드(§14.4). <b>각 케이스의 부족 원인이 하나씩만 되도록 짰다</b> — 재고와 포인트가 동시에
	 * 모자라면 어느 규칙이 발동했는지 캡처만으로 판정할 수 없다.
	 *
	 * <table>
	 *   <tr><th>고객</th><th>총액</th><th>포인트</th><th>재고</th><th>재현 대상</th></tr>
	 *   <tr><td>1 김스칼라</td><td>84,000</td><td>1,911,000 ✅</td><td>충분 ✅</td><td>다건 장바구니</td></tr>
	 *   <tr><td>2 이포인트</td><td>25,000</td><td>10,000 ❌</td><td>충분 ✅</td><td>포인트 부족</td></tr>
	 *   <tr><td>3 박취소</td><td>87,000</td><td>300,000 ✅</td><td>1 ❌</td><td>재고 부족</td></tr>
	 *   <tr><td>4 최구매</td><td>78,000</td><td>500,000 ✅</td><td>충분 ✅</td><td>체크아웃 성공</td></tr>
	 * </table>
	 *
	 * <p><b>왜 kim 이 아니라 choi 로 체크아웃을 찍는가.</b> 체크아웃은 카트를 비운다(BR-29).
	 * kim 의 카트로 찍으면 그 다음부터 "장바구니 다건" 화면을 다시 찍을 수 없다. lee·park 은
	 * 거부되므로 상태를 바꾸지 않아 반복 촬영에 가장 안전하다.
	 */
	private void seedCarts() {
		// kim — 3라인 84,000원. 어떤 캡처에서도 소비되지 않으므로 몇 번을 열어도 그대로다.
		cartService.addItem("kim@skala.shop", new CartItemAddRequest(4L, 1));
		cartService.addItem("kim@skala.shop", new CartItemAddRequest(9L, 2));
		cartService.addItem("kim@skala.shop", new CartItemAddRequest(19L, 1));

		// lee — 포인트 부족 재현용. 재고는 50으로 충분해 원인이 하나다.
		cartService.addItem("lee@skala.shop", new CartItemAddRequest(1L, 1));

		// choi — 체크아웃 성공 재현용.
		cartService.addItem("choi@skala.shop", new CartItemAddRequest(11L, 1));

		seedOutOfStockCart();
	}

	/**
	 * park 카트 — <b>서비스가 아니라 리포지토리로 직접 저장한다</b>(§14.4).
	 *
	 * <p>수량 3인데 상품 20의 재고는 1이다. {@code CartService.addItem}은 이를 {@code OUT_OF_STOCK}으로
	 * 거부하므로(BR-26) 정상 경로로는 만들 수 없다. <b>그리고 만들 수 없다는 사실 자체가 재현 대상이다</b> —
	 * "담은 뒤 재고가 줄어든 상태"를 기동만으로 만들어야 하는데, 그 상태에 도달하는 정상 경로는
	 * 정의상 존재하지 않는다.
	 *
	 * <p>§6.4의 "주문 시드는 실제 서비스를 호출해 만든다" 원칙은 유지된다. 여기가 유일한 예외이며
	 * 예외인 이유를 이 주석이 들고 있다. 이 라인 덕분에 {@code INSUFFICIENT_STOCK} 표시와
	 * {@code checkoutable: false}가 <b>손 조작 없이</b> 화면에 나타난다 — 없으면 촬영자가 재고를
	 * 손으로 낮춰야 하고, 다시 찍을 때 같은 상태를 복원할 수 없다.
	 *
	 * <p>{@code unitPriceAtAdd}는 현재가(29,000)와 같다. 가격까지 어긋나게 하면 {@code priceChanged}와
	 * {@code INSUFFICIENT_STOCK}이 한 화면에 겹쳐 어느 것을 보여주는 캡처인지 판정할 수 없다.
	 */
	private void seedOutOfStockCart() {
		Cart parkCart = cartRepository.findByCustomerId(3L).orElseThrow();
		Product refurbished = productRepository.findById(20L).orElseThrow();
		cartItemRepository.save(CartItem.createUnchecked(parkCart, refurbished, 3));
	}

	private void seedCustomers() {
		customerRepository.saveAll(List.of(
				Customer.create("김스칼라", "kim@skala.shop", 2_000_000),
				// 캡처 05 — 포인트 부족 재현용. 최저가 상품(25,000)보다도 적다.
				Customer.create("이포인트", "lee@skala.shop", 10_000),
				Customer.create("박취소", "park@skala.shop", 300_000),
				Customer.create("최구매", "choi@skala.shop", 500_000),
				// id 5 — 결제 검증 전용(§14.6). 아래 seedShopperProfiles 가 **빈 카트**를 붙인다.
				Customer.create("한지우", "han@skala.shop", 3_000_000)));
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
