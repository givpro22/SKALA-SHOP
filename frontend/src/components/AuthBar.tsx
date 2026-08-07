import { useState, type FormEvent } from 'react';
import { ErrorBanner } from './ErrorBanner';
import type { AuthState } from '../hooks/useAuth';

type Mode = 'login' | 'signup';

/**
 * 헤더의 로그인·계정 등록 영역.
 *
 * <p>별도 로그인 **페이지**를 만들지 않았다. 이 앱은 조회가 공개이고 쓰기만 인증을 요구하므로
 * (계약 §8.5), 로그인 페이지로 먼저 보내면 로그인 없이 볼 수 있는 화면까지 가려진다.
 * 헤더에 두면 지금 보고 있는 화면을 잃지 않고 로그인할 수 있다.
 *
 * <p>등록도 같은 자리에서 모드만 바꾼다. 입력 항목이 로그인과 동일(아이디·비밀번호)이라
 * 화면을 따로 만들면 같은 폼이 두 벌 생기고, 그중 하나만 고치는 일이 반드시 생긴다.
 *
 * <p><b>폼은 헤더 흐름 안이 아니라 아래로 펼쳐지는 패널이다.</b> 인라인으로 두었더니
 * 입력 2개 + 버튼 3개가 헤더 폭을 넘겨 두 줄이 되었고, 헤더가 높아진 만큼 페이지 전체가
 * 아래로 밀렸다. 로그인 하나를 열었다고 보던 화면이 움직이면 안 된다.
 */
export function AuthBar({ auth }: { auth: AuthState }) {
  const [mode, setMode] = useState<Mode | null>(null);
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');

  function close() {
    setMode(null);
    setPassword('');
    auth.reset();
  }

  function switchMode(next: Mode) {
    setMode(next);
    // 아이디는 남기고 비밀번호만 지운다. 로그인에 실패해 등록으로 넘어가는 흐름에서
    // 방금 친 아이디를 다시 치게 하면 오타가 늘고, 그 오타가 새 계정으로 만들어진다.
    setPassword('');
    auth.reset();
  }

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const ok = mode === 'signup'
      ? await auth.signup(username, password)
      : await auth.login(username, password);
    if (ok) close();
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
      <button
        type="button"
        className="btn btn--sm"
        aria-expanded={mode !== null}
        onClick={() => (mode === null ? switchMode('login') : close())}
      >
        {mode === null ? '로그인' : '닫기'}
      </button>

      {mode !== null && (
        <div className="authbar__panel" role="dialog" aria-label="로그인">
          <p className="authbar__title">{mode === 'signup' ? '계정 등록' : '로그인'}</p>

          <form className="authbar__form" onSubmit={(event) => void handleSubmit(event)}>
            <label>
              아이디
              <input
                type="email"
                name="username"
                required
                maxLength={100}
                placeholder="admin@skala.shop"
                autoComplete="username"
                value={username}
                onChange={(event) => setUsername(event.target.value)}
              />
            </label>

            <label>
              비밀번호
              <input
                type="password"
                name="password"
                required
                // 등록에서만 길이를 건다. 로그인에 걸면 규칙이 바뀌기 전에 만든 계정이
                // 브라우저 단에서 막혀 서버에 닿지도 못한다.
                minLength={mode === 'signup' ? 8 : undefined}
                maxLength={72}
                placeholder={mode === 'signup' ? '8자 이상' : ''}
                autoComplete={mode === 'signup' ? 'new-password' : 'current-password'}
                value={password}
                onChange={(event) => setPassword(event.target.value)}
              />
            </label>

            <div className="authbar__actions">
              <button type="submit" className="btn btn--sm btn--primary" disabled={auth.pending}>
                {auth.pending ? '확인 중…' : mode === 'signup' ? '등록' : '로그인'}
              </button>
              <button
                type="button"
                className="btn btn--sm"
                onClick={() => switchMode(mode === 'signup' ? 'login' : 'signup')}
              >
                {mode === 'signup' ? '로그인하기' : '계정 등록'}
              </button>
            </div>
          </form>

          {/* 에러는 패널 안에 둔다. 밖에 두면 패널과 배너가 서로 겹친다 */}
          {auth.error !== null && (
            <ErrorBanner error={auth.error} onDismiss={auth.reset} />
          )}
        </div>
      )}
    </div>
  );
}
