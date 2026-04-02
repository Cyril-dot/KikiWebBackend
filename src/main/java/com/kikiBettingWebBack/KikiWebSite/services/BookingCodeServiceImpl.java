package com.kikiBettingWebBack.KikiWebSite.services;

import com.kikiBettingWebBack.KikiWebSite.dtos.*;
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
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class BookingCodeServiceImpl implements BookingCodeService {

    private static final String CODE_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final int CODE_SEGMENT_LENGTH = 4;
    private static final int MAX_CODE_GEN_ATTEMPTS = 10;

    private final BookingCodeRepository bookingCodeRepository;
    private final BookingCodeGameRepository bookingCodeGameRepository;
    private final AdminRepository adminRepository;
    private final GameRepository gameRepository;

    private final SecureRandom secureRandom = new SecureRandom();

    // ──────────────────────────────────────────────────────────────────────────
    // CREATE
    // ──────────────────────────────────────────────────────────────────────────

    @Override
    @Transactional
    public BookingCodeResponse createBookingCode(CreateBookingCodeRequest request, UUID adminId) {
        Admin admin = adminRepository.findById(adminId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Admin not found"));

        // Validate no duplicate games within this slip
        List<UUID> gameIds = request.getGames().stream()
                .map(CreateBookingCodeRequest.BookingCodeGameRequest::getGameId)
                .toList();

        if (gameIds.stream().distinct().count() != gameIds.size()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "A slip cannot contain the same game more than once");
        }

        // Validate all game IDs exist
        List<Game> games = gameRepository.findAllById(gameIds);
        if (games.size() != gameIds.size()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "One or more game IDs are invalid");
        }

        // Validate score predictions when required
        if (request.isIncludesScorePrediction()) {
            boolean anyMissingScore = request.getGames().stream()
                    .anyMatch(g -> g.getScorePrediction() == null || g.getScorePrediction().isBlank());
            if (anyMissingScore) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "All games must have a score prediction when includesScorePrediction is true");
            }
        }

        // Calculate combined odds
        BigDecimal combinedOdds = request.getGames().stream()
                .map(CreateBookingCodeRequest.BookingCodeGameRequest::getOdds)
                .reduce(BigDecimal.ONE, BigDecimal::multiply)
                .setScale(4, RoundingMode.HALF_UP);

        // Generate a unique code
        String code = generateUniqueCode();

        // Build and persist the BookingCode
        BookingCode bookingCode = BookingCode.builder()
                .code(code)
                .combinedOdds(combinedOdds)
                .gameCount(request.getGames().size())
                .includesScorePrediction(request.isIncludesScorePrediction())
                .status(BookingCodeStatus.ACTIVE)
                .createdBy(admin)
                .expiresAt(request.getExpiresAt())
                .games(new ArrayList<>())
                .build();

        bookingCode = bookingCodeRepository.save(bookingCode);

        // Build and persist game rows
        List<BookingCodeGame> gameRows = new ArrayList<>();
        List<CreateBookingCodeRequest.BookingCodeGameRequest> gameRequests = request.getGames();

        for (int i = 0; i < gameRequests.size(); i++) {
            CreateBookingCodeRequest.BookingCodeGameRequest gr = gameRequests.get(i);
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
                    .scorePrediction(gr.getScorePrediction())
                    .correctScoreOptionId(gr.getCorrectScoreOptionId())
                    .build();

            gameRows.add(bookingCodeGameRepository.save(bcGame));
        }

        bookingCode.setGames(gameRows);

        log.info("Admin {} created booking code {} with {} games, combined odds {}",
                adminId, code, gameRows.size(), combinedOdds);

        return toResponse(bookingCode, true);
    }

    // ──────────────────────────────────────────────────────────────────────────
    // LOAD BY CODE (user-facing)
    // ──────────────────────────────────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public BookingCodeResponse loadByCode(String code) {
        BookingCode bookingCode = bookingCodeRepository.findByCodeWithGames(code.toUpperCase())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Booking code not found: " + code));

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
    public Page<BookingCodeResponse> getMyBookingCodes(UUID adminId, Pageable pageable) {
        return bookingCodeRepository
                .findByCreatedByIdOrderByCreatedAtDesc(adminId, pageable)
                .map(bc -> toResponse(bc, true));
    }

    // ──────────────────────────────────────────────────────────────────────────
    // DISABLE CODE
    // ──────────────────────────────────────────────────────────────────────────

    @Override
    @Transactional
    public BookingCodeResponse disableBookingCode(UUID bookingCodeId, UUID adminId) {
        BookingCode bookingCode = bookingCodeRepository.findById(bookingCodeId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Booking code not found"));

        if (!bookingCode.getCreatedBy().getId().equals(adminId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "You can only disable codes you created");
        }

        bookingCode.setStatus(BookingCodeStatus.DISABLED);
        bookingCode = bookingCodeRepository.save(bookingCode);

        log.info("Admin {} disabled booking code {}", adminId, bookingCode.getCode());
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

        log.info("Expired {} booking codes", overdue.size());
    }

    // ──────────────────────────────────────────────────────────────────────────
    // HELPERS
    // ──────────────────────────────────────────────────────────────────────────

    private String generateUniqueCode() {
        for (int attempt = 0; attempt < MAX_CODE_GEN_ATTEMPTS; attempt++) {
            String candidate = randomSegment() + "-" + randomSegment();
            if (!bookingCodeRepository.existsByCode(candidate)) {
                return candidate;
            }
        }
        throw new IllegalStateException("Failed to generate a unique booking code after "
                + MAX_CODE_GEN_ATTEMPTS + " attempts");
    }

    private String randomSegment() {
        StringBuilder sb = new StringBuilder(CODE_SEGMENT_LENGTH);
        for (int i = 0; i < CODE_SEGMENT_LENGTH; i++) {
            sb.append(CODE_CHARS.charAt(secureRandom.nextInt(CODE_CHARS.length())));
        }
        return sb.toString();
    }

    private BookingCodeResponse toResponse(BookingCode bc, boolean includeAdminFields) {
        List<BookingCodeResponse.BookingCodeGameResponse> gameResponses = bc.getGames().stream()
                .map(bcg -> BookingCodeResponse.BookingCodeGameResponse.builder()
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
                        .build())
                .toList();

        return BookingCodeResponse.builder()
                .id(bc.getId())
                .code(bc.getCode())
                .combinedOdds(bc.getCombinedOdds())
                .gameCount(bc.getGameCount())
                .includesScorePrediction(bc.isIncludesScorePrediction())
                .status(bc.getStatus())
                .createdAt(bc.getCreatedAt())
                .expiresAt(bc.getExpiresAt())
                .createdByEmail(includeAdminFields ? bc.getCreatedBy().getEmail() : null)
                .games(gameResponses)
                .build();
    }
}