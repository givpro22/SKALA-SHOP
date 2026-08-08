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
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import shop.dto.ErrorResponse;
import shop.dto.ProductCreateRequest;
import shop.dto.ProductResponse;
import shop.dto.ProductUpdateRequest;
import shop.service.ProductService;

/**
 * 상품 API. Controller는 얇게 유지한다 — 요청 검증, Service 위임, 응답 변환만 한다.
 * 비즈니스 판정을 여기 두면 트랜잭션 밖에서 판정하는 셈이 되어 동시성 상황에서 규칙이 깨진다.
 */
@Tag(name = "상품", description = "상품 등록·조회·수정·삭제")
@RestController
@RequestMapping("/api/products")
@RequiredArgsConstructor
public class ProductController {

	private final ProductService productService;

	@Operation(summary = "상품 등록",
			description = "상품을 등록한다. 상품명이 이미 존재하면 409로 거부한다(BR-16). "
					+ "성공 시 Location 헤더에 생성된 리소스 경로를 담는다.",
			security = @SecurityRequirement(name = "bearerAuth"))
	@ApiResponses({
			@ApiResponse(responseCode = "201", description = "등록됨"),
			@ApiResponse(responseCode = "400", description = "VALIDATION_ERROR",
					content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
			@ApiResponse(responseCode = "409", description = "DUPLICATE_PRODUCT_NAME",
					content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
			@ApiResponse(responseCode = "401", description = "UNAUTHORIZED / TOKEN_EXPIRED",
					content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
			@ApiResponse(responseCode = "403", description = "FORBIDDEN — 토큰은 유효하나 ADMIN이 아니다",
					content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
	})
	@PostMapping
	public ResponseEntity<ProductResponse> create(@Valid @RequestBody ProductCreateRequest request) {
		ProductResponse response = productService.create(request);
		return ResponseEntity
				.created(URI.create("/api/products/" + response.productId()))
				.body(response);
	}

	@Operation(summary = "상품 목록 조회",
			description = "전체 상품을 배열로 반환한다. 결과가 없으면 404가 아니라 200 + 빈 배열이다.")
	@ApiResponse(responseCode = "200", description = "조회됨")
	@GetMapping
	public ResponseEntity<List<ProductResponse>> findAll() {
		return ResponseEntity.ok(productService.findAll());
	}

	@Operation(summary = "상품 단건 조회")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "조회됨"),
			@ApiResponse(responseCode = "400", description = "TYPE_MISMATCH",
					content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
			@ApiResponse(responseCode = "404", description = "PRODUCT_NOT_FOUND",
					content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
	})
	@GetMapping("/{id}")
	public ResponseEntity<ProductResponse> findById(
			@Parameter(description = "상품 id", example = "1") @PathVariable Long id) {
		return ResponseEntity.ok(productService.findById(id));
	}

	@Operation(summary = "상품 수정",
			description = "전체 교체(PUT) 시맨틱이다. description을 생략하면 null로 덮어쓴다. "
					+ "여기서 바꾼 가격·재고는 기존 주문에 소급되지 않는다(BR-12). "
					+ "동시 요청으로 같은 상품 행의 갱신이 충돌하면 409를 반환하며, 이때 어떤 변경도 반영되지 않는다.",
			security = @SecurityRequirement(name = "bearerAuth"))
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "수정됨"),
			@ApiResponse(responseCode = "400", description = "VALIDATION_ERROR / TYPE_MISMATCH",
					content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
			@ApiResponse(responseCode = "404", description = "PRODUCT_NOT_FOUND",
					content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
			@ApiResponse(responseCode = "409", description = "DUPLICATE_PRODUCT_NAME / CONCURRENT_UPDATE",
					content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
			@ApiResponse(responseCode = "401", description = "UNAUTHORIZED / TOKEN_EXPIRED",
					content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
			@ApiResponse(responseCode = "403", description = "FORBIDDEN — 토큰은 유효하나 ADMIN이 아니다",
					content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
	})
	@PutMapping("/{id}")
	public ResponseEntity<ProductResponse> update(
			@Parameter(description = "상품 id", example = "1") @PathVariable Long id,
			@Valid @RequestBody ProductUpdateRequest request) {
		return ResponseEntity.ok(productService.update(id, request));
	}

	@Operation(summary = "상품 삭제",
			description = "주문에 참조된 상품은 삭제할 수 없다(BR-17). 취소된 주문의 라인도 참조로 센다. "
					+ "성공 시 204이며 본문이 없다.",
			security = @SecurityRequirement(name = "bearerAuth"))
	@ApiResponses({
			@ApiResponse(responseCode = "204", description = "삭제됨 (본문 없음)"),
			@ApiResponse(responseCode = "400", description = "TYPE_MISMATCH",
					content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
			@ApiResponse(responseCode = "404", description = "PRODUCT_NOT_FOUND",
					content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
			@ApiResponse(responseCode = "409", description = "PRODUCT_IN_USE",
					content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
			@ApiResponse(responseCode = "401", description = "UNAUTHORIZED / TOKEN_EXPIRED",
					content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
			@ApiResponse(responseCode = "403", description = "FORBIDDEN — 토큰은 유효하나 ADMIN이 아니다",
					content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
	})
	@DeleteMapping("/{id}")
	public ResponseEntity<Void> delete(
			@Parameter(description = "상품 id", example = "1") @PathVariable Long id) {
		productService.delete(id);
		return ResponseEntity.noContent().build();
	}
}
