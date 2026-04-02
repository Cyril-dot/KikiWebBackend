package com.kikiBettingWebBack.KikiWebSite.repos;

import com.kikiBettingWebBack.KikiWebSite.entities.BookingCode;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface BookingCodeRepository extends JpaRepository<BookingCode, UUID> {

    /**
     * Find by the short code string — used when user enters code to load slip.
     * Eagerly fetches games + game details to avoid N+1 on the load endpoint.
     */
    @Query("""
        SELECT bc FROM BookingCode bc
        LEFT JOIN FETCH bc.games bcg
        LEFT JOIN FETCH bcg.game g
        WHERE bc.code = :code
    """)
    Optional<BookingCode> findByCodeWithGames(@Param("code") String code);

    /** Plain lookup — used for existence checks. */
    boolean existsByCode(String code);

    /** Admin: list all codes they created, newest first. */
    Page<BookingCode> findByCreatedByIdOrderByCreatedAtDesc(UUID adminId, Pageable pageable);

    /** Find all ACTIVE codes that have passed their expiry — for a cleanup job. */
    @Query("""
        SELECT bc FROM BookingCode bc
        WHERE bc.status = 'ACTIVE'
        AND bc.expiresAt IS NOT NULL
        AND bc.expiresAt < :now
    """)
    List<BookingCode> findExpiredActiveCodes(@Param("now") LocalDateTime now);

    /** Count bets placed on a booking code — quick stat for admin. */
    @Query("SELECT COUNT(pb) FROM PlacedBet pb WHERE pb.bookingCode.id = :id")
    long countPlacedBetsByBookingCodeId(@Param("id") UUID id);
}