package shop.domain;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import shop.exception.InvalidOrderStateException;

/**
 * 주문. 도메인 스펙 §1.3.
 *
 * <p>테이블명이 {@code orders}인 이유: {@code order}는 SQL 예약어라 H2/MySQL 양쪽에서 문제가 된다.
 *
 * <p>{@code @Version}을 걸지 않았다(스펙 §7.5). 주문은 생성 후 {@code status}가 한 번 바뀔 뿐이고,
 * 그 전이는 {@link #cancel()}의 상태 검사가 이미 막는다. <b>이중 취소를 막는 것은 락이 아니라
 * 상태 전이표다.</b>
 */
@Entity
@Table(name = "orders")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Order {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "customer_id", nullable = false)
	private Customer customer;

	@OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
	private List<OrderItem> orderItems = new ArrayList<>();

	/** 주문 시점에 계산해 저장한다(BR-11). 조회 시 재계산하지 않는다. */
	@Column(nullable = false)
	private int totalPrice;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private OrderStatus status;

	@Column(nullable = false)
	private LocalDateTime orderedAt;

	/** {@code ORDERED}면 {@code null}, {@code CANCELED}면 취소 시각. */
	private LocalDateTime canceledAt;

	private Order(Customer customer) {
		this.customer = customer;
		this.status = OrderStatus.ORDERED;
		this.orderedAt = LocalDateTime.now();
	}

	public static Order create(Customer customer, List<OrderItem> items) {
		Order order = new Order(customer);
		items.forEach(order::addOrderItem);
		order.totalPrice = items.stream().mapToInt(OrderItem::getSubtotal).sum();
		return order;
	}

	/**
	 * 양방향 편의 메서드. 컬렉션 추가와 반대편 세팅을 <b>함께</b> 한다 — 한쪽만 하면
	 * {@code cascade = ALL}로 저장은 되지만 {@code order_id}가 {@code null}로 들어간다.
	 */
	private void addOrderItem(OrderItem item) {
		this.orderItems.add(item);
		item.assignOrder(this);
	}

	/**
	 * 취소 상태로 전이한다(BR-9).
	 *
	 * <p>재고·포인트를 건드리기 <b>전에</b> 호출해야 한다. 이미 취소된 주문이면 여기서 예외가 나고,
	 * 그래야 이중 환급으로 포인트가 증식하지 않는다.
	 *
	 * <p>검사를 Service의 {@code if}로 대신하지 않는 이유: 다른 취소 경로가 생겼을 때 검사가
	 * 빠질 수 있다. 상태 전이 규칙은 상태를 가진 객체가 지킨다.
	 */
	public void cancel() {
		if (this.status != OrderStatus.ORDERED) {
			throw new InvalidOrderStateException(this.id);
		}
		this.status = OrderStatus.CANCELED;
		this.canceledAt = LocalDateTime.now();
	}

	/** 외부에서 컬렉션을 직접 조작하지 못하게 한다. */
	public List<OrderItem> getOrderItems() {
		return Collections.unmodifiableList(orderItems);
	}
}
