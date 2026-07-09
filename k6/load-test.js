import http from 'k6/http';
import { check, sleep } from 'k6';
import { SharedArray } from 'k6/data';
import { Counter, Rate, Trend } from 'k6/metrics';

const BASE_URL = 'https://d17mchdxg3nzdu.cloudfront.net/api';

// 커스텀 메트릭
const loginSuccess = new Rate('login_success_rate');
const signupSuccess = new Rate('signup_success_rate');
const stockQueryTime = new Trend('stock_query_duration');
const errorCount = new Counter('error_count');

export const options = {
  scenarios: {
    // 1. 워밍업: 천천히 시작
    warmup: {
      executor: 'ramping-vus',
      startVUs: 0,
      stages: [
        { duration: '30s', target: 5 },
        { duration: '30s', target: 5 },
      ],
      gracefulRampDown: '10s',
      exec: 'stockScenario',
      tags: { scenario: 'warmup' },
    },

    // 2. 기본 부하: 일반 유저 시나리오
    normal_load: {
      executor: 'ramping-vus',
      startTime: '1m',
      startVUs: 0,
      stages: [
        { duration: '1m', target: 20 },
        { duration: '2m', target: 20 },
        { duration: '30s', target: 0 },
      ],
      gracefulRampDown: '10s',
      exec: 'userScenario',
      tags: { scenario: 'normal_load' },
    },

    // 3. 스트레스 테스트: 한계점 찾기
    stress_test: {
      executor: 'ramping-vus',
      startTime: '5m',
      startVUs: 0,
      stages: [
        { duration: '1m', target: 50 },
        { duration: '1m', target: 100 },
        { duration: '1m', target: 150 },
        { duration: '1m', target: 200 },
        { duration: '1m', target: 0 },
      ],
      gracefulRampDown: '30s',
      exec: 'stockScenario',
      tags: { scenario: 'stress_test' },
    },

    // 4. 스파이크 테스트: 갑작스러운 트래픽 급증
    spike_test: {
      executor: 'ramping-vus',
      startTime: '11m',
      startVUs: 0,
      stages: [
        { duration: '10s', target: 0 },
        { duration: '10s', target: 200 },  // 급증
        { duration: '1m', target: 200 },
        { duration: '10s', target: 0 },    // 급감
      ],
      gracefulRampDown: '10s',
      exec: 'stockScenario',
      tags: { scenario: 'spike_test' },
    },
  },

  thresholds: {
    http_req_duration: ['p(95)<3000'],   // 95%가 3초 이내
    http_req_failed: ['rate<0.1'],        // 에러율 10% 미만
    login_success_rate: ['rate>0.9'],     // 로그인 성공률 90% 이상
    stock_query_duration: ['p(90)<2000'], // 주식 조회 90%가 2초 이내
  },
};

// 랜덤 문자열 생성
function randomString(len) {
  const chars = 'abcdefghijklmnopqrstuvwxyz0123456789';
  let result = '';
  for (let i = 0; i < len; i++) {
    result += chars[Math.floor(Math.random() * chars.length)];
  }
  return result;
}

// 시나리오 1: 주식 조회만 (비로그인)
export function stockScenario() {
  const params = { headers: { 'Content-Type': 'application/json' } };

  // 시장 상위 종목 조회
  const start = Date.now();
  const marketRes = http.get(`${BASE_URL}/stock/market/top`, params);
  stockQueryTime.add(Date.now() - start);

  check(marketRes, {
    'market top 200': (r) => r.status === 200,
  }) || errorCount.add(1);

  sleep(1);

  // 개별 종목 조회 (삼성전자)
  const stockRes = http.get(`${BASE_URL}/stock/005930`, params);
  check(stockRes, {
    'stock detail 200': (r) => r.status === 200,
  }) || errorCount.add(1);

  sleep(1);

  // 환율 조회
  const exchangeRes = http.get(`${BASE_URL}/exchange/rates`, params);
  check(exchangeRes, {
    'exchange 200 or 500': (r) => r.status === 200 || r.status === 500,
  });

  sleep(Math.random() * 2 + 1);
}

// 시나리오 2: 전체 유저 플로우 (회원가입 → 로그인 → 관심종목)
export function userScenario() {
  const params = { headers: { 'Content-Type': 'application/json' } };
  const email = `test_${randomString(8)}@test.com`;
  const password = 'Test1234!';

  // 1. 회원가입
  const signupRes = http.post(
    `${BASE_URL}/auth/signup`,
    JSON.stringify({ email, password, nickname: `User_${randomString(4)}` }),
    params
  );
  const signupOk = check(signupRes, {
    'signup 200 or 201': (r) => r.status === 200 || r.status === 201,
  });
  signupSuccess.add(signupOk);

  if (!signupOk) {
    errorCount.add(1);
    sleep(2);
    return;
  }

  sleep(1);

  // 2. 로그인
  const loginRes = http.post(
    `${BASE_URL}/auth/login`,
    JSON.stringify({ email, password }),
    params
  );
  const loginOk = check(loginRes, {
    'login 200': (r) => r.status === 200,
    'has accessToken': (r) => {
      try { return JSON.parse(r.body).accessToken !== undefined; }
      catch { return false; }
    },
  });
  loginSuccess.add(loginOk);

  if (!loginOk) {
    errorCount.add(1);
    sleep(2);
    return;
  }

  const token = JSON.parse(loginRes.body).accessToken;
  const authParams = {
    headers: {
      'Content-Type': 'application/json',
      'Authorization': `Bearer ${token}`,
    },
  };

  sleep(1);

  // 3. 주식 검색
  const searchRes = http.get(`${BASE_URL}/stock/search?keyword=삼성`, authParams);
  check(searchRes, { 'search 200': (r) => r.status === 200 });

  sleep(1);

  // 4. 관심종목 추가
  const favRes = http.post(
    `${BASE_URL}/stock/favorites`,
    JSON.stringify({ stockSymbol: '005930', stockName: '삼성전자' }),
    authParams
  );
  check(favRes, { 'favorite add 200 or 201': (r) => r.status === 200 || r.status === 201 });

  sleep(1);

  // 5. 관심종목 목록 조회
  const favListRes = http.get(`${BASE_URL}/stock/favorites`, authParams);
  check(favListRes, { 'favorites list 200': (r) => r.status === 200 });

  sleep(1);

  // 6. 시장 데이터 조회
  const start = Date.now();
  const marketRes = http.get(`${BASE_URL}/stock/market/top`, authParams);
  stockQueryTime.add(Date.now() - start);
  check(marketRes, { 'market top 200': (r) => r.status === 200 });

  sleep(Math.random() * 3 + 1);
}
