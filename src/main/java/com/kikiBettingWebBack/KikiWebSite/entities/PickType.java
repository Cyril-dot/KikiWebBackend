package com.kikiBettingWebBack.KikiWebSite.entities;

public enum PickType {
    HOME_WIN,
    DRAW,
    AWAY_WIN,
    CORRECT_SCORE;

    /** Returns the display label shown on the frontend pick pill. */
    public String getLabel() {
        return switch (this) {
            case HOME_WIN -> "1";
            case DRAW -> "X";
            case AWAY_WIN -> "2";
            case CORRECT_SCORE -> "CS"; // or whatever label you want
        };
    }
}