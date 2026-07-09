package com.ktds.portal.approval.controller;

import com.ktds.portal.approval.domain.Approval;
import com.ktds.portal.approval.dto.ApprovalResponse;
import com.ktds.portal.approval.dto.CreateApprovalRequest;
import com.ktds.portal.approval.dto.ProcessRequest;
import com.ktds.portal.approval.service.ApprovalService;
import org.springframework.web.bind.annotation.*;
import java.util.List;

/**
 * 결재 REST 컨트롤러.
 * [리팩토링] Map&lt;String,Object&gt; 요청 바디 → record DTO(CreateApprovalRequest/ProcessRequest)로 교체,
 * Approval 엔티티 직접 노출 → ApprovalResponse record DTO로 교체(dto 패키지, docs/4-4·4-10 참고).
 * JSON 필드명·구조·정수 status 표현은 레거시와 동일(계약 보존) — 각 record의 클래스 주석에 세부 차이를 남겼다.
 */
@RestController
@RequestMapping("/api/approvals")
public class ApprovalController {

    private final ApprovalService service;

    public ApprovalController(ApprovalService service) {
        this.service = service;
    }

    @PostMapping
    public ApprovalResponse create(@RequestBody CreateApprovalRequest request) {
        Approval approval = service.create(
                request.title(),
                request.content(),
                request.type(),       // type: 1=지출 2=휴가 3=구매 4=기타 — 요청자가 숫자 뜻을 외워야 함
                request.priority(),   // priority: 1=낮음 2=보통 3=높음 — 잘못된 값(5 등)도 그냥 통과
                request.drafterId(),
                request.approverId(),
                request.amount(),
                request.urgent()
        );
        return ApprovalResponse.from(approval);
    }

    // action: 1=상신, 2=승인, 3=반려, 9=취소  ([스멜] 매직넘버를 API 가 그대로 강요)
    @PostMapping("/{id}/process")
    public void process(@PathVariable Long id, @RequestBody ProcessRequest request) {
        service.processApproval(
                id,
                request.userId(),
                request.action(),     // 1=상신 2=승인 3=반려 9=취소 — 요청자(프론트엔드)가 숫자를 외워야 함
                request.reason()
        );
    }

    @GetMapping("/drafts/{userId}")
    public List<ApprovalResponse> drafts(@PathVariable Long userId) {
        return service.myDrafts(userId).stream().map(ApprovalResponse::from).toList();
    }

    @GetMapping("/inbox/{userId}")
    public List<ApprovalResponse> inbox(@PathVariable Long userId) {
        return service.myInbox(userId).stream().map(ApprovalResponse::from).toList();
    }
}
