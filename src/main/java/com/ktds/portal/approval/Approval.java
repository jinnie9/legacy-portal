package com.ktds.portal.approval;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * 결재 엔티티.
 *
 * [스멜] 빈약한 도메인 모델(Anemic Domain Model) — 데이터만 있고 행위가 없다.
 * [스멜] 원시 타입 집착(Primitive Obsession) — type, priority 는 아직 int (enum 후보로 남아 있음).
 *        type:   1=지출, 2=휴가, 3=구매, 4=기타
 *        priority: 1=낮음, 2=보통, 3=높음
 * [리팩토링] status 는 int → {@link ApprovalStatus} enum 으로 전환했다(Primitive Obsession 제거).
 *        DB 컬럼은 여전히 정수(0/1/2/3/9) 그대로 — {@link ApprovalStatusConverter} 참고.
 * [스멜] 캡슐화 부재 — 모든 필드에 public setter. 누구나 상태를 마음대로 바꿀 수 있다.
 */
@Entity
public class Approval {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String title;
    private String content;
    private int type;       // 1=지출 2=휴가 3=구매 4=기타 (의미를 주석으로만 설명 → enum 후보)
    @Convert(converter = ApprovalStatusConverter.class)
    @Column(nullable = false)
    private ApprovalStatus status;   // DB엔 0/1/2/3/9 정수 그대로 저장(컨버터가 매핑) — 레거시 저장값 불변
    private int priority;   // 1=낮음 2=보통 3=높음
    private Long drafterId;     // 기안자
    private Long approverId;    // 결재자
    private String rejectReason;
    private long amount;        // 지출/구매 금액
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public int getType() { return type; }
    public void setType(int type) { this.type = type; }
    public ApprovalStatus getStatus() { return status; }
    public void setStatus(ApprovalStatus status) { this.status = status; }
    public int getPriority() { return priority; }
    public void setPriority(int priority) { this.priority = priority; }
    public Long getDrafterId() { return drafterId; }
    public void setDrafterId(Long drafterId) { this.drafterId = drafterId; }
    public Long getApproverId() { return approverId; }
    public void setApproverId(Long approverId) { this.approverId = approverId; }
    public String getRejectReason() { return rejectReason; }
    public void setRejectReason(String rejectReason) { this.rejectReason = rejectReason; }
    public long getAmount() { return amount; }
    public void setAmount(long amount) { this.amount = amount; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
