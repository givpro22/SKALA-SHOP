package shop.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import shop.domain.Product;
import shop.dto.ProductCreateRequest;
import shop.dto.ProductResponse;
import shop.dto.ProductUpdateRequest;
import shop.exception.DuplicateProductNameException;
import shop.exception.ProductInUseException;
import shop.exception.ProductNotFoundException;
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

	@Transactional
	public ProductResponse create(ProductCreateRequest request) {
		if (productRepository.existsByName(request.name())) {
			throw new DuplicateProductNameException(request.name());
		}
		Product product = productRepository.save(Product.create(
				request.name(), request.description(), request.price(), request.stock()));
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
		product.update(request.name(), request.description(), request.price(), request.stock());
		return ProductResponse.from(product);
	}

	/**
	 * BR-17. 주문에 참조된 상품은 삭제할 수 없다.
	 *
	 * <p>참조 검사에 {@code status} 조건을 붙이지 않는다 — <b>취소된 주문의 라인도 참조로 센다.</b>
	 * 주문 이력은 보존 대상이고, 참조 상품이 사라지면 이력이 깨진다.
	 */
	@Transactional
	public void delete(Long id) {
		Product product = getProductOrThrow(id);
		if (orderItemRepository.existsByProductId(id)) {
			throw new ProductInUseException(id);
		}
		productRepository.delete(product);
	}

	private Product getProductOrThrow(Long id) {
		return productRepository.findById(id)
				.orElseThrow(() -> new ProductNotFoundException(id));
	}
}
