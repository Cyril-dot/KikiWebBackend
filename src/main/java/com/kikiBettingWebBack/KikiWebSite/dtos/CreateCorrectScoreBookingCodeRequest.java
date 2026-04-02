package com.kikiBettingWebBack.KikiWebSite.dtos;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Request DTO for creating a correct-score booking code slip.
 * Every game must carry a scorePrediction and a correctScoreOptionId.
 * The includesScorePrediction flag is always true — it is not accepted from the
 * caller; the service enforces it internally.
 */
@Data
public class CreateCorrectScoreBookingCodeRequest {

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

        @NotBlank(message = "Score prediction is required for every game on a correct-score slip")
        @Pattern(regexp = "\\d{1,2}-\\d{1,2}", message = "Score prediction must be in the format '2-1'")
        private String scorePrediction;

        @NotNull(message = "Correct score option ID is required")
        private UUID correctScoreOptionId;

        @NotNull(message = "Odds are required")
        @DecimalMin(value = "1.00", message = "Odds must be at least 1.00")
        @Digits(integer = 6, fraction = 4, message = "Odds format is invalid")
        private BigDecimal odds;
    }
}