package com.kikiBettingWebBack.KikiWebSite.controllers;

import com.kikiBettingWebBack.KikiWebSite.services.FootballService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.Collections;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/v1/football")
@RequiredArgsConstructor
public class FootballController {

    private final FootballService footballService;

    // ── GET /api/v1/football/fixtures ─────────────────────────────────────────
    // Returns all upcoming + live club fixtures (next 7 days) + WC 2026
    @GetMapping("/fixtures")
    public Mono<ResponseEntity<Map<String, Object>>> getFixtures() {
        log.info("GET /api/v1/football/fixtures called");
        return footballService.getAllFixtures()
                .map(matches -> {
                    long club = matches.stream().filter(m -> "club".equals(m.getCategory())).count();
                    long wc   = matches.stream().filter(m -> "wc".equals(m.getCategory())).count();
                    log.info("✅ Returning {} fixtures ({} club, {} WC)", matches.size(), club, wc);
                    return ResponseEntity.ok(Map.<String, Object>of(
                            "total",   matches.size(),
                            "club",    club,
                            "wc",      wc,
                            "matches", matches
                    ));
                })
                .onErrorResume(e -> {
                    log.error("❌ /fixtures failed: {}", e.getMessage());
                    return Mono.just(ResponseEntity.ok(Map.<String, Object>of(
                            "total",   0,
                            "club",    0,
                            "wc",      0,
                            "matches", Collections.emptyList()
                    )));
                });
    }

    // ── GET /api/v1/football/live ─────────────────────────────────────────────
    // Returns only in-play / paused matches — always 200, never 500
    @GetMapping("/live")
    public Mono<ResponseEntity<Map<String, Object>>> getLive() {
        log.info("GET /api/v1/football/live called");
        return footballService.getLiveFixtures()
                .map(matches -> {
                    log.info("✅ Returning {} live fixtures", matches.size());
                    return ResponseEntity.ok(Map.<String, Object>of(
                            "total",   matches.size(),
                            "matches", matches
                    ));
                })
                .onErrorResume(e -> {
                    log.warn("⚠️ /live failed (returning empty): {}", e.getMessage());
                    return Mono.just(ResponseEntity.ok(Map.<String, Object>of(
                            "total",   0,
                            "matches", Collections.emptyList()
                    )));
                });
    }

    // ── GET /api/v1/football/health ───────────────────────────────────────────
    // Quick health-check endpoint for Render / Railway uptime monitors
    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        return ResponseEntity.ok(Map.of("status", "UP", "service", "gstake-backend"));
    }
}