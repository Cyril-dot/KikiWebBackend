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
 * Request DTO for creating a standard (1X2 — Home / Away / Draw) booking code slip.
 * Score prediction fields are intentionally absent from this DTO.
 */
@Data
public class CreateStandardBookingCodeRequest {

    @NotNull(message = "Expiry date is required")
    @Future(message = "Expiry date must be in the future")
    private LocalDateTime expiresAt;

    @NotEmpty(message = "A slip must contain at least one game")
    @Size(min = 1, max = 20, message = "A slip may contain between 1 and 20 games")
    @Valid
    private List<GameRequest> games;

    @Data
    public static class GameRequest {

        @NotNull(message = "Game ID is required")
        private UUID gameId;

        @NotNull(message = "Pick is required")
        private PickType pick;

        @NotNull(message = "Odds are required")
        @DecimalMin(value = "1.00", message = "Odds must be at least 1.00")
        @Digits(integer = 6, fraction = 4, message = "Odds format is invalid")
        private BigDecimal odds;
    }
}