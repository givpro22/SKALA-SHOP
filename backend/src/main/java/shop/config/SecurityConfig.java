package shop.config;

import org.springframework.boot.actuate.autoconfigure.security.servlet.EndpointRequest;
import org.springframework.boot.actuate.health.HealthEndpoint;
import org.springframework.boot.actuate.info.InfoEndpoint;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import lombok.RequiredArgsConstructor;
import shop.exception.ErrorCode;
import shop.security.ErrorResponseWriter;
import shop.security.JwtAuthenticationFilter;

/**
 * 인증 경계. 계약 §8.5.
 *
 * <p><b>읽기는 공개, 쓰기는 인증.</b> 목록·상세는 로그인 없이 보이고 생성·수정·삭제만 토큰을
 * 요구한다. 전부 막지 않은 것은 의도다 — 이 앱의 평가 대상은 비즈니스 규칙(포인트·재고·취소)
 * 이고, 그 화면을 보려면 매번 로그인부터 해야 한다면 규칙 자체가 가려진다.
 */
@Configuration
@RequiredArgsConstructor
public class SecurityConfig {

	private final JwtAuthenticationFilter jwtAuthenticationFilter;
	private final ErrorResponseWriter errorResponseWriter;

	@Bean
	PasswordEncoder passwordEncoder() {
		return new BCryptPasswordEncoder();
	}

