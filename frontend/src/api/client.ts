import { clearToken, readToken } from '../lib/token';
import type { ErrorCode, ErrorResponse, FieldError } from '../types/api';

/**
 * 공통 fetch 래퍼.
 *
 * 이 파일의 존재 이유는 "제네릭은 검증이 아니다" 하나다.
 * `apiGet<ProductResponse[]>()` 는 컴파일러에게 하는 약속일 뿐 런타임에 아무것도 확인하지
 * 않는다. 서버가 `{content: [...]}` 를 주면 빌드는 통과하고 화면에서 `.map is not a function`
 * 으로 죽는다. 그래서 이 래퍼는 **실제로 틀릴 수 있는 두 지점만 런타임에 검사**한다:
 *
 *   1. 목록 응답이 정말 배열인가 (`apiGetList`)
 *   2. 에러 본문의 `code` 가 계약 §5 표에 있는 코드인가 (`isErrorCode`)
 *
 * 나머지 필드까지 검증하려면 스키마 라이브러리가 필요한데, 그 비용 대비 효용이 낮다 —
 * 위 두 가지가 실제로 화면을 깨뜨리는 형태의 불일치다.
 */

/** 경로 prefix(`/api`) 는 각 호출부가 붙인다. 미설정이면 same-origin. */
const BASE_URL: string = import.meta.env.VITE_API_BASE_URL ?? '';

// 개발 중에 값이 비어 있으면 요청이 Vite dev server 자신에게 가고, dev server 는 SPA
// fallback 으로 index.html 을 돌려준다. 그러면 "JSON 을 읽을 수 없습니다" 라는, 원인과
// 한참 떨어진 에러만 보인다. 설정 누락을 그 자리에서 드러낸다.
// (배포 시에는 Dockerfile 의 build arg 로 주입되므로 이 경고가 뜨지 않는다.)
if (import.meta.env.DEV && BASE_URL === '') {
  console.warn(
    'VITE_API_BASE_URL 이 설정되지 않았습니다. `cp .env.example .env.local` 후 백엔드 주소를 넣으세요.',
  );
}

/**
 * 서버 코드가 아닌, 클라이언트 쪽에서만 발생하는 실패.
 * 계약 §5 표("표에 없는 실패는 존재하지 않는다")를 오염시키지 않도록 `ErrorCode` 와 분리해 둔다.
 */
export type ClientOnlyErrorCode =
  /** fetch 자체가 실패했다 — 서버가 안 떠 있거나 CORS 로 막혔다. 응답이 없다. */
  | 'NETWORK_ERROR'
  /** 응답은 왔지만 계약과 다른 모양이다. 백엔드에 보고해야 하는 상황이다. */
  | 'MALFORMED_RESPONSE'
  /** 프론트 코드 자체의 버그. 서버 탓으로 표시하지 않기 위해 따로 둔다. */
  | 'UNEXPECTED_ERROR';

export type ClientErrorCode = ErrorCode | ClientOnlyErrorCode;

/** 화면이 `code` 로 분기할 수 있도록 코드를 보존해 던지는 에러. */
export class ApiError extends Error {
  readonly code: ClientErrorCode;
  /** HTTP 상태. 응답 자체를 못 받았으면 `0`. */
  readonly status: number;
  readonly fieldErrors: FieldError[] | null;

  // `erasableSyntaxOnly` 가 켜져 있어 생성자 파라미터 프로퍼티를 쓸 수 없다. 명시 대입한다.
  constructor(
    code: ClientErrorCode,
    message: string,
    status: number,
    fieldErrors: FieldError[] | null,
  ) {
    super(message);
    this.name = 'ApiError';
    this.code = code;
    this.status = status;
    this.fieldErrors = fieldErrors;
  }
}

/**
 * 계약 §5 표의 22개 코드.
 *
 * `Record<ErrorCode, true>` 로 선언했기 때문에 계약에 코드가 추가됐는데 여기에 빠지면
 * **컴파일이 실패한다.** 배열 리터럴로 두면 조용히 누락된다.
 */
