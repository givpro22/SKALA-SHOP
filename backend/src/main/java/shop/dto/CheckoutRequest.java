package shop.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

/**
 * 계약 §9.2.5 — 체크아웃 요청. <b>필드가 하나뿐이다.</b>
 *
 * <p><b>품목({@code items})도 {@code customerId}도 요청에 없다.</b> 둘 다 서버가 결정한다 —
 * 클라이언트가 보낼 수 있으면 조작할 수 있고, {@code customerId}를 실어 보낸 구매자가
 * <b>남의 포인트로 주문</b>하게 된다.
 *
 * <p>{@code expectedTotalPrice}는 <b>필수다.</b> 선택으로 두면 프론트가 생략하는 쪽으로 수렴하고,
 * 그 순간 {@code CART_STALE} 방어가 사라진다. 이 값은 {@code CartResponse.totalPrice}를 그대로
 * 담아 보낸다.
 */
@Schema(description = "체크아웃 요청")
public record CheckoutRequest(

		@Schema(description = "사용자가 화면에서 본 총액. 서버 재계산값과 다르면 409 CART_STALE로 거부한다. "
				+ "GET /api/shop/cart 응답의 totalPrice를 그대로 넣는다",
				example = "84000")
		@NotNull(message = "확인한 총액은 필수입니다.")
		@PositiveOrZero(message = "확인한 총액은 0원 이상이어야 합니다.")
		Integer expectedTotalPrice) {
}
