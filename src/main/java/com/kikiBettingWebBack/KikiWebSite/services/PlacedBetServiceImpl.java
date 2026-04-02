package com.kikiBettingWebBack.KikiWebSite.services;

import com.kikiBettingWebBack.KikiWebSite.dtos.*;
import com.kikiBettingWebBack.KikiWebSite.entities.*;
import com.kikiBettingWebBack.KikiWebSite.entities.PlacedBet;
import com.kikiBettingWebBack.KikiWebSite.repos.BookingCodeRepository;
import com.kikiBettingWebBack.KikiWebSite.repos.PlacedBetRepository;
import com.kikiBettingWebBack.KikiWebSite.repos.UserRepository;
import com.kikiBettingWebBack.KikiWebSite.repos.WalletRepository;
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
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

@Slf4j
@Service
@RequiredArgsConstructor
public class PlacedBetServiceImpl implements PlacedBetService {

    private static final DateTimeFormatter REF_DATE_FMT = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final PlacedBetRepository placedBetRepository;
    private final BookingCodeRepository bookingCodeRepository;
    private final UserRepository userRepository;
    private final WalletRepository walletRepository;

    // ──────────────────────────────────────────────────────────────────────────
    // PLACE BET
    // ──────────────────────────────────────────────────────────────────────────

    @Override
    @Transactional
    public PlacedBetResponse placeBet(NewPlaceBetRequest request, UUID userId) {

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

        Wallet wallet = walletRepository.findByUserId(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Wallet not found — please contact support"));

        BookingCode bookingCode = bookingCodeRepository
                .findByCodeWithGames(request.getBookingCode().toUpperCase())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Booking code not found: " + request.getBookingCode()));

        if (!bookingCode.isUsable()) {
            String reason = bookingCode.isExpired() ? "expired" : "disabled";
            throw new ResponseStatusException(HttpStatus.GONE,
                    "Booking code is no longer active — it has been " + reason);
        }

        if (placedBetRepository.existsByUserIdAndBookingCodeId(userId, bookingCode.getId())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "You have already placed a bet on this booking code");
        }

        BigDecimal stake = request.getStake();
        if (wallet.getBalance().compareTo(stake) < 0) {
            throw new ResponseStatusException(HttpStatus.PAYMENT_REQUIRED,
                    "Insufficient balance. Available: " + wallet.getBalance() + " " + user.getCurrency());
        }

        wallet.setBalance(wallet.getBalance().subtract(stake));
        walletRepository.save(wallet);

        BigDecimal oddsAtPlacement = bookingCode.getCombinedOdds();
        BigDecimal potentialPayout = stake.multiply(oddsAtPlacement).setScale(2, RoundingMode.HALF_UP);

        String betReference = generateBetReference();

        PlacedBet placedBet = PlacedBet.builder()
                .user(user)
                .bookingCode(bookingCode)
                .stake(stake)
                .oddsAtPlacement(oddsAtPlacement)
                .potentialPayout(potentialPayout)
                .actualPayout(null)
                .status(BetStatus.PENDING)
                .betReference(betReference)
                .currency(user.getCurrency())
                .build();

        placedBet = placedBetRepository.save(placedBet);

        log.info("User {} placed bet {} on code {} — stake {}, potential payout {}",
                userId, betReference, bookingCode.getCode(), stake, potentialPayout);

        return toResponse(placedBet, wallet.getBalance());
    }

