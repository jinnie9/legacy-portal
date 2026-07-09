package com.ktds.portal.common;

/**
 * [리팩토링] 감사 로그 기록 인터페이스 — ConsoleAuditLogger(구 FileAuditLogger)를 직접 new 하던
 * 강결합(Tight Coupling)을 해소하기 위해 추출.
 * 구현은 {@link ConsoleAuditLogger} 하나뿐이지만, 서비스가 인터페이스에 의존하게 되어 출력 대상(파일/DB/콘솔)을
 * 바꿀 때 호출부를 고치지 않아도 된다.
 * [리팩토링] write(String line) → write(action, id, userId)로 시그니처를 좁혔다(docs/4-21 "동일 중복" #1).
 * 이전에는 ApprovalService/NoticeService/ScheduleService 각자가 타임스탬프를 포맷하고
 * "[시각] ACTION id=X by=Y" 문자열을 직접 조립해 완성된 line을 넘겼다 — 포맷을 바꾸려면 6곳을 동시에
 * 고쳐야 하는 중복이었다. 이제 타임스탬프 포맷팅 + 문자열 조립은 구현체({@link ConsoleAuditLogger})
 * 책임이고, 호출부는 "무엇을(action)·무엇에 대해(id)·누가(userId)"만 넘긴다.
 */
public interface AuditLogger {
    void write(String action, Long id, Long userId);
}
