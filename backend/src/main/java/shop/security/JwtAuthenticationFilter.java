package shop.security;

import java.io.IOException;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;

/**
 * `Authorization: Bearer {token}` 을 읽어 SecurityContext 를 채운다.
 *
 * <p><b>토큰이 없으면 통과시킨다.</b> 거부는 여기서 하지 않는다 — 이 앱은 GET 을 공개로
 * 두므로(계약 §8.5) "토큰 없음"이 곧 실패가 아니다. 무엇을 막을지는 {@code SecurityConfig}
 * 의 경로 규칙이 정하고, 필터는 "누구인지"만 확인한다. 두 책임을 한곳에 두면 공개
 * 엔드포인트를 늘릴 때마다 필터를 고쳐야 한다.
 *
 * <p>토큰이 <b>있는데 잘못된</b> 경우는 다르다. 그때는 조용히 익명으로 넘기지 않고 즉시
 * 401 을 쓴다. 넘기면 사용자는 "로그인했는데 권한이 없다"는 화면을 보게 되고, 원인이
 * 만료인지 권한인지 구분할 수 없다.
 */
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

	private static final String HEADER = "Authorization";
	private static final String PREFIX = "Bearer ";

	private final JwtTokenProvider tokenProvider;
	private final ErrorResponseWriter errorResponseWriter;

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
			FilterChain chain) throws ServletException, IOException {

		String header = request.getHeader(HEADER);
		if (header == null || !header.startsWith(PREFIX)) {
			chain.doFilter(request, response);
			return;
		}

		String token = header.substring(PREFIX.length()).trim();
		String username;
		try {
			username = tokenProvider.parseUsername(token);
		} catch (JwtVerificationException e) {
			SecurityContextHolder.clearContext();
			errorResponseWriter.write(request, response, e.getErrorCode());
			return;
		}

		var authentication = new UsernamePasswordAuthenticationToken(username, null, java.util.List.of());
		authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
		SecurityContextHolder.getContext().setAuthentication(authentication);
		chain.doFilter(request, response);
	}
}
