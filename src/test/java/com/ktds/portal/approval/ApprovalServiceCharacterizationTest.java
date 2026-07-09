package com.ktds.portal.approval;

import com.ktds.portal.approval.domain.Approval;
import com.ktds.portal.approval.domain.ApprovalStatus;
import com.ktds.portal.approval.repository.ApprovalRepository;
import com.ktds.portal.approval.service.ApprovalService;
import com.ktds.portal.common.FileAuditLogger;
import com.ktds.portal.common.SmtpMailSender;
import com.ktds.portal.user.User;
import com.ktds.portal.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * [특성화 테스트] processApproval(id, userId, action, reason)의 현재 동작을 리팩토링 전 안전망으로 고정한다.
 *
 * 목적은 "옳고 그름 판단"이 아니라 "지금 이렇게 동작한다"는 사실 고정이다 — 특히 4)·5)·6번은
 * 레거시가 예외 없이 조용히 무시하는 케이스인데, 이 동작을 CLAUDE.md 불변 규칙에 따라 그대로 보존한다.
 * 리팩토링(예: docs/4-12 백로그 5번 God Class 분해) 전후로 이 6개 테스트는 변경 없이 그대로 green이어야 한다.
 *
 * @DataJpaTest + @Import(...): 서비스를 직접 new 하지 않고, 슬라이스 테스트 컨텍스트에 명시적으로
 * 끌어들여 실제 ApprovalRepository/UserRepository 빈을 생성자로 주입받게 한다. ApprovalService가
 * MailSender/AuditLogger도 생성자로 받게 되면서(강결합 해소), @DataJpaTest 슬라이스가 자동으로 스캔하지
 * 않는 SmtpMailSender/FileAuditLogger(@Component)도 함께 @Import해야 컨텍스트가 정상적으로 뜬다.
 * @AutoConfigureTestDatabase(Replace.NONE): src/test/resources/application.properties 의 H2(MODE=LEGACY)
 * 설정을 그대로 쓴다 — @DataJpaTest 기본값(Replace.ANY)이 임의의 임베디드 DB로 바꿔치기하는 것을 막는다.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({ApprovalService.class, SmtpMailSender.class, FileAuditLogger.class})
class ApprovalServiceCharacterizationTest {

    @Autowired
    private ApprovalService approvalService;

    @Autowired
    private ApprovalRepository approvalRepository;

    @Autowired
    private UserRepository userRepository;

    private Long 기안자_사원_id;
    private Long 결재자_팀장_id;
    private Long 결재자_사원_id;   // 권한 없는 결재자(role=1) 시나리오용

    @BeforeEach
    void 사용자_데이터_준비() {
        기안자_사원_id = userRepository.save(new User("김사원", "kim@test.com", 1, "개발팀")).getId();
        결재자_팀장_id = userRepository.save(new User("박팀장", "park@test.com", 2, "개발팀")).getId();
        결재자_사원_id = userRepository.save(new User("최사원", "choi@test.com", 1, "개발팀")).getId();
    }

    @Test
    @DisplayName("1) 상신 후 승인하면 결재가 승인 상태(2)로 바뀐다")
    void 상신_후_승인하면_승인_상태로_바뀐다() {
        Approval approval = approvalService.create(
                "노트북 구매", "개발용 노트북", 1, 2, 기안자_사원_id, 결재자_팀장_id, 1_200_000L, false);

        approvalService.processApproval(approval.getId(), 기안자_사원_id, 1, "");   // 상신
        approvalService.processApproval(approval.getId(), 결재자_팀장_id, 2, "");   // 승인

        Approval 결과 = approvalRepository.findById(approval.getId()).orElseThrow();
        assertThat(결과.getStatus()).isEqualTo(ApprovalStatus.APPROVED);
        assertThat(결과.getPriority()).isEqualTo(3); // 지출 + 100만원 이상 → 상신 시 자동 상향(observed)
    }

