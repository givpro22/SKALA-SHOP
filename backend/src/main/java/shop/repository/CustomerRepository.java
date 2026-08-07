package shop.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import shop.domain.Customer;

public interface CustomerRepository extends JpaRepository<Customer, Long> {

	/** 등록 시 중복 검사(BR-15). */
	boolean existsByEmail(String email);

	/** 수정 시 중복 검사(BR-15). 자기 자신은 중복으로 보지 않는다. */
	boolean existsByEmailAndIdNot(String email, Long id);
}
