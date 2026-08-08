package shop.domain;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import shop.exception.CartItemNotFoundException;

/**
 * 장바구니. 도메인 스펙 §9.2. <b>고객당 정확히 하나</b>({@code customer_id} unique).
 *
 * <p><b>{@code Order}와 달리 빈 상태가 정상이다.</b> {@code orderItems}는 최소 1건이지만 카트는
 * 0건이 기본값이다. 빈 카트를 예외로 만들면 "담기 전"이라는 가장 흔한 상태가 오류가 된다.
 *
 * <p><b>{@code status} 필드가 없다.</b> 카트는 상태 기계가 아니라 편집 가능한 집합이다. 체크아웃은
 * 카트를 다른 상태로 바꾸는 것이 아니라 <b>비우는 것</b>이다(BR-29) — 상태를 남기면 "체크아웃된
 * 카트"가 계속 쌓이고 다음 담기에서 어느 카트를 쓸지 다시 판정해야 한다.
 *
 * <p><b>{@code @Version}을 걸지 않았다</b>(스펙 §13.1). 갱신 주체가 소유자 1명뿐이고,
 * 이중 체크아웃은 {@code Customer.@Version}이 이미 막는다 — 두 체크아웃이 겹치면 같은 고객 행의
 * 포인트를 경합해 한쪽이 409가 된다. 카트에 락을 걸면 같은 사용자가 두 탭에서 담기만 해도 409가
 * 나는데, 그 실패는 사용자가 이해할 수도 대응할 수도 없다.
 */
@Entity
@Table(name = "cart", uniqueConstraints = @UniqueConstraint(name = "uk_cart_customer", columnNames = "customer_id"))
@EntityListeners(AuditingEntityListener.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Cart {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@OneToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "customer_id", nullable = false)
	private Customer customer;

	@OneToMany(mappedBy = "cart", cascade = CascadeType.ALL, orphanRemoval = true)
	private List<CartItem> items = new ArrayList<>();

	@CreatedDate
	@Column(nullable = false, updatable = false)
	private LocalDateTime createdAt;

	@LastModifiedDate
	@Column(nullable = false)
	private LocalDateTime updatedAt;

	private Cart(Customer customer) {
		this.customer = customer;
	}

	public static Cart create(Customer customer) {
		return new Cart(customer);
	}

	/** 외부에서 컬렉션을 직접 조작하지 못하게 한다({@code Order.getOrderItems}와 같은 규약). */
	public List<CartItem> getItems() {
		return Collections.unmodifiableList(items);
	}

	/**
	 * BR-23 · BR-24 — 담기. <b>이미 담긴 상품이면 수량을 합산한다.</b>
	 *
	 * <p>주문은 중복 라인을 합산하지 <b>않는데</b>(BR-2) 카트는 합산한다. 목적이 다르기 때문이다 —
	 * 주문은 판정 대상이라 합산하면 재고 검증 수량이 요청과 달라져 어느 규칙이 발동했는지 모호해지고,
	 * 카트는 사용자의 편집 대상이라 같은 상품을 두 번 담았을 때 라인이 둘로 갈라지는 것은 사용자가
	 * 기대하지 않는 결과다.
	 *
	 * <p>합산 시 {@code unitPriceAtAdd}·{@code addedAt}은 <b>갱신하지 않는다</b> — 처음 담은 시점이
	 * 그대로 남아야 {@code priceChanged} 안내가 의미를 갖는다.
	 */
	public void addOrIncrease(Product product, int quantity) {
		Optional<CartItem> existing = findItem(product.getId());
		if (existing.isPresent()) {
			CartItem item = existing.get();
			item.applyQuantity(item.getQuantity() + quantity, product.getStock());
			return;
		}
		CartItem item = CartItem.create(this, product, quantity);
		this.items.add(item);
	}

	/**
	 * BR-34 — 카트에 없는 상품이면 404. <b>절대값으로 덮어쓴다. 더하지 않는다</b>(계약 §9.2.4).
	 *
	 * <p>담기 버튼은 누를 때마다 늘어야 하고 수량 입력창은 입력한 값이 되어야 한다. 둘을 같은
	 * 시맨틱으로 만들면 한쪽이 반드시 어색해진다.
	 */
	public void changeQuantity(Product product, int quantity) {
		CartItem item = findItem(product.getId())
				.orElseThrow(() -> new CartItemNotFoundException(product.getId()));
		item.applyQuantity(quantity, product.getStock());
	}

	/** BR-34 — 카트에 없는 상품이면 404. {@code orphanRemoval}이 실제 삭제를 수행한다. */
	public void removeItem(Long productId) {
		CartItem item = findItem(productId)
				.orElseThrow(() -> new CartItemNotFoundException(productId));
		this.items.remove(item);
	}

	/**
	 * BR-29 · BR-35 — 라인 전부 삭제. <b>{@code Cart} 행은 남는다.</b>
	 *
	 * <p>행까지 지우면 다음 담기에서 다시 만들어야 하고, 그 사이 동시 요청이 카트를 두 개 만들 수 있다.
	 * 이미 비어 있을 때 호출해도 오류가 아니다 — 비우기는 멱등하다.
	 */
	public void clearItems() {
		this.items.clear();
	}

	private Optional<CartItem> findItem(Long productId) {
		return this.items.stream()
				.filter(item -> item.getProduct().getId().equals(productId))
				.findFirst();
	}
}
