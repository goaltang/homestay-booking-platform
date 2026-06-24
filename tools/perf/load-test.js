// =============================================================================
// 民宿系统 - k6 性能压测脚本
// =============================================================================
// 目标：测量后端关键接口的 TPS / p95 / 错误率
// 场景：80% 列表 + 15% 详情 + 5% 下单（贴近真实流量分布）
//
// 跑法：
//   k6 run tools/perf/load-test.js
//   k6 run --out json=tools/perf/reports/result.json tools/perf/load-test.js
// =============================================================================

import http from 'k6/http';
import { check, sleep, group } from 'k6';
import { Rate, Trend, Counter } from 'k6/metrics';
import { SharedArray } from 'k6/data';

// ---------- 配置 ----------
const BASE_URL = __ENV.BASE_URL || 'http://127.0.0.1:8080';
const HOMESTAY_ID_MIN = 1;
const HOMESTAY_ID_MAX = 5000;
const HOMESTAY_IDS = new SharedArray('homestay_ids', function () {
  const ids = [];
  for (let i = HOMESTAY_ID_MIN; i <= HOMESTAY_ID_MAX; i++) {
    ids.push(i);
  }
  return ids;
});

// ---------- 自定义指标 ----------
const errorRate = new Rate('errors');
const loginTrend = new Trend('login_duration', true);
const listTrend = new Trend('homestay_list_duration', true);
const detailTrend = new Trend('homestay_detail_duration', true);
const orderTrend = new Trend('order_create_duration', true);
const orderSuccessCounter = new Counter('order_success');

// ---------- 压测场景 ----------
export const options = {
  scenarios: {
    list_heavy: {
      executor: 'ramping-vus',
      startVUs: 0,
      stages: [
        { duration: '20s', target: 50 },   // 预热
        { duration: '40s', target: 200 },  // 加压
        { duration: '30s', target: 200 },  // 稳定
        { duration: '20s', target: 0 },    // 降压
      ],
      gracefulRampDown: '10s',
      exec: 'listScenario',
      tags: { scenario: 'list' },
    },
    detail_mixed: {
      executor: 'constant-arrival-rate',
      rate: 100,
      timeUnit: '1s',
      duration: '1m50s',
      preAllocatedVUs: 30,
      maxVUs: 80,
      exec: 'detailScenario',
      tags: { scenario: 'detail' },
      startTime: '10s',
    },
    order_concurrent: {
      executor: 'constant-arrival-rate',
      rate: 20,
      timeUnit: '1s',
      duration: '1m30s',
      preAllocatedVUs: 20,
      maxVUs: 50,
      exec: 'orderScenario',
      tags: { scenario: 'order' },
      startTime: '20s',
    },
  },
  thresholds: {
    'http_req_failed':                ['rate<0.01'],     // 错误率 < 1%
    'http_req_duration{scenario:list}':   ['p(95)<500'],  // 列表 p95 < 500ms
    'http_req_duration{scenario:detail}': ['p(95)<800'],  // 详情 p95 < 800ms
    'http_req_duration{scenario:order}':  ['p(95)<1500'], // 下单 p95 < 1.5s
  },
  noConnectionReuse: false,
  userAgent: 'k6-perf-test/1.0',
};

// ---------- 工具函数 ----------
function randomHomestayId() {
  return HOMESTAY_IDS[Math.floor(Math.random() * HOMESTAY_IDS.length)];
}

function randomDateOffset(min, max) {
  const offset = min + Math.floor(Math.random() * (max - min));
  const d = new Date(Date.now() + offset * 24 * 3600 * 1000);
  return d.toISOString().slice(0, 10);
}

// token 缓存：所有 VU 共享（但 k6 VU 隔离，所以每个 VU 自己登录一次）
let token = null;
let tokenExpiry = 0;

function getToken() {
  // token 5 分钟内复用
  if (token && Date.now() < tokenExpiry) return token;

  const loginRes = http.post(
    `${BASE_URL}/api/auth/login`,
    JSON.stringify({ username: 'perf_guest_1', password: '123456' }),
    { headers: { 'Content-Type': 'application/json' }, tags: { api: 'login' } }
  );

  loginTrend.add(loginRes.timings.duration);

  if (loginRes.status !== 200) {
    console.error(`登录失败: ${loginRes.status} ${loginRes.body}`);
    return null;
  }
  try {
    const body = JSON.parse(loginRes.body);
    token = body.token || body.accessToken || body.data?.token;
    tokenExpiry = Date.now() + 5 * 60 * 1000;
    return token;
  } catch (e) {
    console.error('解析登录响应失败:', e);
    return null;
  }
}

