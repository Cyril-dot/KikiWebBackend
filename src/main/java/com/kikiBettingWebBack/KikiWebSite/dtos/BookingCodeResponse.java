package com.kikiBettingWebBack.KikiWebSite.dtos;

import com.kikiBettingWebBack.KikiWebSite.entities.*;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Returned after admin generates a code (full detail)
 * and also when a user loads a code (same shape — user just ignores admin fields).
 */
@Data
@Builder
public class BookingCodeResponse {

    private UUID id;

    /** The shareable code, e.g. "AXKP-7BM2" */
    private String code;

    private BigDecimal combinedOdds;
    private int gameCount;
    private boolean includesScorePrediction;
    private BookingCodeStatus status;
    private LocalDateTime createdAt;
    private LocalDateTime expiresAt;

    /** Who created the code — only sent to ADMIN role responses. */
    private String createdByEmail;

    private List<BookingCodeGameResponse> games;

    @Data
    @Builder
    public static class BookingCodeGameResponse {

        private UUID id;
        private UUID gameId;

        /** Home team name */
        private String homeTeam;

        /** Away team name */
        private String awayTeam;

        /** Match date/time — maps to Game.matchDate */
        private LocalDateTime matchDate;

        /** Position in the slip (1-based) */
        private int position;

        /** The pre-set pick: HOME_WIN / DRAW / AWAY_WIN */
        private PickType pick;

        /** Frontend display label: "1", "X", or "2" */
        private String pickLabel;

        /** Locked-in odds for this pick */
        private BigDecimal odds;

        /** Score prediction string, e.g. "2-1". Null if not included. */
        private String scorePrediction;
    }
}