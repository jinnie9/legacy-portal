package com.ktds.portal.approval.domain;

/**
 * [리팩토링] processApproval()의 action 파라미터(int: 1/2/3/9) 매직넘버를 enum으로 전환.
 *
 * 공개 메서드 시그니처는 불변 규칙에 따라 그대로 int로 유지한다
 * (processApproval(id, userId, action, reason) — 계약 변경 금지). 메서드 내부에서만
 * {@link #fromCode(int)}로 변환해 비교한다.
 *
 * {@link ApprovalStatus}와 코드값이 우연히 겹치지만(SUBMIT=1↔SUBMITTED=1, APPROVE=2↔APPROVED=2 등)
 * "요청 액션"과 "결과 상태"는 서로 다른 개념이라 하나의 enum으로 합치지 않고 분리했다(docs/4-8 참고).
 */
public enum ApprovalAction {
    SUBMIT(1),
    APPROVE(2),
    REJECT(3),
    CANCEL(9);

    private final int code;

    ApprovalAction(int code) {
        this.code = code;
    }

    public int getCode() {
        return code;
    }

    /**
     * 알 수 없는 코드는 {@code null}을 반환한다(예외 아님) — 레거시가 알 수 없는 action을
     * 조용히 무시하던 동작(보존 대상)을 그대로 유지하기 위함이다.
     */
    public static ApprovalAction fromCode(int code) {
        for (ApprovalAction action : values()) {
            if (action.code == code) {
                return action;
            }
        }
        return null;
    }
}
