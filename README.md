# FinanceAI

Spring Boot 3.5 + React 기반 주식/금융 대시보드 프로젝트.  
AWS SAA(Solutions Architect Associate) 자격증 학습을 목적으로 실제 인프라 구성 및 배포까지 진행한 풀스택 사이드 프로젝트.

> 매수/매도 시그널은 정보 제공 목적이며, 실제 주문은 사용자가 직접 증권사를 통해 수행합니다.

---

## 라이브 데모

**URL:** https://d17mchdxg3nzdu.cloudfront.net

---

## 기술 스택

| 구분 | 기술 |
|---|---|
| Backend | Java 21, Spring Boot 3.5, Spring Security, JPA, JWT, WebSocket |
| Frontend | React 18, Vite, Tailwind CSS, Recharts, React Router |
| Database | MySQL 8 (AWS RDS) |
| Infra | AWS VPC · EC2 · RDS · S3 · CloudFront · ALB |
| CI/CD | GitHub Actions (Gradle 빌드 → SCP → SSH 재시작) |
| 부하테스트 | k6 |

---

## AWS 아키텍처

```
사용자
  ↓ HTTPS
CloudFront (d17mchdxg3nzdu.cloudfront.net)
  ├── /api/*  → ALB → EC2 t2.micro (Spring Boot :8080) → RDS MySQL
  └── /*      → S3 (React 정적 파일)
```

### 구성 요소

| 서비스 | 이름 | 비고 |
|---|---|---|
| VPC | finance-ai-vpc | 서울 리전 (ap-northeast-2) |
| EC2 | finance-ai-server | t2.micro, systemd 등록 |
| RDS | finance-ai-db | db.t3.micro, MySQL 8, 단일 AZ |
| S3 | finance-ai-frontend | 정적 웹 호스팅 |
| CloudFront | d17mchdxg3nzdu.cloudfront.net | S3 + ALB 멀티 오리진 |
| ALB | finance-ai-alb | HTTP:80, 대상그룹 :8080 |

### 보안 그룹

```
finance-alb-sg  → 인바운드: HTTP 80, HTTPS 443 (0.0.0.0/0)
finance-ec2-sg  → 인바운드: SSH 22, TCP 8080 (finance-alb-sg 소스)
RDS SG          → 인바운드: MySQL 3306 (finance-ec2-sg 소스)
```

---

## 구현 기능

### 주식
- KRX 전체 종목 데이터 로딩 및 시가총액 TOP 조회
- 종목 검색, 개별 종목 상세 / 히스토리 차트 / 뉴스
- 코스피/코스닥 시장 지수 위젯
- 최근 본 종목 (localStorage, 최대 10개)

### 관심종목 / 포트폴리오
- 관심종목 추가/삭제
- 보유 수량·평균단가 입력 → 평가손익·수익률 자동 계산
- 포트폴리오 파이차트 (비중 시각화)
- 섹터 분석 (업종별 종목 수·비중 바 차트)
- 정렬 기능 (날짜순 / 이름순 / 수익률순)
- 투자 메모 (종목별 메모 작성/수정, 최대 500자)
- 네이버 주식 바로가기 링크

### 알림
- 목표가 알림 설정 (이상/이하 도달 시)
- 이메일 알림 (선택적 활성화)

### AI / 시그널
- AI 종목 분석 (OpenAI 연동)
- 매수/매도 시그널 생성 (이동평균 골든크로스/데드크로스)
- AI 채팅

### 기타
- 환율 조회 (한국수출입은행 API, DB 캐싱)
- 귀금속 가격 (금/은 시세)
- 자산 상관관계 분석
- JWT 인증 (Access Token + Refresh Token)
- WebSocket 실시간 가격 업데이트

---

## CI/CD 흐름

```
git push origin main
  → GitHub Actions 트리거
  → Gradle clean build (테스트 제외)
  → SCP로 jar 파일 EC2 전송
  → SSH로 systemd 재시작
```

GitHub Secrets: `EC2_HOST` · `EC2_USER` · `EC2_KEY` · `EC2_PORT`

---

## k6 부하테스트 결과

| 시나리오 | 동시 유저 |
|---|---|
| warmup | 5명 |
| normal_load | 20명 (전체 유저 플로우) |
| stress_test | 50→200명 점진적 증가 |
| spike_test | 0→200명 급증/급감 |

