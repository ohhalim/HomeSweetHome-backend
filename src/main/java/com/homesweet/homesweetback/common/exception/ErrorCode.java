package com.homesweet.homesweetback.common.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

/**
 * 에러 코드 모음
 *
 * @author junnukim1007gmail.com
 * @date 25. 10. 18.
 */
@Getter
@RequiredArgsConstructor
public enum ErrorCode {

    // User
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "해당하는 사용자를 찾을 수 없습니다"),
    GRADE_NOT_FOUND(HttpStatus.NOT_FOUND, "해당하는 등급을 찾을 수 없습니다"),

    // Auth
    REFRESH_TOKEN_NOT_FOUND(HttpStatus.NOT_FOUND, "Refresh Token을 찾을 수 없습니다"),
    INVALID_REFRESH_TOKEN(HttpStatus.UNAUTHORIZED, "유효하지 않은 Refresh Token입니다"),
    INVALID_BIRTH_DATE(HttpStatus.BAD_REQUEST, "생일은 미래 날짜가 될 수 없습니다"),
    INVALID_PHONE_NUMBER_FORMAT(HttpStatus.BAD_REQUEST, "올바른 핸드폰 번호 형식이 아닙니다"),
    // System
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "서버 에러입니다"),
    FILE_STREAM_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "파일 스트림 처리 중 오류가 발생했습니다"),
    MISSING_INPUT_DATA(HttpStatus.BAD_REQUEST, "필수적인 입력값을 전달받지 못했습니다"),

    // S3
    FAILED_UPLOAD_S3_ERROR(HttpStatus.BAD_REQUEST, "S3 저장소에 업로드를 실패했습니다"),
    INVALID_FILE_ERROR(HttpStatus.BAD_REQUEST, "유효하지 않은 파일입니다"),
    CANNOT_FOUND_S3_ERROR(HttpStatus.NOT_FOUND, "S3 저장소에서 해당하는 파일을 찾을 수 없습니다"),

    // Product
    DUPLICATED_CATEGORY_NAME_ERROR(HttpStatus.CONFLICT, "이미 해당하는 카테고리 이름이 존재합니다"),
    CANNOT_FOUND_PARENT_CATEGORY_ERROR(HttpStatus.NOT_FOUND, "부모 카테고리를 찾을 수 없습니다"),
    CATEGORY_DEPTH_EXCEEDED_ERROR(HttpStatus.BAD_REQUEST, "카테고리 기준 깊이를 넘었습니다"),
    DUPLICATED_PRODUCT_NAME_ERROR(HttpStatus.CONFLICT, "동일한 제품명을 사용할 수 없습니다"),
    CANNOT_FOUND_CATEGORY_ERROR(HttpStatus.NOT_FOUND, "해당하는 카테고리를 찾을 수 없습니다"),
    OUT_OF_STOCK(HttpStatus.CONFLICT, "재고가 부족합니다"),
    OUT_OF_OPTION_INDEX(HttpStatus.BAD_REQUEST, "잘못된 옵션 인덱스를 입력했습니다"),
    EXCEEDED_IMAGE_LIMIT_ERROR(HttpStatus.BAD_REQUEST, "상세 이미지는 최대 5장까지 업로드 가능합니다"),

    // Review
    ALREADY_REVIEW_EXISTS(HttpStatus.CONFLICT, "이미 해당 상품에 대한 리뷰를 작성했습니다."),
    PRODUCT_REVIEW_NOT_FOUND_ERROR(HttpStatus.NOT_FOUND, "해당하는 제품 리뷰를 찾을 수 없습니다"),
    SKU_NOT_FOUND_ERROR(HttpStatus.NOT_FOUND, "해당하는 옵션을 찾을 수 없습니다"),
    CART_NOT_FOUND_ERROR(HttpStatus.NOT_FOUND, "해당하는 장바구니를 찾을 수 없습니다"),
    PRODUCT_REVIEW_FORBIDDEN(HttpStatus.BAD_REQUEST, "본인이 작성한 리뷰만 수정할 수 있습니다"),
    CART_LIMIT_EXCEEDED_ERROR(HttpStatus.BAD_REQUEST, "장바구니에는 최대 10개 제품만 담을 수 있습니다"),
    CART_ITEM_TYPE_LIMIT_EXCEEDED_ERROR(HttpStatus.BAD_REQUEST,"장바구니에는 최대 10개 종류의 상품만 담을 수 있습니다."),
    PRODUCT_NOT_FOUND_ERROR(HttpStatus.NOT_FOUND, "해당하는 제품을 찾을 수 없습니다"),
    INVALID_INPUT_VALUE(HttpStatus.BAD_REQUEST, "유효하지 않은 입력값입니다."),

    // Community
    COMMUNITY_POST_NOT_FOUND(HttpStatus.NOT_FOUND, "해당하는 게시글을 찾을 수 없습니다"),
    COMMUNITY_POST_FORBIDDEN(HttpStatus.FORBIDDEN, "본인이 작성한 게시글만 수정/삭제할 수 있습니다"),
    COMMUNITY_COMMENT_NOT_FOUND(HttpStatus.NOT_FOUND, "해당하는 댓글을 찾을 수 없습니다"),
    COMMUNITY_COMMENT_FORBIDDEN(HttpStatus.FORBIDDEN, "본인이 작성한 댓글만 수정/삭제할 수 있습니다"),

    // Notification
    NOTIFICATION_NOT_FOUND(HttpStatus.NOT_FOUND, "알림을 찾을 수 없습니다"),
    NOTIFICATION_ACCESS_DENIED(HttpStatus.FORBIDDEN, "알림에 접근할 권한이 없습니다"),
    NOTIFICATION_CATEGORY_NOT_FOUND(HttpStatus.NOT_FOUND, "알림 카테고리를 찾을 수 없습니다"),
    NOTIFICATION_TEMPLATE_NOT_FOUND(HttpStatus.NOT_FOUND, "알림 템플릿을 찾을 수 없습니다"),
    SSE_CONNECTION_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "SSE 연결 중 오류가 발생했습니다"),
    SSE_CONNECTION_NOT_FOUND(HttpStatus.NOT_FOUND, "SSE 연결을 찾을 수 없습니다"),
    INVALID_NOTIFICATION_CONTEXT(HttpStatus.BAD_REQUEST, "알림 컨텍스트 데이터가 부족합니다"),
    NOTIFICATION_EVENT_TYPE_MISMATCH(HttpStatus.BAD_REQUEST, "알림 이벤트 타입이 일치하지 않습니다"),
    NOTIFICATION_CONTEXT_DATA_IS_NULL(HttpStatus.BAD_REQUEST, "알림 컨텍스트 데이터가 없습니다"),
    NOTIFICATION_USER_ID_IS_NULL(HttpStatus.BAD_REQUEST, "알림 사용자 ID가 없습니다"),

    // Chat
    ROOM_NOT_FOUND(HttpStatus.NOT_FOUND, "채팅방을 찾을 수 없습니다."),
    INVALID_ROOM_TYPE(HttpStatus.BAD_REQUEST, "잘못된 채팅방 타입입니다."),
    ROOM_ACCESS_DENIED(HttpStatus.FORBIDDEN, "참여자를 조회할 수 없습니다."),
    MESSAGE_REJECTED(HttpStatus.FORBIDDEN, "메세지 전송에 실패했습니다."),
    MESSAGE_BAD_REQUEST(HttpStatus.BAD_REQUEST, "잘못된 요청입니다"),
    MESSAGE_INVALID_REQUEST(HttpStatus.BAD_REQUEST,"유효하지 않은 메시지 요청입니다. roomId 또는 senderId가 누락되었습니다."),
    MESSAGE_UNAUTHORIZED_ACCESS(HttpStatus.BAD_REQUEST,"채팅방 접근 권한이 없습니다. 퇴장한 사용자이거나 존재하지 않는 멤버입니다."),
    ROOM_MEMBER_NOT_FOUND(HttpStatus.BAD_REQUEST,  "채팅방 멤버를 찾을 수 없습니다."),
    ALREADY_JOINED_ROOM(HttpStatus.BAD_REQUEST, "이미 입장한 채팅방입니다."),
    NOT_JOINED_ROOM(HttpStatus.BAD_REQUEST, "입장하지 않은 채팅방입니다."),
    MEMBER_ALREADY_ACTIVE(HttpStatus.BAD_REQUEST, "이미 입장 처리된 멤버입니다. "),


    // Token
    TOKEN_INVALID(HttpStatus.UNAUTHORIZED, "유효하지 않은 또는 만료된 토큰입니다. 다시 로그인해주세요."),
    TOKEN_MISSING(HttpStatus.UNAUTHORIZED, "인증 토큰이 누락되었거나 형식이 올바르지 않습니다. 다시 로그인해주세요."),
    TOKEN_REFRESH_NOT_ALLOWED(HttpStatus.FORBIDDEN, "Refresh Token은 이 요청에서 사용할 수 없습니다."),

    // Settlement
    SETTLEMENT_NOT_FOUND(HttpStatus.NOT_FOUND, "조회된 정산 데이터가 없습니다."),
    SETTLEMENT_NOT_CREATED(HttpStatus.BAD_REQUEST, "결제완료 상태만 정산을 생성할 수 있습니다."),
    ORDER_ITEMS_EMPTY(HttpStatus.NOT_FOUND, "제품이 없습니다."),
    SELLER_NOT_FOUND(HttpStatus.NOT_FOUND, "판매자를 찾을 수 없습니다."),
    INVALID_SELLER_ROLE(HttpStatus.FORBIDDEN, "유효한 판매자가 아닙니다."),
    INVALID_SETTLEMENT_STATUS(HttpStatus.BAD_REQUEST, "유효하지 않은 정산 상태입니다."),
    INVALID_DATE_RANGE(HttpStatus.BAD_REQUEST, "시작일은 종료일보다 이후일 수 없습니다."),
    INVALID_ORDER_STATUS(HttpStatus.BAD_REQUEST, "주문 상태가 유효하지 않습니다."),
    ORDERS_NOT_FOUND(HttpStatus.NOT_FOUND, "주문 건이 없습니다."),
    ORDER_CANCELED_NOT_FOUND(HttpStatus.NOT_FOUND, "정산 취소 주문 건이 없습니다."),
    DELIVERY_STATUS_NOT_DELIVERED(HttpStatus.NOT_FOUND, "배송상태가 배송완료가 아닙니다."),
    DUPLICATE_SETTLEMENT(HttpStatus.BAD_REQUEST, "정산은 중복될 수 없습니다."),
    ALREADY_SETTLEMENT_CANCELED(HttpStatus.BAD_REQUEST, "이미 취소된 정산입니다."),
    INVALID_TOTAL_AMOUNT(HttpStatus.BAD_REQUEST, "금액은 음수가 될 수 없습니다."),

    // Subscription
    SUBSCRIPTION_REQUIRED(HttpStatus.FORBIDDEN, "프리미엄 구독이 필요한 기능입니다."),
    SUBSCRIPTION_NOT_FOUND(HttpStatus.NOT_FOUND, "구독 정보를 찾을 수 없습니다."),
    SUBSCRIPTION_ALREADY_EXISTS(HttpStatus.CONFLICT, "이미 구독 중입니다."),
    BILLING_KEY_ISSUE_FAILED(HttpStatus.BAD_REQUEST, "빌링키 발급에 실패했습니다.");


    private final HttpStatus status;
    private final String message;
}
