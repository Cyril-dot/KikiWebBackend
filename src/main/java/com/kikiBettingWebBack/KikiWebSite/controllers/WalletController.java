package com.kikiBettingWebBack.KikiWebSite.controllers;

import com.kikiBettingWebBack.KikiWebSite.services.WalletService;
import com.kikiBettingWebBack.KikiWebSite.dtos.DepositInitiateRequest;
import com.kikiBettingWebBack.KikiWebSite.dtos.DepositInitiateResponse;
import com.kikiBettingWebBack.KikiWebSite.dtos.WithdrawRequest;
import com.kikiBettingWebBack.KikiWebSite.dtos.WithdrawResponse;
import com.kikiBettingWebBack.KikiWebSite.dtos.TransactionResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
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
    public ResponseEntity<DepositInitiateResponse> initiateDeposit(
            @AuthenticationPrincipal UUID userId,
            @RequestBody DepositInitiateRequest request) {
        return ResponseEntity.ok(walletService.initiateDeposit(userId, request));
    }

    // THIS IS THE FIX — frontend calls this after Paystack redirects back
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
    public ResponseEntity<WithdrawResponse> withdraw(
            @AuthenticationPrincipal UUID userId,
            @RequestBody WithdrawRequest request) {
        return ResponseEntity.ok(walletService.withdraw(userId, request));
    }

    @GetMapping("/transactions")
    public ResponseEntity<List<TransactionResponse>> getTransactions(
            @AuthenticationPrincipal UUID userId) {
        return ResponseEntity.ok(walletService.getTransactionHistory(userId));
    }
}