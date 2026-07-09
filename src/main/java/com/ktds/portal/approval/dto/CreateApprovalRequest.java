package com.ktds.portal.approval.dto;

/**
 * [리팩토링] ApprovalController.create()가 받던 Map&lt;String,Object&gt; 요청 바디를 대체.
 * JSON 필드명은 레거시(Map 키)와 완전히 동일: title/content/type/priority/drafterId/approverId/amount/urgent.
 *
 * - {@code amount}/{@code urgent}는 기본형(long/boolean)이라, 레거시의
 *   {@code body.getOrDefault("amount", 0)}/{@code body.getOrDefault("urgent", false)}와 동일하게
 *   JSON에 키가 없으면 0/false로 채워진다.
 * - {@code type}/{@code priority}는 일부러 박싱 타입(Integer)으로 뒀다 — 레거시가
 *   {@code (int) body.get("type")}처럼 캐스팅해서 키가 없으면 NPE로 터지던 것과 동일하게,
 *   여기서도 값이 없으면 null이 되고 ApprovalController가 {@code ApprovalService.create(int, ...)}에
 *   그대로 넘길 때 언박싱 시점에 NPE가 나서 관찰 가능한 실패 방식이 최대한 비슷하게 유지된다.
 * - {@code drafterId}/{@code approverId}는 레거시도 {@code Long}으로 전달되던 값이라 이 부분만큼은
 *   레거시의 "누락 시 .longValue()에서 NPE" 타이밍을 그대로 재현하지 못한다(값이 없으면 조용히 null로
 *   전달됨) — ApprovalService.create()의 공개 시그니처가 이미 Long(박싱)을 받고 있어 이 차이는
 *   DTO 도입으로 어쩔 수 없이 생기는 부분이다.
 */
public record CreateApprovalRequest(
        String title,
        String content,
        Integer type,
        Integer priority,
        Long drafterId,
        Long approverId,
        long amount,
        boolean urgent
) {
}
