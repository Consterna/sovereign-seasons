package com.sovereignseasons.api;

/**
 * Sub-division of a season based on elapsed days within that season.
 */
public enum SubSeason {
    EARLY("Temprano", "Early"),
    MID("Medio", "Mid"),
    LATE("Tardío", "Late");

    private final String spanishName;
    private final String englishName;

    SubSeason(String spanishName, String englishName) {
        this.spanishName = spanishName;
        this.englishName = englishName;
    }

    public String getSpanishName() {
        return spanishName;
    }

    public String getEnglishName() {
        return englishName;
    }

    public static SubSeason calculate(int dayOfSeason, int totalDaysPerSeason) {
        if (totalDaysPerSeason <= 1) {
            return MID;
        }
        if (totalDaysPerSeason == 2) {
            return dayOfSeason <= 1 ? EARLY : LATE;
        }

        double oneThird = totalDaysPerSeason / 3.0;
        double twoThirds = (totalDaysPerSeason * 2.0) / 3.0;

        if (dayOfSeason <= Math.round(oneThird)) {
            return EARLY;
        } else if (dayOfSeason <= Math.round(twoThirds)) {
            return MID;
        } else {
            return LATE;
        }
    }
}
