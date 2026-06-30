# AWS 배포 + SAA 학습 런북

리전: **ap-northeast-2 (서울)** · 콘솔 수동 구축 · IaC 없음 (개념 학습 목적)

이 문서는 3단계로 구성됩니다.
- **Phase 1**: VPC + EC2(ASG, 최소 2대) + ALB + RDS — Docker Compose를 AWS 네이티브 배포로 전환
- **Phase 2**: EventBridge Scheduler + Lambda — 시그널 생성 크론을 서버리스로 교체
- **Phase 3**: SNS — 시그널 알림을 SNS Topic으로 발행

각 단계는 독립적으로 완료 가능하며, Phase 1이 끝나야 ALB 엔드포인트가 생겨서 Phase 2의 Lambda가 호출할 대상이 생깁니다.

---

## Phase 1 — VPC + EC2(ASG) + ALB + RDS

### 1-1. VPC 생성
**VPC 콘솔 → VPC 생성 → "VPC 등"**
- 이름: `financeai-vpc`
- IPv4 CIDR: `10.0.0.0/16`
- 가용 영역(AZ) 수: **2**
- 퍼블릭 서브넷 수: **2** (`10.0.0.0/24`, `10.0.1.0/24`)
- 프라이빗 서브넷 수: **2** (`10.0.10.0/24`, `10.0.11.0/24`)
- NAT 게이트웨이: **없음** (비용 절감 — RDS는 outbound 불필요, EC2는 퍼블릭 서브넷에 둘 것)
- VPC 엔드포인트: 없음 (지금은 생략)

> **SAA 포인트**: VPC 생성 마법사가 IGW, 라우트 테이블(퍼블릭→IGW, 프라이빗→로컬만)을 자동으로 만들어줍니다. 콘솔에서 직접 만들고 싶으면 "VPC만" 옵션으로 시작해서 서브넷/IGW/라우트 테이블을 하나씩 만들어보세요 — 시험에 더 자주 나오는 부분입니다.

### 1-2. 보안 그룹 3종 생성
**EC2 콘솔 → 보안 그룹 → 보안 그룹 생성** (VPC: `financeai-vpc`)

| 이름 | 인바운드 규칙 |
|------|----------------|
| `financeai-alb-sg` | HTTP(80) from `0.0.0.0/0`, HTTPS(443) from `0.0.0.0/0` |
| `financeai-ec2-sg` | HTTP(8080) from `financeai-alb-sg`, SSH(22) from 내 IP만 |
| `financeai-rds-sg` | MySQL(3306) from `financeai-ec2-sg` |

> **SAA 포인트**: 보안 그룹은 "source"로 다른 보안 그룹을 지정할 수 있습니다(IP 대역이 아니라). ALB→EC2, EC2→RDS를 전부 SG 참조로 연결하면 IP가 바뀌어도 규칙을 안 고쳐도 됩니다.

### 1-3. RDS (MySQL) 생성
**RDS 콘솔 → 데이터베이스 생성**
- 엔진: MySQL 8.0
- 템플릿: 프리 티어 (db.t3.micro / db.t4g.micro)
- DB 인스턴스 식별자: `financeai-db`
- 마스터 사용자: `root`, 비밀번호는 직접 설정 (나중에 Secrets Manager로 옮길 예정이니 메모해두기)
- VPC: `financeai-vpc`
- 서브넷 그룹: 새로 생성 → 프라이빗 서브넷 2개 선택
- 퍼블릭 액세스: **아니요**
- VPC 보안 그룹: `financeai-rds-sg`
- 초기 데이터베이스 이름: `finance_db`
- Multi-AZ: 선택(비용 2배, HA 연습용이면 켜보고 끝나면 끄기)

생성 후 **엔드포인트 주소**를 메모해둡니다 (예: `financeai-db.xxxxx.ap-northeast-2.rds.amazonaws.com`).

### 1-4. Secrets Manager에 자격증명 저장
**Secrets Manager 콘솔 → 새 보안 암호 저장**
- 유형: "Amazon RDS 데이터베이스에 대한 자격 증명"
- DB 사용자명/비밀번호 입력, RDS 인스턴스 `financeai-db` 선택
- 이름: `financeai/rds`

