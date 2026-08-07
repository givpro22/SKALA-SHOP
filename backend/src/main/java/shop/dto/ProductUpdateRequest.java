package shop.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/**
 * 계약 §2.2. <b>전체 교체(PUT) 시맨틱</b>이므로 모든 필드를 보내야 한다.
 * {@code description}을 생략하면 {@code null}로 덮어쓴다.
 */
@Schema(description = "상품 수정 요청 (전체 교체)")
public record ProductUpdateRequest(

		@Schema(description = "상품명. 자기 자신을 제외하고 중복 검사한다", example = "무선 마우스", maxLength = 100)
		@NotBlank(message = "상품명은 필수입니다")
		@Size(max = 100, message = "상품명은 100자를 넘을 수 없습니다")
		String name,

		@Schema(description = "상품 설명. 생략하면 null로 덮어쓴다", example = "조용한 클릭, 2.4GHz 무선",
				nullable = true, maxLength = 500)
		@Size(max = 500, message = "상품 설명은 500자를 넘을 수 없습니다")
		String description,

		@Schema(description = "단가(원)", example = "27000")
		@NotNull(message = "가격은 필수입니다")
		@PositiveOrZero(message = "가격은 0 이상이어야 합니다")
		Integer price,

		@Schema(description = "재고 수량. 재고 실사 반영용이며 기존 주문에 소급되지 않는다(BR-12·BR-13)",
				example = "48")
		@NotNull(message = "재고는 필수입니다")
		@PositiveOrZero(message = "재고는 0 이상이어야 합니다")
		Integer stock,

		@Schema(description = "상품 이미지 주소. 생략하면 null로 덮어쓴다(전체 교체 시맨틱)",
				example = "/products/mouse.svg", nullable = true, maxLength = 500)
		@Size(max = 500, message = "이미지 주소는 500자를 넘을 수 없습니다")
		String imageUrl) {
}
