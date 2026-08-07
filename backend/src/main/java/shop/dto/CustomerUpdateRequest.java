package shop.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 계약 §2.4.
 *
 * <p><b>{@code point} 필드가 없다.</b> 요청에 넣어도 역직렬화 대상이 아니라 무시된다.
 * 포인트를 임의로 덮어쓰는 경로를 만들지 않기 위한 것이며, 변경 경로는 충전과 주문/취소뿐이다.
 */
@Schema(description = "고객 수정 요청 (포인트는 변경할 수 없다)")
public record CustomerUpdateRequest(

		@Schema(description = "고객명", example = "김스칼라", maxLength = 50)
		@NotBlank(message = "고객명은 필수입니다")
		@Size(max = 50, message = "고객명은 50자를 넘을 수 없습니다")
		String name,

		@Schema(description = "이메일. 자기 자신을 제외하고 중복 검사한다", example = "kim@skala.shop",
				maxLength = 100)
		@NotBlank(message = "이메일은 필수입니다")
		@Email(message = "이메일 형식이 올바르지 않습니다")
		@Size(max = 100, message = "이메일은 100자를 넘을 수 없습니다")
		String email) {
}
