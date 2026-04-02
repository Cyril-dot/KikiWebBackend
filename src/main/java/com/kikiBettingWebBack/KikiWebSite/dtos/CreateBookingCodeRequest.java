package com.kikiBettingWebBack.KikiWebSite.dtos;

import com.kikiBettingWebBack.KikiWebSite.entities.PickType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Request body for POST /api/admin/booking-codes
 * Sent by admin to generate a new booking code slip.
 */
@Data
public class CreateBookingCodeRequest {

    @NotNull(message = "Games list is required")
    @Size(min = 1, max = 10, message = "Slip must contain between 1 and 10 games")
    @Valid
    private List<BookingCodeGameRequest> games;

    /**
     * Whether this slip includes score predictions.
     * Used for display purposes; actual scores live in each GameRequest.
     */
    private boolean includesScorePrediction = false;

    /**
     * Optional expiry datetime. Null = never expires.
     */
    @Future(message = "Expiry must be in the future")
    private LocalDateTime expiresAt;

    /**
     * Represents one game entry on the slip being created.
     */
    @Data
    public static class BookingCodeGameRequest {

        @NotNull(message = "Game ID is required")
        private UUID gameId;

        @NotNull(message = "Pick is required")
        private PickType pick;

        /**
         * The odds for this pick. Must be provided by admin (sourced from odds API).
         */
        @NotNull(message = "Odds are required")
        @DecimalMin(value = "1.01", message = "Odds must be at least 1.01")
        @Digits(integer = 6, fraction = 2)
        private BigDecimal odds;

        /**
         * Optional score prediction string, e.g. "2-1".
         * Required when parent includesScorePrediction = true.
         */
        @Pattern(regexp = "^\\d{1,2}-\\d{1,2}$", message = "Score prediction must be in format 'N-N' e.g. '2-1'")
        private String scorePrediction;

        /**
         * FK to a CorrectScoreOption if applicable.
         */
        private UUID correctScoreOptionId;
    }
}