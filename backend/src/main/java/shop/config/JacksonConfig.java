package shop.config;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateTimeDeserializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateTimeSerializer;

/**
 * API 계약 §0.1 — 모든 {@code LocalDateTime}은 {@code 2026-08-05T14:30:00} 형태로만 직렬화한다.
 *
 * <p>{@code write-dates-as-timestamps: false}만 켜면 ISO-8601이 되지만, 나노초가 0이 아닐 때
 * {@code 2026-08-05T14:30:00.123456}처럼 소수점 이하가 붙는다. 계약은 밀리초 없음을 요구하므로
 * 포매터를 직접 고정한다. DTO 필드의 {@code @JsonFormat}과 중복이지만, 한쪽이 빠져도 계약이
 * 깨지지 않도록 양쪽에 둔다.
 */
@Configuration
public class JacksonConfig {

	public static final String DATE_TIME_PATTERN = "yyyy-MM-dd'T'HH:mm:ss";

	private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern(DATE_TIME_PATTERN);

	@Bean
	Jackson2ObjectMapperBuilderCustomizer localDateTimeCustomizer() {
		return builder -> builder
				.serializerByType(LocalDateTime.class, new LocalDateTimeSerializer(FORMATTER))
				.deserializerByType(LocalDateTime.class, new LocalDateTimeDeserializer(FORMATTER));
	}
}
