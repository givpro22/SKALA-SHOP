package shop.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.hibernate.boot.Metadata;
import org.hibernate.boot.MetadataSources;
import org.hibernate.boot.registry.StandardServiceRegistry;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * {@code app_user.role} 이 <b>기존 DB 업그레이드에서 계정을 승격시키지 않는지</b> 지키는 회귀 테스트.
 *
 * <h2>이 테스트가 막는 결함</h2>
 *
 * <p>{@code role} 은 나중에 추가된 {@code NOT NULL} 컬럼이다. 이미 계정이 있는 MySQL DB 에
 * {@code ddl-auto=update} 로 올리면 Hibernate 가 {@code ALTER TABLE ... ADD COLUMN} 을 내는데,
 * {@code DEFAULT} 가 없으면 MySQL 이 <b>{@code ENUM} 의 첫 값</b>을 채운다. 처음에는 그 값이
 * {@code ADMIN} 이어서 <b>기존 계정 전원이 관리자가 됐다</b>(prod DB 에서 실제 관측).
 *
 * <p><b>이 결함은 조용하다.</b> 빈 DB 로 처음 배포하면 재현되지 않고, 기동도 헬스체크도 정상이며,
 * 애플리케이션 코드는 아무 잘못이 없다 — 역할을 부여한 것은 스키마 마이그레이션이다.
 * 그래서 <b>사람이 눈으로 볼 수 있는 실패 신호가 없고</b>, 되돌려도 H2 기반 테스트는 전부 통과한다.
 *
 * <h2>왜 애너테이션 확인이 아니라 DDL 을 생성해서 보는가</h2>
 *
 * <p>{@code @ColumnDefault} 가 붙어 있는지만 보면 <b>"애너테이션이 실제 DDL 에 반영되는가"를
 * 확인하지 못한다.</b> Hibernate 버전이나 매핑 방식이 바뀌어 무시되더라도 그 확인은 통과한다.
 * 여기서는 <b>MySQL 방언으로 실제 스키마를 생성해</b> 문자열을 읽는다 — 배포본이 받게 될 것과 같은
 * 경로다. (DB 연결은 필요 없다. 오프라인 스크립트 생성이다.)
 */
@DisplayName("app_user.role 마이그레이션 안전장치")
class UserRoleSchemaDefaultTest {

	/**
	 * MySQL 방언으로 {@code CREATE} 스크립트를 <b>오프라인 생성</b>한다 — DB 연결이 없어도 된다.
	 *
	 * <p>{@code allow_jdbc_metadata_access=false} 가 그 열쇠다. 켜 두면 Hibernate 가 방언을
	 * 확정하려고 실제 커넥션을 잡으려 하고, 커넥션이 없으면 부팅이 실패한다.
	 */
	private String generateMySqlDdl(Path dir) throws IOException {
		Path script = dir.resolve("schema.sql");
		StandardServiceRegistry registry = new StandardServiceRegistryBuilder()
				.applySetting("hibernate.dialect", "org.hibernate.dialect.MySQLDialect")
				.applySetting("hibernate.boot.allow_jdbc_metadata_access", "false")
				.applySetting("jakarta.persistence.schema-generation.scripts.action", "create")
				.applySetting("jakarta.persistence.schema-generation.scripts.create-target",
						script.toString())
				.build();
		try {
			Metadata metadata = new MetadataSources(registry)
					.addAnnotatedClass(User.class)
					.buildMetadata();
			// SessionFactory 를 만드는 시점에 위 설정이 스크립트를 쓴다.
			metadata.buildSessionFactory().close();
		} finally {
			StandardServiceRegistryBuilder.destroy(registry);
		}
		return Files.readString(script);
	}

	/**
	 * {@code role} 컬럼 정의만 잘라낸다.
	 *
	 * <p><b>쉼표로 쪼개면 안 된다</b> — 생성되는 타입이 {@code enum ('ADMIN','SHOPPER')} 이라
	 * 괄호 안에 쉼표가 들어 있고, 단순 분리는 {@code role enum ('ADMIN'} 에서 끊겨
	 * {@code default} 절을 놓친다. 실제로 처음에 그렇게 짰다가 <b>가드가 멀쩡한데 테스트가
	 * 실패하는</b> 상태를 만들었다.
	 */
	private String roleColumnLine(String ddl) {
		Matcher m = Pattern.compile("\\brole\\s+enum\\s*\\([^)]*\\)[^,)]*",
				Pattern.CASE_INSENSITIVE).matcher(ddl);
		if (!m.find()) {
			throw new AssertionError(
					"생성된 DDL 에서 role 컬럼을 찾지 못했다. 매핑이 바뀌었는지 확인하라.\nDDL:\n" + ddl);
		}
		return m.group();
	}

	@Test
	@DisplayName("MySQL DDL 의 role 컬럼에 SHOPPER 기본값이 실제로 들어간다")
	void roleColumnHasShopperDefaultInGeneratedDdl(@TempDir Path dir) throws IOException {
		String column = roleColumnLine(generateMySqlDdl(dir)).toLowerCase(Locale.ROOT);

		assertThat(column)
				.as("role 컬럼에 DEFAULT 가 없으면 기존 DB 업그레이드에서 MySQL 이 ENUM 첫 값을 채운다. "
						+ "생성된 컬럼 정의: %s", column)
				.contains("default")
				.contains("shopper");
	}

	@Test
	@DisplayName("생성된 enum 목록의 첫 값은 ADMIN 이다 — 그래서 DEFAULT 를 생략할 수 없다")
	void implicitDefaultWouldBeAdmin(@TempDir Path dir) throws IOException {
		String column = roleColumnLine(generateMySqlDdl(dir));
		String enumList = column.substring(column.indexOf('('), column.indexOf(')') + 1);

		assertThat(enumList.replace(" ", ""))
				.as("Hibernate 는 네이티브 enum 값을 **알파벳 순**으로 생성한다. 따라서 Java 선언 순서를 "
						+ "바꿔도 implicit default 는 항상 ADMIN 이며, 선언 순서로는 이 결함을 막을 수 없다. "
						+ "이 사실이 바뀌면(예: Hibernate 가 선언 순서를 쓰게 되면) 위 default 검증의 "
						+ "근거 설명을 갱신해야 한다. 실제 컬럼: %s", column)
				.startsWith("('ADMIN'");
	}

	@Test
	@DisplayName("가입 계정은 SHOPPER, 관리자는 전용 팩터리로만 만들어진다")
	void factoriesAssignExpectedRoles() {
		assertThat(User.create("kim@skala.shop", "hash").getRole()).isEqualTo(UserRole.SHOPPER);
		assertThat(User.createAdmin("admin@skala.shop", "hash").getRole()).isEqualTo(UserRole.ADMIN);
	}
}
