package com.kikiBettingWebBack.KikiWebSite.services;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;

/**
 * Fetches odds from BSD (Bzzoiro Sports Data) /api/events/
 *
 * BSD returns odds directly inside each event object:
 *   odds_home    → home win decimal odds
 *   odds_draw    → draw decimal odds
 *   odds_away    → away win decimal odds
 *   odds_over_15 → over 1.5 goals
 *   odds_over_25 → over 2.5 goals
 *   odds_over_35 → over 3.5 goals
 *   odds_btts    → both teams to score
 *
 * This class replaces the old The Odds API integration.
 * It keeps the same public interface (getAllSoccerOdds, getOddsForSport)
 * so all callers (MatchMapper, LiveGamesService, etc.) work unchanged.
 */
@Component
@Slf4j
public class OddsApiClient {

    private static final String BSD_API_KEY  = "b72b9fd801323f6c22892218cd687fedf109ef91";
    private static final String BSD_BASE_URL = "https://sports.bzzoiro.com";

    private final ObjectMapper      objectMapper;
    private final WebClient.Builder webClientBuilder;

    public OddsApiClient(ObjectMapper objectMapper, WebClient.Builder webClientBuilder) {
        this.objectMapper     = objectMapper;
        this.webClientBuilder = webClientBuilder;
    }

    // ══════════════════════════════════════════════════════════════
    // PUBLIC API — same signature as before so callers don't change
    // ══════════════════════════════════════════════════════════════

    /**
     * Fetch odds for all soccer events in a date window (today + 7 days).
     * Returns a single-entry map keyed "bsd_soccer" → array of BSD events.
     * The region param is ignored (BSD is region-agnostic).
     */
    public Map<String, JsonNode> getAllSoccerOdds(String ignoredRegion) {
        log.info("📊 [ODDS/BSD] Fetching all soccer odds from BSD events...");
        Map<String, JsonNode> result = new LinkedHashMap<>();

        try {
            String today  = LocalDate.now().toString();
            String inWeek = LocalDate.now().plusDays(7).toString();

            JsonNode events = fetchEvents(today, inWeek);

            if (events != null && events.isArray() && events.size() > 0) {
                // Filter to only events that actually have odds
                var withOdds = objectMapper.createArrayNode();
                for (JsonNode e : events) {
                    if (hasOdds(e)) withOdds.add(e);
                }
                result.put("bsd_soccer", withOdds);
                log.info("✅ [ODDS/BSD] {} events fetched, {} have odds", events.size(), withOdds.size());
            } else {
                log.info("ℹ️ [ODDS/BSD] No events returned from BSD");
            }

        } catch (Exception e) {
            log.error("❌ [ODDS/BSD] getAllSoccerOdds failed: {}", e.getMessage());
        }

        return result;
    }

    /**
     * Fetch odds for a specific sport key (legacy signature kept for compatibility).
     * BSD doesn't filter by sport key, so we return all soccer events.
     * The sportKey param is ignored.
     */
    public JsonNode getOddsForSport(String ignoredSportKey, String ignoredRegion) {
        log.info("📊 [ODDS/BSD] getOddsForSport called — fetching from BSD...");
        Map<String, JsonNode> all = getAllSoccerOdds(ignoredRegion);
        return all.getOrDefault("bsd_soccer", objectMapper.createArrayNode());
    }

    // ══════════════════════════════════════════════════════════════
    // BSD EVENTS FETCHER — handles pagination
    // ══════════════════════════════════════════════════════════════

    private JsonNode fetchEvents(String dateFrom, String dateTo) {
        var allResults = objectMapper.createArrayNode();
        String nextUrl = null;
        int page = 1;

        // First page
        try {
            JsonNode response = executeBsd(c -> c.get()
                    .uri(u -> u.path("/api/events/")
                            .queryParam("date_from", dateFrom)
                            .queryParam("date_to",   dateTo)
                            .build())
                    .retrieve()
                    .bodyToMono(String.class)
                    .block());

            JsonNode results = response.path("results");
            if (results.isArray()) {
                for (JsonNode r : results) allResults.add(r);
            }
            nextUrl = response.path("next").isNull() ? null : response.path("next").asText(null);
            log.debug("📄 [ODDS/BSD] Page {} — {} events", page, results.size());

        } catch (Exception e) {
            log.error("❌ [ODDS/BSD] Failed fetching page {}: {}", page, e.getMessage());
            return allResults;
        }

        // Additional pages (pagination)
        while (nextUrl != null && !nextUrl.isBlank()) {
            page++;
            final String url = nextUrl;
            try {
                Thread.sleep(150); // be polite
                JsonNode response = executeBsd(c -> c.get()
                        .uri(url)
                        .retrieve()
                        .bodyToMono(String.class)
                        .block());

                JsonNode results = response.path("results");
                if (results.isArray()) {
                    for (JsonNode r : results) allResults.add(r);
                }
                nextUrl = response.path("next").isNull() ? null : response.path("next").asText(null);
                log.debug("📄 [ODDS/BSD] Page {} — {} events", page, results.size());

            } catch (Exception e) {
                log.error("❌ [ODDS/BSD] Failed fetching page {}: {}", page, e.getMessage());
                break;
            }
        }

        log.info("✅ [ODDS/BSD] Total events fetched across {} page(s): {}", page, allResults.size());
        return allResults;
    }

    // ══════════════════════════════════════════════════════════════
    // HELPERS
    // ══════════════════════════════════════════════════════════════

    /**
     * Check if a BSD event has at least home odds populated.
     * BSD fields: odds_home, odds_draw, odds_away, odds_over_15,
     *             odds_over_25, odds_over_35, odds_btts
     */
    private boolean hasOdds(JsonNode event) {
        JsonNode oddsHome = event.path("odds_home");
        return !oddsHome.isNull() && !oddsHome.isMissingNode() && oddsHome.asDouble(0) > 0;
    }

    private JsonNode executeBsd(Function<WebClient, String> call) {
        try {
            String response = call.apply(bsdClient());
            if (response == null || response.isBlank()) return objectMapper.createObjectNode();
            return objectMapper.readTree(response);
        } catch (WebClientResponseException e) {
            int status = e.getStatusCode().value();
            if (status == 401 || status == 403) log.error("❌ [ODDS/BSD] Auth error {} — check BSD_API_KEY", status);
            else if (status == 429)             log.warn("⚠️ [ODDS/BSD] Rate limit hit");
            else                                log.error("❌ [ODDS/BSD] HTTP {}: {}", status, e.getMessage());
            return objectMapper.createObjectNode();
        } catch (Exception e) {
            log.error("❌ [ODDS/BSD] Unexpected: {}", e.getMessage());
            return objectMapper.createObjectNode();
        }
    }

    private WebClient bsdClient() {
        return webClientBuilder.clone()
                .baseUrl(BSD_BASE_URL)
                .defaultHeader("Authorization", "Token " + BSD_API_KEY)
                .build();
    }
}