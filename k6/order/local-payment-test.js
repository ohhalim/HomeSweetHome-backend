import http from 'k6/http';
import { check, sleep } from 'k6';
import { Counter, Trend } from 'k6/metrics';
import {
    parseBool,
    parsePositiveInt,
    parseIdCsv,
    pickRandom,
    randomInt,
    jsonAuthHeaders,
    discoverUserPool,
    discoverSkuPool,
} from './order-test-data.js';

const orderCreationErrors = new Counter('order_creation_errors');
const paymentConfirmErrors = new Counter('payment_confirm_errors');
const orderCreationDuration = new Trend('order_creation_duration');
const paymentConfirmDuration = new Trend('payment_confirm_duration');
const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const USE_REAL_TOSS = parseBool(__ENV.USE_REAL_TOSS, false);
const LOAD_PROFILE = String(__ENV.LOAD_PROFILE || 'after').toLowerCase();
const RUN_LABEL = __ENV.RUN_LABEL || `payment-flow-${LOAD_PROFILE}`;

const USER_IDS = parseIdCsv(__ENV.USER_IDS);
const SKU_IDS = parseIdCsv(__ENV.SKU_IDS);
const DISCOVER_USERS = parseBool(__ENV.DISCOVER_USERS, true);
const DISCOVER_SKUS = parseBool(__ENV.DISCOVER_SKUS, true);
const AUTH_PROBE_PATH = __ENV.AUTH_PROBE_PATH || '/api/v1/orders';

const USER_SCAN_START = parsePositiveInt(__ENV.USER_SCAN_START, 1);
const USER_SCAN_END = parsePositiveInt(__ENV.USER_SCAN_END, 300);
const MIN_USER_POOL = parsePositiveInt(__ENV.MIN_USER_POOL, 5);

const PRODUCT_DISCOVERY_PAGES = parsePositiveInt(__ENV.PRODUCT_DISCOVERY_PAGES, 5);
const PRODUCT_DISCOVERY_LIMIT = parsePositiveInt(__ENV.PRODUCT_DISCOVERY_LIMIT, 24);
const MAX_PRODUCTS_TO_SCAN = parsePositiveInt(__ENV.MAX_PRODUCTS_TO_SCAN, 60);
const MIN_SKU_POOL = parsePositiveInt(__ENV.MIN_SKU_POOL, 10);
const MIN_STOCK = parsePositiveInt(__ENV.MIN_STOCK, 1);

const SKIP_PAYMENT_CONFIRM = parseBool(__ENV.SKIP_PAYMENT_CONFIRM, true);

function buildStages(profile) {
    if (profile === 'before') {
        return [
            { duration: '20s', target: 5 },
            { duration: '40s', target: 20 },
            { duration: '1m', target: 20 },
            { duration: '20s', target: 0 },
        ];
    }

    return [
        { duration: '30s', target: 10 },
        { duration: '1m', target: 50 },
        { duration: '2m', target: 50 },
        { duration: '30s', target: 0 },
    ];
}

export const options = {
    stages: buildStages(LOAD_PROFILE),
    tags: {
        load_profile: LOAD_PROFILE,
        run_label: RUN_LABEL,
    },
    thresholds: {
        http_req_duration: ['p(95)<4000'],
        http_req_failed: [{ threshold: 'rate<0.50', abortOnFail: true }],
        order_creation_duration: ['p(95)<3000'],
        payment_confirm_duration: ['p(95)<3000'],
        checks: ['rate>0.80'],
        order_creation_errors: ['count<200'],
        payment_confirm_errors: ['count<200'],
    },
};

