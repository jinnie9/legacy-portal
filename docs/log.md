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

## 2026-07-09 10:2x — approval 패키지를 계층별 하위 패키지로 분리

`com.ktds.portal.approval` 평면 패키지를 `controller`/`service`/`domain`/`repository` 4개 하위 패키지로 분리. `git mv`로 이동해 히스토리 보존, 클래스명·엔드포인트·DB 저장값은 전부 그대로.

**이동 내역**
```
approval/ApprovalController.java        → approval/controller/ApprovalController.java
approval/ApprovalService.java           → approval/service/ApprovalService.java
approval/Approval.java                  → approval/domain/Approval.java
approval/ApprovalStatus.java            → approval/domain/ApprovalStatus.java
approval/ApprovalAction.java            → approval/domain/ApprovalAction.java
approval/ApprovalStatusConverter.java   → approval/domain/ApprovalStatusConverter.java
approval/ApprovalRepository.java        → approval/repository/ApprovalRepository.java
```

각 파일의 `package` 선언을 갱신하고, 계층을 넘나드는 참조에는 import를 추가했다(같은 `domain` 패키지끼리는 import 불필요). 테스트 파일(`ApprovalServiceCharacterizationTest`)은 패키지 이동 없이 `com.ktds.portal.approval`에 남기고 새 하위 패키지 import만 추가.

**뜻밖의 발견 — `User.isManagerOrAbove()`가 API 응답을 바꾸고 있었다**

패키지 이동 후 앱을 재기동해 스모크 테스트하던 중, `/api/users` 응답에 원래 없던 `"managerOrAbove":true/false` 필드가 나오는 것을 발견했다. 원인: Jackson이 `isXxx()` 형태의 메서드를 bean getter로 인식해 자동으로 JSON에 노출시킨다 — 이전 커밋(`bcb3983`, User에 `isManagerOrAbove()` 추가)에서 이미 발생했지만 그때는 `/api/users` 엔드포인트를 스모크 테스트하지 않아 놓쳤던 회귀다.

**변경 전**
```java
public boolean isManagerOrAbove() {
    return role >= 2;   // role: 1=사원·2=팀장·3=임원
}
```

**변경 후**
```java
// [리팩토링 유의] Jackson은 isXxx() 메서드를 bean getter로 인식해 "managerOrAbove" 필드를
// 자동으로 JSON에 노출시킨다 — /api/users 응답 형식이 바뀌는 것(불변 규칙 위반)이므로 @JsonIgnore로 막는다.
@JsonIgnore
public boolean isManagerOrAbove() {
    return role >= 2;   // role: 1=사원·2=팀장·3=임원
}
```

**검증**
- 테스트 6개 green(패키지 이동 후, `@JsonIgnore` 추가 후 둘 다 재확인).
- 앱 재기동 후 `/api/approvals`·`/api/users` 실제 호출로 엔드포인트·응답 형식 불변 확인.
- `@JsonIgnore` 적용 전: `/api/users` 응답에 `managerOrAbove` 필드 존재(회귀) → 적용 후: 원래 4개 필드(`id/name/email/role/dept`)만 남음, 정상 복구.

## 2026-07-09 11:0x — ApprovalController의 Map<String,Object>를 record DTO로 교체

`approval.dto` 패키지 신설: `CreateApprovalRequest`(생성 요청), `ProcessRequest`(처리 요청), `ApprovalResponse`(응답, Entity 비노출). JSON 필드명·구조·`status`의 정수 표현은 레거시와 동일하게 유지(계약 보존).

**변경 전**
```java
@PostMapping
public Approval create(@RequestBody Map<String, Object> body) {
    return service.create(
            (String) body.get("title"),
            (String) body.get("content"),
            (int) body.get("type"),
            (int) body.get("priority"),
            ((Number) body.get("drafterId")).longValue(),
            ((Number) body.get("approverId")).longValue(),
            ((Number) body.getOrDefault("amount", 0)).longValue(),
            (boolean) body.getOrDefault("urgent", false)
    );
}
```

**변경 후**
```java
@PostMapping
public ApprovalResponse create(@RequestBody CreateApprovalRequest request) {
    Approval approval = service.create(
            request.title(), request.content(), request.type(), request.priority(),
            request.drafterId(), request.approverId(), request.amount(), request.urgent()
    );
    return ApprovalResponse.from(approval);
}
```

**설계 포인트 — 레거시의 "누락 필드 시 크래시" 동작을 최대한 자연스럽게 보존**

