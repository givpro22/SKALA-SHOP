package shop.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 계약 §8.2. */
@Schema(description = "계정 등록 요청")
public record SignupRequest(

		@Schema(description = "계정 아이디. 이메일 형식이며 중복 시 409", example = "operator@skala.shop")
		@NotBlank(message = "아이디는 필수입니다")
		@Email(message = "아이디는 이메일 형식이어야 합니다")
		@Size(max = 100, message = "아이디는 100자를 넘을 수 없습니다")
		String username,

		/*
		 * 최소 8자만 건다. 대소문자·특수문자 조합을 강제하지 않는 것은 의도다 — 규칙이 늘수록
		 * 사용자는 규칙을 만족하는 짧고 예측 가능한 비밀번호를 만든다. 길이가 더 강한 방어다.
		 */
		@Schema(description = "비밀번호. 8자 이상", example = "skala1234", minLength = 8)
		@NotBlank(message = "비밀번호는 필수입니다")
		@Size(min = 8, max = 72, message = "비밀번호는 8자 이상 72자 이하여야 합니다")
		String password) {
}
