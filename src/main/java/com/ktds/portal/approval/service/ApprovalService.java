package com.ktds.portal.approval.service;

import com.ktds.portal.approval.domain.Approval;
import com.ktds.portal.approval.domain.ApprovalAction;
import com.ktds.portal.approval.domain.ApprovalStatus;
import com.ktds.portal.approval.repository.ApprovalRepository;
import com.ktds.portal.common.AuditLogger;
import com.ktds.portal.common.MailSender;
import com.ktds.portal.user.User;
import com.ktds.portal.user.UserRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 결재 서비스 — 이 클래스가 이 과정의 "주인공 안티패턴"이다.
 *
 * ============================ 의도적으로 심어둔 스멜 목록 (원본 기준) ============================
 *  1. God Class            : (대폭 축소) 상태 전이·권한 판정은 Approval 도메인 메서드로, 메일/감사로그는
 *                            인터페이스로, 표현/등급 계산은 제거로 빠져나갔다 — 이제 남은 건 오케스트레이션
 *                            (조회→도메인 호출→저장/알림/로그)과 create() 뿐이다. God Class 완전 해소는
 *                            아니지만(여전히 여러 협력자를 조합) 훨씬 얇아졌다.
 *  2. Long Method          : (해결) processApproval() 86줄·중첩 6단계 → submit/approve/reject/cancel
 *                            private 메서드로 분해, 상태·권한 검증 자체는 Approval 도메인 메서드로 이동.
 *  3. Magic Number         : Approval.type/priority 필드 자체는 아직 int(enum 후보로 남음, 아래 참고).
 *                            status/action/role>=2/금액 임계값 매직넘버는 enum·상수·도메인 메서드로 정리 완료.
 *  4. Duplicated Code      : (감사 로그 부분 해결) 타임스탬프 포맷팅·문자열 조립은 AuditLogger로 이동(docs/4-21).
 *                            메일 본문 생성은 여전히 메서드마다 있다(submit/approvedBody/rejectedBody, 유사 중복).
 *  5. Tight Coupling       : (해결) MailSender/AuditLogger 인터페이스 + 생성자 주입으로 전환.
 *                            new ConsoleMailSender()/new ConsoleAuditLogger() 직접 생성 제거.
 *  6. Feature Envy         : (해결) amountGrade()는 AmountGrade enum으로, submit/approve/reject/cancel의
 *                            상태·권한 판정은 Approval 도메인 메서드로 이동 — 서비스가 더 이상 Approval의
 *                            필드를 꺼내 직접 규칙을 계산하지 않는다.
 *  7. Primitive Obsession  : Approval.type/priority 는 여전히 int 비교로 처리한다.
 *  8. Long Parameter List  : create() 파라미터 8개.
 *  9. Poor Naming          : (해결) d/u/s/proc/tmp 약어를 의미 있는 이름으로 교체.
 * 10. Comment Smell        : (해결) 나쁜 이름을 변명하던 주석 제거.
 * 11. No Tests             : (해결) ApprovalServiceCharacterizationTest 6개로 안전망 확보.
 * ===============================================================================================
 *
 * [리팩토링] status: int(0/1/2/3/9) → {@link ApprovalStatus} enum.
 *   - DB 저장값은 그대로 정수(컨버터가 매핑) — 관찰 가능한 동작 불변.
 *   - API 응답도 그대로 정수(enum에 @JsonValue) — 요청/응답 형식 불변.
 * [리팩토링] Move Method — 표현/도메인 규칙을 서비스 밖으로 완전히 이동(호출부가 없어 위임 래퍼도 삭제):
 *   - statusLabel(Approval): 5분기 if-else + tmp 변수를 제거하고 {@link ApprovalStatus#label()}로 이동.
 *   - amountGrade(Approval): 금액 등급 if-else를 제거하고 {@link com.ktds.portal.approval.domain.AmountGrade#forAmount(long)}로 이동.
 * [리팩토링] 이름 개선: d→approval, u→actor, s(지역변수)→status, proc(action을 다시 담던 임시변수) 제거.
 * [리팩토링] action: int(1/2/3/9) → {@link ApprovalAction} enum으로 변환해 비교(공개 시그니처는 int 그대로 유지 —
 *   계약 변경 금지). 알 수 없는 action 코드는 fromCode()가 null을 반환해 조용히 무시되는 레거시 동작 보존.
 * [리팩토링] 상태 전이·권한 판정을 Approval 도메인 메서드로 이동: submit()/approve(User,Long)/
 *   reject(User,Long,String)/cancel(Long)이 각각 Approval 엔티티에 있다. 위반 시 예외 대신
 *   {@code false}를 반환해 "조건 불충족 시 조용히 무시"하던 레거시 동작을 그대로 보존한다.
 *   서비스의 submit/approve/reject/cancel private 메서드는 이제 "도메인 메서드 호출 → 성공하면
 *   저장·메일·감사로그" 오케스트레이션만 담당한다(EXPENSE_TYPE 등 금액 임계값 상수도 Approval로 이동).
 * [리팩토링] 메일/감사로그 강결합 해소: mail/audit 필드 타입을 {@link MailSender}/{@link AuditLogger}
 *   인터페이스로 바꾸고 생성자 주입으로 전환(직접 new 제거).
 * [리팩토링] Move Method — {@link AuditLogger#write}의 시그니처를 write(line: String)에서
 *   write(action, id, userId)로 좁히고, 타임스탬프 포맷팅("yyyy-MM-dd HH:mm:ss")과 "[시각] ACTION id=X by=Y"
 *   문자열 조립 자체를 {@link com.ktds.portal.common.ConsoleAuditLogger}로 옮겼다(docs/4-21 "동일 중복" #1).
 *   서비스 안에서 직접 포맷을 조립하던 private writeAudit()도 더 이상 필요 없어 삭제 — 호출부는
 *   audit.write(action, id, userId)만 호출한다. create()의 "type=" 상세는 action 문자열에 실어 보존했다
 *   (콘솔 로그 필드 순서만 뒤로 이동, id/by 값과 정보량은 동일 — DB/API에는 영향 없음).
 */
@Service
public class ApprovalService {

    private final ApprovalRepository repo;
    private final UserRepository userRepo;
    private final MailSender mail;
    private final AuditLogger audit;

    public ApprovalService(ApprovalRepository repo, UserRepository userRepo, MailSender mail, AuditLogger audit) {
        this.repo = repo;
        this.userRepo = userRepo;
        this.mail = mail;
        this.audit = audit;
    }

    // [스멜8] 파라미터 8개.
    public Approval create(String title, String content, int type, int priority,
                           Long drafterId, Long approverId, long amount, boolean urgent) {
        Approval approval = new Approval();
        approval.setTitle(title);
        approval.setContent(content);
        approval.setType(type);
        approval.setPriority(urgent ? Approval.HIGH_PRIORITY : priority);   // priority(우선순위): 1 낮음·2 보통·3 높음
        approval.setStatus(ApprovalStatus.DRAFT);
        approval.setDrafterId(drafterId);
        approval.setApproverId(approverId);
        approval.setAmount(amount);
        approval.setCreatedAt(LocalDateTime.now());
        approval.setUpdatedAt(LocalDateTime.now());
        repo.save(approval);

        // [리팩토링] 타임스탬프 포맷팅 + 문자열 조립을 AuditLogger 구현체로 위임(docs/4-21 "동일 중복" #1 해소).
        // 기존 라인의 "type=" 정보는 action 문자열에 실어 그대로 보존(콘솔 출력에서 필드 순서만 뒤로 이동).
        audit.write("APPROVAL CREATE type=" + approval.getType(), approval.getId(), drafterId);
        return approval;
    }

    /**
     * 결재 처리 — 상신/승인/반려/취소를 action 코드로 분기해 각 private 메서드로 위임한다.
     * 공개 시그니처와 관찰 가능한 동작(상태 전이·메일·감사로그·조용한 무시)은 리팩토링 전과 동일하다.
     *
     * action: 1=상신, 2=승인, 3=반려, 9=취소
     */
    public void processApproval(Long id, Long userId, int action, String reason) {
        // [리팩토링] guard clause — 대상/요청자 부재를 조기 반환으로 처리(레거시와 동일하게 조용히 무시).
        Approval approval = repo.findById(id).orElse(null);
        if (approval == null) {
            // [스멜] 예외 대신 조용히 리턴 — 호출자는 실패를 알 수 없다.
            return;
        }
        User actor = userRepo.findById(userId).orElse(null);
        if (actor == null) {
            return;
        }

        // 공개 시그니처는 int action 그대로 유지(계약 변경 금지) — 여기서만 enum으로 변환해 분기한다.
        // 알 수 없는 코드는 fromCode()가 null을 반환 → 아래 어떤 분기에도 안 걸려 조용히 무시(레거시 동작 보존).
        ApprovalAction requestedAction = ApprovalAction.fromCode(action);

        // [리팩토링] 상신/승인/반려/취소 각각을 private 메서드로 추출(Long Method 분해). 위임만 한다.
        // switch는 selector가 null이면 기본적으로 NPE를 던지므로, case null을 명시해 알 수 없는
        // action(fromCode()가 null 반환)을 조용히 무시하는 레거시 동작을 그대로 보존한다.
        switch (requestedAction) {
            case SUBMIT -> submit(approval, userId);
            case APPROVE -> approve(approval, actor, userId);
            case REJECT -> reject(approval, actor, userId, reason);
            case CANCEL -> cancel(approval, userId);
            case null -> {
                // 알 수 없는 action → 조용히 무시(레거시 동작 보존)
            }
        }
    }

    // [리팩토링] 상태·권한 검증은 Approval.submit()이 판단(false면 조건 불충족 → 조용히 무시).
    // 여기는 "성공하면 저장·메일·감사로그"만 오케스트레이션한다.
    private void submit(Approval approval, Long userId) {
        if (!approval.submit()) {
            return;
        }
        repo.save(approval);

        // [스멜4] 메일 발송 — 본문 생성 로직이 곳곳에 복붙.
        User approver = userRepo.findById(approval.getApproverId()).orElse(null);
        if (approver != null) {
            String body = "안녕하세요 " + approver.getName() + "님,\n"
                    + "결재 요청이 도착했습니다.\n제목: " + approval.getTitle()
                    + "\n기안자ID: " + approval.getDrafterId();
            mail.send(approver.getEmail(), "[결재요청] " + approval.getTitle(), body);
        }
        audit.write("APPROVAL SUBMIT", approval.getId(), userId);
    }

    // [리팩토링] 상태·본인확인·권한 검증은 Approval.approve(actor, userId)가 판단.
    private void approve(Approval approval, User actor, Long userId) {
        if (!approval.approve(actor, userId)) {
            return;
        }
        repo.save(approval);

        // [스멜4] 또 복붙된 메일 발송
        User drafter = userRepo.findById(approval.getDrafterId()).orElse(null);
        if (drafter != null) {
            mail.send(drafter.getEmail(), "[결재승인] " + approval.getTitle(), approvedBody(approval, drafter));
        }
        audit.write("APPROVAL APPROVE", approval.getId(), userId);
    }

    // [리팩토링] Extract Method — approve()에 있던 메일 본문 조립을 분리.
    private String approvedBody(Approval approval, User drafter) {
        return "안녕하세요 " + drafter.getName() + "님,\n"
                + "결재가 승인되었습니다.\n제목: " + approval.getTitle();
    }

    // [리팩토링] 상태·본인확인·권한 검증은 Approval.reject(actor, userId, reason)가 판단(승인과 조건 동일 — docs/4-5).
    private void reject(Approval approval, User actor, Long userId, String reason) {
        if (!approval.reject(actor, userId, reason)) {
            return;
        }
        repo.save(approval);

        User drafter = userRepo.findById(approval.getDrafterId()).orElse(null);
        if (drafter != null) {
            mail.send(drafter.getEmail(), "[결재반려] " + approval.getTitle(), rejectedBody(approval, drafter, reason));
        }
        audit.write("APPROVAL REJECT", approval.getId(), userId);
    }

    // [리팩토링] Extract Method — reject()에 있던 메일 본문 조립을 분리.
    private String rejectedBody(Approval approval, User drafter, String reason) {
        return "안녕하세요 " + drafter.getName() + "님,\n"
                + "결재가 반려되었습니다.\n제목: " + approval.getTitle()
                + "\n사유: " + reason;
    }

    // [리팩토링] 상태·본인확인 검증은 Approval.cancel(userId)가 판단.
    private void cancel(Approval approval, Long userId) {
        if (!approval.cancel(userId)) {
            return;
        }
        repo.save(approval);
        audit.write("APPROVAL CANCEL", approval.getId(), userId);
    }

    public List<Approval> myDrafts(Long userId) {
        return repo.findByDrafterId(userId);
    }

    public List<Approval> myInbox(Long userId) {
        return repo.findByApproverId(userId);
    }
}