- `type`/`priority`(CreateApprovalRequest), `action`(ProcessRequest)은 일부러 **박싱 타입(Integer)**으로 뒀다. 레거시는 `(int) body.get("type")`처럼 캐스팅해서 키가 없으면 NPE로 터졌는데, `Integer` 필드를 `ApprovalService.create(int, ...)`/`processApproval(..., int, ...)`(둘 다 공개 시그니처가 원시 타입 `int`) 호출부에 그대로 넘기면 언박싱 시점에 똑같이 NPE가 나서 실패 방식이 유지된다.
- `drafterId`/`approverId`/`userId`는 레거시도 `Long`으로 전달되긴 했지만, 원래 `.longValue()` 호출 자체에서 NPE가 났던 것과 달리 지금은 `Long` 필드가 그대로 서비스에 전달돼 값이 없으면 조용히 null로 넘어간다 — `ApprovalService`의 공개 시그니처가 이미 박싱 `Long`을 받고 있어서 DTO만으로는 이 차이를 없앨 수 없다(각 record의 클래스 주석에 명시).
- `amount`/`urgent`(long/boolean 기본형)와 `reason`(compact 생성자에서 null → `""` 정규화)은 레거시의 `getOrDefault` 기본값과 동일하게 동작한다.

**검증**
- `mvnw.cmd -q compile`로 컴파일만 확인(테스트 스위트는 미실행 — 사용자 지시 시에만 실행하기로 함).
- 앱 재기동 후 실제 API로 생성→상신 흐름 호출, JSON 응답이 `status`(정수)·전체 필드명 그대로인 것과 우선순위 자동상향(2→3)이 여전히 동작하는 것을 확인.

## 2026-07-09 13:2x — statusLabel/amountGrade를 표현·도메인 규칙으로 Move Method

`docs/4-19`(SRP 분석)의 F축("화면 표시용 문구/등급 기준")을 서비스에서 완전히 제거. 두 메서드 모두 `ApprovalController`나 테스트 어디에서도 호출되지 않는 미사용 public 메서드였음을 grep으로 먼저 확인한 뒤 진행.

- `statusLabel(Approval)`은 이미 `ApprovalStatus.label()`에 위임만 하고 있었으므로(이전 리팩토링), 호출부가 없는 위임 래퍼 자체를 삭제.
- `amountGrade(Approval)`은 신규 `AmountGrade` enum(`domain` 패키지)의 `forAmount(long)` 정적 메서드로 로직을 옮기고, 서비스 메서드는 삭제.

**변경 전**
```java
public String statusLabel(Approval approval) {
    return approval.getStatus().label();
}

public String amountGrade(Approval approval) {
    long a = approval.getAmount();
    if (a >= 10000000) return "S";
    else if (a >= 1000000) return "A";
    else if (a >= 100000) return "B";
    else return "C";
}
```

**변경 후 — ApprovalService에서 완전히 삭제**, 신규 `AmountGrade.java`:
```java
public enum AmountGrade {
    S(10_000_000L),
    A(1_000_000L),
    B(100_000L),
    C(0L);

    private final long minAmount;

    AmountGrade(long minAmount) {
        this.minAmount = minAmount;
    }

    public static AmountGrade forAmount(long amount) {
        for (AmountGrade grade : values()) {
            if (amount >= grade.minAmount) {
                return grade;
            }
        }
        return C;
    }
}
```

enum 상수 이름(S/A/B/C)이 레거시가 반환하던 문자열과 동일해서, 필요하면 `AmountGrade.forAmount(amount).name()`으로 그대로 꺼내 쓸 수 있다.

**검증**
- `mvnw.cmd -q compile`로 컴파일만 확인(테스트 스위트 미실행, 사용자 지시 시에만 실행).
- 두 메서드 모두 호출부가 없어 API 응답에는 영향 없음 — 별도 스모크 테스트 불필요.

## 2026-07-09 13:3x — approve/reject 메일 본문 조립을 Extract Method

`approve()`/`reject()` 안에 인라인으로 있던 메일 본문 문자열 조립을 `approvedBody()`/`rejectedBody()` private 메서드로 추출(서비스 내부에 그대로 유지, 외부 공개 없음).

**변경 전 (approve 발췌)**
```java
User drafter = userRepo.findById(approval.getDrafterId()).orElse(null);
if (drafter != null) {
    String body = "안녕하세요 " + drafter.getName() + "님,\n"
            + "결재가 승인되었습니다.\n제목: " + approval.getTitle();
    mail.send(drafter.getEmail(), "[결재승인] " + approval.getTitle(), body);
}
```

