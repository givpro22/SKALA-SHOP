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
  // --- 400 요청이 잘못됨: 입력을 고쳐야 한다
  VALIDATION_ERROR: {
    title: '입력값을 확인해 주세요',
    retryable: false,
    hint: '아래 항목을 고친 뒤 다시 제출해 주세요.',
  },
  MALFORMED_REQUEST: {
    title: '요청 형식 오류',
    retryable: false,
    hint: '입력값을 다시 확인해 주세요.',
  },
  TYPE_MISMATCH: {
    title: '값 형식 오류',
    retryable: false,
    hint: 'id 는 숫자여야 합니다.',
  },
  DUPLICATE_ORDER_ITEM: {
    title: '중복된 주문 항목',
    retryable: false,
    hint: '같은 상품이 두 번 담겼습니다. 한 줄로 합쳐 수량을 올려 주세요.',
  },

  // --- 400 비즈니스 규칙 위반: 재시도해도 결과가 같다
  OUT_OF_STOCK: {
    title: '재고 부족',
    retryable: false, // 재고가 늘기 전까지 몇 번을 눌러도 같은 결과다
    hint: '재고가 채워질 때까지는 다시 시도해도 같은 결과입니다. 수량을 줄여 주세요.',
  },
  INSUFFICIENT_POINT: {
    title: '포인트 부족',
    retryable: false,
    hint: '포인트를 충전하거나 주문 금액을 낮춰 주세요.',
  },
  ALREADY_CANCELED: {
    title: '이미 취소된 주문',
    retryable: false,
    hint: '이 주문은 이미 취소되어 환급이 끝났습니다. 다시 취소할 수 없습니다.',
  },

  // --- 404
  PRODUCT_NOT_FOUND: {
    title: '상품을 찾을 수 없음',
    retryable: false,
    hint: '목록을 새로고침한 뒤 다시 선택해 주세요.',
  },
  CUSTOMER_NOT_FOUND: {
    title: '고객을 찾을 수 없음',
    retryable: false,
    hint: '목록을 새로고침한 뒤 다시 선택해 주세요.',
  },
  ORDER_NOT_FOUND: {
    title: '주문을 찾을 수 없음',
    retryable: false,
    hint: '목록을 새로고침한 뒤 다시 선택해 주세요.',
  },
  ENDPOINT_NOT_FOUND: {
    title: '없는 경로',
    retryable: false,
    hint: '프론트가 호출한 경로가 백엔드에 없습니다. 개발자에게 알려 주세요.',
  },
  METHOD_NOT_ALLOWED: {
    title: '허용되지 않은 요청 방식',
    retryable: false,
    hint: '프론트와 백엔드의 HTTP 메서드가 어긋났습니다. 개발자에게 알려 주세요.',
  },

  // --- 409 충돌
  DUPLICATE_PRODUCT_NAME: {
    title: '상품명 중복',
    retryable: false,
    hint: '다른 이름을 사용해 주세요.',
  },
  DUPLICATE_EMAIL: {
    title: '이메일 중복',
    retryable: false,
    hint: '다른 이메일을 사용해 주세요.',
  },
  PRODUCT_IN_USE: {
    title: '삭제할 수 없는 상품',
    retryable: false,
    hint: '주문에 사용된 상품은 이력 보존을 위해 삭제할 수 없습니다.',
  },
  CUSTOMER_HAS_ORDERS: {
    title: '삭제할 수 없는 고객',
    retryable: false,
    hint: '주문 이력이 있는 고객은 삭제할 수 없습니다.',
  },
  CONCURRENT_UPDATE: {
    title: '요청이 몰리고 있습니다',
    retryable: true, // 재고도 포인트도 충분했다. 다시 보내면 성공할 가능성이 높다
    hint: '',
  },

  // --- 500 및 클라이언트 측
  INTERNAL_ERROR: {
    title: '서버 오류',
    retryable: true, // 일시적일 수 있다
    hint: '',
  },
  NETWORK_ERROR: {
    title: '서버에 연결할 수 없습니다',
    retryable: true,
    hint: '',
  },
  MALFORMED_RESPONSE: {
    title: '계약과 다른 응답',
    retryable: false,
    hint: '백엔드 응답이 API 계약과 다릅니다. 프론트에서 덮지 않고 그대로 표시합니다.',
  },
  UNEXPECTED_ERROR: {
    title: '예기치 못한 오류',
    retryable: true,
    hint: '',
  },
};