| 지표 | 결과 |
|---|---|
| 평균 응답시간 | 15ms |
| p(95) 응답시간 | 23ms |
| 주식 조회 p(90) | 17ms |
| 최대 동시 유저 | 200명 (안정적) |

---

## 로컬 실행

### 백엔드
```bash
./gradlew bootRun
```

필요 환경변수:
```
DB_HOST, DB_PORT, DB_NAME, DB_USERNAME, DB_PASSWORD
JWT_SECRET
EXIM_API_KEY
SPRING_PROFILES_ACTIVE=dev
```

### 프론트엔드
```bash
cd frontend
npm install
npm run dev
```

---

## 환경변수

| 변수 | 설명 |
|---|---|
| `DB_HOST` ~ `DB_PASSWORD` | MySQL 접속 정보 |
| `JWT_SECRET` | JWT 서명 키 (운영 시 반드시 교체) |
| `EXIM_API_KEY` | 한국수출입은행 환율 API 키 |
| `OPENAI_API_KEY` | 미설정 시 AI 기능 비활성화 |
| `MAIL_USERNAME`, `MAIL_PASSWORD` | 이메일 알림용 Gmail 앱 비밀번호 |

---

## API 주요 엔드포인트

| Method | URL | 설명 | 인증 |
|---|---|---|---|
| POST | `/api/auth/signup` | 회원가입 | X |
| POST | `/api/auth/login` | 로그인 | X |
| GET | `/api/stock/market/top` | 시가총액 TOP | X |
| GET | `/api/stock/search` | 종목 검색 | X |
| GET/POST | `/api/stock/favorites` | 관심종목 조회/추가 | O |
| PUT | `/api/stock/favorites/{symbol}/holding` | 보유정보 수정 | O |
| PATCH | `/api/stock/favorites/{symbol}/memo` | 투자 메모 수정 | O |
| GET | `/api/stock/portfolio/summary` | 포트폴리오 요약 | O |
| GET | `/api/stock/portfolio/sector-breakdown` | 섹터 분석 | O |
| GET | `/api/exchange/rates` | 환율 조회 | X |
| GET | `/api/signals` | 매매 시그널 | X |
| POST | `/api/ai/analyze` | AI 분석 | O |

---

## 향후 개선 계획

### AWS 인프라
- [ ] Multi-AZ RDS — 장애 자동 복구
- [ ] Read Replica — 읽기 쿼리 분산
- [ ] Auto Scaling Group — 트래픽 기반 자동 스케일 아웃
- [ ] AWS Secrets Manager — 환경변수 보안 강화
- [ ] CloudWatch 알람 — CPU·메모리·RDS 모니터링
- [ ] ALB HTTP → HTTPS 리다이렉트
- [ ] ACM 인증서 + 커스텀 도메인

### 기능
- [ ] 포트폴리오 손익 히스토리 — 날짜별 총 평가금액 추이
- [ ] 알림 히스토리 — 목표가 달성 기록
- [ ] 종목 비교 — 두 종목 수익률 나란히 비교
- [ ] 뉴스 실제 연동 — 현재 Mock → 네이버 뉴스 API

### 보안
- [ ] JWT_SECRET 강화 — 랜덤 256bit 이상으로 교체
- [ ] EC2 SSH IP 제한 — GitHub Actions IP 범위로 축소
- [ ] RDS 비밀번호 Secrets Manager 이관

---

## 주요 트러블슈팅

| 이슈 | 원인 | 해결 |
|---|---|---|
| ALB Target Unhealthy | EC2 SG가 ALB SG 트래픽 미허용 | finance-ec2-sg에 finance-alb-sg 소스 추가 |
| Mixed Content 에러 | CloudFront(HTTPS)에서 EC2(HTTP) 직접 호출 | CloudFront ALB 오리진 추가 + /api/* 동작 생성 |
| CloudFront 404 | React 라우터 경로를 S3가 모름 | 오류 페이지 403/404 → index.html:200 설정 |
| Spring Boot 시작 실패 | JavaMailSender 빈 미존재 | Optional<JavaMailSender>로 변경 |
| Health Check 500 | /api/exchange/rates 외부 API 의존 | /api/stock/market/top으로 변경 |
