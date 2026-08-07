import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react()],
  server: {
    // 백엔드가 허용하는 CORS 오리진은 http://localhost:5173 과 http://localhost:3000 둘뿐이다
    // (backend/src/main/resources/application.yml). 포트가 다른 곳으로 밀리면 모든 요청이
    // CORS로 막히는데, Vite는 기본적으로 조용히 다음 포트를 잡는다.
    // strictPort 로 "조용한 이동"을 실패로 바꿔 원인을 즉시 드러낸다.
    port: 3000,
    strictPort: true,
  },
})
