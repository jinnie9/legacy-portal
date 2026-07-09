package com.ktds.portal.user;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;

/**
 * 사용자 엔티티.
 * [스멜] role 이 int. 1=사원, 2=팀장, 3=임원 — 의미가 코드 곳곳에 매직넘버로 흩어진다.
 * [리팩토링] role>=2("팀장 이상") 권한 판정을 {@link #isManagerOrAbove()}로 이동(Rich Domain).
 *   ApprovalService.processApproval()의 승인·반려 분기에 있던 중복 판정을 여기로 모았다.
 *   role 필드 자체는 아직 int 그대로 — NoticeService 등 다른 곳의 동일 판정은 이번 범위 밖(추후 정리 대상).
 */
@Entity
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;
    private String email;
    private int role;       // 1=사원 2=팀장 3=임원 (서비스의 role>=2 판정에 쓰이는 매직넘버 → enum 후보)
    private String dept;

    public User() {}

    public User(String name, String email, int role, String dept) {
        this.name = name;
        this.email = email;
        this.role = role;
        this.dept = dept;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public int getRole() { return role; }
    public void setRole(int role) { this.role = role; }
    public String getDept() { return dept; }
    public void setDept(String dept) { this.dept = dept; }

    // [리팩토링 유의] Jackson은 isXxx() 메서드를 bean getter로 인식해 "managerOrAbove" 필드를
    // 자동으로 JSON에 노출시킨다 — /api/users 응답 형식이 바뀌는 것(불변 규칙 위반)이므로 @JsonIgnore로 막는다.
    @JsonIgnore
    public boolean isManagerOrAbove() {
        return role >= 2;   // role: 1=사원·2=팀장·3=임원
    }
}
