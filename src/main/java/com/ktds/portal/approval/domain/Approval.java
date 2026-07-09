package com.ktds.portal.approval.domain;

import com.ktds.portal.user.User;
import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * 결재 엔티티.
 *
 * [스멜] 빈약한 도메인 모델(Anemic Domain Model) — (부분 해소) 상태 전이·권한 판정은 아래
 * {@link #submit()}/{@link #approve(User, Long)}/{@link #reject(User, Long, String)}/{@link #cancel(Long)}로
 * 옮겨왔다(원래 ApprovalService의 if-지옥이었던 부분). 위반 시 예외 대신 {@code false}를 반환해
 * "조용히 무시"하던 레거시 동작을 그대로 보존한다 — 호출자(ApprovalService)가 반환값을 보고
 * 저장·메일·감사로그 여부를 결정한다.
 * [스멜] 원시 타입 집착(Primitive Obsession) — type, priority 는 아직 int (enum 후보로 남아 있음).
 *        type:   1=지출, 2=휴가, 3=구매, 4=기타
 *        priority: 1=낮음, 2=보통, 3=높음
 * [리팩토링] status 는 int → {@link ApprovalStatus} enum 으로 전환했다(Primitive Obsession 제거).
 *        DB 컬럼은 여전히 정수(0/1/2/3/9) 그대로 — {@link ApprovalStatusConverter} 참고.
 * [스멜] 캡슐화 부재 — 모든 필드에 public setter는 남아 있다(레거시 create() 등이 setter로 초기화).
 */
@Entity
public class Approval {

    // [리팩토링] ApprovalService에 있던 매직넘버(type==1, amount>=1000000, priority=3)를
    // submit()이 여기로 옮겨오면서 함께 이동했다. HIGH_PRIORITY는 ApprovalService.create()의
    // urgent 플래그 처리에서도 같은 값을 써야 해서 public으로 열어둔다(단일 출처 유지).
    private static final int EXPENSE_TYPE = 1;                        // type: 1=지출
    private static final long HIGH_PRIORITY_AMOUNT_THRESHOLD = 1_000_000L;
    public static final int HIGH_PRIORITY = 3;                        // priority: 3=높음

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

    /**
     * 상신: 임시저장일 때만 가능. 지출 + 100만원 이상이면 우선순위를 자동으로 높음으로 올린다.
     * 레거시와 동일하게 기안자 본인 확인은 하지 않는다(원래 submit 분기에 없던 검증이라 추가하지 않음).
     * @return 전이 성공 여부. false면 아무것도 바뀌지 않는다(호출자는 조용히 무시하면 됨).
     */
    public boolean submit() {
        if (status != ApprovalStatus.DRAFT) {
            return false;
        }
        if (type == EXPENSE_TYPE && amount >= HIGH_PRIORITY_AMOUNT_THRESHOLD) {
            priority = HIGH_PRIORITY;
        }
        status = ApprovalStatus.SUBMITTED;
        updatedAt = LocalDateTime.now();
        return true;
    }

    /**
     * 승인: 상신 상태 + 본인이 결재자 + 권한(팀장 이상)일 때만 가능.
     * @return 전이 성공 여부. false면 아무것도 바뀌지 않는다.
     */
    public boolean approve(User actor, Long userId) {
        if (status != ApprovalStatus.SUBMITTED) {
            return false;
        }
        if (approverId == null || !approverId.equals(userId)) {
            return false;
        }
        if (!actor.isManagerOrAbove()) {
            return false;
        }
        status = ApprovalStatus.APPROVED;
        updatedAt = LocalDateTime.now();
        return true;
    }

    /**
     * 반려: 상신 상태 + 본인이 결재자 + 권한(팀장 이상)일 때만 가능(승인과 조건 동일 — docs/4-5 중복 코드).
     * @return 전이 성공 여부. false면 아무것도 바뀌지 않는다(rejectReason도 저장 안 됨).
     */
    public boolean reject(User actor, Long userId, String reason) {
        if (status != ApprovalStatus.SUBMITTED) {
            return false;
        }
        if (approverId == null || !approverId.equals(userId)) {
            return false;
        }
        if (!actor.isManagerOrAbove()) {
            return false;
        }
        status = ApprovalStatus.REJECTED;
        rejectReason = reason;
        updatedAt = LocalDateTime.now();
        return true;
    }

    /**
     * 취소: 기안자 본인 + 아직 승인 전(임시저장 또는 상신)일 때만 가능.
     * @return 전이 성공 여부. false면 아무것도 바뀌지 않는다.
     */
    public boolean cancel(Long userId) {
        if (status != ApprovalStatus.DRAFT && status != ApprovalStatus.SUBMITTED) {
            return false;
        }
        if (drafterId == null || !drafterId.equals(userId)) {
            return false;
        }
        status = ApprovalStatus.CANCELED;
        updatedAt = LocalDateTime.now();
        return true;
    }
}
