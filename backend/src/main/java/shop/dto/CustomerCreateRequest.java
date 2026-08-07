package shop.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/** 계약 §2.3. */
@Schema(description = "고객 등록 요청")
public record CustomerCreateRequest(

		@Schema(description = "고객명", example = "김스칼라", maxLength = 50)
		@NotBlank(message = "고객명은 필수입니다")
		@Size(max = 50, message = "고객명은 50자를 넘을 수 없습니다")
		String name,

		@Schema(description = "이메일. 중복 시 409", example = "kim@skala.shop", maxLength = 100)
		@NotBlank(message = "이메일은 필수입니다")
		@Email(message = "이메일 형식이 올바르지 않습니다")
		@Size(max = 100, message = "이메일은 100자를 넘을 수 없습니다")
		String email,

		@Schema(description = "초기 포인트. 생략하면 0", example = "2000000", nullable = true)
		@PositiveOrZero(message = "포인트는 0 이상이어야 합니다")
		Integer point) {

	/** 계약 §2.3 — 생략/{@code null}이면 {@code 0}. */
	public int pointOrZero() {
		return point == null ? 0 : point;
	}
}
