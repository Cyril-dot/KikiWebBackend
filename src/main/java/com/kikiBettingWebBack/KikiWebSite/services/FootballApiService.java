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

    private static final String API_KEY          = "ffe42eb2b40046dfa0c6499921113213";
    private static final String BASE_URL         = "https://api.football-data.org/v4";
    private static final String DEFAULT_COMP_IDS = "2021,2001,2014,2002,2019,2015";

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    // ── Public API ────────────────────────────────────────────────────────────

    public List<LiveGameResponse> getLiveGames() {
        log.info("📡 Fetching live games from football-data.org...");
        try {
            String url = BASE_URL + "/matches?competitions=" + DEFAULT_COMP_IDS + "&status=IN_PLAY,PAUSED";
            JsonNode root = fetch(url);
            List<LiveGameResponse> games = parseLiveGames(root);
            log.info("✅ Live games fetched: {}", games.size());
            return games;
        } catch (Exception e) {
            log.warn("⚠️ getLiveGames failed: {}", e.getMessage());
            return new ArrayList<>();
        }
    }

    public List<LiveGameResponse> getLiveGamesByLeagues(String commaSeparatedCompIds) {
        log.info("📡 Fetching live games for leagues: {}", commaSeparatedCompIds);
        try {
            String url = BASE_URL + "/matches?competitions=" + commaSeparatedCompIds + "&status=IN_PLAY,PAUSED";
            JsonNode root = fetch(url);
            List<LiveGameResponse> games = parseLiveGames(root);
            log.info("✅ Live games by league fetched: {}", games.size());
            return games;
        } catch (Exception e) {
            log.warn("⚠️ getLiveGamesByLeagues failed: {}", e.getMessage());
            return new ArrayList<>();
        }
    }

    public List<UpcomingFixtureResponse> getTodayUpcomingFixtures() {
        log.info("📡 Fetching today's upcoming fixtures...");
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
            log.warn("⚠️ getTodayUpcomingFixtures failed: {}", e.getMessage());
            return new ArrayList<>();
        }
    }

    public List<UpcomingFixtureResponse> getUpcomingByLeague(int competitionId, int ignoredSeason) {
        log.info("📡 Fetching upcoming fixtures for competition: {}", competitionId);
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
            log.warn("⚠️ getUpcomingByLeague({}) failed: {}", competitionId, e.getMessage());
            return new ArrayList<>();
        }
    }

    public List<LiveGameResponse> getTodayFinishedGames() {
        log.info("📡 Fetching today's finished games...");
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
            log.warn("⚠️ getTodayFinishedGames failed: {}", e.getMessage());
            return new ArrayList<>();
        }
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
            throw new RuntimeException("Failed to parse response: " + e.getMessage(), e);
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

                // FIX: pass status so score() knows which node to prefer
                dto.setHomeScore(score(m, "home", status));
                dto.setAwayScore(score(m, "away", status));

                dto.setStatusShort(status);
                dto.setStatusLong(status);

                // FIX: pass full match node so parseElapsed can read status
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

    /**
     * FIX: For IN_PLAY and PAUSED, the API populates score.fullTime with the
     * current/live score. The old code fell back to halfTime on null fullTime,
     * which could show 0-0 at half when the real score was e.g. 2-1.
     *
     * For FINISHED games, fullTime is the final score — still correct to use first.
     * halfTime fallback is kept only as a genuine last resort.
     */
    private int score(JsonNode match, String side, String status) {
        JsonNode scoreNode = match.path("score");

        // Live or paused: current score lives in fullTime during the match
        JsonNode current = scoreNode.path("fullTime").path(side);
        if (!current.isNull() && current.isNumber()) return current.asInt();

        // Finished: fullTime is the final score — same node, already handled above.
        // halfTime fallback only if fullTime is genuinely absent (shouldn't happen).
        if (!status.equals("IN_PLAY") && !status.equals("PAUSED")) {
            JsonNode ht = scoreNode.path("halfTime").path(side);
            if (!ht.isNull() && ht.isNumber()) return ht.asInt();
        }

        return 0;
    }

    /**
     * FIX: Old version ignored the ~15-minute halftime break, causing elapsed
     * to read e.g. 60' when the real minute was 45' (second half just started).
     *
     * Strategy:
     *  - PAUSED  → always return 45 (half time)
     *  - IN_PLAY, mins <= 52 → first half, return raw minutes (capped at 45)
     *  - IN_PLAY, mins >  52 → second half started; subtract 15 min HT break
     */
    private Integer parseElapsed(JsonNode match) {
        try {
            String status  = match.path("status").asText("");
            Instant kickoff = Instant.parse(match.path("utcDate").asText());
            long mins = ChronoUnit.MINUTES.between(kickoff, Instant.now());

            if (status.equals("PAUSED")) {
                return 45;
            }

            // First half: kickoff up to ~52 min wall-clock (45 + small buffer)
            if (mins <= 52) {
                return (int) Math.min(mins, 45);
            }

            // Second half: subtract the halftime break (~15 min)
            long adjusted = mins - 15;
            return (int) Math.min(adjusted, 90);

        } catch (Exception e) {
            log.warn("⚠️ parseElapsed failed: {}", e.getMessage());
            return null;
        }
    }
}