const KNOWN_ERROR_CODES: Record<ErrorCode, true> = {
  VALIDATION_ERROR: true,
  MALFORMED_REQUEST: true,
  TYPE_MISMATCH: true,
  OUT_OF_STOCK: true,
  INSUFFICIENT_POINT: true,
  ALREADY_CANCELED: true,
  DUPLICATE_ORDER_ITEM: true,
  PRODUCT_NOT_FOUND: true,
  CUSTOMER_NOT_FOUND: true,
  ORDER_NOT_FOUND: true,
  ENDPOINT_NOT_FOUND: true,
  METHOD_NOT_ALLOWED: true,
  DUPLICATE_PRODUCT_NAME: true,
  DUPLICATE_EMAIL: true,
  PRODUCT_IN_USE: true,
  CUSTOMER_HAS_ORDERS: true,
  CONCURRENT_UPDATE: true,
  INTERNAL_ERROR: true,
  INVALID_CREDENTIALS: true,
  UNAUTHORIZED: true,
  TOKEN_EXPIRED: true,
  DUPLICATE_USERNAME: true,
};

function isErrorCode(value: unknown): value is ErrorCode {
  return typeof value === 'string'
    && Object.prototype.hasOwnProperty.call(KNOWN_ERROR_CODES, value);
}

function isFieldError(value: unknown): value is FieldError {
  if (typeof value !== 'object' || value === null) return false;
  if (!('field' in value) || typeof value.field !== 'string') return false;
  if (!('reason' in value) || typeof value.reason !== 'string') return false;
  if (!('rejectedValue' in value)) return false;
  return value.rejectedValue === null || typeof value.rejectedValue === 'string';
}

function isFieldErrorList(value: unknown): value is FieldError[] {
  return Array.isArray(value) && value.every(isFieldError);
}

/** 계약 §4.1 형태인지 실제로 확인한다. `code` 가 표에 없으면 계약 위반이므로 통과시키지 않는다. */
function isErrorResponse(value: unknown): value is ErrorResponse {
  if (typeof value !== 'object' || value === null) return false;
  if (!('code' in value) || !isErrorCode(value.code)) return false;
  if (!('message' in value) || typeof value.message !== 'string') return false;
  if (!('fieldErrors' in value)) return false;
  return value.fieldErrors === null || isFieldErrorList(value.fieldErrors);
}

// ---------------------------------------------------------------- 요청 실행

interface SendOptions {
  method: string;
  /** 있으면 JSON 으로 직렬화하고 Content-Type 을 붙인다. 없으면 둘 다 붙이지 않는다. */
  body?: unknown;
}

/**
 * 토큰이 있으면 붙이고 없으면 붙이지 않는다.
 *
 * 빈 `Authorization` 헤더를 보내지 않는 것이 중요하다. 서버 필터는 헤더가 **있는데**
 * 잘못된 경우를 401 로 즉시 거부하므로(계약 §8.5), 빈 값을 보내면 공개 GET 까지 막힌다.
 */
function authHeader(): Record<string, string> {
  const token = readToken();
  return token === null ? {} : { Authorization: `Bearer ${token}` };
}

async function send(path: string, options: SendOptions): Promise<Response> {
  const hasBody = options.body !== undefined;
  try {
    return await fetch(`${BASE_URL}${path}`, {
      method: options.method,
      // 취소(POST /api/orders/{id}/cancel)는 본문이 없다. Content-Type 도 붙이지 않는다.
      headers: {
        ...(hasBody ? { 'Content-Type': 'application/json;charset=UTF-8' } : {}),
        ...authHeader(),
      },
      body: hasBody ? JSON.stringify(options.body) : undefined,
    });
  } catch {
    throw new ApiError(
      'NETWORK_ERROR',
      `서버에 연결할 수 없습니다. 백엔드가 실행 중인지 확인해 주세요. (${BASE_URL || 'same-origin'}${path})`,
      0,
      null,
    );
  }
}

