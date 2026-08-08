package shop.domain;

/**
 * 계정 역할. 도메인 스펙 §9.4.
 *
 * <p>역할이 둘이고 경계가 <b>경로로</b> 갈라져 있으므로({@code /api/shop/**} vs 나머지)
 * 권한 테이블·리소스별 ACL 없이 필드 하나로 표현된다. 확장 가능성을 위해 구조를 미리 만드는 것은
 * 지금 없는 요구에 지금 비용을 치르는 것이다.
 *
 * <p>{@code @Enumerated(EnumType.STRING)}으로 저장한다 — ordinal 저장은 값이 추가되면 기존 행의
 * 의미가 바뀐다({@link OrderStatus}와 같은 판단).
 *
 * <h2>선언 순서로는 마이그레이션 사고를 막을 수 없다 — 측정으로 확인했다</h2>
 *
 * <p>이 프로젝트는 실제 사고를 겪었다. {@code role}은 나중에 추가된 {@code NOT NULL} 컬럼이라,
 * 이미 계정이 있는 MySQL DB에 {@code ddl-auto=update}로 올리면
 * {@code ALTER TABLE app_user ADD COLUMN role ... NOT NULL}이 나가고, <b>MySQL은 {@code DEFAULT}가
 * 없는 {@code NOT NULL} 컬럼을 추가할 때 {@code ENUM}의 첫 값을 채운다.</b> 그래서
 * <b>기존 계정 전원이 {@code ADMIN}으로 승격됐다</b>(prod DB에서 실제 관측).
 *
 * <p>고칠 때 <b>"{@code SHOPPER}를 먼저 선언하면 implicit default가 안전해진다"고 생각했는데,
 * 생성된 DDL을 실제로 뽑아 보니 틀렸다.</b> Hibernate 6은 네이티브 {@code enum} 타입을 만들 때
 * 값을 <b>알파벳 순으로</b> 늘어놓는다.
 *
 * <pre>role enum ('ADMIN','SHOPPER') default 'SHOPPER' not null</pre>
 *
 * <p>{@code ADMIN}이 알파벳상 앞서므로 <b>Java 선언 순서를 어떻게 바꿔도 implicit default는 항상
 * {@code ADMIN}이다.</b> 따라서 선언 순서는 이 결함에 아무 방어도 되지 못한다 —
 * 그렇게 고쳤다면 <b>동작하지 않는 안전장치를 주석으로 보증한 셈</b>이 됐을 것이다.
 *
 * <p><b>실제 방어는 {@code User.role}의 {@code @ColumnDefault("'SHOPPER'")} 하나뿐이고</b>,
 * 그것이 생성 DDL에 실제로 들어가는지는 {@code UserRoleSchemaDefaultTest}가 <b>DDL을 생성해서</b>
 * 확인한다. 애너테이션이 붙어 있는지만 보는 확인으로는 부족하다 — 매핑이 바뀌어 무시되어도 통과한다.
 */
public enum UserRole {

	/** 운영자. 상품·고객·관리자 주문 API를 쓴다. 구매하지 않는다. */
	ADMIN,

	/** 구매자. {@code /api/shop/**}를 쓴다. 상품·고객을 수정하지 않는다. */
	SHOPPER
}
