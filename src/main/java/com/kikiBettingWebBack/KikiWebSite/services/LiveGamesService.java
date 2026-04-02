package com.kikiBettingWebBack.KikiWebSite.services;

import com.fasterxml.jackson.databind.JsonNode;
import com.kikiBettingWebBack.KikiWebSite.dtos.NewMatchDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class LiveGamesService {

    private final ApiFootballClient apiFootballClient;
    private final OddsApiClient     oddsApiClient;
    private final MatchMapper       matchMapper;

    // ══════════════════════════════════════════════════════════════
    // MAIN METHOD
    // ══════════════════════════════════════════════════════════════

    /**
     * Fetches all currently live/in-play matches, attaches odds to each,
     * logs the full list to console, and returns the DTOs.
     */
    public List<NewMatchDTO> getLiveGames() {
        log.info("═══════════════════════════════════════════════════");
        log.info("🔴 [LIVE GAMES] Starting fetch...");
        log.info("═══════════════════════════════════════════════════");

        // ── 1. Fetch live fixtures ────────────────────────────────
        JsonNode response = apiFootballClient.getLiveFixtures();
        JsonNode matches  = response.path("matches");

        if (matches.isMissingNode() || matches.isEmpty()) {
            log.info("ℹ️ [LIVE GAMES] No live matches found right now.");
            return List.of();
        }

        log.info("📋 [LIVE GAMES] {} live fixtures found — fetching odds...", matches.size());

        // ── 2. Fetch odds for all major soccer leagues ────────────
        Map<String, JsonNode> oddsMap = fetchAllOdds();

        // Flatten all odds events into one list for matching
        List<JsonNode> allOddsEvents = flattenOddsEvents(oddsMap);
        log.info("📊 [LIVE GAMES] {} total odds events fetched across all leagues", allOddsEvents.size());

        // ── 3. Map each fixture to MatchDTO with odds ─────────────
        List<NewMatchDTO> result = new ArrayList<>();
        for (JsonNode match : matches) {
            JsonNode oddsEvent = matchMapper.findMatchingOddsEvent(match, allOddsEvents);
            NewMatchDTO dto       = matchMapper.toDto(match, oddsEvent);
            result.add(dto);
        }

        // ── 4. Print to console ───────────────────────────────────
        printMatchList("🔴 LIVE GAMES", result);

        log.info("✅ [LIVE GAMES] Returning {} live matches with odds", result.size());
        return result;
    }

    // ══════════════════════════════════════════════════════════════
    // ODDS HELPERS
    // ══════════════════════════════════════════════════════════════

    private Map<String, JsonNode> fetchAllOdds() {
        try {
            return oddsApiClient.getAllSoccerOdds("uk");
        } catch (Exception e) {
            log.error("❌ [LIVE GAMES] Failed to fetch odds: {}", e.getMessage());
            return Map.of();
        }
    }

    private List<JsonNode> flattenOddsEvents(Map<String, JsonNode> oddsMap) {
        List<JsonNode> all = new ArrayList<>();
        for (JsonNode sportEvents : oddsMap.values()) {
            if (sportEvents != null && sportEvents.isArray()) {
                sportEvents.forEach(all::add);
            }
        }
        return all;
    }

    // ══════════════════════════════════════════════════════════════
    // CONSOLE PRINT
    // ══════════════════════════════════════════════════════════════

    private void printMatchList(String title, List<NewMatchDTO> matches) {
        System.out.println();
        System.out.println("╔══════════════════════════════════════════════════════════╗");
        System.out.printf ("║  %-56s  ║%n", title + " (" + matches.size() + " matches)");
        System.out.println("╠══════════════════════════════════════════════════════════╣");

        if (matches.isEmpty()) {
            System.out.println("║  No matches found.                                       ║");
        }

        for (NewMatchDTO m : matches) {
            String score  = (m.getHomeScore() != null && m.getAwayScore() != null)
                    ? m.getHomeScore() + " - " + m.getAwayScore()
                    : "vs";
            String minute = (m.getElapsedMinutes() != null && m.getElapsedMinutes() > 0)
                    ? m.getElapsedMinutes() + "'"
                    : m.getStatus();

            System.out.printf("║  %-22s %5s %-22s  ║%n",
                    truncate(m.getHomeTeam(), 22),
                    score,
                    truncate(m.getAwayTeam(), 22));

            System.out.printf("║  League: %-20s  Period: %-14s ║%n",
                    truncate(m.getLeague(), 20),
                    truncate(m.getMatchPeriod() + " " + minute, 14));

            if (m.getHomeWinOdds() != null) {
                System.out.printf("║  Odds  → 1: %-6.2f  X: %-6.2f  2: %-6.2f              ║%n",
                        orZero(m.getHomeWinOdds()),
                        orZero(m.getDrawOdds()),
                        orZero(m.getAwayWinOdds()));
            } else {
                System.out.println("║  Odds  → Not available                                   ║");
            }

            if (m.getOver25Odds() != null) {
                System.out.printf("║  Goals → O2.5: %-5.2f  U2.5: %-5.2f  O1.5: %-5.2f       ║%n",
                        orZero(m.getOver25Odds()),
                        orZero(m.getUnder25Odds()),
                        orZero(m.getOver15Odds()));
            }

            System.out.println("║  ──────────────────────────────────────────────────────  ║");
        }

        System.out.println("╚══════════════════════════════════════════════════════════╝");
        System.out.println();
    }

    private String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() > max ? s.substring(0, max - 1) + "…" : s;
    }

    private double orZero(Double d) {
        return d != null ? d : 0.0;
    }
}