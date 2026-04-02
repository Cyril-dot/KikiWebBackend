package com.kikiBettingWebBack.KikiWebSite.services;

import com.kikiBettingWebBack.KikiWebSite.dtos.CorrectScoreBookingCodeResponse;
import com.kikiBettingWebBack.KikiWebSite.dtos.CreateCorrectScoreBookingCodeRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

/**
 * Service contract for correct-score booking code operations.
 */
public interface CorrectScoreBookingCodeService {

    /**
     * Creates a new correct-score booking code slip for the given admin.
     * All games must carry a scorePrediction and correctScoreOptionId.
     */
    CorrectScoreBookingCodeResponse createBookingCode(CreateCorrectScoreBookingCodeRequest request, UUID adminId);

    /**
     * Loads a correct-score booking code by its code string (user-facing).
     * Throws 404 if not found, 410 if expired or disabled.
     */
    CorrectScoreBookingCodeResponse loadByCode(String code);

    /**
     * Returns a paginated list of correct-score booking codes created by the given admin.
     */
    Page<CorrectScoreBookingCodeResponse> getMyBookingCodes(UUID adminId, Pageable pageable);

    /**
     * Disables a booking code. Only the admin who created it may disable it.
     */
    CorrectScoreBookingCodeResponse disableBookingCode(UUID bookingCodeId, UUID adminId);
}