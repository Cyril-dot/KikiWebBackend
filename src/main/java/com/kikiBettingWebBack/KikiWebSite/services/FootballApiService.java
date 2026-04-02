package com.kikiBettingWebBack.KikiWebSite.services;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kikiBettingWebBack.KikiWebSite.dtos.LiveGameResponse;
import com.kikiBettingWebBack.KikiWebSite.dtos.UpcomingFixtureResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.*;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class FootballApiService {

    private static final String API_KEY          = "be1005d63c744335b70addc178dfce37";
    private static final String BASE_URL         = "https://api.football-data.org/v4";
    private static final String DEFAULT_COMP_IDS = "2021,2001,2014,2002,2019,2015";

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Live games across all default competitions.
     * Cached for 60 seconds — live scores change frequently.
     * Always returns a list, never throws.
     */
    @Cacheable(value = "apiLive", unless = "#result == null")
    public List<LiveGameResponse> getLiveGames() {
        log.info("📡 [CACHE MISS] Fetching live games from football-data.org...");
        try {
            String url = BASE_URL + "/matches?competitions=" + DEFAULT_COMP_IDS + "&status=IN_PLAY,PAUSED";
            JsonNode root = fetch(url);
            List<LiveGameResponse> games = parseLiveGames(root);
            log.info("✅ Live games fetched: {}", games.size());
            return games;
        } catch (Exception e) {
            log.warn("⚠️ getLiveGames failed (returning empty list): {}", e.getMessage());
            return new ArrayList<>();
        }
    }

    /**
     * Live games filtered to specific competition IDs.
     * Cached per league string for 60 seconds.
     * Always returns a list, never throws.
     */
    @Cacheable(value = "apiLiveByLeague", key = "#commaSeparatedCompIds", unless = "#result == null")
    public List<LiveGameResponse> getLiveGamesByLeagues(String commaSeparatedCompIds) {
        log.info("📡 [CACHE MISS] Fetching live games for leagues: {}", commaSeparatedCompIds);
        try {
            String url = BASE_URL + "/matches?competitions=" + commaSeparatedCompIds + "&status=IN_PLAY,PAUSED";
            JsonNode root = fetch(url);
            List<LiveGameResponse> games = parseLiveGames(root);
            log.info("✅ Live games by league fetched: {}", games.size());
            return games;
        } catch (Exception e) {
            log.warn("⚠️ getLiveGamesByLeagues failed (returning empty list): {}", e.getMessage());
            return new ArrayList<>();
        }
    }

    /**
     * Today's upcoming fixtures across all default competitions.
     * Cached for 30 minutes — upcoming fixtures don't change often.
     * Always returns a list, never throws.
     */
    @Cacheable(value = "apiUpcoming", unless = "#result == null")
    public List<UpcomingFixtureResponse> getTodayUpcomingFixtures() {
        log.info("📡 [CACHE MISS] Fetching today's upcoming fixtures...");
        try {
            String today = java.time.LocalDate.now().toString();
            String url = BASE_URL + "/matches?competitions=" + DEFAULT_COMP_IDS
                    + "&dateFrom=" + today
                    + "&dateTo=" + today
                    + "&status=SCHEDULED,TIMED";
            JsonNode root = fetch(url);
            List<UpcomingFixtureResponse> fixtures = parseUpcomingFixtures(root);
            log.info("✅ Today's upcoming fixtures fetched: {}", fixtures.size());
            return fixtures;
        } catch (Exception e) {
            log.warn("⚠️ getTodayUpcomingFixtures failed (returning empty list): {}", e.getMessage());
            return new ArrayList<>();
        }
    }

    /**
     * Next 7 days of fixtures for a specific competition.
     * Cached per competition ID for 30 minutes.
     * Always returns a list, never throws.
     */
    @Cacheable(value = "apiUpcomingByLeague", key = "#competitionId", unless = "#result == null")
    public List<UpcomingFixtureResponse> getUpcomingByLeague(int competitionId, int ignoredSeason) {
        log.info("📡 [CACHE MISS] Fetching upcoming fixtures for competition: {}", competitionId);
        try {
            String today  = Instant.now().toString().substring(0, 10);
            String inWeek = Instant.now().plus(7, ChronoUnit.DAYS).toString().substring(0, 10);
            String url = BASE_URL + "/competitions/" + competitionId
                    + "/matches?dateFrom=" + today + "&dateTo=" + inWeek + "&status=SCHEDULED,TIMED";
            JsonNode root = fetch(url);
            List<UpcomingFixtureResponse> fixtures = parseUpcomingFixtures(root);
            log.info("✅ Upcoming fixtures for competition {} fetched: {}", competitionId, fixtures.size());
            return fixtures;
        } catch (Exception e) {
            log.warn("⚠️ getUpcomingByLeague({}) failed (returning empty list): {}", competitionId, e.getMessage());
            return new ArrayList<>();
        }
    }

    /**
     * Today's finished games with real scores.
     * Cached for 5 minutes — finished scores don't change.
     * Always returns a list, never throws.
     */
    @Cacheable(value = "apiFinished", unless = "#result == null")
    public List<LiveGameResponse> getTodayFinishedGames() {
        log.info("📡 [CACHE MISS] Fetching today's finished games...");
        try {
            String today = java.time.LocalDate.now().toString();
            String url = BASE_URL + "/matches?competitions=" + DEFAULT_COMP_IDS
                    + "&dateFrom=" + today
                    + "&dateTo=" + today
                    + "&status=FINISHED";
            JsonNode root = fetch(url);
            List<LiveGameResponse> games = parseLiveGames(root);
            log.info("✅ Finished games fetched: {}", games.size());
            return games;
        } catch (Exception e) {
            log.warn("⚠️ getTodayFinishedGames failed (returning empty list): {}", e.getMessage());
            return new ArrayList<>();
        }
    }

    // ── Cache eviction schedules ──────────────────────────────────────────────

    /** Evict live cache every 60 seconds so scores stay current. */
    @Scheduled(fixedDelay = 60 * 1000)
    @CacheEvict(value = {"apiLive", "apiLiveByLeague"}, allEntries = true)
    public void evictLiveCache() {
        log.info("🗑️ apiLive cache evicted — will refresh on next request");
    }

    /** Evict upcoming cache every 30 minutes — fixtures rarely change. */
    @Scheduled(fixedDelay = 30 * 60 * 1000)
    @CacheEvict(value = {"apiUpcoming", "apiUpcomingByLeague"}, allEntries = true)
    public void evictUpcomingCache() {
        log.info("🗑️ apiUpcoming cache evicted — will refresh on next request");
    }

    /** Evict finished cache every 5 minutes. */
    @Scheduled(fixedDelay = 5 * 60 * 1000)
    @CacheEvict(value = "apiFinished", allEntries = true)
    public void evictFinishedCache() {
        log.info("🗑️ apiFinished cache evicted — will refresh on next request");
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private JsonNode fetch(String url) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Auth-Token", API_KEY);
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));
        HttpEntity<Void> entity = new HttpEntity<>(headers);

        log.debug("📡 Calling football-data.org: {}", url);
        ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);

        try {
            JsonNode root = objectMapper.readTree(response.getBody());
            if (!root.has("matches")) {
                log.warn("⚠️ Unexpected response (no 'matches' key): {}",
                        response.getBody() == null ? "null" :
                                response.getBody().substring(0, Math.min(300, response.getBody().length())));
                return objectMapper.readTree("{\"matches\":[]}");
            }
            return root;
        } catch (Exception e) {
            log.error("❌ Failed to parse football-data.org response: {}", e.getMessage());
            throw new RuntimeException("Failed to parse football-data.org response: " + e.getMessage(), e);
        }
    }

    private List<LiveGameResponse> parseLiveGames(JsonNode root) {
        List<LiveGameResponse> results = new ArrayList<>();
        if (root == null) return results;

        JsonNode matches = root.path("matches");
        if (!matches.isArray()) return results;

        for (JsonNode m : matches) {
            try {
                LiveGameResponse dto = new LiveGameResponse();

                String status  = m.path("status").asText("SCHEDULED");
                boolean isLive = status.equals("IN_PLAY") || status.equals("PAUSED");

                dto.setFixtureId(m.path("id").asLong());
                dto.setKickoff(m.path("utcDate").asText());
                dto.setHomeTeam(teamName(m, "homeTeam"));
                dto.setAwayTeam(teamName(m, "awayTeam"));
                dto.setHomeScore(score(m, "home"));
                dto.setAwayScore(score(m, "away"));
                dto.setStatusShort(status);
                dto.setStatusLong(status);
                dto.setElapsed(isLive ? parseElapsed(m) : null);
                dto.setLeague(m.path("competition").path("name").asText(""));
                dto.setCountry(m.path("area").path("name").asText(""));
                dto.setRound(m.path("season").path("currentMatchday").asText(""));

                results.add(dto);
            } catch (Exception e) {
                log.warn("⚠️ Skipping malformed match: {}", e.getMessage());
            }
        }
        return results;
    }

    private List<UpcomingFixtureResponse> parseUpcomingFixtures(JsonNode root) {
        List<UpcomingFixtureResponse> results = new ArrayList<>();
        if (root == null) return results;

        JsonNode matches = root.path("matches");
        if (!matches.isArray()) return results;

        for (JsonNode m : matches) {
            try {
                UpcomingFixtureResponse dto = new UpcomingFixtureResponse();

                dto.setFixtureId(m.path("id").asLong());
                dto.setKickoff(m.path("utcDate").asText());
                dto.setHomeTeam(teamName(m, "homeTeam"));
                dto.setHomeLogo(m.path("homeTeam").path("crest").asText(""));
                dto.setAwayTeam(teamName(m, "awayTeam"));
                dto.setAwayLogo(m.path("awayTeam").path("crest").asText(""));
                dto.setStatusShort(m.path("status").asText("SCHEDULED"));
                dto.setLeague(m.path("competition").path("name").asText(""));
                dto.setCountry(m.path("area").path("name").asText(""));
                dto.setRound("Matchday " + m.path("matchday").asText("?"));

                results.add(dto);
            } catch (Exception e) {
                log.warn("⚠️ Skipping malformed upcoming fixture: {}", e.getMessage());
            }
        }
        return results;
    }

    // ── Field helpers ─────────────────────────────────────────────────────────

    private String teamName(JsonNode match, String side) {
        JsonNode t = match.path(side);
        String shortName = t.path("shortName").asText(null);
        return (shortName != null && !shortName.isBlank()) ? shortName : t.path("name").asText("TBD");
    }

    private int score(JsonNode match, String side) {
        JsonNode ft = match.path("score").path("fullTime").path(side);
        if (!ft.isNull() && ft.isNumber()) return ft.asInt();
        JsonNode ht = match.path("score").path("halfTime").path(side);
        if (!ht.isNull() && ht.isNumber()) return ht.asInt();
        return 0;
    }

    private Integer parseElapsed(JsonNode match) {
        try {
            Instant kickoff = Instant.parse(match.path("utcDate").asText());
            long mins = ChronoUnit.MINUTES.between(kickoff, Instant.now());
            return (int) Math.min(mins, 90);
        } catch (Exception e) {
            return null;
        }
    }
}