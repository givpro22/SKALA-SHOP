package shop.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import shop.domain.Customer;
import shop.dto.CustomerCreateRequest;
import shop.dto.CustomerResponse;
import shop.dto.CustomerUpdateRequest;
import shop.dto.PointChargeRequest;
import shop.exception.CustomerHasOrdersException;
import shop.exception.CustomerNotFoundException;
import shop.exception.DuplicateEmailException;
import shop.repository.CartItemRepository;
import shop.repository.CartRepository;
import shop.repository.CustomerRepository;
import shop.repository.OrderRepository;
import shop.repository.ShopperProfileRepository;

/**
 * 고객 비즈니스 로직. 클래스에 {@code readOnly = true}, 쓰기 메서드에만 {@code @Transactional}.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CustomerService {

	private final CustomerRepository customerRepository;
	private final OrderRepository orderRepository;
	private final CartRepository cartRepository;
	private final CartItemRepository cartItemRepository;
	private final ShopperProfileRepository shopperProfileRepository;

	@Transactional
	public CustomerResponse create(CustomerCreateRequest request) {
		if (customerRepository.existsByEmail(request.email())) {
			throw new DuplicateEmailException(request.email());
		}
		Customer customer = customerRepository.save(
				Customer.create(request.name(), request.email(), request.pointOrZero()));
		return CustomerResponse.from(customer);
	}

	public List<CustomerResponse> findAll() {
		return customerRepository.findAll().stream()
				.map(CustomerResponse::from)
				.toList();
	}

	public CustomerResponse findById(Long id) {
		return CustomerResponse.from(getCustomerOrThrow(id));
	}

	/**
	 * 이름·이메일만 바꾼다. 요청 DTO에 {@code point}가 없으므로 이 경로로 포인트가 변경될 수 없다.
	 */
	@Transactional
	public CustomerResponse update(Long id, CustomerUpdateRequest request) {
		Customer customer = getCustomerOrThrow(id);
		if (customerRepository.existsByEmailAndIdNot(request.email(), id)) {
			throw new DuplicateEmailException(request.email());
		}
		customer.update(request.name(), request.email());
		return CustomerResponse.from(customer);
	}

	/**
	 * BR-18. 주문 이력이 있는 고객은 삭제할 수 없다.
	 *
	 * <p>{@code status} 조건을 붙이지 않는다 — <b>취소된 주문도 이력으로 센다</b>(스펙 §2.3).
	 *
	 * <p><b>카트·구매자 프로필은 판정 대상이 아니라 정리 대상이다</b>(스펙 §10.2, 2026-08-08 확장).
	 * 상품 삭제에서 {@code CartItem}을 다룬 것과 같은 판단이다 — 장바구니는 이력이 아니므로 삭제를
	 * 막을 근거가 되지 못하고, 그대로 두면 FK 위반으로 500이 난다. 삭제 순서는 참조의 역순
	 * (라인 → 카트 → 프로필 → 고객)이며 전부 한 트랜잭션이다.
	 */
	@Transactional
	public void delete(Long id) {
		Customer customer = getCustomerOrThrow(id);
		if (orderRepository.existsByCustomerId(id)) {
			throw new CustomerHasOrdersException(id);
		}
		cartRepository.findByCustomerId(id).ifPresent(cart -> {
			cartItemRepository.deleteByCartId(cart.getId());
			cartRepository.delete(cart);
		});
		shopperProfileRepository.findByCustomerId(id).ifPresent(shopperProfileRepository::delete);
		customerRepository.delete(customer);
	}

	/** BR-14. 멱등하지 않다 — 두 번 호출하면 두 번 충전된다. */
	@Transactional
	public CustomerResponse chargePoint(Long id, PointChargeRequest request) {
		Customer customer = getCustomerOrThrow(id);
		customer.chargePoint(request.amount());
		return CustomerResponse.from(customer);
	}

	private Customer getCustomerOrThrow(Long id) {
		return customerRepository.findById(id)
				.orElseThrow(() -> new CustomerNotFoundException(id));
	}
}
