package fr.tropimon.stocksmanager;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

@Timeout(10)
final class IndexPersistenceTest {
    @TempDir Path directory;

    @Test
    void snapshotStaysImmutableWhileStockHistoryAndWatchThresholdsChange() throws Exception {
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        List<TownChestIndex.IndexFile> written = new CopyOnWriteArrayList<>();
        Path path = directory.resolve("index.json");
        TownChestIndex index = new TownChestIndex(path, snapshot -> {
            if (written.isEmpty()) {
                entered.countDown();
                assertTrue(release.await(5, TimeUnit.SECONDS));
            }
            written.add(snapshot);
            TownChestIndex.writeSnapshot(path, snapshot);
        });
        try {
            index.updateStoredChest("server", "dimension", 1, 2, 3, "Chest", List.of(item(10)), 1L);
            assertTrue(entered.await(5, TimeUnit.SECONDS));
            index.updateStoredChest("server", "dimension", 1, 2, 3, "Chest", List.of(item(20)), 2L);
            index.setWatchThreshold("server", "minecraft:stone", 64);
            index.setWatchThreshold("server", "minecraft:stone", 32);
            index.deleteAllItemHistory("server", "minecraft:stone");
        } finally {
            release.countDown();
            index.close();
        }
        TownChestIndex.IndexFile first = written.getFirst();
        TownChestIndex.IndexFile last = written.getLast();
        assertEquals(10, first.chests().getFirst().items().getFirst().count());
        assertEquals(10, first.history().getFirst().totals().get("minecraft:stone"));
        assertTrue(first.watchThresholds().isEmpty());
        assertEquals(20, last.chests().getFirst().items().getFirst().count());
        assertTrue(last.history().isEmpty());
        assertEquals(32, last.watchThresholds().get("server").get("minecraft:stone"));
        assertThrows(UnsupportedOperationException.class, () -> first.chests().clear());
        assertThrows(UnsupportedOperationException.class, () -> first.chests().getFirst().items().clear());
        assertThrows(UnsupportedOperationException.class, () -> first.history().getFirst().totals().clear());
        assertThrows(UnsupportedOperationException.class, () -> last.watchThresholds().get("server").clear());
        assertTrue(Files.isRegularFile(path));
        assertFalse(Files.exists(directory.resolve("index.json.tmp")));
    }

    @Test
    void shutdownPersistsChangesStillInsideTheTwoSecondDelayAndReloadsSameSchema() throws Exception {
        Path path = directory.resolve("index.json");
        try (TownChestIndex index = new TownChestIndex(path)) {
            index.updateStoredChest("server", "dimension", 1, 2, 3, "Chest", List.of(item(10)), 1L);
            index.updateStoredChest("server", "dimension", 1, 2, 3, "Chest", List.of(item(99)), 2L);
            index.setWatchThreshold("server", "minecraft:stone", 120);
        }
        var json = JsonParser.parseString(Files.readString(path)).getAsJsonObject();
        assertEquals(3, json.get("version").getAsInt());
        assertEquals(99, json.getAsJsonArray("chests").get(0).getAsJsonObject()
                .getAsJsonArray("items").get(0).getAsJsonObject().get("count").getAsInt());
        try (TownChestIndex reloaded = new TownChestIndex(path)) {
            assertEquals(99, reloaded.findItem("server", "minecraft:stone").total());
            assertEquals(120, reloaded.watchThreshold("server", "minecraft:stone"));
            reloaded.setWatchThreshold("server", "minecraft:stone", 64);
            assertEquals(64, reloaded.watchThreshold("server", "minecraft:stone"));
        }
    }

    @Test
    void legacyIndexWithoutHistoryOrCheckedAtRemainsReadable() throws Exception {
        Path path = directory.resolve("legacy.json");
        Files.writeString(path, """
                {"version":1,"chests":[{"id":"old","server":"server","dimension":"dimension",
                "x":1,"y":2,"z":3,"title":"Chest","updatedAt":100,"fingerprint":"old",
                "items":[{"itemId":"minecraft:stone","displayName":"Stone","count":7}]}]}
                """);
        try (TownChestIndex index = new TownChestIndex(path)) {
            var item = index.findItem("server", "minecraft:stone");
            assertEquals(7, item.total());
            assertEquals(100, item.oldestCheckedAt());
        }
    }

    @Test
    void nestedContainersAndCallerCollectionsCannotMutateSavedItems() {
        List<TownChestIndex.StoredItem> contents = new ArrayList<>(List.of(item(64)));
        var shulker = new TownChestIndex.StoredItem("minecraft:shulker_box", "Box", 1, true, contents);
        contents.clear();
        assertEquals(1, shulker.contents().size());
        assertThrows(UnsupportedOperationException.class, () -> shulker.contents().clear());
    }

    private static TownChestIndex.StoredItem item(int count) {
        return new TownChestIndex.StoredItem("minecraft:stone", "Stone", count, false, List.of());
    }
}
