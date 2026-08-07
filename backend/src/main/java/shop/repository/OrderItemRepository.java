package shop.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import shop.domain.OrderItem;

public interface OrderItemRepository extends JpaRepository<OrderItem, Long> {

	/**
	 * BR-17 — 상품 삭제 가능 여부. <b>{@code status = 'ORDERED'} 조건을 붙이지 않는다.</b>
	 * 취소된 주문의 라인도 참조로 세기 때문이다(스펙 §2.3) — 주문 이력은 보존 대상이고,
	 * 참조 상품이 사라지면 이력이 깨진다.
	 */
	boolean existsByProductId(Long productId);
}