추가로 일반 텍스트 보안 암호 하나 더 생성:
- 이름: `financeai/app-secrets`
- 키-값: `JWT_SECRET`, `EXIM_API_KEY`, `OPENAI_API_KEY`, `ALPHA_VANTAGE_API_KEY`, `SERVICE_TOKEN`(아무 긴 랜덤 문자열, Lambda가 내부 API 호출 시 쓸 공유 토큰) 등

### 1-5. IAM Role 생성 (EC2용)
**IAM 콘솔 → 역할 생성 → AWS 서비스 → EC2**
- 이름: `financeai-ec2-role`
- 정책: `SecretsManagerReadWrite` 대신 **직접 만든 최소 권한 정책** 권장
  ```json
  {
    "Version": "2012-10-17",
    "Statement": [{
      "Effect": "Allow",
      "Action": ["secretsmanager:GetSecretValue"],
      "Resource": [
        "arn:aws:secretsmanager:ap-northeast-2:*:secret:financeai/*"
      ]
    }]
  }
  ```

> **SAA 포인트**: EC2에 자격증명을 하드코딩하거나 `.env` 파일로 넣지 않고, IAM Role을 인스턴스에 붙여서 SDK가 자동으로 임시 자격증명을 받아오게 하는 게 "최소 권한 원칙"의 핵심 예제입니다.

### 1-6. EC2 시작 템플릿 생성
**EC2 콘솔 → 시작 템플릿 → 시작 템플릿 생성**
- 이름: `financeai-launch-template`
- AMI: Amazon Linux 2023
- 인스턴스 유형: t3.micro (프리 티어)
- 키 페어: 새로 생성 또는 기존 사용 (SSH 디버깅용)
- 네트워크: 퍼블릭 서브넷, **퍼블릭 IP 자동 할당 활성화**
- 보안 그룹: `financeai-ec2-sg`
- IAM 인스턴스 프로파일: `financeai-ec2-role`
- 사용자 데이터(User data) — Docker 설치 + 컨테이너 기동:
  ```bash
  #!/bin/bash
  dnf install -y docker
  systemctl enable --now docker
  usermod -aG docker ec2-user

  SECRET_JSON=$(aws secretsmanager get-secret-value --secret-id financeai/app-secrets --region ap-northeast-2 --query SecretString --output text)
  DB_PASSWORD=$(aws secretsmanager get-secret-value --secret-id financeai/rds --region ap-northeast-2 --query SecretString --output text | jq -r .password)
  JWT_SECRET=$(echo "$SECRET_JSON" | jq -r .JWT_SECRET)
  EXIM_API_KEY=$(echo "$SECRET_JSON" | jq -r .EXIM_API_KEY)
  SERVICE_TOKEN=$(echo "$SECRET_JSON" | jq -r .SERVICE_TOKEN)

  docker run -d --name financeai-backend -p 8080:8080 \
    -e DB_HOST=<RDS 엔드포인트> \
    -e DB_PORT=3306 \
    -e DB_NAME=finance_db \
    -e DB_USERNAME=root \
    -e DB_PASSWORD="$DB_PASSWORD" \
    -e JWT_SECRET="$JWT_SECRET" \
    -e EXIM_API_KEY="$EXIM_API_KEY" \
    -e SERVICE_TOKEN="$SERVICE_TOKEN" \
    -e SIGNAL_SCHEDULER_ENABLED=false \
    -e SPRING_PROFILES_ACTIVE=local \
    <ECR 이미지 URI 또는 Docker Hub 이미지>:latest
  ```
  > `SIGNAL_SCHEDULER_ENABLED=false`로 인스턴스 내장 크론을 꺼야 합니다 — ASG로 2대 이상 떠 있으면 인스턴스마다 중복 실행되기 때문입니다. Phase 2의 Lambda+EventBridge가 그 역할을 대신합니다.
  > 이미지는 ECR(Elastic Container Registry)에 푸시해두는 걸 권장 — 이것도 SAA 범위(ECR)입니다. `docker tag` → `docker push`로 올리고 위 user data에서 그 URI를 참조하세요.

