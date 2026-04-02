package com.kikiBettingWebBack.KikiWebSite.services;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.function.Function;

@Component
@Slf4j
public class ApiFootballClient {

    // ══════════════════════════════════════════════════════════════
    // HARDCODED API KEYS — replace with your real keys
    // ══════════════════════════════════════════════════════════════

    // football-data.org
    private static final String FD_API_KEY  = "3df294e03628423ab00ca624a294fe2f";
    private static final String FD_BASE_URL = "https://api.football-data.org/v4";

    // api-sports.io (api-football)
    private static final String AF_API_KEY  = "9d6a776df560c09ed3b71cad0b6406e9";
    private static final String AF_BASE_URL = "https://v3.football.api-sports.io";

    private final ObjectMapper        objectMapper;
    private final WebClient.Builder   webClientBuilder;

    public ApiFootballClient(ObjectMapper objectMapper, WebClient.Builder webClientBuilder) {
        this.objectMapper      = objectMapper;
        this.webClientBuilder  = webClientBuilder;
    }

    // ══════════════════════════════════════════════════════════════
    // PUBLIC METHODS
    // ══════════════════════════════════════════════════════════════

    /**
     * Live / in-play fixtures from both sources, merged and deduped.
     */
    public JsonNode getLiveFixtures() {
        log.info("🔴 [LIVE] Fetching live fixtures from all sources...");
        ArrayNode merged = objectMapper.createArrayNode();

        // ── Source 1: football-data.org ───────────────────────────
        try {
            JsonNode fdResponse = executeFd(c -> c.get()
                    .uri(u -> u.path("/matches")
                            .queryParam("status", "IN_PLAY,PAUSED")
                            .build())
                    .retrieve()
                    .bodyToMono(String.class)
                    .block());

            if (fdResponse.has("matches")) {
                int count = 0;
                for (JsonNode m : fdResponse.get("matches")) {
                    merged.add(m);
                    count++;
                }
                log.info("✅ [FD][LIVE] {} matches fetched", count);
            }
        } catch (Exception e) {
            log.error("❌ [FD][LIVE] Failed: {}", e.getMessage());
        }

        // ── Source 2: api-sports ──────────────────────────────────
        if (isApiSportsEnabled()) {
            try {
                JsonNode afResponse = executeAf(c -> c.get()
                        .uri(u -> u.path("/fixtures")
                                .queryParam("live", "all")
                                .build())
                        .retrieve()
                        .bodyToMono(String.class)
                        .block());

                if (afResponse.has("response")) {
                    int count = 0;
                    for (JsonNode f : afResponse.get("response")) {
                        merged.add(normaliseAfFixture(f));
                        count++;
                    }
                    log.info("✅ [AF][LIVE] {} matches fetched", count);
                }
            } catch (Exception e) {
                log.error("❌ [AF][LIVE] Failed: {}", e.getMessage());
            }
        }

        ArrayNode deduped = deduplicate(merged);
        log.info("📦 [LIVE] Total after dedup: {}", deduped.size());
        return wrap(deduped);
    }

    /**
     * Today's fixtures from both sources, merged and deduped.
     */
    public JsonNode getTodayFixtures() {
        String today = LocalDate.now().toString();
        log.info("📅 [TODAY] Fetching fixtures for {}", today);
        return getFixturesByDateRange(today, today);
    }

    /**
     * Upcoming fixtures — today+1 through today+7.
     */
    public JsonNode getUpcomingFixtures() {
        String from = LocalDate.now().plusDays(1).toString();
        String to   = LocalDate.now().plusDays(7).toString();
        log.info("📅 [UPCOMING] Fetching fixtures {} → {}", from, to);
        return getFixturesByDateRange(from, to);
    }

