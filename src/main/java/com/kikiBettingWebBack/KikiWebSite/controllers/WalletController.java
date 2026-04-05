package com.kikiBettingWebBack.KikiWebSite.controllers;

import com.kikiBettingWebBack.KikiWebSite.Config.ApiResponse;
import com.kikiBettingWebBack.KikiWebSite.Config.Security.UserPrincipal;
import com.kikiBettingWebBack.KikiWebSite.services.WalletService;
import com.kikiBettingWebBack.KikiWebSite.dtos.DepositInitiateRequest;
import com.kikiBettingWebBack.KikiWebSite.dtos.DepositInitiateResponse;
import com.kikiBettingWebBack.KikiWebSite.dtos.WithdrawRequest;
import com.kikiBettingWebBack.KikiWebSite.dtos.WithdrawResponse;
import com.kikiBettingWebBack.KikiWebSite.dtos.TransactionResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/wallet")
@RequiredArgsConstructor
public class WalletController {

    private final WalletService walletService;

    @PostMapping("/deposit")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<DepositInitiateResponse> initiateDeposit(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @RequestBody DepositInitiateRequest request) {
        UUID userId = userPrincipal.getUserId();
        return ResponseEntity.ok(walletService.initiateDeposit(userId, request));
    }

    @GetMapping("/deposit/verify/{reference}")
    public ResponseEntity<Map<String, String>> verifyDeposit(
            @PathVariable String reference) {
        String result = walletService.verifyDeposit(reference);
        return ResponseEntity.ok(Map.of("status", result));
    }

    @PostMapping("/paystack/webhook")
    public ResponseEntity<Void> handleWebhook(
            @RequestBody String payload,
            @RequestHeader("x-paystack-signature") String signature) {
        walletService.handlePaystackWebhook(payload, signature);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/withdraw")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<WithdrawResponse> withdraw(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @RequestBody WithdrawRequest request) {
        UUID userId = userPrincipal.getUserId();
        return ResponseEntity.ok(walletService.withdraw(userId, request));
    }

    @GetMapping("/transactions")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<List<TransactionResponse>> getTransactions(
            @AuthenticationPrincipal UserPrincipal userPrincipal) {
        UUID userId = userPrincipal.getUserId();
        return ResponseEntity.ok(walletService.getTransactionHistory(userId));
    }
}