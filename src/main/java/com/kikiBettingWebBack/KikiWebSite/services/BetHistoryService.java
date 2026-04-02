package com.kikiBettingWebBack.KikiWebSite.services;

import com.kikiBettingWebBack.KikiWebSite.dtos.*;
import com.kikiBettingWebBack.KikiWebSite.repos.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class BetHistoryService {

    private final BetSlipRepository betSlipRepository;
    private final PlacedBetRepository placedBetRepository;
    private final BettingService bettingService;
    private final PlacedBetServiceImpl placedBetService;

    @Transactional(readOnly = true)
    public UserBetHistoryResponse getFullBetHistory(UUID userId) {

        List<BetSlipResponse> slipBets = betSlipRepository
                .findByUserIdOrderByPlacedAtDesc(userId)
                .stream()
                .map(bettingService::toBetSlipResponse)
                .collect(Collectors.toList());

        List<PlacedBetResponse> bookingCodeBets = placedBetRepository
                .findByUserIdOrderByPlacedAtDesc(userId, Pageable.unpaged())
                .stream()
                .map(pb -> placedBetService.toResponse(pb, null))
                .collect(Collectors.toList());

        return UserBetHistoryResponse.builder()
                .mySlipBets(slipBets)
                .myBookingCodeBets(bookingCodeBets)
                .build();
    }
}