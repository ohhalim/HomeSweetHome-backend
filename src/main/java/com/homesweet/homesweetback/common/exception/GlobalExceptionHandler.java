package com.homesweet.homesweetback.common.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.bind.MethodArgumentNotValidException;

/**
 * 공통 예외 처리
 *
 * @author junnukim1007gmail.com
 * @date 25. 10. 18.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    // 비지니스 예외
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResponse> handleBusiness(BusinessException ex) {
        log.error("[Business Exception]: {}", ex.getMessage(), ex);
        ErrorCode errorCode = ex.getErrorCode();
        ErrorResponse errorResponse = ErrorResponse.of(errorCode.getStatus(), errorCode.getMessage());

        return ResponseEntity.status(errorCode.getStatus()).body(errorResponse);
    }

    // 잘못된 요청 값
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgument(IllegalArgumentException ex) {
        log.warn("[Bad Request]: {}", ex.getMessage(), ex);
        ErrorResponse errorResponse = ErrorResponse.of(HttpStatus.BAD_REQUEST, ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse);
    }

    // 잘못된 비즈니스 상태(중복 처리 등)
    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ErrorResponse> handleIllegalState(IllegalStateException ex) {
        log.warn("[Conflict]: {}", ex.getMessage(), ex);
        ErrorResponse errorResponse = ErrorResponse.of(HttpStatus.CONFLICT, ex.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(errorResponse);
    }

    // Validation 실패
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex) {
        log.warn("[Validation Failed]: {}", ex.getMessage(), ex);
        ErrorResponse errorResponse = ErrorResponse.of(HttpStatus.BAD_REQUEST, "입력값이 올바르지 않습니다.");
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse);
    }

    // 그 외 모든 예외
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleException(Exception ex) {
        log.error("[Exception]: {}", ex.getMessage(), ex);
        ErrorCode errorCode = ErrorCode.INTERNAL_SERVER_ERROR;
        ErrorResponse errorResponse = ErrorResponse.of(errorCode.getStatus(), errorCode.getMessage());

        return ResponseEntity.status(errorCode.getStatus()).body(errorResponse);
    }

    @ExceptionHandler(StockInsufficientException.class)
    public ResponseEntity<ErrorResponse> handleStockInsufficientException(StockInsufficientException ex) {
        log.warn("[Stock Insufficient]: {}", ex.getMessage(), ex);
        ErrorResponse errorResponse = ErrorResponse.of(HttpStatus.BAD_REQUEST, ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse);
    }

    // Toss API 예외 처리 핸들러
    @ExceptionHandler(TossApiFailedException.class)
    public ResponseEntity<String> handleTossApiFailedException(TossApiFailedException e) {
        log.error("PG사 연동 오류 발생: {}", e.getMessage());

        // 502 Bad Gateway: "내 잘못이 아니라, 내가 연결한 저쪽(Gateway) 서버가 잘못했다"는 의미
        return ResponseEntity
                .status(HttpStatus.BAD_GATEWAY)
                .body(e.getMessage()); // 또는 "결제 시스템이 지연되고 있습니다." 같은 친절한 메시지
    }
}
