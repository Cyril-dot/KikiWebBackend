package com.kikiBettingWebBack.KikiWebSite.controllers;

import com.kikiBettingWebBack.KikiWebSite.dtos.CorrectScoreBookingCodeResponse;
import com.kikiBettingWebBack.KikiWebSite.dtos.CreateCorrectScoreBookingCodeRequest;
import com.kikiBettingWebBack.KikiWebSite.services.CorrectScoreBookingCodeService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * REST controller for correct-score booking codes.
 *
 * Public endpoints:
 *   GET  /api/v1/booking-codes/correct-score/{code}         — load a slip by code
 *
 * Admin-only endpoints (requires ROLE_ADMIN):
 *   POST /api/v1/booking-codes/correct-score                — create a slip
 *   GET  /api/v1/booking-codes/correct-score/admin/mine     — list my slips
 *   PUT  /api/v1/booking-codes/correct-score/{id}/disable   — disable a slip
 */
@RestController
@RequestMapping("/api/v1/booking-codes/correct-score")
@RequiredArgsConstructor
public class CorrectScoreBookingCodeController {

    private final CorrectScoreBookingCodeService correctScoreBookingCodeService;

    // ── Public ───────────────────────────────────────────────────────────────

    /**
     * Load a correct-score booking code slip by its code string.
     * Returns 404 if the code does not exist, 410 if it is expired or disabled.
     */
    @GetMapping("/{code}")
    public ResponseEntity<CorrectScoreBookingCodeResponse> loadByCode(@PathVariable String code) {
        return ResponseEntity.ok(correctScoreBookingCodeService.loadByCode(code));
    }

    // ── Admin ────────────────────────────────────────────────────────────────

    /**
     * Create a new correct-score booking code slip.
     * Every game in the request must include a scorePrediction (e.g. "2-1")
     * and a correctScoreOptionId.
     */
    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<CorrectScoreBookingCodeResponse> createBookingCode(
            @Valid @RequestBody CreateCorrectScoreBookingCodeRequest request,
            @RequestHeader("X-Admin-Id") UUID adminId) {

        CorrectScoreBookingCodeResponse response =
                correctScoreBookingCodeService.createBookingCode(request, adminId);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * List all correct-score booking codes created by the authenticated admin.
     * Supports pagination via ?page=0&size=20&sort=createdAt,desc.
     */
    @GetMapping("/admin/mine")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Page<CorrectScoreBookingCodeResponse>> getMyBookingCodes(
            @RequestHeader("X-Admin-Id") UUID adminId,
            @PageableDefault(size = 20, sort = "createdAt") Pageable pageable) {

        return ResponseEntity.ok(correctScoreBookingCodeService.getMyBookingCodes(adminId, pageable));
    }

    /**
     * Disable a correct-score booking code.
     * Only the admin who created the code may disable it.
     */
    @PutMapping("/{id}/disable")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<CorrectScoreBookingCodeResponse> disableBookingCode(
            @PathVariable UUID id,
            @RequestHeader("X-Admin-Id") UUID adminId) {

        return ResponseEntity.ok(correctScoreBookingCodeService.disableBookingCode(id, adminId));
    }
}