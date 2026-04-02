package com.kikiBettingWebBack.KikiWebSite.controllers;

import com.kikiBettingWebBack.KikiWebSite.dtos.*;
import com.kikiBettingWebBack.KikiWebSite.services.BookingCodeService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * Admin-only endpoints for managing booking codes.
 *
 * All routes under /api/admin/** require ROLE_ADMIN.
 */
@RestController
@RequestMapping("/api/admin/booking-codes")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminBookingCodeController {

    private final BookingCodeService bookingCodeService;

    /**
     * POST /api/admin/booking-codes
     *
     * Admin creates a new booking code slip.
     * Returns the generated code + full slip details.
     *
     * Body: { games: [...], includesScorePrediction: false, expiresAt: null }
     */
    @PostMapping
    public ResponseEntity<BookingCodeResponse> createBookingCode(
            @Valid @RequestBody CreateBookingCodeRequest request,
            @AuthenticationPrincipal UUID adminId
    ) {
        BookingCodeResponse response = bookingCodeService.createBookingCode(request, adminId);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * GET /api/admin/booking-codes
     *
     * Paginated list of all codes this admin has created.
     * Default: page 0, size 20, sorted by createdAt desc.
     */
    @GetMapping
    public ResponseEntity<Page<BookingCodeResponse>> getMyBookingCodes(
            @AuthenticationPrincipal UUID adminId,
            @PageableDefault(size = 20) Pageable pageable
    ) {
        return ResponseEntity.ok(bookingCodeService.getMyBookingCodes(adminId, pageable));
    }

    /**
     * PATCH /api/admin/booking-codes/{id}/disable
     *
     * Soft-disables a booking code so users can no longer place bets on it.
     * Existing placed bets are unaffected.
     */
    @PatchMapping("/{id}/disable")
    public ResponseEntity<BookingCodeResponse> disableBookingCode(
            @PathVariable UUID id,
            @AuthenticationPrincipal UUID adminId
    ) {
        return ResponseEntity.ok(bookingCodeService.disableBookingCode(id, adminId));
    }
}