function authHeaders() {
  const t = getToken();
  return t
    ? { headers: { 'Content-Type': 'application/json', 'Authorization': `Bearer ${t}` } }
    : { headers: { 'Content-Type': 'application/json' } };
}

// ---------- 场景 1：列表查询（流量大头，缓存命中点） ----------
export function listScenario() {
  group('list_homestays', function () {
    const city = ['110000', '310000', '330100', '510100', '440300'][Math.floor(Math.random() * 5)];
    const url = `${BASE_URL}/api/homestays?cityCode=${city}&page=0&size=20`;
    const res = http.get(url, { tags: { api: 'homestay_list' } });

    listTrend.add(res.timings.duration);
    const ok = check(res, {
      'list status 200': r => r.status === 200,
      'list has data':   r => {
        try { return JSON.parse(r.body).data !== undefined; } catch { return false; }
      },
    });
    errorRate.add(!ok);
  });
  sleep(Math.random() * 2 + 0.5);
}

// ---------- 场景 2：详情查询（带库存/评论关联） ----------
export function detailScenario() {
  group('detail_homestay', function () {
    const id = randomHomestayId();
    const res = http.get(`${BASE_URL}/api/homestays/${id}`, { tags: { api: 'homestay_detail' } });

    detailTrend.add(res.timings.duration);
    const ok = check(res, { 'detail status 200': r => r.status === 200 });
    errorRate.add(!ok);
  });
  sleep(Math.random() * 1.5 + 0.3);
}

// ---------- 场景 3：下单（写库 + 库存校验，最易爆） ----------
export function orderScenario() {
  const homestayId = randomHomestayId();
  const checkIn = randomDateOffset(7, 30);
  const checkOut = (() => {
    const d = new Date(checkIn);
    d.setDate(d.getDate() + 2);
    return d.toISOString().slice(0, 10);
  })();

  const payload = JSON.stringify({
    homestayId: homestayId,
    checkInDate: checkIn,
    checkOutDate: checkOut,
    guestCount: 2,
    guestName: '压测用户',
    guestPhone: '13900000000',
    remark: 'k6-perf',
  });

  const res = http.post(`${BASE_URL}/api/orders`, payload, { ...authHeaders(), tags: { api: 'order_create' } });

  orderTrend.add(res.timings.duration);
  const isSuccess = res.status === 201 || res.status === 200;
  const ok = check(res, {
    'order status 2xx': r => r.status === 201 || r.status === 200,
    'order has number': r => {
      try { return JSON.parse(r.body).orderNumber !== undefined; } catch { return false; }
    },
  });
  errorRate.add(!ok);
  if (isSuccess) orderSuccessCounter.add(1);

  sleep(Math.random() * 2 + 1);
}

// ---------- 收尾 ----------
export function handleSummary(data) {
  const summary = {
    timestamp: new Date().toISOString(),
    base_url: BASE_URL,
    total_requests: data.metrics.http_reqs?.values?.count || 0,
    rps: data.metrics.http_reqs?.values?.rate || 0,
    avg_duration_ms: data.metrics.http_req_duration?.values?.avg || 0,
    p95_duration_ms: data.metrics.http_req_duration?.values?.['p(95)'] || 0,
    p99_duration_ms: data.metrics.http_req_duration?.values?.['p(99)'] || 0,
    error_rate: data.metrics.http_req_failed?.values?.rate || 0,
    list_p95:    data.metrics['http_req_duration{scenario:list}']?.values?.['p(95)'] || 0,
    detail_p95:  data.metrics['http_req_duration{scenario:detail}']?.values?.['p(95)'] || 0,
    order_p95:   data.metrics['http_req_duration{scenario:order}']?.values?.['p(95)'] || 0,
    order_success: data.metrics.order_success?.values?.count || 0,
  };
  console.log('\n========== 压测汇总 ==========');
  console.log(JSON.stringify(summary, null, 2));
  return {
    'stdout': textSummary(data, { indent: '  ', enableColors: true }),
    'tools/perf/reports/summary.json': JSON.stringify(summary, null, 2),
  };
}

import { textSummary } from 'https://jslib.k6.io/k6-summary/0.0.3/index.js';
