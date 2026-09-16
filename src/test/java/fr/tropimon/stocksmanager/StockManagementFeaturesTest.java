package fr.tropimon.stocksmanager;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

final class StockManagementFeaturesTest {
    @TempDir Path directory;

    @Test void chestNamesTagsFavoritesAndMissingCleanupStaySeparateFromStocks() {
        try (TownChestIndex index = new TownChestIndex(directory.resolve("index.json"))) {
            index.updateStoredChest("s", "d", 1, 2, 3, "Large Chest", List.of(stone(64)), 100L);
            String id = "s|d|1,2,3";
            index.configureChest(id, "Building blocks", "stone, bulk, stone", true);
            TownChestIndex.ChestInfo chest = index.chest(id);
            assertEquals("Building blocks", chest.displayName());
            assertEquals(List.of("stone", "bulk"), chest.tags());
            assertTrue(chest.favorite());
            assertEquals("Building blocks", index.findItem("s", "minecraft:stone")
                    .sourceDetails().getFirst().title());
            assertTrue(index.search("s", "bulk", TownChestIndex.Scope.ALL,
                    TownChestIndex.Namespace.ALL).size() == 1);

            index.noteChestMissing(id, 200L);
            assertTrue(index.chest(id).suspectedMissing());
            assertFalse(index.chest(id).removable());
            assertEquals(0, index.cleanupMissing("s"));
            index.noteChestMissing(id, 300L);
            assertTrue(index.chest(id).removable());
            assertEquals(1, index.cleanupMissing("s"));
            assertNull(index.chest(id));
            assertTrue(index.search("s", "", TownChestIndex.Scope.ALL,
                    TownChestIndex.Namespace.ALL).isEmpty());
        }
    }

    @Test void seeingOrReopeningAChestClearsMissingSuspicion() {
        try (TownChestIndex index = new TownChestIndex(directory.resolve("index.json"))) {
            index.updateStoredChest("s", "d", 1, 2, 3, "Chest", List.of(stone(1)), 100L);
            String id = "s|d|1,2,3";
            index.noteChestMissing(id, 200L);
            index.noteChestPresent(id);
            assertEquals(0, index.chest(id).missingPasses());
            index.noteChestMissing(id, 300L);
            index.updateStoredChest("s", "d", 1, 2, 3, "Chest", List.of(stone(2)), 400L);
            assertEquals(0, index.chest(id).missingPasses());
        }
    }

    @Test void filtersSortsAndFourSavedViewsPersist() throws Exception {
        Path path = directory.resolve("index.json");
        try (TownChestIndex index = new TownChestIndex(path)) {
            index.updateStoredChest("s", "d", 0, 0, 0, "A", List.of(stone(5)), 1L);
            index.updateStoredChest("s", "d", 1, 0, 0, "B",
                    List.of(new TownChestIndex.StoredItem("cobblemon:oran_berry", "Oran Berry", 20,
                            false, List.of())), 1L);
            index.setWatchThreshold("s", "minecraft:stone", 10);
            assertEquals("minecraft:stone", index.search("s", "", TownChestIndex.Scope.ALL,
                    TownChestIndex.Namespace.ALL, TownChestIndex.SortOrder.ALERT, false, false)
                    .getFirst().itemId());
            assertEquals(1, index.search("s", "", TownChestIndex.Scope.ALL,
                    TownChestIndex.Namespace.ALL, TownChestIndex.SortOrder.NAME, true, false).size());
            for (int i = 1; i <= 5; i++) index.saveView("s", new TownChestIndex.SavedView("View " + i, "",
                    TownChestIndex.Scope.ALL, TownChestIndex.Namespace.ALL,
                    TownChestIndex.SortOrder.QUANTITY, i % 2 == 0, false));
            assertEquals(List.of("View 2", "View 3", "View 4", "View 5"),
                    index.savedViews("s").stream().map(TownChestIndex.SavedView::name).toList());
        }
        try (TownChestIndex reloaded = new TownChestIndex(path)) {
            assertEquals(4, reloaded.savedViews("s").size());
        }
    }

    @Test void alertTransitionsAndSnoozesPersistWithoutReconnectSpam() throws Exception {
        Path path = directory.resolve("index.json");
        try (TownChestIndex index = new TownChestIndex(path)) {
            index.updateStoredChest("s", "d", 0, 0, 0, "Chest", List.of(stone(5)), 100L);
            index.setWatchThreshold("s", "minecraft:stone", 10);
            assertEquals(1, index.updateAlertTransitions("s", 200L).size());
            assertTrue(index.updateAlertTransitions("s", 201L).isEmpty());
            index.snoozeAlert("s", "minecraft:stone", System.currentTimeMillis() + 10_000L);
            assertEquals(0, index.activeAlertCount("s"));
        }
        try (TownChestIndex reloaded = new TownChestIndex(path)) {
            assertTrue(reloaded.updateAlertTransitions("s", System.currentTimeMillis()).isEmpty());
            assertTrue(reloaded.watchStatuses("s").getFirst().snoozed(System.currentTimeMillis()));
        }
    }

    @Test void historyStatsRespectRangeAndIgnoreRestocksInConsumptionAverage() {
        try (TownChestIndex index = new TownChestIndex(directory.resolve("index.json"))) {
            long first = LocalDate.now().minusDays(3).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli();
            index.updateStoredChest("s", "d", 0, 0, 0, "Chest", List.of(stone(100)), first);
            index.updateStoredChest("s", "d", 0, 0, 0, "Chest", List.of(stone(80)), first + 86_400_000L);
            index.updateStoredChest("s", "d", 0, 0, 0, "Chest", List.of(stone(120)), first + 172_800_000L);
            index.updateStoredChest("s", "d", 0, 0, 0, "Chest", List.of(stone(90)), first + 259_200_000L);
            TownChestIndex.HistoryStats stats = index.historyStats("s", "minecraft:stone", 7);
            assertEquals(7, stats.days());
            assertEquals(4, stats.entries().size());
            assertEquals(50D / 3D, stats.averageDailyConsumption(), 0.001D);
            assertEquals(6, stats.estimatedDaysRemaining());
        }
    }

    @Test void coverageReasonsProduceExpectedGroups() {
        var current = new TownChestAutoIndexer.CoverageEntry(null,
                TownChestAutoIndexer.CoverageReason.COOLDOWN, 1L);
        var old = new TownChestAutoIndexer.CoverageEntry(null,
                TownChestAutoIndexer.CoverageReason.OLD, 1L);
        var blocked = new TownChestAutoIndexer.CoverageEntry(null,
                TownChestAutoIndexer.CoverageReason.OBSTACLE, 1L);
        assertTrue(current.upToDate());
        assertTrue(old.old());
        assertTrue(blocked.inaccessible());
    }

    private static TownChestIndex.StoredItem stone(int count) {
        return new TownChestIndex.StoredItem("minecraft:stone", "Stone", count, false, List.of());
    }
}
