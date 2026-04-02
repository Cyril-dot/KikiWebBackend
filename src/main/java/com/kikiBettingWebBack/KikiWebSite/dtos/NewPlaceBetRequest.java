package com.kikiBettingWebBack.KikiWebSite.dtos;

import jakarta.validation.constraints.*;
import lombok.Data;

import java.math.BigDecimal;

/**
 * Request body for POST /api/bets/place
 * Sent by user when they enter a booking code and choose a stake.
 */
@Data
public class NewPlaceBetRequest {

    @NotBlank(message = "Booking code is required")
    @Pattern(regexp = "^[A-Z0-9]{4}-[A-Z0-9]{4}$", message = "Invalid booking code format")
    private String bookingCode;

    @NotNull(message = "Stake amount is required")
    @DecimalMin(value = "1.00", message = "Minimum stake is 1.00")
    @DecimalMax(value = "100000.00", message = "Maximum stake is 100,000.00")
    @Digits(integer = 10, fraction = 2)
    private BigDecimal stake;
}