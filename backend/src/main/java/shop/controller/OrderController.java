package shop.controller;

import java.net.URI;
import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
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
import shop.dto.ErrorResponse;
import shop.dto.OrderCreateRequest;
import shop.dto.OrderResponse;
import shop.service.OrderService;

@Tag(name = "주문", description = "주문 생성 및 취소")
@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class OrderController {

	private final OrderService orderService;

	@Operation(summary = "주문 생성",
			description = """
					재고 차감 · 포인트 차감 · 주문 저장을 **하나의 트랜잭션**으로 처리한다. \
					하나라도 실패하면 전부 롤백되므로 "포인트만 빠지고 주문이 없는" 상태는 만들어지지 않는다.

					검증은 고정된 순서로 이루어지며, 먼저 실패한 것의 에러가 반환된다: \
					항목 중복 → 고객 존재 → 상품 존재 → 재고 → 포인트. \
					**재고와 포인트가 동시에 부족하면 OUT_OF_STOCK이 반환된다.**

					동시 요청으로 재고/포인트 갱신이 충돌하면 409를 반환하며, 이때 어떤 변경도 반영되지 않는다.""",
			security = @SecurityRequirement(name = "bearerAuth"))
	@ApiResponses({
			@ApiResponse(responseCode = "201", description = "주문 생성됨"),
			@ApiResponse(responseCode = "400",
					description = "VALIDATION_ERROR / DUPLICATE_ORDER_ITEM / OUT_OF_STOCK / INSUFFICIENT_POINT",
					content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
			@ApiResponse(responseCode = "404", description = "CUSTOMER_NOT_FOUND / PRODUCT_NOT_FOUND",
					content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
			@ApiResponse(responseCode = "409",
					description = "CONCURRENT_UPDATE — 동시 요청으로 재고/포인트 갱신이 충돌했다. 어떤 변경도 반영되지 않았다",
					content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
			@ApiResponse(responseCode = "401", description = "UNAUTHORIZED / TOKEN_EXPIRED",
					content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
			@ApiResponse(responseCode = "403", description = "FORBIDDEN — 토큰은 유효하나 ADMIN이 아니다",
					content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
	})
	@PostMapping
	public ResponseEntity<OrderResponse> create(@Valid @RequestBody OrderCreateRequest request) {
		OrderResponse response = orderService.createOrder(request);
		return ResponseEntity
				.created(URI.create("/api/orders/" + response.orderId()))
				.body(response);
	}

	@Operation(summary = "주문 목록 조회",
			description = "orderedAt 내림차순(동일 시각이면 orderId 내림차순)으로 반환한다. "
					+ "customerId를 주면 해당 고객의 주문만 조회한다.")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "조회됨"),
			@ApiResponse(responseCode = "400", description = "TYPE_MISMATCH",
					content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
			@ApiResponse(responseCode = "404", description = "CUSTOMER_NOT_FOUND (customerId 지정 시 해당 고객 없음)",
					content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
	})
	@GetMapping
	public ResponseEntity<List<OrderResponse>> findAll(
			@Parameter(description = "조회할 고객 id. 생략하면 전체 주문", example = "1")
			@RequestParam(required = false) Long customerId) {
		return ResponseEntity.ok(orderService.findAll(customerId));
	}

	@Operation(summary = "주문 단건 조회")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "조회됨"),
			@ApiResponse(responseCode = "400", description = "TYPE_MISMATCH",
					content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
			@ApiResponse(responseCode = "404", description = "ORDER_NOT_FOUND",
					content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
	})
	@GetMapping("/{id}")
	public ResponseEntity<OrderResponse> findById(
			@Parameter(description = "주문 id", example = "1") @PathVariable Long id) {
		return ResponseEntity.ok(orderService.findById(id));
	}

	@Operation(summary = "주문 취소",
			description = """
					재고 복원 · 포인트 환급 · 상태 전이를 **하나의 트랜잭션**으로 처리한다. \
					요청 본문이 없다.

					**이미 취소된 주문을 다시 취소하면 400 ALREADY_CANCELED로 거부한다.** \
					허용하면 환급이 두 번 일어나 포인트가 증식한다. 상태 검사는 재고·포인트를 건드리기 전에 한다.

					동시 요청으로 갱신이 충돌하면 409를 반환하며, 이때 취소는 전혀 반영되지 않고 주문은 ORDERED로 남는다.""",
			security = @SecurityRequirement(name = "bearerAuth"))
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "취소됨"),
			@ApiResponse(responseCode = "400", description = "ALREADY_CANCELED / TYPE_MISMATCH",
					content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
			@ApiResponse(responseCode = "404", description = "ORDER_NOT_FOUND",
					content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
			@ApiResponse(responseCode = "409",
					description = "CONCURRENT_UPDATE — 어떤 변경도 반영되지 않았고 주문은 ORDERED로 남는다",
					content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
			@ApiResponse(responseCode = "401", description = "UNAUTHORIZED / TOKEN_EXPIRED",
					content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
			@ApiResponse(responseCode = "403", description = "FORBIDDEN — 토큰은 유효하나 ADMIN이 아니다",
					content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
	})
	@PostMapping("/{id}/cancel")
	public ResponseEntity<OrderResponse> cancel(
			@Parameter(description = "취소할 주문 id", example = "2") @PathVariable Long id) {
		return ResponseEntity.ok(orderService.cancelOrder(id));
	}
}
