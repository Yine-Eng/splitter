package com.splitter.backend.balance.controller;

import com.splitter.backend.balance.dto.BalanceResponse;
import com.splitter.backend.balance.service.BalanceService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/groups")
public class BalanceController {

    private final BalanceService balanceService;

    public BalanceController(BalanceService balanceService) {
        this.balanceService = balanceService;
    }

    @GetMapping("/{groupId}/balances")
    public BalanceResponse getGroupBalances(
            @PathVariable UUID groupId,
            Authentication authentication
    ) {
        String username = authentication.getName();
        return balanceService.getGroupBalances(groupId, username);
    }
}
