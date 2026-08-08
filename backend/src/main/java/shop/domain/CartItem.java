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
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import shop.exception.InvalidCartQuantityException;
import shop.exception.OutOfStockException;

/**
 * 장바구니 라인. 도메인 스펙 §9.3.
 *
 * <p><b>DB unique 제약 {@code (cart_id, product_id)}.</b> 같은 상품이 두 라인으로 존재할 수 없다 —
 * 담기 요청이 중복되면 {@link Cart#addOrIncrease}가 합산한다(BR-24). 애플리케이션 로직만으로
 * 막으면 동시 요청 두 건이 각각 "없음"을 읽고 둘 다 새 라인을 만들 수 있다.
 */
@Entity
@Table(name = "cart_item", uniqueConstraints = @UniqueConstraint(
		name = "uk_cart_item_cart_product", columnNames = { "cart_id", "product_id" }))
@EntityListeners(AuditingEntityListener.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CartItem {

	/** 계약 §9.2.3 · §9.2.4의 수량 상한. 넘기는 요청은 <b>잘라내지 않고 거부한다</b>. */
	public static final int MAX_QUANTITY = 99;

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "cart_id", nullable = false)
	private Cart cart;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "product_id", nullable = false)
	private Product product;

	@Column(nullable = false)
	private int quantity;

	/**
	 * <b>담은 시점 단가.</b> 가격 변동을 화면에 알리는 용도이며 <b>결제 금액이 아니다</b>(BR-27).
	 *
	 * <p>{@code OrderItem.orderPrice}와 다른 것이다. {@code orderPrice}는 결제에 쓰이는 확정
	 * 스냅샷이고 이 값은 "담을 때는 이 가격이었다"를 보여주기 위한 참고값이다. 둘을 같은 것으로
	 * 취급하면 <b>카트에 오래 둔 상품이 옛 가격으로 결제되는 경로</b>가 열리고, 상품 가격 인상이
	 * 카트에 담아둔 사람들에게 적용되지 않아 손실이 조용히 누적된다.
	 */
	@Column(nullable = false)
	private int unitPriceAtAdd;

	/** 화면 정렬 기준({@code addedAt DESC}). 합산 시에도 갱신하지 않는다 — 처음 담은 시점이다. */
	@CreatedDate
	@Column(nullable = false, updatable = false)
	private LocalDateTime addedAt;

	private CartItem(Cart cart, Product product, int quantity, int unitPriceAtAdd) {
		this.cart = cart;
		this.product = product;
		this.quantity = quantity;
		this.unitPriceAtAdd = unitPriceAtAdd;
	}

	/**
	 * 생성 시점의 {@code product.price}를 복사해 {@code unitPriceAtAdd}로 굳힌다.
	 *
	 * <p>생성 시에도 {@link #applyQuantity}를 거친다 — 검증 경로를 하나로 두지 않으면 담기와
	 * 수량 변경 중 한쪽에서만 상한·재고 검사가 빠진다.
	 */
	static CartItem create(Cart cart, Product product, int quantity) {
		CartItem item = new CartItem(cart, product, quantity, product.getPrice());
		item.applyQuantity(quantity, product.getStock());
		return item;
	}

	/**
	 * 시드 전용 생성. <b>재고 검증을 거치지 않는다</b>(스펙 §14.4).
	 *
	 * <p>"담은 뒤 재고가 줄어든 상태"는 정상 경로로 만들 수 없다 — {@link #applyQuantity}가
	 * {@code OUT_OF_STOCK}으로 거부하기 때문이다. 그 상태가 곧 재현 대상(`INSUFFICIENT_STOCK` 표시와
	 * {@code checkoutable: false})이므로 시드만 예외를 둔다. 이 메서드의 호출부는
	 * {@code LocalSeedRunner} 하나뿐이며, 이름에 {@code Unchecked}를 넣어 일반 경로에서 눈에 띄게 했다.
	 */
	public static CartItem createUnchecked(Cart cart, Product product, int quantity) {
		return new CartItem(cart, product, quantity, product.getPrice());
	}

	/**
	 * 수량을 절대값으로 설정한다. <b>검증 순서가 규칙이다</b>(BR-24).
	 *
	 * <ol>
	 *   <li>형식 — {@code 1 <= quantity <= 99} → {@code VALIDATION_ERROR}</li>
	 *   <li>재고 — {@code quantity <= stock} → {@code OUT_OF_STOCK}</li>
	 * </ol>
	 *
	 * <p>둘 다 위반이면 {@code VALIDATION_ERROR}가 나간다 — <b>형식 검증이 항상 먼저다.</b>
	 * 순서가 뒤집히면 "0개를 담아 달라"는 요청에 "재고가 부족합니다"가 나가 원인이 뒤바뀐다.
	 *
	 * <p>재고 검사는 <b>즉시 피드백을 위한 것이고 이후를 보장하지 않는다</b>(BR-26·BR-28) —
	 * 담은 뒤 재고가 줄 수 있고, 그때는 조회 응답의 {@code availability}가 알린다.
	 */
	void applyQuantity(int newQuantity, int stock) {
		if (newQuantity < 1 || newQuantity > MAX_QUANTITY) {
			throw new InvalidCartQuantityException(newQuantity);
		}
		if (newQuantity > stock) {
			throw new OutOfStockException(this.product.getName(), stock, newQuantity);
		}
		this.quantity = newQuantity;
	}
}
