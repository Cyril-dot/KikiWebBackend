package com.kikiBettingWebBack.KikiWebSite.services;

import com.kikiBettingWebBack.KikiWebSite.dtos.CreateStandardBookingCodeRequest;
import com.kikiBettingWebBack.KikiWebSite.dtos.StandardBookingCodeResponse;
import com.kikiBettingWebBack.KikiWebSite.entities.*;
import com.kikiBettingWebBack.KikiWebSite.repos.AdminRepository;
import com.kikiBettingWebBack.KikiWebSite.repos.BookingCodeGameRepository;
import com.kikiBettingWebBack.KikiWebSite.repos.BookingCodeRepository;
import com.kikiBettingWebBack.KikiWebSite.repos.GameRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Handles creation and lifecycle of standard (Home / Away / Draw) booking codes.
 * Score prediction fields do not exist in this service — they are structurally absent
 * from the request and response DTOs.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StandardBookingCodeServiceImpl implements StandardBookingCodeService {

    private static final List<PickType> ALLOWED_PICKS = List.of(PickType.HOME_WIN, PickType.AWAY_WIN, PickType.DRAW);

    private final BookingCodeRepository bookingCodeRepository;
    private final BookingCodeGameRepository bookingCodeGameRepository;
    private final AdminRepository adminRepository;
    private final GameRepository gameRepository;
    private final BookingCodeHelper bookingCodeHelper;

    // ──────────────────────────────────────────────────────────────────────────
    // CREATE
    // ──────────────────────────────────────────────────────────────────────────

    @Override
    @Transactional
    public StandardBookingCodeResponse createBookingCode(CreateStandardBookingCodeRequest request, UUID adminId) {

        Admin admin = adminRepository.findById(adminId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Admin not found"));

        List<UUID> gameIds = request.getGames().stream()
                .map(CreateStandardBookingCodeRequest.GameRequest::getGameId)
                .toList();

        // No duplicate games on the same slip
        if (gameIds.stream().distinct().count() != gameIds.size()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "A slip cannot contain the same game more than once");
        }

        // Only HOME, AWAY, DRAW are valid for standard slips
        request.getGames().forEach(gr -> {
            if (!ALLOWED_PICKS.contains(gr.getPick())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Invalid pick '" + gr.getPick() + "' — standard slips only allow HOME, AWAY, or DRAW");
            }
        });

        List<Game> games = gameRepository.findAllById(gameIds);
        if (games.size() != gameIds.size()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "One or more game IDs are invalid");
        }

        BigDecimal combinedOdds = request.getGames().stream()
                .map(CreateStandardBookingCodeRequest.GameRequest::getOdds)
                .reduce(BigDecimal.ONE, BigDecimal::multiply)
                .setScale(4, RoundingMode.HALF_UP);

        String code = bookingCodeHelper.generateUniqueCode();

        BookingCode bookingCode = BookingCode.builder()
                .code(code)
                .combinedOdds(combinedOdds)
                .gameCount(request.getGames().size())
                .includesScorePrediction(false)
                .status(BookingCodeStatus.ACTIVE)
                .createdBy(admin)
                .expiresAt(request.getExpiresAt())
                .games(new ArrayList<>())
                .build();

        bookingCode = bookingCodeRepository.save(bookingCode);

        List<BookingCodeGame> gameRows = buildGameRows(bookingCode, request.getGames(), games);
        bookingCode.setGames(gameRows);

        log.info("Admin {} created standard booking code {} with {} games, combined odds {}",
                adminId, code, gameRows.size(), combinedOdds);

        return toResponse(bookingCode, true);
    }

    // ──────────────────────────────────────────────────────────────────────────
    // LOAD BY CODE (user-facing)
    // ──────────────────────────────────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public StandardBookingCodeResponse loadByCode(String code) {
        BookingCode bookingCode = bookingCodeRepository.findByCodeWithGames(code.toUpperCase())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Booking code not found: " + code));

        if (bookingCode.isIncludesScorePrediction()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "This code is a correct-score slip — use the correct-score endpoint to load it");
        }

        if (!bookingCode.isUsable()) {
            String reason = bookingCode.isExpired() ? "expired" : "disabled";
            throw new ResponseStatusException(HttpStatus.GONE,
                    "Booking code is no longer active — it has been " + reason);
        }

        return toResponse(bookingCode, false);
    }

    // ──────────────────────────────────────────────────────────────────────────
    // ADMIN LISTING
    // ──────────────────────────────────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public Page<StandardBookingCodeResponse> getMyBookingCodes(UUID adminId, Pageable pageable) {
        return bookingCodeRepository
                .findByCreatedByIdAndIncludesScorePredictionFalseOrderByCreatedAtDesc(adminId, pageable)
                .map(bc -> toResponse(bc, true));
    }

    // ──────────────────────────────────────────────────────────────────────────
    // DISABLE
    // ──────────────────────────────────────────────────────────────────────────

    @Override
    @Transactional
    public StandardBookingCodeResponse disableBookingCode(UUID bookingCodeId, UUID adminId) {
        BookingCode bookingCode = bookingCodeRepository.findById(bookingCodeId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Booking code not found"));

        if (!bookingCode.getCreatedBy().getId().equals(adminId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "You can only disable codes you created");
        }

        if (bookingCode.isIncludesScorePrediction()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "This code is a correct-score slip — use the correct-score endpoint to disable it");
        }

        bookingCode.setStatus(BookingCodeStatus.DISABLED);
        bookingCode = bookingCodeRepository.save(bookingCode);

        log.info("Admin {} disabled standard booking code {}", adminId, bookingCode.getCode());
        return toResponse(bookingCode, true);
    }

    // ──────────────────────────────────────────────────────────────────────────
    // SCHEDULED EXPIRY
    // ──────────────────────────────────────────────────────────────────────────

    @Override
    @Scheduled(fixedRate = 60_000)
    @Transactional
    public void expireOverdueCodes() {
        List<BookingCode> overdue = bookingCodeRepository.findExpiredActiveCodes(LocalDateTime.now());
        if (overdue.isEmpty()) return;

        overdue.forEach(bc -> bc.setStatus(BookingCodeStatus.EXPIRED));
        bookingCodeRepository.saveAll(overdue);

        log.info("Expired {} booking codes (standard + correct-score)", overdue.size());
    }

    // ──────────────────────────────────────────────────────────────────────────
    // HELPERS
    // ──────────────────────────────────────────────────────────────────────────

    private List<BookingCodeGame> buildGameRows(
            BookingCode bookingCode,
            List<CreateStandardBookingCodeRequest.GameRequest> gameRequests,
            List<Game> games) {

        List<BookingCodeGame> rows = new ArrayList<>();
        for (int i = 0; i < gameRequests.size(); i++) {
            CreateStandardBookingCodeRequest.GameRequest gr = gameRequests.get(i);
            Game game = games.stream()
                    .filter(g -> g.getId().equals(gr.getGameId()))
                    .findFirst()
                    .orElseThrow();

            BookingCodeGame bcGame = BookingCodeGame.builder()
                    .bookingCode(bookingCode)
                    .game(game)
                    .position(i + 1)
                    .pick(gr.getPick())
                    .odds(gr.getOdds())
                    .scorePrediction(null)
                    .correctScoreOptionId(null)
                    .build();

            rows.add(bookingCodeGameRepository.save(bcGame));
        }
        return rows;
    }

    private StandardBookingCodeResponse toResponse(BookingCode bc, boolean includeAdminFields) {
        List<StandardBookingCodeResponse.GameResponse> gameResponses = bc.getGames().stream()
                .map(bcg -> StandardBookingCodeResponse.GameResponse.builder()
                        .id(bcg.getId())
                        .gameId(bcg.getGame().getId())
                        .homeTeam(bcg.getGame().getHomeTeam())
                        .awayTeam(bcg.getGame().getAwayTeam())
                        .matchDate(bcg.getGame().getMatchDate())
                        .position(bcg.getPosition())
                        .pick(bcg.getPick())
                        .pickLabel(bcg.getPick().getLabel())
                        .odds(bcg.getOdds())
                        .build())
                .toList();

        return StandardBookingCodeResponse.builder()
                .id(bc.getId())
                .code(bc.getCode())
                .combinedOdds(bc.getCombinedOdds())
                .gameCount(bc.getGameCount())
                .status(bc.getStatus())
                .createdAt(bc.getCreatedAt())
                .expiresAt(bc.getExpiresAt())
                .createdByEmail(includeAdminFields ? bc.getCreatedBy().getEmail() : null)
                .games(gameResponses)
                .build();
    }
}