package com.kikiBettingWebBack.KikiWebSite.services;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.time.LocalDate;
import java.util.function.Function;

@Component
@Slf4j
public class ApiFootballClient {

    // ── BSD (Bzzoiro Sports Data) — LIVE GAMES ONLY ───────────────
    private static final String BSD_API_KEY  = "b72b9fd801323f6c22892218cd687fedf109ef91";
    private static final String BSD_BASE_URL = "https://sports.bzzoiro.com";

    // ── TheSportsDB — TODAY + UPCOMING FIXTURES ───────────────────
    private static final String TSDB_API_KEY  = "123";
    private static final String TSDB_BASE_URL = "https://www.thesportsdb.com/api/v1/json/" + TSDB_API_KEY;

    private final ObjectMapper      objectMapper;
    private final WebClient.Builder webClientBuilder;

    public ApiFootballClient(ObjectMapper objectMapper, WebClient.Builder webClientBuilder) {
        this.objectMapper     = objectMapper;
        this.webClientBuilder = webClientBuilder;
    }

    // ══════════════════════════════════════════════════════════════
    // LIVE FIXTURES — BSD /api/live/
    // Only returns matches currently IN PLAY with real-time scores
    // ══════════════════════════════════════════════════════════════

    public JsonNode getLiveFixtures() {
        log.info("🔴 [LIVE] Fetching live fixtures from BSD...");
        ArrayNode matches = objectMapper.createArrayNode();

        try {
            JsonNode response = executeBsd(c -> c.get()
                    .uri("/api/live/")
                    .retrieve()
                    .bodyToMono(String.class)
                    .block());

            JsonNode results = response.path("results");
            if (!results.isArray()) {
                log.warn("⚠️ [BSD][LIVE] No results array in response");
                return wrap(matches);
            }

            for (JsonNode event : results) {
                // Only include truly active matches
                String status = event.path("status").asText("");
                if (isActiveLiveStatus(status)) {
                    matches.add(normaliseBsdLive(event));
                }
            }

            log.info("✅ [BSD][LIVE] {} active live matches", matches.size());

        } catch (Exception e) {
            log.error("❌ [BSD][LIVE] Failed: {}", e.getMessage());
        }

        return wrap(matches);
    }

    // ══════════════════════════════════════════════════════════════
    // TODAY'S FIXTURES — TheSportsDB eventsday.php
    // ══════════════════════════════════════════════════════════════

    public JsonNode getTodayFixtures() {
        String today = LocalDate.now().toString();
        log.info("📅 [TODAY] Fetching today's fixtures from TheSportsDB for {}...", today);
        return getFixturesByDate(today);
    }

    // ══════════════════════════════════════════════════════════════
    // UPCOMING FIXTURES — TheSportsDB eventsday.php next 7 days
    // ══════════════════════════════════════════════════════════════

    public JsonNode getUpcomingFixtures() {
        log.info("📅 [UPCOMING] Fetching upcoming fixtures from TheSportsDB...");
        ArrayNode merged = objectMapper.createArrayNode();

        for (int i = 1; i <= 7; i++) {
            String date = LocalDate.now().plusDays(i).toString();
            try {
                JsonNode dayResult  = getFixturesByDate(date);
                JsonNode dayMatches = dayResult.path("matches");
                if (dayMatches.isArray()) {
                    for (JsonNode m : dayMatches) merged.add(m);
                }
                Thread.sleep(200);
            } catch (Exception e) {
                log.warn("⚠️ [TSDB][UPCOMING] Failed for date {}: {}", date, e.getMessage());
            }
        }

        log.info("✅ [TSDB][UPCOMING] {} total upcoming fixtures", merged.size());
        return wrap(merged);
    }

    // ══════════════════════════════════════════════════════════════
    // DATE RANGE — TheSportsDB
    // ══════════════════════════════════════════════════════════════

