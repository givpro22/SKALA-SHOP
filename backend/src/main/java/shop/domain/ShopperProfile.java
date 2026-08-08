package shop.domain;

import java.time.LocalDateTime;

import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 로그인 계정이 쇼핑할 때의 구매자 신원. 도메인 스펙 §9.1. <b>생성 후 불변이다.</b>
 *
 * <p><b>왜 별도 엔티티인가.</b> {@code User}(로그인 주체)와 {@code Customer}(결제 주체)는 서로를
 * 참조하지 않는다. 장바구니의 종착점은 주문이고 주문은 {@code Customer}에 귀속되는데 로그인하는 것은
 * {@code User}다 — 이 단절을 메우는 것이 이 엔티티의 존재 이유다.
 *
 * <p><b>왜 {@code User.customer_id} 나 {@code Customer.user_id} 가 아닌가.</b> 관리자 계정에는
 * 그 값이 영원히 {@code null}이고, 관리자가 {@code POST /api/customers}로 만든 고객도 마찬가지다.
 * 엔티티의 절반이 쓰지 않는 필드는 곧 <b>"null이면 무슨 뜻인가"를 각 호출부가 다르게 해석하는 지점</b>이
 * 된다. 링크의 부재를 {@code null}이 아니라 <b>행의 부재</b>로 표현하면 해석의 여지가 없다.
 *
 * <p><b>양쪽 모두 unique다.</b> {@code user_id}만 걸면 한 고객이 여러 계정에 연결되어 두 계정이 같은
 * 포인트 지갑을 공유하고, {@code customer_id}만 걸면 한 계정이 여러 지갑을 갖는다. 둘 다 걸어야 1:1이다.
 *
 * <p><b>수정 메서드를 만들지 않는다.</b> 연결을 바꾸는 것은 결제 주체를 바꾸는 것이고, 그 경로가
 * 열려 있으면 주문 이력의 귀속이 사후에 달라진다.
 */
@Entity
@Table(name = "shopper_profile", uniqueConstraints = {
		@UniqueConstraint(name = "uk_shopper_profile_user", columnNames = "user_id"),
		@UniqueConstraint(name = "uk_shopper_profile_customer", columnNames = "customer_id") })
@EntityListeners(AuditingEntityListener.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ShopperProfile {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	/*
	 * @OneToOne 의 JPA 기본값도 EAGER 다. 명시하지 않으면 프로필을 읽을 때마다 User 와 Customer 가
	 * 같이 딸려온다. LAZY 프록시는 주인 쪽에서만 동작하는데, 역참조를 두지 않았으므로 여기가 주인이다.
	 */
	@OneToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "user_id", nullable = false)
	private User user;

	@OneToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "customer_id", nullable = false)
	private Customer customer;

	@CreatedDate
	@Column(nullable = false, updatable = false)
	private LocalDateTime createdAt;

	private ShopperProfile(User user, Customer customer) {
		this.user = user;
		this.customer = customer;
	}

	public static ShopperProfile create(User user, Customer customer) {
		return new ShopperProfile(user, customer);
	}
}
