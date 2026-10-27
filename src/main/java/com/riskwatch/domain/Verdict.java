package com.riskwatch.domain;

// 판정과 HomeFin 호환 위험 등급 (업무 명세 4장)
public enum Verdict {
    // 적격
    ELIGIBLE("LOW"),
    // 검토 필요
    REVIEW("MEDIUM"),
    // 부적격
    INELIGIBLE("HIGH");

    private final String riskLevel;

    Verdict(String riskLevel) {
        this.riskLevel = riskLevel;
    }

    public String riskLevel() {
        return riskLevel;
    }
}
