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

/**
 * All data now sourced from BSD (Bzzoiro Sports Data):
 *
 *   Live games   → GET /api/live/          (30s cache, real-time scores)
 *   Today        → GET /api/events/?date_from=TODAY&date_to=TODAY
 *   Upcoming     → GET /api/events/?date_from=TOMORROW&date_to=TODAY+7
 *
 * BSD has 34+ leagues and handles pagination (50 items/page).
 * All responses are normalised to the same internal shape so
 * MatchMapper, FootballApiService etc need no changes.
 */
@Component
@Slf4j
public class ApiFootballClient {

    private static final String BSD_API_KEY  = "b72b9fd801323f6c22892218cd687fedf109ef91";
    private static final String BSD_BASE_URL = "https://sports.bzzoiro.com";

    private final ObjectMapper      objectMapper;
    private final WebClient.Builder webClientBuilder;

    public ApiFootballClient(ObjectMapper objectMapper, WebClient.Builder webClientBuilder) {
        this.objectMapper     = objectMapper;
        this.webClientBuilder = webClientBuilder;
    }

    // ══════════════════════════════════════════════════════════════
    // LIVE FIXTURES  — BSD /api/live/
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
                log.warn("⚠️ [BSD][LIVE] Unexpected response shape");
                return wrap(matches);
            }

            for (JsonNode event : results) {
                String status = event.path("status").asText("");
                if (isActiveLiveStatus(status)) {
                    matches.add(normaliseBsdEvent(event, true));
                }
            }
            log.info("✅ [BSD][LIVE] {} active live matches", matches.size());

        } catch (Exception e) {
            log.error("❌ [BSD][LIVE] Failed: {}", e.getMessage());
        }
        return wrap(matches);
    }

    // ══════════════════════════════════════════════════════════════
    // TODAY'S FIXTURES  — BSD /api/events/
    // ══════════════════════════════════════════════════════════════

    public JsonNode getTodayFixtures() {
        String today = LocalDate.now().toString();
        log.info("📅 [TODAY] Fetching today's fixtures from BSD for {}...", today);
        return fetchEventsByDateRange(today, today);
    }

    // ══════════════════════════════════════════════════════════════
    // UPCOMING FIXTURES  — BSD /api/events/ next 7 days
    // ══════════════════════════════════════════════════════════════

    public JsonNode getUpcomingFixtures() {
        String from = LocalDate.now().plusDays(1).toString();
        String to   = LocalDate.now().plusDays(7).toString();
        log.info("📅 [UPCOMING] Fetching upcoming fixtures from BSD {} → {}...", from, to);
        return fetchEventsByDateRange(from, to);
    }

    // ══════════════════════════════════════════════════════════════
    // DATE RANGE  — public helper used by FootballApiService
    // ══════════════════════════════════════════════════════════════

    public JsonNode getFixturesByDateRange(String dateFrom, String dateTo) {
        log.info("📅 [BSD] Fetching fixtures {} → {}", dateFrom, dateTo);
        return fetchEventsByDateRange(dateFrom, dateTo);
    }

    // ══════════════════════════════════════════════════════════════
    // CORE PAGINATED FETCHER  — BSD /api/events/
    // Handles BSD pagination (50 items/page) automatically
    // ══════════════════════════════════════════════════════════════

    private JsonNode fetchEventsByDateRange(String dateFrom, String dateTo) {
        ArrayNode allMatches = objectMapper.createArrayNode();
        int page = 1;

        // ── First page ────────────────────────────────────────────
        try {
            final String df = dateFrom;
            final String dt = dateTo;
            JsonNode response = executeBsd(c -> c.get()
                    .uri(u -> u.path("/api/events/")
                            .queryParam("date_from", df)
                            .queryParam("date_to",   dt)
                            .build())
                    .retrieve()
                    .bodyToMono(String.class)
                    .block());

            JsonNode results = response.path("results");
            if (results.isArray()) {
                for (JsonNode e : results) allMatches.add(normaliseBsdEvent(e, false));
            }

            log.debug("📄 [BSD] Page {} — {} events", page, results.size());

            // ── Subsequent pages ──────────────────────────────────
            String nextUrl = response.path("next").isNull() ? null
                    : response.path("next").asText(null);

            while (nextUrl != null && !nextUrl.isBlank()) {
                page++;
                final String url = nextUrl;
                try {
                    Thread.sleep(150);
                    JsonNode nextResp = executeBsd(c -> c.get()
                            .uri(url)
                            .retrieve()
                            .bodyToMono(String.class)
                            .block());

                    JsonNode nextResults = nextResp.path("results");
                    if (nextResults.isArray()) {
                        for (JsonNode e : nextResults) allMatches.add(normaliseBsdEvent(e, false));
                    }
                    log.debug("📄 [BSD] Page {} — {} events", page, nextResults.size());
                    nextUrl = nextResp.path("next").isNull() ? null
                            : nextResp.path("next").asText(null);

                } catch (Exception e) {
                    log.error("❌ [BSD] Failed page {}: {}", page, e.getMessage());
                    break;
                }
            }

        } catch (Exception e) {
            log.error("❌ [BSD] fetchEventsByDateRange failed: {}", e.getMessage());
        }

        log.info("✅ [BSD] {} total events fetched ({} → {}) across {} page(s)",
                allMatches.size(), dateFrom, dateTo, page);
        return wrap(allMatches);
    }

    // ══════════════════════════════════════════════════════════════
    // NORMALISER  — BSD event → internal DTO shape
    //
    // BSD /api/events/ and /api/live/ share the same fields:
    //
    //   id                   integer
    //   home_team            string  (team name)
    //   away_team            string  (team name)
    //   home_team_obj        { id, api_id, name, short_name, country }
    //   away_team_obj        { id, api_id, name, short_name, country }
    //   home_score           integer | null
    //   away_score           integer | null
    //   home_score_ht        integer | null
    //   away_score_ht        integer | null
    //   current_minute       integer | null   (live only)
    //   period               "1T" | "HT" | "2T" | "FT"
    //   status               notstarted | inprogress | 1st_half |
    //                        halftime | 2nd_half | finished |
    //                        postponed | cancelled
    //   event_date           ISO 8601 datetime
    //   round_number         integer | null
    //   league               { id, api_id, name, country, season_id }
    //   odds_home            float | null
    //   odds_draw            float | null
    //   odds_away            float | null
    //   odds_over_15         float | null
    //   odds_over_25         float | null
    //   odds_over_35         float | null
    //   odds_btts            float | null
    //
    //   /api/live/ extras:
    //   incidents            [ { type, minute, player_name, is_home } ]
    //   live_stats           { home: { ball_possession, total_shots, … },
    //                          away: { … } }
    // ══════════════════════════════════════════════════════════════

    private JsonNode normaliseBsdEvent(JsonNode bsd, boolean isLive) {
        var out = objectMapper.createObjectNode();

        String bsdStatus = bsd.path("status").asText("notstarted");

        out.put("id",      "bsd-" + bsd.path("id").asText());
        out.put("source",  "bsd");
        out.put("utcDate", bsd.path("event_date").asText());
        out.put("status",  mapBsdStatus(bsdStatus));
        out.put("minute",  bsd.path("current_minute").asInt(0));
        out.put("period",  bsd.path("period").asText(""));

        // ── Home team ─────────────────────────────────────────────
        var      home      = objectMapper.createObjectNode();
        JsonNode homeObj   = bsd.path("home_team_obj");
        int      homeApiId = homeObj.path("api_id").asInt(0);
        home.put("id",    homeObj.path("id").asInt(0));
        home.put("apiId", homeApiId);
        home.put("name",  bsd.path("home_team").asText());
        home.put("crest", homeApiId > 0
                ? BSD_BASE_URL + "/img/team/" + homeApiId + "/" : "");
        out.set("homeTeam", home);

        // ── Away team ─────────────────────────────────────────────
        var      away      = objectMapper.createObjectNode();
        JsonNode awayObj   = bsd.path("away_team_obj");
        int      awayApiId = awayObj.path("api_id").asInt(0);
        away.put("id",    awayObj.path("id").asInt(0));
        away.put("apiId", awayApiId);
        away.put("name",  bsd.path("away_team").asText());
        away.put("crest", awayApiId > 0
                ? BSD_BASE_URL + "/img/team/" + awayApiId + "/" : "");
        out.set("awayTeam", away);

        // ── Competition ───────────────────────────────────────────
        var      comp        = objectMapper.createObjectNode();
        JsonNode league      = bsd.path("league");
        int      leagueApiId = league.path("api_id").asInt(0);
        comp.put("id",     league.path("id").asInt(0));
        comp.put("apiId",  leagueApiId);
        comp.put("name",   league.path("name").asText(""));
        comp.put("emblem", leagueApiId > 0
                ? BSD_BASE_URL + "/img/league/" + leagueApiId + "/" : "");
        out.set("competition", comp);

        // ── Area ──────────────────────────────────────────────────
        var area = objectMapper.createObjectNode();
        area.put("name", league.path("country").asText(""));
        out.set("area", area);

        // ── Score ─────────────────────────────────────────────────
        var score    = objectMapper.createObjectNode();
        var fullTime = objectMapper.createObjectNode();
        JsonNode hs  = bsd.path("home_score");
        JsonNode as_ = bsd.path("away_score");
        fullTime.put("home", hs.isNull()  || hs.isMissingNode()  ? 0 : hs.asInt());
        fullTime.put("away", as_.isNull() || as_.isMissingNode() ? 0 : as_.asInt());
        score.set("fullTime", fullTime);

        // half-time score if available
        JsonNode hsHt = bsd.path("home_score_ht");
        JsonNode asHt = bsd.path("away_score_ht");
        if (!hsHt.isNull() && !hsHt.isMissingNode()) {
            var halfTime = objectMapper.createObjectNode();
            halfTime.put("home", hsHt.asInt());
            halfTime.put("away", asHt.asInt());
            score.set("halfTime", halfTime);
        }
        out.set("score", score);

        // ── Odds (flat BSD fields) ────────────────────────────────
        var odds = objectMapper.createObjectNode();
        setOddsField(odds, "home",   bsd.path("odds_home"));
        setOddsField(odds, "draw",   bsd.path("odds_draw"));
        setOddsField(odds, "away",   bsd.path("odds_away"));
        setOddsField(odds, "over15", bsd.path("odds_over_15"));
        setOddsField(odds, "over25", bsd.path("odds_over_25"));
        setOddsField(odds, "over35", bsd.path("odds_over_35"));
        setOddsField(odds, "btts",   bsd.path("odds_btts"));
        out.set("odds", odds);

        // ── Live-only extras ──────────────────────────────────────
        if (isLive) {
            JsonNode incidents = bsd.path("incidents");
            if (incidents.isArray()) out.set("incidents", incidents);

            JsonNode liveStats = bsd.path("live_stats");
            if (!liveStats.isMissingNode() && !liveStats.isNull()) {
                out.set("liveStats", liveStats);
            }
        }

        return out;
    }

    // ══════════════════════════════════════════════════════════════
    // STATUS HELPERS
    // ══════════════════════════════════════════════════════════════

    private boolean isActiveLiveStatus(String status) {
        return switch (status.toLowerCase()) {
            case "inprogress", "1st_half", "halftime", "2nd_half" -> true;
            default -> false;
        };
    }

    private String mapBsdStatus(String s) {
        if (s == null) return "SCHEDULED";
        return switch (s.toLowerCase()) {
            case "inprogress", "1st_half", "2nd_half" -> "IN_PLAY";
            case "halftime"                            -> "PAUSED";
            case "finished"                            -> "FINISHED";
            case "postponed"                           -> "POSTPONED";
            case "cancelled"                           -> "CANCELLED";
            default                                    -> "SCHEDULED";
        };
    }

    // ══════════════════════════════════════════════════════════════
    // HELPERS
    // ══════════════════════════════════════════════════════════════

    private void setOddsField(com.fasterxml.jackson.databind.node.ObjectNode node,
                              String key, JsonNode value) {
        if (!value.isNull() && !value.isMissingNode() && value.asDouble(0) > 0) {
            node.put(key, value.asDouble());
        } else {
            node.putNull(key);
        }
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

    private WebClient bsdClient() {
        return webClientBuilder.clone()
                .baseUrl(BSD_BASE_URL)
                .defaultHeader("Authorization", "Token " + BSD_API_KEY)
                .build();
    }

    private JsonNode wrap(ArrayNode matches) {
        var wrapper = objectMapper.createObjectNode();
        wrapper.set("matches", matches);
        return wrapper;
    }
}