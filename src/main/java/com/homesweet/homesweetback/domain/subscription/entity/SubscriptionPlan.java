package com.homesweet.homesweetback.domain.subscription.entity;

/**
 * 구독 플랜 타입
 */
public enum SubscriptionPlan {
    PREMIUM_MONTHLY(9900L, "프리미엄 월간 구독", 30);

    private final Long price;
    private final String displayName;
    private final int durationDays;

    SubscriptionPlan(Long price, String displayName, int durationDays) {
        this.price = price;
        this.displayName = displayName;
        this.durationDays = durationDays;
    }

    public Long getPrice() {
        return price;
    }

    public String getDisplayName() {
        return displayName;
    }

    public int getDurationDays() {
        return durationDays;
    }
}
