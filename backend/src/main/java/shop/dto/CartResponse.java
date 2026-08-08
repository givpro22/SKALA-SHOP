package shop.dto;

import java.util.Comparator;
import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import shop.domain.Cart;
import shop.domain.CartItem;
import shop.domain.CartItemAvailability;

/**
 * 계약 §9.3.3 — 장바구니 전체 상태.
 *
 * <p><b>모든 카트 조작이 이 응답 전체를 반환한다.</b> 라인 하나를 바꿔도 {@code totalPrice}·
 * {@code checkoutable}·{@code totalItemCount}가 함께 바뀌므로, 부분 응답을 주면 프론트가 반드시
 * 재조회한다 — 라운드트립 1회와 그 사이의 화면 불일치를 함께 없앤다. 라인 {@code DELETE}가
 * 204가 아닌 이유이며 {@code [호환성 쟁점 C-4]}다.
 *
 * <p>{@code totalPrice}는 <b>현재 단가 기준</b> 합계이며(BR-27),
 * {@code CheckoutRequest.expectedTotalPrice}에 그대로 넣는 값이다.
 */
@Schema(description = "장바구니 응답")
public record CartResponse(

		@Schema(description = "카트 소유자 고객 id. 구매자 프로필이 없으면 null",
				example = "1", nullable = true)
		Long customerId,

		@Schema(description = "라인 배열. 정렬은 addedAt DESC, cartItemId DESC (최근 담은 것이 위)")
		List<CartItemResponse> items,

		@Schema(description = "라인 수 (수량 합계가 아니다)", example = "3")
		int totalItemCount,

		@Schema(description = "수량 합계", example = "4")
		int totalQuantity,

		@Schema(description = "현재 단가 기준 합계. 체크아웃의 expectedTotalPrice에 그대로 넣는다",
				example = "84000")
		int totalPrice,

		@Schema(description = "availability가 AVAILABLE이 아닌 라인 수", example = "0")
		int unavailableItemCount,

		@Schema(description = "items가 비어있지 않고 모든 라인이 AVAILABLE", example = "true")
		boolean checkoutable) {

	/**
	 * 구매자 프로필이 아직 없는 계정. <b>조회는 아무것도 만들지 않는다</b>(스펙 §8.4) —
	 * 만들게 두면 목록 화면을 열기만 해도 고객 레코드가 늘어나고, 관리자 고객 목록이 한 번도
	 * 구매하지 않은 유령 고객으로 채워진다.
	 */
	public static CartResponse empty() {
		return new CartResponse(null, List.of(), 0, 0, 0, 0, false);
	}

	public static CartResponse empty(Long customerId) {
		return new CartResponse(customerId, List.of(), 0, 0, 0, 0, false);
	}

	public static CartResponse from(Cart cart) {
		/*
		 * 화면 정렬은 addedAt DESC, cartItemId DESC 다. **검증 순회 순서(productId ASC)와 다르다** —
		 * 목적이 다르기 때문이다. 화면 순서는 사용성, 검증 순서는 재현성을 위한 것이다(스펙 §12.1).
		 * cartItemId 2차 키가 없으면 같은 초에 담긴 라인들의 순서가 비결정적이 된다.
		 */
		List<CartItemResponse> items = cart.getItems().stream()
				.sorted(Comparator.comparing(CartItem::getAddedAt).reversed()
						.thenComparing(Comparator.comparing(CartItem::getId).reversed()))
				.map(CartItemResponse::from)
				.toList();

		int unavailable = (int) items.stream()
				.filter(item -> item.availability() != CartItemAvailability.AVAILABLE)
				.count();

		return new CartResponse(
				cart.getCustomer().getId(),
				items,
				items.size(),
				items.stream().mapToInt(CartItemResponse::quantity).sum(),
				items.stream().mapToInt(CartItemResponse::subtotal).sum(),
				unavailable,
				// 빈 카트는 체크아웃할 수 없다. && 순서를 뒤집으면 빈 카트에서 allMatch가 true라
				// checkoutable: true 가 되어 CART_EMPTY 로만 걸러지는 상태가 만들어진다.
				!items.isEmpty() && unavailable == 0);
	}
}
