package com.ktds.portal.approval.service;

import com.ktds.portal.approval.domain.Approval;
import com.ktds.portal.approval.domain.ApprovalStatus;
import com.ktds.portal.approval.repository.ApprovalRepository;
import com.ktds.portal.common.AuditLogger;
import com.ktds.portal.common.FakeMailSender;
import com.ktds.portal.user.User;
import com.ktds.portal.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * [단위 테스트] ApprovalService가 상신/승인/반려 처리 중 {@link com.ktds.portal.common.MailSender#send}를
 * 올바른 수신자·제목·본문으로 호출하는지 검증한다.
 *
 * ApprovalServiceCharacterizationTest(안전망, @DataJpaTest 기반 — 실제 DB에 상태가 저장되는지를 확인)와는
 * 목적이 다르다. 여기서는 DB/Spring 컨텍스트 없이 ApprovalRepository/UserRepository/AuditLogger를 Mockito로,
 * MailSender만 {@link FakeMailSender}(실 발송 없이 호출 인자만 기록하는 테스트 더블)로 주입해
 * "메일 협력이 기대한 대로 일어나는가"만 빠르게 검증한다. 기존 특성화 테스트는 수정하지 않고 별도 파일로 추가.
 */
class ApprovalServiceMailNotificationTest {

    private ApprovalRepository approvalRepository;
    private UserRepository userRepository;
    private FakeMailSender mailSender;
    private ApprovalService approvalService;

    private static final Long 기안자_id = 10L;
    private static final Long 결재자_팀장_id = 20L;
    private static final Long 결재자_사원_id = 30L;

    @BeforeEach
    void setUp() {
        approvalRepository = mock(ApprovalRepository.class);
        userRepository = mock(UserRepository.class);
        mailSender = new FakeMailSender();
        AuditLogger auditLogger = mock(AuditLogger.class);   // 이 테스트의 관심사가 아니므로 목으로만 채움
        approvalService = new ApprovalService(approvalRepository, userRepository, mailSender, auditLogger);

        User 기안자 = new User("김기안", "drafter@test.com", 1, "개발팀");
        User 결재자_팀장 = new User("박팀장", "approver@test.com", 2, "개발팀");
        User 결재자_사원 = new User("최사원", "junior-approver@test.com", 1, "개발팀");
        when(userRepository.findById(기안자_id)).thenReturn(Optional.of(기안자));
        when(userRepository.findById(결재자_팀장_id)).thenReturn(Optional.of(결재자_팀장));
        when(userRepository.findById(결재자_사원_id)).thenReturn(Optional.of(결재자_사원));
    }

    private Approval 상신대기_결재(Long approverId) {
        Approval approval = new Approval();
        approval.setId(100L);
        approval.setTitle("노트북 구매");
        approval.setStatus(ApprovalStatus.DRAFT);
        approval.setType(2);
        approval.setDrafterId(기안자_id);
        approval.setApproverId(approverId);
        return approval;
    }

    private Approval 승인대기_결재(Long approverId) {
        Approval approval = 상신대기_결재(approverId);
        approval.setStatus(ApprovalStatus.SUBMITTED);
        return approval;
    }

    @Test
    @DisplayName("상신하면 결재자에게 결재요청 메일이 발송된다")
    void 상신하면_결재자에게_메일이_발송된다() {
        Approval approval = 상신대기_결재(결재자_팀장_id);
        when(approvalRepository.findById(100L)).thenReturn(Optional.of(approval));

        approvalService.processApproval(100L, 기안자_id, 1, "");

        assertThat(mailSender.sentMails()).hasSize(1);
        FakeMailSender.SentMail sent = mailSender.sentMails().get(0);
        assertThat(sent.to()).isEqualTo("approver@test.com");
        assertThat(sent.subject()).isEqualTo("[결재요청] 노트북 구매");
    }

    @Test
    @DisplayName("승인하면 기안자에게 결재승인 메일이 발송된다")
    void 승인하면_기안자에게_메일이_발송된다() {
        Approval approval = 승인대기_결재(결재자_팀장_id);
        when(approvalRepository.findById(100L)).thenReturn(Optional.of(approval));

        approvalService.processApproval(100L, 결재자_팀장_id, 2, "");

        assertThat(mailSender.sentMails()).hasSize(1);
        FakeMailSender.SentMail sent = mailSender.sentMails().get(0);
        assertThat(sent.to()).isEqualTo("drafter@test.com");
        assertThat(sent.subject()).isEqualTo("[결재승인] 노트북 구매");
    }

    @Test
    @DisplayName("반려하면 기안자에게 반려 사유가 포함된 메일이 발송된다")
    void 반려하면_기안자에게_사유가_포함된_메일이_발송된다() {
        Approval approval = 승인대기_결재(결재자_팀장_id);
        when(approvalRepository.findById(100L)).thenReturn(Optional.of(approval));

        approvalService.processApproval(100L, 결재자_팀장_id, 3, "예산 부족");

        assertThat(mailSender.sentMails()).hasSize(1);
        FakeMailSender.SentMail sent = mailSender.sentMails().get(0);
        assertThat(sent.to()).isEqualTo("drafter@test.com");
        assertThat(sent.subject()).isEqualTo("[결재반려] 노트북 구매");
        assertThat(sent.body()).contains("예산 부족");
    }

    @Test
    @DisplayName("권한 없는(사원) 결재자가 승인을 시도하면 메일이 발송되지 않는다")
    void 권한_없는_사용자가_승인_시도하면_메일이_발송되지_않는다() {
        Approval approval = 승인대기_결재(결재자_사원_id);
        when(approvalRepository.findById(100L)).thenReturn(Optional.of(approval));

        approvalService.processApproval(100L, 결재자_사원_id, 2, "");

        assertThat(mailSender.sentMails()).isEmpty();
    }

    @Test
    @DisplayName("취소는 메일을 발송하지 않는다(승인 대상이 없어 알림 자체가 없는 설계)")
    void 취소하면_메일이_발송되지_않는다() {
        Approval approval = 상신대기_결재(결재자_팀장_id);
        when(approvalRepository.findById(100L)).thenReturn(Optional.of(approval));

        approvalService.processApproval(100L, 기안자_id, 9, "");

        assertThat(mailSender.sentMails()).isEmpty();
    }
}
