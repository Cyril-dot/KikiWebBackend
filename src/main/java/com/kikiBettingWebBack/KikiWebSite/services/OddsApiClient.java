package com.kikiBettingWebBack.KikiWebSite.services;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiFunction;

@Component
@Slf4j
public class OddsApiClient {

    // ══════════════════════════════════════════════════════════════
    // HARDCODED API KEYS — replace with your real keys
    // Key2 is optional — leave as empty string if you only have one
    // ══════════════════════════════════════════════════════════════

    private static final String ODDS_API_KEY_1   = "1436d615d4e1161c27cd9eaac9362a83";
    private static final String ODDS_API_KEY_2   = "d98835812b3540078d9263650e431cd6";   // optional second key
    private static final String ODDS_API_BASE    = "https://api.the-odds-api.com/v4";

    private final ObjectMapper      objectMapper;
    private final WebClient.Builder webClientBuilder;
    private final AtomicInteger     keyIndex = new AtomicInteger(0);

    public OddsApiClient(ObjectMapper objectMapper, WebClient.Builder webClientBuilder) {
        this.objectMapper     = objectMapper;
        this.webClientBuilder = webClientBuilder;
    }

    // ══════════════════════════════════════════════════════════════
    // PUBLIC METHODS
    // ══════════════════════════════════════════════════════════════

    /**
     * Fetch h2h + totals odds for a given sport key and region.
     * sportKey examples: "soccer_epl", "soccer_spain_la_liga",
     *                    "soccer_italy_serie_a", "soccer_germany_bundesliga",
     *                    "soccer_france_ligue_one", "soccer_uefa_champs_league"
     */
    public JsonNode getOddsForSport(String sportKey, String region) {
        log.info("📊 [ODDS] Fetching odds — sport={} region={}", sportKey, region);
        return executeWithFallback((client, key) -> client.get()
                .uri(u -> u.path("/sports/{sport}/odds")
                        .queryParam("apiKey",     key)
                        .queryParam("regions",    region)
                        .queryParam("markets",    "h2h,totals")
                        .queryParam("oddsFormat", "decimal")
                        .build(sportKey))
                .retrieve()
                .bodyToMono(String.class)
                .block());
    }

    /**
     * Fetch odds for all major soccer leagues at once.
     * Returns a map: sportKey → JsonNode (array of events with odds)
     */
    public java.util.Map<String, JsonNode> getAllSoccerOdds(String region) {
        List<String> soccerKeys = List.of(
                "soccer_epl",
                "soccer_spain_la_liga",
                "soccer_italy_serie_a",
                "soccer_germany_bundesliga",
                "soccer_france_ligue_one",
                "soccer_uefa_champs_league",
                "soccer_uefa_europa_league",
                "soccer_africa_cup_of_nations"
        );

        java.util.Map<String, JsonNode> result = new java.util.LinkedHashMap<>();
        for (String sportKey : soccerKeys) {
            try {
                JsonNode odds = getOddsForSport(sportKey, region);
                if (odds != null && odds.isArray() && odds.size() > 0) {
                    result.put(sportKey, odds);
                    log.info("✅ [ODDS] {} — {} events", sportKey, odds.size());
                }
                Thread.sleep(300); // avoid hitting rate limit
            } catch (Exception e) {
                log.error("❌ [ODDS] Failed for {}: {}", sportKey, e.getMessage());
            }
        }
        return result;
    }

    // ══════════════════════════════════════════════════════════════
    // CORE EXECUTOR WITH KEY FALLBACK
    // ══════════════════════════════════════════════════════════════

    private JsonNode executeWithFallback(BiFunction<WebClient, String, String> call) {
        List<String> keys = getActiveKeys();
        if (keys.isEmpty()) {
            log.warn("⚠️ [ODDS] No valid API keys configured — returning empty");
            return objectMapper.createArrayNode();
        }

        int start   = keyIndex.getAndUpdate(i -> (i + 1) % keys.size());
        List<String> ordered = new ArrayList<>();
        for (int i = 0; i < keys.size(); i++) {
            ordered.add(keys.get((start + i) % keys.size()));
        }

        for (int i = 0; i < ordered.size(); i++) {
            String key   = ordered.get(i);
            String label = "odds-key" + (i + 1);
            try {
                log.debug("🔑 [ODDS] Trying {}", label);
                String response = call.apply(client(), key);
                if (response == null || response.isBlank() || response.equals("{}")) {
                    log.warn("⚠️ [ODDS] {} returned empty, trying next...", label);
                    continue;
                }
                JsonNode node = objectMapper.readTree(response);
                log.debug("✅ [ODDS] {} succeeded", label);
                return node;
            } catch (WebClientResponseException e) {
                int status = e.getStatusCode().value();
                if (status == 401 || status == 403) {
                    log.warn("⚠️ [ODDS] {} — invalid key (HTTP {}), trying next...", label, status);
                } else if (status == 429) {
                    log.warn("⚠️ [ODDS] {} — quota exhausted (HTTP 429), trying next...", label);
                } else {
                    log.error("❌ [ODDS] {} — HTTP {}: {}", label, status, e.getMessage());
                }
                if (i == ordered.size() - 1) log.error("🚨 [ODDS] All API keys exhausted!");
            } catch (Exception e) {
                log.warn("⚠️ [ODDS] {} threw: {}", label, e.getMessage());
                if (i == ordered.size() - 1) log.error("🚨 [ODDS] All API keys exhausted!");
            }
        }
        return objectMapper.createArrayNode();
    }

    // ══════════════════════════════════════════════════════════════
    // HELPERS
    // ══════════════════════════════════════════════════════════════

    private List<String> getActiveKeys() {
        List<String> active = new ArrayList<>();
        if (ODDS_API_KEY_1 != null && !ODDS_API_KEY_1.isBlank()
                && !ODDS_API_KEY_1.equals("YOUR_ODDS_API_KEY_1_HERE")) {
            active.add(ODDS_API_KEY_1);
        }
        if (ODDS_API_KEY_2 != null && !ODDS_API_KEY_2.isBlank()) {
            active.add(ODDS_API_KEY_2);
        }
        return active;
    }

    private WebClient client() {
        return webClientBuilder.clone()
                .baseUrl(ODDS_API_BASE)
                .build();
    }
}