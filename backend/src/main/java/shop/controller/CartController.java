package shop.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import shop.dto.CartItemAddRequest;
import shop.dto.CartItemUpdateRequest;
import shop.dto.CartResponse;
import shop.dto.ErrorResponse;
import shop.service.CartService;

/**
 * 장바구니 API. <b>SHOPPER 전용이며 ADMIN이 호출하면 403이다.</b>
 *
 * <p>모든 조작이 {@code CartResponse} <b>전체</b>를 반환한다 — 라인 하나를 바꿔도 총액과
 * {@code checkoutable}이 함께 바뀌므로, 부분 응답이나 204를 주면 프론트가 반드시 재조회하고
 * 그 사이 다른 탭이 카트를 바꾸면 화면이 어긋난다.
 *
 * <p>카트 소유자를 요청에서 받지 않는다. 서버가 토큰 → 구매자 프로필 → 고객으로 결정한다 —
 * 클라이언트가 보낼 수 있으면 <b>남의 카트를 조작</b>할 수 있다.
 */
@Tag(name = "장바구니", description = "담기 · 수량 변경 · 라인 삭제 · 비우기 (SHOPPER 전용)")
@RestController
@RequestMapping("/api/shop/cart")
@RequiredArgsConstructor
public class CartController {

	private final CartService cartService;

