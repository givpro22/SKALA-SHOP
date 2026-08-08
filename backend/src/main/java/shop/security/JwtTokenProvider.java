package shop.security;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Date;

import javax.crypto.SecretKey;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import shop.domain.UserRole;
import shop.exception.ErrorCode;

/**
 * 토큰 발급·검증. 계약 §8.
 *
 * <p>비밀키는 환경변수로 받는다. 하드코딩하면 이미지에 박혀 배포본에서 위조가 가능해진다 —
 * DB 접속 정보를 환경변수로 뺀 것과 같은 이유다.
 *
 * <p>키 길이를 기동 시점에 검증한다. HS256 은 256비트(32바이트) 미만 키를 거부하는데,
 * 그 실패는 첫 로그인 요청에서야 500 으로 드러난다. {@code ProdEnvironmentGuard} 와 같은
 * 판단이다 — <b>잘못된 설정은 늦게 터질수록 원인을 찾기 어렵다.</b>
 */
@Component
public class JwtTokenProvider {

	private static final String ROLE_CLAIM = "role";

	private final SecretKey key;
	private final Duration validity;

	public JwtTokenProvider(
			@Value("${jwt.secret}") String secret,
			@Value("${jwt.expiration-seconds}") long expirationSeconds) {

		byte[] bytes = secret.getBytes(StandardCharsets.UTF_8);
		if (bytes.length < 32) {
			throw new IllegalStateException(
					"jwt.secret 이 너무 짧습니다. HS256 은 32바이트 이상이 필요합니다. (현재 %d바이트)"
							.formatted(bytes.length));
		}
		this.key = Keys.hmacShaKeyFor(bytes);
		this.validity = Duration.ofSeconds(expirationSeconds);
	}

	/**
	 * 토큰에 역할을 클레임으로 싣는다(계약 §9.4).
	 *
	 * <p><b>왜 요청마다 DB 에서 읽지 않는가.</b> 역할 판정은 <b>모든</b> 보호 요청의 필터 단계에서
	 * 일어난다. 매번 계정을 조회하면 조회 한 건이 요청 수만큼 늘고, 그 조회는 인증 이전 단계라
	 * 캐시할 자리도 마땅치 않다.
	 *
	 * <p>그 대가는 <b>토큰 수명 동안 역할이 고정된다</b>는 것인데, 이 앱에서는 대가가 없다 —
	 * 역할을 바꾸는 API 경로가 존재하지 않고 {@code ADMIN} 은 시드로만 만들어지기 때문이다.
	 * 역할 변경 기능이 생기면 이 판단을 다시 해야 하며, 그때 고칠 곳은 이 메서드와 필터 둘뿐이다.
	 */
	public String issue(String username, UserRole role) {
		Date now = new Date();
		return Jwts.builder()
				.subject(username)
				.claim(ROLE_CLAIM, role.name())
				.issuedAt(now)
				.expiration(new Date(now.getTime() + validity.toMillis()))
				.signWith(key)
				.compact();
	}

	public long validitySeconds() {
		return validity.toSeconds();
	}

	/**
	 * 유효하면 {@link AuthenticatedUser}, 아니면 {@link JwtVerificationException}.
	 *
	 * <p>만료와 그 밖의 실패를 <b>다른 에러 코드로 나눈다.</b> 만료는 다시 로그인하면 풀리고
	 * 위조·손상은 그렇지 않다 — 화면이 "다시 로그인하세요"를 띄울지 판단하려면 이 구분이 필요하다.
	 *
	 * <p><b>알 수 없는 역할 문자열은 거부한다.</b> 클레임은 서명으로 보호되지만, 역할 값을 해석하지
	 * 못했을 때 조용히 {@code SHOPPER} 로 떨어뜨리면 관리자 토큰이 구매자로 강등되어 "로그인은
	 * 되는데 관리 화면만 403" 이라는 진단하기 어려운 상태가 된다. 401 로 끊으면 원인이 토큰임이 즉시 드러난다.
	 */
	public AuthenticatedUser parse(String token) {
		try {
			Claims claims = Jwts.parser().verifyWith(key).build()
					.parseSignedClaims(token)
					.getPayload();
			return new AuthenticatedUser(claims.getSubject(), parseRole(claims));
		} catch (ExpiredJwtException e) {
			throw new JwtVerificationException(ErrorCode.TOKEN_EXPIRED);
		} catch (JwtException | IllegalArgumentException e) {
			throw new JwtVerificationException(ErrorCode.UNAUTHORIZED);
		}
	}

	private UserRole parseRole(Claims claims) {
		String role = claims.get(ROLE_CLAIM, String.class);
		if (role == null) {
			throw new JwtVerificationException(ErrorCode.UNAUTHORIZED);
		}
		try {
			return UserRole.valueOf(role);
		} catch (IllegalArgumentException e) {
			throw new JwtVerificationException(ErrorCode.UNAUTHORIZED);
		}
	}

	/** 토큰이 확인해 준 신원. 필터가 {@code SecurityContext}를 채우는 데 쓰는 값 전부다. */
	public record AuthenticatedUser(String username, UserRole role) {
	}
}
