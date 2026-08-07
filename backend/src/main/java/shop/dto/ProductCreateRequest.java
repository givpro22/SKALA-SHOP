package shop.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/** 계약 §2.1. example은 local 시드와 맞춰 "Try it out"이 바로 성공하도록 했다. */
@Schema(description = "상품 등록 요청")
public record ProductCreateRequest(

		@Schema(description = "상품명. 중복 시 409", example = "무선 마우스", maxLength = 100)
		@NotBlank(message = "상품명은 필수입니다")
		@Size(max = 100, message = "상품명은 100자를 넘을 수 없습니다")
		String name,

		@Schema(description = "상품 설명. 생략 가능", example = "조용한 클릭, 2.4GHz 무선",
				nullable = true, maxLength = 500)
		@Size(max = 500, message = "상품 설명은 500자를 넘을 수 없습니다")
		String description,

		@Schema(description = "단가(원). 정수 원 단위", example = "25000")
		@NotNull(message = "가격은 필수입니다")
		@PositiveOrZero(message = "가격은 0 이상이어야 합니다")
		Integer price,

		@Schema(description = "재고 수량", example = "50")
		@NotNull(message = "재고는 필수입니다")
		@PositiveOrZero(message = "재고는 0 이상이어야 합니다")
		Integer stock) {
}
