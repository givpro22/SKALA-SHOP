import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
// 서체는 CDN 이 아니라 번들에 넣는다. 외부 호스트에 의존하면 Docker 이미지가
// 네트워크 없이 뜰 때 화면이 폴백 서체로 무너진다.
// Inter 는 가변축(wght)만 가져온다 — 웨이트 320/330/340/480/540/700 이 필요하다.
import '@fontsource-variable/inter/wght.css'
import '@fontsource/jetbrains-mono/400.css'
import './index.css'
import App from './App.tsx'

const container = document.getElementById('root')
if (container === null) throw new Error('#root 엘리먼트를 찾을 수 없습니다.')

createRoot(container).render(
  <StrictMode>
    <App />
  </StrictMode>,
)
