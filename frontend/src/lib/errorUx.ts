import type { ClientErrorCode } from '../api/client';

/**
 * 에러 코드 → 화면 처리 방식.
 *
 * `Record<ClientErrorCode, ErrorUx>` 로 선언했으므로 코드가 하나라도 빠지면 **컴파일이 실패한다.**
 * 매핑 누락이 런타임에 `undefined.title` 로 드러나는 일이 없다.
 *
 * `message` 를 여기 넣지 않는 이유: 서버가 내려보내는 `message` 에는 구체 수치가 들어 있다
 * ("보유 포인트: 10,000원, 주문 총액: 25,000원"). 프론트가 고정 문구로 덮으면 그 정보가 사라진다.
 * 여기서는 **짧은 제목과 재시도 여부만** 정하고 상세 문장은 서버 메시지를 그대로 보여준다.
 */
export interface ErrorUx {
  /** 배너 제목. 한눈에 무슨 일인지 드러나는 짧은 말. */
  title: string;
  /**
   * 같은 요청을 그대로 다시 보내면 성공할 가능성이 있는가.
   * `true` 면 재시도 버튼을 노출하고, `false` 면 아래 `hint` 로 왜 소용없는지 알려준다.
   */
  retryable: boolean;
  /** 재시도해도 소용없을 때 사용자가 무엇을 해야 하는지. */
  hint: string;
  /**
   * **재시도가 아닌 다른 동작으로만 풀리는 실패**의 버튼 문구. 없으면 `null`.
   *
   * `retryable` 이 "같은 요청을 그대로 다시 보내도 되는가"라면, 이것은 "그러면 안 되는데
   * 사용자가 할 수 있는 일이 있는가"다. 둘은 배타적이다 — `retryable: true` 이면서
   * 복구 버튼이 있는 코드는 없다.
   *
   * `CART_STALE` 이 지금 유일한 사례다. `retryable: false` 만으로는 버튼이 사라질 뿐이라
   * 사용자는 막다른 길에 남는다. **"장바구니 새로고침"** 이 그 자리를 대신한다.
   */
  recoverLabel: string | null;
}

/**
 * 계약 §5.1 — `CONCURRENT_UPDATE` 와 `OUT_OF_STOCK` 을 같은 에러 화면으로 묶지 않는다.
 *
 * | | OUT_OF_STOCK (400) | CONCURRENT_UPDATE (409) |
 * |---|---|---|
 * | 원인 | 재고가 실제로 모자람 | 재고는 충분했다. 다른 요청과 같은 행을 동시에 갱신 |
 * | 다시 시도하면 | **실패한다** | **성공할 가능성이 높다** |
 * | 재시도 버튼 | 노출하지 않음 | **노출** |
 *
 * 둘을 묶으면 사용자는 재고가 있는데도 포기하거나(409를 재고 부족으로 오인),
 * 없는 재고를 계속 두드리게 된다(400을 일시적 문제로 오인).
 */
