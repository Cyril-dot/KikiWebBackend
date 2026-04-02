package com.kikiBettingWebBack.KikiWebSite.dtos;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.kikiBettingWebBack.KikiWebSite.entities.BookingCodeStatus;
import com.kikiBettingWebBack.KikiWebSite.entities.PickType;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Response DTO for correct-score booking codes.
 * Includes scorePrediction and correctScoreOptionId on every game row.
 */
@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CorrectScoreBookingCodeResponse {

    private UUID id;
    private String code;
    private BigDecimal combinedOdds;
    private int gameCount;
    private BookingCodeStatus status;
    private LocalDateTime createdAt;
    private LocalDateTime expiresAt;

    /** Only populated in admin-facing responses. */
    private String createdByEmail;

    private List<GameResponse> games;

    @Data
    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class GameResponse {
        private UUID id;
        private UUID gameId;
        private String homeTeam;
        private String awayTeam;
        private LocalDateTime matchDate;
        private int position;
        private PickType pick;
        private String pickLabel;
        private BigDecimal odds;
        private String scorePrediction;
        private UUID correctScoreOptionId;
    }
}