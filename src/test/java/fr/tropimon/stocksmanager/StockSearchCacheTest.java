package fr.tropimon.stocksmanager;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import static fr.tropimon.stocksmanager.TownChestIndex.Namespace;
import static fr.tropimon.stocksmanager.TownChestIndex.Scope;
import static org.junit.jupiter.api.Assertions.*;

final class StockSearchCacheTest {
    @TempDir Path directory;

    @Test
    void searchPreservesShulkerCountingScopesOrderingAndAccentInsensitiveChestQueries() {
        try (TownChestIndex index = new TownChestIndex(directory.resolve("index.json"))) {
            var iron = item("minecraft:iron_ingot", "Fer", 10);
            var inside = item("minecraft:iron_ingot", "Fer", 64);
            var box = new TownChestIndex.StoredItem("minecraft:shulker_box", "Shulker", 1, true, List.of(inside));
            var empty = new TownChestIndex.StoredItem("minecraft:shulker_box", "Shulker", 2, true, List.of());
            index.updateStoredChest("s", "d", -12, 60, 34, "Dépôt Est", List.of(iron, box, empty), 100L);

            var all = index.search("s", "DEPOT", Scope.ALL, Namespace.ALL);
            assertEquals(List.of("minecraft:iron_ingot", "minecraft:shulker_box"),
                    all.stream().map(TownChestIndex.ItemAggregate::itemId).toList());
            assertEquals(74, all.getFirst().total());
            assertEquals(10, all.getFirst().directCount());
            assertEquals(64, all.getFirst().shulkerCount());
            assertEquals(2, all.getLast().total());
            assertEquals(74, index.search("s", "-12, 60, 34", Scope.ALL, Namespace.ALL).getFirst().total());
            assertEquals(10, index.search("s", "fer", Scope.DIRECT, Namespace.MINECRAFT).getFirst().total());
            var inBoxes = index.search("s", "", Scope.SHULKER, Namespace.ALL);
            assertEquals(1, inBoxes.size());
            assertEquals(64, inBoxes.getFirst().total());
            assertEquals(74, all.getFirst().total(), "Later scope queries must not mutate an earlier result");
            assertSame(all.getFirst().sourceDetails(), inBoxes.getFirst().sourceDetails());
            assertSame(all.getFirst().sourceCounts(), inBoxes.getFirst().sourceCounts());
            assertTrue(index.search("s", "", Scope.ALL, Namespace.TROPIMON).isEmpty());
        }
    }

    @Test
    void rechecksInvalidateFreshnessAndChangesInvalidateOnlyTheirServer() {
        try (TownChestIndex index = new TownChestIndex(directory.resolve("index.json"))) {
            var stock = List.of(item("minecraft:stone", "Stone", 10));
            index.updateStoredChest("a", "d", 0, 0, 0, "Chest", stock, 100L);
            index.updateStoredChest("b", "d", 0, 0, 0, "Chest", stock, 100L);
            var before = index.findItem("a", "minecraft:stone");
            var other = index.findItem("b", "minecraft:stone");
            assertFalse(index.updateStoredChest("a", "d", 0, 0, 0, "Chest", stock, 200L));
            assertEquals(200, index.findItem("a", "minecraft:stone").oldestCheckedAt());
            assertEquals(100, before.oldestCheckedAt());
            assertSame(other.sourceDetails(), index.findItem("b", "minecraft:stone").sourceDetails());
            index.updateStoredChest("a", "d", 0, 0, 0, "New chest",
                    List.of(item("minecraft:stone", "Stone", 25)), 300L);
            assertEquals(25, index.search("a", "new chest", Scope.ALL, Namespace.ALL).getFirst().total());
            assertTrue(index.search("a", "", Scope.SHULKER, Namespace.ALL).isEmpty());
        }
    }

    @Test
    void cachedStockDoesNotFreezeWatchThresholdsOrHistoryComparisons() {
        long yesterday = LocalDate.now().minusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli();
        try (TownChestIndex index = new TownChestIndex(directory.resolve("index.json"))) {
            index.updateStoredChest("s", "d", 0, 0, 0, "Chest",
                    List.of(item("minecraft:stone", "Stone", 320)), yesterday);
            index.updateStoredChest("s", "d", 0, 0, 0, "Chest",
                    List.of(item("minecraft:stone", "Stone", 245)), System.currentTimeMillis());
            var stock = index.findItem("s", "minecraft:stone");
            assertEquals(-75, stock.dailyDelta());
            assertTrue(stock.hasDailyComparison());
            index.setWatchThreshold("s", "minecraft:stone", 300);
            assertTrue(index.findItem("s", "minecraft:stone").belowThreshold());
            index.setWatchThreshold("s", "minecraft:stone", 100);
            assertFalse(index.findItem("s", "minecraft:stone").belowThreshold());
            index.deleteAllItemHistory("s", "minecraft:stone");
            assertFalse(index.findItem("s", "minecraft:stone").hasDailyComparison());
            assertEquals(245, index.findItem("s", "minecraft:stone").total());
        }
    }

    private static TownChestIndex.StoredItem item(String id, String name, int count) {
        return new TownChestIndex.StoredItem(id, name, count, false, List.of());
    }
}
