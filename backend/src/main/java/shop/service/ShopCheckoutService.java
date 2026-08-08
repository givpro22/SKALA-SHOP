package shop.service;

import java.util.Comparator;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import shop.domain.Cart;
import shop.domain.CartItem;
import shop.domain.Customer;
import shop.domain.Product;
import shop.dto.OrderCreateRequest;
import shop.dto.OrderItemRequest;
import shop.dto.OrderResponse;
import shop.exception.CartEmptyException;
import shop.exception.CartStaleException;
import shop.exception.ProductNotFoundException;
import shop.repository.CartRepository;
import shop.repository.ProductRepository;

/**
 * 장바구니 체크아웃. 도메인 스펙 §12.1 · BR-29.
 *
 * <p><b>재고·포인트 로직을 복제하지 않는다.</b> 이 서비스가 하는 일은 카트를 읽어
 * {@code OrderCreateRequest}와 동등한 입력을 만들고, 그 뒤 {@link OrderService#createOrder}를
 * <b>위임 호출</b>하는 것뿐이다. 복제했다면 §4.1의 검증 순서가 두 벌이 되고 BR-19~BR-22의 불변식이
 * 한쪽에서만 지켜진다 — 그리고 §6.6의 동시성 테스트 네 개를 체크아웃용으로 또 써야 했을 것이다.
 * 위임했기 때문에 그 테스트가 <b>이 경로도 그대로 보증한다.</b>
 *
 * <p>{@code createOrder}는 이미 {@code @Transactional}이며 기본 전파({@code REQUIRED})이므로
 * 이 트랜잭션에 <b>합류한다.</b> 새 트랜잭션을 열면 BR-29의 원자성이 깨져 "주문은 커밋됐는데 카트
 * 비우기가 롤백된" 상태가 만들어진다.
 */
@Service
@RequiredArgsConstructor
public class ShopCheckoutService {

	private final CartRepository cartRepository;
	private final ProductRepository productRepository;
	private final ShopperProfileService shopperProfileService;
	private final OrderService orderService;

	/**
	 * 체크아웃. <b>검증 순서는 스펙 §12.1에 고정되어 있으며 임의로 바꾸지 않는다.</b>
	 *
	 * <pre>
	 *   2. 카트에 라인 1건 이상            → CART_EMPTY          (400)
	 *   3. 각 라인의 상품 존재 (productId ASC) → PRODUCT_NOT_FOUND (404)
	 *   4. expectedTotalPrice == 재계산 총액  → CART_STALE        (409)
	 *   5. 재고        (productId ASC)      → OUT_OF_STOCK        (400)  ─┐
	 *   6. 포인트                            → INSUFFICIENT_POINT (400)  ─┼ createOrder 가 한다
	 *   7. flush/commit 시 버전 일치         → CONCURRENT_UPDATE  (409)  ─┘
	 * </pre>
	 *
	 * <p><b>왜 4번이 5·6번보다 앞인가.</b> 총액이 어긋난 상태에서 재고·포인트를 판정하면 사용자가
	 * 보지 않은 금액으로 판정한 결과를 돌려주게 된다. 가격이 25,000 → 40,000으로 오른 상황에서
	 * 4번이 뒤에 있으면 {@code INSUFFICIENT_POINT}가 나가고 <b>사용자는 자기 포인트를 의심한다.</b>
	 * 실제 원인은 가격 변동이다 — 진단 가능한 에러가 진단 불가능한 에러보다 앞에 와야 한다.
	 *
	 * <p><b>왜 순회 순서를 {@code productId} 오름차순으로 고정하는가.</b> §4.1은 "요청 배열 순서"로
	 * 순회를 고정했는데 체크아웃에는 요청 배열이 없다 — 서버가 카트에서 읽는다. 순서를 DB 반환
	 * 순서에 맡기면 여러 라인이 동시에 재고 부족일 때 <b>어느 상품이 메시지에 나올지 매번 달라지고</b>
	 * QA가 재현할 수 없다. {@code productId} 오름차순은 DB·JPA 구현과 무관하게 결정적이다.
	 * (화면 정렬은 {@code addedAt DESC}이며 목적이 다르다 — 사용성 대 재현성.)
	 *
	 * <p>실패하면(400·409 포함) <b>카트는 손대지 않은 상태로 남는다</b> — 트랜잭션 전체가 롤백되므로
	 * 그대로 재시도할 수 있다.
	 */
	@Transactional
	public OrderResponse checkout(String username, int expectedTotalPrice) {
		Customer customer = shopperProfileService.resolveForWrite(username);

		Cart cart = cartRepository.findByCustomerIdWithItems(customer.getId())
				.orElseThrow(CartEmptyException::new);

		// 2. 빈 카트 거부. 조회는 빈 카트가 정상이지만 결제는 아니다.
		List<CartItem> lines = cart.getItems().stream()
				.sorted(Comparator.comparing(item -> item.getProduct().getId()))
				.toList();
		if (lines.isEmpty()) {
			throw new CartEmptyException();
		}

		/*
		 * 3. 상품 존재 확인 — productId 오름차순.
		 *
		 * BR-30 이 상품 삭제 시 참조 CartItem 을 함께 지우므로 정상 경로에서는 여기서 걸리지 않는다.
		 * 그래도 확인하는 이유: CartItem.product 는 LAZY 프록시라, 행이 사라진 상태에서 가격을 읽는
		 * 순간 EntityNotFoundException 이 나 500 으로 샌다. 계약에 있는 404 로 번역해 둔다.
		 */
		List<Product> products = lines.stream()
				.map(line -> productRepository.findById(line.getProduct().getId())
						.orElseThrow(() -> new ProductNotFoundException(line.getProduct().getId())))
				.toList();

		/*
		 * 4. 화면 버전 검증 — 낙관적 락을 화면 계층으로 올린 것이다(스펙 §13.2).
		 *
		 * @Version 이 DB 행의 버전을 검증하듯, 여기서는 사용자가 본 총액의 버전을 검증한다.
		 * **현재 단가로 재계산한다**(BR-27) — unitPriceAtAdd 로 계산하면 카트에 오래 둔 상품이
		 * 옛 가격으로 결제되고 CART_STALE 자체가 무의미해진다.
		 */
		int actualTotalPrice = 0;
		for (int i = 0; i < lines.size(); i++) {
			actualTotalPrice += products.get(i).getPrice() * lines.get(i).getQuantity();
		}
		if (actualTotalPrice != expectedTotalPrice) {
			throw new CartStaleException(expectedTotalPrice, actualTotalPrice);
		}

		/*
		 * 5·6·7 — 기존 주문 생성에 위임한다. **같은 규칙, 같은 코드다.**
		 * 순회 순서가 productId ASC 로 고정되므로 createOrder 의 "요청 배열 순서" 순회가
		 * 결정적으로 동작한다.
		 */
		List<OrderItemRequest> items = lines.stream()
				.map(line -> new OrderItemRequest(line.getProduct().getId(), line.getQuantity()))
				.toList();
		OrderResponse order = orderService.createOrder(new OrderCreateRequest(customer.getId(), items));

		/*
		 * BR-29 ③ — 카트 비우기. **같은 트랜잭션이다.**
		 * 별도 트랜잭션으로 빼면 주문은 됐는데 카트가 남아 사용자가 같은 것을 다시 산다.
		 */
		cart.clearItems();

		return order;
	}
}
