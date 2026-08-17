package fr.tropimon.stocksmanager;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.block.ShulkerBoxBlock;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.ContainerComponent;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Index local des seuls coffres effectivement ouverts par le joueur. */
public final class TownChestIndex {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final int MAX_SHULKER_DEPTH = 4;
    private static final int MAX_HISTORY_DAYS = 90;
    private static final long SAVE_INTERVAL_MILLIS = 2_000L;
    public static final long FRESH_MILLIS = 2L * 60L * 60L * 1000L;
    public static final long STALE_MILLIS = 24L * 60L * 60L * 1000L;

    private final Path path = FabricLoader.getInstance().getConfigDir()
            .resolve("tropimon_stocks_manager")
            .resolve("town-chests.json");
    private final Map<String, ChestSnapshot> chests = new LinkedHashMap<>();
    private final List<DailySnapshot> history = new ArrayList<>();
    private final Map<String, Map<String, Integer>> watchThresholds = new LinkedHashMap<>();
    private boolean loaded;
    private boolean dirty;
    private long lastSavedAt;

    private TownChestIndex() {
    }

    public static TownChestIndex get() {
        return Holder.INSTANCE;
    }

    private static final class Holder {
        private static final TownChestIndex INSTANCE = new TownChestIndex();
    }

    public synchronized boolean updateChest(String server, String dimension, BlockPos pos, String title,
                                            List<ItemStack> stacks) {
        ensureLoaded();
        long now = System.currentTimeMillis();
        String id = chestId(server, dimension, pos);
        String safeTitle = title == null || title.isBlank() ? "Coffre" : title;
        List<StoredItem> items = new ArrayList<>();
        for (ItemStack stack : stacks) {
            if (!stack.isEmpty()) items.add(store(stack, 0));
        }
        String fingerprint = fingerprint(items);
        ChestSnapshot previous = chests.get(id);
        boolean changed = previous == null
                || !previous.fingerprint.equals(fingerprint)
                || !previous.title.equals(safeTitle);
        if (changed) {
            ChestSnapshot snapshot = new ChestSnapshot();
            snapshot.id = id;
            snapshot.server = server;
            snapshot.dimension = dimension;
            snapshot.x = pos.getX();
            snapshot.y = pos.getY();
            snapshot.z = pos.getZ();
            snapshot.title = safeTitle;
            snapshot.updatedAt = now;
            snapshot.checkedAt = now;
            snapshot.fingerprint = fingerprint;
            snapshot.items = items;
            chests.put(id, snapshot);
        } else {
            previous.checkedAt = now;
        }
        recordDailySnapshot(server, now);
        requestSave(now);
        return changed;
    }

    /** Dernière lecture effective du coffre, conservée entre les sessions de jeu. */
    public synchronized long lastCheckedAt(String server, String dimension, BlockPos pos) {
        ensureLoaded();
        ChestSnapshot snapshot = chests.get(chestId(server, dimension, pos));
        return snapshot == null ? 0L : checkedAt(snapshot);
    }

    public synchronized List<ItemAggregate> search(String server, String query, Scope scope, Namespace namespace) {
        ensureLoaded();
        String normalizedQuery = normalize(query);
        Map<String, ItemAggregate> totals = aggregate(server);
        List<ItemAggregate> result = new ArrayList<>();
        for (ItemAggregate item : totals.values()) {
            int completeTotal = item.directCount + item.shulkerCount;
            item.total = switch (scope) {
                case ALL -> completeTotal;
                case DIRECT -> item.directCount;
                case SHULKER -> item.shulkerCount;
            };
            applyMetadata(server, item, completeTotal);
            if (item.total <= 0 || !namespace.accepts(item.itemId)) continue;
            String haystack = normalize(item.displayName + " " + item.itemId + " "
                    + String.join(" ", item.sourceCounts().keySet()));
            if (normalizedQuery.isBlank() || haystack.contains(normalizedQuery)) result.add(item);
        }
        result.sort(Comparator.comparingInt(ItemAggregate::total).reversed()
                .thenComparing(ItemAggregate::displayName, String.CASE_INSENSITIVE_ORDER));
        return result;
    }

