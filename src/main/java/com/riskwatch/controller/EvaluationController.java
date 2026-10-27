package com.riskwatch.controller;

import com.riskwatch.common.ApiResponse;
import com.riskwatch.common.TraceIds;
import com.riskwatch.dto.evaluation.EvaluationRequest;
import com.riskwatch.dto.evaluation.EvaluationView;
import com.riskwatch.security.AuthPrincipal;
import com.riskwatch.service.EvaluationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

// 평가 API: 요청은 HomeFin·FinFlow 서버(서비스 토큰)만, 조회는 요청한 서버·운영자
@RestController
@RequiredArgsConstructor
public class EvaluationController {

    private final EvaluationService evaluationService;

    // 평가 (201, 같은 입력을 다시 보내면 새 평가 기록)
    @PostMapping("/api/evaluations")
    public ResponseEntity<ApiResponse<EvaluationView>> evaluate(@Valid @RequestBody EvaluationRequest request,
            @AuthenticationPrincipal AuthPrincipal caller) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(evaluationService.evaluate(request, caller), TraceIds.current()));
    }

    // 평가 조회 (다른 서버의 평가는 404)
    @GetMapping("/api/evaluations/{evaluationId}")
    public ResponseEntity<ApiResponse<EvaluationView>> find(@PathVariable String evaluationId,
            @AuthenticationPrincipal AuthPrincipal caller) {
        return ResponseEntity.ok(ApiResponse.success(evaluationService.find(evaluationId, caller),
                TraceIds.current()));
    }
}
