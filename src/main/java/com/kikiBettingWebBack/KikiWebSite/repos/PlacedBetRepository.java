package com.kikiBettingWebBack.KikiWebSite.repos;

import com.kikiBettingWebBack.KikiWebSite.entities.BetStatus;
import com.kikiBettingWebBack.KikiWebSite.entities.PlacedBet;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PlacedBetRepository extends JpaRepository<PlacedBet, UUID> {

    /** Find by bet reference — shown on success screen + used for support queries. */
    Optional<PlacedBet> findByBetReference(String betReference);

    /** All bets placed by a user, newest first. */
    Page<PlacedBet> findByUserIdOrderByPlacedAtDesc(UUID userId, Pageable pageable);

    /** All bets placed against a specific booking code. */
    Page<PlacedBet> findByBookingCodeIdOrderByPlacedAtDesc(UUID bookingCodeId, Pageable pageable);

    /** All PENDING bets — used by settlement job. */
    List<PlacedBet> findByStatus(BetStatus status);

    /**
     * Prevent duplicate bets: check if a user already placed a bet
     * on a specific booking code (optional business rule — 1 bet per user per code).
     */
    boolean existsByUserIdAndBookingCodeId(UUID userId, UUID bookingCodeId);

    /** Sum of all stakes placed on a booking code — admin stat. */
    @Query("""
        SELECT COALESCE(SUM(pb.stake), 0)
        FROM PlacedBet pb
        WHERE pb.bookingCode.id = :bookingCodeId
    """)
    java.math.BigDecimal sumStakesByBookingCodeId(@Param("bookingCodeId") UUID bookingCodeId);


    @Query("SELECT pb FROM PlacedBet pb WHERE pb.bookingCode.id IN :bookingCodeIds AND pb.status = 'PENDING'")
    List<PlacedBet> findPendingByBookingCodeIds(@Param("bookingCodeIds") List<UUID> bookingCodeIds);

    /** All PENDING bets on a booking code — for batch settlement. */
    @Query("""
        SELECT pb FROM PlacedBet pb
        WHERE pb.bookingCode.id = :bookingCodeId
        AND pb.status = 'PENDING'
    """)
    List<PlacedBet> findPendingByBookingCodeId(@Param("bookingCodeId") UUID bookingCodeId);
}