package com.kikiBettingWebBack.KikiWebSite.dtos;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NewMatchDTO {

    // ── Identity ──────────────────────────────────────────────────
    private String  externalFixtureId;
    private String  source;             // "football-data" | "api-sports"

    // ── Teams ─────────────────────────────────────────────────────
    private String  homeTeam;
    private String  awayTeam;
    private String  homeLogo;
    private String  awayLogo;

    // ── Competition ───────────────────────────────────────────────
    private String  league;
    private String  country;
    private String  leagueLogo;

    // ── Timing ────────────────────────────────────────────────────
    private LocalDateTime kickoffTime;
    private String  status;             // UPCOMING | LIVE | FINISHED | CANCELLED | POSTPONED
    private String  matchPeriod;        // "1st Half" | "Half Time" | "2nd Half" | "Full Time" etc.
    private Integer elapsedMinutes;

    // ── Score ─────────────────────────────────────────────────────
    private Integer homeScore;
    private Integer awayScore;

    // ── Odds (1X2) ────────────────────────────────────────────────
    private Double  homeWinOdds;
    private Double  drawOdds;
    private Double  awayWinOdds;

    // ── Odds (Totals) ─────────────────────────────────────────────
    private Double  over15Odds;
    private Double  under15Odds;
    private Double  over25Odds;
    private Double  under25Odds;
    private Double  over35Odds;
    private Double  under35Odds;

    // ── Odds meta ─────────────────────────────────────────────────
    private String  oddsBookmaker;
    private boolean oddsFromFallback;   // true = came from The-Odds-API fallback
}