    public JsonNode getFixturesByDateRange(String dateFrom, String dateTo) {
        log.info("📅 [TSDB] Fetching fixtures {} → {}", dateFrom, dateTo);
        ArrayNode merged = objectMapper.createArrayNode();

        LocalDate from = LocalDate.parse(dateFrom);
        LocalDate to   = LocalDate.parse(dateTo);

        for (LocalDate date = from; !date.isAfter(to); date = date.plusDays(1)) {
            try {
                JsonNode dayResult  = getFixturesByDate(date.toString());
                JsonNode dayMatches = dayResult.path("matches");
                if (dayMatches.isArray()) {
                    for (JsonNode m : dayMatches) merged.add(m);
                }
                Thread.sleep(200);
            } catch (Exception e) {
                log.warn("⚠️ [TSDB] Failed for date {}: {}", date, e.getMessage());
            }
        }

        return wrap(merged);
    }

    // ══════════════════════════════════════════════════════════════
    // SINGLE DATE — TheSportsDB eventsday.php
    // ══════════════════════════════════════════════════════════════

    private JsonNode getFixturesByDate(String date) {
        try {
            JsonNode response = executeTsdb(c -> c.get()
                    .uri(u -> u.path("/eventsday.php")
                            .queryParam("d", date)
                            .queryParam("s", "Soccer")
                            .build())
                    .retrieve()
                    .bodyToMono(String.class)
                    .block());

            ArrayNode matches = objectMapper.createArrayNode();
            JsonNode events   = response.path("events");

            if (events.isArray()) {
                for (JsonNode event : events) {
                    matches.add(normaliseTsdbEvent(event));
                }
                log.info("✅ [TSDB] {} events fetched for {}", matches.size(), date);
            } else {
                log.debug("ℹ️ [TSDB] No events for {}", date);
            }

            return wrap(matches);

        } catch (Exception e) {
            log.error("❌ [TSDB] Failed for date {}: {}", date, e.getMessage());
            return wrap(objectMapper.createArrayNode());
        }
    }

    // ══════════════════════════════════════════════════════════════
    // NORMALISER — BSD /api/live/ → shared DTO shape
    //
    // BSD live fields (from docs):
    //   id
    //   home_team          → string (team name)
    //   away_team          → string (team name)
    //   home_team_obj      → { id, api_id, name, short_name, country }
    //   away_team_obj      → { id, api_id, name, short_name, country }
    //   home_score         → integer
    //   away_score         → integer
    //   current_minute     → integer
    //   period             → "1T" | "HT" | "2T" | "FT"
    //   status             → "inprogress" | "1st_half" | "halftime" | "2nd_half"
    //   event_date         → ISO 8601 datetime
    //   league             → { id, api_id, name, country, season_id }
    //   incidents          → [ { type, minute, player_name, is_home } ]
    //   live_stats         → { home: { ball_possession, total_shots, ... },
    //                          away: { ... } }
    // ══════════════════════════════════════════════════════════════

