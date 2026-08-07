package shop.service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import shop.domain.Customer;
import shop.domain.Order;
import shop.domain.OrderItem;
import shop.domain.Product;
import shop.dto.OrderCreateRequest;
import shop.dto.OrderItemRequest;
import shop.dto.OrderResponse;
import shop.exception.CustomerNotFoundException;
import shop.exception.DuplicateOrderItemException;
import shop.exception.OrderNotFoundException;
import shop.exception.ProductNotFoundException;
import shop.repository.CustomerRepository;
import shop.repository.OrderRepository;
import shop.repository.ProductRepository;

/**
 * 주문 비즈니스 로직.
 *
 * <p>이 서비스의 두 쓰기 메서드가 이 프로젝트의 핵심이다. 재고·포인트·주문이 <b>전부 성공하거나
 * 전부 실패</b>해야 하며, 그 보장은 메서드마다 트랜잭션을 하나로 유지한 데서 나온다.
 * 내부에서 {@code REQUIRES_NEW}로 쪼개면 "포인트만 빠지고 주문이 없는" 상태가 실제로 만들어진다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class OrderService {

	private final OrderRepository orderRepository;
	private final CustomerRepository customerRepository;
	private final ProductRepository productRepository;

	/**
	 * 주문 생성. <b>검증 순서는 스펙 §4.1에 고정되어 있으며 임의로 바꾸지 않는다.</b>
	 *
	 * <pre>
	 *   2. items 내 productId 중복       → DUPLICATE_ORDER_ITEM (400)
	 *   3. 고객 존재                      → CUSTOMER_NOT_FOUND   (404)
	 *   4. 각 상품 존재 (요청 배열 순서)   → PRODUCT_NOT_FOUND    (404)
	 *   5. 각 상품 재고 (요청 배열 순서)   → OUT_OF_STOCK         (400)
	 *   6. 포인트                         → INSUFFICIENT_POINT   (400)
	 *   7. flush/commit 시 버전 일치       → CONCURRENT_UPDATE    (409)
	 * </pre>
	 *
	 * <p>4번과 5번을 <b>두 번의 순회로 나눈 것은 의도</b>다. 한 번에 처리하면 "0번 항목은 재고 부족,
	 * 1번 항목은 미존재"일 때 {@code OUT_OF_STOCK}이 먼저 나가는데, 스펙 순서상 답은
	 * {@code PRODUCT_NOT_FOUND}다.
	 *
	 * <p>재고·포인트가 <b>동시에</b> 부족하면 {@code OUT_OF_STOCK}이 나간다 — 5번이 6번보다 앞이다.
	 *
	 * <p>7번은 검증이 아니다. 여기서 try/catch 하지 않는다 — commit은 프록시가 이 메서드를 빠져나온
	 * 뒤 수행하므로 잡히지 않는 경우가 있다. 전역 핸들러가 처리한다(스펙 §4.1, §5.1).
	 */
	@Transactional
	public OrderResponse createOrder(OrderCreateRequest request) {
		validateNoDuplicateProduct(request.items());

		Customer customer = customerRepository.findById(request.customerId())
				.orElseThrow(() -> new CustomerNotFoundException(request.customerId()));

		// 4. 상품 존재 확인 — 요청 배열 순서를 보존하는 List로 순회한다.
		//    Set으로 순회하면 순서가 비결정적이 되어 QA가 재현할 수 없다.
		List<Product> products = new ArrayList<>();
		for (OrderItemRequest item : request.items()) {
			products.add(productRepository.findById(item.productId())
					.orElseThrow(() -> new ProductNotFoundException(item.productId())));
		}

		// 5. 재고 확인 및 차감. 부족하면 Product가 OutOfStockException을 던진다.
		//    앞선 항목이 이미 차감됐더라도 트랜잭션 전체가 롤백되므로 재고는 원래대로 남는다.
		List<OrderItem> orderItems = new ArrayList<>();
		for (int i = 0; i < request.items().size(); i++) {
			OrderItemRequest itemRequest = request.items().get(i);
			Product product = products.get(i);
			product.decreaseStock(itemRequest.quantity());
			orderItems.add(OrderItem.create(product, itemRequest.quantity()));
		}

		Order order = Order.create(customer, orderItems);

		// 6. 포인트 확인 및 차감. 부족하면 Customer가 InsufficientPointException을 던진다.
		customer.usePoint(order.getTotalPrice());

		return OrderResponse.from(orderRepository.save(order));
	}

	/**
	 * 주문 취소(BR-10).
	 *
	 * <p>{@link Order#cancel()}을 <b>가장 먼저</b> 호출한다. 이미 취소된 주문이면 여기서 예외가 나고,
	 * 재고·포인트는 손대지 않은 상태로 끝난다(BR-9). 순서를 뒤집으면 이중 환급이 일어난다.
	 */
	@Transactional
	public OrderResponse cancelOrder(Long orderId) {
		Order order = orderRepository.findByIdWithDetails(orderId)
				.orElseThrow(() -> new OrderNotFoundException(orderId));

		order.cancel();

		for (OrderItem item : order.getOrderItems()) {
			item.getProduct().increaseStock(item.getQuantity());
		}
		order.getCustomer().refundPoint(order.getTotalPrice());

		return OrderResponse.from(order);
	}

	/** {@code customerId}가 있으면 해당 고객의 주문만. 없는 고객이면 404(계약 §1.3). */
	public List<OrderResponse> findAll(Long customerId) {
		if (customerId == null) {
			return orderRepository.findAllWithDetails().stream().map(OrderResponse::from).toList();
		}
		if (!customerRepository.existsById(customerId)) {
			throw new CustomerNotFoundException(customerId);
		}
		return orderRepository.findAllByCustomerIdWithDetails(customerId).stream()
				.map(OrderResponse::from)
				.toList();
	}

	public OrderResponse findById(Long orderId) {
		return OrderResponse.from(orderRepository.findByIdWithDetails(orderId)
				.orElseThrow(() -> new OrderNotFoundException(orderId)));
	}

	/**
	 * BR-2. <b>중복 라인을 합산하지 않는다</b> — 합산하면 재고 검증 대상 수량이 요청과 달라져
	 * 어떤 규칙이 적용됐는지 판정이 모호해진다.
	 */
	private void validateNoDuplicateProduct(List<OrderItemRequest> items) {
		Set<Long> seen = new HashSet<>();
		for (OrderItemRequest item : items) {
			if (!seen.add(item.productId())) {
				throw new DuplicateOrderItemException(item.productId());
			}
		}
	}
}
