package com.homesweet.homesweetback.common.exception;

/**
 * Toss 4xx 요청 오류 전용 예외
 */
public class TossApiClientException extends TossApiFailedException {

    public TossApiClientException(String message) {
        super(message);
    }

    public TossApiClientException(String message, Throwable cause) {
        super(message, cause);
    }
}
