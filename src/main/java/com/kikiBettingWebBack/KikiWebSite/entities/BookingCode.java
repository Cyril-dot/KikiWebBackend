package com.kikiBettingWebBack.KikiWebSite.entities;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "booking_codes")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BookingCode {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "code", nullable = false, unique = true, length = 10)
    private String code;

    @Column(name = "combined_odds", nullable = false, precision = 10, scale = 4)
    private BigDecimal combinedOdds;

    @Column(name = "game_count", nullable = false)
    private int gameCount;

    @Column(name = "includes_score_prediction", nullable = false)
    @Builder.Default
    private boolean includesScorePrediction = false;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private BookingCodeStatus status = BookingCodeStatus.ACTIVE;

    /**
     * The admin who generated this code.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by", nullable = false)
    private Admin createdBy;  // ← was User, now Admin

    @OneToMany(mappedBy = "bookingCode", cascade = CascadeType.ALL, fetch = FetchType.LAZY, orphanRemoval = true)
    @Builder.Default
    private List<BookingCodeGame> games = new ArrayList<>();

    @OneToMany(mappedBy = "bookingCode", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @Builder.Default
    private List<PlacedBet> placedBets = new ArrayList<>();

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "expires_at")
    private LocalDateTime expiresAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

    public boolean isExpired() {
        return expiresAt != null && LocalDateTime.now().isAfter(expiresAt);
    }

    public boolean isUsable() {
        return status == BookingCodeStatus.ACTIVE && !isExpired();
    }
}