package com.ktds.portal.approval.domain;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * [리팩토링] ApprovalStatus enum ↔ DB 정수 매핑.
 *
 * {@code @Enumerated(ORDINAL)} 대신 이 컨버터를 쓰는 이유: ORDINAL은 enum 선언 순서(0,1,2,3,4)를
 * 저장하는데 CANCELED=9 와 어긋난다. {@code @Enumerated(STRING)}은 DB에 문자열을 저장해
 * 레거시 정수 저장값(불변 규칙: DB 저장값 변경 금지)을 깬다. 그래서 코드값을 직접 매핑하는
 * AttributeConverter를 쓴다.
 */
@Converter
public class ApprovalStatusConverter implements AttributeConverter<ApprovalStatus, Integer> {

    @Override
    public Integer convertToDatabaseColumn(ApprovalStatus status) {
        return status == null ? null : status.getCode();
    }

    @Override
    public ApprovalStatus convertToEntityAttribute(Integer code) {
        return code == null ? null : ApprovalStatus.fromCode(code);
    }
}
