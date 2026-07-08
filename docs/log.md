# 작업 로그

코드 변경 작업을 시간순으로 기록하는 파일. 매 변경 단계마다 변경 전/후 코드를 붙여 이어서 추가한다(append-only).

## 2026-07-08 15:2x — 이름 개선 (d/u/s/proc 제거)

`ApprovalService.java` 지역변수 `d`→`approval`, `u`→`actor`, `s`→`status`, `proc` 임시변수 삭제(`action` 파라미터 직접 비교). `statusLabel(Approval d)`/`amountGrade(Approval d)` 파라미터도 `approval`로 통일.

**변경 전**
```java
Approval d = repo.findById(id).orElse(null);   // d = 결재 문서(Approval 객체)
if (d == null) {
    return;
}
User u = userRepo.findById(userId).orElse(null);   // u = 사용자(User 객체)
if (u == null) {
    return;
}

int s = d.getStatus();     // s = status(상태): 0 임시저장·1 상신·2 승인·3 반려·9 취소  [스멜9: 한 글자라 의미 불명]
int proc = action;          // proc = action(처리 구분): 1 상신·2 승인·3 반려·9 취소  [스멜9: action 을 다른 약어로 또 담음, 의미 없음]
```

**변경 후**
```java
Approval approval = repo.findById(id).orElse(null);
if (approval == null) {
    return;
}
User actor = userRepo.findById(userId).orElse(null);
if (actor == null) {
    return;
}

ApprovalStatus status = approval.getStatus();
```

## 2026-07-08 15:3x — Approval.status: int → ApprovalStatus enum

신규 `ApprovalStatus.java`(`DRAFT(0)/SUBMITTED(1)/APPROVED(2)/REJECTED(3)/CANCELED(9)`, 라벨도 enum이 소유) + `ApprovalStatusConverter.java`(`AttributeConverter`, DB엔 정수 그대로). `Approval.java`는 `@Convert` + `@Column(nullable=false)`로 필드 타입 변경. **API 응답 형식 보존**을 위해 `getCode()`에 `@JsonValue`(안 붙이면 Jackson이 `"status":"APPROVED"`처럼 문자열로 직렬화해 불변 규칙 위반).

**변경 전 (`ApprovalService.processApproval` 상신 분기)**
```java
if (proc == 1) {            // proc==1 → 상신 (숫자 1을 외워야 의미를 앎)
    // 상신: 임시저장(0)일 때만 가능
    if (s == 0) {           // s==0 → 임시저장 상태일 때만
        if (d.getType() == 1 && d.getAmount() >= 1000000) {
            d.setPriority(3);
        }
        d.setStatus(1);   // 1 = 상신 (SUBMITTED)
        d.setUpdatedAt(LocalDateTime.now());
        repo.save(d);
```

**변경 후**
```java
if (action == 1) {
    if (status == ApprovalStatus.DRAFT) {
        if (approval.getType() == 1 && approval.getAmount() >= 1000000) {
            approval.setPriority(3);
        }
        approval.setStatus(ApprovalStatus.SUBMITTED);
        approval.setUpdatedAt(LocalDateTime.now());
        repo.save(approval);
```

**`statusLabel()` — 변경 전**
```java
public String statusLabel(Approval d) {
    int s = d.getStatus();
    String tmp;
    if (s == 0) tmp = "임시저장";
    else if (s == 1) tmp = "상신";
    else if (s == 2) tmp = "승인";
    else if (s == 3) tmp = "반려";
    else if (s == 9) tmp = "취소";
    else tmp = "알수없음";
    return tmp;
}
```

**변경 후**
```java
public String statusLabel(Approval approval) {
    return approval.getStatus().label();
}
```

검증: 테스트 6개 green, 앱 재기동 후 실제 API 호출(생성→상신→승인)로 JSON `status`가 `0→1→2` 정수로 그대로 나옴을 확인. 커밋: `cd391fe`

## 2026-07-08 16:0x — processApproval 남은 매직넘버 정리

신규 `ApprovalAction.java`(`SUBMIT(1)/APPROVE(2)/REJECT(3)/CANCEL(9)`) — 공개 시그니처(`processApproval(id, userId, action, reason)`)는 계약 변경 금지라 `int` 그대로 두고, 메서드 내부에서만 `fromCode(action)`으로 변환. 알 수 없는 코드는 예외 대신 `null` 반환(레거시 "조용한 무시" 보존). `User.isManagerOrAbove()`로 `role>=2` 판정 이동. `type==1`/`amount>=1000000`/`priority=3`은 이름 있는 상수로 추출.

