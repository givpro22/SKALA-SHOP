package shop.dto;

import java.time.LocalDateTime;

import com.fasterxml.jackson.annotation.JsonFormat;

import io.swagger.v3.oas.annotations.media.Schema;
import shop.domain.Product;

/**
 * 계약 §3.1. <b>필드 8개 고정</b>(2026-08-08 {@code imageUrl} 추가).
 *
 * <p>id 필드명이 {@code id}가 아니라 {@code productId}다. 중첩 응답에서 어느 엔티티의 id인지
 * 드러나게 하기 위한 것이며, 프론트 타입이 이 이름에 의존한다.
 *
 * <p>엔티티의 {@code version}(낙관적 락)은 <b>포함하지 않는다.</b> 아래 {@link #from}이 필드를
 * 하나씩 옮기므로 엔티티에 필드가 늘어도 응답이 조용히 커지지 않는다.
 */
@Schema(description = "상품 응답")
public record ProductResponse(

		@Schema(description = "상품 id", example = "1")
		Long productId,

		@Schema(description = "상품명", example = "무선 마우스")
		String name,

		@Schema(description = "상품 설명. 없으면 null", example = "조용한 클릭, 2.4GHz 무선", nullable = true)
		String description,

		@Schema(description = "단가(원)", example = "25000")
		int price,

		@Schema(description = "재고 수량", example = "50")
		int stock,

		@Schema(description = "상품 이미지 주소. 없으면 null이며 화면은 대체 표시로 떨어진다",
				example = "/products/mouse.svg", nullable = true)
		String imageUrl,

		@Schema(description = "등록 시각", example = "2026-08-05T14:30:00")
		@JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss")
		LocalDateTime createdAt,

		@Schema(description = "수정 시각", example = "2026-08-05T14:30:00")
		@JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss")
		LocalDateTime updatedAt) {

	public static ProductResponse from(Product product) {
		return new ProductResponse(
				product.getId(),
				product.getName(),
				product.getDescription(),
				product.getPrice(),
				product.getStock(),
				product.getImageUrl(),
				product.getCreatedAt(),
				product.getUpdatedAt());
	}
}
