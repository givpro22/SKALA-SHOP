package shop.service;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import shop.domain.User;
import shop.dto.LoginRequest;
import shop.dto.LoginResponse;
import shop.dto.SignupRequest;
import shop.dto.UserResponse;
import shop.exception.DuplicateUsernameException;
import shop.exception.InvalidCredentialsException;
import shop.repository.UserRepository;
import shop.security.JwtTokenProvider;

/** 계약 §8. 인증 규칙은 전부 여기 있다 — Controller 에 조건문을 두지 않는다. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AuthService {

	private final UserRepository userRepository;
	private final PasswordEncoder passwordEncoder;
	private final JwtTokenProvider tokenProvider;

	@Transactional
	public UserResponse signup(SignupRequest request) {
		if (userRepository.existsByUsername(request.username())) {
			throw new DuplicateUsernameException(request.username());
		}
		// 평문은 여기서 즉시 해시로 바뀌고, 그 뒤로는 어디에도 남지 않는다.
		User user = userRepository.save(
				User.create(request.username(), passwordEncoder.encode(request.password())));
		return UserResponse.from(user);
	}

	public LoginResponse login(LoginRequest request) {
		User user = userRepository.findByUsername(request.username())
				.orElseThrow(InvalidCredentialsException::new);

		if (!passwordEncoder.matches(request.password(), user.getPassword())) {
			throw new InvalidCredentialsException();
		}

		return new LoginResponse(
				tokenProvider.issue(user.getUsername()),
				tokenProvider.validitySeconds(),
				user.getUsername());
	}

	/**
	 * 토큰의 주인을 되돌려준다.
	 *
	 * <p>토큰이 유효해도 <b>그 사이에 계정이 지워졌을 수 있다.</b> 토큰만 믿고 username 을
	 * 그대로 반환하면 없는 계정으로 로그인한 화면이 만들어지므로 DB 를 다시 확인한다.
	 */
	public UserResponse me(String username) {
		return userRepository.findByUsername(username)
				.map(UserResponse::from)
				.orElseThrow(InvalidCredentialsException::new);
	}
}
