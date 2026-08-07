package shop.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import shop.domain.Product;

public interface ProductRepository extends JpaRepository<Product, Long> {

	/** 등록 시 중복 검사(BR-16). */
	boolean existsByName(String name);

	/**
	 * 수정 시 중복 검사(BR-16). <b>자기 자신은 중복으로 보지 않는다.</b>
	 *
	 * <p>{@code existsByName}을 그대로 쓰면 이름을 바꾸지 않고 PUT했을 때 자기 자신과 충돌해
	 * 409가 난다.
	 */
	boolean existsByNameAndIdNot(String name, Long id);
}
