package com.kikiBettingWebBack.KikiWebSite.controllers;

import com.kikiBettingWebBack.KikiWebSite.dtos.LiveGameResponse;
import com.kikiBettingWebBack.KikiWebSite.dtos.UpcomingFixtureResponse;
import com.kikiBettingWebBack.KikiWebSite.services.FootballApiService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Public endpoints to expose live scores and upcoming fixtures.
 * Base path: /api/football
 */
@Slf4j
@RestController
@RequestMapping("/api/football")
@RequiredArgsConstructor
public class FootballApiController {

    private final FootballApiService footballApiService;

    // ── Live games ────────────────────────────────────────────────────────────

    /**
     * GET /api/football/live
     * Returns all in-play fixtures across every league.
     * Always returns 200 — empty list if none or on error.
     */
    @GetMapping("/live")
    public ResponseEntity<List<LiveGameResponse>> getLiveGames() {
        try {
            List<LiveGameResponse> games = footballApiService.getLiveGames();
            return ResponseEntity.ok(games != null ? games : List.of());
        } catch (Exception e) {
            log.warn("⚠️ /api/football/live failed, returning empty list: {}", e.getMessage());
            return ResponseEntity.ok(List.of());
        }
    }

    /**
     * GET /api/football/live/leagues?leagues=2021,2014
     * Returns live fixtures filtered to specific competition IDs.
     * Always returns 200 — empty list if none or on error.
     */
    @GetMapping("/live/leagues")
    public ResponseEntity<List<LiveGameResponse>> getLiveGamesByLeagues(
            @RequestParam String leagues) {
        try {
            List<LiveGameResponse> games = footballApiService.getLiveGamesByLeagues(leagues);
            return ResponseEntity.ok(games != null ? games : List.of());
        } catch (Exception e) {
            log.warn("⚠️ /api/football/live/leagues failed, returning empty list: {}", e.getMessage());
            return ResponseEntity.ok(List.of());
        }
    }

    // ── Upcoming fixtures ─────────────────────────────────────────────────────

    /**
     * GET /api/football/upcoming
     * Returns today's not-yet-started fixtures for all default leagues.
     * Always returns 200 — empty list if none or on error.
     */
    @GetMapping("/upcoming")
    public ResponseEntity<List<UpcomingFixtureResponse>> getUpcomingGames() {
        try {
            List<UpcomingFixtureResponse> fixtures = footballApiService.getTodayUpcomingFixtures();
            return ResponseEntity.ok(fixtures != null ? fixtures : List.of());
        } catch (Exception e) {
            log.warn("⚠️ /api/football/upcoming failed, returning empty list: {}", e.getMessage());
            return ResponseEntity.ok(List.of());
        }
    }

    /**
     * GET /api/football/upcoming/{leagueId}?season=2024
     * Returns upcoming fixtures for a specific league.
     * Always returns 200 — empty list if none or on error.
     */
    @GetMapping("/upcoming/{leagueId}")
    public ResponseEntity<List<UpcomingFixtureResponse>> getUpcomingByLeague(
            @PathVariable int leagueId,
            @RequestParam(required = false) Integer season) {
        try {
            int resolvedSeason = (season != null) ? season : currentSeason();
            List<UpcomingFixtureResponse> fixtures = footballApiService.getUpcomingByLeague(leagueId, resolvedSeason);
            return ResponseEntity.ok(fixtures != null ? fixtures : List.of());
        } catch (Exception e) {
            log.warn("⚠️ /api/football/upcoming/{} failed, returning empty list: {}", leagueId, e.getMessage());
            return ResponseEntity.ok(List.of());
        }
    }

    // ── Helper ────────────────────────────────────────────────────────────────

    private int currentSeason() {
        int month = java.time.LocalDate.now().getMonthValue();
        int year  = java.time.LocalDate.now().getYear();
        return month >= 8 ? year : year - 1;
    }
}