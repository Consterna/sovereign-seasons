package com.sovereignseasons.api;

import com.sovereignseasons.api.calendar.SeasonService;
import com.sovereignseasons.api.greenhouse.GreenhouseService;

import java.util.Objects;

/**
 * Main public entry point for the Sovereign Seasons API.
 * Other modules (Pantry, Logistics, Commerce) depend on this API.
 */
public interface SovereignSeasonsApi {

    SeasonService getSeasonService();

    GreenhouseService getGreenhouseService();

    // Singleton holder
    final class Holder {
        private static SovereignSeasonsApi instance;

        private Holder() {}

        public static void set(SovereignSeasonsApi api) {
            instance = api;
        }

        public static SovereignSeasonsApi get() {
            return Objects.requireNonNull(instance, "SovereignSeasonsApi has not been initialized yet.");
        }
    }

    static SovereignSeasonsApi get() {
        return Holder.get();
    }
}
