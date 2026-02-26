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
const orderCreationDuration = new Trend('order_creation_duration');
const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const LOAD_PROFILE = String(__ENV.LOAD_PROFILE || 'after').toLowerCase();
const RUN_LABEL = __ENV.RUN_LABEL || `order-create-${LOAD_PROFILE}`;

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

function buildStages(profile) {
    if (profile === 'before') {
        return [
            { duration: '20s', target: 8 },
            { duration: '40s', target: 25 },
            { duration: '1m', target: 25 },
            { duration: '20s', target: 0 },
        ];
    }

    return [
        { duration: '30s', target: 20 },
        { duration: '1m', target: 100 },
        { duration: '3m', target: 100 },
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
        http_req_duration: ['p(95)<3000'],
        http_req_failed: [{ threshold: 'rate<0.50', abortOnFail: true }],
        order_creation_duration: ['p(95)<2500'],
        checks: ['rate>0.90'],
        order_creation_errors: ['count<100'],
    },
};

export function setup() {
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

    const health = http.get(`${BASE_URL}/api/v1/products/previews?limit=1&sortType=LATEST`, {
        tags: { name: 'SETUP health', run_label: RUN_LABEL },
    });
    if (health.status !== 200) {
        throw new Error(`상품 API 헬스체크 실패: status=${health.status}, body=${health.body}`);
    }

    console.log('주문 생성 부하 테스트 시작');
    console.log(`BASE_URL=${BASE_URL}`);
    console.log(`LOAD_PROFILE=${LOAD_PROFILE}, RUN_LABEL=${RUN_LABEL}`);
    console.log(`users=${users.length}, skus=${skuDiscovery.skuIds.length}`);

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
        recipientName: `Load Test User ${userId}`,
        recipientPhone: '010-1234-5678',
        shippingAddress: `Seoul Test Address ${userId}`,
        shippingRequest: '문 앞에 놓아주세요',
    });

    const startTime = Date.now();
    const orderRes = http.post(`${BASE_URL}/api/v1/orders`, orderPayload, {
        headers: jsonAuthHeaders(userId),
        tags: { name: 'CreateOrder', run_label: RUN_LABEL },
    });
    orderCreationDuration.add(Date.now() - startTime);

    const success = check(orderRes, {
        '주문 생성 성공 (200/201)': (r) => r.status === 200 || r.status === 201,
        '응답 시간 < 3s': (r) => r.timings.duration < 3000,
        orderNumber_exists: (r) => {
            try {
                const body = r.json();
                return body.orderNumber !== undefined && body.orderNumber !== null;
            } catch (e) {
                return false;
            }
        },
        totalAmount_exists: (r) => {
            try {
                const body = r.json();
                return body.totalAmount !== undefined && body.totalAmount > 0;
            } catch (e) {
                return false;
            }
        },
    });

    if (!success) {
        orderCreationErrors.add(1);
    }

    sleep(2);
}

export function teardown() {
    console.log('주문 생성 부하 테스트 완료');
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
