package com.ktds.portal.approval.domain;

import com.fasterxml.jackson.annotation.JsonValue;

/**
 * [리팩토링] 레거시 Approval.status(int: 0/1/2/3/9) → enum으로 전환 (Primitive Obsession 제거).
 *
 * - DB 저장값은 기존 정수 그대로 유지한다 — {@link ApprovalStatusConverter} 참고(불변 규칙: DB 저장값 변경 금지).
 * - API 응답도 기존과 동일하게 정수로 나가야 하므로 {@link #getCode()}에 {@code @JsonValue}를 붙였다.
 *   이게 없으면 Jackson이 기본값(enum 이름 문자열, 예: "APPROVED")으로 직렬화해 응답 형식이 바뀐다(불변 규칙 위반).
 * - {@link #label()}이 기존 ApprovalService.statusLabel()의 5분기 if-else와 tmp 변수를 대체한다.
 */
public enum ApprovalStatus {
    DRAFT(0, "임시저장"),
    SUBMITTED(1, "상신"),
    APPROVED(2, "승인"),
    REJECTED(3, "반려"),
    CANCELED(9, "취소");

    private final int code;
    private final String label;

    ApprovalStatus(int code, String label) {
        this.code = code;
        this.label = label;
    }

    @JsonValue
    public int getCode() {
        return code;
    }

    public String label() {
        return label;
    }

    public static ApprovalStatus fromCode(int code) {
        for (ApprovalStatus status : values()) {
            if (status.code == code) {
                return status;
            }
        }
        throw new IllegalArgumentException("알 수 없는 Approval status 코드: " + code);
    }
}
