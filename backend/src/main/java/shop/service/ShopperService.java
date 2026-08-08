package shop.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import shop.domain.Customer;
import shop.domain.ShopperProfile;
import shop.dto.ShopperResponse;
import shop.exception.InvalidCredentialsException;
import shop.repository.CartRepository;
import shop.repository.UserRepository;

/**
 * {@code GET /api/shop/me} — 로그인한 사람이 쇼핑에서 누구인가. 계약 §9.3.2.
 *
 * <p><b>이 엔드포인트만 역할 제한이 없다</b>(§9.4.2). {@code ADMIN}으로 로그인한 프론트도 관리
 * 메뉴를 보일지 판단하려면 {@code role}을 알아야 하는데, {@code role}은 {@code UserResponse}
 * (3필드 고정)에 없고 넣지 않기로 했으므로 여기가 유일한 전달 경로다. {@code ADMIN}이 호출하면
 * {@code role: "ADMIN"}, {@code customerId: null}, {@code point: null}, {@code cartItemCount: 0}이 온다.
 *
 * <p><b>조회이며 아무것도 생성하지 않는다.</b> shop 쓰기 경로가 프로필을 자동 생성하는 것과 대비된다 —
 * 여기서도 만들면 관리자가 화면을 열기만 해도 관리자 이름의 고객 레코드가 고객 목록에 생긴다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ShopperService {

	private final UserRepository userRepository;
	private final CartRepository cartRepository;
	private final ShopperProfileService shopperProfileService;

	/**
	 * 토큰이 유효해도 <b>그 사이에 계정이 지워졌을 수 있다</b> — {@code AuthService.me}와 같은 이유로
	 * DB를 다시 확인한다. 토큰만 믿으면 없는 계정으로 로그인한 화면이 만들어진다.
	 */
	public ShopperResponse me(String username) {
		return shopperProfileService.findProfile(username)
				.map(this::withProfile)
				.orElseGet(() -> ShopperResponse.withoutProfile(
						userRepository.findByUsername(username)
								.orElseThrow(InvalidCredentialsException::new)));
	}

	private ShopperResponse withProfile(ShopperProfile profile) {
		Customer customer = profile.getCustomer();
		/*
		 * 라인 수만 필요하므로 fetch join 으로 상품까지 끌어오는 조회를 쓰지 않는다. 헤더 배지는
		 * 화면 전환마다 호출되는 경로라, 여기서 카트 전체를 직렬화 가능한 형태로 만들면 가장 잦은
		 * 요청이 가장 무거워진다.
		 */
		int cartItemCount = cartRepository.findByCustomerId(customer.getId())
				.map(cart -> cart.getItems().size())
				.orElse(0);
		return ShopperResponse.of(profile.getUser(), customer, cartItemCount);
	}
}
