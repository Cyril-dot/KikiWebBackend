package com.kikiBettingWebBack.KikiWebSite.entities;

/**
 * Standard 1X2 market options used on booking code slips.
 *
 * HOME_WIN  — "1" — Home team wins
 * DRAW      — "X" — Match ends in a draw
 * AWAY_WIN  — "2" — Away team wins
 */
public enum PickType {
    HOME_WIN,
    DRAW,
    AWAY_WIN;

    /** Returns the display label shown on the frontend pick pill. */
    public String getLabel() {
        return switch (this) {
            case HOME_WIN -> "1";
            case DRAW -> "X";
            case AWAY_WIN -> "2";
        };
    }
}