package com.kikiBettingWebBack.KikiWebSite.services;

import com.kikiBettingWebBack.KikiWebSite.dtos.NewMatchDTO;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

@Slf4j
@Component
@RequiredArgsConstructor
public class GameDataStore {

    private final LiveGamesService     liveGamesService;
    private final TodayGamesService    todayGamesService;
    private final UpcomingGamesService upcomingGamesService;

    private final CopyOnWriteArrayList<NewMatchDTO> liveGames     = new CopyOnWriteArrayList<>();
    private final CopyOnWriteArrayList<NewMatchDTO> todayGames    = new CopyOnWriteArrayList<>();
    private final CopyOnWriteArrayList<NewMatchDTO> upcomingGames = new CopyOnWriteArrayList<>();

    private volatile Instant liveLastUpdated     = null;
    private volatile Instant todayLastUpdated    = null;
    private volatile Instant upcomingLastUpdated = null;

    // ── Startup ───────────────────────────────────────────────────────────────

    @PostConstruct
    public void init() {
        log.info("╔══════════════════════════════════════════════════════╗");
        log.info("║   🚀 GameDataStore — STARTUP FETCH INITIATED         ║");
        log.info("╚══════════════════════════════════════════════════════╝");

        refreshLive();
        refreshToday();
        refreshUpcoming();

        log.info("╔══════════════════════════════════════════════════════╗");
        log.info("║   ✅ GameDataStore — STARTUP FETCH COMPLETE          ║");
        log.info("║   🔴 Live     : {} matches", liveGames.size());
        log.info("║   📅 Today    : {} matches", todayGames.size());
        log.info("║   📆 Upcoming : {} matches", upcomingGames.size());
        log.info("╚══════════════════════════════════════════════════════╝");
    }

    // ── Schedulers ────────────────────────────────────────────────────────────

    // Live: every 2 minutes
    @Scheduled(fixedDelay = 2 * 60 * 1000, initialDelay = 2 * 60 * 1000)
    public void scheduleLive() {
        log.info("⏰ [GameDataStore] Scheduled trigger — LIVE games refresh");
        refreshLive();
    }

    // Today: every 15 minutes
    @Scheduled(fixedDelay = 15 * 60 * 1000, initialDelay = 15 * 60 * 1000)
    public void scheduleToday() {
        log.info("⏰ [GameDataStore] Scheduled trigger — TODAY games refresh");
        refreshToday();
    }

    // Upcoming: every 60 minutes
    @Scheduled(fixedDelay = 60 * 60 * 1000, initialDelay = 60 * 60 * 1000)
    public void scheduleUpcoming() {
        log.info("⏰ [GameDataStore] Scheduled trigger — UPCOMING games refresh");
        refreshUpcoming();
    }

    // ── Public getters ────────────────────────────────────────────────────────

    public List<NewMatchDTO> getLiveGames()     { return Collections.unmodifiableList(liveGames); }
    public List<NewMatchDTO> getTodayGames()    { return Collections.unmodifiableList(todayGames); }
    public List<NewMatchDTO> getUpcomingGames() { return Collections.unmodifiableList(upcomingGames); }

    public Instant getLiveLastUpdated()     { return liveLastUpdated; }
    public Instant getTodayLastUpdated()    { return todayLastUpdated; }
    public Instant getUpcomingLastUpdated() { return upcomingLastUpdated; }

    // ── Refresh methods ───────────────────────────────────────────────────────

    private void refreshLive() {
        log.info("───────────────────────────────────────────────────────");
        log.info("🔴 [LIVE] Refresh started...");
        long start = System.currentTimeMillis();
        try {
            List<NewMatchDTO> fetched = liveGamesService.getLiveGames();
            int before = liveGames.size();
            liveGames.clear();
            liveGames.addAll(fetched);
            liveLastUpdated = Instant.now();
            long ms = System.currentTimeMillis() - start;
            log.info("✅ [LIVE] Done in {}ms — before={} after={} updatedAt={}", ms, before, liveGames.size(), liveLastUpdated);
        } catch (Exception e) {
            long ms = System.currentTimeMillis() - start;
            log.error("❌ [LIVE] FAILED after {}ms — keeping {} stale entries", ms, liveGames.size());
            log.error("❌ [LIVE] Error : {} — {}", e.getClass().getSimpleName(), e.getMessage());
            if (e.getCause() != null) log.error("❌ [LIVE] Cause : {}", e.getCause().getMessage());
        }
        log.info("───────────────────────────────────────────────────────");
    }

    private void refreshToday() {
        log.info("───────────────────────────────────────────────────────");
        log.info("📅 [TODAY] Refresh started...");
        long start = System.currentTimeMillis();
        try {
            List<NewMatchDTO> fetched = todayGamesService.getTodayGames();
            int before = todayGames.size();
            todayGames.clear();
            todayGames.addAll(fetched);
            todayLastUpdated = Instant.now();
            long ms = System.currentTimeMillis() - start;
            log.info("✅ [TODAY] Done in {}ms — before={} after={} updatedAt={}", ms, before, todayGames.size(), todayLastUpdated);
        } catch (Exception e) {
            long ms = System.currentTimeMillis() - start;
            log.error("❌ [TODAY] FAILED after {}ms — keeping {} stale entries", ms, todayGames.size());
            log.error("❌ [TODAY] Error : {} — {}", e.getClass().getSimpleName(), e.getMessage());
            if (e.getCause() != null) log.error("❌ [TODAY] Cause : {}", e.getCause().getMessage());
        }
        log.info("───────────────────────────────────────────────────────");
    }

    private void refreshUpcoming() {
        log.info("───────────────────────────────────────────────────────");
        log.info("📆 [UPCOMING] Refresh started...");
        long start = System.currentTimeMillis();
        try {
            List<NewMatchDTO> fetched = upcomingGamesService.getUpcomingGames();
            int before = upcomingGames.size();
            upcomingGames.clear();
            upcomingGames.addAll(fetched);
            upcomingLastUpdated = Instant.now();
            long ms = System.currentTimeMillis() - start;
            log.info("✅ [UPCOMING] Done in {}ms — before={} after={} updatedAt={}", ms, before, upcomingGames.size(), upcomingLastUpdated);
        } catch (Exception e) {
            long ms = System.currentTimeMillis() - start;
            log.error("❌ [UPCOMING] FAILED after {}ms — keeping {} stale entries", ms, upcomingGames.size());
            log.error("❌ [UPCOMING] Error : {} — {}", e.getClass().getSimpleName(), e.getMessage());
            if (e.getCause() != null) log.error("❌ [UPCOMING] Cause : {}", e.getCause().getMessage());
        }
        log.info("───────────────────────────────────────────────────────");
    }
}