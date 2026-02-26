import http from 'k6/http';

export function parseBool(value, defaultValue) {
    if (value === undefined) {
        return defaultValue;
    }
    return String(value).toLowerCase() === 'true';
}

export function parsePositiveInt(value, defaultValue) {
    const parsed = parseInt(value || String(defaultValue), 10);
    if (Number.isNaN(parsed) || parsed <= 0) {
        return defaultValue;
    }
    return parsed;
}

export function parseNonNegativeInt(value, defaultValue) {
    const parsed = parseInt(value ?? String(defaultValue), 10);
    if (Number.isNaN(parsed) || parsed < 0) {
        return defaultValue;
    }
    return parsed;
}

export function parseIdCsv(value) {
    if (!value) {
        return [];
    }
    return value
        .split(',')
        .map((v) => Number(String(v).trim()))
        .filter((n) => Number.isInteger(n) && n > 0);
}

export function dedupeNumericArray(values) {
    const uniq = [];
    const seen = {};
    for (let i = 0; i < values.length; i += 1) {
        const v = values[i];
        if (!seen[v]) {
            seen[v] = true;
            uniq.push(v);
        }
    }
    return uniq;
}

export function mergeUnique(base, extras) {
    const seen = {};
    for (let i = 0; i < base.length; i += 1) {
        seen[base[i]] = true;
    }
    for (let i = 0; i < extras.length; i += 1) {
        const v = extras[i];
        if (!seen[v]) {
            seen[v] = true;
            base.push(v);
        }
    }
}

export function randomInt(min, max) {
    if (max <= min) {
        return min;
    }
    return Math.floor(Math.random() * (max - min + 1)) + min;
}

export function pickRandom(items) {
    return items[randomInt(0, items.length - 1)];
}

export function randomFloat(min, max) {
    if (max <= min) {
        return min;
    }
    return Math.random() * (max - min) + min;
}

export function authHeaders(userId) {
    return {
        Authorization: `Bearer ${userId}`,
    };
}

export function jsonAuthHeaders(userId) {
    return {
        'Content-Type': 'application/json',
        Authorization: `Bearer ${userId}`,
    };
}

function probeAuth(baseUrl, path, userId) {
    const res = http.get(`${baseUrl}${path}`, {
        headers: authHeaders(userId),
        tags: { name: 'SETUP auth probe' },
    });
    return res.status === 200;
}

export function discoverUserPool(config) {
    const {
        baseUrl,
        authProbePath,
        candidateUserIds = [],
        scanStart = 1,
        scanEnd = 300,
        minUsers = 5,
        discoverUsers = true,
    } = config;

    const users = dedupeNumericArray(candidateUserIds.slice());
    const validatedUsers = [];

    for (let i = 0; i < users.length; i += 1) {
        if (probeAuth(baseUrl, authProbePath, users[i])) {
            validatedUsers.push(users[i]);
        }
    }

    if (discoverUsers && validatedUsers.length < minUsers) {
        for (let userId = scanStart; userId <= scanEnd; userId += 1) {
            if (validatedUsers.length >= minUsers) {
                break;
            }
            if (validatedUsers.indexOf(userId) !== -1) {
                continue;
            }
            if (probeAuth(baseUrl, authProbePath, userId)) {
                validatedUsers.push(userId);
            }
        }
    }

    return dedupeNumericArray(validatedUsers);
}

function discoverProductIds(baseUrl, pages, limit) {
    const productIds = [];
    let cursorId = null;

    for (let page = 0; page < pages; page += 1) {
        let url = `${baseUrl}/api/v1/products/previews?limit=${limit}&sortType=LATEST`;
        if (cursorId !== null) {
            url += `&cursorId=${cursorId}`;
        }

        const res = http.get(url, { tags: { name: 'SETUP discover products' } });
        if (res.status !== 200) {
            break;
        }

        let payload;
        try {
            payload = res.json();
        } catch (e) {
            break;
        }

        const items = Array.isArray(payload.contents)
            ? payload.contents
            : Array.isArray(payload.content)
                ? payload.content
                : [];
        if (items.length === 0) {
            break;
        }

        for (let i = 0; i < items.length; i += 1) {
            const id = Number(items[i].id);
            if (Number.isInteger(id) && id > 0) {
                productIds.push(id);
            }
        }

        const nextCursor = payload.nextCursorId ?? payload.nextCursor ?? null;
        if (!payload.hasNext || nextCursor === null || nextCursor === undefined) {
            break;
        }
        cursorId = nextCursor;
    }

    return dedupeNumericArray(productIds);
}

function discoverSkusFromProducts(baseUrl, productIds, maxProductsToScan, minStock) {
    const skuIds = [];
    const limit = Math.min(productIds.length, maxProductsToScan);

    for (let i = 0; i < limit; i += 1) {
        const productId = productIds[i];
        const res = http.get(`${baseUrl}/api/v1/products/${productId}/stocks`, {
            tags: { name: 'SETUP discover skus' },
        });

        if (res.status !== 200) {
            continue;
        }

        let stocks;
        try {
            stocks = res.json();
        } catch (e) {
            continue;
        }

        if (!Array.isArray(stocks)) {
            continue;
        }

        for (let j = 0; j < stocks.length; j += 1) {
            const skuId = Number(stocks[j].skuId);
            const stockQty = Number(stocks[j].stockQuantity);
            if (Number.isInteger(skuId) && skuId > 0 && Number.isFinite(stockQty) && stockQty >= minStock) {
                skuIds.push(skuId);
            }
        }
    }

    return dedupeNumericArray(skuIds);
}

export function discoverSkuPool(config) {
    const {
        baseUrl,
        candidateSkuIds = [],
        discoverSkus = true,
        productDiscoveryPages = 5,
        productDiscoveryLimit = 24,
        maxProductsToScan = 60,
        minSkuPool = 10,
        minStock = 1,
    } = config;

    const skuIds = dedupeNumericArray(candidateSkuIds.slice());
    let productIds = [];

    if (discoverSkus && skuIds.length < minSkuPool) {
        productIds = discoverProductIds(baseUrl, productDiscoveryPages, productDiscoveryLimit);
        const discoveredSkus = discoverSkusFromProducts(baseUrl, productIds, maxProductsToScan, minStock);
        mergeUnique(skuIds, discoveredSkus);
    }

    return {
        skuIds: dedupeNumericArray(skuIds),
        productIds: dedupeNumericArray(productIds),
    };
}
