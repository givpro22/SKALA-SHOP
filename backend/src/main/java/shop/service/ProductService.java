package shop.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import shop.domain.Product;
import shop.dto.ProductCreateRequest;
import shop.dto.ProductPageResponse;
import shop.dto.ProductResponse;
import shop.dto.ProductSearchQuery;
import shop.dto.ProductUpdateRequest;
import shop.exception.DuplicateProductNameException;
import shop.exception.ProductInUseException;
import shop.exception.ProductNotFoundException;
import shop.repository.CartItemRepository;
import shop.repository.OrderItemRepository;
import shop.repository.ProductRepository;

/**
 * 상품 비즈니스 로직. 스펙 §4.3에 따라 클래스에 {@code readOnly = true}를 걸고
 * 쓰기 메서드에만 {@code @Transactional}을 재선언한다.
 *
 * <p>낙관적 락 충돌({@code CONCURRENT_UPDATE})을 여기서 try/catch 하지 않는다. 버전 검사는
 * flush 시점에 일어나고 그 flush는 {@code @Transactional} 프록시가 <b>메서드를 빠져나온 뒤</b>
 * commit하면서 수행하므로, 서비스 안의 catch 블록은 실행되지 않는 죽은 코드가 된다.
 * 전역 핸들러에서 {@code OptimisticLockingFailureException}을 잡는다(스펙 §5.1).
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProductService {

	private final ProductRepository productRepository;
	private final OrderItemRepository orderItemRepository;
	private final CartItemRepository cartItemRepository;

	@Transactional
	public ProductResponse create(ProductCreateRequest request) {
		if (productRepository.existsByName(request.name())) {
			throw new DuplicateProductNameException(request.name());
		}
		Product product = productRepository.save(Product.create(
				request.name(), request.description(), request.price(), request.stock(),
				request.imageUrl()));
		return ProductResponse.from(product);
	}

	/** 목록은 래핑 없는 배열로 내려간다(계약 §0.2). {@code Pageable}을 받지 않는다. */
	public List<ProductResponse> findAll() {
		return productRepository.findAll().stream()
				.map(ProductResponse::from)
				.toList();
	}

	public ProductResponse findById(Long id) {
		return ProductResponse.from(getProductOrThrow(id));
	}

	/**
	 * 계약 §9.1 — 구매자용 상품 검색·정렬·페이지네이션.
	 *
	 * <p><b>기존 {@link #findAll()}에 파라미터를 붙이지 않고 메서드를 나눴다.</b> 붙이면 같은 경로가
	 * 파라미터 유무에 따라 배열과 객체를 오가고, 프론트 타입이 {@code ProductResponse[] |
	 * ProductPageResponse}가 되어 기존 관리 화면·QA 체크리스트가 전부 흔들린다. 새 경로를 만드는
	 * 비용이 기존 경로를 다형적으로 만드는 비용보다 훨씬 싸다({@code [호환성 쟁점 C-3]}).
	 *
	 * <p>검증(정렬 축·크기 상한·검색어 길이)은 {@link ProductSearchQuery}가 이미 끝냈다. 여기서
	 * 다시 판정하지 않는다 — 두 곳에서 판정하면 언젠가 한쪽만 고쳐진다.
	 */
	public ProductPageResponse search(ProductSearchQuery query) {
		return ProductPageResponse.from(
				productRepository.findByNameContainingIgnoreCase(query.keyword(), query.toPageRequest()));
	}

	/**
	 * 전체 교체(PUT). 더티 체킹으로 반영되므로 {@code save}를 호출하지 않는다.
	 *
	 * <p>중복 검사에 {@code existsByNameAndIdNot}을 쓴다 — 이름을 바꾸지 않고 PUT했을 때
	 * 자기 자신과 충돌해 409가 나면 안 된다(BR-16).
	 */
	@Transactional
	public ProductResponse update(Long id, ProductUpdateRequest request) {
		Product product = getProductOrThrow(id);
		if (productRepository.existsByNameAndIdNot(request.name(), id)) {
			throw new DuplicateProductNameException(request.name());
		}
		product.update(request.name(), request.description(), request.price(), request.stock(),
				request.imageUrl());
		return ProductResponse.from(product);
	}

	/**
	 * BR-17. 주문에 참조된 상품은 삭제할 수 없다.
	 *
	 * <p>참조 검사에 {@code status} 조건을 붙이지 않는다 — <b>취소된 주문의 라인도 참조로 센다.</b>
	 * 주문 이력은 보존 대상이고, 참조 상품이 사라지면 이력이 깨진다.
	 *
	 * <p><b>BR-30 (2026-08-08 확장, {@code [호환성 쟁점 C-5]}) — 카트 라인은 판정 대상이 아니라
	 * 정리 대상이다.</b> 판정 대상에 넣으면 <b>다른 사람이 장바구니에 담아둔 것만으로 관리자가
	 * 상품을 삭제하지 못한다.</b> 카트는 구매 의사 표시일 뿐 이력이 아니다. 대신 삭제가 확정되면
	 * 참조 라인을 같은 트랜잭션에서 지운다 — <b>지우지 않으면 FK 위반으로 500이 난다.</b>
	 *
	 * <p>관측 계약은 그대로다: 성공은 여전히 204이고 {@code PRODUCT_IN_USE}의 근거는
	 * {@code OrderItem}뿐이다. 늘어난 것은 서비스 내부의 삭제 한 줄이다.
	 */
	@Transactional
	public void delete(Long id) {
		getProductOrThrow(id);
		if (orderItemRepository.existsByProductId(id)) {
			throw new ProductInUseException(id);
		}
		/*
		 * 벌크 삭제는 clearAutomatically 로 영속성 컨텍스트를 비운다. 위에서 읽은 Product 인스턴스를
		 * 그대로 delete(entity) 에 넘기면 준영속 상태라 merge 로 한 번 더 읽히므로, id 로 다시
		 * 지운다 — 삭제 대상이 두 경로로 갈라지지 않는다.
		 */
		cartItemRepository.deleteByProductId(id);
		productRepository.deleteById(id);
	}

	private Product getProductOrThrow(Long id) {
		return productRepository.findById(id)
				.orElseThrow(() -> new ProductNotFoundException(id));
	}
}
