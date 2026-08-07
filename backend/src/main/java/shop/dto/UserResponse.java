package shop.dto;

import java.time.LocalDateTime;

import com.fasterxml.jackson.annotation.JsonFormat;

import io.swagger.v3.oas.annotations.media.Schema;
import shop.domain.User;

/** 계약 §8.3. 필드 3개 고정 — 비밀번호는 어떤 경로로도 나가지 않는다. */
@Schema(description = "계정 응답")
public record UserResponse(

		@Schema(description = "계정 id", example = "1")
		Long userId,

		@Schema(description = "계정 아이디", example = "admin@skala.shop")
		String username,

		@Schema(description = "등록 시각", example = "2026-08-08T02:10:00")
		@JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss")
		LocalDateTime createdAt) {

	public static UserResponse from(User user) {
		return new UserResponse(user.getId(), user.getUsername(), user.getCreatedAt());
	}
}
