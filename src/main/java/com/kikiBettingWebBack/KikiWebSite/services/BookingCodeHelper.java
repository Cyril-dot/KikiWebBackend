package com.kikiBettingWebBack.KikiWebSite.services;

import com.kikiBettingWebBack.KikiWebSite.repos.BookingCodeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;

/**
 * Shared utility for generating unique booking codes.
 * Used by both StandardBookingCodeServiceImpl and CorrectScoreBookingCodeServiceImpl.
 */
@Component
@RequiredArgsConstructor
public class BookingCodeHelper {

    private static final String CODE_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final int CODE_SEGMENT_LENGTH = 4;
    private static final int MAX_CODE_GEN_ATTEMPTS = 10;

    private final BookingCodeRepository bookingCodeRepository;
    private final SecureRandom secureRandom = new SecureRandom();

    /**
     * Generates a unique booking code of the form XXXX-XXXX.
     * Retries up to MAX_CODE_GEN_ATTEMPTS times before throwing.
     */
    public String generateUniqueCode() {
        for (int attempt = 0; attempt < MAX_CODE_GEN_ATTEMPTS; attempt++) {
            String candidate = randomSegment() + "-" + randomSegment();
            if (!bookingCodeRepository.existsByCode(candidate)) {
                return candidate;
            }
        }
        throw new IllegalStateException(
                "Failed to generate a unique booking code after " + MAX_CODE_GEN_ATTEMPTS + " attempts");
    }

    private String randomSegment() {
        StringBuilder sb = new StringBuilder(CODE_SEGMENT_LENGTH);
        for (int i = 0; i < CODE_SEGMENT_LENGTH; i++) {
            sb.append(CODE_CHARS.charAt(secureRandom.nextInt(CODE_CHARS.length())));
        }
        return sb.toString();
    }
}