	@Operation(summary = "장바구니 조회",
			description = """
					내 장바구니 전체를 반환한다. 구매자 프로필이 없으면 **빈 카트**를 돌려주며 \
					아무것도 생성하지 않는다.

					**라인 금액은 항상 현재 상품 가격으로 계산한다.** 담은 시점 단가(`unitPriceAtAdd`)는 \
					`priceChanged` 판정을 위해 함께 내려가는 참고값이며 결제에 쓰이지 않는다 — \
					옛 가격으로 결제되면 상품 가격 인상이 카트에 담아둔 사람들에게 적용되지 않아 \
					손실이 조용히 누적된다.

					**장바구니는 재고를 예약하지 않는다.** 담긴 뒤 재고가 줄면 라인을 삭제하거나 수량을 \
					자동 조정하지 않고, `availability`(`INSUFFICIENT_STOCK` / `SOLD_OUT`)와 현재 `stock`을 \
					실어 보내며 `checkoutable: false`가 된다. 자동 조정하지 않는 이유는 사용자가 요청하지 \
					않은 수량으로 결제되는 것을 막기 위해서다.""",
			security = @SecurityRequirement(name = "bearerAuth"))
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "조회됨. 비어 있어도 200 + 빈 items"),
			@ApiResponse(responseCode = "401", description = "UNAUTHORIZED / TOKEN_EXPIRED",
					content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
			@ApiResponse(responseCode = "403", description = "FORBIDDEN — 토큰은 유효하나 SHOPPER가 아니다",
					content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
	})
	@GetMapping
	public ResponseEntity<CartResponse> getCart(@AuthenticationPrincipal String username) {
		return ResponseEntity.ok(cartService.getCart(username));
	}

	@Operation(summary = "장바구니에 담기",
			description = """
					**201이 아니라 200이고 `Location` 헤더가 없다.** 라인을 개별 조회하는 경로가 없어 \
					가리킬 URI가 없고, 같은 상품을 다시 담으면 새 라인이 아니라 **기존 라인의 수량이 는다** \
					— 생성이 아니라 갱신이다.

					수량은 **상대값**이다(기존 수량에 더해진다). 합산 결과가 99를 넘으면 \
					`VALIDATION_ERROR`, 재고를 넘으면 `OUT_OF_STOCK`이며 **둘 다 위반이면 \
					`VALIDATION_ERROR`가 나간다 — 형식 검증이 항상 먼저다.**

					합산 시 담은 시점 단가와 담은 시각은 갱신하지 않는다.

					구매자 프로필이 없으면 이 요청이 고객·프로필·빈 카트를 함께 만든다. \
					단, 계정 아이디와 같은 이메일의 고객이 이미 있으면 409로 거부한다 — \
					기존 고객에 자동 결합하면 남의 이메일로 가입해 그 고객의 포인트와 주문 이력을 \
					획득하는 경로가 열린다.""",
			security = @SecurityRequirement(name = "bearerAuth"))
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "담김 (201이 아니다). 갱신된 카트 전체를 반환"),
			@ApiResponse(responseCode = "400", description = "VALIDATION_ERROR — 수량이 1~99를 벗어남(합산 결과 포함) / "
					+ "OUT_OF_STOCK — 합산 수량이 현재 재고를 넘음",
					content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
			@ApiResponse(responseCode = "401", description = "UNAUTHORIZED / TOKEN_EXPIRED",
					content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
			@ApiResponse(responseCode = "403", description = "FORBIDDEN — SHOPPER가 아니다",
					content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
			@ApiResponse(responseCode = "404", description = "PRODUCT_NOT_FOUND",
					content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
			@ApiResponse(responseCode = "409",
					description = "SHOPPER_PROFILE_CONFLICT — 계정 아이디와 같은 이메일의 고객이 이미 존재한다",
					content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
	})
	@PostMapping("/items")
	public ResponseEntity<CartResponse> addItem(
			@AuthenticationPrincipal String username,
			@Valid @RequestBody CartItemAddRequest request) {
		return ResponseEntity.ok(cartService.addItem(username, request));
	}

	@Operation(summary = "장바구니 수량 변경",
			description = """
					수량은 **절대값**이다. 기존 수량에 더해지지 않는다 — 담기 버튼은 누를 때마다 늘어야 하고 \
					수량 입력창은 입력한 값이 되어야 하므로 두 시맨틱을 나눴다.

					**`quantity: 0`은 삭제가 아니라 400이다.** 0을 삭제로 받으면 수량 입력창에서 지웠다가 \
					다시 쓰는 흔한 조작이 라인 삭제가 된다. 삭제는 `DELETE`가 한다.""",
			security = @SecurityRequirement(name = "bearerAuth"))
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "변경됨. 갱신된 카트 전체를 반환"),
			@ApiResponse(responseCode = "400", description = "VALIDATION_ERROR — 수량이 1~99를 벗어남(0 포함) / "
					+ "OUT_OF_STOCK / TYPE_MISMATCH",
					content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
			@ApiResponse(responseCode = "401", description = "UNAUTHORIZED / TOKEN_EXPIRED",
					content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
			@ApiResponse(responseCode = "403", description = "FORBIDDEN — SHOPPER가 아니다",
					content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
			@ApiResponse(responseCode = "404", description = "CART_ITEM_NOT_FOUND — 내 카트에 없는 상품 / "
					+ "PRODUCT_NOT_FOUND",
					content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
	})
	@PutMapping("/items/{productId}")
	public ResponseEntity<CartResponse> updateQuantity(
			@AuthenticationPrincipal String username,
			@Parameter(description = "카트에 담긴 상품 id", example = "4") @PathVariable Long productId,
			@Valid @RequestBody CartItemUpdateRequest request) {
		return ResponseEntity.ok(cartService.updateQuantity(username, productId, request));
	}

	@Operation(summary = "장바구니 라인 삭제",
			description = """
					**204가 아니라 200이고 갱신된 카트 전체를 반환한다.** 카트는 엔티티 컬렉션이 아니라 \
					하나의 집합 상태이며, 라인 하나를 지워도 총액·`checkoutable`이 바뀐다. \
					204를 주면 프론트가 반드시 재조회하고, 그 사이 다른 탭이 카트를 바꾸면 화면이 어긋난다.

					(엔티티 삭제인 `DELETE /api/products/{id}`·`/api/customers/{id}`는 204 그대로다.)""",
			security = @SecurityRequirement(name = "bearerAuth"))
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "삭제됨 (204가 아니다). 갱신된 카트 전체를 반환"),
			@ApiResponse(responseCode = "400", description = "TYPE_MISMATCH",
					content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
			@ApiResponse(responseCode = "401", description = "UNAUTHORIZED / TOKEN_EXPIRED",
					content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
			@ApiResponse(responseCode = "403", description = "FORBIDDEN — SHOPPER가 아니다",
					content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
			@ApiResponse(responseCode = "404", description = "CART_ITEM_NOT_FOUND — 내 카트에 없는 상품",
					content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
	})
	@DeleteMapping("/items/{productId}")
	public ResponseEntity<CartResponse> removeItem(
			@AuthenticationPrincipal String username,
			@Parameter(description = "카트에서 뺄 상품 id", example = "4") @PathVariable Long productId) {
		return ResponseEntity.ok(cartService.removeItem(username, productId));
	}

	@Operation(summary = "장바구니 비우기",
			description = """
					라인을 전부 지운다. **카트 자체는 남는다** — 지우면 다음 담기에서 다시 만들어야 하고, \
					그 사이 동시 요청이 카트를 두 개 만들 수 있다.

					**이미 비어 있어도 200이다.** 비우기는 멱등하며, 빈 카트를 비우는 것이 오류일 이유가 없다.""",
			security = @SecurityRequirement(name = "bearerAuth"))
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "비워짐 (204가 아니다). 빈 카트를 반환"),
			@ApiResponse(responseCode = "401", description = "UNAUTHORIZED / TOKEN_EXPIRED",
					content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
			@ApiResponse(responseCode = "403", description = "FORBIDDEN — SHOPPER가 아니다",
					content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
	})
	@DeleteMapping
	public ResponseEntity<CartResponse> clear(@AuthenticationPrincipal String username) {
		return ResponseEntity.ok(cartService.clear(username));
	}
}
