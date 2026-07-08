package com.ktds.portal.approval;

import com.ktds.portal.common.FileAuditLogger;
import com.ktds.portal.common.SmtpMailSender;
import com.ktds.portal.user.User;
import com.ktds.portal.user.UserRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 결재 서비스 — 이 클래스가 이 과정의 "주인공 안티패턴"이다.
 *
 * ============================ 의도적으로 심어둔 스멜 목록 (원본 기준) ============================
 *  1. God Class            : 검증 + 영속화 + 메일 + 감사로그 + 포맷팅 + 권한판정을 혼자 다 한다(책임 분산은
 *                            차후 과제 — 이번엔 메서드 단위로만 쪼갬, 아래 2번 참고).
 *  2. Long Method          : (해결) processApproval() 86줄·중첩 6단계 → submit/approve/reject/cancel
 *                            private 메서드 + guard clause로 분해. 각 메서드는 조기 반환으로 중첩 없이 평탄화.
 *  3. Magic Number         : Approval.type/priority 필드 자체는 아직 int(enum 후보로 남음, 아래 참고).
 *                            processApproval() 안의 매직넘버(status/action/role>=2/금액 임계값)는
 *                            전부 enum·상수·도메인 메서드로 정리 완료 — 아래 [리팩토링] 참고.
 *  4. Duplicated Code      : 메일 본문 생성/감사 로그 기록이 메서드마다 복붙 되어 있다.
 *  5. Tight Coupling       : new SmtpMailSender(), new FileAuditLogger() 직접 생성(DI 없음).
 *  6. Feature Envy         : Approval 의 필드를 꺼내 서비스가 직접 상태/금액 규칙을 계산한다.
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
 *   - statusLabel()의 5분기 if-else + tmp 변수는 제거하고 {@link ApprovalStatus#label()}에 위임.
 * [리팩토링] 이름 개선: d→approval, u→actor, s(지역변수)→status, proc(action을 다시 담던 임시변수) 제거.
 * [리팩토링] action: int(1/2/3/9) → {@link ApprovalAction} enum으로 변환해 비교(공개 시그니처는 int 그대로 유지 —
 *   계약 변경 금지). 알 수 없는 action 코드는 fromCode()가 null을 반환해 조용히 무시되는 레거시 동작 보존.
 * [리팩토링] role>=2 판정: User.isManagerOrAbove() 도메인 메서드로 이동(승인·반려 분기의 중복 판정 통합).
 * [리팩토링] type==1/amount>=1000000/priority=3 매직넘버: EXPENSE_TYPE/HIGH_PRIORITY_AMOUNT_THRESHOLD/
 *   HIGH_PRIORITY 이름 있는 상수로 추출.
 * [리팩토링] processApproval() 분해: 공개 시그니처(processApproval(id, userId, action, reason))와
 *   관찰 가능한 동작은 그대로 두고, 내부만 submit()/approve()/reject()/cancel() private 메서드로 추출.
 *   각 메서드 안의 중첩 if는 guard clause(조기 반환)로 평탄화했다 — 예: "상신 상태 + 본인 결재자 + 팀장 이상"
 *   3중 중첩이던 승인 조건이 조건마다 하나씩 조기 반환하는 3줄로 바뀌었을 뿐, 최종 판정 결과는 동일하다.
 */
@Service
public class ApprovalService {

    // [리팩토링] processApproval()에 남아있던 매직넘버(type==1, amount>=1000000, priority=3)를
    // 이름 있는 상수로 추출했다. Approval.type/priority 필드 자체는 아직 int 그대로다 —
    // create()가 외부(API)에서 받은 int를 검증 없이 그대로 저장하는 레거시 동작이 있어서,
    // 여기까지 enum으로 바꾸면 "잘못된 값도 조용히 통과하던" 동작이 예외로 바뀔 위험이 있다
    // (docs/4-4 참고). 그래서 이번 리팩토링 범위는 상수 추출까지로 한정했다.
    private static final int EXPENSE_TYPE = 1;                        // Approval.type: 1=지출
    private static final long HIGH_PRIORITY_AMOUNT_THRESHOLD = 1_000_000L;
    private static final int HIGH_PRIORITY = 3;                       // Approval.priority: 3=높음

    private final ApprovalRepository repo;
    private final UserRepository userRepo;

    // [스멜5] 강결합 — 협력 객체를 생성자 주입 없이 직접 new 한다. 테스트에서 갈아끼울 수 없다.
    private final SmtpMailSender mail = new SmtpMailSender();
    private final FileAuditLogger audit = new FileAuditLogger();

    public ApprovalService(ApprovalRepository repo, UserRepository userRepo) {
        this.repo = repo;
        this.userRepo = userRepo;
    }

    // [스멜8] 파라미터 8개.
    public Approval create(String title, String content, int type, int priority,
                           Long drafterId, Long approverId, long amount, boolean urgent) {
        Approval approval = new Approval();
        approval.setTitle(title);
        approval.setContent(content);
        approval.setType(type);
        approval.setPriority(urgent ? HIGH_PRIORITY : priority);   // priority(우선순위): 1 낮음·2 보통·3 높음
        approval.setStatus(ApprovalStatus.DRAFT);
        approval.setDrafterId(drafterId);
        approval.setApproverId(approverId);
        approval.setAmount(amount);
        approval.setCreatedAt(LocalDateTime.now());
        approval.setUpdatedAt(LocalDateTime.now());
        repo.save(approval);

        // [스멜4] 감사 로그 기록 — 이 6줄이 submit/approve/reject/cancel 에도 복붙 되어 있다.
        String now = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        String line = "[" + now + "] APPROVAL CREATE id=" + approval.getId()
                + " by=" + drafterId + " type=" + approval.getType();
        audit.write(line);
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
            case null -> { } // 알 수 없는 action → 조용히 무시(레거시 동작 보존)
        }
    }

    // 상신: 임시저장일 때만 가능. [스멜6] 금액 기준 우선순위 자동 상향 — 도메인 규칙이 서비스에 박혀 있다.
    private void submit(Approval approval, Long userId) {
        if (approval.getStatus() != ApprovalStatus.DRAFT) {
            return;
        }
        if (approval.getType() == EXPENSE_TYPE && approval.getAmount() >= HIGH_PRIORITY_AMOUNT_THRESHOLD) {
            approval.setPriority(HIGH_PRIORITY);
        }
        approval.setStatus(ApprovalStatus.SUBMITTED);
        approval.setUpdatedAt(LocalDateTime.now());
        repo.save(approval);

        // [스멜4] 메일 발송 — 본문 생성 로직이 곳곳에 복붙.
        User approver = userRepo.findById(approval.getApproverId()).orElse(null);
        if (approver != null) {
            String body = "안녕하세요 " + approver.getName() + "님,\n"
                    + "결재 요청이 도착했습니다.\n제목: " + approval.getTitle()
                    + "\n기안자ID: " + approval.getDrafterId();
            mail.send(approver.getEmail(), "[결재요청] " + approval.getTitle(), body);
        }
        writeAudit("APPROVAL SUBMIT", approval.getId(), userId);
    }

    // 승인: 상신 상태 + 본인이 결재자 + 권한(팀장 이상) 일 때만.
    private void approve(Approval approval, User actor, Long userId) {
        if (approval.getStatus() != ApprovalStatus.SUBMITTED) {
            return;
        }
        if (approval.getApproverId() == null || !approval.getApproverId().equals(userId)) {
            return;
        }
        if (!actor.isManagerOrAbove()) {
            return;
        }
        approval.setStatus(ApprovalStatus.APPROVED);
        approval.setUpdatedAt(LocalDateTime.now());
        repo.save(approval);

        // [스멜4] 또 복붙된 메일 발송
        User drafter = userRepo.findById(approval.getDrafterId()).orElse(null);
        if (drafter != null) {
            String body = "안녕하세요 " + drafter.getName() + "님,\n"
                    + "결재가 승인되었습니다.\n제목: " + approval.getTitle();
            mail.send(drafter.getEmail(), "[결재승인] " + approval.getTitle(), body);
        }
        writeAudit("APPROVAL APPROVE", approval.getId(), userId);
    }

    // 반려: 상신 상태 + 본인이 결재자 + 권한(팀장 이상) 일 때만 (승인과 조건 동일 — docs/4-5 중복 코드).
    private void reject(Approval approval, User actor, Long userId, String reason) {
        if (approval.getStatus() != ApprovalStatus.SUBMITTED) {
            return;
        }
        if (approval.getApproverId() == null || !approval.getApproverId().equals(userId)) {
            return;
        }
        if (!actor.isManagerOrAbove()) {
            return;
        }
        approval.setStatus(ApprovalStatus.REJECTED);
        approval.setRejectReason(reason);
        approval.setUpdatedAt(LocalDateTime.now());
        repo.save(approval);

        User drafter = userRepo.findById(approval.getDrafterId()).orElse(null);
        if (drafter != null) {
            String body = "안녕하세요 " + drafter.getName() + "님,\n"
                    + "결재가 반려되었습니다.\n제목: " + approval.getTitle()
                    + "\n사유: " + reason;
            mail.send(drafter.getEmail(), "[결재반려] " + approval.getTitle(), body);
        }
        writeAudit("APPROVAL REJECT", approval.getId(), userId);
    }

    // 취소: 기안자 본인 + 아직 승인 전(임시저장 또는 상신)일 때만.
    private void cancel(Approval approval, Long userId) {
        if (approval.getStatus() != ApprovalStatus.DRAFT && approval.getStatus() != ApprovalStatus.SUBMITTED) {
            return;
        }
        if (approval.getDrafterId() == null || !approval.getDrafterId().equals(userId)) {
            return;
        }
        approval.setStatus(ApprovalStatus.CANCELED);
        approval.setUpdatedAt(LocalDateTime.now());
        repo.save(approval);
        writeAudit("APPROVAL CANCEL", approval.getId(), userId);
    }

    // [스멜4] 그나마 추출했지만 create() 안에는 또 복붙이 남아 있다(불완전한 중복 제거).
    private void writeAudit(String act, Long id, Long userId) {
        String now = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        audit.write("[" + now + "] " + act + " id=" + id + " by=" + userId);
    }

    // [스멜1] 화면 표시용 문자열까지 서비스가 만든다.
    // [리팩토링] 5분기 if-else + tmp 변수 제거 — 라벨 소유권을 ApprovalStatus enum으로 이동, 여기서는 위임만 한다.
    public String statusLabel(Approval approval) {
        return approval.getStatus().label();
    }

    // [스멜6] Feature Envy — Approval 데이터를 꺼내 금액 등급을 서비스가 계산.
    public String amountGrade(Approval approval) {
        long a = approval.getAmount();   // a = amount(금액, 원)  [스멜9: 한 글자 약어]
        if (a >= 10000000) return "S";   // [스멜3] 1000만원=S — 기준 숫자의 의미가 코드에 없음
        else if (a >= 1000000) return "A";   // 100만원=A
        else if (a >= 100000) return "B";    // 10만원=B
        else return "C";
    }

    public List<Approval> myDrafts(Long userId) {
        return repo.findByDrafterId(userId);
    }

    public List<Approval> myInbox(Long userId) {
        return repo.findByApproverId(userId);
    }
}
