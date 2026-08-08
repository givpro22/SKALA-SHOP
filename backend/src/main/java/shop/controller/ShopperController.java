package shop.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import shop.dto.ErrorResponse;
import shop.dto.ShopperResponse;
import shop.service.ShopperService;

/** 구매자 신원. {@code /api/shop/**} 중 <b>유일하게 역할 제한이 없는</b> 엔드포인트다(계약 §9.4.2). */
@Tag(name = "구매", description = "구매자 신원 · 체크아웃 · 내 주문 내역")
@RestController
@RequestMapping("/api/shop/me")
@RequiredArgsConstructor
public class ShopperController {

	private final ShopperService shopperService;

	@Operation(summary = "내 구매자 신원 조회",
			description = """
					로그인한 계정의 역할과 구매자 신원(고객 id · 이름 · 포인트 · 장바구니 라인 수)을 반환한다.

					**shop 경로 중 유일하게 ADMIN도 호출할 수 있다.** ADMIN으로 로그인한 화면도 \
					관리 메뉴를 보일지 판단하려면 `role`을 알아야 하는데, \
					`GET /api/auth/me`는 3필드로 고정되어 있어 `role`을 나르지 않기 때문이다. \
					ADMIN이 호출하면 `role: "ADMIN"`, `customerId: null`, `point: null`, `cartItemCount: 0`이 온다.

					**이 엔드포인트는 아무것도 생성하지 않는다.** 장바구니 쓰기·체크아웃은 구매자 프로필이 \
					없으면 그 자리에서 만들지만, 조회가 데이터를 만들면 화면을 열기만 해도 \
					고객 레코드가 늘어난다.

					`cartItemCount`가 있어 헤더의 장바구니 배지를 그리려고 카트 전체를 불러올 필요가 없다.""",
			security = @SecurityRequirement(name = "bearerAuth"))
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "조회됨"),
			@ApiResponse(responseCode = "401", description = "UNAUTHORIZED — 토큰 없음·위조·손상 / "
					+ "TOKEN_EXPIRED — 만료 (다시 로그인하면 풀린다)",
					content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
	})
	@GetMapping
	public ResponseEntity<ShopperResponse> me(@AuthenticationPrincipal String username) {
		return ResponseEntity.ok(shopperService.me(username));
	}
}
