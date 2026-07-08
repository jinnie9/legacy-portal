# 스멜 종합 목록 (legacy-portal)

`4-4`~`4-10` 조사 문서를 종합한 마스터 체크리스트. 심각도 순으로 정렬했으며, 리팩토링 진행 시 이 목록을 갱신해 나간다.
심각도 판단 기준과 순위별 근거는 [`4-10. 도출 스멜 - 심각도 표.md`](4-10.%20도출%20스멜%20-%20심각도%20표.md)를 참고. 각 항목의 상세 근거는 괄호 안 문서를 참고.

## 최상

- [ ] **테스트 부재** — `src/test`에 Java 테스트 클래스 0개. 다른 모든 리팩토링의 선행 조건(특성화 테스트 필요). (4-4)

## 상

- [ ] **God Class + Long Method** — `ApprovalService`가 검증·영속화·메일·감사로그·포맷팅·권한판정을 전부 수행. `processApproval()` 86줄·중첩 깊이 6(승인/반려 분기). (4-4, 4-7)
- [ ] **Primitive Obsession / 매직넘버** — `Approval.status`(0/1/2/3/9), `Approval.type`(1-4), `Approval.priority`(1-3), `Notice.status`(0/1/9), `Notice.category`(1-3), `Schedule.status`(0/1/9), `User.role`(1-3), `processApproval`의 `action` 파라미터(1/2/3/9, status값과 우연히 일치)가 전부 `int`. (4-8)

## 중상

- [ ] **중복 코드 — 감사 로그 포맷팅** — 날짜 포맷 + `audit.write`, 6곳(Approval 2·Notice 2·Schedule 2). `ApprovalService.create()`는 자체 헬퍼(`writeAudit`)조차 안 쓰고 또 복붙. (4-5)
- [ ] **중복 코드 — 메일 본문 조립** — "안녕하세요 …님," 인사말 + `mail.send`, 4곳(결재 상신/승인/반려, 공지 긴급메일). (4-5)
- [ ] **중복 코드 — 조회 후 조용히 return** — 엔티티·사용자 null 체크 패턴, 3곳(processApproval/publish/confirm). (4-5)
- [ ] **중복 코드 — `role >= 2` 권한 판정** — 완전 동일 조건 하드코딩, 3곳(승인/반려/공지게시). (4-5)
- [ ] **중복 코드 — 상태값→한글 라벨 변환** — Approval/Notice에 있고 Schedule엔 없음(비대칭). (4-5)
- [ ] **중복 코드 — 본인 확인 패턴** — 4곳, `ScheduleService.confirm()`만 null 가드 누락(미세한 불일치 이미 발생). (4-5)
- [ ] **중복 코드 — `setUpdatedAt` 갱신** — `ApprovalService` 내부 5곳. (4-5)

## 중

- [ ] **강결합 (직접 `new`, DIP 위반)** — `SmtpMailSender`·`FileAuditLogger`를 생성자 주입 없이 직접 생성, 5곳(Approval 2·Notice 2·Schedule 1). 인터페이스 자체가 없어 테스트 대체 불가. (4-9)
- [ ] **Feature Envy** — `ApprovalService`가 `Approval` 데이터를 꺼내 금액 등급(`amountGrade`)·우선순위 자동 상향을 계산. 100만원 임계값이 두 메서드에 중복 하드코딩. (4-8)
- [ ] **DTO 부재** — `ApprovalController`가 `Map<String, Object>`를 그대로 캐스팅해서 사용(오타 키·잘못된 타입이 런타임에 터짐). (4-4)
- [ ] **Long Parameter List** — `ApprovalService.create()` 파라미터 8개. (4-4, `ApprovalService` 헤더)

## 하

- [ ] **Poor Naming** — `d`/`u`/`s`/`proc`/`tmp`/`flag1`/`sc`/`n` 등 약어·의미 불명 변수명. `ApprovalService`·`ScheduleService` 전반.
- [ ] **Comment Smell** — 나쁜 이름을 주석으로 변명(위 Poor Naming과 동일 지점 — 이름을 고치면 자동 해소).

## 참고 (스멜 아님 / 손대면 안 됨)

- **조용한 실패** (권한 없음·상태 불일치 시 예외 없이 무시) — `processApproval`/`publish`/`confirm`. CLAUDE.md 기준 **리팩토링 단계에서 동작 변경 금지**, 별도 재설계 단계 대상.
- **구조적 비일관성** — `notice`/`schedule`은 Controller 없음, `user`는 Service 없음. 미완성 기능이지 스멜이 아님. (4-4)

## DB 저장 규칙 (리팩토링 시 항상 유의)

위 매직넘버 필드를 enum화하더라도 DB에는 기존 정수를 그대로 저장해야 한다. `@Convert` + `AttributeConverter` 사용, `@Enumerated(STRING/ORDINAL)` 금지(순번이 `9`처럼 어긋나는 값과 맞지 않음).
