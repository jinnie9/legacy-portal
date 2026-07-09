package com.ktds.portal.approval.repository;

import com.ktds.portal.approval.domain.Approval;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ApprovalRepository extends JpaRepository<Approval, Long> {
    List<Approval> findByDrafterId(Long drafterId);
    List<Approval> findByApproverId(Long approverId);
}
