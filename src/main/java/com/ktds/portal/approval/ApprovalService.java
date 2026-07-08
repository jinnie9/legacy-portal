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
 *  1. God Class            : 검증 + 영속화 + 메일 + 감사로그 + 포맷팅 + 권한판정을 혼자 다 한다.
 *  2. Long Method          : processApproval() 한 메서드가 100줄 이상, 중첩 if 6단계.
 *  3. Magic Number         : type 1~4, role 1~3, priority 1~3 이 아직 흩어져 있다.
 *                            (status 0/1/2/3/9는 ApprovalStatus enum으로 전환 완료 — 아래 [리팩토링] 참고)
 *  4. Duplicated Code      : 메일 본문 생성/감사 로그 기록이 메서드마다 복붙 되어 있다.
 *  5. Tight Coupling       : new SmtpMailSender(), new FileAuditLogger() 직접 생성(DI 없음).
 *  6. Feature Envy         : Approval 의 필드를 꺼내 서비스가 직접 상태/금액 규칙을 계산한다.
 *  7. Primitive Obsession  : type/priority/role 은 여전히 int 비교로 처리한다.
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
 * [리팩토링] 이름 개선: d→approval, u→actor, s(지역변수)→status, proc(action을 다시 담던 임시변수) 제거하고
 *   action 파라미터를 직접 비교에 사용.
 */
@Service
public class ApprovalService {

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
        approval.setPriority(urgent ? 3 : priority);   // priority(우선순위): 1 낮음·2 보통·3 높음  [스멜3: 3=높음, 왜 3이 높음? 코드만 봐선 모름]
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
     * 결재 처리 — 상신/승인/반려/취소를 action 코드로 분기한다.
     * [스멜2] 이 메서드 하나가 모든 일을 한다. [스멜1][스멜6] 규칙 계산을 서비스가 떠안는다.
     *
     * action: 1=상신, 2=승인, 3=반려, 9=취소
     */
    public void processApproval(Long id, Long userId, int action, String reason) {
        Approval approval = repo.findById(id).orElse(null);
        if (approval == null) {
            // [스멜] 예외 대신 조용히 리턴 — 호출자는 실패를 알 수 없다.
            return;
        }
        User actor = userRepo.findById(userId).orElse(null);
        if (actor == null) {
            return;
        }

        ApprovalStatus status = approval.getStatus();

        // [스멜2][스멜3] 거대한 if-지옥. 상태 전이 규칙이 action 값 비교로 흩어져 있다.
        if (action == 1) {            // action==1 → 상신 (숫자 1을 외워야 의미를 앎)
            // 상신: 임시저장일 때만 가능
            if (status == ApprovalStatus.DRAFT) {
                // [스멜6] 금액 기준 결재자 자동 상향 — 도메인 규칙이 서비스에 박혀 있다.
                if (approval.getType() == 1 && approval.getAmount() >= 1000000) {   // type 1=지출·2=휴가·3=구매·4=기타 → type==1(지출) && 100만원↑
                    approval.setPriority(3);   // 3 = 높음
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
        } else if (action == 2) {     // action==2 → 승인
            // 승인: 상신 상태 + 본인이 결재자 + 권한(role>=2) 일 때만
            if (status == ApprovalStatus.SUBMITTED) {
                if (approval.getApproverId() != null && approval.getApproverId().equals(userId)) {
                    if (actor.getRole() >= 2) {   // role 1=사원·2=팀장·3=임원 (role>=2 승인권한)  [스멜3: 숫자로 권한 판정]
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
                }
            }
        } else if (action == 3) {     // action==3 → 반려
            // 반려
            if (status == ApprovalStatus.SUBMITTED) {
                if (approval.getApproverId() != null && approval.getApproverId().equals(userId)) {
                    if (actor.getRole() >= 2) {   // role>=2 → 팀장 이상 (위 승인 분기와 똑같은 판정 복붙)
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
                }
            }
        } else if (action == 9) {     // action==9 → 취소 (왜 9? 4~8 은 비워둔 규칙 없는 번호)
            // 취소: 기안자 본인 + 아직 승인 전(임시저장 또는 상신)
            if (status == ApprovalStatus.DRAFT || status == ApprovalStatus.SUBMITTED) {
                if (approval.getDrafterId() != null && approval.getDrafterId().equals(userId)) {
                    approval.setStatus(ApprovalStatus.CANCELED);
                    approval.setUpdatedAt(LocalDateTime.now());
                    repo.save(approval);
                    writeAudit("APPROVAL CANCEL", approval.getId(), userId);
                }
            }
        }
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
