package com.kikiBettingWebBack.KikiWebSite.controllers;

import com.kikiBettingWebBack.KikiWebSite.Config.Security.AdminPrincipal;
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
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/booking-codes/correct-score")
@RequiredArgsConstructor
public class CorrectScoreBookingCodeController {

    private final CorrectScoreBookingCodeService correctScoreBookingCodeService;

    // ── Public ───────────────────────────────────────────────────────────────

    @GetMapping("/{code}")
    public ResponseEntity<CorrectScoreBookingCodeResponse> loadByCode(@PathVariable String code) {
        return ResponseEntity.ok(correctScoreBookingCodeService.loadByCode(code));
    }

    // ── Admin ────────────────────────────────────────────────────────────────

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<CorrectScoreBookingCodeResponse> createBookingCode(
            @Valid @RequestBody CreateCorrectScoreBookingCodeRequest request,
            @AuthenticationPrincipal AdminPrincipal principal) {

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(correctScoreBookingCodeService.createBookingCode(request, principal.getId()));
    }

    @GetMapping("/admin/mine")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Page<CorrectScoreBookingCodeResponse>> getMyBookingCodes(
            @AuthenticationPrincipal AdminPrincipal principal,
            @PageableDefault(size = 20, sort = "createdAt") Pageable pageable) {

        return ResponseEntity.ok(correctScoreBookingCodeService.getMyBookingCodes(principal.getId(), pageable));
    }

    @PutMapping("/{id}/disable")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<CorrectScoreBookingCodeResponse> disableBookingCode(
            @PathVariable UUID id,
            @AuthenticationPrincipal AdminPrincipal principal) {

        return ResponseEntity.ok(correctScoreBookingCodeService.disableBookingCode(id, principal.getId()));
    }
}