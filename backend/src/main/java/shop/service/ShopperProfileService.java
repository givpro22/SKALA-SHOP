package shop.service;

import java.util.Optional;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import shop.domain.Cart;
import shop.domain.Customer;
import shop.domain.ShopperProfile;
import shop.domain.User;
import shop.exception.InvalidCredentialsException;
import shop.exception.ShopperProfileConflictException;
import shop.repository.CartRepository;
import shop.repository.CustomerRepository;
import shop.repository.ShopperProfileRepository;
import shop.repository.UserRepository;

/**
 * 로그인 계정 ↔ 구매자 신원의 유일한 연결 지점. 도메인 스펙 §8.4 · BR-31.
 *
 * <p><b>쓰기와 조회를 다른 메서드로 나눈 것이 이 클래스의 요지다.</b>
 *
 * <ul>
 *   <li>{@link #resolveForWrite} — 프로필이 없으면 <b>그 자리에서 만든다.</b> 장바구니 쓰기·체크아웃이
 *       호출한다.</li>
 *   <li>{@link #findCustomer} — <b>아무것도 만들지 않는다.</b> 카트 조회·내 주문 내역·
 *       {@code /api/shop/me}가 호출한다.</li>
 * </ul>
 *
 * <p><b>조회가 데이터를 만들면 안 된다.</b> 만들게 두면 목록 화면을 열기만 해도 고객 레코드가
 * 늘어나고, 관리자 고객 목록이 한 번도 구매하지 않은 유령 고객으로 채워진다. 두 메서드를 하나로
 * 합치고 플래그로 분기하면 새 호출부가 생길 때마다 그 플래그를 옳게 넘기는 데 의존하게 된다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ShopperProfileService {

	/**
	 * 가입 축하 포인트(BR-31). <b>0으로 시작하지 않는 이유:</b> 포인트가 결제 수단이고 충전은
	 * 관리자 전용 API({@code POST /api/customers/{id}/points})다. 0이면 신규 가입자는 관리자가
	 * 개입하기 전까지 아무것도 살 수 없고, 모든 주문이 {@code INSUFFICIENT_POINT}로 끝난다.
	 */
	public static final int WELCOME_POINT = 100_000;

	private final ShopperProfileRepository shopperProfileRepository;
	private final UserRepository userRepository;
	private final CustomerRepository customerRepository;
	private final CartRepository cartRepository;

	/** 조회 전용. 프로필이 없으면 비어 있는 값을 돌려주고 <b>만들지 않는다</b>(스펙 §8.4). */
	public Optional<Customer> findCustomer(String username) {
		return shopperProfileRepository.findByUsername(username).map(ShopperProfile::getCustomer);
	}

	public Optional<ShopperProfile> findProfile(String username) {
		return shopperProfileRepository.findByUsername(username);
	}

	/**
	 * BR-31 — 구매 <b>쓰기</b> 경로의 첫 단계. 프로필이 없으면 고객 · 프로필 · 빈 카트를 함께 만든다.
	 *
	 * <p>세 저장이 한 트랜잭션이어야 한다. 나누면 "고객은 생겼는데 카트가 없는" 상태가 만들어지고,
	 * 그 상태는 다음 요청에서 카트를 또 만들려 해 {@code customer_id} unique 제약에 걸린다.
	 *
	 * <p>이 메서드는 호출부의 트랜잭션에 <b>합류한다</b>(기본 전파 {@code REQUIRED}).
	 * {@code REQUIRES_NEW}로 열면 체크아웃이 실패해 롤백될 때 고객만 남는다.
	 */
	@Transactional
	public Customer resolveForWrite(String username) {
		return shopperProfileRepository.findByUsername(username)
				.map(ShopperProfile::getCustomer)
				.orElseGet(() -> createProfile(username));
	}

	private Customer createProfile(String username) {
		/*
		 * 토큰이 유효해도 그 사이에 계정이 지워졌을 수 있다. 토큰만 믿고 진행하면 없는 계정의
		 * 고객 레코드가 만들어진다(AuthService.me 와 같은 판단).
		 */
		User user = userRepository.findByUsername(username)
				.orElseThrow(InvalidCredentialsException::new);

		// BR-32 — 기존 고객에 자동으로 결합하지 않는다. 이메일 소유를 확인하는 절차가 이 앱에
		// 없으므로, 결합을 허용하면 남의 이메일로 가입해 그 고객의 포인트와 주문 이력을 획득하는
		// 경로가 열린다.
		if (customerRepository.existsByEmail(username)) {
			throw new ShopperProfileConflictException(username);
		}

		Customer customer = customerRepository.save(
				Customer.create(displayNameOf(username), username, WELCOME_POINT));

		try {
			/*
			 * saveAndFlush 로 즉시 INSERT 한다. 지연시키면 unique 위반이 commit 시점에 터지는데,
			 * 그때는 이 try 블록을 이미 빠져나간 뒤라 잡을 코드가 남아 있지 않고 500 으로 샌다.
			 * 동시 요청 두 건이 각각 "프로필 없음"을 읽는 경우를 애플리케이션 검사만으로는 못 막는다.
			 */
			shopperProfileRepository.saveAndFlush(ShopperProfile.create(user, customer));
		} catch (DataIntegrityViolationException e) {
			throw new ShopperProfileConflictException(username);
		}

		cartRepository.save(Cart.create(customer));
		return customer;
	}

	/**
	 * {@code kim@skala.shop} → {@code kim}. 이메일 전체를 이름으로 쓰면 고객 목록에서 이름 열과
	 * 이메일 열이 같은 값으로 채워져 화면이 읽히지 않는다.
	 */
	private String displayNameOf(String username) {
		int at = username.indexOf('@');
		return at > 0 ? username.substring(0, at) : username;
	}
}
