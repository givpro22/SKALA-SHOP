package shop.dto;

import java.util.List;

import org.springframework.data.domain.Page;

import io.swagger.v3.oas.annotations.media.Schema;
import shop.domain.Product;

/**
 * 계약 §9.3.1. <b>이 프로젝트에서 유일한 래핑 목록 응답</b>({@code [호환성 쟁점 C-3]}).
 *
 * <p>§0.2는 "모든 목록 응답은 순수 배열"이라고 못 박았지만 페이지네이션은 {@code totalElements}·
 * {@code hasNext}를 배열로 표현할 수 없다. <b>깨지는 범위를 경로 하나로 격리했다</b> —
 * {@code /api/products}, {@code /api/customers}, {@code /api/orders}, {@code /api/shop/orders}는
 * 전부 순수 배열 그대로다.
 *
 * <p><b>Spring {@code Page<T>}를 그대로 직렬화하지 않는다.</b> 그대로 내리면
 * {@code {content, pageable: {sort: {sorted, unsorted, empty}, offset, paged, unpaged}, ...}} 같은
 * 프레임워크 내부 구조가 계약에 새어 들어오고, Spring Data 버전이 올라가면 그 모양이 바뀐다.
 * Repository가 {@code Page<Product>}를 반환하더라도 여기서 <b>6필드만</b> 옮겨 담는다.
 */
@Schema(description = "상품 검색 결과 (페이지)")
public record ProductPageResponse(

		@Schema(description = "이 페이지의 상품 배열. 결과가 없으면 빈 배열")
		List<ProductResponse> items,

		@Schema(description = "0-based 현재 페이지", example = "0")
		int page,

		@Schema(description = "요청된 페이지 크기", example = "12")
		int size,

		@Schema(description = "필터 적용 후 전체 건수", example = "20")
		long totalElements,

		@Schema(description = "전체 페이지 수. 0건이면 0", example = "2")
		int totalPages,

		@Schema(description = "다음 페이지 존재 여부 (page + 1 < totalPages)", example = "true")
		boolean hasNext) {

	public static ProductPageResponse from(Page<Product> page) {
		int totalPages = page.getTotalPages();
		int number = page.getNumber();
		return new ProductPageResponse(
				page.getContent().stream().map(ProductResponse::from).toList(),
				number,
				page.getSize(),
				page.getTotalElements(),
				totalPages,
				// 계약이 명시한 식을 그대로 쓴다. page.hasNext()와 결과는 같지만, 계약 문서의
				// 한 줄과 코드 한 줄이 눈으로 대조되는 편이 나중에 어긋났을 때 빨리 드러난다.
				number + 1 < totalPages);
	}
}
