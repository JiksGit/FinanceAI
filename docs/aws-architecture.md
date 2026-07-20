# FinanceAI AWS 구성 참고 문서

나중에 다시 볼 때 "왜 이렇게 설정했는지" 이해하기 위한 문서.

---

## 전체 구성도

```
[사용자 브라우저]
      ↓ HTTPS
[CloudFront] d17mchdxg3nzdu.cloudfront.net
      ├── /api/* → [ALB] finance-ai-alb
      │               ↓ HTTP:8080
      │           [EC2] finance-ai-server (t2.micro)
      │               ↓
      │           [RDS] finance-ai-db (MySQL 8, db.t3.micro)
      │
      └── /* → [S3] finance-ai-frontend (React 빌드 파일)
```

---

## 각 서비스 설명과 설정 이유

### VPC (finance-ai-vpc)
- **왜?** EC2와 RDS를 같은 네트워크 안에 격리해서 RDS에 인터넷 직접 접근 차단
- 서브넷 2개 생성 (가용영역 다름) → ALB는 2개 이상의 AZ 서브넷 필요
- 인터넷 게이트웨이 연결 → EC2 인터넷 통신 가능

### RDS (finance-ai-db)
- **왜 VPC 안에?** DB는 인터넷에 노출되면 안 됨. EC2만 접근 가능하도록 격리
- 엔드포인트: `finance-ai-db.cggfqax3smkr.ap-northeast-2.rds.amazonaws.com`
- 포트: 3306 / 엔진: MySQL 8
- **현재 단일 AZ** → 향후 Multi-AZ 전환 시 장애 자동 복구 가능

### EC2 (finance-ai-server)
- **왜 t2.micro?** 프리티어(만료됨), 개발/테스트 목적
- Elastic IP 할당 → 재시작해도 IP 고정
- systemd 서비스 등록 → EC2 재시작 시 Spring Boot 자동 실행
- jar 위치: `/home/ec2-user/dashboard-0.0.1-SNAPSHOT.jar`

**systemd 서비스 파일 위치:** `/etc/systemd/system/finance-ai.service`

```ini
[Service]
User=ec2-user
Environment="DB_HOST=finance-ai-db.cggfqax3smkr.ap-northeast-2.rds.amazonaws.com"
Environment="SPRING_PROFILES_ACTIVE=prod"
ExecStart=/usr/bin/java -jar /home/ec2-user/dashboard-0.0.1-SNAPSHOT.jar
Restart=on-failure
```

**주요 명령어:**
```bash
sudo systemctl status finance-ai   # 상태 확인
sudo systemctl restart finance-ai  # 재시작
sudo journalctl -u finance-ai -f   # 실시간 로그
```

### S3 (finance-ai-frontend)
- **왜?** React 빌드 파일(정적 파일)은 서버 필요 없음. S3 정적 호스팅으로 비용 절감
- 버킷 정책: 퍼블릭 읽기 허용
- CloudFront 뒤에 위치 → 직접 S3 URL 접근보다 성능/보안 개선

### CloudFront
- **왜?** S3 단독 사용 시 HTTPS 불가, 전 세계 캐싱 없음
- **Nginx와의 차이:** Nginx는 로컬 리버스 프록시, CloudFront는 글로벌 CDN
- 오리진 2개 설정:
  - **기본:** S3 버킷 (정적 파일)
  - **ALB 오리진:** API 요청용
- 동작(Behavior) 설정:
  - `/api/*` → ALB 오리진 (캐시 없음)
  - `/*` 기본 → S3 오리진
- 오류 페이지: 403/404 → `/index.html` (200) → React 라우터 동작을 위해 필요

### ALB (Application Load Balancer)
- **왜 ALB?** EC2 직접 노출 대신 ALB를 앞에 두면: 헬스체크 자동화, 향후 오토스케일링 연동 가능
- 리스너: HTTP 80 → 대상그룹 forwarding
- 대상그룹 (finance-ai-tg): EC2 인스턴스, 포트 8080
- **헬스체크 경로:** `/api/stock/market/top` (처음 `/api/exchange/rates` → 500 떠서 변경)

---

## 보안 그룹 설계 원칙

```
[인터넷] → ALB (80/443) → EC2 (8080, ALB SG 소스만) → RDS (3306, EC2 SG 소스만)
```

ALB SG를 소스로 쓰는 이유: IP 범위 대신 SG를 소스로 지정하면
ALB가 어느 IP로 바뀌어도 자동 적용됨 (더 안전하고 유지보수 편함)

| 보안 그룹 | 인바운드 규칙 |
|---|---|
| finance-alb-sg | HTTP 80, HTTPS 443 / 소스: 0.0.0.0/0 |
| finance-ec2-sg | TCP 8080 / 소스: finance-alb-sg |
| finance-ec2-sg | SSH 22 / 소스: 0.0.0.0/0 (임시, 추후 제한 필요) |
| RDS SG | MySQL 3306 / 소스: finance-ec2-sg |

---

## CI/CD 흐름

```
개발자 git push origin main
        ↓
GitHub Actions (.github/workflows/deploy.yml)
        ↓
1. actions/checkout@v4
2. Java 21 설치 (corretto)
3. ./gradlew clean build -x test
4. appleboy/scp-action → jar 파일을 EC2 /home/ec2-user/ 에 복사
5. appleboy/ssh-action → sudo systemctl restart finance-ai
```

