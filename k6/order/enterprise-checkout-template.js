import {
    options as realisticOptions,
    setup as realisticSetup,
    teardown as realisticTeardown,
    default as realisticDefault,
} from './realistic-checkout-test.js';

/**
 * Enterprise template alias.
 * This script intentionally reuses realistic-checkout-test.js so the runtime
 * output format and thresholds match the validated "realistic" profile.
 */
export const options = realisticOptions;

export function setup() {
    const baseUrl = __ENV.BASE_URL || 'http://localhost:8080';
    if (!__ENV.BASE_URL) {
        console.log(`BASE_URL not provided. using default=${baseUrl}`);
    } else {
        console.log(`BASE_URL from env=${baseUrl}`);
    }
    console.log(`enterprise template: using realistic-checkout-test.js settings`);
    console.log(`CART_ADD_MAX_RETRIES=${__ENV.CART_ADD_MAX_RETRIES || '3'}, CART_ADD_RETRY_DELAY_MS=${__ENV.CART_ADD_RETRY_DELAY_MS || '30'}`);
    return realisticSetup();
}

export default function (data) {
    return realisticDefault(data);
}

export function teardown(data) {
    return realisticTeardown(data);
}
