package shop.dto;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

/**
 * 계약 §2.6.
 *
 * <p>{@code items}에 {@code @Valid}가 붙어야 각 {@link OrderItemRequest}의 제약이 검사된다.
 * 빠뜨리면 중첩 객체의 애너테이션이 아무 일도 하지 않는다.
 */
@Schema(description = "주문 생성 요청")
public record OrderCreateRequest(

		@Schema(description = "주문 고객 id", example = "4")
		@NotNull(message = "고객 id는 필수입니다")
		Long customerId,

		@Schema(description = "주문 항목 배열. 최소 1건")
		@NotEmpty(message = "주문 항목은 1건 이상이어야 합니다")
		@Valid
		List<OrderItemRequest> items) {
}
