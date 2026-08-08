package shop.dto;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import shop.exception.InvalidSearchParameterException;

/**
 * 계약 §9.2.1 · §9.2.2 — 상품 검색 쿼리 파라미터를 검증해 굳힌 값.
 *
 * <p><b>검증을 애너테이션이 아니라 이 팩터리에 둔 이유.</b> {@code @RequestParam}에 붙인 제약은
 * Spring 버전에 따라 {@code ConstraintViolationException}이 되기도 하고
 * {@code HandlerMethodValidationException}이 되기도 한다 — 어느 쪽이 나오느냐로 <b>에러 코드가
 * 바뀌면 계약이 프레임워크 버전에 묶인다.</b> 여기서 던지면 {@code VALIDATION_ERROR}가 확정된다.
 *
 * <p>{@code sort}는 Spring 의 {@code sort=price,asc} 문법을 <b>노출하지 않는다.</b> 그 문법을 열면
 * 클라이언트가 아무 필드로나 정렬할 수 있고({@code sort=version,desc}) 존재하지 않는 필드는 500이
 * 된다. 정렬 가능한 축을 계약이 열거하면 그 둘이 모두 불가능해진다.
 */
public record ProductSearchQuery(String keyword, SortOption sort, int page, int size) {

	private static final int MAX_KEYWORD_LENGTH = 100;
	private static final int MAX_SIZE = 50;
	private static final int DEFAULT_PAGE = 0;
	private static final int DEFAULT_SIZE = 12;

	/**
	 * 허용 정렬 축. <b>모든 축에 {@code id} 2차 키가 붙는다.</b>
	 *
	 * <p>시드 20건은 같은 초에 생성되어 {@code createdAt}만으로는 순서가 결정되지 않고, 동일가 상품도
	 * 마찬가지다. tie-breaker가 없으면 목록이 흔들리는 것으로 끝나지 않고 <b>같은 상품이 1페이지와
	 * 2페이지에 동시에 나오거나 어느 페이지에도 나오지 않는다.</b>
	 */
	public enum SortOption {
		LATEST(Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"))),
		PRICE_ASC(Sort.by(Sort.Order.asc("price"), Sort.Order.asc("id"))),
		PRICE_DESC(Sort.by(Sort.Order.desc("price"), Sort.Order.desc("id")));

		private final Sort sort;

		SortOption(Sort sort) {
			this.sort = sort;
		}

		Sort sort() {
			return sort;
		}
	}

	/**
	 * 원시 쿼리 파라미터를 검증한다. 어긋나면 {@code 400 VALIDATION_ERROR}.
	 *
	 * <p>{@code size} 상한을 넘긴 요청을 <b>조용히 50으로 자르지 않는다</b> — 자르면 클라이언트는
	 * 자기가 요청한 만큼 받았다고 믿는다. 반면 <b>범위를 벗어난 {@code page}는 오류가 아니다</b>:
	 * 200 + 빈 {@code items}로 응답한다(§0.3 "없음이 정상 결과").
	 */
	public static ProductSearchQuery of(String q, String sort, Integer page, Integer size) {
		String keyword = q == null ? "" : q.trim();
		if (keyword.length() > MAX_KEYWORD_LENGTH) {
			throw new InvalidSearchParameterException("q", q,
					"검색어는 %d자 이하여야 합니다. (입력 길이: %d자)".formatted(MAX_KEYWORD_LENGTH, keyword.length()));
		}

		SortOption sortOption = parseSort(sort);

		int pageValue = page == null ? DEFAULT_PAGE : page;
		if (pageValue < 0) {
			throw new InvalidSearchParameterException("page", page, "page는 0 이상이어야 합니다.");
		}

		int sizeValue = size == null ? DEFAULT_SIZE : size;
		if (sizeValue < 1 || sizeValue > MAX_SIZE) {
			throw new InvalidSearchParameterException("size", size,
					"size는 1 이상 %d 이하여야 합니다.".formatted(MAX_SIZE));
		}

		return new ProductSearchQuery(keyword, sortOption, pageValue, sizeValue);
	}

	/**
	 * 빈 값·생략은 기본 {@code LATEST}, 그 밖의 알 수 없는 값은 400이다(§9.2.6 확정표).
	 *
	 * <p>{@code toUpperCase}로 대소문자를 흡수하지만 {@code price,asc} 같은 Spring 문법은 집합에
	 * 없으므로 거부된다 — 그것이 이 메서드의 목적이다.
	 */
	private static SortOption parseSort(String sort) {
		if (sort == null || sort.isBlank()) {
			return SortOption.LATEST;
		}
		try {
			return SortOption.valueOf(sort.trim().toUpperCase());
		} catch (IllegalArgumentException e) {
			throw new InvalidSearchParameterException("sort", sort,
					"허용되는 정렬 값은 LATEST, PRICE_ASC, PRICE_DESC 입니다.");
		}
	}

	public PageRequest toPageRequest() {
		return PageRequest.of(page, size, sort.sort());
	}
}
