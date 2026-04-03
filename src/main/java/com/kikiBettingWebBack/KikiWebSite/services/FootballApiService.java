package com.kikiBettingWebBack.KikiWebSite.services;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kikiBettingWebBack.KikiWebSite.dtos.LiveGameResponse;
import com.kikiBettingWebBack.KikiWebSite.dtos.UpcomingFixtureResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class FootballApiService {

    private final ApiFootballClient apiFootballClient;
    private final ObjectMapper      objectMapper;

    // ══════════════════════════════════════════════════════════════
    // LIVE GAMES — BSD /api/live/
    // ══════════════════════════════════════════════════════════════

    /**
     * All active live games across every league.
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
     * Live games filtered to a comma-separated list of league names.
     * e.g. "Premier League,La Liga"
     * BSD doesn't support server-side league filtering on /api/live/
     * so we fetch all live games and filter client-side by league name.
     */
    public List<LiveGameResponse> getLiveGamesByLeagues(String commaSeparatedLeagues) {
        log.info("📡 [FAS] Fetching live games filtered by leagues: {}", commaSeparatedLeagues);
        try {
            Set<String> leagueFilter = Arrays.stream(commaSeparatedLeagues.split(","))
                    .map(String::trim)
                    .map(String::toLowerCase)
                    .collect(Collectors.toSet());

            List<LiveGameResponse> all = getLiveGames();

            List<LiveGameResponse> filtered = all.stream()
                    .filter(g -> g.getLeague() != null
                            && leagueFilter.contains(g.getLeague().toLowerCase()))
                    .toList();

            log.info("✅ [FAS] Filtered live games: {} of {}", filtered.size(), all.size());
            return filtered;
        } catch (Exception e) {
            log.warn("⚠️ [FAS] getLiveGamesByLeagues failed: {}", e.getMessage());
            return new ArrayList<>();
        }
    }

    // ══════════════════════════════════════════════════════════════
    // TODAY + UPCOMING — TheSportsDB
    // ══════════════════════════════════════════════════════════════

    /**
     * Today's fixtures — sourced from TheSportsDB
     */
    public List<UpcomingFixtureResponse> getTodayUpcomingFixtures() {
        log.info("📡 [FAS] Fetching today's fixtures from TheSportsDB...");
        try {
            JsonNode root    = apiFootballClient.getTodayFixtures();
            JsonNode matches = root.path("matches");
            List<UpcomingFixtureResponse> fixtures = parseUpcomingFixtures(matches);
            log.info("✅ [FAS] Today fixtures: {}", fixtures.size());
            return fixtures;
        } catch (Exception e) {
            log.warn("⚠️ [FAS] getTodayUpcomingFixtures failed: {}", e.getMessage());
            return new ArrayList<>();
        }
    }

    /**
     * Upcoming fixtures for a specific competition — TheSportsDB.
     * TheSportsDB free tier doesn't filter by competition ID,
     * so we fetch the full 7-day window and return everything.
     */
    public List<UpcomingFixtureResponse> getUpcomingByLeague(int competitionId, int ignoredSeason) {
        log.info("📡 [FAS] Fetching upcoming fixtures from TheSportsDB (competitionId={})...", competitionId);
        try {
            JsonNode root    = apiFootballClient.getFixturesByDateRange(
                    java.time.LocalDate.now().toString(),
                    java.time.LocalDate.now().plusDays(7).toString()
            );
            JsonNode matches = root.path("matches");
            List<UpcomingFixtureResponse> fixtures = parseUpcomingFixtures(matches);
            log.info("✅ [FAS] Upcoming fixtures: {}", fixtures.size());
            return fixtures;
        } catch (Exception e) {
            log.warn("⚠️ [FAS] getUpcomingByLeague({}) failed: {}", competitionId, e.getMessage());
            return new ArrayList<>();
        }
    }

    /**
     * Today's finished games — TheSportsDB (filtered by FINISHED status)
     */
    public List<LiveGameResponse> getTodayFinishedGames() {
        log.info("📡 [FAS] Fetching today's finished games from TheSportsDB...");
        try {
            JsonNode root    = apiFootballClient.getTodayFixtures();
            JsonNode matches = root.path("matches");

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

    // ══════════════════════════════════════════════════════════════
    // PARSERS
    // ══════════════════════════════════════════════════════════════

    private List<LiveGameResponse> parseLiveGames(JsonNode matches) {
        List<LiveGameResponse> results = new ArrayList<>();
        if (matches == null || !matches.isArray()) return results;

        for (JsonNode m : matches) {
            try {
                LiveGameResponse dto = new LiveGameResponse();
                String  status = m.path("status").asText("SCHEDULED");
                boolean isLive = status.equals("IN_PLAY") || status.equals("PAUSED");

                dto.setFixtureId(parseId(m.path("id").asText()));
                dto.setKickoff(m.path("utcDate").asText());
                dto.setHomeTeam(m.path("homeTeam").path("name").asText(""));
                dto.setAwayTeam(m.path("awayTeam").path("name").asText(""));
                dto.setHomeScore(scoreVal(m, "home"));
                dto.setAwayScore(scoreVal(m, "away"));
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

    // ══════════════════════════════════════════════════════════════
    // HELPERS
    // ══════════════════════════════════════════════════════════════

    private int scoreVal(JsonNode match, String side) {
        JsonNode val = match.path("score").path("fullTime").path(side);
        if (!val.isNull() && val.isNumber()) return val.asInt();
        return 0;
    }

    /**
     * IDs come prefixed from the normalizers e.g. "bsd-123" or "tsdb-456".
     * Strip the prefix and parse as long, fall back to hashCode.
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