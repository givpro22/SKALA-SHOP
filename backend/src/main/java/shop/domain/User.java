package shop.domain;

import java.time.LocalDateTime;

import org.hibernate.annotations.ColumnDefault;
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
	 *
	 * <h3>{@code @ColumnDefault} 는 장식이 아니라 마이그레이션 안전장치다</h3>
	 *
	 * <p>이 컬럼은 <b>나중에 추가됐다.</b> 이미 계정이 있는 DB 에 {@code ddl-auto=update} 로 올리면
	 * Hibernate 가 {@code ALTER TABLE ... ADD COLUMN role ... NOT NULL} 을 내는데, {@code DEFAULT} 가
	 * 없으면 MySQL 이 타입의 implicit default(= {@code ENUM} 의 첫 값)를 채운다. 그 결과
	 * <b>기존 계정 전원이 {@code ADMIN} 으로 승격됐다</b> — 실제 prod DB 에서 관측된 결함이며
	 * 근거는 {@link UserRole} 주석에 있다.
	 *
	 * <p>{@code DEFAULT} 를 명시하면 그 상황에서 기존 행이 {@code SHOPPER} 로 채워진다.
	 *
	 * <p><b>이것이 유일한 방어다.</b> {@link UserRole} 의 선언 순서를 바꿔 implicit default 를
	 * 안전한 쪽으로 돌리는 방법은 <b>통하지 않는다</b> — Hibernate 가 네이티브 {@code enum} 타입을
	 * 알파벳 순으로 생성해 {@code ADMIN} 이 항상 앞에 오기 때문이며, 생성 DDL 을 뽑아 확인했다.
	 * 근거는 {@link UserRole} 주석에 있다.
	 *
	 * <p>이 애너테이션이 <b>실제 DDL 에 반영되는지</b>는 {@code UserRoleSchemaDefaultTest} 가
	 * MySQL 방언으로 스키마를 생성해 확인한다. 애너테이션의 존재만 확인하면 매핑이 바뀌어
	 * 무시되는 경우를 놓친다.
	 *
	 * <p><b>애플리케이션 경로에는 영향이 없다</b> — {@link #create}/{@link #createAdmin} 이 항상 값을
	 * 넣으므로 이 기본값이 실제로 쓰이는 것은 스키마 마이그레이션 시점뿐이다.
	 */
	@Enumerated(EnumType.STRING)
	@ColumnDefault("'SHOPPER'")
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
