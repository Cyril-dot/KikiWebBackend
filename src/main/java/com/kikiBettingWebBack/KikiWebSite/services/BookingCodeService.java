package com.kikiBettingWebBack.KikiWebSite.services;

import com.kikiBettingWebBack.KikiWebSite.dtos.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface BookingCodeService {

    /**
     * Admin creates a new booking code slip.
     * Validates all game IDs, calculates combined odds,
     * generates a unique code, persists everything.
     *
     * @param request  the slip definition from admin
     * @param adminId  the authenticated admin's user ID
     * @return full BookingCodeResponse including the generated code
     */
    BookingCodeResponse createBookingCode(CreateBookingCodeRequest request, UUID adminId);

    /**
     * Load a booking code by its code string.
     * Called by the user-side frontend to display the slip.
     * Returns 404 if not found, 410 if expired/disabled.
     *
     * @param code  e.g. "AXKP-7BM2"
     * @return the slip with all games and pre-set picks
     */
    BookingCodeResponse loadByCode(String code);

    /**
     * Admin: paginated list of all codes they created.
     */
    Page<BookingCodeResponse> getMyBookingCodes(UUID adminId, Pageable pageable);

    /**
     * Admin: disable a code so no new bets can be placed.
     */
    BookingCodeResponse disableBookingCode(UUID bookingCodeId, UUID adminId);

    /**
     * Scheduled job helper: expire all ACTIVE codes past their expiresAt.
     * Called by a @Scheduled task — not exposed via HTTP.
     */
    void expireOverdueCodes();
}