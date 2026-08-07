package shop.security;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Date;

import javax.crypto.SecretKey;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
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

	public String issue(String username) {
		Date now = new Date();
		return Jwts.builder()
				.subject(username)
				.issuedAt(now)
				.expiration(new Date(now.getTime() + validity.toMillis()))
				.signWith(key)
				.compact();
	}

	public long validitySeconds() {
		return validity.toSeconds();
	}

	/**
	 * 유효하면 subject(username), 아니면 {@link JwtVerificationException}.
	 *
	 * <p>만료와 그 밖의 실패를 <b>다른 에러 코드로 나눈다.</b> 만료는 다시 로그인하면 풀리고
	 * 위조·손상은 그렇지 않다 — 화면이 "다시 로그인하세요"를 띄울지 판단하려면 이 구분이 필요하다.
	 */
	public String parseUsername(String token) {
		try {
			return Jwts.parser().verifyWith(key).build()
					.parseSignedClaims(token)
					.getPayload()
					.getSubject();
		} catch (ExpiredJwtException e) {
			throw new JwtVerificationException(ErrorCode.TOKEN_EXPIRED);
		} catch (JwtException | IllegalArgumentException e) {
			throw new JwtVerificationException(ErrorCode.UNAUTHORIZED);
		}
	}
}
