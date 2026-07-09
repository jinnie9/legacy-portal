package com.ktds.portal.common;

/**
 * [리팩토링] 감사 로그 기록 인터페이스 — FileAuditLogger를 직접 new 하던 강결합(Tight Coupling)을 해소하기 위해 추출.
 * 구현은 {@link FileAuditLogger} 하나뿐이지만, 서비스가 인터페이스에 의존하게 되어 출력 대상(파일/DB/콘솔)을
 * 바꿀 때 호출부를 고치지 않아도 된다.
 */
public interface AuditLogger {
    void write(String line);
}
