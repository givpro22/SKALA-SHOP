package shop.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import shop.domain.Customer;
import shop.domain.Order;
import shop.dto.OrderResponse;
import shop.exception.OrderNotFoundException;
import shop.repository.OrderRepository;

/**
 * 구매자의 주문 조회·취소. 도메인 스펙 §11.1 · BR-33.
 *
 * <p><b>상태 전이 로직이 여기 없다.</b> 구매자 취소는 소유권 검사를 앞에 붙인 뒤 기존
 * {@link OrderService#cancelOrder}를 그대로 호출한다 — 새 전이도, 새 부수 효과도 없다.
 * 복제했다면 상태 전이표가 두 개가 되고 언젠가 한쪽만 고쳐진다.
 *
 * <p><b>남의 주문에는 404를 반환한다. 403이 아니다.</b> 403은 "그 번호의 주문은 존재한다"를
 * 알려주므로 주문 번호를 훑어 남의 주문 존재를 확인할 수 있다. {@code INVALID_CREDENTIALS}가
 * 아이디 존재 여부를 감춘 것과 같은 판단이다.
 *
 * <p><b>조회·취소 어느 쪽도 구매자 프로필을 만들지 않는다.</b> 프로필이 없으면 주문도 없으므로
 * 목록은 빈 배열, 단건·취소는 404다. 취소 시도만으로 고객 레코드가 생기는 것은 스펙 §8.4가 막는다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ShopOrderService {

	private final OrderRepository orderRepository;
	private final ShopperProfileService shopperProfileService;
	private final OrderService orderService;

	/**
	 * 내 주문 내역. <b>순수 배열이다</b>(계약 §0.2) — 개인 주문 건수는 상품 카탈로그와 달리
	 * 폭발하지 않고, 페이지네이션을 넣는 순간 §0.2의 예외가 둘이 된다.
	 *
	 * <p>정렬은 {@code orderedAt DESC, orderId DESC}로 §1.3과 같은 규칙이며, 같은 리포지토리
	 * 메서드를 쓰므로 어긋날 수 없다.
	 */
	public List<OrderResponse> findMyOrders(String username) {
		return shopperProfileService.findCustomer(username)
				.map(customer -> orderRepository.findAllByCustomerIdWithDetails(customer.getId()).stream()
						.map(OrderResponse::from)
						.toList())
				.orElseGet(List::of);
	}

	public OrderResponse findMyOrder(String username, Long orderId) {
		return OrderResponse.from(getOwnOrderOrThrow(username, orderId));
	}

	/**
	 * 구매자 취소. 소유권 검사 → 기존 {@code cancelOrder} 위임.
	 *
	 * <p>{@code orderService}는 다른 빈이므로 프록시를 거쳐 호출되고, 기본 전파로 이 트랜잭션에
	 * 합류한다. 같은 클래스 안에서 {@code this.}로 부르면 프록시를 지나지 않아 트랜잭션 설정이
	 * 적용되지 않는다 — 위임 대상을 별도 빈으로 둔 것이 여기서도 이득이다.
	 */
	@Transactional
	public OrderResponse cancelOwnOrder(String username, Long orderId) {
		getOwnOrderOrThrow(username, orderId);
		return orderService.cancelOrder(orderId);
	}

	/**
	 * BR-33 — 내 것이 아니면 <b>존재 자체를 감춘다.</b>
	 *
	 * <p>"없음"과 "남의 것"이 같은 응답이어야 한다. 두 경우에 다른 코드를 주면 그 차이가 곧
	 * "이 번호의 주문은 존재한다"는 정보가 된다.
	 */
	private Order getOwnOrderOrThrow(String username, Long orderId) {
		Customer customer = shopperProfileService.findCustomer(username)
				.orElseThrow(() -> new OrderNotFoundException(orderId));

		Order order = orderRepository.findByIdWithDetails(orderId)
				.orElseThrow(() -> new OrderNotFoundException(orderId));

		if (!order.getCustomer().getId().equals(customer.getId())) {
			throw new OrderNotFoundException(orderId);
		}
		return order;
	}
}
