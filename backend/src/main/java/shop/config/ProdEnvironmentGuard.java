package shop.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * prod 기동에 반드시 필요한 환경변수가 실제로 주입되었는지 기동 시점에 확인한다.
 *
 * <p>이 클래스가 없으면 변수가 비어도 애플리케이션이 그냥 뜬다. Spring Boot가
 * {@code application-prod.yml}의 {@code ${DB_URL}}을 바인딩할 때 쓰는
 * {@code PropertySourcesPlaceholdersResolver}는 <b>해석 실패를 무시하도록</b> 되어 있어,
 * 치환되지 않은 {@code "${DB_URL}"} 문자열이 그대로 JDBC 드라이버에 전달된다. 실제로 확인한 결과
 * Hibernate는 이를 {@code HHH000342 Could not obtain connection} <b>경고</b>로만 남기고 기동을
 * 계속했다. 그 상태의 컨테이너는 헬스체크를 통과한 뒤 첫 요청에서야 500으로 죽는다.
 *
 * <p>반면 {@code @Value}는 해석 실패 시 {@code IllegalArgumentException}을 던진다. 같은 값을
 * 생성자로 한 번 더 받아 두는 것만으로, 변수 누락이 "조용한 경고"에서 "기동 실패"로 바뀐다.
 */
@Configuration
@Profile("prod")
public class ProdEnvironmentGuard {

	private static final Logger log = LoggerFactory.getLogger(ProdEnvironmentGuard.class);

	public ProdEnvironmentGuard(@Value("${DB_URL}") String dbUrl,
			@Value("${DB_USERNAME}") String dbUsername,
			@Value("${DB_PASSWORD}") String dbPassword) {

		if (dbUrl.isBlank() || dbUsername.isBlank()) {
			throw new IllegalStateException(
					"prod 프로파일에는 DB_URL, DB_USERNAME이 비어 있지 않은 값으로 필요합니다.");
		}
		// 비밀번호는 길이만 남긴다. 값을 로그에 남기면 컨테이너 로그 수집기로 그대로 흘러간다.
		log.info("prod 데이터소스 확인 - url={}, username={}, password={}자",
				dbUrl, dbUsername, dbPassword.length());
	}
}