	@Bean
	SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
		http
				// 토큰 기반이라 서버가 세션을 들고 있을 이유가 없다. STATELESS 로 두지 않으면
				// 요청마다 JSESSIONID 가 발급되어 "토큰 없이도 되는" 경로가 생긴다.
				.sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
				// CSRF 는 쿠키 기반 세션을 노린 공격이다. Authorization 헤더는 브라우저가
				// 자동으로 붙이지 않으므로 해당하지 않는다.
				.csrf(csrf -> csrf.disable())
				.httpBasic(b -> b.disable())
				.formLogin(f -> f.disable())
				// CORS 는 WebConfig 가 이미 정의했다. 여기서는 그 설정을 쓰도록 켜기만 한다 —
				// 끄면 프리플라이트가 401 로 막혀 브라우저에서 모든 쓰기 요청이 실패한다.
				.cors(c -> {})
				/*
				 * ── 매처는 선언 순서로 첫 매치가 이긴다. 순서가 곧 규칙이다. ──
				 *
				 * 특히 `GET /api/**` permitAll 은 **아래쪽에 있어야 한다.** 위에 두면
				 * `GET /api/shop/cart` · `GET /api/shop/orders` 까지 삼켜서, 계약 §9.4.2 가
				 * SHOPPER 전용으로 규정한 경로가 **구현되는 순간 공개가 된다.** 아래 shop 규칙 셋을
				 * 그 위에 올려 둔 것이 이 블록에서 가장 중요한 한 가지다.
				 */
				.authorizeHttpRequests(auth -> auth
						// 프리플라이트에는 Authorization 헤더가 실리지 않는다. 막으면 본 요청이 나가지 못한다.
						.requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
						.requestMatchers("/api/auth/login", "/api/auth/signup").permitAll()
						.requestMatchers("/swagger-ui/**", "/v3/api-docs/**", "/swagger-ui.html").permitAll()

						/*
						 * ── Actuator. 좁은 것(health·info) 먼저, 넓은 것(나머지 전부) 나중. ──
						 *
						 * 순서를 뒤집으면 toAnyEndpoint() 가 health 까지 삼켜 **컨테이너 헬스체크가
						 * 401 을 받는다.** 그 증상은 "배포가 영원히 끝나지 않는다"로 나타나서 원인이
						 * 애플리케이션에 있는 것처럼 보인다 — 이 파일 아래쪽 `GET /api/**` 가
						 * `/api/shop/**` 을 삼켰던 것과 같은 종류의 사고다.
						 *
						 * **경로 문자열("/actuator/**")이 아니라 EndpointRequest 를 쓴다.**
						 * management.endpoints.web.base-path 를 바꾸면 문자열 매처는 조용히 어긋나고,
						 * 어긋난 결과가 "아무도 안 막힘"이다(아래 anyRequest().permitAll() 로 떨어진다).
						 * EndpointRequest 는 실제 매핑을 따라가므로 base-path 를 바꿔도 함께 움직인다.
						 *
						 * 노출하지 않은 엔드포인트(env·configprops·heapdump·threaddump)는
						 * toAnyEndpoint() 에 **매치되지 않는다** — 매핑 자체가 없기 때문이다.
						 * 그래서 ADMIN 이 /actuator/env 를 불러도 403 이 아니라 404 다. 이 차이가
						 * "인가로 막았다"와 "애초에 없다"를 구분하는 지점이라 판정 항목에 넣었다.
						 */
						.requestMatchers(EndpointRequest.to(HealthEndpoint.class, InfoEndpoint.class)).permitAll()
						.requestMatchers(EndpointRequest.toAnyEndpoint()).hasRole("ADMIN")

						/*
						 * 계약 §8.5 는 이 경로를 "인증 필요"로 규정한다. **아래 GET /api/** permitAll 보다
						 * 반드시 위에 있어야 한다** — 아래에 두면 그것이 먼저 매치해 경로가 공개가 되고,
						 * 토큰 없는 요청이 컨트롤러까지 도달한다.
						 *
						 * 그때 나가는 코드는 401 INVALID_CREDENTIALS 였다. 상태코드는 같지만 뜻이 다르다 —
						 * INVALID_CREDENTIALS 는 "아이디나 비밀번호가 틀렸다"이므로, code 로 분기하는 프론트는
						 * **로그인한 적 없는 사용자에게 "아이디/비밀번호가 틀렸습니다"를 보여주고** 사용자는
						 * 맞는 비밀번호를 계속 다시 넣게 된다. 필요한 안내는 "로그인이 필요합니다"다.
						 */
						.requestMatchers("/api/auth/me").authenticated()

						// 카탈로그는 비로그인도 본다(계약 §9.4.2). 아래 SHOPPER 규칙보다 먼저 와야 한다.
						.requestMatchers(HttpMethod.GET, "/api/shop/products").permitAll()
						/*
						 * 역할 제한이 없는 유일한 shop 경로. ADMIN 으로 로그인한 프론트도 관리 메뉴를
						 * 보일지 판단하려면 role 을 알아야 하는데, UserResponse(3필드 고정)에 role 이
						 * 없으므로 여기가 유일한 전달 경로다. 조회이며 아무것도 생성하지 않는다.
						 */
						.requestMatchers("/api/shop/me").authenticated()
						/*
						 * 나머지 shop 은 전부 SHOPPER 다. **ADMIN 이 호출하면 403 이다.**
						 * 편의만 보면 관리자도 구매하게 두는 편이 데모가 쉽지만, shop 쓰기 경로는
						 * 프로필이 없으면 Customer 를 자동 생성하므로(BR-31) 관리자가 카트를 한 번
						 * 건드리면 관리자 이름의 고객이 고객 목록에 생긴다. 그 고객은 포인트를 갖고
						 * 관리자는 포인트 충전 API 를 쓸 수 있다 — 즉 **자기 지갑을 자기가 충전해서
						 * 구매**할 수 있다. 역할을 나눈 목적 자체가 되살아나 무너진다.
						 */
						.requestMatchers("/api/shop/**").hasRole("SHOPPER")

						// 조회는 계속 공개다(§8.5 그대로). 위 shop 규칙에 걸리지 않은 것만 여기 온다.
						.requestMatchers(HttpMethod.GET, "/api/**").permitAll()

						/*
						 * 기존 쓰기 API 는 ADMIN 전용이 된다({@code [호환성 쟁점 C-2]}).
						 * 하지 않으면 구매자가 POST /api/orders 로 **아무 고객의 포인트나 써서**
						 * 주문을 만들 수 있다 — 그 경로는 요청 본문이 customerId 를 지정하기 때문이다.
						 * 시드 계정 admin@skala.shop 이 ADMIN 이라 기존 캡처는 전부 그대로 재현된다.
						 */
						.requestMatchers("/api/products", "/api/products/**").hasRole("ADMIN")
						.requestMatchers("/api/customers", "/api/customers/**").hasRole("ADMIN")
						.requestMatchers("/api/orders", "/api/orders/**").hasRole("ADMIN")

						.requestMatchers("/api/**").authenticated()
						.anyRequest().permitAll())
				.exceptionHandling(e -> e
						// 인증 없이 보호 경로에 온 경우. 기본 동작(빈 401)을 쓰지 않고
						// 계약 §4.1 의 실패 형태로 맞춘다.
						.authenticationEntryPoint((req, res, ex) ->
								errorResponseWriter.write(req, res, ErrorCode.UNAUTHORIZED))
						/*
						 * 인가 실패(403). **401 과 같은 문제를 한 번 더 겪지 않기 위해 함께 넣는다** —
						 * 인가 실패도 필터 단계라 @RestControllerAdvice 가 잡지 못하고, 그대로 두면
						 * 403 만 Spring 기본 본문(빈 응답 또는 HTML)이 되어 프론트 에러 파서가
						 * `code` 를 찾지 못한다. 계약 §4.1 은 "상태코드와 무관하게 형태가 같다"이므로
						 * 401 에 쓰던 ErrorResponseWriter 를 그대로 공유한다.
						 */
						.accessDeniedHandler((req, res, ex) ->
								errorResponseWriter.write(req, res, ErrorCode.FORBIDDEN)))
				.addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

		return http.build();
	}
}
