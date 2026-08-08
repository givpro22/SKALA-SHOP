package shop.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
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
 * <h2>이름이 이미 쓰이고 있으면 기동을 끊는다 — 승격시키지 않는다</h2>
 *
 * <p>{@code ADMIN}이 0건이어도 <b>같은 username 이 다른 역할로 존재할 수 있다.</b> 실제 경로가 있다:
 * {@code role} 컬럼 사고를 정리하려고 전량 강등({@code UPDATE ... SET role='SHOPPER'})하면
 * {@code admin@skala.shop}이 SHOPPER 로 남는데, 그 이름은 {@code local} 시드가 쓰는 이름이라
 * 운영자가 {@code ADMIN_USERNAME}으로 그대로 고를 가능성이 높다.
 *
 * <p>그대로 저장하면 unique 제약 위반으로 <b>{@code DataIntegrityViolationException}</b>이 나고,
 * 그 메시지는 무엇을 고쳐야 하는지 알려주지 않는다. 컨테이너 재시작 정책에 따라
 * <b>무한 재시작</b>으로 남는다. 그래서 저장 전에 확인하고 {@link IllegalStateException}으로 끊는다 —
 * <b>조용한 실패를 시끄러운 실패로 바꾸는 것</b>이 이 검사의 전부다.
 *
 * <p><b>사전 검사와 저장 실패 번역을 둘 다 두는 이유</b>(계약 §9.4.5 조건 3): 계약이 요구하는 것은
 * <b>"어떤 실행 순서에서도 기동 실패 + 읽을 수 있는 원인"</b>이지 특정 수단이 아니다. 사전 검사만으로는
 * 검사와 저장 사이의 인터리빙을 막지 못한다 — 공개 {@code signup} 유입, 그리고 <b>두 인스턴스가 동시에
 * 기동하는 경우</b>(컨테이너에서는 평범한 조건이다). 사전 검사로 걸러도, 저장 실패를 번역해도, 둘 다 해도
 * <b>관측 결과가 같으면 계약 준수</b>다.
 *
 * <p><b>기존 계정을 승격시키는 선택지는 없다.</b> {@code POST /api/auth/signup}은 공개이므로,
 * 승격을 허용하면 <b>운영자가 쓸 이메일을 미리 가입해 두는 것이 곧 관리자 권한 획득</b>이 된다.
 * §9.4.5가 "first user wins"를 기각한 이유가 정확히 그것이다.
 *
 * <p><b>알려진 한계 — 공개 {@code signup}으로 부트스트랩 username을 선점당하면 기동이 막힌다</b>
 * (계약 §9.4.5 한계 표 · §9.4.6). <b>감수하는 이유:</b> 코드로 막으려면 {@code signup}을 제한해야 하고,
 * <b>그 방어의 대가가 구매자 가입 기능 자체</b>다. <b>대응:</b> §9.4.6의 username 선택 규칙으로
 * 무력화한다 — 공격은 정확한 값을 알아야 성립하므로, 추측 불가능한 값이면 성립하지 않는다.
 * 아래 {@link #SEEDED_ADMIN_USERNAME} 거부가 그 규칙을 강제하는 지점이다.
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

	/**
	 * {@code local} 시드가 쓰는 관리자 아이디. <b>{@code prod} 에서는 이 값을 거부한다.</b>
	 *
	 * <p>소스·문서·캡처에 전부 적혀 있어 <b>공개된 이름</b>이며, 배포본이 그대로 쓰면 선점·추측의
	 * 표적이 된다. {@code ProdEnvironmentGuard} 가 {@code JWT_SECRET} 의 개발 기본값을 거부하는 것과
	 * 같은 판단이다 — 개발용으로 알려진 값이 배포본에 흘러드는 것을 기동 시점에 끊는다.
	 */
	private static final String SEEDED_ADMIN_USERNAME = "admin@skala.shop";

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

		/*
		 * 같은 username 이 이미 있으면 **여기서 끊는다.**
		 *
		 * 이 검사가 없으면 저장이 unique 제약에 걸려 DataIntegrityViolationException 이 나는데,
		 * 그 예외는 무엇을 어떻게 고쳐야 하는지 알려주지 않는다. §9.4.5 가 자격증명 누락에
		 * 친절한 메시지를 붙인 것과 같은 이유로, 이 실패도 같은 형태여야 한다.
		 *
		 * **기존 계정을 ADMIN 으로 승격시키지 않는다.** 승격시키면 공개 엔드포인트인
		 * POST /api/auth/signup 으로 운영자가 쓸 이메일을 선점하는 것이 곧 관리자 권한 획득이 되어,
		 * §9.4.5 가 기각한 "first user wins" 를 뒷문으로 들이게 된다. 스키마 마이그레이션이
		 * 권한을 부여했던 결함(UserRole 주석)과도 같은 부류다.
		 */
		if (userRepository.existsByUsername(adminUsername)) {
			throw new IllegalStateException(
					("ADMIN_USERNAME 으로 지정한 계정이 이미 존재합니다: %s "
							+ "(ADMIN 이 아닌 계정입니다). 다른 ADMIN_USERNAME 을 쓰거나 해당 계정을 정리한 뒤 "
							+ "다시 기동해 주세요. 기존 계정을 관리자로 승격시키지는 않습니다 — "
							+ "가입으로 이름을 선점하는 것이 권한 획득이 되어서는 안 되기 때문입니다.")
							.formatted(adminUsername));
		}

		User admin;
		try {
			/*
			 * 위 검사와 저장 사이에 같은 이름으로 가입이 들어오는 경쟁 조건이 남는다.
			 * saveAndFlush 로 즉시 INSERT 해서 그 위반을 **이 try 안에서** 잡고 같은 예외로 번역한다.
			 * 지연시키면 commit 시점에 터져 잡을 코드가 남아 있지 않다
			 * (ShopperProfileService.createProfile 과 같은 패턴).
			 */
			admin = userRepository.saveAndFlush(
					User.createAdmin(adminUsername, passwordEncoder.encode(adminPassword)));
		} catch (DataIntegrityViolationException e) {
			throw new IllegalStateException(
					"ADMIN 부트스트랩 중 계정 아이디가 충돌했습니다: %s (동시에 같은 아이디로 가입이 있었습니다). 다시 기동해 주세요."
							.formatted(adminUsername), e);
		}

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
		/*
		 * 시드에 적힌 이름은 prod 에서 거부한다.
		 *
		 * ProdEnvironmentGuard 가 JWT_SECRET 에 "local-dev" 가 들어 있으면 거부하는 것과
		 * **같은 패턴·같은 이유**다 — 개발용으로 알려진 값이 배포본에 그대로 쓰이는 것을 막는다.
		 *
		 * 이 검사가 선점 공격 방어의 실질이다. 공격이 성립하려면 공격자가 ADMIN_USERNAME 을
		 * **정확히** 알아야 하는데, 소스와 문서에 적혀 있는 이 값이 추측 대상 1순위다.
		 * 권고로 두면 지켜지지 않고, 지켜지지 않은 결과는 기동 불가다.
		 *
		 * **대신 쓸 값을 여기에 박지 않는다.** 박는 순간 그 값이 새로운 "알려진 이름"이 되어
		 * 이 검사가 막으려던 상태를 코드가 스스로 만든다.
		 */
		if (SEEDED_ADMIN_USERNAME.equalsIgnoreCase(adminUsername.trim())) {
			throw new IllegalStateException(
					("ADMIN_USERNAME 으로 %s 를 쓸 수 없습니다. 이 값은 local 시드와 문서에 적혀 있어 "
							+ "공개된 이름이며, 배포본의 관리자 아이디로 쓰면 선점·추측의 표적이 됩니다. "
							+ "배포 환경에서만 아는 다른 아이디를 지정해 주세요.")
							.formatted(SEEDED_ADMIN_USERNAME));
		}
		if (adminPassword.length() < PASSWORD_MIN || adminPassword.length() > PASSWORD_MAX) {
			throw new IllegalStateException(
					"ADMIN_PASSWORD 는 %d자 이상 %d자 이하여야 합니다. (현재 %d자) — 계약 §8.2 와 같은 규칙입니다."
							.formatted(PASSWORD_MIN, PASSWORD_MAX, adminPassword.length()));
		}
	}
}
