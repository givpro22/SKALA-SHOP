package shop.controller;

import java.net.URI;
import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
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
import shop.dto.CheckoutRequest;
import shop.dto.ErrorResponse;
import shop.dto.OrderResponse;
import shop.service.ShopCheckoutService;
import shop.service.ShopOrderService;

/**
 * 구매자 주문 API. <b>SHOPPER 전용이며 ADMIN이 호출하면 403이다.</b>
 *
 * <p>기존 {@code POST /api/orders}(관리자)와 <b>같은 {@code OrderResponse}를 반환하고 같은 테이블에
 * 저장한다.</b> {@code Order}에 출처 필드를 넣지 않았다 — 넣으면 취소·목록·통계가 전부 출처를
 * 분기해야 하고, 그 분기가 어긋나면 "관리자가 만든 주문을 구매자가 취소하지 못하는" 결함이 생긴다.
 */
@Tag(name = "구매", description = "구매자 신원 · 체크아웃 · 내 주문 내역")
@RestController
@RequestMapping("/api/shop/orders")
@RequiredArgsConstructor
public class ShopOrderController {

	private final ShopCheckoutService shopCheckoutService;
	private final ShopOrderService shopOrderService;

	@Operation(summary = "장바구니 체크아웃",
			description = """
					**장바구니를 읽어 주문을 만들고 카트를 비우는 것까지 하나의 트랜잭션이다. \
					실패하면 장바구니는 손대지 않은 상태로 남는다.** 그대로 재시도할 수 있고, \
					성공하면 카트 라인은 0건이 된다.

					요청 본문에 품목도 `customerId`도 없다 — 둘 다 서버가 토큰에서 결정한다. \
					클라이언트가 보낼 수 있으면 조작할 수 있고, `customerId`를 실어 보낸 구매자가 \
					남의 포인트로 주문하게 된다.

					**화면에서 확인한 총액과 서버 계산 총액이 다르면 409 `CART_STALE`로 거부한다 — \
					낙관적 락을 화면 계층에 적용한 것이다.** `@Version`이 DB 행의 버전을 검증하듯 \
					이 검사는 사용자가 본 화면의 버전을 검증한다. 같은 `expectedTotalPrice`로 \
					재시도하면 **반드시 다시 실패하므로** 카트를 다시 읽어야 한다 — \
					재시도로 풀리는 `CONCURRENT_UPDATE`와 다른 코드인 이유다.

					검증 순서는 고정되어 있고 먼저 실패한 것의 에러가 반환된다: \
					빈 카트 → 상품 존재 → **총액 일치** → 재고 → 포인트 → 동시성. \
					총액 검사가 재고·포인트보다 **앞**인 것이 중요하다. 뒤에 두면 가격이 오른 상황에서 \
					`INSUFFICIENT_POINT`가 나가고 사용자는 자기 포인트를 의심하지만 실제 원인은 가격 변동이다.

					재고 차감·포인트 차감·주문 저장은 관리자 주문 생성과 **같은 코드**를 쓴다.""",
			security = @SecurityRequirement(name = "bearerAuth"))
	@ApiResponses({
			@ApiResponse(responseCode = "201", description = "주문 생성됨. 카트는 비워진다"),
			@ApiResponse(responseCode = "400",
					description = "VALIDATION_ERROR / CART_EMPTY — 라인이 0건 / "
							+ "OUT_OF_STOCK — 담긴 뒤 재고가 줄었다 / INSUFFICIENT_POINT",
					content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
			@ApiResponse(responseCode = "401", description = "UNAUTHORIZED / TOKEN_EXPIRED",
					content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
			@ApiResponse(responseCode = "403", description = "FORBIDDEN — SHOPPER가 아니다",
					content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
			@ApiResponse(responseCode = "404", description = "PRODUCT_NOT_FOUND",
					content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
			@ApiResponse(responseCode = "409",
					description = "CART_STALE — 확인한 총액과 서버 총액이 다르다. 카트를 다시 읽어야 하며 "
							+ "같은 값으로 재시도하면 반드시 다시 실패한다 / "
							+ "CONCURRENT_UPDATE — 동시 요청과 충돌했다. 어떤 변경도 반영되지 않았으므로 재시도하면 된다",
					content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
	})
	@PostMapping
	public ResponseEntity<OrderResponse> checkout(
			@AuthenticationPrincipal String username,
			@Valid @RequestBody CheckoutRequest request) {
		OrderResponse response = shopCheckoutService.checkout(username, request.expectedTotalPrice());
		/*
		 * 주문의 정본 위치는 관리자 경로다. 구매자 경로는 그것에 소유권 필터를 씌운 뷰일 뿐이라
		 * /api/shop/orders/{id} 를 가리키지 않는다.
		 */
		return ResponseEntity
				.created(URI.create("/api/orders/" + response.orderId()))
				.body(response);
	}

	@Operation(summary = "내 주문 내역",
			description = """
					내 주문만 `orderedAt` 내림차순(동일 시각이면 `orderId` 내림차순)으로 반환한다.

					**순수 배열이다.** 개인 주문 건수는 상품 카탈로그와 달리 폭발하지 않으므로 \
					페이지네이션을 넣지 않았다 — 넣는 순간 래핑 응답 예외가 둘이 된다.

					구매자 프로필이 아직 없으면 빈 배열이며, 아무것도 생성하지 않는다.""",
			security = @SecurityRequirement(name = "bearerAuth"))
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "조회됨. 없으면 빈 배열"),
			@ApiResponse(responseCode = "401", description = "UNAUTHORIZED / TOKEN_EXPIRED",
					content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
			@ApiResponse(responseCode = "403", description = "FORBIDDEN — SHOPPER가 아니다",
					content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
	})
	@GetMapping
	public ResponseEntity<List<OrderResponse>> findMyOrders(@AuthenticationPrincipal String username) {
		return ResponseEntity.ok(shopOrderService.findMyOrders(username));
	}

	@Operation(summary = "내 주문 단건 조회",
			description = """
					**남의 주문에는 404 `ORDER_NOT_FOUND`를 반환한다. 403이 아니다.** \
					403은 "그 번호의 주문은 존재한다"를 알려주므로 주문 번호를 훑어 남의 주문 존재를 \
					확인할 수 있다. 없는 주문과 남의 주문이 구분되지 않아야 한다.""",
			security = @SecurityRequirement(name = "bearerAuth"))
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "조회됨"),
			@ApiResponse(responseCode = "400", description = "TYPE_MISMATCH",
					content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
			@ApiResponse(responseCode = "401", description = "UNAUTHORIZED / TOKEN_EXPIRED",
					content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
			@ApiResponse(responseCode = "403", description = "FORBIDDEN — SHOPPER가 아니다",
					content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
			@ApiResponse(responseCode = "404", description = "ORDER_NOT_FOUND — 없는 주문이거나 남의 주문",
					content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
	})
	@GetMapping("/{id}")
	public ResponseEntity<OrderResponse> findMyOrder(
			@AuthenticationPrincipal String username,
			@Parameter(description = "주문 id", example = "2") @PathVariable Long id) {
		return ResponseEntity.ok(shopOrderService.findMyOrder(username, id));
	}

	@Operation(summary = "내 주문 취소",
			description = """
					소유권을 확인한 뒤 **관리자 취소와 같은 코드**로 처리한다. 상태 전이도 부수 효과도 \
					동일하다 — 재고 복원 · 포인트 환급 · `ORDERED → CANCELED`가 하나의 트랜잭션이다.

					**이미 취소된 주문을 다시 취소하면 400 `ALREADY_CANCELED`다.** 허용하면 환급이 \
					두 번 일어나 포인트가 증식한다. 상태 검사는 재고·포인트를 건드리기 전에 한다.

					**남의 주문에는 404다. 403이 아니다** — 존재를 감추기 위해서다.""",
			security = @SecurityRequirement(name = "bearerAuth"))
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "취소됨"),
			@ApiResponse(responseCode = "400", description = "ALREADY_CANCELED / TYPE_MISMATCH",
					content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
			@ApiResponse(responseCode = "401", description = "UNAUTHORIZED / TOKEN_EXPIRED",
					content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
			@ApiResponse(responseCode = "403", description = "FORBIDDEN — SHOPPER가 아니다",
					content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
			@ApiResponse(responseCode = "404", description = "ORDER_NOT_FOUND — 없는 주문이거나 남의 주문",
					content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
			@ApiResponse(responseCode = "409",
					description = "CONCURRENT_UPDATE — 취소는 전혀 반영되지 않았고 주문은 ORDERED로 남는다",
					content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
	})
	@PostMapping("/{id}/cancel")
	public ResponseEntity<OrderResponse> cancel(
			@AuthenticationPrincipal String username,
			@Parameter(description = "취소할 주문 id", example = "2") @PathVariable Long id) {
		return ResponseEntity.ok(shopOrderService.cancelOwnOrder(username, id));
	}
}