    private JsonNode normaliseBsdLive(JsonNode bsd) {
        var out = objectMapper.createObjectNode();

        out.put("id",      "bsd-" + bsd.path("id").asText());
        out.put("source",  "bsd");
        out.put("utcDate", bsd.path("event_date").asText());           // ✅ event_date
        out.put("status",  "IN_PLAY");
        out.put("minute",  bsd.path("current_minute").asInt(0));       // ✅ current_minute
        out.put("period",  bsd.path("period").asText(""));

        // ── Home team ─────────────────────────────────────────────
        var home    = objectMapper.createObjectNode();
        JsonNode homeObj  = bsd.path("home_team_obj");
        int      homeApiId = homeObj.path("api_id").asInt(0);
        home.put("id",    homeObj.path("id").asInt(0));
        home.put("apiId", homeApiId);
        home.put("name",  bsd.path("home_team").asText());             // ✅ string name
        home.put("crest", homeApiId > 0
                ? BSD_BASE_URL + "/img/team/" + homeApiId + "/"
                : "");
        out.set("homeTeam", home);

        // ── Away team ─────────────────────────────────────────────
        var away    = objectMapper.createObjectNode();
        JsonNode awayObj  = bsd.path("away_team_obj");
        int      awayApiId = awayObj.path("api_id").asInt(0);
        away.put("id",    awayObj.path("id").asInt(0));
        away.put("apiId", awayApiId);
        away.put("name",  bsd.path("away_team").asText());             // ✅ string name
        away.put("crest", awayApiId > 0
                ? BSD_BASE_URL + "/img/team/" + awayApiId + "/"
                : "");
        out.set("awayTeam", away);

        // ── Competition ───────────────────────────────────────────
        var      comp        = objectMapper.createObjectNode();
        JsonNode league      = bsd.path("league");
        int      leagueApiId = league.path("api_id").asInt(0);
        comp.put("id",     league.path("id").asInt(0));
        comp.put("apiId",  leagueApiId);
        comp.put("name",   league.path("name").asText(""));
        comp.put("emblem", leagueApiId > 0
                ? BSD_BASE_URL + "/img/league/" + leagueApiId + "/"
                : "");
        out.set("competition", comp);

        // ── Area ──────────────────────────────────────────────────
        var area = objectMapper.createObjectNode();
        area.put("name", league.path("country").asText(""));
        out.set("area", area);

        // ── Score — BSD: home_score / away_score are plain integers
        var score    = objectMapper.createObjectNode();
        var fullTime = objectMapper.createObjectNode();
        JsonNode hs  = bsd.path("home_score");
        JsonNode as_ = bsd.path("away_score");
        fullTime.put("home", hs.isNull() || hs.isMissingNode() ? 0 : hs.asInt());
        fullTime.put("away", as_.isNull() || as_.isMissingNode() ? 0 : as_.asInt());
        score.set("fullTime", fullTime);
        out.set("score", score);

        // ── Incidents (goals, cards, subs) ────────────────────────
        JsonNode incidents = bsd.path("incidents");
        if (incidents.isArray()) out.set("incidents", incidents);

        // ── Live stats ────────────────────────────────────────────
        JsonNode liveStats = bsd.path("live_stats");
        if (!liveStats.isMissingNode() && !liveStats.isNull()) {
            out.set("liveStats", liveStats);
        }

        return out;
    }

    // ══════════════════════════════════════════════════════════════
    // NORMALISER — TheSportsDB event → shared DTO shape
    // ══════════════════════════════════════════════════════════════

    private JsonNode normaliseTsdbEvent(JsonNode tsdb) {
        var out = objectMapper.createObjectNode();

        out.put("id",      "tsdb-" + tsdb.path("idEvent").asText());
        out.put("source",  "thesportsdb");

        String date = tsdb.path("dateEvent").asText("");
        String time = tsdb.path("strTime").asText("00:00:00");
        out.put("utcDate", date + "T" + time);
        out.put("status",  mapTsdbStatus(tsdb.path("strStatus").asText()));

        var home = objectMapper.createObjectNode();
        home.put("id",    tsdb.path("idHomeTeam").asText(""));
        home.put("name",  tsdb.path("strHomeTeam").asText());
        home.put("crest", tsdb.path("strHomeTeamBadge").asText(""));
        out.set("homeTeam", home);

        var away = objectMapper.createObjectNode();
        away.put("id",    tsdb.path("idAwayTeam").asText(""));
        away.put("name",  tsdb.path("strAwayTeam").asText());
        away.put("crest", tsdb.path("strAwayTeamBadge").asText(""));
        out.set("awayTeam", away);

        var comp = objectMapper.createObjectNode();
        comp.put("id",   tsdb.path("idLeague").asText(""));
        comp.put("name", tsdb.path("strLeague").asText());
        out.set("competition", comp);

        var area = objectMapper.createObjectNode();
        area.put("name", tsdb.path("strCountry").asText());
        out.set("area", area);

        var score    = objectMapper.createObjectNode();
        var fullTime = objectMapper.createObjectNode();
        JsonNode homeScore = tsdb.path("intHomeScore");
        JsonNode awayScore = tsdb.path("intAwayScore");
        if (!homeScore.isNull() && !homeScore.isMissingNode() && !homeScore.asText().isBlank()) {
            fullTime.put("home", homeScore.asInt());
            fullTime.put("away", awayScore.asInt());
        } else {
            fullTime.putNull("home");
            fullTime.putNull("away");
        }
        score.set("fullTime", fullTime);
        out.set("score", score);

        return out;
    }

