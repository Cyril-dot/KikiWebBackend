package com.kikiBettingWebBack.KikiWebSite.dtos;

import com.kikiBettingWebBack.KikiWebSite.entities.BetStatus;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Returned after a user successfully places a bet.
 * Shown on the success screen with the bet reference number.
 */
@Data
@Builder
public class PlacedBetResponse {

    private UUID id;

    /** Unique reference shown to user, e.g. "BET-20240402-A3F9" */
    private String betReference;

    /** The booking code they bet against */
    private String bookingCode;

    private BigDecimal stake;
    private String currency;
    private BigDecimal oddsAtPlacement;
    private BigDecimal potentialPayout;
    private BetStatus status;
    private LocalDateTime placedAt;

    /** Convenience: wallet balance after deducting stake */
    private BigDecimal remainingBalance;
}