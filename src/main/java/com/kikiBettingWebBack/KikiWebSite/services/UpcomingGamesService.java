package com.kikiBettingWebBack.KikiWebSite.services;

import com.fasterxml.jackson.databind.JsonNode;
import com.kikiBettingWebBack.KikiWebSite.dtos.NewMatchDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class UpcomingGamesService {

    private final ApiFootballClient apiFootballClient;
    private final OddsApiClient     oddsApiClient;
    private final MatchMapper       matchMapper;

    // ══════════════════════════════════════════════════════════════
    // MAIN METHOD
    // ══════════════════════════════════════════════════════════════

    /**
     * Fetches all upcoming fixtures (today+1 through today+7), attaches odds,
     * logs the full list grouped by date to console, and returns the DTOs.
     */
    public List<NewMatchDTO> getUpcomingGames() {
        String from = LocalDate.now().plusDays(1).format(DateTimeFormatter.ISO_LOCAL_DATE);
        String to   = LocalDate.now().plusDays(7).format(DateTimeFormatter.ISO_LOCAL_DATE);

        log.info("═══════════════════════════════════════════════════");
        log.info("📅 [UPCOMING GAMES] Starting fetch {} → {}...", from, to);
        log.info("═══════════════════════════════════════════════════");

        // ── 1. Fetch upcoming fixtures ────────────────────────────
        JsonNode response = apiFootballClient.getUpcomingFixtures();
        JsonNode matches  = response.path("matches");

        if (matches.isMissingNode() || matches.isEmpty()) {
            log.info("ℹ️ [UPCOMING GAMES] No upcoming matches found.");
            return List.of();
        }

        log.info("📋 [UPCOMING GAMES] {} fixtures found — fetching odds...", matches.size());

        // ── 2. Fetch odds ─────────────────────────────────────────
        Map<String, JsonNode> oddsMap = fetchAllOdds();
        List<JsonNode> allOddsEvents  = flattenOddsEvents(oddsMap);
        log.info("📊 [UPCOMING GAMES] {} odds events fetched", allOddsEvents.size());

        // ── 3. Map to DTOs with odds ──────────────────────────────
        List<NewMatchDTO> result = new ArrayList<>();

        // Group by date for nice console output
        Map<String, List<NewMatchDTO>> byDate = new LinkedHashMap<>();

        for (JsonNode match : matches) {
            JsonNode oddsEvent = matchMapper.findMatchingOddsEvent(match, allOddsEvents);
            NewMatchDTO dto       = matchMapper.toDto(match, oddsEvent);
            result.add(dto);

            String dateKey = dto.getKickoffTime() != null
                    ? dto.getKickoffTime().toLocalDate().format(DateTimeFormatter.ISO_LOCAL_DATE)
                    : "Unknown Date";
            byDate.computeIfAbsent(dateKey, k -> new ArrayList<>()).add(dto);
        }

        // ── 4. Print to console grouped by date ───────────────────
        for (Map.Entry<String, List<NewMatchDTO>> entry : byDate.entrySet()) {
            printMatchList("📅 " + entry.getKey(), entry.getValue());
        }

        log.info("✅ [UPCOMING GAMES] Returning {} matches across {} days",
                result.size(), byDate.size());

        return result;
    }

    // ══════════════════════════════════════════════════════════════
    // ODDS HELPERS
    // ══════════════════════════════════════════════════════════════

    private Map<String, JsonNode> fetchAllOdds() {
        try {
            return oddsApiClient.getAllSoccerOdds("uk");
        } catch (Exception e) {
            log.error("❌ [UPCOMING GAMES] Failed to fetch odds: {}", e.getMessage());
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
            String koTime = m.getKickoffTime() != null
                    ? m.getKickoffTime().toLocalTime().toString()
                    : "TBD";

            System.out.printf("║  %5s  %-20s vs %-20s   ║%n",
                    koTime,
                    truncate(m.getHomeTeam(), 20),
                    truncate(m.getAwayTeam(), 20));

            System.out.printf("║         League: %-40s  ║%n",
                    truncate(m.getLeague() + " (" + m.getCountry() + ")", 40));

            if (m.getHomeWinOdds() != null) {
                System.out.printf("║         Odds → 1: %-5.2f  X: %-5.2f  2: %-5.2f  [%s]%n",
                        orZero(m.getHomeWinOdds()),
                        orZero(m.getDrawOdds()),
                        orZero(m.getAwayWinOdds()),
                        m.getOddsBookmaker() != null ? m.getOddsBookmaker() : "N/A");
            } else {
                System.out.println("║         Odds → Not available yet");
            }

            if (m.getOver25Odds() != null) {
                System.out.printf("║         Goals → O2.5: %-5.2f  U2.5: %-5.2f  O1.5: %-5.2f%n",
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