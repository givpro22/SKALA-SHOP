package shop.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import shop.domain.Customer;
import shop.domain.User;
import shop.domain.UserRole;

/**
 * 계약 §9.3.2 — 로그인한 사람이 쇼핑에서 누구인가.
 *
 * <p><b>{@code GET /api/auth/me}(§8.3)는 손대지 않는다.</b> 여전히
 * {@code {userId, username, createdAt}} 3필드다. 역할과 구매자 신원이 필요해진 것은 새 화면이므로
 * <b>새 엔드포인트가 그것을 나른다</b> — 기존 응답에 필드를 더하면 프론트 타입과 QA 판정표가 함께 바뀐다.
 *
 * <p>{@code cartItemCount}가 있어 헤더의 장바구니 배지를 그리려고 카트 전체를 불러올 필요가 없다.
 *
 * <p>프로필이 없으면 {@code customerId}·{@code customerName}·{@code point}가 {@code null}이고
 * {@code cartItemCount}는 {@code 0}이다. <b>키는 항상 존재한다</b>(계약 §0.4) — 값이 없을 때 키를
 * 생략하면 프론트 타입이 흔들린다.
 */
@Schema(description = "구매자 신원 응답")
public record ShopperResponse(

		@Schema(description = "계정 id", example = "2")
		Long userId,

		@Schema(description = "계정 아이디", example = "kim@skala.shop")
		String username,

		@Schema(description = "역할. 대문자 문자열", example = "SHOPPER",
				allowableValues = { "ADMIN", "SHOPPER" })
		UserRole role,

		@Schema(description = "구매자 고객 id. 프로필이 없으면 null", example = "1", nullable = true)
		Long customerId,

		@Schema(description = "구매자 이름. 프로필이 없으면 null", example = "김스칼라", nullable = true)
		String customerName,

		@Schema(description = "현재 보유 포인트. 프로필이 없으면 null", example = "1911000", nullable = true)
		Integer point,

		@Schema(description = "장바구니 라인 수. 프로필이 없으면 0", example = "3")
		int cartItemCount) {

	/** 구매자 신원이 아직 없는 계정(관리자 포함). */
	public static ShopperResponse withoutProfile(User user) {
		return new ShopperResponse(user.getId(), user.getUsername(), user.getRole(),
				null, null, null, 0);
	}

	public static ShopperResponse of(User user, Customer customer, int cartItemCount) {
		return new ShopperResponse(user.getId(), user.getUsername(), user.getRole(),
				customer.getId(), customer.getName(), customer.getPoint(), cartItemCount);
	}
}