/** 실패 응답을 계약 §4.1 로 파싱해 코드를 보존한 `ApiError` 로 만든다. */
async function toApiError(res: Response, path: string): Promise<ApiError> {
  const text = await res.text();

  if (text.length === 0) {
    return new ApiError(
      'MALFORMED_RESPONSE',
      `서버가 본문 없이 실패했습니다. (HTTP ${res.status} ${path})`,
      res.status,
      null,
    );
  }

  let parsed: unknown;
  try {
    parsed = JSON.parse(text);
  } catch {
    return new ApiError(
      'MALFORMED_RESPONSE',
      `서버 응답이 JSON 이 아닙니다. (HTTP ${res.status} ${path})`,
      res.status,
      null,
    );
  }

  if (!isErrorResponse(parsed)) {
    // 계약 위반이다. 프론트에서 적당히 덮지 않고 그대로 드러낸다 — 백엔드를 고쳐야 한다.
    return new ApiError(
      'MALFORMED_RESPONSE',
      `실패 응답이 계약 §4.1 형태가 아닙니다. (HTTP ${res.status} ${path}) 원문: ${text.slice(0, 200)}`,
      res.status,
      null,
    );
  }

  /*
   * 토큰이 더 이상 통하지 않으면 그 자리에서 지운다.
   *
   * 지우지 않으면 화면은 "로그인됨"으로 보이는데 모든 쓰기가 401 로 실패한다 — 사용자는
   * 무엇이 잘못됐는지 알 수 없고, 다시 로그인할 방법도 화면에 없다.
   *
   * INVALID_CREDENTIALS 는 제외한다. 그건 로그인 시도 자체가 틀린 것이라 지울 토큰이 없다.
   */
  if (parsed.code === 'TOKEN_EXPIRED' || parsed.code === 'UNAUTHORIZED') {
    clearToken();
  }

  return new ApiError(parsed.code, parsed.message, res.status, parsed.fieldErrors);
}

async function readJson<T>(res: Response, path: string): Promise<T> {
  const text = await res.text();
  if (text.length === 0) {
    throw new ApiError(
      'MALFORMED_RESPONSE',
      `본문이 있어야 하는 응답이 비어 있습니다. (HTTP ${res.status} ${path})`,
      res.status,
      null,
    );
  }
  try {
    return JSON.parse(text);
  } catch {
    throw new ApiError(
      'MALFORMED_RESPONSE',
      `응답 본문을 JSON 으로 읽을 수 없습니다. (HTTP ${res.status} ${path})`,
      res.status,
      null,
    );
  }
}

async function request<T>(path: string, options: SendOptions): Promise<T> {
  const res = await send(path, options);
  if (!res.ok) throw await toApiError(res, path);
  return readJson<T>(res, path);
}

// ---------------------------------------------------------------- 공개 API

export function apiGet<T>(path: string): Promise<T> {
  return request<T>(path, { method: 'GET' });
}

/**
 * 목록 조회 전용.
 *
 * 계약 §0.2 는 모든 목록이 순수 배열이라고 못 박았고 실제 응답도 그렇다. 그래도 여기서
 * 한 번 더 확인하는 이유: 나중에 누가 페이지네이션을 넣어 `{content: [...]}` 로 바꾸면
 * 제네릭은 아무 말도 하지 않고 화면이 `.map is not a function` 으로 죽는다.
 * 그 크래시를 "계약과 다른 응답" 이라는 읽을 수 있는 에러로 바꾼다.
 */
export async function apiGetList<T>(path: string): Promise<T[]> {
  const body = await request<T[]>(path, { method: 'GET' });
  if (!Array.isArray(body)) {
    throw new ApiError(
      'MALFORMED_RESPONSE',
      `목록 응답이 배열이 아닙니다. 계약 §0.2 위반입니다. (${path})`,
      200,
      null,
    );
  }
  return body;
}

export function apiPost<T>(path: string, body?: unknown): Promise<T> {
  return request<T>(path, { method: 'POST', body });
}

export function apiPut<T>(path: string, body: unknown): Promise<T> {
  return request<T>(path, { method: 'PUT', body });
}

/**
 * DELETE 는 204 + 0바이트다(계약 §1.1). `res.json()` 을 호출하면 파싱 예외가 난다.
 * 그래서 본문을 아예 읽지 않는 전용 함수를 둔다.
 */
export async function apiDelete(path: string): Promise<void> {
  const res = await send(path, { method: 'DELETE' });
  if (!res.ok) throw await toApiError(res, path);
  // 204 No Content — 본문을 읽지 않는다.
}

/** catch 블록의 `unknown` 을 화면이 쓸 수 있는 `ApiError` 로 좁힌다. */
export function asApiError(cause: unknown): ApiError {
  if (cause instanceof ApiError) return cause;
  const detail = cause instanceof Error ? cause.message : String(cause);
  return new ApiError('UNEXPECTED_ERROR', `예기치 못한 오류가 발생했습니다. (${detail})`, 0, null);
}
