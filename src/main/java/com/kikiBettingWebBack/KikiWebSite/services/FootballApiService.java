package com.kikiBettingWebBack.KikiWebSite.services;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kikiBettingWebBack.KikiWebSite.dtos.LiveGameResponse;
import com.kikiBettingWebBack.KikiWebSite.dtos.UpcomingFixtureResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
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

    // ── Hardcoded football-data.org credentials ───────────────────────────────
    private static final String API_KEY  = "be1005d63c744335b70addc178dfce37";
    private static final String BASE_URL = "https://api.football-data.org/v4";

    // Competition IDs on football-data.org:
    // 2021 = Premier League, 2001 = Champions League, 2014 = La Liga
    // 2002 = Bundesliga, 2019 = Serie A, 2015 = Ligue 1
    private static final String DEFAULT_COMP_IDS = "2021,2001,2014,2002,2019,2015";

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    // ─────────────────────────────────────────────────────────────────────────
    // PUBLIC API
    // ─────────────────────────────────────────────────────────────────────────

    /** Live games across all default competitions. */
    public List<LiveGameResponse> getLiveGames() {
        String url = BASE_URL + "/matches?competitions=" + DEFAULT_COMP_IDS + "&status=IN_PLAY,PAUSED";
        JsonNode root = fetch(url);
        return parseLiveGames(root);
    }

    /** Live games filtered to specific competition IDs (comma-separated). */
    public List<LiveGameResponse> getLiveGamesByLeagues(String commaSeparatedCompIds) {
        String url = BASE_URL + "/matches?competitions=" + commaSeparatedCompIds + "&status=IN_PLAY,PAUSED";
        JsonNode root = fetch(url);
        return parseLiveGames(root);
    }

    /** Today's upcoming fixtures across all default competitions. */
    public List<UpcomingFixtureResponse> getTodayUpcomingFixtures() {
        String today = java.time.LocalDate.now().toString();
        String url = BASE_URL + "/matches?competitions=" + DEFAULT_COMP_IDS
                + "&dateFrom=" + today
                + "&dateTo=" + today
                + "&status=SCHEDULED,TIMED";
        try {
            JsonNode root = fetch(url);
            return parseUpcomingFixtures(root);
        } catch (Exception e) {
            log.warn("Failed to fetch today's upcoming fixtures: {}", e.getMessage());
            return new ArrayList<>();
        }
    }

    /** Next 7 days of fixtures for a specific competition. */
    public List<UpcomingFixtureResponse> getUpcomingByLeague(int competitionId, int ignoredSeason) {
        String today  = Instant.now().toString().substring(0, 10);
        String inWeek = Instant.now().plus(7, ChronoUnit.DAYS).toString().substring(0, 10);
        String url = BASE_URL + "/competitions/" + competitionId
                + "/matches?dateFrom=" + today + "&dateTo=" + inWeek + "&status=SCHEDULED,TIMED";
        try {
            JsonNode root = fetch(url);
            return parseUpcomingFixtures(root);
        } catch (Exception e) {
            log.warn("Failed to fetch upcoming for competition {}: {}", competitionId, e.getMessage());
            return new ArrayList<>();
        }
    }

    /** Today's finished games with real scores. */
    public List<LiveGameResponse> getTodayFinishedGames() {
        String today = java.time.LocalDate.now().toString();
        String url = BASE_URL + "/matches?competitions=" + DEFAULT_COMP_IDS
                + "&dateFrom=" + today
                + "&dateTo=" + today
                + "&status=FINISHED";
        try {
            JsonNode root = fetch(url);
            return parseLiveGames(root);
        } catch (Exception e) {
            log.warn("Failed to fetch finished games: {}", e.getMessage());
            return new ArrayList<>();
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // PRIVATE HELPERS
    // ─────────────────────────────────────────────────────────────────────────

    private JsonNode fetch(String url) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Auth-Token", API_KEY);   // football-data.org auth header
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));

        HttpEntity<Void> entity = new HttpEntity<>(headers);

        log.debug("Calling football-data.org: {}", url);
        ResponseEntity<String> response = restTemplate.exchange(
                url, HttpMethod.GET, entity, String.class);

        try {
            JsonNode root = objectMapper.readTree(response.getBody());
            // football-data.org wraps results in a "matches" array
            if (!root.has("matches")) {
                log.error("Unexpected football-data.org response: {}",
                        response.getBody().substring(0, Math.min(300, response.getBody().length())));
                throw new RuntimeException("Invalid response from football-data.org");
            }
            return root;
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse football-data.org response: " + e.getMessage(), e);
        }
    }

    private List<LiveGameResponse> parseLiveGames(JsonNode root) {
        List<LiveGameResponse> results = new ArrayList<>();
        JsonNode matches = root.path("matches");
        if (!matches.isArray()) return results;

        for (JsonNode m : matches) {
            try {
                LiveGameResponse dto = new LiveGameResponse();

                String status = m.path("status").asText("SCHEDULED");
                boolean isLive = status.equals("IN_PLAY") || status.equals("PAUSED");

                dto.setFixtureId(m.path("id").asLong());
                dto.setKickoff(m.path("utcDate").asText());

                dto.setHomeTeam(teamName(m, "homeTeam"));
                dto.setAwayTeam(teamName(m, "awayTeam"));

                // Real scores from football-data.org
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
                log.warn("Skipping malformed match: {}", e.getMessage());
            }
        }
        return results;
    }

    private List<UpcomingFixtureResponse> parseUpcomingFixtures(JsonNode root) {
        List<UpcomingFixtureResponse> results = new ArrayList<>();
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
                log.warn("Skipping malformed upcoming fixture: {}", e.getMessage());
            }
        }
        return results;
    }

    // ── Field helpers ─────────────────────────────────────────────────────────

    private String teamName(JsonNode match, String side) {
        JsonNode t = match.path(side);
        String short_ = t.path("shortName").asText(null);
        return (short_ != null && !short_.isBlank()) ? short_ : t.path("name").asText("TBD");
    }

    private int score(JsonNode match, String side) {
        JsonNode ft = match.path("score").path("fullTime").path(side);
        if (!ft.isNull() && ft.isNumber()) return ft.asInt();
        JsonNode ht = match.path("score").path("halfTime").path(side);
        if (!ht.isNull() && ht.isNumber()) return ht.asInt();
        return 0;
    }

    private Integer parseElapsed(JsonNode match) {
        // football-data.org doesn't give elapsed minutes on free tier
        // so we calculate approximate elapsed from kickoff time
        try {
            Instant kickoff = Instant.parse(match.path("utcDate").asText());
            long mins = ChronoUnit.MINUTES.between(kickoff, Instant.now());
            return (int) Math.min(mins, 90);
        } catch (Exception e) {
            return null;
        }
    }
}