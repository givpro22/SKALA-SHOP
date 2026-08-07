package shop.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;

@Configuration
public class OpenApiConfig {

	@Bean
	OpenAPI skalaShopOpenAPI() {
		return new OpenAPI()
				/*
				 * bearer 스킴을 등록해야 Swagger UI 에 Authorize 버튼이 생긴다. 없으면
				 * 보호된 엔드포인트를 "Try it out" 으로 시험할 방법이 없어, 문서가 있어도
				 * 인증이 실제로 동작하는지 화면에서 확인할 수 없다.
				 */
				.components(new Components().addSecuritySchemes("bearerAuth",
						new SecurityScheme()
								.type(SecurityScheme.Type.HTTP)
								.scheme("bearer")
								.bearerFormat("JWT")))
				.info(new Info()
				.title("SKALA-SHOP API")
				.version("v1")
				.description("""
						상품 · 고객 · 주문 REST API.

						주문 생성은 재고 차감 · 포인트 차감 · 주문 저장을 하나의 트랜잭션으로 처리하고,
						주문 취소는 재고 복원 · 포인트 환급을 하나의 트랜잭션으로 처리한다.
						실패 응답은 상태코드와 무관하게 항상 `{code, message, timestamp, path, fieldErrors}` 형태다.

						**인증:** 조회(GET)는 공개이고 생성·수정·삭제는 `Authorization: Bearer {token}` 이 필요하다.
						토큰은 `POST /api/auth/login` 으로 받는다."""));
	}
}
