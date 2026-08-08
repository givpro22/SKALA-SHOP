package shop.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import shop.domain.Cart;
import shop.domain.Customer;
import shop.domain.Product;
import shop.dto.CartItemAddRequest;
import shop.dto.CartItemUpdateRequest;
import shop.dto.CartResponse;
import shop.exception.ProductNotFoundException;
import shop.repository.CartRepository;
import shop.repository.ProductRepository;

/**
 * 장바구니 비즈니스 로직. 도메인 스펙 §12.
 *
 * <p><b>모든 조작이 {@code CartResponse} 전체를 반환한다.</b> 라인 하나를 바꿔도 총액·
 * {@code checkoutable}·라인 수가 함께 바뀌므로 부분 응답을 주면 프론트가 반드시 재조회한다 —
 * 라운드트립 1회와 그 사이의 화면 불일치를 함께 없앤다.
 *
 * <p>수량 상한·재고 판정은 여기가 아니라 {@code CartItem}에 있다. 서비스에 두면 담기와 수량 변경
 * 두 경로에 같은 검사를 복제하게 되고, 언젠가 한쪽만 고쳐진다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CartService {

	private final CartRepository cartRepository;
	private final ProductRepository productRepository;
	private final ShopperProfileService shopperProfileService;

	/**
	 * 조회. <b>프로필도 카트도 만들지 않는다</b>(스펙 §8.4) — 없으면 빈 카트를 돌려준다.
	 *
	 * <p>여기가 3단 방어의 1단계다(스펙 §13.2). 라인마다 {@code availability}·현재 재고·
	 * {@code priceChanged}를 내리고 {@code checkoutable}을 계산해, 화면이 결제 버튼을 비활성화할
	 * 근거를 준다. 실패가 아니라 <b>예고</b>다.
	 */
	public CartResponse getCart(String username) {
		return shopperProfileService.findCustomer(username)
				.map(customer -> cartRepository.findByCustomerIdWithItems(customer.getId())
						.map(CartResponse::from)
						.orElseGet(() -> CartResponse.empty(customer.getId())))
				.orElseGet(CartResponse::empty);
	}

	/** BR-23 · BR-24 — 담기. 이미 담긴 상품이면 수량을 합산한다. */
	@Transactional
	public CartResponse addItem(String username, CartItemAddRequest request) {
		Cart cart = resolveCart(username);
		Product product = getProductOrThrow(request.productId());
		cart.addOrIncrease(product, request.quantity());
		return respond(cart);
	}

	/** 계약 §9.2.4 — 수량 변경. <b>절대값이다.</b> 카트에 없는 상품이면 404(BR-34). */
	@Transactional
	public CartResponse updateQuantity(String username, Long productId, CartItemUpdateRequest request) {
		Cart cart = resolveCart(username);
		Product product = getProductOrThrow(productId);
		cart.changeQuantity(product, request.quantity());
		return respond(cart);
	}

	/** BR-34 — 라인 삭제. 200 + 갱신된 카트 전체를 반환한다(204가 아니다, {@code [C-4]}). */
	@Transactional
	public CartResponse removeItem(String username, Long productId) {
		Cart cart = resolveCart(username);
		cart.removeItem(productId);
		return respond(cart);
	}

	/**
	 * BR-35 — 비우기. 라인만 지우고 <b>{@code Cart} 행은 남긴다.</b>
	 *
	 * <p>이미 비어 있어도 200이다 — 비우기는 멱등하며, 빈 카트를 비우는 것이 오류일 이유가 없다.
	 */
	@Transactional
	public CartResponse clear(String username) {
		Cart cart = resolveCart(username);
		cart.clearItems();
		return respond(cart);
	}

	/**
	 * 쓰기 뒤의 응답 생성. <b>매핑 전에 flush 한다.</b>
	 *
	 * <p>새로 담은 라인은 flush 전까지 {@code cartItemId}가 {@code null}이고 {@code addedAt}도
	 * 비어 있다 — {@code @CreatedDate}는 {@code @PrePersist} 시점에 채워지기 때문이다. 그대로 매핑하면
	 * 정렬 비교에서 <b>NPE</b>가 나고, 운 좋게 통과하더라도 응답의 {@code cartItemId}가 {@code null}로
	 * 나가 프론트가 라인을 식별하지 못한다.
	 *
	 * <p>조회({@link #getCart})에는 필요 없다 — 읽어 온 라인은 이미 두 값을 갖고 있다. 쓰기 경로에만
	 * 두어 조회가 불필요한 flush 비용을 지지 않게 했다.
	 */
	private CartResponse respond(Cart cart) {
		cartRepository.flush();
		return CartResponse.from(cart);
	}

	/**
	 * 쓰기 경로의 카트 확보. 프로필이 없으면 BR-31이 고객·프로필·빈 카트를 함께 만든다.
	 *
	 * <p>{@code orElseGet}으로 카트를 한 번 더 만들 수 있게 둔 것은 방어다 — 정상 경로에서는
	 * {@code resolveForWrite}가 이미 카트를 만들었으므로 도달하지 않는다. 관리자가
	 * {@code POST /api/customers}로 만든 고객이 뒤늦게 계정과 연결되는 경로가 생기면 여기가 받는다.
	 */
	private Cart resolveCart(String username) {
		Customer customer = shopperProfileService.resolveForWrite(username);
		return cartRepository.findByCustomerIdWithItems(customer.getId())
				.orElseGet(() -> cartRepository.save(Cart.create(customer)));
	}

	private Product getProductOrThrow(Long productId) {
		return productRepository.findById(productId)
				.orElseThrow(() -> new ProductNotFoundException(productId));
	}
}
