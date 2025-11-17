import http from 'k6/http';
import { check } from 'k6';
import { config, getHeaders } from './config.js';

/**
 * 사용자 로그인 (실제 환경에 맞게 수정 필요)
 *
 * Note: 이 프로젝트는 OAuth2 Google 로그인을 사용하므로,
 * 실제 성능 테스트 시에는 다음 중 하나를 사용해야 합니다:
 * 1. 테스트용 JWT 토큰을 미리 발급받아 환경변수로 전달
 * 2. 테스트 전용 로그인 엔드포인트 구현
 * 3. Mock 서버를 통한 인증 우회
 */
export function login(email, password) {
  // 환경변수에서 테스트 토큰을 가져옴
  const testToken = __ENV.TEST_TOKEN;

  if (testToken) {
    return testToken;
  }

  // 실제 로그인 엔드포인트가 있다면 사용
  const url = `${config.baseURL}${config.endpoints.login}`;
  const payload = JSON.stringify({
    email: email,
    password: password,
  });
  const params = {
    headers: getHeaders(),
    tags: { name: 'Login' },
  };

  const response = http.post(url, payload, params);

  const success = check(response, {
    'login: status is 200': (r) => r.status === 200,
    'login: has token': (r) => {
      try {
        const body = JSON.parse(r.body);
        return body.token !== undefined || body.accessToken !== undefined;
      } catch (e) {
        return false;
      }
    },
  });

  if (success) {
    try {
      const body = JSON.parse(response.body);
      return body.token || body.accessToken || null;
    } catch (e) {
      return null;
    }
  }

  return null;
}

/**
 * 테스트용 토큰 생성 (개발 환경 전용)
 * 실제 프로덕션에서는 사용하지 마세요!
 */
export function getMockToken(userId = 1) {
  // JWT 구조를 흉내낸 Mock 토큰
  // 실제로는 백엔드에서 발급받은 유효한 토큰을 사용해야 합니다
  const header = btoa(JSON.stringify({ alg: 'HS256', typ: 'JWT' }));
  const payload = btoa(JSON.stringify({
    sub: userId.toString(),
    email: `test${userId}@example.com`,
    iat: Math.floor(Date.now() / 1000),
    exp: Math.floor(Date.now() / 1000) + 3600,
  }));
  const signature = 'mock-signature-for-testing';

  return `${header}.${payload}.${signature}`;
}

/**
 * 환경변수 또는 Mock 토큰 가져오기
 */
export function getAuthToken(userId = 1) {
  // 환경변수에 토큰이 있으면 그것을 사용
  const envToken = __ENV.TEST_TOKEN || __ENV.AUTH_TOKEN;
  if (envToken) {
    return envToken;
  }

  // 없으면 Mock 토큰 생성
  return getMockToken(userId);
}

/**
 * 여러 사용자의 토큰을 미리 준비
 */
export function setupTestUsers(count = 10) {
  const tokens = [];
  for (let i = 1; i <= count; i++) {
    tokens.push({
      userId: i,
      token: getAuthToken(i),
    });
  }
  return tokens;
}
