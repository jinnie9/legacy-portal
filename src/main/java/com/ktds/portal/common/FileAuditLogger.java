package com.ktds.portal.common;

import org.springframework.stereotype.Component;

/**
 * 감사 로그 기록기.
 * [리팩토링] {@link AuditLogger} 인터페이스 구현 + {@code @Component}로 등록 — ApprovalService가
 * 더 이상 이 구체 클래스를 직접 new 하지 않고 생성자 주입으로 받는다(Tight Coupling 해소).
 */
@Component
public class FileAuditLogger implements AuditLogger {

    @Override
    public void write(String line) {
        // 실제로는 파일에 append. 실습용으로 콘솔 출력.
        System.out.println("[AUDIT] " + line);
    }
}