    // ──────────────────────────────────────────────────────────────────────────
    // MY BETS
    // ──────────────────────────────────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public Page<PlacedBetResponse> getMyBets(UUID userId, Pageable pageable) {
        return placedBetRepository
                .findByUserIdOrderByPlacedAtDesc(userId, pageable)
                .map(pb -> toResponse(pb, null));
    }

    // ──────────────────────────────────────────────────────────────────────────
    // GET BY REFERENCE
    // ──────────────────────────────────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public PlacedBetResponse getBetByReference(String betReference, UUID userId) {
        PlacedBet placedBet = placedBetRepository.findByBetReference(betReference)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Bet not found: " + betReference));

        if (!placedBet.getUser().getId().equals(userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "You do not have access to this bet");
        }

        return toResponse(placedBet, null);
    }

    // ──────────────────────────────────────────────────────────────────────────
    // SETTLEMENT
    // ──────────────────────────────────────────────────────────────────────────

    @Override
    @Transactional
    public void settleBetsForBookingCode(UUID bookingCodeId, UUID adminId) {
        BookingCode bookingCode = bookingCodeRepository.findById(bookingCodeId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Booking code not found"));

        List<PlacedBet> pendingBets = placedBetRepository.findPendingByBookingCodeId(bookingCodeId);

        if (pendingBets.isEmpty()) {
            log.info("No pending bets to settle for booking code {}", bookingCode.getCode());
            return;
        }

        boolean allCorrect = determineSlipOutcome(bookingCode);
        LocalDateTime now = LocalDateTime.now();

        for (PlacedBet bet : pendingBets) {
            if (allCorrect) {
                bet.setStatus(BetStatus.WON);
                bet.setActualPayout(bet.getPotentialPayout());
                bet.setSettledAt(now);

                Wallet wallet = walletRepository.findByUserId(bet.getUser().getId())
                        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                                "Wallet not found for user " + bet.getUser().getId()));
                wallet.setBalance(wallet.getBalance().add(bet.getPotentialPayout()));
                walletRepository.save(wallet);
            } else {
                bet.setStatus(BetStatus.LOST);
                bet.setActualPayout(BigDecimal.ZERO);
                bet.setSettledAt(now);
            }
        }

        placedBetRepository.saveAll(pendingBets);

        log.info("Admin {} settled {} bets for booking code {} — outcome: {}",
                adminId, pendingBets.size(), bookingCode.getCode(), allCorrect ? "WON" : "LOST");
    }

    // ──────────────────────────────────────────────────────────────────────────
    // HELPERS
    // ──────────────────────────────────────────────────────────────────────────

    private boolean determineSlipOutcome(BookingCode bookingCode) {
        return bookingCode.getGames().stream()
                .allMatch(bcg -> {
                    Game game = bcg.getGame();

                    if (game.getHomeScore() == null || game.getAwayScore() == null) {
                        throw new ResponseStatusException(HttpStatus.CONFLICT,
                                "Game " + game.getId() + " (" + game.getHomeTeam()
                                        + " vs " + game.getAwayTeam() + ") does not have scores entered yet");
                    }

                    PickType actualResult;
                    int home = game.getHomeScore();
                    int away = game.getAwayScore();

                    if (home > away) actualResult = PickType.HOME_WIN;
                    else if (away > home) actualResult = PickType.AWAY_WIN;
                    else actualResult = PickType.DRAW;

                    return actualResult == bcg.getPick();
                });
    }

    private String generateBetReference() {
        String datePart = LocalDateTime.now().format(REF_DATE_FMT);
        String randomPart = String.format("%04X", ThreadLocalRandom.current().nextInt(0x10000));
        return "BET-" + datePart + "-" + randomPart;
    }

    // Package-private so BetHistoryService can reuse it
    PlacedBetResponse toResponse(PlacedBet pb, BigDecimal remainingBalance) {
        return PlacedBetResponse.builder()
                .id(pb.getId())
                .betReference(pb.getBetReference())
                .bookingCode(pb.getBookingCode().getCode())
                .stake(pb.getStake())
                .currency(pb.getCurrency())
                .oddsAtPlacement(pb.getOddsAtPlacement())
                .potentialPayout(pb.getPotentialPayout())
                .status(pb.getStatus())
                .placedAt(pb.getPlacedAt())
                .remainingBalance(remainingBalance)
                .build();
    }
}