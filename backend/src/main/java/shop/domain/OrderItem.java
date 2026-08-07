package shop.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 주문 상세 라인. 도메인 스펙 §1.4. */
@Entity
@Table(name = "order_item")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OrderItem {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	// @ManyToOne의 기본값은 EAGER다. 명시하지 않으면 주문 목록 조회에서 매 건마다 조인이 나간다.
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "order_id", nullable = false)
	private Order order;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "product_id", nullable = false)
	private Product product;

	@Column(nullable = false)
	private int quantity;

	/**
	 * <b>주문 시점의 상품 단가 스냅샷</b>(BR-11). 조회할 때 {@code product.price}를 다시 읽지 않는다.
	 *
	 * <p>이 값을 저장하지 않고 조회 시 재계산하면, 상품 가격이 바뀐 뒤 주문을 취소할 때
	 * 차감했던 금액과 환급하는 금액이 달라진다.
	 */
	@Column(nullable = false)
	private int orderPrice;

	private OrderItem(Product product, int quantity, int orderPrice) {
		this.product = product;
		this.quantity = quantity;
		this.orderPrice = orderPrice;
	}

	/** 생성 시점의 {@code product.price}를 복사해 스냅샷으로 굳힌다. */
	public static OrderItem create(Product product, int quantity) {
		return new OrderItem(product, quantity, product.getPrice());
	}

	/** 라인 소계. DB에 저장하지 않고 필요할 때 계산한다(스펙 §1.4). */
	public int getSubtotal() {
		return orderPrice * quantity;
	}

	/** {@link Order#addOrderItem}에서만 호출한다. 양방향 연관관계의 반대편을 맞춘다. */
	void assignOrder(Order order) {
		this.order = order;
	}
}
