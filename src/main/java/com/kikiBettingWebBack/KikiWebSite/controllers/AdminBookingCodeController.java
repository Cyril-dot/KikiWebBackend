package com.kikiBettingWebBack.KikiWebSite.controllers;

import com.kikiBettingWebBack.KikiWebSite.Config.Security.AdminPrincipal;
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

@RestController
@RequestMapping("/api/admin/booking-codes")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminBookingCodeController {

    private final BookingCodeService bookingCodeService;

    /**
     * POST /api/admin/booking-codes
     */
    @PostMapping
    public ResponseEntity<BookingCodeResponse> createBookingCode(
            @Valid @RequestBody CreateBookingCodeRequest request,
            @AuthenticationPrincipal AdminPrincipal principal
    ) {
        BookingCodeResponse response = bookingCodeService.createBookingCode(request, principal.getId());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * GET /api/admin/booking-codes
     */
    @GetMapping
    public ResponseEntity<Page<BookingCodeResponse>> getMyBookingCodes(
            @AuthenticationPrincipal AdminPrincipal principal,
            @PageableDefault(size = 20) Pageable pageable
    ) {
        return ResponseEntity.ok(bookingCodeService.getMyBookingCodes(principal.getId(), pageable));
    }

    /**
     * PATCH /api/admin/booking-codes/{id}/disable
     */
    @PatchMapping("/{id}/disable")
    public ResponseEntity<BookingCodeResponse> disableBookingCode(
            @PathVariable UUID id,
            @AuthenticationPrincipal AdminPrincipal principal
    ) {
        return ResponseEntity.ok(bookingCodeService.disableBookingCode(id, principal.getId()));
    }
}