export const ERROR_UX: Record<ClientErrorCode, ErrorUx> = {
  // --- 401 인증: 로그인 상태를 고쳐야 한다
  INVALID_CREDENTIALS: {
    title: '로그인 실패',
    retryable: false,
    hint: '아이디와 비밀번호를 다시 확인해 주세요.',
    recoverLabel: null,
  },
  UNAUTHORIZED: {
    title: '로그인이 필요합니다',
    retryable: false,
    hint: '오른쪽 위에서 로그인한 뒤 다시 시도해 주세요.',
    recoverLabel: null,
  },
  TOKEN_EXPIRED: {
    title: '로그인이 만료되었습니다',
    // 같은 요청을 그대로 보내면 또 만료다. 먼저 로그인해야 하므로 재시도 버튼을 주지 않는다.
    retryable: false,
    hint: '다시 로그인한 뒤 이어서 진행해 주세요.',
    recoverLabel: null,
  },
  DUPLICATE_USERNAME: {
    title: '이미 사용 중인 아이디',
    retryable: false,
    hint: '다른 아이디로 등록해 주세요.',
    recoverLabel: null,
  },

  // --- 400 요청이 잘못됨: 입력을 고쳐야 한다
  VALIDATION_ERROR: {
    title: '입력값을 확인해 주세요',
    retryable: false,
    hint: '아래 항목을 고친 뒤 다시 제출해 주세요.',
    recoverLabel: null,
  },
  MALFORMED_REQUEST: {
    title: '요청 형식 오류',
    retryable: false,
    hint: '입력값을 다시 확인해 주세요.',
    recoverLabel: null,
  },
  TYPE_MISMATCH: {
    title: '값 형식 오류',
    retryable: false,
    hint: 'id 는 숫자여야 합니다.',
    recoverLabel: null,
  },
  DUPLICATE_ORDER_ITEM: {
    title: '중복된 주문 항목',
    retryable: false,
    hint: '같은 상품이 두 번 담겼습니다. 한 줄로 합쳐 수량을 올려 주세요.',
    recoverLabel: null,
  },

  // --- 400 비즈니스 규칙 위반: 재시도해도 결과가 같다
  OUT_OF_STOCK: {
    title: '재고 부족',
    retryable: false, // 재고가 늘기 전까지 몇 번을 눌러도 같은 결과다
    hint: '재고가 채워질 때까지는 다시 시도해도 같은 결과입니다. 수량을 줄여 주세요.',
    recoverLabel: null,
  },
  INSUFFICIENT_POINT: {
    title: '포인트 부족',
    retryable: false,
    hint: '포인트를 충전하거나 주문 금액을 낮춰 주세요.',
    recoverLabel: null,
  },
  ALREADY_CANCELED: {
    title: '이미 취소된 주문',
    retryable: false,
    hint: '이 주문은 이미 취소되어 환급이 끝났습니다. 다시 취소할 수 없습니다.',
    recoverLabel: null,
  },

  // --- 404
  PRODUCT_NOT_FOUND: {
    title: '상품을 찾을 수 없음',
    retryable: false,
    hint: '목록을 새로고침한 뒤 다시 선택해 주세요.',
    recoverLabel: null,
  },
  CUSTOMER_NOT_FOUND: {
    title: '고객을 찾을 수 없음',
    retryable: false,
    hint: '목록을 새로고침한 뒤 다시 선택해 주세요.',
    recoverLabel: null,
  },
  ORDER_NOT_FOUND: {
    title: '주문을 찾을 수 없음',
    retryable: false,
    hint: '목록을 새로고침한 뒤 다시 선택해 주세요.',
    recoverLabel: null,
  },
  ENDPOINT_NOT_FOUND: {
    title: '없는 경로',
    retryable: false,
    hint: '프론트가 호출한 경로가 백엔드에 없습니다. 개발자에게 알려 주세요.',
    recoverLabel: null,
  },
  METHOD_NOT_ALLOWED: {
    title: '허용되지 않은 요청 방식',
    retryable: false,
    hint: '프론트와 백엔드의 HTTP 메서드가 어긋났습니다. 개발자에게 알려 주세요.',
    recoverLabel: null,
  },

  // --- 409 충돌
  DUPLICATE_PRODUCT_NAME: {
    title: '상품명 중복',
    retryable: false,
    hint: '다른 이름을 사용해 주세요.',
    recoverLabel: null,
  },
  DUPLICATE_EMAIL: {
    title: '이메일 중복',
    retryable: false,
    hint: '다른 이메일을 사용해 주세요.',
    recoverLabel: null,
  },
  PRODUCT_IN_USE: {
    title: '삭제할 수 없는 상품',
    retryable: false,
    hint: '주문에 사용된 상품은 이력 보존을 위해 삭제할 수 없습니다.',
    recoverLabel: null,
  },
  CUSTOMER_HAS_ORDERS: {
    title: '삭제할 수 없는 고객',
    retryable: false,
    hint: '주문 이력이 있는 고객은 삭제할 수 없습니다.',
    recoverLabel: null,
  },
  CONCURRENT_UPDATE: {
    title: '요청이 몰리고 있습니다',
    retryable: true, // 재고도 포인트도 충분했다. 다시 보내면 성공할 가능성이 높다
    hint: '',
    recoverLabel: null,
  },

  // --- 500 및 클라이언트 측
  INTERNAL_ERROR: {
    title: '서버 오류',
    retryable: true, // 일시적일 수 있다
    hint: '',
    recoverLabel: null,
  },
  NETWORK_ERROR: {
    title: '서버에 연결할 수 없습니다',
    retryable: true,
    hint: '',
    recoverLabel: null,
  },
  MALFORMED_RESPONSE: {
    title: '계약과 다른 응답',
    retryable: false,
    hint: '백엔드 응답이 API 계약과 다릅니다. 프론트에서 덮지 않고 그대로 표시합니다.',
    recoverLabel: null,
  },
  UNEXPECTED_ERROR: {
    title: '예기치 못한 오류',
    retryable: true,
    hint: '',
    recoverLabel: null,
  },

  // --- 구매 흐름 (계약 §9.5) ------------------------------------------------

  CART_EMPTY: {
    title: '장바구니가 비어 있습니다',
    retryable: false,
    hint: '상품을 담은 뒤 다시 주문해 주세요.',
    recoverLabel: null,
  },
  CART_ITEM_NOT_FOUND: {
    title: '장바구니에 없는 상품',
    retryable: false,
    // 다른 탭에서 이미 지웠거나 관리자가 상품을 삭제해 라인이 함께 사라진 경우다(BR-30).
    // 어느 쪽이든 화면이 낡은 것이므로 다시 읽는 것이 답이다.
    hint: '다른 곳에서 이미 변경된 것 같습니다. 장바구니를 다시 불러와 주세요.',
    recoverLabel: '장바구니 새로고침',
  },

  /*
   * CART_STALE — 이 프로젝트에서 `retryable: false` 가 가장 중요한 자리다.
   *
   * CONCURRENT_UPDATE 와 **둘 다 409** 라 상태코드로는 구분되지 않는다. `code` 로 분기해야 하며,
   * 두 코드의 올바른 행동이 정반대다:
   *
   * | | CONCURRENT_UPDATE | CART_STALE |
   * |---|---|---|
   * | 무엇이 바뀌었나 | DB 행의 version | **사용자가 본 총액** |
   * | 같은 요청 재전송 | 성공할 가능성이 높다 | **반드시 다시 실패한다** |
   * | 버튼 | 다시 시도 | **장바구니 새로고침** |
   *
   * 여기에 "다시 시도" 버튼을 달면 같은 `expectedTotalPrice` 를 다시 보내게 되고,
   * 총액은 이미 달라졌으므로 **무한히 실패한다.** 사용자는 버튼을 계속 누르며 왜 안 되는지
   * 알 수 없다. 반드시 카트를 다시 읽어야 하고, 그 재조회 결과가 곧 화면의 "현재 금액"이다
   * (§9.5.4 권고안 2 — 값을 얻는 데 추가 비용이 0이다).
   *
   * hint 에 금액을 적지 않는 이유: 여기는 코드마다 고정된 문구라 실제 수치를 알 수 없다.
   * 두 금액은 `CheckoutPage` 가 자기가 보낸 값과 재조회 값으로 만든다.
   */
  CART_STALE: {
    title: '장바구니 금액이 변경되었습니다',
    retryable: false,
    hint: '가격이 바뀌어 결제가 중단됐습니다. 아래 금액을 확인한 뒤 다시 진행해 주세요.',
    recoverLabel: '장바구니 새로고침',
  },

  /*
   * FORBIDDEN — 401 과 뭉뚱그리면 안 되는 자리다(§9.4.3).
   *
   * 토큰은 **유효하다.** 역할이 맞지 않을 뿐이라 다시 로그인해도 풀리지 않는다.
   * 그래서 hint 가 "로그인해 주세요"가 아니라 "다른 계정이 필요하다"여야 하고,
   * `client.ts` 도 이 코드에서는 토큰을 지우지 않는다.
   */
  FORBIDDEN: {
    title: '권한이 없습니다',
    retryable: false,
    hint: '로그인은 되어 있지만 이 계정의 역할로는 할 수 없는 작업입니다. 다시 로그인해도 같은 결과이며, 권한이 있는 계정으로 바꿔야 합니다.',
    recoverLabel: null,
  },

  SHOPPER_PROFILE_CONFLICT: {
    title: '구매자 정보를 만들 수 없습니다',
    retryable: false,
    hint: '같은 이메일을 쓰는 고객이 이미 있습니다. 관리자에게 문의해 주세요.',
    recoverLabel: null,
  },
};
