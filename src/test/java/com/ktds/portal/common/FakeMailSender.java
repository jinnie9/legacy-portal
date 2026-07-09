package com.ktds.portal.common;

import java.util.ArrayList;
import java.util.List;

/**
 * [테스트 더블] {@link MailSender}의 테스트용 가짜 구현.
 * 실제 발송(콘솔 출력조차) 없이 send() 호출 인자만 리스트에 기록한다 — ApprovalServiceMailNotificationTest에서
 * "메일이 보내졌는가/어떤 인자로 보내졌는가"만 검증하고 싶을 때 ConsoleMailSender 대신 주입한다.
 */
public class FakeMailSender implements MailSender {

    public record SentMail(String to, String subject, String body) {
    }

    private final List<SentMail> sentMails = new ArrayList<>();

    @Override
    public void send(String to, String subject, String body) {
        sentMails.add(new SentMail(to, subject, body));
    }

    public List<SentMail> sentMails() {
        return sentMails;
    }
}
