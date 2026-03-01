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
    return realisticSetup();
}

export default function (data) {
    return realisticDefault(data);
}

export function teardown(data) {
    return realisticTeardown(data);
}
