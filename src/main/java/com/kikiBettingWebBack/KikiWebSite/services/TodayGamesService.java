package com.kikiBettingWebBack.KikiWebSite.services;

import com.fasterxml.jackson.databind.JsonNode;
import com.kikiBettingWebBack.KikiWebSite.dtos.NewMatchDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class TodayGamesService {

    private final ApiFootballClient apiFootballClient;
    private final OddsApiClient     oddsApiClient;
    private final MatchMapper       matchMapper;

    // ══════════════════════════════════════════════════════════════
    // MAIN METHOD
    // ══════════════════════════════════════════════════════════════

    /**
     * Fetches all of today's fixtures (any status), attaches odds to each,
     * logs the full list to console, and returns the DTOs.
     */
    public List<NewMatchDTO> getTodayGames() {
        String today = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE);

        log.info("═══════════════════════════════════════════════════");
        log.info("📅 [TODAY GAMES] Starting fetch for {}...", today);
        log.info("═══════════════════════════════════════════════════");

        // ── 1. Fetch today's fixtures ─────────────────────────────
        JsonNode response = apiFootballClient.getTodayFixtures();
        JsonNode matches  = response.path("matches");

        if (matches.isMissingNode() || matches.isEmpty()) {
            log.info("ℹ️ [TODAY GAMES] No matches found for today ({}).", today);
            return List.of();
        }

        log.info("📋 [TODAY GAMES] {} fixtures found — fetching odds...", matches.size());

        // ── 2. Fetch odds ─────────────────────────────────────────
        Map<String, JsonNode> oddsMap = fetchAllOdds();
        List<JsonNode> allOddsEvents  = flattenOddsEvents(oddsMap);
        log.info("📊 [TODAY GAMES] {} odds events fetched", allOddsEvents.size());

        // ── 3. Map to DTOs with odds ──────────────────────────────
        List<NewMatchDTO> result     = new ArrayList<>();
        List<NewMatchDTO> upcoming   = new ArrayList<>();
        List<NewMatchDTO> live       = new ArrayList<>();
        List<NewMatchDTO> finished   = new ArrayList<>();

        for (JsonNode match : matches) {
            JsonNode oddsEvent = matchMapper.findMatchingOddsEvent(match, allOddsEvents);
            NewMatchDTO dto       = matchMapper.toDto(match, oddsEvent);
            result.add(dto);

            // Bucket by status for grouped console output
            switch (dto.getStatus()) {
                case "LIVE"     -> live.add(dto);
                case "FINISHED" -> finished.add(dto);
                default         -> upcoming.add(dto);
            }
        }

        // ── 4. Print to console grouped by status ─────────────────
        printMatchList("🔴 LIVE NOW",   live);
        printMatchList("📅 UPCOMING",   upcoming);
        printMatchList("✅ FINISHED",   finished);

        log.info("✅ [TODAY GAMES] Returning {} total matches (live={}, upcoming={}, finished={})",
                result.size(), live.size(), upcoming.size(), finished.size());

        return result;
    }

    // ══════════════════════════════════════════════════════════════
    // ODDS HELPERS
    // ══════════════════════════════════════════════════════════════

    private Map<String, JsonNode> fetchAllOdds() {
        try {
            return oddsApiClient.getAllSoccerOdds("uk");
        } catch (Exception e) {
            log.error("❌ [TODAY GAMES] Failed to fetch odds: {}", e.getMessage());
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
        if (matches.isEmpty()) return;

        System.out.println();
        System.out.println("╔══════════════════════════════════════════════════════════╗");
        System.out.printf ("║  %-56s  ║%n", title + " (" + matches.size() + " matches)");
        System.out.println("╠══════════════════════════════════════════════════════════╣");

        for (NewMatchDTO m : matches) {
            String score = (m.getHomeScore() != null && m.getAwayScore() != null)
                    ? m.getHomeScore() + " - " + m.getAwayScore()
                    : "vs";
            String timeInfo = m.getKickoffTime() != null
                    ? m.getKickoffTime().toLocalTime().toString()
                    : m.getStatus();

            System.out.printf("║  %-22s %5s %-22s  ║%n",
                    truncate(m.getHomeTeam(), 22), score, truncate(m.getAwayTeam(), 22));
            System.out.printf("║  %-20s  KO: %-8s  Period: %-12s ║%n",
                    truncate(m.getLeague(), 20), timeInfo,
                    truncate(m.getMatchPeriod(), 12));

            if (m.getHomeWinOdds() != null) {
                System.out.printf("║  1: %-6.2f  X: %-6.2f  2: %-6.2f  [%s]%n",
                        orZero(m.getHomeWinOdds()),
                        orZero(m.getDrawOdds()),
                        orZero(m.getAwayWinOdds()),
                        m.getOddsBookmaker() != null ? m.getOddsBookmaker() : "N/A");
            } else {
                System.out.println("║  Odds not available");
            }

            if (m.getOver25Odds() != null) {
                System.out.printf("║  O2.5: %-5.2f  U2.5: %-5.2f  O1.5: %-5.2f%n",
                        orZero(m.getOver25Odds()),
                        orZero(m.getUnder25Odds()),
                        orZero(m.getOver15Odds()));
            }

            System.out.println("║  ──────────────────────────────────────────────────────");
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