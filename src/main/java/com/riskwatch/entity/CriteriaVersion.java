package com.riskwatch.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

// 심사 기준 버전 (만든 뒤 바뀌지 않음, 업무 명세 5장)
@Entity
@Table(name = "criteria_versions")
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class CriteriaVersion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 버전 이름 (예: 2026-10-26)
    @Column(nullable = false, updatable = false)
    private String version;

    // 이 시각부터 평가에 쓰임 (UTC)
    @Column(name = "effective_from", nullable = false, updatable = false)
    private LocalDateTime effectiveFrom;

    @Column(name = "created_by", nullable = false, updatable = false)
    private String createdBy;

    @Column(nullable = false, updatable = false)
    private String reason;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
