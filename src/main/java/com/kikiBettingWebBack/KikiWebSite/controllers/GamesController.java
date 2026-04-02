package com.kikiBettingWebBack.KikiWebSite.controllers;

import com.kikiBettingWebBack.KikiWebSite.dtos.NewMatchDTO;
import com.kikiBettingWebBack.KikiWebSite.services.GameDataStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/fetch/api/games")   // ← whitelist this in your security config
@RequiredArgsConstructor
@CrossOrigin(origins = "*")     // adjust to your frontend origin if needed
public class GamesController {

    private final GameDataStore gameDataStore;

    // ── GET /api/games/live ───────────────────────────────────────────────────
    @GetMapping("/live")
    public ResponseEntity<List<NewMatchDTO>> getLiveGames() {
        log.info("📥 [GET] /api/games/live — serving {} games from store", gameDataStore.getLiveGames().size());
        return ResponseEntity.ok(gameDataStore.getLiveGames());
    }

    // ── GET /api/games/today ──────────────────────────────────────────────────
    @GetMapping("/today")
    public ResponseEntity<List<NewMatchDTO>> getTodayGames() {
        log.info("📥 [GET] /api/games/today — serving {} games from store", gameDataStore.getTodayGames().size());
        return ResponseEntity.ok(gameDataStore.getTodayGames());
    }

    // ── GET /api/games/upcoming ───────────────────────────────────────────────
    @GetMapping("/upcoming")
    public ResponseEntity<List<NewMatchDTO>> getUpcomingGames() {
        log.info("📥 [GET] /api/games/upcoming — serving {} games from store", gameDataStore.getUpcomingGames().size());
        return ResponseEntity.ok(gameDataStore.getUpcomingGames());
    }

    // ── GET /api/games/status ─────────────────────────────────────────────────
    // Useful for checking when each list was last refreshed
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getStoreStatus() {
        Map<String, Object> status = Map.of(
                "live",     Map.of("count", gameDataStore.getLiveGames().size(),     "lastUpdated", nullSafe(gameDataStore.getLiveLastUpdated())),
                "today",    Map.of("count", gameDataStore.getTodayGames().size(),    "lastUpdated", nullSafe(gameDataStore.getTodayLastUpdated())),
                "upcoming", Map.of("count", gameDataStore.getUpcomingGames().size(), "lastUpdated", nullSafe(gameDataStore.getUpcomingLastUpdated()))
        );
        log.info("📥 [GET] /api/games/status — store status requested");
        return ResponseEntity.ok(status);
    }

    private String nullSafe(Instant instant) {
        return instant != null ? instant.toString() : "not yet fetched";
    }
}