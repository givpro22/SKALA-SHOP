/// <reference types="vite/client" />

// vite/client 의 ImportMetaEnv 에는 `[key: string]: any` 인덱스 시그니처가 있다.
// 그대로 두면 import.meta.env.X 가 전부 any 로 흘러 경계면 검사가 무력화되므로,
// 이 프로젝트가 실제로 쓰는 키만 명시 타입으로 선언해 덮는다.
// (명시 프로퍼티가 인덱스 시그니처보다 우선한다.)
interface ImportMetaEnv {
  /** 백엔드 API base URL. 미설정이면 same-origin('') 으로 동작한다. */
  readonly VITE_API_BASE_URL?: string
  /** Vite 가 채우는 개발 모드 플래그. */
  readonly DEV: boolean
}

interface ImportMeta {
  readonly env: ImportMetaEnv
}
