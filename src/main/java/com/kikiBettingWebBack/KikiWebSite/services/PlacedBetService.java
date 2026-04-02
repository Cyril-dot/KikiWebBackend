package com.kikiBettingWebBack.KikiWebSite.services;

import com.kikiBettingWebBack.KikiWebSite.dtos.PlacedBetResponse;
import com.kikiBettingWebBack.KikiWebSite.dtos.NewPlaceBetRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface PlacedBetService {

    PlacedBetResponse placeBet(NewPlaceBetRequest request, UUID userId);

    Page<PlacedBetResponse> getMyBets(UUID userId, Pageable pageable);

    PlacedBetResponse getBetByReference(String betReference, UUID userId);

    void settleBetsForBookingCode(UUID bookingCodeId, UUID adminId);
}