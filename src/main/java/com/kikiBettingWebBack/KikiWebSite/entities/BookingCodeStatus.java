package com.kikiBettingWebBack.KikiWebSite.entities;

/**
 * Lifecycle status for a BookingCode.
 *
 * ACTIVE   — Users can load and bet against this code.
 * EXPIRED  — The code's expiresAt timestamp has passed.
 * DISABLED — Admin manually deactivated the code.
 */
public enum BookingCodeStatus {
    ACTIVE,
    EXPIRED,
    DISABLED
}