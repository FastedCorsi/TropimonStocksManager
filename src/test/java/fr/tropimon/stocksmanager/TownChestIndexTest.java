package fr.tropimon.stocksmanager;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class TownChestIndexTest {
    @Test
    void searchNormalizationIsCaseAndAccentInsensitive() {
        assertEquals("pokeball mega", TownChestIndex.normalize("  PokéBall MÉGA  "));
    }

    @Test
    void tropiFilterAcceptsBothServerContentNamespaces() {
        assertTrue(TownChestIndex.Namespace.TROPIMON.accepts("tropimon:example"));
        assertTrue(TownChestIndex.Namespace.TROPIMON.accepts("tropimod:amethyst_bricks"));
        assertFalse(TownChestIndex.Namespace.TROPIMON.accepts("minecraft:amethyst_block"));
    }

    @Test
    void dailyHistoryKeepsOnlyOneUpdatedSnapshotPerDayAndServer() {
        List<TownChestIndex.DailySnapshot> history = new ArrayList<>();
        TownChestIndex.upsertDailySnapshot(history, "server", "2026-08-17", 1L,
                Map.of("minecraft:iron_ingot", 320));
        TownChestIndex.upsertDailySnapshot(history, "server", "2026-08-17", 2L,
                Map.of("minecraft:iron_ingot", 245));

        assertEquals(1, history.size());
        assertEquals(2L, history.getFirst().recordedAt());
        assertEquals(245, history.getFirst().totals().get("minecraft:iron_ingot"));

        TownChestIndex.upsertDailySnapshot(history, "server", "2026-08-18", 3L, Map.of());
        assertEquals(2, history.size());
    }

    @Test
    void freshnessUsesTwoHoursAndOneDayThresholds() {
        long now = 100_000_000L;
        assertEquals(TownChestIndex.Freshness.FRESH,
                TownChestIndex.freshness(now - TownChestIndex.FRESH_MILLIS + 1L, now));
        assertEquals(TownChestIndex.Freshness.AGING,
                TownChestIndex.freshness(now - TownChestIndex.FRESH_MILLIS, now));
        assertEquals(TownChestIndex.Freshness.STALE,
                TownChestIndex.freshness(now - TownChestIndex.STALE_MILLIS, now));
    }

    @Test
    void itemHistoryCanDeleteOneDayOrEveryDayWithoutTouchingOtherItems() {
        List<TownChestIndex.DailySnapshot> history = new ArrayList<>();
        TownChestIndex.upsertDailySnapshot(history, "server", "2026-08-17", 1L,
                Map.of("minecraft:iron_ingot", 320, "minecraft:gold_ingot", 12));
        TownChestIndex.upsertDailySnapshot(history, "server", "2026-08-18", 2L,
                Map.of("minecraft:iron_ingot", 245, "minecraft:gold_ingot", 18));

        assertTrue(TownChestIndex.deleteItemHistory(history, "server", "minecraft:iron_ingot",
                "2026-08-17"));
        assertEquals(2, history.size());
        assertFalse(history.getFirst().totals().containsKey("minecraft:iron_ingot"));
        assertEquals(12, history.getFirst().totals().get("minecraft:gold_ingot"));

        assertTrue(TownChestIndex.deleteItemHistory(history, "server", "minecraft:iron_ingot", null));
        assertFalse(history.getLast().totals().containsKey("minecraft:iron_ingot"));
        assertEquals(18, history.getLast().totals().get("minecraft:gold_ingot"));
    }
}
