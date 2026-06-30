# AI Finance Dashboard

실시간 환율·주식 데이터를 시각화하고, 기술적 지표 기반 매매 시그널을 AI가 해설해주는 금융 대시보드입니다.

> **참고**: 초기 기획은 "주식 자동매수" 프로젝트였으나, 실거래 자동 실행은 책임 소재·인가받지 않은 투자자문 등의 문제로 범위에서 제외하고 **시그널 알림 + 포트폴리오 트래커**로 방향을 잡았습니다. 매수/매도 시그널은 정보 제공 목적이며, 실제 주문은 사용자가 직접 증권사를 통해 수행합니다.

---

## 핵심 기능

| 영역 | 설명 |
|------|------|
| **환율 대시보드** | 한국수출입은행 API 연동, USD/JPY/EUR/CNY 실시간 환율 + 30일 추이 차트. DB 캐싱으로 API 호출 최소화(휴일은 캐시로 스킵) |
| **주식 조회** | Alpha Vantage 연동(무료 플랜 25건/일 한도 대응 Mock 모드 지원), 종목 검색·실시간 시세·히스토리 차트 |
| **포트폴리오 트래커** | 즐겨찾기 종목에 보유 수량/평단가를 입력하면 현재가 대비 손익(P/L)을 자동 계산 |
| **매매 시그널** | 5일/20일 이동평균선 골든크로스·데드크로스를 평일 매일 자동 탐지. 사용자들이 즐겨찾기한 종목을 기준으로 추적 대상이 동적으로 결정됨 |
| **AI 분석** | GPT가 환율/포트폴리오 실시간 데이터를 context로 받아 자연어 질의응답. 시그널 발생 시에도 GPT가 그 의미를 해설(키 미설정 시 규칙 기반 설명으로 자동 폴백) |
| **이메일 알림** | 관심 종목에 매매 시그널이 발생하면 구독 중인 사용자에게 메일 발송 |
| **인증** | JWT 기반 회원가입/로그인/리프레시 토큰 |

---

## 기술 스택

### Backend
- **Spring Boot 3.5** (Java 17)
- **Spring Security + JWT** (jjwt) — Stateless 인증
- **Spring Data JPA / Hibernate** — MySQL 8
- **Spring Mail** — 시그널 이메일 알림
- **RestClient** — 외부 API 연동 (한국수출입은행, Alpha Vantage, OpenAI)
- **Spring Scheduling** — 매매 시그널 일일 자동 생성

### Frontend
- **React 19 + Vite**
- **TailwindCSS v4**
- **Recharts** — 환율/주식 차트
- **Zustand** — 인증 토큰 상태 관리
- **Axios** — JWT 자동 주입 + 401 시 토큰 자동 갱신 인터셉터
- **React Router**

### Infra
- **Docker / Docker Compose** — MySQL + Backend + Frontend(Nginx) 전체 스택을 컨테이너로 기동
- **Nginx** — 정적 파일 서빙 + `/api` 리버스 프록시

