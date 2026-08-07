package shop.domain;

import java.time.LocalDateTime;

import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import shop.exception.InsufficientPointException;

/**
 * 고객. 도메인 스펙 §1.2.
 *
 * <p>{@code point}는 결제 수단 그 자체다(1포인트 = 1원). 잔액이 음수가 될 수 없다는 불변식을
 * 엔티티 안에 둔다.
 */
@Entity
@Table(name = "customer", uniqueConstraints = @UniqueConstraint(name = "uk_customer_email", columnNames = "email"))
@EntityListeners(AuditingEntityListener.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Customer {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, length = 50)
	private String name;

	@Column(nullable = false, length = 100)
	private String email;

	@Column(nullable = false)
	private int point;

	@CreatedDate
	@Column(nullable = false, updatable = false)
	private LocalDateTime createdAt;

	@LastModifiedDate
	@Column(nullable = false)
	private LocalDateTime updatedAt;

	/**
	 * 낙관적 락 버전(§7). Product와 같은 이유로 getter를 만들지 않는다.
	 *
	 * <p><b>Customer에도 거는 이유:</b> 같은 고객이 동시에 2건을 주문하면 두 트랜잭션이 같은
	 * 잔액을 읽고 각자 차감분을 써서 포인트가 한 번만 빠진다. 재고만 막고 포인트를 열어두면
	 * 동시성 제어가 절반만 성립한다.
	 */
	@Version
	@Getter(AccessLevel.NONE)
	private Long version;

	private Customer(String name, String email, int point) {
		this.name = name;
		this.email = email;
		this.point = point;
	}

	public static Customer create(String name, String email, int point) {
		return new Customer(name, email, point);
	}

	/**
	 * 이름·이메일만 바꾼다. <b>{@code point}는 여기서 바꿀 수 없다</b>(계약 §2.4) —
	 * 포인트 변경 경로는 충전과 주문/취소뿐이며, 임의로 덮어쓰는 경로를 만들지 않는다.
	 */
	public void update(String name, String email) {
		this.name = name;
		this.email = email;
	}

	/** 포인트 충전(BR-14). */
	public void chargePoint(int amount) {
		this.point += amount;
	}

	/** 주문 생성 시 포인트 차감(BR-7). 부족하면 차감하지 않고 거부한다. */
	public void usePoint(int amount) {
		if (this.point < amount) {
			throw new InsufficientPointException(this.point, amount);
		}
		this.point -= amount;
	}

	/** 주문 취소 시 포인트 환급(BR-10). */
	public void refundPoint(int amount) {
		this.point += amount;
	}
}