**변경 후**
```java
User drafter = userRepo.findById(approval.getDrafterId()).orElse(null);
if (drafter != null) {
    mail.send(drafter.getEmail(), "[결재승인] " + approval.getTitle(), approvedBody(approval, drafter));
}
```
```java
private String approvedBody(Approval approval, User drafter) {
    return "안녕하세요 " + drafter.getName() + "님,\n"
            + "결재가 승인되었습니다.\n제목: " + approval.getTitle();
}
```

`reject()`도 동일한 방식으로 `rejectedBody(approval, drafter, reason)`을 추출(반려 사유 포함). 문자열 내용은 한 글자도 바뀌지 않았고, 조립 로직의 위치만 옮겼다.

**검증**
- `mvnw.cmd -q compile`로 컴파일만 확인(테스트 스위트 미실행).

## 2026-07-09 13:4x — 메일/감사로그 DI 전환 + 상태전이·권한 규칙을 Approval 도메인으로 이동

두 작업을 한 번에 진행(둘 다 submit/approve/reject/cancel을 건드리므로 따로 하면 같은 코드를 두 번 고치게 됨).

**1) MailSender/AuditLogger 인터페이스 + 생성자 주입 (Tight Coupling 해소)**

신규 `common/MailSender.java`, `common/AuditLogger.java` 인터페이스. `SmtpMailSender`/`FileAuditLogger`가 각각 구현 + `@Component` 등록.

**변경 전**
```java
private final SmtpMailSender mail = new SmtpMailSender();
private final FileAuditLogger audit = new FileAuditLogger();

public ApprovalService(ApprovalRepository repo, UserRepository userRepo) {
    this.repo = repo;
    this.userRepo = userRepo;
}
```

**변경 후**
```java
private final MailSender mail;
private final AuditLogger audit;

public ApprovalService(ApprovalRepository repo, UserRepository userRepo, MailSender mail, AuditLogger audit) {
    this.repo = repo;
    this.userRepo = userRepo;
    this.mail = mail;
    this.audit = audit;
}
```

**2) 상태 전이·권한 규칙을 Approval 도메인 메서드로 이동 (Rich Domain)**

`submit()`/`approve()`/`reject()`/`cancel()`의 상태·본인확인·권한 검증(원래 서비스의 if-지옥)을 `Approval` 엔티티의 `submit()`/`approve(User,Long)`/`reject(User,Long,String)`/`cancel(Long)`로 이동. **위반 시 예외 대신 `false` 반환** — "조건 불충족 시 조용히 무시"하던 레거시 동작을 그대로 보존. 금액 임계값 상수(`EXPENSE_TYPE`/`HIGH_PRIORITY_AMOUNT_THRESHOLD`/`HIGH_PRIORITY`)도 `submit()`을 따라 Approval로 이동(`HIGH_PRIORITY`만 `create()`의 urgent 처리에도 필요해 `public`으로 유지).

**변경 전 (ApprovalService.approve, 발췌)**
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
    ...
}
```

**변경 후**
```java
// Approval.java
public boolean approve(User actor, Long userId) {
    if (status != ApprovalStatus.SUBMITTED) {
        return false;
    }
    if (approverId == null || !approverId.equals(userId)) {
        return false;
    }
    if (!actor.isManagerOrAbove()) {
        return false;
    }
    status = ApprovalStatus.APPROVED;
    updatedAt = LocalDateTime.now();
    return true;
}
```
```java
// ApprovalService.java
private void approve(Approval approval, User actor, Long userId) {
    if (!approval.approve(actor, userId)) {
        return;
    }
    repo.save(approval);
    ...
}
```

`submit()`/`reject()`/`cancel()`도 동일한 패턴. `ApprovalService`는 이제 "도메인 메서드 호출 → 성공하면 저장·메일·감사로그"만 오케스트레이션한다.

**부수 조치**: `ApprovalServiceCharacterizationTest`의 `@Import(ApprovalService.class)`가 이제 `MailSender`/`AuditLogger` 빈도 필요해서, `@DataJpaTest` 슬라이스가 자동 스캔하지 않는 `SmtpMailSender`/`FileAuditLogger`(`@Component`)를 함께 `@Import`하도록 수정(테스트는 실행하지 않음, import만 맞춤).

**검증**
- `mvnw.cmd -q test-compile`로 컴파일만 확인(테스트 스위트 미실행).
- 앱 재기동 후 실제 API로 전체 수명주기 확인: 생성(`status=0`) → 상신(`status=1`, 200만원 지출 우선순위 2→3 자동상향) → 권한없는 사용자(id=4) 승인 시도(`HTTP 200`, 무변화) → 정상 승인(id=2, `status=2`) → 별도 건으로 반려(`status=3`, `rejectReason` 저장) → 별도 건으로 취소(`status=9`) — 전부 리팩토링 전과 동일.
