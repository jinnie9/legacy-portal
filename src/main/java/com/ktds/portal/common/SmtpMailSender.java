package com.ktds.portal.common;

import org.springframework.stereotype.Component;

/**
 * 메일 발송기.
 * [리팩토링] {@link MailSender} 인터페이스 구현 + {@code @Component}로 등록 — ApprovalService가
 * 더 이상 이 구체 클래스를 직접 new 하지 않고 생성자 주입으로 받는다(Tight Coupling 해소).
 * 실습에서는 실제 SMTP 대신 콘솔에 출력만 한다.
 */
@Component
public class SmtpMailSender implements MailSender {

    @Override
    public void send(String to, String subject, String body) {
        // 실제로는 JavaMailSender 등을 사용. 실습용으로 콘솔 출력.
        System.out.println("=== MAIL ===");
        System.out.println("TO: " + to);
        System.out.println("SUBJECT: " + subject);
        System.out.println(body);
        System.out.println("============");
    }
}