### 1-7. Application Load Balancer 생성
**EC2 콘솔 → 로드 밸런서 → 생성 → Application Load Balancer**
- 이름: `financeai-alb`
- 체계: 인터넷 경계(internet-facing)
- VPC: `financeai-vpc`, 퍼블릭 서브넷 2개 선택
- 보안 그룹: `financeai-alb-sg`
- 리스너: HTTP:80 → 나중에 HTTPS:443 추가 (ACM 인증서 필요, Phase 1 끝나고 추가)
- 대상 그룹: 새로 생성
  - 이름: `financeai-tg`
  - 프로토콜/포트: HTTP / 8080
  - 상태 검사 경로: `/api/exchange/today` (200 또는 503도 "살아있음"으로 간주되게 매처 조정 필요 — 간단히는 `/actuator/health` 엔드포인트를 추가하는 걸 추천)

### 1-8. Auto Scaling Group 생성
**EC2 콘솔 → Auto Scaling 그룹 → 생성**
- 이름: `financeai-asg`
- 시작 템플릿: `financeai-launch-template`
- VPC/서브넷: 퍼블릭 서브넷 2개
- 로드 밸런싱: 기존 대상 그룹(`financeai-tg`)에 연결
- 그룹 크기: **최소 2 / 원하는 용량 2 / 최대 4**
- 조정 정책: 대상 추적, 평균 CPU 사용률 60%

생성 후 ASG가 인스턴스 2대를 띄우고 ALB 대상 그룹에 자동 등록됩니다. **대상 그룹의 상태 검사가 "Healthy"가 되는지 꼭 확인하세요.**

### 1-9. 검증
```bash
curl http://<ALB DNS 이름>/api/exchange/today
```
정상 응답이 오면 Phase 1 완료. 인스턴스 1대를 강제 종료해도 ASG가 새로 띄우고 ALB가 트래픽을 나머지 정상 인스턴스로만 보내는지 확인해보세요 — 이게 SAA의 "고가용성" 핵심 시나리오입니다.

### (선택) 프론트엔드를 S3 + CloudFront로
- S3 버킷 생성(정적 웹 호스팅 비활성, OAC로 CloudFront만 접근 허용 — 퍼블릭 버킷 안티패턴 피하기)
- `npm run build` 결과물(`frontend/dist`)을 S3에 업로드
- CloudFront 배포 생성, Origin: S3(OAC), 캐시 무효화는 배포 시마다
- Route53에 CNAME/ALIAS로 CloudFront 연결

### (선택) HTTPS
- ACM에서 도메인 인증서 발급(Route53에 도메인 있으면 DNS 검증 자동)
- ALB 리스너에 HTTPS:443 추가, 인증서 연결, HTTP→HTTPS 리다이렉트 규칙 추가

---

## Phase 2 — EventBridge Scheduler + Lambda

목표: 백엔드의 `SignalService.generateDailySignals()` (`@Scheduled(cron = "${signal.cron}")`)를 대체. EC2가 여러 대(ASG)면 cron이 여러 인스턴스에서 중복 실행될 수 있다는 문제도 있는데, Lambda+EventBridge로 빼면 "단일 실행 보장"도 해결됩니다.

### 2-1. Lambda 함수 생성
**Lambda 콘솔 → 함수 생성**
- 이름: `financeai-signal-trigger`
- 런타임: Node.js 20.x (또는 Python 3.12 — 취향껏)
- 실행 역할: 새로 생성, 기본 Lambda 권한만 (VPC 안 들어가므로 추가 권한 불필요)

코드 (Node.js 예시):
```javascript
export const handler = async () => {
  const res = await fetch("http://<ALB DNS 이름>/api/internal/signals/generate", {
    method: "POST",
    headers: { "X-Service-Token": process.env.SERVICE_TOKEN }
  });
  const body = await res.json();
  console.log("generated signals:", body);
  return { statusCode: res.status, body: JSON.stringify(body) };
};
```

백엔드에 이미 `/api/internal/signals/generate` 엔드포인트가 구현되어 있습니다 ([InternalController.java](../src/main/java/com/finance/dashboard/controller/InternalController.java)). 일반 사용자 JWT가 아니라 `X-Service-Token` 헤더를 [ServiceTokenAuthenticationFilter](../src/main/java/com/finance/dashboard/security/ServiceTokenAuthenticationFilter.java)가 검증하는 별도 경로이므로 Lambda가 그대로 호출하면 됩니다.

