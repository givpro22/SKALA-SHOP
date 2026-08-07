import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
// 서체는 CDN 이 아니라 번들에 넣는다. 외부 호스트에 의존하면 Docker 이미지가
// 네트워크 없이 뜰 때 화면이 폴백 서체로 무너진다.
// Inter 는 가변축(wght)만 가져온다 — 웨이트 320/330/340/480/540/700 이 필요하다.
import '@fontsource-variable/inter/wght.css'
import '@fontsource/jetbrains-mono/400.css'
// 한글 가변축. Inter 에는 한글이 없어 시스템 서체로 폴백했는데, 폴백은 이산 웨이트만
// 가지므로 320·330·340 이 같은 굵기로 뭉개져 화면 대부분(한글)에서 위계가 사라졌다.
// Pretendard 는 45~920 가변축이라 같은 값이 그대로 나온다.
//
// 동적 서브셋을 쓴다. 단일 파일은 2MB 라 첫 화면이 그만큼 늦어지는데, 서브셋은
// unicode-range 로 나뉘어 있어 **실제로 쓰인 글자가 든 조각만** 내려받는다.
import 'pretendard/dist/web/variable/pretendardvariable-dynamic-subset.css'
import './index.css'
import App from './App.tsx'

const container = document.getElementById('root')
if (container === null) throw new Error('#root 엘리먼트를 찾을 수 없습니다.')

createRoot(container).render(
  <StrictMode>
    <App />
  </StrictMode>,
)
