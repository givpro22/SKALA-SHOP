package shop.controller;

import java.net.URI;
import java.util.List;

import org.springframework.http.ResponseEntity;
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
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import shop.dto.CustomerCreateRequest;
import shop.dto.CustomerResponse;
import shop.dto.CustomerUpdateRequest;
import shop.dto.ErrorResponse;
import shop.dto.PointChargeRequest;
import shop.service.CustomerService;

@Tag(name = "고객", description = "고객 등록·조회·수정·삭제 및 포인트 충전")
@RestController
@RequestMapping("/api/customers")
@RequiredArgsConstructor
public class CustomerController {

	private final CustomerService customerService;

	@Operation(summary = "고객 등록",
			description = "고객을 등록한다. 이메일이 이미 존재하면 409로 거부한다(BR-15). "
					+ "point를 생략하면 0으로 시작한다.")
	@ApiResponses({
			@ApiResponse(responseCode = "201", description = "등록됨"),
			@ApiResponse(responseCode = "400", description = "VALIDATION_ERROR",
					content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
			@ApiResponse(responseCode = "409", description = "DUPLICATE_EMAIL",
					content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
	})
	@PostMapping
	public ResponseEntity<CustomerResponse> create(@Valid @RequestBody CustomerCreateRequest request) {
		CustomerResponse response = customerService.create(request);
		return ResponseEntity
				.created(URI.create("/api/customers/" + response.customerId()))
				.body(response);
	}

	@Operation(summary = "고객 목록 조회",
			description = "전체 고객을 배열로 반환한다. 결과가 없으면 200 + 빈 배열이다.")
	@ApiResponse(responseCode = "200", description = "조회됨")
	@GetMapping
	public ResponseEntity<List<CustomerResponse>> findAll() {
		return ResponseEntity.ok(customerService.findAll());
	}

	@Operation(summary = "고객 단건 조회")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "조회됨"),
			@ApiResponse(responseCode = "400", description = "TYPE_MISMATCH",
					content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
			@ApiResponse(responseCode = "404", description = "CUSTOMER_NOT_FOUND",
					content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
	})
	@GetMapping("/{id}")
	public ResponseEntity<CustomerResponse> findById(
			@Parameter(description = "고객 id", example = "1") @PathVariable Long id) {
		return ResponseEntity.ok(customerService.findById(id));
	}

	@Operation(summary = "고객 수정",
			description = "이름과 이메일만 변경한다. 요청에 point를 넣어도 무시된다 — 포인트 변경 경로는 "
					+ "충전과 주문/취소뿐이다. 동시 요청으로 같은 고객 행의 갱신이 충돌하면 409를 반환하며, "
					+ "이때 어떤 변경도 반영되지 않는다.")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "수정됨"),
			@ApiResponse(responseCode = "400", description = "VALIDATION_ERROR / TYPE_MISMATCH",
					content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
			@ApiResponse(responseCode = "404", description = "CUSTOMER_NOT_FOUND",
					content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
			@ApiResponse(responseCode = "409", description = "DUPLICATE_EMAIL / CONCURRENT_UPDATE",
					content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
	})
	@PutMapping("/{id}")
	public ResponseEntity<CustomerResponse> update(
			@Parameter(description = "고객 id", example = "1") @PathVariable Long id,
			@Valid @RequestBody CustomerUpdateRequest request) {
		return ResponseEntity.ok(customerService.update(id, request));
	}

	@Operation(summary = "고객 삭제",
			description = "주문 이력이 있는 고객은 삭제할 수 없다(BR-18). 취소된 주문도 이력으로 센다. "
					+ "성공 시 204이며 본문이 없다.")
	@ApiResponses({
			@ApiResponse(responseCode = "204", description = "삭제됨 (본문 없음)"),
			@ApiResponse(responseCode = "400", description = "TYPE_MISMATCH",
					content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
			@ApiResponse(responseCode = "404", description = "CUSTOMER_NOT_FOUND",
					content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
			@ApiResponse(responseCode = "409", description = "CUSTOMER_HAS_ORDERS",
					content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
	})
	@DeleteMapping("/{id}")
	public ResponseEntity<Void> delete(
			@Parameter(description = "고객 id", example = "1") @PathVariable Long id) {
		customerService.delete(id);
		return ResponseEntity.noContent().build();
	}

	@Operation(summary = "포인트 충전",
			description = "보유 포인트를 증가시킨다(BR-14). PUT이 아닌 POST이며 멱등하지 않다 — "
					+ "두 번 호출하면 두 번 충전된다. 성공 시 201이 아니라 200이다. "
					+ "동시 요청으로 같은 고객 행의 갱신이 충돌하면 409를 반환한다.")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "충전됨"),
			@ApiResponse(responseCode = "400", description = "VALIDATION_ERROR (amount가 1 미만) / TYPE_MISMATCH",
					content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
			@ApiResponse(responseCode = "404", description = "CUSTOMER_NOT_FOUND",
					content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
			@ApiResponse(responseCode = "409", description = "CONCURRENT_UPDATE",
					content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
	})
	@PostMapping("/{id}/points")
	public ResponseEntity<CustomerResponse> chargePoint(
			@Parameter(description = "고객 id", example = "1") @PathVariable Long id,
			@Valid @RequestBody PointChargeRequest request) {
		return ResponseEntity.ok(customerService.chargePoint(id, request));
	}
}
