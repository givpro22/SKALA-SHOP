package shop.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;

@Configuration
public class OpenApiConfig {

	@Bean
	OpenAPI skalaShopOpenAPI() {
		return new OpenAPI().info(new Info()
				.title("SKALA-SHOP API")
				.version("v1")
				.description("""
						상품 · 고객 · 주문 REST API.

						주문 생성은 재고 차감 · 포인트 차감 · 주문 저장을 하나의 트랜잭션으로 처리하고,
						주문 취소는 재고 복원 · 포인트 환급을 하나의 트랜잭션으로 처리한다.
						실패 응답은 상태코드와 무관하게 항상 `{code, message, timestamp, path, fieldErrors}` 형태다."""));
	}
}
