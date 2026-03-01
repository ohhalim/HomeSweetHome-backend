package com.homesweet.homesweetback.domain.order.entity;

/**
 * 결제 상태 (토스페이먼츠 API 기준)
 */
public enum PaymentStatus {
    READY, // 결제 준비
    IN_PROGRESS, // 결제 진행 중
    WAITING_FOR_DEPOSIT, // 가상계좌 입금 대기
    DONE, // 결제 완료
    CANCEL_REQUESTED, // 취소 요청됨 (외부 PG/내부 DB 정합성 보강용)
    CANCEL_FAILED, // 취소 처리 실패 (재시도 대상)
    CANCELLED, // 결제 취소
    PARTIAL_CANCELED, // 부분 취소
    ABORTED, // 결제 승인 실패
    EXPIRED // 결제 만료 (가상계좌 입금 기한 초과)
}
