package shop.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import shop.domain.User;
import shop.domain.UserRole;

public interface UserRepository extends JpaRepository<User, Long> {

	Optional<User> findByUsername(String username);

	boolean existsByUsername(String username);

	/**
	 * 계약 §9.4.5 — {@code prod} ADMIN 부트스트랩의 판정 근거.
	 *
	 * <p>조건을 "최초 기동"이 아니라 <b>계정 수</b>로 잡았기 때문에 필요한 조회다. "최초"는 무엇이
	 * 최초인지 판정할 수 없어 별도 마커가 필요해지지만, 0건 여부는 이 한 줄로 확인된다 —
	 * 그 덕분에 부트스트랩이 멱등해지고 관리자 계정이 지워져도 재기동으로 복구된다.
	 */
	long countByRole(UserRole role);
}
