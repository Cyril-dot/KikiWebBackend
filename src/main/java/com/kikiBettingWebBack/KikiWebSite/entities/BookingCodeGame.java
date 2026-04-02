package com.kikiBettingWebBack.KikiWebSite.entities;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * One game entry within a booking code slip.
 * Stores the fixture, the admin's pre-set pick (1/X/2),
 * the locked-in odds, and optionally a score prediction.
 *
 * When a user loads a booking code, they see all of these rows
 * and cannot change the picks — they only choose a stake.
 */
@Entity
@Table(name = "booking_code_games")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BookingCodeGame {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "booking_code_id", nullable = false)
    private BookingCode bookingCode;

    /**
     * The actual match this game entry refers to.
     * Links to the Game entity that admin picked.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "game_id", nullable = false)
    private Game game;

    /**
     * Display order within the slip (1-based).
     */
    @Column(name = "position", nullable = false)
    private int position;

    /**
     * The market the admin pre-selected: HOME_WIN, DRAW, or AWAY_WIN.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "pick", nullable = false)
    private PickType pick;

    /**
     * Odds for this pick at the time the booking code was generated.
     * Locked in — won't change even if live odds shift.
     */
    @Column(name = "odds", nullable = false, precision = 10, scale = 2)
    private BigDecimal odds;

    /**
     * Optional score prediction (e.g. "2-1").
     * Only populated when BookingCode.includesScorePrediction = true.
     */
    @Column(name = "score_prediction", length = 10)
    private String scorePrediction;

    /**
     * FK to a CorrectScoreOption if the admin picked via that market.
     * Null when no score prediction is included.
     */
    @Column(name = "correct_score_option_id")
    private UUID correctScoreOptionId;
}