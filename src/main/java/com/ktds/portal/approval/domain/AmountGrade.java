package com.ktds.portal.approval.domain;

/**
 * [리팩토링] 레거시 ApprovalService.amountGrade()의 금액 등급 규칙을 이 enum으로 이동(Move Method).
 *
 * 금액 등급은 Approval의 데이터(amount)만 보고 정해지는 표현 규칙인데 서비스가 계산하고 있던 것을
 * (Feature Envy, docs/4-8·4-10 "권장 리팩토링" 참고) 이 값 자체가 소유하도록 옮겼다.
 * enum 상수 이름(S/A/B/C)이 곧 레거시가 반환하던 라벨 문자열과 같아서, 필요하면 {@code name()}으로
 * 그대로 꺼내 쓸 수 있다.
 */
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
