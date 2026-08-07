package shop.controller;

import java.net.URI;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import shop.dto.ErrorResponse;
import shop.dto.LoginRequest;
import shop.dto.LoginResponse;
import shop.dto.SignupRequest;
import shop.dto.UserResponse;
import shop.service.AuthService;

@Tag(name = "인증", description = "계정 등록 · 로그인 · 현재 계정 조회")
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

	private final AuthService authService;

	@Operation(summary = "계정 등록", description = "아이디가 중복되면 409를 반환한다.")
	@ApiResponses({
			@ApiResponse(responseCode = "201", description = "등록됨"),
			@ApiResponse(responseCode = "400", description = "VALIDATION_ERROR",
					content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
			@ApiResponse(responseCode = "409", description = "DUPLICATE_USERNAME",
					content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
	})
	@PostMapping("/signup")
	public ResponseEntity<UserResponse> signup(@Valid @RequestBody SignupRequest request) {
		UserResponse created = authService.signup(request);
		return ResponseEntity.created(URI.create("/api/auth/users/" + created.userId())).body(created);
	}

	@Operation(summary = "로그인",
			description = "성공하면 Bearer 토큰을 반환한다. 아이디 없음과 비밀번호 불일치를 구분하지 않는다.")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "토큰 발급"),
			@ApiResponse(responseCode = "400", description = "VALIDATION_ERROR",
					content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
			@ApiResponse(responseCode = "401", description = "INVALID_CREDENTIALS",
					content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
	})
	@PostMapping("/login")
	public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
		return ResponseEntity.status(HttpStatus.OK).body(authService.login(request));
	}

	@Operation(summary = "현재 계정 조회", description = "토큰의 주인을 반환한다.",
			security = @SecurityRequirement(name = "bearerAuth"))
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "조회됨"),
			@ApiResponse(responseCode = "401", description = "UNAUTHORIZED / TOKEN_EXPIRED",
					content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
	})
	@GetMapping("/me")
	public ResponseEntity<UserResponse> me(@AuthenticationPrincipal String username) {
		return ResponseEntity.ok(authService.me(username));
	}
}