### 외부 API
- [한국수출입은행 환율 API](https://oapi.koreaexim.go.kr) (무료, 1,000건/일)
- [Alpha Vantage](https://www.alphavantage.co) 주식 API (무료, 25건/일 — 개발 중엔 Mock 데이터 사용)
- [OpenAI](https://platform.openai.com) GPT (gpt-4o-mini)

---

## 시스템 아키텍처

```
[React (Vite)] ── Axios ──▶ [Nginx] ── /api/* ──▶ [Spring Boot] ──▶ [MySQL]
                                │                        │
                                │                        ├──▶ 한국수출입은행 API
                                │                        ├──▶ Alpha Vantage API (or Mock)
                                │                        ├──▶ OpenAI API
                                └── 정적 파일 서빙              └──▶ SMTP (시그널 알림 메일)
```

---

## 디렉토리 구조

```
.
├── src/main/java/com/finance/dashboard/
│   ├── config/        # Security, JWT, Exim, Alpha Vantage, OpenAI, Signal, Web(CORS) 설정
│   ├── controller/     # Auth, ExchangeRate, Stock, Signal, Ai
│   ├── service/        # AuthService, ExchangeRateService, StockService, SignalService, AiAnalysisService, OpenAiService, EmailService
│   ├── repository/     # Spring Data JPA 리포지토리
│   ├── entity/          # User, ExchangeRateCache, FavoriteStock, StockSignal, ChatHistory
│   ├── dto/              # request / response
│   ├── security/        # JwtTokenProvider, JwtAuthenticationFilter, UserPrincipal
│   └── exception/        # ErrorCode, CustomException, GlobalExceptionHandler
├── src/main/resources/
│   ├── application.yml          # 공통 설정 (env var 참조)
│   ├── application-local.yml    # 로컬 MySQL
│   └── application-prod.yml     # 운영(RDS) 설정
├── frontend/
│   └── src/
│       ├── api/          # axios 인스턴스 + 도메인별 API 모듈
│       ├── components/   # common / exchange / stock / signal / ai
│       ├── pages/         # HomePage, StockPage, SignalsPage, AiPage, LoginPage
│       ├── hooks/, store/
│       └── App.jsx
├── Dockerfile               # Backend (gradle 멀티스테이지 빌드)
├── frontend/Dockerfile      # Frontend (npm build → nginx)
├── frontend/nginx.conf      # 정적 서빙 + /api 리버스 프록시
└── docker-compose.yml       # mysql + backend + frontend
```

---

## 실행 방법

### 1. Docker Compose로 전체 스택 실행 (권장)

```bash
cp .env.example .env   # 값 채우기
docker compose up --build
```

- Frontend: http://localhost
- Backend: 컨테이너 내부 네트워크 (nginx가 `/api`로 프록시)

### 2. 로컬 개발 (백엔드/프론트 따로)

```bash
# MySQL에 finance_db 데이터베이스 생성 후

# Backend
DB_PASSWORD=xxxx EXIM_API_KEY=xxxx ./gradlew bootRun

# Frontend
cd frontend
cp .env.example .env.local
npm install
npm run dev
```

---

## 환경 변수

`.env.example` 참고. 핵심 변수:

| 변수 | 설명 |
|------|------|
| `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USERNAME`, `DB_PASSWORD` | MySQL 접속 정보 |
| `JWT_SECRET` | JWT 서명 키 (운영 환경에서는 반드시 교체) |
| `EXIM_API_KEY` | 한국수출입은행 환율 API 키 |
| `ALPHA_VANTAGE_API_KEY`, `STOCK_MOCK_MODE` | 주식 API 키 / Mock 모드 토글(기본 `true`) |
| `OPENAI_API_KEY` | 미설정 시 AI 분석은 503, 시그널 해설은 규칙 기반 설명으로 자동 폴백 |
| `MAIL_USERNAME`, `MAIL_PASSWORD`, `SIGNAL_MAIL_ENABLED` | 시그널 이메일 알림 (Gmail 앱 비밀번호) |
| `SIGNAL_CRON` | 시그널 자동 생성 스케줄 (기본: 평일 09:30 KST) |

---

## API 개요

| Method | URL | 설명 | 인증 |
|--------|-----|------|------|
| POST | `/api/auth/signup` | 회원가입 | X |
| POST | `/api/auth/login` | 로그인 | X |
| POST | `/api/auth/refresh` | 토큰 갱신 | X |
| GET | `/api/exchange/today` | 오늘 환율 | X |
| GET | `/api/exchange/history` | 환율 히스토리 | X |
| GET | `/api/stock/search` | 종목 검색 | X |
| GET | `/api/stock/{symbol}` | 종목 시세 | X |
| GET | `/api/stock/{symbol}/history` | 시세 히스토리 | X |
| GET/POST | `/api/stock/favorites` | 포트폴리오 조회/추가 | O |
| PUT | `/api/stock/favorites/{symbol}/holding` | 보유 수량/평단가 수정 | O |
| DELETE | `/api/stock/favorites/{symbol}` | 즐겨찾기 삭제 | O |
| GET | `/api/signals` | 전체 시그널 | X |
| GET | `/api/signals/my` | 내 포트폴리오 시그널 | O |
| POST | `/api/signals/generate` | 시그널 수동 생성 | O |
| POST | `/api/ai/analyze` | AI 질의응답 | O |
| GET/DELETE | `/api/ai/history` | 채팅 히스토리 조회/삭제 | O |

---

## 향후 계획

- [ ] AWS EC2 + RDS 배포
- [ ] Jenkins + GitHub Webhook CI/CD
- [ ] Nginx HTTPS (Let's Encrypt)
- [ ] 단위/통합 테스트 추가
