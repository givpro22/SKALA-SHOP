package shop.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import shop.domain.Order;

public interface OrderRepository extends JpaRepository<Order, Long> {

	/**
	 * 목록 조회. <b>fetch join으로 N+1을 막는다</b> — 걸지 않으면 주문 수만큼 고객·상품 조회가 나간다.
	 *
	 * <p>정렬에 2차 키 {@code o.id DESC}가 붙는다(계약 §1.3). {@code orderedAt} 하나로는 같은 초에
	 * 들어온 주문의 순서가 비결정적이 되어 목록 캡처와 QA 판정이 매번 달라진다.
	 *
	 * <p>컬렉션 fetch join은 1개까지만 허용되므로 {@code orderItems} 하나만 걸고, 나머지는
	 * {@code @ManyToOne}이라 함께 조인해도 무방하다.
	 */
	@Query("select distinct o from Order o "
			+ "join fetch o.customer "
			+ "join fetch o.orderItems oi "
			+ "join fetch oi.product "
			+ "order by o.orderedAt desc, o.id desc")
	List<Order> findAllWithDetails();

	@Query("select distinct o from Order o "
			+ "join fetch o.customer c "
			+ "join fetch o.orderItems oi "
			+ "join fetch oi.product "
			+ "where c.id = :customerId "
			+ "order by o.orderedAt desc, o.id desc")
	List<Order> findAllByCustomerIdWithDetails(@Param("customerId") Long customerId);

	@Query("select distinct o from Order o "
			+ "join fetch o.customer "
			+ "join fetch o.orderItems oi "
			+ "join fetch oi.product "
			+ "where o.id = :id")
	Optional<Order> findByIdWithDetails(@Param("id") Long id);

	/**
	 * BR-18 — 고객 삭제 가능 여부. <b>{@code status} 조건을 붙이지 않는다.</b>
	 * 취소된 주문도 이력으로 세기 때문이다(스펙 §2.3).
	 */
	boolean existsByCustomerId(Long customerId);
}
