package com.riskwatch.controller;

import com.riskwatch.common.ApiResponse;
import com.riskwatch.common.TraceIds;
import com.riskwatch.domain.ProductCode;
import com.riskwatch.domain.Verdict;
import com.riskwatch.dto.criteria.NewVersionRequest;
import com.riskwatch.dto.criteria.VersionView;
import com.riskwatch.dto.evaluation.EvaluationListItem;
import com.riskwatch.security.AuthPrincipal;
import com.riskwatch.service.CriteriaAdminService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

// 운영자용 기준표·평가 기록 API (운영자 토큰, ADMIN만). 기준 버전을 바꾸거나 지우는 API는 없음
@RestController
@RequiredArgsConstructor
public class AdminCriteriaController {

    private final CriteriaAdminService criteriaAdminService;

    // 지금 적용 중인 기준
    @GetMapping("/api/admin/criteria")
    public ResponseEntity<ApiResponse<VersionView.Detail>> current() {
        return ResponseEntity.ok(ApiResponse.success(criteriaAdminService.current(), TraceIds.current()));
    }

    // 버전 목록 (최신순)
    @GetMapping("/api/admin/criteria/versions")
    public ResponseEntity<ApiResponse<List<VersionView.Summary>>> versions() {
        return ResponseEntity.ok(ApiResponse.success(criteriaAdminService.versions(), TraceIds.current()));
    }

    // 한 버전 상세 (값 전체 + 이전 버전 대비 바뀐 값)
    @GetMapping("/api/admin/criteria/versions/{version}")
    public ResponseEntity<ApiResponse<VersionView.Detail>> version(@PathVariable String version) {
        return ResponseEntity.ok(ApiResponse.success(criteriaAdminService.version(version), TraceIds.current()));
    }

    // 새 버전 만들기 (201)
    @PostMapping("/api/admin/criteria/versions")
    public ResponseEntity<ApiResponse<VersionView.Detail>> create(@Valid @RequestBody NewVersionRequest request,
            @AuthenticationPrincipal AuthPrincipal actor) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(criteriaAdminService.create(request, actor), TraceIds.current()));
    }

    // 평가 기록 목록 (?productCode=&verdict=&page=0&size=20)
    @GetMapping("/api/admin/evaluations")
    public ResponseEntity<ApiResponse<List<EvaluationListItem>>> evaluations(
            @RequestParam(required = false) ProductCode productCode,
            @RequestParam(required = false) Verdict verdict,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.success(criteriaAdminService.evaluations(productCode, verdict, page, size),
                TraceIds.current()));
    }
}
