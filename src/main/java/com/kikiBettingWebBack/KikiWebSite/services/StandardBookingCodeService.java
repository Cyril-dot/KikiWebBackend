package com.kikiBettingWebBack.KikiWebSite.services;

import com.kikiBettingWebBack.KikiWebSite.dtos.CreateStandardBookingCodeRequest;
import com.kikiBettingWebBack.KikiWebSite.dtos.StandardBookingCodeResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

/**
 * Service contract for standard (Home / Away / Draw) booking code operations.
 */
public interface StandardBookingCodeService {

    /**
     * Creates a new standard booking code slip for the given admin.
     */
    StandardBookingCodeResponse createBookingCode(CreateStandardBookingCodeRequest request, UUID adminId);

    /**
     * Loads a standard booking code by its code string (user-facing).
     * Throws 404 if not found, 410 if expired or disabled.
     */
    StandardBookingCodeResponse loadByCode(String code);

    /**
     * Returns a paginated list of standard booking codes created by the given admin.
     */
    Page<StandardBookingCodeResponse> getMyBookingCodes(UUID adminId, Pageable pageable);

    /**
     * Disables a booking code. Only the admin who created it may disable it.
     */
    StandardBookingCodeResponse disableBookingCode(UUID bookingCodeId, UUID adminId);

    /**
     * Scheduled job — marks all overdue ACTIVE codes as EXPIRED.
     */
    void expireOverdueCodes();
}