package shop.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import shop.dto.ErrorResponse;
import shop.dto.ProductPageResponse;
import shop.dto.ProductSearchQuery;
import shop.service.ProductService;

/**
 * 구매자용 상품 검색. <b>비로그인도 볼 수 있다.</b>
 *
 * <p>태그를 기존 "상품"과 분리한다(계약 §9.7) — 합치면 Swagger 목록에서 관리자 API 와 구매자 API 가
 * 섞여 경로로 갈라 둔 경계가 화면에서 사라진다.
 */
@Tag(name = "상품 검색", description = "구매자용 상품 검색 · 정렬 · 페이지네이션 (비로그인 공개)")
@RestController
@RequestMapping("/api/shop/products")
@RequiredArgsConstructor
public class ShopProductController {

	private final ProductService productService;

	@Operation(summary = "상품 검색",
			description = """
					상품명 부분 일치(대소문자 무시)로 검색하고 정렬·페이지네이션을 적용한다. \
					**검색 대상은 상품명뿐이며 설명은 대상이 아니다.**

					**이 프로젝트에서 유일하게 래핑 객체를 반환하는 목록이다.** 다른 목록은 전부 순수 배열이며, \
					여기만 예외인 것은 `totalElements`·`hasNext`를 배열로 표현할 수 없기 때문이다. \
					응답은 `items·page·size·totalElements·totalPages·hasNext` **6필드뿐**이고 \
					Spring의 `pageable`·`sort`·`unpaged` 같은 내부 구조는 포함하지 않는다.

					`sort`는 열거된 세 값만 받는다 — Spring의 `sort=price,asc` 문법을 열면 \
					클라이언트가 아무 필드로나 정렬할 수 있고 존재하지 않는 필드는 500이 된다. \
					모든 정렬에 `productId` 2차 키가 붙어 페이지 경계에서 상품이 중복·누락되지 않는다.

					**범위를 벗어난 `page`는 404가 아니라 200 + 빈 배열이다.** \
					반면 `size`가 상한(50)을 넘으면 조용히 자르지 않고 400으로 거부한다 — \
					자르면 클라이언트는 자기가 요청한 만큼 받았다고 믿는다.""")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "조회됨. 결과가 없으면 items 빈 배열 + totalPages 0"),
			@ApiResponse(responseCode = "400",
					description = "VALIDATION_ERROR — sort가 허용값이 아니거나 size가 1~50을 벗어남 / "
							+ "TYPE_MISMATCH — page·size가 숫자가 아님",
					content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
	})
	@GetMapping
	public ResponseEntity<ProductPageResponse> search(
			@Parameter(description = "상품명 검색어. 앞뒤 공백은 제거된다. 생략하면 전체", example = "키보드")
			@RequestParam(required = false) String q,

			@Parameter(description = "정렬 축. 그 외 값은 400",
					schema = @Schema(allowableValues = { "LATEST", "PRICE_ASC", "PRICE_DESC" },
							defaultValue = "LATEST"))
			@RequestParam(required = false) String sort,

			@Parameter(description = "0-based 페이지 번호", example = "0")
			@RequestParam(required = false) Integer page,

			@Parameter(description = "페이지 크기. 1~50", example = "12")
			@RequestParam(required = false) Integer size) {

		return ResponseEntity.ok(productService.search(ProductSearchQuery.of(q, sort, page, size)));
	}
}
