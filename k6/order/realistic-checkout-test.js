import http from 'k6/http';
import { check, sleep } from 'k6';
import { Counter, Rate, Trend } from 'k6/metrics';
import {
    parseBool,
    parsePositiveInt,
    parseIdCsv,
    parseNonNegativeInt,
    randomFloat,
    randomInt,
    pickRandom,
    jsonAuthHeaders,
    authHeaders,
    discoverUserPool,
    discoverSkuPool,
} from './order-test-data.js';

const browseErrors = new Counter('browse_errors');
const stockViewErrors = new Counter('stock_view_errors');
const cartAddErrors = new Counter('cart_add_errors');
const orderCreateErrors = new Counter('order_create_errors');
const paymentConfirmErrors = new Counter('payment_confirm_errors');

const browseDuration = new Trend('browse_duration', true);
const stockViewDuration = new Trend('stock_view_duration', true);
const cartAddDuration = new Trend('cart_add_duration', true);
const orderCreateDuration = new Trend('order_create_duration', true);
const paymentConfirmDuration = new Trend('payment_confirm_duration', true);

const checkoutSuccessRate = new Rate('checkout_success_rate');
const orderCreateSuccessRate = new Rate('order_create_success_rate');

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
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
const MIN_STOCK = parseNonNegativeInt(__ENV.MIN_STOCK, 0);

const ENABLE_PAYMENT_CONFIRM = parseBool(__ENV.ENABLE_PAYMENT_CONFIRM, false);

const THINK_TIME_MIN = Number(__ENV.THINK_TIME_MIN || '0.2');
const THINK_TIME_MAX = Number(__ENV.THINK_TIME_MAX || '1.0');

const TRAFFIC_WEIGHTS = {
    BROWSE: parsePositiveInt(__ENV.W_BROWSE, 55),
    STOCK_VIEW: parsePositiveInt(__ENV.W_STOCK_VIEW, 20),
    ADD_CART: parsePositiveInt(__ENV.W_ADD_CART, 15),
    CHECKOUT: parsePositiveInt(__ENV.W_CHECKOUT, 10),
};
const TOTAL_WEIGHT = Object.values(TRAFFIC_WEIGHTS).reduce((a, b) => a + b, 0);

export const options = {
    scenarios: {
        checkout_mix: {
            executor: 'ramping-vus',
            startVUs: 1,
            stages: [
                { duration: '30s', target: 20 },
                { duration: '1m', target: 60 },
                { duration: '3m', target: 60 },
                { duration: '30s', target: 0 },
            ],
        },
    },
    thresholds: {
        http_req_failed: ['rate<0.20'],
        http_req_duration: ['p(95)<4000'],
        order_create_duration: ['p(95)<3000'],
        checkout_success_rate: ['rate>0.85'],
        order_create_success_rate: ['rate>0.90'],
    },
};

export function setup() {
    const health = http.get(`${BASE_URL}/api/v1/products/previews?limit=1&sortType=LATEST`, {
        tags: { name: 'SETUP health' },
    });
    if (health.status !== 200) {
        throw new Error(`상품 API 헬스체크 실패: status=${health.status}, body=${health.body}`);
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
        throw new Error(
            '주문 테스트용 userId를 찾지 못했습니다. USER_IDS를 직접 지정하거나 DISCOVER_USERS 범위를 늘리세요.'
        );
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
        throw new Error(
            '주문 테스트용 skuId를 찾지 못했습니다. MIN_STOCK, SKU_IDS, 또는 상품/재고 데이터를 확인하세요.'
        );
    }

    console.log(`BASE_URL=${BASE_URL}`);
    console.log(`users=${users.length}, skus=${skuDiscovery.skuIds.length}, products=${skuDiscovery.productIds.length}`);
    console.log(`ENABLE_PAYMENT_CONFIRM=${ENABLE_PAYMENT_CONFIRM}`);

    return {
        users,
        skuIds: skuDiscovery.skuIds,
        productIds: skuDiscovery.productIds,
        startedAt: new Date().toISOString(),
    };
}

export function teardown(data) {
    console.log(`Order realistic test completed. startedAt=${data.startedAt}`);
}

function pickOperation() {
    const randomWeight = Math.random() * TOTAL_WEIGHT;
    let cumulative = 0;

    cumulative += TRAFFIC_WEIGHTS.BROWSE;
    if (randomWeight < cumulative) return 'BROWSE';

    cumulative += TRAFFIC_WEIGHTS.STOCK_VIEW;
    if (randomWeight < cumulative) return 'STOCK_VIEW';

    cumulative += TRAFFIC_WEIGHTS.ADD_CART;
    if (randomWeight < cumulative) return 'ADD_CART';

    return 'CHECKOUT';
}

function browseProducts() {
    const start = Date.now();
    const response = http.get(`${BASE_URL}/api/v1/products/previews?limit=12&sortType=LATEST`, {
        tags: { name: 'GET /products/previews' },
    });
    browseDuration.add(Date.now() - start);

    const ok = check(response, {
        'browse status is 200': (r) => r.status === 200,
    });
    if (!ok) {
        browseErrors.add(1);
    }

    return ok;
}

