package com.kikiBettingWebBack.KikiWebSite.controllers;

import com.kikiBettingWebBack.KikiWebSite.dtos.UserBetHistoryResponse;
import com.kikiBettingWebBack.KikiWebSite.services.BetHistoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;
@RestController
@RequestMapping("/api/v1/bets")  // fix: add v1, and broaden the base path
@RequiredArgsConstructor
@PreAuthorize("hasRole('USER')")
public class BetHistoryController {

    private final BetHistoryService betHistoryService;

    @GetMapping("/history")
    public ResponseEntity<UserBetHistoryResponse> getMyFullBetHistory(
            @AuthenticationPrincipal UUID userId) {
        return ResponseEntity.ok(betHistoryService.getFullBetHistory(userId));
    }
}
