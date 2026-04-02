package com.kikiBettingWebBack.KikiWebSite.controllers;

import com.kikiBettingWebBack.KikiWebSite.Config.Security.UserPrincipal;
import com.kikiBettingWebBack.KikiWebSite.dtos.*;
import com.kikiBettingWebBack.KikiWebSite.services.BookingCodeService;
import com.kikiBettingWebBack.KikiWebSite.services.PlacedBetService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * User-facing endpoints for the booking code betting flow.
 *
 * Flow:
 *   1. GET  /api/bets/slip/{code}   → load slip (see games + picks + odds)
 *   2. POST /api/bets/place         → enter stake, place bet
 *   3. GET  /api/bets/my            → view bet history
 *   4. GET  /api/bets/{reference}   → view single bet by reference
 */
@RestController
@RequestMapping("/api/bets")
@RequiredArgsConstructor
@PreAuthorize("hasRole('USER')")
public class BetController {

    private final BookingCodeService bookingCodeService;
    private final PlacedBetService placedBetService;

    /**
     * GET /api/bets/slip/{code}
     *
     * Load a booking code slip by its code string.
     * Returns all games, pre-set picks, locked odds, and combined odds.
     * No authentication required — anyone with the code can preview the slip.
     */
    @GetMapping("/slip/{code}")
    @PreAuthorize("permitAll()")
    public ResponseEntity<BookingCodeResponse> loadSlip(@PathVariable String code) {
        return ResponseEntity.ok(bookingCodeService.loadByCode(code));
    }

    /**
     * POST /api/bets/place
     *
     * Place a bet against a booking code.
     * Requires the user to be authenticated and have sufficient wallet balance.
     *
     * Body: { bookingCode: "AXKP-7BM2", stake: 20.00 }
     *
     * Returns: bet reference, potential payout, remaining balance.
     */
    @PostMapping("/place")
    public ResponseEntity<PlacedBetResponse> placeBet(
            @Valid @RequestBody NewPlaceBetRequest request,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        UUID userId = principal.getUserId();
        return ResponseEntity.ok(placedBetService.placeBet(request, userId));
    }

    @GetMapping("/my")
    public ResponseEntity<Page<PlacedBetResponse>> getMyBets(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @PageableDefault(size = 10) Pageable pageable
    ) {
        UUID userId = userPrincipal.getUserId();
        return ResponseEntity.ok(placedBetService.getMyBets(userId, pageable));
    }

    @GetMapping("/{reference}")
    public ResponseEntity<PlacedBetResponse> getBetByReference(
            @PathVariable String reference,
            @AuthenticationPrincipal UserPrincipal userPrincipal
    ) {
        UUID userId = userPrincipal.getUserId();
        return ResponseEntity.ok(placedBetService.getBetByReference(reference, userId));
    }
}