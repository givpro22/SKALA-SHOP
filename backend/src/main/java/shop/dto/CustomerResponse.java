package shop.dto;

import java.time.LocalDateTime;

import com.fasterxml.jackson.annotation.JsonFormat;

import io.swagger.v3.oas.annotations.media.Schema;
import shop.domain.Customer;

/**
 * 계약 §3.2. <b>필드 6개 고정.</b> 엔티티의 {@code version}은 포함하지 않는다.
 */
@Schema(description = "고객 응답")
public record CustomerResponse(

		@Schema(description = "고객 id", example = "1")
		Long customerId,

		@Schema(description = "고객명", example = "김스칼라")
		String name,

		@Schema(description = "이메일", example = "kim@skala.shop")
		String email,

		@Schema(description = "현재 보유 포인트(원)", example = "1911000")
		int point,

		@Schema(description = "등록 시각", example = "2026-08-05T14:30:00")
		@JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss")
		LocalDateTime createdAt,

		@Schema(description = "수정 시각", example = "2026-08-05T14:30:00")
		@JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss")
		LocalDateTime updatedAt) {

	public static CustomerResponse from(Customer customer) {
		return new CustomerResponse(
				customer.getId(),
				customer.getName(),
				customer.getEmail(),
				customer.getPoint(),
				customer.getCreatedAt(),
				customer.getUpdatedAt());
	}
}
