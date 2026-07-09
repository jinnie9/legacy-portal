package com.ktds.portal.common;

import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 감사 로그 기록기 — 콘솔 출력 구현체.
 * [리팩토링] {@link AuditLogger} 인터페이스 구현 + {@code @Component}로 등록 — ApprovalService/NoticeService/
 * ScheduleService가 더 이상 이 구체 클래스를 직접 new 하지 않고 생성자 주입으로 받는다(Tight Coupling 해소).
 * [리팩토링] 이름 개선: FileAuditLogger → ConsoleAuditLogger. 실제로는 파일에 append하지 않고 콘솔에
 * 출력만 하므로, 실제 동작과 일치하는 이름으로 교체(Poor Naming 방지 — 클래스 이름이 거짓말하지 않게).
 * [리팩토링] Move Method — 타임스탬프 포맷팅("yyyy-MM-dd HH:mm:ss")과 "[시각] ACTION id=X by=Y"
 * 문자열 조립을 세 서비스에서 여기로 모았다(docs/4-21 "동일 중복" #1). 출력되는 최종 문자열은
 * 기존과 동일("[AUDIT] [시각] ACTION id=X by=Y") — 조립 위치만 옮겼을 뿐 콘솔 출력 결과는 그대로다.
 */
@Component
public class ConsoleAuditLogger implements AuditLogger {

    private static final DateTimeFormatter TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Override
    public void write(String action, Long id, Long userId) {
        // 실제 서비스라면 파일/DB에 append하겠지만, 이 실습에서는 콘솔 출력이 곧 실제 동작이다.
        String now = LocalDateTime.now().format(TIMESTAMP_FORMAT);
        System.out.println("[AUDIT] [" + now + "] " + action + " id=" + id + " by=" + userId);
    }
}
