package com.kikiBettingWebBack.KikiWebSite.controllers;

import com.kikiBettingWebBack.KikiWebSite.dtos.CreateStandardBookingCodeRequest;
import com.kikiBettingWebBack.KikiWebSite.dtos.StandardBookingCodeResponse;
import com.kikiBettingWebBack.KikiWebSite.services.StandardBookingCodeService;
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
 * REST controller for standard (Home / Away / Draw) booking codes.
 *
 * Public endpoints:
 *   GET  /api/v1/booking-codes/standard/{code}          — load a slip by code
 *
 * Admin-only endpoints (requires ROLE_ADMIN):
 *   POST /api/v1/booking-codes/standard                 — create a slip
 *   GET  /api/v1/booking-codes/standard/admin/mine      — list my slips
 *   PUT  /api/v1/booking-codes/standard/{id}/disable    — disable a slip
 */
@RestController
@RequestMapping("/api/v1/booking-codes/standard")
@RequiredArgsConstructor
public class StandardBookingCodeController {

    private final StandardBookingCodeService standardBookingCodeService;

    // ── Public ───────────────────────────────────────────────────────────────

    /**
     * Load a standard booking code slip by its code string.
     * Returns 404 if the code does not exist, 410 if it is expired or disabled.
     */
    @GetMapping("/{code}")
    public ResponseEntity<StandardBookingCodeResponse> loadByCode(@PathVariable String code) {
        return ResponseEntity.ok(standardBookingCodeService.loadByCode(code));
    }

    // ── Admin ────────────────────────────────────────────────────────────────

    /**
     * Create a new standard booking code slip.
     * The adminId is expected to be resolved from the authenticated principal
     * via a method-security expression or extracted from the JWT upstream.
     * Here it is accepted as a request header for clarity — adapt to your
     * security context as needed (e.g. @AuthenticationPrincipal).
     */
    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<StandardBookingCodeResponse> createBookingCode(
            @Valid @RequestBody CreateStandardBookingCodeRequest request,
            @RequestHeader("X-Admin-Id") UUID adminId) {

        StandardBookingCodeResponse response = standardBookingCodeService.createBookingCode(request, adminId);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * List all standard booking codes created by the authenticated admin.
     * Supports pagination via ?page=0&size=20&sort=createdAt,desc.
     */
    @GetMapping("/admin/mine")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Page<StandardBookingCodeResponse>> getMyBookingCodes(
            @RequestHeader("X-Admin-Id") UUID adminId,
            @PageableDefault(size = 20, sort = "createdAt") Pageable pageable) {

        return ResponseEntity.ok(standardBookingCodeService.getMyBookingCodes(adminId, pageable));
    }

    /**
     * Disable a standard booking code.
     * Only the admin who created the code may disable it.
     */
    @PutMapping("/{id}/disable")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<StandardBookingCodeResponse> disableBookingCode(
            @PathVariable UUID id,
            @RequestHeader("X-Admin-Id") UUID adminId) {

        return ResponseEntity.ok(standardBookingCodeService.disableBookingCode(id, adminId));
    }
}