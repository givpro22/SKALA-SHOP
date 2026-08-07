package shop.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

/** 계약 §8.1. example 은 local 시드 계정과 맞췄다 — "Try it out" 이 바로 성공한다. */
@Schema(description = "로그인 요청")
public record LoginRequest(

		@Schema(description = "계정 아이디", example = "admin@skala.shop")
		@NotBlank(message = "아이디는 필수입니다")
		String username,

		@Schema(description = "비밀번호", example = "skala1234")
		@NotBlank(message = "비밀번호는 필수입니다")
		String password) {
}
