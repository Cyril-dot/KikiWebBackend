// dtos/UserBetHistoryResponse.java
package com.kikiBettingWebBack.KikiWebSite.dtos;

import lombok.Builder;
import lombok.Data;
import java.util.List;

@Data
@Builder
public class UserBetHistoryResponse {
    private List<BetSlipResponse> mySlipBets;
    private List<PlacedBetResponse> myBookingCodeBets;
}