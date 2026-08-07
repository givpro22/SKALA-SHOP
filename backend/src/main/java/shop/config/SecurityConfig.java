package shop.config;

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
				.authorizeHttpRequests(auth -> auth
						// 프리플라이트에는 Authorization 헤더가 실리지 않는다. 막으면 본 요청이 나가지 못한다.
						.requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
						.requestMatchers("/api/auth/login", "/api/auth/signup").permitAll()
						.requestMatchers("/swagger-ui/**", "/v3/api-docs/**", "/swagger-ui.html").permitAll()
						.requestMatchers(HttpMethod.GET, "/api/**").permitAll()
						.requestMatchers("/api/**").authenticated()
						.anyRequest().permitAll())
				.exceptionHandling(e -> e
						// 인증 없이 보호 경로에 온 경우. 기본 동작(빈 401)을 쓰지 않고
						// 계약 §4.1 의 실패 형태로 맞춘다.
						.authenticationEntryPoint((req, res, ex) ->
								errorResponseWriter.write(req, res, ErrorCode.UNAUTHORIZED)))
				.addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

		return http.build();
	}
}