export function setup() {
    if (USE_REAL_TOSS) {
        throw new Error('local-payment-test.js는 mock 결제 전용입니다. 실제 Toss 연동 검증은 payment-confirm-smoke.js를 사용하세요.');
    }

    const users = discoverUserPool({
        baseUrl: BASE_URL,
        authProbePath: AUTH_PROBE_PATH,
        candidateUserIds: USER_IDS,
        scanStart: USER_SCAN_START,
        scanEnd: USER_SCAN_END,
        minUsers: MIN_USER_POOL,
        discoverUsers: DISCOVER_USERS,
    });

    if (users.length === 0) {
        throw new Error('유효한 userId를 찾지 못했습니다. USER_IDS 환경변수를 지정하세요.');
    }

    const skuDiscovery = discoverSkuPool({
        baseUrl: BASE_URL,
        candidateSkuIds: SKU_IDS,
        discoverSkus: DISCOVER_SKUS,
        productDiscoveryPages: PRODUCT_DISCOVERY_PAGES,
        productDiscoveryLimit: PRODUCT_DISCOVERY_LIMIT,
        maxProductsToScan: MAX_PRODUCTS_TO_SCAN,
        minSkuPool: MIN_SKU_POOL,
        minStock: MIN_STOCK,
    });

    if (skuDiscovery.skuIds.length === 0) {
        throw new Error('유효한 skuId를 찾지 못했습니다. SKU_IDS를 지정하거나 상품 재고를 확인하세요.');
    }

    const health = http.get(`${BASE_URL}/actuator/health`, {
        tags: { name: 'SETUP health', run_label: RUN_LABEL },
    });
    if (health.status !== 200) {
        throw new Error(`백엔드 헬스체크 실패: status=${health.status}, body=${health.body}`);
    }

    console.log('결제 부하 테스트 시작');
    console.log(`BASE_URL=${BASE_URL}`);
    console.log(`LOAD_PROFILE=${LOAD_PROFILE}, RUN_LABEL=${RUN_LABEL}`);
    console.log(`users=${users.length}, skus=${skuDiscovery.skuIds.length}`);
    console.log(`payment_confirm=${SKIP_PAYMENT_CONFIRM ? 'disabled' : 'enabled'}`);

    return {
        users,
        skuIds: skuDiscovery.skuIds,
    };
}

export default function (data) {
    const userId = pickRandom(data.users);
    const skuId = pickRandom(data.skuIds);
    const quantity = randomInt(1, 3);

    const orderPayload = JSON.stringify({
        orderItems: [{
            skuId,
            quantity,
        }],
        recipientName: `Test User ${userId}`,
        recipientPhone: '010-1234-5678',
        shippingAddress: `Test Address ${userId}`,
        shippingRequest: '배송 전 연락주세요',
    });

    const orderStartTime = Date.now();
    const orderRes = http.post(`${BASE_URL}/api/v1/orders`, orderPayload, {
        headers: jsonAuthHeaders(userId),
        tags: { name: 'CreateOrder', run_label: RUN_LABEL },
    });
    orderCreationDuration.add(Date.now() - orderStartTime);

    const orderSuccess = check(orderRes, {
        '주문 생성 성공 (200/201)': (r) => r.status === 200 || r.status === 201,
        order_has_orderNumber: (r) => {
            try {
                const body = r.json();
                return body.orderNumber !== undefined;
            } catch (e) {
                return false;
            }
        },
    });

    if (!orderSuccess) {
        orderCreationErrors.add(1);
        return;
    }

    const orderData = orderRes.json();
    const orderNumber = orderData.orderNumber;
    const totalAmount = orderData.totalAmount;

    sleep(1);

    if (SKIP_PAYMENT_CONFIRM) {
        sleep(2);
        return;
    }

    const paymentPayload = JSON.stringify({
        paymentKey: `test_payment_${orderNumber}_${Date.now()}`,
        orderId: orderNumber,
        amount: totalAmount,
    });

    const paymentStartTime = Date.now();
    const paymentRes = http.post(`${BASE_URL}/api/v1/payments/confirm`, paymentPayload, {
        headers: jsonAuthHeaders(userId),
        tags: { name: 'ConfirmPayment', run_label: RUN_LABEL },
    });
    paymentConfirmDuration.add(Date.now() - paymentStartTime);

    const paymentSuccess = check(paymentRes, {
        '결제 승인 성공 (200/201)': (r) => r.status === 200 || r.status === 201,
        payment_body_parseable: (r) => {
            try {
                r.json();
                return true;
            } catch (e) {
                return false;
            }
        },
    });

    if (!paymentSuccess) {
        paymentConfirmErrors.add(1);
    }

    sleep(2);
}

export function teardown() {
    console.log('결제 부하 테스트 완료');
}

export function handleSummary(data) {
    return {
        stdout: textSummary(data),
        [`artifacts/k6/${RUN_LABEL}/summary.json`]: JSON.stringify(data),
    };
}

function textSummary(data) {
    const metrics = data && data.metrics ? data.metrics : {};
    const req = metrics.http_reqs && metrics.http_reqs.values ? metrics.http_reqs.values.count : 0;
    const failRate = metrics.http_req_failed && metrics.http_req_failed.values ? metrics.http_req_failed.values.rate : 0;
    const p95 = metrics.http_req_duration && metrics.http_req_duration.values ? metrics.http_req_duration.values['p(95)'] : 0;
    return [
        '',
        `run_label=${RUN_LABEL} load_profile=${LOAD_PROFILE}`,
        `http_reqs=${req}`,
        `http_req_failed_rate=${failRate}`,
        `http_req_duration_p95=${p95}`,
    ].join('\n');
}
