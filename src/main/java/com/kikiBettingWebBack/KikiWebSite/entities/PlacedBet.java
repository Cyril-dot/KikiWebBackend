package com.kikiBettingWebBack.KikiWebSite.entities;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Represents a single user's bet placed using a booking code.
 *
 * Unlike BetSlip (which supports fully custom selections),
 * PlacedBet ties directly to a BookingCode — the games and picks
 * are inherited from it. The user only supplies a stake.
 *
 * Multiple users can place bets on the same BookingCode,
 * each generating their own PlacedBet with a unique bet reference.
 *
 * Example bet reference: BET-20240402-A3F9
 */
@Entity
@Table(name = "placed_bets")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PlacedBet {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /**
     * The user who placed this bet.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /**
     * The booking code slip this bet was placed against.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "booking_code_id", nullable = false)
    private BookingCode bookingCode;

    /**
     * The amount the user staked (in their wallet currency).
     */
    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal stake;

    /**
     * Snapshot of combined odds from the booking code at placement time.
     * Stored separately in case booking code odds are later adjusted.
     */
    @Column(name = "odds_at_placement", nullable = false, precision = 10, scale = 4)
    private BigDecimal oddsAtPlacement;

    /**
     * Potential payout = stake × oddsAtPlacement.
     * Pre-calculated and stored for quick display.
     */
    @Column(name = "potential_payout", nullable = false, precision = 19, scale = 2)
    private BigDecimal potentialPayout;

    /**
     * Actual payout credited to wallet after settlement.
     * Null until the bet is settled (WON or LOST).
     */
    @Column(name = "actual_payout", precision = 19, scale = 2)
    private BigDecimal actualPayout;

    /**
     * Current lifecycle status of this bet.
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private BetStatus status = BetStatus.PENDING;

    /**
     * Unique human-readable reference shown to user after placing.
     * Format: BET-YYYYMMDD-XXXX
     */
    @Column(name = "bet_reference", unique = true, nullable = false)
    private String betReference;

    /**
     * The currency the stake was placed in (copied from user's wallet currency).
     */
    @Column(name = "currency", nullable = false, length = 3)
    private String currency;

    @Column(name = "placed_at", nullable = false, updatable = false)
    private LocalDateTime placedAt;

    @Column(name = "settled_at")
    private LocalDateTime settledAt;

    @PrePersist
    protected void onCreate() {
        placedAt = LocalDateTime.now();
    }
}