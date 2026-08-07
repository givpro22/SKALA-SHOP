import { useState, type FormEvent } from 'react';
import { ErrorBanner } from './ErrorBanner';
import type { AuthState } from '../hooks/useAuth';

/**
 * 헤더의 로그인 영역.
 *
 * <p>별도 로그인 **페이지**를 만들지 않았다. 이 앱은 조회가 공개이고 쓰기만 인증을 요구하므로
 * (계약 §8.5), 로그인 페이지로 먼저 보내면 로그인 없이 볼 수 있는 화면까지 가려진다.
 * 헤더에 두면 지금 보고 있는 화면을 잃지 않고 로그인할 수 있다.
 */
export function AuthBar({ auth }: { auth: AuthState }) {
  const [open, setOpen] = useState(false);
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const ok = await auth.login(username, password);
    if (ok) {
      setOpen(false);
      setPassword('');
    }
  }

  if (auth.loggedIn) {
    return (
      <div className="authbar">
        <span className="authbar__who" data-logged-in="true">
          {auth.username}
        </span>
        <button type="button" className="btn btn--sm" onClick={auth.logout}>
          로그아웃
        </button>
      </div>
    );
  }

  return (
    <div className="authbar">
      {!open && (
        <button type="button" className="btn btn--sm" onClick={() => setOpen(true)}>
          로그인
        </button>
      )}

      {open && (
        <form className="authbar__form" onSubmit={(event) => void handleSubmit(event)}>
          <input
            type="email"
            name="username"
            required
            placeholder="admin@skala.shop"
            autoComplete="username"
            value={username}
            onChange={(event) => setUsername(event.target.value)}
          />
          <input
            type="password"
            name="password"
            required
            placeholder="비밀번호"
            autoComplete="current-password"
            value={password}
            onChange={(event) => setPassword(event.target.value)}
          />
          <button type="submit" className="btn btn--sm btn--primary" disabled={auth.pending}>
            {auth.pending ? '확인 중…' : '로그인'}
          </button>
          <button
            type="button"
            className="btn btn--sm"
            onClick={() => {
              setOpen(false);
              auth.reset();
            }}
          >
            닫기
          </button>
        </form>
      )}

      {auth.error !== null && (
        <div className="authbar__error">
          <ErrorBanner error={auth.error} onDismiss={auth.reset} />
        </div>
      )}
    </div>
  );
}