    public synchronized ItemAggregate findItem(String server, String itemId) {
        ensureLoaded();
        ItemAggregate item = aggregate(server).get(itemId);
        if (item == null) {
            item = new ItemAggregate(itemId, displayName(itemId));
        }
        int completeTotal = item.directCount + item.shulkerCount;
        item.total = completeTotal;
        applyMetadata(server, item, completeTotal);
        return item;
    }

    public synchronized List<WatchStatus> watchStatuses(String server) {
        ensureLoaded();
        Map<String, ItemAggregate> totals = aggregate(server);
        List<WatchStatus> result = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : watchThresholds
                .getOrDefault(server, Map.of()).entrySet()) {
            ItemAggregate item = totals.get(entry.getKey());
            int current = item == null ? 0 : item.directCount + item.shulkerCount;
            String name = item == null ? displayName(entry.getKey()) : item.displayName;
            result.add(new WatchStatus(entry.getKey(), name, current, entry.getValue(), current < entry.getValue()));
        }
        result.sort(Comparator.comparing(WatchStatus::belowThreshold).reversed()
                .thenComparing(WatchStatus::displayName, String.CASE_INSENSITIVE_ORDER));
        return result;
    }

    public synchronized int watchThreshold(String server, String itemId) {
        ensureLoaded();
        return watchThresholds.getOrDefault(server, Map.of()).getOrDefault(itemId, 0);
    }

    public synchronized void setWatchThreshold(String server, String itemId, int threshold) {
        ensureLoaded();
        Map<String, Integer> serverThresholds = watchThresholds.computeIfAbsent(server,
                ignored -> new LinkedHashMap<>());
        if (threshold <= 0) serverThresholds.remove(itemId);
        else serverThresholds.put(itemId, threshold);
        if (serverThresholds.isEmpty()) watchThresholds.remove(server);
        dirty = true;
        saveNow();
    }

    public synchronized void flushScheduledSave() {
        if (dirty && System.currentTimeMillis() - lastSavedAt >= SAVE_INTERVAL_MILLIS) saveNow();
    }

    public synchronized Summary summary(String server) {
        ensureLoaded();
        int chestCount = 0;
        long checkedAt = 0L;
        for (ChestSnapshot chest : chests.values()) {
            if (chest.server.equals(server)) {
                chestCount++;
                checkedAt = Math.max(checkedAt, checkedAt(chest));
            }
        }
        List<ItemAggregate> all = search(server, "", Scope.ALL, Namespace.ALL);
        return new Summary(chestCount, all.size(), all.stream().mapToInt(ItemAggregate::total).sum(), checkedAt);
    }

    public ItemStack icon(String itemId) {
        Identifier id = Identifier.tryParse(itemId);
        if (id == null || !Registries.ITEM.containsId(id)) return ItemStack.EMPTY;
        return new ItemStack(Registries.ITEM.get(id));
    }

    public static Freshness freshness(long checkedAt, long now) {
        if (checkedAt <= 0L) return Freshness.UNKNOWN;
        long age = Math.max(0L, now - checkedAt);
        if (age < FRESH_MILLIS) return Freshness.FRESH;
        if (age < STALE_MILLIS) return Freshness.AGING;
        return Freshness.STALE;
    }

    private Map<String, ItemAggregate> aggregate(String server) {
        Map<String, ItemAggregate> totals = new LinkedHashMap<>();
        for (ChestSnapshot chest : chests.values()) {
            if (!chest.server.equals(server)) continue;
            for (StoredItem item : chest.items) add(totals, item, chest, false, 1);
        }
        return totals;
    }

    private void applyMetadata(String server, ItemAggregate item, int completeTotal) {
        DailySnapshot previous = previousDailySnapshot(server);
        if (previous != null) {
            item.previousTotal = previous.totals.getOrDefault(item.itemId, 0);
            item.dailyDelta = completeTotal - item.previousTotal;
            item.comparisonDate = previous.date;
            item.hasDailyComparison = true;
        }
        item.watchThreshold = watchThresholds.getOrDefault(server, Map.of()).getOrDefault(item.itemId, 0);
    }

    private void recordDailySnapshot(String server, long now) {
        String today = LocalDate.ofInstant(Instant.ofEpochMilli(now), ZoneId.systemDefault()).toString();
        upsertDailySnapshot(history, server, today, now, currentTotals(server));
        history.sort(Comparator.comparing((DailySnapshot value) -> value.date).reversed());
        while (history.size() > MAX_HISTORY_DAYS) history.remove(history.size() - 1);
    }

    static void upsertDailySnapshot(List<DailySnapshot> entries, String server, String date,
                                    long recordedAt, Map<String, Integer> totals) {
        DailySnapshot snapshot = null;
        for (DailySnapshot candidate : entries) {
            if (candidate.server.equals(server) && candidate.date.equals(date)) {
                snapshot = candidate;
                break;
            }
        }
        if (snapshot == null) {
            snapshot = new DailySnapshot();
            snapshot.server = server;
            snapshot.date = date;
            entries.add(snapshot);
        }
        snapshot.recordedAt = recordedAt;
        snapshot.totals = new LinkedHashMap<>(totals);
    }

    private DailySnapshot previousDailySnapshot(String server) {
        String today = LocalDate.now().toString();
        return history.stream()
                .filter(value -> value.server.equals(server) && value.date.compareTo(today) < 0)
                .max(Comparator.comparing(value -> value.date))
                .orElse(null);
    }

    private Map<String, Integer> currentTotals(String server) {
        Map<String, Integer> result = new LinkedHashMap<>();
        for (ItemAggregate item : aggregate(server).values()) {
            result.put(item.itemId, item.directCount + item.shulkerCount);
        }
        return result;
    }

    private static void add(Map<String, ItemAggregate> totals, StoredItem item, ChestSnapshot chest,
                            boolean insideShulker, int multiplier) {
        // Une shulker remplie est un stockage : son contenu compte, pas la boîte elle-même.
        if (item.shulker && item.contents != null && !item.contents.isEmpty()) {
            for (StoredItem child : item.contents) {
                add(totals, child, chest, true, Math.max(1, multiplier * item.count));
            }
            return;
        }
        int count = Math.max(0, item.count * multiplier);
        ItemAggregate aggregate = totals.computeIfAbsent(item.itemId,
                ignored -> new ItemAggregate(item.itemId, item.displayName));
        MutableSource source = aggregate.sources.computeIfAbsent(chest.id,
                ignored -> new MutableSource(chest));
        if (insideShulker) {
            aggregate.shulkerCount += count;
            source.shulkerCount += count;
        } else {
            aggregate.directCount += count;
            source.directCount += count;
        }
        long checkedAt = checkedAt(chest);
        if (aggregate.oldestCheckedAt == 0L || checkedAt < aggregate.oldestCheckedAt) {
            aggregate.oldestCheckedAt = checkedAt;
        }
    }

    private static String chestId(String server, String dimension, BlockPos pos) {
        return server + "|" + dimension + "|" + pos.getX() + "," + pos.getY() + "," + pos.getZ();
    }

    private static long checkedAt(ChestSnapshot chest) {
        return chest.checkedAt > 0L ? chest.checkedAt : chest.updatedAt;
    }

    private static String sourceLabel(ChestSnapshot chest) {
        return chest.title + "  [" + chest.x + ", " + chest.y + ", " + chest.z + "]";
    }

    private static String displayName(String itemId) {
        Identifier id = Identifier.tryParse(itemId);
        if (id != null && Registries.ITEM.containsId(id)) {
            return new ItemStack(Registries.ITEM.get(id)).getName().getString();
        }
        return itemId;
    }

    private static StoredItem store(ItemStack stack, int depth) {
        StoredItem stored = new StoredItem();
        stored.itemId = Registries.ITEM.getId(stack.getItem()).toString();
        stored.displayName = stack.getName().getString();
        stored.count = stack.getCount();
        stored.shulker = isShulker(stack);
        if (stored.shulker && depth < MAX_SHULKER_DEPTH) {
            ContainerComponent container = stack.get(DataComponentTypes.CONTAINER);
            if (container != null) {
                for (ItemStack child : container.iterateNonEmpty()) stored.contents.add(store(child, depth + 1));
            }
        }
        return stored;
    }

    private static boolean isShulker(ItemStack stack) {
        return stack.getItem() instanceof BlockItem blockItem
                && blockItem.getBlock() instanceof ShulkerBoxBlock;
    }

    private static String fingerprint(List<StoredItem> items) {
        StringBuilder value = new StringBuilder();
        appendFingerprint(value, items);
        return Integer.toHexString(value.toString().hashCode()) + ":" + value.length();
    }

    private static void appendFingerprint(StringBuilder target, List<StoredItem> items) {
        for (StoredItem item : items) {
            target.append(item.itemId).append('=').append(item.count).append('{');
            appendFingerprint(target, item.contents);
            target.append('}');
        }
    }

    static String normalize(String value) {
        return value == null ? "" : java.text.Normalizer.normalize(value, java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .toLowerCase(Locale.ROOT)
                .trim();
    }

    private void ensureLoaded() {
        if (loaded) return;
        loaded = true;
        if (!Files.isRegularFile(path)) return;
        try (Reader reader = Files.newBufferedReader(path)) {
            IndexFile file = GSON.fromJson(reader, IndexFile.class);
            if (file == null) return;
            if (file.chests != null) {
                for (ChestSnapshot chest : file.chests) {
                    if (chest != null && chest.id != null) chests.put(chest.id, chest);
                }
            }
            if (file.history != null) history.addAll(file.history);
            if (file.watchThresholds != null) watchThresholds.putAll(file.watchThresholds);
        } catch (Exception exception) {
            TropimonStocksManagerClient.LOGGER.warn("Unable to read the local town chest index", exception);
        }
    }

    private void requestSave(long now) {
        dirty = true;
        if (lastSavedAt == 0L || now - lastSavedAt >= SAVE_INTERVAL_MILLIS) saveNow();
    }

    private void saveNow() {
        try {
            Files.createDirectories(path.getParent());
            Path temporary = path.resolveSibling(path.getFileName() + ".tmp");
            IndexFile file = new IndexFile();
            file.version = 2;
            file.savedAt = Instant.now().toString();
            file.chests = new ArrayList<>(chests.values());
            file.history = new ArrayList<>(history);
            file.watchThresholds = new LinkedHashMap<>(watchThresholds);
            try (Writer writer = Files.newBufferedWriter(temporary)) {
                GSON.toJson(file, writer);
            }
            Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING);
            dirty = false;
            lastSavedAt = System.currentTimeMillis();
        } catch (IOException exception) {
            TropimonStocksManagerClient.LOGGER.warn("Unable to save the local town chest index", exception);
        }
    }

    public enum Scope { ALL, DIRECT, SHULKER }

    public enum Namespace {
        ALL(""), MINECRAFT("minecraft"), COBBLEMON("cobblemon"), TROPIMON("tropimon");

        private final String prefix;

        Namespace(String prefix) { this.prefix = prefix; }

        boolean accepts(String itemId) {
            if (this == ALL) return true;
            String itemNamespace = itemId.contains(":") ? itemId.substring(0, itemId.indexOf(':')) : "minecraft";
            return this == TROPIMON
                    ? itemNamespace.equals("tropimon") || itemNamespace.equals("tropimod")
                    : itemNamespace.equals(prefix);
        }
    }

    public enum Freshness { FRESH, AGING, STALE, UNKNOWN }

    public static final class ItemAggregate {
        private final String itemId;
        private final String displayName;
        private final Map<String, MutableSource> sources = new LinkedHashMap<>();
        private int directCount;
        private int shulkerCount;
        private int total;
        private long oldestCheckedAt;
        private boolean hasDailyComparison;
        private int previousTotal;
        private int dailyDelta;
        private String comparisonDate = "";
        private int watchThreshold;

        private ItemAggregate(String itemId, String displayName) {
            this.itemId = itemId;
            this.displayName = displayName;
        }

        public String itemId() { return itemId; }
        public String displayName() { return displayName; }
        public int directCount() { return directCount; }
        public int shulkerCount() { return shulkerCount; }
        public int total() { return total; }
        public long oldestCheckedAt() { return oldestCheckedAt; }
        public Freshness freshness() { return TownChestIndex.freshness(oldestCheckedAt, System.currentTimeMillis()); }
        public boolean hasDailyComparison() { return hasDailyComparison; }
        public int previousTotal() { return previousTotal; }
        public int dailyDelta() { return dailyDelta; }
        public String comparisonDate() { return comparisonDate; }
        public int watchThreshold() { return watchThreshold; }
        public boolean belowThreshold() { return watchThreshold > 0 && directCount + shulkerCount < watchThreshold; }

        public Map<String, Integer> sourceCounts() {
            Map<String, Integer> result = new LinkedHashMap<>();
            for (MutableSource source : sources.values()) {
                result.put(sourceLabel(source.chest), source.directCount + source.shulkerCount);
            }
            return Map.copyOf(result);
        }

        public List<ItemSource> sourceDetails() {
            List<ItemSource> result = new ArrayList<>();
            for (MutableSource source : sources.values()) {
                ChestSnapshot chest = source.chest;
                result.add(new ItemSource(chest.id, chest.title, chest.dimension,
                        chest.x, chest.y, chest.z, source.directCount, source.shulkerCount, checkedAt(chest)));
            }
            result.sort(Comparator.comparingInt(ItemSource::total).reversed()
                    .thenComparing(ItemSource::title, String.CASE_INSENSITIVE_ORDER));
            return result;
        }
    }

    public record ItemSource(String chestId, String title, String dimension, int x, int y, int z,
                             int directCount, int shulkerCount, long checkedAt) {
        public int total() { return directCount + shulkerCount; }
        public Freshness freshness() { return TownChestIndex.freshness(checkedAt, System.currentTimeMillis()); }
    }

    public record WatchStatus(String itemId, String displayName, int current, int threshold,
                              boolean belowThreshold) { }

    public record Summary(int chestCount, int itemTypes, int itemCount, long updatedAt) { }

    private static final class MutableSource {
        final ChestSnapshot chest;
        int directCount;
        int shulkerCount;

        MutableSource(ChestSnapshot chest) { this.chest = chest; }
    }

    private static final class IndexFile {
        int version;
        String savedAt;
        List<ChestSnapshot> chests;
        List<DailySnapshot> history;
        Map<String, Map<String, Integer>> watchThresholds;
    }

    static final class DailySnapshot {
        String server = "";
        String date = "";
        long recordedAt;
        Map<String, Integer> totals = new LinkedHashMap<>();
    }

    private static final class ChestSnapshot {
        String id = "";
        String server = "";
        String dimension = "";
        int x;
        int y;
        int z;
        String title = "";
        long updatedAt;
        long checkedAt;
        String fingerprint = "";
        List<StoredItem> items = new ArrayList<>();
    }

    private static final class StoredItem {
        String itemId = "minecraft:air";
        String displayName = "";
        int count;
        boolean shulker;
        List<StoredItem> contents = new ArrayList<>();
    }
}
