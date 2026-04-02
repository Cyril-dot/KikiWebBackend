package com.kikiBettingWebBack.KikiWebSite.services;

import com.fasterxml.jackson.databind.JsonNode;
import com.kikiBettingWebBack.KikiWebSite.dtos.NewMatchDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

/**
 * Converts raw JsonNode (football-data.org normalised shape) + optional
 * odds JsonNode (The-Odds-API event) → MatchDTO.
 *
 * No DB interaction. Pure mapping.
 */
@Component
@Slf4j
public class MatchMapper {

    private static final DateTimeFormatter FMT_ZULU  =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");
    private static final DateTimeFormatter FMT_ZULU2 =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm");

    // ══════════════════════════════════════════════════════════════
    // MAIN MAPPING METHOD
    // ══════════════════════════════════════════════════════════════

    /**
     * @param match  raw fixture node (football-data.org shape or normalised api-sports)
     * @param odds   The-Odds-API event node for this match (may be null)
     */
    public NewMatchDTO toDto(JsonNode match, JsonNode odds) {

        String status  = match.path("status").asText("SCHEDULED");
        int    elapsed = match.path("minute").asInt(0);

        NewMatchDTO.NewMatchDTOBuilder b = NewMatchDTO.builder()
                .externalFixtureId(match.path("id").asText())
                .source(match.has("source") ? match.path("source").asText() : "football-data")
                .homeTeam(match.path("homeTeam").path("name").asText())
                .awayTeam(match.path("awayTeam").path("name").asText())
                .homeLogo(match.path("homeTeam").path("crest").asText())
                .awayLogo(match.path("awayTeam").path("crest").asText())
                .league(match.path("competition").path("name").asText())
                .country(match.path("area").path("name").asText())
                .leagueLogo(match.path("competition").path("emblem").asText())
                .kickoffTime(parseDate(match.path("utcDate").asText()))
                .status(mapStatus(status))
                .matchPeriod(resolveMatchPeriod(status, elapsed))
                .elapsedMinutes(elapsed);

        // ── Score ─────────────────────────────────────────────────
        JsonNode fullTime = match.path("score").path("fullTime");
        if (!fullTime.path("home").isNull() && !fullTime.path("home").isMissingNode()) {
            b.homeScore(fullTime.path("home").asInt());
            b.awayScore(fullTime.path("away").asInt());
        }

        // ── Odds from The-Odds-API ────────────────────────────────
        if (odds != null && !odds.isMissingNode() && !odds.isNull()) {
            enrichWithOdds(b, odds,
                    match.path("homeTeam").path("name").asText(),
                    match.path("awayTeam").path("name").asText());
        }

        return b.build();
    }

    // ══════════════════════════════════════════════════════════════
    // ODDS ENRICHMENT
    // The-Odds-API event shape:
    // { "id":"...", "home_team":"...", "away_team":"...",
    //   "bookmakers": [ { "title":"...", "markets": [
    //       { "key":"h2h",    "outcomes": [{ "name":"...", "price": 1.9 }] },
    //       { "key":"totals", "outcomes": [{ "name":"Over","point":2.5,"price":1.8 }] }
    //   ]} ] }
    // ══════════════════════════════════════════════════════════════

    private void enrichWithOdds(NewMatchDTO.NewMatchDTOBuilder b,
                                 JsonNode oddsEvent,
                                 String homeTeamName,
                                 String awayTeamName) {
        try {
            JsonNode bookmakers = oddsEvent.path("bookmakers");
            if (bookmakers.isMissingNode() || bookmakers.isEmpty()) return;

            // Use the first bookmaker that has both h2h and totals
            for (JsonNode bookmaker : bookmakers) {
                boolean h2hDone    = false;
                boolean totalsDone = false;

                for (JsonNode market : bookmaker.path("markets")) {
                    String key = market.path("key").asText();

                    if (key.equals("h2h") && !h2hDone) {
                        for (JsonNode outcome : market.path("outcomes")) {
                            String name  = outcome.path("name").asText();
                            double price = outcome.path("price").asDouble(0);
                            if (price <= 0) continue;
                            if (name.equalsIgnoreCase(homeTeamName))  b.homeWinOdds(price);
                            else if (name.equalsIgnoreCase(awayTeamName)) b.awayWinOdds(price);
                            else b.drawOdds(price);
                        }
                        b.oddsBookmaker(bookmaker.path("title").asText());
                        h2hDone = true;
                    }

                    if (key.equals("totals") && !totalsDone) {
                        for (JsonNode outcome : market.path("outcomes")) {
                            String name  = outcome.path("name").asText();
                            double point = outcome.path("point").asDouble(0);
                            double price = outcome.path("price").asDouble(0);
                            if (price <= 0) continue;

                            if (point == 1.5) {
                                if (name.equals("Over"))  b.over15Odds(price);
                                if (name.equals("Under")) b.under15Odds(price);
                            } else if (point == 2.5) {
                                if (name.equals("Over"))  b.over25Odds(price);
                                if (name.equals("Under")) b.under25Odds(price);
                            } else if (point == 3.5) {
                                if (name.equals("Over"))  b.over35Odds(price);
                                if (name.equals("Under")) b.under35Odds(price);
                            }
                        }
                        totalsDone = true;
                    }
                }

                if (h2hDone) break; // first bookmaker with h2h is enough
            }
        } catch (Exception e) {
            log.warn("⚠️ [MAPPER] Failed to enrich odds: {}", e.getMessage());
        }
    }

