package shop.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import shop.domain.Product;

public interface ProductRepository extends JpaRepository<Product, Long> {

	/**
	 * 계약 §9.2.2 — 상품 검색. <b>{@code name}만 대상이며 {@code description}은 대상이 아니다.</b>
	 *
	 * <p>{@code description}을 포함하면 시드 문구를 한 글자 고칠 때마다 검색 결과 건수가 바뀌어
	 * QA 기대값이 흔들린다. 판정 가능성을 실검색 품질보다 앞에 뒀다.
	 *
	 * <p>검색어가 없을 때도 <b>같은 메서드를 빈 문자열로 호출한다.</b> {@code like '%%'}는 전체와
	 * 일치하므로 분기가 필요 없다 — 조건부로 다른 쿼리를 쓰면 "전체 조회"와 "검색"의 정렬·페이징
	 * 동작이 갈라질 수 있다.
	 *
	 * <p>정렬은 {@link Pageable}이 나른다. <b>모든 정렬에 {@code id} 2차 키가 붙는다</b> —
	 * tie-breaker의 부재는 목록이 흔들리는 것으로 끝나지 않고 <b>같은 상품이 1페이지와 2페이지에
	 * 동시에 나오거나 어느 페이지에도 나오지 않는</b> 결과를 만든다.
	 */
	Page<Product> findByNameContainingIgnoreCase(String name, Pageable pageable);

	/** 등록 시 중복 검사(BR-16). */
	boolean existsByName(String name);

	/**
	 * 수정 시 중복 검사(BR-16). <b>자기 자신은 중복으로 보지 않는다.</b>
	 *
	 * <p>{@code existsByName}을 그대로 쓰면 이름을 바꾸지 않고 PUT했을 때 자기 자신과 충돌해
	 * 409가 난다.
	 */
	boolean existsByNameAndIdNot(String name, Long id);
}
