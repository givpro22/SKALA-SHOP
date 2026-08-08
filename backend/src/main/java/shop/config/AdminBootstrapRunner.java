package shop.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import shop.domain.User;
import shop.domain.UserRole;
import shop.repository.UserRepository;

/**
 * {@code prod} 기동 시 {@code ADMIN}이 0건이면 주입된 자격증명으로 1건 만든다. 계약 §9.4.5.
 *
 * <h2>왜 필요한가</h2>
 *
 * <p>{@code User.createAdmin}의 호출부가 {@code LocalSeedRunner}({@code @Profile("local")}) 하나뿐이라
 * <b>깨끗한 {@code prod} 배포에는 {@code ADMIN}이 영원히 존재하지 않았다.</b>
 * {@code POST /api/products}가 {@code hasRole("ADMIN")}이므로 <b>카탈로그를 채울 방법이 없고
 * 빈 쇼핑몰로 굳는다.</b>
 *
 * <p><b>그 상태는 정상으로 보인다</b> — 기동 성공, 헬스체크 통과, 로그 깨끗, 조회 API 200.
 * 관리 API만 403인데 그건 "권한이 없다"는 정상 응답이다. 설정이 틀렸다는 신호가 어디에도 없다.
 *
 * <h2>조건은 "최초 기동"이 아니라 "{@code ADMIN} 0건"이다</h2>
 *
 * <p>"최초"는 무엇이 최초인지 판정할 수 없어 별도 마커가 또 필요해진다. 계정 수로 잡으면
 * 누구나 확인할 수 있고, 세 가지가 <b>공짜로</b> 따라온다.
 *
 * <ul>
 *   <li><b>멱등하다</b> — 두 번째 기동부터는 이미 있으므로 아무 일도 없다.</li>
 *   <li><b>자기 복구된다</b> — 관리자 계정이 지워져도 재기동으로 되살아난다.</li>
 *   <li><b>비밀번호 회전이 기능 추가 없이 성립한다</b> — 환경변수를 바꾸고 기존 계정을 지운 뒤
 *       재기동하면 새 자격증명으로 다시 만들어진다.</li>
 * </ul>
 *
 * <h2>기존 계정을 절대 덮어쓰지 않는다</h2>
 *
 * <p>덮어쓰면 <b>운영 중에 환경변수를 바꾼 것만으로 관리자 비밀번호가 조용히 리셋된다.</b>
 * 그래서 {@code ADMIN}이 1건이라도 있으면 자격증명이 주입돼 있어도 손대지 않는다.
 *
 * <h2>{@code ProdEnvironmentGuard}에 넣지 않은 이유</h2>
 *
 * <p>그 클래스는 생성자 {@code @Value}로 <b>설정만</b> 검사한다. 이 판정은 <b>DB를 읽어야</b> 하므로
 * 러너 단계에 있어야 한다. 대가는 실패 시점이 늦다는 것이다 — 웹서버가 이미 떠서
 * <b>아주 짧게 헬스체크를 통과하는 순간이 생긴다.</b> 따라서 오케스트레이션은 헬스체크가 아니라
 * <b>종료 코드</b>로 판정해야 하며, 여기서 예외를 던지면 Spring Boot가 컨텍스트를 닫고
 * 프로세스가 0이 아닌 코드로 끝난다.
 */
@Component
@Profile("prod")
public class AdminBootstrapRunner implements CommandLineRunner {

	private static final Logger log = LoggerFactory.getLogger(AdminBootstrapRunner.class);

	/** 계약 §8.2와 <b>같은</b> 규칙. 부트스트랩만 예외를 두면 가장 중요한 계정이 가장 약해진다. */
	private static final int PASSWORD_MIN = 8;
	private static final int PASSWORD_MAX = 72;
	private static final int USERNAME_MAX = 100;

	private final UserRepository userRepository;
	private final PasswordEncoder passwordEncoder;
	private final String adminUsername;
	private final String adminPassword;

	/*
	 * 자격증명을 **생성자로** 받는다. 필드 주입이면 이 러너를 테스트에서 그대로 만들 수 없어,
	 * prod 전용 경로가 또 검증되지 않은 채 남는다 — 이 결함이 정확히 그렇게 생겼다.
	 *
	 * application.yml 에 개발 기본값을 두지 않는다. 두면 그것이 배포된다(JWT_SECRET 에서 이미 겪었다).
	 * 여기서 빈 문자열은 "기본값"이 아니라 **주입되지 않았음**을 뜻하며, 그 경우 기동을 끊는다.
	 */
	public AdminBootstrapRunner(UserRepository userRepository, PasswordEncoder passwordEncoder,
			@Value("${ADMIN_USERNAME:}") String adminUsername,
			@Value("${ADMIN_PASSWORD:}") String adminPassword) {
		this.userRepository = userRepository;
		this.passwordEncoder = passwordEncoder;
		this.adminUsername = adminUsername;
		this.adminPassword = adminPassword;
	}

	@Override
	@Transactional
	public void run(String... args) {
		long adminCount = userRepository.countByRole(UserRole.ADMIN);
		if (adminCount > 0) {
			// 자격증명이 주입돼 있어도 손대지 않는다. 덮어쓰면 비밀번호가 조용히 리셋된다.
			log.info("ADMIN 계정이 이미 존재합니다 ({}건). 부트스트랩을 건너뜁니다.", adminCount);
			return;
		}

		validateCredentials();

		User admin = userRepository.save(
				User.createAdmin(adminUsername, passwordEncoder.encode(adminPassword)));

		// username 만 남긴다. 비밀번호는 어떤 형태로도 로그에 남기지 않는다.
		log.info("ADMIN 계정이 없어 부트스트랩으로 생성했습니다. username={}", admin.getUsername());
	}

	/**
	 * 자격증명이 없거나 형식이 어긋나면 <b>기동을 끊는다.</b>
	 *
	 * <p>여기서 통과시키면 앱은 healthy 한데 카탈로그를 채울 수 없는 배포본이 만들어지고,
	 * 그 실패는 관리 API 를 눌러 볼 때까지 드러나지 않는다.
	 */
	private void validateCredentials() {
		if (adminUsername.isBlank() || adminPassword.isBlank()) {
			throw new IllegalStateException(
					"ADMIN 계정이 0건인데 부트스트랩 자격증명이 없습니다. "
							+ "ADMIN_USERNAME 과 ADMIN_PASSWORD 를 주입해 주세요. "
							+ "(이 배포본은 기동하더라도 카탈로그를 채울 수 없습니다)");
		}
		if (adminUsername.length() > USERNAME_MAX || !adminUsername.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")) {
			throw new IllegalStateException(
					"ADMIN_USERNAME 은 이메일 형식이어야 하고 %d자를 넘을 수 없습니다. (현재 %d자)"
							.formatted(USERNAME_MAX, adminUsername.length()));
		}
		if (adminPassword.length() < PASSWORD_MIN || adminPassword.length() > PASSWORD_MAX) {
			throw new IllegalStateException(
					"ADMIN_PASSWORD 는 %d자 이상 %d자 이하여야 합니다. (현재 %d자) — 계약 §8.2 와 같은 규칙입니다."
							.formatted(PASSWORD_MIN, PASSWORD_MAX, adminPassword.length()));
		}
	}
}
