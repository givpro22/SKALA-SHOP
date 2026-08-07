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
import shop.exception.CustomerNotFoundException;
import shop.exception.DuplicateEmailException;
import shop.repository.CustomerRepository;

/**
 * 고객 비즈니스 로직. 클래스에 {@code readOnly = true}, 쓰기 메서드에만 {@code @Transactional}.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CustomerService {

	private final CustomerRepository customerRepository;

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

	@Transactional
	public void delete(Long id) {
		Customer customer = getCustomerOrThrow(id);
		// TODO(#6): Order 도입 후 주문 이력 검사를 추가한다 — 1건이라도 있으면
		//           CustomerHasOrdersException(BR-18). 취소된 주문도 이력으로 센다.
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