    // ══════════════════════════════════════════════════════════════
    // STATUS HELPERS
    // ══════════════════════════════════════════════════════════════

    /**
     * BSD status values (from docs):
     * notstarted | inprogress | 1st_half | halftime | 2nd_half | finished | postponed | cancelled
     */
    private boolean isActiveLiveStatus(String status) {
        return switch (status.toLowerCase()) {
            case "inprogress", "1st_half", "halftime", "2nd_half" -> true;
            default -> false;
        };
    }

    private String mapTsdbStatus(String s) {
        if (s == null) return "SCHEDULED";
        return switch (s.toLowerCase()) {
            case "match finished", "ft", "aet" -> "FINISHED";
            case "in progress", "live",
                 "1h", "2h", "ht"              -> "IN_PLAY";
            case "postponed"                    -> "POSTPONED";
            case "cancelled"                    -> "CANCELLED";
            default                             -> "SCHEDULED";
        };
    }

    // ══════════════════════════════════════════════════════════════
    // HTTP CLIENTS
    // ══════════════════════════════════════════════════════════════

    private WebClient bsdClient() {
        return webClientBuilder.clone()
                .baseUrl(BSD_BASE_URL)
                .defaultHeader("Authorization", "Token " + BSD_API_KEY)
                .build();
    }

    private WebClient tsdbClient() {
        return webClientBuilder.clone()
                .baseUrl(TSDB_BASE_URL)
                .build();
    }

    private JsonNode executeBsd(Function<WebClient, String> call) {
        try {
            String response = call.apply(bsdClient());
            if (response == null || response.isBlank()) return objectMapper.createObjectNode();
            return objectMapper.readTree(response);
        } catch (WebClientResponseException e) {
            int status = e.getStatusCode().value();
            if (status == 401 || status == 403) log.error("❌ [BSD] Auth error {} — check BSD_API_KEY", status);
            else if (status == 429)             log.warn("⚠️ [BSD] Rate limit hit");
            else                                log.error("❌ [BSD] HTTP {}: {}", status, e.getMessage());
            return objectMapper.createObjectNode();
        } catch (Exception e) {
            log.error("❌ [BSD] Unexpected: {}", e.getMessage());
            return objectMapper.createObjectNode();
        }
    }

    private JsonNode executeTsdb(Function<WebClient, String> call) {
        try {
            String response = call.apply(tsdbClient());
            if (response == null || response.isBlank()) return objectMapper.createObjectNode();
            return objectMapper.readTree(response);
        } catch (WebClientResponseException e) {
            int status = e.getStatusCode().value();
            if (status == 429) log.warn("⚠️ [TSDB] Rate limit hit");
            else               log.error("❌ [TSDB] HTTP {}: {}", status, e.getMessage());
            return objectMapper.createObjectNode();
        } catch (Exception e) {
            log.error("❌ [TSDB] Unexpected: {}", e.getMessage());
            return objectMapper.createObjectNode();
        }
    }

    // ══════════════════════════════════════════════════════════════
    // WRAP HELPER
    // ══════════════════════════════════════════════════════════════

    private JsonNode wrap(ArrayNode matches) {
        var wrapper = objectMapper.createObjectNode();
        wrapper.set("matches", matches);
        return wrapper;
    }
}