**변경 전**
```java
// [스멜2][스멜3] 거대한 if-지옥. 상태 전이 규칙이 action 값 비교로 흩어져 있다.
if (action == 1) {            // action==1 → 상신 (숫자 1을 외워야 의미를 앎)
    // 상신: 임시저장일 때만 가능
    if (status == ApprovalStatus.DRAFT) {
        // [스멜6] 금액 기준 결재자 자동 상향 — 도메인 규칙이 서비스에 박혀 있다.
        if (approval.getType() == 1 && approval.getAmount() >= 1000000) {   // type 1=지출·2=휴가·3=구매·4=기타 → type==1(지출) && 100만원↑
            approval.setPriority(3);   // 3 = 높음
        }
        approval.setStatus(ApprovalStatus.SUBMITTED);
        approval.setUpdatedAt(LocalDateTime.now());
```
```java
} else if (action == 2) {     // action==2 → 승인
    // 승인: 상신 상태 + 본인이 결재자 + 권한(role>=2) 일 때만
    if (status == ApprovalStatus.SUBMITTED) {
        if (approval.getApproverId() != null && approval.getApproverId().equals(userId)) {
            if (actor.getRole() >= 2) {   // role 1=사원·2=팀장·3=임원 (role>=2 승인권한)  [스멜3: 숫자로 권한 판정]
                approval.setStatus(ApprovalStatus.APPROVED);
```

**변경 후**
```java
// 공개 시그니처는 int action 그대로 유지(계약 변경 금지) — 여기서만 enum으로 변환해 비교한다.
// 알 수 없는 코드는 fromCode()가 null을 반환 → 아래 어떤 분기에도 안 걸려 조용히 무시(레거시 동작 보존).
ApprovalAction requestedAction = ApprovalAction.fromCode(action);

// [스멜2] 거대한 if-지옥. 상태 전이 규칙이 분기로 흩어져 있다.
if (requestedAction == ApprovalAction.SUBMIT) {
    // 상신: 임시저장일 때만 가능
    if (status == ApprovalStatus.DRAFT) {
        // [스멜6] 금액 기준 결재자 자동 상향 — 도메인 규칙이 서비스에 박혀 있다.
        if (approval.getType() == EXPENSE_TYPE && approval.getAmount() >= HIGH_PRIORITY_AMOUNT_THRESHOLD) {
            approval.setPriority(HIGH_PRIORITY);
        }
        approval.setStatus(ApprovalStatus.SUBMITTED);
        approval.setUpdatedAt(LocalDateTime.now());
```
```java
} else if (requestedAction == ApprovalAction.APPROVE) {
    // 승인: 상신 상태 + 본인이 결재자 + 권한(팀장 이상) 일 때만
    if (status == ApprovalStatus.SUBMITTED) {
        if (approval.getApproverId() != null && approval.getApproverId().equals(userId)) {
            if (actor.isManagerOrAbove()) {
                approval.setStatus(ApprovalStatus.APPROVED);
```

**상수 선언 (`ApprovalService` 필드)**
```java
private static final int EXPENSE_TYPE = 1;                        // Approval.type: 1=지출
private static final long HIGH_PRIORITY_AMOUNT_THRESHOLD = 1_000_000L;
private static final int HIGH_PRIORITY = 3;                       // Approval.priority: 3=높음
```

**`User.java` — 추가된 도메인 메서드**
```java
public boolean isManagerOrAbove() {
    return role >= 2;   // role: 1=사원·2=팀장·3=임원
}
```

**의도적으로 하지 않은 것**: `Approval.type`/`priority` 필드 자체는 enum으로 바꾸지 않음 — `create()`가 외부 입력을 검증 없이 그대로 저장하는 레거시 동작이 있어서, 완전 enum화하면 "잘못된 값도 조용히 통과하던" 동작이 예외로 바뀔 위험이 있기 때문(상수 추출까지만 진행). `NoticeService`의 동일한 `role>=2` 판정은 이번 범위 밖(추후 정리 대상).

검증: 테스트 6개 재실행 green. 실제 API로 알 수 없는 action(`99`) 호출 시 `HTTP 200` + 상태 변화 없음(레거시 동작 그대로) 확인.

## 2026-07-08 16:3x — processApproval() 분해 (God Class + Long Method 해소, docs/4-12 백로그 5번)

`docs/4-7`에서 86줄·중첩 깊이 6으로 측정됐던 `processApproval()`을 guard clause + 메서드 추출로 분해. 공개 시그니처(`processApproval(id, userId, action, reason)`)와 관찰 가능한 동작은 그대로 유지.

