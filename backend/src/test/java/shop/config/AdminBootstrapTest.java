package shop.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import shop.domain.User;
import shop.domain.UserRole;
import shop.repository.UserRepository;

/**
 * {@code prod} ADMIN 부트스트랩 검증. 계약 §9.4.5.
 *
 * <h2>왜 이 테스트가 요구되었는가</h2>
 *
 * <p>부트스트랩은 {@code @Profile("prod")}이라 <b>{@code local}에서는 한 번도 실행되지 않는다.</b>
 * 이번 결함이 정확히 그 구조에서 나왔다 — {@code User.createAdmin}의 호출부가 {@code local} 시드
 * 하나뿐이어서 {@code prod}에 관리자가 영원히 생기지 않았고, <b>기동·헬스체크·조회가 전부 정상이라
 * 아무 신호도 없었다.</b>
 *
 * <p>같은 구조가 하나 더 있었다 — BR-31(구매자 프로필 자동 생성)도 {@code local}에서는 시드가
 * 대신 만들어 죽어 있었고 {@code prod}에서 처음 실행됐다. <b>정상이었던 것은 운이지 검증이 아니다.</b>
 * 그래서 이 경로를 {@code prod} 배포를 기다리지 않고 여기서 검증한다.
 *
 * <p>러너가 자격증명을 <b>생성자로</b> 받기 때문에 프로파일을 켜지 않고도 그대로 만들어 돌릴 수 있다.
 * 필드 주입이었으면 이 테스트를 쓸 수 없어 {@code prod} 전용 경로가 또 미검증으로 남았을 것이다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("prod ADMIN 부트스트랩 (계약 §9.4.5)")
class AdminBootstrapTest {

	private static final String USERNAME = "operator@skala.shop";
	private static final String PASSWORD = "bootstrap1234";

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private PasswordEncoder passwordEncoder;

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@BeforeEach
	void clearAccounts() {
		userRepository.deleteAll();
	}

	private AdminBootstrapRunner runner(String username, String password) {
		return new AdminBootstrapRunner(userRepository, passwordEncoder, username, password);
	}

	@Test
	@DisplayName("1 — ADMIN 0건 + 자격증명 있음 → 1건 생성, 비밀번호가 인코딩되어 저장된다")
	void createsAdminWhenNoneExists() {
		runner(USERNAME, PASSWORD).run();

		assertThat(userRepository.countByRole(UserRole.ADMIN)).isEqualTo(1);

		User admin = userRepository.findByUsername(USERNAME).orElseThrow();
		assertThat(admin.getRole()).isEqualTo(UserRole.ADMIN);
		assertThat(admin.getPassword())
				.as("평문을 저장하면 계정은 보이는데 로그인만 안 되거나, 유출 시 그대로 쓰인다")
				.isNotEqualTo(PASSWORD);
		assertThat(passwordEncoder.matches(PASSWORD, admin.getPassword()))
				.as("인코딩된 값이 원본과 일치해야 로그인이 된다")
				.isTrue();
	}

	@Test
	@DisplayName("2 — 두 번 실행해도 여전히 1건 (멱등)")
	void isIdempotent() {
		runner(USERNAME, PASSWORD).run();
		runner(USERNAME, PASSWORD).run();

		assertThat(userRepository.countByRole(UserRole.ADMIN))
				.as("조건이 'ADMIN 0건'이라 두 번째 실행은 아무 일도 하지 않아야 한다")
				.isEqualTo(1);
	}

	@Test
	@DisplayName("3 — ADMIN 1건 + 다른 자격증명 → 기존 계정이 바뀌지 않는다")
	void doesNotOverwriteExistingAdmin() {
		runner(USERNAME, PASSWORD).run();
		String originalHash = userRepository.findByUsername(USERNAME).orElseThrow().getPassword();

		runner("intruder@skala.shop", "totallydifferent9999").run();

		assertThat(userRepository.countByRole(UserRole.ADMIN)).isEqualTo(1);
		assertThat(userRepository.findByUsername("intruder@skala.shop"))
				.as("자격증명이 바뀌었다고 새 관리자가 생기면 안 된다")
				.isEmpty();
		assertThat(userRepository.findByUsername(USERNAME).orElseThrow().getPassword())
				.as("덮어쓰면 운영 중 환경변수 변경만으로 관리자 비밀번호가 조용히 리셋된다")
				.isEqualTo(originalHash);
	}

	@Test
	@DisplayName("4 — ADMIN 0건 + 자격증명 없음 → 기동 실패")
	void failsWhenCredentialsMissing() {
		assertThatThrownBy(() -> runner("", "").run())
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("ADMIN_USERNAME");

		assertThat(userRepository.countByRole(UserRole.ADMIN)).isZero();
	}

	@Test
	@DisplayName("4b — 자격증명 형식 위반(짧은 비밀번호 · 이메일 아님)도 기동 실패")
	void failsWhenCredentialsMalformed() {
		assertThatThrownBy(() -> runner(USERNAME, "short").run())
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("ADMIN_PASSWORD");

		assertThatThrownBy(() -> runner("not-an-email", PASSWORD).run())
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("이메일");
	}

	/**
	 * 결함 4 — {@code ADMIN}이 0건이어도 <b>같은 username 이 다른 역할로 있을 수 있다.</b>
	 *
	 * <p>실제 경로가 있다: {@code role} 컬럼 사고를 정리하려고 전량 강등하면
	 * {@code admin@skala.shop}이 SHOPPER 로 남고, 그 이름은 {@code local} 시드가 쓰는 이름이라
	 * 운영자가 {@code ADMIN_USERNAME}으로 그대로 고를 가능성이 높다.
	 *
	 * <p><b>이 단언은 "실패한다"가 아니라 "어떻게 실패하는가"를 본다.</b> 가드가 없어도 기동은
	 * 어차피 실패하지만({@code DataIntegrityViolationException}), 그 실패는 무엇을 고쳐야 하는지
	 * 알려주지 않고 컨테이너를 재시작 루프에 남긴다. 그래서 <b>예외 타입과 메시지에 이름이
	 * 들어가는지</b>를 단언한다 — 그것이 이 수정의 전부이기 때문이다.
	 */
	@Test
	@DisplayName("6 — username 이 SHOPPER 로 이미 있으면 IllegalStateException 으로 끊고, 승격시키지 않는다")
	void failsWhenUsernameAlreadyTaken() {
		userRepository.save(User.create(USERNAME, passwordEncoder.encode("preexisting1234")));

		assertThatThrownBy(() -> runner(USERNAME, PASSWORD).run())
				.as("unique 제약이 던지는 DataIntegrityViolationException 은 원인을 알려주지 않는다")
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining(USERNAME);

		User existing = userRepository.findByUsername(USERNAME).orElseThrow();
		assertThat(existing.getRole())
				.as("승격시키면 공개 signup 으로 이름을 선점하는 것이 곧 권한 획득이 된다 "
						+ "— §9.4.5 가 기각한 'first user wins' 를 뒷문으로 들이는 셈이다")
				.isEqualTo(UserRole.SHOPPER);
		assertThat(passwordEncoder.matches("preexisting1234", existing.getPassword()))
				.as("기존 계정의 비밀번호를 건드리지 않는다")
				.isTrue();
		assertThat(userRepository.countByRole(UserRole.ADMIN)).isZero();
	}

	/**
	 * 시드에 적힌 관리자 아이디는 {@code prod} 에서 거부한다.
	 *
	 * <p>{@code ProdEnvironmentGuard} 가 {@code JWT_SECRET} 의 개발 기본값을 거부하는 것과 같은 패턴이며,
	 * <b>username 선택이 선점 방어의 실질</b>이기 때문이다 — 공격이 성립하려면 공격자가
	 * {@code ADMIN_USERNAME} 을 정확히 알아야 하는데, 소스·문서·캡처에 전부 적힌 이 값이
	 * 추측 대상 1순위다. 권고로 두면 지켜지지 않고, 지켜지지 않은 결과는 기동 불가다.
	 */
	@Test
	@DisplayName("7 — 시드에 적힌 admin@skala.shop 은 ADMIN_USERNAME 으로 쓸 수 없다")
	void rejectsSeededAdminUsername() {
		assertThatThrownBy(() -> runner("admin@skala.shop", PASSWORD).run())
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("admin@skala.shop");

		assertThat(userRepository.countByRole(UserRole.ADMIN))
				.as("거부됐으므로 계정이 만들어지면 안 된다")
				.isZero();
	}

	@Test
	@DisplayName("7b — 대소문자만 다른 값도 거부한다 (이메일은 대소문자를 구분하지 않는다)")
	void rejectsSeededAdminUsernameIgnoringCase() {
		assertThatThrownBy(() -> runner("Admin@Skala.Shop", PASSWORD).run())
				.as("대소문자로 우회되면 검사가 사실상 없는 것과 같다")
				.isInstanceOf(IllegalStateException.class);
	}

	/**
	 * <b>이 테스트가 핵심이다.</b> 1~4번은 "계정이 만들어졌다"만 증명하고
	 * <b>"카탈로그를 채울 수 있다"는 증명하지 않는다.</b> 이번 결함의 실질은 "ADMIN이 없다"가 아니라
	 * <b>"카탈로그를 채울 수 없다"</b>였다 — 계정이 있어도 역할이 실제로 먹히지 않으면 같은 상태다.
	 */
	@Test
	@DisplayName("5 — 생성된 계정으로 POST /api/products 가 201 (실제로 카탈로그를 채울 수 있다)")
	void bootstrappedAdminCanCreateProduct() throws Exception {
		runner(USERNAME, PASSWORD).run();

		MvcResult login = mockMvc.perform(post("/api/auth/login")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"username":"%s","password":"%s"}""".formatted(USERNAME, PASSWORD)))
				.andExpect(status().isOk())
				.andReturn();

		JsonNode body = objectMapper.readTree(login.getResponse().getContentAsString());
		String token = body.get("accessToken").asText();

		mockMvc.perform(post("/api/products")
				.header("Authorization", "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"name":"부트스트랩 확인 상품","description":"관리자 권한 검증","price":1000,"stock":1}"""))
				.andExpect(status().isCreated());
	}
}
