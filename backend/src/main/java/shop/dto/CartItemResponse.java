package shop.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import shop.domain.CartItem;
import shop.domain.CartItemAvailability;
import shop.domain.Product;

/**
 * 계약 §9.3.4 — 장바구니 라인.
 *
 * <p><b>{@code subtotal}은 {@code unitPrice × quantity}이지 {@code unitPriceAtAdd}가 아니다</b>(BR-27).
 * 담을 때의 가격으로 결제하면 상품 가격 인상이 카트에 담아둔 사람들에게 적용되지 않아
 * <b>손실이 조용히 누적된다.</b> {@code unitPriceAtAdd}는 {@code priceChanged}를 계산해 화면에
 * 알리기 위한 참고값으로만 함께 내려간다.
 *
 * <p>{@code stock}·{@code availability}는 <b>조회 시점에 재계산</b>된다(BR-28). 담긴 뒤 재고가
 * 줄어도 라인을 삭제하거나 수량을 자동 조정하지 않는다 — 사용자가 요청하지 않은 수량으로 결제되는
 * 것을 막기 위해서다. 3개를 담았는데 1개만 사는 것은 사용자의 결정이어야 한다.
 */
@Schema(description = "장바구니 라인")
public record CartItemResponse(

		@Schema(description = "카트 라인 id", example = "1")
		Long cartItemId,

		@Schema(description = "상품 id", example = "4")
		Long productId,

		@Schema(description = "현재 상품명", example = "USB-C 허브")
		String productName,

		@Schema(description = "상품 이미지 주소. 없으면 null", example = "/products/hub.svg", nullable = true)
		String imageUrl,

		@Schema(description = "현재 단가. 결제에 쓰이는 값이다", example = "45000")
		int unitPrice,

		@Schema(description = "담은 시점 단가. 참고값이며 결제에 쓰이지 않는다", example = "45000")
		int unitPriceAtAdd,

		@Schema(description = "담은 뒤 가격이 바뀌었는지", example = "false")
		boolean priceChanged,

		@Schema(description = "담긴 수량", example = "1")
		int quantity,

		@Schema(description = "unitPrice × quantity", example = "45000")
		int subtotal,

		@Schema(description = "조회 시점의 현재 재고", example = "15")
		int stock,

		@Schema(description = "구매 가능 여부. INSUFFICIENT_STOCK은 '수량을 줄이면 살 수 있다', "
				+ "SOLD_OUT은 '지금은 살 수 없다'로 안내가 다르다",
				example = "AVAILABLE",
				allowableValues = { "AVAILABLE", "INSUFFICIENT_STOCK", "SOLD_OUT" })
		CartItemAvailability availability) {

	public static CartItemResponse from(CartItem item) {
		Product product = item.getProduct();
		int unitPrice = product.getPrice();
		int quantity = item.getQuantity();
		return new CartItemResponse(
				item.getId(),
				product.getId(),
				product.getName(),
				product.getImageUrl(),
				unitPrice,
				item.getUnitPriceAtAdd(),
				unitPrice != item.getUnitPriceAtAdd(),
				quantity,
				unitPrice * quantity,
				product.getStock(),
				CartItemAvailability.of(product.getStock(), quantity));
	}
}
