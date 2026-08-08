package shop.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import shop.domain.Cart;

public interface CartRepository extends JpaRepository<Cart, Long> {

	/**
	 * 카트 조회. <b>fetch join으로 N+1을 막는다</b>(스펙 §10.1).
	 *
	 * <p>라인 10건짜리 카트가 상품을 10번 따로 조회하면 화면이 눈에 띄게 느려진다. 카트 응답은
	 * 라인마다 현재 단가·재고·상품명을 필요로 하므로 상품 접근이 <b>반드시</b> 일어난다 —
	 * 지연 로딩이 우연히 피해가는 경로가 없다.
	 *
	 * <p>{@code left join}인 이유: 빈 카트가 정상 상태다(스펙 §9.2). {@code join fetch}로 두면
	 * 라인이 0건인 카트가 결과에서 사라져 "카트가 없다"와 구분되지 않는다.
	 */
	@Query("select distinct c from Cart c "
			+ "left join fetch c.items i "
			+ "left join fetch i.product "
			+ "where c.customer.id = :customerId")
	Optional<Cart> findByCustomerIdWithItems(@Param("customerId") Long customerId);

	/** 라인이 필요 없는 경로(배지 개수 계산 전 존재 확인 등). */
	Optional<Cart> findByCustomerId(Long customerId);
}
