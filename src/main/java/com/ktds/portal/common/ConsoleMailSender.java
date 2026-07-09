package com.ktds.portal.common;

import org.springframework.stereotype.Component;

/**
 * 메일 발송기 — 콘솔 출력 구현체.
 * [리팩토링] {@link MailSender} 인터페이스 구현 + {@code @Component}로 등록 — ApprovalService/NoticeService가
 * 더 이상 이 구체 클래스를 직접 new 하지 않고 생성자 주입으로 받는다(Tight Coupling 해소).
 * [리팩토링] 이름 개선: SmtpMailSender → ConsoleMailSender. 실제로는 SMTP를 전혀 쓰지 않고 콘솔에
 * 출력만 하므로, 실제 동작과 일치하는 이름으로 교체(Poor Naming 방지 — 클래스 이름이 거짓말하지 않게).
 */
@Component
public class ConsoleMailSender implements MailSender {

    @Override
    public void send(String to, String subject, String body) {
        // 실제 서비스라면 JavaMailSender 등을 사용하겠지만, 이 실습에서는 콘솔 출력이 곧 실제 동작이다.
        System.out.println("=== MAIL ===");
        System.out.println("TO: " + to);
        System.out.println("SUBJECT: " + subject);
        System.out.println(body);
        System.out.println("============");
    }
}
