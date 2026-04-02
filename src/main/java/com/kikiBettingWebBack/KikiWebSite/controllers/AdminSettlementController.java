package com.kikiBettingWebBack.KikiWebSite.controllers;

import com.kikiBettingWebBack.KikiWebSite.services.PlacedBetService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

/**
 * Admin-only settlement endpoints.
 *
 * Called after game results are confirmed to pay out winning bets
 * and mark losing bets as settled.
 */
@RestController
@RequestMapping("/api/admin/settlement")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminSettlementController {

    private final PlacedBetService placedBetService;

    /**
     * POST /api/admin/settlement/booking-codes/{bookingCodeId}
     *
     * Trigger settlement for all PENDING bets on a booking code.
     *
     * The service will:
     *   - Compare each game's actual result against the pre-set pick
     *   - Mark the bet WON if all picks were correct, LOST otherwise
     *   - Credit winning payouts to user wallets immediately
     *
     * Precondition: all games on the booking code must have a result recorded.
     * The endpoint will return 409 CONFLICT if any game is still pending a result.
     */
    @PostMapping("/booking-codes/{bookingCodeId}")
    public ResponseEntity<Map<String, String>> settleBookingCode(
            @PathVariable UUID bookingCodeId,
            @AuthenticationPrincipal UUID adminId
    ) {
        placedBetService.settleBetsForBookingCode(bookingCodeId, adminId);
        return ResponseEntity.ok(Map.of(
                "status", "settled",
                "message", "All pending bets for this booking code have been settled."
        ));
    }
}