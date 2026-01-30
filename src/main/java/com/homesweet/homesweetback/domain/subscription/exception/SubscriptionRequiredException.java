package com.homesweet.homesweetback.domain.subscription.exception;

import com.homesweet.homesweetback.common.exception.ErrorCode;
import lombok.Getter;

/**
 * 구독이 필요한 기능에 비구독자가 접근할 때 발생하는 예외
 */
@Getter
public class SubscriptionRequiredException extends RuntimeException {

    private final ErrorCode errorCode;

    public SubscriptionRequiredException() {
        super("프리미엄 구독이 필요한 기능입니다.");
        this.errorCode = ErrorCode.SUBSCRIPTION_REQUIRED;
    }

    public SubscriptionRequiredException(String message) {
        super(message);
        this.errorCode = ErrorCode.SUBSCRIPTION_REQUIRED;
    }
}
