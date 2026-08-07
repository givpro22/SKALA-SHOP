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
import shop.exception.OutOfStockException;

/**
 * 상품. 도메인 스펙 §1.1.
 *
 * <p>setter를 열지 않고 의미 있는 메서드로만 상태를 바꾼다. 재고가 음수가 될 수 없다는 불변식을
 * 엔티티 안에 두면 어느 서비스가 호출하든 규칙이 지켜진다. Service에만 두면 새 호출 지점이
 * 생길 때마다 검증을 다시 써야 하고 언젠가 빠뜨린다.
 */
@Entity
@Table(name = "product", uniqueConstraints = @UniqueConstraint(name = "uk_product_name", columnNames = "name"))
@EntityListeners(AuditingEntityListener.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Product {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, length = 100)
	private String name;

	@Column(length = 500)
	private String description;

	@Column(nullable = false)
	private int price;

	@Column(nullable = false)
	private int stock;

	@CreatedDate
	@Column(nullable = false, updatable = false)
	private LocalDateTime createdAt;

	@LastModifiedDate
	@Column(nullable = false)
	private LocalDateTime updatedAt;

	/**
	 * 낙관적 락 버전(§7). JPA가 관리하므로 애플리케이션이 직접 대입하지 않는다.
	 *
	 * <p>getter를 만들지 않는다({@code AccessLevel.NONE}). 응답 DTO에 절대 노출되면 안 되는
	 * 내부 상태이며, 읽을 수단이 없으면 매퍼가 실수로 복사할 수도 없다.
	 */
	@Version
	@Getter(AccessLevel.NONE)
	private Long version;

	private Product(String name, String description, int price, int stock) {
		this.name = name;
		this.description = description;
		this.price = price;
		this.stock = stock;
	}

	public static Product create(String name, String description, int price, int stock) {
		return new Product(name, description, price, stock);
	}

	/**
	 * 전체 교체(PUT) 시맨틱. {@code description}이 {@code null}이면 {@code null}로 덮어쓴다.
	 *
	 * <p>여기서 바꾼 {@code price}·{@code stock}은 기존 주문에 소급되지 않는다(BR-12).
	 * {@code OrderItem.orderPrice}가 주문 시점 스냅샷이므로 자동으로 성립한다.
	 */
	public void update(String name, String description, int price, int stock) {
		this.name = name;
		this.description = description;
		this.price = price;
		this.stock = stock;
	}

	/** 주문 생성 시 재고 차감(BR-7). 부족하면 차감하지 않고 거부한다. */
	public void decreaseStock(int quantity) {
		if (this.stock < quantity) {
			throw new OutOfStockException(this.name, this.stock, quantity);
		}
		this.stock -= quantity;
	}

	/** 주문 취소 시 재고 복원(BR-10). */
	public void increaseStock(int quantity) {
		this.stock += quantity;
	}
}