    /**
     * Fixtures across a date range from both sources, merged and deduped.
     */
    public JsonNode getFixturesByDateRange(String dateFrom, String dateTo) {
        ArrayNode merged = objectMapper.createArrayNode();

        // ── Source 1: football-data.org ───────────────────────────
        try {
            JsonNode fdResponse = executeFd(c -> c.get()
                    .uri(u -> u.path("/matches")
                            .queryParam("dateFrom", dateFrom)
                            .queryParam("dateTo",   dateTo)
                            .build())
                    .retrieve()
                    .bodyToMono(String.class)
                    .block());

            if (fdResponse.has("matches")) {
                int count = 0;
                for (JsonNode m : fdResponse.get("matches")) {
                    merged.add(m);
                    count++;
                }
                log.info("✅ [FD] {} matches fetched ({} → {})", count, dateFrom, dateTo);
            }
        } catch (Exception e) {
            log.error("❌ [FD] Failed to fetch range {}-{}: {}", dateFrom, dateTo, e.getMessage());
        }

        // ── Source 2: api-sports (one call per day) ───────────────
        if (isApiSportsEnabled()) {
            try {
                LocalDate from = LocalDate.parse(dateFrom);
                LocalDate to   = LocalDate.parse(dateTo);

                for (LocalDate date = from; !date.isAfter(to); date = date.plusDays(1)) {
                    final String dateStr = date.toString();
                    try {
                        JsonNode afResponse = executeAf(c -> c.get()
                                .uri(u -> u.path("/fixtures")
                                        .queryParam("date", dateStr)
                                        .build())
                                .retrieve()
                                .bodyToMono(String.class)
                                .block());

                        if (afResponse.has("response")) {
                            int count = 0;
                            for (JsonNode f : afResponse.get("response")) {
                                merged.add(normaliseAfFixture(f));
                                count++;
                            }
                            log.info("✅ [AF] {} matches fetched for {}", count, dateStr);
                        }
                        Thread.sleep(200); // respect rate limit
                    } catch (Exception inner) {
                        log.error("❌ [AF] Failed for date {}: {}", dateStr, inner.getMessage());
                    }
                }
            } catch (Exception e) {
                log.error("❌ [AF] Date range loop failed: {}", e.getMessage());
            }
        }

        ArrayNode deduped = deduplicate(merged);
        log.info("📦 Total after dedup ({} → {}): {}", dateFrom, dateTo, deduped.size());
        return wrap(deduped);
    }

    // ══════════════════════════════════════════════════════════════
    // NORMALISER — api-sports → football-data.org shape
    // ══════════════════════════════════════════════════════════════

    private JsonNode normaliseAfFixture(JsonNode af) {
        var out = objectMapper.createObjectNode();

        // ID prefixed with "af-" to avoid collisions with football-data IDs
        String fixtureId = af.path("fixture").path("id").asText();
        out.put("id",     "af-" + fixtureId);
        out.put("source", "api-sports");

        // Date / kickoff
        String date = af.path("fixture").path("date").asText();
        if (date.length() >= 19) out.put("utcDate", date.substring(0, 19));

        // Status
        String shortStatus = af.path("fixture").path("status").path("short").asText();
        out.put("status",  mapAfStatus(shortStatus));
        out.put("minute",  af.path("fixture").path("status").path("elapsed").asInt(0));

        // Home team
        var home = objectMapper.createObjectNode();
        home.put("id",    af.path("teams").path("home").path("id").asInt(0));
        home.put("name",  af.path("teams").path("home").path("name").asText());
        home.put("crest", af.path("teams").path("home").path("logo").asText());
        out.set("homeTeam", home);

        // Away team
        var away = objectMapper.createObjectNode();
        away.put("id",    af.path("teams").path("away").path("id").asInt(0));
        away.put("name",  af.path("teams").path("away").path("name").asText());
        away.put("crest", af.path("teams").path("away").path("logo").asText());
        out.set("awayTeam", away);

        // Competition
        var comp = objectMapper.createObjectNode();
        comp.put("id",     af.path("league").path("id").asInt(0));
        comp.put("name",   af.path("league").path("name").asText());
        comp.put("emblem", af.path("league").path("logo").asText());
        out.set("competition", comp);

        // Area
        var area = objectMapper.createObjectNode();
        area.put("name", af.path("league").path("country").asText());
        out.set("area", area);

        // Score
        var score    = objectMapper.createObjectNode();
        var fullTime = objectMapper.createObjectNode();
        JsonNode goals = af.path("goals");
        if (!goals.path("home").isNull() && !goals.path("home").isMissingNode()) {
            fullTime.put("home", goals.path("home").asInt());
            fullTime.put("away", goals.path("away").asInt());
        } else {
            fullTime.putNull("home");
            fullTime.putNull("away");
        }
        score.set("fullTime", fullTime);
        out.set("score", score);

        return out;
    }

