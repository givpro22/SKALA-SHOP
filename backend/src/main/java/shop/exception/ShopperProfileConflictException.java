package shop.exception;

/**
 * BR-32 — 구매자 프로필 자동 생성 시 {@code username}과 같은 {@code email}을 가진 고객이 이미 존재.
 *
 * <p><b>기존 고객에 자동으로 결합하지 않는다.</b> 이메일 소유를 확인하는 절차가 이 앱에 없으므로,
 * 결합을 허용하면 <b>남의 이메일로 가입해 그 고객의 포인트와 주문 이력을 획득하는 경로</b>가 열린다.
 * {@code INVALID_CREDENTIALS}가 아이디 존재 여부를 감춘 것과 같은 판단이다.
 *
 * <p>동시 요청 두 건이 각각 "없음"을 읽고 둘 다 만드는 경우는 {@code shopper_profile}의 DB unique
 * 제약이 막고, 그 실패도 이 예외로 번역된다(스펙 §13.1) — 애플리케이션 검사만으로는 못 막는다.
 */
public class ShopperProfileConflictException extends BusinessException {

	public ShopperProfileConflictException(String username) {
		super(ErrorCode.SHOPPER_PROFILE_CONFLICT,
				"이미 사용 중인 이메일이라 구매자 정보를 만들 수 없습니다. (%s)".formatted(username));
	}
}
