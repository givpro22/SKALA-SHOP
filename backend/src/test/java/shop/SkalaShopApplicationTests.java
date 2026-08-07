package shop;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * 컨텍스트가 실제로 뜨는지 확인한다. compileJava는 application.yml의 오타나 주입 실패
 * ({@code app.cors.allowed-origins} 누락 등)를 잡지 못하므로, 골격 단계에서 이 테스트가
 * 유일한 기동 검증이다.
 */
@SpringBootTest
@ActiveProfiles("local")
class SkalaShopApplicationTests {

	@Test
	void contextLoads() {
	}
}