    private String mapAfStatus(String s) {
        return switch (s) {
            case "NS", "TBD"           -> "SCHEDULED";
            case "1H", "2H", "ET", "P" -> "IN_PLAY";
            case "HT"                  -> "PAUSED";
            case "FT", "AET", "PEN"    -> "FINISHED";
            case "SUSP", "INT"         -> "SUSPENDED";
            case "PST"                 -> "POSTPONED";
            case "CANC", "ABD"         -> "CANCELLED";
            default                    -> "SCHEDULED";
        };
    }

    // ══════════════════════════════════════════════════════════════
    // DEDUPLICATION
    // ══════════════════════════════════════════════════════════════

    private ArrayNode deduplicate(ArrayNode matches) {
        ArrayNode result = objectMapper.createArrayNode();
        Set<String> seen = new LinkedHashSet<>();
        for (JsonNode m : matches) {
            String home = norm(m.path("homeTeam").path("name").asText());
            String away = norm(m.path("awayTeam").path("name").asText());
            String date = m.path("utcDate").asText();
            if (date.length() >= 10) date = date.substring(0, 10);
            String key = home + "|" + away + "|" + date;
            if (seen.add(key)) {
                result.add(m);
            } else {
                log.debug("🔁 Duplicate skipped: {} vs {} on {}", home, away, date);
            }
        }
        return result;
    }

    private String norm(String name) {
        return name.toLowerCase()
                .replace(" fc","").replace("fc ","")
                .replace(" afc","").replace("afc ","")
                .replace(" cf","").replace(" sc","")
                .replace(" united","").replace(" city","")
                .replace("manchester ","man ")
                .replaceAll("[^a-z0-9]","").trim();
    }

    // ══════════════════════════════════════════════════════════════
    // HTTP CLIENTS
    // ══════════════════════════════════════════════════════════════

    private WebClient fdClient() {
        return webClientBuilder.clone()
                .baseUrl(FD_BASE_URL)
                .defaultHeader("X-Auth-Token", FD_API_KEY)
                .build();
    }

    private WebClient afClient() {
        return webClientBuilder.clone()
                .baseUrl(AF_BASE_URL)
                .defaultHeader("x-apisports-key", AF_API_KEY)
                .build();
    }

    private JsonNode executeFd(Function<WebClient, String> call) {
        try {
            String response = call.apply(fdClient());
            if (response == null || response.isBlank()) return objectMapper.createObjectNode();
            return objectMapper.readTree(response);
        } catch (WebClientResponseException e) {
            int status = e.getStatusCode().value();
            if (status == 429)                       log.warn("⚠️ [FD] Rate limit hit");
            else if (status == 401 || status == 403) log.error("❌ [FD] Auth error — check FD_API_KEY");
            else                                     log.error("❌ [FD] HTTP {}: {}", status, e.getMessage());
            return objectMapper.createObjectNode();
        } catch (Exception e) {
            log.error("❌ [FD] Unexpected: {}", e.getMessage());
            return objectMapper.createObjectNode();
        }
    }

    private JsonNode executeAf(Function<WebClient, String> call) {
        try {
            String response = call.apply(afClient());
            if (response == null || response.isBlank()) return objectMapper.createObjectNode();
            return objectMapper.readTree(response);
        } catch (WebClientResponseException e) {
            int status = e.getStatusCode().value();
            if (status == 429)                       log.warn("⚠️ [AF] Rate limit — 10 req/min free tier");
            else if (status == 401 || status == 403) log.error("❌ [AF] Auth error — check AF_API_KEY");
            else                                     log.error("❌ [AF] HTTP {}: {}", status, e.getMessage());
            return objectMapper.createObjectNode();
        } catch (Exception e) {
            log.error("❌ [AF] Unexpected: {}", e.getMessage());
            return objectMapper.createObjectNode();
        }
    }

    private boolean isApiSportsEnabled() {
        return AF_API_KEY != null && !AF_API_KEY.isBlank()
                && !AF_API_KEY.equals("YOUR_API_SPORTS_KEY_HERE");
    }

    private JsonNode wrap(ArrayNode matches) {
        var wrapper = objectMapper.createObjectNode();
        wrapper.set("matches", matches);
        return wrapper;
    }
}