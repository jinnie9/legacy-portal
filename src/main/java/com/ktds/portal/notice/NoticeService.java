package com.ktds.portal.notice;

import com.ktds.portal.common.AuditLogger;
import com.ktds.portal.common.MailSender;
import com.ktds.portal.user.User;
import com.ktds.portal.user.UserRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * 공지 서비스.
 * [스멜4 핵심] (감사 로그 부분 해결) ApprovalService 와 거의 동일했던 "감사 로그" 조립 로직은
 *             AuditLogger.write(action, id, userId)로 이동(docs/4-21 "동일 중복" #1). 메일 본문 템플릿은
 *             여전히 여기 있다(유사 중복, 추후 과제).
 * [스멜5] (해결) 역시 협력 객체를 직접 new 하던 강결합 — ApprovalService와 동일하게
 *         {@link MailSender}/{@link AuditLogger} 인터페이스 + 생성자 주입으로 전환(docs/4-9 강결합 탐지 참고).
 */
@Service
public class NoticeService {

    private final NoticeRepository repo;
    private final UserRepository userRepo;
    private final MailSender mail;
    private final AuditLogger audit;

    public NoticeService(NoticeRepository repo, UserRepository userRepo, MailSender mail, AuditLogger audit) {
        this.repo = repo;
        this.userRepo = userRepo;
        this.mail = mail;
        this.audit = audit;
    }

    public Notice create(String title, String content, int category, Long writerId, boolean pinned) {
        Notice n = new Notice();   // n = 공지(Notice 객체)
        n.setTitle(title);
        n.setContent(content);
        n.setCategory(category);   // category(분류): 1 일반·2 긴급·3 인사  [숫자 의미가 코드에 없음]
        n.setStatus(0);            // status(상태): 0 임시·1 게시·9 내림  [0=임시, 게시 전]
        n.setWriterId(writerId);
        n.setPinned(pinned);
        n.setCreatedAt(LocalDateTime.now());
        repo.save(n);

        // [리팩토링] 타임스탬프 포맷팅 + 문자열 조립은 AuditLogger 구현체 책임(docs/4-21 "동일 중복" #1 해소).
        audit.write("NOTICE CREATE", n.getId(), writerId);
        return n;
    }

    public void publish(Long id, Long userId) {
        Notice n = repo.findById(id).orElse(null);   // n = 공지(Notice 객체)
        if (n == null) return;
        User u = userRepo.findById(userId).orElse(null);   // u = 사용자(User 객체)
        if (u == null) return;

        // [스멜3] 권한 매직넘버. [스멜2] 게시 + 긴급공지 메일 + 로그를 한 메서드에서.
        if (u.getRole() >= 2) {        // role 1=사원·2=팀장·3=임원 (role>=2 팀장 이상 게시권한)  [ApprovalService 와 똑같은 권한 판정 복붙]
            if (n.getStatus() == 0) {  // status==0 → 임시(게시 전)일 때만
                n.setStatus(1);   // 1 = 게시 (PUBLISHED)
                repo.save(n);

                // [스멜4] 긴급(2) 공지면 전직원 메일 — 본문 생성 패턴이 또 반복된다.
                if (n.getCategory() == 2) {   // category 1=일반·2=긴급·3=인사 → category==2(긴급)  [숫자 2를 외워야 의미를 앎]
                    for (User member : userRepo.findAll()) {
                        String body = "안녕하세요 " + member.getName() + "님,\n"
                                + "긴급 공지가 게시되었습니다.\n제목: " + n.getTitle();
                        mail.send(member.getEmail(), "[긴급공지] " + n.getTitle(), body);
                    }
                }
                audit.write("NOTICE PUBLISH", n.getId(), userId);
            }
        }
    }

    // [스멜10] statusLabel 도 ApprovalService 와 거의 같은 구조로 또 존재한다.
    public String statusLabel(Notice n) {
        int s = n.getStatus();   // s = status(상태): 0 임시·1 게시·9 내림
        if (s == 0) return "임시";        // 0~9 라벨 번역 — ApprovalService.statusLabel 과 판박이(중복)
        else if (s == 1) return "게시";
        else if (s == 9) return "내림";
        else return "알수없음";
    }
}
