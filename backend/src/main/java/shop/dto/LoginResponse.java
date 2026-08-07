package shop.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 계약 §8.1 응답.
 *
 * <p><b>비밀번호 해시는 물론이고 사용자 id 도 담지 않는다.</b> 프론트가 필요한 것은
 * "누구로 로그인했는가(username)"와 "언제까지 유효한가"뿐이다. 필드를 늘리면 그 값이
 * 화면 어딘가에 노출되고, 노출된 값은 언젠가 신뢰의 근거로 쓰인다.
 */
@Schema(description = "로그인 응답")
public record LoginResponse(

		@Schema(description = "Bearer 토큰. Authorization 헤더에 `Bearer {token}` 으로 넣는다")
		String accessToken,

		@Schema(description = "토큰 유효 기간(초)", example = "3600")
		long expiresIn,

		@Schema(description = "로그인한 계정 아이디", example = "admin@skala.shop")
		String username) {
}
