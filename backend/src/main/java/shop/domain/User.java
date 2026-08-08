package shop.domain;

import java.time.LocalDateTime;

import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 로그인 계정. 계약 §8.
 *
 * <p><b>{@code Customer} 와 별개 엔티티다.</b> 이 앱의 화면은 상품 CRUD·고객 관리라
 * 로그인하는 주체가 구매 고객이 아니라 운영자다. 둘을 한 테이블에 합치면 "포인트를 가진
 * 로그인 계정"이라는 실재하지 않는 개념이 생기고, 고객 등록 화면이 회원가입이 되어
 * 기존 계약(§2.3 CustomerCreateRequest)까지 흔들린다.
 *
 * <p>테이블 이름이 {@code app_user} 인 것은 {@code user} 가 여러 DBMS 에서 예약어이기
 * 때문이다. H2 는 통과시키지만 MySQL·PostgreSQL 에서 문법 오류가 난다 — local 에서만
 * 뜨고 prod 에서 죽는 부류의 결함이라 이름으로 피한다.
 */
@Entity
@Table(name = "app_user", uniqueConstraints = @UniqueConstraint(name = "uk_user_username", columnNames = "username"))
@EntityListeners(AuditingEntityListener.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, length = 100)
	private String username;

	/**
	 * BCrypt 해시. <b>평문을 저장하지 않는다.</b>
	 *
	 * <p>getter 는 있지만 응답 DTO 로 나가는 경로가 없다 — {@code UserResponse} 에 필드
	 * 자체가 없고, 매퍼가 필드를 하나씩 옮기므로 엔티티에 필드가 늘어도 응답이 조용히
	 * 커지지 않는다({@code ProductResponse} 와 같은 규약).
	 */
	@Column(nullable = false, length = 100)
	private String password;

	/**
	 * 계정 역할(계약 §9.4.1, {@code [호환성 쟁점 C-1]}). <b>기존 엔티티에 대한 유일한 변경이다.</b>
	 *
	 * <p>관측 계약은 바뀌지 않는다 — {@code UserResponse} 는 여전히 3필드이고
	 * {@code SignupRequest}·{@code LoginResponse} 도 그대로다. 역할은 {@code GET /api/shop/me} 로만
	 * 나간다.
	 *
	 * <p>이 컬럼이 없으면 <b>구매자 계정의 토큰으로 {@code DELETE /api/products/1} 이 통과한다.</b>
	 * 구매 흐름을 여는 것은 로그인 계정의 성격이 둘로 갈라지는 것이라, 역할 없이는 열 수 없다.
	 */
	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private UserRole role;

	@CreatedDate
	@Column(nullable = false, updatable = false)
	private LocalDateTime createdAt;

	@LastModifiedDate
	@Column(nullable = false)
	private LocalDateTime updatedAt;

	private User(String username, String password, UserRole role) {
		this.username = username;
		this.password = password;
		this.role = role;
	}

	/**
	 * {@code password} 는 <b>이미 인코딩된 값</b>이어야 한다. 인코딩 책임은 Service 에 있다.
	 *
	 * <p><b>역할을 인자로 받지 않는다.</b> 가입({@code POST /api/auth/signup})으로 만들어진 계정은
	 * 항상 {@code SHOPPER} 다 — 요청으로 역할을 받으면 누구나 관리자가 된다(계약 §9.4.1).
	 * {@code ADMIN} 은 {@link #createAdmin} 으로만 만들어지고, 그 호출부는 {@code local} 시드 하나뿐이다.
	 */
	public static User create(String username, String encodedPassword) {
		return new User(username, encodedPassword, UserRole.SHOPPER);
	}

	/**
	 * 운영자 계정. <b>시드 전용이다</b> — API 로 도달하는 경로가 없다.
	 *
	 * <p>메서드를 따로 만든 이유: {@code create(username, password, role)} 하나로 두면 언젠가
	 * 요청 값이 {@code role} 자리에 그대로 흘러 들어간다. 관리자를 만드는 경로가 이름부터 다르면
	 * 그 실수가 호출부에서 눈에 띈다.
	 */
	public static User createAdmin(String username, String encodedPassword) {
		return new User(username, encodedPassword, UserRole.ADMIN);
	}
}
