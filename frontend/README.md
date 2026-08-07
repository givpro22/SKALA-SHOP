# SKALA-SHOP 프론트엔드

React + Vite + TypeScript. 백엔드 REST API(`/api`)를 소비하는 상품·고객·주문 화면.

## 실행

```bash
# 1. 백엔드를 local 프로파일로 띄운다 (시드 데이터 포함)
cd ../backend && ./gradlew bootRun --args='--spring.profiles.active=local --server.port=18080'

# 2. 환경변수 설정 — 백엔드를 띄운 포트에 맞춘다
cp .env.example .env.local

# 3. 프론트 실행
npm install
npm run dev        # → http://localhost:3000
```

`.env.local` 은 추적되지 않으므로 각자 환경에 맞게 고쳐도 저장소가 더러워지지 않는다.

### 포트가 3000 인 이유

백엔드 CORS 허용 오리진이 `http://localhost:5173` 과 `http://localhost:3000` 둘뿐이다
(`backend/src/main/resources/application.yml`). `vite.config.ts` 에 `strictPort: true` 를
걸어 두었으므로, 3000 이 점유돼 있으면 다른 포트로 밀려나는 대신 기동이 실패한다.

밀려난 포트는 CORS 허용 목록에 없어서 **모든 API 요청이 막히는데 화면은 정상적으로 뜬다.**
원인을 찾기 어려운 실패라, 조용한 이동보다 즉시 실패가 낫다. 5173 을 쓰려면
`vite.config.ts` 의 `port` 를 바꾼다.

## 스크립트

| 명령 | 내용 |
|---|---|
| `npm run dev` | 개발 서버 |
| `npm run build` | `tsc -b` 타입 검사 + 프로덕션 번들 |
| `npm run lint` | oxlint |
| `npm run preview` | 빌드 결과 미리보기 |

## 구조

```
src/
├── types/api.ts      API 계약에서 그대로 옮긴 타입 (원본)
├── api/              fetch 래퍼 + 도메인별 함수
├── hooks/            도메인별 커스텀 훅
├── lib/              에러 코드 → 화면 처리, 표시 포맷
├── components/       ErrorBanner, AsyncBoundary
└── pages/            상품 / 고객 / 주문
```

**타입 → API 클라이언트 → 훅 → 페이지** 순으로 쌓았다. 페이지부터 만들면 타입이 화면
편의에 맞춰 변형되어 API 계약에서 이탈한다.

타입의 원본은 `_workspace/01_architect_api-contract.md` §6 이며 손으로 이름을 바꾸지 않는다.
필드명이 하나만 달라도 TypeScript 는 잡아주지 못하고 화면에 `undefined` 가 뜬다.

## 배포

`Dockerfile` 은 멀티스테이지(node 빌드 → nginx 서빙)다. `VITE_*` 변수는 **빌드 시점에
번들로 인라인되므로** 런타임 환경변수로 바꿀 수 없다. API 주소는 build arg 로 넣는다:

```bash
docker build --build-arg VITE_API_BASE_URL=http://localhost:8080 -t skala-shop-frontend .
```

`docker-compose.yml` 이 이 값을 넘긴다.
