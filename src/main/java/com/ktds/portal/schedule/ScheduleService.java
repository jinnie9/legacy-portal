package com.ktds.portal.schedule;

import com.ktds.portal.common.AuditLogger;
import com.ktds.portal.user.User;
import com.ktds.portal.user.UserRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * 일정 서비스.
 * [스멜4] (해결) 감사 로그 조립 코드 복붙 — AuditLogger.write(action, id, userId)로 이동(docs/4-21 "동일 중복" #1).
 * [스멜] 시간 겹침(중복 예약) 검증이 길고 읽기 어렵다.
 * [스멜5] (해결) 협력 객체 직접 new — {@link AuditLogger} 인터페이스 + 생성자 주입으로 전환
 *         (docs/4-9 강결합 탐지 참고). ScheduleService는 메일을 보내지 않아 MailSender 의존은 없다.
 */
@Service
public class ScheduleService {

    private final ScheduleRepository repo;
    private final UserRepository userRepo;
    private final AuditLogger audit;

    public ScheduleService(ScheduleRepository repo, UserRepository userRepo, AuditLogger audit) {
        this.repo = repo;
        this.userRepo = userRepo;
        this.audit = audit;
    }

    public Schedule create(String title, Long ownerId, LocalDateTime startAt,
                           LocalDateTime endAt, String location) {
        // [스멜2] 검증 + 중복확인 + 저장 + 로그가 한 덩어리.
        if (title == null || title.equals("")) {   // [스멜] "".equals 가 아니라 NPE 위험 패턴
            return null;
        }
        if (startAt == null || endAt == null) {
            return null;
        }
        if (endAt.isBefore(startAt)) {
            return null;
        }
        // [스멜] 같은 사람의 시간 겹침을 직접 루프 돌며 확인(읽기 어려움).
        boolean flag1 = false;   // flag1 = 시간 겹침 여부(true=겹침)  [스멜9: 무슨 깃발? isOverlapping 이어야 함]
        for (Schedule s : repo.findByOwnerId(ownerId)) {   // s = 같은 소유자의 기존 일정(Schedule)
            if (s.getStatus() != 9) {   // status(상태): 0 예정·1 확정·9 취소 → 취소(9) 일정은 겹침 검사 제외
                if (s.getStartAt().isBefore(endAt) && startAt.isBefore(s.getEndAt())) {
                    flag1 = true;       // 시간대가 겹침 (이 조건식 자체도 메서드로 추출 대상)
                }
            }
        }
        if (flag1) {
            return null;   // [스멜] null 반환 — 호출자는 '겹쳐서 실패'인지 원인을 모름
        }

        Schedule sc = new Schedule();   // sc = 일정(Schedule 객체)
        sc.setTitle(title);
        sc.setOwnerId(ownerId);
        sc.setStartAt(startAt);
        sc.setEndAt(endAt);
        sc.setLocation(location);
        sc.setStatus(0);   // status(상태): 0 예정·1 확정·9 취소  [0=예정, 확정 전]
        repo.save(sc);

        // [리팩토링] 타임스탬프 포맷팅 + 문자열 조립은 AuditLogger 구현체 책임(docs/4-21 "동일 중복" #1 해소).
        audit.write("SCHEDULE CREATE", sc.getId(), ownerId);
        return sc;
    }

    public void confirm(Long id, Long userId) {
        Schedule sc = repo.findById(id).orElse(null);   // sc = 일정(Schedule 객체)
        if (sc == null) return;
        User u = userRepo.findById(userId).orElse(null);   // u = 사용자(User 객체)
        if (u == null) return;
        if (sc.getOwnerId().equals(userId)) {   // 본인 일정만 확정 가능
            if (sc.getStatus() == 0) {          // status==0 → 예정 상태일 때만
                sc.setStatus(1);   // 1 = 확정 (CONFIRMED)
                repo.save(sc);
                audit.write("SCHEDULE CONFIRM", sc.getId(), userId);
            }
        }
    }
}
