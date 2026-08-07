import type { ApiError } from '../api/client';
import { ERROR_UX } from '../lib/errorUx';

interface Props {
  error: ApiError;
  /** 재시도 가능한 코드일 때만 버튼이 노출된다. */
  onRetry?: () => void;
  onDismiss?: () => void;
}

/**
 * 실패를 화면에 보여주는 단일 지점.
 *
 * - 코드 배지: 서버가 내려보낸 `code` 를 그대로 노출한다. 어떤 규칙이 발동했는지가 화면에
 *   남아야 "포인트 부족으로 거부됐다"는 것이 증거가 된다.
 * - 본문: 서버 `message` 를 그대로 쓴다. 여기에 구체 수치가 들어 있다.
 * - 재시도 버튼: `ERROR_UX[code].retryable` 이 true 일 때만. 계약 §5.1 이 규정한
 *   CONCURRENT_UPDATE / OUT_OF_STOCK 의 분기가 여기서 드러난다.
 */
export function ErrorBanner({ error, onRetry, onDismiss }: Props) {
  const ux = ERROR_UX[error.code];

  return (
    // coral 컬러블록. 서버가 규칙을 발동시킨 순간이 이 화면에서 가장 중요한 장면이다.
    <div className="banner banner--error block block--alert" role="alert" data-error-code={error.code}>
      <div className="banner__head">
        <span className="badge badge--error">{error.code}</span>
        <strong className="banner__title">{ux.title}</strong>
        {error.status > 0 && <span className="banner__status">HTTP {error.status}</span>}
        {onDismiss && (
          <button type="button" className="banner__close" onClick={onDismiss} aria-label="닫기">
            ×
          </button>
        )}
      </div>

      <p className="banner__message">{error.message}</p>

      {error.fieldErrors !== null && error.fieldErrors.length > 0 && (
        <ul className="banner__fields">
          {error.fieldErrors.map((fieldError) => (
            <li key={fieldError.field}>
              <code>{fieldError.field}</code> — {fieldError.reason}
              {fieldError.rejectedValue !== null && ` (입력: ${fieldError.rejectedValue})`}
            </li>
          ))}
        </ul>
      )}

      {ux.retryable ? (
        <div className="banner__actions">
          <p className="banner__hint">일시적인 충돌입니다. 다시 시도하면 성공할 수 있습니다.</p>
          {onRetry && (
            <button type="button" className="btn btn--retry" onClick={onRetry}>
              다시 시도
            </button>
          )}
        </div>
      ) : (
        ux.hint !== '' && <p className="banner__hint">{ux.hint}</p>
      )}
    </div>
  );
}
