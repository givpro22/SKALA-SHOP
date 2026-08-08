package shop.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import shop.domain.ShopperProfile;

public interface ShopperProfileRepository extends JpaRepository<ShopperProfile, Long> {

	/**
	 * 로그인 계정 → 구매자 신원. <b>토큰이 들고 있는 것은 {@code username} 하나</b>이므로 이 경로가
	 * 모든 shop 요청의 첫 단계다.
	 *
	 * <p>{@code join fetch}로 {@code user}·{@code customer}를 함께 읽는다. 둘 다 LAZY라 걸지 않으면
	 * 프로필 하나를 읽는 데 쿼리가 세 번 나가고, 이 조회는 shop 요청마다 발생한다.
	 */
	@Query("select p from ShopperProfile p "
			+ "join fetch p.user u "
			+ "join fetch p.customer "
			+ "where u.username = :username")
	Optional<ShopperProfile> findByUsername(@Param("username") String username);

	/** 고객 삭제 시 함께 지울 프로필을 찾는다(스펙 §10.2). */
	Optional<ShopperProfile> findByCustomerId(Long customerId);
}