환경 변수: `SERVICE_TOKEN` — Secrets Manager(`financeai/app-secrets`)의 값과 **반드시 동일해야** 합니다. Lambda 콘솔에서 직접 입력하거나, 실행 역할에 `secretsmanager:GetSecretValue` 권한을 추가해 런타임에 가져오도록 구성하세요.

### 2-2. EventBridge Scheduler 생성
**Amazon EventBridge 콘솔 → Scheduler → 일정 생성**
- 이름: `financeai-daily-signal`
- 일정 패턴: Cron 식 — `30 9 ? * MON-FRI *` (UTC 기준이므로 KST 09:30 = UTC 00:30 → `30 0 ? * MON-FRI *`)
- 시간대: Asia/Seoul로 지정하면 cron을 KST 그대로 써도 됨
- 대상: Lambda 함수 `financeai-signal-trigger`
- 재시도 정책: 최대 재시도 1~2회

### 2-3. 검증
EventBridge 콘솔에서 "지금 실행" 또는 Lambda 콘솔에서 수동 테스트 이벤트로 트리거 → CloudWatch Logs에서 실행 로그 확인 → DB의 `stock_signals` 테이블에 새 행이 생기는지 확인.

---

## Phase 3 — SNS 알림

목표: 시그널 발생 시 이메일을 SMTP 대신(또는 함께) SNS로 발행.

### 3-1. SNS Topic 생성
**SNS 콘솔 → 주제 생성**
- 유형: 표준(Standard)
- 이름: `financeai-signal-alerts`

### 3-2. 이메일 구독 추가
**주제 → 구독 생성**
- 프로토콜: 이메일
- 엔드포인트: 본인 이메일 (구독 확인 메일이 오면 클릭해서 승인해야 함)

### 3-3. 발행 주체 선택
두 가지 방식 중 택1:

**(A) Lambda가 발행** — Phase 2의 `financeai-signal-trigger`가 백엔드 호출 응답(생성된 시그널 목록)을 받은 뒤 SNS Publish API 호출. Lambda 실행 역할에 `sns:Publish` 권한 추가 필요.

**(B) 백엔드(EC2)가 직접 발행** — 현재 `EmailService.sendSignalAlert()`를 SNS SDK 호출로 교체/병행. EC2 IAM Role(`financeai-ec2-role`)에 `sns:Publish` 권한 추가.

> SAA 학습 관점에서는 **(A) Lambda 발행**이 더 깔끔합니다 — "이벤트 발생 → 서버리스 처리 → 팬아웃 알림"이 한 흐름에 다 들어갑니다.

### 3-4. 검증
시그널이 실제로 생성되는 날(또는 수동 트리거 시 DB에 시그널이 insert된 경우) 구독한 이메일로 알림이 오는지 확인.

---

## 비용 주의사항

| 리소스 | 프리티어 한도 | 한도 초과 시 |
|--------|--------------|--------------|
| EC2 t3.micro | 월 750시간(1대 무료), 2대 운영하면 1대분 과금 | 시간당 약 $0.0116 |
| RDS db.t3.micro | 월 750시간 | 시간당 약 $0.017 |
| ALB | 프리티어 없음 | 시간당 약 $0.0225 + LCU |
| NAT Gateway | 사용 안 함(설계상 제외) | 시간당 $0.059 + 데이터 처리비 (켜면 꽤 나옴) |
| Lambda | 월 100만 건 무료 | 거의 안 나옴 (하루 1회 실행) |
| SNS | 월 100만 건 무료 | 거의 안 나옴 |

**가장 큰 비용 요인은 ALB(상시 과금)와 RDS/EC2 2대 운영입니다.** 학습 끝나면 ASG 용량을 0으로 낮추거나 리소스를 삭제하세요.

---

## 진행 체크리스트

- [ ] Phase 1-1 VPC
- [ ] Phase 1-2 보안 그룹 3종
- [ ] Phase 1-3 RDS
- [ ] Phase 1-4 Secrets Manager
- [ ] Phase 1-5 IAM Role
- [ ] Phase 1-6 시작 템플릿 (+ ECR 이미지 푸시)
- [ ] Phase 1-7 ALB
- [ ] Phase 1-8 ASG
- [ ] Phase 1-9 검증
- [ ] Phase 2 Lambda + EventBridge (백엔드 내부 인증 추가 선행)
- [ ] Phase 3 SNS
