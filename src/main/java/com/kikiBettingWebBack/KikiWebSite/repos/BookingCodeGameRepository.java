package com.kikiBettingWebBack.KikiWebSite.repos;

import com.kikiBettingWebBack.KikiWebSite.entities.BookingCodeGame;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface BookingCodeGameRepository extends JpaRepository<BookingCodeGame, UUID> {

    /**
     * Fetch all game rows for a booking code, ordered by position (ascending).
     * Used when building the response to show games in the correct order.
     */
    @Query("""
        SELECT bcg FROM BookingCodeGame bcg
        LEFT JOIN FETCH bcg.game g
        WHERE bcg.bookingCode.id = :bookingCodeId
        ORDER BY bcg.position ASC
    """)
    List<BookingCodeGame> findByBookingCodeIdOrderByPosition(@Param("bookingCodeId") UUID bookingCodeId);

    /**
     * Check whether a specific game is already included in a booking code.
     * Prevents admin from adding the same game twice to one slip.
     */
    boolean existsByBookingCodeIdAndGameId(UUID bookingCodeId, UUID gameId);
}