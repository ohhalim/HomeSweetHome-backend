package com.homesweet.homesweetback.common.exception;

/**
 * Toss 인증/연동 키 문제(401 등) 전용 예외
 */
public class TossApiAuthenticationException extends TossApiFailedException {

    public TossApiAuthenticationException(String message) {
        super(message);
    }

    public TossApiAuthenticationException(String message, Throwable cause) {
        super(message, cause);
    }
}
