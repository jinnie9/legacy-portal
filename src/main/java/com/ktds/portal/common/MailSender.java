package com.ktds.portal.common;

/**
 * [리팩토링] 메일 발송 인터페이스 — ConsoleMailSender(구 SmtpMailSender)를 직접 new 하던
 * 강결합(Tight Coupling)을 해소하기 위해 추출.
 * 구현은 {@link ConsoleMailSender} 하나뿐이지만, 서비스가 인터페이스에 의존하게 되어 테스트에서 가짜로 교체 가능.
 */
public interface MailSender {
    void send(String to, String subject, String body);
}
