package shop.security;

import java.io.IOException;

import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import shop.dto.ErrorResponse;
import shop.exception.ErrorCode;

/**
 * 필터 체인에서 발생한 실패를 <b>컨트롤러 실패와 같은 형태</b>로 써 준다.
 *
 * <p>이것이 없으면 인증 실패만 Spring Security 기본 응답(빈 본문 401 또는 HTML)이 되어
 * 프론트의 에러 파서가 `code` 를 못 찾는다. 계약 §4.1 은 "상태코드와 무관하게 형태가 같다"고
 * 못박았고, 그 약속은 컨트롤러 밖에서도 지켜져야 한다.
 *
 * <p>{@code ObjectMapper} 를 주입받아 쓰는 이유는 날짜 직렬화 형식 때문이다. 새로 만들면
 * {@code JacksonConfig} 의 설정이 빠져 이 응답의 {@code timestamp} 만 형식이 달라진다.
 */
@Component
@RequiredArgsConstructor
public class ErrorResponseWriter {

	private final ObjectMapper objectMapper;

	public void write(HttpServletRequest request, HttpServletResponse response, ErrorCode errorCode)
			throws IOException {

		response.setStatus(errorCode.status().value());
		response.setContentType(MediaType.APPLICATION_JSON_VALUE);
		response.setCharacterEncoding("UTF-8");
		objectMapper.writeValue(response.getWriter(),
				ErrorResponse.of(errorCode, errorCode.defaultMessage(), request.getRequestURI()));
	}
}
