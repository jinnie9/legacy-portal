package com.ktds.portal.approval.dto;

/**
 * [리팩토링] ApprovalController.process()가 받던 Map&lt;String,Object&gt; 요청 바디를 대체.
 * JSON 필드명은 레거시와 동일: userId/action/reason.
 *
 * - {@code action}은 일부러 박싱 타입(Integer)으로 뒀다 — 레거시가 {@code (int) body.get("action")}으로
 *   캐스팅해서 키가 없으면 NPE로 터지던 것과 동일하게, {@code ApprovalService.processApproval(..., int action, ...)}
 *   호출부에서 언박싱될 때 값이 없으면 NPE가 나서 실패 방식이 최대한 비슷하게 유지된다.
 * - {@code reason}은 레거시의 {@code body.getOrDefault("reason", "")}와 동일하게, JSON에 키가 없거나
 *   null이면 빈 문자열로 정규화한다(아래 compact 생성자).
 * - {@code userId}는 레거시도 {@code Long}으로 전달되던 값이라, 레거시의 "누락 시 .longValue()에서 NPE"
 *   타이밍까지는 재현하지 못한다(값이 없으면 조용히 null로 전달됨) — CreateApprovalRequest의
 *   drafterId/approverId와 같은 이유(공개 시그니처가 이미 Long을 받음)로 생기는 차이다.
 */
public record ProcessRequest(Long userId, Integer action, String reason) {

    public ProcessRequest {
        if (reason == null) {
            reason = "";
        }
    }
}
