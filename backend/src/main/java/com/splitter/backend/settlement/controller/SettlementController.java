package com.splitter.backend.settlement.controller;

import java.util.List;
import java.util.UUID;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.splitter.backend.settlement.dto.CreateSettlementRequest;
import com.splitter.backend.settlement.dto.RejectSettlementRequest;
import com.splitter.backend.settlement.dto.SettlementResponse;
import com.splitter.backend.settlement.service.SettlementService;

@RestController
@RequestMapping("/api/settlements")
public class SettlementController {

    private final SettlementService settlementService;

    public SettlementController(SettlementService settlementService) {
        this.settlementService = settlementService;
    }

    @PostMapping
    public SettlementResponse createSettlement(
            @RequestBody CreateSettlementRequest request,
            Authentication authentication) {
        return settlementService.createSettlement(authentication.getName(), request);
    }

    @PostMapping("/{settlementId}/confirm")
    public SettlementResponse confirmSettlement(
            @PathVariable UUID settlementId,
            Authentication authentication) {
        return settlementService.confirmSettlement(settlementId, authentication.getName());
    }

    @PostMapping("/{settlementId}/reject")
    public SettlementResponse rejectSettlement(
            @PathVariable UUID settlementId,
            @RequestBody(required = false) RejectSettlementRequest request,
            Authentication authentication) {
        String reason = request == null ? null : request.reason();
        return settlementService.rejectSettlement(settlementId, authentication.getName(), reason);
    }

    @GetMapping("/pending/incoming")
    public List<SettlementResponse> getIncomingPending(Authentication authentication) {
        return settlementService.getIncomingPendingSettlements(authentication.getName());
    }

    @GetMapping("/pending/outgoing")
    public List<SettlementResponse> getOutgoingPending(Authentication authentication) {
        return settlementService.getOutgoingPendingSettlements(authentication.getName());
    }

    @GetMapping("/groups/{groupId}")
    public List<SettlementResponse> getGroupSettlementHistory(
            @PathVariable UUID groupId,
            Authentication authentication) {
        return settlementService.getGroupSettlementHistory(groupId, authentication.getName());
    }
}