    // ══════════════════════════════════════════════════════════════
    // ODDS MATCHING HELPERS
    // ══════════════════════════════════════════════════════════════

    /**
     * Tries to find a matching odds event for a fixture by normalising team names.
     * Returns null if no match found.
     */
    public JsonNode findMatchingOddsEvent(JsonNode fixtureMatch,
                                          java.util.List<JsonNode> oddsEvents) {
        if (oddsEvents == null || oddsEvents.isEmpty()) return null;

        String homeNorm = norm(fixtureMatch.path("homeTeam").path("name").asText());
        String awayNorm = norm(fixtureMatch.path("awayTeam").path("name").asText());

        for (JsonNode event : oddsEvents) {
            String oddsHome = norm(event.path("home_team").asText());
            String oddsAway = norm(event.path("away_team").asText());

            if (oddsHome.equals(homeNorm) && oddsAway.equals(awayNorm)) {
                return event;
            }
            // Try reversed (some APIs swap home/away)
            if (oddsHome.equals(awayNorm) && oddsAway.equals(homeNorm)) {
                return event;
            }
        }
        return null;
    }

    // ══════════════════════════════════════════════════════════════
    // STATUS / PERIOD HELPERS
    // ══════════════════════════════════════════════════════════════

    private String mapStatus(String status) {
        return switch (status.toUpperCase()) {
            case "SCHEDULED", "TIMED"         -> "UPCOMING";
            case "IN_PLAY", "PAUSED"          -> "LIVE";
            case "FINISHED"                   -> "FINISHED";
            case "CANCELLED", "SUSPENDED"     -> "CANCELLED";
            case "POSTPONED"                  -> "POSTPONED";
            default                           -> "UPCOMING";
        };
    }

    private String resolveMatchPeriod(String status, int elapsed) {
        return switch (status.toUpperCase()) {
            case "IN_PLAY"  -> elapsed > 0 && elapsed <= 45 ? "1st Half" : "2nd Half";
            case "PAUSED"   -> "Half Time";
            case "FINISHED" -> "Full Time";
            case "POSTPONED"-> "Postponed";
            case "CANCELLED",
                 "SUSPENDED"-> "Cancelled";
            default         -> "Not Started";
        };
    }

    // ══════════════════════════════════════════════════════════════
    // DATE PARSING
    // ══════════════════════════════════════════════════════════════

    private LocalDateTime parseDate(String raw) {
        if (raw == null || raw.isBlank()) return null;
        String cleaned = raw.replace("Z", "").trim();
        if (cleaned.length() > 19) cleaned = cleaned.substring(0, 19);
        try {
            return LocalDateTime.parse(cleaned, FMT_ZULU);
        } catch (DateTimeParseException e1) {
            try {
                return LocalDateTime.parse(cleaned, FMT_ZULU2);
            } catch (DateTimeParseException e2) {
                log.warn("⚠️ [MAPPER] Could not parse date: '{}'", raw);
                return null;
            }
        }
    }

    // ══════════════════════════════════════════════════════════════
    // TEAM NAME NORMALISATION (for odds matching)
    // ══════════════════════════════════════════════════════════════

    private String norm(String name) {
        if (name == null) return "";
        return name.toLowerCase()
                .replace(" fc","").replace("fc ","")
                .replace(" afc","").replace("afc ","")
                .replace(" cf","").replace(" sc","")
                .replace(" united","").replace(" city","")
                .replace("manchester ","man ")
                .replace("paris saint-germain","psg")
                .replace("paris sg","psg")
                .replace("atletico","atletico")
                .replaceAll("[^a-z0-9]","").trim();
    }
}