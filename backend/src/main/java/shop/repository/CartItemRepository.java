package shop.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import shop.domain.CartItem;

public interface CartItemRepository extends JpaRepository<CartItem, Long> {

	/**
	 * BR-30 — 상품 삭제 시 그 상품을 참조하는 <b>모든 카트</b>의 라인을 지운다.
	 *
	 * <p><b>이 메서드가 없으면 카트에 담긴 상품을 지울 때 FK 위반으로 500이 난다.</b>
	 * 삭제 가능 여부의 판정 대상은 여전히 {@code OrderItem}만이고({@code PRODUCT_IN_USE}),
	 * {@code CartItem}은 판정 대상이 아니다 — 넣으면 <b>다른 사람이 장바구니에 담아둔 것만으로
	 * 관리자가 상품을 삭제하지 못한다.</b> 카트는 구매 의사 표시일 뿐 이력이 아니다.
	 *
	 * <p>여러 사용자의 카트에 걸쳐 있어 벌크 삭제로 처리한다. {@code clearAutomatically}로 1차
	 * 캐시를 비우는 이유: 벌크 연산은 영속성 컨텍스트를 우회하므로, 같은 트랜잭션에 이미 로드된
	 * {@code CartItem}이 남아 있으면 이후 조회가 삭제된 행을 되살려 본다.
	 */
	@Modifying(clearAutomatically = true, flushAutomatically = true)
	@Query("delete from CartItem ci where ci.product.id = :productId")
	int deleteByProductId(@Param("productId") Long productId);

	/** 고객 삭제 시 그 고객 카트의 라인을 함께 지운다(스펙 §10.2). */
	@Modifying(clearAutomatically = true, flushAutomatically = true)
	@Query("delete from CartItem ci where ci.cart.id = :cartId")
	int deleteByCartId(@Param("cartId") Long cartId);
}