function viewStock(data) {
    let productId = null;
    if (data.productIds.length > 0) {
        productId = pickRandom(data.productIds);
    } else {
        const fallback = http.get(`${BASE_URL}/api/v1/products/previews?limit=1&sortType=LATEST`, {
            tags: { name: 'GET /products/previews (fallback)' },
        });
        if (fallback.status === 200) {
            try {
                const body = fallback.json();
                if (Array.isArray(body.contents) && body.contents.length > 0) {
                    productId = Number(body.contents[0].id);
                }
            } catch (e) {
                // ignore
            }
        }
    }

    if (productId === null) {
        stockViewErrors.add(1);
        return false;
    }

    const start = Date.now();
    const response = http.get(`${BASE_URL}/api/v1/products/${productId}/stocks`, {
        tags: { name: 'GET /products/:id/stocks' },
    });
    stockViewDuration.add(Date.now() - start);

    const ok = check(response, {
        'stock view status is 200': (r) => r.status === 200,
    });
    if (!ok) {
        stockViewErrors.add(1);
    }
    return ok;
}

function addToCart(userId, skuId, quantity) {
    const payload = JSON.stringify({
        skuId,
        quantity,
    });

    const start = Date.now();
    const response = http.post(`${BASE_URL}/api/v1/carts`, payload, {
        headers: jsonAuthHeaders(userId),
        tags: { name: 'POST /carts' },
    });
    cartAddDuration.add(Date.now() - start);

    const ok = check(response, {
        'cart add status is 200/201': (r) => r.status === 200 || r.status === 201,
    });

    if (!ok) {
        cartAddErrors.add(1);
        return {
            ok: false,
            cartId: null,
            response,
        };
    }

    let cartId = null;
    try {
        const body = response.json();
        cartId = Number(body.id);
    } catch (e) {
        cartId = null;
    }

    return {
        ok: true,
        cartId,
        response,
    };
}

function createOrder(userId, skuId, quantity, cartId) {
    const orderItem = {
        skuId,
        quantity,
    };
    if (cartId !== null) {
        orderItem.cartId = cartId;
    }

    const payload = JSON.stringify({
        orderItems: [orderItem],
        recipientName: `k6 user ${userId}`,
        recipientPhone: '010-1234-5678',
        shippingAddress: '서울시 테스트로 100',
        shippingRequest: '문 앞에 놓아주세요',
    });

    const start = Date.now();
    const response = http.post(`${BASE_URL}/api/v1/orders`, payload, {
        headers: jsonAuthHeaders(userId),
        tags: { name: 'POST /orders' },
    });
    orderCreateDuration.add(Date.now() - start);

    let hasOrderNumber = false;
    let totalAmount = null;
    let orderNumber = null;

    try {
        const body = response.json();
        orderNumber = body.orderNumber;
        totalAmount = body.totalAmount;
        hasOrderNumber = !!orderNumber;
    } catch (e) {
        hasOrderNumber = false;
    }

    const ok = check(response, {
        'order create status is 200/201': (r) => r.status === 200 || r.status === 201,
        'order response has orderNumber': () => hasOrderNumber,
    });

    orderCreateSuccessRate.add(ok);
    if (!ok) {
        orderCreateErrors.add(1);
    }

    return {
        ok,
        orderNumber,
        totalAmount,
        response,
    };
}

function confirmPayment(userId, orderNumber, amount) {
    if (!ENABLE_PAYMENT_CONFIRM) {
        return {
            ok: true,
            skipped: true,
        };
    }

    const payload = JSON.stringify({
        paymentKey: `k6_mock_${orderNumber}_${Date.now()}`,
        orderId: orderNumber,
        amount,
    });

    const start = Date.now();
    const response = http.post(`${BASE_URL}/api/v1/payments/confirm`, payload, {
        headers: jsonAuthHeaders(userId),
        tags: { name: 'POST /payments/confirm' },
    });
    paymentConfirmDuration.add(Date.now() - start);

    const ok = check(response, {
        'payment confirm status is 200/201': (r) => r.status === 200 || r.status === 201,
    });

    if (!ok) {
        paymentConfirmErrors.add(1);
    }

    return {
        ok,
        skipped: false,
        response,
    };
}

function runCheckout(data, userId) {
    const skuId = pickRandom(data.skuIds);
    const quantity = randomInt(1, 2);

    const cart = addToCart(userId, skuId, quantity);
    if (!cart.ok) {
        checkoutSuccessRate.add(false);
        return;
    }

    const order = createOrder(userId, skuId, quantity, cart.cartId);
    if (!order.ok) {
        checkoutSuccessRate.add(false);
        return;
    }

    const payment = confirmPayment(userId, order.orderNumber, order.totalAmount);
    checkoutSuccessRate.add(payment.ok);
}

export default function (data) {
    const userId = pickRandom(data.users);
    const operation = pickOperation();

    if (operation === 'BROWSE') {
        browseProducts();
    } else if (operation === 'STOCK_VIEW') {
        viewStock(data);
    } else if (operation === 'ADD_CART') {
        const skuId = pickRandom(data.skuIds);
        const quantity = randomInt(1, 2);
        addToCart(userId, skuId, quantity);
    } else {
        runCheckout(data, userId);
    }

    sleep(randomFloat(THINK_TIME_MIN, THINK_TIME_MAX));
}