**GitHub Secrets 등록 목록:**
- `EC2_HOST` — EC2 퍼블릭 IP (Elastic IP)
- `EC2_USER` — ec2-user
- `EC2_KEY` — .pem 파일 내용 (개행 포함 전체)
- `EC2_PORT` — 22

---

## 프론트엔드 배포 방법

현재 자동화 안 됨 (수동 배포):

```bash
cd frontend
npm run build
aws s3 sync dist/ s3://finance-ai-frontend --delete
```

또는 AWS CLI 없이 S3 콘솔에서 직접 업로드.

`frontend/.env.production`:
```
VITE_API_BASE_URL=https://d17mchdxg3nzdu.cloudfront.net/api
```

---

## RDS 직접 접속 방법

RDS는 퍼블릭 접근 막혀있으므로 EC2 통해서 접속:

```bash
# EC2 SSH 접속 후
mysql -h finance-ai-db.cggfqax3smkr.ap-northeast-2.rds.amazonaws.com -u admin -p

# 또는 로컬에서 MySQL Workbench SSH 터널
# Connection Method: Standard TCP/IP over SSH
# SSH Host: EC2 퍼블릭 IP
# SSH Key: .pem 파일
# MySQL Host: RDS 엔드포인트
```

---

## 비용 절감 팁

| 상황 | 행동 |
|---|---|
| 안 쓸 때 | EC2 중지 + RDS 중지 (RDS는 최대 7일) |
| 장기 미사용 | ALB 삭제 (시간당 과금, 재생성 번거로움) |
| EC2 중지 | Elastic IP는 유지됨 (IP 안 바뀜) |

**시간당 대략 비용 (서울 리전, 2026년 기준):**
- EC2 t2.micro: $0.0116/h
- RDS db.t3.micro: $0.026/h
- ALB: $0.008/h
- S3/CloudFront: 요청 기반 (소량이면 거의 무료)

---

## 주요 트러블슈팅 기록

### 1. ALB Target Unhealthy
- **증상:** curl로 EC2 직접 호출은 200인데 ALB가 Unhealthy
- **원인:** EC2 보안 그룹이 0.0.0.0/0:8080을 허용했지만, ALB → EC2 통신 시 ALB SG 소스로 들어옴
- **해결:** finance-ec2-sg 인바운드에 `TCP 8080 / 소스: finance-alb-sg` 추가

### 2. Mixed Content 에러
- **증상:** CloudFront(HTTPS)에서 API 호출 시 브라우저 차단
- **원인:** `.env.production`이 EC2 HTTP URL을 직접 호출
- **해결:** CloudFront에 ALB 오리진 추가 → `/api/*` 동작 생성 → `.env.production`을 CloudFront URL로 변경

### 3. CloudFront 404
- **증상:** `/stocks/005930` 같은 React 라우터 경로 새로고침 시 404
- **원인:** S3는 해당 경로의 실제 파일이 없음
- **해결:** CloudFront 오류 페이지 → 403/404 → `/index.html` (응답 코드 200)

### 4. Health Check 500
- **증상:** `/api/exchange/rates` 를 헬스체크 경로로 설정했더니 500
- **원인:** 환율 API가 외부 EXIM API에 의존 → 외부 장애 시 500
- **해결:** 내부 DB 조회만 하는 `/api/stock/market/top`으로 변경

### 5. Spring Boot 시작 실패
- **증상:** `NoSuchBeanDefinitionException: JavaMailSender`
- **원인:** mail 설정 없을 때 JavaMailSender 빈이 생성되지 않음
- **해결:** `EmailService` 생성자에서 `Optional<JavaMailSender>` 로 받도록 변경

---

## 향후 개선 계획 (우선순위순)

### 1단계 — 가용성
- [ ] **Multi-AZ RDS** 전환
  - AWS RDS → 수정 → 다중 AZ 활성화
  - 마스터 장애 시 스탠바이로 자동 페일오버 (약 60~120초)
- [ ] **Read Replica** 생성
  - 읽기 전용 엔드포인트 분리 → 주식 조회 쿼리 분산
  - Spring에서 `@Transactional(readOnly=true)` 시 레플리카로 라우팅

### 2단계 — 모니터링
- [ ] **CloudWatch 알람**
  - EC2 CPU 80% 이상 → SNS 알림
  - RDS 연결 수 80% 이상 → SNS 알림
- [ ] **CloudWatch Logs**
  - EC2에 CloudWatch Agent 설치
  - `/var/log/finance-ai/` 로그 S3/CloudWatch로 수집

### 3단계 — 보안
- [ ] **AWS Secrets Manager**
  - RDS 비밀번호, JWT_SECRET을 systemd 평문에서 분리
  - Spring Boot에서 AWS SDK로 런타임에 조회
- [ ] **JWT_SECRET 교체**
  - 현재 기본값 → `openssl rand -base64 64` 로 생성한 키로 변경
- [ ] **EC2 SSH IP 제한**
  - GitHub Actions IP 대역으로만 SSH 허용

### 4단계 — 스케일링
- [ ] **Auto Scaling Group**
  - EC2 하나를 ASG로 래핑
  - CPU 70% 이상 시 인스턴스 추가, 30% 이하 시 축소
  - ALB와 자동 연동
- [ ] **프론트 배포 자동화**
  - GitHub Actions에 `npm run build + aws s3 sync` 스텝 추가
