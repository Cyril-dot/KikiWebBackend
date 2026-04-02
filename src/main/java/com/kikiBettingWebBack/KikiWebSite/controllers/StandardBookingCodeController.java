package com.kikiBettingWebBack.KikiWebSite.controllers;

import com.kikiBettingWebBack.KikiWebSite.Config.Security.AdminPrincipal;
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
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/booking-codes/standard")
@RequiredArgsConstructor
public class StandardBookingCodeController {

    private final StandardBookingCodeService standardBookingCodeService;

    // ── Public ───────────────────────────────────────────────────────────────

    @GetMapping("/{code}")
    public ResponseEntity<StandardBookingCodeResponse> loadByCode(@PathVariable String code) {
        return ResponseEntity.ok(standardBookingCodeService.loadByCode(code));
    }

    // ── Admin ────────────────────────────────────────────────────────────────

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<StandardBookingCodeResponse> createBookingCode(
            @Valid @RequestBody CreateStandardBookingCodeRequest request,
            @AuthenticationPrincipal AdminPrincipal principal) {

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(standardBookingCodeService.createBookingCode(request, principal.getId()));
    }

    @GetMapping("/admin/mine")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Page<StandardBookingCodeResponse>> getMyBookingCodes(
            @AuthenticationPrincipal AdminPrincipal principal,
            @PageableDefault(size = 20, sort = "createdAt") Pageable pageable) {

        return ResponseEntity.ok(standardBookingCodeService.getMyBookingCodes(principal.getId(), pageable));
    }

    @PutMapping("/{id}/disable")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<StandardBookingCodeResponse> disableBookingCode(
            @PathVariable UUID id,
            @AuthenticationPrincipal AdminPrincipal principal) {

        return ResponseEntity.ok(standardBookingCodeService.disableBookingCode(id, principal.getId()));
    }
}