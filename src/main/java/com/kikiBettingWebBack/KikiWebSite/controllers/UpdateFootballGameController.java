package com.kikiBettingWebBack.KikiWebSite.controllers;

import com.kikiBettingWebBack.KikiWebSite.dtos.NewMatchDTO;
import com.kikiBettingWebBack.KikiWebSite.services.LiveGamesService;
import com.kikiBettingWebBack.KikiWebSite.services.TodayGamesService;
import com.kikiBettingWebBack.KikiWebSite.services.UpcomingGamesService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * PUBLIC — No authentication required.
 *
 * Endpoints:
 *   GET /api/v1/football/live      → all currently live matches with odds
 *   GET /api/v1/football/today     → all of today's matches with odds
 *   GET /api/v1/football/upcoming  → next 7 days of matches with odds
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/football")
@RequiredArgsConstructor
public class UpdateFootballGameController {

    private final LiveGamesService liveGamesService;
    private final TodayGamesService todayGamesService;
    private final UpcomingGamesService upcomingGamesService;

    // ══════════════════════════════════════════════════════════════
    // LIVE
    // ══════════════════════════════════════════════════════════════

    /**
     * GET /api/v1/football/live
     * Returns all currently in-play matches with attached odds.
     */
    @GetMapping("/live")
    public ResponseEntity<Map<String, Object>> getLiveGames() {
        log.info("[CONTROLLER] GET /api/v1/football/live");
        try {
            List<NewMatchDTO> games = liveGamesService.getLiveGames();
            return ResponseEntity.ok(buildResponse("Live games", games));
        } catch (Exception e) {
            log.error("[CONTROLLER] /live failed: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError()
                    .body(buildError("Failed to fetch live games: " + e.getMessage()));
        }
    }

    // ══════════════════════════════════════════════════════════════
    // TODAY
    // ══════════════════════════════════════════════════════════════

    /**
     * GET /api/v1/football/today
     * Returns all of today's matches (any status) with attached odds.
     */
    @GetMapping("/today")
    public ResponseEntity<Map<String, Object>> getTodayGames() {
        log.info("[CONTROLLER] GET /api/v1/football/today");
        try {
            List<NewMatchDTO> games = todayGamesService.getTodayGames();
            return ResponseEntity.ok(buildResponse("Today's games", games));
        } catch (Exception e) {
            log.error("[CONTROLLER] /today failed: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError()
                    .body(buildError("Failed to fetch today's games: " + e.getMessage()));
        }
    }

    // ══════════════════════════════════════════════════════════════
    // UPCOMING
    // ══════════════════════════════════════════════════════════════

    /**
     * GET /api/v1/football/upcoming
     * Returns matches for the next 7 days with attached odds.
     */
    @GetMapping("/upcoming")
    public ResponseEntity<Map<String, Object>> getUpcomingGames() {
        log.info("[CONTROLLER] GET /api/v1/football/upcoming");
        try {
            List<NewMatchDTO> games = upcomingGamesService.getUpcomingGames();
            return ResponseEntity.ok(buildResponse("Upcoming games (next 7 days)", games));
        } catch (Exception e) {
            log.error("[CONTROLLER] /upcoming failed: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError()
                    .body(buildError("Failed to fetch upcoming games: " + e.getMessage()));
        }
    }

    // ══════════════════════════════════════════════════════════════
    // RESPONSE BUILDERS
    // ══════════════════════════════════════════════════════════════

    private Map<String, Object> buildResponse(String message, List<NewMatchDTO> data) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success",   true);
        body.put("message",   message);
        body.put("count",     data.size());
        body.put("timestamp", LocalDateTime.now().toString());
        body.put("data",      data);
        return body;
    }

    private Map<String, Object> buildError(String message) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success",   false);
        body.put("message",   message);
        body.put("timestamp", LocalDateTime.now().toString());
        return body;
    }
}