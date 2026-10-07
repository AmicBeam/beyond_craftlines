package com.amicbeam.beyondcraftlines.common.data;

import org.junit.jupiter.api.Test;
import java.util.Set;
import java.util.function.Supplier;
import static org.junit.jupiter.api.Assertions.*;

final class NetworkSelectionPolicyTest
{
    @Test void explicitMenuWinsOverRememberedAndPrimaryNetworks()
    {
        assertEquals("menu", NetworkSelectionPolicy.select(true, () -> "menu", unused(), unused(),
                Set.of("menu")::contains));
    }

    @Test void directJeiOrderUsesRememberedNetworkWithoutConsultingPrimary()
    {
        assertEquals("shared", NetworkSelectionPolicy.select(false, unused(), () -> "shared", unused(),
                Set.of("shared")::contains));
    }

    @Test void noMemoryFallsBackToPrimary()
    {
        assertEquals("primary", NetworkSelectionPolicy.select(false, unused(), () -> null, () -> "primary",
                Set.of("primary")::contains));
    }

    @Test void deletedOrRevokedRememberedNetworkFallsBackToPrimary()
    {
        assertEquals("primary", NetworkSelectionPolicy.select(false, unused(), () -> "revoked", () -> "primary",
                Set.of("primary")::contains));
    }

    @Test void deniedExplicitMenuNeverSilentlySelectsADifferentNetwork()
    {
        assertNull(NetworkSelectionPolicy.select(true, () -> "revoked", unused(), unused(),
                Set.of("primary")::contains));
        assertNull(NetworkSelectionPolicy.select(true, () -> null, unused(), unused(), value -> true));
    }

    @Test void noAccessibleNetworkCannotCreateAnOrder()
    {
        assertNull(NetworkSelectionPolicy.select(false, unused(), () -> "revoked", () -> "primary",
                value -> false));
        assertNull(NetworkSelectionPolicy.select(false, unused(), () -> null, () -> null,
                value -> true));
    }

    private static Supplier<String> unused()
    { return () -> fail("lower-priority network must not be consulted"); }
}