**변경 전 — 승인 분기만 예시 (3중 중첩)**
```java
} else if (requestedAction == ApprovalAction.APPROVE) {
    // 승인: 상신 상태 + 본인이 결재자 + 권한(팀장 이상) 일 때만
    if (status == ApprovalStatus.SUBMITTED) {
        if (approval.getApproverId() != null && approval.getApproverId().equals(userId)) {
            if (actor.isManagerOrAbove()) {
                approval.setStatus(ApprovalStatus.APPROVED);
                approval.setUpdatedAt(LocalDateTime.now());
                repo.save(approval);
                User drafter = userRepo.findById(approval.getDrafterId()).orElse(null);
                if (drafter != null) {
                    String body = "안녕하세요 " + drafter.getName() + "님,\n"
                            + "결재가 승인되었습니다.\n제목: " + approval.getTitle();
                    mail.send(drafter.getEmail(), "[결재승인] " + approval.getTitle(), body);
                }
                writeAudit("APPROVAL APPROVE", approval.getId(), userId);
            }
        }
    }
}
```

**변경 후 — processApproval은 위임만, approve()는 guard clause로 평탄화**
```java
if (requestedAction == ApprovalAction.SUBMIT) {
    submit(approval, userId);
} else if (requestedAction == ApprovalAction.APPROVE) {
    approve(approval, actor, userId);
} else if (requestedAction == ApprovalAction.REJECT) {
    reject(approval, actor, userId, reason);
} else if (requestedAction == ApprovalAction.CANCEL) {
    cancel(approval, userId);
}
```
```java
private void approve(Approval approval, User actor, Long userId) {
    if (approval.getStatus() != ApprovalStatus.SUBMITTED) {
        return;
    }
    if (approval.getApproverId() == null || !approval.getApproverId().equals(userId)) {
        return;
    }
    if (!actor.isManagerOrAbove()) {
        return;
    }
    approval.setStatus(ApprovalStatus.APPROVED);
    approval.setUpdatedAt(LocalDateTime.now());
    repo.save(approval);

    User drafter = userRepo.findById(approval.getDrafterId()).orElse(null);
    if (drafter != null) {
        String body = "안녕하세요 " + drafter.getName() + "님,\n"
                + "결재가 승인되었습니다.\n제목: " + approval.getTitle();
        mail.send(drafter.getEmail(), "[결재승인] " + approval.getTitle(), body);
    }
    writeAudit("APPROVAL APPROVE", approval.getId(), userId);
}
```

같은 패턴으로 `submit()`/`reject()`/`cancel()`도 추출. 조건 로직은 드모르간 법칙으로 정확히 대응시켰다 — 예: 원래 `if (A && B) { if (C) {...} }` 형태였던 것은 `if (!A || !B) return; if (!C) return;`로, 최종 판정 결과는 100% 동일.

**검증**
- 테스트 6개 green.
- 실제 API로 전체 흐름 재확인: 생성(`status=0`) → 상신(`status=1`, 200만원 지출이라 `priority`도 2→3 자동 상향) → 권한 없는 사용자(id=4) 승인 시도(`HTTP 200`, 상태 변화 없음) → 정상 승인자(id=2) 승인(`status=2`) — 전부 리팩토링 전과 동일하게 동작.

## 2026-07-08 16:3x — 분기를 if-else에서 switch-case로 변경

`processApproval()`의 `requestedAction` 분기를 if-else 체인에서 switch로 교체. **핵심 주의점**: `requestedAction`은 알 수 없는 action 코드일 때 `null`일 수 있는데, 일반 switch는 selector가 `null`이면 `NullPointerException`을 던진다. Java 21의 `case null ->`로 명시적으로 처리해 레거시의 "조용한 무시" 동작을 그대로 보존했다.

**변경 전**
```java
if (requestedAction == ApprovalAction.SUBMIT) {
    submit(approval, userId);
} else if (requestedAction == ApprovalAction.APPROVE) {
    approve(approval, actor, userId);
} else if (requestedAction == ApprovalAction.REJECT) {
    reject(approval, actor, userId, reason);
} else if (requestedAction == ApprovalAction.CANCEL) {
    cancel(approval, userId);
}
```

**변경 후**
```java
switch (requestedAction) {
    case SUBMIT -> submit(approval, userId);
    case APPROVE -> approve(approval, actor, userId);
    case REJECT -> reject(approval, actor, userId, reason);
    case CANCEL -> cancel(approval, userId);
    case null -> { } // 알 수 없는 action → 조용히 무시(레거시 동작 보존)
}
```

**검증**
- 테스트 6개 green.
- 앱 재기동 후 실제 API로 알 수 없는 action(`99`) 호출 → `HTTP 200`, 예외 없음, 상태 변화 없음(변경 전과 동일). `case null`이 없었다면 이 호출은 NPE로 500 에러가 났을 것 — 리팩토링 중 가장 조심해야 했던 지점.
