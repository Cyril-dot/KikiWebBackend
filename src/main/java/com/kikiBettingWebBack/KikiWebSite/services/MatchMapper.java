package com.kikiBettingWebBack.KikiWebSite.services;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.kikiBettingWebBack.KikiWebSite.dtos.NewMatchDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;

/**
 * Converts a normalised BSD event JsonNode → NewMatchDTO.
 *
 * BSD odds fields (flat on the event, normalised into an "odds" wrapper
 * by ApiFootballClient):
 *   odds.home    → home win decimal odds
 *   odds.draw    → draw decimal odds
 *   odds.away    → away win decimal odds
 *   odds.over15  → over 1.5 goals
 *   odds.over25  → over 2.5 goals
 *   odds.over35  → over 3.5 goals
 *   odds.btts    → both teams to score (informational — no DTO field)
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class MatchMapper {

    private final ObjectMapper objectMapper;  // injected by Spring — no new ObjectMapper()

    private static final DateTimeFormatter FMT_ZULU =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");
    private static final DateTimeFormatter FMT_SHORT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm");

    // ══════════════════════════════════════════════════════════════
    // MAIN MAPPING METHOD
    // ══════════════════════════════════════════════════════════════

    public NewMatchDTO toDto(JsonNode match, JsonNode odds) {

        String status  = match.path("status").asText("SCHEDULED");
        int    elapsed = match.path("minute").asInt(0);
        String period  = match.path("period").asText("");

        NewMatchDTO.NewMatchDTOBuilder b = NewMatchDTO.builder()
                .externalFixtureId(match.path("id").asText())
                .source(match.has("source") ? match.path("source").asText() : "bsd")
                .homeTeam(match.path("homeTeam").path("name").asText())
                .awayTeam(match.path("awayTeam").path("name").asText())
                .homeLogo(match.path("homeTeam").path("crest").asText())
                .awayLogo(match.path("awayTeam").path("crest").asText())
                .league(match.path("competition").path("name").asText())
                .country(match.path("area").path("name").asText())
                .leagueLogo(match.path("competition").path("emblem").asText())
                .kickoffTime(parseDate(match.path("utcDate").asText()))
                .status(mapStatus(status))
                .matchPeriod(resolveMatchPeriod(status, elapsed, period))
                .elapsedMinutes(elapsed);

        // ── Score ─────────────────────────────────────────────────
        JsonNode fullTime = match.path("score").path("fullTime");
        if (!fullTime.path("home").isNull() && !fullTime.path("home").isMissingNode()) {
            b.homeScore(fullTime.path("home").asInt());
            b.awayScore(fullTime.path("away").asInt());
        }

        // ── Odds ──────────────────────────────────────────────────
        // Priority 1: normalised odds wrapper already on the match node
        JsonNode oddsNode = match.path("odds");
        if (!oddsNode.isMissingNode() && !oddsNode.isNull()) {
            enrichWithOdds(b, oddsNode);
        }
        // Priority 2: separate odds event from OddsApiClient (raw BSD event)
        else if (odds != null && !odds.isMissingNode() && !odds.isNull()) {
            enrichWithOdds(b, rawBsdToOddsNode(odds));
        }

        return b.build();
    }

    // ══════════════════════════════════════════════════════════════
    // ODDS ENRICHMENT
    // Reads from normalised odds node: { home, draw, away,
    //                                    over15, over25, over35 }
    // ══════════════════════════════════════════════════════════════

    private void enrichWithOdds(NewMatchDTO.NewMatchDTOBuilder b, JsonNode o) {
        try {
            double home   = o.path("home").asDouble(0);
            double draw   = o.path("draw").asDouble(0);
            double away   = o.path("away").asDouble(0);
            double over15 = o.path("over15").asDouble(0);
            double over25 = o.path("over25").asDouble(0);
            double over35 = o.path("over35").asDouble(0);

            if (home   > 0) b.homeWinOdds(home);
            if (draw   > 0) b.drawOdds(draw);
            if (away   > 0) b.awayWinOdds(away);
            if (over15 > 0) b.over15Odds(over15);
            if (over25 > 0) b.over25Odds(over25);
            if (over35 > 0) b.over35Odds(over35);

            b.oddsBookmaker("BSD");

        } catch (Exception e) {
            log.warn("⚠️ [MAPPER] Failed to enrich odds: {}", e.getMessage());
        }
    }

    /**
     * Converts a raw BSD event (odds_home, odds_draw etc) into the
     * normalised odds wrapper shape so enrichWithOdds can handle it.
     */
    private JsonNode rawBsdToOddsNode(JsonNode bsdEvent) {
        ObjectNode node = objectMapper.createObjectNode();
        copyOddsField(node, "home",   bsdEvent.path("odds_home"));
        copyOddsField(node, "draw",   bsdEvent.path("odds_draw"));
        copyOddsField(node, "away",   bsdEvent.path("odds_away"));
        copyOddsField(node, "over15", bsdEvent.path("odds_over_15"));
        copyOddsField(node, "over25", bsdEvent.path("odds_over_25"));
        copyOddsField(node, "over35", bsdEvent.path("odds_over_35"));
        return node;
    }

    private void copyOddsField(ObjectNode node, String key, JsonNode value) {
        if (!value.isNull() && !value.isMissingNode() && value.asDouble(0) > 0) {
            node.put(key, value.asDouble());
        }
    }

    // ══════════════════════════════════════════════════════════════
    // ODDS MATCHING HELPER
    // ══════════════════════════════════════════════════════════════

    public JsonNode findMatchingOddsEvent(JsonNode fixtureMatch, List<JsonNode> oddsEvents) {
        if (oddsEvents == null || oddsEvents.isEmpty()) return null;

        String homeNorm = norm(fixtureMatch.path("homeTeam").path("name").asText());
        String awayNorm = norm(fixtureMatch.path("awayTeam").path("name").asText());

        for (JsonNode event : oddsEvents) {
            String oddsHome = norm(event.path("home_team").asText());
            String oddsAway = norm(event.path("away_team").asText());
            if (oddsHome.equals(homeNorm) && oddsAway.equals(awayNorm)) return event;
            if (oddsHome.equals(awayNorm) && oddsAway.equals(homeNorm)) return event;
        }
        return null;
    }

    // ══════════════════════════════════════════════════════════════
    // STATUS / PERIOD HELPERS
    // ══════════════════════════════════════════════════════════════

    private String mapStatus(String status) {
        return switch (status.toUpperCase()) {
            case "IN_PLAY", "PAUSED"          -> "LIVE";
            case "FINISHED"                   -> "FINISHED";
            case "CANCELLED", "SUSPENDED"     -> "CANCELLED";
            case "POSTPONED"                  -> "POSTPONED";
            default                           -> "UPCOMING";
        };
    }

    private String resolveMatchPeriod(String status, int elapsed, String bsdPeriod) {
        // Use BSD period field when available
        if (bsdPeriod != null && !bsdPeriod.isBlank()) {
            return switch (bsdPeriod.toUpperCase()) {
                case "1T" -> "1st Half";
                case "HT" -> "Half Time";
                case "2T" -> "2nd Half";
                case "FT" -> "Full Time";
                default   -> resolveFromStatus(status, elapsed);
            };
        }
        return resolveFromStatus(status, elapsed);
    }

    private String resolveFromStatus(String status, int elapsed) {
        return switch (status.toUpperCase()) {
            case "IN_PLAY"               -> elapsed <= 45 ? "1st Half" : "2nd Half";
            case "PAUSED"                -> "Half Time";
            case "FINISHED"              -> "Full Time";
            case "POSTPONED"             -> "Postponed";
            case "CANCELLED","SUSPENDED" -> "Cancelled";
            default                      -> "Not Started";
        };
    }

    // ══════════════════════════════════════════════════════════════
    // DATE PARSING
    // Handles BSD format: 2026-04-03T17:30:00+0400
    // ══════════════════════════════════════════════════════════════

    private LocalDateTime parseDate(String raw) {
        if (raw == null || raw.isBlank()) return null;
        // Strip timezone offset (+0400, -0500, Z)
        String cleaned = raw.replaceAll("[+-]\\d{4}$", "")
                .replace("Z", "").trim();
        if (cleaned.length() > 19) cleaned = cleaned.substring(0, 19);
        try {
            return LocalDateTime.parse(cleaned, FMT_ZULU);
        } catch (DateTimeParseException e1) {
            try {
                return LocalDateTime.parse(cleaned, FMT_SHORT);
            } catch (DateTimeParseException e2) {
                log.warn("⚠️ [MAPPER] Could not parse date: '{}'", raw);
                return null;
            }
        }
    }

    // ══════════════════════════════════════════════════════════════
    // TEAM NAME NORMALISATION
    // ══════════════════════════════════════════════════════════════

    private String norm(String name) {
        if (name == null) return "";
        return name.toLowerCase()
                .replace(" fc", "").replace("fc ", "")
                .replace(" afc", "").replace("afc ", "")
                .replace(" cf", "").replace(" sc", "")
                .replace(" united", "").replace(" city", "")
                .replace("manchester ", "man ")
                .replace("paris saint-germain", "psg")
                .replace("paris sg", "psg")
                .replaceAll("[^a-z0-9]", "").trim();
    }
}