package com.kikiBettingWebBack.KikiWebSite.services;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kikiBettingWebBack.KikiWebSite.dtos.LiveGameResponse;
import com.kikiBettingWebBack.KikiWebSite.dtos.UpcomingFixtureResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class FootballApiService {

    // ── BSD — live games ──────────────────────────────────────────
    private static final String BSD_API_KEY  = "b72b9fd801323f6c22892218cd687fedf109ef91";
    private static final String BSD_BASE_URL = "https://sports.bzzoiro.com";

    // ── TheSportsDB — today + upcoming ───────────────────────────
    private static final String TSDB_API_KEY  = "123";
    private static final String TSDB_BASE_URL = "https://www.thesportsdb.com/api/v1/json/" + TSDB_API_KEY;

    private final ApiFootballClient apiFootballClient;
    private final ObjectMapper      objectMapper;

    // ── Public API ────────────────────────────────────────────────

    /**
     * Live games — sourced from BSD /api/live/
     */
    public List<LiveGameResponse> getLiveGames() {
        log.info("📡 [FAS] Fetching live games from BSD...");
        try {
            JsonNode root    = apiFootballClient.getLiveFixtures();
            JsonNode matches = root.path("matches");
            List<LiveGameResponse> games = parseLiveGames(matches);
            log.info("✅ [FAS] Live games fetched: {}", games.size());
            return games;
        } catch (Exception e) {
            log.warn("⚠️ [FAS] getLiveGames failed: {}", e.getMessage());
            return new ArrayList<>();
        }
    }

    /**
     * Today's upcoming fixtures — sourced from TheSportsDB
     */
    public List<UpcomingFixtureResponse> getTodayUpcomingFixtures() {
        log.info("📡 [FAS] Fetching today's fixtures from TheSportsDB...");
        try {
            JsonNode root    = apiFootballClient.getTodayFixtures();
            JsonNode matches = root.path("matches");
            List<UpcomingFixtureResponse> fixtures = parseUpcomingFixtures(matches);
            log.info("✅ [FAS] Today upcoming fixtures: {}", fixtures.size());
            return fixtures;
        } catch (Exception e) {
            log.warn("⚠️ [FAS] getTodayUpcomingFixtures failed: {}", e.getMessage());
            return new ArrayList<>();
        }
    }

    /**
     * Upcoming fixtures for a specific competition — TheSportsDB
     */
    public List<UpcomingFixtureResponse> getUpcomingByLeague(int competitionId, int ignoredSeason) {
        log.info("📡 [FAS] Fetching upcoming for competition: {} from TheSportsDB...", competitionId);
        try {
            // TheSportsDB: get next events for a league by its ID
            JsonNode root    = apiFootballClient.getFixturesByDateRange(
                    java.time.LocalDate.now().toString(),
                    java.time.LocalDate.now().plusDays(7).toString()
            );
            JsonNode matches = root.path("matches");
            List<UpcomingFixtureResponse> fixtures = parseUpcomingFixtures(matches);
            log.info("✅ [FAS] Upcoming for competition {}: {}", competitionId, fixtures.size());
            return fixtures;
        } catch (Exception e) {
            log.warn("⚠️ [FAS] getUpcomingByLeague({}) failed: {}", competitionId, e.getMessage());
            return new ArrayList<>();
        }
    }

    /**
     * Today's finished games — TheSportsDB (filter by FINISHED status)
     */
    public List<LiveGameResponse> getTodayFinishedGames() {
        log.info("📡 [FAS] Fetching today's finished games from TheSportsDB...");
        try {
            JsonNode root    = apiFootballClient.getTodayFixtures();
            JsonNode matches = root.path("matches");

            // Filter to finished only
            List<LiveGameResponse> all      = parseLiveGames(matches);
            List<LiveGameResponse> finished = all.stream()
                    .filter(g -> "FINISHED".equals(g.getStatusShort()))
                    .toList();

            log.info("✅ [FAS] Finished games today: {}", finished.size());
            return finished;
        } catch (Exception e) {
            log.warn("⚠️ [FAS] getTodayFinishedGames failed: {}", e.getMessage());
            return new ArrayList<>();
        }
    }

    // ── Parsers ───────────────────────────────────────────────────

    private List<LiveGameResponse> parseLiveGames(JsonNode matches) {
        List<LiveGameResponse> results = new ArrayList<>();
        if (matches == null || !matches.isArray()) return results;

        for (JsonNode m : matches) {
            try {
                LiveGameResponse dto = new LiveGameResponse();
                String status  = m.path("status").asText("SCHEDULED");
                boolean isLive = status.equals("IN_PLAY") || status.equals("PAUSED");

                dto.setFixtureId(parseId(m.path("id").asText()));
                dto.setKickoff(m.path("utcDate").asText());
                dto.setHomeTeam(m.path("homeTeam").path("name").asText(""));
                dto.setAwayTeam(m.path("awayTeam").path("name").asText(""));
                dto.setHomeScore(scoreVal(m, "home", status));
                dto.setAwayScore(scoreVal(m, "away", status));
                dto.setStatusShort(status);
                dto.setStatusLong(status);
                dto.setElapsed(isLive ? m.path("minute").asInt(0) : null);
                dto.setLeague(m.path("competition").path("name").asText(""));
                dto.setCountry(m.path("area").path("name").asText(""));
                dto.setRound("");

                results.add(dto);
            } catch (Exception e) {
                log.warn("⚠️ [FAS] Skipping malformed match: {}", e.getMessage());
            }
        }
        return results;
    }

    private List<UpcomingFixtureResponse> parseUpcomingFixtures(JsonNode matches) {
        List<UpcomingFixtureResponse> results = new ArrayList<>();
        if (matches == null || !matches.isArray()) return results;

        for (JsonNode m : matches) {
            try {
                UpcomingFixtureResponse dto = new UpcomingFixtureResponse();
                dto.setFixtureId(parseId(m.path("id").asText()));
                dto.setKickoff(m.path("utcDate").asText());
                dto.setHomeTeam(m.path("homeTeam").path("name").asText(""));
                dto.setHomeLogo(m.path("homeTeam").path("crest").asText(""));
                dto.setAwayTeam(m.path("awayTeam").path("name").asText(""));
                dto.setAwayLogo(m.path("awayTeam").path("crest").asText(""));
                dto.setStatusShort(m.path("status").asText("SCHEDULED"));
                dto.setLeague(m.path("competition").path("name").asText(""));
                dto.setCountry(m.path("area").path("name").asText(""));
                dto.setRound("");
                results.add(dto);
            } catch (Exception e) {
                log.warn("⚠️ [FAS] Skipping malformed fixture: {}", e.getMessage());
            }
        }
        return results;
    }

    // ── Helpers ───────────────────────────────────────────────────

    private int scoreVal(JsonNode match, String side, String status) {
        JsonNode val = match.path("score").path("fullTime").path(side);
        if (!val.isNull() && val.isNumber()) return val.asInt();
        return 0;
    }

    /**
     * IDs from the normalizers are prefixed e.g. "bsd-123" or "tsdb-456".
     * Strip the prefix and parse as long, falling back to hashCode.
     */
    private long parseId(String rawId) {
        try {
            String numeric = rawId.replaceAll("^[a-z]+-", "");
            return Long.parseLong(numeric);
        } catch (Exception e) {
            return (long) rawId.hashCode();
        }
    }
}