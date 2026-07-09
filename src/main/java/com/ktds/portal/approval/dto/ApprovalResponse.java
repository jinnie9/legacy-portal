package com.ktds.portal.approval.dto;

import com.ktds.portal.approval.domain.Approval;

import java.time.LocalDateTime;

/**
 * [리팩토링] Approval 엔티티를 API 응답에 직접 노출하던 것을 대체(Entity 비노출).
 * JSON 필드명·타입은 레거시(Approval 엔티티 직접 직렬화)와 완전히 동일하게 유지한다 — 특히
 * {@code status}는 ApprovalStatus enum이 아니라 정수(getCode())로 내보내서, 이 DTO를 도입하기 전과
 * 응답 바이트 하나까지 동일하다(계약 보존).
 */
public record ApprovalResponse(
        Long id,
        String title,
        String content,
        int type,
        int status,
        int priority,
        Long drafterId,
        Long approverId,
        String rejectReason,
        long amount,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {

    public static ApprovalResponse from(Approval approval) {
        return new ApprovalResponse(
                approval.getId(),
                approval.getTitle(),
                approval.getContent(),
                approval.getType(),
                approval.getStatus().getCode(),
                approval.getPriority(),
                approval.getDrafterId(),
                approval.getApproverId(),
                approval.getRejectReason(),
                approval.getAmount(),
                approval.getCreatedAt(),
                approval.getUpdatedAt()
        );
    }
}
