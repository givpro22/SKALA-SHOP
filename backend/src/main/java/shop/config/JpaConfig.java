package shop.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/**
 * 도메인 스펙 §1 — {@code createdAt}/{@code updatedAt}은 {@code @CreatedDate} /
 * {@code @LastModifiedDate}로 자동 설정한다. 이 애너테이션은 감사(auditing)가 켜져 있을 때만
 * 동작하므로, 엔티티에 애너테이션만 붙이고 여기를 빠뜨리면 두 필드가 조용히 {@code null}로 남는다.
 */
@Configuration
@EnableJpaAuditing
public class JpaConfig {
}