    @Test
    @DisplayName("2) 상신 후 반려하면 반려 상태(3)와 사유가 그대로 저장된다")
    void 상신_후_반려하면_반려_상태와_사유가_저장된다() {
        Approval approval = approvalService.create(
                "사무용품 구매", "A4용지", 1, 2, 기안자_사원_id, 결재자_팀장_id, 30_000L, false);

        approvalService.processApproval(approval.getId(), 기안자_사원_id, 1, "");
        approvalService.processApproval(approval.getId(), 결재자_팀장_id, 3, "예산 부족");

        Approval 결과 = approvalRepository.findById(approval.getId()).orElseThrow();
        assertThat(결과.getStatus()).isEqualTo(ApprovalStatus.REJECTED);
        assertThat(결과.getRejectReason()).isEqualTo("예산 부족");
    }

    @Test
    @DisplayName("3) 임시저장 상태에서 취소하면 취소 상태(9)로 바뀐다")
    void 임시저장_상태에서_취소하면_취소_상태로_바뀐다() {
        Approval approval = approvalService.create(
                "연차 휴가", "개인 사유", 2, 1, 기안자_사원_id, 결재자_팀장_id, 0L, false);

        approvalService.processApproval(approval.getId(), 기안자_사원_id, 9, "");   // 임시저장(0) 상태에서 취소

        Approval 결과 = approvalRepository.findById(approval.getId()).orElseThrow();
        assertThat(결과.getStatus()).isEqualTo(ApprovalStatus.CANCELED);
    }

    @Test
    @DisplayName("4) 권한 없는(사원) 결재자가 승인을 시도하면 예외 없이 조용히 무시되어 상태가 그대로 상신이다")
    void 권한_없는_사원이_승인을_시도하면_상태가_그대로_유지된다() {
        Approval approval = approvalService.create(
                "소모품 구매", "청소용품", 1, 2, 기안자_사원_id, 결재자_사원_id, 50_000L, false);

        approvalService.processApproval(approval.getId(), 기안자_사원_id, 1, "");        // 상신
        approvalService.processApproval(approval.getId(), 결재자_사원_id, 2, "");        // role=1이 승인 시도

        Approval 결과 = approvalRepository.findById(approval.getId()).orElseThrow();
        assertThat(결과.getStatus()).isEqualTo(ApprovalStatus.SUBMITTED); // 승인되지 않고 상신 그대로(조용한 무시)
    }

    @Test
    @DisplayName("5) 존재하지 않는 id로 처리를 시도하면 예외 없이 아무 일도 일어나지 않는다")
    void 존재하지_않는_id로_처리하면_아무_일도_일어나지_않는다() {
        long 존재하지_않는_id = 999_999L;

        assertThatCode(() -> approvalService.processApproval(존재하지_않는_id, 기안자_사원_id, 1, ""))
                .doesNotThrowAnyException();
        assertThat(approvalRepository.findById(존재하지_않는_id)).isEmpty();
    }

    @Test
    @DisplayName("6) 이미 승인된 결재를 다시 승인 시도해도 상태가 그대로 승인으로 유지된다")
    void 이미_승인된_결재를_재승인_시도해도_상태가_그대로_유지된다() {
        Approval approval = approvalService.create(
                "출장비", "서울 출장", 1, 2, 기안자_사원_id, 결재자_팀장_id, 200_000L, false);

        approvalService.processApproval(approval.getId(), 기안자_사원_id, 1, "");   // 상신
        approvalService.processApproval(approval.getId(), 결재자_팀장_id, 2, "");   // 승인
        approvalService.processApproval(approval.getId(), 결재자_팀장_id, 2, "");   // 재승인 시도(status==SUBMITTED 조건 불충족)

        Approval 결과 = approvalRepository.findById(approval.getId()).orElseThrow();
        assertThat(결과.getStatus()).isEqualTo(ApprovalStatus.APPROVED); // 승인 상태 그대로, 두 번째 승인 시도는 무시됨
    }
}
