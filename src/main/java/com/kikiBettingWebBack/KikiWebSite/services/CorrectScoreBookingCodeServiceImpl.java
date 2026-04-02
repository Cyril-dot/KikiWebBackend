package com.kikiBettingWebBack.KikiWebSite.services;

import com.kikiBettingWebBack.KikiWebSite.dtos.CorrectScoreBookingCodeResponse;
import com.kikiBettingWebBack.KikiWebSite.dtos.CreateCorrectScoreBookingCodeRequest;
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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Handles creation and lifecycle of correct-score booking codes.
 * Every game on the slip must carry a scorePrediction (e.g. "2-1") and a
 * correctScoreOptionId. The includesScorePrediction flag is always set to true
 * by this service — it is never accepted from the caller.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CorrectScoreBookingCodeServiceImpl implements CorrectScoreBookingCodeService {

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
    public CorrectScoreBookingCodeResponse createBookingCode(
            CreateCorrectScoreBookingCodeRequest request, UUID adminId) {

        Admin admin = adminRepository.findById(adminId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Admin not found"));

        List<UUID> gameIds = request.getGames().stream()
                .map(CreateCorrectScoreBookingCodeRequest.GameRequest::getGameId)
                .toList();

        // No duplicate games on the same slip
        if (gameIds.stream().distinct().count() != gameIds.size()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "A slip cannot contain the same game more than once");
        }

        List<Game> games = gameRepository.findAllById(gameIds);
        if (games.size() != gameIds.size()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "One or more game IDs are invalid");
        }

        BigDecimal combinedOdds = request.getGames().stream()
                .map(CreateCorrectScoreBookingCodeRequest.GameRequest::getOdds)
                .reduce(BigDecimal.ONE, BigDecimal::multiply)
                .setScale(4, RoundingMode.HALF_UP);

        String code = bookingCodeHelper.generateUniqueCode();

        BookingCode bookingCode = BookingCode.builder()
                .code(code)
                .combinedOdds(combinedOdds)
                .gameCount(request.getGames().size())
                .includesScorePrediction(true)          // always true for this service
                .status(BookingCodeStatus.ACTIVE)
                .createdBy(admin)
                .expiresAt(request.getExpiresAt())
                .games(new ArrayList<>())
                .build();

        bookingCode = bookingCodeRepository.save(bookingCode);

        List<BookingCodeGame> gameRows = buildGameRows(bookingCode, request.getGames(), games);
        bookingCode.setGames(gameRows);

        log.info("Admin {} created correct-score booking code {} with {} games, combined odds {}",
                adminId, code, gameRows.size(), combinedOdds);

        return toResponse(bookingCode, true);
    }

    // ──────────────────────────────────────────────────────────────────────────
    // LOAD BY CODE (user-facing)
    // ──────────────────────────────────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public CorrectScoreBookingCodeResponse loadByCode(String code) {
        BookingCode bookingCode = bookingCodeRepository.findByCodeWithGames(code.toUpperCase())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Booking code not found: " + code));

        if (!bookingCode.isIncludesScorePrediction()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "This code is a standard slip — use the standard endpoint to load it");
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
    public Page<CorrectScoreBookingCodeResponse> getMyBookingCodes(UUID adminId, Pageable pageable) {
        return bookingCodeRepository
                .findByCreatedByIdAndIncludesScorePredictionTrueOrderByCreatedAtDesc(adminId, pageable)
                .map(bc -> toResponse(bc, true));
    }

    // ──────────────────────────────────────────────────────────────────────────
    // DISABLE
    // ──────────────────────────────────────────────────────────────────────────

    @Override
    @Transactional
    public CorrectScoreBookingCodeResponse disableBookingCode(UUID bookingCodeId, UUID adminId) {
        BookingCode bookingCode = bookingCodeRepository.findById(bookingCodeId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Booking code not found"));

        if (!bookingCode.getCreatedBy().getId().equals(adminId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "You can only disable codes you created");
        }

        if (!bookingCode.isIncludesScorePrediction()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "This code is a standard slip — use the standard endpoint to disable it");
        }

        bookingCode.setStatus(BookingCodeStatus.DISABLED);
        bookingCode = bookingCodeRepository.save(bookingCode);

        log.info("Admin {} disabled correct-score booking code {}", adminId, bookingCode.getCode());
        return toResponse(bookingCode, true);
    }

    // ──────────────────────────────────────────────────────────────────────────
    // HELPERS
    // ──────────────────────────────────────────────────────────────────────────

    private List<BookingCodeGame> buildGameRows(
            BookingCode bookingCode,
            List<CreateCorrectScoreBookingCodeRequest.GameRequest> gameRequests,
            List<Game> games) {

        List<BookingCodeGame> rows = new ArrayList<>();
        for (int i = 0; i < gameRequests.size(); i++) {
            CreateCorrectScoreBookingCodeRequest.GameRequest gr = gameRequests.get(i);
            Game game = games.stream()
                    .filter(g -> g.getId().equals(gr.getGameId()))
                    .findFirst()
                    .orElseThrow();

            BookingCodeGame bcGame = BookingCodeGame.builder()
                    .bookingCode(bookingCode)
                    .game(game)
                    .position(i + 1)
                    .pick(PickType.CORRECT_SCORE)            // semantic pick for correct-score slips
                    .odds(gr.getOdds())
                    .scorePrediction(gr.getScorePrediction())
                    .correctScoreOptionId(gr.getCorrectScoreOptionId())
                    .build();

            rows.add(bookingCodeGameRepository.save(bcGame));
        }
        return rows;
    }

    private CorrectScoreBookingCodeResponse toResponse(BookingCode bc, boolean includeAdminFields) {
        List<CorrectScoreBookingCodeResponse.GameResponse> gameResponses = bc.getGames().stream()
                .map(bcg -> CorrectScoreBookingCodeResponse.GameResponse.builder()
                        .id(bcg.getId())
                        .gameId(bcg.getGame().getId())
                        .homeTeam(bcg.getGame().getHomeTeam())
                        .awayTeam(bcg.getGame().getAwayTeam())
                        .matchDate(bcg.getGame().getMatchDate())
                        .position(bcg.getPosition())
                        .pick(bcg.getPick())
                        .pickLabel(bcg.getPick().getLabel())
                        .odds(bcg.getOdds())
                        .scorePrediction(bcg.getScorePrediction())
                        .correctScoreOptionId(bcg.getCorrectScoreOptionId())
                        .build())
                .toList();

        return CorrectScoreBookingCodeResponse.builder()
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